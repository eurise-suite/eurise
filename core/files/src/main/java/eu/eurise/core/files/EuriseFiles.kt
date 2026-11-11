package eu.eurise.core.files

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import eu.eurise.core.cloud.CloudAccount
import eu.eurise.core.cloud.CloudAccounts
import eu.eurise.core.cloud.CloudKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Leggere e scrivere nelle cartelle di Eurise File: il telefono e i cloud collegati, senza che chi chiama sappia dove
 * stanno davvero. Sul telefono, nelle cartelle comuni (Documenti, Download, Immagini…) si scrive con MediaStore e non
 * serve nessun permesso; altrove solo con l'accesso a tutti i file già dato a Eurise File.
 */
class EuriseFiles private constructor(private val context: Context) {
    private val accounts = CloudAccounts.get(context)
    private val prefs = context.getSharedPreferences("eurise_files", Context.MODE_PRIVATE)
    private val root: File get() = Environment.getExternalStorageDirectory()

    /** Un luogo in cima: il telefono o un account. */
    data class Place(val account: String?, val label: String, val kind: CloudKind?) {
        val spot get() = Spot(account, if (account == null) PHONE_HOME else "")
    }

    fun places(): List<Place> = listOf(Place(null, "Telefono", null)) + accounts.accounts.value.map { Place(it.id, it.name, it.kind) }

    fun account(id: String?): CloudAccount? = id?.let(accounts::get)

    fun label(spot: Spot): String = if (spot.isPhone) "Telefono" else account(spot.account)?.name ?: "Cloud"

    /** Le cartelle dentro [spot], in ordine di nome. Sul telefono senza accesso a tutti i file mostra quelle che si vedono. */
    suspend fun folders(spot: Spot): List<Entry> = list(spot).filter { it.folder }

    suspend fun list(spot: Spot): List<Entry> = withContext(Dispatchers.IO) {
        val items = if (spot.isPhone) {
            File(root, spot.path).listFiles().orEmpty().filter { !it.name.startsWith(".") }.map {
                Entry(spot.child(it.name), it.name, it.isDirectory, if (it.isFile) it.length() else null, it.lastModified())
            }
        } else {
            val dav = accounts.dav(spot.account) ?: throw IOException("account mancante")
            dav.list(spot.path).map { Entry(spot.child(it.name), it.name, it.folder, it.size, null) }
        }
        items.sortedWith(compareBy<Entry>({ !it.folder }, { it.name.lowercase() }))
    }

    suspend fun mkdir(spot: Spot) = withContext(Dispatchers.IO) {
        if (spot.isPhone) {
            val dir = File(root, spot.path)
            // Senza accesso a tutti i file mkdirs non riesce nelle cartelle comuni: la cartella nasce al primo salvataggio.
            if (!dir.exists()) dir.mkdirs()
        } else {
            (accounts.dav(spot.account) ?: throw IOException("account mancante")).mkdirs(spot.path)
        }
    }

    /**
     * Salva [file] in [spot] con [name] (un numero se c'è già). Restituisce il nome usato. Con [phoneCopy] e un cloud, ne
     * tiene anche una copia sul telefono (Documenti/Eurise/<cartella>), che si apre senza rete.
     */
    suspend fun save(spot: Spot, name: String, file: File, mime: String, phoneCopy: Boolean = false): String = withContext(Dispatchers.IO) {
        val taken = runCatching { list(spot).map { it.name }.toSet() }.getOrDefault(emptySet())
        val free = Names.free(name, taken)
        if (spot.isPhone) savePhone(spot.path, free, file, mime)
        else {
            val dav = accounts.dav(spot.account) ?: throw IOException("account mancante")
            if (spot.path.isNotEmpty()) dav.mkdirs(spot.path)
            dav.upload(Spot.join(spot.path, free), file)
            if (phoneCopy) runCatching { savePhone(Spot.join(PHONE_HOME, "Eurise/" + spot.name.ifEmpty { label(spot) }), free, file, mime) }
        }
        free
    }

    private fun savePhone(path: String, name: String, file: File, mime: String) {
        val top = path.substringBefore('/')
        val shared = top in SHARED
        val manager = Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager()
        if (Build.VERSION.SDK_INT >= 29 && shared && !manager) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, path.trimEnd('/') + "/")
            }
            val collection = when (top) {
                Environment.DIRECTORY_DOWNLOADS -> MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                Environment.DIRECTORY_PICTURES, Environment.DIRECTORY_DCIM -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: throw IOException("non salvato")
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: throw IOException("non salvato")
            return
        }
        if (!manager) throw NeedsAccess()
        val dir = File(root, path).apply { mkdirs() }
        file.copyTo(File(dir, name), overwrite = false)
    }

    /** Copia sul telefono del file in [entry] da aprire (dal cloud si scarica nella cache dell'app). */
    suspend fun fetch(entry: Entry): File = withContext(Dispatchers.IO) {
        if (entry.spot.isPhone) return@withContext File(root, entry.spot.path)
        val dav = accounts.dav(entry.spot.account) ?: throw IOException("account mancante")
        val dir = File(context.cacheDir, "eurise-files/" + Integer.toHexString(entry.spot.key().hashCode())).apply { mkdirs() }
        val target = File(dir, entry.name)
        if (!dav.download(entry.spot.path, target)) throw IOException("non scaricato")
        target
    }

    // --- Memoria delle proposte ---

    val memory: SaveMemory by lazy {
        SaveMemory(prefs.getString(KEY_MEMORY, "").orEmpty().lines().mapNotNull(SaveMemory::decode))
    }

    fun remember(kind: String, spot: Spot, name: String) {
        memory.remember(kind, spot, name, System.currentTimeMillis())
        prefs.edit().putString(KEY_MEMORY, memory.records.joinToString("\n", transform = SaveMemory::encode)).apply()
    }

    /** Sul telefono senza accesso a tutti i file si può scrivere solo nelle cartelle comuni. */
    class NeedsAccess : IOException("serve l'accesso a tutti i file")

    companion object {
        /** La cartella del telefono da cui si parte: Documenti. */
        val PHONE_HOME: String = Environment.DIRECTORY_DOCUMENTS
        private val SHARED = setOf(
            Environment.DIRECTORY_DOCUMENTS, Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_DCIM, Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_MOVIES,
        )
        private const val KEY_MEMORY = "memoria"

        @Volatile private var instance: EuriseFiles? = null
        fun get(context: Context): EuriseFiles = instance ?: synchronized(this) {
            instance ?: EuriseFiles(context.applicationContext).also { instance = it }
        }

        /** Il livello di privacy di un luogo, per le tacche: 3 il telefono, 2 un cloud europeo o il tuo server. */
        fun privacy(kind: CloudKind?): Int = if (kind == null) 3 else 2

        fun country(kind: CloudKind?): String? = when (kind) {
            CloudKind.Murena -> "Francia"
            CloudKind.KDrive -> "Svizzera"
            else -> null
        }
    }
}

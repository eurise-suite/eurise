package eu.eurise.feature.drive

import android.content.Context
import android.os.Environment
import android.os.StatFs
import eu.eurise.core.cloud.CloudAccounts
import eu.eurise.core.cloud.DavShare
import eu.eurise.core.cloud.DavVersion
import eu.eurise.core.cloud.WebDav
import eu.eurise.core.cloud.crypto.Cryptomator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Dove sta un file: [account] nullo vuol dire il telefono; con [vaultRoot] è una cartella cifrata di quell'account. */
data class Place(val account: String?, val label: String, val vaultRoot: String? = null)

data class DriveItem(val place: Place, val path: String, val name: String, val folder: Boolean, val size: Long?, val modified: Long?) {
    val key get() = (place.account ?: "phone") + ":" + place.vaultRoot.orEmpty() + ":" + path
    val extension get() = name.substringAfterLast('.', "").lowercase()
}

data class TrashItem(val id: String, val name: String, val original: String, val at: Long, val size: Long)

data class Space(val label: String, val used: Long, val total: Long?)

/** Accesso unico a telefono e cloud; il navigatore non sa dove sta davvero un file. */
class DriveData(private val context: Context) {
    private val accounts = CloudAccounts.get(context)
    private val prefs = context.getSharedPreferences("drive", Context.MODE_PRIVATE)
    private val root: File get() = Environment.getExternalStorageDirectory()
    private val offlineDir get() = File(context.filesDir, "drive-offline").apply { mkdirs() }
    private val cacheDir get() = File(context.cacheDir, "drive").apply { mkdirs() }
    // Sullo stesso volume dei file, così lo spostamento è immediato e non serve copiarli.
    private val trashDir get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "cestino").apply { mkdirs() }

    val phone = Place(null, "Telefono")
    val places: List<Place> get() = accounts.accounts.value.map { Place(it.id, it.name) }

    private fun dav(place: Place): WebDav = accounts.dav(place.account) ?: throw java.io.IOException("account mancante")

    // --- Cartelle cifrate ---

    private val openVaults = HashMap<String, VaultFs>()
    private val vaultPlain get() = File(cacheDir, "cifrate").apply { mkdirs() }
    private val vaultTemp get() = File(cacheDir, "cifrate-tmp").apply { mkdirs() }

    private fun fs(place: Place): VaultFs? = place.vaultRoot?.let { openVaults[place.account + ":" + it] ?: throw java.io.IOException("cartella chiusa") }

    fun isOpen(place: Place): Boolean = (place.account + ":" + place.vaultRoot) in openVaults

    /** Le cartelle cifrate già viste su questo telefono: solo dove stanno, mai la frase. */
    val vaultPlaces: List<Place>
        get() {
            val array = JSONArray(prefs.getString(KEY_VAULTS, "[]"))
            return (0 until array.length()).map { array.getJSONObject(it) }.map { Place(it.getString("account"), it.getString("label"), it.getString("root")) }
        }

    fun vaultPlace(folder: Place, path: String) = Place(folder.account, path.substringAfterLast('/').ifEmpty { folder.label }, path)

    private fun rememberVault(place: Place) {
        val others = vaultPlaces.filter { it != place }
        prefs.edit().putString(KEY_VAULTS, JSONArray((others + place).map { JSONObject().put("account", it.account).put("label", it.label).put("root", it.vaultRoot) }).toString()).apply()
    }

    private fun forgetVault(account: String?, root: String) {
        val left = vaultPlaces.filterNot { it.account == account && it.vaultRoot == root }
        prefs.edit().putString(KEY_VAULTS, JSONArray(left.map { JSONObject().put("account", it.account).put("label", it.label).put("root", it.vaultRoot) }).toString()).apply()
    }

    /** Apre una cartella cifrata. Con la frase sbagliata lancia [Cryptomator.WrongPassword]. */
    suspend fun unlock(place: Place, password: String) = withContext(Dispatchers.Default) {
        val dav = dav(place)
        val root = place.vaultRoot.orEmpty()
        val config = dav.get("$root/${Cryptomator.CONFIG_FILE}")?.first ?: throw java.io.FileNotFoundException()
        val masterkey = dav.get("$root/${Cryptomator.MASTERKEY_FILE}")?.first ?: throw java.io.FileNotFoundException()
        val vault = Cryptomator.open(config, masterkey, password)
        openVaults[place.account + ":" + root] = VaultFs(dav, root, vault, vaultTemp)
        rememberVault(place)
    }

    /** Crea una cartella cifrata vuota e la lascia aperta. */
    suspend fun createVault(parent: Folder, name: String, password: String): Place = withContext(Dispatchers.Default) {
        val dav = dav(parent.place)
        val root = join(parent.path, name)
        if (dav.stat(root) != null) throw java.nio.file.FileAlreadyExistsException(root)
        val made = Cryptomator.create(password)
        dav.mkdirs("$root/${made.vault.dirPath("")}")
        dav.put("$root/${Cryptomator.MASTERKEY_FILE}", made.masterkey, "application/json")
        dav.put("$root/${Cryptomator.CONFIG_FILE}", made.config, "application/jwt")
        val place = vaultPlace(parent.place, root)
        openVaults[place.account + ":" + root] = VaultFs(dav, root, made.vault, vaultTemp)
        rememberVault(place)
        place
    }

    /** Chiude la cartella e toglie le copie in chiaro aperte finora. */
    fun lock(place: Place) {
        openVaults.remove(place.account + ":" + place.vaultRoot)?.vault?.close()
        vaultPlain.deleteRecursively()
    }

    fun lockAll() {
        openVaults.values.forEach { it.vault.close() }
        openVaults.clear()
        vaultPlain.deleteRecursively()
    }

    /** Copia nella cartella cifrata un file scelto dalla persona. */
    suspend fun addToVault(folder: Folder, uri: android.net.Uri) = withContext(Dispatchers.IO) {
        val fs = fs(folder.place) ?: throw java.io.IOException()
        val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        val plain = File.createTempFile("add", null, vaultPlain)
        try {
            context.contentResolver.openInputStream(uri)?.use { input -> plain.outputStream().use { input.copyTo(it) } } ?: throw java.io.IOException()
            fs.write(join(folder.path, name), plain)
        } finally {
            plain.delete()
        }
    }

    suspend fun list(place: Place, path: String): List<DriveItem> = withContext(Dispatchers.IO) {
        fs(place)?.let { fs ->
            return@withContext fs.list(path).map { DriveItem(place, join(path, it.name), it.name, it.folder, it.size, null) }
        }
        if (place.account == null) {
            File(root, path).listFiles().orEmpty().filter { !it.name.startsWith(".") }.map {
                DriveItem(place, join(path, it.name), it.name, it.isDirectory, if (it.isFile) it.length() else null, it.lastModified())
            }
        } else {
            dav(place).list(path).map { DriveItem(place, join(path, it.name), it.name, it.folder, it.size, null) }
        }
    }

    suspend fun mkdir(place: Place, path: String) = withContext(Dispatchers.IO) {
        val vault = fs(place)
        if (vault != null) vault.mkdir(path)
        else if (place.account == null) { if (!File(root, path).mkdirs()) throw java.io.IOException() } else dav(place).mkdirs(path)
    }

    suspend fun rename(item: DriveItem, newName: String) = withContext(Dispatchers.IO) {
        val parent = item.path.substringBeforeLast('/', "")
        val vault = fs(item.place)
        if (vault != null) vault.move(item.path, join(parent, newName), item.folder)
        else if (item.place.account == null) {
            if (!File(root, item.path).renameTo(File(root, join(parent, newName)))) throw java.io.IOException()
        } else {
            dav(item.place).move(item.path, join(parent, newName))
        }
    }

    /** Il file su disco, scaricandolo se serve; una copia offline ha la precedenza quando manca la rete. */
    suspend fun localCopy(item: DriveItem): File = withContext(Dispatchers.IO) {
        fs(item.place)?.let { vault ->
            val plain = File(File(vaultPlain, Integer.toHexString(item.key.hashCode())), item.name)
            vault.read(item.path, plain)
            return@withContext plain
        }
        if (item.place.account == null) return@withContext File(root, item.path)
        val offline = offlineFile(item)
        val keep = isOffline(item)
        val target = if (keep) offline else File(cacheDir, safeName(item))
        try {
            if (!dav(item.place).download(item.path, target)) throw java.io.FileNotFoundException()
        } catch (e: java.io.IOException) {
            if (!target.exists()) throw e
        }
        target
    }

    /** Sposta nella cartella [folder] dello stesso luogo. */
    suspend fun move(item: DriveItem, folder: String) = withContext(Dispatchers.IO) {
        val target = join(folder, item.name)
        val vault = fs(item.place)
        if (vault != null) vault.move(item.path, target, item.folder)
        else if (item.place.account == null) {
            val destination = File(root, target)
            if (destination.exists() || !File(root, item.path).renameTo(destination)) throw java.io.IOException()
        } else {
            dav(item.place).move(item.path, target)
        }
    }

    fun supportsLinks(place: Place): Boolean = place.account != null && place.vaultRoot == null && accounts.dav(place.account)?.isNextcloud == true

    suspend fun createLink(item: DriveItem, password: String?, expires: String?): String = dav(item.place).createLink(item.path, password, expires)

    suspend fun versions(item: DriveItem): List<DavVersion> = dav(item.place).versions(item.path)

    suspend fun restoreVersion(item: DriveItem, version: DavVersion) = dav(item.place).restoreVersion(version)

    suspend fun links(): List<Pair<Place, DavShare>> = places.filter(::supportsLinks).flatMap { place ->
        runCatching { dav(place).links() }.getOrDefault(emptyList()).map { place to it }
    }

    suspend fun removeLink(place: Place, share: DavShare) = dav(place).removeLink(share.id)

    // --- Offline ---

    private fun offlineList(): JSONArray = JSONArray(prefs.getString(KEY_OFFLINE, "[]"))

    fun isOffline(item: DriveItem): Boolean = offlineFile(item).exists()

    private fun offlineFile(item: DriveItem) = File(offlineDir, safeName(item))
    private fun safeName(item: DriveItem) = Integer.toHexString(item.key.hashCode()) + "_" + item.name

    suspend fun setOffline(item: DriveItem, on: Boolean) = withContext(Dispatchers.IO) {
        val list = (0 until offlineList().length()).map { offlineList().getJSONObject(it) }.filterNot { it.getString("key") == item.key }
        val next = JSONArray(list)
        if (on) {
            if (!dav(item.place).download(item.path, offlineFile(item))) throw java.io.FileNotFoundException()
            next.put(JSONObject().put("key", item.key).put("account", item.place.account).put("label", item.place.label).put("path", item.path).put("name", item.name))
        } else {
            offlineFile(item).delete()
        }
        prefs.edit().putString(KEY_OFFLINE, next.toString()).apply()
    }

    fun offlineItems(): List<DriveItem> {
        val array = offlineList()
        return (0 until array.length()).map { array.getJSONObject(it) }.map {
            val place = Place(it.optString("account").ifEmpty { null }, it.getString("label"))
            val item = DriveItem(place, it.getString("path"), it.getString("name"), false, null, null)
            item.copy(size = offlineFile(item).length())
        }
    }

    // --- Recenti ---

    fun recents(): List<DriveItem> {
        val array = JSONArray(prefs.getString(KEY_RECENT, "[]"))
        return (0 until array.length()).map { array.getJSONObject(it) }.map {
            DriveItem(Place(it.optString("account").ifEmpty { null }, it.getString("label")), it.getString("path"), it.getString("name"), false, null, it.optLong("at"))
        }
    }

    fun remember(item: DriveItem) {
        if (item.place.vaultRoot != null) return // i nomi dentro una cartella cifrata non si annotano in chiaro
        val entry = JSONObject().put("key", item.key).put("account", item.place.account).put("label", item.place.label)
            .put("path", item.path).put("name", item.name).put("at", System.currentTimeMillis())
        val others = recents().filter { it.key != item.key }.take(MAX_RECENT - 1).map {
            JSONObject().put("key", it.key).put("account", it.place.account).put("label", it.place.label).put("path", it.path).put("name", it.name).put("at", it.modified)
        }
        prefs.edit().putString(KEY_RECENT, JSONArray(listOf(entry) + others).toString()).apply()
    }

    private fun forget(item: DriveItem) {
        val left = recents().filter { it.key != item.key }.map {
            JSONObject().put("key", it.key).put("account", it.place.account).put("label", it.place.label).put("path", it.path).put("name", it.name).put("at", it.modified)
        }
        prefs.edit().putString(KEY_RECENT, JSONArray(left).toString()).apply()
    }

    // --- Cestino ---

    /** Telefono: nel cestino di Eurise per 30 giorni. Cloud: lo gestisce il servizio. */
    suspend fun delete(item: DriveItem) = withContext(Dispatchers.IO) {
        forget(item)
        fs(item.place)?.let { vault ->
            vault.delete(item.path, item.folder)
            return@withContext
        }
        if (item.place.account != null) {
            if (isOffline(item)) setOffline(item, false)
            dav(item.place).delete(item.path)
            if (item.folder) forgetVault(item.place.account, item.path)
            return@withContext
        }
        val source = File(root, item.path)
        val id = UUID.randomUUID().toString()
        if (!source.renameTo(File(trashDir, id))) throw java.io.IOException()
        val entry = JSONObject().put("id", id).put("name", item.name).put("path", item.path).put("at", System.currentTimeMillis()).put("size", item.size ?: 0)
        val array = JSONArray(prefs.getString(KEY_TRASH, "[]")).put(entry)
        prefs.edit().putString(KEY_TRASH, array.toString()).apply()
    }

    suspend fun trash(): List<TrashItem> = withContext(Dispatchers.IO) {
        val array = JSONArray(prefs.getString(KEY_TRASH, "[]"))
        val all = (0 until array.length()).map { array.getJSONObject(it) }.map {
            TrashItem(it.getString("id"), it.getString("name"), it.getString("path"), it.getLong("at"), it.optLong("size"))
        }
        val limit = System.currentTimeMillis() - TRASH_DAYS * 86_400_000L
        val (keep, old) = all.partition { it.at > limit }
        if (old.isNotEmpty()) {
            old.forEach { File(trashDir, it.id).deleteRecursively() }
            store(keep)
        }
        keep.sortedByDescending { it.at }
    }

    private fun store(items: List<TrashItem>) {
        val array = JSONArray(items.map { JSONObject().put("id", it.id).put("name", it.name).put("path", it.original).put("at", it.at).put("size", it.size) })
        prefs.edit().putString(KEY_TRASH, array.toString()).apply()
    }

    /** Non sovrascrive mai: se al posto originale ora c'è altro, il file torna con un nome diverso. */
    suspend fun restore(item: TrashItem) = withContext(Dispatchers.IO) {
        var target = File(root, item.original)
        target.parentFile?.mkdirs()
        var n = 1
        while (target.exists()) target = File(target.parentFile, numbered(item.name, n++))
        if (!File(trashDir, item.id).renameTo(target)) throw java.io.IOException()
        store(trash().filter { it.id != item.id })
    }

    suspend fun deleteForever(item: TrashItem) = withContext(Dispatchers.IO) {
        File(trashDir, item.id).deleteRecursively()
        store(trash().filter { it.id != item.id })
    }

    suspend fun spaces(): List<Space> = withContext(Dispatchers.IO) {
        val stat = StatFs(root.path)
        val out = arrayListOf(Space(phone.label, stat.totalBytes - stat.availableBytes, stat.totalBytes))
        places.forEach { p ->
            runCatching { dav(p).quota() }.getOrNull()?.let { q ->
                out += Space(p.label, q.used ?: 0, q.available?.let { a -> (q.used ?: 0) + a })
            }
        }
        out
    }

    companion object {
        private const val KEY_OFFLINE = "offline"
        private const val KEY_RECENT = "recent"
        private const val KEY_TRASH = "trash"
        private const val KEY_VAULTS = "vaults"
        private const val MAX_RECENT = 12
        private const val TRASH_DAYS = 30

        fun join(parent: String, name: String) = if (parent.isEmpty()) name else "$parent/$name"

        internal fun numbered(name: String, n: Int): String {
            val dot = name.lastIndexOf('.')
            return if (dot > 0) name.substring(0, dot) + " ($n)" + name.substring(dot) else "$name ($n)"
        }
    }
}

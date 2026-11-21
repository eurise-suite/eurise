package eu.eurise.feature.notes.voice

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.eurise.core.cloud.CloudAccount
import eu.eurise.core.cloud.CloudAccounts
import eu.eurise.core.cloud.CloudKind
import eu.eurise.core.cloud.CloudSync
import eu.eurise.core.designsystem.component.DataOut
import eu.eurise.core.designsystem.component.Jurisdiction
import eu.eurise.core.designsystem.component.privacyScore
import eu.eurise.feature.notes.R
import eu.eurise.feature.notes.doc.AudioBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Dove va l'audio delle note vocali (tavola NoteVocaleDove). La registrazione nasce sempre nella cartella privata
 * ([voiceDir]); a «Fine» [VoicePlaces.settle] la porta dove l'utente ha scelto. Nella nota resta il testo in ogni caso.
 */
enum class VoicePlace(val key: String) {
    /** Telefono › Registrazioni › Eurise: la vedono File e le altre app. */
    Phone("phone"),

    /** Cartella privata di Eurise: solo le Note. */
    App("app"),

    /** Cartella «Note vocali» del cloud collegato; sul telefono finché non è caricata. */
    Cloud("cloud");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key } ?: App
    }
}

object VoicePlaces {
    private const val PREFS = "eurise_notes_voice"
    private const val KEY_PLACE = "place"
    private const val KEY_PENDING = "pending"

    /** Cartella del cloud con le note vocali. */
    const val CLOUD_FOLDER = "Note vocali"

    /** Sottocartella di Registrazioni (Android 12+) o di Musica (prima). */
    private const val PUBLIC_FOLDER = "Eurise"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Il posto preferito (tocco breve sul microfono); di base la cartella privata, che non esce da Eurise. */
    fun preferred(context: Context): VoicePlace {
        val p = VoicePlace.of(prefs(context).getString(KEY_PLACE, null))
        return if (p == VoicePlace.Cloud && cloudAccount(context) == null) VoicePlace.App else p
    }

    fun setPreferred(context: Context, place: VoicePlace) = prefs(context).edit().putString(KEY_PLACE, place.key).apply()

    /** L'account per le note vocali nel cloud: quello delle note, altrimenti il primo collegato. */
    fun cloudAccount(context: Context): CloudAccount? {
        val accounts = CloudAccounts.get(context)
        return accounts.get(CloudSync.get(context).settings.value.notesAccount) ?: accounts.accounts.value.firstOrNull()
    }

    /** Le tacchette di ogni posto: privata 10, pubblica sul telefono 8 (la leggono le altre app), cloud secondo il servizio. */
    fun privacy(context: Context, place: VoicePlace): Pair<Int, Jurisdiction?> = when (place) {
        VoicePlace.App -> 10 to null
        VoicePlace.Phone -> 8 to null
        VoicePlace.Cloud -> when (cloudAccount(context)?.kind) {
            CloudKind.Murena -> privacyScore(DataOut.Personal, Jurisdiction.EU) to Jurisdiction.EU
            CloudKind.KDrive -> privacyScore(DataOut.Personal, Jurisdiction.CH) to Jurisdiction.CH
            CloudKind.Nextcloud -> privacyScore(DataOut.Personal, Jurisdiction.Own) to Jurisdiction.Own
            CloudKind.WebDav, null -> privacyScore(DataOut.Personal, Jurisdiction.Other) to Jurisdiction.Other
        }
    }

    /** Il nome del servizio cloud come lo si mostra («Murena», «kDrive»…). */
    fun cloudName(context: Context): String? = cloudAccount(context)?.kind?.let {
        when (it) {
            CloudKind.Murena -> "Murena"
            CloudKind.KDrive -> "kDrive"
            CloudKind.Nextcloud -> "Nextcloud"
            CloudKind.WebDav -> context.getString(R.string.notes_voice_place_cloud_generic)
        }
    }

    /** Su Android 8 e 9 la cartella pubblica chiede il permesso di scrivere nella memoria. */
    fun needsStoragePermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** «Telefono › Registrazioni › Eurise», «Solo nelle Note», «Murena › Note vocali». */
    fun label(context: Context, block: AudioBlock): String = when (VoicePlace.of(block.place)) {
        VoicePlace.Phone -> context.getString(R.string.notes_voice_where_phone_path, publicParentName(context))
        VoicePlace.App -> context.getString(R.string.notes_voice_where_app_path)
        VoicePlace.Cloud -> context.getString(
            if (File(voiceDir(context), block.file).exists() && pending(context).has(block.file)) R.string.notes_voice_where_cloud_waiting
            else R.string.notes_voice_where_cloud_path,
            cloudName(context) ?: context.getString(R.string.notes_voice_place_cloud_generic),
        )
    }

    fun label(context: Context, place: VoicePlace): String = when (place) {
        VoicePlace.Phone -> context.getString(R.string.notes_voice_where_phone_path, publicParentName(context))
        VoicePlace.App -> context.getString(R.string.notes_voice_where_app_path)
        VoicePlace.Cloud -> context.getString(R.string.notes_voice_where_cloud_path, cloudName(context) ?: context.getString(R.string.notes_voice_place_cloud_generic))
    }

    private fun publicParentName(context: Context) = context.getString(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) R.string.notes_voice_folder_recordings else R.string.notes_voice_folder_music,
    )

    /**
     * Porta la registrazione appena finita nel posto scelto e restituisce il blocco aggiornato. Se il posto non si può
     * usare (niente permesso, niente cloud), l'audio resta nella cartella privata: non si perde mai.
     */
    suspend fun settle(context: Context, block: AudioBlock, place: VoicePlace): AudioBlock = withContext(Dispatchers.IO) {
        val file = File(voiceDir(context), block.file)
        if (!file.exists()) return@withContext block
        val size = file.length()
        when (place) {
            VoicePlace.App -> block.copy(place = VoicePlace.App.key, uri = "", size = size)
            VoicePlace.Phone -> toPublic(context, file)?.let { uri ->
                file.delete()
                block.copy(place = VoicePlace.Phone.key, uri = uri, size = size)
            } ?: block.copy(place = VoicePlace.App.key, uri = "", size = size)
            VoicePlace.Cloud -> {
                val account = cloudAccount(context) ?: return@withContext block.copy(place = VoicePlace.App.key, uri = "", size = size)
                addPending(context, block.file, account.id)
                VoiceUploadWorker.enqueue(context)
                block.copy(place = VoicePlace.Cloud.key, uri = "$CLOUD_FOLDER/${block.file}", size = size)
            }
        }
    }

    private fun publicName(): String =
        "Nota vocale " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss")) + ".m4a"

    /** Copia nella cartella pubblica; l'indirizzo (content:// o file://) da cui riascoltarla, null se non si è potuto. */
    private fun toPublic(context: Context, file: File): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val parent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Environment.DIRECTORY_RECORDINGS else Environment.DIRECTORY_MUSIC
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, publicName())
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$parent/$PUBLIC_FOLDER")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return null
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: error("niente flusso")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                uri.toString()
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), PUBLIC_FOLDER).apply { mkdirs() }
            val target = File(dir, publicName())
            file.copyTo(target, overwrite = false)
            MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf("audio/mp4"), null)
            Uri.fromFile(target).toString()
        }
    }.getOrNull()

    /**
     * Da dove si ascolta: il file privato se c'è (anche per il cloud, finché non è caricato), l'indirizzo pubblico, o
     * il file scaricato dal cloud in cache. Null se l'audio non è raggiungibile (per esempio è rimasto sull'altro telefono).
     */
    suspend fun source(context: Context, block: AudioBlock): Uri? = withContext(Dispatchers.IO) {
        val local = File(voiceDir(context), block.file)
        if (local.exists()) return@withContext Uri.fromFile(local)
        when (VoicePlace.of(block.place)) {
            VoicePlace.App -> null
            VoicePlace.Phone -> block.uri.takeIf { it.isNotBlank() }?.let(Uri::parse)?.takeIf { reachable(context, it) }
            VoicePlace.Cloud -> {
                val cached = File(File(context.cacheDir, "note-vocali"), block.file)
                if (cached.exists() && cached.length() > 0) return@withContext Uri.fromFile(cached)
                val dav = cloudAccount(context)?.let { CloudAccounts.get(context).dav(it.id) } ?: return@withContext null
                val path = block.uri.ifBlank { "$CLOUD_FOLDER/${block.file}" }
                if (runCatching { dav.download(path, cached) }.getOrDefault(false)) Uri.fromFile(cached) else { cached.delete(); null }
            }
        }
    }

    /** Vero se il posto dell'audio si raggiunge senza rete (per mostrare subito il pulsante). */
    fun availableOffline(context: Context, block: AudioBlock): Boolean =
        File(voiceDir(context), block.file).exists() || VoicePlace.of(block.place) == VoicePlace.Phone && block.uri.isNotBlank() ||
            VoicePlace.of(block.place) == VoicePlace.Cloud

    private fun reachable(context: Context, uri: Uri): Boolean = when (uri.scheme) {
        "file" -> uri.path?.let { File(it).exists() } == true
        else -> runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false }.getOrDefault(false)
    }

    // Caricamenti in attesa: nome del file → account.

    private fun pending(context: Context) = runCatching { JSONObject(prefs(context).getString(KEY_PENDING, "{}")!!) }.getOrDefault(JSONObject())

    private fun addPending(context: Context, file: String, account: String) = synchronized(this) {
        prefs(context).edit().putString(KEY_PENDING, pending(context).put(file, account).toString()).apply()
    }

    internal fun pendingList(context: Context): Map<String, String> {
        val p = pending(context)
        return p.keys().asSequence().associateWith { p.optString(it) }
    }

    internal fun donePending(context: Context, file: String) = synchronized(this) {
        prefs(context).edit().putString(KEY_PENDING, pending(context).apply { remove(file) }.toString()).apply()
    }
}

/**
 * Carica nel cloud le note vocali in attesa, con il Wi-Fi (o la rete scelta in Cloud): a caricamento riuscito la copia sul
 * telefono si toglie. Se manca la rete o il server risponde male si riprova più tardi.
 */
class VoiceUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        var retry = false
        for ((name, accountId) in VoicePlaces.pendingList(context)) {
            val file = File(voiceDir(context), name)
            if (!file.exists()) { VoicePlaces.donePending(context, name); continue }
            val dav = CloudAccounts.get(context).dav(accountId)
            if (dav == null) { retry = true; continue }
            val ok = runCatching {
                dav.mkdirs(VoicePlaces.CLOUD_FOLDER)
                dav.upload("${VoicePlaces.CLOUD_FOLDER}/$name", file)
            }.isSuccess
            if (ok) {
                VoicePlaces.donePending(context, name)
                file.delete()
            } else retry = true
        }
        return if (retry && runAttemptCount < 8) Result.retry() else Result.success()
    }

    companion object {
        private const val NAME = "eurise-note-vocali-cloud"

        fun enqueue(context: Context) {
            val wifiOnly = CloudSync.get(context).settings.value.wifiOnly
            val work = OneTimeWorkRequestBuilder<VoiceUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, work)
        }
    }
}

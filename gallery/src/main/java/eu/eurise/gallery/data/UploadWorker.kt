package eu.eurise.gallery.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.eurise.core.cloud.CloudAccounts
import eu.eurise.core.cloud.DavException
import eu.eurise.core.cloud.WebDav
import eu.eurise.gallery.R
import eu.eurise.gallery.logic.MediaItem
import eu.eurise.gallery.logic.UploadRecord
import eu.eurise.gallery.logic.UploadRule
import eu.eurise.gallery.logic.remoteFolder
import eu.eurise.gallery.logic.uniqueName
import eu.eurise.gallery.logic.uploadQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Svuota la coda: prima toglie dal cloud ciò che è stato chiesto, poi carica una foto alla volta. */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val store = GalleryStore.get(context)

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, applicationContext.getString(R.string.upload_channel), NotificationManager.IMPORTANCE_MIN))
        val notification = Notification.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_gallery_foreground)
            .setContentTitle(applicationContext.getString(R.string.upload_running))
            .setOngoing(true)
            .build()
        return ForegroundInfo(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = store.settings.value
        val dav = CloudAccounts.get(applicationContext).dav(settings.accountId) ?: return@withContext Result.success()
        try {
            store.records().filter { it.state == UploadRecord.DELETE_REMOTE }.forEach {
                dav.delete(it.remote)
                store.setState(listOf(it.uri), UploadRecord.EXCLUDED)
            }
            if (settings.uploadOn) {
                val known = store.records().map { it.uri }.toSet()
                val queue = uploadQueue(MediaRepository(applicationContext).all(), UploadRule(settings.buckets, settings.fromMillis), known)
                // Da Android 12 il sistema può negarlo a un lavoro partito in background: si continua lo stesso.
                if (queue.isNotEmpty()) runCatching { setForeground(getForegroundInfo()) }
                val folders = HashSet<String>()
                for (item in queue) {
                    if (isStopped) return@withContext Result.retry()
                    upload(dav, item, folders)
                }
            }
            store.lastError = null
            Result.success()
        } catch (e: DavException) {
            store.lastError = "${e.code}"
            if (e.code == 401 || e.code == 403 || e.code == 507) Result.failure() else Result.retry()
        } catch (e: IOException) {
            Result.retry()
        }
    }

    private fun original(item: MediaItem): Uri {
        val uri = Uri.parse(item.uri)
        // Senza questo Android toglie la posizione dalla copia: sul cloud deve arrivare la foto intera.
        val allowed = applicationContext.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
        return if (allowed) runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri) else uri
    }

    private suspend fun upload(dav: WebDav, item: MediaItem, folders: MutableSet<String>) {
        val resolver = applicationContext.contentResolver
        val source = original(item)
        val size = try {
            resolver.openAssetFileDescriptor(source, "r")?.use { it.length.takeIf { n -> n > 0 } } ?: item.size
        } catch (e: IOException) {
            return // sparita nel frattempo
        } catch (e: IllegalArgumentException) {
            return
        }
        val folder = remoteFolder(item.takenAt)
        if (folders.add(folder)) dav.mkdirs(folder)
        val name = item.name.ifEmpty { "${item.id}" }
        var path = "$folder/$name"
        val existing = dav.stat(path)
        if (existing == null || existing.size != size) {
            if (existing != null) path = "$folder/${uniqueName(name, item.id)}"
            dav.uploadStream(path, size) { resolver.openInputStream(source) ?: throw IOException("foto non leggibile") }
            val saved = dav.stat(path) ?: throw IOException("il cloud non ha salvato il file")
            if (saved.size != null && saved.size != size) {
                dav.delete(path)
                throw IOException("caricamento incompleto")
            }
        }
        val id = store.addRecord(item.uri, path, item.takenAt, name, size, item.isVideo, item.durationMs, item.bucket)
        runCatching {
            val thumb = resolver.loadThumbnail(Uri.parse(item.uri), Size(256, 256), null)
            store.thumbFile(id).outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 75, it) }
        }
    }

    companion object {
        private const val CHANNEL = "upload"
        private const val NOTIFICATION = 41
        private const val ONCE = "gallery-upload-now"
        private const val PERIODIC = "gallery-upload"

        private fun constraints(s: GallerySettings) = Constraints.Builder()
            .setRequiredNetworkType(if (s.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(s.chargingOnly)
            .build()

        /** Da chiamare quando cambiano le scelte: mette o toglie il giro periodico. */
        fun schedule(context: Context, settings: GallerySettings) {
            val work = WorkManager.getInstance(context)
            if (settings.uploadOn) {
                work.enqueueUniquePeriodicWork(
                    PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<UploadWorker>(1, TimeUnit.HOURS).setConstraints(constraints(settings)).build(),
                )
            } else {
                work.cancelUniqueWork(PERIODIC)
            }
            runNow(context, settings)
        }

        /** Un giro appena le condizioni lo permettono. */
        fun runNow(context: Context, settings: GallerySettings) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONCE, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<UploadWorker>().setConstraints(constraints(settings)).build(),
            )
        }
    }
}

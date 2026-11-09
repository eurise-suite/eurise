package eu.eurise.gallery.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import eu.eurise.gallery.logic.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Foto e video dal MediaStore, riletti quando il telefono ne aggiunge o ne toglie. */
class MediaRepository(context: Context) {
    private val resolver = context.contentResolver
    private val images = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    private val videos = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    fun observe(): Flow<List<MediaItem>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        resolver.registerContentObserver(images, true, observer)
        resolver.registerContentObserver(videos, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate().map { all() }.flowOn(Dispatchers.IO)

    /** Fotografia del momento, per chi lavora in background. Da chiamare fuori dal thread principale. */
    fun all(): List<MediaItem> = query(images, false) + query(videos, true)

    private fun query(table: Uri, isVideo: Boolean): List<MediaItem> {
        val columns = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            add(MediaStore.MediaColumns.IS_FAVORITE)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.SIZE)
            if (isVideo) add(MediaStore.MediaColumns.DURATION)
        }.toTypedArray()
        val out = ArrayList<MediaItem>()
        resolver.query(table, columns, null, null, "${MediaStore.MediaColumns.DATE_TAKEN} DESC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val taken = c.getLong(1).takeIf { it > 0 } ?: (c.getLong(2) * 1000)
                out += MediaItem(
                    id = id,
                    uri = ContentUris.withAppendedId(table, id).toString(),
                    isVideo = isVideo,
                    takenAt = taken,
                    durationMs = if (isVideo) c.getLong(7) else 0,
                    bucket = c.getString(3).orEmpty(),
                    favorite = c.getInt(4) == 1,
                    name = c.getString(5).orEmpty(),
                    size = c.getLong(6),
                )
            }
        }
        return out
    }
}

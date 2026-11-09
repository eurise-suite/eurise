package eu.eurise.gallery.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import eu.eurise.core.cloud.WebDav
import eu.eurise.gallery.logic.MediaItem
import eu.eurise.gallery.logic.cellLat
import eu.eurise.gallery.logic.cellLon
import eu.eurise.gallery.logic.parseVideoLocation
import eu.eurise.gallery.logic.placeCell
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONObject

/** Legge la posizione salvata nelle foto, sul telefono, e ricorda solo la casella di circa 10 km. */
class PlaceIndexer(private val context: Context, private val store: GalleryStore) {

    private fun canRead() = context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Legge le foto mai viste; [onProgress] riceve quante ne mancano. Da chiamare fuori dal thread principale. */
    suspend fun index(media: List<MediaItem>, onProgress: (Int) -> Unit) {
        if (!canRead()) return
        val known = store.places().keys
        val todo = media.filter { !it.cloudOnly && it.uri !in known }
        val batch = HashMap<String, String?>()
        todo.forEachIndexed { i, item ->
            currentCoroutineContext().ensureActive()
            batch[item.uri] = runCatching { read(item) }.getOrNull()?.let { placeCell(it.first, it.second) }
            if (batch.size == 100) {
                store.addPlaces(batch)
                batch.clear()
                onProgress(todo.size - i - 1)
            }
        }
        if (batch.isNotEmpty()) store.addPlaces(batch)
        onProgress(0)
    }

    private fun read(item: MediaItem): Pair<Double, Double>? {
        val uri = MediaStore.setRequireOriginal(Uri.parse(item.uri))
        if (item.isVideo) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                return parseVideoLocation(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION))
            } finally {
                retriever.release()
            }
        }
        return context.contentResolver.openInputStream(uri)?.use { input ->
            val out = FloatArray(2)
            if (ExifInterface(input).getLatLong(out) && !(out[0] == 0f && out[1] == 0f)) out[0].toDouble() to out[1].toDouble() else null
        }
    }

    /** Chiede a Nominatim il nome delle caselle senza nome, una al secondo come chiede il servizio. */
    suspend fun fetchNames(cells: Set<String>) {
        val known = store.placeNames().keys
        for (cell in cells - known) {
            currentCoroutineContext().ensureActive()
            if (!store.settings.value.namesOn) return
            runCatching { lookup(cell) }.getOrNull()?.let { store.addPlaceName(cell, it) }
            delay(1_100)
        }
    }

    private fun lookup(cell: String): String? {
        val url = "https://nominatim.openstreetmap.org/reverse".toHttpUrl().newBuilder()
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("zoom", "10")
            .addQueryParameter("accept-language", "it")
            .addQueryParameter("lat", cellLat(cell).toString())
            .addQueryParameter("lon", cellLon(cell).toString())
            .build()
        val request = Request.Builder().url(url).header("User-Agent", WebDav.USER_AGENT).build()
        return WebDav.sharedClient.newCall(request).execute().use { res ->
            if (!res.isSuccessful) return null
            val json = JSONObject(res.body?.string().orEmpty())
            val address = json.optJSONObject("address")
            listOf("city", "town", "village", "municipality", "county", "state").firstNotNullOfOrNull { address?.optString(it)?.takeIf(String::isNotEmpty) }
                ?: json.optString("name").takeIf(String::isNotEmpty)
        }
    }
}

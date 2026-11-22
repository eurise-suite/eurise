package eu.eurise.camera

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Size
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.camera.data.Ritocco
import eu.eurise.camera.data.CameraPrefs
import eu.eurise.camera.data.DestinationState
import eu.eurise.camera.data.PhotoLocation
import eu.eurise.core.suite.AppearanceContract
import eu.eurise.core.suite.CleanMedia
import eu.eurise.core.suite.SuiteAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Una foto o un video di questa sessione (le sole che si vedono dal blocco schermo). */
data class Shot(val uri: Uri, val isVideo: Boolean, val thumb: ImageBitmap?)

class CameraModel(app: Application) : AndroidViewModel(app) {
    private val prefs = CameraPrefs(app)

    val destinations = mutableStateOf(prefs.destinations().atStart().also { prefs.save(it) })
    val locationOn = mutableStateOf(prefs.locationOn && PhotoLocation.permitted(app))
    val session = mutableStateListOf<Shot>()

    /** Mano dominante della suite (Scrivania): i mancini hanno la fila in basso specchiata. */
    val leftHanded = mutableStateOf(false)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val left = runCatching {
                SuiteAccess.call(app, Uri.parse("content://" + AppearanceContract.authority(BuildConfig.HUB_PACKAGE)),
                    AppearanceContract.METHOD_HAND, null, null)
            }.getOrNull()?.takeIf { it.containsKey(AppearanceContract.KEY_LEFT) }?.getBoolean(AppearanceContract.KEY_LEFT)
            if (left != null) withContext(Dispatchers.Main) { leftHanded.value = left }
        }
        if (locationOn.value) PhotoLocation.warm(app)
    }

    fun update(change: (DestinationState) -> DestinationState) {
        val next = change(destinations.value)
        destinations.value = next
        prefs.save(next)
    }

    fun setLocation(on: Boolean) {
        prefs.locationOn = on
        locationOn.value = on
        if (on) PhotoLocation.warm(getApplication())
    }

    fun location() = if (locationOn.value) PhotoLocation.latest(getApplication()) else null

    fun onShot(uri: Uri, isVideo: Boolean) {
        val shot = Shot(uri, isVideo, null)
        session.add(0, shot)
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            // Prima della miniatura, così anche lei ha i colori corretti.
            if (!isVideo) Ritocco.correct(app.contentResolver, uri, app.cacheDir)
            val thumb = runCatching {
                getApplication<Application>().contentResolver.loadThumbnail(uri, Size(240, 240), null).asImageBitmap()
            }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main) {
                val i = session.indexOfFirst { it.uri == uri }
                if (i >= 0) session[i] = shot.copy(thumb = thumb)
            }
        }
    }

    fun delete(shot: Shot) {
        session.remove(shot)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { getApplication<Application>().contentResolver.delete(shot.uri, null as Bundle?) }
        }
    }

    /** Il foglio Condividi con copie pulite (posizione e dati del telefono tolti). Null se una copia non riesce. */
    suspend fun cleanShare(shots: List<Shot>): Intent? = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val dir = File(app.cacheDir, "share").apply { deleteRecursively(); mkdirs() }
        val uris = ArrayList<Uri>()
        for ((i, shot) in shots.withIndex()) {
            val raw = File(dir, "eurise-$i." + if (shot.isVideo) "mp4" else "jpg")
            val copied = runCatching {
                app.contentResolver.openInputStream(shot.uri)?.use { input -> raw.outputStream().use { input.copyTo(it) } } != null
            }.getOrDefault(false)
            if (!copied) return@withContext null
            val clean = runCatching { if (shot.isVideo) CleanMedia.video(raw) else CleanMedia.image(raw) }.getOrNull()
                ?: return@withContext null
            uris += FileProvider.getUriForFile(app, app.packageName + ".files", clean)
        }
        if (uris.isEmpty()) return@withContext null
        val type = when {
            shots.all { it.isVideo } -> "video/*"
            shots.none { it.isVideo } -> "image/*"
            else -> "*/*"
        }
        val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        send.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        send.setType(type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        Intent.createChooser(send, null)
    }

    /** La Galleria della suite, se c'è; altrimenti null (chi chiama offre «Apri con»). */
    fun galleryIntent(shot: Shot): Intent? {
        val app = getApplication<Application>()
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(shot.uri, if (shot.isVideo) "video/*" else "image/*")
            .setPackage(BuildConfig.GALLERY_PACKAGE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return SuiteAccess.trusted(app, view)?.takeIf { app.packageManager.resolveActivity(it, 0) != null }
    }

    /** Lo Scanner dell'app Eurise (modalità Documento), solo se l'app Eurise c'è ed è della suite. */
    fun scannerIntent(): Intent? {
        val app = getApplication<Application>()
        val intent = Intent(Intent.ACTION_MAIN).setClassName(BuildConfig.HUB_PACKAGE, BuildConfig.HUB_PACKAGE.removeSuffix(".debug") + ".ScannerApp")
        return SuiteAccess.trusted(app, intent)?.takeIf { app.packageManager.resolveActivity(it, 0) != null }
    }
}

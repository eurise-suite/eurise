package eu.eurise.camera

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import eu.eurise.camera.ui.CameraRoute
import eu.eurise.camera.ui.CameraSetup
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.core.suite.CleanMedia
import eu.eurise.core.suite.SuiteAccess
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File

/**
 * Eurise Fotocamera (tavole della pagina «Fotocamera», approvate il 22 novembre 2026). Tre ingressi con la stessa
 * schermata: l'avvio normale ([CameraActivity]), il blocco schermo ([SecureCameraActivity]) e «Scatta foto» per le
 * altre app ([CaptureActivity]).
 */
open class CameraActivity : ComponentActivity() {
    /** I tasti del volume scattano (o avviano e fermano il video). */
    internal val shutterKeys = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    protected open fun setup(intent: Intent): CameraSetup? = CameraSetup(
        startVideo = intent.action == MediaStore.INTENT_ACTION_VIDEO_CAMERA,
        startFront = intent.action == ACTION_SELFIE,
        startNight = intent.action == ACTION_NIGHT,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val setup = setup(intent) ?: return
        if (javaClass == CameraActivity::class.java) shortcuts()
        setContent { EuriseTheme(darkTheme = true) { CameraRoute(setup, shutterKeys, ::onCaptured) } }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_CAMERA) {
            if (event.repeatCount == 0) shutterKeys.tryEmit(Unit)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Le scorciatoie dell'icona (pressione lunga nel cassetto): Video, Selfie, Notte. Dinamiche perché il nome del
     * pacchetto cambia tra prova e ufficiale; si scrivono una volta sola.
     */
    private fun shortcuts() {
        val manager = getSystemService(android.content.pm.ShortcutManager::class.java) ?: return
        if (manager.dynamicShortcuts.size >= 3) return
        fun item(id: String, label: Int, icon: Int, action: String) = android.content.pm.ShortcutInfo.Builder(this, id)
            .setShortLabel(getString(label))
            .setIcon(android.graphics.drawable.Icon.createWithResource(this, icon))
            .setIntent(Intent(action).setClass(this, CameraActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            .build()
        runCatching {
            manager.dynamicShortcuts = listOf(
                item("video", R.string.shortcut_video, R.drawable.ic_shortcut_video, MediaStore.INTENT_ACTION_VIDEO_CAMERA),
                item("selfie", R.string.shortcut_selfie, R.drawable.ic_shortcut_selfie, ACTION_SELFIE),
                item("notte", R.string.shortcut_night, R.drawable.ic_shortcut_night, ACTION_NIGHT),
            )
        }
    }

    /** Solo per [CaptureActivity]: la foto confermata da restituire. */
    protected open fun onCaptured(file: File) = Unit

    /** Dal blocco schermo: chiede lo sblocco, poi fa [then]. Altrove la fa subito. */
    internal fun afterUnlock(then: () -> Unit) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isKeyguardLocked) { then(); return }
        keyguard.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = then()
        })
    }
}

/** Dal blocco schermo (doppia pressione del tasto di accensione): si vedono solo le foto appena fatte. */
class SecureCameraActivity : CameraActivity() {
    override fun setup(intent: Intent): CameraSetup = CameraSetup(locked = true)
}

/**
 * «Scatta foto» per un'altra app. La foto va dove dice l'app ([MediaStore.EXTRA_OUTPUT]), pulita (senza posizione né
 * dati del telefono); senza indirizzo torna una miniatura. Un indirizzo che punta ai nostri file si rifiuta
 * ([SuiteAccess.isForeignUri]): un'app non può farci scrivere dentro la Fotocamera.
 */
class CaptureActivity : CameraActivity() {
    private var output: Uri? = null

    override fun setup(intent: Intent): CameraSetup? {
        output = IntentCompat.getParcelableExtra(intent, MediaStore.EXTRA_OUTPUT, Uri::class.java)
        val out = output
        if (out != null && !SuiteAccess.isForeignUri(this, out)) {
            Log.w(TAG, "Uscita rifiutata: punta ai file della Fotocamera")
            setResult(Activity.RESULT_CANCELED)
            finish()
            return null
        }
        return CameraSetup(capture = true)
    }

    override fun onCaptured(file: File) {
        val clean = runCatching { CleanMedia.image(file) }.getOrNull()
        if (clean == null) {
            file.delete()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val out = output
        val ok = runCatching {
            if (out != null) {
                contentResolver.openOutputStream(out, "wt")?.use { sink -> clean.inputStream().use { it.copyTo(sink) } } != null
            } else true
        }.getOrDefault(false)
        if (!ok) {
            clean.delete()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val result = Intent()
        if (out != null) result.data = out
        else runCatching {
            val small = ImageDecoder.decodeBitmap(ImageDecoder.createSource(clean)) { decoder, info, _ ->
                val side = maxOf(info.size.width, info.size.height).coerceAtLeast(1)
                val scale = 320f / side
                decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            result.putExtra("data", small as Bitmap)
        }
        clean.delete()
        setResult(Activity.RESULT_OK, result)
        finish()
    }
}

internal const val TAG = "EuriseCamera"
internal const val ACTION_SELFIE = "eu.eurise.camera.SELFIE"
internal const val ACTION_NIGHT = "eu.eurise.camera.NIGHT"

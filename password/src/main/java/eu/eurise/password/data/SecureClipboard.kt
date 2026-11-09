package eu.eurise.password.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle

/** Copia marcando il testo come riservato (la tastiera non lo mostra) e lo cancella dopo 45 secondi. */
object SecureClipboard {
    private val handler = Handler(Looper.getMainLooper())
    private var clear: Runnable? = null

    fun copy(context: Context, label: String, value: String) {
        val clipboard = context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, value)
        clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        clipboard.setPrimaryClip(clip)
        clear?.let(handler::removeCallbacks)
        clear = Runnable {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip()
            else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }.also { handler.postDelayed(it, 45_000) }
    }
}

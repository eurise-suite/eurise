package eu.eurise.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Le anteprime delle schede anche dopo un riavvio (richiesta del proprietario, 15 novembre: riconoscere a colpo d'occhio
 * le schede nella griglia). Solo schede normali, mai private; nella cache dell'app (`cache/anteprime`, che Android può
 * vuotare), una per indirizzo, 270 px di larghezza in WebP. Si tengono solo quelle delle schede ancora aperte.
 */
internal object Thumbs {
    private val io = Executors.newSingleThreadExecutor()
    private val loaded = mutableStateMapOf<String, ImageBitmap>()
    private val asked = HashSet<String>()
    private lateinit var dir: File

    fun init(context: Context) {
        dir = File(context.cacheDir, "anteprime").apply { mkdirs() }
    }

    private fun key(url: String): String =
        MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)

    private fun file(url: String) = File(dir, key(url) + ".webp")

    /** L'anteprima salvata per [url], se c'è; la prima volta si legge dal disco fuori dal thread principale. */
    fun get(url: String): ImageBitmap? {
        if (!::dir.isInitialized || !url.startsWith("http")) return null
        val k = key(url)
        loaded[k]?.let { return it }
        synchronized(asked) { if (!asked.add(k)) return null }
        io.execute { runCatching { BitmapFactory.decodeFile(file(url).path) }.getOrNull()?.let { loaded[k] = it.asImageBitmap() } }
        return null
    }

    fun save(url: String, bmp: Bitmap) {
        if (!::dir.isInitialized || !url.startsWith("http")) return
        loaded[key(url)] = bmp.asImageBitmap()
        io.execute {
            runCatching {
                @Suppress("DEPRECATION")
                val format = if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                file(url).outputStream().use { bmp.compress(format, 70, it) }
            }
        }
    }

    /** Cancella le anteprime delle schede chiuse. */
    fun keepOnly(urls: Collection<String>) {
        if (!::dir.isInitialized) return
        val keep = urls.map { key(it) + ".webp" }.toHashSet()
        io.execute { dir.listFiles()?.forEach { if (it.name !in keep) it.delete() } }
    }
}

package eu.eurise.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest

/**
 * Le icone dei siti (favicon) per schede e segnalibri. L'estensione Eurise dice quale icona dichiara la pagina aperta;
 * qui si scarica dallo stesso sito con il motore di Gecko e senza cookie, si riduce a 64 px e si tiene sul telefono
 * (`files/icone/<sito>.png`). Nessun servizio di icone esterno; nelle schede private non si scarica né si salva nulla;
 * un sito mai aperto resta con la sua iniziale (i segnalibri non contattano siti da soli). Si rinnova dopo 30 giorni.
 */
object SiteIcons {
    private const val SIZE = 64
    private const val MAX_BYTES = 512 * 1024
    private const val REFRESH_MS = 30L * 24 * 60 * 60 * 1000

    private val io = Executors.newSingleThreadExecutor()
    private val loaded = mutableStateMapOf<String, ImageBitmap>()
    private val missing = HashSet<String>()
    private val inFlight = HashSet<String>()
    private lateinit var dir: File

    fun init(context: Context) {
        dir = File(context.filesDir, "icone").apply { mkdirs() }
    }

    private fun key(host: String) = host.lowercase().removePrefix("www.").replace(Regex("[^a-z0-9.-]"), "_")
    private fun file(host: String) = File(dir, key(host) + ".png")

    /** L'icona del sito, se è già sul telefono; la prima volta si legge dal disco (fuori dal thread principale). */
    fun get(host: String): ImageBitmap? {
        if (host.isBlank() || !::dir.isInitialized) return null
        val k = key(host)
        loaded[k]?.let { return it }
        synchronized(missing) { if (k in missing || !inFlight.add("disk:$k")) return null }
        io.execute {
            val bmp = runCatching { BitmapFactory.decodeFile(file(host).path) }.getOrNull()
            synchronized(missing) { inFlight.remove("disk:$k"); if (bmp == null) missing += k }
            if (bmp != null) loaded[k] = bmp.asImageBitmap()
        }
        return null
    }

    /** La pagina [host] dichiara l'icona [url]: si scarica se non c'è o è vecchia. */
    fun offer(context: Context, host: String, url: String, private: Boolean) {
        if (private || host.isBlank() || !::dir.isInitialized) return
        if (!url.startsWith("https://") && !url.startsWith("http://")) return
        val k = key(host)
        val f = file(host)
        if (f.isFile && System.currentTimeMillis() - f.lastModified() < REFRESH_MS) return
        synchronized(missing) { if (!inFlight.add(k)) return }
        val request = WebRequest.Builder(url).addHeader("Accept", "image/png,image/*;q=0.8").build()
        runCatching { GeckoWebExecutor(Gecko.runtime(context)).fetch(request, GeckoWebExecutor.FETCH_FLAGS_ANONYMOUS) }
            .getOrNull()
            ?.accept({ response ->
                io.execute {
                    runCatching {
                        val body = response?.body ?: return@runCatching
                        val bytes = body.use { input ->
                            val out = java.io.ByteArrayOutputStream()
                            val buf = ByteArray(16 * 1024)
                            while (out.size() <= MAX_BYTES) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n) }
                            out.toByteArray()
                        }
                        if (response.statusCode !in 200..299 || bytes.size > MAX_BYTES) return@runCatching
                        val icon = decode(bytes) ?: return@runCatching
                        val small = Bitmap.createScaledBitmap(icon, SIZE, SIZE, true)
                        f.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        synchronized(missing) { missing.remove(k) }
                        loaded[k] = small.asImageBitmap()
                    }
                    synchronized(missing) { inFlight.remove(k) }
                }
            }, { synchronized(missing) { inFlight.remove(k) } })
            ?: synchronized(missing) { inFlight.remove(k) }
    }

    /** PNG, JPEG, WebP e, da Android 9, anche le .ico. */
    private fun decode(bytes: ByteArray): Bitmap? {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return it }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { d, _, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                d.setTargetSize(SIZE, SIZE)
            }
        }.getOrNull()
    }
}

/** L'icona del sito in un quadratino arrotondato, o [fallback] (l'iniziale) se non c'è. */
@Composable
internal fun SiteIcon(url: String, size: Dp, fallback: @Composable () -> Unit) {
    val host = Links.host(url)
    val icon = SiteIcons.get(host)
    if (icon != null) Image(icon, null, Modifier.size(size).clip(RoundedCornerShape(size / 4)))
    else fallback()
}

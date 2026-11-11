package eu.eurise.ai

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import java.io.File
import java.util.Locale

/** Quello che serve a leggere una misura: telefono, processore, memoria. */
internal object Device {

    fun name(): String {
        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } else null
        return listOfNotNull("${Build.MANUFACTURER} ${Build.MODEL}", soc).joinToString(" · ")
    }

    fun cores() = Runtime.getRuntime().availableProcessors()

    fun totalRam(context: Context) = memInfo(context).totalMem

    fun freeRam(context: Context) = memInfo(context).availMem

    private fun memInfo(context: Context) =
        ActivityManager.MemoryInfo().also { (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it) }

    /** Memoria davvero occupata dall'app (VmRSS), compresa la parte del modello già letta dal file. */
    fun rss(): Long = runCatching {
        File("/proc/self/status").readLines().first { it.startsWith("VmRSS:") }.filter { it.isDigit() }.toLong() * 1024
    }.getOrDefault(0)

    fun size(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(Locale.ITALY, "%.1f GB", bytes / (1L shl 30).toDouble())
        else -> String.format(Locale.ITALY, "%d MB", bytes / (1L shl 20))
    }

    fun num(x: Double, decimals: Int = 1) = String.format(Locale.ITALY, "%.${decimals}f", x)
}

/** Il file del modello nella memoria dell'app: una copia sola, scelta dall'utente. */
internal object ModelFile {

    fun file(context: Context) = File(context.filesDir, "models/model.gguf")

    /**
     * Copia il modello scelto. Controlla prima che sia un GGUF; [progress] riceve la percentuale.
     * Restituisce false se il file non è un modello.
     */
    fun import(context: Context, uri: Uri, progress: (Int) -> Unit): Boolean {
        val resolver = context.contentResolver
        val total = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length }?.takeIf { it > 0 } ?: -1L
        val magic = ByteArray(4)
        resolver.openInputStream(uri)?.use { it.read(magic) } ?: return false
        if (String(magic, Charsets.US_ASCII) != "GGUF") return false
        val dest = file(context)
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, "model.part")
        resolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(1 shl 20)
                var done = 0L
                var last = -1
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val p = (done * 100 / total).toInt()
                        if (p != last) progress(p).also { last = p }
                    }
                }
            }
        } ?: return false
        dest.delete()
        return tmp.renameTo(dest)
    }
}

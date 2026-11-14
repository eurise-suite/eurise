package eu.eurise.voice.speech

import android.content.Context
import eu.eurise.voice.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Le voci neurali di Alfred (Piper, licenza MIT; motore sherpa-onnx), scaricate a richiesta. Ogni voce è un archivio
 * preparato dalla suite (`.github/workflows/voci.yml`) con il modello, i simboli e i dati di pronuncia italiani di
 * espeak-ng. Si scarica solo toccando «Scarica»: escono la richiesta del file e l'indirizzo di rete, nient'altro.
 */
enum class Voice(val id: String, val onnx: String, val megabytes: Int, val label: Int, val detail: Int) {
    /** Maschile, pacata: la voce di Alfred. */
    Riccardo("alfred-riccardo", "it_IT-riccardo-x_low.onnx", 19, R.string.voice_tts_riccardo, R.string.voice_tts_riccardo_d),
    /** Femminile, più ricca: per chi la preferisce. */
    Paola("alfred-paola", "it_IT-paola-medium.onnx", 60, R.string.voice_tts_paola, R.string.voice_tts_paola_d);

    val url get() = "https://github.com/eurise-suite/eurise/releases/download/voci/$id.zip"

    fun dir(context: Context) = File(File(context.filesDir, "voices"), id)
    fun pack(context: Context) = VoicePack(id, null, dir(context), onnx)
    fun installed(context: Context) = pack(context).installed
}

/**
 * Una voce sul telefono: una delle nostre ([Voice]) o una caricata dalla persona ([name] è il nome scelto, null per le
 * nostre). Tre cose servono al motore: il modello, i simboli e i dati di pronuncia di espeak-ng.
 */
data class VoicePack(val id: String, val name: String?, val dir: File, val onnx: String) {
    val model get() = File(dir, onnx)
    val tokens get() = File(dir, "tokens.txt")
    val data get() = File(dir, "espeak-ng-data")
    val installed get() = model.isFile && tokens.isFile && data.isDirectory
    val imported get() = name != null
}

/**
 * Come parla Riccardo: com'è, o ritoccato «da maggiordomo» (più lento e più grave). Il tono si abbassa dichiarando
 * all'audio una frequenza più bassa di quella vera ([pitch] < 1: voce più grave e più lenta insieme); la velocità di
 * Piper si corregge perché il passo finale sia [speed]. Niente librerie e niente download: costa zero.
 */
data class VoiceTuning(val retouched: Boolean, val speed: Float, val pitch: Float) {
    /** Pausa in più tra una frase e l'altra, in millisecondi. */
    val pauseMs: Int get() = if (retouched) 220 else 0

    companion object {
        val PLAIN = VoiceTuning(false, 1f, 1f)
        /** Il ritocco proposto: passo all'85%, tono circa un semitono e mezzo sotto. */
        val BUTLER = VoiceTuning(true, 0.85f, 0.92f)
        val SPEED = 0.70f..1.10f
        val PITCH = 0.84f..1.04f
    }
}

object Voices {
    const val HOST = "github.com"
    private const val PREFS = "voices"
    private const val KEY_CHOSEN = "chosen"

    /** Tutte le voci sul telefono: le nostre scaricate e quelle caricate dalla persona. */
    fun all(context: Context): List<VoicePack> =
        Voice.entries.map { it.pack(context) }.filter { it.installed } + imported(context)

    /**
     * La voce in uso: quella scelta se c'è, altrimenti la prima sul telefono. La scelta sta in un file perché la legge
     * anche il processo «:voce».
     */
    fun current(context: Context): VoicePack? {
        val chosen = runCatching { flag(context, "scelta").readText().trim() }.getOrNull()
            ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_CHOSEN, null)
        val all = all(context)
        return all.firstOrNull { it.id == chosen } ?: all.firstOrNull()
    }

    fun choose(context: Context, id: String) {
        runCatching { flag(context, "scelta").writeText(id) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_CHOSEN, id).apply()
    }

    fun choose(context: Context, voice: Voice) = choose(context, voice.id)

    fun delete(context: Context, voice: Voice) = voice.dir(context).deleteRecursively().also { clearStopped(context) }

    fun delete(context: Context, pack: VoicePack) = pack.dir.deleteRecursively().also { clearStopped(context) }

    // ---- Voci caricate dalla persona -------------------------------------------------------------------------------

    private fun importedRoot(context: Context) = File(File(context.filesDir, "voices"), "caricate")

    fun imported(context: Context): List<VoicePack> =
        importedRoot(context).listFiles().orEmpty().filter { it.isDirectory && !it.name.endsWith(".new") }.mapNotNull { d ->
            val onnx = File(d, "modello.txt").takeIf { it.isFile }?.readText()?.trim() ?: return@mapNotNull null
            val name = File(d, "nome.txt").takeIf { it.isFile }?.readText()?.trim() ?: d.name
            VoicePack("caricata-" + d.name, name, d, onnx).takeIf { it.installed }
        }.sortedBy { it.name.orEmpty().lowercase() }

    enum class ImportResult { Ok, NotAVoice, NeedsPronunciation, Failed }

    /**
     * Carica una voce da un archivio .zip nel formato di sherpa-onnx (modello .onnx con i suoi dati, tokens.txt; i dati
     * di pronuncia di espeak-ng se ci sono, altrimenti si prendono da una nostra voce già scaricata). Tutto sul
     * telefono: il file non va da nessuna parte.
     */
    fun importZip(context: Context, zip: java.io.InputStream, displayName: String): ImportResult {
        val root = importedRoot(context).apply { mkdirs() }
        val id = System.currentTimeMillis().toString()
        val part = File(root, "$id.zip.part")
        val unpack = File(root, "$id.new")
        return runCatching {
            part.outputStream().use { zip.copyTo(it) }
            unzip(part, unpack)
            // Il modello può stare in una cartella dentro l'archivio (come negli archivi di sherpa-onnx).
            val model = unpack.walk().filter { it.isFile && it.name.endsWith(".onnx") }.minByOrNull { it.path.length }
                ?: return@runCatching ImportResult.NotAVoice
            val home = model.parentFile ?: return@runCatching ImportResult.NotAVoice
            if (!File(home, "tokens.txt").isFile) return@runCatching ImportResult.NotAVoice
            if (!File(home, "espeak-ng-data").isDirectory) {
                val donor = Voice.entries.map { it.pack(context) }.firstOrNull { it.installed } ?: return@runCatching ImportResult.NeedsPronunciation
                donor.data.copyRecursively(File(home, "espeak-ng-data"), overwrite = true)
            }
            File(home, "modello.txt").writeText(model.name)
            File(home, "nome.txt").writeText(displayName.substringBeforeLast('.').ifBlank { model.nameWithoutExtension })
            val target = File(root, id)
            check(home.renameTo(target))
            ImportResult.Ok
        }.getOrElse { ImportResult.Failed }.also {
            part.delete()
            unpack.deleteRecursively()
        }
    }

    /*
     * La spia della voce. La sintesi gira in un processo a parte (":voce"): se il motore nativo si ferma di colpo, chiude
     * solo quel processo. Il file «parla» si scrive prima di ogni frase e si toglie dopo; se al riavvio c'è ancora, la
     * voce si è fermata parlando e diventa «ferma»: Alfred passa alla voce del telefono invece di restare muto, finché
     * «Ascolta una prova» non la rimette in uso. File e non preferenze, perché li leggono due processi.
     */
    private fun flag(context: Context, name: String) = File(File(context.filesDir, "voices").apply { mkdirs() }, name)
    fun speakingStart(context: Context) = runCatching { flag(context, "parla").writeText("1") }
    fun speakingEnd(context: Context) = runCatching { flag(context, "parla").delete() }
    /** Al riavvio del motore: se l'ultima frase non è finita, la voce è ferma. */
    fun checkCrash(context: Context) = runCatching {
        if (flag(context, "parla").exists()) { flag(context, "parla").delete(); flag(context, "ferma").writeText("1") }
    }
    fun stopped(context: Context) = flag(context, "ferma").exists()

    /** Il ritocco della voce: in un file perché lo legge anche il processo «:voce». */
    fun tuning(context: Context): VoiceTuning = runCatching {
        val p = flag(context, "stile").readText().trim().split(' ')
        if (p.size < 3) VoiceTuning.PLAIN
        else VoiceTuning(p[0] == "1", p[1].toFloat().coerceIn(VoiceTuning.SPEED), p[2].toFloat().coerceIn(VoiceTuning.PITCH))
    }.getOrDefault(VoiceTuning.PLAIN)

    fun setTuning(context: Context, t: VoiceTuning) = runCatching {
        flag(context, "stile").writeText("${if (t.retouched) 1 else 0} ${t.speed} ${t.pitch}")
    }
    fun clearStopped(context: Context) = runCatching { flag(context, "ferma").delete() }

    /** Scarica l'archivio in una copia temporanea, lo apre in una cartella nuova e la mette al suo posto solo se è intera. */
    fun download(context: Context, voice: Voice, progress: (Int) -> Unit): Boolean {
        val root = File(context.filesDir, "voices").apply { mkdirs() }
        val part = File(root, voice.id + ".zip.part")
        val unpack = File(root, voice.id + ".new")
        return runCatching {
            var url = URL(voice.url)
            var connection: HttpURLConnection
            var redirects = 0
            while (true) {
                connection = url.openConnection() as HttpURLConnection
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setRequestProperty("User-Agent", "EuriseVoce")
                val code = connection.responseCode
                if (code in 300..399 && redirects < 6) {
                    url = URL(url, connection.getHeaderField("Location")); redirects++; connection.disconnect(); continue
                }
                if (code != 200) error("HTTP $code")
                break
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var last = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        val pct = if (total > 0) (done * 95 / total).toInt() else 0
                        if (pct != last) { last = pct; progress(pct) }
                    }
                }
            }
            if (total > 0 && part.length() != total) error("download incompleto")
            unpack.deleteRecursively()
            unzip(part, unpack)
            val target = voice.dir(context)
            target.deleteRecursively()
            check(unpack.renameTo(target))
            clearStopped(context)
            progress(100)
            voice.installed(context)
        }.getOrElse { false }.also {
            part.delete()
            unpack.deleteRecursively()
        }
    }

    /** Apre l'archivio dentro [into], rifiutando i nomi che uscirebbero dalla cartella. */
    private fun unzip(zip: File, into: File) {
        into.mkdirs()
        val base = into.canonicalPath + File.separator
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val out = File(into, e.name)
                check(out.canonicalPath.startsWith(base)) { "nome non valido" }
                if (e.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { z.copyTo(it) }
                }
            }
        }
    }
}

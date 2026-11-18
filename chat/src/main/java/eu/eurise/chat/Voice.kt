package eu.eurise.chat

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Messaggi vocali (tavola ChatVocali), logica senza Android: si prova con i test.
 *
 * Un vocale è un `m.audio` con i campi che usano Element e le altre app Matrix: `org.matrix.msc3245.voice` (è un vocale,
 * non un brano) e `org.matrix.msc1767.audio` con durata in millisecondi e forma d'onda (fino a [WAVE_BARS] valori tra 0 e
 * 1024, MSC3246). La trascrizione si fa sul telefono (Eurise Voce, Whisper) e non si manda: chi riceve trascrive da sé.
 */
object Voice {
    const val VOICE = "org.matrix.msc3245.voice"
    const val AUDIO = "org.matrix.msc1767.audio"
    const val WAVE_BARS = 64
    const val WAVE_MAX = 1024

    /** Campioni al secondo della registrazione e di quello che si dà a Whisper. */
    const val RATE = 16_000
    /** Sotto un secondo il vocale non parte (tocco sfuggito). */
    const val MIN_MS = 1_000L
    /** Al massimo dieci minuti. */
    const val MAX_MS = 600_000L

    /** Whisper ascolta al massimo 30 secondi per volta: i pezzi più lunghi si tagliano nel punto più quieto. */
    const val PIECE_MAX_S = 28
    const val PIECE_MIN_S = 18

    /** Dati di un audio letti dal messaggio. */
    data class Info(val durationMs: Long, val wave: List<Int>, val voice: Boolean, val mime: String)

    fun info(content: JSONObject?): Info {
        content ?: return Info(0, emptyList(), false, "")
        val i = content.optJSONObject("info")
        val a = content.optJSONObject(AUDIO)
        val duration = (i?.optLong("duration") ?: 0L).takeIf { it > 0 } ?: (a?.optLong("duration") ?: 0L)
        val w = a?.optJSONArray("waveform")
        val wave = if (w == null) emptyList() else (0 until w.length()).map { w.optInt(it).coerceIn(0, WAVE_MAX) }
        return Info(duration.coerceAtLeast(0), wave, content.has(VOICE), i?.optString("mimetype").orEmpty())
    }

    /** Il contenuto del messaggio: [file] (stanze cifrate, vedi Attachments) oppure [url] in chiaro. */
    fun content(body: String, mime: String, size: Int, durationMs: Long, wave: List<Int>, file: JSONObject?, url: String?): JSONObject {
        val c = JSONObject().put("msgtype", "m.audio").put("body", body)
            .put("info", JSONObject().put("mimetype", mime).put("size", size).put("duration", durationMs))
            .put(AUDIO, JSONObject().put("duration", durationMs).put("waveform", JSONArray(wave)))
            .put(VOICE, JSONObject())
        if (file != null) c.put("file", file) else if (url != null) c.put("url", url)
        return c
    }

    /** La forma d'onda: [bars] valori tra 0 e 1024, energia di ogni tratto rispetto al tratto più forte. */
    fun waveform(pcm: ShortArray, bars: Int = WAVE_BARS): List<Int> {
        if (pcm.isEmpty() || bars <= 0) return emptyList()
        val n = minOf(bars, pcm.size)
        val rms = DoubleArray(n) { b ->
            val from = (b.toLong() * pcm.size / n).toInt()
            val to = ((b + 1).toLong() * pcm.size / n).toInt().coerceAtLeast(from + 1)
            var sum = 0.0
            for (k in from until to) { val v = pcm[k] / 32768.0; sum += v * v }
            sqrt(sum / (to - from))
        }
        val peak = rms.max()
        if (peak <= 0.0) return List(n) { 0 }
        return rms.map { (it / peak * WAVE_MAX).roundToInt().coerceIn(0, WAVE_MAX) }
    }

    /**
     * Le sbarrette da disegnare: [count] valori tra 0,15 e 1 dalla forma d'onda del messaggio (o piatte se manca), così
     * un vocale di Element e uno nostro si vedono uguali.
     */
    fun bars(wave: List<Int>, count: Int): List<Float> {
        if (count <= 0) return emptyList()
        if (wave.isEmpty()) return List(count) { 0.3f }
        return List(count) { i ->
            val from = i * wave.size / count
            val to = ((i + 1) * wave.size / count).coerceAtLeast(from + 1).coerceAtMost(wave.size)
            val v = (from until to).maxOf { wave[it] } / WAVE_MAX.toFloat()
            0.15f + 0.85f * v.coerceIn(0f, 1f)
        }
    }

    /** «0:42», «12:05». */
    fun duration(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 500) / 1000
        return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }

    /**
     * Dove tagliare l'audio per Whisper: pezzi tra [PIECE_MIN_S] e [PIECE_MAX_S] secondi, ognuno finito nel tratto di
     * 100 ms più quieto di quella finestra (di solito una pausa tra due frasi). Restituisce gli intervalli [inizio, fine).
     */
    fun pieces(pcm: ShortArray, rate: Int = RATE, minS: Int = PIECE_MIN_S, maxS: Int = PIECE_MAX_S): List<IntRange> {
        if (pcm.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>()
        val block = rate / 10
        var start = 0
        while (pcm.size - start > maxS * rate) {
            var best = start + maxS * rate
            var quiet = Double.MAX_VALUE
            var at = start + minS * rate
            while (at + block <= start + maxS * rate) {
                var sum = 0.0
                for (k in at until at + block) { val v = pcm[k].toDouble(); sum += v * v }
                if (sum < quiet) { quiet = sum; best = at + block / 2 }
                at += block
            }
            out += start until best
            start = best
        }
        out += start until pcm.size
        return out
    }

    /**
     * Da un audio qualsiasi (PCM a 16 bit, [channels] canali intrecciati, [rate] campioni al secondo) a mono a 16 kHz,
     * come lo vuole Whisper: media dei canali e interpolazione lineare.
     */
    fun toMono16k(samples: ShortArray, channels: Int, rate: Int): ShortArray {
        val ch = channels.coerceAtLeast(1)
        val frames = samples.size / ch
        val mono = if (ch == 1) samples.copyOf(frames) else ShortArray(frames) { f ->
            var s = 0
            for (c in 0 until ch) s += samples[f * ch + c]
            (s / ch).toShort()
        }
        if (rate == RATE || rate <= 0 || frames == 0) return mono
        val outN = (frames.toLong() * RATE / rate).toInt()
        return ShortArray(outN) { i ->
            val pos = i.toDouble() * rate / RATE
            val a = pos.toInt().coerceAtMost(frames - 1)
            val b = (a + 1).coerceAtMost(frames - 1)
            val t = pos - a
            (mono[a] * (1 - t) + mono[b] * t).roundToInt().toShort()
        }
    }

    /** PCM a 16 bit little-endian (come lo legge Voce) da campioni. */
    fun bytes(pcm: ShortArray, range: IntRange = pcm.indices): ByteArray {
        val out = ByteArray(range.count() * 2)
        var j = 0
        for (k in range) { val v = pcm[k].toInt(); out[j++] = v.toByte(); out[j++] = (v shr 8).toByte() }
        return out
    }

    /** Il testo da leggere ad alta voce per un messaggio arrivato: «Anna: ci vediamo alle otto». */
    fun spoken(name: String, text: String): String = if (name.isBlank()) text.trim() else "$name: ${text.trim()}"
}

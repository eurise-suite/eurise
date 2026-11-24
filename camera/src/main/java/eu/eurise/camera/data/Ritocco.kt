package eu.eurise.camera.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import eu.eurise.camera.TAG
import java.io.File
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

/**
 * Ritocco dopo lo scatto (22 novembre 2026), tutto sul telefono e senza librerie, per le foto di sera in casa:
 * - **Colori**: dominanti forti (gialla delle lampade, rosata della frontale: canale più alto oltre [THRESHOLD] volte il
 *   più basso sulle superfici chiare) corrette a metà verso il neutro. Prima si guardava tutta la foto e un albero verde
 *   faceva virare tutto al ciano (23 novembre).
 * - **Macchioline di colore** (la parte più brutta della grana) da [NOISY_ISO] in su.
 * - **Toni medi locali** quando la mediana è sotto [DARK]: le ombre si aprono zona per zona verso [TARGET_MEDIAN], al
 *   massimo di 1,6 volte (1,3 con molto rumore), le luci non si toccano.
 * Le foto che non ne hanno bisogno non si ricodificano. I dati dello scatto (EXIF, anche la posizione se accesa) si ricopiano.
 */
object Ritocco {
    const val THRESHOLD = 1.15
    private const val STRENGTH = 0.5
    private const val MAX_CHANNEL = 1.6f
    private const val MIN_CHANNEL = 0.8f
    const val DARK = 85
    const val TARGET_MEDIAN = 95
    private const val MIN_GAMMA = 0.8
    /** Da quanti ISO in su si tolgono le macchioline di colore. */
    const val NOISY_ISO = 400
    private const val MAX_GAIN = 1.6f
    private const val MAX_GAIN_NOISY = 1.3f
    private const val VERY_NOISY_ISO = 1600
    /** Da qui in su si spiana la grana della luminosità (23 novembre: a ISO 2200 la grana era il difetto principale). */
    const val GRAIN_ISO = 800
    private const val GRAIN_STRENGTH = 0.8f
    /** Nitidezza solo per le foto fuse da almeno tanti fotogrammi. */
    const val SHARPEN_FRAMES = 3
    private const val SHARPEN_AMOUNT = 0.5f
    const val SHARPEN_CORE = 4f
    /** Sotto questo indice di grana la foto fusa è pulita e non si filtra. */
    const val NOISE_OFF = 1.4f
    private const val SHADOW_START = 80f
    private const val SHADOW_FULL = 20f
    /** Nelle ombre più profonde il colore cala di tanto (le macchie di colore lì sono quasi solo rumore). */
    private const val SHADOW_DESATURATE = 0.35f

    /**
     * Guadagni rosso e blu per i valori medi [r], [g], [b], o null se i colori vanno bene così. Vale per ogni dominante:
     * gialla e arancione delle lampade (rosso alto, blu basso) e rosata o magenta della fotocamera frontale (rosso e blu
     * alti sul verde). Si scala rosso e blu verso il verde, a metà strada.
     */
    fun gains(r: Double, g: Double, b: Double): Pair<Float, Float>? {
        if (r <= 0 || g <= 0 || b <= 0) return null
        if (maxOf(r, g, b) / minOf(r, g, b) < THRESHOLD) return null
        val gr = (g / r).pow(STRENGTH).toFloat().coerceIn(MIN_CHANNEL, MAX_CHANNEL)
        val gb = (g / b).pow(STRENGTH).toFloat().coerceIn(MIN_CHANNEL, MAX_CHANNEL)
        return gr to gb
    }

    /** Esponente della curva dei toni per la mediana [median] (0-255), o null se la foto non è scura. */
    fun gamma(median: Int): Double? {
        if (median <= 0 || median >= DARK) return null
        return (ln(TARGET_MEDIAN / 255.0) / ln(median / 255.0)).coerceIn(MIN_GAMMA, 1.0)
    }

    /** Corregge la foto a [uri] (JPEG) se serve. Restituisce true se l'ha cambiata. Mai un errore verso chi chiama. */
    fun correct(resolver: ContentResolver, uri: Uri, cache: File): Boolean = runCatching {
        val src = File(cache, "ritocco-in.jpg"); val out = File(cache, "ritocco-out.jpg")
        resolver.openInputStream(uri)?.use { input -> src.outputStream().use { input.copyTo(it) } } ?: return false
        val done = correctFile(src, out)
        if (done) resolver.openOutputStream(uri, "wt")?.use { sink -> out.inputStream().use { it.copyTo(sink) } }
        src.delete(); out.delete()
        done
    }.onFailure { Log.w(TAG, "Ritocco non riuscito", it) }.getOrDefault(false)

    /** Come [correct], da file a file: [out] si scrive solo se serve una correzione. */
    fun correctFile(src: File, out: File): Boolean {
        val iso = runCatching { ExifInterface(src.path).getAttributeInt("ISOSpeedRatings", 0) }.getOrDefault(0)
        val bitmap = BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inMutable = true }) ?: return false
        try {
            if (!process(bitmap, iso)) return false
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        } finally {
            bitmap.recycle()
        }
        copyExif(src, out, "Eurise Fotocamera · ritocco · ISO $iso")
        return true
    }

    /**
     * Il ritocco sul posto: colori, macchioline di colore (da [NOISY_ISO] in su: il colore si media su blocchi di 4×4
     * pixel, la luminosità resta piena, quindi niente dettaglio perso) e toni medi locali (la luce media della zona, a
     * scala di qualche decina di pixel, decide quanto schiarire: le ombre si aprono, la tenda chiara non si brucia).
     * Tre passate riga per riga, poca memoria oltre la foto. Restituisce false se non c'era niente da fare.
     */
    fun process(bitmap: Bitmap, iso: Int, frames: Int = 1): Boolean {
        val w = bitmap.width; val h = bitmap.height
        val row = IntArray(w)
        // 1. Colori dalle superfici chiare (muri, tende, fogli: tra il 75° e il 97° percentile della luminosità, luci
        // bruciate escluse), non dalla media di tutto: un albero verde o una sedia blu non devono spostare i colori.
        // Una riga e un pixel su quattro.
        val hist = IntArray(256); var all = 0
        var y = 0
        while (y < h) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            var x = 0
            while (x < w) {
                val c = row[x]
                if (maxOf(c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF) < 240) { hist[luma8(c)]++; all++ }
                x += 4
            }
            y += 4
        }
        if (all < 1000) return false
        val lo = percentile(hist, all, 0.75); val hi = percentile(hist, all, 0.97)
        var sr = 0.0; var sg = 0.0; var sb = 0.0; var n = 0
        y = 0
        while (y < h) {
            bitmap.getPixels(row, 0, w, 0, y, w, 1)
            var x = 0
            while (x < w) {
                val c = row[x]
                val r = c shr 16 and 0xFF; val g = c shr 8 and 0xFF; val b = c and 0xFF
                if (maxOf(r, g, b) < 240 && luma8(c) in lo..hi) { sr += r; sg += g; sb += b; n++ }
                x += 4
            }
            y += 4
        }
        val wb = if (n < 200) null else gains(sr / n, sg / n, sb / n)
        val (gr, gb) = wb ?: (1f to 1f)
        val chroma = iso >= NOISY_ISO

        // 2. Griglie: colore a 1/4, luminosità a 1/8 (dopo i colori corretti).
        val cw = (w + 3) / 4; val ch = (h + 3) / 4
        val lw = (w + 7) / 8; val lh = (h + 7) / 8
        val cb = FloatArray(cw * ch); val cr = FloatArray(cw * ch); val cc = IntArray(cw * ch)
        val lum = FloatArray(lw * lh); val lc = IntArray(lw * lh)
        for (yy in 0 until h) {
            bitmap.getPixels(row, 0, w, 0, yy, w, 1)
            val crow = (yy shr 2) * cw; val lrow = (yy shr 3) * lw
            for (x in 0 until w) {
                val c = row[x]
                val r = (c shr 16 and 0xFF) * gr; val g = (c shr 8 and 0xFF).toFloat(); val b = (c and 0xFF) * gb
                val l = 0.299f * r + 0.587f * g + 0.114f * b
                val ci = crow + (x shr 2)
                cb[ci] += b - l; cr[ci] += r - l; cc[ci]++
                val li = lrow + (x shr 3)
                lum[li] += l; lc[li]++
            }
        }
        for (i in cb.indices) if (cc[i] > 0) { cb[i] /= cc[i]; cr[i] /= cc[i] }
        for (i in lum.indices) if (lc[i] > 0) lum[i] /= lc[i]
        val median = lum.copyOf().also { it.sort() }[lum.size / 2].toInt()
        val curve = gamma(median)
        // Dopo la Notte la grana rimasta si misura sulla foto stessa (24 novembre: armadio a ISO 2745, 6 fotogrammi,
        // grana ancora visibile; l'albero a ISO 2153 era pulito con la stessa formula basata sugli ISO).
        val noise = if (frames > 1) noiseIndex(bitmap) else 0f
        val grain = if (frames > 1) noiseThreshold(noise) else grainThreshold(iso, frames)
        val crisp = frames >= SHARPEN_FRAMES && grain == 0
        if (wb == null && !chroma && curve == null && grain == 0 && !crisp) return false
        // Con molto rumore si schiarisce meno: schiarire mostra la grana.
        // Dopo la Notte conta il rumore rimasto: gli ISO divisi per i fotogrammi.
        val maxGain = if (iso / frames.coerceAtLeast(1) >= VERY_NOISY_ISO) MAX_GAIN_NOISY else MAX_GAIN
        if (chroma) { boxBlur(cb, cw, ch, 1); boxBlur(cr, cw, ch, 1) }
        // Per le ombre un colore ancora più levigato (blocchi di circa 30 pixel): lì le macchie rosse e verdi sono
        // larghe e il colore vero è poco (23 novembre, scaffali sotto la TV).
        val cbWide = if (chroma) cb.copyOf().also { boxBlur(it, cw, ch, 3); boxBlur(it, cw, ch, 3) } else null
        val crWide = if (chroma) cr.copyOf().also { boxBlur(it, cw, ch, 3); boxBlur(it, cw, ch, 3) } else null
        if (curve != null) { boxBlur(lum, lw, lh, 6); boxBlur(lum, lw, lh, 6) }
        // Guadagno dei toni in funzione della luce della zona (0-255, a passi di 1/4).
        val gainLut = FloatArray(1024) { i ->
            val l = maxOf(i / 4f, 1f)
            if (curve == null) return@FloatArray 1f
            val g = (255.0 * (l / 255.0).pow(curve) / l).toFloat().coerceIn(1f, maxGain)
            // Le luci restano dove sono: il guadagno si spegne tra 80 e 180.
            1f + (g - 1f) * ((180f - l) / 100f).coerceIn(0f, 1f)
        }

        // 3. Ricomposizione: luminosità piena del pixel, colore dalla griglia (o dal pixel), guadagno dalla zona.
        val cx = Interp(w, 4, cw); val lx = Interp(w, 8, lw)
        // Righe di luminosità originale da yy-2 a yy+2 per la grana (la riga yy+2 non è ancora riscritta).
        val win = if (grain > 0 || crisp) Array(5) { IntArray(w) } else null
        val tmp = IntArray(w)
        fun lumaRow(yy: Int, into: IntArray) {
            bitmap.getPixels(tmp, 0, w, 0, yy.coerceIn(0, h - 1), w, 1)
            for (x in 0 until w) into[x] = luma8(tmp[x])
        }
        if (win != null) for (i in 0 until 5) lumaRow(i - 2, win[i])
        for (yy in 0 until h) {
            if (win != null && yy > 0) {
                val first = win[0]
                for (i in 0 until 4) win[i] = win[i + 1]
                win[4] = first
                lumaRow(yy + 2, first)
            }
            bitmap.getPixels(row, 0, w, 0, yy, w, 1)
            val cy = Interp.at(yy, 4, ch); val ly = Interp.at(yy, 8, lh)
            for (x in 0 until w) {
                val c = row[x]
                val r = (c shr 16 and 0xFF) * gr; val g = (c shr 8 and 0xFF).toFloat(); val b = (c and 0xFF) * gb
                var l = 0.299f * r + 0.587f * g + 0.114f * b
                var u = b - l; var v = r - l
                if (win != null) {
                    if (grain > 0) l += GRAIN_STRENGTH * smooth(win, x, w, grain)
                    // Nelle ombre niente nitidezza: tirerebbe fuori la grana.
                    if (crisp) l += SHARPEN_AMOUNT * (1f - shadow(l)) * detail(win, x, w)
                }
                if (chroma) {
                    u = cx.sample(cb, cw, cy, x); v = cx.sample(cr, cw, cy, x)
                    val t = shadow(l)
                    if (t > 0f) {
                        // Nelle ombre: colore della zona larga e un po' meno saturo.
                        val fade = 1f - SHADOW_DESATURATE * t
                        u = (u + (cx.sample(cbWide!!, cw, cy, x) - u) * t) * fade
                        v = (v + (cx.sample(crWide!!, cw, cy, x) - v) * t) * fade
                    }
                }
                var k = 1f
                if (curve != null) {
                    val zone = 0.5f * lx.sample(lum, lw, ly, x) + 0.5f * l
                    k = gainLut[(zone * 4f).toInt().coerceIn(0, 1023)]
                }
                val l2 = l * k; val u2 = u * k; val v2 = v * k
                val r2 = l2 + v2; val b2 = l2 + u2; val g2 = (l2 - 0.299f * r2 - 0.114f * b2) / 0.587f
                row[x] = (c and 0xFF000000.toInt()) or (clamp(r2) shl 16) or (clamp(g2) shl 8) or clamp(b2)
            }
            bitmap.setPixels(row, 0, w, 0, yy, w, 1)
        }
        Log.i(TAG, "Ritocco: colori ${wb != null}, rumore di colore $chroma (ISO $iso), grana $grain (indice $noise), mediana $median, curva $curve")
        return true
    }

    /**
     * Quanta grana c'è: la mediana di |pixel − media dei 3×3 vicini| tra i toni medi (luminosità 60-160), su una riga ogni
     * otto. La mediana ignora i contorni. Tarata sulle foto di Sun: ~1 pulita, ~2 grana visibile, 4-5 una foto sola a ISO 2000.
     */
    internal fun noiseIndex(bitmap: Bitmap): Float {
        val w = bitmap.width; val h = bitmap.height
        if (w < 3 || h < 3) return 0f
        val rows = Array(3) { IntArray(w) }
        val hist = IntArray(256); var n = 0
        var y = 1
        while (y < h - 1) {
            for (k in 0 until 3) {
                bitmap.getPixels(rows[k], 0, w, 0, y - 1 + k, w, 1)
                for (x in 0 until w) rows[k][x] = luma8(rows[k][x])
            }
            var x = 1
            while (x < w - 1) {
                val c = rows[1][x]
                if (c in 60..160) {
                    var sum = 0
                    for (k in 0 until 3) sum += rows[k][x - 1] + rows[k][x] + rows[k][x + 1]
                    val d = abs(c * 9 - sum) * 4 / 9
                    hist[minOf(d, 255)]++; n++
                }
                x += 2
            }
            y += 8
        }
        if (n < 1000) return 0f
        return percentile(hist, n, 0.5) / 4f
    }

    /** Soglia del filtro della grana dopo la Notte, dall'indice di [noiseIndex]: spento sotto 1,4, poi 6 volte l'indice. */
    internal fun noiseThreshold(index: Float): Int = if (index < NOISE_OFF) 0 else (6f * index).toInt().coerceAtMost(40)

    /**
     * Soglia del filtro della grana (0 = spento): da [GRAIN_ISO] in su, più alta con più ISO, divisa per i fotogrammi
     * fusi. Con la radice (23 novembre, giro 1099) dopo la Notte restava acceso e faceva l'effetto acquerello: la fusione
     * aveva già portato la grana da 14 a 3.
     */
    internal fun grainThreshold(iso: Int, frames: Int): Int {
        if (iso < GRAIN_ISO) return 0
        val t = minOf(12.0 + (iso - GRAIN_ISO) / 60.0, 40.0) / frames.coerceAtLeast(1)
        return if (t < 6) 0 else t.toInt()
    }

    /** Quanto è ombra un pixel di luminosità [l]: 0 da [SHADOW_START] in su, 1 sotto [SHADOW_FULL]. */
    internal fun shadow(l: Float): Float = ((SHADOW_START - l) / (SHADOW_START - SHADOW_FULL)).coerceIn(0f, 1f)

    /**
     * Nitidezza dopo la Notte: il pixel meno la media dei 5×5 vicini (la fusione ammorbidisce un poco), con le differenze
     * sotto [SHARPEN_CORE] lasciate stare, così la grana rimasta non torna fuori.
     */
    internal fun detail(win: Array<IntArray>, x: Int, w: Int): Float {
        val own = win[2][x]
        var sum = 0; var n = 0
        val x0 = maxOf(0, x - 2); val x1 = minOf(w - 1, x + 2)
        for (r in win) for (i in x0..x1) { sum += r[i]; n++ }
        val d = own - sum.toFloat() / n
        return when {
            d > SHARPEN_CORE -> d - SHARPEN_CORE
            d < -SHARPEN_CORE -> d + SHARPEN_CORE
            else -> 0f
        }
    }

    /**
     * Filtro sigma sulla luminosità: la media dei vicini 5×5 che differiscono dal pixel meno di [t] (gli altri sono un
     * bordo, e il bordo resta). Restituisce quanto spostare il pixel: la grana si spiana, i contorni no.
     */
    internal fun smooth(win: Array<IntArray>, x: Int, w: Int, t: Int): Float {
        val own = win[2][x]
        var sum = 0; var n = 0
        val x0 = maxOf(0, x - 2); val x1 = minOf(w - 1, x + 2)
        for (r in win) for (i in x0..x1) { val q = r[i]; if (abs(q - own) < t) { sum += q; n++ } }
        return sum.toFloat() / n - own
    }

    private fun luma8(c: Int): Int = ((c shr 16 and 0xFF) * 77 + (c shr 8 and 0xFF) * 150 + (c and 0xFF) * 29) shr 8

    internal fun percentile(hist: IntArray, all: Int, q: Double): Int {
        var acc = 0
        for (i in hist.indices) { acc += hist[i]; if (acc >= q * all) return i }
        return 255
    }

    private fun clamp(v: Float): Int = if (v <= 0f) 0 else if (v >= 255f) 255 else (v + 0.5f).toInt()

    /** Sfocatura a scatola separabile, raggio [r], sul posto. */
    internal fun boxBlur(a: FloatArray, w: Int, h: Int, r: Int) {
        val tmp = FloatArray(maxOf(w, h))
        for (y in 0 until h) {
            for (x in 0 until w) {
                var s = 0f; var c = 0
                for (k in -r..r) { val xx = x + k; if (xx in 0 until w) { s += a[y * w + xx]; c++ } }
                tmp[x] = s / c
            }
            for (x in 0 until w) a[y * w + x] = tmp[x]
        }
        for (x in 0 until w) {
            for (y in 0 until h) {
                var s = 0f; var c = 0
                for (k in -r..r) { val yy = y + k; if (yy in 0 until h) { s += a[yy * w + x]; c++ } }
                tmp[y] = s / c
            }
            for (y in 0 until h) a[y * w + x] = tmp[y]
        }
    }

    /** Interpolazione bilineare da una griglia ridotta di [f] volte: colonne precalcolate, riga con [at]. */
    private class Interp(w: Int, f: Int, gw: Int) {
        val x0 = IntArray(w); val x1 = IntArray(w); val fx = FloatArray(w)
        init {
            for (x in 0 until w) {
                val p = ((x + 0.5f) / f - 0.5f).coerceIn(0f, (gw - 1).toFloat())
                x0[x] = p.toInt(); x1[x] = minOf(x0[x] + 1, gw - 1); fx[x] = p - x0[x]
            }
        }
        fun sample(g: FloatArray, gw: Int, r: Row, x: Int): Float {
            val a = g[r.y0 * gw + x0[x]] * (1 - fx[x]) + g[r.y0 * gw + x1[x]] * fx[x]
            val b = g[r.y1 * gw + x0[x]] * (1 - fx[x]) + g[r.y1 * gw + x1[x]] * fx[x]
            return a * (1 - r.fy) + b * r.fy
        }
        class Row(val y0: Int, val y1: Int, val fy: Float)
        companion object {
            fun at(y: Int, f: Int, gh: Int): Row {
                val p = ((y + 0.5f) / f - 0.5f).coerceIn(0f, (gh - 1).toFloat())
                val y0 = p.toInt()
                return Row(y0, minOf(y0 + 1, gh - 1), p - y0)
            }
        }
    }

    private val EXIF_TAGS = listOf(
        "Make", "Model", "DateTime", "DateTimeOriginal", "DateTimeDigitized", "SubSecTime", "SubSecTimeOriginal",
        "SubSecTimeDigitized", "OffsetTime", "OffsetTimeOriginal", "OffsetTimeDigitized", "Orientation", "ExposureTime",
        "FNumber", "ISOSpeedRatings", "PhotographicSensitivity", "FocalLength", "FocalLengthIn35mmFilm", "Flash",
        "WhiteBalance", "LightSource", "ExposureProgram", "ExposureMode", "MeteringMode", "ApertureValue",
        "ShutterSpeedValue", "BrightnessValue", "ExposureBiasValue", "DigitalZoomRatio", "SceneCaptureType",
        "GPSLatitude", "GPSLatitudeRef", "GPSLongitude", "GPSLongitudeRef", "GPSAltitude", "GPSAltitudeRef",
        "GPSTimeStamp", "GPSDateStamp", "GPSProcessingMethod",
    )

    /**
     * Ricopia i dati dello scatto. [how] va in «Software»: come è stata fatta la foto (ritocco, Notte e fotogrammi), così
     * dalle foto di prova si capisce cosa è successo. Niente di personale, e resta anche quando si condivide.
     */
    internal fun copyExif(from: File, to: File, how: String) {
        runCatching {
            val a = ExifInterface(from.path); val b = ExifInterface(to.path)
            EXIF_TAGS.forEach { tag -> a.getAttribute(tag)?.let { b.setAttribute(tag, it) } }
            b.setAttribute("Software", how)
            b.saveAttributes()
        }.onFailure { Log.w(TAG, "Dati dello scatto non ricopiati", it) }
    }

}

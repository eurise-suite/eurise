package eu.eurise.gallery.logic

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Le regolazioni che non stanno in una matrice di colore (tavole GalRegolaTutto e GalCurve, approvate il 17 novembre):
 * atmosfera, ombre e alte luci, vividezza, curve, struttura e nitidezza. Lavorano sui pixel ARGB di un'immagine; la
 * matrice ([adjustMatrix]) si applica dopo, così anteprima e copia salvata danno lo stesso risultato.
 * Nessuna dipendenza da Android: si prova in `DevelopTest`.
 */

/** Un punto della curva: ingresso e uscita da 0 a 1. */
data class CurvePoint(val x: Float, val y: Float)

/** Le quattro curve: luminosità (su tutti e tre i canali) e una per colore. Vuota = diritta. */
data class Curves(
    val luma: List<CurvePoint> = emptyList(),
    val red: List<CurvePoint> = emptyList(),
    val green: List<CurvePoint> = emptyList(),
    val blue: List<CurvePoint> = emptyList(),
) {
    val isNeutral: Boolean get() = listOf(luma, red, green, blue).all(::isStraight)

    fun channel(c: CurveChannel) = when (c) { CurveChannel.Luma -> luma; CurveChannel.Red -> red; CurveChannel.Green -> green; CurveChannel.Blue -> blue }

    fun with(c: CurveChannel, points: List<CurvePoint>) = when (c) {
        CurveChannel.Luma -> copy(luma = points); CurveChannel.Red -> copy(red = points)
        CurveChannel.Green -> copy(green = points); CurveChannel.Blue -> copy(blue = points)
    }
}

enum class CurveChannel { Luma, Red, Green, Blue }

private fun isStraight(points: List<CurvePoint>) = points.all { abs(it.x - it.y) < 0.002f }

/** Curve pronte (tavola GalCurve): punti sulla curva della luminosità. */
enum class CurvePreset(val points: List<CurvePoint>) {
    None(emptyList()),
    SoftS(listOf(CurvePoint(0.25f, 0.2f), CurvePoint(0.75f, 0.8f))),
    StrongS(listOf(CurvePoint(0.25f, 0.15f), CurvePoint(0.75f, 0.87f))),
    Matte(listOf(CurvePoint(0f, 0.08f), CurvePoint(0.5f, 0.52f), CurvePoint(1f, 0.94f))),
    Cross(listOf(CurvePoint(0.3f, 0.24f), CurvePoint(0.7f, 0.78f))),
}

/**
 * La curva come tabella 0..255, morbida e sempre crescente (interpolazione monotona di Fritsch e Carlson).
 * Gli estremi (0,0) e (1,1) ci sono sempre, salvo che un punto li sposti.
 */
fun curveLut(points: List<CurvePoint>): IntArray {
    val pts = buildList {
        if (points.none { it.x <= 0.001f }) add(CurvePoint(0f, 0f))
        addAll(points.map { CurvePoint(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) })
        if (points.none { it.x >= 0.999f }) add(CurvePoint(1f, 1f))
    }.sortedBy { it.x }.distinctBy { (it.x * 1000).roundToInt() }
    val n = pts.size
    if (n < 2) return IntArray(256) { it }
    val xs = FloatArray(n) { pts[it].x }
    val ys = FloatArray(n) { pts[it].y }
    // Le uscite non scendono mai: una curva che torna indietro invertirebbe i toni.
    for (i in 1 until n) if (ys[i] < ys[i - 1]) ys[i] = ys[i - 1]
    val d = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / max(1e-6f, xs[it + 1] - xs[it]) }
    val m = FloatArray(n)
    m[0] = d[0]; m[n - 1] = d[n - 2]
    for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2
    for (i in 0 until n - 1) {
        if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
        val a = m[i] / d[i]
        val b = m[i + 1] / d[i]
        val s = a * a + b * b
        if (s > 9f) { val t = 3f / kotlin.math.sqrt(s); m[i] = t * a * d[i]; m[i + 1] = t * b * d[i] }
    }
    return IntArray(256) { v ->
        val x = v / 255f
        val k = when {
            x <= xs[0] -> return@IntArray (ys[0] * 255f + 0.5f).toInt()
            x >= xs[n - 1] -> return@IntArray (ys[n - 1] * 255f + 0.5f).toInt()
            else -> (0 until n - 1).first { x <= xs[it + 1] }
        }
        val h = xs[k + 1] - xs[k]
        val t = (x - xs[k]) / h
        val t2 = t * t
        val t3 = t2 * t
        val y = (2 * t3 - 3 * t2 + 1) * ys[k] + (t3 - 2 * t2 + t) * h * m[k] + (-2 * t3 + 3 * t2) * ys[k + 1] + (t3 - t2) * h * m[k + 1]
        (y.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    }
}

/**
 * Atmosfera: schiarisce le ombre e trattiene le alte luci in modo morbido, insieme alla curva di ombre e alte luci
 * ([toneLut]). Restituisce la tabella dei toni da usare su tutti e tre i canali.
 */
fun toneTable(a: Adjust): IntArray {
    val base = toneLut(a.shadows + a.ambiance * 0.45f, a.highlights - a.ambiance * 0.35f)
    return if (a.curves.luma.isEmpty()) base else {
        val c = curveLut(a.curves.luma)
        IntArray(256) { c[base[it]] }
    }
}

/** Sfocatura a scatola separabile (due passate), su una copia della sola luminosità. [r] in pixel. */
fun boxBlur(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
    if (r < 1) return src.copyOf()
    val tmp = FloatArray(src.size)
    val out = FloatArray(src.size)
    val span = 2 * r + 1
    for (y in 0 until h) {
        val row = y * w
        var acc = 0f
        for (i in -r..r) acc += src[row + i.coerceIn(0, w - 1)]
        for (x in 0 until w) {
            tmp[row + x] = acc / span
            acc += src[row + min(w - 1, x + r + 1)] - src[row + max(0, x - r)]
        }
    }
    for (x in 0 until w) {
        var acc = 0f
        for (i in -r..r) acc += tmp[i.coerceIn(0, h - 1) * w + x]
        for (y in 0 until h) {
            out[y * w + x] = acc / span
            acc += tmp[min(h - 1, y + r + 1) * w + x] - tmp[max(0, y - r) * w + x]
        }
    }
    return out
}

/**
 * Tutto ciò che si fa pixel per pixel, nell'ordine: toni (ombre, alte luci, atmosfera, curva della luminosità), curve
 * dei colori, vividezza, struttura e nitidezza (contrasto locale sulla luminosità), infine la [matrix] se c'è.
 * Modifica [px] (ARGB) sul posto. Con regolazioni neutre l'immagine resta identica.
 */
fun develop(px: IntArray, w: Int, h: Int, a: Adjust, matrix: FloatArray? = null, radiusBase: Int = min(w, h)) {
    val tone = if (a.hasCurve || a.ambiance != 0f || a.curves.luma.isNotEmpty()) toneTable(a) else null
    val rl = a.curves.red.takeIf { it.isNotEmpty() }?.let(::curveLut)
    val gl = a.curves.green.takeIf { it.isNotEmpty() }?.let(::curveLut)
    val bl = a.curves.blue.takeIf { it.isNotEmpty() }?.let(::curveLut)
    val vib = a.vibrance + a.ambiance * 0.25f
    val detail = a.structure != 0f || a.sharpen != 0f
    val n = w * h
    // Primo giro: toni, curve dei colori, vividezza.
    for (i in 0 until n) {
        val c = px[i]
        var r = (c shr 16) and 0xFF
        var g = (c shr 8) and 0xFF
        var b = c and 0xFF
        if (tone != null) { r = tone[r]; g = tone[g]; b = tone[b] }
        if (rl != null) r = rl[r]
        if (gl != null) g = gl[g]
        if (bl != null) b = bl[b]
        if (vib != 0f) {
            // Vividezza: più forte sui colori spenti, quasi nulla su quelli già saturi (e sulla pelle, che è poco satura ma calda).
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            val sat = if (mx == 0) 0f else (mx - mn) / mx.toFloat()
            val k = 1f + vib * (1f - sat) * 0.9f
            val l = 0.2126f * r + 0.7152f * g + 0.0722f * b
            r = (l + (r - l) * k).roundToInt().coerceIn(0, 255)
            g = (l + (g - l) * k).roundToInt().coerceIn(0, 255)
            b = (l + (b - l) * k).roundToInt().coerceIn(0, 255)
        }
        px[i] = (c and -0x1000000) or (r shl 16) or (g shl 8) or b
    }
    if (detail) {
        val luma = FloatArray(n) { i -> val c = px[i]; 0.2126f * ((c shr 16) and 0xFF) + 0.7152f * ((c shr 8) and 0xFF) + 0.0722f * (c and 0xFF) }
        // Struttura: dettagli medi (raggio ~1% del lato corto); nitidezza: bordi fini (raggio 1).
        val big = if (a.structure != 0f) boxBlur(luma, w, h, max(2, radiusBase / 100)) else null
        val small = if (a.sharpen != 0f) boxBlur(luma, w, h, 1) else null
        for (i in 0 until n) {
            var delta = 0f
            if (big != null) delta += (luma[i] - big[i]) * a.structure * 1.4f
            if (small != null) delta += (luma[i] - small[i]) * a.sharpen * 2.2f
            if (delta == 0f) continue
            val c = px[i]
            val d = delta.roundToInt()
            px[i] = (c and -0x1000000) or
                ((((c shr 16) and 0xFF) + d).coerceIn(0, 255) shl 16) or
                ((((c shr 8) and 0xFF) + d).coerceIn(0, 255) shl 8) or
                ((c and 0xFF) + d).coerceIn(0, 255)
        }
    }
    if (matrix != null) applyMatrix(px, matrix)
}

/** La matrice di colore 4×5 (come quella di Android) sui pixel. */
fun applyMatrix(px: IntArray, m: FloatArray) {
    for (i in px.indices) {
        val c = px[i]
        val r = ((c shr 16) and 0xFF).toFloat()
        val g = ((c shr 8) and 0xFF).toFloat()
        val b = (c and 0xFF).toFloat()
        val nr = (m[0] * r + m[1] * g + m[2] * b + m[4]).roundToInt().coerceIn(0, 255)
        val ng = (m[5] * r + m[6] * g + m[7] * b + m[9]).roundToInt().coerceIn(0, 255)
        val nb = (m[10] * r + m[11] * g + m[12] * b + m[14]).roundToInt().coerceIn(0, 255)
        px[i] = (c and -0x1000000) or (nr shl 16) or (ng shl 8) or nb
    }
}

/**
 * Bianco giusto (contagocce, tavola GalRegolaTutto): dal colore di un punto che dovrebbe essere grigio, il calore e la
 * tinta che lo rendono neutro con la matrice di [adjustMatrix].
 */
fun neutralize(r: Int, g: Int, b: Int, current: Adjust): Adjust {
    // Nella matrice il calore sposta rosso +28 e blu −28, la tinta sposta il verde di −28: si annulla la differenza.
    val warmth = ((b - r) / 2f / 28f).coerceIn(-1f, 1f)
    val tint = ((g - (r + b) / 2f) / 28f).coerceIn(-1f, 1f)
    return current.copy(warmth = (current.warmth + warmth).coerceIn(-1f, 1f), tint = (current.tint + tint).coerceIn(-1f, 1f))
}

/** Una modalità tua (tavola GalModifiche, «Salva come look»): un nome e le regolazioni. */
data class SavedLook(val id: Long, val name: String, val adjust: Adjust)

/** Forma testuale per le preferenze: una riga per modalità, campi separati da tabulazioni. */
object LookCodec {
    private fun pts(p: List<CurvePoint>) = p.joinToString(";") { "${it.x},${it.y}" }
    private fun parsePts(s: String) = if (s.isBlank()) emptyList() else s.split(';').mapNotNull { v ->
        val (x, y) = v.split(',').takeIf { it.size == 2 } ?: return@mapNotNull null
        CurvePoint(x.toFloatOrNull() ?: return@mapNotNull null, y.toFloatOrNull() ?: return@mapNotNull null)
    }

    fun encode(looks: List<SavedLook>): String = looks.joinToString("\n") { l ->
        val a = l.adjust
        listOf(
            "1", l.id.toString(), l.name.replace('\t', ' ').replace('\n', ' '),
            a.light, a.contrast, a.saturation, a.warmth, a.shadows, a.highlights, a.vignette,
            a.ambiance, a.vibrance, a.tint, a.structure, a.sharpen,
            pts(a.curves.luma), pts(a.curves.red), pts(a.curves.green), pts(a.curves.blue),
        ).joinToString("\t")
    }

    fun decode(text: String?): List<SavedLook> = text.orEmpty().lines().mapNotNull { line ->
        val f = line.split('\t')
        if (f.size != 19 || f[0] != "1") return@mapNotNull null
        val n = (3..14).map { f[it].toFloatOrNull() ?: return@mapNotNull null }
        SavedLook(
            f[1].toLongOrNull() ?: return@mapNotNull null, f[2],
            Adjust(
                light = n[0], contrast = n[1], saturation = n[2], warmth = n[3], shadows = n[4], highlights = n[5], vignette = n[6],
                ambiance = n[7], vibrance = n[8], tint = n[9], structure = n[10], sharpen = n[11],
                curves = Curves(parsePts(f[15]), parsePts(f[16]), parsePts(f[17]), parsePts(f[18])),
            ),
        )
    }
}

/**
 * Una modalità tua come testo da mandare o da mettere in un codice QR (tavola GalLookQR): `eurise-look:` e le sole
 * regolazioni, in base64. Nessuna foto, nessun dato della persona.
 */
object LookShare {
    const val PREFIX = "eurise-look:"

    fun encode(look: SavedLook): String =
        PREFIX + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(LookCodec.encode(listOf(look.copy(id = 0))).toByteArray(Charsets.UTF_8))

    /** La modalità contenuta in un testo (anche con altre parole attorno), con un id nuovo; null se non ce n'è. */
    fun decode(text: String?, newId: Long): SavedLook? {
        val t = text ?: return null
        val start = t.indexOf(PREFIX).takeIf { it >= 0 } ?: return null
        val payload = t.substring(start + PREFIX.length).takeWhile { it.isLetterOrDigit() || it == '-' || it == '_' }
        val raw = runCatching { String(java.util.Base64.getUrlDecoder().decode(payload), Charsets.UTF_8) }.getOrNull() ?: return null
        return LookCodec.decode(raw).firstOrNull()?.copy(id = newId)
    }
}

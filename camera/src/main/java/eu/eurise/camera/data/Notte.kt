package eu.eurise.camera.data

import kotlin.math.abs

/**
 * Notte con più fotogrammi (22 novembre 2026): con poca luce lo scatto prende alcuni fotogrammi di fila e qui si
 * fondono. Ogni fotogramma si allinea al primo (spostamento della mano, cercato prima su una versione piccola e poi
 * affinato a piena risoluzione) e si somma pixel per pixel; dove un pixel è troppo diverso dal primo (qualcosa si è mosso)
 * si tiene solo il primo, così niente fantasmi. Il rumore cala come la radice del numero di fotogrammi: con 4 la metà.
 *
 * Il cuore lavora su righe di pixel ARGB, senza Bitmap: si prova anche senza telefono.
 */
class Notte(private val w: Int, private val h: Int) {
    private val sr = ShortArray(w * h)
    private val sg = ShortArray(w * h)
    private val sb = ShortArray(w * h)
    private val count = ByteArray(w * h)
    var frames = 0
        private set

    /** Il primo fotogramma, riga per riga: è il riferimento. */
    fun addReference(y: Int, row: IntArray) {
        val o = y * w
        for (x in 0 until w) {
            val c = row[x]
            sr[o + x] = (c shr 16 and 0xFF).toShort(); sg[o + x] = (c shr 8 and 0xFF).toShort(); sb[o + x] = (c and 0xFF).toShort()
            count[o + x] = 1
        }
        if (y == h - 1) frames = 1
    }

    /**
     * Una riga di un altro fotogramma, già spostata: [row] è la riga y+[dy] del fotogramma, [ref] la riga y del primo.
     * [last] dice che è l'ultima riga del fotogramma.
     */
    fun add(y: Int, ref: IntArray, row: IntArray?, dx: Int, last: Boolean) {
        if (row != null) {
            val o = y * w
            for (x in 0 until w) {
                val xs = x + dx
                if (xs < 0 || xs >= w) continue
                val c = row[xs]; val r0 = ref[x]
                if (abs(luma(c) - luma(r0)) > GHOST) continue
                sr[o + x] = (sr[o + x] + (c shr 16 and 0xFF)).toShort()
                sg[o + x] = (sg[o + x] + (c shr 8 and 0xFF)).toShort()
                sb[o + x] = (sb[o + x] + (c and 0xFF)).toShort()
                count[o + x]++
            }
        }
        if (last) frames++
    }

    /** La riga y del risultato, in [out] (ARGB opaco). */
    fun result(y: Int, out: IntArray) {
        val o = y * w
        for (x in 0 until w) {
            val n = count[o + x].toInt().coerceAtLeast(1)
            val half = n / 2
            out[x] = (0xFF shl 24) or (((sr[o + x] + half) / n) shl 16) or (((sg[o + x] + half) / n) shl 8) or ((sb[o + x] + half) / n)
        }
    }

    companion object {
        /** Differenza di luminosità (0-255) oltre la quale un pixel si considera mosso. */
        const val GHOST = 28
        /** Al massimo 8 fotogrammi: 8 × 255 sta in uno Short. */
        const val MAX_FRAMES = 8

        fun luma(c: Int): Int = ((c shr 16 and 0xFF) * 77 + (c shr 8 and 0xFF) * 150 + (c and 0xFF) * 29) shr 8

        /**
         * Lo spostamento (dx, dy) che porta [frame] su [ref], immagini di luminosità [w]×[h] (una riga dopo l'altra),
         * cercato entro ±[range] pixel con la somma delle differenze su un pixel ogni [step].
         */
        fun shift(ref: ByteArray, frame: ByteArray, w: Int, h: Int, range: Int, step: Int = 2, cx: Int = 0, cy: Int = 0): Pair<Int, Int> {
            var best = Long.MAX_VALUE; var bx = cx; var by = cy
            val m = range + maxOf(abs(cx), abs(cy))
            for (dy in cy - range..cy + range) for (dx in cx - range..cx + range) {
                var sad = 0L
                var y = m
                while (y < h - m) {
                    val ro = y * w; val fo = (y + dy) * w
                    var x = m
                    while (x < w - m) {
                        sad += abs((ref[ro + x].toInt() and 0xFF) - (frame[fo + x + dx].toInt() and 0xFF))
                        x += step
                    }
                    y += step
                }
                if (sad < best) { best = sad; bx = dx; by = dy }
            }
            return bx to by
        }
    }
}

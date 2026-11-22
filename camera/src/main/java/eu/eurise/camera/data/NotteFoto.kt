package eu.eurise.camera.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.util.Log
import eu.eurise.camera.TAG
import java.io.File
import kotlin.math.abs

/**
 * Dalla raffica di JPEG alla foto di [Notte]: decodifica un fotogramma alla volta (in memoria restano il primo, quello in
 * lavoro e le somme), allinea, somma, poi passa la foto fusa al [Ritocco] e scrive il JPEG con i dati del primo scatto.
 */
object NotteFoto {
    /** Fonde [frames] (JPEG) nel file [out]. [iso] è quello del primo fotogramma. False se non riesce. */
    fun merge(frames: List<ByteArray>, out: File, iso: Int, cache: File): Boolean {
        if (frames.isEmpty()) return false
        val start = System.currentTimeMillis()
        val opts = BitmapFactory.Options().apply { inMutable = true }
        val ref = BitmapFactory.decodeByteArray(frames[0], 0, frames[0].size, opts) ?: return false
        try {
            val w = ref.width; val h = ref.height
            val notte = Notte(w, h)
            val row = IntArray(w); val other = IntArray(w)
            for (y in 0 until h) { ref.getPixels(row, 0, w, 0, y, w, 1); notte.addReference(y, row) }
            val small = smallLuma(frames[0]) ?: return false
            var used = 1
            for (i in 1 until minOf(frames.size, Notte.MAX_FRAMES)) {
                val s = smallLuma(frames[i]) ?: continue
                if (s.w != small.w || s.h != small.h) continue
                val (sx, sy) = Notte.shift(small.luma, s.luma, small.w, small.h, range = 12)
                val f = BitmapFactory.decodeByteArray(frames[i], 0, frames[i].size) ?: continue
                try {
                    if (f.width != w || f.height != h) continue
                    val (dx, dy) = refine(ref, f, sx * small.scale, sy * small.scale, small.scale)
                    for (y in 0 until h) {
                        ref.getPixels(row, 0, w, 0, y, w, 1)
                        val ys = y + dy
                        val src = if (ys in 0 until h) { f.getPixels(other, 0, w, 0, ys, w, 1); other } else null
                        notte.add(y, row, src, dx, last = y == h - 1)
                    }
                    used++
                    Log.i(TAG, "Notte: fotogramma $i spostato di $dx, $dy")
                } finally {
                    f.recycle()
                }
            }
            for (y in 0 until h) { notte.result(y, row); ref.setPixels(row, 0, w, 0, y, w, 1) }
            Ritocco.process(ref, iso)
            out.outputStream().use { ref.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            Log.i(TAG, "Notte: $used fotogrammi fusi in ${System.currentTimeMillis() - start} ms")
        } finally {
            ref.recycle()
        }
        // I dati dello scatto dal primo fotogramma (orientamento compreso).
        val first = File(cache, "notte-primo.jpg")
        runCatching {
            first.writeBytes(frames[0])
            Ritocco.copyExif(first, out)
        }
        first.delete()
        return true
    }

    fun isoOf(jpeg: ByteArray, cache: File): Int {
        val f = File(cache, "notte-iso.jpg")
        return runCatching { f.writeBytes(jpeg); ExifInterface(f.path).getAttributeInt("ISOSpeedRatings", 0) }
            .getOrDefault(0).also { f.delete() }
    }

    private class Small(val luma: ByteArray, val w: Int, val h: Int, val scale: Int)

    private fun smallLuma(jpeg: ByteArray): Small? {
        val scale = 4
        val b = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, BitmapFactory.Options().apply { inSampleSize = scale }) ?: return null
        try {
            val w = b.width; val h = b.height
            val px = IntArray(w * h)
            b.getPixels(px, 0, w, 0, 0, w, h)
            return Small(ByteArray(w * h) { Notte.luma(px[it]).toByte() }, w, h, scale)
        } finally {
            b.recycle()
        }
    }

    /** Affina a piena risoluzione, entro ±[scale] pixel, su alcune righe sparse. */
    private fun refine(ref: Bitmap, f: Bitmap, cx: Int, cy: Int, scale: Int): Pair<Int, Int> {
        val w = ref.width; val h = ref.height
        val margin = abs(cx) + abs(cy) + scale + 8
        if (h <= 2 * margin + 32 || w <= 2 * margin + 32) return cx to cy
        val rows = 24
        val r = IntArray(w); val o = IntArray(w)
        val refRows = Array(rows) { i ->
            val y = margin + (h - 2 * margin) * i / rows
            ref.getPixels(r, 0, w, 0, y, w, 1)
            IntArray(w) { Notte.luma(r[it]) }
        }
        var best = Long.MAX_VALUE; var bx = cx; var by = cy
        for (dy in cy - scale..cy + scale) {
            val frameRows = Array(rows) { i ->
                val y = margin + (h - 2 * margin) * i / rows + dy
                f.getPixels(o, 0, w, 0, y, w, 1)
                IntArray(w) { Notte.luma(o[it]) }
            }
            for (dx in cx - scale..cx + scale) {
                var sad = 0L
                for (i in 0 until rows) {
                    val a = refRows[i]; val b = frameRows[i]
                    var x = margin
                    while (x < w - margin) { sad += abs(a[x] - b[x + dx]); x += 3 }
                }
                if (sad < best) { best = sad; bx = dx; by = dy }
            }
        }
        return bx to by
    }
}

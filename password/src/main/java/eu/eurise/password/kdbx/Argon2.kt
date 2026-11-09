package eu.eurise.password.kdbx

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Argon2 (RFC 9106), versione 0x13, varianti d e id: ricava la chiave dalla frase principale nei file KeePass.
 * Le corsie si riempiono una dopo l'altra: il risultato è identico a quello in parallelo, perché dentro una
 * fetta una corsia legge solo le fette già finite delle altre.
 */
internal object Argon2 {
    const val TYPE_D = 0
    const val TYPE_ID = 2
    private const val BLOCK_WORDS = 128
    private const val BLOCK_BYTES = 1024
    private const val SYNC_POINTS = 4

    fun hash(
        type: Int,
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        memoryKiB: Int,
        parallelism: Int,
        tagLength: Int,
        secret: ByteArray = ByteArray(0),
        associated: ByteArray = ByteArray(0),
    ): ByteArray {
        val h0 = Blake2b.hash(
            64,
            le32(parallelism), le32(tagLength), le32(memoryKiB), le32(iterations), le32(0x13), le32(type),
            le32(password.size), password, le32(salt.size), salt,
            le32(secret.size), secret, le32(associated.size), associated,
        )
        val minBlocks = maxOf(memoryKiB, 2 * SYNC_POINTS * parallelism)
        val laneLength = minBlocks / (SYNC_POINTS * parallelism) * SYNC_POINTS
        val segment = laneLength / SYNC_POINTS
        val blocks = laneLength * parallelism
        val m = LongArray(blocks * BLOCK_WORDS)

        for (lane in 0 until parallelism) {
            load(m, lane * laneLength, longHash(BLOCK_BYTES, h0, le32(0), le32(lane)))
            load(m, lane * laneLength + 1, longHash(BLOCK_BYTES, h0, le32(1), le32(lane)))
        }

        val fill = Filler(m, type, blocks, iterations, parallelism, laneLength, segment)
        for (pass in 0 until iterations) {
            for (slice in 0 until SYNC_POINTS) {
                for (lane in 0 until parallelism) fill.segment(pass, slice, lane)
            }
        }

        val last = LongArray(BLOCK_WORDS)
        for (lane in 0 until parallelism) {
            val from = ((lane + 1) * laneLength - 1) * BLOCK_WORDS
            for (i in 0 until BLOCK_WORDS) last[i] = last[i] xor m[from + i]
        }
        m.fill(0)
        val bytes = ByteBuffer.allocate(BLOCK_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        last.forEach { bytes.putLong(it) }
        return longHash(tagLength, bytes.array())
    }

    private class Filler(
        val m: LongArray,
        val type: Int,
        val blocks: Int,
        val iterations: Int,
        val lanes: Int,
        val laneLength: Int,
        val segmentLength: Int,
    ) {
        val r = LongArray(BLOCK_WORDS)
        val tmp = LongArray(BLOCK_WORDS)

        fun segment(pass: Int, slice: Int, lane: Int) {
            val independent = type == TYPE_ID && pass == 0 && slice < SYNC_POINTS / 2
            val input = LongArray(BLOCK_WORDS)
            val address = LongArray(BLOCK_WORDS)
            val zero = LongArray(BLOCK_WORDS)
            if (independent) {
                input[0] = pass.toLong(); input[1] = lane.toLong(); input[2] = slice.toLong()
                input[3] = blocks.toLong(); input[4] = iterations.toLong(); input[5] = type.toLong()
            }
            fun nextAddresses() {
                input[6]++
                compress(zero, 0, input, 0, address, false)
                compress(zero, 0, address, 0, address, false)
            }

            var start = 0
            if (pass == 0 && slice == 0) {
                start = 2
                if (independent) nextAddresses()
            }
            var current = lane * laneLength + slice * segmentLength + start
            var previous = if (current % laneLength == 0) current + laneLength - 1 else current - 1

            for (i in start until segmentLength) {
                if (current % laneLength == 1) previous = current - 1
                val pseudo = if (independent) {
                    if (i % BLOCK_WORDS == 0) nextAddresses()
                    address[i % BLOCK_WORDS]
                } else {
                    m[previous * BLOCK_WORDS]
                }
                var refLane = ((pseudo ushr 32) % lanes).toInt()
                if (pass == 0 && slice == 0) refLane = lane
                val refIndex = refIndex(pass, slice, i, pseudo and 0xFFFFFFFFL, refLane == lane)
                val ref = refLane * laneLength + refIndex
                compress(m, previous * BLOCK_WORDS, m, ref * BLOCK_WORDS, m, pass != 0, current * BLOCK_WORDS)
                current++
                previous++
            }
        }

        fun refIndex(pass: Int, slice: Int, index: Int, pseudo: Long, sameLane: Boolean): Int {
            val area: Int = if (pass == 0) {
                when {
                    slice == 0 -> index - 1
                    sameLane -> slice * segmentLength + index - 1
                    else -> slice * segmentLength + if (index == 0) -1 else 0
                }
            } else {
                laneLength - segmentLength + if (sameLane) index - 1 else if (index == 0) -1 else 0
            }
            val x = (pseudo * pseudo) ushr 32
            val relative = area - 1 - ((area.toLong() * x) ushr 32).toInt()
            val start = if (pass != 0 && slice != SYNC_POINTS - 1) (slice + 1) * segmentLength else 0
            return ((start.toLong() + relative) % laneLength).toInt()
        }

        private fun compress(a: LongArray, ai: Int, b: LongArray, bi: Int, out: LongArray, xor: Boolean, oi: Int = 0) {
            for (i in 0 until BLOCK_WORDS) r[i] = a[ai + i] xor b[bi + i]
            r.copyInto(tmp)
            if (xor) for (i in 0 until BLOCK_WORDS) tmp[i] = tmp[i] xor out[oi + i]
            for (i in 0 until 8) {
                val s = i * 16
                round(s, s + 1, s + 2, s + 3, s + 4, s + 5, s + 6, s + 7, s + 8, s + 9, s + 10, s + 11, s + 12, s + 13, s + 14, s + 15)
            }
            for (i in 0 until 8) {
                val s = i * 2
                round(s, s + 1, s + 16, s + 17, s + 32, s + 33, s + 48, s + 49, s + 64, s + 65, s + 80, s + 81, s + 96, s + 97, s + 112, s + 113)
            }
            for (i in 0 until BLOCK_WORDS) out[oi + i] = tmp[i] xor r[i]
        }

        private fun round(v0: Int, v1: Int, v2: Int, v3: Int, v4: Int, v5: Int, v6: Int, v7: Int, v8: Int, v9: Int, v10: Int, v11: Int, v12: Int, v13: Int, v14: Int, v15: Int) {
            // Le sedici parole si rimescolano in r, che alla fine di compress viene riusato come copia di partenza.
            gb(v0, v4, v8, v12); gb(v1, v5, v9, v13); gb(v2, v6, v10, v14); gb(v3, v7, v11, v15)
            gb(v0, v5, v10, v15); gb(v1, v6, v11, v12); gb(v2, v7, v8, v13); gb(v3, v4, v9, v14)
        }

        private fun gb(a: Int, b: Int, c: Int, d: Int) {
            val w = r
            w[a] = mix(w[a], w[b]); w[d] = java.lang.Long.rotateRight(w[d] xor w[a], 32)
            w[c] = mix(w[c], w[d]); w[b] = java.lang.Long.rotateRight(w[b] xor w[c], 24)
            w[a] = mix(w[a], w[b]); w[d] = java.lang.Long.rotateRight(w[d] xor w[a], 16)
            w[c] = mix(w[c], w[d]); w[b] = java.lang.Long.rotateRight(w[b] xor w[c], 63)
        }

        private fun mix(x: Long, y: Long): Long = x + y + 2 * (x and 0xFFFFFFFFL) * (y and 0xFFFFFFFFL)
    }

    private fun load(m: LongArray, block: Int, bytes: ByteArray) {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until BLOCK_WORDS) m[block * BLOCK_WORDS + i] = buf.getLong()
    }

    /** La funzione H' della specifica: impronta di lunghezza qualsiasi. */
    private fun longHash(length: Int, vararg parts: ByteArray): ByteArray {
        if (length <= 64) return Blake2b.hash(length, le32(length), *parts)
        val out = ByteArray(length)
        var v = Blake2b.hash(64, le32(length), *parts)
        v.copyInto(out, 0, 0, 32)
        var at = 32
        while (length - at > 64) {
            v = Blake2b.hash(64, v)
            v.copyInto(out, at, 0, 32)
            at += 32
        }
        Blake2b.hash(length - at, v).copyInto(out, at)
        return out
    }

    private fun le32(value: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
}

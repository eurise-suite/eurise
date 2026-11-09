package eu.eurise.password.kdbx

/**
 * ChaCha20 (RFC 8439). KeePass lo usa per i campi protetti dentro il file, come un unico flusso che continua da un
 * campo all'altro; la versione di sistema c'è solo da Android 9 e non tiene la posizione tra una chiamata e l'altra.
 */
internal class ChaCha20(key: ByteArray, nonce: ByteArray) {
    private val state = IntArray(16)
    private val block = ByteArray(64)
    private var used = 64

    init {
        require(key.size == 32 && nonce.size == 12)
        state[0] = 0x61707865; state[1] = 0x3320646e; state[2] = 0x79622d32; state[3] = 0x6b206574
        for (i in 0 until 8) state[4 + i] = word(key, i * 4)
        state[12] = 0
        for (i in 0 until 3) state[13 + i] = word(nonce, i * 4)
    }

    /** Applica il flusso a [data] e restituisce il risultato: la stessa operazione cifra e decifra. */
    fun apply(data: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        for (i in data.indices) {
            if (used == 64) refill()
            out[i] = (data[i].toInt() xor block[used++].toInt()).toByte()
        }
        return out
    }

    private fun refill() {
        val x = state.copyOf()
        repeat(10) {
            quarter(x, 0, 4, 8, 12); quarter(x, 1, 5, 9, 13); quarter(x, 2, 6, 10, 14); quarter(x, 3, 7, 11, 15)
            quarter(x, 0, 5, 10, 15); quarter(x, 1, 6, 11, 12); quarter(x, 2, 7, 8, 13); quarter(x, 3, 4, 9, 14)
        }
        for (i in 0 until 16) {
            val v = x[i] + state[i]
            block[i * 4] = v.toByte(); block[i * 4 + 1] = (v ushr 8).toByte()
            block[i * 4 + 2] = (v ushr 16).toByte(); block[i * 4 + 3] = (v ushr 24).toByte()
        }
        state[12]++
        used = 0
    }

    private fun quarter(x: IntArray, a: Int, b: Int, c: Int, d: Int) {
        x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] xor x[a], 16)
        x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] xor x[c], 12)
        x[a] += x[b]; x[d] = Integer.rotateLeft(x[d] xor x[a], 8)
        x[c] += x[d]; x[b] = Integer.rotateLeft(x[b] xor x[c], 7)
    }

    private fun word(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)
}

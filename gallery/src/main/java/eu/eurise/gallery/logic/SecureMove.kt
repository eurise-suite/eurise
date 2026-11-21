package eu.eurise.gallery.logic

import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.security.DigestInputStream
import java.security.DigestOutputStream
import java.security.MessageDigest

/**
 * Il passaggio di un file da una cartella protetta all'altra (la cartella arrivata «dal backup» che entra nella tua,
 * 21 novembre 2026): si decifra con la chiave vecchia e si cifra con la nuova **in un tubo in memoria**, a blocchi,
 * senza mai scrivere il contenuto in chiaro su disco. Poi la copia nuova si rilegge ([digest]) e si confronta.
 */
object SecureMove {
    /**
     * Ricifra [input] (cifrato con [fromKey] e [fromLabel]) in [output] con [toKey] e [toLabel]. Restituisce lo SHA-256
     * del contenuto in chiaro; se la lettura del vecchio fallisce a metà (file rovinato, chiave sbagliata) lancia
     * l'errore, anche se la parte già scritta sembra a posto.
     */
    fun reencrypt(fromKey: ByteArray, fromLabel: String, toKey: ByteArray, toLabel: String, input: InputStream, output: OutputStream): ByteArray {
        val pipeIn = PipedInputStream(SecureStream.CHUNK)
        val pipeOut = PipedOutputStream(pipeIn)
        var failure: Throwable? = null
        val reader = Thread {
            try {
                pipeOut.use { SecureStream.decrypt(fromKey, fromLabel, input, it) }
            } catch (t: Throwable) {
                failure = t
                runCatching { pipeOut.close() }
            }
        }
        reader.start()
        val sha = MessageDigest.getInstance("SHA-256")
        try {
            SecureStream.encrypt(toKey, toLabel, DigestInputStream(pipeIn, sha), output)
        } finally {
            // Chiudere il lato che legge sblocca chi scrive anche se la cifratura si è fermata a metà.
            runCatching { pipeIn.close() }
            reader.join()
        }
        failure?.let { throw it }
        return sha.digest()
    }

    /** Lo SHA-256 del contenuto in chiaro di [input], decifrato con [key] e [label]. */
    fun digest(key: ByteArray, label: String, input: InputStream): ByteArray {
        val sha = MessageDigest.getInstance("SHA-256")
        DigestOutputStream(NullOutput, sha).use { SecureStream.decrypt(key, label, input, it) }
        return sha.digest()
    }

    private object NullOutput : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }
}

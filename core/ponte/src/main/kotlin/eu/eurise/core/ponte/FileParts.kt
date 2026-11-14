package eu.eurise.core.ponte

import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

/**
 * Un file sul Ponte (da Firefox sul computer al telefono, 15 novembre): pezzi da [PART] byte in Base64, ognuno un
 * messaggio [Kind.File] con `fid` (lo stesso per tutti i pezzi), `name`, `mime`, `size`, `part` (da 0), `parts`,
 * `sha` (SHA-256 del file intero, in esadecimale) e `data`. Chi riceve li raccoglie con [Inbox].
 */
object FileParts {
    const val PART = 512 * 1024
    const val MAX = 50L * 1024 * 1024

    fun split(name: String, mime: String, bytes: ByteArray, fid: String = UUID.randomUUID().toString()): List<Map<String, String>> {
        require(bytes.size <= MAX) { "file troppo grande" }
        val parts = maxOf(1, (bytes.size + PART - 1) / PART)
        val sha = sha256(bytes)
        val b64 = Base64.getEncoder()
        return (0 until parts).map { i ->
            val from = i * PART
            val to = minOf(bytes.size, from + PART)
            mapOf(
                "fid" to fid, "name" to safeName(name), "mime" to mime, "size" to bytes.size.toString(),
                "part" to i.toString(), "parts" to parts.toString(), "sha" to sha,
                "data" to b64.encodeToString(bytes.copyOfRange(from, to)),
            )
        }
    }

    /** Un nome senza cartelle né caratteri che i sistemi rifiutano; mai vuoto. */
    fun safeName(name: String): String =
        name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\u0000-\\u001f:*?\"<>|]"), "_").trim().trimStart('.')
            .take(120).ifBlank { "file" }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Il file ricomposto, pronto da salvare dove si vuole. */
    class Done(val name: String, val mime: String, val bytes: ByteArray)

    /**
     * Raccoglie i pezzi in [dir] (una cartella per `fid`); restituisce il file quando ci sono tutti e il controllo
     * SHA-256 torna. Pezzi rifiutati: indici fuori posto, file oltre [MAX], dati non Base64. Le cartelle più vecchie di
     * un giorno si cancellano.
     */
    class Inbox(private val dir: File) {
        fun add(f: Map<String, String>, now: Long = System.currentTimeMillis()): Done? {
            val fid = f["fid"]?.takeIf { Regex("[A-Za-z0-9-]{8,64}").matches(it) } ?: return null
            val parts = f["parts"]?.toIntOrNull()?.takeIf { it in 1..(MAX / PART + 1).toInt() } ?: return null
            val part = f["part"]?.toIntOrNull()?.takeIf { it in 0 until parts } ?: return null
            val size = f["size"]?.toLongOrNull()?.takeIf { it in 0..MAX } ?: return null
            val data = runCatching { Base64.getDecoder().decode(f["data"].orEmpty()) }.getOrNull() ?: return null
            dir.mkdirs()
            dir.listFiles()?.forEach { if (now - it.lastModified() > 24 * 3_600_000L) it.deleteRecursively() }
            val box = File(dir, fid).apply { mkdirs() }
            File(box, "$part.part").writeBytes(data)
            val have = (0 until parts).map { File(box, "$it.part") }
            if (!have.all { it.isFile }) return null
            val bytes = have.fold(java.io.ByteArrayOutputStream()) { out, p -> out.write(p.readBytes()); out }.toByteArray()
            box.deleteRecursively()
            if (bytes.size.toLong() != size) return null
            f["sha"]?.let { if (it != sha256(bytes)) return null }
            return Done(safeName(f["name"].orEmpty()), f["mime"]?.takeIf { it.contains('/') } ?: "application/octet-stream", bytes)
        }
    }
}

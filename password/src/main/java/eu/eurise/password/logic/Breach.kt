package eu.eurise.password.logic

import java.security.MessageDigest

/** Controllo per k-anonimato: al servizio vanno solo i primi 5 caratteri dell'impronta SHA-1, mai la password. */
object Breach {
    fun sha1(password: String): String =
        MessageDigest.getInstance("SHA-1").digest(password.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }

    /** Cerca il resto dell'impronta nella risposta "SUFFISSO:CONTEGGIO"; le righe a zero sono imbottitura. */
    fun found(hash: String, response: String): Boolean {
        val suffix = hash.drop(5)
        return response.lineSequence().any { line ->
            line.substringBefore(':').trim().equals(suffix, ignoreCase = true) &&
                (line.substringAfter(':', "0").trim().toIntOrNull() ?: 0) > 0
        }
    }
}

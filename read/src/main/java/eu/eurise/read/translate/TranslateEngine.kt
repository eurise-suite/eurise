package eu.eurise.read.translate

import android.content.Context
import java.io.Closeable

/**
 * I modelli aperti, riusati tra una richiesta e l'altra (aprirne uno costa un secondo circa). Da usare da un solo thread:
 * slimt non va chiamato da più thread insieme. Tra due lingue diverse dall'inglese passa dall'inglese ([Models.route]).
 */
class TranslateEngine(private val context: Context) : Closeable {
    private val open = HashMap<String, Translator>()

    class MissingModel(val pair: String) : Exception(pair)

    /** Traduce [text] riga per riga (le righe vuote restano). Lancia [MissingModel] se manca una direzione. */
    fun translate(text: String, from: String, to: String): String {
        val route = Models.route(from, to)
        if (route.isEmpty()) return text
        val translators = route.map { pair ->
            open[pair] ?: (Models.installed(context, pair)?.let(Translator::open) ?: throw MissingModel(pair)).also { open[pair] = it }
        }
        return text.split('\n').joinToString("\n") { line ->
            if (line.isBlank()) line else translators.fold(line) { acc, t -> t.translate(acc) }
        }
    }

    /** Chiude i modelli della lingua [lang] (prima di toglierla dal telefono). */
    fun forget(lang: String) {
        open.keys.filter { it.startsWith("$lang-") || it.endsWith("-$lang") }.forEach { open.remove(it)?.close() }
    }

    override fun close() {
        open.values.forEach { it.close() }
        open.clear()
    }
}

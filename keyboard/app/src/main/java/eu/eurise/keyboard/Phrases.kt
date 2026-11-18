package eu.eurise.keyboard

import android.content.Context
import helium314.keyboard.latin.utils.prefs
import java.io.File

/**
 * Le frasi abituali della persona ([PhraseModel]) nella Tastiera: si imparano quando una frase finisce (punto, a capo)
 * o quando il campo si svuota dopo l'invio, e si salvano in un file della Tastiera. Di base accese; l'interruttore è
 * «Frase intera tra le parole» nelle impostazioni.
 */
object Phrases {
    const val PREF_ON = "eurise_phrase_on"
    private const val FILE = "eurise_phrases.txt"
    private val model = PhraseModel()
    @Volatile private var loaded = false
    private var lastText = ""
    private var lastLearned = ""
    private var dirty = false

    fun isOn(context: Context) = context.prefs().getBoolean(PREF_ON, true)

    private fun ensure(context: Context) {
        if (loaded) return
        loaded = true
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()?.let(model::load)
    }

    /** Il testo prima del cursore è cambiato: se una frase si è chiusa, la si conta. */
    fun observe(context: Context, text: String) {
        if (!isOn(context)) { lastText = text; return }
        ensure(context)
        val prev = lastText
        lastText = text
        val closed = when {
            text.isNotEmpty() && text.last() in ".!?\n" -> text.dropLast(1).split(Regex("[.!?\n]")).last()
            // Messaggio mandato: il campo si è svuotato.
            text.isBlank() && prev.isNotBlank() -> prev.split(Regex("[.!?\n]")).last()
            else -> return
        }.trim()
        if (closed.isEmpty() || closed == lastLearned) return
        lastLearned = closed
        model.learn(closed)
        dirty = true
    }

    /** La frase in corso (dall'ultima fine di frase) e il resto proposto, o null. */
    fun propose(context: Context, text: String): Pair<String, String>? {
        if (!isOn(context)) return null
        ensure(context)
        val current = text.split(Regex("[.!?\n]")).last().trimStart()
        return model.propose(current)?.let { current to it }
    }

    fun forget(context: Context, current: String, rest: String) {
        ensure(context)
        model.forget(current, rest)
        dirty = true
        save(context)
    }

    fun save(context: Context) {
        if (!dirty) return
        dirty = false
        val text = model.save()
        Thread { runCatching { File(context.filesDir, FILE).writeText(text) } }.start()
    }

    /** «Dimentica le frasi» nelle impostazioni. */
    fun clear(context: Context) {
        ensure(context)
        model.clear()
        dirty = true
        save(context)
    }

    fun count(context: Context): Int { ensure(context); return model.size }
}

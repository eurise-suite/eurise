package eu.eurise.core.data.alfred

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.time.LocalDate

/**
 * Ciò che Alfred ricorda dell'utente (tavola AlfCervello, «Alfred ricorda»). Solo sul telefono, nelle impostazioni
 * «eurise_…» che entrano nel backup cifrato; ogni voce si vede e si cancella; spenta, Alfred non impara e non usa nulla.
 *
 * - [Kind.Person]: un nome detto e il contatto scelto («Marco» è Marco Ferri), imparato quando una chiamata o un
 *   messaggio partono davvero.
 * - [Kind.Channel]: l'app con cui si scrive a una persona, quando è sempre la stessa (almeno 3 volte su 4).
 * - [Kind.Said]: una cosa detta ad Alfred («ricorda che dopo pranzo sono le 13»).
 */
class AlfredMemory(context: Context) {
    enum class Kind { Person, Channel, Said }

    data class Fact(val id: String, val kind: Kind, val key: String, val value: String, val since: LocalDate, val count: Int = 1)

    private val prefs = context.applicationContext.getSharedPreferences("eurise_alfred_memory", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    fun all(): List<Fact> = runCatching {
        val a = JSONArray(prefs.getString("facts", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Fact(o.getString("id"), Kind.valueOf(o.getString("kind")), o.getString("key"), o.getString("value"),
                LocalDate.parse(o.getString("since")), o.optInt("count", 1))
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<Fact>) {
        val a = JSONArray()
        list.forEach { f ->
            a.put(JSONObject().put("id", f.id).put("kind", f.kind.name).put("key", f.key).put("value", f.value)
                .put("since", f.since.toString()).put("count", f.count))
        }
        prefs.edit().putString("facts", a.toString()).apply()
    }

    fun forget(id: String) = save(all().filterNot { it.id == id })

    fun forgetAll() = save(emptyList())

    /** Il contatto che l'utente intende con [spoken] («marco» → «Marco Ferri»), se l'ha già scelto. */
    fun person(spoken: String): String? =
        if (!enabled) null else all().firstOrNull { it.kind == Kind.Person && it.key == fold(spoken) }?.value

    /** L'app abituale per scrivere a [name] (nome dell'enum Channel), se è chiara. */
    fun channel(name: String): String? =
        if (!enabled) null else all().firstOrNull { it.kind == Kind.Channel && it.key == fold(name) && it.count >= 3 }?.value

    /** Una chiamata o un messaggio partiti verso [name] detto come [spoken]: si ricorda l'abbinamento. */
    fun learnPerson(spoken: String, name: String) {
        if (!enabled || spoken.isBlank() || name.isBlank() || fold(spoken) == fold(name)) return
        if (spoken.any { it.isDigit() }) return
        val list = all().toMutableList()
        val i = list.indexOfFirst { it.kind == Kind.Person && it.key == fold(spoken) }
        if (i >= 0) list[i] = list[i].copy(value = name, count = if (list[i].value == name) list[i].count + 1 else 1)
        else list += Fact(newId(), Kind.Person, fold(spoken), name, LocalDate.now())
        save(list)
    }

    /**
     * Un messaggio a [name] con [channel]: si contano le volte; la voce vale quando lo stesso canale è stato usato 3
     * volte e un canale diverso fa ripartire il conto (tiene la regola «almeno 3 su 4» senza salvare la storia).
     */
    fun learnChannel(name: String, channel: String) {
        if (!enabled || name.isBlank()) return
        val list = all().toMutableList()
        val i = list.indexOfFirst { it.kind == Kind.Channel && it.key == fold(name) }
        when {
            i < 0 -> list += Fact(newId(), Kind.Channel, fold(name), channel, LocalDate.now())
            list[i].value == channel -> list[i] = list[i].copy(count = list[i].count + 1)
            list[i].count >= 4 -> list[i] = list[i].copy(count = list[i].count - 1)
            else -> list[i] = list[i].copy(value = channel, count = 1, since = LocalDate.now())
        }
        save(list)
    }

    /** «Ricorda che…»: la frase così com'è detta. */
    fun remember(text: String): Fact? {
        if (!enabled || text.isBlank()) return null
        val f = Fact(newId(), Kind.Said, fold(text), text.trim().replaceFirstChar { it.uppercase() }, LocalDate.now())
        save(all().filterNot { it.kind == Kind.Said && it.key == f.key } + f)
        return f
    }

    /**
     * Le righe per il cervello di Alfred («Cose che sai dell'utente»): poche e brevi, le più recenti; nessun numero di
     * telefono o indirizzo, solo nomi e abitudini.
     */
    fun hints(): String {
        if (!enabled) return ""
        return all().sortedByDescending { it.since }.take(20).joinToString("\n") { f ->
            when (f.kind) {
                Kind.Person -> "- «${f.key}» è ${f.value}"
                Kind.Channel -> if (f.count >= 3) "- a ${f.key} di solito scrive su ${f.value.lowercase()}" else ""
                Kind.Said -> "- ${f.value}"
            }
        }.lines().filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun newId() = java.util.UUID.randomUUID().toString().take(8)

    companion object {
        /** Minuscole, senza accenti né punteggiatura: «Marco,» e «marco» sono la stessa chiave. */
        fun fold(s: String): String = Normalizer.normalize(s.lowercase().trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N} ]"), "").replace(Regex("\\s+"), " ").trim()
    }
}

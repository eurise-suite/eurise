package eu.eurise.core.insieme

import eu.eurise.core.insieme.Scheda.Kind
import eu.eurise.core.insieme.Scheda.State

/**
 * Le risposte per email a una scheda (tavola InsStessaScheda), senza Android: si prova con i test.
 *
 * Chi non usa Eurise risponde con il suo programma di posta: basta «fatto», «non posso», «vista», per un'uscita i numeri
 * delle date che vanno bene («1 e 3»), per un orario il suo numero, per una spesa «ho pagato». Contano solo le righe scritte
 * da lui: la citazione (righe con «>», «Il … ha scritto:», la firma dopo «--») resta fuori.
 */
object Risposte {
    private val quoteHeader = Regex("^(il|on|le|am|el|op) .*(scritto|wrote|écrit|schrieb|escribió|geschreven)\\s*:?$", RegexOption.IGNORE_CASE)

    /** Le righe scritte da chi risponde, senza la citazione. */
    fun own(reply: String): String = reply.replace("\r", "").lines().takeWhile { l ->
        val t = l.trim()
        !t.startsWith(">") && !quoteHeader.matches(t) && !t.startsWith("-----") && t != "—" && t != "--" &&
            !t.startsWith("Da: ") && !t.startsWith("From: ")
    }.joinToString("\n").trim()

    private fun words(s: String): List<String> = s.lowercase().replace('’', '\'').replace(Regex("[^\\p{L}\\p{N}' ]"), " ").trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun starts(head: List<String>, phrase: String): Boolean {
        val p = phrase.split(' ')
        return head.size >= p.size && head.subList(0, p.size) == p
    }

    private fun any(head: List<String>, phrases: List<String>) = phrases.any { starts(head, it) }

    private val done = listOf("fatto", "fatta", "fatti", "fatte", "ok fatto", "ok fatta", "tutto fatto", "è fatto", "è fatta", "l'ho fatto", "l'ho fatta",
        "ho fatto", "completato", "completata", "finito", "finita", "done", "erledigt", "fait", "hecho")
    private val declined = listOf("non posso", "non riesco", "non ce la faccio", "impossibile", "non mi è possibile", "purtroppo no", "can't", "cannot",
        "kann nicht", "no non posso")
    private val working = listOf("ci lavoro", "ci sto lavorando", "in corso", "me ne occupo", "ci penso io", "ci penso", "lo faccio", "la faccio", "working on it")
    private val seen = listOf("vista", "visto", "ricevuto", "ricevuta", "letto", "letta", "ok", "okay", "va bene", "preso nota", "seen", "noted", "gesehen")
    private val paid = listOf("ho pagato", "pagato", "pagata", "ho versato", "versato", "fatto il bonifico", "ho fatto il bonifico", "bonifico fatto", "paid")
    private val yes = listOf("sì", "si", "ci sono", "ci sarò", "presente", "certo", "yes", "ja", "oui")
    private val late = listOf("tardi", "più tardi", "arrivo tardi", "faccio tardi", "dopo", "late")
    private val no = listOf("no", "non ci sono", "non ci sarò", "niente", "salto", "nein", "non")
    private val none = listOf("nessuna", "nessuno", "nessuna data", "nessun orario", "nessuna va bene", "non posso", "no")
    private val ordinals = mapOf("prima" to 0, "primo" to 0, "seconda" to 1, "secondo" to 1, "terza" to 2, "terzo" to 2, "quarta" to 3, "quarto" to 3,
        "quinta" to 4, "quinto" to 4, "sesta" to 5, "sesto" to 5, "tutte" to -1, "tutti" to -1, "entrambe" to -1, "tutte e due" to -1)

    /**
     * Che cosa dice la risposta a una scheda di tipo [kind] con [options] scelte; null se non si capisce (allora resta da
     * guardare a chi l'ha mandata, come una normale email).
     */
    fun read(kind: Kind, reply: String, options: Int = 0): Scheda.Answer? {
        val text = own(reply)
        if (text.isEmpty()) return null
        val firstLine = text.lines().first { it.isNotBlank() }
        val head = words(firstLine).take(6)
        if (head.isEmpty()) return null
        val rest = text.lines().drop(1).joinToString("\n").trim().take(300)
        if (Scheda.choosing(kind)) return choices(head, firstLine, options, Scheda.single(kind))?.let { Scheda.Answer(choices = it, note = rest) }
        val state = when (kind) {
            Kind.Cena -> when {
                any(head, late) -> State.Late
                any(head, listOf("non ci sono", "non ci sarò")) || any(head, listOf("no")) -> State.No
                any(head, yes) -> State.Yes
                else -> null
            }
            Kind.Spesa, Kind.Regalo -> if (any(head, paid) || any(head, done)) State.Paid else null
            Kind.Proposta -> when {
                any(head, listOf("altro orario", "un altro orario", "non va bene", "non posso", "no", "spostiamo")) -> State.Other
                any(head, listOf("confermo", "conferma", "confermato", "va bene", "ok", "sì", "si", "perfetto", "d'accordo")) -> State.Confirmed
                else -> null
            }
            else -> when {
                any(head, declined) -> State.Declined
                head.first() == "non" -> null // «non ancora», «non l'ho fatto»: non è una risposta dei tasti
                any(head, done) -> if (kind == Kind.Condividi || kind == Kind.Elenco || kind == Kind.Decisione) State.Seen else State.Done
                any(head, working) -> if (kind == Kind.Affida) State.Working else null
                any(head, seen) -> State.Seen
                else -> null
            }
        } ?: return null
        if (state !in Scheda.actions(kind) && state != State.Working) return null
        return Scheda.Answer(state, note = rest)
    }

    /** I numeri delle scelte (da 1 nelle email, da 0 qui); «nessuna» = insieme vuoto; «tutte» = tutte. */
    private fun choices(head: List<String>, line: String, options: Int, single: Boolean): Set<Int>? {
        if (options <= 0) return null
        if (any(head, none) && !head.any { it.toIntOrNull() != null }) return emptySet()
        val nums = Regex("\\d+").findAll(line.take(80)).map { it.value.toInt() - 1 }.filter { it in 0 until options }.toMutableSet()
        if (nums.isEmpty()) {
            val joined = head.joinToString(" ")
            ordinals.forEach { (w, i) -> if (Regex("(^| )$w( |$)").containsMatchIn(joined)) { if (i < 0) nums.addAll(0 until options) else if (i < options) nums.add(i) } }
        }
        if (nums.isEmpty()) return null
        return if (single) setOf(nums.min()) else nums
    }
}

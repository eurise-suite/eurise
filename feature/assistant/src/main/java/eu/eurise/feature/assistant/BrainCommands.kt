package eu.eurise.feature.assistant

import eu.eurise.core.data.model.QuickAdd
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Le righe del cervello di Alfred («tipo|chi|testo|data|ora|canale», vedi BrainContract) diventano comandi della suite.
 * Come per la cattura rapida, dopo il modello ci sono controlli fissi, perché un modello piccolo sbaglia proprio lì
 * (misure della compilazione, 15 novembre): date e ore le decide [QuickAdd] dalla frase quando le trova; un'ora o un
 * giorno che nella frase non ci sono si tolgono; nomi, canali e testi che non vengono dalla frase si tolgono; una seconda
 * azione vale solo se la frase ne chiede più d'una. Una riga che non si capisce si salta.
 */
object BrainCommands {

    fun parse(lines: String, now: LocalDateTime, sentence: String = ""): List<Command> {
        val said = sentence.lowercase()
        val q = sentence.takeIf { it.isNotBlank() }?.let { QuickAdd.parse(it, now) }
        val all = lines.lineSequence().mapNotNull { line(it, now, said, q) }.toList()
        // Più azioni solo se la frase le unisce («… e …», «poi», una virgola): altrimenti la prima.
        return if (all.size > 1 && sentence.isNotBlank() && !MORE.containsMatchIn(said)) all.take(1) else all
    }

    private val MORE = Regex("\\b(e|ed|poi|anche|inoltre)\\b|,")
    private val TIME_WORDS = Regex("\\d|\\b(una|due|tre|quattro|cinque|sei|sette|otto|nove|dieci|undici|dodici|mezzogiorno|mezzanotte|pranzo|cena|stasera|stamattina|stanotte|mattina|pomeriggio|sera|mezza|quarto)\\b")
    private val DATE_WORDS = Regex("\\d|\\b(oggi|domani|dopodomani|stasera|stamattina|lunedì|martedì|mercoledì|giovedì|venerdì|sabato|domenica|settimana|mese|gennaio|febbraio|marzo|aprile|maggio|giugno|luglio|agosto|settembre|ottobre|novembre|dicembre)\\b")

    private fun line(raw: String, now: LocalDateTime, said: String, q: QuickAdd.Result?): Command? {
        val f = (raw.split('|').map { it.trim() } + List(6) { "" }).take(6)
        val kind = f[0]
        val checking = said.isNotEmpty()
        val clean = { s: String -> s.replace("**", "").replace("*", "").replace("_", " ").trim() }
        // Un nome vale solo se è nella frase (il modello a volte se ne inventa uno, «Eurise»).
        val who = clean(f[1]).takeIf { !checking || it.isBlank() || words(it).any { w -> w in said } }.orEmpty()
        val textRaw = clean(f[2])
        // Il testo deve venire dalla frase (almeno metà delle parole lunghe): niente note inventate.
        val text = textRaw.takeIf { !checking || overlap(it, said) }.orEmpty()
        val modelDate = runCatching { LocalDate.parse(f[3]) }.getOrNull()
        val modelTime = runCatching { LocalTime.parse(f[4]) }.getOrNull()
        val hasDate = q?.pieces?.any { it.kind == QuickAdd.Kind.Date } == true
        val hasTime = q?.pieces?.any { it.kind == QuickAdd.Kind.Time } == true
        val date = when {
            !checking -> modelDate
            hasDate -> q?.due
            DATE_WORDS.containsMatchIn(said) -> modelDate
            else -> null
        }
        val time = when {
            !checking -> modelTime
            hasTime -> q?.time
            TIME_WORDS.containsMatchIn(said) -> modelTime
            else -> null
        }
        val channel = f[5].takeIf { !checking || it in said }
        val title = text.replaceFirstChar { it.uppercase() }
        return when (kind) {
            "chiama" -> who.takeIf { it.isNotBlank() }?.let { Command.Call(it) }
            "messaggio" -> who.takeIf { it.isNotBlank() }?.let {
                Command.Message(it, textRaw.takeIf { t -> !checking || overlap(t, said, 0.34) }.orEmpty().replaceFirstChar { c -> c.uppercase() },
                    when (channel) {
                        "whatsapp" -> Channel.WhatsApp
                        "telegram" -> Channel.Telegram
                        "signal" -> Channel.Signal
                        "sms" -> Channel.Sms
                        else -> null
                    })
            }
            "nota" -> Command.Note(title)
            "evento" -> Command.Event(listOf(title, who).filter { it.isNotBlank() }.distinct().joinToString(" · ").ifBlank { "Impegno" }, null, date, time)
            "da_fare" -> title.takeIf { it.isNotBlank() }?.let { Command.Task(it, date, time) }
            "promemoria" -> title.takeIf { it.isNotBlank() }?.let { Command.Task(it, date ?: if (time != null) now.toLocalDate() else null, time) }
            "sveglia" -> time?.let { Command.Alarm(it, date) }
            "timer" -> textRaw.filter { it.isDigit() }.toIntOrNull()?.takeIf { it in 1..1440 }?.let { Command.Timer(it) }
            "naviga" -> who.ifBlank { text }.takeIf { it.isNotBlank() }?.let { Command.Navigate(it) }
            "apri" -> who.ifBlank { text }.takeIf { it.isNotBlank() }?.let { Command.Open(it) }
            // La domanda com'è stata detta: il modello tende ad allungarla.
            "domanda" -> Command.Free(said.takeIf { it.isNotBlank() }?.let { _ -> "" } ?: textRaw)
            else -> null
        }
    }

    private fun words(s: String) = s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }

    private fun overlap(text: String, said: String, min: Double = 0.5): Boolean {
        val w = words(text).filter { it.length > 3 }
        if (w.isEmpty()) return true
        return w.count { it in said } >= w.size * min
    }
}

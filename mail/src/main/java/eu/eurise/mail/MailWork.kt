package eu.eurise.mail

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Email da lavoro (tavole MailSmart e MailScriviLavoro), la logica senza Android: cosa chiedono a te («Per te»), cosa
 * aspetti tu («Aspetti»), il punto del mattino e l'ora giusta per mandare. Tutto si calcola sul telefono dalle sole
 * intestazioni già scaricate: niente esce.
 */
object MailWork {
    private const val DAY = 86_400_000L

    /** Una risposta che aspetti: da [to] su [subject] dopo [sentAt]; [by] è il giorno del promemoria (epoch day). */
    data class Waiting(val id: String, val to: String, val name: String, val subject: String, val sentAt: Long, val by: Long, val nudged: Boolean = false)

    /** Cosa chiede un'email, letto dall'oggetto: decide le risposte in un tocco. */
    enum class Ask { Approve, Confirm, Question, Urgent }

    private val APPROVE = Regex("\\b(approv|via libera|autorizz|ok per|nulla osta|firma|sign[- ]?off|approval|approve)", RegexOption.IGNORE_CASE)
    private val CONFIRM = Regex("\\b(confermi|conferma\\?|puoi confermare|mi confermi|ci sei|confirm|rsvp|disponibil)", RegexOption.IGNORE_CASE)
    private val URGENT = Regex("\\b(urgente|urgent|asap|entro oggi|scadenza|deadline|sollecito)", RegexOption.IGNORE_CASE)

    fun ask(subject: String): Ask? {
        val s = MailBalls.core(subject)
        return when {
            APPROVE.containsMatchIn(s) -> Ask.Approve
            CONFIRM.containsMatchIn(s) -> Ask.Confirm
            URGENT.containsMatchIn(s) -> Ask.Urgent
            s.trimEnd().endsWith("?") -> Ask.Question
            else -> null
        }
    }

    /** «Per te»: persone (non notifiche né newsletter), non da te, senza la tua risposta, degli ultimi 14 giorni. */
    fun forYou(inbox: List<Summary>, mine: Set<String>, now: Long): List<Summary> = inbox.filter {
        it.kind == Kind.People && !it.answered && it.fromAddress.lowercase() !in mine && now - it.date <= 14 * DAY
    }.sortedWith(compareByDescending<Summary> { ask(it.subject) != null && !it.seen }.thenByDescending { it.date })

    /** La risposta è arrivata: un'email da lui sullo stesso oggetto dopo l'invio. */
    fun answered(w: Waiting, inbox: List<Summary>): Boolean =
        inbox.any { it.fromAddress.equals(w.to, ignoreCase = true) && it.date > w.sentAt && MailBalls.core(it.subject) == MailBalls.core(w.subject) }

    /** Le attese ancora aperte, la più vecchia prima. */
    fun open(list: List<Waiting>, inbox: List<Summary>): List<Waiting> = list.filter { !answered(it, inbox) }.sortedBy { it.sentAt }

    fun days(since: Long, now: Long): Int = ((now - since) / DAY).toInt().coerceAtLeast(0)

    /** Il punto del mattino: quante chiedono qualcosa, chi aspetti da più tempo, quante non lette dalle persone. */
    data class Digest(val asks: Int, val unread: Int, val longest: Waiting?, val longestDays: Int, val stale: Int) {
        val empty get() = asks == 0 && unread == 0 && longest == null && stale == 0
    }

    fun digest(inbox: List<Summary>, waiting: List<Waiting>, balls: List<MailBalls.Ball>, mine: Set<String>, now: Long): Digest {
        val mail = forYou(inbox, mine, now)
        val open = open(waiting, inbox)
        val longest = open.firstOrNull()?.takeIf { days(it.sentAt, now) >= 2 }
        return Digest(mail.count { ask(it.subject) != null }, mail.count { !it.seen }, longest, longest?.let { days(it.sentAt, now) } ?: 0,
            balls.count { MailBalls.stale(it, now) })
    }

    /**
     * L'ora giusta per scrivere a [address]: l'ora a cui di solito scrive lui (mediana delle sue email, servono almeno
     * tre), arrotondata alla mezz'ora prima, il primo giorno feriale utile da [now]. Null se non si sa.
     */
    fun bestTime(inbox: List<Summary>, address: String, now: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? {
        val minutes = inbox.filter { it.fromAddress.equals(address.trim(), ignoreCase = true) }
            .map { Instant.ofEpochMilli(it.date).atZone(zone) }.filter { it.dayOfWeek.value <= 5 }
            .map { it.hour * 60 + it.minute }.sorted()
        if (minutes.size < 3) return null
        val m = minutes[minutes.size / 2] / 30 * 30
        return next(now, LocalTime.of(m / 60, m % 60))
    }

    /** Domani mattina alle 8:30, o lunedì se domani è sabato o domenica. */
    fun nextMorning(now: LocalDateTime): LocalDateTime = next(now.plusDays(1).toLocalDate().atStartOfDay(), LocalTime.of(8, 30))

    /** Il primo momento feriale da [now] (almeno mezz'ora dopo) all'ora [at]. */
    fun next(now: LocalDateTime, at: LocalTime): LocalDateTime {
        var d: LocalDate = now.toLocalDate()
        while (true) {
            val t = d.atTime(at)
            if (t.isAfter(now.plusMinutes(29)) && d.dayOfWeek != DayOfWeek.SATURDAY && d.dayOfWeek != DayOfWeek.SUNDAY) return t
            d = d.plusDays(1)
        }
    }

    /** «la mattina», «il pomeriggio», «la sera»: per dire perché quell'ora. */
    fun partOfDay(t: LocalTime): Int = when {
        t.hour < 12 -> 0
        t.hour < 18 -> 1
        else -> 2
    }

    fun encode(list: List<Waiting>): String = JSONArray().apply {
        list.forEach { put(JSONObject().put("id", it.id).put("to", it.to).put("name", it.name).put("subject", it.subject).put("sent", it.sentAt).put("by", it.by).put("nudged", it.nudged)) }
    }.toString()

    fun decode(s: String?): List<Waiting> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Waiting(o.optString("id"), o.optString("to"), o.optString("name"), o.optString("subject"), o.optLong("sent"), o.optLong("by"), o.optBoolean("nudged"))
                .takeIf { it.id.isNotEmpty() && it.to.isNotEmpty() }
        }
    }.getOrDefault(emptyList())
}

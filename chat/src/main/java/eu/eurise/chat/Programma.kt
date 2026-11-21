package eu.eurise.chat

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Invia più tardi, quando lavora, a scomparsa e Ricordamelo (tavola ChatProgramma), senza Android: si prova con i test.
 *
 * - I messaggi **programmati** restano sul telefono finché non è ora (Matrix non ha un invio differito comune a tutti i
 *   server): li manda l'app aperta o, ad app chiusa, un lavoro di WorkManager con la rete.
 * - I messaggi **a scomparsa** portano il campo [FADE] (dopo quanto dalla lettura) e li cancella dal server chi li ha
 *   mandati, appena vede la conferma di lettura dell'altro e passa il tempo: così spariscono anche da Element.
 */
object Programma {
    /** Il campo dei messaggi a scomparsa: `{"after_read_ms": 86400000}`. */
    const val FADE = "eu.eurise.fade"
    /** Un giorno dopo la lettura, come nella tavola. */
    const val FADE_MS = 86_400_000L

    // --- Orari proposti ---

    /** «domani alle 8:30». */
    fun tomorrowAt(now: LocalDateTime, h: Int, m: Int = 0): LocalDateTime = now.toLocalDate().plusDays(1).atTime(h, m)

    /** «lunedì alle 9»: il prossimo lunedì (mai oggi). */
    fun nextMonday(now: LocalDateTime, h: Int = 9): LocalDateTime =
        now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(h, 0)

    /** Quando comincia il lavoro: adesso se è già in orario (null), se no il prossimo giorno feriale alle 8 (vedi [Quiet]). */
    fun workStart(now: LocalDateTime): LocalDateTime? {
        if (!Quiet.isQuiet(now)) return null
        var d = if (now.hour < Quiet.TO_HOUR) now.toLocalDate() else now.toLocalDate().plusDays(1)
        while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.plusDays(1)
        return d.atTime(Quiet.TO_HOUR, 0)
    }

    /** Le scelte di Ricordamelo: tra un'ora, stasera alle 18 (solo prima delle 17), domani alle 9. */
    fun remindChoices(now: LocalDateTime): List<Pair<Remind, LocalDateTime>> = buildList {
        add(Remind.Hour to now.plusHours(1).truncatedTo(ChronoUnit.MINUTES))
        if (now.hour < 17) add(Remind.Evening to now.toLocalDate().atTime(18, 0))
        add(Remind.Tomorrow to tomorrowAt(now, 9))
    }

    enum class Remind { Hour, Evening, Tomorrow }

    /** Come dire un orario rispetto ad adesso: oggi, domani, un giorno di questa settimana, oppure una data. */
    enum class Day { Today, Tomorrow, Week, Date }

    fun day(at: LocalDateTime, now: LocalDateTime): Day {
        val d = ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate())
        return when {
            d <= 0L -> Day.Today
            d == 1L -> Day.Tomorrow
            d < 7L -> Day.Week
            else -> Day.Date
        }
    }

    fun time(t: LocalTime): String = "${t.hour}:${t.minute.toString().padStart(2, '0')}"

    fun toLocal(ms: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), zone)
    fun toMs(t: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long = t.atZone(zone).toInstant().toEpochMilli()

    // --- Messaggi programmati ---

    /** Un messaggio che parte più tardi. [fadeMs] > 0: anche a scomparsa. [work]: «quando lavora» (per l'etichetta). */
    data class Planned(
        val id: String,
        val account: String,
        val roomId: String,
        val text: String,
        val at: Long,
        val encrypted: Boolean,
        val fadeMs: Long = 0,
        val work: Boolean = false,
    )

    fun encodePlanned(list: List<Planned>): String = JSONArray().apply {
        list.forEach { p ->
            put(JSONObject().put("id", p.id).put("account", p.account).put("room", p.roomId).put("text", p.text).put("at", p.at)
                .put("enc", p.encrypted).put("fade", p.fadeMs).put("work", p.work))
        }
    }.toString()

    fun decodePlanned(s: String?): List<Planned> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Planned(o.optString("id"), o.optString("account"), o.optString("room"), o.optString("text"), o.optLong("at"),
                o.optBoolean("enc"), o.optLong("fade"), o.optBoolean("work")).takeIf { it.id.isNotEmpty() && it.roomId.isNotEmpty() }
        }
    }.getOrDefault(emptyList())

    /** Da mandare adesso, nell'ordine in cui erano previsti. */
    fun due(list: List<Planned>, account: String, now: Long): List<Planned> =
        list.filter { it.account == account && it.at <= now }.sortedBy { it.at }

    /** Il prossimo orario da attendere (per il lavoro ad app chiusa), o null. */
    fun next(list: List<Planned>): Long? = list.minOfOrNull { it.at }

    /** Il contenuto del messaggio, con il campo della scomparsa se serve. */
    fun content(text: String, fadeMs: Long): JSONObject = JSONObject().put("msgtype", "m.text").put("body", text).also {
        if (fadeMs > 0) it.put(FADE, JSONObject().put("after_read_ms", fadeMs))
    }

    /** Dopo quanto dalla lettura sparisce il messaggio [content] (0 = resta). */
    fun fadeOf(content: JSONObject?): Long = content?.optJSONObject(FADE)?.optLong("after_read_ms")?.coerceAtLeast(0) ?: 0

    // --- Messaggi a scomparsa ---

    /** Un messaggio mandato a scomparsa: [readAt] quando l'altro l'ha letto (0 = non ancora). */
    data class Fading(val account: String, val roomId: String, val eventId: String, val sentAt: Long, val afterMs: Long, val readAt: Long = 0) {
        val goneAt: Long get() = if (readAt > 0) readAt + afterMs else Long.MAX_VALUE
    }

    fun encodeFading(list: List<Fading>): String = JSONArray().apply {
        list.forEach { f ->
            put(JSONObject().put("account", f.account).put("room", f.roomId).put("event", f.eventId).put("sent", f.sentAt)
                .put("after", f.afterMs).put("read", f.readAt))
        }
    }.toString()

    fun decodeFading(s: String?): List<Fading> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            Fading(o.optString("account"), o.optString("room"), o.optString("event"), o.optLong("sent"), o.optLong("after"), o.optLong("read"))
                .takeIf { it.eventId.isNotEmpty() }
        }
    }.getOrDefault(emptyList())

    /**
     * Le conferme di lettura degli altri in una risposta di /sync: per stanza, l'ora più recente in cui qualcuno che non
     * sono io ha letto qualcosa. Basta per sapere che un messaggio mandato prima è stato letto.
     */
    fun receipts(sync: JSONObject, me: String): Map<String, Long> {
        val out = HashMap<String, Long>()
        val join = sync.optJSONObject("rooms")?.optJSONObject("join") ?: return out
        join.keys().forEach { roomId ->
            val events = join.optJSONObject(roomId)?.optJSONObject("ephemeral")?.optJSONArray("events") ?: return@forEach
            for (i in 0 until events.length()) {
                val e = events.optJSONObject(i) ?: continue
                if (e.optString("type") != "m.receipt") continue
                val c = e.optJSONObject("content") ?: continue
                c.keys().forEach { eventId ->
                    val byType = c.optJSONObject(eventId) ?: return@forEach
                    listOf("m.read", "m.read.private").forEach { t ->
                        val users = byType.optJSONObject(t) ?: return@forEach
                        users.keys().forEach { user ->
                            if (user != me) {
                                val ts = users.optJSONObject(user)?.optLong("ts") ?: 0L
                                if (ts > (out[roomId] ?: 0L)) out[roomId] = ts
                            }
                        }
                    }
                }
            }
        }
        return out
    }

    /** I messaggi a scomparsa con la lettura segnata dalle conferme arrivate (solo quelli mandati prima). */
    fun markRead(list: List<Fading>, account: String, read: Map<String, Long>): List<Fading> = list.map { f ->
        val ts = read[f.roomId] ?: return@map f
        if (f.account == account && f.readAt == 0L && ts >= f.sentAt) f.copy(readAt = ts) else f
    }

    /** Da cancellare adesso. */
    fun gone(list: List<Fading>, account: String, now: Long): List<Fading> = list.filter { it.account == account && it.goneAt <= now }

    /** La data per l'etichetta «il 24/11». */
    fun shortDate(d: LocalDate): String = "${d.dayOfMonth}/${d.monthValue}"
}

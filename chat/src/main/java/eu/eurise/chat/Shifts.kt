package eu.eurise.chat

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.YearMonth
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turni nei gruppi della Chat (tavole ChatTurni e ChatTurniCrea, approvate il 17 novembre), con le funzioni generali di
 * Bussola CA per fare, importare ed esportare i turni, declinabili per ogni contesto (reparto, negozio, associazione,
 * squadra, famiglia): titolari, sostituti con il loro periodo e reperibili esterni; preferenze, indisponibilità e monte
 * ore; «Genera» che rispetta riposi, preferenze e i turni fissati a mano; turni in doppia con chi tiene il telefono;
 * diurni divisi; straordinari a passi di 15 minuti con il motivo; cambi registrati che si annullano; turni effettivi e
 * riepilogo delle ore. Importazione ed esportazione stanno in [ShiftsIO].
 *
 * Tutto vive nella stanza cifrata del gruppo come messaggi con un tipo (come le Spese):
 * - [PLAN]: il piano (turni, persone, caselle), scritto da chi coordina; vale l'ultima versione del primo autore;
 * - [AVAIL]: indisponibilità e preferenze di una persona, valgono solo se le manda lei;
 * - [CHANGE]: scambio, cessione o straordinario, chiesti da chi ha il turno;
 * - [REPLY]: sì o no di chi riceve la richiesta, o l'annullamento di un cambio.
 * Il `body` di riserva fa leggere tutto anche da Element. I conti si fanno sul telefono: nessun servizio esterno.
 */
object Shifts {
    const val PLAN = "eu.eurise.shifts.plan"
    const val AVAIL = "eu.eurise.shifts.avail"
    const val CHANGE = "eu.eurise.shifts.change"
    const val REPLY = "eu.eurise.shifts.reply"
    val TYPES = setOf(PLAN, AVAIL, CHANGE, REPLY)

    /** Riposo minimo tra due turni (11 ore, come nella tavola e nella legge sull'orario di lavoro). */
    const val REST_MINUTES = 11 * 60
    /** Giorni di fila al massimo: almeno un riposo ogni sette. */
    const val MAX_IN_A_ROW = 6

    // --- Il piano ---

    /**
     * Un tipo di turno. [end] prima o uguale a [start] vuol dire che finisce il giorno dopo (notte). [days] sono i giorni
     * in cui c'è (1 = lunedì … 7 = domenica; con [Plan.holidays] i festivi contano come domenica e i prefestivi come
     * sabato, come nella continuità assistenziale). [people] è quante persone servono (2 o 3 = in doppia o tripla).
     */
    data class Type(
        val code: String,
        val name: String,
        val start: LocalTime,
        val end: LocalTime,
        val people: Int = 1,
        val days: Set<Int> = (1..7).toSet(),
    ) {
        val minutes: Int get() = Duration.between(start, end).toMinutes().toInt().let { if (it <= 0) it + 24 * 60 else it }
        fun begin(date: LocalDate): LocalDateTime = date.atTime(start)
        fun finish(date: LocalDate): LocalDateTime = begin(date).plusMinutes(minutes.toLong())
    }

    enum class Role { Holder, Substitute, External }

    /**
     * Chi fa i turni. [target] è il monte ore del mese in minuti (0 = nessuno). Un sostituto con [replaces] e il periodo
     * [from]–[to] in quelle date lavora al posto del titolare; un reperibile esterno non entra mai in «Genera».
     */
    data class Person(
        val id: String,
        val role: Role = Role.Holder,
        val target: Int = 0,
        val replaces: String? = null,
        val from: LocalDate? = null,
        val to: LocalDate? = null,
    ) {
        fun replacing(date: LocalDate): Boolean = replaces != null && from != null && to != null && !date.isBefore(from) && !date.isAfter(to)
    }

    /**
     * Una casella: un turno di un giorno con le sue persone. [phone] è chi tiene il telefono quando sono in più;
     * con [split] il turno è diviso in due: la prima persona fino a [split], la seconda da lì alla fine.
     * [locked] = fissato a mano: «Genera» non lo tocca.
     */
    data class Entry(
        val date: LocalDate,
        val type: String,
        val people: List<String>,
        val phone: String? = null,
        val split: LocalTime? = null,
        val locked: Boolean = false,
    ) {
        val key: String get() = "$date|$type"
    }

    data class Plan(
        val id: String,
        val types: List<Type>,
        val people: List<Person>,
        val entries: List<Entry>,
        val rest: Int = REST_MINUTES,
        /** Festivi italiani (e [localHolidays]) come domenica, prefestivi come sabato. */
        val holidays: Boolean = false,
        val localHolidays: List<MonthDay> = emptyList(),
        val calendar: Boolean = true,
        val reminder: Boolean = true,
        val time: Long = 0L,
        /** Giorni di lavoro di fila al massimo (un riposo ogni [maxInARow] + 1). */
        val maxInARow: Int = MAX_IN_A_ROW,
        /** Intestazione dei documenti esportati (ente, reparto, negozio…), scelta da chi coordina. */
        val title: String = "",
    ) {
        fun type(code: String) = types.firstOrNull { it.code == code }
        fun person(id: String) = people.firstOrNull { it.id == id }
    }

    // --- Disponibilità e preferenze di una persona ---

    /** Non disponibile da [from] a [to] (compresi); con [types] solo per quei turni, altrimenti per tutti. */
    data class Off(val from: LocalDate, val to: LocalDate, val types: Set<String> = emptySet()) {
        fun covers(date: LocalDate, type: String) = !date.isBefore(from) && !date.isAfter(to) && (types.isEmpty() || type in types)
    }

    data class Avail(
        val person: String,
        val off: List<Off> = emptyList(),
        /** Turni preferiti ed evitati (codici); giorni della settimana evitati (1–7). */
        val prefer: Set<String> = emptySet(),
        val avoid: Set<String> = emptySet(),
        val avoidDays: Set<Int> = emptySet(),
        val time: Long = 0L,
    ) {
        fun unavailable(date: LocalDate, type: String) = off.any { it.covers(date, type) }
    }

    // --- Cambi e straordinari ---

    enum class Kind { Swap, Give, Extra }

    /**
     * Un cambio chiesto da [by] sul suo turno [date]/[type].
     * - [Kind.Swap]: con [to] che cede il suo [withDate]/[withType];
     * - [Kind.Give]: il turno passa a [to] (o a chiunque lo prenda, se [to] è null);
     * - [Kind.Extra]: [minutes] in più (a passi di 15) per [reason]; vale subito.
     */
    data class Change(
        val id: String,
        val kind: Kind,
        val by: String,
        val date: LocalDate,
        val type: String,
        val to: String? = null,
        val withDate: LocalDate? = null,
        val withType: String? = null,
        val minutes: Int = 0,
        val reason: String = "",
        val time: Long = 0L,
    )

    data class Reply(val change: String, val from: String, val accept: Boolean, val undo: Boolean = false, val time: Long = 0L)

    enum class State { Waiting, Accepted, Refused, Undone, Stale }

    data class Applied(val change: Change, val state: State, val taker: String? = null)

    // --- Giorni ---

    /** Festività nazionali italiane dell'anno, Pasqua e Pasquetta comprese. */
    fun italianHolidays(year: Int): Set<LocalDate> {
        val easter = easter(year)
        return setOf(
            LocalDate.of(year, 1, 1), LocalDate.of(year, 1, 6), easter, easter.plusDays(1), LocalDate.of(year, 4, 25),
            LocalDate.of(year, 5, 1), LocalDate.of(year, 6, 2), LocalDate.of(year, 8, 15), LocalDate.of(year, 11, 1),
            LocalDate.of(year, 12, 8), LocalDate.of(year, 12, 25), LocalDate.of(year, 12, 26),
        )
    }

    /** Domenica di Pasqua (algoritmo anonimo gregoriano). */
    fun easter(year: Int): LocalDate {
        val a = year % 19; val b = year / 100; val c = year % 100; val d = b / 4; val e = b % 4
        val f = (b + 8) / 25; val g = (b - f + 1) / 3; val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7; val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31; val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    fun isHoliday(plan: Plan, date: LocalDate): Boolean =
        date in italianHolidays(date.year) || plan.localHolidays.any { it.month == date.month && it.dayOfMonth == date.dayOfMonth }

    /** Il «giorno» per i turni: 7 per domeniche e festivi, 6 per sabati e prefestivi (se il piano li usa). */
    fun dayKind(plan: Plan, date: LocalDate): Int {
        val dow = date.dayOfWeek.value
        if (!plan.holidays) return dow
        if (dow == 7 || isHoliday(plan, date)) return 7
        if (dow == 6 || isHoliday(plan, date.plusDays(1))) return 6
        return dow
    }

    fun typesOn(plan: Plan, date: LocalDate): List<Type> {
        val k = dayKind(plan, date)
        return plan.types.filter { k in it.days }.sortedBy { it.start }
    }

    // --- Persone e intervalli ---

    /** Chi lavora davvero in un intervallo della casella: con il turno diviso, metà a testa. */
    data class Slot(val person: String, val start: LocalDateTime, val end: LocalDateTime, val entry: Entry) {
        val minutes: Long get() = Duration.between(start, end).toMinutes()
    }

    fun slots(plan: Plan, e: Entry): List<Slot> {
        val t = plan.type(e.type) ?: return emptyList()
        val b = t.begin(e.date); val f = t.finish(e.date)
        val split = e.split
        if (split != null && e.people.size >= 2) {
            var mid = e.date.atTime(split)
            if (!mid.isAfter(b)) mid = mid.plusDays(1)
            if (mid.isBefore(f)) return listOf(Slot(e.people[0], b, mid, e), Slot(e.people[1], mid, f, e)) +
                e.people.drop(2).map { Slot(it, b, f, e) }
        }
        return e.people.map { Slot(it, b, f, e) }
    }

    fun allSlots(plan: Plan, entries: List<Entry> = plan.entries): List<Slot> = entries.flatMap { slots(plan, it) }

    /** Chi può entrare in «Genera» in quel giorno: niente reperibili esterni; i titolari sostituiti restano fuori. */
    fun eligible(plan: Plan, person: Person, date: LocalDate): Boolean = when (person.role) {
        Role.External -> false
        Role.Substitute -> person.replaces == null || person.replacing(date)
        Role.Holder -> plan.people.none { it.replaces == person.id && it.replacing(date) }
    }

    // --- Controlli ---

    enum class Problem { Unavailable, Overlap, Rest, InARow }

    /**
     * Cosa non va se [person] fa l'intervallo [start]–[end] avendo già [busy] (gli altri suoi turni): sovrapposizione,
     * meno di [Plan.rest] minuti di riposo prima o dopo, troppi giorni di fila, indisponibilità dichiarata.
     */
    fun problems(
        plan: Plan, person: String, date: LocalDate, type: String, start: LocalDateTime, end: LocalDateTime,
        busy: List<Slot>, avail: Avail?,
    ): Set<Problem> {
        val out = HashSet<Problem>()
        if (avail?.unavailable(date, type) == true) out += Problem.Unavailable
        for (s in busy) {
            if (s.person != person) continue
            if (s.start < end && start < s.end) { out += Problem.Overlap; continue }
            val gap = if (!s.end.isAfter(start)) Duration.between(s.end, start) else Duration.between(end, s.start)
            if (gap.toMinutes() < plan.rest) out += Problem.Rest
        }
        val days = busy.filter { it.person == person }.map { it.start.toLocalDate() }.toSet() + date
        var run = 1
        var d = date.minusDays(1)
        while (d in days) { run++; d = d.minusDays(1) }
        d = date.plusDays(1)
        while (d in days) { run++; d = d.plusDays(1) }
        if (run > plan.maxInARow) out += Problem.InARow
        return out
    }

    /**
     * Cosa non va se [person] prende il posto di [from] nella casella [take], lasciando la sua [drop] (scambio) o
     * niente (cessione). Si controllano i turni effettivi [entries] tolte le due caselle in gioco.
     */
    fun check(plan: Plan, entries: List<Entry>, avail: Avail?, person: String, take: Entry, from: String, drop: Entry? = null): Set<Problem> {
        if (person in take.people) return setOf(Problem.Overlap)
        val after = take.copy(people = take.people.map { if (it == from) person else it })
        val busy = allSlots(plan, entries.filter { it.key != take.key && it.key != drop?.key }).filter { it.person == person }
        return slots(plan, after).filter { it.person == person }
            .flatMap { problems(plan, person, take.date, take.type, it.start, it.end, busy, avail) }.toSet()
    }

    // --- Genera ---

    data class Generated(val entries: List<Entry>, val uncovered: List<Pair<LocalDate, String>>)

    /**
     * Crea i turni da [from] per [days] giorni. Restano come sono i turni fuori dal periodo e quelli fissati a mano.
     * Per ogni casella sceglie, tra chi può (niente indisponibilità, sovrapposizioni, meno del riposo minimo, più giorni
     * di fila del consentito), chi è più indietro rispetto al suo monte ore del mese, poi chi ha fatto meno turni di quel tipo;
     * le preferenze spostano la scelta, gli «evito» la sconsigliano. A parità decide l'ordine dei nomi, così ogni telefono
     * ottiene gli stessi turni. Le caselle senza nessuno possibile restano scoperte e vengono elencate.
     */
    fun generate(plan: Plan, avail: Map<String, Avail>, from: LocalDate, days: Int): Generated {
        val until = from.plusDays(days.toLong())
        fun inRange(d: LocalDate) = !d.isBefore(from) && d.isBefore(until)
        val kept = plan.entries.filter { !inRange(it.date) || it.locked }
        val out = kept.toMutableList()
        val busy = HashMap<String, MutableList<Slot>>()
        allSlots(plan, kept).forEach { busy.getOrPut(it.person) { ArrayList() } += it }
        val typeCount = HashMap<Pair<String, String>, Int>()
        val phoneCount = HashMap<String, Int>()
        busy.values.flatten().forEach { typeCount.merge(it.person to it.entry.type, 1, Int::plus) }
        kept.forEach { e -> e.phone?.let { phoneCount.merge(it, 1, Int::plus) } }
        // Equità sul periodo che si genera (non sul mese di calendario, che a cavallo azzererebbe i conti), pesata sul
        // monte ore: chi ha un monte ore doppio riceve circa il doppio dei turni.
        val window = HashMap<String, Long>()
        allSlots(plan, kept.filter { inRange(it.date) }).forEach { window.merge(it.person, it.minutes, Long::plus) }
        val avgTarget = plan.people.filter { it.target > 0 }.map { it.target }.average().takeIf { !it.isNaN() && it > 0 }
        fun weight(p: Person) = if (p.target > 0 && avgTarget != null) p.target / avgTarget else 1.0
        val uncovered = ArrayList<Pair<LocalDate, String>>()
        var d = from
        while (d.isBefore(until)) {
            val day = d
            val types = typesOn(plan, day).filter { t -> kept.none { it.date == day && it.type == t.code } }
            // Le caselle del giorno si scelgono insieme (chi preferisce la notte non viene preso prima per la mattina):
            // si provano le combinazioni migliori e vince quella con il punteggio più basso.
            val seats = types.flatMap { t -> List(t.people) { t } }
            fun score(p: Person, t: Type, extra: Long): Double {
                val a = avail[p.id]
                // In minuti: le ore già date, poi un turno preferito vale 4 ore in meno, uno evitato 8 in più.
                return ((window[p.id] ?: 0L) + extra) / weight(p) + (typeCount[p.id to t.code] ?: 0) * 10.0 +
                    (if (a != null && t.code in a.prefer) -240.0 else 0.0) +
                    (if (a != null && t.code in a.avoid) 480.0 else 0.0) +
                    (if (a != null && dayKind(plan, day) in a.avoidDays) 480.0 else 0.0)
            }
            val pool = plan.people.filter { eligible(plan, it, day) }
            // Chi può fare ogni turno del giorno rispetto ai turni già dati (calcolato una volta sola per il giorno);
            // nella ricerca resta da controllare solo il resto della stessa giornata.
            val ok = types.associate { t ->
                t.code to pool.associate { p ->
                    val near = busy[p.id].orEmpty().filter { it.start.toLocalDate() >= day.minusDays(7) && it.start.toLocalDate() <= day.plusDays(7) }
                    p.id to problems(plan, p.id, day, t.code, t.begin(day), t.finish(day), near, avail[p.id]).isEmpty()
                }
            }
            val width = when { seats.size <= 3 -> 6; seats.size <= 5 -> 4; else -> 3 }
            var best: List<String?>? = null
            var bestScore = Double.MAX_VALUE
            val pick = arrayOfNulls<String>(seats.size)
            val temp = ArrayList<Slot>()
            fun search(i: Int, acc: Double) {
                if (acc >= bestScore) return
                if (i == seats.size) { bestScore = acc; best = pick.toList(); return }
                val t = seats[i]
                val start = t.begin(day); val end = t.finish(day)
                val sameEntry = (0 until i).filter { seats[it] == t }.mapNotNull { pick[it] }
                val options = pool.asSequence()
                    .filter { p ->
                        val today = temp.filter { it.person == p.id }
                        p.id !in sameEntry && ok.getValue(t.code)[p.id] == true &&
                            (today.isEmpty() || problems(plan, p.id, day, t.code, start, end, today, null).isEmpty())
                    }
                    .map { p -> p to score(p, t, temp.filter { it.person == p.id }.sumOf { it.minutes }) }
                    .sortedWith(compareBy<Pair<Person, Double>> { it.second }.thenBy { it.first.id })
                    .take(width).toList()
                for ((p, sc) in options) {
                    pick[i] = p.id
                    val slot = Slot(p.id, start, end, Entry(day, t.code, listOf(p.id)))
                    temp += slot
                    search(i + 1, acc + sc)
                    temp.remove(slot)
                }
                // Lasciarla scoperta costa moltissimo: solo se non c'è nessun'altra strada.
                pick[i] = null
                search(i + 1, acc + 1e9)
            }
            search(0, 0.0)
            val chosenAll = best ?: List(seats.size) { null }
            for (t in types) {
                val chosen = seats.indices.filter { seats[it] == t }.mapNotNull { chosenAll[it] }
                chosen.forEach { id ->
                    busy.getOrPut(id) { ArrayList() } += Slot(id, t.begin(day), t.finish(day), Entry(day, t.code, listOf(id)))
                    window.merge(id, t.minutes.toLong(), Long::plus)
                    typeCount.merge(id to t.code, 1, Int::plus)
                }
                if (chosen.size < t.people) uncovered += day to t.code
                if (chosen.isEmpty()) continue
                // In doppia il telefono va a chi l'ha tenuto meno («una volta a testa»).
                val phone = if (chosen.size > 1) chosen.minWith(compareBy<String> { phoneCount[it] ?: 0 }.thenBy { it }) else null
                phone?.let { phoneCount.merge(it, 1, Int::plus) }
                out += Entry(day, t.code, chosen.sorted(), phone)
            }
            d = d.plusDays(1)
        }
        return Generated(out.sortedWith(compareBy<Entry> { it.date }.thenBy { plan.type(it.type)?.start }), uncovered)
    }

    /** «Come la settimana scorsa»: copia i sette giorni prima di [from] sulle [weeks] settimane da [from]. */
    fun repeatLastWeek(plan: Plan, from: LocalDate, weeks: Int): List<Entry> {
        val last = plan.entries.filter { !it.date.isBefore(from.minusDays(7)) && it.date.isBefore(from) }
        val until = from.plusWeeks(weeks.toLong())
        val keep = plan.entries.filter { it.date.isBefore(from) || !it.date.isBefore(until) || it.locked }
        val copies = (0 until weeks).flatMap { w -> last.map { it.copy(date = it.date.plusWeeks(w + 1L), locked = false) } }
            .filter { c -> keep.none { it.key == c.key } }
        return (keep + copies).sortedBy { it.date }
    }

    // --- Turni effettivi ---

    /**
     * I turni effettivi: il piano con i cambi accettati (in ordine), e lo stato di ogni richiesta. Un cambio che non
     * torna più (chi l'ha chiesto non è più in quel turno) resta «superato» e non si applica.
     */
    data class Effective(val entries: List<Entry>, val changes: List<Applied>, val extras: List<Change>)

    fun effective(plan: Plan, changes: List<Change>, replies: List<Reply>, coordinator: String? = null): Effective {
        val byKey = LinkedHashMap<String, Entry>()
        plan.entries.forEach { byKey[it.key] = it }
        val applied = ArrayList<Applied>()
        val extras = ArrayList<Change>()
        for (c in changes.sortedBy { it.time }) {
            val rs = replies.filter { it.change == c.id }.sortedBy { it.time }
            // Annulla chi l'ha chiesto, chi l'ha accettato o chi coordina: il turno torna com'era.
            val taker = rs.firstOrNull { r -> !r.undo && r.accept && r.from != c.by && (c.to == null || r.from == c.to) }?.from
            if (rs.any { it.undo && (it.from == c.by || it.from == taker || (coordinator != null && it.from == coordinator)) }) {
                applied += Applied(c, State.Undone, taker); continue
            }
            if (c.kind == Kind.Extra) {
                val e = byKey["${c.date}|${c.type}"]
                if (e != null && c.by in e.people && c.minutes > 0) { extras += c; applied += Applied(c, State.Accepted, c.by) }
                else applied += Applied(c, State.Stale)
                continue
            }
            val answer = rs.firstOrNull { r -> !r.undo && r.from != c.by && (c.to == null || r.from == c.to) }
            if (answer == null) { applied += Applied(c, State.Waiting); continue }
            if (!answer.accept) { applied += Applied(c, State.Refused, answer.from); continue }
            val mine = byKey["${c.date}|${c.type}"]
            if (mine == null || c.by !in mine.people || answer.from in mine.people) { applied += Applied(c, State.Stale); continue }
            when (c.kind) {
                Kind.Give -> byKey[mine.key] = mine.swapPerson(c.by, answer.from)
                Kind.Swap -> {
                    val theirs = byKey["${c.withDate}|${c.withType}"]
                    if (theirs == null || answer.from !in theirs.people || c.by in theirs.people) { applied += Applied(c, State.Stale); continue }
                    byKey[mine.key] = mine.swapPerson(c.by, answer.from)
                    byKey[theirs.key] = theirs.swapPerson(answer.from, c.by)
                }
                Kind.Extra -> Unit
            }
            applied += Applied(c, State.Accepted, answer.from)
        }
        return Effective(byKey.values.toList(), applied, extras)
    }

    private fun Entry.swapPerson(out: String, inn: String): Entry =
        copy(people = people.map { if (it == out) inn else it }, phone = if (phone == out) inn else phone)

    // --- Ore ---

    data class Hours(val person: String, val planned: Long, val worked: Long, val extra: Long, val target: Int, val shifts: Int, val nights: Int)

    /** Ore del mese per persona: programmate, svolte (con i cambi), straordinari, monte ore; in minuti. */
    fun hours(plan: Plan, eff: Effective, month: YearMonth): List<Hours> {
        fun inMonth(s: Slot) = YearMonth.from(s.start) == month
        val planned = allSlots(plan).filter(::inMonth).groupBy { it.person }
        val worked = allSlots(plan, eff.entries).filter(::inMonth).groupBy { it.person }
        val extra = eff.extras.filter { YearMonth.from(it.date) == month }.groupBy { it.by }.mapValues { (_, v) -> v.sumOf { it.minutes.toLong() } }
        val ids = (plan.people.map { it.id } + planned.keys + worked.keys).distinct()
        return ids.map { id ->
            val w = worked[id].orEmpty()
            Hours(id, planned[id].orEmpty().sumOf { it.minutes }, w.sumOf { it.minutes }, extra[id] ?: 0,
                plan.person(id)?.target ?: 0, w.size, w.count { it.start.toLocalTime() >= LocalTime.of(19, 0) || it.end.toLocalDate() != it.start.toLocalDate() })
        }.sortedBy { it.person }
    }

    /** Chi è in turno all'ora [now] e chi viene dopo, nelle prossime 24 ore. */
    fun now(plan: Plan, entries: List<Entry>, now: LocalDateTime): Pair<List<Slot>, List<Slot>> {
        val all = allSlots(plan, entries.filter { !it.date.isBefore(now.toLocalDate().minusDays(1)) && !it.date.isAfter(now.toLocalDate().plusDays(1)) })
        val current = all.filter { !it.start.isAfter(now) && it.end.isAfter(now) }.sortedBy { it.start }
        val next = all.filter { it.start.isAfter(now) && it.start.isBefore(now.plusHours(24)) }.sortedBy { it.start }
        return current to next
    }

    // --- Messaggi ---

    private fun LocalDate?.s() = this?.toString()
    private fun date(s: String?) = s?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    private fun time(s: String?) = s?.takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    fun content(p: Plan, body: String): JSONObject = JSONObject().put("msgtype", PLAN).put("body", body).put(PLAN, JSONObject()
        .put("id", p.id).put("rest", p.rest).put("holidays", p.holidays).put("calendar", p.calendar).put("reminder", p.reminder)
        .put("in_a_row", p.maxInARow).put("title", p.title)
        .put("local_holidays", JSONArray(p.localHolidays.map { "%02d-%02d".format(it.monthValue, it.dayOfMonth) }))
        .put("types", JSONArray(p.types.map { t ->
            JSONObject().put("code", t.code).put("name", t.name).put("start", t.start.toString()).put("end", t.end.toString())
                .put("people", t.people).put("days", JSONArray(t.days.sorted()))
        }))
        .put("people", JSONArray(p.people.map { x ->
            JSONObject().put("id", x.id).put("role", x.role.name.lowercase()).put("target", x.target)
                .apply { x.replaces?.let { put("replaces", it) }; x.from?.let { put("from", it.toString()) }; x.to?.let { put("to", it.toString()) } }
        }))
        .put("entries", JSONArray(p.entries.map { e ->
            JSONObject().put("d", e.date.toString()).put("t", e.type).put("p", JSONArray(e.people))
                .apply { e.phone?.let { put("ph", it) }; e.split?.let { put("sp", it.toString()) }; if (e.locked) put("lock", true) }
        })))

    fun plan(content: JSONObject, time: Long): Plan? = runCatching {
        val o = content.getJSONObject(PLAN)
        val types = o.getJSONArray("types").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.mapNotNull { t ->
            val start = time(t.optString("start")) ?: return@mapNotNull null
            val end = time(t.optString("end")) ?: return@mapNotNull null
            val code = t.optString("code").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Type(code, t.optString("name", code), start, end, t.optInt("people", 1).coerceIn(1, 3),
                t.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.getInt(it) }.filter { it in 1..7 }.toSet() } ?: (1..7).toSet())
        }
        val people = o.getJSONArray("people").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.map { x ->
            Person(x.getString("id"), Role.entries.firstOrNull { it.name.equals(x.optString("role"), true) } ?: Role.Holder,
                x.optInt("target"), x.optString("replaces").ifBlank { null }, date(x.optString("from")), date(x.optString("to")))
        }
        val entries = o.optJSONArray("entries")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty().mapNotNull { e ->
            val d = date(e.optString("d")) ?: return@mapNotNull null
            val ps = e.optJSONArray("p")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            Entry(d, e.getString("t"), ps, e.optString("ph").ifBlank { null }, time(e.optString("sp")), e.optBoolean("lock"))
        }
        val local = o.optJSONArray("local_holidays")?.let { a -> (0 until a.length()).mapNotNull { runCatching { MonthDay.parse("--" + a.getString(it)) }.getOrNull() } }.orEmpty()
        Plan(o.getString("id"), types, people, entries, o.optInt("rest", REST_MINUTES), o.optBoolean("holidays"), local,
            o.optBoolean("calendar", true), o.optBoolean("reminder", true), time,
            o.optInt("in_a_row", MAX_IN_A_ROW).coerceIn(1, 14), o.optString("title"))
    }.getOrNull()

    fun content(a: Avail, body: String): JSONObject = JSONObject().put("msgtype", AVAIL).put("body", body).put(AVAIL, JSONObject()
        .put("off", JSONArray(a.off.map { JSONObject().put("from", it.from.toString()).put("to", it.to.toString()).put("types", JSONArray(it.types.sorted())) }))
        .put("prefer", JSONArray(a.prefer.sorted())).put("avoid", JSONArray(a.avoid.sorted())).put("avoid_days", JSONArray(a.avoidDays.sorted())))

    /** Le disponibilità valgono solo per chi le ha mandate: [sender] viene dal messaggio, non dal contenuto. */
    fun avail(content: JSONObject, sender: String, time: Long): Avail? = runCatching {
        val o = content.getJSONObject(AVAIL)
        fun strings(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty().toSet()
        val off = o.optJSONArray("off")?.let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.orEmpty().mapNotNull { x ->
            val f = date(x.optString("from")) ?: return@mapNotNull null
            val t = date(x.optString("to")) ?: f
            if (t.isBefore(f)) null else Off(f, t, x.optJSONArray("types")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty().toSet())
        }
        val days = o.optJSONArray("avoid_days")?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty().filter { it in 1..7 }.toSet()
        Avail(sender, off, strings("prefer"), strings("avoid"), days, time)
    }.getOrNull()

    fun content(c: Change, body: String): JSONObject = JSONObject().put("msgtype", CHANGE).put("body", body).put(CHANGE, JSONObject()
        .put("id", c.id).put("kind", c.kind.name.lowercase()).put("date", c.date.toString()).put("type", c.type)
        .apply {
            c.to?.let { put("to", it) }; c.withDate?.let { put("with_date", it.toString()) }; c.withType?.let { put("with_type", it) }
            if (c.kind == Kind.Extra) put("minutes", c.minutes).put("reason", c.reason)
        })

    fun change(content: JSONObject, sender: String, time: Long): Change? = runCatching {
        val o = content.getJSONObject(CHANGE)
        val kind = Kind.entries.first { it.name.equals(o.getString("kind"), true) }
        val c = Change(o.getString("id"), kind, sender, LocalDate.parse(o.getString("date")), o.getString("type"),
            o.optString("to").ifBlank { null }, date(o.optString("with_date")), o.optString("with_type").ifBlank { null },
            o.optInt("minutes"), o.optString("reason"), time)
        when (kind) {
            Kind.Swap -> c.takeIf { it.to != null && it.withDate != null && it.withType != null && it.to != sender }
            Kind.Give -> c.takeIf { it.to != sender }
            Kind.Extra -> c.takeIf { it.minutes in 15..(12 * 60) && it.minutes % 15 == 0 }
        }
    }.getOrNull()

    fun content(r: Reply, body: String): JSONObject = JSONObject().put("msgtype", REPLY).put("body", body).put(REPLY, JSONObject()
        .put("change", r.change).put("accept", r.accept).apply { if (r.undo) put("undo", true) })

    fun reply(content: JSONObject, sender: String, time: Long): Reply? = runCatching {
        val o = content.getJSONObject(REPLY)
        Reply(o.getString("change"), sender, o.optBoolean("accept"), o.optBoolean("undo"), time)
    }.getOrNull()

    /** «1 h 30», «7 h», «45 min». */
    fun duration(minutes: Long): String {
        val h = minutes / 60; val m = minutes % 60
        return when { h == 0L -> "$m min"; m == 0L -> "$h h"; else -> "$h h %02d".format(m) }
    }

    fun mondayOf(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())

    // --- Modelli pronti ---

    /**
     * Modelli pronti, da cui si parte e che si cambiano a piacere: valgono per un reparto, un negozio, un'associazione,
     * una squadra, la famiglia. «A mano» parte da un turno solo.
     */
    enum class Preset { Ward, TwelveHours, Shop, Continuity, Blank }

    fun preset(p: Preset): List<Type> = when (p) {
        Preset.Ward -> ward()
        Preset.TwelveHours -> listOf(
            Type("G", "Giorno", LocalTime.of(8, 0), LocalTime.of(20, 0)),
            Type("N", "Notte", LocalTime.of(20, 0), LocalTime.of(8, 0)),
        )
        Preset.Shop -> listOf(
            Type("A", "Apertura", LocalTime.of(9, 0), LocalTime.of(14, 0), days = (1..6).toSet()),
            Type("C", "Chiusura", LocalTime.of(14, 0), LocalTime.of(20, 0), days = (1..6).toSet()),
        )
        Preset.Continuity -> continuity()
        Preset.Blank -> listOf(Type("T", "Turno", LocalTime.of(9, 0), LocalTime.of(13, 0)))
    }

    /** Reparto: mattina, pomeriggio, notte tutti i giorni. */
    fun ward(): List<Type> = listOf(
        Type("M", "Mattina", LocalTime.of(7, 0), LocalTime.of(14, 0)),
        Type("P", "Pomeriggio", LocalTime.of(14, 0), LocalTime.of(21, 0)),
        Type("N", "Notte", LocalTime.of(21, 0), LocalTime.of(7, 0)),
    )

    /** Continuità assistenziale (come Bussola CA): notti 20–8 ogni giorno, festivi 8–20, prefestivi 10–20. */
    fun continuity(): List<Type> = listOf(
        Type("F", "Festivo", LocalTime.of(8, 0), LocalTime.of(20, 0), days = setOf(7)),
        Type("PF", "Prefestivo", LocalTime.of(10, 0), LocalTime.of(20, 0), days = setOf(6)),
        Type("N", "Notte", LocalTime.of(20, 0), LocalTime.of(8, 0)),
    )
}

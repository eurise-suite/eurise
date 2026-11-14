package eu.eurise.feature.assistant

import android.Manifest
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import eu.eurise.core.contacts.Contact
import eu.eurise.core.contacts.Contacts
import eu.eurise.core.contacts.Reach
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.calendar.DeviceCalendar
import eu.eurise.core.data.calendar.EventDraft
import eu.eurise.core.data.model.QuickAdd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Una riga della scheda di conferma: [choices] non vuoto = si sceglie toccando, altrimenti si scrive. */
data class Field(val key: String, val label: Int, val value: String, val choices: List<String> = emptyList())

/** Una pillola sotto la scheda («Usa SMS», «Oppure Marco Rossi»): cambia la scheda, non agisce. */
data class Chip(val label: String, val command: Command)

enum class Kind { Event, Message, Note, Task, Call, Route, Report, Alarm, Timer, Search, Open, Free }

/**
 * La scheda di conferma (tavole AstConferma, AstMessaggio, AstLibera): cosa ha capito Alfred, righe da correggere,
 * e il pulsante che agisce. Niente succede prima del tocco su [action].
 */
data class Card(
    val kind: Kind,
    val command: Command,
    val lead: String,
    val title: Int,
    val fields: List<Field>,
    val note: String?,
    val action: String,
    val chips: List<Chip> = emptyList(),
    /** Falso quando manca qualcosa (persona non trovata): il pulsante resta spento finché non si corregge. */
    val ready: Boolean = true,
    internal val contact: Contact? = null,
    internal val calendars: List<DeviceCalendar> = emptyList(),
    internal val app: Intent? = null,
) {
    fun value(key: String) = fields.firstOrNull { it.key == key }?.value.orEmpty()
    fun with(key: String, value: String) = copy(fields = fields.map { if (it.key == key) it.copy(value = value) else it })
}

/** Com'è andata: la frase di Alfred e se il pannello si chiude da sé. */
data class Outcome(val line: String, val close: Boolean)

private val DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ITALIAN)
private val HOUR = DateTimeFormatter.ofPattern("HH:mm")

/** Prepara le schede e, dopo il tocco, agisce attraverso i dati e le app della suite. */
class Butler(private val context: Context) {
    private val data by lazy { EuriseData.create(context) }
    private fun s(id: Int, vararg args: Any) = context.getString(id, *args)

    suspend fun plan(command: Command): Card = when (command) {
        is Command.Event -> event(command)
        is Command.Message -> message(command)
        is Command.Call -> call(command)
        is Command.Note -> Card(Kind.Note, command, s(R.string.alfred_lead_note), R.string.alfred_card_note,
            listOf(Field("text", R.string.alfred_f_text, command.text)), s(R.string.alfred_note_grammar), s(R.string.alfred_do_note))
        is Command.Task -> Card(Kind.Task, command, s(R.string.alfred_lead_task), R.string.alfred_card_task,
            listOf(Field("what", R.string.alfred_f_what, command.title), Field("when", R.string.alfred_f_when, whenText(command.due, command.time, allDay = command.time == null, empty = ""))),
            s(R.string.alfred_note_grammar), s(R.string.alfred_do_task))
        is Command.Navigate -> Card(Kind.Route, command, s(R.string.alfred_lead_route), R.string.alfred_card_route,
            listOf(Field("place", R.string.alfred_f_destination, command.place)), s(R.string.alfred_note_route), s(R.string.alfred_do_route))
        is Command.Report -> Card(Kind.Report, command, s(R.string.alfred_lead_report), R.string.alfred_card_report,
            listOf(Field("what", R.string.alfred_f_what, s(reportLabel(command.kind)))), s(R.string.alfred_note_report),
            s(R.string.alfred_do_report))
        is Command.Alarm -> Card(Kind.Alarm, command, s(R.string.alfred_lead_alarm), R.string.alfred_card_alarm,
            listOf(Field("time", R.string.alfred_f_time, command.time.format(HOUR))),
            if (command.day != null && command.day != nextOccurrence(command.time)) s(R.string.alfred_note_alarm_day) else s(R.string.alfred_note_grammar),
            s(R.string.alfred_do_alarm))
        is Command.Timer -> Card(Kind.Timer, command, s(R.string.alfred_lead_timer), R.string.alfred_card_timer,
            listOf(Field("minutes", R.string.alfred_f_length, command.minutes.toString())), null, s(R.string.alfred_do_timer))
        is Command.Search -> Card(Kind.Search, command, s(R.string.alfred_lead_search), R.string.alfred_card_search,
            listOf(Field("query", R.string.alfred_f_query, command.query),
                Field("scope", R.string.alfred_f_in, s(if (command.scope == Scope.Web) R.string.alfred_web else R.string.alfred_suite),
                    listOf(s(R.string.alfred_suite), s(R.string.alfred_web)))),
            null, s(R.string.alfred_do_search))
        is Command.Open -> open(command)
        is Command.Free -> free(command)
    }

    // --- Appuntamento ---

    private suspend fun event(c: Command.Event): Card {
        val contact = c.who?.let { findPeople(it).firstOrNull() }
        val calendars = writableCalendars()
        val where = contact?.addresses?.firstOrNull()?.value?.let { s(R.string.alfred_from_card, it.replace('\n', ' '), contact.name.split(' ').first()) }.orEmpty()
        val fields = buildList {
            add(Field("what", R.string.alfred_f_what, c.title))
            if (c.who != null) add(Field("with", R.string.alfred_f_with, contact?.name ?: c.who))
            add(Field("when", R.string.alfred_f_when, whenText(c.day, c.time, allDay = c.time == null, empty = s(R.string.alfred_today))))
            add(Field("where", R.string.alfred_f_where, where))
            if (calendars.isNotEmpty()) add(Field("calendar", R.string.alfred_f_calendar, calendars.first().name, calendars.map { it.name }.distinct()))
        }
        return Card(Kind.Event, c, s(R.string.alfred_lead_event), R.string.alfred_card_event, fields,
            s(R.string.alfred_note_grammar), s(R.string.alfred_do_event), contact = contact, calendars = calendars)
    }

    private suspend fun writableCalendars(): List<DeviceCalendar> {
        if (context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val all = runCatching { data.calendar.observeCalendars().first() }.getOrDefault(emptyList()).filter { it.writable }
        // Prima quelli sincronizzati (si vedono anche altrove), poi quelli solo sul telefono.
        return all.sortedBy { it.local }
    }

    // --- Messaggio ---

    private suspend fun message(c: Command.Message): Card {
        val people = findPeople(c.who)
        val contact = people.firstOrNull()
        val channel = c.channel ?: Channel.Sms
        val available = Channel.entries.filter { it == Channel.Sms || channelPackage(it) != null }
        val number = contact?.phones?.firstOrNull()?.value
        val to = when {
            contact != null -> contact.name + (number?.let { " · " + Contacts.format(it) } ?: "")
            Reach.digits(c.who).length >= 6 -> c.who
            else -> c.who
        }
        val ready = number != null || Reach.digits(c.who).length >= 6
        val note = when (channel) {
            Channel.WhatsApp -> s(R.string.alfred_note_whatsapp)
            Channel.Sms -> s(R.string.alfred_note_sms)
            else -> s(R.string.alfred_note_other_app)
        }
        val chips = buildList {
            available.filter { it != channel }.forEach { add(Chip(s(R.string.alfred_use, channelName(it)), c.copy(channel = it))) }
            people.drop(1).take(2).forEach { add(Chip(s(R.string.alfred_or, it.name), c.copy(who = it.name))) }
        }
        return Card(
            Kind.Message, c,
            if (ready) s(R.string.alfred_lead_recap) else s(R.string.alfred_lead_unknown_person, c.who),
            R.string.alfred_card_message,
            listOf(
                Field("to", R.string.alfred_f_to, to),
                Field("via", R.string.alfred_f_via, channelName(channel), available.map { channelName(it) }),
                Field("text", R.string.alfred_f_text, c.text),
            ),
            note,
            if (channel == Channel.Sms) s(R.string.alfred_do_send_sms) else s(R.string.alfred_do_send_app, channelName(channel)),
            chips, ready, contact = contact,
        )
    }

    fun channelName(c: Channel) = when (c) {
        Channel.WhatsApp -> "WhatsApp"
        Channel.Sms -> "SMS"
        Channel.Telegram -> "Telegram"
        Channel.Signal -> "Signal"
    }

    private fun channelPackage(c: Channel): String? = when (c) {
        Channel.WhatsApp -> "com.whatsapp".takeIf { Reach.installed(context, it) }
        Channel.Telegram -> listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram").firstOrNull { Reach.installed(context, it) }
        Channel.Signal -> listOf("org.thoughtcrime.securesms", "im.molly.app").firstOrNull { Reach.installed(context, it) }
        Channel.Sms -> null
    }

    // --- Chiamata ---

    private suspend fun call(c: Command.Call): Card {
        val people = findPeople(c.who)
        val contact = people.firstOrNull()
        val number = contact?.phones?.firstOrNull()?.value ?: c.who.takeIf { Reach.digits(it).length >= 3 }
        val canCall = context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        return Card(
            Kind.Call, c,
            if (number != null) s(R.string.alfred_lead_call, contact?.name?.let { " $it" } ?: "") else s(R.string.alfred_lead_unknown_person, c.who),
            R.string.alfred_card_call,
            listOf(Field("to", R.string.alfred_f_to, contact?.name ?: c.who), Field("number", R.string.alfred_f_number, number?.let { Contacts.format(it) }.orEmpty(),
                contact?.phones?.map { Contacts.format(it.value) }?.distinct()?.takeIf { it.size > 1 }.orEmpty())),
            if (canCall) null else s(R.string.alfred_note_call),
            s(R.string.alfred_do_call),
            people.drop(1).take(3).map { Chip(s(R.string.alfred_or, it.name), c.copy(who = it.name)) },
            number != null, contact = contact,
        )
    }

    // --- Apri ---

    private fun open(c: Command.Open): Card {
        val intent = findApp(c.app)
        return Card(Kind.Open, c, if (intent != null) s(R.string.alfred_lead_open) else s(R.string.alfred_lead_no_app, c.app),
            R.string.alfred_card_open, listOf(Field("app", R.string.alfred_f_app, intent?.getStringExtra(LABEL) ?: c.app)), null,
            s(R.string.alfred_do_open), ready = intent != null, app = intent)
    }

    /** Le sezioni della suite che vivono nell'app Eurise (stessi nomi del cassetto delle app). */
    private val sections = mapOf(
        "oggi" to "today", "calendario" to "calendar", "orologio" to "clock", "file" to "drive", "scanner" to "scanner",
        "calcolatrice" to "calculator", "meteo" to "weather", "lettore qr" to "qr", "qr" to "qr", "privacy" to "privacy",
        "note" to "notes", "attività" to "tasks", "cose da fare" to "tasks",
    )

    private fun findApp(name: String): Intent? {
        val wanted = fold(name)
        sections.entries.firstOrNull { fold(it.key) == wanted }?.let { (label, section) ->
            return Intent().setClassName(context.packageName, "eu.eurise.hub.MainActivity")
                .putExtra("eu.eurise.extra.SECTION", section).putExtra(LABEL, label.replaceFirstChar { it.uppercase() })
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pm = context.packageManager
        val apps = runCatching { pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0) }.getOrDefault(emptyList())
        val labelled = apps.map { it to it.loadLabel(pm).toString() }
        val hit = labelled.firstOrNull { fold(it.second) == wanted }
            ?: labelled.firstOrNull { fold(it.second).startsWith(wanted) || fold(it.second).removePrefix("eurise ") == wanted }
            ?: labelled.firstOrNull { wanted.length >= 4 && fold(it.second).contains(wanted) }
            ?: return null
        return Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(hit.first.activityInfo.packageName, hit.first.activityInfo.name)
            .putExtra(LABEL, hit.second)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    // --- Richiesta libera ---


    private fun free(c: Command.Free): Card =
        Card(Kind.Free, c, s(R.string.alfred_lead_free), R.string.alfred_card_free,
            listOf(Field("text", R.string.alfred_f_text, c.text)),
            s(R.string.alfred_note_free), s(R.string.alfred_search_instead),
            listOf(Chip(s(R.string.alfred_search_instead), Command.Search(c.text, Scope.Web))))

    // --- Persone ---

    /** I contatti il cui nome contiene tutte le parole dette, i preferiti per primi; con numeri e indirizzi. */
    suspend fun findPeople(spoken: String): List<Contact> {
        if (!Contacts.canRead(context)) return emptyList()
        val words = fold(spoken).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty() || Reach.digits(spoken).length >= 6) return emptyList()
        val all = Contacts.all(context)
        val hits = all.filter { c -> val n = fold(c.name); words.all { w -> n.split(' ').any { it.startsWith(w) } } }
            .sortedWith(compareBy<Contact>({ !it.starred }, { fold(it.name).split(' ').first() != words.first() }, { it.name.length }))
            .take(4)
        return hits.mapNotNull { Contacts.detail(context, it.id) }.filter { it.phones.isNotEmpty() || it.addresses.isNotEmpty() || it.emails.isNotEmpty() }
            .ifEmpty { hits }
    }

    // --- Azione, solo dopo il tocco ---

    /** Dopo un'azione riuscita, una volta su tre, una massima o un detto adatto (mai nelle schede incomplete). */
    private fun withMaxim(card: Card, out: Outcome): Outcome {
        if (!out.close || kotlin.random.Random.nextInt(3) != 0) return out
        val array = when (card.kind) {
            Kind.Event -> R.array.alfred_maxims_event
            Kind.Note -> R.array.alfred_maxims_note
            Kind.Task -> R.array.alfred_maxims_task
            Kind.Message -> R.array.alfred_maxims_message
            Kind.Route -> R.array.alfred_maxims_route
            Kind.Alarm -> R.array.alfred_maxims_alarm
            else -> return out
        }
        return out.copy(line = out.line + " " + context.resources.getStringArray(array).random())
    }

    suspend fun run(card: Card): Outcome = withMaxim(card, act(card))

    private suspend fun act(card: Card): Outcome = runCatching {
        when (card.kind) {
            Kind.Event -> saveEvent(card)
            Kind.Note -> {
                val text = card.value("text")
                withContext(Dispatchers.IO) { data.notes.save(null, text.lineSequence().first().take(60), text) }
                Outcome(s(R.string.alfred_done_note), true)
            }
            Kind.Task -> {
                val q = QuickAdd.parse(card.value("when"), LocalDateTime.now())
                val c = card.command as Command.Task
                val due = if (card.value("when").isBlank()) null else q.due ?: c.due
                withContext(Dispatchers.IO) { data.tasks.add(card.value("what"), due = due, time = q.time ?: c.time.takeIf { due != null }) }
                Outcome(s(R.string.alfred_done_task), true)
            }
            Kind.Message -> sendMessage(card)
            Kind.Call -> {
                val number = card.value("number")
                val canCall = context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                start(if (canCall) Reach.call(number) else Reach.dial(number))
            }
            Kind.Route -> {
                val place = card.value("place")
                val uri = Uri.parse("geo:0,0?q=" + Uri.encode(place))
                val maps = listOf("eu.eurise.maps").firstOrNull { Reach.installed(context, it) }
                start(Intent(Intent.ACTION_VIEW, uri).apply { maps?.let { setPackage(it) } })
            }
            Kind.Report -> {
                // Senza conferma, come il tocco sulla griglia: Mappe segna dove si è e lascia «Annulla» per 5 secondi.
                val kind = (card.command as Command.Report).kind
                if (!Reach.installed(context, MAPS)) return Outcome(s(R.string.alfred_no_maps), false)
                start(Intent().setClassName(MAPS, "app.organicmaps.eurise.EuriseGuideReportActivity")
                    .putExtra("eu.eurise.maps.REPORT_KIND", kind.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    .let { if (it.close) Outcome(s(R.string.alfred_done_report, s(reportLabel(kind))), true) else it }
            }
            Kind.Alarm -> {
                val t = LocalTime.parse(card.value("time").replace('.', ':').padStart(5, '0'))
                start(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, t.hour).putExtra(AlarmClock.EXTRA_MINUTES, t.minute)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true).setPackage(context.packageName), fallback = Intent(AlarmClock.ACTION_SET_ALARM)
                    .putExtra(AlarmClock.EXTRA_HOUR, t.hour).putExtra(AlarmClock.EXTRA_MINUTES, t.minute))
            }
            Kind.Timer -> {
                val minutes = card.value("minutes").filter { it.isDigit() }.toIntOrNull() ?: 1
                start(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                    .setPackage(context.packageName), fallback = Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, minutes * 60))
            }
            Kind.Search -> search(card.value("query"), card.value("scope") == s(R.string.alfred_web))
            Kind.Open -> start(card.app ?: return Outcome(s(R.string.alfred_failed), false))
            Kind.Free -> search(card.value("text"), web = true)
        }
    }.getOrElse { Outcome(s(R.string.alfred_failed), false) }

    private suspend fun saveEvent(card: Card): Outcome {
        if (context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return Outcome(s(R.string.alfred_need_calendar), false)
        }
        val c = card.command as Command.Event
        val now = LocalDateTime.now()
        val edited = QuickAdd.parse(card.value("when"), now)
        val day = edited.due ?: c.day ?: now.toLocalDate()
        val time = edited.time ?: c.time
        var calendar = card.calendars.firstOrNull { it.name == card.value("calendar") } ?: card.calendars.firstOrNull()
        if (calendar == null) {
            val id = data.calendar.createLocalCalendar("Eurise", 0xFF3E5C8A.toInt()) ?: return Outcome(s(R.string.alfred_no_calendar), false)
            calendar = DeviceCalendar(id, "Eurise", "", 0, writable = true, local = true)
        }
        val start = if (time != null) day.atTime(time) else day.atStartOfDay()
        val end = if (time != null) start.plusHours(1) else day.plusDays(1).atStartOfDay()
        val with = card.value("with").takeIf { it.isNotBlank() }
        val draft = EventDraft(
            eventId = null, calendarId = calendar.id, title = card.value("what"),
            location = card.value("where").substringBefore(" (dalla scheda").trim(),
            description = with?.let { "Con $it" }.orEmpty(), allDay = time == null, start = start, end = end,
        )
        val id = data.calendar.save(draft)
        return if (id != null) Outcome(s(R.string.alfred_done_event), true) else Outcome(s(R.string.alfred_failed), false)
    }

    private suspend fun sendMessage(card: Card): Outcome {
        val c = card.command as Command.Message
        val written = card.value("to").substringAfter(" · ", "")
        val number = card.contact?.phones?.firstOrNull()?.value ?: written.ifBlank { c.who }.takeIf { Reach.digits(it).length >= 6 }
            ?: return Outcome(s(R.string.alfred_lead_unknown_person, c.who), false)
        val text = card.value("text")
        val channel = Channel.entries.firstOrNull { channelName(it) == card.value("via") } ?: Channel.Sms
        val intent = when (channel) {
            Channel.Sms -> Reach.sms(number, text)
            Channel.WhatsApp -> Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=" + Reach.international(number) + "&text=" + Uri.encode(text)))
                .setPackage(channelPackage(channel))
            Channel.Telegram -> { clip(text); Reach.telegram(context, number) }
            Channel.Signal -> { clip(text); Reach.signal(context, number) }
        } ?: return Outcome(s(R.string.alfred_failed), false)
        return start(intent)
    }

    private fun search(query: String, web: Boolean): Outcome {
        if (!web) {
            return start(Intent().setClassName(context.packageName, "eu.eurise.hub.LauncherActivity")
                .putExtra("eu.eurise.extra.SEARCH", query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return start(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query),
            fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.qwant.com/?q=" + Uri.encode(query))))
    }

    private fun start(intent: Intent, fallback: Intent? = null): Outcome {
        val ok = Reach.open(context, intent) || (fallback != null && Reach.open(context, fallback))
        return if (ok) Outcome(s(R.string.alfred_done), true) else Outcome(s(R.string.alfred_failed), false)
    }

    private fun clip(text: String) {
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Alfred", text))
    }

    // --- Testi ---

    private fun whenText(day: LocalDate?, time: LocalTime?, allDay: Boolean, empty: String): String {
        val today = LocalDate.now()
        val d = when (day) {
            null -> if (time == null) return empty else s(R.string.alfred_today)
            today -> s(R.string.alfred_today)
            today.plusDays(1) -> s(R.string.alfred_tomorrow)
            else -> day.format(DAY)
        }.replaceFirstChar { it.uppercase() }
        return when {
            time != null -> "$d · ${time.format(HOUR)}–${time.plusHours(1).format(HOUR)}"
            allDay -> "$d · ${s(R.string.alfred_no_time)}"
            else -> d
        }
    }

    private fun nextOccurrence(t: LocalTime): LocalDate =
        if (t.isAfter(LocalTime.now())) LocalDate.now() else LocalDate.now().plusDays(1)

    private fun reportLabel(k: ReportKind) = when (k) {
        ReportKind.CAMERA -> R.string.alfred_report_camera
        ReportKind.LIMIT -> R.string.alfred_report_limit
        ReportKind.CLOSED -> R.string.alfred_report_closed
        ReportKind.WORKS -> R.string.alfred_report_works
        ReportKind.QUEUE -> R.string.alfred_report_queue
        ReportKind.DANGER -> R.string.alfred_report_danger
    }

    companion object {
        private const val LABEL = "eu.eurise.alfred.LABEL"
        private const val MAPS = "eu.eurise.maps"

        /** Minuscole, senza accenti né punteggiatura: «Città» e «citta» sono la stessa parola. */
        fun fold(s: String): String =
            Normalizer.normalize(s.lowercase(Locale.ITALIAN), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
                .replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()
    }
}

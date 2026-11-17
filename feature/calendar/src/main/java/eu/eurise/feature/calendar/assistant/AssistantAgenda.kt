package eu.eurise.feature.calendar.assistant

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import eu.eurise.core.cloud.CloudKind
import eu.eurise.core.cloud.CloudSync
import eu.eurise.core.cloud.DavCalendarObject
import eu.eurise.core.cloud.Proposals
import eu.eurise.core.cloud.SyncJob
import eu.eurise.core.cloud.TaskSharing
import eu.eurise.core.data.calendar.CalendarEvent
import eu.eurise.core.data.calendar.CalendarRepository
import eu.eurise.core.data.calendar.DeviceCalendar
import eu.eurise.core.data.calendar.EventDraft
import eu.eurise.core.data.calendar.Ics
import eu.eurise.core.designsystem.component.DataOut
import eu.eurise.core.designsystem.component.Jurisdiction
import eu.eurise.core.designsystem.component.privacyScore
import eu.eurise.feature.calendar.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Una proposta dell'assistente: l'oggetto sul server, l'appuntamento letto e chi l'ha proposta. */
data class Proposal(val item: DavCalendarObject, val event: Ics.Event, val by: String) {
    val key: String get() = item.path
    val cancelled: Boolean get() = event.status == "CANCELLED"

    /** La prima riga della descrizione, il perché della proposta («hanno chiesto giovedì mattina»). */
    val reason: String get() = event.description.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
}

/**
 * Agenda con l'assistente nel Calendario (tavole CollAssistente e InsAssistente): legge le proposte dal calendario
 * «Proposte» del cloud ([Proposals]), le tiene in un flusso per lo schermo e per le viste del Calendario (in grigio
 * finché non si conferma), le conferma nel calendario scelto o le rifiuta.
 */
object AssistantAgenda {
    private const val CHANNEL = "eurise_assistant_agenda"

    /** Grigio delle proposte nelle viste del Calendario (lo stesso della tavola). */
    const val PROPOSAL_COLOR = 0xFFBDBDB5.toInt()

    private val _proposals = MutableStateFlow<List<Proposal>>(emptyList())

    /** Le proposte da confermare (non annullate, non passate), in ordine di tempo. */
    val proposals: StateFlow<List<Proposal>> = _proposals.asStateFlow()

    private var loaded = false

    /** Carica le ultime proposte lette (senza rete), una volta sola. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        _proposals.value = pending(parse(context, Proposals.cached(context)))
    }

    fun privacy(kind: CloudKind?): Pair<Int, Jurisdiction> = when (kind) {
        CloudKind.Murena -> privacyScore(DataOut.Personal, Jurisdiction.EU) to Jurisdiction.EU
        else -> privacyScore(DataOut.Personal, Jurisdiction.Own) to Jurisdiction.Own
    }

    private fun parse(context: Context, items: List<DavCalendarObject>): List<Proposal> {
        val assistant = Proposals.assistant(context)?.second
        val me = TaskSharing.me(context)
        return items.mapNotNull { item ->
            val event = Ics.parse(item.data).firstOrNull() ?: return@mapNotNull null
            val org = event.organizer.takeIf { o -> o.isNotBlank() && me?.let { o == it.first || o.equals(it.second, ignoreCase = true) } != true }
            Proposal(item, event, org ?: assistant ?: context.getString(R.string.asst_someone))
        }
    }

    private fun pending(all: List<Proposal>, now: LocalDateTime = LocalDateTime.now()): List<Proposal> =
        all.filter { !it.cancelled && it.event.end.isAfter(now) }.sortedBy { it.event.start }

    /**
     * Rilegge le proposte dal server e restituisce quelle da confermare. Toglie dal server le proposte passate da più
     * di un giorno (confermate altrove o dimenticate) e quelle annullate già passate.
     */
    suspend fun refresh(context: Context): List<Proposal> {
        loaded = true
        val all = parse(context, Proposals.fetch(context))
        val stale = LocalDateTime.now().minusDays(1)
        all.filter { it.event.end.isBefore(stale) || (it.cancelled && it.event.end.isBefore(LocalDateTime.now())) }
            .forEach { runCatching { Proposals.remove(context, it.item) } }
        return pending(all).also { _proposals.value = it }
    }

    /**
     * Conferma: l'appuntamento nasce nel calendario [calendarId] del telefono (alle ore proposte, o a [start]–[end] se
     * scelte con «Altra ora») e la proposta lascia il server. Vero se l'appuntamento è stato creato.
     */
    suspend fun confirm(
        context: Context,
        calendar: CalendarRepository,
        p: Proposal,
        calendarId: Long,
        start: LocalDateTime = p.event.start,
        end: LocalDateTime = p.event.end,
    ): Boolean {
        val id = calendar.save(
            EventDraft(
                eventId = null,
                calendarId = calendarId,
                title = p.event.title,
                location = p.event.location,
                description = p.event.description,
                allDay = p.event.allDay,
                start = start,
                end = if (end.isAfter(start)) end else start.plusHours(1),
            )
        ) ?: return false
        Proposals.setTarget(context, calendarId)
        runCatching { Proposals.remove(context, p.item) }
        _proposals.value = _proposals.value.filterNot { it.key == p.key }
        CloudSync.get(context).requestSoon()
        return id > 0
    }

    /** Rifiuta: la proposta resta sul server annullata (l'assistente la vede barrata) e sparisce da qui. */
    suspend fun decline(context: Context, p: Proposal) {
        Proposals.replace(context, p.item, Ics.withStatus(p.item.data, "CANCELLED"))
        _proposals.value = _proposals.value.filterNot { it.key == p.key }
    }

    /**
     * Le proposte come appuntamenti grigi per le viste del Calendario (id negativi: un tocco apre l'agenda condivisa).
     * Solo se il calendario «Proposte» non è già sul telefono (sincronizzato da DAVx⁵), per non mostrarle due volte.
     */
    fun asEvents(list: List<Proposal>, deviceHasProposte: Boolean): List<CalendarEvent> {
        if (deviceHasProposte) return emptyList()
        val zone = ZoneId.systemDefault()
        return list.mapIndexed { i, p ->
            val e = p.event
            CalendarEvent(
                eventId = -1L - i,
                calendarId = -1L,
                title = e.title,
                location = e.location,
                description = e.description,
                allDay = e.allDay,
                start = e.start,
                end = e.end,
                color = PROPOSAL_COLOR,
                recurring = false,
                calendarName = Proposals.NAME,
                writable = false,
                beginMillis = if (e.allDay) e.start.toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                else e.start.atZone(zone).toInstant().toEpochMilli(),
            )
        }
    }

    /** Vero per il calendario delle proposte sul telefono («Proposte», o «Proposte (Mario)» per chi propone). */
    fun isProposalCalendar(name: String): Boolean = name == Proposals.NAME || name.startsWith(Proposals.NAME + " (")

    /**
     * Il calendario dove vanno le confermate: quello scelto l'ultima volta, altrimenti quello con lo stesso nome del primo
     * calendario che l'assistente vede, altrimenti il primo che si può modificare (mai le proposte).
     */
    fun defaultTarget(context: Context, calendars: List<DeviceCalendar>, seenNames: List<String>): DeviceCalendar? {
        val usable = calendars.filter { it.writable && !isProposalCalendar(it.name) }
        Proposals.target(context)?.let { id -> usable.firstOrNull { it.id == id }?.let { return it } }
        seenNames.forEach { n -> usable.firstOrNull { it.name.equals(n, ignoreCase = true) }?.let { return it } }
        return usable.firstOrNull { !it.local } ?: usable.firstOrNull()
    }

    private val dayTime = DateTimeFormatter.ofPattern("EEE d · HH:mm", Locale.ITALIAN)

    /** Avvisa le proposte arrivate dall'ultima volta; tocca e si apre l'agenda condivisa. */
    fun notifyNew(context: Context, list: List<Proposal>) {
        val known = Proposals.notified(context)
        val fresh = list.filter { it.key !in known }
        Proposals.setNotified(context, list.map { it.key }.toSet())
        if (fresh.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.asst_channel), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = Intent().setClassName(context, "eu.eurise.hub.MainActivity")
            .putExtra("eu.eurise.extra.SECTION", "proposals")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        fresh.take(3).forEach { p ->
            val id = p.key.hashCode()
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(eu.eurise.core.designsystem.R.drawable.ic_stat_eurise)
                .setContentTitle(context.getString(R.string.asst_note_title, p.by.substringBefore(' ')))
                .setContentText(p.event.title + " · " + p.event.start.format(dayTime))
                .setContentIntent(PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                .setAutoCancel(true)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
        }
    }
}

/** Nella sincronizzazione del cloud (ogni 30 minuti e dopo una modifica): rilegge le proposte e avvisa le nuove. */
class AssistantSyncJob : SyncJob {
    override suspend fun run(context: Context, cloud: CloudSync): List<String> {
        if (!Proposals.active(context)) return emptyList()
        val before = Proposals.notified(context)
        val list = AssistantAgenda.refresh(context)
        AssistantAgenda.notifyNew(context, list)
        val fresh = list.count { it.key !in before }
        return if (fresh > 0) listOf(context.resources.getQuantityString(R.plurals.asst_log_new, fresh, fresh)) else emptyList()
    }
}

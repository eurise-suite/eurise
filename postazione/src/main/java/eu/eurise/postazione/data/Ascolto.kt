package eu.eurise.postazione.data

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow

/** Un messaggio, una mail o una chiamata persa ancora tra le notifiche: [apri] è ciò che farebbe il tocco sulla notifica. */
class Avviso(
    val chiave: String, val pacchetto: String, val titolo: String, val testo: String, val quando: Long,
    val apri: PendingIntent?,
    /** L'azione «Rispondi» della notifica, se l'app la offre: serve a «Rispondo domattina». */
    val rispondi: Notification.Action? = null,
    /** Una chiamata persa («Ti hanno cercato»); altrimenti un messaggio o una mail («Da rispondere»). */
    val chiamata: Boolean = false,
    /** L'azione «Richiama» della notifica, se l'app del telefono la offre. */
    val richiama: Notification.Action? = null,
    /** Si può togliere dalle notifiche («Vista»). */
    val cancellabile: Boolean = true,
    /** Dalle app della suite e non da una notifica: il numero da richiamare (Telefono)… */
    val numero: String = "",
    /** …o l'email da aprire in Eurise Email («casella:uid») con l'indirizzo di chi scrive. */
    val rifMail: String = "",
    val indirizzo: String = "",
    /** Quante (chiamate perse, email da leggere) dietro questa riga. */
    val quante: Int = 1,
    /** I messaggi arrivati nella notifica, dal più vecchio: chi scrive, testo, ed eventuale foto o audio. */
    val storia: List<Messaggio> = emptyList(),
    /** La foto della notifica (stile «immagine grande»), se l'app la mette. */
    val foto: android.graphics.Bitmap? = null,
    /** «Segna come letto» e «Archivia» della notifica, se l'app li offre. */
    val letto: Notification.Action? = null,
    val archivia: Notification.Action? = null,
    /** Il nome dell'app che ha mandato la notifica («WhatsApp», «Email»). */
    val app: String = "",
    /**
     * Il simbolo piccolo della notifica (quello della barra di stato): è già monocromatico e dice l'app senza scriverla.
     * Resta in memoria come il resto della notifica.
     */
    val simbolo: android.graphics.drawable.Icon? = null,
) {
    val dallaSuite: Boolean get() = numero.isNotEmpty() || rifMail.isNotEmpty()
}

/** Un messaggio di una conversazione: [uri] e [tipo] quando l'app allega una foto o un audio alla notifica. */
class Messaggio(val chi: String?, val testo: String, val uri: android.net.Uri? = null, val tipo: String = "") {
    val foto: Boolean get() = uri != null && tipo.startsWith("image/")
    val audio: Boolean get() = uri != null && tipo.startsWith("audio/")
}

/** Quello che sta suonando (o è in pausa) in un'app di musica o video. */
data class Brano(val titolo: String, val artista: String, val pacchetto: String, val suona: Boolean)

/**
 * «Da rispondere» e la musica che suona: servono l'accesso alle notifiche di Android, **spento di base**. Si accende
 * dalla Postazione dopo la spiegazione, si spegne da lì o dalle impostazioni di Android. Di ogni notifica si guardano
 * solo messaggi, posta e chiamate perse, e si tengono in memoria titolo, testo e ora finché la notifica c'è: niente
 * viene scritto su disco e la Postazione non ha il permesso di rete.
 */
class AscoltoService : NotificationListenerService() {
    override fun onListenerConnected() {
        istanza = this
        val prefs = getSharedPreferences("postazione", MODE_PRIVATE)
        fino = prefs.getLong(K_FINO, 0L)
        inPausa = prefs.getString(K_APP, null).orEmpty().lines().filter { it.isNotBlank() }.toSet()
        aggiorna()
    }

    override fun onListenerDisconnected() {
        istanza = null
        avvisi.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = aggiorna()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = aggiorna()

    internal fun aggiorna() {
        // Spento dalla Postazione o dal centro privacy: nulla in memoria, anche se Android tiene ancora l'accesso.
        if (!Uso.Notifiche.acceso(this)) { avvisi.value = emptyList(); return }
        val attive = runCatching { activeNotifications }.getOrNull() ?: return
        val ora = System.currentTimeMillis()
        val pausa = ora < fino
        // «Lavoro in pausa»: le notifiche delle app scelte si rimandano da sole alla fine della pausa (mai chiamate e sveglie).
        if (pausa) attive.forEach { sbn ->
            val categoria = sbn.notification?.category
            if (sbn.packageName in inPausa && !sbn.isOngoing && categoria != Notification.CATEGORY_CALL && categoria != Notification.CATEGORY_ALARM) {
                runCatching { snoozeNotification(sbn.key, fino - ora) }.onSuccess { fermate.value = fermate.value + sbn.key }
            }
        }
        avvisi.value = attive.filter { !(pausa && it.packageName in inPausa) }.mapNotNull { leggi(it) }
            .filter { it.chiave !in nascosti.value }.sortedByDescending { it.quando }.take(MAX)
    }

    /**
     * Messaggi, posta e chiamate perse. Molte app non dichiarano la categoria: valgono anche le notifiche in stile
     * conversazione, quelle con «Rispondi» e quelle dell'app del telefono e degli SMS scelte da Android.
     */
    private fun leggi(sbn: StatusBarNotification): Avviso? {
        val n = sbn.notification ?: return null
        if (sbn.isOngoing || n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (sbn.packageName == packageName) return null
        val categoria = n.category
        val stile = n.extras?.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val rispondi = n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
        val telefono = sbn.packageName == dialer
        val chiamata = categoria == CATEGORIA_PERSA || (telefono && categoria != Notification.CATEGORY_CALL && categoria != Notification.CATEGORY_STATUS) ||
            (categoria == Notification.CATEGORY_CALL && telefono)
        val messaggio = categoria == Notification.CATEGORY_MESSAGE || categoria == Notification.CATEGORY_EMAIL ||
            stile.endsWith("MessagingStyle") || stile.endsWith("InboxStyle") || rispondi != null || sbn.packageName == sms ||
            POSTA.any { sbn.packageName.startsWith(it) }
        if (!chiamata && !messaggio) return null
        val titolo = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val testo = n.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (titolo.isBlank() && testo.isBlank()) return null
        val richiama = if (chiamata) n.actions?.firstOrNull { a -> a.remoteInputs == null && a.actionIntent != null && RICHIAMA.containsMatchIn(a.title?.toString().orEmpty()) } else null
        val semplici = n.actions?.filter { it.remoteInputs == null && it.actionIntent != null }.orEmpty()
        val letto = semplici.firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ || LETTO.containsMatchIn(it.title?.toString().orEmpty()) }
        val archivia = semplici.firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_ARCHIVE || ARCHIVIA.containsMatchIn(it.title?.toString().orEmpty()) }
        val app = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault("")
        return Avviso(
            sbn.key, sbn.packageName, n.extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.takeIf { it.isNotBlank() } ?: titolo,
            testo, sbn.postTime, n.contentIntent, rispondi, chiamata, richiama, sbn.isClearable,
            storia = storia(n), foto = foto(n), letto = letto, archivia = archivia, app = app, simbolo = n.smallIcon,
        )
    }

    /** I messaggi di una notifica in stile conversazione (WhatsApp, Signal, Messaggi…), al più gli ultimi dieci. */
    @Suppress("DEPRECATION")
    private fun storia(n: Notification): List<Messaggio> {
        val lista = n.extras?.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return emptyList()
        // Ogni messaggio è un Bundle con «text», «sender» o «sender_person», e «uri»/«type» per foto e audio.
        return runCatching {
            lista.mapNotNull { p ->
                val b = p as? android.os.Bundle ?: return@mapNotNull null
                val t = b.getCharSequence("text")?.toString().orEmpty()
                val uri = b.getParcelable("uri") as? android.net.Uri
                if (t.isBlank() && uri == null) return@mapNotNull null
                val chi = (b.getParcelable("sender_person") as? android.app.Person)?.name?.toString() ?: b.getCharSequence("sender")?.toString()
                Messaggio(chi, t, uri, b.getString("type").orEmpty())
            }.takeLast(10)
        }.getOrDefault(emptyList())
    }

    @Suppress("DEPRECATION")
    private fun foto(n: Notification): android.graphics.Bitmap? =
        runCatching { n.extras?.getParcelable(Notification.EXTRA_PICTURE) as? android.graphics.Bitmap }.getOrNull()

    private val dialer: String? by lazy { runCatching { getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage }.getOrNull() }
    private val sms: String? by lazy { runCatching { android.provider.Telephony.Sms.getDefaultSmsPackage(this) }.getOrNull() }

    /** «Vista»: la notifica si toglie (se l'app lo permette), altrimenti sparisce solo dalla Postazione. */
    internal fun togli(chiave: String) { runCatching { cancelNotification(chiave) } }

    /** «Più tardi»: torna fra un'ora. */
    internal fun rimanda(chiave: String) { runCatching { snoozeNotification(chiave, 60 * 60_000L) } }

    companion object {
        private const val MAX = 12
        private const val CATEGORIA_PERSA = "missed_call"
        /** App di posta che non sempre dichiarano la categoria «email». */
        private val POSTA = listOf(
            "eu.eurise.mail", "com.google.android.gm", "eu.faircode.email", "com.fsck.k9", "net.thunderbird", "ch.protonmail",
            "de.tutao.tutanota", "com.microsoft.office.outlook", "foundation.e.mail", "com.samsung.android.email",
        )
        private val LETTO = Regex("(?i)letto|letta|read|gelesen|\\blu\\b")
        private val ARCHIVIA = Regex("(?i)archiv")
        private val RICHIAMA = Regex("(?i)richiam|call ?back|chiama|rappel|zurückrufen|devolver")

        internal const val K_FINO = "pausa.fino"
        internal const val K_APP = "pausa.app"

        /** Solo in memoria: si svuota quando l'accesso si spegne o l'app si chiude. */
        val avvisi = MutableStateFlow<List<Avviso>>(emptyList())

        /** Gli avvisi «visti» che l'app non lascia togliere: spariscono solo dalla Postazione, finché la notifica c'è. */
        val nascosti = MutableStateFlow<Set<String>>(emptySet())

        /** Le notifiche rimandate in questa pausa (le chiavi): per dire quante aspettano. */
        val fermate = MutableStateFlow<Set<String>>(emptySet())

        @Volatile internal var fino = 0L
        @Volatile internal var inPausa: Set<String> = emptySet()
        @Volatile internal var istanza: AscoltoService? = null
    }
}

object Ascolto {
    /** Accesso dato da Android e interruttore della Postazione acceso ([Uso.Notifiche]). */
    fun acceso(context: Context): Boolean = permesso(context) && Uso.Notifiche.acceso(context)

    fun permesso(context: Context): Boolean =
        runCatching { NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName) }.getOrDefault(false)

    /** La pagina di Android dove l'accesso si accende e si spegne. */
    fun impostazioni(context: Context) {
        val nome = ComponentName(context, AscoltoService::class.java).flattenToString()
        val preciso = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, nome).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(preciso) }.isFailure) {
            runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /**
     * «Lavoro in pausa»: fino a [fino] le notifiche delle app [pacchetti] si rimandano da sole (Android le ripresenta
     * a quell'ora). Con [fino] a zero la pausa finisce: le nuove arrivano subito, quelle già rimandate tornano alla loro ora.
     */
    fun pausa(context: Context, pacchetti: Set<String>, fino: Long) {
        context.getSharedPreferences("postazione", Context.MODE_PRIVATE).edit()
            .putLong(AscoltoService.K_FINO, fino).putString(AscoltoService.K_APP, pacchetti.joinToString("\n")).apply()
        AscoltoService.fino = fino
        AscoltoService.inPausa = pacchetti
        AscoltoService.fermate.value = emptySet()
        AscoltoService.istanza?.aggiorna()
    }

    fun pausaFino(context: Context): Long =
        context.getSharedPreferences("postazione", Context.MODE_PRIVATE).getLong(AscoltoService.K_FINO, 0L)

    /** Una risposta scritta da te con un tocco, mandata con l'azione «Rispondi» della notifica: mai da sola. */
    fun rispondi(context: Context, avviso: Avviso, testo: String): Boolean {
        val azione = avviso.rispondi ?: return false
        val campi = azione.remoteInputs ?: return false
        return runCatching {
            val intent = Intent()
            val dati = Bundle()
            campi.forEach { dati.putCharSequence(it.resultKey, testo) }
            RemoteInput.addResultsToIntent(campi, intent, dati)
            azione.actionIntent.send(context, 0, intent)
        }.isSuccess
    }

    /** «Richiama»: l'azione della notifica se c'è, altrimenti come toccarla (apre l'app del telefono). */
    fun richiama(context: Context, a: Avviso): Boolean =
        a.richiama?.let { azione -> runCatching { azione.actionIntent.send() }.isSuccess } ?: apri(context, a)

    fun vista(a: Avviso) {
        if (a.cancellabile) AscoltoService.istanza?.togli(a.chiave)
        AscoltoService.nascosti.value = AscoltoService.nascosti.value + a.chiave
        AscoltoService.avvisi.value = AscoltoService.avvisi.value.filter { it.chiave != a.chiave }
    }

    fun piuTardi(a: Avviso) {
        AscoltoService.istanza?.rimanda(a.chiave)
        AscoltoService.avvisi.value = AscoltoService.avvisi.value.filter { it.chiave != a.chiave }
    }

    /** Come toccare la notifica. */
    fun apri(context: Context, avviso: Avviso): Boolean {
        val pi = avviso.apri ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                val opzioni = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED).toBundle()
                pi.send(context, 0, null, null, null, null, opzioni)
            } else {
                pi.send()
            }
        }.isSuccess
    }
}

object Musica {
    enum class Comando { SuonaPausa, Avanti, Indietro }

    private fun controllo(context: Context): MediaController? {
        val gestore = context.getSystemService(MediaSessionManager::class.java) ?: return null
        val sessioni = runCatching { gestore.getActiveSessions(ComponentName(context, AscoltoService::class.java)) }.getOrNull().orEmpty()
        return sessioni.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessioni.firstOrNull { it.metadata != null }
    }

    fun brano(context: Context): Brano? {
        val c = controllo(context) ?: return null
        val m = c.metadata ?: return null
        val titolo = m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: return null
        val artista = m.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        return Brano(titolo, artista, c.packageName, c.playbackState?.state == PlaybackState.STATE_PLAYING)
    }

    fun comando(context: Context, comando: Comando) {
        val c = controllo(context) ?: return
        val t = c.transportControls
        when (comando) {
            Comando.SuonaPausa -> if (c.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
            Comando.Avanti -> t.skipToNext()
            Comando.Indietro -> t.skipToPrevious()
        }
    }
}

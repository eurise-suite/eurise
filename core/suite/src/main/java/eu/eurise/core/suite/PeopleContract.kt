package eu.eurise.core.suite

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * «Una persona, tutti i canali» (tavole UniPersone, UniConversazione, UniCanale): la Chat mette insieme Chat, SMS,
 * chiamate con la nota ed email di una persona. I dati restano dove sono (Messaggi, Telefono, Email) e passano alla Chat
 * solo sul telefono, dai fornitori `<pacchetto>.persone` che rispondono soltanto alle app firmate con la stessa chiave
 * (permesso della suite, controllato a ogni chiamata con [allowed]). Nessun server in mezzo, nessuna copia.
 */
object PeopleContract {
    /** Le app che danno i loro dati: Messaggi (SMS), Telefono (chiamate, note e Rubrica), Email (posta ricevuta). */
    enum class App(val base: String) { Messages("eu.eurise.messages"), Phone("eu.eurise.phone"), Mail("eu.eurise.mail") }

    /** Per ogni indirizzo l'ultima cosa e quante non lette (vista Persone). */
    const val METHOD_RECENT = "recent"
    /** Tutto quello che c'è con questi numeri o indirizzi email (il filo di una persona). */
    const val METHOD_ITEMS = "items"
    /** Chi è in Rubrica dietro numeri, email e utenti della Chat (Telefono e Messaggi, che leggono i contatti). */
    const val METHOD_RESOLVE = "resolve"
    /** Contatti della Rubrica per nome (per unire una persona della Chat a quella della Rubrica). */
    const val METHOD_SEARCH = "search"
    /** Manda un SMS (Messaggi). */
    const val METHOD_SEND = "send"
    /** Telefono: le chiamate da richiamare, la stessa lista «Da richiamare» dei Recenti (per la Postazione). */
    const val METHOD_CALLBACK = "callBack"
    /** Telefono: «Non richiamare» ([EXTRA_ADDRESS], fino all'ora [EXTRA_SINCE]). */
    const val METHOD_DISMISS_CALLBACK = "dismissCallBack"
    /** Email: segna come letta l'email [EXTRA_REF] («casella:uid»), sul telefono e nella casella. */
    const val METHOD_MARK_SEEN = "markSeen"

    /** Chat: chi sono io sulla Chat (per il link dell'invito) e se un contatto è già unito a una persona della Chat. */
    const val METHOD_ME = "me"
    const val METHOD_KNOWS = "knows"
    /** Chat: apre «Invita sulla Chat» per un contatto della Rubrica (tavola ChatInvita). */
    const val ACTION_INVITE = "eu.eurise.action.CHAT_INVITE"
    private const val CHAT_ACTIVITY = "eu.eurise.chat.ChatActivity"
    const val EXTRA_NAME = "name"
    const val EXTRA_KEY = "key"
    const val EXTRA_USER = "user"

    /** Telefono: chiama subito questo numero (solo dalle app della suite, attraverso un ingresso col permesso di firma). */
    const val ACTION_CALL = "eu.eurise.action.SUITE_CALL"
    private const val CALL_ALIAS = "eu.eurise.phone.SuiteCall"
    /** Email: apre questo messaggio ([EXTRA_REF] «casella:uid»). */
    const val ACTION_OPEN_MAIL = "eu.eurise.action.OPEN_MAIL"
    private const val MAIL_ACTIVITY = "eu.eurise.mail.MailActivity"

    const val EXTRA_PHONES = "phones"
    const val EXTRA_EMAILS = "emails"
    const val EXTRA_LIMIT = "limit"
    const val EXTRA_SINCE = "since"
    const val EXTRA_ADDRESS = "address"
    const val EXTRA_ADDRESSES = "addresses"
    const val EXTRA_TEXT = "text"
    const val EXTRA_QUERY = "query"
    const val EXTRA_RESULT = "result"
    const val EXTRA_REF = "ref"

    private const val K_KIND = "kind"
    private const val K_ADDRESS = "address"
    private const val K_NAME = "name"
    private const val K_TIME = "time"
    private const val K_TEXT = "text"
    private const val K_SUBJECT = "subject"
    private const val K_DURATION = "duration"
    private const val K_NOTE = "note"
    private const val K_UNREAD = "unread"
    private const val K_REF = "ref"
    private const val K_COUNT = "count"
    private const val K_INDEX = "index"
    private const val K_KEY = "key"
    private const val K_MATRIX = "matrix"

    /** Che cosa è successo, e da che parte. */
    enum class Kind { SmsIn, SmsOut, CallIn, CallOut, CallMissed, MailIn, MailOut }

    /**
     * Una cosa sola del filo: un SMS, una chiamata (con [duration] in secondi e la [note] scritta durante la chiamata) o
     * un'email ([subject], [name] di chi scrive). [ref] serve ad aprirla nella sua app (conversazione, «casella:uid»).
     */
    data class Item(
        val kind: Kind,
        val address: String,
        val time: Long,
        val text: String = "",
        val subject: String = "",
        val duration: Long = 0,
        val note: String = "",
        val unread: Boolean = false,
        val ref: String = "",
        val name: String = "",
    )

    /** L'ultima cosa con un indirizzo e quante sono da leggere. */
    data class Recent(val item: Item, val unread: Int)

    /** Una persona della Rubrica: tutti i suoi numeri, le email e gli utenti della Chat scritti nel contatto. */
    data class Person(val key: String, val name: String, val phones: List<String>, val emails: List<String>, val matrix: List<String>)

    /** Indirizzi per [resolve]: un numero, un'email o un utente della Chat (con il nome che mostra, per riconoscerlo). */
    fun tel(number: String) = "tel:$number"
    fun mailto(address: String) = "mailto:$address"
    fun mx(userId: String, displayName: String) = "mx:$userId|$displayName"

    // --- Lato fornitore (Messaggi, Telefono, Email) ---

    /** Vero se chi chiama è un'app della suite (Android non controlla da solo i permessi per call()). */
    fun allowed(context: Context): Boolean = SuiteAccess.callerAllowed(context)

    fun packItems(items: List<Item>): Bundle = Bundle().apply {
        putStringArray(K_KIND, items.map { it.kind.name }.toTypedArray())
        putStringArray(K_ADDRESS, items.map { it.address }.toTypedArray())
        putStringArray(K_NAME, items.map { it.name }.toTypedArray())
        putLongArray(K_TIME, items.map { it.time }.toLongArray())
        // Un testo lunghissimo non serve nel filo: si tiene la parte iniziale (il Bundle ha un limite di dimensione).
        putStringArray(K_TEXT, items.map { it.text.take(2000) }.toTypedArray())
        putStringArray(K_SUBJECT, items.map { it.subject.take(300) }.toTypedArray())
        putLongArray(K_DURATION, items.map { it.duration }.toLongArray())
        putStringArray(K_NOTE, items.map { it.note.take(2000) }.toTypedArray())
        putBooleanArray(K_UNREAD, items.map { it.unread }.toBooleanArray())
        putStringArray(K_REF, items.map { it.ref }.toTypedArray())
    }

    fun packRecent(list: List<Recent>): Bundle = packItems(list.map { it.item }).apply { putIntArray(K_COUNT, list.map { it.unread }.toIntArray()) }

    fun unpackItems(b: Bundle?): List<Item> {
        b ?: return emptyList()
        val kinds = b.getStringArray(K_KIND) ?: return emptyList()
        val address = b.getStringArray(K_ADDRESS); val name = b.getStringArray(K_NAME); val time = b.getLongArray(K_TIME)
        val text = b.getStringArray(K_TEXT); val subject = b.getStringArray(K_SUBJECT); val duration = b.getLongArray(K_DURATION)
        val note = b.getStringArray(K_NOTE); val unread = b.getBooleanArray(K_UNREAD); val ref = b.getStringArray(K_REF)
        return kinds.indices.mapNotNull { i ->
            val kind = runCatching { Kind.valueOf(kinds[i]) }.getOrNull() ?: return@mapNotNull null
            Item(
                kind, address?.getOrNull(i).orEmpty(), time?.getOrNull(i) ?: 0L, text?.getOrNull(i).orEmpty(), subject?.getOrNull(i).orEmpty(),
                duration?.getOrNull(i) ?: 0L, note?.getOrNull(i).orEmpty(), unread?.getOrNull(i) ?: false, ref?.getOrNull(i).orEmpty(),
                name?.getOrNull(i).orEmpty(),
            )
        }
    }

    fun unpackRecent(b: Bundle?): List<Recent> {
        val items = unpackItems(b)
        val counts = b?.getIntArray(K_COUNT)
        return items.mapIndexed { i, it -> Recent(it, counts?.getOrNull(i) ?: 0) }
    }

    /** [index] per ogni indirizzo chiesto: la posizione in [people] o -1 se non è in Rubrica. */
    fun packPeople(people: List<Person>, index: IntArray = IntArray(0)): Bundle = Bundle().apply {
        putIntArray(K_INDEX, index)
        putStringArray(K_KEY, people.map { it.key }.toTypedArray())
        putStringArray(K_NAME, people.map { it.name }.toTypedArray())
        putStringArray(EXTRA_PHONES, people.map { it.phones.joinToString("\n") }.toTypedArray())
        putStringArray(EXTRA_EMAILS, people.map { it.emails.joinToString("\n") }.toTypedArray())
        putStringArray(K_MATRIX, people.map { it.matrix.joinToString("\n") }.toTypedArray())
    }

    /** Le persone e, per ogni indirizzo chiesto, quale delle persone è (null se nessuna). */
    fun unpackPeople(b: Bundle?): Pair<List<Person>, List<Int>> {
        b ?: return emptyList<Person>() to emptyList()
        val keys = b.getStringArray(K_KEY) ?: return emptyList<Person>() to emptyList()
        fun split(s: String?) = s.orEmpty().split('\n').filter { it.isNotBlank() }
        val names = b.getStringArray(K_NAME); val phones = b.getStringArray(EXTRA_PHONES); val emails = b.getStringArray(EXTRA_EMAILS)
        val matrix = b.getStringArray(K_MATRIX)
        val people = keys.indices.map { i ->
            Person(keys[i], names?.getOrNull(i).orEmpty(), split(phones?.getOrNull(i)), split(emails?.getOrNull(i)), split(matrix?.getOrNull(i)))
        }
        return people to (b.getIntArray(K_INDEX)?.toList() ?: emptyList())
    }

    // --- Lato Chat ---

    private fun debug(context: Context) = context.packageName.endsWith(".debug")
    private fun chatPackage(context: Context) = "eu.eurise.chat" + if (debug(context)) ".debug" else ""

    /** Il mio utente della Chat (es. «@mario:matrix.org»), o null se la Chat non c'è o non è collegata. */
    fun chatUser(context: Context): String? = runCatching {
        SuiteAccess.call(context, Uri.parse("content://" + chatPackage(context) + ".persone"), METHOD_ME, null, null)?.getString(EXTRA_USER)
    }.getOrNull()

    /** Vero se nella Chat questo contatto ([key] della Rubrica) è già unito a una persona della Chat. */
    fun chatKnows(context: Context, key: String): Boolean = runCatching {
        SuiteAccess.call(context, Uri.parse("content://" + chatPackage(context) + ".persone"), METHOD_KNOWS, key, null)?.getBoolean(EXTRA_RESULT) == true
    }.getOrDefault(false)

    /** Apre nella Chat l'invito per un contatto, con nome, numeri ed email già scelti; null se la Chat non c'è. */
    fun inviteIntent(context: Context, key: String, name: String, phones: List<String>, emails: List<String>): Intent? {
        val intent = Intent(ACTION_INVITE).setComponent(ComponentName(chatPackage(context), CHAT_ACTIVITY))
            .putExtra(EXTRA_KEY, key).putExtra(EXTRA_NAME, name)
            .putExtra(EXTRA_PHONES, phones.toTypedArray()).putExtra(EXTRA_EMAILS, emails.toTypedArray())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent.takeIf { context.packageManager.resolveActivity(it, 0) != null }
    }
    fun packageOf(context: Context, app: App) = app.base + if (debug(context)) ".debug" else ""
    private fun uri(context: Context, app: App) = Uri.parse("content://" + packageOf(context, app) + ".persone")

    private fun call(context: Context, app: App, method: String, extras: Bundle?): Bundle? =
        runCatching { SuiteAccess.call(context, uri(context, app), method, null, extras) }.getOrNull()

    /** L'ultima cosa per indirizzo da [app] (fuori dal thread principale). Vuoto se l'app non c'è o non ha i permessi. */
    fun recent(context: Context, app: App, since: Long = 0L): List<Recent> =
        unpackRecent(call(context, app, METHOD_RECENT, Bundle().apply { putLong(EXTRA_SINCE, since) }))

    /** Da richiamare secondo Eurise Telefono (fuori dal thread principale); null se il Telefono non c'è o non risponde. */
    fun callBack(context: Context): List<Recent>? =
        call(context, App.Phone, METHOD_CALLBACK, null)?.let { unpackRecent(it) }

    /** «Non richiamare» nel Telefono (fuori dal thread principale). */
    fun dismissCallBack(context: Context, number: String, upTo: Long): Boolean =
        call(context, App.Phone, METHOD_DISMISS_CALLBACK, Bundle().apply { putString(EXTRA_ADDRESS, number); putLong(EXTRA_SINCE, upTo) })
            ?.getBoolean(EXTRA_RESULT) == true

    /** Segna come letta un'email in Eurise Email (fuori dal thread principale). */
    fun markMailSeen(context: Context, ref: String): Boolean =
        call(context, App.Mail, METHOD_MARK_SEEN, Bundle().apply { putString(EXTRA_REF, ref) })?.getBoolean(EXTRA_RESULT) == true

    /** Il filo con questi numeri ed email da [app] (fuori dal thread principale). */
    fun items(context: Context, app: App, phones: List<String>, emails: List<String>, limit: Int = 200): List<Item> {
        if (phones.isEmpty() && emails.isEmpty()) return emptyList()
        val extras = Bundle().apply {
            putStringArray(EXTRA_PHONES, phones.toTypedArray()); putStringArray(EXTRA_EMAILS, emails.toTypedArray()); putInt(EXTRA_LIMIT, limit)
        }
        return unpackItems(call(context, app, METHOD_ITEMS, extras))
    }

    /** Chi c'è in Rubrica dietro gli indirizzi (vedi [tel], [mailto], [mx]); prima dal Telefono, poi da Messaggi. */
    fun resolve(context: Context, addresses: List<String>): Pair<List<Person>, List<Int>> {
        if (addresses.isEmpty()) return emptyList<Person>() to emptyList()
        val extras = Bundle().apply { putStringArray(EXTRA_ADDRESSES, addresses.toTypedArray()) }
        for (app in listOf(App.Phone, App.Messages)) {
            val b = call(context, app, METHOD_RESOLVE, extras) ?: continue
            return unpackPeople(b)
        }
        return emptyList<Person>() to emptyList()
    }

    /** Contatti della Rubrica il cui nome contiene [query]. */
    fun search(context: Context, query: String): List<Person> {
        if (query.isBlank()) return emptyList()
        val extras = Bundle().apply { putString(EXTRA_QUERY, query) }
        for (app in listOf(App.Phone, App.Messages)) {
            val b = call(context, app, METHOD_SEARCH, extras) ?: continue
            return unpackPeople(b).first
        }
        return emptyList()
    }

    /** Manda un SMS da Messaggi. False se Messaggi non c'è o il messaggio non è partito. */
    fun sendSms(context: Context, number: String, text: String): Boolean {
        val extras = Bundle().apply { putString(EXTRA_ADDRESS, number); putString(EXTRA_TEXT, text) }
        return call(context, App.Messages, METHOD_SEND, extras)?.getBoolean(EXTRA_RESULT) == true
    }

    /** Chiama subito con Eurise Telefono; null se non c'è (allora si apre il tastierino di sistema). */
    fun callIntent(context: Context, number: String): Intent? {
        val pkg = packageOf(context, App.Phone)
        val intent = Intent(ACTION_CALL, Uri.fromParts("tel", number, null)).setComponent(ComponentName(pkg, CALL_ALIAS))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent.takeIf { context.packageManager.resolveActivity(it, 0) != null }
    }

    /** Apre in Eurise Email il messaggio [ref] («casella:uid»); null se Email non c'è. */
    fun openMailIntent(context: Context, ref: String): Intent? {
        val pkg = packageOf(context, App.Mail)
        val intent = Intent(ACTION_OPEN_MAIL).setComponent(ComponentName(pkg, MAIL_ACTIVITY)).putExtra(EXTRA_REF, ref)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent.takeIf { context.packageManager.resolveActivity(it, 0) != null }
    }

    /** Scrivi un'email con Eurise Email (o l'app di posta scelta) a [to], con il testo già scritto. */
    fun composeMailIntent(context: Context, to: String, body: String, subject: String = ""): Intent {
        // Oggetto e testo vanno nel link «mailto:» (li legge Email) e negli extra (li leggono le altre app di posta).
        val query = listOfNotNull(subject.takeIf { it.isNotBlank() }?.let { "subject=" + Uri.encode(it) }, "body=" + Uri.encode(body)).joinToString("&")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(to) + "?" + query)).putExtra(Intent.EXTRA_TEXT, body)
            .putExtra(Intent.EXTRA_SUBJECT, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ours = Intent(intent).setPackage(packageOf(context, App.Mail))
        return if (context.packageManager.resolveActivity(ours, 0) != null) ours else intent
    }
}

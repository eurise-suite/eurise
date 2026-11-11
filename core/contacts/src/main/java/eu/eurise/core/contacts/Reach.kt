package eu.eurise.core.contacts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * I modi per raggiungere una persona o agire su ciò che c'è in un messaggio: chiamata, SMS, WhatsApp, Telegram, email,
 * mappa, evento nel Calendario, contatto nuovo. Le app di chat si propongono solo se sono installate.
 */
object Reach {
    const val WHATSAPP = "com.whatsapp"
    const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    const val TELEGRAM = "org.telegram.messenger"
    const val TELEGRAM_FOSS = "org.telegram.messenger.web"
    const val SIGNAL = "org.thoughtcrime.securesms"

    fun installed(context: Context, pkg: String) = runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    /** Il numero senza spazi e trattini, con il + se c'era (serve alle app di chat). */
    fun digits(number: String): String = number.filter { it.isDigit() || it == '+' }

    /** Il numero con il prefisso internazionale (+39 se manca): WhatsApp e Telegram lo vogliono così. */
    fun international(number: String, country: String = "39"): String {
        val d = digits(number)
        return when {
            d.startsWith("+") -> d.drop(1)
            d.startsWith("00") -> d.drop(2)
            else -> country + d
        }
    }

    fun call(number: String) = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", digits(number), null))
    fun dial(number: String) = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", digits(number), null))
    fun sms(number: String, text: String? = null) = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", digits(number), null))
        .apply { text?.let { putExtra("sms_body", it) } }
    fun email(address: String) = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(address)))
    fun map(address: String) = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address)))
    fun web(url: String) = Intent(Intent.ACTION_VIEW, Uri.parse(if (url.startsWith("http")) url else "https://$url"))

    fun whatsapp(context: Context, number: String): Intent? {
        val pkg = listOf(WHATSAPP, WHATSAPP_BUSINESS).firstOrNull { installed(context, it) } ?: return null
        return Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=" + international(number))).setPackage(pkg)
    }

    fun telegram(context: Context, number: String): Intent? {
        val pkg = listOf(TELEGRAM, TELEGRAM_FOSS).firstOrNull { installed(context, it) } ?: return null
        return Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?phone=" + international(number))).setPackage(pkg)
    }

    fun signal(context: Context, number: String): Intent? {
        if (!installed(context, SIGNAL)) return null
        return Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", "+" + international(number), null)).setPackage(SIGNAL)
    }

    /** Contatto nuovo con questo numero, nella rubrica di sistema. */
    fun addContact(number: String? = null, email: String? = null, name: String? = null) =
        Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE).apply {
            number?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
            email?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
            name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
        }

    /** Evento nuovo nel Calendario, già compilato (si conferma nell'app del calendario). */
    fun event(title: String, start: LocalDateTime, minutes: Long = 60, location: String? = null, description: String? = null): Intent {
        val begin = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + minutes * 60_000)
            .putExtra(CalendarContract.Events.TITLE, title)
            .apply {
                location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
                description?.let { putExtra(CalendarContract.Events.DESCRIPTION, it) }
            }
    }

    /** Evento di un giorno intero (quando nel messaggio c'è il giorno ma non l'ora). */
    fun allDayEvent(title: String, day: java.time.LocalDate): Intent {
        val begin = day.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
        return Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 86_400_000)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            .putExtra(CalendarContract.Events.TITLE, title)
    }

    /** Apre [intent]; falso se nessuna app lo gestisce. */
    fun open(context: Context, intent: Intent?): Boolean {
        if (intent == null) return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}

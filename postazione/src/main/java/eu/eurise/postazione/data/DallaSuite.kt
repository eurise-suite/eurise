package eu.eurise.postazione.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.eurise.core.suite.PeopleContract

/**
 * «Ti hanno cercato» e «Da rispondere» anche senza notifiche: le chiamate da richiamare le dice Eurise Telefono (la
 * stessa lista dei Recenti, che resta anche dopo aver aperto il Telefono) e le email delle persone ancora da leggere le
 * dice Eurise Email. Solo tra app firmate con la stessa chiave, sul telefono; se un'app non c'è, quella parte resta
 * alle notifiche. Viste e rimandate si ricordano qui (la chiave e fino a quando), non si toccano Telefono ed Email.
 */
object DallaSuite {
    class Letto(val avvisi: List<Avviso>, val telefono: Boolean, val posta: Boolean)

    private const val TRE_GIORNI = 3 * 86_400_000L
    private const val K_VISTI = "suite.visti"

    fun leggi(context: Context): Letto {
        if (!Uso.Suite.acceso(context)) return Letto(emptyList(), false, false)
        val ora = System.currentTimeMillis()
        val via = visti(context, ora)
        val chiamate = runCatching { PeopleContract.callBack(context) }.getOrNull()
        val posta = runCatching { if (installata(context, PeopleContract.App.Mail)) PeopleContract.recent(context, PeopleContract.App.Mail) else null }.getOrNull()
        val pkgTel = PeopleContract.packageOf(context, PeopleContract.App.Phone)
        val pkgMail = PeopleContract.packageOf(context, PeopleContract.App.Mail)
        val lista = ArrayList<Avviso>()
        chiamate?.forEach { r ->
            val chiave = "tel:" + r.item.address + ":" + r.item.time
            if (chiave in via) return@forEach
            lista += Avviso(
                chiave, pkgTel, r.item.name.ifBlank { r.item.address }, "", r.item.time, null,
                chiamata = true, numero = r.item.address, quante = r.unread.coerceAtLeast(1),
            )
        }
        posta?.filter { it.unread > 0 && ora - it.item.time < TRE_GIORNI && it.item.ref.isNotBlank() }?.forEach { r ->
            val chiave = "mail:" + r.item.ref
            if (chiave in via) return@forEach
            lista += Avviso(
                chiave, pkgMail, r.item.name.ifBlank { r.item.address }, r.item.subject, r.item.time, null,
                rifMail = r.item.ref, indirizzo = r.item.address, quante = r.unread,
            )
        }
        return Letto(lista, chiamate != null, posta != null)
    }

    private fun installata(context: Context, app: PeopleContract.App) =
        runCatching { context.packageManager.getPackageInfo(PeopleContract.packageOf(context, app), 0) }.isSuccess

    /** «Vista»: non torna più; «Più tardi»: torna fra un'ora. */
    fun nascondi(context: Context, a: Avviso, perUnOra: Boolean) {
        val fino = if (perUnOra) System.currentTimeMillis() + 3_600_000L else Long.MAX_VALUE
        val p = context.getSharedPreferences("postazione", Context.MODE_PRIVATE)
        val righe = p.getString(K_VISTI, null).orEmpty().lines().filter { it.isNotBlank() }.takeLast(200) + "${a.chiave}|$fino"
        p.edit().putString(K_VISTI, righe.joinToString("\n")).apply()
    }

    private fun visti(context: Context, ora: Long): Set<String> =
        context.getSharedPreferences("postazione", Context.MODE_PRIVATE).getString(K_VISTI, null).orEmpty().lines()
            .mapNotNull { r -> r.substringBeforeLast('|', "").takeIf { it.isNotEmpty() && (r.substringAfterLast('|').toLongOrNull() ?: 0L) > ora } }
            .toSet()

    /** Richiama con Eurise Telefono (subito) o, se manca, con il tastierino del telefono. */
    fun richiama(context: Context, numero: String) {
        val i = PeopleContract.callIntent(context, numero)
            ?: Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", numero, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(i) }
    }

    /** Un SMS scritto da te, mandato da Eurise Messaggi; falso se Messaggi non c'è (allora si apre l'app degli SMS). */
    fun sms(context: Context, numero: String, testo: String): Boolean = runCatching { PeopleContract.sendSms(context, numero, testo) }.getOrDefault(false)

    fun apriMail(context: Context, a: Avviso): Boolean =
        PeopleContract.openMailIntent(context, a.rifMail)?.let { runCatching { context.startActivity(it) }.isSuccess } ?: false

    /** Rispondi a un'email: si apre la scrittura con il testo già scritto (l'invio lo fai tu). */
    fun rispondiMail(context: Context, a: Avviso, testo: String) {
        runCatching { context.startActivity(PeopleContract.composeMailIntent(context, a.indirizzo, testo).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

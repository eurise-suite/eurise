package eu.eurise.postazione.data

import android.content.Context
import android.net.Uri
import eu.eurise.core.suite.PostazioneContract as C
import eu.eurise.core.suite.SuiteAccess

/** Un impegno: [id] per aprirlo nel Calendario; [eliminabile] se il calendario si può scrivere e non si ripete. */
data class Impegno(
    val titolo: String, val luogo: String, val inizio: Long, val fine: Long, val tuttoIlGiorno: Boolean,
    val id: Long = 0L, val eliminabile: Boolean = false,
)

/** Una cosa da fare; [farmaco] se è la dose di un farmaco (si prende o si salta, non va a domani). */
data class Cosa(val id: Long, val titolo: String, val scadenza: Long, val scaduta: Boolean, val farmaco: Boolean = false)

/** Una cosa trovata dalla lente nell'app Eurise. */
data class Trovata(val tipo: String, val id: Long, val titolo: String, val dettaglio: String, val quando: Long)

/** L'oroscopo dall'app Eurise: [acceso] è il servizio «Oroscopo» del centro privacy. */
data class Oroscopo(val acceso: Boolean, val segno: Int, val segni: List<String>, val testo: String, val fonte: String, val link: String)

/** Il meteo già sul telefono (copia dell'app Eurise): gradi, cielo (nome di `Sky`) e prima ora di pioggia (0 = niente). */
data class Meteo(val gradi: Int, val cielo: String, val pioggia: Long)

/** Una notizia dalle fonti scelte nell'app Eurise. */
data class Notizia(val titolo: String, val fonte: String, val link: String, val quando: Long)

/** La giornata dall'app Eurise; [collegata] è falso se l'app Eurise non c'è (la Postazione funziona anche da sola). */
data class Giornata(
    val collegata: Boolean = false,
    val impegni: List<Impegno> = emptyList(),
    val cose: List<Cosa> = emptyList(),
    val aperte: Int = 0,
    val fatte: Int = 0,
    val meteo: Meteo? = null,
    /** L'app Eurise vede il calendario (senza il suo permesso gli impegni non arrivano). */
    val calendario: Boolean = true,
)

object LettoreGiornata {
    /** Da chiamare fuori dal thread principale. */
    fun leggi(context: Context, hub: String): Giornata {
        val uri = Uri.parse("content://" + C.authority(hub))
        val b = runCatching { SuiteAccess.call(context, uri, C.METHOD_TODAY, null, null) }.getOrNull() ?: return Giornata()
        val titoli = b.getStringArray(C.KEY_EV_TITLE) ?: emptyArray()
        val luoghi = b.getStringArray(C.KEY_EV_PLACE) ?: emptyArray()
        val inizi = b.getLongArray(C.KEY_EV_BEGIN) ?: LongArray(0)
        val fini = b.getLongArray(C.KEY_EV_END) ?: LongArray(0)
        val interi = b.getBooleanArray(C.KEY_EV_ALLDAY) ?: BooleanArray(0)
        val evId = b.getLongArray(C.KEY_EV_ID) ?: LongArray(0)
        val evEdit = b.getBooleanArray(C.KEY_EV_EDIT) ?: BooleanArray(0)
        val impegni = titoli.indices.filter { it < inizi.size && it < fini.size }.map { i ->
            Impegno(titoli[i], luoghi.getOrElse(i) { "" }, inizi[i], fini[i], interi.getOrElse(i) { false }, evId.getOrElse(i) { 0L }, evEdit.getOrElse(i) { false })
        }
        val ids = b.getLongArray(C.KEY_TASK_ID) ?: LongArray(0)
        val cTitoli = b.getStringArray(C.KEY_TASK_TITLE) ?: emptyArray()
        val scad = b.getLongArray(C.KEY_TASK_DUE) ?: LongArray(0)
        val late = b.getBooleanArray(C.KEY_TASK_LATE) ?: BooleanArray(0)
        val med = b.getBooleanArray(C.KEY_TASK_MED) ?: BooleanArray(0)
        val cose = ids.indices.filter { it < cTitoli.size }.map { i ->
            Cosa(ids[i], cTitoli[i], scad.getOrElse(i) { 0L }, late.getOrElse(i) { false }, med.getOrElse(i) { false })
        }
        val meteo = if (b.getBoolean(C.KEY_W_OK)) Meteo(b.getInt(C.KEY_W_TEMP), b.getString(C.KEY_W_SKY).orEmpty(), b.getLong(C.KEY_W_RAIN)) else null
        return Giornata(true, impegni, cose, b.getInt(C.KEY_TASK_OPEN), b.getInt(C.KEY_TASK_DONE), meteo, b.getBoolean(C.KEY_EV_PERM, true))
    }

    /** «Chiudi la giornata»: le cose aperte passano a domani. Torna quante, o -1 se l'app Eurise non risponde. */
    fun rimanda(context: Context, hub: String): Int {
        val uri = Uri.parse("content://" + C.authority(hub))
        val b = runCatching { SuiteAccess.call(context, uri, C.METHOD_POSTPONE, null, null) }.getOrNull() ?: return -1
        return b.getInt(C.KEY_MOVED)
    }

    /** «Mettile lì»: un orario alle cose [ids], una ogni 25 minuti da [inizio]. Torna quante, o -1. */
    fun metti(context: Context, hub: String, ids: LongArray, inizio: Long): Int {
        val uri = Uri.parse("content://" + C.authority(hub))
        val extra = android.os.Bundle().apply { putLongArray(C.EXTRA_IDS, ids); putLong(C.EXTRA_START, inizio) }
        val b = runCatching { SuiteAccess.call(context, uri, C.METHOD_SCHEDULE, null, extra) }.getOrNull() ?: return -1
        return b.getInt(C.KEY_MOVED)
    }

    /** Le notizie le legge l'app Eurise (servizio «Notizie» del centro privacy); null se l'app Eurise non risponde. */
    fun notizie(context: Context, hub: String): List<Notizia>? {
        val uri = Uri.parse("content://" + C.authority(hub))
        val b = runCatching { SuiteAccess.call(context, uri, C.METHOD_NEWS, null, null) }.getOrNull() ?: return null
        val titoli = b.getStringArray(C.KEY_N_TITLE) ?: emptyArray()
        val fonti = b.getStringArray(C.KEY_N_SOURCE) ?: emptyArray()
        val link = b.getStringArray(C.KEY_N_LINK) ?: emptyArray()
        val quando = b.getLongArray(C.KEY_N_WHEN) ?: LongArray(0)
        return titoli.indices.filter { it < link.size }.map { i -> Notizia(titoli[i], fonti.getOrElse(i) { "" }, link[i], quando.getOrElse(i) { 0L }) }
    }

    /** Fatta, a domani o eliminata ([C.ACT_DONE], [C.ACT_TOMORROW], [C.ACT_DELETE]); vero se l'app Eurise l'ha fatto. */
    fun cosa(context: Context, hub: String, id: Long, azione: String): Boolean {
        val uri = Uri.parse("content://" + C.authority(hub))
        val extra = android.os.Bundle().apply { putLong(C.EXTRA_ID, id) }
        return runCatching { SuiteAccess.call(context, uri, C.METHOD_TASK, azione, extra)?.getBoolean(C.KEY_OK) == true }.getOrDefault(false)
    }

    /** Una cosa da fare per oggi nell'app Eurise («Richiamare Giulia»); vero se l'ha scritta. */
    fun aggiungiCosa(context: Context, hub: String, titolo: String): Boolean {
        val uri = Uri.parse("content://" + C.authority(hub))
        return runCatching { SuiteAccess.call(context, uri, C.METHOD_ADD_TASK, titolo.take(C.MAX_TITLE), null)?.getBoolean(C.KEY_OK) == true }.getOrDefault(false)
    }

    fun eliminaImpegno(context: Context, hub: String, id: Long): Boolean {
        val uri = Uri.parse("content://" + C.authority(hub))
        val extra = android.os.Bundle().apply { putLong(C.EXTRA_ID, id) }
        return runCatching { SuiteAccess.call(context, uri, C.METHOD_EVENT, C.ACT_DELETE, extra)?.getBoolean(C.KEY_OK) == true }.getOrDefault(false)
    }

    /** La ricerca nell'app Eurise (impegni, cose da fare, note, documenti); vuota se l'app Eurise manca. */
    fun cerca(context: Context, hub: String, testo: String): List<Trovata> {
        val q = testo.trim().take(C.MAX_QUERY)
        if (q.length < 2) return emptyList()
        val uri = Uri.parse("content://" + C.authority(hub))
        val b = runCatching { SuiteAccess.call(context, uri, C.METHOD_SEARCH, q, null) }.getOrNull() ?: return emptyList()
        val tipi = b.getStringArray(C.KEY_S_KIND) ?: emptyArray()
        val ids = b.getLongArray(C.KEY_S_ID) ?: LongArray(0)
        val titoli = b.getStringArray(C.KEY_S_TITLE) ?: emptyArray()
        val dett = b.getStringArray(C.KEY_S_DETAIL) ?: emptyArray()
        val quando = b.getLongArray(C.KEY_S_WHEN) ?: LongArray(0)
        return tipi.indices.filter { it < ids.size && it < titoli.size }.map { i ->
            Trovata(tipi[i], ids[i], titoli[i], dett.getOrElse(i) { "" }, quando.getOrElse(i) { 0L })
        }
    }

    /** L'oroscopo; [segno] (facoltativo) cambia il segno. Null se l'app Eurise non risponde. */
    fun oroscopo(context: Context, hub: String, segno: Int? = null): Oroscopo? {
        val uri = Uri.parse("content://" + C.authority(hub))
        val extra = segno?.let { android.os.Bundle().apply { putInt(C.EXTRA_SIGN, it) } }
        val b = runCatching { SuiteAccess.call(context, uri, C.METHOD_HOROSCOPE, null, extra) }.getOrNull() ?: return null
        return Oroscopo(
            b.getBoolean(C.KEY_H_ON), b.getInt(C.KEY_H_SIGN), b.getStringArray(C.KEY_H_SIGNS)?.toList().orEmpty(),
            b.getString(C.KEY_H_TEXT).orEmpty(), b.getString(C.KEY_H_SOURCE).orEmpty(), b.getString(C.KEY_H_LINK).orEmpty(),
        )
    }
}

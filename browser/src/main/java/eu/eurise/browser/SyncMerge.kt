package eu.eurise.browser

import org.json.JSONArray
import org.json.JSONObject

/**
 * Una voce sincronizzata tra telefono e computer (tavola BrwSincro): segnalibro, sito fissato o «Leggi dopo».
 * L'indirizzo è la chiave: due voci con lo stesso indirizzo sono la stessa voce.
 */
data class SyncItem(val url: String, val title: String, val folder: String = "", val pinned: Boolean = false)

/**
 * L'unione a tre vie della sincronizzazione, senza Android (si prova con i test). Il telefono ricorda, per ogni computer,
 * lo stato comune dell'ultima volta ([base]); da lì capisce chi ha aggiunto, cambiato o tolto cosa:
 * - aggiunta da una parte sola: entra;
 * - tolta da una parte e non cambiata dall'altra: esce (non torna alla sincronizzazione dopo);
 * - tolta da una parte ma cambiata dall'altra: resta, con il cambiamento (non si perde un lavoro);
 * - cambiata da tutte e due: campo per campo vince chi l'ha cambiato; se l'hanno cambiato entrambi, il telefono.
 * Alla prima volta ([base] vuota) è un'unione: niente si perde, a parità vince il telefono.
 */
object SyncMerge {
    /** Oltre tanto non si sincronizza (il resto resta dove sta): il messaggio verso Firefox ha un tetto di 1 MB. */
    const val MAX = 2000

    fun merge(base: List<SyncItem>, phone: List<SyncItem>, pc: List<SyncItem>): List<SyncItem> {
        val b = base.associateBy { it.url }
        val p = phone.associateBy { it.url }
        val c = pc.associateBy { it.url }
        // L'ordine: quello del telefono, poi le voci nuove del computer nel loro ordine.
        val order = LinkedHashSet<String>().apply { addAll(p.keys); addAll(c.keys) }
        val out = ArrayList<SyncItem>()
        for (url in order) {
            val was = b[url]
            val onPhone = p[url]
            val onPc = c[url]
            val item = when {
                was == null -> when {
                    onPhone != null && onPc != null -> onPhone.copy(pinned = onPhone.pinned || onPc.pinned)
                    else -> onPhone ?: onPc
                }
                onPhone == null && onPc == null -> null
                onPhone == null -> onPc!!.takeIf { it != was }
                onPc == null -> onPhone.takeIf { it != was }
                else -> SyncItem(
                    url,
                    title = pick(was.title, onPhone.title, onPc.title),
                    folder = pick(was.folder, onPhone.folder, onPc.folder),
                    pinned = pick(was.pinned, onPhone.pinned, onPc.pinned),
                )
            }
            if (item != null) out += item
        }
        return out.take(MAX)
    }

    private fun <T> pick(was: T, phone: T, pc: T): T = if (phone != was) phone else pc

    // --- Formato sul Ponte: un array JSON di {u, t, f, p} ---

    fun encode(items: List<SyncItem>): String {
        val a = JSONArray()
        items.take(MAX).forEach { i ->
            val o = JSONObject().put("u", i.url).put("t", i.title.take(300))
            if (i.folder.isNotEmpty()) o.put("f", i.folder.take(300))
            if (i.pinned) o.put("p", 1)
            a.put(o)
        }
        return a.toString()
    }

    /** Solo indirizzi web; ciò che non si legge si salta. */
    fun decode(text: String?): List<SyncItem> {
        if (text.isNullOrBlank()) return emptyList()
        val a = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return (0 until minOf(a.length(), MAX)).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("u").takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return@mapNotNull null
            SyncItem(url, o.optString("t").ifBlank { Links.host(url) }, o.optString("f"), o.optInt("p") == 1)
        }.distinctBy { it.url }
    }
}

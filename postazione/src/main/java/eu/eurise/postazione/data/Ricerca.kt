package eu.eurise.postazione.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts

/** Un contatto trovato dalla lente: [numero] vuoto se non ne ha uno. */
data class Persona(val id: Long, val chiave: String, val nome: String, val numero: String)

object Rubrica {
    /**
     * I contatti che hanno [testo] nel nome o nel numero, al più [max]. Solo con il permesso dei contatti e l'interruttore
     * acceso ([Preferiti.permesso]); da chiamare fuori dal thread principale. Nulla esce dalla Postazione.
     */
    fun cerca(context: Context, testo: String, max: Int = 4): List<Persona> {
        val q = testo.trim()
        if (q.length < 2 || !Preferiti.permesso(context)) return emptyList()
        val trovati = LinkedHashMap<Long, Persona>()
        val cifre = q.filter { it.isDigit() }
        runCatching {
            val uri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(q))
            context.contentResolver.query(uri, arrayOf(Phone.CONTACT_ID, Phone.LOOKUP_KEY, Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use { c ->
                while (c.moveToNext() && trovati.size < max) {
                    val id = c.getLong(0)
                    if (id !in trovati) trovati[id] = Persona(id, c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(3).orEmpty())
                }
            }
        }
        // Chi non ha numeri non compare tra i telefoni: si cerca anche per nome.
        if (trovati.size < max && cifre.length < q.length) runCatching {
            val uri = Uri.withAppendedPath(Contacts.CONTENT_FILTER_URI, Uri.encode(q))
            context.contentResolver.query(uri, arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY), null, null, null)?.use { c ->
                while (c.moveToNext() && trovati.size < max) {
                    val id = c.getLong(0)
                    if (id !in trovati) trovati[id] = Persona(id, c.getString(1).orEmpty(), c.getString(2).orEmpty(), "")
                }
            }
        }
        return trovati.values.toList()
    }
}

/**
 * Le intelligenze artificiali a cui la lente può passare la domanda. Alfred resta sul telefono; le altre si aprono nel
 * browser con la domanda già scritta, **solo al tocco**: in quel momento la domanda va a quel servizio. Le libere ed
 * europee vengono prima; i servizi chiusi statunitensi sono in fondo, con una o due tacchette e l'avviso prima di
 * sceglierli come preferiti (regola «Servizi chiusi» di CLAUDE.md).
 */
enum class Ai(val nome: String, val tacche: Int, val dove: String, private val indirizzo: String?) {
    Alfred("Alfred", 10, "Phone", null),
    Lumo("Lumo · Proton", 8, "CH", "https://lumo.proton.me/"),
    LeChat("Le Chat · Mistral", 6, "EU", "https://chat.mistral.ai/chat?q="),
    Claude("Claude · Anthropic", 2, "US", "https://claude.ai/new?q="),
    Perplexity("Perplexity", 2, "US", "https://www.perplexity.ai/search?q="),
    ChatGpt("ChatGPT · OpenAI", 1, "US", "https://chatgpt.com/?q=");

    val sulTelefono: Boolean get() = indirizzo == null
    val chiuso: Boolean get() = dove == "US"

    /** L'indirizzo con la domanda; Lumo non la accetta nell'indirizzo e si apre vuoto. */
    fun pagina(domanda: String): String? = indirizzo?.let {
        if (it.endsWith("=")) it + java.net.URLEncoder.encode(domanda, Charsets.UTF_8).replace("+", "%20") else it
    }

    companion object {
        fun da(nome: String?): Ai = entries.firstOrNull { it.name == nome } ?: Alfred
    }
}

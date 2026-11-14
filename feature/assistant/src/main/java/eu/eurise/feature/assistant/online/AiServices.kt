package eu.eurise.feature.assistant.online

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Un'AI online a cui Alfred passa la domanda già ripulita (tavole AlfScegli, AlfFinestra). Le web app si aprono nella
 * finestra AI di Eurise, col proprio account; le app installate ricevono il testo condiviso. [score] è il semaforo su
 * dieci, [where] il paese ("CH", "UE", "USA"), [closed] i servizi chiusi da scoraggiare (regola del 12 novembre).
 */
data class AiService(
    val id: String,
    val name: String,
    val company: String,
    val url: String,
    val score: Int,
    val where: String,
    val noteRes: Int = 0,
    val warnRes: Int = 0,
    /** L'indirizzo che porta già la domanda: «%s» si sostituisce con il testo. Vuoto: lo script la scrive nel campo. */
    val prefill: String = "",
    /** App installata a cui mandare il testo (al posto della web app). */
    val pkg: String = "",
    val closed: Boolean = false,
    val custom: Boolean = false,
) {
    val isApp: Boolean get() = pkg.isNotEmpty()

    /** I domini in cui la finestra resta: quello del servizio e i suoi accessi; il resto si apre nel browser. */
    val home: String get() = registrable(Uri.parse(url).host.orEmpty())

    fun startUrl(question: String): String =
        if (prefill.isNotEmpty() && question.isNotEmpty()) prefill.replace("%s", Uri.encode(question)) else url

    companion object {
        /** «chat.mistral.ai» → «mistral.ai»; basta per tenere insieme sito e pagine d'accesso dello stesso servizio. */
        fun registrable(host: String): String = host.split('.').takeLast(2).joinToString(".")
    }
}

/**
 * I servizi offerti, il preferito (la stellina) e quelli aggiunti dall'utente. Le scelte restano sul telefono.
 * Lumo per primo e preferito di base: conversazioni cifrate, nessun registro (decisione del 14 novembre).
 */
class AiServices(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("alfred_online", Context.MODE_PRIVATE)

    val builtIn: List<AiService> = listOf(
        AiService("lumo", "Lumo", "Proton", "https://lumo.proton.me/", 8, "CH", noteRes = eu.eurise.feature.assistant.R.string.alfred_on_lumo_d),
        AiService("euria", "Euria", "Infomaniak", "https://euria.infomaniak.com/", 7, "CH", noteRes = eu.eurise.feature.assistant.R.string.alfred_on_euria_d),
        AiService(
            "lechat", "Le Chat", "Mistral", "https://chat.mistral.ai/chat", 6, "UE",
            noteRes = eu.eurise.feature.assistant.R.string.alfred_on_lechat_d, warnRes = eu.eurise.feature.assistant.R.string.alfred_on_lechat_warn,
            prefill = "https://chat.mistral.ai/chat?q=%s",
        ),
    )

    /** I servizi chiusi più noti, che si possono aggiungere con l'avviso; mai preferiti di base. */
    val closedOptions: List<AiService> = listOf(
        AiService("chatgpt", "ChatGPT", "OpenAI", "https://chatgpt.com/", 2, "USA", warnRes = eu.eurise.feature.assistant.R.string.alfred_on_closed_warn, closed = true, prefill = "https://chatgpt.com/?q=%s"),
        AiService("gemini", "Gemini", "Google", "https://gemini.google.com/app", 1, "USA", warnRes = eu.eurise.feature.assistant.R.string.alfred_on_closed_warn, closed = true),
        AiService("copilot", "Copilot", "Microsoft", "https://copilot.microsoft.com/", 1, "USA", warnRes = eu.eurise.feature.assistant.R.string.alfred_on_closed_warn, closed = true),
    )

    fun all(): List<AiService> = builtIn + custom()

    fun preferred(): AiService = all().firstOrNull { it.id == prefs.getString(KEY_FAV, "lumo") } ?: builtIn.first()

    fun setPreferred(s: AiService) = prefs.edit().putString(KEY_FAV, s.id).apply()

    /** Aggiunti dall'utente: «id|nome|indirizzo|pacchetto|chiuso», una riga ciascuno. */
    fun custom(): List<AiService> = prefs.getString(KEY_CUSTOM, "").orEmpty().lines().filter { it.isNotBlank() }.mapNotNull { line ->
        val p = line.split('|')
        if (p.size < 5) return@mapNotNull null
        val closed = p[4] == "1"
        val url = p[2]
        AiService(
            p[0], p[1], if (p[3].isNotEmpty()) "app" else Uri.parse(url).host.orEmpty(), url,
            if (closed) 2 else 5, if (closed) "USA" else "", closed = closed, custom = true, pkg = p[3],
            warnRes = if (closed) eu.eurise.feature.assistant.R.string.alfred_on_closed_warn else 0,
            prefill = if (url.contains("%s")) url else "",
        )
    }

    fun add(s: AiService) {
        val line = listOf(s.id, s.name.replace('|', ' '), s.url, s.pkg, if (s.closed) "1" else "0").joinToString("|")
        val rest = prefs.getString(KEY_CUSTOM, "").orEmpty().lines().filter { it.isNotBlank() && !it.startsWith(s.id + "|") }
        prefs.edit().putString(KEY_CUSTOM, (rest + line).joinToString("\n")).apply()
    }

    fun remove(s: AiService) {
        val rest = prefs.getString(KEY_CUSTOM, "").orEmpty().lines().filter { it.isNotBlank() && !it.startsWith(s.id + "|") }
        prefs.edit().putString(KEY_CUSTOM, rest.joinToString("\n")).apply()
        if (prefs.getString(KEY_FAV, "") == s.id) prefs.edit().remove(KEY_FAV).apply()
    }

    /** Da un indirizzo incollato: con «%s» la domanda va nell'indirizzo, altrimenti la scrive lo script nel campo. */
    fun fromAddress(address: String): AiService? {
        val url = address.trim().let { if (it.startsWith("http")) it else "https://$it" }
        val host = Uri.parse(url.replace("%s", "x")).host ?: return null
        if (!host.contains('.')) return null
        val closed = CLOSED_HOSTS.any { host.endsWith(it) }
        return AiService("web:" + host, host.removePrefix("www."), host, url, if (closed) 2 else 5, if (closed) "USA" else "",
            closed = closed, custom = true, prefill = if (url.contains("%s")) url else "")
    }

    /** Le app installate che accettano un testo condiviso e sembrano assistenti AI (per «Da un'app installata»). */
    fun installedApps(): List<AiService> {
        val pm = app.packageManager
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        return pm.queryIntentActivities(send, PackageManager.MATCH_DEFAULT_ONLY)
            .asSequence()
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { (pkg, _) -> pkg != app.packageName && !pkg.startsWith("eu.eurise") }
            .filter { (pkg, label) -> AI_HINTS.any { pkg.contains(it, true) || label.contains(it, true) } }
            .distinctBy { it.first }
            .map { (pkg, label) ->
                val closed = CLOSED_PACKAGES.any { pkg.startsWith(it) }
                AiService("app:$pkg", label, "app", "", if (closed) 2 else 5, if (closed) "USA" else "", pkg = pkg, closed = closed, custom = true)
            }.toList()
    }

    /** Manda la domanda a un'app installata: arriva già scritta nel suo campo (se l'app lo permette). */
    fun shareTo(s: AiService, question: String): Boolean = runCatching {
        app.startActivity(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, question)
            .setPackage(s.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)

    private companion object {
        const val KEY_FAV = "preferred"
        const val KEY_CUSTOM = "custom"
        val AI_HINTS = listOf("lumo", "euria", "mistral", "chat", "gpt", "gemini", "copilot", "claude", "perplexity", "assistant", "ai.")
        val CLOSED_HOSTS = listOf("openai.com", "chatgpt.com", "google.com", "microsoft.com", "bing.com", "claude.ai", "meta.ai", "perplexity.ai", "x.ai", "grok.com")
        val CLOSED_PACKAGES = listOf("com.openai", "com.google", "com.microsoft", "com.anthropic", "ai.perplexity", "com.facebook", "com.meta", "ai.x.")
    }
}

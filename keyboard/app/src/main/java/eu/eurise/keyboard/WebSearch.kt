package eu.eurise.keyboard

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.prefs

/**
 * I tasti Browser e AI della barra. La ricerca si apre in un riquadro sopra l'app in cui si scrive (lo mostra l'app
 * Eurise: la tastiera non va su Internet); dal riquadro "Apri nel browser" o "Apri nell'app" porta la ricerca fuori.
 * Senza l'app Eurise si apre direttamente il browser o l'app scelta.
 */
internal object WebSearch {
    const val PREF = "eurise_web_target"
    const val PREF_ENGINE = "eurise_web_engine"
    const val PREF_AI = "eurise_ai_target"

    /** Un posto dove cercare: un browser installato ([pkg]) o un sito con la domanda nell'indirizzo ([url] con %s). */
    data class Target(val id: String, val label: String, val pkg: String? = null, val url: String? = null, val home: String? = null)

    /** Motori per il riquadro del tasto Browser; DuckDuckGo di base. */
    val engines = listOf(
        Target("engine:ddg", "DuckDuckGo", url = "https://duckduckgo.com/?q=%s", home = "https://duckduckgo.com/"),
        Target("engine:startpage", "Startpage", url = "https://www.startpage.com/do/search?q=%s", home = "https://www.startpage.com/"),
        Target("engine:qwant", "Qwant", url = "https://www.qwant.com/?q=%s", home = "https://www.qwant.com/"),
        Target("engine:ecosia", "Ecosia", url = "https://www.ecosia.org/search?q=%s", home = "https://www.ecosia.org/"),
        Target("engine:google", "Google", url = "https://www.google.com/search?q=%s", home = "https://www.google.com/"),
    )

    /** Assistenti che accettano la domanda nell'indirizzo; [pkg] è la loro app, per "Apri nell'app". */
    val assistants = listOf(
        Target("ai:lechat", "Le Chat", pkg = "ai.mistral.chat", url = "https://chat.mistral.ai/chat?q=%s", home = "https://chat.mistral.ai/chat"),
        Target("ai:claude", "Claude", pkg = "com.anthropic.claude", url = "https://claude.ai/new?q=%s", home = "https://claude.ai/new"),
        Target("ai:chatgpt", "ChatGPT", pkg = "com.openai.chatgpt", url = "https://chatgpt.com/?q=%s", home = "https://chatgpt.com/"),
        Target("ai:perplexity", "Perplexity", pkg = "ai.perplexity.app.android", url = "https://www.perplexity.ai/search?q=%s", home = "https://www.perplexity.ai/"),
        Target("ai:duck", "Duck.ai", pkg = "com.duckduckgo.mobile.android", url = "https://duck.ai/?q=%s", home = "https://duck.ai/"),
    )

    fun browsers(context: Context): List<Target> {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://eurise.eu")).addCategory(Intent.CATEGORY_BROWSABLE)
        return context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
            .distinctBy { it.first }
            .map { (pkg, label) -> Target("pkg:$pkg", label, pkg = pkg) }
            .sortedBy { it.label.lowercase() }
    }

    /** Il browser per "Apri nel browser" (nessuno = quello predefinito). Le vecchie scelte di assistenti passano al tasto AI. */
    fun browser(context: Context): Target? {
        val id = context.prefs().getString(PREF, null) ?: return null
        if (id.startsWith("ai:")) {
            context.prefs().edit().remove(PREF).apply { if (!context.prefs().contains(PREF_AI)) putString(PREF_AI, id) }.apply()
            return null
        }
        return id.removePrefix("pkg:").takeIf { id.startsWith("pkg:") }?.let { Target(id, it, pkg = it) }
    }

    fun engine(context: Context): Target =
        engines.firstOrNull { it.id == context.prefs().getString(PREF_ENGINE, null) } ?: engines.first()

    /** L'assistente collegato al tasto AI; di base nessuno. */
    fun assistant(context: Context): Target? {
        browser(context) // porta qui una scelta fatta prima nel tasto Browser
        return assistants.firstOrNull { it.id == context.prefs().getString(PREF_AI, null) }
    }

    fun installed(context: Context, pkg: String?) =
        pkg != null && runCatching { context.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    private fun address(site: Target, q: String) =
        if (q.isEmpty()) site.home ?: site.url!!.substringBefore('?') else site.url!!.format(Uri.encode(q))

    /** Tasto Browser: [query] nel motore scelto, nel riquadro; vuota apre la pagina del motore. */
    fun search(context: Context, hub: String, query: String) {
        val q = query.trim()
        val engine = engine(context)
        val browser = browser(context)
        if (popup(context, hub, address(engine, q), engine.label, ai = false, pkg = browser?.pkg, query = q)) return
        val intents = buildList {
            if (q.isNotEmpty()) add(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q).apply { browser?.pkg?.let(::setPackage) })
            add(Intent(Intent.ACTION_VIEW, Uri.parse(address(engine, q))).apply { browser?.pkg?.let(::setPackage) })
            add(Intent(Intent.ACTION_VIEW, Uri.parse(address(engine, q))))
        }
        start(context, intents)
    }

    /** Tasto AI: la domanda all'assistente collegato, nel riquadro. */
    fun ask(context: Context, hub: String, assistant: Target, query: String) {
        val q = query.trim()
        if (popup(context, hub, address(assistant, q), assistant.label, ai = true, pkg = assistant.pkg, query = q)) return
        start(context, buildList {
            if (installed(context, assistant.pkg)) {
                if (q.isNotEmpty()) add(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, q).setPackage(assistant.pkg))
                context.packageManager.getLaunchIntentForPackage(assistant.pkg!!)?.let(::add)
            }
            add(Intent(Intent.ACTION_VIEW, Uri.parse(address(assistant, q))))
        })
    }

    /** Il riquadro dell'app Eurise; falso se non è installata (o è troppo vecchia per averlo). */
    private fun popup(context: Context, hub: String, url: String, title: String, ai: Boolean, pkg: String?, query: String): Boolean {
        val intent = Intent().setComponent(ComponentName(hub, "eu.eurise.hub.web.WebPopupActivity"))
            .putExtra("eu.eurise.extra.URL", url)
            .putExtra("eu.eurise.extra.TITLE", title)
            .putExtra("eu.eurise.extra.QUERY", query)
            .apply { if (ai) putExtra("eu.eurise.extra.KIND", "ai") }
            .apply { if (pkg != null && installed(context, pkg)) putExtra("eu.eurise.extra.PACKAGE", pkg) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    private fun start(context: Context, intents: List<Intent>) {
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
    }
}

/** Una fila di pillole che scorre di lato; [on] è l'id scelto. */
private fun pills(bar: EuriseBar, targets: List<WebSearch.Target>, on: String?, label: (WebSearch.Target) -> String = { it.label }, pick: (WebSearch.Target) -> Unit) =
    android.widget.HorizontalScrollView(bar.context).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            targets.forEach { t ->
                addView(context.pill(label(t), bar.p, on = t.id == on) { pick(t) }.lp(LinearLayout.LayoutParams.WRAP_CONTENT, context.dp(40)).apply {
                    (layoutParams as LinearLayout.LayoutParams).marginEnd = context.dp(6)
                })
            }
        })
    }

/** Pressione lunga su Browser: il motore del riquadro e il browser per "Apri nel browser". */
class BrowserPanel(bar: EuriseBar) : Panel(bar, R.string.eurise_browser) {
    override fun view(): View {
        val prefs = context.prefs()
        val default = WebSearch.Target("pkg:", context.getString(R.string.eurise_browser_default))
        return column(
            header(context.getString(R.string.eurise_browser_choose)),
            context.label(context.getString(R.string.eurise_browser_engine), p, 12f, muted = true),
            pills(bar, WebSearch.engines, WebSearch.engine(context).id) {
                prefs.edit().putString(WebSearch.PREF_ENGINE, it.id).apply()
                bar.searchWeb()
            },
            context.label(context.getString(R.string.eurise_browser_browsers), p, 12f, muted = true),
            pills(bar, listOf(default) + WebSearch.browsers(context), WebSearch.browser(context)?.id ?: default.id) {
                prefs.edit().apply { if (it.pkg == null) remove(WebSearch.PREF) else putString(WebSearch.PREF, it.id) }.apply()
                bar.open(BrowserPanel(bar))
            },
            privacy(R.string.eurise_browser_note),
        )
    }
}

/** Tasto AI senza collegamento, o pressione lunga: si collega un assistente (la sua web app; la sua app, se c'è). */
class AiPanel(bar: EuriseBar) : Panel(bar, R.string.eurise_ai) {
    override fun view(): View {
        val prefs = context.prefs()
        val current = WebSearch.assistant(context)
        val app = context.getString(R.string.eurise_ai_app)
        val views = mutableListOf<View>(
            header(context.getString(R.string.eurise_ai_choose)),
            context.label(context.getString(R.string.eurise_ai_hint), p, 13f),
            pills(bar, WebSearch.assistants, current?.id, label = { if (WebSearch.installed(context, it.pkg)) "${it.label} · $app" else it.label }) {
                prefs.edit().putString(WebSearch.PREF_AI, it.id).apply()
                bar.askAi()
            },
        )
        if (current != null) views += context.pill(context.getString(R.string.eurise_ai_remove), p) {
            prefs.edit().remove(WebSearch.PREF_AI).apply()
            bar.open(AiPanel(bar))
        }.lp(LinearLayout.LayoutParams.WRAP_CONTENT, context.dp(40))
        views += privacy(R.string.eurise_ai_note)
        return column(*views.toTypedArray())
    }
}

package eu.eurise.keyboard

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.View
import android.widget.LinearLayout
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.prefs

/**
 * Il comando Browser della barra: cerca sul web ciò che si è scritto (o selezionato), con il browser o l'assistente
 * scelto una volta con la pressione lunga. La tastiera non va su Internet: apre il browser, che fa la ricerca.
 */
internal object WebSearch {
    const val PREF = "eurise_web_target"

    /** Un posto dove cercare: un browser installato (pacchetto) oppure un assistente sul web (indirizzo con %s). */
    data class Target(val id: String, val label: String, val pkg: String? = null, val url: String? = null)

    /** Assistenti sul web che accettano la domanda nell'indirizzo; si aprono nel browser predefinito. */
    val assistants = listOf(
        Target("ai:lechat", "Le Chat (Mistral)", url = "https://chat.mistral.ai/chat?q=%s"),
        Target("ai:claude", "Claude", url = "https://claude.ai/new?q=%s"),
        Target("ai:chatgpt", "ChatGPT", url = "https://chatgpt.com/?q=%s"),
        Target("ai:perplexity", "Perplexity", url = "https://www.perplexity.ai/search?q=%s"),
        Target("ai:duck", "Duck.ai", url = "https://duck.ai/?q=%s"),
    )

    fun browsers(context: Context): List<Target> {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://eurise.eu")).addCategory(Intent.CATEGORY_BROWSABLE)
        return context.packageManager.queryIntentActivities(probe, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName to it.loadLabel(context.packageManager).toString() }
            .distinctBy { it.first }
            .map { (pkg, label) -> Target("pkg:$pkg", label, pkg = pkg) }
            .sortedBy { it.label.lowercase() }
    }

    fun chosen(context: Context): Target? {
        val id = context.prefs().getString(PREF, null) ?: return null
        return assistants.firstOrNull { it.id == id } ?: id.removePrefix("pkg:").takeIf { id.startsWith("pkg:") }?.let { Target(id, it, pkg = it) }
    }

    /** Apre la ricerca: [query] vuota apre solo il browser. */
    fun run(context: Context, target: Target?, query: String) {
        val q = query.trim()
        val intents = buildList {
            when {
                target?.url != null -> add(Intent(Intent.ACTION_VIEW, Uri.parse(target.url.format(Uri.encode(q)))))
                q.isEmpty() -> add(Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/")).apply { target?.pkg?.let(::setPackage) })
                else -> {
                    // Prima la ricerca del browser col suo motore; se non la gestisce, DuckDuckGo nello stesso browser.
                    add(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q).apply { target?.pkg?.let(::setPackage) })
                    add(Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(q))).apply { target?.pkg?.let(::setPackage) })
                }
            }
        }
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent) }.isSuccess) return
        }
    }
}

/** Pressione lunga su Browser: si sceglie dove cercare; la scelta resta per le volte dopo. */
class BrowserPanel(bar: EuriseBar) : Panel(bar, R.string.eurise_browser) {
    override fun view(): View {
        val current = context.prefs().getString(WebSearch.PREF, null)
        fun row(targets: List<WebSearch.Target>) = android.widget.HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                targets.forEach { t ->
                    addView(context.pill(t.label, p, on = t.id == current) {
                        context.prefs().edit().putString(WebSearch.PREF, t.id).apply()
                        bar.searchWeb(t)
                    }.lp(LinearLayout.LayoutParams.WRAP_CONTENT, context.dp(40)).apply {
                        (layoutParams as LinearLayout.LayoutParams).marginEnd = context.dp(6)
                    })
                }
            })
        }
        return column(
            header(context.getString(R.string.eurise_browser_choose)),
            context.label(context.getString(R.string.eurise_browser_browsers), p, 12f, muted = true),
            row(WebSearch.browsers(context)),
            context.label(context.getString(R.string.eurise_browser_ai), p, 12f, muted = true),
            row(WebSearch.assistants),
            context.label(context.getString(R.string.eurise_browser_note), p, 12f, muted = true),
        )
    }
}

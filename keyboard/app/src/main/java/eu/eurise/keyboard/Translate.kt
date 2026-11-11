package eu.eurise.keyboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Traduzione con Eurise Leggi, che ha i modelli (la tastiera non ha Internet e non li può scaricare): il testo va al
 * servizio di Leggi sul telefono, solo tra app con la stessa firma, e non esce. Le costanti sono quelle di
 * `core/suite/TranslateContract` nell'app principale (la tastiera è un progetto Gradle a sé): vanno tenute uguali.
 */
class Translator(context: Context, private val bridge: Bridge) {
    private val app = context.applicationContext
    val readPackage = if (bridge.hubPackage.endsWith(".debug")) "eu.eurise.read.debug" else "eu.eurise.read"

    sealed interface Result {
        data class Done(val text: String) : Result
        data class Languages(val list: List<String>) : Result
        data object Missing : Result
        data object NoApp : Result
        data object Failed : Result
    }

    fun installed() = bridge.installed(readPackage)

    fun openLanguages() = runCatching {
        app.startActivity(Intent(ACTION_LANGUAGES).setPackage(readPackage).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    /** Chiede a Leggi ([what] = [MSG_TRANSLATE] o [MSG_LANGUAGES]) e risponde sul thread principale. */
    fun ask(what: Int, text: String = "", from: String = "", to: String = "", done: (Result) -> Unit) {
        if (!installed()) return done(Result.NoApp)
        var connection: ServiceConnection? = null
        var finished = false
        val main = Handler(Looper.getMainLooper())
        fun finish(r: Result) {
            if (finished) return
            finished = true
            connection?.let { runCatching { app.unbindService(it) } }
            done(r)
        }
        val reply = Messenger(Handler(Looper.getMainLooper()) { msg ->
            val d = msg.data
            finish(
                when {
                    d.getString(KEY_ERROR) == ERROR_NO_MODEL -> Result.Missing
                    d.getStringArrayList(KEY_LANGUAGES) != null -> Result.Languages(d.getStringArrayList(KEY_LANGUAGES).orEmpty())
                    d.getString(KEY_RESULT) != null -> Result.Done(d.getString(KEY_RESULT).orEmpty())
                    else -> Result.Failed
                },
            )
            true
        })
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder) {
                val msg = Message.obtain(null, what).apply {
                    data = Bundle().apply { putString(KEY_TEXT, text); putString(KEY_FROM, from); putString(KEY_TO, to) }
                    replyTo = reply
                }
                runCatching { Messenger(service).send(msg) }.onFailure { finish(Result.Failed) }
            }
            override fun onServiceDisconnected(name: ComponentName?) = finish(Result.Failed)
        }
        connection = conn
        val ok = runCatching { app.bindService(Intent(ACTION_TRANSLATE).setPackage(readPackage), conn, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!ok) finish(Result.NoApp)
        main.postDelayed({ finish(Result.Failed) }, 60_000)
    }

    companion object {
        const val ACTION_TRANSLATE = "eu.eurise.action.TRANSLATE"
        const val ACTION_LANGUAGES = "eu.eurise.action.TRANSLATE_LANGUAGES"
        const val MSG_TRANSLATE = 1
        const val MSG_LANGUAGES = 2
        const val KEY_TEXT = "text"
        const val KEY_FROM = "from"
        const val KEY_TO = "to"
        const val KEY_RESULT = "result"
        const val KEY_LANGUAGES = "languages"
        const val KEY_ERROR = "error"
        const val ERROR_NO_MODEL = "no_model"
    }
}

/**
 * Pannello Traduci: prende il testo selezionato (o quello scritto prima del cursore, fino all'inizio del paragrafo),
 * lo traduce sul telefono con Eurise Leggi e lo mette al suo posto o lo aggiunge sotto. Lingue: quelle scaricate in Leggi.
 */
class TranslatePanel(bar: EuriseBar) : Panel(bar, R.string.eurise_translate) {
    private val translator = Translator(context, bar.bridge)
    private var languages = listOf("it", "en")
    private var from = "it"
    private var to = "en"
    private var result: String? = null
    private lateinit var fromView: TextView
    private lateinit var toView: TextView
    private lateinit var sourceView: TextView
    private lateinit var resultView: TextView
    private lateinit var actions: LinearLayout

    /** Il testo da tradurre e quanti caratteri prima del cursore sostituire (0 se è una selezione). */
    private fun source(): Pair<String, Int> {
        val selected = bar.ime.currentInputConnection?.getSelectedText(0)?.toString().orEmpty()
        if (selected.isNotBlank()) return selected to 0
        val before = bar.before(1500)
        val paragraph = before.substringAfterLast("\n\n")
        return paragraph to paragraph.length
    }

    private fun name(code: String) = Locale(code).getDisplayLanguage(Locale.ITALIAN).replaceFirstChar { it.uppercase() }

    private fun refresh() {
        fromView.text = name(from)
        toView.text = name(to)
        val (text, _) = source()
        sourceView.text = text.ifBlank { context.getString(R.string.eurise_translate_empty) }
        resultView.text = result ?: ""
        actions.visibility = if (result != null) View.VISIBLE else View.GONE
    }

    private fun run() {
        val (text, _) = source()
        if (text.isBlank()) return
        resultView.text = context.getString(R.string.eurise_translate_working)
        translator.ask(Translator.MSG_TRANSLATE, text, from, to) { r ->
            result = null
            when (r) {
                is Translator.Result.Done -> result = r.text
                Translator.Result.Missing -> { translator.openLanguages(); resultView.text = context.getString(R.string.eurise_translate_missing); return@ask }
                Translator.Result.NoApp -> { resultView.text = context.getString(R.string.eurise_translate_no_app); return@ask }
                else -> { resultView.text = context.getString(R.string.eurise_translate_failed); return@ask }
            }
            refresh()
        }
    }

    private fun chip(onClick: () -> Unit) = context.label("", p, 13.5f, bold = true).apply {
        gravity = Gravity.CENTER
        background = rounded(p.surface, context.dp(12).toFloat(), p.faint)
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun button(text: Int, onClick: () -> Unit) = context.label(context.getString(text), p, 13.5f, bold = true).apply {
        gravity = Gravity.CENTER
        setTextColor(p.onAccent)
        background = rounded(p.accent, context.dp(12).toFloat(), p.faint)
        setPadding(context.dp(12), context.dp(9), context.dp(12), context.dp(9))
        isClickable = true
        setOnClickListener { onClick() }
    }

    override fun view(): View {
        fromView = chip { from = next(from, skip = to); result = null; refresh() }
        toView = chip { to = next(to, skip = from); result = null; refresh() }
        val swap = chip { val f = from; from = to; to = f; result = null; refresh() }.apply { text = "⇄"; contentDescription = context.getString(R.string.eurise_translate_swap) }
        val langs = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(fromView.lp(0, weight = 1f))
            addView(swap.lp().apply { (layoutParams as LinearLayout.LayoutParams).marginStart = context.dp(6); (layoutParams as LinearLayout.LayoutParams).marginEnd = context.dp(6) })
            addView(toView.lp(0, weight = 1f))
        }
        sourceView = context.label("", p, 13f, muted = true).apply { maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.START }
        resultView = context.label("", p, 15f, bold = true).apply { maxLines = 5; typeface = Typeface.DEFAULT_BOLD }
        val go = button(R.string.eurise_translate_go) { run() }
        actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button(R.string.eurise_translate_replace) {
                val r = result ?: return@button
                val (_, length) = source()
                bar.ime.euriseReplace(length, r)
                bar.close()
            }.lp(0, weight = 1f))
            addView(button(R.string.eurise_translate_add) {
                val r = result ?: return@button
                bar.insert("\n" + r)
                bar.close()
            }.lp(0, weight = 1f).apply { (layoutParams as LinearLayout.LayoutParams).marginStart = context.dp(6) })
        }
        val root = column(header(context.getString(R.string.eurise_translate)), langs, sourceView, go, resultView, actions)
        translator.ask(Translator.MSG_LANGUAGES) { r ->
            if (r is Translator.Result.Languages && r.list.size >= 2) {
                languages = r.list
                if (from !in languages) from = languages.first()
                if (to !in languages || to == from) to = languages.first { it != from }
                refresh()
            }
        }
        refresh()
        return root
    }

    private fun next(current: String, skip: String): String {
        val list = languages.filter { it != skip }.ifEmpty { return current }
        return list[(list.indexOf(current) + 1).mod(list.size)]
    }
}

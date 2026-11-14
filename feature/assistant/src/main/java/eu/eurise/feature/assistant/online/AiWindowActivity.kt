package eu.eurise.feature.assistant.online

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.feature.assistant.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * La finestra AI (tavola AlfFinestra, approvata il 14 novembre): la web app del servizio scelto, col proprio account,
 * dentro Eurise. La domanda, già ripulita da Alfred, entra da sola nel campo; Invio resta a chi usa il telefono.
 * «Porta ad Alfred» legge l'ultima risposta (o il testo selezionato) e la riporta nel pannello.
 *
 * Cosa fa lo script, e nient'altro: scrive nel campo della domanda e legge l'ultima risposta. La finestra resta sul
 * dominio del servizio (gli altri indirizzi si aprono nel browser), blocca i tracciatori noti, non condivide nulla
 * con Eurise oltre al testo riportato. Se il campo non si trova la domanda va negli appunti, con l'avviso.
 * L'accesso con Google non funziona dentro le app (lo blocca Google): si dice, con «Apri nel browser».
 */
class AiWindowActivity : ComponentActivity() {
    private var web: WebView? = null
    private var notice by mutableStateOf(Notice.Filling)
    private var progress by mutableIntStateOf(0)
    private var question = ""
    private var home = ""
    private var tries = 0

    enum class Notice { Filling, Ready, Copied, Google, SelectAnswer, None }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL) ?: return finish()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val score = intent.getIntExtra(EXTRA_SCORE, 5)
        question = intent.getStringExtra(EXTRA_QUESTION).orEmpty()
        home = AiService.registrable(Uri.parse(url).host.orEmpty())
        val prefilled = intent.getBooleanExtra(EXTRA_PREFILLED, false)
        if (question.isEmpty()) notice = Notice.None
        else if (prefilled) notice = Notice.Ready

        setContent {
            EuriseTheme {
                BackHandler { if (web?.canGoBack() == true) web?.goBack() else finish() }
                Column(Modifier.fillMaxSize().background(Color(0xFFF4F4F1))) {
                    Row(
                        Modifier.fillMaxWidth().background(Navy).statusBarsPadding().padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val back = stringResource(R.string.alfred_on_back)
                        Box(Modifier.size(44.dp).clip(CircleShape).clickable { finish() }.semantics { contentDescription = back },
                            contentAlignment = Alignment.Center) {
                            Icon(EuriseIcons.chevronLeft(2.2f), null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                DarkMeter(score)
                                Text(stringResource(R.string.alfred_on_window_sub, home), fontSize = 11.sp, color = Color(0xFFCBD5E1),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Row(
                            Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFE9C77B)).clickable { bringBack() }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(EuriseIcons.bowtie(1.9f), null, tint = Navy, modifier = Modifier.size(16.dp))
                            Text(stringResource(R.string.alfred_on_bring), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Navy)
                        }
                    }
                    if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = Color(0xFFE9C77B), trackColor = Color.Transparent)
                    AndroidView(
                        factory = { ctx -> makeWebView(ctx).also { w -> web = w; w.loadUrl(url) } },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    NoticeBar(notice, onBrowser = { openOutside(web?.url ?: url) })
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun makeWebView(ctx: Context) = WebView(ctx).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportMultipleWindows(false)
        // Alcuni siti rifiutano le finestre dentro le app solo per la sigla «wv»: la togliamo, il resto è invariato.
        settings.userAgentString = settings.userAgentString.replace("; wv", "")
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { this@AiWindowActivity.progress = newProgress }
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host.orEmpty()
                if (host.endsWith("accounts.google.com")) { notice = Notice.Google; return true }
                if (AiService.registrable(host) == home) return false
                openOutside(request.url.toString())
                return true
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val host = request.url.host.orEmpty()
                return if (TRACKERS.any { host == it || host.endsWith(".$it") })
                    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))) else null
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (question.isNotEmpty() && notice == Notice.Filling) fill(view)
            }
        }
    }

    /** Scrive la domanda nel campo; le web app disegnano il campo dopo il caricamento, quindi si riprova per qualche secondo. */
    private fun fill(view: WebView) {
        view.evaluateJavascript(FILL.replace("QUESTION", JSONObject.quote(question))) { r ->
            when {
                r?.contains("ok") == true -> notice = Notice.Ready
                ++tries < 14 -> view.postDelayed({ if (notice == Notice.Filling) fill(view) }, 700)
                else -> copyFallback()
            }
        }
    }

    private fun copyFallback() {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Alfred", question))
        notice = Notice.Copied
    }

    /** Porta ad Alfred: il testo selezionato o l'ultima risposta. Se non si trova, chiede di selezionarla col dito. */
    private fun bringBack() {
        val w = web ?: return
        w.evaluateJavascript(READ) { raw ->
            val text = runCatching { JSONArray("[$raw]").getString(0) }.getOrDefault("").trim()
            if (text.length < 20 || text == question.trim()) { notice = Notice.SelectAnswer; return@evaluateJavascript }
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_ANSWER, text).putExtra(EXTRA_NAME, intent.getStringExtra(EXTRA_NAME)))
            finish()
        }
    }

    private fun openOutside(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        web?.destroy()
        web = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "eu.eurise.alfred.online.URL"
        const val EXTRA_NAME = "eu.eurise.alfred.online.NAME"
        const val EXTRA_SCORE = "eu.eurise.alfred.online.SCORE"
        const val EXTRA_QUESTION = "eu.eurise.alfred.online.QUESTION"
        const val EXTRA_PREFILLED = "eu.eurise.alfred.online.PREFILLED"
        const val EXTRA_ANSWER = "eu.eurise.alfred.online.ANSWER"

        private val Navy = Color(0xFF1F2A44)

        /** Tracciatori noti: richieste bloccate, il resto della pagina funziona come sul sito. */
        private val TRACKERS = listOf(
            "google-analytics.com", "googletagmanager.com", "doubleclick.net", "googlesyndication.com", "facebook.net",
            "connect.facebook.net", "hotjar.com", "segment.io", "segment.com", "mixpanel.com", "amplitude.com", "clarity.ms",
            "fullstory.com", "intercomcdn.com", "posthog.com", "heap.io", "datadoghq-browser-agent.com",
        )

        /** Il campo della domanda: l'ultimo visibile fra textarea, campo di testo e area modificabile. */
        private const val FILL = """(function(q){
function vis(e){var r=e.getBoundingClientRect();var s=getComputedStyle(e);return r.width>40&&r.height>10&&s.visibility!='hidden'&&s.display!='none';}
var els=[].slice.call(document.querySelectorAll('textarea,[contenteditable="true"],div[role="textbox"],input[type="text"],input:not([type])')).filter(vis);
if(!els.length)return 'none';
var e=els[els.length-1];e.focus();
if(e.tagName==='TEXTAREA'||e.tagName==='INPUT'){if(e.value===q)return 'ok';var p=e.tagName==='TEXTAREA'?HTMLTextAreaElement.prototype:HTMLInputElement.prototype;Object.getOwnPropertyDescriptor(p,'value').set.call(e,q);e.dispatchEvent(new Event('input',{bubbles:true}));}
else{if(e.innerText.trim()===q)return 'ok';document.execCommand('selectAll',false,null);document.execCommand('insertText',false,q);}
return 'ok';})(QUESTION)"""

        /** L'ultima risposta: prima il testo selezionato, poi l'ultimo blocco di messaggio più interno. */
        private const val READ = """(function(){
var s=window.getSelection?String(window.getSelection()):'';if(s&&s.trim().length>20)return s;
var sel='[data-message-author-role="assistant"],[data-role="assistant"],[class*="assistant"],.markdown,.prose,[class*="answer"],[class*="response"],[class*="message"],article';
var c=[].slice.call(document.querySelectorAll(sel)).filter(function(e){return e.innerText&&e.innerText.trim().length>40;});
if(!c.length)return '';
var leaf=c.filter(function(e){return !c.some(function(o){return o!==e&&e.contains(o);});});
var l=leaf.length?leaf:c;return l[l.length-1].innerText;})()"""
    }
}

@androidx.compose.runtime.Composable
private fun NoticeBar(notice: AiWindowActivity.Notice, onBrowser: () -> Unit) {
    if (notice == AiWindowActivity.Notice.None) return
    val (text, color) = when (notice) {
        AiWindowActivity.Notice.Filling -> stringResource(R.string.alfred_on_filling) to Color(0xFF56564F)
        AiWindowActivity.Notice.Ready -> stringResource(R.string.alfred_on_ready) to Color(0xFF3F6E52)
        AiWindowActivity.Notice.Copied -> stringResource(R.string.alfred_on_copied) to Color(0xFF8A6A1F)
        AiWindowActivity.Notice.Google -> stringResource(R.string.alfred_on_google) to Color(0xFF8C2F39)
        AiWindowActivity.Notice.SelectAnswer -> stringResource(R.string.alfred_on_select) to Color(0xFF8A6A1F)
        AiWindowActivity.Notice.None -> "" to Color.Unspecified
    }
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFF4F4F1)).navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(EuriseIcons.shield(2.4f), null, tint = color, modifier = Modifier.size(14.dp))
            Text(text, fontSize = 12.5.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, color = color)
        }
        if (notice == AiWindowActivity.Notice.Google) {
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF1F2A44)).clickable(onClick = onBrowser)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.alfred_on_open_browser), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        }
    }
}

/** Il semaforo a dieci tacchette su fondo scuro (barra della finestra, pannello di Alfred). */
@androidx.compose.runtime.Composable
internal fun DarkMeter(score: Int) {
    val s = score.coerceIn(1, 10)
    val c = when { s >= 8 -> Color(0xFF7FC39A); s >= 5 -> Color(0xFFE2B95C); else -> Color(0xFFE88A93) }
    Row(horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
        repeat(10) { i ->
            Box(Modifier.size(width = 3.dp, height = 10.dp).clip(RoundedCornerShape(1.5.dp))
                .background(if (i < s) c else Color(0x2EFFFFFF)))
        }
    }
}

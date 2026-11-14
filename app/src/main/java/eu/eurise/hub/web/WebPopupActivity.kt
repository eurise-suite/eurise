package eu.eurise.hub.web

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.hub.R

/**
 * Il riquadro di ricerca della Tastiera (tasti Browser e AI): si apre sopra l'app in cui si sta scrivendo, che resta
 * visibile dietro, e mostra il sito scelto dall'utente (motore di ricerca o assistente). La Tastiera non ha accesso a
 * Internet: chiede a Eurise di aprire questo riquadro, solo tra app firmate con la stessa chiave.
 *
 * "Apri nell'app" passa la ricerca al browser o all'app dell'assistente, per andare più a fondo.
 */
class WebPopupActivity : ComponentActivity() {
    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)?.takeIf { it.startsWith("https://") } ?: return finish()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val ai = intent.getStringExtra(EXTRA_KIND) == KIND_AI
        setContent {
            EuriseTheme {
                Popup(
                    url = url,
                    title = title,
                    ai = ai,
                    onWeb = { web = it },
                    onOpen = { openOutside(ai) },
                    onClose = ::finish,
                )
            }
        }
    }

    override fun onDestroy() {
        web?.run { stopLoading(); destroy() }
        web = null
        CookieManager.getInstance().flush()
        super.onDestroy()
    }

    /** Passa la ricerca fuori: all'app dell'assistente (se installata) o al browser, che riprende dalla pagina attuale. */
    private fun openOutside(ai: Boolean) {
        val here = web?.url?.takeIf { it.startsWith("http") } ?: intent.getStringExtra(EXTRA_URL).orEmpty()
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        val query = intent.getStringExtra(EXTRA_QUERY).orEmpty()
        val tries = buildList {
            if (pkg != null) {
                if (ai) {
                    if (query.isNotBlank()) add(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, query).setPackage(pkg))
                    packageManager.getLaunchIntentForPackage(pkg)?.let(::add)
                } else add(Intent(Intent.ACTION_VIEW, Uri.parse(here)).setPackage(pkg))
            }
            add(Intent(Intent.ACTION_VIEW, Uri.parse(here)))
        }
        for (i in tries) {
            if (runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) break
        }
        finish()
    }

    companion object {
        const val EXTRA_URL = "eu.eurise.extra.URL"
        const val EXTRA_TITLE = "eu.eurise.extra.TITLE"
        const val EXTRA_KIND = "eu.eurise.extra.KIND"
        const val EXTRA_PACKAGE = "eu.eurise.extra.PACKAGE"
        const val EXTRA_QUERY = "eu.eurise.extra.QUERY"
        const val KIND_AI = "ai"
    }
}

@Composable
private fun Popup(url: String, title: String, ai: Boolean, onWeb: (WebView) -> Unit, onOpen: () -> Unit, onClose: () -> Unit) {
    var host by remember { mutableStateOf(Uri.parse(url).host.orEmpty().removePrefix("www.")) }
    var progress by remember { mutableIntStateOf(0) }
    var canBack by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<WebView?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler { if (view?.canGoBack() == true) view?.goBack() else onClose() }
    val none = remember { MutableInteractionSource() }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable(none, null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .statusBarsPadding()
                .padding(top = 56.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                // Sfondo con la forma, senza clip: una WebView dentro un ritaglio in una finestra trasparente su
                // alcuni telefoni resta bianca (segnalato dal proprietario, 11 novembre).
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .clickable(none, null) {}
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)))
            }
            Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { view?.goBack() }, enabled = canBack) {
                    Icon(EuriseIcons.chevronLeft(), stringResource(R.string.web_back), Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 2.dp)) {
                    Text(title.ifBlank { host }, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(EuriseIcons.lock(), null, Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.web_only_query, host), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                TextButton(onClick = onOpen) {
                    Icon(EuriseIcons.external(), null, Modifier.size(16.dp))
                    Text(stringResource(if (ai) R.string.web_open_app else R.string.web_open_browser), Modifier.padding(start = 6.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                IconButton(onClick = onClose) { Icon(EuriseIcons.close(), stringResource(R.string.web_close), Modifier.size(18.dp)) }
            }
            if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, Modifier.fillMaxWidth().height(2.dp))
            else Box(Modifier.height(2.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                WebPane(url, Modifier.fillMaxSize(), onCreated = { view = it; onWeb(it) }, onPage = { page ->
                    host = Uri.parse(page).host.orEmpty().removePrefix("www.")
                    canBack = view?.canGoBack() == true
                    error = null
                }, onProgress = { progress = it }, onError = { error = it })
                // Se la pagina non si apre si dice perché, e la ricerca va nel browser già scritta.
                error?.let { why ->
                    Column(
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.web_error, why), fontSize = 15.sp)
                        TextButton(onClick = onOpen) {
                            Icon(EuriseIcons.external(), null, Modifier.size(16.dp))
                            Text(stringResource(if (ai) R.string.web_open_app else R.string.web_open_browser), Modifier.padding(start = 6.dp), fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { error = null; view?.reload() }) { Text(stringResource(R.string.web_retry), fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebPane(url: String, modifier: Modifier, onCreated: (WebView) -> Unit, onPage: (String) -> Unit, onProgress: (Int) -> Unit, onError: (String) -> Unit) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.WHITE)
                settings.javaScriptEnabled = true // i motori e gli assistenti non funzionano senza
                settings.domStorageEnabled = true
                settings.setSupportMultipleWindows(false)
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                // Alcuni servizi (Duck.ai) riconoscono la WebView da «; wv» e mostrano una pagina bianca: si presenta come
                // il Chrome del telefono, senza dire altro di sé.
                settings.userAgentString = settings.userAgentString.replace("; wv", "").replace(Regex("""Version/\d+(\.\d+)* """), "")
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val u = request.url
                        if (u.scheme == "https" || u.scheme == "http") return false
                        // intent:, mailto:, tel: e simili: alle app del telefono, mai dentro il riquadro.
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        return true
                    }

                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = onPage(url)

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) onError(error.description?.toString().orEmpty().ifBlank { "errore ${error.errorCode}" })
                    }

                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                        if (request.isForMainFrame && response.statusCode >= 400) onError("HTTP ${response.statusCode}")
                    }
                    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) = onPage(url)
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)
                }
                onCreated(this)
                loadUrl(url)
            }
        },
    )
}

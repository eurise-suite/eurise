package eu.eurise.browser

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import eu.eurise.core.designsystem.theme.EuriseTheme
import org.mozilla.geckoview.GeckoView

/**
 * Una webapp vera («In home» dal Browser): il sito in una finestra sua, a schermo intero, senza barra degli indirizzi,
 * con un posto suo tra le app recenti (nome e iniziale del sito). Cookie e accessi sono quelli del Browser (stesso
 * motore), gli accessi vengono da Eurise Password come nel Browser. I link che aprono una finestra nuova vanno nel
 * Browser.
 */
class WebAppActivity : ComponentActivity() {
    private lateinit var tab: Tab

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val url = intent?.dataString ?: return finish()
        val name = intent.getStringExtra(EXTRA_NAME) ?: Links.host(url)
        Logins.init(this, if (BuildConfig.DEBUG) "eu.eurise.password.debug" else "eu.eurise.password")
        setTaskDescription(ActivityManager.TaskDescription(name, letterIcon(this, name, 96)))
        tab = Tab(this, System.currentTimeMillis(), private = false, onNewTab = { link ->
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).setClass(this, BrowserActivity::class.java)) }
        })
        tab.load(url)
        setContent {
            EuriseTheme {
                BackHandler { if (tab.canBack) tab.session.goBack() else finish() }
                Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.ime)) {
                    Box(Modifier.weight(1f).fillMaxWidth().then(if (tab.fullscreen) Modifier else Modifier.statusBarsPadding())) {
                        AndroidView(factory = { ctx -> GeckoView(ctx).also { it.setSession(tab.session) } }, modifier = Modifier.fillMaxSize())
                        if (tab.progress in 1..99) {
                            LinearProgressIndicator(progress = { tab.progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp).align(Alignment.TopCenter))
                        }
                    }
                    if (!tab.fullscreen) {
                        LoginBubbles(tab, onUnlock = ::unlock)
                        Box(Modifier.navigationBarsPadding())
                    }
                }
            }
        }
    }

    private val unlockResult = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) { Logins.lockedDomain = null; tab.session.reload() }
    }

    private fun unlock() {
        val pkg = if (BuildConfig.DEBUG) "eu.eurise.password.debug" else "eu.eurise.password"
        val intent = Intent(eu.eurise.core.suite.PasswordContract.ACTION_UNLOCK)
            .setComponent(android.content.ComponentName(pkg, eu.eurise.core.suite.PasswordContract.ALIAS))
        runCatching { unlockResult.launch(intent) }
    }

    override fun onDestroy() {
        if (::tab.isInitialized) tab.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_NAME = "name"

        /** L'intent della webapp: una finestra per sito (lo stesso sito riapre la sua). */
        fun intent(context: Context, url: String, name: String): Intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).setClass(context, WebAppActivity::class.java)
                .putExtra(EXTRA_NAME, name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)

        /** L'icona della webapp: l'iniziale del sito su un tondo pieno, nel colore che il sito «prende» dal nome. */
        fun letterIcon(context: Context, name: String, sizeDp: Int, full: Boolean = false): Bitmap {
            val px = (sizeDp * context.resources.displayMetrics.density).toInt()
            val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val tints = intArrayOf(0xFF3E5C8A.toInt(), 0xFF4F6B5A.toInt(), 0xFF85672B.toInt(), 0xFF8A3A5C.toInt(),
                0xFF5B4E8A.toInt(), 0xFF2F6F73.toInt(), 0xFFA0522D.toInt(), 0xFF1F2A44.toInt())
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tints[Math.floorMod(name.lowercase().hashCode(), tints.size)] }
            // Per l'icona adattiva il colore riempie tutto (la forma la decide il launcher), con la lettera nella zona sicura.
            if (full) c.drawRect(0f, 0f, px.toFloat(), px.toFloat(), p) else c.drawCircle(px / 2f, px / 2f, px / 2f, p)
            p.color = 0xFFFFFFFF.toInt()
            p.textSize = px * if (full) 0.34f else 0.46f
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            p.textAlign = Paint.Align.CENTER
            val letter = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•"
            c.drawText(letter, px / 2f, px / 2f - (p.descent() + p.ascent()) / 2f, p)
            return bmp
        }
    }
}

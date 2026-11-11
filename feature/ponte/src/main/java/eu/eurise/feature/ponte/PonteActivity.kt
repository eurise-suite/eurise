package eu.eurise.feature.ponte

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.core.ponte.Kind
import eu.eurise.core.ponte.Pairing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Telefono e computer (tavola Ponte). Tre ingressi: il lettore QR con un codice del Ponte (collega), «Invia al
 * computer» dal menu Condividi (un link si apre sul computer, un testo va negli appunti), la schermata dalla Scrivania.
 */
class PonteActivity : ComponentActivity() {
    private lateinit var store: PonteStore
    private lateinit var client: PonteClient
    private var pairing by mutableStateOf<PairState?>(null)
    private var version by mutableStateOf(0)

    private sealed class PairState {
        data class Working(val name: String) : PairState()
        data class Done(val name: String, val code: String) : PairState()
        data object Failed : PairState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        store = PonteStore(applicationContext)
        client = PonteClient(store)
        if (intent?.action == Intent.ACTION_SEND) {
            shareToComputer(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(), intent.getStringExtra(Intent.EXTRA_SUBJECT))
            return
        }
        if (intent?.action == ACTION_PAIR) pair(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
        // Con la schermata davanti Android lascia partire il collegamento in primo piano.
        PonteLink.start(applicationContext)
        setContent { EuriseTheme { PonteScreen() } }
    }

    private fun pair(code: String) {
        val p = Pairing.parse(code)
        if (p == null) { Toast.makeText(this, R.string.ponte_invalid, Toast.LENGTH_LONG).show(); return }
        pairing = PairState.Working(p.name)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { client.pair(p) }.getOrNull() }
            pairing = if (result == null) PairState.Failed else PairState.Done(result.first.name, result.second)
            version++
            if (result != null) PonteLink.start(applicationContext)
        }
    }

    /** «Invia al computer»: un link diventa «Continua qui», un testo va negli appunti. Si chiude da sola. */
    private fun shareToComputer(text: String, subject: String?) {
        val computer = store.computers().firstOrNull()
        if (computer == null) {
            setContent { EuriseTheme { PonteScreen() } }
            return
        }
        val url = Regex("https?://\\S+").find(text)?.value
        val kind = if (url != null) Kind.Handoff else Kind.Clipboard
        if (!store.isOn(kind)) {
            Toast.makeText(this, R.string.ponte_off, Toast.LENGTH_LONG).show(); finish(); return
        }
        val fields = if (url != null) mapOf("url" to url, "title" to (subject ?: text.substringBefore(url).trim().ifBlank { url })) else mapOf("text" to text)
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { client.send(computer, kind, fields) }
            Toast.makeText(this@PonteActivity, getString(if (ok) R.string.ponte_sent else R.string.ponte_not_sent, computer.name), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Composable
    private fun PonteScreen() {
        val context = LocalContext.current
        val scheme = MaterialTheme.colorScheme
        val scope = rememberCoroutineScope()
        val computers = remember(version) { store.computers() }
        val noSpeech = stringResource(R.string.ponte_no_speech)
        val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return@rememberLauncherForActivityResult
            val c = computers.firstOrNull() ?: return@rememberLauncherForActivityResult
            scope.launch {
                val ok = withContext(Dispatchers.IO) { client.send(c, Kind.Dictation, mapOf("text" to said)) }
                Toast.makeText(context, context.getString(if (ok) R.string.ponte_sent else R.string.ponte_not_sent, c.name), Toast.LENGTH_SHORT).show()
            }
        }
        Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable { finish() }, contentAlignment = Alignment.Center) {
                    Icon(EuriseIcons.chevronLeft(2.2f), null, modifier = Modifier.size(20.dp))
                }
                Column {
                    Text(stringResource(R.string.ponte_title), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(stringResource(R.string.ponte_subtitle), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                pairing?.let { state ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(scheme.primary.copy(alpha = 0.12f)).padding(14.dp)) {
                        when (state) {
                            is PairState.Working -> Text(stringResource(R.string.ponte_pairing, state.name), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            is PairState.Done -> {
                                Text(stringResource(R.string.ponte_paired, state.name), fontSize = 14.sp)
                                Text(state.code.chunked(3).joinToString(" "), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                            }
                            PairState.Failed -> Text(stringResource(R.string.ponte_pair_failed), fontSize = 13.5.sp)
                        }
                    }
                }
                Section(stringResource(R.string.ponte_computers))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(horizontal = 14.dp, vertical = 4.dp)) {
                    if (computers.isEmpty()) Text(stringResource(R.string.ponte_none), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
                    computers.forEachIndexed { i, c ->
                        if (i > 0) HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
                        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(EuriseIcons.work(2f), null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.name, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                                Text(listOfNotNull(c.host, c.port.toString()).joinToString(":"), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                            }
                            Text(stringResource(R.string.ponte_forget), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = scheme.error,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { store.forget(c); version++ }.padding(8.dp))
                        }
                    }
                }
                Receive(enabled = computers.isNotEmpty())
                Section(stringResource(R.string.ponte_flows))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(horizontal = 14.dp, vertical = 4.dp)) {
                    Flow(EuriseIcons.browser(2f), R.string.ponte_flow_handoff, R.string.ponte_flow_handoff_sub, Kind.Handoff)
                    Flow(EuriseIcons.copy(2f), R.string.ponte_flow_clip, R.string.ponte_flow_clip_sub, Kind.Clipboard)
                    Flow(EuriseIcons.mic(2f), R.string.ponte_flow_dict, R.string.ponte_flow_dict_sub, Kind.Dictation)
                    Flow(EuriseIcons.bell(2f), R.string.ponte_flow_notify, R.string.ponte_flow_notify_sub, Kind.Notify)
                }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(scheme.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.ponte_how_home), fontSize = 12.5.sp)
                    Text(stringResource(R.string.ponte_how_away), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(EuriseIcons.scan(2f), stringResource(R.string.ponte_pair), strong = false, modifier = Modifier.weight(1f)) {
                    // Il lettore QR della suite riconosce il codice del Ponte e torna qui con «Collega questo computer».
                    val qr = Intent().setClassName(context.packageName, "eu.eurise.hub.MainActivity").putExtra("eu.eurise.extra.SECTION", "qr")
                    runCatching { startActivity(qr) }
                }
                Button(EuriseIcons.mic(2f), stringResource(R.string.ponte_dictate), strong = true, modifier = Modifier.weight(1f), enabled = computers.isNotEmpty() && store.isOn(Kind.Dictation)) {
                    val speak = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    runCatching { dictate.launch(speak) }.onFailure { Toast.makeText(context, noSpeech, Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    /** «Dal computer al telefono»: acceso solo dopo la spiegazione; spento subito, senza conferme. */
    @Composable
    private fun Receive(enabled: Boolean) {
        val scheme = MaterialTheme.colorScheme
        var on by remember { mutableStateOf(store.receiveOn) }
        var asking by remember { mutableStateOf(false) }
        val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { PonteLink.start(applicationContext) }
        fun turnOn() {
            store.receiveOn = true
            on = true
            val power = getSystemService(PowerManager::class.java)
            if (power != null && !power.isIgnoringBatteryOptimizations(packageName)) {
                runCatching { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
            }
            if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) else PonteLink.start(applicationContext)
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(EuriseIcons.work(2f), null, tint = scheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ponte_receive), fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                Text(store.lastThing.takeIf { on } ?: stringResource(R.string.ponte_receive_sub), fontSize = 12.sp, color = scheme.onSurfaceVariant)
            }
            Switch(checked = on, enabled = enabled || on, onCheckedChange = { want ->
                if (want) asking = true
                else { store.receiveOn = false; on = false; PonteLink.stop(applicationContext) }
            })
        }
        if (asking) AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.ponte_receive_title)) },
            text = { Text(stringResource(R.string.ponte_receive_explain), fontSize = 14.sp) },
            confirmButton = { TextButton(onClick = { asking = false; turnOn() }) { Text(stringResource(R.string.ponte_receive_on)) } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text(stringResource(R.string.ponte_receive_cancel)) } },
        )
    }

    @Composable
    private fun Section(text: String) =
        Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

    @Composable
    private fun Flow(icon: ImageVector, title: Int, sub: Int, kind: Kind) {
        var on by remember { mutableStateOf(store.isOn(kind)) }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(sub), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = on, onCheckedChange = { on = it; store.setOn(kind, it) })
        }
    }

    @Composable
    private fun Button(icon: ImageVector, label: String, strong: Boolean, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
        val scheme = MaterialTheme.colorScheme
        Row(
            modifier.height(50.dp).clip(RoundedCornerShape(25.dp)).background(if (strong && enabled) scheme.primary else scheme.surface)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = if (strong && enabled) scheme.onPrimary else scheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = if (strong && enabled) scheme.onPrimary else scheme.onSurface)
        }
    }

    companion object {
        const val ACTION_PAIR = "eu.eurise.action.PONTE_PAIR"
    }
}

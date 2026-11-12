package eu.eurise.phone.call

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.eurise.core.contacts.Avatar
import eu.eurise.core.contacts.Contact
import eu.eurise.core.contacts.Contacts
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.core.suite.HubBridge
import eu.eurise.phone.BuildConfig
import eu.eurise.phone.R
import eu.eurise.phone.data.Engagement
import eu.eurise.phone.data.PhoneData
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Schermata della chiamata (anteprime TelChiamata): chi è, da quanto si parla, gli impegni con la persona dal
 * Calendario, la nota della chiamata (che finisce in Note quando la chiamata si chiude) e i comandi. Per le chiamate in
 * arrivo: Rispondi, Rifiuta e Rifiuta con un messaggio.
 */
class CallActivity : ComponentActivity() {
    private var proximity: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        handle(intent)
        setContent { EuriseTheme(darkTheme = true) { CallScreen(onEnd = ::finishSoon, onProximity = ::proximity) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == ACTION_ANSWER) CallManager.primary()?.takeIf { it.state == Call.STATE_RINGING }?.let { CallManager.answer(it.call) }
    }

    /** Spegne lo schermo quando il telefono è all'orecchio (non in vivavoce). */
    private fun proximity(on: Boolean) {
        val pm = getSystemService(PowerManager::class.java)
        if (on && proximity == null && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "eurise:call").apply { acquire(4 * 60 * 60_000L) }
        } else if (!on) {
            proximity?.takeIf { it.isHeld }?.release()
            proximity = null
        }
    }

    private fun finishSoon() {
        proximity(false)
        window.decorView.postDelayed({ if (CallManager.calls.value.isEmpty()) finishAndRemoveTask() }, 1200)
    }

    override fun onDestroy() {
        proximity(false)
        super.onDestroy()
    }

    companion object {
        const val ACTION_ANSWER = "eu.eurise.phone.ANSWER"
    }
}

private val Navy = Color(0xFF1F2A44)
private val Veil = Color(0x1AFFFFFF)
private val Soft = Color(0xFFC9D2E6)
private val Gold = Color(0xFFD2B06A)
private val Red = Color(0xFFB63A2B)
private val Green = Color(0xFF2F7D4F)

@Composable
private fun CallScreen(onEnd: () -> Unit, onProximity: (Boolean) -> Unit) {
    val context = LocalContext.current
    val calls by CallManager.calls.collectAsStateWithLifecycle()
    val audio by CallManager.audio.collectAsStateWithLifecycle()
    val item = CallManager.primary(calls)
    var lastNumber by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf<Contact?>(null) }
    var engagements by remember { mutableStateOf<List<Engagement>>(emptyList()) }
    var note by remember { mutableStateOf("") }
    var keypad by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var replies by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var ended by remember { mutableStateOf(false) }
    val speaker = audio?.route == CallAudioState.ROUTE_SPEAKER
    val muted = audio?.isMuted == true

    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    LaunchedEffect(item?.number) {
        val number = item?.number ?: return@LaunchedEffect
        if (number == lastNumber) return@LaunchedEffect
        lastNumber = number
        val c = Contacts.byNumber(context, number)?.let { Contacts.detail(context, it.id) ?: it }
        contact = c
        engagements = c?.let { PhoneData.engagements(context, it.name, it.emails.map { e -> e.value }) }.orEmpty()
    }
    LaunchedEffect(item?.state, speaker) { onProximity(item?.state == Call.STATE_ACTIVE && !speaker) }
    // Chiamata finita: la nota va in Note (se scritta) e la schermata si chiude.
    LaunchedEffect(calls.isEmpty()) {
        if (calls.isEmpty() && lastNumber.isNotEmpty() && !ended) {
            ended = true
            if (note.isNotBlank()) {
                val who = contact?.name ?: lastNumber
                val time = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.ITALIAN))
                HubBridge.send(context, BuildConfig.HUB_PACKAGE, permission(), HubBridge.ACTION_NOTE,
                    context.getString(R.string.ph_note_title, who, time) + "\n" + note.trim())
            }
            onEnd()
        } else if (calls.isEmpty() && lastNumber.isEmpty()) onEnd()
    }

    val name = contact?.name ?: item?.number?.takeIf { it.isNotEmpty() }?.let(Contacts::format) ?: if (item == null) "" else stringResource(R.string.ph_private_number)
    val status = when (item?.state) {
        null, Call.STATE_DISCONNECTED -> stringResource(R.string.ph_call_ended)
        Call.STATE_RINGING -> stringResource(R.string.ph_incoming)
        Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_NEW -> stringResource(R.string.ph_dialing)
        Call.STATE_HOLDING -> stringResource(R.string.ph_on_hold)
        else -> if (item.connectedAt > 0) duration((now - item.connectedAt) / 1000) else stringResource(R.string.ph_ongoing)
    }
    val other = calls.firstOrNull { it.call != item?.call && it.state != Call.STATE_DISCONNECTED }

    Column(Modifier.fillMaxSize().background(Navy).safeDrawingPadding().imePadding()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(36.dp))
            Avatar(contact?.name ?: name, contact?.photo, 88.dp)
            Spacer(Modifier.height(10.dp))
            Text(name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(status, color = Soft, fontSize = 15.sp)
            if (other != null) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.clip(RoundedCornerShape(16.dp)).background(Veil).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.ph_other_call, other.number.let(Contacts::format)), color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f, fill = false))
                    SmallAction(stringResource(R.string.ph_swap)) { CallManager.swap() }
                    if (item?.state == Call.STATE_ACTIVE || other.state == Call.STATE_HOLDING) SmallAction(stringResource(R.string.ph_merge)) { CallManager.merge() }
                }
            }
            if (engagements.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Veil).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.ph_before_answering).uppercase(Locale.getDefault()), color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
                    engagements.forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(EuriseIcons.calendar(), null, tint = Soft, modifier = Modifier.size(16.dp))
                            Text(e.title + " · " + e.start.format(DateTimeFormatter.ofPattern("EEEE d, HH:mm", Locale.ITALIAN)) + (e.location?.let { " · $it" } ?: ""), color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
            if (item != null && item.state != Call.STATE_RINGING) {
                Spacer(Modifier.height(12.dp))
                // La nota della chiamata ha lo stesso stile del riquadro «Prima di rispondere» (correzione del proprietario):
                // velo sul blu, titolo dorato in maiuscolo, testo bianco.
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Veil).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.ph_call_note).uppercase(Locale.getDefault()), color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
                    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                        if (note.isEmpty()) Text(stringResource(R.string.ph_call_note_hint), color = Soft, fontSize = 14.sp)
                        BasicTextField(
                            note, { note = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp), cursorBrush = SolidColor(Color.White),
                        )
                    }
                }
            }
            if (keypad && item != null) {
                Spacer(Modifier.height(12.dp))
                Text(typed, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Keys { d -> typed += d; CallManager.tone(item.call, d) }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (item != null && item.state == Call.STATE_RINGING) {
            if (replies) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(R.string.ph_reply_later, R.string.ph_reply_meeting, R.string.ph_reply_driving).forEach { r ->
                        val text = stringResource(r)
                        Text(text, color = Color.White, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Veil)
                            .clickable(role = Role.Button) { CallManager.rejectWithMessage(item.call, text) }.padding(14.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
            } else {
                Text(stringResource(R.string.ph_reply_with_message), color = Soft, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { replies = true }.padding(12.dp))
            }
            Row(Modifier.fillMaxWidth().padding(start = 40.dp, end = 40.dp, bottom = 36.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                RoundButton(EuriseIcons.hangup(), stringResource(R.string.ph_decline), Red) { CallManager.reject(item.call) }
                RoundButton(EuriseIcons.phone(), stringResource(R.string.ph_answer), Green) { CallManager.answer(item.call) }
            }
        } else if (item != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Control(EuriseIcons.micOff(), stringResource(R.string.ph_mute), muted, Modifier.weight(1f)) { CallManager.setMuted(!muted) }
                Control(EuriseIcons.dialpad(), stringResource(R.string.ph_keys), keypad, Modifier.weight(1f)) { keypad = !keypad }
                Control(EuriseIcons.volume(), stringResource(R.string.ph_speaker), speaker, Modifier.weight(1f)) { CallManager.setSpeaker(!speaker) }
                Control(EuriseIcons.pause(), stringResource(R.string.ph_hold), item.state == Call.STATE_HOLDING, Modifier.weight(1f)) { CallManager.toggleHold(item.call) }
            }
            Box(Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 36.dp), contentAlignment = Alignment.Center) {
                RoundButton(EuriseIcons.hangup(), stringResource(R.string.ph_hangup), Red) { CallManager.hangup(item.call) }
            }
        } else {
            Spacer(Modifier.height(140.dp))
        }
    }

    DisposableEffect(Unit) { onDispose { onProximity(false) } }
}

private fun permission() = if (BuildConfig.DEBUG) "eu.eurise.permission.SUITE_DEBUG" else "eu.eurise.permission.SUITE"

private fun duration(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    Text(label, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp))
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(color).clickable(role = Role.Button, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Text(label, color = Soft, fontSize = 13.sp)
    }
}

@Composable
private fun Control(icon: ImageVector, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(74.dp).clip(RoundedCornerShape(22.dp)).background(if (on) Color.White else Veil).clickable(role = Role.Switch, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (on) Navy else Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (on) Navy else Color.White, fontSize = 12.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold)
    }
}

/** Tastiera dei toni (durante la chiamata). */
@Composable
internal fun Keys(onKey: (Char) -> Unit) {
    val rows = listOf("123", "456", "789", "*0#")
    Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { d ->
                    Box(Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(18.dp)).background(Veil).clickable(role = Role.Button) { onKey(d) }, contentAlignment = Alignment.Center) {
                        Text(d.toString(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

package eu.eurise.feature.clock.system

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import eu.eurise.core.designsystem.icon.EuriseIcons
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.eurise.feature.clock.R
import eu.eurise.feature.clock.logic.Challenge
import eu.eurise.core.designsystem.theme.EuriseTheme
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Schermo della sveglia che suona: anche sopra il blocco. Rimanda, Spegni (o un piccolo calcolo). */
class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            EuriseTheme(darkTheme = true) {
                val ringing by RingService.state.collectAsStateWithLifecycle()
                LaunchedEffect(ringing) { if (ringing == null) finish() }
                ringing?.let { RingScreen(it, onSnooze = { RingService.snooze(this) }, onDismiss = { RingService.dismiss(this) }, onStopTimer = { id ->
                    sendBroadcast(android.content.Intent(this, ClockReceiver::class.java).setAction(ClockReceiver.TIMER_STOP).putExtra(ClockReceiver.EXTRA_ID, id))
                }, onPlusMinute = { id ->
                    sendBroadcast(android.content.Intent(this, ClockReceiver::class.java).setAction(ClockReceiver.TIMER_PLUS).putExtra(ClockReceiver.EXTRA_ID, id))
                }) }
            }
        }
    }
}

@Composable
private fun RingScreen(r: Ringing, onSnooze: () -> Unit, onDismiss: () -> Unit, onStopTimer: (Int) -> Unit, onPlusMinute: (Int) -> Unit) {
    // Con il piccolo calcolo resta lo schermo classico (Alfred parla e suona lo stesso).
    r.alfred?.takeIf { !r.challenge }?.let { AlfredRingScreen(r, it, onSnooze, onDismiss); return }
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(1_000) } }
    val challenge = remember(r) { if (r.challenge) Challenge.make() else null }
    var answer by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color(0xFF14171C)).safeDrawingPadding().padding(24.dp)) {
        Column(Modifier.align(Alignment.TopCenter).padding(top = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), fontSize = 84.sp, fontWeight = FontWeight.Medium, color = Color(0xFFF1EEE8))
            Text(r.title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFBFC6D4))
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (r.timerId != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BigButton(stringResource(R.string.timer_plus1), false, Modifier.weight(1f)) { onPlusMinute(r.timerId) }
                    BigButton(stringResource(R.string.timer_stop), true, Modifier.weight(1f)) { onStopTimer(r.timerId) }
                }
            } else {
                if (r.snoozesLeft > 0) BigButton(stringResource(R.string.ring_snooze_left, r.snoozesLeft), false, Modifier.fillMaxWidth(), onSnooze)
                if (challenge != null) {
                    Text(stringResource(R.string.ring_challenge, challenge.text), fontSize = 20.sp, color = Color(0xFFF1EEE8), fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { v ->
                            answer = v.filter { it.isDigit() || it == '-' }.take(4)
                            if (answer.toIntOrNull() == challenge.answer) onDismiss()
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    BigButton(stringResource(R.string.ring_dismiss), true, Modifier.fillMaxWidth(), onDismiss)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun BigButton(label: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(64.dp).clip(RoundedCornerShape(32.dp))
            .background(if (filled) Color(0xFF8FB6E8) else Color(0xFF262B33))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (filled) Color(0xFF0E1420) else Color(0xFFF1EEE8))
    }
}

/**
 * La sveglia di Alfred (tavola AlfSveglia, approvata): cielo dell'alba, papillon, ora e data; il saluto con meteo, la
 * massima e il brano con il suo commento; «Ancora N minuti» e «Sono sveglia/o». La scelta della musica sta nella sveglia.
 */
@Composable
private fun AlfredRingScreen(r: Ringing, a: AlfredScreen, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { now = LocalTime.now(); delay(1_000) } }
    val gold = Color(0xFFE9C77B)
    val date = remember { java.time.LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.ITALIAN)).replaceFirstChar { it.uppercase() } }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2B3A62), Color(0xFF7F6A86), Color(0xFFE7B58F))))) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF3E5C8A), Color(0xFF8A3A5C)))),
                    contentAlignment = Alignment.Center,
                ) { Icon(EuriseIcons.bowtie(1.7f), null, tint = Color.White, modifier = Modifier.size(42.dp)) }
                Text(stringResource(R.string.alfred_label), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, color = gold, modifier = Modifier.padding(top = 8.dp))
                Text(now.format(DateTimeFormatter.ofPattern("H:mm")), fontSize = 64.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Text(date, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCBD5E1))
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("«${a.wake.greeting}»", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp, color = Color.White)
                Text(a.wake.motto, fontSize = 16.sp, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif, lineHeight = 23.sp, color = gold)
                val p = a.wake.pearl
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = 0.94f)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF2DDE5)), contentAlignment = Alignment.Center) {
                        Icon(EuriseIcons.music(), null, tint = Color(0xFF8A3A5C), modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${p.artist} · «${p.title}» (${p.year})", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E1D1B))
                        val from = when (a.music) {
                            null -> null
                            AlfredRing.PHONE -> stringResource(R.string.alfred_from_phone)
                            else -> stringResource(R.string.alfred_from, a.music)
                        }
                        Text("«${p.comment}»" + (from?.let { " · $it" }.orEmpty()), fontSize = 12.sp, lineHeight = 17.sp, color = Color(0xFF56564F))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (r.snoozesLeft > 0) AlfredButton(stringResource(R.string.alfred_snooze, r.snoozeMinutes), filled = false, Modifier.weight(1f), onSnooze)
                AlfredButton(
                    stringResource(when (a.madam) { true -> R.string.alfred_awake_f; false -> R.string.alfred_awake_m; null -> R.string.alfred_awake }),
                    filled = true, Modifier.weight(1f), onDismiss,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AlfredButton(label: String, filled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(58.dp).clip(RoundedCornerShape(29.dp))
            .then(if (filled) Modifier.background(Color.White) else Modifier.border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(29.dp)))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 15.sp, fontWeight = if (filled) FontWeight.ExtraBold else FontWeight.Bold, color = if (filled) Color(0xFF1F2A44) else Color.White)
    }
}

package eu.eurise.feature.clock.system

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

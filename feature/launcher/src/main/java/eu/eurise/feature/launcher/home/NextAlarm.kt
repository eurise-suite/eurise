package eu.eurise.feature.launcher.home

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * La prossima sveglia del telefono: quella che l'app Orologio ha comunicato al sistema
 * (AlarmManager.setAlarmClock). Si aggiorna da sola quando la sveglia viene impostata, spostata o spenta.
 */
@Composable
fun rememberNextAlarm(): LocalDateTime? {
    val context = LocalContext.current
    var next by remember { mutableStateOf(readNextAlarm(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                next = readNextAlarm(context)
            }
        }
        val filter = IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        // Gli annunci di sistema arrivano anche ai ricevitori non esportati.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        next = readNextAlarm(context)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return next
}

private fun readNextAlarm(context: Context): LocalDateTime? = runCatching {
    val info = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock ?: return null
    LocalDateTime.ofInstant(Instant.ofEpochMilli(info.triggerTime), ZoneId.systemDefault())
}.getOrNull()

/** "06:45" se suona entro un giorno, altrimenti con il giorno davanti: "lun 06:45". */
internal fun alarmLabel(alarm: LocalDateTime, now: LocalDateTime): String =
    if (alarm.isBefore(now.plusHours(24))) alarm.format(AlarmTime)
    else alarm.format(AlarmDayTime)

private val AlarmTime = DateTimeFormatter.ofPattern("HH:mm")
private val AlarmDayTime = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ITALIAN)

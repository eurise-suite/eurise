package eu.eurise.feature.clock

import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import eu.eurise.feature.clock.data.ClockStore
import eu.eurise.feature.clock.logic.Alarm
import eu.eurise.feature.clock.logic.ClockTimer
import eu.eurise.feature.clock.system.Notifications
import eu.eurise.feature.clock.system.Scheduler
import eu.eurise.feature.clock.ui.ClockApp
import eu.eurise.core.designsystem.theme.EuriseTheme
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Eurise Orologio: Sveglie, Timer, Cronometro e Fusi. Risponde anche alle richieste standard di Android
 * (SET_ALARM, SET_TIMER…), così i timer rapidi della Scrivania e le altre app partono qui; con "senza interfaccia"
 * fa il lavoro e si chiude subito.
 */
class ClockActivity : ComponentActivity() {
    private var tab by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Notifications.channels(this)
        if (savedInstanceState == null && handle(intent)) { finish(); return }
        setContent { EuriseTheme { ClockApp(tab, onTab = { tab = it }) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        Scheduler.rescheduleAll(this)
    }

    /** Esegue la richiesta; vero se chiede di non mostrare nulla. */
    private fun handle(intent: Intent?): Boolean {
        intent ?: return false
        intent.getIntExtra(EXTRA_TAB, -1).takeIf { it >= 0 }?.let { tab = it }
        val store = ClockStore.get(this)
        val skipUi = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)
        when (intent.action) {
            AlarmClock.ACTION_SET_TIMER -> {
                tab = 1
                val seconds = intent.getIntExtra(AlarmClock.EXTRA_LENGTH, 0)
                if (seconds <= 0) return false
                val t = ClockTimer(store.nextId(), intent.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty(), seconds * 1000L)
                store.saveTimer(t.start(System.currentTimeMillis()))
                Scheduler.rescheduleAll(this)
                return skipUi
            }
            AlarmClock.ACTION_SET_ALARM -> {
                tab = 0
                if (!intent.hasExtra(AlarmClock.EXTRA_HOUR)) return false
                val days = intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS).orEmpty().mapNotNull { calendarDay(it) }.toSet()
                val a = Alarm(
                    store.nextId(),
                    LocalTime.of(intent.getIntExtra(AlarmClock.EXTRA_HOUR, 7).coerceIn(0, 23), intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0).coerceIn(0, 59)),
                    intent.getStringExtra(AlarmClock.EXTRA_MESSAGE).orEmpty(), days,
                )
                store.saveAlarm(a)
                Scheduler.rescheduleAll(this)
                Toast.makeText(this, getString(R.string.alarm_set_at, a.time.toString()), Toast.LENGTH_SHORT).show()
                return skipUi
            }
            AlarmClock.ACTION_SHOW_ALARMS -> tab = 0
            AlarmClock.ACTION_SHOW_TIMERS -> tab = 1
        }
        return false
    }

    /** I giorni di AlarmClock seguono java.util.Calendar (1 = domenica). */
    private fun calendarDay(d: Int): DayOfWeek? = if (d in 1..7) DayOfWeek.of(if (d == 1) 7 else d - 1) else null

    companion object {
        const val EXTRA_TAB = "eu.eurise.feature.clock.TAB"
    }
}

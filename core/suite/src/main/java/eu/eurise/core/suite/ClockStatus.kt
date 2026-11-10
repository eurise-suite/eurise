package eu.eurise.core.suite

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Che cosa sta facendo l'Orologio, per la home: un conto alla rovescia che corre, il cronometro acceso. Lo scrive
 * l'Orologio quando cambia qualcosa; la home lo legge senza dipendere dal modulo dell'Orologio.
 */
object ClockStatus {
    data class Status(
        /** Fine del conto alla rovescia più vicino che corre (ms, orologio di sistema). */
        val timerEndsAt: Long? = null,
        val timerLabel: String = "",
        /** Cronometro: istante di partenza se corre, più il tempo già accumulato. */
        val stopwatchStart: Long? = null,
        val stopwatchAccumulated: Long = 0,
    )

    private val _state = MutableStateFlow(Status())
    val state: StateFlow<Status> = _state.asStateFlow()
    @Volatile private var loaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("clock_status", Context.MODE_PRIVATE)

    /** Da chiamare fuori dal thread principale prima di leggere [state] la prima volta. */
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        _state.value = Status(
            p.getLong("timer_end", -1).takeIf { it > 0 }, p.getString("timer_label", "").orEmpty(),
            p.getLong("sw_start", -1).takeIf { it >= 0 }, p.getLong("sw_acc", 0),
        )
    }

    fun publish(context: Context, status: Status) {
        loaded = true
        _state.value = status
        prefs(context).edit()
            .putLong("timer_end", status.timerEndsAt ?: -1).putString("timer_label", status.timerLabel)
            .putLong("sw_start", status.stopwatchStart ?: -1).putLong("sw_acc", status.stopwatchAccumulated)
            .apply()
    }
}

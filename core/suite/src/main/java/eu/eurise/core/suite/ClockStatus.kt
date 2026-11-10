package eu.eurise.core.suite

import android.content.Context
import android.content.SharedPreferences

/**
 * Che cosa sta facendo l'Orologio, per la home: un conto alla rovescia che corre, il cronometro acceso. Lo scrive
 * l'Orologio quando cambia qualcosa; la home lo legge (e ascolta i cambiamenti) senza dipendere dal suo modulo.
 * Niente dipendenze: preferenze del telefono e un ascoltatore.
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

    const val PREFS = "clock_status"

    fun prefs(context: Context): SharedPreferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Da leggere fuori dal thread principale la prima volta (apre il file delle preferenze). */
    fun read(context: Context): Status {
        val p = prefs(context)
        return Status(
            p.getLong("timer_end", -1).takeIf { it > 0 }, p.getString("timer_label", "").orEmpty(),
            p.getLong("sw_start", -1).takeIf { it >= 0 }, p.getLong("sw_acc", 0),
        )
    }

    fun publish(context: Context, status: Status) {
        prefs(context).edit()
            .putLong("timer_end", status.timerEndsAt ?: -1).putString("timer_label", status.timerLabel)
            .putLong("sw_start", status.stopwatchStart ?: -1).putLong("sw_acc", status.stopwatchAccumulated)
            .apply()
    }
}

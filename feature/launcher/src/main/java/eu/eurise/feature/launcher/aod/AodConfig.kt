package eu.eurise.feature.launcher.aod

import android.content.Context
import androidx.annotation.StringRes
import eu.eurise.feature.launcher.R

/** Quando si accende il display a riposo. */
enum class AodMode { Popup, Always, Charging }

/** Le bolle che il display a riposo può mostrare. Codici e consegne restano fuori: a telefono bloccato non si vedono. */
enum class AodBubble(val id: String, @StringRes val title: Int) {
    NextEvent("next_event", R.string.bubble_next_title),
    Today("today", R.string.bubble_today_name),
    Clock("clock", R.string.bubble_clock_title),
    Calendar("calendar", R.string.card_calendar),
    Comms("comms", R.string.welcome_comms),
    Music("music", R.string.welcome_music),
    Meds("meds", R.string.bubble_meds_title);

    companion object {
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }
}

const val AOD_MAX_BUBBLES = 4

/** Che cosa ha chiesto di accendere il display a riposo. */
enum class AodTrigger { Notification, Lift, ScreenOn, ScreenOff, Charger }

data class AodConfig(
    val enabled: Boolean = false,
    val mode: AodMode = AodMode.Popup,
    val seconds: Int = 10,
    val bubbles: List<AodBubble> = listOf(AodBubble.NextEvent, AodBubble.Today, AodBubble.Clock, AodBubble.Comms),
    val nightOff: Boolean = true,
    val nightFromMinute: Int = 23 * 60,
    val nightToMinute: Int = 7 * 60,
    val minBattery: Int = 20,
) {
    /** La schermata resta accesa finché non la si spegne. */
    fun staysOn(charging: Boolean): Boolean = mode == AodMode.Always || (mode == AodMode.Charging && charging)
}

/** Vero se [minute] (del giorno) cade nella fascia notturna, anche a cavallo della mezzanotte. */
fun isNight(minute: Int, from: Int, to: Int): Boolean =
    if (from <= to) minute in from until to else minute >= from || minute < to

/** La regola unica: per questo [trigger], in questo momento, il display a riposo si accende? */
fun shouldShowAod(config: AodConfig, trigger: AodTrigger, minute: Int, battery: Int, charging: Boolean): Boolean {
    if (!config.enabled) return false
    if (config.nightOff && isNight(minute, config.nightFromMinute, config.nightToMinute)) return false
    if (battery < config.minBattery && !charging) return false
    return when (config.mode) {
        AodMode.Popup -> trigger in setOf(AodTrigger.Notification, AodTrigger.Lift, AodTrigger.ScreenOn)
        AodMode.Always -> true
        AodMode.Charging -> charging
    }
}

/** Le caselle di un mese, da lunedì: null per i giorni vuoti prima del primo del mese. */
fun monthCells(month: java.time.YearMonth): List<java.time.LocalDate?> =
    List(month.atDay(1).dayOfWeek.value - 1) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

/** Le bolle sullo schermo: nella versione sempre accesa solo la prima, per consumare meno. */
fun visibleBubbles(config: AodConfig, charging: Boolean): List<AodBubble> =
    if (config.mode == AodMode.Always && !charging) config.bubbles.take(1) else config.bubbles.take(AOD_MAX_BUBBLES)

/** Impostazioni salvate a parte: il servizio le legge senza caricare quelle della Scrivania. */
class AodStore(context: Context) {
    private val prefs = context.getSharedPreferences("eurise_aod", Context.MODE_PRIVATE)

    fun load() = AodConfig(
        enabled = prefs.getBoolean("enabled", false),
        mode = AodMode.entries.firstOrNull { it.name == prefs.getString("mode", null) } ?: AodMode.Popup,
        seconds = prefs.getInt("seconds", 10),
        bubbles = prefs.getString("bubbles", null)?.split(',')?.mapNotNull { AodBubble.byId(it) } ?: AodConfig().bubbles,
        nightOff = prefs.getBoolean("night_off", true),
        minBattery = prefs.getInt("min_battery", 20),
    )

    fun save(config: AodConfig) {
        prefs.edit()
            .putBoolean("enabled", config.enabled)
            .putString("mode", config.mode.name)
            .putInt("seconds", config.seconds)
            .putString("bubbles", config.bubbles.joinToString(",") { it.id })
            .putBoolean("night_off", config.nightOff)
            .putInt("min_battery", config.minBattery)
            .apply()
    }
}

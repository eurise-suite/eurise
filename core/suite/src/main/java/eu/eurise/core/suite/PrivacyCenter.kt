package eu.eurise.core.suite

import android.content.Context
import java.io.File

/**
 * I servizi esterni che la suite contatta da sola, ognuno con il suo interruttore. Regola fissa del 14 novembre: tutti
 * spenti di base; li accende la persona, dopo l'avviso su cosa esce e come cambia la privacy. Chi aggiorna da una
 * versione in cui erano accesi di base li ritrova accesi (vedi [PrivacyCenter.keepOldChoices]).
 */
enum class PrivacyService { Weather, Pollen, Alerts, News, Markets, Horoscope, Videos, Routes, RouteWeather, MapSearch, MapReports, FuelPrices,
    /** Alfred che passa una domanda, ripulita sul telefono, all'AI online scelta (Lumo, Euria, Le Chat…). */
    AiOnline;

    val defaultOn: Boolean get() = false
}

/** Una riga del registro: [count] richieste allo stesso servizio raccolte in una. */
data class PrivacyEvent(val time: Long, val service: PrivacyService, val count: Int)

/**
 * Regia del centro privacy: un servizio spento non viene più contattato, e ogni contatto finisce in un registro che resta
 * sul telefono e si svuota dopo 30 giorni. [init] va chiamato all'avvio; non legge il disco: lo fa il primo uso.
 */
object PrivacyCenter {
    private const val DAY = 86_400_000L
    private const val MERGE_MS = 10 * 60_000L
    private lateinit var app: Context
    private val prefs by lazy { app.getSharedPreferences("privacy_center", Context.MODE_PRIVATE).also(::keepOldChoices) }
    private val file by lazy { File(app.filesDir, "privacy_log.txt") }
    private val lock = Any()

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun allowed(service: PrivacyService): Boolean = prefs.getBoolean(service.name, service.defaultOn)

    fun set(service: PrivacyService, on: Boolean) {
        prefs.edit().putBoolean(service.name, on).apply()
    }

    /**
     * Vero una volta sola, dopo la prima installazione: la Scrivania mostra l'elenco dei servizi che si possono accendere.
     * Chi aggiorna non lo vede (i servizi che usava restano com'erano). Da chiamare fuori dal thread principale.
     */
    fun welcomePending(): Boolean = !prefs.getBoolean(KEY_WELCOME, false)

    fun welcomeDone() {
        prefs.edit().putBoolean(KEY_WELCOME, true).apply()
    }

    /**
     * Fino al giro 474 alcuni servizi erano accesi di base e la scelta si salvava solo se la persona toccava
     * l'interruttore. Chi aggiorna da allora li ritrova accesi: si scrive «acceso» dove non c'è già una scelta, una volta
     * sola. Un'app già installata si riconosce dalla data d'installazione diversa da quella dell'ultimo aggiornamento.
     */
    private fun keepOldChoices(p: android.content.SharedPreferences) {
        if (p.getBoolean(KEY_MIGRATED, false)) return
        val updated = runCatching {
            val info = app.packageManager.getPackageInfo(app.packageName, 0)
            info.firstInstallTime != info.lastUpdateTime
        }.getOrDefault(false)
        p.edit().apply {
            if (updated) {
                OLD_DEFAULT_ON.forEach { if (!p.contains(it.name)) putBoolean(it.name, true) }
                putBoolean(KEY_WELCOME, true)
            }
            putBoolean(KEY_MIGRATED, true)
        }.apply()
    }

    private const val KEY_MIGRATED = "old_defaults_kept"
    private const val KEY_WELCOME = "welcome_done"
    private val OLD_DEFAULT_ON = listOf(
        PrivacyService.Weather, PrivacyService.Pollen, PrivacyService.Alerts, PrivacyService.News, PrivacyService.Markets,
        PrivacyService.Horoscope, PrivacyService.Videos, PrivacyService.MapReports,
    )

    /** Annota un contatto; richieste vicine allo stesso servizio diventano una riga sola. */
    fun log(service: PrivacyService) = synchronized(lock) {
        val now = System.currentTimeMillis()
        val list = read(now).toMutableList()
        val last = list.lastOrNull()
        if (last != null && last.service == service && now - last.time < MERGE_MS) list[list.lastIndex] = last.copy(count = last.count + 1)
        else list += PrivacyEvent(now, service, 1)
        file.writeText(list.joinToString("\n") { "${it.time}\t${it.service.name}\t${it.count}" })
    }

    /** Il registro, dal più recente. Da chiamare fuori dal thread principale. */
    fun events(): List<PrivacyEvent> = synchronized(lock) { read(System.currentTimeMillis()).asReversed() }

    private fun read(now: Long): List<PrivacyEvent> = parseLog(if (file.exists()) file.readText() else "", now - 30 * DAY)

    internal fun parseLog(text: String, since: Long): List<PrivacyEvent> = text.lineSequence().mapNotNull { line ->
        val p = line.split('\t')
        val time = p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val service = PrivacyService.entries.firstOrNull { it.name == p.getOrNull(1) } ?: return@mapNotNull null
        PrivacyEvent(time, service, p.getOrNull(2)?.toIntOrNull() ?: 1)
    }.filter { it.time >= since }.toList()
}

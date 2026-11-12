package eu.eurise.core.suite

import android.content.Context
import java.io.File

/** I servizi esterni che la Scrivania contatta da sola, ognuno con il suo interruttore. */
enum class PrivacyService(val defaultOn: Boolean) { Weather(true), Pollen(true), Alerts(true), News(true), Markets(true), Horoscope(true), Videos(true), Routes(false), RouteWeather(false) }

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
    private val prefs by lazy { app.getSharedPreferences("privacy_center", Context.MODE_PRIVATE) }
    private val file by lazy { File(app.filesDir, "privacy_log.txt") }
    private val lock = Any()

    fun init(context: Context) {
        app = context.applicationContext
    }

    fun allowed(service: PrivacyService): Boolean = prefs.getBoolean(service.name, service.defaultOn)

    fun set(service: PrivacyService, on: Boolean) {
        prefs.edit().putBoolean(service.name, on).apply()
    }

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

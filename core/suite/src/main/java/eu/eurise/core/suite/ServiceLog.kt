package eu.eurise.core.suite

import android.content.Context
import java.io.File

/** Una riga del registro di un'app separata: [count] contatti vicini allo stesso servizio raccolti in uno. */
data class ServiceEvent(val time: Long, val id: String, val count: Int)

/**
 * Il registro dei contatti ai servizi esterni di un'app separata (Password, Galleria), come [PrivacyCenter.log] per
 * l'app Eurise: resta sul telefono, nella cartella dell'app, 30 giorni; il centro privacy lo legge con [AppHubContract].
 * Si scrive fuori dal thread principale.
 */
object ServiceLog {
    private const val DAY = 86_400_000L
    private const val MERGE_MS = 10 * 60_000L
    private val lock = Any()

    private fun file(context: Context) = File(context.applicationContext.filesDir, "service_log.txt")

    fun log(context: Context, id: String) = synchronized(lock) {
        runCatching {
            val f = file(context)
            val now = System.currentTimeMillis()
            val text = merge(if (f.exists()) f.readText() else "", id, now)
            f.writeText(text)
        }
    }

    /** Il registro come testo (per [AppHubContract.KEY_LOG]); da chiamare fuori dal thread principale. */
    fun text(context: Context): String = synchronized(lock) {
        val f = file(context)
        val now = System.currentTimeMillis()
        format(parse(if (f.exists()) f.readText() else "", now - 30 * DAY))
    }

    fun parse(text: String, since: Long = 0L): List<ServiceEvent> = text.lineSequence().mapNotNull { line ->
        val p = line.split('\t')
        val time = p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
        val id = p.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        ServiceEvent(time, id, p.getOrNull(2)?.toIntOrNull()?.coerceAtLeast(1) ?: 1)
    }.filter { it.time >= since }.toList()

    fun format(events: List<ServiceEvent>): String = events.joinToString("\n") { "${it.time}\t${it.id}\t${it.count}" }

    /** Aggiunge un contatto a [id] in [now]: se l'ultima riga è dello stesso servizio da meno di 10 minuti, la conta. */
    fun merge(text: String, id: String, now: Long): String {
        val list = parse(text, now - 30 * DAY).toMutableList()
        val last = list.lastOrNull()
        if (last != null && last.id == id && now - last.time < MERGE_MS) list[list.lastIndex] = last.copy(count = last.count + 1)
        else list += ServiceEvent(now, id, 1)
        return format(list)
    }
}

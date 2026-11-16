package eu.eurise.desktop.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.eurise.core.ponte.Kind
import eu.eurise.desktop.Control
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Ciò che «Oggi» mostra, preso dal Ponte attraverso il socket locale (lo stesso che usa Firefox, così funziona sia con
 * il Ponte dentro l'app sia con `eurise-ponte` acceso da terminale). Il riassunto si chiede al telefono al massimo ogni
 * due minuti e solo con Oggi in vista; l'ultimo resta in memoria (mai su disco) per sei ore.
 */
class TodayFeed {
    enum class Phone { Unknown, Ok, Off, Away }

    var summary by mutableStateOf<TodayData.Summary?>(null)
        private set
    var phone by mutableStateOf(Phone.Unknown)
        private set
    var recent by mutableStateOf<List<TodayData.Recent>>(emptyList())
        private set

    /** Vero mentre la sezione Oggi è in vista: solo allora si disturba il telefono. */
    @Volatile var visible = false
    @Volatile private var lastAsk = 0L
    @Volatile private var summaryAt = 0L

    /** Il nome dell'utente dal sistema (il primo del campo GECOS), per il saluto; null se non c'è. */
    val name: String? by lazy { userName() }

    fun start() {
        thread(isDaemon = true, name = "oggi") {
            while (true) {
                runCatching { refresh() }
                Thread.sleep(if (visible) 15_000 else 60_000)
            }
        }
    }

    /** Subito, per esempio quando si torna su Oggi. */
    fun poke() { thread(isDaemon = true) { runCatching { refresh() } } }

    @Synchronized
    private fun refresh() {
        Control.query("recent")?.let { recent = TodayData.parseRecent(it) }
        if (summaryAt > 0 && System.currentTimeMillis() - summaryAt > 6 * 3_600_000L) { summary = null; summaryAt = 0 }
        // Prima ciò che il Ponte ha già (chiesto da poco anche da Firefox), poi, se è vecchio, al telefono.
        val cached = Control.query("today")?.firstOrNull()
        val age = cached?.let { eu.eurise.core.ponte.Json.read(it)?.get("age")?.toLongOrNull() }
        if (age != null && cached != null) take(cached, System.currentTimeMillis() - age * 1000)
        if (!visible || System.currentTimeMillis() - lastAsk < 120_000) return
        lastAsk = System.currentTimeMillis()
        val asked = Control.ask(Kind.TodayAsk, emptyMap())
        if (asked == null || asked.none { "arrivato" in it }) { if (summary == null || phone == Phone.Unknown) phone = Phone.Away; return }
        repeat(8) {
            Thread.sleep(600)
            val line = Control.query("today")?.firstOrNull() ?: return@repeat
            val a = eu.eurise.core.ponte.Json.read(line)?.get("age")?.toLongOrNull() ?: return@repeat
            if (a <= 10) { take(line, System.currentTimeMillis()); return }
        }
    }

    private fun take(line: String, at: Long) {
        val s = TodayData.parseSummary(line) ?: return
        if (at < summaryAt) return
        summaryAt = at
        if (s.off) { phone = Phone.Off; summary = null } else { phone = Phone.Ok; summary = s }
    }

    /**
     * Manda al telefono ciò che è scritto in «Scrivi come parli»: risponde con la frase da mostrare sotto il campo.
     * Se il telefono non è sulla rete di casa resta in coda e parte appena torna.
     */
    fun send(kind: Kind, fields: Map<String, String>): String {
        val lines = Control.ask(kind, fields) ?: return "Il Ponte non è acceso: riapri Eurise."
        val what = when (kind) { Kind.Event -> "Appuntamento"; Kind.Task -> "Cosa da fare"; else -> "Nota" }
        return when {
            lines.any { it.startsWith("errore") } -> "Non mandato: ${lines.first()}"
            lines.isEmpty() -> "Nessun telefono abbinato."
            lines.all { "arrivato" in it } -> "$what mandato al telefono."
            else -> "$what in coda: parte quando il telefono torna sulla rete di casa."
        }.also { poke() }
    }

    private fun userName(): String? = runCatching {
        val user = System.getProperty("user.name") ?: return null
        val p = ProcessBuilder("getent", "passwd", user).redirectErrorStream(true).start()
        if (!p.waitFor(2, TimeUnit.SECONDS)) { p.destroy(); return null }
        p.inputStream.bufferedReader().readText().split(':').getOrNull(4)
            ?.split(',')?.firstOrNull()?.trim()?.split(' ')?.firstOrNull()?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

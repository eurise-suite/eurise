package eu.eurise.core.cloud.backup

import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** I tre ruoli di un backup conservato. */
enum class Tier(val letter: Char) { Recent('r'), Monthly('m'), Quarterly('q') }

/** Un backup nella destinazione, riconosciuto dal nome: eurise-AAAAMMGG-hhmmss-r.eurisebk. */
data class BackupFile(val name: String, val at: LocalDateTime, val tier: Tier, val size: Long? = null)

/** Che cosa fare dei vecchi backup dopo averne caricato uno nuovo. */
sealed interface RetentionAction {
    data class Rename(val from: String, val to: String) : RetentionAction
    data class Delete(val name: String) : RetentionAction
}

/**
 * Regola di conservazione (CLAUDE.md, "Dati e backup"): i tre più recenti, un mensile e un trimestrale.
 * Il nuovo prende il posto del più vecchio dei recenti; quello che esce diventa il mensile se il mensile ha più di
 * un mese, e allora il vecchio mensile diventa il trimestrale se il trimestrale ha più di tre mesi.
 */
object Retention {
    const val RECENT = 3
    private val Stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val Pattern = Regex("""eurise-(\d{8}-\d{6})-([rmq])\.eurisebk""")

    fun fileName(at: LocalDateTime, tier: Tier) = "eurise-${at.format(Stamp)}-${tier.letter}.eurisebk"

    fun parse(name: String, size: Long? = null): BackupFile? {
        val m = Pattern.matchEntire(name) ?: return null
        val at = runCatching { LocalDateTime.parse(m.groupValues[1], Stamp) }.getOrNull() ?: return null
        val tier = Tier.entries.first { it.letter == m.groupValues[2][0] }
        return BackupFile(name, at, tier, size)
    }

    fun plan(files: List<BackupFile>): List<RetentionAction> {
        val actions = mutableListOf<RetentionAction>()
        val recents = files.filter { it.tier == Tier.Recent }.sortedByDescending { it.at }
        var monthly = files.filter { it.tier == Tier.Monthly }.maxByOrNull { it.at }
        var quarterly = files.filter { it.tier == Tier.Quarterly }.maxByOrNull { it.at }
        // Doppioni di mensile o trimestrale (per esempio da un vecchio telefono): resta il più recente.
        files.filter { it.tier == Tier.Monthly && it != monthly }.forEach { actions += RetentionAction.Delete(it.name) }
        files.filter { it.tier == Tier.Quarterly && it != quarterly }.forEach { actions += RetentionAction.Delete(it.name) }

        for (out in recents.drop(RECENT).sortedBy { it.at }) {
            val m = monthly
            if (m == null || Duration.between(m.at, out.at).toDays() >= 30) {
                val promoted = out.copy(name = fileName(out.at, Tier.Monthly), tier = Tier.Monthly)
                actions += RetentionAction.Rename(out.name, promoted.name)
                if (m != null) {
                    val q = quarterly
                    if (q == null || Duration.between(q.at, m.at).toDays() >= 90) {
                        val toQ = fileName(m.at, Tier.Quarterly)
                        actions += RetentionAction.Rename(m.name, toQ)
                        if (q != null) actions += RetentionAction.Delete(q.name)
                        quarterly = m.copy(name = toQ, tier = Tier.Quarterly)
                    } else {
                        actions += RetentionAction.Delete(m.name)
                    }
                }
                monthly = promoted
            } else {
                actions += RetentionAction.Delete(out.name)
            }
        }
        return actions
    }

    /** Applica le azioni a una lista (per sapere che cosa resterà, e per i test). */
    fun apply(files: List<BackupFile>, actions: List<RetentionAction>): List<BackupFile> {
        val map = files.associateBy { it.name }.toMutableMap()
        actions.forEach { a ->
            when (a) {
                is RetentionAction.Delete -> map.remove(a.name)
                is RetentionAction.Rename -> map.remove(a.from)?.let { f -> parse(a.to)?.let { map[a.to] = it.copy(size = f.size) } }
            }
        }
        return map.values.sortedByDescending { it.at }
    }
}

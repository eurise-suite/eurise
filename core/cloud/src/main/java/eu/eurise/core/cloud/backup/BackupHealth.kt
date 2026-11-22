package eu.eurise.core.cloud.backup

import java.util.concurrent.TimeUnit

/**
 * Quando il backup automatico è fermo da troppo. Il lavoro parte solo con Wi-Fi e telefono in carica, quindi qualche
 * giorno di ritardo è normale: l'avviso sulla Scrivania compare dopo [GRACE_DAYS] giorni oltre la frequenza scelta.
 */
object BackupHealth {
    const val GRACE_DAYS = 3

    /**
     * Giorni di ritardo rispetto alla frequenza, o null se va tutto bene o non c'è niente da dire.
     * [since]: l'ultimo backup riuscito, o l'accensione se non ce n'è ancora uno; [frequencyDays] 0 = solo a mano.
     */
    fun lateDays(since: Long?, frequencyDays: Int, now: Long): Int? {
        if (since == null || frequencyDays <= 0 || now < since) return null
        val late = TimeUnit.MILLISECONDS.toDays(now - since) - frequencyDays
        return if (late >= GRACE_DAYS) late.toInt() else null
    }
}

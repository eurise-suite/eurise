package eu.eurise.hub

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import eu.eurise.core.data.calendar.EventDraft
import eu.eurise.core.data.model.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Riempie l'app con dati di esempio inventati, per le schermate di F-Droid scattate dal telefono simulato.
 * Esiste solo nelle versioni di prova; si chiama da adb:
 * `am broadcast -a eu.eurise.debug.DEMO_DATA -n eu.eurise.hub.debug/eu.eurise.hub.DemoDataReceiver`.
 */
class DemoDataReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val data = (context.applicationContext as EuriseApplication).data
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val today = LocalDate.now()
                data.notes.save(null, "Viaggio a Bruxelles", "Treno delle 7:12, albergo vicino a Place Flagey.\nPortare il caricatore e il libro.")
                data.notes.save(null, "Spesa", "Pane, latte, mele, caffè, basilico")
                data.notes.save(null, "Idee per il compleanno di Marta", "Corso di ceramica, cena al lago, una bici usata da sistemare insieme.")

                data.tasks.add("Inviare il preventivo", due = today, time = LocalTime.of(15, 0), priority = Task.PRIORITY_HIGH)
                data.tasks.add("Ritirare il pacco in posta", due = today)
                data.tasks.add("Prenotare il treno per Torino", due = today.plusDays(1))
                data.tasks.add("Chiamare l'idraulico", due = today.plusDays(2))
                data.tasks.add("Rinnovare la carta d'identità")

                val calendar = data.calendar.createLocalCalendar("Eurise", 0xFF6F93D6.toInt())
                if (calendar != null) {
                    fun at(day: LocalDate, h: Int, m: Int = 0) = LocalDateTime.of(day, LocalTime.of(h, m))
                    listOf(
                        Triple("Riunione di progetto", "Sala Europa", at(today, 10) to at(today, 11)),
                        Triple("Pranzo con Giulia", "Trattoria da Rosa", at(today, 13) to at(today, 14)),
                        Triple("Palestra", "", at(today, 18, 30) to at(today, 19, 30)),
                        Triple("Dentista", "Via Roma 12", at(today.plusDays(1), 9) to at(today.plusDays(1), 9, 45)),
                        Triple("Concerto al parco", "Parco Sempione", at(today.plusDays(2), 21) to at(today.plusDays(2), 23)),
                    ).forEach { (title, place, time) ->
                        data.calendar.save(EventDraft(null, calendar, title, place, "", false, time.first, time.second))
                    }
                }
                Log.i(TAG, "Dati di esempio pronti")
            } catch (e: Exception) {
                Log.e(TAG, "Dati di esempio non riusciti", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "EuriseDemo"
    }
}

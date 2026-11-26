package eu.eurise.postazione

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.postazione.data.App
import eu.eurise.postazione.data.Giornata
import eu.eurise.postazione.data.Gruppi
import eu.eurise.postazione.data.Gruppo
import eu.eurise.postazione.data.LettoreGiornata
import eu.eurise.postazione.data.Scheda
import eu.eurise.postazione.data.Schede
import eu.eurise.postazione.data.Telefono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * Lo stato della Postazione. Tutto resta sul telefono: le scelte (nome, mano, gruppi, scheda scelta a mano) in
 * SharedPreferences privati; impegni e cose da fare si chiedono all'app Eurise a ogni ritorno sulla schermata.
 */
class PostazioneModel(application: Application) : AndroidViewModel(application) {
    val telefono = Telefono(application)
    private val prefs = application.getSharedPreferences("postazione", Context.MODE_PRIVATE)

    var app by mutableStateOf<List<App>>(emptyList()); private set
    private var ruoli: Map<String, String> = emptyMap()
    var gruppi by mutableStateOf<Map<Gruppo, List<String>>>(emptyMap()); private set
    var giornata by mutableStateOf(Giornata()); private set
    var adesso by mutableStateOf(LocalDateTime.now()); private set
    var nome by mutableStateOf(prefs.getString(K_NOME, "").orEmpty()); private set
    var mancino by mutableStateOf(prefs.getBoolean(K_MANCINO, false)); private set
    private var manuale by mutableStateOf(prefs.getString(K_MANUALE, null)?.let { runCatching { Scheda.valueOf(it) }.getOrNull() })
    private var autoAllaScelta by mutableStateOf(prefs.getString(K_AUTO, null)?.let { runCatching { Scheda.valueOf(it) }.getOrNull() })

    val scheda: Scheda get() = Schede.attiva(Schede.automatica(adesso), manuale, autoAllaScelta)
    val automatica: Boolean get() = scheda == Schede.automatica(adesso)

    fun aggiorna() {
        adesso = LocalDateTime.now()
        viewModelScope.launch {
            val (lista, r) = withContext(Dispatchers.Default) { telefono.app() to telefono.ruoli() }
            app = lista; ruoli = r
            ricalcolaGruppi()
            giornata = withContext(Dispatchers.IO) { LettoreGiornata.leggi(getApplication<Application>(), BuildConfig.HUB_PACKAGE) }
        }
    }

    fun tic() { adesso = LocalDateTime.now() }

    private fun ricalcolaGruppi() {
        val chiavi = app.map { it.chiave }
        val insieme = chiavi.toSet()
        gruppi = Gruppo.entries.associateWith { g ->
            Gruppi.leggi(prefs.getString(K_GRUPPO + g.name, null), insieme) ?: Gruppi.risolvi(g.predefinite, chiavi, ruoli)
        }
    }

    private fun salva(g: Gruppo, lista: List<String>) {
        prefs.edit().putString(K_GRUPPO + g.name, Gruppi.scrivi(lista)).apply()
        gruppi = gruppi + (g to lista)
    }

    fun preferita(g: Gruppo, chiave: String) = salva(g, Gruppi.preferita(gruppi[g].orEmpty(), chiave))
    fun aggiungi(g: Gruppo, chiave: String) = salva(g, Gruppi.aggiungi(gruppi[g].orEmpty(), chiave))
    fun togli(g: Gruppo, chiave: String) = salva(g, Gruppi.togli(gruppi[g].orEmpty(), chiave))

    fun scegli(s: Scheda) {
        val auto = Schede.automatica(LocalDateTime.now())
        if (s == auto) { manuale = null; autoAllaScelta = null } else { manuale = s; autoAllaScelta = auto }
        prefs.edit().putString(K_MANUALE, manuale?.name).putString(K_AUTO, autoAllaScelta?.name).apply()
    }

    fun impostaNome(n: String) { nome = n.trim(); prefs.edit().putString(K_NOME, nome).apply() }
    fun impostaMancino(m: Boolean) { mancino = m; prefs.edit().putBoolean(K_MANCINO, m).apply() }

    /** Alfred: l'app AI della suite se c'è, altrimenti l'assistente che il telefono usa. */
    fun alfred() {
        val ai = app.firstOrNull { it.pacchetto == "eu.eurise.ai" || it.pacchetto == "eu.eurise.ai.debug" }
        if (ai == null || !telefono.apri(ai.chiave)) telefono.avvia(android.content.Intent(android.content.Intent.ACTION_ASSIST))
    }

    fun nomeDi(chiave: String): String = app.firstOrNull { it.chiave == chiave }?.nome ?: chiave.substringAfterLast('.')

    private companion object {
        const val K_NOME = "nome"
        const val K_MANCINO = "mancino"
        const val K_MANUALE = "scheda.manuale"
        const val K_AUTO = "scheda.auto"
        const val K_GRUPPO = "gruppo."
    }
}

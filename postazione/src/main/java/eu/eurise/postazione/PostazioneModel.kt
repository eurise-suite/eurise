package eu.eurise.postazione

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.postazione.data.App
import eu.eurise.postazione.data.Ascolto
import eu.eurise.postazione.data.AscoltoService
import eu.eurise.postazione.data.Avviso
import eu.eurise.postazione.data.Brano
import eu.eurise.postazione.data.Giornata
import eu.eurise.postazione.data.Gruppi
import eu.eurise.postazione.data.Gruppo
import eu.eurise.postazione.data.LettoreGiornata
import eu.eurise.postazione.data.Musica
import eu.eurise.postazione.data.Preferiti
import eu.eurise.postazione.data.Preferito
import eu.eurise.postazione.data.Scheda
import eu.eurise.postazione.data.Schede
import eu.eurise.postazione.data.Siti
import eu.eurise.postazione.data.Telefono
import eu.eurise.postazione.data.Usi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Lo stato della Postazione. Tutto resta sul telefono: le scelte (nome, mano, gruppi, siti, scheda scelta a mano, quante
 * volte apri un'app) in SharedPreferences privati; impegni, cose da fare e meteo si chiedono all'app Eurise a ogni
 * ritorno sulla schermata; messaggi in attesa e musica vivono solo in memoria.
 */
class PostazioneModel(application: Application) : AndroidViewModel(application) {
    val telefono = Telefono(application)
    private val prefs = application.getSharedPreferences("postazione", Context.MODE_PRIVATE)
    private val contesto: Context get() = getApplication<Application>()

    var app by mutableStateOf<List<App>>(emptyList()); private set
    private var ruoli: Map<String, String> = emptyMap()
    var gruppi by mutableStateOf<Map<Gruppo, List<String>>>(emptyMap()); private set
    var giornata by mutableStateOf(Giornata()); private set
    var adesso by mutableStateOf(LocalDateTime.now()); private set
    var nome by mutableStateOf(prefs.getString(K_NOME, "").orEmpty()); private set
    var mancino by mutableStateOf(prefs.getBoolean(K_MANCINO, false)); private set
    private var manuale by mutableStateOf(prefs.getString(K_MANUALE, null)?.let { runCatching { Scheda.valueOf(it) }.getOrNull() })
    private var autoAllaScelta by mutableStateOf(prefs.getString(K_AUTO, null)?.let { runCatching { Scheda.valueOf(it) }.getOrNull() })

    /** Accesso alle notifiche acceso (da Android): «Da rispondere» e musica. */
    var ascolto by mutableStateOf(false); private set
    var avvisi by mutableStateOf<List<Avviso>>(emptyList()); private set
    var brano by mutableStateOf<Brano?>(null); private set
    var contatti by mutableStateOf(false); private set
    var preferiti by mutableStateOf<List<Preferito>>(emptyList()); private set
    private var usi by mutableStateOf(Usi.leggi(prefs.getString(K_USI, null)))
    var recenti by mutableStateOf(prefs.getString(K_RECENTI, null).orEmpty().lines().filter { it.isNotBlank() }); private set
    private var chiusa by mutableStateOf(prefs.getString(K_CHIUSA, null))

    val scheda: Scheda get() = Schede.attiva(Schede.automatica(adesso), manuale, autoAllaScelta)
    val automatica: Boolean get() = scheda == Schede.automatica(adesso)

    /** «Chiudi la giornata» da proporre: fine del lavoro, non ancora chiusa oggi. */
    val fineGiornata: Boolean get() = Schede.fineGiornata(adesso) && chiusa != adesso.toLocalDate().toString()

    init {
        viewModelScope.launch { AscoltoService.avvisi.collect { avvisi = it } }
    }

    fun aggiorna() {
        adesso = LocalDateTime.now()
        ascolto = Ascolto.acceso(contesto)
        contatti = Preferiti.permesso(contesto)
        viewModelScope.launch {
            val (lista, r) = withContext(Dispatchers.Default) { telefono.app() to telefono.ruoli() }
            app = lista; ruoli = r
            ricalcolaGruppi()
            giornata = withContext(Dispatchers.IO) { LettoreGiornata.leggi(contesto, BuildConfig.HUB_PACKAGE) }
            preferiti = withContext(Dispatchers.IO) { Preferiti.leggi(contesto) }
            leggiMusica()
        }
    }

    fun tic() {
        adesso = LocalDateTime.now()
        viewModelScope.launch { leggiMusica() }
    }

    private suspend fun leggiMusica() {
        brano = if (ascolto) withContext(Dispatchers.Default) { runCatching { Musica.brano(contesto) }.getOrNull() } else null
    }

    fun musica(comando: Musica.Comando) {
        viewModelScope.launch {
            withContext(Dispatchers.Default) { runCatching { Musica.comando(contesto, comando) } }
            delay(400)
            leggiMusica()
        }
    }

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

    /** Un sito in un gruppo; falso se l'indirizzo non è un indirizzo web. */
    fun aggiungiSito(g: Gruppo, indirizzo: String, nomeSito: String): Boolean {
        val pulito = Siti.normalizza(indirizzo) ?: return false
        val chiave = Siti.chiave(pulito)
        prefs.edit().putString(K_SITO + chiave, nomeSito.trim().ifEmpty { Siti.nomeDa(pulito) }).apply()
        aggiungi(g, chiave)
        return true
    }

    /** Apre un'app o un sito e se lo ricorda per «Più usate» e «Recenti». */
    fun apri(chiave: String): Boolean {
        val ok = if (Siti.eSito(chiave)) telefono.apriSito(Siti.indirizzo(chiave)) else telefono.apri(chiave)
        if (ok && !Siti.eSito(chiave)) {
            usi = usi + (chiave to (usi[chiave] ?: 0) + 1)
            recenti = (listOf(chiave) + recenti.filter { it != chiave }).take(8)
            prefs.edit().putString(K_USI, Usi.scrivi(usi)).putString(K_RECENTI, recenti.joinToString("\n")).apply()
        }
        return ok
    }

    fun piuUsate(n: Int): List<String> = Usi.piuUsate(usi, app.map { it.chiave }, n)

    /** Le app che servono adesso: la preferita di ogni gruppo della scheda aperta. */
    fun perAdesso(n: Int): List<String> =
        Gruppi.barra(scheda).mapNotNull { g -> gruppi[g]?.firstOrNull { !Siti.eSito(it) } }.distinct().take(n)

    fun scegli(s: Scheda) {
        val auto = Schede.automatica(LocalDateTime.now())
        if (s == auto) { manuale = null; autoAllaScelta = null } else { manuale = s; autoAllaScelta = auto }
        prefs.edit().putString(K_MANUALE, manuale?.name).putString(K_AUTO, autoAllaScelta?.name).apply()
    }

    /** «Chiudi la giornata»: con [rimanda] le cose aperte passano a domani; poi la scheda Personale. */
    fun chiudiGiornata(rimanda: Boolean) {
        chiusa = LocalDate.now().toString()
        prefs.edit().putString(K_CHIUSA, chiusa).apply()
        viewModelScope.launch {
            if (rimanda) {
                withContext(Dispatchers.IO) { LettoreGiornata.rimanda(contesto, BuildConfig.HUB_PACKAGE) }
                giornata = withContext(Dispatchers.IO) { LettoreGiornata.leggi(contesto, BuildConfig.HUB_PACKAGE) }
                scegli(Scheda.Personale)
            }
        }
    }

    fun impostaNome(n: String) { nome = n.trim(); prefs.edit().putString(K_NOME, nome).apply() }
    fun impostaMancino(m: Boolean) { mancino = m; prefs.edit().putBoolean(K_MANCINO, m).apply() }

    /** Alfred: l'app AI della suite se c'è, altrimenti l'assistente che il telefono usa. */
    fun alfred() {
        val ai = app.firstOrNull { it.pacchetto == "eu.eurise.ai" || it.pacchetto == "eu.eurise.ai.debug" }
        if (ai == null || !apri(ai.chiave)) telefono.avvia(Intent(Intent.ACTION_ASSIST))
    }

    fun nomeDi(chiave: String): String =
        if (Siti.eSito(chiave)) prefs.getString(K_SITO + chiave, null) ?: Siti.nomeDa(Siti.indirizzo(chiave))
        else app.firstOrNull { it.chiave == chiave }?.nome ?: chiave.substringAfterLast('.')

    fun nomeDelPacchetto(pacchetto: String): String = app.firstOrNull { it.pacchetto == pacchetto }?.nome ?: pacchetto

    private companion object {
        const val K_NOME = "nome"
        const val K_MANCINO = "mancino"
        const val K_MANUALE = "scheda.manuale"
        const val K_AUTO = "scheda.auto"
        const val K_GRUPPO = "gruppo."
        const val K_SITO = "sito."
        const val K_USI = "usi"
        const val K_RECENTI = "recenti"
        const val K_CHIUSA = "giornata.chiusa"
    }
}

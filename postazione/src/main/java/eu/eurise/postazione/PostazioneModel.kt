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
import eu.eurise.postazione.data.Auto
import eu.eurise.postazione.data.Intervallo
import eu.eurise.postazione.data.Liberi
import eu.eurise.postazione.data.Notizia
import eu.eurise.postazione.data.Posto
import eu.eurise.postazione.data.Ascolto
import eu.eurise.postazione.data.AscoltoService
import eu.eurise.postazione.data.Avviso
import eu.eurise.postazione.data.Brano
import eu.eurise.postazione.data.Giornata
import eu.eurise.postazione.data.Ai
import eu.eurise.postazione.data.Categoria
import eu.eurise.postazione.data.Contesto
import eu.eurise.postazione.data.Cosa
import eu.eurise.postazione.data.Gruppi
import eu.eurise.postazione.data.Gruppo
import eu.eurise.postazione.data.Impegno
import eu.eurise.postazione.data.Oroscopo
import eu.eurise.postazione.data.Persona
import eu.eurise.postazione.data.Rubrica
import eu.eurise.postazione.data.Trovata
import eu.eurise.postazione.data.Uso
import eu.eurise.core.suite.PostazioneContract
import eu.eurise.postazione.data.LettoreGiornata
import eu.eurise.postazione.data.Musica
import eu.eurise.postazione.data.Preferiti
import eu.eurise.postazione.data.Preferito
import eu.eurise.postazione.data.Scheda
import eu.eurise.postazione.data.Schede
import eu.eurise.postazione.data.Siti
import eu.eurise.postazione.data.Telefono
import eu.eurise.postazione.data.Usi
import eu.eurise.postazione.ui.Accento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

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
    /** Le sei categorie della dock, le stesse in ogni scheda, e le app di ognuna (per id della categoria). */
    var dock by mutableStateOf(Gruppi.leggiDock(prefs.getString(K_DOCK, null))); private set
    var gruppi by mutableStateOf<Map<String, List<String>>>(emptyMap()); private set
    /** Le app fissate in ogni scheda e quelle tolte dai suggerimenti. */
    private var fissate by mutableStateOf<Map<Scheda, List<String>>>(emptyMap())
    private var nascoste by mutableStateOf(Scheda.entries.associateWith { s -> prefs.getString(K_CTX_NO + s.name, null).orEmpty().lines().filter { it.isNotBlank() }.toSet() })
    private var usiScheda by mutableStateOf(Scheda.entries.associateWith { s -> Usi.leggi(prefs.getString(K_USI + "." + s.name, null)) })
    /** L'intelligenza artificiale del tocco sulla riga «Chiedi a…» (tenere premuto le mostra tutte). */
    var ai by mutableStateOf(Ai.da(prefs.getString(K_AI, null))); private set
    var oroscopo by mutableStateOf<Oroscopo?>(null); private set
    private var oroscopoAlle = 0L
    /** Cresce quando il centro privacy chiede di aprire le impostazioni. */
    var impostazioniRichieste by mutableStateOf(0); private set
    /** Le cose appena toccate (fatte, rimandate, tolte): spariscono subito, prima che l'app Eurise risponda. */
    var toccate by mutableStateOf<Set<Long>>(emptySet()); private set
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
    var accento by mutableStateOf(runCatching { Accento.valueOf(prefs.getString(K_ACCENTO, null).orEmpty()) }.getOrDefault(Accento.Ceruleo)); private set

    /** Le notizie dall'app Eurise: null finché non le ha chieste (o se l'app Eurise manca). */
    var notizie by mutableStateOf<List<Notizia>?>(null); private set
    private var notizieAlle = 0L

    /** «Lavoro in pausa» fino a quest'ora (0 = niente pausa) e quanti avvisi aspettano. */
    var pausaFino by mutableStateOf(0L); private set
    var inAttesa by mutableStateOf(0); private set

    /** Dove hai lasciato l'auto, e lo stato di «Segna qui». */
    var auto by mutableStateOf(leggiAuto()); private set
    var posizione by mutableStateOf(false); private set
    var cercoAuto by mutableStateOf(false); private set
    var autoNonTrovata by mutableStateOf(false); private set

    val scheda: Scheda get() = Schede.attiva(Schede.automatica(adesso), manuale, autoAllaScelta)
    val automatica: Boolean get() = scheda == Schede.automatica(adesso)

    /** «Chiudi la giornata» da proporre: fine del lavoro, non ancora chiusa oggi. */
    val fineGiornata: Boolean get() = Schede.fineGiornata(adesso) && chiusa != adesso.toLocalDate().toString()

    init {
        viewModelScope.launch { AscoltoService.avvisi.collect { avvisi = it } }
        viewModelScope.launch { AscoltoService.fermate.collect { inAttesa = it.size } }
    }

    fun aggiorna() {
        adesso = LocalDateTime.now()
        ascolto = Ascolto.acceso(contesto)
        contatti = Preferiti.permesso(contesto)
        posizione = Posto.permesso(contesto)
        pausaFino = Ascolto.pausaFino(contesto).takeIf { it > System.currentTimeMillis() } ?: 0L
        viewModelScope.launch {
            val (lista, r) = withContext(Dispatchers.Default) { telefono.app() to telefono.ruoli() }
            app = lista; ruoli = r
            ricalcolaGruppi()
            giornata = withContext(Dispatchers.IO) { LettoreGiornata.leggi(contesto, BuildConfig.HUB_PACKAGE) }
            preferiti = withContext(Dispatchers.IO) { Preferiti.leggi(contesto) }
            leggiMusica()
            leggiNotizie()
            leggiOroscopo()
        }
    }

    fun tic() {
        adesso = LocalDateTime.now()
        viewModelScope.launch { leggiMusica() }
    }

    /** Solo con la scheda Personale davanti, e al più ogni venti minuti: le legge l'app Eurise, con le sue regole. */
    private suspend fun leggiNotizie() {
        val ora = System.currentTimeMillis()
        if (scheda != Scheda.Personale || !giornata.collegata || ora - notizieAlle < 20 * 60_000L) return
        notizieAlle = ora
        notizie = withContext(Dispatchers.IO) { LettoreGiornata.notizie(contesto, BuildConfig.HUB_PACKAGE) }
    }

    /** Come le notizie: solo con la scheda Personale davanti, al più ogni mezz'ora (o subito se cambi segno). */
    private suspend fun leggiOroscopo(segno: Int? = null) {
        val ora = System.currentTimeMillis()
        if (segno == null && (scheda != Scheda.Personale || !giornata.collegata || ora - oroscopoAlle < 30 * 60_000L)) return
        oroscopoAlle = ora
        oroscopo = withContext(Dispatchers.IO) { LettoreGiornata.oroscopo(contesto, BuildConfig.HUB_PACKAGE, segno) }
    }

    fun cambiaSegno(segno: Int) { viewModelScope.launch { leggiOroscopo(segno) } }

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
        gruppi = dock.associate { c -> c.id to appDi(c, chiavi, insieme) }
        fissate = Scheda.entries.associateWith { s ->
            Gruppi.leggi(prefs.getString(K_CTX + s.name, null), insieme) ?: Gruppi.risolvi(Contesto.predefinite(s), chiavi, ruoli)
        }
    }

    private fun appDi(c: Categoria, chiavi: List<String>, insieme: Set<String>): List<String> =
        Gruppi.leggi(prefs.getString(K_GRUPPO + c.id, null), insieme) ?: Gruppi.risolvi(c.pronta?.predefinite.orEmpty(), chiavi, ruoli)

    private fun salva(id: String, lista: List<String>) {
        prefs.edit().putString(K_GRUPPO + id, Gruppi.scrivi(lista)).apply()
        gruppi = gruppi + (id to lista)
    }

    fun categoria(id: String): Categoria? = dock.firstOrNull { it.id == id }
    fun preferita(id: String, chiave: String) = salva(id, Gruppi.preferita(gruppi[id].orEmpty(), chiave))
    fun aggiungi(id: String, chiave: String) = salva(id, Gruppi.aggiungi(gruppi[id].orEmpty(), chiave))
    fun togli(id: String, chiave: String) = salva(id, Gruppi.togli(gruppi[id].orEmpty(), chiave))

    /** Cambia nome e icona della categoria [id], o la sostituisce con un'altra già pronta (che porta le sue app). */
    fun modificaCategoria(id: String, nuova: Categoria) {
        dock = Gruppi.sostituisci(dock, id, nuova)
        prefs.edit().putString(K_DOCK, Gruppi.scriviDock(dock)).apply()
        ricalcolaGruppi()
    }

    fun spostaCategoria(id: String, verso: Int) {
        dock = Gruppi.sposta(dock, id, verso)
        prefs.edit().putString(K_DOCK, Gruppi.scriviDock(dock)).apply()
    }

    /** Le app della scheda: fissate e più usate in quella scheda. */
    fun appDellaScheda(s: Scheda): List<String> =
        Contesto.mostra(fissate[s].orEmpty(), usiScheda[s].orEmpty(), nascoste[s].orEmpty(), app.map { it.chiave })

    fun fissataInScheda(s: Scheda, chiave: String): Boolean = chiave in fissate[s].orEmpty()

    fun fissaInScheda(s: Scheda, chiave: String) {
        val lista = Gruppi.aggiungi(fissate[s].orEmpty(), chiave)
        fissate = fissate + (s to lista)
        val no = nascoste[s].orEmpty() - chiave
        nascoste = nascoste + (s to no)
        prefs.edit().putString(K_CTX + s.name, Gruppi.scrivi(lista)).putString(K_CTX_NO + s.name, no.joinToString("\n")).apply()
    }

    fun togliDaScheda(s: Scheda, chiave: String) {
        val lista = Gruppi.togli(fissate[s].orEmpty(), chiave)
        fissate = fissate + (s to lista)
        val no = nascoste[s].orEmpty() + chiave
        nascoste = nascoste + (s to no)
        prefs.edit().putString(K_CTX + s.name, Gruppi.scrivi(lista)).putString(K_CTX_NO + s.name, no.joinToString("\n")).apply()
    }

    /** Un sito in un gruppo; falso se l'indirizzo non è un indirizzo web. */
    fun aggiungiSito(g: String, indirizzo: String, nomeSito: String): Boolean {
        val pulito = Siti.normalizza(indirizzo) ?: return false
        val chiave = Siti.chiave(pulito)
        prefs.edit().putString(K_SITO + chiave, nomeSito.trim().ifEmpty { Siti.nomeDa(pulito) }).apply()
        val scheda = g.removePrefix("scheda:").takeIf { g.startsWith("scheda:") }?.let { runCatching { Scheda.valueOf(it) }.getOrNull() }
        if (scheda != null) fissaInScheda(scheda, chiave) else aggiungi(g, chiave)
        return true
    }

    /** Apre un'app o un sito e se lo ricorda per «Più usate» e «Recenti». */
    fun apri(chiave: String): Boolean {
        val ok = if (Siti.eSito(chiave)) telefono.apriSito(Siti.indirizzo(chiave)) else telefono.apri(chiave)
        if (ok && !Siti.eSito(chiave)) {
            usi = usi + (chiave to (usi[chiave] ?: 0) + 1)
            val s = scheda
            val inScheda = usiScheda[s].orEmpty()
            usiScheda = usiScheda + (s to inScheda + (chiave to (inScheda[chiave] ?: 0) + 1))
            recenti = (listOf(chiave) + recenti.filter { it != chiave }).take(8)
            prefs.edit().putString(K_USI, Usi.scrivi(usi)).putString(K_USI + "." + s.name, Usi.scrivi(usiScheda[s].orEmpty()))
                .putString(K_RECENTI, recenti.joinToString("\n")).apply()
        }
        return ok
    }

    fun piuUsate(n: Int): List<String> = Usi.piuUsate(usi, app.map { it.chiave }, n)

    /** Le app che servono adesso: quelle della scheda aperta. */
    fun perAdesso(n: Int): List<String> = appDellaScheda(scheda).filter { !Siti.eSito(it) }.take(n)

    fun scegli(s: Scheda) {
        val auto = Schede.automatica(LocalDateTime.now())
        if (s == auto) { manuale = null; autoAllaScelta = null } else { manuale = s; autoAllaScelta = auto }
        prefs.edit().putString(K_MANUALE, manuale?.name).putString(K_AUTO, autoAllaScelta?.name).apply()
        viewModelScope.launch { leggiNotizie(); leggiOroscopo() }
    }

    /**
     * «Chiudi la giornata»: con [rimanda] le cose aperte passano a domani; con [silenzio] le notifiche della posta e
     * delle app del gruppo Lavoro si rimandano da sole fino alle 8:30; poi la scheda Personale.
     */
    fun chiudiGiornata(rimanda: Boolean, silenzio: Boolean) {
        chiusa = LocalDate.now().toString()
        prefs.edit().putString(K_CHIUSA, chiusa).apply()
        if (silenzio && ascolto) {
            val ora = LocalDateTime.now()
            val mattina = (if (ora.hour < 8 || (ora.hour == 8 && ora.minute < 30)) ora.toLocalDate() else ora.toLocalDate().plusDays(1)).atTime(8, 30)
            pausaFino = mattina.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            Ascolto.pausa(contesto, appDiLavoro(), pausaFino)
        }
        viewModelScope.launch {
            if (rimanda) {
                withContext(Dispatchers.IO) { LettoreGiornata.rimanda(contesto, BuildConfig.HUB_PACKAGE) }
                giornata = withContext(Dispatchers.IO) { LettoreGiornata.leggi(contesto, BuildConfig.HUB_PACKAGE) }
            }
            if (rimanda || silenzio) scegli(Scheda.Personale)
        }
    }

    /** Le app che vanno in pausa la sera: quelle del gruppo Lavoro e la posta del telefono. */
    private fun appDiLavoro(): Set<String> =
        (gruppi[Gruppo.Lavoro.name].orEmpty().filter { !Siti.eSito(it) }.map { it.substringBefore('/') } + listOfNotNull(ruoli["email"]))
            // Mai l'app Eurise: da lì arrivano i promemoria dei farmaci e gli avvisi delle riunioni.
            .filter { it != contesto.packageName && it != BuildConfig.HUB_PACKAGE }.toSet()

    fun finePausa() {
        pausaFino = 0L
        Ascolto.pausa(contesto, emptySet(), 0L)
    }

    /** «Mettile lì»: le prime cose da fare prendono un orario dentro lo spazio libero. */
    fun mettiNelloSpazio(spazio: Intervallo) {
        // Le dosi dei farmaci hanno già il loro orario: non si spostano.
        val ids = giornata.cose.filter { !it.farmaco }.let { c -> c.take(Liberi.quante(spazio, c.size)) }.map { it.id }.toLongArray()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { LettoreGiornata.metti(contesto, BuildConfig.HUB_PACKAGE, ids, spazio.inizio) }
            giornata = withContext(Dispatchers.IO) { LettoreGiornata.leggi(contesto, BuildConfig.HUB_PACKAGE) }
        }
    }

    /** Fatta (o dose presa), a domani, eliminata: sparisce subito, poi si rilegge la giornata. */
    fun azioneCosa(c: Cosa, azione: String) {
        toccate = toccate + c.id
        viewModelScope.launch {
            withContext(Dispatchers.IO) { LettoreGiornata.cosa(contesto, BuildConfig.HUB_PACKAGE, c.id, azione) }
            giornata = withContext(Dispatchers.IO) { LettoreGiornata.leggi(contesto, BuildConfig.HUB_PACKAGE) }
            toccate = toccate - c.id
        }
    }

    fun eliminaImpegno(e: Impegno) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { LettoreGiornata.eliminaImpegno(contesto, BuildConfig.HUB_PACKAGE, e.id) }
            giornata = withContext(Dispatchers.IO) { LettoreGiornata.leggi(contesto, BuildConfig.HUB_PACKAGE) }
        }
    }

    /** Apre nell'app Eurise l'impegno, la cosa da fare (o i farmaci), la nota o il documento. */
    fun apriNellApp(tipo: String, id: Long, inizio: Long = 0L): Boolean {
        val intent = Intent(PostazioneContract.ACTION_OPEN)
            .setClassName(BuildConfig.HUB_PACKAGE, PostazioneContract.OPEN_ACTIVITY)
            .putExtra(PostazioneContract.EXTRA_KIND, tipo).putExtra(PostazioneContract.EXTRA_ID, id).putExtra(PostazioneContract.EXTRA_BEGIN, inizio)
        return telefono.avvia(intent)
    }

    fun apriImpegno(e: Impegno) {
        if (e.id <= 0L || !apriNellApp(PostazioneContract.KIND_EVENT, e.id, e.inizio)) apriSezione("calendar")
    }

    fun apriCosa(c: Cosa) {
        val ok = if (c.farmaco) apriNellApp(PostazioneContract.KIND_MEDICINES, 0L) else apriNellApp(PostazioneContract.KIND_TASK, c.id)
        if (!ok) apriSezione("tasks")
    }

    /** Una sezione dell'app Eurise (meteo, centro privacy, calendario…), con la sua schermata iniziale se è vecchia. */
    fun apriSezione(sezione: String): Boolean {
        val intent = Intent().setClassName(BuildConfig.HUB_PACKAGE, "eu.eurise.hub.MainActivity")
            .putExtra("eu.eurise.extra.SECTION", sezione)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return telefono.avvia(intent)
    }

    // La lente

    var trovate by mutableStateOf<List<Trovata>>(emptyList()); private set
    var persone by mutableStateOf<List<Persona>>(emptyList()); private set

    /** Cerca nell'app Eurise e nella rubrica (solo se i contatti sono accesi). Le app le filtra il cassetto. */
    suspend fun cerca(testo: String) {
        if (testo.trim().length < 2) { trovate = emptyList(); persone = emptyList(); return }
        val (t, p) = withContext(Dispatchers.IO) {
            LettoreGiornata.cerca(contesto, BuildConfig.HUB_PACKAGE, testo) to Rubrica.cerca(contesto, testo)
        }
        trovate = t; persone = p
    }

    fun apriPersona(p: Persona) {
        val suite = apriSezione("person:" + p.chiave)
        if (!suite) telefono.avvia(Intent(Intent.ACTION_VIEW, android.content.ContentUris.withAppendedId(android.provider.ContactsContract.Contacts.CONTENT_URI, p.id)))
    }

    fun cercaSulWeb(testo: String) {
        val q = testo.trim()
        if (q.isEmpty()) return
        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, q)
        val eurise = listOf("eu.eurise.browser", "eu.eurise.browser.debug").firstOrNull { telefono.avviaPacchetto(it) != null }
        if (eurise != null && telefono.avvia(Intent(intent).setPackage(eurise))) return
        if (!telefono.avvia(intent)) telefono.apriSito("https://duckduckgo.com/?q=" + android.net.Uri.encode(q))
    }

    /** Passa la domanda all'AI [scelta] (di base la preferita): Alfred sul telefono, le altre nel browser. */
    fun chiedi(testo: String, scelta: Ai = ai) {
        val q = testo.trim()
        if (scelta.sulTelefono) {
            val alfred = Intent().setClassName(BuildConfig.HUB_PACKAGE, "eu.eurise.hub.alfred.AlfredActivity").putExtra(Intent.EXTRA_TEXT, q)
            if (!telefono.avvia(alfred)) alfred()
        } else scelta.pagina(q)?.let { telefono.apriSito(it) }
    }

    fun preferisciAi(a: Ai) { ai = a; prefs.edit().putString(K_AI, a.name).apply() }

    fun chiediImpostazioni() { impostazioniRichieste++ }

    private fun leggiAuto(): Auto? {
        val quando = prefs.getLong(K_AUTO_QUANDO, 0L)
        if (quando == 0L) return null
        return Auto(Double.fromBits(prefs.getLong(K_AUTO_LAT, 0L)), Double.fromBits(prefs.getLong(K_AUTO_LON, 0L)), quando)
    }

    /** Segna il punto in cui sei come posto dell'auto. */
    fun segnaAuto() {
        if (cercoAuto) return
        cercoAuto = true; autoNonTrovata = false
        Posto.adesso(contesto) { dove ->
            cercoAuto = false
            if (dove == null) { autoNonTrovata = true } else {
                val a = Auto(dove.latitude, dove.longitude, System.currentTimeMillis())
                prefs.edit().putLong(K_AUTO_LAT, a.lat.toRawBits()).putLong(K_AUTO_LON, a.lon.toRawBits()).putLong(K_AUTO_QUANDO, a.quando).apply()
                auto = a
            }
        }
    }

    fun dimenticaAuto() {
        prefs.edit().remove(K_AUTO_LAT).remove(K_AUTO_LON).remove(K_AUTO_QUANDO).apply()
        auto = null
    }

    fun impostaAccento(a: Accento) { accento = a; prefs.edit().putString(K_ACCENTO, a.name).apply() }

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
        const val K_DOCK = "dock"
        const val K_CTX = "scheda.app."
        const val K_CTX_NO = "scheda.tolte."
        const val K_AI = "ai"
        const val K_SITO = "sito."
        const val K_USI = "usi"
        const val K_RECENTI = "recenti"
        const val K_CHIUSA = "giornata.chiusa"
        const val K_ACCENTO = "accento"
        const val K_AUTO_LAT = "auto.lat"
        const val K_AUTO_LON = "auto.lon"
        const val K_AUTO_QUANDO = "auto.quando"
    }
}

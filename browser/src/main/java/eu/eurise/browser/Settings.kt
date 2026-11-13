package eu.eurise.browser

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.eurise.core.designsystem.component.DataOut
import eu.eurise.core.designsystem.component.Jurisdiction
import eu.eurise.core.designsystem.component.privacyScore
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntimeSettings

/** Un servizio DNS cifrato (DNS-over-HTTPS) che si può scegliere; [where]: il paese, scritto; [country]: la sigla. */
data class DnsService(val id: String, val label: String, val where: String, val uri: String, val country: Jurisdiction) {
    /** Riceve i nomi dei siti visitati, senza sapere chi sei: grado di privacy su 10, più la rete nascosta. */
    fun level(hiddenIp: Int = 0) = privacyScore(DataOut.Minimal, country, hiddenIp = hiddenIp)
}

/**
 * Un'AI da interrogare dalla barra: si scrive «? domanda». [out]: cosa ne fa delle conversazioni; [closed]: modello e
 * servizio chiusi (al massimo due tacchette). Grado di privacy su 10, come i motori.
 */
data class AiService(
    val id: String, val label: String, val where: String, val url: String, val country: Jurisdiction,
    val out: DataOut, val closed: Boolean = false,
) {
    fun level(hiddenIp: Int = 0) = privacyScore(out, country, closed = closed, hiddenIp = hiddenIp)
}

/**
 * Le impostazioni del Browser (tavola BrwImpostazioni), sul telefono. Quelle del motore valgono subito per tutte le
 * schede ([applyTo]); le altre le legge l'interfaccia.
 */
class BrowserSettings(context: Context) {
    private val prefs = context.getSharedPreferences("browser_settings", Context.MODE_PRIVATE)

    /** Protezione dai tracciatori «Forte» (rigorosa, come Firefox) o «Standard» (rompe meno siti). */
    var strictProtection by mutableStateOf(prefs.getBoolean("strict", true)); private set
    var httpsOnly by mutableStateOf(prefs.getBoolean("https_only", true)); private set
    var dns by mutableStateOf(prefs.getString("dns", "off") ?: "off"); private set
    var ai by mutableStateOf(prefs.getString("ai", "duck") ?: "duck"); private set
    var restoreTabs by mutableStateOf(prefs.getBoolean("restore_tabs", true)); private set
    /** Schede non aperte da tanti giorni si chiudono da sole all'avvio (0 = mai). */
    var closeAfterDays by mutableIntStateOf(prefs.getInt("close_after", 7)); private set
    /** Dimensione del testo delle pagine (Zoom), 0,8–2. */
    var textScale by mutableFloatStateOf(prefs.getFloat("text_scale", 1f)); private set
    var darkPages by mutableStateOf(prefs.getBoolean("dark_pages", false)); private set
    var swipeTabs by mutableStateOf(prefs.getBoolean("swipe_tabs", true)); private set
    var askWhereToSave by mutableStateOf(prefs.getBoolean("ask_save", true)); private set
    /** La voce del menu messa nella barra in basso, accanto all'indirizzo (tieni premuto nel menu). */
    var barAction by mutableStateOf(prefs.getString("bar_action", "") ?: ""); private set
    /** La persona ha letto e accettato la spiegazione della traduzione sul telefono (modelli scaricati da Mozilla). */
    var translateOk by mutableStateOf(prefs.getBoolean("translate_ok", false)); private set
    var dnsExplained by mutableStateOf(prefs.getBoolean("dns_ok", false)); private set
    /** Barra delle schede sulla pagina (tavola BrwBarraSchede): auto (sul tablet in cima), off, bottom, top. */
    var tabStrip by mutableStateOf(prefs.getString("tab_strip", "auto") ?: "auto"); private set
    /** Dove sta la barra dell'indirizzo: "bottom" (di base) o "top", da tirare giù col pollice (come RicTira). */
    var addressBar by mutableStateOf(prefs.getString("address_bar", "bottom") ?: "bottom"); private set
    /** Immagini nelle pagine (Strumenti › Immagini sì/no). */
    var images by mutableStateOf(prefs.getBoolean("images", true)); private set
    /** Carattere della Lettura: con le grazie (come un libro) o senza. */
    var readerSerif by mutableStateOf(prefs.getBoolean("reader_serif", true)); private set

    @JvmName("changeStrict") fun setStrict(on: Boolean) { strictProtection = on; prefs.edit().putBoolean("strict", on).apply() }
    @JvmName("changeHttpsOnly") fun setHttpsOnly(on: Boolean) { httpsOnly = on; prefs.edit().putBoolean("https_only", on).apply() }
    @JvmName("changeDns") fun setDns(id: String) { dns = id; prefs.edit().putString("dns", id).putBoolean("dns_ok", true).apply(); dnsExplained = true }
    @JvmName("changeAi") fun setAi(id: String) { ai = id; prefs.edit().putString("ai", id).apply() }
    @JvmName("changeRestoreTabs") fun setRestoreTabs(on: Boolean) { restoreTabs = on; prefs.edit().putBoolean("restore_tabs", on).apply() }
    @JvmName("changeCloseAfter") fun setCloseAfter(days: Int) { closeAfterDays = days; prefs.edit().putInt("close_after", days).apply() }
    @JvmName("changeTextScale") fun setTextScale(v: Float) { textScale = v.coerceIn(0.8f, 2f); prefs.edit().putFloat("text_scale", textScale).apply() }
    @JvmName("changeDarkPages") fun setDarkPages(on: Boolean) { darkPages = on; prefs.edit().putBoolean("dark_pages", on).apply() }
    @JvmName("changeSwipeTabs") fun setSwipeTabs(on: Boolean) { swipeTabs = on; prefs.edit().putBoolean("swipe_tabs", on).apply() }
    @JvmName("changeAskWhereToSave") fun setAskWhereToSave(on: Boolean) { askWhereToSave = on; prefs.edit().putBoolean("ask_save", on).apply() }
    @JvmName("changeBarAction") fun setBarAction(id: String) { barAction = id; prefs.edit().putString("bar_action", id).apply() }
    @JvmName("changeTabStrip") fun setTabStrip(v: String) { tabStrip = v; prefs.edit().putString("tab_strip", v).apply() }
    @JvmName("changeAddressBar") fun setAddressBar(v: String) { addressBar = v; prefs.edit().putString("address_bar", v).apply() }
    @JvmName("changeImages") fun setImages(on: Boolean) { images = on; prefs.edit().putBoolean("images", on).apply() }
    @JvmName("changeReaderSerif") fun setReaderSerif(on: Boolean) { readerSerif = on; prefs.edit().putBoolean("reader_serif", on).apply() }
    fun acceptTranslate() { translateOk = true; prefs.edit().putBoolean("translate_ok", true).apply() }

    val dnsService get() = DNS.firstOrNull { it.id == dns }
    val aiService get() = AIS.firstOrNull { it.id == ai }

    /** Porta le impostazioni nel motore: protezione, solo HTTPS, DNS cifrato, dimensione del testo. */
    fun applyTo(settings: GeckoRuntimeSettings) {
        settings.contentBlocking
            .setEnhancedTrackingProtectionLevel(if (strictProtection) ContentBlocking.EtpLevel.STRICT else ContentBlocking.EtpLevel.DEFAULT)
            .setAntiTracking(if (strictProtection) ContentBlocking.AntiTracking.STRICT else ContentBlocking.AntiTracking.DEFAULT)
        settings.setAllowInsecureConnections(if (httpsOnly) GeckoRuntimeSettings.HTTPS_ONLY else GeckoRuntimeSettings.ALLOW_ALL)
        val service = dnsService
        if (service == null) settings.setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_OFF)
        else {
            settings.setTrustedRecursiveResolverUri(service.uri)
            settings.setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_FIRST)
        }
        settings.setAutomaticFontSizeAdjustment(false)
        settings.setFontSizeFactor(textScale)
    }

    companion object {
        val DNS = listOf(
            DnsService("quad9", "Quad9", "Svizzera", "https://dns.quad9.net/dns-query", Jurisdiction.CH),
            DnsService("dns4eu", "DNS4EU", "Unione europea", "https://unfiltered.joindns4.eu/dns-query", Jurisdiction.EU),
            DnsService("mullvad", "Mullvad", "Svezia", "https://dns.mullvad.net/dns-query", Jurisdiction.EU),
        )
        val AIS = listOf(
            AiService("lechat", "Le Chat", "Mistral · Francia, UE · conserva le conversazioni", "https://chat.mistral.ai/chat?q=%s", Jurisdiction.EU, DataOut.Personal),
            AiService("duck", "Duck.ai", "DuckDuckGo · USA, anonima: i modelli non sanno chi sei", "https://duckduckgo.com/?q=%s&ia=chat", Jurisdiction.US, DataOut.Minimal),
            // Servizi chiusi: in fondo, due tacchette su dieci. Conservano le conversazioni e possono usarle per addestrare i modelli.
            // Claude compreso (anche chi scrive questo codice si valuta con la stessa regola): dall'ottobre 2025 le chat
            // servono ad addestrare i modelli se non lo spegni, e allora restano fino a cinque anni; spento, 30 giorni.
            AiService("claude", "Claude", "Anthropic · USA · addestra sulle chat se non lo spegni; le tiene fino a 5 anni", "https://claude.ai/new?q=%s", Jurisdiction.US, DataOut.Personal, closed = true),
            AiService("chatgpt", "ChatGPT", "OpenAI · USA · conserva le conversazioni", "https://chatgpt.com/?q=%s", Jurisdiction.US, DataOut.Personal, closed = true),
            AiService("copilot", "Copilot", "Microsoft · USA · conserva le conversazioni", "https://copilot.microsoft.com/?q=%s", Jurisdiction.US, DataOut.Personal, closed = true),
            AiService("gemini", "Gemini", "Google · USA · conserva le conversazioni", "https://gemini.google.com/app?q=%s", Jurisdiction.US, DataOut.Personal, closed = true),
        )
        val SCALES = listOf(0.9f, 1f, 1.1f, 1.25f, 1.5f)
    }
}

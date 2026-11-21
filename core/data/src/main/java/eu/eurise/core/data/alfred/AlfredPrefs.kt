package eu.eurise.core.data.alfred

import android.content.Context

/** Come Alfred si rivolge a chi usa il telefono. */
enum class Address { Name, Sir, Madam, None }

/** Le disposizioni di Alfred: appellativo, nome, lettura ad alta voce. Nel file «eurise_alfred», così entrano nel backup cifrato. */
class AlfredPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("eurise_alfred", Context.MODE_PRIVATE)

    var address: Address
        get() = prefs.getString("address", null)?.let { v -> Address.entries.firstOrNull { it.name == v } } ?: Address.Name
        set(value) = prefs.edit().putString("address", value.name).apply()

    /** Il nome con cui Alfred si rivolge a chi usa il telefono: lo scrive la persona (lo chiede lui la prima volta). */
    var name: String
        get() = prefs.getString("name", "").orEmpty()
        set(value) = prefs.edit().putString("name", value.trim()).apply()

    var readAloud: Boolean
        get() = prefs.getBoolean("read_aloud", false)
        set(value) = prefs.edit().putBoolean("read_aloud", value).apply()

    /**
     * Domande libere subito online (richiesta del proprietario, 14 novembre): Alfred apre direttamente il servizio AI
     * preferito, con la domanda ripulita, invece di mostrare la scheda. Acceso di base; la prima volta resta comunque la
     * spiegazione del centro privacy, perché il servizio online è spento finché non lo si accende.
     */
    /**
     * Chi risponde alle domande libere (tavola AlfCervello): «phone» prima Eurise AI sul telefono e, se non c'è, il
     * servizio online; «online» subito il servizio online; «phone_only» solo il telefono.
     */
    var answers: String
        get() = prefs.getString("answers", "phone") ?: "phone"
        set(value) = prefs.edit().putString("answers", value).apply()

    var freeOnline: Boolean
        get() = prefs.getBoolean("free_online", true)
        set(value) = prefs.edit().putBoolean("free_online", value).apply()

    /** Alfred dalla schermata di blocco (tavola AlfAvvio): comandi semplici senza sbloccare, il resto chiede lo sblocco. */
    var lockScreen: Boolean
        get() = prefs.getBoolean("lock_screen", true)
        set(value) = prefs.edit().putBoolean("lock_screen", value).apply()

    /** L'app di musica per la perla del punto della giornata (pacchetto); vuoto = non ancora scelta. */
    var musicBriefing: String
        get() = prefs.getString("music_briefing", "").orEmpty()
        set(value) = prefs.edit().putString("music_briefing", value).apply()

    /** L'app di musica per la sveglia di Alfred: scelta a parte, come deciso con il proprietario. */
    var musicAlarm: String
        get() = prefs.getString("music_alarm", "").orEmpty()
        set(value) = prefs.edit().putString("music_alarm", value).apply()
}

/** L'appellativo pronto per le frasi di Alfred: «, Giulia», «, signore» o niente (nome non ancora detto). */
fun addressSuffix(prefs: AlfredPrefs): String = when (prefs.address) {
    Address.None -> ""
    Address.Sir -> ", signore"
    Address.Madam -> ", signora"
    Address.Name -> prefs.name.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""
}


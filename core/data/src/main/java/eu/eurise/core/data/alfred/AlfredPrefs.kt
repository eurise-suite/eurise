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


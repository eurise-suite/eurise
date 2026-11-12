package eu.eurise.feature.assistant

import android.content.Context
import android.provider.ContactsContract
import eu.eurise.core.contacts.Contacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Come Alfred si rivolge a chi usa il telefono. */
enum class Address { Name, Sir, Madam, None }

/** Le disposizioni di Alfred: appellativo, nome, lettura ad alta voce. Poche chiavi, lette quando serve. */
class AlfredPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("alfred", Context.MODE_PRIVATE)

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
}

/**
/** L'appellativo pronto per le frasi di Alfred: «, Giulia», «, signore» o niente (nome non ancora detto). */
fun addressSuffix(prefs: AlfredPrefs): String = when (prefs.address) {
    Address.None -> ""
    Address.Sir -> ", signore"
    Address.Madam -> ", signora"
    Address.Name -> prefs.name.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""
}

/** Il primo nome della scheda del proprietario in Rubrica: solo un suggerimento quando Alfred chiede come chiamarla. */
suspend fun ownFirstName(context: Context): String? = withContext(Dispatchers.IO) {
    if (!Contacts.canRead(context)) return@withContext null
    runCatching {
        context.contentResolver.query(
            ContactsContract.Profile.CONTENT_URI, arrayOf(ContactsContract.Profile.DISPLAY_NAME_PRIMARY), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()?.trim()?.split(' ')?.firstOrNull()?.takeIf { it.isNotBlank() }
}

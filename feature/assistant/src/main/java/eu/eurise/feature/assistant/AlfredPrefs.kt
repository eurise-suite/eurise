package eu.eurise.feature.assistant

import android.content.Context
import android.provider.ContactsContract
import eu.eurise.core.contacts.Contacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Il primo nome della scheda del proprietario in Rubrica: solo un suggerimento quando Alfred chiede come chiamarla. */
suspend fun ownFirstName(context: Context): String? = withContext(Dispatchers.IO) {
    if (!Contacts.canRead(context)) return@withContext null
    runCatching {
        context.contentResolver.query(
            ContactsContract.Profile.CONTENT_URI, arrayOf(ContactsContract.Profile.DISPLAY_NAME_PRIMARY), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()?.trim()?.split(' ')?.firstOrNull()?.takeIf { it.isNotBlank() }
}

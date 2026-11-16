package eu.eurise.core.links

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.data.LinkRepository
import eu.eurise.core.data.model.LinkRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Un contatto proposto: [detail] è l'azienda o il primo numero, per distinguere due omonimi. */
data class PersonHit(val lookupKey: String, val name: String, val detail: String)

/**
 * Le persone scelte dai suggerimenti in un campo (nome intero → contatto). Al salvataggio [refsIn] dice quali sono
 * rimaste nel testo, e [linkAll] le collega a ciò che è nato: le si ritrova nella scheda della persona.
 */
@Stable
class PickedPeople {
    private val map = mutableStateMapOf<String, LinkRef>()

    fun add(name: String, ref: LinkRef) { map[name] = ref }

    fun refsIn(text: String): List<LinkRef> = MentionQuery.stillThere(text, map.keys.toList()).mapNotNull { map[it] }.distinct()

    /** Come le vuole il + unico ([CaptureStart.people]): nome breve in minuscolo → contatto. */
    fun byFirstName(text: String): Map<String, LinkRef> =
        MentionQuery.stillThere(text, map.keys.toList()).mapNotNull { n -> map[n]?.let { MentionQuery.firstName(n).lowercase() to it } }.toMap()

    suspend fun linkAll(links: LinkRepository, created: LinkRef, text: String) {
        refsIn(text).forEach { links.link(created, it) }
    }
}

@Composable
fun rememberPickedPeople(): PickedPeople = remember { PickedPeople() }

/** Cerca nella rubrica del telefono, senza uscire dal telefono. Vuoto senza il permesso. */
suspend fun searchPeople(context: Context, query: String, limit: Int = 3): List<PersonHit> = withContext(Dispatchers.IO) {
    val q = query.trim()
    if (q.isEmpty() || context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return@withContext emptyList()
    }
    runCatching {
        val found = ArrayList<Triple<Long, String, String>>()
        context.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q)),
            arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            null, null,
            // Prima i preferiti e chi senti più spesso.
            "${ContactsContract.Contacts.STARRED} DESC, ${ContactsContract.Contacts.LAST_TIME_CONTACTED} DESC",
        )?.use { c ->
            while (c.moveToNext() && found.size < limit) {
                val name = c.getString(2)?.trim().orEmpty()
                if (name.isNotEmpty()) found += Triple(c.getLong(0), c.getString(1).orEmpty(), name)
            }
        }
        found.map { (id, key, name) -> PersonHit(key, name, detailOf(context, id)) }
    }.getOrDefault(emptyList())
}

/** Un contatto con un indirizzo email (per invitare o condividere). */
data class MailContact(val name: String, val email: String)

/** I contatti del telefono con un'email che somiglia a [query] (nome o indirizzo). Vuoto senza il permesso. */
suspend fun searchEmails(context: Context, query: String, limit: Int = 3): List<MailContact> = withContext(Dispatchers.IO) {
    val q = query.trim()
    if (q.length < 2 || context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return@withContext emptyList()
    }
    runCatching {
        context.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.CommonDataKinds.Email.CONTENT_FILTER_URI, Uri.encode(q)),
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY, ContactsContract.CommonDataKinds.Email.ADDRESS),
            null, null, null,
        )?.use { c -> buildList { while (c.moveToNext() && size < limit * 2) add(MailContact(c.getString(0).orEmpty(), c.getString(1).orEmpty())) } }
            .orEmpty().filter { it.email.isNotBlank() }.distinctBy { it.email.lowercase() }.take(limit)
    }.getOrDefault(emptyList())
}

private fun detailOf(context: Context, id: Long): String {
    var company = ""
    var phone = ""
    context.contentResolver.query(
        ContactsContract.Data.CONTENT_URI,
        arrayOf(ContactsContract.Data.MIMETYPE, ContactsContract.Data.DATA1),
        "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} IN (?, ?)",
        arrayOf(id.toString(), ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
        "${ContactsContract.Data.IS_PRIMARY} DESC",
    )?.use { c ->
        while (c.moveToNext()) {
            val v = c.getString(1)?.trim().orEmpty()
            when (c.getString(0)) {
                ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> if (company.isEmpty()) company = v
                else -> if (phone.isEmpty()) phone = v
            }
        }
    }
    return company.ifEmpty { phone }
}

/**
 * I contatti del telefono proposti mentre si scrive il nome di una persona (tavola AgeScrivi): con «@» ovunque, e
 * con [byName] anche scrivendo il nome dopo «con» (appuntamenti). Un tocco mette il nome nel testo e ricorda il
 * contatto in [picked]. Senza il permesso della rubrica compare una riga per darlo. Tutto resta sul telefono.
 *
 * @param keepAt dopo la chiocciola resta «@Nome» (attività); altrimenti il nome intero (appuntamenti).
 * @param onText riceve il testo nuovo e la posizione del cursore.
 */
@Composable
fun ContactSuggestions(
    text: String,
    caret: Int,
    onText: (String, Int) -> Unit,
    picked: PickedPeople,
    modifier: Modifier = Modifier,
    keepAt: Boolean = true,
    byName: Boolean = false,
) {
    val context = LocalContext.current
    val hit = remember(text, caret, byName) { MentionQuery.find(text, caret, byName) }
    var allowed by remember { mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    var hits by remember { mutableStateOf(emptyList<PersonHit>()) }
    LaunchedEffect(hit?.query, allowed) {
        val q = hit?.query
        if (q == null || !allowed) { hits = emptyList(); return@LaunchedEffect }
        delay(150)
        hits = searchPeople(context, q)
    }
    if (hit == null) return
    // Il nome già scelto non si ripropone.
    if (hits.size == 1 && hits[0].name.equals(hit.query, ignoreCase = true)) return
    val scheme = MaterialTheme.colorScheme
    if (!allowed) {
        if (!hit.at) return
        Surface(shape = RoundedCornerShape(18.dp), color = scheme.surfaceContainerLow, modifier = modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.people_allow),
                fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = scheme.primary,
                modifier = Modifier.clickable { permission.launch(Manifest.permission.READ_CONTACTS) }.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
        return
    }
    if (hits.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = scheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.people_from_phone, (if (hit.at) "@" else "") + hit.query),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
            )
            hits.forEachIndexed { i, p ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (i == 0) scheme.primaryContainer else Color.Transparent)
                        .clickable {
                            val (out, at) = MentionQuery.apply(text, hit, p.name, keepAt)
                            picked.add(p.name, LinkRef.contact(p.lookupKey))
                            onText(out, at)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Initials(p.name)
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (p.detail.isNotBlank()) {
                            Text(p.detail, fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private val AvatarTints = listOf(Color(0xFF4F6B5A), Color(0xFFA8742A), Color(0xFF5B6FA8), Color(0xFF8C2F39))

@Composable
private fun Initials(name: String) {
    val letters = name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(AvatarTints[(name.hashCode() and 0x7fffffff) % AvatarTints.size]),
        contentAlignment = Alignment.Center,
    ) { Text(letters, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

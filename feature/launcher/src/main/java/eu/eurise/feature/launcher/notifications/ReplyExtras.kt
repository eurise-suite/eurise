package eu.eurise.feature.launcher.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import eu.eurise.core.contacts.Contact
import eu.eurise.core.contacts.Contacts
import eu.eurise.core.contacts.Reach
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.feature.launcher.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Menu della pressione lunga su un fumetto (tavola BolRispondi): reazioni rapide mandate come emoji (dalle notifiche
 * la reazione vera non si può mandare), «Rispondi a questa frase», Copia, In Da fare, In Note.
 */
@Composable
internal fun MessageActions(
    canSend: Boolean,
    onReact: (String) -> Unit,
    onQuote: () -> Unit,
    onCopy: () -> Unit,
    onTask: () -> Unit,
    onNote: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(scheme.surface).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (canSend) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ReplyText.REACTIONS.forEach { e ->
                    val label = stringResource(R.string.reply_react, e)
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = label) { onReact(e) }
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) { Text(e, fontSize = 24.sp) }
                }
            }
            MenuLine(EuriseIcons.reply(2f), stringResource(R.string.reply_quote), strong = true, onClick = onQuote)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallAction(EuriseIcons.copy(2f), stringResource(R.string.reply_copy), onCopy, Modifier.weight(1f))
            SmallAction(EuriseIcons.task(2f), stringResource(R.string.comms_todo), onTask, Modifier.weight(1f))
            SmallAction(EuriseIcons.note(2f), stringResource(R.string.reply_to_notes), onNote, Modifier.weight(1f))
        }
        if (canSend) {
            Text(stringResource(R.string.reply_react_hint), fontSize = 11.5.sp, lineHeight = 15.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
        }
    }
}

@Composable
private fun MenuLine(icon: ImageVector, label: String, strong: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp))
            .background(if (strong) scheme.onSurface.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(18.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** La citazione sopra il campo della risposta, con la × per toglierla. */
@Composable
internal fun QuoteStrip(quote: String, onRemove: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(scheme.surface)
            .padding(start = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(36.dp).background(scheme.primary))
        Text("«$quote»", fontSize = 12.5.sp, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 6.dp))
        val remove = stringResource(R.string.reply_quote_remove)
        Box(Modifier.size(36.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = remove, onClick = onRemove).semantics { contentDescription = remove }, contentAlignment = Alignment.Center) {
            Icon(EuriseIcons.close(2f), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * «Allega a …» (tavola BolAllega). Contatto, posizione e disponibilità entrano come testo nella risposta ([onText]);
 * foto, documenti e scatti si aprono nell'app della conversazione, in WhatsApp già nella chat giusta quando la rubrica
 * conosce il numero. Sotto, i contatti nominati nella conversazione.
 */
@Composable
internal fun AttachSheet(
    message: MessageItem,
    availability: suspend () -> String?,
    onText: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val who = message.conversation.ifBlank { message.title }
    val failed = stringResource(R.string.reply_failed)
    val noPlace = stringResource(R.string.reply_no_position)
    val noFree = stringResource(R.string.reply_no_free)
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    LaunchedEffect(Unit) { contacts = runCatching { Contacts.all(context) }.getOrDefault(emptyList()) }
    val texts = remember(message.key) { message.history.map { it.second }.ifEmpty { listOf(message.text) } }
    val suggested = remember(contacts, texts) {
        val names = ReplyText.mentionedContacts(texts, contacts.map { it.name }, who)
        contacts.filter { it.name in names }
    }

    fun sendContact(c: Contact) = scope.launch {
        val full = Contacts.detail(context, c.id) ?: c
        onText(ReplyText.contact(full.name, full.phones.map { it.value }, full.emails.map { it.value }))
        onClose()
    }

    fun share(uri: Uri, mime: String) = scope.launch {
        val ok = withContext(Dispatchers.IO) { ReplyShare.share(context, message, uri, mime, contacts) }
        if (!ok) Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
        onClose()
    }

    // Si sceglie un numero (non il contatto intero): Android concede la lettura di quella riga anche senza il permesso
    // della rubrica.
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val row = runCatching {
            context.contentResolver.query(
                uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null,
            )?.use { if (it.moveToFirst()) (it.getString(0).orEmpty() to it.getString(1).orEmpty()) else null }
        }.getOrNull() ?: return@rememberLauncherForActivityResult
        onText(ReplyText.contact(row.first, listOf(row.second)))
        onClose()
    }
    val pickDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { share(it, context.contentResolver.getType(it) ?: "application/octet-stream") }
    }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { share(it, context.contentResolver.getType(it) ?: "image/*") }
    }
    var shot by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = shot
        if (ok && uri != null) share(uri, "image/jpeg")
    }
    fun position() {
        val place = ReplyShare.lastPosition(context)
        if (place == null) Toast.makeText(context, noPlace, Toast.LENGTH_LONG).show()
        else { onText(ReplyText.position(place.latitude, place.longitude)); onClose() }
    }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) position()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.26f)).pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(scheme.surfaceContainer)
                .pointerInput(Unit) { detectTapGestures { } }.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(scheme.onSurface.copy(alpha = 0.2f)))
            Text(stringResource(R.string.reply_attach_to, who), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            val tiles = listOf(
                Triple(EuriseIcons.person(2f), R.string.reply_attach_contact, CalendarTint.Salvia),
                Triple(EuriseIcons.document(2f), R.string.reply_attach_document, CalendarTint.Ottone),
                Triple(EuriseIcons.image(2f), R.string.reply_attach_gallery, CalendarTint.Terracotta),
                Triple(EuriseIcons.camera(2f), R.string.reply_attach_camera, CalendarTint.BluNotte),
                Triple(EuriseIcons.place(2f), R.string.reply_attach_position, CalendarTint.Prugna),
                Triple(EuriseIcons.calendar(2f), R.string.reply_attach_free, CalendarTint.Grafite),
            )
            tiles.chunked(3).forEachIndexed { r, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { i, (icon, label, tint) ->
                        AttachTile(icon, stringResource(label), tint, Modifier.weight(1f)) {
                            when (r * 3 + i) {
                                0 -> pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                                1 -> pickDocument.launch(arrayOf("*/*"))
                                2 -> pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                                3 -> { val u = ReplyShare.newShot(context); shot = u; takePicture.launch(u) }
                                4 -> if (ReplyShare.canLocate(context)) position()
                                     else askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                else -> scope.launch {
                                    val s = availability()
                                    if (s == null) Toast.makeText(context, noFree, Toast.LENGTH_SHORT).show() else { onText(s); onClose() }
                                }
                            }
                        }
                    }
                }
            }
            if (suggested.isNotEmpty()) {
                Text(stringResource(R.string.reply_attach_suggested).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surface).padding(horizontal = 14.dp)) {
                    suggested.forEachIndexed { i, c ->
                        if (i > 0) HorizontalDivider(thickness = 1.dp, color = scheme.onSurface.copy(alpha = 0.07f))
                        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.reply_attach_contact), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                            }
                            Box(
                                Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(scheme.primary)
                                    .clickable(role = Role.Button) { sendContact(c) }.padding(horizontal = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(stringResource(R.string.reply_attach), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimary) }
                        }
                    }
                }
            }
            Text(stringResource(R.string.reply_attach_note), fontSize = 12.sp, lineHeight = 17.sp, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AttachTile(icon: ImageVector, label: String, tint: CalendarTint, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.red + scheme.surface.green + scheme.surface.blue < 1.5f
    val ink = if (dark) tint.dark else tint.light
    Column(
        modifier.height(88.dp).clip(RoundedCornerShape(18.dp)).background(scheme.surface).clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(lerp(ink, scheme.surface, 0.82f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}

/** Condivisioni e posizione per gli allegati delle risposte. */
internal object ReplyShare {

    private const val WHATSAPP = "com.whatsapp"
    private const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    fun canLocate(context: Context) =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** L'ultima posizione nota, la più recente tra i fornitori del telefono; nessuna richiesta in rete. */
    @SuppressLint("MissingPermission")
    fun lastPosition(context: Context): Location? {
        if (!canLocate(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        return lm.getProviders(true).mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
    }

    /** Un file vuoto nella cache per lo scatto della fotocamera. */
    fun newShot(context: Context): Uri {
        val dir = File(context.cacheDir, "reply").apply { mkdirs() }
        val file = File(dir, "foto-${System.currentTimeMillis() / 1000}.jpg")
        return FileProvider.getUriForFile(context, context.packageName + ".replyfiles", file)
    }

    /**
     * Copia l'allegato nella cache (così il permesso di lettura non dipende dall'app da cui viene) e lo apre nell'app
     * della conversazione. In WhatsApp va direttamente nella chat se la rubrica conosce il numero della persona.
     */
    suspend fun share(context: Context, message: MessageItem, source: Uri, mime: String, contacts: List<Contact>): Boolean = runCatching {
        val uri = if (source.authority == context.packageName + ".replyfiles") source else copy(context, source)
        val intent = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.clipData = ClipData.newRawUri("", uri)
        val pkg = message.packageName
        if (Reach.installed(context, pkg)) intent.setPackage(pkg)
        if (pkg == WHATSAPP || pkg == WHATSAPP_BUSINESS) {
            val who = message.conversation.ifBlank { message.title }
            val match = contacts.firstOrNull { it.name.equals(who, ignoreCase = true) }
            val number = match?.let { Contacts.detail(context, it.id) }?.phones?.firstOrNull()?.value
            if (number != null) intent.putExtra("jid", Reach.international(number).trimStart('+') + "@s.whatsapp.net")
        }
        context.startActivity(if (intent.`package` == null) Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else intent)
        true
    }.getOrDefault(false)

    private fun copy(context: Context, source: Uri): Uri {
        val name = context.contentResolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }?.replace('/', '_') ?: "allegato"
        val dir = File(context.cacheDir, "reply").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L }?.forEach { it.delete() }
        val file = File(dir, name)
        context.contentResolver.openInputStream(source)!!.use { input -> file.outputStream().use { input.copyTo(it) } }
        return FileProvider.getUriForFile(context, context.packageName + ".replyfiles", file)
    }

    fun copyText(context: Context, text: String) {
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("", text))
    }
}

/** Il FileProvider delle risposte: una classe propria, perché nell'APK c'è già quello di Drive. */
class ReplyFileProvider : FileProvider()

package eu.eurise.feature.people

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.eurise.core.data.EuriseData
import eu.eurise.core.data.model.LinkRef
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.GroupCard
import eu.eurise.core.designsystem.component.SectionLabel
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.links.LinkItem
import eu.eurise.core.links.LinkResolver
import eu.eurise.core.links.LinkRow
import eu.eurise.core.links.LinkedSection
import eu.eurise.core.links.Resolved
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val UPCOMING_LIMIT = 5

/**
 * Scheda Persona: i modi per raggiungerla, gli impegni in comune e tutto ciò che è collegato a lei nella suite.
 *
 * @param onOpen apre una cosa collegata.
 * @param onInvite prepara un appuntamento con questa persona.
 */
@Composable
fun PersonRoute(
    lookupKey: String,
    data: EuriseData,
    onBack: () -> Unit,
    onOpen: (LinkItem) -> Unit,
    onInvite: (name: String, contact: LinkRef) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val source = remember { PersonSource(context) }
    val resolver = remember(data) { LinkResolver(context, data) }
    val self = remember(lookupKey) { LinkRef.contact(lookupKey) }
    var grants by remember { androidx.compose.runtime.mutableStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { grants++ }
    val allowed = remember(grants) { context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED }

    // Primo elemento: la lettura è finita; serve a non dire "non trovato" mentre si sta ancora leggendo.
    val load by produceState(false to (null as PersonInfo?), lookupKey, allowed) { if (allowed) value = true to source.load(lookupKey) }
    val person = load.second
    val loaded = load.first
    val links by remember(self) { data.links.observe(self) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val dates by data.dates.dates.collectAsStateWithLifecycle()
    val linkedEvents = links.map { it.other }.filter { it.kind == LinkRef.Kind.Event }.toSet()
    val upcoming by produceState(emptyList<LinkItem>(), person, linkedEvents) {
        val emails = person?.emails.orEmpty()
        val today = LocalDate.now().atStartOfDay()
        value = if (emails.isEmpty() || !data.calendar.hasPermission()) emptyList() else
            source.eventsWith(emails).map { LinkRef.event(it) }.filter { it !in linkedEvents }
                .mapNotNull { (resolver.resolve(it) as? Resolved.Found)?.item }
                .filter { it.at?.isBefore(today) == false }
                .sortedBy { it.at }
                .take(UPCOMING_LIMIT)
    }

    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            Icon(EuriseIcons.chevronLeft(), null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(2.dp))
            Text(stringResource(R.string.person_back), fontWeight = FontWeight.SemiBold)
        }

        val info = person
        when {
            !allowed -> Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.person_no_permission), fontSize = 15.sp)
                Button(onClick = { permission.launch(Manifest.permission.READ_CONTACTS) }) { Text(stringResource(R.string.person_grant)) }
            }
            info == null -> if (loaded) {
                Text(stringResource(R.string.person_missing), fontSize = 15.sp, modifier = Modifier.padding(horizontal = 20.dp))
            }
            else -> {
                val birthday = dates.firstOrNull {
                    it.kind == eu.eurise.core.data.dates.ImportantDate.Kind.Birthday && it.name.equals(info.name, ignoreCase = true)
                }?.let { java.time.MonthDay.of(it.month, it.day) } ?: info.birthday
                Header(info, birthday)
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val phone = info.phones.firstOrNull()
                    val email = info.emails.firstOrNull()
                    ActionTile(EuriseIcons.phone(), R.string.person_call, phone != null, Modifier.weight(1f)) { phone?.let(source::call) }
                    ActionTile(EuriseIcons.message(), R.string.person_message, phone != null, Modifier.weight(1f)) { phone?.let(source::message) }
                    ActionTile(EuriseIcons.mail(), R.string.person_email, email != null, Modifier.weight(1f)) { email?.let(source::email) }
                    ActionTile(EuriseIcons.calendar(), R.string.person_invite, true, Modifier.weight(1f)) { onInvite(info.name, self) }
                }
                if (upcoming.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel(stringResource(R.string.person_upcoming))
                        GroupCard {
                            upcoming.forEachIndexed { i, item ->
                                if (i > 0) CardDivider()
                                LinkRow(item, onClick = { onOpen(item) })
                            }
                        }
                    }
                }
                LinkedSection(from = self, title = info.name, data = data, onOpen = onOpen)
                TextButton(onClick = { source.openInContacts(lookupKey) }, modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)) {
                    Text(stringResource(R.string.person_open_contacts), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun Header(info: PersonInfo, birthday: java.time.MonthDay?) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(scheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            if (info.photo != null) {
                Image(info.photo.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(info.name.take(1).uppercase(), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = scheme.onTertiaryContainer)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(info.name, fontSize = 26.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, color = scheme.onSurface)
            if (birthday != null) {
                val day = birthday.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))
                Text(stringResource(R.string.person_birthday, day), fontSize = 13.sp, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: Int, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .heightIn(min = 62.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerLow)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(5.dp))
        Text(stringResource(label), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurface, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
    }
}

package eu.eurise.feature.scanner

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import eu.eurise.core.data.EuriseData
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.FilterPill
import eu.eurise.core.designsystem.icon.EuriseIcons
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** I codici letti, sul telefono, gli ultimi 30; i codici 2FA non si conservano mai. */
internal class QrHistory(context: Context) {
    private val prefs = context.getSharedPreferences("eurise_qr", Context.MODE_PRIVATE)

    fun load(): List<Pair<String, Long>> = runCatching {
        val a = JSONArray(prefs.getString("history", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("t") to o.getLong("at") } }
    }.getOrDefault(emptyList())

    fun add(text: String) {
        if (QrContent.parse(text) is QrContent.Otp) return
        val list = listOf(text to System.currentTimeMillis()) + load().filter { it.first != text }
        prefs.edit().putString("history", JSONArray(list.take(30).map { JSONObject().put("t", it.first).put("at", it.second) }).toString()).apply()
    }

    fun clear() = prefs.edit().remove("history").apply()
}

internal fun Context.copy(text: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("QR", text))
    Toast.makeText(this, R.string.qr_copied, Toast.LENGTH_SHORT).show()
}

private fun Context.start(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.scan_qr_no_app, Toast.LENGTH_SHORT).show()
    }
}

private fun euro(cents: Long) = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.ITALY).format(cents / 100.0)

/** Breve descrizione di un codice, per la cronologia. */
@Composable
internal fun qrSummary(c: QrContent): Pair<ImageVector, String> = when (c) {
    is QrContent.Link -> EuriseIcons.link() to c.host
    is QrContent.Wifi -> EuriseIcons.wifi() to stringResource(R.string.qr_wifi_named, c.ssid)
    is QrContent.PagoPa -> EuriseIcons.euro() to stringResource(R.string.qr_pagopa_short, euro(c.amountCents))
    is QrContent.Sepa -> EuriseIcons.euro() to c.name.ifBlank { c.iban }
    is QrContent.Contact -> EuriseIcons.person() to c.name.ifBlank { c.phones.firstOrNull() ?: c.emails.firstOrNull().orEmpty() }
    is QrContent.Event -> EuriseIcons.calendar() to c.title
    is QrContent.Email -> EuriseIcons.mail() to c.address
    is QrContent.Phone -> EuriseIcons.phone() to c.number
    is QrContent.Sms -> EuriseIcons.phone() to c.number
    is QrContent.Geo -> EuriseIcons.map() to "${c.lat}, ${c.lon}"
    is QrContent.Otp -> EuriseIcons.lock() to stringResource(R.string.qr_otp_title)
    is QrContent.Text -> EuriseIcons.note() to c.raw.lineSequence().first().take(60)
    is QrContent.Ponte -> EuriseIcons.link() to c.name
}

@Composable
internal fun Field(label: String, value: String, copy: Boolean = true) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
        if (copy) Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer).clickable { context.copy(value) },
            contentAlignment = Alignment.Center,
        ) { Icon(EuriseIcons.copy(), stringResource(R.string.qr_copy_field, label), tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(17.dp)) }
    }
}

@Composable
internal fun Group(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) { content() }
}

@Composable
internal fun Primary(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp)) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable
internal fun Secondary(label: String, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer) }
}

/**
 * Il contenuto di un codice, capito: per un avviso pagoPA importo e codici con "Aggiungi a Da fare", per un link il
 * dominio in evidenza prima di aprirlo, per il Wi-Fi il collegamento, per un contatto il salvataggio in Rubrica.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QrSheet(text: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val content = remember(text) { QrContent.parse(text) }
    LaunchedEffect(text) { QrHistory(context).add(text) }
    val (icon, _) = qrSummary(content)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = scheme.background) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(scheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = scheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                }
                Text(
                    stringResource(
                        when (content) {
                            is QrContent.Link -> R.string.qr_link_title
                            is QrContent.Wifi -> R.string.qr_wifi_title
                            is QrContent.PagoPa -> R.string.qr_pagopa_title
                            is QrContent.Sepa -> R.string.qr_sepa_title
                            is QrContent.Contact -> R.string.qr_contact_title
                            is QrContent.Event -> R.string.qr_event_title
                            is QrContent.Email -> R.string.qr_email_title
                            is QrContent.Phone -> R.string.qr_phone_title
                            is QrContent.Sms -> R.string.qr_sms_title
                            is QrContent.Geo -> R.string.qr_geo_title
                            is QrContent.Otp -> R.string.qr_otp_title
                            is QrContent.Text -> R.string.qr_text_title
                            is QrContent.Ponte -> R.string.qr_ponte_title
                        },
                    ),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant,
                )
            }
            when (content) {
                is QrContent.Link -> {
                    Text(content.host, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Text(content.raw, fontSize = 13.sp, color = scheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(if (content.secure) R.string.qr_link_check else R.string.qr_link_insecure),
                        fontSize = 13.sp, color = if (content.secure) scheme.onSurfaceVariant else scheme.error,
                    )
                    Primary(stringResource(R.string.qr_open_site, content.host)) { context.start(Intent(Intent.ACTION_VIEW, Uri.parse(content.raw))); onClose() }
                    Secondary(stringResource(R.string.qr_copy_link)) { context.copy(content.raw) }
                }
                is QrContent.Wifi -> {
                    Group {
                        Field(stringResource(R.string.qr_wifi_network), content.ssid)
                        if (content.password.isNotEmpty()) { CardDivider(); Field(stringResource(R.string.qr_wifi_password), content.password) }
                        CardDivider(); Field(stringResource(R.string.qr_wifi_security), content.security, copy = false)
                    }
                    Primary(stringResource(R.string.qr_wifi_connect)) { connectWifi(context, content); onClose() }
                }
                is QrContent.PagoPa -> PagoPaPart(content, onClose)
                is QrContent.Sepa -> {
                    content.amount?.let { Text("${it.replace('.', ',')} €", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold) }
                    Group {
                        Field(stringResource(R.string.qr_sepa_name), content.name)
                        CardDivider(); Field("IBAN", content.iban)
                        if (content.reference.isNotBlank()) { CardDivider(); Field(stringResource(R.string.qr_sepa_reference), content.reference) }
                    }
                    Primary(stringResource(R.string.qr_add_todo)) {
                        scope.launch {
                            EuriseData.create(context).tasks.add(
                                context.getString(R.string.qr_sepa_task, content.name, content.amount?.replace('.', ',') ?: ""),
                                due = LocalDate.now().plusDays(7),
                                notes = "IBAN ${content.iban}\n${content.reference}",
                            )
                            Toast.makeText(context, R.string.qr_added_todo, Toast.LENGTH_SHORT).show()
                            onClose()
                        }
                    }
                    Secondary(stringResource(R.string.qr_copy_all)) { context.copy("${content.name}\n${content.iban}\n${content.amount.orEmpty()}\n${content.reference}".trim()) }
                }
                is QrContent.Contact -> {
                    Text(content.name, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Group {
                        content.phones.forEachIndexed { i, p -> if (i > 0) CardDivider(); Field(stringResource(R.string.qr_phone_title), p) }
                        content.emails.forEach { e -> CardDivider(); Field("Email", e) }
                        if (content.org.isNotBlank()) { CardDivider(); Field(stringResource(R.string.qr_contact_org), content.org, copy = false) }
                    }
                    Primary(stringResource(R.string.qr_contact_save)) {
                        context.start(
                            Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
                                .putExtra(ContactsContract.Intents.Insert.NAME, content.name)
                                .putExtra(ContactsContract.Intents.Insert.PHONE, content.phones.firstOrNull())
                                .putExtra(ContactsContract.Intents.Insert.EMAIL, content.emails.firstOrNull())
                                .putExtra(ContactsContract.Intents.Insert.COMPANY, content.org),
                        )
                        onClose()
                    }
                }
                is QrContent.Event -> {
                    Text(content.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    val start = icsTime(content.start)
                    Group {
                        start?.let { Field(stringResource(R.string.qr_event_when), it.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy · H:mm")), copy = false) }
                        if (content.location.isNotBlank()) { CardDivider(); Field(stringResource(R.string.qr_event_where), content.location) }
                    }
                    Primary(stringResource(R.string.qr_event_add)) {
                        val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                            .putExtra(CalendarContract.Events.TITLE, content.title)
                            .putExtra(CalendarContract.Events.EVENT_LOCATION, content.location)
                        start?.let { i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
                        icsTime(content.end)?.let { i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
                        context.start(i)
                        onClose()
                    }
                }
                is QrContent.Email -> {
                    Group { Field("Email", content.address); if (content.subject.isNotBlank()) { CardDivider(); Field(stringResource(R.string.qr_email_subject), content.subject, copy = false) } }
                    Primary(stringResource(R.string.qr_email_write)) { context.start(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + content.address)).putExtra(Intent.EXTRA_SUBJECT, content.subject)); onClose() }
                }
                is QrContent.Phone -> {
                    Group { Field(stringResource(R.string.qr_phone_title), content.number) }
                    Primary(stringResource(R.string.qr_phone_call)) { context.start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(content.number)))); onClose() }
                }
                is QrContent.Sms -> {
                    Group { Field(stringResource(R.string.qr_phone_title), content.number); if (content.body.isNotBlank()) { CardDivider(); Field(stringResource(R.string.qr_text_title), content.body) } }
                    Primary(stringResource(R.string.qr_sms_write)) { context.start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(content.number))).putExtra("sms_body", content.body)); onClose() }
                }
                is QrContent.Geo -> {
                    Group { Field(stringResource(R.string.qr_geo_title), "${content.lat}, ${content.lon}") }
                    Primary(stringResource(R.string.qr_geo_open)) { context.start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${content.lat},${content.lon}"))); onClose() }
                }
                is QrContent.Otp -> {
                    Text(stringResource(R.string.scan_qr_2fa), fontSize = 14.sp)
                    Primary(stringResource(R.string.qr_otp_open)) { context.start(Intent(Intent.ACTION_VIEW, Uri.parse(content.raw))); onClose() }
                }
                is QrContent.Ponte -> {
                    Text(content.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text(stringResource(R.string.qr_ponte_note), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                    Primary(stringResource(R.string.qr_ponte_pair)) {
                        // Il Ponte sta in feature/ponte: lo apre l'app con un'azione esplicita (solo dentro l'APK).
                        context.start(Intent("eu.eurise.action.PONTE_PAIR").setPackage(context.packageName).putExtra(Intent.EXTRA_TEXT, content.raw))
                        onClose()
                    }
                }
                is QrContent.Text -> {
                    val boarding = remember(content.raw) { eu.eurise.core.data.trips.Bcbp.parse(content.raw, java.time.LocalDate.now()) }
                    if (boarding != null) {
                        // Carta d'imbarco: si prepara il viaggio (calendario, sveglia, fuso, modalità Viaggio).
                        val leg = boarding.legs.first()
                        Group { Field(stringResource(R.string.qr_boarding_title), "${leg.number} · ${leg.from} → ${leg.to}") }
                        Primary(stringResource(R.string.qr_boarding_trip)) {
                            val intent = Intent().setClassName(context.packageName, "eu.eurise.hub.MainActivity")
                                .putExtra("eu.eurise.extra.SECTION", "trip:raw:" + Uri.encode(content.raw))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            context.start(intent)
                            onClose()
                        }
                        return@Column
                    }
                    Text(content.raw, fontSize = 15.sp)
                    Primary(stringResource(R.string.qr_text_note)) {
                        scope.launch {
                            EuriseData.create(context).notes.save(null, content.raw.lineSequence().first().take(60), content.raw)
                            Toast.makeText(context, R.string.qr_saved_note, Toast.LENGTH_SHORT).show()
                            onClose()
                        }
                    }
                    Secondary(stringResource(R.string.scan_qr_copy)) { context.copy(content.raw) }
                }
            }
        }
    }
}

@Composable
private fun PagoPaPart(content: QrContent.PagoPa, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var due by remember { mutableStateOf<LocalDate?>(today.plusDays(7)) }
    val notice = content.notice.chunked(4).joinToString(" ")
    Text(euro(content.amountCents), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
    Group {
        Field(stringResource(R.string.qr_pagopa_notice), notice)
        CardDivider()
        Field(stringResource(R.string.qr_pagopa_creditor), content.creditorTaxCode)
    }
    Text(stringResource(R.string.qr_remind).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterPill(stringResource(R.string.qr_in_a_week), null, due == today.plusDays(7)) { due = today.plusDays(7) }
        FilterPill(stringResource(R.string.qr_end_of_month), null, due == today.withDayOfMonth(today.lengthOfMonth())) { due = today.withDayOfMonth(today.lengthOfMonth()) }
        FilterPill(stringResource(R.string.qr_no_date), null, due == null) { due = null }
    }
    Text(stringResource(R.string.qr_pagopa_no_due), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Primary(stringResource(R.string.qr_add_todo)) {
        scope.launch {
            EuriseData.create(context).tasks.add(
                context.getString(R.string.qr_pagopa_task, euro(content.amountCents)),
                due = due,
                notes = context.getString(R.string.qr_pagopa_task_notes, notice, content.creditorTaxCode),
            )
            Toast.makeText(context, R.string.qr_added_todo, Toast.LENGTH_SHORT).show()
            onClose()
        }
    }
    Secondary(stringResource(R.string.qr_copy_for_bank)) { context.copy("$notice\n${content.creditorTaxCode}\n${euro(content.amountCents)}") }
}

/** "20261212T090000" o "20261212" in data e ora del telefono; la Z finale (UTC) si converte. */
private fun icsTime(text: String?): LocalDateTime? {
    val t = text?.trim() ?: return null
    return runCatching {
        when {
            t.endsWith("Z") -> java.time.ZonedDateTime.parse(t, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX")).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            t.length >= 15 -> LocalDateTime.parse(t.take(15), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
            else -> LocalDate.parse(t.take(8), DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay()
        }
    }.getOrNull()
}

/** Da Android 11 la rete si propone al sistema, che chiede conferma; prima si copia la password e si aprono le impostazioni. */
private fun connectWifi(context: Context, wifi: QrContent.Wifi) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val builder = android.net.wifi.WifiNetworkSuggestion.Builder().setSsid(wifi.ssid).setIsHiddenSsid(wifi.hidden)
        when (wifi.security.uppercase()) {
            "WPA", "WPA2" -> builder.setWpa2Passphrase(wifi.password)
            "SAE", "WPA3" -> builder.setWpa3Passphrase(wifi.password)
            else -> Unit
        }
        val list = arrayListOf(runCatching { builder.build() }.getOrNull() ?: return context.copy(wifi.password))
        context.start(Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, list))
    } else {
        if (wifi.password.isNotEmpty()) context.copy(wifi.password)
        context.start(Intent(Settings.ACTION_WIFI_SETTINGS))
    }
}

/** I codici letti prima: si riaprono con un tocco. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QrHistorySheet(onOpen: (String) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val history = remember { QrHistory(context) }
    var entries by remember { mutableStateOf(history.load()) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = scheme.background) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.qr_history), fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (entries.isNotEmpty()) Text(
                    stringResource(R.string.qr_history_clear), fontWeight = FontWeight.Bold, color = scheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { history.clear(); entries = emptyList() }.padding(8.dp),
                )
            }
            if (entries.isEmpty()) Text(stringResource(R.string.qr_history_empty), color = scheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp).clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow)) {
                items(entries, key = { it.second }) { (text, at) ->
                    val (icon, summary) = qrSummary(QrContent.parse(text))
                    Row(Modifier.fillMaxWidth().clickable { onOpen(text) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(summary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(at)), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Crea un QR: testo o link, oppure una rete Wi-Fi da far usare agli ospiti; si condivide come immagine. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QrCreateSheet(onClose: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var wifi by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    fun escape(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace(":", "\\:")
    val payload = if (wifi) ssid.takeIf { it.isNotBlank() }?.let { "WIFI:T:${if (password.isEmpty()) "nopass" else "WPA"};S:${escape(it)};P:${escape(password)};;" } else text.takeIf { it.isNotBlank() }
    val bitmap = remember(payload) { payload?.let { qrBitmap(it, 720) } }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = scheme.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.qr_create), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterPill(stringResource(R.string.qr_create_text), null, !wifi) { wifi = false }
                FilterPill(stringResource(R.string.qr_wifi_title), null, wifi) { wifi = true }
            }
            if (wifi) {
                OutlinedTextField(ssid, { ssid = it }, label = { Text(stringResource(R.string.qr_wifi_network)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.qr_wifi_password)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            } else {
                OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.qr_create_hint)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
            bitmap?.let { bmp ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(bmp.asImageBitmap(), stringResource(R.string.qr_create), modifier = Modifier.size(240.dp).clip(RoundedCornerShape(16.dp)))
                }
                Primary(stringResource(R.string.qr_share)) { shareBitmap(context, bmp) }
            }
        }
    }
}

internal fun qrBitmap(text: String, size: Int): Bitmap? = runCatching {
    val hints = mapOf(com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8", com.google.zxing.EncodeHintType.MARGIN to 2)
    val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()

private fun shareBitmap(context: Context, bitmap: Bitmap) {
    val dir = File(context.cacheDir, "drive/qr").apply { mkdirs() }
    val file = File(dir, "qr.png")
    runCatching { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    val uri = runCatching { FileProvider.getUriForFile(context, context.packageName + ".drivefiles", file) }.getOrNull() ?: return
    context.start(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            null,
        ),
    )
}

/** Legge un codice da una foto scelta dalla persona (QR e codici a barre). */
internal fun decodeFromImage(context: Context, uri: Uri): String? = runCatching {
    val bitmap = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return null
    val scale = minOf(1f, 1600f / maxOf(bitmap.width, bitmap.height))
    val small = if (scale < 1f) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
    val pixels = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
    val source = com.google.zxing.RGBLuminanceSource(small.width, small.height, pixels)
    val hints = mapOf(com.google.zxing.DecodeHintType.TRY_HARDER to true)
    com.google.zxing.MultiFormatReader().decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source)), hints).text
}.getOrNull()

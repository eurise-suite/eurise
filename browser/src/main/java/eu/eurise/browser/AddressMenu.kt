package eu.eurise.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import eu.eurise.core.designsystem.icon.EuriseIcons
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Una scheda che galleggia sopra la barra del browser (menu dell'indirizzo, segnalibri): il fondo si scurisce e un tocco
 * fuori la chiude. Resta sopra la barra, così la barra si vede ancora.
 */
@Composable
internal fun FloatingCard(onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(Color(0xFF141826).copy(alpha = 0.32f)).pointerInput(Unit) { detectTapGestures { onClose() } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.navigationBarsPadding().padding(start = 10.dp, end = 10.dp, bottom = 78.dp).widthIn(max = 560.dp).fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp)).background(scheme.surface)
                .pointerInput(Unit) { detectTapGestures { } }.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/**
 * Il menu dell'indirizzo (tavola BrwIndirizzo): si apre toccando l'indirizzo. Copia, Condividi, Modifica, QR; poi In Note,
 * Da fare, Promemoria, Leggi dopo, Manda al computer, Copia pulito. Tenere premuto l'indirizzo apre subito la scrittura.
 */
@Composable
internal fun AddressMenu(model: BrowserModel, tab: Tab, actions: BrowserActions, onEdit: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var qr by remember { mutableStateOf(false) }
    var remind by remember { mutableStateOf(false) }
    val copied = stringResource(R.string.brw_copied)
    val copiedClean = stringResource(R.string.brw_copied_clean)
    val noteDone = stringResource(R.string.brw_note_done)
    val taskDone = stringResource(R.string.brw_task_done)
    val noHub = stringResource(R.string.brw_no_hub)
    val laterOn = stringResource(R.string.brw_later_saved)
    val laterOff = stringResource(R.string.brw_later_removed)
    val title = tab.title.ifBlank { Links.host(tab.url) }

    FloatingCard(onClose) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SiteDot(tab.url, 36)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tab.url.startsWith("https")) { Icon(EuriseIcons.lock(2.4f), null, tint = Safe, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(5.dp)) }
                    Text(tab.url.substringAfter("://").removePrefix("www."), fontSize = 12.sp, color = if (tab.secure) Safe else Low, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (qr) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)) { QrImage(Links.clean(tab.url)) }
                Text(stringResource(R.string.brw_qr_note), fontSize = 12.sp, color = scheme.onSurfaceVariant)
                Pill(stringResource(R.string.brw_back), EuriseIcons.chevronLeft(2.2f), Modifier.fillMaxWidth()) { qr = false }
            }
            return@FloatingCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BigAction(EuriseIcons.copy(2.2f), stringResource(R.string.brw_copy)) { copy(context, tab.url); model.notice = copied; onClose() }
            BigAction(EuriseIcons.share(2.2f), stringResource(R.string.brw_share)) { onClose(); model.share(tab) }
            BigAction(EuriseIcons.pencil(2.2f), stringResource(R.string.brw_edit)) { onEdit() }
            BigAction(EuriseIcons.qr(2.2f), stringResource(R.string.brw_qr)) { qr = true }
        }
        Column(Modifier.padding(horizontal = 4.dp).verticalScroll(rememberScrollState())) {
            AddressRow(EuriseIcons.note(2.2f), stringResource(R.string.brw_m_notes), stringResource(R.string.brw_addr_notes_sub)) {
                onClose(); model.toSuite(tab, task = false) { ok -> model.notice = if (ok) noteDone else noHub }
            }
            AddressRow(EuriseIcons.check(2.2f), stringResource(R.string.brw_m_task), stringResource(R.string.brw_addr_task_sub), divider = true) {
                onClose(); model.toSuite(tab, task = true) { ok -> model.notice = if (ok) taskDone else noHub }
            }
            AddressRow(EuriseIcons.bell(2.2f), stringResource(R.string.brw_addr_remind), stringResource(R.string.brw_addr_remind_sub), divider = true) { remind = !remind }
            if (remind) {
                val now = LocalDateTime.now()
                val choices = listOf(
                    R.string.brw_remind_hour to now.plusHours(1).withSecond(0).withNano(0),
                    R.string.brw_remind_evening to (if (now.hour < 20) LocalDate.now() else LocalDate.now().plusDays(1)).atTime(20, 0),
                    R.string.brw_remind_tomorrow to LocalDate.now().plusDays(1).atTime(9, 0),
                )
                Row(Modifier.padding(start = 30.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    choices.forEach { (label, at) ->
                        val text = stringResource(label)
                        Box(
                            Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(scheme.background)
                                .clickable(role = Role.Button) {
                                    onClose()
                                    actions.addEvent(title, PageDate(at.toLocalDate(), LocalTime.of(at.hour, at.minute), title), tab.url)
                                }.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            AddressRow(EuriseIcons.bookmark(2.2f), stringResource(if (model.isLater(tab.url)) R.string.brw_addr_later_remove else R.string.brw_addr_later), stringResource(R.string.brw_addr_later_sub), divider = true) {
                onClose()
                tab.page { info -> val on = model.toggleLater(info ?: PageInfo(tab.url, tab.title, "", "", "")); model.notice = if (on) laterOn else laterOff }
            }
            AddressRow(EuriseIcons.monitor(2.2f), stringResource(R.string.brw_addr_computer), stringResource(R.string.brw_addr_computer_sub), divider = true, enabled = false) { }
            AddressRow(EuriseIcons.link(2.2f), stringResource(R.string.brw_addr_clean), stringResource(R.string.brw_addr_clean_sub), divider = true) {
                copy(context, Links.clean(tab.url)); model.notice = copiedClean; onClose()
            }
        }
        Text(stringResource(R.string.brw_addr_hint), fontSize = 11.5.sp, lineHeight = 16.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("link", text))
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BigAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(16.dp)).background(scheme.background).clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
    }
}

@Composable
private fun AddressRow(icon: ImageVector, title: String, sub: String, divider: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    if (divider) HorizontalDivider(color = scheme.onSurface.copy(alpha = 0.07f))
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant)
            Text(sub, fontSize = 11.5.sp, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Il QR di un testo, disegnato sul telefono (ZXing, già nella suite). */
@Composable
internal fun BoxScope.QrImage(text: String) {
    val matrix = remember(text) {
        runCatching { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0)) }.getOrNull()
    } ?: return
    Canvas(Modifier.fillMaxSize().aspectRatio(1f).align(Alignment.Center)) {
        val cell = size.minDimension / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

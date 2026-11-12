package eu.eurise.feature.drive

import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.EuriseThemeExtras

/** I tipi di file con il loro colore (tavola Drive): si riconoscono a colpo d'occhio, anche nel tema Notte. */
internal enum class Kind(val light: Long, val lightInk: Long, val dark: Long, val darkInk: Long) {
    Pdf(0xFFFCE7EF, 0xFFA3164D, 0xFF4A1B2C, 0xFFF4A3C0),
    Doc(0xFFE7EEFF, 0xFF1E4FC2, 0xFF1C2A4D, 0xFFA9C1F2),
    Sheet(0xFFE3F5EC, 0xFF0E7A4F, 0xFF173A2B, 0xFF8FD6B3),
    Slides(0xFFFFF0E0, 0xFFB4560B, 0xFF45301A, 0xFFF2B37A),
    Image(0xFFF0E9FF, 0xFF6B3FC4, 0xFF2F2447, 0xFFC7B1F2),
    Media(0xFFE2F4F6, 0xFF2E6E73, 0xFF1A3638, 0xFF79BCC1),
    Archive(0xFFECECE7, 0xFF56564F, 0xFF2A2D33, 0xFFA4A9B2),
    Other(0xFFECECE7, 0xFF3A3A35, 0xFF262B33, 0xFFC9CDD4);

    companion object {
        fun of(extension: String): Kind = when (extension.lowercase()) {
            "pdf" -> Pdf
            "doc", "docx", "odt", "rtf", "txt", "md", "pages" -> Doc
            "xls", "xlsx", "ods", "csv", "numbers" -> Sheet
            "ppt", "pptx", "odp", "key" -> Slides
            "jpg", "jpeg", "png", "webp", "heic", "gif", "svg", "bmp", "tif", "tiff" -> Image
            "mp3", "m4a", "ogg", "opus", "wav", "flac", "mp4", "mkv", "webm", "mov", "avi" -> Media
            "zip", "rar", "7z", "tar", "gz", "xz", "apk" -> Archive
            else -> Other
        }
    }
}

@Composable
internal fun kindColors(extension: String): Pair<Color, Color> {
    val k = Kind.of(extension)
    return if (EuriseThemeExtras.colors.isDark) Color(k.dark) to Color(k.darkInk) else Color(k.light) to Color(k.lightInk)
}

/** La piastrella colorata del tipo di file («PDF», «DOCX»…). */
@Composable
internal fun KindTile(extension: String, width: Dp, height: Dp, corner: Dp, fontSize: Int) {
    val (bg, ink) = kindColors(extension)
    Box(Modifier.size(width, height).clip(RoundedCornerShape(corner)).background(bg), contentAlignment = Alignment.Center) {
        Text(extension.take(4).uppercase().ifEmpty { "FILE" }, fontSize = fontSize.sp, fontWeight = FontWeight.ExtraBold, color = ink)
    }
}

/** I colori delle cartelle per posto: telefono blu, Murena e Nextcloud azzurro, kDrive verde, cifrate prugna. */
@Composable
internal fun placeColors(place: Place?): Pair<Color, Color> {
    val dark = EuriseThemeExtras.colors.isDark
    val (back, front) = when {
        place?.vaultRoot != null -> 0xFFD9C7E6 to 0xFF8A62A8
        place?.isTree == true -> 0xFFBFE6D4 to 0xFF2E9E6E
        place?.account != null -> 0xFFA9C3FF to 0xFF5C8FF2
        else -> 0xFFB9CBE8 to 0xFF3E5C8A
    }
    return if (dark) Color(front) to Color(back) else Color(back) to Color(front)
}

/** La cartella a due toni della tavola, disegnata (niente immagini nell'APK). */
@Composable
internal fun FolderGlyph(place: Place?, modifier: Modifier = Modifier.size(30.dp, 26.dp)) {
    val (back, front) = placeColors(place)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = w * 0.08f
        val tab = Path().apply {
            moveTo(0f, h * 0.18f + r); quadraticTo(0f, h * 0.09f, w * 0.08f, h * 0.09f)
            lineTo(w * 0.38f, h * 0.09f); lineTo(w * 0.48f, h * 0.2f); lineTo(w * 0.92f, h * 0.2f)
            quadraticTo(w, h * 0.2f, w, h * 0.29f); lineTo(w, h * 0.9f); quadraticTo(w, h, w * 0.92f, h)
            lineTo(w * 0.08f, h); quadraticTo(0f, h, 0f, h * 0.9f); close()
        }
        drawPath(tab, back)
        drawRoundRect(front, topLeft = Offset(0f, h * 0.34f), size = Size(w, h * 0.66f), cornerRadius = CornerRadius(r, r))
    }
}

/** Scansiona, Carica, Cartella: le tre cose che si fanno più spesso, in alto (tavola Drive). */
@Composable
internal fun QuickActions(onScan: () -> Unit, onUpload: () -> Unit, onFolder: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickButton(EuriseIcons.scan(2.2f), stringResource(R.string.drive_scan_short), strong = true, Modifier.weight(1.3f), onScan)
        QuickButton(EuriseIcons.upload(2.2f), stringResource(R.string.drive_upload), strong = false, Modifier.weight(1f), onUpload)
        QuickButton(EuriseIcons.folder(2.2f), stringResource(R.string.drive_new_folder_short), strong = false, Modifier.weight(1f), onFolder)
    }
}

@Composable
private fun QuickButton(icon: ImageVector, label: String, strong: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.height(46.dp).clip(RoundedCornerShape(14.dp))
            .background(if (strong) scheme.primary else scheme.surfaceContainerLow)
            .then(if (strong) Modifier else Modifier.border(1.dp, scheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(14.dp)))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (strong) scheme.onPrimary else scheme.primary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (strong) scheme.onPrimary else scheme.onSurface, maxLines = 1)
    }
}

/** Lo spazio di ogni posto, con la barra colorata (tavola Drive). */
@Composable
internal fun SpaceBars(spaces: List<Space>) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val colors = listOf(Color(0xFF3E5C8A), Color(0xFF5C8FF2), Color(0xFF0E9F6E), Color(0xFF8A62A8), Color(0xFFB4560B))
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        spaces.forEachIndexed { i, s ->
            val used = Formatter.formatShortFileSize(context, s.used)
            val total = s.total?.let { Formatter.formatShortFileSize(context, it) }
            Row {
                Text(s.label, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (total != null) stringResource(R.string.drive_space_of, used, total) else used, fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
            }
            val fraction = s.total?.takeIf { it > 0 }?.let { (s.used.toFloat() / it).coerceIn(0.02f, 1f) }
            if (fraction != null) {
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(scheme.onSurface.copy(alpha = 0.08f))) {
                    Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (fraction > 0.9f) Color(0xFF8C2F39) else colors[i % colors.size]))
                }
            }
        }
    }
}

/** I file da riordinare in Download: documenti da mettere in Documenti, file vecchi e pesanti. */
internal data class Tidy(val documents: List<DriveItem>, val old: List<DriveItem>, val oldBytes: Long)

internal fun tidyOf(downloads: List<DriveItem>, now: Long = System.currentTimeMillis()): Tidy {
    val files = downloads.filter { !it.folder }
    val docs = files.filter { Kind.of(it.extension) in setOf(Kind.Pdf, Kind.Doc, Kind.Sheet, Kind.Slides) }
    val old = files.filter { f -> f.modified != null && now - f.modified > 60L * 86_400_000L }
    return Tidy(docs, old, old.sumOf { it.size ?: 0L })
}

/**
 * «Riordina» (funzione intelligente): Download si riempie da solo. Eurise propone di spostare i documenti in Documenti
 * e mostra quanto spazio occupano i file vecchi; non sposta né cancella nulla senza il tuo tocco.
 */
@Composable
internal fun TidyCard(tidy: Tidy, onMoveDocs: () -> Unit, onReview: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x3385672B)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(EuriseIcons.spark(2f), null, tint = Color(0xFF85672B), modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.drive_tidy_title), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        if (tidy.documents.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.drive_tidy_docs, tidy.documents.size), fontSize = 13.sp, modifier = Modifier.weight(1f))
                SmallAction(stringResource(R.string.drive_tidy_move), onMoveDocs)
            }
        }
        if (tidy.old.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.drive_tidy_old, tidy.old.size, Formatter.formatShortFileSize(context, tidy.oldBytes)),
                    fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                SmallAction(stringResource(R.string.drive_tidy_review), onReview)
            }
        }
    }
}

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.padding(start = 8.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).background(scheme.primary)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimary) }
}


@Composable
private fun kindLabel(k: Kind): String = stringResource(
    when (k) {
        Kind.Pdf -> R.string.drive_kind_pdf
        Kind.Doc -> R.string.drive_kind_doc
        Kind.Sheet -> R.string.drive_kind_sheet
        Kind.Slides -> R.string.drive_kind_slides
        Kind.Image -> R.string.drive_kind_image
        Kind.Media -> R.string.drive_kind_media
        Kind.Archive -> R.string.drive_kind_archive
        Kind.Other -> R.string.drive_kind_other
    },
)

/** I tipi di file della cartella come filtri colorati: un tocco e restano solo quelli. */
@Composable
internal fun KindFilter(counts: Map<Kind, Int>, selected: Kind?, onSelect: (Kind?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val dark = EuriseThemeExtras.colors.isDark
    Row(
        Modifier.padding(bottom = 10.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (selected == null) scheme.primary else scheme.surfaceContainerLow)
                .clickable(role = Role.Button) { onSelect(null) }.padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text(stringResource(R.string.drive_kind_all), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (selected == null) scheme.onPrimary else scheme.onSurface) }
        Kind.entries.filter { it in counts }.forEach { k ->
            val on = selected == k
            val bg = Color(if (dark) k.dark else k.light)
            val ink = Color(if (dark) k.darkInk else k.lightInk)
            Box(
                Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(if (on) ink else bg)
                    .clickable(role = Role.Button) { onSelect(if (on) null else k) }.padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("${kindLabel(k)} ${counts[k]}", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = if (on) bg else ink) }
        }
    }
}


/** Un impegno vicino e i file che sembrano servirgli. */
internal data class ForEvent(val title: String, val start: java.time.LocalDateTime, val files: List<DriveItem>)

private val STOP = setOf(
    "della", "dello", "delle", "degli", "con", "per", "presso", "alle", "dalle", "nella", "nelle", "sulla", "riunione",
    "incontro", "appuntamento", "chiamata", "call", "meeting", "evento", "promemoria", "oggi", "domani", "the", "and", "with",
)

/** Le parole che contano di un titolo: almeno 4 lettere, niente parole vuote, senza accenti. */
internal fun keywords(text: String): Set<String> = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
    .replace(Regex("\\p{Mn}+"), "")
    .split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 4 && it !in STOP }.toSet()

/**
 * Per ogni impegno, i file (al massimo tre) che hanno nel nome almeno una parola importante del titolo o del luogo.
 * Tutto sul telefono: il Calendario non esce.
 */
internal fun filesForEvents(events: List<Triple<String, String, java.time.LocalDateTime>>, files: List<DriveItem>): List<ForEvent> =
    events.mapNotNull { (title, place, start) ->
        val words = keywords("$title $place")
        if (words.isEmpty()) return@mapNotNull null
        val hits = files.filter { f -> !f.folder && keywords(f.name.substringBeforeLast('.')).any { it in words } }.distinctBy { it.key }.take(3)
        if (hits.isEmpty()) null else ForEvent(title, start, hits)
    }

/** «Per i tuoi impegni»: la riunione di oggi alle 15 → preventivo.pdf. Un tocco e si apre. */
@Composable
internal fun ForEventsCard(items: List<ForEvent>, onOpen: (DriveItem) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val fmt = java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm", java.util.Locale.getDefault())
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x334F6B5A)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(EuriseIcons.calendar(2f), null, tint = Color(0xFF4F6B5A), modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.drive_for_events), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        items.forEach { e ->
            Text(e.title + " · " + e.start.format(fmt), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                e.files.forEach { f ->
                    Row(
                        Modifier.height(36.dp).clip(RoundedCornerShape(12.dp)).background(scheme.surfaceContainerLow)
                            .clickable(role = Role.Button) { onOpen(f) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KindTile(f.extension, 24.dp, 24.dp, 6.dp, 7)
                        Spacer(Modifier.width(6.dp))
                        Text(f.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 180.dp))
                    }
                }
            }
        }
    }
}

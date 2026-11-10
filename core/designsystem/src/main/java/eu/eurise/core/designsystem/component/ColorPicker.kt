package eu.eurise.core.designsystem.component

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.R
import eu.eurise.core.designsystem.color.ColorMath
import eu.eurise.core.designsystem.color.Hsv
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** Le otto tinte della suite, nel tono del tema chiaro. */
val SuiteColors: List<Color> = CalendarTint.entries.map { it.light }

private val Perla = 0xFFF4F4F1.toInt()
private val Notte = 0xFF14171C.toInt()

/** Gli ultimi colori scelti con "Il tuo", per riprenderli in un tocco. Restano sul telefono, nell'app che li usa. */
object RecentColors {
    private const val KEY = "recent"
    private fun prefs(context: Context) = context.getSharedPreferences("eurise_colors", Context.MODE_PRIVATE)
    fun get(context: Context): List<Color> = prefs(context).getString(KEY, "").orEmpty().split(',').mapNotNull(ColorMath::parseHex).map(::Color)
    fun add(context: Context, color: Color) {
        val list = (listOf(color) + get(context)).distinctBy { it.toArgb() }.take(6)
        prefs(context).edit().putString(KEY, list.joinToString(",") { ColorMath.hex(it.toArgb()) }).apply()
    }
}

/**
 * La fila di colori di ogni scelta nella suite: i colori proposti ([colors], di base le tinte della suite) e per
 * ultimo "Il tuo", che apre il selettore con ruota e HEX.
 */
@Composable
fun ColorRow(
    selected: Color?,
    onPick: (Color) -> Unit,
    modifier: Modifier = Modifier,
    colors: List<Color> = SuiteColors,
    title: String = stringResource(R.string.ds_color_title),
    swatch: Dp = 30.dp,
    outline: Color = MaterialTheme.colorScheme.outlineVariant,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    var picking by remember { mutableStateOf(false) }
    val own = selected != null && colors.none { it.toArgb() == selected.toArgb() }
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        colors.forEach { c ->
            val on = selected?.toArgb() == c.toArgb()
            val name = ColorMath.hex(c.toArgb())
            Box(
                Modifier.size(swatch).clip(CircleShape).background(c)
                    .border(if (on) 3.dp else 1.5.dp, if (on) accent else outline, CircleShape)
                    .clickable { onPick(c) }.semantics { contentDescription = name },
            )
        }
        val label = stringResource(R.string.ds_color_own)
        Box(
            Modifier.size(swatch).clip(CircleShape)
                .then(if (own) Modifier.background(selected!!) else Modifier.background(Brush.sweepGradient(Wheel)))
                .border(if (own) 3.dp else 1.5.dp, if (own) accent else outline, CircleShape)
                .clickable { picking = true }.semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) { Icon(EuriseIcons.plus(3f), null, tint = Color.White, modifier = Modifier.size(swatch * 0.45f)) }
    }
    if (picking) ColorPickerSheet(selected ?: colors.firstOrNull() ?: SuiteColors.first(), title, colors, onDismiss = { picking = false }) { onPick(it); picking = false }
}

/**
 * Il pallino «Il tuo» da mettere in fondo alle file di tinte che salvano un nome: mostra il colore proprio scelto
 * (se [selected] è un HEX) o la ruota; il tocco apre il selettore e restituisce il colore come "#RRGGBB".
 */
@Composable
fun OwnColorSwatch(selected: String?, onPick: (String) -> Unit, size: Dp = 26.dp, ring: Color = MaterialTheme.colorScheme.onSurface) {
    var picking by remember { mutableStateOf(false) }
    val own = selected?.takeIf { it.startsWith("#") }?.let(ColorMath::parseHex)?.let(::Color)
    val label = stringResource(R.string.ds_color_own)
    Box(
        Modifier.size(size).clip(CircleShape)
            .then(if (own != null) Modifier.background(own).border(3.dp, ring, CircleShape) else Modifier.background(Brush.sweepGradient(Wheel)))
            .clickable { picking = true }.semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { if (own == null) Icon(EuriseIcons.plus(3f), null, tint = Color.White, modifier = Modifier.size(size * 0.5f)) }
    if (picking) ColorPickerSheet(own ?: SuiteColors.first(), stringResource(R.string.ds_color_title), onDismiss = { picking = false }) {
        onPick(ColorMath.hex(it.toArgb())); picking = false
    }
}

private val Wheel = listOf(0xFFE45B5B, 0xFFE4A35B, 0xFFE4DC5B, 0xFF8AD06B, 0xFF5BD0B0, 0xFF5BB2E4, 0xFF6B7BE4, 0xFFA46BE4, 0xFFE45BB8, 0xFFE45B5B).map { Color(it) }

/** Il selettore "Il tuo colore" (anteprima ColoreTuo): tinte della suite, ruota, luminosità, HEX, contrasto, recenti. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(initial: Color, title: String, suite: List<Color> = SuiteColors, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    val context = LocalContext.current
    var hsv by remember { mutableStateOf(ColorMath.toHsv(initial.toArgb())) }
    val argb = ColorMath.toArgb(hsv)
    var hex by remember { mutableStateOf(ColorMath.hex(argb)) }
    val recent = remember { RecentColors.get(context) }
    fun set(next: Hsv) { hsv = next; hex = ColorMath.hex(ColorMath.toArgb(next)) }
    fun done() { val c = Color(ColorMath.toArgb(hsv)); RecentColors.add(context, c); onPick(c) }
    val scheme = MaterialTheme.colorScheme

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = scheme.background) {
        Column(Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(onClick = ::done) { Text(stringResource(R.string.ds_color_done), fontWeight = FontWeight.ExtraBold) }
            }
            Label(stringResource(R.string.ds_color_suite))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suite.forEach { c ->
                    val name = ColorMath.hex(c.toArgb())
                    Box(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(18.dp)).background(c)
                        .border(if (c.toArgb() == argb) 3.dp else 0.dp, if (c.toArgb() == argb) scheme.onBackground else Color.Transparent, RoundedCornerShape(18.dp))
                        .clickable { set(ColorMath.toHsv(c.toArgb())) }.semantics { contentDescription = name })
                }
            }
            Label(stringResource(R.string.ds_color_own))
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                HueWheel(hsv, Modifier.size(196.dp)) { h, s -> set(hsv.copy(hue = h, saturation = s, value = if (hsv.value < 0.15f) 0.7f else hsv.value)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(16.dp)).background(Color(argb)))
                    OutlinedTextField(
                        hex, { text ->
                            hex = text.take(7)
                            ColorMath.parseHex(text)?.let { hsv = ColorMath.toHsv(it) }
                        },
                        label = { Text("HEX") }, singleLine = true,
                        isError = ColorMath.parseHex(hex) == null,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.ds_color_brightness), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(84.dp))
                ValueTrack(hsv, Modifier.weight(1f)) { set(hsv.copy(value = it)) }
            }
            val onLight = ColorMath.contrast(argb, Perla) >= 3.0
            val onDark = ColorMath.contrast(argb, Notte) >= 3.0
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (onLight && onDark) scheme.secondaryContainer else scheme.errorContainer).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(if (onLight && onDark) EuriseIcons.check() else EuriseIcons.eye(), null, modifier = Modifier.size(16.dp))
                Text(
                    stringResource(when {
                        onLight && onDark -> R.string.ds_color_readable
                        onLight -> R.string.ds_color_dark_weak
                        onDark -> R.string.ds_color_light_weak
                        else -> R.string.ds_color_weak
                    }),
                    fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            if (recent.isNotEmpty()) {
                Label(stringResource(R.string.ds_color_recent))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    recent.forEach { c ->
                        val name = ColorMath.hex(c.toArgb())
                        Box(Modifier.size(36.dp).clip(CircleShape).background(c)
                            .border(if (c.toArgb() == argb) 3.dp else 0.dp, if (c.toArgb() == argb) scheme.onBackground else Color.Transparent, CircleShape)
                            .clickable { set(ColorMath.toHsv(c.toArgb())) }.semantics { contentDescription = name })
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) =
    Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Ruota: l'angolo è la tinta, la distanza dal centro l'intensità (al centro il bianco). */
@Composable
private fun HueWheel(hsv: Hsv, modifier: Modifier, onChange: (Float, Float) -> Unit) {
    val label = stringResource(R.string.ds_color_wheel)
    fun pick(p: Offset, w: Float, h: Float) {
        val r = min(w, h) / 2
        val dx = p.x - w / 2
        val dy = p.y - h / 2
        val hue = ((Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()) + 360f) % 360f
        onChange(hue, (hypot(dx, dy) / r).coerceIn(0f, 1f))
    }
    Canvas(
        modifier.semantics { contentDescription = label }
            .pointerInput(Unit) { detectTapGestures { pick(it, size.width.toFloat(), size.height.toFloat()) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> change.consume(); pick(change.position, size.width.toFloat(), size.height.toFloat()) } },
    ) {
        val r = size.minDimension / 2
        // Il cerchio parte da destra e gira in senso orario come atan2 sullo schermo: tinta 0 a destra.
        val hues = (0..12).map { Color(ColorMath.toArgb(Hsv(it * 30f, 1f, 1f))) }
        drawCircle(Brush.sweepGradient(hues, center), r)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, r), r)
        val a = Math.toRadians(hsv.hue.toDouble())
        val p = Offset(center.x + (cos(a) * hsv.saturation * r).toFloat(), center.y + (sin(a) * hsv.saturation * r).toFloat())
        drawCircle(Color(ColorMath.toArgb(hsv)), 11.dp.toPx(), p)
        drawCircle(Color.White, 11.dp.toPx(), p, style = Stroke(4.dp.toPx()))
    }
}

@Composable
private fun ValueTrack(hsv: Hsv, modifier: Modifier, onChange: (Float) -> Unit) {
    val label = stringResource(R.string.ds_color_brightness)
    val dark = Color(ColorMath.toArgb(hsv.copy(value = 0.08f)))
    val full = Color(ColorMath.toArgb(hsv.copy(value = 1f)))
    Canvas(
        modifier.height(28.dp).semantics { contentDescription = label }
            .pointerInput(Unit) { detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectDragGestures { change, _ -> change.consume(); onChange((change.position.x / size.width).coerceIn(0f, 1f)) } },
    ) {
        val h = 14.dp.toPx()
        val top = (size.height - h) / 2
        drawRoundRect(Brush.horizontalGradient(listOf(dark, full)), Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
        val x = hsv.value * size.width
        drawCircle(Color(ColorMath.toArgb(hsv)), 11.dp.toPx(), Offset(x, size.height / 2))
        drawCircle(Color.White, 11.dp.toPx(), Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
    }
}

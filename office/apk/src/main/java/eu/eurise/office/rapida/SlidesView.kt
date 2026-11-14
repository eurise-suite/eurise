package eu.eurise.office.rapida

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.eurise.core.designsystem.component.DockItem
import eu.eurise.core.designsystem.component.EuriseDock
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.office.R

private val Navy = Color(0xFF1F2A44)
private val Accent = Color(0xFF3E5C8A)
private val Found = Color(0xFFFFF6D6)

/** Larghezza di riferimento di una diapositiva: i caratteri sono pensati per questa e crescono con la diapositiva. */
private val Reference = 358.dp

/**
 * Vista rapida di una presentazione (tavola OffLetturaPresentazione): le diapositive una sotto l'altra con le note del
 * relatore, quella che stai guardando segnata; in basso Presenta, Completa, Condividi e la lente. Sola lettura.
 */
@Composable
internal fun SlidesScreen(model: QuickModel, deck: SlideDeck, engineReady: Boolean, onBack: () -> Unit, onFull: () -> Unit, onShare: () -> Unit) {
    val haze = remember { HazeState() }
    val list = rememberLazyListState()
    val current by remember { derivedStateOf { currentSlide(list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset, list.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1) } }
    val q = model.query.trim()

    BackHandler(enabled = model.searching) { model.searching = false }
    LaunchedEffect(q, deck) {
        if (q.length < 2) return@LaunchedEffect
        val index = deck.slides.indexOfFirst { it.text.contains(q, ignoreCase = true) }
        if (index >= 0) list.animateScrollToItem(index)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
        Column(Modifier.fillMaxSize()) {
            SlidesHeader(model, deck, current, onBack, onFull)
            if (deck.slides.isEmpty()) Message(stringResource(R.string.slides_none))
            else LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth().hazeSource(haze),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(deck.slides, key = { i, _ -> i }) { i, slide ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        SlideView(
                            model, slide, deck.ratio, q,
                            Modifier.fillMaxWidth().then(
                                if (i == current && deck.slides.size > 1) Modifier.border(2.dp, Accent, RoundedCornerShape(10.dp)) else Modifier,
                            ).alpha(if (slide.hidden) 0.55f else 1f),
                        )
                        Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                slide.number.toString(), fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                                color = if (i == current) Navy else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (slide.hidden) Text(stringResource(R.string.slides_hidden), fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val notesLabel = stringResource(R.string.slides_notes)
                            val strong = MaterialTheme.colorScheme.onSurface
                            if (slide.notes.isNotBlank()) Text(
                                buildAnnotatedString {
                                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = strong))
                                    append(notesLabel); append(' ')
                                    pop()
                                    append(highlight(slide.notes, q))
                                },
                                fontSize = 11.5.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding()) {
            if (model.searching) SearchField(model)
            EuriseDock(
                items = listOf(
                    DockItem(stringResource(R.string.slides_present)) { EuriseIcons.monitor(it) },
                    DockItem(stringResource(R.string.quick_full)) { EuriseIcons.office(it) },
                    DockItem(stringResource(R.string.quick_share)) { EuriseIcons.share(it) },
                ),
                selected = 0,
                onSelect = {
                    when (it) {
                        0 -> if (deck.slides.isNotEmpty()) model.presenting = firstShown(deck, current)
                        1 -> model.showFull = true
                        2 -> onShare()
                    }
                },
                hazeState = haze,
                actionLabel = stringResource(R.string.slides_search),
                actionIcon = EuriseIcons.search(),
                onAction = { model.searching = !model.searching },
            )
        }
    }

    model.presenting?.let { index -> Presenter(model, deck, index) }
    if (model.showFull) FullSheet(engineReady, onDismiss = { model.showFull = false }, onOpen = { model.showFull = false; onFull() })
}

/** La diapositiva «che stai guardando»: la prima in alto, finché più di metà non è uscita dallo schermo. */
private fun currentSlide(first: Int, offset: Int, size: Int): Int = if (offset > size / 2) first + 1 else first

private fun firstShown(deck: SlideDeck, from: Int): Int =
    (from until deck.slides.size).firstOrNull { !deck.slides[it].hidden } ?: from.coerceIn(0, deck.slides.lastIndex)

@Composable
private fun SlidesHeader(model: QuickModel, deck: SlideDeck, current: Int, onBack: () -> Unit, onExact: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(EuriseIcons.chevronLeft(), contentDescription = stringResource(R.string.pdf_back)) }
            Column(Modifier.weight(1f)) {
                Text(model.name, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val where = if (deck.slides.isEmpty()) model.where
                else stringResource(R.string.slides_position, model.where, (current + 1).coerceAtMost(deck.slides.size), deck.slides.size)
                Text(where, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.height(28.dp).clip(CircleShape).background(Color(0xFFE4E9F2)).padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(EuriseIcons.flash(), contentDescription = null, tint = Navy, modifier = Modifier.size(14.dp))
                Text(stringResource(R.string.quick_badge), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Navy)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onExact) { Text(stringResource(R.string.quick_exact), fontSize = 12.5.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/**
 * Una diapositiva nelle sue proporzioni: titolo, sottotitolo, testi e tabelle in colonna, le immagini a destra (o al
 * centro se non c'è testo). I caratteri crescono con la larghezza, così la stessa vista serve anche a «Presenta».
 */
@Composable
private fun SlideView(model: QuickModel, slide: Slide, ratio: Float, query: String, modifier: Modifier) {
    val background = slide.background?.let { Color(0xFF000000 or it.toLong()) } ?: Color.White
    val dark = background.luminance() < 0.4f
    val ink = if (dark) Color.White else Color(0xFF2A2925)
    val soft = if (dark) Color(0xFFC9D3E6) else Color(0xFF56564F)
    val heading = if (dark) Color.White else Navy
    val label = stringResource(R.string.slides_slide, slide.number) + if (slide.title.isNotBlank()) ": ${slide.title}" else ""
    BoxWithConstraints(
        modifier.aspectRatio(ratio).clip(RoundedCornerShape(10.dp)).background(background).semantics { contentDescription = label },
    ) {
        val slideWidth = maxWidth
        val scale = slideWidth / Reference
        val pad = (20 * scale).dp
        val texts = slide.shapes.filter { it !is SlideImage }
        val images = slide.shapes.filterIsInstance<SlideImage>()
        val cover = texts.isEmpty() && images.isEmpty()
        if (cover) {
            // Diapositiva di apertura: titolo e sottotitolo al centro.
            Column(Modifier.fillMaxSize().padding(horizontal = pad), verticalArrangement = Arrangement.Center) {
                if (slide.title.isNotBlank()) Text(
                    highlight(slide.title, query), fontSize = (22 * scale).sp, lineHeight = (27 * scale).sp,
                    fontWeight = FontWeight.ExtraBold, color = heading,
                )
                if (slide.subtitle.isNotBlank()) Text(
                    highlight(slide.subtitle, query), fontSize = (12.5f * scale).sp, lineHeight = (17 * scale).sp, color = soft,
                    modifier = Modifier.padding(top = (8 * scale).dp),
                )
            }
        } else Row(Modifier.fillMaxSize().padding(horizontal = pad, vertical = (16 * scale).dp), horizontalArrangement = Arrangement.spacedBy((14 * scale).dp)) {
            if (texts.isNotEmpty() || slide.title.isNotBlank()) Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy((7 * scale).dp)) {
                if (slide.title.isNotBlank()) Text(
                    highlight(slide.title, query), fontSize = (16 * scale).sp, lineHeight = (20 * scale).sp,
                    fontWeight = FontWeight.ExtraBold, color = heading, maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                if (slide.subtitle.isNotBlank()) Text(highlight(slide.subtitle, query), fontSize = (12 * scale).sp, color = soft)
                texts.forEach { shape ->
                    when (shape) {
                        is SlideText -> shape.paras.forEach { p -> ParaLine(p, scale, ink, query) }
                        is SlideTable -> TableBlock(shape, scale, ink, soft, query)
                        is SlideImage -> {}
                    }
                }
            }
            if (images.isNotEmpty()) {
                val alone = texts.isEmpty() && slide.title.isBlank()
                Column(
                    if (alone) Modifier.fillMaxSize() else Modifier.width(slideWidth * 0.3f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy((6 * scale).dp, Alignment.CenterVertically),
                ) {
                    images.take(if (alone) 2 else 3).forEach { image ->
                        SlidePicture(model, image, slideWidth, Modifier.fillMaxWidth().weight(1f), dark)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParaLine(p: SlidePara, scale: Float, ink: Color, query: String) {
    if (p.text.isBlank()) { Spacer(Modifier.height((4 * scale).dp)); return }
    val size = (if (p.level == 0) 12f else 11f) * scale
    Row(Modifier.padding(start = (p.level * 14 * scale).dp)) {
        if (p.bullet) Text(if (p.level == 0) "•" else "–", fontSize = size.sp, color = ink, modifier = Modifier.padding(end = (6 * scale).dp))
        Text(
            styled(p.spans, query), fontSize = size.sp, lineHeight = (size * 1.45f).sp, color = ink,
            textAlign = when (p.align) {
                Align.Center -> TextAlign.Center
                Align.End -> TextAlign.End
                Align.Justify -> TextAlign.Justify
                Align.Start -> TextAlign.Start
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TableBlock(t: SlideTable, scale: Float, ink: Color, soft: Color, query: String) {
    Column {
        t.rows.forEachIndexed { r, row ->
            Row(Modifier.fillMaxWidth().padding(vertical = (3 * scale).dp), horizontalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
                row.forEachIndexed { c, cell ->
                    Text(
                        highlight(cell, query), fontSize = (11f * scale).sp, color = ink,
                        fontWeight = if (r == 0 && t.rows.size > 1) FontWeight.Bold else FontWeight.Normal,
                        textAlign = if (c > 0 && cell.any(Char::isDigit) && cell.none(Char::isLetter)) TextAlign.End else TextAlign.Start,
                        modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(soft.copy(alpha = 0.25f)))
        }
    }
}

@Composable
private fun SlidePicture(model: QuickModel, image: SlideImage, slideWidth: Dp, modifier: Modifier, dark: Boolean) {
    val px = with(LocalDensity.current) { slideWidth.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, image.path, px) { value = model.image(image.path, px) }
    val shape = RoundedCornerShape(8.dp)
    val shown = bitmap
    if (shown != null) {
        Image(shown, contentDescription = image.name.ifBlank { null }, contentScale = ContentScale.Fit, modifier = modifier.clip(shape))
    } else {
        Box(modifier.clip(shape).background(if (dark) Color(0x33FFFFFF) else Color(0xFFE9E7E1)), contentAlignment = Alignment.Center) {
            Icon(EuriseIcons.image(), contentDescription = image.name.ifBlank { stringResource(R.string.slides_image) }, tint = Color(0xFF8F8E87), modifier = Modifier.size(26.dp))
        }
    }
}

private fun styled(spans: List<Span>, query: String): AnnotatedString = buildAnnotatedString {
    spans.forEach { s ->
        pushStyle(
            SpanStyle(
                fontWeight = if (s.bold) FontWeight.Bold else null,
                fontStyle = if (s.italic) FontStyle.Italic else null,
                textDecoration = if (s.underline) TextDecoration.Underline else null,
            ),
        )
        append(highlight(s.text, query))
        pop()
    }
}

/** Il testo con le occorrenze della ricerca evidenziate. */
private fun highlight(text: String, query: String): AnnotatedString = buildAnnotatedString {
    if (query.length < 2) { append(text); return@buildAnnotatedString }
    var from = 0
    while (true) {
        val i = text.indexOf(query, from, ignoreCase = true)
        if (i < 0) { append(text.substring(from)); break }
        append(text.substring(from, i))
        pushStyle(SpanStyle(background = Found, color = Color(0xFF1E1D1B)))
        append(text.substring(i, i + query.length))
        pop()
        from = i + query.length
    }
}

/**
 * «Presenta»: una diapositiva alla volta su fondo nero, a schermo intero e in orizzontale. Tocco a destra o scorrimento
 * verso sinistra per andare avanti, a sinistra per tornare; Indietro chiude. Le diapositive nascoste si saltano.
 */
@Composable
private fun Presenter(model: QuickModel, deck: SlideDeck, index: Int) {
    val context = LocalContext.current
    val activity = context as? Activity
    val shown = remember(deck) { deck.slides.indices.filter { !deck.slides[it].hidden }.ifEmpty { deck.slides.indices.toList() } }
    val position = shown.indexOf(index).coerceAtLeast(0)
    fun go(step: Int) {
        val next = position + step
        if (next < 0) return
        if (next >= shown.size) model.presenting = null else model.presenting = shown[next]
    }
    BackHandler { model.presenting = null }
    DisposableEffect(Unit) {
        val window = activity?.window
        val before = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (before != null) activity?.requestedOrientation = before
        }
    }
    val drag = remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(position) {
                detectTapGestures { offset -> go(if (offset.x > size.width / 3f) 1 else -1) }
            }
            .pointerInput(position) {
                detectHorizontalDragGestures(
                    onDragStart = { drag.floatValue = 0f },
                    onHorizontalDrag = { _, amount -> drag.floatValue += amount },
                    onDragEnd = {
                        if (drag.floatValue < -80f) go(1) else if (drag.floatValue > 80f) go(-1)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val slide = deck.slides[shown.getOrElse(position) { 0 }]
        val fitWidth = minOf(maxWidth, maxHeight * deck.ratio)
        SlideView(model, slide, deck.ratio, "", Modifier.width(fitWidth))
        Text(
            stringResource(R.string.slides_counter, position + 1, shown.size),
            fontSize = 12.sp, color = Color(0x99FFFFFF),
            modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
        )
        IconButton(onClick = { model.presenting = null }, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
            Icon(EuriseIcons.close(), contentDescription = stringResource(R.string.slides_close), tint = Color(0x99FFFFFF))
        }
    }
}

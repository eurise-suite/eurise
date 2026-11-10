package eu.eurise.feature.notes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import eu.eurise.core.designsystem.component.GlassDefaults
import eu.eurise.core.designsystem.component.glass
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import eu.eurise.core.designsystem.theme.LocalEuriseColors
import eu.eurise.feature.notes.R
import eu.eurise.feature.notes.doc.DrawingBlock
import eu.eurise.feature.notes.doc.InkPoint
import eu.eurise.feature.notes.doc.Paper
import eu.eurise.feature.notes.doc.Stroke
import eu.eurise.feature.notes.doc.Tool
import eu.eurise.feature.notes.doc.alpha
import eu.eurise.feature.notes.doc.hits
import eu.eurise.feature.notes.doc.outline
import eu.eurise.feature.notes.doc.recognizeShape
import kotlinx.coroutines.delay

/** Colori del foglio e dell'inchiostro nel tema attuale. */
internal data class SheetColors(val paper: Color, val rule: Color, val ink: Color, val tints: InkColors)

@Composable
internal fun sheetColors(): SheetColors {
    val dark = LocalEuriseColors.current.isDark
    return if (dark) SheetColors(Color(0xFF1C2027), Color(0xFF2B313B), Color(0xFFECE9E3), InkColors(true))
    else SheetColors(Color.White, Color(0xFFE6E4DD), Color(0xFF1E1D1B), InkColors(false))
}

/** Il contorno di un tratto come [Path], in unità del disegno. */
private fun Stroke.toPath(): Path {
    val o = outline()
    val path = Path()
    if (o.size < 6) return path
    path.moveTo(o[0], o[1])
    var i = 2
    while (i < o.size) {
        path.lineTo(o[i], o[i + 1])
        i += 2
    }
    path.close()
    return path
}

private fun DrawScope.drawPaper(paper: Paper, height: Int, colors: SheetColors, unit: Float) {
    drawRect(colors.paper)
    val step = DrawingBlock.PAPER_STEP * unit
    val w = size.width
    val h = height * unit
    val line = (1.5f * unit).coerceAtLeast(1f)
    if (paper == Paper.Lines || paper == Paper.Grid) {
        var y = step
        while (y < h) { drawLine(colors.rule, Offset(0f, y), Offset(w, y), line); y += step }
    }
    if (paper == Paper.Grid) {
        var x = step
        while (x < w) { drawLine(colors.rule, Offset(x, 0f), Offset(x, h), line); x += step }
    }
}

private fun DrawScope.drawStrokes(paths: List<Pair<Stroke, Path>>, colors: SheetColors, unit: Float) {
    scale(unit, unit, pivot = Offset.Zero) {
        // L'evidenziatore va sotto l'inchiostro, come su carta.
        for ((s, p) in paths.sortedBy { if (it.first.tool == Tool.Highlighter) 0 else 1 }) {
            val color = s.tint?.let { colors.tints.text(it) } ?: colors.ink
            drawPath(p, color, alpha = s.tool.alpha())
        }
    }
}

/** Il disegno dentro la nota: si apre a schermo intero con un tocco. */
@Composable
internal fun DrawingPreview(block: DrawingBlock, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val colors = sheetColors()
    val paths = remember(block.strokes) { block.strokes.map { it to it.toPath() } }
    val label = stringResource(R.string.notes_drawing_open)
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(DrawingBlock.WIDTH.toFloat() / block.height)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics { contentDescription = label },
    ) {
        val unit = size.width / DrawingBlock.WIDTH
        drawPaper(block.paper, block.height, colors, unit)
        drawStrokes(paths, colors, unit)
    }
}

/** Miniatura per le schede dell'elenco: solo la parte alta del disegno, alta [height]. */
@Composable
internal fun DrawingThumbnail(block: DrawingBlock, modifier: Modifier = Modifier) {
    val colors = sheetColors()
    val paths = remember(block.strokes) { block.strokes.map { it to it.toPath() } }
    Canvas(modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(10.dp)).clipToBounds()) {
        val unit = size.width / DrawingBlock.WIDTH
        drawPaper(block.paper, block.height, colors, unit)
        // Si centra la parte disegnata, se ce n'è una.
        val ys = block.strokes.flatMap { s -> s.points.map { it.y } }
        val top = if (ys.isEmpty()) 0f else ((ys.min() + ys.max()) / 2f * unit - size.height / 2f).coerceAtLeast(0f)
        translate(top = -top) { drawStrokes(paths, colors, unit) }
    }
}

/** Strumenti dell'editor: i quattro che lasciano un segno, più gomma e righello. */
private enum class EditorTool { Pen, Pencil, Marker, Highlighter, Eraser, Ruler }

private fun EditorTool.ink(): Tool = when (this) {
    EditorTool.Pencil -> Tool.Pencil
    EditorTool.Marker -> Tool.Marker
    EditorTool.Highlighter -> Tool.Highlighter
    else -> Tool.Pen
}

/** Spessori disponibili, in unità del disegno. */
private val SIZES = listOf(4f, 7f, 12f)

/**
 * Editor del disegno a schermo intero, come nell'anteprima: foglio a quadretti, righe o bianco; penna,
 * matita, pennarello, evidenziatore, gomma e righello; otto tinte più l'inchiostro; tre spessori.
 * Le forme tracciate a mano (linea, cerchio, rettangolo) diventano precise, con "Tieni a mano" per tornare
 * al tratto originale. Con il pennino il dito non disegna (si appoggia la mano senza lasciare segni).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DrawingEditor(
    initial: DrawingBlock,
    onDone: (DrawingBlock) -> Unit,
    onDelete: () -> Unit,
) {
    var strokes by remember { mutableStateOf(initial.strokes) }
    var height by remember { mutableIntStateOf(initial.height) }
    var paper by remember { mutableStateOf(initial.paper) }
    val undo = remember { mutableStateListOf<List<Stroke>>() }
    val redo = remember { mutableStateListOf<List<Stroke>>() }
    var tool by remember { mutableStateOf(EditorTool.Pen) }
    var tint by remember { mutableStateOf<String?>(null) }
    var penSize by remember { mutableFloatStateOf(SIZES[1]) }
    var current by remember { mutableStateOf<Stroke?>(null) }
    /** Ultima forma riconosciuta e il tratto da cui nasce, per "Tieni a mano". */
    var recognized by remember { mutableStateOf<Pair<Stroke, Stroke>?>(null) }
    var stylusSeen by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val haze = remember { HazeState() }
    val colors = sheetColors()

    fun finish() = onDone(initial.copy(height = height, paper = paper, strokes = strokes))
    fun commit(next: List<Stroke>) {
        undo += strokes
        if (undo.size > 100) undo.removeAt(0)
        redo.clear()
        strokes = next
    }
    BackHandler { finish() }
    LaunchedEffect(recognized) {
        if (recognized != null) {
            delay(4_000)
            recognized = null
        }
    }

    Box(Modifier.fillMaxSize().background(colors.paper)) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .hazeSource(haze)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 60.dp, bottom = 132.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val fit = minOf(maxWidth.value / DrawingBlock.WIDTH, maxHeight.value / height)
            val paths = remember(strokes) { strokes.map { it to it.toPath() } }
            Canvas(
                Modifier
                    .width((DrawingBlock.WIDTH * fit).dp)
                    .height((height * fit).dp)
                    .pointerInput(tool, tint, penSize) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (down.type == PointerType.Stylus || down.type == PointerType.Eraser) stylusSeen = true
                            if (stylusSeen && down.type == PointerType.Touch) return@awaitEachGesture
                            val unit = this.size.width / DrawingBlock.WIDTH.toFloat()
                            val erasing = tool == EditorTool.Eraser || down.type == PointerType.Eraser
                            fun point(pos: Offset, pressure: Float, type: PointerType) = InkPoint(
                                (pos.x / unit).coerceIn(0f, DrawingBlock.WIDTH.toFloat()),
                                (pos.y / unit).coerceIn(0f, height.toFloat()),
                                if (type == PointerType.Stylus) pressure.coerceIn(0.05f, 1f) else 0.5f,
                            )
                            down.consume()
                            if (erasing) {
                                val before = strokes
                                var left = strokes
                                fun erase(p: InkPoint) {
                                    left = left.filterNot { it.hits(p.x, p.y, 14f) }
                                    strokes = left
                                }
                                erase(point(down.position, 0.5f, down.type))
                                while (true) {
                                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    erase(point(change.position, 0.5f, change.type))
                                    change.consume()
                                }
                                if (left.size != before.size) {
                                    strokes = before
                                    commit(left)
                                }
                                return@awaitEachGesture
                            }
                            val points = mutableListOf(point(down.position, down.pressure, down.type))
                            val start = points.first()
                            current = Stroke(tool.ink(), tint, penSize, points.toList())
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if (tool == EditorTool.Ruler) {
                                    points.clear()
                                    points += start
                                    points += point(change.position, change.pressure, change.type)
                                } else {
                                    change.historical.forEach { h -> points += point(h.position, change.pressure, change.type) }
                                    points += point(change.position, change.pressure, change.type)
                                }
                                current = Stroke(tool.ink(), tint, penSize, points.toList())
                                change.consume()
                            }
                            val raw = Stroke(tool.ink(), tint, penSize, points.toList(), shape = tool == EditorTool.Ruler)
                            current = null
                            val shape = if (tool == EditorTool.Ruler || tool == EditorTool.Highlighter) null else raw.recognizeShape()
                            commit(strokes + (shape ?: raw))
                            recognized = shape?.let { raw to it }
                        }
                    },
            ) {
                val unit = this.size.width / DrawingBlock.WIDTH
                drawPaper(paper, height, colors, unit)
                drawStrokes(paths, colors, unit)
                current?.let { drawStrokes(listOf(it to it.toPath()), colors, unit) }
            }
        }

        // Testata satinata: indietro, annulla, ripeti, foglio, fine.
        Row(
            Modifier
                .fillMaxWidth()
                .glass(haze, RoundedCornerShape(0.dp), elevation = 0.dp)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { finish() }) {
                Icon(EuriseIcons.chevronLeft(), contentDescription = stringResource(R.string.notes_drawing_back), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                undo.removeLastOrNull()?.let { redo += strokes; strokes = it; recognized = null }
            }, enabled = undo.isNotEmpty()) {
                Icon(EuriseIcons.undo(), contentDescription = stringResource(R.string.notes_undo), modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = {
                redo.removeLastOrNull()?.let { undo += strokes; strokes = it }
            }, enabled = redo.isNotEmpty()) {
                Icon(EuriseIcons.redo(), contentDescription = stringResource(R.string.notes_redo), modifier = Modifier.size(20.dp))
            }
            Box {
                TextButton(
                    onClick = { menu = true },
                    modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer).height(36.dp),
                ) { Text(stringResource(paperLabel(paper)), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    Paper.entries.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(stringResource(paperLabel(p)), fontWeight = if (p == paper) FontWeight.Bold else FontWeight.Normal) },
                            onClick = { menu = false; paper = p },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.notes_drawing_more_space)) },
                        enabled = height < DrawingBlock.MAX_HEIGHT,
                        onClick = { menu = false; height = (height + 250).coerceAtMost(DrawingBlock.MAX_HEIGHT) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.notes_drawing_delete), color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onDelete() },
                    )
                }
            }
            TextButton(
                onClick = { finish() },
                modifier = Modifier.padding(start = 6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).height(36.dp),
            ) { Text(stringResource(R.string.notes_done), fontWeight = FontWeight.SemiBold) }
        }

        // Avviso della forma riconosciuta.
        recognized?.let { (raw, shape) ->
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = 156.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(start = 14.dp, end = 6.dp)
                    .height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.notes_drawing_shape), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
                TextButton(
                    onClick = {
                        strokes = strokes.map { if (it === shape) raw else it }
                        recognized = null
                    },
                    modifier = Modifier.height(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f)),
                ) { Text(stringResource(R.string.notes_drawing_keep_hand), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) }
            }
        }

        // Strumenti, colori e spessore.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(12.dp)
                .fillMaxWidth()
                .glass(haze, RoundedCornerShape(26.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                ToolChoice(EditorTool.Pen, tool, R.string.notes_tool_pen, EuriseIcons.pen()) { tool = it }
                ToolChoice(EditorTool.Pencil, tool, R.string.notes_tool_pencil, EuriseIcons.pencil()) { tool = it }
                ToolChoice(EditorTool.Marker, tool, R.string.notes_tool_marker, EuriseIcons.marker()) { tool = it }
                ToolChoice(EditorTool.Highlighter, tool, R.string.notes_tool_highlighter, EuriseIcons.highlighter()) { tool = it }
                ToolChoice(EditorTool.Eraser, tool, R.string.notes_tool_eraser, EuriseIcons.eraser()) { tool = it }
                ToolChoice(EditorTool.Ruler, tool, R.string.notes_tool_ruler, EuriseIcons.ruler()) { tool = it }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                InkChoice(stringResource(R.string.notes_drawing_ink), colors.ink, tint == null) { tint = null; if (tool == EditorTool.Eraser) tool = EditorTool.Pen }
                CalendarTint.entries.forEach { t ->
                    InkChoice(tintLabel(t), colors.tints.text(t.name), tint == t.name) { tint = t.name; if (tool == EditorTool.Eraser) tool = EditorTool.Pen }
                }
                Box(Modifier.size(width = 28.dp, height = 44.dp), contentAlignment = Alignment.Center) {
                    eu.eurise.core.designsystem.component.OwnColorSwatch(tint, onPick = { tint = it; if (tool == EditorTool.Eraser) tool = EditorTool.Pen }, size = 21.dp, ring = MaterialTheme.colorScheme.primary)
                }
                Box(Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.outlineVariant))
                val sizeLabel = stringResource(R.string.notes_drawing_size)
                Row(
                    Modifier
                        .size(width = 44.dp, height = 44.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { penSize = SIZES[(SIZES.indexOf(penSize) + 1) % SIZES.size] }
                        .semantics { contentDescription = sizeLabel },
                    horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SIZES.forEach { s ->
                        val d = (3 + SIZES.indexOf(s) * 3).dp
                        Box(Modifier.size(d).clip(CircleShape).background(if (s == penSize) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline))
                    }
                }
            }
        }
    }
}

private fun paperLabel(p: Paper) = when (p) {
    Paper.Blank -> R.string.notes_paper_blank
    Paper.Lines -> R.string.notes_paper_lines
    Paper.Grid -> R.string.notes_paper_grid
}

@Composable
private fun ToolChoice(value: EditorTool, current: EditorTool, label: Int, icon: ImageVector, onPick: (EditorTool) -> Unit) {
    val selected = value == current
    val text = stringResource(label)
    Box(
        Modifier
            .size(GlassDefaults.ItemHeight)
            .then(if (selected) Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape) else Modifier)
            .clip(CircleShape)
            .clickable(role = Role.RadioButton) { onPick(value) }
            .semantics { contentDescription = text; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun InkChoice(label: String, color: Color, selected: Boolean, onPick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(width = 28.dp, height = 44.dp)
            .clickable(role = Role.RadioButton, onClick = onPick)
            .semantics { contentDescription = label; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (selected) 24.dp else 21.dp)
                .then(if (selected) Modifier.border(2.dp, ring, CircleShape).padding(3.dp) else Modifier)
                .clip(CircleShape)
                .background(color),
        )
    }
}

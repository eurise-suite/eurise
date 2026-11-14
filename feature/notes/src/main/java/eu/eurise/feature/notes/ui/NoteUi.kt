package eu.eurise.feature.notes.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import eu.eurise.core.designsystem.theme.LocalEuriseColors
import eu.eurise.core.designsystem.theme.tintTones
import eu.eurise.feature.notes.doc.Mark
import eu.eurise.feature.notes.doc.RichText

/** Colori per il testo colorato e per l'evidenziatore, nel tema attuale. */
internal data class InkColors(val dark: Boolean) {
    fun text(tint: String): Color = tintTones(tint).let { if (dark) it.second else it.first }

    fun highlight(tint: String): Color = tintTones(tint).let {
        if (dark) lerp(Color(0xFF14171C), it.second, 0.38f) else lerp(Color.White, it.first, 0.24f)
    }

    /** Pallino della tinta (taccuini, scelta del colore). */
    fun dot(tint: String): Color = text(tint)
}

@Composable
@ReadOnlyComposable
internal fun inkColors() = InkColors(LocalEuriseColors.current.isDark)

/**
 * Il testo ricco come [AnnotatedString]: si divide il testo nei punti dove cambiano gli stili e a ogni
 * tratto si dà lo stile completo (così sottolineato e barrato insieme si vedono entrambi).
 */
internal fun RichText.toAnnotated(ink: InkColors, prefix: String = ""): AnnotatedString = buildAnnotatedString {
    append(prefix)
    val cuts = sortedSetOf(0, text.length)
    spans.forEach { cuts += it.start; cuts += it.end }
    val points = cuts.toList()
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        if (b <= a) continue
        val marks = spans.filter { it.start <= a && it.end >= b }.map { it.mark }
        if (marks.isEmpty()) {
            append(text.substring(a, b))
            continue
        }
        withStyle(styleFor(marks, ink)) { append(text.substring(a, b)) }
    }
}

private fun styleFor(marks: List<Mark>, ink: InkColors): SpanStyle {
    val decorations = buildList {
        if (Mark.Underline in marks) add(TextDecoration.Underline)
        if (Mark.Strike in marks) add(TextDecoration.LineThrough)
    }
    return SpanStyle(
        fontWeight = if (Mark.Bold in marks) FontWeight.Bold else null,
        fontStyle = if (Mark.Italic in marks) FontStyle.Italic else null,
        textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
        color = marks.filterIsInstance<Mark.Color>().firstOrNull()?.let { ink.text(it.tint) } ?: Color.Unspecified,
        background = marks.filterIsInstance<Mark.Highlight>().firstOrNull()?.let { ink.highlight(it.tint) } ?: Color.Unspecified,
    )
}

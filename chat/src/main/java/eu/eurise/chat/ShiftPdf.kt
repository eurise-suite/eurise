package eu.eurise.chat

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * I PDF dei turni, disegnati con il [PdfDocument] di Android (nessuna libreria): il **prospetto del mese** (persone ×
 * giorni, con le sigle colorate, le ore e la legenda; orizzontale, più pagine se le persone sono tante) e i **fogli
 * presenze** (una pagina per persona: giorno, turno, entrata, uscita, ore, straordinario e motivo, totali, spazi per le
 * firme). L'intestazione è quella scelta da chi coordina: ente, reparto, negozio, associazione.
 */
object ShiftPdf {
    private val IT = Locale.ITALIAN
    private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", IT)
    private val DAYNUM = DateTimeFormatter.ofPattern("EEE d", IT)
    private val HM = DateTimeFormatter.ofPattern("H:mm", IT)
    private val STAMP = DateTimeFormatter.ofPattern("d MMMM yyyy", IT)
    private val COLORS = listOf(0xFFDDE3F0, 0xFFF2E6CC, 0xFFE7DDE8, 0xFFDCE7DF, 0xFFF3DCD3, 0xFFD9E9EA).map { it.toInt() }
    private val INK = 0xFF1E1D1B.toInt()
    private val SOFT = 0xFF56564F.toInt()
    private val LINE = 0xFFC9C9C2.toInt()
    private val WEEKEND = 0xFFF1F1EC.toInt()

    private fun paint(size: Float, bold: Boolean = false, color: Int = INK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun fit(p: Paint, s: String, width: Float): String {
        if (p.measureText(s) <= width) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > width) t = t.dropLast(1)
        return "$t…"
    }

    /** Prospetto del mese. [effective] dice nel sottotitolo se sono i turni programmati o quelli svolti. */
    fun grid(out: File, title: String, plan: Shifts.Plan, entries: List<Shifts.Entry>, month: YearMonth,
             people: List<String>, name: (String) -> String, effective: Boolean) {
        val doc = PdfDocument()
        val w = 842; val h = 595; val m = 28f
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val nameW = 120f; val hoursW = 44f
        val colW = (w - 2 * m - nameW - hoursW) / days.size
        val rowH = 17f
        val top = 96f
        val perPage = ((h - top - 70f) / rowH).toInt().coerceAtLeast(1)
        val chunks = people.chunked(perPage).ifEmpty { listOf(emptyList()) }
        chunks.forEachIndexed { pi, chunk ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pi + 1).create())
            val c = page.canvas
            c.drawText(fit(paint(14f, true), title, w - 2 * m), m, m + 14f, paint(14f, true))
            c.drawText((if (effective) "Turni effettivi" else "Turni programmati") + " · " + month.format(MONTH) +
                if (chunks.size > 1) " · pagina ${pi + 1} di ${chunks.size}" else "", m, m + 32f, paint(10f, color = SOFT))
            // Intestazione dei giorni, con sabati, domeniche e festivi in grigio.
            days.forEachIndexed { i, d ->
                val x = m + nameW + i * colW
                val off = d.dayOfWeek.value >= 6 || Shifts.isHoliday(plan, d)
                if (off) c.drawRect(x, top - 26f, x + colW, top + chunk.size * rowH, Paint().apply { color = WEEKEND })
                val label = d.format(DAYNUM).split(' ')
                val p1 = paint(6.5f, color = SOFT); val p2 = paint(8.5f, true)
                c.drawText(label[0].take(3), x + (colW - p1.measureText(label[0].take(3))) / 2, top - 15f, p1)
                c.drawText(label.getOrElse(1) { "" }, x + (colW - p2.measureText(label.getOrElse(1) { "" })) / 2, top - 4f, p2)
            }
            c.drawText("Ore", m + nameW + days.size * colW + 6f, top - 4f, paint(8.5f, true))
            val line = Paint().apply { color = LINE; strokeWidth = 0.5f }
            chunk.forEachIndexed { r, p ->
                val y = top + r * rowH
                c.drawLine(m, y, w - m, y, line)
                c.drawText(fit(paint(9f, true), name(p), nameW - 6f), m, y + rowH - 5f, paint(9f, true))
                var minutes = 0L
                days.forEachIndexed { i, d ->
                    val here = entries.filter { it.date == d && p in it.people }
                    if (here.isEmpty()) return@forEachIndexed
                    val x = m + nameW + i * colW
                    val idx = plan.types.indexOfFirst { it.code == here.first().type }
                    if (idx >= 0) c.drawRect(x + 1f, y + 1.5f, x + colW - 1f, y + rowH - 1.5f, Paint().apply { color = COLORS[idx % COLORS.size] })
                    val label = here.joinToString("") { it.type }
                    val tp = paint(if (label.length > 2) 6.5f else 8.5f, true)
                    c.drawText(fit(tp, label, colW - 2f), x + (colW - tp.measureText(fit(tp, label, colW - 2f))) / 2, y + rowH - 5f, tp)
                    minutes += here.flatMap { Shifts.slots(plan, it) }.filter { it.person == p }.sumOf { it.minutes }
                }
                c.drawText(Shifts.duration(minutes), m + nameW + days.size * colW + 6f, y + rowH - 5f, paint(8.5f))
            }
            c.drawLine(m, top + chunk.size * rowH, w - m, top + chunk.size * rowH, line)
            // Legenda e piede.
            var x = m
            val ly = h - m - 18f
            plan.types.forEachIndexed { i, t ->
                c.drawRect(x, ly - 9f, x + 12f, ly + 2f, Paint().apply { color = COLORS[i % COLORS.size] })
                val s = "${t.code} ${t.name} ${t.start.format(HM)}–${t.end.format(HM)}"
                c.drawText(s, x + 16f, ly, paint(8.5f))
                x += 16f + paint(8.5f).measureText(s) + 14f
            }
            c.drawText("Fatto con Eurise Chat il ${LocalDate.now().format(STAMP)}", m, h - m, paint(7.5f, color = SOFT))
            doc.finishPage(page)
        }
        out.parentFile?.mkdirs()
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    /** Fogli presenze: una pagina per persona del mese, con totali e spazi per le firme. */
    fun presence(out: File, title: String, plan: Shifts.Plan, entries: List<Shifts.Entry>, extras: List<Shifts.Change>,
                 month: YearMonth, people: List<String>, name: (String) -> String) {
        val doc = PdfDocument()
        val w = 595; val h = 842; val m = 40f
        val cols = listOf("Giorno" to 70f, "Turno" to 90f, "Entrata" to 55f, "Uscita" to 55f, "Ore" to 50f, "Straord." to 55f, "Motivo" to 140f)
        people.forEachIndexed { pi, p ->
            val rows = ShiftsIO.presence(plan, entries, extras, month, p)
            val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pi + 1).create())
            val c = page.canvas
            c.drawText(fit(paint(14f, true), title, w - 2 * m), m, m + 14f, paint(14f, true))
            c.drawText("Foglio presenze · " + month.format(MONTH), m, m + 34f, paint(11f, color = SOFT))
            c.drawText(fit(paint(13f, true), name(p), w - 2 * m), m, m + 56f, paint(13f, true))
            var y = m + 84f
            val line = Paint().apply { color = LINE; strokeWidth = 0.6f }
            var x = m
            cols.forEach { (label, cw) -> c.drawText(label, x, y, paint(9f, true, SOFT)); x += cw }
            y += 6f
            c.drawLine(m, y, w - m, y, line)
            val rowH = 19f
            rows.forEach { r ->
                y += rowH
                val t = plan.type(r.type)
                val cells = listOf(
                    r.date.format(DAYNUM), t?.let { "${it.code} · ${it.name}" } ?: r.type, r.start.format(HM), r.end.format(HM),
                    Shifts.duration(r.minutes), if (r.extra > 0) Shifts.duration(r.extra) else "", r.reason,
                )
                x = m
                cells.forEachIndexed { i, s -> c.drawText(fit(paint(9.5f), s, cols[i].second - 4f), x, y - 5f, paint(9.5f)); x += cols[i].second }
                c.drawLine(m, y, w - m, y, line)
            }
            y += 22f
            c.drawText("Turni: ${rows.size} · Ore: ${Shifts.duration(rows.sumOf { it.minutes })}" +
                (rows.sumOf { it.extra }.takeIf { it > 0 }?.let { " · Straordinari: ${Shifts.duration(it)}" } ?: "") +
                " · Totale: ${Shifts.duration(rows.sumOf { it.minutes + it.extra })}", m, y, paint(10.5f, true))
            val fy = h - m - 30f
            c.drawLine(m, fy, m + 200f, fy, Paint().apply { color = INK; strokeWidth = 0.6f })
            c.drawText("Firma", m, fy + 14f, paint(9f, color = SOFT))
            c.drawLine(w - m - 200f, fy, w - m, fy, Paint().apply { color = INK; strokeWidth = 0.6f })
            c.drawText("Visto", w - m - 200f, fy + 14f, paint(9f, color = SOFT))
            c.drawText("Fatto con Eurise Chat il ${LocalDate.now().format(STAMP)}", m, h - m + 18f, paint(7.5f, color = SOFT))
            doc.finishPage(page)
        }
        if (people.isEmpty()) doc.finishPage(doc.startPage(PdfDocument.PageInfo.Builder(w, h, 1).create()))
        out.parentFile?.mkdirs()
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
    }
}

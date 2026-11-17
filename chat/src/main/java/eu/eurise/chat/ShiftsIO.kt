package eu.eurise.chat

import eu.eurise.chat.Shifts.Entry
import eu.eurise.chat.Shifts.Plan
import java.io.ByteArrayInputStream
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

/**
 * Importare ed esportare i turni, per ogni contesto (come in Bussola CA, ma senza legarsi a un ente o a un modulo).
 *
 * **Importare**: un file (PDF con testo, foglio di calcolo CSV, XLSX o ODS) o un testo incollato diventa una [Table];
 * [read] ci riconosce da solo una di queste forme e ne ricava le caselle:
 * - **righe per persona**: un'intestazione con i giorni (1 2 3 …, «lun 1», date) e sotto, per ogni persona, le sigle;
 * - **colonne per turno**: un'intestazione con i turni (Mattina, Notte…) e sotto, per ogni giorno, i nomi;
 * - **righe per giorno**: ogni riga comincia con una data e porta sigle e nomi («17/11 M Rossi N Bianchi»), anche come
 *   elenco «data; persona; turno».
 * Nomi e sigle che non si riconoscono tornano in [Import.unknownNames] e [Import.unknownCodes]: l'utente li abbina una
 * volta (alias) e si rilegge. Le scansioni senza testo non si leggono.
 *
 * **Esportare**: prospetto del mese in CSV (persone × giorni), elenco CSV, calendario .ics (per qualunque calendario),
 * righe dei fogli presenze ([presence]); i PDF li disegna [ShiftPdf] con queste stesse righe.
 */
object ShiftsIO {

    /** Una tabella letta da un file. [aligned]: le celle sono vere colonne (fogli di calcolo, testo con tabulazioni). */
    data class Table(val rows: List<List<String>>, val aligned: Boolean)

    /** Persona aggiunta dall'importazione o a mano, senza un account della Chat: l'id porta il nome. */
    const val EXTERNAL = "ext:"
    /** Valore di un alias che dice «ignora questo nome o questa sigla». */
    const val IGNORE = ""

    fun display(id: String): String = if (id.startsWith(EXTERNAL)) id.removePrefix(EXTERNAL) else id

    // --- Dai file alla tabella ---

    /** Riconosce il formato dai primi byte (e dal nome): PDF, XLSX, ODS, altrimenti testo (CSV o incollato). */
    fun table(name: String, bytes: ByteArray, pdfText: (ByteArray) -> String): Table {
        val head = String(bytes.copyOfRange(0, minOf(bytes.size, 5)), Charsets.ISO_8859_1)
        return when {
            head.startsWith("%PDF") -> text(pdfText(bytes))
            head.startsWith("PK") -> zipTable(bytes) ?: Table(emptyList(), true)
            name.lowercase().endsWith(".csv") || name.lowercase().endsWith(".tsv") -> csv(decode(bytes))
            else -> decode(bytes).let { t -> if (looksCsv(t)) csv(t) else text(t) }
        }
    }

    private fun decode(bytes: ByteArray): String {
        val utf = String(bytes, Charsets.UTF_8)
        // Excel salva spesso in Windows-1252: se l'UTF-8 non torna, si rilegge così.
        return (if ('\uFFFD' in utf) String(bytes, charset("windows-1252")) else utf).removePrefix("\uFEFF")
    }

    private fun looksCsv(t: String): Boolean {
        val lines = t.lines().filter { it.isNotBlank() }.take(8)
        if (lines.size < 2) return false
        return listOf(';', '\t', ',').any { d -> lines.all { it.count { c -> c == d } >= 2 } }
    }

    /** CSV con separatore riconosciuto (punto e virgola, tabulazione, virgola) e virgolette. */
    fun csv(t: String): Table {
        val lines = t.lines().filter { it.isNotBlank() }
        val sample = lines.take(10)
        val d = listOf(';', '\t', ',').maxBy { c -> sample.sumOf { l -> l.count { it == c } } }
        val rows = lines.map { line ->
            val cells = ArrayList<String>(); val cur = StringBuilder(); var q = false; var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    c == '"' && q && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                    c == '"' -> q = !q
                    c == d && !q -> { cells += cur.toString().trim(); cur.clear() }
                    else -> cur.append(c)
                }
                i++
            }
            cells += cur.toString().trim()
            cells
        }
        return Table(rows, true)
    }

    /**
     * Testo da PDF o incollato: con tabulazioni, «|» o due spazi le celle sono colonne; altrimenti ogni parola è una cella
     * e conta l'ordine.
     */
    fun text(t: String): Table {
        val lines = t.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val aligned = lines.count { '\t' in it || " | " in it } > lines.size / 2
        val rows = lines.map { l ->
            if (aligned) l.split('\t', '|').map { it.trim() }
            else l.split(Regex("[\\s,;]+")).filter { it.isNotEmpty() && it != "|" }
        }
        return Table(rows, aligned)
    }

    private fun zipTable(bytes: ByteArray): Table? {
        val files = HashMap<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            var total = 0L
            while (e != null) {
                val n = e.name
                if (n == "content.xml" || n == "xl/sharedStrings.xml" || n == "xl/workbook.xml" ||
                    (n.startsWith("xl/worksheets/sheet") && n.endsWith(".xml"))) {
                    val data = z.readBytes()
                    total += data.size
                    if (total > 40L * 1024 * 1024) return null
                    files[n] = String(data, Charsets.UTF_8)
                }
                e = z.nextEntry
            }
        }
        files["content.xml"]?.let { return ods(it) }
        val sheet = files.keys.filter { it.startsWith("xl/worksheets/sheet") }.minByOrNull { it.filter(Char::isDigit).toIntOrNull() ?: 99 } ?: return null
        return xlsx(files.getValue(sheet), files["xl/sharedStrings.xml"])
    }

    private fun unescape(s: String): String = s.replace(Regex("&#(x?)([0-9a-fA-F]+);")) { m ->
        val n = if (m.groupValues[1].isNotEmpty()) m.groupValues[2].toInt(16) else m.groupValues[2].toInt()
        String(Character.toChars(n))
    }.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

    private fun texts(xml: String): String = Regex("<(?:\\w+:)?t(?:\\s[^>]*)?>(.*?)</(?:\\w+:)?t>", RegexOption.DOT_MATCHES_ALL)
        .findAll(xml).joinToString("") { unescape(it.groupValues[1]) }

    /** Foglio XLSX: celle condivise, in linea o numeriche; le colonne dalle lettere del riferimento (A1, B1…). */
    fun xlsx(sheet: String, shared: String?): Table {
        val strings = shared?.let { s -> Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(s).map { texts(it.groupValues[1]) }.toList() }.orEmpty()
        val rows = Regex("<row\\b[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(sheet).take(500).map { row ->
            val cells = sortedMapOf<Int, String>()
            Regex("<c\\b([^>]*?)(?:/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL).findAll(row.groupValues[1]).forEach { c ->
                val attrs = c.groupValues[1]
                val ref = Regex("r=\"([A-Z]+)\\d+\"").find(attrs)?.groupValues?.get(1) ?: return@forEach
                val col = ref.fold(0) { a, ch -> a * 26 + (ch - 'A' + 1) } - 1
                if (col > 80) return@forEach
                val type = Regex("t=\"(\\w+)\"").find(attrs)?.groupValues?.get(1)
                val body = c.groupValues[2]
                val v = Regex("<v>(.*?)</v>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)?.let(::unescape)
                cells[col] = when (type) {
                    "s" -> v?.toIntOrNull()?.let { strings.getOrNull(it) }.orEmpty()
                    "inlineStr" -> texts(body)
                    else -> v.orEmpty()
                }.trim()
            }
            val width = (cells.keys.maxOrNull() ?: -1) + 1
            List(width) { cells[it].orEmpty() }
        }.toList()
        return Table(rows, true)
    }

    /** Foglio ODS (LibreOffice): prima tabella, celle ripetute espanse con un tetto, date dal valore della cella. */
    fun ods(content: String): Table {
        val table = Regex("<table:table\\b.*?</table:table>", RegexOption.DOT_MATCHES_ALL).find(content)?.value ?: return Table(emptyList(), true)
        val rows = ArrayList<List<String>>()
        Regex("<table:table-row\\b([^>]*)>(.*?)</table:table-row>", RegexOption.DOT_MATCHES_ALL).findAll(table).forEach { r ->
            if (rows.size >= 500) return@forEach
            val cells = ArrayList<String>()
            Regex("<table:(?:covered-)?table-cell\\b([^>]*?)(?:/>|>(.*?)</table:(?:covered-)?table-cell>)", RegexOption.DOT_MATCHES_ALL)
                .findAll(r.groupValues[2]).forEach { c ->
                    val attrs = c.groupValues[1]
                    val rep = Regex("table:number-columns-repeated=\"(\\d+)\"").find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                    val date = Regex("office:date-value=\"([0-9-]{10})").find(attrs)?.groupValues?.get(1)
                    val text = date ?: Regex("<text:p\\b[^>]*>(.*?)</text:p>", RegexOption.DOT_MATCHES_ALL).findAll(c.groupValues[2])
                        .joinToString(" ") { unescape(it.groupValues[1].replace(Regex("<[^>]+>"), "")) }
                    repeat(minOf(rep, 64 - cells.size).coerceAtLeast(0)) { cells += text.trim() }
                }
            val trimmed = cells.dropLastWhile { it.isEmpty() }
            val rep = Regex("table:number-rows-repeated=\"(\\d+)\"").find(r.groupValues[1])?.groupValues?.get(1)?.toIntOrNull() ?: 1
            if (trimmed.isNotEmpty()) repeat(minOf(rep, 500 - rows.size)) { rows += trimmed }
        }
        return Table(rows, true)
    }

    // --- Riconoscere date, sigle e nomi ---

    fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        .lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private val MONTHS = listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre")
    private val WEEKDAYS = listOf("lunedi", "martedi", "mercoledi", "giovedi", "venerdi", "sabato", "domenica")
    private val REST = setOf("-", "–", "—", "·", ".", "/", "x", "r", "rip", "riposo", "libero", "lib", "off", "ferie", "fe", "fer", "mal", "malattia", "perm", "permesso", "smonto", "s", "0")
    private val WORDS = setOf("nome", "cognome", "nominativo", "turno", "turni", "data", "giorno", "giorni", "totale", "tot", "ore", "mese", "anno",
        "settimana", "medico", "medici", "operatore", "operatori", "persona", "persone", "chi", "orario", "dalle", "alle", "e", "di", "del", "della")

    private fun month(s: String): Int? {
        val n = norm(s)
        if (n.length < 3 || n.any { !it.isLetter() }) return null
        return MONTHS.indexOfFirst { it.startsWith(n) || (n == "sett" && it == "settembre") }.takeIf { it >= 0 }?.plus(1)
    }

    fun isWeekday(s: String): Boolean {
        val n = norm(s)
        if (n.isEmpty() || n.any { !it.isLetter() }) return false
        return (n.length >= 3 && WEEKDAYS.any { it.startsWith(n) }) || n in setOf("lu", "ma", "me", "gi", "ve", "sa", "do")
    }

    /**
     * Una data in una cella o in poche parole: «2026-11-17», «17/11», «17/11/26», «17 nov», «lunedì 17 novembre», un numero
     * di serie di Excel; con [bare] anche il solo giorno del mese di [ym].
     */
    fun date(s: String, ym: YearMonth, bare: Boolean): LocalDate? {
        val t = s.trim()
        if (t.isEmpty()) return null
        Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})").find(t)?.let { m ->
            return runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
        }
        Regex("^(?:[A-Za-zÀ-ÿ]+\\.?\\s+)?(\\d{1,2})[/.-](\\d{1,2})(?:[/.-](\\d{2,4}))?$").find(t)?.let { m ->
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it } ?: ym.year
            return runCatching { LocalDate.of(y, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()
        }
        // Numero di serie delle date di Excel (giorni dal 30 dicembre 1899), tra il 2009 e il 2064.
        t.toDoubleOrNull()?.let { v -> if (v in 40000.0..60000.0 && v == Math.floor(v)) return LocalDate.of(1899, 12, 30).plusDays(v.toLong()) }
        // A parole: prima del giorno solo il giorno della settimana («lun 17»), dopo il mese e l'anno («17 mar 2027»).
        val words = t.split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        val di = words.indexOfFirst { it.trimEnd('.').toIntOrNull() in 1..31 }
        if (di < 0 || words.take(di).any { !isWeekday(it) }) return null
        val day = words[di].trimEnd('.').toInt()
        val after = words.drop(di + 1)
        val mo = after.firstNotNullOfOrNull { month(it) }
        val year = after.firstOrNull { it.toIntOrNull() in 2000..2100 }?.toInt()
        if (after.any { month(it) == null && it.toIntOrNull() !in 2000..2100 && !isWeekday(it) }) return null
        if (mo == null && !bare) return null
        return runCatching { LocalDate.of(year ?: ym.year, mo ?: ym.monthValue, day) }.getOrNull()
    }

    /** Cosa c'è in una cella: un turno (sigla), un riposo, o niente di riconosciuto. */
    sealed interface Code {
        data class Shift(val type: String) : Code
        data object Rest : Code
    }

    /**
     * Abbina sigle e nomi. [names] dà il nome mostrato di ogni persona del piano; si riconoscono il nome intero, il cognome
     * o il nome se non si confondono, «Rossi M.», la parte prima dei due punti dell'id della Chat. [aliases] e [codeAliases]
     * (chiave = [norm]) sono gli abbinamenti scelti dall'utente; [IGNORE] li salta.
     */
    class Matcher(
        val plan: Plan,
        names: Map<String, String>,
        private val aliases: Map<String, String> = emptyMap(),
        private val codeAliases: Map<String, String> = emptyMap(),
    ) {
        private val keys = HashMap<String, MutableSet<String>>()

        init {
            fun add(k: String, id: String) { if (k.length >= 2) keys.getOrPut(k) { HashSet() } += id }
            val ids = (plan.people.map { it.id } + names.keys).distinct()
            for (id in ids) {
                val shown = names[id] ?: display(id)
                val n = norm(shown)
                add(n, id)
                val w = n.split(' ').filter { it.length >= 2 }
                w.forEach { add(it, id) }
                if (w.size >= 2) {
                    add("${w.last()} ${w.first().first()}", id); add("${w.first().first()} ${w.last()}", id)
                    add("${w.first()} ${w.last().first()}", id); add(w.reversed().joinToString(" "), id)
                }
                if (id.startsWith("@")) add(norm(id.removePrefix("@").substringBefore(':')), id)
            }
        }

        /** La persona di una cella o di [n] parole; null = non riconosciuta; "" = da ignorare. */
        fun person(text: String): String? {
            val n = norm(text)
            if (n.isEmpty()) return null
            aliases[n]?.let { return it }
            return keys[n]?.singleOrNull()
        }

        fun code(text: String): Code? {
            val raw = text.trim()
            if (raw.isEmpty()) return Code.Rest
            val n = norm(raw)
            codeAliases[n]?.let { return if (it == IGNORE) Code.Rest else Code.Shift(it) }
            plan.types.firstOrNull { it.code.equals(raw.trimEnd('.'), ignoreCase = true) }?.let { return Code.Shift(it.code) }
            plan.types.firstOrNull { norm(it.name) == n }?.let { return Code.Shift(it.code) }
            if (raw in REST || n in REST) return Code.Rest
            if (n.length >= 3) plan.types.firstOrNull { norm(it.name).startsWith(n) }?.let { return Code.Shift(it.code) }
            return null
        }

        /** Sembra un nome (non sigla, data, numero o parola d'intestazione): è da chiedere all'utente. */
        fun nameLike(text: String): Boolean {
            val n = norm(text)
            return n.length >= 2 && n.any { it.isLetter() } && n.none { it.isDigit() } && n !in WORDS &&
                n.split(' ').none { it in WORDS } && month(n) == null && !isWeekday(n)
        }

        /** Sembra una sigla di turno sconosciuta: corta, maiuscola, non un nome noto. */
        fun codeLike(text: String): Boolean {
            val t = text.trim()
            return t.length in 1..4 && t.any { it.isLetter() } && t.none { it.isWhitespace() } && t == t.uppercase() && person(t) == null
        }
    }

    // --- Dalla tabella alle caselle ---

    data class Import(
        val entries: List<Entry>,
        val unknownNames: List<String>,
        val unknownCodes: List<String>,
        val rowsRead: Int,
        val rowsSkipped: Int,
        val layout: Layout,
    ) {
        val from: LocalDate? get() = entries.minOfOrNull { it.date }
        val to: LocalDate? get() = entries.maxOfOrNull { it.date }
    }

    enum class Layout { PersonRows, TypeColumns, DayRows, Nothing }

    private class Collector(val m: Matcher) {
        val assign = LinkedHashMap<Pair<LocalDate, String>, LinkedHashSet<String>>()
        val unknownNames = LinkedHashMap<String, Int>()
        val unknownCodes = LinkedHashMap<String, Int>()
        var read = 0; var skipped = 0
        fun put(d: LocalDate, type: String, person: String) { if (person != IGNORE) assign.getOrPut(d to type) { LinkedHashSet() } += person }
        fun unknownName(s: String) { unknownNames.merge(s.trim(), 1, Int::plus) }
        fun unknownCode(s: String) { unknownCodes.merge(s.trim(), 1, Int::plus) }
    }

    /** Legge [table] per il mese [ym] (usato quando le date hanno solo il giorno). */
    fun read(table: Table, matcher: Matcher, ym: YearMonth): Import {
        val c = Collector(matcher)
        val rows = table.rows.map { r -> r.map { it.trim() } }.filter { r -> r.any { it.isNotEmpty() } }
        val layout = personRows(rows, table.aligned, c, ym) ?: typeColumns(rows, c, ym) ?: dayRows(rows, table.aligned, c, ym)
        val entries = c.assign.entries.map { (k, ps) ->
            Entry(k.first, k.second, ps.toList(), phone = null, locked = true)
        }.sortedWith(compareBy<Entry> { it.date }.thenBy { matcher.plan.type(it.type)?.start })
        return Import(entries, c.unknownNames.entries.sortedByDescending { it.value }.map { it.key },
            c.unknownCodes.entries.sortedByDescending { it.value }.map { it.key }, c.read, c.skipped,
            if (entries.isEmpty() && c.unknownNames.isEmpty()) Layout.Nothing else layout)
    }

    /** Intestazione con i giorni: almeno cinque date, in ordine. Restituisce colonna (o posizione) → data. */
    private fun dateHeader(row: List<String>, aligned: Boolean, ym: YearMonth): List<Pair<Int, LocalDate>>? {
        val cells = if (aligned) row.withIndex().toList() else row.withIndex().filter { !isWeekday(it.value) }
        val dates = cells.mapNotNull { (i, s) -> date(s, ym, bare = true)?.let { i to it } }
        if (dates.size < 5) return null
        if (dates.zipWithNext().count { (a, b) -> b.second.isAfter(a.second) } < dates.size - 2) return null
        // Senza colonne vere contano solo le date in fila: il resto (nomi del mese, giorni della settimana) si scarta.
        if (!aligned && dates.size < cells.size / 2) return null
        return dates
    }

    private fun personRows(rows: List<List<String>>, aligned: Boolean, c: Collector, ym: YearMonth): Layout? {
        val hi = rows.indexOfFirst { dateHeader(it, aligned, ym) != null }
        if (hi < 0) return null
        var header = dateHeader(rows[hi], aligned, ym)!!
        val m = c.m
        for (r in rows.drop(hi + 1)) {
            // Un'altra intestazione (un altro mese o un'altra settimana più sotto) cambia le colonne.
            val again = dateHeader(r, aligned, ym)
            if (again != null) { header = again; continue }
            if (aligned) {
                val first = header.first().first
                val label = r.take(first).filter { it.isNotEmpty() }.joinToString(" ")
                if (label.isEmpty()) { c.skipped++; continue }
                val who = m.person(label) ?: r.take(first).firstNotNullOfOrNull { m.person(it) }
                if (who == null) { if (m.nameLike(label)) c.unknownName(label) else c.skipped++; continue }
                c.read++
                for ((col, d) in header) {
                    val cell = r.getOrNull(col).orEmpty()
                    cell.split('/', '+', ',', ' ').filter { it.isNotEmpty() }.ifEmpty { listOf("") }.forEach { part ->
                        when (val k = m.code(part)) {
                            is Code.Shift -> c.put(d, k.type, who)
                            Code.Rest -> Unit
                            null -> if (m.codeLike(part)) c.unknownCode(part)
                        }
                    }
                }
            } else {
                // Testo senza colonne: il nome all'inizio (fino a tre parole), poi una sigla per giorno, in ordine.
                var who: String? = null; var used = 0
                for (k in minOf(3, r.size) downTo 1) {
                    // «Rossi M.» è un nome, ma una «M» in fondo è più probabilmente il primo turno.
                    if (k > 1 && m.code(r[k - 1]) is Code.Shift) continue
                    val p = m.person(r.take(k).joinToString(" "))
                    if (p != null) { who = p; used = k; break }
                }
                if (who == null) {
                    val label = r.takeWhile { m.code(it) == null && date(it, ym, false) == null }.take(3)
                    if (label.isNotEmpty() && m.nameLike(label.joinToString(" "))) {
                        c.unknownName(label.joinToString(" "))
                    } else c.skipped++
                    continue
                }
                val codes = r.drop(used).filter { t -> m.code(t) != null || m.codeLike(t) }
                if (codes.size < header.size) { c.skipped++; continue }
                c.read++
                header.forEachIndexed { i, (_, d) ->
                    when (val k = m.code(codes[i])) {
                        is Code.Shift -> c.put(d, k.type, who)
                        Code.Rest -> Unit
                        null -> c.unknownCode(codes[i])
                    }
                }
            }
        }
        return Layout.PersonRows
    }

    private fun splitNames(cell: String): List<String> =
        cell.split(Regex("\\s*(?:,|/|\\+|;|&|\\be\\b)\\s*")).map { it.trim() }.filter { it.isNotEmpty() }

    private fun typeColumns(rows: List<List<String>>, c: Collector, ym: YearMonth): Layout? {
        val m = c.m
        val hi = rows.indexOfFirst { r -> r.count { s -> s.isNotEmpty() && (m.code(s) as? Code.Shift) != null } >= 2 && r.none { date(it, ym, false) != null } }
        if (hi < 0) return null
        val cols = rows[hi].withIndex().mapNotNull { (i, s) -> (m.code(s) as? Code.Shift)?.let { i to it.type } }
        var any = false
        for (r in rows.drop(hi + 1)) {
            val d = r.take(3).firstNotNullOfOrNull { date(it, ym, bare = true) }
            if (d == null) { c.skipped++; continue }
            c.read++; any = true
            for ((col, type) in cols) {
                splitNames(r.getOrNull(col).orEmpty()).forEach { n ->
                    val p = m.person(n)
                    when {
                        p != null -> c.put(d, type, p)
                        m.code(n) == Code.Rest -> Unit
                        m.nameLike(n) -> c.unknownName(n)
                    }
                }
            }
        }
        return if (any) Layout.TypeColumns else null
    }

    private fun dayRows(rows: List<List<String>>, aligned: Boolean, c: Collector, ym: YearMonth): Layout {
        val m = c.m
        for (r in rows) {
            // La data: nelle prime celle (anche «lun 17 nov» spezzato in parole).
            var d: LocalDate? = null; var used = 0
            for (k in minOf(4, r.size) downTo 1) {
                val got = date(r.subList(0, k).joinToString(" "), ym, bare = true)
                if (got != null) { d = got; used = k; break }
            }
            // Un elenco «persona; data; turno» ha la data in mezzo: si cerca in tutta la riga.
            var restCells = r.drop(used)
            if (d == null && aligned) {
                val i = r.indexOfFirst { date(it, ym, bare = false) != null }
                if (i >= 0) { d = date(r[i], ym, false); restCells = r.filterIndexed { j, _ -> j != i } }
            }
            if (d == null) { c.skipped++; continue }
            // Pezzi: sigle e nomi, nell'ordine.
            val tokens = ArrayList<Pair<Code.Shift?, String?>>()
            val cells = if (aligned) restCells.flatMap { cell -> if (m.code(cell) != null || m.person(cell) != null) listOf(cell) else splitNames(cell) } else restCells
            var i = 0
            while (i < cells.size) {
                val t = cells[i]
                val k = m.code(t)
                if (k is Code.Shift) { tokens += k to null; i++; continue }
                if (k == Code.Rest && t.isNotEmpty()) { i++; continue }
                var matched = false
                if (!aligned) for (n in minOf(3, cells.size - i) downTo 1) {
                    if (n > 1 && m.code(cells[i + n - 1]) is Code.Shift) continue
                    val p = m.person(cells.subList(i, i + n).joinToString(" "))
                    if (p != null) { tokens += null to p; i += n; matched = true; break }
                } else m.person(t)?.let { tokens += null to it; i++; matched = true }
                if (!matched) {
                    when {
                        t.isEmpty() -> Unit
                        m.nameLike(t) -> { c.unknownName(t); tokens += null to null }
                        m.codeLike(t) -> c.unknownCode(t)
                    }
                    i++
                }
            }
            val codes = tokens.mapNotNull { it.first }
            val people = tokens.filter { it.first == null }
            if (people.isEmpty()) { c.skipped++; continue }
            c.read++
            when {
                codes.size == 1 -> people.forEach { (_, p) -> p?.let { c.put(d, codes[0].type, it) } }
                codes.isEmpty() -> {
                    // Solo nomi: vanno ai turni di quel giorno, in ordine d'inizio, quante persone serve ciascuno.
                    val seats = Shifts.typesOn(m.plan, d).flatMap { t -> List(t.people) { t.code } }
                    people.forEachIndexed { j, (_, p) -> if (p != null && j < seats.size) c.put(d, seats[j], p) }
                }
                else -> {
                    // Più sigle: se la riga comincia con una sigla i nomi la seguono («M Rossi N Bianchi»),
                    // altrimenti la precedono («Rossi M, Bianchi N»).
                    val codeFirst = tokens.first().first != null
                    var cur: String? = null
                    val pending = ArrayList<String?>()
                    for ((k, p) in tokens) {
                        if (k != null) {
                            if (codeFirst) cur = k.type
                            else { pending.forEach { it?.let { x -> c.put(d, k.type, x) } }; pending.clear() }
                        } else if (codeFirst) { if (cur != null && p != null) c.put(d, cur, p) } else pending += p
                    }
                }
            }
        }
        return Layout.DayRows
    }

    /**
     * Mette le caselle importate nel piano: con [replace] quelle del periodo importato prendono il posto delle vecchie
     * (tranne nei giorni fuori dal periodo); altrimenti si aggiungono alle persone già presenti.
     */
    fun merge(plan: Plan, imported: List<Entry>, replace: Boolean): List<Entry> {
        val from = imported.minOfOrNull { it.date } ?: return plan.entries
        val to = imported.maxOf { it.date }
        val base = if (replace) plan.entries.filter { it.date.isBefore(from) || it.date.isAfter(to) } else plan.entries
        val byKey = LinkedHashMap<String, Entry>()
        base.forEach { byKey[it.key] = it }
        imported.forEach { e ->
            val old = byKey[e.key]
            byKey[e.key] = if (old == null) e else old.copy(people = (old.people + e.people).distinct(), locked = true)
        }
        return byKey.values.sortedWith(compareBy<Entry> { it.date }.thenBy { plan.type(it.type)?.start })
    }

    // --- Esportare ---

    private val DAY = DateTimeFormatter.ofPattern("EEE d", java.util.Locale.ITALIAN)
    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE
    private val HM = DateTimeFormatter.ofPattern("HH:mm")

    private fun csvCell(s: String) = if (s.any { it == ';' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** Persone del mese: quelle del piano, poi chi compare nelle caselle. */
    fun people(plan: Plan, entries: List<Entry>, month: YearMonth): List<String> =
        (plan.people.filter { it.role != Shifts.Role.External }.map { it.id } +
            entries.filter { YearMonth.from(it.date) == month }.flatMap { it.people }).distinct()

    /**
     * Prospetto del mese per i fogli di calcolo: una riga per persona, una colonna per giorno, la sigla nella cella, le ore
     * in fondo; sotto la legenda. Punto e virgola e BOM, così Excel e LibreOffice lo aprono in italiano.
     */
    fun csvGrid(plan: Plan, entries: List<Entry>, month: YearMonth, name: (String) -> String): String {
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val sb = StringBuilder("\uFEFF")
        if (plan.title.isNotBlank()) sb.append(csvCell(plan.title)).append("\r\n")
        sb.append("Nome;").append(days.joinToString(";") { it.format(DAY) }).append(";Ore\r\n")
        for (p in people(plan, entries, month)) {
            val mine = entries.filter { YearMonth.from(it.date) == month && p in it.people }
            val minutes = mine.flatMap { Shifts.slots(plan, it) }.filter { it.person == p }.sumOf { it.minutes }
            sb.append(csvCell(name(p))).append(';')
            sb.append(days.joinToString(";") { d -> mine.filter { it.date == d }.joinToString("+") { it.type } })
            sb.append(';').append(Shifts.duration(minutes)).append("\r\n")
        }
        sb.append("\r\n")
        plan.types.forEach { t -> sb.append(csvCell("${t.code} = ${t.name} ${t.start.format(HM)}–${t.end.format(HM)}")).append("\r\n") }
        return sb.toString()
    }

    /** Elenco del mese, una riga per persona e turno, con straordinari: per chi deve rifare i conti. */
    fun csvList(plan: Plan, entries: List<Entry>, extras: List<Shifts.Change>, month: YearMonth, name: (String) -> String): String {
        val sb = StringBuilder("\uFEFFData;Giorno;Turno;Nome turno;Inizio;Fine;Ore;Persona;Telefono;Straordinario;Motivo\r\n")
        entries.filter { YearMonth.from(it.date) == month }.sortedWith(compareBy<Entry> { it.date }.thenBy { plan.type(it.type)?.start }).forEach { e ->
            val t = plan.type(e.type)
            Shifts.slots(plan, e).forEach { s ->
                val ex = extras.filter { it.date == e.date && it.type == e.type && it.by == s.person }
                sb.append(listOf(e.date.format(ISO), e.date.format(DAY), e.type, t?.name.orEmpty(), s.start.format(HM), s.end.format(HM),
                    Shifts.duration(s.minutes), name(s.person), if (e.phone == s.person) "sì" else "",
                    ex.sumOf { it.minutes }.takeIf { it > 0 }?.let { Shifts.duration(it.toLong()) }.orEmpty(),
                    ex.joinToString(", ") { it.reason }).joinToString(";") { csvCell(it) }).append("\r\n")
            }
        }
        return sb.toString()
    }

    private fun icsText(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n")

    /** Righe lunghe piegate a 75 byte, come vuole il formato del calendario. */
    private fun fold(line: String): String {
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (bytes.size <= 75) return line
        val out = StringBuilder(); var count = 0
        for (ch in line) {
            val n = ch.toString().toByteArray(Charsets.UTF_8).size
            if (count + n > (if (out.isEmpty()) 75 else 74)) { out.append("\r\n "); count = 0 }
            out.append(ch); count += n
        }
        return out.toString()
    }

    /**
     * Calendario .ics dei turni da [from] in poi (di [only] o di tutti), da aprire in qualunque calendario: Eurise,
     * Google, Apple, Outlook, Thunderbird. Orari in UTC, identificativi stabili così un nuovo file aggiorna il vecchio.
     */
    fun ics(plan: Plan, entries: List<Entry>, from: LocalDate, group: String, name: (String) -> String, only: String?, zone: ZoneId, now: Instant): String {
        val utc = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        fun at(t: LocalDateTime) = t.atZone(zone).withZoneSameInstant(ZoneOffset.UTC).format(utc)
        val lines = arrayListOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Eurise//Chat Turni//IT", "CALSCALE:GREGORIAN", "METHOD:PUBLISH",
            "X-WR-CALNAME:" + icsText(listOf("Turni", group).filter { it.isNotBlank() }.joinToString(" · ")))
        for (e in entries) {
            if (e.date.isBefore(from)) continue
            val t = plan.type(e.type) ?: continue
            for (s in Shifts.slots(plan, e)) {
                if (only != null && s.person != only) continue
                val others = e.people.filter { it != s.person }.joinToString(", ") { name(it) }
                lines += "BEGIN:VEVENT"
                lines += "UID:" + "${plan.id}-${e.date}-${e.type}-${norm(name(s.person)).replace(' ', '-')}@eurise"
                lines += "DTSTAMP:" + now.atOffset(ZoneOffset.UTC).format(utc)
                lines += "DTSTART:" + at(s.start)
                lines += "DTEND:" + at(s.end)
                lines += "SUMMARY:" + icsText("${t.code} · ${t.name}" + (if (only == null) " · ${name(s.person)}" else "") + (if (group.isNotBlank()) " · $group" else ""))
                if (others.isNotEmpty() || e.phone == s.person) lines += "DESCRIPTION:" + icsText(listOfNotNull(others.takeIf { it.isNotEmpty() }, if (e.phone == s.person && e.people.size > 1) "Tiene il telefono" else null).joinToString("\n"))
                lines += "END:VEVENT"
            }
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n") { fold(it) } + "\r\n"
    }

    /** Una riga del foglio presenze: giorno, turno, entrata, uscita, ore, straordinario. */
    data class Presence(val date: LocalDate, val type: String, val start: LocalDateTime, val end: LocalDateTime, val minutes: Long, val extra: Long, val reason: String)

    fun presence(plan: Plan, entries: List<Entry>, extras: List<Shifts.Change>, month: YearMonth, person: String): List<Presence> =
        entries.filter { YearMonth.from(it.date) == month && person in it.people }.sortedBy { it.date }.flatMap { e ->
            Shifts.slots(plan, e).filter { it.person == person }.map { s ->
                val ex = extras.filter { it.date == e.date && it.type == e.type && it.by == person }
                Presence(e.date, e.type, s.start, s.end.plusMinutes(ex.sumOf { it.minutes }.toLong()), s.minutes, ex.sumOf { it.minutes }.toLong(), ex.joinToString(", ") { it.reason })
            }
        }
}

package eu.eurise.office.rapida

import kotlin.math.abs

/**
 * Le formule che la vista rapida sa ricalcolare da sola: numeri, riferimenti e intervalli dello stesso foglio, + − × ÷,
 * parentesi e SUM, AVERAGE, MIN, MAX, COUNT, PRODUCT, ROUND, ABS. Tutto il resto (altri fogli, condizioni, testo,
 * funzioni diverse) è [Unsupported]: quella formula si aggiorna nella modifica completa.
 */
class Formula private constructor(private val root: Node, val refs: List<Area>) {
    class Unsupported(why: String) : Exception(why)

    /** Un'area rettangolare (una cella è un'area 1×1), con indici da 0. */
    data class Area(val c1: Int, val r1: Int, val c2: Int, val r2: Int) {
        fun contains(col: Int, row: Int) = col in c1..c2 && row in r1..r2
    }

    private sealed interface Node
    private data class Num(val v: Double) : Node
    private data class Ref(val area: Area, val single: Boolean) : Node
    private data class Unary(val minus: Boolean, val e: Node) : Node
    private data class Bin(val op: Char, val a: Node, val b: Node) : Node
    private data class Call(val name: String, val args: List<Node>) : Node

    fun dependsOn(col: Int, row: Int) = refs.any { it.contains(col, row) }

    /** Il valore, con [value] che dà il numero di una cella (null se vuota o testo). */
    fun eval(value: (Int, Int) -> Double?): Double {
        val v = eval(root, value)
        if (v.isNaN() || v.isInfinite()) throw Unsupported("risultato non numerico")
        return v
    }

    private fun values(n: Node, value: (Int, Int) -> Double?): List<Double> = when (n) {
        is Ref -> buildList { for (r in n.area.r1..n.area.r2) for (c in n.area.c1..n.area.c2) value(c, r)?.let { add(it) } }
        else -> listOf(eval(n, value))
    }

    private fun eval(n: Node, value: (Int, Int) -> Double?): Double = when (n) {
        is Num -> n.v
        is Ref -> if (n.single) value(n.area.c1, n.area.r1) ?: 0.0 else throw Unsupported("intervallo fuori da una funzione")
        is Unary -> if (n.minus) -eval(n.e, value) else eval(n.e, value)
        is Bin -> {
            val a = eval(n.a, value); val b = eval(n.b, value)
            when (n.op) {
                '+' -> a + b; '-' -> a - b; '*' -> a * b
                '/' -> if (b == 0.0) throw Unsupported("divisione per zero") else a / b
                else -> throw Unsupported("operatore ${n.op}")
            }
        }
        is Call -> {
            val all = n.args.flatMap { values(it, value) }
            when (n.name) {
                "SUM" -> all.sum()
                "AVERAGE" -> if (all.isEmpty()) throw Unsupported("media vuota") else all.average()
                "MIN" -> all.minOrNull() ?: 0.0
                "MAX" -> all.maxOrNull() ?: 0.0
                "COUNT" -> all.size.toDouble()
                "PRODUCT" -> all.fold(1.0) { a, b -> a * b }
                "ABS" -> abs(eval(n.args.single(), value))
                "ROUND" -> {
                    val x = eval(n.args[0], value)
                    val d = n.args.getOrNull(1)?.let { eval(it, value).toInt() } ?: 0
                    java.math.BigDecimal(x).setScale(d, java.math.RoundingMode.HALF_UP).toDouble()
                }
                else -> throw Unsupported("funzione ${n.name}")
            }
        }
    }

    companion object {
        private val FUNCS = setOf("SUM", "AVERAGE", "MIN", "MAX", "COUNT", "PRODUCT", "ABS", "ROUND")

        /** Una formula A1 senza «=» iniziale, per esempio `SUM(C2:C9)+C10`. */
        fun parse(text: String): Formula {
            val p = Parser(text)
            val root = p.expr()
            p.end()
            return Formula(root, p.refs)
        }

        /** Formula OpenDocument (`of:=SUM([.C2:.C9])`) → A1 (`SUM(C2:C9)`); null se usa altri fogli o altro. */
        fun fromOdf(odf: String): String? {
            var s = odf.trim()
            s = s.removePrefix("of:").removePrefix("oooc:")
            s = s.removePrefix("=")
            val out = StringBuilder()
            var i = 0
            while (i < s.length) {
                val ch = s[i]
                when {
                    ch == '[' -> {
                        val end = s.indexOf(']', i)
                        if (end < 0) return null
                        val inner = s.substring(i + 1, end)
                        val parts = inner.split(':')
                        val cells = parts.map { part ->
                            val p = part.trim()
                            if (!p.startsWith(".") || p.startsWith("..")) return null // altro foglio
                            p.substring(1)
                        }
                        out.append(cells.joinToString(":"))
                        i = end + 1
                        continue
                    }
                    ch == ';' -> out.append(',')
                    ch == '"' -> return null
                    else -> out.append(ch)
                }
                i++
            }
            return out.toString()
        }

        /** A1 → OpenDocument, per una formula scritta nella vista rapida. */
        fun toOdf(a1: String): String {
            val withRanges = Regex("\\$?[A-Za-z]{1,3}\\$?\\d+(:\\$?[A-Za-z]{1,3}\\$?\\d+)?").replace(a1) { m ->
                "[" + m.value.split(':').joinToString(":") { ".$it" } + "]"
            }
            return "of:=" + withRanges.replace(',', ';')
        }
    }

    private class Parser(val s: String) {
        var i = 0
        val refs = ArrayList<Area>()

        fun end() { skip(); if (i < s.length) throw Unsupported("resto non letto: ${s.substring(i)}") }
        private fun skip() { while (i < s.length && s[i].isWhitespace()) i++ }
        private fun peek(): Char? { skip(); return s.getOrNull(i) }

        fun expr(): Node {
            var a = term()
            while (true) {
                val c = peek()
                if (c == '+' || c == '-') { i++; a = Bin(c, a, term()) } else return a
            }
        }

        private fun term(): Node {
            var a = unary()
            while (true) {
                val c = peek()
                if (c == '*' || c == '/') { i++; a = Bin(c, a, unary()) } else return a
            }
        }

        private fun unary(): Node {
            val c = peek()
            if (c == '-' || c == '+') { i++; return Unary(c == '-', unary()) }
            return atom()
        }

        private fun atom(): Node {
            val c = peek() ?: throw Unsupported("formula finita")
            if (c == '(') { i++; val e = expr(); if (peek() != ')') throw Unsupported("manca )"); i++; return e }
            if (c.isDigit() || c == '.') {
                val m = Regex("\\d*\\.?\\d+([eE][-+]?\\d+)?").find(s, i)?.takeIf { it.range.first == i } ?: throw Unsupported("numero")
                i = m.range.last + 1
                return Num(m.value.toDouble())
            }
            if (c.isLetter() || c == '$') {
                val word = Regex("[A-Za-z_][A-Za-z0-9_.]*|\\$?[A-Za-z]{1,3}\\$?\\d+").find(s, i)?.takeIf { it.range.first == i } ?: throw Unsupported("nome")
                val after = word.range.last + 1
                val next = s.getOrNull(after)
                if (next == '(') {
                    val name = word.value.uppercase()
                    if (name !in FUNCS) throw Unsupported("funzione $name")
                    i = after + 1
                    val args = ArrayList<Node>()
                    if (peek() != ')') {
                        while (true) {
                            args += expr()
                            when (peek()) { ',' -> i++; ')' -> break; else -> throw Unsupported("argomenti") }
                        }
                    }
                    i++
                    return Call(name, args)
                }
                if (next == '!') throw Unsupported("altro foglio")
                val first = Cells.parse(word.value) ?: throw Unsupported("nome ${word.value}")
                i = after
                if (s.getOrNull(i) == ':') {
                    val m2 = Regex("\\$?[A-Za-z]{1,3}\\$?\\d+").find(s, i + 1)?.takeIf { it.range.first == i + 1 } ?: throw Unsupported("intervallo")
                    val second = Cells.parse(m2.value)!!
                    i = m2.range.last + 1
                    val area = Area(minOf(first.first, second.first), minOf(first.second, second.second), maxOf(first.first, second.first), maxOf(first.second, second.second))
                    refs += area
                    return Ref(area, false)
                }
                val area = Area(first.first, first.second, first.first, first.second)
                refs += area
                return Ref(area, true)
            }
            throw Unsupported("carattere $c")
        }
    }
}

/**
 * Ricalcolo dopo una modifica: parte dalla cella cambiata e aggiorna, a cascata, le formule dello stesso foglio che ne
 * dipendono. [formulas] sono le formule del foglio (riferimento → testo A1, null se illeggibile); [value] dà i numeri.
 * Restituisce i nuovi valori e le formule che dipendono ma non si sanno calcolare.
 */
internal object Recalc {
    data class Outcome(val values: Map<Pair<Int, Int>, Double>, val pending: Set<Pair<Int, Int>>)

    fun run(changed: Pair<Int, Int>, formulas: Map<Pair<Int, Int>, String?>, value: (Int, Int) -> Double?): Outcome {
        val parsed = formulas.mapValues { (_, f) -> f?.let { runCatching { Formula.parse(it) }.getOrNull() } }
        val current = HashMap<Pair<Int, Int>, Double>()
        val pending = HashSet<Pair<Int, Int>>()
        fun get(c: Int, r: Int): Double? = current[c to r] ?: value(c, r)
        var frontier = setOf(changed)
        var rounds = 0
        while (frontier.isNotEmpty() && rounds++ < 64) {
            val next = HashSet<Pair<Int, Int>>()
            for ((at, f) in parsed) {
                if (at in pending) continue
                val raw = formulas[at]
                val touches = if (f != null) frontier.any { f.dependsOn(it.first, it.second) }
                else raw == null || frontier.any { raw.contains(Cells.ref(it.first, it.second), ignoreCase = true) }
                if (!touches) continue
                if (f == null) { pending += at; next += at; continue }
                val v = runCatching { f.eval(::get) }.getOrNull()
                if (v == null) { pending += at; next += at; continue }
                if (current[at] != v) { current[at] = v; next += at }
            }
            frontier = next
        }
        // Una formula a valle di una che non si sa calcolare resta in attesa anche lei.
        var grew = true
        while (grew) {
            grew = false
            for ((at, f) in parsed) if (at !in pending && f != null && pending.any { f.dependsOn(it.first, it.second) }) { pending += at; current.remove(at); grew = true }
        }
        return Outcome(current, pending)
    }
}

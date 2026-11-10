package eu.eurise.keyboard

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Calcoli scritti nel testo: "=12*3,5" → 42. Più, meno, per (* x ×), diviso (/ : ÷), potenza (^), percentuale (%),
 * parentesi, virgola o punto per i decimali. Nessun accesso a Internet.
 */
object Calc {

    /** Trova un calcolo in fondo al testo ("=…" o "… =") e ne restituisce il pezzo da sostituire e il risultato. */
    fun find(before: String): Pair<String, String>? {
        val m = Regex("""=\s*([0-9(][0-9+\-*/x×÷:^%.,()\s]*[0-9)%])\s*$""").find(before)
            ?: Regex("""([0-9(][0-9+\-*/x×÷:^%.,()\s]*[0-9)%])\s*=\s*$""").find(before)
            ?: return null
        val expr = m.groupValues[1]
        if (expr.none { it in "+-*/x×÷:^%" }) return null
        val value = eval(expr) ?: return null
        return m.value to format(value)
    }

    fun eval(text: String): BigDecimal? = runCatching { Parser(text.replace(" ", "")).parse() }.getOrNull()

    /** Al massimo 10 cifre decimali, senza zeri finali, con la virgola italiana. */
    fun format(v: BigDecimal): String {
        val s = v.setScale(10, RoundingMode.HALF_UP).stripTrailingZeros()
        val plain = if (s.scale() < 0) s.setScale(0).toPlainString() else s.toPlainString()
        return plain.replace('.', ',')
    }

    private val MC = MathContext(34)

    private class Parser(val s: String) {
        var i = 0
        fun parse(): BigDecimal {
            val v = sum()
            require(i == s.length)
            return v
        }
        /** "a + b%" aggiunge il b% di a e "a − b%" lo toglie, come sulle calcolatrici da ufficio. */
        fun sum(): BigDecimal {
            var v = product()
            while (i < s.length && s[i] in "+-") {
                val op = s[i++]
                val start = i
                var r = product()
                val piece = s.substring(start, i)
                if (piece.endsWith("%") && piece.none { it in "*/x×÷:" }) r = v.multiply(r, MC)
                v = if (op == '+') v.add(r, MC) else v.subtract(r, MC)
            }
            return v
        }
        fun product(): BigDecimal {
            var v = power()
            while (i < s.length && s[i] in "*/x×÷:") {
                val op = s[i++]
                val r = power()
                v = if (op in "/÷:") v.divide(r, MC) else v.multiply(r, MC)
            }
            return v
        }
        fun power(): BigDecimal {
            val base = unary()
            if (i < s.length && s[i] == '^') {
                i++
                val exp = power()
                require(exp.stripTrailingZeros().scale() <= 0 && exp.abs() <= BigDecimal(100))
                val n = exp.toInt()
                return if (n >= 0) base.pow(n, MC) else BigDecimal.ONE.divide(base.pow(-n, MC), MC)
            }
            return base
        }
        fun unary(): BigDecimal {
            if (i < s.length && s[i] == '-') { i++; return unary().negate() }
            if (i < s.length && s[i] == '+') { i++; return unary() }
            var v = atom()
            while (i < s.length && s[i] == '%') { i++; v = v.divide(BigDecimal(100), MC) }
            return v
        }
        fun atom(): BigDecimal {
            if (s[i] == '(') {
                i++
                val v = sum()
                require(s[i] == ')')
                i++
                return v
            }
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == ',')) i++
            val raw = s.substring(start, i)
            require(raw.isNotEmpty())
            // "1.000,5" → 1000.5; "3,5" → 3.5; "3.5" → 3.5
            val norm = when {
                ',' in raw && '.' in raw -> raw.replace(".", "").replace(',', '.')
                ',' in raw -> raw.replace(',', '.')
                else -> raw
            }
            return BigDecimal(norm)
        }
    }
}

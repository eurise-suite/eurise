package eu.eurise.core.data.calc

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.PI
import kotlin.math.E

/**
 * Motore della calcolatrice di Eurise: legge espressioni come si scrivono ("1.250 + 22%", "√(3² + 4²)", "2 × sin 30")
 * e restituisce il risultato. I numeri si scrivono all'italiana (virgola decimale, punto delle migliaia) e anche con il
 * punto decimale. La percentuale funziona come sulle calcolatrici da ufficio: "a + b%" aggiunge il b% di a,
 * "a − b%" lo toglie, "a × b%" è il b% di a, "b%" da solo è b/100.
 */
object Calc {

    class CalcException(message: String) : Exception(message)

    /** Valuta [text]; [degrees] decide come leggere gli angoli di sin, cos e tan. */
    fun evaluate(text: String, degrees: Boolean = true, x: Double? = null): Double {
        val parser = Parser(tokenize(text, x), degrees)
        val value = parser.expression()
        if (!parser.done) throw CalcException("Espressione incompleta")
        if (value.isNaN() || value.isInfinite()) throw CalcException("Risultato non definito")
        return value
    }

    /** Il risultato mentre si scrive: null se l'espressione non è ancora completa. */
    fun preview(text: String, degrees: Boolean = true): Double? = runCatching { evaluate(text, degrees) }.getOrNull()

    /** Numero all'italiana: punto per le migliaia, virgola decimale, al massimo [decimals] cifre dopo la virgola. */
    fun format(value: Double, decimals: Int = 10): String {
        if (value == 0.0) return "0"
        val abs = kotlin.math.abs(value)
        if (abs >= 1e15 || abs < 1e-9) {
            return String.format(Locale.ITALY, "%.6e", value).replace("e+0", "e").replace("e+", "e")
        }
        val symbols = DecimalFormatSymbols(Locale.ITALY)
        val pattern = "#,##0" + if (decimals > 0) "." + "#".repeat(decimals) else ""
        return DecimalFormat(pattern, symbols).apply { roundingMode = RoundingMode.HALF_UP }
            .format(BigDecimal.valueOf(value).round(java.math.MathContext(15)))
    }

    /** Importo in euro con due decimali: 1.268,80. */
    fun money(value: BigDecimal): String =
        DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.ITALY)).format(value.setScale(2, RoundingMode.HALF_UP))

    // --- Lettura ---

    private sealed interface Token {
        data class Num(val value: Double) : Token
        data class Op(val symbol: Char) : Token
        data class Name(val name: String) : Token
    }

    /** Con [x] la lettera x è la variabile del grafico; senza, x è il segno di moltiplicazione. */
    private fun tokenize(text: String, x: Double? = null): List<Token> {
        val out = ArrayList<Token>()
        var i = 0
        val s = text.trim()
        while (i < s.length) {
            val c = s[i]
            when {
                c.isWhitespace() -> i++
                x != null && (c == 'x' || c == 'X') -> { out += Token.Num(x); i++ }
                c.isDigit() || ((c == ',' || c == '.') && i + 1 < s.length && s[i + 1].isDigit()) -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == ',')) i++
                    out += Token.Num(number(s.substring(start, i)))
                }
                c.isLetter() && c != 'π' && c != 'x' && c != 'X' -> {
                    val start = i
                    while (i < s.length && s[i].isLetter()) i++
                    out += Token.Name(s.substring(start, i).lowercase())
                }
                else -> {
                    val op = when (c) {
                        '×', '*', 'x', 'X', '·' -> '*'
                        '÷', '/', ':' -> '/'
                        '−', '–', '-' -> '-'
                        '²' -> '²'
                        '³' -> '³'
                        else -> c
                    }
                    if (op !in "+-*/^()%!√π²³") throw CalcException("Simbolo non riconosciuto: $c")
                    out += Token.Op(op)
                    i++
                }
            }
        }
        return out
    }

    /** "1.250,5", "1250.5", "1.250" (migliaia) e "1,5" sono tutti numeri validi. */
    internal fun number(raw: String): Double {
        val text = when {
            ',' in raw -> raw.replace(".", "").replace(',', '.')
            raw.count { it == '.' } > 1 -> raw.replace(".", "")
            Regex("""\d{1,3}\.\d{3}""").matches(raw) -> raw.replace(".", "")
            else -> raw
        }
        return text.toDoubleOrNull() ?: throw CalcException("Numero non valido: $raw")
    }

    /**
     * Grammatica, dalla precedenza più bassa:
     * espressione = termine (('+'|'-') termine['%'])* ; termine = fattore (('*'|'/') fattore)* ;
     * fattore = '-' fattore | potenza ; potenza = postfisso ('^' fattore)? ; postfisso = primario ('!'|'²'|'³'|'%')*.
     */
    private class Parser(val tokens: List<Token>, val degrees: Boolean) {
        var pos = 0
        val done get() = pos == tokens.size

        private fun peek() = tokens.getOrNull(pos)
        private fun isOp(c: Char) = (peek() as? Token.Op)?.symbol == c
        private fun take() = tokens[pos++]

        fun expression(): Double {
            var left = term()
            while (isOp('+') || isOp('-')) {
                val sign = (take() as Token.Op).symbol
                val (right, percent) = termWithPercent()
                val amount = if (percent) left * right / 100 else right
                left = if (sign == '+') left + amount else left - amount
            }
            return left
        }

        /** Un termine dopo + o −: se finisce con %, il valore è la percentuale grezza (b, non b/100). */
        private fun termWithPercent(): Pair<Double, Boolean> {
            val start = pos
            val value = term()
            val last = tokens.getOrNull(pos - 1)
            if (last is Token.Op && last.symbol == '%' && pos - 1 > start && multiplicativeFree(start, pos - 1)) {
                return value * 100 to true
            }
            return value to false
        }

        private fun multiplicativeFree(from: Int, to: Int): Boolean {
            var depth = 0
            for (i in from until to) {
                val t = tokens[i] as? Token.Op ?: continue
                when (t.symbol) {
                    '(' -> depth++
                    ')' -> depth--
                    '*', '/' -> if (depth == 0) return false
                }
            }
            return true
        }

        private fun term(): Double {
            var left = factor()
            while (true) {
                left = when {
                    isOp('*') -> { take(); left * factor() }
                    isOp('/') -> {
                        take()
                        val d = factor()
                        if (d == 0.0) throw CalcException("Divisione per zero")
                        left / d
                    }
                    // Moltiplicazione sottintesa: 2π, 3(4+1), 2 sin 30.
                    peek() is Token.Num || peek() is Token.Name || isOp('(') || isOp('√') || isOp('π') -> left * factor()
                    else -> return left
                }
            }
        }

        private fun factor(): Double {
            if (isOp('-')) { take(); return -factor() }
            if (isOp('+')) { take(); return factor() }
            return power()
        }

        private fun power(): Double {
            val base = postfix()
            if (isOp('^')) { take(); return Math.pow(base, factor()) }
            return base
        }

        private fun postfix(): Double {
            var v = primary()
            while (true) {
                v = when {
                    isOp('!') -> { take(); factorial(v) }
                    isOp('²') -> { take(); v * v }
                    isOp('³') -> { take(); v * v * v }
                    isOp('%') -> { take(); v / 100 }
                    else -> return v
                }
            }
        }

        private fun primary(): Double {
            val t = peek() ?: throw CalcException("Manca un numero")
            return when {
                t is Token.Num -> { take(); t.value }
                t is Token.Op && t.symbol == '(' -> {
                    take()
                    val v = expression()
                    if (isOp(')')) take()
                    v
                }
                t is Token.Op && t.symbol == '√' -> { take(); sqrt(postfixOrFactor()) }
                t is Token.Op && t.symbol == 'π' -> { take(); PI }
                t is Token.Name -> { take(); function(t.name) }
                else -> throw CalcException("Manca un numero")
            }
        }

        /** L'argomento di √ e delle funzioni: tra parentesi o il numero subito dopo ("sin 30", "√2"). */
        private fun postfixOrFactor(): Double = postfix()

        private fun function(name: String): Double = when (name) {
            "pi" -> PI
            "e" -> E
            "sqrt", "radice" -> sqrt(postfixOrFactor())
            "sin", "sen" -> Math.sin(angle(postfixOrFactor()))
            "cos" -> Math.cos(angle(postfixOrFactor()))
            "tan", "tg" -> Math.tan(angle(postfixOrFactor()))
            "asin", "arcsin" -> fromRadians(Math.asin(postfixOrFactor()))
            "acos", "arccos" -> fromRadians(Math.acos(postfixOrFactor()))
            "atan", "arctan" -> fromRadians(Math.atan(postfixOrFactor()))
            "ln" -> positive(postfixOrFactor()).let(Math::log)
            "log" -> positive(postfixOrFactor()).let(Math::log10)
            "abs" -> kotlin.math.abs(postfixOrFactor())
            else -> throw CalcException("Funzione sconosciuta: $name")
        }

        private fun angle(v: Double) = if (degrees) Math.toRadians(v) else v
        private fun fromRadians(v: Double) = if (degrees) Math.toDegrees(v) else v
        private fun sqrt(v: Double): Double { if (v < 0) throw CalcException("Radice di un numero negativo"); return Math.sqrt(v) }
        private fun positive(v: Double): Double { if (v <= 0) throw CalcException("Logaritmo di un numero non positivo"); return v }

        private fun factorial(v: Double): Double {
            if (v < 0 || v != Math.floor(v) || v > 170) throw CalcException("Fattoriale non definito")
            var r = 1.0
            for (i in 2..v.toInt()) r *= i
            return r
        }
    }
}

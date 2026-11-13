package eu.eurise.core.suite

/**
 * Il testo pronto da leggere ad alta voce: i motori di sintesi pronunciano molti segni («virgolette», «punto medio»,
 * asterischi, frecce, emoji) invece di farne pause. Qui le virgolette spariscono, i separatori diventano virgole, i
 * puntini una pausa, e restano solo lettere, numeri e la punteggiatura che si sente come pausa o intonazione.
 */
object SpeechText {
    private val apostrophe = Regex("""(?<=\p{L})[’'](?=\p{L})""")

    private val steps: List<Pair<Regex, String>> = listOf(
        Regex("""https?://\S+""") to " ",
        Regex("""\s*€\s*/\s*l\b""") to " euro al litro",
        Regex("""\s*(→|->|=>)\s*""") to ", ",
        Regex("""[«»“”„"‘’'`*_#~^|<>\[\]{}]""") to " ",
        Regex("""\s*[·•–—\\]+\s*""") to ", ",
        Regex("""\s*(?:(?<!\d)/|/(?!\d))\s*""") to ", ",
        Regex("""\s*[()]+\s*""") to ", ",
        Regex("""…|\.{2,}""") to ".",
        Regex("""\s*&\s*""") to " e ",
        Regex("""\s*%""") to " per cento",
        Regex("""\s*€""") to " euro",
    )

    private val tidy: List<Pair<Regex, String>> = listOf(
        Regex("""\s+([,.;:!?])""") to "$1",
        Regex(""",(\s*,)+""") to ",",
        Regex("""([.;:!?]),""") to "$1",
        Regex("""([,;:])(?=\p{L})""") to "$1 ",
        Regex("""^[,;:.\s]+""") to "",
        Regex("""[,;:\s]+$""") to "",
        Regex("""\s+""") to " ",
    )

    fun clean(text: String): String {
        // L'apostrofo tra due lettere resta («dell'impegno»): lo si mette da parte prima di togliere le virgolette.
        var s = text.replace(apostrophe, "\u0000")
        steps.forEach { (r, v) -> s = s.replace(r, v) }
        s = s.replace("\u0000", "'")
        s = buildString {
            var i = 0
            while (i < s.length) {
                val cp = s.codePointAt(i)
                val type = Character.getType(cp)
                // Emoji e simboli non si leggono.
                val drop = type == Character.OTHER_SYMBOL.toInt() || type == Character.PRIVATE_USE.toInt() ||
                    type == Character.SURROGATE.toInt() || cp == 0xFE0F || cp == 0x200D
                if (!drop) appendCodePoint(cp)
                i += Character.charCount(cp)
            }
        }
        tidy.forEach { (r, v) -> s = s.replace(r, v) }
        return s.trim()
    }
}

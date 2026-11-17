package eu.eurise.feature.assistant

/**
 * Quanto Alfred dice a voce: l'essenziale, il resto è scritto nel pannello. Parlare costa tempo a chi ascolta (prova del
 * 17 novembre: «deve aiutarmi a fare prima, non farmi aspettare che parli»).
 */
object SpokenText {
    private val END = Regex("(?<=[.!?…])\\s+")

    /** Le prime [sentences] frasi di [text], al massimo [maxChars] caratteri (si taglia a fine frase o a una virgola). */
    fun brief(text: String, sentences: Int = 2, maxChars: Int = 200): String {
        val parts = text.trim().split(END).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return ""
        var out = parts.first()
        for (p in parts.drop(1).take(sentences - 1)) {
            if (out.length + 1 + p.length > maxChars) break
            out += " $p"
        }
        if (out.length <= maxChars) return out
        val cut = out.lastIndexOfAny(charArrayOf(',', ';', ':'), maxChars).takeIf { it > maxChars / 3 }
            ?: out.lastIndexOf(' ', maxChars).takeIf { it > 0 } ?: maxChars
        return out.substring(0, cut).trimEnd(',', ';', ':', ' ') + "…"
    }
}

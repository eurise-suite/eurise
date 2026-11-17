package eu.eurise.voice.speech

/**
 * Il testo da leggere in pezzi: si genera un pezzo alla volta e il primo si sente mentre si generano gli altri. Il primo
 * pezzo è corto (si taglia alla prima virgola dopo qualche parola), perché è quello che si aspetta in silenzio; gli altri
 * sono frasi intere, tagliate alle virgole solo se lunghe.
 */
object SpeechChunks {
    private val SENTENCE = Regex("(?<=[.!?;:…])\\s+")
    const val FIRST_MAX = 48
    const val MAX = 160

    fun split(text: String): List<String> {
        val sentences = text.split(SENTENCE).map { it.trim() }.filter { it.isNotEmpty() }
        val out = mutableListOf<String>()
        for (s in sentences) out += cut(s, if (out.isEmpty()) FIRST_MAX else MAX)
        return out
    }

    /** Taglia [s] a una virgola: per il primo pezzo la prima utile (si sente prima), poi la più lontana entro [MAX]. */
    private fun cut(s: String, max: Int): List<String> {
        if (s.length <= max) return listOf(s)
        val comma = (if (max < MAX) s.indexOf(", ", startIndex = 12).takeIf { it in 12..max } else null)
            ?: s.lastIndexOf(", ", MAX).takeIf { it >= 12 }
            ?: return listOf(s)
        return listOf(s.substring(0, comma + 1)) + cut(s.substring(comma + 2).trim(), MAX)
    }
}

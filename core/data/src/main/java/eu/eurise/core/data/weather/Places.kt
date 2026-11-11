package eu.eurise.core.data.weather

import java.text.Normalizer

/** Un luogo come lo mostra Eurise: «Monza (MB)» con la bandiera e la sigla del paese. */
data class Place(val name: String, val province: String?, val region: String?, val countryCode: String?) {
    /** «Monza (MB)», oppure solo il nome. */
    val label: String get() = if (province != null) "$name ($province)" else name

    /** La bandiera del paese con i simboli regionali Unicode (🇮🇹). */
    val flag: String? get() = countryCode?.uppercase()?.takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
        ?.let { cc -> String(Character.toChars(0x1F1E6 + (cc[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (cc[1] - 'A'))) }
}

/** Sigle delle province italiane, per nome (senza «Provincia di», «Città metropolitana di», accenti e maiuscole). */
object Provinces {
    private val byName: Map<String, String> = """
        agrigento AG|alessandria AL|ancona AN|aosta AO|valle d aosta AO|arezzo AR|ascoli piceno AP|asti AT|avellino AV|bari BA|
        barletta andria trani BT|belluno BL|benevento BN|bergamo BG|biella BI|bologna BO|bolzano BZ|alto adige BZ|brescia BS|
        brindisi BR|cagliari CA|caltanissetta CL|campobasso CB|caserta CE|catania CT|catanzaro CZ|chieti CH|como CO|cosenza CS|
        cremona CR|crotone KR|cuneo CN|enna EN|fermo FM|ferrara FE|firenze FI|foggia FG|forli cesena FC|frosinone FR|genova GE|
        gorizia GO|grosseto GR|imperia IM|isernia IS|la spezia SP|l aquila AQ|aquila AQ|latina LT|lecce LE|lecco LC|livorno LI|lodi LO|
        lucca LU|macerata MC|mantova MN|massa carrara MS|matera MT|messina ME|milano MI|modena MO|monza brianza MB|monza MB|
        napoli NA|novara NO|nuoro NU|oristano OR|padova PD|palermo PA|parma PR|pavia PV|perugia PG|pesaro urbino PU|pescara PE|
        piacenza PC|pisa PI|pistoia PT|pordenone PN|potenza PZ|prato PO|ragusa RG|ravenna RA|reggio calabria RC|reggio emilia RE|reggio nell emilia RE|
        rieti RI|rimini RN|roma RM|rovigo RO|salerno SA|sassari SS|savona SV|siena SI|siracusa SR|sondrio SO|sud sardegna SU|
        taranto TA|teramo TE|terni TR|torino TO|trapani TP|trento TN|treviso TV|trieste TS|udine UD|varese VA|venezia VE|
        verbano cusio ossola VB|vercelli VC|verona VR|vibo valentia VV|vicenza VI|viterbo VT|gallura nord est sardegna SS|
        medio campidano SU|ogliastra NU|carbonia iglesias SU
    """.trimIndent().replace("\n", "").split('|').map { it.trim() }.filter { it.isNotEmpty() }
        .associate { it.substringBeforeLast(' ') to it.substringAfterLast(' ') }

    private val noise = Regex("\\b(provincia|citta metropolitana|libero consorzio comunale|di|del|della|dell|e|autonoma)\\b")

    /** La sigla per il nome dato dal telefono («Provincia di Monza e della Brianza» → MB); una sigla resta com'è. */
    fun code(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        if (s.length == 2 && s.all { it.isUpperCase() }) return s
        val key = normalize(s).replace(noise, " ").replace(Regex("\\s+"), " ").trim()
        byName[key]?.let { return it }
        // Nomi composti: la voce più lunga contenuta nel nome.
        return byName.entries.filter { (name, _) -> key.contains(name) }.maxByOrNull { it.key.length }?.value
    }

    private fun normalize(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").replace(Regex("[^a-z]+"), " ")
}

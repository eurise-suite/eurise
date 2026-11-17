package eu.eurise.core.studio

/**
 * Smista una richiesta in un bisogno, sul telefono e senza AI: parole chiave scelte con cura, provate dai test.
 * Le urgenze vanno in cima e non aspettano: chi le scrive riceve subito il 112 (regole dello studio, tavola StuRegole).
 * Meglio un falso allarme che un dolore al petto in fondo alla coda; ma «esami del sangue» non è un'urgenza.
 */
object Triage {
    private val URGENT = listOf(
        "dolore al petto", "dolore toracico", "male al petto", "peso sul petto", "oppressione al petto", "stretta al petto",
        "non riesco a respirare", "non respiro", "fatico a respirare", "fatica a respirare", "manca il respiro", "manca l'aria",
        "soffoco", "svenut", "perso i sensi", "perde i sensi", "privo di sensi", "non risponde piu", "convulsion",
        "infarto", "ictus", "paralisi", "bocca storta", "non muove il braccio", "non muove la gamba", "non riesce a parlare",
        "emorragia", "perdo sangue", "perde sangue", "vomito sangue", "vomita sangue", "sangue dalla bocca",
        "gonfiore alla gola", "gola gonfia", "shock anafilattico", "reazione allergica forte", "trauma cranico",
        "ha battuto la testa", "overdose", "avvelenat", "suicid", "farla finita", "togliermi la vita",
    )
    private val PRESCRIPTION = listOf(
        "ricett", "farmac", "medicin", "pastiglie", "compresse", "pillole", "gocce", "nre", "impegnativa",
        "terapia", "rinnovo", "piano terapeutico", "finite le", "finito il", "sto finendo", "scatol",
    )
    private val APPOINTMENT = listOf(
        "appuntament", "visita", "prenot", "spostare", "disdire", "disdico", "posso venire", "passare in studio",
        "quando riceve", "orari", "seduta", "sedute", "domani alle", "oggi alle",
    )
    private val REPORT = listOf(
        "referto", "referti", "esami", "esame", "analisi", "risultat", "ecg", "elettrocardiogramma", "ecografia",
        "radiografia", "lastra", "tac", "risonanza", "holter", "spirometria",
    )
    private val ACUPUNCTURE = listOf("agopunt", "seduta", "sedute", "aghi", "ago")

    /** Le parole cominciano dove comincia la parola del testo; quelle brevi («tac», «ecg») devono anche finire lì. */
    private fun hit(t: String, words: List<String>) = words.any { w ->
        Regex("(^|[^a-z])" + Regex.escape(w) + if (w.length <= 4) "($|[^a-z])" else "").containsMatchIn(t)
    }

    /** Il bisogno di [text]; i nomi della terapia abituale ([therapies]) valgono come richiesta di ricetta. */
    fun need(text: String, therapies: List<String> = emptyList()): Need {
        val t = plain(text)
        if (hit(t, URGENT)) return Need.Urgent
        val drugs = therapies.mapNotNull { plain(it).split(' ').firstOrNull()?.takeIf { w -> w.length >= 4 } }
        val appointment = hit(t, APPOINTMENT)
        if (hit(t, PRESCRIPTION) || (drugs.isNotEmpty() && hit(t, drugs) && !appointment)) return Need.Prescription
        if (appointment) return Need.Appointment
        if (hit(t, REPORT)) return Need.Report
        if (thanks(t)) return Need.Thanks
        return Need.Question
    }

    /** Un «grazie» breve, senza domande: in coda per ultimo (si legge quando c'è tempo). */
    private fun thanks(t: String) = t.length <= 80 && '?' !in t &&
        listOf("grazie", "gentilissim", "ok perfetto", "ricevut").any { it in t }

    /** Medicina generale o agopuntura: decide il paziente se ne ha una sola, altrimenti le parole dell'agopuntura. */
    fun practice(text: String, practices: Set<Practice>): Practice = when {
        practices.size == 1 -> practices.first()
        hit(plain(text), ACUPUNCTURE) -> Practice.Acupuncture
        else -> Practice.General
    }
}

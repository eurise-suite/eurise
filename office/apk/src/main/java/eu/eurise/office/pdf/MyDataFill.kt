package eu.eurise.office.pdf

import java.text.Normalizer

/**
 * «Compila con I miei dati» (tavola OffPdf): abbina i campi di un modulo PDF ai dati di un profilo di Eurise Password
 * («Nome», «Codice fiscale», «CAP»…). Il nome del campo nel PDF è spesso un'abbreviazione («cf», «cod_fisc», «Prov.»,
 * «e-mail»), quindi si confronta la forma normalizzata con un elenco di sinonimi. I campi senza un abbinamento sicuro
 * restano vuoti: meglio un campo da scrivere a mano che un dato nel posto sbagliato.
 */
object MyDataFill {
    /** Per ogni dato di Password (nome del campo come lo salva Password), le forme con cui compare nei moduli. */
    private val synonyms: Map<String, List<String>> = mapOf(
        "Nome" to listOf("nome", "name", "firstname", "givenname", "nomedelrichiedente"),
        "Cognome" to listOf("cognome", "surname", "lastname", "familyname"),
        "Codice fiscale" to listOf("codicefiscale", "codfisc", "codfiscale", "cf", "codicefisc", "taxcode", "fiscalcode"),
        "Data di nascita" to listOf("datadinascita", "datanascita", "natoil", "natail", "nascitadata", "birthdate", "dateofbirth", "dob"),
        "Luogo di nascita" to listOf("luogodinascita", "luogonascita", "natoa", "nataa", "comunedinascita", "comunenascita", "birthplace", "placeofbirth"),
        "Telefono" to listOf("telefono", "tel", "cellulare", "cell", "phone", "mobile", "numeroditelefono", "recapitotelefonico"),
        "Email" to listOf("email", "mail", "indirizzoemail", "posta", "postaelettronica"),
        "PEC" to listOf("pec", "postaelettronicacertificata", "emailpec", "indirizzopec"),
        "Ragione sociale" to listOf("ragionesociale", "denominazione", "societa", "ditta", "azienda", "company"),
        "Partita IVA" to listOf("partitaiva", "piva", "pi", "partiva", "vat", "vatnumber"),
        "Codice univoco" to listOf("codiceunivoco", "codicesdi", "sdi", "codicedestinatario"),
        "IBAN" to listOf("iban", "codiceiban", "contocorrente"),
        "Indirizzo" to listOf("indirizzo", "via", "viapiazza", "residenzavia", "indirizzodiresidenza", "address", "street"),
        "CAP" to listOf("cap", "codicepostale", "postalcode", "zip", "zipcode"),
        "Città" to listOf("citta", "comune", "comunediresidenza", "residentea", "residenteinl", "localita", "city"),
        "Provincia" to listOf("provincia", "prov", "pr", "province"),
        "Paese" to listOf("paese", "nazione", "stato", "country"),
        "Documento" to listOf("documento", "numerodocumento", "ndocumento", "documentodiidentita", "cartadidentita"),
    )

    /** «Cod. Fisc.» → «codfisc»: minuscole, senza accenti, solo lettere e cifre. */
    fun normalize(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase().replace(Regex("[^a-z0-9]"), "")

    /** L'ultimo pezzo di un nome qualificato («modulo.sezione.cf» → «cf») e senza numeri finali («nome_2» → «nome»). */
    private fun keyForms(name: String): List<String> {
        val last = name.substringAfterLast('.')
        return listOf(name, last).map(::normalize).flatMap { listOf(it, it.trimEnd { c -> c.isDigit() }) }.filter { it.isNotEmpty() }.distinct()
    }

    /**
     * Il dato del profilo per un campo, o null. [label] è il nome alternativo del campo (quello che il modulo mostra),
     * [name] il nome tecnico. «Nome e cognome» o «Nominativo» prendono i due insieme.
     */
    fun valueFor(label: String, name: String, profile: List<Pair<String, String>>): String? {
        val data = profile.associate { it.first to it.second.trim() }.filterValues { it.isNotEmpty() }
        val forms = (keyForms(label) + keyForms(name)).distinct()
        if (forms.any { it in setOf("nomeecognome", "cognomeenome", "nominativo", "fullname", "nomecognome", "cognomenome") }) {
            val full = listOfNotNull(data["Nome"], data["Cognome"]).joinToString(" ")
            if (full.isNotEmpty()) return if (forms.any { it.startsWith("cognome") }) listOfNotNull(data["Cognome"], data["Nome"]).joinToString(" ") else full
        }
        for (form in forms) {
            synonyms.entries.firstOrNull { (_, list) -> form in list }?.let { (key, _) -> data[key]?.let { return if (key == "IBAN") it.replace(" ", "") else it } }
        }
        // I dati aggiunti dalla persona in Password: stesso nome del campo.
        data.entries.firstOrNull { (key, _) -> normalize(key) in forms }?.let { return it.value }
        return null
    }

    /** Per i campi di testo vuoti di un modulo, i valori da scrivere: nome del campo → valore. */
    fun fill(fields: List<FormField>, profile: List<Pair<String, String>>): Map<String, String> =
        fields.filter { it.kind == FormField.Kind.Text && it.value.isBlank() }
            .mapNotNull { f -> valueFor(f.label, f.name, profile)?.let { f.name to it } }
            .toMap()
}

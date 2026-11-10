package eu.eurise.password.logic

import eu.eurise.password.autofill.FieldKind

/**
 * I miei dati: profili (Personale, Studio…) con i dati che servono nei moduli e da tenere a portata di mano.
 * Ogni profilo è una voce della cassaforte KeePass con un campo per dato, quindi resta leggibile anche da
 * KeePassXC e KeePassDX. Oltre ai campi noti la persona aggiunge i suoi (titolo e valore).
 */
object Profiles {

    /** Un campo noto: [key] è il nome del campo nel file, [kind] il tipo di campo dei moduli che compila. */
    data class Known(val key: String, val section: Section, val sensitive: Boolean = false, val kinds: Set<FieldKind> = emptySet())

    enum class Section { Person, Work, Address }

    const val NAME = "Nome"
    const val SURNAME = "Cognome"
    const val TAX_CODE = "Codice fiscale"
    const val BIRTH_DATE = "Data di nascita"
    const val BIRTH_PLACE = "Luogo di nascita"
    const val PHONE = "Telefono"
    const val EMAIL = "Email"
    const val DOCUMENT = "Documento"
    const val DOCUMENT_EXPIRY = "Scadenza documento"
    const val COMPANY = "Ragione sociale"
    const val VAT = "Partita IVA"
    const val PEC = "PEC"
    const val SDI = "Codice univoco"
    const val IBAN = "IBAN"
    const val STREET = "Indirizzo"
    const val POSTAL_CODE = "CAP"
    const val CITY = "Città"
    const val PROVINCE = "Provincia"
    const val COUNTRY = "Paese"

    val known: List<Known> = listOf(
        Known(NAME, Section.Person, kinds = setOf(FieldKind.GivenName)),
        Known(SURNAME, Section.Person, kinds = setOf(FieldKind.FamilyName)),
        Known(TAX_CODE, Section.Person, sensitive = true, kinds = setOf(FieldKind.TaxCode)),
        Known(BIRTH_DATE, Section.Person, sensitive = true, kinds = setOf(FieldKind.BirthDate)),
        Known(BIRTH_PLACE, Section.Person, kinds = setOf(FieldKind.BirthPlace)),
        Known(PHONE, Section.Person, kinds = setOf(FieldKind.Phone)),
        Known(EMAIL, Section.Person, kinds = setOf(FieldKind.Username)),
        Known(DOCUMENT, Section.Person, sensitive = true, kinds = setOf(FieldKind.Document)),
        Known(DOCUMENT_EXPIRY, Section.Person),
        Known(COMPANY, Section.Work, kinds = setOf(FieldKind.Company)),
        Known(VAT, Section.Work, kinds = setOf(FieldKind.VatNumber)),
        Known(PEC, Section.Work, kinds = setOf(FieldKind.Pec)),
        Known(SDI, Section.Work, kinds = setOf(FieldKind.SdiCode)),
        Known(IBAN, Section.Work, sensitive = true, kinds = setOf(FieldKind.Iban)),
        Known(STREET, Section.Address, kinds = setOf(FieldKind.Street)),
        Known(POSTAL_CODE, Section.Address, kinds = setOf(FieldKind.PostalCode)),
        Known(CITY, Section.Address, kinds = setOf(FieldKind.City)),
        Known(PROVINCE, Section.Address, kinds = setOf(FieldKind.Province)),
        Known(COUNTRY, Section.Address, kinds = setOf(FieldKind.Country)),
    )

    private val byKey = known.associateBy { it.key }

    fun isKnown(key: String) = key in byKey

    /** I campi aggiunti dalla persona sono sempre trattati come riservati. */
    fun isSensitive(key: String) = byKey[key]?.sensitive ?: true

    /** I campi aggiunti dalla persona, nell'ordine in cui li ha scritti. */
    fun custom(fields: List<Pair<String, String>>) = fields.filter { !isKnown(it.first) }

    /** Il valore da scrivere in un campo del modulo di tipo [kind]; null se il profilo non lo ha. */
    fun valueFor(kind: FieldKind, fields: List<Pair<String, String>>): String? {
        val map = fields.toMap()
        fun get(key: String) = map[key]?.trim()?.takeIf { it.isNotEmpty() }
        return when (kind) {
            FieldKind.FullName -> listOfNotNull(get(NAME), get(SURNAME)).joinToString(" ").ifEmpty { null }
            FieldKind.Iban -> get(IBAN)?.replace(" ", "")
            else -> known.firstOrNull { kind in it.kinds }?.let { get(it.key) }
        }
    }

    /** Quanti campi compilerebbe in un modulo con questi tipi di campo. */
    fun fillCount(kinds: List<FieldKind>, fields: List<Pair<String, String>>) = kinds.count { valueFor(it, fields) != null }
}

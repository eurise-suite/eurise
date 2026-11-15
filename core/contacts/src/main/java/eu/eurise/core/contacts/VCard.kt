package eu.eurise.core.contacts

/**
 * Lettore dei file .vcf (vCard 2.1, 3.0 e 4.0) per «Apri con › Contatti» (15 novembre): uno o più contatti con nome,
 * numeri, email, azienda, indirizzo e nota, pronti per [Contacts.save]. Le foto e i campi rari si lasciano fuori.
 * Il quoted-printable della 2.1 (nomi con lettere accentate) si decodifica in UTF-8.
 */
object VCard {
    fun parse(text: String): List<ContactDraft> {
        val out = ArrayList<ContactDraft>()
        var props: MutableList<Pair<String, String>>? = null
        for (line in unfold(text)) {
            val upper = line.uppercase()
            when {
                upper == "BEGIN:VCARD" -> props = ArrayList()
                upper == "END:VCARD" -> { props?.let { draft(it)?.let(out::add) }; props = null }
                props != null -> {
                    val colon = line.indexOf(':').takeIf { it > 0 } ?: continue
                    val key = line.substring(0, colon)
                    var value = line.substring(colon + 1)
                    if (key.contains("QUOTED-PRINTABLE", true)) value = quotedPrintable(value)
                    props.add(key.substringAfter('.') to value)
                }
            }
        }
        return out
    }

    private fun draft(p: List<Pair<String, String>>): ContactDraft? {
        fun all(name: String) = p.filter { it.first.substringBefore(';').equals(name, true) }.map { unescape(it.second).trim() }.filter { it.isNotEmpty() }
        val structured = all("N").firstOrNull()?.split(';')?.map { it.trim() }
        val name = all("FN").firstOrNull()
            ?: structured?.let { n -> listOfNotNull(n.getOrNull(3), n.getOrNull(1), n.getOrNull(2), n.getOrNull(0), n.getOrNull(4)).filter { it.isNotBlank() }.joinToString(" ") }
            ?: ""
        val phones = all("TEL").map { it.removePrefix("tel:") }
        val emails = all("EMAIL").map { it.removePrefix("mailto:") }
        if (name.isBlank() && phones.isEmpty() && emails.isEmpty()) return null
        val address = all("ADR").firstOrNull()?.split(';')?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString(", ").orEmpty()
        return ContactDraft(
            name = name.ifBlank { phones.firstOrNull() ?: emails.first() },
            phones = phones.ifEmpty { listOf("") },
            emails = emails,
            company = all("ORG").firstOrNull()?.split(';')?.firstOrNull().orEmpty(),
            address = address,
            note = all("NOTE").firstOrNull().orEmpty(),
        )
    }

    private fun unfold(text: String): List<String> {
        val out = ArrayList<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { l ->
            when {
                (l.startsWith(" ") || l.startsWith("\t")) && out.isNotEmpty() -> out[out.size - 1] = out.last() + l.substring(1)
                // Quoted-printable 2.1: la riga che finisce con «=» continua nella successiva.
                out.isNotEmpty() && out.last().contains("QUOTED-PRINTABLE", true) && out.last().endsWith("=") -> out[out.size - 1] = out.last().dropLast(1) + l
                l.isNotEmpty() -> out += l
            }
        }
        return out
    }

    private fun quotedPrintable(s: String): String {
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '=' && i + 2 < s.length) {
                val hex = s.substring(i + 1, i + 3).toIntOrNull(16)
                if (hex != null) { bytes.write(hex); i += 3; continue }
            }
            bytes.write(c.code and 0xFF)
            i++
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    private fun unescape(s: String) = s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")
}

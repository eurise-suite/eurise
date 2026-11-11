package eu.eurise.feature.scanner

/**
 * Che cosa c'è in un codice QR, capito per tipo. Niente si apre da solo: la schermata mostra il contenuto e offre le
 * azioni giuste (pagare, collegarsi al Wi-Fi, salvare il contatto…). Senza Android, così si prova nei test.
 */
sealed interface QrContent {
    val raw: String

    data class Link(override val raw: String, val host: String, val secure: Boolean) : QrContent
    data class Wifi(override val raw: String, val ssid: String, val password: String, val security: String, val hidden: Boolean) : QrContent
    /** Avviso pagoPA: [amountCents] in centesimi. La scadenza non è nel codice. */
    data class PagoPa(override val raw: String, val notice: String, val creditorTaxCode: String, val amountCents: Long) : QrContent
    /** Bonifico SEPA (codice "BCD" stampato su fatture e bollettini europei). */
    data class Sepa(override val raw: String, val name: String, val iban: String, val amount: String?, val reference: String) : QrContent
    data class Contact(override val raw: String, val name: String, val phones: List<String>, val emails: List<String>, val org: String) : QrContent
    data class Event(override val raw: String, val title: String, val start: String?, val end: String?, val location: String) : QrContent
    data class Email(override val raw: String, val address: String, val subject: String) : QrContent
    data class Phone(override val raw: String, val number: String) : QrContent
    data class Sms(override val raw: String, val number: String, val body: String) : QrContent
    data class Geo(override val raw: String, val lat: Double, val lon: Double) : QrContent
    /** Codice 2FA: il segreto non va mai mostrato, va solo passato a Password. */
    data class Otp(override val raw: String) : QrContent
    data class Text(override val raw: String) : QrContent
    /** Codice del Ponte Eurise mostrato dal computer: si collega il telefono (core/ponte, feature/ponte). */
    data class Ponte(override val raw: String, val name: String) : QrContent

    companion object {
        fun parse(text: String): QrContent {
            val t = text.trim()
            val upper = t.uppercase()
            return when {
                t.startsWith("eurise-ponte://") -> ponte(t) ?: Text(t)
                upper.startsWith("PAGOPA|") -> pagoPa(t) ?: Text(t)
                upper.startsWith("BCD\n") || upper.startsWith("BCD\r\n") -> sepa(t) ?: Text(t)
                upper.startsWith("WIFI:") -> wifi(t) ?: Text(t)
                upper.startsWith("OTPAUTH://") || upper.startsWith("OTPAUTH-MIGRATION://") -> Otp(t)
                upper.startsWith("BEGIN:VCARD") -> vcard(t)
                upper.startsWith("MECARD:") -> mecard(t)
                upper.contains("BEGIN:VEVENT") -> event(t)
                upper.startsWith("MAILTO:") -> mailto(t)
                upper.startsWith("MATMSG:") -> matmsg(t)
                upper.startsWith("TEL:") -> Phone(t, t.substring(4).trim())
                upper.startsWith("SMSTO:") || upper.startsWith("SMS:") -> sms(t)
                upper.startsWith("GEO:") -> geo(t) ?: Text(t)
                upper.startsWith("HTTP://") || upper.startsWith("HTTPS://") -> link(t) ?: Text(t)
                else -> Text(t)
            }
        }

        private fun ponte(t: String): Ponte? {
            val name = t.substringAfter('?').split('&').firstOrNull { it.startsWith("n=") }?.substringAfter("n=") ?: return null
            return Ponte(t, runCatching { java.net.URLDecoder.decode(name, "UTF-8") }.getOrDefault(name))
        }

        private fun link(t: String): Link? {
            val host = t.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
                .substringAfter('@').substringBefore(':').lowercase()
            if (host.isEmpty()) return null
            return Link(t, host, t.startsWith("https://", ignoreCase = true))
        }

        private fun pagoPa(t: String): PagoPa? {
            val parts = t.split('|')
            if (parts.size < 5) return null
            val notice = parts[2].filter { it.isDigit() }
            val cf = parts[3].trim()
            val cents = parts[4].trim().toLongOrNull() ?: return null
            if (notice.length != 18) return null
            return PagoPa(t, notice, cf, cents)
        }

        private fun sepa(t: String): Sepa? {
            val l = t.lines().map { it.trim() }
            if (l.size < 7 || l.getOrNull(3)?.uppercase() != "SCT") return null
            val amount = l.getOrNull(7)?.takeIf { it.uppercase().startsWith("EUR") }?.substring(3)?.takeIf { it.isNotBlank() }
            val reference = listOfNotNull(l.getOrNull(9), l.getOrNull(10)).firstOrNull { it.isNotBlank() }.orEmpty()
            return Sepa(t, l[5], l[6].replace(" ", ""), amount, reference)
        }

        /** Campi "K:valore;" con i caratteri \ ; , : protetti dalla barra rovescia. */
        private fun fields(body: String): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            var key = StringBuilder()
            var value = StringBuilder()
            var inValue = false
            var i = 0
            while (i < body.length) {
                val c = body[i]
                when {
                    c == '\\' && i + 1 < body.length -> { (if (inValue) value else key).append(body[i + 1]); i++ }
                    !inValue && c == ':' -> inValue = true
                    inValue && c == ';' -> {
                        out.putIfAbsent(key.toString().uppercase(), value.toString())
                        key = StringBuilder(); value = StringBuilder(); inValue = false
                    }
                    else -> (if (inValue) value else key).append(c)
                }
                i++
            }
            if (inValue) out.putIfAbsent(key.toString().uppercase(), value.toString())
            return out
        }

        private fun wifi(t: String): Wifi? {
            val f = fields(t.substring(5))
            val ssid = f["S"] ?: return null
            return Wifi(t, ssid, f["P"].orEmpty(), f["T"].orEmpty().ifEmpty { "nopass" }, f["H"].equals("true", ignoreCase = true))
        }

        private fun mecard(t: String): Contact {
            val body = t.substring(7)
            val f = fields(body)
            val name = f["N"].orEmpty().split(',').reversed().joinToString(" ").trim()
            val phones = Regex("""TEL:([^;]+)""").findAll(body).map { it.groupValues[1] }.toList()
            val emails = Regex("""EMAIL:([^;]+)""").findAll(body).map { it.groupValues[1] }.toList()
            return Contact(t, name, phones, emails, f["ORG"].orEmpty())
        }

        private fun vcard(t: String): Contact {
            val lines = unfold(t)
            fun values(key: String) = lines.filter { it.substringBefore(':').substringBefore(';').equals(key, ignoreCase = true) }
                .map { it.substringAfter(':').trim() }.filter { it.isNotEmpty() }
            val name = values("FN").firstOrNull()
                ?: values("N").firstOrNull()?.split(';')?.let { p -> listOfNotNull(p.getOrNull(1), p.getOrNull(0)).joinToString(" ").trim() }
                ?: ""
            return Contact(t, name, values("TEL"), values("EMAIL"), values("ORG").firstOrNull()?.replace(';', ' ')?.trim().orEmpty())
        }

        private fun event(t: String): Event {
            val lines = unfold(t)
            fun value(key: String) = lines.firstOrNull { it.substringBefore(':').substringBefore(';').equals(key, ignoreCase = true) }?.substringAfter(':')?.trim()
            return Event(t, value("SUMMARY").orEmpty(), value("DTSTART"), value("DTEND"), value("LOCATION").orEmpty())
        }

        /** Le righe di vCard e iCalendar che continuano con uno spazio si riattaccano a quella prima. */
        private fun unfold(t: String): List<String> {
            val out = mutableListOf<String>()
            t.lines().forEach { line -> if ((line.startsWith(" ") || line.startsWith("\t")) && out.isNotEmpty()) out[out.size - 1] += line.drop(1) else out += line }
            return out
        }

        private fun mailto(t: String): Email {
            val rest = t.substring(7)
            val address = java.net.URLDecoder.decode(rest.substringBefore('?'), "UTF-8")
            val subject = Regex("""(?:^|&)subject=([^&]*)""", RegexOption.IGNORE_CASE).find(rest.substringAfter('?', ""))?.groupValues?.get(1)
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }.orEmpty()
            return Email(t, address, subject)
        }

        private fun matmsg(t: String): Email {
            val f = fields(t.substring(7))
            return Email(t, f["TO"].orEmpty(), f["SUB"].orEmpty())
        }

        private fun sms(t: String): Sms {
            val rest = t.substringAfter(':')
            return Sms(t, rest.substringBefore(':').trim(), rest.substringAfter(':', "").trim())
        }

        private fun geo(t: String): Geo? {
            val coords = t.substring(4).substringBefore('?').split(',')
            val lat = coords.getOrNull(0)?.toDoubleOrNull() ?: return null
            val lon = coords.getOrNull(1)?.toDoubleOrNull() ?: return null
            return Geo(t, lat, lon)
        }
    }
}

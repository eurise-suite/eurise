package eu.eurise.core.data.trips

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Un tratto di viaggio letto da un biglietto: tutti i campi si possono correggere prima di creare qualcosa. */
data class Leg(
    val kind: Kind,
    /** "AZ 2041", "FR 9520". */
    val number: String,
    val from: String,
    val to: String,
    val depart: LocalDateTime?,
    val arrive: LocalDateTime?,
    val seat: String = "",
    /** Fuso dell'arrivo, se noto (per l'Orologio). */
    val arriveZone: String? = null,
    val departZone: String? = null,
) {
    enum class Kind { Flight, Train, Other }
}

data class TicketRead(val legs: List<Leg>, val booking: String = "", val passenger: String = "")

/** Aeroporti principali: codice IATA → città e fuso. Pochi e leggeri; gli altri si scrivono a mano. */
object Airports {
    data class Airport(val code: String, val city: String, val zone: String)

    private val table: Map<String, Airport> = """
        FCO Roma Europe/Rome|CIA Roma Europe/Rome|MXP Milano Europe/Rome|LIN Milano Europe/Rome|BGY Bergamo Europe/Rome|VCE Venezia Europe/Rome
        TSF Treviso Europe/Rome|BLQ Bologna Europe/Rome|FLR Firenze Europe/Rome|PSA Pisa Europe/Rome|NAP Napoli Europe/Rome|BRI Bari Europe/Rome
        BDS Brindisi Europe/Rome|CTA Catania Europe/Rome|PMO Palermo Europe/Rome|CAG Cagliari Europe/Rome|OLB Olbia Europe/Rome|AHO Alghero Europe/Rome
        TRN Torino Europe/Rome|GOA Genova Europe/Rome|VRN Verona Europe/Rome|TRS Trieste Europe/Rome|AOI Ancona Europe/Rome|PSR Pescara Europe/Rome
        SUF Lamezia Europe/Rome|REG Reggio_Calabria Europe/Rome|LMP Lampedusa Europe/Rome|PEG Perugia Europe/Rome|CUF Cuneo Europe/Rome
        LHR Londra Europe/London|LGW Londra Europe/London|STN Londra Europe/London|LTN Londra Europe/London|LCY Londra Europe/London|MAN Manchester Europe/London
        EDI Edimburgo Europe/London|DUB Dublino Europe/Dublin|CDG Parigi Europe/Paris|ORY Parigi Europe/Paris|BVA Parigi Europe/Paris|NCE Nizza Europe/Paris
        LYS Lione Europe/Paris|MRS Marsiglia Europe/Paris|TLS Tolosa Europe/Paris|BOD Bordeaux Europe/Paris|NTE Nantes Europe/Paris|BSL Basilea Europe/Zurich
        GVA Ginevra Europe/Zurich|ZRH Zurigo Europe/Zurich|FRA Francoforte Europe/Berlin|MUC Monaco_di_Baviera Europe/Berlin|BER Berlino Europe/Berlin
        HAM Amburgo Europe/Berlin|DUS Düsseldorf Europe/Berlin|CGN Colonia Europe/Berlin|STR Stoccarda Europe/Berlin|VIE Vienna Europe/Vienna
        AMS Amsterdam Europe/Amsterdam|EIN Eindhoven Europe/Amsterdam|BRU Bruxelles Europe/Brussels|CRL Charleroi Europe/Brussels|LUX Lussemburgo Europe/Luxembourg
        MAD Madrid Europe/Madrid|BCN Barcellona Europe/Madrid|VLC Valencia Europe/Madrid|AGP Malaga Europe/Madrid|SVQ Siviglia Europe/Madrid|PMI Palma Europe/Madrid
        IBZ Ibiza Europe/Madrid|TFS Tenerife Atlantic/Canary|LPA Gran_Canaria Atlantic/Canary|ACE Lanzarote Atlantic/Canary|LIS Lisbona Europe/Lisbon
        OPO Porto Europe/Lisbon|FAO Faro Europe/Lisbon|ATH Atene Europe/Athens|SKG Salonicco Europe/Athens|JTR Santorini Europe/Athens|JMK Mykonos Europe/Athens
        HER Creta Europe/Athens|RHO Rodi Europe/Athens|CFU Corfù Europe/Athens|MLA Malta Europe/Malta|CPH Copenaghen Europe/Copenhagen|ARN Stoccolma Europe/Stockholm
        OSL Oslo Europe/Oslo|HEL Helsinki Europe/Helsinki|KEF Reykjavík Atlantic/Reykjavik|WAW Varsavia Europe/Warsaw|KRK Cracovia Europe/Warsaw|PRG Praga Europe/Prague
        BUD Budapest Europe/Budapest|OTP Bucarest Europe/Bucharest|SOF Sofia Europe/Sofia|ZAG Zagabria Europe/Zagreb|SPU Spalato Europe/Zagreb|DBV Dubrovnik Europe/Zagreb
        LJU Lubiana Europe/Ljubljana|BEG Belgrado Europe/Belgrade|TIA Tirana Europe/Tirane|IST Istanbul Europe/Istanbul|SAW Istanbul Europe/Istanbul|AYT Antalya Europe/Istanbul
        TLV Tel_Aviv Asia/Jerusalem|CAI Il_Cairo Africa/Cairo|RAK Marrakech Africa/Casablanca|CMN Casablanca Africa/Casablanca|TUN Tunisi Africa/Tunis
        DXB Dubai Asia/Dubai|AUH Abu_Dhabi Asia/Dubai|DOH Doha Asia/Qatar|JFK New_York America/New_York|EWR New_York America/New_York
        BOS Boston America/New_York|MIA Miami America/New_York|IAD Washington America/New_York|ORD Chicago America/Chicago|ATL Atlanta America/New_York
        LAX Los_Angeles America/Los_Angeles|SFO San_Francisco America/Los_Angeles|YYZ Toronto America/Toronto|YUL Montréal America/Toronto
        MEX Città_del_Messico America/Mexico_City|GRU San_Paolo America/Sao_Paulo|EZE Buenos_Aires America/Argentina/Buenos_Aires
        NRT Tokyo Asia/Tokyo|HND Tokyo Asia/Tokyo|KIX Osaka Asia/Tokyo|ICN Seul Asia/Seoul|PEK Pechino Asia/Shanghai|PVG Shanghai Asia/Shanghai
        HKG Hong_Kong Asia/Hong_Kong|SIN Singapore Asia/Singapore|BKK Bangkok Asia/Bangkok|DEL Nuova_Delhi Asia/Kolkata|BOM Mumbai Asia/Kolkata
        SYD Sydney Australia/Sydney|MEL Melbourne Australia/Melbourne|JNB Johannesburg Africa/Johannesburg|CPT Città_del_Capo Africa/Johannesburg
        NBO Nairobi Africa/Nairobi|MLE Malé Indian/Maldives|MRU Mauritius Indian/Mauritius|ZNZ Zanzibar Africa/Dar_es_Salaam
    """.trimIndent().split('|', '\n').mapNotNull { e ->
        val p = e.trim().split(' ')
        if (p.size == 3) Airport(p[0], p[1].replace('_', ' '), p[2]) else null
    }.associateBy { it.code }

    fun get(code: String): Airport? = table[code.uppercase()]
}

/**
 * Legge un biglietto: prima il codice a barre della carta d'imbarco (formato IATA BCBP, preciso), poi il testo del
 * PDF (con regole semplici per voli e treni). Quello che non si capisce resta da scrivere a mano.
 */
object Tickets {

    fun read(barcodes: List<String>, text: String, today: LocalDate): TicketRead {
        val fromBarcodes = barcodes.mapNotNull { Bcbp.parse(it, today) }
        if (fromBarcodes.isNotEmpty()) {
            val first = fromBarcodes.first()
            return TicketRead(fromBarcodes.flatMap { it.legs }.distinctBy { it.number + it.depart?.toLocalDate() }, first.booking, first.passenger)
        }
        return fromText(text, today)
    }

    private val FLIGHT = Regex("""\b(?:([A-Z]{2}|[A-Z]\d|\d[A-Z])\s?(\d{1,4}))\b""")
    private val TRAIN = Regex("""\b(Frecciarossa|Frecciargento|Frecciabianca|Intercity(?:\s?Notte)?|Regionale(?:\s?Veloce)?|Italo|FR|FA|FB|IC|ICN|EC|RV|REG|ES\*?)\s?(\d{3,5})\b""", RegexOption.IGNORE_CASE)
    private val TIME = Regex("""\b([01]?\d|2[0-3])[:.h]([0-5]\d)\b""")
    private val CODE = Regex("""\b([A-Z]{3})\b""")
    private val BOOKING = Regex("""(?i:PNR|codice\s+(?:di\s+)?prenotazione|booking\s+(?:reference|code)|prenotazione|riferimento|reservation)[ \t]*[:#]?[ \t]*([A-Z0-9]{5,8})\b""")
    private val MONTHS = mapOf(
        "gen" to 1, "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "mag" to 5, "may" to 5, "giu" to 6, "jun" to 6,
        "lug" to 7, "jul" to 7, "ago" to 8, "aug" to 8, "set" to 9, "sep" to 9, "ott" to 10, "oct" to 10, "nov" to 11, "dic" to 12, "dec" to 12,
    )
    private val DATE_NUM = Regex("""\b(\d{1,2})[/.-](\d{1,2})[/.-](\d{2,4})\b""")
    private val DATE_ISO = Regex("""\b(20\d{2})-(\d{2})-(\d{2})\b""")
    private val DATE_WORD = Regex("""\b(\d{1,2})\s?([A-Za-z]{3})[a-z]*\.?\s?(\d{2,4})?\b""")

    /** Le date del testo, con la posizione: giorno numerico, ISO o con il mese a parole (anche "12NOV"). */
    internal fun dates(text: String, today: LocalDate): List<Pair<Int, LocalDate>> {
        val out = mutableListOf<Pair<Int, LocalDate>>()
        DATE_ISO.findAll(text).forEach { m ->
            runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()?.let { out += m.range.first to it }
        }
        DATE_NUM.findAll(text).forEach { m ->
            val y = m.groupValues[3].toInt().let { if (it < 100) 2000 + it else it }
            runCatching { LocalDate.of(y, m.groupValues[2].toInt(), m.groupValues[1].toInt()) }.getOrNull()?.let { out += m.range.first to it }
        }
        DATE_WORD.findAll(text).forEach { m ->
            val month = MONTHS[m.groupValues[2].lowercase()] ?: return@forEach
            val day = m.groupValues[1].toInt()
            val year = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            val date = runCatching {
                if (year != null) LocalDate.of(year, month, day)
                else LocalDate.of(today.year, month, day).let { if (it.isBefore(today.minusDays(30))) it.plusYears(1) else it }
            }.getOrNull() ?: return@forEach
            out += m.range.first to date
        }
        return out.distinctBy { it.first }.sortedBy { it.first }
    }

    internal fun fromText(text: String, today: LocalDate): TicketRead {
        val booking = BOOKING.find(text)?.groupValues?.get(1).orEmpty()
        val dates = dates(text, today).filter { !it.second.isBefore(today.minusDays(2)) }
        val legs = mutableListOf<Leg>()

        // Voli: sigla della compagnia e numero, con due aeroporti noti nelle vicinanze.
        FLIGHT.findAll(text).forEach { m ->
            val start = (m.range.first - 120).coerceAtLeast(0)
            val window = text.substring(start, (m.range.last + 300).coerceAtMost(text.length))
            val airports = CODE.findAll(window).mapNotNull { Airports.get(it.value) }.map { it.code }.distinct().take(2).toList()
            if (airports.size < 2) return@forEach
            val after = text.substring(m.range.last + 1, (m.range.last + 300).coerceAtMost(text.length))
            val times = TIME.findAll(after).take(2).map { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.toList()
            val date = nearest(dates, m.range.first)
            val from = Airports.get(airports[0])
            val to = Airports.get(airports[1])
            val depart = date?.let { d -> times.getOrNull(0)?.let { d.atTime(it) } }
            val arrive = date?.let { d -> times.getOrNull(1)?.let { t -> d.atTime(t).let { if (times[0] > t) it.plusDays(1) else it } } }
            legs += Leg(
                Leg.Kind.Flight, "${m.groupValues[1]} ${m.groupValues[2]}",
                "${from?.city} (${airports[0]})", "${to?.city} (${airports[1]})", depart, arrive,
                departZone = from?.zone, arriveZone = to?.zone,
            )
        }
        // Treni: nome o sigla del treno con il numero; stazioni e orari vicini.
        TRAIN.findAll(text).forEach { m ->
            // Le sigle in comune (FR è Ryanair e Frecciarossa): se è già un volo, non è un treno.
            if (legs.any { it.kind == Leg.Kind.Flight && it.number.replace(" ", "") == m.value.replace(" ", "").uppercase() }) return@forEach
            val window = text.substring(m.range.first, (m.range.last + 400).coerceAtMost(text.length))
            val times = TIME.findAll(window).take(2).map { LocalTime.of(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }.toList()
            val date = nearest(dates, m.range.first)
            val stations = stationsNear(window)
            legs += Leg(
                Leg.Kind.Train, "${m.groupValues[1].replaceFirstChar { it.uppercase() }} ${m.groupValues[2]}",
                stations.first, stations.second,
                date?.let { d -> times.getOrNull(0)?.let { d.atTime(it) } },
                date?.let { d -> times.getOrNull(1)?.let { t -> d.atTime(t).let { if (times[0] > t) it.plusDays(1) else it } } },
            )
        }

        return TicketRead(legs.distinctBy { it.number + it.depart }, booking)
    }

    private fun nearest(dates: List<Pair<Int, LocalDate>>, at: Int): LocalDate? =
        dates.minByOrNull { kotlin.math.abs(it.first - at) + (if (it.first > at) 0 else 40) }?.second

    /** Stazioni scritte come "Da Roma Termini a Milano Centrale" o su due righe dopo il treno. */
    private fun stationsNear(window: String): Pair<String, String> {
        Regex("""(?i)\bda\s+([A-ZÀ-Ü][\wÀ-ü'. ]{2,40}?)\s+a\s+([A-ZÀ-Ü][\wÀ-ü'. ]{2,40}?)(?:\s{2,}|\n|,|\s\d)""").find(window)?.let {
            return it.groupValues[1].trim() to it.groupValues[2].trim()
        }
        val names = window.lines().drop(1).map { it.trim() }
            .filter { it.length in 3..40 && it.first().isUpperCase() && !it.any(Char::isDigit) }
            .take(2)
        return names.getOrElse(0) { "" } to names.getOrElse(1) { "" }
    }
}

/**
 * Carta d'imbarco IATA (Bar Coded Boarding Pass, Resolution 792): il testo dentro il codice PDF417 o Aztec. Dà il
 * nome, il codice di prenotazione e, per ogni tratto, aeroporti, compagnia, numero del volo, giorno e posto. L'ora
 * non c'è: si aggiunge a mano (o arriva dal testo del PDF).
 */
object Bcbp {

    fun parse(raw: String, today: LocalDate): TicketRead? {
        val s = raw.trimEnd('\n', '\r')
        if (s.length < 60 || s[0] != 'M' || !s[1].isDigit()) return null
        val count = s[1].digitToInt()
        if (count !in 1..4) return null
        val passenger = s.substring(2, 22).trim().replace('/', ' ')
        val legs = mutableListOf<Leg>()
        var booking = ""
        var pos = 23
        repeat(count) { index ->
            if (pos + 37 > s.length) return@repeat
            val pnr = s.substring(pos, pos + 7).trim()
            val from = s.substring(pos + 7, pos + 10)
            val to = s.substring(pos + 10, pos + 13)
            val carrier = s.substring(pos + 13, pos + 16).trim()
            val flight = s.substring(pos + 16, pos + 21).trim().trimStart('0').ifEmpty { "0" }
            val julian = s.substring(pos + 21, pos + 24).trim().toIntOrNull()
            val seat = s.substring(pos + 25, pos + 29).trim().trimStart('0')
            if (index == 0) booking = pnr
            val day = julian?.let { dayOfYear(it, today) }
            val a = Airports.get(from)
            val b = Airports.get(to)
            legs += Leg(
                Leg.Kind.Flight, "$carrier $flight",
                a?.let { "${it.city} ($from)" } ?: from, b?.let { "${it.city} ($to)" } ?: to,
                day?.atStartOfDay(), null, seat, arriveZone = b?.zone, departZone = a?.zone,
            )
            // Dopo i 37 caratteri fissi, la lunghezza (esadecimale) della parte variabile di questo tratto.
            val size = s.substring(pos + 35, pos + 37).toIntOrNull(16) ?: 0
            pos += 37 + size
        }
        return if (legs.isEmpty()) null else TicketRead(legs, booking, passenger)
    }

    /** Il giorno dell'anno senza anno: il più vicino nel futuro (una carta d'imbarco non vale per gli anni passati). */
    private fun dayOfYear(day: Int, today: LocalDate): LocalDate? {
        if (day !in 1..366) return null
        val candidates = listOf(today.year - 1, today.year, today.year + 1).mapNotNull { y ->
            runCatching { LocalDate.ofYearDay(y, day) }.getOrNull()
        }
        return candidates.firstOrNull { !it.isBefore(today.minusDays(3)) } ?: candidates.lastOrNull()
    }

    /** Il fuso del luogo d'arrivo, se il codice è noto. */
    fun zoneOf(code: String): ZoneId? = Airports.get(code)?.zone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
}

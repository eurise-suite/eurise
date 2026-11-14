package app.organicmaps.eurise.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * I prezzi dei carburanti (tavola MapAttivita): i due file pubblici dell'Osservaprezzi del Ministero delle Imprese
 * (anagrafica degli impianti e prezzi alle 8, licenza IODL 2.0), uguali per tutti. Si scaricano al massimo una volta al
 * giorno, solo con il servizio acceso nel centro privacy, e si tengono sul telefono in un file compatto: la posizione
 * non esce mai, il calcolo di vicini, economici e «sul percorso» si fa qui.
 *
 * Formato (dal 10 febbraio 2026): separatore «|», prima riga «Estrazione del AAAA-MM-GG», poi l'intestazione; i decimali
 * col punto. Le colonne si cercano per nome, così un cambio d'ordine non rompe la lettura.
 */
object EuriseFuelData {
    private const val REGISTRY = "https://www.mimit.gov.it/images/exportCSV/anagrafica_impianti_attivi.csv"
    private const val PRICES = "https://www.mimit.gov.it/images/exportCSV/prezzo_alle_8.csv"
    private const val FILE = "eurise_carburanti.txt"
    private const val MAX_AGE_MS = 20 * 60 * 60 * 1000L
    private val HUBS = arrayOf("eu.eurise.hub.debug", "eu.eurise.hub")
    /** Il nome del servizio nel centro privacy dell'app Eurise (core/suite PrivacyService.FuelPrices). */
    private const val SERVICE = "FuelPrices"

    enum class Fuel { Benzina, Gasolio, Gpl, Metano }

    /** Un impianto con il prezzo più basso per carburante (self se c'è, altrimenti servito); NaN se non lo vende. */
    class Station(
        val name: String, val brand: String, val address: String, val lat: Double, val lon: Double,
        val prices: DoubleArray, val self: BooleanArray, val updated: String,
    )

    private var cache: List<Station>? = null
    private var cacheStamp = 0L

    private fun file(c: Context) = File(c.filesDir, FILE)

    /** Quanto è vecchio il file sul telefono (millisecondi), o -1 se non c'è. */
    fun age(c: Context): Long = file(c).takeIf { it.isFile }?.let { System.currentTimeMillis() - it.lastModified() } ?: -1

    // ---- Centro privacy (come la ricerca online: stato, accensione, registro) -------------------------------------

    /** Lo stato nel centro privacy, o null se l'app Eurise manca. Fuori dal thread principale. */
    fun privacy(c: Context, method: String = "status"): Boolean? {
        for (h in HUBS) {
            try {
                val b: Bundle? = c.contentResolver.call(Uri.parse("content://$h.privacy"), method, SERVICE, null)
                if (b != null) return b.getBoolean("on")
            } catch (_: Exception) {
                // App Eurise assente o di un'altra firma: si prova l'altra.
            }
        }
        return null
    }

    // ---- Scarico e lettura ------------------------------------------------------------------------------------------

    /**
     * Aggiorna il file se è vecchio e il servizio è acceso. Vero se ora sul telefono ci sono i prezzi. Fuori dal thread
     * principale; [force] scarica anche se il file è di oggi.
     */
    fun refresh(c: Context, force: Boolean = false): Boolean {
        val f = file(c)
        val fresh = f.isFile && System.currentTimeMillis() - f.lastModified() < MAX_AGE_MS
        if (fresh && !force) return true
        if (privacy(c) == false) return f.isFile
        return runCatching {
            val stations = HashMap<String, Array<String>>(26_000)
            read(REGISTRY) { cols, row ->
                val id = row.col(cols, "idimpianto") ?: return@read
                val lat = row.col(cols, "latitudine")?.toDoubleOrNull() ?: return@read
                val lon = row.col(cols, "longitudine")?.toDoubleOrNull() ?: return@read
                if (lat == 0.0 || lon == 0.0) return@read
                val name = row.col(cols, "nome impianto").orEmpty().ifBlank { row.col(cols, "gestore").orEmpty() }
                val address = listOfNotNull(row.col(cols, "indirizzo"), row.col(cols, "comune")).filter { it.isNotBlank() }.joinToString(", ")
                stations[id] = arrayOf(name, row.col(cols, "bandiera").orEmpty(), address, lat.toString(), lon.toString())
            }
            val prices = HashMap<String, DoubleArray>(26_000)
            val self = HashMap<String, BooleanArray>(26_000)
            val updated = HashMap<String, String>(26_000)
            read(PRICES) { cols, row ->
                val id = row.col(cols, "idimpianto") ?: return@read
                if (id !in stations) return@read
                val fuel = fuelOf(row.col(cols, "desccarburante").orEmpty()) ?: return@read
                val price = row.col(cols, "prezzo")?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it in 0.3..5.0 } ?: return@read
                val isSelf = row.col(cols, "isself") == "1"
                val p = prices.getOrPut(id) { DoubleArray(Fuel.entries.size) { Double.NaN } }
                val s = self.getOrPut(id) { BooleanArray(Fuel.entries.size) }
                val i = fuel.ordinal
                // Vince il self; tra due prezzi dello stesso tipo, il più basso.
                if (p[i].isNaN() || (isSelf && !s[i]) || (isSelf == s[i] && price < p[i])) {
                    p[i] = price
                    s[i] = isSelf
                }
                row.col(cols, "dtcomu")?.let { d -> if (d > (updated[id] ?: "")) updated[id] = d }
            }
            val tmp = File(c.filesDir, "$FILE.new")
            tmp.bufferedWriter().use { w ->
                for ((id, p) in prices) {
                    val st = stations[id] ?: continue
                    val s = self[id]!!
                    w.append(st.joinToString("|") { it.replace('|', ' ') })
                    for (i in p.indices) w.append('|').append(if (p[i].isNaN()) "" else (if (s[i]) "s" else "v") + p[i])
                    w.append('|').append(updated[id].orEmpty()).append('\n')
                }
            }
            check(tmp.length() > 10_000) { "file dei prezzi troppo piccolo" }
            check(tmp.renameTo(f))
            cache = null
            privacy(c, "log")
            true
        }.getOrElse { f.isFile }
    }

    /** Gli impianti sul telefono (letti una volta). Fuori dal thread principale. */
    fun stations(c: Context): List<Station> {
        val f = file(c)
        if (!f.isFile) return emptyList()
        cache?.takeIf { cacheStamp == f.lastModified() }?.let { return it }
        val out = ArrayList<Station>(24_000)
        f.forEachLine { line ->
            val p = line.split('|')
            if (p.size < 5 + Fuel.entries.size) return@forEachLine
            val lat = p[3].toDoubleOrNull() ?: return@forEachLine
            val lon = p[4].toDoubleOrNull() ?: return@forEachLine
            val prices = DoubleArray(Fuel.entries.size) { i -> p[5 + i].drop(1).toDoubleOrNull() ?: Double.NaN }
            val self = BooleanArray(Fuel.entries.size) { i -> p[5 + i].startsWith("s") }
            out += Station(p[0], p[1], p[2], lat, lon, prices, self, p.getOrNull(5 + Fuel.entries.size).orEmpty())
        }
        cache = out
        cacheStamp = f.lastModified()
        return out
    }

    private fun fuelOf(desc: String): Fuel? {
        val d = desc.lowercase()
        return when {
            "gpl" in d -> Fuel.Gpl
            "metano" in d || "gnl" in d -> Fuel.Metano
            "gasolio" in d || "diesel" in d -> Fuel.Gasolio
            "benzina" in d || "verde" in d -> Fuel.Benzina
            else -> null
        }
    }

    /** Legge un file del Ministero riga per riga: [row] riceve le colonne (nome → indice) e i campi. */
    private fun read(address: String, row: (Map<String, Int>, List<String>) -> Unit) {
        val conn = URL(address).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.setRequestProperty("User-Agent", "EuriseMappe")
        try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8), 64 * 1024).use { r ->
                var cols: Map<String, Int>? = null
                var sep = '|'
                while (true) {
                    val line = r.readLine() ?: break
                    if (line.isBlank() || line.startsWith("Estrazione", ignoreCase = true)) continue
                    if (cols == null) {
                        // L'intestazione: il separatore è «|» dal 2026, «;» prima; i nomi senza maiuscole.
                        sep = if ('|' in line) '|' else ';'
                        cols = line.split(sep).mapIndexed { i, n -> n.trim().lowercase() to i }.toMap()
                        continue
                    }
                    row(cols, line.split(sep))
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun List<String>.col(cols: Map<String, Int>, name: String): String? = cols[name]?.let { getOrNull(it)?.trim() }
}

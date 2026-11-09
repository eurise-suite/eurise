package eu.eurise.feature.launcher.markets

import eu.eurise.core.suite.PrivacyCenter
import eu.eurise.core.suite.PrivacyService
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Una quotazione: prezzo attuale e variazione rispetto alla chiusura precedente. */
data class Quote(val symbol: String, val name: String, val price: Double, val changePercent: Double, val currency: String)

/** Simboli proposti all'inizio: indice di Milano, Euro Stoxx 50, euro/dollaro. */
val DefaultSymbols = listOf("FTSEMIB.MI", "^STOXX50E", "EURUSD=X")

/** Nomi leggibili per i simboli più comuni; per gli altri si usa il nome restituito dal servizio. */
private val KnownNames = mapOf(
    "FTSEMIB.MI" to "FTSE MIB", "^STOXX50E" to "Euro Stoxx 50", "EURUSD=X" to "Euro / Dollaro",
    "^GDAXI" to "DAX", "^FCHI" to "CAC 40", "^GSPC" to "S&P 500", "^IXIC" to "Nasdaq", "BTC-EUR" to "Bitcoin",
)

/**
 * Quotazioni dal servizio pubblico di grafici di Yahoo Finance: gratuito, senza account né chiave, dati con
 * circa 15 minuti di ritardo. Si inviano solo i simboli scelti; il risultato resta in memoria 15 minuti.
 */
class MarketsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("eurise_markets", Context.MODE_PRIVATE)
    private val _symbols = MutableStateFlow(
        prefs.getString("symbols", null)?.split('|')?.filter { it.isNotBlank() } ?: DefaultSymbols,
    )
    val symbols: StateFlow<List<String>> = _symbols.asStateFlow()
    private var cache: Pair<Long, List<Quote>>? = null

    fun add(symbol: String) {
        val clean = symbol.trim().uppercase()
        if (clean.isEmpty() || clean in _symbols.value) return
        save(_symbols.value + clean)
    }

    fun remove(symbol: String) = save(_symbols.value - symbol)

    private fun save(list: List<String>) {
        _symbols.value = list
        prefs.edit().putString("symbols", list.joinToString("|")).apply()
        cache = null
    }

    suspend fun quotes(): List<Quote> {
        val now = System.currentTimeMillis()
        cache?.let { (at, list) -> if (now - at < 15 * 60_000) return list }
        val list = withContext(Dispatchers.IO) {
            coroutineScope { _symbols.value.map { s -> async { runCatching { fetch(s) }.getOrNull() } }.awaitAll() }
        }.filterNotNull()
        if (list.isNotEmpty()) cache = now to list
        return list
    }

    private fun fetch(symbol: String): Quote {
        if (!PrivacyCenter.allowed(PrivacyService.Markets)) throw java.io.IOException("spento nel centro privacy")
        PrivacyCenter.log(PrivacyService.Markets)
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/${URLEncoder.encode(symbol, "UTF-8")}?range=1d&interval=1d"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Eurise")
        }
        val body = try { connection.inputStream.bufferedReader().readText() } finally { connection.disconnect() }
        val meta = JSONObject(body).getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
        val price = meta.getDouble("regularMarketPrice")
        val previous = meta.optDouble("chartPreviousClose", meta.optDouble("previousClose", price))
        val change = if (previous != 0.0) (price - previous) / previous * 100 else 0.0
        val name = KnownNames[symbol] ?: meta.optString("shortName").ifBlank { symbol }
        return Quote(symbol, name, price, change, meta.optString("currency"))
    }
}

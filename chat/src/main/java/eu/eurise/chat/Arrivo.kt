package eu.eurise.chat

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Sono arrivato (tavola FamArrivato): quando arrivi in un posto scelto (casa, scuola, palestra), nella chat scelta parte da sola
 * la frase «È arrivato a casa · 23:42». Il posto si riconosce sul telefono (avviso di vicinanza di Android, niente servizi
 * esterni); esce solo la frase, mai la posizione. Lo decide chi lo accende e lo spegne quando vuole; gli altri vedono se è acceso.
 * Spento di base. Per funzionare ad app chiusa serve la posizione «sempre», chiesta solo quando lo si accende.
 */
object Arrivo {
    const val STATE = "eu.eurise.arrival"

    enum class When { Tonight, SchoolDays, Always }

    data class Place(val name: String, val lat: Double, val lon: Double)
    data class Rule(
        val id: String, val account: String, val roomId: String, val encrypted: Boolean, val places: List<Place>, val whenever: When,
        val midnight: Boolean, val since: Long, val arrivedDay: Long = 0, val warnedDay: Long = 0,
    )

    const val RADIUS_M = 150f

    /** Vale adesso? Stasera: dal momento in cui lo accendi fino alle 6 del giorno dopo; giorni di scuola: lunedì–sabato. */
    fun active(r: Rule, now: LocalDateTime): Boolean = when (r.whenever) {
        When.Always -> true
        When.SchoolDays -> now.dayOfWeek != DayOfWeek.SUNDAY
        When.Tonight -> Programma.toMs(now) <= Programma.toMs(java.time.Instant.ofEpochMilli(r.since).atZone(java.time.ZoneId.systemDefault()).toLocalDate().plusDays(1).atTime(6, 0))
    }

    /** A mezzanotte: avvisa se stasera non è ancora arrivato (una volta). */
    fun lateWarning(r: Rule, today: LocalDate): Boolean = r.midnight && r.arrivedDay < today.toEpochDay() && r.warnedDay < today.toEpochDay()

    fun encode(list: List<Rule>): String = JSONArray().apply {
        list.forEach { r ->
            put(JSONObject().put("id", r.id).put("account", r.account).put("room", r.roomId).put("enc", r.encrypted).put("when", r.whenever.name)
                .put("midnight", r.midnight).put("since", r.since).put("arrived", r.arrivedDay).put("warned", r.warnedDay)
                .put("places", JSONArray().apply { r.places.forEach { p -> put(JSONObject().put("name", p.name).put("lat", p.lat).put("lon", p.lon)) } }))
        }
    }.toString()

    fun decode(s: String?): List<Rule> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val places = o.optJSONArray("places")?.let { p -> (0 until p.length()).mapNotNull { j -> p.optJSONObject(j)?.let { Place(it.optString("name"), it.optDouble("lat"), it.optDouble("lon")) } } }.orEmpty()
            Rule(o.optString("id"), o.optString("account"), o.optString("room"), o.optBoolean("enc"), places,
                runCatching { When.valueOf(o.optString("when")) }.getOrDefault(When.Tonight), o.optBoolean("midnight"), o.optLong("since"),
                o.optLong("arrived"), o.optLong("warned")).takeIf { it.id.isNotEmpty() && it.places.isNotEmpty() }
        }
    }.getOrDefault(emptyList())

    // --- Sul telefono ---

    private fun prefs(c: Context) = c.getSharedPreferences("chat_arrival", Context.MODE_PRIVATE)
    fun rules(c: Context) = decode(prefs(c).getString("rules", null))
    @Synchronized fun save(c: Context, list: List<Rule>) { prefs(c).edit().putString("rules", encode(list)).apply(); register(c) }

    /** Quando la Chat è aperta su quell'account manda lei (tiene le chiavi). */
    @Volatile var sink: ((String, String, JSONObject) -> Boolean)? = null

    fun background(c: Context) = c.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun location(c: Context) = c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun pending(c: Context, rule: String, place: Int): PendingIntent = PendingIntent.getBroadcast(c, (rule + place).hashCode(),
        Intent(c, ArrivoReceiver::class.java).putExtra("rule", rule).putExtra("place", place), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

    /** Rimette gli avvisi di vicinanza per le regole accese (e toglie quelli vecchi), e l'avviso di mezzanotte. */
    @SuppressLint("MissingPermission")
    fun register(c: Context) {
        val lm = c.getSystemService(LocationManager::class.java) ?: return
        val list = rules(c)
        val old = prefs(c).getStringSet("registered", emptySet()).orEmpty()
        old.forEach { k -> val (r, p) = k.split('|').let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 0) }; runCatching { lm.removeProximityAlert(pending(c, r, p)) } }
        val now = mutableSetOf<String>()
        if (location(c)) list.forEach { r -> r.places.forEachIndexed { i, p ->
            runCatching { lm.addProximityAlert(p.lat, p.lon, RADIUS_M, -1, pending(c, r.id, i)); now += r.id + "|" + i }
        } }
        prefs(c).edit().putStringSet("registered", now).apply()
        val wm = WorkManager.getInstance(c)
        if (list.any { it.midnight }) {
            val t = LocalDate.now().plusDays(1).atStartOfDay().plusMinutes(1)
            wm.enqueueUniqueWork("eurise-chat-arrivo-mezzanotte", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ArrivoWorker>()
                .setInitialDelay((Programma.toMs(t) - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putString("kind", "midnight").build()).build())
        } else wm.cancelUniqueWork("eurise-chat-arrivo-mezzanotte")
    }
}

/** L'avviso di vicinanza: entrando nel posto parte il lavoro che manda la frase. */
class ArrivoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)) return
        val rule = intent.getStringExtra("rule") ?: return
        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<ArrivoWorker>()
            .setInputData(Data.Builder().putString("kind", "arrived").putString("rule", rule).putInt("place", intent.getIntExtra("place", 0)).build()).build())
    }
}

/** Manda «È arrivato a …» (o, a mezzanotte, «non è ancora arrivato») nella chat della regola, cifrato se la stanza lo è. */
class ArrivoWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val now = LocalDateTime.now()
        val today = now.toLocalDate().toEpochDay()
        val rules = Arrivo.rules(ctx)
        if (inputData.getString("kind") == "midnight") {
            val yesterday = now.toLocalDate().minusDays(1)
            val next = rules.map { r ->
                val due = r.whenever != Arrivo.When.SchoolDays || yesterday.dayOfWeek != DayOfWeek.SUNDAY
                if (due && Arrivo.lateWarning(r, yesterday)) {
                    send(ctx, r, ctx.getString(R.string.ar_not_yet, r.places.first().name.lowercase()))
                    r.copy(warnedDay = yesterday.toEpochDay())
                } else r
            }.filter { it.whenever != Arrivo.When.Tonight || Arrivo.active(it, now) }
            Arrivo.save(ctx, next)
            return Result.success()
        }
        val r = rules.firstOrNull { it.id == inputData.getString("rule") } ?: return Result.success()
        if (!Arrivo.active(r, now)) return Result.success()
        val place = r.places.getOrNull(inputData.getInt("place", 0)) ?: return Result.success()
        // Una volta per posto ogni mezz'ora: chi gira intorno a casa non manda dieci messaggi.
        val p = ctx.getSharedPreferences("chat_arrival", Context.MODE_PRIVATE)
        val key = "last_" + r.id + place.name
        if (System.currentTimeMillis() - p.getLong(key, 0) < 30 * 60_000L) return Result.success()
        p.edit().putLong(key, System.currentTimeMillis()).apply()
        send(ctx, r, ctx.getString(R.string.ar_arrived, place.name.lowercase(), "%d:%02d".format(now.hour, now.minute)))
        Arrivo.save(ctx, Arrivo.rules(ctx).map { if (it.id == r.id) it.copy(arrivedDay = today) else it })
        return Result.success()
    }

    private suspend fun send(ctx: Context, r: Arrivo.Rule, text: String) {
        val content = JSONObject().put("msgtype", "m.notice").put("body", "📍 " + text).put(Arrivo.STATE, JSONObject().put("auto", true))
        if (Arrivo.sink?.invoke(r.account, r.roomId, content) == true) return
        runCatching {
            val s = SessionStore(ctx, Account.valueOf(r.account)).load() ?: return
            val c = ChatClient(s)
            val cr = if (r.encrypted) ChatCrypto(ctx, s) else null
            ProgrammaStore.sendNow(c, cr, r.roomId, content, r.encrypted)
            cr?.let { runCatching { it.flush(c) } }
        }
    }
}

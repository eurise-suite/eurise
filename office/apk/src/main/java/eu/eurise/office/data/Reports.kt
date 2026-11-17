package eu.eurise.office.data

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import eu.eurise.office.BuildConfig
import eu.eurise.office.home.OfficePrefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Rapporti del problema (tavole OffRapporto e OffRapportoVedi, approvate il 14 novembre): quando Office o il motore si sono
 * chiusi da soli, la volta dopo la schermata iniziale lo dice e offre il rapporto. Le uscite le registra Android
 * (ApplicationExitInfo, da Android 11): quelle di Office le legge Office, quelle del motore le chiede al motore (provider
 * `Uscite`, solo app della suite). Il rapporto resta sul telefono 30 giorni e non esce se non lo si condivide.
 * Dentro: cosa è successo, quando, versioni e modello del telefono; mai il contenuto dei documenti né account. Il nome
 * del file solo se lo si sceglie.
 */
object Reports {
    /** Un'uscita del processo: [engine] vero se era il motore. [reason] è il codice di ApplicationExitInfo. */
    data class Exit(val time: Long, val engine: Boolean, val reason: Int, val status: Int, val pssKb: Long, val description: String)

    /** Un rapporto: l'uscita, cosa stava facendo Office ([doing]: tipo di documento) e il nome del file (solo sul telefono). */
    data class Report(
        val id: Long,
        val exit: Exit,
        val docKind: String,
        val fileName: String,
        val officeVersion: String,
        val engineVersion: String,
        val device: String,
        val android: String,
    )

    private const val DAYS_30 = 30L * 86_400_000L
    private fun file(context: Context) = File(context.filesDir, "rapporti.json")

    /** I rapporti sul telefono, il più recente per primo (quelli più vecchi di 30 giorni si cancellano). */
    fun all(context: Context): List<Report> {
        val now = System.currentTimeMillis()
        val list = runCatching { decode(file(context).readText()) }.getOrDefault(emptyList())
        val kept = list.filter { now - it.exit.time < DAYS_30 }
        if (kept.size != list.size) save(context, kept)
        return kept.sortedByDescending { it.exit.time }
    }

    fun delete(context: Context, id: Long) = save(context, all(context).filter { it.id != id })

    /**
     * Cerca le chiusure nuove dall'ultima volta (Office e motore) e ne fa rapporti. Restituisce il rapporto da mostrare
     * nella scheda della schermata iniziale, se ce n'è uno non ancora visto. Da chiamare fuori dal thread principale.
     */
    fun refresh(context: Context): Report? {
        if (Build.VERSION.SDK_INT < 30) return null
        val prefs = context.getSharedPreferences("office_rapporti", Context.MODE_PRIVATE)
        val since = prefs.getLong("visto_fino", 0L)
        val exits = (ownExits(context) + engineExits(context)).filter { it.time > since && isProblem(it) }
        if (since == 0L) {
            // Prima volta: si parte da adesso, senza ripescare le chiusure di prima dell'aggiornamento.
            prefs.edit().putLong("visto_fino", System.currentTimeMillis()).apply()
            return null
        }
        if (exits.isNotEmpty()) {
            val (name, kind, at) = OfficePrefs.lastEngineOpen(context)
            val reports = exits.map { e ->
                // Il documento conta se il motore l'aveva ricevuto nelle due ore prima della chiusura.
                val related = e.engine && at in (e.time - 7_200_000L)..e.time
                Report(
                    id = e.time,
                    exit = e,
                    docKind = if (related) kind else "",
                    fileName = if (related) name else "",
                    officeVersion = BuildConfig.VERSION_NAME,
                    engineVersion = engineVersion(context),
                    device = (Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL).trim(),
                    android = Build.VERSION.RELEASE,
                )
            }
            save(context, (all(context) + reports).distinctBy { it.id })
            prefs.edit().putLong("visto_fino", exits.maxOf { it.time }).putLong("da_mostrare", reports.maxOf { it.id }).apply()
        }
        val show = prefs.getLong("da_mostrare", 0L)
        return all(context).firstOrNull { it.id == show }
    }

    /** «Ignora» o rapporto visto: la scheda non torna (il rapporto resta in Impostazioni › Rapporti dei problemi). */
    fun dismiss(context: Context) {
        context.getSharedPreferences("office_rapporti", Context.MODE_PRIVATE).edit().remove("da_mostrare").apply()
    }

    @androidx.annotation.RequiresApi(30)
    private fun ownExits(context: Context): List<Exit> = runCatching {
        val am = context.getSystemService(ActivityManager::class.java)
        am.getHistoricalProcessExitReasons(null, 0, 8).map {
            Exit(it.timestamp, false, it.reason, it.status, it.pss, it.description.orEmpty())
        }
    }.getOrDefault(emptyList())

    private fun engineExits(context: Context): List<Exit> = runCatching {
        val b = context.contentResolver.call(Uri.parse("content://" + Engine.packageName + ".uscite"), "uscite", null, null)
            ?: return emptyList()
        val times = b.getLongArray("tempi") ?: return emptyList()
        val reasons = b.getIntArray("motivi") ?: IntArray(0)
        val statuses = b.getIntArray("stati") ?: IntArray(0)
        val pss = b.getLongArray("memoria") ?: LongArray(0)
        val descriptions = b.getStringArray("descrizioni") ?: emptyArray()
        times.indices.map { i ->
            Exit(times[i], true, reasons.getOrElse(i) { 0 }, statuses.getOrElse(i) { 0 }, pss.getOrElse(i) { 0L }, descriptions.getOrElse(i) { "" })
        }
    }.getOrDefault(emptyList())

    private fun engineVersion(context: Context) = Engine.version(context).orEmpty()

    // Codici di ApplicationExitInfo (stabili da Android 11): si scrivono qui per poterli provare senza telefono.
    const val REASON_EXIT_SELF = 1
    const val REASON_SIGNALED = 2
    const val REASON_LOW_MEMORY = 3
    const val REASON_CRASH = 4
    const val REASON_CRASH_NATIVE = 5
    const val REASON_ANR = 6
    const val REASON_INITIALIZATION_FAILURE = 7
    const val REASON_EXCESSIVE_RESOURCE_USAGE = 9

    /**
     * Le chiusure che sono un problema: arresti (Java e nativi), «non risponde», memoria finita, avvio fallito, risorse
     * eccessive, e i segnali che non sono uno stop normale. Restano fuori l'utente che chiude, l'app che esce da sé e il
     * sistema che riordina (cambio di permessi, aggiornamento…).
     */
    fun isProblem(e: Exit): Boolean = when (e.reason) {
        REASON_CRASH, REASON_CRASH_NATIVE, REASON_ANR, REASON_LOW_MEMORY, REASON_INITIALIZATION_FAILURE, REASON_EXCESSIVE_RESOURCE_USAGE -> true
        // 9 = SIGKILL (chiusura del sistema, di solito per memoria quando l'app era in primo piano: «lmk»), 6 = SIGABRT, 11 = SIGSEGV.
        REASON_SIGNALED -> e.status == 6 || e.status == 11 || e.status == 7 || (e.status == 9 && e.description.contains("lmk", ignoreCase = true))
        else -> false
    }

    /** Il tipo di problema, per la frase «Cosa è successo». */
    enum class Kind { Memory, Crash, NativeCrash, NotResponding, Other }

    fun kindOf(e: Exit): Kind = when {
        e.reason == REASON_LOW_MEMORY || (e.reason == REASON_SIGNALED && e.status == 9) -> Kind.Memory
        e.reason == REASON_CRASH -> Kind.Crash
        e.reason == REASON_CRASH_NATIVE || (e.reason == REASON_SIGNALED && e.status in setOf(6, 7, 11)) -> Kind.NativeCrash
        e.reason == REASON_ANR -> Kind.NotResponding
        else -> Kind.Other
    }

    /** La parte tecnica del rapporto, in testo semplice (anche quello che si copia e si condivide). */
    fun technical(r: Report): String = buildString {
        append("uscita: ").append(reasonName(r.exit.reason))
        if (r.exit.reason == REASON_SIGNALED) append(" · segnale ").append(r.exit.status)
        append(" (processo ").append(if (r.exit.engine) "motore" else "Office").append(")\n")
        if (r.exit.pssKb > 0) append("memoria usata: ").append(r.exit.pssKb / 1024).append(" MB\n")
        if (r.exit.description.isNotBlank()) append("Android: ").append(r.exit.description.take(400)).append('\n')
        if (r.docKind.isNotEmpty()) append("documento: ").append(r.docKind).append('\n')
    }

    fun reasonName(reason: Int): String = when (reason) {
        REASON_EXIT_SELF -> "uscita"
        REASON_SIGNALED -> "segnale"
        REASON_LOW_MEMORY -> "memoria esaurita"
        REASON_CRASH -> "arresto (Java)"
        REASON_CRASH_NATIVE -> "arresto nativo"
        REASON_ANR -> "non risponde"
        REASON_INITIALIZATION_FAILURE -> "avvio non riuscito"
        REASON_EXCESSIVE_RESOURCE_USAGE -> "risorse eccessive"
        else -> "motivo $reason"
    }

    private fun save(context: Context, list: List<Report>) {
        runCatching { file(context).writeText(encode(list)) }
    }

    fun encode(list: List<Report>): String = JSONArray().apply {
        list.forEach { r ->
            put(JSONObject().apply {
                put("id", r.id); put("tempo", r.exit.time); put("motore", r.exit.engine); put("motivo", r.exit.reason)
                put("stato", r.exit.status); put("memoria", r.exit.pssKb); put("descrizione", r.exit.description)
                put("tipo", r.docKind); put("file", r.fileName); put("office", r.officeVersion); put("versione_motore", r.engineVersion)
                put("telefono", r.device); put("android", r.android)
            })
        }
    }.toString()

    fun decode(text: String): List<Report> {
        val array = JSONArray(text)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Report(
                id = o.getLong("id"),
                exit = Exit(o.getLong("tempo"), o.getBoolean("motore"), o.getInt("motivo"), o.optInt("stato"), o.optLong("memoria"), o.optString("descrizione")),
                docKind = o.optString("tipo"),
                fileName = o.optString("file"),
                officeVersion = o.optString("office"),
                engineVersion = o.optString("versione_motore"),
                device = o.optString("telefono"),
                android = o.optString("android"),
            )
        }
    }
}

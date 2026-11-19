package eu.eurise.feature.assistant

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * I due cervelli di Alfred (tavola AlfCervello). Modelli liberi, GGUF ufficiali dei loro autori su Hugging Face, Q4_K_M.
 * Si scaricano da Eurise (Eurise AI non ha Internet) e poi si passano a Eurise AI, che ne tiene la copia.
 */
enum class BrainModel(val id: String, val repo: String, val label: Int, val detail: Int) {
    /** Granite 4.0 1B di IBM, Apache 2.0, italiano tra le lingue: comandi e domande brevi. */
    Rapido("rapido", "ibm-granite/granite-4.0-1b-GGUF", R.string.alfred_brain_fast, R.string.alfred_brain_fast_d),
    /** Ministral 3 3B Instruct di Mistral AI, Apache 2.0: più preciso, più lento. */
    Completo("completo", "mistralai/Ministral-3-3B-Instruct-2512-GGUF", R.string.alfred_brain_full, R.string.alfred_brain_full_d),
}

object BrainDownload {
    private fun prefs(context: Context) = context.getSharedPreferences("alfred_brain", Context.MODE_PRIVATE)
    private fun manager(context: Context) = context.getSystemService(DownloadManager::class.java)

    fun file(context: Context, model: BrainModel) = File(context.getExternalFilesDir("cervello"), "${model.id}.gguf")

    /**
     * Il file da scaricare: il Q4_K_M fissato a una versione precisa con la sua impronta (Contents, docs/SICUREZZA.md).
     * Prima si chiedeva a Hugging Face l'elenco del ramo main: un file cambiato a monte arrivava a llama.cpp senza controlli
     * (e c'era una richiesta in più).
     */
    @Suppress("RedundantSuspendModifier")
    suspend fun url(model: BrainModel): String? = eu.eurise.core.suite.Contents.brain[model.repo]?.url

    /** Comincia lo scarico (solo Wi-Fi, con la notifica di sistema): continua anche chiudendo Alfred. */
    fun start(context: Context, model: BrainModel, url: String, title: String) {
        file(context, model).delete()
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(title)
            .setDestinationInExternalFilesDir(context, "cervello", "${model.id}.gguf")
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val id = manager(context).enqueue(req)
        prefs(context).edit().putLong(model.id, id).apply()
    }

    fun cancel(context: Context, model: BrainModel) {
        val id = prefs(context).getLong(model.id, -1)
        if (id >= 0) manager(context).remove(id)
        prefs(context).edit().remove(model.id).apply()
        file(context, model).delete()
    }

    /** Stato dello scarico: null se non ce n'è uno; altrimenti percentuale (0–100) e se è finito. */
    fun progress(context: Context, model: BrainModel): Pair<Int, Boolean>? {
        val id = prefs(context).getLong(model.id, -1).takeIf { it >= 0 } ?: return null
        manager(context).query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return null
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            if (status == DownloadManager.STATUS_FAILED) { prefs(context).edit().remove(model.id).apply(); return null }
            val pct = if (total > 0) (done * 100 / total).toInt() else 0
            return pct to (status == DownloadManager.STATUS_SUCCESSFUL)
        }
        return null
    }

    /**
     * Passa a Eurise AI gli scarichi finiti (uno alla volta); [onStart] avvisa che la copia comincia.
     * Vero se un modello nuovo è stato installato.
     */
    suspend fun handOverFinished(context: Context, brain: AlfredBrain, onStart: () -> Unit = {}): Boolean {
        val m = BrainModel.entries.firstOrNull { progress(context, it)?.second == true } ?: return false
        if (!brain.installed) return false
        onStart()
        // llama.cpp legge il modello con codice C: solo il file con l'impronta attesa, controllata fuori dal thread
        // dell'interfaccia (qualche secondo per 1–2 GB). Un file diverso si butta e si riscarica.
        val pinned = eu.eurise.core.suite.Contents.brain[m.repo]
        val genuine = pinned != null && withContext(Dispatchers.IO) { eu.eurise.core.suite.Contents.matches(file(context, m), pinned) }
        if (!genuine) { handedOver(context, m); return false }
        val ok = brain.install(file(context, m), m.id)
        if (ok) handedOver(context, m)
        return ok
    }

    /** Scarico finito e passato a Eurise AI: si toglie la copia di Eurise. */
    fun handedOver(context: Context, model: BrainModel) {
        prefs(context).edit().remove(model.id).apply()
        file(context, model).delete()
    }
}

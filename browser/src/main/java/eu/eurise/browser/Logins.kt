package eu.eurise.browser

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.eurise.core.suite.PasswordContract as C
import java.util.concurrent.Executors
import org.mozilla.geckoview.Autocomplete
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * Una risposta a Gecko data una volta sola. Gecko può aver già chiuso la richiesta (pagina cambiata, modulo sparito) o la
 * bolla può essere toccata due volte: rispondere di nuovo lanciava un errore che chiudeva il Browser (prova del 13
 * novembre: schermo nero mentre compilava le password). Qui la seconda risposta non fa niente.
 */
abstract class OnceAnswer(
    private val request: GeckoSession.PromptDelegate.BasePrompt,
    private val result: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>,
) {
    private var answered = false

    protected fun answer(response: () -> GeckoSession.PromptDelegate.PromptResponse) {
        if (answered || request.isComplete) { answered = true; return }
        answered = true
        runCatching { result.complete(response()) }
    }

    /** Gecko chiude la richiesta da sé: [onGone] toglie la bolla. */
    fun onGone(onGone: () -> Unit) = runCatching {
        request.setDelegate(object : GeckoSession.PromptDelegate.PromptInstanceDelegate {
            override fun onPromptDismiss(prompt: GeckoSession.PromptDelegate.BasePrompt) {
                answered = true
                onGone()
            }
        })
    }
}

/** Una scelta di accesso da confermare con un tocco (bolla «Accedi come…»). */
class LoginChoice(
    val options: List<Autocomplete.LoginSelectOption>,
    private val request: GeckoSession.PromptDelegate.AutocompleteRequest<Autocomplete.LoginSelectOption>,
    result: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>,
) : OnceAnswer(request, result) {
    fun pick(option: Autocomplete.LoginSelectOption) = answer { request.confirm(option) }
    fun dismiss() = answer { request.dismiss() }
}

/** Un accesso nuovo o cambiato da salvare in Eurise Password (bolla «Salvo l'accesso?»). */
class LoginSave(
    val entry: Autocomplete.LoginEntry,
    val update: Boolean,
    private val request: GeckoSession.PromptDelegate.AutocompleteRequest<Autocomplete.LoginSaveOption>,
    result: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>,
) : OnceAnswer(request, result) {
    fun save() = answer { request.confirm(request.options.first()) }
    fun dismiss() = answer { request.dismiss() }
}

/**
 * Gli accessi ai siti vengono da Eurise Password, chiesti direttamente (PasswordContract, solo tra app della suite):
 * Gecko riconosce il modulo, chiede gli accessi del dominio e compila da sé (un solo accesso) o chiede quale (bolla).
 * A cassaforte chiusa niente accessi: la pagina mostra «Sblocca» e, aperta, si ricarica. Nessuna password resta nel
 * Browser: le si chiede ogni volta.
 */
object Logins : Autocomplete.StorageDelegate {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var app: Context
    private var passwordPackage = ""

    /** Un modulo di accesso su un dominio con Password chiusa: la bolla «Sblocca» (dominio), finché non la si chiude. */
    var lockedDomain by mutableStateOf<String?>(null)
    /** Quanti accessi ci sono per il dominio della pagina (per dirlo nella bolla dell'indirizzo). */
    var lastDomain by mutableStateOf<String?>(null)
        private set

    fun init(context: Context, passwordPackage: String) {
        app = context.applicationContext
        this.passwordPackage = passwordPackage
    }

    private fun call(method: String, arg: String? = null, extras: Bundle? = null): Bundle? = runCatching {
        app.contentResolver.call(Uri.parse("content://" + C.loginsAuthority(passwordPackage)), method, arg, extras)
    }.getOrNull()

    override fun onLoginFetch(domain: String): GeckoResult<Array<Autocomplete.LoginEntry>> {
        val result = GeckoResult<Array<Autocomplete.LoginEntry>>()
        io.execute {
            val b = call(C.METHOD_FETCH, domain)
            val entries = if (b?.getBoolean(C.KEY_OPEN) == true) {
                val guid = b.getStringArray(C.KEY_GUID).orEmpty()
                val user = b.getStringArray(C.KEY_USERNAME).orEmpty()
                val pass = b.getStringArray(C.KEY_PASSWORD).orEmpty()
                val origin = b.getStringArray(C.KEY_ORIGIN).orEmpty()
                // Gecko compila solo gli accessi con l'origine esatta della pagina: Password li ha già scelti per dominio
                // (anche i sottodomini), quindi prendono l'origine della pagina aperta. Il modulo può mandare i dati
                // altrove (accounts.…): l'origine del modulo resta vuota, che per Gecko vuol dire «qualsiasi».
                // Prova del 13 novembre: accessi salvati in Password ma mai compilati.
                val page = pageOrigin(domain)
                guid.indices.filter { it < user.size && it < pass.size && it < origin.size }.map { i ->
                    Autocomplete.LoginEntry.Builder().guid(guid[i]).origin(page ?: origin[i]).formActionOrigin("")
                        .username(user[i]).password(pass[i]).build()
                }.toTypedArray()
            } else emptyArray()
            main.post {
                lastDomain = domain
                // Cassaforte chiusa (Password c'è ma non risponde «aperta»): si offre lo sblocco.
                lockedDomain = if (b != null && !b.getBoolean(C.KEY_OPEN)) domain else null
            }
            result.complete(entries)
        }
        return result
    }

    override fun onLoginSave(login: Autocomplete.LoginEntry) {
        io.execute {
            val extras = Bundle().apply {
                putString(C.KEY_ORIGIN, login.origin)
                putString(C.KEY_USERNAME, login.username)
                putString(C.KEY_PASSWORD, login.password)
                login.guid?.let { putString(C.KEY_GUID, it) }
            }
            val b = call(C.METHOD_SAVE, extras = extras)
            if (b?.getBoolean(C.KEY_OK) != true) main.post { onSaveFailed?.invoke(login) }
        }
    }

    override fun onLoginUsed(login: Autocomplete.LoginEntry, usedFields: Int) {
        val guid = login.guid ?: return
        io.execute { call(C.METHOD_USED, guid) }
    }

    /** L'indirizzo della scheda in primo piano (lo imposta l'attività). */
    var currentUrl: () -> String? = { null }

    /** «https://www.sito.it» della pagina aperta, se è del dominio chiesto da Gecko. */
    private fun pageOrigin(domain: String): String? = runCatching {
        val url = Uri.parse(currentUrl() ?: return null)
        val host = url.host ?: return null
        val scheme = url.scheme?.takeIf { it == "https" || it == "http" } ?: return null
        val d = domain.removePrefix("www.")
        if (host != domain && host != d && !host.endsWith(".$d")) return null
        scheme + "://" + host + if (url.port > 0) ":" + url.port else ""
    }.getOrNull()

    /** Salvataggio non riuscito (cassaforte chiusa): l'attività apre Password con la voce compilata. */
    var onSaveFailed: ((Autocomplete.LoginEntry) -> Unit)? = null

    /** Il nome da mostrare: il nome utente o, se manca, il sito. */
    fun label(entry: Autocomplete.LoginEntry) = entry.username.ifBlank { Uri.parse(entry.origin).host.orEmpty() }
}

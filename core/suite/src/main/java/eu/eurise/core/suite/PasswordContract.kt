package eu.eurise.core.suite

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Una voce da tenere in Eurise Password, mandata da un'altra app della suite (per ora la chiave di recupero della Chat).
 * Password apre la scheda della voce già compilata e la salva solo se la persona conferma; nulla passa per gli appunti.
 * L'ingresso ([ALIAS]) accetta solo le app firmate con la stessa chiave (permesso della suite).
 */
object PasswordContract {
    const val ACTION_ADD = "eu.eurise.action.ADD_PASSWORD"
    /** L'ingresso protetto dal permesso della suite (activity-alias di PasswordActivity). */
    const val ALIAS = "eu.eurise.password.AddFromSuite"
    const val EXTRA_TITLE = "title"
    const val EXTRA_USERNAME = "username"
    const val EXTRA_SECRET = "secret"
    const val EXTRA_URL = "url"
    const val EXTRA_NOTES = "notes"

    fun passwordPackage(debug: Boolean) = if (debug) "eu.eurise.password.debug" else "eu.eurise.password"

    /**
     * Accessi ai siti per il Browser della suite (fornitore protetto dal permesso della suite). Funziona solo a cassaforte
     * aperta: chiusa, risponde [KEY_OPEN] falso e il Browser offre «Sblocca» ([ACTION_UNLOCK] sullo stesso [ALIAS]).
     */
    fun loginsAuthority(passwordPackage: String) = "$passwordPackage.logins"
    const val ACTION_UNLOCK = "eu.eurise.action.UNLOCK_PASSWORD"
    const val METHOD_STATUS = "status"
    /** arg: il dominio della pagina. Risposta: [KEY_GUID], [KEY_USERNAME], [KEY_PASSWORD], [KEY_ORIGIN] (array). */
    const val METHOD_FETCH = "fetch"
    /** extras: [KEY_ORIGIN], [KEY_USERNAME], [KEY_PASSWORD] e, per aggiornare, [KEY_GUID]. Risposta: [KEY_OK]. */
    const val METHOD_SAVE = "save"
    /** arg: guid della voce appena usata (le più usate vengono prima). */
    const val METHOD_USED = "used"
    const val KEY_OPEN = "open"
    const val KEY_OK = "ok"
    const val KEY_GUID = "guid"
    const val KEY_USERNAME = "username"
    const val KEY_PASSWORD = "password"
    const val KEY_ORIGIN = "origin"

    /** L'intent per aggiungere la voce, o null se Password non è installata. */
    fun addIntent(context: Context, debug: Boolean, title: String, secret: String, username: String = "", url: String = "", notes: String = ""): Intent? {
        val intent = Intent(ACTION_ADD).setComponent(ComponentName(passwordPackage(debug), ALIAS))
            .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_SECRET, secret).putExtra(EXTRA_USERNAME, username)
            .putExtra(EXTRA_URL, url).putExtra(EXTRA_NOTES, notes)
        return intent.takeIf { context.packageManager.resolveActivity(it, 0) != null }
    }
}

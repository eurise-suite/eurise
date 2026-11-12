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

    /** L'intent per aggiungere la voce, o null se Password non è installata. */
    fun addIntent(context: Context, debug: Boolean, title: String, secret: String, username: String = "", url: String = "", notes: String = ""): Intent? {
        val intent = Intent(ACTION_ADD).setComponent(ComponentName(passwordPackage(debug), ALIAS))
            .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_SECRET, secret).putExtra(EXTRA_USERNAME, username)
            .putExtra(EXTRA_URL, url).putExtra(EXTRA_NOTES, notes)
        return intent.takeIf { context.packageManager.resolveActivity(it, 0) != null }
    }
}

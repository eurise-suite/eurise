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

    /**
     * «I miei dati» per un'altra app della suite (Office: compilare un modulo PDF, tavola OffPdf). Password sblocca, mostra i
     * profili e restituisce quello scelto solo dopo il tocco della persona (con impronta o PIN se ha dati riservati):
     * risultato [KEY_TITLE], [KEY_FIELD_NAMES] e [KEY_FIELD_VALUES] (stesso ordine, nomi dei campi come in Password:
     * «Nome», «Codice fiscale», «CAP»…). Nulla passa per gli appunti. Attività protetta dal permesso della suite.
     */
    const val ACTION_PICK_MY_DATA = "eu.eurise.action.PICK_MY_DATA"
    const val MY_DATA_ACTIVITY = "eu.eurise.password.autofill.MyDataActivity"
    const val KEY_TITLE = "title"
    const val KEY_FIELD_NAMES = "field_names"
    const val KEY_FIELD_VALUES = "field_values"

    /** L'intent per scegliere un profilo di «I miei dati», o null se Password della suite non c'è. */
    fun pickMyDataIntent(context: Context, debug: Boolean): Intent? {
        val pkg = passwordPackage(debug)
        if (!SuiteAccess.isSuitePackage(context, pkg)) return null
        val intent = Intent(ACTION_PICK_MY_DATA).setComponent(ComponentName(pkg, MY_DATA_ACTIVITY))
        return intent.takeIf { context.packageManager.resolveActivity(it, 0) != null }
    }

    /** L'intent per aggiungere la voce, o null se Password non è installata. */
    fun addIntent(context: Context, debug: Boolean, title: String, secret: String, username: String = "", url: String = "", notes: String = ""): Intent? {
        val intent = Intent(ACTION_ADD).setComponent(ComponentName(passwordPackage(debug), ALIAS))
            .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_SECRET, secret).putExtra(EXTRA_USERNAME, username)
            .putExtra(EXTRA_URL, url).putExtra(EXTRA_NOTES, notes)
        // Il segreto va solo a Password della suite: se manca, un'app con lo stesso nome di pacchetto non lo riceve.
        if (!SuiteAccess.isSuitePackage(context, passwordPackage(debug))) return null
        return intent.takeIf { context.packageManager.resolveActivity(it, 0) != null }
    }
}

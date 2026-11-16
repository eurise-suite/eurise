package eu.eurise.core.suite

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * «Scansiona e restituisci»: un'altra app della suite (Office) apre lo Scanner dell'app Eurise e riceve le pagine come PDF,
 * senza passare da «Salva in…». Il risultato è un content:// in sola lettura in [Intent.getData]. L'ingresso ([ACTIVITY])
 * accetta solo le app firmate con la stessa chiave (permesso della suite).
 */
object ScannerContract {
    const val ACTION_SCAN = "eu.eurise.action.SCAN_PAGES"
    const val ACTIVITY = "eu.eurise.hub.ScanForResultActivity"

    /** L'intent da lanciare per un risultato, o null se l'app Eurise non c'è. */
    fun scanIntent(context: Context, hubPackage: String): Intent? {
        val intent = Intent(ACTION_SCAN).setComponent(ComponentName(hubPackage, ACTIVITY))
        return intent.takeIf { context.packageManager.resolveActivity(it, 0) != null }
    }
}

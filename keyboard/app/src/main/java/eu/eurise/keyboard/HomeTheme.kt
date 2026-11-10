package eu.eurise.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.utils.prefs

/**
 * Chiaro o scuro come la Scrivania di Eurise: la Scrivania dice alla tastiera se le sue bolle sono scure
 * (di notte, con il cielo scuro o con il tema forzato) e la tastiera, in modalità automatica, fa lo stesso.
 * Finché la Scrivania non ha mai parlato si segue il sistema, come prima.
 */
object HomeTheme {
    /** Impostazione: seguire la Scrivania (di base sì). */
    const val FOLLOW = "eurise_follow_home"
    private const val DARK = "eurise_home_dark"
    const val ACTION = "eu.eurise.action.HOME_THEME"
    const val EXTRA_DARK = "dark"

    /** Scuro o chiaro secondo la Scrivania; null se non la si segue o non si sa ancora. */
    fun dark(prefs: SharedPreferences): Boolean? =
        if (!prefs.getBoolean(FOLLOW, true) || !prefs.contains(DARK)) null else prefs.getBoolean(DARK, false)

    internal fun store(context: Context, dark: Boolean) {
        val prefs = context.prefs()
        if (prefs.contains(DARK) && prefs.getBoolean(DARK, false) == dark) return
        prefs.edit { putBoolean(DARK, dark) }
        if (prefs.getBoolean(FOLLOW, true)) KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }
}

/** Riceve dalla Scrivania (solo app Eurise con la stessa firma) il tema delle bolle. */
class HomeThemeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HomeTheme.ACTION || !intent.hasExtra(HomeTheme.EXTRA_DARK)) return
        HomeTheme.store(context, intent.getBooleanExtra(HomeTheme.EXTRA_DARK, false))
    }
}

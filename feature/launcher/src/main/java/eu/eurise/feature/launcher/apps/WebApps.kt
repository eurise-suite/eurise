package eu.eurise.feature.launcher.apps

import android.app.Activity
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.widget.Toast
import eu.eurise.feature.launcher.R

/** Una web app installata da un browser: è una scorciatoia fissata che appartiene al browser. */
data class WebShortcut(val packageName: String, val id: String, val user: UserHandle)

/**
 * Le web app (PWA e "Aggiungi a schermata Home") arrivano al launcher come scorciatoie fissate: il browser chiede di
 * fissarle e il launcher predefinito le accetta. Eurise le tiene tra le app, nella categoria che il nome suggerisce
 * o in Browser; si aprono, si spostano e si tolgono come le altre. Funziona solo se Eurise è il launcher predefinito.
 */
object WebApps {

    private fun launcherApps(context: Context) = context.getSystemService(LauncherApps::class.java)

    /** Le web app fissate in Eurise, per tutti i profili (lavoro compreso). */
    fun load(context: Context): List<AppEntry> {
        val la = launcherApps(context) ?: return emptyList()
        if (!runCatching { la.hasShortcutHostPermission() }.getOrDefault(false)) return emptyList()
        val users = runCatching { la.profiles }.getOrNull() ?: listOf(Process.myUserHandle())
        return users.flatMap { user ->
            val query = LauncherApps.ShortcutQuery().setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            runCatching { la.getShortcuts(query, user) }.getOrNull().orEmpty()
        }.filter { it.isEnabled }.map { it.toEntry() }
    }

    private fun ShortcutInfo.toEntry(): AppEntry {
        val label = (longLabel?.takeIf { it.length <= 30 } ?: shortLabel ?: id).toString()
        return AppEntry(
            key = "web:$`package`/$id",
            label = label,
            packageName = `package`,
            component = null,
            category = classifyApp("", label) ?: Category.Browser,
            shortcut = WebShortcut(`package`, id, userHandle),
        )
    }

    fun launch(context: Context, s: WebShortcut) {
        runCatching { launcherApps(context)?.startShortcut(s.packageName, s.id, null, null, s.user) }
    }

    fun icon(context: Context, s: WebShortcut): Drawable? {
        val la = launcherApps(context) ?: return null
        val query = LauncherApps.ShortcutQuery()
            .setPackage(s.packageName)
            .setShortcutIds(listOf(s.id))
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        val info = runCatching { la.getShortcuts(query, s.user) }.getOrNull()?.firstOrNull() ?: return null
        return runCatching { la.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi) }.getOrNull()
    }

    /** Toglie la web app: resta solo nel browser che l'ha creata (i suoi dati del sito non si toccano). */
    fun remove(context: Context, s: WebShortcut) {
        val la = launcherApps(context) ?: return
        val query = LauncherApps.ShortcutQuery().setPackage(s.packageName).setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        val keep = runCatching { la.getShortcuts(query, s.user) }.getOrNull().orEmpty().map { it.id }.filter { it != s.id }
        runCatching { la.pinShortcuts(s.packageName, keep, s.user) }
    }

    /** Callback per aggiornare l'elenco quando un browser aggiunge, cambia o toglie una web app. */
    fun watch(context: Context, onChange: () -> Unit): () -> Unit {
        val la = launcherApps(context) ?: return {}
        val cb = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackageAdded(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackageChanged(packageName: String?, user: UserHandle?) = onChange()
            override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = onChange()
            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = onChange()
            override fun onShortcutsChanged(packageName: String, shortcuts: MutableList<ShortcutInfo>, user: UserHandle) = onChange()
        }
        runCatching { la.registerCallback(cb, android.os.Handler(android.os.Looper.getMainLooper())) }
        return { runCatching { la.unregisterCallback(cb) } }
    }
}

/**
 * Riceve dal sistema la richiesta di un browser di aggiungere una web app. Il browser ha già chiesto conferma
 * alla persona ("Installa", "Aggiungi a schermata Home"), quindi qui si accetta subito e si dice dove trovarla.
 */
class PinShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = runCatching { getSystemService(LauncherApps::class.java)?.getPinItemRequest(intent) }.getOrNull()
        if (request != null && request.isValid && request.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            val info = request.shortcutInfo
            if (info != null && runCatching { request.accept() }.getOrDefault(false)) {
                val label = (info.shortLabel ?: info.id).toString()
                val category = classifyApp("", label) ?: Category.Browser
                Toast.makeText(this, getString(R.string.webapp_added, label, category.label), Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }
}

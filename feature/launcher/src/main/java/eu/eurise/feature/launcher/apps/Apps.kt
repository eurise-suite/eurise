package eu.eurise.feature.launcher.apps

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.vector.ImageVector
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.CalendarTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Le categorie della barra in basso. Preferiti non raccoglie app per tipo: sono quelle scelte dall'utente.
 * Il colore del simbolo delle icone a tema viene dalla categoria (variante B delle icone).
 */
enum class Category(val label: String, val icon: (Float) -> ImageVector, val tint: CalendarTint) {
    Favorites("Preferiti", { EuriseIcons.star(it) }, CalendarTint.Grafite),
    Communication("Comunicazione", { EuriseIcons.chat(it) }, CalendarTint.Salvia),
    Organization("Organizzazione", { EuriseIcons.calendar(it) }, CalendarTint.BluNotte),
    PhotosFiles("Foto e file", { EuriseIcons.folder(it) }, CalendarTint.Ottone),
    Tools("Strumenti", { EuriseIcons.tools(it) }, CalendarTint.Prugna),
    Other("Altre app", { EuriseIcons.grid(it) }, CalendarTint.Grafite),
}

/** Un'app (o una sezione di Eurise) che si può aprire dal launcher. */
data class AppEntry(
    val key: String,
    val label: String,
    val packageName: String,
    val component: ComponentName?,
    val category: Category,
    /** Per le sezioni di Eurise: la sezione da aprire nell'app hub. */
    val euriseSection: String? = null,
)

/** Elenco delle app installate, aggiornato quando se ne installa o rimuove una. */
class AppsRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager

    fun observe(): Flow<List<AppEntry>> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        trySend(Unit)
        awaitClose { context.unregisterReceiver(receiver) }
    }.conflate().map { load() }.flowOn(Dispatchers.IO)

    private fun load(): List<AppEntry> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val own = context.packageName
        val installed = pm.queryIntentActivities(launcherIntent, 0)
            .filter { it.activityInfo.packageName != own }
            .map { info ->
                val pkg = info.activityInfo.packageName
                AppEntry(
                    key = "$pkg/${info.activityInfo.name}",
                    label = info.loadLabel(pm).toString(),
                    packageName = pkg,
                    component = ComponentName(pkg, info.activityInfo.name),
                    category = categorize(pkg, info.activityInfo.applicationInfo),
                )
            }
        return (euriseSections(own) + installed).sortedBy { it.label.lowercase() }
    }

    /** Le sezioni dell'app Eurise compaiono come app a sé nelle loro categorie. */
    private fun euriseSections(own: String) = listOf(
        AppEntry("eurise:today", "Oggi", own, null, Category.Organization, "today"),
        AppEntry("eurise:calendar", "Calendario", own, null, Category.Organization, "calendar"),
        AppEntry("eurise:notes", "Note", own, null, Category.Organization, "notes"),
        AppEntry("eurise:tasks", "Attività", own, null, Category.Organization, "tasks"),
    )

    /**
     * Assegna una categoria: prima le app note per nome di pacchetto, poi la categoria che l'app dichiara
     * ad Android. Quello che resta va in "Altre app".
     */
    private fun categorize(pkg: String, app: ApplicationInfo): Category {
        KNOWN.entries.firstOrNull { (prefix, _) -> pkg.startsWith(prefix) }?.let { return it.value }
        return when (app.category) {
            ApplicationInfo.CATEGORY_SOCIAL -> Category.Communication
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> Category.Organization
            ApplicationInfo.CATEGORY_IMAGE, ApplicationInfo.CATEGORY_VIDEO -> Category.PhotosFiles
            ApplicationInfo.CATEGORY_MAPS, ApplicationInfo.CATEGORY_NEWS -> Category.Tools
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_GAME -> Category.Other
            else -> Category.Other
        }
    }

    private companion object {
        val KNOWN = linkedMapOf(
            // Comunicazione
            "com.android.dialer" to Category.Communication, "com.google.android.dialer" to Category.Communication,
            "org.fossify.phone" to Category.Communication, "com.android.messaging" to Category.Communication,
            "org.fossify.messages" to Category.Communication, "com.android.contacts" to Category.Communication,
            "org.fossify.contacts" to Category.Communication, "foundation.e.mail" to Category.Communication,
            "com.fsck.k9" to Category.Communication, "net.thunderbird" to Category.Communication,
            "ch.protonmail" to Category.Communication, "com.infomaniak.mail" to Category.Communication,
            "com.whatsapp" to Category.Communication, "org.telegram" to Category.Communication,
            "org.thoughtcrime.securesms" to Category.Communication, "im.vector" to Category.Communication,
            "io.element" to Category.Communication,
            // Organizzazione
            "foundation.e.calendar" to Category.Organization, "org.fossify.calendar" to Category.Organization,
            "org.fossify.clock" to Category.Organization, "com.android.deskclock" to Category.Organization,
            "foundation.e.notes" to Category.Organization, "foundation.e.tasks" to Category.Organization,
            "org.tasks" to Category.Organization, "com.kunzisoft.keepass" to Category.Organization,
            // Foto e file
            "foundation.e.gallery" to Category.PhotosFiles, "org.fossify.gallery" to Category.PhotosFiles,
            "com.android.camera" to Category.PhotosFiles, "foundation.e.camera" to Category.PhotosFiles,
            "com.nextcloud" to Category.PhotosFiles, "com.infomaniak.drive" to Category.PhotosFiles,
            "foundation.e.drive" to Category.PhotosFiles, "com.android.documentsui" to Category.PhotosFiles,
            "org.fossify.filemanager" to Category.PhotosFiles,
            // Strumenti
            "foundation.e.browser" to Category.Tools, "org.mozilla" to Category.Tools, "app.organicmaps" to Category.Tools,
            "app.comaps" to Category.Tools, "foundation.e.privacycentral" to Category.Tools,
            "foundation.e.advancedprivacy" to Category.Tools, "com.android.settings" to Category.Tools,
            "foundation.e.apps" to Category.Tools, "org.fdroid" to Category.Tools, "de.markusfisch.android.binaryeye" to Category.Tools,
        )
    }
}

/** Apre un'app o una sezione di Eurise. */
fun Context.launch(entry: AppEntry) {
    val intent = when {
        entry.euriseSection != null -> return openEurise(entry.euriseSection)
        entry.component != null ->
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(entry.component)
        else -> return
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { startActivity(intent) }
}

/** Sezione da aprire nell'app hub: "today", "calendar", "notes", "tasks", "new_event", "new_note". */
const val EXTRA_SECTION = "eu.eurise.extra.SECTION"

/** Apre una sezione dell'app Eurise (vedi [EXTRA_SECTION]); "note:<id>" apre una nota. */
fun Context.openEurise(section: String) {
    val intent = Intent().setClassName(packageName, "eu.eurise.hub.MainActivity")
        .putExtra(EXTRA_SECTION, section)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    runCatching { startActivity(intent) }
}

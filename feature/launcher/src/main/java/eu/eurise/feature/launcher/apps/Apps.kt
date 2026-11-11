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
    All("Tutte", { EuriseIcons.grid(it) }, CalendarTint.Grafite),
    Money("Soldi", { EuriseIcons.euro(it) }, CalendarTint.Ottone),
    Tools("Strumenti", { EuriseIcons.compass(it) }, CalendarTint.Prugna),
    Media("Media", { EuriseIcons.image(it) }, CalendarTint.Terracotta),
    Work("Lavoro", { EuriseIcons.work(it) }, CalendarTint.BluNotte),
    Communication("Comunicazione", { EuriseIcons.chat(it) }, CalendarTint.Salvia),
    Browser("Browser", { EuriseIcons.browser(it) }, CalendarTint.Petrolio),
    Ai("AI", { EuriseIcons.spark(it) }, CalendarTint.Borgogna),
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
    /** Per le web app installate da un browser (vedi [WebApps]). */
    val shortcut: WebShortcut? = null,
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
        val stopWatching = WebApps.watch(context) { trySend(Unit) }
        trySend(Unit)
        awaitClose {
            context.unregisterReceiver(receiver)
            stopWatching()
        }
    }.conflate().map { load() }.flowOn(Dispatchers.IO)

    private fun load(): List<AppEntry> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val own = context.packageName
        val installed = pm.queryIntentActivities(launcherIntent, 0)
            .filter { it.activityInfo.packageName != own }
            .map { info ->
                val pkg = info.activityInfo.packageName
                val label = info.loadLabel(pm).toString()
                AppEntry(
                    key = "$pkg/${info.activityInfo.name}",
                    label = label,
                    packageName = pkg,
                    component = ComponentName(pkg, info.activityInfo.name),
                    category = categorize(pkg, label, info.activityInfo.applicationInfo),
                )
            }
        return (euriseSections(own) + installed + WebApps.load(context)).sortedBy { it.label.lowercase() }
    }

    /** Le sezioni dell'app Eurise compaiono come app a sé nelle loro categorie. */
    private fun euriseSections(own: String) = listOf(
        AppEntry("eurise:today", "Oggi", own, null, Category.Work, "today"),
        AppEntry("eurise:calendar", "Calendario", own, null, Category.Work, "calendar"),
        AppEntry("eurise:clock", "Orologio", own, null, Category.Tools, "clock"),
        AppEntry("eurise:drive", "File", own, null, Category.Tools, "drive"),
        AppEntry("eurise:scanner", "Scanner", own, null, Category.Tools, "scanner"),
        AppEntry("eurise:calculator", "Calcolatrice", own, null, Category.Tools, "calculator"),
        AppEntry("eurise:weather", "Meteo", own, null, Category.Tools, "weather"),
        AppEntry("eurise:qr", "Lettore QR", own, null, Category.Tools, "qr"),
        AppEntry("eurise:privacy", "Privacy", own, null, Category.Tools, "privacy"),
        AppEntry("eurise:ponte", "Telefono e computer", own, null, Category.Tools, "ponte"),
        AppEntry("eurise:notes", "Note", own, null, Category.Work, "notes"),
        AppEntry("eurise:tasks", "Attività", own, null, Category.Work, "tasks"),
    )

    /**
     * Assegna una categoria: prima le app riconosciute per pacchetto o per nome, poi la categoria che l'app
     * dichiara ad Android. Quello che resta si trova solo in "Tutte", finché non lo si sposta.
     */
    private fun categorize(pkg: String, label: String, app: ApplicationInfo): Category =
        classifyApp(pkg, label) ?: when (app.category) {
            ApplicationInfo.CATEGORY_SOCIAL -> Category.Communication
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> Category.Work
            ApplicationInfo.CATEGORY_IMAGE, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_AUDIO -> Category.Media
            ApplicationInfo.CATEGORY_MAPS -> Category.Tools
            else -> Category.Other
        }
}

/** Apre un'app o una sezione di Eurise. */
fun Context.launch(entry: AppEntry) {
    val intent = when {
        entry.euriseSection != null -> return openEurise(entry.euriseSection)
        entry.shortcut != null -> return WebApps.launch(this, entry.shortcut)
        entry.component != null ->
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(entry.component)
        else -> return
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { startActivity(intent) }
}

/** Prefisso del pacchetto della Galleria della suite (la versione di prova finisce in ".debug"). */
private const val SUITE_GALLERY = "eu.eurise.gallery"

/** La Galleria della suite, se è installata. */
fun List<AppEntry>.suiteGallery(): AppEntry? = firstOrNull { it.packageName.startsWith(SUITE_GALLERY) }

/** Servizi di ascolto, nell'ordine in cui compaiono nella bolla Musica. */
private val MusicServices = listOf(
    "com.spotify.music", "com.google.android.apps.youtube.music", "com.apple.android.music", "deezer.android.app", "com.amazon.mp3",
    "com.aspiro.tidal", "com.qobuz.music", "com.soundcloud.android", "com.github.libretube", "org.polymorphicshade.tubular", "org.schabi.newpipe",
)

/** I primi tre servizi di ascolto installati. */
fun musicServices(apps: List<AppEntry>): List<AppEntry> =
    MusicServices.mapNotNull { pkg -> apps.firstOrNull { it.component != null && it.packageName.startsWith(pkg) } }.take(3)

/** La prima app che apre i PDF. */
fun Context.pdfApp(apps: List<AppEntry>): AppEntry? {
    val known = packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW).setType("application/pdf"), 0).map { it.activityInfo.packageName }.toSet()
    return apps.firstOrNull { it.component != null && it.packageName in known }
}

private val OfficeApps = listOf("org.libreoffice", "com.collabora", "com.microsoft.office", "cn.wps", "com.onlyoffice", "com.google.android.apps.docs.editors")
private val DriveApps = listOf("com.infomaniak.drive", "foundation.e.drive", "com.nextcloud", "me.proton.android.drive", "com.google.android.apps.docs", "com.dropbox")

fun officeApp(apps: List<AppEntry>): AppEntry? =
    OfficeApps.firstNotNullOfOrNull { prefix -> apps.firstOrNull { it.packageName.startsWith(prefix) && !it.packageName.contains("outlook") } }

fun driveApp(apps: List<AppEntry>): AppEntry? =
    DriveApps.firstNotNullOfOrNull { prefix -> apps.firstOrNull { it.packageName == prefix || it.packageName.startsWith("$prefix.") && !it.packageName.contains("editors") } }

/** Le app capaci di mostrare le foto, per la scelta sul pulsante accanto all'ora. */
fun Context.galleryChoices(apps: List<AppEntry>): List<AppEntry> {
    val viewers = packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW).setType("image/*"), 0).map { it.activityInfo.packageName }
    val galleries = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY), 0).map { it.activityInfo.packageName }
    val known = (viewers + galleries).toSet()
    return apps.filter { it.component != null && (it.packageName in known || it.packageName.startsWith(SUITE_GALLERY)) }.distinctBy { it.packageName }
}

/** Le app fotocamera, per la scelta sul pulsante accanto all'ora. */
fun Context.cameraChoices(apps: List<AppEntry>): List<AppEntry> {
    val known = packageManager.queryIntentActivities(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), 0).map { it.activityInfo.packageName }.toSet()
    return apps.filter { it.component != null && it.packageName in known }.distinctBy { it.packageName }
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

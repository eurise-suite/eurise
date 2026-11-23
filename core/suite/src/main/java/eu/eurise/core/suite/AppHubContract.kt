package eu.eurise.core.suite

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * Le app separate che hanno servizi propri o un cestino proprio (Password, Galleria) si fanno vedere nell'app Eurise:
 * nel centro privacy (servizi con le 10 tacchette, accesi o spenti, contatti nel registro) e nel cestino unico. Il verso è
 * questo: l'app Eurise chiede, l'app risponde. Accendere un servizio vuole la spiegazione e spesso un collegamento, quindi
 * si fa nell'app ([ACTION_OPEN] con [EXTRA_PAGE]); spegnere si fa anche da qui, senza conferme.
 *
 * Fornitore `<pacchetto>.hub` con il permesso della suite nel manifesto e il controllo in `call` ([SuiteAccess.callerAllowed]).
 * L'app Eurise chiama solo fornitori della suite ([SuiteAccess.call]).
 */
object AppHubContract {
    fun authority(pkg: String) = "$pkg.hub"
    fun uri(pkg: String): Uri = Uri.parse("content://" + authority(pkg))

    fun passwordPackage(debug: Boolean) = PasswordContract.passwordPackage(debug)
    fun galleryPackage(debug: Boolean) = if (debug) "eu.eurise.gallery.debug" else "eu.eurise.gallery"

    /** I servizi dell'app: [KEY_IDS], [KEY_TITLES], [KEY_DETAILS], [KEY_SCORES], [KEY_WHERE], [KEY_ON]; registro in [KEY_LOG]. */
    const val METHOD_PRIVACY = "privacy"
    /** arg: id del servizio. Lo spegne subito. */
    const val METHOD_DISABLE = "disable"
    /** Il cestino: [KEY_IDS], [KEY_TITLES], [KEY_TIMES], [KEY_SIZES]; [KEY_LOCKED] e [KEY_COUNT] se l'app non può dire di più. */
    const val METHOD_TRASH = "trash"
    /** arg: id della cosa nel cestino. Risposta [KEY_OK]. */
    const val METHOD_RESTORE = "restore"
    const val METHOD_DELETE = "delete"
    /**
     * Gli id del cestino della Galleria che cominciano così sono originali messi da parte con «Sostituisci»
     * (`originale:<quando>:<indirizzo della foto>`): si eliminano con [METHOD_DELETE]; per rimetterli serve il permesso
     * di Android di riscrivere la foto, quindi si apre la foto nella Galleria ([originalPhoto]).
     */
    const val ORIGINAL_PREFIX = "originale:"

    /** L'indirizzo della foto di un originale messo da parte, dal suo id; null se l'id non è di un originale. */
    fun originalPhoto(id: String): Uri? = id.takeIf { it.startsWith(ORIGINAL_PREFIX) }
        ?.removePrefix(ORIGINAL_PREFIX)?.substringAfter(':', "")?.takeIf { it.startsWith("content://") }?.let(Uri::parse)

    const val KEY_IDS = "ids"
    const val KEY_TITLES = "titles"
    const val KEY_DETAILS = "details"
    const val KEY_SCORES = "scores"
    /** Nome di `Jurisdiction` del sistema di disegno (Phone, EU, CH, UK…). */
    const val KEY_WHERE = "where"
    const val KEY_ON = "on"
    const val KEY_TIMES = "times"
    const val KEY_SIZES = "sizes"
    const val KEY_LOCKED = "locked"
    const val KEY_COUNT = "count"
    const val KEY_OK = "ok"
    /** Nella richiesta del cestino: mandare anche gli originali messi da parte ([ORIGINAL_PREFIX]). */
    const val KEY_ORIGINALS = "originals"
    /** Il registro dei contatti come testo di [ServiceLog] (ultimi 30 giorni). */
    const val KEY_LOG = "log"

    /** Apre l'app sulla pagina [EXTRA_PAGE]: ingresso protetto dal permesso della suite. */
    const val ACTION_OPEN = "eu.eurise.action.OPEN_FROM_HUB"
    const val EXTRA_PAGE = "page"
    const val PAGE_PRIVACY = "privacy"
    const val PAGE_TRASH = "trash"

    /** Un servizio esterno di un'app, come lo vede il centro privacy. */
    data class Service(val id: String, val title: String, val detail: String, val score: Int, val where: String, val on: Boolean)

    /** Una cosa nel cestino di un'app; [at] è quando è stata tolta (0 se non si sa), [size] in byte (0 se non conta). */
    data class Item(val id: String, val title: String, val at: Long, val size: Long)

    /** Il cestino di un'app: [locked] vuol dire che l'app è chiusa (Password) e sa solo quante sono, [count]. */
    data class Trash(val items: List<Item>, val locked: Boolean, val count: Int)

    fun servicesBundle(services: List<Service>, log: String): Bundle = Bundle().apply {
        putStringArray(KEY_IDS, services.map { it.id }.toTypedArray())
        putStringArray(KEY_TITLES, services.map { it.title }.toTypedArray())
        putStringArray(KEY_DETAILS, services.map { it.detail }.toTypedArray())
        putIntArray(KEY_SCORES, services.map { it.score }.toIntArray())
        putStringArray(KEY_WHERE, services.map { it.where }.toTypedArray())
        putBooleanArray(KEY_ON, services.map { it.on }.toBooleanArray())
        putString(KEY_LOG, log)
    }

    fun trashBundle(trash: Trash): Bundle = Bundle().apply {
        putStringArray(KEY_IDS, trash.items.map { it.id }.toTypedArray())
        putStringArray(KEY_TITLES, trash.items.map { it.title }.toTypedArray())
        putLongArray(KEY_TIMES, trash.items.map { it.at }.toLongArray())
        putLongArray(KEY_SIZES, trash.items.map { it.size }.toLongArray())
        putBoolean(KEY_LOCKED, trash.locked)
        putInt(KEY_COUNT, trash.count)
    }

    /** I servizi di [pkg] e il loro registro; null se l'app non c'è, non è della suite o non risponde. */
    fun services(context: Context, pkg: String): Pair<List<Service>, String>? = runCatching {
        val b = SuiteAccess.call(context, uri(pkg), METHOD_PRIVACY, null, null) ?: return null
        val ids = b.getStringArray(KEY_IDS).orEmpty()
        val titles = b.getStringArray(KEY_TITLES).orEmpty()
        val details = b.getStringArray(KEY_DETAILS).orEmpty()
        val scores = b.getIntArray(KEY_SCORES) ?: IntArray(0)
        val where = b.getStringArray(KEY_WHERE).orEmpty()
        val on = b.getBooleanArray(KEY_ON) ?: BooleanArray(0)
        val list = ids.indices.filter { it < titles.size && it < scores.size && it < on.size }.map {
            Service(ids[it], titles[it], details.getOrElse(it) { "" }, scores[it].coerceIn(1, 10), where.getOrElse(it) { "" }, on[it])
        }
        list to b.getString(KEY_LOG).orEmpty()
    }.getOrNull()

    fun disable(context: Context, pkg: String, id: String) {
        runCatching { SuiteAccess.call(context, uri(pkg), METHOD_DISABLE, id, null) }
    }

    /**
     * Il cestino di [pkg]; null se l'app non c'è o non risponde. [withOriginals]: la Galleria aggiunge gli originali
     * messi da parte ([ORIGINAL_PREFIX]); chi non lo chiede (un'app Eurise più vecchia) non li riceve.
     */
    fun trash(context: Context, pkg: String, withOriginals: Boolean = false): Trash? = runCatching {
        val extras = if (withOriginals) Bundle().apply { putBoolean(KEY_ORIGINALS, true) } else null
        val b = SuiteAccess.call(context, uri(pkg), METHOD_TRASH, null, extras) ?: return null
        val ids = b.getStringArray(KEY_IDS).orEmpty()
        val titles = b.getStringArray(KEY_TITLES).orEmpty()
        val times = b.getLongArray(KEY_TIMES) ?: LongArray(0)
        val sizes = b.getLongArray(KEY_SIZES) ?: LongArray(0)
        val items = ids.indices.filter { it < titles.size }.map { Item(ids[it], titles[it], times.getOrElse(it) { 0L }, sizes.getOrElse(it) { 0L }) }
        Trash(items, b.getBoolean(KEY_LOCKED, false), b.getInt(KEY_COUNT, items.size))
    }.getOrNull()

    fun restore(context: Context, pkg: String, id: String): Boolean =
        runCatching { SuiteAccess.call(context, uri(pkg), METHOD_RESTORE, id, null)?.getBoolean(KEY_OK) == true }.getOrDefault(false)

    fun delete(context: Context, pkg: String, id: String): Boolean =
        runCatching { SuiteAccess.call(context, uri(pkg), METHOD_DELETE, id, null)?.getBoolean(KEY_OK) == true }.getOrDefault(false)

    /** L'intent che apre [pkg] su [page], o null se l'app non è della suite. */
    fun openIntent(context: Context, pkg: String, page: String): Intent? {
        if (!SuiteAccess.isSuitePackage(context, pkg)) return null
        val intent = Intent(ACTION_OPEN).setPackage(pkg).putExtra(EXTRA_PAGE, page).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val target = context.packageManager.resolveActivity(intent, 0)?.activityInfo ?: return null
        return intent.setComponent(ComponentName(target.packageName, target.name))
    }
}

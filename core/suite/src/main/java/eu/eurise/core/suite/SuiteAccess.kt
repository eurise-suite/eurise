package eu.eurise.core.suite

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import java.io.File

/**
 * Chi è della suite, in un posto solo (revisione delle porte d'ingresso, 18 novembre 2026, docs/SICUREZZA.md).
 *
 * - Chi **chiama** un nostro fornitore o servizio deve avere il permesso di firma della **nostra** variante: `SUITE` nelle
 *   versioni ufficiali, `SUITE_DEBUG` in quelle di prova. Mai tutti e due: nelle versioni ufficiali nessuna app della suite
 *   dichiara `SUITE_DEBUG`, quindi un'app qualunque potrebbe dichiararlo lei come permesso «normale» e ottenerlo senza
 *   chiedere nulla (e viceversa con `SUITE` sul telefono con le sole app di prova).
 * - A chi **mandiamo** dati (un fornitore, un servizio, un'attività di un'altra app della suite) controlliamo prima che sia
 *   davvero della suite: se l'app destinataria non è installata, un'app estranea può prendere lo stesso nome di pacchetto
 *   o lo stesso indirizzo di fornitore e ricevere ciò che le diamo. Il permesso di firma lo possono avere solo le nostre.
 * - I file che arrivano da fuori (Condividi, Apri con) non possono puntare ai dati privati dell'app stessa.
 */
object SuiteAccess {
    const val SUITE = "eu.eurise.permission.SUITE"
    const val SUITE_DEBUG = "eu.eurise.permission.SUITE_DEBUG"

    fun isDebug(context: Context) = context.packageName.endsWith(".debug")

    /** Il permesso della suite per la variante di questa app. */
    fun permission(context: Context) = if (isDebug(context)) SUITE_DEBUG else SUITE

    /** Vero se chi ci chiama ora (fornitore, servizio, ricevitore) è un'app della suite della stessa variante. */
    fun callerAllowed(context: Context): Boolean =
        context.checkCallingPermission(permission(context)) == PackageManager.PERMISSION_GRANTED

    /** Come [callerAllowed], ma ferma la chiamata. */
    fun enforceCaller(context: Context) {
        if (!callerAllowed(context)) throw SecurityException("Eurise: solo le app della suite")
    }

    /** Vero se [pkg] è questa app o un'app della suite (ha il nostro permesso di firma). */
    fun isSuitePackage(context: Context, pkg: String?): Boolean {
        if (pkg.isNullOrEmpty()) return false
        if (pkg == context.packageName) return true
        return runCatching { context.packageManager.checkPermission(permission(context), pkg) }
            .getOrDefault(PackageManager.PERMISSION_DENIED) == PackageManager.PERMISSION_GRANTED
    }

    /** Vero se il fornitore con [authority] appartiene a un'app della suite. */
    fun providerTrusted(context: Context, authority: String?): Boolean {
        if (authority.isNullOrEmpty()) return false
        val info = runCatching { context.packageManager.resolveContentProvider(authority, 0) }.getOrNull() ?: return false
        return isSuitePackage(context, info.packageName)
    }

    /** `ContentResolver.call` solo verso un fornitore della suite; null altrimenti (come se l'app mancasse). */
    fun call(context: Context, uri: Uri, method: String, arg: String?, extras: Bundle?): Bundle? {
        if (!providerTrusted(context, uri.authority)) return null
        return context.contentResolver.call(uri, method, arg, extras)
    }

    /** L'intent se va (per pacchetto o componente) a un'app della suite; null altrimenti. */
    fun trusted(context: Context, intent: Intent): Intent? =
        intent.takeIf { isSuitePackage(context, it.component?.packageName ?: it.`package`) }

    /**
     * Falso se [uri], arrivato da un'altra app, punta ai dati di questa app: `content://` di un nostro fornitore o
     * `file://` dentro le nostre cartelle private. Così «Condividi» o «Apri con» non diventano un modo per farci allegare,
     * inviare o mostrare i nostri file (account, chiavi, database).
     */
    fun isForeignUri(context: Context, uri: Uri?): Boolean {
        if (uri == null) return false
        return when (uri.scheme?.lowercase()) {
            "content" -> uri.authority?.let { it !in ownAuthorities(context) } ?: false
            "file" -> {
                val path = uri.path?.let { p -> runCatching { File(p).canonicalPath }.getOrNull() } ?: return false
                privateDirs(context).none { dir -> path == dir || path.startsWith("$dir/") }
            }
            else -> true
        }
    }

    private fun ownAuthorities(context: Context): Set<String> = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PROVIDERS).providers.orEmpty()
            .flatMap { it.authority.orEmpty().split(';') }.filter { it.isNotEmpty() }.toSet()
    }.getOrDefault(emptySet())

    private fun privateDirs(context: Context): List<String> {
        val dirs = mutableListOf<File?>(
            File(context.applicationInfo.dataDir),
            context.createDeviceProtectedStorageContext().dataDir,
            context.getExternalFilesDir(null)?.parentFile,
            context.externalCacheDir?.parentFile,
            context.obbDir,
        )
        return dirs.filterNotNull().mapNotNull { runCatching { it.canonicalPath }.getOrNull() }.distinct()
    }
}

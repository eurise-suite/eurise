package eu.eurise.keyboard

import android.content.Context
import android.content.pm.PackageManager
import helium314.keyboard.latin.BuildConfig

/**
 * Chi è della suite, per la Tastiera (progetto Gradle a parte, senza `core:suite`; stessa regola di
 * `eu.eurise.core.suite.SuiteAccess`, docs/SICUREZZA.md, porte d'ingresso).
 *
 * Solo il permesso di firma della nostra variante; e prima di mandare a un'altra app ciò che si scrive (testo da
 * tradurre, ricerche) si controlla che abbia quel permesso: se l'app della suite manca, un'app estranea con lo stesso
 * nome di pacchetto non riceve nulla.
 */
object SuiteTrust {
    private val debug = BuildConfig.BUILD_TYPE != "release" && BuildConfig.BUILD_TYPE != "nouserlib"

    fun permission() = if (debug) "eu.eurise.permission.SUITE_DEBUG" else "eu.eurise.permission.SUITE"

    /** Chi ci chiama ora (fornitore, ricevitore) è della suite. */
    fun callerAllowed(context: Context) = context.checkCallingPermission(permission()) == PackageManager.PERMISSION_GRANTED

    /** [pkg] è installato ed è un'app della suite. */
    fun isSuite(context: Context, pkg: String?): Boolean {
        if (pkg.isNullOrEmpty()) return false
        return runCatching { context.packageManager.checkPermission(permission(), pkg) }
            .getOrDefault(PackageManager.PERMISSION_DENIED) == PackageManager.PERMISSION_GRANTED
    }
}

package eu.eurise.office.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import eu.eurise.core.suite.FilesContract
import eu.eurise.office.BuildConfig

/** Le altre app della suite che Office apre: Calendario, Note, Scanner nell'app Eurise, «Apri da…» di Eurise File. */
object Suite {
    private const val MAIN = "eu.eurise.hub.MainActivity"
    private const val EXTRA_SECTION = "eu.eurise.extra.SECTION"
    val hub: String get() = BuildConfig.HUB_PACKAGE

    fun installed(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(hub, 0) }.isSuccess

    /** Apre una sezione dell'app Eurise («calendar», «notes», «scanner»). */
    fun section(context: Context, section: String): Boolean = try {
        context.startActivity(
            Intent().setClassName(hub, MAIN).putExtra(EXTRA_SECTION, section)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    fun openFrom(extensions: Collection<String>, from: String): Intent = FilesContract.openFrom(hub, extensions, from)
}

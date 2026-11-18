package eu.eurise.office.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.eurise.core.suite.FilesContract
import eu.eurise.core.suite.PrivacyContract
import eu.eurise.core.suite.PrivacyService
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

    fun openFrom(extensions: Collection<String>, from: String, start: String? = null): Intent =
        FilesContract.openFrom(hub, extensions, from, start)

    /** Un luogo di Eurise File: [id] vuoto è il telefono, altrimenti l'account cloud ([kind]: Murena, Nextcloud…). */
    data class Place(val id: String, val label: String, val kind: String)

    /** I cloud collegati in Eurise File (senza il telefono); vuoto se l'app Eurise non c'è o non li offre ancora. */
    fun clouds(context: Context): List<Place> = runCatching {
        val b = eu.eurise.core.suite.SuiteAccess.call(context, Uri.parse("content://" + FilesContract.placesAuthority(hub)), FilesContract.METHOD_PLACES, null, null)
        val ids = b?.getStringArray(FilesContract.KEY_IDS).orEmpty()
        val labels = b?.getStringArray(FilesContract.KEY_LABELS).orEmpty()
        val kinds = b?.getStringArray(FilesContract.KEY_KINDS).orEmpty()
        ids.indices.filter { ids[it].isNotEmpty() && it < labels.size }.map { Place(ids[it], labels[it], kinds.getOrElse(it) { "" }) }
    }.getOrDefault(emptyList())

    /**
     * Avvisa il centro privacy dell'app Eurise ([PrivacyContract]: acceso, contatto nel registro). Senza l'app Eurise, o
     * con una versione che non conosce il servizio, non succede nulla.
     */
    fun privacy(context: Context, method: String, service: PrivacyService) {
        runCatching {
            eu.eurise.core.suite.SuiteAccess.call(context, Uri.parse("content://" + PrivacyContract.authority(hub)), method, service.name, null)
        }
    }
}

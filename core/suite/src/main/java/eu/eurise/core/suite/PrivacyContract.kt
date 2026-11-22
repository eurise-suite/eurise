package eu.eurise.core.suite

import android.content.Context
import android.net.Uri

/**
 * I servizi esterni che un'altra app della suite contatta da sé (per ora Eurise Mappe: ricerca, note su OpenStreetMap, prezzi dei carburanti)
 * passano comunque dal centro privacy dell'app Eurise: lì si vedono, si accendono e si spengono, e ogni contatto finisce
 * nel registro. Solo app con la stessa firma (permesso della suite, controllato dal fornitore). Mappe è in Java: qui
 * solo nomi.
 */
object PrivacyContract {
    fun authority(hubPackage: String) = "$hubPackage.privacy"

    /** Lo stato del servizio [arg] (nome di [PrivacyService]): risposta [KEY_ON]. */
    const val METHOD_STATUS = "status"
    /** Acceso dalla persona dopo la spiegazione nell'app che lo usa. */
    const val METHOD_ENABLE = "enable"
    /** Spento, senza conferme. */
    const val METHOD_DISABLE = "disable"
    /** Un contatto appena fatto: va nel registro del centro privacy. */
    const val METHOD_LOG = "log"

    const val KEY_ON = "on"

    /** I servizi che le altre app possono chiedere qui: nessun altro interruttore si tocca da fuori. */
    val SHARED = setOf(PrivacyService.MapSearch, PrivacyService.MapReports, PrivacyService.FuelPrices, PrivacyService.PublicTransport,
        PrivacyService.Milestones, PrivacyService.Lyrics, PrivacyService.OfficeEngine,
        PrivacyService.ReadModels, PrivacyService.VoiceModels)

    /**
     * Uno scaricamento chiesto dalla persona con «Scarica» (modelli, lingue, voci, motore): il servizio si accende e il
     * contatto finisce nel registro del centro privacy dell'app Eurise, come per il motore di Office. Senza l'app Eurise, o
     * con una versione che non conosce il servizio, non succede nulla. Lavora su un thread a parte (disco e Binder).
     */
    fun downloadStarted(context: Context, service: PrivacyService) {
        val app = context.applicationContext
        Thread {
            runCatching {
                val hub = SecretsContract.hubPackage(SuiteAccess.isDebug(app))
                if (app.packageName == hub) {
                    PrivacyCenter.init(app)
                    PrivacyCenter.set(service, true)
                    PrivacyCenter.log(service)
                } else {
                    val uri = Uri.parse("content://" + authority(hub))
                    SuiteAccess.call(app, uri, METHOD_ENABLE, service.name, null)
                    SuiteAccess.call(app, uri, METHOD_LOG, service.name, null)
                }
            }
        }.start()
    }
}

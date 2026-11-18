package eu.eurise.core.studio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * L'accordo tra Email, Messaggi e la Chat, dove sta lo Studio (archivio cifrato e coda). Email e Messaggi offrono alla
 * Chat ogni messaggio arrivato; la Chat tiene solo quelli dei pazienti (o con un codice di verifica) e dice di chi sono.
 * Tutto resta sul telefono: il fornitore della Chat risponde solo alle app firmate con la stessa chiave (permesso della
 * suite). Con lo Studio spento la Chat non risponde nulla e non conserva nulla.
 */
object StudioContract {
    const val METHOD_OFFER = "offer"
    const val METHOD_PATIENTS = "patients"
    const val ACTION_OPEN = "eu.eurise.action.STUDIO"
    const val ACTIVITY = "eu.eurise.chat.ChatActivity"

    const val EXTRA_ADDRESS = "address"
    const val EXTRA_TEXT = "text"
    const val EXTRA_REF = "ref"
    const val EXTRA_TIME = "time"
    const val EXTRA_RESULT = "result"
    const val EXTRA_PATIENT = "patient"
    const val EXTRA_NAME = "name"
    const val EXTRA_NEED = "need"
    const val EXTRA_EMAILS = "emails"
    const val EXTRA_PHONES = "phones"

    const val RESULT_QUEUED = "queued"
    const val RESULT_VERIFIED = "verified"
    const val RESULT_IGNORED = "ignored"

    fun chatPackage(debug: Boolean) = if (debug) "eu.eurise.chat.debug" else "eu.eurise.chat"
    fun authority(chatPackage: String) = "$chatPackage.studio"
    private fun uri(context: Context) = Uri.parse("content://" + authority(chatPackage(context.packageName.endsWith(".debug"))))

    /** Che cosa la Chat ha fatto del messaggio. */
    data class Answer(val result: String, val patientId: String?, val patientName: String?, val need: Need?)

    /**
     * Offre un messaggio arrivato da [address] su [channel] (fuori dal thread principale). Null se la Chat non c'è o lo
     * Studio è spento.
     */
    fun offer(context: Context, channel: Channel, address: String, text: String, ref: String, time: Long): Answer? = runCatching {
        val extras = Bundle().apply {
            putString(EXTRA_ADDRESS, address); putString(EXTRA_TEXT, text); putString(EXTRA_REF, ref); putLong(EXTRA_TIME, time)
        }
        val b = eu.eurise.core.suite.SuiteAccess.call(context, uri(context), METHOD_OFFER, channel.name, extras) ?: return null
        Answer(
            b.getString(EXTRA_RESULT) ?: RESULT_IGNORED, b.getString(EXTRA_PATIENT), b.getString(EXTRA_NAME),
            b.getString(EXTRA_NEED)?.let { runCatching { Need.valueOf(it) }.getOrNull() },
        )
    }.getOrNull()

    /** Indirizzi email e numeri dei pazienti, per riconoscerli negli elenchi. Vuoti con lo Studio spento. */
    data class Known(val emails: Set<String>, val phones: Set<String>) {
        fun email(address: String) = Addresses.email(address) in emails
        fun phone(address: String) = Addresses.phone(address).let { it.length >= 6 && it in phones }
    }

    fun known(context: Context): Known = runCatching {
        val b = eu.eurise.core.suite.SuiteAccess.call(context, uri(context), METHOD_PATIENTS, null, null)
        Known(
            b?.getStringArray(EXTRA_EMAILS)?.map(Addresses::email)?.toSet().orEmpty(),
            b?.getStringArray(EXTRA_PHONES)?.map(Addresses::phone)?.toSet().orEmpty(),
        )
    }.getOrDefault(Known(emptySet(), emptySet()))

    /** Apre lo Studio nella Chat (sulla scheda di [patientId], se c'è), o null se la Chat non c'è. */
    fun openIntent(context: Context, patientId: String? = null): Intent? {
        val pkg = chatPackage(context.packageName.endsWith(".debug"))
        val intent = Intent(ACTION_OPEN).setComponent(ComponentName(pkg, ACTIVITY)).putExtra(EXTRA_PATIENT, patientId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent.takeIf { context.packageManager.resolveActivity(it, 0) != null }
    }
}

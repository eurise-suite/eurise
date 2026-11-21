package eu.eurise.password.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.suite.AppHubContract as C
import eu.eurise.core.suite.ServiceLog
import eu.eurise.core.suite.SuiteAccess
import eu.eurise.password.R
import eu.eurise.password.ui.PwdPrivacy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

/** I nomi dei servizi di Password nel centro privacy e nel registro ([ServiceLog]). */
object PwdHub {
    const val SYNC = "sync"
    const val BREACH = "breach"
    const val ADDY = "alias_addy"
    const val SIMPLELOGIN = "alias_simplelogin"

    /** Il nome nel registro del servizio di alias [p] (kMail non ha ancora una riga sua nel centro privacy). */
    fun aliasId(p: AliasProvider) = when (p) {
        AliasProvider.Addy -> ADDY
        AliasProvider.SimpleLogin -> SIMPLELOGIN
        else -> "alias_" + p.name.lowercase()
    }

    /** Cresce quando un servizio si spegne da fuori (centro privacy): le schermate aperte rileggono le scelte. */
    val changes = MutableStateFlow(0)
}

/**
 * Password nell'app Eurise ([C]): i servizi che possono far uscire dati (sincronizzazione, controllo delle violazioni,
 * alias) con le tacchette e lo stato, il registro dei contatti, lo spegnimento senza conferme; il cestino della
 * cassaforte nel cestino unico. Solo app della suite (permesso nel manifesto e controllo qui). Il cestino si vede e si
 * tocca solo a cassaforte aperta; chiusa, l'app Eurise sa solo quante voci ci sono e offre «Sblocca».
 */
class HubProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (!SuiteAccess.callerAllowed(context)) throw SecurityException("Eurise: solo le app della suite")
        val repo = VaultRepository.get(context)
        return when (method) {
            C.METHOD_PRIVACY -> {
                val alias = AliasStore(context).provider
                val services = listOf(
                    C.Service(PwdHub.SYNC, context.getString(R.string.pwd_pv_sync), context.getString(R.string.pwd_pv_sync_d), PwdPrivacy.SYNC, "EU", repo.syncEnabled),
                    C.Service(PwdHub.BREACH, context.getString(R.string.pwd_pv_breach), context.getString(R.string.pwd_pv_breach_d), PwdPrivacy.BREACH, "UK", BreachStore(context).enabled),
                    C.Service(PwdHub.ADDY, context.getString(R.string.pwd_pv_addy), context.getString(R.string.pwd_pv_alias_d), PwdPrivacy.ALIAS,
                        PwdPrivacy.aliasWhere(AliasProvider.Addy).name, alias == AliasProvider.Addy),
                    C.Service(PwdHub.SIMPLELOGIN, context.getString(R.string.pwd_pv_simplelogin), context.getString(R.string.pwd_pv_alias_d), PwdPrivacy.ALIAS,
                        PwdPrivacy.aliasWhere(AliasProvider.SimpleLogin).name, alias == AliasProvider.SimpleLogin),
                )
                C.servicesBundle(services, ServiceLog.text(context))
            }
            C.METHOD_DISABLE -> {
                when (arg) {
                    PwdHub.SYNC -> repo.disableSync()
                    PwdHub.BREACH -> BreachStore(context).enabled = false
                    PwdHub.ADDY, PwdHub.SIMPLELOGIN -> {
                        val store = AliasStore(context)
                        val which = if (arg == PwdHub.ADDY) AliasProvider.Addy else AliasProvider.SimpleLogin
                        if (store.provider == which) store.provider = AliasProvider.None
                    }
                    else -> return null
                }
                PwdHub.changes.value++
                Bundle().apply { putBoolean(C.KEY_OK, true) }
            }
            C.METHOD_TRASH -> {
                repo.expireIfDue()
                val open = repo.state.value is VaultState.Open
                val binned = if (open) runBlocking { repo.binned() } else null
                val trash = if (binned == null) C.Trash(emptyList(), locked = true, count = repo.binCount)
                else C.Trash(binned.map { (e, at) -> C.Item(e.uuid, e.title.ifBlank { e.username }, at, 0L) }, locked = false, count = binned.size)
                C.trashBundle(trash)
            }
            C.METHOD_RESTORE, C.METHOD_DELETE -> {
                val id = arg ?: return null
                repo.expireIfDue()
                if (repo.state.value !is VaultState.Open) return Bundle().apply { putBoolean(C.KEY_OK, false) }
                repo.leave()
                val ok = runBlocking { if (method == C.METHOD_RESTORE) repo.restoreEntry(id) else repo.deleteForever(id) }
                Bundle().apply { putBoolean(C.KEY_OK, ok) }
            }
            else -> null
        }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

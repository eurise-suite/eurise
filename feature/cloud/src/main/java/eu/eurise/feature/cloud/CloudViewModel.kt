package eu.eurise.feature.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.core.cloud.CloudAccount
import eu.eurise.core.cloud.CloudAccounts
import eu.eurise.core.cloud.CloudKind
import eu.eurise.core.cloud.CloudPrefs
import eu.eurise.core.cloud.CloudSync
import eu.eurise.core.cloud.DavException
import eu.eurise.core.cloud.DavQuota
import eu.eurise.core.cloud.NextcloudLogin
import eu.eurise.core.cloud.SyncEvent
import eu.eurise.core.cloud.WebDav
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A che punto è il collegamento di un account. */
sealed interface Connecting {
    /** Aspettiamo l'approvazione nella pagina aperta nel browser. */
    data class Browser(val kind: CloudKind, val url: String) : Connecting
    /** Stiamo provando indirizzo e password. */
    data object Checking : Connecting
    /** Non è andata: [reason] dice perché, [code] è la risposta del server (se c'è stata). */
    data class Failed(val reason: Reason, val code: Int? = null) : Connecting
}

enum class Reason { Password, Address, Network, Timeout, Plan }

data class CloudUiState(
    val quotas: Map<String, DavQuota> = emptyMap(),
    val connecting: Connecting? = null,
)

class CloudViewModel(private val cloud: CloudSync) : ViewModel() {

    val accounts: StateFlow<List<CloudAccount>> = cloud.accounts.accounts
    val settings: StateFlow<CloudPrefs> = cloud.settings
    val events: StateFlow<List<SyncEvent>> = cloud.events
    val running: StateFlow<Boolean> = cloud.running

    private val _ui = MutableStateFlow(CloudUiState())
    val ui: StateFlow<CloudUiState> = _ui.asStateFlow()

    private var login: Job? = null

    init {
        refreshQuotas()
    }

    fun refreshQuotas() {
        viewModelScope.launch {
            for (account in accounts.value) {
                val quota = runCatching { cloud.accounts.dav(account.id)?.quota() }.getOrNull() ?: continue
                _ui.update { it.copy(quotas = it.quotas + (account.id to quota)) }
            }
        }
    }

    /** Accesso dal browser per Murena o per un server Nextcloud. */
    fun connectNextcloud(kind: CloudKind, server: String) {
        login?.cancel()
        login = viewModelScope.launch {
            val flow = NextcloudLogin()
            val start = try {
                flow.start(if (kind == CloudKind.Murena) NextcloudLogin.MURENA_SERVER else server)
            } catch (e: Exception) {
                fail(if (e is DavException) Reason.Address else Reason.Network)
                return@launch
            }
            _ui.update { it.copy(connecting = Connecting.Browser(kind, start.loginUrl)) }
            val result = flow.await(start)
            if (result == null) {
                fail(Reason.Timeout)
                return@launch
            }
            val dav = CloudAccounts.nextcloudDav(result.server, result.loginName)
            val name = if (kind == CloudKind.Murena && '@' !in result.loginName) "${result.loginName}@murena.io" else result.loginName
            added(cloud.accounts.add(kind, name, result.loginName, dav, result.appPassword))
        }
    }

    /** kDrive: email, password per le app e numero del kDrive. */
    fun connectKDrive(email: String, appPassword: String, driveId: String) {
        val id = driveId.filter { it.isDigit() }
        if (id.isEmpty()) {
            fail(Reason.Address)
            return
        }
        connectWithPassword(
            CloudKind.KDrive, email.trim(), email.trim(),
            listOf(CloudAccounts.kDriveDav(id), CloudAccounts.kDriveDavAlt(id)), appPassword,
        )
    }

    fun connectWebDav(url: String, user: String, password: String) {
        val address = NextcloudLogin.normalizeServer(url).let { if (it.endsWith("/")) it else "$it/" }
        connectWithPassword(CloudKind.WebDav, user.trim().ifEmpty { address }, user.trim(), listOf(address), password)
    }

    /** Prova gli indirizzi [davs] uno dopo l'altro e collega il primo che accetta nome e password. */
    private fun connectWithPassword(kind: CloudKind, name: String, user: String, davs: List<String>, password: String) {
        login?.cancel()
        _ui.update { it.copy(connecting = Connecting.Checking) }
        login = viewModelScope.launch {
            var failure: Connecting.Failed? = null
            for (dav in davs) {
                failure = try {
                    WebDav(dav, user, password).quota()
                    added(cloud.accounts.add(kind, name, user, dav, password))
                    return@launch
                } catch (e: DavException) {
                    val reason = when (e.code) {
                        401 -> Reason.Password
                        403 -> if (kind == CloudKind.KDrive) Reason.Plan else Reason.Password
                        else -> Reason.Address
                    }
                    Connecting.Failed(reason, e.code)
                } catch (e: IllegalArgumentException) {
                    Connecting.Failed(Reason.Address)
                } catch (e: Exception) {
                    failure ?: Connecting.Failed(Reason.Network)
                }
                // Una password sbagliata resta sbagliata anche con l'altro indirizzo: inutile insistere.
                if (failure.code == 401) break
            }
            _ui.update { it.copy(connecting = failure ?: Connecting.Failed(Reason.Address)) }
        }
    }

    private fun added(id: String) {
        // Il primo account collegato riceve le note.
        if (cloud.accounts.get(cloud.settings.value.notesAccount) == null) cloud.update { it.copy(notesAccount = id) }
        _ui.update { it.copy(connecting = null) }
        refreshQuotas()
        syncNow()
    }

    private fun fail(reason: Reason) = _ui.update { it.copy(connecting = Connecting.Failed(reason)) }

    fun cancelConnecting() {
        login?.cancel()
        _ui.update { it.copy(connecting = null) }
    }

    fun remove(id: String) {
        cloud.accounts.remove(id)
        if (cloud.settings.value.notesAccount == id) {
            cloud.update { it.copy(notesAccount = cloud.accounts.accounts.value.firstOrNull()?.id) }
        }
        _ui.update { it.copy(quotas = it.quotas - id) }
    }

    fun setNotesAccount(id: String?) = cloud.update { it.copy(notesAccount = id) }.also { syncNow() }

    fun setNotesEnabled(on: Boolean) = cloud.update { it.copy(notesEnabled = on) }.also { if (on) syncNow() }

    fun setTasksEnabled(on: Boolean) = cloud.update { it.copy(tasksEnabled = on) }.also { if (on) syncNow() }

    fun setWifiOnly(on: Boolean) = cloud.update { it.copy(wifiOnly = on) }

    fun syncNow() {
        viewModelScope.launch { cloud.runNow() }
    }
}

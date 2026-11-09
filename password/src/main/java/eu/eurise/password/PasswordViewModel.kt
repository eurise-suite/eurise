package eu.eurise.password

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import eu.eurise.password.data.AliasCheck
import eu.eurise.password.data.AliasProvider
import eu.eurise.password.data.AliasStore
import eu.eurise.password.data.BiometricVault
import eu.eurise.password.data.BreachStore
import eu.eurise.password.data.PendingLogin
import eu.eurise.password.data.SecureClipboard
import eu.eurise.password.data.VaultRepository
import eu.eurise.password.data.VaultState
import eu.eurise.password.kdbx.Vault
import eu.eurise.password.kdbx.VaultEntry
import eu.eurise.password.data.askBiometric
import eu.eurise.password.kdbx.EntryDraft
import eu.eurise.password.kdbx.ImportCounts
import eu.eurise.password.kdbx.KdbxException
import eu.eurise.password.kdbx.Kdbx
import eu.eurise.password.logic.ImportItem
import eu.eurise.password.logic.ImportKind
import eu.eurise.password.logic.ImportOutcome
import eu.eurise.password.logic.Importer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Le tappe dell'importazione mostrate come finestre. */
sealed interface ImportStage {
    data class Preview(val ready: ImportOutcome.Ready) : ImportStage
    data class Done(val counts: ImportCounts) : ImportStage
    data object Encrypted : ImportStage
    class Keepass(val bytes: ByteArray, val failed: Boolean = false) : ImportStage
    data object Unreadable : ImportStage
}

class PasswordViewModel(app: Application) : AndroidViewModel(app) {
    val repo = VaultRepository.get(app)
    val state = repo.state
    val sync = repo.sync

    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val _waitingBrowser = MutableStateFlow(false)
    val waitingBrowser = _waitingBrowser.asStateFlow()

    val bio = BiometricVault(app)
    private val _bioOn = MutableStateFlow(bio.enabled)
    val bioOn = _bioOn.asStateFlow()
    val aliases = AliasStore(app)
    private val _alias = MutableStateFlow(aliases.provider)
    val alias = _alias.asStateFlow()

    init { viewModelScope.launch { repo.load() } }

    /** Accende lo sblocco con l'impronta: chiude la chiave corrente nel chip di sicurezza, dopo la conferma del dito. */
    fun enableBio(activity: android.content.Context) {
        val cipher = runCatching { bio.encryptCipher() }.getOrNull() ?: run { _error.value = text(R.string.pwd_bio_unavailable); return }
        askBiometric(activity, text(R.string.pwd_bio_enable_title), text(R.string.pwd_cancel), cipher) { done ->
            val key = repo.currentKey() ?: return@askBiometric
            bio.store(done, key)
            key.fill(0)
            _bioOn.value = true
        }
    }

    fun disableBio() { bio.disable(); _bioOn.value = false }

    fun unlockWithBio(activity: android.content.Context) {
        val cipher = bio.decryptCipher()
        if (cipher == null) {
            disableBio()
            _error.value = text(R.string.pwd_bio_reset)
            return
        }
        askBiometric(activity, text(R.string.pwd_bio_title), text(R.string.pwd_cancel), cipher) { done ->
            val key = runCatching { bio.read(done) }.getOrNull() ?: return@askBiometric
            work { repo.unlockWithKey(key) }
        }
    }

    /** Conferma con il dito prima di usare una carta già disponibile; senza impronta attiva basta il tocco sulla carta. */
    fun confirmBio(activity: android.content.Context, then: () -> Unit) {
        val cipher = if (bio.enabled) bio.decryptCipher() else null
        if (cipher == null) { then(); return }
        askBiometric(activity, text(R.string.pwd_card_fill), text(R.string.pwd_cancel), cipher) { then() }
    }

    fun selectAlias(provider: AliasProvider) { aliases.provider = provider; _alias.value = provider }

    /** Verifica e salva la chiave del servizio; [done] riceve l'eventuale messaggio d'errore. */
    fun connectAlias(provider: AliasProvider, secret: String, done: (String?) -> Unit) = viewModelScope.launch {
        when (aliases.connect(provider, secret)) {
            AliasCheck.Ok -> { selectAlias(provider); done(null) }
            AliasCheck.Rejected -> done(text(R.string.pwd_alias_bad_key))
            AliasCheck.Offline -> done(text(R.string.pwd_alias_offline))
        }
    }

    private val _importing = MutableStateFlow<ImportStage?>(null)
    val importing = _importing.asStateFlow()

    /** Legge il file e mostra cosa contiene; nella cassaforte non entra nulla finché non si conferma. */
    fun readImport(uri: Uri) {
        importedFile = uri
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { Importer.read(it) } ?: ImportOutcome.Unknown
                } catch (e: java.io.IOException) {
                    ImportOutcome.Unknown
                } catch (e: RuntimeException) {
                    ImportOutcome.Unknown
                } catch (e: OutOfMemoryError) {
                    ImportOutcome.Unknown
                }
            }
            _importing.value = when (outcome) {
                is ImportOutcome.Ready -> if (outcome.items.isEmpty()) ImportStage.Unreadable else ImportStage.Preview(outcome)
                ImportOutcome.Encrypted -> ImportStage.Encrypted
                is ImportOutcome.Keepass -> ImportStage.Keepass(outcome.bytes)
                ImportOutcome.Unknown -> ImportStage.Unreadable
            }
        }
    }

    fun confirmImport(ready: ImportOutcome.Ready) {
        _importing.value = null
        work {
            val counts = repo.importItems(ready.items.map { it.draft })
            if (counts != null) _importing.value = ImportStage.Done(counts)
        }
    }

    fun closeImport() { _importing.value = null }

    private var importedFile: Uri? = null

    /** Un QR con un solo codice apre la voce da controllare; quelli di Google Authenticator ne hanno molti. */
    fun showImport(ready: ImportOutcome.Ready) { importedFile = null; _importing.value = ImportStage.Preview(ready) }

    /** Toglie il file esportato, che contiene tutto in chiaro; falso se l'app da cui viene non lo permette. */
    fun deleteImportedFile(): Boolean {
        val uri = importedFile ?: return false
        importedFile = null
        return runCatching { android.provider.DocumentsContract.deleteDocument(getApplication<Application>().contentResolver, uri) }.getOrDefault(false)
    }

    val canDeleteImportedFile: Boolean get() = importedFile?.let { android.provider.DocumentsContract.isDocumentUri(getApplication(), it) } == true

    /** Legge un altro file KeePass con la sua frase e prepara le voci, password e passkey comprese. */
    fun readKeepass(bytes: ByteArray, password: String) {
        viewModelScope.launch {
            _importing.value = try {
                val entries = withContext(Dispatchers.Default) { Vault(Kdbx.read(bytes, Kdbx.compositeKey(password)).document).entries() }
                ImportStage.Preview(ImportOutcome.Ready(entries.map { e ->
                    val kind = when {
                        e.card != null -> ImportKind.Card
                        e.password.isEmpty() && e.otp != null -> ImportKind.Code
                        e.password.isEmpty() && e.passkey == null -> ImportKind.Note
                        else -> ImportKind.Login
                    }
                    ImportItem(kind, EntryDraft(e.title, e.username, e.password, e.url, e.notes, e.otp, e.card, e.passkey))
                }, 0, 0))
            } catch (e: KdbxException) {
                ImportStage.Keepass(bytes, failed = true)
            } catch (e: OutOfMemoryError) {
                ImportStage.Unreadable
            }
        }
    }

    val breach = BreachStore(app)
    private val _breachOn = MutableStateFlow(breach.enabled)
    val breachOn = _breachOn.asStateFlow()
    private val _breaches = MutableStateFlow(breach.results())
    val breaches = _breaches.asStateFlow()
    private val _breachLast = MutableStateFlow(breach.lastCheck)
    val breachLast = _breachLast.asStateFlow()
    private val _breachFailed = MutableStateFlow(false)
    val breachFailed = _breachFailed.asStateFlow()

    fun setBreach(on: Boolean) {
        breach.enabled = on
        _breachOn.value = on
        _breaches.value = breach.results()
        _breachLast.value = breach.lastCheck
        if (on) checkBreaches()
    }

    fun checkBreaches() {
        val open = repo.state.value as? VaultState.Open ?: return
        viewModelScope.launch {
            val ok = breach.check(open.entries)
            _breachFailed.value = !ok
            _breaches.value = breach.results()
            _breachLast.value = breach.lastCheck
        }
    }

    fun checkBreachesIfDue() { if (breach.due) checkBreaches() }

    /** Dati in attesa dal servizio di compilazione: stanno solo in memoria, mai nell'Intent. */
    fun saveLogin(pending: PendingLogin, existing: VaultEntry?) = viewModelScope.launch {
        if (existing != null) {
            repo.saveEntry(EntryDraft(existing.title, existing.username, pending.password, existing.url, existing.notes, existing.otp), existing.uuid)
        } else {
            val url = pending.webDomain?.let { "https://$it" } ?: (Vault.APP_SCHEME + pending.packageName)
            repo.saveEntry(EntryDraft(pending.webDomain ?: pending.packageName, pending.username, pending.password, url, "", null), null)
        }
    }

    fun linkApp(uuid: String, packageName: String) = viewModelScope.launch { repo.linkApp(uuid, packageName) }

    fun clearError() { _error.value = null }

    private fun text(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)

    private fun work(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: KdbxException.WrongPassword) {
                _error.value = text(R.string.pwd_err_wrong)
            } catch (e: KdbxException.Unsupported) {
                _error.value = text(R.string.pwd_err_unsupported, e.message.orEmpty().removePrefix("Non supportato: "))
            } catch (e: KdbxException.Corrupt) {
                _error.value = text(R.string.pwd_err_corrupt)
            } catch (e: OutOfMemoryError) {
                _error.value = text(R.string.pwd_err_memory)
            } catch (e: java.io.IOException) {
                _error.value = text(R.string.pwd_err_generic)
            } catch (e: IllegalStateException) {
                _error.value = text(R.string.pwd_err_generic)
            } finally {
                _busy.value = false
            }
        }
    }

    fun create(password: String) = work { repo.create(password) }
    fun unlock(password: String) = work { repo.unlock(password) }

    fun import(uri: Uri, password: String) = work {
        val bytes = withContext(Dispatchers.IO) {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } ?: throw java.io.IOException()
        repo.importFile(bytes, password)
    }

    fun lock() = repo.lock()
    fun save(draft: EntryDraft, uuid: String?) = viewModelScope.launch { repo.saveEntry(draft, uuid) }
    fun delete(uuid: String) = viewModelScope.launch { repo.deleteEntry(uuid) }
    fun markUsed(uuid: String) = viewModelScope.launch { repo.markUsed(uuid) }
    fun syncNow() = viewModelScope.launch { repo.syncNow() }
    fun disableSync() = repo.disableSync()

    /** Vero se c'è già un account su cui sincronizzare: allora non serve chiedere dove. */
    val hasSyncAccount: Boolean get() = repo.syncAccountName != null

    /** Accende la sincronizzazione; se manca l'account apre prima l'accesso nel browser ([server] nullo = Murena). */
    fun enableSync(server: String?, openBrowser: (String) -> Unit) {
        if (hasSyncAccount) {
            viewModelScope.launch { repo.enableSync() }
            return
        }
        login(server, openBrowser) { repo.enableSync() }
    }

    /** Telefono nuovo: accesso al cloud e download del file già presente. */
    fun fetchFromCloud(server: String?, openBrowser: (String) -> Unit) = login(server, openBrowser) {
        if (!repo.adoptRemote()) _error.value = text(R.string.pwd_err_no_remote)
    }

    private fun login(server: String?, openBrowser: (String) -> Unit, then: suspend () -> Unit) = viewModelScope.launch {
        _error.value = null
        _waitingBrowser.value = true
        try {
            val start = repo.startCloudLogin(server)
            openBrowser(start.loginUrl)
            if (repo.finishCloudLogin(start, server)) then() else _error.value = text(R.string.pwd_err_login)
        } catch (e: java.io.IOException) {
            _error.value = text(R.string.pwd_err_login)
        } catch (e: eu.eurise.core.cloud.DavException) {
            _error.value = text(R.string.pwd_err_login)
        } catch (e: IllegalArgumentException) {
            _error.value = text(R.string.pwd_err_login) // indirizzo scritto male
        } catch (e: org.json.JSONException) {
            _error.value = text(R.string.pwd_err_login) // all'indirizzo non risponde un Nextcloud
        } finally {
            _waitingBrowser.value = false
        }
    }

    fun copy(value: String) {
        val app = getApplication<Application>()
        SecureClipboard.copy(app, text(R.string.pwd_copied_label), value)
        Toast.makeText(app, R.string.pwd_copied, Toast.LENGTH_SHORT).show()
    }
}

package eu.eurise.password.autofill

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.suite.PasswordContract as C
import eu.eurise.password.data.VaultRepository
import eu.eurise.password.data.VaultState
import eu.eurise.password.kdbx.EntryDraft
import kotlinx.coroutines.runBlocking

/**
 * Gli accessi ai siti per il Browser della suite ([C.loginsAuthority]): il Browser li chiede quando una pagina ha un
 * modulo di accesso e li compila da sé, senza cercare né copiare. Solo app con la firma della suite (controllo qui:
 * `call` non guarda il permesso del manifesto) e solo a cassaforte aperta; ogni richiesta rimanda di due minuti la
 * chiusura automatica, così mentre navighi non si richiude a ogni pagina.
 */
class LoginsProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (PERMISSIONS.none { context.checkCallingPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            throw SecurityException("Eurise: solo le app della suite")
        }
        val repo = VaultRepository.get(context)
        repo.expireIfDue()
        val open = repo.state.value as? VaultState.Open
        val out = Bundle().apply { putBoolean(C.KEY_OPEN, open != null) }
        if (open == null) return out
        repo.leave()
        when (method) {
            C.METHOD_STATUS -> Unit
            C.METHOD_FETCH -> {
                val domain = arg?.let(Matching::host).orEmpty()
                if (domain.isEmpty()) return out
                val found = open.entries.filter { e ->
                    if (e.card != null || e.password.isEmpty()) return@filter false
                    val h = Matching.host(e.url)
                    h.isNotEmpty() && (h == domain || h.endsWith(".$domain") || Matching.domainMatches(e.url, domain))
                }.sortedByDescending { it.lastUsed }.take(MAX)
                out.putStringArray(C.KEY_GUID, found.map { it.uuid }.toTypedArray())
                out.putStringArray(C.KEY_USERNAME, found.map { it.username }.toTypedArray())
                out.putStringArray(C.KEY_PASSWORD, found.map { it.password }.toTypedArray())
                out.putStringArray(C.KEY_ORIGIN, found.map { "https://" + Matching.host(it.url) }.toTypedArray())
            }
            C.METHOD_SAVE -> {
                val origin = extras?.getString(C.KEY_ORIGIN).orEmpty()
                val username = extras?.getString(C.KEY_USERNAME).orEmpty()
                val password = extras?.getString(C.KEY_PASSWORD).orEmpty()
                if (origin.isEmpty() || password.isEmpty()) return out
                val guid = extras?.getString(C.KEY_GUID)
                val old = open.entries.firstOrNull { it.uuid == guid }
                    ?: open.entries.firstOrNull { it.username == username && Matching.host(it.url) == Matching.host(origin) && it.card == null }
                val draft = if (old != null) {
                    EntryDraft(old.title, username.ifEmpty { old.username }, password, old.url, old.notes, old.otp)
                } else {
                    EntryDraft(Matching.host(origin), username, password, origin, "", null)
                }
                val id = runBlocking { repo.saveEntry(draft, old?.uuid) }
                out.putBoolean(C.KEY_OK, id != null)
            }
            C.METHOD_USED -> arg?.let { runBlocking { repo.markUsed(it) } }
            else -> return null
        }
        return out
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    private companion object {
        val PERMISSIONS = listOf("eu.eurise.permission.SUITE", "eu.eurise.permission.SUITE_DEBUG")
        const val MAX = 12
    }
}

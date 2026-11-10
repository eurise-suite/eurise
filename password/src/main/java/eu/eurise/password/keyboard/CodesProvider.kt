package eu.eurise.password.keyboard

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import eu.eurise.password.data.VaultRepository
import eu.eurise.password.data.VaultState
import eu.eurise.password.logic.Totp

/**
 * Codici 2FA per Eurise Tastiera. Lo leggono solo le app Eurise firmate con la stessa chiave (permesso della suite).
 * Risponde soltanto se la cassaforte è già aperta e soltanto con le voci collegate all'app in cui si sta scrivendo:
 * nessuna password esce da qui, solo il codice del momento.
 *
 * `content://<pacchetto>.codes/otp?package=<app>` → righe (title, code, remaining); con la cassaforte chiusa
 * una sola riga con `state = locked`.
 */
class CodesProvider : ContentProvider() {

    override fun onCreate() = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val cursor = MatrixCursor(arrayOf(COL_STATE, COL_TITLE, COL_CODE, COL_REMAINING))
        val target = uri.getQueryParameter("package")?.takeIf { it.isNotBlank() } ?: return cursor
        val repo = VaultRepository.get(context ?: return cursor)
        repo.expireIfDue()
        val open = repo.state.value as? VaultState.Open
        if (open == null) {
            cursor.addRow(arrayOf(STATE_LOCKED, null, null, null))
            return cursor
        }
        val now = System.currentTimeMillis()
        open.entries.filter { target in it.apps }.sortedByDescending { it.lastUsed }.forEach { e ->
            val totp = Totp.parse(e.otp) ?: return@forEach
            cursor.addRow(arrayOf(STATE_OPEN, e.title, totp.code(now), totp.remaining(now)))
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val COL_STATE = "state"
        const val COL_TITLE = "title"
        const val COL_CODE = "code"
        const val COL_REMAINING = "remaining"
        const val STATE_OPEN = "open"
        const val STATE_LOCKED = "locked"
    }
}

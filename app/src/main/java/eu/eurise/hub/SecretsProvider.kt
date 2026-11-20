package eu.eurise.hub

import eu.eurise.core.suite.SuiteAccess
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.suite.SecretsContract
import eu.eurise.core.cloud.lock.SuiteSecrets

/**
 * I segreti affidati dagli altri APK della suite (SecretsContract), sigillati nel Keystore ([SuiteSecrets]) e portati nel
 * backup cifrato della suite. Risponde solo a chi ha il permesso della suite
 * (stessa firma): il controllo è qui, perché `call` non passa dai permessi dichiarati nel manifesto.
 */
class SecretsProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (!allowed(context)) throw SecurityException("Eurise: solo le app della suite")
        val name = arg?.takeIf { it.isNotBlank() } ?: return null
        return when (method) {
            SecretsContract.METHOD_PUT -> {
                val value = extras?.getString(SecretsContract.EXTRA_VALUE) ?: return null
                SuiteSecrets.put(context, name, value)
                Bundle()
            }
            SecretsContract.METHOD_GET -> Bundle().apply { putString(SecretsContract.EXTRA_VALUE, SuiteSecrets.get(context, name)) }
            SecretsContract.METHOD_REMOVE -> { SuiteSecrets.remove(context, name); Bundle() }
            else -> null
        }
    }

    private fun allowed(context: Context): Boolean = SuiteAccess.callerAllowed(context)

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}

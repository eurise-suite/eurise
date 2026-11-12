package eu.eurise.hub

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.suite.SecretsContract

/**
 * I segreti affidati dagli altri APK della suite (SecretsContract), tenuti nelle preferenze «eurise_secrets»: sono
 * nei dati privati dell'app e finiscono nel backup cifrato della suite. Risponde solo a chi ha il permesso della suite
 * (stessa firma): il controllo è qui, perché `call` non passa dai permessi dichiarati nel manifesto.
 */
class SecretsProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (!allowed(context)) throw SecurityException("Eurise: solo le app della suite")
        val name = arg?.takeIf { it.isNotBlank() } ?: return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return when (method) {
            SecretsContract.METHOD_PUT -> {
                val value = extras?.getString(SecretsContract.EXTRA_VALUE) ?: return null
                prefs.edit().putString(name, value).apply()
                Bundle()
            }
            SecretsContract.METHOD_GET -> Bundle().apply { putString(SecretsContract.EXTRA_VALUE, prefs.getString(name, null)) }
            SecretsContract.METHOD_REMOVE -> { prefs.edit().remove(name).apply(); Bundle() }
            else -> null
        }
    }

    private fun allowed(context: Context): Boolean = PERMISSIONS.any {
        context.checkCallingPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    private companion object {
        const val PREFS = "eurise_secrets"
        val PERMISSIONS = listOf("eu.eurise.permission.SUITE", "eu.eurise.permission.SUITE_DEBUG")
    }
}

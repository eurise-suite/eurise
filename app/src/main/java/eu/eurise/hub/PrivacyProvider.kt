package eu.eurise.hub

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.suite.PrivacyCenter
import eu.eurise.core.suite.PrivacyContract as C
import eu.eurise.core.suite.PrivacyService

/**
 * Centro privacy per le altre app della suite ([C]): stato, accensione, spegnimento e registro dei servizi in
 * [C.SHARED]. Solo app con la stessa firma: il controllo è qui, `call` non guarda il manifesto.
 */
class PrivacyProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (PERMISSIONS.none { context.checkCallingPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            throw SecurityException("Eurise: solo le app della suite")
        }
        val service = PrivacyService.entries.firstOrNull { it.name == arg }?.takeIf { it in C.SHARED } ?: return null
        PrivacyCenter.init(context)
        when (method) {
            C.METHOD_ENABLE -> PrivacyCenter.set(service, true)
            C.METHOD_DISABLE -> PrivacyCenter.set(service, false)
            C.METHOD_LOG -> if (PrivacyCenter.allowed(service)) PrivacyCenter.log(service)
            C.METHOD_STATUS -> Unit
            else -> return null
        }
        return Bundle().apply { putBoolean(C.KEY_ON, PrivacyCenter.allowed(service)) }
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    private companion object {
        val PERMISSIONS = listOf("eu.eurise.permission.SUITE", "eu.eurise.permission.SUITE_DEBUG")
    }
}

package eu.eurise.hub

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import eu.eurise.core.data.weather.RouteWeather
import eu.eurise.core.suite.PrivacyCenter
import eu.eurise.core.suite.PrivacyService
import eu.eurise.core.suite.RouteWeatherContract as C

/**
 * Meteo sul percorso per Eurise Mappe ([C]): acceso e spento dal centro privacy o da Mappe (dopo la spiegazione), la
 * richiesta passa da [RouteWeather]. Solo per le app della suite: il controllo è qui, `call` non guarda il manifesto.
 * Mappe lo chiama da un suo thread, mai dal principale: la rete qui è lecita.
 */
class RouteWeatherProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (PERMISSIONS.none { context.checkCallingPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            throw SecurityException("Eurise: solo le app della suite")
        }
        PrivacyCenter.init(context)
        when (method) {
            C.METHOD_ENABLE -> PrivacyCenter.set(PrivacyService.RouteWeather, true)
            C.METHOD_DISABLE -> PrivacyCenter.set(PrivacyService.RouteWeather, false)
            C.METHOD_STATUS -> Unit
            C.METHOD_ROUTE -> {
                val lat = extras?.getDoubleArray(C.KEY_LAT) ?: return null
                val lon = extras.getDoubleArray(C.KEY_LON) ?: return null
                val time = extras.getLongArray(C.KEY_TIME) ?: return null
                if (lat.size > C.MAX_POINTS) return null
                val on = PrivacyCenter.allowed(PrivacyService.RouteWeather)
                val list = if (on) RouteWeather.fetch(lat, lon, time) else null
                return Bundle().apply {
                    putBoolean(C.KEY_ON, on)
                    if (list != null) {
                        putIntArray(C.KEY_CODE, list.map { it.code }.toIntArray())
                        putFloatArray(C.KEY_TEMP, list.map { it.temp }.toFloatArray())
                        putIntArray(C.KEY_RAIN, list.map { it.rain }.toIntArray())
                        putFloatArray(C.KEY_GUSTS, list.map { it.gusts }.toFloatArray())
                    }
                }
            }
            else -> return null
        }
        return Bundle().apply { putBoolean(C.KEY_ON, PrivacyCenter.allowed(PrivacyService.RouteWeather)) }
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

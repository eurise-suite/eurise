package eu.eurise.camera.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Nomi e cartelle dei file nuovi in MediaStore: IMG_20261122_103015_123.jpg, VID_….mp4. */
object MediaOut {
    fun fileName(prefix: String, extension: String, now: Long): String =
        prefix + "_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.ROOT).format(Date(now)) + "." + extension

    fun photo(dest: String?, now: Long = System.currentTimeMillis()) = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName("IMG", "jpg", now))
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Destinations.relativePath(dest))
        put(MediaStore.MediaColumns.DATE_TAKEN, now)
    }

    /** L'affiancata prima/dopo: PRIMADOPO_….jpg nella stessa cartella. */
    fun pair(dest: String?, now: Long = System.currentTimeMillis()) = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName("PRIMADOPO", "jpg", now))
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Destinations.relativePath(dest))
        put(MediaStore.MediaColumns.DATE_TAKEN, now)
    }

    /** La posizione nei dati della foto (android.media.ExifInterface non ha setGpsInfo). */
    fun writeGps(exif: android.media.ExifInterface, where: Location) {
        exif.setAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE, dms(where.latitude))
        exif.setAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE_REF, if (where.latitude >= 0) "N" else "S")
        exif.setAttribute(android.media.ExifInterface.TAG_GPS_LONGITUDE, dms(where.longitude))
        exif.setAttribute(android.media.ExifInterface.TAG_GPS_LONGITUDE_REF, if (where.longitude >= 0) "E" else "W")
    }

    /** 45.4642 → «45/1,27/1,51120/1000» (gradi, minuti, secondi). */
    fun dms(value: Double): String {
        var v = kotlin.math.abs(value)
        val d = v.toInt(); v = (v - d) * 60
        val m = v.toInt(); v = (v - m) * 60
        return "$d/1,$m/1,${Math.round(v * 1000)}/1000"
    }

    fun video(dest: String?, now: Long = System.currentTimeMillis()) = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName("VID", "mp4", now))
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Destinations.relativePath(dest))
        put(MediaStore.MediaColumns.DATE_TAKEN, now)
    }
}

/**
 * La posizione da scrivere nelle foto, solo con l'interruttore acceso (spento di base). Niente Play Services: i
 * fornitori del sistema. All'apertura si chiede un punto fresco, poi allo scatto si usa il più recente: lo scatto non
 * aspetta mai il GPS.
 */
object PhotoLocation {
    private const val MAX_AGE_MS = 10 * 60 * 1000L
    @Volatile private var fresh: Location? = null

    fun permitted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Chiede al sistema un punto nuovo, senza aspettarlo. */
    @SuppressLint("MissingPermission")
    fun warm(context: Context) {
        if (!permitted(context)) return
        val lm = context.getSystemService(LocationManager::class.java) ?: return
        val provider = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull {
            runCatching { lm.isProviderEnabled(it) }.getOrDefault(false)
        } ?: return
        runCatching {
            lm.getCurrentLocation(provider, CancellationSignal(), ContextCompat.getMainExecutor(context)) { loc -> if (loc != null) fresh = loc }
        }
    }

    /** Il punto più recente (al massimo di dieci minuti fa), o null. */
    @SuppressLint("MissingPermission")
    fun latest(context: Context): Location? {
        if (!permitted(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val known = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        val best = (known + listOfNotNull(fresh)).maxByOrNull { it.time } ?: return null
        return best.takeIf { System.currentTimeMillis() - it.time <= MAX_AGE_MS }
    }
}

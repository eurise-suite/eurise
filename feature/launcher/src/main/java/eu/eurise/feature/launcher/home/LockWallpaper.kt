package eu.eurise.feature.launcher.home

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.eurise.feature.launcher.settings.SettingsStore
import eu.eurise.feature.launcher.weather.Sky
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.MonthDay
import java.util.concurrent.TimeUnit

/**
 * Lo sfondo della Scrivania anche sulla schermata di blocco (tavola SfoBlocco). Android mostra lì un'immagine fissa:
 * Eurise disegna la scena e la consegna al sistema solo quando cambia (momento, meteo, festa), dalla Scrivania quando è
 * aperta e da un lavoro di WorkManager ogni ora, senza servizi. Solo FLAG_LOCK: lo sfondo della Scrivania è suo.
 */
object LockWallpaper {
    enum class Mode { Off, Same, Other }

    /** [other]: la scena scelta per il blocco («season:Winter», «fest:Christmas», null = automatico) o [PHOTO]. */
    data class Options(val mode: Mode = Mode.Off, val dim: Boolean = true, val follow: Boolean = true, val other: String? = null)

    const val PHOTO = "photo"
    private const val PREFS = "eurise_lock_wallpaper"
    private const val WORK = "lock_wallpaper"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): Options = prefs(context).let { p ->
        Options(
            Mode.entries.getOrElse(p.getInt("mode", 0)) { Mode.Off },
            p.getBoolean("dim", true), p.getBoolean("follow", true), p.getString("other", null),
        )
    }

    fun save(context: Context, o: Options) {
        prefs(context).edit().putInt("mode", o.mode.ordinal).putBoolean("dim", o.dim).putBoolean("follow", o.follow)
            .putString("other", o.other).remove("hash").apply()
    }

    /** Ciò che la Scrivania sa e il lavoro in background no: meteo del giorno e date personali. */
    fun noteInputs(context: Context, sky: Sky?, personal: PersonalDays) {
        prefs(context).edit().putString("sky", sky?.name).putString("birthday", personal.birthday?.toString())
            .putString("anniversary", personal.anniversary?.toString()).apply()
    }

    /** La scena del blocco adesso, senza il tema scelto per la Scrivania. */
    fun scene(context: Context, o: Options = load(context)): Scene {
        val p = prefs(context)
        val sky = p.getString("sky", null)?.let { n -> Sky.entries.firstOrNull { it.name == n } }
        val personal = PersonalDays(
            p.getString("birthday", null)?.let { runCatching { MonthDay.parse(it) }.getOrNull() },
            p.getString("anniversary", null)?.let { runCatching { MonthDay.parse(it) }.getOrNull() },
        )
        val base = SettingsStore(context).settings.value.wallpaper
        val options = if (o.mode == Mode.Other && o.other != PHOTO) base.copy(fixed = o.other) else base
        return sceneFor(LocalDateTime.now(), options, sky, personal)
    }

    /** Ridisegna se serve: spento, foto o «non seguire» si disegnano solo con [force]. */
    suspend fun refresh(context: Context, force: Boolean = false): Boolean = withContext(Dispatchers.Default) {
        val o = load(context)
        if (o.mode == Mode.Off || (o.mode == Mode.Other && o.other == PHOTO)) return@withContext true
        if (!force && !o.follow) return@withContext true
        val scene = scene(context, o)
        val (w, h) = screen(context)
        val hash = "${scene.hashCode()}:${o.dim}:$w:$h"
        if (!force && prefs(context).getString("hash", null) == hash) return@withContext true
        val ok = runCatching { setLock(context, render(scene, w, h, o.dim)) }.getOrDefault(false)
        if (ok) prefs(context).edit().putString("hash", hash).putBoolean("applied", true).apply()
        ok
    }

    /** Una foto dell'utente, ritagliata alla misura dello schermo. Non cambia più finché non se ne sceglie un'altra. */
    suspend fun setPhoto(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val (w, h) = screen(context)
            val src = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                    val k = maxOf(w.toFloat() / info.size.width, h.toFloat() / info.size.height).coerceAtMost(1f)
                    d.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it) }
            }
            val k = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
            val scaled = Bitmap.createScaledBitmap(src, (src.width * k).toInt().coerceAtLeast(w), (src.height * k).toInt().coerceAtLeast(h), true)
            val crop = Bitmap.createBitmap(scaled, (scaled.width - w) / 2, (scaled.height - h) / 2, w, h)
            val final = if (load(context).dim) darken(crop) else crop
            setLock(context, final).also { if (it) prefs(context).edit().putBoolean("applied", true).remove("hash").apply() }
        }.getOrDefault(false)
    }

    /** Torna lo sfondo di sistema sul blocco, se l'avevamo cambiato noi. */
    fun clear(context: Context) {
        if (!prefs(context).getBoolean("applied", false)) return
        runCatching { WallpaperManager.getInstance(context).clear(WallpaperManager.FLAG_LOCK) }
        prefs(context).edit().putBoolean("applied", false).remove("hash").apply()
    }

    /** Il lavoro orario esiste solo se la scena del blocco deve seguire momento, meteo e feste. */
    fun schedule(context: Context) {
        val o = load(context)
        val wm = WorkManager.getInstance(context)
        if (o.follow && (o.mode == Mode.Same || (o.mode == Mode.Other && o.other != PHOTO))) {
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<LockWallpaperWorker>(1, TimeUnit.HOURS).build())
        } else {
            wm.cancelUniqueWork(WORK)
        }
    }

    fun render(scene: Scene, w: Int, h: Int, dim: Boolean): Bitmap {
        val image = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(image), Size(w.toFloat(), h.toFloat())) {
            drawScene(scene)
            if (dim) drawRect(Color.Black.copy(alpha = DIM))
        }
        return image.asAndroidBitmap()
    }

    private const val DIM = 0.22f

    private fun darken(b: Bitmap): Bitmap {
        val out = b.copy(Bitmap.Config.ARGB_8888, true)
        android.graphics.Canvas(out).drawColor(android.graphics.Color.argb((DIM * 255).toInt(), 0, 0, 0))
        return out
    }

    private fun setLock(context: Context, bitmap: Bitmap): Boolean {
        val wm = WallpaperManager.getInstance(context)
        if (!wm.isWallpaperSupported || !wm.isSetWallpaperAllowed) return false
        return wm.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK) != 0
    }

    private fun screen(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(WindowManager::class.java)
        val (w, h) = if (Build.VERSION.SDK_INT >= 30) wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        else DisplayMetrics().also { @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
        // Sempre in verticale: la schermata di blocco del telefono lo è.
        return minOf(w, h) to maxOf(w, h)
    }
}

class LockWallpaperWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        LockWallpaper.refresh(applicationContext)
        return Result.success()
    }
}

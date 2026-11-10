package eu.eurise.feature.launcher.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import eu.eurise.core.designsystem.icon.EuriseIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** L'immagine di un'icona: il simbolo monocromatico dell'app (da colorare) oppure l'icona originale. */
sealed interface IconImage {
    data class Mono(val bitmap: ImageBitmap) : IconImage
    data class Full(val bitmap: ImageBitmap) : IconImage
    /** Simbolo della suite per le app di sistema che non hanno un simbolo monocromatico. */
    data class Glyph(val vector: ImageVector) : IconImage
}

/**
 * Carica le icone a tema. Da Android 13 le app possono fornire un simbolo monocromatico: il launcher lo
 * colora con la tinta della categoria su un tondo blu notte. Se manca, si usa l'icona originale nel tondo.
 */
object IconLoader {
    private val cache = LruCache<String, IconImage>(300)

    suspend fun load(context: Context, entry: AppEntry, px: Int): IconImage? = withContext(Dispatchers.IO) {
        cache.get(entry.key)?.let { return@withContext it }
        val drawable = when {
            entry.shortcut != null -> WebApps.icon(context, entry.shortcut)
            entry.component != null -> runCatching { context.packageManager.getActivityIcon(entry.component) }.getOrNull()
            else -> null
        } ?: return@withContext null
        val image = officialMono(drawable, px)
            ?: SystemGlyphs.find(entry)?.takeIf { entry.shortcut == null }?.let { IconImage.Glyph(it) }
            ?: silhouette(drawable, px)
            ?: IconImage.Full(drawable.toBitmap(px, px, Bitmap.Config.ARGB_8888).asImageBitmap())
        cache.put(entry.key, image)
        image
    }

    /** Il simbolo monocromatico fornito dall'app (Android 13+). */
    private fun officialMono(drawable: Drawable, px: Int): IconImage? {
        if (Build.VERSION.SDK_INT < 33 || drawable !is AdaptiveIconDrawable) return null
        val mono = drawable.monochrome ?: return null
        return IconImage.Mono(cropCenter(mono.toBitmap(px * 3 / 2, px * 3 / 2, Bitmap.Config.ARGB_8888)).asImageBitmap())
    }

    /**
     * Per le app senza simbolo monocromatico: se il primo piano dell'icona adattiva è un disegno su fondo
     * trasparente (e non un riquadro pieno), se ne usa la sagoma, colorata come le altre.
     */
    private fun silhouette(drawable: Drawable, px: Int): IconImage? {
        if (drawable !is AdaptiveIconDrawable) return null
        val fg = drawable.foreground ?: return null
        val bitmap = cropCenter(fg.toBitmap(px * 3 / 2, px * 3 / 2, Bitmap.Config.ARGB_8888))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val coverage = pixels.count { (it ushr 24) > 128 }.toFloat() / pixels.size
        if (coverage < 0.04f || coverage > 0.42f) return null
        return IconImage.Mono(bitmap.extractAlpha().asImageBitmap())
    }

    /** Le icone adattive stanno in un'area di 108 dp di cui si vedono i 72 centrali. */
    private fun cropCenter(full: Bitmap): Bitmap {
        val inset = full.width / 6
        return Bitmap.createBitmap(full, inset, inset, full.width - 2 * inset, full.height - 2 * inset)
    }
}

/** Simboli della suite per le app di sistema di /e/OS e Android, riconosciute per pacchetto o per nome. */
private object SystemGlyphs {
    private val byPackage: List<Pair<String, () -> ImageVector>> = listOf(
        "com.android.dialer" to { EuriseIcons.phone(1.9f) }, "com.google.android.dialer" to { EuriseIcons.phone(1.9f) },
        "org.fossify.phone" to { EuriseIcons.phone(1.9f) },
        "foundation.e.message" to { EuriseIcons.message(1.9f) }, "com.android.messaging" to { EuriseIcons.message(1.9f) },
        "com.google.android.apps.messaging" to { EuriseIcons.message(1.9f) }, "org.fossify.messages" to { EuriseIcons.message(1.9f) },
        "com.android.contacts" to { EuriseIcons.person(1.9f) }, "org.fossify.contacts" to { EuriseIcons.person(1.9f) },
        "foundation.e.mail" to { EuriseIcons.mail(1.9f) },
        "foundation.e.browser" to { EuriseIcons.globe(1.9f) }, "org.lineageos.jelly" to { EuriseIcons.globe(1.9f) },
        "foundation.e.camera" to { EuriseIcons.camera(1.9f) }, "org.lineageos.aperture" to { EuriseIcons.camera(1.9f) },
        "com.android.camera" to { EuriseIcons.camera(1.9f) },
        "eu.eurise.gallery" to { EuriseIcons.image(1.9f) }, "foundation.e.gallery" to { EuriseIcons.image(1.9f) }, "org.lineageos.glimpse" to { EuriseIcons.image(1.9f) },
        "com.android.gallery3d" to { EuriseIcons.image(1.9f) },
        "com.android.settings" to { EuriseIcons.gear(1.9f) },
        "com.android.deskclock" to { EuriseIcons.clock(1.9f) }, "org.fossify.clock" to { EuriseIcons.clock(1.9f) },
        "com.android.calculator2" to { EuriseIcons.calculator(1.9f) }, "org.lineageos.calculator" to { EuriseIcons.calculator(1.9f) },
        "org.lineageos.eleven" to { EuriseIcons.music(1.9f) }, "foundation.e.music" to { EuriseIcons.music(1.9f) },
        "org.lineageos.recorder" to { EuriseIcons.mic(1.9f) },
        "foundation.e.calendar" to { EuriseIcons.calendar(1.9f) }, "org.lineageos.etar" to { EuriseIcons.calendar(1.9f) },
        "foundation.e.notes" to { EuriseIcons.note(1.9f) }, "foundation.e.tasks" to { EuriseIcons.task(1.9f) },
        "foundation.e.advancedprivacy" to { EuriseIcons.shield(1.9f) }, "foundation.e.privacycentral" to { EuriseIcons.shield(1.9f) },
        "foundation.e.apps" to { EuriseIcons.bag(1.9f) }, "org.fdroid" to { EuriseIcons.bag(1.9f) },
        "com.android.documentsui" to { EuriseIcons.folder(1.9f) }, "foundation.e.drive" to { EuriseIcons.folder(1.9f) },
        "app.organicmaps" to { EuriseIcons.map(1.9f) }, "app.comaps" to { EuriseIcons.map(1.9f) }, "foundation.e.maps" to { EuriseIcons.map(1.9f) },
        "foundation.e.weather" to { EuriseIcons.sun(1.9f) },
    )

    /** Parole del nome dell'app, per le varianti di sistema con un pacchetto diverso. */
    private val byLabel: List<Pair<String, () -> ImageVector>> = listOf(
        "telefono" to { EuriseIcons.phone(1.9f) }, "phone" to { EuriseIcons.phone(1.9f) },
        "messag" to { EuriseIcons.message(1.9f) }, "contatti" to { EuriseIcons.person(1.9f) }, "contacts" to { EuriseIcons.person(1.9f) },
        "fotocamera" to { EuriseIcons.camera(1.9f) }, "camera" to { EuriseIcons.camera(1.9f) },
        "galleria" to { EuriseIcons.image(1.9f) }, "gallery" to { EuriseIcons.image(1.9f) },
        "impostazioni" to { EuriseIcons.gear(1.9f) }, "settings" to { EuriseIcons.gear(1.9f) },
        "orologio" to { EuriseIcons.clock(1.9f) }, "calcolatrice" to { EuriseIcons.calculator(1.9f) },
        "registratore" to { EuriseIcons.mic(1.9f) }, "browser" to { EuriseIcons.globe(1.9f) },
        "meteo" to { EuriseIcons.sun(1.9f) }, "mappe" to { EuriseIcons.map(1.9f) }, "musica" to { EuriseIcons.music(1.9f) },
        "file" to { EuriseIcons.folder(1.9f) }, "app lounge" to { EuriseIcons.bag(1.9f) },
    )

    fun find(entry: AppEntry): ImageVector? {
        byPackage.firstOrNull { entry.packageName.startsWith(it.first) }?.let { return it.second() }
        val label = entry.label.lowercase()
        return byLabel.firstOrNull { label.contains(it.first) }?.second?.invoke()
    }
}

/** Vero quando i simboli delle icone prendono colori vari invece del colore della categoria. */
val LocalVariedIcons = androidx.compose.runtime.compositionLocalOf { false }

/** Vero quando il simbolo prende il colore dell'icona originale dell'app, portato alle tinte della suite. */
val LocalAppIconColors = androidx.compose.runtime.compositionLocalOf { false }

/** Colore del simbolo secondo la categoria della barra in cui sta l'app (fornito dalla Scrivania). */
val LocalCategoryTint = androidx.compose.runtime.compositionLocalOf<(AppEntry) -> Color?> { { null } }

/** Fondo dei tondi delle icone: blu notte di giorno, grafite di sera. */
val IconBackgroundLight = Color(0xFF1F2A44)
val IconBackgroundDark = Color(0xFF1A1E25)

/**
 * Icona a tema nel tondo della suite, con il numero di notifiche in alto a destra se [badge] è maggiore di zero.
 */
@Composable
fun AppIcon(entry: AppEntry, size: Dp, dark: Boolean, badge: Int = 0, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    var image by remember(entry.key) { mutableStateOf<IconImage?>(null) }
    LaunchedEffect(entry.key) { image = IconLoader.load(context, entry, px) }
    val fromApp = LocalAppIconColors.current
    var appTint by remember(entry.key) { mutableStateOf(AppColors.cached(entry)) }
    if (fromApp && entry.euriseSection == null) {
        LaunchedEffect(entry.key) { if (appTint == null) appTint = AppColors.suiteTint(context, entry) }
    }
    val tint = if (fromApp && appTint != null) {
        appTint!!
    } else if (LocalVariedIcons.current) {
        eu.eurise.feature.launcher.settings.IconPalette[Math.floorMod(entry.packageName.hashCode() + entry.label.hashCode(), eu.eurise.feature.launcher.settings.IconPalette.size)]
    } else {
        LocalCategoryTint.current(entry) ?: entry.category.tint.dark
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(if (dark) IconBackgroundDark else IconBackgroundLight),
            contentAlignment = Alignment.Center,
        ) {
            when (val img = image) {
                is IconImage.Mono -> Image(img.bitmap, contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = Modifier.size(size * 0.62f))
                is IconImage.Full -> Image(img.bitmap, contentDescription = null, modifier = Modifier.size(size))
                is IconImage.Glyph -> Icon(img.vector, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
                null -> Icon(
                    sectionIcon(entry),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(size * 0.46f),
                )
            }
        }
        if (badge > 0) Badge(badge, dark, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-3).dp))
    }
}

/** Simbolo delle sezioni di Eurise (che non hanno un'icona di sistema propria). */
private fun sectionIcon(entry: AppEntry) = when (entry.euriseSection) {
    "calendar" -> EuriseIcons.calendar(1.9f)
    "notes" -> EuriseIcons.note(1.9f)
    "tasks" -> EuriseIcons.task(1.9f)
    "clock" -> EuriseIcons.clock(1.9f)
    "drive" -> EuriseIcons.folder(1.9f)
    "scanner" -> EuriseIcons.scan(1.9f)
    "calculator" -> EuriseIcons.calculator(1.9f)
    "qr" -> EuriseIcons.scan(1.9f)
    "privacy" -> EuriseIcons.shield(1.9f)
    "today" -> EuriseIcons.today(1.9f)
    else -> entry.category.icon(1.9f)
}

/** Numero di notifiche: pastiglia piena in accento. */
@Composable
fun Badge(count: Int, dark: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .background(if (dark) Color(0xFFA9C1F2) else Color(0xFF1F2A44), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (count > 99) "99+" else "$count",
            color = if (dark) Color(0xFF14203A) else Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

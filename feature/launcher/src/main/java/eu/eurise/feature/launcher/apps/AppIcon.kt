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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
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
    /** Icona di sistema o di un pacchetto di icone: si mostra com'è, senza il tondo della suite. */
    data class Plain(val bitmap: ImageBitmap) : IconImage
}

/**
 * Carica le icone a tema. Da Android 13 le app possono fornire un simbolo monocromatico: il launcher lo
 * colora con la tinta della categoria su un tondo blu notte. Se manca, si usa l'icona originale nel tondo.
 */
object IconLoader {
    private val cache = LruCache<String, IconImage>(300)

    suspend fun load(context: Context, entry: AppEntry, px: Int, theme: String = IconThemes.EURISE): IconImage? = withContext(Dispatchers.IO) {
        val cacheKey = "$theme|${entry.key}"
        cache.get(cacheKey)?.let { return@withContext it }
        val drawable = when {
            entry.shortcut != null -> WebApps.icon(context, entry.shortcut)
            entry.component != null -> runCatching { context.packageManager.getActivityIcon(entry.component) }.getOrNull()
            else -> null
        } ?: return@withContext null
        if (!IconThemes.isSuite(theme)) {
            // Tema di sistema o pacchetto di icone: il disegno del tema se c'è, altrimenti l'icona originale.
            val themed = entry.component?.takeIf { theme != IconThemes.SYSTEM }?.let { IconThemes.drawable(context, theme, it) }
            val plain = IconImage.Plain((themed ?: drawable).toBitmap(px, px, Bitmap.Config.ARGB_8888).asImageBitmap())
            cache.put(cacheKey, plain)
            return@withContext plain
        }
        // Le web app: il pacchetto è quello del browser, quindi il simbolo si cerca solo per nome; poi la sagoma
        // del logo staccata dal fondo, così hanno il tondo e le tinte della suite come le altre app.
        val glyph = if (entry.shortcut == null) SystemGlyphs.find(entry) else SystemGlyphs.findByLabel(entry.label)
        val image = officialMono(drawable, px)
            ?: glyph?.let { IconImage.Glyph(it) }
            ?: silhouette(drawable, px)
            ?: logoOnBackground(drawable, px)
            ?: IconImage.Full(drawable.toBitmap(px, px, Bitmap.Config.ARGB_8888).asImageBitmap())
        cache.put(cacheKey, image)
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

    /**
     * Per le icone a tutto riquadro (le web app hanno quasi sempre il logo su un fondo pieno): il fondo è il colore del
     * bordo; i punti che se ne staccano formano la sagoma del logo. Se il bordo non è uniforme (una foto, un disegno
     * sfumato) o la sagoma è troppo piccola o troppo grande, null: resta l'icona originale.
     */
    internal fun logoOnBackground(drawable: Drawable, px: Int): IconImage? {
        val size = px * 3 / 2
        val bitmap = if (drawable is AdaptiveIconDrawable) cropCenter(drawable.toBitmap(size, size, Bitmap.Config.ARGB_8888))
        else drawable.toBitmap(px, px, Bitmap.Config.ARGB_8888)
        val mask = LogoMask.of(bitmap) ?: return null
        return IconImage.Mono(mask.asImageBitmap())
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
        return findByLabel(entry.label)
    }

    fun findByLabel(label: String): ImageVector? {
        val l = label.lowercase()
        return byLabel.firstOrNull { l.contains(it.first) }?.second?.invoke()
    }
}

/** Vero quando i simboli delle icone prendono colori vari invece del colore della categoria. */
val LocalVariedIcons = androidx.compose.runtime.compositionLocalOf { false }

/** Vero quando il simbolo prende il colore dell'icona originale dell'app, portato alle tinte della suite. */
val LocalAppIconColors = androidx.compose.runtime.compositionLocalOf { false }

/** Tema delle icone scelto in Griglia e icone (vedi [IconThemes]). */
val LocalIconTheme = androidx.compose.runtime.compositionLocalOf { IconThemes.EURISE }

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
    val theme = LocalIconTheme.current
    var image by remember(entry.key, theme) { mutableStateOf<IconImage?>(null) }
    LaunchedEffect(entry.key, theme) { image = IconLoader.load(context, entry, px, theme) }
    val colorful = theme == IconThemes.EURISE_COLOR
    val fromApp = LocalAppIconColors.current || colorful
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
    // A colori: tondo chiaro nella tinta (un po' più sfumato in alto a sinistra), simbolo nella stessa tinta più profonda;
    // di sera il tondo perde luce ma resta colorato.
    val symbolTint = if (colorful) lerp(tint, Color.Black, if (dark) 0.05f else 0.32f) else tint
    val round: Brush = if (colorful) {
        val base = if (dark) lerp(tint, IconBackgroundDark, 0.55f) else lerp(tint, Color.White, 0.80f)
        Brush.linearGradient(listOf(lerp(base, Color.White, if (dark) 0.06f else 0.45f), base))
    } else androidx.compose.ui.graphics.SolidColor(if (dark) IconBackgroundDark else IconBackgroundLight)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val plain = image as? IconImage.Plain
        if (plain != null) {
            Image(plain.bitmap, contentDescription = null, modifier = Modifier.size(size))
        } else Box(
            Modifier
                .size(size)
                .shadow(if (colorful) 3.dp else 6.dp, CircleShape)
                .clip(CircleShape)
                .background(round),
            contentAlignment = Alignment.Center,
        ) {
            when (val img = image) {
                is IconImage.Mono -> Image(img.bitmap, contentDescription = null, modifier = Modifier.size(size * 0.74f).softTint(symbolTint))
                is IconImage.Full -> Image(img.bitmap, contentDescription = null, modifier = Modifier.size(size))
                is IconImage.Glyph -> Icon(img.vector, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.6f).softTint(symbolTint))
                is IconImage.Plain -> Unit
                null -> Icon(
                    sectionIcon(entry),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.56f).softTint(symbolTint),
                )
            }
        }
        if (badge > 0) Badge(badge, dark, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-3).dp))
    }
}

/**
 * Colora il disegno con un leggero sfumato: dalla tinta schiarita in alto a sinistra alla tinta piena in basso a destra.
 * Il disegno sotto conta solo per la forma (alfa).
 */
private fun Modifier.softTint(tint: Color): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.linearGradient(listOf(lerp(tint, Color.White, 0.3f), tint), start = Offset.Zero, end = Offset(size.width, size.height)),
            blendMode = BlendMode.SrcIn,
        )
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
    "weather" -> EuriseIcons.sun(1.9f)
    "qr" -> EuriseIcons.scan(1.9f)
    "privacy" -> EuriseIcons.shield(1.9f)
    "ponte" -> EuriseIcons.work(1.9f)
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

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
import androidx.compose.ui.graphics.toArgb
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
import eu.eurise.core.designsystem.icon.SuiteSymbols
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
        "com.android.dialer" to { SuiteSymbols.phone }, "com.google.android.dialer" to { SuiteSymbols.phone },
        "org.fossify.phone" to { SuiteSymbols.phone },
        "foundation.e.message" to { SuiteSymbols.messages }, "com.android.messaging" to { SuiteSymbols.messages },
        "com.google.android.apps.messaging" to { SuiteSymbols.messages }, "org.fossify.messages" to { SuiteSymbols.messages },
        "com.android.contacts" to { SuiteSymbols.contacts }, "org.fossify.contacts" to { SuiteSymbols.contacts },
        "foundation.e.mail" to { SuiteSymbols.mail },
        "foundation.e.browser" to { SuiteSymbols.browser }, "org.lineageos.jelly" to { SuiteSymbols.browser },
        "foundation.e.camera" to { EuriseIcons.camera(2.3f) }, "org.lineageos.aperture" to { EuriseIcons.camera(2.3f) },
        "com.android.camera" to { EuriseIcons.camera(2.3f) },
        "eu.eurise.office" to { SuiteSymbols.office },
        "eu.eurise.gallery" to { SuiteSymbols.gallery }, "foundation.e.gallery" to { SuiteSymbols.gallery }, "org.lineageos.glimpse" to { SuiteSymbols.gallery },
        "com.android.gallery3d" to { SuiteSymbols.gallery },
        "com.android.settings" to { EuriseIcons.gear(2.3f) },
        "com.android.deskclock" to { SuiteSymbols.clock }, "org.fossify.clock" to { SuiteSymbols.clock },
        "com.android.calculator2" to { SuiteSymbols.calculator }, "org.lineageos.calculator" to { SuiteSymbols.calculator },
        "org.lineageos.eleven" to { EuriseIcons.music(2.3f) }, "foundation.e.music" to { EuriseIcons.music(2.3f) },
        "org.lineageos.recorder" to { EuriseIcons.mic(2.3f) },
        "foundation.e.calendar" to { SuiteSymbols.calendar }, "org.lineageos.etar" to { SuiteSymbols.calendar },
        "foundation.e.notes" to { SuiteSymbols.notes }, "foundation.e.tasks" to { SuiteSymbols.tasks },
        "foundation.e.advancedprivacy" to { EuriseIcons.shield(2.3f) }, "foundation.e.privacycentral" to { EuriseIcons.shield(2.3f) },
        "foundation.e.apps" to { EuriseIcons.bag(2.3f) }, "org.fdroid" to { EuriseIcons.bag(2.3f) },
        "com.android.documentsui" to { SuiteSymbols.files }, "foundation.e.drive" to { SuiteSymbols.files },
        "app.organicmaps" to { SuiteSymbols.maps }, "app.comaps" to { SuiteSymbols.maps }, "foundation.e.maps" to { SuiteSymbols.maps },
        "foundation.e.weather" to { SuiteSymbols.weather },
    )

    /** Parole del nome dell'app, per le varianti di sistema con un pacchetto diverso. */
    private val byLabel: List<Pair<String, () -> ImageVector>> = listOf(
        "telefono" to { SuiteSymbols.phone }, "phone" to { SuiteSymbols.phone },
        "messag" to { SuiteSymbols.messages }, "contatti" to { SuiteSymbols.contacts }, "contacts" to { SuiteSymbols.contacts },
        "fotocamera" to { EuriseIcons.camera(2.3f) }, "camera" to { EuriseIcons.camera(2.3f) },
        "galleria" to { SuiteSymbols.gallery }, "gallery" to { SuiteSymbols.gallery },
        "impostazioni" to { EuriseIcons.gear(2.3f) }, "settings" to { EuriseIcons.gear(2.3f) },
        "orologio" to { SuiteSymbols.clock }, "calcolatrice" to { SuiteSymbols.calculator },
        "registratore" to { EuriseIcons.mic(2.3f) }, "browser" to { SuiteSymbols.browser },
        "meteo" to { SuiteSymbols.weather }, "mappe" to { SuiteSymbols.maps }, "musica" to { EuriseIcons.music(2.3f) },
        "file" to { SuiteSymbols.files }, "app lounge" to { EuriseIcons.bag(2.3f) },
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

/** Forma delle icone scelta in Griglia e icone (vedi [IconStyles.shape]). */
val LocalIconShape = androidx.compose.runtime.compositionLocalOf { IconStyles.SHAPE_AUTO }

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
    val fromApp = LocalAppIconColors.current || colorful || IconStyles.isStyle(theme)
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
    // A colori (tavola IconeColori): tondo chiaro nella tinta (un po' più sfumato in alto a sinistra), di sera il tondo
    // perde luce ma resta colorato; il simbolo va da una tinta viva a una più profonda, chiara e brillante di sera.
    // Eurise (tavola IconeStili): tondo blu notte con la sfumatura diagonale e l'alone caldo, il simbolo nella tinta.
    // Stili iOS, Android e /e/OS: il colore più vicino della palette dello stile (tavola IconeStili).
    val style = if (IconStyles.isStyle(theme)) IconStyles.swatch(theme, tint) else null
    val symbolBrush = when {
        style != null -> listOf(style.symbol, style.symbol)
        colorful -> vividSymbol(tint, dark)
        else -> listOf(lerp(tint, Color.White, 0.3f), tint)
    }
    val round: Brush = when {
        style != null -> Brush.verticalGradient(listOf(style.top, style.bottom))
        colorful -> {
            val base = if (dark) lerp(tint, IconBackgroundDark, 0.55f) else lerp(tint, Color.White, 0.80f)
            Brush.linearGradient(listOf(lerp(base, Color.White, if (dark) 0.06f else 0.45f), base))
        }
        else -> EuriseRound
    }
    val shape = IconStyles.shape(LocalIconShape.current, theme)
    val glow = theme == IconThemes.EURISE
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        val plain = image as? IconImage.Plain
        if (plain != null) {
            Image(plain.bitmap, contentDescription = null, modifier = Modifier.size(size))
        } else Box(
            Modifier
                .size(size)
                .shadow(if (colorful || style != null) 3.dp else 6.dp, shape)
                .clip(shape)
                .background(round)
                .then(if (glow) Modifier.background(EuriseGlow) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            when (val img = image) {
                is IconImage.Mono -> Image(img.bitmap, contentDescription = null, modifier = Modifier.size(size * 0.8f).softTint(symbolBrush))
                is IconImage.Full -> Image(img.bitmap, contentDescription = null, modifier = Modifier.size(size))
                is IconImage.Glyph -> Icon(img.vector, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.72f).softTint(symbolBrush))
                is IconImage.Plain -> Unit
                null -> Icon(
                    sectionIcon(entry),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.72f).softTint(symbolBrush),
                )
            }
        }
        if (badge > 0) Badge(badge, dark, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-3).dp))
    }
}

/**
 * Colora il disegno con una sfumatura diagonale: [colors] dall'alto a sinistra al basso a destra. Il disegno sotto conta
 * solo per la forma (alfa).
 */
private fun Modifier.softTint(colors: List<Color>): Modifier = this
    .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(Brush.linearGradient(colors, start = Offset.Zero, end = Offset(size.width, size.height)), blendMode = BlendMode.SrcIn)
    }

/** Il tondo blu notte delle icone: sfumatura diagonale dal lato della stellina (in alto a destra) al lato opposto. */
private val EuriseRound: Brush = object : androidx.compose.ui.graphics.ShaderBrush() {
    override fun createShader(size: androidx.compose.ui.geometry.Size) = androidx.compose.ui.graphics.LinearGradientShader(
        from = Offset(size.width, 0f), to = Offset(0f, size.height),
        colors = listOf(Color(0xFF3A4D80), Color(0xFF222D4D), Color(0xFF121828)), colorStops = listOf(0f, 0.55f, 1f),
    )
}

/** L'alone caldo attorno al posto della stellina (centro 77,35 e raggio 26 nel tondo da 72 della tavola). */
private val EuriseGlow: Brush = object : androidx.compose.ui.graphics.ShaderBrush() {
    override fun createShader(size: androidx.compose.ui.geometry.Size) = androidx.compose.ui.graphics.RadialGradientShader(
        center = Offset(size.width * 59f / 72f, size.height * 17f / 72f), radius = size.width * 26f / 72f,
        colors = listOf(Color(0x6BE4C27A), Color(0x24C9A45A), Color(0x00C9A45A)), colorStops = listOf(0f, 0.5f, 1f),
    )
}

/**
 * «Eurise a colori»: il simbolo nella tinta dell'app, più vivo e contrastato del tondo (tavola IconeColori): di giorno
 * dalla luminosità 0,48 a 0,31, di sera da 0,83 a 0,70, con la saturazione portata su con un tetto.
 */
internal fun vividSymbol(tint: Color, dark: Boolean): List<Color> {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(tint.toArgb(), hsl)
    val s = if (dark) (hsl[1] + 0.14f).coerceIn(0.60f, 0.68f) else (0.15f + 0.45f * hsl[1]).coerceIn(0.20f, 0.55f)
    val (from, to) = if (dark) 0.83f to 0.70f else 0.48f to 0.31f
    return listOf(Color(androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(hsl[0], s, from))),
        Color(androidx.core.graphics.ColorUtils.HSLToColor(floatArrayOf(hsl[0], s, to))))
}

/** Simbolo delle sezioni di Eurise (che non hanno un'icona di sistema propria). */
private fun sectionIcon(entry: AppEntry) = when (entry.euriseSection) {
    "calendar" -> SuiteSymbols.calendar
    "notes" -> SuiteSymbols.notes
    "tasks" -> SuiteSymbols.tasks
    "clock" -> SuiteSymbols.clock
    "drive" -> SuiteSymbols.files
    "scanner" -> SuiteSymbols.scanner
    "calculator" -> SuiteSymbols.calculator
    "weather" -> SuiteSymbols.weather
    "qr" -> EuriseIcons.scan(SuiteSymbols.Stroke)
    "privacy" -> EuriseIcons.shield(SuiteSymbols.Stroke)
    "ponte" -> EuriseIcons.work(SuiteSymbols.Stroke)
    "today" -> EuriseIcons.today(SuiteSymbols.Stroke)
    "alfred" -> SuiteSymbols.alfred
    else -> entry.category.icon(SuiteSymbols.Stroke)
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

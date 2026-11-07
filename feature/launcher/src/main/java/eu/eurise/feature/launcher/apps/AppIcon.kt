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
}

/**
 * Carica le icone a tema. Da Android 13 le app possono fornire un simbolo monocromatico: il launcher lo
 * colora con la tinta della categoria su un tondo blu notte. Se manca, si usa l'icona originale nel tondo.
 */
object IconLoader {
    private val cache = LruCache<String, IconImage>(300)

    suspend fun load(context: Context, entry: AppEntry, px: Int): IconImage? = withContext(Dispatchers.IO) {
        cache.get(entry.key)?.let { return@withContext it }
        val component = entry.component ?: return@withContext null
        val drawable = runCatching { context.packageManager.getActivityIcon(component) }.getOrNull() ?: return@withContext null
        val image = render(drawable, px)
        cache.put(entry.key, image)
        image
    }

    private fun render(drawable: Drawable, px: Int): IconImage {
        if (Build.VERSION.SDK_INT >= 33 && drawable is AdaptiveIconDrawable) {
            val mono = drawable.monochrome
            if (mono != null) {
                // Il simbolo monocromatico sta al centro di un'area di 108 dp: se ne ritaglia la parte visibile.
                val full = mono.toBitmap(px * 3 / 2, px * 3 / 2, Bitmap.Config.ARGB_8888)
                val inset = full.width / 6
                val cropped = Bitmap.createBitmap(full, inset, inset, full.width - 2 * inset, full.height - 2 * inset)
                return IconImage.Mono(cropped.asImageBitmap())
            }
        }
        return IconImage.Full(drawable.toBitmap(px, px, Bitmap.Config.ARGB_8888).asImageBitmap())
    }
}

/** Fondo dei tondi delle icone: blu notte di giorno, grafite di sera. */
val IconBackgroundLight = Color(0xFF1F2A44)
val IconBackgroundDark = Color(0xFF262B33)

/**
 * Icona a tema nel tondo della suite, con il numero di notifiche in alto a destra se [badge] è maggiore di zero.
 */
@Composable
fun AppIcon(entry: AppEntry, size: Dp, dark: Boolean, badge: Int = 0, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    var image by remember(entry.key) { mutableStateOf<IconImage?>(null) }
    LaunchedEffect(entry.key) { image = IconLoader.load(context, entry, px) }
    val tint = entry.category.tint.dark
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

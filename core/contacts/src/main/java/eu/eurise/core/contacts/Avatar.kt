package eu.eurise.core.contacts

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.theme.LocalEuriseColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Coppie fondo/testo per le iniziali (le tinte delle anteprime), scelte dal nome: la stessa persona ha sempre lo stesso colore. */
private val LIGHT = listOf(
    Color(0xFFDDE3F0) to Color(0xFF3E5C8A), Color(0xFFDCE7DF) to Color(0xFF4F6B5A), Color(0xFFF2E6CC) to Color(0xFF85672B),
    Color(0xFFE7DDE8) to Color(0xFF6A4A6B), Color(0xFFF6DADC) to Color(0xFF8C2F39), Color(0xFFE0E8EC) to Color(0xFF3D6470),
)
private val DARK = listOf(
    Color(0xFF2C3A57) to Color(0xFFA9C1F2), Color(0xFF263A2E) to Color(0xFF93BBA0), Color(0xFF3B3222) to Color(0xFFD2B06A),
    Color(0xFF3A2C3B) to Color(0xFFD3B3D4), Color(0xFF3E2629) to Color(0xFFE8939B), Color(0xFF243840) to Color(0xFF9CC6D2),
)

/** Avatar rotondo: la foto della rubrica se c'è (ridotta alla misura mostrata), altrimenti le iniziali su una tinta. */
@Composable
fun Avatar(name: String, photo: Uri?, size: Dp = 42.dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var image by remember(photo) { mutableStateOf<ImageBitmap?>(null) }
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    LaunchedEffect(photo) {
        image = photo?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= px) sample *= 2
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    }?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    val palette = if (LocalEuriseColors.current.isDark) DARK else LIGHT
    val (bg, fg) = palette[Math.floorMod(name.trim().lowercase().hashCode(), palette.size)]
    Box(modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        val img = image
        if (img != null) Image(img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        else Text(Contacts.initials(name), color = fg, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
    }
}

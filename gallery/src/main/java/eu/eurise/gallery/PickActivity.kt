package eu.eurise.gallery

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.gallery.data.MediaRepository
import eu.eurise.gallery.logic.MediaItem
import eu.eurise.gallery.ui.Thumb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * La Galleria come scelta quando un'app chiede una foto o un video («Apri da», «Scegli un'immagine»: GET_CONTENT e
 * PICK, 15 novembre). Una o più foto, secondo quello che l'app permette; all'app torna solo il permesso di leggere i
 * file scelti, niente altro della libreria.
 */
class PickActivity : ComponentActivity() {
    private val readPermissions: Array<String> =
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    private fun canRead() = readPermissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val types = (intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList() ?: listOfNotNull(intent.type)).ifEmpty { listOf("*/*") }
        val wantImages = types.any { it.startsWith("image/") || it.startsWith("*/") }
        val wantVideos = types.any { it.startsWith("video/") || it.startsWith("*/") }
        val many = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        setContent {
            EuriseTheme {
                var allowed by remember { mutableStateOf(canRead()) }
                val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    allowed = canRead()
                    if (!allowed) { setResult(Activity.RESULT_CANCELED); finish() }
                }
                LaunchedEffect(Unit) { if (!allowed) ask.launch(readPermissions) }
                if (allowed) Picker(wantImages, wantVideos, many)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Picker(images: Boolean, videos: Boolean, many: Boolean) {
        val scheme = MaterialTheme.colorScheme
        val media by produceState<List<MediaItem>?>(null) {
            value = withContext(Dispatchers.IO) {
                MediaRepository(this@PickActivity).all().filter { !it.cloudOnly && if (it.isVideo) videos else images }.sortedByDescending { it.takenAt }
            }
        }
        var chosen by remember { mutableStateOf(listOf<MediaItem>()) }
        Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { setResult(Activity.RESULT_CANCELED); finish() }, contentAlignment = Alignment.Center) {
                    Icon(EuriseIcons.close(), stringResource(R.string.close), tint = scheme.onSurface)
                }
                Text(
                    if (chosen.isEmpty()) stringResource(if (videos && !images) R.string.pick_title_video else R.string.pick_title)
                    else pluralStringResource(R.plurals.selected_count, chosen.size, chosen.size),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                if (many && chosen.isNotEmpty()) Text(
                    stringResource(R.string.pick_done), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = scheme.onPrimary,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(scheme.primary).clickable { give(chosen) }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
            val list = media
            if (list != null && list.isEmpty()) {
                Text(stringResource(R.string.empty_title), modifier = Modifier.padding(24.dp), color = scheme.onSurfaceVariant)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                items(list.orEmpty(), key = { it.uri }) { item ->
                    val on = item in chosen
                    Box(
                        Modifier.padding(1.5.dp).aspectRatio(1f).clip(RoundedCornerShape(6.dp))
                            .then(if (on) Modifier.border(3.dp, scheme.primary, RoundedCornerShape(6.dp)) else Modifier)
                            .clickable { if (many) chosen = if (on) chosen - item else chosen + item else give(listOf(item)) },
                    ) {
                        Thumb(item, Modifier.fillMaxSize())
                        if (item.isVideo) Icon(EuriseIcons.play(), null, tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.align(Alignment.Center).size(26.dp))
                        if (on) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(scheme.primary), contentAlignment = Alignment.Center) {
                            Icon(EuriseIcons.check(2.4f), null, tint = scheme.onPrimary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }

    /** All'app che ha chiesto tornano gli indirizzi scelti con il solo permesso di lettura. */
    private fun give(items: List<MediaItem>) {
        val uris = items.map { Uri.parse(it.uri) }
        val back = Intent().setData(uris.first()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val clip = ClipData.newRawUri(null, uris.first())
        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        back.clipData = clip
        setResult(Activity.RESULT_OK, back)
        finish()
    }
}

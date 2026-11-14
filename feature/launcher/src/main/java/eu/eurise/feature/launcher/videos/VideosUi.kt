package eu.eurise.feature.launcher.videos

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.feature.launcher.R
import eu.eurise.feature.launcher.feeds.FeedReader
import eu.eurise.feature.launcher.home.Pill
import eu.eurise.feature.launcher.summary.BottomSheet
import eu.eurise.feature.launcher.summary.SwitchRow
import eu.eurise.core.designsystem.component.ServiceGate
import eu.eurise.core.suite.PrivacyService
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Pillola della home: gli ultimi video dei canali preferiti, da scorrere di lato. */
@Composable
fun VideosPill(
    videos: List<Video>?,
    hasChannels: Boolean,
    hazeState: HazeState,
    modifier: Modifier,
    onOpen: (Video) -> Unit,
    onChannels: () -> Unit,
) {
    Pill(hazeState, modifier, title = stringResource(R.string.videos_title), trailing = {
        Text(
            stringResource(R.string.videos_channels), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onChannels).padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }) {
        ServiceGate(PrivacyService.Videos) {
            when {
                !hasChannels -> Text(
                    stringResource(R.string.videos_empty), fontSize = 12.5.sp, lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onChannels),
                )
                videos == null -> Text(stringResource(R.string.news_loading), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                videos.isEmpty() -> Text(stringResource(R.string.videos_none), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(videos, key = { it.id }) { v -> VideoTile(v) { onOpen(v) } }
                }
            }
        }
    }
}

@Composable
private fun VideoTile(video: Video, onClick: () -> Unit) {
    var bitmap by remember(video.id) { mutableStateOf(Thumbs[video.id]) }
    LaunchedEffect(video.id) {
        if (bitmap == null) FeedReader.image(video.thumbnail, maxSide = 200)?.let { Thumbs[video.id] = it; bitmap = it }
    }
    Column(Modifier.width(150.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            bitmap?.let { Image(it, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.play2(2f), null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
        Text(video.title, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            video.channel + " · " + eu.eurise.feature.launcher.summary.ago(System.currentTimeMillis(), video.publishedAt),
            fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

private val Thumbs = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, ImageBitmap>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > 60
})

/** Canali preferiti: importazione da NewPipe, aggiunta con l'indirizzo, interruttore per ciascuno. */
@Composable
fun ChannelsSheet(repository: VideosRepository, onImport: () -> Unit, onClose: () -> Unit) {
    val channels by repository.channels.collectAsStateWithLifecycle()
    var address by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    BottomSheet(stringResource(R.string.videos_channels_title), onClose) {
        item {
            Text(stringResource(R.string.videos_channels_hint), fontSize = 12.5.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primary)
                        .clickable(role = Role.Button, onClick = onImport).padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.videos_import), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) }
            }
        }
        item {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    if (address.isEmpty()) Text(stringResource(R.string.videos_address_hint), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicTextField(
                        address, { address = it; error = false }, singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (busy) R.string.news_loading else R.string.videos_add), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = address.isNotBlank() && !busy) {
                        busy = true
                        scope.launch {
                            val ok = repository.addChannel(address)
                            busy = false
                            if (ok) address = "" else error = true
                        }
                    }.padding(10.dp),
                )
            }
        }
        if (error) item { Text(stringResource(R.string.videos_address_error), fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
        items(channels, key = { it.id }) { ch ->
            Column(Modifier.heightIn(min = 48.dp)) {
                SwitchRow(ch.name, ch.enabled, { repository.setEnabled(ch.id, it) }, trailing = {
                    Text(
                        stringResource(R.string.news_remove), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { repository.remove(ch.id) }.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                })
            }
        }
    }
}

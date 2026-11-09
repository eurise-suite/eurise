package eu.eurise.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.gallery.R
import eu.eurise.gallery.logic.MediaItem
import eu.eurise.gallery.logic.Memory
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

private val Dark = Color(0xFF14171C)

/** Una foto a schermo intero: due dita o doppio tocco ingrandiscono; a ingrandimento normale si scorre alla vicina. */
@Composable
private fun ZoomableMedia(item: MediaItem, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    ZoomableBitmap(rememberFull(item), item.isVideo, item.uri, onPlay, modifier)
}

@Composable
internal fun ZoomableBitmap(image: ImageBitmap?, isVideo: Boolean, key: String, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    var scale by remember(key) { mutableFloatStateOf(1f) }
    var offset by remember(key) { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .fillMaxSize()
            .pointerInput(key) {
                detectTapGestures(onDoubleTap = {
                    scale = if (scale > 1f) 1f else 2.5f
                    offset = Offset.Zero
                })
            }
            .pointerInput(key) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        // Con un dito solo e senza ingrandimento il gesto resta al pager.
                        if (event.changes.count { it.pressed } >= 2 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                            offset = if (scale > 1f) offset + event.calculatePan() else Offset.Zero
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        image?.let {
            Image(
                it, null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                },
            )
        }
        if (isVideo) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Dark.copy(alpha = 0.55f)).clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) { Icon(EuriseIcons.play2(2.2f), stringResource(R.string.play), tint = Color.White, modifier = Modifier.size(30.dp)) }
        }
    }
}

@Composable
internal fun Viewer(list: List<MediaItem>, start: Int, actions: MediaActions, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (list.size - 1).coerceAtLeast(0))) { list.size }
    Box(Modifier.fillMaxSize().background(Dark)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { list[it].uri }) { page ->
            ZoomableMedia(list[page], onPlay = { actions.play(list[page]) })
        }
        val current = list.getOrNull(pager.currentPage) ?: return@Box
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundAction(EuriseIcons.close(), stringResource(R.string.close), onClose)
            Spacer(Modifier.weight(1f))
            Text(
                longDate(Instant.ofEpochMilli(current.takenAt).atZone(ZoneId.systemDefault()).toLocalDate()),
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(44.dp))
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (!current.cloudOnly) RoundAction(EuriseIcons.heart(if (current.favorite) 3f else 2.2f), stringResource(R.string.favorite)) { actions.setFavorite(current, !current.favorite) }
            RoundAction(EuriseIcons.share(), stringResource(R.string.share)) { actions.share(listOf(current)) }
            if (!current.cloudOnly && !current.isVideo) RoundAction(EuriseIcons.pencil(), stringResource(R.string.edit)) { actions.edit(current) }
            RoundAction(EuriseIcons.trash(), stringResource(R.string.delete)) { actions.trash(listOf(current)) }
        }
    }
}

@Composable
internal fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(containerColor = Dark.copy(alpha = 0.45f), contentColor = Color.White),
        modifier = Modifier.size(44.dp),
    ) { Icon(icon, label, modifier = Modifier.size(20.dp)) }
}

/** Accadde oggi: le foto di un giorno di qualche anno fa, una alla volta. */
@Composable
internal fun MemoryScreen(memory: Memory, actions: MediaActions, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val items = memory.items
    val pager = rememberPagerState { items.size }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(Dark)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { items[it].uri }) { page ->
            ZoomableMedia(items[page], onPlay = { actions.play(items[page]) })
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Oltre venti foto i trattini diventerebbero invisibili: basta la striscia delle miniature.
                if (items.size <= 20) items.indices.forEach { i ->
                    Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (i == pager.currentPage) Color.White else Color.White.copy(alpha = 0.4f)))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.memory_title), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                RoundAction(EuriseIcons.close(), stringResource(R.string.close), onClose)
            }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Dark.copy(alpha = 0.62f)).navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "${pluralStringResource(R.plurals.memory_years, memory.yearsAgo, memory.yearsAgo)} · ${pluralStringResource(R.plurals.items_count, items.size, items.size)}",
                    color = Color(0xFFD9D5CC), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(longDate(memory.date), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
            }
            LazyRow(contentPadding = PaddingValues(0.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(items, key = { _, it -> it.uri }) { i, item ->
                    val shape = RoundedCornerShape(10.dp)
                    Thumb(
                        item,
                        Modifier.size(56.dp).clip(shape)
                            .then(if (i == pager.currentPage) Modifier.border(2.dp, Color.White, shape) else Modifier)
                            .clickable { scope.launch { pager.animateScrollToPage(i) } },
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Color.White).clickable { actions.diary(items) },
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(EuriseIcons.book(), null, tint = Color(0xFF1E1D1B), modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.memory_to_diary), color = Color(0xFF1E1D1B), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.16f)).clickable { actions.shareClean(items) },
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.share_clean_button), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

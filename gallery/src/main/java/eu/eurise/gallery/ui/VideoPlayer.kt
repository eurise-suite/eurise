package eu.eurise.gallery.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.util.Rational
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.gallery.GalleryActivity
import eu.eurise.gallery.R
import eu.eurise.gallery.data.VideoTools
import eu.eurise.gallery.logic.MediaItem
import eu.eurise.gallery.logic.Trim
import eu.eurise.gallery.logic.clock
import eu.eurise.gallery.logic.moveTrim
import eu.eurise.gallery.logic.nextSpeed
import eu.eurise.gallery.logic.seekTarget
import eu.eurise.gallery.logic.speedLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.media3.common.MediaItem as ExoItem

private val Back = Color(0xFF0B0D10)
private val Sheet = Color(0xFF14171C)
private val Raised = Color(0xFF262B33)
private val Muted = Color(0xFFA4A9B2)
private val Text1 = Color(0xFFECE9E3)
private val Accent = Color(0xFFA9C1F2)
private const val STEP_MS = 10_000L
private const val FRAMES = 7

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * Il lettore video della Galleria (anteprima GalVideo): doppio tocco ±10 s, scorrimento verticale per luminosità
 * (a sinistra) e volume (a destra), due dita per riempire lo schermo, barra con le miniature, velocità, audio,
 * ripetizione, fotogramma come foto, Taglia (copia senza ricodificare), finestra sopra le altre app.
 *
 * [item] manca per i video della cartella protetta: allora niente Preferita, Taglia ed Elimina.
 */
@Composable
internal fun VideoScreen(
    item: MediaItem?,
    title: String,
    load: suspend () -> Uri?,
    actions: MediaActions?,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { ExoPlayer.Builder(context).build() }
    val uri by produceState<Uri?>(null) { value = load() }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(item?.durationMs ?: 0L) }
    var size by remember { mutableStateOf(VideoSize.UNKNOWN) }
    var controls by remember { mutableStateOf(true) }
    var fill by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var muted by remember { mutableStateOf(false) }
    var loop by remember { mutableStateOf(false) }
    var trimming by remember { mutableStateOf(false) }
    var trim by remember { mutableStateOf(Trim()) }
    var hint by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val pip = GalleryActivity.inPip

    BackHandler { if (trimming) trimming = false else onClose() }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onVideoSizeChanged(videoSize: VideoSize) { size = videoSize }
            override fun onPlaybackStateChanged(state: Int) {
                if (player.duration > 0) duration = player.duration
                if (state == Player.STATE_ENDED) controls = true
            }
            override fun onPlayerError(error: PlaybackException) { failed = true }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    LaunchedEffect(uri) {
        val u = uri ?: return@LaunchedEffect
        player.setMediaItem(ExoItem.fromUri(u))
        player.prepare()
        player.play()
    }
    // Posizione aggiornata solo mentre si guarda; in taglio il video si ferma alla fine del tratto.
    LaunchedEffect(playing, trimming) {
        while (playing) {
            position = player.currentPosition
            if (trimming && duration > 0 && position >= trim.endMs(duration)) player.seekTo(trim.startMs(duration))
            delay(200)
        }
        position = player.currentPosition
    }
    LaunchedEffect(controls, playing) { if (controls && playing && !trimming) { delay(3500); controls = false } }
    LaunchedEffect(hint) { if (hint != null) { delay(900); hint = null } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP && !GalleryActivity.inPip) player.pause() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) { onDispose { GalleryActivity.pipAllowed = false } }
    GalleryActivity.pipAllowed = playing
    val frames by produceState<List<Bitmap>>(emptyList(), uri, duration) {
        val u = uri ?: return@produceState
        if (duration > 0) value = withContext(Dispatchers.IO) { VideoTools.strip(context, u, duration, FRAMES) }
    }

    fun seekBy(delta: Long) {
        val target = seekTarget(player.currentPosition, delta, duration)
        player.seekTo(target); position = target
        hint = if (delta > 0) "+10 s" else "−10 s"
    }

    fun enterPip() {
        val activity = context.activity() ?: return
        val w = size.width.takeIf { it > 0 } ?: 16
        val h = size.height.takeIf { it > 0 } ?: 9
        val ratio = Rational(w, h).let { if (it.toFloat() > 2.39f) Rational(239, 100) else if (it.toFloat() < 0.42f) Rational(42, 100) else it }
        runCatching { activity.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build()) }
    }

    Box(Modifier.fillMaxSize().background(Back)) {
        VideoSurface(player, size, fill, Modifier.fillMaxSize())
        if (!pip) Gestures(
            onTap = { controls = !controls },
            onDoubleTap = { right -> seekBy(if (right) STEP_MS else -STEP_MS) },
            onVertical = { right, delta -> hint = if (right) changeVolume(context, delta) else changeBrightness(context, delta) },
            onPinch = { grow -> fill = grow },
        )
        hint?.let {
            Text(it, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center).offset(y = (-110).dp).clip(RoundedCornerShape(18.dp)).background(Back.copy(alpha = 0.6f)).padding(horizontal = 14.dp, vertical = 8.dp))
        }
        if (failed) Text(stringResource(R.string.video_failed), color = Text1, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        if (pip) return@Box

        if (controls || trimming) {
            Header(title, item != null, size, duration, onClose = onClose, onPip = ::enterPip, onOther = {
                uri?.let { u ->
                    val open = Intent(Intent.ACTION_VIEW).setDataAndType(u, "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(Intent.createChooser(open, null)) }
                }
            })
            if (!trimming) Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(34.dp), verticalAlignment = Alignment.CenterVertically) {
                Round(EuriseIcons.undo(), stringResource(R.string.video_back10), 52) { seekBy(-STEP_MS) }
                Round(if (playing) EuriseIcons.pause() else EuriseIcons.play2(2.2f), stringResource(if (playing) R.string.video_pause else R.string.play), 72) {
                    if (playing) player.pause() else { if (player.playbackState == Player.STATE_ENDED) player.seekTo(0); player.play() }
                }
                Round(EuriseIcons.redo(), stringResource(R.string.video_forward10), 52) { seekBy(STEP_MS) }
            }
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(Sheet)
                    .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(clock(position), color = Text1, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Strip(frames, duration, position, trim.takeIf { trimming }, Modifier.weight(1f),
                        onSeek = { f -> val t = (f * duration).toLong(); player.seekTo(t); position = t },
                        onTrim = { start, f -> trim = moveTrim(trim, start, f, duration); player.seekTo(if (start) trim.startMs(duration) else trim.endMs(duration)); position = player.currentPosition },
                    )
                    Text(clock(duration), color = Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
                if (trimming) TrimBar(trim, duration,
                    onCancel = { trimming = false; trim = Trim() },
                    onSave = {
                        val u = uri ?: return@TrimBar
                        val from = trim.startMs(duration); val to = trim.endMs(duration)
                        trimming = false
                        Toast.makeText(context, R.string.video_cut_working, Toast.LENGTH_SHORT).show()
                        scope.launch {
                            val done = withContext(Dispatchers.IO) { VideoTools.cut(context, u, from, to, "Eurise_" + System.currentTimeMillis()) }
                            Toast.makeText(context, if (done != null) R.string.video_cut_done else R.string.video_cut_failed, Toast.LENGTH_LONG).show()
                        }
                    })
                else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Chip(speedLabel(speed), null, Modifier.weight(1f), stringResource(R.string.video_speed)) { speed = nextSpeed(speed); player.setPlaybackSpeed(speed) }
                        Chip(null, if (muted) EuriseIcons.volumeOff() else EuriseIcons.volume(), Modifier.weight(1f), stringResource(if (muted) R.string.video_unmute else R.string.video_mute)) {
                            muted = !muted; player.volume = if (muted) 0f else 1f
                        }
                        Chip(null, EuriseIcons.repeat(if (loop) 3f else 2.2f), Modifier.weight(1f), stringResource(R.string.video_loop), on = loop) {
                            loop = !loop; player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                        }
                        // Nella cartella protetta niente fotogrammi: finirebbero tra le foto visibili.
                        if (item != null) Chip(null, EuriseIcons.camera(), Modifier.weight(1f), stringResource(R.string.video_frame)) {
                            val u = uri ?: return@Chip
                            val at = player.currentPosition
                            scope.launch {
                                val saved = withContext(Dispatchers.IO) { VideoTools.saveFrame(context, u, at, "Eurise_" + System.currentTimeMillis()) }
                                Toast.makeText(context, if (saved != null) R.string.video_frame_done else R.string.action_failed, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                        if (item != null && actions != null) {
                            Action(EuriseIcons.share(), stringResource(R.string.share)) { actions.share(listOf(item)) }
                            if (!item.cloudOnly) Action(EuriseIcons.scissors(), stringResource(R.string.video_cut)) { trimming = true; player.pause(); controls = true }
                            if (!item.cloudOnly) Action(EuriseIcons.heart(if (item.favorite) 3f else 2.3f), stringResource(R.string.favorite)) { actions.setFavorite(item, !item.favorite) }
                            Action(EuriseIcons.trash(), stringResource(R.string.delete)) { player.pause(); actions.trash(listOf(item)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoSurface(player: ExoPlayer, size: VideoSize, fill: Boolean, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val ratio = if (size.width > 0 && size.height > 0) size.width * size.pixelWidthHeightRatio / size.height else 16f / 9f
        val boxRatio = maxWidth.value / maxHeight.value
        // Adatta: tutto il video si vede. Riempi: si ingrandisce finché copre lo schermo.
        val (w, h) = if (ratio > boxRatio) maxWidth to maxWidth / ratio else maxHeight * ratio to maxHeight
        val cover = if (fill) maxOf(maxWidth / w, maxHeight / h) else 1f
        AndroidView(
            factory = { SurfaceView(it).also(player::setVideoSurfaceView) },
            onRelease = { player.clearVideoSurfaceView(it) },
            modifier = Modifier.width(w).height(h).graphicsLayer { scaleX = cover; scaleY = cover },
        )
    }
}

/** Gesti sul video: un livello trasparente sopra la superficie (la SurfaceView non li riceve in Compose). */
@Composable
private fun Gestures(onTap: () -> Unit, onDoubleTap: (Boolean) -> Unit, onVertical: (Boolean, Float) -> Unit, onPinch: (Boolean) -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onDoubleTap = { onDoubleTap(it.x > size.width / 2) }) }
            .pointerInput(Unit) {
                var right = false
                detectVerticalDragGestures(onDragStart = { right = it.x > size.width / 2 }) { change, drag ->
                    change.consume(); onVertical(right, -drag / size.height)
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zoom = 1f
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } >= 2) zoom *= event.calculateZoom()
                    } while (event.changes.any { it.pressed })
                    if (zoom > 1.15f) onPinch(true) else if (zoom < 0.87f) onPinch(false)
                }
            },
    )
}

private fun changeVolume(context: Context, delta: Float): String {
    val audio = context.getSystemService(AudioManager::class.java)
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    acc += delta * max * 1.6f
    val steps = acc.toInt()
    if (steps != 0) {
        acc -= steps
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (audio.getStreamVolume(AudioManager.STREAM_MUSIC) + steps).coerceIn(0, max), 0)
    }
    return context.getString(R.string.video_volume, audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max.coerceAtLeast(1))
}

private var acc = 0f

private fun changeBrightness(context: Context, delta: Float): String {
    val window = context.activity()?.window ?: return ""
    val attrs = window.attributes
    val now = if (attrs.screenBrightness < 0) 0.5f else attrs.screenBrightness
    attrs.screenBrightness = (now + delta * 1.4f).coerceIn(0.02f, 1f)
    window.attributes = attrs
    return context.getString(R.string.video_brightness, (attrs.screenBrightness * 100).toInt())
}

@Composable
private fun Header(title: String, shareable: Boolean, size: VideoSize, duration: Long, onClose: () -> Unit, onPip: () -> Unit, onOther: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Back.copy(alpha = 0.85f), Color.Transparent)))
            .statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(EuriseIcons.chevronLeft(), stringResource(R.string.close), tint = Text1, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Text1, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val quality = if (size.height > 0) "${minOf(size.width, size.height)}p · " else ""
            Text(quality + clock(duration), color = Muted, fontSize = 12.sp)
        }
        IconButton(onClick = onPip) { Icon(EuriseIcons.pip(), stringResource(R.string.video_pip), tint = Text1, modifier = Modifier.size(21.dp)) }
        if (shareable) Box {
            IconButton(onClick = { menu = true }) { Icon(EuriseIcons.more(), stringResource(R.string.more), tint = Text1, modifier = Modifier.size(21.dp)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.video_open_other)) }, onClick = { menu = false; onOther() })
            }
        }
    }
}

@Composable
private fun Round(icon: ImageVector, label: String, size: Int, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Back.copy(alpha = 0.55f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size((size * 0.42f).dp))
    }
}

@Composable
private fun Chip(text: String?, icon: ImageVector?, modifier: Modifier, label: String, on: Boolean = false, onClick: () -> Unit) {
    Box(modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) Color(0xFF2C3A57) else Raised).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (text != null) Text(text, color = if (on) Accent else Text1, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (icon != null) Icon(icon, label, tint = if (on) Accent else Text1, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).height(54.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Text1, modifier = Modifier.size(21.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Text1, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** La barra con le miniature: tocco o trascinamento = vai lì. In taglio, due maniglie segnano il tratto da tenere. */
@Composable
private fun Strip(frames: List<Bitmap>, duration: Long, position: Long, trim: Trim?, modifier: Modifier, onSeek: (Float) -> Unit, onTrim: (Boolean, Float) -> Unit) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier.height(40.dp)) {
        val widthPx = with(density) { maxWidth.toPx() }
        fun fraction(x: Float) = (x / widthPx).coerceIn(0f, 1f)
        var handle by remember { mutableStateOf<Boolean?>(null) }
        Row(
            Modifier.fillMaxSize().padding(vertical = 3.dp).clip(RoundedCornerShape(6.dp))
                .pointerInput(trim != null, duration) {
                    detectTapGestures { if (trim == null) onSeek(fraction(it.x)) }
                }
                .pointerInput(trim != null, duration) {
                    detectDragGestures(
                        onDragStart = { start ->
                            handle = trim?.let { t -> val f = fraction(start.x); kotlin.math.abs(f - t.start) < kotlin.math.abs(f - t.end) }
                            if (trim == null) onSeek(fraction(start.x))
                        },
                        onDragEnd = { handle = null },
                    ) { change, _ ->
                        change.consume()
                        val f = fraction(change.position.x)
                        val h = handle
                        if (h == null) onSeek(f) else onTrim(h, f)
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (frames.isEmpty()) Box(Modifier.fillMaxSize().background(Raised))
            frames.forEach { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxHeight()) }
        }
        if (trim != null) {
            val left = maxWidth * trim.start
            val right = maxWidth * trim.end
            Box(Modifier.width(left).fillMaxHeight().padding(vertical = 3.dp).background(Back.copy(alpha = 0.65f)))
            Box(Modifier.offset(x = right).width(maxWidth - right).fillMaxHeight().padding(vertical = 3.dp).background(Back.copy(alpha = 0.65f)))
            Box(Modifier.offset(x = left).width(right - left).fillMaxHeight().border(3.dp, Accent, RoundedCornerShape(6.dp)))
        }
        if (duration > 0) {
            val x = maxWidth * (position.toFloat() / duration).coerceIn(0f, 1f)
            Box(Modifier.offset(x = x - 2.dp).width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Color.White))
        }
    }
}

@Composable
private fun TrimBar(trim: Trim, duration: Long, onCancel: () -> Unit, onSave: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.video_cut_hint, clock(trim.startMs(duration)), clock(trim.endMs(duration))), color = Muted, fontSize = 12.5.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(23.dp)).background(Raised).clickable(onClick = onCancel), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.cancel), color = Text1, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(23.dp)).background(if (trim.isFull) Raised else Accent).clickable(enabled = !trim.isFull, onClick = onSave), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.video_cut_save), color = if (trim.isFull) Muted else Color(0xFF14203A), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

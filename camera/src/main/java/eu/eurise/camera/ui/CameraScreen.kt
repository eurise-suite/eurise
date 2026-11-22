package eu.eurise.camera.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ZoomState
import androidx.camera.view.video.AudioConfig
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recording
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.eurise.camera.CameraActivity
import eu.eurise.camera.CameraModel
import eu.eurise.camera.R
import eu.eurise.camera.data.MediaOut
import eu.eurise.core.designsystem.component.Jurisdiction
import eu.eurise.core.designsystem.component.PrivacyConsentSheet
import eu.eurise.core.designsystem.icon.EuriseIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/** Come si apre la Fotocamera: avvio normale, dal blocco schermo, o per restituire una foto a un'altra app. */
data class CameraSetup(val startVideo: Boolean = false, val locked: Boolean = false, val capture: Boolean = false)

private enum class Mode { Photo, Video }

internal val Veil = Color(0xB314171C)
internal val Ground = Color(0xFF14171C)
internal val Accent = Color(0xFFA9C1F2)
internal val OnAccent = Color(0xFF14203A)
internal val Soft = Color(0xFFC9C6BF)

@Composable
fun CameraRoute(setup: CameraSetup, shutterKeys: Flow<Unit>, onCaptured: (File) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(has(context, Manifest.permission.CAMERA)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    Box(Modifier.fillMaxSize().background(Ground)) {
        if (granted) CameraScreen(setup, shutterKeys, onCaptured)
        else PermissionScreen { ask.launch(Manifest.permission.CAMERA) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@SuppressLint("MissingPermission")
@Composable
private fun CameraScreen(setup: CameraSetup, shutterKeys: Flow<Unit>, onCaptured: (File) -> Unit) {
    val context = LocalContext.current
    val activity = context as? CameraActivity
    val owner = LocalLifecycleOwner.current
    val model: CameraModel = viewModel()
    val scope = rememberCoroutineScope()
    val executor = remember { ContextCompat.getMainExecutor(context) }

    var mode by remember { mutableStateOf(if (setup.startVideo && !setup.capture) Mode.Video else Mode.Photo) }
    val controller = remember {
        LifecycleCameraController(context).apply {
            /*
             * Scatto rapido ma con la catena delle foto vere (22 novembre 2026). Lo «zero shutter lag» prendeva un fotogramma
             * del mirino, con riduzione del rumore e nitidezza da anteprima: sul Fairphone 6, in casa, grana e aloni.
             * MINIMIZE_LATENCY usa il modello di richiesta per le foto (rumore e bordi in alta qualità) e resta veloce.
             */
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            setEnabledUseCases(if (setup.capture) CameraController.IMAGE_CAPTURE else CameraController.IMAGE_CAPTURE or CameraController.VIDEO_CAPTURE)
        }
    }
    DisposableEffect(owner) {
        controller.bindToLifecycle(owner)
        onDispose { controller.unbind() }
    }
    var zoom by remember { mutableStateOf<ZoomState?>(null) }
    DisposableEffect(owner) {
        val obs = Observer<ZoomState> { zoom = it }
        controller.zoomState.observe(owner, obs)
        onDispose { controller.zoomState.removeObserver(obs) }
    }

    var flash by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
    var torch by remember { mutableStateOf(false) }
    var front by remember { mutableStateOf(false) }
    // In Foto l'esposizione può allungarsi fino a 1/15 s invece di alzare gli ISO; in Video si torna ai 30 fotogrammi fissi.
    LaunchedEffect(mode, front) {
        delay(300) // dopo il cambio di obiettivo la fotocamera si ricollega
        var waited = 0
        while (controller.cameraControl == null && waited++ < 40) delay(50)
        applyFrameRate(controller, still = mode == Mode.Photo)
    }
    var showExposure by remember { mutableStateOf(false) }
    var exposure by remember { mutableFloatStateOf(0f) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var recordedMs by remember { mutableLongStateOf(0L) }
    var blink by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf(false) }
    var destSheet by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<File?>(null) }
    val left = model.leftHanded.value
    val dest = if (setup.locked || setup.capture) null else model.destinations.value.current

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) model.setLocation(true)
    }
    val askAudio = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun takePhoto() {
        if (recording != null) return
        blink = true
        if (setup.capture) {
            val file = File(context.cacheDir, "capture/scatto.jpg").apply { parentFile?.mkdirs(); delete() }
            controller.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(), executor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) { confirm = file }
                override fun onError(exception: ImageCaptureException) = toast(context, R.string.error_save)
            })
            return
        }
        val meta = ImageCapture.Metadata().apply { location = model.location() }
        val options = ImageCapture.OutputFileOptions.Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaOut.photo(dest))
            .setMetadata(meta).build()
        controller.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) { output.savedUri?.let { model.onShot(it, false) } }
            override fun onError(exception: ImageCaptureException) = toast(context, R.string.error_save)
        })
    }

    fun startVideo() {
        if (recording != null || setup.capture) return
        val audio = has(context, Manifest.permission.RECORD_AUDIO)
        if (!audio) askAudio.launch(Manifest.permission.RECORD_AUDIO)
        val options = MediaStoreOutputOptions.Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(MediaOut.video(dest))
            .apply { model.location()?.let { setLocation(it) } }
            .build()
        recordedMs = 0
        recording = runCatching {
            controller.startRecording(options, if (audio) AudioConfig.create(true) else AudioConfig.AUDIO_DISABLED, executor) { event ->
                when (event) {
                    is VideoRecordEvent.Status -> recordedMs = event.recordingStats.recordedDurationNanos / 1_000_000
                    is VideoRecordEvent.Finalize -> {
                        recording = null
                        if (!event.hasError()) model.onShot(event.outputResults.outputUri, true)
                        else if (event.outputResults.outputUri != Uri.EMPTY) model.onShot(event.outputResults.outputUri, true)
                    }
                    else -> Unit
                }
            }
        }.getOrNull()
    }

    fun stopVideo() {
        recording?.stop()
    }

    fun shutter() = when {
        mode == Mode.Video -> if (recording == null) startVideo() else stopVideo()
        else -> takePhoto()
    }

    LaunchedEffect(Unit) { shutterKeys.collect { if (confirm == null && !review) shutter() } }
    LaunchedEffect(blink) { if (blink) { delay(90); blink = false } }

    // Il mirino: 3:4 come la foto, sotto la barra in alto.
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Spacer(Modifier.height(56.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        this.controller = controller
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (blink) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
            ZoomRow(zoom, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) { controller.setZoomRatio(it) }
            if (!setup.capture) {
                DestinationPill(
                    name = dest ?: stringResource(R.string.dest_roll),
                    modifier = Modifier.align(if (left) Alignment.BottomStart else Alignment.BottomEnd).padding(start = 12.dp, end = 12.dp, bottom = 64.dp),
                    onClick = { if (setup.locked) toast(context, R.string.dest_locked) else model.update { it.toggle() } },
                    onLongClick = { if (setup.locked) toast(context, R.string.dest_locked) else destSheet = true },
                )
            }
            if (showExposure) {
                val range = controller.cameraInfo?.exposureState?.exposureCompensationRange
                if (range != null && range.lower < range.upper) {
                    Row(
                        Modifier.align(Alignment.TopCenter).padding(12.dp).clip(RoundedCornerShape(22.dp)).background(Veil).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.exposure), color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        Slider(
                            value = exposure,
                            onValueChange = { v -> exposure = v; controller.cameraControl?.setExposureCompensationIndex(v.toInt()) },
                            valueRange = range.lower.toFloat()..range.upper.toFloat(),
                            steps = (range.upper - range.lower - 1).coerceAtLeast(0),
                            modifier = Modifier.width(200.dp).padding(start = 10.dp),
                            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                        )
                    }
                }
            }
        }
    }

    // In alto: flash, posizione (o stato del blocco schermo), esposizione.
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        RoundButton(
            EuriseIcons.flash(),
            stringResource(
                when {
                    mode == Mode.Video -> if (torch) R.string.torch_on else R.string.torch_off
                    flash == ImageCapture.FLASH_MODE_AUTO -> R.string.flash_auto
                    flash == ImageCapture.FLASH_MODE_ON -> R.string.flash_on
                    else -> R.string.flash_off
                },
            ),
            active = (mode == Mode.Video && torch) || (mode == Mode.Photo && flash != ImageCapture.FLASH_MODE_OFF),
        ) {
            if (mode == Mode.Video) { torch = !torch; controller.enableTorch(torch) }
            else {
                flash = when (flash) {
                    ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                    ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                    else -> ImageCapture.FLASH_MODE_OFF
                }
                controller.imageCaptureFlashMode = flash
            }
        }
        when {
            recording != null -> StatusChip(stringResource(R.string.recording, clock(recordedMs)), dot = Color(0xFFE46F5A))
            setup.locked -> StatusChip(stringResource(R.string.locked_status), icon = EuriseIcons.lock())
            setup.capture -> Spacer(Modifier.size(1.dp))
            else -> LocationChip(model.locationOn.value) {
                if (model.locationOn.value) model.setLocation(false) else consent = true
            }
        }
        RoundButton(EuriseIcons.sun(), stringResource(R.string.exposure), active = showExposure) { showExposure = !showExposure }
    }

    // In basso: modalità e la fila dello scatto (specchiata per i mancini).
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 18.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (!setup.capture) {
            val scanner = remember(setup.locked) { if (setup.locked) null else model.scannerIntent() }
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.Center) {
                ModeChip(stringResource(R.string.mode_video), mode == Mode.Video) { if (recording == null) { mode = Mode.Video; flashOffFor(controller) } }
                ModeChip(stringResource(R.string.mode_photo), mode == Mode.Photo) { if (recording == null) { mode = Mode.Photo; torch = false; controller.enableTorch(false) } }
                if (scanner != null) ModeChip(stringResource(R.string.mode_document), false) { runCatching { context.startActivity(scanner) } }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val thumb: @Composable () -> Unit = {
                val last = model.session.firstOrNull()
                Box(
                    Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, Color.White, RoundedCornerShape(12.dp))
                        .background(Color(0xFF2A2D33))
                        .semantics { contentDescription = context.getString(R.string.last_photo) }
                        .pointerInput(last) { detectTapGestures { if (last != null) review = true } },
                ) {
                    last?.thumb?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
            }
            val flip: @Composable () -> Unit = {
                RoundButton(EuriseIcons.rotate(), stringResource(R.string.flip), size = 54) {
                    val next = if (front) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                    if (recording == null && runCatching { controller.hasCamera(next) }.getOrDefault(false)) {
                        controller.cameraSelector = next
                        front = !front
                    }
                }
            }
            if (left) flip() else if (setup.capture) Spacer(Modifier.size(54.dp)) else thumb()
            Shutter(
                video = mode == Mode.Video,
                recording = recording != null,
                onTap = { shutter() },
                onHoldStart = { if (mode == Mode.Photo && !setup.capture) startVideo() },
                onHoldEnd = { if (mode == Mode.Photo) stopVideo() },
                scope = scope,
            )
            if (left) { if (setup.capture) Spacer(Modifier.size(54.dp)) else thumb() } else flip()
        }
    }

    if (review) {
        ReviewScreen(
            model = model,
            locked = setup.locked,
            onClose = { review = false },
            onUnlockThen = { action -> activity?.afterUnlock(action) ?: action() },
        )
    }
    confirm?.let { file ->
        CaptureConfirm(file, onRetake = { file.delete(); confirm = null }, onUse = { confirm = null; onCaptured(file) })
    }
    if (destSheet) DestinationSheet(model, onDismiss = { destSheet = false })
    if (consent) {
        PrivacyConsentSheet(
            title = stringResource(R.string.location_title),
            read = stringResource(R.string.location_read),
            out = stringResource(R.string.location_out),
            who = stringResource(R.string.location_who),
            back = stringResource(R.string.location_back),
            score = 7,
            where = Jurisdiction.Phone,
            onDismiss = { consent = false },
            onAccept = {
                consent = false
                askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
        )
    }
}

private fun flashOffFor(controller: LifecycleCameraController) {
    controller.imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
}

/** Il pulsante di scatto: tocco = foto (o avvio e fine del video), pressione lunga in Foto = video finché tieni. */
@Composable
private fun Shutter(
    video: Boolean,
    recording: Boolean,
    onTap: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val label = stringResource(if (video) (if (recording) R.string.shutter_video_stop else R.string.shutter_video_start) else R.string.shutter)
    val ring = if (video || recording) Color(0xFFE46F5A) else Color.White
    Box(
        Modifier.size(80.dp).clip(CircleShape).border(4.dp, ring, CircleShape).padding(7.dp)
            .semantics { role = Role.Button; contentDescription = label; onClick(label) { onTap(); true } }
            .pointerInput(video) {
                detectTapGestures(onPress = {
                    if (video) {
                        if (tryAwaitRelease()) onTap()
                        return@detectTapGestures
                    }
                    var held = false
                    val timer = scope.launch { delay(380); held = true; onHoldStart() }
                    val released = tryAwaitRelease()
                    timer.cancel()
                    if (held) onHoldEnd() else if (released) onTap()
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(if (recording) 28.dp else 62.dp)
                .clip(if (recording) RoundedCornerShape(6.dp) else CircleShape)
                .background(if (video || recording) Color(0xFFE46F5A) else Color.White),
        )
    }
}

@Composable
private fun ZoomRow(zoom: ZoomState?, modifier: Modifier, onZoom: (Float) -> Unit) {
    val z = zoom ?: return
    val stops = zoomStops(z.minZoomRatio, z.maxZoomRatio)
    if (stops.size < 2) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        stops.forEach { stop ->
            val near = nearestStop(stops, z.zoomRatio) == stop
            val label = if (near) zoomLabel(z.zoomRatio) else zoomLabel(stop)
            Box(
                Modifier.size(if (near) 44.dp else 38.dp).clip(CircleShape).background(if (near) Color(0xD914171C) else Veil)
                    .semantics { role = Role.Button; contentDescription = label + "×" }
                    .pointerInput(stop) { detectTapGestures { onZoom(stop) } },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (near) label + "×" else label, color = if (near) Accent else Color(0xFFECE9E3), fontSize = if (near) 13.5.sp else 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DestinationPill(name: String, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val label = stringResource(R.string.dest_label, name)
    Row(
        modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Veil)
            .combinedClickable(onClickLabel = label, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(EuriseIcons.folder(), null, tint = Accent, modifier = Modifier.size(18.dp))
        Text(name, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Icon(EuriseIcons.chevronDown(), null, tint = Soft, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun LocationChip(on: Boolean, onClick: () -> Unit) {
    val text = stringResource(if (on) R.string.location_on else R.string.location_off)
    Row(
        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(if (on) Color(0xFF2C3A57) else Veil)
            .semantics { role = Role.Switch; contentDescription = text; onClick(text) { onClick(); true } }
            .pointerInput(on) { detectTapGestures { onClick() } }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(EuriseIcons.place(), null, tint = if (on) Accent else Color(0xFFECE9E3), modifier = Modifier.size(15.dp))
        Text(text, color = if (on) Accent else Color(0xFFECE9E3), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun StatusChip(text: String, icon: ImageVector? = null, dot: Color? = null) {
    Row(
        Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(Veil).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(15.dp))
        Text(text, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 3.dp).height(34.dp).clip(RoundedCornerShape(17.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .semantics { role = Role.RadioButton; contentDescription = text; onClick(text) { onClick(); true } }
            .pointerInput(text) { detectTapGestures { onClick() } }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color(0xFF1E1D1B) else Soft, fontSize = 13.5.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold)
    }
}

@Composable
internal fun RoundButton(icon: ImageVector, label: String, active: Boolean = false, size: Int = 44, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size.dp).clip(CircleShape).background(if (active) Color(0xFF2C3A57) else Veil),
    ) {
        Icon(icon, label, tint = if (active) Accent else Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun PermissionScreen(onAllow: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(EuriseIcons.camera(), null, tint = Accent, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.permission_title), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.permission_text), color = Soft, fontSize = 14.sp)
        Spacer(Modifier.height(20.dp))
        PillButton(stringResource(R.string.permission_allow), primary = true, onClick = onAllow)
    }
}

@Composable
internal fun PillButton(text: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (primary) Accent else Color(0x1FFFFFFF),
            contentColor = if (primary) OnAccent else Color.White,
        ),
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}

/** Le pillole dello zoom: il grandangolo se c'è, 1×, 2× e 5× dove il telefono ci arriva. */
internal fun zoomStops(min: Float, max: Float): List<Float> = buildList {
    if (min < 0.95f) add(min)
    add(1f)
    if (max >= 2f) add(2f)
    if (max >= 5f) add(5f)
}

internal fun nearestStop(stops: List<Float>, ratio: Float): Float = stops.minBy { kotlin.math.abs(it - ratio) }

internal fun zoomLabel(r: Float): String {
    val rounded = Math.round(r * 10) / 10f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else String.format(Locale.ITALY, "%.1f", rounded)
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
}

private fun has(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

internal fun toast(context: Context, text: Int) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

/**
 * Gamma di fotogrammi al secondo chiesta all'esposizione automatica. Per le foto la più ampia che arriva a 30 (es. 15-30):
 * con poca luce il telefono allunga il tempo invece di alzare gli ISO, meno rumore. Per il video nessuna richiesta (la
 * sceglie CameraX, fissa). Si sceglie solo tra le gamme che la fotocamera dichiara: niente valori inventati.
 */
@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
private fun applyFrameRate(controller: CameraController, still: Boolean) {
    val control = controller.cameraControl ?: return
    val info = controller.cameraInfo ?: return
    val options = CaptureRequestOptions.Builder()
    if (still) {
        val ranges = Camera2CameraInfo.from(info).getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        val best = ranges?.filter { it.upper == 30 && it.lower >= 10 }?.minByOrNull { it.lower } ?: return
        options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, best)
    }
    runCatching { Camera2CameraControl.from(control).setCaptureRequestOptions(options.build()) }
}

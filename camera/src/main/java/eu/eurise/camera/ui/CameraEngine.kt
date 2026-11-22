package eu.eurise.camera.ui

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.util.Log
import android.util.Range
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import eu.eurise.camera.TAG
import java.util.concurrent.Executor

/**
 * La fotocamera vera e propria, su ProcessCameraProvider con i suoi casi d'uso (mirino, foto, video) al posto del
 * controller di CameraX: serve a leggere tempo e ISO dell'esposizione automatica fotogramma per fotogramma.
 *
 * Meno rumore in casa (22 novembre 2026). Sul Fairphone 6, in interni, l'esposizione automatica resta a 1/100 s e alza gli
 * ISO (475-751): grana. Allo scatto, se la luce è poca, la foto si fa con un tempo più lungo (fino a 1/30 s, 1/60 s da 2×
 * in su) e ISO più bassi in proporzione: stessa luminosità, circa due stop di rumore in meno. Solo dove la fotocamera
 * dichiara il controllo manuale del sensore, mai con il flash; altrove resta l'automatico.
 */
@SuppressLint("UnsafeOptInUsageError")
@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
class CameraEngine(private val context: Context) {
    private val main: Executor = ContextCompat.getMainExecutor(context)
    private var provider: ProcessCameraProvider? = null
    private var owner: LifecycleOwner? = null
    private var withVideo = true

    /** La fotocamera collegata ora (cambia con l'obiettivo): Compose la osserva per zoom ed esposizione. */
    var camera by mutableStateOf<Camera?>(null)
        private set

    /** Tempo (ns) e ISO dell'ultimo fotogramma del mirino. */
    @Volatile var exposureNs = 0L
        private set
    @Volatile var iso = 0
        private set
    /** Valori mostrati nel pannello dell'esposizione, aggiornati ogni mezzo secondo. */
    var meter by mutableStateOf("")
        private set
    private var lastMeter = 0L

    var cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        set(value) { field = value; rebind() }

    private var still = true

    private val callback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            val e = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return
            val s = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: return
            exposureNs = e; iso = s
            val now = System.currentTimeMillis()
            if (now - lastMeter > 500) {
                lastMeter = now
                val text = "ISO $s · ${shutterLabel(e)}"
                main.execute { meter = text }
            }
        }
    }

    private val preview: Preview = Preview.Builder().also { Camera2Interop.Extender(it).setSessionCaptureCallback(callback) }.build()
    private val imageCapture: ImageCapture = ImageCapture.Builder()
        // La catena delle foto vere (rumore e bordi in alta qualità), non un fotogramma del mirino.
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()
    private val recorder: Recorder = Recorder.Builder().build()
    private val videoCapture: VideoCapture<Recorder> = VideoCapture.withOutput(recorder)

    var flashMode: Int
        get() = imageCapture.flashMode
        set(value) { imageCapture.flashMode = value }

    fun surface(view: androidx.camera.view.PreviewView) = preview.setSurfaceProvider(view.surfaceProvider)

    fun bind(owner: LifecycleOwner, video: Boolean) {
        this.owner = owner
        withVideo = video
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ provider = runCatching { future.get() }.getOrNull(); rebind() }, main)
    }

    fun unbind() {
        provider?.unbindAll()
        camera = null
        owner = null
    }

    fun hasCamera(selector: CameraSelector): Boolean = provider?.hasCamera(selector) == true

    private fun rebind() {
        val p = provider ?: return
        val o = owner ?: return
        p.unbindAll()
        val cases = if (withVideo) arrayOf(preview, imageCapture, videoCapture) else arrayOf(preview, imageCapture)
        camera = runCatching { p.bindToLifecycle(o, cameraSelector, *cases) }
            .recoverCatching { Log.w(TAG, "Mirino, foto e video insieme non riescono: solo mirino e foto", it); p.bindToLifecycle(o, cameraSelector, preview, imageCapture) }
            .getOrNull()
        applyBase()
    }

    /** Foto o video: per le foto l'esposizione automatica può scendere fino a 15 fotogrammi al secondo. */
    fun setStill(value: Boolean) {
        still = value
        applyBase()
    }

    fun setZoomRatio(ratio: Float) { camera?.cameraControl?.setZoomRatio(ratio) }
    fun enableTorch(on: Boolean) { camera?.cameraControl?.enableTorch(on) }
    fun setExposureIndex(index: Int) { camera?.cameraControl?.setExposureCompensationIndex(index) }

    private fun characteristic(key: CameraCharacteristics.Key<*>): Any? {
        val info = camera?.cameraInfo ?: return null
        return runCatching { Camera2CameraInfo.from(info).getCameraCharacteristic(key) }.getOrNull()
    }

    private fun baseOptions(): CaptureRequestOptions.Builder {
        val options = CaptureRequestOptions.Builder()
        if (still) {
            @Suppress("UNCHECKED_CAST")
            val ranges = characteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) as? Array<Range<Int>>
            ranges?.filter { it.upper == 30 && it.lower >= 10 }?.minByOrNull { it.lower }
                ?.let { options.setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
        }
        return options
    }

    private fun applyBase() {
        val control = camera?.cameraControl ?: return
        runCatching { Camera2CameraControl.from(control).setCaptureRequestOptions(baseOptions().build()) }
    }

    private fun manualSensor(): Boolean {
        val caps = characteristic(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES) as? IntArray ?: return false
        return CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in caps
    }

    /**
     * Tempo e ISO per la foto con poca luce, o null se va bene l'automatico. Il prodotto tempo × ISO del mirino, con un
     * eventuale correzione [BIAS], si distribuisce allungando il
     * tempo finché gli ISO scendono verso [TARGET_ISO]. Tetto del tempo: 1/20 s con lo stabilizzatore ottico dichiarato, 1/25 s senza (sul Fairphone 6 non risulta: le foto si fermavano a 1/30 s),
     * la metà da 2× in su.
     */
    private fun lowLight(zoom: Float): Pair<Long, Int>? {
        val e = exposureNs; val s = iso
        if (e <= 0 || s < 320 || flashMode != ImageCapture.FLASH_MODE_OFF || !manualSensor()) return null
        val ois = (characteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION) as? IntArray)
            ?.contains(CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON) == true
        val base = if (ois) 50_000_000L else 40_000_000L
        val cap = if (zoom >= 2f) base / 2 else base
        @Suppress("UNCHECKED_CAST")
        val maxExposure = (characteristic(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) as? Range<Long>)?.upper ?: return null
        @Suppress("UNCHECKED_CAST")
        val isoRange = characteristic(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) as? Range<Int> ?: return null
        val total = e.toDouble() * s * BIAS
        val target = minOf(cap, maxExposure, (total / TARGET_ISO).toLong())
        if (target < e * 13 / 10) return null
        val newIso = (total / target).toInt().coerceIn(isoRange.lower, isoRange.upper)
        return target to newIso
    }

    /**
     * Esegue [shoot] con tempo e ISO della poca luce, se servono, e poi rimette l'automatico: [shoot] riceve la funzione da
     * chiamare a scatto finito (riuscito o no).
     */
    private fun withLowLight(zoom: Float, shoot: (restore: () -> Unit) -> Unit) {
        val control = camera?.cameraControl
        val manual = if (control != null) lowLight(zoom) else null
        if (control == null || manual == null) { shoot {}; return }
        val (exposure, sensitivity) = manual
        Log.i(TAG, "Poca luce: ISO $iso · ${shutterLabel(exposureNs)} → ISO $sensitivity · ${shutterLabel(exposure)}")
        val manualOptions = baseOptions()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure)
            .setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, sensitivity)
            .setCaptureRequestOption(CaptureRequest.SENSOR_FRAME_DURATION, maxOf(exposure, 33_333_333L))
            .build()
        val set = runCatching { Camera2CameraControl.from(control).setCaptureRequestOptions(manualOptions) }.getOrNull()
        if (set == null) { shoot {}; return }
        set.addListener({ shoot { applyBase() } }, main)
    }

    fun takePicture(options: ImageCapture.OutputFileOptions, zoom: Float, executor: Executor, done: ImageCapture.OnImageSavedCallback) {
        withLowLight(zoom) { restore ->
            imageCapture.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) { restore(); done.onImageSaved(output) }
                override fun onError(exception: ImageCaptureException) { restore(); done.onError(exception) }
            })
        }
    }

    /** Fotocamera frontale? */
    val front: Boolean get() = camera?.cameraInfo?.lensFacing == CameraSelector.LENS_FACING_FRONT

    /**
     * Quanti fotogrammi per la Notte, o null se basta uno scatto: con il flash spento e ISO del mirino da [NIGHT_ISO] in
     * su (in Foto, da sola) 4 sulla posteriore, 6 sulla frontale (sensore più piccolo). Nella modalità Notte sempre, con [nightModeFrames].
     */
    fun nightFrames(): Int? {
        if (flashMode != ImageCapture.FLASH_MODE_OFF || iso < NIGHT_ISO) return null
        return if (front) 6 else 4
    }

    /** Fotogrammi della modalità Notte scelta a mano: sempre la raffica, più lunga. */
    fun nightModeFrames(): Int = if (front) 8 else 6

    /** Raffica di [n] JPEG in memoria, uno dopo l'altro, con la stessa esposizione. */
    fun takeBurst(n: Int, zoom: Float, done: (List<ByteArray>) -> Unit, failed: () -> Unit) {
        withLowLight(zoom) { restore ->
            val frames = ArrayList<ByteArray>(n)
            fun next() {
                imageCapture.takePicture(main, object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                        val bytes = runCatching {
                            val buffer = image.planes[0].buffer
                            ByteArray(buffer.remaining()).also { buffer.get(it) }
                        }.getOrNull()
                        image.close()
                        if (bytes != null) frames += bytes
                        if (frames.size < n && bytes != null) next()
                        else { restore(); if (frames.isNotEmpty()) done(frames) else failed() }
                    }
                    override fun onError(exception: ImageCaptureException) {
                        restore(); if (frames.isNotEmpty()) done(frames) else failed()
                    }
                })
            }
            next()
        }
    }

    @SuppressLint("MissingPermission")
    fun startRecording(options: MediaStoreOutputOptions, audio: Boolean, executor: Executor, listener: Consumer<VideoRecordEvent>): Recording =
        recorder.prepareRecording(context, options).apply { if (audio) withAudioEnabled() }.start(executor, listener)

    companion object {
        /** Gli ISO a cui si punta con poca luce: sotto, sul sensore del Fairphone 6, la grana quasi non si vede. */
        const val TARGET_ISO = 200
        /** Nessuno stop in più rispetto al mirino: schiariva troppo e mostrava la grana (23 novembre); le ombre le apre il Ritocco. */
        const val BIAS = 1.0
        /** Da quanti ISO del mirino in su si scatta la Notte con più fotogrammi. */
        const val NIGHT_ISO = 800
    }
}

/** «1/100 s», «1/30 s», «0,5 s». */
internal fun shutterLabel(ns: Long): String =
    if (ns >= 300_000_000L) String.format(java.util.Locale.ITALY, "%.1f s", ns / 1e9) else "1/${Math.round(1e9 / ns.coerceAtLeast(1))} s"

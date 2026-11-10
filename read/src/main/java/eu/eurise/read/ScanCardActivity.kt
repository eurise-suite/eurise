package eu.eurise.read

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.googlecode.tesseract.android.TessBaseAPI
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.core.suite.CardContract
import eu.eurise.core.suite.CardFields
import eu.eurise.core.suite.CardText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Legge una carta di pagamento inquadrandola, per Eurise Password (solo app con la stessa firma possono aprirla).
 * I fotogrammi si leggono sul telefono con Tesseract, ritagliati alla sagoma della carta, e non si salvano mai.
 * Quando lo stesso numero valido torna due volte si propone il risultato; scadenza e intestatario si aggiungono
 * appena compaiono. Il risultato torna a Password; Leggi non tiene nulla.
 */
class ScanCardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { EuriseTheme(darkTheme = true) { ScanCard(onDone = ::finishWith, onCancel = ::finish) } }
    }

    private fun finishWith(card: CardFields) {
        setResult(RESULT_OK, Intent()
            .putExtra(CardContract.EXTRA_NUMBER, card.number)
            .putExtra(CardContract.EXTRA_EXPIRY, card.expiry)
            .putExtra(CardContract.EXTRA_HOLDER, card.holder))
        finish()
    }
}

/** Le proporzioni di una carta di pagamento (ISO/IEC 7810 ID-1). */
private const val CARD_RATIO = 85.6f / 53.98f

@Composable
private fun ScanCard(onDone: (CardFields) -> Unit, onCancel: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var allowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var model by remember { mutableStateOf(TessModel.ready(context)) }
    var downloading by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<CardFields?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = it }
    LaunchedEffect(Unit) { if (!allowed) ask.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0D10))) {
        if (allowed && model) CameraReader(onCard = { card -> found = merge(found, card) })
        Frame()
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.card_title), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.card_hint), color = Color(0xFFC9CDD4), fontSize = 13.5.sp)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(20.dp)
                .clip(RoundedCornerShape(22.dp)).background(Color(0xFF14171C)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                !allowed -> Text(stringResource(R.string.card_need_camera), color = Color.White, fontSize = 14.sp)
                !model -> {
                    Text(stringResource(R.string.read_download_text), color = Color.White, fontSize = 13.5.sp)
                    Pill(stringResource(if (downloading) R.string.read_downloading else R.string.read_download), primary = true) {
                        if (downloading) return@Pill
                        downloading = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { runCatching { TessModel.download(TessModel.file(context)) }.isSuccess }
                            downloading = false
                            model = ok && TessModel.ready(context)
                        }
                    }
                }
                else -> {
                    val card = found
                    Text(card?.number ?: stringResource(R.string.card_searching), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    if (card != null) Text(
                        listOf(card.expiry.ifEmpty { "—/—" }, card.holder.ifEmpty { stringResource(R.string.card_no_holder) }).joinToString(" · "),
                        color = Color(0xFFC9CDD4), fontSize = 13.5.sp,
                    )
                    if (card != null) Pill(stringResource(R.string.card_use), primary = true) { onDone(card) }
                }
            }
            Pill(stringResource(R.string.read_cancel), primary = false, onClick = onCancel)
            Text(stringResource(R.string.card_privacy), color = Color(0xFF8D939C), fontSize = 11.5.sp)
        }
    }
}

/** Tiene il numero già trovato e aggiunge ciò che mancava (scadenza, intestatario) dai fotogrammi successivi. */
private fun merge(old: CardFields?, new: CardFields): CardFields =
    if (old == null || old.number != new.number) new
    else old.copy(expiry = old.expiry.ifEmpty { new.expiry }, holder = old.holder.ifEmpty { new.holder })

@Composable
private fun Pill(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(23.dp)).background(if (primary) Color(0xFFA9C1F2) else Color(0xFF262B33)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (primary) Color(0xFF14203A) else Color.White, fontSize = 14.5.sp, fontWeight = FontWeight.ExtraBold) }
}

/** La sagoma della carta: fuori velato, dentro trasparente, bordo arrotondato. */
@Composable
private fun Frame() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width * 0.86f
        val h = w / CARD_RATIO
        val left = (size.width - w) / 2
        val top = size.height * 0.42f - h / 2
        val r = 18.dp.toPx()
        val hole = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(left, top, left + w, top + h, CornerRadius(r))) }
        clipPath(hole, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.55f)) }
        drawRoundRect(Color.White, Offset(left, top), Size(w, h), CornerRadius(r), style = Stroke(2.5.dp.toPx()))
    }
}

/**
 * Fotocamera e lettura. Ogni fotogramma (uno alla volta, gli altri si saltano) viene girato, ritagliato alla sagoma
 * e letto; un numero vale solo se torna uguale due volte.
 */
@Composable
private fun CameraReader(onCard: (CardFields) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val owner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val tess = remember {
        TessBaseAPI().apply {
            init(TessModel.dir(context).absolutePath, "ita")
            pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
            setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789/ ABCDEFGHIJKLMNOPQRSTUVWXYZ'.-")
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            executor.execute { tess.recycle() }
            executor.shutdown()
        }
    }
    var last: String? = null
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val view = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            val future = ProcessCameraProvider.getInstance(ctx)
            future.addListener({
                val provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                @Suppress("DEPRECATION")
                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(1280, 960))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                analysis.setAnalyzer(executor) { image ->
                    val card = runCatching { read(tess, image, view.width, view.height) }.getOrNull()
                    image.close()
                    if (card != null) {
                        if (card.number == last) view.post { onCard(card) }
                        last = card.number
                    }
                }
                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
    )
}

private fun read(tess: TessBaseAPI, image: ImageProxy, viewW: Int, viewH: Int): CardFields? {
    val frame = image.toBitmap()
    val rotation = image.imageInfo.rotationDegrees
    val upright = if (rotation == 0) frame else Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
    // La sagoma è disegnata sullo schermo; l'anteprima riempie lo schermo tagliando l'immagine (FILL_CENTER):
    // si riporta la sagoma nelle misure dell'immagine, con un po' di margine attorno.
    val iw = upright.width.toFloat()
    val ih = upright.height.toFloat()
    val vw = viewW.takeIf { it > 0 }?.toFloat() ?: iw
    val vh = viewH.takeIf { it > 0 }?.toFloat() ?: ih
    val scale = maxOf(vw / iw, vh / ih)
    val cardW = vw * 0.86f
    val cardH = cardW / CARD_RATIO
    val cx = iw / 2
    val cy = ih / 2 + (vh * 0.42f - vh / 2) / scale
    val w = (cardW / scale * 1.08f).coerceAtMost(iw)
    val h = (cardH / scale * 1.12f).coerceAtMost(ih)
    val left = (cx - w / 2).toInt().coerceIn(0, (iw - w).toInt())
    val top = (cy - h / 2).toInt().coerceIn(0, (ih - h).toInt())
    val crop = Bitmap.createBitmap(upright, left, top, w.toInt(), h.toInt())
    tess.setImage(crop)
    val text = tess.utF8Text.orEmpty()
    tess.clear()
    return CardText.parse(text)
}

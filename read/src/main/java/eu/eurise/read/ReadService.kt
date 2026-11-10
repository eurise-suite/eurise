package eu.eurise.read

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import eu.eurise.core.suite.ReadContract
import eu.eurise.read.ocr.Ocr

/**
 * Servizio di lettura per l'app Eurise ([ReadContract]): riceve gli indirizzi delle pagine, le legge con Tesseract su un
 * thread suo e risponde. Protetto dal permesso della suite (solo app con la stessa firma). Le immagini non si salvano.
 */
class ReadService : Service() {
    private lateinit var thread: HandlerThread
    private lateinit var messenger: Messenger
    private var ocr: Ocr? = null

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("eurise-read").apply { start() }
        messenger = Messenger(Handler(thread.looper) { msg -> if (msg.what == ReadContract.MSG_READ) handle(msg); true })
    }

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onDestroy() {
        val engine = ocr
        ocr = null
        Handler(thread.looper).post { engine?.close() }
        thread.quitSafely()
        super.onDestroy()
    }

    private fun handle(msg: Message) {
        val replyTo = msg.replyTo ?: return
        val data = msg.data.apply { classLoader = Uri::class.java.classLoader }
        @Suppress("DEPRECATION")
        val pages = data.getParcelableArrayList<Uri>(ReadContract.KEY_PAGES).orEmpty().take(ReadContract.MAX_PAGES)
        val out = Bundle()
        val engine = ocr ?: TessModel.languages(this)?.let { Ocr.open(TessModel.dataDir(this), it) }?.also { ocr = it }
        if (engine == null) {
            out.putString(ReadContract.KEY_ERROR, if (TessModel.ready(this)) ReadContract.ERROR_FAILED else ReadContract.ERROR_NO_MODEL)
        } else {
            val texts = ArrayList<String>()
            val words = ArrayList<String>()
            val sizes = IntArray(pages.size * 2)
            var failed = false
            pages.forEachIndexed { i, uri ->
                val bitmap = runCatching { decode(uri) }.getOrNull()
                if (bitmap == null) { failed = true; return@forEachIndexed }
                runCatching {
                    texts += engine.read(bitmap)
                    words += engine.words()
                    sizes[2 * i] = bitmap.width
                    sizes[2 * i + 1] = bitmap.height
                    engine.clear()
                }.onFailure { failed = true }
                bitmap.recycle()
            }
            if (failed || texts.size != pages.size) out.putString(ReadContract.KEY_ERROR, ReadContract.ERROR_FAILED)
            else {
                out.putStringArrayList(ReadContract.KEY_TEXTS, texts)
                out.putStringArrayList(ReadContract.KEY_WORDS, words)
                out.putIntArray(ReadContract.KEY_SIZES, sizes)
            }
        }
        try {
            replyTo.send(Message.obtain(null, ReadContract.MSG_RESULT).apply { this.data = out })
        } catch (_: RemoteException) {
            // L'app che ha chiesto non c'è più: niente da fare.
        }
    }

    /** L'immagine ridotta se serve (lato lungo fino a [MAX_SIDE] pixel: oltre non migliora la lettura e pesa in memoria). */
    private fun decode(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (longSide <= 0) return null
        var sample = 1
        while (longSide / (sample * 2) >= MAX_SIDE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private companion object {
        const val MAX_SIDE = 3500
    }
}

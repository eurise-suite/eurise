package eu.eurise.ai

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import eu.eurise.core.suite.BrainContract
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Il cervello di Alfred (vedi [BrainContract]). Il modello si carica alla prima richiesta e resta in memoria finché
 * Alfred è collegato; tutto avviene sul telefono: questa app non ha il permesso Internet.
 */
class BrainService : Service() {

    private lateinit var thread: HandlerThread
    private lateinit var inbox: HandlerThread
    private lateinit var engineHandler: Handler
    private lateinit var messenger: Messenger
    private var ai: Ai? = null
    private var loadedName: String? = null
    private val stop = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("eurise-brain").apply { start() }
        inbox = HandlerThread("eurise-brain-inbox").apply { start() }
        engineHandler = Handler(thread.looper)
        // I messaggi arrivano su un thread loro: MSG_STOP ferma subito una risposta in corso sul thread del motore.
        messenger = Messenger(Handler(inbox.looper) { msg ->
            if (msg.what == BrainContract.MSG_STOP) stop.set(true)
            else Message.obtain(msg).let { copy -> engineHandler.post { runCatching { handle(copy) } } }
            true
        })
    }

    override fun onBind(intent: Intent?): IBinder {
        // Si comincia a caricare subito: mentre Alfred ascolta, il modello è già pronto.
        engineHandler.post { runCatching { engine() } }
        return messenger.binder
    }

    override fun onDestroy() {
        stop.set(true)
        inbox.quitSafely()
        engineHandler.post { ai?.close(); ai = null; thread.quitSafely() }
        super.onDestroy()
    }

    private fun handle(msg: Message) {
        when (msg.what) {
            BrainContract.MSG_STATUS -> reply(msg, BrainContract.MSG_STATUS, status())
            BrainContract.MSG_INSTALL -> {
                val pfd = msg.data.getParcelableCompat<ParcelFileDescriptor>(BrainContract.KEY_FILE)
                val name = msg.data.getString(BrainContract.KEY_NAME).orEmpty()
                val ok = pfd != null && Models.install(this, name, pfd)
                if (ok) Models.use(this, name)
                reply(msg, BrainContract.MSG_STATUS, status().apply { if (!ok) putString(BrainContract.KEY_ERROR, BrainContract.ERROR_FAILED) })
            }
            BrainContract.MSG_USE -> {
                Models.use(this, msg.data.getString(BrainContract.KEY_NAME).orEmpty())
                reply(msg, BrainContract.MSG_STATUS, status())
            }
            BrainContract.MSG_REMOVE -> {
                val name = msg.data.getString(BrainContract.KEY_NAME).orEmpty()
                if (name == loadedName) { ai?.close(); ai = null; loadedName = null }
                Models.remove(this, name)
                reply(msg, BrainContract.MSG_STATUS, status())
            }
            BrainContract.MSG_COMMAND -> {
                val e = engine() ?: return fail(msg, BrainContract.MSG_COMMAND, BrainContract.ERROR_NO_MODEL)
                val date = msg.data.getString(BrainContract.KEY_DATE).orEmpty().split('-').mapNotNull { it.toIntOrNull() }
                if (date.size != 3) return fail(msg, BrainContract.MSG_COMMAND, BrainContract.ERROR_FAILED)
                val t0 = System.currentTimeMillis()
                val a = e.command(msg.data.getString(BrainContract.KEY_TEXT).orEmpty(), msg.data.getString(BrainContract.KEY_HINTS).orEmpty(),
                    date[0], date[1], date[2])
                if (!a.ok) return fail(msg, BrainContract.MSG_COMMAND, BrainContract.ERROR_FAILED)
                reply(msg, BrainContract.MSG_COMMAND, Bundle().apply {
                    putString(BrainContract.KEY_TEXT, a.text)
                    putLong(BrainContract.KEY_MS, System.currentTimeMillis() - t0)
                })
            }
            BrainContract.MSG_ASK -> {
                val e = engine() ?: return fail(msg, BrainContract.MSG_DONE, BrainContract.ERROR_NO_MODEL)
                stop.set(false)
                val a = e.askStream(msg.data.getString(BrainContract.KEY_TEXT).orEmpty(), 320) { piece ->
                    reply(msg, BrainContract.MSG_PIECE, Bundle().apply { putString(BrainContract.KEY_TEXT, piece) })
                    !stop.get()
                }
                reply(msg, BrainContract.MSG_DONE, Bundle().apply {
                    putString(BrainContract.KEY_TEXT, a.text)
                    if (!a.ok) putString(BrainContract.KEY_ERROR, BrainContract.ERROR_FAILED)
                })
            }
        }
    }

    /** Il motore con il modello scelto; null se non c'è un modello. */
    private fun engine(): Ai? {
        val name = Models.current(this) ?: return null
        if (ai != null && loadedName == name) return ai
        ai?.close()
        ai = Ai.load(Models.file(this, name).absolutePath, Ai.defaultThreads())
        loadedName = if (ai != null) name else null
        return ai
    }

    private fun status() = Bundle().apply {
        val current = Models.current(this@BrainService)
        putBoolean(BrainContract.KEY_READY, current != null)
        putString(BrainContract.KEY_MODEL, current)
        putStringArrayList(BrainContract.KEY_MODELS, ArrayList(Models.installed(this@BrainService)))
    }

    private fun fail(msg: Message, what: Int, error: String) =
        reply(msg, what, Bundle().apply { putString(BrainContract.KEY_ERROR, error) })

    private fun reply(msg: Message, what: Int, data: Bundle) {
        val to = msg.replyTo ?: return
        runCatching { to.send(Message.obtain(null, what).apply { this.data = data }) }
    }
}

/** I modelli installati: `models/<nome>.gguf`; il vecchio `model.gguf` dell'app di prova vale come «model». */
internal object Models {
    private fun dir(context: Context) = File(context.filesDir, "models").apply { mkdirs() }
    private fun prefs(context: Context) = context.getSharedPreferences("brain", Context.MODE_PRIVATE)

    fun file(context: Context, name: String) = File(dir(context), "$name.gguf")

    fun installed(context: Context): List<String> =
        dir(context).listFiles { f -> f.name.endsWith(".gguf") }?.map { it.name.removeSuffix(".gguf") }?.sorted().orEmpty()

    fun current(context: Context): String? {
        val all = installed(context)
        return prefs(context).getString("current", null)?.takeIf { it in all } ?: all.firstOrNull()
    }

    fun use(context: Context, name: String) {
        if (name in installed(context)) prefs(context).edit().putString("current", name).apply()
    }

    fun remove(context: Context, name: String) {
        file(context, name).delete()
    }

    /** Copia il GGUF ricevuto (controlla l'intestazione) e lo mette al suo posto solo se la copia è completa. */
    fun install(context: Context, name: String, pfd: ParcelFileDescriptor): Boolean {
        if (!name.matches(Regex("[a-z0-9._-]{1,60}"))) return false
        val dest = file(context, name)
        val tmp = File(dir(context), "$name.part")
        return runCatching {
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                val magic = ByteArray(4)
                if (input.read(magic) != 4 || String(magic, Charsets.US_ASCII) != "GGUF") return false
                tmp.outputStream().use { out ->
                    out.write(magic)
                    input.copyTo(out, 1 shl 20)
                }
            }
            dest.delete()
            tmp.renameTo(dest)
        }.getOrElse { tmp.delete(); false }
    }
}

@Suppress("DEPRECATION")
private inline fun <reified T : android.os.Parcelable> Bundle.getParcelableCompat(key: String): T? =
    if (android.os.Build.VERSION.SDK_INT >= 33) getParcelable(key, T::class.java) else getParcelable(key)

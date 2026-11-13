package eu.eurise.feature.assistant

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import eu.eurise.core.contacts.Reach
import java.util.Locale

/**
 * Come si parla ad Alfred: con Eurise Voce (Whisper sul telefono, il testo torna con il permesso della suite) oppure,
 * se non c'è, con il riconoscimento vocale del telefono. Va creato in `onCreate` dell'attività.
 */
class Dictation(private val activity: ComponentActivity) {
    private var onText: ((String) -> Unit)? = null

    private val system = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { onText?.invoke(it) }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { onText?.invoke(it) }
        }
    }
    private var registered = false

    private val voicePackage: String? get() = listOf("eu.eurise.voice", "eu.eurise.voice.debug").firstOrNull { Reach.installed(activity, it) }

    private val systemIntent get() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)

    enum class Source { EuriseVoice, System, None }

    val source: Source get() = when {
        voicePackage != null -> Source.EuriseVoice
        activity.packageManager.resolveActivity(systemIntent, 0) != null -> Source.System
        else -> Source.None
    }

    /** Ascolta e restituisce il testo detto; falso se non c'è modo di ascoltare. */
    fun start(onText: (String) -> Unit): Boolean {
        this.onText = onText
        val voice = voicePackage
        if (voice != null) {
            if (!registered) {
                val filter = IntentFilter(ACTION_RESULT)
                if (Build.VERSION.SDK_INT >= 33) activity.registerReceiver(receiver, filter, suitePermission(), null, Context.RECEIVER_EXPORTED)
                else activity.registerReceiver(receiver, filter, suitePermission(), null)
                registered = true
            }
            val intent = Intent().setClassName(voice, "eu.eurise.voice.DictateActivity")
                .putExtra("reply_to", activity.packageName)
                .putExtra("language", Locale.getDefault().language)
            return runCatching { activity.startActivity(intent) }.isSuccess
        }
        return runCatching { system.launch(systemIntent) }.isSuccess
    }

    fun release() {
        if (registered) runCatching { activity.unregisterReceiver(receiver) }
        registered = false
    }

    private fun suitePermission() =
        if (activity.packageName.endsWith(".debug")) "eu.eurise.permission.SUITE_DEBUG" else "eu.eurise.permission.SUITE"

    private companion object {
        const val ACTION_RESULT = "eu.eurise.action.DICTATION_RESULT"
    }
}


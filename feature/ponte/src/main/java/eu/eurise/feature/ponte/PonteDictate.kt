package eu.eurise.feature.ponte

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import eu.eurise.core.ponte.Kind
import kotlin.concurrent.thread

/**
 * «Detta dal telefono» chiesto da Firefox sul computer: si apre dalla notifica, ascolta subito e manda il testo al
 * computer con lo stesso `ask`, così Firefox lo scrive nel campo dove si era. Senza schermate in mezzo.
 */
class PonteDictate : ComponentActivity() {
    private val listen = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim()
        if (said.isNullOrEmpty()) { finish(); return@registerForActivityResult }
        val store = PonteStore(this)
        val computer = store.computers().firstOrNull { it.id == intent.getStringExtra(EXTRA_COMPUTER) } ?: run { finish(); return@registerForActivityResult }
        val ask = intent.getStringExtra(EXTRA_ASK).orEmpty()
        val app = applicationContext
        thread {
            val ok = PonteClient(store).send(computer, Kind.Dictation, mapOf("text" to said, "ask" to ask))
            runOnUiThread {
                Toast.makeText(app, app.getString(if (ok) R.string.ponte_dict_sent else R.string.ponte_pwd_failed, computer.name), Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val speak = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.ponte_dict_prompt))
        runCatching { listen.launch(speak) }.onFailure {
            Toast.makeText(this, R.string.ponte_no_speech, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        const val EXTRA_ASK = "ask"
        const val EXTRA_COMPUTER = "computer"
    }
}

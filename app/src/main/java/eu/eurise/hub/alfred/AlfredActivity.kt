package eu.eurise.hub.alfred

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.feature.assistant.AlfredPanel
import eu.eurise.feature.assistant.BriefingPanel
import eu.eurise.feature.assistant.Dictation

/**
 * Alfred sopra l'app in cui si è: dal tasto home tenuto premuto (assistente digitale di Android o ACTION_ASSIST),
 * dalla dock (Ricerca AI tenuta premuta) o dalla sua icona. Con [Intent.EXTRA_TEXT] parte già dalla frase; con
 * [EXTRA_BRIEFING] apre il punto della giornata (pulsante «Il punto» in «I tuoi impegni»).
 */
class AlfredActivity : ComponentActivity() {
    private lateinit var dictation: Dictation
    private var session by mutableStateOf(0)
    private var initial by mutableStateOf<String?>(null)
    private var briefing by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        dictation = Dictation(this)
        initial = intent?.getStringExtra(Intent.EXTRA_TEXT)
        briefing = intent?.getBooleanExtra(EXTRA_BRIEFING, false) == true
        val source = SuiteBriefingSource(applicationContext)
        setContent {
            EuriseTheme {
                key(session) {
                    if (briefing) BriefingPanel(source, onClose = ::finish)
                    else AlfredPanel(dictation, initial, onClose = ::finish)
                }
            }
        }
    }

    /** Richiamato mentre è aperto: si riparte da capo (o dalla frase nuova). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        initial = intent.getStringExtra(Intent.EXTRA_TEXT)
        briefing = intent.getBooleanExtra(EXTRA_BRIEFING, false)
        session++
    }

    companion object {
        /** Il punto della giornata invece del pannello dei comandi; lo stesso nome usa la Scrivania. */
        const val EXTRA_BRIEFING = "eu.eurise.extra.ALFRED_BRIEFING"
    }

    override fun onDestroy() {
        dictation.release()
        super.onDestroy()
    }
}

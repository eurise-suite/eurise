package eu.eurise.postazione

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.core.suite.AppHubContract
import eu.eurise.postazione.ui.PostazioneScreen
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Eurise Postazione: la schermata iniziale nuova (tela «Eurise · nuova interfaccia», 26 novembre 2026). Si può scegliere
 * come schermata iniziale del telefono oppure aprire come un'app qualsiasi per provarla accanto alla Scrivania di oggi.
 */
class PostazioneActivity : ComponentActivity() {
    private val model: PostazioneModel by viewModels()

    /** Il tasto Home premuto quando la Postazione è già davanti: chiude cassetto e ventaglio. */
    private val home = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Come schermata iniziale «Indietro» non porta da nessuna parte; i pannelli aperti lo gestiscono da soli.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() {} })
        setContent { EuriseTheme { PostazioneScreen(model, home) } }
        if (savedInstanceState == null) dalCentroPrivacy(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        home.tryEmit(Unit)
        dalCentroPrivacy(intent)
    }

    /** Il centro privacy dell'app Eurise chiede di accendere un interruttore: si aprono le impostazioni, con la spiegazione. */
    private fun dalCentroPrivacy(intent: Intent?) {
        if (intent?.action == AppHubContract.ACTION_OPEN && intent.getStringExtra(AppHubContract.EXTRA_PAGE) == AppHubContract.PAGE_PRIVACY) {
            model.chiediImpostazioni()
        }
    }

    override fun onResume() {
        super.onResume()
        model.aggiorna()
    }
}

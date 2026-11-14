package eu.eurise.hub

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.eurise.feature.launcher.LauncherRoute

/** Eurise Scrivania: la schermata iniziale del telefono. */
class LauncherActivity : ComponentActivity() {
    private var homePresses by mutableIntStateOf(0)
    /** Domanda arrivata da «Nella suite» della Tastiera: apre la ricerca universale con quel testo. */
    private var searchRequest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Sul telefono la Scrivania resta in verticale; la disposizione orizzontale è per i tablet.
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val data = (application as EuriseApplication).data
        searchRequest = intent?.getStringExtra(EXTRA_SEARCH)
        setContent {
            LauncherRoute(data, homePresses, searchRequest) { searchRequest = null }
            // Primo avvio: i servizi che si possono accendere (compare una volta sola, poi più).
            eu.eurise.core.designsystem.theme.EuriseTheme { eu.eurise.core.designsystem.component.ServicesWelcome() }
        }
    }

    override fun onResume() {
        super.onResume()
        eu.eurise.feature.calendar.reminders.Reminders.refresh(this)
        eu.eurise.core.data.modes.Modes.refresh(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
        intent.getStringExtra(EXTRA_SEARCH)?.let { searchRequest = it }
    }

    private companion object {
        const val EXTRA_SEARCH = "eu.eurise.extra.SEARCH"
    }
}

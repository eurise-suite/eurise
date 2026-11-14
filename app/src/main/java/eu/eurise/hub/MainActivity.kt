package eu.eurise.hub

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.hub.navigation.EuriseHub
import eu.eurise.hub.navigation.HubRequest

class MainActivity : ComponentActivity() {
    /** Sezione chiesta dalla Scrivania o da un collegamento; ogni richiesta ha un numero nuovo. */
    private var request by mutableStateOf<HubRequest?>(null)
    private var requestCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        val data = (application as EuriseApplication).data
        setContent {
            EuriseTheme {
                EuriseHub(data, request)
                eu.eurise.core.designsystem.component.ServicesWelcome()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Voci automatiche (farmaci, regali) e prossimi avvisi.
        eu.eurise.feature.calendar.reminders.Reminders.refresh(this)
        eu.eurise.core.data.modes.Modes.refresh(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "application/pdf") {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM) ?: return
            request = HubRequest("trip:uri:" + android.net.Uri.encode(uri.toString()), ++requestCount)
            return
        }
        val section = intent?.getStringExtra(EXTRA_SECTION) ?: return
        request = HubRequest(section, ++requestCount)
    }

    companion object {
        /** Stessa chiave usata dalla Scrivania (feature:launcher). */
        const val EXTRA_SECTION = "eu.eurise.extra.SECTION"
    }
}

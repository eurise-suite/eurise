package eu.eurise.hub

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import eu.eurise.feature.launcher.LauncherRoute

/** Eurise Scrivania: la schermata iniziale del telefono. */
class LauncherActivity : ComponentActivity() {
    private var homePresses by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Sul telefono la Scrivania resta in verticale; la disposizione orizzontale è per i tablet.
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val data = (application as EuriseApplication).data
        setContent { LauncherRoute(data, homePresses) }
    }

    override fun onResume() {
        super.onResume()
        eu.eurise.feature.calendar.reminders.Reminders.refresh(this)
        eu.eurise.core.data.modes.Modes.refresh(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
    }
}

package eu.eurise.hub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.hub.navigation.EuriseHub

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val data = (application as EuriseApplication).data
        setContent {
            EuriseTheme {
                EuriseHub(data)
            }
        }
    }
}

package eu.eurise.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.gallery.ui.GalleryRoute

class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { EuriseTheme { GalleryRoute() } }
    }
}

package eu.eurise.gallery

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.gallery.ui.GalleryRoute

class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { EuriseTheme { GalleryRoute() } }
    }

    /** Uscendo con il tasto Home mentre un video va, il video continua in una finestrella. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipAllowed) runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().build()) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    companion object {
        /** Vero mentre la Galleria è nella finestrella sopra le altre app: il lettore nasconde i comandi. */
        var inPip by mutableStateOf(false)
            private set

        /** Il lettore lo accende mentre un video va. */
        var pipAllowed = false
    }
}

package eu.eurise.camera

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

/** Il riquadro nelle impostazioni rapide: apre la Fotocamera; a telefono bloccato, quella del blocco schermo. */
class CameraTileService : TileService() {
    override fun onClick() {
        val target = if (isLocked) SecureCameraActivity::class.java else CameraActivity::class.java
        val intent = Intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

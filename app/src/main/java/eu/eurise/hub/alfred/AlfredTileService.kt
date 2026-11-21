package eu.eurise.hub.alfred

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Il riquadro «Alfred» nella tendina (tavola AlfAvvio): un tocco e Alfred ascolta, anche dalla schermata di blocco se
 * le disposizioni lo permettono (altrimenti Android chiede prima di sbloccare). Si spegne dalle disposizioni di Alfred.
 */
class AlfredTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(eu.eurise.feature.assistant.R.string.alfred_name)
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, AlfredActivity::class.java).putExtra(AlfredActivity.EXTRA_LISTEN, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val open = {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            } else {
                @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
                startActivityAndCollapse(intent)
            }
        }
        if (isLocked && !eu.eurise.core.data.alfred.AlfredPrefs(this).lockScreen) unlockAndRun { open() } else open()
    }
}

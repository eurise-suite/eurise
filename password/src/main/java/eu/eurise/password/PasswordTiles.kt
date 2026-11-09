package eu.eurise.password

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import eu.eurise.password.data.SecureClipboard
import eu.eurise.password.data.VaultRepository
import eu.eurise.password.data.VaultState
import eu.eurise.password.logic.Generator

/** Riquadro della tendina: blocca subito la cassaforte. */
class LockTile : TileService() {
    override fun onStartListening() {
        val open = VaultRepository.get(this).state.value is VaultState.Open
        qsTile?.apply { state = if (open) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; updateTile() }
    }

    override fun onClick() {
        VaultRepository.get(this).lock()
        onStartListening()
    }
}

/** Riquadro della tendina: una frase nuova negli appunti. */
class GenerateTile : TileService() {
    override fun onClick() {
        SecureClipboard.copy(this, getString(R.string.pwd_copied_label), Generator.passphrase())
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(this, R.string.pwd_copied, Toast.LENGTH_SHORT).show()
    }
}

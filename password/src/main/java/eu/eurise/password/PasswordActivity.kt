package eu.eurise.password

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.password.data.VaultRepository
import eu.eurise.password.ui.PasswordRoute

/** Le schermate non compaiono negli screenshot né tra le app recenti; fuori dall'app per due minuti, si blocca. */
class PasswordActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val lockTask = Runnable { VaultRepository.get(this).lock() }
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { EuriseTheme { PasswordRoute() } }
    }

    override fun onStart() {
        super.onStart()
        handler.removeCallbacks(lockTask)
        // Il timer può ritardare quando il telefono dorme: al ritorno si ricontrolla il tempo trascorso.
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt >= AUTO_LOCK_MS) lockTask.run()
        stoppedAt = 0L
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
        handler.postDelayed(lockTask, AUTO_LOCK_MS)
    }

    private companion object {
        const val AUTO_LOCK_MS = 2 * 60_000L
    }
}

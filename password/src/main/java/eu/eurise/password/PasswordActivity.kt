package eu.eurise.password

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.password.data.VaultRepository
import eu.eurise.password.ui.PasswordRoute

/** Le schermate non compaiono negli screenshot né tra le app recenti; fuori dall'app per due minuti, si blocca. */
class PasswordActivity : ComponentActivity() {
    private val repo by lazy { VaultRepository.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        readOtp(intent)
        setContent { EuriseTheme { PasswordRoute() } }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        readOtp(intent)
    }

    private fun readOtp(intent: android.content.Intent?) {
        val uri = intent?.takeIf { it.action == android.content.Intent.ACTION_VIEW }?.dataString ?: return
        repo.pendingOtp.value = uri
    }

    override fun onStart() {
        super.onStart()
        repo.enter()
    }

    override fun onStop() {
        super.onStop()
        repo.leave()
    }
}

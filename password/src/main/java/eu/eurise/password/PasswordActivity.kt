package eu.eurise.password

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.lifecycle.lifecycleScope
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
        // «Sblocca» chiesto dal Browser della suite: appena la cassaforte è aperta si torna alla pagina.
        if (intent?.action == eu.eurise.core.suite.PasswordContract.ACTION_UNLOCK &&
            intent.component?.className == eu.eurise.core.suite.PasswordContract.ALIAS
        ) {
            lifecycleScope.launch {
                repo.state.first { it is eu.eurise.password.data.VaultState.Open }
                setResult(RESULT_OK)
                finish()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        readOtp(intent)
    }

    private fun readOtp(intent: android.content.Intent?) {
        when (intent?.action) {
            // Solo dall'ingresso protetto dal permesso della suite: altrove l'azione si ignora.
            eu.eurise.core.suite.PasswordContract.ACTION_ADD -> {
                if (intent.component?.className != eu.eurise.core.suite.PasswordContract.ALIAS) return
                val secret = intent.getStringExtra(eu.eurise.core.suite.PasswordContract.EXTRA_SECRET).orEmpty()
                if (secret.isEmpty()) return
                fun x(k: String) = intent.getStringExtra(k).orEmpty()
                repo.pendingEntry.value = eu.eurise.password.kdbx.EntryDraft(
                    x(eu.eurise.core.suite.PasswordContract.EXTRA_TITLE), x(eu.eurise.core.suite.PasswordContract.EXTRA_USERNAME), secret,
                    x(eu.eurise.core.suite.PasswordContract.EXTRA_URL), x(eu.eurise.core.suite.PasswordContract.EXTRA_NOTES), null,
                )
                // Il segreto non resta nell'intent (che il sistema ricorda per le app recenti).
                intent.removeExtra(eu.eurise.core.suite.PasswordContract.EXTRA_SECRET)
            }
            android.content.Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                if (data.scheme == "content" || data.scheme == "file") repo.pendingImport.value = data else repo.pendingOtp.value = data.toString()
            }
            android.content.Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                repo.pendingImport.value = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM) ?: return
            }
        }
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

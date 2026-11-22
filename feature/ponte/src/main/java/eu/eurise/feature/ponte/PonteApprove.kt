package eu.eurise.feature.ponte

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.core.ponte.Kind
import eu.eurise.core.suite.PasswordContract
import kotlin.concurrent.thread

/**
 * «Password per sito.it: approva» (tavola PcFirefox): gli accessi di Eurise Password per il sito chiesto dal computer.
 * Password chiusa → si sblocca con l'impronta o la frase; si sceglie quale accesso, e solo quello parte verso il computer
 * collegato, cifrato sul Ponte. «Non mandare» chiude senza mandare niente.
 */
class PonteApprove : ComponentActivity() {
    private class Login(val user: String, val password: String, val origin: String)

    private var logins by mutableStateOf<List<Login>?>(null)
    private var locked by mutableStateOf(false)
    private var status by mutableStateOf<String?>(null)

    private val site get() = intent.getStringExtra(EXTRA_SITE).orEmpty()
    private val passwordPackage get() = PasswordContract.passwordPackage(eu.eurise.core.suite.SuiteAccess.isDebug(this))

    private val unlock = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { load() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
        setContent {
            EuriseTheme {
                val scheme = MaterialTheme.colorScheme
                Box(Modifier.fillMaxSize().background(scheme.background).navigationBarsPadding().padding(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.ponte_pwd_title, site), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text(stringResource(R.string.ponte_pwd_explain), fontSize = 13.sp, color = scheme.onSurfaceVariant)
                        val list = logins
                        when {
                            status != null -> Text(status!!, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                            locked -> Action(stringResource(R.string.ponte_pwd_unlock), strong = true) { askUnlock() }
                            list == null -> Text("…")
                            list.isEmpty() -> Text(stringResource(R.string.ponte_pwd_none), fontSize = 14.sp)
                            else -> list.forEach { l ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(scheme.surface)
                                        .clickable(role = Role.Button) { give(l) }.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(l.user.ifBlank { site }, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        Text(Uri.parse(l.origin).host ?: l.origin, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                                    }
                                    Text(stringResource(R.string.ponte_pwd_send), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = scheme.primary)
                                }
                            }
                        }
                        Action(stringResource(if (status != null) R.string.ponte_pwd_close else R.string.ponte_pwd_deny), strong = false) { finish() }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Action(label: String, strong: Boolean, onClick: () -> Unit) {
        val scheme = MaterialTheme.colorScheme
        Text(
            label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (strong) scheme.onPrimary else scheme.primary,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if (strong) scheme.primary else scheme.primary.copy(alpha = 0.12f))
                .clickable(role = Role.Button, onClick = onClick).padding(vertical = 13.dp, horizontal = 18.dp),
        )
    }

    private fun load() {
        thread {
            val b = runCatching {
                eu.eurise.core.suite.SuiteAccess.call(this@PonteApprove, Uri.parse("content://" + PasswordContract.loginsAuthority(passwordPackage)), PasswordContract.METHOD_FETCH, site, null)
            }.getOrNull()
            runOnUiThread {
                when {
                    b == null -> { locked = false; logins = emptyList(); status = getString(R.string.ponte_pwd_missing) }
                    !b.getBoolean(PasswordContract.KEY_OPEN) -> locked = true
                    else -> {
                        locked = false
                        val u = b.getStringArray(PasswordContract.KEY_USERNAME).orEmpty()
                        val p = b.getStringArray(PasswordContract.KEY_PASSWORD).orEmpty()
                        val o = b.getStringArray(PasswordContract.KEY_ORIGIN).orEmpty()
                        logins = u.indices.filter { it < p.size && it < o.size }.map { Login(u[it], p[it], o[it]) }
                    }
                }
            }
        }
    }

    private fun askUnlock() {
        // Solo verso Password della suite: un'app con lo stesso nome potrebbe chiedere la frase principale al suo posto.
        val i = eu.eurise.core.suite.SuiteAccess.trusted(this, Intent(PasswordContract.ACTION_UNLOCK)
            .setComponent(ComponentName(passwordPackage, PasswordContract.ALIAS))) ?: return
        runCatching { unlock.launch(i) }
    }

    private fun give(l: Login) {
        val store = PonteStore(this)
        val computer = store.computers().firstOrNull { it.id == intent.getStringExtra(EXTRA_COMPUTER) } ?: return
        status = getString(R.string.ponte_pwd_sending)
        thread {
            val ok = PonteClient(store).send(computer, Kind.PasswordGive, mapOf(
                "ask" to intent.getStringExtra(EXTRA_ASK).orEmpty(), "site" to site, "username" to l.user, "password" to l.password,
            ))
            runOnUiThread { status = getString(if (ok) R.string.ponte_pwd_sent else R.string.ponte_pwd_failed, computer.name); if (ok) window.decorView.postDelayed({ finish() }, 1200) }
        }
    }

    companion object {
        const val EXTRA_SITE = "site"
        const val EXTRA_ASK = "ask"
        const val EXTRA_COMPUTER = "computer"
    }
}

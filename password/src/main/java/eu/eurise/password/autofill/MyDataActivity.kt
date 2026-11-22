package eu.eurise.password.autofill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.eurise.core.designsystem.component.CardDivider
import eu.eurise.core.designsystem.component.GroupCard
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.core.suite.PasswordContract
import eu.eurise.password.PasswordViewModel
import eu.eurise.password.R
import eu.eurise.password.data.VaultRepository
import eu.eurise.password.data.VaultState
import eu.eurise.password.ui.UnlockScreen

/**
 * «I miei dati» per un'altra app della suite ([PasswordContract.ACTION_PICK_MY_DATA], solo con il permesso della suite):
 * foglio sopra l'app che chiede (Office, per i moduli PDF). Sblocca, mostra i profili e restituisce quello toccato, con la
 * conferma (impronta o PIN) richiesta per i dati riservati. Senza un tocco non esce nulla; mai negli appunti.
 */
class MyDataActivity : ComponentActivity() {
    private val repo by lazy { VaultRepository.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        // I dati tornano solo a un'app della suite che li ha chiesti con startActivityForResult (callingPackage noto).
        if (!eu.eurise.core.suite.SuiteAccess.isSuitePackage(this, callingPackage)) { finish(); return }
        val caller = callingPackage?.let { pkg ->
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
        } ?: "Eurise"
        setContent { EuriseTheme { MyDataSheet(caller, onCancel = ::finish, onPick = ::finishWith) } }
    }

    override fun onStart() { super.onStart(); repo.enter() }
    override fun onStop() { super.onStop(); repo.leave() }

    private fun finishWith(title: String, fields: List<Pair<String, String>>) {
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(PasswordContract.KEY_TITLE, title)
                .putExtra(PasswordContract.KEY_FIELD_NAMES, fields.map { it.first }.toTypedArray())
                .putExtra(PasswordContract.KEY_FIELD_VALUES, fields.map { it.second }.toTypedArray()),
        )
        finish()
    }
}

@Composable
private fun MyDataSheet(caller: String, onCancel: () -> Unit, onPick: (String, List<Pair<String, String>>) -> Unit, vm: PasswordViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable(onClick = onCancel), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().imePadding()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(scheme.background)
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState()).navigationBarsPadding().padding(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(CircleShape).background(scheme.outlineVariant))
            when (val s = state) {
                VaultState.Loading -> Unit
                VaultState.Empty -> Text(stringResource(R.string.pwd_fill_no_vault), modifier = Modifier.padding(20.dp))
                VaultState.Locked -> UnlockScreen(vm, compact = true)
                is VaultState.Open -> {
                    Text(stringResource(R.string.pwd_fill_choose_profile), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
                    Text(stringResource(R.string.pwd_mydata_for, caller), fontSize = 13.sp, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                    val profiles = s.entries.filter { it.profile != null }
                    if (profiles.isEmpty()) {
                        Text(stringResource(R.string.pwd_fill_no_profile), modifier = Modifier.padding(horizontal = 20.dp))
                    } else {
                        GroupCard {
                            profiles.forEachIndexed { i, e ->
                                if (i > 0) CardDivider()
                                val fields = e.profile!!.filter { it.second.isNotBlank() }
                                Column(
                                    Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable {
                                        vm.confirmSensitive(context) {
                                            vm.markUsed(e.uuid)
                                            onPick(e.title, fields)
                                        }
                                    }.padding(vertical = 10.dp),
                                ) {
                                    Text(e.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text(stringResource(R.string.pwd_mydata_fields, fields.size), fontSize = 12.5.sp, color = scheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

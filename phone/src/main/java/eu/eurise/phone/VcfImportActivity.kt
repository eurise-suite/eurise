package eu.eurise.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import eu.eurise.core.contacts.ContactDraft
import eu.eurise.core.contacts.Contacts
import eu.eurise.core.contacts.VCard
import eu.eurise.core.designsystem.component.ImportRow
import eu.eurise.core.designsystem.component.ImportScreen
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.theme.EuriseTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * «Apri con › Rubrica» per i file .vcf (biglietti da visita, rubriche esportate; 15 novembre): si vedono i contatti del
 * file, si spuntano e si aggiungono alla rubrica del telefono (quella sincronizzata con il conto scelto in Rubrica).
 */
class VcfImportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val uri: Uri? = intent.data ?: @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
        if (uri == null) { finish(); return }
        setContent { EuriseTheme { Screen(uri) } }
    }

    private val perms = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
    private fun granted() = perms.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    @Composable
    private fun Screen(uri: Uri) {
        val scope = rememberCoroutineScope()
        var allowed by remember { mutableStateOf(granted()) }
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { allowed = granted() }
        LaunchedEffect(Unit) { if (!allowed) ask.launch(perms) }
        val people by produceState<List<ContactDraft>?>(null, uri) {
            value = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()?.let(VCard::parse) ?: emptyList()
            }
        }
        val list = people.orEmpty()
        if (people != null && list.isEmpty()) {
            LaunchedEffect(Unit) { Toast.makeText(this@VcfImportActivity, R.string.vcf_empty, Toast.LENGTH_LONG).show(); finish() }
            return
        }
        var chosen by remember(people) { mutableStateOf(list.indices.toSet()) }
        var busy by remember { mutableStateOf(false) }
        ImportScreen(
            icon = EuriseIcons.person(),
            title = stringResource(R.string.vcf_title),
            subtitle = if (people == null) "…" else pluralStringResource(R.plurals.vcf_found, list.size, list.size),
            rows = list.map { c ->
                ImportRow(c.name, (c.phones.filter { it.isNotBlank() } + c.emails + listOf(c.company)).filter { it.isNotBlank() }.joinToString(" · "))
            },
            chosen = chosen,
            onToggle = { i -> chosen = if (i in chosen) chosen - i else chosen + i },
            action = if (!allowed) stringResource(R.string.vcf_allow) else pluralStringResource(R.plurals.vcf_add, chosen.size, chosen.size),
            busy = busy,
            onAction = action@{
                if (!allowed) { ask.launch(perms); return@action }
                busy = true
                scope.launch {
                    val added = chosen.sorted().mapNotNull { list.getOrNull(it) }.count { runCatching { Contacts.save(this@VcfImportActivity, it) }.getOrNull() != null }
                    Toast.makeText(this@VcfImportActivity, resources.getQuantityString(R.plurals.vcf_done, added, added), Toast.LENGTH_LONG).show()
                    finish()
                }
            },
            onClose = { finish() },
        )
    }
}

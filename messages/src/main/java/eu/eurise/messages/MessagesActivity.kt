package eu.eurise.messages

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.messages.system.Notifications
import eu.eurise.messages.ui.MessagesApp
import eu.eurise.messages.ui.Open

/**
 * Eurise Messaggi: si apre dall'icona, dalle notifiche (sulla conversazione), dai numeri toccati nelle altre app
 * (sms:, smsto:) e da Condividi (testo o immagine). Al primo avvio chiede i permessi e propone di diventare l'app per gli SMS.
 */
class MessagesActivity : ComponentActivity() {
    private val open = mutableStateOf<Open?>(null)
    private val refresh = mutableIntStateOf(0)

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh.intValue++ }
    private val role = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh.intValue++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        read(intent)
        if (savedInstanceState == null) askPermissions()
        setContent {
            EuriseTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MessagesApp(
                        open = open.value, onOpened = { open.value = null }, refresh = refresh.intValue,
                        isDefault = isDefault(), onBecomeDefault = ::requestRole, onAskPermissions = ::askPermissions,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        read(intent)
    }

    override fun onResume() {
        super.onResume()
        refresh.intValue++
    }

    private fun read(intent: Intent?) {
        intent ?: return
        val thread = intent.getLongExtra(Notifications.EXTRA_THREAD, -1)
        val address = intent.getStringExtra(Notifications.EXTRA_ADDRESS)
        when {
            thread >= 0 && address != null -> open.value = Open.Thread(thread, listOf(address))
            intent.action == Intent.ACTION_SENDTO || intent.action == Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                val to = data.schemeSpecificPart.orEmpty().substringBefore('?').split(',', ';').map { Uri.decode(it).trim() }.filter { it.isNotEmpty() }
                val body = intent.getStringExtra("sms_body") ?: data.getQueryParameter("body")
                open.value = Open.New(to, body.orEmpty(), null, null)
            }
            intent.action == Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                open.value = Open.New(emptyList(), intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(), stream, intent.type)
            }
        }
    }

    private fun askPermissions() {
        val wanted = buildList {
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.SEND_SMS)
            add(Manifest.permission.RECEIVE_SMS)
            add(Manifest.permission.RECEIVE_MMS)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) permissions.launch(wanted.toTypedArray())
    }

    private fun isDefault(): Boolean = if (Build.VERSION.SDK_INT >= 29) getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)
    else Telephony.Sms.getDefaultSmsPackage(this) == packageName

    private fun requestRole() {
        val intent = if (Build.VERSION.SDK_INT >= 29) getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS)
        else Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
        runCatching { role.launch(intent) }
    }
}

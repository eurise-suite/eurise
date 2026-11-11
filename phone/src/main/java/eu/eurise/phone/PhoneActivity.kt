package eu.eurise.phone

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import eu.eurise.core.contacts.Reach
import eu.eurise.core.designsystem.theme.EuriseTheme

/**
 * Eurise Telefono: recenti, preferiti, rubrica e tastierino. Si apre anche dai numeri toccati nelle altre app (tel:).
 * Al primo avvio chiede i permessi e propone di diventare l'app per le chiamate (serve per la schermata della chiamata).
 */
class PhoneActivity : ComponentActivity() {
    /** Numero arrivato da un'altra app (tel:): apre il tastierino già compilato. */
    private val dialNumber = mutableStateOf<String?>(null)
    /** Aperta dall'icona Rubrica o da «Aggiungi in rubrica» di un'altra app. */
    private val start = mutableStateOf<StartAt?>(null)
    private val refresh = mutableStateOf(0)

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh.value++ }
    private val role = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh.value++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        read(intent)
        if (savedInstanceState == null) askPermissions()
        setContent {
            EuriseTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    PhoneApp(
                        dialNumber = dialNumber.value,
                        onDialConsumed = { dialNumber.value = null },
                        start = start.value,
                        onStartConsumed = { start.value = null },
                        refresh = refresh.value,
                        isDefault = isDefaultDialer(),
                        onBecomeDefault = ::requestRole,
                        onAskPermissions = ::askPermissions,
                        onCall = ::call,
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
        refresh.value++
    }

    private fun read(intent: Intent?) {
        intent ?: return
        if (intent.action == Intent.ACTION_INSERT) {
            start.value = StartAt.Insert(
                eu.eurise.core.contacts.ContactDraft(
                    name = intent.getStringExtra(android.provider.ContactsContract.Intents.Insert.NAME).orEmpty(),
                    phones = listOf(intent.getStringExtra(android.provider.ContactsContract.Intents.Insert.PHONE).orEmpty()),
                    emails = listOfNotNull(intent.getStringExtra(android.provider.ContactsContract.Intents.Insert.EMAIL)),
                ),
            )
            return
        }
        if (intent.action == Intent.ACTION_MAIN && intent.component?.className?.endsWith("ContactsAlias") == true) {
            start.value = StartAt.People
            return
        }
        val data = intent.data ?: return
        if (data.scheme == "tel") dialNumber.value = data.schemeSpecificPart.orEmpty()
    }

    private fun askPermissions() {
        val wanted = buildList {
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.WRITE_CONTACTS)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALENDAR)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) permissions.launch(wanted.toTypedArray())
    }


    private fun isDefaultDialer(): Boolean = if (Build.VERSION.SDK_INT >= 29) {
        getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)
    } else {
        getSystemService(TelecomManager::class.java).defaultDialerPackage == packageName
    }

    private fun requestRole() {
        val intent = if (Build.VERSION.SDK_INT >= 29) getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_DIALER)
        else Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
        runCatching { role.launch(intent) }
    }

    /** Chiama: con il sistema delle chiamate se c'è il permesso, altrimenti apre il tastierino di sistema. */
    private fun call(number: String) {
        if (number.isBlank()) return
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            runCatching { getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", Reach.digits(number), null), Bundle()) }
                .onFailure { Reach.open(this, Reach.call(number)) }
        } else {
            Reach.open(this, Reach.dial(number))
        }
    }
}

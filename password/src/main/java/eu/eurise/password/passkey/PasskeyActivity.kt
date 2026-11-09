package eu.eurise.password.passkey

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.PendingIntentHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import eu.eurise.core.designsystem.theme.EuriseTheme
import eu.eurise.password.PasswordViewModel
import eu.eurise.password.R
import eu.eurise.password.data.VaultRepository
import eu.eurise.password.data.VaultState
import eu.eurise.password.kdbx.EntryDraft
import eu.eurise.password.kdbx.VaultEntry
import eu.eurise.password.ui.UnlockScreen
import org.json.JSONArray
import org.json.JSONObject

/** I browser approvati dalla persona: solo loro possono parlare a nome di un sito. */
internal class TrustedBrowsers(context: Context) {
    private val prefs = context.getSharedPreferences("password_browsers", Context.MODE_PRIVATE)

    fun isTrusted(packageName: String, fingerprint: String) = prefs.getString(packageName, null) == fingerprint

    fun trust(packageName: String, fingerprint: String) { prefs.edit().putString(packageName, fingerprint).apply() }

    fun allowlist(packageName: String, fingerprint: String): String = JSONObject().put(
        "apps",
        JSONArray().put(
            JSONObject().put("type", "android").put(
                "info",
                JSONObject().put("package_name", packageName).put(
                    "signatures",
                    JSONArray().put(JSONObject().put("build", "release").put("cert_fingerprint_sha256", fingerprint)),
                ),
            ),
        ),
    ).toString()
}

/** Da dove arriva la richiesta, già verificato. */
private sealed interface Caller {
    data class Site(val origin: String, val hash: ByteArray) : Caller
    data class App(val origin: String) : Caller
    data class Untrusted(val packageName: String, val fingerprint: String) : Caller
}

/** Foglio di conferma: sblocca, chiede il dito se attivo, poi firma e restituisce il risultato ad Android. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class PasskeyActivity : ComponentActivity() {
    private val repo by lazy { VaultRepository.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val mode = intent.getStringExtra(EXTRA_MODE)
        val uuid = intent.getStringExtra(EXTRA_UUID)
        setContent { EuriseTheme { PasskeySheet(mode, uuid, ::cancel, ::complete) } }
    }

    override fun onStart() { super.onStart(); repo.enter() }
    override fun onStop() { super.onStop(); repo.leave() }

    private fun complete(result: Intent) { setResult(Activity.RESULT_OK, result); finish() }

    private fun cancel() {
        val result = Intent()
        when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_CREATE -> PendingIntentHandler.setCreateCredentialException(result, CreateCredentialCancellationException())
            MODE_GET -> PendingIntentHandler.setGetCredentialException(result, GetCredentialCancellationException())
            else -> { finish(); return }
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_UUID = "uuid"
        const val MODE_UNLOCK = "unlock"
        const val MODE_CREATE = "create"
        const val MODE_GET = "get"
    }
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun resolve(context: Context, app: CallingAppInfo, hash: ByteArray?): Caller {
    val certificate = app.signingInfo.apkContentsSigners.first().toByteArray()
    val fingerprint = WebAuthn.fingerprint(certificate)
    // Un'app qualunque non può dichiarare un sito: l'hash fornito dal chiamante vale solo da un browser approvato.
    if (hash == null) return Caller.App(WebAuthn.appOrigin(certificate))
    val trusted = TrustedBrowsers(context)
    if (!trusted.isTrusted(app.packageName, fingerprint)) return Caller.Untrusted(app.packageName, fingerprint)
    val origin = app.getOrigin(trusted.allowlist(app.packageName, fingerprint)) ?: return Caller.Untrusted(app.packageName, fingerprint)
    return Caller.Site(origin.trimEnd('/'), hash)
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
private fun PasskeySheet(mode: String?, uuid: String?, onCancel: () -> Unit, onDone: (Intent) -> Unit, vm: PasswordViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable(onClick = onCancel), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().imePadding().clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(scheme.background)
                .clickable(enabled = false) {}.navigationBarsPadding().padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                VaultState.Loading -> Unit
                VaultState.Empty -> Text(stringResource(R.string.pwd_fill_no_vault), modifier = Modifier.padding(20.dp))
                VaultState.Locked -> UnlockScreen(vm, compact = true)
                is VaultState.Open -> when (mode) {
                    PasskeyActivity.MODE_UNLOCK -> UnlockedResult(s.entries, onDone)
                    PasskeyActivity.MODE_CREATE -> CreateFlow(s.entries, vm, onCancel, onDone)
                    PasskeyActivity.MODE_GET -> GetFlow(s.entries.firstOrNull { it.uuid == uuid }, vm, onCancel, onDone)
                    else -> LaunchedEffect(Unit) { onCancel() }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
private fun UnlockedResult(entries: List<VaultEntry>, onDone: (Intent) -> Unit) {
    val activity = LocalContext.current as Activity
    LaunchedEffect(Unit) {
        val request = PendingIntentHandler.retrieveBeginGetCredentialRequest(activity.intent)
        val result = Intent()
        if (request != null) PendingIntentHandler.setBeginGetCredentialResponse(result, Passkeys.response(activity, request, entries))
        onDone(result)
    }
}

@Composable
private fun Title(text: String) = Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))

@Composable
private fun Body(text: String) = Text(text, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp))

@Composable
private fun Actions(confirm: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text(confirm) }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pwd_passkey_cancel)) }
    }
}

@Composable
private fun TrustPrompt(caller: Caller.Untrusted, onTrust: () -> Unit, onCancel: () -> Unit) {
    Title(stringResource(R.string.pwd_passkey_trust_title))
    Body(stringResource(R.string.pwd_passkey_trust_body, caller.packageName))
    Actions(stringResource(R.string.pwd_passkey_trust_ok), onTrust, onCancel)
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
private fun CreateFlow(entries: List<VaultEntry>, vm: PasswordViewModel, onCancel: () -> Unit, onDone: (Intent) -> Unit) {
    val activity = LocalContext.current as Activity
    val provider = remember { PendingIntentHandler.retrieveProviderCreateCredentialRequest(activity.intent) }
    val request = provider?.callingRequest as? CreatePublicKeyCredentialRequest
    if (provider == null || request == null) { LaunchedEffect(Unit) { onCancel() }; return }
    var version by remember { mutableStateOf(0) }
    val caller = remember(version) { resolve(activity, provider.callingAppInfo, request.clientDataHash) }
    val json = remember { JSONObject(request.requestJson) }
    val rp = json.getJSONObject("rp")
    val rpId = rp.optString("id")
    val user = json.getJSONObject("user")
    val algorithms = json.optJSONArray("pubKeyCredParams")
    val es256 = algorithms != null && (0 until algorithms.length()).any { algorithms.getJSONObject(it).optInt("alg") == -7 }

    when {
        caller is Caller.Untrusted -> TrustPrompt(caller, { TrustedBrowsers(activity).trust(caller.packageName, caller.fingerprint); version++ }, onCancel)
        !es256 -> { Body(stringResource(R.string.pwd_passkey_only_es256)); Actions(stringResource(R.string.pwd_passkey_cancel), onCancel, onCancel) }
        caller is Caller.Site && !WebAuthn.rpAllowed(rpId, caller.origin) -> { Body(stringResource(R.string.pwd_passkey_wrong_site, rpId)); Actions(stringResource(R.string.pwd_passkey_cancel), onCancel, onCancel) }
        else -> {
            Title(stringResource(R.string.pwd_passkey_create, rpId))
            Body(user.optString("name"))
            Actions(stringResource(R.string.pwd_passkey_confirm), {
                vm.confirmBio(activity) {
                    val key = WebAuthn.newPasskey(rpId, user.optString("name"), user.optString("id"))
                    val origin = when (caller) { is Caller.Site -> caller.origin; is Caller.App -> caller.origin; else -> "" }
                    val id = WebAuthn.unb64(key.credentialId)
                    val public = WebAuthn.publicKeyOf(key.privateKeyPem)
                    val authData = WebAuthn.attestationAuthData(rpId, id, public)
                    // Se il browser ha già calcolato i dati del client li sostituisce lui: qui basta un segnaposto.
                    val client = if (caller is Caller.Site) "{}".toByteArray() else WebAuthn.clientData("webauthn.create", json.getString("challenge"), origin)
                    val body = JSONObject()
                        .put("clientDataJSON", WebAuthn.b64(client))
                        .put("attestationObject", WebAuthn.b64(WebAuthn.attestationObject(authData)))
                        .put("authenticatorData", WebAuthn.b64(authData))
                        .put("publicKey", WebAuthn.b64(public.encoded))
                        .put("publicKeyAlgorithm", -7)
                        .put("transports", JSONArray().put("internal"))
                    val response = JSONObject().put("id", key.credentialId).put("rawId", key.credentialId).put("type", "public-key")
                        .put("authenticatorAttachment", "platform").put("response", body).put("clientExtensionResults", JSONObject())
                    val same = entries.filter { it.card == null && it.passkey == null && it.username.equals(user.optString("name"), ignoreCase = true) && eu.eurise.password.autofill.Matching.domainMatches(it.url, rpId) }.singleOrNull()
                    val draft = if (same != null) EntryDraft(same.title, same.username, same.password, same.url, same.notes, same.otp, passkey = key)
                    else EntryDraft(rp.optString("name").ifBlank { rpId }, user.optString("name"), "", "https://$rpId", "", null, passkey = key)
                    vm.save(draft, same?.uuid)
                    val result = Intent()
                    runCatching {
                        PendingIntentHandler.setCreateCredentialResponse(result, CreatePublicKeyCredentialResponse(response.toString()))
                    }.onFailure { PendingIntentHandler.setCreateCredentialException(result, CreateCredentialUnknownException()) }
                    onDone(result)
                }
            }, onCancel)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
private fun GetFlow(entry: VaultEntry?, vm: PasswordViewModel, onCancel: () -> Unit, onDone: (Intent) -> Unit) {
    val activity = LocalContext.current as Activity
    val provider = remember { PendingIntentHandler.retrieveProviderGetCredentialRequest(activity.intent) }
    val option = provider?.credentialOptions?.filterIsInstance<GetPublicKeyCredentialOption>()?.firstOrNull()
    val key = entry?.passkey
    if (provider == null || option == null || key == null) { LaunchedEffect(Unit) { onCancel() }; return }
    var version by remember { mutableStateOf(0) }
    val caller = remember(version) { resolve(activity, provider.callingAppInfo, option.clientDataHash) }
    val json = remember { JSONObject(option.requestJson) }

    when {
        caller is Caller.Untrusted -> TrustPrompt(caller, { TrustedBrowsers(activity).trust(caller.packageName, caller.fingerprint); version++ }, onCancel)
        caller is Caller.Site && !WebAuthn.rpAllowed(key.rpId, caller.origin) -> { Body(stringResource(R.string.pwd_passkey_wrong_site, key.rpId)); Actions(stringResource(R.string.pwd_passkey_cancel), onCancel, onCancel) }
        else -> {
            Title(stringResource(R.string.pwd_passkey_use, key.rpId))
            Body(key.username)
            Actions(stringResource(R.string.pwd_passkey_continue), {
                vm.confirmBio(activity) {
                    val authData = WebAuthn.assertionAuthData(key.rpId)
                    val client: ByteArray
                    val hash: ByteArray
                    if (caller is Caller.Site) {
                        client = "{}".toByteArray()
                        hash = caller.hash
                    } else {
                        client = WebAuthn.clientData("webauthn.get", json.getString("challenge"), (caller as Caller.App).origin)
                        hash = WebAuthn.clientHash(client)
                    }
                    val signature = WebAuthn.sign(key.privateKeyPem, authData + hash)
                    val body = JSONObject()
                        .put("clientDataJSON", WebAuthn.b64(client))
                        .put("authenticatorData", WebAuthn.b64(authData))
                        .put("signature", WebAuthn.b64(signature))
                        .put("userHandle", key.userHandle)
                    val response = JSONObject().put("id", key.credentialId).put("rawId", key.credentialId).put("type", "public-key")
                        .put("authenticatorAttachment", "platform").put("response", body).put("clientExtensionResults", JSONObject())
                    vm.markUsed(entry.uuid)
                    val result = Intent()
                    runCatching {
                        PendingIntentHandler.setGetCredentialResponse(result, GetCredentialResponse(PublicKeyCredential(response.toString())))
                    }.onFailure { PendingIntentHandler.setGetCredentialException(result, GetCredentialUnknownException()) }
                    onDone(result)
                }
            }, onCancel)
        }
    }
}

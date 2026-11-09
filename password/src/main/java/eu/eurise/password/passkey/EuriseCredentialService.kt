package eu.eurise.password.passkey

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.annotation.RequiresApi
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.provider.AuthenticationAction
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.credentials.provider.PublicKeyCredentialEntry
import eu.eurise.password.R
import eu.eurise.password.data.VaultRepository
import eu.eurise.password.data.VaultState
import eu.eurise.password.kdbx.VaultEntry
import org.json.JSONObject

/** Dice ad Android quali passkey ha la cassaforte: nessun segreto esce di qui, la firma avviene in [PasskeyActivity]. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
class EuriseCredentialService : CredentialProviderService() {

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
    ) {
        val repo = VaultRepository.get(this)
        repo.expireIfDue()
        val open = repo.state.value as? VaultState.Open
        if (open != null) {
            callback.onResult(Passkeys.response(this, request, open.entries))
        } else {
            val action = AuthenticationAction(getString(R.string.pwd_passkey_unlock), Passkeys.intent(this, PasskeyActivity.MODE_UNLOCK, null))
            callback.onResult(BeginGetCredentialResponse.Builder().setAuthenticationActions(listOf(action)).build())
        }
    }

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    ) {
        if (request !is BeginCreatePublicKeyCredentialRequest) {
            callback.onError(CreateCredentialUnknownException())
            return
        }
        val entry = CreateEntry.Builder(getString(R.string.pwd_passkey_new_entry), Passkeys.intent(this, PasskeyActivity.MODE_CREATE, null)).build()
        callback.onResult(BeginCreateCredentialResponse.Builder().setCreateEntries(listOf(entry)).build())
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) = callback.onResult(null)
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal object Passkeys {
    private var nextRequest = 1

    fun intent(context: Context, mode: String, uuid: String?): PendingIntent {
        val intent = Intent(context, PasskeyActivity::class.java).putExtra(PasskeyActivity.EXTRA_MODE, mode).putExtra(PasskeyActivity.EXTRA_UUID, uuid)
        return PendingIntent.getActivity(context, nextRequest++, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    }

    /** Solo passkey del sito richiesto e, se il sito ne indica alcune, solo quelle. */
    fun response(context: Context, request: BeginGetCredentialRequest, entries: List<VaultEntry>): BeginGetCredentialResponse {
        val out = ArrayList<CredentialEntry>()
        request.beginGetCredentialOptions.filterIsInstance<BeginGetPublicKeyCredentialOption>().forEach { option ->
            val json = runCatching { JSONObject(option.requestJson) }.getOrNull() ?: return@forEach
            val rpId = json.optString("rpId")
            val allowed = json.optJSONArray("allowCredentials")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).optString("id") } }.orEmpty()
            entries.filter { e -> e.passkey?.rpId == rpId && (allowed.isEmpty() || e.passkey.credentialId in allowed) }.forEach { e ->
                val name = e.passkey!!.username.ifBlank { e.username }.ifBlank { e.title }
                out += PublicKeyCredentialEntry.Builder(context, name, intent(context, PasskeyActivity.MODE_GET, e.uuid), option)
                    .setDisplayName(e.title).build()
            }
        }
        return BeginGetCredentialResponse.Builder().setCredentialEntries(out).build()
    }
}

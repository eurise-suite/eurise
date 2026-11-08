package eu.eurise.password.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import eu.eurise.password.R
import eu.eurise.password.data.VaultRepository
import eu.eurise.password.data.VaultState

/**
 * Compilazione automatica di sistema. Propone direttamente solo le voci del sito o dell'app giusti e a cassaforte
 * aperta; in tutti gli altri casi (bloccata, carte, nuovo account) la riga apre [FillActivity].
 */
class EuriseAutofillService : AutofillService() {

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess(null)
        val form = Form.parse(structure)
        if (form == null || form.packageName == packageName) return callback.onSuccess(null)

        val repo = VaultRepository.get(this)
        repo.expireIfDue()
        val response = FillResponse.Builder()
        var count = 0
        val open = repo.state.value as? VaultState.Open
        if (open != null && form.hasPassword) {
            open.entries.filter(form::matches).sortedByDescending { it.lastUsed }.take(MAX_DIRECT).forEach { e ->
                val label = getString(R.string.pwd_fill_for, e.title.ifBlank { form.target }, e.username)
                form.loginDataset(this, e.username, e.password, label)?.let { response.addDataset(it); count++ }
            }
        }
        if (form.hasLogin || form.hasCard) {
            val label = getString(if (form.hasCard && !form.hasLogin) R.string.pwd_fill_card else R.string.pwd_fill_open)
            response.addDataset(opener(form, label))
            count++
        }
        callback.onSuccess(if (count > 0) response.build() else null)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) = callback.onSuccess()

    /** Riga che apre il foglio: i valori arrivano solo dopo, come risultato dell'autenticazione. */
    @Suppress("DEPRECATION")
    private fun opener(form: Form, label: String): Dataset {
        val intent = Intent(this, FillActivity::class.java)
            .putParcelableArrayListExtra(FillActivity.EXTRA_IDS, form.ids)
            .putExtra(FillActivity.EXTRA_KINDS, form.kinds.map { it.ordinal }.toIntArray())
            .putExtra(FillActivity.EXTRA_DOMAIN, form.webDomain)
            .putExtra(FillActivity.EXTRA_PACKAGE, form.packageName)
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val sender = PendingIntent.getActivity(this, nextRequest++, intent, PendingIntent.FLAG_CANCEL_CURRENT or mutable).intentSender
        val view = Form.presentation(this, label)
        val builder = Dataset.Builder(view).setAuthentication(sender)
        form.ids.forEach { builder.setValue(it, null, view) }
        return builder.build()
    }

    private companion object {
        const val MAX_DIRECT = 4
        var nextRequest = 1
    }
}

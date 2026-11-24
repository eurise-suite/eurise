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
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import eu.eurise.password.R
import eu.eurise.password.data.PendingLogin
import eu.eurise.password.data.VaultRepository
import eu.eurise.password.data.VaultState

/**
 * Compilazione automatica di sistema. Propone direttamente solo le voci del sito o dell'app giusti e a cassaforte
 * aperta; in tutti gli altri casi (bloccata, carte, nuovo account) la riga apre [FillActivity].
 */
class EuriseAutofillService : AutofillService() {

    /**
     * Un errore qui non deve mai fermare Password né l'app che si sta compilando: si risponde «niente da proporre» (24
     * novembre: un modulo con la password e senza nome utente faceva cadere Password a ogni apertura).
     */
    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        var answered = false
        try {
            fill(request) { response -> answered = true; callback.onSuccess(response) }
        } catch (e: Exception) {
            android.util.Log.w("EuriseAutofill", "richiesta di compilazione non gestita", e)
            if (!answered) runCatching { callback.onSuccess(null) }
        }
    }

    /** La compilazione vera; [reply] si chiama una volta sola, con le proposte o con null. */
    private fun fill(request: FillRequest, reply: (FillResponse?) -> Unit) {
        val callback = object { fun onSuccess(r: FillResponse?) = reply(r) }
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess(null)
        val form = Form.parse(structure)
        if (form == null || form.packageName == packageName) return callback.onSuccess(null)

        val repo = VaultRepository.get(this)
        repo.expireIfDue()
        val response = FillResponse.Builder()
        var count = 0
        val open = repo.state.value as? VaultState.Open
        // Proposte in linea per la tastiera (Android 11+): la Tastiera di Eurise le mostra nella fila «Compila · account».
        val inline = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) request.inlineSuggestionsRequest else null
        val inlineMax = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) inline?.maxSuggestionCount ?: 0 else 0
        fun inlineFor(index: Int, title: String, subtitle: String?): android.service.autofill.InlinePresentation? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || inline == null || index >= inlineMax) return null
            val specs = inline.inlinePresentationSpecs
            if (specs.isEmpty()) return null
            return InlineRows.presentation(this, specs[minOf(index, specs.size - 1)], title, subtitle)
        }
        if (open != null && form.hasPassword) {
            open.entries.filter(form::matches).sortedByDescending { it.lastUsed }.take(MAX_DIRECT).forEach { e ->
                val label = getString(R.string.pwd_fill_for, e.title.ifBlank { form.target }, e.username)
                val chip = inlineFor(count, e.username.ifBlank { e.title.ifBlank { form.target } }, e.title.ifBlank { form.target }.takeIf { e.username.isNotBlank() })
                form.loginDataset(this, e.username, e.password, label, chip)?.let { response.addDataset(it); count++ }
            }
        }
        if (form.hasLogin || form.hasCard || form.hasProfile) {
            val label = getString(
                when {
                    form.hasCard && !form.hasLogin -> R.string.pwd_fill_card
                    form.hasProfile && !form.hasLogin -> R.string.pwd_fill_profile
                    else -> R.string.pwd_fill_open
                },
            )
            response.addDataset(opener(form, label, inlineFor(count, label, null)))
            count++
        }
        val passwords = form.idsOf(FieldKind.Password, FieldKind.NewPassword)
        if (passwords.isNotEmpty() && !form.hasCard) {
            // Android rifiuta un elenco vuoto di campi facoltativi: senza campo del nome si salva solo la password.
            val usernames = form.idsOf(FieldKind.Username)
            val type = if (usernames.isEmpty()) SaveInfo.SAVE_DATA_TYPE_PASSWORD else SaveInfo.SAVE_DATA_TYPE_PASSWORD or SaveInfo.SAVE_DATA_TYPE_USERNAME
            val save = SaveInfo.Builder(type, passwords)
            if (usernames.isNotEmpty()) save.setOptionalIds(usernames)
            response.setSaveInfo(save.build())
        }
        callback.onSuccess(if (count > 0) response.build() else null)
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        val form = structure?.let(Form::parse)
        val password = form?.typed(FieldKind.NewPassword, FieldKind.Password)
        if (form == null || password == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return callback.onSuccess()
        VaultRepository.get(this).pendingLogin =
            PendingLogin(form.typed(FieldKind.Username).orEmpty(), password, form.webDomain, form.packageName)
        val intent = Intent(this, FillActivity::class.java).putExtra(FillActivity.EXTRA_SAVE, true)
        val flags = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        callback.onSuccess(PendingIntent.getActivity(this, nextRequest++, intent, flags).intentSender)
    }

    /** Riga che apre il foglio: i valori arrivano solo dopo, come risultato dell'autenticazione. */
    @Suppress("DEPRECATION")
    private fun opener(form: Form, label: String, inline: android.service.autofill.InlinePresentation? = null): Dataset {
        val intent = Intent(this, FillActivity::class.java)
            .putParcelableArrayListExtra(FillActivity.EXTRA_IDS, form.ids)
            .putExtra(FillActivity.EXTRA_KINDS, form.kinds.map { it.ordinal }.toIntArray())
            .putExtra(FillActivity.EXTRA_DOMAIN, form.webDomain)
            .putExtra(FillActivity.EXTRA_PACKAGE, form.packageName)
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val sender = PendingIntent.getActivity(this, nextRequest++, intent, PendingIntent.FLAG_CANCEL_CURRENT or mutable).intentSender
        val view = Form.presentation(this, label)
        val builder = Dataset.Builder(view).setAuthentication(sender)
        if (inline != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) builder.setInlinePresentation(inline)
        form.ids.forEach { builder.setValue(it, null, view) }
        return builder.build()
    }

    private companion object {
        const val MAX_DIRECT = 4
        var nextRequest = 1
    }
}

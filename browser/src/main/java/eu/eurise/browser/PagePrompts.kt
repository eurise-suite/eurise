package eu.eurise.browser

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession.PromptDelegate as P

/**
 * Le richieste delle pagine al browser (prova del 16 novembre: non si caricava una foto). Il motore le passa al browser e
 * aspetta la risposta; senza risposta le chiudeva da solo: niente scelta dei file, niente menu a tendina (`<select>`),
 * niente avvisi e conferme dei siti, niente campi data e ora, niente accesso con nome e password del server.
 * Qui: una richiesta per volta per scheda, risposta una volta sola ([OnceAnswer]).
 */
class PageAsk(val prompt: P.BasePrompt, result: GeckoResult<P.PromptResponse>) : OnceAnswer(prompt, result) {
    fun reply(response: () -> P.PromptResponse) = answer(response)
    fun cancel() = answer { prompt.dismiss() }
}

/**
 * «Scegli un file» delle pagine: il selettore di Android (foto, file, cloud), uno o più file secondo la pagina.
 * Da creare quando si crea l'attività (registra il risultato prima che l'attività parta).
 */
class FilePicker(private val activity: ComponentActivity) {
    private var pending: PageAsk? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val ask = pending ?: return@registerForActivityResult
        pending = null
        val data = r.data
        val uris = buildList {
            data?.clipData?.let { c -> for (i in 0 until c.itemCount) c.getItemAt(i).uri?.let(::add) }
            if (isEmpty()) data?.data?.let(::add)
        }
        val p = ask.prompt as P.FilePrompt
        if (r.resultCode != ComponentActivity.RESULT_OK || uris.isEmpty()) { ask.cancel(); return@registerForActivityResult }
        ask.reply { if (uris.size == 1 || p.type != P.FilePrompt.Type.MULTIPLE) p.confirm(activity, uris[0]) else p.confirm(activity, uris.toTypedArray()) }
    }

    fun pick(ask: PageAsk) {
        val p = ask.prompt as P.FilePrompt
        pending?.cancel()
        // Le cartelle intere (webkitdirectory) il selettore di Android non le dà.
        if (p.type == P.FilePrompt.Type.FOLDER) { ask.cancel(); return }
        val types = mimes(p.mimeTypes)
        val intent = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE)
            .setType(types.singleOrNull() ?: "*/*")
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, p.type == P.FilePrompt.Type.MULTIPLE)
        if (types.size > 1) intent.putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
        pending = ask
        runCatching { launcher.launch(intent) }.onFailure { pending = null; ask.cancel() }
    }

    companion object {
        /** «image/*», «.pdf», «image/png» della pagina in tipi di Android; vuoto = qualsiasi file. */
        fun mimes(accept: Array<String>?): List<String> = accept.orEmpty().mapNotNull { a ->
            val t = a.trim().lowercase()
            when {
                t.isEmpty() -> null
                t.startsWith(".") -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(t.removePrefix("."))
                t.contains('/') -> t
                else -> null
            }
        }.distinct()
    }
}

/** La finestra della richiesta in corso nella scheda: si chiude da sola quando la pagina la ritira. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PagePromptDialog(tab: Tab) {
    val ask = tab.pagePrompt ?: return
    val done = { tab.pagePrompt = null }
    val cancel = { ask.cancel(); done() }
    val host = Links.host(tab.url).removePrefix("www.")
    when (val p = ask.prompt) {
        is P.AlertPrompt -> AlertDialog(
            onDismissRequest = cancel,
            title = { Text(p.title?.takeIf { it.isNotBlank() } ?: host) },
            text = { Text(p.message.orEmpty()) },
            confirmButton = { TextButton(onClick = cancel) { Text(stringResource(R.string.brw_ask_ok)) } },
        )
        is P.ButtonPrompt -> AlertDialog(
            onDismissRequest = cancel,
            title = { Text(p.title?.takeIf { it.isNotBlank() } ?: host) },
            text = { Text(p.message.orEmpty()) },
            confirmButton = { TextButton(onClick = { ask.reply { p.confirm(P.ButtonPrompt.Type.POSITIVE) }; done() }) { Text(stringResource(R.string.brw_ask_ok)) } },
            dismissButton = { TextButton(onClick = { ask.reply { p.confirm(P.ButtonPrompt.Type.NEGATIVE) }; done() }) { Text(stringResource(R.string.brw_ask_cancel)) } },
        )
        is P.TextPrompt -> {
            var text by remember(p) { mutableStateOf(p.defaultValue.orEmpty()) }
            AlertDialog(
                onDismissRequest = cancel,
                title = { Text(p.title?.takeIf { it.isNotBlank() } ?: host) },
                text = { Column { p.message?.takeIf { it.isNotBlank() }?.let { Text(it, modifier = Modifier.padding(bottom = 8.dp)) }; OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) } },
                confirmButton = { TextButton(onClick = { ask.reply { p.confirm(text) }; done() }) { Text(stringResource(R.string.brw_ask_ok)) } },
                dismissButton = { TextButton(onClick = cancel) { Text(stringResource(R.string.brw_ask_cancel)) } },
            )
        }
        is P.ChoicePrompt -> ChoiceDialog(p, ask, done, cancel)
        is P.DateTimePrompt -> DateTimeDialog(p, ask, done, cancel)
        is P.AuthPrompt -> {
            val onlyPassword = (p.authOptions.flags and P.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0
            var user by remember(p) { mutableStateOf(p.authOptions.username.orEmpty()) }
            var pass by remember(p) { mutableStateOf(p.authOptions.password.orEmpty()) }
            AlertDialog(
                onDismissRequest = cancel,
                title = { Text(stringResource(R.string.brw_ask_auth, host)) },
                text = {
                    Column {
                        p.message?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp) }
                        if (!onlyPassword) OutlinedTextField(user, { user = it }, label = { Text(stringResource(R.string.brw_ask_user)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(pass, { pass = it }, label = { Text(stringResource(R.string.brw_ask_password)) }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                    }
                },
                confirmButton = { TextButton(onClick = { ask.reply { if (onlyPassword) p.confirm(pass) else p.confirm(user, pass) }; done() }) { Text(stringResource(R.string.brw_ask_login)) } },
                dismissButton = { TextButton(onClick = cancel) { Text(stringResource(R.string.brw_ask_cancel)) } },
            )
        }
        is P.BeforeUnloadPrompt -> AlertDialog(
            onDismissRequest = { ask.reply { p.confirm(AllowOrDeny.DENY) }; done() },
            title = { Text(stringResource(R.string.brw_ask_leave)) },
            text = { Text(stringResource(R.string.brw_ask_leave_sub)) },
            confirmButton = { TextButton(onClick = { ask.reply { p.confirm(AllowOrDeny.ALLOW) }; done() }) { Text(stringResource(R.string.brw_ask_leave_yes)) } },
            dismissButton = { TextButton(onClick = { ask.reply { p.confirm(AllowOrDeny.DENY) }; done() }) { Text(stringResource(R.string.brw_ask_stay)) } },
        )
        is P.RepostConfirmPrompt -> AlertDialog(
            onDismissRequest = { ask.reply { p.confirm(AllowOrDeny.DENY) }; done() },
            title = { Text(stringResource(R.string.brw_ask_repost)) },
            text = { Text(stringResource(R.string.brw_ask_repost_sub)) },
            confirmButton = { TextButton(onClick = { ask.reply { p.confirm(AllowOrDeny.ALLOW) }; done() }) { Text(stringResource(R.string.brw_ask_repost_yes)) } },
            dismissButton = { TextButton(onClick = { ask.reply { p.confirm(AllowOrDeny.DENY) }; done() }) { Text(stringResource(R.string.brw_ask_cancel)) } },
        )
        is P.ColorPrompt -> {
            var text by remember(p) { mutableStateOf(p.defaultValue ?: "#000000") }
            AlertDialog(
                onDismissRequest = cancel,
                title = { Text(stringResource(R.string.brw_ask_color)) },
                text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
                confirmButton = {
                    TextButton(onClick = { if (Regex("#[0-9a-fA-F]{6}").matches(text.trim())) { ask.reply { p.confirm(text.trim().lowercase()) }; done() } }) {
                        Text(stringResource(R.string.brw_ask_ok))
                    }
                },
                dismissButton = { TextButton(onClick = cancel) { Text(stringResource(R.string.brw_ask_cancel)) } },
            )
        }
        else -> cancel()
    }
}

/** Un `<select>`: tocco su una voce per sceglierla; con scelta multipla, caselle e «OK». I gruppi restano con il loro titolo. */
@Composable
private fun ChoiceDialog(p: P.ChoicePrompt, ask: PageAsk, done: () -> Unit, cancel: () -> Unit) {
    val rows = remember(p) { flatten(p.choices) }
    val multiple = p.type == P.ChoicePrompt.Type.MULTIPLE
    var picked by remember(p) { mutableStateOf(rows.filter { !it.header && it.choice.selected }.map { it.choice.id }.toSet()) }
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = cancel,
        title = p.title?.takeIf { it.isNotBlank() }?.let { t -> { Text(t) } },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(rows) { r ->
                    if (r.header) {
                        Text(r.choice.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                    } else {
                        val c = r.choice
                        val on = c.id in picked
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 46.dp).clickable(enabled = !c.disabled) {
                                if (multiple) picked = if (on) picked - c.id else picked + c.id
                                else { ask.reply { p.confirm(c) }; done() }
                            }.padding(start = if (r.indent) 12.dp else 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (multiple) Checkbox(on, null, enabled = !c.disabled) else RadioButton(c.selected, null, enabled = !c.disabled)
                            Spacer(Modifier.width(10.dp))
                            Text(c.label, color = if (c.disabled) scheme.onSurface.copy(alpha = 0.38f) else scheme.onSurface)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (multiple) TextButton(onClick = { ask.reply { p.confirm(picked.toTypedArray()) }; done() }) { Text(stringResource(R.string.brw_ask_ok)) }
        },
        dismissButton = { TextButton(onClick = cancel) { Text(stringResource(R.string.brw_ask_cancel)) } },
    )
}

private class ChoiceRow(val choice: P.ChoicePrompt.Choice, val header: Boolean, val indent: Boolean)

private fun flatten(choices: Array<P.ChoicePrompt.Choice>, depth: Int = 0): List<ChoiceRow> = choices.flatMap { c ->
    when {
        c.separator -> emptyList()
        c.items != null -> listOf(ChoiceRow(c, header = true, indent = false)) + flatten(c.items!!, depth + 1)
        else -> listOf(ChoiceRow(c, header = false, indent = depth > 0))
    }
}

/** Data con il calendario, ora con l'orologio; mese, settimana e data con ora come testo nel formato della pagina. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeDialog(p: P.DateTimePrompt, ask: PageAsk, done: () -> Unit, cancel: () -> Unit) {
    val clear = @Composable { TextButton(onClick = { ask.reply { p.confirm("") }; done() }) { Text(stringResource(R.string.brw_ask_clear)) } }
    when (p.type) {
        P.DateTimePrompt.Type.DATE -> {
            val start = p.defaultValue?.let { runCatching { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrNull() }
            val state = rememberDatePickerState(initialSelectedDateMillis = start)
            DatePickerDialog(
                onDismissRequest = cancel,
                confirmButton = {
                    TextButton(onClick = {
                        val ms = state.selectedDateMillis
                        if (ms == null) cancel() else { ask.reply { p.confirm(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate().toString()) }; done() }
                    }) { Text(stringResource(R.string.brw_ask_ok)) }
                },
                dismissButton = clear,
            ) { DatePicker(state) }
        }
        P.DateTimePrompt.Type.TIME -> {
            val (h, m) = p.defaultValue?.split(":")?.mapNotNull { it.take(2).toIntOrNull() }?.takeIf { it.size >= 2 }?.let { it[0] to it[1] } ?: (9 to 0)
            val state = rememberTimePickerState(h, m, is24Hour = true)
            AlertDialog(
                onDismissRequest = cancel,
                text = { TimePicker(state) },
                confirmButton = { TextButton(onClick = { ask.reply { p.confirm("%02d:%02d".format(state.hour, state.minute)) }; done() }) { Text(stringResource(R.string.brw_ask_ok)) } },
                dismissButton = clear,
            )
        }
        else -> {
            val hint = when (p.type) {
                P.DateTimePrompt.Type.MONTH -> "2026-11"
                P.DateTimePrompt.Type.WEEK -> "2026-W47"
                else -> "2026-11-16T09:30"
            }
            var text by remember(p) { mutableStateOf(p.defaultValue.orEmpty()) }
            AlertDialog(
                onDismissRequest = cancel,
                text = { OutlinedTextField(text, { text = it }, placeholder = { Text(hint) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
                confirmButton = { TextButton(onClick = { ask.reply { p.confirm(text.trim()) }; done() }) { Text(stringResource(R.string.brw_ask_ok)) } },
                dismissButton = clear,
            )
        }
    }
}

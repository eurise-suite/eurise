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
import eu.eurise.core.suite.SuiteAccess
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
 *
 * GeckoView carica solo file con un percorso vero (`file://`): gli indirizzi `content://` che dà il selettore (foto,
 * Download, cloud) li perdeva quando non ne ricavava il percorso, uno o tutti (prova del 22 novembre). Come Firefox, i
 * file scelti si copiano in una cartella privata ([UPLOADS], dentro la cache) e la pagina riceve le copie. Si accettano
 * solo file di altre app ([SuiteAccess.isForeignUri]: mai il profilo o le password del Browser); le copie si cancellano
 * dopo [KEEP_MS] e a ogni avvio del Browser.
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
        }.distinct().filter { SuiteAccess.isForeignUri(activity, it) }
        if (r.resultCode != android.app.Activity.RESULT_OK || uris.isEmpty()) { ask.cancel(); return@registerForActivityResult }
        val p = ask.prompt as P.FilePrompt
        val wanted = if (p.type == P.FilePrompt.Type.MULTIPLE) uris else uris.take(1)
        val app = activity.applicationContext
        Thread {
            val copies = copy(app, wanted)
            activity.runOnUiThread {
                when {
                    copies.isEmpty() -> {
                        ask.cancel()
                        android.widget.Toast.makeText(activity, R.string.brw_upload_failed, android.widget.Toast.LENGTH_LONG).show()
                    }
                    copies.size == 1 || p.type != P.FilePrompt.Type.MULTIPLE -> ask.reply { p.confirm(activity, copies[0]) }
                    else -> ask.reply { p.confirm(activity, copies.toTypedArray()) }
                }
                if (copies.isNotEmpty() && copies.size < wanted.size) {
                    android.widget.Toast.makeText(activity, activity.getString(R.string.brw_upload_partial, copies.size, wanted.size), android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    init {
        val app = activity.applicationContext
        Thread { sweep(app, KEEP_MS) }.start()
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
        /** La cartella delle copie da caricare, dentro la cache privata del Browser. */
        private const val UPLOADS = "uploads"
        /** Quanto restano le copie: il tempo di compilare e inviare il modulo della pagina. */
        private const val KEEP_MS = 6 * 60 * 60 * 1000L

        /**
         * Copia ogni file scelto in una cartella nuova sotto [UPLOADS], con il suo nome ripulito (la pagina lo mostra),
         * e restituisce gli indirizzi `file://` delle copie riuscite. Fuori dal thread principale.
         */
        internal fun copy(context: android.content.Context, uris: List<Uri>): List<Uri> {
            sweep(context, KEEP_MS)
            val root = java.io.File(context.cacheDir, UPLOADS)
            return uris.mapIndexedNotNull { i, uri ->
                runCatching {
                    val dir = java.io.File(root, java.util.UUID.randomUUID().toString()).apply { mkdirs() }
                    val target = java.io.File(dir, safeName(displayName(context, uri), context.contentResolver.getType(uri), i))
                    val ok = context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { input.copyTo(it, 64 * 1024) }
                        true
                    } ?: false
                    if (!ok) { dir.deleteRecursively(); null } else Uri.fromFile(target)
                }.getOrNull()
            }
        }

        /** Cancella le copie più vecchie di [maxAgeMs]. */
        internal fun sweep(context: android.content.Context, maxAgeMs: Long) {
            val now = System.currentTimeMillis()
            java.io.File(context.cacheDir, UPLOADS).listFiles().orEmpty()
                .filter { now - it.lastModified() > maxAgeMs }
                .forEach { runCatching { it.deleteRecursively() } }
        }

        private fun displayName(context: android.content.Context, uri: Uri): String? = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment

        /**
         * Un nome di file sicuro: niente cartelle («../»), niente caratteri di controllo o riservati, al massimo 120
         * caratteri; senza nome si usa «file» con l'estensione del tipo.
         */
        internal fun safeName(name: String?, mime: String?, index: Int): String {
            val base = name.orEmpty().substringAfterLast('/').substringAfterLast('\\')
                .filter { it >= ' ' && it !in "\\/:*?\"<>|" }.trim().trimStart('.')
            if (base.isNotEmpty()) return if (base.length <= 120) base else {
                val ext = base.substringAfterLast('.', "").take(10)
                if (ext.isEmpty()) base.take(120) else base.take(120 - ext.length - 1) + "." + ext
            }
            val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            return "file${if (index > 0) "-${index + 1}" else ""}${ext?.let { ".$it" }.orEmpty()}"
        }

        /** I tipi accettati dalla pagina (`image/` con l'asterisco, «.pdf», «image/png») in tipi di Android; vuoto = qualsiasi file. */
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

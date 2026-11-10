package eu.eurise.feature.calculator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.data.calc.Calc
import eu.eurise.core.designsystem.component.FilterPill
import eu.eurise.core.designsystem.icon.EuriseIcons
import org.json.JSONArray
import org.json.JSONObject

private enum class Mode(val label: Int) { Calc(R.string.calc_mode_calc), Pro(R.string.calc_mode_pro), Units(R.string.calc_mode_units), Graph(R.string.calc_mode_graph) }

/** Una riga del nastro: il calcolo, il risultato e, se la persona la dà, un'etichetta ("preventivo Bianchi"). */
internal data class TapeLine(val expr: String, val result: String, val label: String = "")

/** Il nastro dei calcoli resta sul telefono, gli ultimi 60. */
internal class Tape(context: Context) {
    private val prefs = context.getSharedPreferences("eurise_calculator", Context.MODE_PRIVATE)

    fun load(): List<TapeLine> = runCatching {
        val a = JSONArray(prefs.getString("tape", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> TapeLine(o.getString("e"), o.getString("r"), o.optString("l")) } }
    }.getOrDefault(emptyList())

    fun save(lines: List<TapeLine>) {
        val a = JSONArray(lines.takeLast(60).map { JSONObject().put("e", it.expr).put("r", it.result).put("l", it.label) })
        prefs.edit().putString("tape", a.toString()).apply()
    }

    var degrees: Boolean
        get() = prefs.getBoolean("degrees", true)
        set(v) { prefs.edit().putBoolean("degrees", v).apply() }

    fun rate(key: String, default: String): String = prefs.getString("rate_$key", default) ?: default
    fun setRate(key: String, value: String) { prefs.edit().putString("rate_$key", value).apply() }
}

internal fun Context.copyText(text: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Eurise", text))
    Toast.makeText(this, R.string.calc_copied, Toast.LENGTH_SHORT).show()
}

/**
 * La calcolatrice di Eurise: Calcolo (nastro e risultato mentre si scrive, funzioni scientifiche), Professionale
 * (fattura, scorporo, sconto e ricarico, rata, giorni), Unità e Grafico. Tutto sul telefono.
 *
 * @param onSaveNote crea una nota con il testo dato (il nastro).
 */
@Composable
fun CalculatorRoute(onBack: () -> Unit, onSaveNote: (String, String) -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val tape = remember { Tape(context) }
    var mode by rememberSaveable { mutableStateOf(Mode.Calc) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(scheme.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(EuriseIcons.chevronLeft(), stringResource(R.string.calc_back), tint = scheme.primary)
            }
            Text(stringResource(R.string.calc_title), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        LazyRow(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        ) {
            items(Mode.entries.toList()) { m -> FilterPill(stringResource(m.label), null, m == mode) { mode = m } }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (mode) {
                Mode.Calc -> CalcScreen(tape, onSaveNote)
                Mode.Pro -> ProScreen(tape)
                Mode.Units -> UnitsScreen()
                Mode.Graph -> GraphScreen(tape)
            }
        }
    }
}

// --- Calcolo ---

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun CalcScreen(tape: Tape, onSaveNote: (String, String) -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val lines = remember { mutableStateListOf<TapeLine>().apply { addAll(tape.load()) } }
    var expr by rememberSaveable { mutableStateOf("") }
    var degrees by remember { mutableStateOf(tape.degrees) }
    var scientific by rememberSaveable { mutableStateOf(false) }
    var labeling by remember { mutableStateOf<Int?>(null) }
    val preview = remember(expr, degrees) { Calc.preview(clean(expr), degrees)?.let { Calc.format(it) } }
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1) }

    fun commit() {
        val r = preview ?: return
        lines += TapeLine(shown(expr), r)
        tape.save(lines)
        expr = r.replace(".", "")
    }

    fun press(k: String) {
        expr = when (k) {
            "C" -> ""
            "⌫" -> expr.dropLast(1)
            "( )" -> {
                val open = expr.count { it == '(' } - expr.count { it == ')' }
                expr + if (open > 0 && expr.lastOrNull()?.let { it.isDigit() || it == ')' || it == '%' || it == '²' || it == 'π' } == true) ")" else "("
            }
            "sin", "cos", "tan", "ln", "log", "√" -> expr + (if (k == "√") "√(" else "$k(")
            "x²" -> "$expr²"
            "xʸ" -> "$expr^"
            "n!" -> "$expr!"
            else -> expr + k
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Column(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(18.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            if (lines.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.calc_tape_empty), fontSize = 13.sp, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState) {
                    items(lines.size) { i ->
                        val line = lines[i]
                        Row(
                            Modifier.fillMaxWidth()
                                .combinedClickable(onClick = { expr += line.result.replace(".", "") }, onLongClick = { labeling = i })
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (line.label.isNotBlank()) Text(line.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = scheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.weight(1f))
                            Text("${line.expr} ", fontSize = 14.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("= ${line.result}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    SmallAction(stringResource(R.string.calc_tape_note)) {
                        onSaveNote(context.getString(R.string.calc_tape_note_title), lines.joinToString("\n") { (if (it.label.isNotBlank()) it.label + ": " else "") + "${it.expr} = ${it.result}" })
                        Toast.makeText(context, R.string.calc_tape_saved, Toast.LENGTH_SHORT).show()
                    }
                    SmallAction(stringResource(R.string.calc_tape_copy)) { context.copyText(lines.joinToString("\n") { "${it.expr} = ${it.result}" }) }
                    SmallAction(stringResource(R.string.calc_tape_clear)) { lines.clear(); tape.save(lines) }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), horizontalAlignment = Alignment.End) {
            Text(shown(expr).ifEmpty { "0" }, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
            Text(
                preview?.let { "= $it" } ?: " ",
                fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1,
                modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { preview?.let { context.copyText(it) } }),
            )
        }
        if (scientific) {
            KeyGrid(
                listOf(listOf("sin", "cos", "tan", "√", "xʸ", "π"), listOf("ln", "log", "x²", "n!", "e", if (degrees) "DEG" else "RAD")),
                small = true,
            ) { k ->
                if (k == "DEG" || k == "RAD") { degrees = !degrees; tape.degrees = degrees } else press(k)
            }
            Spacer(Modifier.height(6.dp))
        }
        KeyGrid(
            listOf(
                listOf("C", "( )", "%", "÷"),
                listOf("7", "8", "9", "×"),
                listOf("4", "5", "6", "−"),
                listOf("1", "2", "3", "+"),
                listOf(if (scientific) "123" else "f(x)", "0", ",", "⌫", "="),
            ),
        ) { k ->
            when (k) {
                "=" -> commit()
                "f(x)", "123" -> scientific = !scientific
                else -> press(k)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    labeling?.let { i ->
        var text by remember(i) { mutableStateOf(lines.getOrNull(i)?.label.orEmpty()) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { labeling = null },
            title = { Text(stringResource(R.string.calc_label_title)) },
            text = { OutlinedTextField(text, { text = it }, singleLine = true, placeholder = { Text(stringResource(R.string.calc_label_hint)) }) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    lines.getOrNull(i)?.let { lines[i] = it.copy(label = text.trim()); tape.save(lines) }
                    labeling = null
                }) { Text(stringResource(R.string.calc_ok)) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { labeling = null }) { Text(stringResource(R.string.calc_cancel)) } },
        )
    }
}

/** Dalla scrittura a schermo a quella che il motore legge. */
private fun clean(text: String) = text.replace('×', '*').replace('÷', '/').replace('−', '-')

private fun shown(text: String) = text.replace('*', '×').replace('/', '÷')

@Composable
private fun SmallAction(label: String, onClick: () -> Unit) {
    Text(
        label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** Tastierino: le righe possono avere un numero diverso di tasti, "=" è sempre pieno d'accento. */
@Composable
internal fun KeyGrid(rows: List<List<String>>, small: Boolean = false, onKey: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { k ->
                    val digit = k.length == 1 && (k[0].isDigit() || k == ",")
                    val accent = k == "="
                    Box(
                        Modifier.weight(1f).height(if (small) 40.dp else 56.dp).clip(RoundedCornerShape(if (small) 12.dp else 18.dp))
                            .background(
                                when {
                                    accent -> scheme.primary
                                    digit -> scheme.surfaceContainerLow
                                    small -> scheme.surfaceVariant
                                    else -> scheme.primaryContainer
                                },
                            )
                            .clickable(role = Role.Button, onClickLabel = k) { onKey(k) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == "⌫") Icon(EuriseIcons.chevronLeft(), stringResource(R.string.calc_delete), tint = scheme.onSurface)
                        else Text(
                            k,
                            fontSize = if (small) 14.sp else if (k.length > 2) 15.sp else 24.sp,
                            fontWeight = if (accent || !digit) FontWeight.SemiBold else FontWeight.Normal,
                            color = when {
                                accent -> scheme.onPrimary
                                digit -> scheme.onSurface
                                small -> scheme.onSurface
                                else -> scheme.onPrimaryContainer
                            },
                        )
                    }
                }
            }
        }
    }
}

// --- Campi numerici comuni ---

@Composable
internal fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, suffix: String? = null) {
    OutlinedTextField(
        value, onChange,
        label = { Text(label) },
        singleLine = true,
        suffix = suffix?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Un numero scritto all'italiana ("1.250,50") o con il punto; null se non è un numero. */
internal fun parseAmount(text: String): java.math.BigDecimal? =
    runCatching { java.math.BigDecimal.valueOf(Calc.evaluate(text.trim())) }.getOrNull()

@Composable
internal fun ResultRow(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = if (strong) 15.5.sp else 14.5.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal, color = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontSize = if (strong) 15.5.sp else 14.5.sp, fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
internal fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(vertical = 4.dp)) { content() }
}

@Composable
internal fun ScrollColumn(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

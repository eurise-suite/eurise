package eu.eurise.feature.assistant

import eu.eurise.core.data.alfred.Address
import eu.eurise.core.data.alfred.addressSuffix
import eu.eurise.core.data.alfred.AlfredPrefs
import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.eurise.core.contacts.Contacts
import eu.eurise.core.designsystem.icon.EuriseIcons
import eu.eurise.core.designsystem.component.ServiceConsentSheet
import eu.eurise.core.suite.PrivacyCenter
import eu.eurise.core.suite.PrivacyService
import eu.eurise.feature.assistant.online.AiService
import eu.eurise.feature.assistant.online.AiServices
import eu.eurise.feature.assistant.online.AiWindowActivity
import eu.eurise.feature.assistant.online.AnswerView
import eu.eurise.feature.assistant.online.AskBox
import eu.eurise.feature.assistant.online.ChooseView
import eu.eurise.feature.assistant.online.Redaction
import eu.eurise.feature.assistant.online.Redactor
import eu.eurise.feature.assistant.online.WaysLegend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

private val Panel = Color(0xE6181E2C)
private val Line = Color(0x24FFFFFF)
private val Muted = Color(0xFFA9B4C4)
private val Soft = Color(0xFFCBD5E1)
private val Gold = Color(0xFFE9C77B)
private val Safe = Color(0xFF9FD3B0)
private val Ink = Color(0xFF1E1D1B)
private val InkSoft = Color(0xFF56564F)
private val Navy = Color(0xFF1F2A44)
private val Mic = Brush.linearGradient(listOf(Color(0xFFFF9A5B), Color(0xFFFF3D77)))
private val Seal = Brush.linearGradient(listOf(Color(0xFF3E5C8A), Color(0xFF8A3A5C)))

private sealed interface Phase {
    data object Ready : Phase
    data object Thinking : Phase
    /** Alfred ascolta: il microfono è aperto, si chiude da solo quando si smette di parlare. */
    data object Listening : Phase
    /** La frase è finita: Eurise Voce la sta trascrivendo. */
    data object Working : Phase
    /** Qualcosa impedisce di ascoltare: si dice cosa, e cosa fare. */
    data class Trouble(val problem: Ear.Problem) : Phase
    data class Confirm(val card: Card) : Phase
    data class Said(val line: String) : Phase
    data object Settings : Phase
    /** «Chiedi a…»: pressione lunga sul globo (tavola AlfScegli). */
    data object Choose : Phase
    /** La risposta dell'AI online tornata in Alfred, con i dati rimessi (tavola AlfRisposta). */
    data class Answer(val text: String, val from: String) : Phase
}

/**
 * Il pannello di Alfred in basso, sopra l'app in cui si è (tavole AstAscolto, AstConferma, AstMessaggio, AstLibera,
 * AstImpostazioni): si parla o si scrive, Alfred prepara la scheda, il tocco sul pulsante agisce.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlfredPanel(dictation: Dictation, initial: String?, onClose: () -> Unit, listenNow: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { AlfredPrefs(context) }
    val butler = remember { Butler(context.applicationContext) }
    var address by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf<Phase>(Phase.Ready) }
    var heard by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    var readAloud by remember { mutableStateOf(prefs.readAloud) }
    val speaker = remember { lazy { Speaker(context) } }
    DisposableEffect(Unit) { onDispose { if (speaker.isInitialized()) speaker.value.release() } }
    val ear = remember { Ear(context) }
    DisposableEffect(Unit) { onDispose { ear.cancel() } }
    var level by remember { mutableStateOf(0f) }
    var speaking by remember { mutableStateOf(false) }
    /** Alfred aspetta a voce «sì» o «no» sulla scheda (solo se gli si è parlato). */
    var answering by remember { mutableStateOf(false) }

    // AI online (tavole AlfUnico, AlfScegli, AlfFinestra, AlfRisposta): la domanda esce ripulita, nella finestra AI.
    val services = remember { AiServices(context) }
    var favorite by remember { mutableStateOf(services.preferred()) }
    val leftHanded = remember { context.getSharedPreferences("eurise_launcher", android.content.Context.MODE_PRIVATE).getBoolean("left_handed", false) }
    var redaction by remember { mutableStateOf<Redaction?>(null) }
    var consentFor by remember { mutableStateOf<AiService?>(null) }
    val onlineWindow = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val back = result.data?.getStringExtra(AiWindowActivity.EXTRA_ANSWER)
        if (result.resultCode == android.app.Activity.RESULT_OK && !back.isNullOrBlank()) {
            phase = Phase.Answer(redaction?.restore(back) ?: back, result.data?.getStringExtra(AiWindowActivity.EXTRA_NAME).orEmpty())
        }
    }

    /** La domanda da mandare online: quella scritta, o l'ultima detta. */
    fun question(): String = typed.ifBlank { heard }.trim()

    /** Toglie i dati personali sul telefono e apre il servizio con la domanda già scritta. */
    fun sendOnline(s: AiService) {
        val q = question()
        scope.launch {
            val names = withContext(Dispatchers.IO) {
                val people = if (Contacts.canRead(context)) runCatching { Contacts.all(context).map { it.name } }.getOrDefault(emptyList()) else emptyList()
                people + listOf(prefs.name).filter { it.isNotBlank() }
            }
            val r = Redactor.redact(q, names)
            redaction = r
            withContext(Dispatchers.IO) { PrivacyCenter.log(PrivacyService.AiOnline) }
            if (s.isApp) {
                val ok = services.shareTo(s, r.text)
                phase = Phase.Said(context.getString(if (ok) R.string.alfred_sent_to_app else R.string.alfred_app_failed, s.name))
                return@launch
            }
            onlineWindow.launch(
                Intent(context, AiWindowActivity::class.java)
                    .putExtra(AiWindowActivity.EXTRA_URL, s.startUrl(r.text))
                    .putExtra(AiWindowActivity.EXTRA_NAME, s.name)
                    .putExtra(AiWindowActivity.EXTRA_SCORE, s.score)
                    .putExtra(AiWindowActivity.EXTRA_QUESTION, r.text)
                    .putExtra(AiWindowActivity.EXTRA_PREFILLED, s.prefill.isNotEmpty() && r.text.isNotEmpty()),
            )
        }
    }

    /** Il globo: la prima volta la spiegazione del centro privacy (servizio spento di base), poi subito il servizio. */
    fun askOnline(s: AiService) {
        scope.launch {
            val on = withContext(Dispatchers.IO) { PrivacyCenter.allowed(PrivacyService.AiOnline) }
            if (on) sendOnline(s) else consentFor = s
        }
    }

    fun say(line: String, onDone: (() -> Unit)? = null) {
        if (readAloud) speaker.value.say(line, onDone) else onDone?.invoke()
    }

    fun execute(card: Card) {
        answering = false
        ear.cancel()
        scope.launch {
            val out = butler.run(card)
            phase = Phase.Said(out.line)
            say(out.line)
            if (out.close) { delay(900L + out.line.length * 35L); onClose() }
        }
    }

    lateinit var understandSaid: (String, Boolean) -> Unit

    /** Dopo la scheda letta a voce, Alfred ascolta la risposta: «sì» agisce, «no» annulla, altro è una correzione. */
    fun askAnswer(card: Card) {
        if ((phase as? Phase.Confirm)?.card != card) return
        answering = true
        ear.listen(
            onLevel = { level = it }, onSpeech = {}, onWorking = {},
            onText = { said ->
                answering = false
                if ((phase as? Phase.Confirm)?.card != card) return@listen
                when (Grammar.answer(said)) {
                    Grammar.Answer.Yes -> execute(card)
                    Grammar.Answer.No -> { heard = ""; phase = Phase.Ready; say(context.getString(R.string.alfred_cancelled)) }
                    Grammar.Answer.Other -> understandSaid(said, true)
                }
            },
            onFail = { answering = false },
        )
    }

    understandSaid = { text, spoken ->
        if (text.isNotBlank()) {
            heard = text.trim()
            typed = ""
            phase = Phase.Thinking
            scope.launch {
                val card = butler.plan(Grammar.parse(heard, LocalDateTime.now()))
                // Segnala in guida: niente scheda né «Procedo?», chi guida non deve toccare nulla (tavola MapSegnalaGuida).
                if (card.kind == Kind.Report) { execute(card); return@launch }
                phase = Phase.Confirm(card)
                val canAct = card.ready && (card.kind != Kind.Call ||
                    context.checkSelfPermission(Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                if (spoken && readAloud && canAct) say(card.lead + " " + context.getString(R.string.alfred_ask_confirm)) { askAnswer(card) }
                else say(card.lead)
            }
        }
    }
    fun understand(text: String) = understandSaid(text, false)

    fun replan(command: Command) {
        answering = false
        ear.cancel()
        phase = Phase.Thinking
        scope.launch { phase = Phase.Confirm(butler.plan(command)) }
    }

    /** Un tocco e Alfred ascolta; quando si smette di parlare capisce da sé che si è finito e risponde. */
    fun listenNow() {
        if (speaker.isInitialized()) speaker.value.stop()
        speaking = false
        answering = false
        level = 0f
        phase = Phase.Listening
        ear.listen(
            onLevel = { level = it },
            onSpeech = { speaking = true },
            onWorking = { phase = Phase.Working },
            onText = { understandSaid(it, true) },
            onFail = { problem -> phase = Phase.Trouble(problem) },
        )
    }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) listenNow() else phase = Phase.Trouble(Ear.Problem.Mic)
    }
    fun voice() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) listenNow()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        (phase as? Phase.Confirm)?.let { replan(it.card.command) }
    }
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        (phase as? Phase.Confirm)?.let { c -> execute(c.card) }
    }

    // La prima volta Alfred chiede come chiamare chi usa il telefono (il nome si cambia poi dalle disposizioni).
    var askingName by remember { mutableStateOf(prefs.address == Address.Name && prefs.name.isBlank()) }
    var suggestion by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        address = addressSuffix(prefs)
        if (askingName) suggestion = ownFirstName(context)
        if (!initial.isNullOrBlank() && !askingName) understand(initial)
        else if (listenNow && !askingName) voice()
        else if (readAloud) say(context.getString(if (askingName) R.string.alfred_ask_name else R.string.alfred_greeting, address))
    }

    fun introduce(name: String) {
        val n = name.trim().split(Regex("\\s+")).take(3).joinToString(" ").replaceFirstChar { it.uppercase() }.take(30)
        if (n.isBlank()) return
        prefs.name = n
        address = addressSuffix(prefs)
        askingName = false
        typed = ""
        val line = context.getString(R.string.alfred_pleased, n)
        phase = Phase.Said(line)
        say(line)
    }

    Box(
        Modifier.fillMaxSize().background(Color(0x4D141826))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.navigationBarsPadding().imePadding().padding(10.dp).fillMaxWidth()
                .heightIn(max = 760.dp)
                .clip(RoundedCornerShape(28.dp)).background(Panel).border(1.dp, Line, RoundedCornerShape(28.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (askingName && phase == Phase.Ready) {
                Header(stringResource(R.string.alfred_ask_name), Mood.Hello, onSettings = { phase = Phase.Settings })
                suggestion?.let { OutlineChip(stringResource(R.string.alfred_name_suggest, it)) { introduce(it) } }
                InputRow(value = typed, onValue = { typed = it }, onSend = { introduce(typed) }, voice = false, onVoice = {},
                    hint = stringResource(R.string.alfred_s_name_hint))
                OutlineChip(stringResource(R.string.alfred_name_skip)) { prefs.address = Address.None; askingName = false }
            } else when (val p = phase) {
                Phase.Ready, Phase.Thinking -> {
                    Header(
                        if (p == Phase.Thinking) stringResource(R.string.alfred_transcribing) else stringResource(R.string.alfred_greeting, address),
                        if (p == Phase.Thinking) Mood.Thinking else Mood.Hello,
                        onSettings = { phase = Phase.Settings },
                    )
                    if (heard.isNotEmpty() && p == Phase.Thinking) Quote(heard)
                    Text(stringResource(R.string.alfred_hint), fontSize = 12.sp, lineHeight = 18.sp, color = Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(R.string.alfred_ex_call, R.string.alfred_ex_note, R.string.alfred_ex_route, R.string.alfred_ex_message,
                            R.string.alfred_ex_search, R.string.alfred_ex_alarm).forEach { id ->
                            val label = stringResource(id)
                            OutlineChip(label) { if (label.endsWith("…")) typed = label.removeSuffix("…") else understand(label) }
                        }
                    }
                    WaysLegend(favorite)
                    AskBox(
                        value = typed, onValue = { typed = it }, hint = stringResource(R.string.alfred_input_hint),
                        favorite = favorite.name, leftHanded = leftHanded, voice = ear.available, onVoice = { voice() },
                        onSend = { understand(typed) }, onOnline = { askOnline(favorite) }, onChoose = { phase = Phase.Choose },
                        onPc = { phase = Phase.Said(context.getString(R.string.alfred_pc_soon)) },
                    )
                    Text(stringResource(R.string.alfred_how_to_open), fontSize = 11.5.sp, color = Muted, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
                Phase.Listening -> {
                    Header(stringResource(if (speaking) R.string.alfred_listening else R.string.alfred_listen_go, address), Mood.Attentive, onSettings = {})
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            Modifier.size(64.dp).clip(CircleShape).background(Mic).clickable { ear.finish() }
                                .semantics { contentDescription = context.getString(R.string.alfred_listen_done) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size((30 + 30 * level).dp).clip(CircleShape).background(Color(0x40FFFFFF)))
                            Icon(EuriseIcons.mic(2.2f), null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Text(stringResource(R.string.alfred_listen_hint), fontSize = 12.5.sp, lineHeight = 18.sp, color = Muted, modifier = Modifier.weight(1f))
                    }
                    OutlineChip(stringResource(R.string.alfred_cancel)) { ear.cancel(); phase = Phase.Ready }
                }
                Phase.Working -> {
                    Header(stringResource(R.string.alfred_working), Mood.Thinking, onSettings = {})
                    Text(stringResource(R.string.alfred_on_phone), fontSize = 12.sp, color = Muted)
                }
                is Phase.Trouble -> {
                    val line = when (p.problem) {
                        Ear.Problem.Nothing -> stringResource(R.string.alfred_p_nothing, address)
                        Ear.Problem.NoModel -> stringResource(R.string.alfred_p_no_model)
                        Ear.Problem.NoEngine -> stringResource(R.string.alfred_p_no_engine)
                        Ear.Problem.Mic -> stringResource(R.string.alfred_p_mic)
                        Ear.Problem.Failed -> stringResource(R.string.alfred_p_failed)
                    }
                    Header(line, Mood.Attentive, onSettings = { phase = Phase.Settings })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (p.problem == Ear.Problem.NoModel) OutlineChip(stringResource(R.string.alfred_p_get_model)) { AlfredTts.openModels(context) }
                        if (p.problem != Ear.Problem.NoEngine) OutlineChip(stringResource(R.string.alfred_p_again)) { voice() }
                        OutlineChip(stringResource(R.string.alfred_p_write)) { phase = Phase.Ready }
                    }
                }
                is Phase.Confirm -> {
                    val card = p.card
                    Header(card.lead, Mood.Attentive, onSettings = { phase = Phase.Settings })
                    Quote(heard)
                    CardView(card, onChange = { key, value ->
                        val c = card.command
                        phase = when {
                            key == "to" && c is Command.Message -> { replan(c.copy(who = value)); Phase.Thinking }
                            key == "to" && c is Command.Call -> { replan(c.copy(who = value)); Phase.Thinking }
                            key == "via" && c is Command.Message -> {
                                replan(c.copy(channel = Channel.entries.firstOrNull { butler.channelName(it) == value })); Phase.Thinking
                            }
                            else -> Phase.Confirm(card.with(key, value))
                        }
                    })
                    if (!Contacts.canRead(context) && card.kind in setOf(Kind.Call, Kind.Message, Kind.Event)) {
                        OutlineChip(stringResource(R.string.alfred_need_contacts)) { contactsPermission.launch(Manifest.permission.READ_CONTACTS) }
                    }
                    if (card.kind == Kind.Free) OutlineChip(stringResource(R.string.alfred_ask_online, favorite.name)) { askOnline(favorite) }
                    if (card.chips.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            card.chips.forEach { chip -> OutlineChip(chip.label) { replan(chip.command) } }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigButton(stringResource(R.string.alfred_cancel), primary = false, enabled = true, modifier = Modifier.weight(1f)) {
                            answering = false
                            ear.cancel()
                            heard = ""
                            phase = Phase.Ready
                        }
                        BigButton(card.action, primary = true, enabled = card.ready, modifier = Modifier.weight(1.4f)) {
                            if (card.kind == Kind.Call && context.checkSelfPermission(Manifest.permission.CALL_PHONE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                answering = false
                                ear.cancel()
                                callPermission.launch(Manifest.permission.CALL_PHONE)
                            } else execute(card)
                        }
                    }
                    if (answering) Text(stringResource(R.string.alfred_answer_hint), fontSize = 12.sp, color = Gold, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.alfred_last_step), fontSize = 11.5.sp, color = Muted, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
                is Phase.Said -> {
                    Header(p.line, Mood.Bow, onSettings = { phase = Phase.Settings })
                    AskBox(
                        value = typed, onValue = { typed = it }, hint = stringResource(R.string.alfred_input_hint),
                        favorite = favorite.name, leftHanded = leftHanded, voice = ear.available, onVoice = { voice() },
                        onSend = { understand(typed) }, onOnline = { askOnline(favorite) }, onChoose = { phase = Phase.Choose },
                        onPc = { phase = Phase.Said(context.getString(R.string.alfred_pc_soon)) },
                    )
                }
                Phase.Choose -> ChooseView(
                    services, question = question().let { if (it.isEmpty()) it else Redactor.redact(it).text }, favorite = favorite,
                    onFavorite = { services.setPreferred(it); favorite = it },
                    onPick = { askOnline(it) },
                    onBack = { phase = Phase.Ready },
                )
                is Phase.Answer -> {
                    Header(stringResource(R.string.alfred_answer_line, p.from), Mood.Bow, onSettings = { phase = Phase.Settings })
                    AnswerView(
                        p.text,
                        onNote = { replan(Command.Note(p.text)) },
                        onTask = { replan(Command.Task(p.text.lineSequence().first { it.isNotBlank() }.take(90), null, null)) },
                        onSend = { replan(Command.Message("", p.text, null)) },
                    )
                }
                Phase.Settings -> SettingsView(prefs, dictation.source, readAloud, natural = speaker.isInitialized() && speaker.value.natural,
                    onReadAloud = { readAloud = it; prefs.readAloud = it },
                    onAddress = { address = addressSuffix(prefs) },
                    onTry = { understand(context.getString(R.string.alfred_try_text)) },
                    onBack = { phase = Phase.Ready })
            }
        }
        consentFor?.let { s ->
            ServiceConsentSheet(PrivacyService.AiOnline, onDismiss = { consentFor = null }, onAccept = { consentFor = null; sendOnline(s) })
        }
    }
}

@Composable
private fun Header(line: String, mood: Mood, onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(Seal).clickable(onClick = onSettings)
                .semantics { contentDescription = "Alfred" },
            contentAlignment = Alignment.Center,
        ) { BowTie(mood, Modifier.size(19.dp)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(stringResource(R.string.alfred_name).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.9.sp, color = Gold)
            Text(line, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, color = Soft)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(EuriseIcons.shield(2.4f), null, tint = Safe, modifier = Modifier.size(12.dp))
            Text(stringResource(R.string.alfred_on_phone), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Safe)
        }
        // Le impostazioni anche da un'icona visibile (prova del 13 novembre: col solo tondo del papillon non si trovavano).
        Box(
            Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onSettings)
                .semantics { contentDescription = "Impostazioni di Alfred" },
            contentAlignment = Alignment.Center,
        ) { Icon(EuriseIcons.gear(2.2f), null, tint = Soft, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun Quote(text: String) {
    if (text.isNotBlank()) Text("“$text”", fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, color = Color.White)
}

@Composable
private fun OutlineChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 34.dp).clip(RoundedCornerShape(17.dp)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(17.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White) }
}

@Composable
private fun InputRow(
    value: String, onValue: (String) -> Unit, onSend: () -> Unit, voice: Boolean, onVoice: () -> Unit,
    hint: String = stringResource(R.string.alfred_input_hint),
) {
    val label = stringResource(R.string.alfred_input_label)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(23.dp)).background(Color(0x1AFFFFFF)).padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) Text(hint, fontSize = 14.5.sp, color = Muted)
            BasicTextField(
                value, onValue, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.5.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
        val send = value.isNotBlank() || !voice
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(Mic).clickable { if (send) onSend() else onVoice() }
                .semantics { contentDescription = if (send) "Invia" else "Parla" },
            contentAlignment = Alignment.Center,
        ) { Icon(if (send) EuriseIcons.send(2.2f) else EuriseIcons.mic(2.2f), null, tint = Color.White, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun CardView(card: Card, onChange: (String, String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val (bg, fg) = when (card.kind) {
                Kind.Event, Kind.Alarm, Kind.Timer -> Color(0xFFDDE3F0) to Color(0xFF3E5C8A)
                Kind.Message, Kind.Call -> Color(0xFFDCE7DF) to Color(0xFF2F6B45)
                Kind.Free, Kind.Note, Kind.Task -> Color(0xFFF3E2CF) to Color(0xFF7A4A2A)
                else -> Color(0xFFE6E1EC) to Color(0xFF5B4A7A)
            }
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(bg), contentAlignment = Alignment.Center) {
                Icon(kindIcon(card.kind), null, tint = fg, modifier = Modifier.size(17.dp))
            }
            Text(stringResource(card.title), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        }
        card.fields.forEachIndexed { i, f ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x121E1D1B)))
            FieldRow(f) { onChange(f.key, it) }
        }
        card.note?.let { Text(it, fontSize = 11.5.sp, lineHeight = 16.sp, color = InkSoft) }
    }
}

private fun kindIcon(kind: Kind) = when (kind) {
    Kind.Event -> EuriseIcons.calendar(2.3f)
    Kind.Message -> EuriseIcons.chat(2.3f)
    Kind.Call -> EuriseIcons.phone(2.3f)
    Kind.Note -> EuriseIcons.note(2.3f)
    Kind.Task -> EuriseIcons.task(2.3f)
    Kind.Route, Kind.Report -> EuriseIcons.map(2.3f)
    Kind.Alarm, Kind.Timer -> EuriseIcons.clock(2.3f)
    Kind.Search -> EuriseIcons.search(2.3f)
    Kind.Open -> EuriseIcons.grid(2.3f)
    Kind.Free -> EuriseIcons.spark(2.3f)
}

@Composable
private fun FieldRow(f: Field, onChange: (String) -> Unit) {
    var editing by remember(f.key, f.value) { mutableStateOf(false) }
    var draft by remember(f.key, f.value) { mutableStateOf(f.value) }
    var menu by remember { mutableStateOf(false) }
    val label = stringResource(f.label)
    Row(Modifier.heightIn(min = 44.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.width(64.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = InkSoft)
        if (editing) {
            BasicTextField(
                draft, { draft = it }, singleLine = true,
                textStyle = TextStyle(color = Ink, fontSize = 14.5.sp, fontWeight = FontWeight.Bold),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { editing = false; onChange(draft) }),
                modifier = Modifier.weight(1f).semantics { contentDescription = label },
            )
        } else {
            Text(f.value.ifBlank { "—" }, Modifier.weight(1f), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Ink)
        }
        val change = stringResource(R.string.alfred_change, label.lowercase())
        Box {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFF4F4F1))
                    .clickable {
                        when {
                            editing -> { editing = false; onChange(draft) }
                            f.choices.isNotEmpty() -> menu = true
                            else -> editing = true
                        }
                    }
                    .semantics { contentDescription = change },
                contentAlignment = Alignment.Center,
            ) { Icon(EuriseIcons.pencil(2.2f), null, tint = Navy, modifier = Modifier.size(14.dp)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                f.choices.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { menu = false; onChange(c) }) }
            }
        }
    }
}

@Composable
private fun BigButton(label: String, primary: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier.height(48.dp).clip(shape)
            .then(if (primary) Modifier.background(if (enabled) Color.White else Color(0x66FFFFFF)) else Modifier.border(1.dp, Color(0x38FFFFFF), shape))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 14.5.sp, fontWeight = if (primary) FontWeight.ExtraBold else FontWeight.Bold, color = if (primary) Navy else Color.White)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsView(
    prefs: AlfredPrefs,
    source: Dictation.Source,
    readAloud: Boolean,
    natural: Boolean,
    onReadAloud: (Boolean) -> Unit,
    onAddress: () -> Unit,
    onTry: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var address by remember { mutableStateOf(prefs.address) }
    var name by remember { mutableStateOf(prefs.name) }
    var menu by remember { mutableStateOf(false) }
    Header(stringResource(R.string.alfred_settings_lead), Mood.Attentive, onSettings = onBack)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(horizontal = 14.dp)) {
        SettingRow(stringResource(R.string.alfred_s_quick), stringResource(R.string.alfred_s_quick_d)) { Toggle(true, enabled = false) {} }
        Divider()
        SettingRow(stringResource(R.string.alfred_s_address), stringResource(R.string.alfred_s_address_d)) {
            Box {
                val label = when (address) {
                    Address.Name -> name.ifBlank { stringResource(R.string.alfred_s_address_name) }
                    Address.Sir -> stringResource(R.string.alfred_s_address_sir)
                    Address.Madam -> stringResource(R.string.alfred_s_address_madam)
                    Address.None -> stringResource(R.string.alfred_s_address_none)
                }
                Box(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF4F4F1)).clickable { menu = true }.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Navy) }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    listOf(Address.Name to R.string.alfred_s_address_name, Address.Sir to R.string.alfred_s_address_sir,
                        Address.Madam to R.string.alfred_s_address_madam, Address.None to R.string.alfred_s_address_none).forEach { (a, id) ->
                        DropdownMenuItem(text = { Text(stringResource(id)) }, onClick = { menu = false; address = a; prefs.address = a; onAddress() })
                    }
                }
            }
        }
        if (address == Address.Name) {
            Box(Modifier.fillMaxWidth().padding(bottom = 10.dp).height(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF4F4F1)).padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart) {
                if (name.isEmpty()) Text(stringResource(R.string.alfred_s_name_hint), fontSize = 14.sp, color = InkSoft)
                BasicTextField(name, { name = it.take(30); prefs.name = name; onAddress() }, singleLine = true,
                    textStyle = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
            }
        }
        Divider()
        SettingRow(
            stringResource(R.string.alfred_s_dictation),
            stringResource(when (source) {
                Dictation.Source.EuriseVoice -> R.string.alfred_s_dictation_d_voice
                Dictation.Source.System -> R.string.alfred_s_dictation_d_system
                Dictation.Source.None -> R.string.alfred_s_dictation_d_none
            }),
        ) {}
        Divider()
        SettingRow(stringResource(R.string.alfred_s_voice), stringResource(R.string.alfred_s_voice_d)) { Toggle(readAloud, enabled = true, onReadAloud) }
        Divider()
        SettingRow(
            stringResource(R.string.alfred_s_voice_mine),
            stringResource(if (natural) R.string.alfred_s_voice_mine_on else R.string.alfred_s_voice_mine_off),
        ) {
            if (!natural) Box(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF4F4F1))
                .clickable { AlfredTts.openModels(context) }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.alfred_s_voice_get), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Navy)
            }
        }
        Divider()
        SettingRow(stringResource(R.string.alfred_s_ai), stringResource(R.string.alfred_s_ai_d)) {}
        Divider()
        SettingRow(stringResource(R.string.alfred_s_home), stringResource(R.string.alfred_s_home_d)) {
            Box(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF4F4F1))
                .clickable { runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } } }
                .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.alfred_s_home_open), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Navy)
            }
        }
    }
    Text(stringResource(R.string.alfred_s_footer), fontSize = 12.sp, lineHeight = 18.sp, color = Soft)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlineChip(stringResource(R.string.alfred_try), onTry)
        OutlineChip(stringResource(R.string.alfred_back), onBack)
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x121E1D1B)))

@Composable
private fun SettingRow(title: String, detail: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
            Text(detail, fontSize = 12.sp, lineHeight = 17.sp, color = InkSoft)
        }
        trailing()
    }
}

@Composable
private fun Toggle(on: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Switch(on, onChange, enabled = enabled, colors = SwitchDefaults.colors(
        checkedTrackColor = Navy, checkedThumbColor = Color.White, disabledCheckedTrackColor = Navy.copy(alpha = 0.6f),
        disabledCheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFD6D6D0), uncheckedThumbColor = Color.White,
    ))
}


package eu.eurise.feature.assistant

import eu.eurise.core.data.alfred.Address
import eu.eurise.core.data.alfred.addressSuffix
import eu.eurise.core.data.alfred.AlfredPrefs
import java.util.Locale
import java.time.format.DateTimeFormatter
import eu.eurise.core.data.alfred.AlfredMemory
import android.Manifest
import android.app.KeyguardManager
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
import eu.eurise.core.designsystem.component.PrivacyMeter
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
    /** La risposta dell'AI online tornata in Alfred, con i dati rimessi (tavola AlfRisposta); [local]: da Eurise AI sul
     *  telefono, che scrive mentre pensa (tavola AlfTelefono), con [question] per passarla online. */
    data class Answer(val text: String, val from: String, val local: Boolean = false, val question: String = "", val done: Boolean = true) : Phase
    /** «Quale Marco chiamo?»: più persone con lo stesso nome e nessuna chiaramente la più probabile (tavola AlfQuale). */
    data class Which(val command: Command, val options: List<Butler.Option>, val remember: Boolean = true) : Phase
    /** Più cose da fare in una frase (tavola AlfCapisce). */
    data class Many(val cards: List<Card>) : Phase
    /** Chi risponde e il cervello sul telefono (tavola AlfCervello). */
    data object Brain : Phase
    /** La risposta a una domanda sui dati, cercata sul telefono (tavola AlfDati). */
    data class Data(val answer: DataAnswer) : Phase
    /** «Cosa sai fare?» (tavola AlfAzioni). */
    data object Help : Phase
    /** «Chiamare Alfred» e il registro delle richieste (tavola AlfAvvio). */
    data object Call : Phase
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
    val brain = remember { AlfredBrain(context.applicationContext) }
    var brainReady by remember { mutableStateOf(false) }
    // Il cervello si collega all'apertura: il modello comincia a caricarsi mentre si parla.
    LaunchedEffect(Unit) {
        brainReady = brain.status()
        // Un cervello scaricato mentre Alfred era chiuso si passa a Eurise AI alla prima apertura.
        if (BrainDownload.handOverFinished(context, brain)) brainReady = brain.ready
    }
    DisposableEffect(Unit) { onDispose { brain.stop(); brain.close() } }
    var address by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf<Phase>(Phase.Ready) }
    /** La scheda com'era quando Alfred l'ha proposta: se al tocco è diversa, nel registro è «corretta». */
    var presented by remember { mutableStateOf<Card?>(null) }
    var heard by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    var readAloud by remember { mutableStateOf(prefs.readAloud) }
    val speaker = remember { lazy { Speaker(context) } }
    DisposableEffect(Unit) { onDispose { if (speaker.isInitialized()) speaker.value.release() } }
    val ear = remember { Ear(context) }
    DisposableEffect(Unit) { onDispose { ear.close() } }
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

    /** Alfred parla solo se gli si è parlato: aprirlo o scrivergli non lo fa parlare. */
    var voiceTurn by remember { mutableStateOf(false) }
    fun say(line: String, onDone: (() -> Unit)? = null) {
        if (readAloud && voiceTurn) speaker.value.say(line, onDone) else onDone?.invoke()
    }

    /** [freeQuestion] è definita più sotto (usa understandSaid): execute la chiama da qui. */
    lateinit var askFree: (String, Card?) -> Unit

    /**
     * Dalla schermata di blocco (tavola AlfAvvio): torcia, volume, musica, timer e segnalazioni subito; il resto (dati,
     * messaggi, chiamate) chiede prima lo sblocco e riprende da solo.
     */
    fun unlocked(card: Card?, then: () -> Unit) {
        val km = context.getSystemService(KeyguardManager::class.java)
        val activity = context.findActivity()
        if (km?.isKeyguardLocked != true || activity == null || card?.immediate == true || card?.kind == Kind.Report) { then(); return }
        phase = Phase.Said(context.getString(R.string.alfred_unlock_first))
        km.requestDismissKeyguard(activity, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = then()
        })
    }

    fun execute(card: Card) {
        answering = false
        ear.cancel()
        if (speaker.isInitialized()) speaker.value.stop()
        val km = context.getSystemService(KeyguardManager::class.java)
        if (km?.isKeyguardLocked == true && !card.immediate && card.kind != Kind.Report) { unlocked(card) { execute(card) }; return }
        // Richiesta libera: sul telefono non c'è ancora chi risponde; va al servizio online preferito, ripulita.
        if (card.kind == Kind.Free) { askFree(card.value("text").ifBlank { heard }, null); return }
        scope.launch {
            val out = butler.run(card)
            val corrected = presented?.let { it.kind == card.kind && it.fields.map { f -> f.value } != card.fields.map { f -> f.value } } == true
            AlfredLog.add(context, heard, when {
                !out.close -> AlfredLog.Result.Failed
                corrected -> AlfredLog.Result.Corrected
                else -> AlfredLog.Result.Done
            }, logDetail(context, card, out))
            phase = Phase.Said(out.line)
            // Si chiude quando ha finito di dire la battuta (non la si tronca) o dopo il tempo di leggerla, al massimo 5 s;
            // l'azione è già fatta, quindi non si aspetta nulla per lavorare.
            val spoken = kotlinx.coroutines.CompletableDeferred<Unit>()
            say(out.spoken) { spoken.complete(Unit) }
            if (out.close) {
                delay(700L + (out.line.length * 25L).coerceAtMost(1800L))
                kotlinx.coroutines.withTimeoutOrNull(2500L) { spoken.await() }
                onClose()
            }
        }
    }

    lateinit var understandSaid: (String, Boolean) -> Unit

    /** Cerca sul telefono e mostra la risposta; a voce la legge, se gli si è parlato. */
    suspend fun showData(ask: Command.Ask) {
        val answer = butler.lookup(ask)
        phase = Phase.Data(answer)
        // I messaggi si leggono per intero (fino a una ventina di righe): è ciò che si è chiesto.
        say(if (ask.kind == AskKind.Messages) SpokenText.brief(answer.spoken, sentences = 12, maxChars = 700) else SpokenText.brief(answer.spoken))
    }

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
                    Grammar.Answer.No -> { AlfredLog.add(context, heard, AlfredLog.Result.Cancelled, logDetail(context, card, null)); heard = ""; phase = Phase.Ready; say(context.getString(R.string.alfred_cancelled)) }
                    Grammar.Answer.Other -> understandSaid(said, true)
                }
            },
            onFail = { answering = false },
        )
    }

    /** Una domanda libera: Eurise AI sul telefono, poi (o subito, se scelto) il servizio online. */
    fun freeQuestion(question: String, card: Card?) {
        val answers = prefs.answers
        when {
            brainReady && answers != "online" -> {
                val from = context.getString(R.string.alfred_from_phone)
                var text = ""
                phase = Phase.Answer("", from, local = true, question = question, done = false)
                brain.ask(question, onPiece = { piece ->
                    text += piece
                    if ((phase as? Phase.Answer)?.local == true) phase = Phase.Answer(text.trimStart(), from, true, question, done = false)
                }, onDone = { ok ->
                    if ((phase as? Phase.Answer)?.local != true) return@ask
                    if (!ok && text.isBlank()) {
                        if (answers == "phone_only") phase = Phase.Said(context.getString(R.string.alfred_brain_failed))
                        else { phase = Phase.Ready; askOnline(favorite) }
                    } else {
                        phase = Phase.Answer(text.trim(), from, true, question, done = true)
                        // A voce le prime due frasi: il resto è scritto, e si legge prima di quanto si ascolta.
                        say(SpokenText.brief(text.trim()))
                    }
                })
            }
            answers == "phone_only" -> phase = Phase.Said(context.getString(R.string.alfred_brain_missing))
            answers == "online" || prefs.freeOnline -> { phase = Phase.Ready; askOnline(favorite) }
            card != null -> phase = Phase.Confirm(card)
            else -> { phase = Phase.Ready; askOnline(favorite) }
        }
    }

    askFree = { q, c -> freeQuestion(q, c) }

    /** Ascolta una risposta detta e la capisce come una frase nuova (dopo «A che ora lo sposto?»). */
    fun listenReply() {
        answering = true
        ear.listen(onLevel = { level = it }, onSpeech = {}, onWorking = {},
            onText = { said -> answering = false; understandSaid(said, true) }, onFail = { answering = false })
    }

    lateinit var chooseWhich: (Phase.Which, Int) -> Unit

    /** Dopo «Quale Marco?» si ascolta la scelta: «il primo», «Ferri», «quello del lavoro». */
    fun askWhich(p: Phase.Which) {
        answering = true
        ear.listen(onLevel = { level = it }, onSpeech = {}, onWorking = {},
            onText = { said ->
                answering = false
                val now = phase as? Phase.Which
                if (now == null || now.command != p.command) return@listen
                val i = Grammar.pick(said, now.options.map { it.name to it.detail })
                when {
                    i != null -> chooseWhich(now, i)
                    Grammar.answer(said) == Grammar.Answer.No -> { heard = ""; phase = Phase.Ready; say(context.getString(R.string.alfred_cancelled)) }
                    else -> understandSaid(said, true)
                }
            },
            onFail = { answering = false })
    }

    /** Prepara la scheda di un comando (o chiede quale persona, se c'è un dubbio vero) e, a voce, chiede conferma. */
    suspend fun present(command: Command, spoken: Boolean) {
        butler.doubt(command)?.let { options ->
            val p = Phase.Which(command, options)
            phase = p
            val first = options.first().name.split(' ').first()
            val line = context.getString(if (command is Command.Call) R.string.alfred_which_call else R.string.alfred_which_message, first)
            if (spoken) say(line) { askWhich(p) } else say(line)
            return
        }
        val card = butler.plan(command)
        // Segnala in guida: niente scheda né «Procedo?», chi guida non deve toccare nulla (tavola MapSegnalaGuida).
        // Lo stesso per torcia, volume, musica e timer (tavola AlfAzioni): si fanno subito e si disfano con una frase.
        if (card.kind == Kind.Report || card.immediate) { execute(card); return }
        if (card.kind == Kind.Help) { phase = Phase.Help; say(card.lead); return }
        // Domande libere subito online (disposizione accesa di base): niente scheda, si apre il servizio preferito.
        // Nessun messaggio intermedio: la domanda va subito al servizio (la prima volta, la spiegazione del centro
        // privacy chiede il permesso).
        if (card.kind == Kind.Free) {
            freeQuestion((command as? Command.Free)?.text?.ifBlank { heard } ?: heard, card)
            return
        }
        phase = Phase.Confirm(card)
        presented = card
        val canAct = card.ready && (card.kind != Kind.Call ||
            context.checkSelfPermission(Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED)
        when {
            spoken && readAloud && canAct -> say(butler.spokenConfirm(card)) { askAnswer(card) }
            // «A che ora lo sposto?»: la risposta detta completa la scheda.
            spoken && card.kind == Kind.Follow && !card.ready && card.fields.isNotEmpty() -> say(SpokenText.brief(card.lead, 1)) { listenReply() }
            else -> say(SpokenText.brief(card.lead, 1))
        }
    }

    chooseWhich = { p, i ->
        val o = p.options[i]
        // Con la spunta, [said] fa imparare la scelta quando la chiamata o il messaggio partono; senza, "" non insegna nulla.
        val said = if (p.remember) null else ""
        val next = when (val c = p.command) {
            is Command.Call -> c.copy(who = o.name, said = said ?: c.who)
            is Command.Message -> c.copy(who = o.name, said = said ?: c.who)
            else -> c
        }
        answering = false
        ear.cancel()
        phase = Phase.Thinking
        scope.launch { present(next, voiceTurn) }
    }

    understandSaid = { text, spoken ->
        voiceTurn = spoken
        if (text.isNotBlank()) {
            // Le parole corrette a mano in passato si correggono da sé (tavola AlfSeguito).
            heard = Corrections.apply(text.trim(), butler.memory.words())
            typed = ""
            phase = Phase.Thinking
            scope.launch {
                val now = LocalDateTime.now()
                // Prima le regole esatte (subito); se nessuna prende la frase, la legge il cervello sul telefono (fino a
                // tre azioni); senza cervello, la seconda lettura delle parole chiave.
                // Entro due minuti dall'ultima azione: «spostalo alle 10», «cancellalo», «mandalo anche a Luca».
                val exact = (if (FollowUp.current() != null) Grammar.follow(heard, now) else null) ?: Grammar.exact(heard, now)
                val fromBrain = if (exact == null && brainReady) brain.command(heard, butler.memory.hints(), now.toLocalDate())?.let { BrainCommands.parse(it, now, heard) }.orEmpty() else emptyList()
                val actions = fromBrain.filter { it !is Command.Free && it !is Command.Ask }
                // Domande sui dati (tavola AlfDati): si risponde subito, cercando solo sul telefono.
                val asked = exact as? Command.Ask ?: fromBrain.firstOrNull { it is Command.Ask }?.takeIf { actions.isEmpty() } as? Command.Ask
                if (asked != null) {
                    unlocked(null) { scope.launch { showData(asked) } }
                    return@launch
                }
                if (actions.size >= 2) {
                    val cards = actions.map { butler.plan(it) }
                    phase = Phase.Many(cards)
                    say(context.resources.getQuantityString(R.plurals.alfred_many_lead, cards.size, cards.size))
                    return@launch
                }
                val command = exact ?: actions.firstOrNull() ?: fromBrain.firstOrNull() ?: Grammar.parse(heard, now)
                present(command, spoken)
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
        // La voce si prepara mentre si parla (modello caricato e scaldato in Eurise Voce), così risponde senza attesa.
        if (readAloud) speaker.value.stop()
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
    val dataPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        (phase as? Phase.Data)?.let { d -> scope.launch { phase = Phase.Data(butler.lookup(d.answer.ask)) } }
    }

    // La prima volta Alfred chiede come chiamare chi usa il telefono (il nome si cambia poi dalle disposizioni).
    var askingName by remember { mutableStateOf(prefs.address == Address.Name && prefs.name.isBlank()) }
    var suggestion by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        address = addressSuffix(prefs)
        if (askingName) suggestion = ownFirstName(context)
        if (!initial.isNullOrBlank() && !askingName) understand(initial)
        else if (listenNow && !askingName) voice()
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
                        onHelp = { phase = Phase.Help },
                    )
                    if (heard.isNotEmpty() && p == Phase.Thinking) Quote(heard)
                    Text(stringResource(R.string.alfred_hint), fontSize = 12.sp, lineHeight = 18.sp, color = Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(R.string.alfred_ex_help, R.string.alfred_ex_data, R.string.alfred_ex_call, R.string.alfred_ex_note, R.string.alfred_ex_route, R.string.alfred_ex_message,
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
                            else -> {
                                // Una parola capita male e corretta a mano: la prossima volta la corregge da sé.
                                if (key == "what" || key == "text") Corrections.diff(card.value(key), value).forEach { (a, b) -> butler.memory.learnWord(a, b) }
                                val edited = card.with(key, value)
                                Phase.Confirm(if (card.kind == Kind.Follow && key == "when" && value.isNotBlank()) edited.copy(ready = true) else edited)
                            }
                        }
                    })
                    if (!Contacts.canRead(context) && card.kind in setOf(Kind.Call, Kind.Message, Kind.Event)) {
                        OutlineChip(stringResource(R.string.alfred_need_contacts)) { contactsPermission.launch(Manifest.permission.READ_CONTACTS) }
                    }
                    if (card.chips.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            card.chips.forEach { chip -> OutlineChip(chip.label) { replan(chip.command) } }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigButton(stringResource(R.string.alfred_cancel), primary = false, enabled = true, modifier = Modifier.weight(1f)) {
                            answering = false
                            ear.cancel()
                            AlfredLog.add(context, heard, AlfredLog.Result.Cancelled, logDetail(context, card, null))
                            heard = ""
                            phase = Phase.Ready
                        }
                        val action = if (card.kind == Kind.Free) stringResource(R.string.alfred_ask_online, favorite.name) else card.action
                        BigButton(action, primary = true, enabled = card.ready, modifier = Modifier.weight(1.4f)) {
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
                    Header(
                        if (p.local) stringResource(if (p.done) R.string.alfred_answer_phone else R.string.alfred_answer_phone_writing)
                        else stringResource(R.string.alfred_answer_line, p.from),
                        if (p.done) Mood.Bow else Mood.Thinking, onSettings = { phase = Phase.Settings })
                    AnswerView(
                        p.text.ifBlank { "…" },
                        onNote = { replan(Command.Note(p.text)) },
                        onTask = { replan(Command.Task(p.text.lineSequence().first { it.isNotBlank() }.take(90), null, null)) },
                        onSend = { replan(Command.Message("", p.text, null)) },
                    )
                    if (p.local) {
                        OutlineChip(stringResource(R.string.alfred_ask_online, favorite.name)) {
                            brain.stop()
                            heard = p.question
                            typed = ""
                            phase = Phase.Ready
                            askOnline(favorite)
                        }
                    }
                }
                is Phase.Many -> {
                    Header(context.resources.getQuantityString(R.plurals.alfred_many_lead, p.cards.size, p.cards.size), Mood.Attentive, onSettings = { phase = Phase.Settings })
                    if (heard.isNotEmpty()) Quote(heard)
                    ManyView(p.cards, onOpen = { phase = Phase.Confirm(it) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BigButton(stringResource(R.string.alfred_cancel), primary = false, enabled = true, modifier = Modifier.weight(1f)) { phase = Phase.Ready }
                        BigButton(stringResource(R.string.alfred_many_all), primary = true, enabled = p.cards.any { it.ready }, modifier = Modifier.weight(1.4f)) {
                            scope.launch {
                                val lines = p.cards.filter { it.ready }.map { butler.run(it).line }
                                val line = lines.joinToString(" ")
                                phase = Phase.Said(line)
                                say(line)
                            }
                        }
                    }
                }
                is Phase.Which -> {
                    val first = p.options.first().name.split(' ').first()
                    Header(stringResource(if (p.command is Command.Call) R.string.alfred_which_call else R.string.alfred_which_message, first),
                        Mood.Attentive, onSettings = { phase = Phase.Settings })
                    if (heard.isNotEmpty()) Quote(heard)
                    p.options.forEachIndexed { i, o ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).clickable { chooseWhich(p, i) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0xFFEEF0F5)), contentAlignment = Alignment.Center) {
                                Text("${i + 1}", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Navy)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(o.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
                                if (o.detail.isNotBlank()) Text(o.detail, fontSize = 12.5.sp, color = InkSoft)
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { phase = p.copy(remember = !p.remember) }.padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            Modifier.size(20.dp).clip(RoundedCornerShape(6.dp))
                                .then(if (p.remember) Modifier.background(Gold) else Modifier.border(1.5.dp, Color(0x66FFFFFF), RoundedCornerShape(6.dp))),
                            contentAlignment = Alignment.Center,
                        ) { if (p.remember) Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Black, color = Navy) }
                        Text(stringResource(R.string.alfred_which_remember, first), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Soft)
                    }
                    if (answering) Text(stringResource(R.string.alfred_which_listening), fontSize = 12.sp, color = Gold)
                    Text(stringResource(R.string.alfred_which_hint), fontSize = 12.sp, lineHeight = 17.sp, color = Muted)
                    BigButton(stringResource(R.string.alfred_cancel), primary = false, enabled = true, modifier = Modifier.fillMaxWidth()) {
                        answering = false; ear.cancel(); heard = ""; phase = Phase.Ready
                    }
                }
                Phase.Help -> {
                    Header(stringResource(R.string.alfred_help_lead), Mood.Bow, onSettings = { phase = Phase.Settings })
                    HelpList()
                    Text(stringResource(R.string.alfred_help_foot), fontSize = 12.sp, lineHeight = 18.sp, color = Soft)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlineChip(stringResource(R.string.alfred_back)) { phase = Phase.Ready }
                    }
                }
                is Phase.Data -> {
                    val a = p.answer
                    Header(a.lead, Mood.Bow, onSettings = { phase = Phase.Settings })
                    if (heard.isNotEmpty()) Quote(heard)
                    if (a.rows.isNotEmpty()) DataList(a.rows) { section -> openSection(context, section) }
                    a.needs?.let { perm ->
                        OutlineChip(stringResource(if (perm == Manifest.permission.READ_CALL_LOG) R.string.alfred_data_need_calls else R.string.alfred_data_need_calendar)) {
                            dataPermission.launch(
                                if (perm == Manifest.permission.READ_CALL_LOG) arrayOf(perm)
                                else arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                            )
                        }
                    }
                    val chips = a.chips + if (a.empty && a.needs == null && a.ask.kind == AskKind.When) {
                        listOf(Chip(context.getString(R.string.alfred_ask_online, favorite.name), Command.Free(heard)))
                    } else emptyList()
                    if (chips.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            chips.forEach { chip ->
                                OutlineChip(chip.label) {
                                    if (chip.command is Command.Free) { phase = Phase.Ready; askOnline(favorite) } else replan(chip.command)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val openLabel = when {
                            a.open.startsWith("note:") -> R.string.alfred_data_open_note
                            a.open == "notes" -> R.string.alfred_data_open_notes
                            a.open == "calls" -> R.string.alfred_data_open_calls
                            else -> R.string.alfred_data_open_calendar
                        }
                        BigButton(stringResource(openLabel), primary = false, enabled = true, modifier = Modifier.weight(1f)) { openSection(context, a.open) }
                        BigButton(stringResource(R.string.alfred_data_again), primary = false, enabled = true, modifier = Modifier.weight(1f)) {
                            speaker.value.say(a.spoken, null)
                        }
                    }
                    Text(stringResource(R.string.alfred_data_where, a.sources), fontSize = 11.5.sp, lineHeight = 16.sp, color = Muted)
                }
                Phase.Brain -> BrainView(prefs, brain, butler.memory, favorite.name, onChanged = { brainReady = brain.ready }, onBack = { phase = Phase.Settings })
                Phase.Settings -> SettingsView(prefs, dictation.source, readAloud, natural = speaker.isInitialized() && speaker.value.natural, favoriteName = favorite.name,
                    onReadAloud = { readAloud = it; prefs.readAloud = it },
                    onAddress = { address = addressSuffix(prefs) },
                    onTry = { understand(context.getString(R.string.alfred_try_text)) },
                    onBrain = { phase = Phase.Brain },
                    onCall = { phase = Phase.Call },
                    onBack = { phase = Phase.Ready })
                Phase.Call -> CallView(prefs, onBack = { phase = Phase.Settings })
            }
        }
        consentFor?.let { s ->
            ServiceConsentSheet(PrivacyService.AiOnline, onDismiss = { consentFor = null }, onAccept = { consentFor = null; sendOnline(s) })
        }
    }
}

@Composable
private fun Header(line: String, mood: Mood, onSettings: () -> Unit, onHelp: (() -> Unit)? = null) {
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
        // «Cosa sai fare?» dal punto interrogativo (tavola AlfAzioni).
        onHelp?.let { help ->
            Box(
                Modifier.size(36.dp).clip(CircleShape).clickable(onClick = help)
                    .semantics { contentDescription = "Cosa sa fare Alfred" },
                contentAlignment = Alignment.Center,
            ) { Text("?", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Soft) }
        }
        // Le impostazioni anche da un'icona visibile (prova del 13 novembre: col solo tondo del papillon non si trovavano).
        Box(
            Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onSettings)
                .semantics { contentDescription = "Impostazioni di Alfred" },
            contentAlignment = Alignment.Center,
        ) { Icon(EuriseIcons.gear(2.2f), null, tint = Soft, modifier = Modifier.size(18.dp)) }
    }
}

/** L'elenco della tavola AlfAzioni: gruppo in grassetto, esempi sotto, su fondo bianco. */
@Composable
private fun HelpList() {
    val groups = listOf(
        R.string.alfred_help_g_events to R.string.alfred_help_x_events,
        R.string.alfred_help_g_tasks to R.string.alfred_help_x_tasks,
        R.string.alfred_help_g_messages to R.string.alfred_help_x_messages,
        R.string.alfred_help_g_clock to R.string.alfred_help_x_clock,
        R.string.alfred_help_g_phone to R.string.alfred_help_x_phone,
        R.string.alfred_help_g_music to R.string.alfred_help_x_music,
        R.string.alfred_help_g_drive to R.string.alfred_help_x_drive,
        R.string.alfred_help_g_people to R.string.alfred_help_x_people,
        R.string.alfred_help_g_more to R.string.alfred_help_x_more,
    )
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 14.dp)) {
        groups.forEachIndexed { i, (title, examples) ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x121E1D1B)))
            Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(title), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Navy)
                Text(stringResource(examples), fontSize = 12.5.sp, lineHeight = 18.sp, color = Color(0xFF56564F))
            }
        }
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
    Kind.Remember -> EuriseIcons.note(2.3f)
    Kind.Ask -> EuriseIcons.search(2.3f)
    Kind.Follow -> EuriseIcons.calendar(2.3f)
    Kind.Complete -> EuriseIcons.task(2.3f)
    Kind.Clock -> EuriseIcons.clock(2.3f)
    Kind.Guide -> EuriseIcons.map(2.3f)
    Kind.Device -> EuriseIcons.phone(2.3f)
    Kind.Help -> EuriseIcons.spark(2.3f)
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
        } else if (f.previous != null) {
            // Il valore di prima barrato e quello nuovo in grassetto (tavola AlfSeguito).
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(f.previous, fontSize = 13.sp, color = Color(0xFF8A8A82), textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                Text(f.value.ifBlank { "—" }, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Navy)
            }
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
    favoriteName: String,
    onReadAloud: (Boolean) -> Unit,
    onAddress: () -> Unit,
    onTry: () -> Unit,
    onBrain: () -> Unit,
    onCall: () -> Unit,
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
        SettingRow(stringResource(R.string.alfred_s_brain), stringResource(when (prefs.answers) {
            "online" -> R.string.alfred_answers_online_short
            "phone_only" -> R.string.alfred_answers_phone_only_short
            else -> R.string.alfred_answers_phone_short
        }, favoriteName)) {
            Box(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF4F4F1))
                .clickable(onClick = onBrain).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.alfred_s_brain_open), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Navy)
            }
        }
        Divider()
        SettingRow(stringResource(R.string.alfred_call_title), stringResource(R.string.alfred_call_row_d)) {
            Box(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF4F4F1))
                .clickable(onClick = onCall).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.alfred_s_brain_open), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Navy)
            }
        }
    }
    Text(stringResource(R.string.alfred_s_footer), fontSize = 12.sp, lineHeight = 18.sp, color = Soft)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlineChip(stringResource(R.string.alfred_try), onTry)
        OutlineChip(stringResource(R.string.alfred_back), onBack)
    }
}

/** «Chiamare Alfred» (tavola AlfAvvio): tasto dell'assistente, riquadro nella tendina, schermata di blocco, «Di' Alfred», registro. */
@Composable
private fun CallView(prefs: AlfredPrefs, onBack: () -> Unit) {
    val context = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val assistant = remember(tick) { isAssistant(context) }
    var tile by remember { mutableStateOf(AlfredTile.enabled(context)) }
    var lock by remember { mutableStateOf(prefs.lockScreen) }
    var logOn by remember { mutableStateOf(AlfredLog.enabled(context)) }
    var entries by remember { mutableStateOf(AlfredLog.all(context)) }
    LaunchedEffect(Unit) { tick++ }
    Header(stringResource(R.string.alfred_call_title), Mood.Attentive, onSettings = onBack)
    Text(stringResource(R.string.alfred_call_gesture), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Soft)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(horizontal = 14.dp)) {
        SettingRow(stringResource(R.string.alfred_call_assist), stringResource(if (assistant) R.string.alfred_call_assist_on else R.string.alfred_call_assist_d)) {
            if (!assistant) Box(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF4F4F1))
                .clickable {
                    runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                }
                .padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.alfred_s_home_open), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Navy)
            }
        }
        Divider()
        SettingRow(stringResource(R.string.alfred_call_tile), stringResource(R.string.alfred_call_tile_d)) {
            Toggle(tile, enabled = true) { tile = it; AlfredTile.setEnabled(context, it); if (it) AlfredTile.offer(context) }
        }
        Divider()
        SettingRow(stringResource(R.string.alfred_call_lock), stringResource(R.string.alfred_call_lock_d)) {
            Toggle(lock, enabled = true) { lock = it; prefs.lockScreen = it }
        }
    }
    Text(stringResource(R.string.alfred_call_voice), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Soft)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.alfred_call_word), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(stringResource(R.string.alfred_call_word_d), fontSize = 12.sp, lineHeight = 17.sp, color = InkSoft)
                Text(stringResource(R.string.alfred_call_word_battery), fontSize = 11.5.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9A5B1E))
            }
            Toggle(false, enabled = false) {}
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.alfred_log_title), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Soft)
        Text(stringResource(R.string.alfred_log_off_default), fontSize = 12.sp, color = Muted)
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(horizontal = 14.dp)) {
        SettingRow(stringResource(R.string.alfred_log_keep), stringResource(R.string.alfred_log_keep_d)) {
            Toggle(logOn, enabled = true) { logOn = it; AlfredLog.setEnabled(context, it); entries = AlfredLog.all(context) }
        }
        entries.take(12).forEach { e ->
            Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val (mark, color) = when (e.result) {
                    AlfredLog.Result.Done -> R.string.alfred_log_done to Color(0xFF2F6B45)
                    AlfredLog.Result.Corrected -> R.string.alfred_log_corrected to Color(0xFF9A5B1E)
                    AlfredLog.Result.Cancelled -> R.string.alfred_log_cancelled to Color(0xFF8A3A5C)
                    AlfredLog.Result.Failed -> R.string.alfred_log_failed to Color(0xFFC2453F)
                }
                Text(stringResource(mark), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = color, modifier = Modifier.width(72.dp))
                Column(Modifier.weight(1f)) {
                    Text("«${e.said}»", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    if (e.detail.isNotBlank()) Text(e.detail, fontSize = 12.sp, color = InkSoft)
                }
            }
        }
        if (logOn && entries.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEEF0F5)).clickable {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.alfred_log_subject))
                        .putExtra(Intent.EXTRA_TEXT, AlfredLog.text(entries))
                    runCatching { context.startActivity(Intent.createChooser(send, context.getString(R.string.alfred_log_send)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }, contentAlignment = Alignment.Center) { Text(stringResource(R.string.alfred_log_send), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Navy) }
                Box(Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF4F4F1)).clickable {
                    AlfredLog.clear(context); entries = emptyList()
                }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.alfred_log_clear), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8A3A5C))
                }
            }
        }
    }
    Text(stringResource(R.string.alfred_log_foot), fontSize = 12.sp, lineHeight = 18.sp, color = Soft)
    OutlineChip(stringResource(R.string.alfred_back), onBack)
}

/** Eurise è l'assistente di Android (tasto Home o accensione tenuto premuto). */
private fun isAssistant(context: android.content.Context): Boolean = runCatching {
    if (android.os.Build.VERSION.SDK_INT < 29) return false
    context.getSystemService(android.app.role.RoleManager::class.java)?.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT) == true
}.getOrDefault(false)

private tailrec fun android.content.Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Cosa scrive il registro accanto alla frase: il tipo di scheda e le righe principali, o com'è andata. */
private fun logDetail(context: android.content.Context, card: Card, out: Outcome?): String {
    val fields = card.fields.map { it.value }.filter { it.isNotBlank() }.take(3).joinToString(" · ")
    val title = runCatching { context.getString(card.title) }.getOrDefault("")
    return listOf(title, fields.ifBlank { out?.line.orEmpty() }).filter { it.isNotBlank() }.joinToString(" · ")
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


/** Le righe della risposta sui dati (tavola AlfDati): un tocco apre la sezione della suite. */
@Composable
private fun DataList(rows: List<DataRow>, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.alfred_data_from_phone), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2F6B45),
                modifier = Modifier.padding(end = 6.dp))
            PrivacyMeter(10)
        }
        rows.forEachIndexed { i, r ->
            if (i > 0) Divider()
            Row(
                Modifier.fillMaxWidth().clickable(enabled = r.open != null) { r.open?.let(onOpen) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(r.title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Ink)
                    if (r.detail.isNotBlank()) Text(r.detail, fontSize = 12.5.sp, color = InkSoft)
                }
                Box(Modifier.size(8.dp).clip(CircleShape).background(Color(r.tint)))
            }
        }
    }
}

/** Apre una sezione di Eurise («calendar», «note:12»…) o, per «calls», il registro delle chiamate. */
private fun openSection(context: android.content.Context, section: String) {
    val intent = if (section == "calls") Intent(Intent.ACTION_VIEW).setType(android.provider.CallLog.Calls.CONTENT_TYPE)
    else if (section == "messages") context.packageManager.getLaunchIntentForPackage(
        eu.eurise.core.suite.PeopleContract.packageOf(context, eu.eurise.core.suite.PeopleContract.App.Messages)) ?: return
    else Intent().setClassName(context.packageName, "eu.eurise.hub.MainActivity").putExtra("eu.eurise.extra.SECTION", section)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    eu.eurise.core.contacts.Reach.open(context, intent)
}

/** Le azioni capite in una frase, una per riga: un tocco apre la scheda per correggerla (tavola AlfCapisce). */
@Composable
private fun ManyView(cards: List<Card>, onOpen: (Card) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 14.dp)) {
        cards.forEachIndexed { i, card ->
            if (i > 0) Divider()
            Column(Modifier.fillMaxWidth().clickable { onOpen(card) }.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(card.title), fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Ink)
                val detail = card.fields.map { it.value }.filter { it.isNotBlank() }.joinToString(" · ")
                Text(if (card.ready) detail else stringResource(R.string.alfred_many_todo, detail), fontSize = 12.5.sp, color = InkSoft)
            }
        }
    }
}

/** Chi risponde alle domande libere e quale cervello usa Alfred sul telefono (tavola AlfCervello). */
@Composable
private fun BrainView(prefs: AlfredPrefs, brain: AlfredBrain, memory: AlfredMemory, favoriteName: String, onChanged: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var answers by remember { mutableStateOf(prefs.answers) }
    var models by remember { mutableStateOf(brain.models) }
    var current by remember { mutableStateOf(brain.model) }
    var progress by remember { mutableStateOf(emptyMap<BrainModel, Int>()) }
    var note by remember { mutableStateOf<String?>(null) }
    fun refresh() { models = brain.models; current = brain.model; onChanged() }
    LaunchedEffect(Unit) {
        brain.status(); refresh()
        while (true) {
            if (BrainDownload.handOverFinished(context, brain) { note = context.getString(R.string.alfred_brain_installing) }) {
                note = null; refresh()
            }
            progress = BrainModel.entries.mapNotNull { m -> BrainDownload.progress(context, m)?.let { m to it.first } }.toMap()
            delay(1_000)
        }
    }
    Header(stringResource(R.string.alfred_brain_lead), Mood.Attentive, onSettings = onBack)
    Text(stringResource(R.string.alfred_answers_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 14.dp)) {
        listOf(
            Triple("phone", R.string.alfred_answers_phone, R.string.alfred_answers_phone_d),
            Triple("online", R.string.alfred_answers_online, R.string.alfred_answers_online_d),
            Triple("phone_only", R.string.alfred_answers_phone_only, R.string.alfred_answers_phone_only_d),
        ).forEachIndexed { i, (key, title, detail) ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().clickable { answers = key; prefs.answers = key }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(22.dp).clip(CircleShape).border(if (answers == key) 7.dp else 2.dp, if (answers == key) Navy else InkSoft, CircleShape))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(title), fontSize = 15.sp, fontWeight = if (answers == key) FontWeight.Bold else FontWeight.SemiBold, color = Ink)
                    Text(stringResource(detail, favoriteName), fontSize = 12.5.sp, color = InkSoft)
                }
            }
        }
    }
    Text(stringResource(R.string.alfred_brain_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 14.dp)) {
        if (!brain.installed) {
            Text(stringResource(R.string.alfred_brain_no_ai), fontSize = 13.sp, color = InkSoft, modifier = Modifier.padding(vertical = 12.dp))
        } else BrainModel.entries.forEachIndexed { i, m ->
            if (i > 0) Divider()
            val installed = m.id in models
            val inUse = current == m.id
            val pct = progress[m]
            val detail = when {
                inUse -> stringResource(R.string.alfred_brain_in_use, stringResource(m.detail))
                pct != null -> stringResource(R.string.alfred_brain_downloading, pct)
                else -> stringResource(m.detail)
            }
            SettingRow(stringResource(m.label), detail) {
                val (label, action) = when {
                    pct != null -> R.string.alfred_brain_cancel to { BrainDownload.cancel(context, m) }
                    installed && !inUse -> R.string.alfred_brain_use to { scope.launch { brain.use(m.id); refresh() }; Unit }
                    !installed -> R.string.alfred_brain_get to {
                        scope.launch {
                            val url = BrainDownload.url(m)
                            if (url == null) note = context.getString(R.string.alfred_brain_url_failed)
                            else BrainDownload.start(context, m, url, context.getString(R.string.alfred_brain_notification, context.getString(m.label)))
                        }
                        Unit
                    }
                    else -> null to {}
                }
                if (label != null) Box(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF4F4F1))
                    .clickable { action() }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(label), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Navy)
                }
            }
        }
    }
    Text(note ?: stringResource(R.string.alfred_brain_where), fontSize = 12.sp, color = Color(0xFFCBD5E1))
    MemorySection(memory)
}

/** «Alfred ricorda»: interruttore e voci, ognuna si cancella (tavola AlfCervello). */
@Composable
private fun MemorySection(memory: AlfredMemory) {
    var enabled by remember { mutableStateOf(memory.enabled) }
    var facts by remember { mutableStateOf(memory.all().sortedByDescending { it.since }) }
    val day = remember { DateTimeFormatter.ofPattern("d MMMM", Locale.ITALIAN) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.alfred_memory_title), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCBD5E1), modifier = Modifier.weight(1f))
        Toggle(enabled, enabled = true) { enabled = it; memory.enabled = it }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(start = 14.dp, end = 4.dp)) {
        val shown = facts.filter { it.kind != AlfredMemory.Kind.Channel || it.count >= 3 }
        if (shown.isEmpty()) Text(stringResource(R.string.alfred_memory_empty), fontSize = 12.5.sp, color = InkSoft, modifier = Modifier.padding(vertical = 12.dp, horizontal = 0.dp))
        shown.forEachIndexed { i, f ->
            if (i > 0) Divider()
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(when (f.kind) {
                        AlfredMemory.Kind.Person -> stringResource(R.string.alfred_memory_person, f.key.replaceFirstChar { it.uppercase() }, f.value)
                        AlfredMemory.Kind.Channel -> stringResource(R.string.alfred_memory_channel, f.key.split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } },
                            Channel.entries.firstOrNull { it.name == f.value }?.let { c -> when (c) { Channel.Sms -> "SMS"; else -> c.name } } ?: f.value)
                        AlfredMemory.Kind.Said -> f.value
                        AlfredMemory.Kind.Word -> stringResource(R.string.alfred_memory_word, f.key, f.value)
                    }, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                    Text(stringResource(R.string.alfred_memory_since, f.since.format(day)), fontSize = 12.sp, color = InkSoft)
                }
                Box(Modifier.size(44.dp).clickable { memory.forget(f.id); facts = memory.all().sortedByDescending { it.since } }, contentAlignment = Alignment.Center) {
                    Text("×", fontSize = 20.sp, color = InkSoft)
                }
            }
        }
    }
    Text(stringResource(R.string.alfred_memory_where), fontSize = 12.sp, color = Color(0xFFCBD5E1))
}

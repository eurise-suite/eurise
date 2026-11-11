package eu.eurise.keyboard

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.view.isVisible
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.createToolbarKey
import helium314.keyboard.latin.utils.getEnabledToolbarKeys
import helium314.keyboard.latin.utils.onClickToolbarKey
import helium314.keyboard.latin.utils.prefs
import java.time.format.DateTimeFormatter

/**
 * La barra di Eurise sopra i tasti: i quattro comandi (Disponibilità, File, Persona, Codici), le proposte del momento
 * (calcolo, contatto citato con @, abbreviazione, frase da trasformare in attività, cosa appena copiata) e, a richiesta,
 * la fila degli strumenti separata dai suggerimenti. I pannelli si aprono al posto dei tasti.
 */
class EuriseBar(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    internal lateinit var ime: LatinIME
    private lateinit var keyboardArea: View
    internal val bridge = Bridge(context)
    private val handler = Handler(Looper.getMainLooper())
    internal var p = Palette()
        private set

    private val toolsRow = LinearLayout(context)
    private val commandsRow = LinearLayout(context)
    private val chipsScroll = HorizontalScrollView(context)
    private val chips = LinearLayout(context)
    private val buttons = LinearLayout(context)
    private val panelHost = FrameLayout(context)
    private var panel: Panel? = null

    init {
        orientation = VERTICAL
        chips.orientation = HORIZONTAL
        chipsScroll.isHorizontalScrollBarEnabled = false
        chipsScroll.addView(chips)
        commandsRow.orientation = HORIZONTAL
        commandsRow.gravity = Gravity.CENTER_VERTICAL
        commandsRow.setPadding(context.dp(6), context.dp(4), context.dp(6), context.dp(6))
        commandsRow.contentDescription = context.getString(R.string.eurise_bar_description)
        commandsRow.addView(chipsScroll.lp(0, LayoutParams.WRAP_CONTENT, 0f))
        commandsRow.addView(buttons.lp(0, LayoutParams.WRAP_CONTENT, 1f))
        toolsRow.orientation = HORIZONTAL
        addView(toolsRow.lp(h = context.dp(40)))
        addView(commandsRow.lp())
        panelHost.isVisible = false
    }

    /** Collega la barra alla tastiera: la chiama LatinIME quando crea la sua vista. */
    fun attach(ime: LatinIME, inputView: View) {
        this.ime = ime
        keyboardArea = inputView.findViewById(R.id.keyboard_view_wrapper)
        arrange()
        refreshLook()
        current = java.lang.ref.WeakReference(this)
        // Ricevitore e ascolto degli appunti si registrano una volta sola per processo: la vista della tastiera
        // si ricrea spesso (tema, lingua) e una barra vecchia non deve inserire testo.
        if (!registered) {
            registered = true
            val app = context.applicationContext
            androidx.core.content.ContextCompat.registerReceiver(
                app, DictationReceiver(), android.content.IntentFilter(ACTION_DICTATION),
                bridge.suitePermission(), null, androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
            )
            (app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.addPrimaryClipChangedListener {
                lastClipAt = System.currentTimeMillis() // il contenuto si legge solo quando la tastiera è visibile
            }
        }
    }

    /**
     * Ordine delle file: di base la barra di Eurise sta sopra e i suggerimenti subito sopra i tasti
     * ([BAR_ON_TOP] spento: il contrario). I pannelli si mettono sempre al posto dei tasti, qualunque sia l'ordine.
     */
    private fun arrange() {
        val frame = parent as? ViewGroup
        if (frame == null) {
            if (panelHost.parent == null) addView(panelHost.lp())
            return
        }
        (panelHost.parent as? ViewGroup)?.removeView(panelHost)
        frame.addView(panelHost, frame.indexOfChild(keyboardArea), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        val strip = frame.findViewById<View>(R.id.strip_container)?.takeIf { it.parent === frame } ?: return
        frame.removeView(this)
        val at = frame.indexOfChild(strip)
        frame.addView(this, if (prefs.getBoolean(BAR_ON_TOP, true)) at else at + 1)
    }

    private val prefs get() = context.prefs()
    private val enabled get() = prefs.getBoolean(Settings.PREF_EURISE_BAR, Defaults.PREF_EURISE_BAR)

    /** Colori, fila degli strumenti e visibilità secondo le impostazioni correnti. */
    fun refreshLook() {
        p = Palette()
        setBackgroundColor(p.background)
        isVisible = enabled || prefs.getBoolean(SEPARATE_TOOLS, false)
        commandsRow.isVisible = enabled
        buildTools()
        showChips(emptyList())
    }

    private fun buildTools() {
        toolsRow.removeAllViews()
        val separate = prefs.getBoolean(SEPARATE_TOOLS, false)
        toolsRow.isVisible = separate
        if (!separate) return
        getEnabledToolbarKeys(prefs).forEach { key ->
            val button = createToolbarKey(context, key)
            button.setColorFilter(p.text)
            button.setOnClickListener { v ->
                onClickToolbarKey(v) { code -> ime.onCodeInput(code, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false) }
            }
            toolsRow.addView(button.lp(0, LayoutParams.MATCH_PARENT, 1f))
        }
    }

    /**
     * Gli otto comandi: Calcola, Codici, File, Persona, Disponibilità, Traduci, Browser, AI. Icona sopra l'etichetta, così ci
     * stanno tutti anche sui telefoni stretti; con le proposte presenti restano solo le icone. Browser e AI: tocco =
     * cerca subito nel riquadro sopra l'app, pressione lunga = scegli motore, browser o assistente.
     */
    private fun buildButtons(compact: Boolean) {
        buttons.removeAllViews()
        listOf(
            Command(R.string.eurise_calc, R.drawable.eurise_ic_calc) { open(CalcPanel(this)) },
            Command(R.string.eurise_codes, R.drawable.eurise_ic_key) { open(CodesPanel(this)) },
            Command(R.string.eurise_file, R.drawable.eurise_ic_folder) { open(FilePanel(this)) },
            Command(R.string.eurise_person, R.drawable.eurise_ic_person) { open(PersonPanel(this)) },
            Command(R.string.eurise_availability, R.drawable.eurise_ic_calendar) { open(CalendarPanel(this)) },
            Command(R.string.eurise_translate, R.drawable.eurise_ic_translate) { open(TranslatePanel(this)) },
            Command(R.string.eurise_browser, R.drawable.eurise_ic_globe) { searchWeb() },
            Command(R.string.eurise_ai, R.drawable.eurise_ic_spark) { askAi() },
        ).forEachIndexed { i, (label, icon, action) ->
            val text = context.getString(label)
            val active = panel?.title == label
            val b = command(if (compact) "" else text, icon, active) { if (active) close() else action() }
            if (label == R.string.eurise_browser) b.setOnLongClickListener { open(BrowserPanel(this)); true }
            if (label == R.string.eurise_ai) b.setOnLongClickListener { open(AiPanel(this)); true }
            buttons.addView(b.lp(0, context.dp(if (compact) 40 else 46), 1f).apply { (layoutParams as LayoutParams).marginStart = if (i == 0) 0 else context.dp(4) })
        }
    }

    /** Un comando: icona e, sotto, l'etichetta piccola. */
    private fun command(label: String, icon: Int, on: Boolean, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        background = rounded(if (on) p.accent else p.surface, context.dp(12).toFloat(), p.faint)
        isClickable = true
        contentDescription = label
        setOnClickListener { onClick() }
        addView(android.widget.ImageView(context).apply {
            setImageDrawable(androidx.core.content.ContextCompat.getDrawable(context, icon)?.mutate()?.apply { setTint(if (on) p.onAccent else p.accent) })
            layoutParams = LayoutParams(context.dp(18), context.dp(18))
        })
        if (label.isNotEmpty()) addView(android.widget.TextView(context).apply {
            text = label
            textSize = 10f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(if (on) p.onAccent else p.text)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(context.dp(2), context.dp(2), context.dp(2), 0)
        })
    }

    /** Ciò che è selezionato o, se niente, l'ultima riga scritta nel campo: la domanda per Browser e AI. */
    private fun query(): String {
        val selected = ime.currentInputConnection?.getSelectedText(0)?.toString().orEmpty()
        return selected.ifBlank { before(300).substringAfterLast('\n') }
    }

    /** Cerca sul web nel riquadro, con il motore scelto. */
    internal fun searchWeb() {
        val q = query()
        close()
        WebSearch.search(context, bridge.hubPackage, q)
    }

    /** Chiede all'assistente collegato; senza collegamento apre la scelta (di base non ce n'è nessuno). */
    internal fun askAi() {
        val assistant = WebSearch.assistant(context) ?: return open(AiPanel(this))
        val q = query()
        close()
        WebSearch.ask(context, bridge.hubPackage, assistant, q)
    }

    internal fun open(next: Panel) {
        panel?.onClose()
        val height = keyboardArea.height.takeIf { it > 0 } ?: context.dp(260)
        panel = next
        panelHost.removeAllViews()
        panelHost.addView(next.view(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height))
        panelHost.isVisible = true
        keyboardArea.isVisible = false
        showChips(emptyList())
    }

    internal fun close() {
        panel?.onClose()
        panel = null
        panelHost.removeAllViews()
        panelHost.isVisible = false
        keyboardArea.isVisible = true
        showChips(emptyList())
    }

    /** Chiede i permessi con una schermata trasparente (una tastiera non può farlo da sola). */
    internal fun ask(vararg permissions: String) {
        val intent = Intent(context, EurisePermissionActivity::class.java)
            .putExtra(EurisePermissionActivity.EXTRA_PERMISSIONS, permissions)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    // --- Scrittura ---

    internal fun insert(text: String) = ime.euriseReplace(0, text)

    private fun replaceBefore(length: Int, text: String) = ime.euriseReplace(length, text)

    internal fun before(n: Int = 160): String = ime.currentInputConnection?.getTextBeforeCursor(n, 0)?.toString().orEmpty()

    internal val targetPackage: String get() = ime.currentInputEditorInfo?.packageName.orEmpty()

    internal fun toast(text: Int) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    internal fun attachPicked() {
        val picked = EuriseFilePickActivity.pending ?: return
        EuriseFilePickActivity.pending = null
        if (!ime.euriseCommitFile(picked.file, picked.mime)) {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".eurise.files", picked.file)
            val share = Intent(Intent.ACTION_SEND).setType(picked.mime).putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            runCatching { context.startActivity(Intent.createChooser(share, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    // --- Dettatura (Eurise Voce) ---

    /** Il testo dettato torna da Eurise Voce (solo app con la stessa firma possono mandarlo) e va alla barra attuale. */
    private class DictationReceiver : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() } ?: return
            val bar = current?.get()
            if (bar != null && bar.ready && bar.ime.isInputViewShown && bar.ime.currentInputConnection != null) bar.insertDictation(text)
            else pendingDictation = text
        }
    }

    private val ready get() = ::ime.isInitialized

    private fun insertDictation(text: String) {
        val before = before(1)
        insert(if (before.isNotEmpty() && !before.last().isWhitespace()) " $text" else text)
    }

    /** Il tasto della voce apre la dettatura di Eurise Voce; falso se non è installata (resta quella di sistema). */
    fun startDictation(): Boolean {
        if (!::ime.isInitialized || !bridge.installed(bridge.voicePackage)) return false
        val intent = Intent().setClassName(bridge.voicePackage, "eu.eurise.voice.DictateActivity")
            .putExtra("reply_to", context.packageName)
            .putExtra("language", ime.euriseLanguage())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    // --- Ciclo di vita ---

    /** Nuovo campo di testo: si chiude il pannello e si allega l'eventuale file appena scelto. */
    fun onStartInput() {
        if (!::ime.isInitialized) return
        if (panel != null && panel !is CodesPanel) close()
        refreshLook()
        attachPicked()
        pendingDictation?.let { pendingDictation = null; handler.postDelayed({ insertDictation(it) }, 150) }
        handler.postDelayed({ onTextChanged() }, 120)
    }

    fun onFinishInput() {
        if (::ime.isInitialized && panel != null) close()
    }

    private val check = Runnable { computeChips() }

    /** Il testo è cambiato: le proposte si ricalcolano poco dopo, non a ogni lettera. */
    fun onTextChanged() {
        if (!::ime.isInitialized || !enabled || panel != null) return
        handler.removeCallbacks(check)
        handler.postDelayed(check, 150)
    }

    private data class Command(val label: Int, val icon: Int, val action: () -> Unit)

    private data class Chip(val label: String, val icon: Int? = null, val action: () -> Unit)

    /** Comandi subito (aprono un pannello); le altre proposte si calcolano fuori dal thread principale (Rubrica, Calendario). */
    private fun computeChips() {
        val text = before()
        Detect.command(text)?.let { (cmd, raw) ->
            replaceBefore(raw.length, "")
            when (cmd) {
                Detect.Command.Availability -> open(CalendarPanel(this))
                Detect.Command.File -> open(FilePanel(this))
                Detect.Command.Person -> open(PersonPanel(this))
                Detect.Command.Codes -> open(CodesPanel(this))
            }
            return
        }
        val abbreviations = prefs.getString(ABBREVIATIONS, "").orEmpty()
        chipWorker.execute {
            val found = buildChips(text, abbreviations)
            post { if (panel == null && before() == text) showChips(found) }
        }
    }

    private fun buildChips(text: String, abbreviationsPref: String): List<Chip> {
        val found = mutableListOf<Chip>()
        Calc.find(text)?.let { (raw, result) ->
            val keep = if (raw.trimEnd().endsWith("=")) raw.trimEnd() + " " else ""
            found += Chip("= $result") { replaceBefore(raw.length, keep + result) }
        }
        Detect.mention(text)?.takeIf { bridge.canReadContacts() }?.let { name ->
            bridge.people(name, 3).forEach { person ->
                bridge.details(person).drop(1).take(2).forEach { d ->
                    found += Chip("${person.name} · ${d.value}", R.drawable.eurise_ic_person) { replaceBefore(name.length + 1, d.value) }
                }
                if (bridge.details(person).size == 1) found += Chip(person.name, R.drawable.eurise_ic_person) { replaceBefore(name.length + 1, person.name) }
            }
        }
        Detect.lastWord(text)?.let { word ->
            Detect.parseAbbreviations(abbreviationsPref)[word.lowercase()]?.let { full ->
                found += Chip("→ $full") { replaceBefore(word.length, full) }
            }
        }
        val sentence = Detect.lastSentence(text)
        if (Regex("""(?iu)(arrivo|arriviamo|ci sono)(\s+alle)?\s*$""").containsMatchIn(sentence)) {
            bridge.nextEventWithPlace()?.let { (start, title) ->
                val time = start.format(DateTimeFormatter.ofPattern("H:mm"))
                val withAlle = sentence.trimEnd().endsWith("alle", ignoreCase = true)
                found += Chip(context.getString(R.string.eurise_arrive, time, title), R.drawable.eurise_ic_calendar) {
                    insert(if (withAlle) " $time" else " alle $time")
                }
            }
        }
        if (Detect.looksLikeTask(sentence)) {
            found += Chip(context.getString(R.string.eurise_create_task), R.drawable.eurise_ic_calendar) {
                if (bridge.sendTask(sentence)) toast(R.string.eurise_task_sent) else toast(R.string.eurise_hub_missing)
                showChips(emptyList())
            }
        }
        clipChip()?.let { found += it }
        return found.take(4)
    }

    /** Una cosa copiata da poco (IBAN, numero, email): si propone di salvarla. Gli appunti si leggono solo qui. */
    private fun clipChip(): Chip? {
        if (System.currentTimeMillis() - lastClipAt > 120_000) return null
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = runCatching { cm.primaryClip }.getOrNull() ?: return null
        if (android.os.Build.VERSION.SDK_INT >= 33 && clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return null
        val value = clip.getItemAt(0)?.text?.toString()?.trim() ?: return null
        return when (Detect.kindOf(value)) {
            Detect.Kind.Iban -> Chip("${context.getString(R.string.eurise_copied_iban)} · ${context.getString(R.string.eurise_save_note)}") {
                if (bridge.sendNote("IBAN\n$value")) toast(R.string.eurise_note_sent) else toast(R.string.eurise_hub_missing); lastClipAt = 0; showChips(emptyList())
            }
            Detect.Kind.Phone -> Chip("${context.getString(R.string.eurise_copied_phone)} · ${context.getString(R.string.eurise_save_contact)}", R.drawable.eurise_ic_person) {
                bridge.saveContactNumber(value); lastClipAt = 0; showChips(emptyList())
            }
            Detect.Kind.Email -> Chip("${context.getString(R.string.eurise_copied_email)} · ${context.getString(R.string.eurise_save_note)}") {
                if (bridge.sendNote(value)) toast(R.string.eurise_note_sent) else toast(R.string.eurise_hub_missing); lastClipAt = 0; showChips(emptyList())
            }
            null -> null
        }
    }

    /** Con proposte presenti i quattro comandi restano come sole icone, così la barra non cambia altezza. */
    private fun showChips(list: List<Chip>) {
        chips.removeAllViews()
        list.forEachIndexed { i, c ->
            val v = context.pill(c.label, p, on = i == 0, icon = c.icon) { c.action() }
            chips.addView(v.lp(LayoutParams.WRAP_CONTENT, context.dp(40)).apply { (layoutParams as LayoutParams).marginEnd = context.dp(5) })
        }
        val has = list.isNotEmpty()
        chipsScroll.layoutParams = LayoutParams(if (has) 0 else 0, LayoutParams.WRAP_CONTENT, if (has) 2.2f else 0f)
        chipsScroll.isVisible = has
        buildButtons(compact = has)
    }

    companion object {
        const val SEPARATE_TOOLS = "eurise_separate_tools"
        /** Barra di Eurise sopra i suggerimenti (di base sì). */
        const val BAR_ON_TOP = "eurise_bar_on_top"
        const val ABBREVIATIONS = "eurise_abbreviations"
        const val ACTION_DICTATION = "eu.eurise.action.DICTATION_RESULT"
        private var current: java.lang.ref.WeakReference<EuriseBar>? = null
        private var registered = false
        @Volatile private var pendingDictation: String? = null
        @Volatile private var lastClipAt = 0L
        private val chipWorker = java.util.concurrent.Executors.newSingleThreadExecutor()
    }
}

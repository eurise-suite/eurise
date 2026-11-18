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
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.utils.ToolbarKey
import helium314.keyboard.latin.utils.createToolbarKey
import helium314.keyboard.latin.utils.getEnabledToolbarKeys
import helium314.keyboard.latin.utils.onClickToolbarKey
import helium314.keyboard.latin.utils.onLongClickToolbarKey
import helium314.keyboard.latin.utils.prefs
import java.time.format.DateTimeFormatter

/**
 * Eurise nella tastiera (tavole TastComparsa, approvate il 16 novembre). Sopra i tasti resta solo la fila delle parole:
 * - il tasto con la stella, in fondo alla fila delle parole dal lato della mano, apre un riquadro sopra le parole con i
 *   comandi di Eurise (Calcola, Codici, File, Persona, Libero, Traduci, Cerca) e gli strumenti della tastiera (Appunti,
 *   Emoji, Cursore, Una mano, Annulla, Voce, Opzioni), tutti fatti allo stesso modo. Il riquadro si chiude scegliendo,
 *   ritoccando la stella o riprendendo a scrivere; sta sopra l'app senza spostarla;
 * - le proposte del momento (calcolo, contatto citato con @, abbreviazione, «Crea attività», «arrivo alle…») entrano tra
 *   le parole come prima voce; se ce n'è più d'una, un pallino sulla stella e le altre in cima al riquadro.
 * I pannelli (Calcola, Codici…) si aprono al posto dei tasti. Questa vista resta nella colonna della tastiera solo per i
 * pannelli e, a richiesta, la vecchia fila degli strumenti separata.
 */
class EuriseBar(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    internal lateinit var ime: LatinIME
    private lateinit var keyboardArea: View
    internal val bridge = Bridge(context)
    private val handler = Handler(Looper.getMainLooper())
    internal var p = Palette()
        private set

    private val toolsRow = LinearLayout(context)
    /** Il riquadro a comparsa: proposte in più, comandi di Eurise, strumenti della tastiera. */
    private val menu = LinearLayout(context)
    private val menuChips = LinearLayout(context)
    private val menuChipsScroll = HorizontalScrollView(context)
    private val euriseRow = LinearLayout(context)
    private val toolsMenuRow = LinearLayout(context)
    private val panelHost = FrameLayout(context)
    private var panel: Panel? = null
    private var chipsNow: List<Chip> = emptyList()
    /** Dove va la prima proposta: dentro la fila delle parole, prima delle parole. */
    private var stripSlot: LinearLayout? = null
    private var stripKey: android.widget.ImageButton? = null
    val menuOpen get() = menu.parent != null && menu.isVisible

    init {
        orientation = VERTICAL
        toolsRow.orientation = HORIZONTAL
        addView(toolsRow.lp(h = context.dp(40)))
        panelHost.isVisible = false
        menu.orientation = VERTICAL
        menu.isVisible = false
        menu.isClickable = true // i tocchi sul riquadro non passano all'app sotto
        menu.contentDescription = context.getString(R.string.eurise_bar_description)
        menu.setPadding(context.dp(8), context.dp(10), context.dp(8), context.dp(8))
        menuChips.orientation = HORIZONTAL
        menuChipsScroll.isHorizontalScrollBarEnabled = false
        menuChipsScroll.addView(menuChips)
        euriseRow.orientation = HORIZONTAL
        toolsMenuRow.orientation = HORIZONTAL
    }

    /** Collega la barra alla tastiera: la chiama LatinIME quando crea la sua vista. */
    fun attach(ime: LatinIME, inputView: View) {
        this.ime = ime
        keyboardArea = inputView.findViewById(R.id.keyboard_view_wrapper)
        arrange()
        refreshLook()
        current = java.lang.ref.WeakReference(this)
        post { installMenu() }
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

    /**
     * Il riquadro sta nella finestra della tastiera, sopra la fila delle parole, come i tasti a comparsa di HeliBoard:
     * così copre l'app senza farla rimpicciolire. La prima proposta va in uno spazio della fila delle parole.
     */
    private fun installMenu() {
        val frame = parent as? ViewGroup ?: return
        stripSlot = frame.findViewById(R.id.eurise_proposal)
        stripKey = frame.findViewById(R.id.suggestions_strip_toolbar_key)
        val content = rootView?.findViewById<ViewGroup>(android.R.id.content) ?: return
        (menu.parent as? ViewGroup)?.removeView(menu)
        // Una tastiera ricreata (tema, lingua) lascerebbe il riquadro vecchio nella finestra.
        (0 until content.childCount).map { content.getChildAt(it) }.filter { it.tag == MENU_TAG }.forEach { content.removeView(it) }
        menu.tag = MENU_TAG
        content.addView(menu, FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            leftMargin = context.dp(6); rightMargin = context.dp(6)
        })
        decorateKey()
    }

    /** La stella nella fila delle parole: accesa col riquadro aperto, pallino se ci sono altre proposte. */
    fun decorateKey(key: android.widget.ImageButton? = stripKey) {
        key ?: return
        stripKey = key
        val open = menuOpen
        val star = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.eurise_ic_star)?.mutate()?.apply { setTint(if (open) p.onAccent else p.accent) }
        val icon = if (chipsNow.size > 1 && !open && star != null) {
            val dot = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(DOT); setSize(context.dp(8), context.dp(8)) }
            android.graphics.drawable.LayerDrawable(arrayOf(star, dot)).apply {
                setLayerInset(1, context.dp(17), 0, 0, context.dp(17))
            }
        } else star
        key.setImageDrawable(icon)
        key.background = rounded(if (open) p.accent else p.surface, context.dp(12).toFloat(), p.faint)
        key.contentDescription = context.getString(if (open) R.string.eurise_menu_close else R.string.eurise_menu_open)
        key.scaleX = 1f
    }

    /** Il tasto con la stella. */
    fun toggleMenu() { if (menuOpen) closeMenu() else openMenu() }

    private fun openMenu() {
        if (panel != null) close()
        if (menu.parent == null) installMenu()
        val content = menu.parent as? ViewGroup ?: return
        buildMenu()
        // Il riquadro poggia sopra la fila delle parole, ovunque sia la tastiera nella finestra.
        val strip = (parent as? ViewGroup)?.findViewById<View>(R.id.strip_container) ?: return
        val a = IntArray(2).also { strip.getLocationInWindow(it) }
        val b = IntArray(2).also { content.getLocationInWindow(it) }
        (menu.layoutParams as FrameLayout.LayoutParams).bottomMargin = (b[1] + content.height) - a[1] + context.dp(4)
        menu.background = rounded(p.background, context.dp(20).toFloat()).also { menu.elevation = context.dp(8).toFloat() }
        menu.isVisible = true
        menu.requestLayout()
        decorateKey()
    }

    fun closeMenu() {
        if (!menu.isVisible) return
        menu.isVisible = false
        decorateKey()
    }

    /** Dove comincia il riquadro, rispetto a [view] (per l'area toccabile della tastiera); -1 se è chiuso. */
    fun menuTop(view: View): Int {
        if (!menuOpen || menu.height == 0) return -1
        val a = IntArray(2).also { menu.getLocationInWindow(it) }
        val b = IntArray(2).also { view.getLocationInWindow(it) }
        return a[1] - b[1]
    }

    private fun section(title: Int) = context.label(context.getString(title).uppercase(), p, size = 11f, bold = true, muted = true).apply {
        setPadding(context.dp(4), 0, 0, context.dp(6))
    }

    private fun buildMenu() {
        menu.removeAllViews()
        if (chipsNow.size > 1) {
            menuChips.removeAllViews()
            chipsNow.drop(1).forEach { c ->
                menuChips.addView(context.pill(c.label, p, icon = c.icon) { closeMenu(); c.action() }.lp(LayoutParams.WRAP_CONTENT, context.dp(40)).apply {
                    (layoutParams as LayoutParams).marginEnd = context.dp(5)
                })
            }
            menu.addView(menuChipsScroll.lp(h = LayoutParams.WRAP_CONTENT).apply { setPadding(0, 0, 0, context.dp(8)) })
        }
        menu.addView(section(R.string.eurise_menu_eurise))
        buildButtons()
        menu.addView(euriseRow.lp())
        menu.addView(View(context).apply { setBackgroundColor(p.faint) }.lp(h = 1, top = context.dp(8)))
        menu.addView(section(R.string.eurise_menu_keyboard).apply { setPadding(context.dp(4), context.dp(8), 0, context.dp(6)) })
        buildKeyboardTools()
        menu.addView(toolsMenuRow.lp())
    }

    private val prefs get() = context.prefs()
    private val enabled get() = prefs.getBoolean(Settings.PREF_EURISE_BAR, Defaults.PREF_EURISE_BAR)

    /** Colori, fila degli strumenti e visibilità secondo le impostazioni correnti. */
    fun refreshLook() {
        p = Palette()
        setBackgroundColor(p.background)
        isVisible = prefs.getBoolean(SEPARATE_TOOLS, false)
        buildTools()
        closeMenu()
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

    /** I comandi di Eurise nel riquadro (tavola TastComparsaAperta). Cerca: pressione lunga = motore e browser. */
    private fun buildButtons() {
        euriseRow.removeAllViews()
        listOf(
            Command(R.string.eurise_calc, R.drawable.eurise_ic_calc) { open(CalcPanel(this)) },
            Command(R.string.eurise_codes, R.drawable.eurise_ic_key) { open(CodesPanel(this)) },
            Command(R.string.eurise_file, R.drawable.eurise_ic_folder) { open(FilePanel(this)) },
            Command(R.string.eurise_person, R.drawable.eurise_ic_person) { open(PersonPanel(this)) },
            Command(R.string.eurise_free, R.drawable.eurise_ic_calendar) { open(CalendarPanel(this)) },
            Command(R.string.eurise_translate, R.drawable.eurise_ic_translate) { open(TranslatePanel(this)) },
            Command(R.string.eurise_search, R.drawable.eurise_ic_globe) { openSearch(ai = false) },
        ).forEachIndexed { i, (label, icon, action) ->
            val text = context.getString(label)
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, icon)?.mutate()?.apply { setTint(p.accent) }
            val b = command(text, drawable) { closeMenu(); action() }
            if (label == R.string.eurise_search) b.setOnLongClickListener { closeMenu(); open(BrowserPanel(this)); true }
            euriseRow.addView(b.cell(i))
        }
    }

    /**
     * Gli strumenti della tastiera nel riquadro, fatti come i comandi di Eurise (richiesta del proprietario, 16 novembre),
     * nell'ordine scelto dal proprietario: Opzioni, Una mano, Emoji, Cursore, Annulla, Voce, Appunti (i più usati a destra,
     * sotto il pollice). Pressione lunga = quella di HeliBoard.
     */
    private fun buildKeyboardTools() {
        toolsMenuRow.removeAllViews()
        listOf(
            ToolbarKey.SETTINGS to R.string.eurise_tool_settings,
            ToolbarKey.ONE_HANDED to R.string.eurise_tool_one_hand,
            ToolbarKey.EMOJI to R.string.eurise_tool_emoji,
            ToolbarKey.DPAD to R.string.eurise_tool_cursor,
            ToolbarKey.UNDO to R.string.eurise_tool_undo,
            ToolbarKey.VOICE to R.string.eurise_tool_voice,
            ToolbarKey.CLIPBOARD to R.string.eurise_tool_clipboard,
        ).forEachIndexed { i, (key, label) ->
            val drawable = KeyboardIconsSet.instance.getNewDrawable(key.name, context)?.mutate()?.apply { setTint(p.accent) }
            val b = command(context.getString(label), drawable) {}
            b.tag = key
            b.setOnClickListener { v -> closeMenu(); onClickToolbarKey(v) { code -> input(code) } }
            b.setOnLongClickListener { v -> closeMenu(); onLongClickToolbarKey(v) { code, _ -> input(code) }; true }
            toolsMenuRow.addView(b.cell(i))
        }
    }

    private fun input(code: Int) = ime.onCodeInput(code, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false)

    private fun View.cell(i: Int) = lp(0, context.dp(54), 1f).apply { (layoutParams as LayoutParams).marginStart = if (i == 0) 0 else context.dp(4) }

    /** Un comando: icona e, sotto, l'etichetta piccola; tutti uguali (tasti dello stesso gruppo, stessa larghezza). */
    private fun command(label: String, icon: android.graphics.drawable.Drawable?, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        background = rounded(p.surface, context.dp(13).toFloat(), p.faint)
        isClickable = true
        contentDescription = label
        setOnClickListener { onClick() }
        addView(android.widget.ImageView(context).apply {
            setImageDrawable(icon)
            layoutParams = LayoutParams(context.dp(19), context.dp(19))
        })
        addView(android.widget.TextView(context).apply {
            text = label
            textSize = 10f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(p.text)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setPadding(context.dp(2), context.dp(3), context.dp(2), 0)
        })
    }

    /** Ciò che è selezionato o, se niente, l'ultima riga scritta nel campo: la domanda per Browser e AI. */
    private fun query(): String {
        val selected = ime.currentInputConnection?.getSelectedText(0)?.toString().orEmpty()
        return selected.ifBlank { before(300).substringAfterLast('\n') }
    }

    /** Cerca sul web nel riquadro, con il motore scelto (dalla scelta del pannello BrowserPanel). */
    internal fun searchWeb() {
        val q = query()
        close()
        WebSearch.search(context, bridge.hubPackage, q)
    }

    /** Tasti Browser e AI: la ricerca rapida con i posti e le tacche di privacy (TastCerca). */
    internal fun openSearch(ai: Boolean) {
        val selected = ime.currentInputConnection?.getSelectedText(0)?.toString().orEmpty()
        open(SearchPanel(this, query(), fromSelection = selected.isNotBlank(), ai = ai))
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
        closeMenu()
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
        if (!::ime.isInitialized) return
        Phrases.save(context)
        closeMenu()
        if (panel != null) close()
    }

    private val check = Runnable { computeChips() }

    /** Il testo è cambiato: le proposte si ricalcolano poco dopo, non a ogni lettera. */
    fun onTextChanged() {
        if (!::ime.isInitialized || !enabled || panel != null) return
        closeMenu() // si riprende a scrivere: il riquadro si chiude
        handler.removeCallbacks(check)
        handler.postDelayed(check, 150)
    }

    private data class Command(val label: Int, val icon: Int, val action: () -> Unit)

    private data class Chip(val label: String, val icon: Int? = null, val onLong: (() -> Unit)? = null, val action: () -> Unit)

    /** Comandi subito (aprono un pannello); le altre proposte si calcolano fuori dal thread principale (Rubrica, Calendario). */
    private fun computeChips() {
        val text = before()
        Phrases.observe(context, text)
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
        // Frase intera (tavola TastImpostazioniFrase): la frase abituale che comincia così, prima di tutto.
        Phrases.propose(context, text)?.let { (current, rest) ->
            found += Chip("→ $rest", onLong = {
                Phrases.forget(context, current, rest); toast(R.string.eurise_phrase_forgotten); showChips(emptyList())
            }) { insert("$rest "); showChips(emptyList()) }
        }
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
        // Gli appunti stanno solo nella fila dei suggerimenti sopra i tasti (tavola TastRighe): qui schiacciavano i comandi.
        return found.take(4)
    }

    /**
     * Una cosa copiata da poco (IBAN, numero, email): si propone di salvarla. Fuori dalla barra dall'11 novembre; tornerà
     * come azione «Salva» dentro la proposta degli appunti della fila dei suggerimenti (tavola TastRighe).
     */
    @Suppress("unused")
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

    /** La prima proposta tra le parole (tavola TastComparsaProposta); le altre nel riquadro, col pallino sulla stella. */
    private fun showChips(list: List<Chip>) {
        chipsNow = if (enabled) list else emptyList()
        val slot = stripSlot
        if (slot != null) {
            slot.removeAllViews()
            chipsNow.firstOrNull()?.let { c ->
                slot.addView(context.pill(c.label, p, on = true, icon = c.icon) { c.action() }.apply {
                    minimumHeight = context.dp(32)
                    c.onLong?.let { long -> setOnLongClickListener { long(); true } }
                }.lp(LayoutParams.WRAP_CONTENT, context.dp(32)).apply {
                    (layoutParams as LayoutParams).gravity = Gravity.CENTER_VERTICAL
                    (layoutParams as LayoutParams).marginStart = context.dp(4)
                    (layoutParams as LayoutParams).marginEnd = context.dp(4)
                })
            }
            slot.isVisible = chipsNow.isNotEmpty()
        }
        decorateKey()
    }

    companion object {
        /** Il pallino sulla stella: arancio della suite, si vede su Perla e su Notte. */
        private const val DOT = 0xFFC2662D.toInt()
        private const val MENU_TAG = "eurise_menu"

        /** Dalla fila delle parole (SuggestionStripView): la stella. */
        @JvmStatic fun toggleFromStrip() { current?.get()?.toggleMenu() }

        /** Dalla fila delle parole, quando ridisegna il suo tasto: lo veste da stella. */
        @JvmStatic fun decorateFromStrip(key: android.widget.ImageButton) { current?.get()?.decorateKey(key) }

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

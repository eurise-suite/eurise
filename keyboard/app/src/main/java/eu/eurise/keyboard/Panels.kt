package eu.eurise.keyboard

import android.Manifest
import android.content.Intent
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.prefs
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.Executors

/** Un pannello che prende il posto dei tasti; [title] è l'etichetta del comando che lo apre. */
abstract class Panel(protected val bar: EuriseBar, val title: Int) {
    protected val context get() = bar.context
    protected val p get() = bar.p
    abstract fun view(): View
    open fun onClose() {}

    /** Intestazione comune: titolo e pulsante per tornare ai tasti. */
    protected fun header(text: String, extra: View? = null) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(context.label(text, p, 15f, bold = true).lp(0, weight = 1f))
        if (extra != null) addView(extra)
        addView(iconButton(R.drawable.eurise_ic_close, context.getString(R.string.eurise_close)) { bar.close() })
    }

    protected fun iconButton(icon: Int, description: String, enabled: Boolean = true, onClick: () -> Unit) = ImageButton(context).apply {
        setImageDrawable(ContextCompat.getDrawable(context, icon)?.mutate()?.apply { setTint(if (enabled) p.text else p.muted) })
        background = rounded(p.surface, context.dp(12).toFloat(), p.faint)
        contentDescription = description
        isEnabled = enabled
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(context.dp(44), context.dp(36)).apply { marginStart = context.dp(6) }
    }

    protected fun column(vararg views: View) = ScrollView(context).apply {
        isFillViewport = true
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(12), context.dp(4), context.dp(12), context.dp(8))
            views.forEachIndexed { i, v -> addView(v.lp(top = if (i == 0) 0 else context.dp(8))) }
        })
    }

    protected fun privacy(text: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(android.widget.ImageView(context).apply {
            setImageDrawable(ContextCompat.getDrawable(context, R.drawable.eurise_ic_lock)?.mutate()?.apply { setTint(p.muted) })
            layoutParams = LinearLayout.LayoutParams(context.dp(13), context.dp(13)).apply { marginEnd = context.dp(6) }
        })
        addView(context.label(context.getString(text), p, 11.5f, muted = true).lp(0, weight = 1f))
    }

    protected fun permissionRequest(text: Int, vararg permissions: String) = column(
        context.label(context.getString(text), p, 14f),
        context.pill(context.getString(R.string.eurise_allow), p, on = true) { bar.ask(*permissions); bar.close() }.lp(LinearLayout.LayoutParams.WRAP_CONTENT),
    )

    protected companion object {
        /** Letture dal telefono fuori dal thread principale, una alla volta. */
        val io = Executors.newSingleThreadExecutor()
    }

    protected fun <T> load(work: () -> T, then: (T) -> Unit) {
        io.execute {
            val result = work()
            bar.post { then(result) }
        }
    }
}

/** Disponibilità: il mese con i giorni liberi, poi gli orari del giorno scelto, scorrevoli; la frase pronta si inserisce. */
class CalendarPanel(bar: EuriseBar) : Panel(bar, R.string.eurise_availability) {
    private var month = YearMonth.now()
    private var day: LocalDate? = null
    private var minutes = 60
    private val chosen = linkedSetOf<Slot>()
    private var busy: List<Span> = emptyList()
    private val root = LinearLayout(bar.context).apply { orientation = LinearLayout.VERTICAL }
    private val prefs = bar.context.prefs()

    override fun view(): View {
        if (!bar.bridge.canReadCalendar()) return permissionRequest(R.string.eurise_need_calendar, Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        minutes = prefs.getInt("eurise_slot_minutes", 60)
        reload()
        return ScrollView(context).apply { addView(root) }
    }

    private fun reload() {
        val m = month
        load({ bar.bridge.busy(m.atDay(1), m.atEndOfMonth()) }) { busy = it; render() }
    }

    private fun render() {
        root.removeAllViews()
        root.setPadding(context.dp(12), context.dp(4), context.dp(12), context.dp(8))
        val now = LocalDateTime.now()
        val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()).replaceFirstChar { it.titlecase() } + " " + month.year
        val canGoBack = month.isAfter(YearMonth.now())
        val nav = LinearLayout(context).apply {
            addView(iconButton(R.drawable.eurise_ic_back, context.getString(R.string.eurise_prev_month), canGoBack) { if (canGoBack) { month = month.minusMonths(1); reload() } })
            addView(iconButton(R.drawable.eurise_ic_next, context.getString(R.string.eurise_next_month)) { month = month.plusMonths(1); reload() })
        }
        root.addView(header(name, nav).lp())

        val grid = GridLayout(context).apply {
            columnCount = 7
            background = rounded(p.surface, context.dp(14).toFloat())
            setPadding(context.dp(4), context.dp(6), context.dp(4), context.dp(6))
        }
        val weekdays = java.time.DayOfWeek.entries
        weekdays.forEach { d -> grid.addView(cell(d.getDisplayName(TextStyle.NARROW_STANDALONE, Locale.getDefault()), small = true)) }
        repeat(month.atDay(1).dayOfWeek.value - 1) { grid.addView(cell("")) }
        for (n in 1..month.lengthOfMonth()) {
            val date = month.atDay(n)
            val past = date.isBefore(now.toLocalDate())
            val free = !past && !Availability.isWeekend(date) && Availability.hasFree(date, busy, minutes, now)
            val picked = chosen.any { it.day == date }
            grid.addView(cell(n.toString(), selected = date == day, today = date == now.toLocalDate(), enabled = !past, dot = free, mark = picked) {
                day = date; render()
            })
        }
        root.addView(grid.lp(top = context.dp(6)))

        val selected = day
        if (selected == null) {
            root.addView(context.label(context.getString(R.string.eurise_pick_day), p, 12.5f, muted = true).lp(top = context.dp(8)))
        } else {
            val slots = Availability.slots(selected, busy, minutes, now)
            val dayName = selected.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.titlecase() } + " " + selected.dayOfMonth
            val durations = LinearLayout(context).apply {
                listOf(30 to R.string.eurise_minutes_30, 60 to R.string.eurise_hour_1, 120 to R.string.eurise_hours_2).forEach { (m, label) ->
                    addView(context.pill(context.getString(label), p, on = m == minutes) { minutes = m; prefs.edit().putInt("eurise_slot_minutes", m).apply(); render() }
                        .lp(LinearLayout.LayoutParams.WRAP_CONTENT, context.dp(34)).apply { (layoutParams as LinearLayout.LayoutParams).marginStart = context.dp(4) })
                }
            }
            root.addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(context.label(context.getString(if (slots.isEmpty()) R.string.eurise_busy_on else R.string.eurise_free_on, dayName), p, 13f, bold = true).lp(0, weight = 1f))
                addView(durations)
            }.lp(top = context.dp(8)))
            // Orari del giorno: una fila che scorre a destra e a sinistra.
            val row = LinearLayout(context)
            slots.forEach { s ->
                val on = s in chosen
                row.addView(context.pill(Availability.describe(s, now.toLocalDate()).substringAfter(' ').let { label -> label.replace("dalle ", "").replace(" alle ", " – ") }, p, on = on) {
                    if (on) chosen.remove(s) else chosen.add(s)
                    render()
                }.lp(LinearLayout.LayoutParams.WRAP_CONTENT, context.dp(40)).apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = context.dp(6) })
            }
            root.addView(HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(row) }.lp(top = context.dp(6)))
        }

        if (chosen.isNotEmpty()) {
            val sentence = Availability.sentence(chosen.toList(), now.toLocalDate())
            root.addView(context.label(sentence, p, 13.5f).apply {
                background = rounded(p.surface, context.dp(14).toFloat())
                setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
            }.lp(top = context.dp(8)))
            val tentative = CheckBox(context).apply {
                text = context.getString(R.string.eurise_tentative)
                setTextColor(p.text)
                textSize = 12.5f
                isChecked = prefs.getBoolean("eurise_tentative", true)
                setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("eurise_tentative", on).apply() }
            }
            root.addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(tentative.lp(0, weight = 1f))
                addView(context.pill(context.getString(R.string.eurise_insert), p, on = true) {
                    val list = chosen.toList()
                    if (tentative.isChecked) {
                        if (bar.bridge.granted(Manifest.permission.WRITE_CALENDAR)) io.execute { bar.bridge.addTentative(list, context.getString(R.string.eurise_tentative_title)) }
                        else bar.ask(Manifest.permission.WRITE_CALENDAR)
                    }
                    bar.close()
                    bar.insert(sentence)
                }.lp(LinearLayout.LayoutParams.WRAP_CONTENT, context.dp(44)))
            }.lp(top = context.dp(6)))
        }
        root.addView(privacy(R.string.eurise_calendar_private).lp(top = context.dp(8)))
    }

    private fun cell(text: String, small: Boolean = false, selected: Boolean = false, today: Boolean = false, enabled: Boolean = true,
                     dot: Boolean = false, mark: Boolean = false, onClick: (() -> Unit)? = null): View {
        val size = context.dp(if (small) 18 else 34)
        val label = TextView(context).apply {
            this.text = text
            gravity = Gravity.CENTER
            textSize = if (small) 10.5f else 13.5f
            typeface = if (selected || today) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            setTextColor(when { selected -> p.onAccent; !enabled || small -> p.muted; else -> p.text })
            if (selected) background = rounded(p.accent, context.dp(10).toFloat())
            else if (today) background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = context.dp(10).toFloat(); setStroke(context.dp(1.5), p.accent)
            }
            if (onClick != null && enabled) { isClickable = true; setOnClickListener { onClick() } }
            contentDescription = text
        }
        val wrap = android.widget.FrameLayout(context)
        wrap.addView(label, android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, size))
        if ((dot || mark) && !selected) wrap.addView(View(context).apply {
            background = rounded(if (mark) p.accent else 0xFF6E8F72.toInt(), context.dp(2).toFloat())
        }, android.widget.FrameLayout.LayoutParams(context.dp(if (mark) 14 else 4), context.dp(if (mark) 3 else 4), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = context.dp(2) })
        wrap.layoutParams = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply {
            width = 0; height = size
        }
        return wrap
    }
}

/** Persona: preferiti e poi tutti; si tocca un contatto e si sceglie cosa inserire (nome, numero, email, indirizzo). */
class PersonPanel(bar: EuriseBar) : Panel(bar, R.string.eurise_person) {
    private val list = LinearLayout(bar.context).apply { orientation = LinearLayout.VERTICAL }

    override fun view(): View {
        if (!bar.bridge.canReadContacts()) return permissionRequest(R.string.eurise_need_contacts, Manifest.permission.READ_CONTACTS)
        load({ bar.bridge.people() }) { show(it) }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(12), context.dp(4), context.dp(12), 0)
            addView(header(context.getString(R.string.eurise_person)).lp())
            addView(ScrollView(context).apply { addView(list) }.lp(h = 0, weight = 1f, top = context.dp(4)))
        }
    }

    private fun show(people: List<Bridge.Person>) {
        list.removeAllViews()
        if (people.isEmpty()) list.addView(context.label(context.getString(R.string.eurise_no_contacts), p, 13f, muted = true))
        people.forEach { person ->
            list.addView(context.label(person.name, p, 15f, bold = person.starred).apply {
                minHeight = context.dp(44)
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                setOnClickListener { load({ bar.bridge.details(person) }) { details(person, it) } }
            }.lp())
        }
    }

    private fun details(person: Bridge.Person, details: List<Bridge.Detail>) {
        list.removeAllViews()
        list.addView(context.label(person.name, p, 15f, bold = true).lp())
        details.forEach { d ->
            list.addView(context.pill(if (d.value == person.name) d.value else "${d.label}  ${d.value}", p) { bar.close(); bar.insert(d.value) }
                .lp(top = context.dp(6)))
        }
    }
}

/** File: il selettore di Android (telefono e cloud); il file si allega se l'app lo accetta, altrimenti si apre Condividi. */
class FilePanel(bar: EuriseBar) : Panel(bar, R.string.eurise_file) {
    override fun view(): View = column(
        header(context.getString(R.string.eurise_file)),
        context.label(context.getString(R.string.eurise_file_hint), p, 13f, muted = true),
        context.pill(context.getString(R.string.eurise_file_pick), p, on = true, icon = R.drawable.eurise_ic_folder) {
            bar.close()
            runCatching { context.startActivity(Intent(context, EuriseFilePickActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }.lp(LinearLayout.LayoutParams.WRAP_CONTENT, context.dp(44)),
        privacy(R.string.eurise_file_private),
    )
}

/** Codici: il codice 2FA del momento per le voci di Password collegate all'app in cui si scrive. */
class CodesPanel(bar: EuriseBar) : Panel(bar, R.string.eurise_codes) {
    private val body = LinearLayout(bar.context).apply { orientation = LinearLayout.VERTICAL }
    private val tick = object : Runnable {
        override fun run() { refresh(); bar.postDelayed(this, 1000) }
    }

    override fun view(): View {
        bar.post(tick)
        return column(header(context.getString(R.string.eurise_codes)), body, privacy(R.string.eurise_codes_private))
    }

    override fun onClose() { bar.removeCallbacks(tick) }

    private fun refresh() {
        val target = bar.targetPackage
        load({ bar.bridge.codes(target) }) { codes ->
            body.removeAllViews()
            when (codes) {
                Bridge.Codes.Missing -> body.addView(context.label(context.getString(R.string.eurise_codes_missing), p, 13.5f))
                Bridge.Codes.Locked -> {
                    body.addView(context.label(context.getString(R.string.eurise_codes_locked), p, 13.5f))
                    body.addView(context.pill(context.getString(R.string.eurise_codes_open_password), p, on = true, icon = R.drawable.eurise_ic_key) { bar.bridge.openPassword() }
                        .lp(LinearLayout.LayoutParams.WRAP_CONTENT, context.dp(44), top = context.dp(8)))
                }
                is Bridge.Codes.Open -> if (codes.codes.isEmpty()) body.addView(context.label(context.getString(R.string.eurise_codes_none), p, 13.5f))
                else codes.codes.forEach { c ->
                    val pretty = if (c.code.length == 6) c.code.substring(0, 3) + " " + c.code.substring(3) else c.code
                    body.addView(context.pill("$pretty  ·  ${c.title}  ·  ${c.remaining}s", p, on = true, icon = R.drawable.eurise_ic_key) {
                        bar.close(); bar.insert(c.code)
                    }.lp(h = context.dp(52), top = context.dp(6)))
                }
            }
        }
    }
}

/**
 * Calcola: una piccola calcolatrice al posto dei tasti. Il risultato compare mentre si scrive; «Scrivi» lo mette nel
 * testo (tenuto premuto, anche il calcolo). IVA aggiunge il 22% e la percentuale funziona come da ufficio.
 */
class CalcPanel(bar: EuriseBar) : Panel(bar, R.string.eurise_calc) {
    private var expr = ""
    private var last: String? = null
    private lateinit var exprView: TextView
    private lateinit var resultView: TextView
    private lateinit var writeLabel: TextView

    private fun result(): String? =
        expr.takeIf { it.isNotEmpty() }?.let { Calc.eval(it.replace('−', '-')) }?.let { Calc.format(it) }

    private fun refresh() {
        exprView.text = expr.replace('*', '×').replace('/', '÷').ifEmpty { "0" }
        val r = result()
        resultView.text = r?.let { "= $it" } ?: ""
        writeLabel.text = context.getString(R.string.eurise_calc_write, r ?: "")
        exprView.contentDescription = exprView.text
    }

    private fun press(key: String) {
        when (key) {
            "C" -> { if (expr.isNotEmpty()) last = expr; expr = "" }
            "⌫" -> expr = expr.dropLast(1)
            "( )" -> {
                val open = expr.count { it == '(' } - expr.count { it == ')' }
                expr += if (open > 0 && (expr.lastOrNull()?.let { it.isDigit() || it == ')' || it == '%' } == true)) ")" else "("
            }
            "IVA" -> expr += "+22%"
            "=" -> result()?.let { last = expr; expr = it.replace(".", "") }
            else -> expr += key
        }
        refresh()
    }

    private fun key(text: String, function: Boolean = false, onLong: (() -> Unit)? = null, onClick: () -> Unit) = TextView(context).apply {
        this.text = text
        gravity = Gravity.CENTER
        textSize = if (text.length > 3) 13.5f else 18f
        if (function || text.length > 1) typeface = Typeface.DEFAULT_BOLD
        setTextColor(p.text)
        background = rounded(if (function) p.faint else p.surface, context.dp(9).toFloat(), p.faint)
        isClickable = true
        contentDescription = if (text == "⌫") context.getString(R.string.eurise_calc_delete) else text
        setOnClickListener { onClick() }
        if (onLong != null) setOnLongClickListener { onLong(); true }
    }

    override fun view(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(6), context.dp(2), context.dp(6), context.dp(4))
        }
        val display = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            background = rounded(p.surface, context.dp(14).toFloat())
            setPadding(context.dp(14), context.dp(6), context.dp(14), context.dp(6))
        }
        exprView = context.label("0", p, 16f).apply { gravity = Gravity.END; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.START }
        resultView = context.label("", p, 24f, bold = true).apply { gravity = Gravity.END; maxLines = 1 }
        display.addView(exprView.lp())
        display.addView(resultView.lp())
        root.addView(display.lp())

        writeLabel = TextView(context)
        val rows = listOf(
            listOf("C", "( )", "%", "/", "⌫"),
            listOf("7", "8", "9", "*", "IVA"),
            listOf("4", "5", "6", "−", "="),
            listOf("1", "2", "3", "+", "ABC"),
            listOf(",", "0", "000", "WRITE"),
        )
        rows.forEach { row ->
            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEachIndexed { i, k ->
                val view = when (k) {
                    "ABC" -> key(context.getString(R.string.eurise_calc_keys), function = true) { bar.close() }
                    "WRITE" -> writeLabel.apply {
                        gravity = Gravity.CENTER
                        textSize = 14f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(p.onAccent)
                        background = rounded(p.accent, context.dp(9).toFloat(), p.faint)
                        isClickable = true
                        setOnClickListener { result()?.let { bar.insert(it); bar.close() } }
                        setOnLongClickListener {
                            result()?.let { bar.insert(expr.replace('*', '×').replace('/', '÷') + " = " + it); bar.close() }
                            true
                        }
                    }
                    else -> {
                        val shown = when (k) { "*" -> "×"; "/" -> "÷"; else -> k }
                        key(shown, function = k !in listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ",", "000")) { press(k) }
                    }
                }
                val weight = if (k == "WRITE") 2f else 1f
                line.addView(view, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply { if (i > 0) marginStart = context.dp(5) })
            }
            root.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = context.dp(5) })
        }
        root.addView(context.label(context.getString(R.string.eurise_calc_hint), p, 11f, muted = true).apply { gravity = Gravity.CENTER }.lp(top = context.dp(3)))
        refresh()
        return root
    }
}

/**
 * «Altro» (tavola TastRighe): gli strumenti della tastiera (appunti, emoji, una mano, annulla, impostazioni…) in una
 * griglia al posto dei tasti, invece che nella fila dei suggerimenti dove si sovrapponevano alla barra di Eurise.
 * Sono gli stessi scelti nelle impostazioni «Barra degli strumenti».
 */
class ToolsPanel(bar: EuriseBar) : Panel(bar, R.string.eurise_more) {
    override fun view(): View {
        val keys = helium314.keyboard.latin.utils.getEnabledToolbarKeys(context.prefs())
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        keys.chunked(5).forEach { row ->
            grid.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                row.forEach { key ->
                    val button = helium314.keyboard.latin.utils.createToolbarKey(context, key)
                    button.setColorFilter(p.text)
                    button.background = rounded(p.surface, context.dp(14).toFloat(), p.faint)
                    button.setOnClickListener { v ->
                        bar.close()
                        helium314.keyboard.latin.utils.onClickToolbarKey(v) { code ->
                            bar.ime.onCodeInput(code, helium314.keyboard.latin.common.Constants.SUGGESTION_STRIP_COORDINATE, helium314.keyboard.latin.common.Constants.SUGGESTION_STRIP_COORDINATE, false)
                        }
                    }
                    button.setOnLongClickListener { v ->
                        bar.close()
                        helium314.keyboard.latin.utils.onLongClickToolbarKey(v) { code, _ ->
                            bar.ime.onCodeInput(code, helium314.keyboard.latin.common.Constants.SUGGESTION_STRIP_COORDINATE, helium314.keyboard.latin.common.Constants.SUGGESTION_STRIP_COORDINATE, false)
                        }
                        true
                    }
                    addView(button, LinearLayout.LayoutParams(0, context.dp(52), 1f).apply { setMargins(context.dp(3), context.dp(3), context.dp(3), context.dp(3)) })
                }
                repeat(5 - row.size) { addView(View(context), LinearLayout.LayoutParams(0, context.dp(52), 1f)) }
            })
        }
        return column(header(context.getString(R.string.eurise_tools_title)), grid)
    }
}

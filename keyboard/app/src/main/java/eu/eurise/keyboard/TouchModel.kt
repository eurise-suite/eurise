package eu.eurise.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.InputPointers

/**
 * Il modello del tocco per tasto ([KeyModel]) dentro la tastiera: impara, salva e risponde a `KeyDetector` (scelta del
 * tasto) e a `ProximityInfo` (punti preferiti per il motore delle correzioni).
 *
 * Da dove impara, senza mai uscire dal telefono:
 * - parole scritte e tenute: ogni lettera è un esempio del suo tasto;
 * - parole corrette (proposta scelta o correzione automatica non annullata) della stessa lunghezza: il tocco di una
 *   lettera sbagliata è un esempio della lettera giusta, ed è proprio questo che insegna le «lettere accanto»;
 * - «lettera, cancella, lettera vicina»: il primo tocco voleva la seconda lettera.
 */
object TouchModel {
    private const val PREF = "key_model_1"
    /** Ogni tanti esempi nuovi si ricostruiscono le tastiere, perché il motore riceva i punti aggiornati. */
    private const val REFRESH_EVERY = 200

    val model = KeyModel()
    private var prefs: SharedPreferences? = null
    private var savedAt = 0
    private var refreshedAt = 0

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences("eurise_touch", Context.MODE_PRIVATE)
        prefs = p
        model.load(p.getString(PREF, null))
        savedAt = model.learned
        refreshedAt = model.learned
    }

    private fun box(key: Key) = KeyBox(Character.toLowerCase(key.code), key.x, key.y, key.width, key.height)

    private fun letterKey(keyboard: Keyboard, code: Int): Key? {
        val lower = Character.toLowerCase(code)
        return keyboard.getKey(code) ?: keyboard.getKey(lower) ?: keyboard.getKey(Character.toUpperCase(lower))
    }

    // --- Lettere precedenti (17 novembre: precisione alta fin dall'inizio) ---

    @Volatile private var charModel: CharModel? = null
    @Volatile private var charLocale: String? = null
    @Volatile private var lmOn = false
    private val word = StringBuilder()

    /** Le lettere precedenti contano solo scrivendo testo normale (non password, numeri, codici). */
    @JvmStatic
    fun lmActive() = lmOn && charModel != null

    /**
     * Inizio della scrittura in un campo (LatinIME.onStartInputView): si decide se usare le lettere precedenti, si
     * riprendono le ultime lettere prima del cursore e, la prima volta per una lingua, si prepara il modello delle lettere
     * dal dizionario principale (in un thread a parte; poi resta in un file e si apre subito).
     */
    @JvmStatic
    fun startInput(context: Context, editorInfo: android.view.inputmethod.EditorInfo?) {
        val type = editorInfo?.inputType ?: 0
        val cls = type and android.text.InputType.TYPE_MASK_CLASS
        val variation = type and android.text.InputType.TYPE_MASK_VARIATION
        val password = variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        lmOn = cls == android.text.InputType.TYPE_CLASS_TEXT && !password
        word.setLength(0)
        if (lmOn && android.os.Build.VERSION.SDK_INT >= 30) {
            editorInfo?.getInitialTextBeforeCursor(24, 0)?.toString()?.takeLastWhile { it.isLetter() }?.let { word.append(it.lowercase()) }
        }
        val locale = helium314.keyboard.latin.RichInputMethodManager.getInstance().currentSubtypeLocale
        val tag = locale.language
        if (charLocale == tag) return
        charLocale = tag
        charModel = null
        Thread {
            val file = java.io.File(context.filesDir, "charmodel_$tag.bin")
            val model = (if (file.exists()) file.inputStream().use { CharModel.read(it) } else null)
                ?: buildCharModel(context, locale)?.also { m -> runCatching { file.outputStream().use { m.write(it) } } }
            if (charLocale == tag) charModel = model
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    /** Il modello delle lettere dal dizionario principale della lingua (tutte le parole, pesate per frequenza). */
    private fun buildCharModel(context: Context, locale: java.util.Locale): CharModel? = runCatching {
        val (cached, assets) = helium314.keyboard.latin.dictionary.DictionaryFactory.getAvailableDictsForLocale(locale, context, false)
        val file = cached.firstOrNull { it.name.startsWith("main") }
            ?: assets.firstOrNull { it.startsWith("main") }?.let { helium314.keyboard.latin.utils.DictionaryInfoUtils.extractAssetsDictionary(it, locale, context) }
            ?: return null
        val dict = com.android.inputmethod.latin.BinaryDictionary(file.absolutePath, 0, file.length(), false, locale, "main", false)
        if (!dict.isValidDictionary) { dict.close(); return null }
        val builder = CharModel.Builder()
        var token = 0
        var count = 0
        do {
            val r = dict.getNextWordProperty(token)
            val wp = r.mWordProperty
            if (wp != null && !wp.mIsNotAWord && wp.mWord.isNotEmpty()) { builder.add(wp.mWord, 20f * CharModel.weightOf(wp.probability)); count++ }
            token = r.mNextToken
        } while (token != 0 && count < 2_000_000)
        dict.close()
        if (count < 100) null else builder.build()
    }.getOrNull()

    private fun lmFor(code: Int): Float? {
        val m = charModel ?: return null
        val c = when {
            code == ' '.code -> CharModel.BOUNDARY
            Character.isLetter(code) -> Character.toLowerCase(code).toChar()
            else -> return null
        }
        val n = word.length
        val p1 = if (n >= 1) word[n - 1] else CharModel.BOUNDARY
        val p2 = if (n >= 2) word[n - 2] else CharModel.BOUNDARY
        return m.logProb(p2, p1, c)
    }

    // --- Scelta del tasto (KeyDetector) ---

    /** Il tasto da scrivere per un tocco in ([rawX], [rawY]) (coordinate senza correzioni) finito su [hit]. */
    @JvmStatic
    fun choose(keyboard: Keyboard, hit: Key?, rawX: Int, rawY: Int): Key? {
        if (hit == null) return hit
        if (lmActive() && (Character.isLetter(hit.code) || hit.code == ' '.code) && keyboard.mId.element.isAlphabet) {
            // Tocco e lettere precedenti insieme: lettere vicine e spazio sono candidati.
            val near = keyboard.getNearestKeys(rawX, rawY).filter { (Character.isLetter(it.code) || it.code == ' '.code) && it !== hit }
            if (near.isEmpty()) return hit
            val code = model.chooseWith(box(hit), near.map(::box), rawX.toFloat(), rawY.toFloat(), ::lmFor)
            if (code == Character.toLowerCase(hit.code)) return hit
            return near.firstOrNull { Character.toLowerCase(it.code) == code } ?: hit
        }
        if (!Character.isLetter(hit.code) || !model.ready) return hit
        val near = keyboard.getNearestKeys(rawX, rawY).filter { Character.isLetter(it.code) && it !== hit }
        if (near.isEmpty()) return hit
        val code = model.choose(box(hit), near.map(::box), rawX.toFloat(), rawY.toFloat())
        if (code == Character.toLowerCase(hit.code)) return hit
        return near.firstOrNull { Character.toLowerCase(it.code) == code } ?: hit
    }

    // --- Punti preferiti (ProximityInfo) ---

    /** Punto preferito e raggio di un tasto per il motore, o null se il modello non è ancora pronto. */
    @JvmStatic
    fun sweetSpot(key: Key): FloatArray? {
        if (!model.ready || !Character.isLetter(key.code)) return null
        return model.sweetSpot(box(key))
    }

    @JvmStatic
    fun isReady() = model.ready

    // --- «lettera, cancella, lettera vicina» ---

    private class Tap(val code: Int, val x: Int, val y: Int, val at: Long)
    @Volatile private var lastTap: Tap? = null
    @Volatile private var deletedTap: Tap? = null

    /** Il tasto scelto per un tocco (KeyDetector), con le coordinate senza correzioni. */
    @JvmStatic
    fun touched(key: Key?, rawX: Int, rawY: Int) {
        lastTap = if (key != null && Character.isLetter(key.code)) Tap(Character.toLowerCase(key.code), rawX, rawY, SystemClock.uptimeMillis()) else null
    }

    /** Ogni tasto davvero scritto (LatinIME.onEvent). */
    @JvmStatic
    fun committed(code: Int) {
        when {
            code == KeyCode.DELETE -> if (word.isNotEmpty()) word.setLength(word.length - 1)
            Character.isLetter(code) -> { word.appendCodePoint(Character.toLowerCase(code)); if (word.length > 32) word.delete(0, word.length - 32) }
            else -> word.setLength(0)
        }
        val now = SystemClock.uptimeMillis()
        if (code == KeyCode.DELETE) {
            deletedTap = lastTap?.takeIf { now - it.at < 2_000 }
            lastTap = null
            return
        }
        val del = deletedTap ?: return
        deletedTap = null
        val lower = Character.toLowerCase(code)
        if (!Character.isLetter(code) || lower == del.code || now - del.at > 3_000) return
        val keyboard = KeyboardSwitcher.getInstance().keyboard ?: return
        val wanted = letterKey(keyboard, lower) ?: return
        val wrong = letterKey(keyboard, del.code) ?: return
        // Solo tasti accanto: chi cancella per cambiare idea non sta correggendo il dito.
        if (kotlin.math.abs(wanted.x + wanted.width / 2 - (wrong.x + wrong.width / 2)) > 1.6f * wanted.width ||
            kotlin.math.abs(wanted.y + wanted.height / 2 - (wrong.y + wrong.height / 2)) > 1.2f * wanted.height) return
        model.record(box(wanted), del.x.toFloat(), del.y.toFloat())
    }

    // --- Parole ---

    private class Word(val letters: List<Pair<KeyBox, Pair<Float, Float>>>)
    @Volatile private var pendingCorrection: Word? = null

    /**
     * Una parola lasciata nel testo (InputLogic.commitChosenWord). [autoCorrected]: la correzione automatica l'ha
     * cambiata e la persona può ancora annullarla con Cancella; si impara alla parola dopo.
     */
    @JvmStatic
    fun wordCommitted(typed: String?, chosen: String?, pointers: InputPointers?, batch: Boolean, autoCorrected: Boolean) {
        flushPending()
        word.setLength(0)
        if (batch || typed.isNullOrEmpty() || chosen.isNullOrEmpty() || pointers == null) return
        val t = codePoints(typed)
        val c = codePoints(chosen)
        if (t.size != c.size || t.size > pointers.pointerSize) return
        // Al più due lettere diverse: oltre, è un'altra parola e non un dito scivolato.
        if (t.indices.count { Character.toLowerCase(t[it]) != Character.toLowerCase(c[it]) } > 2) return
        val keyboard = KeyboardSwitcher.getInstance().keyboard ?: return
        val xs = pointers.xCoordinates
        val ys = pointers.yCoordinates
        val letters = t.indices.mapNotNull { i ->
            if (!Character.isLetter(c[i]) || xs[i] < 0 || ys[i] < 0) return@mapNotNull null
            val key = letterKey(keyboard, c[i]) ?: return@mapNotNull null
            box(key) to (xs[i].toFloat() to ys[i].toFloat())
        }
        val word = Word(letters)
        if (autoCorrected) pendingCorrection = word else learn(word)
    }

    private fun codePoints(s: String): IntArray {
        val out = IntArray(s.codePointCount(0, s.length))
        var i = 0
        var k = 0
        while (i < s.length) { val cp = s.codePointAt(i); out[k++] = cp; i += Character.charCount(cp) }
        return out
    }

    /** La persona ha annullato la correzione automatica: quella parola non insegna nulla. */
    @JvmStatic
    fun reverted() { pendingCorrection = null }

    private fun flushPending() {
        pendingCorrection?.let(::learn)
        pendingCorrection = null
    }

    private fun learn(word: Word) {
        for ((box, p) in word.letters) model.record(box, p.first, p.second)
        if (model.learned - savedAt >= 50) save()
    }

    // --- Salvataggio ---

    /** Fine della scrittura (LatinIME.onFinishInputView): salva e, ogni tanto, fa ricostruire le tastiere. */
    @JvmStatic
    fun finishInput() {
        flushPending()
        save()
        if (model.ready && model.learned - refreshedAt >= REFRESH_EVERY) {
            refreshedAt = model.learned
            KeyboardLayoutSet.onKeyboardThemeChanged()
        }
    }

    fun save() {
        savedAt = model.learned
        prefs?.edit()?.putString(PREF, model.save())?.apply()
    }

    /** «Ricomincia»: dimentica come tocca la persona. */
    fun reset() { model.clear(); pendingCorrection = null; save(); KeyboardLayoutSet.onKeyboardThemeChanged() }
}

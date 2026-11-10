package eu.eurise.keyboard

import android.content.Context
import android.util.Log
import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.dictionary.DictionaryFactory
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.SuggestionResults
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Collega il riconoscitore libero di Eurise ([GlideDecoder]) alla tastiera: prepara l'elenco delle parole dal dizionario
 * principale della lingua (una volta, in sottofondo, poi da una copia in cache) e trasforma una traccia in proposte.
 */
object EuriseGlide {
    private const val TAG = "EuriseGlide"
    private val decoder = GlideDecoder()
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var readyFor: Locale? = null
    @Volatile private var loadingFor: Locale? = null
    @Volatile private var app: Context? = null

    /** Prepara le parole per [locale] se non sono già pronte; non blocca. */
    fun prepare(context: Context, locale: Locale) {
        val app = context.applicationContext
        this.app = app
        if (readyFor == locale || loadingFor == locale) return
        loadingFor = locale
        worker.execute {
            runCatching {
                val words = load(app, locale)
                decoder.setLexicon(words.asSequence())
                readyFor = locale
                Log.i(TAG, "parole pronte per $locale: ${decoder.size}")
            }.onFailure { Log.w(TAG, "parole non caricate per $locale", it) }
            if (loadingFor == locale) loadingFor = null
        }
    }

    /** Proposte per una traccia; vuote se le parole non sono ancora pronte (la prima volta in una lingua). */
    fun suggest(data: ComposedData, keyboard: Keyboard, locale: Locale): SuggestionResults {
        val results = SuggestionResults(18, false, false)
        if (readyFor != locale) { app?.let { prepare(it, locale) }; return results }
        val keys = HashMap<Char, Pair<Float, Float>>()
        var widthSum = 0f
        for (key in keyboard.sortedKeys) {
            val code = key.code
            if (code <= 0 || !Character.isLetter(code)) continue
            val c = Character.toLowerCase(code.toChar())
            if (c !in 'a'..'z' || c in keys) continue
            keys[c] = (key.x + key.width / 2f) to (key.y + key.height / 2f)
            widthSum += key.width
        }
        if (keys.size < 20) return results
        val keyWidth = widthSum / keys.size
        val pointers = data.mInputPointers
        val candidates = decoder.decode(keys, keyWidth, pointers.xCoordinates, pointers.yCoordinates, pointers.pointerSize)
        candidates.forEachIndexed { i, c ->
            val score = (1_000_000 - (c.cost * 10_000).toInt() - i).coerceAtLeast(1)
            results.add(SuggestedWordInfo(c.word, "", score, SuggestedWordInfo.KIND_CORRECTION, Dictionary.DICTIONARY_HARDCODED,
                SuggestedWordInfo.NOT_AN_INDEX, SuggestedWordInfo.NOT_A_CONFIDENCE))
        }
        return results
    }

    /** Parole e frequenze del dizionario principale; la prima volta dal dizionario, poi da una copia in cache. */
    private fun load(context: Context, locale: Locale): List<Pair<String, Int>> {
        val (extracted, assets) = DictionaryFactory.getAvailableDictsForLocale(locale, context, false)
        val file = extracted.firstOrNull { it.name.startsWith(Dictionary.TYPE_MAIN) }
            ?: assets.firstOrNull { it.startsWith(Dictionary.TYPE_MAIN) }?.let { DictionaryInfoUtils.extractAssetsDictionary(it, locale, context) }
            ?: return emptyList()
        val cache = File(context.cacheDir, "eurise_glide_${locale.toLanguageTag()}_${file.length()}_${file.lastModified()}.txt")
        if (cache.isFile) {
            return cache.readLines().mapNotNull { line -> line.split('\t').takeIf { it.size == 2 }?.let { it[0] to (it[1].toIntOrNull() ?: 0) } }
        }
        val words = ArrayList<Pair<String, Int>>()
        val dict = BinaryDictionary(file.absolutePath, 0, file.length(), false, locale, Dictionary.TYPE_MAIN, false)
        try {
            if (!dict.isValidDictionary) return emptyList()
            var token = 0
            do {
                val r = dict.getNextWordProperty(token)
                val w = r.mWordProperty ?: break
                if (!w.mIsNotAWord && !w.mIsPossiblyOffensive && !w.mIsBeginningOfSentence && w.mWord.length in 2..24)
                    words += w.mWord to w.mProbabilityInfo.mProbability
                token = r.mNextToken
            } while (token != 0)
        } finally {
            dict.close()
        }
        context.cacheDir.listFiles { f -> f.name.startsWith("eurise_glide_${locale.toLanguageTag()}_") }?.forEach { it.delete() }
        runCatching { cache.writeText(words.joinToString("\n") { "${it.first}\t${it.second}" }) }
        return words
    }
}

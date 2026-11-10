package eu.eurise.core.data.docs

import android.content.Context

/**
 * Interruttori della lettura dei documenti (analisi dei contenuti, quindi spenta di base e accesa solo dopo la
 * spiegazione). [auto]: legge ogni scansione appena fatta; altrimenti solo con «Leggi il testo».
 */
object DocReading {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("doc_reading", Context.MODE_PRIVATE)

    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun auto(context: Context) = prefs(context).getBoolean("auto", true)

    fun set(context: Context, enabled: Boolean, auto: Boolean = auto(context)) {
        prefs(context).edit().putBoolean("enabled", enabled).putBoolean("auto", auto).apply()
    }
}

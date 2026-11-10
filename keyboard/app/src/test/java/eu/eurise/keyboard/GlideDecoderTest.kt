package eu.eurise.keyboard

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random

/**
 * Prova del riconoscitore su tracce simulate: per ogni parola si disegna il percorso tra i centri dei tasti con
 * un po' di imprecisione (il dito non passa mai esattamente al centro e taglia le curve). La percentuale finisce
 * nel riepilogo della compilazione (build/glide-report.txt).
 */
class GlideDecoderTest {

    private val keyWidth = 100f

    /** Tastiera QWERTY: tre file di lettere, come quella vera (seconda fila spostata di mezzo tasto, terza di uno e mezzo). */
    private val keys: Map<Char, Pair<Float, Float>> = buildMap {
        listOf("qwertyuiop" to 0f, "asdfghjkl" to 0.5f, "zxcvbnm" to 1.5f).forEachIndexed { row, (letters, shift) ->
            letters.forEachIndexed { i, c -> put(c, (shift + i + 0.5f) * keyWidth to (row + 0.5f) * keyWidth * 1.4f) }
        }
    }

    private fun trace(word: String, random: Random, noise: Float): Pair<IntArray, IntArray> {
        val letters = GlideDecoder.baseLetters(word)
        val pts = ArrayList<Pair<Float, Float>>()
        var prev: Char? = null
        for (c in letters) {
            if (c == prev) continue
            val (x, y) = keys.getValue(c)
            pts += (x + random.nextGaussian().toFloat() * noise * keyWidth) to (y + random.nextGaussian().toFloat() * noise * keyWidth)
            prev = c
        }
        if (pts.size == 1) pts += pts[0].first + 3f to pts[0].second
        // punti fitti lungo il percorso, con le curve un po' tagliate (media mobile)
        val dense = ArrayList<Pair<Float, Float>>()
        for (i in 1 until pts.size) {
            val (ax, ay) = pts[i - 1]; val (bx, by) = pts[i]
            val steps = 12
            for (s in 0 until steps) dense += (ax + (bx - ax) * s / steps) to (ay + (by - ay) * s / steps)
        }
        dense += pts.last()
        val smooth = dense.indices.map { i ->
            val a = maxOf(0, i - 3); val b = minOf(dense.lastIndex, i + 3)
            if (i == 0 || i == dense.lastIndex) dense[i]
            else (a..b).map { dense[it] }.let { w -> w.map { it.first }.average().toFloat() to w.map { it.second }.average().toFloat() }
        }
        return IntArray(smooth.size) { smooth[it].first.toInt() } to IntArray(smooth.size) { smooth[it].second.toInt() }
    }

    private fun measure(noise: Float, seed: Long): Triple<Int, Int, Int> {
        val decoder = GlideDecoder()
        val words = ITALIAN.split(Regex("\\s+")).filter { it.isNotBlank() }
        decoder.setLexicon(words.asSequence().mapIndexed { rank, w -> w to (255 - rank * 255 / words.size).coerceAtLeast(1) })
        val random = Random(seed)
        var top1 = 0; var top3 = 0; var total = 0
        words.filter { GlideDecoder.baseLetters(it).length >= 2 }.forEach { w ->
            val (xs, ys) = trace(w, random, noise)
            val result = decoder.decode(keys, keyWidth, xs, ys, xs.size).map { it.word }
            total++
            if (result.firstOrNull() == w) top1++
            if (w in result.take(3)) top3++
        }
        return Triple(top1, top3, total)
    }

    @Test fun recognizesItalianWords() {
        val clean = measure(0.12f, 1)
        val sloppy = measure(0.25f, 2)
        val report = buildString {
            appendLine("### Scrittura scorrendo (riconoscitore libero di Eurise)")
            appendLine("Tracce simulate su ${clean.third} parole italiane comuni (vocabolario piccolo: con il dizionario intero la percentuale scende, va provato sul telefono).")
            appendLine("- Dito preciso: prima proposta giusta ${pct(clean.first, clean.third)}, tra le prime tre ${pct(clean.second, clean.third)}")
            appendLine("- Dito impreciso: prima proposta giusta ${pct(sloppy.first, sloppy.third)}, tra le prime tre ${pct(sloppy.second, sloppy.third)}")
        }
        runCatching { File("build").mkdirs(); File("build/glide-report.txt").writeText(report) }
        println(report)
        assertTrue("prima proposta giusta troppo bassa: $report", clean.first * 100 >= clean.third * 85)
        assertTrue("tra le prime tre troppo bassa: $report", sloppy.second * 100 >= sloppy.third * 85)
    }

    private fun pct(a: Int, b: Int) = "${a * 100 / b}%"

    companion object {
        /** Parole comuni dell'italiano, dalla più frequente. */
        const val ITALIAN = """
            di che non la il per una con sono ma come anche più questo mi lo se ci io della quando cosa fatto
            tutto stato essere fare molto ho hai ha abbiamo avete hanno era sei siamo siete grazie ciao bene
            casa tempo vita anno giorno volta uomo donna parte mondo lavoro paese caso modo grande nuovo primo
            ultimo stesso altro tanto poco ora dopo prima sempre mai già ancora oggi domani ieri sera notte
            mattina pomeriggio settimana mese ragazzo ragazza amico amica famiglia madre padre figlio figlia
            fratello sorella marito moglie città strada scuola acqua cibo pane vino caffè latte pasta pizza
            libro lettera parola nome numero ora minuto momento storia problema domanda risposta idea punto
            mano occhio testa cuore piede voce porta finestra tavolo sedia letto camera cucina bagno giardino
            macchina treno aereo bicicletta telefono computer lavorare parlare mangiare bere dormire andare
            venire vedere sentire sapere volere potere dovere dire dare stare trovare pensare credere chiamare
            prendere mettere tenere portare lasciare aspettare cercare aprire chiudere scrivere leggere
            giocare correre camminare guidare comprare vendere pagare costare aiutare capire ricordare
            dimenticare imparare insegnare studiare finire cominciare continuare tornare uscire entrare
            salire scendere restare vivere morire nascere amare piacere servire succedere sembrare diventare
            bello brutto buono cattivo piccolo lungo corto alto basso facile difficile felice triste caldo
            freddo vecchio giovane ricco povero forte veloce lento pieno vuoto aperto chiuso vero falso
            pronto stanco contento sicuro possibile importante necessario libero occupato vicino lontano
            sopra sotto dentro fuori davanti dietro insieme subito presto tardi spesso qualche ogni nessuno
            qualcuno niente qualcosa tutti tutte dove perché come quanto quale chi quindi però invece allora
            appena forse proprio davvero certo esatto ecco basta dai scusa prego salve buongiorno buonasera
            arrivederci auguri complimenti appuntamento riunione progetto ufficio cliente prezzo conto banca
            soldi euro regalo festa cena pranzo colazione vacanza viaggio albergo stazione aeroporto mare
            montagna lago fiume sole pioggia neve vento cielo luna stelle fiore albero erba gatto cane
            cavallo uccello pesce carne verdura frutta mela arancia limone zucchero sale olio formaggio
            uovo riso dolce torta gelato birra tè bottiglia bicchiere piatto forchetta coltello cucchiaio
            musica film canzone foto video messaggio notizia giornale lettera pacco consegna documento
            contratto firma preventivo fattura bolletta medico ospedale farmacia salute febbre dottore
            nonno nonna zio zia cugino bambino bambina persona gente squadra partita calcio sport palestra
            lunedì martedì mercoledì giovedì venerdì sabato domenica gennaio febbraio marzo aprile maggio
            giugno luglio agosto settembre ottobre novembre dicembre primavera estate autunno inverno
        """
    }
}

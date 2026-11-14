package eu.eurise.core.data.alfred

import java.time.LocalDate

/*
 * Il catalogo di Alfred sta qui (e non in feature/assistant) perché lo usano due app che non si conoscono: il punto della
 * giornata (Alfred) e la sveglia di Alfred (Orologio).
 */

/** Un brano del catalogo di Alfred: titolo, autori, anno, genere e il suo commento (verificato, scritto da noi). */
data class Pearl(val artist: String, val title: String, val year: String, val authors: String, val genre: String, val comment: String, val evening: Boolean = false) {
    fun spoken(): String = "$artist, «$title» ($year)${if (authors.isNotBlank()) ", $authors" else ""}. $comment"
    /** La ricerca da passare all'app di musica scelta. */
    val query: String get() = "$artist $title"
}

/**
 * Il catalogo di Alfred, sul telefono: brani di generi diversi (italiani, internazionali, classica), opere, luoghi,
 * curiosità e massime. Ogni voce è controllata; il commento è suo. Si sceglie con il giorno dell'anno, così ogni
 * giorno cambia e lo stesso giorno resta uguale.
 */
object AlfredCatalog {
    val pearls = listOf(
        Pearl("Lucio Battisti", "Emozioni", "1970", "testo di Mogol", "canzone d'autore",
            "Un pezzo che si ascolta meglio a occhi chiusi, e con il volume di chi non deve scusarsi con i vicini."),
        Pearl("Mina", "Se telefonando", "1966", "musica di Ennio Morricone", "pop",
            "Tre minuti scarsi e un arrangiamento che non invecchia. Le telefonate di oggi, temo, non sono all'altezza."),
        Pearl("Domenico Modugno", "Nel blu, dipinto di blu", "1958", "con Franco Migliacci", "canzone",
            "La canzone che ha insegnato al mondo a dire «volare». Io mi limito a consigliarla."),
        Pearl("Fabrizio De André", "Bocca di rosa", "1967", "", "canzone d'autore",
            "Una storia di paese raccontata con una malizia gentile. De André non alzava mai la voce, e si sentiva lo stesso."),
        Pearl("Franco Battiato", "Centro di gravità permanente", "1981", "", "pop d'autore",
            "Si balla e si pensa nello stesso momento: un'abilità che raccomando, possibilmente non in ufficio."),
        Pearl("Lucio Dalla", "4/3/1943", "1971", "testo di Paola Pallottino", "canzone d'autore",
            "Una data come titolo e dentro una vita intera. Dalla aveva il dono di sembrare semplice."),
        Pearl("Adriano Celentano", "Azzurro", "1968", "di Paolo Conte e Vito Pallavicini", "canzone",
            "Un'estate che dura da più di mezzo secolo. Paolo Conte l'ha scritta; l'Italia l'ha adottata."),
        Pearl("Nina Simone", "Feeling Good", "1965", "di Anthony Newley e Leslie Bricusse", "soul",
            "Comincia senza orchestra, solo una voce che decide come andrà la giornata. Una lezione di stile."),
        Pearl("The Beatles", "Here Comes the Sun", "1969", "di George Harrison", "rock",
            "Harrison la scrisse in un giardino, sfuggendo a una riunione d'affari. Un precedente che trovo incoraggiante."),
        Pearl("Bill Withers", "Lovely Day", "1977", "con Skip Scarborough", "soul",
            "C'è una nota tenuta lunghissima a metà brano: la prenda come un invito a non avere fretta."),
        Pearl("Queen", "Bohemian Rhapsody", "1975", "di Freddie Mercury", "rock",
            "Sei minuti, nessun ritornello e un'opera lirica nel mezzo. All'epoca dissero che non sarebbe mai passata in radio."),
        Pearl("Miles Davis", "So What", "1959", "dall'album Kind of Blue", "jazz", evening = true,
            comment = "Il disco di jazz più venduto di sempre, suonato quasi senza prove. L'eleganza, a volte, è non insistere."),
        Pearl("Johann Sebastian Bach", "Suite per violoncello n. 1, Preludio", "1720 circa", "BWV 1007", "classica",
            "Un solo strumento e nessuna fretta. Pablo Casals ne trovò la partitura da ragazzo in una bottega di Barcellona, e il mondo gliene è ancora grato."),
        Pearl("Antonio Vivaldi", "La primavera", "1725", "dalle Quattro stagioni", "classica",
            "Uccelli, ruscelli e un temporale, tutto scritto in partitura. Vivaldi annotò perfino i versi sul pentagramma."),
        Pearl("Gioachino Rossini", "Il barbiere di Siviglia, sinfonia", "1816", "", "classica",
            "A Rossini si attribuisce il vanto di poter mettere in musica anche la lista del bucato. Qui, per fortuna, ha scelto altro."),
        Pearl("Claude Debussy", "Clair de lune", "1905", "dalla Suite bergamasque", "classica", evening = true,
            comment = "Musica che sa di finestra aperta sulla notte. La consiglio a volume basso e senza telefono in mano."),
        Pearl("Erik Satie", "Gymnopédie n. 1", "1888", "", "classica", evening = true,
            comment = "Lenta, quasi immobile, e per questo indimenticabile. Satie la indicò da suonare «lento e doloroso»: io direi lento e basta."),
    )

    private val art = listOf(
        "Caravaggio, «Vocazione di san Matteo» (1599–1600), a San Luigi dei Francesi, a Roma. Segua la luce: indica chi viene chiamato.",
        "Sandro Botticelli, «Nascita di Venere» (1485 circa), agli Uffizi di Firenze. Venere arriva su una conchiglia: un ingresso che nessun ricevimento ha più eguagliato.",
        "Leonardo da Vinci, «L'Ultima Cena» (1494–1498), in Santa Maria delle Grazie a Milano. Leonardo dipinse su muro asciutto, non a fresco: per questo è così fragile.",
        "Giotto, la Cappella degli Scrovegni (1303–1305 circa), a Padova. Il cielo stellato della volta è di un blu che vale da solo il viaggio.",
        "Raffaello, «La scuola di Atene» (1509–1511), nelle Stanze Vaticane. Tra i filosofi, si dice, ha messo i volti dei colleghi: Platone avrebbe quello di Leonardo.",
        "Gian Lorenzo Bernini, «Apollo e Dafne» (1622–1625), alla Galleria Borghese di Roma. Il marmo diventa foglie sotto le dita: guardi le mani di Dafne.",
        "Antonio Canova, «Amore e Psiche» (1787–1793), al Louvre di Parigi. Un abbraccio sospeso un istante prima del bacio; Canova sapeva aspettare.",
        "Johannes Vermeer, «Ragazza con l'orecchino di perla» (1665 circa), al Mauritshuis dell'Aia. Non è un ritratto di qualcuno: è un volto inventato, e per questo di tutti.",
        "Katsushika Hokusai, «La grande onda di Kanagawa» (1831 circa). Sullo sfondo, piccolissimo, il monte Fuji: il protagonista che si fa da parte.",
        "Piero della Francesca, «Flagellazione di Cristo» (metà del Quattrocento), alla Galleria Nazionale delle Marche di Urbino. Una prospettiva così esatta che gli studiosi ne discutono ancora.",
    )

    private val places = listOf(
        "Matera, i Sassi: case e chiese scavate nella roccia, patrimonio dell'UNESCO dal 1993. Al tramonto la città sembra accendersi da sola.",
        "Civita di Bagnoregio, nel Lazio: un borgo su uno sperone di tufo, raggiunto solo da un ponte pedonale. Ci si arriva a piedi, e ne vale la pena.",
        "Castel del Monte, in Puglia: il castello ottagonale voluto da Federico II nel Duecento. Otto lati, otto torri, e ancora nessuno sa dire con certezza a cosa servisse.",
        "La Val d'Orcia, in Toscana: colline, cipressi e poderi, patrimonio dell'UNESCO dal 2004. Il paesaggio che i pittori del Rinascimento mettevano sullo sfondo.",
        "Alberobello e i suoi trulli, in Puglia: case di pietra a secco con il tetto a cono, patrimonio dell'UNESCO dal 1996.",
        "Ravenna e i suoi mosaici paleocristiani e bizantini, patrimonio dell'UNESCO dal 1996. Entri in San Vitale e alzi lo sguardo: l'oro fa il resto.",
        "Procida, nel golfo di Napoli: case color pastello sul porto della Corricella. Capitale italiana della cultura nel 2022, e con ragione.",
        "Le Dolomiti, patrimonio dell'UNESCO dal 2009. All'alba e al tramonto le rocce si tingono di rosa: lo chiamano enrosadira.",
    )

    private val curiosities = listOf(
        "«Ciao» viene dal veneziano «s'ciavo», cioè «schiavo vostro»: una formula di cortesia. Da maggiordomo, non posso che approvare.",
        "La parola «quarantena» viene dai quaranta giorni di isolamento chiesti alle navi nei porti adriatici tra Trecento e Quattrocento.",
        "La Torre di Pisa cominciò a pendere già durante la costruzione, iniziata nel 1173: il terreno era troppo morbido. Un difetto diventato vanto.",
        "Per la botanica la banana è una bacca, la fragola no. Le classificazioni, come certe etichette a tavola, hanno logiche tutte loro.",
        "Il ponte di Rialto, a Venezia, fu finito in pietra nel 1591; prima ce n'erano di legno, e uno crollò sotto la folla che guardava un corteo.",
        "Secondo la tradizione, la pizza Margherita fu dedicata alla regina Margherita a Napoli nel 1889, con i colori della bandiera. Gli storici discutono; i napoletani no.",
        "Il Colosseo poteva ospitare decine di migliaia di spettatori e aveva un velario, una grande tenda che faceva ombra sulle gradinate.",
        "Una delle spiegazioni del nome «espresso»: il caffè preparato al momento, espressamente per chi lo chiede. Un principio che condivido.",
    )

    private val morning = listOf(
        "«Chi ben comincia è a metà dell'opera. Lei, con un caffè, è già oltre.»",
        "«Il mattino ha l'oro in bocca. Io, per prudenza, controllerei anche le tasche.»",
        "«Non rimandi a domani ciò che può affidare a me oggi.»",
        "«La puntualità è la cortesia dei re. Ai maggiordomi tocca ricordarla.»",
        "«L'ottimismo, a mio avviso, è una forma di cortesia verso il futuro.»",
        "«Chi va piano va sano e va lontano. Chi va di corsa arriva prima, ma a lamentarsi.»",
        "«Il tempo è denaro, dicono. Mi permetta di farle risparmiare qualche minuto.»",
        "«Dicono che il mondo sia di chi si alza presto. Il resto, mi risulta, è di chi si organizza.»",
    )

    private val night = listOf(
        "«La giornata è finita. Le ore, almeno, hanno fatto il loro dovere.»",
        "«Il riposo non è tempo perso: è tempo che si fa i fatti suoi.»",
        "«Domani è un altro giorno. Con lo stesso calendario, temo.»",
        "«La notte porta consiglio. Io, nel frattempo, porto la sveglia.»",
        "«Chi dorme non piglia pesci. Ma nemmeno rimproveri.»",
    )

    private fun <T> pick(list: List<T>, day: LocalDate, salt: Int = 0): T = list[Math.floorMod(day.dayOfYear * 7 + day.year + salt, list.size)]

    fun pearl(day: LocalDate, evening: Boolean): Pearl {
        val pool = if (evening) pearls.filter { it.evening || it.genre == "classica" || it.genre == "jazz" } else pearls.filter { !it.evening }
        return pick(pool, day, 1)
    }

    fun art(day: LocalDate): String = pick(art, day, 2)
    fun place(day: LocalDate): String = pick(places, day, 3)
    fun curiosity(day: LocalDate): String = pick(curiosities, day, 4)
    fun motto(day: LocalDate, evening: Boolean): String = pick(if (evening) night else morning, day, 5)
}

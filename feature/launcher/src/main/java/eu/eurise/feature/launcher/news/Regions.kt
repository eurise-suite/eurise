package eu.eurise.feature.launcher.news

/**
 * Regioni italiane con il feed regionale dell'ANSA e le loro province. Le province non hanno un feed
 * comune a tutte: si segue il feed della regione e si portano in cima le notizie che nominano la provincia
 * o il suo capoluogo. Per un giornale della propria provincia si può aggiungere il suo feed a mano.
 */
data class Region(val id: String, val name: String, val ansaSlug: String, val provinces: List<String>) {
    val feed: Feed get() = Feed("region:$id", "ANSA · $name", "https://www.ansa.it/$ansaSlug/notizie/${ansaSlug}_rss.xml", FeedGroup.Local)
}

val Regions = listOf(
    Region("abruzzo", "Abruzzo", "abruzzo", listOf("Chieti", "L'Aquila", "Pescara", "Teramo")),
    Region("basilicata", "Basilicata", "basilicata", listOf("Matera", "Potenza")),
    Region("calabria", "Calabria", "calabria", listOf("Catanzaro", "Cosenza", "Crotone", "Reggio Calabria", "Vibo Valentia")),
    Region("campania", "Campania", "campania", listOf("Avellino", "Benevento", "Caserta", "Napoli", "Salerno")),
    Region("emiliaromagna", "Emilia-Romagna", "emiliaromagna",
        listOf("Bologna", "Ferrara", "Forlì-Cesena", "Modena", "Parma", "Piacenza", "Ravenna", "Reggio Emilia", "Rimini")),
    Region("friuliveneziagiulia", "Friuli Venezia Giulia", "friuliveneziagiulia", listOf("Gorizia", "Pordenone", "Trieste", "Udine")),
    Region("lazio", "Lazio", "lazio", listOf("Frosinone", "Latina", "Rieti", "Roma", "Viterbo")),
    Region("liguria", "Liguria", "liguria", listOf("Genova", "Imperia", "La Spezia", "Savona")),
    Region("lombardia", "Lombardia", "lombardia",
        listOf("Bergamo", "Brescia", "Como", "Cremona", "Lecco", "Lodi", "Mantova", "Milano", "Monza e Brianza", "Pavia", "Sondrio", "Varese")),
    Region("marche", "Marche", "marche", listOf("Ancona", "Ascoli Piceno", "Fermo", "Macerata", "Pesaro e Urbino")),
    Region("molise", "Molise", "molise", listOf("Campobasso", "Isernia")),
    Region("piemonte", "Piemonte", "piemonte",
        listOf("Alessandria", "Asti", "Biella", "Cuneo", "Novara", "Torino", "Verbano-Cusio-Ossola", "Vercelli")),
    Region("puglia", "Puglia", "puglia", listOf("Bari", "Barletta-Andria-Trani", "Brindisi", "Foggia", "Lecce", "Taranto")),
    Region("sardegna", "Sardegna", "sardegna", listOf("Cagliari", "Nuoro", "Oristano", "Sassari", "Sud Sardegna")),
    Region("sicilia", "Sicilia", "sicilia",
        listOf("Agrigento", "Caltanissetta", "Catania", "Enna", "Messina", "Palermo", "Ragusa", "Siracusa", "Trapani")),
    Region("toscana", "Toscana", "toscana",
        listOf("Arezzo", "Firenze", "Grosseto", "Livorno", "Lucca", "Massa-Carrara", "Pisa", "Pistoia", "Prato", "Siena")),
    Region("trentino", "Trentino-Alto Adige", "trentino", listOf("Bolzano", "Trento")),
    Region("umbria", "Umbria", "umbria", listOf("Perugia", "Terni")),
    Region("valledaosta", "Valle d'Aosta", "valledaosta", listOf("Aosta")),
    Region("veneto", "Veneto", "veneto", listOf("Belluno", "Padova", "Rovigo", "Treviso", "Venezia", "Verona", "Vicenza")),
)

/** Parole con cui riconoscere una provincia nei titoli: "Forlì-Cesena" vale per Forlì e per Cesena. */
fun provinceKeywords(province: String): List<String> = when (province) {
    "Monza e Brianza" -> listOf("Monza", "Brianza")
    "Pesaro e Urbino" -> listOf("Pesaro", "Urbino")
    "Barletta-Andria-Trani" -> listOf("Barletta", "Andria", "Trani")
    "Verbano-Cusio-Ossola" -> listOf("Verbania", "Ossola", "Verbano")
    "Massa-Carrara" -> listOf("Massa", "Carrara")
    "Forlì-Cesena" -> listOf("Forlì", "Cesena")
    "Sud Sardegna" -> listOf("Sud Sardegna", "Carbonia", "Iglesias")
    else -> listOf(province)
}

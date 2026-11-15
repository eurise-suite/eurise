# Eurise Mappe – base CoMaps

Base approvata dal proprietario l'11 novembre 2026: **CoMaps** (Apache 2.0, fork comunitario di Organic Maps, su F-Droid).
Non Organic Maps: dall'agosto 2025 i suoi dati delle mappe hanno una licenza non libera.

## Come si compila (lavoro «Prototipo di Eurise Mappe», `.github/workflows/maps-engine.yml`)
- Sorgenti da `https://codeberg.org/comaps/comaps.git` al commit fissato `COMAPS_COMMIT` (11 novembre 2026), sottomoduli
  senza storia. I sorgenti non stanno nel nostro repository: sono grandi (C++ con boost, ICU, protobuf…).
- `maps/eurise_patch.py` cambia solo nome («Eurise Mappe»), pacchetto (`eu.eurise.maps`, senza suffissi) e firma (chiave
  di debug della suite, così il permesso di firma vale con l'APK principale). Il codice Java resta in `app.organicmaps`.
- `./configure.sh` (stili, simboli, testi, mappe del mondo) e `./gradlew -Parm64 assembleFdroidRelease` (release ridotta con R8 e librerie senza simboli; senza i file segreti di CoMaps la firma è la nostra chiave di debug), con ccache salvata
  nella cache di Actions. Java 21 come chiede CoMaps.
- Artefatto `eurise-mappe-debug-N`: APK, misure (tempo per fase, peso per parte dell'APK), registro di Gradle.

## Peso (giro 5, 11 novembre)
Giro 3: APK arm64 di 57 MB (simboli 11,7, mappe del mondo e file 11,5, caratteri 10,1, codice e risorse 10,1, nomi dei
paesi 6,7, libreria 6,0). `maps/eurise_slim.py`, dopo `configure.sh`, esclude dall'APK (con `ignoreAssetsPattern`; i file restano su disco perché
Gradle rigenera i simboli che mancano): simboli mdpi, hdpi e xxxhdpi (restano
xhdpi, 6plus, xxhdpi; `eurise_patch.py` aggiorna la tabella di `VisualParams`), i caratteri delle scritture rare (CJK,
tibetano, birmano, khmer, code2000: il motore usa i Noto del sistema e salta i file che mancano), i nomi dei paesi nelle
lingue non europee (per quelle il motore usa l'inglese). Stima: circa 17 MB in meno. Dopo: caratteri rari scaricabili a
richiesta nella cartella dei dati dell'app (il motore li cerca anche lì), se servono.

## Stile della suite (giro 8, 12 novembre)
`maps/eurise_style.py` (chiamato da `eurise_patch.py`) riscrive i valori dei colori di CoMaps: tema Material 3 e colori
di base delle schermate a View diventano Perla (chiaro, accento blu notte) e Notte (scuro, accento azzurro); 100 colori,
provati sui sorgenti del commit fissato. Icona della suite (`docs/brand/android/maps`) al posto di quella di CoMaps.

## Guida (giro 9, 12 novembre)
`maps/android/.../eurise/EuriseGuide.java`, agganciato da `eurise_patch.py` a `NavigationController` e `NavMenu`:
- il cartello della svolta e la striscia della via prendono il colore del tipo di strada dove si gira (riferimento OSM
  `A…`/`RA…` = autostrada, Salvia; `SS`/`SR`/`E…` = statale, Blu notte; `SP`/`SC` = provinciale, Petrolio; senza
  riferimento = comunale, blu della suite; se manca il riferimento decide il colore del cartello di OSM);
- i metri alla svolta scendono a passi di 10 m sotto i 200 m, 50 m fino a 500, 100 m fino al chilometro (si aggiornano
  a ogni posizione);
- «Manda l'arrivo» nel menu della guida (ora d'arrivo, minuti e distanza, con l'app che scegli).
Velocità attuale e limite c'erano già in CoMaps (si accendono in Impostazioni).

## Passi seguenti (dalle tavole approvate: MapGuida, MapEsplora, MapAttivita, MapLivelli, MapPercorso, MapCerca)
1. Prova del prototipo sul telefono così com'è (CoMaps con il nostro nome).
2. Stile della suite: tema Perla/Notte, barra satinata, icone EuriseIcons; schermate nuove in Compose dove si può
   (CoMaps usa le View di Android: si parte da ricerca, livelli e scheda del luogo).
3. Guida: velocità attuale e limite (OSM), frase d'arrivo, meteo sul percorso da `core/data/weather`.
   Richiesta del proprietario (11 novembre): il cartello dell'indicazione prende il colore del tipo di strada, con le tinte
   della suite (autostrada = verde della suite, statale/extraurbana principale = blu della suite, provinciale e comunale
   con tinte proprie); i metri alla svolta si aggiornano in tempo reale (a passi di 10 m sotto i 200 m, poi 50/100 m).
   In CoMaps la classe della strada c'è già (tag `highway` di OSM: motorway, trunk, primary, secondary…).
4. Suite: «dove devi andare» da Calendario, Viaggi e Rubrica; manda l'arrivo con Messaggi; percorso legato all'impegno.
5. Benzinai col prezzo (open data MIMIT, CSV quotidiani, licenza IODL 2.0; dal 10 febbraio 2026 è cambiato il separatore).
6. Centro privacy: cosa resta sul telefono (mappe, percorsi) e cosa esce (download delle mappe, prezzi, meteo).
Traffico in tempo reale: nessuna fonte libera nazionale; resta spento «in prova». Lettura dei cartelli con la fotocamera:
serve un modello nostro (dataset liberi piccoli), fuori da GitHub Actions.

## Interfaccia sulle tavole, passo a) (12 novembre, sera)
- Compose nel modulo app di CoMaps (`eurise_patch.py`: plugin del compilatore Compose con la versione di Kotlin di
  CoMaps, BOM 2024.12.01, solo `ui` e `foundation`). Le schermate nuove stanno in `maps/android/…/eurise/ui/`.
- `EuriseMapBar.kt`: barra satinata in basso (Cerca, Livelli, Posizione, Salvati, Menu) e «+» satinato per segnalare
  su OpenStreetMap il punto al centro della mappa. È un `ComposeView` dentro `map_buttons_bottom.xml` (verticale); i
  pulsanti di CoMaps restano nascosti perché `MapButtonsController` li cerca per id, e i comandi passano da
  `MwmActivity` come prima. In orizzontale per ora resta la disposizione di CoMaps.
- Ricerca su OpenStreetMap (Photon) nel centro privacy dell'app Eurise (`PrivacyContract`, servizio `MapSearch`):
  acceso, spento e registrato lì; Mappe segue lo stato del centro privacy prima di ogni richiesta.

## Interfaccia sulle tavole, passo b) (13 novembre)
- `eurise/ui/EuriseSearchHome.kt` (Compose): nella ricerca, prima di scrivere, la pagina della tavola MapCerca al posto
  delle schede di CoMaps (cronologia, categorie, «Impegni»), che restano nel layout ma nascoste. Sezioni: «Dove devi
  andare» (impegni con un luogo dall'app Eurise, come prima; più «L'indirizzo di un contatto», che usa il selettore della
  Rubrica di CoMaps, senza permesso dei contatti), «Cerca vicino» (farmacia, benzinaio, parcheggio, dove mangiare,
  alimentari, bancomat, ospedale, bagni, alberghi: le categorie del motore), «Cercati di recente» (se la cronologia è
  accesa), «Mappe sul telefono» (regioni scaricate con peso, Pronta o Aggiorna, e «Scarica o aggiorna le mappe»).
  Testo d'aiuto del campo: «Indirizzo, luogo, impegno, farmacia».
- Non ancora: il campo di ricerca e la barra in alto restano quelli di CoMaps (colori della suite già applicati); la
  proposta di scaricare la regione di un impegno che non è sul telefono (serve sapere dove sta il luogo prima di cercarlo).

## Interfaccia sulle tavole, passo c) (13 novembre)
- `eurise/ui/EuriseLuogo.kt` (Compose, tavole MapLuogo e MapLuogoAperta approvate): nella scheda del luogo, sotto nome,
  tipo, indirizzo e stato di apertura di CoMaps, le azioni della suite: «Indicazioni» grande e «Parti ora» (il percorso si
  calcola e la guida parte da sola: `MwmActivity.onBuiltRoute`), poi Salva, Chiama (primo numero di OSM), Impegno (nuovo
  evento con il luogo già scritto, nel calendario predefinito), Condividi (nome, indirizzo e link OpenStreetMap), e
  l'impegno della suite con quel luogo (DestinationsContract, come «Dove devi andare»).
- In basso restano solo «Parti da qui» e «Segnala»; quando si pianifica un percorso tornano tutti i pulsanti di CoMaps.
- Colori Perla e Notte e lettura degli impegni ora sono in comune (`eurise/ui/EuriseMapStyle.kt`).
- Non ancora: la scheda aperta con orari e contatti nello stile della tavola (oggi sono le righe di CoMaps con i colori
  della suite), «Nota del luogo» e «Cerca qui vicino».

## Guida, passo e) prima parte (13 novembre, sera)
- `eurise/EuriseSpeed.java` (tavola MapVelocita): la velocità diventa un tondo rosso oltre il limite più la tolleranza
  (Impostazioni › Navigazione: 0, +5 km/h di base, +10%), con un suono breve all'inizio del superamento (non più di uno
  ogni 20 s); il cartello del limite resta bianco col bordo rosso; quando il limite cambia il cartello pulsa e suona due
  volte (si spegne). Limite da OpenStreetMap, velocità dal GPS.
- `eurise/EuriseJunctionScene.java` (tavole MapIncrocio e MapRotonda): la manovra vista dal volante, disegnata su Canvas.
  Uscita: corsie di OSM, corsia di uscita colorata, frecce che scorrono dritte verso la rampa, portale con il cartello
  (numero d'uscita, sigla, destinazioni; verde se autostrada) che lampeggia piano. Rotonda: uscite numerate, quella
  giusta blu, frecce dipinte che si accendono nel verso di marcia, frecce dritte all'uscita, cartello «3ª uscita · via».
  Compare 600 m prima su autostrada e superstrada, 300 m altrove e alle rotonde; si chiude dopo la manovra.
  `EuriseJunctionView` la mostra animata sotto le corsie (solo in verticale per ora); in Android Auto la stessa scena,
  ferma, va nella finestrella della manovra (`RoutingInfo.Builder.setJunctionImage`).
- Non ancora: «tra 200 m: 70» (il limite che arriva: CoMaps non lo dà in anticipo), il limite «dedotto» (bordo
  tratteggiato), velocità e limite anche senza percorso, la scena in orizzontale.

## Guida, passo e) seconda parte: Segnala in guida (13 novembre, notte)
- `eurise/EuriseGuideReports.java` (tavole MapSegnalaGuida e MapAutoSegnala): sei voci, l'archivio (preferenze dell'app,
  JSON), la fine del viaggio e l'avviso a chi ripassa. Il punto e la direzione si prendono al tocco (ultima posizione
  della guida, anche in auto), con la strada sotto l'auto (`EuriseCarInfo.roadLabel`) e, per «limite errato», il limite
  in mappa in quel momento (`EuriseSpeed.lastLimit`).
  - Autovelox, limite errato, strada chiusa: restano finché il viaggio finisce (`MwmActivity.onNavigationCancelled`,
    anche all'arrivo; in auto `NavigationScreen.onNavigationCancelled`). Poi `EuriseReportReviewActivity`: l'elenco con la
    spunta, «Manda N a OpenStreetMap» (una nota anonima ciascuna: tipo, limite in mappa, direzione in gradi, strada,
    `#EuriseMappe`), «Più tardi». Se il viaggio finisce in auto o col telefono in tasca, l'elenco si apre alla prossima
    apertura della mappa. Quelle non partite (niente rete) restano per il viaggio dopo.
  - Lavori, coda, pericolo: restano sul telefono due ore. Chi ripassa di lì, nella stessa direzione (±60°), entro 500 m
    davanti, vede una volta «Tua segnalazione di 25 min fa: Coda tra 300 m» (avviso sul telefono, CarToast in auto).
    Condividerle con altri richiede un servizio comune: da decidere.
- `eurise/EuriseGuideReportActivity.java`: il foglio sul telefono (bandierina tonda sopra la lente, a sinistra, nel
  layout della guida), due gruppi da tre, barra scura con il tondo che si svuota e «Annulla»; dopo 5 s si chiude. È
  esportata solo alle app della suite (`SUITE_DEBUG`): Alfred la apre con `REPORT_KIND` e segna subito.
- `eurise/car/EuriseCarReportScreen.java`: in Android Auto la bandierina nella fila in alto della guida apre la griglia
  di sei voci sopra la mappa (`MapWithContentTemplate` + `GridTemplate`), poi un messaggio con «Annulla» e «OK» che torna
  alla guida da solo dopo 5 s. Il microfono di Alfred in auto (senza guida) capisce «autovelox», «c'è una coda»
  (`Kind.fromWords`) e segna invece di cercare.
- Centro privacy: le note vanno sotto il servizio `MapReports` dell'app Eurise (OpenStreetMap Foundation, Regno Unito,
  6 su 10), acceso di base perché ogni invio parte da un tocco; spento, l'elenco lo dice e non manda nulla. Vale anche per
  «Segnala qui» fuori guida.
- Simboli: `res/drawable/eurise_rep_*.xml` (riquadro chiaro nella tinta, disegno della tavola), `eurise_ic_flag.xml`.
- Non ancora: la barra in orizzontale (Segnala, Sosta, Voce, Insieme, Fine: tavola MapOrizzontaleGuida); le segnalazioni
  brevi disegnate sulla mappa (oggi solo l'avviso).

## Cielo e colline in guida (14 novembre, notte; tavola MapCieloColline)
- `geometry/screenbase.cpp` (da `eurise_patch.py`): con `ScreenBase::SetEuriseSky(true)` la prospettiva automatica sale
  fino a 70° (Android Auto) o 71° (telefono) invece di 53°/55°, con la stessa salita al cambiare della scala; il piano
  della mappa (`CalculateScale3d`) resta grande quanto con l'angolo pieno di CoMaps. Stesse tessere e stessa vista vicino
  all'auto; il bordo lontano scende sotto il bordo alto dello schermo (circa il 27% di cielo). Calcoli con le matrici
  di `SetRotationAngle`: alzare l'angolo senza fermare il piano non funziona (oltre 60° la formula del piano diverge).
- `maps/engine/libs/drape_frontend/eurise_sky.{hpp,cpp}`: disegna l'immagine del cielo con il programma `ScreenQuad`
  (nessuno shader nuovo), in `FrontendRenderer::RenderScene` dopo palazzi e percorso, prima di scritte e segni. Otto
  triangoli calcolati a ogni fotogramma dagli spigoli del piano (`PtoP3d`): cielo fino al bordo, colline sotto il bordo
  e nei due cunei ai lati dove la mappa lontana non arriva al bordo dello schermo. La riga 0,8 dell'immagine cade sul bordo.
- `EuriseSky.java`: l'immagine (mezza risoluzione) con `EuriseLandscape` (cielo dal meteo, sole o luna, nuvole, pioggia o
  neve, colline e alberelli della stagione, più scuro la sera); passa al motore con `Framework.nativeEuriseSetSky`.
  Si accende con la guida (`NavigationController.show`), si rifà a ogni rotazione e ogni 5 minuti, si spegne a fine guida
  o con l'interruttore «Cielo e colline sopra la mappa» (Navigazione › Cielo e stagioni, acceso di base: nulla esce).

## Avvio con l'icona della suite (15 novembre, Mappe 48)
- `res/layout/activity_splash.xml` nostro (stessi id di CoMaps): icona di Eurise Mappe (`ic_maps_foreground`, 240 dp al
  centro) sul blu notte della suite (`eurise_icon_background`), nome e riga di OpenStreetMap in basso. `MwmTheme.Splash`
  blu notte anche col tema scuro (`eurise_patch.py`); Android 12+ in `values-v31/eurise_splash.xml` con la stessa icona
  senza cerchio, così l'avvio di sistema e quello di CoMaps coincidono.

## «Dove sei» mentre si esplora, passo f) (15 novembre, Mappe 51; tavola MapEsplora)
- `eurise/ui/EuriseDoveSei.kt`: scheda satinata sopra la barra (nella colonna della barra, accanto a zoom e lente):
  bandiera, «Monza (MB)», «Via Italia · Lombardia · IT»; a destra gradi e cielo (solo con il meteo acceso nel centro
  privacy, `EuriseRouteWeather.hereParts`) e tramonto o alba (calcolati, `SunTimes`). Tocco: «Salva il luogo» e «Manda
  la posizione». Il punto è il centro della mappa; si cerca quando la mappa è ferma e il centro si è spostato di 30 m
  (una lettura al secondo, ferma in secondo piano). Nascosta durante percorso e guida; interruttore «Dove sei» in Livelli.
- Comune: `Framework.nativeEuriseCityAt` (JNI aggiunto da `eurise_patch.py`, `search::CityFinder` con il nome locale).
  Sigla e regione: `assets/eurise_comuni.tsv` (7904 comuni ISTAT al 1/1/2020, CC BY 4.0, da matteocontrini/comuni-json;
  circa 50 KB compressi). Per i sei nomi doppi si sceglie con la regione della mappa di CoMaps; le province sarde del
  2025 non ci sono ancora. Paese dal paese della mappa (`MapManager.nativeFindCountry`) con il codice ISO.
- Non ancora: nomi delle vie più leggibili (stile del motore) e cielo con l'orizzonte in esplorazione (dopo la prova del
  cielo in guida di Mappe 47).

## Esplora e Attività, resto (15 novembre; tavole MapEsplora, MapAttivita, MapLivelli approvate)
- Orizzonte esplorando: `eurise/EuriseSkyBand.java`, vista in `activity_map.xml` sopra la mappa (non prende i tocchi):
  dietro la barra di stato e poi 112 dp (64 in orizzontale), disegnata da `EuriseSky.band` con gli stessi pezzi della
  guida. Nascosta in percorso e guida; interruttore «Orizzonte col meteo e l'ora» in Livelli (stessa preferenza del cielo
  in guida, `EuriseSky.PREF`).
- Scorciatoie in alto: `eurise/ui/EuriseEsplora.kt` in `map_buttons_layout_regular.xml` (solo esplorando): Benzinai
  (`EuriseFuelActivity`), Farmacie, Mangiare, Negozi (ricerca per categoria), Professionisti (presto con QuiAccanto).
- Nomi delle vie: `eurise_patch.py` alza di un punto le scritte delle strade da auto in `Roads_label.mapcss` (dalle
  autostrade alle vie di quartiere) e porta il grigio chiaro al medio; scudi e sentieri come in CoMaps.
- Benzinai: «Cerca anche» della tavola (Negozi e servizi, Punti di interesse, Officine e gommisti, Professionisti presto).
- Livelli: in più «Orizzonte col meteo e l'ora», «Prezzi dei carburanti» (stato e interruttore del centro privacy) e
  «Traffico e cantieri» spento e non toccabile (nessuna fonte libera per ora). «Leggi i cartelli con la fotocamera»: non
  ancora (serve un modello di visione scaricato a richiesta, senza ML Kit).

## Ricerca, scheda aperta, orizzontale (15 novembre; tavole MapCerca, MapLuogoAperta, MapOrizzontale approvate)
- Ricerca: il campo di tutte le barre di ricerca di Mappe è una pillola satinata (`drawable/eurise_search_pill.xml`,
  `toolbar_search_controls.xml` ritoccato da `eurise_patch.py`). La testata resta quella di CoMaps (blu notte).
- Scheda aperta: `eurise/ui/EuriseLuogoAperta.kt`, sotto gli orari di CoMaps (`place_page_details.xml`): «Contatti e
  indirizzo» (indirizzo e coordinate da copiare con pressione lunga, «Aggiungi alla Rubrica» con l'inserimento di
  sistema), «Nella suite» (nuovo impegno qui, «Nota del luogo» con `ShareToNotesActivity` dell'app Eurise), «Altro»
  («Cerca qui vicino» centra la mappa sul luogo e apre la ricerca, «Correggi su OSM» apre Segnala qui). Telefono, sito e
  social restano le righe di CoMaps; orari ancora quelli di CoMaps (nei colori della suite).
- Orizzontale esplorando: `EuriseMapBar.Landscape`, barra in colonna a sinistra (Cerca, Livelli, Posizione, Salvati,
  Menu) con Segnala sotto; a destra «Dove sei» e lo zoom. `layout-land/map_buttons_bottom.xml` riscritto da
  `eurise_patch.py` come quello verticale (pulsanti di CoMaps nascosti, barra Compose); scorciatoie in alto spostate dopo
  la colonna. Le quattro varianti di `map_buttons_layout_regular.xml` hanno le scorciatoie.

## Guida e Android Auto, resto (15 novembre; tavole MapVelocita, MapAutoCerca approvate)
- Limite che arriva: `Framework.nativeEuriseNextSpeedLimit(800)` (JNI in `eurise_patch.py`: tratti del percorso di
  `RoutingSession::GetRouteForTests`, limite per tratto `RouteSegment::GetSpeedLimit`); `EuriseSpeed` mostra la pillola
  «tra 200 m: 70» (`eurise_next_limit` nei tre `layout_nav_top.xml`, a passi di 50 m), con l'interruttore «Avviso
  quando il limite cambia».
- Limite dedotto: `EuriseCarInfo.inferredLimitKmh` (Italia: autostrada 130, superstrada 110; altrove e sulle altre strade
  nessuno, perché dipende dal centro abitato) mostrato nel cartello con il bordo tratteggiato
  (`drawable/eurise_limit_dashed.xml` come primo piano); non fa suonare e non colora la velocità.
- Android Auto: `car/EuriseCarDestinations.java`, «Dove devi andare» (fino a 4 impegni con luogo) in cima alla ricerca;
  la panoramica del percorso prima di partire si riadatta quando cambia l'area visibile
  (`Framework.nativeEuriseShowRouteOverview` da `SurfaceCallbackBase.onVisibleAreaChanged`).

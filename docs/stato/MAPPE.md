# Stato dell'area Mappe

File di stato dell'area, tenuto dalle chat del progetto «Eurise · Mappe» (dal 16 novembre 2026). `docs/STATO.md` resta il
diario generale: lì si scrive solo una riga per consegna che rimanda qui. Regole in `CLAUDE.md`, sistemi in
`docs/PIATTAFORME.md`. Ramo `brand`; giri propri su `prova-mappe`.

## Cosa comprende
Mappe (APK CoMaps), guida, Android Auto, Meteo, segnalazioni OSM, Mappe sul computer.

## Fatto (in breve, al 16 novembre, notte)
- **Mappe in stile suite** su CoMaps (patch in `maps/eurise_patch.py`, codice in `maps/android/…/eurise/`, guida in
  `maps/EURISE.md`): Esplora (orizzonte col meteo in alto, scorciatoie, «Dove sei»), Attività e Cerca, ricerca a pillola,
  Livelli e fonti, scheda del luogo e scheda aperta (con gli **orari nello stile della tavola**), barra in orizzontale,
  avvio con l'icona di Eurise Mappe. **Dall'apertura alla partenza** come le tavole MapViaggio 2-6 (ricerca, luogo,
  percorso e tappe nello stile della suite, senza la barra di CoMaps).
- **Guida**: guida nello stile della suite, limite che arriva e limite dedotto, avvisi sopra il limite, autovelox fissi,
  meteo sul percorso, «I tuoi giorni» nel paesaggio, cielo e colline nel motore, voce di Alfred, Segnala in guida.
- **Android Auto**: guida, «Dove devi andare», panoramica, ricerca, segnalazioni, «Sei su … · km», soccorsi con km e
  coordinate, meteo.
- **Mezzi e viaggi lontani** con Transitous (servizio «Mezzi pubblici» nel centro privacy), **prezzi dei carburanti**
  (file del Ministero), **km dalle pietre miliari** (lavoro `pietre.yml`, pre-release `pietre`, servizio «Km sulle
  strade»). Dettagli del 15 novembre, sera, in `docs/STATO.md`.

## Tavole
- Tutte le tavole di Mappe sulla tela (pagina Mappe: `Map*`, comprese MapAutoGuida, MapLuogoAperta, MapLivelli, MapMezzi,
  MapCieli) sono **approvate e in codice**.
- **MapViaggio 2-6** (fila «Mappe dall'apertura alla partenza») in codice dal 16 novembre, 13:03 (commit 579b305, giro
  **Mappe 69**); MapViaggio1 è com'era l'app prima.
- **PcMappe** (Mappe sul computer, con CoMaps) approvata il 16 novembre: codice ancora da scrivere (con l'area Computer).
- **MapBollaAuto, MapBollaAutoChiamata, MapBollaTelefono** (pagina «comunicazione», riga sotto MapViaggio): approvate il
  16 novembre, sera, e in codice (vedi sotto).
- **Approvate il 17 novembre e in codice (Mappe 81)**: riga MapBolla (pagina «comunicazione», a destra di MapBollaTelefono) con le otto aggiunte
  alla bolla: **MapBollaPodcast** (a), **MapBollaDopo** (b), **MapBollaRadio** (c), **MapBollaPersa** (d),
  **MapBollaRitardo** (e), **MapBollaMessaggio** (f), **MapBollaImpegni** (g), **MapBollaArrivo** (h) e
  **MapBollaAutoStati** (Android Auto: le azioni passano dagli avvisi dell'auto, `AppManager.showAlert`, max 2 pulsanti;
  la fila della guida resta a 4). Scelte proposte: messaggi senza testo in vista, risposta con la risposta rapida della
  notifica; arrivo condiviso per SMS da Eurise Messaggi (niente server, mai la posizione continua). Generatore:
  `/home/claude/tela/gen/bolla.py` (fuori dal repo). **Parte comune toccata**: `canvas.json` della tela, solo per le nove voci.
  Richiesta del proprietario all'approvazione: in auto spostare più in basso e rimpicciolire i 4 tasti tondi. Non si può
  (li disegna Android Auto); la bolla in auto è diventata più stretta e bassa, appoggiata ai tasti (MapBollaAutoStati
  aggiornata).
## 16 novembre, 13:03: dall'apertura alla partenza come le tavole, Alfred in Android Auto (Mappe 69)
- Percorso (tavole 4 e 6, `eurise/ui/EurisePercorso.kt`): in alto scheda di vetro con partenza, tappe e arrivo, scambia,
  pillole «Evita …» del mezzo e «+ Tappa» **al posto della barra di CoMaps**; in basso mezzi con simbolo e tempo e il
  riassunto (tempo, distanza, arrivo, scelte attive, dislivello in bici e a piedi, impegno).
- Tappe (tavola 5, `EuriseViaggio.kt`): foglio con partenza, tappe e arrivo da trascinare, togli, «lungo la strada»,
  aggiungi, Fatto.
- Luogo (tavola 3, `EuriseLuogo.kt`): impegno in cima, «Indicazioni · ≈ N min»; niente barra di CoMaps se non si pianifica.
- Ricerca (tavola 2, `EuriseSearchHome.kt`): titolo grande, Dal Calendario, Sempre a portata (Casa e Lavoro dai preferiti,
  contatto, benzinai), recenti; campo in basso sopra la tastiera.
- Android Auto, dettatura: «Ti ascolto» in una schermata che mostra ogni passo e ogni errore (`car/EuriseCarListenScreen.java`;
  prima sembrava non succedere nulla), controllo del microfono che non manda audio, microfono di Alfred anche in guida e
  nella ricerca.
- Compilazione: **Mappe 69** riuscito (commit 51ab924; Mappe 67 non è arrivato), pubblicato in `apk-prova` alle 13:43:
  https://github.com/eurise-suite/eurise/releases/download/apk-prova/eurise-mappe-debug.apk

## 16 novembre: correzioni dopo la prova di Mappe 58 (Mappe 65)
- Prova del proprietario: «va molto bene». Correzioni chieste e fatte:
  - logo di CoMaps nella tendina: icona piccola di Eurise Mappe (`res/drawable/ic_logo_small.xml`) e notifica della guida
    blu notte;
  - cartelli oltre l'orizzonte: le scritte e i segni si disegnano solo sotto le colline (`EuriseSkyClipLabels` in
    `drape_frontend/eurise_sky.cpp`, taglio dello schermo attorno a `RenderOverlayLayer`);
  - colline di notte marroni: di sera e di notte prendono i toni del cielo (`EuriseSky.hillColors`);
  - riquadri della guida scuri: scheda della manovra, colonna in orizzontale e corsie in vetro satinato della suite,
    chiaro di giorno e scuro di notte (`res/values*/eurise_colors.xml`, tema notte di CoMaps col tramonto); il tipo di
    strada colora solo la freccia (`EuriseGuide.tint`, versioni chiare di notte);
  - finestrino della svolta con l'incrocio vero: `RoutingManager::EuriseJunctionArms` (grafo stradale da auto, nodo entro
    15 m) → `TurnGeometry.arms` → `EuriseJunctionScene.shape`: la strada dritta oltre l'incrocio solo se c'è, le altre
    vie dove sono; senza dati la scena di prima;
  - Android Auto con lo schermo diviso: pillola di vetro in basso a sinistra con velocità, via (e km) e meteo
    (`car/EuriseCarSplitInfo.java`), solo quando la mappa è stretta rispetto allo schermo dell'auto. **Da verificare in
    auto**: il riconoscimento dello schermo diviso è una stima (superficie sotto l'80% della larghezza dell'auto o area
    visibile sotto il 55%).
- **Dettatura in Android Auto che non funziona**: affrontata nel giro delle 13:03 (vedi sopra); in guida il microfono è
  nella schermata della bandierina dalla sera del 16.
- **Tavole nuove** (pagina Mappe della tela, fila «Mappe dall'apertura alla partenza», poi in codice alle 13:03): MapViaggio1Apertura
  (com'è oggi), 2 Dove devi andare (campo in basso con la dettatura), 3 Luogo (Indicazioni coi minuti, Parti ora),
  4 Percorso (partenza e arrivo in vetro al posto della barra di CoMaps con i tipi di percorso; Evita e + Tappa in
  pillole; mezzi coi minuti), 5 Tappe (si trascinano, benzinaio e bar lungo la strada), 6 Bici e a piedi (piste,
  dislivello, scelte). Oggi in alto nel percorso c'è ancora la barra di CoMaps (indietro, auto/pedone/mezzi/bici/righello,
  opzioni): adattata nel giro delle 13:03.

## 16 novembre, pomeriggio: le schermate come le tavole MapViaggio (approvate) e Alfred in Android Auto (Mappe 69)
- Il proprietario approva le tavole MapViaggio 1-6 e chiede che l'app sia davvero così (in Mappe 65 restavano pezzi di
  CoMaps). Fatto:
  - **Percorso** (4 e 6): `eurise/ui/EuriseViaggio.kt` in alto (`routing_plan.xml`, agganciata in
    `RoutingPlanController`): scheda di vetro con partenza, tappe, arrivo e scambia; pillole «Evita …» del mezzo (solo
    quelle che il motore sa fare: auto pedaggi, autostrade, traghetti; bici sterrati, scale, traghetti; a piedi scale,
    sterrati) e «+ Tappa». La barra di CoMaps e il suo riquadro delle opzioni restano nel layout, nascosti. In basso
    `EurisePercorso`: mezzi con simbolo e tempo, riassunto (tempo, distanza, arrivo, scelte attive, dislivello col grafico
    del motore in bici e a piedi, impegno) al posto delle righe di CoMaps (nascoste a ogni aggiornamento).
    Non fatte perché il motore non le ha: «km su pista ciclabile» e le scelte «più piste / meno salita / più breve».
  - **Tappe** (5): foglio dal basso (`EuriseViaggio.showStops`): righe da trascinare tenendo premuto, togli, «lungo la
    strada» (benzinaio, bar, parcheggio, farmacia: ricerca della categoria, il luogo scelto entra come tappa), aggiungi
    una tappa, Fatto (stesso ordine dei punti di «Pianifica» di CoMaps).
  - **Luogo** (3): impegno in cima, «Indicazioni · ≈ N min» (stima dalla linea d'aria), la barra dei pulsanti di CoMaps
    sparisce quando non si pianifica.
  - **Ricerca** (2): titolo grande, Dal Calendario, Sempre a portata (Casa e Lavoro = preferiti chiamati «Casa»/«Lavoro»,
    contatto, benzinai), recenti, poi Cerca vicino e mappe; campo di CoMaps spostato in basso sopra la tastiera
    (`EuriseSearchHome.bottomField`, `fragment_search.xml`).
- **Dettatura in Android Auto**: il proprietario dice che né il nostro microfono né quello della tastiera dell'auto fanno
  nulla. Quello della tastiera è dell'auto (Assistente Google): senza i servizi Google non va e non possiamo cambiarlo.
  Il nostro ora apre «Ti ascolto» (`car/EuriseCarListenScreen.java`) con ogni passo e ogni errore in vista (permesso sul
  telefono, microfono dell'auto che non manda audio entro 3 s, Eurise Voce assente o senza modello) e «Riprova»; il
  microfono c'è anche in guida e nella ricerca. Un ascolto rimasto appeso non blocca più i tocchi dopo.

## 16 novembre, sera: Mappe 69 si chiudeva all'avvio della navigazione in Android Auto
- Prova del proprietario: avviando la navigazione in auto compare un errore e Mappe si chiude anche sul telefono.
- Causa: il giro delle 13:03 aveva messo il microfono nella fila in alto della guida (`NavigationScreen.createActionStrip`),
  che con voce, impostazioni, bandierina e stop arrivava a cinque pulsanti; l'auto ne accetta al massimo quattro e la
  schermata della guida lanciava un errore, che chiude tutto il processo (auto e telefono).
- Correzione (commit 960cd74, **Mappe 75 pubblicato** in `apk-prova` il 16 novembre alle 22:40; Mappe 70 fallito per Gradle che non trovava il plugin
  Kotlin, errore di rete): il microfono in guida sta in alto nella schermata della bandierina
  (`EuriseCarReportScreen.micAction`); «autovelox», «c'è una coda»… segnano, ogni altra frase cerca il luogo. La fila
  della guida torna a quattro. Nella ricerca dell'auto restano due pulsanti (microfono e «Mostra sulla mappa»), il massimo.
- Regola per i prossimi giri: prima di aggiungere un pulsante in Android Auto contare quelli che ci sono (fila della
  guida e della mappa: 4; ricerca: 2; intestazione: 2).

## 16 novembre, sera: bolla della musica e delle chiamate in guida (tavole MapBolla approvate)
- `eurise/EuriseNowPlaying.java`: musica (sessioni multimediali: titolo, autore, album, anno se c'è, durata, posizione,
  copertina ridotta, app) e chiamata (notifica di categoria chiamata: nome o numero, testo, inizio, in arrivo, foto,
  impegno in comune dagli impegni dell'app Eurise con il nome di chi chiama, da dove passa la voce). Arriva tutto
  dall'accesso alle notifiche (`EuriseNowPlayingService`, nel manifesto con la patch); interruttore «Musica e chiamate in
  guida» in Livelli › Sul telefono, spento di base, con la spiegazione e poi le impostazioni del telefono per l'accesso.
- `eurise/EuriseMediaBubble.java`: la stessa vista sul telefono (`NavigationController`: sotto cartello, corsie e meteo;
  in orizzontale accanto a ciò che sta a destra) e in auto (`SurfaceCallback.initSpeedPill`: in alto a destra dell'area
  libera). Solo in guida; in pausa sparisce dopo 10 s; la chiamata prende il posto della musica e dopo 5 s torna la
  musica. Sul telefono tocco = apre l'app o la chiamata, pressione lunga = nascosta per il viaggio.
- Testi: `eurise/EuriseLyrics.java` da LRCLIB (titolo, autore, album, durata) con il servizio nuovo
  **`PrivacyService.Lyrics`** («Testi delle canzoni», spento di base, interruttore in Livelli › Dalla rete). Calcoli in
  `NowPlayingText.java` con le prove `maps/test/…/NowPlayingTextTest.java` (aggiunte al lavoro di Mappe).
- **Parti comuni toccate**: `core/suite` (PrivacyCenter, PrivacyContract.SHARED), `core/designsystem` (ServiceConsent,
  ServicesWelcome, `ds_s_lyrics`). Serve anche l'APK di Eurise nuovo perché la voce dei testi compaia.
- **Pubblicati** il 16 novembre alle 22:41: **Mappe 76** (bolla e correzione di Android Auto) ed **Eurise 681** (servizio dei
  testi nel centro privacy; nello stesso giro è fallito solo il Flatpak per Linux, errore del disco del runner).
- Da provare: guida con Spotify e con un'altra app; chiamata in arrivo e in corso (anche in auto); pausa; telefono in
  orizzontale; testi accesi.

## 17 novembre: le otto aggiunte alla bolla in guida (Mappe 81, Eurise 707, Messaggi 707)
- Codice: `eurise/EuriseNowPlaying.java` (coda «Dopo», capitolo e numero della traccia, podcast/audiolibri e radio,
  numero di chi chiama, chiamata persa = squillo finito senza risposta o notifica «missed_call» recente, messaggi con
  MessagingStyle e risposta rapida, timer con conto alla rovescia, sveglia da `AlarmManager.getNextAlarmClock`, impegni
  delle prossime 3 ore), `EuriseTrip.java` (avvisi con due pulsanti e scadenza, «Richiama all'arrivo» = attività
  nell'app Eurise con il ricevitore della Tastiera + promemoria a fine guida come notifica «Chiama», «Avvisa: arrivo
  alle…» con Annulla 5 s, lettura di Alfred e risposta a voce riletta, arrivo condiviso per SMS: alla partenza, se cambia
  di 5 min e non prima di 10 min dall'ultimo, all'arrivo; da sola si spegne se la guida non parte entro 2 ore),
  `EuriseSms.java`, `EuriseSpeak.java` (sintesi del telefono, musica abbassata), `EuriseReplyActivity.java` (riconoscimento
  vocale del telefono e scelta dalla Rubrica, trasparente), `EuriseMediaBubble.java` (righe e pulsanti),
  `car/EuriseCarAlerts.java` (avvisi di Android Auto, livello 5; prima un messaggio breve), `car/EuriseCarShareScreen.java`
  e secondo pulsante nell'intestazione della bandierina in auto; «Ti ascolto» con la frase intera per le risposte.
  Ingressi dell'arrivo condiviso: bandierina sul telefono, «Manda l'arrivo» nel percorso (una volta / per SMS fino
  all'arrivo), bandierina in auto (persone già usate). Calcoli in `NowPlayingText` con le prove.
- Interruttore nuovo **«Messaggi in guida»** in Livelli (sotto la bolla, spento di base, spiegazione prima di accendere):
  legge chi scrive e il testo solo in guida, in memoria. Spiegazione della bolla aggiornata (chiamate perse, timer).
- **Parti comuni toccate**: Messaggi (`system/SuiteSmsReceiver.kt`, ricevitore con il permesso della suite: numero o nome
  da cercare in Rubrica, invio come app predefinita o con il permesso SMS); app Eurise (`DestinationsProvider` metodo
  «soon», `core/suite/DestinationsContract.METHOD_SOON`).
- In auto: bolla 360 dp invece di 440, copertina 48, una riga sola per le righe nuove (al posto dell'album), niente
  «riga dopo» del testo della canzone; margine 2 dp dai tasti. **Non provato**: la bolla sotto i tasti (l'auto decide
  dove stanno; serve una prova in auto prima di provarci).
- Non fatto (detto nella tavola g): «Avvisa» per un impegno vicino senza chiamata (servirebbero i partecipanti
  dell'evento); capitoli veri dei podcast (Android non li passa: si usa il numero della traccia, se l'app lo dà).
- Pubblicati il 17 novembre, 00:01 (UTC): Mappe 81, Eurise e Messaggi dal giro 707.
- **Da provare**: Livelli › Messaggi in guida; guida con podcast (AntennaPod), musica con coda (Spotify), radio; chiamata
  persa in guida → Richiama all'arrivo → attività in Attività e notifica a fine guida; chiamata di chi ha un impegno
  con te mentre sei in ritardo → Avvisa (Messaggi installato); messaggio → Leggi → Rispondi a voce; timer dell'Orologio;
  bandierina › Condividi l'arrivo; tutto anche in Android Auto (avvisi con i pulsanti).
- Passo f) di Esplora: i **nomi delle vie più leggibili sono già in codice** (patch su `Roads_label.mapcss`: un punto in
  più e grigio medio). Resta l'orizzonte col cielo esplorando: il motore disegna il cielo solo con la mappa inclinata
  oltre l'orizzonte, e in esplorazione l'inclinazione di CoMaps non ci arriva; serve una scelta del proprietario (vedi
  Prossimi passi).

## Prossimi passi, in ordine
1. **Prova del proprietario di Mappe 81, Eurise 707 e Messaggi 707** (le otto aggiunte, elenco sopra) e di quanto restava
   da Mappe 76 ed Eurise 681: avvio della navigazione in auto (non deve più chiudersi; microfono
   nella schermata della bandierina); bolla in guida (Livelli › Musica e chiamate in guida, poi l'accesso alle notifiche)
   con Spotify e un'altra app, chiamata in arrivo e in corso, pausa, telefono in orizzontale, auto; Livelli › Dalla rete ›
   Testi delle canzoni.
2. Bolla, dopo la prova: «Avvisa» anche per l'impegno vicino senza chiamata, se il proprietario lo vuole (partecipanti).
3. **Pietre miliari**: Germania e Austria mancano (Geofabrik rifiuta i runner di GitHub dopo i primi download; ora c'è lo
   specchio di openstreetmap.fr). In Italia le pietre in OpenStreetMap sono poche (8.672): il km compare soprattutto in
   autostrada. I file restano su GitHub (16 novembre, il proprietario lascia perdere il server europeo).
4. Passo f) di Esplora, parte che resta: orizzonte col cielo anche esplorando. Proposta: quando si inclina la mappa con due
   dita, l'inclinazione arriva fino al cielo (come in guida) e la fascia di MapEsplora sparisce; serve l'ok del
   proprietario e una tavola (cambia il gesto di CoMaps).
5. **Mappe sul computer** (tavola PcMappe, CoMaps su Linux), insieme all'area Computer.
6. In attesa di una fonte o di una decisione: segnalazioni brevi condivise con gli altri (serve un servizio comune);
   traffico (nessuna fonte libera nazionale); nei viaggi lontani prezzi di treni e aerei, pedaggi, auto + treno, Cose da
   fare e «Il mio viaggio» (nessuna fonte libera verificata).
- **Passo del proprietario prima di pubblicare Mappe**: con Transitous concordare l'uso oltre 720 richieste al giorno
  (canale Matrix) e mettere un contatto nel User-Agent.
- **Messo da parte** (16 novembre, decisione del proprietario): «Leggi i cartelli con la fotocamera». Forse un
  aggiornamento tra qualche anno; la voce resta solo nella tavola MapLivelli, non nell'app.

## Come si lavora
- Codice di Mappe: `maps/android/` (copiato sopra CoMaps) e `maps/eurise_patch.py` (modifiche ai file di CoMaps); si
  compila con «Prototipo di Eurise Mappe» (`maps-engine.yml`, circa 30 minuti, un giro alla volta per tutti i rami).
- Prove senza telefono in `maps/test/` (Java puro) e `maps/pietre/test_pietre.py`, lanciate dai lavori prima di compilare.
- APK: dopo «Compila APK» e Mappe riusciti, `gh workflow run apk-prova.yml --ref brand -f run_id=<id di Compila>` (Mappe
  = ultimo riuscito tra `prova-mappe` e `brand`); poi in chat i link diretti, per esempio
  https://github.com/eurise-suite/eurise/releases/download/apk-prova/eurise-mappe-debug.apk e, se cambia il centro
  privacy, anche https://github.com/eurise-suite/eurise/releases/download/apk-prova/app-debug.apk.
- Giri propri (istruzioni del progetto): commit su `brand` con `[skip ci]` in testa, la stessa testa su `prova-mappe` per
  compilare. `apk-prova.yml` prende Mappe dall'ultimo giro riuscito tra `prova-mappe` e `brand` (o da `mappe_run`): dopo un
  giro di Mappe basta `gh workflow run apk-prova.yml --ref brand` (con `-f run_id=` se serve anche un giro di Compila).
  Gli artefatti non si scaricano dall'ambiente di Claude (archivio di Azure bloccato).
- Servizi nuovi che fanno uscire dati: `PrivacyService` in core/suite (con `SHARED` in `PrivacyContract`), testi in
  `ServiceConsent`, interruttore in Livelli con la spiegazione prima di accendere; servono APK di Eurise e di Mappe nuovi.
- Storia completa: sezioni datate di `docs/STATO.md` dall'11 al 15 novembre (Mappe, Android Auto, guida).

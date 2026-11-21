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

## Sicurezza: variante prova/ufficiale (21 novembre 2026, progetto «Eurise Sicurezza»)
- Il patch sceglie la variante con `EURISE_VARIANTE` (prova di base; `maps-engine.yml` → «variante»). Nomi delle app della
  suite e permesso solo da `eurise/EuriseSuite.java` (`HUB`, `VOICE`, `MESSAGES`, `PERMISSION`, `call`, `trusted`,
  `isSuite`): **un codice nuovo non scrive a mano `"eu.eurise.hub.debug"` o il permesso**, altrimenti il passo «Controlla
  la variante» (`maps/test/controlla_variante.py`) ferma il giro. Nel patch, per il manifesto: `SUITE_PERM`, `HUB_PKG`,
  `VOICE_PKG`, `MESSAGES_PKG` dentro f-string.
- Il giro «ufficiale» produce `eurise-mappe-ufficiale.apk`, che `apk-prova` non pubblica. Dettagli in `docs/SICUREZZA.md`.

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
- **MapEsploraInclinata** approvata il 17 novembre e in codice (Mappe 88): tasto «3D»/«2D» esplorando. Il gesto a due
  dita non c'è (CoMaps non lo ha; va scritto nel motore, se il proprietario lo vuole).
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

## 17 novembre, notte: tasto 3D esplorando e ordine di «Dove devi andare» (Mappe 88)
- **Tasto «3D»/«2D»** (`eurise/EuriseTilt.java`, nelle quattro varianti di `map_buttons_layout_regular.xml`, a destra
  sotto la fascia): `EuriseSky.explore3d` mette il cielo della guida e chiama `Framework.nativeEuriseSetExplore3d`, che
  usa `DrapeEngine::EnablePerspective` con un «desiderio» in `eurise_sky.cpp` (vero di base: l'inizio della guida resta
  com'era; falso spegne la prospettiva). In 3D la fascia di MapEsplora si nasconde; all'inizio della guida il 3D
  dell'esplorazione si spegne. Da lontano la prospettiva del motore è zero: la mappa resta piatta anche in 3D (e la
  fascia resta nascosta finché il tasto dice «2D»).
- **«Dove devi andare»** (`ui/EuriseSearchHome.kt`), richiesta del proprietario: Sempre a portata, Recenti, Cerca
  vicino, poi Dal Calendario, poi le mappe. La tavola MapViaggio2Dove ha ancora l'ordine vecchio. In Android Auto
  l'elenco resta com'era.
- Pubblicato il 17 novembre, 01:51 (UTC): **Mappe 88** in `apk-prova`, con Eurise e Messaggi del giro 721 di Compila
  (ultimo riuscito su `brand`, contiene le parti comuni del giro 707).

## 17 novembre, giorno: prova del proprietario in auto e sul telefono (Mappe 90, pubblicato alle 10:51 UTC)
- **La bolla non compare** né sul telefono né in auto, pur avendo «attivato tutto dall'app privacy». La bolla non è un
  servizio del centro privacy: servono l'interruttore di Mappe (Livelli › Sul telefono › Musica e chiamate in guida) e
  l'accesso alle notifiche nelle impostazioni del telefono. Probabile ostacolo: le **impostazioni con restrizioni** di
  Android 13+ per le app installate dal browser (interruttore grigio). Ora Livelli, con la bolla accesa e senza accesso,
  mostra la riga «Dai l'accesso alle notifiche» con la spiegazione, «Info app» (menu ⋮ › Consenti impostazioni con
  restrizioni) e il pulsante per l'accesso. Da confermare con il proprietario.
- **In auto i riquadri sono neri**: li disegna Android Auto. Il riquadro della manovra ora chiede il verde della suite
  (`NavigationTemplate.setBackgroundColor`, come le tavole MapAutoGuida/MapBollaAuto; l'auto può scurirlo per il
  contrasto). Il riquadro dell'arrivo (tempo, km, «Sei su…») non ha un colore che l'app possa scegliere.
- **«Sei su IT:national/SS7 …»**: il riferimento della strada veniva dalla relazione con la rete; ora
  `NowPlayingText.roadRef` tiene solo «SS7» (con le prove).

## 17 novembre, pomeriggio: bolla in auto spostata (Mappe 93) e tavole dei riquadri della suite in auto
- Prova del proprietario: in Android Auto niente bolla e riquadri ancora neri. La bolla stava in alto a destra
  dell'area libera, dove l'auto disegna i 4 tasti tondi che la coprivano (l'area libera non li esclude). Ora in basso a
  sinistra dell'area libera, accanto al riquadro dell'arrivo (sopra la pillola dello schermo diviso, se c'è).
  Pubblicato alle 14:03 (UTC).
- **Da approvare**: **MapAutoSuiteGiorno** e **MapAutoSuiteNotte** (pagina «comunicazione», dopo MapEsploraInclinata):
  in auto i riquadri (manovra, meteo sul percorso, arrivo con «Sei su», bolla accanto all'arrivo, limite) li disegna
  Mappe nel vetro della suite come sul telefono; l'auto non mostra più i suoi (si toglie `NavigationInfo` e la stima
  d'arrivo dal modello di guida; la guida va ancora all'auto con `NavigationManager.updateTrip` per il quadro
  strumenti). Restano dell'auto la barra a sinistra e i tasti tondi. Cambia la tavola approvata MapAutoGuida («cornice
  del sistema»).

## 17 novembre, sera: riquadri della suite in auto e bolla in alto a destra (Mappe 96, pubblicato alle 15:04 UTC)
- Tavole **MapAutoSuiteGiorno/Notte approvate** e in codice: `eurise/car/EuriseCarGuide.java` (manovra con freccia nel
  colore della strada, distanza, sigla e via, corsie, scena dell'uscita o della rotonda, «Poi …»; meteo sul percorso;
  arrivo con ora, minuti, km, avanzamento e «Sei su»), nello strato dell'area libera (SurfaceCallback). Il modello di
  guida non riceve più `NavigationInfo` né la stima d'arrivo (patch in `NavigationScreen`), così l'auto non disegna i
  suoi riquadri; `updateTrip` resta. Il limite resta la pillola di CoMaps in basso a destra (non a sinistra come nella
  tavola).
- Prova del proprietario con Mappe 93 (dopo aver dato l'accesso alle notifiche: la bolla compare): bolla «molto
  piccolina» e da rimettere in alto a destra. Ora in auto è più grande (fino a 480 dp, metà dell'area libera,
  copertina 64 dp, testi più grandi) e sta in alto a destra **dentro l'area stabile** (`onStableAreaChanged`, patch in
  `SurfaceCallbackBase`): sotto i 4 tasti tondi, che compaiono e scompaiono senza coprirla.
- Da provare: riquadri della suite (giorno e notte, rotonde, corsie, scena dell'uscita), bolla in alto a destra con i
  tasti visibili e nascosti, schermo diviso.

## 17 novembre, sera: campo di ricerca visibile (Mappe 97, pubblicato alle 15:38 UTC)
- Prova del proprietario: in «Dove devi andare» il campo in basso non si vedeva (tutto bianco). Il campo di CoMaps ha
  testo, suggerimento e icone bianchi per la barra colorata; spostato in basso su fondo trasparente spariva. Ora
  `EuriseSearchHome.pill` lo fa pillola della suite (vetro chiaro/scuro, bordo, testo e icone nell'inchiostro).

## 18 novembre: prova in auto di Mappe 97, scheda unica a sinistra (tavole da approvare) e bolla più leggibile
- Prova del proprietario in Android Auto (foto): riquadri della suite e bolla «finalmente come li vogliamo». **Regola nuova
  del proprietario**: si prova solo l'ultimo APK, che contiene i giri prima; non chiedere più la prova dei giri vecchi.
- Leggibilità della bolla in auto (`EuriseMediaBubble`): testi più grandi (titolo 24, autore 20, album 17, tempi 17,
  testo della canzone 20 sp), inchiostri più scuri di giorno e più chiari di notte, vetro quasi pieno (la mappa non
  traspare), niente «· da Spotify» (lo dice il simbolo sulla copertina), niente «♪» da sola quando la riga del testo è
  vuota (la riga sparisce; vale anche sul telefono).
- **Tavole da approvare** (pagina separata, la tela è al limite di 512 file: https://claude.ai/artifact/XxtGwSwV8dnfUJVQLq3snD;
  generatore `/home/claude/tela/gen/scheda.py`, fuori dal repo): **una scheda sola a sinistra** che cambia con quel che
  serve, tutta la parte destra libera per la strada. Svolta vicina (sotto 300 m in città, circa 1 km in autostrada) =
  finestrino; svolta lontana = una riga in cima e sotto la parte che cambia, in ordine chiamata in arrivo › messaggio,
  ritardo, chiamata persa, timer, sveglia, impegno › musica, podcast, radio (con il testo della canzone sempre, se c'è).
  **Scelta del proprietario**: con la svolta vicina il finestrino resta sopra e la chiamata scende a una riga sotto.
  In basso la scheda del viaggio con il meteo come riga. Senza destinazione: pillola con via e meteo che si apre solo
  quando serve; con la musica, via in cima e musica con testo. Schermo diviso: in guida svolta e ora d'arrivo, via con
  velocità e meteo, musica in una riga; senza guida via e finestra che si adatta (musica con copertina piccola, titolo,
  autore e testo; chiamate). Notte. Cambia le tavole approvate MapAutoSuiteGiorno/Notte e MapBollaAuto*.
- **Approvate** dal proprietario sulla pagina separata («procedi anche senza tavole, l'artefatto è ok»): sulla tela non
  vanno (è piena). In codice (giro sotto):
  - `car/EuriseCarGuide.java`: colonna unica a sinistra (36% dello schermo, 320–440 dp). Svolta vicina (300 m, 1 km su
    autostrada e superstrada) = finestrino con corsie, scena e «Poi…»; svolta lontana = riga con freccia, distanza e via.
    Il meteo sul percorso è una riga della scheda del viaggio. Senza destinazione: pillola con «Sei in…» e meteo.
    Schermo diviso: svolta sempre in riga con l'arrivo sotto («09:03 · 2 min · 760 m»), niente scheda in basso (resta la
    pillola dello schermo diviso con velocità, via e meteo). `slot()` dice alla bolla dove stare.
  - `EuriseMediaBubble`: in auto sotto la scheda in alto, larga come la colonna e unita (angoli dritti tra le due); c'è
    anche senza destinazione (musica, chiamate, timer, impegni). Con la svolta vicina si ritira, salvo una chiamata
    (una riga). Con lo schermo diviso o poco spazio niente barra dei tempi e album; il testo della canzone resta.
  - Da provare in auto: svolta vicina e lontana, chiamata in svolta, guida senza destinazione con e senza musica,
    schermo diviso con e senza guida, notte.
  - **Mappe 114 pubblicato** in `apk-prova` il 18 novembre alle 12:16 UTC (commit 7ba7942, con la leggibilità della
    bolla): https://github.com/eurise-suite/eurise/releases/download/apk-prova/eurise-mappe-debug.apk
- Compilazione: commit su `brand` e `prova-mappe` (5b1d8f1); i giri 108 e seguenti sono falliti allo scarico di CoMaps
  perché codeberg.org non si risolveva dai server di GitHub. `maps-engine.yml` ora riprova quattro volte e al secondo
  tentativo aggiunge un DNS pubblico. Codeberg è tornato verso le 11:45 UTC. Nota: la coda dei giri di Mappe tiene un
  solo giro in attesa; un giro chiesto da un altro ramo cancella quello in attesa (successo il 18 novembre).

## 22 novembre: schermo diviso, Mappe sul computer, «Dove devi andare» in auto, gesto a due dita per il 3D
- Prova del proprietario di Mappe 114: «funziona tutto»; solo nello schermo diviso in guida la bolla era troppo centrale
  e copriva. Ora con lo schermo diviso la colonna è metà della mappa (240-300 dp) e in guida la bolla è una riga
  (copertina 40 dp, titolo, autore) dentro la scheda, come la tavola 7; senza guida resta con il testo della canzone.
- **Mappe sul computer** (tavola PcMappe), `desktop/app/MapsScreen.kt` e `MapsData.kt` (prove `MapsDataTest`): a
  sinistra «Dove devi andare?» (Invio apre il luogo nella mappa), il prossimo impegno con un luogo (dal riassunto del
  telefono col Ponte, come Oggi) con «Al telefono, per guidare» e «Mappa», gli altri impegni con un luogo, riga della
  privacy 10/10. **La mappa vera è CoMaps per Linux** (Flathub `app.comaps.comaps`, le stesse mappe offline): Eurise la
  apre sul luogo con un link `geo:0,0?q=…` attraverso il sistema (xdg-open, dal Flatpak passa dal portale), a destra
  «Apri la mappa» e «Installa CoMaps». **Non c'è la mappa dentro la finestra di Eurise**: CoMaps per Linux è un'app Qt e
  su Wayland non si incastra in un'altra finestra; per averla dentro servirebbe il motore di CoMaps compilato come
  libreria per il computer (lavoro grande, da decidere). Non ancora dalla tavola: tempo di strada e «parti alle…»,
  «Avviso alle…», luoghi salvati (servirebbe un tipo del Ponte per i preferiti), Mezzi/Bici/Carburanti.
- «Al telefono, per guidare»: il Ponte manda il link `geo:` come «Continua qui»; **parte comune toccata**:
  `feature/ponte/PonteLink.kt` accetta anche `geo:0,0?q=…` (solo quella forma, senza spazi, al massimo 700 caratteri) e
  lo apre dalla notifica, cioè in Mappe. Serve l'APK di Eurise nuovo (`app-debug.apk`) e il Flatpak nuovo.
- **Android Auto, «Dove devi andare»** nell'ordine del telefono: Sempre a portata (Casa e Lavoro, gli stessi preferiti
  del telefono: `EuriseSearchHome.habitualPlaces` ora `internal`; un tocco cerca le coordinate), Recenti, Categorie,
  impegni dal Calendario (`EuriseCarDestinations`, patch in `SearchScreen`).
- **Gesto a due dita per il 3D** (`EuriseTiltGesture.java`, messo dal tasto «3D»): due dita che salgono insieme
  piegano la mappa, che scendono la rimettono piatta (70 dp, distanza tra le dita che non cambia oltre 45 dp). Non è nel
  motore (il sorgente di CoMaps qui non si raggiunge): si riconosce nella finestra prima della mappa; riconosciuto, la
  mappa riceve «annulla». Pizzico e rotazione restano della mappa. Da provare: che non disturbi zoom e rotazione.

## Prossimi passi, in ordine
1. **Prova**: schermo diviso in auto; «Dove devi andare» in auto con Casa e Lavoro; gesto a due dita; sul computer la
   sezione Mappe con CoMaps installato da Flathub e «Al telefono, per guidare».
2. Decidere se vale la pena avere la mappa dentro la finestra di Eurise sul computer (motore di CoMaps come libreria).
   «Avvisa» per l'impegno vicino senza chiamata, se lo vuoi.
3. **Pietre miliari**: Germania e Austria ci sono (giro 8 di «Pietre miliari», 17 novembre, con lo specchio di
   openstreetmap.fr; file in `pietre` alle 14:46 UTC, tutti i 17 paesi). La Croazia quel giro è fallita (Geofabrik non
   risponde, sullo specchio il file non c'è): resta il file del 15 novembre, si rifà al giro mensile. In Italia le pietre
   in OpenStreetMap sono poche: il km compare soprattutto in autostrada.
5. In attesa di una fonte o di una decisione: segnalazioni brevi condivise (serve un servizio comune); traffico (nessuna
   fonte libera nazionale); nei viaggi lontani prezzi di treni e aerei, pedaggi, auto + treno, Cose da fare e «Il mio
   viaggio».
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

## Nota dall'area Alfred (17 novembre)
- Battute di Alfred in guida: `GuideWit`/`EuriseWit` in `maps/android/…/eurise/`, una riga in `eurise_patch.py`
  (NavigationController) e in `EuriseCarGuide.refresh`, `EuriseSpeak.busy()`, stringhe `eurise_wit_*`, prova
  `GuideWitTest` in `maps-engine.yml`. Dettagli in `docs/stato/ALFRED.md`.

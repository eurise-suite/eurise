# Stato dell'area Mappe

File di stato dell'area, tenuto dalle chat del progetto «Eurise · Mappe» (dal 16 novembre 2026). `docs/STATO.md` resta il
diario generale: lì si scrive solo una riga per consegna che rimanda qui. Regole in `CLAUDE.md`, sistemi in
`docs/PIATTAFORME.md`. Ramo `brand`; giri propri su `prova-mappe`.

## Cosa comprende
Mappe (APK CoMaps), guida, Android Auto, Meteo, segnalazioni OSM, Mappe sul computer.

## Fatto (in breve, al 16 novembre)
- **Mappe in stile suite** su CoMaps (patch in `maps/eurise_patch.py`, codice in `maps/android/…/eurise/`, guida in
  `maps/EURISE.md`): Esplora (orizzonte col meteo in alto, scorciatoie, «Dove sei»), Attività e Cerca, ricerca a pillola,
  Livelli e fonti, scheda del luogo e scheda aperta (con gli **orari nello stile della tavola**), barra in orizzontale,
  avvio con l'icona di Eurise Mappe.
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
- **PcMappe** (Mappe sul computer, con CoMaps) approvata il 16 novembre: codice ancora da scrivere (con l'area Computer).

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
- **Dettatura in Android Auto che non funziona**: chiesto al proprietario quale microfono (il tasto di Alfred nostro o
  quello della tastiera dell'auto nella ricerca) e cosa compare.
- **Tavole nuove** (pagina Mappe della tela, fila «Mappe dall'apertura alla partenza», da discutere): MapViaggio1Apertura
  (com'è oggi), 2 Dove devi andare (campo in basso con la dettatura), 3 Luogo (Indicazioni coi minuti, Parti ora),
  4 Percorso (partenza e arrivo in vetro al posto della barra di CoMaps con i tipi di percorso; Evita e + Tappa in
  pillole; mezzi coi minuti), 5 Tappe (si trascinano, benzinaio e bar lungo la strada), 6 Bici e a piedi (piste,
  dislivello, scelte). Oggi in alto nel percorso c'è ancora la barra di CoMaps (indietro, auto/pedone/mezzi/bici/righello,
  opzioni): è la parte da adattare.

## Prossimi passi, in ordine
1. **Prove del proprietario**: Mappe 60 (orari della scheda aperta; Livelli → «Km sulle strade»; km in auto su autostrada).
   Restano senza esito Mappe 48 e 51 («Avvia la navigazione» dopo essere usciti durante il calcolo, «Dove sei») e Mappe 58.
2. Prossimo giro di Mappe su `brand`: porta la spiegazione corretta del km («di solito meno di 1 MB»; Mappe 60 dice
   «qualche MB»).
3. **Pietre miliari**: Germania e Austria mancano (Geofabrik rifiuta i runner di GitHub dopo i primi download; ora c'è lo
   specchio di openstreetmap.fr). In Italia le pietre in OpenStreetMap sono poche (8.672): il km compare soprattutto in
   autostrada. Scelta del proprietario: spostare i file su un server europeo (Codeberg o nostro) per più tacchette.
4. Passo f) di Esplora, parte che resta: nomi delle vie più leggibili; orizzonte col cielo anche esplorando.
5. Km anche sul telefono («Dove sei» o guida), solo se il proprietario lo vuole: serve una riga nella tavola.
6. **Mappe sul computer** (tavola PcMappe, CoMaps su Linux), insieme all'area Computer.
7. In attesa di una fonte o di una decisione: segnalazioni brevi condivise con gli altri (serve un servizio comune);
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
  = ultimo riuscito su `brand`); poi in chat i link diretti, per esempio
  https://github.com/eurise-suite/eurise/releases/download/apk-prova/eurise-mappe-debug.apk e, se cambia il centro
  privacy, anche https://github.com/eurise-suite/eurise/releases/download/apk-prova/app-debug.apk.
- Servizi nuovi che fanno uscire dati: `PrivacyService` in core/suite (con `SHARED` in `PrivacyContract`), testi in
  `ServiceConsent`, interruttore in Livelli con la spiegazione prima di accendere; servono APK di Eurise e di Mappe nuovi.
- Storia completa: sezioni datate di `docs/STATO.md` dall'11 al 15 novembre (Mappe, Android Auto, guida).

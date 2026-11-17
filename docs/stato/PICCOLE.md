# Stato dell'area Password, Tastiera, Galleria

File di stato dell'area, tenuto dalle chat del progetto «Eurise · Password, Tastiera, Galleria» (dal 16 novembre 2026). `docs/STATO.md` resta il
diario generale: lì si scrive solo una riga per consegna che rimanda qui. Regole in `CLAUDE.md`, sistemi in
`docs/PIATTAFORME.md`. Ramo `brand`; giri propri su `prova-piccole`.

## Cosa comprende
Le tre app separate: Eurise Password (anche Famiglia e I miei dati), Tastiera (barra di Eurise, password, codici) e
Galleria (foto, video, editor). Voce e Alfred sono dell'area Alfred; Leggi e Scanner di Organizzazione.

## Fatto (in breve, al 16 novembre)
- **Password** (`password/`, APK `eu.eurise.password`, Android 8+): cassaforte KeePass `.kdbx` sul telefono (KDBX 4 scritto
  da noi, senza librerie, riaperto da pykeepass nella compilazione), sincronizzazione con Murena via WebDAV spenta di base,
  unione voce per voce; impronta, blocco dopo 2 minuti, appunti cancellati dopo 45 s; codici 2FA, generatore, deboli e
  riusate, controllo violazioni (Have I Been Pwned, spento di base); carte con NFC e fotocamera (tramite Eurise Leggi);
  alias SimpleLogin, addy.io e indirizzo fisso; importazione da Proton Pass, Bitwarden, Aegis, 2FAS, KeePass, CSV, QR di
  Google Authenticator; passkey (Android 14+, provate su webauthn.io il 9 novembre); compilazione automatica di Android con
  antifrode sul dominio. **I miei dati** (PwdDati, PwdDatiCompila): profili personali e professionali nella cassaforte, che
  compilano i moduli. Con la suite: riceve voci dalle altre app (`PasswordContract`, Chat, Email), accessi istantanei
  nel Browser (`LoginsProvider`, «Sblocca e accedi»), import dal Browser senza file, «Password per sito: approva» dal
  computer col Ponte. Ricerca in basso, stato della sincronizzazione sotto il titolo. Icona nuova: riccio-chiave (16 novembre).
- **Tastiera** (`keyboard/`, HeliBoard 4.2, APK `eu.eurise.keyboard`, senza Internet): temi Perla/Notte e chiaro/scuro come la
  Scrivania; barra di Eurise sopra i suggerimenti con sette comandi (Calcola, Codici, File, Persona, Libero, Traduci, Cerca)
  più «Altro» con gli strumenti di HeliBoard; nei campi password niente parole né appunti; le nove funzioni in più
  (/comandi, calcoli, @contatti, abbreviazioni, «Crea attività», arrivo alle…, lingua per app, nomi della Rubrica, tablet);
  scrittura scorrendo libera (`GlideDecoder`); microfono con Eurise Voce; ricerca rapida web/AI con le tacche; Traduci col
  motore dentro Eurise Leggi. Precisione: isteresi 16 dp, `TouchFix` (fascia sopra lo spazio che conta come spazio, si regola
  da sola), `TouchOffset` (scarto medio dei tocchi), spazio tra i tasti 0,7, altezza 1,10, migrazioni di HeliBoard saltate
  (`AppUpgrade.kt`). Icona nuova colorata (16 novembre).
- **Galleria** (`gallery/`, APK `eu.eurise.gallery`, Android 11+, senza Internet e senza librerie per le immagini): foto per
  giorno con `ScrubRail`, Album, Luoghi, Cerca, Accadde oggi, diario, eliminazione a tre scelte con cestino di sistema,
  foto «solo cloud», caricamento su Murena/Nextcloud (`UploadWorker`, solo Wi-Fi, spento di base), cartella protetta,
  pulizia. **Video** (Media3 ExoPlayer solo motore): gesti, miniature, velocità, fotogramma come foto, Taglia senza
  ricodifica, finestra sopra le altre app. **Editor** su copia: ritaglio e proporzioni, Raddrizza, luce/ombre/alte luci,
  vignetta, Automatico, filtri, scritte, pennarello; «Il tuo colore» comune in `core/designsystem`. «Salva in…» di File dal
  foglio Condividi. Icona nuova colorata (16 novembre).

## 16 novembre, sera: modello del tocco per tasto e parole imparate nel backup (giro 663, in apk-prova)
- **Modello del tocco per tasto** (`keyboard/.../KeyModel.kt`, logica pura con `KeyModelTest`; `TouchModel.kt` la collega
  alla tastiera): per ogni lettera punto medio e dispersione dei tocchi, in quote del tasto. Impara da parole tenute, parole
  corrette della stessa lunghezza (proposta scelta o correzione automatica non annullata: il tocco sbagliato insegna la
  lettera giusta) e «lettera, cancella, lettera vicina». Con pochi esempi resta prudente (media di tutti i tasti, limiti
  0,15 in larghezza e 0,20 in altezza). Salvato in `eurise_touch` (`key_model_1`).
- **Correzioni con il dizionario**: da 150 esempi il motore di HeliBoard riceve punto preferito e raggio di ogni lettera
  (`ProximityInfo`; prima la correzione della posizione era spenta), così proposte e correzione automatica col dizionario
  italiano tengono conto di come scrive la persona. Le tastiere si ricostruiscono ogni 200 esempi nuovi, a fine scrittura.
- **Scelta del tasto sul bordo** (`KeyDetector`): il modello cambia la lettera solo se conosce bene entrambi i tasti
  (40 esempi l'uno) e quella accanto spiega il tocco circa tre volte meglio. `TouchOffset` e `TouchFix` restano come prima.
- **Backup**: `BackupProvider` della Tastiera (permesso della suite) dà le parole imparate (UserHistoryDictionary), il
  dizionario personale e il modo di toccare; l'app Eurise li mette nel backup cifrato (`core/cloud` `KeyboardBackup`,
  voce `apps/keyboard.zip`) e al ripristino li riconsegna, anche se la Tastiera si installa dopo. Testo «Cosa viene letto»
  del backup aggiornato. **Tocca anche l'app Eurise**: serve `app-debug.apk` nuovo.
- **Frasi abituali**: già attive in HeliBoard (parole imparate fino a quattro di fila, previsioni della parola dopo). Una
  proposta di frase intera nella fila dei suggerimenti sarebbe un'aggiunta all'interfaccia: prima una tavola.

## 16 novembre, notte: zoom nell'editor della Galleria (giro 665, in apk-prova), tavola della barra a comparsa
- **Galleria, editor**: con due dita si ingrandisce (fino a 6 volte) e si sposta la foto mentre la si modifica; un dito
  resta per ritagliare, scrivere e disegnare (i gesti degli strumenti lavorano nelle coordinate della foto, quindi non
  cambiano). Tornando sotto 1,05 la foto torna intera. Richiesta del proprietario, senza tavola (è un gesto).
- **Tastiera, impressione del proprietario sul giro 663**: «migliorata notevolmente»; la precisione si può spingere ancora.
- **Tavola nuova, da approvare** (pagina Integrazioni, fila a y 4200; toccato `canvas.json` della tela): la barra di Eurise
  non sta più sempre sopra le parole. Un tasto con la stella sulla fila delle parole (lato della mano dominante) apre sopra
  le parole un riquadro con i comandi di Eurise (Calcola, Codici, File, Persona, Libero, Traduci, Cerca) e gli strumenti
  della tastiera (Appunti, Emoji, Cursore, Una mano, Annulla, Dettatura, Impostazioni); le proposte (Crea attività, codici,
  abbreviazioni) entrano tra le parole come prima voce e un pallino sul tasto avvisa. Tavole: TastComparsaChiusa,
  TastComparsaAperta, TastComparsaProposta. Proposta del proprietario.

## 16 novembre, notte: barra di Eurise a comparsa (giro 675, in apk-prova)
- Tavole TastComparsaChiusa, TastComparsaAperta, TastComparsaProposta **approvate**; il proprietario ha chiesto gli
  strumenti della tastiera fatti come i comandi di Eurise (icona sopra, etichetta sotto, stessa larghezza): fatto anche
  sulla tavola (etichette corte: Voce, Opzioni).
- Codice (`EuriseBar.kt`): la fila dei comandi non c'è più; il tasto della fila delle parole di HeliBoard
  (`suggestions_strip_toolbar_key`, lato della mano) diventa la stella (`eurise_ic_star`) e apre il riquadro, messo nella
  finestra della tastiera (`android.R.id.content`, come i tasti a comparsa) così copre l'app senza spostarla; `LatinIME.
  onComputeInsets` allarga l'area toccabile solo mentre è aperto. Due righe: comandi di Eurise e strumenti (CLIPBOARD,
  EMOJI, DPAD, ONE_HANDED, UNDO, VOICE, SETTINGS di HeliBoard; pressione lunga come in HeliBoard). Si chiude scegliendo,
  ritoccando la stella, scrivendo, cambiando campo.
- Proposte: la prima nella fila delle parole (`eurise_proposal` in `suggestions_strip.xml`), le altre in cima al riquadro;
  pallino arancio sulla stella se ce n'è più d'una. «Altro» (`ToolsPanel`) non è più nei comandi; tolta dalle impostazioni
  la voce «barra sopra i suggerimenti».
- Prova del proprietario sul giro 675: «funziona tutto». Poi, da una foto, nuovo ordine della riga Tastiera: Opzioni,
  Una mano, Emoji, Cursore, Annulla, Voce, Appunti (giro 690, in apk-prova; fatto anche sulla tavola TastComparsaAperta). Nel giro 690 è fallito
  il Flatpak di Eurise, che non dipende da quest'area.
- Da provare sul telefono: posizione del riquadro sopra le parole (anche con una mano e tastiera flottante), tocchi sul
  riquadro, pallino, Voce e Cursore dal riquadro.

## 17 novembre: niente tendina sulle parole sbagliate (giro 704, in apk-prova)
- Il proprietario: toccando una parola sottolineata, l'app (Vivaldi) apriva la tendina delle proposte del controllo
  ortografico, nascondeva la tastiera e obbligava a scegliere o cancellare la parola. Ora di base il controllo ortografico
  della Tastiera sottolinea soltanto (`PREF_SPELLCHECK_SUGGEST` spento di base, `RESULT_ATTR_DONT_SHOW_UI_FOR_SUGGESTIONS`,
  Android 12 e oltre); si riaccende in Correzione del testo. Le correzioni restano nella fila delle parole.
- Foto del proprietario: il controllo ortografico di sistema era «Android (AOSP)», perché quello di Eurise era spento nel
  manifest dal 13 novembre (stesso problema). Riacceso (giro 711, in apk-prova): ora sottolinea soltanto. Il proprietario lo
  sceglie in Impostazioni › Lingue e immissione › Controllo ortografico › Controllo ortografico predefinito, oppure spegne
  «Usa il controllo ortografico» (nessuna sottolineatura, nessuna tendina). La tastiera non può cambiarlo da sola.

## 17 novembre: editor completo della Galleria, tavole da approvare
- Richiesta del proprietario: nell'editor tutte le funzioni che si trovano in Snapseed. Sono funzioni comuni degli editor
  fotografici: si rifanno con disegno e codice nostri, nello stile della suite. Sei tavole nuove sulla pagina Galleria,
  fila a y 2488 (toccato `canvas.json` della tela): GalRegolaTutto (Luce, Colore con bilanciamento del bianco e
  contagocce, Dettagli con struttura e nitidezza, Curve, Automatico), GalCurve, GalRitocca (Selettivo a punti, Pennello,
  Correttivo, Ritratto), GalProspettiva (Ruota, Prospettiva, Espandi con bordi riempiti), GalEffetti (HDR, Drammatico,
  Bagliore, Contrasto tonale, Vintage, Grana, Retrò, Grunge, Bianco e nero, Noir, Sfocatura, Doppia esposizione,
  Ritratto, Cornici), GalModifiche (pila dei passi: riapri, togli, pennello per passo, salva come look, copia su altre foto).
  Barra in basso: Ritaglia, Regola, Ritocca, Effetti, Testo, Disegna.
- Fuori: «posa della testa» (serve un modello AI del viso); Ritratto usa il riconoscimento dei volti di Android
  (`android.media.FaceDetector`, senza librerie Google). Correttivo e «Riempi in modo intelligente» con un algoritmo nostro
  sul telefono (riempimento a toppe), senza modelli scaricati.

## 17 novembre: editor completo, primo giro (giro 722, in apk-prova)
- Tavole dell'editor **approvate** dal proprietario con due aggiunte: Vignettatura e «le tue modalità» (una regolazione
  fatta su una foto si salva e si riusa sulle altre). Tavola GalEffetti aggiornata con entrambe.
- Ordine dei giri: 1 Regola e Curve (fatto) · 2 Ritaglia (Ruota, Prospettiva, Espandi) · 3 pila delle Modifiche ·
  4 Ritocca (Selettivo, Pennello, Correttivo, Ritratto) · 5 Effetti (con Vignettatura con centro e grandezza).
- Codice: `logic/Develop.kt` (puro, `DevelopTest`): curve monotone (luminosità e colori, curve pronte), atmosfera,
  vividezza, tinta (nella matrice), struttura e nitidezza (contrasto locale sulla luminosità), bianco giusto (`neutralize`),
  `LookCodec` per le modalità tue. `Adjust` ha i campi nuovi; `EditState.mine` per una modalità tua.
  Anteprima: lavoro sui pixel su una copia dopo 70 ms, matrice sulla GPU; copia salvata con `developBitmap` a fasce
  sovrapposte, la matrice dopo i pixel come nell'anteprima. Modalità tue in `GalleryStore` (`my_looks`).
- Interfaccia: Regola con Luce (Atmosfera, Vignettatura), Colore (Vividezza, Tinta, «Bianco giusto: tocca un grigio»),
  Dettagli (Struttura, Nitidezza), Curve disegnate sopra la foto (tocco aggiunge, trascina sposta, fuori toglie).
  Filtri: «Salva la tua» prima di tutto, poi le tue, poi quelle della suite; pressione lunga: rinomina, elimina.
  La barra in basso resta quella di oggi finché non arrivano Ritocca ed Effetti.

## 17 novembre, mattina: editor, secondo giro (giro 726, in apk-prova) e tavole in più
- Ritaglia ha tre parti: Ritaglia (Ruota, Specchia, Raddrizza, proporzioni), Prospettiva (Verticale e Orizzontale coi
  cursori, oppure Libera trascinando i quattro angoli sulla foto non raddrizzata), Espandi (Quanto; Continua la foto,
  Sfocato, Bianco, Nero). Logica in `logic/Frame.kt` (`FrameTest`); `Files.perspective` (setPolyToPoly) dopo rotazione e
  specchio, `Files.expanded` alla fine dopo il ritaglio. Nell'anteprima Espandi è calcolato su una copia piccola attorno
  alla foto intera (col ritaglio attivo la copia salvata ha i bordi attorno alla parte ritagliata).
- Richiesta del proprietario: ogni altra funzione dell'editor di riferimento. Già sulle tavole quasi tutte; in più due
  tavole da approvare: GalEsporta (Salva: copia nuova o Sostituisci con l'originale tenuto 30 giorni; formato JPEG, PNG,
  WebP; qualità; dimensione; togli la posizione, di base acceso; togli i dati della fotocamera) e GalLookQR (una tua
  modalità condivisa con un codice QR o come testo: solo regolazioni, nessun dato; dati della foto). Resta fuori solo
  la posa della testa (serve un modello AI del viso).

## 17 novembre: editor, terzo giro (giro 730, in apk-prova)
- GalEsporta e GalLookQR **approvate**.
- Pila delle Modifiche (GalModifiche): pulsante in alto con il numero dei passi; foglio con i passi (Ritaglia, Prospettiva,
  Espandi, Regola, Curve, Filtro, Testo, Disegno), Riapri nel suo strumento, Togli senza perdere gli altri, «Salva come
  tua modalità». Il pennello per passo arriva con Ritocca.
- Foglio Salva (GalEsporta): copia nuova o Sostituisci (`MediaStore.createWriteRequest`; l'originale in `files/originali`
  per 30 giorni, elenco in `GalleryStore.kept`, tolti all'avvio da `dropExpiredOriginals`; nel visore «Torna
  all'originale» con i giorni che restano); JPEG, PNG, WebP; qualità 50–100; grandezza originale, 2048, 1080; posizione
  tolta di base; dati della fotocamera a scelta (`ExifInterface` di Android, orientamento sempre normale); Condividi senza
  salvare. Scelte ricordate tranne Sostituisci. Logica in `logic/Save.kt` (`SaveTest`).
- Giro 728 fallito per gli apostrofi non protetti nei testi nuovi (corretto: `\'`).

## Tavole
Tela: https://claude.ai/artifact/AauRJ8e5WdH7nXTqAEXkoo (pagine Password, Integrazioni, Galleria).
- **Approvate e in codice**:
  - Password: PwdCassaforte, PwdVoce, PwdRegistra, PwdCarta, PwdAlias (9 novembre), PwdDati, PwdDatiCompila (10 novembre).
  - Tastiera (pagina Integrazioni): TastBarra, TastDisponibilita, TastFile, TastCodici, TastCalcolatrice, TastCerca,
    TastRighe, TastPassword (la fila «Compila · account» c'è nel disegno, ma Password non manda ancora le proposte in linea:
    vedi Prossimi passi).
  - Galleria: Galleria, GalFoto, GalElimina, GalAccadde; GalVideo, GalRitaglio, GalRegola, GalTesto, ColoreTuo (seconda fila,
    11 novembre: scritte in codice subito, su richiesta del proprietario di non aspettare le conferme).
  - Senza tavola, nello stile della suite: Luoghi, Cerca, diario, cartella protetta, pulizia della Galleria; importazione di
    Password.
- **Approvate il 16 novembre, notte, e in codice**: TastComparsaChiusa, TastComparsaAperta, TastComparsaProposta.
- **Approvate il 17 novembre**: GalRegolaTutto, GalCurve (in codice), GalRitocca, GalProspettiva, GalEffetti, GalModifiche.
- **Approvate il 17 novembre**: GalEsporta (in codice), GalLookQR.
- **Da approvare**: PwdFamiglia, PwdFamigliaEntra, PwdFamigliaInvita (Famiglia rimandata dal proprietario in fondo alla road map).
- **Da rifare**: le tavole di quest'area con le tacche vecchie della privacy (ora 10 tacchette a semaforo).

## Prossimi passi, in ordine
0. **Editor della Galleria, giri successivi**: Copia e incolla delle modifiche su altre foto, modalità col codice QR o come
   testo (zxing-core, già nel catalogo della suite) e dati della foto; poi Ritocca (con il pennello per passo); poi Effetti. Prova del proprietario sui giri 722 (Regola, Curve, le tue
   modalità), 726 (Prospettiva, Espandi) e 730 (Modifiche, Salva, Torna all'originale).
   Prova del proprietario sul giro 675 (Tastiera: barra a comparsa) e sul giro 665 (Galleria: zoom nell'editor).
1. **Prova del proprietario sul giro 663** (Tastiera e app Eurise): scrivere qualche giorno come sempre. Il modello entra in
   gioco dopo circa 150 lettere imparate; le correzioni col dizionario si aggiornano alla tastiera ricostruita (ogni 200
   esempi, a fine scrittura). Dire se le lettere accanto escono ancora, se le correzioni sono migliori o peggiori, e
   provare un backup con la Tastiera installata. Ultime correzioni ancora senza esito: `TouchOffset`, migrazioni saltate,
   spazio tra i tasti.
2. **Password → Tastiera, «Compila · account» in linea** (TastPassword approvata): Password non dà ancora
   `InlinePresentation` nella compilazione automatica, quindi la fila della Tastiera nei campi password resta vuota.
3. **Tastiera fuori stile**: schermate delle impostazioni ancora di HeliBoard (tavola prima del codice). Con l'occasione
   «Ricomincia» per il modo di toccare (`TouchModel.reset`, oggi senza pulsante).
4. **Frase intera nella fila dei suggerimenti** (frasi abituali visibili): tavola sulla pagina Integrazioni, poi codice.
5. **Privacy di quest'area**: semaforo a 10 tacchette in Password (Have I Been Pwned, alias, Murena) e Galleria
   (OpenStreetMap per i luoghi, caricamento); Password e Galleria comandabili dal centro privacy e nel cestino unico
   (serve il permesso di firma con l'app Eurise; tocca anche `app-debug.apk`).
6. **Galleria, resti**: impronta per la cartella protetta, luogo nella scheda Accadde oggi, esportazione del diario,
   «Apri da…» di File.
7. **Pulizia del codice**: `PasswordApp` (1355 righe) da dividere; componenti copiati (Chip, Card, Pill, SwitchRow) da
   portare in `core/designsystem` (parte comune: scriverlo qui).
8. **In fondo**: Password Famiglia (tavole da approvare); kMail tra gli alias (API non verificata); kDrive (bloccato da
   Infomaniak); scrittura a mano nella Tastiera (rimandata: nessun modello libero).
- **Da provare sul telefono, senza esito scritto**: carte con NFC e fotocamera, I miei dati che compilano i moduli,
  accessi istantanei nel Browser, video ed editor della Galleria, caricamento su Murena.

## Come si lavora
- Codice: `password/` (modulo `:password`; `kdbx/`, `autofill/`, `passkey/`, `logic/`), `gallery/` (modulo `:gallery`;
  `data/`, `logic/`, `ui/`), `keyboard/` (progetto Gradle a sé, HeliBoard; codice nostro in
  `app/src/main/java/eu/eurise/keyboard/`, agganci elencati in `keyboard/EURISE.md`). Contratti con la suite in
  `core/suite` (`PasswordContract`, `CardContract`, `FilesContract`): se cambiano, cambia anche `app-debug.apk`.
- Prima le tavole sulla tela (pagine Password, Integrazioni, Galleria), poi il codice dopo l'approvazione.
- Prove senza telefono: `KeyModelTest` e le altre `eu.eurise.*` della Tastiera; `KdbxTest`, `EmvTest` in Password (più la riapertura con pykeepass in `build.yml`); `GalleryLogicTest`,
  `TimelineTest`, `EditToolsTest`, `VideoTimeTest` in Galleria; prove di HeliBoard nella Tastiera.
- Compilazione: «Compila APK» (`build.yml`) parte a ogni push. Prima di spingere `git fetch` e rebase su `origin/brand`
  (altre chat lavorano sullo stesso ramo); poi la stessa testa anche su `prova-piccole` e si pubblica quel giro:
  `gh workflow run apk-prova.yml --ref brand -f run_id=<id del giro su prova-piccole>`.
- In chat sempre i link diretti degli APK cambiati, con nome e giro (mai la pagina della release né allegati):
  - Password: https://github.com/eurise-suite/eurise/releases/download/apk-prova/password-debug.apk
  - Tastiera: https://github.com/eurise-suite/eurise/releases/download/apk-prova/EuriseTastiera-debug.apk
  - Galleria: https://github.com/eurise-suite/eurise/releases/download/apk-prova/gallery-debug.apk
  - Se cambia `core/suite`: https://github.com/eurise-suite/eurise/releases/download/apk-prova/app-debug.apk
- Parti comuni toccate: `canvas.json` della tela (fila dell'editor sulla pagina Galleria) il 17 novembre; `canvas.json` (fila TastComparsa) il 16 novembre, notte; `core/cloud` (backup: `KeyboardBackup`, `queries` nel manifest) il 16 novembre, sera.
- Ogni servizio che fa uscire dati è spento di base e compare nel centro privacy; servizi chiusi per ultimi con avviso.
- Parti comuni (`core/designsystem`, `core/ponte`, `CLAUDE.md`, `canvas.json` della tela): si toccano solo se serve e si
  scrive qui.
- A ogni consegna: aggiornare questo file e una sola riga in `docs/STATO.md`.
- Storia completa: sezioni datate di `docs/STATO.md` dall'8 al 16 novembre (Password e Galleria dall'8–9 novembre; Tastiera
  dal 10 novembre; carte e Galleria seconda fila l'11 novembre; Password con Browser e Chat il 12 novembre).

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

## 17 novembre, sera: Tastiera precisa da subito (giro 758, in apk-prova)
- Proprietario: tasti della grandezza giusta; errori meno di prima ma gli stessi (lettera vicina, lettera al posto dello
  spazio e viceversa); serve precisione alta dal primo uso. Proposta sua: la lettera premuta in un piccolo riquadro.
- `CharModel.kt` (puro, `CharModelTest`): probabilità di lettera o spazio dopo le due precedenti, dal dizionario
  principale della lingua (`BinaryDictionary.getNextWordProperty`, parole pesate per frequenza), calcolata una volta in un
  thread a parte e salvata in `files/charmodel_<lingua>.bin`.
- `KeyModel.chooseWith`: sul bordo tra tasti, o tra lettera e spazio, decide tocco + lettere precedenti (peso 0,8,
  vantaggio richiesto 0,35); al centro vince sempre il tocco; lo spazio conta per l'altezza. Attivo dal primo tocco
  (forma prudente dei tasti finché non impara), solo nei campi di testo non password; in quei campi sostituisce
  `TouchFix`. Le lettere della parola in corso: `TouchModel.word` (anche dal testo prima del cursore, Android 11+).
- La lettera premuta compare sopra il dito di base su tutti i telefoni (`Defaults.PREF_POPUP_ON = true`).
- Nel giro 758 è fallito solo il Flatpak di Eurise (non di quest'area).

## 17 novembre, notte: editor, quarto giro (giro 773, in apk-prova)
- Copia modifiche nella pila (`GalleryStore.copiedEdit`, l'effetto complessivo come `Adjust`); nella Galleria, con più
  foto scelte, «Incolla modifiche» salva una copia modificata di ognuna con le ultime scelte del foglio Salva.
- Modalità tue condivise: `LookShare` (`eurise-look:` + base64 delle sole regolazioni, test in `DevelopTest`); codice QR
  con zxing-core (`Files.qrBitmap`), «Manda come testo»; «Ricevi» tra i filtri: da una foto o schermata col codice
  (`Files.readQr`, sul telefono) o dagli appunti.
- Dati della foto nel visore (`Files.photoInfo`): quando, nome, grandezza, peso, fotocamera, scatto, posizione arrotondata.
- Giri 767 e 769 falliti: un campo EXIF che esiste solo in androidx (corretto) e poi un errore della Scrivania su
  `brand`, corretto da quella chat.

## 17 novembre, notte: editor, quinto giro: Ritocca (giro 779, in apk-prova)
- Nuovo strumento «Ritocca» nella barra in basso, fra Regola e Filtri (`logic/Retouch.kt`, test in `RetouchTest`):
  - Selettivo: si tocca la foto e nasce un punto che cambia luce, contrasto, saturazione e calore dei colori simili
    attorno a sé (ampiezza regolabile); si sposta trascinandolo, «Togli il punto».
  - Pennello: Schiarisci, Scurisci, Più/Meno calore, Più/Meno colore, Gomma; grandezza e intensità; «Vedi le pennellate».
  - Correttivo: si tocca una macchia e si copre con la zona vicina più simile (16 direzioni, raccordo del colore).
- I ritocchi stanno sulla foto prima di raddrizzarla e prima delle regolazioni, sia nell'anteprima sia nella copia salvata
  (`Files.retouchBitmap`, a fasce). Nella pila compare il passo «Ritocca».
- Prossimo: Ritratto (FaceDetector di Android) e il pennello per ogni passo della pila; poi Effetti.

## 18 novembre: editor, sesto giro: Ritratto e pennello per passo (giro 787, in apk-prova)
- Ritocca ha la quarta parte, **Ritratto** (`logic/Portrait.kt`, `PortraitTest`): volti trovati sul telefono con
  `android.media.FaceDetector` (`Files.detectFaces`, su una copia di 1024 px) o toccati a mano tra gli occhi; ovale
  spostabile e con grandezza; Luce sul viso (volto più chiaro, attorno più scuro), Pelle liscia (solo colori di pelle e
  senza bordi: occhi, bocca, capelli restano nitidi), Occhi (più chiari e nitidi). Nella copia salvata su un ritaglio attorno
  a ogni volto; la luce a fasce.
- **Pennello per passo** (`logic/StepMask.kt`, `StepMaskTest`): nella pila Regola, Curve e Filtro hanno il pennello;
  si dipinge dove valgono (100, 75, 50, 25 %, Gomma, Inverti, Ovunque), zone dipinte in rosso. Per ora **una maschera sola
  per i tre passi** (`EditState.toneMask`): separarli vorrebbe dire sviluppare la foto più volte. Gli Effetti avranno
  ciascuno la sua. Con la maschera l'anteprima calcola anche la matrice sulla copia; nella copia salvata la maschera si
  riporta sulla foto raddrizzata (`blendMasked`).
- Giro 783 fallito per un nome in conflitto nel disegno dell'ovale (corretto).

## 18 novembre: editor, settimo giro: Effetti (giro 797, in apk-prova)
- Barra in basso come sulle tavole: Ritaglia, Regola, Ritocca, **Effetti**, Testo, Disegna. In Effetti la prima fila sono
  gli effetti (miniature fatte sulla foto), sotto «Salva la tua», «Ricevi», le tue modalità e i filtri della suite.
- `logic/Effects.kt` (`EffectsTest`, più un foglio di prova visivo fatto in locale): HDR (Naturale, Persone, Fine, Forte),
  Drammatico (Chiaro 1–2, Scuro 1–2), Bagliore, Contrasto tonale (alte luci, toni medi, ombre), Vintage, Grana (Neutro,
  Caldo, Freddo, Seppia), Retrò (luce che filtra da un angolo), Grunge, Bianco e nero (filtri Rosso, Arancio, Giallo, Verde,
  Blu), Noir (con lavaggio), Sfocatura (ellittica o lineare), Doppia esposizione (seconda foto scelta dalla Galleria;
  Normale, Schiarisci, Scurisci, Somma, Moltiplica, Sovrapponi), Vignettatura (fuori, dentro, grandezza), Cornici (Bianca,
  Nera, Arrotondata, Sfumata, Pellicola). «Ritratto» tra gli effetti apre Ritocca › Ritratto.
- Ogni effetto è un passo della pila (`EditState.effects`) con stili, cursori, «Pennello» (maschera sua), «Togli»; la
  Sfocatura e la Vignettatura hanno il centro da trascinare sulla foto. Posizioni in quote del riquadro finale
  (`EffectSpace`, anche con foto raddrizzata e ritagliata).
- Gli effetti che guardano attorno ai pixel usano una copia piccola (`Plane`, 512 px) sfocata: poco lavoro e stesso aspetto
  in anteprima e nella copia salvata; differenze forti smorzate per non fare aloni. Con effetti o pennello per passo
  l'anteprima calcola tutto sulla copia (niente matrice sulla GPU). Nella copia salvata a fasce (`Files.effectsBitmap`).
- Le tue modalità e «Copia modifiche» portano solo le regolazioni, non gli effetti (come prima).

## 18 novembre: Password in linea nella Tastiera, resti della Galleria, Password divisa, tavole nuove (giro 822, in apk-prova)
- **Password → Tastiera** (TastPassword): con Android 11+ la compilazione automatica dà anche le proposte in linea
  (`autofill/InlineRows.kt`, `InlinePresentation` per ogni voce e per «Apri Password»). La Tastiera di HeliBoard le chiedeva
  già: ora la fila sopra i tasti mostra gli account del sito o dell'app, con la chiave. Libreria nuova `androidx.autofill`
  1.3.0 (Jetpack, aperta, già nella Tastiera), motivo nel commit. Giro 818 solo con questo.
- **Galleria**: impronta per la cartella protetta (`SecureFolder.bio*`: chiave della cartella cifrata con una chiave del
  Keystore legata all'impronta forte, annullata da un'impronta nuova; la frase resta; interruttore nella cartella aperta,
  permesso `USE_BIOMETRIC`); luogo del giorno nella scheda e nella pagina di Accadde oggi (`dayPlace`); diario come PDF
  (`data/DiaryPdf.kt`, `PdfDocument` del sistema, pulsante nel diario, poi Condividi o Salva in Eurise File); «Apri da File»
  negli Album (`FilesContract.openFrom`, apre nel visore di `OpenActivity`). Senza tavola, nello stile della suite.
- **Pulizia**: `PasswordApp.kt` diviso in `Common`, `Setup`, `VaultList`, `EntryScreens`, `SyncScreen`, `Import`,
  `Profiles` (stesso comportamento). Restano i componenti copiati da portare in `core/designsystem` (parte comune).
- **Tavole nuove, da approvare** (toccato `canvas.json` della tela): TastImpostazioniFrase (pagina Integrazioni, y 5600:
  impostazioni della Tastiera nello stile di Eurise, Come tocchi con Ricomincia, frase intera tra le parole),
  PwdPrivacyTacche (Password, y 2488), GalPrivacyTacche (Galleria, y 3732): servizi con le 10 tacchette, spenti di base,
  foglio «prima di accendere». Valutazioni proposte: Murena per Password 9, controllo violazioni 9, addy.io e SimpleLogin 7,
  Luoghi 7, caricamento su Murena 6, tutto ciò che resta sul telefono 10.
- **Tela piena** (512 file, il massimo): per fare posto ho riunito tavole già approvate e in codice dell'area,
  TastComparsaChiusa/Aperta/Proposta in TastComparsaAperta e GalEsporta in GalLookQR. Le tavole nuove sono a tre e a due
  schermate per file. Per altre tavole servirà togliere qualcosa di superato (decide il proprietario).

## 18 novembre: tavole approvate in codice, tela ripulita (giro 830, in apk-prova)
- Approvate dal proprietario: TastImpostazioniFrase, PwdPrivacyTacche, GalPrivacyTacche.
- **Tastiera**: impostazioni nello stile di Eurise (`EuriseSettings.kt`: Scrivere, Precisione, Aspetto e mano, Barra di Eurise e
  dati, Privacy 10/10, «Tutte le impostazioni» con le schermate di HeliBoard); colori Perla e Notte in tutte le schermate di
  HeliBoard (`latin/utils/Theme.kt`, niente più colori del sistema). «Come tocchi» (rotta `eurise_touch`): mappa dei tocchi
  per tasto, interruttori in `eurise_touch` (`learn_on`, `char_lm_on`, `space_band_on`) e Ricomincia con conferma
  (`TouchModel.reset`, `TouchOffset.reset`). **Frase intera** (`PhraseModel` con `PhraseModelTest`, `Phrases`): si impara
  quando una frase finisce (punto, a capo) o il campo si svuota dopo l'invio; da 3 volte la frase compare per prima tra le
  parole; pressione lunga la toglie; file `eurise_phrases.txt` nella Tastiera (non ancora nel backup); interruttore
  `eurise_phrase_on`.
- **Password e Galleria**: schermata Privacy (Password: in cima alle impostazioni; Galleria: negli Album) con il livello
  attuale, i servizi spenti di base e «prima di accendere». Valutazioni: Murena 9, violazioni 9, alias 7, Luoghi 7,
  caricamento 6, il resto 10. Usano `PrivacyMeter` comune e il nuovo `PrivacyConsentSheet` in **core/designsystem** (parte
  comune toccata: componente nuovo e due testi `ds_pc_read`, `ds_pc_out`); sostituisce le vecchie finestre di avviso.
- **Resta**: le righe di Password e Galleria nel centro privacy e nel cestino unico dell'app Eurise (serve un provider con
  permesso di firma e tocca `app-debug.apk`); frasi della Tastiera nel backup.
- **Tela**: tolte le tavole superate dell'area (TastBarra, TastRighe, GalRegola); le tavole nuove segnate approvate.

## 21 novembre: editor della Galleria più veloce e ritaglio riparato; Password e Galleria nel centro privacy e nel cestino unico (giro 965, in apk-prova)
- **Editor, cose rotte** (dal proprietario: «lento, pachidermico, i cambiamenti non in tempo reale, il ritaglio non va»):
  - Ritaglio: dal giro 797 il ritaglio era tra le chiavi del gesto sulla foto, così il gesto ripartiva a ogni passo e il
    riquadro non si trascinava. Ora si legge dentro il gesto (anche il centro di Sfocatura e Vignettatura).
  - Cursori e ghiera: i gesti chiamavano le funzioni del primo disegno, che partivano dai valori di allora; muovere un
    cursore rimetteva com'erano gli altri della stessa sezione (anche Prospettiva ed Espandi). Ora `rememberUpdatedState`.
- **Editor, velocità**: prima ogni movimento avviava, dopo 70 ms, un calcolo sulla copia grande che non si fermava più,
  e se ne accumulavano molti. Ora un calcolo alla volta (`PreviewJob`, `renderPreview`) su una copia da 800 px mentre si
  trascina, la copia grande 220 ms dopo che la mano si ferma, in un lavoro a parte che non blocca i movimenti nuovi.
  Prospettiva sulla copia piccola durante il gesto, la grande fuori dal thread principale. La foto da modificare si legge
  già a 1600 px (`setTargetSize`; prima una foto da 4000 px restava a 2000). Misure della cornice sempre dalla copia
  grande, così passare dalla piccola alla grande non sposta nulla.
- **Centro privacy e cestino unico** (passo 5 dei prossimi): contratto nuovo `AppHubContract` in **core/suite** (parte
  comune; più `ServiceLog` con `ServiceLogTest` e le `queries` dei pacchetti di Password e Galleria nel manifesto).
  Fornitore `<pacchetto>.hub` in Password e Galleria (`data/HubProvider.kt`), permesso della suite nel manifesto e
  `SuiteAccess.callerAllowed` in `call`: è una porta d'ingresso nuova, da mettere nella prova sull'emulatore della chat
  Sicurezza (`prova-porte`).
  - Centro privacy dell'app Eurise: sotto «Altre app della suite» i servizi di Password (sincronizzazione, violazioni,
    addy.io, SimpleLogin) e della Galleria (Luoghi, caricamento) con le tacchette e lo stato. Spegnere da lì, senza
    conferme; accendere apre la privacy dell'app (ingresso `ACTION_OPEN` protetto: alias `AddFromSuite` di Password,
    `FromSuite` nuovo della Galleria), dove ci sono la spiegazione e il collegamento. I contatti di Password e Galleria
    (sincronizzazione, Have I Been Pwned, alias, Nominatim, caricamento) vanno nel loro registro e compaiono nel registro
    del centro privacy. Spenti da fuori, le schermate aperte di Password rileggono le scelte (`PwdHub.changes`).
  - Cestino unico: filtro Password. A cassaforte aperta le voci del cestino della cassaforte, con Rimetti ed Elimina per
    sempre (`Vault.binned`, `restore`, `deleteForever`, con test: un'eliminata per sempre resta tale anche dopo l'unione con
    un'altra copia); chiusa, solo quante sono (si ricorda il numero, mai i nomi) e «Sblocca», che apre Password e torna.
    «Svuota» non tocca le password né le foto. Foto e video nel cestino di Android arrivano dalla Galleria quando l'app
    Eurise non ha il permesso delle foto; Rimetti ed Elimina li chiede Android, come prima.
  - **Tocca l'app Eurise**: serve `app-debug.apk` nuovo, oltre a Password e Galleria.
- **Pulizia di Password**: le righe con interruttore delle impostazioni usano `SwitchRow` comune. Gli altri componenti
  copiati erano già passati a `GroupCard`, `SectionLabel`, `CardDivider`; restano propri solo pezzi di Password
  (`BackHeader`, `PillAction`, `RoundIcon`).
- Nel giro 965 l'APK della Tastiera all'inizio non si è compilato: Gradle non trovava il plugin di Kotlin 2.4.0 (errore
  di scaricamento, non del codice). Attenzione: una pubblicazione senza Tastiera la **toglie** da `apk-prova`. Rifatto il
  solo lavoro della Tastiera (riuscito) e ripubblicato il giro: in `apk-prova` ci sono tutti. Il giro 962 si era fermato su
  un errore della Chat, corretto da quella chat.
- Prima di cominciare ho controllato il lavoro della chat Sicurezza: `prova-sicurezza` è tutto dentro `brand`, l'editor
  non l'ha toccato nessun altro; niente librerie nuove (la verifica delle librerie blocca su `brand`).

## 22 novembre: frasi della Tastiera nel backup (giro 1011, in apk-prova)
- `BackupProvider` della Tastiera mette nello zip anche le frasi intere imparate (`phrases.txt`, da `Phrases.exportText`).
  Al ripristino si uniscono a quelle già sul telefono (`PhraseModel.merge`: per ogni frase il conto più alto, nessuna si
  perde; test `unireNonPerdeNulla`) e il file si scrive subito, perché poi la tastiera riparte.
- Testi: nelle impostazioni della frase intera «Restano sul telefono e nel backup cifrato»; in «Cosa viene letto» del
  backup dell'app Eurise «frasi abituali» (`feature/cloud`, solo il testo); KDoc di `KeyboardBackup` (`core/cloud`, solo
  commento). Lo zip per l'app Eurise resta lo stesso contenitore: serve `app-debug.apk` nuovo solo per il testo.
- Giri 981, 988 e 1007 fermati da altre aree (test e compilazione della Posta, poi l'Orologio dopo il passo 3 di Alfred:
  `now` mancante in `ClockReceiver`, timer in pausa). Dopo più di venti minuti senza correzione l'ho corretto io con una
  riga (`System.currentTimeMillis()`, come gli altri usi), commit «Orologio: compila di nuovo»: **area Alfred, da
  sapere**. Pubblicati Tastiera, Password, Galleria e app Eurise del giro 1011.
- Nel file di stato: segnati come fatti i passi 3, 4 e 6 (impostazioni e frase intera nel giro 830, resti della Galleria
  nel giro 822).

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
0. **Prova del proprietario sul giro 1011** (Tastiera e app Eurise): backup con la Tastiera installata, poi ripristino;
   le frasi abituali devono tornare.
   **Prova del proprietario sul giro 965**: editor della Galleria (ritaglio, cursori, velocità dell'anteprima con e senza
   effetti), centro privacy e cestino unico con Password e Galleria. Se l'anteprima è ancora lenta: `PROXY_SIDE` e
   `IDLE_MS` in `Editor.kt`.
   **Prova del proprietario sul giro 758** (Tastiera: lettere precedenti, lettera sopra il dito). Se serve, regolare
   `LM_WEIGHT` e `LM_MARGIN` in `KeyModel`.
   **Editor della Galleria**: tutte le tavole in codice (giri 722–797). Resta la prova del proprietario; possibili ritocchi
   dopo la prova (forza degli effetti, velocità dell'anteprima con molti effetti). Prova del proprietario sui giri 722 (Regola, Curve, le tue
   modalità), 726 (Prospettiva, Espandi) e 730 (Modifiche, Salva, Torna all'originale).
   Prova del proprietario sul giro 675 (Tastiera: barra a comparsa) e sul giro 665 (Galleria: zoom nell'editor).
1. **Prova del proprietario sul giro 663** (Tastiera e app Eurise): scrivere qualche giorno come sempre. Il modello entra in
   gioco dopo circa 150 lettere imparate; le correzioni col dizionario si aggiornano alla tastiera ricostruita (ogni 200
   esempi, a fine scrittura). Dire se le lettere accanto escono ancora, se le correzioni sono migliori o peggiori, e
   provare un backup con la Tastiera installata. Ultime correzioni ancora senza esito: `TouchOffset`, migrazioni saltate,
   spazio tra i tasti.
2. ~~Password → Tastiera in linea~~ fatto nel giro 818/822. Era: Password non dava ancora
   `InlinePresentation` nella compilazione automatica, quindi la fila della Tastiera nei campi password resta vuota.
3. ~~Tastiera fuori stile~~ fatto nel giro 830 (impostazioni nello stile di Eurise, «Ricomincia» in Come tocchi).
4. ~~Frase intera nella fila dei suggerimenti~~ fatto nel giro 830; nel backup dal giro 1011.
5. ~~Privacy di quest'area~~ fatta nei giri 830 e 965 (tacchette, centro privacy, cestino unico). Resta la prova sul
   telefono con Password, Galleria e app Eurise del giro 965. Frasi della Tastiera nel backup: giro 1011.
6. ~~Galleria, resti~~ fatti nel giro 822. Restano piccoli: gli originali tenuti 30 giorni (dopo «Sostituisci») nel
   cestino unico; una maschera per ciascun passo di Regola, Curve e Filtro (solo se serve: rallenta l'anteprima).
7. ~~Pulizia del codice di Password~~ fatta (giri 822 e 965): `PasswordApp` diviso, componenti comuni al posto delle copie.
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
- Parti comuni toccate: `core/suite` (`AppHubContract`, `ServiceLog`, `queries` nel manifesto) il 21 novembre; `canvas.json` della tela (fila dell'editor sulla pagina Galleria) il 17 novembre; `canvas.json` (fila TastComparsa) il 16 novembre, notte; `core/cloud` (backup: `KeyboardBackup`, `queries` nel manifest) il 16 novembre, sera.
- Ogni servizio che fa uscire dati è spento di base e compare nel centro privacy; servizi chiusi per ultimi con avviso.
- Parti comuni (`core/designsystem`, `core/ponte`, `CLAUDE.md`, `canvas.json` della tela): si toccano solo se serve e si
  scrive qui.
- A ogni consegna: aggiornare questo file e una sola riga in `docs/STATO.md`.
- Storia completa: sezioni datate di `docs/STATO.md` dall'8 al 16 novembre (Password e Galleria dall'8–9 novembre; Tastiera
  dal 10 novembre; carte e Galleria seconda fila l'11 novembre; Password con Browser e Chat il 12 novembre).

## Sicurezza (18 novembre 2026, notte, progetto Eurise Sicurezza)
- Password: `LoginsProvider` accetta solo il permesso della propria variante (prima anche l'altro, che un'app qualunque
  poteva dichiararsi); `CodesProvider` protetto anche in scrittura; un file KDBX con Argon2 fuori misura si rifiuta.
- Tastiera: `allowBackup="false"` e regole di estrazione complete (prima le parole imparate finivano nel backup Google);
  il ripristino (`BackupProvider`) controlla chi chiama; traduzione e ricerca nella suite vanno solo a Leggi e all'app
  Eurise vere (`SuiteTrust`).
- Galleria: «Modifica» non accetta i file privati della Galleria stessa. Dettagli in `docs/SICUREZZA.md`.

## Sicurezza, 20 novembre 2026 (progetto Eurise Sicurezza)
Password, `LoginsProvider` (METHOD_FETCH): chi chiedeva «com» riceveva tutte le voci .com; ora la scelta passa da
`Matching.fetchMatches` (nome con il punto; sito, domini superiori e sottodomini), con test. Nessun cambiamento ai dati.
Dettagli in `docs/SICUREZZA.md`, sezione «Computer».

## Sicurezza, 20 novembre 2026, sera (progetto Eurise Sicurezza)
Galleria: «Salva in» e «Apri da File» solo verso l'app Eurise della suite; Tastiera: dettatura solo verso Voce della suite (`SuiteTrust`). Dettagli in `docs/SICUREZZA.md`, «Intent verso le altre app della suite».

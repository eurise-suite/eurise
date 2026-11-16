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
- **Da approvare**: PwdFamiglia, PwdFamigliaEntra, PwdFamigliaInvita (Famiglia rimandata dal proprietario in fondo alla road map).
- **Da rifare**: le tavole di quest'area con le tacche vecchie della privacy (ora 10 tacchette a semaforo).

## Prossimi passi, in ordine
1. **Tastiera, lavoro principale** (approvato il 12 novembre; road map: «il proprietario la sta usando: sentire cosa non
   va»). Prima chiedere al proprietario com'è la tastiera oggi (ultime correzioni non ancora confermate: `TouchOffset`,
   migrazioni saltate, spazio 0,7). Poi: modello del tocco per tasto (come FUTO, senza il suo codice), correzioni con le
   lettere vicine e il dizionario italiano, suggerimenti dalle frasi abituali, dizionario personale e apprendimento nel
   backup cifrato (oggi il backup copre solo l'app Eurise: la Tastiera va aggiunta col permesso della suite).
2. **Password → Tastiera, «Compila · account» in linea** (TastPassword approvata): Password non dà ancora
   `InlinePresentation` nella compilazione automatica, quindi la fila della Tastiera nei campi password resta vuota.
3. **Tastiera fuori stile**: schermate delle impostazioni ancora di HeliBoard (tavola prima del codice).
4. **Privacy di quest'area**: semaforo a 10 tacchette in Password (Have I Been Pwned, alias, Murena) e Galleria
   (OpenStreetMap per i luoghi, caricamento); Password e Galleria comandabili dal centro privacy e nel cestino unico
   (serve il permesso di firma con l'app Eurise; tocca anche `app-debug.apk`).
5. **Galleria, resti**: impronta per la cartella protetta, luogo nella scheda Accadde oggi, esportazione del diario,
   «Apri da…» di File.
6. **Pulizia del codice**: `PasswordApp` (1355 righe) da dividere; componenti copiati (Chip, Card, Pill, SwitchRow) da
   portare in `core/designsystem` (parte comune: scriverlo qui).
7. **In fondo**: Password Famiglia (tavole da approvare); kMail tra gli alias (API non verificata); kDrive (bloccato da
   Infomaniak); scrittura a mano nella Tastiera (rimandata: nessun modello libero).
- **Da provare sul telefono, senza esito scritto**: carte con NFC e fotocamera, I miei dati che compilano i moduli,
  accessi istantanei nel Browser, video ed editor della Galleria, caricamento su Murena.

## Come si lavora
- Codice: `password/` (modulo `:password`; `kdbx/`, `autofill/`, `passkey/`, `logic/`), `gallery/` (modulo `:gallery`;
  `data/`, `logic/`, `ui/`), `keyboard/` (progetto Gradle a sé, HeliBoard; codice nostro in
  `app/src/main/java/eu/eurise/keyboard/`, agganci elencati in `keyboard/EURISE.md`). Contratti con la suite in
  `core/suite` (`PasswordContract`, `CardContract`, `FilesContract`): se cambiano, cambia anche `app-debug.apk`.
- Prima le tavole sulla tela (pagine Password, Integrazioni, Galleria), poi il codice dopo l'approvazione.
- Prove senza telefono: `KdbxTest`, `EmvTest` in Password (più la riapertura con pykeepass in `build.yml`); `GalleryLogicTest`,
  `TimelineTest`, `EditToolsTest`, `VideoTimeTest` in Galleria; prove di HeliBoard nella Tastiera.
- Compilazione: «Compila APK» (`build.yml`) parte a ogni push. Prima di spingere `git fetch` e rebase su `origin/brand`
  (altre chat lavorano sullo stesso ramo); poi la stessa testa anche su `prova-piccole` e si pubblica quel giro:
  `gh workflow run apk-prova.yml --ref brand -f run_id=<id del giro su prova-piccole>`.
- In chat sempre i link diretti degli APK cambiati, con nome e giro (mai la pagina della release né allegati):
  - Password: https://github.com/eurise-suite/eurise/releases/download/apk-prova/password-debug.apk
  - Tastiera: https://github.com/eurise-suite/eurise/releases/download/apk-prova/EuriseTastiera-debug.apk
  - Galleria: https://github.com/eurise-suite/eurise/releases/download/apk-prova/gallery-debug.apk
  - Se cambia `core/suite`: https://github.com/eurise-suite/eurise/releases/download/apk-prova/app-debug.apk
- Ogni servizio che fa uscire dati è spento di base e compare nel centro privacy; servizi chiusi per ultimi con avviso.
- Parti comuni (`core/designsystem`, `core/ponte`, `CLAUDE.md`, `canvas.json` della tela): si toccano solo se serve e si
  scrive qui.
- A ogni consegna: aggiornare questo file e una sola riga in `docs/STATO.md`.
- Storia completa: sezioni datate di `docs/STATO.md` dall'8 al 16 novembre (Password e Galleria dall'8–9 novembre; Tastiera
  dal 10 novembre; carte e Galleria seconda fila l'11 novembre; Password con Browser e Chat il 12 novembre).

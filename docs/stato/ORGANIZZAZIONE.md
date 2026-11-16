# Stato dell'area Organizzazione (APK principale)

File di stato dell'area, tenuto dalle chat del progetto «Eurise · Organizzazione (APK principale)» (dal 16 novembre 2026). `docs/STATO.md` resta il
diario generale: lì si scrive solo una riga per consegna che rimanda qui. Regole in `CLAUDE.md`, sistemi in
`docs/PIATTAFORME.md`. Ramo `brand`; giri propri su `prova-organizzazione`.

## Cosa comprende
Scrivania, Oggi, Calendario, Attività, Note, Contatti, Cloud, Orologio, File, Scanner, lettore PDF, bolle, suonerie e sfondi.
Fuori dall'area, anche se stanno nell'APK principale: Alfred (`docs/stato/ALFRED.md`), Meteo (`MAPPE.md`), Rubrica
dell'APK Telefono (`COMUNICAZIONE.md`), schermate per il computer (`COMPUTER.md`).

## Fatto (in breve, al 16 novembre)
- **Scrivania** (`feature/launcher`): tre pagine, home con schede e bolle (Da fare oggi, Prossimo impegno, Codici e
  consegne, Farmaci, Timer e sveglie, Appunto al volo), «I tuoi impegni» con farmaci e ricorrenze di oggi in fondo e la
  frase su tempo e strada (BolImpegni, `MeteoLineBox`). **Dock** sul telefono al posto della barra delle categorie
  (`home/Dock.kt`: Tutte le app con le categorie in colonna dal lato della mano, app che compaiono scorrendo il dito,
  apertura sulla categoria più usata; Stella con le tue app; tablet con `TabletDock`). Preferiti trascinabili, «Seleziona»
  e «Sposta in…», fino a 9 categorie. Ricerca universale con una mano. Widget delle app nelle bolle (`home/AppWidgets.kt`).
  Pannello Eurise e sfondi illustrati (stagione, meteo, feste, momento, date personali).
- **«Ti hanno cercato»**: reazioni, «Rispondi a questa frase», Allega (BolRispondi, BolAllega, `notifications/ReplyExtras.kt`),
  tondo del menu con le app per comunicare (BolComunica, BolComunicaApp), icone dirette solo nella tendina.
- **Icone nella Scrivania**: temi Eurise, Eurise a colori, Sistema, pacchetti di icone (`apps/IconThemes.kt`), stili iOS,
  Android, /e/OS e forma a scelta (`IconStyles.kt`), loghi senza simbolo staccati dal fondo (`LogoMask`). **Dal 16
  novembre, in prova**: temi «Bolle chiare (nuove)», «Bolle scure (nuove)», «Colorate (nuove)» (`SuiteDrawings.kt` generato
  da `scripts/icone_kotlin.py`, `SuiteIcons.kt`), accanto ai temi vecchi.
- **Privacy nella Scrivania**: servizi spenti di base con `ServiceGate`/`ServiceOffNotice` nelle schede (meteo, notizie,
  ricette, borsa, oroscopo, video), correzione per chi aggiorna (`PrivacyCenter.keepOldChoices`), foglio del primo avvio
  (PrivPrimoAvvio, `ServicesWelcome`).
- **Suonerie e sfondo di blocco** (14 novembre, `feature/launcher/.../sounds/`, `home/LockWallpaper.kt`): suoni per persona,
  per app e generali (persona > app > generale), taglio di una canzone con «Trova il ritornello», 8 suoni nell'APK e 13 nel
  pacchetto `suoni.zip` (`scripts/suoni.py`, lavoro `suoni.yml`); WhatsApp e Telegram suonano con Eurise quando il loro
  canale è senza suono; sfondo della Scrivania anche sul blocco (`FLAG_LOCK`, `LockWallpaperWorker` ogni ora).
- **Calendario**: Date importanti, farmaci, «Aggiungi al calendario» e apertura di un evento dalle altre app
  (`EventIntentActivity`), import .ics (`IcsImportActivity`); contratti verso le altre app: `YourDaysProvider` (compleanno o
  anniversario di oggi per Mappe), `DestinationsProvider` (impegni con un luogo), `TodayLinksProvider` (per il Browser).
- **Note, Attività, Cloud**: editor a blocchi e disegno, voci di elenco legate alle Attività, ricorrenze; sincronizzazione
  WebDAV con Murena e Nextcloud a tre vie (`Eurise/Note/<id>.html`); «Condividi › Note» (`ShareToNotesActivity`);
  «Il tuo colore» per testo, taccuini ed elenchi.
- **Orologio**: prossima sveglia sulla home; «Ti sveglia Alfred» nella sveglia (saluto, musica, rete di sicurezza verso la
  suoneria; codice in `core/data/alfred`).
- **File (Drive)**: navigatore unico telefono e cloud, icona propria nel cassetto (anche Scanner, `SectionShortcutActivity`),
  «Salva in…» / «Apri da…» comuni (`core/files`, `FilesActivity`, `FilesContract`; usati da Galleria, Browser, Office,
  Email) con il semaforo per destinazione, «Riordina» (download, file vecchi, copie doppie nel Cestino per 30 giorni, nomi
  veri per le scansioni), ricerca nel testo dei PDF, «Per i tuoi impegni», `PickFileActivity` per le altre app, «Aggiungi il
  testo (PDF cercabile)».
- **Scanner**: scatto automatico (`AutoShot`), lati rifiniti (`SideRefiner`), carta senza dominante (`DocumentFilter`), editor
  delle pagine (ScanPagine, ScanPagina, ScanRitaglio, aspetto «Perfetta»), lettura con Eurise Leggi (DocLeggi, spenta di
  base: nome, cartella, scadenze, importi, IBAN → Da fare, PDF cercabile); lettore QR con mirino animato.
- **Lettore PDF**: «Apri con» sui PDF (`PdfOpenActivity`, su una copia); dalla bolla Documenti, con Eurise Office installata,
  il PDF passa a Office (Annota, Pagine, Firma).
- **Comuni a tutta la suite** (core/designsystem): registro errori dopo una chiusura improvvisa (`CrashInstaller`,
  `CrashActivity`, processo `:errore`), `ImportScreen`, `PrivacyMeter`, `ServiceConsentSheet`.

## 16 novembre, sera: icone nel cassetto, pulizia, tavole delle Note
- **Calendario, Attività e Note con un'icona propria** tra le app di Android (activity-alias `.CalendarApp`, `.TasksApp`,
  `.NotesApp` → `SectionShortcutActivity`, come File e Scanner), disegni della serie colorata (`scripts/icone.py`:
  `ic_calendar_app`, `ic_tasks_app`, `ic_notes_app`); la Scrivania le riconosce (`SuiteIcons`).
- **Parte comune toccata**: `core/designsystem/component/SwitchRow.kt` al posto di sette copie (Orologio, Cloud,
  Attività, Scrivania: fonti, modalità, sfondo di blocco, suoni). Tutta la riga si tocca; titolo in grassetto con la
  spiegazione sotto (`hint`/`strong`), `enabled`, `trailing`. Le altre aree possono usarlo al posto delle loro copie.
- **File divisi** (solo spostamenti, `private` → `internal` dove serve): `LauncherScreen.kt` (1783 righe) in
  `LauncherScreen`, `LauncherPhone`, `LauncherTablet`; `DriveRoute.kt` (1305) in `DriveRoute`, `DriveTabs`, `DrivePdf`
  (il lettore PDF della suite ora sta in un file suo).
- **Tavole nuove, da approvare** (pagina Note, seconda fila): NoteBlocca (cifrata sul telefono, impronta o PIN, nel cloud
  solo cifrata, fuori dalla nota solo il titolo, chiave nel backup cifrato della suite), NoteBloccata (sblocco, si
  richiude dopo 5 minuti, nell'elenco testo nascosto), NoteVocale (testo che compare mentre parli con Eurise Voce, audio e
  trascrizione nella nota, cose da fare trovate). Le entrate (lucchetto in NoteTesto, «Nota vocale» in NoteInserisci)
  erano già approvate.
- **Giro 661** (Compila 43928299330 su `prova-organizzazione`) riuscito e pubblicato in `apk-prova`: https://github.com/eurise-suite/eurise/releases/download/apk-prova/app-debug.apk

## 16 novembre, notte: Note bloccate e vocali, correzioni chieste dal proprietario
- **Note bloccate** (NoteBlocca, NoteBloccata, approvate): `LockedBlock` nel formato della nota (`<section data-eurise-lock>`),
  contenuto e titolo cifrati AES-GCM (`feature/notes/lock/NoteLock.kt`); chiave in `core/cloud/lock/LockKeys.kt`, sigillata
  dal portachiavi di Android, portata dal backup cifrato della suite (voce `lock/notes.key` in `BackupArchive`); sblocco
  con `BiometricPrompt` di sistema (Android 8: schermata del sistema), si richiude dopo 5 minuti o uscendo dall'app;
  ricerca, Alfred, Scrivania e cloud vedono solo il titolo (o niente, se nascosto). Senza blocco schermo non si blocca.
- **Note vocali** (NoteVocale, approvata): `AudioBlock` (`<figure data-eurise="voice">`), registrazione `AudioRecord` 16 kHz
  in AAC .m4a con MediaCodec (`voice/VoiceRecorder.kt`, file in `files/note-vocali/`), testo da Eurise Voce a pezzi
  mentre si parla (`voice/VoiceTranscriber.kt`, `VoiceContract`), «Metti il testo nella nota», «Da fare» trovato.
  **Manca**: i file audio non vanno ancora nel cloud (sul telefono nuovo resta il testo).
- **Blocchi delle Note**: menu sempre visibile (Sposta su/giù, Duplica, Elimina) per tabella, disegno, linea, nota vocale;
  tabella con linee tra le colonne e + / − di righe e colonne.
- **Cursore sempre in vista**: `core/designsystem/component/CaretInView.kt` (parte comune), usato in Note, Attività,
  Calendario; le altre aree possono usarlo per i loro editor.
- **Calendario**: testata compatta, via l'Agenda dalle viste, icona col numero di oggi; ogni appuntamento la sua casella.
- **Icone di altre app**: simbolo nel colore del disegno originale (`LogoMask.glyphColor`), se leggibile sulla bolla.
- **Drive**: selezione multipla (pressione lunga; Tutti, Sposta, Elimina); `glass(solid = true)` in core/designsystem ha un
  fondo quasi opaco (testata e dock di Drive non più trasparenti; vale per tutte le parti «solid»).
- **Tavole nuove da approvare**: pagina Agenda (AgeGiorno, AgeScrivi: Oggi diventa Agenda, contatti suggeriti mentre si
  scrive), pagina Rubrica (RubElenco, RubNuovo con «Dove lo salvo?», RubScheda), IcoColoreBolle (pagina Icone), Drive come
  Murena (DriveCartelle, DriveCassetto, pagina Drive e Scanner, dalle foto del proprietario).
- Giri: 671 (Note) pubblicato; il secondo (Calendario, icone, Drive) è Compila 43961822438.

## Tavole (tela «Icone della suite»)
- **Approvate e in codice**: pagine Scrivania, Calendario, Note, Attività, Cloud, Orologio, Bolle (tavole dall'8 al 10
  novembre, comprese RicBasso/RicAppuntamento/RicDocumenti e le Int* di riunioni, viaggi, modalità, cestino unico);
  Scrivania fila 12 (ScrDock, ScrDockTieni, ScrDockStella, BolComunica, BolComunicaApp: la tavola ScrDock non è stata
  ridisegnata con la correzione delle categorie in colonna, il codice segue la descrizione); Bolle (WidAggiungi, WidBolla,
  BolRispondi, BolAllega, BolImpegni); Drive e Scanner (ScanPagine, ScanPagina, ScanRitaglio, FileSalvaIn, FileApriDa,
  DocLeggi, DocMotore); «Suonerie e sfondo» (SuoSuonerie, SuoTaglia, SuoRegole, SfoBlocco); Privacy (PrivPrimoAvvio);
  Icone (IconeStili, IconeColori).
- **Approvate, codice in prova o da finire**: Icone (IcoBolleChiare, IcoBolleScure, IcoColorate): APK in codice, nella
  Scrivania come temi «(nuove)» accanto ai vecchi; Calendario, Attività e Note non hanno ancora un'icona di sistema loro.
- **Approvate, sul computer (le segue l'area Computer, `COMPUTER.md`)**: PcOggi e PcOggiNotte in codice dal 16 novembre
  (`desktop/app`, dati dal telefono col Ponte); PcCalendario, PcNote, PcAttivita da scrivere.
- **Da approvare**: AgeGiorno, AgeScrivi, RubElenco, RubNuovo, RubScheda, IcoColoreBolle, DriveCartelle, DriveCassetto (16 novembre, notte). NoteBlocca, NoteBloccata, NoteVocale approvate e in codice.

## Prossimi passi, in ordine
1. **Prove del proprietario rimaste senza esito** (giro 651 in `apk-prova` o il prossimo): temi delle icone «(nuove)» nella
   Scrivania e decisione se togliere i temi vecchi; suonerie (qualità dei suoni sintetizzati, WhatsApp col canale
   silenziato, suoneria per persona con Eurise Telefono); sfondo del blocco (alcune ROM ignorano `FLAG_LOCK`); foglio del
   primo avvio; «Salva in…» / «Apri da…»; editor dello scanner (confronto con kDrive); «Aggiungi al calendario» e .ics dalle
   altre app; registro errori; «I tuoi giorni» (compleanno di oggi in Date importanti → incrocio in Mappe).
2. **Approvazione delle tavole del 16 novembre notte**, poi il codice: Agenda al posto di Oggi (con contatti suggeriti
   anche nel nuovo appuntamento e con @ nelle Attività), Rubrica (con l'area Comunicazione, che ha la Rubrica
   dell'APK Telefono), icone che seguono il colore delle bolle, Drive a cartelle con il cassetto.
   Poi: file audio delle note vocali nel cloud (`Eurise/Note/<id>/`); i simboli nuovi nei temi «Eurise» ed «Eurise a colori».
   Idee di collaborazione rapida proposte in chat (da scegliere).
3. **Pulizia che resta**: Chip, Card, Pill e Field copiati tra le app hanno forme diverse da caso a caso: unificarli solo
   dove sono davvero uguali (come `SwitchRow`).
4. **Rimasti aperti delle app**: foto e scansioni nelle Note; colori dei calendari con «Il tuo
   colore» e nella Scrivania; voce «dati sul telefono» nel centro privacy (serve per la memoria di Alfred e per il Ponte);
   timer dalle ricette della Scrivania; il widget nella bolla che segua l'altezza scelta.
5. **Schermate del computer** (PcCalendario, PcNote, PcAttivita) su `core/sync`, insieme all'area Computer.
- **Passi del proprietario**: nome della Scrivania (Benvenuto, Casimiro, Larino: Desco e Domenico sono occupati) e verifica
  TMview dei nomi dell'area (Agenore, Brigida, Scribò, Rubens, Orazio, Stipet, Copisto, Tommaso); finché non sono verificati
  in interfaccia restano i nomi dei servizi.

## Come si lavora
- Ramo `brand`, con altre chat in parallelo: prima di ogni push `git fetch origin brand` e `git rebase origin/brand`. Per un
  giro tutto proprio spingere la stessa testa anche su `prova-organizzazione` (la `concurrency` di «Compila APK» è per
  ramo: due chat su `brand` si annullano i giri), poi pubblicarlo con
  `gh workflow run apk-prova.yml --ref brand -f run_id=<id del giro di Compila>`.
- Link da dare a ogni consegna (diretto, non la pagina della release):
  https://github.com/eurise-suite/eurise/releases/download/apk-prova/app-debug.apk («Eurise, giro N»); se cambia un contratto
  in `core/suite` usato da altri APK, dire quali vanno reinstallati.
- Si compila solo su GitHub Actions (qui Maven non si raggiunge); i test unitari (`core/data`, `feature/*`, `core/files`)
  girano nella compilazione. Negli `strings.xml` controllare gli apostrofi dopo ogni scrittura.
- Parti comuni (`core/designsystem`, `core/ponte`, `CLAUDE.md`, `canvas.json` della tela) solo se serve, scrivendolo qui.
- Tela: https://claude.ai/artifact/AauRJ8e5WdH7nXTqAEXkoo (pagine Scrivania, Calendario, Note, Attività, Cloud, Drive e
  Scanner, Orologio, Bolle, Icone, «Suonerie e sfondo»).
- Storia completa: le sezioni datate di `docs/STATO.md` dall'8 al 16 novembre (in cima anche la descrizione di Scrivania,
  Calendario, Note, Cloud e Attività come erano l'8 novembre).

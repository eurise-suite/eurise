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
- **Approvate, codice da scrivere con l'area Computer**: pagina «Eurise sul computer», PcOggi, PcOggiNotte, PcCalendario,
  PcNote, PcAttivita.
- **Da approvare**: nessuna tavola dell'area risulta in attesa in `docs/STATO.md` (da riconfermare sulla tela alla prima
  tavola nuova).

## Prossimi passi, in ordine
1. **Prove del proprietario rimaste senza esito** (giro 651 in `apk-prova` o il prossimo): temi delle icone «(nuove)» nella
   Scrivania e decisione se togliere i temi vecchi; suonerie (qualità dei suoni sintetizzati, WhatsApp col canale
   silenziato, suoneria per persona con Eurise Telefono); sfondo del blocco (alcune ROM ignorano `FLAG_LOCK`); foglio del
   primo avvio; «Salva in…» / «Apri da…»; editor dello scanner (confronto con kDrive); «Aggiungi al calendario» e .ics dalle
   altre app; registro errori; «I tuoi giorni» (compleanno di oggi in Date importanti → incrocio in Mappe).
2. **Icone di sistema per Calendario, Attività e Note** con i disegni nuovi della serie colorata (alias come File e
   Scanner), poi i simboli nuovi al posto di quelli a tratto nei temi «Eurise» ed «Eurise a colori».
3. **Pulizia rimasta dalla revisione del 14 novembre**: componenti copiati tra le app (Chip ×12, Card ×11, Pill ×10,
   SwitchRow ×7, Field ×6) da portare in `core/designsystem` (parte comune: scriverlo qui e avvisare), file troppo lunghi
   da dividere (`LauncherScreen` 1734 righe, `DriveRoute` 1305).
4. **Rimasti aperti delle app**: Note bloccate, foto e scansioni nelle Note, note vocali; colori dei calendari con «Il tuo
   colore» e nella Scrivania; voce «dati sul telefono» nel centro privacy (serve per la memoria di Alfred e per il Ponte);
   timer dalle ricette della Scrivania; il widget nella bolla che segua l'altezza scelta.
5. **Schermate del computer** (PcOggi, PcCalendario, PcNote, PcAttivita) su `core/sync`, insieme all'area Computer.
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

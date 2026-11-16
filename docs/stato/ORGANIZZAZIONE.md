# Stato dell'area Organizzazione (APK principale)

File di stato dell'area, tenuto dalle chat del progetto «Eurise · Organizzazione (APK principale)» (dal 16 novembre 2026). `docs/STATO.md` resta il
diario generale: lì si scrive solo una riga per consegna che rimanda qui. Regole in `CLAUDE.md`, sistemi in
`docs/PIATTAFORME.md`. Ramo `brand`; giri propri su `prova-organizzazione`.

## Cosa comprende
Scrivania, Agenda (prima Oggi), Calendario, Attività, Note, Contatti, Cloud, Orologio, File, Scanner, lettore PDF, bolle, suonerie e sfondi.
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
- Giri: 671 (Note) pubblicato; il secondo (Calendario, icone, Drive) è il giro 673, pubblicato in `apk-prova`.

## 16 novembre, notte tarda: decisioni del proprietario (da fare nella prossima chat)
- **Approvate**: pagina Agenda (AgeGiorno, AgeScrivi) e pagina Rubrica (RubElenco, RubNuovo, RubScheda), **con le
  aggiunte qui sotto**. Approvate anche **tutte** le otto idee di collaborazione rapida (sotto): prima le tavole.
- **Rubrica, campi che mancano** (le tavole vanno completate e arricchite, poi il codice): titolo (Dott., Ing., Avv.,
  Prof.… prima del nome), soprannome, più numeri con l'etichetta (cellulare, casa, lavoro, fax, altro), email **e PEC**
  (separata, con il suo segno), indirizzi (casa, lavoro, altro) con via, CAP, città, provincia, paese; poi tutto ciò che
  hanno le rubriche migliori: azienda, reparto e ruolo, codice fiscale e partita IVA, compleanno e anniversario, sito e
  profili, relazioni (coniuge, figli, assistente…), nota, etichette, suoneria della persona, foto. Va fatta con l'area
  Comunicazione (la Rubrica sta nell'APK Telefono; `core/contacts` è comune).
- **Drive** (DriveCartelle, DriveCassetto da ridisegnare con queste aggiunte, poi il codice): un'icona per lo Scanner in
  alto; per il telefono e per ogni cloud lo **spazio usato su spazio totale** (barra e «86 GB su 256»), nel cassetto e in
  testa a ogni posto; la parte di oggi della nostra Drive (foto del proprietario in `docs/stato/foto/drive-oggi-16nov.jpg`: pulsanti Scansiona, Carica, Cartella;
  «Per i tuoi impegni»; «Riordina»; «Recenti» a schede) va **sotto le cartelle**, nella stessa pagina.
- **Idee di collaborazione rapida, tutte approvate** (tavole prima del codice, sui cloud scelti: Murena, Nextcloud; tacchette
  della privacy): 1) «Condividi con…» su appuntamenti, note, cose da fare, cartelle, con vede/modifica; 2) «Affida a…» una
  cosa da fare con scadenza e avviso quando è fatta; 3) agenda condivisa con l'assistente che propone appuntamenti da
  confermare; 4) elenchi condivisi (spesa, casa, ufficio) con chi l'ha preso e chi l'ha fatto; 5) nota della riunione
  condivisa, le cose da fare diventano attività assegnate; 6) disponibilità in un link, l'appuntamento nasce in tutti e due
  i calendari; 7) «Passa la palla»: inoltro di email, messaggio o documento con una riga sul da farsi e lo stato; 8) cartella
  di squadra in Drive con chi c'è e cosa è cambiato da ieri. Toccano anche Chat, Email (area Comunicazione) e Drive.

## 16 novembre, notte fonda: tavole arricchite, Agenda in codice, collaborazione
- **Rubrica, tavole arricchite** (pagina Rubrica, da riguardare col proprietario): RubNuovo (390×2760) con titolo a pillole
  (Dott., Dott.ssa, Avv., Ing., Prof., Arch.…), secondo nome, soprannome, numeri con etichetta (cellulare, lavoro, fax, +
  casa/altro), email ed **email PEC a parte col sigillo**, indirizzo con via, CAP, città, provincia, paese, azienda/reparto/
  ruolo, codice fiscale e P.IVA, compleanno e anniversario (Date importanti), sito e profili, persone legate, etichette,
  nota, suoneria, «Dove lo salvo?»; RubScheda (390×2100) con gli stessi campi in lettura, ultima ricevuta PEC, «Portami».
  Il codice della Rubrica va fatto con l'area Comunicazione (vCard 4 per titolo, PEC, relazioni).
- **Drive, tavole rifatte** (pagina Drive e Scanner, da approvare): DriveCartelle (Scanner in alto, «86 GB su 256» con la
  barra, cartelle, sotto la parte di oggi come nella foto: Scansiona/Carica/Cartella, Per i tuoi impegni, Riordina,
  Recenti a schede), DriveCassetto (telefono e ogni cloud con «x GB su y» e tacchette), DriveCloud nuova (Murena aperto).
- **Agenda in codice** (AgeGiorno e AgeScrivi approvate): `feature/today` riscritto (`AgendaScreen`, `AgendaViewModel`,
  `AgendaText` con i test, `DayPeople`, `Maxims`, raccolta di 34 massime nell'APK). Oggi si chiama «Agenda» (sezione
  della barra con il simbolo `EuriseIcons.agenda`, alias `.AgendaApp` con l'icona `ic_agenda_app` della serie colorata,
  «Agenda» nella Scrivania e nei temi nuovi). Giorno con saluto, settimana e meteo (Open-Meteo, solo se acceso), si scorre
  ai giorni prima e dopo (frecce, tocco sul titolo torna a oggi); appuntamenti con «Parti alle… · N min in auto» (Strada,
  solo se accesa); cose da fare con le persone come pillole; persone del giorno (collegate agli impegni e alle cose da fare,
  invitati in rubrica) con Chiama e Scrivi; massima del giorno; campo in basso: + crea una cosa da fare per il giorno
  mostrato, il foglio una nota, una frase da appuntamento («domani alle 10 dentista») apre il + unico già scritto.
  Le note recenti non sono più in Agenda (stanno in Note).
- **Contatti suggeriti mentre si scrive** (`core/links`: `ContactSuggestions`, `MentionQuery` con i test, `PickedPeople`):
  con «@» ovunque, e nel nuovo appuntamento anche scrivendo il nome dopo «con»; la persona scelta si collega a ciò che
  nasce (la si ritrova nella sua scheda). In Agenda, nuovo appuntamento (`EventEditorScreen`) e aggiunta rapida delle
  Attività (`QuickAddSheet`, resta «@Nome» nel titolo). Senza permesso della rubrica compare una riga per darlo.
- **Parti comuni toccate**: `core/designsystem` (solo il simbolo `EuriseIcons.agenda`), `core/data` (`RouteRepository`
  spostato da `feature/launcher/route` a `core/data/route`, stesso codice), `canvas.json` della tela (pagina nuova
  «Collaborazione», titoli di Rubrica, Drive e Agenda), `scripts/icone_disegni.json` (disegno «Agenda», Corallo vivo).
- **Collaborazione rapida, tavole** (pagina nuova «Collaborazione», da approvare): CollCondividi, CollAffida,
  CollAssistente, CollElenchi, CollRiunione, CollDisponibilita, CollPassaPalla, CollSquadra. **Attenzione**: nello stesso
  momento l'area Comunicazione ha disegnato le stesse idee (Ins* nella pagina «Chat ed Email», più InsRuoli e le Spe* delle
  spese di gruppo). Le Coll* sono la seconda proposta, vista da Agenda, Attività, Note e Drive: il proprietario sceglie o
  unisce; poi il codice si fa insieme a Comunicazione, una sola versione.
- Giro 688 (Compila 43973959364 su `prova-organizzazione`) pubblicato in `apk-prova`: https://github.com/eurise-suite/eurise/releases/download/apk-prova/app-debug.apk

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
- **Approvate e in codice dal 16 novembre (notte fonda)**: AgeGiorno, AgeScrivi (Agenda). NoteBlocca, NoteBloccata,
  NoteVocale in codice (giri 671 e 673).
- **Approvate, codice da scrivere**: RubElenco, RubNuovo, RubScheda (Rubrica, tavole arricchite il 16 novembre: da
  riguardare col proprietario, poi il codice con l'area Comunicazione).
- **Da approvare**: IcoColoreBolle; DriveCartelle, DriveCassetto e DriveCloud (rifatte con Scanner, spazio usato e la
  parte di oggi sotto le cartelle); le otto tavole Coll* della collaborazione (da confrontare con le Ins* di Comunicazione).

## Prossimi passi, in ordine
1. **Il proprietario guarda** le tavole arricchite (Rubrica), quelle rifatte (Drive) e le Coll* accanto alle Ins* di
   Comunicazione; prova l'Agenda e i contatti suggeriti (giro 688).
2. **Codice di Drive** dopo l'approvazione: Scanner in alto, spazio usato su totale (telefono: `StatFs`; cloud: quota
   WebDAV `quota-used-bytes`/`quota-available-bytes`), la parte di oggi sotto le cartelle, DriveCloud.
3. **Collaborazione**: scelta tra Coll* e Ins*, poi il codice con l'area Comunicazione, nell'ordine: Condividi con…
   (CalDAV/WebDAV di Murena e Nextcloud), Affida a…, Elenchi condivisi, Disponibilità in un link, Nota della riunione,
   Agenda con l'assistente, Passa la palla, Cartella di squadra.
4. **Codice della Rubrica** con l'area Comunicazione (campi in `core/contacts`, vCard 4); «Dove lo salvo?» con gli account
   del telefono (Murena e Nextcloud via DAVx5/CardDAV, solo telefono, Google per ultimo con l'avviso).
5. **Agenda, rifiniture possibili**: nome dell'utente nel saluto (preso dall'Accoglienza della Scrivania), invitati anche
   da aggiungere all'appuntamento quando la persona scelta ha un'email.
6. **Prove del proprietario** sui giri 671, 673 e 688.
7. Poi: IcoColoreBolle dopo l'approvazione; audio delle note vocali nel cloud; simboli nuovi nei temi «Eurise» ed «Eurise a
   colori»; pulizia di Chip, Card, Pill e Field; foto e scansioni nelle Note; schermate del computer con l'area Computer.
- **Passi del proprietario**: nome della Scrivania (Benvenuto, Casimiro, Larino) e verifica TMview dei nomi dell'area.

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
- Tela: https://claude.ai/artifact/AauRJ8e5WdH7nXTqAEXkoo (pagine Agenda, Rubrica, Collaborazione, Scrivania, Calendario,
  Note, Attività, Cloud, Drive e Scanner, Orologio, Bolle, Icone, «Suonerie e sfondo»).
- Storia completa: le sezioni datate di `docs/STATO.md` dall'8 al 16 novembre (in cima anche la descrizione di Scrivania,
  Calendario, Note, Cloud e Attività come erano l'8 novembre).

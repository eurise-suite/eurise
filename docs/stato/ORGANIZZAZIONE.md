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

## 17 novembre, primissime ore: tutte le tavole approvate, Drive in codice, prime funzioni di collaborazione
- **Approvate dal proprietario** (17 novembre): tutte le tavole del 16 notte, cioè Rubrica arricchita, Drive (DriveCartelle,
  DriveCassetto, DriveCloud), le Coll* (pagina Collaborazione) e, dalla stessa frase, le Ins* di Comunicazione. Il codice
  della collaborazione si divide (vedi `COMUNICAZIONE.md`, decisione del 17 novembre): a Comunicazione il trasporto in
  Chat ed Email (stessa scheda nei due), a noi Calendario, Attività, Note e Drive.
- **Drive in codice**: testata con cassetto dei posti (`DrivePlaces.kt`: `PlacesDrawer`, `SpaceCard`, `SpaceBar`,
  `ScannerButton`, `placePrivacy` con `privacyScore`), nome del telefono (`Settings.Global.DEVICE_NAME`), pulsante
  Scanner; panoramica: spazio usato su totale, tipi di file, cartelle del telefono in griglia (8, poi «Tutte le N»), sotto
  «Oggi» (Scansiona/Carica/Cartella, Per i tuoi impegni, Riordina, Recenti a schede: Tutti, Documenti, Scansioni, Foto,
  Dal cloud). In cima a un cloud: tacchette e spazio (`DriveData.cloudSpace`, quota WebDAV). Tolta la fila delle pillole.
- **«Condividi con…» per file e cartelle del cloud** (`ShareWith.kt`, voce «Con…» nel foglio del file, solo Nextcloud e
  Murena): email (il server manda il link personale) o nome utente dello stesso server, Vede/Modifica, chi c'è già con
  cambio e rimozione, tacchette. `core/cloud` `WebDav.shareWith/sharesOf/setShareEdit/removeShare` (API di condivisione
  OCS, la stessa dei link). È la base della «cartella di squadra»; manca «cosa è cambiato da ieri».
- **Invitati negli appuntamenti** («Condividi con…» di un appuntamento): `CalendarRepository.invite/uninvite` (Attendees
  del Calendar Provider; DAVx⁵ e il server mandano inviti e disdette), sezione Invitati nell'editor con i contatti che
  hanno un'email (`core/links` `searchEmails`, `MailContact`); avviso per il calendario solo sul telefono.
- Giro 701 (Compila 43978824089 su `prova-organizzazione`) pubblicato in `apk-prova`: https://github.com/eurise-suite/eurise/releases/download/apk-prova/app-debug.apk

## 17 novembre, mattina: icone «Come le bolle», elenchi condivisi, «Affida a…»
- **IcoColoreBolle approvata e in codice** (tema delle icone «Come le bolle», `IconThemes.BUBBLES`, **ora di base** per chi
  non ha mai scelto un tema): la bolla dell'icona prende il colore d'accento della Scrivania (il «colore delle bolle»: non
  c'era un colore delle bolle a parte, si cambia con il colore d'accento), di sera verso il blu notte; simbolo crema, o scuro
  se il colore è chiaro; le app non nostre tengono il colore del loro disegno se si legge. Con questo tema le bolle della
  Scrivania prendono un velo della stessa tinta (`launcherGlass(…, bubbleTint)`). Codice in `AppIcon.kt`.
- **Elenchi condivisi** (CollElenchi): un elenco delle Attività è un calendario di attività CalDAV; «Condividi l'elenco»
  (icona di condivisione in Elenchi, o la fascia in cima all'elenco) lo condivide sul server con email o nome utente dello
  stesso Murena/Nextcloud, Vede/Modifica, chi c'è con cambio e rimozione (`WebDav.shareCalendar/unshareCalendar`, API
  `oc:share` di Nextcloud; persone e proprietario letti con `oc:invite`, `oc:owner-principal`, `nc:owner-displayname`).
  Gli elenchi che altri condividono con te arrivano da soli (`TaskList.KIND_SHARED`). Negli elenchi condivisi un tocco
  **prende** la cosa da fare, il secondo la segna **fatta**; sotto il titolo «preso da Marta · 10:12» / «fatto da Luca ·
  ieri» con il tondo della persona. Nel VTODO: `STATUS:IN-PROCESS`, `X-EURISE-TAKEN-BY/-TAKEN-AT/-DONE-BY`.
- **«Affida a…»** (CollAffida), nella scheda dell'attività: persona tra i contatti con un'email, entro (oggi, domani, tra 7
  giorni), «Avvisami quando è fatta» (e se il giorno della scadenza alle 9 non è partita), una riga per lei. La cosa passa
  nell'elenco **«Con <persona>»**, condiviso solo con lei (vede solo ciò che le affidi). Nel VTODO: `ATTENDEE` con
  `PARTSTAT` (affidata NEEDS-ACTION, vista ACCEPTED, iniziata IN-PROCESS, fatta COMPLETED), `ORGANIZER`,
  `X-EURISE-HANDOFF` (ore dei passi), `-MESSAGE`, `-NOTIFY`: le altre app capiscono lo stato. «Come va» con i quattro passi;
  chi la riceve la segna vista aprendola e ha «Ho iniziato»; chi la affida ha «Scrivile un'email» (app di posta, finché il
  trasporto di Chat ed Email non c'è) e «Riprendi». Avvisi (canale «Attività condivise e affidate») quando arriva una cosa
  affidata, quando è fatta, e alle 9 del giorno di scadenza se non è partita. Gli invitati messi da altre app restano loro.
- Dati: `core/data` `TaskCollab` (test in `TaskCollabTest`), colonne `tasks.collab`, `task_lists.owner/members`
  (**migrazione 7→8**), `TaskRepository.takeOrDone/setCollab/addSharedList/setListPeople`; `WebDav.me()` per nome ed
  email dell'account (tenuti in `tasks_sync`). Parti comuni: nessuna di quelle elencate (solo `core/data` e `core/cloud`).
- **Da provare** con due account Murena: condividere un elenco, prendere e spuntare dai due telefoni, affidare una cosa e
  vedere arrivare l'avviso; verificare che Murena accetti la condivisione per email (`mailto:`) e non solo per nome utente.
- Giro 717 (Compila 43991645566 su `prova-organizzazione`).

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
- **Approvate, codice da scrivere**: RubElenco, RubNuovo, RubScheda (Rubrica arricchita, approvata il 17 novembre: il
  codice con l'area Comunicazione); le altre Coll* (Riunione, Squadra, Assistente, Disponibilità; Condividi in parte) e le
  Ins* di Comunicazione.
- **Approvate e in codice dal 17 novembre**: DriveCartelle, DriveCassetto, DriveCloud; «Condividi con…» per Drive e per
  gli appuntamenti (invitati).
- **Approvate e in codice dal 17 novembre (mattina)**: IcoColoreBolle (tema «Come le bolle», di base), CollElenchi,
  CollAffida.

## Prossimi passi, in ordine
1. **Prove del proprietario** sul giro 717 (contiene anche il 701): Agenda (giorni, «@», «Parti alle…»), Drive (cassetto,
   spazio, Oggi sotto le cartelle), «Condividi con…» su una cartella di Murena, invitati in un appuntamento di Murena;
   icone «Come le bolle»; elenchi condivisi e «Affida a…» con un secondo account Murena.
2. **Accesso a kDrive e alla posta di Infomaniak** (chiesto dal proprietario il 17 novembre, da risolvere): kDrive è fermo
   perché Infomaniak non concede lo scope OAuth «drive» e il WebDAV risponde 403 sul piano my kSuite+ (`STATO.md`, 9
   novembre); la posta Infomaniak in Eurise Email (area Comunicazione) richiede la verifica in due passi e una password per le
   app. Da fare: rileggere le condizioni attuali di Infomaniak (WebDAV, API kDrive, OAuth), riprovare con il proprietario e,
   per la posta, concordare con l'area Comunicazione.
3. **Collaborazione, la nostra parte, in ordine**: «Affida a…» con il trasporto di Comunicazione (Chat o email, stessa
   scheda) al posto dell'email a mano; nota della riunione condivisa (cose da fare → attività assegnate); cartella di
   squadra (cosa è cambiato da ieri: attività di Nextcloud); agenda con l'assistente (calendario condiviso «Proposte»);
   disponibilità in un link (verificare gli Appuntamenti di Nextcloud Calendar su Murena).
4. **Codice della Rubrica** con l'area Comunicazione: campi in `core/contacts` (titolo, soprannome, numeri ed email con
   etichetta, PEC come email con etichetta «PEC», indirizzi strutturati, azienda/reparto/ruolo, CF e P.IVA come campi
   personalizzati, date, siti, relazioni, nota), editor e scheda nell'APK Telefono; «Dove lo salvo?» con gli account.
5. **Agenda, rifiniture possibili**: nome nel saluto (dall'Accoglienza della Scrivania); invitati anche dal + unico.
6. Poi: audio delle note vocali nel cloud; simboli nuovi nei temi «Eurise» ed «Eurise a
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

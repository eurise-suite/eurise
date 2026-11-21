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

## 21 novembre: note vocali complete e Impostazioni Eurise (tavole da approvare, niente codice)
- Richiesta del proprietario: «Nota vocale» accanto ad «Aggiungi nota»; l'audio in una cartella del telefono o nel cloud,
  a scelta; nella nota il testo trascritto e riordinato (punteggiatura, titoli, paragrafi) con Alfred; riassunto su
  richiesta; pensare a un posto unico per le impostazioni.
- **C'è già** (16 novembre, NoteVocale): registrazione 16 kHz in .m4a (`voice/VoiceRecorder.kt`) nella cartella privata
  `files/note-vocali/`, testo da Eurise Voce (Whisper sul telefono) mentre si parla, «Metti il testo nella nota», «Da fare»
  trovato. Si entra solo da dentro una nota (NoteInserisci).
- **Tavole nuove** (pagina Note, quarta fila): NoteVocaleEntra (microfono sopra il +: tocco breve registra, tenuto premuto
  sceglie dove salvare), NoteVocaleRegistra (testo grezzo mentre parli, dove va l'audio sempre in vista), NoteVocaleOrdinata
  (titolo, titoletti e paragrafi da Alfred, audio con la cartella, «Riassunto», «Testo com'era», «Riordina di nuovo»),
  NoteVocaleDove (telefono › Registrazioni › Eurise, visibile alle altre app; solo nelle Note, privata; cloud collegato,
  cartella «Note vocali»), ImpEurise (un posto unico con la ricerca: suite, dati, app; ogni app apre la sua sezione).
- **Come si farà, dopo l'approvazione**: cartella pubblica con MediaStore (`Recordings/Eurise`, senza permessi in più da
  Android 10; su Android 8–9 `Music/Eurise`); cloud con il caricamento già usato da Cloud (WorkManager, Wi-Fi); riordino e
  riassunto con il cervello di Alfred (Eurise AI, modello GGUF sul telefono) quando c'è; senza modello restano la
  punteggiatura di Whisper e i paragrafi alle pause, e «Riassunto» non compare. Il testo grezzo resta sempre («Testo com'era»).
- Impostazioni Eurise tocca parti comuni (navigazione nell'app, voci di ogni area): va concordata con le altre aree.

## 20 novembre: Telefono e Rubrica (giro 896, area Comunicazione)
- Su richiesta del proprietario questa chat ha corretto Telefono e Rubrica (APK `phone/`, area Comunicazione) e toccato
  `core/contacts` (comune con Messaggi). Dettagli in `docs/stato/COMUNICAZIONE.md`, voce «correzioni del 20 novembre».
- 21 novembre, giro 910: elenco Eurise dei numeri molesti. Parti comuni toccate: `core/suite` (`PrivacyService.SpamList`,
  `SpamList`, `SpamListContract`) e `core/designsystem` (testi del servizio, gruppo «Telefono» nel primo avvio); nell'app
  Eurise `SpamListProvider` e `SpamListWorker`. Dettagli in `COMUNICAZIONE.md`.
- 21 novembre, giro 918: l'elenco si scarica dalla pre-release `elenco-molesti` (flusso con controllo), controllo
  settimanale programmato; «Segnala a Eurise» aspetta il server Eurise (**da ricordare al proprietario**).

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

## 17 novembre, tarda mattina: riunione «Assegna a tutti», cartella di squadra, Infomaniak verificato
- **Nota della riunione** (CollRiunione, la nostra parte): nella chiusura della riunione ogni azione con «@nome» che
  corrisponde a un invitato con email (nome, cognome o inizio dell'indirizzo) mostra «a Laura», e il pulsante diventa
  **«Assegna a tutti»**: l'azione nasce affidata a lei (stesso modello di «Affida a…»: elenco «Con …» condiviso solo con
  lei, avviso a chi l'ha affidata quando è fatta). Nella riunione, sotto «Azioni», «a Laura · vista» per ciascuna.
  **Manca**: la nota stessa scritta a più mani dagli invitati (serve che le Note degli altri ricevano le note condivise).
- **Cartella di squadra in Drive** (CollSquadra): in una cartella di Nextcloud/Murena condivisa con qualcuno, in cima
  le persone, «Marco e Laura modificano · Luca vede», «Gestisci» (il «Condividi con…» della cartella) e **«Cambiato da
  ieri · N»** con le frasi del server dall'app Attività di Nextcloud (`WebDav.activity`, `DriveData.team`, `DriveTeam.kt`);
  un tocco su un cambio apre il file. Se l'app Attività non c'è sul server, la sezione non compare.
- **Parte comune nuova in `core/cloud`**: `TaskSharing` (condivisione degli elenchi delle Attività, nome ed email
  dell'account, condivisioni in attesa), usata da Attività e Note. `Collab` delle Attività la richiama.
- **Infomaniak, verificato il 17 novembre** sulle guide ufficiali: **WebDAV di kDrive non è disponibile con my kSuite
  e my kSuite+** (né con kSuite Free/Standard); c'è con kDrive Solo, Team, Pro e kSuite Business/Enterprise. La via API
  vuole un token con lo scope «drive», che si crea a mano nel pannello (contro la regola dei due tocchi) e che le
  applicazioni OAuth registrate non ricevono (prova del 9 novembre). Quindi con il piano del proprietario kDrive non si
  può collegare in modo pulito: o un piano kDrive con WebDAV (poi la password per le app, già gestita), o chiedere a
  Infomaniak lo scope «drive» per l'app OAuth di Eurise. La **posta** Infomaniak funziona in IMAP/SMTP con una password
  per le app (`Providers.kt`), è dell'area Comunicazione. Decisione del proprietario.
- **Accento «Ceruleo»** (chiesto dal proprietario): il colore del gufo del Browser con la stessa sfumatura del suo tondo
  (`ExtraTint.Ceruleo`, `Accent.gradient`); la sfumatura si vede nel campione e nelle bolle delle icone «Come le bolle»
  (`LocalAccentGradient`); altrove l'accento resta un colore pieno (#1298DF di giorno, #5EB9E9 di sera). Fuori dai colori
  «Vario» (`inPalette = false`) perché aggiungerlo non cambi i colori delle categorie. Le altre app lo ricevono già con la
  tavolozza di `AppearanceContract` (colore pieno).
- **Sfondo del blocco che non funziona** (segnalato dal proprietario): dal codice non si vede un errore certo. Ora, se
  `setBitmap` risponde 0, si riprova con `setStream`; si controlla che il blocco abbia davvero uno sfondo suo
  (`getWallpaperId(FLAG_LOCK)`), e l'avviso «non ha accettato» mostra il **motivo** dato dal sistema. Da riprovare sul
  telefono del proprietario e leggere il motivo.
- Giro 723 (Compila 43997542215 su `prova-organizzazione`; il 720 è stato sostituito).

## 17 novembre, mattina (dopo le prove): icone «Sfondo delle bolle», Ceruleo cangiante, sfondo dietro le recenti
- **Sfondo del blocco**: sul telefono del proprietario è arrivato dopo un riavvio. Aprendo le **app recenti** compariva
  per un attimo il vecchio sfondo: è lo sfondo di sistema della home, che la Scrivania non usava (disegna da sé la sua
  scena). Ora `LockWallpaper.refreshHome` mette come sfondo di sistema della home la scena della Scrivania, ridisegnata solo
  quando cambia (momento del giorno, meteo, feste); interruttore «Dietro le app recenti › Stesso sfondo della Scrivania»
  (acceso di base) in Sfondo anche sul blocco. Il blocco non cambia (Android gli tiene il suo).
- **Ceruleo cangiante**: oltre al campione e alle bolle delle icone, tutti i fondi pieni nel colore d'accento della
  Scrivania (pulsanti, giorno di oggi, numeri, risposte rapide, suonerie…) usano la sfumatura del gufo
  (`Modifier.accentBackground`, `apps/AccentFill.kt`). Interruttori, caselle e testi restano a colore pieno (Material non
  accetta sfumature).
- **Icone «Sfondo delle bolle»** (`IconThemes.BUBBLE_GLASS`, **ora di base**): la bolla è il vetro delle bolle della
  Scrivania; «Segui il colore delle bolle» (acceso: di sera vetro scuro; spento: sempre chiaro). Le nostre app con il loro
  disegno nei colori cangianti (serie chiara o scura secondo il vetro); le altre con il simbolo nel **colore dominante
  dell'icona originale** (`AppColors.originalTint`, senza portarlo alle tinte della suite: WhatsApp verde, Telegram
  azzurro) con una sfumatura dal più chiaro al più intenso (`shimmer`). Il tema di prima resta come «Colore d'accento».
- Giro 731 (Compila 44052844574 su `prova-organizzazione`).

## 17 novembre, fine mattina: tavole IcoCeruleo e IcoCeruleoNotte (scelto E di giorno, F di sera)
- Il proprietario: la sfumatura del Ceruleo va bene e resta; **dove l'accento è pieno deve tendere al blu intenso**, non
  al chiaro. Tavola **IcoCeruleo** (pagina Bolle della home, in alto): sette blu pieni da A #1298DF (oggi) a G #003AC7,
  ciascuno su pulsante, giorno di oggi, interruttore, numero, testo, con il contrasto del testo bianco (A 3,2:1 non
  basta; da C #0B74D8 in su va bene). Toccato `canvas.json` della tela (una voce in `boards` e in `order`). Dopo la scelta:
  `ExtraTint.Ceruleo.light` (e un `dark` coerente), la sfumatura non cambia.
- Tavola **IcoCeruleoNotte** accanto: gli stessi sette blu sul tema Notte. Presi come sono si leggono male (da 3,6:1 di A
  a 1,7:1 di G); schiariti per la sera arrivano a 4,5:1 (testo bianco sopra circa 4:1) e da C a G si somigliano molto.
  Per ognuno la versione di sera calcolata (`dark`), da usare con la scelta del proprietario.
- **Scelto: E di giorno (#0552DC), F di sera (#85A4F1)**, in `ExtraTint.Ceruleo`; sfumature invariate. **Giro 735** (Compila 44079077368).
- Parte non nostra toccata: `chat/src/main/res/values/bussola_strings.xml`, apostrofo non protetto in `bu_rep_type`
  («un'immagine») che fermava la compilazione di tutti (giro 734); corretto con `\'`.

## 17 novembre, pomeriggio: agenda con l'assistente (CollAssistente, InsAssistente)
- **Prove del proprietario sul 735**: l'esito non è ancora arrivato (la chat del 17 pomeriggio non l'ha ricevuto).
- **Come funziona**: sul cloud dell'utente (Murena o Nextcloud; l'account delle note se sa condividere, altrimenti il primo
  dei due) nasce il calendario di appuntamenti **«Proposte»** (`eurise-proposte`), condiviso **in modifica** con chi aiuta
  (email o nome utente dello stesso server). Lei ci scrive le proposte con qualunque app di calendario (anche Eurise: con
  DAVx⁵ le compare «Proposte (Mario)» tra i calendari dell'editor); la prima riga della descrizione è il perché. I
  calendari scelti in «Cosa vede» le si condividono in sola lettura («Titoli»); gli appuntamenti privati (CLASS
  confidenziale) Nextcloud li mostra come «Occupato». «Libero/occupato» per un calendario intero non c'è: Nextcloud non
  ha questa condivisione (la tavola InsAssistente lo prevedeva per Personale; ora è «Non vede»).
- **Schermo** «Agenda con Laura» (`feature/calendar/assistant/AssistantScreen.kt`, rotta `calendar/assistant`): settimana
  con i punti (grigi le proposte), «Da confermare · N», schede con «Laura propone · perché», Conferma / Altra ora (giorno e
  ora, stessa durata) / ✕, «Conferma tutte e N» in basso a destra, «Le confermate vanno in» (calendario del telefono, di
  base quello con lo stesso nome del primo calendario che lei vede), «Cosa vede Laura», tacchette, «Smetti di condividere».
  Senza assistente lo stesso schermo serve a condividere (persona tra i contatti con email, calendari di lavoro già scelti).
- **Confermata**: nasce nel calendario scelto (Calendar Provider, poi DAVx⁵ la porta sul server, dove lei la vede se ha
  quel calendario) e la proposta si cancella dal server. **Rifiutata**: resta su «Proposte» con `STATUS:CANCELLED` (lei la
  vede barrata); le annullate passate e le proposte passate da più di un giorno si tolgono da sole.
- **Nel Calendario**: proposte in grigio nelle viste (se il calendario «Proposte» non è già sul telefono; se c'è, i suoi
  appuntamenti diventano grigi), un tocco apre l'agenda condivisa; striscia «Agenda con Laura · N da confermare» sotto le
  viste. **Avviso** delle proposte nuove (canale «Agenda con l'assistente») dal lavoro `AssistantSyncJob` nella
  sincronizzazione del cloud (ogni 30 minuti); tocco → sezione `proposals`. Voce «Agenda con l'assistente» in Cloud.
- Codice: `core/cloud` `Proposals` (nuovo; `CloudSync` sincronizza anche con la sola agenda attiva), `WebDav.calendarObjects`
  (REPORT con `calendar-data`), `mkCalendar(…, events = true)`, `DavCalendar.events`; `core/data` `Ics.Event` con `uid`,
  `status`, `organizer` e `Ics.withStatus` (test in `IcsTest`); `feature/calendar` dipende ora da `core/cloud`. Parti comuni
  dell'elenco: nessuna.
- **Manca**: le proposte nell'Agenda (sezione Oggi); «Altra ora» che torna all'assistente come controproposta (ora decide
  l'utente e l'appuntamento nasce a quell'ora); proporre da Eurise con un pulsante «Proponi a Mario» invece di scegliere il
  calendario «Proposte (Mario)».
- **Da provare** con due account Murena: condividere l'agenda, scrivere una proposta dal secondo account (anche dal web di
  Murena), vederla arrivare con l'avviso, confermarla, rifiutarne una e controllare che dall'altra parte risulti annullata.
- Giro 748 (Compila 44118965229 su `prova-organizzazione`; il 745 si era fermato su `chat/.../ChatModel.kt`: setter di
  `workQuiet` con lo stesso nome JVM di `setWorkQuiet`, corretto con `@set:JvmName("assignWorkQuiet")`, parte non nostra).

## 17 novembre, sera: pagine della Scrivania a piacere (tavole da approvare)
- **Chiesto dal proprietario**: mettere le app delle categorie in qualunque pagina della Scrivania, **aggiungere tutte le
  pagine che vuole e farci quello che vuole**. Oggi nel codice le pagine sono tre fisse (bacheca a sinistra, home, riepilogo
  a destra) e un'app va solo nei Preferiti della home («Aggiungi alla home», `LauncherViewModel.toggleFavorite`, un elenco
  solo, al massimo colonne × righe).
- **Tavole nuove** (pagina Scrivania, fila «Pagine della Scrivania a piacere», y 12400): **ScrPagAggiungi** (pressione lunga
  su un'app: «Aggiungi alla Scrivania» con le miniature delle pagine, tocco breve = la pagina con la stella, «Nuova» crea una
  pagina con l'app; anche in «Seleziona più app › Sposta in… › Scrivania»), **ScrPagTrascina** (trascinare app, bolle,
  cartelle e widget: al bordo si passa alla pagina accanto, sulla fila delle pagine si salta, sul «+» nasce una pagina),
  **ScrPagGestisci** («Le tue pagine»: miniature da riordinare, ⋯ con Rinomina e simbolo, Sfondo, Apri da qui, Duplica,
  Quando si vede, Elimina con 30 giorni nel Cestino; ci si arriva pizzicando o da un punto vuoto › Pagine),
  **ScrPagNuova** (nome, simbolo, parti da Vuota / App e bolle / Solo bolle / Copia, solo in una modalità, sfondo, apri da
  qui), **ScrPagLibera** (una pagina tua in modifica: griglia 4 × 7 con app, cartella, bolla, widget di un'altra app e
  appunto, ognuno spostabile e con la misura che vuoi; barra App, Bolla, Widget, Cartella, Testo, Fatto).
- Bacheca e riepilogo diventano pagine come le altre (si possono riordinare, nascondere, riempire di app).
- **Parte comune toccata**: `canvas.json` della tela (cinque voci in `boards` e `order`, nota `t-pagine`).
- **Approvate dal proprietario («procedi») e in codice** nello stesso giorno, tranne ScrPagTrascina:
  - **Modello**: `home/DeskPages.kt` (`DeskPage` con id, nome, simbolo, modalità; `PageSymbol`; `PageStart`; codifica).
    `LauncherSettings.pages`, `startPage`, `addPage` (la stella), `pageSlots` (bolle delle pagine nuove), `slotsOf`/`withSlots`
    per tutte le pagine. Le app sono bolle `HomeKind.App` (`HomeSlot.app`, chiave `app#…` codificata; larghezza minima 0,2,
    di base 0,25: quattro per riga), disegnate da `PageApp` in `LauncherPhone.kt`.
  - **Carosello**: le pagine visibili (con la modalità accesa) nell'ordine scelto; parte da `startPage`, il tasto Home porta
    lì. Bacheca, riepilogo e pagine nuove usano `SidePage` con le stesse bolle; catalogo e widget per pagina (`sideCatalog`
    e `widgetPage` ora sono id di pagina).
  - **Fogli** (`home/PagesSheet.kt`): `PagesSheet` («Le tue pagine», dai puntini in basso, ora toccabili), `PageDialog`
    (pagina nuova o modifica), `PagePickerSheet` («Dove metto…?»). Voci «Aggiungi alla Scrivania…» nel menu delle app delle
    categorie (`AppTile.onAddToPage`) e pressione lunga in «Tutte le app» della dock (`AllAppsPanel.onHold`).
    `LauncherViewModel.addToPage` (sulla home nei preferiti finché c'è posto, poi tra le bolle), `createPage`, `deletePage`.
  - **Non ancora**: miniature vere delle pagine; il Cestino per le pagine eliminate (ora c'è la conferma); cartelle e testo
    libero dentro le pagine nuove. (Trascinamento e preferiti verso altre pagine: fatti nel giro 772, sezione sotto.)
  - Giro 760 (Compila 44166106646 su `prova-organizzazione`).

## 17 novembre, notte: trascinamento tra le pagine (ScrPagTrascina, approvata)
- **Prove del proprietario sui giri 735, 748 e 760**: l'esito non è arrivato in questa chat (annunciato, non incollato).
- **Come funziona**: un'app o una bolla tenuta premuta e trascinata va su qualunque pagina della Scrivania. Ai bordi, tenendola
  un attimo (550 ms), si passa alla pagina accanto (frecce ai lati); sulla **fila delle pagine** in alto (etichette con
  simbolo e nome) si salta diretti; sul **«+»** nasce una pagina nuova dopo quella vista, con l'oggetto dentro (nome
  «Pagina», si rinomina in «Le tue pagine»). Sulla pagina un **buco tratteggiato «qui»** mostra dove cadrà e le altre bolle
  fanno spazio (prima o dopo la bolla sotto il dito, secondo la metà; sotto tutte, in fondo). Sulla home, sopra i
  preferiti, l'app entra tra i preferiti (la griglia si accende) se c'è posto, altrimenti tra le bolle della home.
- **Da dove si parte**: dalle categorie (si tiene premuta e si riordina come prima; appena si muove compare la fila delle
  pagine in alto e, salendoci sopra, l'app passa alla Scrivania); da «Tutte le app» della dock (tenuta ferma: «Dove
  metto…?» come prima; trascinata: si porta); dai preferiti della home (sulla fila o ai bordi escono dalla griglia: è la
  voce «app dei preferiti spostabili» che mancava); da **ogni app e bolla** delle pagine, home compresa, widget compresi
  (tenuta ferma: entra in modifica come prima, al rilascio; trascinata: si porta). Le cartelle nelle pagine non ci sono ancora.
- **Codice**: `home/DeskCarry.kt` (`DeskCarry` con `CarryItem`/`CarryTarget`, `deskCarryTracker` sulla radice della Scrivania
  che segue il dito nel passaggio Initial, così il trascinamento sopravvive alle pagine e alle categorie che escono di
  scena; `carryIndex`, `CarryHole`, `DeskCarryLayer` con fila delle pagine, frecce e oggetto sotto il dito);
  `List<HomeSlot>.placing` in `DeskPages.kt`; `LauncherViewModel.carryTo`; `HomeBubble` registra dove sta ogni bolla
  (`carryBounds`, `LocalDeskPage`); passaggi in `CategoryPages`, `FavoritesGrid`, `HomeBoard`, `AllAppsPanel`. Test in
  `DeskCarryTest`. Solo telefono (sul tablet la Scrivania è una pagina). Parti comuni: nessuna.
- **Da provare**: portare un'app da una categoria su «Lavoro» passando dalla fila; da «Tutte le app» sulla bacheca; un
  preferito della home su un'altra pagina; una bolla (anche un widget) dalla home a una pagina nuova col «+»; il bordo per
  girare pagina; tenere ferme app e bolle (menu, modifica) per vedere che non è cambiato niente.
- Giro 772 (Compila 44188594640 su `prova-organizzazione`; il 766 si era fermato su una chiamata con nome completo di una
  funzione con ricevitore `BoxScope`).

## 18 novembre: spostare app tra categorie e fare cartelle (dalla dock e dalle pagine)
- **Prove del proprietario sul giro 772**: il trascinamento tra le pagine funziona (dalle categorie, preferiti, bolle e widget
  col «+», bordi, menu e modifica da fermi). **Non funzionavano** spostare un'app da una categoria all'altra e creare
  cartelle: le voci c'erano solo nelle pagine intere delle categorie, non in «Tutte le app» della dock (tenuta ferma apriva
  solo «Dove metto…?») né nelle app delle pagine (tenuta ferma entrava in modifica).
- **«Tutte le app» della dock**: tenuta ferma, un'app apre il suo menu, lo stesso delle categorie (crea cartella, sposta
  in…, preferita, aggiungi alla Scrivania…, informazioni). Trascinata: sopra un'altra categoria della colonna (si accende)
  **si sposta lì**; sopra un'altra app **nasce una cartella** con le due (nome di base, nella categoria aperta o, da «Tutte»,
  in quella dell'app sotto; l'app portata passa in quella categoria); sopra una cartella **ci entra**; uscendo dal pannello
  va sulla Scrivania come prima. Il pannello mostra in testa le cartelle della categoria (un tocco le apre) e non ripete
  le app che stanno in una cartella.
- **App delle pagine**: tenute ferme aprono lo stesso menu, più «Modifica la pagina» (la modifica di prima) e «Togli dalla
  pagina». Le bolle tenute ferme entrano in modifica come prima. Una cartella creata da qui nasce nella categoria
  dell'app (le cartelle dentro le pagine non ci sono ancora).
- **Codice**: menu comune `AppMenu` / `AppMenuFor` con `AppMenuActions` (`LocalAppMenu`) in `home/HomeParts.kt`, usato da
  `AppTile`, `AllAppsPanel`, `PageApp`; `SlotMenu` (`LocalSlotMenu`) in `HomeBoard`; `DeskCarry` sa del pannello (`panel`,
  `panelCats`, `panelApps`, `inPanel`, `dropCategory`, `dropOnApp`; `CarryItem.category`). Parti comuni: nessuna.
- **Da provare**: in «Tutte le app» tenere ferma un'app (menu), trascinarla su un'altra categoria, trascinarla su un'altra
  app (cartella) e poi una terza sulla cartella; nelle pagine intere delle categorie «Sposta in…» e «Crea cartella» dal
  menu; un'app su una pagina tenuta ferma (menu con «Modifica la pagina»).
- Giro 782 (Compila 44198366637 su `prova-organizzazione`).

## 18 novembre, mattina: pagina d'agenda (giro 827) e pulizia della tela
- **Agenda come una vecchia agenda** (tavola `AgePagina`, approvata con le correzioni del proprietario): in alto «Buongiorno
  Marco» (nome da Aspetto della Scrivania, `user_name` in `eurise_launcher`) e a destra «2026 · settimana 47 · 321° giorno»;
  a sinistra, in una colonna di 88 dp, nome del giorno e mese con le lettere distribuite e il numero misurato perché due
  cifre la riempiano (`DayNumber`, `SpreadText`); a destra meteo e massima (tolta la scheda della massima in fondo). Colori
  del numero: blu notte i feriali, salvia i prefestivi (sabati e vigilie), amaranto i festivi (domeniche e feste,
  `ItalianHolidays`); nome del giorno colorato solo nei prefestivi e festivi; versioni chiare per il tema Notte. Fondo
  unico, filo quasi invisibile, niente frecce (si scorre). `AgendaText.dayKind`, `dayOfYear` con test.
- **Numero arrotondato**: Nunito Bold con le sole cifre (`feature/today/res/font/nunito_digits_bold.ttf`, 2,6 KB, SIL OFL
  1.1, licenza in `assets/licenze/nunito-OFL.txt`). In Aspetto della Scrivania «Numero del giorno nell'Agenda»:
  Arrotondato (di base) o Il nostro (Plus Jakarta Sans leggero), chiave `agenda_number_ours`.
- **Tela ripulita** su richiesta del proprietario (512 → 471 file): tolte le tavole segnate «superata» o rese inutili da
  una scelta: tutta la pagina «App e launcher» (prima versione: LauncherPhone, LauncherSearch, LauncherDrawer,
  LauncherSettings, LauncherTablet, LauncherTabletApps, Drive, Scanner, Galleria, Mail, Note, Calendario, Orologio,
  Telefono, Messaggi, Contatti, PDF, Traduttore, Mappe, Assistente, Riconosci, ModalitaAuto, con la pagina e i suoi
  titoli); il primo set di icone (Main, Home, Tendina, Icon); le prove del simbolo prima della stella polare
  (BrandSimbolo, BrandStelle, BrandStelleVive, BrandStelleMare, BrandRiflesso, BrandProve5, BrandProve6, BrandCielo,
  BrandSfera, BrandGlobo, con i titoli delle file); CalTreGiorniGrafite e CalFondoBianco (non scelti); ScrHomeAttuale,
  ScrAccoglienzaA e B (scelta la C). Restano BrandLogo e BrandMark (componente usato da altre tavole). Le versioni
  precedenti della tela le conservano. **Parte comune toccata**: `canvas.json` della tela.

## 18 novembre, sera: bolle che si adattano accese di base (giro 838)
- Il proprietario vedeva le bolle vuote restare alte: l'opzione era spenta di base e non l'aveva accesa (poi l'ha accesa
  e funziona). Dal giro 838 l'opzione è **accesa di base**, anche dove l'app c'era già (chiave nuova
  `adaptive_bubbles_on`, perché la vecchia era salvata spenta); si spegne da Aspetto. Questo cambia la scelta
  «spenta di base» del giro 800: da confermare con il proprietario.
- Giro 838 pubblicato in `apk-prova` con `run_id` alle 22:57.

## 18 novembre, pomeriggio: Calendario, Messaggi e Appunti fissati si adattano anche loro (giro 837)
- **Cosa cambia**: con «Bolle che si adattano» accesa le tre schede non hanno più l'altezza fissa (Calendario 300,
  Messaggi 240, Appunti fissati 200) ma seguono il contenuto, ognuna per conto suo, senza maniglia dell'altezza né catena,
  come le altre bolle a elenco. Calendario: mese e, accanto, **tutti** gli impegni e le cose da fare del giorno scelto
  (prima 4 e 3); Messaggi: tutte le righe; Appunti fissati: tutte le note fissate (prima 12), una riga sola se non ce ne
  sono. Con «Altezza massima» (metà o un terzo dello schermo) la scheda si ferma lì e scorre dentro di sé; senza massimo
  non scorre. Spenta l'opzione, tutto torna come prima (altezza scelta o di partenza).
- **Codice**: `HomeKind.adapts` con Calendar, Messages, Notes; `SummaryCard` senza scorrimento interno quando la bolla si
  adatta senza massimo (una colonna con peso e scorrimento, dentro una pagina che scorre, non avrebbe un'altezza);
  `LauncherPhone` non impone l'altezza di partenza alle schede che si adattano; `InteractiveMonth` e `PinnedNotesCard`
  senza tetto di righe con `LocalBubbleGrows`. Testo dell'interruttore aggiornato; `SnapHeightTest` esteso. Parti comuni:
  nessuna.
- **Da provare**: accendere l'opzione con le tre schede sulla home o su una pagina; giorno vuoto e pieno nel Calendario
  (toccare un giorno con molti impegni), molti e nessun messaggio, nessuna e molte note fissate; poi «Metà schermo».
- **Giro 837** compilato da `brand` (Compila 44295933239): lo spostamento di `prova-organizzazione` sulla stessa testa
  non è stato permesso in questa chat.
- **«Pacchetto non valido» (risolto)**: alle 12:16 `apk-prova` era stato ripubblicato senza numero di giro e aveva preso
  il **giro 821** (versione 921), più vecchio di quello sul telefono: Android lo rifiuta. Senza `run_id` il flusso prende
  l'ultimo giro di Compila *riuscito per intero* su brand, e da quando il Flatpak fallisce i giri recenti risultano falliti
  anche con gli APK buoni. Ripubblicato il 837 alle 13:52 (versione 937). Finché il Flatpak non torna verde, pubblicare
  **sempre con `run_id`**.

## 18 novembre, notte: bolle che si adattano (giro 800) e pagina d'agenda (tavola da approvare)
- **Decisioni del proprietario** sulle tavole ScrBolleAdatta: si allungano **senza limite**, con un massimo facoltativo;
  affiancate **ognuna per conto suo**, con uno scatto e un indicatore quando hanno la stessa altezza; elenco ok.
- **Codice (giro 800)**: impostazione in Aspetto «Bolle che si adattano» (spenta di base) con «Altezza massima»: Nessuna,
  Metà schermo, Un terzo (`adaptiveBubbles`, `adaptiveMax`). Accesa, le bolle a elenco (`HomeKind.adapts`: I tuoi impegni,
  Da fare oggi, Ti hanno cercato, Prossimo impegno, Farmaci, Codici e consegne) ignorano altezza scelta e catena, mostrano
  tutte le righe (`LocalBubbleGrows`, gli elenchi interni non scorrono più da soli) e non hanno maniglia dell'altezza né
  catena; «I tuoi impegni» in una giornata vuota è una riga sola con il «+». Per tutte le altre bolle la maniglia
  dell'altezza **scatta** sull'altezza della vicina non legata (entro 12 dp, `snapHeight`), con vibrazione ed etichetta
  «Stessa altezza». Calendario, Messaggi e Appunti fissati: fatti nel giro 837 (sezione sopra). Test `SnapHeightTest`.
- **Pagina d'agenda (tavola `AgePagina`, pagina «Agenda» della tela, sopra la fila, da approvare)**: intestazione come una
  vecchia agenda: il numero del giorno grande in amaranto (#8C2F39), sopra il giorno e sotto il mese (più anno e
  settimana); accanto meteo e massima del giorno; sotto appuntamenti, cose da fare, persone di oggi come prima; la massima
  non sta più in fondo.
- **Tela**: tolta la mia tavola `ScrBolleAdattaTanto` (superata dal codice) per fare posto ad `AgePagina`; aggiornata
  `ScrBolleAdattaImpostazioni`; `canvas.json` toccato. La tela resta a 512 file.

## 18 novembre: bolle che si adattano al contenuto (tavole da approvare)
- **Richiesta del proprietario**: un'opzione per le bolle della Scrivania che si adattano al contenuto: con pochi o nessun
  impegno e cosa da fare si riducono, con tanti si allungano.
- **Tavole** sulla pagina «Bolle» della tela, fila «Bolle che si adattano al contenuto · da approvare» (y 3900):
  `ScrBolleAdattaPoco` (giornata quasi vuota: una riga per bolla), `ScrBolleAdattaTanto` (giornata piena: righe fino
  all'altezza scelta, poi «Altri…»), `ScrBolleAdattaImpostazioni` (interruttore «Bolle che si adattano» in Impostazioni
  della Scrivania, spento di base; quali bolle si adattano e quali no; due bolle affiancate restano alte uguali, prendono
  la più piena). **Parte comune toccata**: `project/canvas.json` della tela (tre tavole e la nota `t-bol4`). La tela ora
  ha 512 file, il massimo: per le prossime tavole bisognerà toglierne qualcuna superata.
- **Oggi nel codice** (`HomeBoard`): l'altezza è fissa se scelta con la maniglia, se la bolla è affiancata (prende la
  scelta o la naturale più alta) o di base per alcune (Calendario 300, Messaggi 240, Appunti 200…); altrimenti è quella del
  contenuto. Con l'opzione accesa l'altezza scelta diventerà il massimo e il contenuto deciderà sotto.

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
- **Approvate e in codice dal 17 novembre (pomeriggio)**: CollAssistente, InsAssistente (agenda con l'assistente).
- **Approvate e in codice dal 17 novembre (sera e notte)**: ScrPagAggiungi, ScrPagGestisci, ScrPagNuova, ScrPagLibera (giro 760),
  ScrPagTrascina (giro 772).
- **Approvate, codice da scrivere**: RubElenco, RubNuovo, RubScheda (Rubrica arricchita, approvata il 17 novembre: il
  codice con l'area Comunicazione); CollDisponibilita, CollPassaPalla (con Comunicazione) e le Ins* di
  Comunicazione.
- **Approvate e in codice dal 17 novembre**: DriveCartelle, DriveCassetto, DriveCloud; «Condividi con…» per Drive e per
  gli appuntamenti (invitati).
- **Approvate e in codice dal 17 novembre (mattina)**: IcoColoreBolle (tema «Come le bolle», di base), CollElenchi,
  CollAffida; CollRiunione (affidamento delle azioni; manca la nota a più mani), CollSquadra.

## Prossimi passi, in ordine
1. **Prove del proprietario** sul giro 748 (contiene il 735; esito del 735 ancora da ricevere), più l'agenda con
   l'assistente (vedi la sezione del 17 pomeriggio): Ceruleo pieno (E di giorno, F di sera) e cangiante; app recenti senza il
   vecchio sfondo; icone «Sfondo delle bolle» (WhatsApp, Spotify, Telegram nei loro colori); Agenda (giorni, «@», «Parti
   alle…»); Drive (cassetto, spazio, Oggi sotto le cartelle, «Cambiato da ieri» in una cartella condivisa); «Condividi
   con…» su una cartella di Murena; invitati in un appuntamento di Murena; elenchi condivisi e «Affida a…» con un secondo
   account Murena; una riunione con invitati e «Assegna a tutti».
2. **kDrive e posta di Infomaniak**: verificato (sezione del 17 novembre, tarda mattina). Serve una **decisione del
   proprietario**: piano kDrive con WebDAV, oppure richiesta a Infomaniak dello scope «drive» per l'app OAuth; la posta si
   prova in Email (Comunicazione) con una password per le app.
3. **Collaborazione, la nostra parte, in ordine**: (agenda con l'assistente fatta nel giro 748: da provare, poi le
   proposte anche nell'Agenda) disponibilità in un link (verificare gli Appuntamenti di Nextcloud Calendar su Murena); nota della riunione scritta a più
   mani (Note che ricevono le note condivise); «Affida a…» con il trasporto di Comunicazione al posto dell'email a mano.
4. **Codice della Rubrica** con l'area Comunicazione: campi in `core/contacts` (titolo, soprannome, numeri ed email con
   etichetta, PEC come email con etichetta «PEC», indirizzi strutturati, azienda/reparto/ruolo, CF e P.IVA come campi
   personalizzati, date, siti, relazioni, nota), editor e scheda nell'APK Telefono; «Dove lo salvo?» con gli account.
5. **Agenda, rifiniture possibili**: nome nel saluto (dall'Accoglienza della Scrivania); invitati anche dal + unico.
6. Poi: audio delle note vocali nel cloud; simboli nuovi nei temi «Eurise» ed «Eurise a
   colori»; pulizia di Chip, Card, Pill e Field; foto e scansioni nelle Note; schermate del computer con l'area Computer.
- **Pagine della Scrivania a piacere**: in codice nel giro 760, da provare (creare pagine, «Aggiungi alla Scrivania…»,
  ordine, Apri da qui, solo in una modalità, elimina) e il trascinamento tra pagine (ScrPagTrascina, giro 772).
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

## Sicurezza (18 novembre 2026, notte, progetto Eurise Sicurezza)
- I fornitori dell'APK principale (segreti, aspetto, luoghi, privacy, destinazioni, Oggi, I tuoi giorni, meteo sul percorso)
  accettano solo il permesso della propria variante; backup della suite a 600 000 iterazioni per i nuovi; regole di
  estrazione complete. Dettagli in `docs/SICUREZZA.md`.

## Sicurezza, 21 novembre 2026 (progetto Eurise Sicurezza)
App Eurise: i segreti affidati dalle altre app (chiave di recupero della Chat) ora sono sigillati nel Keystore (`SuiteSecrets` in `core/cloud`), con una voce a parte nel backup (`lock/secrets.json`); migrazione da sola dal vecchio `eurise_secrets`. Dettagli in `docs/SICUREZZA.md`, «Crittografia».

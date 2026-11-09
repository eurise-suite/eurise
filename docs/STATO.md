# Eurise – stato dei lavori (8 novembre 2026, sera)

Nota di passaggio tra una chat e l'altra: chi riprende il lavoro legge prima questo file e `CLAUDE.md`.

## Riferimenti
- Documento di progetto: Claude Doc `81d73EgPHtgsSEd7YkavhU`.
- Tela di design "Icone della suite": https://claude.ai/artifact/AauRJ8e5WdH7nXTqAEXkoo
  (pagine: App e launcher, Icone, Calendario, Scrivania (launcher), Scrivania tablet, Note, Cloud, Attività,
  Drive e Scanner, Orologio, Galleria, Password, Integrazioni, Privacy).
- Repository: `eurise-suite/eurise`. Un ramo per lavoro (`launcher`, `note`, `disegno`, `cloud`…), unito in
  `main` quando il proprietario l'ha provato sul telefono.
  L'APK di prova si scarica da GitHub Actions (artefatto `eurise-debug-N` della compilazione su `main`).

## Metodo concordato con il proprietario
- Prima le anteprime sulla tela, se ne parla, e solo dopo l'approvazione si scrive il codice.
  Le correzioni chiare (bug, colori, spaziature) si fanno direttamente.
- Si compila solo su GitHub Actions; gli errori si leggono dalle annotazioni della compilazione.
- Le versioni di prova sono firmate con una chiave di debug fissa (`app/debug-key/`, pubblica e senza valore),
  così si aggiornano senza disinstallare; il `versionCode` cresce con il numero della compilazione.
- La chiave delle versioni ufficiali non si chiede e non si tocca: sta solo nei segreti di GitHub.
- Si parla in italiano.

## Scrivania (launcher, `feature/launcher`) – fatto
- Tre pagine: sinistra (Notizie, Borsa, Musica, Oroscopo, Ricetta), home, Riepilogo (Calendario, Comunicazioni).
- Home: saluto, orario con alba e tramonto, galleria e fotocamera, meteo, schede modificabili
  (pressione lunga: aggiungi, togli, ridimensiona in modo continuo, altezze unite con la catena),
  preferiti ancorati in basso, cartelle, barra delle categorie (fino a 8, Preferiti eliminabili, 50 simboli).
- Le categorie (Preferiti compresi, ora categoria come le altre) si aprono con le app in basso, vicino al
  pollice, e il titolo subito sopra; toccare di nuovo la categoria aperta torna alla home.
- Griglia e icone: tema (segue lo sfondo, chiaro, scuro) e sfondo (automatico o scelto: stagioni e feste).
- Schede della home: appuntamenti, Ti hanno cercato (scorrimento: Rispondi, Letto, Elimina; risposta rapida),
  Salute (Health Connect), Lavoro, Musica, Video dai canali (NewPipe), più le schede delle pagine laterali.
- Pannello Eurise (scorrendo in giù da ogni pagina): interruttori, luminosità e volume, impostazioni,
  notifiche raggruppate per app e conversazione. Ricerca (scorrendo in su): contatti, azioni, app,
  conversazioni, note e attività, documenti.
- Sfondi illustrati che cambiano con stagione, meteo, feste e date personali, momento del giorno.
- Tema scuro: testo che segue il tema, accento notturno più scuro, bolle e icone scure; il testo in alto
  resta scuro finché il cielo è chiaro (sera), chiaro di notte.
- Colore dei simboli delle icone: "Dall'app" (colore dell'icona originale portato alla tinta della suite),
  per categoria o vario.

## Calendario (`feature/calendar`) – fatto
- Date importanti (compleanni, anniversari, relazione, promemoria regalo 10 giorni prima).
- Farmaci con orari di assunzione; le voci entrano automaticamente tra le attività con un avviso.

## Note (`feature/notes`) – testo ricco e disegno provati e uniti a `main`
- Anteprime approvate: tela, pagina "Note".
- Editor a blocchi: paragrafi, titolo e sottotitolo, elenchi puntati e numerati, elenchi di controllo,
  tabelle, collegamenti ad altre note, linea; grassetto, corsivo, sottolineato, colori ed evidenziatore
  nelle otto tinte; Annulla; barra di formattazione satinata che segue la tastiera.
- Voci di un elenco di controllo aggiungibili alle Attività: spuntarle nella nota spunta l'attività,
  e all'apertura la nota riprende lo stato dall'attività.
- Taccuini con tinta, tag ricavati dalle parole con `#`, note fissate, cestino che si svuota dopo 30 giorni,
  schermate Organizza e Cestino.
- Salvataggio: database del telefono (Room v2, migrazione da v1) con il contenuto in HTML5 aperto
  (`doc/NoteHtml.kt`, con test). Lo stesso HTML diventerà la cartella della nota su Murena con la
  sincronizzazione: `NoteHtml.document()` produce già il file completo.
- Disegno (provato col dito; col pennino si prova quando arriva il tablet): blocco disegno salvato come SVG dentro l'HTML (contorni pieni + punti
  originali in `data-points`), editor a schermo intero con penna e matita sensibili alla pressione,
  pennarello, evidenziatore, gomma, righello, inchiostro e otto tinte, tre spessori, fogli bianco/righe/
  quadretti, annulla e ripeti, forme riconosciute con "Tieni a mano", dito ignorato quando si usa il pennino.
  Codice in `doc/Drawing.kt` (geometria e forme, con test) e `ui/DrawingCanvas.kt`.
- Prossimi passi delle Note: note bloccate, foto e scansioni, note vocali, tema Notte da rivedere sul telefono.

## Cloud – note sincronizzate (provato con Murena e unito a `main`)
- Anteprime approvate: tela, pagina "Cloud".
- `core/cloud`: account con password per le app cifrate dal Keystore (`CloudAccounts`), client WebDAV
  (`WebDav`, OkHttp, con test), accesso dal browser per Murena e Nextcloud (`NextcloudLogin`, Login Flow v2),
  regia e lavori in background (`CloudSync`, `SyncWorker` con WorkManager: ogni 30 minuti e 8 secondi dopo
  la chiusura di una nota). I lavori si registrano in `EuriseApplication.onCreate`.
- Note: cartella `Eurise/Note`, un file `<uid>.html` per nota (documento completo, apribile nel browser;
  taccuino, fissata, date e cestino nei `<meta name="eurise:…">`). Scelto un file per nota invece di una
  cartella per nota: un solo PROPFIND elenca tutto con gli etag; gli allegati andranno in `Eurise/Note/<uid>/`.
- Sincronizzazione a tre vie (`NotesSyncJob`, `doc/NoteMerge.kt` con test): ciò che cambia da una parte sola
  passa all'altra, i cambi in punti diversi si uniscono, la stessa parte cambiata in due modi apre
  "Cambiata in due posti" nell'editor. Ogni PUT/DELETE usa If-Match o If-None-Match.
- Database v3 (migrazione da v2): uid, etag, base dell'ultima sincronizzazione, conflitto, eliminazioni.
- Schermate (`feature/cloud`): account con spazio usato, cosa va dove, collega (Murena, Nextcloud, kDrive,
  WebDAV; Proton Drive "in attesa"), sincronizzazione con ultimi passaggi. Si apre da Note → Organizza → Cloud.
- Prossimi passi del cloud: Attività via CalDAV (elenco "Attività" su Murena), copia delle impostazioni,
  allegati delle note, note bloccate cifrate (formato Cryptomator), account di sistema Android.
- Proton Drive: SDK in anteprima, non pronto per app di terze parti, senza login (gennaio 2026).
- Murena: collegamento dal browser provato e funzionante (spazio mostrato, sincronizzazione partita).
- kDrive: rifiutato con 403. L'app usa `https://<numero>.connect.kdrive.infomaniak.com/` e, se rifiutato,
  `https://connect.drive.infomaniak.com/<numero>/`; mostra il codice del server. Cause possibili: numero del
  kDrive sbagliato (si legge in kdrive.infomaniak.com/app/drive/NUMERO/…), password non "per le app", piano
  senza WebDAV (non incluso in kSuite Free e Standard). Prova del proprietario nel browser con il numero giusto:
  `<error/>` senza richiesta di credenziali (un WebDAV attivo chiederebbe email e password), quindi molto
  probabilmente il piano non include WebDAV. Confermato: il piano è **my kSuite+**, che secondo Infomaniak
  non include WebDAV. Strada scelta da valutare: l'API di kDrive con un token personale (portata `drive` e
  `user_info`, creato in Manager → token API), come fanno FolderSync e l'integrazione kDrive di Home Assistant.
  Da verificare che l'API risponda anche con my kSuite+ prima di costruire il client.

## Attività (`feature/tasks`) – scritte nei rami `attivita` e `leggerezza`, da provare sul telefono
- Tela, pagina "Attività" (anteprime approvate). Decisioni del proprietario (8 novembre): importanza a tre livelli
  (No, Media, Alta = PRIORITY 0, 5, 1 di CalDAV); attività con un orario anche nel Calendario, interruttore acceso
  di base; Farmaci e Date importanti come elenchi automatici, di base solo sul telefono, con interruttore
  "Sincronizza" e avviso sui rischi ("Lascia sul telefono" in evidenza).
- Fatto nel ramo `attivita` (compila con i test verdi; contiene anche `sveglia-home`):
  - Database v4 (`MIGRATION_3_4`, nessun dato perso): tabella `task_lists` con tre elenchi a id fisso (1 Attività,
    2 Farmaci, 3 Date importanti) creati anche sulle installazioni nuove; nelle attività `list_id`, `due_minute`,
    `remind_before`, `rrule`, `priority`, `parent_id` (passi).
  - `Recurrence` (RRULE: FREQ, INTERVAL, BYDAY, con test): spuntata un'attività che si ripete nasce la prossima,
    con i passi da rifare; quella fatta resta come storico senza ripetizione.
  - `TaskSettings`: "Mostra le fatte in fondo" e "Attività anche nel Calendario" (il Calendario usa
    `observeForCalendar`).
  - Schermate: elenco con viste Oggi (In ritardo, Oggi, Domani, poi "Più avanti · Fatte oggi"), Programmate,
    Importanti, Tutte ed elenchi; ricerca; scheda con salvataggio automatico (elenco, scadenza, ora, avviso,
    ripetizione, importanza, passi, note, nota d'origine); aggiunta rapida "come si parla" (`QuickAdd`, con test)
    con i pezzi evidenziati e togliibili; elenchi con modifica, tinta, avviso di sincronizzazione e preferenze.
  - Promemoria: le voci automatiche di `Reminders.kt` vanno negli elenchi Farmaci e Date importanti (anche quelle
    già esistenti); gli avvisi delle attività usano la stessa sveglia dei farmaci; l'app riprogramma quando un avviso
    cambia (`TaskRepository.onRemindersChanged`, impostato in `EuriseApplication`).
- Da provare sul telefono: migrazione dei dati esistenti, aggiunta rapida, avvisi all'ora giusta, ripetizioni.
- Poi: sincronizzazione CalDAV (VTODO) con Murena per gli elenchi con "Sincronizza" acceso; collegamento a un
  contatto (previsto nell'anteprima, non ancora fatto).

## Anteprime nuove da discutere (8 novembre, nell'ordine del documento di progetto)
- Drive e Scanner (pagina "Drive e Scanner"): un solo navigatore per telefono, Murena e kDrive con pillole per
  luogo, cartelle sincronizzate e cifrate, spazio usato; file aperto con offline, link, versioni e scadenza letta;
  scanner con bordi e scatto automatico; esito con nome automatico, cartella proposta e promemoria della scadenza.
- Orologio (pagina "Orologio"): sveglie con frase "suona domani alle…", festivi e ferie dal calendario,
  sveglia smart, ora di dormire; modifica della sveglia; timer multipli, Pomodoro, avvio rapido.
- Galleria (pagina "Galleria"): foto per giorno con barra di anni e mesi, coda di caricamento solo Wi-Fi,
  Accadde oggi con "Mettile in un diario di viaggio", eliminazione a tre scelte (di base "Solo dal telefono").
- Servizi aggiuntivi (richiesta del proprietario, che non li usa ma "ad altri potrebbero interessare"): Google Drive,
  OneDrive, Dropbox e MEGA nel gruppo "Altri servizi" di Cloud → Collega (tela, pagina Cloud). Nelle pillole di
  Drive compaiono solo i servizi collegati. Note tecniche: Google Drive API v3, Microsoft Graph, Dropbox API v2 con
  accesso OAuth dal browser (niente librerie Google proprietarie, vedi CLAUDE.md); per MEGA l'SDK ufficiale
  (C++, licenza BSD) o la sua API, da valutare. Le prime tre richiedono di registrare l'app presso ciascun
  servizio (identificativo dell'app): lo fa il proprietario. Su F-Droid potrebbero far comparire l'avviso
  "servizi di rete non liberi" (NonFreeNet): da verificare prima della pubblicazione.
- Da decidere: quale delle tre si scrive per prima; se Drive, Orologio e Galleria entrano nell'app hub come
  sezioni o restano app separate (il documento prevede fork di Fossify per Orologio e Galleria).

## Decisioni dell'8 novembre (documento di progetto, sezioni in fondo)
- Struttura degli APK: via di mezzo (vedi CLAUDE.md). Integrazioni tra le app approvate tutte, in quest'ordine:
  collegamenti tra tutto, scheda Persona, un solo +, Tastiera come ponte, riunioni, viaggi, modalità, cestino e
  trasferimento unici. Funzioni in più per ogni app approvate (tabella nel documento).
- Leggerezza come regola fissa (CLAUDE.md). Ramo `leggerezza` (parte da `attivita`): via material-icons-extended e
  gli strumenti di anteprima, risorse solo in italiano e inglese, pezzi grafici comuni in `core/designsystem`
  (`FilterPill`, `SectionLabel`, `CardDivider`, `CalendarTint.named`, `tone()`). Risultato: APK di prova da circa
  21 a 13,6 MB; versione ufficiale 2,5 MB. La compilazione pesa entrambi a ogni giro (tetto 6 MB).
- Anteprime nuove sulla tela, pagina "Integrazioni": evento con la striscia "Collegati", collegare qualsiasi cosa,
  scheda Persona, un solo +. Da discutere.

## Collegamenti tra tutto (ramo `collegamenti`, parte da `leggerezza`) – scritti, da provare sul telefono
- Anteprime: tela, pagina "Integrazioni" (approvate l'8 novembre insieme alla regola della privacy chiara).
- `core/data`: `LinkRef` (Note, Task, Event, Contact + chiave), tabella `links` (coppia ordinata, una riga per
  legame), database v5 (`MIGRATION_4_5`), `LinkRepository` con i legami impliciti nota–attività.
- `core/links` (modulo nuovo): `LinkResolver` (legge e cerca note, attività, eventi, contatti sul telefono; contatti
  e calendario solo con permesso già dato, `Resolved.Unavailable` altrimenti; `Resolved.Gone` scollega da sé),
  `LinkedSection` (sezione "Collegati") e `LinkPickerSheet` (ricerca in tutta la suite, scelta multipla).
- Dove compare: scheda dell'attività, fondo delle note salvate, evento esistente. I moduli ricevono uno spazio
  `linked` dall'app; l'app apre la cosa collegata sopra a quella di partenza (`openLinked` in `EuriseHub`).
- Da provare: collegare una nota a un evento e a un'attività, aprire i collegati, togliere un collegamento,
  cancellare un evento dal calendario e vedere sparire il collegamento.
- Prossimi passi delle integrazioni: scheda Persona, un solo +, poi Tastiera, riunioni, viaggi, modalità.

## Privacy (tela, pagina "Privacy") – anteprime da discutere
- Panoramica (dove stanno i dati, servizi esterni con i dati che ricevono, interruttori), foglio "Cosa cambia
  per la tua privacy" prima di accendere una funzione, registro degli accessi (solo sul telefono, 30 giorni).
- Servizi esterni trovati nel codice: Open-Meteo (posizione a 2 decimali), OSRM e Nominatim (percorsi),
  Yahoo (borsa), siti di notizie e ricette, oroscopi.info, video dei canali.

## Punti aperti
- Tablet: da provare sul Volla quando arriva (pagina "Scrivania tablet" sulla tela).
- Oroscopo: nuova fonte per segno (oroscopi.info); da confermare sul telefono che il testo compaia.
- Fonti non ancora verificate sul telefono: Borsa (Yahoo), alcuni feed di notizie e ricette, video dei canali.
- kDrive da sistemare (vedi Cloud).
- Home: prossima sveglia sotto alba e tramonto (icona piccola e orario, terza riga della stessa colonna; con il
  giorno davanti se manca più di un giorno). Ramo `sveglia-home`, già unito in `attivita`. Legge la sveglia che
  l'app Orologio comunica al sistema (`AlarmManager.nextAlarmClock`).
- Password (tela, pagina "Password", 8 novembre notte): cassaforte con il controllo delle password, voce con
  codice 2FA e passkey, registrazione su un sito con alias e password generata. Da discutere.
- Bolle della home (ramo `bolle`, 8 novembre): "Ti hanno cercato" e la scheda Comunicazioni sono una bolla sola
  (`home/Comms.kt`): linea lievissima tra le righe, fino a 20 comunicazioni, striscia a destra con barra di scorrimento;
  verso destra (o tocco) la conversazione in grande con Rispondi, Segna come letto, Nascondi (maschera: sparisce da
  Eurise, nell'app resta da leggere, nessuna spunta) ed Elimina; verso sinistra Elimina. Regola: in ogni bolla si fa
  tutto lì, l'app si apre solo a richiesta. Proposte sulla tela (pagina "Bolle della home"): Da fare oggi, Prossimo
  impegno, Codici e consegne, Farmaci, Timer e sveglie, Appunto al volo: approvate e fatte (`home/Bubbles.kt`, si
  aggiungono dal catalogo delle schede della home). Codici e consegne è spenta di base, con spiegazione all'accensione.
  Il lettore QuickAdd è passato in `core/data`. APK di prova 109, versione ufficiale 2,6 MB.
- Eurise Orologio (ramo `orologio`, nato da `bolle`, 8 novembre): nato come APK separato e poi unito all'APK
  principale su richiesta del proprietario (modulo `feature/clock`, icona propria nel cassetto), senza database
  (preferenze JSON) e senza librerie nuove.
  Sveglie con festivi italiani, ferie e "prima se c'è un impegno presto" dal calendario (spiegazione all'accensione,
  "Usa il calendario" si spegne senza conferme), suono che cresce, rimanda, piccolo calcolo, routine del mattino a
  voce, ora di dormire con avviso, Salta. Timer multipli, Pomodoro con Non disturbare, avvio rapido, cronometro,
  fusi. Risponde a SET_ALARM/SET_TIMER. `core/suite` contiene ClockContract (messaggi interni tra Scrivania e
  Orologio) e resta per i futuri APK separati, con il permesso di firma (SUITE per le ufficiali, SUITE_DEBUG per le prove).
  Da fare: timer lanciati dalle ricette della Scrivania; meteo nella routine (oggi lo ha solo la Scrivania);
  sveglia calcolata sul tempo di viaggio; centro privacy che elenchi il calendario dell'Orologio.
- Backup cifrato (ramo `orologio`, 8 novembre, regole in CLAUDE.md "Dati e backup"): motore in
  `core/cloud/backup` (BackupCrypto AES-256-GCM con chiave dei dati chiusa da frase e codice di recupero,
  PBKDF2 310.000 giri; Retention tre recenti + mensile + trimestrale; BackupArchive con database e impostazioni,
  fuori cache, account cloud e chiavi; destinazioni WebDAV in Eurise/Backup o cartella del selettore; BackupWorker
  giornaliero con Wi-Fi e in carica che rispetta la frequenza). Schermata `feature/cloud/BackupScreen.kt`
  (Cloud → Backup cifrato; sezione "backup" dalla Scrivania; passo "Hai un backup?" nella scheda di avvio).
  Il ripristino prepara i file e li applica al riavvio (`EuriseApplication`). Gli account cloud vanno ricollegati
  dopo un ripristino. Da fare: comparire nel centro privacy; Proton e MEGA solo via selettore delle cartelle.

## Dove siamo (8 novembre, fine giornata)
- `main` contiene: Attività, sveglia nella home, leggerezza, collegamenti.
- Ramo `orologio` (da provare e poi unire a `main`): bolle (Ti hanno cercato unificata + sei bolle nuove),
  Orologio dentro l'APK principale, backup cifrato, scorrimenti secondo il tipo di comunicazione (chiamata:
  Richiama / Vista e Richiamo; mail: Leggi / Da fare, Archivia, Elimina; chat: conversazione / Da fare, Elimina),
  vocali in bolla quando l'app allega l'audio, SwipeRow comune a tutte le bolle. Ultimo APK di prova:
  eurise-debug-118 (versione ufficiale 2,7 MB).
- Sul ramo `orologio`, dopo la prova dell'APK 118 (note del proprietario "Note Eurise" e "Categorie Eurise"):
  Note con testata e pillola opache, elenchi allineati e con spazio pari all'interlinea, modalità Seleziona (⋯ →
  Seleziona testo, sola lettura, selezione su tutti i punti), selezione multipla nell'elenco (pressione lunga: fissa,
  sposta, condividi, cestino), icona Note e + nella bolla Appunto al volo. Scrivania: otto categorie predefinite
  (Tutte, Soldi, Strumenti, Media, Lavoro, Comunicazione, Browser, AI; riconoscimento in `apps/AppClassifier.kt`),
  app trascinabili con la pressione lunga dentro la categoria e, tenendole sul bordo dello schermo, verso quella
  accanto (ordine in `appOrder`); "Ripristina le categorie predefinite" nell'editor. Scritto senza anteprima sulla
  tela su decisione del proprietario. Da provare sul telefono.
- `orologio` unito a `main` (1e36de4) su indicazione del proprietario. Ramo `galleria` (parte da `main`): Orologio come
  sezione dell'app tra Calendario e Note (`ClockSection`, pillole in alto al posto della barra propria); primo pezzo
  della Galleria, modulo `:gallery`, APK separato `eu.eurise.gallery`, artefatto `eurise-galleria-debug-N`, Android 11+
  (cestino di sistema), scritta in Compose senza Fossify e senza librerie per le immagini, senza Internet: foto per
  giorno, barra anni e mesi, filtri, Album, visore con zoom, preferite, selezione, cestino, Accadde oggi.
  Mancano: coda di caricamento e scelta telefono/cloud, Luoghi, Cerca, diario di viaggio, condivisione senza
  posizione, cartella protetta, editor, pulizia, riconoscimento locale, permesso di firma con l'hub.
- Sul ramo `galleria`, dalla prova del proprietario: Orologio anche tra le app della Scrivania (`eurise:clock`) e
  apertura dall'orario dentro Eurise; `ScrubRail` comune (lettere in Tutte, mese e anno nella Galleria, si ingrandisce
  sotto il dito con la data); pulsanti galleria e fotocamera accanto all'ora con scelta dell'app a pressione lunga;
  bolle della home trascinabili; bolle nelle pagine laterali (`CardKind.bubbleId`, `HomeBubble`) e Calendario e
  Comunicazioni tra le schede della home.
- Galleria provata dal proprietario sull'APK 124: corretti i ricordi nascosti dalla testata e l'etichetta "Calendario"
  tagliata nella barra a cinque voci. `galleria` unito a `main`.
- Prossimo: Password (APK separato, ramo `password` da `main`). Riferimenti: tela PwdCassaforte, PwdVoce, PwdRegistra;
  documento, sezione Password. Da decidere con il proprietario prima del codice: la "strada A" del documento
  (cassaforte in un file KeePass .kdbx su kDrive). Nella Galleria restano pulizia e cartella protetta; il caricamento
  su kDrive aspetta Drive.
- Ramo `aod` da `main`: display a riposo (tela, pagina "Display sempre attivo"; `feature/launcher/.../aod`, Activity in
  `app`). Eccezione concordata con il proprietario alla regola sui servizi: `AodService` esiste solo se la funzione è
  accesa. Limite di Android: un'app non può spegnere lo schermo, a tempo scaduto diventa nero e lo spegne il sistema.
  Il sollevamento usa il sensore a basso consumo, se il telefono lo espone. Salute e Meteo non ancora tra le bolle.
- Lessico: gli elementi della home si chiamano **bolle** (non pillole). Ogni bolla scorre con la barra a destra se il
  contenuto non entra (`Pill`, titolo fisso); con la tastiera aperta i preferiti sotto le bolle si nascondono.
- Password (unita a `main` il 9 novembre, passkey provate sul telefono su webauthn.io): modulo `:password`, APK separato `eu.eurise.password`, artefatto
  `eurise-password-debug-N`, Android 8+. Decisione del proprietario (9 novembre): file KeePass `.kdbx` sul telefono,
  sincronizzato su Murena via WebDAV (`Eurise/Password/eurise.kdbx`), interruttore spento di base con spiegazione;
  kDrive si aggiunge con Drive, perché il piano my kSuite+ non ha WebDAV e serve l'API con token. Sulla tela la
  pillola dice ancora "kDrive". Fatto: formato KDBX 4 in `kdbx/` senza librerie (Argon2 d/id, BLAKE2b, ChaCha20;
  test con i vettori RFC, con un file creato da pykeepass, e la compilazione fa riaprire a pykeepass il file scritto da
  noi), campi sconosciuti conservati, cronologia, cestino, unione voce per voce con la versione perdente in cronologia,
  apri/importa file, prendi da Murena, codici 2FA, generatore (frase o caratteri), controllo locale deboli/riusate,
  blocco a mano e dopo 2 minuti fuori dall'app, schermo protetto da screenshot, appunti riservati e cancellati dopo
  45 s. Secondo pezzo (9 novembre, tele approvate tutte dal proprietario): impronta con API di sistema (Android 9+),
  carte (PwdCarta, campi aggiuntivi della voce), `autofill/` con antifrode sul dominio (`Matching`), foglio PwdRegistra,
  alias (PwdAlias) con SimpleLogin (la via per Proton: Proton Pass non risulta avere API pubbliche), addy.io e indirizzo
  fisso (Murena ha un solo alias, senza API). kMail non ancora: parametri API da verificare e il piano my kSuite+ ha 2 alias.
  Importazione (`logic/Importer`, senza anteprima su richiesta del proprietario): Proton Pass ZIP/JSON non cifrato, Proton
  Authenticator, Aegis, 2FAS, Bitwarden, CSV (browser e altri), KeePass, QR di Google Authenticator; apertura e condivisione
  dei file verso Password; le passkey si trasferiscono solo da KeePass.
  Controllo violazioni (Have I Been Pwned, interruttore spento di base), salvataggio dei login digitati, riquadri Blocca e
  Genera. Mancano: kMail tra gli alias, Famiglia. Passkey in `passkey/` (Android 14+, formato KPEX di KeePassXC, browser approvati dalla persona): la catena e' stata provata sul telefono con KeePassDX su webauthn.io il 9 novembre. Poi Drive e Scanner (tele approvate).
  Il centro privacy dovrà elencare Murena per questa app.
- File, Scanner e PDF (uniti a `main` il 9 novembre): File e Scanner dentro l'APK principale (decisione del 9 novembre, come Orologio: stessa
  integrazione, peso piccolo; icone `eurise:drive` e `eurise:scanner`, senza voce nella barra). Moduli `feature/drive`
  (telefono + account WebDAV, Recenti, Offline, Cestino telefono 30 giorni, lettore PDF di sistema con zoom) e
  `feature/scanner` (CameraX, bordi e scatto automatico in `EdgeDetector`, raddrizzamento, filtri, PDF su telefono o
  cloud), QR e ritaglio; link con password e scadenza, Condivisi, versioni e sposta (Nextcloud); PDF: firma e annota su copia,
  ricerca da Android 15. Office resta APK separato. Mancano: lettura del testo (OCR, serve scelta sul peso), quindi nome
  automatico, scadenze e ricerca nei PDF; cartelle cifrate; kDrive via API. Cartelle allineate in `FolderSync` (decisioni in `SyncPlanner`,
  con freno se sparisce troppo; automatico ogni 6 ore solo in Wi-Fi). Peso della versione ufficiale: da 2,7 a 3,5 MB, quasi tutto CameraX. MANAGE_EXTERNAL_STORAGE va rivisto se un giorno si pubblica su Play.
- Resto della Galleria (ramo `galleria-completa`, 9 novembre, APK 165, da provare sul telefono e poi unire a `main`): dock Foto, Album, Luoghi, Cerca
  con fotocamera a lato. `data/GalleryStore` (SQLite di sistema: caricate, posizioni, nomi, diari), `UploadWorker` (Eurise/Foto/AAAA/MM, solo Wi-Fi di base,
  cartelle scelte, spento di base con spiegazione), eliminazione a tre scelte, foto "solo cloud" con miniatura locale, Luoghi (caselle da circa 10 km, elenco senza
  mappa, nomi da Nominatim con interruttore spento), Cerca locale (date, tipo, cartella, luogo), diari, condivisione senza posizione (`data/Files.kt`), cartella
  protetta (`SecureFolder`, `logic/SecureStream`; dati persi se si disinstalla senza tenerli), editor su copia, pulizia. Solo GalFoto, GalElimina e GalAccadde avevano
  un'anteprima: Luoghi, Cerca, diario, cartella protetta, editor e pulizia sono nello stile della suite, senza tela. Account: Murena e Nextcloud, collegati dentro la
  Galleria; kDrive arriva con l'API. Mancano: impronta per la cartella protetta, luogo nella scheda Accadde oggi, esportazione del diario. Il centro privacy dovrà elencare
  il cloud scelto e Nominatim per questa app.
- Attenzione: il 9 novembre `main` (6a16e07) NON conteneva i sei commit del ramo `bolle` (lente, ricerca universale, Musica, bolle): stavano solo in `integrazione`.
  Per questo l'APK 165, nato da `main`, risultava più vecchio. `galleria-completa` ora include `integrazione`; prima di un ramo nuovo controllare che `main` abbia tutto.
- Cartelle cifrate di File (ramo `galleria-completa`, APK 169, da provare): formato Cryptomator 8 in `core/cloud/crypto/Cryptomator.kt` (senza librerie, vettori RFC nei test, la compilazione
  fa aprire una cartella a pycryptomator), `feature/drive/VaultFs.kt`, solo su account cloud; si creano col lucchetto in una cartella cloud, si richiudono uscendo da File. Controllata solo la lettura da parte di un altro
  programma: quella di una cartella creata da Cryptomator non ha ancora un test. Alias kMail in Password (`AliasStore`, token + numero del servizio + casella): endpoint presi da fonti non ufficiali, mai provati; servono diritti di amministratore.
- Regola dei due tocchi (CLAUDE.md): alias kMail tolto dalla schermata (il codice in `AliasStore` resta) finché non c'è l'accesso dal browser; SimpleLogin e addy.io, che chiedono una chiave, sono da rivedere.
  kDrive: provato il 9 novembre con un'applicazione "Cellulare" registrata dal proprietario (ID a9bba3c7-42e3-4fae-89ad-0efc0111426b, redirect eurise://kdrive): login OAuth2 con PKCE senza chiave segreta riuscito, ma Infomaniak concede solo
  "profile email phone"; lo scope "drive" dà invalid_scope e l'API risponde 403 all_scopes ("require this specific scope: drive"). kDrive tolto da Cloud → Collega (modulo e codice di prova; il ramo di codice WebDAV resta) finché Infomaniak non abilita lo scope. Da riprendere solo se il supporto risponde. Famiglia: rimandata dal proprietario (9 novembre) alla fine della road map; anteprime PwdFamiglia* già sulla tela, non approvate.
- Centro privacy (ramo `galleria-completa`, 9 novembre, anteprime approvate): modulo `feature/privacy`, icona `eurise:privacy` tra le app; `core/suite/PrivacyCenter` con interruttori e registro (30 giorni, sul telefono). Meteo, notizie e ricette, borsa, oroscopo,
  video e percorsi controllano l'interruttore prima di contattare il servizio; Percorsi è spento di base come nell'anteprima. Il registro annota questi contatti e i passaggi del cloud, non le letture locali (contatti, calendario). Galleria e Password sono descritte ma non comandabili da qui: servirà il permesso di firma.
- File e altre app cloud: pillola "+ Aggiungi" = selettore di Android (`TreeFs`, permesso persistente): cartelle di kDrive, Proton Drive, Nextcloud ecc. esposte dalle loro app, senza API né token. Sfogliare, aprire, caricare; niente link, versioni, offline né cartelle cifrate.
- Attività su Murena (ramo `galleria-completa`): `feature/tasks/TasksSyncJob` e `core/data/model/Vtodo` (con test), database v6 (`MIGRATION_5_6`: uid, etag, base, `task_tombstones`). Interruttore in Cloud, spento di base, sullo stesso account delle note; un elenco con Sincronizza = un calendario VTODO (cerca lo stesso nome, altrimenti crea `eurise-<id>`). Unione campo per campo, a parità vince il telefono; campi sconosciuti e promemoria del server conservati, gli avvisi di Eurise restano sul telefono. Da provare sul telefono.
- Cloud, regola di fondo (9 novembre): Eurise fa da hub. Murena/Nextcloud è il canale di sincronizzazione piena (note, attività, foto, backup); kDrive, Proton e simili entrano solo come cartelle dalle loro app (selettore di Android), per documenti, backup e al più copia foto in un verso, mai togliendo foto dal telefono su quella base. Prossimo: integrazioni (scheda Persona, un solo +, Tastiera, riunioni, viaggi, modalità, cestino unico), poi lettura del testo, Browser; Famiglia in fondo. `galleria-completa` va unito a `main` dopo la prova del proprietario.
- Residui ancora da fare: Password (Famiglia: "cassaforte condivisa separata" nel documento, nessuna anteprima), File (kDrive: bloccato, vedi sopra).
- Ultimo APK: 181 dal ramo `galleria-completa`, che contiene anche `integrazione`; su `main` 161 (`eurise-debug-N`, `eurise-password-debug-N`, `eurise-galleria-debug-N`). Compilare da una base aggiornata di
  `main`: un ramo vecchio dà un APK a cui mancano i lavori recenti.
- Prossimi passi, in ordine: resto della Galleria (caricamento su cloud, Luoghi, Cerca, diario di viaggio, condivisione senza
  posizione, cartella protetta, editor, pulizia); kDrive (bloccato, in attesa di Infomaniak);
  lettura del testo per Scanner e PDF come APK aggiuntivo (motore nativo troppo pesante per il principale); Browser su GeckoView con
  prototipo minimo per passkey e compilazione, Matrix per ultima; poi le integrazioni
- Ramo `bolle`: tutte le pagine della Scrivania usano lo stesso modello (`HomeBoard`, `HomeSlot`; `rightSlots`, `leftSlots`,
  convertite una volta dalle vecchie schede). I prossimi impegni e I tuoi appuntamenti con azioni per evento (`EventEntry`),
  tempo per ogni luogo (`refreshTravels`, anche #luogo nelle attività). Musica unica, Lavoro diventa Documenti.
  `SummaryBoard` e `CardCatalog` non sono più usati: da togliere.
- Sempre su `bolle`: Musica con più lettori (`NotificationStore.players`), barra unica con lente e browser jolly, Pannello e
  ricerca a scorrimento spenti di base (Griglia e icone), ricerca universale con contatti, browser, AI, Note, Attività, Calendario.
  L'icona Drive nella ricerca si aggiunge quando il ramo `documenti` è in `main`.
- Prossimi passi, in ordine: resto della Galleria,
  Password (APK separato), Drive e Scanner, con le funzioni in più del documento di progetto; poi le integrazioni
  (scheda Persona, + unico, Tastiera, riunioni, viaggi, modalità, cestino unico); centro privacy; sincronizzazione
  CalDAV delle Attività con Murena; in fondo Famiglia di Password.

## Integrazioni: scheda Persona e un solo + (ramo `galleria-completa`, 9 novembre, da provare sul telefono)
- Sulla tela, pagina Integrazioni, esistono anteprime solo per Collegamenti, Persona e un solo +. Tastiera, riunioni, viaggi, modalità e cestino unico NON hanno ancora anteprime: per CLAUDE.md prima si disegnano.
- Scheda Persona: modulo `feature/people` (`PersonRoute`, `PersonSource`), rotta `person/{key}`, richiesta `person:<chiave>` dalla Scrivania. Si apre dai collegamenti e dai contatti della Scrivania. Chiama, Messaggio, Email, Invita (apre il + con la persona già citata), compleanno (Date importanti, poi Rubrica), prossimi impegni con lei (invitata per email), Collegati.
  Non ancora: foto insieme (servono riconoscimento dei volti e permesso di firma con la Galleria), ultimo messaggio, file condivisi (File non è collegabile).
- Un solo +: `core/links/QuickCaptureSheet`, lettore `core/data/model/Capture` (con test). Tondo + accanto alla barra della Scrivania (telefono) e + in Oggi; gli altri + restano contestuali. "@Anna" collega ciò che nasce al contatto.
  Differenze dall'anteprima, volute: la persona viene collegata ma non aggiunta agli invitati (su un calendario sincronizzato partirebbe un invito vero); niente microfono finché non c'è la Tastiera di Eurise (la dettatura di sistema manderebbe la voce a un servizio non elencato nel centro privacy).

## Nextcloud alla pari di Murena (9 novembre, ramo `galleria-completa`, da provare su un Nextcloud vero)
- Il proprietario usa Murena; altri utenti potranno usare Nextcloud. Password chiede dove sta la cassaforte (`CloudChoiceDialog`); testi generici; `NextcloudLogin.Result.davUser` prende l'identificativo dal server (su molti Nextcloud differisce dal nome di accesso).
- Calendario e Contatti passano dal sistema: su Nextcloud servono DAVx⁵ o simili. Certificati autofirmati non gestiti.

## Office: decisione del 9 novembre (dopo le integrazioni)
- Un solo APK Eurise Office, motore LibreOffice core partendo da Collabora Office per Android (MPL 2.0), due interfacce sullo stesso documento:
  telefono = interfaccia nostra per le modifiche al volo (barra che cambia con la selezione, barra delle formule, revisioni, commenti, PDF) più
  "Tutte le opzioni" che apre l'interfaccia completa; tablet Volla (12,6", 2560×1600, Helio G99, 12 GB, tastiera) = interfaccia completa con
  tastiera e mouse, documenti affiancati, sezioni satinate della suite. Nextcloud Office/Euro-Office (AGPL, gira dal server) solo come
  "Apri nell'editor del cloud" da File.
- Primo passo: prototipo che compila solo il motore su GitHub Actions e misura spazio, tempo e peso. Se non ci sta: computer di compilazione
  o parti già compilate, da decidere con il proprietario. Poi anteprime telefono e tablet.

## Tastiera come ponte: anteprime (9 novembre, tela, pagina Integrazioni, seconda fila) – da discutere
- TastBarra (barra di Eurise sopra i tasti: Disponibilità, File, Persona, Codici), TastDisponibilita (orari liberi scelti e scritti nel testo,
  "forse" nel Calendario), TastFile (link con scadenza o allegato; il link lo crea File, la tastiera non ha Internet), TastCodici (codice 2FA
  proposto solo se il sito coincide, cassaforte aperta con l'impronta). Base prevista: fork di HeliBoard (GPLv3), APK separato, dialogo con
  l'APK principale e con Password solo con permesso di firma.

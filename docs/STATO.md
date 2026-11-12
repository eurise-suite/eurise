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
- Ultimo APK: vedi la sezione "10 novembre, notte" in fondo (ramo `tastiera`) (contiene `galleria-completa`; prima 181 da `galleria-completa`), che contiene anche `integrazione`; su `main` 161 (`eurise-debug-N`, `eurise-password-debug-N`, `eurise-galleria-debug-N`). Compilare da una base aggiornata di
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
- 10 novembre, decisioni del proprietario sulla Tastiera: Disponibilità con calendario del mese (frecce per i mesi dopo), giorno scelto → orari liberi
  scorrevoli a destra e sinistra; fila dei numeri accesa di base (si può nascondere); simboli della pressione lunga in piccolo sui tasti; emoji e
  simboli; suggerimenti e strumenti (traduci, voce, penna, appunti, cursore, una mano, impostazioni) uniti in una fila con opzione per separarli;
  obiettivo: tutte le funzioni di Gboard, tastiera Apple e Samsung, tutto sul telefono. Base consigliata: HeliBoard (GPLv3, 4.2 di ottobre 2026,
  senza Internet); FlorisBoard è ancora beta senza suggerimenti. Punto debole: scrittura scorrendo (HeliBoard la ha solo con la libreria chiusa
  di Google, esclusa da CLAUDE.md): da costruire libera. Funzioni in più proposte (frase → attività, calcoli, arrivo alle…, lingua per
  conversazione, appunti protetti, comandi /, abbreviazioni, nomi della Rubrica, tablet) in attesa di scelta.
- 10 novembre: pulsante degli strumenti a destra (opzione a sinistra per i mancini); tutte e nove le funzioni in più approvate; base HeliBoard
  approvata. FUTO Keyboard esclusa come base: licenza FUTO Source First (solo uso non commerciale, niente sublicenze) incompatibile con GPLv3;
  utile solo il suo dataset di swipe in inglese (MIT) per il riconoscitore libero dello scorrimento.

## Tastiera, Voce (ramo `tastiera`, notte tra 10 e 11 novembre, da provare sul telefono)
- Ramo `tastiera` nato da `galleria-completa`. Eurise Tastiera in `keyboard/` (HeliBoard 4.2, vedi `keyboard/EURISE.md`), APK
  `eurise-tastiera-debug-N` (lavoro a parte nella compilazione). Eurise Voce in `voice/` (whisper.cpp in `third_party/`), APK
  `eurise-voce-debug-N`.
- Fatto: punto 1 (base, temi Perla/Notte, numeri e simboli, strumenti a destra, barra di Eurise con Disponibilità/File/Persona/
  Codici), punto 2 (le nove funzioni: /comandi, calcoli, @contatti, abbreviazioni, "Crea attività" via app Eurise, cose
  copiate, "arrivo alle", lingua per app, nomi della Rubrica, tablet con sola barra), punto 3 (scrittura scorrendo libera:
  `GlideDecoder`, prova su tracce simulate con percentuale nel riepilogo), punto 4 (dettatura con Whisper in Eurise Voce).
- Non fatti: traduzione (Bergamot, compilazione nativa molto pesante: va prima un prototipo come per l'Office) e scrittura a
  mano (manca un modello libero pronto). File come link dal cloud: serve un canale con File dell'app Eurise.
- Limiti noti: "Arrivo alle" usa l'ora dell'evento, non il tempo di viaggio; la lingua si ricorda per app, non per
  conversazione (la tastiera non sa con chi si scrive); i codici 2FA compaiono solo se la voce di Password è collegata
  all'app e la cassaforte è aperta.

## Riunioni, viaggi, modalità, cestino unico: anteprime (10 novembre, tela, pagina Integrazioni, terza fila) – da discutere
- IntRiunione: nota dell'evento aperta all'inizio (dall'avviso), presenti da toccare, ordine del giorno spuntabile, appunti in cui
  le righe con @persona e data diventano attività collegate all'evento alla chiusura; "Condividi il riassunto" solo a richiesta.
- IntViaggio: da un biglietto (PDF in File o condiviso verso Eurise, letto sul telefono) si preparano, ognuna togliibile: voli nel
  Calendario, sveglia e fuso nell'Orologio, cartella in File › Viaggi, diario nella Galleria, mappa offline (spenta di base, unica cosa
  che va su Internet, arriva con Mappe), modalità Viaggio nelle date. Dalle email solo quando ci sarà Mail.
- IntModalita: Lavoro, Casa, Viaggio, Notte; ognuna cambia Scrivania, notifiche, calendari ed elenchi, suoni; accensione per orario
  (salta festivi e ferie) o durante gli eventi di un calendario. Niente accensione per luogo, per non leggere la posizione.
- IntCestino: un cestino per Note, Attività, File, Foto ed eventi, 30 giorni, i collegamenti tornano con ciò che si ripristina; in fondo
  "Passa a un telefono nuovo" (backup cifrato o QR tra i due telefoni). Foto e Password dagli APK separati: serve il permesso di firma.

## In fondo alla road map: computer (10 novembre) – si parte solo con l'ok esplicito del proprietario
Priorità invariata: /e/OS e Android prima di tutto. Quanto segue viene dopo tutto il resto, Famiglia di Password compresa.
- Piattaforme: Linux desktop (Ubuntu, Fedora…), poi forse macOS e Windows. App: Office, lettore PDF, Note, Attività, Calendario,
  Browser e altre. Struttura proposta: Kotlin Multiplatform per la logica comune, Compose Multiplatform per l'interfaccia desktop
  (JVM: lo stesso codice gira su Linux, macOS e Windows; cambiano solo pacchetto e firma). Pacchetto Flatpak su Linux.
  Decisione da scrivere in CLAUDE.md prima di toccare la struttura (è difficile da cambiare dopo).
- Già da ora, a costo zero: la logica pura (formati, unioni, cifratura, WebDAV/CalDAV) resta senza `android.*`, così passerà
  ai moduli comuni senza riscritture.
- Cloud sul computer: i file arrivano già dal client Nextcloud/Murena. Note e Attività invece parlano direttamente WebDAV/CalDAV
  con la nostra unione a tre vie (scrivere nella cartella sincronizzata creerebbe copie in conflitto). Accesso dal browser in
  due tocchi anche lì. Cartelle cifrate aperte dalla nostra app (formato Cryptomator).
- Ordine: 1 moduli comuni multipiattaforma (telefono invariato); 2 Note e Attività; 3 Calendario; 4 lettore PDF (PDFium o
  poppler); 5 Office (LibreOfficeKit con la nostra interfaccia, "Tutte le opzioni" = interfaccia di Collabora); 6 Browser.
- Browser sul computer (idea del proprietario): Firefox così com'è, aggiornato da Mozilla, con la nostra interfaccia ed
  estensioni disattivabili per l'integrazione (password e passkey dalla cassaforte, invia a Note/Attività, collegamenti),
  in dialogo con l'app Eurise via native messaging. Limite: un'estensione cambia colori, barra laterale, nuova scheda,
  pulsanti e menu, non l'intera finestra. Da valutare se la stessa estensione serve anche su Firefox per Android al posto
  del browser GeckoView.
- Office online: la collaborazione in tempo reale passa da Collabora Online (Nextcloud Office) sul server dell'utente; le
  nostre app aprono quelle sessioni. Gli strumenti collaborativi nostri (chi modifica, commenti con @persona → attività,
  revisioni, collegamenti) funzionano ovunque. Un nostro server Office è sconsigliato (costi, privacy, manutenzione).

## 10 novembre, dopo la prova: home, web app, ricerca, I miei dati (ramo `tastiera`)
- Fatto (APK 187): tolto il + accanto alla barra della home (la cattura passa dalla ricerca universale); la lente è di nuovo in fondo
  a destra nella barra e "respira": 2,4 s per verso, da 0,8 a 1,3 volte, colore d'accento pieno al massimo (`BreathingSearchIcon`).
- Web app (APK 187): i browser installano le PWA come scorciatoie fissate; Eurise non riceveva la richiesta e sparivano. Ora
  `apps/PinShortcutActivity` accetta `CONFIRM_PIN_SHORTCUT`, `apps/WebApps` le elenca (FLAG_MATCH_PINNED, solo da launcher
  predefinito), le apre, ne mostra l'icona e le toglie ("Togli la web app" nel menu). Categoria dal nome, altrimenti Browser.
  Quelle installate prima vanno reinstallate dal browser: la richiesta allora non è stata accettata da nessuno.
- Anteprime da approvare: pagina Scrivania, fila "Ricerca universale con una mano" (RicBasso, RicAppuntamento, RicDocumenti);
  pagina Password, fila "I miei dati" (PwdDati, PwdDatiCompila). Password oggi compila solo accessi e carte: i dati personali e
  professionali (P.IVA, CF, PEC, codice univoco, IBAN, sede, documenti) sono da scrivere, dentro la cassaforte .kdbx, con
  impronta o PIN per vederli, copiarli o compilarli. Più profili (Personale, Studio).
- Ricerca universale con una mano (APK 189, tavole RicBasso/RicAppuntamento/RicDocumenti approvate con due modifiche): barra in basso,
  contatti (punteggio dalle interazioni, `ContactsRepository.ranked`) e app in file laterali indipendenti, due voci intere e la terza
  accennata, primo risultato a destra ("Ricerca per mancini" in Aspetto lo porta a sinistra); sopra, Calendario, Note, Attività e
  conversazioni. Fila Nota/Da fare/Evento/Documenti/Web sopra la tastiera: fogli rapidi in `core/links/QuickSheets.kt` (evento con
  mese e orari liberi, `core/data/calendar/FreeSlots` con test), `files/DocumentsSheet.kt` (telefono + Murena/Nextcloud con
  `WebDav.search`), Web con il browser predefinito. Invio apre il primo contatto o la prima app. Tolti il + della barra e i bersagli
  browser/AI della vecchia ricerca. Le cartelle di kDrive/Proton aggiunte in File non sono cercate dal foglio Documenti.
- Password, I miei dati (APK 189, tavole PwdDati con "Altri dati" e PwdDatiCompila): profili come voci KeePass con il segno
  `Eurise-Profilo` e un campo per dato (persona, attività, indirizzo, più i campi aggiunti con titolo e valore), `logic/Profiles.kt`;
  `data/SensitiveGate.kt` chiede impronta o PIN per vedere, copiare o compilare dati riservati e carte, valido 2 minuti, chiuso dal
  blocco; compilazione dei moduli di dati in `autofill` (`Matching.byProfileWords`, test in KdbxTest). Android 8 senza richiesta PIN.
- Tastiera: tasto microfono sulla fila in basso accanto alla virgola (solo lettere), apre la dettatura di Eurise Voce.

## Calcolatrice, lettore QR, lettura dei documenti (10 novembre, sera) – anteprime da approvare
Richiesta del proprietario prima di allontanarsi. Tela, pagina nuova "Calcolatrice, QR e documenti" e pagina Integrazioni (TastCalcolatrice).
- Tastiera: quinto pulsante "Calcola" nella barra di Eurise (Disponibilità, File, Persona, Codici, Calcola), con icona sopra l'etichetta
  per farli stare tutti; il pannello sostituisce i tasti: tastierino con %, IVA, €↔, "Scrivi 1.525" (tenuto premuto scrive anche il calcolo).
- Calcolatrice (CalcPrincipale, CalcPro, CalcGrafico): nastro dei calcoli con etichette e "Salva in Note", risultato mentre si scrive,
  funzioni scientifiche; Professionale: fattura con cassa, IVA, ritenuta, forfettario con bollo, anche dal netto, più scorporo IVA,
  sconto e ricarico, rata, giorni tra date; Unità; Grafico con funzioni, zeri, minimo e intersezioni. Da decidere: sezione dell'APK
  principale (come Orologio) o app a parte; proposta: sezione dell'APK principale, `feature/calculator`.
- Motore già scritto e provato (core/data/calc): `Calc` (numeri all'italiana, percentuali da ufficio, funzioni, gradi/radianti,
  formato) e `Invoice` (prospetto e calcolo inverso dal netto), test in `CalcTest`. Il grafico richiederà la variabile x.
- Lettore QR (QrLettore, QrRisultato): oggi è una modalità dello Scanner con una finestra semplice. Proposta: lettore a sé con torcia,
  "Da una foto", "Crea un QR", "Letti prima"; risultati capiti per tipo: pagoPA (importo, codice avviso, ente; la scadenza non è nel
  codice, la sceglie la persona → Da fare), link con dominio in evidenza, Wi-Fi, contatti, eventi, IBAN, 2FA verso Password.
- Lettura dei documenti (DocLeggi, DocMotore), letta come "lettura del testo" (OCR) delle scansioni e dei PDF: nome proposto, cartella,
  scadenze e importi → Da fare, PDF cercabile, lettura ad alta voce. DECISIONE DEL PROPRIETARIO prima del codice: il motore (pesante,
  nativo) va in un'app a parte "Eurise Leggi" scaricata a richiesta, come previsto; candidati liberi da provare con un prototipo che
  misuri peso e qualità in italiano: Tesseract (Apache 2.0, tramite Tesseract4Android) o un modello leggero via ONNX Runtime (MIT).
  Interruttore con spiegazione (analisi dei contenuti), tutto sul telefono.

## 10 novembre, notte: approvazioni e lavoro fatto (ramo `tastiera`)
Il proprietario ha approvato: riunioni, viaggi, modalità, cestino unico (tavole Int*), calcolatrice nella Tastiera e calcolatrice,
lettura dei documenti; "mi fido del resto" (lettore QR compreso). Proverà direttamente l'APK e poi si lima.
- Fatto e compilato:
  - Tastiera: comando "Calcola" (quinto nella barra, `CalcPanel` in `keyboard/.../Panels.kt`), percentuali da ufficio anche nei
    calcoli scritti nel testo (`keyboard/.../Calc.kt`), tasto microfono sulla fila in basso.
  - Calcolatrice: `feature/calculator` (APK principale, icona "Calcolatrice" tra gli Strumenti, rotta `calculator`): Calcolo con
    nastro (etichette, Salva in Note, Copia), funzioni e gradi/radianti; Professionale (fattura dal compenso o dal netto,
    forfettario con bollo, scorporo, sconto e ricarico, rata, giorni lavorativi); Unità; Grafico (fino a 3 funzioni, zoom con le
    dita, zeri ed estremi). Motore `core/data/calc` (Calc, Invoice, Finance) con test.
  - Lettore QR: icona "Lettore QR" (rotta `qr`, Scanner che parte in QR); `QrContent` con test; fogli per tipo in `QrScreens.kt`;
    Da una foto, Crea un QR (testo o Wi-Fi, condivisione PNG), Letti prima (30, mai i 2FA); dal vivo anche Data Matrix, EAN, Code 128.
- Da fare, nell'ordine (tutto già approvato, si scrive direttamente):
  1. Riunioni (IntRiunione): "Apri la nota della riunione" dall'evento e dall'avviso d'inizio; presenti, ordine del giorno,
     appunti; alla chiusura le righe con @persona e data diventano attività collegate all'evento (lettore `Capture` già esistente).
  2. Modalità (IntModalita): Lavoro, Casa, Viaggio, Notte nel Pannello; ognuna cambia categoria iniziale della Scrivania, notifiche
     lasciate passare, calendari ed elenchi visibili, suoneria; accensione per orario (salta festivi e ferie) o durante gli eventi
     di un calendario. Niente luogo.
  3. Cestino unico (IntCestino): Note, Attività, File (cestino 30 giorni del telefono), Foto (cestino di sistema, Android 11+),
     eventi; ripristino con i collegamenti; "Passa a un telefono nuovo" porta al backup cifrato. Password e Galleria: permesso di firma.
  4. Viaggi (IntViaggio): da un biglietto PDF in File o condiviso verso Eurise; voli nel Calendario, sveglia e fuso nell'Orologio,
     cartella File › Viaggi, diario nella Galleria, mappa offline spenta di base, modalità Viaggio nelle date.
  5. Lettura dei documenti (DocLeggi, DocMotore): app a parte "Eurise Leggi" scaricata a richiesta. Primo passo: prototipo che
     compili il motore su GitHub Actions e ne misuri peso e qualità in italiano (Tesseract via Tesseract4Android, Apache 2.0);
     poi nome proposto, cartella, scadenze e importi → Da fare, PDF cercabile. Interruttore con spiegazione, tutto sul telefono.

## 11 novembre, notte: riunioni, modalità, cestino unico, viaggi, prototipo Leggi (ramo `tastiera`)
Il proprietario prova sul telefono quanto in attesa (ricerca universale, I miei dati, web app, calcolatrice, lettore QR, Tastiera,
Voce, Galleria, cartelle cifrate, centro privacy, Attività su Murena); le correzioni arrivano dalla sua prova. Intanto, nell'ordine:
- Riunioni (APK 194–195): `feature/notes/meeting` (MeetingScreen, MeetingViewModel, MeetingDoc con test). "Nota della riunione"
  nell'evento (non per quelli di tutto il giorno) e avviso un minuto prima dell'inizio per gli eventi con altre persone invitate
  (`Reminders`, canale "Inizio delle riunioni"). Presenti dagli invitati (`CalendarRepository.attendees`, `isMeeting`), ordine del
  giorno anche dagli elenchi della descrizione, appunti. "Chiudi la riunione": le righe con @persona e data (`core/data/meetings/
  MeetingActions`, test) diventano attività collegate a nota ed evento; si può richiamare per le azioni nuove. Riassunto solo a
  richiesta (condividi). La nota è normale (si apre anche in Note), nasce alla prima modifica; `MeetingStore` lega occorrenza e nota.
- Modalità (APK 195): `core/data/modes` (ModeRules puro con test, Modes con salvataggio e sveglia `ModeReceiver`). Nel Pannello una
  fila Lavoro/Casa/Viaggio/Notte: tocco = a mano fino al prossimo cambio (ritocco = automatico), pressione lunga o "Imposta" =
  `ModesSheet`. Ognuna: categoria di partenza della Scrivania (anche il tasto Home ci porta), notifiche (Non disturbare, con
  spiegazione prima di chiedere l'accesso; nel centro privacy compare tra i permessi), suoneria, calendari ed elenchi visibili
  (filtrati in Calendario e Attività). Accensione per orario (festivi italiani e giorni con evento "Ferie"/"Vacanze" saltabili) o
  durante gli eventi di un calendario; mai luogo. Uscendo tornano suoneria e filtro di prima. Di base tutto spento.
- Cestino unico (APK 196): database 7 (tabella `trash`, migrazione 6→7), `core/data/trash/TrashRepository`. Attività (con passi) ed
  eventi tolti vengono copiati con i collegamenti e tornano (id nuovi) ricollegati; note col loro cestino; file col cestino di File;
  foto dal cestino di Android 11+ (ripristino con conferma di sistema; servono i permessi foto, altrimenti avviso). Si apre da
  File › Cestino e dal cestino di Note (rotta `trash`). Filtri, Svuota con conferma, "Passa a un telefono nuovo" → backup cifrato.
  Mancano: Password e Galleria (serve il permesso di firma), eventi ricorrenti (non si cancellano da Eurise).
- Viaggi (APK 197): `core/data/trips` (PdfText: testo dei PDF senza librerie, con test; Bcbp: carte d'imbarco IATA; Tickets: voli e
  treni dal testo; Airports: ~150 aeroporti con fuso). `feature/calendar/trips` (TripScreen, TripViewModel: codici PDF417/Aztec letti
  dalle pagine con PdfRenderer + ZXing). Si apre condividendo un PDF verso Eurise, da File (azione "Viaggio" sui PDF) o dal Lettore QR
  (carta d'imbarco; il lettore ora legge anche PDF417 e Aztec). Ognuna togliibile: Calendario (orari del luogo convertiti nel fuso
  del telefono), sveglia in una data (Alarm.date, `ClockContract.trip`), fuso della destinazione nell'Orologio, PDF in Documenti ›
  Viaggi, modalità Viaggio nelle date (`Modes.addTrip`). Diario nella Galleria e mappa offline mostrati spenti (arrivano con i loro
  APK). Limiti: le scansioni (immagini) non si leggono finché non c'è Eurise Leggi; treni riconosciuti con regole semplici.
- Lettura dei documenti, primo passo (prototipo): modulo `read/` (Eurise Leggi, prova) con Tesseract4Android 4.7.0 (JitPack, solo per
  quel gruppo e solo con EURISE_READ=1, così non tocca le altre compilazioni). Lavoro "Prototipo Eurise Leggi": APK
  `eurise-leggi-debug-N`, peso nel riepilogo, e misura della qualità in italiano (`.github/scripts/ocr_quality.py`: documenti di
  prova rovinati come scansioni e foto, modelli fast e best, caratteri e parole giusti, importi/scadenze/codici ritrovati).
  Risultati (APK 198, Tesseract 5.5): APK ridotto 7,6 MB (motore nativo arm64), italiano fast 2,6 MB a richiesta; sulle prove
  99,5–99,9 % di caratteri giusti, importi/scadenze/codici 11 su 11 anche da foto difficile.
  DECISO (10 novembre): il motore è Tesseract; ONNX non si prova (porterebbe solo peso, non qualità). Prossimi passi: compilare
  Tesseract e Leptonica dai sorgenti dentro il progetto (third_party, come whisper.cpp) al posto dell'AAR di JitPack, per F-Droid;
  poi nome proposto, cartella, scadenze e importi → Da fare, PDF cercabile, lettura dei biglietti scansionati per i Viaggi.

## Aspetto Eurise per Linux (10 novembre, deciso dal proprietario) – ultimo passo della chat del brand
Dopo l'approvazione del brand, prima delle app desktop. Per GNOME (Ubuntu, Fedora) e KDE (Kubuntu e altri).
- GNOME: estensione "Aspetto Eurise" (extensions.gnome.org, un clic) che accesa applica e spenta ripristina: tema di icone Eurise
  (anche per Firefox, LibreOffice, Thunderbird…), sfondi che cambiano con l'ora (cieli della Scrivania, Perla e Notte), accento,
  chiaro/scuro, Plus Jakarta Sans, dock di Ubuntu flottante e satinato, tema del pannello. Da verificare che la revisione accetti
  un'estensione che porta il tema di icone; in alternativa pacchetti .deb e .rpm.
- KDE: tema globale (icone, colori, stile del pannello e delle finestre, sfondi) installabile dalle impostazioni con un clic.
- Limite: le app libadwaita di GNOME prendono accento, caratteri e chiaro/scuro ma non forme proprie; niente CSS forzato
  (si rompe a ogni aggiornamento). Schermata di accesso fuori.
- Metodo: anteprime sulla tela, pagina "Linux" (scrivania Ubuntu con pannello, dock, File e sfondo; stessa cosa per KDE).

## 11 novembre: prova del proprietario sul telefono (APK 198) e correzioni (ramo `tastiera`)
Giudizio: Voce riconosce molto bene (anche la punteggiatura) ma è lenta col pacchetto più pesante; Lettore QR non funzionava;
scanner pessimo nel trovare e ritagliare le pagine. Deciso: Tesseract per Eurise Leggi (vedi sopra).
Fatto (APK 203, compilazione verde con i test):
- Tastiera: lo spazio dichiarato alle app ora conta la barra di Eurise (prima le app restavano coperte, anche fuori dalla ricerca);
  fascia di sistema con freccia e mondo tolta dove Android lo permette (`euriseHideImeNavBar`, dal 15; da verificare sul Murena);
  barra: Calcola, Codici, File, Persona, Disponibilità, Browser con icona sopra l'etichetta; Browser cerca subito il testo scritto o
  selezionato, pressione lunga = browser installati o assistenti sul web (`WebSearch.kt`, `BrowserPanel`); nome "Eurise Tastiera"
  anche nelle versioni di prova, "based on HeliBoard" in Informazioni. Manca: icona propria della Tastiera (è ancora quella di HeliBoard).
- Tema chiaro: `glass(solid = true)` per i pulsanti (bianco quasi pieno, bordo, ombra) nella ricerca; velo di fondo grigio caldo.
- Bolle: appuntamenti con Sposta (giorno e ora) ed Elimina (cestino unico), pulsantini a 26 dp come in "Ti hanno cercato", nota della
  riunione → schermata riunioni; bolla Calendario di nuovo col mese (`PlacedCard` alta 30); riga sotto alba e tramonto con sveglia,
  conto alla rovescia o cronometro (`core/suite/ClockStatus`, scritto da `ClockStore`) o "No sveglia", menù Sveglia/Cronometro/Conto
  alla rovescia (sezioni `clock:0/1/2`); dopo la risposta da Eurise la conversazione si segna letta e l'eco non torna per 30 s.
- Scanner: foglio dai bordi (`HoughQuad`, test), analisi più fine; codici con fotogrammi 1280×960, modalità accurata, centro + intero
  + invertito. DA RIPROVARE sul telefono: se non basta ancora, prossimo passo raffinare gli angoli sul fotogramma dello scatto.
Da fare, in quest'ordine (anteprime sulla tela prima del codice dove indicato):
1. Galleria: lettore video integrato (Media3 ExoPlayer, motivo da scrivere nel commit) — editor foto più evoluto (anteprima: ritaglio
   con proporzioni, raddrizza, luce/contrasto/saturazione/calore, filtri, testo e disegno) — in ogni scelta di colore anche "il tuo"
   (selettore con ruota/HEX, componente comune in `core/designsystem`, usato in tutta la suite).
2. Coerenza delle icone: un solo stile (quello di EuriseIcons) per condividi, altro, indietro ecc. in tutte le app e negli APK separati
   (Galleria, Password, Tastiera, Voce); censire le icone fuori stile.
3. Password: leggere le carte (fotocamera con Tesseract di Eurise Leggi per numero, scadenza, intestatario; NFC per numero e scadenza
   delle carte contactless) e riempire i campi prima di salvare. Il resto di Password quando il proprietario inizia a usarla.
4. Voce più veloce: misurare (thread, modello quantizzato q5/q8 del pacchetto pesante, dettatura a pezzi mentre si parla).
5. Widget di altre app dentro le bolle (AppWidgetHost; anteprima prima: aggiunta dal menù delle bolle, aspetto integrato).

## 11 novembre, sera: Tastiera AI, Galleria, icone, carte, Voce, QR e scanner, widget (ramo `tastiera`, APK 204–210)
Tutto scritto senza aspettare conferme (richiesta del proprietario); anteprime sulla tela prima del codice dove serviva.
- Tastiera (APK 204/207): tasto **AI** nella barra, di base senza collegamento; si collega Le Chat, Claude, ChatGPT,
  Perplexity o Duck.ai (segnata «app» se installata), pressione lunga = cambia o togli. **Browser e AI si aprono in un
  riquadro** sopra l'app in cui si scrive (`app/.../web/WebPopupActivity`, permesso di firma, WebView di sistema): la
  Tastiera resta senza Internet. Nel riquadro «Apri nel browser» / «Apri nell'app» porta fuori la ricerca. Browser:
  motore del riquadro (DuckDuckGo di base, Startpage, Qwant, Ecosia, Google) e browser per uscire. Senza l'app Eurise si
  apre direttamente il browser o l'app. Manca: indirizzo libero per un assistente non in elenco.
- Galleria (anteprime sulla tela, pagina Galleria, seconda fila: GalVideo, GalRitaglio, GalRegola, GalTesto, ColoreTuo):
  - Lettore video integrato (`ui/VideoPlayer.kt`, Media3 ExoPlayer 1.5.1 solo motore): doppio tocco ±10 s, luminosità e
    volume scorrendo, due dita per riempire, barra con miniature, velocità, audio, ripeti, fotogramma come foto, Taglia
    (copia senza ricodifica, `data/VideoTools.kt`), finestra sopra le altre app (anche uscendo con Home). Anche i video
    della cartella protetta (senza fotogrammi né altre app).
  - Editor: fondo scuro, ghiera Raddrizza ±30°, griglia dei terzi, proporzioni; Ombre/Alte luci (curva `toneLut`),
    Vignetta, Automatico (istogramma); filtri con miniature e intensità (`Look`); scritte in 4 stili, modificabili;
    pennarello, evidenziatore, gomma; Ripeti; occhio tenuto premuto = originale.
  - «Il tuo colore»: `core/designsystem` `ColorRow`, `ColorPickerSheet`, `OwnColorSwatch`, `ColorMath` (test). Le tinte
    salvate per nome accettano un HEX (`tintTones`/`tintTone`): attivo in Note (testo, evidenziatore, disegno, taccuini) ed
    elenchi di Attività. Mancano: calendari (prendono il colore dal provider), colori della Scrivania.
- Coerenza delle icone (censimento): Galleria, Password, Voce e moduli dell'APK principale usavano già solo EuriseIcons.
  Fuori stile trovati e sistemati: tasti e barra della Tastiera (HeliBoard; ora `eurise_key_*` con lo stile Material di
  base, `KeyboardIconsSet.euriseIcons`), notifiche del Calendario (icone di Android → `ic_stat_bell`, `ic_stat_note`), «✕»
  testuale nella Calcolatrice. Restano fuori stile: le schermate di impostazione della Tastiera (HeliBoard) e la sua icona.
- Password: «Avvicina la carta» (NFC, `data/CardNfc.kt`, logica EMV `logic/Emv.kt` con test: numero, scadenza, a volte
  intestatario; niente pagamenti) e «Fotocamera» (apre Eurise Leggi, `read/.../ScanCardActivity.kt`: CameraX + Tesseract,
  sagoma della carta, numero valido due volte; `core/suite` `CardContract` e `CardText` con test). Per la fotocamera va
  installata Eurise Leggi (artefatto `eurise-leggi-debug-N`). CVV sempre a mano.
- Voce (APK 209): modello caricato mentre si parla; dettatura a pezzi nelle pause (`Chunker`, test) con testo che compare
  mentre si parla; contesto audio ridotto alla durata del pezzo; flash attention; pezzo precedente come contesto; thread
  4 di base sugli 8 core, «Misura la velocità» (dall'icona di Voce) prova 2/4/6 e tiene il migliore. DA PROVARE: tempi
  e qualità col pacchetto pesante; se la qualità cala, togliere il contesto ridotto (`Whisper.transcribe(fullContext)`).
  Non fatto: modello q8 (più grande, forse più veloce: da misurare se serve ancora).
- Lettore QR: zoom 1,8× e fuoco al centro ogni 3 s; codici a barre dritti (fotogramma girato). Scanner: bordi ricercati
  sulla foto scattata. DA RIPROVARE sul telefono.
- Widget delle app nelle bolle (anteprime WidAggiungi, WidBolla sulla pagina Bolle): catalogo con scheda «Widget delle
  app» (per app, anteprima e misura), permesso di Android, schermata di impostazione del widget; bolla con titolo dell'app
  e «Apri»; si toglie come le altre (libera il numero). `home/AppWidgets.kt`; `HomeSlot.widget` e `HomeSlot.key` (più
  widget nella stessa pagina). Manca: il widget non segue l'altezza scelta per la bolla (usa la sua altezza minima).
Prossimo: prova del proprietario di tutto questo; poi Eurise Leggi oltre il prototipo (Tesseract dai sorgenti in
third_party), Password quando il proprietario comincia a usarla.

## 11 novembre: tutto sul ramo `brand` (chat del brand)
Il proprietario ha chiesto di unire tutti i rami qui: `tastiera` è unito in `brand` (nessun conflitto), gli altri rami erano già
contenuti. **Si lavora su `brand`.** Brand: vedi `docs/BRAND.md`.
Fatto dopo l'unione (da provare sul telefono):
- Tastiera: la barra di Eurise sta sopra e i suggerimenti subito sopra i tasti; impostazione «Barra di Eurise sopra i suggerimenti»
  (Barra degli strumenti) per l'ordine di prima; i pannelli si aprono sempre al posto dei tasti (`EuriseBar.arrange`).
- Tastiera: altezza al 112% in verticale di base (tasti presi per sbaglio). Chi l'aveva già cambiata a mano tiene la sua.
- Tastiera: chiaro/scuro come le bolle della Scrivania (`HomeTheme`, broadcast `eu.eurise.action.HOME_THEME` con permesso di firma,
  mandato da `tellKeyboardTheme` quando cambia `scene.themeDark`); impostazione «Chiaro e scuro come la Scrivania» in Aspetto,
  vale con il giorno/notte automatico acceso. Icona propria della Tastiera: arriva con l'unione (stella polare).
- Tema Notte: `EuriseTheme` dà ai testi il colore del tema (prima titoli e righe senza colore restavano neri sul fondo scuro).
- Password, NFC: controllo di presenza diradato, risposte 61xx/6Cxx seguite, applicazioni note provate (PagoBANCOMAT per prima);
  se non legge, il foglio dice il passo («Lettura fermata a: …», es. «avvio 6985»). Se non basta, chiedere al proprietario quel testo.
- Notifiche della Galleria con la stella (`ic_stat_eurise`).

## 11 novembre, notte: Eurise Leggi completa (ramo `brand`, APK 223–224) – da provare sul telefono
Ordine deciso dal proprietario per la road map: 1 Leggi, 4 traduzione e scrittura a mano, 6 Mappe/Messaggi/Telefono, 5 Office,
3 Browser, poi il resto. Per i punti nuovi valgono anteprime o prototipo prima del codice.
- Motore (APK 223): Tesseract 5.5.1 e Leptonica 1.85.0 dai sorgenti in `third_party/` (copie ridotte, vedi i loro EURISE.md), CMake
  proprio in `read/src/main/cpp` (senza motore legacy, grafica di debug e librerie d'immagine), ponte JNI `eurise_ocr`, classe
  `eu.eurise.read.ocr.Ocr`. Tolti Tesseract4Android e JitPack. Versione ridotta 3,6 MB (prima 7,6). La compilazione misura la
  qualità con lo stesso motore compilato per il computer (`host_test.cpp`, EURISE_OCR in `ocr_quality.py`): fast 99,5–99,9 %.
- Collegamento (APK 224): `core/suite/ReadContract` (servizio `ReadService` di Leggi, permesso della suite, Messenger);
  `core/data/docs/ReadClient` + `ReadPagesProvider` (pagine passate con permesso temporaneo per file, cancellate subito; `<queries>`
  per i pacchetti di Leggi). Leggi risponde con testo, parole e riquadri (TSV) e misure dell'immagine. Inglese a scelta in Leggi.
- Logica pura con test (`core/data/docs`, DocsTest): `DocReader` (tipo e cartella, chi lo manda, mese, nome «2026-11 Bolletta
  luce Enel», scadenza vicino a «entro/scadenza», importo vicino a «totale da pagare», IBAN con controllo, codice avviso pagoPA),
  `OcrWords`, `SearchablePdf` (JPEG + testo invisibile in Helvetica WinAnsi, riletto da PdfText nei test; provato anche con
  qpdf e pdftotext).
- Scanner (tavole DocLeggi e DocMotore): scheda Testo nella revisione; spiegazione prima di accendere (`DocReading`, spenta di
  base, «Leggi ogni scansione da sola»); nome proposto se non toccato; Da fare con scadenza, importo, IBAN e codice; copia; lettura
  ad alta voce con la sintesi del telefono; PDF cercabile in Documenti/<cartella proposta> (es. Casa/Bollette) o Eurise/<cartella>
  sul cloud. Differenza dalla tavola: le lingue si scelgono in Leggi, non nel foglio.
- File: «Aggiungi il testo (PDF cercabile)» sui PDF di sole immagini (copia «… (con testo).pdf» accanto, `Searchable.kt`).
- Viaggi: biglietti PDF scansionati letti da Leggi se la lettura è accesa. Centro privacy: voce «Lettura dei documenti», si spegne lì.
- Da provare: scansione di una bolletta (proposte e PDF cercabile), «Aggiungi il testo» su un PDF scansionato, biglietto
  scansionato in Viaggi. Leggi va installata (`eurise-leggi-debug-N`) e va scaricato l'italiano dall'app Leggi.
- Restano: il pulsante «Installa» apre lo store (market://), che non ha ancora Leggi; ricerca di File nel testo dei PDF.

## 11 novembre, notte: punto 4, prototipo della traduzione (ramo `brand`, APK 226) e scrittura a mano
- Motore: slimt (MIT, copia di Offline Translator, F-Droid) con ruy e SentencePiece (Apache 2.0), dai sorgenti in `third_party/`
  (circa 4,5 MB di sorgenti, vedi i loro EURISE.md). Modulo `translate/` (EURISE_TRANSLATE=1, lavoro «Prototipo Eurise Traduci»),
  `engine.hpp` comune a JNI e prova sul computer; modelli di Firefox (MPL 2.0) dall'elenco pubblico di Mozilla su Google Cloud
  Storage (`Models.kt`, scelta: rilascio migliore, base-memory), decompressi nei file dell'app.
- Misure (CI): APK ridotto **2,2 MB**; modelli 25 MB per direzione; chrF su 12 frasi di prova it→en 82,8, en→it 78,5 (buono;
  errori tipici: «quote» → «citazione», «imprevisto» → «unforeseen one»). Velocità sul computer di compilazione non indicativa
  (ruy senza AVX): va misurata sul telefono con l'app di prova `eurise-traduci-debug-N`. ruy è senza cpuinfo (niente dotprod).
- Da decidere con il proprietario: dove sta il motore (la Tastiera non ha Internet). Proposta: dentro Eurise Leggi (ha già
  Internet solo per i modelli, servizio con permesso di firma, OCR per tradurre foto e documenti), +2 MB; in alternativa APK a
  parte «Eurise Traduci». Poi: pannello Traduci nella Tastiera, centro privacy (Google Cloud Storage tra i servizi), più lingue.
- Scrittura a mano: nessun modello libero pronto per l'alfabeto latino. OnlineHTR (MIT) è solo codice; i pesi disponibili sono
  addestrati su IAM-OnDB (solo ricerca, non commerciale) e ML Kit è escluso. Serve addestrare un modello nostro su dati liberi
  (anche sintetici): lavoro lungo, che richiede GPU fuori da GitHub Actions. Proposta: rimandare, da decidere col proprietario.

## 11 novembre, notte: punto 6, Messaggi, Telefono e Mappe – anteprime da approvare (tela, pagina «Mappe, Messaggi e Telefono»)
- Tavole: MsgElenco (Persone / Codici e consegne / Avvisi, codice in alto da copiare che si cancella), MsgConversazione (data capita
  sul telefono → Crea l'evento, risposta pronta, Da fare; barra con Disponibilità, posizione, invio programmato), TelRecenti (da
  richiamare con la modalità in cui sono arrivate, disturbatori riconosciuti sul telefono, preferiti), TelChiamata (impegni e Da
  fare con la persona, nota della chiamata in Note con @persona), MapPercorso (percorso offline legato all'impegno, «parti entro»,
  manda l'arrivo), MapCerca (dove devi andare da Calendario, Viaggi e Rubrica; mappe sul telefono proposte dai viaggi).
- Basi proposte (da approvare): Messaggi = fork di Fossify Messages (GPL-3.0, SMS/MMS, senza Internet); Telefono = fork di Fossify
  Phone (GPL-3.0); Mappe = fork di CoMaps (Apache 2.0, comunitario, F-Droid; NON Organic Maps: dall'agosto 2025 i suoi dati delle
  mappe hanno una licenza non libera). Come per HeliBoard: interfaccia rifatta nello stile della suite, dialogo con l'APK principale
  solo con permesso di firma. Eurise è GPLv3: compatibile con tutte e tre.
- Prossimo, nell'ordine del proprietario: approvazione del punto 6; punto 5 Office (prototipo che compila il motore LibreOffice/
  Collabora su GitHub Actions e misura spazio, tempo e peso: lavoro lungo, va in un lavoro a parte); punto 3 Browser.
- Decisioni aperte: motore della traduzione (in Leggi o APK a parte), scrittura a mano (rimandare?), basi del punto 6.

## 11 novembre: punto 5, prototipo del motore di Office (ramo `brand`, lavoro «Prototipo del motore di Office»)
- Lavoro a parte `.github/workflows/office-engine.yml` (non tocca gli APK): parte a mano o quando cambiano i suoi file; un giro
  alla volta, mai annullato. Sorgenti: `LibreOffice/core` su GitHub, ramo `distro/collabora/co-25.04`, commit fissato
  (`CORE_COMMIT`), scaricato senza storia. NDK 27–29 (il ramo rifiuta i più vecchi), JDK 17, dipendenze con `apt build-dep libreoffice`.
- Configurazione `office/engine/autogen.input.in`: `CPAndroidAarch64` (quella dell'app Collabora Office), solo arm64, solo
  inglese e senza dizionari (vedi `office/engine/EURISE.md`). Risultato atteso: `android/jniLibs/arm64-v8a/liblo-native-code.so`.
- Tempo: GitHub dà al massimo 6 ore per lavoro. make ha il tempo che resta meno 30 minuti; poi si salvano ccache (6 GB al massimo)
  e archivi esterni nella cache di Actions e il giro dopo riparte da lì. Se il riepilogo dice «tempo scaduto», rilanciare.
- Misure (`.github/scripts/office_size.py`, nel riepilogo del lavoro e in `misure.json`): tempo per fase, spazio su disco
  (sorgenti, workdir, instdir, archivi, ccache, libero), libreria com'è e compressa, assets scelti come in
  `android/lib/build.gradle` di Collabora (tipi UNO, testi, configurazione, filtri, interfaccia, lingue, caratteri a parte),
  stima dell'APK con e senza caratteri. Artefatto `eurise-office-motore-N`: libreria, assets, misure, registro di make.
- Fuori dal prototipo: interfaccia web di Collabora (`browser` di online, per «Tutte le opzioni»), parte `online` per Android,
  italiano. Dopo la misura: decidere con il proprietario (se non ci sta: computer di compilazione o parti già compilate), poi
  anteprime telefono e tablet.

## 11 novembre: decisioni del proprietario (traduzione, scrittura a mano, punto 6)
- Traduzione: **il motore va dentro Eurise Leggi** (+2 MB circa). Leggi scarica i modelli e risponde alla Tastiera e alle altre
  app con il suo servizio a permesso di firma. Da fare: spostare `translate/` in Leggi, pannello Traduci nella Tastiera, voce nel
  centro privacy (Google Cloud Storage di Mozilla per i modelli), più lingue.
- Scrittura a mano: **rimandata** (nessun modello libero per l'alfabeto latino; addestrarlo chiede GPU).
- Punto 6, anteprime e basi **approvate** (Messaggi = Fossify Messages, Telefono = Fossify Phone, Mappe = CoMaps), con queste aggiunte:
  - Messaggi: azioni proposte dal contenuto, anche nelle email. Appuntamento scritto e confermato dall'altro → inserimento diretto
    nel Calendario. Numero di telefono → Aggiungi in rubrica, Chiama, SMS, WhatsApp, Telegram, chat nostra (quando ci sarà), email.
    Altra lingua → Traduci. Cosa da fare → Da fare. Appunto → Note. E così via secondo il contenuto.
  - Telefono: va bene così.
  - Mappe: arricchirla il più possibile per competere con Waze e Google Maps: velocità attuale, lettura dei segnali stradali
    (limiti), mappa molto chiara, integrazione con la suite, notizie sul traffico se si trovano fonti, meteo sul percorso.
  - **App nuova Meteo** nello stile della suite: previsioni, allerte, vento, temperature, pollini, alba e tramonto, brevi articoli
    di aggiornamento. Nella bolla degli appuntamenti (che diventerà «I tuoi impegni») una frase breve: «Oggi il tempo è buono e la
    strada scorrevole, dovresti arrivare in tempo», oppure «Allerta meteo arancione: presta attenzione e anticipa la partenza».
  - Da fare prima del codice: anteprime per Mappe arricchita, Meteo e la frase nella bolla; ricerca delle fonti libere (traffico,
    allerte, pollini) da mostrare nel centro privacy.

## 11–12 novembre, notte: Mappe e Meteo approvate, traduzione in Leggi, Telefono, Rubrica e Messaggi (ramo `brand`, APK 237)
**Anteprime approvate dal proprietario** (tela, pagina «Mappe, Messaggi e Telefono», seconda fila): MapGuida (velocità attuale,
cartello letto, meteo e traffico sul percorso, frase d'arrivo), MapEsplora (nomi delle vie, «Monza (MB)» con bandiera e «IT»,
orizzonte che segue meteo e ora, si spegne in Livelli), MapAttivita (benzinai col prezzo, negozi, punti di interesse,
Professionisti «presto con QuiAccanto»), MapLivelli (cosa resta sul telefono e cosa esce, posizione arrotondata), MeteoOggi,
MeteoAllerta (impegni toccati dall'allerta), BolImpegni (la frase su tempo e strada nella bolla «I tuoi impegni»).
Fonti scelte (da mettere nel centro privacy quando si scrive il codice):
- Carburanti: open data MIMIT (Osservaprezzi), CSV quotidiani «anagrafica impianti» e «prezzi alle 8», licenza IODL 2.0;
  file uguale per tutti, nessuna posizione inviata. Dal 10 febbraio 2026 è cambiato il separatore dei campi.
- Meteo: Open-Meteo (CC BY 4.0, attribuzione con link), in Italia il modello ICON-2I di ItaliaMeteo-ARPAE (2 km, 3 giorni);
  pollini dall'Air Quality API di Open-Meteo (CAMS, solo Europa, 4–5 giorni, attribuzione a CAMS).
- Allerte: MeteoAlarm (CAP) e i bollettini della Protezione Civile / Centri funzionali regionali (fanno fede questi).
- Attività e punti di interesse: OpenStreetMap (dentro le mappe di CoMaps), poi QuiAccanto. Pagine Gialle e Registro
  Imprese no: niente API libere (InfoCamere è a pagamento, Pagine Gialle non permette il riuso).
- Traffico in tempo reale: non c'è una fonte libera nazionale (TomTom/HERE a pagamento; open data solo locali, es. Bolzano,
  Torino 5T). Per ora: limiti e autovelox da OSM (CoMaps), «traffico» resta spento «in prova».
- Lettura dei cartelli con la fotocamera: i dataset grandi (Mapillary, DFG, GTSRB) sono non commerciali; ci sono dataset
  piccoli CC BY 4.0 (Roboflow). Serve addestrare un modello nostro: come per la scrittura a mano, va fuori da GitHub Actions.

**Traduzione dentro Eurise Leggi** (decisione del proprietario): motore in `read/src/main/cpp/translate/` (libreria
`eurise_translate` separata da `eurise_ocr`), `TranslateContract` in core/suite, `TranslateService` (permesso della suite),
schermata «Lingue della traduzione» in Leggi (14 lingue, ognuna con l'inglese; tra due lingue si passa dall'inglese), voce
nel centro privacy. Tolto il modulo `translate/`. Leggi ridotta: 4,9 MB; qualità invariata (chrF it→en 82,8, en→it 78,5).
**Tastiera**: pannello Traduci (ottavo comando): selezione o paragrafo prima del cursore → Sostituisci / Aggiungi sotto.

**Eurise Telefono** (`phone/`, artefatto `eurise-telefono-debug-N`, applicationId `eu.eurise.phone`): app per le chiamate
(ruolo Telefono; la propone al primo avvio). Recenti con «Da richiamare» (perse negli ultimi 3 giorni senza chiamate dopo),
preferiti (stella della rubrica), tastierino con ricerca anche per nome (T9), numeri bloccati di sistema, togli dai recenti.
Schermata della chiamata (TelChiamata): impegni con la persona dal Calendario («Prima di rispondere»), nota della chiamata
che a fine chiamata va in Note dell'app Eurise (`HubBridge`, stesso ricevitore della Tastiera), muto, tasti, vivavoce, attesa,
scambia e unisci con due chiamate, rifiuta con un messaggio, sensore di prossimità. Notifica a schermo intero per quelle in
arrivo. **Rubrica**: seconda icona nel cassetto (stessa app, `ContactsAlias`) che apre i contatti e riceve «Aggiungi in
rubrica» dalle altre app; scheda con Chiama, SMS, WhatsApp, Telegram, Email, impegni insieme, recapiti (pressione lunga
copia), editor (nome, numeri, email, azienda, indirizzo, nota), elimina, blocca. Rubrica = quella di sistema (si sincronizza
con l'account del telefono). Base: API Android come Fossify Phone (GPL-3.0); interfaccia nostra in Compose.

**Eurise Messaggi** (`messages/`, artefatto `eurise-messaggi-debug-N`, `eu.eurise.messages`): app per gli SMS (ruolo SMS).
Elenco Persone / Codici e consegne / Avvisi (`MessageInsights.category`), codice in alto da copiare che si cancella dopo
10 minuti (impostazione), proposta «evento giovedì 15:00» nell'elenco. Conversazione: «Capito sul telefono» sotto gli ultimi
messaggi — appuntamento → Crea l'evento (in evidenza quando l'altra parte conferma: «ok», «va bene», «perfetto»…) e Rispondi
«Va bene»; numero → Chiama, SMS, WhatsApp, Telegram, Aggiungi in rubrica; email, link, indirizzo → Mappe; cosa da fare →
Attività; appunto → Note; altra lingua → «Traduci dal …» con Leggi; messaggi sospetti segnalati (conto bloccato, pacco in
giacenza con link). MMS con foto (ricevuti e mandati, foto ridotte sotto 290 KB) con il codice AOSP in
`third_party/aosp-mms` (Apache 2.0, vedi EURISE.md), invio programmato (WorkManager), notifiche con Rispondi, Letto, Copia
il codice; risposta veloce durante le chiamate (`HeadlessSmsSendService`). Logica pura con test: `core/data/messages`.
Comune ai due APK: `core/contacts` (rubrica, `Reach`, `Avatar`). Icone nuove in EuriseIcons (tastierino, chiudi chiamata…).

**Da provare sul telefono**: rendere Telefono e Messaggi le app predefinite; chiamata in uscita e in arrivo (anche a schermo
spento), nota della chiamata in Note, rifiuta con messaggio; SMS in arrivo e in uscita, codice OTP (copia, cancellazione),
MMS con foto in arrivo e in uscita, «Crea l'evento» dopo una conferma, Traduci (serve Leggi con la lingua scaricata);
Rubrica dall'icona e «Aggiungi in rubrica» da Messaggi; pannello Traduci nella Tastiera.
**Non ancora**: Disponibilità e posizione dell'incontro nella barra di Messaggi (la Disponibilità c'è già nella barra della
Tastiera), MMS di gruppo in uscita non provati, doppia SIM (si usa la SIM predefinita per gli SMS), le stesse proposte nelle
email (non c'è ancora l'app Mail), ricerca nel testo di tutti i messaggi (ora: nomi e ultimo testo). Mappe e Meteo: anteprime
approvate, codice da fare (base CoMaps).

**Office (punto 5)**: giro 2 del lavoro «Prototipo del motore di Office» partito alle 23:37 UTC dell'11 novembre (commit
del core `e588bf8`, ramo co-25.04 di LibreOffice/core). Configurazione passata in 1 minuto; disco con 111 GB liberi. Se
il riepilogo dice «tempo scaduto», rilanciare il lavoro (riparte dalla ccache). Le misure vanno nel riepilogo del giro.
**Prossimo passo**: prova sul telefono di Telefono, Rubrica, Messaggi e Traduci (APK 237: eurise-telefono, eurise-messaggi,
eurise-tastiera, eurise-leggi, eurise-debug); misure di Office e decisione; poi Mappe (base CoMaps) e Meteo dalle anteprime
approvate, punto 3 Browser.

## 11 novembre, 01:38 UTC: misure del motore di Office (giro 2, finito; artefatto `eurise-office-motore-2`)
- **Si compila su GitHub Actions in un giro solo**: 2 h 05 in tutto (make 1 h 59, 13.360 file oggetto), con 4 processori.
  Con la ccache salvata (554 MB) i giri dopo saranno molto più corti. Disco: sorgenti 2,5 GB, compilazione 4,6 GB,
  instdir 1,1 GB, archivi esterni 0,6 GB; liberi alla fine 97 GB. Lo spazio non è un problema.
- **Peso**: `liblo-native-code.so` (già ridotta con strip) 182,7 MB, compressa 66,1 MB; altre librerie (NSS, libc++,
  sqlite) 15 MB (4 MB compresse). Assets: interfaccia 35 MB (4 MB compressi), filtri/galleria/tavolozze 11 MB (7 MB),
  caratteri 9,7 MB (4,6 MB), configurazione e tipi UNO 4 MB (0,6 MB).
- **Stima dell'APK**: 214 MB con le librerie non compresse (come si fa di solito: si caricano direttamente), **87 MB** con
  le librerie compresse (installazione più lenta e spazio doppio sul telefono dopo l'installazione); senza caratteri
  (a richiesta) 4,6 MB in meno. Collabora Office su F-Droid sta in quell'ordine (compresso). Manca ancora l'interfaccia web
  di Collabora (cartella browser di online, per «Tutte le opzioni»).
- **Da decidere con il proprietario**: (a) tenere il motore intero (87 MB da scaricare, circa 270 MB installato);
  (b) motore ridotto: togliere Base, Math, Draw, filtri rari e la parte web (stima: si può arrivare intorno ai 50–60 MB
  compressi, va misurato con un secondo giro); (c) motore scaricato a richiesta da Eurise Office dopo l'installazione
  (APK piccolo, il motore come i modelli AI). Poi le anteprime di Office per telefono e tablet.

## 11 novembre, 07:22 UTC: varianti del motore di Office (giro 3, 10 minuti grazie alla ccache)
Stesso motore ricollegato con componenti diversi (`.github/scripts/office_variants.py`):

| Variante | Libreria | Compressa | APK stimato (compresso) | APK stimato (non compresso) |
|---|---|---|---|---|
| Completo | 182,7 MB | 66,1 MB | ~87 MB | ~214 MB |
| Senza Base e formati rari | 177,8 MB | 64,5 MB | ~85 MB | ~209 MB |
| Testi e fogli (senza presentazioni e disegni) | 173,2 MB | 62,8 MB | ~84 MB | ~204 MB |
| Testi e fogli, senza Base e rari | 169,4 MB | 61,5 MB | ~82 MB | ~200 MB |
| Solo testi | 168,0 MB | 61,2 MB | ~82 MB | ~200 MB |

- **Togliere componenti non conviene**: al massimo 5 MB su 87 (6%). Il peso sta nel nucleo comune (interfaccia, grafica,
  filtri Office, dati delle lingue ICU, PDF), che serve a tutte le app. Draw e Impress sono lo stesso componente (sd).
- Con la ccache un giro intero dura 10 minuti: provare altre riduzioni costa poco. Restano da misurare, se servono:
  ottimizzazione per dimensione (-Os) e LTO, dati ICU ridotti alle lingue europee.
- Nota: nel pacchetto `eurise-office-motore-3` la libreria è quella «Solo testi» (svista corretta nel lavoro).

## 11 novembre, mattina: Scrivania, Meteo, prototipo di Mappe, anteprime del Browser (ramo `brand`, APK 245–246)
**Prova del proprietario (APK 237)**: Telefono, Rubrica, Messaggi e Traduci li controlla con l'uso quotidiano e li segnala
via via. Correzioni chieste e fatte (APK 245):
- Home: i preferiti si spostano tenendoli premuti e trascinandoli (`home/FavoritesGrid.kt`); fermi aprono il menu. Tolto
  «Sposta prima/dopo» un passo alla volta.
- Categorie: «Seleziona» nel menu dell'app, poi i tocchi scelgono più app e in alto «Sposta in…» le porta tutte nella
  categoria scelta (`SelectionBar` in `home/Overlays.kt`).
- Temi delle icone (Griglia e icone): Eurise, Sistema e i pacchetti di icone installati (`apps/IconThemes.kt`, formato
  `appfilter.xml` di ADW/Nova/Lawnchair; `<queries>` nel manifest della Scrivania). Le app non coperte tengono l'originale.
- Ricerca universale: velo un pizzico più chiaro (`searchScrim`), tolta la fila Nota/Da fare/Evento/Documenti/Web (è nella
  barra della Tastiera), contatti e app nella stessa pillola di vetro del campo di ricerca.

**Office**: il proprietario non ha scelto; si procede con **(c) motore scaricato a richiesta** (regola della leggerezza; le
varianti fanno risparmiare al massimo 5 MB). Da confermare. Un'altra sessione lavora sul lavoro di Office in parallelo.

**Meteo** (tavole approvate MeteoOggi, MeteoAllerta, BolImpegni; sezione dell'APK principale, `feature/weather`, voce
«Meteo» tra gli strumenti; peso invariato, 4,1 MB):
- `core/data/weather`: `MeteoParse` (Open-Meteo forecast con current/hourly/daily, Air Quality per i pollini CAMS, feed Atom
  di MeteoAlarm letto senza badare ai prefissi), `MeteoText` (nomi, aggiornamento scritto sul telefono, frase per gli
  impegni: calma/pioggia/allerta, del traffico non dice nulla), `Places`/`Provinces` («Monza (MB)», bandiera), `MeteoRepository`
  (file in `files/meteo`, previsioni e allerte 30 minuti, pollini 3 ore; posizione arrotondata a 2 decimali; luogo dal
  Geocoder del telefono). Test in `core/data/src/test/.../weather/MeteoTest.kt` (org.json vero solo nei test).
- Centro privacy: voci nuove **Pollini** (Open-Meteo, posizione arrotondata) e **Allerte** (MeteoAlarm, nessuna posizione:
  il feed del paese è uguale per tutti e la zona si confronta sul telefono).
- Allerta aperta: impegni del Calendario e cose da fare in quelle ore (forniti da `EuriseHub`), «Avvisami alle…» (sveglia
  con `AlarmClock`), Apri, consigli secondo il fenomeno.
- Scrivania: il meteo apre Eurise Meteo; sotto il primo impegno con orario la frase (`MeteoLineBox` in `Bubbles.kt`,
  `LocalMeteo`), caricata a ogni ritorno sulla Scrivania senza pollini.
- Da provare: nome del luogo (dipende dal Geocoder del telefono: su /e/OS può mancare, allora «La tua zona»), allerte vere
  (il confronto per parole tra regione/provincia e area MeteoAlarm va verificato con un'allerta in corso), frase nella bolla.
- Non ancora: modello ICON-2I scelto esplicitamente (ora «best match» di Open-Meteo), bollettini della Protezione Civile
  regionali, spegnere la frase dal menu della bolla, meteo sul percorso in Mappe.

**Mappe**: lavoro a parte «Prototipo di Eurise Mappe» (`.github/workflows/maps-engine.yml`, `maps/EURISE.md`): CoMaps da
Codeberg al commit `df67cc01` (11 novembre), `maps/eurise_patch.py` (nome «Eurise Mappe», pacchetto `eu.eurise.maps`,
chiave di debug della suite), `configure.sh`, `assembleFdroidDebug` arm64 con ccache. Artefatto `eurise-mappe-debug-N` con APK
e misure. Primo giro partito alle 08:40 UTC. Poi: stile della suite e le tavole approvate, un passo alla volta (vedi EURISE.md).

**Browser (punto 3)**: anteprime da approvare sulla tela, pagina nuova «Browser»: BrwNuovaScheda (siti fissati, «dalla suite
per oggi», tracciatori bloccati), BrwPagina (In Note, Da fare, Ascolta, Traduci, data trovata → Calendario; barra in basso
con scudo e indirizzo), BrwAccesso (passkey, password e codice 2FA da Eurise Password), BrwProtezione (bloccati su questo
sito, interruttore, cosa esce). **Da decidere**: motore GeckoView (Gecko di Firefox, estensioni e blocco tracciatori
completi, circa +50 MB per l'APK arm64) oppure WebView di sistema (APK piccolo, ma Chromium, aggiornato dal sistema, niente
estensioni; il blocco dei tracciatori lo facciamo noi con gli elenchi).

**Prossimo passo**: approvazione delle tavole del Browser e scelta del motore; esito del prototipo di Mappe (peso e prova
sul telefono); conferma di Office a richiesta; segnalazioni del proprietario dall'uso quotidiano.

## 11 novembre, tarda mattina: richieste del proprietario dopo l'uso (ramo `brand`, APK 250–252)
- **WhatsApp e WhatsApp Business distinti** (fatto): `Reach.whatsapp(business=)` non ripiega più sull'altro;
  `Reach.whatsappBusiness`. Messaggi, Telefono, bolla degli impegni offrono entrambi; nella Scrivania le righe di chat di
  WA Business nella scheda del contatto si chiamano «WhatsApp Business» (prima si fondevano con WhatsApp).
- **Icone** (fatto): app Eurise (Telefono, Rubrica, Messaggi, Voce, Galleria, Tastiera, Password) con disegno da 1,38 a
  1,75, centrato, stellina d'oro in alto a destra, sfumato leggero (`aapt:attr` gradient); Orologio +18%. Scrivania: simboli
  0,62 → 0,74 (suite 0,5 → 0,6) con sfumato leggero (`softTint` in `AppIcon.kt`). Le copie in `docs/brand/android` sono
  rimaste come prima.
- **Barra delle categorie**: fino a 9 (`MAX_CATEGORIES`).
- **Scanner, priorità altissima** (riferimento: lo scanner di kDrive). Fatto: difetto delle foto a raffica (il contatore
  ripartiva dopo ogni scatto), `AutoShot` (scatta una volta quando angoli e telefono sono fermi, si riarma solo quando la
  scena cambia; Rifai riarma), messa a fuoco sul foglio prima dello scatto, schermata «Pagina N: va bene?» con Rifai /
  Aggiungi un foglio / Fine, `SideRefiner` (lati rifiniti lungo la perpendicolare + retta ai minimi quadrati ortogonali,
  640 colonne in anteprima e 1200 sulla foto; test in `SideRefinerTest`). Da provare sul telefono e confrontare con kDrive.
  Se i bordi non bastano ancora: il passo dopo è un rilevatore appreso (rete piccola per i bordi del documento, come fanno
  gli scanner migliori), da addestrare fuori da Actions.
- **Mappe, prototipo** (giro 3): APK release arm64 di **57 MB** in 17 minuti (libreria 6 MB; simboli di tutte le densità
  11,7 MB, mappe del mondo e file 11,5, caratteri 10,1, testi dei paesi 6,7, codice e risorse 10,1). Riduzioni possibili:
  simboli della sola densità del telefono, caratteri non latini a richiesta, testi solo nelle lingue scelte.
  Artefatto `eurise-mappe-debug-3` (firmato con la chiave di debug della suite).
- **Mappe, segnalazioni degli utenti** (proposta da decidere): incidenti, lavori, polizia, pericoli, ostacoli come in Waze.
  Non c'è un servizio libero in tempo reale: serve un nostro servizio piccolo (segnalazioni anonime senza account, posizione
  solo della segnalazione, scadenza automatica: polizia 1 ora, incidente 2, pericolo 3; conferme «c'è ancora?»; si
  scaricano a riquadri di circa 10 km, così il server non sa dove sei di preciso). Le cose che durano (lavori lunghi,
  autovelox fissi, ostacoli) vanno invece in OpenStreetMap con l'editor già dentro CoMaps (accesso a OSM in due tocchi).
  Da decidere col proprietario: il servizio (costo e chi lo gestisce) e la voce nel centro privacy.
- **Browser**: prima fila **approvata**; seconda fila da approvare (BrwSchede, BrwSblocca con avviso e semaforo, BrwCondividi
  con link pulito e WA/WA Business distinti, BrwSincro: solo telefono / Murena-Nextcloud cifrato (3 tacche) / Firefox Sync
  (2 tacche, Mozilla conosce email e dispositivi, server USA), BrwMotore). Motore di ricerca proposto: **Brave Search**
  (indice proprio, risposte AI con le fonti; azienda USA, più debole su ricerche molto locali); alternative DuckDuckGo
  (Bing, Duck.ai anonimo con più modelli), Qwant ed Ecosia (europei, indice comune EUSP in crescita, in italiano ancora
  molto Bing). AI dalla barra: prima Eurise AI sul telefono, poi Duck.ai (2 tacche, la domanda esce). Motore del browser
  (GeckoView o WebView) ancora da scegliere.
- **AI** (tavole AiCattura, AiModelli, AiAttiva) **approvata**: APK separato «Eurise AI» senza permesso di rete
  (il modello lo scarica un'altra app della suite e lo passa con permesso di firma), comprensione del testo per la cattura
  rapida (modello da circa 2 GB, verificare la licenza: la tavola dice Ministral 3B Apache 2.0), voce in testo (Whisper
  base), traduzione (c'è già in Leggi), «chi può chiedere» per app, registro d'uso. Motore proposto: llama.cpp (MIT) dai
  sorgenti in `third_party/` come Tesseract per Leggi; prima un prototipo che misura peso e velocità.

- **Lettore QR**: mirino animato (angoli, linea che scorre mentre cerca; angoli azzurri che pulsano e «Codice visto · tieni
  fermo» quando ZXing trova i tre quadrati ma non legge ancora; verdi e più stretti per un attimo quando legge).
- **Bolla Documenti**: il pulsante Scanner apre lo scanner della suite (prima cercava un'app di scansione installata).
- **Mappe, guida**: cartello dell'indicazione colorato per tipo di strada con le tinte della suite (autostrada verde,
  statale blu…) e metri alla svolta in tempo reale: requisito scritto in `maps/EURISE.md`, da fare con lo stile di Mappe.

- **Richieste nuove da valutare (11 novembre)**:
  - «Ti hanno cercato»: rispondere a un singolo fumetto e mettere una reazione. Dalle notifiche Android (risposta diretta)
    WhatsApp e Telegram accettano solo testo: risposta citata e reazioni vere non si possono mandare da fuori. Fattibile:
    pressione lunga sul fumetto → «Rispondi a questa frase» (la frase citata in testa alla risposta) e reazioni rapide
    mandate come messaggio emoji; «Apri la chat» per la reazione vera. Prima la tavola.
  - Tasto «Allega» nelle risposte (chat ed email): contatto (vCard), documento, foto dalla Galleria, fotocamera. Dalle
    notifiche non si allega; si fa condividendo all'app con la persona già scelta dove l'app lo consente (WhatsApp sì, con
    il numero; Telegram fa scegliere la chat). Nella nostra Mail (quando ci sarà) e in Messaggi (MMS) è diretto. Prima la tavola.
  - App fotocamera nostra: proposta di non partire da zero ma da Aperture di LineageOS (CameraX, Apache 2.0, già usata e
    rifinita sui telefoni Lineage/e/OS), con telefoni di riferimento scelti dal proprietario (Fairphone 6, HIROH, Gigaset
    GS6 Pro, Volla Tablet, SHIFTphone 8: sensori da verificare modello per modello). Da decidere.
  - Mappe e Trivago/Booking/Tripadvisor: i loro dati non si possono prelevare (condizioni d'uso; API solo per partner con
    chiave e regole di visualizzazione). Fattibile subito: «Vedi su Booking / Tripadvisor» che apre la scheda del posto
    nell'app o nel sito (nome e città, nessun dato nostro). In seguito, se il proprietario registra l'app come partner,
    prezzi e voti dentro Mappe, con la voce nel centro privacy (la ricerca rivela la zona a quel servizio).

**Prossimo passo**: prova sul telefono dello scanner nuovo (APK 252) e della barra a 9; approvazione della seconda fila del
Browser, scelta del motore del browser e del motore di ricerca; decisione sulle segnalazioni in Mappe; poi Eurise AI
(prototipo llama.cpp), riduzione del peso di Mappe e stile della suite su Mappe.

## 11 novembre, mezzogiorno: decisioni del proprietario e tavole nuove (fine della chat del brand)
- **Office: confermato il motore scaricato a richiesta** (circa 66 MB con il Wi-Fi, una volta). Tavole nuove da approvare,
  pagina «Office» della tela: OffMotore (primo avvio, semaforo), OffTelefono (testo, barra di formato e Testo/Inserisci/
  Revisione/Tutte le opzioni, data trovata → Calendario), OffTablet (foglio di calcolo con elenco dei documenti, spese senza
  ricevuta → Da fare/Viaggio), OffComputer (schede dei documenti, struttura, «Collegato a», commenti con @persona → Da fare,
  revisioni Accetta/Rifiuta). Dopo l'approvazione: lavoro sull'app Eurise Office (APK piccolo) e sul download del motore.
- **Regola di spesa** (proprietario): si fa ora tutto ciò che non ha costi aggiuntivi; ciò che richiede servizi a
  pagamento o server nostri va nella road map, «quando e se la suite guadagnerà».
  - Mappe, segnalazioni: ora solo OpenStreetMap (nota anonima o modifica con l'account OSM dall'editor di CoMaps): lavori,
    strada chiusa, ostacolo fisso, autovelox fisso, limite sbagliato. Tavola MapSegnala da approvare. Polizia, incidenti e
    code in tempo reale (servizio nostro): road map.
  - Booking/Tripadvisor: ora solo i collegamenti «Vedi su…»; prezzi e voti come partner: road map.
- **Mappe e Android Auto**: CoMaps lo ha già in tutte le versioni (`app.organicmaps.car.CarAppService`, libreria aperta
  `androidx.car.app` 1.7.0, categoria NAVIGATION): Eurise Mappe lo eredita. Va mantenuto nello stile nuovo (le schermate
  dell'auto usano i modelli di Android Auto: colori e testi nostri, forma decisa da Google). Nota: Android Auto mostra le app
  installate fuori dal Play Store solo con «Fonti sconosciute» acceso nelle impostazioni sviluppatore di Android Auto
  (con App Lounge di /e/OS non serve). Da provare in auto.
- **Icone sulla tela**: tavola BrandIconeApp (pagina Brand) con prima/ora e versione a tema.
- **«Ti hanno cercato»**: tavole BolRispondi (pressione lunga sul fumetto: reazioni rapide inviate come emoji, «Rispondi a
  questa frase» con la citazione, Copia/Da fare/Note, Apri la chat) e BolAllega (Contatto, Documento, Galleria, Fotocamera,
  Posizione, Disponibilità; suggeriti dalla conversazione) da approvare (pagina Bolle).
- **Eurise AI: via libera al prototipo** (llama.cpp dai sorgenti, APK senza rete, misure di peso e velocità).

**Prossimo passo (nuova chat)**: approvazione delle tavole Office, MapSegnala, BolRispondi/BolAllega e della seconda fila
del Browser; prototipo di Eurise AI; poi codice di quanto approvato (bolla, Mappe con segnalazioni OSM, Office).

## 11 novembre, pomeriggio: prototipo di Eurise AI, Office a nastro (ramo `brand`, nuova chat)
- **Icone confermate** dal proprietario (tavola BrandIconeApp).
- **Eurise AI, prototipo** (`ai/`, lavoro «Eurise AI» in `build.yml`, artefatto `eurise-ai-debug-N`, `eu.eurise.ai`):
  llama.cpp `af6b524` (MIT) in `third_party/llama.cpp` (src, include, ggml solo CPU; vedi EURISE.md), CMake nostro
  (`ai/src/main/cpp`). Sul telefono ggml con le varianti Android della CPU (armv8.0 … armv9.2, dotprod e i8mm) scelte
  all'avvio, librerie estratte (`useLegacyPackaging`), `libc++_shared`. **Nessun permesso INTERNET** (la compilazione lo
  controlla con aapt2). Modulo compilato solo con `EURISE_AI=1`: l'APK principale non cambia.
  Motore comune `engine.hpp`: cattura rapida → JSON vincolato da grammatica GBNF (tipo evento/da_fare/promemoria/nota,
  titolo, data, ora, luogo, persone, note; i prossimi 14 giorni scritti per esteso nelle istruzioni, così un modello piccolo
  non sbaglia i conti), domanda libera, misura di velocità (256 token letti, 64 scritti).
  Modello: **Ministral 3 3B Instruct 2512, Apache 2.0** (licenza verificata), GGUF ufficiale di Mistral Q4_K_M, 2,1 GB.
  Nell'app di prova il modello si sceglie da un file già sul telefono (lo si scarica col browser da Hugging Face) e si copia
  nella memoria dell'app; la schermata mostra telefono, varianti della CPU, memoria, thread (2/4/6/8), «Misura completa»
  (velocità + dieci frasi di prova, `res/raw/frasi_prova.txt`, le stesse della CI) con «Copia i risultati».
  In CI: stesso motore per il computer (`eurise_ai_test`), `ai_quality.py` scarica il GGUF (cache) e misura frasi giuste,
  campi giusti e velocità (del computer, non indicativa).
  **Da provare sul telefono**: installare `eurise-ai-debug-N`, scegliere il GGUF, «Misura completa», mandare il testo copiato.
  Prossimi passi dopo le misure: passaggio del modello da un'altra app con permesso di firma (si può caricare anche da un
  descrittore aperto, `llama_model_load_from_file_ptr`, senza copia), servizio «chi può chiedere», Whisper dentro Eurise AI
  con una ggml sola (quella di llama.cpp), interfaccia delle tavole AiCattura/AiModelli/AiAttiva.
- **Office, richieste del proprietario**: su tablet e computer niente interfaccia scarna: **barra a nastro** con schede
  (File, Testo/Celle, Inserisci, …, Suite) e comandi divisi in gruppi con il nome, i più importanti grandi e subito visibili,
  ma con carattere nostro (gruppi come isole bianche sul Perla, scheda attiva con tratto azzurro e grassetto, gruppo
  **Suite** in borgogna con la stellina: Detta, Traduci, Da fare, Evento, Note / Viaggio, Grafico), «Cerca un comando»
  (Ctrl K). **Office collegato a Eurise File**: apre e salva dai file locali e dai cloud scelti (Murena, Nextcloud…,
  chiavetta), l'origine è sempre scritta («Salvato · Murena › Lavoro»), la colonna «In Eurise File» mostra la cartella e i
  file vicini. Tavole OffTablet e OffComputer rifatte sulla tela: **da approvare**. OffTelefono resta compatta.
- **Eurise AI, misure dei giri 2–4**: con le istruzioni tenute in memoria (si rilegge solo la frase) la cattura passa da
  27 a ~7 s per frase sul computer della CI; 8/10 frasi giuste al giro 3. Controlli fissi dopo il modello (`fix_capture`):
  ora tolta se nella frase non ci sono cifre («giovedì mattina»), data senza anno scritto entro i prossimi 12 mesi.
- **Office, altre richieste**: anche sul telefono la Suite (fila Detta, Traduci, Da fare, Evento, Note; scheda Suite nella
  barra in basso); **ricerca rapida** in tutte e tre le versioni (tasto o «Cerca un comando», Ctrl K): campo, Cerca /
  Chiedi all'AI, prima i risultati nella suite (documento, Eurise File, Calendario, Persona), poi le proposte web e AI,
  ognuna con il livello di privacy a tacche (3 resta qui, 2 esce solo la domanda, 1 con il tuo account): Nella suite,
  Brave Search, Qwant, Ecosia, DuckDuckGo; Eurise AI sul dispositivo, Duck.ai, Le Chat. Tavole OffTelefono, OffCerca
  (nuova), OffTablet e OffComputer con il riquadro aperto (interruttore «ricerca» nelle proprietà per nasconderlo).
  La stessa ricerca nella **Tastiera**: tavola TastCerca (pagina Integrazioni). Tutto da approvare.
- **Corretto, riquadro di ricerca della Tastiera** (segnalato: si apriva vuoto e la ricerca andava riscritta nel browser):
  `WebPopupActivity` non ritaglia più la WebView (sfondo con forma al posto di `clip`), pagina con fondo bianco, errori della
  pagina mostrati con «Apri nel browser» (la ricerca passa già scritta) e «Riprova». Da riprovare sul telefono.
- **Icone delle app senza simbolo** (CieID, Eko, Aruba PEC, MyEdison… e le web app): `LogoMask` ricava la sagoma del logo
  staccandola dal colore del bordo e la colora come le altre (test `LogoMaskTest`); per le web app il simbolo si cerca
  solo per nome (il pacchetto è quello del browser). Se il bordo non è uniforme resta l'icona originale.
- **Tema icone «Eurise a colori»** (Griglia e icone, accanto a Eurise e Sistema): stessi simboli su un tondo chiaro nella
  tinta dell'app portata alle tinte della suite, simbolo nella stessa tinta più profonda; di sera il tondo perde luce ma
  resta colorato. Il tema scuro resta quello di base. Da vedere sul telefono (manca la tavola: farla se va ritoccato).
- **Eurise AI, giro 4 (APK 259)**: **10/10 frasi giuste, 34/34 campi** con i controlli fissi; APK ridotto 6,0 MB senza
  Internet. Velocità vera da misurare sul telefono («Misura completa» nell'app, poi incollare il testo in chat).
- **Ultimo APK: 259** (tutti gli artefatti `*-debug-259`, compreso `eurise-ai-debug-259`). Gli artefatti non si scaricano
  dall'ambiente di Claude (memoria di GitHub su Azure bloccata): il proprietario li prende dalla pagina della compilazione.
- **Aggregatore email** (richiesta del proprietario): per /e/OS, Android e Linux, pensato per allargarsi ad altre
  piattaforme. Proposta: prima le tavole (elenco unico di più account, proposte dal contenuto come in Messaggi, collegamenti
  alla suite), poi il prototipo dopo Office; base da verificare: Thunderbird per Android (ex K-9 Mail, Apache 2.0, F-Droid)
  e la parte comune scritta in modo da girare anche su Linux.
- **Ancora da approvare sulla tela**: OffMotore, OffTelefono, OffCerca, OffTablet, OffComputer, TastCerca; MapSegnala; BolRispondi, BolAllega;
  seconda fila del Browser (motore del browser e motore di ricerca da scegliere).
- **Da provare sul telefono**: scanner nuovo, lettore QR, Eurise AI.
- **Poi, nell'ordine**: codice di quanto approvato (bolla «Ti hanno cercato», segnalazioni OpenStreetMap e stile di Mappe
  con Android Auto, riduzione del peso di Mappe, Eurise Office). Road map (regola di spesa): servizio nostro per
  segnalazioni in tempo reale, prezzi e voti come partner di Booking/Tripadvisor.

## 11 novembre, primo pomeriggio: editor da altre app, icone sulla tela, peso di Mappe, sistemi (ramo `brand`)
- **Tela, pagina «Icone»**: ora contiene tutte le tavole delle icone attuali, spostate da Brand e Scrivania (BrandIconeApp,
  BrandFamiglia, BrandAndroid, ScrIcone, componente BrandIcona) più la tavola nuova **IconeColori** (tema «Eurise a colori»
  com'è nel codice: le dodici tinte, giorno, sera, confronto con il tema Eurise). Le icone vecchie restano in fondo
  («Prima del brand»).
- **Galleria, corretto**: «Modifica» da un'altra app (ACTION_EDIT) e «Condividi › Modifica con Eurise» (ACTION_SEND con
  image/*) aprono il nostro editor (`EditActivity`, solo l'editor, in un compito a sé); l'originale resta, la copia va in
  Pictures/Eurise e torna a chi l'ha chiesta. `saveEdited` restituisce la copia. Da provare sul telefono.
- **Mappe, peso** (giro 5 del lavoro «Prototipo di Eurise Mappe»): `maps/eurise_slim.py` dopo `configure.sh` toglie simboli
  mdpi/hdpi/xxxhdpi (restano xhdpi, 6plus, xxhdpi; `eurise_patch.py` aggiorna la tabella di `VisualParams`), i caratteri delle
  scritture rare (il motore usa i Noto del sistema) e i nomi dei paesi in lingue non europee (si ripiega sull'inglese).
  Giro 6: **APK da 57 a 41 MB** (simboli 11,7 → 6,4; caratteri 10,1 → 1,6; nomi dei paesi 6,7 → 4,3). I file restano su
  disco ed escono dall'APK con `ignoreAssetsPattern` (cancellarli non funziona: Gradle rigenera i simboli). Da controllare
  sul telefono: simboli e scritte della mappa.
  I sorgenti di CoMaps si leggono anche dal mirror GitHub `comaps/comaps` (Codeberg non è raggiungibile dall'ambiente di Claude).
- **Sistemi (regola fissa nuova in CLAUDE.md)**: Browser e Office per /e/OS, Android e Linux con Ubuntu e Fedora come
  riferimento, struttura nucleo/interfaccia/guscio per arrivare ad altri sistemi. Proposta in `docs/PIATTAFORME.md`
  (**da approvare**): Browser su Gecko ovunque (GeckoView su Android; Firefox com'è su Linux con l'estensione Eurise
  comune e il native messaging), Office con l'interfaccia web di Collabora personalizzata Eurise (nastro, Suite, ricerca)
  uguale su tutti i sistemi, Flatpak su Flathub e prove automatiche in contenitori Ubuntu e Fedora.
- **Mappe, ancora da fare** (dopo la prova del prototipo sul telefono): stile della suite, cartello colorato per tipo di
  strada con i metri in tempo reale, «Vedi su Booking / Tripadvisor», segnalazioni OSM (tavola MapSegnala da approvare),
  Android Auto nello stile nuovo, caratteri rari scaricabili a richiesta se servono.

- **APK da scaricare direttamente**: pre-release `apk-prova` (https://github.com/eurise-suite/eurise/releases/tag/apk-prova),
  con tutti gli APK dell'ultimo «Compila» riuscito sul ramo brand **e l'ultimo di Eurise Mappe**. Si ripubblica quando
  cambia `.github/workflows/apk-prova.yml` (basta toccarne un commento: il lancio a mano non va, il file non è su `main`).
- **Ultimo APK: 263** (in `apk-prova`; Mappe giro 6).

**Prossimo passo**: approvazioni (tavole Office, seconda fila del Browser, MapSegnala, BolRispondi/BolAllega, TastCerca;
Gecko per il Browser e interfaccia web per Office); prove sul telefono (editor da altre app, Mappe alleggerita, scanner,
QR, Eurise AI); poi Office (interfaccia web Eurise) e Browser (estensione comune, app GeckoView), con Linux nello stesso lavoro.

## 11 novembre, pomeriggio: approvazioni, simboli delle segnalazioni, telefono e computer (ramo `brand`)
- **Approvato dal proprietario**: MapSegnala, con un simbolo nei colori della suite per ogni segnalazione (fatto sulla tela:
  Lavori ottone, Strada chiusa borgogna, Ostacolo fisso terracotta, Autovelox fisso blu notte, Limite sbagliato prugna,
  Altro errore grafite; le segnalazioni inviate restano sulla mappa con lo stesso simbolo finché OSM non le accoglie).
  **Gecko per il Browser** e **interfaccia web di Collabora per Office** su tutti i sistemi (docs/PIATTAFORME.md).
- **Richieste del proprietario per il Browser**: sincronizzazione semplice, segnalibri e password importabili dagli altri
  browser, estensioni nostre anche sul computer (compilazione delle password), predisposizione per le app Eurise del
  computer (email, note, calendario, appuntamenti, cose da fare, Oggi, dettatura), comunicazione rapida e continua tra
  telefono e computer: «il flusso di lavoro non deve interrompersi». Risposta: **Ponte Eurise** (PIATTAFORME.md) e tavole
  nuove sulla tela, pagina Browser: BrwSincro rifatta semplice, terza fila BrwImporta, Ponte, PcFirefox (Ubuntu) —
  **da approvare**.
- **Ancora da approvare**: tavole Office (OffMotore, OffTelefono, OffCerca, OffTablet, OffComputer), seconda e terza fila
  del Browser, TastCerca, BolRispondi, BolAllega.

**Prossimo passo**: codice delle segnalazioni OSM in Mappe (base CoMaps, editor OSM già dentro); dopo le approvazioni,
Office (interfaccia web Eurise) e il nucleo del Ponte; prove sul telefono dell'APK 263.

## 11 novembre, sera: tutto approvato, codice (ramo `brand`)
**Approvato dal proprietario**: tavole Office (OffMotore, OffTelefono, OffCerca, OffTablet, OffComputer), seconda e terza
fila del Browser (BrwSchede, BrwSblocca, BrwCondividi, BrwSincro rifatta, BrwMotore, BrwImporta, Ponte, PcFirefox),
TastCerca, BolRispondi, BolAllega. Indicazione: «macina codice».

- **Ti hanno cercato** (BolRispondi, BolAllega; `feature/launcher/notifications/ReplyExtras.kt`, `ReplyText.kt` con i test):
  pressione lunga su un fumetto → reazioni rapide (mandate come emoji), «Rispondi a questa frase» (in WhatsApp citazione
  vera con «> », altrove tra «»), Copia, In Da fare, In Note. Graffetta «Allega»: Contatto (si sceglie un numero, entra
  come testo), Documento, Galleria, Fotocamera (si aprono nell'app della conversazione; in WhatsApp nella chat giusta se
  la rubrica conosce il numero), Posizione (permesso della posizione precisa chiesto solo qui; link OpenStreetMap),
  Disponibilità (orari liberi da un'ora nei prossimi tre giorni lavorativi, dal Calendario), contatti nominati nella
  conversazione. FileProvider proprio (`ReplyFileProvider`). Compilato (APK 266). Da provare sul telefono.
- **Tastiera, ricerca rapida** (TastCerca; `keyboard/.../WebSearch.kt`, `SearchPanel`): i tasti Browser e AI aprono il
  pannello con la domanda dalla selezione o dall'ultima riga, Cerca / Chiedi all'AI, ogni posto con chi è, dove sta e le
  tacche (3 resta qui, 2 esce solo la domanda, 1 con il tuo account). Brave Search di base. «Nella suite» apre la ricerca
  universale della Scrivania già scritta (extra `eu.eurise.extra.SEARCH` di `LauncherActivity`). Compilato.
- **Mappe, «Segnala qui»** (MapSegnala; `maps/android/.../EuriseReportActivity.java`, copiata e agganciata da
  `eurise_patch.py`): pulsante «Segnala» nella scheda del luogo (oltre il quarto va in «Altro»), sei segnalazioni con il
  simbolo nei colori della suite, dettaglio facoltativo; con l'account OSM nota dall'editor di CoMaps, senza account nota
  anonima con l'API 0.6 di OSM. In compilazione (giro 7 del lavoro Mappe).
- **Eurise Browser** (`browser/`, APK separato `eu.eurise.browser`, solo arm64, lavoro a parte «Eurise Browser» con
  `EURISE_BROWSER=1`): GeckoView dal Maven di Mozilla. La 160 chiede compileSdk 37 e un plugin Android più nuovo della
  suite (8.7.3): per il prototipo `[130.0,142.0)`; **prima della versione ufficiale va aggiornato il plugin per tutta la
  suite e si torna all'ultima GeckoView** (sicurezza). Fatto: schede e schede private, barra in basso (scudo, indirizzo o
  ricerca, schede, menu), nuova scheda con siti fissati e tracciatori bloccati, protezione rigorosa e cookie isolati con
  sblocco per sito (BrwSblocca), In Note / Da fare nell'app Eurise (HubBridge), Ascolta (sintesi vocale del telefono),
  Condividi con link pulito (`Links.clean`), motore di ricerca con le tacche, segnalibri e importazione dal file HTML di
  Chrome, Firefox, Brave, Edge (`BookmarksHtml`), download nel gestore di sistema, apre link, ricerche e testi condivisi.
  L'**estensione Eurise** comune sta in `browser/extension` (stessa per Firefox sul computer): legge titolo, frase scelta
  e testo solo quando l'app lo chiede. Password: il sistema di compilazione automatica di Android con Eurise Password.
  Non ancora: sincronizzazione (passa dal Ponte), passkey, data trovata → Calendario, Traduci.
- **Office per Android** (`.github/workflows/office-app.yml`, `office/app/`): app Collabora Office (online
  `co-25.04`, `--enable-androidapp`) sul nostro motore, POCO e zstd per Android arm64, colori Eurise sull'interfaccia web
  (`eurise.css`: Perla e Notte, accento azzurro, scheda attiva con tratto e grassetto, gruppi a isole), pacchetto
  `eu.eurise.office`, chiave di prova. Primo giro in corso (prima caduto per la rete dei server; ora su Ubuntu 24.04).
  Poi: scheda Suite del nastro, «Cerca un comando», Eurise File, motore a richiesta, italiano; Linux con `--enable-qtapp`.
- **Ponte Eurise** (tavola Ponte; docs/PIATTAFORME.md):
  - `core/ponte` (Kotlin puro JVM, test provati anche nell'ambiente di Claude): identità EC P-256, ECDH + HKDF-SHA256,
    AES-256-GCM con il mittente autenticato, codice di conferma a sei cifre, abbinamento con prova del segreto del QR
    (`Opening`), messaggi (Continua qui, appunti, dettatura, chiamate, password, file), guardia contro i ripetuti, cornici
    TCP, nomi dei file per il passaggio dal cloud (non ancora usato).
  - `desktop/ponte` → **eurise-ponte per Linux** (artefatto `eurise-ponte-linux-N`; serve Java 17): `eurise-ponte collega`
    mostra il QR nel terminale; poi riceve sulla rete di casa e usa gli strumenti di Ubuntu e Fedora (wl-copy/xclip,
    wtype/xdotool, xdg-open, notify-send). Provato qui con `prova-telefono`: abbinamento e messaggi arrivano.
  - `feature/ponte` sul telefono: «Telefono e computer» tra gli strumenti della Scrivania; il lettore QR riconosce il
    codice («Collega questo computer») e mostra il codice di conferma; «Invia al computer» nel menu Condividi (link →
    si apre sul computer, testo → appunti); «Detta al computer». Interruttore per ogni tipo.
  - Non ancora: dal computer al telefono, «Ti hanno cercato» sul computer, password dal telefono, passaggio dal cloud
    fuori casa, interfaccia Compose e Flatpak per il computer, voce nel centro privacy.

- **Ultimo APK: 273**, in `apk-prova` con Eurise Browser (`browser-debug.apk`, 94 MB: GeckoView 141, librerie compresse)
  e **`eurise-ponte-linux.tar.gz`** (estrarre, poi `bin/eurise-ponte collega`; serve Java 17: `sudo apt install
  openjdk-17-jre` su Ubuntu, `sudo dnf install java-17-openjdk` su Fedora). Mappe in `apk-prova` è ancora il giro 6.
- **In corso o da rilanciare**: Mappe giro 7 (con «Segnala qui») e Office app: i server di GitHub non risolvevano i
  mirror di Ubuntu (apt fermo); ora i lavori lunghi girano su Ubuntu 24.04 e riprovano apt-get update. Se un giro
  fallisce ancora in «Installa gli strumenti», rilanciarlo.

**Prossimo passo**: esiti di Mappe giro 7 e Office app (correggere gli errori, poi `apk-prova`); Office: scheda Suite,
«Cerca un comando», motore a richiesta, italiano; Browser: estensione su Firefox per il computer con il Ponte e
aggiornamento del plugin Android della suite (per l'ultima GeckoView); Ponte: dal computer al telefono, «Ti hanno
cercato» e password dal telefono, cloud fuori casa, voce nel centro privacy.

## 11 novembre, sera tardi: Office Suite, scanner, tastiera, bolle (ramo `brand`, nuova chat)
- **Lavori lunghi**: `.github/scripts/apt-update.sh` (mirror di Azure → archive.ubuntu.com → DNS pubblico) in Mappe e
  Office app, passo d'installazione con un limite di 30 minuti. `office-engine.yml` non toccato apposta (stessa coda di
  Office app: farebbe partire ore di compilazione davanti all'app); se lo si rilancia, metterci lo stesso script.
  Il giro di Mappe 43043196722 è rimasto fermo su apt (non si è potuto annullarlo da qui): tiene la coda fino al suo
  limite di 5 ore, poi parte il giro 7 nuovo. **Il proprietario può annullarlo dalla pagina Actions.**
- **Office** (`office/app/EURISE.md`): scheda Suite del nastro, «Cerca un comando» (Ctrl K) con le tacche, pulsante
  Suite sul telefono (`web/Control.Eurise.js`, `eurise_web.py`), guscio Android `EuriseSuite.java` (Da fare, Note,
  Evento, Detta con Voce, Traduci con Leggi, Cerca), italiano del motore (`translations/source/it`, `--with-lang=en-US it`).
  Corretto: `eurise.css` in coda a `color-palette.css` avrebbe fatto cadere stylelint (`:root` doppio). Da fare: motore a
  richiesta (va separato da `libandroidapp.so`, che legge i file dall'APK), Eurise File, Linux.
- **Scanner, documento bluastro: corretto** (`DocumentFilter.kt`, test `DocumentFilterTest`): colore della carta stimato
  zona per zona e diviso via, grigi neutri, colori veri tenuti; bianco e nero dalla pagina pulita. Da provare sul telefono.
- **Tastiera**: metà dello spazio tra i tasti di base (`PREF_KEY_GAP_SCALE` 0,5; regolabile da 0,25) e altezza in verticale
  1,10 (era 1,12): tasti circa +9 % larghi e +6 % alti. Se il proprietario aveva già toccato «Spazio tra i tasti» o
  l'altezza nelle impostazioni, valgono le sue (va rimesso il valore predefinito). **Errori di battitura**: servono
  esempi dal proprietario (tasti vicini? spazio/lettere? lettere perse scrivendo veloce?) per lavorare su correzione del
  tocco e autocorrezione.
- **Scrivania**: «I tuoi appuntamenti» → **«I tuoi impegni»**; farmaci da prendere e compleanni/ricorrenze di oggi in
  fondo, dopo una linea leggera. **Ti hanno cercato**: icone dirette per ogni contatto (chiamata, SMS, email, chat con
  l'icona vera dell'app), recenti divisi in Telefono, Chat, Email (ognuna scorre) con in testa Telefono, WhatsApp,
  Telegram, Email (la nostra quando arriva, `eu.eurise.mail`).
- **Ponte, decisione del proprietario**: dal computer al telefono con un **collegamento fisso solo sul Wi-Fi di casa**
  con un computer abbinato e acceso (eccezione limitata alla regola «niente servizi sempre attivi»), **e sempre con la
  segnalazione di quel che si sta facendo**: la notifica fissa dice a quale computer si è collegati e l'ultima cosa
  passata («12:03 · appunti dal computer», «Password per sito.it: approva»). Piano: worker di WorkManager con
  `setForeground` (tipo `connectedDevice`, vincolo Wi-Fi) che tiene il socket finché il computer risponde, poi si
  rimette in coda; sul computer coda dei messaggi per i telefoni non collegati; socket locale (`$XDG_RUNTIME_DIR`) per
  il native messaging di Firefox e per Office; tipi nuovi Note e Task. Non ancora scritto.
- **Richieste nuove del proprietario (11 sera), servono le tavole prima del codice**:
  - **Editor dello scanner** dopo le foto: pagine una a una o tutte insieme; Foto, Scala di grigi, Bianco e nero,
    versione migliorata con l'AI («documento perfetto da vedere e stampare»), filtri, ritaglio; selezione e ritaglio
    della pagina ancora da rendere precisi. Obiettivo dichiarato: un livello sopra i migliori.
  - **App PDF in Eurise Office**: aprire il PDF e lavorarci con strumenti avanzati prima di condividerlo.
  - **Salva in… / Apri da…** con Eurise File (`feature/drive`, esiste già: navigatore unico telefono, Murena,
    Nextcloud, kDrive, WebDAV): riquadro comune per Scanner, Office, Galleria, download del Browser; si sceglie la
    cartella locale o cloud e si salva. Il proprietario lo considera ormai centrale.
- **Ultimo APK: 285** (in `apk-prova`, con il Ponte dal computer al telefono; prima 281). **Mappe**: l'APK in `apk-prova` è il giro 6 (solo alleggerito): niente ancora di nostro. Stile della suite, cartello
  della strada, «Vedi su…», segnalazioni (giro 7) da completare.

**Prossimo passo**: esito della compilazione di questo giro; tavole Editor scanner, PDF di Office e «Salva in…»;
codice del Ponte dal computer al telefono come deciso; esiti di Office app e Mappe giro 7.

## 11 novembre, notte: tavole nuove, Actions (ramo `brand`, nuova chat)
- **Actions**: annullato il giro di Mappe 43043196722 fermo su apt; il giro 7 (43052890129) è partito dalla coda. Office
  app: il giro 43050501063 (commit «Lavori lunghi», senza la Suite) è lasciato finire perché salva la ccache; dopo parte
  43052890217 con scheda Suite, «Cerca un comando» e italiano, e riparte dalla ccache.
- **Tavole nuove da approvare** (tela «Icone della suite»):
  - pagina **Drive e Scanner**, seconda fila: **ScanPagine** (Una a una / Tutte insieme; aspetto Foto, **Perfetta**, Grigi,
    B/N, Colore per tutte; «Già sistemato»: raddrizzata, dito e ombra tolti, pagina mossa → Rifai), **ScanPagina** (prima e
    dopo con la maniglia, intensità «Carta più bianca», timbro blu tenuto), **ScanRitaglio** (lente ×4 sull'angolo, maniglie
    sui lati, aggancio ai bordi della carta, forme A4/Scontrino/Tessera, libro aperto diviso in due, «Uguale a tutte»),
    **FileSalvaIn** (riquadro comune: nome, formato, proposte con il perché, Telefono/Murena/kDrive/Nextcloud, percorso,
    Nuova cartella, copia sul telefono, tacche di privacy, «Salva in Casa»), **FileApriDa** (chiesto da Office: recenti
    ovunque siano con l'origine, filtri per tipo, ricerca nel testo, Sfoglia, selettore di Android come ultima strada).
  - pagina **Office**, terza fila: **OffPdf** (Annota: evidenzia, nota, penna, testo, firma, moduli; campi da I miei
    dati), **OffPdfPagine** (ruota, estrai, dividi, duplica, elimina; inserisci da Scanner, PDF, foto, vuota),
    **OffPdfCondividi** (riduci peso Schermo/Stampa/Sotto 1 MB per la PEC, togli i dati nascosti, oscura CF e IBAN trovati
    davvero, blocca campi e firme, password in Eurise Password; si condivide una copia).
  - Scelte da confermare: «Perfetta» come nome dell'aspetto migliorato (calcolato sul telefono); PDF nell'app Office
    (non nel lettore PDF dell'APK principale, che resta per leggere).
- **Ponte, dal computer al telefono: scritto** (come deciso: Wi-Fi di casa, notifica che dice sempre cosa passa).
  - `core/ponte`: tipi nuovi `Note`, `Task`, `Ping` (i telefoni vecchi li scartano); **`Outbox`**, la coda su file per i
    telefoni non collegati, con scadenza per tipo (appunti 15 min, link 1 giorno, note e attività 7 giorni), l'ora della
    consegna e l'id di sempre (un doppione si scarta). Test `outboxKeepsOrderExpiresAndRestamps`, `newKindsTravel…`.
  - `desktop/ponte`: il collegamento del telefono resta aperto (segno di vita ogni 4 min, chiuso dopo 15 min di silenzio);
    **`eurise-ponte manda appunti [testo] | link <url> [titolo] | nota <testo> | attivita <testo>`** (senza testo:
    appunti del computer con wl-paste/xclip, o lo standard input). Passa dal **socket locale**
    `$XDG_RUNTIME_DIR/eurise-ponte.sock` (permessi 600, righe JSON: lo useranno Firefox e Office); se il Ponte non è
    acceso va in coda e parte alla prossima accensione. `prova-telefono` ora mostra per 3 s ciò che arriva.
    Idea per GNOME: una scorciatoia (Impostazioni › Tastiera) con `eurise-ponte manda appunti`.
  - Telefono (`feature/ponte/PonteLink.kt`): interruttore **«Dal computer al telefono»**, spento di base, con la
    spiegazione prima di accendere; chiede le notifiche e di togliere Eurise dall'ottimizzazione della batteria (senza,
    Android 12+ non lascia partire il primo piano dal fondo: allora la stessa notifica si mette a mano, mai collegati
    senza vederlo). Worker WorkManager con vincolo Wi-Fi (rete senza consumo) e computer su indirizzo privato; prova il
    computer e solo collegato mette la notifica fissa «Collegato a … · 12:03 · appunti dal computer»; tre tentativi, poi
    di nuovo fra 15 min. Appunti (sensibili se il computer lo dice), link come notifica «Continua qui», note e attività
    al ricevitore della Tastiera (`KeyboardBridgeReceiver`). Rispetta gli interruttori Appunti e Schede.
  - Non provato: qui Maven Central non è raggiungibile, compila e prova Actions. Ancora da fare: voce nel centro privacy
    (oggi elenca solo servizi esterni), password dal telefono, «Ti hanno cercato» sul computer, cloud fuori casa.
- **Ultimo APK: 285** in `apk-prova` (APK principale con il Ponte nuovo, `eurise-ponte-linux.tar.gz` con `manda`) e
  **Mappe giro 7** (lavoro 43052890129, artefatto eurise-mappe-debug-11: stile della suite, «Segnala qui»), da provare.

**Prossimo passo**: approvazione delle tavole nuove (ScanPagine, ScanPagina, ScanRitaglio, FileSalvaIn, FileApriDa,
OffPdf, OffPdfPagine, OffPdfCondividi); prova del Ponte dal computer al telefono sul telefono vero; esempi di errori di
battitura dal proprietario per la tastiera; prova di Mappe giro 7 sul telefono; esito di Office app (giro con la Suite in coda dietro quello che salva la ccache).

## 11 novembre, sera: tavole approvate, tastiera (ramo `brand`)
- **Approvate dal proprietario** tutte le tavole dello scanner (ScanPagine, ScanPagina, ScanRitaglio), FileSalvaIn,
  FileApriDa e l'app PDF di Office (OffPdf, OffPdfPagine, OffPdfCondividi), con «Perfetta» e il PDF da modificare in Office
  (il lettore dell'APK principale resta per leggere). Codice da scrivere.
- **Tastiera, errori del proprietario**: «b e n molto spesso al posto della barra spaziatrice, e invece di una lettera esce
  quella accanto». Fatto:
  - `config_key_hysteresis_distance` 8 → **16 dp**: con la selezione trascinando il dito (attiva sui telefoni) un pollice
    che rotola alzandosi cambiava il tasto in quello accanto.
  - **`TouchFix`** (`keyboard/.../TouchFix.kt`, agganci in `KeyDetector.detectHitKey` e `LatinIME.onEvent`): la parte bassa
    (di base il 20 %) dei tasti sopra la barra spaziatrice conta come spazio; la quota si regola da sola (lettera della
    fascia → cancella → spazio: +2,5 %; spazio della zona → cancella → lettera: −2,5 %; tra 8 % e 38 %), salvata solo
    sulla tastiera.
  - La proposta degli appunti esce dalla barra di Eurise (schiacciava Calcola e gli altri): resta quella di HeliBoard
    nella fila dei suggerimenti.
- **Tavole nuove da approvare** (pagina Integrazioni): **TastRighe** (sopra la barra di Eurise con sette comandi sempre
  interi — Disponibilità diventa «Libero», Browser e AI si uniscono in «Cerca» con la scelta classica/AI nel pannello —
  e «Altro» per gli strumenti di HeliBoard: appunti, emoji, una mano, impostazioni; sotto, sopra i tasti, una proposta
  «Incolla · …» sola e le parole), **TastPassword** (nei campi password la stessa fila mostra «Compila · account» di
  Eurise Password e «Altro account»; niente parole, niente appunti, la tastiera non impara). Dopo l'approvazione: togliere
  la barra degli strumenti di HeliBoard dalla fila dei suggerimenti (sovrapposizioni segnalate dal proprietario).
- **Ultimo APK: 290** in `apk-prova` (288 + spazio tra i tasti 0,7) (tastiera con TouchFix, Ponte dal computer al telefono, Mappe giro 7). Office non
  c'è ancora: il giro con la Suite è in coda dietro quello che salva la ccache.
- **Richiesta del proprietario (11 novembre)**: a fine lavoro gli APK nuovi si allegano **nella chat**, tutti aggiornati
  (scaricati dalla pre-release con `gh api …/releases/assets/<id>`, poi inviati come file), oltre a stare in `apk-prova`.

**Prossimo passo**: approvazione di TastRighe e TastPassword, poi il loro codice; codice dell'editor dello scanner, di
Salva in… / Apri da… e dell'app PDF di Office (tavole approvate); prova sul telefono di tastiera, Ponte e Mappe giro 7.
- **Tastiera, prova del proprietario sul 281** (dopo aver rimesso i valori predefiniti): tasti grandi, «forse un po'
  troppo», e sembrano uniti. Spazio tra i tasti di base da 0,5 a **0,7** (`PREF_KEY_GAP_SCALE`); altezza invariata (1,10).
- **Office in `apk-prova`**: `apk-prova.yml` ora prende anche l'ultimo giro riuscito di `office-app.yml` e lo pubblica
  come `eurise-office-debug.apk`. Giro 5 (43052890217, con la scheda Suite, «Cerca un comando», italiano): **237 MB**,
  perché il motore è ancora dentro l'APK. Il motore scaricato a richiesta (circa 66 MB, deciso) resta da fare. Da provare
  sul telefono.
- **Approvate dal proprietario: TastRighe e TastPassword.** Fatto nella tastiera: comandi Calcola, Codici, File, Persona,
  **Libero**, Traduci, **Cerca** (Web/AI nel pannello; pressione lunga = motore e browser) e **Altro** (`ToolsPanel`: gli
  strumenti di HeliBoard in griglia, tocco e pressione lunga come prima); con la barra di Eurise accesa la fila dei
  suggerimenti non mostra più il tasto della barra degli strumenti né i tasti fissati (`SuggestionStripView`,
  `euriseOwnsTools`); la proposta degli appunti è «Incolla · …» su una riga, al massimo il 62 % della larghezza; nei
  campi password niente appunti (la fila è di Eurise Password, che arriva con la compilazione automatica in linea).
- **Eurise File comune (`core/files`)** e **editor dello scanner** scritti (vedi il commit «Eurise File comune…»): da
  provare sul telefono. `FilesActivity` (`eu.eurise.action.SAVE_IN` / `OPEN_FROM`, permesso della suite) è pronta per
  Office, Galleria e Browser: va ancora chiamata da loro.
- **Office, app PDF** (tavole approvate): schede Annota e Pagine con un PDF, «Prima di condividere» con copia fatta dal
  guscio e passata a Condividi o a «Salva in…» (dettagli in `office/app/EURISE.md`). Il giro di Office è lungo: l'APK
  arriva in `apk-prova` (`eurise-office-debug.apk`) quando finisce.
- **«Salva in…» nelle altre app** (`core/suite/FilesContract`, attività `FilesActivity` dell'app Eurise):
  **Galleria** (terza voce del foglio Condividi per una foto o un video: copia dell'originale, permesso della suite e
  `HUB_PACKAGE` nella build), **Browser** (download finito: avviso in basso «Scaricato … · Salva in…» per 12 s;
  `SaveProvider` per la copia), **Office** (copia PDF di «Prima di condividere»). Manca: «Apri da…» in Office.
- **File e Scanner con un'icona propria tra le app di Android** (segnalato: «mi manca proprio l'app File»; finora erano
  solo nel cassetto della Scrivania): activity-alias `.FileApp` e `.ScannerApp` → `SectionShortcutActivity`, che apre
  l'app Eurise sulla sezione; icone adattive `ic_file`/`ic_scanner` sul fondo della suite con la stellina d'oro.
- **Eurise AI, prima misura del proprietario** (Fairphone 6, SM7635, 4 thread, Q4_K_M): lettura 41,9 token/s, scrittura
  7,4 token/s; **10 frasi su 10 con tipo e date giusti** (sbavature: «idraulico» tra le persone, «mattina» persa); **14,2 s
  a frase**, quasi tutti di scrittura (circa 80 token di JSON). Fatto: la risposta è una **riga compatta**
  `tipo|titolo|data|ora|luogo|persone|note` (vuoti senza «null», circa un terzo dei token), ricomposta in JSON uguale a
  prima (`capture_json`, provata qui); la **grammatica controlla prima solo il token scelto** e filtra tutto il
  vocabolario solo se lo rifiuta; la parte del giorno senza ora va nelle note. Da rimisurare (CI e telefono).

## 11 novembre, notte: prova del proprietario sul giro 290
- APK verificati: erano davvero quelli del giro 290 (versione 390, testi nuovi dentro).
- **Duck.ai bianco nel riquadro** (ricerca AI dalla tastiera): la WebView si presentava con «; wv» e alcuni servizi danno
  una pagina vuota; ora `WebPopupActivity` si presenta come il Chrome del telefono. Da riprovare.
- **Tastiera**: con lo spazio al 70 % «di nuovo piccoli» → **55 %**.
- **Icone più grandi e più spesse** (richiesta): icone adattive di tutti gli APK con disegno da 1,75 a **1,95** e tratto
  da 1,8 a **2,1** (Eurise AI 1,95), stellina spostata di (1, −3) verso l'angolo, ancora nel cerchio sicuro; nella
  Scrivania simboli al 68 % (prima 60 %), 80 % per le icone monocromatiche, 64 % per le sezioni, tratto 2,3 (prima 1,9).
- **Mappe**: il proprietario la vede «ancora quella originale», ed è così: finora solo nome, firma, alleggerimento e
  «Segnala qui». **Da fare** (tavole approvate): stile della suite, cartello colorato per tipo di strada con i metri in
  tempo reale, velocità e limite, «Vedi su…», meteo sul percorso, «dove devi andare» dalla suite.
- **Da capire con il proprietario**: Eurise Voce «non parte» (APK 390 corretto: serve sapere cosa succede, se si chiude
  o non si apre); bolle «non vedo gli aggiornamenti» (le righe nuove compaiono solo con farmaci o ricorrenze di oggi; le
  icone dirette di «Ti hanno cercato» solo con l'accesso alle notifiche).
- **«Ti hanno cercato», icone dirette nelle righe** (il proprietario le cercava lì: nel giro 281 erano solo nel foglio
  delle persone): accanto a ogni riga fino a tre icone (prima la chat da cui ha scritto, poi chiamata, SMS, email), solo
  per chi è in Rubrica (`ContactsRepository.actionsForName`, `BubbleActions.contactActions/startAction`, `DirectIcon`).
- **Eurise Voce «pacchetto non valido»** all'installazione da Telegram: l'APK 390 è integro (zip, firma v2 come gli altri,
  librerie allineate a 16 KB, manifest normale). Ipotesi: un'Eurise Voce già installata in modo diverso; consigliato
  disinstallarla e riprovare, o scaricarla dalla pagina della pre-release.

## 11 novembre, notte fonda: giro 305 consegnato, tavole nuove
- **APK 305** (pre-release `apk-prova`, Compila APK run 43110243048): bolla «Ti hanno cercato» con la fila delle app
  (WhatsApp, WhatsApp Business, Telegram, Telefono, Email, SMS predefinito, Eurise Messaggi quando c'è; solo le
  installate) sopra le righe con le icone dirette. Consegnati tutti in chat. Office nel 305 è ancora la build vecchia.
- **Office «Apri da…»** (commit fa563c6, `office/app/eurise_patch.py`): il pulsante Apri della schermata iniziale apre
  Eurise File (`OPEN_FROM`), altrimenti il selettore di sistema; Uri con lettura e scrittura, `takePersistableUriPermission`
  protetto. In attesa della build Office (run 43110242966, coda lunga). FilesActivity ora concede anche la scrittura.
- **File (file manager)**: non è un APK a parte, è dentro `Eurise.apk` con la sua icona «File» nel cassetto (anche Scanner).
- **Tavole nuove da approvare** (tela "Icone della suite"):
  - pagina **Chat ed Email**: ChatElenco, ChatConversazione, ChatAllega, ChatGruppo, ChatChiamata, ChatAccesso (Matrix,
    funzioni di WhatsApp/Telegram, nessun ponte che legga i messaggi), MailPosta, MailLettura, MailScrivi, MailCaselle.
  - pagina **Browser**, fila «Browser completo»: BrwMenu, BrwImpostazioni, BrwImportaPwd, BrwStrumenti, BrwSessioni.
    Il proprietario: browser troppo scarno (manca il menu Impostazioni e l'importazione delle password), usa Vivaldi,
    vuole di meglio. Dopo l'approvazione: codice in `browser/` (oggi ~1.150 righe: Gecko, schede, segnalibri, import HTML).
- Lezione: non spingere durante una compilazione: ogni push annulla quella in corso (concurrency).
- Prossimi passi: approvazione tavole → codice Browser completo, poi Chat (Matrix SDK leggero) ed Email; Mappe stile suite
  (tavola prima); misura AI dopo il formato corto; Voce: verificare l'installazione sul telefono.

## 12 novembre, notte: giro 310, Browser completo, Email, Mappe in stile suite, Office «Apri da…»
- **APK 310** (Compila APK run 43121538975, pubblicato in `apk-prova` dal run 43123268322) con: Browser completo, **Email
  nuova** (`eurise-email-debug`), Office con «Apri da…» (run 43118307755), Mappe giro 8 (run 43118578759).
- **Office**: un giro fantasma del gruppo `office-engine` (43095129592, «in coda» senza lavori, non annullabile da qui)
  bloccava l'app: `office-app.yml` ora ha il suo gruppo `office-app`. Da annullare a mano quando capita: gli altri fantasmi
  in coda sono «Compila APK» 43097501511, 43095454189, 43095129657 (non bloccano nulla).
- **Browser completo** (tavole BrwMenu, BrwImpostazioni, BrwImportaPwd approvate il 12 novembre): menu a griglia di 15 voci
  (Preferito, In Note, Da fare, Lettura, Traduci sul telefono con Firefox Translations, Trova, Cattura, Zoom del testo,
  Sito PC, Pagina scura, Download, Segnalibri, Password, Stampa PDF, In home); tieni premuto → voce nella barra; scorri
  sull'indirizzo → scheda accanto. Impostazioni in 11 sezioni con ricerca (Ricerca e AI con «? domanda» a Duck.ai o Le Chat,
  Privacy Forte/Standard, solo HTTPS, DNS cifrato Quad9/DNS4EU/Mullvad con spiegazione, Password, Schede riaperte all'avvio
  e chiuse dopo N giorni, Aspetto, Gesti, Siti con i permessi, Download, Importa ed esporta, Telefono e computer «in
  arrivo», Cancella dati). Importa: riconosce Vivaldi e gli altri browser installati, legge CSV e HTML, conta password e
  doppioni, passa le password a Eurise Password (ACTION_SEND) e cancella i file esportati. Cronologia sul telefono
  (`History.kt`, 90 giorni, non dalle private). API di GeckoView 141 verificate sui sorgenti (specchio GitHub
  mozilla-firefox/firefox, tag FIREFOX_141_0_RELEASE). Non fatti: BrwStrumenti, BrwSessioni (tavole approvate, codice dopo).
- **Email** (`mail/`, pacchetto `eu.eurise.mail`; tavole MailPosta, MailLettura, MailScrivi, MailCaselle): caselle con
  indirizzo e password, server trovati da soli (`Providers.kt`: Murena, Posteo, mailbox.org, Disroot, Infomaniak, GMX,
  WEB.DE, Libero, Virgilio, Aruba, Tiscali; poi autoconfig di Thunderbird, poi tentativi sul dominio); password cifrata
  nel Keystore; posta unificata con Persone/Avvisi/Newsletter; lettura in WebView senza JavaScript e senza rete finché non
  tocchi «Mostra»; allegati; Archivia/Elimina/Rispondi; Scrivi con copia negli Inviati; `mailto:`; controllo ogni 15 min
  (WorkManager) con notifica solo per le persone. Libreria nuova: Jakarta Mail per Android 1.6.7.
  **Da decidere col proprietario**: Gmail/Outlook/iCloud servono la registrazione OAuth di Eurise presso Google/Microsoft
  (passo una tantum del proprietario); per ora la schermata lo dice e non li offre. Non fatti: «Da fare» e scadenze trovate
  (bolletta), In breve, cartelle oltre la posta in arrivo, allegati in Scrivi, ricerca sul server.
- **Mappe giro 8**: `maps/eurise_style.py` (chiamato da `eurise_patch.py`) mette i colori Perla e Notte in 100 colori di
  CoMaps (Material 3 e schermate a View), provato sui sorgenti del commit fissato (specchio github.com/comaps/comaps);
  icona di Eurise Mappe al posto di quella di CoMaps. Resta da fare (tavole approvate): cartello colorato per tipo di strada,
  metri in tempo reale, velocità e limite, «Vedi su…», meteo sul percorso, «dove devi andare» dalla suite.
- **Drive/File**: invariato (nell'APK principale).
- Lezioni: in Kotlin una `fun setX()` accanto a `var x … private set` si scontra nella firma JVM (usare `@JvmName`);
  da qui non si raggiungono maven.mozilla.org, Maven Central né codeberg: i sorgenti si leggono dagli specchi su GitHub.
- **Prossimi passi**: prova sul telefono di Browser (import da Vivaldi, Traduci, DNS), Email (collegare una casella
  Murena/Libero) e Mappe; poi BrwStrumenti e BrwSessioni, Chat (Matrix), OAuth per Email, guida di Mappe.

## 12 novembre, notte (seconda parte): servizi chiusi, Email integrata, Chat cifrata, Drive come sulla tela
- **Regola nuova** (CLAUDE.md, «Servizi chiusi»): Google, Apple, Microsoft si offrono, decide l'utente; per ultimi, una
  tacca, avviso con limiti e alternative. Applicata a Email (Gmail, iCloud, Yahoo con password per le app e avviso;
  Outlook aspetta l'OAuth) e Browser (Google, Bing; ChatGPT, Copilot, Gemini).
- **OAuth (passo del proprietario)**: registrare Eurise presso Google Cloud (verifica per la posta), Microsoft Azure,
  Apple Developer; poi il client ID nel codice e l'accesso diventa «due tocchi».
- **Email con la suite**: proposte da MessageInsights (Calendario via `Reach.event`, Da fare e Note via `HubBridge`,
  Rubrica, chiama, Mappe, avviso truffa); allegati Apri / Salva in Drive (`FilesContract.saveIn`); Scrivi con allegati da
  Drive («Apri da…») o dal selettore; ACTION_SEND/SEND_MULTIPLE con file da ogni app.
- **Chat** (`chat/`, `eu.eurise.chat`): Matrix su HTTP nostro (ChatClient), accesso SSO dal browser (eurisechat://login)
  o password; elenco, inviti, conversazione, gruppi, notifiche ogni 15 min. **Cifratura end-to-end** (`ChatCrypto`):
  crypto-android 26.05.12 (matrix-sdk-crypto, come Element per Android; riferimento: OlmMachine.kt e
  OutgoingRequestsProcessor.kt di element-hq/element-android) + JNA 5.14 @aar, APK solo arm64. Chat nuove cifrate;
  decifratura con riprova quando arriva la chiave; il lavoro in background passa le chiavi al modulo (se l'app è aperta,
  `ChatModel.active`, lascia fare a lei). **Da fare**: chiave di recupero e backup delle chiavi (nel backup cifrato
  della suite), verifica dei dispositivi con emoji, UnifiedPush, server Eurise, prova con Element su un'altra chat.
- **Drive** (icona «Drive», ex «File»): testata e dock trasparenti corretti (la testata era dentro il suo `hazeSource`:
  ora ogni scheda ha il suo fondo e testata/dock stanno sopra). Come sulla tela: Scansiona/Carica/Cartella, colori per
  tipo (`DriveLook.kt`, anche Notte), cartelle a due toni per posto, barre dello spazio, filtri per tipo con i conteggi.
  Intelligenti: «Riordina Download» (documenti → Documenti, file vecchi e quanto pesano). Fatto anche «Per i tuoi impegni»
  (file per gli appuntamenti delle prossime 36 ore, dal Calendario). **Ultimo APK: giro 316** in `apk-prova`. Prossime idee: doppioni, ricerca nel testo dei PDF e delle scansioni, nomi proposti alle scansioni.

## 12 novembre, mattina: Browser (BrwStrumenti, BrwSessioni), guida di Mappe, Drive «Riordina», tavole della Chat
- **Browser · Lettura con gli strumenti** (tavola BrwStrumenti, `ReaderScreen.kt`, `Reading.kt`, `ArticleImage.kt`): in alto
  minuti di lettura, Traduci (rilegge il testo dopo la traduzione), Ascolta; la prima data da oggi in poi trovata nel testo
  (stesso lettore dei messaggi, `MessageInsights`) con «Promemoria» → evento nel Calendario (`Reach.event`); la nota del sito
  in cima. Strumenti in basso (si nascondono scorrendo): Trova nel testo con evidenziazione, Cattura intera (l'articolo
  disegnato in un'immagine lunga, fino a 16.000 px), Nota del sito (sul telefono, per dominio), Testo %, Carattere con o
  senza grazie, PDF, Immagini sì/no (lo sfondo dell'estensione `background.js` ferma le richieste di immagini con
  webRequest; versione dell'estensione 0.2.0), Leggi dopo (il testo resta sul telefono; elenco «Da leggere» in Segnalibri,
  si apre anche senza rete).
- **Browser · Schede** (tavola BrwSessioni, `TabsScreen.kt`, `Sessions.kt`): schermata intera con gruppi colorati (tinte
  della suite; tieni premuta una scheda → gruppo; i link aperti da una scheda restano nel suo gruppo), griglia con miniature
  (solo in memoria), ricerca, sessioni salvate da riaprire, «Chiuse di recente» (40), schede dimenticate dopo 7 giorni che
  finiscono lì invece di sparire. Tastiera: Ctrl+Tab, Ctrl+Maiusc+Tab, Ctrl+K, Ctrl+T, Ctrl+W. Non fatti: due schede affiancate
  sul tablet, «Sul computer» (in arrivo con l'estensione su Firefox). Il modulo usa ora anche `core:data` e `core:contacts`.
- **Mappe · guida** (giro 9, `maps/android/.../eurise/EuriseGuide.java` agganciato da `eurise_patch.py`): cartello della
  svolta e striscia della via con il colore del tipo di strada (A = Salvia, SS/SR/E = Blu notte, SP = Petrolio, comunale =
  blu della suite; senza riferimento decide il colore del cartello OSM), metri alla svolta a passi fini (10 m sotto i 200,
  50 fino a 500, 100 fino al km), «Manda l'arrivo» nel menu della guida. Patch provata sui sorgenti del commit fissato
  (specchio github.com/comaps/comaps). Restano: meteo sul percorso, frase d'arrivo, «dove devi andare» dalla suite.
- **Drive · «Riordina»** (accorpato, come chiesto: solo l'utile, in una scheda): oltre a documenti da spostare e file
  vecchi, ora copie doppie (stesso peso e stesso nome a meno di «(1)», «- Copia», «-1»: resta la più recente, le altre nel
  Cestino di Eurise per 30 giorni, con conferma) e nomi veri per le scansioni con un nome da scanner (Scan_0012,
  IMG_…, CamScanner…: testo del PDF letto sul telefono, `DocReader.fileName`, es. «2026-11 Bolletta luce Enel»; conferma
  con l'elenco). **Ricerca nel testo**: cercando in una cartella del telefono compare «Nel testo dei PDF» con il pezzo di
  frase (`DriveSmart.pdfText`, fino a 12 MB, in memoria). I nomi delle scansioni fatte con Eurise c'erano già (Scanner).
- **Tavole nuove da approvare** (tela, pagina «Chat ed Email», prima fila): **ChatRecupero** (chiave di recupero a gruppi,
  Mostra/Copia; dove tenerla: backup cifrato della suite consigliato, Eurise Password, carta con QR; copia cifrata delle
  chiavi sul server) e **ChatVerifica** (sette figure SAS di Matrix da confrontare, Sono uguali / Non sono uguali,
  dispositivi collegati con «Esci», verifica di una persona dalla chat). Dopo l'approvazione: codice in `chat/` con
  crypto-android (backup delle chiavi, `bootstrapCrossSigning`, verifica SAS).
- **Ultimo APK: giro 320** (Compila APK run 43172446974, pubblicato in `apk-prova` dal run 43174430865) con Browser,
  Drive e **Mappe giro 13** (guida). Test nuovi: `SessionsTest`, `ReadingTest` (browser), `DriveSmartTest` (drive).
- Lezione: con `str.replace` su un testo che compare due volte (`/** «Sito PC»`) il codice nuovo finisce due volte; contare
  prima le occorrenze.
- **Prossimi passi**: prova sul telefono (Lettura e strumenti, Immagini no, schede a gruppi e sessioni, Riordina con copie e
  nomi, ricerca nel testo dei PDF, guida di Mappe con cartello colorato e metri fini, più le prove del giro 316 di Chat,
  Email, Browser); approvazione di ChatRecupero e ChatVerifica, poi il loro codice; Mappe: meteo sul percorso e frase
  d'arrivo; Browser: schede affiancate sul tablet.

## 12 novembre, tarda mattina: Chat (ChatRecupero, ChatVerifica) e barra delle schede del Browser · giro 324
- Tavole **ChatRecupero**, **ChatVerifica** e **BrwBarraSchede** approvate dal proprietario; codice scritto.
- **Chat · chiave di recupero** (`SecretStorage.kt`, `ChatSecurity.kt`, `SecurityUi.kt`): archivio dei segreti di Matrix
  (m.secret_storage.v1.aes-hmac-sha2, HKDF/AES-CTR/HMAC) e chiave nel formato di Element (base58, prefisso 0x8B01,
  parità), compatibili con Element. Prima volta: chiavi di firma (`bootstrapCrossSigning`; i server recenti non chiedono
  conferma, altrimenti foglio «Conferma che sei tu»: password o conferma nel browser con il fallback SSO), copia cifrata
  delle chiavi (`room_keys`, megolm backup v1, firmata) e archivio. Se l'account ha già una chiave (Element): la si
  inserisce → chiavi di firma importate, telefono firmato (`verifyDevice`), copia riattivata e scaricata
  (`importRoomKeysFromBackup`). La copia delle chiavi sale a ogni tornata (`backupRoomKeys` in `flush`). Chiave sigillata
  dal Keystore per «Mostra/Copia» (appunti segnati sensibili).
- **Dove la tengo**: backup cifrato della suite tramite `SecretsContract` (core/suite) e `SecretsProvider` nell'app
  Eurise (preferenze `eurise_secrets`, comprese nel backup; risponde solo con il permesso della suite); sul telefono nuovo
  «Usa quella del backup della suite». Eurise Password: copia la chiave e apre Password (Password non ha ancora un modo
  per ricevere voci dalle altre app). Carta: stampa di Android (anche PDF), senza QR per ora.
- **Chat · verifica** (SAS con le 64 figure di Matrix, nomi italiani): richiesta da un altro dispositivo → si apre la
  schermata; «Verifica questo telefono» → `requestSelfVerification`; la verifica avanza da sola ogni 0,7 s; Sono uguali /
  Non sono uguali (`m.mismatched_sas`). Elenco dispositivi (`/devices` + fiducia del modulo) con «Esci» (`delete_devices`
  con conferma d'identità). Non ancora: verifica di una persona dalla chat (in stanza), QR.
- **Browser · barra delle schede** (`TabStrip.kt`, tavola BrwBarraSchede): Impostazioni › Schede › Barra delle schede:
  Automatica (tablet in cima, telefono spenta), Spenta, Sopra la barra in basso, In cima alla pagina; colore del gruppo
  sotto la scheda, ✕ su quella aperta, «+»; tieni premuto: sposta a sinistra/destra, gruppo, chiudi.
- Test nuovi: `SecretStorageTest` (esempio della specifica, andata e ritorno, errori di battitura, mac).
- Lezione (di nuovo): negli `strings.xml` scritti con Python, `\'` dentro `'''…'''` diventa `'` e aapt2 si ferma
  («Invalid unicode escape»). Dopo ogni scrittura di testi: controllare gli apostrofi.
- **Ultimo APK: giro 324** in `apk-prova`. Prossimi passi: prova sul telefono (giri 316, 320, 324); verifica di una
  persona dalla chat; QR sulla carta; Password che riceve voci dalla suite; Mappe: meteo sul percorso.

## 12 novembre, pomeriggio: verifica di una persona, QR sulla carta, Password dalla suite, meteo sul percorso · giro 328
- **Chat · verifica di una persona** (tavola ChatVerifica, `ChatSecurity.requestUser`, `ChatCrypto.sendInRoom` e
  `receiveVerification`): nelle chat a due cifrate lo scudo in testata manda la richiesta come messaggio della stanza
  (`verificationRequestContent` + `requestVerification`, cifrata come gli altri messaggi; i passi della verifica pure). I
  passi in arrivo (`m.key.verification.*` e la richiesta) vanno dal /sync al modulo (`receiveVerificationEvent`; per la
  richiesta prima `/keys/query` del mittente). Le richieste in arrivo si cercano anche tra le persone che hanno mandato passi
  (`incoming(peers)`): la schermata delle figure si apre da sola con i testi per la persona («confrontatele di persona o in
  chiamata»), senza elenco dei dispositivi. Testata: «Cifrata · persona verificata» (`isIdentityVerified`). Nella chat la
  richiesta è una riga al centro («Anna ha chiesto di verificarvi»). Se la persona non ha chiavi di firma: avviso. Corretto
  anche `decrypt`: `m.relates_to` (in chiaro fuori dalla parte cifrata) torna nel contenuto decifrato.
- **Chat · QR sulla carta** (`KeyQr.kt`): SVG disegnato con ZXing (già nella suite), correzione M, accanto alla chiave;
  si legge con lo Scanner di Eurise e si incolla in «Inserisci la chiave».
- **Password riceve voci dalla suite** (`PasswordContract` in core/suite): ingresso `AddFromSuite` (activity-alias di
  PasswordActivity con il permesso della suite; l'azione `eu.eurise.action.ADD_PASSWORD` si ignora se non arriva da lì);
  la voce si apre compilata e si salva solo se confermi; il segreto si toglie dall'intent. La scheda di modifica ora prende
  dalla voce precompilata anche password, indirizzo e note. La Chat manda «Chat · chiave di recupero» così (niente appunti);
  con una Password vecchia resta «copia e apri».
- **Mappe · meteo sul percorso** (tavola MapGuida): Mappe non contatta servizi da sola. Chiede all'app Eurise
  (`RouteWeatherContract`, `RouteWeatherProvider` nel modulo app) che passa dal **centro privacy** (servizio nuovo
  «Meteo sul percorso · Open-Meteo», `PrivacyService.RouteWeather`, **spento di base**) e fa una sola richiesta a Open-Meteo
  con fino a 8 punti della strada che resta (arrotondati a ~1 km) e l'ora di passaggio (`core/data` `RouteWeather`, test
  `RouteWeatherTest`). In Mappe (`EuriseRouteWeather.java`, `RouteSampler.java`, agganci in `eurise_patch.py`): riga
  satinata sotto la via («🌧 Pioggia tra 40 km, verso le 15:20», «☀ Niente da segnalare · 14° all'arrivo»), toccandola
  l'elenco punto per punto con «Spegni»; spenta, la riga invita ad accenderlo con la spiegazione (cosa esce, dove va, chi
  lo vede, come si torna indietro). Punti dalla strada del percorso (`nativeGetRouteJunctionPoints(1000)`), uno ogni almeno
  20 km; ore in proporzione al tempo che resta; si richiede ogni 15 minuti. Solo in auto/bici, non a piedi. Mappe dichiara
  e usa `SUITE_DEBUG` (è firmata con la chiave di debug della suite); senza l'app Eurise la riga non compare. Patch provata
  sui sorgenti del commit fissato; `RouteSampler` provato con javac qui.
- Nota per dopo: `SecretsProvider` e `RouteWeatherProvider` accettano anche `SUITE_DEBUG`; con le versioni ufficiali
  converrà accettare solo `SUITE` (la chiave di debug sta nelle Actions).
- **Ultimo APK: giro 328** (Compila APK run 43224127579) e **Mappe giro 14** (run 43224127057) in `apk-prova`.
- **Prossimi passi**: prova sul telefono dei giri 316, 320, 324 (elenco nella chat del 12 novembre) e ora 328: verifica di una
  persona con Element sull'altro telefono, QR stampato e letto con lo Scanner, «In Eurise Password» dalla Chiave di recupero
  (voce compilata), meteo sul percorso in un viaggio (accendi, riga, elenco, Spegni; poi controlla il centro privacy).
  Dopo: Mappe frase d'arrivo e «dove devi andare» dalla suite; Browser schede affiancate sul tablet; Email «Salva in
  Password» con lo stesso contratto.

## 12 novembre, pomeriggio (seconda parte): «Dove devi andare», frase d'arrivo, schede affiancate · giro 331
- **Mappe · «Dove devi andare»** (tavola MapCerca): prima scheda della ricerca, «Impegni» (`search/EuriseGoFragment.java`,
  aggancio in `TabAdapter` da `eurise_patch.py`): impegni dei prossimi 7 giorni con un luogo, letti dall'app Eurise
  (`DestinationsContract` in core/suite, `DestinationsProvider` nel modulo app, solo app della suite, niente rete). Un tocco
  mette il luogo nella ricerca (mappe scaricate) e ricorda l'impegno. Messaggi se manca l'app Eurise o il permesso del
  Calendario. Non ancora: Viaggi (voli) e Rubrica come voci a sé (la Rubrica c'è già col pulsante contatti di CoMaps),
  «Mappe sul telefono» proposte dai viaggi.
- **Mappe · frase d'arrivo** (tavola MapGuida): riga sotto il meteo nella guida (`EuriseArrival.java`, `ArrivalPhrase.java`
  provata con javac): «Pioggia all'arrivo: arrivi a «Sopralluogo» delle 9:30 con qualche minuto di margine (6 min)»; anticipo,
  giusto in tempo, ritardo; il tempo all'arrivo solo se il meteo sul percorso è acceso. Toccandola, «Manda l'arrivo». Vale per
  6 ore dalla scelta e fino a mezz'ora dopo l'inizio. Non ancora: «Manda l'arrivo ad Anna» con i partecipanti dell'evento.
- **Email**: nel foglio «Collega», «Tienila anche in Eurise Password» (acceso se Password c'è): dopo il collegamento Password
  apre la voce compilata (PasswordContract).
- **Browser · schede affiancate** sul tablet (larghezza ≥ 720 dp; tavola BrwSessioni): «Affianca a quella aperta» tenendo
  premuta una scheda nella barra, Ctrl+\; un tocco sceglie il riquadro in uso (riga colorata), ✕ per tornare a una sola.
  Una sessione di Gecko sta in una vista alla volta (`Gecko.views`); la vista si libera quando sparisce.
- **Ultimo APK: giro 331** (Compila APK run 43229914728) e **Mappe giro 15** (run 43229649215) in `apk-prova`.
  Cambiati rispetto al 328: app-debug (Eurise), browser, mail, eurise-mappe. Uguali: gli altri.
- **Regola del proprietario** (12 novembre): a ogni consegna dire quali APK sono cambiati e quali no.
- **Prossimi passi**: prove sul telefono (giri 316–331); Mappe: Viaggi e Rubrica in «Dove devi andare», mappe proposte dai
  viaggi, «Manda l'arrivo» a un partecipante; «parti entro» (MapPercorso) con la sveglia.

## 12 novembre, sera: accessi ai siti, import senza file, webapp, tastiera, bolla «Ti hanno cercato» · giro 335 (vedi sotto)
Richieste del proprietario (12 novembre, dopo le prove del giro 331), con lo stato:
- **Password ↔ Browser, accessi istantanei** (fatto): `LoginsProvider` in Password (`autofill/`, solo app della suite,
  a cassaforte aperta, ogni richiesta rimanda di 2 minuti la chiusura automatica), ingresso «Sblocca»
  (`PasswordContract.ACTION_UNLOCK` sull'alias `AddFromSuite`, torna appena aperta). Browser: `Logins.kt` è lo
  `Autocomplete.StorageDelegate` di Gecko (`loginAutofillEnabled`): un accesso solo → Gecko compila da sé; più accessi →
  bolla «Accedi come…» (`LoginBubbles.kt`, `onLoginSelect`); dopo un accesso nuovo → «Salvo l'accesso?» (`onLoginSave`,
  mai nelle private); Password chiusa → «Sblocca e accedi» e la pagina si ricarica. Salvataggio a cassaforte chiusa: voce
  compilata in Password. Corrispondenza per dominio (anche sottodomini dello stesso dominio chiesto da Gecko).
- **Import senza file** (fatto): il Browser riceve la condivisione di «Esporta password» di Vivaldi/Chrome/Brave
  (`SEND`/`SEND_MULTIPLE` CSV o HTML, voce «Eurise · importa») e apre Importa con i conteggi; guida aggiornata («Apri»
  apre il browser di origine). Firefox esporta solo in un file: resta «Ho già un file esportato».
- **Webapp vere** (fatto): «In home» crea una scorciatoia verso `WebAppActivity` (finestra per sito, a schermo intero,
  `documentLaunchMode=intoExisting`, nome e iniziale nelle recenti, icona adattiva con l'iniziale). Accessi da Password
  anche lì; i link che aprono finestre nuove vanno nel Browser.
- **«Ti hanno cercato»** (fatto in parte): tolte le icone accanto a ogni messaggio/chiamata, la fila di app in testa alla
  bolla e ai recenti, le icone accanto ai contatti recenti e preferiti (resta la tendina, con le icone delle app nel tema
  scelto, anche per telefono/SMS/posta predefiniti: `contacts/DefaultApps.kt`). Da fare dopo l'approvazione: icona menu
  in testa (stessa dimensione e stile di recenti e preferiti) che apre la bolla con tutte le app per comunicare, icone del
  tema e numeri delle notifiche (tavole **BolComunica**, **BolComunicaApp**).
- **Tastiera** (fatto): tasti piccoli a ogni installazione = le migrazioni di HeliBoard ripartivano perché i numeri di
  versione di Eurise sono più bassi (una rimetteva lo spazio tra i tasti al 175 %). Saltate (`AppUpgrade.kt`), e una volta
  sola si tolgono i valori 1,75/1,1 lasciati. Precisione: `TouchOffset.kt` impara lo scarto medio dei tocchi riusciti e lo
  toglie prima di scegliere il tasto (piano, max ¼ di tasto). Da valutare dopo la prova: confronto con FUTO Keyboard
  (modello di tocco per tasto, correzione con il dizionario sulle lettere vicine).
- **Tavole nuove da approvare** (tela «Icone della suite»): pagina Browser fila 5 — **BrwNuovaSchedaPro** (ricerca con
  «Chiedi all'AI» e QR, «Riprendi da dove eri», siti fissati, «Per oggi, dalla suite», strumenti, protezione),
  **BrwIndirizzo** (tocco sull'indirizzo: Copia, Condividi, Modifica, QR, In Note, Da fare, Promemoria, Leggi dopo, Manda al
  computer, Copia pulito), **BrwSegnalibri** (icona segnalibri accanto all'indirizzo, cartelle e ricerca),
  **BrwEstensioni** (estensioni di Firefox da addons.mozilla.org: GeckoView le supporta); pagina Scrivania fila 12 —
  **ScrDock** (icona tutte le app → categorie + fino a 3 tue), **ScrDockTieni** (tieni premuto: app della categoria con
  numeri e stella per la dock), **ScrDockStella** (le tue app con +), **BolComunica**, **BolComunicaApp**; pagina
  Comunicazione — **TelPillola** (pillola satinata Tastierino, Preferiti, Recenti, Rubrica + «+» a parte per annotare un
  numero). Ordine della dock come chiesto «da destra a sinistra»: Browser, Ricerca AI, Chat, Email, SMS, Drive, Mappe,
  Stella, poi Tutte le app; lente a destra fuori dalla dock.
- **Mappe: rifare l'interfaccia e la ricerca** (da fare, priorità del proprietario): «sa di vecchio», lontana dalle tavole
  (MapCerca, MapPercorso, MapGuida, MapEsplora, MapAttivita, MapLivelli, MapSegnala); la ricerca non trova facilmente i
  luoghi (es. Apice, BN). Piano proposto: 1) ricerca: capire se la regione era scaricata e come CoMaps ordina i risultati
  (comuni prima dei POI omonimi lontani, nomi senza accenti), ricerca anche nelle regioni non scaricate con un servizio OSM
  online facoltativo (Photon/Nominatim, dal centro privacy come il meteo, spento di base) che propone di scaricare la
  regione; 2) interfaccia: schermata iniziale, ricerca, scheda del luogo e percorso rifatti sulle tavole (Compose sopra il
  motore di CoMaps, al posto delle schermate di CoMaps), poi la guida.
- **Email** (12 sera): Proton/Tuta restano fuori (IMAP solo con Bridge sul computer): messaggio spiegato. Infomaniak
  (kMail) «Password non accettata» = verifica in due passaggi → serve la password per le app: messaggio che lo spiega.
  Da fare: se il dominio è personale, guardare l'MX (mx.infomaniak.ch → server di Infomaniak); **lettura di un messaggio
  con la pillola satinata in basso** (Rispondi, Inoltra, Archivia, Sposta in…, Elimina, Spam) come nelle altre app.
- **Password**: il campo di ricerca va in basso (vicino al pollice), come nelle altre app. Da fare.
- **Ultimo APK: giro 336** in `apk-prova`. Cambiati rispetto al 331: app-debug (Scrivania), browser, password, mail,
  EuriseTastiera. Uguali: gli altri (Mappe resta la 15).
- **Prossima chat**: prova del giro 336; approvazione delle tavole nuove (Browser fila 5, Scrivania fila 12, TelPillola) e
  loro codice; pillola della lettura in Email; ricerca in basso in Password; Mappe (piano sopra) come lavoro principale.
- **Meteo** (fatto, giro 338): in cima il tempo disegnato nello stile degli sfondi (sereno, poco nuvoloso, nuvoloso,
  nebbia, pioviggine, pioggia, rovesci, neve, temporale; giorno e notte) — `feature/weather/SkyScene.kt`; toccando un giorno
  della settimana si apre il dettaglio (`DayScreen`: ora per ora, fasce di pioggia, raffiche, alba e tramonto).
- **AI e Voce: oggi non servono a niente di concreto** (proprietario, 12 sera). Proposta da discutere (prima la tavola):
  una sola **Assistente** (tavola Assistente già sulla pagina App) che si apre tenendo premuto il tasto home o dalla dock
  («Ricerca AI»), con voce o testo, e che *fa* le cose: «chiama Anna», «nota: …», «appuntamento giovedì alle 10 con Marco
  dal commercialista», «portami ad Apice», «manda a Marco su WhatsApp che arrivo», «cerca …», «apri …», «scrivi una lettera
  a …». Come: 1) parole → testo sul telefono (riconoscimento più rapido e corretto: modello per i comandi brevi in streaming,
  Whisper solo per le dettature lunghe); 2) testo → azione con una grammatica italiana dei comandi più comuni (subito, senza
  modello) e il modello AI sul telefono solo per le richieste libere, che sceglie tra le azioni della suite (funzioni); 3)
  una scheda di conferma prima di agire (chi, cosa, quando), un tocco per eseguire; 4) le azioni passano dai contratti della
  suite (Calendario, Note, Attività, Mappe, Telefono, Messaggi, Browser, Office). Voce e AI non restano app a parte: diventano
  l'Assistente (e la dettatura nella Tastiera).
- **Ultimo APK: giro 338** in `apk-prova`. Cambiato rispetto al 336: solo app-debug (Meteo).

## 12 novembre, notte: tavole approvate dal proprietario (con le sue correzioni)
- **Browser, fila 5: APPROVATE** BrwNuovaSchedaPro, BrwIndirizzo, BrwSegnalibri, BrwEstensioni. Correzione: nel popup dei
  segnalibri l'elenco **scorre** quando i segnalibri sono più di quelli che entrano.
- **Scrivania, fila 12: APPROVATE** ScrDock, ScrDockTieni, ScrDockStella, BolComunica, BolComunicaApp. Correzione per
  «Tutte le app»: le **categorie stanno in colonna sul lato destro** (sinistro per i mancini); **scorrendo il dito sulle
  categorie**, a sinistra compaiono le app della categoria su cui si trova il dito. Al tocco sul pulsante della dock si apre
  per prima **la categoria aperta più spesso**. Il resto come nelle tavole (le tre categorie in più scelte dall'utente
  restano). La tavola ScrDock va aggiornata con questa disposizione prima del codice, oppure si segue questa descrizione.
- **Telefono, TelPillola: APPROVATA** con due correzioni: resta **la carrellata dei preferiti in alto** com'era (tondi con
  le iniziali, sopra le sezioni); la **bolla «Nota della chiamata»** che compare quando arriva una chiamata deve avere **lo
  stesso stile e gli stessi colori della schermata di prima di rispondere**.
- **Assistente: APPROVATA l'idea** (AI + Voce che eseguono comandi, con scheda di conferma, vedi sopra). Prossimo passo: la
  tavola dell'Assistente, poi il codice.
- Ordine di lavoro proposto per la prossima chat: 1) codice delle tavole approvate (Browser, Scrivania, Telefono) con le
  correzioni; 2) pillola nella lettura delle Email e ricerca in basso in Password; 3) tavola dell'Assistente; 4) Mappe
  (ricerca e interfaccia, piano sopra). A ogni consegna: elenco degli APK cambiati e di quelli uguali.

## 12 novembre, notte: tavole approvate in codice, pillole, ricerca di Mappe, tavola dell'Assistente · giro 342, Mappe 16
Fatto (ramo `brand`), nell'ordine chiesto dal proprietario:
- **Browser, fila 5** (`browser/`): `NewTab.kt` (BrwNuovaSchedaPro: ricerca con «Chiedi all'AI», «Nella cronologia» e QR,
  che apre il lettore QR della suite; «Riprendi da dove eri» = ultima scheda chiusa e primo «Leggi dopo»; siti fissati con
  «Fissa» (apre i segnalibri in modo «fissa»; tieni premuto un sito per toglierlo); «Per oggi, dalla suite» dal nuovo
  `TodayLinksProvider` dell'app Eurise (`core/suite/TodayLinksContract`: impegni delle prossime 18 ore e cose da fare dei
  prossimi 3 giorni con un link, permesso della suite); scorciatoie Download, Password, Estensioni, Cronologia; tracciatori
  fermati nella settimana). Differenze dalla tavola: «Sul computer» e «Al computer» non ci sono finché manca la
  sincronizzazione; «Traduci» non è tra le scorciatoie della nuova scheda (non c'è una pagina da tradurre).
  Barra a pillola galleggiante: tocco sull'indirizzo = `AddressMenu.kt` (BrwIndirizzo; «Manda al computer» spento con la
  spiegazione), tieni premuto = scrivi; icona segnalibri = `BookmarksPopup.kt` (BrwSegnalibri, elenco che **scorre**,
  cartelle, ricerca, tieni premuto per scheda nuova/fissa/rinomina/sposta/togli). `Extensions.kt` + `ExtensionsUi.kt`
  (BrwEstensioni): Impostazioni › Estensioni, consigliate da AMO, scheda dei permessi prima di installare (GeckoView 141,
  `onInstallPromptRequest` a 4 argomenti), interruttore e rimozione; i file .xpi aperti da addons.mozilla.org si installano
  passando dalla stessa scheda. ZXing aggiunto al Browser per il QR dell'indirizzo.
- **Scrivania, fila 12** (`feature/launcher/home/Dock.kt`): sul telefono la dock sostituisce la barra delle categorie
  (sul tablet resta `TabletDock`). Tutte le app con la correzione (colonna di categorie a destra, a sinistra per i mancini,
  app che compaiono scorrendo il dito, apertura sulla categoria più aperta: conteggi in `DockPrefs`, preferenze «dock»);
  «Apri la pagina» porta alla pagina intera della categoria (riordino, cartelle). Stella = le tue app con «+». Compiti
  (Mappe, Drive, SMS, Email, Chat, Ricerca AI, Browser): app trovate per intent (geo, smsto, mailto, https) e per elenchi
  di pacchetti, la suite per prima; stella = app di riferimento. «Ti hanno cercato»: tondo del menu (`LocalCommsApps`) con
  le app per comunicare. La tavola ScrDock non è stata ridisegnata: si è seguita la descrizione della correzione.
- **Telefono** (TelPillola): `EuriseDock` con Tastierino (apre il tastierino), Preferiti (griglia: tocco chiama, tieni
  premuto apre), Recenti (con la carrellata dei preferiti in cima), Rubrica; «+» = contatto nuovo. Nota della chiamata con
  lo stile del riquadro «Prima di rispondere» (velo, titolo dorato, testo bianco).
- **Email**: lettura con la pillola satinata Rispondi, Inoltra (oggetto «I:», testo citato; gli allegati non si inoltrano
  ancora), Archivia, Sposta in… (cartelle IMAP), Elimina, Spam (cartella `\Junk` o per nome).
- **Password**: ricerca in basso (sempre visibile, si scrive subito); stato della sincronizzazione sotto il titolo.
- **Assistente**: tavola sulla tela, pagina AI, seconda fila (AstAscolto, AstConferma, AstMessaggio, AstLibera,
  AstImpostazioni) · **da approvare**; il codice parte dopo l'approvazione.
- **Mappe, punto 1 del piano (ricerca)**: `maps/android/…/eurise/EuriseOnlineSearch.java` + agganci in `eurise_patch.py`:
  la domanda va al motore senza la sigla della provincia («Apice, BN» → «Apice»); se le mappe scaricate non trovano
  nulla, scheda «non è nelle mappe del telefono» con «Cerca su OpenStreetMap» (Photon di komoot: solo le parole cercate,
  niente posizione; spiegazione la prima volta, si spegne dai risultati) e «Scarica mappe»; toccando un risultato la mappa
  va lì e, se la regione non è sul telefono, propone di scaricarla con il peso. Da fare: la voce nel centro privacy.
- **Mappe, punto 2 (interfaccia rifatta sulle tavole)**: non ancora iniziato. Serve Compose nel modulo app di CoMaps
  (oggi solo View) e schermate nuove al posto di quelle di CoMaps: home della mappa con la barra satinata, ricerca
  (MapCerca), scheda del luogo, percorso (MapPercorso), poi guida (MapGuida), livelli, segnala. Lavoro lungo: va fatto
  in una chat dedicata, una schermata per giro di Mappe.
- **Ultimo APK: giro 342** in `apk-prova`, Mappe 16. Cambiati rispetto al 338: app-debug (Scrivania, «Per oggi» per il
  Browser), browser, phone, mail, password, eurise-mappe. Uguali nel funzionamento (solo ricompilati per le icone nuove
  di `core/designsystem` e il contratto in `core/suite`): ai, chat, gallery, messages, read, voice, EuriseTastiera, Office.
- **Prossima chat**: prova del giro 342; approvazione della tavola dell'Assistente e suo codice; Mappe punto 2.

## 12 novembre, notte (fine chat): scelte del proprietario
- **Assistente: si chiama Alfred e parla come un maggiordomo inglese.** Tavole AstAscolto, AstConferma, AstMessaggio,
  AstLibera, AstImpostazioni approvate nel contenuto. Prima del codice vanno rifatti i testi della tavola nella voce di Alfred.
  Cosa vuol dire, come proposta da confermare nella prossima chat:
  - parla italiano (la lingua del telefono), con il registro di un maggiordomo inglese: dà del Lei, è cortese e
    asciutto, ha un filo di ironia britannica e non è mai servile né chiacchierone. Esempi: «Subito.», «Mi permetta di
    riepilogare prima di procedere.», «Ho preparato l'appuntamento: attende soltanto il suo assenso.», «Temo che
    WhatsApp non mi lasci inviare da qui: le apro la conversazione con il testo già scritto.», «Non ho trovato Apice
    tra le mappe del telefono. Desidera che cerchi altrove?»;
  - come si rivolge all'utente: col nome della propria scheda nella Rubrica, oppure «signore»/«signora» (scelta nelle
    impostazioni), oppure senza appellativo;
  - la voce di sintesi (quando legge le risposte) è una voce maschile pacata, scaricata a richiesta;
  - nome e icona: «Alfred» al posto di «Assistente» nella dock (Ricerca AI tenuta premuta), nel tasto home e nelle schede.
    La scheda di conferma resta sempre l'ultimo passo, prima di ogni azione.
- **Mappe, punto 2: interfaccia rifatta sulle tavole.** Il proprietario ha chiesto la spiegazione e il lavoro, nella
  prossima chat. Come si fa:
  1. Compose dentro Mappe: `eurise_patch.py` aggiunge al modulo `android/app` di CoMaps il plugin Kotlin e Compose,
     Compose BOM e Material 3; i nostri file Kotlin stanno in `maps/android/…/eurise/ui/` e si copiano sopra l'albero
     come gli altri. Il motore (disegno della mappa in C++, `Framework`, `SearchEngine`, `RoutingController`, `MapManager`)
     non si tocca: le schermate nuove lo chiamano come fanno quelle di CoMaps. Peso stimato: 2–3 MB in più nell'APK di
     Mappe, da spiegare nel commit.
  2. Le schermate nuove entrano in `ComposeView` dentro le attività e i frammenti di CoMaps (`MwmActivity`,
     `SearchFragment`, scheda del luogo), una alla volta; quelle vecchie restano finché la nuova non è provata.
  3. Ordine, un giro di Mappe per passo (circa 16 minuti ciascuno) e una prova sul telefono: a) schermata della mappa con la
     barra satinata in basso (ricerca, livelli, posizione, «+» per segnala) al posto dei pulsanti di CoMaps; b) ricerca
     (MapCerca, con «Dove devi andare» e la ricerca online già fatta); c) scheda del luogo; d) percorso (MapPercorso);
     e) guida (MapGuida); f) livelli (MapLivelli), segnala (MapSegnala), esplora (MapEsplora), attività (MapAttivita).
  4. Insieme al passo a): la voce «Ricerca su OpenStreetMap (Photon)» nel centro privacy.

## 12 novembre, sera: Alfred in codice, «Alfred ovunque» in tavola, Mappe passo a), Android Auto (ramo `brand`)
Fatto, nell'ordine chiesto dal proprietario:
- **Alfred (proposta confermata dal proprietario)**: parla italiano, dà del Lei, cortese e asciutto; ironia britannica **alla
  Groucho Marx e Woody Allen** (battute secche, paradossi, autoironia; quasi sempre sue, citazioni brevi e dichiarate),
  anche **per massime e detti**, una volta su tre dopo un'azione riuscita, mai nelle conferme pratiche né alla guida.
  Appellativo: **di base il nome che scrive l'utente** (la prima volta Alfred lo chiede, con il nome della Rubrica come
  suggerimento); opzioni signore, signora, nessuno. Icona: **papillon** morbido e arrotondato (`EuriseIcons.bowtie`),
  **animato** nell'app (si aggiusta all'arrivo, ondeggia mentre pensa, inchino quando ha fatto: `BowTie.kt`).
- Tavole AstAscolto, AstConferma, AstMessaggio, AstLibera, AstImpostazioni rifatte nella voce di Alfred (pagina AI,
  seconda fila); testi approvati con le correzioni sopra.
- **Codice** (`feature/assistant`, nuovo modulo, nessuna libreria nuova):
  - `Grammar.kt` + `GrammarTest.kt`: chiama, nota, ricordami/da fare, appuntamento (anche «dentista martedì alle 9»),
    portami, manda/scrivi a … (su WhatsApp/SMS/Telegram/Signal) che …, sveglia, timer, cerca (web o suite), apri;
    il resto è richiesta libera. Date e ore da `QuickAdd`.
  - `Plan.kt` (`Butler`): scheda di conferma con righe da correggere (tocco sulla matita: testo o scelta), persone dalla
    Rubrica (preferiti prima, alternative come pillole), calendari scrivibili, azioni **solo dopo il tocco**: evento
    (`CalendarRepository.save`), nota, attività, chiamata (permesso CALL_PHONE chiesto al momento, altrimenti
    tastierino), Mappe (`geo:` verso Eurise Mappe), sveglia e timer (Orologio della suite), WhatsApp con il testo già
    scritto, SMS precompilato, Telegram/Signal con il testo negli appunti, ricerca della Scrivania o del web, app per
    nome. Richiesta libera: passa a Eurise AI se c'è (testo negli appunti), altrimenti cerca. Il modello AI che sceglie
    le azioni da sé è il passo successivo.
  - `Voice.kt`: dettatura con Eurise Voce (risultato col permesso della suite) o il riconoscimento del telefono;
    lettura ad alta voce con la sintesi del telefono, solo voci locali (la voce di Alfred da 60 MB arriverà dopo).
  - `AlfredPrefs` nel file `eurise_alfred`, quindi dentro il **backup cifrato**.
  - App: `alfred/AlfredActivity` (ASSIST e VOICE_COMMAND: si sceglie come «app di assistenza digitale» di Android
    per il tasto home tenuto premuto). **Niente VoiceInteractionService**: cambierebbe il riconoscimento vocale delle
    altre app. Scrivania: «Alfred» tra le app AI e prima scelta di Ricerca AI nella dock (`openAlfred`).
  - Centro privacy: permesso «Chiamate» (per Alfred).
- **Tavole nuove approvate** (pagina AI, terza fila «Alfred ovunque»): **AlfGiornata** (bottone «Il punto» col papillon in
  «I tuoi appuntamenti»: impegni, meteo, Borse, notizie Italia e mondo, una perla musicale del passato col suo commento,
  un'opera, un luogo, una curiosità; letto dalla sua voce, pausa e salta), **AlfSera** (il discorso cambia con l'ora),
  **AlfSveglia** (Alfred come opzione della sveglia: saluto col meteo, una frase, un brano scelto con gusto, italiano o
  internazionale, generi diversi, classica compresa, con due righe su brano e autori), **AlfMappe** (indicazioni nella
  sua voce, «una battuta ogni tanto» mai vicino a una svolta). Da fare in codice.
- **Musica di Alfred (deciso)**: catalogo suo sul telefono (titolo, autori, anno, genere, momento, commento verificato);
  riproduzione con l'intento standard «riproduci da ricerca» nell'app scelta dall'utente (Spotify, YouTube Music,
  Deezer, VLC…), prima i brani già sul telefono; **la scelta è dell'utente, separata per il punto della giornata e per
  la sveglia**. Sveglia con rete di sicurezza: se la musica non parte in pochi secondi, classica libera scaricata la
  sera prima (Musopen, Wikimedia Commons), poi la suoneria. Centro privacy: «l'app di musica riceve il brano».
- **Memoria di Alfred (proposta, da fare)**: il modello non si riaddestra sul telefono; Alfred impara con una memoria
  personale locale (chi è «Marco», canale preferito, calendario, abitudini, generi saltati), visibile e cancellabile
  dal centro privacy, nel backup cifrato.
- **Tastiera: prossimo lavoro principale (approvato)**: modello del tocco per tasto (come FUTO), correzioni con le
  lettere vicine e il dizionario italiano, suggerimenti dalle frasi abituali, dizionario personale e apprendimento nel
  backup (oggi il backup copre solo l'app Eurise: `BackupArchive` prende `eurise.db` e le preferenze `eurise_*`;
  EuriseTastiera è un APK a parte e va aggiunta passando dal permesso della suite).
- **Mappe, passo a) del piano** (in codice): Compose nel modulo app di CoMaps (`eurise_patch.py`: plugin del compilatore
  con la Kotlin di CoMaps 2.2.20, BOM 2024.12.01, solo ui e foundation; opzione `-Xskip-metadata-version-check` perché la
  BOM di CoMaps porta la libreria standard 2.4). `eurise/ui/EuriseMapBar.kt`: barra satinata (Cerca, Livelli, Posizione,
  Salvati, Menu) e «+» per Segnala al centro della mappa, dentro `map_buttons_bottom.xml` (verticale); pulsanti di CoMaps
  nascosti ma presenti (id). **Ricerca su OpenStreetMap (Photon) nel centro privacy**: servizio `MapSearch`,
  `PrivacyContract` + `PrivacyProvider` nell'app Eurise; Mappe chiede lo stato prima di ogni richiesta e registra i
  contatti.
- **Android Auto e orizzontale (richiesta del proprietario: Mappe si userà soprattutto in auto)**: tavole nuove sulla
  pagina «Mappe, Messaggi e Telefono», ultima fila: MapOraVerticale (passo a com'è), **MapOrizzontale** (barra in colonna
  a sinistra, schede a destra), **MapAutoGuida** e **MapAutoCerca** (in Android Auto la cornice è del sistema, nostri
  mappa, colori, testi, elenchi e voce). Da approvare; poi passo b) e il lavoro sullo schermo dell'auto (CoMaps ha già
  la parte Android Auto in `app/organicmaps/car`).
- Nota: in `eurise_patch.py` e nelle stringhe di Mappe c'erano già, prima di questo lavoro, un blocco Compose e i testi
  della barra (da un lavoro precedente non annotato qui): tenuto un solo blocco.
- **Ultimo APK: giro 350** in `apk-prova`, **Mappe 21**. Cambiati rispetto al 342: app-debug (Alfred, Scrivania con
  Alfred nella dock, centro privacy con Chiamate e ricerca OpenStreetMap), eurise-mappe (barra satinata, Photon nel
  centro privacy). Uguali nel funzionamento (ricompilati per il papillon in `core/designsystem` e il servizio nuovo in
  `core/suite`): browser, phone, mail, password, ai, chat, gallery, messages, read, voice, EuriseTastiera, Office.
- **Richieste del proprietario per Mappe (12 sera, da mettere nelle tavole)**: velocità istantanea e limite sempre in
  vista; conto alla rovescia in metri (c'è, anche in Auto e nella voce); alle diramazioni zoom automatico e riquadro
  grande con lo schema, la freccia e le corsie; segnalazioni rapide in guida; lettura dei cartelli (Android Auto non
  passa i dati della telecamera dell'auto: **deciso dal proprietario, limiti dalle mappe OSM**, niente fotocamera);
  traffico (nessuna fonte libera nazionale: ricerca sulle fonti aperte italiane, CCISS, concessionarie, Comuni).
- **Prossima chat / prossimi passi**: prova del giro 350 e di Mappe 21; approvazione delle tavole di Mappe (orizzontale,
  Android Auto) e tavole nuove (incrocio ingrandito, velocità e limite, segnalazioni); codice di «Alfred ovunque»
  (punto della giornata, sveglia, voce in Mappe, musica); tastiera (lavoro principale); correzioni del proprietario
  dopo la prova del giro 342 (non ancora ricevute).

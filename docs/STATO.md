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
  impegno, Codici e consegne, Farmaci, Timer e sveglie, Appunto al volo.
- Prossimo passo: provare l'APK del ramo `bolle` e unirlo a `main` (che contiene già Attività, sveglia, leggerezza e
  collegamenti); scegliere le bolle nuove; poi scheda Persona e + unico; anteprime di Drive, Orologio, Galleria,
  Password e Privacy; sincronizzazione CalDAV delle Attività con Murena.

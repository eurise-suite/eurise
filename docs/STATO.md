# Eurise – stato dei lavori (8 novembre 2026)

Nota di passaggio tra una chat e l'altra: chi riprende il lavoro legge prima questo file e `CLAUDE.md`.

## Riferimenti
- Documento di progetto: Claude Doc `81d73EgPHtgsSEd7YkavhU`.
- Tela di design "Icone della suite": https://claude.ai/artifact/AauRJ8e5WdH7nXTqAEXkoo
  (pagine: App e launcher, Icone, Calendario, Scrivania (launcher), Scrivania tablet, Note, Cloud).
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
  probabilmente il piano non include WebDAV. Da verificare il nome del piano nel Manager Infomaniak; se
  WebDAV non c'è, valutare l'API di kDrive (api.infomaniak.com, con accesso OAuth) al posto di WebDAV.

## Punti aperti
- Tablet: da provare sul Volla quando arriva (pagina "Scrivania tablet" sulla tela).
- Oroscopo: nuova fonte per segno (oroscopi.info); da confermare sul telefono che il testo compaia.
- Fonti non ancora verificate sul telefono: Borsa (Yahoo), alcuni feed di notizie e ricette, video dei canali.
- kDrive da sistemare (vedi Cloud).
- Prossimo passo: Attività (completare l'app, poi sincronizzazione CalDAV con Murena), sempre partendo dalle
  anteprime sulla tela; poi le altre app nell'ordine del documento di progetto.

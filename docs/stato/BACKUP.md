# Backup della suite – stato

Area avviata il 20 novembre 2026 (progetto «Eurise Beckup»). Obiettivo: **un solo backup**, semplice, che salva tutta la
suite e si ripristina senza sorprese su un telefono nuovo. Regole in `CLAUDE.md` («Dati e backup») e `docs/SICUREZZA.md`
(«Crittografia», «Backup di Android»).

### Per ripartire
- Ramo `prova-backup` (da `brand`). Prima di ogni push: fetch di `origin/brand`; dato che il ramo è già pubblicato e il
  push forzato è vietato, `brand` si porta dentro con un merge (il rebase riscriverebbe i commit già spinti).
- Fase 1 (cosa entra) decisa il 20 novembre; fase 2 (tavole approvate, schermate vere) consegnata il 21 col giro 909.
  Fase 3 (prova vera sull'emulatore) superata il 21 novembre, 17 su 17; poi tutta la suite (tranne Mappe) nel backup,
  30 su 30. Il 21 sera: **Mappe nel backup** (fornitore nelle patch di CoMaps) e **tavole** della cartella protetta
  «dal backup» in Galleria (in attesa di approvazione). **Prossimo**: controllo del proprietario sul telefono; con le tavole
  approvate, il codice della Galleria (vedi «Mappe e cartella dal backup» in fondo).

## Cosa c'è già (al 20 novembre)
- `core/cloud/backup`: `BackupArchive` (zip in memoria), `BackupCrypto` (AES-256-GCM, chiave dei dati chiusa con frase e
  codice di recupero, PBKDF2 600 000; i file a 310 000 si aprono ancora), `BackupManager` + `BackupWorker` (WorkManager,
  Wi-Fi e carica), `Retention` (tre recenti + mensile + trimestrale, i vecchi si toccano solo dopo la verifica),
  destinazioni WebDAV e cartella (SAF), ripristino «in attesa» applicato in `EuriseApplication.onCreate` prima del database.
- Nel backup oggi: `eurise.db`, preferenze `eurise_*` (tranne `eurise_feeds_*`) e `clock`, `eurise_secrets` (chiave di
  recupero della Chat), chiave delle note bloccate (`LockKeys`), Tastiera (`apps/keyboard.zip` tramite il suo
  `BackupProvider`, protetto dal permesso della suite).
- Ripristino dalla prima schermata: passo «Ripristina» nella preparazione della Scrivania, finché non ci sono note né attività.

## Cose trovate da correggere (prima del lavoro nuovo) — 1, 2, 3 e 5 fatte il 20 novembre, sera
1. **Il ripristino sovrascrive senza conferma**: frase giusta → i dati attuali vengono sostituiti al riavvio. Serve una
   conferma che dica cosa si sostituisce (e, se ci sono già dati, cosa si perde).
2. **Tastiera: zip in chiaro lasciato nella cache** (`eurise-backup-export.zip` non si cancella dopo la lettura; anche
   `eurise-backup-import.zip` resta se `apply` non arriva). Da passare a un tubo (`createPipe`): niente file.
3. **Zip della Tastiera in attesa** (`keyboard_restore.zip` nei file di Eurise) resta in chiaro finché la Tastiera non
   c'è, anche per sempre. Da tenere cifrato (chiave nel Keystore) e da cancellare dopo un tempo o su richiesta.
4. La copia del database (`backup-snapshot.db` nella cache) è in chiaro per pochi secondi: stessi dati del database, si
   cancella subito; si può evitare leggendo lo snapshot direttamente nello zip in memoria (da valutare: VACUUM INTO
   vuole un file).
5. Il manifesto dello zip (`format: 1`) non dice quali app ci sono: al ripristino non si può mostrare «cosa contiene».

## Proposta: cosa entra, app per app (da decidere con il proprietario)
Principio: entra ciò che è **tuo e non si può riscaricare**; restano fuori le cose che si riscaricano (mappe, modelli,
voci), le cache e ciò che è legato al Keystore di questo telefono (sul nuovo si ricollega con due tocchi).
Ogni app separata consegna i suoi dati con un fornitore protetto dal permesso della suite, come la Tastiera.

| App | Entra (proposta) | Resta fuori | Da decidere |
|---|---|---|---|
| **Password** | `cassaforte.kdbx` così com'è (già cifrata con la frase principale: doppio lucchetto); alias, browser abbinati, sincronizzazione | impronta (`password_bio`, chiave del Keystore: si riattiva), controllo violazioni (cache) | entra? Al ripristino, se sul telefono c'è già una cassaforte: mai sovrascrivere, si tengono tutte e due |
| **Galleria** | diari di viaggio e note dei diari, nomi dei luoghi, impostazioni | foto e video (hanno la loro sincronizzazione), miniature, stato dei caricamenti | **cartella protetta**: oggi non va da nessuna parte, se si perde il telefono si perde. Entrarci pesa quanto le foto. I diari puntano a indirizzi MediaStore che sul telefono nuovo cambiano: si ricollegano per nome e data |
| **Posta** | caselle (server, nomi, firme, impostazioni) | posta (sta sul server), password delle caselle (chiuse nel Keystore: sul nuovo si reinseriscono o si rifà l'accesso) | va bene richiedere l'accesso alle caselle? |
| **Messaggi** | impostazioni, risposte rapide | — | **gli SMS e MMS stanno nel database di Android, non nostro**: salvarli (Messaggi deve essere l'app SMS predefinita per riscriverli) o lasciarli fuori? MMS con allegati pesano |
| **Telefono** | note delle chiamate, filtro dello spam | — | registro chiamate e numeri bloccati (database di Android): dentro o fuori? |
| **Chat** | impostazioni, abbinamenti nomi/sigle, turni | sessione e chiavi del dispositivo (`crypto/`): copiarle su un altro telefono ne farebbe un doppione dello stesso dispositivo, cosa che Matrix sconsiglia | sul nuovo si accede come nuovo dispositivo e la **chiave di recupero** (già nel backup) riapre la cronologia cifrata. Va bene così? |
| **Leggi** | impostazioni | lingue del riconoscimento testo e modelli di traduzione (si riscaricano) | — |
| **Voce** | voce scelta, velocità | voci e modelli (si riscaricano, con un avviso al ripristino) | — |
| **AI** | impostazioni | modello (`.gguf`, gigabyte: si riscarica) | — |
| **Office** | documenti creati in Office (`documenti/`), rapporti, recenti, impostazioni | file aperti da altre cartelle (restano dove sono) | i documenti possono pesare: tetto? |
| **Mappe** | segnalibri e tracce, impostazioni Eurise (viaggio, guida) | mappe scaricate (si riscaricano), prezzi dei carburanti | — |
| **Browser** | segnalibri, «Leggi dopo», note dei siti, impostazioni, schede aperte | cookie e accessi ai siti (le password stanno in Password), cache, icone | **cronologia**: dentro (comoda) o fuori (più riservata)? |
| **Tastiera** | già fatto: parole imparate, dizionario personale, modo di toccare | appunti (cronologia degli appunti) | — |

## Decisioni del proprietario (20 novembre 2026)
- **Galleria**: la cartella protetta entra **sempre** nel backup (resta cifrata con la sua chiave anche dentro).
- **Messaggi e Telefono**: **tutto dentro**: SMS, MMS con allegati, registro chiamate, numeri bloccati. Al ripristino
  si riscrivono quando Messaggi è l'app SMS predefinita e Telefono quella delle chiamate (si chiede in quel momento).
- **Browser**: la **cronologia entra**.
- **Password**: la cassaforte **entra** così com'è (doppio lucchetto). Al ripristino, se sul telefono ce n'è già una,
  non si sostituisce: si tengono tutte e due e si chiede quale usare o se unirle.
- Posta (accesso da rifare), Chat (nuovo dispositivo + chiave di recupero), Office (documenti dentro): approvate.
- **Frase nuova con lo sblocco del telefono**: finché si ha il telefono, frase e codice di recupero nuovi si fanno
  confermando con impronta o codice di sblocco, senza la frase vecchia (la chiave dei dati è già nel Keystore).
- **Promemoria di prova una volta l'anno**: il telefono chiede di scrivere la frase solo per controllarla; se non la si
  ricorda, la si cambia subito.
- Se si perdono frase e codice, il backup non si apre: nessuna porta di servizio (deciso, spiegato al proprietario).

## Anteprime (fase 2, 20 novembre 2026)
Tela «Icone della suite», pagina **Backup** (https://claude.ai/artifact/AauRJ8e5WdH7nXTqAEXkoo#page-backup), in attesa di
approvazione. Generate da uno script (stile Perla, come le tavole del Cloud).
- Fila Backup: `BakPrima` (spento, cosa salva app per app, «Fai il backup ora» sotto il pollice), `BakDove` (destinazioni
  con le dieci tacchette, servizi USA per ultimi con avviso), `BakFrase`, `BakCodice` (codice su carta, si controlla un
  gruppo), `BakStato` (fatto e verificato, frequenza, i cinque conservati), `BakPromemoria` (una volta l'anno),
  `BakNuovaFrase` (con l'impronta; subito un backup nuovo, i vecchi si aprono con frase e codice vecchi).
- Fila Ripristino: `RipPrimo` (prima schermata del telefono nuovo), `RipScegli` (dove sta, quale backup), `RipFrase`
  (frase o codice), `RipConferma` (cosa rimetto, app per app, interruttori; avviso su ciò che si sostituisce; la
  cassaforte si aggiunge accanto), `RipDopo` (passi che restano, uno per app; i dati aspettano cifrati).

## Dove si trova il backup (20 novembre 2026)
Prima era solo in Eurise › File › menu laterale › «Collega un cloud» › Cloud › «Backup cifrato» (più il passo «Ritrova i
tuoi dati» nella preparazione della Scrivania e «Passa a un telefono nuovo» nel Cestino). Il proprietario non lo trovava.
Aggiunti, su sua richiesta (senza tavola):
- **Icona «Backup» nel cassetto** (`activity-alias .BackupApp`, sezione `backup`), disegno nuovo «la freccia che fa il
  giro e torna, con la stella» in Ottanio profondo (`scripts/icone_disegni.json`, generato da `icone.py` e
  `icone_kotlin.py`; vale anche nei temi di icone della Scrivania).
- **Scorciatoia «Backup» sull'icona di Eurise** (pressione lunga, `BackupShortcut`, dinamica perché il pacchetto di
  prova ha un nome diverso).
- **Avviso sulla Scrivania** quando il backup automatico è fermo da almeno 3 giorni oltre la frequenza scelta
  (`BackupHealth`, con test; data di accensione in `configured_at`): pillola «Ultimo backup N giorni fa · Guarda» in
  cima alla home del telefono, apre Cloud › Backup. Sul tablet non ancora.
- Consegnato con il giro **893**; correzioni e icona nel cassetto della Scrivania con il giro **905** (Eurise e Tastiera).

## Correzioni del 20 novembre, sera
- **Conferma prima di sostituire**: `BackupManager.open/openBytes` aprono e verificano il backup solo in memoria
  (`Opened`: contenuto, cosa c'è sul telefono, zip, chiavi); niente cambia finché `apply` dopo la conferma. Finestra
  «Cosa rimetto» (`ConfirmDialog`): data, dati di Eurise con i conteggi, impostazioni, Tastiera, note bloccate; avviso
  «Sostituisce quello che c'è ora» con note e attività presenti, o «non si perde niente» se il telefono è vuoto; se il
  telefono aveva già un backup acceso, avvisa che userà frase e codice del backup ripristinato. «Sostituisci e riavvia».
- **Manifesto formato 2**: conteggi di note (fuori dal cestino) e attività. I backup di formato 1 si aprono come prima
  (la finestra non mostra i numeri). Le versioni vecchie dell'app ignorano il manifesto.
- **Tastiera senza file in chiaro**: `BackupProvider` esporta e riceve lo zip in un tubo (`createPipe`); lo zip ricevuto
  resta in memoria fino ad `apply` (che aspetta la fine della ricezione); i file vecchi nella cache si cancellano.
  Compatibile con le versioni precedenti dell'app Eurise (stesse chiamate).
- **Zip della Tastiera in attesa sigillato**: `LocalSeal` (AES-256-GCM, chiave `eurise-backup-waiting` nel Keystore) in
  `keyboard_restore.sealed`; al ripristino va lì direttamente, senza passare dalla cartella d'attesa in chiaro; il
  vecchio `keyboard_restore.zip` si sigilla e si cancella al primo avvio.
- **Avviso sulla Scrivania anche sul tablet**, sopra l'orologio.
- **Icona «Backup» nel cassetto della Scrivania** (`eurise:backup` in `Apps.euriseSections`): il cassetto della
  Scrivania non mostra le icone di Android dell'app Eurise (né l'alias `.BackupApp` né Eurise stessa), elenca a mano le
  sezioni. Il proprietario non le vedeva col giro 893. La scorciatoia sulla pressione lunga vale solo negli altri
  launcher, perché nella Scrivania non c'è un'icona di Eurise.
- Da provare dal vero (fase 3, emulatore): backup con la Tastiera installata, ripristino senza Tastiera, poi Tastiera.

## Schermate vere (21 novembre 2026, approvate le tavole)
`feature/cloud/BackupScreen.kt` riscritto a pagine intere (`BackupPage`: titolo in alto, comandi in basso sotto il
pollice), al posto delle finestre:
- **Spento**: cosa salva davvero oggi (Eurise, Tastiera, note bloccate) e «In arrivo» per le altre app; «Fai il backup
  ora» e sopra «Ho già un backup: ripristina».
- **Accendere**: Dove (cloud collegati ordinati per tacchette con `PrivacyMeter`, chiavetta o cartella 10, «Collega un
  cloud» che apre Cloud; sotto, la spiegazione «Prima di accendere» richiesta dalla regola della privacy) → Frase (dieci
  tacchette di robustezza, `PhraseStrength` con test, almeno 10 caratteri) → Codice (sei gruppi, si riscrive un gruppo
  scelto a caso; niente copia negli appunti, che finirebbe nella cronologia della Tastiera) → backup subito.
- **Acceso**: «Fatto e verificato», frequenza, conservati (tocco → ripristino), Dove con tacchette, Frase segreta,
  ripristino da altrove, spegnimento.
- **Frase nuova con lo sblocco** (`BiometricPrompt` con impronta o codice dal livello 30, `KeyguardManager` prima):
  frase e codice nuovi per la stessa chiave dei dati (`BackupCrypto.rewrapAll`, `BackupManager.resetSecrets`), poi
  backup subito. Senza blocco schermo serve la frase attuale o il codice (finestra di prima).
- **Promemoria annuale**: `BackupState.checkDue()` (365 giorni da `checked_at`, o dall'accensione), `verify()`; riga
  nella pagina e avviso sulla Scrivania «Ti ricordi la frase del backup? · Prova» (sezione `backup_check`).
- **Ripristino**: Dove sta → Quale backup → Aprilo (linguette Frase / Codice di recupero) → Cosa rimetto →
  «Sostituisci e riavvia». Dalla Scrivania di un telefono vuoto con il backup spento: «Telefono nuovo? Ritrova i tuoi
  dati · Ripristina» (sezione `backup_restore`, rotta `cloud/backup?start=restore`).
- **Bentornato**: `applyPending` segna `restored_at`; avviso sulla Scrivania e scheda in cima alla pagina (Eurise al
  suo posto, parole della Tastiera in attesa se non c'è, backup automatico, «In arrivo»), finché si tocca «Ho visto».
- L'avviso della Scrivania è uno solo alla volta: telefono nuovo → bentornato → backup fermo → promemoria annuale.
- Consegnato con il giro **909** (solo l'APK principale). Non ancora provato sul telefono né sull'emulatore.
- Fatto il 21 novembre: prova vera sull'emulatore (fase 3), poi Password e le altre app (vedi sotto).

## Prova vera sull'emulatore (fase 3, 21 novembre 2026)
Flusso `.github/workflows/prova-backup.yml` (parte con il push dei suoi file sul ramo; prende l'ultimo giro riuscito di
Compila APK su `prova-backup`, o quello scritto in `.github/prova-backup-giro`) e script
`.github/scripts/prova-backup-emulatore.sh`. Una sonda (`.github/prova-backup/sonda`, instrumentation in Java compilata
con javac e d8, firmata con la chiave di prova delle app, nessuna libreria) gira dentro Eurise e usa il codice vero.
Esito col giro 921 (run 44887973106): **17 controlli su 17**.
- Backup: 3 note, 2 attività, un'impostazione e una parola della Tastiera entrano; file `EURISEB1`, «Prova backup»
  non si legge nel file; nessuno zip in chiaro nei file di Eurise né nella cache della Tastiera.
- Telefono nuovo (`pm clear` di Eurise, Tastiera disinstallata): frase sbagliata rifiutata; il codice di recupero
  scritto minuscolo e a gruppi apre, come la frase; il telefono risulta vuoto; le parole della Tastiera aspettano
  sigillate (non uno zip), non nella cartella d'attesa in chiaro; note, attività e impostazione tornano; «Bentornato»
  segnato; la cartella d'attesa sparisce.
- Tastiera reinstallata: le parole in attesa le arrivano dal tubo e la parola di prova torna al suo posto.
- **Non coperto** dalla prova: le schermate (si guardano sul telefono), l'invio a WebDAV o a una cartella, la regola
  dei cinque conservati sui file veri, il lavoro pianificato, le chiavi nel Keystore di `BackupManager` e il riavvio.

## Tutta la suite nel backup (21 novembre 2026, pomeriggio)
**Formato a pezzi** (`BackupCrypto` versione 2, `BackupStreamTest`): stessi file `EURISEB1`, dati in pezzi da 1 MiB
cifrati e autenticati uno per uno (nonce = 8 byte dell'intestazione + numero; AAD = intestazione + numero + «ultimo»):
un file troncato, toccato o allungato non si apre. Il backup si cifra mentre lo zip si scrive (`BackupArchive.create`
su un flusso), in un file di cache che contiene solo dati cifrati; parte, si riscarica in un altro file e si riapre
tutto prima di toccare i vecchi. I file di versione 1 si aprono come prima. Ripristino: il file (cifrato) in cache,
`contents` lo legge tutto (così è verificato), `apply` lo riapre e prepara. Destinazioni a file (`write`, `readTo`).

**App separate** (`core/suite/SuiteBackupProvider`, `core/cloud/backup/SuiteApps`): ogni app ha un fornitore
`<pacchetto>.eurise.backup`, `exported` con `readPermission`/`writePermission` = permesso della suite e controllo di chi
chiama anche in `openFile` e `call`; lo zip esce ed entra in un tubo; `summary` (riga per «Cosa rimetto»), `ready`
(se ora può rimettere), `apply` (aspetta la fine). Eurise controlla che il fornitore sia della suite
(`SuiteAccess.providerTrusted`) prima di chiedere o mandare; `<queries>` per gli indirizzi in `core/cloud`. Al
ripristino ogni zip aspetta sigillato (`LocalSeal.sealTo`, a pezzi, chiave del Keystore) in `apps_waiting/` e si
consegna appena l'app c'è ed è pronta: subito dopo il ripristino e a ogni ritorno alla Scrivania. Manifesto formato 3
con le app e le loro righe (`apps`, `missing` se un'app non ha risposto).

| App | Nel backup | Al ripristino |
|---|---|---|
| Password | `cassaforte.kdbx` così com'è, alias, browser abbinati | se c'è già una cassaforte non si tocca: quella del backup va accanto (`cassaforte-dal-backup.kdbx`) e Password propone di unirla (percorso «Importa da KeePass», con la frase principale del backup) |
| Messaggi | SMS (`sms.json`), MMS con indirizzi e pezzi, impostazioni | solo quando Messaggi è l'app SMS predefinita (`ready`); niente doppioni (numero, ora, testo; MMS per data) |
| Telefono | registro chiamate, numeri bloccati, note delle chiamate | solo quando Telefono è l'app delle chiamate e ha il permesso del registro; niente doppioni |
| Galleria | cartella protetta (file già cifrati, senza `bio.json`), diari con nome/data/peso di ogni foto, nomi dei luoghi, impostazioni scelte | cartella vuota o la stessa: si riempie; un'altra con foto: va accanto in `secure-dal-backup` (da aprire: non ancora nell'interfaccia); diari ricollegati alle foto per nome e data |
| Tastiera | come prima (il suo `BackupProvider`) | come prima |
| Browser | segnalibri, cronologia, Leggi dopo, note dei siti, schede, impostazioni | si unisce: liste per indirizzo, note per sito, cronologia del backup prima; schede solo se non ce ne sono |
| Office | `documenti/`, rapporti, impostazioni (fuori i «recenti») | un documento diverso con lo stesso nome arriva come «nome (dal backup)» |
| Posta | caselle **senza password**, impostazioni | le caselle aspettano «da ricollegare»; il foglio «Collega» le propone già scritte, con i loro server: basta la password |
| Chat | impostazioni, persone, turni (fuori sessione, chiavi del dispositivo, accesso allo studio) | si accede come dispositivo nuovo; la chiave di recupero è nel backup di Eurise |
| Voce, AI | impostazioni (voci e modelli si riscaricano) | — |
| Leggi | niente di suo da salvare (lingue e modelli si riscaricano) | — |
| Mappe | segnalibri e tracce (i `.kml` di `bookmarks/`), `settings.ini`, impostazioni `eurise_*`, persone del viaggio condiviso | elenchi che mancano arrivano; uguali si saltano; diversi accanto come «nome (dal backup).kml»; poi Mappe riparte |

**Prova sull'emulatore** col giro 933 (run 44922024981): **30 controlli su 30**. In più rispetto alla prima: formato a
pezzi; cassaforte, foto protetta, casella di posta e documento piantati, salvati, cancellati e tornati; la casella torna
«da ricollegare» senza la sua password chiusa; Messaggi (non predefinita sull'emulatore) resta in attesa sigillata;
la shell (senza permesso della suite) non legge `export` di nessuna delle sette app né chiama `apply`.
Non provato sull'emulatore: SMS/MMS e registro chiamate veri (servono l'app predefinita e messaggi veri), il contenuto
del Browser (l'APK di prova non è «debuggable»: solo esporta e riceve), l'unione della cassaforte in Password (serve la
frase principale), Chat, Voce, AI (solo impostazioni), le schermate, l'invio a un cloud o a una cartella.

Consegnato con il giro **938** (`apk-prova`): Eurise, Password, Messaggi, Telefono, Galleria, Posta, Chat, Voce, AI,
Browser, Office. La prova sull'emulatore è ripetuta sul giro 938.

**Limiti noti**
- Galleria: se sul telefono nuovo c'è già un'altra cartella protetta con foto, quella del backup va in
  `secure-dal-backup` ma la Galleria non la mostra ancora.
- Posta: la password di ogni casella va riscritta (sta nel Keystore del telefono vecchio).
- Chat: si accede come dispositivo nuovo; la cronologia cifrata torna con la chiave di recupero.

## Formato (proposta)
- Stesso file `EURISEB1` e stessa cifratura; nello zip il manifesto passa a `format: 2` con l'elenco delle app e la loro
  versione dei dati; ogni app separata in `apps/<app>.zip`. I file `format: 1` si ripristinano come oggi.
- Al ripristino, l'app che non è ancora installata riceve i suoi dati quando arriva (come la Tastiera), ma lo zip in
  attesa resta **cifrato** con una chiave del Keystore.

## Mappe e cartella dal backup (21 novembre 2026, sera)
**Mappe** (`maps/android/.../eurise/EuriseBackupProvider.java`, Java perché Mappe si compila dentro CoMaps; logica dei file
in `BookmarkFiles.java` con `BookmarkFilesTest`, lanciato da `maps-engine.yml`). Stesso accordo di `SuiteBackupProvider`
(export/import in un tubo, `summary` «N elenchi, M luoghi e tracce», `ready` sempre vero, `apply` che aspetta e poi
chiude Mappe perché ricarichi i segnalibri). Indirizzo `eu.eurise.maps.eurise.backup` in tutte e due le varianti
(Mappe ha lo stesso pacchetto): la variante la decide il permesso di firma (`SuiteApps.App.samePackage`).
- Dentro: i `.kml` di `files/bookmarks` (CoMaps carica solo quelli; tracce registrate comprese), `settings.ini`, le chiavi
  `eurise_*` delle preferenze, `eurise_trip.recent`. Fuori: mappe, pietre miliari, prezzi, cestino (`.Trash`), segnalazioni
  in guida (due ore), interruttore della ricerca online (lo tiene il centro privacy).
- Percorsi verificati sul commit fissato di CoMaps (mirror `github.com/comaps/comaps`): `GetBookmarksDirectory()` =
  `SettingsDir()/bookmarks`, `getSettingsPath` = `getFilesDir()`. `eurise_patch.py` si ferma se cambiano.
- **Backup di Android spento anche in Mappe**: CoMaps mandava `bookmarks/` e `settings.ini` nel backup di Google e nel
  trasferimento tra telefoni (`allowBackup="true"`); ora `allowBackup="false"` e `eurise_no_backup.xml`, come le altre app.
- Non provato sull'emulatore (Mappe è solo arm64 e si compila a parte): da provare sul telefono.
- Consegnato con il giro **946** e **Mappe 125** (`apk-prova`, 21 novembre sera); la prova sull'emulatore sul giro 946
  è tutta a posto (Mappe non c'è sull'emulatore: il backup la salta).

**Galleria, cartella «dal backup»**: tavole sulla tela, pagina Backup, terza fila (in attesa di approvazione):
`GalDalBackup` (scheda «Arrivata dal backup · Apri» in cima alla cartella protetta), `GalDalBackupApri` (frase o codice
della cartella del telefono di prima, non del backup), `GalDalBackupUnisci` (aperta: «Sposta nella mia cartella», ogni foto
ricifrata con la chiave attuale, riletta e solo dopo tolta; oppure «Tienila a parte»). Codice dopo l'approvazione.

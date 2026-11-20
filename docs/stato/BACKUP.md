# Backup della suite – stato

Area avviata il 20 novembre 2026 (progetto «Eurise Beckup»). Obiettivo: **un solo backup**, semplice, che salva tutta la
suite e si ripristina senza sorprese su un telefono nuovo. Regole in `CLAUDE.md` («Dati e backup») e `docs/SICUREZZA.md`
(«Crittografia», «Backup di Android»).

### Per ripartire
- Ramo `prova-backup` (da `brand`). Prima di ogni push: fetch di `origin/brand`; dato che il ramo è già pubblicato e il
  push forzato è vietato, `brand` si porta dentro con un merge (il rebase riscriverebbe i commit già spinti).
- Fase 1 (cosa entra) decisa il 20 novembre. **Fase 2: anteprime sulla tela di design**, poi codice, poi prova vera (fase 3).

## Cosa c'è già (al 20 novembre)
- `core/cloud/backup`: `BackupArchive` (zip in memoria), `BackupCrypto` (AES-256-GCM, chiave dei dati chiusa con frase e
  codice di recupero, PBKDF2 600 000; i file a 310 000 si aprono ancora), `BackupManager` + `BackupWorker` (WorkManager,
  Wi-Fi e carica), `Retention` (tre recenti + mensile + trimestrale, i vecchi si toccano solo dopo la verifica),
  destinazioni WebDAV e cartella (SAF), ripristino «in attesa» applicato in `EuriseApplication.onCreate` prima del database.
- Nel backup oggi: `eurise.db`, preferenze `eurise_*` (tranne `eurise_feeds_*`) e `clock`, `eurise_secrets` (chiave di
  recupero della Chat), chiave delle note bloccate (`LockKeys`), Tastiera (`apps/keyboard.zip` tramite il suo
  `BackupProvider`, protetto dal permesso della suite).
- Ripristino dalla prima schermata: passo «Ripristina» nella preparazione della Scrivania, finché non ci sono note né attività.

## Cose trovate da correggere (prima del lavoro nuovo)
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

## Formato (proposta)
- Stesso file `EURISEB1` e stessa cifratura; nello zip il manifesto passa a `format: 2` con l'elenco delle app e la loro
  versione dei dati; ogni app separata in `apps/<app>.zip`. I file `format: 1` si ripristinano come oggi.
- Al ripristino, l'app che non è ancora installata riceve i suoi dati quando arriva (come la Tastiera), ma lo zip in
  attesa resta **cifrato** con una chiave del Keystore.

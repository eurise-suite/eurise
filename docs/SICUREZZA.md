# Sicurezza della suite

Da 18 novembre 2026 sera il lavoro continua nel progetto **«Eurise Sicurezza»** (chat dedicata), che tiene questo file
come suo stato. Fatto: punto 1 (chiave di prova, fasi 1 e 2 installate dal proprietario), punto 2 in parte (strumenti),
punto 3 (GeckoView 160); il 18 novembre, notte, punti 5 e 6 (porte d'ingresso, crittografia, backup della Tastiera) nel ramo
**`prova-sicurezza`**, prova sull'emulatore verde, **giro 842 in `apk-prova`** (Office e Mappe non toccati in quel giro:
vengono dai loro flussi). Punto 4 avviato (azioni allo SHA, Dependabot, OSV-Scanner, impronte «lenient») e punto 7 in
parte (`SECURITY.md`, `docs/MINACCE.md`). Prossimo: lanciare `librerie-verificate.yml` e passare a «strict»; permessi
minimi nei flussi; Ponte con chiavi effimere; punto 8. Rami di lavoro rimasti, già uniti o superati:
`sicurezza` (prova della firma sull'emulatore), `strumenti`, `strumenti-2`, `prova-avvio`.

## Strumenti di prova
- `.github/workflows/prova-firma.yml` + `.github/scripts/prova-firma-emulatore.sh`: firma e permessi sull'emulatore
  (girano sul ramo `sicurezza`). Modello per provare dal vero ogni correzione sui permessi.
- `.github/workflows/prova-avvio-browser.yml`: avvio del Browser sull'emulatore (ramo `prova-avvio` o a mano).
- `.github/workflows/prova-porte.yml` + `.github/scripts/prova-porte-emulatore.sh` + `.github/prova-porte/intrusa/`:
  porte d'ingresso sull'emulatore (parte dopo ogni Compila riuscito sul ramo `prova-sicurezza`, o a mano con l'id del giro).
  Un'app «curiosa» (codice vero, chiave sua, permesso della suite dichiarato da sé come normale) bussa a segreti, accessi
  di Password, SMS, ripristino della Tastiera, impostazioni, codici 2FA e Ponte; un'app «amica» (stessa chiave delle app)
  controlla che la suite parli ancora con sé stessa. Prima con le app di `apk-prova` (si vedono i buchi), poi con quelle
  del ramo (devono essere chiusi).

La privacy senza sicurezza non vale nulla: questo file raccoglie lo stato e il piano (avviato il 18 novembre 2026).

## Chiave di prova (18 novembre 2026)
- Problema: la chiave che firmava gli APK di prova (`app/debug-key/eurise-debug.keystore`, password «android») è nel
  repository pubblico. Le app della suite si fidano tra loro con permessi «di firma» (`eu.eurise.permission.SUITE_DEBUG`):
  chiunque poteva firmare un'app con quella chiave e chiedere dati a Password, Contatti, Ponte. Provato sull'emulatore:
  un'app firmata solo con la vecchia chiave otteneva il permesso.
- Soluzione: rotazione della chiave (APK Signature Scheme v3, lineage), senza disinstallare e senza perdere dati.
  `.github/scripts/firma-prova.sh` rifirma ogni APK di prova al momento della pubblicazione (`apk-prova.yml`,
  `office-app.yml`, `office-prova.yml`) con la chiave nuova (EC P-256, alias `eurise-prova`, segreto
  `EURISE_PROVA_KEYSTORE`; impronta SHA-256 del certificato `96d2cf60…5e55e2`). Rotazione valida da Android 9.
- Due fasi (`EURISE_PROVA_FASE`, di base nello script): Android rifiuta l'aggiornamento se la vecchia chiave perde i
  permessi nello stesso passo (INSTALL_FAILED_DUPLICATE_PERMISSION).
  - Fase 1: chiave nuova; la vecchia conserva i permessi solo per il passaggio. Il proprietario installa sopra tutte le app.
  - Fase 2: la vecchia chiave conserva solo «essere aggiornata». Un'app firmata solo con lei non ottiene più il permesso
    della suite e non può rimpiazzare le nostre.
- Prova sull'emulatore (API 34): `.github/workflows/prova-firma.yml` (ramo `sicurezza`), tutta verde il 18 novembre:
  aggiornamento nelle due fasi con il dato di Password intatto, intrusa che chiede il permesso → `granted=false`,
  APK con la sola vecchia chiave rifiutato, app con la chiave nuova con il permesso.
- La chiave delle versioni ufficiali resta solo al proprietario e non è toccata.

## Piano
1. Chiave di prova fuori dal repository (sopra). Fase 1 pubblicata nel giro 808 e installata dal proprietario su tutte le
   app il 18 novembre; **fase 2** di base nello script dallo stesso giorno.
2. Strumenti aggiornati (18 novembre, giro 828 tutto verde): AGP 9.5.0, Gradle 9.6.0, Kotlin 2.3.20, KSP 2.3.12,
   Room 2.7.2, compileSdk 37, Compose BOM 2026.08.00. Restano: le altre librerie (AndroidX, OkHttp 5, CameraX, Media3,
   PDFBox…), il passaggio al Kotlin di AGP e al DSL nuovo, la Tastiera (progetto Gradle a parte).
3. GeckoView: dalla 141 (agosto 2025) alla **160** (16 novembre 2026); da ora sempre l'ultima uscita a ogni giro.
4. Aggiornamenti automatici, vulnerabilità note a ogni giro (OSV-Scanner), verifica delle librerie di Gradle
   (`verification-metadata.xml`), azioni di GitHub fissate allo SHA. **Avviato il 18 novembre, notte** (sotto).
5. Revisione delle porte d'ingresso: componenti esportati e fornitori di dati, crittografia di Password, backup e Ponte.
   **Fatta il 18 novembre, notte** (sotto); restano le voci «Aperto» in fondo alla sezione.
6. Correzioni già note: `allowBackup="true"` nella Tastiera. **Fatta il 18 novembre, notte**, con le regole di estrazione
   complete in tutte le app (sotto).
7. `SECURITY.md` (come segnalare una falla), modello delle minacce, revisione esterna prima della 1.0 (NLnet / NGI).
   **`SECURITY.md` e `docs/MINACCE.md` scritti il 18 novembre, notte.** Passo del proprietario: accendere in GitHub
   Settings → Code security «Private vulnerability reporting», «Dependabot alerts» e «Dependabot security updates» (da qui
   non si può: serve l'amministratore).
8. Computer: Ponte con accoppiamento cifrato e autenticato, estensione Firefox con i soli permessi necessari, Flatpak
   su runtime aggiornato.

## Porte d'ingresso (18 novembre 2026, notte)
Contati uno per uno i **90 componenti esportati** dei manifesti (inventario: attività, alias, servizi, ricevitori, fornitori)
e letti i fornitori di dati, i servizi della suite e ogni punto in cui un'app della suite manda dati a un'altra. Regola
nuova in un posto solo: `core/suite/.../SuiteAccess.kt` (per la Tastiera, progetto a parte, `keyboard/.../SuiteTrust.kt`).

| Trovato | Rischio reale? | Come è chiuso |
|---|---|---|
| Nove fornitori (`SecretsProvider`, `LoginsProvider` di Password, Aspetto, Luoghi, Privacy, Destinazioni, Oggi, I tuoi giorni, Meteo sul percorso) e `Uscite` del motore di Office accettavano nel codice **sia** `SUITE` **sia** `SUITE_DEBUG`. | **No, per fortuna** (difetto di difesa, non porta aperta). Quei fornitori hanno `android:permission` nel manifesto e Android, quando un'app apre un fornitore, la respinge se non ha quel permesso, anche per `call()`. Provato sull'emulatore: anche con le app di prima l'app curiosa (con `SUITE` dichiarato da sé) è respinta con «Permission Denial». Sarebbe diventato sfruttabile togliendo il permesso dal manifesto. | Solo il permesso della propria variante anche nel codice (`SuiteAccess.callerAllowed`). |
| Le app mandavano dati a un altro pacchetto o fornitore della suite senza controllare chi c'è dietro (chiave di recupero della Chat ai segreti, password salvate dal Browser e da Posta, messaggi dei pazienti dello Studio alla Chat, audio da trascrivere a Voce, testo da tradurre a Leggi, testo della Tastiera, domande ad AI, SMS da mandare). | **Sì, medio**: solo se l'app destinataria non è installata un'altra può prendere lo stesso nome di pacchetto o indirizzo e ricevere tutto. | Prima di chiamare, legarsi o aprire: il destinatario deve avere il nostro permesso di firma (`SuiteAccess.call`, `isSuitePackage`; `SuiteTrust` nella Tastiera). Leggi ora chiede anche per sé il permesso che dichiarava. |
| `PonteActivity` (esportata) accettava `PONTE_PAIR` da chiunque: collegava subito il «computer» del codice ricevuto; il codice a sei cifre compariva solo dopo il salvataggio. | **Sì, alto**. Un'app sul telefono si collegava in silenzio come computer (appunti, avvisi di chiamate e messaggi, note, attività, appuntamenti, richieste di password con l'impronta). | L'azione passa da un alias non esportato (`.PontePair`) raggiungibile solo dal lettore QR della stessa app; da fuori si ignora. |
| Ripristino della Tastiera (`BackupProvider.call("apply")`) senza controllo. | Basso: rimetteva solo un file già scritto dalla suite, ma chiunque poteva riavviare la Tastiera. | Controllo del permesso in `call`. |
| `CodesProvider` di Password: lettura protetta, scrittura no. | Nessuno oggi (scrittura vuota), ma fragile. | `android:permission` su tutto il fornitore. |
| Chat: il server dell'accesso restava salvato per sempre; un `eurisechat://login?loginToken=…` di un'altra app poteva far entrare la Chat in un altro account giorni dopo. | **Sì, medio-basso** (servono un'app installata e un token dell'attaccante). | Vale dieci minuti e una volta sola. |
| «Condividi» verso Posta, Messaggi e Modifica della Galleria con un `content://` o `file://` che punta ai dati privati dell'app stessa. | **Sì, basso**: la Posta o un MMS potevano allegare i propri file (account) a un messaggio che l'utente poi manda; la Galleria salvare fuori una copia dell'area protetta. | `SuiteAccess.isForeignUri`: i file propri si scartano. |
| I cinque fornitori «Persone» (Posta, Messaggi, Telefono, Chat) e lo Studio, esportati senza permesso nel manifesto: controllano già il permesso giusto in `call()`. Gli altri componenti (circa 70): avvio, condivisione, apertura di file, servizi di sistema (`BIND_*`, `BROADCAST_SMS`…), ricevitori di avvio, importazioni con conferma (calendario, contatti, Password). | No: protetti dal sistema, con conferma dell'utente o senza dati in uscita. | Restano come sono. |

Prova sull'emulatore: `prova-porte.yml`, **verde il 18 novembre, notte** (giro 839 di Compila). Prima (giro di `brand`):
la curiosa si collegava al Ponte come computer (`ponte=APERTA`), respinta altrove. Dopo: respinta ovunque, Ponte chiuso;
l'amica con la chiave della suite usa ancora segreti, Password, SMS, ripristino della Tastiera e codici 2FA.

Aperto (rischio basso, deciso di non toccarlo in questo giro):
- Intent verso l'app Eurise senza controllo del destinatario: «Salva in» e «Apri da» di File, Scanner, Carte (`FilesContract`
  e simili); un intruso servirebbe solo con l'app Eurise disinstallata. Da portare su `SuiteAccess.trusted`.
- Mappe (patch di Organic Maps) usa `SUITE_DEBUG` anche fuori dalle prove: da sistemare prima della prima versione ufficiale.
- «Condividi → Note» e «Invia al computer» non chiedono conferma (da Android 10 un'app deve essere in primo piano per aprirli).
- I visori (PDF, Galleria, Office, import di calendario e contatti) aprono anche file propri: servono all'app stessa e non
  fanno uscire nulla.

## Crittografia (18 novembre 2026, notte)
- **Password** (KDBX 4, compatibile KeePassXC): Argon2id 64 MiB, 3 passaggi, AES-256-CBC; intestazione con SHA-256 e
  HMAC-SHA-256, ogni blocco con HMAC verificato **prima** di decifrare; seme e vettore nuovi a ogni salvataggio; flusso
  interno ChaCha20; XML senza DTD. Argon2, BLAKE2b e ChaCha20 sono scritti a mano ma provati con i vettori ufficiali
  (RFC 9106, BLAKE2b «abc») e con un file di KeePassXC (`KdbxTest`). Impronta: chiave AES-GCM nel Keystore, autorizzata dal
  dito a ogni uso, annullata se si aggiunge un'impronta. Chiusura dopo 2 minuti fuori dall'app, schermate protette
  (`FLAG_SECURE`). Corretto: un file con iterazioni o parallelismo fuori misura ora si rifiuta (bloccava l'app).
- **Backup della suite**: AES-256-GCM con l'intestazione autenticata; chiave dei dati chiusa due volte (frase e codice di
  recupero di 24 simboli, circa 119 bit); sul telefono la chiave sta sigillata nel Keystore. Corretto: PBKDF2-HMAC-SHA256
  da 310 000 a **600 000** iterazioni per i backup nuovi (soglia OWASP 2023; i vecchi si aprono con le loro); un file con
  un numero di iterazioni assurdo non si prova.
- **Ponte**: ECDH P-256 tra chiavi fisse, HKDF-SHA256 con il segreto del QR, AES-256-GCM, mittente nei dati autenticati,
  difesa dalle ripetizioni; l'abbinamento richiede la prova di aver letto il QR. Corretto: la chiave privata del telefono
  stava **in chiaro** nelle preferenze; ora è sigillata nel Keystore (`priv_k`), migrata alla prima lettura senza rifare
  l'abbinamento. **Segretezza in avanti (19 novembre)**: sulla rete di casa ogni collegamento usa una chiave di sessione
  (v2, `Upgrade` in `core/ponte`): il telefono manda una chiave effimera nel saluto, il computer la sua nel primo
  messaggio (cifrato con la chiave fissa, quindi autenticato), la sessione mescola ECDH effimero ed ECDH fisso con il
  segreto del QR. Chi un giorno ruba le chiavi fisse non legge il traffico già registrato. Compatibile: con un lato
  vecchio si resta in v1; il telefono ricorda i computer già passati in v2 e con loro rifiuta di tornare indietro (chi
  togliesse la chiave effimera dal saluto in chiaro). Test in `PonteTest`. Aperto: la cassetta nel cloud (file
  asincroni, senza sessione) usa ancora la chiave fissa; ECDH dentro il Keystore da Android 12.
- **Segreti affidati all'app Eurise** (`eurise_secrets`, chiave di recupero della Chat): in chiaro nelle preferenze, ma
  entrano nel backup cifrato; sigillarli nel Keystore richiede di cambiare anche il backup (aperto).

### Telefono rubato
- Spento o mai sbloccato dopo l'accensione: tutto è nella memoria cifrata di Android (FBE), illeggibile senza il codice.
- Bloccato ma sbloccato almeno una volta: senza una falla di Android i dati delle app restano chiusi. Con una falla (o
  root): la cassaforte resta cifrata con la frase principale (Argon2id: dipende dalla sua forza); le chiavi del Keystore
  non escono dal telefono; prima di questo giro la chiave del Ponte si poteva copiare, ora no.
- Sbloccato in mano a qualcuno: Password chiede frase o dito dopo 2 minuti; le altre app sono aperte come per il
  proprietario. Il backup cifrato e la chiusura delle sessioni da un altro dispositivo sono la via di uscita.

## Backup di Android (18 novembre 2026, notte)
- Tastiera: `allowBackup="true"` → `false`. Rischio reale sui telefoni con i servizi Google: le parole imparate (la storia
  di ciò che si scrive) e il dizionario personale finivano nel backup di Google Drive.
- Da Android 12 `allowBackup="false"` non ferma il trasferimento da telefono a telefono. Tutte le app hanno ora le regole
  di estrazione (`eurise_no_backup.xml`; nell'APK principale `data_extraction_rules.xml`) che escludono tutto, anche la
  memoria protetta del dispositivo (`device_*`), dove la Tastiera tiene i suoi dati. I dati passano solo dal backup cifrato
  della suite.

## Catena di compilazione e librerie (18 novembre 2026, notte)
- **Azioni di GitHub fissate allo SHA** in tutti i flussi (13 azioni, commento con la versione accanto): un'azione
  manomessa a monte non entra più nei nostri giri. Dependabot le aggiorna (SHA nuovi) ogni lunedì.
- **Dependabot** (`.github/dependabot.yml`): azioni, Gradle della suite e della Tastiera, proposte raggruppate sul ramo
  `brand`; GeckoView esclusa (regola «sempre l'ultima» del Browser). Scelto al posto di Renovate perché non richiede di
  installare un'app esterna con accesso al repository.
- Primo giro di OSV-Scanner (18 novembre, notte): **16 vulnerabilità note** nelle librerie C incluse: 14 in Tesseract
  5.5.1 (OCR di Leggi e dello Scanner: le immagini possono arrivare da fuori), una in protobuf-lite dentro SentencePiece
  (traduzione). Tesseract portato a **5.5.4** e Leptonica a **1.87.0** (ultime uscite); SentencePiece/protobuf-lite
  aperto (copia di browsermt/slimt, da aggiornare a parte): eccezione in `osv-scanner.toml` fino al 31 dicembre 2026
  (legge solo i modelli di traduzione scaricati in HTTPS da Mozilla). Rischio reale: medio (serve un'immagine preparata che l'utente
  passi all'OCR).
- **OSV-Scanner** (`vulnerabilita.yml`, v2.7.1 con impronta verificata): a ogni push su `brand` e `prova-sicurezza` e ogni
  lunedì; legge le impronte Gradle e riconosce le librerie C incluse in `third_party` (llama.cpp, whisper.cpp, tesseract,
  leptonica, sentencepiece…). Una vulnerabilità nota fa diventare rosso il giro.
- **Impronte scritte il 19 novembre**: 645 componenti della suite e 620 della Tastiera (1 040 e 1 009 impronte SHA-256).
  Ancora «lenient». Primo giro di Compila con le impronte: suite pulita; nella Tastiera manca solo
  `junit-bom-5.11.0-M2.module` (c'è il `.pom`). Prima di «strict» vanno coperti anche i flussi che compilano altrove
  (Office, Mappe, schermate), per non fermare gli altri progetti dopo l'unione in `brand`.
- **Librerie di esecuzione**: 522 nelle app della suite e nel computer, 281 nella Tastiera (`gradle.lockfile` sotto
  `gradle/eurise/esecuzione/`). Primo controllo: **BouncyCastle 1.72** (tramite `pdfbox-android`, 12 vulnerabilità note,
  due critiche) e **Guava 31.1 e 33.3.1** (3) arrivano davvero nelle app: il `build.gradle.kts` principale ora impone
  almeno Guava 33.7.2-android e sostituisce BouncyCastle jdk15to18 con **jdk18on 1.85** (la jdk15to18 1.85 ha una classe
  in due archivi e Android si ferma; in Office si escludono le licenze doppie). Netty e il resto degli strumenti non ci
  arrivano. Office in `apk-prova` arriva dal suo flusso: avrà la correzione dopo l'unione in `brand`.
- **Impronte delle librerie** (`librerie-verificate.yml`, a mano o al cambio del suo file sul ramo di lavoro): compila tutto scrivendo
  `gradle/verification-metadata.xml` (e quello della Tastiera) e lo spinge sul ramo. Per ora
  `org.gradle.dependency.verification=lenient` (segnala senza fermare: altri progetti compilano sullo stesso ramo); a file
  completo e giri puliti si passa a `strict`.
- **Cosa blocca e cosa no** (19 novembre): con le impronte OSV-Scanner vede anche gli strumenti di compilazione (37
  pacchetti con vulnerabilità note: Netty, BouncyCastle, jose4j, jdom2, Guava, httpclient… tutti di AGP, Kotlin e lint, che
  girano solo su GitHub e non arrivano sugli APK; `org.json` solo nei test). Il giro diventa rosso solo per le librerie
  che finiscono nelle app (`gradle/eurise/esecuzione/gradle.lockfile` e quello della Tastiera, scritti da
  `librerie-verificate.yml` con lo script `gradle/eurise/librerie-esecuzione.gradle`) e per le librerie C incluse; gli
  strumenti vanno in un rapporto a parte nel riepilogo del giro e si aggiornano con Dependabot.
- Rete: nessun traffico in chiaro (target SDK 35, nessuna eccezione); lettori XML senza DTD (Password, WebDAV, Office).

## Impostazioni di GitHub e permessi dei flussi (19 novembre 2026)
- Accesi dal proprietario (Settings → Code security): segnalazione privata delle falle, avvisi e aggiornamenti di
  sicurezza di Dependabot, Secret Protection con blocco dei push che contengono segreti, Copilot Autofix. Consigliati in
  più: Malware alerts, Grouped security updates, Automatic dependency submission. Lasciati spenti: AI Scan e Agentic
  autofix (a crediti, in prova, solo su `main`) e la configurazione automatica di CodeQL (solo `main`).
- **CodeQL** (`codeql.yml`, configurazione avanzata): Kotlin/Java compilando le app, flussi di GitHub, estensione per
  Firefox, Python, con le regole `security-extended`; su `brand` e `prova-sicurezza` e ogni mercoledì. `third_party`
  resta a OSV-Scanner.
- **Permessi minimi**: `build.yml` ora legge soltanto e scrive solo il lavoro `release` (tag `v*`); prima tutti i lavori,
  anche quelli lanciati da una richiesta di modifica, avevano la scrittura sul repository. In tutti i flussi che non
  spingono commit la copia del codice non conserva il token (`persist-credentials: false`): uno script o un plugin di
  compilazione manomesso non lo trova.
- Da sapere: il ramo principale del repository è `main`, quello vivo `brand`. Gli aggiornamenti di sicurezza automatici di
  Dependabot aprono le proposte su `main`; quelli ordinari (`dependabot.yml`) su `brand`. Da decidere se rendere `brand`
  il ramo principale.

### Primo giro di CodeQL (19 novembre 2026): 64 segnalazioni, una per una
Il riepilogo di ogni giro sta nel registro del lavoro «Riepilogo» di `codeql.yml` (gli avvisi in Security → Code scanning).
- **Corrette**: `postMessage` dell'editor di Office verso «*» (una pagina estranea che incornicia l'editor poteva leggere i
  messaggi; ora solo all'origine di chi lo ospita); WebView della stampa della chiave di recupero della Chat senza
  JavaScript, file, contenuti né rete.
- **Falsi allarmi, verificati nel codice**: zip slip in backup, suonerie e voci (nomi già filtrati: elenco chiuso, solo il
  nome del file, percorso canonico); PendingIntent impliciti del Ponte (immutabili, solo link http/https, nomi dei file
  ripuliti); «dati sensibili» negli intent per chiamare, scrivere SMS e mail (li sceglie l'utente); WebView di Posta,
  finestre web e AI (file e contenuti già chiusi, JavaScript solo dove serve, nessun ponte JavaScript); preferenze «in
  chiaro» (solo impostazioni e identificativi, nessun segreto); algoritmi «deboli» imposti dai formati (SHA-1 di TOTP RFC
  6238 e del controllo k-anonimo delle password violate, AES di KeePass e Cryptomator); backup dell'Orologio (manifesto di
  libreria, fuso con quello dell'APK principale che lo vieta); un ciclo nel parser MMS di AOSP (codice di altri).
- **Aperti, da progetto**: (1) blocco delle note e conferma in Password: l'impronta apre la porta ma non è legata alla
  chiave (le note sono già cifrate con una chiave sigillata nel Keystore); legarla richiede di cambiare come il backup
  porta con sé quella chiave. (2) Certificate pinning: oggi TLS standard verso i servizi pubblici; per i download di
  modelli, voci e motori la difesa migliore è l'impronta SHA-256 attesa scritta nel codice, da aggiungere download per
  download.

## Contenuti scaricati (19 novembre 2026)
Nessun download controllava ciò che arrivava; Whisper, cervelli di Alfred e lingue dell'OCR si prendevano dal ramo `main`
di Hugging Face e di GitHub (che può cambiare) e finiscono in lettori scritti in C (whisper.cpp, llama.cpp, Tesseract).
**Rischio reale, medio**: un file cambiato a monte (account compromesso) o per strada poteva far eseguire codice nell'app.
- Ora ogni file è fissato a un commit preciso con l'impronta SHA-256 e la lunghezza attese, in un posto solo
  (`core/suite/.../Contents.kt`): Voce (2 modelli Whisper), Alfred (2 cervelli GGUF, niente più richiesta all'API di
  Hugging Face per scegliere il file), Leggi e Scanner (ita, eng). Il file si usa solo se l'impronta corrisponde;
  altrimenti si butta. Il controllo dei cervelli (1–2 GB) gira fuori dal thread dell'interfaccia.
- Le impronte di Hugging Face le legge `impronte-contenuti.yml` (da qui Hugging Face non si raggiunge); quelle di GitHub
  si calcolano dal repository. Aggiornare un modello = cambiare commit e impronta insieme.
- Già a posto: il motore di Office (firma dell'APK confrontata con quella di Office prima di installarlo).
- Aperti, rischio basso: voci e suonerie vengono dalle nostre pre-release (`voci`, `suoni`, rigenerate dai nostri flussi:
  le impronte vanno scritte dagli stessi flussi); i modelli di traduzione si scelgono dall'elenco di Mozilla (HTTPS, nessuna
  impronta nell'elenco); sui telefoni con Android 10 o precedente il cervello scaricato sta nella memoria esterna
  dell'app, che un'app con il permesso di memoria può cambiare tra il controllo e la copia in Eurise AI.

## Chiavi nel chip separato e regole del repository (20 novembre 2026)
- **StrongBox**: le chiavi dello sblocco con l'impronta di Password e della cartella protetta della Galleria nascono nel
  chip di sicurezza separato quando il telefono lo ha (Titan M e simili), altrimenti nell'area protetta come prima
  (`core/suite/.../SecureKeys.kt`). Vale per le chiavi nuove (alla prossima accensione dello sblocco con l'impronta);
  quelle già create restano dove sono, nulla da rifare.
- **Rami protetti** (creata dal proprietario il 20 novembre, verificata via API): regola «Rami principali protetti»,
  attiva su `brand` e `main`, vieta cancellazione e push forzato; nessuna eccezione. Niente controlli obbligatori né
  richieste di modifica: le chat spingono direttamente su `brand`. Da rivedere quando ci saranno versioni ufficiali
  (ramo delle uscite con controlli obbligatori).
- **Voci e suonerie** (pre-release `voci` e `suoni`): rischio accettato. Chi potesse cambiarle avrebbe già la scrittura sul
  repository; un'impronta nel codice obbligherebbe a riscriverlo a ogni rigenerazione.

## Impronte delle librerie: come funzionano adesso (20 novembre 2026)
- **Copertura**: il flusso `librerie-verificate.yml` compila come tutti i lavori di Compila (anche Leggi, AI e Browser con
  le loro variabili, le versioni release, GeckoView x86_64, il computer e la Tastiera). Prima mancavano GeckoView e le
  librerie di Leggi e AI: non erano né verificate né lette da OSV-Scanner. Ora le librerie di esecuzione sono 549 (280
  nella Tastiera).
- **Prova a cache vuota**: dopo aver scritto le impronte il flusso svuota la cache, riscarica tutto con la verifica
  bloccante e aggiunge da solo le impronte dei file che Gradle chiede solo verificando (`.github/scripts/impronte_mancanti.py`,
  da Maven Central). Ultimo esito: suite, computer e Tastiera OK.
- **Indicatori dei plugin** (`*.gradle.plugin`) esclusi: Maven Central e Gradle Plugin Portal li servono diversi; il plugin
  vero è verificato. Senza questa esclusione, con la cache vuota, anche «lenient» fermava la compilazione (successo il 20
  novembre sul lavoro del computer; la verifica è stata spenta per qualche ora su `brand` per non bloccare nessuno).
- **Dove blocca**: su `prova-sicurezza`, in Compila e CodeQL (`GRADLE_OPTS=-Dorg.gradle.dependency.verification=strict`).
  Su `brand` e negli altri flussi è «lenient»: un passo «Impronte delle librerie» in ogni lavoro mostra in evidenza
  (avviso e riepilogo) ogni file che non corrisponde. Motivo: le impronte di una libreria nuova arrivano dopo il primo giro
  di chi la aggiunge (il flusso parte da solo quando cambiano i file delle librerie e poi rilancia Compila), e GeckoView
  cambia versione da sé a ogni uscita di Mozilla.
- **Firme PGP degli autori** (20 novembre): il flusso scrive `pgp,sha256` ed esporta le chiavi in
  `gradle/verification-keyring.keys` (45 chiavi; Tastiera 47). Dove l'autore firma, Gradle controlla la firma oltre
  all'impronta: GeckoView con la chiave «Mozilla Software Releases» (sottochiave 678E455D76767AA3), Kotlin con quella di
  JetBrains, AndroidX con quella di Google, BouncyCastle con la sua. Chi sostituisse un file sul repository Maven dovrebbe
  anche avere la chiave privata dell'autore. Le chiavi si leggono dal file nel repository: i server di chiavi servono
  solo al flusso quando arriva un autore nuovo. Prova a cache vuota in modalità stretta: suite, computer e Tastiera OK.


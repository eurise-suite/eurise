# Sicurezza della suite

Da 18 novembre 2026 sera il lavoro continua nel progetto **«Eurise Sicurezza»** (chat dedicata), che tiene questo file
come suo stato. Fatto: punto 1 (chiave di prova, fasi 1 e 2 installate dal proprietario), punto 2 in parte (strumenti),
punto 3 (GeckoView 160); il 18 novembre, notte, punti 5 e 6 (porte d'ingresso, crittografia, backup della Tastiera) nel ramo
**`prova-sicurezza`**, da compilare e provare con `prova-porte.yml` prima di arrivare al telefono. Prossimo: punto 4
(Renovate, OSV-Scanner, verifica delle librerie, azioni allo SHA), poi 7 e 8. Rami di lavoro rimasti, già uniti o superati:
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
4. Aggiornamenti automatici (Renovate), vulnerabilità note a ogni giro (OSV-Scanner), verifica delle librerie di Gradle
   (`verification-metadata.xml`), azioni di GitHub fissate allo SHA.
5. Revisione delle porte d'ingresso: componenti esportati e fornitori di dati, crittografia di Password, backup e Ponte.
   **Fatta il 18 novembre, notte** (sotto); restano le voci «Aperto» in fondo alla sezione.
6. Correzioni già note: `allowBackup="true"` nella Tastiera. **Fatta il 18 novembre, notte**, con le regole di estrazione
   complete in tutte le app (sotto).
7. `SECURITY.md` (come segnalare una falla), modello delle minacce, revisione esterna prima della 1.0 (NLnet / NGI).
8. Computer: Ponte con accoppiamento cifrato e autenticato, estensione Firefox con i soli permessi necessari, Flatpak
   su runtime aggiornato.

## Porte d'ingresso (18 novembre 2026, notte)
Contati uno per uno i **90 componenti esportati** dei manifesti (inventario: attività, alias, servizi, ricevitori, fornitori)
e letti i fornitori di dati, i servizi della suite e ogni punto in cui un'app della suite manda dati a un'altra. Regola
nuova in un posto solo: `core/suite/.../SuiteAccess.kt` (per la Tastiera, progetto a parte, `keyboard/.../SuiteTrust.kt`).

| Trovato | Rischio reale? | Come è chiuso |
|---|---|---|
| Nove fornitori (`SecretsProvider`, `LoginsProvider` di Password, Aspetto, Luoghi, Privacy, Destinazioni, Oggi, I tuoi giorni, Meteo sul percorso) e `Uscite` del motore di Office accettavano **sia** `SUITE` **sia** `SUITE_DEBUG`. | **Sì, grave**. Con le versioni ufficiali nessuna app della suite dichiara `SUITE_DEBUG`: un'app qualunque lo dichiara da sé come permesso normale, lo ottiene senza chiedere e legge tutte le password dei siti (a cassaforte aperta), la chiave di recupero della Chat, i luoghi, e salva accessi nuovi. Sul telefono con le sole app di prova vale lo stesso con `SUITE`. | Solo il permesso della propria variante (`SuiteAccess.callerAllowed`). |
| Le app mandavano dati a un altro pacchetto o fornitore della suite senza controllare chi c'è dietro (chiave di recupero della Chat ai segreti, password salvate dal Browser e da Posta, messaggi dei pazienti dello Studio alla Chat, audio da trascrivere a Voce, testo da tradurre a Leggi, testo della Tastiera, domande ad AI, SMS da mandare). | **Sì, medio**: solo se l'app destinataria non è installata un'altra può prendere lo stesso nome di pacchetto o indirizzo e ricevere tutto. | Prima di chiamare, legarsi o aprire: il destinatario deve avere il nostro permesso di firma (`SuiteAccess.call`, `isSuitePackage`; `SuiteTrust` nella Tastiera). Leggi ora chiede anche per sé il permesso che dichiarava. |
| `PonteActivity` (esportata) accettava `PONTE_PAIR` da chiunque: collegava subito il «computer» del codice ricevuto; il codice a sei cifre compariva solo dopo il salvataggio. | **Sì, alto**. Un'app sul telefono si collegava in silenzio come computer (appunti, avvisi di chiamate e messaggi, note, attività, appuntamenti, richieste di password con l'impronta). | L'azione passa da un alias non esportato (`.PontePair`) raggiungibile solo dal lettore QR della stessa app; da fuori si ignora. |
| Ripristino della Tastiera (`BackupProvider.call("apply")`) senza controllo. | Basso: rimetteva solo un file già scritto dalla suite, ma chiunque poteva riavviare la Tastiera. | Controllo del permesso in `call`. |
| `CodesProvider` di Password: lettura protetta, scrittura no. | Nessuno oggi (scrittura vuota), ma fragile. | `android:permission` su tutto il fornitore. |
| Chat: il server dell'accesso restava salvato per sempre; un `eurisechat://login?loginToken=…` di un'altra app poteva far entrare la Chat in un altro account giorni dopo. | **Sì, medio-basso** (servono un'app installata e un token dell'attaccante). | Vale dieci minuti e una volta sola. |
| «Condividi» verso Posta, Messaggi e Modifica della Galleria con un `content://` o `file://` che punta ai dati privati dell'app stessa. | **Sì, basso**: la Posta o un MMS potevano allegare i propri file (account) a un messaggio che l'utente poi manda; la Galleria salvare fuori una copia dell'area protetta. | `SuiteAccess.isForeignUri`: i file propri si scartano. |
| I cinque fornitori «Persone» (Posta, Messaggi, Telefono, Chat) e lo Studio, esportati senza permesso nel manifesto: controllano già il permesso giusto in `call()`. Gli altri componenti (circa 70): avvio, condivisione, apertura di file, servizi di sistema (`BIND_*`, `BROADCAST_SMS`…), ricevitori di avvio, importazioni con conferma (calendario, contatti, Password). | No: protetti dal sistema, con conferma dell'utente o senza dati in uscita. | Restano come sono. |

Prova sull'emulatore: `prova-porte.yml` (curiosa respinta ovunque, amica accolta, Ponte chiuso). **Esito: da riportare
qui dopo il primo giro.**

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
  l'abbinamento. Aperto (punto 8): nessuna segretezza in avanti (chiavi fisse: chi ruba una chiave legge il traffico
  registrato); da fare con chiavi effimere per sessione e, da Android 12, ECDH dentro il Keystore.
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


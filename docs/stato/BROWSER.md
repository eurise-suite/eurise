# Stato del Browser Eurise (telefono e computer)

File di stato dell'area Browser, tenuto dalle chat dedicate al Browser (dal 16 novembre 2026). `docs/STATO.md` resta il
diario generale: lì le chat del Browser scrivono solo una riga per consegna che rimanda qui. Le regole sono quelle di
`CLAUDE.md`; l'architettura per i sistemi in `docs/PIATTAFORME.md`.

## Cosa comprende
- **Telefono**: APK separato `browser` (`browser/src/main/java/eu/eurise/browser`, GeckoView), file
  `browser-debug.apk` in `apk-prova`. Tavole: pagina «Browser» della tela (tutte approvate; le ultime: nuova scheda rifatta,
  menu dell'indirizzo, segnalibri dalla barra, estensioni, barra delle schede).
- **Computer**: Firefox com'è con l'estensione Eurise (`desktop/firefox`, `eurise-firefox.xpi`, non firmata: si prova da
  `about:debugging`; per installarla stabilmente serve la firma con l'account AMO del proprietario). Tavole: PcFirefox
  (pagina Browser) e **PcFirefoxScheda** (pagina «Eurise sul computer», approvata il 16 novembre, scritta il 16 novembre).
- **In comune**: il Ponte telefono–computer (`core/ponte`, `desktop/ponte`, ora anche dentro l'app `desktop/app`), la
  sincronizzazione del Browser (tavola BrwSincro), l'importazione dai browser (`core/sync` `BrowserImport`).

## Fatto (in breve, dettagli nei giorni di STATO.md)
- Telefono: motore GeckoView; nuova scheda, menu, impostazioni, importa password/segnalibri/cronologia (da file), strumenti
  (lettura con voce, trova, traduci, cattura), sessioni e gruppi, barra delle schede, download con `DownloadCard`, favicon
  e anteprime delle schede, «Apri con», `CrashGuard` (giro 592: il Browser non si chiude più all'apertura). Motore di ricerca
  di base Brave Search (`Links.kt`), gli altri nella scelta con la stellina.
- Firefox (15–16 novembre): aspetto Perla/Notte; pulsante Eurise (apri sul telefono, In Note, Da fare, appuntamento,
  documento al telefono, Detta dal telefono); pannello laterale con Oggi e «Dal telefono»; tasto destro; password dal
  telefono (spenta di base); **Protezione Eurise** (browser.privacy: tracciatori sempre, cookie dei tracciatori rifiutati,
  solo HTTPS, niente ping e prefetch) **accesa di base, con l'interruttore in vista nella finestrella del pulsante Eurise**
  per spegnerla con un tocco (decisione del proprietario, 16 novembre), anche nelle Impostazioni con lo stato voce per
  voce; Brave Search come motore (`chrome_settings_overrides`, Firefox chiede all'utente).
- Firefox, **nuova scheda Eurise** (16 novembre, estensione 0.2.0; `ui/newtab.*`, `ui/ai.js`): dalla tavola PcFirefoxScheda.
  Cerca (motore di Firefox, `browser.search`), Chiedi all'AI (elenco del telefono, nessuna di base, spiegazione prima di
  accenderla, servizi chiusi con conferma), Nella cronologia (`history`); Riprendi (ultimo handoff dal Ponte nelle 24 ore,
  **Leggi dopo** nuovo sul computer in `storage.local` con voce nel pulsante e nel tasto destro, ultimo download); siti
  fissati (fino a 8, lettera nella tinta); Per oggi (`todayNewTab`: TodayCache del Ponte, poi al telefono al massimo ogni
  2 minuti, l'ultimo resta nella sessione); Traduci (`about:translations`, **da verificare** che Firefox lo lasci aprire:
  altrimenti un avviso); Password (`pwdFor`, solo dalle pagine dell'estensione e solo se «Password dal telefono» è
  accesa; copia e svuota gli appunti dopo 30 s); Detta (nel campo di ricerca); In Note (il testo del campo); tracciatori
  fermati contati da `webRequest.onErrorOccurred` (NS_ERROR_TRACKING_URI e simili), per settimana. Permessi nuovi:
  history, downloads, downloads.open, search, webRequest, clipboardWrite (Firefox li chiede aggiornando). Il saluto non ha
  il nome (il Ponte non lo dà).
- Telefono, correzioni dalla prova del 16 novembre: **download multipli** (un riquadro per download, fino a quattro uno
  sopra l'altro; prima il riquadro unico mostrava solo l'ultimo e quello finito prima lo sostituiva); **niente doppioni**
  (con «Chiedi dove salvare» il file andava in Download e in più nella cartella scelta: ora, quando Eurise File risponde
  che l'ha salvato, la copia in Download si toglie; se si annulla resta in Download con l'avviso; i «Salva in…» vanno in
  fila uno alla volta); nella barra delle schede la **✕ della scheda aperta sta in fondo alla scheda**, non dopo il titolo.
- Telefono, **richieste delle pagine** (16 novembre, `PagePrompts.kt`): il motore passava al browser «scegli un file»,
  `<select>`, avvisi e conferme, testo, data e ora, colore, accesso HTTP, «lasciare la pagina?», «rimandare il modulo?»
  e senza risposta le chiudeva: **gli upload non funzionavano** (prova del proprietario: una foto). Ora il selettore di
  Android (uno o più file, tipi della pagina) e finestre Material per il resto; anche nelle web app.
- Telefono, **schede e indirizzo compatti come Vivaldi** (17 novembre, tavola BrwCompatta approvata, la proposta centrale;
  il proprietario ha scartato colori del sito e barre che si nascondono): `TopBar.kt` (fila di 46 dp: scudo, sito con il
  percorso in grigio sfumato, Ricarica/Ferma dentro il campo; menu Eurise a destra, a sinistra per i mancini; numero
  delle schede solo senza la fila delle schede) e `TabStrip.kt` (40 dp, schede squadrate uguali, la aperta bianca con la
  ✕ in fondo; con molte schede la aperta resta di 136 dp e le altre scendono fino all'icona). Da 112 a 86 dp prima della
  pagina. La mano arriva dall'app Eurise: **parte comune toccata**, `AppearanceContract.METHOD_HAND` in `core/suite` e
  la risposta in `app/.../AppearanceProvider.kt`.
- Telefono, **più svelto** (18 novembre, giro 789, richiesta del proprietario): l'APK di prova (`browser-debug.apk`) è
  ora compilato come quello ufficiale (R8 con `-dontobfuscate`, non «debuggable», profili di avvio di Compose), stesso id e
  chiave di prova: si aggiorna sopra; da 95,3 a 80,1 MB. I pacchetti `.debug` delle altre app li sceglie
  `BuildConfig.SUITE_DEBUG` (non più `BuildConfig.DEBUG`, che segue «debuggable»); lint vital spento per il Browser.
  Le schede che non si vedono vanno **a riposo** nel motore (`Tab.setVisible`: `setActive`, `setPriorityHint`; 800 ms dopo
  il cambio, per lasciar finire la miniatura); quella in vista ha priorità alta. Le schede riaperte all'avvio e quelle delle
  sessioni salvate **aprono la sessione Gecko solo quando vanno in vista** (`ensureOpen`). Chiusa la scheda in uso, la
  successiva passa da `show()`. Fuori dal thread principale: «Nella cronologia» della nuova scheda e le miniature.
  In `build.yml` il riassunto degli errori del Browser mostra anche quelli di R8.
- **Sicurezza della suite** (18 novembre, avviata da questa chat; da ora nel progetto «Eurise Sicurezza»): chiave di prova nuova con rotazione, piano completo in
  `docs/SICUREZZA.md`. Parti comuni toccate: CLAUDE.md (regola «Chiave di prova»), `.github/workflows/apk-prova.yml`,
  `office-app.yml`, `office-prova.yml`, `.github/scripts/firma-prova.sh`.
- **Correzione del giro 789** (18 novembre): con R8 il Browser si chiudeva un secondo dopo l'apertura (giro 808, segnalato
  dal proprietario). Riprodotto sull'emulatore: SIGSEGV di libxul nel thread «launcher», perché R8 toglieva classi di
  org.mozilla che il motore cerca dal codice nativo. Ora `-keep class org.mozilla.** { *; }` (più `-dontwarn
  kotlin.annotations.jvm.**`). Prova automatica: `prova-avvio-browser.yml` (ramo `prova-avvio` o a mano) compila il
  Browser come l'APK di prova con il motore x86_64 (`-Peurise.geckoAbi=x86_64`) e lo avvia sull'emulatore: primo avvio,
  due pagine, riapertura con le schede salvate. Da far girare prima di ogni consegna che tocca R8 o GeckoView.
- **GeckoView 160** (18 novembre, con gli strumenti nuovi della suite): motore dalla 141 alla 160, sempre l'ultima a ogni
  giro (`[160.0,300.0)`); compileSdk 37.2 per il Browser. Prova di avvio sull'emulatore verde (quattro passi).
- Importazione dal computer, nucleo (`core/sync/BrowserImport.kt`, test): profili di Chrome, Chromium, Brave, Edge,
  Vivaldi, Opera, Firefox (anche snap e Flatpak), segnalibri (JSON di Chromium, `jsonlz4` di Firefox), HTML di Netscape,
  password di Chromium v10/v11 dato il segreto del portachiavi.

## Prossimi passi (in ordine)
1. Provare la nuova scheda in Firefox (about:debugging) e correggere ciò che segnala il proprietario; verificare Traduci.
2. **Importazione dai browser nell'app del computer**: schermata (serve la tavola, partire da BrwImporta del telefono);
   lettura SQLite (password `Login Data`, cronologia; Firefox `logins.json` + `key4.db`); le password vanno in Eurise
   Password con un tipo del Ponte apposta, mai un CSV in Download.
3. **Sincronizzazione Browser telefono–computer** (BrwSincro): segnalibri, schede aperte, leggi dopo, cifrati dal cloud
   scelto o via Ponte.
4. Firma dell'estensione su AMO (passo del proprietario), poi pubblicazione.
5. Telefono: ciò che il proprietario segnala provando l'APK; dire se il giro 789 è più svelto (avvio, cambio scheda,
   pagine). Possibili passi dopo: profilo di avvio proprio del Browser (Baseline Profile), GeckoView più recente.

## Come si lavora in quest'area
- Ramo `brand` come le altre chat; per avere un giro tutto proprio spingere la stessa testa su `prova-browser` (la
  `concurrency` è per ramo: due chat sullo stesso ramo si annullano i giri) e pubblicare quel giro con `apk-prova`
  (`gh workflow run apk-prova.yml --ref brand -f run_id=<giro>`).
- Link da dare a ogni consegna: `browser-debug.apk` (Browser, giro N) e, se cambiato, `eurise-firefox.xpi`.
- Firefox: `node --check` sui file JS prima del commit; il lavoro «Eurise per Firefox» di Compila fa il pacchetto.

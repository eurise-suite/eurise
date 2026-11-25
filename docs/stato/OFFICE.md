# Stato dell'area Office

File di stato dell'area, tenuto dalle chat del progetto «Eurise · Office» (dal 16 novembre 2026). `docs/STATO.md` resta il
diario generale: lì si scrive solo una riga per consegna che rimanda qui. Regole in `CLAUDE.md`, sistemi in
`docs/PIATTAFORME.md`. Ramo `brand`; giri propri su `prova-office`.

## Cosa comprende
- **Telefono e tablet**: APK `eu.eurise.office` (modulo `office/apk`, Kotlin/Compose, leggero: schermata iniziale, vista
  rapida, editor PDF) più l'APK compagno **«Eurise Office Motore»** (`office/motore`, `eu.eurise.office.motore`: motore
  LibreOffice/Collabora co-25.04 con la nostra interfaccia web, `office/app`).
- **Computer (Linux)**: Flatpak `eu.eurise.Office` (`office/computer`): guscio Qt di Collabora Office 26.04 con la nostra
  interfaccia web (stesso `Control.Eurise.js` ed `eurise.css` di Android).
- **Vista rapida** (`office/apk/.../rapida`): docx, odt, xlsx, ods, pptx, odp aperti subito senza motore.
- **PDF**: editor nativo in Office (PdfBox-Android) e lettore PDF dell'APK principale (`feature/drive`, «Modifica in Office»).

## Dove sta il lavoro
Dal 16 novembre (sera) tutto è su `brand`: il ramo `office` (13–15 novembre) è stato unito; il suo diario sta in fondo a
`docs/STATO.md` («Office (ramo `office`…)»). Il ramo `office` e la pre-release `apk-prova-office` restano solo come archivio.
Pubblicazione: **Compila** carica `eurise-office-debug.apk` (Office leggero) con gli altri APK; **office-app** fa il
motore (`eurise-office-motore-debug.apk`); `apk-prova.yml` mette insieme i due (motore dall'ultimo office-app riuscito su
`prova-office` o `brand`). I lavori lunghi di Office partono dal push solo su `prova-office`.

## Fatto (in breve, dettagli nei giorni di STATO.md)
- **Motore** (9–11 novembre): LibreOffice core `distro/collabora/co-25.04` compilato su GitHub Actions (`office-engine.yml`,
  2 h la prima volta, 10 min con la ccache). Togliere componenti fa risparmiare al massimo 5 MB: si tiene intero.
  **Motore a richiesta** deciso l'11 novembre.
- **Interfaccia web Eurise** (11 novembre, `office/app/web`, `eurise_web.py`, `eurise_patch.py`): colori Perla/Notte,
  scheda e gruppo **★ Suite** (Detta, Traduci, Da fare, Evento, Note; Viaggio e Grafico nei fogli), **Cerca un comando**
  (Ctrl K) con le tacche di privacy, guscio Android `EuriseSuite.java`, italiano del motore, «Apri da…» con Eurise File.
- **Perché non si apriva nulla** (13 novembre, trovato con la prova sull'emulatore `office-prova.yml`): SIGSEGV in
  `UnitKit::get()` chiamata da `Document::drainQueue()` su mobile. Corretto in `office/app/eurise_online.py`. Poi la
  chiusura durante le modifiche: era `.uno:Save` → ora `save dontTerminateEdit=1 dontSaveIfUnmodified=1`.
  `onRenderProcessGone` gestito (memoria).
- **Motore a richiesta, strada A** (13 novembre, decisione del proprietario): APK compagno firmato con la stessa chiave,
  senza INTERNET né icona; Office lo scarica (`EngineInstaller`, controlla pacchetto e firme), Android chiede conferma.
  Pesi veri: **Office 17 MB, Motore 113 MB da scaricare, circa 315 MB sul telefono**. Provato dal proprietario il 14
  novembre: «Scarica» funziona, documenti e PDF si aprono.
- **Office nativo** (`office/apk`): schermata iniziale OffHome, `DocumentActivity` per «Apri con», copia di lavoro con il
  nome vero, Eurise File passa i file del telefono in scrittura.
- **Editor PDF nativo** (PdfBox-Android): Annota, Modifica, Pagine, Suite, Altro, Prima di condividere (peso, dati
  nascosti, oscura CF e IBAN, password); dock con pannello a comparsa; annotazioni visibili (`PdfDisplay`).
  «Inserisci da Scanner» con `ScannerContract`/`ScanForResultActivity` (solo sul ramo `office`).
- **Vista rapida** (13–14 novembre): docx/odt/xlsx/ods si aprono subito; correzione di un paragrafo o di una cella che
  riscrive solo quel pezzo del file, commenti, formule semplici ricalcolate, Annulla 20 passi; pptx/odp in sola lettura
  con Presenta. «Modifica completa» apre il motore. Test JVM (Docx, Odt, Sheet, Slides, PdfLogic).
- **Interfaccia Eurise nel motore** su telefono (intestazione, dock per tipo, **card dei gruppi** OffTelefonoGruppi con
  tendine di carattere e dimensione) e tablet (nastro Eurise, una riga sola con la pillola del titolo); **110+ icone
  nostre** al posto di quelle di Collabora con il **colore d'accento** della Scrivania (contratto `AppearanceContract`);
  caricamento nei colori della suite; cursore e maniglie nell'accento. Provato sull'emulatore (telefono e tablet).
- **Computer** (14–15 novembre): Flatpak **strada 1** (Collabora da Flathub ricomposto, `flatpak-prova-office`) e
  **strada 2** dai sorgenti (`office-sorgenti.yml`, ricetta nostra, nome D-Bus `eu.eurise.Office`, **schede dei
  documenti** in una finestra, rinomina dalla pillola; `flatpak-sorgenti-office`). Interfaccia: Inizio Eurise, nastri
  completi come Word/Excel/PowerPoint con la Suite, tendine attaccate al comando, menu File compatto, Trova e sostituisci
  a barra, finestre nello stile della suite, colonna Proprietà ridisegnata con Struttura / Collegato a / In Eurise File,
  righelli, **ergonomia** (barra rapida sulla selezione, schede contestuali, tasto destro nostro, tasti Alt, «I tuoi
  comandi», nastro a scomparsa, Concentrazione), Plus Jakarta Sans e più contrasto. Ultimo pacchetto consegnato il 15
  novembre alle 17:20 UTC dopo la seconda prova del proprietario su Ubuntu.
- **Su `brand`** (indipendenti dal ramo `office`): bolla Documenti apre Eurise Office per primo (14 novembre); icona B
  (14 novembre) poi serie colorata viola → magenta (16 novembre, `scripts/icone.py`, anche l'icona dell'APK via
  `eurise_patch.py`); `CrashGuard` in `core/designsystem/crash` (per ora solo nel Browser); «Apri con» PDF nell'APK
  principale (`PdfOpenActivity`, 15 novembre).

- **22 novembre, giro 1015**: «Compila con I miei dati» nei moduli PDF (Eurise Password, `MyDataActivity` nuova in
  `password`, contratto in `PasswordContract`; abbinamento `MyDataFill` con test); «Link dal cloud» in Prima di
  condividere (`FilesContract.EXTRA_LINK`, `EuriseFiles.link`, solo Murena e Nextcloud); «Chiedi all'AI · Alfred» in Cerca
  un comando sul telefono (`EuriseSuite.java` apre Alfred con la domanda; sul computer «in arrivo»); mano dominante dalla
  Scrivania (`AppearanceContract.METHOD_HAND`, l'interruttore di Office resta solo senza l'app Eurise). Prova del
  computer (office-computer) verde con il nuovo `Control.Eurise.js`. Parti comuni toccate: `core/suite`, `core/files`,
  `password`.
- **17 novembre, giri 718 e 724** (motore con `Uscite`; prova sull'emulatore 44003666557: dock a posto, motore vivo): dock della schermata iniziale Recenti / Telefono / Cloud (tavola OffOggiHome: tocco breve
  sul cloud preferito, pressione lunga «File dal cloud» con la stellina; `PlacesProvider` nell'app Eurise, `EXTRA_START`
  in `FilesContract`, `onLongSelect` in `EuriseDock`); **rapporto del problema** (OffRapporto, OffRapportoVedi):
  chiusure di Office e del motore da ApplicationExitInfo (provider `Uscite` nel motore), scheda sopra i recenti,
  rapporto da condividere, Impostazioni › Rapporti dei problemi, test `ReportsTest`.
- **16 novembre, giro 692** (`apk-prova`): Office leggero 17 MB e motore 113 MB (primo giro di office-app su
  `prova-office`, 1 h 46 senza ccache). `CrashGuard` è già dentro Office leggero (fornitore di `core/designsystem`).
- **16 novembre, unione in `brand`**: un solo «Apri con» per i PDF (quello di File, con «Modifica in Office»);
  `AppearanceProvider` nell'app Eurise (accento della Scrivania per le icone di Office); centro privacy: servizio
  «Motore dei documenti di Office» (GitHub, Stati Uniti), acceso e annotato da Office quando si scarica; icona colorata
  anche in Office leggero e nel motore; prova sull'emulatore con schermo «grande» (1080×2400, 420 dpi).

## Tavole (tela, pagina «Office»)
**Approvate**
- 11 novembre: OffMotore, OffTelefono, OffCerca, OffTablet, OffComputer (in codice, tranne «Chiedi all'AI» ancora «in
  arrivo» e la colonna «In Eurise File» con i file vicini); OffPdf, OffPdfPagine, OffPdfCondividi (in codice, nativo).
- 13 novembre: OffHome; OffLettura, OffLetturaCorreggi, OffLetturaFoglio, OffLetturaCompleta (in codice).
- 14 novembre: OffTelefonoGruppi, OffLetturaPresentazione (in codice); OffRapporto, OffRapportoVedi (in codice il 17
  novembre); OfficeIcona B (pagina Icone; superata dalla serie colorata del 16).
- 15 novembre: OffPCInizio, OffPCNastro, OffPCTendine, OffPCFile, OffPCFinestre («via direttamente», in codice) e le
  dieci proposte di ergonomia del computer (in codice; la 10, anteprima al passaggio, solo in parte).
- Pagina «Drive e Scanner»: FileSalvaIn, FileApriDa (approvate 11 novembre, usate da Office).

**Da approvare**: nessuna. Restano solo di riferimento le tavole «Office com'è oggi» (OffOggiTelefono, OffOggiHome,
OffOggiCaricamento, OffOggiTablet, OffOggiComputer, 14 novembre).
**Da sistemare sulla tela** (titoli fermi): OffPC* e il titolo di fila «Office sul computer…» dicono ancora «da
approvare»; OfficeIcona pure. Si correggono la prossima volta che si tocca la tela.

## Prossimi passi (in ordine)
1. **Motore nuovo fatto** (giro 1023, office-app 44965496829; prova sull'emulatore 44976944123 verde, testata nostra): porta il controllo d'origine di postMessage (area
   Sicurezza, 19 novembre) e «Chiedi all'AI · Alfred». Prova sull'emulatore (`schermo=grande`, poi `tablet`) e
   pubblicazione in `apk-prova`. Attenzione: un office-app vecchio in corso tiene ferma la coda del gruppo `office-app`.
2. **Prova del proprietario sul telefono**: testata nel motore («Aspetto esatto»), dock Telefono/Cloud, rapporto,
   «Scarica» del motore, vista rapida, PDF con «Inserisci da Scanner», «Compila con I miei dati», «Link dal cloud»,
   «Chiedi all'AI», mano dalla Scrivania.
3. **Computer**: esito della prova del pacchetto del 15 novembre; poi «Apri da Eurise File» e «Collegato a» con dati veri
   (Ponte), «Chiedi all'AI» anche lì, proposta 10 da rivedere, verso Flathub; poi la webapp (decisa il 15 novembre).
4. **Peso del motore** (113/315 MB): librerie non compresse, -Os/LTO, ICU solo europea; F-Droid con due pacchetti.
5. **Ancora da fare**: «I miei dati» anche nei moduli aperti nel motore (oggi solo nel PDF nativo); tablet vero (Volla);
   titoli sulla tela (OffPC* e OfficeIcona dicono ancora «da approvare»).

## Come si lavora
- Inizio di ogni chat: `CLAUDE.md`, questo file; di `docs/STATO.md` solo le parti citate qui. Prima di ogni push
  `git fetch` e rebase su `origin/brand`; poi la stessa testa anche su `prova-office`. Mai `git add -A` (altre chat
  lavorano sulla stessa copia o sullo stesso ramo).
- Prima le tavole sulla tela (pagina «Office»), poi il codice. Parti comuni (`core/designsystem`, `core/ponte`,
  `core/suite`, `CLAUDE.md`, `canvas.json`) solo se serve, e scriverlo qui.
- **Lavori** (Actions):
  - `office-app.yml`: motore e APK compagno (gruppo `office-app`, coda unica; ~25 min con la ccache, ore senza).
    `office-engine.yml` non si tocca (riparte la compilazione lunga). DIAGNOSI=1 solo per cercare un arresto.
  - `office-prova.yml`: emulatore Android 14 con sei campioni; `fonte` = motore | nativo (compila e pubblica anche
    Office leggero) | collabora (+ `versione`); `schermo` = telefono | tablet. Un push su `office/prova/**` annulla la
    prova in corso. Esito in `riassunto.md` e nella pre-release `office-prova-schermate` (scaricare con
    `gh api .../releases/assets/ID`).
  - `office-computer.yml` (strada 1 e prova, `pacchetto=sorgenti` per provare la strada 2, schermate in
    `office-computer-schermate` / `office-sorgenti-schermate`), `office-sorgenti.yml` (strada 2, ~45 min con la ccache),
    `office-guscio.yml` (sorgenti del guscio Qt 26.04, solo su gerrit).
- **Regole imparate**: mai `.uno:Save` dal nostro codice (manda in crash il motore); ESLint 7 e Stylelint 13.7 con le
  regole di Collabora si provano in locale prima di spingere; test JVM della vista rapida con kotlinc scaricato da
  GitHub (Maven non si raggiunge da qui); nella 26.04 la diagnosi passa da `app.console.log`.
- **Consegna**: appena la compilazione riesce, link diretti in chat, uno per file con nome e giro, presi da `apk-prova`
  (`browser_download_url`), mai allegati: per esempio
  «Office, giro N: https://github.com/eurise-suite/eurise/releases/download/apk-prova/eurise-office-debug.apk» e
  «Motore di Office, giro N: …/apk-prova/eurise-office-motore-debug.apk». Il Flatpak: link diretto al `.flatpak` della
  sua pre-release, si installa con `flatpak install --user <file>` (runtime KDE 6.11 da Flathub). A ogni consegna:
  aggiornare questo file e una sola riga in `docs/STATO.md`.
- Attenzione: un push che **crea** `prova-office` fa partire tutti i lavori del repository (i filtri dei percorsi non
  valgono per un ramo nuovo): annullare quelli che non servono (Mappe, Pietre miliari, Voci, Suonerie, apk-prova).
- Computer (strada 2, 15 novembre):
  https://github.com/eurise-suite/eurise/releases/download/flatpak-sorgenti-office/eurise-office-sorgenti.flatpak

## Sicurezza (18 novembre 2026, notte, progetto Eurise Sicurezza)
- `Uscite` del motore accetta solo il permesso della propria variante; Office chiede aspetto, luoghi, privacy e uscite solo
  a fornitori della suite verificati. Dettagli in `docs/SICUREZZA.md`.

## Sicurezza, 20 novembre 2026, sera (progetto Eurise Sicurezza)
Office: «Salva in», «Apri da» e `Suite.installed` contano solo l'app Eurise della suite (`FilesContract` con il `Context`). Dettagli in `docs/SICUREZZA.md`, «Intent verso le altre app della suite».

## 22 novembre 2026: Impostazioni Eurise, proposta dell'area Organizzazione (serve la vostra risposta)
Un posto unico per le impostazioni della suite (tavola ImpEurise, approvata): ogni app tiene le sue impostazioni, Impostazioni Eurise le mostra, le cerca e le apre. Proposta di voci, navigazione e contratto (`AppHubContract.METHOD_SETTINGS`, `EXTRA_SETTING`) in `docs/IMPOSTAZIONI.md`, con le domande per ogni area: scrivete la risposta in fondo a quel file, una riga. Finché non rispondete la vostra app compare con una riga di riserva che non tocca nulla da voi.

## 22 novembre 2026, chat unica del proprietario: sicurezza e pulizia di tutta la suite (giro 1038)
Il proprietario ha riunito il lavoro in questa chat: tutti gli APK alla versione corrente, controllo di sicurezza su
tutte le app, analisi di ciò che resta per ogni app, pulizia del codice.
- **Sicurezza**: esito completo in `docs/SICUREZZA.md`, sezione «22 novembre 2026: controllo completo». Corretti Alfred
  (una frase da un'altra app non parte più da sola: `AlfredTrust`), note bloccate con `FLAG_SECURE`, iniezione nei flussi
  `office-prova.yml` e `prova-porte.yml`. Da decidere: APK di prova non debuggable per Password ed Eurise.
- **Office**: la versione ufficiale non compilava più (lint: `office_background_night` solo in `values-night`): ora il
  colore sta anche in `values/themes.xml`. Vista rapida dei fogli: una formula che non sa leggere si tornava a vedere
  come valore modificabile (il `?: ""` cancellava il null): ora rimanda di nuovo alla modifica completa.
- **Pulizia**: 29 risorse inutilizzate tolte; avvio dell'app Eurise più leggero (pianificazione del cloud fuori dal
  thread principale, dopo la registrazione dei lavori); avvisi del compilatore (chiamate sicure e `!!` inutili, ritorno
  nel Ponte del computer che con Kotlin 2.5 diventerebbe errore, `localeFilters` al posto di `resourceConfigurations` in
  13 moduli, `LocalLifecycleOwner` nuovo). Versione ufficiale 5,5 MB (tetto 6).
- **File comuni toccati**: `core/cloud` (WebDav, NextcloudLogin, SuiteSecrets), `core/data` (AlfredTrust nuovo, QuickAdd,
  Modes, MeteoRepository), `core/designsystem` (ColorPicker, solo un `!!`), `build.gradle.kts` di 13 moduli.

## Sicurezza, 22 novembre 2026, mezzogiorno (progetto Eurise Sicurezza)
R8 anche negli APK di prova (giro 1048): regole comuni prudenti in `proguard-suite.pro` (codice `eu.eurise` tenuto, nomi leggibili), più il `proguard-rules.pro` dell'app. Una libreria nuova che si carica per nome (riflessione, JNI) va tenuta lì; se un'app si guasta: `-Peurise.r8.senza=:modulo`. Dettagli in `docs/SICUREZZA.md`.

## 22 novembre 2026, sera (progetto Eurise Sicurezza, su richiesta del proprietario)
- **Fogli a schedario** (tavole OffSchedarioComputer e OffSchedarioTelefono, approvate): pezzo comune
  `core/designsystem/component/Folder.kt` (`FolderTabs`, `folderPath`, `folderBackground`), lo stesso del Browser.
  - Vista rapida (giro 1064): `QuickScreen.SheetTabs` usa `FolderTabs` con la linguetta che pende dalla tabella; la tabella
    finisce sopra le linguette e la barra in basso non la copre più (spazio misurato); cambiando foglio quello nuovo arriva
    dalla linguetta (180 ms). Pressione lunga della tavola (Rinomina, Sposta, Duplica, Elimina) fatta il 23 novembre,
    sera (giro 1107, sezione sotto).
  - Modifica completa (`office/app/web/eurise.css`, in fondo): le schede dei fogli di Collabora a schedario, con i
    piedini e senza la sottolineatura di Collabora 26.04; provate sul computer (prova del Flatpak, giro 51). Sul telefono e
    sul tablet arrivano con il motore 67.
  - Schede dei documenti sul computer: il guscio di Collabora 26.04 le disegna già a schedario (maschera con i piedini,
    `qtapp-tabstrip.css`); `eurise-tabstrip.css` dà solo i colori. La prova del computer ora copia i fogli della striscia
    in `office-computer-schermate` (sorgente-qtapp-tabstrip.*.txt), perché non sono su GitHub.
- **Nastro a schedario** su tablet e computer (tavola SchedarioProve, decisa il 22 novembre; `eurise.css` in fondo,
  `body.eurise-tablet`): la scheda aperta del nastro è una linguetta bianca che continua nella fascia bianca dei comandi
  (`--eurise-band`), i gruppi sono parti della fascia divise da un filo. Provato sul computer (prova del Flatpak).
  Sul tablet Android arriva con il motore 68.
- Motore 68 fallito sul controllo di stile di Collabora (selettore doppio `body.eurise-tablet #eurise-top` nel nastro):
  unito a quello esistente; **motore 69 riuscito e pubblicato** (23 novembre, 01:30 UTC): nastro e fogli a schedario
  anche nella modifica completa su telefono e tablet. Attenzione: `eurise.css` passa da stylelint
  (`no-duplicate-selectors`) quando si compila il motore, non nella prova del computer.

## 23 novembre 2026, sera (progetto Eurise Sicurezza): pressione lunga sui fogli della vista rapida
- **Cosa fa** (tavola OffSchedarioTelefono): sulla linguetta di un foglio xlsx o ods la pressione lunga apre in basso nome e
  posizione («Foglio 2 di 3»), «A sinistra» / «A destra» (stessa larghezza; il menu resta aperto sul foglio, così lo si
  sposta di più posti), Rinomina (il motivo appare sotto il campo: troppo lungo, caratteri, nome già preso), Duplica (subito
  dopo l'originale, «Nome (2)», e si apre la copia), Elimina (con conferma; spento se il foglio è l'ultimo). Ogni operazione
  si salva come le celle e «Annulla» riporta anche al foglio che si guardava.
- **Codice**: `rapida/SheetOps.kt` (`SheetNames`, `ExcelRefs`, `OdfRefs`, `XlsxSheets`, `OdsSheets`); `SheetFormat` ha
  `renameSheet`, `moveSheet`, `duplicateSheet`, `deleteSheet`; `QuickModel` le chiama con `sheetChange`; `QuickScreen`
  ha `SheetMenu` e `RenameSheetDialog`.
- **Cosa segue il foglio**: Excel: formule di tutti i fogli, nomi definiti, grafici (`c:f`), convalide e formati
  condizionali, collegamenti interni (`location`), sorgente delle pivot; i nomi locali (area di stampa, titoli) seguono
  spostamento e copia; dopo Sposta/Duplica/Elimina si toglie `calcChain.xml` (Excel la ricostruisce, niente «riparare il
  file»). OpenDocument: formule, nomi, convalide, formati condizionali, forme ancorate, grafici incorporati (`Object
  N/content.xml`) e le impostazioni di vista (`settings.xml`: zoom del foglio, foglio aperto). Stringhe e riferimenti ad
  altri file restano come sono.
- **Rifiuti verso la modifica completa** (messaggio chiaro, il file non cambia): duplicare un foglio con grafici,
  tabelle, commenti o disegni (Excel) o con oggetti incorporati e dati collegati (ods); eliminare un foglio che altri
  fogli, nomi o grafici usano (Excel e LibreOffice li riempirebbero di #RIF!); l'ultimo foglio.
- **Prove**: `SheetOpsTest` (9 test: nomi, riferimenti nei due formati, le quattro operazioni su xlsx e ods, anche
  passando dallo zip). Da provare sul telefono: un foglio Excel vero con grafici e uno di LibreOffice, rinominare e
  spostare, poi aprirli sul computer.
- **Resta**: con un solo foglio le linguette non si vedono (tavola), quindi niente pressione lunga: per duplicare
  l'unico foglio serve la modifica completa. I riferimenti a più fogli di Excel (`Gen:Mar!A1`) seguono la rinomina; uno
  spostamento cambia quali fogli stanno tra Gen e Mar, come in Excel.

## 25 novembre 2026 (progetto Eurise Sicurezza): tutto schermo nei documenti
- Chiesto dal proprietario («soprattutto con il telefono orizzontale»). Vista rapida (testi e fogli), PDF di Office e
  lettore PDF di File: in orizzontale si entra da soli a tutto schermo; in verticale con il pulsante a quattro angoli in
  alto. Spariscono intestazione, linguette dei fogli, barra in basso e barre di sistema (uno scorrimento dal bordo le
  mostra per un momento).
- Una maniglia piccola e velata, in basso dal lato della mano, richiama la barra sopra il documento (senza spingerlo),
  con «Esci dal tutto schermo»; dopo 5 secondi si rinasconde. Nel PDF la barra resta mentre si annota, si cerca o è aperto
  un pannello. Indietro chiude prima la barra, poi il tutto schermo. La scelta vale finché non si ruota il telefono.
- Pezzi comuni in `core/designsystem` (`component/Fullscreen.kt`: `SystemBarsHidden`, `FullscreenHandle`,
  `ExitFullscreen`; icone `fullscreen` e `fullscreenExit`). Le diapositive hanno già «Presenta».

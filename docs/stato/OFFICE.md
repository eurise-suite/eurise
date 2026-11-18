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
1. **Testata in stile Collabora sul telefono** (segnalata il 16 novembre aprendo «Aspetto esatto»): con Office e motore
   del giro 692 la prova sull'emulatore a schermo grande (prova 43977581784, `schermo=grande`, 1080×1920) mostra la
   nostra testata (indietro, nome, Salvato, ricerca, annulla, condividi) e la dock. Quasi certamente sul telefono c'era
   Office 57 (vecchia app di Collabora, stesso pacchetto). Aspettare la conferma del proprietario; se resta, chiedere una foto.
2. **Pubblicazione condivisa**: `apk-prova` la rifanno tutte le aree. Un'area che pubblica da un ramo senza l'unione del 16
   novembre lascia `apk-prova` senza Office (è successo con il giro 667): si ripubblica da `prova-office` portato alla
   testa di `brand`. Dal giro 692 Compila carica sempre Office e `apk-prova` prende il motore da office-app.
3. **Prova del proprietario sul telefono**: dock Telefono/Cloud e rapporto (giro 718), «Scarica» del motore, vista rapida (anche presentazioni), card dei gruppi,
   PDF con «Inserisci da Scanner» (ora l'app Eurise ha `ScanForResultActivity`), accento delle icone dalla Scrivania.
4. **Computer**: esito della prova del pacchetto del 15 novembre; poi «Apri da Eurise File» e «Collegato a» con dati veri
   (Ponte), proposta 10 da rivedere, verso Flathub; poi la webapp (decisa il 15 novembre).
5. **Peso del motore** (113/315 MB): librerie non compresse, -Os/LTO, ICU solo europea; F-Droid con due pacchetti.
6. **Mancanze note**: «I miei dati» nei moduli PDF, «Link dal cloud», «Salva in…» per ogni documento, «Chiedi all'AI»,
   mano dominante presa dalla suite; tablet vero (Volla) appena arriva.

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

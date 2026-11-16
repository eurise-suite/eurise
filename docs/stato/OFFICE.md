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

## ⚠️ Dove sta il lavoro (16 novembre)
Quasi tutto il lavoro dal 13 al 15 novembre è sul **ramo `office`** (≈150 commit da `b858da8`, 123 file, mai unito a
`brand`); il diario completo è nella sezione «Office (ramo `office`…)» di `docs/STATO.md` **su quel ramo**. Su `brand`
c'è ancora la vecchia app di Collabora con il motore dentro (`office-app.yml` → «Office 57», 237–249 MB, documenti che
non si aprono): è quella che `apk-prova` pubblica oggi come `eurise-office-debug.apk`. Gli APK buoni sono nella
pre-release `apk-prova-office`. Prova di unione fatta qui: conflitti solo in `CLAUDE.md`, `docs/STATO.md` e
`feature/drive/.../DriveRoute.kt` (anche `brand` il 15 novembre ha fatto il suo `PdfOpenActivity`).

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

## Tavole (tela, pagina «Office»)
**Approvate**
- 11 novembre: OffMotore, OffTelefono, OffCerca, OffTablet, OffComputer (in codice, tranne «Chiedi all'AI» ancora «in
  arrivo» e la colonna «In Eurise File» con i file vicini); OffPdf, OffPdfPagine, OffPdfCondividi (in codice, nativo).
- 13 novembre: OffHome; OffLettura, OffLetturaCorreggi, OffLetturaFoglio, OffLetturaCompleta (in codice).
- 14 novembre: OffTelefonoGruppi, OffLetturaPresentazione (in codice); **OffRapporto, OffRapportoVedi** (rapporto quando
  Office si è chiuso: **non ancora in codice**); OfficeIcona B (pagina Icone; superata dalla serie colorata del 16).
- 15 novembre: OffPCInizio, OffPCNastro, OffPCTendine, OffPCFile, OffPCFinestre («via direttamente», in codice) e le
  dieci proposte di ergonomia del computer (in codice; la 10, anteprima al passaggio, solo in parte).
- Pagina «Drive e Scanner»: FileSalvaIn, FileApriDa (approvate 11 novembre, usate da Office).

**Da approvare**: nessuna. Restano solo di riferimento le tavole «Office com'è oggi» (OffOggiTelefono, OffOggiHome,
OffOggiCaricamento, OffOggiTablet, OffOggiComputer, 14 novembre).
**Da sistemare sulla tela** (titoli fermi): OffPC* e il titolo di fila «Office sul computer…» dicono ancora «da
approvare»; OfficeIcona pure. Si correggono la prossima volta che si tocca la tela.

## Prossimi passi (in ordine)
1. **Unire `office` in `brand`** (sciogliere i tre conflitti; per i PDF tenere un solo «Apri con» con «Modifica in
   Office»). Al merge, come scritto sul ramo `office`:
   - `apk-prova.yml` pubblica `eurise-office-debug.apk` (Office leggero) **e** `eurise-office-motore-debug.apk`, presi dal
     giro di `prova-office`; la vecchia app di Collabora non si pubblica più;
   - `office-computer.yml`, `office-sorgenti.yml`, `office-guscio.yml` partono oggi solo dal ramo `office`: aggiungere
     `brand` e `prova-office`;
   - `ENGINE_URL` di prova verso `apk-prova` invece di `apk-prova-office`;
   - icona colorata nuova anche in `office/apk` e nel caricamento del motore;
   - nell'app Eurise: `AppearanceProvider` (accento della Scrivania per Office) e la voce **«Download del motore di
     Office»** nel centro privacy (parti comuni: scriverlo qui).
2. **Giro su `prova-office` e prova del proprietario sul telefono**: «Scarica» del motore, vista rapida (anche
   presentazioni), card dei gruppi, PDF con «Inserisci da Scanner» (arriva con il merge), chiusure durante la modifica.
3. **OffRapporto e OffRapportoVedi in codice** con `CrashGuard` (in Office e nel processo del motore): serve per
   capire le chiusure che l'emulatore non riproduce.
4. **Computer**: esito della prova del pacchetto del 15 novembre; poi «Apri da Eurise File» e «Collegato a» con dati veri
   (Ponte, socket `eurise-ponte.sock`), proposta 10 da rivedere con il proprietario, verso Flathub. Quando Office sul
   computer è completo: la webapp (Office nel browser), decisa il 15 novembre.
5. **Peso del motore** (113/315 MB): provare librerie non compresse, -Os/LTO, ICU solo europea; F-Droid con due pacchetti.
6. **Mancanze note**: «I miei dati» nei moduli PDF (contratto con Password), «Link dal cloud», «Salva in…» per ogni
   documento con l'origine scritta, «Chiedi all'AI», mano dominante presa dalla suite; tablet vero (Volla) appena arriva.
7. **Dock della schermata iniziale** (OffOggiHome: Recenti, Telefono, Cloud con la stellina): la faceva un'altra chat e i
   suoi file (`Clouds.kt`, `CloudSheet.kt`…) **non sono nel repository**: chiedere al proprietario se rifarla qui.

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
- **APK di oggi da provare** (finché il merge non è fatto): Office 14 novembre
  https://github.com/eurise-suite/eurise/releases/download/apk-prova-office/eurise-office-debug.apk · Motore 14 novembre
  https://github.com/eurise-suite/eurise/releases/download/apk-prova-office/eurise-office-motore-debug.apk · Computer
  (strada 2, 15 novembre)
  https://github.com/eurise-suite/eurise/releases/download/flatpak-sorgenti-office/eurise-office-sorgenti.flatpak

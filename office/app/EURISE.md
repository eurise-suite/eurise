# Eurise Office per Android: prototipo dell'app (dopo il motore)

Scelta approvata l'11 novembre (docs/PIATTAFORME.md): un'interfaccia sola in HTML/JS, quella di Collabora Online
(MPL 2.0), sopra il nostro motore LibreOffice; la stessa su Android, Linux (Collabora Office per il computer, Qt) e
gli altri sistemi. Questo lavoro costruisce l'app Android di Collabora con il nostro motore e la adatta a Eurise.

- Lavoro: `.github/workflows/office-app.yml` (a mano o quando cambiano `office/app/**`). Stesso motore e stessa ccache
  di «Prototipo del motore di Office» (`CORE_COMMIT`), poi POCO (`poco_android.py`, solo arm64, API 26) e zstd per
  Android, `online` al commit fissato (`ONLINE_COMMIT`, ramo `distro/collabora/co-25.04`) con `--enable-androidapp`,
  infine Gradle. Artefatto `eurise-office-app-N`: APK, misure, registri.
- **Italiano**: il lavoro prende dal sottomodulo `translations` solo `source/it`, allo stesso commit che il core chiede,
  e configura il motore con `--with-lang=en-US it` (solo nel lavoro dell'app: `office/engine/autogen.input.in` resta
  com'è, così non riparte il lavoro del motore). `WITH_LANG` finisce solo in `config_langs.h`: la ccache regge.

## Interfaccia web (`eurise_web.py`, prima di `./autogen.sh` di online)
- `web/Control.Eurise.js` → `browser/src/control/`, nella lista dei JS dopo `Control.NotebookbarBuilder.js`:
  - **scheda Suite** del nastro in Writer, Calc, Impress e Draw (tavole OffTablet e OffComputer): gruppo «Cerca»
    («Cerca un comando») e gruppo «★ Suite» in borgogna con Detta, Traduci, Da fare, Evento e Note; nel foglio di calcolo
    Viaggio e Grafico al posto di Note;
  - **Cerca un comando** (OffCerca; Ctrl K o Cmd K, che in LibreOffice era «Collegamento»: il collegamento resta tra i
    comandi): comandi del nastro con la scheda («Tabella · Inserisci»), poi Nella suite (3 tacche), Sul web (Brave
    Search, Qwant, Ecosia, DuckDuckGo: 2 tacche), Chiedi all'AI (Eurise AI sul dispositivo «in arrivo», Duck.ai 2,
    Le Chat 1). Se c'è testo scelto il campo parte già scritto;
  - **pulsante Suite** in testa alla barra in basso del telefono (OffTelefono): apre la stessa ricerca;
  - il testo scelto si chiede al motore (`gettextselection`), il testo da inserire si scrive al cursore (`textinput`).
  - **PDF** (tavole OffPdf, OffPdfPagine, OffPdfCondividi; solo se il file aperto è un `.pdf`, che si apre in Draw):
    schede **Annota** (Evidenzia = sfondo sabbia sul testo scelto, Sottolinea, Nota = commento, Penna, Testo, Firma a
    mano) e **Pagine** (Nuova, Duplica, Sposta prima/dopo, Togli) prima delle altre; sul telefono Evidenzia, Penna e
    Condividi dopo il pulsante Suite. **Prima di condividere**: peso (Schermo 150 dpi, Stampa 300, Sotto 1 MB per la
    PEC 96), note e commenti tolti, oscuramento di codici fiscali e IBAN (sostituiti nel testo con █, poi annullati in
    questo file dopo la copia), password per aprirlo (creata qui, copiata negli appunti). La copia la fa il guscio
    (`pdf`: `saveAs` con le opzioni JSON del filtro PDF) e la condivide o la passa a «Salva in…» di Eurise File
    (`FilesActivity` dell'app Eurise, permesso della suite). Provato qui con eslint 7 e stylelint 13.7 con le regole
    di Collabora; da provare sul telefono (opzioni JSON del filtro, oscuramento con l'espressione regolare in Draw).
- `web/eurise.css` → ultimo foglio di stile (vince sui colori scuri di Collabora; in coda a `color-palette.css` avrebbe
  avuto `:root` doppio, che stylelint rifiuta). Colori Perla/Notte, scheda attiva con tratto e grassetto, gruppi a
  isole, gruppo Suite borgogna, riquadro della ricerca con le tacche, avviso breve in basso.
- `web/images/` → icone della Suite (chiare e `dark/`) dai tracciati di `EuriseIcons`.
- Provato fuori dalla CI con le versioni di Collabora: eslint 7.0.0, prettier 3, stylelint 13.7.0, uglify-js 3.17.4, e
  con una prova in jsdom (schede, contenuto per tipo di documento, messaggi al guscio, ricerca, Ctrl K).

## Guscio Android (`eurise_patch.py`, dopo `./configure` di online)
- Pacchetto `eu.eurise.office` (`.debug` nella prova), chiave di prova della suite.
- `android/EuriseSuite.java` → `org.libreoffice.androidlib`; `LOActivity` passa i messaggi `EURISE {json}` a
  `EuriseSuite.handle` e risponde con `euriseReply` (`window.euriseOffice.reply`). Stesso protocollo per il guscio Linux.
  - Da fare e Note → app Eurise (`KEYBOARD_TASK`/`KEYBOARD_NOTE`, permesso della suite), con «Da «documento»» in coda;
  - Viaggio → per ora Da fare con il segno «Viaggio · » (l'app Eurise non ha ancora un ricevitore per i viaggi);
  - Evento → modulo del calendario (`ACTION_INSERT`) con titolo e descrizione;
  - Detta → Eurise Voce (`DictateActivity`, risposta `DICTATION_RESULT`), il testo entra al cursore;
  - Traduci → servizio di Eurise Leggi (`TranslateContract`): scelta Da/A tra le lingue scaricate, il testo tradotto
    sostituisce la selezione; se manca una lingua si apre Leggi;
  - Cerca → Scrivania (`EXTRA_SEARCH`) o riquadro web dell'app Eurise (`WebPopupActivity`), altrimenti il browser.
- Manifest: permesso `SUITE_DEBUG` (solo quello di prova: l'APK è firmato con la chiave di prova; la versione ufficiale
  userà `SUITE`), `<queries>` per le app Eurise di prova, il calendario e il browser.

## Ancora da fare
- **Motore scaricato a richiesta** (OffMotore, circa 66 MB con il Wi-Fi): oggi il motore è collegato dentro
  `libandroidapp.so` e legge i suoi file dall'APK con l'AssetManager. Va separato: libreria e file del motore in un
  pacchetto scaricato nella memoria dell'app, `System.load` dal percorso, file letti dal disco (modifica a
  `androidapp.cpp`). Si fa dopo il primo APK che funziona, con le misure vere.
- Eurise File per aprire (`eu.eurise.action.OPEN_FROM`, già pronta nell'app Eurise) e salvare ogni documento,
  non solo la copia PDF (origine sempre scritta); inserire pagine dallo Scanner; ruotare le pagine del PDF; colonna «In Eurise File», interruttore «ricerca» nelle
  proprietà, campo di ricerca sempre visibile nel nastro.
- Linux: `--enable-qtapp` e Flatpak, con le stesse modifiche all'interfaccia (`eurise_web.py` vale già).

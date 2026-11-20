# Stato dell'area Computer

File di stato dell'area, tenuto dalle chat del progetto «Eurise · Computer» (dal 16 novembre 2026). `docs/STATO.md` resta il
diario generale: lì si scrive solo una riga per consegna che rimanda qui. Regole in `CLAUDE.md`, sistemi in
`docs/PIATTAFORME.md`. Ramo `brand`; giri propri su `prova-computer`.

## Cosa comprende
L'app Eurise per Linux (`desktop/app`, Compose Desktop) con Oggi, Calendario, Note, Attività, Email, Mappe e Alfred;
`core/sync` (sincronizzazione su JVM); il Ponte sul computer (`desktop/ponte`, dentro l'app); Flatpak
(`desktop/flatpak`) e Flathub. Firefox con l'estensione Eurise è dell'area Browser (`docs/stato/BROWSER.md`); Office per
Linux dell'area Office. Le schermate per area arrivano dai progetti delle aree o da qui.

Riferimento: Ubuntu e Fedora (si prova sull'ultima Ubuntu LTS, l'ultima Ubuntu e l'ultima Fedora; GNOME, poi KDE);
Flatpak su Flathub. Un'app «Eurise» sola per il computer che tiene il Ponte e accoglie le sezioni (PIATTAFORME.md).

## Fatto (in breve, dettagli nei giorni di STATO.md)
- **Ponte** (11–15 novembre): `core/ponte` (Kotlin puro, identità EC P-256, ECDH + HKDF, test) e `desktop/ponte`
  (`eurise-ponte`): abbinamento con il QR, telefono → computer (link, appunti, dettatura), computer → telefono con il
  collegamento fisso solo sul Wi-Fi di casa e la notifica che dice sempre cosa passa (decisione del proprietario);
  `Outbox` per i telefoni non collegati; socket locale `$XDG_RUNTIME_DIR/eurise-ponte.sock` (righe JSON, permessi 600);
  `eurise manda appunti|link|nota|attivita|evento|file`; file a pezzi da 512 KB con SHA-256 (`FileParts`); native
  messaging per Firefox (`eurise firefox`, `eurise installa-firefox`, anche per il Flatpak di Firefox); password dal
  telefono (approva sul telefono, in memoria 2 minuti); `DictationAsk`, `TodayAsk`/`Today` (`TodayCache`: l'ultimo Oggi
  dal telefono, solo con il telefono collegato; sul telefono «Oggi sul computer» **spento di base**: appuntamenti di oggi
  ancora da finire e fino a cinque attività aperte, prima le scadute).
- **`core/sync`** (15 novembre, con test `:core:sync:test`): `DavHttp`, `CalDav` + `CalSync.plan` (conflitto → più recente,
  eliminazioni mai a perdere), `ICal`, `Merge3` e `NoteDoc`/`NotesSync` (stesso formato delle note del telefono, unione a
  tre vie), `MailClient` IMAP/SMTP e `MailText` (persona/avviso/newsletter), `BrowserImport`.
- **`desktop/app`** (15 novembre, scheletro): Compose Desktop 1.7.3, solo con `EURISE_DESKTOP=1`; finestra con la barra
  laterale e le sezioni vuote, Perla/Notte dal tema di GNOME o KDE, Ponte dentro (QR nella finestra se non c'è un telefono
  abbinato), stesso programma per i comandi del Ponte. Colori copiati da `core/designsystem` finché non è multipiattaforma.
  Chiudendo la finestra il Ponte si ferma.
- **Flatpak** `eu.eurise.Eurise` (Freedesktop 25.08 dal 20 novembre: la 24.08 non riceve più correzioni) con il programma compilato da Gradle (Java dentro); permessi per rete,
  notifiche, Download, native messaging di Firefox, profili dei browser in sola lettura.
- **Compila**: lavori «Eurise per il computer» (prove di `core/sync` e `core/ponte`, `createDistributable`, prova d'avvio
  con Xvfb su Ubuntu 24.04, Ubuntu 22.04 e Fedora) e «Flatpak di Eurise»; `apk-prova` pubblica `eurise-linux.tar.gz` e
  `eurise-linux.flatpak`.

## Tavole
Tela (https://claude.ai/artifact/AauRJ8e5WdH7nXTqAEXkoo), pagina **«Eurise sul computer»**, 1440×900 su Ubuntu: barra
laterale con le sezioni (Oggi, Calendario, Note, Attività, Email, Mappe, Alfred), ricerca su tutta la suite (Ctrl K),
Nuovo, Alfred (Ctrl Spazio), stato del Ponte e centro privacy in fondo.
- **Approvate il 16 novembre** (tutte): PcOggi, PcOggiNotte, PcCalendario, PcNote, PcAttivita, PcEmail, PcMappe (con
  CoMaps: gli stessi file del telefono, offline), PcAlfred, PcFirefoxScheda (dell'area Browser, già scritta).
- Pagina Browser: PcFirefox (approvata, scritta), Ponte (approvata). Pagina AI: AlfComputer (Alfred con il computer come
  cervello).
- Da disegnare quando servono: importazione dai browser nell'app (partire da BrwImporta), icona nella barra.

## Prossimi passi (in ordine)
1. **Oggi**: provare la schermata (16 novembre, sotto) e correggere ciò che segnala il proprietario. Restano: spuntare
   un'attività dal computer (serve un tipo del Ponte «fatta»), meteo (servizio da scegliere, spento di base), note
   recenti ed email importanti (con le loro sezioni), proposte di Alfred, ricerca Ctrl K, Impostazioni.
2. **Calendario e Attività** su CalDAV (`core/sync`), con il collegamento in due tocchi dal browser.
3. **Note** su WebDAV con l'unione a tre vie.
4. **Email** (IMAP/SMTP, `MailClient`).
5. **Mappe** con CoMaps per Linux (versione desktop in Qt: da capire come integrarla nella finestra o accanto).
6. **Alfred** (PcAlfred, poi AlfComputer: il computer come cervello via Ponte).
7. Icona nella barra (il Ponte resta acceso a finestra chiusa) e avvio con la sessione.
8. Flathub: compilazione dai sorgenti (dipendenze Maven scaricate prima), metainfo e schermate.
9. Spostare in comune ciò che serve a entrambi (colori di `core/designsystem`, NoteMerge, categorie dell'Email) quando
   diventa multipiattaforma, scrivendolo qui.

## Come si lavora
- Ramo `brand` come le altre chat: prima di ogni push `git fetch` e rebase su `origin/brand`. Per un giro tutto proprio
  si spinge la stessa testa anche su `prova-computer` (la `concurrency` di «Compila APK» è per ramo), poi si pubblica quel
  giro: `gh workflow run apk-prova.yml --ref brand -f run_id=<giro di prova-computer>`.
- Si compila solo su GitHub Actions; l'app desktop entra nella compilazione solo con `EURISE_DESKTOP=1` (il lavoro
  «Eurise per il computer» lo imposta). Prove: `:core:sync:test`, `:core:ponte:test`, prova d'avvio su Ubuntu 24.04,
  22.04 e Fedora (si chiude o scrive un'eccezione → il giro fallisce).
- Link da dare a ogni consegna (diretti, dalla pre-release `apk-prova`):
  https://github.com/eurise-suite/eurise/releases/download/apk-prova/eurise-linux.flatpak e
  https://github.com/eurise-suite/eurise/releases/download/apk-prova/eurise-linux.tar.gz; più l'APK del telefono
  (`app-debug.apk`) se è cambiato anche lì.
- Provare: Flatpak con `flatpak install --user eurise-linux.flatpak` e `flatpak run eu.eurise.Eurise`; archivio:
  estrarre e lanciare `eurise/bin/eurise`.
- Parti comuni (core/designsystem, core/ponte, CLAUDE.md, canvas.json della tela) si toccano solo se serve, e si scrive qui.
- A ogni consegna: aggiornare questo file e scrivere una riga in `docs/STATO.md`.

## Giorni

### 16 novembre: Oggi dalle tavole PcOggi e PcOggiNotte · giro 657 (prova-computer, in apk-prova)
- `desktop/app`: `TodayScreen.kt` come la tavola (data, saluto con il nome dal sistema e «Oggi hai 4 impegni, il primo
  alle 11», «Scrivi come parli» con Evento/Da fare/Nota, tre colonne di schede, riga della privacy), Perla e Notte con lo
  stesso codice (riquadri delle icone = tinta stemperata nella scheda). Barra in alto con **Nuovo** (Ctrl N: Oggi con il
  cursore nel campo) e **Alfred** (Ctrl Spazio); Ctrl 1…7 per le sezioni; nella barra laterale il numero delle cose da
  fare oggi su Attività, «<telefono> collegato» e il Centro privacy con le dieci tacchette (solo indicazione per ora).
- Dati (`TodayFeed.kt`, `TodayData.kt` con `TodayDataTest`): dal socket locale del Ponte, come Firefox. Appuntamenti e
  cose da fare dal riassunto del telefono (`today-ask` al massimo ogni 2 minuti e solo con Oggi in vista; l'ultimo resta
  in memoria 6 ore, mai su disco); «Dal telefono» dalle ultime cose passate (`recent`; un link si riapre con un clic,
  «Continua qui»). Telefono senza «Oggi sul computer» acceso o fuori casa: lo si dice nella scheda.
- «Scrivi come parli»: Invio manda al telefono con il Ponte (in coda se non è in casa). Evento: giorno e ora letti con la
  stessa `QuickAdd` del telefono (compilata da core/data così com'è, `quickAddSources` in `desktop/app/build.gradle.kts`,
  più `TaskStub.kt` con le tre costanti), anteprima sotto il campo; senza ora è tutto il giorno, con l'ora dura un'ora. Da
  fare e Nota: il testo com'è (il telefono lo legge come sempre).
- Non ancora, scritto nella schermata senza dati inventati: note recenti, email importanti; meteo e proposta di Alfred
  tolti finché non c'è una fonte. Le caselle delle attività si vedono ma non si spuntano. Finestra con le decorazioni
  del sistema (i tre pulsanti della tavola li disegna GNOME).
- Compila: il lavoro «Eurise per il computer» lancia anche `:desktop:app:test`.

## Sicurezza (18 novembre 2026, notte, progetto Eurise Sicurezza)
- Ponte sul telefono: collegare un computer solo dal lettore QR dell'app Eurise (prima qualunque app poteva collegarne uno
  in silenzio); chiave privata del telefono sigillata nel Keystore, senza rifare l'abbinamento. Da fare (punto 8 di
  `docs/SICUREZZA.md`): chiavi effimere per sessione. Dettagli in `docs/SICUREZZA.md`.
- 19 novembre (Sicurezza): Ponte v2 con chiavi di sessione sulla rete di casa (telefono e computer aggiornati insieme;
  con un lato vecchio resta la v1). Dettagli in `docs/SICUREZZA.md`.

## Sicurezza, 20 novembre 2026 (progetto Eurise Sicurezza)
Ponte: risponde solo dalla rete di casa e dalle VPN private (`Peers`), al massimo 32 collegamenti, 10 s e 16 KB per
presentarsi; da Firefox niente percorsi di file; `wtype -` e `notify-send --`; nome del telefono ripulito. Flatpak su
Freedesktop 25.08. Da fare con quest'area: chiave del Ponte nel portachiavi del sistema (Secret Service). Dettagli in
`docs/SICUREZZA.md`, sezione «Computer».

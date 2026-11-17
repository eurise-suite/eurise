# Stato dell'area Alfred e AI

File di stato dell'area, tenuto dalle chat del progetto «Eurise · Alfred e AI» (dal 16 novembre 2026). `docs/STATO.md` resta il
diario generale: lì si scrive solo una riga per consegna che rimanda qui. Regole in `CLAUDE.md`, sistemi in
`docs/PIATTAFORME.md`. Ramo `brand`; giri propri su `prova-alfred`.

## Cosa comprende
Alfred, Eurise AI (cervello sul telefono e sul computer), Voce (Kokoro, dettatura), Leggi (testo dalle immagini).

## Fatto (in breve, al 16 novembre; ultimo giro in `apk-prova`: Compila 760, Mappe 102)
- **Alfred unico** (`feature/assistant`, app `alfred/AlfredActivity`, ASSIST e VOICE_COMMAND): maggiordomo che dà del Lei,
  ironia britannica (una battuta su tre dopo un'azione riuscita, mai alla guida), appellativo scelto dall'utente. Tre tasti
  nel campo (PC · online col preferito e la stellina · Invio sotto il pollice, anche per mancini). Parla solo se gli si è
  parlato. Prima scelta di Ricerca AI nella dock della Scrivania. `AlfredPrefs` nel backup cifrato.
- **Come capisce**: regole esatte (`Grammar`, anche `Grammar.guess` come seconda lettura con scheda di conferma) → cervello
  sul telefono → online. Più azioni in una frase: «Ho capito N cose» con «Fai tutto». Dopo un comando a voce «Procedo?» con
  sì/no a voce.
- **Passo 1, domande sui dati** (giro 615): «cosa ho domani», «quando ho il dentista», «chi mi ha chiamato», «cosa avevo
  scritto sul cancello»; cerca solo sul telefono (`Records`: Calendario, Attività, Note, chiamate), risposta scritta e a voce
  con proposte. Mancano i messaggi (l'APK principale non ha READ_SMS: arrivano col passo 3).
- **Passo 2, seguiti** (giro 628): «spostalo alle 10», «no, alle 11», «cancellalo», «annulla», «mandalo anche a Luca»
  (`FollowUp`, due minuti, solo in memoria); «Quale Marco chiamo?» a voce (`Butler.doubt`); correzioni a mano delle parole
  ricordate (`AlfredMemory.Kind.Word`).
- **Alfred ricorda** (giro 600, `core/data/alfred/AlfredMemory`, nel backup cifrato): persona scelta per un nome, app abituale
  per persona, «ricorda che…»; usata nella ricerca delle persone e come `hints` del cervello; voci con × in «Chi risponde».
- **Eurise AI** (`ai/`, APK `eu.eurise.ai` **senza permesso di rete**, llama.cpp dai sorgenti in `third_party/`):
  `BrainService` con permesso di firma (`core/suite/BrainContract`): installa il GGUF passato da Eurise, comandi con
  grammatica GBNF (fino a tre righe `tipo|chi|testo|data|ora|canale`, tipo `dati`), risposta libera in streaming
  (`askStream`). Cervelli scaricati solo con Wi-Fi da huggingface.co (`BrainDownload`): **Rapido** Granite 4.0 1B (circa
  0,8 GB), **Completo** Ministral 3 3B (circa 2,5 GB), entrambi Apache 2.0. Controlli fissi dopo il cervello
  (`BrainCommands`: date e ore da QuickAdd, niente nomi, canali, testi o azioni inventati). Misure in CI
  (`.github/scripts/ai_commands.py`): Ministral 12/15 in circa 5 s, Granite 12/15 in circa 2,9 s. Prima misura sul
  Fairphone 6 (prototipo): 41,9 token/s in lettura, 7,4 in scrittura.
- **Chi risponde** (`AlfredPrefs.answers`): prima il telefono / subito online / solo telefono; risposta dal telefono che si
  scrive mentre pensa, «Chiedi a <preferito>».
- **AI online** (deciso il 14 novembre: niente conto Eurise né inoltro): il globo apre la **finestra AI**
  (`online/AiWindowActivity`) sul servizio preferito col suo account, domanda ripulita dai dati personali (`Redactor`, con
  segnaposto) e scritta nel campo, Invio all'utente, «Porta ad Alfred» riprende la risposta. Lumo (8 tacchette), Euria (7),
  Le Chat (6, avviso sull'addestramento), «Aggiungi»; servizi chiusi in fondo. Servizio `PrivacyService.AiOnline` spento
  di base.
- **Alfred ovunque**: «Il punto» della giornata (`Briefing.kt`, tavole AlfGiornata e AlfSera), sveglia di Alfred
  (AlfSveglia: saluto, frase, brano dal catalogo in `core/data/alfred`), voce di Alfred nella guida di Mappe (AlfMappe),
  «Alfred, autovelox / c'è una coda» che segna in Mappe, microfono di Alfred in Android Auto (anche in guida e nella ricerca,
  con «Ti ascolto» che mostra ogni passo: lavoro dell'area Mappe, 16 novembre).
- **Voce** (`voice/`, APK separato, per l'utente «Alfred · voce e modelli»): riconoscimento con Whisper
  (`TranscribeService`, `core/suite/VoiceContract`; Alfred registra da sé con `AlfredEar` e `Endpoint`, fine frase dopo
  700 ms; modello tenuto in memoria mentre il pannello è aperto, contesto 768; volume normalizzato contro i «Mi scusi?»);
  dettatura a pezzi nelle pause per la Tastiera; motore di sintesi di sistema (`AlfredVoiceService`, sherpa-onnx). Voci:
  **Nicola (Kokoro) è la voce di Alfred** (116 MB, si scarica se manca), Sara, Riccardo e Paola (Piper), Riccardo ritoccato
  (velocità e tono), «Carica una voce dal telefono». Senza Voce, Alfred usa il riconoscimento e la voce del telefono.
- **Leggi** (`read/`, APK separato scaricato a richiesta): Tesseract compilato dai sorgenti (`eurise_ocr`, 99,5–99,9 % in
  CI), `ReadService` con permesso della suite (`core/suite/ReadContract`), lingue scaricabili (italiano, inglese);
  traduzione dentro Leggi (`TranslateService`, 14 lingue via inglese, chrF it→en 82,8). Usata da Scanner (testo, nome
  proposto, Da fare con scadenze e importi, PDF cercabile), carte di Password (`ScanCardActivity`), Viaggi, Telefono e
  Messaggi (Traduci). Leggi non è un lettore di libri: niente epub.
- **Centro privacy**: voci Voce, Traduzione, Lettura dei documenti, AI online, permesso Chiamate per Alfred.

## 17 novembre: Alfred più svelto (giro 739)
- Prova del proprietario: Nicola più naturale ma «estremamente lento nel parlare»; Alfred deve far prima, non far
  aspettare che parli. Fatto:
  - **Dice solo l'essenziale**: conferme in una domanda sola (`Butler.spokenConfirm`: «Chiamo Marco?», «Lo annoto?»,
    «Sveglia alle 7:00?», stringhe `alfred_say_*`) al posto di frase della scheda + «Procedo?»; dopo un'azione solo l'esito
    (`Outcome.spoken`), la battuta resta scritta; risposte del cervello e domande sui dati a voce solo nelle prime due frasi
    (`SpokenText.brief`, test `SpokenTextTest`), «Rileggi» legge tutto. Il pannello si chiude prima (al massimo 2,5 s).
  - **Mai in attesa della voce**: toccare la scheda la zittisce; la voce si apre appena si comincia a parlare, così in
    Eurise Voce il modello è già caricato e scaldato (frase di prova all'avvio del motore) quando serve.
  - **Nicola più svelto**: Kokoro al passo 1,15 (prima 0,95, `KOKORO_PACE`, vale anche per la guida di Mappe); il primo
    pezzo si taglia alla prima virgola (`SpeechChunks`, test `SpeechChunksTest`), così la prima parola arriva prima e il
    resto si genera mentre parla.
- **Da provare**: tempo dalla fine della frase detta alla prima parola di Alfred; il passo di Nicola (se ancora lento o
  troppo svelto si ritocca `KOKORO_PACE`, o si dà il cursore Velocità anche a Nicola in Voce, oggi solo per Riccardo).
- Se Kokoro resta lento a generare sul telefono: proposta di usare Riccardo (Piper, molto più leggero) per le conferme
  brevi e Nicola per le risposte lunghe, oppure ridurre i thread o provare il modello Kokoro solo italiano/inglese.

## 17 novembre: l'ironia anche a voce (giro 754)
- Il proprietario: «Mi raccomando, l'ironia!», «mettine parecchie, di battute, massime, proverbi, citazioni», «deve essere
  uno spasso da ascoltare pur rimanendo molto professionale». Fatto:
  - **89 battute** dopo le azioni (`alfred_maxims_*`: appuntamento, nota, attività, messaggio, strada, sveglia, timer,
    «ricorda», seguiti): proverbi, motti latini, citazioni (Shakespeare, Franklin, Luigi XVIII), ironia da maggiordomo.
    Brevi, **dette anche a voce** dopo l'esito, **una volta su due**, mai una delle ultime sei (`recentQuips`).
  - **Conferme ironiche** una volta su tre da elenchi di varianti (`alfred_say_wit_*`: «Disturbo Marco?», «Alle 6:00. Ne
    è proprio sicuro?»).
  - Il pannello si chiude dopo la battuta (al massimo 2,5 s in più), così non la tronca.
- Regola per chi aggiunge battute: Lei sempre, niente volgarità, al massimo una decina di parole, citazioni solo se
  l'attribuzione è sicura (altrimenti come proverbio). Niente testi di canzoni (anche un verso): solo allusioni originali.

## 17 novembre, sera: Alfred spiritoso anche alla guida (Mappe 102, pubblicato con Compila 760)
- Il proprietario: «anche alla guida può essere simpatico… l'importante è che non confonda le istruzioni». Cambia la
  regola «mai alla guida» di Alfred unico: **in guida sì, ma solo nei momenti tranquilli**.
- `GuideWit` (logica, prova `GuideWitTest` nel lavoro di Mappe) e `EuriseWit` (aggancio) in
  `maps/android/…/eurise/`: partenza dopo 30 s se la prossima manovra è lontana; rettilineo con manovra oltre 4 km
  (400 m a piedi), una ogni 15 minuti al massimo; arrivo una volta, 4 s dopo l'annuncio della guida. Mai vicino a una
  manovra; solo con la voce della guida accesa e se Alfred non sta già leggendo (`EuriseSpeak.busy`).
- Partenza e rettilineo **senza parole di direzione** (filtro `GuideWit.safe` e prova sulle stringhe
  `eurise_wit_*`); all'arrivo sì: «Arrivati. Destra o sinistra, ormai, poco importa.» (cenno a Gaber senza citarlo).
- **File di Mappe toccati** (area Mappe avvisata in `docs/stato/MAPPE.md`): `maps/eurise_patch.py` (una riga in
  NavigationController), `EuriseCarGuide.java` (una riga), `EuriseSpeak.java` (`busy()`), stringhe di Mappe,
  `.github/workflows/maps-engine.yml` (prova in più).
- Da provare: che non parli mai sopra le istruzioni; se 15 minuti tra una battuta e l'altra sono troppi o pochi.
- Da provare: se una su due è troppo (o troppo poco); `withMaxim` in `Plan.kt`.

## 17 novembre, notte: una voce che sembri italiana (in corso)
- Il proprietario: Nicola «sembra che non sia italiano» (Kokoro pronuncia l'italiano con espeak-ng). Candidati liberi
  che girano sul telefono, da far **ascoltare prima di metterli nell'app** (lavoro `voci-ascolto.yml`, script
  `.github/scripts/voci_ascolto.py`, pre-release `voci-ascolto`, stesse frasi per tutti):
  - **Supertonic 3** (99M, ONNX, 31 lingue con l'italiano, 5 voci maschili e 5 femminili; RTF 0,2 sul server): pesi
    OpenRAIL-M (limiti d'uso, non libera in senso stretto) e progetto chiuso dall'autore (Supertone).
  - **Piper Serena** (medium e high, CC BY 4.0, italiano standard senza accento, stesso motore di Paola e Riccardo:
    si aggiunge subito): voce **femminile**; il suo checkpoint permette di addestrare un Alfred maschile italiano.
  - **Pocket TTS** di Kyutai (100M, CC BY 4.0, modello italiano con 26 voci tra cui una «giovanni»; in sherpa-onnx
    c'è il motore): campione non ancora fatto, serve il runtime ONNX della comunità (KevinAHM/pocket-tts-onnx) nel
    lavoro, e lanciare codice di terzi va approvato dal proprietario.
- Prossimo: il proprietario sceglie a orecchio; la voce scelta entra in `voci.yml` e in Voce (`Voices.kt`).

## Tavole (tela, pagina «AI»; per il computer pagina «Eurise sul computer»)
- **Approvate e in codice**: AiCattura, AiModelli, AiAttiva; AstAscolto, AstConferma, AstMessaggio, AstLibera,
  AstImpostazioni (nella voce di Alfred); AlfGiornata, AlfSera, AlfSveglia, AlfMappe; AlfUnico, AlfUnicoMancino, AlfScegli,
  AlfFinestra, AlfRisposta, AlfDove; AlfTelefono, AlfCapisce, AlfCervello; AlfDati, AlfSeguito, AlfQuale.
- **Approvate, codice da fare**: AlfAzioni (passo 3), AlfAvvio (passi 4 e 5); **PcAlfred** (Alfred sul computer,
  approvata il 16 novembre con le tavole del computer).
- **Da confermare**: AlfComputer (il PC come cervello via Ponte, 14 novembre): l'idea è approvata («Alfred unico»), ma il
  diario non registra l'approvazione della tavola; oggi il tasto PC dice che arriva col Ponte.

## Prossimi passi, in ordine
1. **Prove del proprietario su Compila 760 e Mappe 102** (Alfred più svelto, ironia a voce e in guida, vedi sopra) (Eurise, Eurise AI, Voce): passi 1 e 2 («cosa ho domani?», «spostalo alle 10»,
   «annulla», «Quale Marco?»), cervello Rapido e Completo scaricati e misurati sul telefono (tempi e frasi capite), Alfred
   ricorda, Nicola come voce. Sentire se Alfred ora capisce (la prova del giro 548 diceva «il 90 % delle volte capisce una
   cosa per un'altra»).
2. **Passo 3, azioni nuove** (tavola AlfAzioni), una per volta e con test: prima leggi e rispondi ai messaggi dall'APK
   Messaggi (completa «chi mi ha scritto?»); poi sposta/cancella impegni, spunta attività, ferma timer e sveglie, torcia,
   non disturbare, volume, modalità, musica, «quanto manca» in guida.
3. **Passo 4** (AlfAvvio): Eurise come app assistente di Android (ROLE_ASSISTANT), riquadro nella tendina, dalla schermata
   di blocco; «Di' Alfred» con le parole chiave di sherpa-onnx, spento di base con l'avviso sulla batteria (misurarla).
4. **Passo 5**: registro locale delle ultime 100 richieste (`eurise_alfred_log`, fuori dal backup, spento di base, «Mandalo
   a chi sviluppa…», «Cancella»): serve anche a capire se sbaglia l'ascolto o la comprensione.
5. Dalla tavola AlfTelefono: azione utile proposta dalla risposta libera (es. «Timer 9 min»).
6. Alfred ricorda nel centro privacy (serve prima un elenco dei dati locali dove agganciarlo).
7. **Il PC come cervello** (AlfComputer, PcAlfred): col Ponte, modello scelto sulla scheda video, risveglio dalla rete;
   insieme all'area Computer (`docs/stato/COMPUTER.md`).
8. In attesa: Lumo con l'API ufficiale quando esce (oggi la finestra AI col suo account); musica classica scaricata per la
   sveglia (servono fonti verificate); icona di Eurise AI (provvisoria).

## Come si lavora
- Codice: Alfred in `feature/assistant` (e `app/…/alfred/`), Eurise AI in `ai/` (llama.cpp in `third_party/`), Voce in
  `voice/`, Leggi in `read/` (Tesseract e traduzione in `read/src/main/cpp`), contratti in `core/suite` (`BrainContract`,
  `VoiceContract`, `ReadContract`, `TranslateContract`), memoria e catalogo in `core/data/alfred`.
- Prove senza telefono: `GrammarTest`, `BrainCommandsTest`, `FollowTest`, `DataQuestionsTest`, `Endpoint`; misura del
  cervello in CI con `.github/scripts/ai_commands.py` (frasi → comandi, ogni frase nuova va aggiunta lì); qualità di Leggi
  con `.github/scripts/ocr_quality.py`.
- Compilazione: «Compila APK» (`build.yml`) parte a ogni push. Prima di spingere `git fetch` e rebase su `origin/brand`
  (altre chat lavorano sullo stesso ramo); poi si spinge la stessa testa anche su `prova-alfred`, così i giri delle altre
  chat non lo annullano, e si pubblica quel giro: `gh workflow run apk-prova.yml --ref brand -f run_id=<id del giro su
  prova-alfred>`.
- In chat sempre i link diretti degli APK cambiati, con nome e giro:
  - Eurise (Alfred): https://github.com/eurise-suite/eurise/releases/download/apk-prova/app-debug.apk
  - Eurise AI: https://github.com/eurise-suite/eurise/releases/download/apk-prova/ai-debug.apk
  - Voce: https://github.com/eurise-suite/eurise/releases/download/apk-prova/voice-debug.apk
  - Leggi: https://github.com/eurise-suite/eurise/releases/download/apk-prova/read-debug.apk
- `apk-prova` pubblica solo gli APK riusciti nel giro scelto: se un lavoro fallisce (es. «Eurise Leggi» al giro 659 per il
  plugin KSP non scaricato, errore di rete), il suo APK sparisce dai link (404). Si rilancia solo quel lavoro
  (`gh api -X POST repos/eurise-suite/eurise/actions/runs/<id>/rerun-failed-jobs`) e si ripubblica lo stesso giro.
- Voci e modelli si scaricano a richiesta (pre-release `voci`, lavoro `voci.yml`; cervelli da huggingface.co), mai dentro
  l'APK. Ogni servizio che fa uscire dati è spento di base e compare nel centro privacy.
- Parti comuni (`core/designsystem`, `core/ponte`, `CLAUDE.md`, `canvas.json` della tela): si toccano solo se serve e si
  scrive qui.
- A ogni consegna: aggiornare questo file e una sola riga in `docs/STATO.md`.
- Storia completa: sezioni datate di `docs/STATO.md` dall'11 al 16 novembre (Eurise AI, Assistente/Alfred, Voce, Leggi).

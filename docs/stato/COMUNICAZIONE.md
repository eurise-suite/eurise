# Stato dell'area Comunicazione

File di stato dell'area, tenuto dalle chat del progetto «Eurise · Comunicazione» (dal 16 novembre 2026). `docs/STATO.md` resta il
diario generale: lì si scrive solo una riga per consegna che rimanda qui. Regole in `CLAUDE.md`, sistemi in
`docs/PIATTAFORME.md`. Ramo `brand`; giri propri su `prova-comunicazione`.

## Cosa comprende
Email, Chat (Matrix), Messaggi (SMS), Telefono, Rubrica, anche da lavoro. Collegati, ma di altre aree: la bolla «Ti hanno
cercato» e le suonerie (Scrivania, area Organizzazione), «leggi e rispondi ai messaggi» di Alfred (area Alfred), Email sul
computer (area Computer, tavola PcEmail).

## Fatto (in breve, al 16 novembre)
- **Telefono** (`phone/`, `eu.eurise.phone`, giro 237, poi TelPillola al giro 342): ruolo Telefono proposto al primo avvio;
  Recenti con «Da richiamare», preferiti, tastierino con ricerca per nome (T9), numeri bloccati, togli dai recenti.
  Chiamata: «Prima di rispondere» con gli impegni dal Calendario, nota della chiamata che va in Note (`HubBridge`), muto,
  tasti, vivavoce, attesa, scambia e unisci, rifiuta con un messaggio, prossimità, schermo intero per quelle in arrivo.
  Pillola satinata (`EuriseDock`): Tastierino, Preferiti, Recenti (con la carrellata dei preferiti in cima), Rubrica, «+»
  per un contatto nuovo; la nota della chiamata ha lo stile di «Prima di rispondere».
- **Rubrica**: seconda icona dello stesso APK (`ContactsAlias`), riceve «Aggiungi in rubrica» dalle altre app; scheda con
  Chiama, SMS, WhatsApp, WhatsApp Business (distinti), Telegram, Email, impegni insieme, editor, elimina, blocca. È la
  rubrica di sistema. Comune con Messaggi: `core/contacts` (`Reach`, `Avatar`, `DefaultApps`).
- **Messaggi** (`messages/`, `eu.eurise.messages`): ruolo SMS; elenco Persone / Codici e consegne / Avvisi, codice in alto
  da copiare che si cancella dopo 10 minuti; «Capito sul telefono» (appuntamento → Crea l'evento, in evidenza quando
  l'altro conferma; numero → Chiama/SMS/WhatsApp/Telegram/Aggiungi; indirizzo → Mappe; Da fare; Note; Traduci con Leggi;
  messaggi sospetti segnalati). MMS con foto (codice AOSP in `third_party/aosp-mms`), invio programmato, notifiche con
  Rispondi/Letto/Copia il codice, risposta rapida durante le chiamate. Logica pura con test: `core/data/messages`
  (`MessageInsights`).
- **Email** (`mail/`, `eu.eurise.mail`, dal giro 310): caselle con indirizzo e password, server trovati da soli
  (`Providers.kt`, poi autoconfig di Thunderbird), password nel Keystore, posta unificata Persone/Avvisi/Newsletter,
  lettura senza JavaScript e senza rete finché non tocchi «Mostra», controllo ogni 15 minuti con notifica solo per le
  persone. Servizi chiusi (Gmail, iCloud, Yahoo) per ultimi con password per le app e avviso; Outlook aspetta l'OAuth;
  Proton/Tuta spiegati (serve il Bridge); Infomaniak con la verifica in due passi spiegata. Con la suite: proposte da
  `MessageInsights` (Calendario, Da fare, Note, Rubrica, Mappe, avviso truffa), allegati Apri / Salva in Drive, Scrivi con
  allegati da Drive, condivisione da ogni app, «Tienila anche in Eurise Password». Lettura con la pillola satinata
  (Rispondi, Inoltra, Archivia, Sposta in…, Elimina, Spam).
- **Chat** (`chat/`, `eu.eurise.chat`): Matrix con client HTTP nostro (`ChatClient`), accesso SSO dal browser o password,
  elenco, inviti, conversazione, gruppi, notifiche ogni 15 minuti. Cifratura end-to-end con crypto-android
  (matrix-sdk-crypto, come Element), APK solo arm64. Chiave di recupero compatibile con Element (`SecretStorage`), chiavi di
  firma, copia cifrata delle chiavi sul server, chiave nel backup della suite (`SecretsContract`), in Eurise Password
  (`PasswordContract`) o su carta con QR. Verifica a figure (SAS) di un proprio dispositivo e di una persona dalla chat,
  elenco dei dispositivi con «Esci».
- **Suonerie per persona e per app** (approvate e in codice il 14 novembre, nella Scrivania › Griglia e icone › Suoni):
  suoneria del contatto (`CUSTOM_RINGTONE`), suono al posto di WhatsApp/Telegram tramite l'ascolto delle notifiche.
- **Spese di gruppo in Chat** (17 novembre, giro 713; tavole SpeGruppo, SpeAggiungi, SpeSaldi): nei gruppi la scheda
  Spese (totale, la tua parte, chi ha speso e saldo, ultime spese), «Nuova spesa» (chi ha pagato; uguale, quote, importi, %),
  «Chi deve a chi» con i pagamenti minimi, Ricorda, Pagato. Ogni spesa/pagamento è un messaggio cifrato della stanza
  (`msgtype` `eu.eurise.expense` / `eu.eurise.settlement`, con `body` leggibile da Element X e FluffyChat); vale l'ultima
  versione di una spesa, solo dell'autore. Logica pura in `chat/.../Expenses.kt` con `ExpensesTest`; interfaccia in
  `ExpensesUi.kt`; per i conti si legge tutta la storia della stanza (`ChatModel.loadExpenses`, al massimo 200 pagine).
  Non ancora: scontrino letto con lo Scanner, IBAN o link di pagamento, correggi/togli una spesa, spese dall'email.
- **Studio medico, passo 1** (17 novembre, tavole StuSpazio e StuPaziente; giro 718): modulo nuovo `core/studio` (logica pura
  con `TriageTest` e `StudioTest`, nessuna dipendenza): pazienti, consensi con la storia (la revoca vale subito e resta
  scritta), terapia abituale, smistamento sul telefono senza AI (`Triage`: urgenze in cima con parole scelte, ricette anche
  dai nomi della terapia abituale, appuntamenti, referti, domande, grazie; medicina generale o agopuntura), codici di
  verifica (`Verification`, 30 minuti, lettere che non si confondono), coda (`Queue.receive`/`summary`), JSON
  (`StudioCodec`), contratto tra APK (`StudioContract`: `offer`, `known`, `openIntent`).
  Chat: lo Studio vive qui, **spento di base** (ingresso dal foglio dell'account; acceso, icona in alto con il pallino
  delle urgenze). Archivio cifrato con chiave AES del Keystore (`StudioRepo`, `studio.bin`), mai all'avvio sul thread
  principale. Spazio Studio (Tutto / Medicina generale / Agopuntura, «Da guardare prima», da fare per bisogno, da dove
  arrivano), coda di un bisogno (Rispondi dal canale d'origine, Scheda, Fatto / Rimetti in coda), pazienti (cerca, verificato
  o da verificare), scheda (verifica, canale preferito, indirizzi, consensi con interruttori e storia, terapia, storia degli
  scambi, togli dallo Studio), modifica. **Verifica in studio**: QR con `https://matrix.to/#/<medico>` (apre la chat con lo
  studio, va bene Element) + codice che il paziente scrive in Chat, email o SMS: quel canale diventa suo e la scheda diventa
  verde da sola; in Chat arriva una conferma automatica. Dalla chat a due: icona dello Studio (scheda o «Aggiungi allo
  Studio» già compilato). I messaggi dei pazienti entrano in coda dal ciclo di /sync, dai messaggi decifrati dopo e da
  `ChatWorker` ad app chiusa (solo quelli dopo l'accensione). `StudioProvider` risponde solo con il permesso di firma e
  lo Studio acceso.
  Email: `MailModel.refresh` e `SyncWorker` offrono le email non lette dei pazienti (oggetto); aprendole va il testo intero
  (la richiesta si aggiorna e si rismista); segno «Paziente» nell'elenco, «Apri nello Studio» nella lettura.
  Messaggi: `SmsDeliverReceiver` offre ogni SMS (la Chat tiene solo pazienti e codici).
  Non ancora (passi successivi): risposta automatica alle urgenze con 112 e guardia medica (passo 5, regole), segno
  «Paziente» in Messaggi, segreteria, ricette con Millewin (passo 2), referti (passo 3), lo Studio nel backup cifrato
  della suite (oggi solo sul telefono: allowBackup spento).
- **Icone nuove degli APK** (16 novembre, serie colorata): Email, Chat (smeraldo), Messaggi (oltremare), Telefono, Contatti.
- **Ultimo APK pubblicato** in `apk-prova`: chat, mail e messages giro 718 (17 novembre, Studio passo 1); phone del
  16 novembre.

## Tavole
Tela: https://claude.ai/artifact/AauRJ8e5WdH7nXTqAEXkoo
- **Approvate e in codice**
  - pagina «Mappe, Messaggi e Telefono»: MsgElenco, MsgConversazione, TelRecenti, TelChiamata (11 novembre, con le aggiunte
    del proprietario), TelPillola (12 novembre, con le due correzioni).
  - pagina «Chat ed Email»: MailPosta, MailLettura, MailScrivi, MailCaselle; ChatElenco, ChatConversazione, ChatGruppo,
    ChatAccesso; ChatRecupero, ChatVerifica (12 novembre).
  - Scrivania (area Organizzazione): BolRispondi, BolAllega, BolComunica, BolComunicaApp; pagina «Suonerie e sfondo»:
    SuoSuonerie, SuoTaglia, SuoRegole.
- **Approvate, codice da fare**: ChatChiamata (chiamate e videochiamate: nel codice non c'è nulla), ChatAllega (oggi la
  Chat mostra immagini e file ricevuti ma non li manda); MsgConversazione per la parte «Disponibilità e posizione» nella barra; **PcEmail** (pagina «Eurise sul
  computer», approvata il 16 novembre: schermate da scrivere insieme all'area Computer, IMAP/SMTP già in `core/sync`).
- **Da approvare** (pagina «Chat ed Email», 14 novembre, notte): fila «Chat al lavoro» con **ChatLavoro**, **ChatVideo**,
  **ChatCondividi**; fila «Email da lavoro» con **MailSmart**, **MailInsieme**, **MailScriviLavoro**. Documento collegato
  «Mail e Chat da lavoro · proposta, guida KMail, posta nostra»: https://claude.ai/artifact/F7uLSEo2KPWyaYBFtNCWJe
- **Da approvare** (pagina «Chat ed Email», 16 novembre, sera; richiesta del proprietario):
  - le otto idee sono già **approvate come idee** dal proprietario (nella chat Organizzazione, 16 novembre, notte tarda);
    qui si approvano le tavole. Il codice si divide con l'area Organizzazione (Calendario, Attività, Note, Drive);
  - fila «Lavorare insieme»: **InsRuoli** (persone e ruoli: capo, segreteria, collaboratori, colleghi, famiglia, amici, con
    cosa vede ognuno di base; da qui partono le proposte), **InsCondividi** («Condividi con…» per appuntamento, nota, cosa da
    fare, elenco, cartella: Vede/Modifica, arriva in Chat o per email, dal tuo cloud), **InsAffida** («Affida a…» con
    scadenza, una riga, ritorno se non risponde, avviso a fatta), **InsAssistente** (agenda con la segreteria: vede i liberi o
    i titoli di lavoro, mai il personale; propone, confermi con un tocco), **InsElenchi** (spesa, casa, ufficio: chi lo
    prende, chi l'ha fatto), **InsRiunione** (nota condivisa, @persona → attività assegnata), **InsDisponibilita**
    (orari liberi in un link, l'appuntamento nasce nei due calendari), **InsPalla** («Passa la palla» da email, messaggio o
    documento, con lo stato), **InsSquadra** (cartella di squadra in Drive: chi c'è, cosa è cambiato da ieri);
  - fila «Spese di gruppo in Chat»: **SpeGruppo** (totale, quota, saldo di ognuno), **SpeAggiungi** (scontrino letto,
    chi ha pagato, divisione uguale/quote/importi/%), **SpeSaldi** (chi deve a chi con i pagamenti ridotti al minimo,
    IBAN o link di pagamento, «Segna come pagato»).
  - Generatore delle tavole: `/home/claude/tela/gen/ins.py` (fuori dal repo, la tela resta la fonte). Toccato `canvas.json`
    della tela solo per aggiungere le due file e i titoli.
- **Decisione del proprietario (17 novembre)**: queste funzioni ci sono **sia in Email sia in Chat**, con la stessa scheda
  (stesso modulo comune, cambia solo il trasporto: stanza Matrix o email con il link). La Chat deve diventare uno strumento
  di lavoro potentissimo e anche di organizzazione della famiglia e degli amici. Le tavole Ins* sono disegnate in un'app
  sola: prima del codice vanno mostrate anche nell'altra (Email ↔ Chat).
- **Idee e tavole approvate dal proprietario (17 novembre)** (pagina «Chat ed Email», cinque file sotto le
  Spese di gruppo; generatore `/home/claude/tela/gen/ins2.py` con `lib.py`, fuori dal repo):
  - **Email ↔ Chat**: InsStessaScheda (la scheda viva in Chat; mail con pulsanti e risposta «fatto» per chi non usa
    Eurise), InsPallaChat, InsDisponibilitaMail, SpeEmail;
  - **Famiglia**: FamFaccende (a turno), FamScadenze (da email o Scanner: promemoria in Attività o Calendario, documento
    conservato in Drive, chi paga, spese di casa; anche garanzie, ricevute e contratti con la data di fine), FamCena,
    FamScuola, FamArrivato, FamGenitori, FamDocumenti;
  - **Amici**: AmiUscita, AmiViaggio, AmiRegalo;
  - **Lavoro**: LavApprova, LavNoteSpese, LavStato, LavDecisioni, LavRiassunto, LavOspiti, LavRapporto, LavRicordami;
  - **Spazi**: SpaSpazi, SpaFuoriOrario, SpaTraduci.
  Ordine proposto per il codice: Spese di gruppo, Passa la palla, Approvazioni, Faccende a turno, Organizza un'uscita,
  Fuori orario; poi il resto. Ognuna in Chat e in Email.
- **Richiesta nuova (17 novembre): comunicare con i pazienti** (appuntamenti, richieste di farmaci, invio di referti),
  in Chat, Email e SMS. Proposta in chat, tavole dopo le risposte del proprietario (medico di medicina generale o
  specialista; gestionale usato). Vincoli trovati: NRE della ricetta dematerializzata via email, SMS o app (stabilizzato
  dal Milleproroghe); referti online facoltativi, con consenso, accesso protetto, al massimo 45 giorni e cancellabili
  dal paziente (linee guida del Garante 2009 e FAQ); compendio del Garante 2024 sulle piattaforme medico-paziente.
  Il proprietario è **MMG e agopuntore (privato)**, usa **Millewin** (in attesa di MilleWeb o di **Bussola MMG**, il
  gestionale nostro). Tavole da approvare, fila «Studio medico» (generatore `gen/ins3.py`): StuSpazio (coda di
  ricette, appuntamenti, referti, messaggi; urgenze in cima), StuAppuntamento, StuRicette (Eurise non fa la ricetta:
  raccoglie le richieste e rimanda il NRE), StuReferto (Chat cifrata a tempo 45 giorni o email con link e codice per SMS;
  consenso), StuPaziente (verifica con QR, consensi, terapia abituale), StuRegole (fuori orario con 112 e guardia medica,
  numero dell'ASL da inserire; segreteria senza referti), StuAgopuntura (sedute, ricevuta sanitaria, dati per il
  Sistema TS a chi li invia), StuMillewin (promemoria PDF in una cartella del cloud dello studio, letto e abbinato).
  Da fare prima di usarlo con i pazienti: informativa e consenso controllati da un esperto; server Matrix dello studio in UE.
  **Fila Studio approvata (17 novembre).** Note tecniche: i pazienti possono usare anche Element X, che vuole dal server
  il Simplified Sliding Sync (Synapse 1.114 e successivi) e accesso con password o OIDC nativo (non il vecchio SSO); i 45
  giorni dei referti: regola di conservazione per stanza su Synapse più cancellazione (redaction) dal nostro client. La
  cifratura protegge il contenuto, non i metadati (chi parla con chi, quando, nomi): per questo gli account dei pazienti
  vanno sul server dello studio (invito con QR), federazione chiusa o limitata per lo spazio Studio.
- **Studio, piano del codice (17 novembre)**, in quest'ordine, un giro per passo:
  1. **fatto il 17 novembre (vedi «Fatto»)**: pazienti e consensi (elenco locale, ruolo «paziente», canale, consensi con data, terapia abituale, verifica con QR)
     e lo spazio Studio con la coda;
  2. ricette: richieste dalla terapia abituale; promemoria PDF di Millewin letti da una cartella (`PdfText` di core/data
     da spostare in un modulo piccolo comune), NRE e codice fiscale, invio nel canale del paziente;
  3. referti: allegati cifrati in Matrix (serve prima l'invio di file, ChatAllega), cancellazione a 45 giorni
     (WorkManager + redaction), consenso; per email link Murena con password e scadenza (contratto con l'APK principale);
  4. appuntamenti: orari liberi dal Calendario, scelta, promemoria, lista d'attesa; 5. regole (fuori orario, parole urgenti,
     segreteria); 6. agopuntura (cicli, ricevuta PDF). Fino al server dello studio i pazienti passano da SMS ed email.
- **Server (domanda del proprietario, 17 novembre)**: niente dominio né voglia di gestire server. Opzioni trovate: etke.cc
  (Matrix gestito su Hetzner in Germania/Finlandia; prezzi sul modulo d'ordine; chiedere il contratto art. 28 GDPR);
  Element Pro (circa 5 $ per utente al mese: caro con i pazienti); VPS Hetzner CX23 (5,49 € + 0,50 € IPv4 + IVA, nuovi
  clienti limitati) o Infomaniak VPS Lite (in Svizzera, da CHF 2,70 al mese, non gestito). Proposta: Studio su etke.cc
  dopo il contratto; un server Eurise separato per i servizi della suite (UnifiedPush, pagine pubbliche, segnalazioni).
- **Come si farebbe (proposta, da confermare con le tavole)**:
  - condivisione dal cloud dell'utente: condivisioni di Nextcloud/Murena (lettura o modifica) per note, elenchi e cartelle,
    calendari condivisi su CalDAV, inviti iTIP per un appuntamento solo; chi non usa Eurise riceve un link o l'invito per email;
  - ruoli in Rubrica (etichetta della suite sul contatto) con i permessi di base in `core`, cambiabili persona per persona;
  - «Affida a», «Passa la palla», stato (affidata, vista, fatta) e spese come eventi strutturati nella stanza Matrix cifrata
    con la persona o il gruppo (email come ripiego); scadenze e ritorno con WorkManager;
  - segreteria: calendario Lavoro condiviso (liberi o titoli) e un calendario «Proposte» dove scrive lei; confermare sposta
    l'evento nel tuo;
  - disponibilità in un link: serve una pagina pubblica; verificare se Murena offre la prenotazione di Nextcloud Calendar
    («Appuntamenti»), altrimenti servizio piccolo nostro (spento di base, nel centro privacy);
  - spese: calcolo sul telefono (logica pura con test in `core/data`, pagamenti minimi), nessun servizio esterno.

## Prossimi passi, in ordine
0. **Studio medico**: passo 2, ricette (richieste dalla terapia abituale, promemoria PDF di Millewin da una cartella con
   `PdfText` spostato in un modulo piccolo comune, NRE e codice fiscale, invio nel canale del paziente); poi referti,
   appuntamenti, regole, agopuntura (piano qui sopra). Prove da chiedere: accendere lo Studio, aggiungere un paziente,
   verifica con Element sull'altro telefono, email e SMS di un paziente in coda.
1. **Tavole approvate il 17 novembre** («Tutto approvato»): le sei da lavoro del 14, le dodici del 16 e le venticinque del 17.
   Codice nell'ordine: Spese di gruppo, Passa la palla, Approvazioni, Faccende a turno, Organizza un'uscita, Fuori orario,
   poi il resto; ognuna in Chat e in Email. Decisioni ancora aperte: dove stanno gli strumenti del gruppo
   (cloud del gruppo o server Eurise); posta con un fornitore (Migadu, Purelymail, mailbox.org) o server nostro (Stalwart +
   Continuwuity + LiveKit); il dominio.
2. **Email da lavoro, prima parte** (dopo l'approvazione; tutto sul telefono): viste Per te / Aspetti / Squadra, il punto
   del mattino, risposte in un tocco, promemoria «se non risponde», invio all'ora giusta (MailSmart, MailScriviLavoro).
3. **Email, cose rimaste**: «Da fare» e scadenze trovate (bolletta), In breve, ricerca sul server, inoltro con gli
   allegati, MX per i domini personali (es. mx.infomaniak.ch); riassunto delle email importanti per la suite (serve a «Oggi»
   sul telefono, nel pannello di Firefox e sul computer). OAuth per Gmail/Outlook: passo una tantum del proprietario.
4. **Messaggi**: contratto per Alfred «leggi e rispondi» (passo 3 di Alfred, completa «chi mi ha scritto?»; l'APK
   principale non ha READ_SMS), Disponibilità e posizione nella barra, ricerca in tutto il testo, doppia SIM, MMS di gruppo.
5. **Chat**: invio di allegati (ChatAllega), chiamate (ChatChiamata, poi ChatVideo con MatrixRTC e LiveKit), UnifiedPush al posto del controllo ogni 15
   minuti, gruppi di lavoro (ChatLavoro, ChatCondividi, commenti della squadra alle email in una stanza legata al
   Message-ID), server Eurise se deciso.
6. **Email sul computer** (PcEmail) con l'area Computer.
7. **Nomi** (in attesa della verifica TMview/EUIPO, per ora in interfaccia restano i nomi dei servizi): Ciarly (Chat),
   Fidippo o Omodeo (Email, consigliato Fidippo), Bisbi (Messaggi), Chiara (Telefono), Rubens (Contatti).

Prove sul telefono mai tornate (da chiedere al proprietario quando prova): Telefono e Messaggi come app predefinite,
chiamata in arrivo a schermo spento, nota della chiamata in Note, rifiuta con messaggio, OTP, MMS con foto, «Crea l'evento»
dopo una conferma, Traduci; Email con una casella Murena/Libero; Chat con Element sull'altro telefono (cifratura, chiave di
recupero, verifica di una persona, QR stampato letto con lo Scanner); suoneria per persona con Eurise Telefono e WhatsApp
che suona con Eurise.

## Come si lavora
- Codice: `mail/`, `chat/`, `messages/`, `phone/` (con la Rubrica); in comune `core/contacts`, `core/studio` (Studio medico), `core/data/messages`
  (`MessageInsights`, con test), contratti in `core/suite`; per il computer `core/sync` (`MailClient`, `MailText`).
- Prima le tavole sulla tela (pagine «Chat ed Email» e «Mappe, Messaggi e Telefono»), poi il codice dopo l'approvazione.
- Compilazione: «Compila APK» (`build.yml`) parte a ogni push. Prima di spingere `git fetch` e rebase su `origin/brand`
  (altre chat lavorano sullo stesso ramo); poi la stessa testa anche su `prova-comunicazione`, così i giri delle altre
  chat non lo annullano, e si pubblica quel giro: `gh workflow run apk-prova.yml --ref brand -f run_id=<id del giro su
  prova-comunicazione>`.
- In chat sempre i link diretti degli APK cambiati, con nome e giro (mai la pagina della release né allegati):
  - Email: https://github.com/eurise-suite/eurise/releases/download/apk-prova/mail-debug.apk
  - Chat: https://github.com/eurise-suite/eurise/releases/download/apk-prova/chat-debug.apk
  - Messaggi: https://github.com/eurise-suite/eurise/releases/download/apk-prova/messages-debug.apk
  - Telefono e Rubrica: https://github.com/eurise-suite/eurise/releases/download/apk-prova/phone-debug.apk
- Ogni servizio che fa uscire dati è spento di base e compare nel centro privacy; servizi chiusi per ultimi con avviso.
- Parti comuni (`core/designsystem`, `core/ponte`, `CLAUDE.md`, `canvas.json` della tela): si toccano solo se serve e si
  scrive qui.
- A ogni consegna: aggiornare questo file e una sola riga in `docs/STATO.md`.
- Storia completa: sezioni datate di `docs/STATO.md` dall'11 al 16 novembre (Telefono, Rubrica e Messaggi dall'APK 237;
  Email dal giro 310; Chat dal giro 316; Chat e Mail da lavoro il 14 novembre, notte).

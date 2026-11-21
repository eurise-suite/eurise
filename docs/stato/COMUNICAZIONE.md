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
- **La mia Bussola in Chat, prima parte** (17 novembre, notte; tavole Bus* approvate con le quattro decisioni; giro 724):
  protocollo in `core/studio/Bussola.kt` con `BussolaTest`: `m.room.message` con `msgtype` `it.bussola.rx_request`,
  `rx_status` (con `m.relates_to` `m.reference` alla richiesta; stati Received, Preparing, Ready con NRE, Review,
  Collected), `checkup_invite` / `checkup_reply` (le quattro risposte rapide), `studio` (orari, telefono, indirizzo,
  guardia medica, assenza, sostituto), `therapy` (terapia abituale, solo cifrata nella stanza). Body di riserva leggibile
  da Element. Lo studio è chi ha mandato per primo `studio`; stati, inviti, orari e terapia valgono solo da lì; «ritirata»
  anche dal paziente. Ricetta valida 30 giorni dall'arrivo del NRE. `StudioState.info` (orari dello studio, cifrati).
  Chat: `Event.Kind.Bussola`; `ChatModel.bussolaRole/bussolaItems/sendBussola/bussolaActivate`; la storia della stanza
  si legge tutta (come le Spese). Lato paziente: banda del 118, «Urgenze: 118» sotto il campo, tre azioni grandi
  (Chiedi un rinnovo, Le mie ricette, Orari e contatti), rinnovo con un tocco, stato in tre tappe, NRE grande con Copia,
  Condividi, «L'ho ritirata», scaduta dopo 30 giorni, risposte agli inviti a pulsanti grandi, accento verde petrolio.
  Lato studio: Studio › «Orari e contatti dello studio» (salvando arrivano nelle chat attive), scheda del paziente ›
  «La mia Bussola» › «Attiva nella sua chat» (orari + terapia), nella chat In preparazione / Da rivedere / Ricetta pronta
  (NRE scritto a mano dal promemoria di Millewin; la lettura del PDF è il passo 2), Invito al controllo, Aggiorna orari
  e terapia; i rinnovi e le risposte entrano nella coda dello Studio. Notifiche (`ChatWorker`, `BussolaRooms`): nelle
  stanze di Bussola solo «Hai ricevuto una nuova comunicazione da parte del tuo medico su La mia Bussola»; sul telefono
  del medico, per i pazienti dello Studio, «Hai nuovi messaggi dei pazienti» senza nomi.
  Decisioni del proprietario (17 novembre, notte, «Ok su tutto»): niente risposta automatica sulle parole urgenti (solo il
  118 fisso e la risposta fuori orario); «Da guardare prima» resta solo dalla parte del medico come aiuto di lettura;
  notifiche neutre; fino al server dello studio i pazienti passano da SMS ed email.
  Non ancora: attivazione con codice, SMS e passkey (lato server),
  ruoli di assistente e infermiere (servono i loro account), la seconda fila (prenotazioni, questionario, esami,
  certificati e 730, calendario, diario delle misure, scheda di emergenza, deleghe, lingue), segno di lettura
  «paziente in attesa» per chi non ha attivato l'area.
- **Allegati cifrati, referti e promemoria di Millewin** (17 novembre, giorno; giro 737: chat, mail, messages, phone):
  - **Chat › allegati (tavola ChatAllega)**: graffetta a sinistra del campo; file fino a 25 MB, foto come `m.image` (con
    larghezza e altezza), il resto `m.file` con `filename`. Nelle stanze cifrate il file si cifra sul telefono come in
    Element (`Attachments.kt`: AES-256-CTR, chiave nuova per file, 64 bit bassi del vettore a zero, SHA-256 controllato
    prima di aprire; test `AttachmentsTest`); `ChatClient.upload`/`fetch`/`redact`. I file cifrati ricevuti ora si aprono
    (prima si leggeva solo `url`); le didascalie di Matrix 1.10 (`filename` + `body`) si leggono. La cache dei file aperti
    si svuota dopo un giorno.
  - **La mia Bussola › referti (tavola BusReferto, passo 3 dello Studio)**: `m.file`/`m.image` cifrato con il campo
    `it.bussola.report` (`note`, `expires`), così Element lo apre comunque; PDF, JPG o PNG fino a 10 MB, due righe
    facoltative (didascalia), avviso «test genetici ed esami HIV di persona». Paziente: dalla graffetta; studio: terza azione
    «Invia un referto», solo con il consenso «referti online» della scheda. Nella chat: scheda del referto con «Apri» e
    «Visibile fino al…»; dopo 45 giorni (dall'ora del server) non si mostra più e chi l'ha mandato lo cancella dalla stanza
    (redaction; elenco in `BussolaReports`, all'apertura e dal lavoro ogni 15 minuti). Solo in chat cifrate. I referti dei
    pazienti entrano nella coda dello Studio come «Referto: nome — righe» (`ChatModel.studioText`, anche ad app chiusa).
    Il file cifrato sul server lo toglie la regola di conservazione del server dello studio (Synapse), da impostare.
  - **Promemoria PDF di Millewin (tavola StuMillewin, passo 2)**: `PdfText` spostato nel modulo nuovo **`core/pdf`**
    (core/data lo delega, nessun import cambiato altrove: parte comune toccata). `core/studio/Millewin.kt` con
    `MillewinTest`: NRE (`\d{4}[A-Z]\d{10}`, anche spezzato), codici fiscali con il carattere di controllo, abbinamento al
    paziente (codice fiscale, o nome e cognome) e ai farmaci chiesti. Scheda del paziente: campo **codice fiscale**
    (`Patient.fiscalCode`, controllato). Foglio «Ricetta pronta»: si compila da sé dalla **cartella dei promemoria**
    (scelta una volta, PDF degli ultimi 14 giorni, il più adatto) o «Dal promemoria PDF»; avvisi se il promemoria sembra di
    un altro paziente o senza i farmaci chiesti. Si può sempre scrivere a mano.
- **Turni nei gruppi della Chat** (17 novembre, sera; tavole ChatTurni e ChatTurniCrea approvate, con le funzioni di
  **Bussola CA** chieste dal proprietario: preferenze, indisponibilità e il resto; giro 755, solo chat): scheda **Turni**
  accanto a Messaggi e Spese. Logica pura in `chat/.../Shifts.kt` con `ShiftsTest`: tipi di turno (sigla, orari, quante
  persone, giorni; notte che finisce il giorno dopo; modelli «Reparto M P N» e «Continuità assistenziale» notti 20–8,
  festivi 8–20, prefestivi 10–20, con festività italiane, Pasqua e festivi locali), persone con ruolo (**titolare**,
  **sostituto** con «al posto di» e periodo, **reperibile esterno** mai generato) e **monte ore**; **disponibilità** di ognuno
  (giorni no, turni preferiti ed evitati, giorni della settimana evitati); **Genera** che sceglie l'intera giornata insieme
  (riposo di 11 ore, al massimo sei giorni di fila, equità pesata sul monte ore, preferenze; fissati a mano intatti; caselle
  scoperte segnalate); «Come la settimana scorsa»; «A mano». Caselle in **doppia/tripla** con chi tiene il telefono
  («una volta a testa»), **diurni divisi** all'ora scelta, **fissato a mano**. Cambi: **scambio** e **cessione** (a una persona
  o a chiunque) con il controllo delle regole per tutti e due prima di chiedere e prima di accettare, **straordinario** a passi
  di 15 minuti con il motivo; ogni cambio resta registrato e **si annulla** (chi l'ha chiesto, chi l'ha preso o chi
  coordina). **Turni effettivi** e **ore del mese** (svolte, programmate, straordinari, monte ore, turni e notti), condivisibili
  come testo. Messaggi della stanza cifrata `eu.eurise.shifts.plan` (vale l'ultima versione di chi l'ha mandato per primo),
  `.avail` (solo di chi le manda), `.change`, `.reply`, con `body` leggibile da Element. Calendario **«Turni»** locale
  del telefono (`ShiftCalendar`, account locale «Eurise Chat», promemoria alle 20 della sera prima), acceso da un
  interruttore nella scheda: permesso del calendario chiesto solo allora (nuovi permessi READ/WRITE_CALENDAR nel manifest).
  Non ancora (da Bussola CA): moduli ASL di fine mese e fogli firme, registro attività, import dei turni da PDF, «Da foglio o
  foto» della tavola, riepilogo per SMS/email a chi non usa la Chat (oggi «Condividi»), turni nel calendario di chi usa
  Element (link .ics), notifiche di promemoria dalla Chat stessa.
- **Turni generici, da importare ed esportare** (17 novembre, notte; precisazione del proprietario: «le funzioni di Bussola
  CA per fare i turni, importarli, esportarli; funzioni generiche declinabili per molti contesti»; giro 759, solo chat):
  modelli pronti **Reparto M P N**, **Giorno e notte 12 h**, **Negozio** (apertura e chiusura, lunedì–sabato), **Continuità
  assistenziale**, **Da zero**; regole regolabili nel piano (`rest`: nessuno, 8, 11, 12 h; `in_a_row`: 5, 6, 7, 14 giorni);
  **intestazione dei documenti** (`title`); **persone senza Chat** (id `ext:Nome`, aggiunte a mano o dall'importazione).
  **Importa** (`ShiftsIO`, con `ShiftsIOTest`): PDF con testo (`core/pdf`), CSV (punto e virgola, virgola, tabulazione,
  anche Windows-1252), **XLSX** e **ODS** letti senza librerie (zip e XML), testo incollato; riconosce da sé **righe per
  persona** (intestazione con i giorni: «1 2 3», «lun 2», date, numeri di serie di Excel), **colonne per turno** (Mattina,
  Notte… con i nomi sotto), **righe per giorno** («17/11 M Rossi N Bianchi», «Rossi M, Bianchi N», solo nomi assegnati ai
  turni del giorno in ordine, come i calendari dei presidi) ed **elenchi** «persona; data; turno». Nomi e sigle sconosciuti
  si abbinano una volta (a una persona, «Persona senza Chat», «Ignora»; sigla → turno o riposo) e restano ricordati per il
  gruppo sul telefono; anteprima con i turni a testa; «Sostituisci i turni del periodo» o «Aggiungi»; le caselle importate
  sono fissate a mano. Anche da «Fai i turni › Come girano › Da un file» (tavola ChatTurniCrea, «Da foglio o foto»).
  **Esporta** (programmati o effettivi, mese scelto, «Solo i miei turni»): **prospetto del mese PDF** (persone × giorni,
  sigle colorate, festivi in grigio, ore, legenda, più pagine; `ShiftPdf` con `PdfDocument`), **fogli presenze PDF** (una
  pagina per persona: giorno, turno, entrata, uscita, ore, straordinario e motivo, totali, «Firma» e «Visto»), **prospetto
  CSV** per Excel e LibreOffice, **elenco CSV** con ore, telefono e straordinari, **calendario .ics** (UTC, identificativi
  stabili, righe piegate), testo. File nella cache della Chat, condivisi con l'app scelta (`ChatActions.shareFile`).
  Non ancora: «da foto» (OCR: servirebbe Eurise Leggi), XLS vecchio formato, firma disegnata sul foglio presenze, moduli
  già impaginati di un ente (si usano intestazione e PDF generici).
- **Una persona, tutti i canali** (17 novembre, notte; tavole UniPersone, UniConversazione, UniCanale; giro 764: chat,
  messages, phone, mail): contratto **`PeopleContract`** in `core/suite` (parte comune toccata: un file nuovo) con i
  fornitori `<pacchetto>.persone` di Messaggi, Telefono ed Email, che rispondono solo con il permesso di firma (controllato
  in `call()`): `recent` (l'ultima cosa per indirizzo e quante da leggere), `items` (il filo con numeri ed email), `resolve`
  e `search` (Rubrica, da Telefono o Messaggi: **`PeopleLookup`** in `core/contacts`, che ora dipende da `core:suite`),
  `send` (SMS da Messaggi; da app non predefinita lo manda direttamente). Telefono: le note delle chiamate restano anche
  sul telefono (`CallNotes`, ultime 300) e si abbinano alla chiamata; ingresso **`SuiteCall`** con il permesso di firma che
  chiama subito. Email: `MailCache` (posta già sul telefono, senza rete), `ACTION_OPEN_MAIL` apre il messaggio «casella:uid».
  Chat: logica pura `People.kt` con `PeopleTest` (numeri scritti in modi diversi, una riga per persona, filo senza
  doppioni, scelta del canale), dati in `Uni.kt` (in memoria, nessuna copia su disco), interfaccia in `PeopleUi.kt`.
  Elenco con **Persone / Solo Chat / Non lette · n**: una riga per persona con le icone dei canali (Chat, SMS, email,
  chiamate), i gruppi restano stanze. Il **filo** della persona: filtri Tutto/Chat/SMS/Email/Chiamate, giorni, SMS e Chat a
  fumetti, email a scheda (tocco: si apre in Email), chiamata con durata e nota, chiamata persa con **Richiama**; in alto
  Chiama con un tocco, «Apri solo la Chat» (allegati, spese, verifica) e, per chi è solo nella Chat, **Unisci al contatto
  della Rubrica** (resta sul telefono). In basso il **canale** (tocco per cambiare) e Invia: tocco = canale scelto,
  pressione lunga = **foglio del canale** con tacchette, stellina per persona e **Rispondi dove ti ha scritto** (acceso:
  testo lungo → email, altrimenti il canale dell'ultima cosa ricevuta, poi la stellina). Email: si apre Scrivi già
  compilato. La persona si riconosce da numero, email o utente della Chat scritto nel contatto (anche link matrix.to);
  altrimenti dal nome uguale a un solo contatto.
  Non ancora: email mandate (Email tiene solo la posta ricevuta), «non è in linea da un giorno → SMS» (serve la presenza),
  videochiamata (ChatChiamata), foto dei contatti (la Chat non legge la Rubrica), invito a chi non è sulla Chat (prossimo).
- **Chat, le basi** (18 novembre; tavola ChatBasi; giro 795: chat): risposte, reazioni, modifica, cancella per tutti,
  messaggi fissati, inoltra e **sondaggi**, con gli eventi di Matrix che usa anche Element. Logica pura **`Relations.kt`**
  con `RelationsTest`: reazioni una per persona e chiave (tocco sulla reazione = metti o togli la tua), modifiche valide
  solo di chi ha scritto (tenute per autore: quella di un altro non le cancella), cancellazioni che tolgono anche reazioni e
  voti, risposte senza la vecchia citazione in testa, sondaggi MSC3381 (`org.matrix.msc3381.poll.*`, voto palese, una
  risposta; vale l'ultimo voto di ognuno dato prima della chiusura; l'ora di chiusura è un campo nostro
  `eu.eurise.closes` e la fine la manda chi l'ha aperto), fissati in `m.room.pinned_events`. `ChatModel`: invio di eventi
  di ogni tipo (cifrati se la stanza lo è), `reply`, `react`, `edit`, `delete`, `pin`, `forward`, `sendPoll`, `vote`,
  `endPoll`; `ChatClient.sendEvent`/`setState`. Interfaccia in **`BasiUi.kt`**: **pressione lunga su un messaggio** = sei
  reazioni veloci e Rispondi, Copia, Modifica, Fissa, Inoltra, Cancella (con secondo tocco di conferma), Chiudi il
  sondaggio; citazione dentro il fumetto, «modificato» accanto all'ora, reazioni sotto il fumetto, **barra dei fissati** in
  cima (tocco = va al messaggio, con più fissati passa al successivo), sopra il campo «Rispondi a…»/«Modifichi il
  messaggio» con ×. **Graffetta: tocco = file, pressione lunga = sondaggio** (domanda, 2–6 risposte, si chiude: quando lo
  chiudi tu, tra un'ora, domani alle 20, tra una settimana). L'avviso «Tieni premuto un messaggio…» resta finché non si usa
  il menu. Non ancora: reazioni con emoji qualsiasi (solo le sei veloci), risposte multiple e voto segreto nei sondaggi
  nuovi (si leggono se arrivano da altri).
- **Proposte, riassunto, quando lavora, gruppi a tempo** (21 novembre, notte; tavole ChatProposta, ChatRiassunto,
  ChatQuandoLavora, ChatGruppoTempo; giro GIRO: chat; la Chat ora dipende da `core/data` per `MessageInsights` e
  `QuickAdd`, le stesse proposte di Messaggi: provato a staccarli in un modulo piccolo `core/text` come `core/pdf`, ma
  `QuickAdd` usa `Task` e `Recurrence` di core/data; il giro 969 è fallito per questo e lo spostamento è stato tolto
  subito. Da fare con l'area Organizzazione: modulo piccolo con QuickAdd, Task e Recurrence). **Proposte**: sotto gli ultimi sei messaggi ricevuti (non nelle chat di La mia Bussola) le stesse
  proposte di Messaggi: «Giovedì 19, 15:00 · con Anna — libero in Calendario (o «c'è già: …», con il permesso del
  calendario) — Aggiungi» (apre Calendario con l'evento) e «Manda il preventivo… — Attività · entro venerdì — Aggiungi»
  (HubBridge). **Riassunto**: entrando in un gruppo con almeno 15 messaggi da leggere, sopra i messaggi la scheda «84
  messaggi da leggere, in breve» con **Riassumi sul telefono** (Eurise AI, `BrainContract.MSG_ASK`, risposta a pezzi;
  senza app o modello lo dice), le proposte trovate in quei messaggi come pulsanti e ×. **Quando lavora**: nel foglio
  dell'account di lavoro «Fai sapere il tuo orario di lavoro» (**spento di base**): acceso, nelle chat a due mette lo
  stato `eu.eurise.hours` (feriali 8–19, fuso) con la chiave del proprio id, anche nelle chat nuove; chi scrive con
  Ciarly a una persona fuori orario vede «fuori orario fino alle 9» in alto e sopra il campo «Marco è fuori orario
  (lavora 9–18). Il messaggio parte quando inizia…»: i messaggi diventano programmati per l'inizio (con l'orologio ambra
  «arriva domani alle 9:00»), salvo **Manda subito se è urgente**; «Invia quando lavora» usa l'orario dell'altro.
  **Gruppi a tempo**: nei gruppi il pulsante «A tempo» (dal, al, fine del viaggio): stato `eu.eurise.timebox`, che vedono
  tutti; in cima «Gruppo a tempo: dal 18 al 30 novembre», finito il viaggio il riepilogo «148 foto · 9 spese · 4
  documenti» con Spese e «Il gruppo va in archivio domenica 30 · Tieni aperto»; dopo l'ultimo giorno il gruppo sta nella
  cartella **Archivio** dell'elenco (sul telefono, salvo «Tieni aperto»). Logica pura in `Orari.kt`, `Tempo.kt` con i
  test. Non ancora: «Album in Galleria» e «Documenti in File» del riepilogo (servono i contratti con Galleria e File),
  orari diversi da 8–19, proposte nelle discussioni.
- **Difesa dalle truffe, presa visione, modalità semplice** (21 novembre, notte; tavole ChatTruffa, ChatPresaVisione,
  ChatSemplice; giro 964: chat e messages; toccata una parte comune: `core/suite` con un file nuovo, `ScamCheck.kt`, e
  il suo test). **Truffe**: `ScamCheck` riconosce sul telefono i copioni più usati (familiare con il «numero nuovo» che
  chiede soldi, soldi urgenti, conto bloccato con link, pacco in giacenza, richiesta del codice arrivato per SMS; almeno
  due segni insieme; chi è in rubrica non fa scattare l'avviso, tranne per il codice). In **Messaggi**, sotto l'SMS di
  chi non è in rubrica, la scheda rossa «Attenzione: sembra una truffa» con il perché, **Chiama Luca (rubrica)** quando
  il messaggio dice «sono Luca» e Luca è in rubrica (se no Apri la rubrica) e **Blocca** (numeri bloccati di Android,
  con l'app predefinita; doppio tocco). In **Chat**, nella chat a due con chi non è verificato e a cui non hai mai
  scritto: la stessa scheda con «Chiama chi conosci» e Blocca (lista `m.ignored_user_list` come Element, poi esce dalla
  chat). Non ancora: l'avviso a «chi hai indicato come aiuto». **Presa visione**: nella pressione lunga su Invia dei
  gruppi «Chiedi la presa visione»; il messaggio porta `eu.eurise.ack` e in fondo, per Element, «— Presa visione:
  conferma con ✅»; chi riceve ha il tasto **Ricevuto** (la reazione ✅, quindi anche chi usa Element conferma); chi l'ha
  mandato vede la barra «Ricevuto da 4 su 6», «Mancano: Paolo (letto, non confermato), Marta (non letto)» dalle
  conferme di lettura per persona, e **Ricorda a chi manca** (una risposta con i nomi). **Modalità semplice**: dal foglio
  dell'account si sceglie il nome del saluto, fino a quattro persone e chi riceve «Aiuto»; accesa, al posto dell'elenco
  c'è «Ciao Bruna», le persone in grande con «1 messaggio nuovo», **Manda un vocale** (a chi? poi la chat) e il tasto
  rosso **Aiuto** («Chiedo aiuto a Luca?»: un messaggio e la posizione dal vivo per un'ora, se c'è il permesso); in fondo
  sempre «Modalità semplice accesa · puoi toglierla» e «Torna alla Chat completa» con doppio tocco. Logica pura in
  `Ack.kt`, `Semplice.kt` con i test. Non ancora: impostarla da un familiare a distanza («impostata da Luca con il tuo
  consenso»), chiamare dalla modalità semplice (le chiamate non ci sono ancora).
- **Posizione dal vivo a tempo** (21 novembre; tavola ChatPosizione; giro 957: chat): **pressione lunga sulla
  graffetta** = Sondaggio o Posizione dal vivo (prima apriva subito il sondaggio); «Per quanto?» 15 min, 1 ora, Fino
  alle 23 (prima delle 22), Finché arrivo. Eventi di **Element** (MSC3672/MSC3488): stato `beacon_info` per persona
  (dal vivo e durata, **nessuna posizione**: gli stati Matrix non sono cifrati) e punti `beacon` **cifrati** come i
  messaggi; quindi Element vede la posizione di Ciarly e viceversa. Il **servizio in primo piano**
  `LiveLocationService` (tipo location) tiene l'**avviso fisso** «Stai condividendo la posizione con Giulia · fino alle
  23:00» con **Smetti**, usa il GPS e la rete del telefono (`LocationManager`, niente Google), manda un punto ogni 20
  secondi al massimo, spegne a tempo, all'arrivo o con Smetti (stato «non più dal vivo»). «Finché arrivo» senza
  destinazione: arrivato quando, dopo essersi spostati di 500 m, si resta entro 60 m per 5 minuti (al più tre ore);
  con Mappe si userà la meta del percorso. Con la Chat aperta i punti li manda lei (`LiveShare.sink`, tiene le chiavi),
  se no il servizio con la sua sessione. Nella conversazione la scheda con segnaposto e coordinate (**nessuna mappa
  scaricata**: nessun server di mappe sa dove sei), «Posizione dal vivo · fino alle 23:00», «aggiornata alle…», Smetti
  (tua) e **Apri in Mappe** (`geo:`, l'app di mappe scelta). Si leggono anche le posizioni singole `m.location` di
  Element. Permessi nuovi: posizione (chiesta alla prima condivisione), servizio in primo piano. Logica pura in
  **`Posizione.kt`** con `PosizioneTest`. Non ancora: mandare una posizione singola, la meta di Mappe per «Finché
  arrivo», la mappa disegnata (servirebbero le mappe scaricate di Mappe), più condivisioni insieme.
- **Discussioni a filo** (21 novembre; tavola ChatThread; giro 950: chat): i **thread di Matrix** (`m.thread`, come
  Element, quindi le discussioni aperte da Element si vedono e viceversa). Nel menu di un messaggio di un gruppo
  «Rispondi in una discussione» apre la discussione: in alto indietro, il titolo (prima riga del messaggio di partenza)
  e «discussione in «Casa al mare» · 6 risposte»; in cima la scheda «Luca · messaggio di partenza», poi le risposte con
  i fumetti di sempre (reazioni, risposte, modifiche), sotto l'avviso «Le risposte restano qui: il gruppo vede solo «6
  risposte»» e il campo «Rispondi nella discussione». Nella conversazione del gruppo le risposte non ci sono: sotto il
  messaggio di partenza c'è «6 risposte · 10:11» (azzurro), che apre la discussione; nell'elenco delle chat, sotto
  l'anteprima, «2 discussioni · Turni pulizie (6), Bollette (3)». All'apertura si chiedono al server tutte le risposte
  (`/relations/…/m.thread`) e, se manca, il messaggio di partenza; il conteggio usa anche il riepilogo del server
  (`unsigned.m.relations.m.thread.count`). Il riferimento di ripiego (`is_falling_back`) non diventa una citazione nel
  fumetto. Dentro una discussione niente microfono, graffetta, programmati né discussioni nella discussione. Logica pura
  in **`Threads.kt`** con `ThreadsTest`. Non ancora: vocali e allegati nella discussione, notifiche separate per le
  discussioni che segui, l'elenco di tutte le discussioni di un gruppo.
- **Cartelle, «Messaggi a me», vocali scritti a richiesta** (21 novembre; tavola ChatCartelle; giro 930: chat).
  **Cartelle** scelte dall'utente sotto le viste (Tutte, Famiglia · 3 = chat da leggere, …, «+»): una chat può stare in
  più cartelle; pressione lunga su una chat = le cartelle come pulsanti da accendere e «+ Nuova»; il bottone con la
  cartella in alto apre l'elenco (cambia nome, chat dentro, elimina). Restano **sul telefono** per account
  (preferenze `chat_folders`): i nomi non vanno al server, quindi non si vedono su un altro telefono. **Messaggi a me**
  in cima all'elenco (pallino scuro con la puntina): stanza cifrata con dentro solo l'utente, creata al primo tocco, il
  cui id sta nei dati dell'account Matrix (`eu.eurise.self_room`) così un secondo telefono ritrova la stessa; sotto ogni
  messaggio «→ Note» e «→ Attività» (HubBridge; in Attività si capiscono date e ore). Logica pura in **`Cartelle.kt`** con
  `CartelleTest`. **Vocali** (richiesta del proprietario): la trascrizione non parte più da sola; accanto alla durata c'è
  la piccola icona «T» che **scrive il vocale senza ascoltarlo** (parte da sola solo con «Leggi da soli i messaggi»
  acceso); se non riesce, «tocca per riprovare». Con la trascrizione fatta, l'anteprima nell'elenco diventa «vocale
  trascritto · «arrivo alle 8»». Non ancora: le discussioni dentro una chat (tavola ChatThread, la prossima) e la presa
  visione (ChatPresaVisione) che la tavola mostra nell'elenco; cartelle uguali su più telefoni.
- **Invia più tardi, quando lavora, a scomparsa, Ricordamelo** (21 novembre; tavola ChatProgramma; giro 919: chat;
  toccate parti comuni: `core/suite` HubBridge e `app` KeyboardBridgeReceiver, vedi sotto). **Pressione lunga su Invia**
  (non nelle chat di La mia Bussola né in modifica): foglio con *Invia più tardi* (Domani 8:30, Lunedì 9:00, Scegli… con i
  selettori di data e ora del telefono), *Invia quando lavora* (solo nell'account di lavoro: con l'orario 8–19 dei
  feriali di `Quiet`, «arriva lunedì alle 8:00»; in orario è spento: parte con il tocco breve), *A scomparsa* (parte
  subito e sparisce un giorno dopo la lettura). I **programmati** restano sul telefono (`noBackup/chat_programmati.json`):
  in fondo alla conversazione come fumetti chiari con l'orologio ambra «arriva domani alle 9:00»; un tocco apre Invia
  adesso / Modifica (il testo torna nel campo) / Annulla l'invio. Partono dall'app aperta (giro ogni 30 s) o ad app
  chiusa da `PlannedWorker` (WorkManager, con la rete, all'ora del primo; due nomi che si alternano per riprogrammarsi
  senza fermarsi), e anche dalla sincronizzazione ogni 15 minuti; cifrati se la stanza lo era quando si è programmato.
  Matrix non ha un invio differito comune a tutti i server: il telefono deve essere acceso con la rete. **A scomparsa**:
  campo `eu.eurise.fade` `{after_read_ms}` nel messaggio; chi manda segna l'evento (`noBackup/chat_scomparsa.json`), legge
  le **conferme di lettura** degli altri dalle tornate di /sync (`m.read` e `m.read.private`, app aperta e lavoro) e
  **cancella il messaggio dal server** (redazione) un giorno dopo: sparisce anche da Element. Sotto il messaggio
  l'occhio con «sparisce 1 giorno dopo la lettura», poi «sparisce domani alle 22:48»; chi riceve con Eurise vede «a
  scomparsa». **Ricordamelo** nel menu di un messaggio ricevuto: Tra un'ora, Stasera 18 (prima delle 17), Domani 9,
  Scegli…; crea in **Attività** «Rispondi a Marco: «…»» con data e ora già decise e nelle note il link
  `eurisechat://messaggio?account&room&event`, che la Chat apre nell'account e nella conversazione giusti. Per questo
  `HubBridge.send` ha `due`, `time`, `notes` facoltativi (extra `EXTRA_DUE`, `EXTRA_TIME`, `EXTRA_NOTES`) e il
  ricevitore dell'app Eurise, con quegli extra, non interpreta il testo (un «alle otto» nel messaggio citato non sposta
  la data). Logica pura in **`Programma.kt`** con `ProgrammaTest`. Non ancora: le note delle attività non rendono
  cliccabile il link (è di Attività, area Organizzazione); l'orario di lavoro dell'altra persona (tavola
  ChatQuandoLavora: oggi vale l'8–19 dei feriali); programmare risposte, allegati e vocali; durata della scomparsa a
  scelta; Delayed events dei server che li hanno (MSC4140), per partire anche a telefono spento.
- **Vocali trascritti e letti ad alta voce** (18 novembre; tavola ChatVocali; giro 809: chat): **microfono** al posto di
  Invia con il campo vuoto (non nelle chat di La mia Bussola): si tiene premuto e si parla, si lascia per mandare, scorrendo
  a sinistra si annulla (striscia rossa con tempo e livello della voce al posto del campo; sotto un secondo non parte;
  massimo dieci minuti). Permesso del microfono chiesto solo al primo vocale (RECORD_AUDIO nuovo nel manifest).
  Registrazione a 16 kHz mono in memoria (`VoiceAudio.kt`: `VoiceRecorder`), file **AAC .m4a** con i codec del telefono
  (`VoiceCodec.encode`, nessuna libreria), cifrato come gli allegati, mandato come `m.audio` con i campi dei vocali di
  Element (`org.matrix.msc3245.voice`, `org.matrix.msc1767.audio` con durata e forma d'onda 0–1024). Logica pura in
  **`Voice.kt`** con `VoiceTest` (contenuto, forma d'onda, sbarrette, durata, tagli nei punti quieti, mono a 16 kHz).
  Fumetto del vocale: tasto Ascolta/Pausa, forma d'onda (tocco = salta lì), durata che scorre; sotto la **trascrizione
  fatta sul telefono** con **Eurise Voce** (Whisper, `VoiceContract`; pezzi di al massimo 28 s tagliati nelle pause), che
  parte da sola quando il vocale si vede, uno alla volta, anche per quelli di Element (Ogg Opus decodificato con
  `MediaExtractor`); riga «Trascritto sul telefono · l'audio non esce · ora». Senza modello: «scarica il modello in Eurise
  Voce» (apre Voce); senza Voce: una riga che lo dice. Le trascrizioni restano **solo in memoria** (`VoiceHub`), non si
  mandano e non si salvano. **Leggi ad alta voce** nel menu della pressione lunga (testi e trascrizioni), con il motore di
  sintesi del telefono (anche Eurise Voce). **Per chi vede poco**: interruttore «Leggi da soli i messaggi» nel foglio
  dell'account (e avviso una tantum nella conversazione): con la conversazione aperta i messaggi nuovi si leggono da soli,
  «Nome: testo», e sotto ogni messaggio ricevuto c'è il tasto «Leggi ad alta voce» della tavola (di base il tasto sta solo
  nel menu, per non riempire ogni fumetto). L'ascolto si ferma uscendo dalla Chat.
  Non ancora: Ogg Opus anche in uscita (oggi AAC, che Element apre), vocale bloccato a mani libere, risposta con un vocale
  (oggi il vocale non porta la risposta), velocità 1,5×/2×, lettura automatica anche dei vocali.
- **Invito sulla Chat** (17 novembre, notte; tavole ChatInvita e ChatInvitaElenco; giro 777: chat e phone): il link è
  **`https://matrix.to/#/<mio utente>`** (apre una chat con me in Eurise, Element o un'altra app Matrix; niente server
  nostro: `eurise.eu/i/…` della tavola aspetta dominio e server). Nei testi resta «Chat» e non «Ciarly» finché il marchio non
  è verificato (punto 7 dei prossimi passi). **Una persona** (`InviteUi.InviteScreen`): dalla scheda della **Rubrica**
  (banner «Invita» nel Telefono, solo con la Chat collegata, contatto senza utente della Chat scritto e non già unito:
  `PeopleContract.chatUser`/`chatKnows`/`inviteIntent`, fornitore `ChatPeopleProvider`), dal **foglio del canale** (riga
  Chat di chi non c'è) o da Persone; SMS o email a scelta (quello già usato per primo), testo modificabile, «Manda
  l'invito»: SMS da Messaggi (se non parte, l'app degli SMS già compilata), email con Scrivi già compilato (oggetto e testo
  ora anche nel link `mailto:`, che Email legge). **Più persone** (`InviteListScreen`): le persone della Rubrica con cui si
  scrive o si parla ma non sulla Chat (`People.inviteCandidates`, test), le prime tre non ancora invitate già scelte,
  motivo per riga, «Invita N persone»: SMS subito, email una alla volta (nessuno vede gli altri). Ingressi: riga in fondo a
  Persone e foglio dell'account. Invitata il… resta ricordato sul telefono.
  Non ancora: «modalità semplice già accesa» (ChatSemplice), chat creata in anticipo (serve sapere l'utente dell'altro).
- **Telefono: chiamata sempre raggiungibile e bolla** (17 novembre, richiesta del proprietario; senza tavola, descritta da
  lui): la chiamata in corso è un **servizio in primo piano** con lo stile «chiamata in corso» (pillola verde nella barra
  di stato da Android 12; se il sistema non lo concede resta la notifica, ora a importanza normale: canale nuovo
  `ongoing_call`, il vecchio era tra le silenziose). La schermata della chiamata compare nelle app recenti finché dura.
  **Bolla**: tasto accanto a Chiudi; la chiamata diventa una finestra piccola (picture-in-picture di Android, nessun
  permesso in più) con nome, durata o stato e muto; Muto e Chiudi sopra la bolla; si apre da sola uscendo dalla chiamata
  (Android 12+ automatico, prima con `onUserLeaveHint`). La nota della chiamata resta. Nel Telefono, con una chiamata in
  corso, fascia verde «Torna alla chiamata». Lo schermo diviso lo fa Android (attività ridimensionabile).
- **Icone nella tendina** (17 novembre): ogni app ha la sua icona piccola invece della stella di Eurise: cornetta
  (`phone`, `ic_stat_call`), fumetto squadrato (`messages`, `ic_stat_sms`), fumetto arrotondato (`chat`, `ic_stat_chat`),
  busta (`mail`, `ic_stat_mail`). Le altre app della suite usano ancora `ic_stat_eurise` (non di quest'area).
- **Icone nuove degli APK** (16 novembre, serie colorata): Email, Chat (smeraldo), Messaggi (oltremare), Telefono, Contatti.
- **Ultimo APK pubblicato** in `apk-prova`: chat giro GIRO (21 novembre: proposte, riassunto, quando lavora, gruppi a tempo); chat e messages giro 964 (21 novembre: truffe, presa visione, modalità semplice; prima chat giro 957, posizione dal vivo); phone giro 777 (invito sulla
  Chat); mail e messages giro 764 («Una persona, tutti i canali»).
- **Telefono e Rubrica: correzioni del 20 novembre (giro 896, fatte dalla chat Organizzazione su richiesta del
  proprietario)**: (1) **chiamate perse**: `MissedCallsReceiver` riceve `ACTION_SHOW_MISSED_CALLS_NOTIFICATION` (da app
  predefinita il sistema le affida a noi; prima la notifica era di sistema e apriva il Telefono di Murena, che chiedeva di
  tornare predefinito). La notifica apre i Recenti e ha Richiama (dal `CallActionReceiver` non esportato) e SMS. Il
  Telefono gestisce anche `VIEW` `vnd.android.cursor.dir/calls`. Aprendo il Telefono le perse sono «viste»
  (`cancelMissedCallsNotification`, `CallLog.NEW = 0`). (2)+(5) **tastierino**: il numero è un campo vero
  (`InterceptPlatformTextInput`: si incolla tenendo premuto, niente tastiera), «Incolla» quando negli appunti c'è un numero
  (`phoneIn`, con test), «Aggiungi in rubrica» sempre dalle tre cifre (prima spariva se le cifre somigliavano a un nome),
  due suggerimenti in uno spazio fisso e foglio aperto per intero: il tasto verde non scende. (3) **fine chiamata**: la
  bolla automatica (PiP del 17 novembre) restava accesa e Android rimpiccioliva la schermata verso un angolo; ora si spegne
  appena si chiude o la chiamata termina (`ending`). (4) **contatti senza cornetta**: `Contacts.messengerNumber` legge il
  numero dai campi di WhatsApp, Signal, Telegram (solo indirizzo WhatsApp o numero con «+», mai gli identificativi; con
  test) in `firstNumbers` e `detail`; il Telefono toglie i doppioni vuoti della stessa persona (`withoutEmptyTwins`).
  Ipotesi da confermare con il proprietario. (6) **ricerca in rubrica** in Preferiti, Recenti e Rubrica (`SearchState`,
  stessa parola nelle tre, Indietro la cancella). (7) **chiamata**: Bolla e «Blocca questo numero» come icone discrete in
  alto (senza fondo); Blocca compare per i numeri fuori rubrica, chiude e blocca. Senza tavola: indicazioni precise del
  proprietario.
- **Filtro antispam, decisioni del proprietario (20 novembre)**: (a) i call center **non si bloccano, si segnalano**:
  «Sospetto spam (0844…)» sulla chiamata, nella notifica e nei Recenti per chi non è in rubrica e chiama da 0843/0844
  (AGCOM, delibera 156/18/CIR) o da un prefisso segnato a mano (Filtro › Sospetto spam). `SpamFilter.suspect`, con test.
  Giro 902. (b) **No** al blocco di tutti i numeri fuori rubrica (resta solo l'interruttore già esistente «Tutti gli
  sconosciuti in silenzio», spento di base). (c) **Sì** all'elenco dei numeri molesti scaricato e aggiornato
  periodicamente: **fatto nel giro 910** (21 novembre), con la fonte scelta dal proprietario: un **elenco curato da
  Eurise** in `data/spam/elenco.txt` (formato e regole in `data/spam/README.md`: solo voci con segnalazioni pubbliche e
  ripetute, mai paesi europei interi). Prime voci: prefissi wangiri +216, +373, +375, +383, +255 e 0843/0844. Lo scarica
  l'app Eurise (`SpamListProvider`, `SpamListWorker` settimanale col Wi-Fi), servizio `PrivacyService.SpamList` spento di
  base (GitHub, Stati Uniti: vede l'indirizzo IP di chi scarica, come per le pietre miliari); il Telefono resta senza
  Internet e chiede l'elenco con il permesso della suite (`SpamListClient`, `SpamListContract`). Chi è nell'elenco e non è
  in rubrica arriva in silenzio con «Elenco Eurise: …» (chiamata, notifica, Recenti); nessun blocco automatico.
  **Aggiornamento (21 novembre, giro 918)**: (1) il telefono non scarica più dal ramo: il flusso «Elenco dei numeri
  molesti» (`.github/workflows/elenco-molesti.yml`) controlla il file con `data/spam/controlla.py` (formato, categorie,
  doppioni, niente paesi europei interi né prefissi italiani corti) e lo carica nella pre-release `elenco-molesti`, da cui
  l'app Eurise scarica. (2) **Controllo settimanale** delle segnalazioni pubbliche: attività programmata «Elenco numeri
  molesti: controllo settimanale» (lunedì 10.23, ora di Roma, approvazione automatica, notifica al proprietario) che segue
  le regole del README, scrive `data/spam/registro.md` e spinge su brand. (3) **«Segnala a Eurise»** nel Telefono: il
  proprietario lo vuole con **segnalazioni anonime su un server Eurise** (niente email né GitHub). **Ricordarglielo quando
  il server Eurise ci sarà** (è nei piani della posta e della chat: Stalwart + Continuwuity su un VPS, sezione «Mail e Chat
  da lavoro»). Resta da valutare la firma del file oltre all'HTTPS.

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
- **Turni in Chat** (17 novembre, sera; richiesta del proprietario; **approvate e in codice** il 17 novembre, vedi «Fatto»), fila y 21278: **ChatTurni** (la
  settimana in cima al gruppo, chi è in turno ora, scambi controllati sulle 11 ore di riposo che cambiano i calendari) e
  **ChatTurniCrea** (+ › Turni: chi, che turni, come girano; nel calendario «Turni» di ognuno, promemoria la sera prima).
  Da legare alle tavole Turni* già sulla tela (TurniFoglio, TurniOra, TurniProposta, area Organizzazione).
- **Due account (approvate, codice fatto, 17 novembre, pomeriggio; giro 750, solo chat)**: ChatDueAccount e ChatAccountAggiungi. `Accounts.kt`
  (Personal con le preferenze di sempre `chat_session`, Work `chat_session_work`; `Quiet` con fuori orario 19-8 e weekend,
  dominio dall'email, tacchette del server; test `AccountsTest`), `SessionStore` per account, `ChatModel.switchTo`,
  `ChatClient.unreadTotal` (non lette dell'altro ogni 2 minuti senza spostarne il punto di sincronizzazione), barra in
  basso Personale/Lavoro, foglio «I tuoi account», accesso del lavoro con l'email di lavoro, `ChatWorker` per i due
  account con canale `chat_quiet` per il lavoro fuori orario e notifica che apre l'account giusto. Lo Studio non è ancora
  legato al solo account di lavoro (oggi vive in quello aperto).
- **Approvate il 17 novembre, sera («Tutte le tavole approvate»)** (pagina «Chat ed Email», 17 novembre, pomeriggio; il proprietario ha approvato le idee, qui le tavole;
  toccato `canvas.json`; generatori fuori dal repo in `/home/claude/tela/gen/` `lib.py`, `uni.py`, `feat.py`):
  - **Una persona, tutti i canali** (la funzione di Eurise secondo il proprietario), fila y 17516: **UniPersone** (vista
    Persone: una riga per persona con Chat, SMS, email e chiamate), **UniConversazione** (un filo con email, chat, SMS,
    chiamate con nota, chiamata persa; filtri; «rispondi dove ti ha scritto»), **UniCanale** (tocco = canale con la
    stellina, pressione lunga = elenco con tacchette; scelta automatica). Tutto unito sul telefono; servono i dati di
    Messaggi, Telefono ed Email via ponte con permessi di firma.
  - **Invito a Ciarly** (richiesta del proprietario), fila y 16262: **ChatInvita** (dalla scheda della rubrica, per SMS o
    email dal telefono, link con la chat pronta), **ChatInvitaElenco** (più persone, ognuna sul suo canale).
  - Funzioni, file y 18770 e 20024: ChatBasi (risposte, reazioni, modifica, fissati, sondaggi; in codice), ChatVocali
    (trascritti e letti ad alta voce; in codice), ChatProgramma (più tardi, quando lavora, a scomparsa, Ricordamelo), ChatCartelle (cartelle,
    Messaggi a me, discussioni), ChatThread, ChatPosizione (a tempo), ChatTruffa, ChatSemplice, ChatPresaVisione,
    ChatProposta, ChatRiassunto, ChatQuandoLavora, ChatGruppoTempo.
- **Prima tavola dei due account** (17 novembre, pomeriggio): fila nuova a y 16262 con **ChatDueAccount** (Personale e Lavoro nella stessa Chat, due server; si cambia dalla barra in
  basso sotto il pollice, l'altro account mostra solo quante non lette; ricerca, notifiche, cifratura e backup separati) e
  **ChatAccountAggiungi** (i tuoi account con server e tacchette di privacy, fuori orario per il lavoro; aggiungi con
  matrix.org, con l'email di lavoro che trova il server dal dominio, o da un invito/QR; al massimo due). Codice dopo l'ok.
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
- **La mia Bussola in Chat (richiesta del proprietario, 17 novembre, notte)**: la Chat fa il lato paziente del gestionale
  BUSSOLA MMG, con **Millewin oggi e Bussola MMG poi**. Specifiche complete in `docs/stato/BUSSOLA-CHAT.md` (dalla chat
  del gestionale; prototipi con dati finti: area paziente Premium FFKpJRz5SZ6C3VsAg3Ey2D, Base NajX12E4Dg1o7wEvaav3PF,
  vista del medico HYcWdRa8AUunov5CRysch5). Punti fermi: il paziente vede solo i suoi dati; «Per un'urgenza chiama il
  118» in ogni schermata con la chat (Campania: **118**, non 112); **nessuna interpretazione automatica lato paziente**
  (fuori dalla MDR: niente triage né chatbot clinici); notifiche sempre con la frase fissa «Hai ricevuto una nuova
  comunicazione da parte del tuo medico su La mia Bussola»; servizio facoltativo; lingua semplice.
  Tavole da approvare, fila «La mia Bussola in Chat» sulla pagina «Chat ed Email» (y 15008; generatore
  `/home/claude/tela/gen/bus.py`, fuori dal repo): BusAttiva (invito, 4 passi, impronta/PIN/SPID-CIE), BusChat (118,
  azioni grandi sopra la chat, mittente, ora e tipo, stato del rinnovo, invito al controllo con risposte rapide), BusRinnovo
  (un tocco sulla terapia già prescritta; nuovi farmaci o sintomi solo per messaggio), BusReferto (PDF/JPG/PNG 10 MB,
  genetici e HIV di persona, 45 giorni), BusRicette (NRE, 30 giorni, stati, condividi/stampa), BusAvvisi (avvisi semplici,
  notifica neutra), BusOrari (assenza e sostituto, 118, guardia medica), BusStudio (lato studio: ruoli, l'assistente
  prepara e il medico firma, NRE dal promemoria di Millewin, paziente non attivo in attesa, stanza interna), BusCollega
  (oggi il telefono del medico fa da collegamento con Millewin, domani Bussola diretto; eventi `it.bussola.*` con body di
  riserva per Element; 45 giorni). Seconda fila dopo l'approvazione: prenota una visita, questionario prima della visita,
  esami e impegnative, certificati e spese per il 730, il mio calendario (.ics), promemoria farmaci e diario delle misure,
  scheda di emergenza con QR, familiari e deleghe, privacy e consensi (scarica i miei dati, chiudi l'area), accessibilità
  e lingue (italiano, inglese, rumeno, ucraino).
  **Da decidere con il proprietario**: (1) la risposta automatica col 112 sulle «parole urgenti» (tavola StuRegole) va
  contro le specifiche: proposta di toglierla e tenere solo il promemoria fisso del 118 e la risposta fuori orario; (2) lato
  studio, «Da guardare prima» ordina per parole: tenerlo (aiuto di lettura solo per il medico) o toglierlo; (3) le
  notifiche della Chat oggi mostrano il testo: per le stanze dei pazienti diventano neutre; (4) il server dello studio
  (senza, le stanze dei pazienti non esistono: fino ad allora SMS ed email con la frase fissa).
  Già corretto nel codice (esce col prossimo giro): la conferma della verifica dice 118.
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
00. **Tavole approvate il 17 novembre, sera** (tutte quelle in attesa sulla pagina «Chat ed Email»). Codice in quest'ordine:
   ~~Una persona, tutti i canali~~ (fatto al giro 764, vedi «Fatto»; prove da chiedere: vista Persone con Messaggi,
   Telefono ed Email installati e con i permessi, filo di un contatto con SMS, chiamata con nota ed email, SMS mandato dal
   filo, Chiama con un tocco, «Unisci al contatto»), ~~invito a Ciarly~~ (fatto al giro 777; prove: banner nella scheda della Rubrica, invito per SMS ed email, elenco con
   più persone),
   poi le 13 tavole Chat* (~~ChatBasi~~ fatta al giro 795; prove: rispondi, reazione, modifica, cancella, fissa, inoltra,
   sondaggio con voto e chiusura, anche con Element dall'altra parte; ~~ChatVocali~~ fatta al giro 809; prove: vocale
   tenuto premuto e annullato, ascolto, trascrizione con Eurise Voce e il modello scaricato, vocale verso Element e da
   Element, «Leggi ad alta voce», «Leggi da soli i messaggi»; ~~ChatProgramma~~ fatta al giro 919; prove: programmato domani
   e annullato, programmato tra pochi minuti con la Chat chiusa, quando lavora dall'account di lavoro, a scomparsa letto
   da Element e sparito il giorno dopo, Ricordamelo che compare in Attività e il link (copiato) che apre il messaggio;
   ~~ChatCartelle~~ fatta al giro 930; prove: cartella nuova con due chat, chat in due cartelle, pressione lunga su
   una chat, Messaggi a me con «→ Note» e «→ Attività», icona «T» su un vocale ricevuto; ~~ChatThread~~ fatta al giro
   950; prove: discussione aperta da Eurise e vista in Element (e al contrario), «6 risposte» sotto il messaggio,
   riga delle discussioni nell'elenco; ~~ChatPosizione~~ fatta al giro 957; prove: 15 minuti con la Chat chiusa
   (avviso fisso, punti in Element), Smetti dall'avviso, Finché arrivo, posizione di Element vista in Ciarly;
   ~~ChatTruffa, ChatPresaVisione, ChatSemplice~~ fatte al giro 964; ~~ChatProposta, ChatRiassunto, ChatQuandoLavora,
   ChatGruppoTempo~~ fatte al giro GIRO: **le tavole Chat* sono tutte in codice**; poi la seconda fila di La mia
   Bussola), poi le tavole della seconda fila di La mia Bussola.
   Turni: fatti ai giri 755 e 759 (vedi «Fatto»); prove da chiedere: importare un PDF e un foglio Excel veri del
   proprietario, esportare prospetto e fogli presenze; un gruppo con due telefoni, Fai i turni con Genera, scambio
   accettato dall'altro, calendario «Turni» acceso.
0. **La mia Bussola in Chat**: prima parte, referti e promemoria di Millewin fatti (vedi «Fatto»). Prossimo: tavole della
   seconda fila; regola di conservazione a 45 giorni sul server dello studio; referti per email (link e codice per SMS).
   Prove da chiedere anche: allegato cifrato verso Element e da Element, referto paziente → studio e studio → paziente,
   cartella dei promemoria, bolla della chiamata e pillola verde. Prove da chiedere: due telefoni (medico ed Element o Eurise sul secondo), attivazione dalla
   scheda, rinnovo, NRE, invito, notifiche neutre.
   **Studio medico**: passi 2 e 3 in Chat fatti; resta il NRE nel canale del paziente per email e SMS (oggi solo Chat),
   poi appuntamenti, regole, agopuntura (piano qui sopra). Prove da chiedere: accendere lo Studio, aggiungere un paziente,
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
5. **Chat**: anteprime delle foto nella conversazione (oggi «tocca per aprire»), chiamate (ChatChiamata, poi ChatVideo con MatrixRTC e LiveKit), UnifiedPush al posto del controllo ogni 15
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
- Codice: `mail/`, `chat/`, `messages/`, `phone/` (con la Rubrica); in comune `core/contacts` (con `PeopleLookup`), `core/studio` (Studio medico), `core/data/messages`
  (`MessageInsights`, con test), contratti in `core/suite` (`PeopleContract` per «Una persona, tutti i canali»); per il computer `core/sync` (`MailClient`, `MailText`).
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

## Sicurezza (18 novembre 2026, notte, progetto Eurise Sicurezza)
- Posta e Messaggi: «Condividi» non accetta come allegato i file privati dell'app stessa; SMS da mandare, persone e Studio
  passano solo tra app della suite verificate (`SuiteAccess`). Chat: il ritorno dall'accesso vale dieci minuti e una volta;
  la chiave di recupero va solo all'app Eurise vera; trascrizione dei vocali solo a Voce della suite.
  Dettagli in `docs/SICUREZZA.md`.

## Sicurezza, 20 novembre 2026, sera (progetto Eurise Sicurezza)
Posta: «Salva in» e «Apri da» di Eurise File solo verso l'app Eurise della suite (`FilesContract` con il `Context`). Dettagli in `docs/SICUREZZA.md`, «Intent verso le altre app della suite».

## Sicurezza, 21 novembre 2026: verifica delle librerie bloccante su brand
Da ora Compila e CodeQL su `brand` fermano il giro se una libreria non ha la sua impronta. Chi aggiunge o aggiorna una libreria non deve fare nulla: quel giro resta solo segnalazione, il flusso «Impronte delle librerie» scrive le impronte e rilancia Compila. Se un giro si ferma per un'impronta, non togliere la verifica: dirlo al progetto Sicurezza (docs/SICUREZZA.md, «Dove blocca»).

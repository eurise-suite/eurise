# La mia Bussola in Chat: specifiche (dalla chat del gestionale BUSSOLA MMG, 17 novembre 2026)

Testo ricevuto dal proprietario così com'è; le decisioni prese nell'area Comunicazione stanno in `docs/stato/COMUNICAZIONE.md`.

Specifiche per l’app di chat Matrix: lato paziente di BUSSOLA MMG (“La mia Bussola”)

Contesto

L’app deve riprodurre come chat Matrix l’area paziente del gestionale BUSSOLA MMG: un medico di medicina generale in Campania (ASL Benevento) con il suo studio.
Riferimenti di prototipo, solo dati fittizi:
area paziente Premium: https://claude.ai/artifact/FFKpJRz5SZ6C3VsAg3Ey2D
area paziente Base: https://claude.ai/artifact/NajX12E4Dg1o7wEvaav3PF
vista del medico: https://claude.ai/artifact/HYcWdRa8AUunov5CRysch5
Nessun collegamento reale a Sistema TS, FSE o INPS.

Principi da rispettare

Il paziente vede solo i propri dati. Non vede le ipotesi cliniche del medico, il suo diario o i dati di altri pazienti.
Ogni schermata con la chat ricorda: “Per un’urgenza chiama il 118: i messaggi non sono letti in tempo reale.” Lo studio risponde in orario di studio.
Nessuna interpretazione automatica dei valori o dei sintomi lato paziente: il significato lo spiega il medico. Questo tiene l’app fuori dalla classificazione come dispositivo medico (MDR); niente triage o chatbot clinici.
Le notifiche non contengono mai dati sanitari. Il testo fisso è: «Hai ricevuto una nuova comunicazione da parte del tuo medico su La mia Bussola».
Il servizio online è facoltativo: senza consenso tutto continua di persona o al telefono.
Lingua semplice, frasi brevi, niente sigle non spiegate.

Accesso e attivazione

Invito dallo studio: codice di attivazione o QR/link, poi 4 passi:
codice;
verifica dell’identità;
codice via SMS al cellulare registrato;
scelta del modo di accesso: impronta o volto (consigliato, passkey), PIN di 6 cifre (non la data di nascita) oppure SPID/CIE.
Impronta e volto restano sul telefono: lo studio non li riceve mai.
Senza invito: “Chiedi l’accesso” con codice fiscale, data di nascita e cellulare. Il medico approva, poi arriva il codice via SMS.
Gli accessi vengono registrati. Il medico può revocare l’area e il paziente vede una schermata dedicata.
La chiusura dell’area da parte del paziente non cancella la cartella clinica, che il medico deve conservare per legge.

Chat con lo studio (il cuore dell’app)

Il paziente scrive al medico o all’assistente di studio. Ogni messaggio mostra mittente, ora e tipo, con un’etichetta come “rinnovo”.
Tipi di messaggio strutturati:
richiesta di rinnovo di una terapia cronica già prescritta (un tocco sul farmaco). Stato visibile: ricevuta dallo studio → in preparazione → ricetta pronta, oppure “da rivedere con il medico”;
invio di un referto (PDF, JPG o PNG, massimo 10 MB, più due righe facoltative per il medico). Non si caricano test genetici o esami HIV, che vanno consegnati di persona;
messaggio libero;
invito a un controllo dallo studio, con risposte rapide: “Ho prenotato”, “L’ho già fatto”, “Mi serve la richiesta”, “Vorrei parlarne con il medico”.
Dal campo di scrittura si arriva subito a “allega un referto” e “chiedi un rinnovo”, sempre con il promemoria “Urgenze: 118”.
Farmaci nuovi o sintomi nuovi passano solo da un messaggio libero, mai dal rinnovo rapido.
Una pagina Orari e contatti dello studio indica anche l’assenza del medico e il suo sostituto (es. “assente dal 04/12 all’11/12, ti sostituisce Dott.ssa B. Prova”).

Altre funzioni dell’area paziente, da portare come stanze, schede o comandi

Avvisi in linguaggio semplice:
nuovi messaggi;
ricetta pronta (con codice NRE: in farmacia basta la tessera sanitaria);
rinnovo in lavorazione;
farmaco in esaurimento;
esame richiesto da fare;
piano terapeutico in scadenza;
vaccino antinfluenzale gratuito;
screening dovuti, con il numero della ASL;
visita prenotata con questionario da compilare;
delega in attesa.
Ricette: valide 30 giorni; stati “in preparazione”, “valida”, “ritirata”, “scaduta”; storico; promemoria da stampare o condividere.
Referti e documenti: visibili al massimo 45 giorni, poi si cancellano da soli (linee guida del Garante sui referti online).
Prenota una visita:
giorno e ora tra quelli liberi;
tipi di visita e numero di prenotazioni contemporanee decisi dallo studio;
lo studio può disattivare la prenotazione online;
per un problema urgente non si prenota.
Questionario “Prima della visita” (motivo, da quando…), che il medico legge prima di ricevere il paziente.
Esami e impegnative: NRE e priorità, istruzioni “Come prepararsi”, condivisione.
Certificati (richiesta; alcuni sono a pagamento) e Spese mediche per il 730 (fatture per anno, detraibilità, riepilogo da stampare).
Il mio calendario: visite, controlli, screening, vaccini e scadenze, esportabili sul calendario del telefono in formato .ics.
Promemoria farmaci (cosa prendere oggi, scorte residue) e Diario delle misure (pressione, glicemia, peso). Il medico vede le misure in cartella; gli obiettivi li fissa lui.
Scheda di emergenza: allergie, farmaci, eventuale terapia anticoagulante, contatto di emergenza, medico. Contiene un QR leggibile anche senza internet.
Familiari e deleghe: gestione dell’area di un familiare con delega approvata dal medico. Per i figli sotto i 14 anni basta essere il genitore registrato. Sempre visibile un banner “stai gestendo l’area di…”.
Privacy e consensi:
consenso al servizio online;
canale di avviso a scelta: SMS, WhatsApp, e-mail oppure nessuno;
titolare del trattamento: il medico;
“Scarica i miei dati”;
“Chiudi l’area personale”.
Accessibilità e lingua: testo normale, grande o molto grande; contrasto alto; “Leggi la pagina” ad alta voce; lingue italiano, inglese, rumeno e ucraino.

Lato studio, che la chat deve servire

Una conversazione per paziente, gestita da medico, assistente e infermiere secondo il ruolo. L’assistente prepara i rinnovi, il medico firma.
Una chat interna dello studio separata (medico, assistente, infermiere), mai mescolata con i pazienti.
I messaggi da leggere hanno un contatore. Ogni risposta al paziente fa partire la notifica neutra sul canale che ha scelto, e questo resta registrato.
Se il paziente non ha ancora attivato l’area, lo studio lo vede e il messaggio resta in attesa.

Indicazioni per Matrix (proposta)

Server: Synapse con dati in UE e registrazione chiusa (solo su invito o approvazione). Niente federazione aperta, oppure federazione limitata con ACL.
Stanze:
una stanza privata cifrata end-to-end per paziente, con il paziente, gli account dello studio ed eventuali delegati, aggiunti solo dopo l’approvazione della delega;
una stanza “Studio” solo per lo staff.
Messaggi strutturati: tipi di evento personalizzati (es. it.bussola.rx_request, it.bussola.report, it.bussola.checkup_invite) con un body testuale di riserva. In alternativa campi aggiuntivi in m.room.message. Gli stati, come quelli di un rinnovo, si aggiornano con eventi di relazione o modifiche.
Push: gateway (Sygnal) con formato event_id_only, così la notifica non porta contenuti; il testo mostrato è sempre quello neutro. SMS, WhatsApp ed e-mail passano da un servizio separato che invia solo la frase fissa.
Conservazione: policy di retention a 45 giorni per allegati e referti nelle stanze paziente. Copia in cartella lato gestionale, non nella chat.
Accesso: passkey o PIN sul dispositivo; SPID/CIE tramite un gateway OIDC (con Matrix Authentication Service o equivalente).
Bot dello studio: per le richieste strutturate, i promemoria, le risposte rapide e il collegamento simulato al gestionale.
Client: partire da matrix-rust-sdk (o dal codice di Element X) e semplificare l’interfaccia per pazienti anziani: grandi pulsanti con le azioni rapide sopra la chat.

Aspetto grafico

Coerente con la suite Eurise: fondo “Perla” (
#F4F4F1) con schede bianche, tema scuro “Notte” (
#14171C), carattere Plus Jakarta Sans, angoli morbidi (12–24 px), barre in vetro satinato, pulsanti a pillola, icone a tratto pieno.
Accento verde petrolio BUSSOLA (
#135C63; 
#79BCC1 nel tema scuro).
Avvisi nelle tinte Eurise: borgogna per gli urgenti, ottone per quelli da tenere d’occhio, salvia per quelli a posto.
Questo stile è ancora in anteprima, sul solo prototipo Premium medico, versione 26.11.15.1.

Prima di andare in produzione: valutazione d’impatto privacy (DPIA, dati sanitari, art. 9 GDPR), nomina del fornitore come responsabile del trattamento e registro degli accessi.
## Sicurezza, 21 novembre 2026 (progetto Eurise Sicurezza)
Chat: la chiave di recupero affidata all'app Eurise ora è sigillata nel Keystore lì (`SuiteSecrets`); nessuna modifica nella Chat. Dettagli in `docs/SICUREZZA.md`, «Crittografia».

## Sicurezza, 22 novembre 2026, mezzogiorno (progetto Eurise Sicurezza)
R8 anche negli APK di prova (giro 1048): regole comuni prudenti in `proguard-suite.pro` (codice `eu.eurise` tenuto, nomi leggibili), più il `proguard-rules.pro` dell'app. Una libreria nuova che si carica per nome (riflessione, JNI) va tenuta lì; se un'app si guasta: `-Peurise.r8.senza=:modulo`. Dettagli in `docs/SICUREZZA.md`.

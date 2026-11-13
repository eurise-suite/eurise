# Eurise – indicazioni per chi lavora al codice

## Metodo
- Prima del codice vengono sempre le anteprime: render sulla tela di design, discussione con il proprietario,
  e solo dopo l'approvazione si scrive il codice dell'app.
- Il documento di progetto e la tela "Icone della suite" (pagina Calendario, fila "Dopo") sono il riferimento.
- La chiave di firma delle versioni ufficiali non si chiede e non si tocca: la custodisce solo il proprietario.

## Conversazioni lunghe (regola, 8 novembre 2026)
- Quando la chat con il proprietario diventa lunga (molti lavori, contesto riassunto più volte), avvisarlo alla
  consegna dell'APK e consigliare una chat nuova, con le istruzioni pronte da incollare: leggere CLAUDE.md e
  docs/STATO.md, il ramo su cui si lavora, l'ultimo APK, i lavori in corso e il prossimo passo.
- Prima di consigliare la chat nuova, aggiornare docs/STATO.md perché contenga tutto ciò che serve a ripartire.

## Codice
- Kotlin + Jetpack Compose (Material 3), MVVM, un modulo per funzione (`feature/*`), servizi comuni in `core/*`.
- I moduli `feature` dipendono solo da `core`, mai tra loro; la navigazione passa dal modulo `app`.
- KDoc sulle parti non ovvie, niente commenti ridondanti. Testi dell'interfaccia in `strings.xml`.
- Nessuna libreria Google proprietaria (Play Services, Firebase, ML Kit): compatibilità F-Droid.
- Si compila solo su GitHub Actions (l'ambiente di Claude non raggiunge i repository Maven di Google).
- Il documento di progetto e la sezione "Integrazioni tra le app" decidono l'ordine delle integrazioni.

## Caratteristiche distintive (8 novembre 2026)
Ogni scelta si misura su queste: schermata iniziale molto informativa; tastiera eccellente; privacy e codice
aperto; leggerezza; funzionalità; integrazione tra le app; rapidità nel flusso di lavoro e anche nello staccare;
ricerca "totale" su tutta la suite. Tenere molte app in un APK aiuta sia l'integrazione sia la leggerezza.

## Privacy sempre chiara (regola fissa, 8 novembre 2026)
- Massima funzionalità con massima privacy: di base tutto resta sul telefono; ciò che esce lo sceglie l'utente.
- L'utente sa sempre a che livello di privacy si trova: il centro privacy mostra dove sta ogni dato, quali
  servizi esterni vengono contattati e con quali dati, quali permessi sono attivi.
- Ogni funzione che cambia la privacy (dati che escono, permessi, analisi di contenuti) si accende e si spegne
  con un interruttore; accenderla mostra prima una spiegazione breve: cosa viene letto, dove va, chi lo vede,
  come si torna indietro. Spegnerla non chiede conferme.
- Nessun servizio esterno senza che compaia nel centro privacy con i dati che riceve. Si invia il minimo
  (es. posizione arrotondata), mai identificativi del telefono.

## Servizi chiusi: si offrono, decide l'utente (regola fissa, 12 novembre 2026)
- Google, Apple, Microsoft e gli altri servizi chiusi (posta, cloud, AI, motori di ricerca, mappe…) si possono usare:
  siamo aperti anche in questo, e la scelta è dell'utente.
- Ma vanno scoraggiati: sempre per ultimi negli elenchi, con valutazione di privacy bassa (una tacca), e prima di
  collegarli un avviso chiaro su limiti e pericoli (dove stanno i dati, chi li vede, cosa ne fanno, come si torna indietro)
  con le alternative libere ed europee accanto.
- Dove l'accesso «due tocchi» richiede che Eurise sia registrata presso il servizio (OAuth), è un passo una tantum del
  proprietario; nel frattempo si può offrire la via che il servizio permette (es. password per le app) con l'avviso.

## Collegamenti in due tocchi (regola fissa, 9 novembre 2026)
- Collegare un servizio deve costare due tocchi: "Collega", accesso nel browser, approva. All'utente non si chiedono mai
  token API, chiavi, numeri di servizio o indirizzi tecnici. Registrare l'app presso un servizio è un passo una tantum del proprietario.
- Un servizio che non permette questo percorso non entra nell'interfaccia, anche se il codice è pronto.

## Dati e backup (deciso l'8 novembre 2026)
- Tre livelli: sul telefono (sempre), sincronizzazione con il servizio scelto, backup cifrato di tutta la suite.
- Backup cifrato sul telefono prima di uscire, con frase segreta dell'utente e codice di recupero (senza, nessuno
  può aprirlo, nemmeno noi). Frequenza: giornaliero, settimanale, mensile o a richiesta; con Wi-Fi e in carica.
- Si tengono i tre backup più recenti più uno mensile e uno trimestrale. Il nuovo backup prende il posto del più
  vecchio dei tre recenti (che diventa il mensile o il trimestrale quando tocca), e solo dopo che il nuovo è
  caricato e verificato. Le foto restano fuori (hanno la loro sincronizzazione).
- Destinazioni con una valutazione di privacy (server proprio, Murena, kDrive, Proton, MEGA, poi i servizi USA con
  avviso) e cartella locale o chiavetta. Ripristino dalla prima schermata del telefono nuovo.

## Struttura degli APK (decisa l'8 novembre 2026)
- APK principale "Eurise": Scrivania, Oggi, Calendario, Attività, Note, Contatti, Cloud, Orologio (unito l'8 novembre:
  troppo integrato per restare fuori), File, Scanner e lettore PDF (9 novembre). Office resta un APK separato. Ogni app ha la sua icona
  nel cassetto e si può spegnere; spenta, non carica nulla.
- APK separati per Password, Galleria, Tastiera, Mappe, Browser, Messaggi e Telefono. Parlano con il
  principale solo tra app firmate con la stessa chiave (permessi di firma), mai con canali aperti alle altre app.
- Niente moduli dinamici del Play Store: non funzionano su F-Droid né con l'installazione diretta.

## Sistemi (regola fissa, 11 novembre 2026)
- Prima /e/OS e Android; le app pensate anche per il computer (per ora Browser e Office) nascono per Linux nello stesso
  lavoro, con **Ubuntu e Fedora come riferimento**: si provano sull'ultima Ubuntu LTS, l'ultima Ubuntu e l'ultima Fedora
  (GNOME, poi KDE); pacchetto Flatpak su Flathub.
- Struttura per arrivare ad altri sistemi mobili e desktop: nucleo comune senza API di un solo sistema, interfaccia
  condivisa dove si può, un guscio sottile per sistema. Dettagli in `docs/PIATTAFORME.md`.

## Leggerezza (regola fissa, decisa l'8 novembre 2026)
- Peso: la versione ufficiale dell'APK principale pesava 2,5 MB l'8 novembre; tetto 6 MB a suite completa.
  La compilazione la pesa a ogni giro (riepilogo e avviso; avvertimento oltre il tetto). Un ramo che la fa
  crescere di più di 300 KB lo spiega nel messaggio del commit.
- Librerie: nessuna nuova senza un motivo scritto nel commit; mai una libreria intera per una funzione piccola.
  Icone solo da `EuriseIcons` (niente material-icons). Pezzi grafici comuni in `core/designsystem`, mai copiati.
- Contenuti pesanti (modelli AI, mappe, voci, sfondi grandi) si scaricano a richiesta, mai dentro l'APK.
- Avvio: in `Application.onCreate` solo registrazioni leggere; niente disco o rete sul thread principale; i dati
  si caricano quando la schermata li chiede. Il database si apre una volta sola (`EuriseData.create`).
- Memoria: liste lunghe in `LazyColumn` con chiavi stabili; immagini ridotte alla dimensione mostrata; flussi
  condivisi con `SharingStarted.WhileSubscribed(5_000)` e letti con `collectAsStateWithLifecycle`.
- Batteria: lavori in background con WorkManager e vincoli (rete, carica); niente servizi sempre attivi;
  analisi pesanti solo con il telefono in carica.
- Database: indici sulle colonne filtrate; conteggi con query, non leggendo tutto; migrazioni senza perdita di dati.
- Qualità: la logica che si può provare senza telefono (lettori, calcoli, formati) ha i suoi test.

## Ergonomia (regola importantissima, 13 novembre 2026)
- Sempre massima attenzione all'ergonomia: ciò che si usa spesso sta dove arriva il pollice. Se serve, la stessa funzione
  si declina diversamente su un formato diverso (telefono, tablet, computer) invece di copiare la disposizione.
- Mano dominante: impostazione della suite (destra di base, sinistra per i mancini). La lente della ricerca e i comandi
  principali delle barre stanno dal lato di quella mano.
- Ricerca in alto che si tira giù: dove un'app ha il campo di ricerca in alto, uno swipe verso il basso con la lista già in
  cima porta il campo appena sopra la tastiera, sotto il pollice; avviata la ricerca, torna al suo posto. Dove il campo non
  c'è, il gesto fa quello che faceva già (per esempio scorrere un documento). La prima volta il campo accenna a scendere.
- «Tirare giù» non aggiorna più le liste: l'aggiornamento è un pulsante nella zona del campo di ricerca, così scende con
  lui ed è raggiungibile con lo stesso gesto.

## Stile (deciso il 7 novembre 2026)
- Tema chiaro Perla, tema scuro Notte (blu-grafite, accento azzurro).
- Barra delle sezioni flottante e satinata con il pulsante + satinato accanto; la sezione attiva si riconosce
  da colore, tratto più marcato ed etichetta in grassetto, senza alone.
- Calendari: otto tinte della suite; ogni calendario prende la tinta più vicina al suo colore.

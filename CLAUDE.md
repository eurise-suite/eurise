# Eurise – indicazioni per chi lavora al codice

## Principi di base (regola fissa, 22 novembre 2026: valgono per ogni app, già fatta o futura)
Ogni scelta, in ogni app, si misura su queste coordinate. Una funzione che ne tradisce una non entra, anche se il codice è pronto.
1. **Sicurezza**: nessuna porta aperta alle altre app (solo permessi di firma della suite, `SuiteAccess`), nessuna backdoor,
   nessuna libreria chiusa, contenuti scaricati con impronta, file in arrivo controllati (`isForeignUri`). Dettagli e stato
   in `docs/SICUREZZA.md`; ogni app nuova passa dalla prova delle porte sull'emulatore prima del telefono.
2. **Privacy**: di base tutto resta sul telefono; ogni servizio che fa uscire dati è spento di base e sta nel centro privacy
   (sezioni «Privacy sempre chiara» e «Spenti di base» qui sotto). Senza sicurezza la privacy non vale nulla.
3. **Funzionalità** al livello dei migliori concorrenti **e oltre**: prima si studia cosa fanno i migliori (app di sistema,
   Google, Apple, Samsung e le migliori libere) e si scrive nella proposta dove li superiamo.
4. **Opzioni intelligenti e «game changer»**: in ogni app almeno qualche funzione che nessuno ha, utile davvero, pensata
   per far risparmiare tempo; si propone sulla tela e decide il proprietario.
5. **Utilità nel lavoro e nella vita di tutti i giorni**, con riguardo particolare per **chi lavora** (professionisti,
   artigiani, studi, squadre): flussi rapidi, poche mosse, dati che finiscono dove servono (cliente, progetto, pratica).
6. **Ergonomia**: comandi sotto il pollice e dal lato della mano dominante, tocco breve = preferito, pressione lunga = scelte
   (sezione «Ergonomia»); telefono, tablet e computer ognuno con la sua disposizione.
7. **Design moderno, caldo e impeccabile, da riferimento**: stile della suite (Perla e Notte, `core/designsystem`,
   `EuriseIcons`), cura dei dettagli, movimento fluido; le anteprime sulla tela vengono prima del codice (sezione «Metodo»).
8. **Integrazione profonda con la suite** (le app si passano dati e azioni tra loro: un contatto, un impegno, una nota, un
   file vanno dove servono senza copiarli a mano) **e libertà**: ogni app separata funziona **anche da sola**, senza le
   altre installate. L'integrazione si accende quando l'altra app c'è (controllata con il permesso di firma) e sparisce
   senza errori quando manca; mai un'app che obbliga a installare la suite.
9. **Leggerezza e velocità** (sezione «Leggerezza»): avvio rapido, scorrimento fluido, niente lavoro inutile.

## Metodo
- Prima del codice vengono sempre le anteprime: render sulla tela di design, discussione con il proprietario,
  e solo dopo l'approvazione si scrive il codice dell'app.
- Il documento di progetto e la tela "Icone della suite" (pagina Calendario, fila "Dopo") sono il riferimento.
- La chiave di firma delle versioni ufficiali non si chiede e non si tocca: la custodisce solo il proprietario.

## APK in chat (regola fissa, 14 novembre 2026; aggiornata il 15 novembre)
- Ogni volta che si aggiorna qualcosa, appena la compilazione riesce si danno in chat i **link diretti di download** di ogni
  APK cambiato, uno per app con nome e giro (es. «Office, giro 586: https://github.com/eurise-suite/eurise/releases/download/apk-prova/eurise-office-debug.apk»),
  presi dalla pre-release `apk-prova` (`browser_download_url`). Aperti dal browser del telefono si installano subito.
- Non il link alla pagina della release né gli artefatti: il link diretto al file. I file mandati come allegato della chat
  oltre ~40 MB arrivano sul telefono come archivio e non si installano (15 novembre): niente allegati per gli APK grandi.
  Se un APK richiede più tempo (Office, Mappe), si danno subito i link di quelli pronti e gli altri appena arrivano.

## Chiave di prova (regola fissa, 18 novembre 2026)
- La vecchia chiave di prova (`app/debug-key`) è pubblica: gli APK di prova arrivano al telefono **solo** da `apk-prova` e
  `apk-prova-office`, dove `.github/scripts/firma-prova.sh` li rifirma con la chiave nuova (segreto `EURISE_PROVA_KEYSTORE`)
  e la prova di rotazione. Mai dare al proprietario un APK preso dagli artefatti o firmato in altro modo: ha la sola chiave
  vecchia e non si installa sopra (o, peggio, non ha i permessi della suite).
- Ogni nuovo flusso che pubblica APK di prova chiama lo stesso script. Dettagli in `docs/SICUREZZA.md`.

## Strumenti (regola fissa, 18 novembre 2026)
- La suite compila con AGP 9.5, Gradle 9.6, Kotlin 2.3, KSP 2.3, compileSdk 37 (Browser 37.2), Compose BOM 2026.08.
  Per ora `android.builtInKotlin=false` e `android.newDsl=false` in gradle.properties (da togliere prima di AGP 10).
- Nei moduli il bersaglio Java si scrive `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }` (non più
  `kotlinOptions`); niente Provider in `sourceSets` (cartelle già risolte); un modulo nuovo usa compileSdk 37.
- Il Browser usa sempre l'ultima GeckoView (correzioni di sicurezza di Firefox circa ogni quattro settimane).

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
- Il grado di privacy si misura con **10 tacchette piccole** a semaforo (non 3 o 4 tacche), ovunque compaia:
  servizi, destinazioni, fonti di dati (13 novembre 2026).
- **Spenti di base** (regola fissa, 14 novembre 2026): ogni servizio che può far uscire dati è spento di base. Si accende
  solo se l'utente lo vuole, in modo facile e visibile, con l'avviso su cosa succede e su come cambia la privacy.
- Nessun servizio esterno senza che compaia nel centro privacy con i dati che riceve. Si invia il minimo
  (es. posizione arrotondata), mai identificativi del telefono.

## Servizi chiusi: si offrono, decide l'utente (regola fissa, 12 novembre 2026)
- Google, Apple, Microsoft e gli altri servizi chiusi (posta, cloud, AI, motori di ricerca, mappe…) si possono usare:
  siamo aperti anche in questo, e la scelta è dell'utente.
- Ma vanno scoraggiati: sempre per ultimi negli elenchi, con valutazione di privacy bassa (una o due tacchette su dieci), e prima di
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

## Ergonomia (regola fissa, 14 novembre 2026)
- I comandi usati spesso stanno dal lato della mano dominante: a destra per i destrimani, a sinistra per i mancini
  (impostazione «mano» già usata dalla ricerca); il comando principale sotto il pollice, il più usato dopo subito accanto.
- Tasti dello stesso gruppo della stessa larghezza.
- Campi password (regola fissa, 24 novembre 2026): ogni campo che nasconde ciò che si scrive ha sempre l'occhio per
  vederlo (`SecretEye` e `secretText` in `core/designsystem`, `component/Secret.kt`).
- Tocco breve = l'opzione preferita; pressione lunga = l'elenco delle opzioni, con la stellina per scegliere il preferito
  e «Aggiungi» (da un'app installata o dal web) dove ha senso. Vale ovunque ci sia una scelta di servizio.
  Eccezione decisa dal proprietario (27 novembre 2026): nella dock della Postazione il tocco apre le app della categoria
  (ventaglio) e la pressione lunga apre subito la preferita.
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

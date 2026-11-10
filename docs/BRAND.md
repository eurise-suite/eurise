# Eurise – brand (ramo `brand`, nato da `tastiera`)

Nota di passaggio per chi riprende il lavoro sul marchio. Questo file è separato da `docs/STATO.md`
per non toccare i file su cui lavora la chat del ramo `tastiera`.

## Decisione (10 novembre 2026, sera)
- **Simbolo approvato: la stella polare** (variante `polare` / `polarep` di `BrandMark`, tavola `BrandPolare`).
  Polare al centro con raggi sottili, 44 stelle (Stati d'Europa, conteggio ONU) disposte a sfera, più piccole
  allontanandosi; nessuna tocca la polare. Sotto i 40 px (simbolo) o 72 px (icona) si usa la versione con 16 stelle.
  Colori approvati: blu notte #1F2A44 e oro #D2B06A (ottone #85672B sui fondi chiari).
- Portato su tutte le tavole: logotipo, famiglia, Android, avvio, F-Droid, sito. La firma degli APK separati è ora
  una stellina d'oro (non più il sole). Restano da approvare: famiglia, schermata di avvio, frase, nome e dominio.

## Stato (10 novembre 2026)
- Anteprime sulla tela "Icone della suite", pagina nuova **Brand** (https://claude.ai/artifact/AauRJ8e5WdH7nXTqAEXkoo).
  Nessuna è ancora approvata: il codice non è stato toccato.
- Tavole:
  - `BrandSimbolo`: tre simboli a confronto. A «Alba» (evoluzione dell'icona attuale), B «Cerchio d'alba»
    (sette punti dorati ad arco: Europa che sorge; da far valutare per la vicinanza all'emblema UE),
    C «La e che sorge» (e minuscola aperta, la barra dorata è l'orizzonte). Consigliata C.
  - `BrandLogo`: logotipo in Plus Jakarta Sans (minuscolo grassetto consigliato), marchio completo chiaro,
    scuro, di marca e monocromatico, nomi delle app («eurise password»), regole d'uso.
  - `BrandFamiglia`: icona principale e nove APK (Password, Galleria, Tastiera, Voce, Office, Mappe, Browser,
    Messaggi, Telefono) in tre famiglie: «Firma d'alba» (consigliata: piccolo sole dorato sull'orizzonte sotto
    il simbolo), «Orizzonte», «Sobria» (uguale alle icone B della Scrivania).
  - `BrandAndroid`: livelli dell'icona adattiva (sfondo colore, primo piano, monocromatico), forme, icone a tema
    chiare e scure, icona piccola delle notifiche.
  - `BrandPalette`: Blu notte #1F2A44 e Oro d'alba #D2B06A come colori di marca, Ottone #85672B per l'oro sui
    fondi chiari, Crema #F3EBDD per il tratto; Perla, Notte e otto tinte invariate; Plus Jakarta Sans.
  - `BrandVoce`: tono di voce (chiara, onesta, breve, sobria, precisa), esempi così / non così, parole della
    casa, tre frasi di presentazione da scegliere.
  - `BrandAvvio`: schermata di avvio, «Blu notte sempre» o «Segue il tema» (consigliata).
  - `BrandFDroid`: immagine in evidenza 1024×500 (due proposte), icona 512, riassunto, schermate.
  - `BrandSito`: pagina principale del sito.
  - `BrandNome`: verifica del nome (vedi sotto).
- Componenti riusabili sulla tela: `BrandMark` (simbolo, varianti alba/cerchio/e) e `BrandIcona`
  (icona di un'app, famiglia, forma, monocromatico).

## Seconda prova del simbolo (10 novembre, dopo la discussione)
- Il proprietario scarta la «e» (troppo vicina al simbolo di /e/OS, con cui si spera in una collaborazione) e il
  sole (dice poco). Colori approvati. Chiede una stella o una corona di stelle che nasce, anche con logo animato.
- Tavole `BrandStelle` (D Stella che nasce, E Corona che sorge consigliata, F Stelle che salgono; stelle a quattro
  punte, mai dodici a cinque punte in cerchio, per restare lontani dall'emblema UE) e `BrandStelleVive`
  (logo animato: orizzonte, stelle che salgono, lampo, cielo dorato, nome; si ferma con «Riduci animazioni»).
- `BrandMark` e `BrandIcona` hanno le varianti nuove (corona, stella, salita); le tavole della prima prova
  restano con il selettore vecchio finché non si sceglie.

## Terza prova (10 novembre): tavola `BrandStelleMare`, animata
- G «La e stellata» (metà alta della e = corona), G2 «La e sul mare» (barra a onda), H «Corona sul mare»,
  I «Tondo marino» (corona in alto, onda al centro, il mare chiude il cerchio: consigliata per le icone tonde).
- Varianti aggiunte a `BrandMark`/`BrandIcona`: estelle, emare, mare, tondo.

## Quarta prova (10 novembre): tavola `BrandRiflesso`, animata
- Il proprietario non è convinto del tratto crema: vuole il mare intuito, non disegnato, con le stelle che si specchiano.
- J «Riflesso» (consigliata: solo oro, corona sopra e riflessi spezzati sotto, il cerchio nasce da solo),
  K «Scia di luce» (la luce della stella centrale si allunga sull'acqua), L «Riflesso e filo d'orizzonte».
- Varianti `riflesso`, `scia`, `filo` in `BrandMark`/`BrandIcona`. Dentro gli SVG si usa l'attributo `display`
  al posto di `sc-if` (anche in `BrandStelleVive` e `BrandStelleMare`).

## Quinta prova (10 novembre): tavola `BrandProve5`, animata
- M «La e che si intuisce»: mezza corona in alto, orizzonte a tratti singoli (barra della e), riflessi singoli
  a sinistra e sotto, cerchio aperto a destra.
- N «La stella e quattro compagne», N2 con tre compagne. Varianti `eintuita`, `compagne`, `tre`.

## Sesta prova (10 novembre): tavola `BrandProve6`, animata
- M2 «La e, più piena»: stelle più tonde, orizzonte a quattro trattini spessi, riflessi a punti grandi quanto la
  stella specchiata (l'ultimo più piccolo, la e resta aperta). Variante `e2`.
- N3 «La stella polare»: grande al centro e cinque compagne a spirale, sempre più piccole allontanandosi.
  Idea del proprietario: ogni stella è un principio, la grande è la stella polare. Da decidere quale principio
  è la polare e l'ordine delle compagne. Variante `nucleo`. N4 con sei compagne: `nucleo6`.

## Settima prova (10 novembre): tavola `BrandCielo`, animata
- Dalla N4, su indicazione del proprietario: stella polare al centro più grande e 27 stelle (una per Stato
  dell'Unione) disposte a spirale, sempre più piccole allontanandosi: «il cielo del futuro europeo».
  Variante `cielo`. Sotto i 36 px le stelle lontane spariscono e resta la polare con le più vicine.
- Da verificare con il consulente: oro su blu richiama l'emblema UE (ma stelle a quattro punte, in spirale).

## Ottava prova (10 novembre): tavola `BrandSfera`, animata — direzione buona secondo il proprietario
- Polare più grande al centro; 44 stelle (Stati d'Europa secondo l'ONU, dell'Unione e non: apertura a tutti,
  augurio di un'Europa unita) disposte come su una sfera vista di fronte (spirale di Fibonacci proiettata):
  grandi davanti, piccole e fitte al bordo. Alternativa: 46 del Consiglio d'Europa.
- Due disegni: `sfera` (completa) e `sferap` (piccola: polare più grande, 21 stelle più grosse che coprono quasi
  tutto il tondo). `BrandMark` passa a `sferap` sotto i 40 px, `BrandIcona` sotto i 72 px; nell'icona il
  simbolo occupa il 90% del tondo.

## Nona prova (10 novembre): tavola `BrandGlobo`, animata
- Polare invariata («perfetta»). Stelle intorno a metà strada tra settima e ottava prova: 44 stelle più piccole,
  sfera raccolta a circa 4/5 del tondo; versione piccola con 16 stelle. Varianti `globo` e `globop`
  (cambio automatico come per `sfera`). La tavola confronta prima, ottava e via di mezzo nelle icone.

## Decima prova (10 novembre): tavola `BrandPolare`, animata
- Polare della stessa grandezza ma con raggi sottili (punto di controllo delle curve al 10% del raggio invece del 28%:
  niente più «rombo»). Copertura dell'area come la nona. Regola: nessuna stellina tocca la polare; distanza minima
  pari a quella delle due stelline che nella nona stavano in alto a destra (circa 1 unità su 48 dal bordo della
  polare e un raggio libero di circa 0,68 R attorno al centro). Varianti `polare` (44 stelle) e `polarep` (16).

## Verifica del nome (ricerche web del 10 novembre, non una ricerca di anteriorità)
- Euria (Infomaniak, assistente IA): vicinanza alta, stesso settore. EURISE Network (rete europea di software
  per la ricerca): stesso nome, non commerciale. eyrise (Merck, vetri): suono vicino in inglese. Eurise
  (minibus elettrici in Cina), Eurisotop, Everise: lontani.
- Domini: eurise.com registrato dal 2016 (scade ottobre 2027). .eu, .app, .org, .it, .dev da verificare a mano.
- Da decidere al proprietario: tenere il nome, dominio principale, deposito EUIPO (850 € una classe).

## Quando arriva l'approvazione (vincoli)
- Solo risorse delle icone e file nuovi del brand: niente file del ramo `tastiera`.
- Icone adattive vettoriali per ogni APK (`app`, `password`, `gallery`, `keyboard`, `voice`, poi gli altri) con
  livello `<monochrome>`; un solo colore di fondo `eurise_icon_background` #1F2A44.
- Tastiera: oggi usa le icone PNG di HeliBoard (mipmap-*dpi) → sostituirle con l'adattiva vettoriale
  (e un `ic_launcher_round` che punta alla stessa).
- Password, Galleria, Voce: aggiungere il livello monocromatico, oggi mancante.
- Schermata di avvio solo con attributi del tema (niente `core-splashscreen`), icona di notifica bianca per APK.
- Immagini F-Droid in `fastlane/metadata/android/it-IT/images/` ed `en-US`.

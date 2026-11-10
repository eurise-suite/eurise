# Eurise – brand (ramo `brand`, nato da `tastiera`)

Nota di passaggio per chi riprende il lavoro sul marchio. Questo file è separato da `docs/STATO.md`
per non toccare i file su cui lavora la chat del ramo `tastiera`.

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

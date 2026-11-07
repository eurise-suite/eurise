# Eurise – stato dei lavori (7 novembre 2026)

Nota di passaggio tra una chat e l'altra: chi riprende il lavoro legge prima questo file e `CLAUDE.md`.

## Riferimenti
- Documento di progetto: Claude Doc `81d73EgPHtgsSEd7YkavhU`.
- Tela di design "Icone della suite": https://claude.ai/artifact/AauRJ8e5WdH7nXTqAEXkoo
  (pagine: App e launcher, Icone, Calendario, Scrivania (launcher), Scrivania tablet).
- Repository: `eurise-suite/eurise`. Ramo di lavoro `launcher`, unito in `main` a ogni versione provata.
  L'APK di prova si scarica da GitHub Actions (artefatto `eurise-debug-N` della compilazione su `main`).

## Metodo concordato con il proprietario
- Prima le anteprime sulla tela, se ne parla, e solo dopo l'approvazione si scrive il codice.
  Le correzioni chiare (bug, colori, spaziature) si fanno direttamente.
- Si compila solo su GitHub Actions; gli errori si leggono dalle annotazioni della compilazione.
- Le versioni di prova sono firmate con una chiave di debug fissa (`app/debug-key/`, pubblica e senza valore),
  così si aggiornano senza disinstallare; il `versionCode` cresce con il numero della compilazione.
- La chiave delle versioni ufficiali non si chiede e non si tocca: sta solo nei segreti di GitHub.
- Si parla in italiano.

## Scrivania (launcher, `feature/launcher`) – fatto
- Tre pagine: sinistra (Notizie, Borsa, Musica, Oroscopo, Ricetta), home, Riepilogo (Calendario, Comunicazioni).
- Home: saluto, orario con alba e tramonto, galleria e fotocamera, meteo, schede modificabili
  (pressione lunga: aggiungi, togli, ridimensiona in modo continuo, altezze unite con la catena),
  preferiti ancorati in basso, cartelle, barra delle categorie (fino a 8, Preferiti eliminabili, 50 simboli).
- Le categorie (Preferiti compresi, ora categoria come le altre) si aprono con le app in basso, vicino al
  pollice, e il titolo subito sopra; toccare di nuovo la categoria aperta torna alla home.
- Griglia e icone: tema (segue lo sfondo, chiaro, scuro) e sfondo (automatico o scelto: stagioni e feste).
- Schede della home: appuntamenti, Ti hanno cercato (scorrimento: Rispondi, Letto, Elimina; risposta rapida),
  Salute (Health Connect), Lavoro, Musica, Video dai canali (NewPipe), più le schede delle pagine laterali.
- Pannello Eurise (scorrendo in giù da ogni pagina): interruttori, luminosità e volume, impostazioni,
  notifiche raggruppate per app e conversazione. Ricerca (scorrendo in su): contatti, azioni, app,
  conversazioni, note e attività, documenti.
- Sfondi illustrati che cambiano con stagione, meteo, feste e date personali, momento del giorno.
- Tema scuro: testo che segue il tema, accento notturno più scuro, bolle e icone scure; il testo in alto
  resta scuro finché il cielo è chiaro (sera), chiaro di notte.
- Colore dei simboli delle icone: "Dall'app" (colore dell'icona originale portato alla tinta della suite),
  per categoria o vario.

## Calendario (`feature/calendar`) – fatto
- Date importanti (compleanni, anniversari, relazione, promemoria regalo 10 giorni prima).
- Farmaci con orari di assunzione; le voci entrano automaticamente tra le attività con un avviso.

## Punti aperti
- Tablet: da provare sul Volla quando arriva (pagina "Scrivania tablet" sulla tela).
- Oroscopo: nuova fonte per segno (oroscopi.info); da confermare sul telefono che il testo compaia.
- Fonti non ancora verificate sul telefono: Borsa (Yahoo), alcuni feed di notizie e ricette, video dei canali.
- Prossimo passo: scegliere con il proprietario la prossima app (Calendario da completare, oppure Note,
  Attività, File, Contatti…), partendo dalle anteprime sulla tela.

# Eurise Mappe – base CoMaps

Base approvata dal proprietario l'11 novembre 2026: **CoMaps** (Apache 2.0, fork comunitario di Organic Maps, su F-Droid).
Non Organic Maps: dall'agosto 2025 i suoi dati delle mappe hanno una licenza non libera.

## Come si compila (lavoro «Prototipo di Eurise Mappe», `.github/workflows/maps-engine.yml`)
- Sorgenti da `https://codeberg.org/comaps/comaps.git` al commit fissato `COMAPS_COMMIT` (11 novembre 2026), sottomoduli
  senza storia. I sorgenti non stanno nel nostro repository: sono grandi (C++ con boost, ICU, protobuf…).
- `maps/eurise_patch.py` cambia solo nome («Eurise Mappe»), pacchetto (`eu.eurise.maps`, senza suffissi) e firma (chiave
  di debug della suite, così il permesso di firma vale con l'APK principale). Il codice Java resta in `app.organicmaps`.
- `./configure.sh` (stili, simboli, testi, mappe del mondo) e `./gradlew -Parm64 assembleFdroidRelease` (release ridotta con R8 e librerie senza simboli; senza i file segreti di CoMaps la firma è la nostra chiave di debug), con ccache salvata
  nella cache di Actions. Java 21 come chiede CoMaps.
- Artefatto `eurise-mappe-debug-N`: APK, misure (tempo per fase, peso per parte dell'APK), registro di Gradle.

## Peso (giro 5, 11 novembre)
Giro 3: APK arm64 di 57 MB (simboli 11,7, mappe del mondo e file 11,5, caratteri 10,1, codice e risorse 10,1, nomi dei
paesi 6,7, libreria 6,0). `maps/eurise_slim.py`, dopo `configure.sh`, esclude dall'APK (con `ignoreAssetsPattern`; i file restano su disco perché
Gradle rigenera i simboli che mancano): simboli mdpi, hdpi e xxxhdpi (restano
xhdpi, 6plus, xxhdpi; `eurise_patch.py` aggiorna la tabella di `VisualParams`), i caratteri delle scritture rare (CJK,
tibetano, birmano, khmer, code2000: il motore usa i Noto del sistema e salta i file che mancano), i nomi dei paesi nelle
lingue non europee (per quelle il motore usa l'inglese). Stima: circa 17 MB in meno. Dopo: caratteri rari scaricabili a
richiesta nella cartella dei dati dell'app (il motore li cerca anche lì), se servono.

## Stile della suite (giro 8, 12 novembre)
`maps/eurise_style.py` (chiamato da `eurise_patch.py`) riscrive i valori dei colori di CoMaps: tema Material 3 e colori
di base delle schermate a View diventano Perla (chiaro, accento blu notte) e Notte (scuro, accento azzurro); 100 colori,
provati sui sorgenti del commit fissato. Icona della suite (`docs/brand/android/maps`) al posto di quella di CoMaps.

## Passi seguenti (dalle tavole approvate: MapGuida, MapEsplora, MapAttivita, MapLivelli, MapPercorso, MapCerca)
1. Prova del prototipo sul telefono così com'è (CoMaps con il nostro nome).
2. Stile della suite: tema Perla/Notte, barra satinata, icone EuriseIcons; schermate nuove in Compose dove si può
   (CoMaps usa le View di Android: si parte da ricerca, livelli e scheda del luogo).
3. Guida: velocità attuale e limite (OSM), frase d'arrivo, meteo sul percorso da `core/data/weather`.
   Richiesta del proprietario (11 novembre): il cartello dell'indicazione prende il colore del tipo di strada, con le tinte
   della suite (autostrada = verde della suite, statale/extraurbana principale = blu della suite, provinciale e comunale
   con tinte proprie); i metri alla svolta si aggiornano in tempo reale (a passi di 10 m sotto i 200 m, poi 50/100 m).
   In CoMaps la classe della strada c'è già (tag `highway` di OSM: motorway, trunk, primary, secondary…).
4. Suite: «dove devi andare» da Calendario, Viaggi e Rubrica; manda l'arrivo con Messaggi; percorso legato all'impegno.
5. Benzinai col prezzo (open data MIMIT, CSV quotidiani, licenza IODL 2.0; dal 10 febbraio 2026 è cambiato il separatore).
6. Centro privacy: cosa resta sul telefono (mappe, percorsi) e cosa esce (download delle mappe, prezzi, meteo).
Traffico in tempo reale: nessuna fonte libera nazionale; resta spento «in prova». Lettura dei cartelli con la fotocamera:
serve un modello nostro (dataset liberi piccoli), fuori da GitHub Actions.

# Eurise Mappe – base CoMaps

Base approvata dal proprietario l'11 novembre 2026: **CoMaps** (Apache 2.0, fork comunitario di Organic Maps, su F-Droid).
Non Organic Maps: dall'agosto 2025 i suoi dati delle mappe hanno una licenza non libera.

## Come si compila (lavoro «Prototipo di Eurise Mappe», `.github/workflows/maps-engine.yml`)
- Sorgenti da `https://codeberg.org/comaps/comaps.git` al commit fissato `COMAPS_COMMIT` (11 novembre 2026), sottomoduli
  senza storia. I sorgenti non stanno nel nostro repository: sono grandi (C++ con boost, ICU, protobuf…).
- `maps/eurise_patch.py` cambia solo nome («Eurise Mappe»), pacchetto (`eu.eurise.maps`, senza suffissi) e firma (chiave
  di debug della suite, così il permesso di firma vale con l'APK principale). Il codice Java resta in `app.organicmaps`.
- `./configure.sh` (stili, simboli, testi, mappe del mondo) e `./gradlew -Parm64 assembleFdroidDebug`, con ccache salvata
  nella cache di Actions. Java 21 come chiede CoMaps.
- Artefatto `eurise-mappe-debug-N`: APK, misure (tempo per fase, peso per parte dell'APK), registro di Gradle.

## Passi seguenti (dalle tavole approvate: MapGuida, MapEsplora, MapAttivita, MapLivelli, MapPercorso, MapCerca)
1. Prova del prototipo sul telefono così com'è (CoMaps con il nostro nome).
2. Stile della suite: tema Perla/Notte, barra satinata, icone EuriseIcons; schermate nuove in Compose dove si può
   (CoMaps usa le View di Android: si parte da ricerca, livelli e scheda del luogo).
3. Guida: velocità attuale e limite (OSM), frase d'arrivo, meteo sul percorso da `core/data/weather`.
4. Suite: «dove devi andare» da Calendario, Viaggi e Rubrica; manda l'arrivo con Messaggi; percorso legato all'impegno.
5. Benzinai col prezzo (open data MIMIT, CSV quotidiani, licenza IODL 2.0; dal 10 febbraio 2026 è cambiato il separatore).
6. Centro privacy: cosa resta sul telefono (mappe, percorsi) e cosa esce (download delle mappe, prezzi, meteo).
Traffico in tempo reale: nessuna fonte libera nazionale; resta spento «in prova». Lettura dei cartelli con la fotocamera:
serve un modello nostro (dataset liberi piccoli), fuori da GitHub Actions.

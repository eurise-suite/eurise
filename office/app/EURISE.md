# Eurise Office per Android: prototipo dell'app (dopo il motore)

Scelta approvata l'11 novembre (docs/PIATTAFORME.md): un'interfaccia sola in HTML/JS, quella di Collabora Online
(MPL 2.0), sopra il nostro motore LibreOffice; la stessa su Android, Linux (Collabora Office per il computer, Qt) e
gli altri sistemi. Questo lavoro costruisce l'app Android di Collabora con il nostro motore e la adatta a Eurise.

- Lavoro: `.github/workflows/office-app.yml` (a mano o quando cambiano `office/app/**`). Stesso motore e stessa ccache
  di «Prototipo del motore di Office» (`CORE_COMMIT`), poi POCO (`poco_android.py`, solo arm64, API 26) e zstd per
  Android, `online` al commit fissato (`ONLINE_COMMIT`, ramo `distro/collabora/co-25.04`) con `--enable-androidapp`,
  infine Gradle. Artefatto `eurise-office-app-N`: APK, misure, registri.
- Adattamenti: `eurise_css.py` (prima di make) mette `eurise.css` in coda alla tavolozza dell'interfaccia: Perla e Notte,
  accento azzurro, scheda attiva del nastro con tratto e grassetto, gruppi come isole bianche. `eurise_patch.py` (dopo
  configure): pacchetto `eu.eurise.office` (`.debug` per la prova), chiave di prova della suite.
- Passi seguenti, dalle tavole approvate (OffMotore, OffTelefono, OffCerca, OffTablet, OffComputer): scheda **Suite**
  del nastro (Detta, Traduci, Da fare, Evento, Note / Viaggio, Grafico) in `Control.NotebookbarWriter.js` e compagni,
  «Cerca un comando» (Ctrl K) con il riquadro di ricerca e le tacche, Eurise File per aprire e salvare (origine sempre
  scritta), motore scaricato a richiesta (circa 66 MB con il Wi-Fi) al posto della libreria dentro l'APK, italiano
  (sottomodulo translations del motore). Linux: `--enable-qtapp` e Flatpak, con le stesse modifiche all'interfaccia.

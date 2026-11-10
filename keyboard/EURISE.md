# Eurise Tastiera

Nasce da **HeliBoard 4.2** (https://github.com/HeliBorg/HeliBoard, tag `v4.2`, commit `0c60dffb`), licenza GPL-3.0-only
(parti AOSP Apache-2.0). Progetto Gradle a sé: segue le versioni di AGP e Kotlin di HeliBoard, così gli aggiornamenti si
riportano più facilmente. Si compila nel lavoro "APK della Tastiera" della compilazione.

## Cosa cambia rispetto a HeliBoard
- Identità: `eu.eurise.keyboard`, nome "Eurise Tastiera", chiave di debug della suite, risorse solo italiano e inglese.
- Aspetto: temi Perla e Notte di base, tasti arrotondati; fila dei numeri e simboli in piccolo accesi.
- Barra degli strumenti: tasto a destra (opzione per i mancini), strumenti di base dell'anteprima, opzione "fila a parte".
- Codice Eurise in `app/src/main/java/eu/eurise/keyboard/`; agganci in `LatinIME` (sezione "Eurise"), `Suggest`
  (scrittura scorrendo), `SuggestionStripView`, `KeyboardTheme`, `Defaults`, `ToolbarScreen`, `JniUtils`.
- Niente librerie chiuse: `JniUtils` non carica più la libreria di Google; la scrittura scorrendo usa `GlideDecoder`.
- Nessun accesso a Internet (come HeliBoard). Parla con Eurise, Password e Voce solo con il permesso della suite.

Per riportare una nuova versione di HeliBoard: confrontare i file elencati sopra, il resto si sostituisce.

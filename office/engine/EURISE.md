# Motore di Eurise Office: prototipo (punto 5 della road map)

Scopo: sapere se il motore LibreOffice, nella versione di Collabora Office per Android (MPL 2.0), si compila su
GitHub Actions e quanto costa, prima di disegnare le anteprime di Office per telefono e tablet.

- Sorgenti: `LibreOffice/core` (copia su GitHub di git.libreoffice.org), ramo `distro/collabora/co-25.04`, commit
  fissato nel lavoro (`.github/workflows/office-engine.yml`, variabile `CORE_COMMIT`). Non si copiano in `third_party/`:
  sono troppo grandi (circa 1 GB anche senza storia).
- Configurazione: `autogen.input.in` (base `CPAndroidAarch64`, solo arm64; il tablet Volla e i telefoni recenti
  sono arm64). Risultato: `liblo-native-code.so`, l'unica libreria che l'app carica, più i file dati
  (configurazione, filtri, interfaccia, caratteri) che l'app Collabora mette negli assets.
- Misure (`.github/scripts/office_size.py`, nel riepilogo del lavoro): tempo di ogni fase, spazio su disco
  (sorgenti, compilazione, archivi esterni, ccache), peso della libreria e degli assets, com'è e compresso, con e
  senza caratteri (che per la regola della leggerezza si possono scaricare a richiesta). Stima dell'APK.
- Tempo: un lavoro di GitHub dura al massimo 6 ore. Se la compilazione non finisce, la cache di ccache si salva
  lo stesso e il giro dopo riparte da lì: va rilanciato a mano (Actions → Prototipo del motore di Office →
  Run workflow) finché il riepilogo dice «compilazione finita».
- Non ancora: l'interfaccia web di Collabora (cartella `browser` di `CollaboraOnline/online`, serve per «Tutte le
  opzioni»), la parte `online` per Android, l'italiano. Vengono dopo la misura, se il proprietario dà l'ok.

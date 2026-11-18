Tesseract 5.5.4 (7e548e6eff172f38f97664afe7f7ce40db36c8d6; era 5.5.1 fino al 18 novembre 2026, vulnerabilità note segnalate da OSV-Scanner; Apache 2.0), copia ridotta: solo `include` e le cartelle di `src`
che compongono la libreria (niente programmi, addestramento, test, dati). Si compila con `read/src/main/cpp/CMakeLists.txt`,
senza il motore legacy (i modelli tessdata_fast sono solo LSTM) e senza la grafica di debug.

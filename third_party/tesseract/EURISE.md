Tesseract 5.5.1 (3b7c70e34dea179549ed3e995872e2e019eb8477, Apache 2.0), copia ridotta: solo `include` e le cartelle di `src`
che compongono la libreria (niente programmi, addestramento, test, dati). Si compila con `read/src/main/cpp/CMakeLists.txt`,
senza il motore legacy (i modelli tessdata_fast sono solo LSTM) e senza la grafica di debug.

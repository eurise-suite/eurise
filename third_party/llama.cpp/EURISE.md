llama.cpp af6b5243d6871923dfecbe06814bd566d9dc383b (11 novembre 2026, MIT), copia ridotta per Eurise AI:
solo `src` (con tutti i modelli: `llama-model.cpp` li chiama tutti), `include`, `LICENSE` e `ggml` con il solo backend
CPU (`ggml/src/ggml-cpu`, niente CUDA, Vulkan, Metal, OpenCL…). Il CMake è quello nostro in `ai/src/main/cpp`.

- Sul telefono: ggml condivisa con GGML_BACKEND_DL e GGML_CPU_ALL_VARIANTS, come l'esempio Android di llama.cpp
  (varianti android_armv8.0 … armv9.2 scelte all'avvio da `ggml_backend_load_all_from_path`). Niente KleidiAI (si
  scaricherebbe durante la compilazione e non accelera i Q4_K) e niente OpenMP (come Voce).
- La ggml di whisper.cpp (Voce) è la stessa versione 0.29 con 36 file diversi: quando Whisper entrerà in Eurise AI
  (tavola AiModelli) si userà una copia sola, questa.
- Per aggiornare: copiare di nuovo le stesse cartelle da un commit nuovo e cambiare LLAMA_BUILD_COMMIT nel CMake.

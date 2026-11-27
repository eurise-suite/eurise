Leptonica dal ramo principale a monte, commit c82d51632fe9a2712d2aee16a0f9c4c99eff7193 (24 novembre 2026): la 1.87.0
(13275a278eb55b5746e33f95fbf5a2c8f604b3ab, dicembre 2025) più 188 correzioni, quasi tutte controlli su dimensioni e indici
(letture e scritture fuori dai limiti trovate con il fuzzing e la ricerca di vulnerabilità di novembre 2026). Aggiornata
il 27 novembre 2026 per CVE-2026-137063; nessuna funzione tolta, solo `const` in più e quattro funzioni nuove. Era 1.85.0
fino al 18 novembre 2026. Licenza BSD a 2 clausole. Copia ridotta: solo `src` (file .c e .h).
Compilata senza librerie d'immagine esterne (niente jpeg, png, tiff, webp, gif, zlib): le immagini arrivano già come pixel
da Android. Si compila con `read/src/main/cpp/CMakeLists.txt`.
Alla prossima uscita ufficiale (1.88) si torna a un'etichetta.

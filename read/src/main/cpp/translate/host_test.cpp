// Prova sul computer del motore di Eurise Traduci: traduce le righe di stdin (una frase o un paragrafo per riga) e
// scrive una traduzione per riga; su stderr il tempo. Uso: eurise_translate_test modello vocabolario lista [vocab_destinazione]
#include <chrono>
#include <iostream>
#include <string>

#include "engine.hpp"

int main(int argc, char **argv) {
  if (argc != 4 && argc != 5) {
    std::cerr << "uso: eurise_translate_test modello.bin vocab.spm lex.bin [trgvocab.spm]\n";
    return 2;
  }
  auto start = std::chrono::steady_clock::now();
  eurise::Engine engine(argv[1], argv[2], argc == 5 ? argv[4] : "", argv[3]);
  auto loaded = std::chrono::steady_clock::now();
  std::string line;
  size_t words = 0;
  while (std::getline(std::cin, line)) {
    for (char c : line) words += c == ' ';
    ++words;
    std::cout << engine.translate(line) << "\n";
  }
  auto end = std::chrono::steady_clock::now();
  auto ms = [](auto a, auto b) { return std::chrono::duration_cast<std::chrono::milliseconds>(b - a).count(); };
  std::cerr << "caricamento_ms " << ms(start, loaded) << " traduzione_ms " << ms(loaded, end) << " parole " << words << "\n";
  return 0;
}

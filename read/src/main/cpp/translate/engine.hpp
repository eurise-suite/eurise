// Motore di traduzione di Eurise: un modello Bergamot (file .bin a 8 bit, vocabolario SentencePiece, lista breve) letto
// con slimt. Usato dal ponte JNI e dalla prova sul computer, così la compilazione misura esattamente il codice dell'app.
#pragma once
#include <cstring>
#include <memory>
#include <string>
#include <vector>

#include "slimt/Frontend.hh"
#include "slimt/Io.hh"
#include "slimt/Model.hh"
#include "slimt/Response.hh"

namespace eurise {

class Engine {
 public:
  /** [tgtVocab] vuoto per i modelli con un solo vocabolario (quasi tutti). Lancia un'eccezione se i file non vanno. */
  Engine(const std::string &model, const std::string &vocab, const std::string &tgtVocab, const std::string &shortlist)
      : service_(slimt::Config{}) {
    slimt::Package<std::string> package{
        .model = model, .vocabulary = vocab, .target_vocabulary = tgtVocab, .shortlist = shortlist, .ssplit = ""};
    size_t enc = 0, dec = 0;
    layers(model, enc, dec);
    slimt::Model::Config config = slimt::preset::tiny();
    if (enc > 0) config.encoder_layers = enc;
    if (dec > 0) config.decoder_layers = dec;
    model_ = std::make_shared<slimt::Model>(config, package);
  }

  std::string translate(const std::string &text) {
    slimt::Options options{};
    auto responses = service_.translate(model_, {text}, options);
    return responses.empty() ? std::string() : responses[0].target.text;
  }

 private:
  /** I modelli non dicono quanti strati hanno: si contano dai nomi dei parametri (encoder_l1…, decoder_l1…). */
  static void layers(const std::string &path, size_t &enc, size_t &dec) {
    slimt::io::MmapFile file(path);
    for (const auto &item : slimt::io::load_items(file.data())) {
      count(item.name, "encoder_l", enc);
      count(item.name, "decoder_l", dec);
    }
  }

  static void count(const std::string &name, const char *prefix, size_t &out) {
    const size_t n = std::strlen(prefix);
    if (name.compare(0, n, prefix) != 0) return;
    size_t i = n, value = 0;
    while (i < name.size() && name[i] >= '0' && name[i] <= '9') value = value * 10 + static_cast<size_t>(name[i++] - '0');
    if (i > n && value > out) out = value;
  }

  slimt::Blocking service_;
  std::shared_ptr<slimt::Model> model_;
};

}  // namespace eurise

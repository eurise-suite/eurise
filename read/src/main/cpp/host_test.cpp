// Prova sul computer del motore compilato come nel telefono: legge un'immagine PPM (P6) passando i pixel RGBA
// a Tesseract come fa il ponte JNI, e scrive il testo.
// Uso: eurise_ocr_test <cartella tessdata> <lingua> <immagine.ppm> [modo di segmentazione, es. 6]
#include <tesseract/baseapi.h>
#include <allheaders.h>
#include <cstdlib>
#include <cstdio>
#include <fstream>
#include <iostream>
#include <vector>

int main(int argc, char **argv) {
  if (argc != 4 && argc != 5) { std::cerr << "uso: eurise_ocr_test tessdata lingua immagine.ppm [psm]\n"; return 2; }
  setMsgSeverity(L_SEVERITY_NONE);
  std::ifstream in(argv[3], std::ios::binary);
  std::string magic; int w, h, max;
  in >> magic >> w >> h >> max;
  in.get();
  if (magic != "P6" || max != 255) { std::cerr << "serve un PPM P6 a 8 bit\n"; return 2; }
  std::vector<unsigned char> rgb(static_cast<size_t>(w) * h * 3), rgba(static_cast<size_t>(w) * h * 4);
  in.read(reinterpret_cast<char *>(rgb.data()), rgb.size());
  for (size_t i = 0; i < static_cast<size_t>(w) * h; ++i) {
    rgba[i * 4] = rgb[i * 3]; rgba[i * 4 + 1] = rgb[i * 3 + 1]; rgba[i * 4 + 2] = rgb[i * 3 + 2]; rgba[i * 4 + 3] = 255;
  }
  tesseract::TessBaseAPI api;
  if (api.Init(argv[1], argv[2], tesseract::OEM_LSTM_ONLY) != 0) { std::cerr << "init fallito\n"; return 1; }
  if (argc == 5) api.SetPageSegMode(static_cast<tesseract::PageSegMode>(std::atoi(argv[4])));
  api.SetImage(rgba.data(), w, h, 4, w * 4);
  char *text = api.GetUTF8Text();
  std::cout << text;
  std::cerr << "fiducia " << api.MeanTextConf() << "\n";
  delete[] text;
  api.End();
  return 0;
}

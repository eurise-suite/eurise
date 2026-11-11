// Prova sul computer di Eurise AI: stesso motore dell'APK. Legge le frasi da stdin (una per riga) e scrive una riga
// JSON per ognuna, poi la misura della velocità. Uso: eurise_ai_test MODELLO THREAD [AAAA-MM-GG]
#include <cstdio>
#include <iostream>
#include <string>

#include "engine.hpp"

static void quiet(enum ggml_log_level level, const char *text, void *) {
    if (level == GGML_LOG_LEVEL_ERROR) std::fputs(text, stderr);
}

int main(int argc, char **argv) {
    if (argc < 3) {
        std::fprintf(stderr, "uso: %s MODELLO THREAD [AAAA-MM-GG]\n", argv[0]);
        return 2;
    }
    llama_log_set(quiet, nullptr);
    int y = 2026, m = 11, d = 11;
    if (argc > 3) std::sscanf(argv[3], "%d-%d-%d", &y, &m, &d);
    eurise_ai::Engine *e = eurise_ai::load(argv[1], std::atoi(argv[2]), 4096);
    if (!e) {
        std::fprintf(stderr, "modello non caricato\n");
        return 1;
    }
    char desc[128] = {0};
    llama_model_desc(e->model, desc, sizeof(desc));
    std::printf("{\"model\":\"%s\",\"params\":%llu,\"bytes\":%llu,\"ms_load\":%.0f}\n", desc,
                (unsigned long long) llama_model_n_params(e->model), (unsigned long long) llama_model_size(e->model), e->ms_load);
    std::fflush(stdout);

    std::string line;
    while (std::getline(std::cin, line)) {
        if (line.empty()) continue;
        std::string out;
        eurise_ai::Stats st;
        const bool ok = eurise_ai::capture(*e, line, y, m, d, out, st);
        std::string r = eurise_ai::result_json(ok, out, st);
        r.insert(1, "\"frase\":\"" + eurise_ai::json_escape(line) + "\",");
        std::puts(r.c_str());
        std::fflush(stdout);
    }

    std::string out;
    eurise_ai::Stats st;
    eurise_ai::ask(*e, "In due frasi: perché è utile che un assistente lavori senza Internet?", 80, out, st);
    std::string r = eurise_ai::result_json(true, out, st);
    r.insert(1, "\"domanda\":true,");
    std::puts(r.c_str());

    r = eurise_ai::result_json(true, "", eurise_ai::bench(*e, 256, 64));
    r.insert(1, "\"bench\":true,");
    std::puts(r.c_str());
    delete e;
    return 0;
}

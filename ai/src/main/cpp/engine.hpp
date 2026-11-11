// Motore di Eurise AI, comune al ponte JNI (telefono) e alla prova sul computer (host_test.cpp).
// Usa llama.cpp: carica un modello GGUF, capisce una frase della cattura rapida (risposta in JSON vincolata da una
// grammatica, così è sempre leggibile), risponde a una domanda libera e misura la velocità.
#pragma once

#include "llama.h"

#include <chrono>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <ctime>
#include <functional>
#include <random>
#include <string>
#include <vector>

namespace eurise_ai {

/** Tempi di una risposta: token del prompt e token generati, con i millisecondi spesi per ciascuna parte. */
struct Stats {
    int n_prompt = 0;
    double ms_prompt = 0;
    int n_gen = 0;
    double ms_gen = 0;
};

struct Engine {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    int n_ctx = 0;
    int n_batch = 0;
    double ms_load = 0;
    /** I token che stanno ora nella memoria del contesto (prompt e risposta dell'ultima volta). */
    std::vector<llama_token> cached;

    ~Engine() {
        if (ctx) llama_free(ctx);
        if (model) llama_model_free(model);
    }
};

inline double now_ms() {
    using namespace std::chrono;
    return duration<double, std::milli>(steady_clock::now().time_since_epoch()).count();
}

/** Carica il modello (mappato in memoria, non copiato) e prepara un contesto di [n_ctx] token. */
inline Engine *load(const char *path, int threads, int n_ctx) {
    static bool started = false;
    if (!started) {
        llama_backend_init();
        started = true;
    }
    auto *e = new Engine();
    const double t0 = now_ms();
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    e->model = llama_model_load_from_file(path, mp);
    if (!e->model) {
        delete e;
        return nullptr;
    }
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = (uint32_t) n_ctx;
    cp.n_batch = 512;
    cp.n_ubatch = 512;
    cp.n_threads = threads;
    cp.n_threads_batch = threads;
    cp.no_perf = true;
    e->ctx = llama_init_from_model(e->model, cp);
    if (!e->ctx) {
        delete e;
        return nullptr;
    }
    e->vocab = llama_model_get_vocab(e->model);
    e->n_ctx = n_ctx;
    e->n_batch = 512;
    e->ms_load = now_ms() - t0;
    return e;
}

inline void set_threads(Engine &e, int threads) { llama_set_n_threads(e.ctx, threads, threads); }

/** Il prompt nel formato di chat del modello; se il modello non lo dichiara, quello di Mistral. */
inline std::string chat_prompt(const Engine &e, const std::string &system, const std::string &user) {
    const char *tmpl = llama_model_chat_template(e.model, nullptr);
    if (tmpl) {
        llama_chat_message msgs[2] = {{"system", system.c_str()}, {"user", user.c_str()}};
        std::vector<char> buf(2 * (system.size() + user.size()) + 512);
        int n = llama_chat_apply_template(tmpl, msgs, 2, true, buf.data(), (int32_t) buf.size());
        if (n > (int) buf.size()) {
            buf.resize(n);
            n = llama_chat_apply_template(tmpl, msgs, 2, true, buf.data(), (int32_t) buf.size());
        }
        if (n > 0) return std::string(buf.data(), n);
    }
    return "[SYSTEM_PROMPT]" + system + "[/SYSTEM_PROMPT][INST]" + user + "[/INST]";
}

inline std::vector<llama_token> tokenize(const Engine &e, const std::string &text) {
    int n = -llama_tokenize(e.vocab, text.c_str(), (int32_t) text.size(), nullptr, 0, true, true);
    std::vector<llama_token> out(n > 0 ? n : 0);
    if (n > 0) llama_tokenize(e.vocab, text.c_str(), (int32_t) text.size(), out.data(), n, true, true);
    return out;
}

inline bool decode_all(Engine &e, std::vector<llama_token> &tokens) {
    for (size_t i = 0; i < tokens.size(); i += e.n_batch) {
        const int n = (int) std::min<size_t>(e.n_batch, tokens.size() - i);
        if (llama_decode(e.ctx, llama_batch_get_one(tokens.data() + i, n)) != 0) return false;
    }
    return true;
}

/**
 * Genera la risposta a [prompt] (già nel formato di chat). Con [grammar] la risposta segue quella grammatica GBNF;
 * senza, scelta deterministica del token più probabile. [on_piece] riceve il testo man mano (può fermare con false).
 * Restituisce false se il prompt non entra nel contesto o il motore si ferma.
 */
inline bool generate(Engine &e, const std::string &prompt, const char *grammar, int max_tokens, std::string &out, Stats &st,
                     const std::function<bool(const std::string &)> &on_piece = nullptr) {
    out.clear();
    st = Stats();
    std::vector<llama_token> tokens = tokenize(e, prompt);
    if (tokens.empty() || (int) tokens.size() + max_tokens > e.n_ctx) return false;

    // Le istruzioni sono uguali da una richiesta all'altra: la parte iniziale già letta resta in memoria e si legge
    // solo il resto (la frase nuova). Almeno l'ultimo token si rilegge, per avere le probabilità del successivo.
    llama_memory_t mem = llama_get_memory(e.ctx);
    size_t keep = 0;
    while (keep < e.cached.size() && keep + 1 < tokens.size() && e.cached[keep] == tokens[keep]) keep++;
    if (keep == 0 || !llama_memory_seq_rm(mem, 0, (llama_pos) keep, -1)) {
        llama_memory_clear(mem, true);
        keep = 0;
    }
    e.cached.assign(tokens.begin(), tokens.begin() + keep);
    std::vector<llama_token> rest(tokens.begin() + keep, tokens.end());

    // Con la grammatica non si filtra tutto il vocabolario (oltre 100.000 token) a ogni passo: si sceglie il token più
    // probabile e si chiede alla grammatica solo di lui; solo se lo rifiuta si filtra tutto e si sceglie di nuovo
    // (come «grammar_first = false» negli strumenti di llama.cpp). Stesso risultato, molto meno lavoro.
    llama_sampler *g = nullptr;
    if (grammar) {
        g = llama_sampler_init_grammar(e.vocab, grammar, "root");
        if (!g) return false;
    }
    const int n_vocab = llama_vocab_n_tokens(e.vocab);
    std::vector<llama_token_data> all;
    auto pick = [&]() -> llama_token {
        const float *logits = llama_get_logits_ith(e.ctx, -1);
        llama_token best = 0;
        for (int i = 1; i < n_vocab; i++) if (logits[i] > logits[best]) best = i;
        if (!g) return best;
        llama_token_data one = {best, logits[best], 0.0f};
        llama_token_data_array single = {&one, 1, -1, false};
        llama_sampler_apply(g, &single);
        if (single.data[0].logit != -INFINITY) return best;
        all.resize(n_vocab);
        for (int i = 0; i < n_vocab; i++) all[i] = {i, logits[i], 0.0f};
        llama_token_data_array full = {all.data(), (size_t) n_vocab, -1, false};
        llama_sampler_apply(g, &full);
        size_t at = 0;
        for (size_t i = 1; i < full.size; i++) if (full.data[i].logit > full.data[at].logit) at = i;
        return full.data[at].id;
    };

    double t = now_ms();
    const bool ok = decode_all(e, rest);
    st.n_prompt = (int) rest.size();
    st.ms_prompt = now_ms() - t;
    if (!ok) {
        llama_memory_clear(mem, true);
        e.cached.clear();
        if (g) llama_sampler_free(g);
        return false;
    }
    e.cached.insert(e.cached.end(), rest.begin(), rest.end());

    t = now_ms();
    char piece[256];
    for (int i = 0; i < max_tokens; i++) {
        llama_token tok = pick();
        if (llama_vocab_is_eog(e.vocab, tok)) break;
        if (g) llama_sampler_accept(g, tok);
        const int n = llama_token_to_piece(e.vocab, tok, piece, sizeof(piece), 0, false);
        st.n_gen++;
        if (n > 0) {
            std::string p(piece, n);
            out += p;
            if (on_piece && !on_piece(p)) break;
        }
        if (llama_decode(e.ctx, llama_batch_get_one(&tok, 1)) != 0) break;
        e.cached.push_back(tok);
    }
    st.ms_gen = now_ms() - t;
    if (g) llama_sampler_free(g);
    return true;
}

/** Misura come llama-bench: [n_prompt] token letti insieme, poi [n_gen] token uno alla volta. */
inline Stats bench(Engine &e, int n_prompt, int n_gen) {
    Stats st;
    llama_memory_clear(llama_get_memory(e.ctx), true);
    e.cached.clear();
    const int n_vocab = llama_vocab_n_tokens(e.vocab);
    std::mt19937 rng(42);
    std::vector<llama_token> tokens(n_prompt);
    for (auto &t : tokens) t = (llama_token) (rng() % n_vocab);
    double t = now_ms();
    decode_all(e, tokens);
    st.n_prompt = n_prompt;
    st.ms_prompt = now_ms() - t;
    t = now_ms();
    llama_token tok = tokens.back();
    for (int i = 0; i < n_gen; i++) {
        if (llama_decode(e.ctx, llama_batch_get_one(&tok, 1)) != 0) break;
        tok = (llama_token) (rng() % n_vocab);
        st.n_gen++;
    }
    st.ms_gen = now_ms() - t;
    llama_memory_clear(llama_get_memory(e.ctx), true);
    return st;
}

// ---- Cattura rapida -------------------------------------------------------------------------------------------

/**
 * La risposta della cattura in una riga sola, i sette campi separati da «|» e vuoti quando mancano:
 * `evento|Dentista con Marco|2026-11-12|15:00||Marco|portare i referti`. Senza nomi dei campi né «null» il modello
 * scrive circa un terzo dei token del JSON (sul telefono ogni token scritto costa un passo intero); il JSON lo
 * ricompone [capture_json], sempre uguale a quello di prima per chi lo legge.
 */
inline const char *CAPTURE_GRAMMAR = R"(
root    ::= tipo "|" testo "|" data? "|" ora? "|" testo? "|" persone? "|" testo?
tipo    ::= "evento" | "da_fare" | "promemoria" | "nota"
testo   ::= c{1,80}
c       ::= [^|\x7F\x00-\x1F]
data    ::= [0-9] [0-9] [0-9] [0-9] "-" [0-1] [0-9] "-" [0-3] [0-9]
ora     ::= [0-2] [0-9] ":" [0-5] [0-9]
persone ::= nome ("," " "? nome){0,5}
nome    ::= n{1,40}
n       ::= [^|,\x7F\x00-\x1F]
)";

inline std::string json_str(const std::string &v) {
    std::string out = "\"";
    for (unsigned char ch : v) {
        if (ch == '"' || ch == '\\') { out += '\\'; out += (char) ch; }
        else if (ch < 0x20) out += ' ';
        else out += (char) ch;
    }
    return out + "\"";
}

inline std::string trimmed(const std::string &v) {
    size_t a = 0, b = v.size();
    while (a < b && std::isspace((unsigned char) v[a])) a++;
    while (b > a && std::isspace((unsigned char) v[b - 1])) b--;
    return v.substr(a, b - a);
}

/** Dalla riga compatta al JSON della cattura (campi in quest'ordine, «null» per quelli vuoti). */
inline std::string capture_json(const std::string &line) {
    std::vector<std::string> f;
    size_t start = 0;
    for (size_t i = 0; i <= line.size(); i++) {
        if (i == line.size() || line[i] == '|') { f.push_back(trimmed(line.substr(start, i - start))); start = i + 1; }
    }
    f.resize(7);
    // Se il modello scrive comunque «null» o «-», vale come campo vuoto.
    auto none = [](const std::string &v) { return v.empty() || v == "null" || v == "-" || v == "nessuno" || v == "nessuna"; };
    auto opt = [&](const std::string &v) { return none(v) ? std::string("null") : json_str(v); };
    std::string people = "[";
    size_t p = 0;
    bool first = true;
    const std::string &list = f[5];
    while (p <= list.size() && !list.empty()) {
        size_t q = list.find(',', p);
        if (q == std::string::npos) q = list.size();
        std::string name = trimmed(list.substr(p, q - p));
        if (!none(name)) { people += (first ? "" : ", ") + json_str(name); first = false; }
        p = q + 1;
    }
    people += "]";
    return "{\"tipo\": " + json_str(f[0].empty() ? "nota" : f[0]) + ", \"titolo\": " + json_str(f[1]) + ", \"data\": " + opt(f[2]) +
           ", \"ora\": " + opt(f[3]) + ", \"luogo\": " + opt(f[4]) + ", \"persone\": " + people + ", \"note\": " + opt(f[6]) + "}";
}

inline std::string iso(const std::tm &d) {
    char b[40];
    std::snprintf(b, sizeof(b), "%04d-%02d-%02d", d.tm_year + 1900, d.tm_mon + 1, d.tm_mday);
    return b;
}

inline std::string spoken(const std::tm &d) {
    static const char *days[] = {"domenica", "lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato"};
    static const char *months[] = {"gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio",
                                   "agosto", "settembre", "ottobre", "novembre", "dicembre"};
    return std::string(days[d.tm_wday]) + " " + std::to_string(d.tm_mday) + " " + months[d.tm_mon] + " " +
           std::to_string(d.tm_year + 1900);
}

/**
 * Istruzioni della cattura per il giorno [y]-[m]-[d]. I prossimi 14 giorni sono scritti per esteso: un modello piccolo
 * sbaglia i conti sul calendario, mentre sa leggere «venerdì 13 novembre 2026 = 2026-11-13».
 */
inline std::string capture_system(int y, int m, int d) {
    std::tm base{};
    base.tm_year = y - 1900;
    base.tm_mon = m - 1;
    base.tm_mday = d;
    base.tm_hour = 12;
    std::mktime(&base);
    std::string s =
        "Sei il modulo di comprensione di Eurise, una suite per il telefono. Ricevi una frase scritta o dettata in "
        "fretta e la trasformi in un oggetto JSON.\n"
        "Campi:\n"
        "- tipo: \"evento\" se c'è un appuntamento, un incontro o un impegno in un giorno; \"da_fare\" se è una cosa da "
        "fare o da comprare; \"promemoria\" se chiede di essere avvisato o svegliato a un'ora precisa; \"nota\" per idee, "
        "appunti e informazioni da ricordare (codici, numeri, indirizzi). Se la frase comincia con «idea», «appunto» o "
        "«nota», il tipo è \"nota\".\n"
        "- titolo: breve, in italiano, senza giorno, ora e luogo.\n"
        "- data: AAAA-MM-GG oppure null se non c'è un giorno. Un giorno della settimana da solo («venerdì», «entro "
        "venerdì») è quello segnato con ← nell'elenco qui sotto. Una data senza anno («il 3 dicembre») è la prima che viene "
        "da oggi in poi: di quest'anno, se non è già passata.\n"
        "- ora: HH:MM in 24 ore oppure null. «Mattina», «pomeriggio» o «sera» senza un'ora: null.\n"
        "- luogo: il posto o l'indirizzo, oppure null.\n"
        "- persone: solo i nomi propri delle persone citate (non mestieri o gruppi), anche vuoto.\n"
        "- note: altri dettagli utili, oppure null. Una parte del giorno senza ora («mattina», «sera») va nelle note.\n"
        "Rispondi con una riga sola, i campi in quest'ordine separati da |, vuoti quando mancano (niente «null»), le "
        "persone separate da virgole: tipo|titolo|data|ora|luogo|persone|note\n"
        "Esempio: «domani alle 15 dentista con Marco, portare i referti» → evento|Dentista con Marco|<data di domani>|15:00||Marco|portare i referti\n"
        "Oggi è " + spoken(base) + " (" + iso(base) + ").\n";
    for (int i = 1; i <= 14; i++) {
        std::tm t = base;
        t.tm_mday += i;
        std::mktime(&t);
        const char *label = i == 1 ? "domani, " : i == 2 ? "dopodomani, " : "";
        static const char *names[] = {"domenica", "lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato"};
        const std::string mark = i <= 7 ? std::string(" ← «") + names[t.tm_wday] + "»" : "";
        s += "- " + std::string(label) + spoken(t) + " = " + iso(t) + mark + "\n";
    }
    return s;
}

/**
 * Controlli fissi dopo il modello, dove sbaglia anche con le istruzioni giuste: un'ora che nella frase non c'è (nessuna
 * cifra: «giovedì mattina») si toglie; una data senza anno scritto non va oltre i prossimi 12 mesi.
 * Il testo segue sempre la grammatica, quindi i campi si trovano per posizione fissa.
 */
inline void fix_capture(std::string &out, const std::string &sentence, int y, int m, int d) {
    bool digits = false, year = false;
    for (size_t i = 0; i < sentence.size(); i++) {
        if (!isdigit((unsigned char) sentence[i])) continue;
        digits = true;
        if (i + 3 < sentence.size() && isdigit((unsigned char) sentence[i + 1]) && isdigit((unsigned char) sentence[i + 2]) &&
            isdigit((unsigned char) sentence[i + 3]) && (sentence[i] == '1' || sentence[i] == '2')) year = true;
    }
    const std::string ora = "\"ora\": \"";
    size_t p = out.find(ora);
    if (!digits && p != std::string::npos) out.replace(p + 7, 7, "null");
    const std::string data = "\"data\": \"";
    p = out.find(data);
    if (!year && p != std::string::npos) {
        int yy = 0, mm = 0, dd = 0;
        if (std::sscanf(out.c_str() + p + data.size(), "%4d-%2d-%2d", &yy, &mm, &dd) == 3) {
            int fixed = (mm > m || (mm == m && dd >= d)) ? y : y + 1;
            if (yy != fixed) {
                char b[8];
                std::snprintf(b, sizeof(b), "%04d", fixed);
                out.replace(p + data.size(), 4, b);
            }
        }
    }
}

inline bool capture(Engine &e, const std::string &sentence, int y, int m, int d, std::string &out, Stats &st) {
    std::string line;
    const bool ok = generate(e, chat_prompt(e, capture_system(y, m, d), sentence), CAPTURE_GRAMMAR, 110, line, st);
    if (!ok) return false;
    out = capture_json(line);
    fix_capture(out, sentence, y, m, d);
    return true;
}

inline bool ask(Engine &e, const std::string &question, int max_tokens, std::string &out, Stats &st,
                const std::function<bool(const std::string &)> &on_piece = nullptr) {
    const std::string system = "Sei Eurise AI, l'assistente che lavora solo sul telefono dell'utente. Rispondi in "
                               "italiano, in modo breve e preciso.";
    return generate(e, chat_prompt(e, system, question), nullptr, max_tokens, out, st, on_piece);
}

inline std::string json_escape(const std::string &s) {
    std::string o;
    for (unsigned char c : s) {
        switch (c) {
            case '"': o += "\\\""; break;
            case '\\': o += "\\\\"; break;
            case '\n': o += "\\n"; break;
            case '\r': o += "\\r"; break;
            case '\t': o += "\\t"; break;
            default:
                if (c < 0x20) {
                    char b[8];
                    std::snprintf(b, sizeof(b), "\\u%04x", c);
                    o += b;
                } else {
                    o += (char) c;
                }
        }
    }
    return o;
}

/** Una risposta con i suoi tempi, come oggetto JSON (lo leggono sia l'app sia lo script della compilazione). */
inline std::string result_json(bool ok, const std::string &text, const Stats &st) {
    char b[200];
    std::snprintf(b, sizeof(b), "\"ok\":%s,\"n_prompt\":%d,\"ms_prompt\":%.1f,\"n_gen\":%d,\"ms_gen\":%.1f",
                  ok ? "true" : "false", st.n_prompt, st.ms_prompt, st.n_gen, st.ms_gen);
    return "{" + std::string(b) + ",\"text\":\"" + json_escape(text) + "\"}";
}

}  // namespace eurise_ai

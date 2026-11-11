// Motore di Eurise AI, comune al ponte JNI (telefono) e alla prova sul computer (host_test.cpp).
// Usa llama.cpp: carica un modello GGUF, capisce una frase della cattura rapida (risposta in JSON vincolata da una
// grammatica, così è sempre leggibile), risponde a una domanda libera e misura la velocità.
#pragma once

#include "llama.h"

#include <chrono>
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

    llama_sampler *chain = llama_sampler_chain_init(llama_sampler_chain_default_params());
    if (grammar) {
        llama_sampler *g = llama_sampler_init_grammar(e.vocab, grammar, "root");
        if (!g) {
            llama_sampler_free(chain);
            return false;
        }
        llama_sampler_chain_add(chain, g);
    }
    llama_sampler_chain_add(chain, llama_sampler_init_greedy());

    double t = now_ms();
    const bool ok = decode_all(e, rest);
    st.n_prompt = (int) rest.size();
    st.ms_prompt = now_ms() - t;
    if (!ok) {
        llama_memory_clear(mem, true);
        e.cached.clear();
        llama_sampler_free(chain);
        return false;
    }
    e.cached.insert(e.cached.end(), rest.begin(), rest.end());

    t = now_ms();
    char piece[256];
    for (int i = 0; i < max_tokens; i++) {
        llama_token tok = llama_sampler_sample(chain, e.ctx, -1);
        if (llama_vocab_is_eog(e.vocab, tok)) break;
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
    llama_sampler_free(chain);
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

/** La risposta della cattura: sempre questo oggetto, con i campi in quest'ordine. */
inline const char *CAPTURE_GRAMMAR = R"(
root  ::= "{\"tipo\": " tipo ", \"titolo\": " str ", \"data\": " (data | "null") ", \"ora\": " (ora | "null") ", \"luogo\": " (str | "null") ", \"persone\": " lista ", \"note\": " (str | "null") "}"
tipo  ::= "\"evento\"" | "\"da_fare\"" | "\"promemoria\"" | "\"nota\""
str   ::= "\"" char{1,80} "\""
char  ::= [^"\\\x7F\x00-\x1F] | "\\" ["\\/bfnrt]
data  ::= "\"" [0-9] [0-9] [0-9] [0-9] "-" [0-1] [0-9] "-" [0-3] [0-9] "\""
ora   ::= "\"" [0-2] [0-9] ":" [0-5] [0-9] "\""
lista ::= "[" ( str ( ", " str ){0,5} )? "]"
)";

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
        "- note: altri dettagli utili, oppure null.\n"
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

inline bool capture(Engine &e, const std::string &sentence, int y, int m, int d, std::string &out, Stats &st) {
    return generate(e, chat_prompt(e, capture_system(y, m, d), sentence), CAPTURE_GRAMMAR, 160, out, st);
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

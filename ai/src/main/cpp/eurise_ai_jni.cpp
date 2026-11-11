// Ponte JNI tra Eurise AI (eu.eurise.ai.AiNative) e il motore di engine.hpp.
#include <jni.h>
#include <android/log.h>

#include <string>

#include "engine.hpp"
#include "ggml-backend.h"

#define TAG "EuriseAI"

using namespace eurise_ai;

namespace {

void log_to_android(enum ggml_log_level level, const char *text, void *) {
    if (level == GGML_LOG_LEVEL_ERROR) __android_log_print(ANDROID_LOG_ERROR, TAG, "%s", text);
    else if (level == GGML_LOG_LEVEL_WARN) __android_log_print(ANDROID_LOG_WARN, TAG, "%s", text);
}

std::string str(JNIEnv *env, jstring s) {
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string out(c);
    env->ReleaseStringUTFChars(s, c);
    return out;
}

jstring jstr(JNIEnv *env, const std::string &s) { return env->NewStringUTF(s.c_str()); }

}  // namespace

extern "C" {

/** Carica le varianti della parte CPU dalla cartella delle librerie e dice quale è stata scelta e con che istruzioni. */
JNIEXPORT jstring JNICALL
Java_eu_eurise_ai_AiNative_backends(JNIEnv *env, jclass, jstring libDir) {
    static bool loaded = false;
    if (!loaded) {
        llama_log_set(log_to_android, nullptr);
        ggml_backend_load_all_from_path(str(env, libDir).c_str());
        loaded = true;
    }
    std::string out;
    for (size_t i = 0; i < ggml_backend_reg_count(); i++) {
        ggml_backend_reg_t reg = ggml_backend_reg_get(i);
        if (!out.empty()) out += "; ";
        out += ggml_backend_reg_name(reg);
        auto get = (ggml_backend_get_features_t) ggml_backend_reg_get_proc_address(reg, "ggml_backend_get_features");
        if (get) {
            for (ggml_backend_feature *f = get(reg); f && f->name; f++) {
                out += " ";
                out += f->name;
                if (std::string(f->value) != "1") out += std::string("=") + f->value;
            }
        }
    }
    return jstr(env, out);
}

JNIEXPORT jlong JNICALL
Java_eu_eurise_ai_AiNative_load(JNIEnv *env, jclass, jstring path, jint threads, jint nCtx) {
    return (jlong) load(str(env, path).c_str(), threads, nCtx);
}

JNIEXPORT void JNICALL
Java_eu_eurise_ai_AiNative_free(JNIEnv *, jclass, jlong ptr) {
    delete (Engine *) ptr;
}

/** Descrizione del modello, parametri, byte dei pesi e millisecondi del caricamento, separati da «|». */
JNIEXPORT jstring JNICALL
Java_eu_eurise_ai_AiNative_info(JNIEnv *env, jclass, jlong ptr) {
    auto *e = (Engine *) ptr;
    char desc[128] = {0};
    llama_model_desc(e->model, desc, sizeof(desc));
    return jstr(env, std::string(desc) + "|" + std::to_string(llama_model_n_params(e->model)) + "|" +
                         std::to_string(llama_model_size(e->model)) + "|" + std::to_string((long long) e->ms_load));
}

JNIEXPORT void JNICALL
Java_eu_eurise_ai_AiNative_threads(JNIEnv *, jclass, jlong ptr, jint threads) {
    set_threads(*(Engine *) ptr, threads);
}

JNIEXPORT jstring JNICALL
Java_eu_eurise_ai_AiNative_capture(JNIEnv *env, jclass, jlong ptr, jstring sentence, jint y, jint m, jint d) {
    std::string out;
    Stats st;
    const bool ok = capture(*(Engine *) ptr, str(env, sentence), y, m, d, out, st);
    return jstr(env, result_json(ok, out, st));
}

JNIEXPORT jstring JNICALL
Java_eu_eurise_ai_AiNative_ask(JNIEnv *env, jclass, jlong ptr, jstring question, jint maxTokens) {
    std::string out;
    Stats st;
    const bool ok = ask(*(Engine *) ptr, str(env, question), maxTokens, out, st);
    return jstr(env, result_json(ok, out, st));
}

JNIEXPORT jstring JNICALL
Java_eu_eurise_ai_AiNative_bench(JNIEnv *env, jclass, jlong ptr, jint nPrompt, jint nGen) {
    return jstr(env, result_json(true, "", bench(*(Engine *) ptr, nPrompt, nGen)));
}

}  // extern "C"

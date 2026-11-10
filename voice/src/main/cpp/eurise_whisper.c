// Ponte JNI tra Eurise Voce e whisper.cpp: carica un modello, trascrive l'audio, libera la memoria.
#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <android/log.h>
#include "whisper.h"

#define TAG "EuriseWhisper"

JNIEXPORT jlong JNICALL
Java_eu_eurise_voice_WhisperNative_init(JNIEnv *env, jclass clazz, jstring path) {
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *ctx = whisper_init_from_file_with_params(p, cparams);
    (*env)->ReleaseStringUTFChars(env, path, p);
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_eu_eurise_voice_WhisperNative_free(JNIEnv *env, jclass clazz, jlong ptr) {
    if (ptr != 0) whisper_free((struct whisper_context *) ptr);
}

JNIEXPORT jstring JNICALL
Java_eu_eurise_voice_WhisperNative_transcribe(JNIEnv *env, jclass clazz, jlong ptr, jint threads, jfloatArray audio, jstring language) {
    struct whisper_context *ctx = (struct whisper_context *) ptr;
    if (ctx == NULL) return (*env)->NewStringUTF(env, "");
    jfloat *samples = (*env)->GetFloatArrayElements(env, audio, NULL);
    const jsize n = (*env)->GetArrayLength(env, audio);
    const char *lang = (*env)->GetStringUTFChars(env, language, NULL);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = lang;
    params.n_threads = threads;
    params.no_context = true;
    params.single_segment = false;
    params.suppress_blank = true;

    char *out = NULL;
    if (whisper_full(ctx, params, samples, n) == 0) {
        const int segments = whisper_full_n_segments(ctx);
        size_t total = 1;
        for (int i = 0; i < segments; i++) total += strlen(whisper_full_get_segment_text(ctx, i));
        out = (char *) calloc(total, 1);
        for (int i = 0; i < segments; i++) strcat(out, whisper_full_get_segment_text(ctx, i));
    } else {
        __android_log_print(ANDROID_LOG_WARN, TAG, "whisper_full non riuscito");
    }
    (*env)->ReleaseStringUTFChars(env, language, lang);
    (*env)->ReleaseFloatArrayElements(env, audio, samples, JNI_ABORT);
    jstring result = (*env)->NewStringUTF(env, out != NULL ? out : "");
    free(out);
    return result;
}

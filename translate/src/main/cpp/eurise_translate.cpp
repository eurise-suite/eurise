// Ponte JNI tra Eurise Traduci (eu.eurise.translate.Translator) ed engine.hpp. Un motore si usa da un solo thread alla volta.
#include <jni.h>

#include <exception>
#include <string>

#include "engine.hpp"

namespace {
std::string str(JNIEnv *env, jstring s) {
  if (s == nullptr) return {};
  const char *c = env->GetStringUTFChars(s, nullptr);
  std::string out(c);
  env->ReleaseStringUTFChars(s, c);
  return out;
}

std::string utf8(JNIEnv *env, jbyteArray b) {
  const jsize n = env->GetArrayLength(b);
  std::string out(static_cast<size_t>(n), '\0');
  env->GetByteArrayRegion(b, 0, n, reinterpret_cast<jbyte *>(out.data()));
  return out;
}

jbyteArray bytes(JNIEnv *env, const std::string &s) {
  jbyteArray out = env->NewByteArray(static_cast<jsize>(s.size()));
  if (out != nullptr && !s.empty()) env->SetByteArrayRegion(out, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
  return out;
}
}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_eu_eurise_translate_Translator_nativeOpen(JNIEnv *env, jclass, jstring model, jstring vocab,
                                                                        jstring tgtVocab, jstring shortlist) {
  try {
    return reinterpret_cast<jlong>(new eurise::Engine(str(env, model), str(env, vocab), str(env, tgtVocab), str(env, shortlist)));
  } catch (const std::exception &) {
    return 0;
  }
}

JNIEXPORT void JNICALL Java_eu_eurise_translate_Translator_nativeClose(JNIEnv *, jclass, jlong handle) {
  delete reinterpret_cast<eurise::Engine *>(handle);
}

/** Testo in UTF-8 come byte (l'UTF-8 "modificato" di JNI non va bene per tutti i caratteri). Null se non riesce. */
JNIEXPORT jbyteArray JNICALL Java_eu_eurise_translate_Translator_nativeTranslate(JNIEnv *env, jclass, jlong handle, jbyteArray text) {
  try {
    return bytes(env, reinterpret_cast<eurise::Engine *>(handle)->translate(utf8(env, text)));
  } catch (const std::exception &) {
    return nullptr;
  }
}

}  // extern "C"

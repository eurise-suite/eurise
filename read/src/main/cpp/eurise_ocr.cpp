// Ponte JNI tra Eurise Leggi (eu.eurise.read.ocr.Ocr) e Tesseract. Ogni motore si usa da un solo thread alla volta.
#include <jni.h>
#include <android/bitmap.h>
#include <tesseract/baseapi.h>
#include <allheaders.h>
#include <string>

namespace {
tesseract::TessBaseAPI *api(jlong handle) { return reinterpret_cast<tesseract::TessBaseAPI *>(handle); }

std::string str(JNIEnv *env, jstring s) {
  const char *c = env->GetStringUTFChars(s, nullptr);
  std::string out(c);
  env->ReleaseStringUTFChars(s, c);
  return out;
}
}  // namespace

extern "C" {

/** Leptonica scrive avvisi su stderr (che su Android non legge nessuno): si spengono. */
JNIEXPORT jint JNI_OnLoad(JavaVM *, void *) {
  setMsgSeverity(L_SEVERITY_NONE);
  return JNI_VERSION_1_6;
}

JNIEXPORT jlong JNICALL Java_eu_eurise_read_ocr_Ocr_nativeOpen(JNIEnv *env, jclass, jstring dataPath, jstring lang) {
  auto *tess = new tesseract::TessBaseAPI();
  if (tess->Init(str(env, dataPath).c_str(), str(env, lang).c_str(), tesseract::OEM_LSTM_ONLY) != 0) {
    delete tess;
    return 0;
  }
  return reinterpret_cast<jlong>(tess);
}

JNIEXPORT void JNICALL Java_eu_eurise_read_ocr_Ocr_nativeClose(JNIEnv *, jclass, jlong handle) {
  auto *tess = api(handle);
  tess->End();
  delete tess;
}

JNIEXPORT jboolean JNICALL Java_eu_eurise_read_ocr_Ocr_nativeSetVariable(JNIEnv *env, jclass, jlong handle,
                                                                         jstring name, jstring value) {
  return api(handle)->SetVariable(str(env, name).c_str(), str(env, value).c_str());
}

JNIEXPORT void JNICALL Java_eu_eurise_read_ocr_Ocr_nativeSetPageSegMode(JNIEnv *, jclass, jlong handle, jint mode) {
  api(handle)->SetPageSegMode(static_cast<tesseract::PageSegMode>(mode));
}

/** Legge una Bitmap ARGB_8888 (in memoria R, G, B, A) e restituisce il testo, o null se l'immagine non va. */
JNIEXPORT jstring JNICALL Java_eu_eurise_read_ocr_Ocr_nativeRead(JNIEnv *env, jclass, jlong handle, jobject bitmap) {
  AndroidBitmapInfo info;
  if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return nullptr;
  if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return nullptr;
  void *pixels = nullptr;
  if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return nullptr;
  auto *tess = api(handle);
  tess->SetImage(static_cast<const unsigned char *>(pixels), static_cast<int>(info.width),
                 static_cast<int>(info.height), 4, static_cast<int>(info.stride));
  AndroidBitmap_unlockPixels(env, bitmap);  // SetImage copia i pixel
  char *text = tess->GetUTF8Text();
  jstring out = env->NewStringUTF(text != nullptr ? text : "");
  delete[] text;
  return out;
}

/** Fiducia media (0–100) dell'ultima lettura. */
JNIEXPORT jint JNICALL Java_eu_eurise_read_ocr_Ocr_nativeConfidence(JNIEnv *, jclass, jlong handle) {
  return api(handle)->MeanTextConf();
}

JNIEXPORT void JNICALL Java_eu_eurise_read_ocr_Ocr_nativeClear(JNIEnv *, jclass, jlong handle) {
  api(handle)->Clear();
}

}  // extern "C"

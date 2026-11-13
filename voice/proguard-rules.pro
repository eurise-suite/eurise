# I metodi nativi di Whisper sono chiamati per nome dal codice C.
-keep class eu.eurise.voice.WhisperNative { *; }

# sherpa-onnx: le classi di configurazione si leggono dal codice nativo (JNI) per nome di campo.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Il motore (libxul) cerca per nome, dal codice nativo, classi e metodi Java di tutto org.mozilla (gecko, geckoview,
# mozglue…): R8 non lo vede e li toglieva, e il Browser andava in crash appena partito (giro 808, SIGSEGV nel thread
# «launcher», 18 novembre). Si tengono tutti, con i loro membri, attributi e annotazioni.
-keep class org.mozilla.** { *; }
-keep interface org.mozilla.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions
# Nomi delle classi leggibili nei rapporti degli errori (CrashGuard): si toglie solo il codice che non serve.
-dontobfuscate
# SnakeYAML (lo usa GeckoView per il file di configurazione di debug) cita java.beans, che Android non ha.
-dontwarn java.beans.**
# Annotazioni di Kotlin citate dall'exoplayer interno di GeckoView (tenuto per intero sopra): non servono a runtime.
-dontwarn kotlin.annotations.jvm.**

# GeckoView porta le sue regole; qui solo le nostre.
-keep class org.mozilla.geckoview.** { *; }
# Nomi delle classi leggibili nei rapporti degli errori (CrashGuard): si toglie solo il codice che non serve.
-dontobfuscate

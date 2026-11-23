# Regole comuni di R8 per tutte le app della suite (22 novembre 2026, docs/SICUREZZA.md, «Pulizia e velocità»).
# Prudenti di proposito: R8 qui serve a far girare più svelte le librerie (Compose prima di tutto), non a togliere ogni
# byte. Si allargano solo con un motivo scritto e la prova d'avvio verde (prova-avvio.yml).

# Nomi leggibili nei rapporti degli errori (CrashGuard) e nelle tracce: si toglie solo il codice che non serve.
-dontobfuscate
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions,SourceFile,LineNumberTable

# Il «tieni tutto il codice nostro» sta in proguard-nostro.pro: lo lasciano, una alla volta, le app che reggono senza
# (build.gradle.kts, elenco r8Pieno).

# PdfBox-Android (core:pdf, quindi quasi ogni app tramite core:data): font, profili colore e filtri trovati per nome.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.gemalto.jp2.**

# BouncyCastle: algoritmi registrati e caricati per nome dal provider.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Classi del computer citate da librerie Java che su Android non le usano.
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.naming.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**

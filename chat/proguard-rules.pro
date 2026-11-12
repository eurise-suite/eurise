# Cifratura di Matrix: le classi generate da uniffi e JNA si chiamano dalla libreria nativa per nome.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class uniffi.** { *; }
-keep class org.matrix.rustcomponents.** { *; }
-dontwarn java.awt.**

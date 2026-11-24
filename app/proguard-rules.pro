# Regole aggiuntive per la versione release. Room e Compose portano già le proprie regole.

# Prova del backup sull'emulatore (.github/prova-backup, Sonda.java): la sonda guarda il lavoro pianificato con
# WorkManager e prepara un OkHttp che si fida del certificato del WebDAV finto, per nome. Da quando R8 gira anche negli APK
# di prova (22 novembre) WorkManager finiva fuso nella sua implementazione e il metodo di OkHttp tolto: la prova si fermava
# (24 novembre). Pochi byte; nessun effetto sul comportamento dell'app.
-keep class androidx.work.WorkManager { public *; }
-keep class okhttp3.OkHttpClient$Builder { public *; }

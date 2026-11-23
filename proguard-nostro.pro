# Il codice nostro si tiene tutto, con i membri (22 novembre 2026): WorkManager e i ricevitori lo trovano per nome, il
# codice nativo di Leggi (OCR, traduzione), AI e Voce lo chiama per nome, e qualche schermata usa la riflessione.
# Le app nell'elenco r8Pieno di build.gradle.kts non lo usano più: R8 toglie e ottimizza anche il codice nostro che non
# serve (prova d'avvio e un giro delle funzioni prima di aggiungerne un'altra).
-keep class eu.eurise.** { *; }
-keep interface eu.eurise.** { *; }

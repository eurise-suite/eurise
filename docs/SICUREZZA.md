# Sicurezza della suite

La privacy senza sicurezza non vale nulla: questo file raccoglie lo stato e il piano (avviato il 18 novembre 2026).

## Chiave di prova (18 novembre 2026)
- Problema: la chiave che firmava gli APK di prova (`app/debug-key/eurise-debug.keystore`, password «android») è nel
  repository pubblico. Le app della suite si fidano tra loro con permessi «di firma» (`eu.eurise.permission.SUITE_DEBUG`):
  chiunque poteva firmare un'app con quella chiave e chiedere dati a Password, Contatti, Ponte. Provato sull'emulatore:
  un'app firmata solo con la vecchia chiave otteneva il permesso.
- Soluzione: rotazione della chiave (APK Signature Scheme v3, lineage), senza disinstallare e senza perdere dati.
  `.github/scripts/firma-prova.sh` rifirma ogni APK di prova al momento della pubblicazione (`apk-prova.yml`,
  `office-app.yml`, `office-prova.yml`) con la chiave nuova (EC P-256, alias `eurise-prova`, segreto
  `EURISE_PROVA_KEYSTORE`; impronta SHA-256 del certificato `96d2cf60…5e55e2`). Rotazione valida da Android 9.
- Due fasi (`EURISE_PROVA_FASE`, di base nello script): Android rifiuta l'aggiornamento se la vecchia chiave perde i
  permessi nello stesso passo (INSTALL_FAILED_DUPLICATE_PERMISSION).
  - Fase 1: chiave nuova; la vecchia conserva i permessi solo per il passaggio. Il proprietario installa sopra tutte le app.
  - Fase 2: la vecchia chiave conserva solo «essere aggiornata». Un'app firmata solo con lei non ottiene più il permesso
    della suite e non può rimpiazzare le nostre.
- Prova sull'emulatore (API 34): `.github/workflows/prova-firma.yml` (ramo `sicurezza`), tutta verde il 18 novembre:
  aggiornamento nelle due fasi con il dato di Password intatto, intrusa che chiede il permesso → `granted=false`,
  APK con la sola vecchia chiave rifiutato, app con la chiave nuova con il permesso.
- La chiave delle versioni ufficiali resta solo al proprietario e non è toccata.

## Piano
1. Chiave di prova fuori dal repository (sopra). Fase 1 pubblicata nel giro 808 e installata dal proprietario su tutte le
   app il 18 novembre; **fase 2** di base nello script dallo stesso giorno.
2. Strumenti e librerie aggiornati (AGP, Kotlin, Gradle, SDK, AndroidX, OkHttp, CameraX, PDFBox…) su un ramo a parte,
   unione con una finestra annunciata in `docs/STATO.md`.
3. GeckoView all'ultima versione e poi a ogni uscita di Firefox (circa ogni quattro settimane).
4. Aggiornamenti automatici (Renovate), vulnerabilità note a ogni giro (OSV-Scanner), verifica delle librerie di Gradle
   (`verification-metadata.xml`), azioni di GitHub fissate allo SHA.
5. Revisione delle porte d'ingresso: componenti esportati e fornitori di dati, crittografia di Password, backup e Ponte.
6. Correzioni già note: `allowBackup="true"` nella Tastiera.
7. `SECURITY.md` (come segnalare una falla), modello delle minacce, revisione esterna prima della 1.0 (NLnet / NGI).
8. Computer: Ponte con accoppiamento cifrato e autenticato, estensione Firefox con i soli permessi necessari, Flatpak
   su runtime aggiornato.

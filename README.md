# Eurise

App hub della suite Eurise per Android: privacy prima di tutto, dati sul telefono o sui cloud dell'utente, nessun servizio Google.

Questa prima versione contiene tre sezioni:

- **Oggi**: data, cattura rapida (una riga diventa un'attività per oggi o una nota), attività in scadenza oggi o in ritardo, note recenti.
- **Note**: elenco con ricerca, note fissate in alto, editor con salvataggio automatico.
- **Attività**: da fare e completate, scadenza (oggi, domani o una data a scelta), note, pulizia delle completate.

Tutti i dati restano in un database locale sul telefono. La sincronizzazione con Murena/Nextcloud (CalDAV per le attività, WebDAV per le note) arriverà nelle prossime sessioni.

## Struttura

| Modulo | Contenuto |
|---|---|
| `app` | Guscio dell'app: attività principale, barra delle sezioni, navigazione |
| `core/designsystem` | Tema Carta (chiaro) e Satinato (scuro), tipografia, componenti comuni, formati delle date |
| `core/data` | Database Room, modelli `Note` e `Task`, repository |
| `feature/today` | Schermata Oggi |
| `feature/notes` | Lista e editor delle note |
| `feature/tasks` | Lista e modifica delle attività |

Ogni funzione è un modulo separato con architettura MVVM (ViewModel + Jetpack Compose): in futuro si può spostare in un'app a sé senza riscriverla. I moduli `feature` dipendono solo da `core`, mai tra loro; la navigazione tra sezioni passa dal modulo `app`.

## Compilare

Non serve Android Studio: a ogni push su `main` GitHub Actions compila l'APK di prova (scheda **Actions** › ultima esecuzione › **Artifacts**). L'APK di prova si chiama "Eurise" con suffisso `.debug` e convive con la versione ufficiale.

In locale, con JDK 17 e Android SDK:

```bash
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # test
```

## Firmare le versioni ufficiali

La chiave di firma la custodisce solo il proprietario del progetto e non entra mai nel repository né nelle mani di agenti AI.

1. Crea la chiave una volta sola sul tuo computer:
   `keytool -genkeypair -v -keystore eurise.jks -alias eurise -keyalg RSA -keysize 4096 -validity 10000`
2. Conservane due copie in luoghi sicuri: se si perde, gli aggiornamenti non si installano più sopra la versione esistente.
3. Nel repository, **Settings › Secrets and variables › Actions**, aggiungi:
   - `EURISE_KEYSTORE_BASE64`: il file codificato (`base64 -w0 eurise.jks`)
   - `EURISE_KEYSTORE_PASSWORD`, `EURISE_KEY_ALIAS`, `EURISE_KEY_PASSWORD`
4. Crea un tag di versione (`v0.1.0`): GitHub Actions compila l'APK firmato e lo pubblica nella pagina **Releases**.

## Principi

- Kotlin + Jetpack Compose (Material 3), MVVM, codice nuovo documentato con KDoc dove non è ovvio.
- Nessuna libreria Google proprietaria (Play Services, Firebase, ML Kit): compatibile con F-Droid.
- Nessun tracker, statistica d'uso o pubblicità. Nessun permesso finché non serve.
- Backup automatico di Android disattivato: i dati si sincronizzeranno solo sui cloud scelti dall'utente.

## Licenza

GPLv3, vedi [LICENSE](LICENSE).

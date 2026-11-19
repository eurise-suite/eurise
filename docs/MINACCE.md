# Modello delle minacce di Eurise

Prima stesura: 18 novembre 2026, notte (progetto «Eurise Sicurezza»). Si aggiorna a ogni revisione; lo stato dei lavori
sta in `docs/SICUREZZA.md`. Obiettivo: proteggere i dati dell'utente meglio dei grandi (Google, Apple, Microsoft), che
per costruzione vedono i dati sui loro server. Da noi i dati restano sul telefono e ciò che esce è cifrato da chiavi che
abbiamo solo noi utenti, mai il progetto.

## Cosa proteggiamo (in ordine di danno)
1. Password, codici 2FA, passkey, carte (Password).
2. Messaggi, posta, chiamate, chat, note mediche dello Studio (Messaggi, Posta, Telefono, Chat).
3. Ciò che si scrive (Tastiera: parole imparate, appunti) e ciò che si dice (Voce).
4. Foto e documenti (Galleria, File, Office, Scanner); posizione e luoghi (Mappe, Oggi).
5. Le chiavi: firma degli APK, Ponte, backup, sessioni di posta e chat.

## Chi può attaccare e come ci difendiamo
| Chi | Cosa può fare | Difesa | Stato |
|---|---|---|---|
| Un'app qualunque sul telefono | Chiamare i nostri fornitori, servizi, attività; fingersi un'app della suite; mandarci file e collegamenti. | Permessi di firma della propria variante; destinatari verificati (`SuiteAccess`); niente file propri da «Condividi»; azioni delicate solo da ingressi non esportati; prova sull'emulatore con un'app intrusa. | Fatto il 18/11 (`prova-porte.yml`). |
| Chi conosce la vecchia chiave di prova (pubblica) | Firmare un'app che la suite crede sua. | Rotazione della chiave di prova (fase 2): la vecchia non ha più permessi. | Fatto il 18/11. |
| Chi ruba il telefono | Leggere la memoria, provare le frasi. | Cifratura di Android; Password con Argon2id e chiusura dopo 2 minuti; chiavi nel Keystore (impronta, backup, Ponte); niente backup Google né trasferimento automatico. | Fatto; da fare: Keystore con StrongBox dove c'è, chiusura sessioni da un altro dispositivo. |
| Chi è sulla stessa rete (Wi-Fi pubblico, casa) | Ascoltare o alterare il traffico, fingersi il computer del Ponte. | TLS ovunque (verificare: nessun traffico in chiaro); Ponte cifrato e autenticato con il QR. | Fatto: nessun traffico in chiaro; Ponte con chiavi di sessione (segretezza in avanti) sulla rete di casa. Da fare: cassetta nel cloud. |
| Il cloud dove va il backup | Leggere o cambiare i backup. | AES-256-GCM, chiave dalla frase dell'utente o dal codice di recupero; intestazione autenticata. | Fatto; da valutare Argon2id al posto di PBKDF2 (formato v2). |
| Un sito web nel Browser | Sfruttare GeckoView, rubare password con siti copia. | Sempre l'ultima GeckoView; compilazione automatica solo sul dominio esatto. | Fatto; da fare: isolamento dei siti (Fission) e controllo dei permessi delle estensioni. |
| Una libreria o un'azione di GitHub manomessa | Mettere codice nostro negli APK. | Azioni fissate allo SHA; impronte SHA-256 delle librerie Gradle; OSV-Scanner; Dependabot. | Avviato il 18/11 (impronte prima «lenient», poi «strict»). |
| Chi ottiene l'accesso a GitHub o ai segreti | Firmare e pubblicare APK falsi. | Chiave ufficiale solo dal proprietario; token dei flussi con i permessi minimi. | Da fare: permessi minimi in ogni flusso, regole di protezione dei rami, firma dei commit, compilazioni riproducibili. |
| Un servizio esterno scelto dall'utente | Profilare l'utente. | Spenti di base, centro privacy, dati minimi. | Regola fissa (CLAUDE.md). |

## Fuori portata (per ora)
Un telefono già compromesso a livello di sistema (root dell'attaccante, firmware manomesso), attacchi fisici al chip,
un proprietario costretto a sbloccare. Qui aiutano solo /e/OS o GrapheneOS aggiornati e il blocco del telefono.

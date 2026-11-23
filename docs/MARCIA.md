# Tabella di marcia, app per app (22 novembre 2026)

Cosa resta da fare in ogni app, raccolto dai file di stato delle aree (`docs/stato/*.md`) e dal controllo di sicurezza del
22 novembre (`docs/SICUREZZA.md`). Ogni voce si misura sui «Principi di base» di CLAUDE.md. Le prove del proprietario sul
telefono vengono prima di ogni lavoro nuovo: molte funzioni sono in codice e aspettano solo l'esito.

Legenda: **P** = prova del proprietario sul telefono · **D** = decisione del proprietario · **C** = codice da scrivere ·
**S** = sicurezza.

## Eurise (Scrivania, Oggi, Calendario, Attività, Note, Contatti, Cloud, Orologio, File, Scanner) — area Organizzazione
- **P**: giro 748 e successivi: Ceruleo, icone «Sfondo delle bolle», Agenda con «@» e «Parti alle…», Drive («Cambiato da
  ieri», cartelle condivise di Murena), elenchi condivisi e «Affida a…», pagine della Scrivania a piacere e trascinamento.
- **D**: kDrive (piano con WebDAV o scope «drive» di Infomaniak); nome della Scrivania (Benvenuto, Casimiro, Larino) e TMview.
- **C**: disponibilità in un link; nota della riunione a più mani; «Affida a…» col trasporto di Comunicazione; Rubrica
  completa in `core/contacts` (con Comunicazione); Impostazioni Eurise (proposta in `docs/IMPOSTAZIONI.md`, aspetta le tre
  scelte); simboli nuovi nei temi; foto e scansioni nelle Note.
- **S**: Condividi verso Eurise ora rifiuta i file privati dell'app (22 novembre).

## Alfred ed Eurise AI — area Alfred
- **P**: frasi di AlfAzioni e AlfAvvio, Vittorio come voce, cervello sul telefono, passi 1 e 2.
- **C**: «Di' Alfred» (parola di risveglio con sherpa-onnx in Voce, spenta di base, solo in carica); azione proposta dalla
  risposta libera; messaggi letti segnati come letti (contratto di Messaggi); Alfred nel centro privacy; il PC come cervello
  (con Computer).
- **In attesa**: API ufficiale di Lumo; musica classica per la sveglia; icona di Eurise AI.
- **S**: le azioni partono solo dal tocco o dal «sì» dell'utente, a telefono sbloccato (verificato il 22 novembre).

## Password — area Piccole
- **P**: carte con NFC e fotocamera, «I miei dati» nei moduli, accessi istantanei nel Browser, centro privacy e cestino unico.
- **C, in fondo**: Password Famiglia (tavole da approvare); kMail tra gli alias; kDrive (bloccato da Infomaniak).
- **S**: il backup della cassaforte lo legge solo l'app Eurise (22 novembre); import da file solo da fuori dall'app.

## Browser — area Browser
- **P**: Telefono e computer (estensione 0.3.0), la nuova scheda di Firefox, Traduci.
- **D**: tavola PcImporta; firma dell'estensione su AMO.
- **C**: import delle password dal computer (SQLite di Chrome/Firefox, tipo del Ponte, mai un CSV); Telefono e computer fuori
  casa; cronologia (spenta di base).
- **S**: Play Services di Google sostituite da microG (22 novembre); passkey non usate dal proprietario, nessuna prova.

## Galleria — area Piccole
- **P**: editor (ritaglio, cursori, velocità dell'anteprima, Regola, Curve, Prospettiva, Espandi, Modifiche), video, caricamento su Murena.
- **C, piccoli**: originali tenuti 30 giorni dopo «Sostituisci»; maschere per singolo passo (solo se serve).

## Messaggi, Telefono, Chat, Posta — area Comunicazione
- **P**: molte tavole in codice aspettano la prova (Persone e canali, ChatBasi, Vocali, Programma, Cartelle, Thread,
  Posizione, Truffa, Turni, Spese, Approvazioni, Faccende, Uscite…), con due telefoni o Element dall'altra parte.
- **D**: dove stanno gli strumenti del gruppo (cloud del gruppo o server Eurise); posta con un fornitore (Migadu,
  Purelymail, mailbox.org) o server nostro (Stalwart + Continuwuity + LiveKit); il dominio; OAuth Gmail/Outlook (passo del proprietario).
- **C**: Email da lavoro (Per te / Aspetti / Squadra, punto del mattino, «se non risponde», invio all'ora giusta); Email,
  resti («Da fare» e scadenze, In breve, ricerca sul server, inoltro con allegati, riassunto per «Oggi»); Faccende e Uscite
  anche in Email; La mia Bussola, seconda fila (in futuro); Studio medico (NRE per email e SMS, appuntamenti).
- **In corso** (22 novembre, mattina): ramo `prova-comunicazione`, da unire a `brand` quando ha un giro verde.

## Leggi — area Piccole/Browser
- ~~**C**: modelli di traduzione nel centro privacy~~ fatto il 22 novembre (giro 1048, «Lingue e traduzioni di Leggi»).
- **S**: impronta dei modelli di traduzione controllata quando l'elenco di Mozilla la porta, rinvii solo in https (22 novembre).

## Voce — area Alfred
- **C**: parola di risveglio (vedi Alfred). ~~Scaricamento dei modelli nel centro privacy~~ fatto il 22 novembre (giro 1048).

## Office — area Office
- **P**: testata nel motore, dock, «Scarica» del motore, vista rapida, PDF con «Inserisci da Scanner», «Compila con I miei
  dati», «Link dal cloud», «Chiedi all'AI».
- **C**: pubblicazione del motore nuovo (giro 1023 di office-app) dopo la prova sull'emulatore `grande` e `tablet`;
  computer («Apri da Eurise File», «Collegato a», Flathub, poi la webapp); peso del motore (113/315 MB); «I miei dati» nei
  moduli del motore; tablet vero.
- **S**: Office non apre più da fuori i propri file privati (22 novembre).

## Mappe — area Mappe
- **P**: schermo diviso in auto, «Dove devi andare» con Casa e Lavoro, gesto a due dita, sezione Mappe sul computer.
- **D**: mappa dentro la finestra di Eurise sul computer; «Avvisa» per l'impegno vicino.
- **C**: pietre miliari della Croazia (giro mensile); in attesa di fonti: segnalazioni condivise, traffico, prezzi dei viaggi.
- **Rimandato alla pubblicazione** (deciso dal proprietario): accordo con Transitous oltre 720 richieste al giorno, contatto nel User-Agent.
- **S**: variante prova/ufficiale nel patch (21 novembre); backup solo verso l'app Eurise (22 novembre).

## Tastiera — area Piccole
- **P**: giri 663, 675, 758, 1011 (modello delle lettere, barra a comparsa, frasi abituali nel backup).
- **C, rimandato**: scrittura a mano (nessun modello libero).

## Computer (Ponte, app, estensione, Flatpak) — area Computer
- **P**: la schermata del 16 novembre.
- **C**: spuntare un'attività dal computer; meteo; note ed email importanti; Calendario e Attività su CalDAV; Note su
  WebDAV; Email; Alfred; icona nella barra e avvio con la sessione; Flathub dai sorgenti.

## Backup — area Backup
- **P**: backup e ripristino veri sul telefono (sull'emulatore 48 controlli su 48, giro 1014).
- **S**: lo zip di ogni app lo legge e lo rimette solo l'app Eurise (22 novembre); prova delle porte aggiornata.

## Fotocamera — nuova (22 novembre)
- Chat nuova con le istruzioni del proprietario: studio dei migliori, proposta, anteprime, poi codice (ramo `prova-fotocamera`).

## Sicurezza e velocità di tutta la suite — questo progetto
1. ~~APK di prova non «debuggable»~~ fatto il 22 novembre (prove sull'emulatore riscritte senza `run-as`).
2. Librerie: zxing 3.5.4 (firma), Tastiera e Office, Compose BOM 2026.11 con compileSdk 37.1 e i due interruttori di AGP 9, Kotlin 2.4.
3. ~~Scaricamenti dei modelli (Leggi, Voce, AI) nel centro privacy~~ fatto il 22 novembre (giro 1048).
4. Pulizia del codice e velocità (in corso): ~~R8 in tutte le app~~ (giro 1048); poi R8 meno prudente app per app.
5. Impronte «strict» anche nei flussi a parte (Office, Mappe, schermate, prova d'avvio del Browser).
6. ~~Schedario~~ (22–23 novembre, fatto: Browser, Office, Messaggi, Backup, Chat; pressione lunga sui fogli della vista
   rapida di Office, giro 1107).

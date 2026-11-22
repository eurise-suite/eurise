# Impostazioni Eurise: proposta di voci e navigazione (da concordare tra le aree)

Proposta dell'area Organizzazione, 22 novembre 2026, sulla tavola **ImpEurise** (pagina Note della tela, approvata dal
proprietario il 21 novembre). Prima del codice serve il sì delle aree che hanno una voce: la risposta si scrive in fondo,
in «Risposte delle aree», una riga per area. Chi non risponde resta con la riga di riserva (punto 4), che non tocca nulla
nella sua app.

## 1. L'idea in tre righe
- Un posto unico, **Impostazioni Eurise**, con la ricerca in alto e tre gruppi: **Per tutta la suite**, **I tuoi dati**,
  **Le app**. Ogni riga ha il nome e, sotto, lo stato in poche parole («Destra · ricerca che si tira giù»).
- **Nessuna impostazione cambia padrone**: ogni app tiene le sue preferenze, le sue pagine e le sue entrate di oggi.
  Impostazioni Eurise mostra, cerca e apre; non scrive le preferenze delle altre app.
- Le sole cose che salgono a livello di suite sono quelle che valgono già per tutte (tema, accento, mano), e restano
  dove sono salvate oggi (chiavi della Scrivania, servite da `AppearanceContract`): nessuna migrazione.

## 2. Navigazione
- **Dove vive**: una rotta `settings` nell'app Eurise (`MainActivity`, `EuriseHub`), modulo nuovo `feature/settings`
  (dipende solo da `core`; la navigazione verso le altre sezioni passa da `app`, come vuole CLAUDE.md).
- **Come ci si arriva**:
  1. **Ingranaggio del Pannello Eurise**: tocco breve = Impostazioni Eurise; pressione lunga = Impostazioni di Android
     (oggi il tocco apre Android). Regola dell'ergonomia: il tocco breve è la scelta preferita.
  2. **Icona nel cassetto** «Impostazioni Eurise» (activity-alias `.SettingsApp` → `SectionShortcutActivity`, sezione
     `settings`), come File, Scanner, Calendario, Attività e Note. Si può spegnere come le altre.
  3. **Ricerca universale** della Scrivania: le voci entrano tra i risultati («tema» → Aspetto, «registrazioni» → Note ›
     Dove salvo le registrazioni).
  4. **Da ogni app**: dove un'app ha già le sue impostazioni, quelle restano; in più può aprire Impostazioni Eurise sulla
     sua voce (sezione `settings:<voce>` con `MainActivity.EXTRA_SECTION`).
- **Indirizzi**: `settings` (pagina principale), `settings:<voce>` (una voce, es. `settings:notes`),
  `settings:<voce>/<punto>` (un punto preciso, es. `settings:notes/voice-place`). Gli stessi indirizzi li usano la
  ricerca e le altre app.
- **Indietro**: dalla pagina di una voce si torna a Impostazioni Eurise se si è entrati da lì, altrimenti dove si era.
- **Ricerca**: campo in alto che si tira giù sotto il pollice (regola dell'ergonomia), cerca nei titoli, negli stati e
  nelle parole di ogni punto; il risultato dice il percorso («Note › Dove salvo le registrazioni»). Le parole delle nostre
  voci stanno in un catalogo nel modulo; quelle delle altre app arrivano dal contratto (punto 4).

## 3. Le voci

### Per tutta la suite
| Voce | Dentro | Oggi sta in | Area | Cosa cambia |
|---|---|---|---|---|
| **Aspetto** | tema (segui lo sfondo, Perla, Notte), colore d'accento e colore personale, tema delle icone, forma, colore dei simboli | Scrivania › Griglia e icone (`AppearanceSheet`, chiavi `eurise_launcher`) | Organizzazione | pagina propria con le stesse chiavi; nella Scrivania restano griglia, nomi, bolle, sfondi, numero dell'Agenda, con un rimando ad Aspetto |
| **Mano e gesti** | mano dominante (destra, sinistra), scorrere giù (Pannello), scorrere su (ricerca), ricerca che si tira giù | Scrivania (`appearance_left_handed` e i due scorrimenti) | Organizzazione | «Ricerca per mancini» diventa «Mano dominante: Destra / Sinistra», stessa chiave (`METHOD_HAND` non cambia) |
| **Privacy** | servizi accesi con le tacchette, permessi, registro dei contatti | rotta `privacy` (`feature/privacy`) | Organizzazione con Sicurezza | nessuno: la riga apre il centro privacy |

### I tuoi dati
| Voce | Dentro | Oggi sta in | Area | Cosa cambia |
|---|---|---|---|---|
| **Backup cifrato** | frequenza, destinazione, frase, ripristino | rotta `cloud/backup` | Sicurezza (`BackupArchive`), schermo di Organizzazione | nessuno |
| **Cloud e account** | account collegati, calendari e rubrica, solo Wi-Fi, agenda con l'assistente | rotta `cloud` | Organizzazione | nessuno |

### Le app (solo quelle installate e accese, nell'ordine della suite)
| Voce | Dentro | Oggi sta in | Area |
|---|---|---|---|
| **Scrivania** | pagine, bolle che si adattano, griglia, categorie e dock, modalità, schermo sempre acceso, sfondi e blocco, suonerie, fonti (notizie, borsa, video, oroscopo) | fogli della Scrivania (`LauncherScreen`) | Organizzazione |
| **Agenda** | nome nel saluto, numero del giorno, meteo e strada (servizi) | Scrivania › Aspetto e Accoglienza | Organizzazione |
| **Calendario e Attività** | calendari mostrati e colori, promemoria, Date importanti, Farmaci, elenchi condivisi | Calendario, Attività | Organizzazione |
| **Note** | **Dove salvo le registrazioni** (NoteVocaleDove), riordino con Alfred, note bloccate | oggi solo tenendo premuto il microfono o «Cambia» | Organizzazione |
| **Orologio** | sveglie, «Ti sveglia Alfred», suoneria delle sveglie | Orologio | Organizzazione |
| **File e Scanner** | Eurise Leggi (spenta di base), Riordina, PDF cercabile | Drive, Scanner | Organizzazione |
| **Alfred** | voce, modelli sul telefono, cosa può leggere, online | `feature/assistant` (AstImpostazioni), APK AI e Voce | Alfred |
| **Telefono e Rubrica** | filtro, sospetto spam, elenco dei numeri molesti | APK Telefono | Comunicazione |
| **Messaggi, Chat, Email** | una riga per app | APK Messaggi, Chat, Email | Comunicazione |
| **Browser** | — | `browser/SettingsScreen.kt` | Browser |
| **Tastiera, Password, Galleria** | una riga per app | APK separati | Password, Tastiera, Galleria |
| **Mappe e Meteo** | — | APK Mappe; Meteo nell'APK principale | Mappe |
| **Office** | — | APK Office | Office |

Le voci dell'APK principale hanno una pagina con i loro punti (titolo, stato, parole per la ricerca, indirizzo); un tocco
apre il punto dove sta oggi (foglio della Scrivania, rotta dell'app). Per la Scrivania serve un'entrata nuova:
`LauncherActivity` accetta `eu.eurise.extra.SETTINGS` con il nome del foglio da aprire.

## 4. Le altre app: contratto (parte comune, `core/suite`)
Si allarga `AppHubContract` (lo stesso verso del centro privacy e del cestino: l'app Eurise chiede, l'app risponde; solo
app con la firma della suite).
- **`METHOD_SETTINGS`**: risposta con `KEY_IDS`, `KEY_TITLES`, `KEY_DETAILS` (stato in poche parole, può essere vuoto) e
  `KEY_WORDS` (parole per la ricerca, separate da virgola), stesso ordine. Un'app può dare un punto solo («Browser»)
  o più punti («Filtro delle chiamate», «Elenco dei numeri molesti»…).
- **Aprire**: `ACTION_OPEN` con `EXTRA_PAGE = "settings"` e il nuovo `EXTRA_SETTING = <id>` (senza id: le impostazioni
  dell'app). Password e Galleria hanno già `ACTION_OPEN` (per privacy e cestino); le altre aggiungono l'intent-filter quando vogliono.
- **Riga di riserva** (finché un'app non risponde): nome e icona dell'app; il tocco apre le sue impostazioni se dichiara
  `ACTION_APPLICATION_PREFERENCES` (oggi nessuna lo fa: è una riga di manifesto), altrimenti l'app. Nessuna ricerca dentro.
- L'app Eurise chiede le voci solo quando si apre Impostazioni Eurise o si cerca, sul thread di fondo, e le tiene in
  memoria per la sessione.

## 5. Domande alle aree
- **Tutte**: va bene il contratto del punto 4? Quali punti volete esporre e con quali parole?
- **Comunicazione**: una riga «Messaggi, Chat, Email» o tre righe? La Rubrica (quando ci sarà il codice delle tavole Rub*)
  sta sotto Telefono o è una voce a parte?
- **Alfred**: le impostazioni di Alfred stanno nell'APK principale (`feature/assistant`): la pagina della voce Alfred la
  scrivete voi nel vostro modulo (noi mettiamo solo la riga e la rotta)? E «riordino con Alfred» nelle Note: interruttore
  nostro o vostro?
- **Sicurezza**: Privacy e Backup restano schermi nostri con i vostri pezzi dentro, come oggi?
- **Tema della suite**: oggi il tema Perla/Notte lo sceglie solo la Scrivania; le altre app seguono Android. Proposta:
  `AppearanceContract.METHOD_THEME` (segui lo sfondo, Perla, Notte) così il tema di Aspetto vale per tutte. Chi lo vuole?

## 6. Ordine del codice (dopo il sì)
1. `feature/settings`: pagina principale, ricerca, catalogo delle nostre voci, rotta `settings`, indirizzi
   `settings:<voce>/<punto>`, ingranaggio del Pannello, icona nel cassetto, voci nella ricerca universale.
2. **Note › Dove salvo le registrazioni** come pagina (oggi è solo un foglio), con le tacchette.
3. **Aspetto** e **Mano e gesti** in pagina propria (stesse chiavi), con il rimando dalla Scrivania.
4. Contratto `METHOD_SETTINGS` in `core/suite` e righe delle altre app (con la riserva); poi ogni area risponde quando vuole.
Peso stimato: qualche decina di KB (niente librerie nuove).

## Risposte delle aree
(una riga per area: sì / sì con modifiche / no, e la data)

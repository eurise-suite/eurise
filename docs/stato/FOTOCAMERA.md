# Stato della Fotocamera

File di stato di Eurise Fotocamera (dal 22 novembre 2026). `docs/STATO.md` resta il diario generale: lì si scrive solo una
riga per consegna che rimanda qui. Regole in `CLAUDE.md`, porte e chiavi in `docs/SICUREZZA.md`. Ramo di lavoro
`prova-fotocamera` (nato da `brand`); prima di ogni push si fa fetch e, se `brand` è andato avanti, lo si unisce. Mai push forzato.

## Decisioni del proprietario (22 novembre 2026)
- **APK separato senza rete** (`camera/`, pacchetto `eu.eurise.camera`, di prova `eu.eurise.camera.debug`, Android 11+).
  `INTERNET` e `ACCESS_NETWORK_STATE` tolti nel manifesto con `tools:node="remove"`; Compila APK si ferma se un permesso di
  rete entra nel manifesto finale (passo «Fotocamera senza rete»).
- **Compose da zero su CameraX** (1.7, nel catalogo), prendendo da Aperture le idee utili; nessuna libreria Google chiusa.
- Tutte e dieci le tavole della pagina «Fotocamera» approvate («Approvate, vai», 22 novembre). È un'app nuova: non
  sostituisce nulla, quindi non costringe a disinstallare niente.

## Fatto nel primo passo (v1)
- **Mirino** CameraX (`LifecycleCameraController`, scatto `ZERO_SHUTTER_LAG`), 3:4 sotto la barra; zoom a pillole che
  seguono gli obiettivi del telefono (`zoomStops`: 0,6 · 1 · 2 · 5 secondo quello che c'è); flash e torcia; esposizione.
- **Modi**: Video, Foto, Documento (apre lo Scanner dell'app Eurise quando c'è ed è firmata con la nostra chiave).
  In Foto, tenere premuto lo scatto (380 ms) registra un video finché si lascia. Tasti del volume e tasto fotocamera scattano.
- **Destinazioni** (game changer per chi lavora): pillola sopra lo scatto con la cartella attuale (Rullino o una cartella di
  cantiere/cliente: `DCIM/Eurise/<nome>`). Tocco breve = torna all'ultima usata, pressione lunga = elenco con stellina
  (preferita all'avvio), aggiungi e togli. Nomi puliti (`Destinations.clean`, niente percorsi). Test in `DestinationsTest`.
- **Mano dominante**: letta dalla suite (`AppearanceContract.METHOD_HAND`); da mancini la fila in basso si specchia e la
  pillola delle destinazioni va a sinistra. Senza l'app Eurise: destra.
- **Posizione nelle foto**: spenta di base; l'interruttore apre il foglio della suite (`PrivacyConsentSheet`, 10 tacchette,
  7 su 10 da accesa: resta sul telefono ma finisce nelle foto) e solo dopo chiede il permesso. Posizione da
  `LocationManager`, mai Play Services.
- **Condividi pulito**: le copie per «Condividi» passano da `CleanMedia` (nuovo in `core/suite`: toglie GPS, numeri di
  serie, proprietario, note del produttore, XMP; video rimessi in contenitore senza metadati) e da un FileProvider non
  esportato che dà accesso al solo file condiviso.
- **Rivedi**: le foto della sessione (anche più d'una), elimina, condividi pulito, apri nella Galleria (solo se firmata con
  la nostra chiave, `SuiteAccess.trusted`).
- **Ingressi**: avvio normale, «Apri la fotocamera» e «Registra un video» di Android, riquadro nelle impostazioni rapide
  (`CameraTileService`), blocco schermo (`SecureCameraActivity`, `STILL_IMAGE_CAMERA_SECURE`: si vedono solo le foto appena
  fatte; Galleria, condividi ed elimina chiedono prima lo sblocco), «Scatta foto» per le altre app (`CaptureActivity`,
  `IMAGE_CAPTURE`: foto pulita dove dice l'app, miniatura se non dice nulla; un indirizzo che punta ai file della Fotocamera
  si rifiuta con `isForeignUri`).
- **Icona**: Smeraldo, generata da `scripts/icone.py` (voce «Fotocamera» in `scripts/icone_disegni.json`).

## Porte esportate (da confrontare con la prova sull'emulatore)
`CameraActivity`, `SecureCameraActivity`, `CaptureActivity`, `CameraTileService` (solo il sistema, `BIND_QUICK_SETTINGS_TILE`).
Più `ProfileInstallReceiver` di androidx, protetto da `DUMP`. Il FileProvider non è esportato. Nessun fornitore di dati e
nessun servizio aperto alle altre app; verso la suite solo `SuiteAccess` (permesso di firma).

## Prova sull'emulatore
`.github/workflows/prova-fotocamera.yml` (parte a ogni push di `prova-fotocamera` che tocca `camera/` o `core/suite/`) con
`.github/scripts/prova-fotocamera-emulatore.sh`: nessun permesso di rete, solo i componenti attesi esportati, ogni ingresso
si apre senza errori (schermate nell'artefatto), il volume scatta (prova morbida con la fotocamera simulata), «Scatta foto»
rifiuta un indirizzo verso i nostri file, il fornitore delle copie non si apre dalla shell. Senza «adb root», di proposito.

## Da fare (passi successivi, già sulle tavole)
- **Private**: foto direttamente nella cartella cifrata della Galleria. La chiave della cartella oggi vive solo in memoria
  mentre è aperta: serve una chiave pubblica della Galleria (X25519 + AES-GCM) per cifrare senza aprirla. Cambia la Galleria.
- Destinazioni verso Attività (pratica, cliente) e Drive; proposta dal Calendario («sei al cantiere Verdi»).
- Suggerimenti dal testo (Tesseract con modello scaricato a impronta, via Leggi) e QR con zxing.
- Prima/dopo, rapporto fotografico, annotazioni, misure con oggetto di riferimento (senza ARCore).
- Ritratto, Pro, Notte/HDR (estensioni CameraX dove il telefono le ha), scorciatoie dell'icona.
- Galleria: usare `CleanMedia` al posto del suo codice di pulizia (stesso lavoro, un posto solo).

## Consegne
- 22 novembre: primo passo (v1) sul ramo `prova-fotocamera`, giro 1053 di Compila APK in `apk-prova` (`camera-debug.apk`, 5,4 MB). Prova sull'emulatore riuscita; lo scatto col volume parte (`takePictureInternal`) ma con la fotocamera simulata la foto non arriva nei 6 secondi della prova: da guardare sul telefono.
- 22 novembre, pomeriggio: meno rumore in casa (giro 1056). Le prime foto sul Fairphone 6 (ISO 475-677, 1/100 s) avevano grana e aloni perché lo «zero shutter lag» usa un fotogramma del mirino, con riduzione del rumore e nitidezza da anteprima. Ora c'è `CAPTURE_MODE_MINIMIZE_LATENCY` (la catena delle foto vere) e, in Foto, la gamma di fotogrammi più ampia fino a 30 che la fotocamera dichiara (es. 15-30), così il tempo si allunga invece di alzare gli ISO. Il video tenendo premuto lo scatto in Foto eredita questa gamma: da guardare. Il passo dopo per la poca luce è Notte/HDR con le estensioni CameraX, se il Fairphone le offre.
- 22 novembre, sera: ancora grana (foto di prova: ISO 751, 1/100 s, quindi l'esposizione automatica non ha allungato il tempo). Il controller di CameraX lascia il posto a `CameraEngine` (ProcessCameraProvider con mirino, foto e video), che legge tempo e ISO dal mirino. Allo scatto con poca luce, senza flash e dove il sensore ha il controllo manuale, la foto si fa con tempo più lungo (fino a 1/30 s, 1/60 s da 2× in su) e ISO più bassi in proporzione, puntando a ISO 200. Tempo e ISO si vedono nel pannello dell'esposizione (icona del sole).
- 22 novembre, notte (foto di Sun in un altro ambiente: ISO 285 e 756 a 1/30 s, molto meglio ma un po' scure e molto gialle sotto le lucine). Con poca luce un terzo di stop in più (in manuale il telefono non schiarisce più le ombre da sé) e tempo fino a 1/20 s se c'è lo stabilizzatore ottico (1/40 s da 2×). Luce calda: `Warmth` corregge a metà strada verso il neutro solo quando la dominante è forte (rosso medio oltre 1,3 volte il blu sui toni medi), ricopiando i dati dello scatto; le altre foto non si ricodificano. Test in `WarmthTest`. Giro 1068 in `apk-prova`.
- 22 novembre, sera tardi (foto col giro 1068: albero ISO 847 a 1/30 s, più chiaro e meno arancione ma tenda ancora crema; pianta ISO 220 a 1/30 s, sempre scura, mediana 76 su 255). Lo stabilizzatore ottico non risulta dichiarato (tempo fermo a 1/30 s): tetto portato a 1/25 s anche senza. `Warmth` diventa `Ritocco`: colori corretti al 70% verso il neutro (prima al 50%) da una dominante di 1,2, e curva dei toni medi quando la mediana è sotto 95 (verso 110, bianco e nero fermi). Test in `RitoccoTest`.
- 22 novembre, notte: rielaborazione dopo lo scatto, primo passo (`Ritocco`): colori per ogni dominante (anche la rosata della frontale), macchioline di colore tolte da ISO 400 in su (colore mediato su 4×4 pixel, luminosità piena), toni medi locali (luce della zona su griglia a 1/8, curva verso 110, guadagno al massimo 2,2). Provato prima su un modello in Python con le foto di Sun. Frontale (ISO 1828-2058 a 1/25 s, senza stabilizzatore): niente tempi più lunghi, solo ritocco. Giro 1072 in `apk-prova`. Prossimo passo: Notte con più fotogrammi fusi, anche per la frontale; poi le estensioni CameraX dove ci sono.
- 22 novembre, notte: **Notte con più fotogrammi** (passo 2). Con il flash spento e ISO del mirino da 400 in su lo scatto prende una raffica in memoria (4 fotogrammi sulla posteriore, 6 sulla frontale) con la stessa esposizione; `NotteFoto` li allinea al primo (ricerca su versione a 1/4, poi affinamento a piena risoluzione su righe sparse), `Notte` li somma pixel per pixel scartando ciò che si è mosso (niente fantasmi), poi passa al `Ritocco`. Dati dello scatto dal primo fotogramma, posizione se accesa. Durante la raffica la scritta «Notte · tieni fermo», poi «Notte · elaborazione». Test in `NotteTest`. Da guardare sul telefono: tempo della raffica e della fusione, allineamento con la mano.
- 23 novembre, notte (foto col giro 1072: tende a sovraesporre e la grana si vede; con pochissima luce, ISO 2374, l'albero vira al ciano e le lucine si bruciano). Il terzo di stop in più è tolto. `Ritocco` più prudente: colori presi dalle superfici chiare (75°-97° percentile, non tutta la foto: l'albero verde faceva virare tutto) e corretti a metà; toni medi solo sotto mediana 85, verso 95, guadagno al massimo 1,6 (1,3 da ISO 1600), spento sulle luci (da 80 a 180). **Modalità Notte** scelta a mano tra i modi (6 fotogrammi sulla posteriore, 8 sulla frontale, senza flash); in Foto la Notte parte da sola da ISO 800.
- 23 novembre: **tocca per mettere a fuoco ed esporre** (cerchio sul punto, torna all'automatico dopo 5 s). Riferimento della fotocamera del Fairphone (foto di Sun): albero ISO 1199 a 1/33 s, mediana della luminosità 144 (la nostra ~109), colori lasciati caldi (superfici chiare R/B 1,65), poca grana: il sistema fonde più fotogrammi da sé. Quindi la strada è la Notte con più fotogrammi, non schiarire di più una foto sola. Notte, ritocco prudente e messa a fuoco nel giro 1080 in `apk-prova`.
- 23 novembre: la Fotocamera entra in `brand` (scelta di Sun: così `apk-prova` non la perde a ogni pubblicazione delle altre app). **Notte del telefono**: nella modalità Notte, dove il produttore offre l'estensione Notte di CameraX (`camera-extensions`, libreria aperta; l'elaborazione sta nel telefono, niente rete), lo scatto lo fa il telefono con i suoi algoritmi e la scritta dice «Notte del telefono»; dove non c'è, la Notte di Eurise con più fotogrammi. Nel registro (`EuriseCamera`) l'elenco delle elaborazioni del telefono per obiettivo (notte, HDR, auto, sfocato, volto). Da guardare sul Fairphone: se l'estensione c'è. Giro 1088 in `apk-prova`.
- 23 novembre, sera (foto col giro 1088, albero: ISO 2163-2195 a 1/30 s, con e senza Notte quasi uguali, grana misurata 14-15 su un muro in entrambe). La Notte non toglieva rumore: la soglia fissa dei fantasmi (28) a ISO 2000 scartava anche la grana. Ora la soglia segue la grana misurata nell'allineamento (3 deviazioni, tra 28 e 64). Nuovo filtro della grana nel `Ritocco` (filtro sigma 5×5 sulla luminosità da ISO 800, soglia che sale con gli ISO e scende con i fotogrammi fusi; provato in Python sulla foto di Sun: grana da 14 a 10 su un fotogramma). Riduzione del rumore del telefono chiesta in alta qualità. In «Software» dei dati della foto c'è come è stata fatta (ritocco, Notte con fotogrammi, pixel fusi, soglie): dalle foto di prova si capisce cosa è successo. La Notte del telefono lascia il «Software» del produttore. Giro 1099 in `apk-prova`.
- 23 novembre, sera (foto col giro 1099, albero): Notte 6/6 fotogrammi (modalità Notte, ISO 2153 a 1/25 s) e 4/4 (partita da sola in Foto, ISO 2687), in media 5,8 e 3,9 fotogrammi per pixel, soglie al massimo (64). Grana sul muro da 14-15 a 2,4-3,4: il salto atteso. Dettagli puliti, nessun fantasma visibile. La Notte del telefono non compare: il Fairphone 6 non offre l'estensione Notte alle altre app.
- 23 novembre, sera tardi: dopo la Notte niente filtro della grana (soglia divisa per i fotogrammi, non per la radice: faceva l'effetto acquerello su velluto e ombre) e al suo posto un po' di nitidezza (pixel meno media 5×5, metà, differenze sotto 4 lasciate stare). Notte automatica con tutti i fotogrammi (6 dietro, 8 davanti) da ISO 1600. Giro 1104, ripubblicato con tutta brand nel giro 1106.
- 23 novembre, notte (foto col giro 1106: ingresso Notte 6/6 a ISO 1058, pulito; TV tra gli scaffali Notte 4/4 a ISO 511, macchie di colore rosse e verdi e grana nelle ombre). Nelle ombre (luminosità da 80 a 20) il colore viene da una griglia molto più levigata (~30 pixel) e cala fino al 35%; la nitidezza si spegne nelle ombre. **Verso delle foto**: lo schermo è fisso in verticale e CameraX non sapeva come era girato il telefono, le foto in orizzontale uscivano di lato: ora un `OrientationEventListener` dà il verso a foto e video. Giro 1112 in `apk-prova`.

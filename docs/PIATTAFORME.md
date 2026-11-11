# Eurise su più sistemi: /e/OS, Android, Linux (11 novembre 2026)

Richiesta del proprietario: Browser e Office nascono per /e/OS, Android e Linux, con Ubuntu e Fedora come riferimento
(regola fissa in CLAUDE.md), e impostati per arrivare in seguito ad altri sistemi mobili e desktop. Questo documento dice
come. Le scelte segnate **da approvare** aspettano il proprietario; il resto applica decisioni già prese (STATO.md,
«In fondo alla road map: computer» e «Aspetto Eurise per Linux»).

## Regola generale: nucleo comune, guscio per sistema
Ogni app che deve girare su più sistemi è divisa in tre strati.

| Strato | Cosa contiene | Dove gira |
|---|---|---|
| **Nucleo** | logica, formati, dati, sincronizzazione (WebDAV/CalDAV, unione a tre vie), cifratura, protocollo con la suite | ovunque, senza `android.*` né API di un solo sistema |
| **Interfaccia** | schermate dalle tavole approvate | condivisa dove il motore lo permette (vedi sotto) |
| **Guscio** | avvio, permessi, file, notifiche, pacchetto, dialogo con le altre app Eurise | uno per sistema, il più sottile possibile |

- Il nucleo in Kotlin Multiplatform (già deciso il 10 novembre); le interfacce in Compose Multiplatform quando sono nostre,
  in HTML/JS quando il motore ne ha già una che va ovunque (Office).
- Ciò che dipende dal sistema passa da un'interfaccia piccola nel nucleo (`expect/actual` o un'interfaccia Kotlin) con
  un'implementazione per sistema: così un sistema nuovo è un guscio nuovo, non una riscrittura.
- Formati aperti e uguali ovunque (ODF/OOXML, iCalendar, vCard, Markdown/HTML delle note, il nostro backup cifrato).
- La privacy non cambia con il sistema: centro privacy, interruttori, «cosa esce» uguali su telefono e computer.

## Linux: Ubuntu e Fedora come riferimento
- **Versioni di prova**: l'ultima Ubuntu LTS e l'ultima Ubuntu, l'ultima Fedora Workstation; GNOME su entrambe, KDE
  (Kubuntu, Fedora KDE) come seconda verifica. Architettura x86_64, poi aarch64.
- **Pacchetto**: Flatpak su Flathub per le nostre app (su Fedora è già pronto; su Ubuntu si aggiunge con un comando).
  Se serve, dopo, anche .deb e .rpm. Costo: zero (Flathub e la compilazione su GitHub Actions sono gratuiti).
- **Prova automatica**: GitHub Actions compila il Flatpak e lo avvia in contenitori Ubuntu e Fedora (avvio, apertura di
  un documento, chiusura) a ogni giro, come oggi si pesano gli APK.
- **Aspetto**: estensione GNOME e tema KDE «Aspetto Eurise» già decisi; le nostre app seguono Perla/Notte da sole.

## Browser: Gecko su tutti i sistemi (da approvare)
La richiesta di Linux decide la questione rimasta aperta (GeckoView o WebView): **Gecko**, il motore di Firefox, ovunque.
WebView esiste solo su Android e non ha estensioni; Gecko c'è su Android (GeckoView) e sul computer (Firefox).

- **Nucleo condiviso: l'estensione Eurise** (WebExtension, lo standard di Firefox): In Note, Da fare, Traduci, data
  trovata → Calendario, Ascolta, password e passkey da Eurise Password, protezione aggiuntiva, nuova scheda «dalla suite».
  La stessa estensione gira dentro GeckoView (estensione integrata nell'APK, parla con l'app in Kotlin) e dentro Firefox
  sul computer (parla con l'app Eurise del computer con il native messaging).
- **Android ed /e/OS**: app nostra in Kotlin/Compose (tavole BrwNuovaScheda, BrwPagina, BrwAccesso, BrwProtezione e la
  seconda fila quando è approvata) sopra GeckoView. Peso: circa +50 MB per l'APK arm64 (già noto); è un APK a parte.
- **Linux**: Firefox così com'è, aggiornato da Mozilla e dalla distribuzione (idea del proprietario del 10 novembre), con
  l'estensione Eurise e un profilo di impostazioni Eurise (motore di ricerca, protezione, nuova scheda). Non si compila
  un Firefox nostro: costerebbe aggiornamenti di sicurezza che non possiamo garantire.
  - Ubuntu: Firefox è uno snap; il native messaging passa dal portale WebExtensions (funziona dal 2022).
  - Fedora: Firefox è un pacchetto RPM; il native messaging funziona direttamente. Con Firefox Flatpak passa da
    `xdg-native-messaging-proxy` (soluzione ufficiale del 2025): da provare.
  - Firma dell'estensione: Firefox installa solo estensioni firmate da Mozilla. La firma per la distribuzione fuori
    vetrina è gratuita e automatica (regola di spesa rispettata).
  - Limite già noto: l'estensione cambia barra laterale, nuova scheda, pulsanti, menu e colori, non la finestra intera.
- **Altri sistemi**: Windows e macOS come Linux (Firefox + estensione). iOS obbliga a WebKit: lì la stessa estensione
  diventerebbe una Safari Web Extension (stesso standard, con differenze da adattare).
- **Ancora da scegliere**: motore di ricerca predefinito (proposta Brave Search) e seconda fila delle tavole.

## Office: LibreOfficeKit con l'interfaccia web di Collabora (da approvare)
Il motore è già scelto (LibreOffice, ramo co-25.04 di Collabora; su Android scaricato a richiesta, circa 66 MB). Per
più sistemi la strada è quella che Collabora stessa ha preso nel 2025: **un'interfaccia sola in HTML/JS** (quella di
Collabora Online, MPL 2.0) sopra il motore locale, la stessa su Android, Linux, Windows e macOS. Collabora Office per il
computer è già su Flathub (`com.collaboraoffice.Office`) e come snap: Ubuntu e Fedora sono coperti.

- **Nucleo condiviso: interfaccia web personalizzata Eurise**: barra a nastro con le isole bianche sul Perla, gruppo
  Suite in borgogna (Detta, Traduci, Da fare, Evento, Note / Viaggio, Grafico), «Cerca un comando» (Ctrl K), riquadro di
  ricerca con le tacche di privacy, colonna «In Eurise File». Le tavole OffTablet e OffComputer diventano CSS e JS di
  questa interfaccia: scritte una volta, uguali ovunque. OffTelefono usa la versione per il telefono della stessa
  interfaccia, compatta.
- **Android ed /e/OS**: guscio Kotlin (Eurise Office, APK piccolo): scarica il motore, apre e salva con Eurise File e i
  cloud scelti, porta i comandi della Suite alle altre app Eurise (permesso di firma).
- **Linux**: guscio desktop sul modello di quello di Collabora (Qt), con il nostro motore e la nostra interfaccia;
  Flatpak su Flathub. Il motore qui è dentro il pacchetto (sul computer il peso conta meno), i caratteri dal sistema.
- **Collaborazione**: in tempo reale con Collabora Online / Nextcloud Office sul server dell'utente (già deciso); nessun
  server nostro.
- **Altri sistemi**: Windows e macOS hanno già un guscio Collabora da cui partire; iOS ha l'app Collabora Office (stessa
  architettura).

## Ordine proposto
1. Approvazione: tavole Office e seconda fila del Browser; Gecko per il Browser; interfaccia web di Collabora per Office.
2. Office: interfaccia web Eurise (nastro, Suite, ricerca) provata prima nel browser del computer con il motore di
   Collabora, poi nel guscio Android (motore a richiesta) e nel Flatpak per Linux.
3. Browser: estensione Eurise (comune), poi app Android su GeckoView, poi profilo ed estensione per Firefox su Ubuntu e
   Fedora con l'app Eurise del computer (native messaging).
4. Lavori di Actions: Flatpak e prove su Ubuntu e Fedora per entrambe.

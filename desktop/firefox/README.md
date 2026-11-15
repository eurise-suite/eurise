# Eurise per Firefox (computer)

Firefox così com'è, aggiornato da Mozilla e dalla distribuzione, con l'aspetto e le funzioni di Eurise (tavola PcFirefox,
`docs/PIATTAFORME.md`). Si parte da Linux (Ubuntu e Fedora); la stessa estensione andrà su Windows e macOS.

- **Aspetto**: Perla e Notte su finestra, schede (quella aperta ben distinta), barra degli indirizzi, menu e pannello.
- **Pulsante Eurise** (Ctrl+Maiusc+E): apri sul telefono, In Note, Da fare, Appuntamento in Calendario (data trovata
  nella frase scelta), Documento al telefono (fino a 50 MB, in Download/Eurise).
- **Pannello Eurise** (Alt+Maiusc+E): scrivi e manda (Note, Attività, appunti), la scheda, «Dal telefono».
- **Tasto destro**: le stesse cose su pagina, link, immagini e testo scelto.
- **Oggi** nel pannello: appuntamenti di oggi e cose da fare, chiesti al telefono solo con il pannello aperto (sul
  telefono «Oggi sul computer», spento di base).
- **Detta dal telefono** (Alt+Maiusc+D, pannello o pulsante): il telefono ascolta e il testo va nel campo della pagina
  dove si stava scrivendo.
- **Impostazioni Eurise per Firefox**: protezione del Browser Eurise (tracciatori bloccati ovunque, cookie dei tracciatori
  rifiutati e gli altri chiusi per sito, solo HTTPS, niente ping e collegamenti anticipati) con `browser.privacy`, accesa
  di base perché trattiene i dati; spenta, ogni impostazione torna a Firefox (`clear`). Brave Search come motore
  (`chrome_settings_overrides`): Firefox chiede all'installazione, decide l'utente.
- **Password dal telefono** (spenta di base): «Chiedi al telefono» sui moduli di accesso; Eurise Password approva con
  l'impronta e la password si scrive nel campo.

Tutto passa dal **Ponte Eurise** (`desktop/ponte`): l'estensione parla con `eurise-ponte firefox` (native messaging,
registrato con `eurise-ponte installa-firefox`), che usa il socket locale del Ponte; se il Ponte è spento, ciò che si
manda resta in coda.

Provare senza firma: `about:debugging` › Questo Firefox › Carica componente aggiuntivo temporaneo › `manifest.json`.
Per installarla stabilmente Firefox vuole la firma di Mozilla (gratuita, fuori vetrina): serve l'account AMO del
proprietario.

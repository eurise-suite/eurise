# Eurise per Firefox (computer)

Firefox così com'è, aggiornato da Mozilla e dalla distribuzione, con l'aspetto e le funzioni di Eurise (tavola PcFirefox,
`docs/PIATTAFORME.md`). Si parte da Linux (Ubuntu e Fedora); la stessa estensione andrà su Windows e macOS.

- **Aspetto**: Perla e Notte su finestra, schede (quella aperta ben distinta), barra degli indirizzi, menu e pannello.
- **Pulsante Eurise** (Ctrl+Maiusc+E): apri sul telefono, In Note, Da fare, Appuntamento in Calendario (data trovata
  nella frase scelta), Documento al telefono (fino a 50 MB, in Download/Eurise).
- **Pannello Eurise** (Alt+Maiusc+E): scrivi e manda (Note, Attività, appunti), la scheda, «Dal telefono».
- **Tasto destro**: le stesse cose su pagina, link, immagini e testo scelto.
- **Password dal telefono** (spenta di base): «Chiedi al telefono» sui moduli di accesso; Eurise Password approva con
  l'impronta e la password si scrive nel campo.

Tutto passa dal **Ponte Eurise** (`desktop/ponte`): l'estensione parla con `eurise-ponte firefox` (native messaging,
registrato con `eurise-ponte installa-firefox`), che usa il socket locale del Ponte; se il Ponte è spento, ciò che si
manda resta in coda.

Provare senza firma: `about:debugging` › Questo Firefox › Carica componente aggiuntivo temporaneo › `manifest.json`.
Per installarla stabilmente Firefox vuole la firma di Mozilla (gratuita, fuori vetrina): serve l'account AMO del
proprietario.

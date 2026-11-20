# Elenco Eurise dei numeri molesti

`elenco.txt` è l'elenco che Eurise Telefono usa per segnare le chiamate moleste (decisione del proprietario,
21 novembre 2026). Lo scarica l'app Eurise una volta a settimana, solo col Wi-Fi e solo se la persona l'ha acceso dal
Telefono (Recenti › scudo) o dal centro privacy. Il file è uguale per tutti: non esce nessun numero né la rubrica, le
chiamate si confrontano sul telefono. GitHub vede l'indirizzo da cui si scarica, come ogni sito.

## Formato
- La prima riga è sempre `# eurise-spam 1` (senza, il telefono scarta il file).
- Una riga per numero o prefisso, **in forma internazionale con il «+»** (`+390212345678`, `+216`), poi una
  **tabulazione**, la **categoria** e, dopo un'altra tabulazione, una nota breve.
- Categorie: `wangiri` (squillo dall'estero), `callcenter`, `truffa`, `molesto`.
- Le righe con `#` sono commenti; quelle sbagliate si saltano. Tetto: 400 KB.

## Cosa entra
- Solo numeri o prefissi con **segnalazioni pubbliche e ripetute** (Polizia Postale, AGCOM, operatori, stampa), con la
  fonte nella nota o nel commit. Un numero singolo segnalato da una sola persona non basta: una voce sbagliata fa perdere
  chiamate vere a tutti.
- Mai paesi europei interi né prefissi di città: troppe chiamate vere.
- Ogni modifica è un commit con la fonte; una voce che risulta sbagliata si toglie subito.

## Cosa fa il telefono
Chi è in rubrica o è già stato chiamato passa sempre. Per gli altri, una voce dell'elenco mostra «Elenco Eurise:
<categoria>» sulla chiamata, nella notifica e nei Recenti; con il filtro acceso la chiamata arriva in silenzio (e chi
insiste dall'estero viene rifiutato). Non si blocca nessuno in automatico.

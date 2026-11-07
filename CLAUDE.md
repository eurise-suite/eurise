# Eurise – indicazioni per chi lavora al codice

## Metodo
- Prima del codice vengono sempre le anteprime: render sulla tela di design, discussione con il proprietario,
  e solo dopo l'approvazione si scrive il codice dell'app.
- Il documento di progetto e la tela "Icone della suite" (pagina Calendario, fila "Dopo") sono il riferimento.
- La chiave di firma delle versioni ufficiali non si chiede e non si tocca: la custodisce solo il proprietario.

## Codice
- Kotlin + Jetpack Compose (Material 3), MVVM, un modulo per funzione (`feature/*`), servizi comuni in `core/*`.
- I moduli `feature` dipendono solo da `core`, mai tra loro; la navigazione passa dal modulo `app`.
- KDoc sulle parti non ovvie, niente commenti ridondanti. Testi dell'interfaccia in `strings.xml`.
- Nessuna libreria Google proprietaria (Play Services, Firebase, ML Kit): compatibilità F-Droid.
- Si compila solo su GitHub Actions (l'ambiente di Claude non raggiunge i repository Maven di Google).

## Stile (deciso il 7 novembre 2026)
- Tema chiaro Perla, tema scuro Notte (blu-grafite, accento azzurro).
- Barra delle sezioni flottante e satinata con il pulsante + satinato accanto; la sezione attiva si riconosce
  da colore, tratto più marcato ed etichetta in grassetto, senza alone.
- Calendari: otto tinte della suite; ogni calendario prende la tinta più vicina al suo colore.

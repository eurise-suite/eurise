# Icone degli APK futuri (pronte da copiare)

Famiglia «Firma di stella», come Password, Galleria, Voce e Tastiera: simbolo nella tinta della categoria,
stellina d'oro #D2B06A in basso, fondo blu notte #1F2A44. Disegni presi dalla tela (pagina Brand, `BrandIcona`).

| Cartella   | App      | Tinta                          |
|------------|----------|--------------------------------|
| `office`   | Office   | #8FA9D9 Documenti (azzurro)    |
| `maps`     | Mappe    | #BA9BCB Strumenti (prugna)     |
| `browser`  | Browser  | #79BCC1 Rete (verde acqua)     |
| `messages` | Messaggi | #93BBA0 Comunicazione (salvia) |
| `phone`    | Telefono | #93BBA0 Comunicazione (salvia) |

Quando nasce l'APK:
1. Copiare `drawable/` e `mipmap-anydpi-v26/` della sua cartella in `<modulo>/src/main/res/`.
2. Definire `eurise_icon_background` (#1F2A44) nei colori del modulo, se non lo prende già da un altro modulo.
3. Nel manifest: `android:icon="@mipmap/ic_<nome>"` e `android:roundIcon="@mipmap/ic_<nome>_round"`.
4. Avvio blu notte: `android:windowSplashScreenBackground` = `@color/eurise_icon_background` nel tema (anche in values-night).
5. Notifiche: `eu.eurise.core.designsystem.R.drawable.ic_stat_eurise`.

Il primo piano vale anche come livello monocromatico (icone a tema di Android 13+). `anteprima-*.svg` serve solo
a guardare il disegno senza compilare.


## 24 novembre 2026, sera (progetto Eurise Sicurezza): compilazione automatica, Password non cade più
- **Segnalato dal proprietario** (Fairphone 6, Android 16): `IllegalArgumentException: must have at least one id` da
  `SaveInfo.Builder.setOptionalIds` in `EuriseAutofillService.onFillRequest`. Un modulo con la password e senza campo del
  nome (cambio password, sblocco, PIN) dava ad Android un elenco vuoto di campi facoltativi: Password cadeva a ogni
  richiesta.
- **Correzione**: i campi del nome si passano solo se ci sono; senza, il salvataggio dichiara solo la password
  (`SAVE_DATA_TYPE_PASSWORD`). In più `onFillRequest` è protetta: un errore imprevisto risponde «niente da proporre» e
  finisce nel registro, senza fermare Password né l'app che si compila.

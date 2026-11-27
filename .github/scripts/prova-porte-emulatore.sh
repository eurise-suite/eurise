#!/usr/bin/env bash
# Prova delle porte d'ingresso sull'emulatore (vedi .github/workflows/prova-porte.yml e docs/SICUREZZA.md).
# «prima» ci si aspetta di vedere i buchi (serve a dimostrare che la prova li trova); «dopo» devono essere tutti chiusi
# per la curiosa e tutti aperti (tranne il Ponte) per l'amica. L'esito della prova dipende solo da «dopo».
set -uo pipefail
source "$(dirname "$0")/emulatore-root.sh"
esito=0
ok() { echo "OK   $*"; }
ko() { echo "NO   $*"; esito=1; }
nota() { echo "     $*"; }

installa() { # cartella
  for f in "$1"/*.apk; do
    out=$(adb install -r -g "$f" 2>&1 | tail -1); echo "  $(basename "$f"): $out"
  done
}
togli() {
  for p in eu.eurise.hub.debug eu.eurise.password.debug eu.eurise.messages.debug eu.eurise.keyboard.debug eu.eurise.postazione.debug org.esempio.curiosa org.esempio.amica; do
    adb uninstall "$p" >/dev/null 2>&1 || true
  done
}
bussa() { # pacchetto → righe «porta=STATO …»
  adb logcat -c
  adb shell am start -W -n "$1/org.esempio.curiosa.Prova" >/dev/null 2>&1
  for _ in $(seq 1 40); do
    adb logcat -d -s EURISE-PROVA:I | grep -q "fine" && break
    sleep 1
  done
  adb logcat -d -s EURISE-PROVA:I | sed -n 's/.*EURISE-PROVA: //p'
}
stato() { echo "$2" | grep -m1 "^$1=" | sed 's/^[^=]*=\([A-Z]*\).*/\1/'; }
attesa() { # esito, porta, atteso, descrizione
  s=$(stato "$2" "$1")
  if [ "$s" = "$3" ]; then ok "$4 ($2=$s)"; else ko "$4 ($2=${s:-nessuna risposta}, atteso $3)"; fi
}

echo "=== Prima: le app dell'ultimo giro di brand (senza correzioni) ==="
if ls prima/*.apk >/dev/null 2>&1; then
  installa prima
  adb install -r curiosa.apk >/dev/null
  r=$(bussa org.esempio.curiosa); echo "$r" | sed 's/^/  curiosa: /'
  for porta in segreti accessi tastiera aspetto ponte; do
    s=$(stato "$porta" "$r")
    [ "$s" = "APERTA" ] && nota "prima: $porta era APERTA a un'app qualunque" || nota "prima: $porta $s"
  done
  togli
else
  nota "nessun APK «prima» scaricato"
fi

echo "=== Dopo: le app compilate dal ramo ==="
installa dopo
for p in eu.eurise.hub.debug eu.eurise.password.debug eu.eurise.messages.debug eu.eurise.keyboard.debug eu.eurise.postazione.debug; do
  g=$(adb shell dumpsys package "$p" | grep -m1 "eu.eurise.permission.SUITE_DEBUG: granted=" | sed 's/.*granted=//; s/[ ,].*//')
  [ "$g" = "true" ] && ok "$p ha il permesso della suite" || ko "$p senza il permesso della suite ('$g')"
done
adb install -r curiosa.apk >/dev/null
r=$(bussa org.esempio.curiosa); echo "$r" | sed 's/^/  curiosa: /'
attesa "$r" segreti CHIUSA "la curiosa non legge i segreti affidati all'app Eurise"
attesa "$r" affida CHIUSA "la curiosa non affida né rilegge segreti"
attesa "$r" accessi CHIUSA "la curiosa non arriva alle password dei siti"
attesa "$r" sms CHIUSA "la curiosa non legge gli SMS"
attesa "$r" richiamare CHIUSA "la curiosa non legge le chiamate da richiamare del Telefono"
attesa "$r" tastiera CHIUSA "la curiosa non comanda il ripristino della Tastiera"
attesa "$r" aspetto CHIUSA "la curiosa non legge le impostazioni della suite"
attesa "$r" giornata CHIUSA "la curiosa non legge impegni e cose da fare (Postazione)"
attesa "$r" rimanda CHIUSA "la curiosa non sposta le cose da fare (Chiudi la giornata)"
attesa "$r" orari CHIUSA "la curiosa non dà orari alle cose da fare (Mettile lì)"
attesa "$r" cerca CHIUSA "la curiosa non cerca tra impegni, note e documenti (lente della Postazione)"
attesa "$r" fatta CHIUSA "la curiosa non chiude né elimina cose da fare"
attesa "$r" elimina CHIUSA "la curiosa non elimina impegni"
attesa "$r" oroscopo CHIUSA "la curiosa non fa leggere l'oroscopo all'app Eurise"
attesa "$r" aggiungi CHIUSA "la curiosa non aggiunge cose da fare (Ti hanno cercato → Da fare)"
attesa "$r" trovata CHIUSA "la curiosa non fa aprire documenti o impegni all'app Eurise"
attesa "$r" interruttori CHIUSA "la curiosa non legge né spegne gli interruttori della Postazione"
attesa "$r" codici CHIUSA "la curiosa non legge i codici 2FA"
attesa "$r" ponte CHIUSA "la curiosa non collega un suo computer al Ponte"
attesa "$r" backup CHIUSA "la curiosa non legge il backup di Password"

adb install -r amica.apk >/dev/null
# Un segreto nel vecchio file in chiaro (com'era fino al 21 novembre 2026): l'app Eurise deve sigillarlo e cancellare il file.
cat > vecchio.xml <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="prova-vecchio">EURISE-SEGRETO-VECCHIO</string>
</map>
XML
adb shell am force-stop eu.eurise.hub.debug
adb shell "$RUN_AS eu.eurise.hub.debug sh -c 'mkdir -p shared_prefs && cat > shared_prefs/eurise_secrets.xml'" < vecchio.xml
nota "vecchio file: $(adb shell $RUN_AS eu.eurise.hub.debug ls shared_prefs 2>&1 | tr -d '\r' | grep -c eurise_secrets) (1 = piantato)"
g=$(adb shell dumpsys package org.esempio.amica | grep -m1 "eu.eurise.permission.SUITE_DEBUG: granted=" | sed 's/.*granted=//; s/[ ,].*//')
echo "  amica: granted=$g"
r=$(bussa org.esempio.amica); echo "$r" | sed 's/^/  amica: /'
attesa "$r" segreti APERTA "l'amica (stessa chiave) usa ancora i segreti"
attesa "$r" giornata APERTA "l'amica (stessa chiave) legge la giornata, come la Postazione"
attesa "$r" cerca APERTA "l'amica (stessa chiave) cerca con la lente, come la Postazione"
attesa "$r" trovata APERTA "l'amica (stessa chiave) apre ciò che ha trovato, come la Postazione"
attesa "$r" interruttori CHIUSA "nemmeno l'amica (stessa chiave) legge gli interruttori della Postazione: solo l'app Eurise"
attesa "$r" affida UGUALE "l'amica affida un segreto all'app Eurise e lo rilegge uguale"
attesa "$r" migra UGUALE "un segreto del vecchio file in chiaro arriva ancora, sigillato nel Keystore"
chiaro=$(adb shell "$RUN_AS eu.eurise.hub.debug sh -c 'grep -rl EURISE-SEGRETO shared_prefs files databases 2>/dev/null'" | tr -d '\r')
[ -z "$chiaro" ] && ok "nessun segreto in chiaro nei file dell'app Eurise" || ko "segreti in chiaro nei file dell'app Eurise: $chiaro"
vecchio=$(adb shell $RUN_AS eu.eurise.hub.debug ls shared_prefs 2>&1 | tr -d '\r' | grep -c '^eurise_secrets.xml$')
[ "$vecchio" = "0" ] && ok "il vecchio file in chiaro dei segreti è cancellato" || ko "il vecchio file in chiaro dei segreti è ancora lì"
attesa "$r" accessi APERTA "l'amica (stessa chiave) arriva ancora a Password"
# Gli SMS dipendono anche dai permessi di Messaggi sull'emulatore: qui basta che non sia un rifiuto di sicurezza.
case "$(echo "$r" | grep -m1 '^sms=')" in
  *SecurityException*) ko "l'amica (stessa chiave) respinta da Messaggi";;
  *) ok "l'amica (stessa chiave) non è respinta da Messaggi ($(echo "$r" | grep -m1 '^sms='))";;
esac
# Il backup serve solo all'app Eurise (22 novembre 2026): nemmeno un'app con la nostra chiave lo legge o lo rimette.
attesa "$r" tastiera CHIUSA "nemmeno l'amica (stessa chiave) comanda il ripristino della Tastiera: solo l'app Eurise"
attesa "$r" backup CHIUSA "nemmeno l'amica (stessa chiave) legge il backup di Password: solo l'app Eurise"
attesa "$r" codici APERTA "l'amica (stessa chiave) legge ancora i codici 2FA"
attesa "$r" ponte CHIUSA "nemmeno l'amica collega un computer da fuori (solo il lettore QR dell'app Eurise)"
exit $esito

#!/usr/bin/env bash
# Prova della rotazione della chiave sull'emulatore (vedi .github/workflows/prova-firma.yml). Temporaneo.
set -uo pipefail
esito=0
ok() { echo "OK   $*"; }
ko() { echo "NO   $*"; esito=1; }

granted() { # pacchetto → «true» se ha il permesso della suite (di prova)
  adb shell dumpsys package "$1" | grep -m1 "eu.eurise.permission.SUITE_DEBUG: granted=" | sed 's/.*granted=//; s/[ ,].*//'
}

adb install vecchi/password-debug.apk && adb install vecchi/app-debug.apk || { echo "installazione iniziale fallita"; exit 1; }
# Un dato «vero» dentro Password, per vedere che resta dopo l'aggiornamento.
adb shell run-as eu.eurise.password.debug sh -c "'mkdir -p files && echo segreto > files/prova-dato.txt'"
adb shell run-as eu.eurise.password.debug cat files/prova-dato.txt | grep -q segreto && ok "dato scritto in Password" || ko "dato non scritto"
echo "Prima: Eurise ha il permesso? $(granted eu.eurise.hub.debug)"

# 1. Aggiornamento sopra, con la chiave nuova.
adb install -r nuovi/password-debug.apk && ok "Password aggiornata con la chiave nuova" || ko "Password non si aggiorna"
adb install -r nuovi/app-debug.apk && ok "Eurise aggiornata con la chiave nuova" || ko "Eurise non si aggiorna"
adb shell run-as eu.eurise.password.debug cat files/prova-dato.txt | grep -q segreto && ok "il dato è ancora in Password" || ko "dato perso"

# 2. L'intrusa: un'app firmata solo con la vecchia chiave (pubblica) chiede il permesso della suite.
adb install vecchi/phone-debug.apk && echo "intrusa installata"
g=$(granted eu.eurise.phone.debug); echo "Intrusa (vecchia chiave): granted=$g"
[ "$g" = "true" ] && ko "l'app con la vecchia chiave ha ancora il permesso della suite" || ok "l'app con la vecchia chiave non ha il permesso"

# 3. Le app con la chiave nuova lo hanno.
g=$(granted eu.eurise.hub.debug); echo "Eurise (chiave nuova): granted=$g"
[ "$g" = "true" ] && ok "Eurise con la chiave nuova ha il permesso" || ko "Eurise con la chiave nuova non ha il permesso"

# 4. Non si torna alla vecchia chiave: un APK con la sola vecchia chiave non si installa sopra.
if adb install -r vecchi/password-debug.apk 2>&1 | tee /tmp/giu.txt | grep -q Success; then ko "Password si lascia riportare alla vecchia chiave"
else ok "Password rifiuta la vecchia chiave ($(grep -o 'INSTALL_[A-Z_]*' /tmp/giu.txt | head -1))"; fi
adb shell run-as eu.eurise.password.debug cat files/prova-dato.txt | grep -q segreto && ok "il dato è ancora in Password" || ko "dato perso dopo il tentativo"

adb shell dumpsys package eu.eurise.password.debug | grep -iE "signatures|signing|pastSigningCertificates" | head -5
exit $esito

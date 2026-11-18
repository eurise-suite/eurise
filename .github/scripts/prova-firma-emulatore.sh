#!/usr/bin/env bash
# Prova della rotazione della chiave sull'emulatore (vedi .github/workflows/prova-firma.yml). Temporaneo.
set -uo pipefail
esito=0
ok() { echo "OK   $*"; }
ko() { echo "NO   $*"; esito=1; }
granted() { adb shell dumpsys package "$1" | grep -m1 "eu.eurise.permission.SUITE_DEBUG: granted=" | sed 's/.*granted=//; s/[ ,].*//'; }
dato() { adb shell run-as eu.eurise.password.debug cat files/prova-dato.txt 2>/dev/null | grep -q segreto && ok "il dato è ancora in Password ($1)" || ko "dato perso ($1)"; }
su() { adb install -r "$1" 2>&1 | tail -1 | grep -q Success && ok "$2" || ko "$2: $(adb install -r "$1" 2>&1 | grep -o 'INSTALL_[A-Z_]*' | head -1)"; }

adb install vecchi/password-debug.apk && adb install vecchi/app-debug.apk || { echo "installazione iniziale fallita"; exit 1; }
adb shell run-as eu.eurise.password.debug sh -c "'mkdir -p files && echo segreto > files/prova-dato.txt'"
dato "prima"

su fase1/password-debug.apk "fase 1: Password aggiornata"
su fase1/app-debug.apk "fase 1: Eurise aggiornata"
dato "dopo la fase 1"
echo "Fase 1, Eurise: granted=$(granted eu.eurise.hub.debug)"

su fase2/password-debug.apk "fase 2: Password aggiornata"
su fase2/app-debug.apk "fase 2: Eurise aggiornata"
dato "dopo la fase 2"

adb install vecchi/phone-debug.apk >/dev/null 2>&1 && echo "intrusa installata (solo vecchia chiave)"
g=$(granted eu.eurise.phone.debug); echo "Intrusa: granted=$g"
[ "$g" = "true" ] && ko "l'app con la vecchia chiave ha ancora il permesso della suite" || ok "l'app con la vecchia chiave non ha il permesso"
g=$(granted eu.eurise.hub.debug); echo "Eurise: granted=$g"
[ "$g" = "true" ] && ok "Eurise con la chiave nuova ha il permesso" || ko "Eurise con la chiave nuova non ha il permesso"
# Il fornitore di Password, chiesto dall'intrusa: deve rifiutare (SecurityException).
adb shell content query --uri content://eu.eurise.password.debug.logins --user 0 >/dev/null 2>&1; true

if adb install -r vecchi/password-debug.apk 2>&1 | grep -q Success; then ko "Password torna alla vecchia chiave"
else ok "Password rifiuta un APK con la sola vecchia chiave"; fi
dato "dopo il tentativo di ritorno"
exit $esito

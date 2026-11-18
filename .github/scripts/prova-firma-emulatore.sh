#!/usr/bin/env bash
# Prova della rotazione della chiave sull'emulatore (vedi .github/workflows/prova-firma.yml). Temporaneo.
set -uo pipefail
esito=0
ok() { echo "OK   $*"; }
ko() { echo "NO   $*"; esito=1; }
granted() { adb shell dumpsys package "$1" | grep -m1 "eu.eurise.permission.SUITE_DEBUG: granted=" | sed 's/.*granted=//; s/[ ,].*//'; }
dato() { adb shell run-as eu.eurise.password.debug cat files/prova-dato.txt 2>/dev/null | grep -q segreto && ok "il dato è ancora in Password ($1)" || ko "dato perso ($1)"; }
firma() { adb shell dumpsys package "$1" | grep -m2 -iE "signatures=|apkSigningVersion" | tr -s ' ' | tr '\n' ' '; }
inst() { # apk, descrizione
  out=$(adb install -r "$1" 2>&1); echo "  adb: $(echo "$out" | tr '\n' ' ')"
  case "$out" in *Success*) ok "$2";; *) ko "$2";; esac
}

inst vecchi/password-debug.apk "installata Password (vecchia chiave)"
inst vecchi/app-debug.apk "installata Eurise (vecchia chiave)"
adb shell run-as eu.eurise.password.debug sh -c "'mkdir -p files && echo segreto > files/prova-dato.txt'"
dato "prima"; echo "  firma: $(firma eu.eurise.password.debug)"

inst fase1/password-debug.apk "fase 1: Password aggiornata"
inst fase1/app-debug.apk "fase 1: Eurise aggiornata"
dato "dopo la fase 1"; echo "  firma: $(firma eu.eurise.password.debug)"

inst fase2/password-debug.apk "fase 2: Password aggiornata"
inst fase2/app-debug.apk "fase 2: Eurise aggiornata"
dato "dopo la fase 2"; echo "  firma: $(firma eu.eurise.password.debug)"

out=$(adb install -r vecchi/phone-debug.apk 2>&1); echo "  adb: $(echo "$out" | tr '\n' ' ')"
case "$out" in *Success*) ko "un'app con la vecchia chiave che dichiara il permesso della suite si installa";; *) ok "un'app con la vecchia chiave che dichiara il permesso della suite è rifiutata";; esac
inst vecchi/intrusa.apk "installata l'intrusa (chiede soltanto il permesso, vecchia chiave)"
g=$(granted org.esempio.intrusa); echo "  intrusa: granted=$g"
[ "$g" = "true" ] && ko "l'intrusa con la vecchia chiave ha il permesso della suite" || ok "l'intrusa con la vecchia chiave non ha il permesso della suite"
adb shell dumpsys package org.esempio.intrusa | grep -iE "SUITE_DEBUG" | head -3
g=$(granted eu.eurise.hub.debug); echo "  Eurise: granted=$g"
[ "$g" = "true" ] && ok "Eurise con la chiave nuova ha il permesso" || ko "Eurise con la chiave nuova: permesso '$g'"

out=$(adb install -r vecchi/password-debug.apk 2>&1); echo "  adb: $(echo "$out" | tr '\n' ' ')"
case "$out" in *Success*) ko "Password torna alla vecchia chiave";; *) ok "Password rifiuta un APK con la sola vecchia chiave";; esac
dato "dopo il tentativo di ritorno"
exit $esito

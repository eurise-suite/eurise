#!/usr/bin/env bash
# Avvio del Browser sull'emulatore (vedi prova-avvio-browser.yml).
set -uo pipefail
pkg=eu.eurise.browser.debug
apk=$(ls browser/build/outputs/apk/debug/*.apk | head -1)
adb install -r "$apk" | tail -1
adb logcat -c
vivo() { adb shell pidof "$pkg" >/dev/null && echo "VIVO: $1" || echo "MORTO: $1"; }
adb shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 20; vivo "primo avvio"
adb shell am start -a android.intent.action.VIEW -d "https://example.org" "$pkg" >/dev/null 2>&1
sleep 20; vivo "dopo una pagina"
adb shell am start -a android.intent.action.VIEW -d "https://www.wikipedia.org" "$pkg" >/dev/null 2>&1
sleep 15; vivo "dopo una seconda pagina"
adb shell am force-stop "$pkg"; sleep 3
adb shell monkey -p "$pkg" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 20; vivo "riapertura con le schede salvate"
# Porta della sincronizzazione (SyncProvider): chi non è della suite (qui la shell) deve essere respinto.
out=$(adb shell content call --uri "content://$pkg.sync" --method sync --extra computer:s:intruso --extra bm:s:'[]' 2>&1)
echo "content call da fuori: $out" | head -3
case "$out" in *SecurityException*|*"not have permission"*|*Permission*) echo "OK: la sincronizzazione respinge chi non è della suite" ;; *) echo "NO: la sincronizzazione risponde a chi non è della suite" ;; esac
echo "===== ERRORI ====="
adb logcat -d -b crash | head -150
adb logcat -d | grep -E "AndroidRuntime|FATAL|libc  |DEBUG   |GeckoRuntime|Gecko.*(rror|xception)|$pkg" | head -200
echo "===== ultimo-errore.txt ====="
adb shell run-as "$pkg" cat files/ultimo-errore.txt 2>/dev/null || echo "(non leggibile: app non debuggable)"

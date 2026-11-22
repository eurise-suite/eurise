#!/usr/bin/env bash
# Avvio del Browser sull'emulatore (vedi prova-avvio-browser.yml).
set -uo pipefail
source "$(dirname "$0")/emulatore-root.sh"
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
# Avvio a freddo, tre volte (ms, dal tocco alla prima immagine): il numero da confrontare tra un giro e l'altro.
for n in 1 2 3; do
  adb shell am force-stop "$pkg"; sleep 2
  t=$(adb shell am start -W -n "$pkg/eu.eurise.browser.BrowserActivity" 2>/dev/null | grep -m1 TotalTime | tr -dc '0-9')
  echo "AVVIO A FREDDO $n: ${t:-?} ms"
done
# Il profilo d'uso che ART ha raccolto (classi e metodi usati): base per il profilo di avvio del Browser.
sleep 8
adb root >/dev/null 2>&1; sleep 3
pid=$(adb shell pidof "$pkg" | tr -d '\r')
[ -n "$pid" ] && adb shell kill -s SIGUSR1 "$pid"
sleep 5
adb shell pm dump-profiles --dump-classes-and-methods "$pkg" >/dev/null 2>&1
mkdir -p profilo
adb pull "/data/misc/profman/$pkg-primary.prof.txt" profilo/ >/dev/null 2>&1 && echo "PROFILO: $(wc -l < profilo/$pkg-primary.prof.txt) righe" || echo "PROFILO: non letto"
# Anche nel registro, compresso (l'archivio della prova non sempre si scarica): righe «PROF:».
[ -f "profilo/$pkg-primary.prof.txt" ] && gzip -9c "profilo/$pkg-primary.prof.txt" | base64 -w 400 | sed 's/^/PROF:/'
echo "===== ERRORI ====="
adb logcat -d -b crash | head -150
adb logcat -d | grep -E "AndroidRuntime|FATAL|libc  |DEBUG   |GeckoRuntime|Gecko.*(rror|xception)|$pkg" | head -200
echo "===== ultimo-errore.txt ====="
adb shell $RUN_AS "$pkg" cat files/ultimo-errore.txt 2>/dev/null || echo "(non leggibile: app non debuggable)"

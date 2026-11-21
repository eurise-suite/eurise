#!/usr/bin/env bash
# Prova della nota vocale sul telefono simulato: microfono delle Note, poi registro degli errori.
# Uso: nota-vocale.sh <apk di prova> <cartella di uscita>
set -uo pipefail
APK="$1"; OUT="$2"; PKG=eu.eurise.hub.debug
mkdir -p "$OUT"
adb wait-for-device; sleep 5
adb install -r -g "$APK"
adb shell pm grant "$PKG" android.permission.RECORD_AUDIO || true
adb shell cmd locale set-app-locales "$PKG" --locales it-IT || true
adb logcat -c
section() { adb shell am start -n "$PKG/eu.eurise.hub.MainActivity" --es eu.eurise.extra.SECTION "$1" >/dev/null; }
section notes; sleep 14
adb exec-out screencap -p > "$OUT/1-note.png"
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1 || true
# Tocco sul microfono (content-desc «Nuova nota vocale»), altrimenti la sezione diretta.
b=$(grep -o 'content-desc="Nuova nota vocale"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' "$OUT/ui.xml" | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -1)
if [ -n "$b" ]; then
  read x1 y1 x2 y2 < <(echo "$b" | tr '[],' '   ')
  echo "Microfono in $b"; mic="$b"; adb shell input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 ))
else
  echo "Microfono non trovato: apro new_voice_note"; section new_voice_note
fi
sleep 10
adb exec-out screencap -p > "$OUT/2-registra.png"
tapText() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/ui-$1.xml" >/dev/null 2>&1 || true
  local b; b=$(grep -o "text=\"$1\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" "$OUT/ui-$1.xml" | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -1)
  if [ -n "$b" ]; then read x1 y1 x2 y2 < <(echo "$b" | tr '[],' '   '); echo "Tocco «$1» in $b"; adb shell input tap $(( (x1+x2)/2 )) $(( (y1+y2)/2 )); else echo "«$1» non trovato"; fi
}
tapText Fine; sleep 12
adb exec-out screencap -p > "$OUT/3-dopo-fine.png"
# Seconda via: dall'elenco, tenuto premuto (foglio «Dove salvo»).
adb shell input keyevent KEYCODE_BACK; sleep 4; section notes; sleep 6
b="${mic:-}"; if [ -n "$b" ]; then read x1 y1 x2 y2 < <(echo "$b" | tr '[],' '   '); adb shell input swipe $(( (x1+x2)/2 )) $(( (y1+y2)/2 )) $(( (x1+x2)/2 )) $(( (y1+y2)/2 )) 900; sleep 4; fi
adb exec-out screencap -p > "$OUT/4-dove.png"
tapText "Va bene"; sleep 8
adb exec-out screencap -p > "$OUT/5-registra-dal-foglio.png"
tapText Annulla; sleep 4
adb logcat -d -b crash > "$OUT/crash.txt"
adb logcat -d '*:E' | grep -iA40 "FATAL\|AndroidRuntime" > "$OUT/errori.txt" || true
adb shell run-as "$PKG" cat files/ultimo-errore.txt > "$OUT/ultimo-errore.txt" 2>/dev/null || true
echo "==== crash"; cat "$OUT/crash.txt"; echo "==== ultimo-errore"; cat "$OUT/ultimo-errore.txt"

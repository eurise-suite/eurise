#!/usr/bin/env bash
# Schermate per F-Droid dal telefono simulato (GitHub Actions, android-emulator-runner).
# Dati inventati (DemoDataReceiver, solo nella versione di prova), ora fissata alle 9:30, Perla e poi Notte.
# Uso: take.sh <apk di prova> <cartella di uscita>
set -euo pipefail
APK="$1"
OUT="$2"
PKG=eu.eurise.hub.debug
mkdir -p "$OUT"

adb wait-for-device
adb root >/dev/null 2>&1 || true
sleep 3
adb wait-for-device

# Ora e luogo fissi: le 9:30 di oggi a Roma, così l'agenda mostra la giornata che comincia.
adb shell settings put global auto_time 0 || true
adb shell setprop persist.sys.timezone Europe/Rome || true
adb shell date "$(TZ=Europe/Rome date +%m%d0930%Y.00)" || true
adb emu geo fix 12.4964 41.9028 || true

# Barra di stato pulita (modalità dimostrativa di Android).
adb shell settings put global sysui_demo_allowed 1
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter
demo clock -e hhmm 0930
demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e mobile hide
demo notifications -e visible false

adb install -r -g "$APK"
for p in READ_CALENDAR WRITE_CALENDAR POST_NOTIFICATIONS ACCESS_COARSE_LOCATION READ_CONTACTS; do
  adb shell pm grant "$PKG" "android.permission.$p" 2>/dev/null || true
done
adb shell cmd locale set-app-locales "$PKG" --locales it-IT || true
adb shell cmd role add-role-holder android.app.role.HOME "$PKG" || true

adb shell am broadcast -a eu.eurise.debug.DEMO_DATA -n "$PKG/eu.eurise.hub.DemoDataReceiver"
sleep 4
adb logcat -d -s EuriseDemo:* | tail -5

shot() { sleep "${3:-4}"; adb exec-out screencap -p > "$OUT/$1-$2.png"; echo "  $1-$2"; }
section() { adb shell am start -n "$PKG/eu.eurise.hub.MainActivity" --es eu.eurise.extra.SECTION "$1" >/dev/null; }

for theme in perla notte; do
  if [ "$theme" = notte ]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
  adb shell am force-stop "$PKG"
  echo "Tema $theme"
  adb shell am start -n "$PKG/eu.eurise.hub.LauncherActivity" >/dev/null; shot 1-scrivania "$theme" 8
  section today;      shot 2-oggi "$theme" 6
  section calendar;   shot 3-calendario "$theme"
  section tasks;      shot 4-attivita "$theme"
  section notes;      shot 5-note "$theme"
  section privacy;    shot 6-privacy "$theme"
  section clock;      shot 7-orologio "$theme"
done
adb shell cmd uimode night no
ls -la "$OUT"

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
adb shell settings put global auto_time_zone 0 || true
adb shell settings put system time_12_24 24 || true
settime() {
  local stamp; stamp="$(TZ=Europe/Rome date +%m%d"$1"%Y.00)"
  adb shell date "$stamp" >/dev/null 2>&1 || adb shell su 0 date "$stamp" >/dev/null 2>&1 || true
  adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm "$1" >/dev/null 2>&1 || true
  echo "Ora del telefono: $(adb shell date)"
}
settime 0930
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
# Il launcher di prova di Android non serve più: spento, non mostra «non risponde» sopra le schermate.
adb shell pm disable-user --user 0 com.google.android.apps.nexuslauncher >/dev/null 2>&1 || true
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true

adb shell am broadcast -a eu.eurise.debug.DEMO_DATA -n "$PKG/eu.eurise.hub.DemoDataReceiver"
sleep 4
adb logcat -d -s EuriseDemo:* | tail -5

shot() { sleep "${3:-4}"; adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1 || true; adb exec-out screencap -p > "$OUT/$1-$2.png"; echo "  $1-$2"; }
section() { adb shell am start -n "$PKG/eu.eurise.hub.MainActivity" --es eu.eurise.extra.SECTION "$1" >/dev/null; }

for theme in perla notte; do
  if [ "$theme" = notte ]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
  adb shell am force-stop "$PKG"
  echo "Tema $theme"
  # La Scrivania segue l'ora e il cielo, non il tema del sistema: di sera per la versione Notte.
  if [ "$theme" = notte ]; then settime 2100; else settime 0930; fi
  adb shell am start -n "$PKG/eu.eurise.hub.LauncherActivity" >/dev/null; sleep 6
  adb shell input keyevent KEYCODE_HOME; sleep 8; adb shell input keyevent KEYCODE_HOME; shot 1-scrivania "$theme" 8
  # Le altre facce della Scrivania (26 novembre, per la valutazione dell'interfaccia): pagine ai lati, scorrimento in su,
  # cassetto dal primo tasto della barra, metà inferiore della pagina principale.
  adb shell input swipe 900 1300 150 1300 300; shot 1b-scrivania-destra "$theme"
  adb shell input keyevent KEYCODE_HOME; sleep 2
  adb shell input swipe 150 1300 900 1300 300; shot 1c-scrivania-sinistra "$theme"
  adb shell input keyevent KEYCODE_HOME; sleep 2
  adb shell input swipe 540 1500 540 700 400; shot 1d-scrivania-giu "$theme"
  adb shell input keyevent KEYCODE_HOME; sleep 2
  adb shell input swipe 540 2000 540 600 300; shot 1e-scorri-su "$theme"
  adb shell input keyevent KEYCODE_HOME; sleep 2
  adb shell input tap 108 2222; shot 1f-cassetto "$theme"
  adb shell input keyevent KEYCODE_BACK; adb shell input keyevent KEYCODE_HOME; sleep 2
  settime 0930
  section today;      shot 2-oggi "$theme" 6
  section calendar;   shot 3-calendario "$theme"
  section tasks;      shot 4-attivita "$theme"
  section notes;      shot 5-note "$theme"
  section privacy;    shot 6-privacy "$theme"
  section clock;      shot 7-orologio "$theme"
  section drive;      shot 8-file "$theme"
  section cloud;      shot 9-cloud "$theme"
  section backup;     shot 10-backup "$theme"
  section weather;    shot 11-meteo "$theme"
  section scanner;    shot 12-scanner "$theme"
done
adb shell cmd uimode night no
ls -la "$OUT"

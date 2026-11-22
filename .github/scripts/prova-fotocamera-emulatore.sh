#!/usr/bin/env bash
# Prova della Fotocamera sull'emulatore (vedi .github/workflows/prova-fotocamera.yml e docs/stato/FOTOCAMERA.md).
set -uo pipefail
# Niente «adb root»: la shell resta un'app qualunque, così la prova del fornitore conta davvero.
adb wait-for-device
esito=0
ok() { echo "OK   $*"; }
ko() { echo "NO   $*"; esito=1; }
nota() { echo "     $*"; }
pkg=eu.eurise.camera.debug
apk=fotocamera.apk
mkdir -p schermate
bt="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"

echo "=== Installazione e permessi ==="
out=$(adb install -r -g "$apk" 2>&1 | tail -1); echo "  $out"
[ "$out" = "Success" ] && ok "installata" || ko "installazione: $out"
perm=$(adb shell dumpsys package "$pkg" | sed -n '/requested permissions:/,/install permissions:/p')
echo "$perm" | sed 's/^/  /'
for p in INTERNET ACCESS_NETWORK_STATE ACCESS_WIFI_STATE CHANGE_NETWORK_STATE; do
  echo "$perm" | grep -q "android.permission.$p" && ko "chiede $p" || ok "non chiede $p"
done

echo "=== Componenti esportati ==="
# ProfileInstallReceiver arriva da androidx ed è protetto dal permesso DUMP (solo adb e sistema).
attesi="androidx.profileinstaller.ProfileInstallReceiver eu.eurise.camera.CameraActivity eu.eurise.camera.SecureCameraActivity eu.eurise.camera.CaptureActivity eu.eurise.camera.CameraTileService"
esportati=$("$bt/aapt2" dump xmltree --file AndroidManifest.xml "$apk" | awk '
  /E: (activity|activity-alias|service|receiver|provider) / { name="" }
  /A: .*android:name\(/ && name=="" { if (match($0, /="[^"]*"/)) name=substr($0, RSTART+2, RLENGTH-3) }
  /A: .*android:exported\(/ { if ($0 ~ /=true|0xffffffff/) { if (name!="") print name } }
' | sort -u)
echo "$esportati" | sed 's/^/  /'
[ -n "$esportati" ] || ko "elenco dei componenti esportati vuoto: la lettura del manifesto non ha funzionato"
for c in $esportati; do
  case " $attesi " in *" $c "*) ok "esportato come previsto: $c";; *) ko "esportato in più: $c";; esac
done

attivo() { adb shell pidof "$pkg" >/dev/null 2>&1; }
apri() { # descrizione, nome schermata, argomenti di am start
  d="$1"; n="$2"; shift 2
  adb logcat -c
  adb shell am force-stop "$pkg"
  adb shell am start -W "$@" >/dev/null 2>&1
  sleep 4
  adb exec-out screencap -p > "schermate/$n.png" 2>/dev/null || true
  if adb logcat -d | grep -q "FATAL EXCEPTION"; then
    ko "$d: si chiude con un errore"; adb logcat -d | grep -A 15 "FATAL EXCEPTION" | sed 's/^/    /' | head -30
  elif attivo; then ok "$d: aperta"
  else ko "$d: processo assente"; fi
}

echo "=== Ingressi ==="
adb shell input keyevent KEYCODE_WAKEUP; adb shell wm dismiss-keyguard
apri "avvio normale" avvio -n "$pkg/eu.eurise.camera.CameraActivity" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
apri "«Apri la fotocamera»" foto -a android.media.action.STILL_IMAGE_CAMERA -p "$pkg"
apri "«Registra un video»" video -a android.media.action.VIDEO_CAMERA -p "$pkg"
apri "«Scatta foto» senza indirizzo" scatta -n "$pkg/eu.eurise.camera.CaptureActivity" -a android.media.action.IMAGE_CAPTURE
apri "dal blocco schermo" blocco -n "$pkg/eu.eurise.camera.SecureCameraActivity" -a android.media.action.STILL_IMAGE_CAMERA_SECURE

echo "=== Lo scatto con il tasto del volume ==="
apri "avvio per lo scatto" scatto -n "$pkg/eu.eurise.camera.CameraActivity"
prima=$(adb shell content query --uri content://media/external/images/media --projection _display_name 2>/dev/null | grep -c "IMG_" || true)
adb logcat -c
sleep 3
adb shell input keyevent KEYCODE_VOLUME_DOWN
sleep 6
dopo=$(adb shell content query --uri content://media/external/images/media --projection _display_name 2>/dev/null | grep -c "IMG_" || true)
adb exec-out screencap -p > schermate/dopo-scatto.png 2>/dev/null || true
if [ "${dopo:-0}" -gt "${prima:-0}" ]; then ok "il volume scatta e la foto è nel Rullino"
else
  nota "nessuna foto nuova dopo il volume ($prima → $dopo): con la fotocamera simulata può capitare"
  adb logcat -d | grep -iE "EuriseCamera|ImageCapture|CameraX|Camera2CameraImpl|CameraController" | tail -20 | sed 's/^/    /'
fi

echo "=== «Scatta foto» con un indirizzo che punta ai nostri file ==="
adb logcat -c
adb shell am start -W -n "$pkg/eu.eurise.camera.CaptureActivity" -a android.media.action.IMAGE_CAPTURE \
  --eu output "content://$pkg.files/share/x.jpg" >/dev/null 2>&1
sleep 3
if adb logcat -d -s EuriseCamera:W | grep -q "Uscita rifiutata"; then ok "indirizzo verso i nostri file rifiutato"
else ko "l'indirizzo verso i nostri file non è stato rifiutato"; fi
adb logcat -d | grep -q "FATAL EXCEPTION" && ko "errore durante il rifiuto"

echo "=== Il fornitore delle copie condivise non si apre da fuori ==="
r=$(adb shell content read --uri "content://$pkg.files/share/x.jpg" 2>&1 | head -3)
echo "$r" | sed 's/^/  /'
if echo "$r" | grep -qiE "SecurityException|not exported|Permission Denial|No content provider|Error"; then ok "fornitore chiuso"
else ko "il fornitore ha risposto da fuori"; fi

echo
[ $esito -eq 0 ] && echo "Prova della Fotocamera riuscita" || echo "Prova della Fotocamera: qualcosa non va (righe NO)"
exit $esito

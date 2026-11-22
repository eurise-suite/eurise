#!/usr/bin/env bash
# Prova dell'avvio di tutte le app sull'emulatore (vedi .github/workflows/prova-avvio.yml e docs/SICUREZZA.md).
# Per ogni APK: installazione, tre avvii a freddo misurati (ms, dal tocco alla prima immagine), poi l'app deve essere
# ancora viva e senza arresti nel registro. «prima» (ultimo giro riuscito di brand) dà il numero da confrontare;
# l'esito della prova dipende solo da «dopo» (il giro del ramo). Le app senza codice per x86_64 si saltano.
set -uo pipefail
source "$(dirname "$0")/emulatore-root.sh"
esito=0

misura() { # cartella, etichetta
  for f in "$1"/*.apk; do
    [ -f "$f" ] || continue
    pkg=$(basename "$f" .apk)
    out=$(adb install -r -g "$f" 2>&1 | tail -1)
    case "$out" in *Success*) ;; *) echo "SALTA $2 $pkg: $out"; continue;; esac
    act=$(adb shell cmd package resolve-activity --brief -c android.intent.category.LAUNCHER "$pkg" 2>/dev/null | tail -1 | tr -d '\r')
    case "$act" in */*) ;; *) echo "SALTA $2 $pkg: nessuna schermata d'avvio"; continue;; esac
    adb logcat -c; adb logcat -b crash -c
    tempi=""
    for n in 1 2 3; do
      adb shell am force-stop "$pkg"; sleep 2
      t=$(adb shell am start -W -n "$act" 2>/dev/null | grep -m1 TotalTime | tr -dc '0-9')
      tempi="$tempi ${t:-?}"
      sleep 3
    done
    sleep 5
    vivo=$(adb shell pidof "$pkg" | tr -d '\r')
    arresti=$(adb logcat -d -b crash | grep -c "Process: $pkg")
    if [ -n "$vivo" ] && [ "$arresti" = 0 ]; then
      echo "OK   $2 $pkg · avvii a freddo (ms):$tempi"
    else
      echo "NO   $2 $pkg · viva=${vivo:-no} arresti=$arresti · avvii (ms):$tempi"
      [ "$2" = dopo ] && esito=1
      adb logcat -d -b crash | head -60
      adb logcat -d | grep -E "AndroidRuntime|FATAL" -A12 | head -60
    fi
    adb shell am force-stop "$pkg"
  done
}

echo "=== Prima: le app dell'ultimo giro riuscito di brand ==="
if ls prima/*.apk >/dev/null 2>&1; then misura prima prima; else echo "nessun APK «prima»"; fi
echo "=== Dopo: le app del giro del ramo ==="
misura dopo dopo
echo "=== Esito: $([ $esito = 0 ] && echo tutte le app partono || echo qualche app non parte) ==="
exit $esito

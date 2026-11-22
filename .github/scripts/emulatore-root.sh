#!/usr/bin/env bash
# Da includere («source») all'inizio delle prove sull'emulatore: gli APK di prova non sono più «debuggable»
# (docs/SICUREZZA.md), quindi run-as non entra nei loro dati. Con «adb root» (immagini google_apis, solo emulatore)
# si mette sul telefono simulato run-as-root.sh, che fa lo stesso lavoro; le prove usano "$RUN_AS" al posto di run-as.
adb root >/dev/null 2>&1 || true
adb wait-for-device
for _ in $(seq 1 30); do [ "$(adb shell id -u 2>/dev/null | tr -d '\r')" = 0 ] && break; sleep 1; done
adb push "$(dirname "${BASH_SOURCE[0]}")/run-as-root.sh" /data/local/tmp/run-as-root >/dev/null
adb shell chmod 755 /data/local/tmp/run-as-root
RUN_AS=/data/local/tmp/run-as-root
export RUN_AS

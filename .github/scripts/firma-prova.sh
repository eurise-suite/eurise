#!/usr/bin/env bash
# Firma di prova con rotazione della chiave (18 novembre 2026).
#
# La vecchia chiave di prova (app/debug-key/eurise-debug.keystore) è pubblica nel repository: chiunque poteva firmare
# un'app che il telefono trattava come parte di Eurise (permessi «di firma») e chiedere dati a Password o ai Contatti.
# Qui ogni APK di prova viene rifirmato con la chiave nuova, che sta solo nei segreti di GitHub, insieme alla «prova di
# rotazione» (lineage, APK Signature Scheme v3): il telefono accetta l'aggiornamento sopra le app installate, senza
# disinstallare e senza perdere dati. Nella prova la vecchia chiave conserva solo il diritto di essere aggiornata
# (installed-data): niente permessi di firma, niente uid condiviso, niente ritorno indietro, niente autorizzazioni.
#
# Uso: firma-prova.sh file.apk [altri.apk...]
# Ambiente: EURISE_PROVA_KEYSTORE (keystore PKCS12 in base64, chiave EC P-256, alias «eurise-prova»). Il segreto è il
# file stesso: la sua password è fissa (EURISE_PROVA_PASSWORD, facoltativa, la cambia). Senza il segreto non firma nulla e
# fallisce: un APK di prova con la sola chiave pubblica non deve più arrivare sul telefono.
set -euo pipefail

[ "$#" -gt 0 ] || { echo "Nessun APK da firmare"; exit 0; }
if [ -z "${EURISE_PROVA_KEYSTORE:-}" ]; then
  echo "::error::Manca il segreto EURISE_PROVA_KEYSTORE: gli APK di prova non si pubblicano con la sola chiave pubblica."
  exit 1
fi
EURISE_PROVA_PASSWORD="${EURISE_PROVA_PASSWORD:-eurise-prova}"

root="$(cd "$(dirname "$0")/../.." && pwd)"
old_ks="$root/app/debug-key/eurise-debug.keystore"
alias_new="${EURISE_PROVA_ALIAS:-eurise-prova}"
bt="$(ls -d "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"/build-tools/* | sort -V | tail -1)"
apksigner="$bt/apksigner"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

printf '%s' "$EURISE_PROVA_KEYSTORE" | tr -d ' \n\r' | base64 -d > "$work/prova.p12"
export EURISE_PROVA_PASSWORD

old=(--ks "$old_ks" --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android)
new=(--ks "$work/prova.p12" --ks-type PKCS12 --ks-pass env:EURISE_PROVA_PASSWORD --ks-key-alias "$alias_new" --key-pass env:EURISE_PROVA_PASSWORD)

"$apksigner" rotate --out "$work/lineage" \
  --old-signer "${old[@]}" \
    --set-installed-data true --set-shared-uid false --set-permission false --set-rollback false --set-auth false \
  --new-signer "${new[@]}"

# La vecchia chiave non deve avere il diritto ai permessi di firma: se la prova lo concede, ci si ferma.
"$apksigner" lineage --in "$work/lineage" --print-certs -v | tee "$work/lineage.txt"
# Solo il blocco del primo firmatario (la vecchia chiave); la nuova ha tutti i diritti.
awk '/#2/{exit} {print}' "$work/lineage.txt" > "$work/old.txt"
grep -qi "capabilit" "$work/old.txt" || echo "::warning::Diritti della vecchia chiave non leggibili nella prova: controllare il registro."
if grep -qi "permission capability: true" "$work/old.txt"; then
  echo "::error::La prova di rotazione lascia i permessi di firma alla vecchia chiave: non si firma."
  exit 1
fi

for apk in "$@"; do
  # Rotazione valida da Android 9 (API 28): senza --rotation-min-sdk-version, apksigner la applicherebbe solo da
  # Android 13 e prima resterebbe la vecchia chiave.
  "$apksigner" sign "${old[@]}" --next-signer "${new[@]}" \
    --lineage "$work/lineage" --rotation-min-sdk-version 28 "$apk"
  "$apksigner" verify -v --print-certs "$apk" > "$work/verify.txt"
  grep -q "Verified using v3 scheme (APK Signature Scheme v3): true" "$work/verify.txt" || {
    cat "$work/verify.txt"; echo "::error::$(basename "$apk"): firma v3 mancante"; exit 1; }
  echo "Firmato con la chiave nuova: $(basename "$apk")"
done
rm -f "$work/prova.p12"

#!/usr/bin/env bash
# Nessuna libreria Google chiusa nelle app (CLAUDE.md, «Codice»; docs/SICUREZZA.md, «Aggiornamento delle librerie»):
# Play Services, Firebase, ML Kit, Play Core. Si guardano le librerie di esecuzione scritte dal flusso delle impronte
# (gradle.lockfile della suite e della Tastiera), che le rifà a ogni cambio delle librerie. Le versioni libere di microG
# (org.microg.gms) vanno bene. Esce con errore alla prima trovata.
set -euo pipefail
vietate='^(com\.google\.android\.gms|com\.google\.firebase|com\.google\.mlkit|com\.google\.android\.play|com\.google\.android\.datatransport):'
trovate=0
for f in gradle/eurise/esecuzione/gradle.lockfile keyboard/gradle/eurise/esecuzione/gradle.lockfile; do
  [ -f "$f" ] || continue
  while IFS= read -r riga; do
    echo "::error file=$f,title=Libreria Google chiusa::${riga%%=*} (togliere o sostituire con microG: docs/SICUREZZA.md)"
    trovate=1
  done < <(grep -E "$vietate" "$f" | sed "s/=.*//" | sort -u || true)
done
[ "$trovate" = 0 ] && echo "librerie-vietate: nessuna libreria Google chiusa"
exit "$trovate"

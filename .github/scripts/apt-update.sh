#!/usr/bin/env bash
# apt-get update che regge quando i server di GitHub non risolvono il mirror di Azure (azure.archive.ubuntu.com).
# 1) prova il mirror predefinito; 2) passa al mirror principale di Ubuntu; 3) aggiunge un DNS pubblico (Quad9, Cloudflare).
# Con --src abilita anche le sorgenti (deb-src) per apt-get build-dep. Esce con errore se nessuna strada funziona.
set -uo pipefail

SOURCES=/etc/apt/sources.list.d/ubuntu.sources
LOG="${RUNNER_TEMP:-/tmp}/apt.log"

if [[ "${1:-}" == "--src" ]]; then
  sudo sed -i 's/^Types: deb$/Types: deb deb-src/' "$SOURCES"
fi

update_ok() {
  sudo apt-get update -qq 2>&1 | tee "$LOG"
  ! grep -qE "Temporary failure resolving|Failed to fetch|Could not resolve" "$LOG"
}

if update_ok; then exit 0; fi

echo "::warning::Mirror di Azure non raggiungibile: passo ad archive.ubuntu.com"
sudo sed -i 's#mirror+file:/etc/apt/apt-mirrors.txt#http://archive.ubuntu.com/ubuntu/#' "$SOURCES"
sudo sed -i 's#http://azure.archive.ubuntu.com/ubuntu/\?#http://archive.ubuntu.com/ubuntu/#' "$SOURCES" /etc/apt/sources.list 2>/dev/null || true
if update_ok; then exit 0; fi

echo "::warning::Neanche archive.ubuntu.com si risolve: aggiungo un DNS pubblico"
if command -v resolvectl >/dev/null; then
  iface=$(ip route show default | awk '{print $5; exit}')
  sudo resolvectl dns "$iface" 9.9.9.9 1.1.1.1 || true
  sudo resolvectl flush-caches || true
else
  echo "nameserver 9.9.9.9" | sudo tee /etc/resolv.conf >/dev/null
fi
for i in 1 2 3; do
  if update_ok; then exit 0; fi
  echo "Ancora niente, riprovo tra 30 s ($i)"; sleep 30
done

echo "::error::apt-get update non riesce: mirror di Ubuntu non raggiungibili da questo server"
exit 1

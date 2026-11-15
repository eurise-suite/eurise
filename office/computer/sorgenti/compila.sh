#!/usr/bin/env bash
# Eurise Office per Linux dai sorgenti (strada 2): compila la ricetta eu.eurise.Office (ricetta.py) con flatpak-builder,
# a tappe come il motore Android: si compila finché c'è tempo, la ccache si salva tra un giro e l'altro (nella
# pre-release flatpak-sorgenti-cache, a pezzi: le cache delle Actions servono al motore Android e hanno un tetto per tutto
# il repository) e il giro dopo riparte con i colpi della cache. Quando la compilazione arriva in fondo esce il pacchetto.
# Uso: compila.sh <cartella di lavoro> <cartella di uscita> <minuti per flatpak-builder>
# Variabili: GH_TOKEN e GITHUB_REPOSITORY per la cache nelle release.
set -euo pipefail
WORK=$(realpath -m "$1")
OUT=$(realpath -m "$2")
MINUTES=$3
REPO=$(cd "$(dirname "$0")/../../.." && pwd)
APP=eu.eurise.Office
# Ricetta di Collabora su Flathub (flathub/com.collaboraoffice.Office, 6 novembre 2026: coda-26.04.5.3-1).
FLATHUB_COMMIT=1718414aec4f2f799c70e56ead688a7f4c7f82ac
CACHE_TAG=flatpak-sorgenti-cache
mkdir -p "$WORK" "$OUT"
STATE="$WORK/stato"
mkdir -p "$STATE/ccache"

echo "== ricetta"
git clone -q https://github.com/flathub/com.collaboraoffice.Office "$WORK/flathub"
git -C "$WORK/flathub" checkout -q "$FLATHUB_COMMIT"
python3 "$REPO/office/computer/sorgenti/ricetta.py" "$WORK/flathub/com.collaboraoffice.Office.json" "$REPO" "$WORK/$APP.json"
cp "$WORK/$APP.json" "$OUT/"

echo "== ccache del giro prima"
if gh release view "$CACHE_TAG" -R "$GITHUB_REPOSITORY" > /dev/null 2>&1; then
	mkdir -p "$WORK/pezzi"
	if gh release download "$CACHE_TAG" -R "$GITHUB_REPOSITORY" -p 'ccache.tar.zst.*' -D "$WORK/pezzi" 2> /dev/null; then
		cat "$WORK"/pezzi/ccache.tar.zst.* | zstd -d -T0 | tar -x -C "$STATE"
		rm -rf "$WORK/pezzi"
		du -sh "$STATE/ccache" || true
	fi
fi
# Percorsi stabili tra un giro e l'altro (/run/build/<modulo>): conta il contenuto, non l'ora dei file.
cat > "$STATE/ccache/ccache.conf" <<'CONF'
max_size = 7G
compiler_check = content
sloppiness = pch_defines,time_macros,include_file_mtime,include_file_ctime,file_stat_matches
hash_dir = false
CONF

echo "== runtime, SDK e BaseApp da Flathub"
flatpak remote-add --user --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo

echo "== compilazione (al massimo $MINUTES minuti)"
status=0
( while sleep 600; do echo "$(date -u +%H:%M) – $(du -sh "$STATE/ccache" 2> /dev/null | cut -f1) di ccache – $(tail -1 "$OUT/compila.log" 2> /dev/null | cut -c1-160)"; done ) &
progress=$!
timeout -s INT -k 300 "$((MINUTES * 60))" flatpak-builder --user --install-deps-from=flathub --ccache \
	--state-dir="$STATE" --disable-rofiles-fuse --force-clean --repo="$WORK/repo" \
	"$WORK/app" "$WORK/$APP.json" > "$OUT/compila.log" 2>&1 || status=$?
kill "$progress" 2> /dev/null || true
echo "flatpak-builder: $status"
tail -60 "$OUT/compila.log"
echo "$status" > "$OUT/esito.txt"

echo "== ccache per il giro dopo"
if [ -n "${GH_TOKEN:-}" ] && [ -d "$STATE/ccache" ]; then
	gh release view "$CACHE_TAG" -R "$GITHUB_REPOSITORY" > /dev/null 2>&1 || \
		gh release create "$CACHE_TAG" -R "$GITHUB_REPOSITORY" --prerelease \
			--title "Cache di compilazione di Eurise Office per Linux" --notes "Pezzi della ccache tra un giro e l'altro. Non si installa."
	mkdir -p "$WORK/su"
	tar -C "$STATE" -c ccache | zstd -T0 -3 | split -b 1900m -d -a 2 - "$WORK/su/ccache.tar.zst."
	ls -la "$WORK/su"
	# Via i pezzi vecchi che il nuovo giro non sostituisce.
	for old in $(gh release view "$CACHE_TAG" -R "$GITHUB_REPOSITORY" --json assets -q '.assets[].name'); do
		[ -e "$WORK/su/$old" ] || gh release delete-asset "$CACHE_TAG" "$old" -R "$GITHUB_REPOSITORY" -y || true
	done
	gh release upload "$CACHE_TAG" "$WORK"/su/ccache.tar.zst.* -R "$GITHUB_REPOSITORY" --clobber
fi

if [ "$status" = 0 ]; then
	echo "== pacchetto"
	flatpak build-bundle "$WORK/repo" "$OUT/eurise-office-sorgenti.flatpak" "$APP" --runtime-repo=https://dl.flathub.org/repo/flathub.flatpakrepo
	ls -la "$OUT/eurise-office-sorgenti.flatpak"
fi

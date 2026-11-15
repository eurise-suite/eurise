#!/usr/bin/env bash
# Flatpak Eurise Office, prototipo (strada 1, decisa dal proprietario il 15 novembre): riusa Collabora Office per Linux già
# compilato da Flathub (motore e guscio Qt coda-qt) e ci mette sopra la nostra interfaccia web, il nome «Eurise Office»,
# l'icona e la voce nel menu delle app con l'identificativo eu.eurise.Office. Una finestra per documento (il guscio è
# quello di Collabora). Non è per Flathub (che vuole la compilazione dai sorgenti: strada 2, dopo).
# Uso: office/computer/flatpak/costruisci.sh <cartella di uscita>      (su Ubuntu con flatpak)
# Esce: <uscita>/eurise-office.flatpak, da installare con «flatpak install --user eurise-office.flatpak».
set -euo pipefail
OUT=$(realpath -m "${1:-flatpak-eurise}")
mkdir -p "$OUT"
REPO=$(cd "$(dirname "$0")/../../.." && pwd)
HERE="$REPO/office/computer/flatpak"
SRC=com.collaboraoffice.Office
APP=eu.eurise.Office
WORK=$(mktemp -d)

flatpak remote-add --user --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo
flatpak config --user --set languages "it;en"
flatpak install --user -y --noninteractive flathub "$SRC" > "$OUT/installa.log" 2>&1 || { tail -30 "$OUT/installa.log"; exit 1; }
LOC=$(flatpak info --user --show-location "$SRC")
VERSION=$(flatpak info --user "$SRC" | sed -n 's/^ *Version: *//p')
RUNTIME=$(sed -n 's/^runtime=//p' "$LOC/metadata")        # es. org.kde.Platform/x86_64/6.11
RT_ID=${RUNTIME%%/*}
RT_BRANCH=${RUNTIME##*/}
echo "Collabora Office $VERSION, runtime $RUNTIME"

# 1. La cartella dell'app: i file di Collabora (motore, guscio Qt, QtWebEngine della BaseApp), copiati.
flatpak build-init "$WORK/app" "$APP" "$RT_ID" "$RT_ID" "$RT_BRANCH"
cp -a "$LOC/files/." "$WORK/app/files/"
chmod -R u+w "$WORK/app/files"

# 2. La nostra interfaccia nella pagina del guscio Qt.
find "$WORK/app/files" -name 'cool-qt.html' -path '*dist*' | while read -r page; do
	python3 "$REPO/office/computer/inietta.py" "$(dirname "$page")" "$REPO/office/app/web"
done

# 3. Nome, icona e voce nel menu: via quelle di Collabora, le nostre con l'identificativo eu.eurise.Office.
SHARE="$WORK/app/files/share"
ORIG_DESKTOP=$(ls "$SHARE"/applications/"$SRC".desktop 2> /dev/null | head -1 || true)
python3 "$HERE/voce.py" "${ORIG_DESKTOP:-/nessuno}" "$SHARE/applications/$APP.desktop"
find "$SHARE" \( -name "$SRC*.desktop" -o -name "$SRC*.png" -o -name "$SRC*.svg" -o -name "$SRC*.xml" \) -delete
mkdir -p "$SHARE/icons/hicolor/scalable/apps"
cp "$HERE/$APP.svg" "$SHARE/icons/hicolor/scalable/apps/$APP.svg"
for size in 64 128 256; do
	mkdir -p "$SHARE/icons/hicolor/${size}x${size}/apps"
	rsvg-convert -w $size -h $size "$HERE/$APP.svg" -o "$SHARE/icons/hicolor/${size}x${size}/apps/$APP.png"
done
mkdir -p "$SHARE/metainfo"
sed "s/@VERSIONE@/$VERSION/" "$HERE/$APP.metainfo.xml" > "$SHARE/metainfo/$APP.metainfo.xml"

# 4. Permessi e variabili come Collabora (dal suo metadata), comando coda-qt.
mapfile -t ARGS < <(python3 - "$LOC/metadata" <<'PY'
import configparser, sys
c = configparser.ConfigParser(interpolation=None, strict=False)
c.optionxform = str
c.read(sys.argv[1])
ctx = c['Context'] if c.has_section('Context') else {}
for key, flag in (('shared', '--share'), ('sockets', '--socket'), ('devices', '--device'), ('filesystems', '--filesystem')):
    for v in filter(None, ctx.get(key, '').split(';')):
        print(f'{flag}={v}')
if c.has_section('Environment'):
    for k, v in c['Environment'].items():
        print(f'--env={k}={v}')
PY
)
# Il guscio Qt tiene un'istanza sola con il nome D-Bus di Collabora (DBusService.cpp): con un altro identificativo serve
# il permesso esplicito di prenderlo.
ARGS+=("--own-name=com.collaboraoffice.Office")
echo "permessi: ${ARGS[*]}"
flatpak build-finish "$WORK/app" --command=coda-qt "${ARGS[@]}"

# 5. Pacchetto: deposito locale, poi il file .flatpak (il runtime lo prende da Flathub chi lo installa).
flatpak build-export "$WORK/repo" "$WORK/app" > /dev/null
flatpak build-bundle "$WORK/repo" "$OUT/eurise-office.flatpak" "$APP" --runtime-repo=https://dl.flathub.org/repo/flathub.flatpakrepo
ls -la "$OUT/eurise-office.flatpak"
echo "$VERSION" > "$OUT/versione-collabora.txt"

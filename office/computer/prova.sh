#!/usr/bin/env bash
# Prototipo di Office sul computer (tavola OffComputer): Collabora Office per Linux da Flathub (coda-qt, stesso motore e
# stessa interfaccia web di Collabora) con sopra la nostra interfaccia: Control.Eurise.js ed eurise.css aggiunti alla
# sua pagina cool.html. Apre testo, foglio e presentazione su uno schermo virtuale e fotografa.
# Uso: office/computer/prova.sh <cartella delle schermate>     (su Ubuntu, con flatpak, Xvfb, xdotool, imagemagick)
set -u
OUT=$(realpath -m "${1:-schermate}")
mkdir -p "$OUT"
REPO=$(cd "$(dirname "$0")/../.." && pwd)
WEB="$REPO/office/app/web"
APP=com.collaboraoffice.Office
W=1440
H=900
R="$OUT/riassunto.md"
echo "# Office sul computer: prova del prototipo" > "$R"

flatpak remote-add --user --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo
flatpak install --user -y --noninteractive flathub "$APP" > "$OUT/installa.log" 2>&1 || { tail -30 "$OUT/installa.log"; exit 1; }
VERSION=$(flatpak info --user "$APP" | sed -n 's/^ *Version: *//p')
echo "- Collabora Office $VERSION da Flathub" >> "$R"

# La pagina dell'interfaccia: WebView::load apre <dati>/browser/dist/cool.html.
FILES="$(flatpak info --user --show-location "$APP")/files"
COOL=$(find "$FILES" -path '*browser/dist/cool.html' | head -1)
[ -n "$COOL" ] || { echo "cool.html non trovato" | tee -a "$R"; exit 1; }
DIST=$(dirname "$COOL")
echo "- interfaccia in \`${DIST#"$FILES"}\`" >> "$R"
chmod -R u+w "$DIST"
# Copie nuove (cp poi mv): i file del pacchetto sono legami fisici con il deposito di ostree.
put() { cp "$1" "$2.eurise" && mv -f "$2.eurise" "$2"; }
put "$WEB/Control.Eurise.js" "$DIST/Control.Eurise.js"
put "$WEB/eurise.css" "$DIST/eurise.css"
mkdir -p "$DIST/images/dark"
for svg in "$WEB"/images/*.svg; do put "$svg" "$DIST/images/$(basename "$svg")"; done
for svg in "$WEB"/images/dark/*.svg; do [ -e "$svg" ] && put "$svg" "$DIST/images/dark/$(basename "$svg")"; done
python3 - "$COOL" <<'EOF'
import re, sys
p = sys.argv[1]
s = open(p, encoding='utf-8').read()
if 'Control.Eurise.js' not in s:
    # Il nostro foglio di stile per ultimo; il nostro script subito dopo bundle.js, con gli stessi attributi (defer).
    s = s.replace('</head>', '<link rel="stylesheet" href="eurise.css" />\n</head>', 1)
    m = re.search(r'<script[^>]*src="[^"]*bundle\.js"[^>]*>\s*</script>', s)
    if not m:
        sys.exit('bundle.js non trovato in cool.html')
    tag = m.group(0)
    ours = re.sub(r'src="[^"]*bundle\.js"', 'src="Control.Eurise.js"', tag)
    s = s.replace(tag, tag + '\n' + ours, 1)
open(p + '.eurise', 'w', encoding='utf-8').write(s)
EOF
mv -f "$COOL.eurise" "$COOL"
grep -o '<script[^>]*\(bundle\|Eurise\)[^>]*>' "$COOL" | sed 's/^/    /' >> "$R"

# Schermo virtuale e sessione D-Bus (il guscio Qt parla con il portale per il tema).
export DISPLAY=:99
Xvfb :99 -screen 0 ${W}x${H}x24 -nolisten tcp > /dev/null 2>&1 &
sleep 2
mkdir -p "$HOME/Documents"
cp "$REPO"/office/prova/campioni/{lettera.docx,conti.xlsx,slide.pptx} "$HOME/Documents/"

shot() { import -window root "$OUT/$1.png" 2> /dev/null || xwd -root -silent | convert xwd:- "$OUT/$1.png"; }

apri() {
	local file=$1 name=${1%%.*}
	echo "" >> "$R"
	echo "## $file" >> "$R"
	dbus-run-session -- flatpak run --user \
		--env=QTWEBENGINE_CHROMIUM_FLAGS="--disable-gpu" \
		--env=QT_QPA_PLATFORM=xcb \
		--env=QT_LOGGING_RULES="js=true" \
		"$APP" "$HOME/Documents/$file" > "$OUT/$name.log" 2>&1 &
	local pid=$!
	local win=""
	for _ in $(seq 1 60); do
		win=$(xdotool search --onlyvisible --name "$file" 2> /dev/null | head -1)
		[ -n "$win" ] && break
		sleep 1
	done
	if [ -z "$win" ]; then
		echo "- finestra non comparsa in 60 s" >> "$R"
	else
		# A tutto schermo come la tavola (1440×900).
		xdotool windowsize "$win" $W $H windowmove "$win" 0 0 2> /dev/null
		echo "- finestra comparsa" >> "$R"
	fi
	sleep 25
	shot "$name-1-aperto"
	# Ctrl K: «Cerca un comando» della nostra interfaccia.
	[ -n "$win" ] && xdotool mousemove 720 520 click 1 && sleep 1 && xdotool key ctrl+k && sleep 2 && shot "$name-2-cerca" && xdotool key Escape
	grep -iE "eurise|uncaught|error" "$OUT/$name.log" | grep -v "^$" | head -15 | sed 's/^/    /' >> "$R"
	kill "$pid" 2> /dev/null
	pkill -f coda-qt 2> /dev/null
	sleep 3
}

apri lettera.docx
apri conti.xlsx
apri slide.pptx
echo "" >> "$R"
ls "$OUT"/*.png 2> /dev/null | wc -l | sed 's/^/- schermate: /' >> "$R"

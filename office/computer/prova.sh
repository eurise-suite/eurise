#!/usr/bin/env bash
# Prova di Office sul computer (tavola OffComputer). Di base apre il Flatpak Eurise Office (eu.eurise.Office, già installato
# da costruisci.sh: Collabora Office per Linux con la nostra interfaccia dentro); con APP=com.collaboraoffice.Office prova
# l'interfaccia sopra il Collabora di Flathub. Apre testo, foglio e presentazione su uno schermo virtuale e fotografa.
# Uso: [APP=…] office/computer/prova.sh <cartella delle schermate>     (su Ubuntu, con flatpak, Xvfb, xdotool, imagemagick)
set -u
OUT=$(realpath -m "${1:-schermate}")
mkdir -p "$OUT"
REPO=$(cd "$(dirname "$0")/../.." && pwd)
WEB="$REPO/office/app/web"
APP=${APP:-eu.eurise.Office}
W=1440
H=900
R="$OUT/riassunto.md"
echo "# Office sul computer: prova del prototipo" > "$R"

flatpak remote-add --user --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo
# Interfaccia in italiano: si scaricano anche le traduzioni (estensioni .Locale) dell'italiano.
flatpak config --user --set languages "it;en"
if [ "$APP" = com.collaboraoffice.Office ]; then
	flatpak install --user -y --noninteractive flathub "$APP" > "$OUT/installa.log" 2>&1 || { tail -30 "$OUT/installa.log"; exit 1; }
fi
flatpak info --user "$APP" > /dev/null || { echo "$APP non è installato" >> "$R"; exit 1; }
VERSION=$(flatpak info --user "$APP" | sed -n 's/^ *Version: *//p')
echo "- $APP ${VERSION:-} (motore di Collabora Office)" >> "$R"

# La pagina dell'interfaccia: WebView::load apre <dati>/browser/dist/cool.html; nel pacchetto ce ne può essere più d'una
# (cool-qt.html è quella del guscio Qt, cool.html quella del server), quindi la nostra interfaccia va in tutte e due.
FILES="$(flatpak info --user --show-location "$APP")/files"
find "$FILES" -name 'cool*.html' | sed "s|^$FILES|    |" >> "$R"
DIAG=$(mktemp)
cat > "$DIAG" <<'JS'
(function () {
	// Nella 26.04 console.log non arriva al registro del guscio: passa solo app.console.
	function say(text) {
		if (window.app && window.app.console && window.app.console.log) window.app.console.log(text);
		else console.log(text);
	}
	function report(when) {
		var m = window.mode;
		say('EURISE-DIAG ' + when + ' ' + location.href.split('?')[0] + ' ' + JSON.stringify({
			qt: !!window.ThisIsTheQtApp, mobileApp: !!window.ThisIsAMobileApp,
			desktop: !!(m && m.isDesktop && m.isDesktop()), tablet: !!(m && m.isTablet && m.isTablet()),
			ours: !!window.euriseOffice, map: !!(window.app && window.app.map), doc: !!(window.app && window.app.map && window.app.map._docLayer),
			body: document.body ? document.body.className : '', top: !!document.getElementById('eurise-top'),
			ribbon: !!document.getElementById('eurise-ribbon'), row: !!document.getElementById('toolbar-row'),
			trace: window.euriseOffice ? window.euriseOffice.trace : null,
			missingIcons: window.euriseOffice && window.euriseOffice.missingIcons ? Object.keys(window.euriseOffice.missingIcons) : null,
			ui: window.app && window.app.UI ? Object.keys(window.app.UI) : null,
			rulerPref: (function () { try { return window.app.map.uiManager.getBooleanDocTypePref('ShowRuler', null); } catch (e) { return String(e); } })(),
			rulers: { v: !!document.getElementById('vertical-ruler'), vShown: !!(document.getElementById('vertical-ruler') && document.getElementById('vertical-ruler').offsetParent) },
			modeKeys: m ? Object.keys(m).map(function (k) { return k + ':' + typeof m[k]; }) : null,
			appKeys: window.app ? Object.keys(window.app).slice(0, 60) : null
		}));
	}
	window.addEventListener('error', function (e) { say('EURISE-DIAG errore ' + e.message + ' @ ' + (e.filename || '') + ':' + e.lineno); });
	// Struttura della colonna di destra e di quella di sinistra (per lo stile della colonna Properties).
	function outline(el, depth, out) {
		if (!el || out.length > 220) return out;
		var cls = typeof el.className === 'string' ? el.className.trim().split(/\s+/).slice(0, 4).join('.') : '';
		var shown = el.offsetParent !== null || el === document.body;
		var high = ' h' + el.offsetHeight + (getComputedStyle(el).display === 'none' ? ' none' : '');
		out.push(new Array(depth + 1).join('  ') + el.tagName.toLowerCase() + (el.id ? '#' + el.id : '') + (cls ? '.' + cls : '') +
			(shown ? '' : ' (nascosto)') + high + (el.children.length ? '' : ' «' + (el.textContent || '').trim().slice(0, 30) + '»'));
		if (depth < 12) Array.prototype.forEach.call(el.children, function (c) { outline(c, depth + 1, out); });
		return out;
	}
	function dom(when) {
		['eurise-struttura', 'sidebar-dock-wrapper', 'navigation-sidebar'].forEach(function (id) {
			outline(document.getElementById(id), 0, []).forEach(function (line) { say('EURISE-DOM ' + when + ' ' + line); });
		});
		Array.prototype.forEach.call(document.querySelectorAll('[class*="tour"], [id*="tour"], [class*="welcome"], [id*="welcome"], dialog[open], [role="dialog"]'), function (el) {
			say('EURISE-DOM ' + when + ' finestra ' + el.tagName.toLowerCase() + '#' + el.id + '.' + el.className + ' «' + (el.textContent || '').trim().slice(0, 60) + '»');
		});
	}
	// Nel foglio di calcolo la prova cambia l'accento delle icone in Cuoio (come dal menu File › Colore delle icone).
	setTimeout(function () {
		if (window.euriseOffice && window.euriseOffice.setAccent && window.app && window.app.map && window.app.map.getDocType() === 'spreadsheet') {
			window.euriseOffice.setAccent('#7A4A2A', '#C9956B');
		}
	}, 12000);
	// Tasti della prova (solo con la diagnosi): F6 scheda seguente del nastro, F7 menu File, F8 tendina del carattere,
	// F9 finestra Paragrafo; così le schermate mostrano il nastro completo, le tendine e le finestre.
	document.addEventListener('keydown', function (e) {
		var q = function (sel) { return document.querySelector(sel); };
		if (e.key === 'F6') {
			var tabs = Array.prototype.slice.call(document.querySelectorAll('#eurise-tabs .eurise-rtab[data-id]'));
			var i = tabs.findIndex(function (b) { return b.classList.contains('active'); });
			if (tabs.length) tabs[(i + 1) % tabs.length].click();
		} else if (e.key === 'F7' && q('#eurise-tabs .eurise-rtab.file')) q('#eurise-tabs .eurise-rtab.file').click();
		else if (e.key === 'F8' && q('#eurise-ribbon .eurise-drop.name')) q('#eurise-ribbon .eurise-drop.name').click();
		else if (e.key === 'F9' && window.app && window.app.map) window.app.map.sendUnoCommand('.uno:ParagraphDialog');
		else if (e.key === 'F10' && q('#eurise-tabs [data-id="vista"]')) {
			q('#eurise-tabs [data-id="vista"]').click();
			setTimeout(function () { var b = q('#eurise-ribbon [aria-label="Concentrazione"]'); if (b) b.click(); }, 200);
		} else if (e.key === 'F11' && window.app && window.app.map) {
			window.app.map.sendUnoCommand('.uno:InsertTable', { Columns: { type: 'long', value: 3 }, Rows: { type: 'long', value: 2 } });
		}
		else return;
		e.preventDefault();
		e.stopPropagation();
	}, true);
	setTimeout(function () { report('5s'); }, 5000);
	setTimeout(function () { report('20s'); dom('20s'); }, 20000);
})();
JS
# Interfaccia Eurise (già dentro il Flatpak Eurise; sopra il Collabora di Flathub la mette qui) e diagnosi della prova.
find "$FILES" -name 'cool-qt.html' -path '*dist*' | while read -r COOL; do
	chmod -R u+w "$(dirname "$COOL")"
	python3 "$REPO/office/computer/inietta.py" "$(dirname "$COOL")" "$WEB" "$DIAG" | sed 's/^/- /' >> "$R"
	grep -o '<script[^>]*\(bundle\|Eurise\|diag\)[^>]*>' "$COOL" | sed 's/^/    /' >> "$R"
done

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
		--filesystem="$HOME/Documents" \
		--env=QT_LOGGING_RULES="js=true" \
		--env=LANG=it_IT.UTF-8 --env=LANGUAGE=it --env=LC_ALL=it_IT.UTF-8 \
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
	# Il benvenuto di Collabora al primo avvio copre la pagina: Esc lo chiude.
	[ -n "$win" ] && xdotool key Escape && sleep 2
	shot "$name-1-aperto"
	# Con il mouse sul documento (la 26.04 mostra alcune cose solo al passaggio).
	[ -n "$win" ] && xdotool mousemove 520 480 && sleep 2 && shot "$name-1-mouse" 
	# Ctrl K: «Cerca un comando» della nostra interfaccia.
	[ -n "$win" ] && xdotool mousemove 720 520 click 1 && sleep 1 && xdotool key ctrl+k && sleep 2 && shot "$name-2-cerca" && xdotool key Escape
	# Nastro completo, menu File, tendina del carattere, Trova e sostituisci, una finestra (tavole OffPC, 15 novembre).
	if [ -n "$win" ]; then
		xdotool mousemove 720 520 click 1 && sleep 1
		for n in 1 2 3 4 5 6 7 8; do shot "$name-nastro-$n"; xdotool key F6; sleep 1; done
		xdotool key F7 && sleep 2 && shot "$name-file" && xdotool key Escape && sleep 1
		xdotool key F8 && sleep 3 && shot "$name-carattere" && xdotool key Escape && sleep 1
		xdotool mousemove 720 520 click 1 && sleep 1 && xdotool key ctrl+h && sleep 2 && shot "$name-trova" && xdotool key Escape && sleep 1
		xdotool key F9 && sleep 4 && shot "$name-finestra" && xdotool key Escape && sleep 1
		if [ "$name" = lettera ]; then
			# Ergonomia (15 novembre): lettere del tasto Alt, barra rapida su una parola, tasto destro, Concentrazione,
			# scheda Tabella che compare da sola.
			xdotool mousemove 720 520 click 1 && sleep 1 && xdotool key alt && sleep 1 && shot "$name-alt" && xdotool key Escape && sleep 1
			xdotool mousemove 300 397 click --repeat 2 --delay 80 1 && sleep 2 && shot "$name-barra"
			xdotool mousemove 300 420 click 3 && sleep 2 && shot "$name-destro" && xdotool key Escape && sleep 1
			xdotool key F10 && sleep 2 && shot "$name-concentrazione" && xdotool key Escape && sleep 2
			xdotool mousemove 700 600 click 1 && sleep 1 && xdotool key ctrl+End && sleep 1 && xdotool key F11 && sleep 3 && shot "$name-tabella"
		fi
	fi
	grep -E "EURISE-DIAG|Uncaught|TypeError|ReferenceError" "$OUT/$name.log" | head -20 | sed 's/^/    /' >> "$R"
	kill "$pid" 2> /dev/null
	pkill -f coda-qt 2> /dev/null
	sleep 3
}

# Il giro guidato della 26.04: dove sta nel codice dell'interfaccia (per spegnerlo per bene).
for COOL in $(find "$FILES" -name 'bundle.js' -path '*dist*' | head -1); do
	grep -o '.\{0,300\}short tour.\{0,300\}' "$COOL" | head -3 > "$OUT/giro-guidato.txt"
	grep -o '.\{0,200\}[Tt]our[A-Za-z]*[Ss]een.\{0,200\}' "$COOL" | head -5 >> "$OUT/giro-guidato.txt"
	grep -rl "short tour" "$(dirname "$COOL")" | sed "s|^$FILES||" >> "$OUT/giro-guidato.txt"
	# Righelli: quando li mostra la 26.04.
	echo "--- righelli css" >> "$OUT/giro-guidato.txt"
	grep -oh '[^}]\{0,200\}ruler[^}]\{0,250\}}' "$(dirname "$COOL")"/*.css | head -40 >> "$OUT/giro-guidato.txt"
	echo "--- righelli js" >> "$OUT/giro-guidato.txt"
	grep -oh '.\{0,300\}\(rulerVisib\|autoHideRuler\|ruler-hover\|showRulerOnHover\|hideRuler\).\{0,300\}' "$COOL" | head -12 >> "$OUT/giro-guidato.txt"
	# Come la 26.04 colora le icone (classe icon-maskable).
	echo "--- icon-maskable" >> "$OUT/giro-guidato.txt"
	grep -oh '.\{0,250\}icon-maskable.\{0,350\}' "$(dirname "$COOL")"/*.css "$COOL" | head -12 >> "$OUT/giro-guidato.txt"
done

# Due documenti nella stessa finestra: il secondo arriva per D-Bus al guscio già aperto e diventa una scheda; la striscia
# delle schede (con lo stile Eurise) compare.
schede() {
	echo "" >> "$R"
	echo "## Due documenti, due schede" >> "$R"
	local script=$OUT/schede.sh
	cat > "$script" <<SCHEDE
apri() { flatpak run --user --env=QTWEBENGINE_CHROMIUM_FLAGS=--disable-gpu --env=QT_QPA_PLATFORM=xcb \\
	--filesystem=$HOME/Documents --env=LANG=it_IT.UTF-8 --env=LANGUAGE=it --env=LC_ALL=it_IT.UTF-8 $APP "\$1" >> $OUT/schede.log 2>&1 & }
apri $HOME/Documents/lettera.docx
sleep 30
w=\$(xdotool search --onlyvisible --name lettera.docx 2> /dev/null | head -1)
[ -n "\$w" ] && xdotool windowsize "\$w" $W $H windowmove "\$w" 0 0
apri $HOME/Documents/conti.xlsx
sleep 30
import -window root $OUT/schede.png
SCHEDE
	dbus-run-session -- bash "$script" || true
	grep -c . "$OUT/schede.log" | sed 's/^/- righe di registro: /' >> "$R"
	pkill -f coda-qt 2> /dev/null
	sleep 3
}

# Inizio Eurise: il guscio aperto senza documenti (tavola OffPCInizio).
inizio() {
	echo "" >> "$R"
	echo "## Inizio" >> "$R"
	dbus-run-session -- flatpak run --user --env=QTWEBENGINE_CHROMIUM_FLAGS="--disable-gpu" --env=QT_QPA_PLATFORM=xcb \
		--filesystem="$HOME/Documents" --env=QT_LOGGING_RULES="js=true" \
		--env=LANG=it_IT.UTF-8 --env=LANGUAGE=it --env=LC_ALL=it_IT.UTF-8 "$APP" > "$OUT/inizio.log" 2>&1 &
	local pid=$! win=""
	for _ in $(seq 1 60); do
		win=$(xdotool search --onlyvisible --name "Office" 2> /dev/null | head -1)
		[ -n "$win" ] && break
		sleep 1
	done
	[ -n "$win" ] && xdotool windowsize "$win" $W $H windowmove "$win" 0 0 2> /dev/null && echo "- finestra comparsa" >> "$R"
	sleep 15
	shot inizio
	grep -E "EURISE-DIAG|Uncaught|TypeError|ReferenceError" "$OUT/inizio.log" | head -10 | sed 's/^/    /' >> "$R"
	kill "$pid" 2> /dev/null
	pkill -f coda-qt 2> /dev/null
	sleep 3
}

apri lettera.docx
inizio
apri conti.xlsx
apri slide.pptx
schede
echo "" >> "$R"
ls "$OUT"/*.png 2> /dev/null | wc -l | sed 's/^/- schermate: /' >> "$R"

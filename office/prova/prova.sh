#!/usr/bin/env bash
# Prova di apertura di Eurise Office sull'emulatore (lavoro «Office: prova sull'emulatore»).
# Uso: office/prova/prova.sh <apk di Office> <cartella dei risultati>
#
# Per ogni campione (docx, xlsx, pptx, odt, ods, pdf) due vie, come sul telefono:
#   - content: il file in Download, aperto con VIEW e un Uri content:// (come «Apri con» da Eurise File o da un'altra app);
#   - file:    il file copiato nella memoria dell'app e aperto con file:// (prova il motore senza i permessi).
# Per ogni apertura: registro dell'app (logcat), schermata dopo l'attesa, esito in riassunto.md.
set -u
APK=$1
OUT=$2
PKG=eu.eurise.office.debug
LO=org.libreoffice.androidlib.LOActivity
WAIT=${WAIT:-75}
HERE=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$OUT"
sum="$OUT/riassunto.md"
echo "# Prova di apertura di Eurise Office" > "$sum"

adb wait-for-device
adb shell getprop ro.product.cpu.abilist | tee -a "$sum"
adb install -r -g "$APK" 2>&1 | tail -2 | tee -a "$sum" || true
adb shell pm list packages | grep -q "$PKG" || { echo "Installazione non riuscita" | tee -a "$sum"; exit 0; }

mime() {
	case "$1" in
	*.docx) echo application/vnd.openxmlformats-officedocument.wordprocessingml.document ;;
	*.xlsx) echo application/vnd.openxmlformats-officedocument.spreadsheetml.sheet ;;
	*.pptx) echo application/vnd.openxmlformats-officedocument.presentationml.presentation ;;
	*.odt) echo application/vnd.oasis.opendocument.text ;;
	*.ods) echo application/vnd.oasis.opendocument.spreadsheet ;;
	*.pdf) echo application/pdf ;;
	esac
}

# Primo avvio dalla schermata iniziale (estrae i file del motore).
adb logcat -c
adb shell am start -W -n "$PKG/org.libreoffice.androidapp.ui.LibreOfficeUIActivity" | tee -a "$sum"
sleep 20
adb exec-out screencap -p > "$OUT/00-iniziale.png"
adb logcat -d > "$OUT/00-iniziale.log"
adb shell am force-stop "$PKG"

for f in "$HERE"/campioni/*; do
	name=$(basename "$f")
	type=$(mime "$name")
	adb push "$f" "/sdcard/Download/$name" > /dev/null
	adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Download/$name" > /dev/null
	adb shell "cat > /data/local/tmp/$name" < "$f"
	adb shell "run-as $PKG mkdir -p files/prova && cat /data/local/tmp/$name | run-as $PKG sh -c 'cat > files/prova/$name'"
done
sleep 5

open_one() { # via nome uri tipo
	local via=$1 name=$2 uri=$3 type=$4 tag="$2-$1"
	adb logcat -c
	echo "## $name ($via)" >> "$sum"
	echo '```' >> "$sum"
	adb shell am start -W -a android.intent.action.VIEW -t "$type" -d "'$uri'" \
		--grant-read-uri-permission --grant-write-uri-permission -n "$PKG/$LO" 2>&1 | tee -a "$sum"
	sleep "$WAIT"
	adb exec-out screencap -p > "$OUT/$tag.png"
	adb logcat -d > "$OUT/$tag.log"
	{
		echo "processo: $(adb shell pidof "$PKG" || echo 'non c\x27è più')"
		echo "attività in primo piano: $(adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity' | sed 's/^ *//')"
		grep -E "FATAL|AndroidRuntime|UnsatisfiedLink|SIGSEGV|SIGABRT|backtrace|couldn't create temporary|file not found|Success copying|LOActivity|COOLWSD|loadDocument|lok|kit|error|Error" "$OUT/$tag.log" |
			grep -v -E "chatty|GoogleApi|Finsky|gms" | head -60
	} >> "$sum"
	echo '```' >> "$sum"
	adb shell am force-stop "$PKG"
	sleep 3
}

for f in "$HERE"/campioni/*; do
	name=$(basename "$f")
	type=$(mime "$name")
	id=$(adb shell content query --uri content://media/external/file --projection _id --where "\"_display_name='$name'\"" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | head -1)
	if [ -n "$id" ]; then
		open_one content "$name" "content://media/external/file/$id" "$type"
	else
		echo "## $name (content): non trovato in MediaStore" >> "$sum"
	fi
	open_one file "$name" "file:///data/data/$PKG/files/prova/$name" "$type"
done
cat "$sum"

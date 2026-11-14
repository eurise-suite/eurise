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
PKG=${PKG:-eu.eurise.office.debug}
# Strada A: il motore è l'APK compagno «Eurise Office Motore», con il suo processo.
MOTORE_PKG=${MOTORE_PKG:-eu.eurise.office.motore.debug}
# «Apri con» passa da DocumentActivity (LOActivity non è esportata: ci arriva solo Office).
LO=${LO:-eu.eurise.office.home.DocumentActivity}
WAIT=${WAIT:-40}
HERE=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$OUT"
sum="$OUT/riassunto.md"
echo "# Prova di apertura di Eurise Office" > "$sum"

adb wait-for-device
adb logcat -G 32M || true
adb shell getprop ro.product.cpu.abilist | tee -a "$sum"
adb install -r -g "$APK" 2>&1 | tail -2 | tee -a "$sum" || true
adb shell pm list packages | grep -q "$PKG" || { echo "Installazione non riuscita" | tee -a "$sum"; exit 0; }
if [ -n "${MOTORE_APK:-}" ] && [ -f "$MOTORE_APK" ]; then
	echo "motore: $(du -m "$MOTORE_APK" | cut -f1) MB" >> "$sum"
	adb install -r -g "$MOTORE_APK" 2>&1 | tail -2 | tee -a "$sum" || true
fi

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
adb shell am start -W -n "$PKG/${HOME_ACTIVITY:-eu.eurise.office.home.OfficeActivity}" | tee -a "$sum"
sleep 20
adb exec-out screencap -p > "$OUT/00-iniziale.png"
adb logcat -d > "$OUT/00-iniziale.log"
adb shell am force-stop "$PKG"
# COMPLETA=1: «Apri sempre nella modifica completa», così anche Word, Excel e OpenDocument passano dal motore.
if [ -n "${COMPLETA:-}" ]; then
	adb shell "run-as $PKG mkdir -p shared_prefs"
	echo '<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map><boolean name="sempre_completa" value="true" /></map>' |
		adb shell "run-as $PKG sh -c 'cat > shared_prefs/office.xml'"
	echo "modifica completa per tutti i documenti" >> "$sum"
fi

for f in "$HERE"/campioni/*; do
	name=$(basename "$f")
	type=$(mime "$name")
	adb push "$f" "/sdcard/Download/$name" > /dev/null
	adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d "file:///sdcard/Download/$name" > /dev/null
	adb shell "cat > /data/local/tmp/$name" < "$f"
	adb shell "run-as $PKG mkdir -p files/prova && cat /data/local/tmp/$name | run-as $PKG sh -c 'cat > files/prova/$name'"
done
sleep 5

# Traduce le righe «EuriseDiag: pila #NN pc SCOSTAMENTO LIBRERIA» con le librerie non ripulite di $SIMBOLI.
simbolizza() {
	[ -n "${SIMBOLI:-}" ] && [ -d "$SIMBOLI" ] || return 0
	local sym
	sym=$(command -v llvm-symbolizer || ls "${ANDROID_NDK_HOME:-/nonexistent}"/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-symbolizer 2>/dev/null | head -1)
	[ -n "$sym" ] || { echo "(llvm-symbolizer non trovato)"; return 0; }
	echo "--- pila tradotta ---"
	# Le righe di debuggerd («#05 pc 0000000001234abc  /data/app/.../liblo-native-code.so (…)») e quelle di EuriseDiag.
	{
		grep -oE "#[0-9]+ pc [0-9a-f]+ +[^ ]+\.so" "$1" "$2" 2>/dev/null | sed -E 's/^[^#]*#([0-9]+) pc ([0-9a-f]+) +(.*)$/pila #\1 pc \2 \3/'
		grep -oE "pila #[0-9]+ pc [0-9a-f]+ [^ ]+" "$1"
	} | awk '!seen[$0]++' | head -60 | while read -r _ n _ off lib; do
		base=$(basename "$lib")
		if [ -f "$SIMBOLI/$base" ]; then
			echo "$n $base+0x$off $("$sym" --obj="$SIMBOLI/$base" --functions=linkage --demangle "0x$off" 2>/dev/null | head -1)"
		else
			echo "$n $base+0x$off"
		fi
	done
}

open_one() { # via nome uri tipo
	local via=$1 name=$2 uri=$3 type=$4 tag="$2-$1"
	adb logcat -c; adb logcat -b crash -c
	echo "## $name ($via)" >> "$sum"
	echo '```' >> "$sum"
	adb shell am start -W -a android.intent.action.VIEW -t "$type" -d "'$uri'" \
		--grant-read-uri-permission --grant-write-uri-permission -n "$PKG/$LO" 2>&1 | tee -a "$sum"
	sleep "$WAIT"
	adb exec-out screencap -p > "$OUT/$tag.png"
	adb logcat -d > "$OUT/$tag.log"
	adb logcat -b crash -d > "$OUT/$tag-arresto.log"
	{
		echo "processo: $(adb shell pidof "$PKG" || echo 'non c\x27è più') · motore: $(adb shell pidof "$MOTORE_PKG" || echo 'nessuno')"
		echo "attività in primo piano: $(adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity' | sed 's/^ *//')"
		# Del motore: avvertimenti ed errori di coolwsd e del kit, poi l'eventuale arresto con la pila completa.
		grep -E "coolwsd.*(WRN|ERR|FTL|CRT)|LibreOfficeKit|LOActivity: (Success copying [0-9]|SCHEME|Initializing|postMobileMessage: load)|couldn't create temporary|file not found|AndroidRuntime" "$OUT/$tag.log" | head -40
		# Tutte le righe del processo di Office (messaggi di libc++abi, asserzioni, ultime righe del kit).
		for p in "$PKG" "$MOTORE_PKG"; do
			pid=$(grep -m1 -oE "Start proc [0-9]+:$p[/ ]" "$OUT/$tag.log" | grep -oE "[0-9]+" | head -1)
			[ -n "$pid" ] && awk -v p="$pid" '$3 == p' "$OUT/$tag.log" | grep -v -E "OpenGLRenderer|Success copying unpack|CoreBackPreview|HWUI|EGL|EuriseDiag: gestore" | tail -60
		done
		grep -E " F (libc|DEBUG) .*(Abort message|signal|Cause)" "$OUT/$tag.log" "$OUT/$tag-arresto.log" | cut -d: -f2- | sort -u | head -10
	} >> "$sum"
	echo "--- debuggerd ---" >> "$sum"
	grep -hE "F DEBUG +: +(#[0-9]+|backtrace|Abort|Cause|signal)" "$OUT/$tag.log" "$OUT/$tag-arresto.log" | cut -c33- | awk '!seen[$0]++' | head -50 >> "$sum"
	simbolizza "$OUT/$tag.log" "$OUT/$tag-arresto.log" >> "$sum"
	echo '```' >> "$sum"
	adb shell am force-stop "$PKG"
	adb shell am force-stop "$MOTORE_PKG"
	sleep 3
}

for f in "$HERE"/campioni/${SOLO:-*}; do
	name=$(basename "$f")
	type=$(mime "$name")
	id=$(adb shell content query --uri content://media/external/file --projection _id --where "\"_display_name='$name'\"" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | head -1)
	if [ -n "$id" ]; then
		open_one content "$name" "content://media/external/file/$id" "$type"
	else
		echo "## $name (content): non trovato in MediaStore" >> "$sum"
	fi
	# file:// solo se chiesto: in Collabora quel ramo non apre la pagina (serve un file temporaneo).
	[ -n "${CON_FILE:-}" ] && open_one file "$name" "file:///data/data/$PKG/files/prova/$name" "$type"
done

# Correzione nella vista rapida: tocca, scrive, «Fatto», e guarda se il file nel telefono è cambiato davvero.
centro() { # testo (anche parziale) -> «x y» del primo elemento sullo schermo che lo contiene
	adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
	adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep -F "text=\"$1" | head -1 |
		sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' |
		awk '{ printf "%d %d", ($1 + $3) / 2, ($2 + $4) / 2 }'
}
tocca() { local xy; xy=$(centro "$1"); [ -n "$xy" ] && adb shell input tap $xy && return 0; echo "non trovato sullo schermo: $1" >> "$sum"; return 1; }
correggi() { # nome testo-da-toccare tasti-da-cancellare testo-nuovo voce-nel-file cosa-cercare
	local name=$1 target=$2 dels=$3 typed=$4 entry=$5 expect=$6
	local id type
	type=$(mime "$name")
	id=$(adb shell content query --uri content://media/external/file --projection _id --where "\"_display_name='$name'\"" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | head -1)
	[ -n "$id" ] || return 0
	echo "## Correzione in $name" >> "$sum"
	adb shell am start -W -a android.intent.action.VIEW -t "$type" -d "'content://media/external/file/$id'" \
		--grant-read-uri-permission --grant-write-uri-permission -n "$PKG/$LO" > /dev/null 2>&1
	sleep 8
	tocca "$target" || { adb shell am force-stop "$PKG"; return 0; }
	sleep 2
	adb shell input keyevent KEYCODE_MOVE_END
	for _ in $(seq 1 "$dels"); do adb shell input keyevent KEYCODE_DEL; done
	adb shell input text "$typed"
	sleep 1
	adb exec-out screencap -p > "$OUT/$name-correzione.png"
	tocca "Fatto"
	sleep 4
	adb exec-out screencap -p > "$OUT/$name-corretto.png"
	adb shell cat "/sdcard/Download/$name" > "$OUT/$name"
	if unzip -p "$OUT/$name" "$entry" 2>/dev/null | grep -q "$expect"; then echo "file cambiato: trovato «$expect» in $entry" >> "$sum"
	else echo "**il file non contiene «$expect»**" >> "$sum"; fi
	adb shell am force-stop "$PKG"
	sleep 2
}
# Nel motore, come farebbe una persona: matita (modifica), doppio tocco sul titolo, tocco lungo. Una schermata per passo
# e gli errori della pagina web (console di Chromium) del processo del motore.
usa_motore() { # nome scheda
	local name=$1 id type W H
	type=$(mime "$name")
	id=$(adb shell content query --uri content://media/external/file --projection _id --where "\"_display_name='$name'\"" | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | head -1)
	[ -n "$id" ] || return 0
	read -r W H < <(adb shell wm size | grep -oE "[0-9]+x[0-9]+" | tail -1 | tr x ' ')
	echo "## Uso nel motore: $name (${W}x$H)" >> "$sum"
	adb logcat -c
	adb shell am start -W -a android.intent.action.VIEW -t "$type" -d "'content://media/external/file/$id'" \
		--grant-read-uri-permission --grant-write-uri-permission -n "$PKG/$LO" > /dev/null 2>&1
	sleep 12
	adb exec-out screencap -p > "$OUT/$name-uso-0-aperto.png"
	# La prima scheda della dock Eurise (Testo, Celle o Diapositiva): il suo pannello deve comparire.
	tocca "$2" || adb shell input tap $((W * 86 / 100)) $((H * 845 / 1000))
	sleep 4
	adb exec-out screencap -p > "$OUT/$name-uso-1-scheda.png"
	local x=$((W / 2)) y=$((H * 385 / 1000))
	adb shell input tap $x $y; adb shell input tap $x $y
	sleep 3
	adb exec-out screencap -p > "$OUT/$name-uso-2-doppio.png"
	adb shell input swipe $x $y $x $y 1200
	sleep 3
	adb exec-out screencap -p > "$OUT/$name-uso-3-lungo.png"
	adb logcat -d > "$OUT/$name-uso.log"
	{
		echo '```'
		grep -E "chromium|Console|Uncaught|TypeError|ReferenceError|\.js:[0-9]+" "$OUT/$name-uso.log" | grep -v "EuriseDiag" | cut -c1-260 | tail -40
		echo '```'
	} >> "$sum"
	adb shell am force-stop "$PKG"
	adb shell am force-stop "$MOTORE_PKG"
	sleep 2
}
if [ -n "${COMPLETA:-}" ]; then
	usa_motore slide.pptx Diapositiva
	usa_motore lettera.docx Testo
	usa_motore conti.xlsx Celle
fi

if [ -z "${SOLO:-}" ] && [ -z "${COMPLETA:-}" ] && [ "$PKG" = eu.eurise.office.debug ]; then
	correggi lettera.docx "Gentile cliente" 1 "%sCORRETTO." word/document.xml "IBAN IT60X0542811101000000123456 CORRETTO."
	correggi conti.xlsx "750" 3 "800" xl/worksheets/sheet1.xml "<v>1260</v>"
	correggi testo.odt "Testo ODF" 1 "%sCORRETTO." content.xml "CORRETTO."
fi
cat "$sum"

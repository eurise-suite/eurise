#!/system/bin/sh
# «run-as» per le prove sull'emulatore, ora che gli APK di prova non sono più «debuggable» (docs/SICUREZZA.md,
# «APK di prova non debuggable»). Gira sull'emulatore con «adb root» (immagini google_apis), mai sul telefono.
# Uso come run-as: run-as-root <pacchetto> <comando…>, dentro la cartella dei dati dell'app.
# Dopo il comando, ciò che è nuovo o cambiato nella cartella riprende proprietario ed etichetta SELinux dell'app,
# così l'app lo legge come se l'avesse scritto lei (come succedeva con run-as).
pkg="$1"
shift
d="/data/user/0/$pkg"
[ -d "$d" ] || { echo "run-as-root: pacchetto sconosciuto: $pkg" >&2; exit 1; }
own=$(stat -c %u "$d")
ctx=$(ls -Zd "$d" | awk '{print $1}')
segno=/data/local/tmp/.run-as-root-segno
touch "$segno"
cd "$d" || exit 1
"$@"
esito=$?
find "$d" -newer "$segno" 2>/dev/null | while IFS= read -r f; do
  chown "$own:$own" "$f" 2>/dev/null
  chcon "$ctx" "$f" 2>/dev/null
done
exit $esito

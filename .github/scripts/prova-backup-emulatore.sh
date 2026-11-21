#!/usr/bin/env bash
# Prova vera del backup sull'emulatore (area Backup, 21 novembre 2026; docs/stato/BACKUP.md).
# Backup → dati cancellati (Eurise, Password, Galleria, Posta, Office, Browser) e Tastiera tolta → ripristino → Tastiera di nuovo → tutto torna.
# Le app sono in apk/ (eu.eurise.hub.debug.apk, eu.eurise.keyboard.debug.apk, eu.eurise.password.debug.apk,
# eu.eurise.gallery.debug.apk), la sonda in sonda.apk.
set -u
E=eu.eurise.hub.debug
T=eu.eurise.keyboard.debug
P=eu.eurise.password.debug
G=eu.eurise.gallery.debug
M=eu.eurise.messages.debug
ML=eu.eurise.mail.debug
O=eu.eurise.office.debug
B=eu.eurise.browser.debug
S=eu.eurise.prova.backup/.Sonda
SP=eu.eurise.prova.backup
DAVROOT=$PWD/dav/root
fallite=0
esito() { # nome, condizione già valutata (0 = bene)
  if [ "$2" -eq 0 ]; then echo "✔ $1"; else echo "✘ $1"; fallite=$((fallite + 1)); fi
}
sonda() { # fase
  out=$(adb shell am instrument -w -e fase "$1" "$S" 2>&1)
  echo "$out" | sed -n 's/^INSTRUMENTATION_RESULT: dettagli=//p' | tr '|' '\n' | sed '/^$/d;s/^/  · /'
  echo "$out" | grep -q 'INSTRUMENTATION_RESULT: esito=ok'
  r=$?
  [ $r -ne 0 ] && echo "$out" | grep -E 'esito=|INSTRUMENTATION_FAILED|Exception' | head -5
  return $r
}

# WebDAV finto sul computer (10.0.2.2 per l'emulatore), HTTPS con un certificato fatto qui (.github/prova-backup/dav.py).
mkdir -p dav && rm -rf "$DAVROOT" dav/guasta
openssl req -x509 -newkey rsa:2048 -nodes -keyout dav/chiave.pem -out dav/cert.pem -days 2 -subj "/CN=10.0.2.2" \
  -addext "subjectAltName=IP:10.0.2.2" 2>/dev/null
python3 -I .github/prova-backup/dav.py "$DAVROOT" 8443 dav/cert.pem dav/chiave.pem 2>dav/registro.txt &
DAVPID=$!
trap 'kill $DAVPID 2>/dev/null; adb shell dumpsys battery reset >/dev/null 2>&1' EXIT

adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 2; done'
adb install -r -g apk/$E.apk >/dev/null && adb install -r -g apk/$T.apk >/dev/null && adb install -r -g apk/$P.apk >/dev/null &&
  adb install -r -g apk/$G.apk >/dev/null && adb install -r -g apk/$M.apk >/dev/null && adb install -r -g apk/$ML.apk >/dev/null &&
  adb install -r -g apk/$O.apk >/dev/null && adb install -r -g apk/$B.apk >/dev/null && adb install -r sonda.apk >/dev/null
esito "installate Eurise, Tastiera, Password, Galleria, Messaggi, Posta, Office, Browser e sonda" $?

# Eurise parte una volta, così crea il suo database; poi si ferma.
adb shell am start -W -n $E/eu.eurise.hub.MainActivity >/dev/null 2>&1
sleep 15
adb shell am force-stop $E
adb shell run-as $E ls databases/eurise.db >/dev/null 2>&1
esito "il database di Eurise esiste" $?

# Una parola «imparata» della Tastiera, da ritrovare dopo.
adb shell "run-as $T sh -c 'mkdir -p files && echo parola-di-prova > files/UserHistoryDictionary.prova'"
adb shell run-as $T cat files/UserHistoryDictionary.prova | grep -q parola-di-prova
esito "parola di prova piantata nella Tastiera" $?

# Una cassaforte e una foto protetta «finte» (i fornitori copiano i file così come sono).
adb shell "run-as $P sh -c 'mkdir -p files && echo finta-cassaforte > files/cassaforte.kdbx'"
adb shell "run-as $G sh -c 'mkdir -p files/secure && echo intestazione-di-prova > files/secure/header.json && echo foto-cifrata > files/secure/prova.enc'"
adb shell run-as $G cat files/secure/prova.enc | grep -q foto-cifrata
esito "cassaforte e foto protetta di prova piantate" $?
# Una casella di posta (con la sua password chiusa, che nel backup non deve entrare) e un documento.
pianta() { # app, file locale, percorso nell'app
  adb push "$2" /data/local/tmp/pianta >/dev/null && adb shell chmod 644 /data/local/tmp/pianta
  adb shell "run-as $1 sh -c 'mkdir -p \$(dirname $3) && cat /data/local/tmp/pianta > $3'"
  adb shell rm /data/local/tmp/pianta
}
echo '[{"id":"1","email":"prova@esempio.eu","name":"Prova","label":"Esempio","imap":{"host":"imap.esempio.eu","port":993,"sec":"Ssl"},"smtp":{"host":"smtp.esempio.eu","port":465,"sec":"Ssl"},"user":"","secret":"SEGRETO-DEL-KEYSTORE","tint":2}]' > casella.json
echo documento-di-prova > documento.txt
pianta $ML casella.json files/accounts.json
pianta $O documento.txt files/documenti/prova.txt
adb shell run-as $ML cat files/accounts.json | grep -q prova@esempio.eu && adb shell run-as $O cat files/documenti/prova.txt | grep -q documento-di-prova
esito "casella e documento di prova piantati" $?
# Il Browser di prova è compilato come la versione ufficiale (non «debuggable»): niente run-as, quindi qui si prova
# solo che esporti e riceva; il contenuto (unione dei segnalibri) va guardato sul telefono.

# Un'app senza il permesso della suite (qui la shell) non legge né scrive i dati del backup.
for a in $P $G $T $M $ML $O $B; do
  out=$(adb shell content read --uri content://$a.eurise.backup/export 2>&1 | head -c 400)
  echo "$out" | grep -qiE 'SecurityException|Permission Denial|requires'; esito "$a: «export» rifiutato a chi non è della suite" $?
done
out=$(adb shell content call --uri content://$P.eurise.backup --method apply 2>&1 | head -c 400)
echo "$out" | grep -qiE 'SecurityException|Permission Denial|solo le app della suite'; esito "Password: «apply» rifiutato a chi non è della suite" $?

echo "— Backup"
sonda backup; esito "backup cifrato con note, attività, impostazione e Tastiera; niente zip in chiaro in Eurise" $?
adb shell run-as $T ls cache 2>/dev/null | grep -q 'eurise-backup'
[ $? -ne 0 ]; esito "nella cache della Tastiera nessun file del backup" $?
adb exec-out run-as $E cat cache/prova.eurb > prova.eurb
head -c 8 prova.eurb | grep -q EURISEB1; esito "il file del backup esce dal telefono cifrato (EURISEB1)" $?
grep -c "Prova backup" prova.eurb >/dev/null; [ $? -ne 0 ]; esito "nel file non si legge «Prova backup»" $?

echo "— Telefono nuovo: dati di Eurise, Password e Galleria cancellati, Tastiera tolta"
adb shell pm clear $E >/dev/null; esito "dati di Eurise cancellati" $?
adb shell pm clear $P >/dev/null && adb shell pm clear $G >/dev/null && adb shell pm clear $ML >/dev/null &&
  adb shell pm clear $O >/dev/null && adb shell pm clear $B >/dev/null; esito "dati di Password, Galleria, Posta, Office e Browser cancellati" $?
adb uninstall $T >/dev/null; esito "Tastiera disinstallata" $?
# Sul telefono nuovo c'è già una cartella protetta tua, con un'altra frase: quella del backup deve andare accanto.
adb shell "run-as $G sh -c 'mkdir -p files/secure && echo mia-intestazione > files/secure/header.json && echo mia-foto > files/secure/mia.enc'"
adb shell run-as $G cat files/secure/mia.enc | grep -q mia-foto
esito "cartella protetta diversa già sul telefono nuovo" $?
adb push prova.eurb /data/local/tmp/prova.eurb >/dev/null && adb shell chmod 644 /data/local/tmp/prova.eurb
adb shell "run-as $E sh -c 'mkdir -p cache && cat /data/local/tmp/prova.eurb > cache/prova.eurb'"
adb shell rm /data/local/tmp/prova.eurb

echo "— Ripristino"
sonda ripristino; esito "frase sbagliata rifiutata, codice e frase aprono, dati delle app in attesa sigillati, ripristino pronto" $?
adb shell run-as $E ls files/restore_pending >/dev/null 2>&1; esito "il ripristino aspetta che Eurise riparta" $?
# Il riavvio vero: Eurise si apre (all'avvio, prima del database, il ripristino prende il suo posto) e si chiude.
adb shell am start -W -n $E/eu.eurise.hub.MainActivity >/dev/null 2>&1
sleep 12
adb shell am force-stop $E
adb shell run-as $E ls files/restore_pending >/dev/null 2>&1
[ $? -ne 0 ]; esito "riaprendo Eurise il ripristino prende il suo posto" $?
sonda riavvio; esito "dopo il riavvio note, attività e impostazione sono tornate, Bentornato segnato" $?

echo "— La Tastiera arriva dopo"
adb install -r -g apk/$T.apk >/dev/null; esito "Tastiera reinstallata" $?
sonda consegna; esito "dati consegnati a tutte le app pronte; Messaggi (non predefinita) aspetta, sigillato" $?
sleep 2
adb shell run-as $P cat files/cassaforte.kdbx 2>/dev/null | grep -q finta-cassaforte
esito "la cassaforte è tornata in Password" $?
adb shell run-as $G cat files/secure-dal-backup/prova.enc 2>/dev/null | grep -q foto-cifrata &&
  adb shell run-as $G cat files/secure-dal-backup/header.json 2>/dev/null | grep -q intestazione-di-prova
esito "la foto protetta è tornata nella Galleria, accanto («dal backup»), con la sua intestazione" $?
adb shell run-as $G cat files/secure/mia.enc 2>/dev/null | grep -q mia-foto &&
  adb shell run-as $G cat files/secure/header.json 2>/dev/null | grep -q mia-intestazione
esito "la cartella protetta che c'era resta com'era" $?
adb shell run-as $G ls files/secure/prova.enc >/dev/null 2>&1
[ $? -ne 0 ]; esito "le due cartelle non si mescolano" $?
adb shell run-as $ML cat files/accounts_dal_backup.json 2>/dev/null | grep -q prova@esempio.eu
esito "la casella di posta è tornata, da ricollegare" $?
adb shell run-as $ML cat files/accounts_dal_backup.json 2>/dev/null | grep -q SEGRETO
[ $? -ne 0 ]; esito "la password chiusa della casella non è passata dal backup" $?
adb shell run-as $O cat files/documenti/prova.txt 2>/dev/null | grep -q documento-di-prova
esito "il documento è tornato in Office" $?

adb shell run-as $T cat files/UserHistoryDictionary.prova 2>/dev/null | grep -q parola-di-prova
esito "la parola di prova è tornata nella Tastiera" $?
adb shell run-as $T ls cache 2>/dev/null | grep -q 'eurise-backup'
[ $? -ne 0 ]; esito "nella cache della Tastiera nessun file del backup, anche dopo" $?

echo "— Il backup come lo fa l'app: chiave nel Keystore, automatico con Wi-Fi e carica, cartella, WebDAV, i cinque"
pianta $E dav/cert.pem cache/prova-dav.pem
adb shell am broadcast -n $SP/.Concedi 2>&1 | grep -q 'data="content://'; esito "la cartella di prova è concessa a Eurise (come dal selettore)" $?
adb shell dumpsys battery unplug
sleep 2
sonda gestore-inizio; esito "backup acceso, chiave sigillata dal Keystore, cartella scelta, automatico in coda con Wi-Fi e carica" $?
# La sonda parte fermando Eurise, e Android allora toglie i suoi lavori pianificati: come dopo «Forza l'arresto», WorkManager
# li rimette alla prima apertura. Eurise si apre e resta in background, come su un telefono vero.
adb shell am start -W -n $E/eu.eurise.hub.MainActivity >/dev/null 2>&1
sleep 8
adb shell input keyevent 3
sleep 20
n=$(adb shell run-as $SP ls files/cartella 2>/dev/null | grep -c 'eurisebk')
[ "$n" -eq 0 ]; esito "senza carica il backup automatico non parte" $?
adb shell dumpsys battery reset
fatto=1
for i in $(seq 1 36); do
  adb shell run-as $E cat shared_prefs/backup.xml 2>/dev/null | grep -q 'name="last_at"' && { fatto=0; break; }
  # Se JobScheduler tarda, gli si chiede di provare: parte solo se i vincoli (rete e carica) ci sono.
  if [ "$i" = 12 ]; then
    jid=$(adb shell dumpsys jobscheduler $E 2>/dev/null | grep -oE 'JOB #u0a[0-9]+/[0-9]+' | head -1 | sed 's#.*/##')
    [ -n "$jid" ] && adb shell cmd jobscheduler run $E "$jid" >/dev/null 2>&1
  fi
  sleep 5
done
esito "in carica il backup automatico parte da solo" $fatto
if [ $fatto -ne 0 ]; then
  echo "  · rete: $(adb shell dumpsys connectivity 2>/dev/null | grep -m1 -oE 'NOT_METERED|METERED' || echo ?)"
  echo "  · batteria: $(adb shell dumpsys battery 2>/dev/null | grep -E 'AC powered|status' | tr -s ' \n' ' ')"
  adb shell dumpsys jobscheduler $E 2>/dev/null | grep -iE 'JOB #|Required constraints|Satisfied constraints|Unsatisfied|Ready' | head -12 | sed 's/^/  · /'
fi
sleep 3
n=$(adb shell run-as $SP ls files/cartella 2>/dev/null | grep -c -- '-r\.eurisebk')
[ "$n" -eq 1 ]; esito "il backup è nella cartella, uno solo" $?
adb exec-out run-as $SP sh -c 'cat files/cartella/*-r.eurisebk' 2>/dev/null | head -c 8 | grep -q EURISEB1
esito "nella cartella il backup è cifrato (EURISEB1)" $?
sonda gestore-cartella; esito "dalla cartella il backup si elenca e si riapre con la frase" $?

# Cinque backup «vecchi» già sul WebDAV: tre recenti, un mensile, un trimestrale (le date sono nei nomi).
B=$DAVROOT/Eurise/Backup
mkdir -p "$B"
nome() { echo "eurise-$(date -u -d "$1" +%Y%m%d-%H%M%S)-$2.eurisebk"; }
R1=$(nome "-1 day" r); R2=$(nome "-2 days" r); R3=$(nome "-3 days" r); MM=$(nome "-50 days" m); QQ=$(nome "-170 days" q)
for f in $R1 $R2 $R3 $MM $QQ; do echo "vecchio backup $f" > "$B/$f"; done
sonda gestore-dav; esito "backup sul WebDAV (HTTPS), riscaricato e riaperto; la chiave dei dati non è in chiaro sul telefono" $?
ls "$B" | sort > dav/dopo.txt
cat dav/dopo.txt | sed 's/^/  · /'
nuovo=$(grep -- '-r\.eurisebk' dav/dopo.txt | grep -vxF -e "$R1" -e "$R2" -e "$R3")
[ -n "$nuovo" ] && head -c 8 "$B/$nuovo" | grep -q EURISEB1; esito "il nuovo backup è sul WebDAV, cifrato" $?
atteso=$(printf '%s\n' "$nuovo" "$R1" "$R2" "${R3%-r.eurisebk}-m.eurisebk" "${MM%-m.eurisebk}-q.eurisebk" | sort)
[ "$(cat dav/dopo.txt)" = "$atteso" ]; esito "regola dei cinque: tre recenti, il più vecchio diventa mensile, il mensile trimestrale, il trimestrale va via" $?

touch dav/guasta
sonda gestore-guasto; esito "un backup che non si rilegge uguale non è dato per buono" $?
rm -f dav/guasta
ls "$B" | sort > dav/dopo-guasto.txt
cmp -s dav/dopo.txt dav/dopo-guasto.txt; esito "e i cinque di prima restano com'erano (quello non verificato non resta)" $?
sonda gestore-fine; esito "spento: chiave tolta dal Keystore, niente più backup automatico" $?

echo
if [ $fallite -eq 0 ]; then echo "Prova del backup: tutto bene"; else echo "Prova del backup: $fallite controlli non riusciti"; fi
exit $fallite

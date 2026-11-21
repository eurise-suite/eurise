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
# Una casella di posta (con la sua password chiusa, che nel backup non deve entrare), un documento, un segnalibro.
pianta() { # app, file locale, percorso nell'app
  adb push "$2" /data/local/tmp/pianta >/dev/null && adb shell chmod 644 /data/local/tmp/pianta
  adb shell "run-as $1 sh -c 'mkdir -p \$(dirname $3) && cat /data/local/tmp/pianta > $3'"
  adb shell rm /data/local/tmp/pianta
}
echo '[{"id":"1","email":"prova@esempio.eu","name":"Prova","label":"Esempio","imap":{"host":"imap.esempio.eu","port":993,"sec":"Ssl"},"smtp":{"host":"smtp.esempio.eu","port":465,"sec":"Ssl"},"user":"","secret":"SEGRETO-DEL-KEYSTORE","tint":2}]' > casella.json
echo documento-di-prova > documento.txt
echo '[{"title":"Prova","url":"https://esempio.eu/prova","folder":"","pinned":false}]' > segnalibri.json
pianta $ML casella.json files/accounts.json
pianta $O documento.txt files/documenti/prova.txt
pianta $B segnalibri.json files/bookmarks.json
adb shell run-as $ML cat files/accounts.json | grep -q prova@esempio.eu && adb shell run-as $O cat files/documenti/prova.txt | grep -q documento-di-prova &&
  adb shell run-as $B cat files/bookmarks.json | grep -q esempio.eu/prova
esito "casella, documento e segnalibro di prova piantati" $?

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
adb push prova.eurb /data/local/tmp/prova.eurb >/dev/null && adb shell chmod 644 /data/local/tmp/prova.eurb
adb shell "run-as $E sh -c 'mkdir -p cache && cat /data/local/tmp/prova.eurb > cache/prova.eurb'"
adb shell rm /data/local/tmp/prova.eurb

echo "— Ripristino"
sonda ripristino; esito "frase sbagliata rifiutata, codice e frase aprono, tutto tornato, dati delle app in attesa sigillati" $?

echo "— La Tastiera arriva dopo"
adb install -r -g apk/$T.apk >/dev/null; esito "Tastiera reinstallata" $?
sonda consegna; esito "dati consegnati a tutte le app pronte; Messaggi (non predefinita) aspetta, sigillato" $?
sleep 2
adb shell run-as $P cat files/cassaforte.kdbx 2>/dev/null | grep -q finta-cassaforte
esito "la cassaforte è tornata in Password" $?
adb shell run-as $G cat files/secure/prova.enc 2>/dev/null | grep -q foto-cifrata
esito "la foto protetta è tornata nella Galleria" $?
adb shell run-as $ML cat files/accounts_dal_backup.json 2>/dev/null | grep -q prova@esempio.eu
esito "la casella di posta è tornata, da ricollegare" $?
adb shell run-as $ML cat files/accounts_dal_backup.json 2>/dev/null | grep -q SEGRETO
[ $? -ne 0 ]; esito "la password chiusa della casella non è passata dal backup" $?
adb shell run-as $O cat files/documenti/prova.txt 2>/dev/null | grep -q documento-di-prova
esito "il documento è tornato in Office" $?
adb shell run-as $B cat files/bookmarks.json 2>/dev/null | grep -q esempio.eu/prova
esito "il segnalibro è tornato nel Browser" $?
adb shell run-as $T cat files/UserHistoryDictionary.prova 2>/dev/null | grep -q parola-di-prova
esito "la parola di prova è tornata nella Tastiera" $?
adb shell run-as $T ls cache 2>/dev/null | grep -q 'eurise-backup'
[ $? -ne 0 ]; esito "nella cache della Tastiera nessun file del backup, anche dopo" $?

echo
if [ $fallite -eq 0 ]; then echo "Prova del backup: tutto bene"; else echo "Prova del backup: $fallite controlli non riusciti"; fi
exit $fallite

#!/usr/bin/env bash
# Prova vera del backup sull'emulatore (area Backup, 21 novembre 2026; docs/stato/BACKUP.md).
# Backup → dati cancellati e Tastiera tolta → ripristino → Tastiera di nuovo → le sue parole tornano.
# Le app sono in apk/ (eu.eurise.hub.debug.apk, eu.eurise.keyboard.debug.apk), la sonda in sonda.apk.
set -u
E=eu.eurise.hub.debug
T=eu.eurise.keyboard.debug
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
adb install -r -g apk/$E.apk >/dev/null && adb install -r -g apk/$T.apk >/dev/null && adb install -r sonda.apk >/dev/null
esito "installate Eurise, Tastiera e sonda" $?

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

echo "— Backup"
sonda backup; esito "backup cifrato con note, attività, impostazione e Tastiera; niente zip in chiaro in Eurise" $?
adb shell run-as $T ls cache 2>/dev/null | grep -q 'eurise-backup'
[ $? -ne 0 ]; esito "nella cache della Tastiera nessun file del backup" $?
adb exec-out run-as $E cat cache/prova.eurb > prova.eurb
head -c 8 prova.eurb | grep -q EURISEB1; esito "il file del backup esce dal telefono cifrato (EURISEB1)" $?
grep -c "Prova backup" prova.eurb >/dev/null; [ $? -ne 0 ]; esito "nel file non si legge «Prova backup»" $?

echo "— Telefono nuovo: dati di Eurise cancellati, Tastiera tolta"
adb shell pm clear $E >/dev/null; esito "dati di Eurise cancellati" $?
adb uninstall $T >/dev/null; esito "Tastiera disinstallata" $?
adb push prova.eurb /data/local/tmp/prova.eurb >/dev/null && adb shell chmod 644 /data/local/tmp/prova.eurb
adb shell "run-as $E sh -c 'mkdir -p cache && cat /data/local/tmp/prova.eurb > cache/prova.eurb'"
adb shell rm /data/local/tmp/prova.eurb

echo "— Ripristino"
sonda ripristino; esito "frase sbagliata rifiutata, codice e frase aprono, tutto tornato, Tastiera in attesa sigillata" $?

echo "— La Tastiera arriva dopo"
adb install -r -g apk/$T.apk >/dev/null; esito "Tastiera reinstallata" $?
sonda consegna; esito "parole in attesa consegnate alla Tastiera" $?
sleep 2
adb shell run-as $T cat files/UserHistoryDictionary.prova 2>/dev/null | grep -q parola-di-prova
esito "la parola di prova è tornata nella Tastiera" $?
adb shell run-as $T ls cache 2>/dev/null | grep -q 'eurise-backup'
[ $? -ne 0 ]; esito "nella cache della Tastiera nessun file del backup, anche dopo" $?

echo
if [ $fallite -eq 0 ]; then echo "Prova del backup: tutto bene"; else echo "Prova del backup: $fallite controlli non riusciti"; fi
exit $fallite

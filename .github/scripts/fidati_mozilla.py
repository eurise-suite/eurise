#!/usr/bin/env python3
"""GeckoView: fidarsi della chiave di Mozilla per tutte le versioni (docs/SICUREZZA.md, «Impronte delle librerie»).

Gradle, scrivendo le impronte, lega la chiave «Mozilla Software Releases» alla sola versione di GeckoView di quel
giorno. Ma il Browser prende sempre l'ultima GeckoView (circa ogni quattro settimane): con la verifica bloccante la
versione nuova si fermerebbe finché qualcuno non riscrive le impronte. Qui la chiave diventa fidata per tutto il gruppo
org.mozilla.geckoview: una versione nuova passa solo se è firmata da Mozilla. Si lancia dopo ogni scrittura del file.
"""
import re
import sys

MOZILLA = "827E658608679618CD349F93678E455D76767AA3"
path = sys.argv[1] if len(sys.argv) > 1 else "gradle/verification-metadata.xml"
s = open(path, encoding="utf-8").read()
block = re.compile(r'[ \t]*<trusted-key id="%s"(?:\s*/>|[^>]*>.*?</trusted-key>)\n' % MOZILLA, re.S)
wanted = '         <trusted-key id="%s" group="org.mozilla.geckoview"/>\n' % MOZILLA
if block.search(s):
    s = block.sub(wanted, s, count=1)
else:
    s = s.replace("      </trusted-keys>", wanted + "      </trusted-keys>", 1)
if wanted not in s:
    sys.exit("chiave di Mozilla non scritta in " + path)
open(path, "w", encoding="utf-8").write(s)
print("GeckoView: chiave di Mozilla fidata per tutte le versioni")

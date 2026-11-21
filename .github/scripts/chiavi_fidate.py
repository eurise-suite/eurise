#!/usr/bin/env python3
"""Nessuna chiave fidata sparisce quando Gradle riscrive le impronte (docs/SICUREZZA.md, «Impronte delle librerie»).

Il 21 novembre 2026 due giri del flusso hanno perso chiavi fidate di AndroidX, Apache e ASM: un errore nella ricerca
di fidati_mozilla.py, che correva fino al blocco dopo. La verifica bloccante ha fermato Compila (giri 914 e 917). Qui
ogni <trusted-key> che c'era nella versione precedente del file e manca nella nuova torna com'era: un paracadute per
qualunque causa. Una chiave da
togliere davvero si toglie a mano, con un commit che lo spiega.

Uso: chiavi_fidate.py <file precedente> <file nuovo>
"""
import re
import sys

old_path, new_path = sys.argv[1], sys.argv[2]
old = open(old_path, encoding="utf-8").read()
new = open(new_path, encoding="utf-8").read()
key = re.compile(r'[ \t]*<trusted-key id="([0-9A-F]+)"(?:[^>]*/>|[^>/]*>.*?</trusted-key>)\n', re.S)
present = {m.group(1) for m in key.finditer(new)}
missing = [m.group(0) for m in key.finditer(old) if m.group(1) not in present]
if missing:
    new = new.replace("      </trusted-keys>", "".join(missing) + "      </trusted-keys>", 1)
    open(new_path, "w", encoding="utf-8").write(new)
for block in missing:
    print("rimessa:", key.match(block).group(1))
print(f"chiavi fidate: {len(present) + len(missing)} ({len(missing)} rimesse)")

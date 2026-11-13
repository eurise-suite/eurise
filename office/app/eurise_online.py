#!/usr/bin/env python3
"""Correzioni al sorgente di Collabora Online (kit e comune) per il motore di Eurise Office.

Uso: python3 office/app/eurise_online.py <cartella di online>   (subito dopo il checkout, prima di autogen)

Chiusura del motore trovata il 13 novembre 2026 con la diagnosi (office/app/diag): SIGSEGV nel thread kitbroker in
UnitKit::get(), chiamata da Document::drainQueue() appena creata la sessione. Su mobile nessuno chiama UnitWSD::init
(in COOLWSD::innerInitialize sta dentro «#if !MOBILEAPP»), quindi la tabella dei test di unità resta vuota e
UnitKit::get() legge GlobalArray[-1]. Le altre chiamate del kit sono già protette con «if (!Util::isMobileApp())»;
questa no. Due correzioni:
1. drainQueue salta il filtro dei test su mobile, come fanno le altre chiamate;
2. UnitKit::get() su mobile, se la tabella è vuota, restituisce un UnitKit vuoto (nessun test, tutti i filtri spenti)
   invece di leggere fuori dalla memoria: rete di sicurezza per chiamate simili in versioni future.
Ogni sostituzione deve riuscire: se Collabora cambia quelle righe, lo script si ferma e lo dice.
"""
import sys
from pathlib import Path

online = Path(sys.argv[1]).resolve()


def patch(rel: str, old: str, new: str) -> None:
    f = online / rel
    s = f.read_text()
    if new in s:
        return
    if s.count(old) != 1:
        sys.exit(f"eurise_online: in {rel} non trovo una e una sola volta:\n{old}")
    f.write_text(s.replace(old, new))
    print(f"eurise_online: {rel} corretto")


patch(
    "kit/Kit.cpp",
    "    if (UnitKit::get().filterDrainQueue())\n",
    "    if (!Util::isMobileApp() && UnitKit::get().filterDrainQueue())\n",
)

patch(
    "common/Unit.cpp",
    "UnitKit& UnitKit::get()\n{\n    assert(GlobalArray[GlobalIndex]);\n",
    "UnitKit& UnitKit::get()\n{\n"
    "    if constexpr (Util::isMobileApp())\n"
    "    {\n"
    "        // Eurise: su mobile UnitBase::init non viene chiamata, la tabella dei test può essere vuota.\n"
    "        if (GlobalArray == nullptr || GlobalIndex < 0 || GlobalArray[GlobalIndex] == nullptr)\n"
    "        {\n"
    "            static UnitKit noTests(\"UnitKit\");\n"
    "            return noTests;\n"
    "        }\n"
    "    }\n"
    "    assert(GlobalArray[GlobalIndex]);\n",
)

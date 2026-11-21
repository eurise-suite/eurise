#!/usr/bin/env python3
"""Controlla data/spam/elenco.txt prima che venga pubblicato (stesse regole di core/suite SpamList.kt).

Si ferma (uscita 1) se manca l'intestazione, se il file è troppo grande, se una riga non è nel formato, se una voce è
doppia o se segna un paese europeo intero o un prefisso troppo corto in Italia: una voce sbagliata fa perdere chiamate
vere a tutti.
"""
import re
import sys

HEADER = "# eurise-spam 1"
MAX_BYTES = 400_000
NUMBER = re.compile(r"^\+\d{2,15}$")
CATEGORIES = {"wangiri", "callcenter", "truffa", "molesto"}
# Prefissi dei paesi europei (e vicini con molte chiamate vere): mai interi, solo numeri o sottoprefissi.
EUROPE = {"30", "31", "32", "33", "34", "36", "39", "40", "41", "43", "44", "45", "46", "47", "48", "49", "350", "351",
          "352", "353", "354", "356", "357", "358", "359", "370", "371", "372", "376", "377", "378", "380", "381", "385",
          "386", "387", "420", "421", "423"}


def main(path: str) -> int:
    raw = open(path, "rb").read()
    errors = []
    if len(raw) > MAX_BYTES:
        errors.append(f"file troppo grande: {len(raw)} byte (tetto {MAX_BYTES})")
    text = raw.decode("utf-8")
    if not text.lstrip("﻿").startswith(HEADER):
        errors.append(f"la prima riga deve essere «{HEADER}»")
    seen = {}
    count = 0
    for n, line in enumerate(text.splitlines(), 1):
        l = line.strip()
        if not l or l.startswith("#"):
            continue
        parts = l.split("\t")
        number = parts[0].strip()
        if not NUMBER.match(number):
            errors.append(f"riga {n}: «{number}» non è un numero con il + (separare con una tabulazione)")
            continue
        if len(parts) < 2 or parts[1].strip() not in CATEGORIES:
            errors.append(f"riga {n}: categoria mancante o sconosciuta (una di {', '.join(sorted(CATEGORIES))})")
        digits = number[1:]
        if digits in EUROPE:
            errors.append(f"riga {n}: {number} è un paese europeo intero: troppe chiamate vere")
        if digits.startswith("39") and len(digits) < 6:
            errors.append(f"riga {n}: {number} è un prefisso italiano troppo corto (almeno 4 cifre dopo il 39)")
        if digits in seen:
            errors.append(f"riga {n}: {number} c'è già alla riga {seen[digits]}")
        seen[digits] = n
        count += 1
    for e in errors:
        print("ERRORE", e)
    print(f"{count} voci, {len(raw)} byte")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "data/spam/elenco.txt"))

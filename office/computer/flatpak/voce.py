#!/usr/bin/env python3
"""Voce nel menu delle app di Eurise Office (eu.eurise.Office.desktop), con i tipi di file di quella di Collabora.

Uso: voce.py <desktop di Collabora, anche mancante> <desktop da scrivere>
Lo usano il Flatpak della strada 1 (costruisci.sh) e la ricetta dai sorgenti (strada 2, ricetta.py).
"""
import sys
from pathlib import Path

orig, out = Path(sys.argv[1]), Path(sys.argv[2])
mimes = ''
if orig.is_file():
    for line in orig.read_text(encoding='utf-8').splitlines():
        if line.startswith('MimeType='):
            mimes = line.strip()
            break
lines = [
    '[Desktop Entry]',
    'Type=Application',
    'Name=Eurise Office',
    'GenericName=Documenti, fogli e presentazioni',
    'GenericName[en]=Documents, spreadsheets and presentations',
    'Comment=Scrivi, calcola e presenta: privato, libero, europeo',
    'Comment[en]=Write, calculate and present: private, free, European',
    'Icon=eu.eurise.Office',
    'Exec=coda-qt %U',
    'Terminal=false',
    'StartupNotify=true',
    'StartupWMClass=coda-qt',
    'Categories=Office;WordProcessor;Spreadsheet;Presentation;',
    'Keywords=documento;foglio;presentazione;docx;xlsx;pptx;odt;ods;odp;pdf;',
]
if mimes:
    lines.append(mimes)
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text('\n'.join(lines) + '\n', encoding='utf-8')
print(f'{out.name}: {len(mimes.split(";")) - 1 if mimes else 0} tipi di file')

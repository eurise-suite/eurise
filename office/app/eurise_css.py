#!/usr/bin/env python3
"""Mette i colori Eurise (office/app/eurise.css) in coda al foglio di stile dell'interfaccia di Collabora Online.

Uso: python3 office/app/eurise_css.py <cartella di online>   (prima di make: l'interfaccia si compila con make)
"""
import sys
from pathlib import Path

online = Path(sys.argv[1])
css = online / "browser" / "css" / "color-palette.css"
if not css.exists():
    sys.exit(f"eurise_css: non trovo {css}")
mark = "/* Eurise */"
text = css.read_text()
if mark not in text:
    css.write_text(text + "\n" + mark + "\n" + (Path(__file__).resolve().parent / "eurise.css").read_text())
print("eurise_css: colori Eurise sull'interfaccia web")

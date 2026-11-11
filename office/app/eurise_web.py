#!/usr/bin/env python3
"""Mette le parti Eurise nell'interfaccia web di Collabora Online (vedi office/app/EURISE.md).

Uso: python3 office/app/eurise_web.py <cartella di online>   (prima di ./autogen.sh: tocca browser/Makefile.am)

- web/Control.Eurise.js → browser/src/control/, nella lista dei JS subito dopo Control.NotebookbarBuilder.js
  (scheda Suite del nastro, «Cerca un comando» con Ctrl K, pulsante Suite sul telefono);
- web/eurise.css → browser/css/, ultimo foglio di stile (vince sui colori di Collabora, anche quelli scuri);
- web/images/ → browser/images/ (icone della Suite, chiare e scure, dai tracciati di EuriseIcons).
Ogni sostituzione deve riuscire: se Collabora cambia quelle righe, lo script si ferma e lo dice.
"""
import shutil
import sys
from pathlib import Path

online = Path(sys.argv[1]).resolve()
browser = online / "browser"
web = Path(__file__).resolve().parent / "web"
makefile = browser / "Makefile.am"
if not makefile.exists():
    sys.exit(f"eurise_web: non trovo {makefile}")
m = makefile.read_text()


def insert_after(text: str, anchor: str, line: str) -> str:
    if line in text:
        return text
    if anchor not in text:
        sys.exit(f"eurise_web: non trovo «{anchor.strip()}» in browser/Makefile.am")
    return text.replace(anchor, anchor + line, 1)


m = insert_after(m, "\tsrc/control/Control.NotebookbarBuilder.js \\\n", "\tsrc/control/Control.Eurise.js \\\n")
last_css = "\t$(srcdir)/css/settings.css\n"
if "css/eurise.css" not in m:
    if last_css not in m:
        sys.exit("eurise_web: non trovo l'ultimo foglio di stile (settings.css) in COOL_CSS_LST")
    m = m.replace(last_css, "\t$(srcdir)/css/settings.css \\\n\t$(srcdir)/css/eurise.css\n", 1)
makefile.write_text(m)

shutil.copy(web / "Control.Eurise.js", browser / "src" / "control" / "Control.Eurise.js")
shutil.copy(web / "eurise.css", browser / "css" / "eurise.css")
icons = 0
for svg in (web / "images").rglob("*.svg"):
    target = browser / "images" / svg.relative_to(web / "images")
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy(svg, target)
    icons += 1
print(f"eurise_web: scheda Suite, Cerca un comando, colori Eurise, {icons} icone")

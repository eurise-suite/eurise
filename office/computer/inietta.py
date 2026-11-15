#!/usr/bin/env python3
"""Mette l'interfaccia Eurise nella pagina del guscio Qt di Collabora (cool-qt.html, e cool.html se c'è).

Uso: inietta.py <cartella dist> <cartella web> [script di diagnosi]

Copia Control.Eurise.js, eurise.css e le immagini nella cartella dist e aggiunge alla pagina, subito dopo bundle.js e con
gli stessi attributi (defer), il nostro script; il foglio di stile prima di </head>. Alla striscia delle schede del guscio
(qtapp-tabstrip.html) aggiunge eurise-tabstrip.css. Lo usano il Flatpak Eurise
(costruisci.sh) e la prova del computer (prova.sh, che aggiunge anche la diagnosi). Toglie il benvenuto di Collabora
(welcome/*.odp). Si può ripetere: non duplica nulla.
"""
import re
import shutil
import sys
from pathlib import Path


def put(src: Path, dst: Path) -> None:
    # Copia nuova e poi rinomina: nei pacchetti installati i file sono legami fisici con il deposito di ostree.
    tmp = dst.with_name(dst.name + '.eurise')
    shutil.copyfile(src, tmp)
    tmp.replace(dst)


def main() -> None:
    dist, web = Path(sys.argv[1]), Path(sys.argv[2])
    diag = Path(sys.argv[3]) if len(sys.argv) > 3 else None
    put(web / 'Control.Eurise.js', dist / 'Control.Eurise.js')
    put(web / 'eurise.css', dist / 'eurise.css')
    for sub in ('', 'dark'):
        src = web / 'images' / sub
        if not src.is_dir():
            continue
        (dist / 'images' / sub).mkdir(parents=True, exist_ok=True)
        for svg in src.glob('*.svg'):
            put(svg, dist / 'images' / sub / svg.name)
    if diag:
        put(diag, dist / 'eurise-diag.js')
    # Striscia delle schede del guscio Qt (26.04): solo il nostro foglio di stile, dopo il suo.
    strip = dist / 'qtapp-tabstrip.html'
    if strip.exists():
        put(Path(__file__).with_name('eurise-tabstrip.css'), dist / 'eurise-tabstrip.css')
        s = strip.read_text(encoding='utf-8')
        if 'eurise-tabstrip.css' not in s:
            s = s.replace('</head>', '<link rel="stylesheet" href="eurise-tabstrip.css" />\n</head>', 1)
            tmp = strip.with_name(strip.name + '.eurise')
            tmp.write_text(s, encoding='utf-8')
            tmp.replace(strip)
        print(f'schede Eurise in {strip.name}')
    # Il benvenuto di Collabora (diapositive col suo logo, la prima volta): il guscio lo mostra solo se il file c'è.
    for odp in (dist / 'welcome').glob('*.odp') if (dist / 'welcome').is_dir() else []:
        odp.unlink()
        print(f'via il benvenuto di Collabora ({odp.name})')
    for page in [dist / 'cool-qt.html', dist / 'cool.html']:
        if not page.exists():
            continue
        s = page.read_text(encoding='utf-8')
        m = re.search(r'<script[^>]*src="([^"]*)bundle\.js"[^>]*>\s*</script>', s)
        if not m:
            sys.exit(f'{page}: bundle.js non trovato')
        tag, prefix = m.group(0), m.group(1)
        added = ''
        if 'Control.Eurise.js' not in s:
            added += '\n' + tag.replace(prefix + 'bundle.js', prefix + 'Control.Eurise.js')
            s = s.replace('</head>', f'<link rel="stylesheet" href="{prefix}eurise.css" />\n</head>', 1)
        if diag and 'eurise-diag.js' not in s:
            added += '\n' + tag.replace(prefix + 'bundle.js', prefix + 'eurise-diag.js')
        if added:
            s = s.replace(tag, tag + added, 1)
            tmp = page.with_name(page.name + '.eurise')
            tmp.write_text(s, encoding='utf-8')
            tmp.replace(page)
        print(f'interfaccia Eurise in {page.name}')


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Ricetta Flatpak di Eurise Office dai sorgenti (strada 2, decisa dal proprietario il 15 novembre).

Uso: ricetta.py <ricetta Flathub di Collabora (com.collaboraoffice.Office.json)> <repo Eurise> <ricetta da scrivere>

Parte dalla ricetta di Collabora su Flathub (flathub/com.collaboraoffice.Office, fissata in compila.sh): stesse sorgenti
(motore e guscio Qt dal tag coda-*, 89 archivi), stesso runtime e stessa BaseApp di QtWebEngine. Cambia:
- identificativo eu.eurise.Office, nome «Eurise Office» e venditore «Eurise» nel configure del guscio;
- nome D-Bus del guscio Qt (un'istanza sola): eu.eurise.Office, che un'app può prendere senza permessi in più;
- in fondo un modulo nostro: interfaccia Eurise nella pagina del guscio (inietta.py), voce nel menu, icona, metainfo.
Il guscio con le schede dei documenti è il passo successivo (patch in office/computer/sorgenti/patch/).
"""
import json
import re
import sys
from pathlib import Path

APP = 'eu.eurise.Office'
SRC_ID = 'com.collaboraoffice.Office'


def main() -> None:
    src, repo, out = Path(sys.argv[1]), Path(sys.argv[2]).resolve(), Path(sys.argv[3])
    m = json.loads(src.read_text(encoding='utf-8'))
    m['id'] = APP
    m.pop('app-id', None)
    # Il dettaglio dei simboli di debug della BaseApp non serve e pesa: via.
    m.pop('base-extensions', None)

    office = next(x for x in m['modules'] if isinstance(x, dict) and x.get('name') == 'collabora-office')
    cmds = []
    for c in office['build-commands']:
        if c.startswith('./configure'):
            c = re.sub(r'--with-vendor="[^"]*"', '--with-vendor="Eurise"', c)
            c = re.sub(r'--with-app-name="[^"]*"', '--with-app-name="Eurise Office"', c)
            c = re.sub(r'--with-info-url=\S+', '--with-info-url=https://github.com/eurise-suite/eurise', c)
        cmds.append(c)
    # Nome D-Bus del guscio Qt (DBusService.cpp) e patch nostre del guscio, prima di compilarlo.
    patches = sorted((repo / 'office/computer/sorgenti/patch').glob('*.patch'))
    pre = [f"sed -i 's/\"{SRC_ID}\"/\"{APP}\"/' qt/DBusService.cpp"]
    for p in patches:
        office['sources'].append({'type': 'file', 'path': str(p), 'dest': 'eurise-patch'})
        pre.append(f'patch -p1 < eurise-patch/{p.name}')
    # Dopo «autoreconf» (prima riga del guscio): le patch toccano qt/, il motore è già compilato.
    at = next(i for i, c in enumerate(cmds) if c.startswith('autoreconf'))
    cmds[at:at] = pre
    # La voce di Collabora serve per i tipi di file della nostra.
    cmds.append(f'install -Dm644 qt/{SRC_ID}.desktop /app/share/eurise/collabora.desktop || true')
    office['build-commands'] = cmds

    m['modules'].append({
        'name': 'eurise',
        'buildsystem': 'simple',
        'sources': [
            {'type': 'dir', 'path': str(repo / 'office/app/web'), 'dest': 'web'},
            {'type': 'dir', 'path': str(repo / 'office/computer'), 'dest': 'computer'},
        ],
        'build-commands': [
            'for d in $(find /app/share -name cool-qt.html -path "*dist*" -exec dirname {} \\;); do python3 computer/inietta.py "$d" web; done',
            f'python3 computer/flatpak/voce.py /app/share/eurise/collabora.desktop /app/share/applications/{APP}.desktop',
            f'rm -rf /app/share/eurise; find /app/share \\( -name "{SRC_ID}*.desktop" -o -name "{SRC_ID}*.png" -o -name "{SRC_ID}*.svg" -o -name "{SRC_ID}*.xml" \\) -delete',
            f'install -Dm644 computer/flatpak/{APP}.svg /app/share/icons/hicolor/scalable/apps/{APP}.svg',
            f'sed "s/@VERSIONE@/dai-sorgenti/" computer/flatpak/{APP}.metainfo.xml > {APP}.metainfo.xml',
            f'install -Dm644 {APP}.metainfo.xml /app/share/metainfo/{APP}.metainfo.xml',
        ],
    })
    out.write_text(json.dumps(m, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print(f'{out}: {len(office["sources"])} sorgenti nel modulo del motore, {len(patches)} patch del guscio')


if __name__ == '__main__':
    main()

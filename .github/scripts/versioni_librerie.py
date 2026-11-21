#!/usr/bin/env python3
"""Ultime versioni stabili delle librerie della suite (docs/SICUREZZA.md, «Aggiornamento delle librerie»).

Legge il catalogo `gradle/libs.versions.toml`, le versioni scritte a mano nei `build.gradle.kts` (suite e Tastiera) e
le librerie di esecuzione (`gradle/eurise/esecuzione/gradle.lockfile` e quello della Tastiera), chiede a Maven Google e
a Maven Central l'ultima versione stabile e scrive una tabella in Markdown (riepilogo del giro). Non cambia nulla: serve
a decidere gli aggiornamenti, che poi passano dalle impronte e dai giri bloccanti come sempre.

Uso: python3 .github/scripts/versioni_librerie.py > riepilogo.md
"""
import re
import sys
import tomllib
import urllib.request
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

REPOS = ("https://dl.google.com/android/maven2", "https://repo1.maven.org/maven2",
         "https://plugins.gradle.org/m2")
INSTABILE = re.compile(r"(?i)(alpha|beta|rc|dev|eap|snapshot|preview|-m\d|\.m\d|milestone|-pre)")
LETTERALE = re.compile(r'"([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+):([0-9][A-Za-z0-9_.+-]*)"')


def chiave(v: str):
    """Confronto di versioni sufficiente per scegliere la più recente (numeri prima, poi testo)."""
    return [(0, int(p), "") if p.isdigit() else (1, 0, p) for p in re.split(r"[.\-+_]", v)]


def ultime(coord: str):
    """(ultima stabile, ultima qualunque) da maven-metadata.xml, o (None, None)."""
    g, a = coord.split(":")
    for repo in REPOS:
        url = f"{repo}/{g.replace('.', '/')}/{a}/maven-metadata.xml"
        try:
            with urllib.request.urlopen(url, timeout=20) as r:
                root = ET.fromstring(r.read())
        except Exception:
            continue
        versioni = [v.text.strip() for v in root.iter("version") if v.text]
        if not versioni:
            continue
        stabili = [v for v in versioni if not INSTABILE.search(v)]
        return (max(stabili, key=chiave) if stabili else None), max(versioni, key=chiave)
    return None, None


def main() -> None:
    root = Path(".")
    dirette = {}  # coord -> {versione: [dove]}

    cat = tomllib.loads((root / "gradle/libs.versions.toml").read_text())
    vers = cat.get("versions", {})
    for nome, lib in cat.get("libraries", {}).items():
        if isinstance(lib, str):
            g, a, v = lib.split(":")
            mod = f"{g}:{a}"
        else:
            mod = lib.get("module") or f"{lib['group']}:{lib['name']}"
            v = lib.get("version")
            if isinstance(v, dict):
                v = vers.get(v["ref"]) if "ref" in v else v.get("strictly") or v.get("require")
        if v:
            dirette.setdefault(mod, {}).setdefault(str(v), []).append(f"catalogo ({nome})")
    for nome, plug in cat.get("plugins", {}).items():
        pid = plug["id"] if isinstance(plug, dict) else plug.split(":")[0]
        v = plug.get("version") if isinstance(plug, dict) else plug.split(":")[1]
        if isinstance(v, dict):
            v = vers.get(v.get("ref", ""))
        if v:
            dirette.setdefault(f"{pid}:{pid}.gradle.plugin", {}).setdefault(str(v), []).append(f"plugin ({nome})")

    for f in sorted(root.rglob("*.gradle.kts")):
        if any(p in f.parts for p in (".git", "build", "third_party")):
            continue
        for g, a, v in LETTERALE.findall(f.read_text(errors="ignore")):
            dirette.setdefault(f"{g}:{a}", {}).setdefault(v, []).append(str(f))

    esecuzione = {}
    for lock in (root / "gradle/eurise/esecuzione/gradle.lockfile", root / "keyboard/gradle/eurise/esecuzione/gradle.lockfile"):
        if not lock.exists():
            continue
        for riga in lock.read_text().splitlines():
            m = re.match(r"([^:#\s]+):([^:\s]+):([^=\s]+)=", riga)
            if m:
                esecuzione.setdefault(f"{m[1]}:{m[2]}", set()).add(m[3])

    tutte = sorted(set(dirette) | set(esecuzione))
    with ThreadPoolExecutor(16) as ex:
        risposte = dict(zip(tutte, ex.map(ultime, tutte)))

    out = ["## Versioni delle librerie", "",
           "Ultima stabile su Maven Google / Central / Plugin Portal. Solo lettura: gli aggiornamenti passano dalle impronte "
           "e dai giri bloccanti.", "",
           "### Dichiarate da noi (catalogo, plugin, versioni scritte nei build.gradle.kts)", "",
           "| Libreria | In uso | Ultima stabile | Ultima | Dove |", "|---|---|---|---|---|"]
    da_aggiornare = 0
    for coord in sorted(dirette):
        stabile, qualunque = risposte[coord]
        for v, dove in sorted(dirette[coord].items()):
            vecchia = stabile is not None and chiave(stabile) > chiave(v)
            da_aggiornare += vecchia
            segno = "⬆️ " if vecchia else ""
            nome = coord.split(":")[0] if coord.endswith(".gradle.plugin") else coord
            luoghi = ", ".join(sorted(set(dove)))[:160]
            out.append(f"| {segno}`{nome}` | {v} | {stabile or '?'} | {qualunque or '?'} | {luoghi} |")
    out += ["", f"**{da_aggiornare}** versioni dichiarate hanno un'uscita stabile più recente.", "",
            "<details><summary>Librerie di esecuzione (anche transitive) con un'uscita stabile più recente</summary>", "",
            "| Libreria | Nelle app | Ultima stabile |", "|---|---|---|"]
    for coord in sorted(esecuzione):
        stabile, _ = risposte[coord]
        usate = sorted(esecuzione[coord], key=chiave)
        if stabile and chiave(stabile) > chiave(usate[-1]):
            out.append(f"| `{coord}` | {', '.join(usate)} | {stabile} |")
    out += ["", "</details>"]
    mancanti = [c for c in tutte if risposte[c][0] is None and risposte[c][1] is None]
    if mancanti:
        out += ["", f"Non trovate nei repository ({len(mancanti)}): " + ", ".join(f"`{c}`" for c in mancanti[:40])]
    print("\n".join(out))


if __name__ == "__main__":
    sys.exit(main())

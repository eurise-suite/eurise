#!/usr/bin/env python3
"""Controlla che l'albero di CoMaps adattato da eurise_patch.py sia di una sola variante (docs/SICUREZZA.md, «Mappe»).

Uso: python3 maps/test/controlla_variante.py <cartella di comaps> <prova|ufficiale>

- Nel manifesto un solo permesso della suite (dichiarato, chiesto e messo sui componenti): SUITE_DEBUG in prova, SUITE
  in ufficiale. Con tutti e due, nelle ufficiali un'app qualunque potrebbe dichiarare SUITE_DEBUG da sé e passare.
- Nei <queries> e nei nomi tra virgolette del codice nostro solo app della suite della stessa variante (.debug in prova,
  senza suffisso in ufficiale).
- EuriseSuite.OFFICIAL come la variante.
Si ferma con un errore alla prima cosa che non torna.
"""
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

A = "{http://schemas.android.com/apk/res/android}"
SUITE, SUITE_DEBUG = "eu.eurise.permission.SUITE", "eu.eurise.permission.SUITE_DEBUG"
# Pacchetti delle app della suite (non i nomi di classe come eu.eurise.hub.ShareToNotesActivity, né le azioni).
APPS = ("hub", "voice", "messages", "password", "phone", "keyboard", "browser", "gallery", "mail", "chat", "read", "ai",
        "office")


def main() -> None:
    root, variante = Path(sys.argv[1]), sys.argv[2]
    if variante not in ("prova", "ufficiale"):
        sys.exit(f"controlla_variante: variante sconosciuta {variante!r}")
    ufficiale = variante == "ufficiale"
    atteso = SUITE if ufficiale else SUITE_DEBUG
    errori = []

    manifest = root / "android" / "app" / "src" / "main" / "AndroidManifest.xml"
    tree = ET.parse(manifest)
    perms = set()
    for el in tree.iter():
        for key in (A + "name", A + "permission"):
            v = el.get(key, "")
            if v.startswith("eu.eurise.permission."):
                if el.tag in ("permission", "uses-permission") or key == A + "permission":
                    perms.add(v)
    if perms != {atteso}:
        errori.append(f"permessi della suite nel manifesto {sorted(perms)}, atteso solo {atteso}")
    dichiarati = [el for el in tree.iter("permission") if el.get(A + "name") == atteso]
    if not dichiarati or any(el.get(A + "protectionLevel") != "signature" for el in dichiarati):
        errori.append(f"{atteso} va dichiarato con protectionLevel=signature")

    for el in tree.iter("package"):
        name = el.get(A + "name", "")
        if name.startswith("eu.eurise.") and name.endswith(".debug") == ufficiale:
            errori.append(f"<queries> verso {name}, che non è della variante {variante}")

    nomi = re.compile(r'"(eu\.eurise\.(?:' + "|".join(APPS) + r')(\.debug)?)"')
    ours = root / "android"
    for f in list(ours.rglob("*.java")) + list(ours.rglob("*.kt")):
        text = f.read_text(errors="ignore")
        if "eu.eurise." not in text or f.name == "EuriseSuite.java":
            continue
        for m in nomi.finditer(text):
            if bool(m.group(2)) == ufficiale:
                errori.append(f"{f.relative_to(root)}: nome {m.group(1)} di un'altra variante")
            elif "/organicmaps/eurise/" in f.as_posix() or f.name.startswith("Eurise"):
                # Il codice nostro passa da EuriseSuite; gli agganci del patch nel codice di CoMaps (per esempio la
                # voce nel modulo sdk, che non vede EuriseSuite) possono scrivere il nome, purché della variante giusta.
                errori.append(f"{f.relative_to(root)}: nome {m.group(1)} scritto a mano (usare EuriseSuite)")
        if SUITE_DEBUG in text and ufficiale or re.search(r'"eu\.eurise\.permission\.SUITE"', text) and not ufficiale:
            errori.append(f"{f.relative_to(root)}: permesso della suite di un'altra variante")

    suite = ours / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "eurise" / "EuriseSuite.java"
    riga = f"public static final boolean OFFICIAL = {'true' if ufficiale else 'false'};"
    if riga not in suite.read_text():
        errori.append(f"{suite.relative_to(root)}: manca «{riga}»")

    if errori:
        for e in errori:
            print(f"::error::{e}")
        sys.exit(1)
    print(f"controlla_variante: variante «{variante}» a posto ({atteso})")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Adatta l'app Android di Collabora Online a Eurise Office (vedi office/app/EURISE.md).

Uso: python3 office/app/eurise_patch.py <cartella android di online> <keystore di debug di Eurise>

Dopo ./configure di online: pacchetto eu.eurise.office (con .debug per la prova, come le altre app della suite),
firma con la chiave di prova della suite (i colori dell'interfaccia li mette eurise_css.py prima di make). Ogni sostituzione deve riuscire: se Collabora cambia quelle righe, lo script si
ferma e lo dice.
"""
import re
import shutil
import sys
from pathlib import Path

android = Path(sys.argv[1]).resolve()
keystore = Path(sys.argv[2]).resolve()
ours = Path(__file__).resolve().parent

settings = android / "app" / "appSettings.gradle"
if not settings.exists():
    sys.exit(f"eurise_patch: manca {settings}: ./configure di online non è passato?")
s = settings.read_text()
s, n = re.subn(r"applicationId '[^']*'", "applicationId 'eu.eurise.office'", s)
if n != 1:
    sys.exit("eurise_patch: non trovo applicationId in appSettings.gradle")
settings.write_text(s)

build = android / "app" / "build.gradle"
b = build.read_text()
old = "    buildTypes {\n        debug {\n"
if old not in b:
    sys.exit("eurise_patch: non trovo buildTypes.debug in app/build.gradle")
b = b.replace(old, """    signingConfigs {
        euriseDebug {
            storeFile file('eurise-debug.keystore')
            storePassword 'android'
            keyAlias 'androiddebugkey'
            keyPassword 'android'
        }
    }

    buildTypes {
        debug {
            signingConfig signingConfigs.euriseDebug
""", 1)
build.write_text(b)
shutil.copy(keystore, android / "app" / "eurise-debug.keystore")

print("eurise_patch: eu.eurise.office, chiave di prova della suite")

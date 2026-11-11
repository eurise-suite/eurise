#!/usr/bin/env python3
"""Adatta i sorgenti di CoMaps a Eurise Mappe (vedi maps/EURISE.md).

Uso: python3 maps/eurise_patch.py <cartella di comaps> <keystore di debug di Eurise>

Cambia solo ciò che serve al prototipo: nome «Eurise Mappe», pacchetto eu.eurise.maps (senza suffissi
.fdroid/.debug, così il permesso di firma della suite vale anche qui), firma con la chiave di debug della
suite. Il codice Java (pacchetto app.organicmaps) resta com'è. Ogni sostituzione deve riuscire: se CoMaps
cambia quelle righe, lo script si ferma e lo dice.
"""
import shutil
import sys
from pathlib import Path

root = Path(sys.argv[1])
keystore = Path(sys.argv[2])
gradle = root / "android" / "app" / "build.gradle.kts"
text = gradle.read_text()


def swap(old: str, new: str, count: int = 1) -> None:
    global text
    found = text.count(old)
    if found < count:
        sys.exit(f"eurise_patch: non trovo {old!r} in {gradle} ({found} volte, ne servono {count})")
    text = text.replace(old, new)


swap('val appName = "CoMaps"', 'val appName = "Eurise Mappe"')
swap('val appId = "app.comaps"', 'val appId = "eu.eurise.maps"')
swap('applicationIdSuffix = ".fdroid"', '// Eurise: nessun suffisso, il pacchetto resta eu.eurise.maps')
swap('applicationIdSuffix = ".debug" // Allows installing debug and release builds together.',
     '// Eurise: nessun suffisso, così la prova si aggiorna sopra la precedente')
swap('resValue("string", "app_name", "$appName Debug")', 'resValue("string", "app_name", appName)')
# Firma: la chiave di debug della suite (pubblica e senza valore), la stessa degli altri APK di prova.
swap('storeFile = project.projectDir.resolve(File(comapsDebugKeystorePath))',
     'storeFile = project.projectDir.resolve(File("eurise-debug.keystore"))')
swap('storePassword = "12345678"\n            keyAlias = "$appName Debug"\n            keyPassword = "12345678"',
     'storePassword = "android"\n            keyAlias = "androiddebugkey"\n            keyPassword = "android"')
gradle.write_text(text)
shutil.copy(keystore, gradle.parent / "eurise-debug.keystore")

strings = root / "android" / "app" / "src" / "main" / "res" / "values" / "strings.xml"
s = strings.read_text()
old = '<string name="project_name" translatable="false">CoMaps</string>'
if old not in s:
    sys.exit(f"eurise_patch: non trovo project_name in {strings}")
strings.write_text(s.replace(old, '<string name="project_name" translatable="false">Eurise Mappe</string>'))
print("eurise_patch: Eurise Mappe, eu.eurise.maps, chiave di debug della suite")

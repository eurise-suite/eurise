#!/usr/bin/env python3
"""Diagnosi del motore nelle build di prova di Eurise Office (vedi office/app/diag/eurise_diag.cpp).

Uso: python3 office/app/eurise_diag.py <cartella android di Collabora Online, dopo configure>

- aggiunge eurise_diag.cpp a libandroidapp (pila al segnale, eccezioni non prese, stderr nel logcat);
- porta il registro di coolwsd da «information» a «trace».
Si usa solo quando il lavoro office-app ha DIAGNOSI=1; la versione ufficiale non la contiene.
"""
import shutil
import sys
from pathlib import Path

android = Path(sys.argv[1])
cpp = android / "lib" / "src" / "main" / "cpp"
here = Path(__file__).resolve().parent

shutil.copy(here / "diag" / "eurise_diag.cpp", cpp / "eurise_diag.cpp")

cmake = cpp / "CMakeLists.txt"
text = cmake.read_text()
if "eurise_diag.cpp" not in text:
    if "            androidapp.cpp\n" not in text:
        sys.exit("eurise_diag: non trovo androidapp.cpp in CMakeLists.txt")
    text = text.replace("            androidapp.cpp\n", "            androidapp.cpp\n            eurise_diag.cpp\n", 1)
    cmake.write_text(text)

app = cpp / "androidapp.cpp"
text = app.read_text()
old = 'Log::initialize("Mobile", "information"'
if old not in text and 'Log::initialize("Mobile", "trace"' not in text:
    sys.exit("eurise_diag: non trovo Log::initialize in androidapp.cpp")
app.write_text(text.replace(old, 'Log::initialize("Mobile", "trace"'))
print("eurise_diag: diagnosi aggiunta (eurise_diag.cpp, registro trace)")

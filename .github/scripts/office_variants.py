"""Varianti del motore di Office: ricollega liblo-native-code.so con gruppi di componenti diversi e ne misura il peso.

Uso: python3 office_variants.py <cartella del core> <cartella di uscita>

Il motore per Android si assembla con solenv/bin/native-code.py, che sceglie quali componenti entrano nella libreria
(gruppi core, writer, calc, draw = Draw e Impress insieme, edit = salvataggio); il linker toglie il codice che nessuno usa.
Così le varianti costano solo un nuovo collegamento, senza ricompilare. Scrive il riepilogo (Markdown) su stdout.
"""
import os
import re
import shutil
import subprocess
import sys
import zlib

MB = 1024 * 1024

# Componenti tolti nella variante «senza Base e formati rari» (blocchi «# percorso/del.component» di native-code.py).
RARE = [
    "dbaccess/util/dba.component",                       # Base (database)
    "connectivity/source/manager/sdbc2.component",       # connessioni ai database
    "connectivity/source/drivers/dbase/dbase.component",  # file dBase
    "extensions/source/bibliography/bib.component",      # bibliografia
    "lingucomponent/source/spellcheck/languagetool/LanguageTool.component",  # correttore via server
    "linguistic/source/spellcheck/MacOSXSpellMacOSXSpell.component",
    "writerperfect/source/draw/wpftdraw.component",      # Visio
    "writerperfect/source/writer/wpftwriter.component",  # esportazione EPUB
]

VARIANTS = [
    ("Completo (come adesso)", ["core", "writer", "calc", "draw", "edit"], False),
    ("Senza Base e formati rari", ["core", "writer", "calc", "draw", "edit"], True),
    ("Testi e fogli (senza presentazioni e disegni)", ["core", "writer", "calc", "edit"], False),
    ("Testi e fogli, senza Base e rari", ["core", "writer", "calc", "edit"], True),
    ("Solo testi", ["core", "writer", "edit"], False),
]


def mb(n: float) -> str:
    return f"{n / MB:.1f} MB".replace(".", ",")


def without(script: str, blocks) -> str:
    """Copia di native-code.py senza le righe dei blocchi indicati (dal commento al blocco successivo)."""
    out, skip = [], False
    for line in script.splitlines(keepends=True):
        m = re.match(r"\s*# (\S+\.component)", line)
        if m:
            skip = any(m.group(1).startswith(b.replace(".component", "")) for b in blocks)
        elif line.strip().startswith("]"):
            skip = False
        if not skip:
            out.append(line)
    return "".join(out)


def main() -> None:
    core, out = sys.argv[1], sys.argv[2]
    os.makedirs(out, exist_ok=True)
    src = os.path.join(core, "android", "source")
    script = os.path.join(core, "solenv", "bin", "native-code.py")
    reduced = os.path.join(out, "native-code-ridotto.py")
    with open(script) as f:
        text = f.read()
    with open(reduced, "w") as f:
        f.write(without(text, RARE))
    so = os.path.join(core, "android", "jniLibs", "arm64-v8a", "liblo-native-code.so")
    original_cxx = os.path.join(src, "native-code.cxx")
    backup = original_cxx + ".eurise"
    if os.path.exists(original_cxx):
        shutil.copy2(original_cxx, backup)
    lines = ["## Varianti del motore (stesso codice, componenti diversi)", "",
             "| Variante | Libreria | Compressa | Differenza (compressa) |", "|---|---|---|---|"]
    base = None
    for name, groups, rare in VARIANTS:
        args = ["python3", reduced if rare else script, "-j"] + sum((["-g", g] for g in groups), [])
        cxx = subprocess.run(args, capture_output=True, text=True, cwd=src)
        if cxx.returncode != 0:
            lines.append(f"| {name} | errore nel generare l'elenco | | |")
            continue
        with open(original_cxx, "w") as f:
            f.write(cxx.stdout)
        if os.path.exists(so):
            os.remove(so)
        objlocal = subprocess.run(["find", src, "-path", "*obj/local*", "-name", "liblo-native-code.so"], capture_output=True, text=True).stdout.split()
        for p in objlocal:
            os.remove(p)
        link = subprocess.run(["make", "-C", src, "link-so"], capture_output=True, text=True)
        if link.returncode != 0 or not os.path.exists(so):
            tail = (link.stdout + link.stderr).strip().splitlines()[-3:]
            lines.append(f"| {name} | collegamento non riuscito | | {' / '.join(t[:80] for t in tail)} |")
            continue
        with open(so, "rb") as f:
            data = f.read()
        raw, packed = len(data), len(zlib.compress(data, 9))
        if base is None:
            base = packed
        diff = "—" if packed == base else f"{mb(packed - base)}"
        lines.append(f"| {name} | {mb(raw)} | {mb(packed)} | {diff} |")
        print(f"::notice title=Variante {name}::{mb(raw)} ({mb(packed)} compressa)", file=sys.stderr)
    if os.path.exists(backup):
        shutil.move(backup, original_cxx)
    lines += ["", "Draw e Impress sono lo stesso componente (sd): non si tolgono uno senza l'altro. Gli assets (interfaccia, "
              "filtri, caratteri: circa 16 MB compressi) restano uguali in tutte le varianti."]
    print("\n".join(lines))


if __name__ == "__main__":
    main()

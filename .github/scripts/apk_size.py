"""Peso di un APK diviso per parti: codice, librerie native, risorse, altro.

Uso: python3 apk_size.py <nome> <file.apk>
Scrive una tabella nel riepilogo della compilazione e una riga di avviso.
"""
import collections
import os
import sys
import zipfile


def part(name: str) -> str:
    if name.endswith(".dex"):
        return "Codice"
    if name.startswith("lib/"):
        return "Librerie native"
    if name.startswith("res/") or name == "resources.arsc":
        return "Risorse"
    if name.startswith("assets/"):
        return "Assets"
    return "Altro"


def main() -> None:
    label, path = sys.argv[1], sys.argv[2]
    size = os.path.getsize(path)
    parts = collections.Counter()
    biggest = []
    with zipfile.ZipFile(path) as apk:
        for info in apk.infolist():
            parts[part(info.filename)] += info.compress_size
            biggest.append((info.compress_size, info.filename))
    biggest.sort(reverse=True)

    lines = [f"### {label}: {size / 1048576:.1f} MB", "", "| Parte | MB |", "| --- | ---: |"]
    lines += [f"| {k} | {v / 1048576:.2f} |" for k, v in parts.most_common()]
    lines += ["", "File più pesanti:", ""]
    lines += [f"- `{name}` {s / 1048576:.2f} MB" for s, name in biggest[:6]]
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as out:
            out.write("\n".join(lines) + "\n\n")
    print("\n".join(lines))
    print(f"::notice title=Peso::{label}: {size / 1048576:.1f} MB")


if __name__ == "__main__":
    main()

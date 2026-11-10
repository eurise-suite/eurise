"""Misure del prototipo del motore di Office: tempo, spazio su disco, peso della libreria e degli assets.

Uso: python3 office_size.py <cartella del core> <file dei tempi> <esito di make> <cartella di uscita>

- file dei tempi: righe «fase secondi» scritte dal lavoro;
- esito di make: 0 finito, 124 tempo scaduto, altro errore.
Scrive il riepilogo (Markdown) su stdout, `misure.json` e `motore.zip` (libreria e assets come li mette
l'app Collabora Office) nella cartella di uscita. La scelta degli assets ricalca android/lib/build.gradle di
CollaboraOnline/online (ramo co-25.04-mobile).
"""
import fnmatch
import json
import os
import shutil
import subprocess
import sys
import zipfile
import zlib

MB = 1024 * 1024

# (gruppo, cartella sotto instdir, schemi inclusi). I caratteri sono un gruppo a parte: si possono scaricare dopo.
ASSETS = [
    ("Tipi e servizi UNO", "program", ["types.rdb", "types/offapi.rdb", "types/oovbaapi.rdb",
                                       "services.rdb", "services/services.rdb"]),
    ("Testi dell'interfaccia", "program/resource", ["**"]),
    ("Configurazione", "share/registry", ["**"]),
    ("Filtri, galleria, tavolozze", "share", ["filter/**", "gallery/**", "palette/**"]),
    ("Interfaccia (soffice.cfg, icone)", "share/config", ["soffice.cfg/**", "images_colibre.zip"]),
    ("Lingue (liblangtag)", "share/liblangtag", ["**"]),
    ("Caratteri", "share/fonts/truetype", ["EmojiOne*.ttf", "Liberation*.ttf", "Caladea-*.ttf",
                                           "Carlito-*.ttf", "Gen*.ttf", "opens___.ttf"]),
]


def mb(n: float) -> str:
    text = f"{n / MB / 1024:.1f} GB" if n >= 1024 * MB else f"{n / MB:.1f} MB"
    return text.replace(".", ",")


def du(path: str) -> int:
    if not os.path.exists(path):
        return 0
    out = subprocess.run(["du", "-sb", path], capture_output=True, text=True).stdout.split()
    return int(out[0]) if out else 0


def deflated(path: str) -> int:
    with open(path, "rb") as f:
        return len(zlib.compress(f.read(), 9))


def matches(rel: str, patterns) -> bool:
    for p in patterns:
        if p == "**" or (p.endswith("/**") and rel.startswith(p[:-2])) or fnmatch.fnmatch(rel, p):
            return True
    return False


def collect(instdir: str):
    groups = []
    for name, sub, patterns in ASSETS:
        base = os.path.join(instdir, sub)
        files = []
        for root, _, names in os.walk(base):
            for n in names:
                full = os.path.join(root, n)
                rel = os.path.relpath(full, base)
                if matches(rel, patterns):
                    files.append((full, os.path.join("assets", sub, rel)))
        raw = sum(os.path.getsize(f) for f, _ in files)
        packed = sum(deflated(f) for f, _ in files)
        groups.append({"gruppo": name, "file": len(files), "byte": raw, "compressi": packed, "_files": files})
    return groups


def fmt_time(s: int) -> str:
    h, m = divmod(s // 60, 60)
    return f"{h} h {m:02d} min" if h else f"{m} min"


def main() -> None:
    core, times_file, status, out = sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4]
    os.makedirs(out, exist_ok=True)
    times = []
    if os.path.exists(times_file):
        for line in open(times_file):
            parts = line.split()
            if len(parts) == 2:
                times.append((parts[0], int(parts[1])))

    lines = ["## Prototipo del motore di Office (LibreOffice di Collabora, arm64)", ""]
    esito = {0: "**compilazione finita**", 124: "**tempo scaduto**: la ccache è salvata, rilanciare il lavoro"}
    lines.append("Esito: " + esito.get(status, f"**errore** (make ha risposto {status}): vedere gli errori sopra"))
    lines += ["", "| Fase | Tempo |", "|---|---|"]
    lines += [f"| {k.replace('_', ' ')} | {fmt_time(v)} |" for k, v in times]
    lines.append(f"| **Totale** | **{fmt_time(sum(v for _, v in times))}** |")

    workdir, instdir = os.path.join(core, "workdir"), os.path.join(core, "instdir")
    disk = {
        "Sorgenti (senza compilazione)": du(core) - du(workdir) - du(instdir),
        "Compilazione (workdir)": du(workdir),
        "Installazione (instdir)": du(instdir),
        "Archivi esterni": du(os.environ.get("LO_TARBALLS", "")),
        "ccache": du(os.environ.get("CCACHE_DIR", "")),
    }
    lines += ["", "| Spazio su disco | |", "|---|---|"]
    lines += [f"| {k} | {mb(v)} |" for k, v in disk.items()]
    free = shutil.disk_usage(core).free
    lines.append(f"| Libero alla fine | {mb(free)} |")
    objects = sum(1 for _, _, ns in os.walk(workdir) for n in ns if n.endswith(".o"))
    lines.append(f"| File oggetto compilati | {objects} |")

    result = {"esito": status, "tempi": dict(times), "disco": disk, "libero": free, "oggetti": objects}
    so_dir = os.path.join(core, "android", "jniLibs", "arm64-v8a")
    libs = sorted(os.listdir(so_dir)) if os.path.isdir(so_dir) else []
    if not libs:
        lines += ["", "La libreria `liblo-native-code.so` non c'è ancora: il peso si misura quando la compilazione finisce."]
    else:
        lines += ["", "| Peso nell'APK | Com'è | Compresso |", "|---|---|---|"]
        so_raw = so_packed = 0
        for n in libs:
            p = os.path.join(so_dir, n)
            raw, packed = os.path.getsize(p), deflated(p)
            so_raw, so_packed = so_raw + raw, so_packed + packed
            lines.append(f"| `{n}` | {mb(raw)} | {mb(packed)} |")
        groups = collect(instdir)
        for g in groups:
            lines.append(f"| {g['gruppo']} ({g['file']} file) | {mb(g['byte'])} | {mb(g['compressi'])} |")
        fonts = next(g for g in groups if g["gruppo"] == "Caratteri")
        assets_packed = sum(g["compressi"] for g in groups)
        # Le librerie si tengono non compresse nell'APK (si caricano direttamente); gli assets compressi.
        apk = so_raw + assets_packed
        lines += [
            "",
            f"Stima dell'APK (librerie non compresse, assets compressi): **{mb(apk)}**; "
            f"senza caratteri (scaricati a richiesta): **{mb(apk - fonts['compressi'])}**; "
            f"con le librerie compresse (installazione più lenta, spazio doppio sul telefono): {mb(so_packed + assets_packed)}.",
            "Manca l'interfaccia web di Collabora (cartella browser di online), che serve solo a «Tutte le opzioni».",
        ]
        result["librerie"] = {n: os.path.getsize(os.path.join(so_dir, n)) for n in libs}
        result["assets"] = [{k: v for k, v in g.items() if not k.startswith("_")} for g in groups]
        result["stima_apk"] = apk
        result["stima_apk_senza_caratteri"] = apk - fonts["compressi"]
        with zipfile.ZipFile(os.path.join(out, "motore.zip"), "w", zipfile.ZIP_DEFLATED) as z:
            for n in libs:
                z.write(os.path.join(so_dir, n), f"lib/arm64-v8a/{n}", compress_type=zipfile.ZIP_STORED)
            for g in groups:
                for full, arc in g["_files"]:
                    z.write(full, arc)
        print(f"::notice title=Motore di Office::APK stimato {mb(apk)}, senza caratteri {mb(apk - fonts['compressi'])}",
              file=sys.stderr)

    with open(os.path.join(out, "misure.json"), "w") as f:
        json.dump(result, f, indent=1, ensure_ascii=False)
    print("\n".join(lines))


if __name__ == "__main__":
    main()

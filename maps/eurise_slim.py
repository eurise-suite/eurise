#!/usr/bin/env python3
"""Alleggerisce gli assets di CoMaps prima della compilazione (vedi maps/EURISE.md, «Peso»).

Uso: python3 maps/eurise_slim.py <cartella di comaps>   (dopo ./configure.sh, che genera simboli e testi)

1. Simboli: restano le densità xhdpi, 6plus e xxhdpi (telefoni da 320 a 480 dpi circa). Gli schermi più densi
   prendono xxhdpi (simboli un po' più piccoli), quelli meno densi xhdpi. eurise_patch.py toglie le stesse densità
   dalla tabella di VisualParams, che altrimenti cercherebbe file che non ci sono.
2. Caratteri: fuori dall'APK quelli delle scritture rare (cinese, giapponese e coreano, tibetano, birmano, khmer e
   code2000). Il motore li cerca anche tra i caratteri del sistema (Noto) e nella cartella dei dati dell'app, dove
   un domani si potranno scaricare a richiesta; se un file manca lo salta.
3. Nomi dei paesi e delle regioni: restano le lingue europee e poche altre; per le lingue assenti il motore usa
   l'inglese (GetTextByIdFactory).
I file restano su disco (la configurazione di Gradle rigenera i simboli che mancano): lo script li esclude dall'APK
aggiungendo i loro nomi a `ignoreAssetsPattern` in android/app/build.gradle.kts. Ogni passo stampa quanto toglie.
Se un percorso atteso non c'è, lo script si ferma: CoMaps è cambiato.
"""
import sys
from pathlib import Path

root = Path(sys.argv[1])
data = root / "data"


def size(p: Path) -> int:
    if p.is_file():
        return p.stat().st_size
    return sum(f.stat().st_size for f in p.rglob("*") if f.is_file())


def mb(n: int) -> str:
    return f"{n / 1048576:.1f} MB"


patterns: list[str] = []


def drop(paths, what: str) -> None:
    total = 0
    for p in paths:
        total += size(p)
        patterns.append(("<dir>" if p.is_dir() else "<file>") + p.name)
    print(f"eurise_slim: {what}: fuori dall'APK {mb(total)} ({len(paths)} voci)")


def need(p: Path) -> Path:
    if not p.exists():
        sys.exit(f"eurise_slim: non trovo {p}: CoMaps è cambiato, controlla lo script")
    return p


# 1. Simboli
symbols = need(data / "symbols")
for keep in ("xhdpi", "6plus", "xxhdpi"):
    need(symbols / keep / "light" / "symbols.png")
drop([need(symbols / d) for d in ("mdpi", "hdpi", "xxxhdpi")], "simboli mdpi, hdpi, xxxhdpi")

# 2. Caratteri delle scritture rare
fonts = need(data / "fonts")
rare = ["02_droidsans-fallback.ttf", "03_jomolhari-id-a3d.ttf", "04_padauk.ttf", "05_khmeros.ttf", "06_code2000.ttf"]
drop([need(fonts / f) for f in rare], "caratteri delle scritture rare")

# 3. Nomi dei paesi: lingue europee (UE e vicine), più inglese e «default»
strings = need(data / "countries-strings")
keep_langs = {
    "default", "en", "it", "de", "fr", "es", "pt", "nl", "pl", "cs", "sk", "sl", "hr", "hu", "ro", "bg", "el",
    "da", "sv", "fi", "et", "lv", "lt", "ga", "mt", "nb", "no", "is", "ca", "eu", "gl", "sq", "sr", "bs", "mk",
    "uk", "be", "ru", "tr", "lb",
}
gone = [d for d in strings.iterdir() if d.name.removesuffix(".json").split("-")[0].split("_")[0] not in keep_langs]
need(strings / "en.json" / "localize.json")
drop(gone, "nomi dei paesi in lingue non europee")

# I modelli valgono per nome in tutti gli assets: le cartelle <lingua>.json escono anche da sound-strings (testi della
# guida vocale), coerente con la scelta delle lingue; mdpi, hdpi e xxxhdpi esistono solo sotto symbols.
gradle = root / "android" / "app" / "build.gradle.kts"
g = gradle.read_text()
anchor = 'ignoreAssetsPattern = "!.svn:!.git:!.DS_Store:!*.scc:.*:<dir>_*:!CVS:!thumbs.db:!picasa.ini:!*~'
if anchor not in g:
    sys.exit(f"eurise_slim: non trovo ignoreAssetsPattern in {gradle}")
gradle.write_text(g.replace(anchor, anchor + ":" + ":".join(patterns)))
print(f"eurise_slim: {len(patterns)} esclusioni in ignoreAssetsPattern")

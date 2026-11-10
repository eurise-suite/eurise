"""Prototipo di Eurise Traduci: scarica i modelli di Firefox italiano↔inglese, li fa girare con il motore compilato dai
sorgenti (eurise_translate_test) e misura velocità e qualità (chrF contro traduzioni di riferimento) su frasi di prova.
Uso: mt_quality.py <eseguibile> <cartella di lavoro>"""
import gzip
import json
import os
import subprocess
import sys
import urllib.request

MANIFEST = "https://storage.googleapis.com/moz-fx-translations-data--303e-prod-translations-data/db/models.json"
RELEASE = {"Release Android": 4, "Release": 3, "Release Desktop": 2, "Nightly": 1}
SIZE = {"tiny": 1, "base": 2, "base-memory": 3}

# Frasi di tutti i giorni, di lavoro e di viaggio, con una traduzione di riferimento.
PAIRS = [
    ("Dov'è la stazione più vicina?", "Where is the nearest station?"),
    ("Ci vediamo domani alle nove davanti all'ufficio.", "See you tomorrow at nine in front of the office."),
    ("Ti mando il preventivo entro venerdì, come d'accordo.", "I'll send you the quote by Friday, as agreed."),
    ("La riunione è spostata a giovedì pomeriggio per un imprevisto.", "The meeting has been moved to Thursday afternoon due to an unexpected event."),
    ("Potrebbe confermarmi la prenotazione per due persone?", "Could you confirm the reservation for two people?"),
    ("Il volo è in ritardo di quaranta minuti.", "The flight is delayed by forty minutes."),
    ("Grazie mille per l'aiuto, sei stato gentilissimo.", "Thank you so much for your help, you were very kind."),
    ("La fattura va pagata entro trenta giorni dalla data di emissione.", "The invoice must be paid within thirty days of the issue date."),
    ("Scusa il ritardo, ho perso il treno.", "Sorry I'm late, I missed the train."),
    ("Puoi richiamarmi quando hai un minuto?", "Can you call me back when you have a minute?"),
    ("Abbiamo bisogno di più tempo per finire il progetto.", "We need more time to finish the project."),
    ("Il negozio chiude alle otto di sera, tranne la domenica.", "The shop closes at eight in the evening, except on Sundays."),
]


def fetch(url):
    with urllib.request.urlopen(url, timeout=120) as r:
        return r.read()


def chrf(hyp, ref, n=6, beta=2.0):
    """chrF semplice (caratteri, n-grammi 1..6), da 0 a 100."""
    hyp, ref = hyp.replace(" ", ""), ref.replace(" ", "")
    precs, recs = [], []
    for k in range(1, n + 1):
        h = [hyp[i:i + k] for i in range(len(hyp) - k + 1)]
        r = [ref[i:i + k] for i in range(len(ref) - k + 1)]
        if not h or not r:
            continue
        rc = {}
        for g in r:
            rc[g] = rc.get(g, 0) + 1
        match = 0
        for g in h:
            if rc.get(g, 0) > 0:
                rc[g] -= 1
                match += 1
        precs.append(match / len(h))
        recs.append(match / len(r))
    if not precs:
        return 0.0
    p, r = sum(precs) / len(precs), sum(recs) / len(recs)
    return 0.0 if p + r == 0 else 100 * (1 + beta ** 2) * p * r / (beta ** 2 * p + r)


def model(manifest, pair, work):
    base = manifest["baseUrl"].rstrip("/")
    best = max(manifest["models"][pair], key=lambda e: RELEASE.get(e.get("releaseStatus"), 0) * 10 + SIZE.get(e.get("architecture"), 0))
    out, size = {}, 0
    d = os.path.join(work, pair)
    os.makedirs(d, exist_ok=True)
    for key in ("model", "lexicalShortlist", "vocab", "srcVocab", "trgVocab"):
        info = best["files"].get(key)
        if not info:
            continue
        data = fetch(f"{base}/{info['path']}")
        size += len(data)
        if info["path"].endswith(".gz"):
            data = gzip.decompress(data)
        path = os.path.join(d, os.path.basename(info["path"]).removesuffix(".gz"))
        with open(path, "wb") as f:
            f.write(data)
        out[key] = path
    return out, best.get("architecture"), best.get("releaseStatus"), size


def run(exe, files, lines):
    vocab = files.get("vocab") or files.get("srcVocab")
    cmd = [exe, files["model"], vocab, files["lexicalShortlist"]] + ([files["trgVocab"]] if "trgVocab" in files else [])
    res = subprocess.run(cmd, input="\n".join(lines) + "\n", capture_output=True, text=True, timeout=600)
    if res.returncode != 0:
        raise SystemExit(f"motore fallito: {res.stderr[-2000:]}")
    timing = dict(zip(res.stderr.split()[::2], res.stderr.split()[1::2]))
    return res.stdout.rstrip("\n").split("\n"), timing


def main():
    exe, work = sys.argv[1], sys.argv[2]
    manifest = json.loads(fetch(MANIFEST))
    report = ["### Eurise Traduci (prototipo): modelli di Firefox con slimt compilato dai sorgenti", ""]
    rows = []
    samples = []
    for pair, src_idx in (("it-en", 0), ("en-it", 1)):
        files, arch, status, size = model(manifest, pair, work)
        sources = [p[src_idx] for p in PAIRS]
        refs = [p[1 - src_idx] for p in PAIRS]
        hyps, timing = run(exe, files, sources)
        score = sum(chrf(h, r) for h, r in zip(hyps, refs)) / len(refs)
        words = int(timing.get("parole", "1"))
        ms = int(timing.get("traduzione_ms", "0"))
        rows.append(f"| {pair} | {arch} ({status}) | {size / 1048576:.1f} MB | {score:.1f} | {timing.get('caricamento_ms')} | {words / max(ms, 1) * 1000:.0f} |")
        samples += [f"| {pair} | {s} | {h} |" for s, h in list(zip(sources, hyps))[:4]]
    report += [
        "| Direzione | Modello | Download | chrF | Caricamento ms (CI) | Parole/s (CI, 1 thread) |",
        "| --- | --- | ---: | ---: | ---: | ---: |",
        *rows,
        "",
        "chrF: somiglianza con una traduzione di riferimento fatta a mano (0–100; sopra 60 di solito è una buona traduzione).",
        "La velocità sul computer di compilazione non è quella del telefono: si misura nell'app.",
        "",
        "| Direzione | Testo | Traduzione |",
        "| --- | --- | --- |",
        *samples,
    ]
    text = "\n".join(report)
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as f:
            f.write(text + "\n")


if __name__ == "__main__":
    main()

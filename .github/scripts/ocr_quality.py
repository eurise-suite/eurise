"""Qualità della lettura del testo (OCR) in italiano con Tesseract, il motore del prototipo di Eurise Leggi.

Disegna pagine di prova simili a documenti veri (bolletta, avviso pagoPA, fattura, lettera), le rovina come una
scansione o una foto (rotazione, sfocatura, rumore, JPEG, risoluzione bassa) e le fa leggere con i modelli
"fast" (quelli che il telefono scaricherebbe) e "best". Misura l'errore sui caratteri (CER), le parole giuste e
se si ritrovano i dati che servono davvero: importi, date di scadenza, codici.

Uso: python3 ocr_quality.py <cartella tessdata_fast> <cartella tessdata_best>
Scrive una tabella nel riepilogo della compilazione.
"""
import io
import os
import random
import re
import subprocess
import sys
import tempfile
import time

from PIL import Image, ImageDraw, ImageFilter, ImageFont

DOCS = {
    "Bolletta": """ENERGIA ITALIA S.p.A.
Bolletta n. 2026/448812 del 03/11/2026
Intestatario: Mario Rossi, Via Garibaldi 12, 40121 Bologna
Periodo di fatturazione: settembre - ottobre 2026
Consumo rilevato: 312 kWh
Totale da pagare: 87,45 euro
Scadenza: 24/11/2026
Codice cliente: 7701234567""",
    "pagoPA": """AVVISO DI PAGAMENTO pagoPA
Ente creditore: Comune di Firenze
Oggetto: Tassa sui rifiuti (TARI) 2026 - prima rata
Codice avviso: 3012 0000 4455 6677 88
Importo: 142,00 euro
Da pagare entro il 16/12/2026
CF ente: 01307110484""",
    "Fattura": """Fattura n. 58/2026 del 28 ottobre 2026
Studio Bianchi - P.IVA 01234567890
Spett.le Verdi S.r.l., Corso Vittorio Emanuele II 45, 10121 Torino
Consulenza tecnica ottobre 2026      1.500,00
Contributo integrativo 4%                60,00
IVA 22%                                 343,20
Ritenuta d'acconto 20%                 -300,00
Netto a pagare                        1.603,20 euro
IBAN IT60 X054 2811 1010 0000 0123 456""",
    "Lettera": """Gentile cliente,
le comunichiamo che dal 1 gennaio 2027 cambieranno le condizioni del suo contratto.
Potrà recedere senza costi entro trenta giorni dalla ricezione di questa lettera,
scrivendo all'indirizzo PEC servizio.clienti@pec.esempio.it.
Cordiali saluti, l'Ufficio clienti""",
}

# Dati che il prototipo dovrà ritrovare per proporre scadenze e importi.
FIELDS = {
    "Bolletta": ["87,45", "24/11/2026", "7701234567"],
    "pagoPA": ["142,00", "16/12/2026", "3012 0000 4455 6677 88"],
    "Fattura": ["1.603,20", "01234567890", "IT60 X054 2811 1010 0000 0123 456"],
    "Lettera": ["1 gennaio 2027", "servizio.clienti@pec.esempio.it"],
}

CONDITIONS = {
    "Scansione 300 dpi": dict(scale=1.0, angle=0.4, blur=0.3, noise=4, jpeg=90),
    "Foto buona": dict(scale=0.75, angle=1.5, blur=0.7, noise=10, jpeg=75),
    "Foto difficile": dict(scale=0.55, angle=3.0, blur=1.1, noise=18, jpeg=55),
}


def font(size):
    for path in ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def page(text):
    # A4 a 300 dpi, testo a 11 punti.
    img = Image.new("L", (2480, 900 + 75 * text.count("\n")), 255)
    d = ImageDraw.Draw(img)
    d.multiline_text((180, 180), text, font=font(46), fill=0, spacing=26)
    return img


def spoil(img, scale, angle, blur, noise, jpeg, seed):
    rnd = random.Random(seed)
    img = img.rotate(angle * (1 if rnd.random() > 0.5 else -1), resample=Image.BICUBIC, expand=True, fillcolor=245)
    img = img.resize((int(img.width * scale), int(img.height * scale)), Image.BILINEAR)
    img = img.filter(ImageFilter.GaussianBlur(blur))
    px = img.load()
    for _ in range(img.width * img.height // 40):
        x, y = rnd.randrange(img.width), rnd.randrange(img.height)
        px[x, y] = max(0, min(255, px[x, y] + rnd.randint(-noise, noise) * 3))
    out = io.BytesIO()
    img.save(out, "JPEG", quality=jpeg)
    out.seek(0)
    return Image.open(out)


def ocr(img, tessdata):
    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as f:
        img.save(f.name)
        start = time.time()
        res = subprocess.run(["tesseract", f.name, "stdout", "-l", "ita", "--tessdata-dir", tessdata, "--psm", "6"],
                             capture_output=True, text=True)
        return res.stdout, time.time() - start


def norm(s):
    return re.sub(r"\s+", " ", s).strip()


def cer(ref, hyp):
    a, b = norm(ref), norm(hyp)
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1] / max(1, len(a))


def words_ok(ref, hyp):
    r, h = norm(ref).split(), set(norm(hyp).split())
    return sum(w in h for w in r) / max(1, len(r))


def main():
    models = {"fast": sys.argv[1], "best": sys.argv[2]}
    rows = []
    for model, tessdata in models.items():
        for cond, params in CONDITIONS.items():
            cers, words, found, total, secs = [], [], 0, 0, 0.0
            for i, (name, text) in enumerate(DOCS.items()):
                img = spoil(page(text), seed=i, **params)
                hyp, t = ocr(img, tessdata)
                secs += t
                cers.append(cer(text, hyp))
                words.append(words_ok(text, hyp))
                flat = norm(hyp)
                for field in FIELDS[name]:
                    total += 1
                    found += norm(field) in flat
            rows.append((model, cond, 100 * (1 - sum(cers) / len(cers)), 100 * sum(words) / len(words), found, total, secs / len(DOCS)))
    sizes = {m: os.path.getsize(os.path.join(p, "ita.traineddata")) / 1048576 for m, p in models.items()}
    lines = [
        "### Eurise Leggi: qualità di Tesseract in italiano",
        "",
        f"Modello italiano: fast {sizes['fast']:.1f} MB, best {sizes['best']:.1f} MB (si scarica a richiesta, fuori dall'APK).",
        "",
        "| Modello | Condizione | Caratteri giusti % | Parole giuste % | Dati ritrovati | s per pagina (CI) |",
        "| --- | --- | ---: | ---: | ---: | ---: |",
    ]
    lines += [f"| {m} | {c} | {a:.1f} | {w:.1f} | {f}/{t} | {s:.1f} |" for m, c, a, w, f, t, s in rows]
    lines += ["", "Dati ritrovati = importi, scadenze e codici trovati identici nel testo letto (servono per le proposte Da fare)."]
    report = "\n".join(lines)
    print(report)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as f:
            f.write(report + "\n")


if __name__ == "__main__":
    main()

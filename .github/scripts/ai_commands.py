"""Eurise AI, comandi di Alfred: frasi dette (anche con errori di trascrizione) → azioni attese. Misura quante ne
capisce il modello e quanto ci mette (sul computer di compilazione: conta il confronto tra modelli, non il tempo assoluto).
Uso: ai_commands.py <eseguibile> <modello.gguf> [thread]"""
import json
import os
import subprocess
import sys

# Oggi per la prova: mercoledì 11 novembre 2026. Ogni voce: frase, tipi attesi in ordine, campi da controllare.
CASES = [
    ("chiama Marco", ["chiama"], {"chi": "marco"}),
    ("Chiamo Laura.", ["chiama"], {"chi": "laura"}),
    ("fai una telefonata alla mamma", ["chiama"], {"chi": "mamma"}),
    ("scrivi una nota", ["nota"], {}),
    ("prendi nota che il codice del cancello è 4512", ["nota"], {"testo": "4512"}),
    ("aggiungi al calendario dentista martedì alle 9", ["evento"], {"data": "2026-11-17", "ora": "09:00"}),
    ("domani dopo pranzo ricordami di chiamare il commercialista", ["promemoria"], {"data": "2026-11-12", "ora": "14:00"}),
    ("manda un whatsapp a Luca che arrivo tra dieci minuti", ["messaggio"], {"chi": "luca", "canale": "whatsapp"}),
    ("svegliami domani alle sette e mezza", ["sveglia"], {"ora": "07:30"}),
    ("metti un timer di 12 minuti", ["timer"], {"testo": "12"}),
    ("portami in via Garibaldi 10 a Torino", ["naviga"], {"chi": "garibaldi"}),
    ("quanto deve cuocere un uovo sodo", ["domanda"], {}),
    ("chi ha scritto i promessi sposi", ["domanda"], {}),
    ("venerdì alle 20 cena da Giulia e dillo ad Anna su telegram", ["evento", "messaggio"], {"ora": "20:00"}),
    ("comprare il latte", ["da_fare"], {"testo": "latte"}),
    ("ho qualcosa venerdì pomeriggio", ["dati"], {"data": "2026-11-13"}),
    ("quando devo portare la macchina dal meccanico", ["dati"], {"testo": "meccanico"}),
]
FIELDS = ["tipo", "chi", "testo", "data", "ora", "canale"]


def parse(text: str) -> list:
    rows = []
    for line in text.strip().splitlines():
        parts = (line.split("|") + [""] * 6)[:6]
        rows.append(dict(zip(FIELDS, [p.strip() for p in parts])))
    return rows


def main() -> int:
    exe, model = sys.argv[1], sys.argv[2]
    threads = sys.argv[3] if len(sys.argv) > 3 else "4"
    env = dict(os.environ, EURISE_COMANDI="1")
    sentences = "\n".join(c[0] for c in CASES) + "\n"
    run = subprocess.run([exe, model, threads, "2026-11-11"], input=sentences, capture_output=True, text=True, env=env, timeout=3000)
    lines = [json.loads(l) for l in run.stdout.splitlines() if l.startswith("{")]
    head, answers = lines[0], [l for l in lines[1:] if "frase" in l]
    good, ms = 0, []
    report = [f"### Comandi di Alfred · {head.get('model')}", "", "| frase | atteso | capito | ms |", "|---|---|---|---|"]
    for (sentence, kinds, fields), r in zip(CASES, answers):
        rows = parse(r.get("text", ""))
        got = [x["tipo"] for x in rows]
        ok = got == kinds and all(v in (rows[0].get(k, "").lower()) for k, v in fields.items())
        good += ok
        t = r.get("ms_prompt", 0) + r.get("ms_gen", 0)
        ms.append(t)
        report.append(f"| {sentence} | {', '.join(kinds)} {fields or ''} | {'✓' if ok else '✗'} `{r.get('text', '').replace(chr(10), ' ⏎ ')}` | {t:.0f} |")
    report += ["", f"**Giuste: {good} su {len(CASES)}** · tempo medio {sum(ms) / max(len(ms), 1):.0f} ms (computer di compilazione)", ""]
    text = "\n".join(report)
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as f:
            f.write(text + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())

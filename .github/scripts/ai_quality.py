"""Eurise AI: fa girare il motore compilato dai sorgenti (eurise_ai_test) con il modello scaricato, sulle stesse frasi di
prova dell'app (ai/src/main/res/raw/frasi_prova.txt, oggi = 2026-11-11), e misura comprensione e velocità.
La velocità del computer di compilazione non vale per il telefono: conta la misura fatta con l'app.
Uso: ai_quality.py <eseguibile> <modello.gguf> <frasi.txt> [thread]"""
import json
import os
import subprocess
import sys

# Cosa ci si aspetta da ogni frase (nell'ordine del file). Per «tipo» basta una delle voci; i campi assenti non contano.
EXPECTED = [
    {"tipo": ["evento"], "data": "2026-11-12", "ora": "15:00", "persone": ["Marco"]},
    {"tipo": ["da_fare", "promemoria"], "data": "2026-11-13", "ora": None},
    {"tipo": ["evento"], "data": "2026-11-14", "ora": "20:30", "persone": ["Giulia"], "luogo": "Roma"},
    {"tipo": ["da_fare"], "data": None, "ora": None},
    {"tipo": ["nota"], "data": None},
    {"tipo": ["evento"], "data": "2026-11-16", "ora": "09:00", "persone": ["Anna", "Paolo"], "luogo": "ufficio"},
    {"tipo": ["da_fare", "promemoria"], "data": "2026-11-12", "ora": None},
    {"tipo": ["evento"], "data": "2026-12-03", "ora": "18:00"},
    {"tipo": ["promemoria"], "data": "2026-11-12", "ora": "06:45"},
    {"tipo": ["nota"], "data": None, "ora": None},
]


def check(out: dict, exp: dict) -> list:
    """Campi sbagliati della risposta."""
    wrong = []
    for key, want in exp.items():
        got = out.get(key)
        if key == "tipo":
            ok = got in want
        elif key == "persone":
            names = " ".join(got or []).lower()
            ok = all(n.lower() in names for n in want)
        elif key == "luogo":
            ok = got is not None and want.lower() in got.lower()
        else:
            ok = got == want
        if not ok:
            wrong.append(f"{key}={got!r}")
    return wrong


def main() -> None:
    exe, model, sentences = sys.argv[1], sys.argv[2], sys.argv[3]
    threads = sys.argv[4] if len(sys.argv) > 4 else "4"
    with open(sentences, encoding="utf-8") as f:
        text = f.read()
    proc = subprocess.run([exe, model, threads, "2026-11-11"], input=text, capture_output=True, text=True, timeout=3600)
    if proc.returncode != 0:
        print(proc.stderr[-3000:], file=sys.stderr)
        sys.exit(f"eurise_ai_test è uscito con {proc.returncode}")
    rows = [json.loads(line) for line in proc.stdout.splitlines() if line.startswith("{")]
    head, captures = rows[0], [r for r in rows if "frase" in r]
    question = next(r for r in rows if r.get("domanda"))
    bench = next(r for r in rows if r.get("bench"))

    fields_ok = fields_all = sentences_ok = 0
    table = ["| Frase | Risposta | Errori | s |", "| --- | --- | --- | ---: |"]
    for r, exp in zip(captures, EXPECTED):
        try:
            out = json.loads(r["text"])
            wrong = check(out, exp)
        except (ValueError, KeyError):
            wrong = ["JSON non valido"]
        fields_all += len(exp)
        fields_ok += len(exp) - len(wrong) if wrong != ["JSON non valido"] else 0
        sentences_ok += not wrong
        secs = (r["ms_prompt"] + r["ms_gen"]) / 1000
        table.append(f"| {r['frase']} | `{r['text']}` | {', '.join(wrong) or '—'} | {secs:.1f} |")

    def speed(n, ms):
        return n * 1000 / ms if ms else 0

    avg = sum((r["ms_prompt"] + r["ms_gen"]) for r in captures) / max(1, len(captures)) / 1000
    lines = [
        f"### Eurise AI: {head['model']}",
        "",
        f"- Modello: {head['bytes'] / 1e9:.2f} GB di pesi, {head['params'] / 1e9:.2f} miliardi di parametri, caricato in {head['ms_load'] / 1000:.1f} s",
        f"- Comprensione (cattura rapida): **{sentences_ok}/{len(captures)} frasi giuste**, {fields_ok}/{fields_all} campi giusti",
        f"- Velocità sul computer di compilazione ({threads} thread, non indicativa del telefono): lettura "
        f"{speed(bench['n_prompt'], bench['ms_prompt']):.1f} token/s, scrittura {speed(bench['n_gen'], bench['ms_gen']):.1f} token/s; "
        f"cattura in media {avg:.1f} s per frase",
        "",
        *table,
        "",
        f"Domanda libera ({question['n_gen']} token in {question['ms_gen'] / 1000:.1f} s): {question['text']}",
        "",
    ]
    report = "\n".join(lines)
    print(report)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            f.write(report + "\n")
    print(f"::notice::Eurise AI: {sentences_ok}/{len(captures)} frasi giuste, {fields_ok}/{fields_all} campi; "
          f"computer: scrittura {speed(bench['n_gen'], bench['ms_gen']):.1f} token/s")


if __name__ == "__main__":
    main()

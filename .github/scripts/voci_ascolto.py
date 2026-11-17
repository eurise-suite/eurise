"""Campioni da ascoltare per scegliere una voce italiana più naturale per Alfred (17 novembre).

Ogni motore legge le stesse frasi; per i motori con più voci, ogni voce si presenta col suo numero («Voce 3.»), e
l'elenco numero → nome finisce in voci.txt. Stampa anche quanto ci mette (RTF sul processore del server, solo come
confronto tra motori: sul telefono è più lento). Uso: voci_ascolto.py <motore> <cartella d'uscita>.
"""
import json
import os
import subprocess
import sys
import time
from pathlib import Path

import numpy as np
import soundfile as sf

TEXT = ("Buongiorno. Oggi ha tre impegni: alle dieci il dentista, alle quindici la riunione con Marco, "
        "e stasera la cena da Giulia. Chiamo Marco? Annotato. Carta canta, diceva mia zia.")

engine, out = sys.argv[1], Path(sys.argv[2])
out.mkdir(parents=True, exist_ok=True)
pieces, names, rate = [], [], None
log = []


def add(i, name, audio, sr, seconds):
    global rate
    rate = rate or sr
    if sr != rate:
        raise SystemExit(f"frequenze diverse: {sr} e {rate}")
    pieces.append(np.asarray(audio, dtype=np.float32).reshape(-1))
    pieces.append(np.zeros(int(sr * 0.8), dtype=np.float32))
    dur = len(pieces[-2]) / sr
    names.append(f"Voce {i}: {name}")
    log.append(f"{engine} {name}: {dur:.1f} s di audio in {seconds:.1f} s (RTF {seconds / max(dur, 0.01):.2f})")
    print(log[-1], flush=True)


if engine == "supertonic":
    from supertonic import TTS
    tts = TTS(auto_download=True)
    for i, v in enumerate(["M1", "M2", "M3", "M4", "M5", "F1", "F2", "F3", "F4", "F5"], 1):
        style = tts.get_voice_style(voice_name=v)
        t = time.time()
        wav, _ = tts.synthesize(f"Voce {i}. " + TEXT, voice_style=style, lang="it")
        add(i, v, wav, tts.sample_rate if hasattr(tts, "sample_rate") else 44100, time.time() - t)

elif engine == "serena":
    from huggingface_hub import hf_hub_download
    for i, q in enumerate(["medium", "high"], 1):
        repo = f"committa/it_IT-serena-{q}"
        model = hf_hub_download(repo, f"it_IT-serena-{q}.onnx")
        hf_hub_download(repo, f"it_IT-serena-{q}.onnx.json")
        wav = out / f"serena-{q}.wav"
        t = time.time()
        subprocess.run([sys.executable, "-m", "piper", "-m", model, "-f", str(wav), "--", f"Voce {i}. " + TEXT], check=True)
        a, sr = sf.read(wav, dtype="float32")
        add(i, f"Serena {q} (Piper)", a, sr, time.time() - t)

elif engine == "pocket":
    # Runtime ONNX della comunità (codice Apache 2.0) con i pesi di Kyutai (CC BY 4.0), pacchetto italiano a 6 strati.
    from huggingface_hub import snapshot_download
    root = Path(snapshot_download("lookbe/pocket-tts-onnx", allow_patterns=["*.py", "*.txt", "*.json", "onnx/italian/**"]))
    for p in sorted(root.rglob("*"))[:200]:
        print("  ", p.relative_to(root), flush=True)
    sys.path.insert(0, str(root))
    from pocket_tts_onnx import PocketTTSOnnx  # noqa: E402
    os.chdir(root)
    tts = PocketTTSOnnx(models_dir="onnx", language="italian")
    bundle = json.loads((root / "onnx" / "italian" / "bundle.json").read_text())
    voices = sorted(p.stem for p in (root / "onnx" / "italian").rglob("*.safetensors"))
    if not voices:
        found = []
        def walk(x):
            if isinstance(x, dict):
                for k, v in x.items():
                    if "voice" in k.lower() and isinstance(v, (dict, list)):
                        found.extend(v.keys() if isinstance(v, dict) else [e if isinstance(e, str) else e.get("name") for e in v])
                    walk(v)
            elif isinstance(x, list):
                for v in x:
                    walk(v)
        walk(bundle)
        voices = [v for v in dict.fromkeys(found) if v]
    print("voci:", voices, flush=True)
    sr = getattr(tts, "sample_rate", None) or bundle.get("sample_rate", 24000)
    for i, v in enumerate(voices, 1):
        t = time.time()
        a = tts.generate(f"Voce {i}. " + TEXT, voice=v)
        add(i, v, a, sr, time.time() - t)
else:
    raise SystemExit("motore sconosciuto: " + engine)

sf.write(out / f"{engine}.wav", np.concatenate(pieces), rate)
(out / f"{engine}.txt").write_text("\n".join(names + [""] + log) + "\n")

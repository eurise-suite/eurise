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
    # Pesi di Kyutai (CC BY 4.0) in ONNX, pacchetto italiano a 6 strati (mirror OpenVoiceOS), letti col runtime ONNX della
    # comunità (KevinAHM/pocket-tts-onnx, clonato dal lavoro in /tmp/ptts; approvato dal proprietario il 17 novembre).
    from huggingface_hub import snapshot_download
    weights = Path(snapshot_download("OpenVoiceOS/phoonnx-pocket-tts", allow_patterns=["italian/*", "italian/voices/*"]))
    voices = sorted(p.stem for p in (weights / "italian").rglob("*.safetensors"))
    runtime = Path("/tmp/ptts")
    src = next((p for p in runtime.rglob("*.py") if "class PocketTTSOnnx" in p.read_text(errors="ignore")), None)
    if src is None:
        raise SystemExit("runtime non trovato: " + str(sorted(str(p) for p in runtime.rglob("*.py"))[:40]))
    print("runtime:", src, flush=True)
    models = Path("/tmp/ptts-models")
    models.mkdir(exist_ok=True)
    if not (models / "italian").exists():
        (models / "italian").symlink_to(weights / "italian")
    sys.path.insert(0, str(src.parent))
    mod = __import__(src.stem)
    import inspect
    print(inspect.signature(mod.PocketTTSOnnx.__init__), inspect.signature(mod.PocketTTSOnnx.generate), flush=True)
    os.chdir(src.parent)
    try:
        tts = mod.PocketTTSOnnx(models_dir=str(models), language="italian")
    except TypeError:
        tts = mod.PocketTTSOnnx(models_dir=str(models / "italian"))
    sr = getattr(tts, "sample_rate", None) or getattr(tts, "SAMPLE_RATE", None) or 24000
    for i, v in enumerate(voices, 1):
        t = time.time()
        try:
            a = tts.generate(f"Voce {i}. " + TEXT, voice=v)
        except Exception as e:  # noqa: BLE001
            print("  voce", v, "per nome no:", e, flush=True)
            a = tts.generate(f"Voce {i}. " + TEXT, voice=str(next((weights / "italian").rglob(v + ".safetensors"))))
        add(i, v, a, sr, time.time() - t)
else:
    raise SystemExit("motore sconosciuto: " + engine)

sf.write(out / f"{engine}.wav", np.concatenate(pieces), rate)
(out / f"{engine}.txt").write_text("\n".join(names + [""] + log) + "\n")

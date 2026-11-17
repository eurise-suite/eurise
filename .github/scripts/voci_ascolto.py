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
    # Pesi di Kyutai (CC BY 4.0) in ONNX, pacchetto italiano, con il motore pockettts di phoonnx (OpenVoiceOS).
    from huggingface_hub import HfApi
    repo = "OpenVoiceOS/phoonnx-pocket-tts"
    files = HfApi().list_repo_files(repo)
    voices = sorted(Path(f).stem for f in files if f.startswith("italian/") and f.endswith(".safetensors"))
    print("file italiani:", [f for f in files if f.startswith("italian/")], flush=True)
    from phoonnx.model_manager import TTSModelManager
    manager = TTSModelManager()
    for i, v in enumerate(voices, 1):
        voice = None
        for vid in (f"pockettts/it/{v}", f"pockettts/it-IT/{v}", f"pockettts/italian/{v}"):
            try:
                voice = manager.download_voice_by_id(vid).load()
                break
            except Exception as e:  # noqa: BLE001
                print("  no", vid, e, flush=True)
        if voice is None:
            continue
        t = time.time()
        wav = out / f"p{i}.wav"
        a = voice.synthesize_wav(f"Voce {i}. " + TEXT)
        if isinstance(a, (str, Path)):
            a, sr = sf.read(a, dtype="float32")
        elif hasattr(a, "readframes") or hasattr(a, "getbuffer"):
            raise SystemExit("formato inatteso da synthesize_wav")
        else:
            sr = getattr(voice, "sample_rate", 24000)
        add(i, v, a, sr, time.time() - t)
else:
    raise SystemExit("motore sconosciuto: " + engine)

sf.write(out / f"{engine}.wav", np.concatenate(pieces), rate)
(out / f"{engine}.txt").write_text("\n".join(names + [""] + log) + "\n")

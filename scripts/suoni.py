#!/usr/bin/env python3
"""Suonerie di Eurise, sintetizzate da zero (nessun campione, nessuna registrazione altrui).

Le melodie dei «grandi classici» sono composizioni in pubblico dominio (autori morti da oltre 70 anni); l'esecuzione
è questa sintesi, quindi anche i file sono liberi (CC0, come tutto ciò che esce da questo script).

Uso: python3 scripts/suoni.py OUT_DIR
Scrive in OUT_DIR/apk i suoni che stanno dentro l'APK (pochi e piccoli) e in OUT_DIR/pacchetto quelli che si scaricano a
richiesta, con il file index.txt (id, gruppo, uso, titolo, sottotitolo, secondi). Serve ffmpeg con libvorbis.
"""
import os
import subprocess
import sys
import wave

import numpy as np

SR = 32000
rng = np.random.default_rng(7)

NOTE = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def hz(name):
    """'C#5' → frequenza."""
    n = NOTE[name[0]]
    i = 1
    while name[i] in "#b":
        n += 1 if name[i] == "#" else -1
        i += 1
    octave = int(name[i:])
    midi = 12 * (octave + 1) + n
    return 440.0 * 2 ** ((midi - 69) / 12)


def env(n, attack, decay):
    t = np.arange(n) / SR
    a = np.minimum(1.0, t / max(attack, 1e-4))
    return a * np.exp(-t / decay)


# ---------------------------------------------------------------- strumenti

def piano(f, dur, vel=0.8):
    n = int((dur + 1.2) * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    b = 0.0004  # inarmonicità della corda
    for k in range(1, 9):
        fk = f * k * np.sqrt(1 + b * k * k)
        if fk > SR / 2.2:
            break
        amp = (1.0 / k ** 1.1) * (0.6 + 0.4 * vel)
        dec = 1.6 / (1 + 0.45 * k) * (440 / max(f, 110)) ** 0.35
        out += amp * np.sin(2 * np.pi * fk * t + rng.random() * 6.28) * np.exp(-t / dec)
    out *= np.minimum(1, t / 0.004)
    hammer = rng.standard_normal(int(0.01 * SR)) * np.exp(-np.arange(int(0.01 * SR)) / (0.002 * SR)) * 0.08
    out[: len(hammer)] += hammer
    # Smorzatore: la nota finisce morbida dopo la sua durata.
    off = int(dur * SR)
    out[off:] *= np.exp(-np.arange(n - off) / (0.18 * SR))
    return out * vel


def harp(f, dur, vel=0.8):
    """Corda pizzicata (Karplus-Strong) con un filo di brillantezza."""
    n = int((dur + 2.0) * SR)
    p = max(2, int(round(SR / f)))
    buf = rng.uniform(-1, 1, p)
    buf = np.convolve(buf, [0.5, 0.5], mode="same")
    out = np.zeros(n)
    damp = 0.996 if f < 600 else 0.993
    for i in range(n):
        j = i % p
        out[i] = buf[j]
        buf[j] = damp * 0.5 * (buf[j] + buf[(j + 1) % p])
    out *= np.minimum(1, np.arange(n) / (0.002 * SR))
    return out * vel * 0.9


def marimba(f, dur, vel=0.8):
    n = int((dur + 0.9) * SR)
    t = np.arange(n) / SR
    out = (np.sin(2 * np.pi * f * t) * np.exp(-t / 0.55)
           + 0.35 * np.sin(2 * np.pi * f * 3.93 * t) * np.exp(-t / 0.09)
           + 0.12 * np.sin(2 * np.pi * f * 9.2 * t) * np.exp(-t / 0.03))
    out *= np.minimum(1, t / 0.003)
    return out * vel


def bell(f, dur, vel=0.8, glass=False):
    n = int((dur + 2.5) * SR)
    t = np.arange(n) / SR
    ratios = [1, 2.0, 2.76, 5.40, 8.93] if glass else [0.5, 1, 1.19, 1.56, 2.0, 2.51, 3.01]
    amps = [1, 0.4, 0.5, 0.25, 0.12] if glass else [0.4, 1, 0.6, 0.35, 0.3, 0.18, 0.12]
    out = np.zeros(n)
    for r, a in zip(ratios, amps):
        dec = (2.2 if glass else 1.8) / r ** 0.6
        out += a * np.sin(2 * np.pi * f * r * t + rng.random()) * np.exp(-t / dec)
    out *= np.minimum(1, t / 0.002)
    return out * vel * 0.6


def flute(f, dur, vel=0.8):
    n = int((dur + 0.25) * SR)
    t = np.arange(n) / SR
    vib = 1 + 0.004 * np.sin(2 * np.pi * 5.2 * t) * np.minimum(1, t / 0.4)
    ph = 2 * np.pi * np.cumsum(f * vib) / SR
    tone = np.sin(ph) + 0.18 * np.sin(2 * ph) + 0.05 * np.sin(3 * ph)
    breath = np.convolve(rng.standard_normal(n), np.ones(12) / 12, mode="same") * 0.04
    a = np.minimum(1, t / 0.06)
    rel = np.ones(n)
    off = int(dur * SR)
    rel[off:] = np.exp(-np.arange(n - off) / (0.06 * SR))
    return (tone + breath) * a * rel * vel * 0.7


def strings(f, dur, vel=0.8):
    """Archi spiccati: dente di sega addolcito, attacco breve."""
    n = int((dur + 0.3) * SR)
    t = np.arange(n) / SR
    vib = 1 + 0.003 * np.sin(2 * np.pi * 5.6 * t)
    ph = np.cumsum(f * vib) / SR
    saw = sum(np.sin(2 * np.pi * k * ph) / k for k in range(1, 10) if f * k < SR / 2.2)
    a = np.minimum(1, t / 0.025)
    rel = np.ones(n)
    off = int(dur * SR)
    rel[off:] = np.exp(-np.arange(n - off) / (0.08 * SR))
    body = saw * a * rel * np.exp(-t / 1.4)
    return np.convolve(body, np.ones(4) / 4, mode="same") * vel * 0.45


def drop(f0, dur=0.22, vel=0.8):
    """Goccia d'acqua: sinusoide che sale in fretta."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    f = f0 * (1 + 1.8 * (1 - np.exp(-t / 0.03)))
    ph = 2 * np.pi * np.cumsum(f) / SR
    return np.sin(ph) * env(n, 0.002, 0.05) * vel


INSTR = {"piano": piano, "harp": harp, "marimba": marimba, "bell": bell, "glass": lambda f, d, v=0.8: bell(f, d, v, True),
         "flute": flute, "strings": strings}


# ---------------------------------------------------------------- partiture

def render(voices, bpm, length=None):
    """voices: [(strumento, [(nota|None, battiti, vel)], volume)]; battito = semiminima."""
    beat = 60.0 / bpm
    total = 0
    parts = []
    for instr, notes, vol in voices:
        t = 0.0
        events = []
        for note, beats, vel in notes:
            d = beats * beat
            if note:
                for nm in note.split("+"):
                    events.append((t, INSTR[instr](hz(nm), d, vel) * vol))
            t += d
        end = int(t * SR) + 3 * SR
        total = max(total, end)
        parts.append(events)
    if length:
        total = int(length * SR)
    out = np.zeros(total)
    for events in parts:
        for t, sig in events:
            i = int(t * SR)
            if i >= total:
                continue
            k = min(len(sig), total - i)
            out[i:i + k] += sig[:k]
    return out


def reverb(x, size=1.4, mix=0.22):
    n = int(size * SR)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / (size / 5.5 * SR))
    ir = np.convolve(ir, np.ones(6) / 6, mode="same")
    ir /= np.sqrt(np.sum(ir ** 2))
    wet = np.fft.irfft(np.fft.rfft(x, len(x) + n) * np.fft.rfft(ir, len(x) + n))[: len(x)]
    return (1 - mix) * x + mix * wet


def finish(x, seconds, fade=1.2):
    x = reverb(x)
    n = int(seconds * SR)
    x = x[:n] if len(x) >= n else np.pad(x, (0, n - len(x)))
    f = int(fade * SR)
    x[-f:] *= np.linspace(1, 0, f) ** 2
    x[: int(0.004 * SR)] *= np.linspace(0, 1, int(0.004 * SR))
    peak = np.max(np.abs(x)) or 1
    x = x / peak
    # Volume percepito simile tra i suoni: un limitatore morbido alza quelli fatti di soli attacchi (arpa, gocce).
    rms = float(np.sqrt(np.mean(x ** 2))) or 1
    g = min(3.0, max(1.0, 0.16 / rms))
    x = np.tanh(x * g)
    return x / (np.max(np.abs(x)) or 1) * 0.89


def seq(spec, vel=0.8):
    """'E5:0.5 D#5:0.5 -:1' → note; '-' è una pausa, 'A3+E4' un accordo."""
    out = []
    for tok in spec.split():
        n, b = tok.split(":")
        out.append((None if n == "-" else n, float(b), vel))
    return out


# Originali di Eurise ------------------------------------------------------

def alba():
    up = "C4:0.5 G4:0.5 D5:0.5 E5:0.5 G5:0.5 D5:0.5 E5:0.5 B5:1.5 "
    up2 = "A3:0.5 E4:0.5 C5:0.5 D5:0.5 E5:0.5 C5:0.5 G5:0.5 E5:1.5 "
    up3 = "F3:0.5 C4:0.5 A4:0.5 C5:0.5 E5:0.5 G5:0.5 A5:0.5 G5:1.5 "
    up4 = "G3:0.5 D4:0.5 B4:0.5 D5:0.5 F#5:0.5 A5:0.5 B5:0.5 D6:1.5 "
    melody = seq(up + up2 + up3 + up4 + up + "C4+G4+E5:4", 0.75)
    bass = seq("C3:5 A2:5 F2:5 G2:5 C3:5 C3:4", 0.6)
    return render([("harp", melody, 1.0), ("harp", bass, 0.7)], bpm=96)


def vetro():
    m = seq("E6:0.5 B5:0.5 G#5:1 -:0.5 E6:0.5 C#6:0.5 B5:1.5 -:0.5 " * 2 + "A5:0.5 B5:0.5 C#6:0.5 E6:2.5", 0.7)
    pad = seq("E4+B4:4 C#4+G#4:4 A3+E4:4 B3+F#4:4 E4+B4:4", 0.25)
    return render([("glass", m, 1.0), ("glass", pad, 0.6)], bpm=84)


def campanelle():
    m = seq("G5:0.5 A5:0.5 B5:0.5 D6:0.5 B5:1 G5:1 " "A5:0.5 B5:0.5 D6:0.5 E6:0.5 D6:2 "
            "E6:0.5 D6:0.5 B5:0.5 A5:0.5 G5:1 E5:1 " "D5:0.5 E5:0.5 G5:0.5 A5:0.5 G5:3", 0.7)
    low = seq("G3:4 D3:4 C3:4 G3:4", 0.5)
    return render([("bell", m, 1.0), ("bell", low, 0.5)], bpm=108)


def marimba_gentile():
    m = seq("D5:0.5 F#5:0.5 A5:0.5 F#5:0.5 B5:0.5 A5:0.5 F#5:1 " "E5:0.5 G5:0.5 B5:0.5 G5:0.5 C#6:0.5 B5:0.5 A5:1 " * 2
            + "D6:0.5 C#6:0.5 B5:0.5 A5:0.5 G5:0.5 F#5:0.5 E5:0.5 D5:1.5", 0.75)
    bass = seq("D3:2 B2:2 A2:2 A2:2 D3:2 B2:2 G2:2 A2:2 D3:4", 0.6)
    return render([("marimba", m, 1.0), ("marimba", bass, 0.8)], bpm=112)


def risveglio():
    m = seq("C5:1 E5:1 G5:1 C6:2 B5:0.5 A5:0.5 G5:2 " "A5:1 F5:1 A5:1 D6:2 C6:0.5 B5:0.5 C6:3", 0.6)
    bass = seq("C3+G3:4 E3+B3:4 F3+C4:4 G3+D4:2 C3+G3:4", 0.4)
    return render([("piano", m, 1.0), ("piano", bass, 0.8)], bpm=92)


def cristalli():
    m = seq("A5:0.25 E6:0.25 C#6:0.25 E6:0.25 " * 4 + "B5:0.25 E6:0.25 D6:0.25 E6:0.25 " * 4 + "A5:2", 0.55)
    return render([("glass", m, 1.0)], bpm=90)


def onde():
    m = seq("G4:1.5 D5:0.5 B4:2 A4:1.5 E5:0.5 C5:2 B4:1.5 G5:0.5 D5:1 C5:1 B4:4", 0.6)
    arp = seq("G3:0.5 D4:0.5 G4:0.5 D4:0.5 " * 2 + "A3:0.5 E4:0.5 A4:0.5 E4:0.5 " * 2 + "G3:0.5 D4:0.5 G4:0.5 D4:0.5 " * 4, 0.4)
    return render([("harp", m, 1.0), ("harp", arp, 0.6)], bpm=88)


def lanterne():
    m = seq("E5:0.5 G5:0.5 A5:1 G5:0.5 E5:0.5 D5:1 " "C5:0.5 D5:0.5 E5:0.5 G5:0.5 A5:2 " * 2 + "C6:1 A5:1 G5:2", 0.7)
    return render([("marimba", m, 1.0), ("marimba", seq("A2:4 F2:4 C3:4 G2:4 A2:4", 0.6), 0.8)], bpm=100)


def mattoni():
    m = seq("C5:0.5 C5:0.5 G5:0.5 G5:0.5 E5:1 C5:1 " "D5:0.5 D5:0.5 A5:0.5 A5:0.5 F5:2 " * 2 + "G5:0.5 E5:0.5 C5:3", 0.7)
    return render([("piano", m, 1.0), ("piano", seq("C3:4 D3:4 D3:4 C3:4", 0.5), 0.7)], bpm=120)


# Grandi classici (composizioni in pubblico dominio) ------------------------

def per_elisa():  # Beethoven, Bagatella WoO 59
    r = ("E5:0.5 D#5:0.5 E5:0.5 D#5:0.5 E5:0.5 B4:0.5 D5:0.5 C5:0.5 A4:1 -:0.5 "
         "C4:0.5 E4:0.5 A4:0.5 B4:1 -:0.5 E4:0.5 G#4:0.5 B4:0.5 C5:1 -:0.5 E4:0.5 ")
    r2 = ("E5:0.5 D#5:0.5 E5:0.5 D#5:0.5 E5:0.5 B4:0.5 D5:0.5 C5:0.5 A4:1 -:0.5 "
          "C4:0.5 E4:0.5 A4:0.5 B4:1 -:0.5 E4:0.5 C5:0.5 B4:0.5 A4:3")
    left = "-:4.5 A2:0.5 E3:0.5 A3:0.5 -:1.5 E2:0.5 E3:0.5 G#3:0.5 -:1.5 A2:0.5 E3:0.5 A3:0.5 -:4.5 A2:0.5 E3:0.5 A3:0.5 -:1.5 E2:0.5 E3:0.5 G#3:0.5 A2+E3+A3:3"
    return render([("piano", seq(r + r2, 0.75), 1.0), ("piano", seq(left, 0.5), 0.8)], bpm=132)


def primavera():  # Vivaldi, La primavera RV 269, Allegro
    m = ("E5:0.5 G#5:0.5 G#5:0.5 G#5:0.5 F#5:0.25 E5:0.25 B5:1.5 B5:0.25 A5:0.25 "
         "G#5:0.5 G#5:0.5 G#5:0.5 F#5:0.25 E5:0.25 B5:1.5 B5:0.25 A5:0.25 "
         "G#5:0.5 A5:0.5 B5:0.5 A5:0.5 G#5:0.5 F#5:0.5 D#5:1 B4:1 "
         "E5:0.5 G#5:0.5 G#5:0.5 G#5:0.5 F#5:0.25 E5:0.25 B5:1.5 B5:0.25 A5:0.25 G#5:0.5 F#5:0.5 E5:2")
    bass = "-:0.5 E3:1 E3:1 E3:1 E3:1 E3:1 E3:1 E3:1 E3:1 B2:1 B2:1 B2:1 B2:1 E3:1 E3:1 E3:1 E3:1 E3:2"
    return render([("strings", seq(m, 0.8), 1.0), ("strings", seq(bass, 0.6), 0.6)], bpm=100)


def mattino():  # Grieg, Peer Gynt: Il mattino
    m = ("G#5:0.5 E5:0.5 D#5:0.5 C#5:0.5 D#5:0.5 E5:0.5 G#5:0.5 E5:0.5 D#5:0.5 C#5:0.5 D#5:0.5 E5:0.5 "
         "D#5:0.5 E5:0.5 G#5:0.5 E5:0.5 G#5:0.5 A5:0.5 E5:0.5 A5:0.5 G#5:0.5 E5:0.5 D#5:0.5 C#5:0.5 "
         "G#5:0.5 E5:0.5 D#5:0.5 C#5:0.5 D#5:0.5 E5:0.5 E5:3")
    pad = seq("E3+B3:3 E3+B3:3 E3+B3:3 A2+E3:3 E3+B3:3 E3+B3:3", 0.35)
    return render([("flute", seq(m, 0.75), 1.0), ("strings", pad, 0.5)], bpm=126)


def serenata():  # Mozart, Eine kleine Nachtmusik K. 525
    m = ("G5:1 -:0.5 D5:0.5 G5:1 -:0.5 D5:0.5 G5:0.5 D5:0.5 G5:0.5 B5:0.5 D6:2 "
         "C6:1 -:0.5 A5:0.5 C6:1 -:0.5 A5:0.5 C6:0.5 A5:0.5 F#5:0.5 A5:0.5 D5:2 "
         "G5:1 G5:0.5 B5:0.25 A5:0.25 G5:0.5 G5:0.5 F#5:0.5 F#5:0.5 E5:0.5 G5:0.5 F#5:0.5 A5:0.5 G5:2")
    bass = "G3:4 G3:4 D3:4 D3:4 G3:2 C3:1 D3:1 D3:2 G3:2"
    return render([("strings", seq(m, 0.8), 1.0), ("strings", seq(bass, 0.6), 0.6)], bpm=124)


def inno():  # Beethoven, Sinfonia n. 9: Inno alla gioia
    m = ("F#5:1 F#5:1 G5:1 A5:1 A5:1 G5:1 F#5:1 E5:1 D5:1 D5:1 E5:1 F#5:1 F#5:1.5 E5:0.5 E5:2 "
         "F#5:1 F#5:1 G5:1 A5:1 A5:1 G5:1 F#5:1 E5:1 D5:1 D5:1 E5:1 F#5:1 E5:1.5 D5:0.5 D5:2")
    left = "D3+A3:4 A2+E3:4 D3+A3:4 A2+E3:2 A2+E3:2 D3+A3:4 A2+E3:4 D3+A3:4 A2+E3:2 D3+A3:2"
    return render([("piano", seq(m, 0.75), 1.0), ("piano", seq(left, 0.45), 0.8)], bpm=116)


def gymnopedie():  # Satie, Gymnopédie n. 1
    left = "G2:1 B3+D4+F#4:2 D2:1 A3+C#4+F#4:2 " * 3
    m = "-:6 F#5:1 A5:1 G5:1 F#5:1 C#5:1 B4:1 C#5:1 D5:1 A4:1 F#4:6"
    return render([("piano", seq(left, 0.45), 0.8), ("piano", seq(m, 0.7), 1.0)], bpm=150)


# Avvisi brevi -------------------------------------------------------------

def gocce():
    out = np.zeros(int(0.9 * SR))
    for t, f in [(0.0, 700), (0.16, 980)]:
        s = drop(f)
        i = int(t * SR)
        out[i:i + len(s)] += s
    return out


def bolla():
    return np.pad(drop(420, 0.3) * 0.9, (0, int(0.4 * SR)))


def campanella():
    return render([("bell", seq("E6:0.3 B6:1", 0.7), 1.0)], bpm=120)


def avviso():
    return render([("marimba", seq("C6:0.25 G5:0.25 C6:0.25 G6:1", 0.9), 1.0)], bpm=150)


def lieve():
    return render([("glass", seq("A5:0.5 E6:1.5", 0.6), 1.0)], bpm=120)


def arpa_breve():
    return render([("harp", seq("D5:0.25 F#5:0.25 A5:0.25 D6:1", 0.7), 1.0)], bpm=120)


#   id, gruppo, uso, titolo, sottotitolo, secondi, funzione, nell'APK
SOUNDS = [
    ("alba", "eurise", "call", "Alba sul lago", "arpa", 17, alba, True),
    ("vetro", "eurise", "call", "Vetro", "cristallo", 15, vetro, True),
    ("campanelle", "eurise", "call", "Campanelle di montagna", "campane", 15, campanelle, True),
    ("gocce", "eurise", "short", "Gocce", "acqua", 1, gocce, True),
    ("bolla", "eurise", "short", "Bolla", "acqua", 1, bolla, True),
    ("campanella", "eurise", "short", "Campanella", "campana", 2, campanella, True),
    ("avviso", "eurise", "short", "Avviso deciso", "marimba", 2, avviso, True),
    ("lieve", "eurise", "short", "Lieve", "cristallo", 2, lieve, True),
    ("marimba", "eurise", "call", "Marimba gentile", "marimba", 15, marimba_gentile, False),
    ("risveglio", "eurise", "call", "Risveglio", "pianoforte", 16, risveglio, False),
    ("cristalli", "eurise", "call", "Cristalli", "cristallo", 12, cristalli, False),
    ("onde", "eurise", "call", "Onde", "arpa", 16, onde, False),
    ("lanterne", "eurise", "call", "Lanterne", "marimba", 14, lanterne, False),
    ("mattoni", "eurise", "call", "Passo allegro", "pianoforte", 12, mattoni, False),
    ("arpa", "eurise", "short", "Arpa", "arpa", 2, arpa_breve, False),
    ("elisa", "classic", "call", "Per Elisa", "Beethoven · pianoforte", 18, per_elisa, False),
    ("primavera", "classic", "call", "La primavera", "Vivaldi · Allegro", 16, primavera, False),
    ("mattino", "classic", "call", "Il mattino", "Grieg · Peer Gynt", 18, mattino, False),
    ("serenata", "classic", "call", "Piccola serenata notturna", "Mozart · Allegro", 17, serenata, False),
    ("inno", "classic", "call", "Inno alla gioia", "Beethoven · Nona sinfonia", 18, inno, False),
    ("gymnopedie", "classic", "call", "Gymnopédie n. 1", "Satie · pianoforte", 19, gymnopedie, False),
]


def write_ogg(x, path):
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    tmp = path + ".wav"
    with wave.open(tmp, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", tmp, "-c:a", "libvorbis", "-q:a", "0",
                    "-map_metadata", "-1", path], check=True)
    os.remove(tmp)


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "suoni"
    only = set(sys.argv[2:])
    for d in ("apk", "pacchetto"):
        os.makedirs(os.path.join(out, d), exist_ok=True)
    index = {"apk": [], "pacchetto": []}
    for sid, group, use, title, sub, secs, fn, in_apk in SOUNDS:
        where = "apk" if in_apk else "pacchetto"
        index[where].append("\t".join([sid, group, use, title, sub, str(secs)]))
        if only and sid not in only:
            continue
        x = finish(fn(), secs, fade=0.25 if use == "short" else 1.2)
        write_ogg(x, os.path.join(out, where, sid + ".ogg"))
        print(sid, where)
    for where, lines in index.items():
        with open(os.path.join(out, where, "index.txt"), "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()

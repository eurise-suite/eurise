#!/usr/bin/env python3
"""Icone degli APK della suite: serie «colorata» approvata il 16 novembre 2026 (tela «Icone della suite», pagina Icone,
tavola IcoColorate).

Ogni app ha un colore suo, vivo e cangiante (una tinta che scivola verso la vicina), e un disegno pieno e morbido
color crema con la stellina della suite dentro il disegno. I disegni e i colori stanno in `scripts/icone_disegni.json`,
esportato dalla tela: per cambiarne uno si cambia lì e si rilancia. Su una griglia di 48 le parti sono:
  t  campitura tenue (bianco al 20 %)      s  tratto sfumato crema          S  tratto pieno più spesso
  p  pieno sfumato crema                   h  tratto nel colore del fondo  hf pieno nel colore del fondo (incavi)
Dal bestiario del 26 novembre 2026 (tela «Eurise · nuova interfaccia», pagina Icone): meno tratti, sagome piene, due piani.
  q  pieno crema a metà (secondo piano)    v  tratto crema a velo           k  taglio sottile nel colore del fondo
  H  taglio spesso nel colore del fondo    o  filo di contorno nel colore del fondo, a metà

Scrive per ogni icona, nel modulo che la usa: primo piano, fondo (sfumatura del colore con la luce in alto a sinistra),
livello monocromatico (icone a tema di Android 13+, solo la sagoma) e icona adattiva; per i moduli che girano anche
prima di Android 8 (Tastiera, Mappe) anche la versione vettoriale intera, a tinte piene.
Uso: python3 scripts/icone.py (dalla cartella del repository).
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = json.loads((ROOT / "scripts" / "icone_disegni.json").read_text())

# (modulo res, nome dell'icona, app nella tela, versione per Android prima dell'8)
ICONS = [
    ("feature/clock/src/main/res", "ic_clock", "Orologio", False),
    ("app/src/main/res", "ic_file", "Drive", False),
    ("app/src/main/res", "ic_scanner", "Scanner", False),
    ("app/src/main/res", "ic_calendar_app", "Calendario", False),
    ("app/src/main/res", "ic_tasks_app", "Attività", False),
    ("app/src/main/res", "ic_notes_app", "Note", False),
    ("app/src/main/res", "ic_agenda_app", "Agenda", False),
    ("app/src/main/res", "ic_backup_app", "Backup", False),
    ("voice/src/main/res", "ic_voice", "Alfred", False),
    ("browser/src/main/res", "ic_browser", "Browser", False),
    ("mail/src/main/res", "ic_mail", "Email", False),
    ("chat/src/main/res", "ic_chat", "Chat", False),
    ("messages/src/main/res", "ic_messages", "Messaggi", False),
    ("phone/src/main/res", "ic_phone", "Telefono", False),
    ("phone/src/main/res", "ic_contacts", "Contatti", False),
    ("gallery/src/main/res", "ic_gallery", "Galleria", False),
    ("camera/src/main/res", "ic_camera", "Fotocamera", False),
    ("password/src/main/res", "ic_password", "Password", False),
    ("keyboard/app/src/main/res", "ic_launcher", "Tastiera", True),
    ("maps/android/app/src/main/res", "ic_maps", "Mappe", True),
    ("office/app/android/res", "ic_office", "Office", False),
    ("office/apk/src/main/res", "ic_office", "Office", False),
    ("office/motore/src/main/res", "ic_office", "Office", False),
]

CREAM = ("#FFFDF8", "#F4E6CC")       # tratto
CREAM_FILL = ("#FFFDF8", "#EFDDBF")  # pieni
STROKE = 2.4
# La griglia del disegno (48, con un margine: da -7,5 a 55,5) occupa i 72 dp visibili dell'icona adattiva da 108:
# il disegno vero (da 6 a 42) resta dentro la zona sicura di 66 dp.
SCALE = 72 / 63
SHIFT = 54 - 24 * SCALE
HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- Generato da scripts/icone.py (tela «Icone della suite», serie colorata): non modificare a mano. -->\n')
VEC = ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
       '    xmlns:aapt="http://schemas.android.com/aapt"\n'
       '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n')


def argb(c: str, alpha: float = 1.0) -> str:
    return "#%02X%s" % (round(alpha * 255), c.lstrip("#").upper())


def grad(attr: str, colors, indent: str) -> str:
    return (f'{indent}<aapt:attr name="{attr}">\n'
            f'{indent}    <gradient android:type="linear" android:startX="10" android:startY="6" android:endX="38"\n'
            f'{indent}        android:endY="42" android:startColor="{colors[0]}" android:endColor="{colors[1]}" />\n'
            f'{indent}</aapt:attr>\n')


def part(kind: str, d: str, hole: str, mono: bool, flat: bool, indent: str) -> str:
    """Una parte del disegno come tracciato di VectorDrawable."""
    cap = 'android:strokeLineCap="round" android:strokeLineJoin="round"'
    white = "#FFFFFFFF"
    if kind == "t":
        if mono:
            return ""
        return f'{indent}<path android:pathData="{d}" android:fillColor="#33FFFFFF" />\n'
    if kind in ("s", "S"):
        width = STROKE + (1.7 if kind == "S" else 0)
        colors = CREAM if kind == "s" else CREAM_FILL
        if mono or flat:
            col = white if mono else colors[0]
            return f'{indent}<path android:pathData="{d}" android:strokeColor="{col}" android:strokeWidth="{width}" {cap} />\n'
        return (f'{indent}<path android:pathData="{d}" android:strokeWidth="{width}" {cap}>\n'
                + grad("android:strokeColor", colors, indent + "    ") + f'{indent}</path>\n')
    if kind == "p":
        if mono or flat:
            col = white if mono else CREAM_FILL[0]
            return f'{indent}<path android:pathData="{d}" android:fillColor="{col}" />\n'
        return (f'{indent}<path android:pathData="{d}">\n'
                + grad("android:fillColor", CREAM_FILL, indent + "    ") + f'{indent}</path>\n')
    if kind == "q":
        if mono or flat:
            col = "#80FFFFFF" if mono else argb(CREAM_FILL[0], 0.5)
            return f'{indent}<path android:pathData="{d}" android:fillColor="{col}" />\n'
        veil = (argb(CREAM_FILL[0], 0.5), argb(CREAM_FILL[1], 0.5))
        return (f'{indent}<path android:pathData="{d}">\n'
                + grad("android:fillColor", veil, indent + "    ") + f'{indent}</path>\n')
    if kind == "v":
        col = "#9EFFFFFF" if mono else argb(CREAM_FILL[0], 0.62)
        return f'{indent}<path android:pathData="{d}" android:strokeColor="{col}" android:strokeWidth="2.1" {cap} />\n'
    # Incavi: nel livello monocromatico non si possono scavare, quindi restano fuori (resta la sagoma).
    if mono:
        return ""
    if kind == "h":
        return f'{indent}<path android:pathData="{d}" android:strokeColor="{hole}" android:strokeWidth="{STROKE}" {cap} />\n'
    if kind == "hf":
        return f'{indent}<path android:pathData="{d}" android:fillColor="{hole}" />\n'
    if kind == "k":
        return f'{indent}<path android:pathData="{d}" android:strokeColor="{hole}" android:strokeWidth="1.7" {cap} />\n'
    if kind == "H":
        return f'{indent}<path android:pathData="{d}" android:strokeColor="{hole}" android:strokeWidth="{STROKE + 1.3}" {cap} />\n'
    if kind == "o":
        return (f'{indent}<path android:pathData="{d}" android:strokeColor="{argb(hole, 0.55)}" android:strokeWidth="1.3" '
                f'android:strokeLineJoin="round" />\n')
    raise ValueError(kind)


def drawing(app: dict, mono: bool, flat: bool, indent: str) -> str:
    return "".join(part(k, d, app["incavo"], mono, flat, indent) for k, d in app["parti"])


def foreground(app: dict, mono: bool) -> str:
    return (HEAD + VEC +
            f'    <group android:translateX="{SHIFT:.3f}" android:translateY="{SHIFT:.3f}" '
            f'android:scaleX="{SCALE:.4f}" android:scaleY="{SCALE:.4f}">\n'
            + drawing(app, mono, False, "        ") +
            '    </group>\n</vector>\n')


def background_items(app: dict, shape: str, indent: str) -> str:
    """Il fondo: sfumatura del colore dell'app (dall'alto a sinistra in basso a destra) e luce in alto a sinistra."""
    a, b, c = app["fondo"]
    # «verticale»: il colore sale dritto dal basso (Ciarly, 21 novembre); «tappe»: dove cade ogni colore.
    x0, x1 = (54, 54) if app.get("verticale") else (40, 68)
    t0, t1, t2 = app.get("tappe", [0, 0.45, 1])
    return (
        f'{indent}<path android:pathData="{shape}">\n'
        f'{indent}    <aapt:attr name="android:fillColor">\n'
        f'{indent}        <gradient android:type="linear" android:startX="{x0}" android:startY="18" android:endX="{x1}" android:endY="92">\n'
        f'{indent}            <item android:offset="{t0:g}" android:color="{a}" />\n'
        f'{indent}            <item android:offset="{t1:g}" android:color="{b}" />\n'
        f'{indent}            <item android:offset="{t2:g}" android:color="{c}" />\n'
        f'{indent}        </gradient>\n'
        f'{indent}    </aapt:attr>\n'
        f'{indent}</path>\n'
        f'{indent}<path android:pathData="{shape}">\n'
        f'{indent}    <aapt:attr name="android:fillColor">\n'
        f'{indent}        <gradient android:type="radial" android:centerX="39.6" android:centerY="33.8" android:gradientRadius="40">\n'
        f'{indent}            <item android:offset="0" android:color="{argb(app["luce"], 1.0)}" />\n'
        f'{indent}            <item android:offset="1" android:color="{argb(app["luce"], 0.0)}" />\n'
        f'{indent}        </gradient>\n'
        f'{indent}    </aapt:attr>\n'
        f'{indent}</path>\n')


def background(app: dict) -> str:
    return HEAD + VEC + background_items(app, "M0,0 H108 V108 H0 Z", "    ") + '</vector>\n'


def adaptive(name: str) -> str:
    return (HEAD +
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            f'    <background android:drawable="@drawable/{name}_background" />\n'
            f'    <foreground android:drawable="@drawable/{name}_foreground" />\n'
            f'    <monochrome android:drawable="@drawable/{name}_monochrome" />\n'
            '</adaptive-icon>\n')


def legacy(app: dict) -> str:
    """Prima di Android 8: il tondo intero in un vettore, a tinte piene (la Tastiera parte da Android 5)."""
    circle = "M18,54 a36,36 0 1,0 72,0 a36,36 0 1,0 -72,0"
    return (HEAD +
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="48dp" android:height="48dp" android:viewportWidth="72" android:viewportHeight="72">\n'
            '    <group android:translateX="-18" android:translateY="-18">\n'
            f'        <path android:fillColor="{app["c"]}" android:pathData="{circle}" />\n'
            f'        <group android:translateX="{SHIFT:.3f}" android:translateY="{SHIFT:.3f}" '
            f'android:scaleX="{SCALE:.4f}" android:scaleY="{SCALE:.4f}">\n'
            + drawing(app, False, True, "            ") +
            '        </group>\n'
            '    </group>\n'
            '</vector>\n')


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


def main() -> None:
    for res, name, key, old in ICONS:
        app = DATA[key]
        r = ROOT / res
        write(r / "drawable" / f"{name}_foreground.xml", foreground(app, mono=False))
        write(r / "drawable" / f"{name}_monochrome.xml", foreground(app, mono=True))
        write(r / "drawable" / f"{name}_background.xml", background(app))
        write(r / "mipmap-anydpi-v26" / f"{name}.xml", adaptive(name))
        if (r / "mipmap-anydpi-v26" / f"{name}_round.xml").exists():
            write(r / "mipmap-anydpi-v26" / f"{name}_round.xml", adaptive(name))
        if old:
            write(r / "mipmap-anydpi" / f"{name}.xml", legacy(app))
            if (r / "mipmap-anydpi" / f"{name}_round.xml").exists():
                write(r / "mipmap-anydpi" / f"{name}_round.xml", legacy(app))
        print(f"icone: {res}/{name} ({key}, {app['colore']})")


if __name__ == "__main__":
    main()

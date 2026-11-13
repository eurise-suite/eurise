#!/usr/bin/env python3
"""Icone degli APK della suite, stile «Eurise» (tavola IconeStili, approvata il 13 novembre 2026).

Tondo blu notte piatto con sfumatura diagonale dal lato della stellina (#3A4D80 → #222D4D al 55 % → #121828) e alone
caldo attorno alla stella (centro 77,35, raggio 26, #E4C27A al 42 %); simbolo del servizio a scala 2,15 e tratto 2,35,
con il tratto che va dalla tinta chiara alla tinta piena; stellina d'oro #D2B06A in alto a destra.

Scrive per ogni icona, nel modulo che la usa: il primo piano (simbolo e stellina), il fondo, il livello monocromatico
(icone a tema di Android 13+) e l'icona adattiva; per i moduli che girano anche prima di Android 8 (Tastiera, Mappe)
anche la versione vettoriale intera. Uso: python3 scripts/icone.py (dalla cartella del repository). Il disegno dei
simboli è quello della tela: per cambiarne uno si cambia qui e si rilancia.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# Tinte del tratto (chiara → piena), come nella tavola.
GOLD = ("#EBD7A8", "#C9A04B")
ROSE = ("#EABAC0", "#C37F87")
SAGE = ("#BCD4C4", "#81A48C")
SKY = ("#C9D8F6", "#94A9D4")
TEAL = ("#ABD5D8", "#6AA5A9")
CLAY = ("#EAC0A7", "#C38764")
STEEL = ("#B9C9E7", "#7D94BE")
PLUM = ("#D4C1DE", "#A388B2")

# Simboli (24×24): ogni voce è (tracciato, spessore); i punti hanno un tratto più grosso.
SYMBOLS = {
    "clock": [("M5,13 a7,7 0 1 0 14,0 a7,7 0 1 0 -14,0 M12,9.5 v3.8 l2.5,1.6 M4,6.5 l3,-2.5 M20,6.5 l-3,-2.5 "
               "M7.5,19.5 l-1.5,2 M16.5,19.5 l1.5,2", 2.35)],
    "folder": [("M3,7 a2,2 0 0 1 2,-2 h4 l2,2.5 h8 a2,2 0 0 1 2,2 V17 a2,2 0 0 1 -2,2 H5 a2,2 0 0 1 -2,-2 z M3,10.5 h18", 2.35)],
    "scanner": [("M4,8 V5 a1,1 0 0 1 1,-1 h3 M16,4 h3 a1,1 0 0 1 1,1 v3 M20,16 v3 a1,1 0 0 1 -1,1 h-3 M8,20 H5 "
                 "a1,1 0 0 1 -1,-1 v-3 M3,12 h18", 2.35)],
    "chat": [("M3.5,4 h11 a1.5,1.5 0 0 1 1.5,1.5 v6 a1.5,1.5 0 0 1 -1.5,1.5 H8 l-4.5,3.5 z M19,8.5 h0.5 "
              "a1.5,1.5 0 0 1 1.5,1.5 v9.5 l-3.5,-2.5 H11 a1.5,1.5 0 0 1 -1.5,-1.5 v-1", 2.35)],
    "messages": [("M4,5 h16 v11 H9 l-5,4 z", 2.35), ("M8.5,10.5 h0.01 M12,10.5 h0.01 M15.5,10.5 h0.01", 3.35)],
    "phone": [("M6.6,3.5 h2.6 l1.5,4 -2,1.3 a11,11 0 0 0 6.5,6.5 l1.3,-2 4,1.5 v2.6 a2,2 0 0 1 -2.2,2 "
               "A17,17 0 0 1 4.6,5.7 a2,2 0 0 1 2,-2.2 z M14.5,3 a6.5,6.5 0 0 1 6.5,6.5 M14.5,6.5 a3,3 0 0 1 3,3", 2.35)],
    "contacts": [("M6.5,3 H17 a2,2 0 0 1 2,2 V19 a2,2 0 0 1 -2,2 H6.5 Z M4.5,7.5 h3 M4.5,12 h3 M4.5,16.5 h3 "
                  "M10.5,10 a2.3,2.3 0 1 0 4.6,0 a2.3,2.3 0 1 0 -4.6,0 M9.2,17 a3.6,3.6 0 0 1 7.2,0", 2.35)],
    "mail": [("M4,5.5 h16 a1,1 0 0 1 1,1 v11 a1,1 0 0 1 -1,1 H4 a1,1 0 0 1 -1,-1 v-11 a1,1 0 0 1 1,-1 z "
              "M3.5,6.5 l8.5,6.5 l8.5,-6.5", 2.35)],
    "gallery": [("M6.2,4.5 H17.8 A3.2,3.2 0 0 1 21,7.7 V16.3 A3.2,3.2 0 0 1 17.8,19.5 H6.2 A3.2,3.2 0 0 1 3,16.3 "
                 "V7.7 A3.2,3.2 0 0 1 6.2,4.5 Z M14.1,9.2 a1.7,1.7 0 1 0 3.4,0 a1.7,1.7 0 1 0 -3.4,0 "
                 "M3.6,17.2 L8.6,12.3 l4,3.9 l2.6,-2.5 l5.2,4.9", 2.35)],
    "password": [("M3,12 a4.5,4.5 0 1 0 9,0 a4.5,4.5 0 1 0 -9,0 M12,12 H21 M18.3,12 v3.2 M15.4,12 v2.4", 2.35),
                 ("M7.5,12 h0.01", 2.95)],
    "keyboard": [("M5.5,6 H18.5 A3,3 0 0 1 21.5,9 V15.5 A3,3 0 0 1 18.5,18.5 H5.5 A3,3 0 0 1 2.5,15.5 V9 "
                  "A3,3 0 0 1 5.5,6 Z M9,15.3 h6", 2.35),
                 ("M6.6,9.8 h0.01 M10.2,9.8 h0.01 M13.8,9.8 h0.01 M17.4,9.8 h0.01 M6.6,12.8 h0.01 M17.4,12.8 h0.01", 2.95)],
    "maps": [("M3.5,6.5 l5,-2 l7,2.5 l5,-2 v13 l-5,2 l-7,-2.5 l-5,2 z M8.5,4.5 v13.5 M15.5,7 v13.5", 2.35)],
    "browser": [("M3.4,12 a8.6,8.6 0 1 0 17.2,0 a8.6,8.6 0 1 0 -17.2,0 M15.6,8.4 l-2.2,5 l-5,2.2 l2.2,-5 z "
                 "M12,3.4 v1.6 M12,19 v1.6", 2.35)],
    # Il papillon morbido di Alfred (lo stesso di EuriseIcons.bowtie): la voce di Alfred è Eurise Voce.
    "alfred": [("M10,10.4 C8.1,8.6 6.2,7.4 4.8,7.6 C3.5,7.8 3,9.5 3,12 s0.5,4.2 1.8,4.4 c1.4,0.2 3.3,-1 5.2,-2.8 "
                "M14,10.4 c1.9,-1.8 3.8,-3 5.2,-2.8 c1.3,0.2 1.8,1.9 1.8,4.4 s-0.5,4.2 -1.8,4.4 "
                "c-1.4,0.2 -3.3,-1 -5.2,-2.8 M11.7,9.7 h0.6 a1.7,1.7 0 0 1 1.7,1.7 v1.2 a1.7,1.7 0 0 1 -1.7,1.7 h-0.6 "
                "a1.7,1.7 0 0 1 -1.7,-1.7 v-1.2 a1.7,1.7 0 0 1 1.7,-1.7 z", 2.35)],
}

# (modulo res, nome dell'icona, simbolo, tinta, versione per Android prima dell'8)
ICONS = [
    ("feature/clock/src/main/res", "ic_clock", "clock", SKY, False),
    ("app/src/main/res", "ic_file", "folder", TEAL, False),
    ("app/src/main/res", "ic_scanner", "scanner", CLAY, False),
    ("voice/src/main/res", "ic_voice", "alfred", ROSE, False),
    ("browser/src/main/res", "ic_browser", "browser", TEAL, False),
    ("mail/src/main/res", "ic_mail", "mail", STEEL, False),
    ("chat/src/main/res", "ic_chat", "chat", SKY, False),
    ("messages/src/main/res", "ic_messages", "messages", SKY, False),
    ("phone/src/main/res", "ic_phone", "phone", SAGE, False),
    ("phone/src/main/res", "ic_contacts", "contacts", SAGE, False),
    ("gallery/src/main/res", "ic_gallery", "gallery", CLAY, False),
    ("password/src/main/res", "ic_password", "password", PLUM, False),
    ("keyboard/app/src/main/res", "ic_launcher", "keyboard", PLUM, True),
    ("maps/android/app/src/main/res", "ic_maps", "maps", PLUM, True),
]

STAR = "M77,30.32 Q77.47,34.53 81.68,35 Q77.47,35.47 77,39.68 Q76.53,35.47 72.32,35 Q76.53,34.53 77,30.32 Z"
HEAD = '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generato da scripts/icone.py (tavola IconeStili, stile «Eurise»): non modificare a mano. -->\n'


def symbol_paths(symbol: str, tint, mono: bool, indent: str) -> str:
    out = []
    for d, width in SYMBOLS[symbol]:
        if mono:
            out.append(f'{indent}<path android:pathData="{d}" android:strokeColor="#FFFFFFFF" android:strokeWidth="{width}"\n'
                       f'{indent}    android:strokeLineCap="round" android:strokeLineJoin="round" />')
        else:
            out.append(
                f'{indent}<path android:pathData="{d}" android:strokeWidth="{width}"\n'
                f'{indent}    android:strokeLineCap="round" android:strokeLineJoin="round">\n'
                f'{indent}    <aapt:attr name="android:strokeColor">\n'
                f'{indent}        <gradient android:type="linear" android:startX="3" android:startY="3" android:endX="21"\n'
                f'{indent}            android:endY="21" android:startColor="{tint[0]}" android:endColor="{tint[1]}" />\n'
                f'{indent}    </aapt:attr>\n'
                f'{indent}</path>')
    return "\n".join(out)


def foreground(symbol: str, tint, mono: bool) -> str:
    star = "#FFFFFFFF" if mono else "#D2B06A"
    return (HEAD +
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
            '    <group android:translateX="28.2" android:translateY="28.2" android:scaleX="2.15" android:scaleY="2.15">\n'
            + symbol_paths(symbol, tint, mono, "        ") + "\n"
            '    </group>\n'
            f'    <path android:fillColor="{star}" android:pathData="{STAR}" />\n'
            '</vector>\n')


def background_items(shape: str, indent: str) -> str:
    """Il fondo: sfumatura diagonale dal lato della stellina e alone caldo, su [shape]."""
    return (
        f'{indent}<path android:pathData="{shape}">\n'
        f'{indent}    <aapt:attr name="android:fillColor">\n'
        f'{indent}        <gradient android:type="linear" android:startX="90" android:startY="18" android:endX="18" android:endY="90">\n'
        f'{indent}            <item android:offset="0" android:color="#3A4D80" />\n'
        f'{indent}            <item android:offset="0.55" android:color="#222D4D" />\n'
        f'{indent}            <item android:offset="1" android:color="#121828" />\n'
        f'{indent}        </gradient>\n'
        f'{indent}    </aapt:attr>\n'
        f'{indent}</path>\n'
        f'{indent}<path android:pathData="{shape}">\n'
        f'{indent}    <aapt:attr name="android:fillColor">\n'
        f'{indent}        <gradient android:type="radial" android:centerX="77" android:centerY="35" android:gradientRadius="26">\n'
        f'{indent}            <item android:offset="0" android:color="#6BE4C27A" />\n'
        f'{indent}            <item android:offset="0.5" android:color="#24C9A45A" />\n'
        f'{indent}            <item android:offset="1" android:color="#00C9A45A" />\n'
        f'{indent}        </gradient>\n'
        f'{indent}    </aapt:attr>\n'
        f'{indent}</path>\n')


def background() -> str:
    return (HEAD +
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
            + background_items("M0,0 H108 V108 H0 Z", "    ") +
            '</vector>\n')


def adaptive(name: str) -> str:
    return (HEAD +
            '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            f'    <background android:drawable="@drawable/{name}_background" />\n'
            f'    <foreground android:drawable="@drawable/{name}_foreground" />\n'
            f'    <monochrome android:drawable="@drawable/{name}_monochrome" />\n'
            '</adaptive-icon>\n')


def legacy(symbol: str, tint) -> str:
    """Prima di Android 8: il tondo intero in un vettore, a tinte piene (le sfumature nei vettori arrivano con Android 7,
    e la Tastiera parte da Android 5): fondo blu notte, simbolo nella tinta piena, stellina d'oro."""
    circle = "M18,54 a36,36 0 1,0 72,0 a36,36 0 1,0 -72,0"
    paths = "\n".join(
        f'            <path android:pathData="{d}" android:strokeColor="{tint[0]}" android:strokeWidth="{w}"\n'
        f'                android:strokeLineCap="round" android:strokeLineJoin="round" />'
        for d, w in SYMBOLS[symbol])
    return (HEAD +
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="48dp" android:height="48dp" android:viewportWidth="72" android:viewportHeight="72">\n'
            '    <group android:translateX="-18" android:translateY="-18">\n'
            f'        <path android:fillColor="#222D4D" android:pathData="{circle}" />\n'
            '        <group android:translateX="28.2" android:translateY="28.2" android:scaleX="2.15" android:scaleY="2.15">\n'
            + paths + "\n"
            '        </group>\n'
            f'        <path android:fillColor="#D2B06A" android:pathData="{STAR}" />\n'
            '    </group>\n'
            '</vector>\n')


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


def main() -> None:
    for res, name, symbol, tint, old in ICONS:
        r = ROOT / res
        write(r / "drawable" / f"{name}_foreground.xml", foreground(symbol, tint, mono=False))
        write(r / "drawable" / f"{name}_monochrome.xml", foreground(symbol, tint, mono=True))
        write(r / "drawable" / f"{name}_background.xml", background())
        write(r / "mipmap-anydpi-v26" / f"{name}.xml", adaptive(name))
        round_icon = r / "mipmap-anydpi-v26" / f"{name}_round.xml"
        if round_icon.exists():
            write(round_icon, adaptive(name))
        if old:
            write(r / "mipmap-anydpi" / f"{name}.xml", legacy(symbol, tint))
            if (r / "mipmap-anydpi" / f"{name}_round.xml").exists():
                write(r / "mipmap-anydpi" / f"{name}_round.xml", legacy(symbol, tint))
        print(f"icone: {res}/{name} ({symbol})")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Porta i disegni nuovi delle icone (scripts/icone_disegni.json, tela «Icone della suite», pagina Icone) nella Scrivania:
scrive feature/launcher/.../apps/SuiteDrawings.kt con i tracciati e i colori delle tre serie (bolle chiare, bolle scure,
colorate), calcolati come sulla tela. Uso: python3 scripts/icone_kotlin.py (dalla cartella del repository).
"""
import colorsys
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = json.loads((ROOT / "scripts" / "icone_disegni.json").read_text())
OUT = ROOT / "feature/launcher/src/main/java/eu/eurise/feature/launcher/apps/SuiteDrawings.kt"


def hx(c):
    c = c.lstrip("#")
    return [int(c[i:i + 2], 16) for i in (0, 2, 4)]


def mix(c, d, t):
    a, b = hx(c), hx(d)
    return "#%02X%02X%02X" % tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def light(c, t): return mix(c, "#FFFFFF", t)
def dark(c, t): return mix(c, "#000000", t)


def argb(c, alpha=1.0):
    return "0x%02X%s" % (round(alpha * 255), c.lstrip("#").upper())


def palettes(app):
    """Gli stessi colori della tela (gen.py, funzione drawing): tratto, pieni, campitura tenue, incavi."""
    c, c2 = app["c"], app["c2"]
    chiaro = dict(s0=mix(c, "#000000", 0.02), s1=dark(c2, 0.30), p0=light(c, 0.12), p1=dark(c2, 0.22),
                  tint=argb(c, 0.16), hole="#F6F5F1", sw=2.3)
    scuro = dict(s0=light(c, 0.62), s1=light(c2, 0.3), p0=light(c, 0.55), p1=light(c2, 0.2),
                 tint=argb(light(c, 0.4), 0.17), hole="#262A33", sw=2.3)
    colori = dict(s0="#FFFDF8", s1="#F4E6CC", p0="#FFFDF8", p1="#EFDDBF",
                  tint=argb("#FFFFFF", 0.20), hole=app["incavo"], sw=2.4)
    return chiaro, scuro, colori


def kpal(p):
    return (f'Palette(Color({argb(p["s0"])}), Color({argb(p["s1"])}), Color({argb(p["p0"])}), Color({argb(p["p1"])}), '
            f'Color({p["tint"]}), Color({argb(p["hole"])}), {p["sw"]}f)')


def main():
    entries = []
    for key, app in sorted(DATA.items()):
        chiaro, scuro, colori = palettes(app)
        a, b, cc = app["fondo"]
        parts = ",\n".join(f'            Part("{k}", "{d}")' for k, d in app["parti"])
        entries.append(
            f'        "{key}" to Drawing(\n'
            f'            listOf(\n{parts},\n            ),\n'
            f'            light = {kpal(chiaro)},\n'
            f'            dark = {kpal(scuro)},\n'
            f'            color = {kpal(colori)},\n'
            f'            round = listOf(Color({argb(a)}), Color({argb(b)}), Color({argb(cc)})),\n'
            f'            glow = Color({argb(app["luce"])}),\n'
            f'        )')
    body = ",\n".join(entries)
    OUT.write_text(f'''package eu.eurise.feature.launcher.apps

import androidx.compose.ui.graphics.Color

// Generato da scripts/icone_kotlin.py da scripts/icone_disegni.json (tela «Icone della suite», pagina Icone, approvata il
// 16 novembre 2026): non modificare a mano.

/**
 * I disegni nuovi delle app della suite, su una griglia di 48 (la tela li mostra in un riquadro da -7,5 a 55,5).
 * Parti: «t» campitura tenue, «s» tratto sfumato, «S» tratto pieno più spesso, «p» pieno sfumato, «h» tratto e «hf»
 * pieno nel colore del fondo (incavi). Ogni disegno ha i colori delle tre serie: bolle chiare, bolle scure, colorate.
 */
internal object SuiteDrawings {{
    class Part(val kind: String, val path: String)

    /** Tratto da [s0] a [s1], pieni da [p0] a [p1], campitura [tint], incavi [hole], spessore del tratto [stroke]. */
    class Palette(val s0: Color, val s1: Color, val p0: Color, val p1: Color, val tint: Color, val hole: Color, val stroke: Float)

    /** [round] è il fondo della serie colorata (tre tappe), [glow] la luce in alto a sinistra. */
    class Drawing(val parts: List<Part>, val light: Palette, val dark: Palette, val color: Palette, val round: List<Color>, val glow: Color)

    val all: Map<String, Drawing> = mapOf(
{body},
    )
}}
''')
    print(f"icone_kotlin: {len(entries)} disegni in {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()

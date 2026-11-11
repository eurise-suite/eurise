#!/usr/bin/env python3
"""Stile della suite per Eurise Mappe: i colori di CoMaps diventano Perla (chiaro) e Notte (scuro).

Chiamato da eurise_patch.py. Riscrive i valori dei colori che CoMaps già usa (tema Material 3 e colori di base
delle schermate a View), così non si tocca il codice Java e un aggiornamento di CoMaps non rompe nulla: un colore
che non c'è più viene saltato e segnalato, uno nuovo resta quello di CoMaps finché non lo aggiungiamo qui.
Tavolozza: core/designsystem/.../theme/Color.kt.
"""
import re
import sys
from pathlib import Path

# Perla: fondo perla, carte bianche, accento blu notte, secondario salvia, terziario ottone.
LIGHT_M3 = {
    "primary": "#1F2A44", "onPrimary": "#FFFFFF", "primaryContainer": "#DDE3F0", "onPrimaryContainer": "#1F2A44",
    "secondary": "#4F6B5A", "onSecondary": "#FFFFFF", "secondaryContainer": "#DCE7DF", "onSecondaryContainer": "#24382C",
    "tertiary": "#85672B", "onTertiary": "#FFFFFF", "tertiaryContainer": "#F2E6CC", "onTertiaryContainer": "#4A3912",
    "error": "#8C2F39", "onError": "#FFFFFF", "errorContainer": "#F6DADC", "onErrorContainer": "#5A1A22",
    "background": "#F4F4F1", "onBackground": "#1E1D1B", "surface": "#F4F4F1", "onSurface": "#1E1D1B",
    "surfaceVariant": "#E9E9E4", "onSurfaceVariant": "#56564F", "outline": "#8A8A82", "outlineVariant": "#DADAD3",
    "inverseSurface": "#2A2A27", "inverseOnSurface": "#F4F4F1", "inversePrimary": "#A9C1F2",
    "primaryFixed": "#DDE3F0", "onPrimaryFixed": "#14203A", "primaryFixedDim": "#A9C1F2", "onPrimaryFixedVariant": "#1F2A44",
    "secondaryFixed": "#DCE7DF", "secondaryFixedDim": "#93BBA0",
    "surfaceDim": "#E4E4DE", "surfaceBright": "#FFFFFF", "surfaceContainerLowest": "#FFFFFF",
    "surfaceContainerLow": "#FAFAF8", "surfaceContainer": "#FFFFFF", "surfaceContainerHigh": "#EEEEEA",
    "surfaceContainerHighest": "#E9E9E4",
}
# Notte: blu-grafite, accento azzurro.
DARK_M3 = {
    "primary": "#A9C1F2", "onPrimary": "#14203A", "primaryContainer": "#2C3A57", "onPrimaryContainer": "#DDE3F0",
    "secondary": "#93BBA0", "onSecondary": "#16261C", "secondaryContainer": "#2E4436", "onSecondaryContainer": "#DCE7DF",
    "tertiary": "#D2B06A", "onTertiary": "#3A2C0C", "tertiaryContainer": "#4F3F1C", "onTertiaryContainer": "#F2E6CC",
    "error": "#E8939B", "onError": "#4A1219", "errorContainer": "#5E2128", "onErrorContainer": "#F6DADC",
    "background": "#14171C", "onBackground": "#ECE9E3", "surface": "#14171C", "onSurface": "#ECE9E3",
    "surfaceVariant": "#262B33", "onSurfaceVariant": "#A4A9B2", "outline": "#5E6672", "outlineVariant": "#323843",
    "inverseSurface": "#ECE9E3", "inverseOnSurface": "#1C2027", "inversePrimary": "#1F2A44",
    "primaryFixed": "#DDE3F0", "onPrimaryFixed": "#14203A", "primaryFixedDim": "#A9C1F2", "onPrimaryFixedVariant": "#2C3A57",
    "secondaryFixed": "#DCE7DF", "secondaryFixedDim": "#93BBA0",
    "surfaceDim": "#14171C", "surfaceBright": "#323843", "surfaceContainerLowest": "#101216",
    "surfaceContainerLow": "#181B21", "surfaceContainer": "#1C2027", "surfaceContainerHigh": "#262B33",
    "surfaceContainerHighest": "#2C323B",
}
# Colori di base delle schermate a View di CoMaps (barre, pulsanti, pannelli).
LIGHT_BASE = {
    "base_accent": "#FF1F2A44", "base_accent_pressed": "#FF3E5C8A", "base_accent_transparent": "#1E1F2A44",
    "bg_window": "#FFF4F4F1", "bg_primary": "#1F2A44", "bg_primary_dark": "#14203A", "bg_statusbar": "#14203A",
    "base_green": "#FF4F6B5A", "light_green": "#93BBA0", "base_red": "#8C2F39", "base_yellow": "#85672B",
    "divider": "#14000000", "icon_tint": "#FF56564F",
}
DARK_BASE = {
    "base_accent": "#FFA9C1F2", "base_accent_pressed": "#FF8FA9D9",
    "bg_window": "#FF14171C", "bg_cards": "#FF1C2027", "bg_primary": "#1C2027", "bg_primary_dark": "#2C3A57",
    "bg_statusbar": "#14171C",
}


def rewrite(path: Path, values: dict, prefix: str = "") -> int:
    if not path.exists():
        print(f"eurise_style: manca {path}, salto")
        return 0
    text = path.read_text()
    done = 0
    for name, value in values.items():
        pattern = re.compile(r'(<color name="' + re.escape(prefix + name) + r'">)[^<]*(</color>)')
        text, n = pattern.subn(lambda m: m.group(1) + value + m.group(2), text)
        if n == 0:
            print(f"eurise_style: {prefix}{name} non c'è in {path.name} ({path.parent.name}), salto")
        done += n
    path.write_text(text)
    return done


def restyle(root: Path) -> None:
    res = root / "android" / "app" / "src" / "main" / "res"
    n = 0
    n += rewrite(res / "values" / "m3_colors.xml", LIGHT_M3, "md_theme_")
    n += rewrite(res / "values-night" / "m3_colors.xml", DARK_M3, "md_theme_")
    n += rewrite(res / "values" / "colors.xml", LIGHT_BASE)
    n += rewrite(res / "values-night" / "colors.xml", DARK_BASE)
    if n < 60:
        sys.exit(f"eurise_style: cambiati solo {n} colori, CoMaps è cambiato troppo: controllare maps/eurise_style.py")
    print(f"eurise_style: Perla e Notte, {n} colori")


if __name__ == "__main__":
    restyle(Path(sys.argv[1]))

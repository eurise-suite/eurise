#!/usr/bin/env python3
"""Adatta i sorgenti di CoMaps a Eurise Mappe (vedi maps/EURISE.md).

Uso: python3 maps/eurise_patch.py <cartella di comaps> <keystore di debug di Eurise>

Cambia solo ciò che serve al prototipo: nome «Eurise Mappe», pacchetto eu.eurise.maps (senza suffissi
.fdroid/.debug, così il permesso di firma della suite vale anche qui), firma con la chiave di debug della
suite. Il codice Java (pacchetto app.organicmaps) resta com'è. Ogni sostituzione deve riuscire: se CoMaps
cambia quelle righe, lo script si ferma e lo dice.
"""
import shutil
import sys
from pathlib import Path

root = Path(sys.argv[1])
keystore = Path(sys.argv[2])
gradle = root / "android" / "app" / "build.gradle.kts"
text = gradle.read_text()


def swap(old: str, new: str, count: int = 1) -> None:
    global text
    found = text.count(old)
    if found < count:
        sys.exit(f"eurise_patch: non trovo {old!r} in {gradle} ({found} volte, ne servono {count})")
    text = text.replace(old, new)


swap('val appName = "CoMaps"', 'val appName = "Eurise Mappe"')
swap('val appId = "app.comaps"', 'val appId = "eu.eurise.maps"')
swap('applicationIdSuffix = ".fdroid"', '// Eurise: nessun suffisso, il pacchetto resta eu.eurise.maps')
swap('applicationIdSuffix = ".debug" // Allows installing debug and release builds together.',
     '// Eurise: nessun suffisso, così la prova si aggiorna sopra la precedente')
swap('resValue("string", "app_name", "$appName Debug")', 'resValue("string", "app_name", appName)')
# Firma: la chiave di debug della suite (pubblica e senza valore), la stessa degli altri APK di prova.
swap('storeFile = project.projectDir.resolve(File(comapsDebugKeystorePath))',
     'storeFile = project.projectDir.resolve(File("eurise-debug.keystore"))')
swap('storePassword = "12345678"\n            keyAlias = "$appName Debug"\n            keyPassword = "12345678"',
     'storePassword = "android"\n            keyAlias = "androiddebugkey"\n            keyPassword = "android"')
gradle.write_text(text)
shutil.copy(keystore, gradle.parent / "eurise-debug.keystore")

strings = root / "android" / "app" / "src" / "main" / "res" / "values" / "strings.xml"
s = strings.read_text()
old = '<string name="project_name" translatable="false">CoMaps</string>'
if old not in s:
    sys.exit(f"eurise_patch: non trovo project_name in {strings}")
strings.write_text(s.replace(old, '<string name="project_name" translatable="false">Eurise Mappe</string>'))
print("eurise_patch: Eurise Mappe, eu.eurise.maps, chiave di debug della suite")

# Densità dei simboli: eurise_slim.py tiene xhdpi, 6plus e xxhdpi; la tabella di VisualParams deve sceglierne una di quelle.
vp = root / "libs" / "drape_frontend" / "visual_params.cpp"
v = vp.read_text()
old_vp = """      {"mdpi", kMdpiScale},

      {"hdpi", kHdpiScale},     {"xhdpi", kXhdpiScale},     {"6plus", k6plusScale},
      {"xxhdpi", kXxhdpiScale}, {"xxxhdpi", kXxxhdpiScale},"""
if old_vp not in v:
    sys.exit(f"eurise_patch: non trovo la tabella delle densità in {vp}")
vp.write_text(v.replace(old_vp, """      // Eurise: solo le densità che restano nell'APK (maps/eurise_slim.py).
      {"xhdpi", kXhdpiScale}, {"6plus", k6plusScale}, {"xxhdpi", kXxhdpiScale},"""))
print("eurise_patch: simboli xhdpi, 6plus, xxhdpi")

# File nostri (maps/android/…) copiati sopra l'albero di CoMaps: «Segnala qui» (tavola MapSegnala) e i suoi testi.
ours = Path(__file__).resolve().parent / "android"
for f in ours.rglob("*"):
    if f.is_file():
        dest = root / "android" / f.relative_to(ours)
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(f, dest)


def patch(path: Path, old: str, new: str) -> None:
    t = path.read_text()
    if old not in t:
        sys.exit(f"eurise_patch: non trovo {old[:70]!r} in {path}")
    path.write_text(t.replace(old, new, 1))


pp = root / "android" / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "widget" / "placepage"
# Il pulsante «Segnala» nella scheda del luogo (oltre il quarto va nel menu «Altro», come gli altri).
patch(pp / "PlacePageButtons.java", "    ROUTE_AVOID_UNPAVED,\n    MORE", "    ROUTE_AVOID_UNPAVED,\n    EURISE_REPORT,\n    MORE")
patch(pp / "PlacePageButtonFactory.java", "      case MORE ->", """      case EURISE_REPORT ->
      {
        titleId = R.string.eurise_report;
        yield R.drawable.eurise_ic_report;
      }
      case MORE ->""")
patch(pp / "PlacePageController.java", "    case ROUTE_AVOID_FERRY -> onAvoidFerryBtnClicked();",
      """    case ROUTE_AVOID_FERRY -> onAvoidFerryBtnClicked();
    case EURISE_REPORT ->
    {
      if (mMapObject != null)
        app.organicmaps.eurise.EuriseReportActivity.open(requireActivity(), mMapObject.getLat(), mMapObject.getLon(),
                                                         mMapObject.getTitle());
    }""")
patch(pp / "PlacePageController.java", """          buttons.add(mapObject.isBookmark() ? PlacePageButtons.ButtonType.BOOKMARK_DELETE
                                             : PlacePageButtons.ButtonType.BOOKMARK_SAVE);
      }
    }
    mViewModel.setCurrentButtons(buttons);""", """          buttons.add(mapObject.isBookmark() ? PlacePageButtons.ButtonType.BOOKMARK_DELETE
                                             : PlacePageButtons.ButtonType.BOOKMARK_SAVE);
      }
      buttons.add(PlacePageButtons.ButtonType.EURISE_REPORT);
    }
    mViewModel.setCurrentButtons(buttons);""")
patch(root / "android" / "app" / "src" / "main" / "AndroidManifest.xml", """    <activity
        android:name="app.organicmaps.MwmActivity\"""", """    <activity
        android:name="app.organicmaps.eurise.EuriseReportActivity"
        android:theme="@android:style/Theme.Translucent.NoTitleBar"
        android:exported="false"
        android:windowSoftInputMode="adjustResize" />

    <activity
        android:name="app.organicmaps.MwmActivity\"""")
print("eurise_patch: «Segnala qui» nella scheda del luogo")

# Stile della suite (Perla e Notte) nei colori di CoMaps: maps/eurise_style.py.
sys.path.insert(0, str(Path(__file__).resolve().parent))
import eurise_style  # noqa: E402

eurise_style.restyle(root)

# Icona della suite (docs/brand/android/maps, famiglia «Firma di stella»): i file sono in maps/android, qui il manifest.
patch(root / "android" / "app" / "src" / "main" / "AndroidManifest.xml",
      '      android:icon="@mipmap/ic_launcher"\n',
      '      android:icon="@mipmap/ic_maps"\n      android:roundIcon="@mipmap/ic_maps_round"\n')
print("eurise_patch: icona di Eurise Mappe")

# Guida (tavola MapGuida): cartello colorato per tipo di strada, metri alla svolta a passi fini, «Manda l'arrivo».
# La logica sta in maps/android/.../eurise/EuriseGuide.java; qui solo gli agganci.
nav = root / "android" / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "routing" / "NavigationController.java"
t = nav.read_text()
old_dist = "    mNextTurnDistance.setText(Utils.formatDistance(mFrame.getContext(), info.distToTurn));"
if t.count(old_dist) != 2:
    sys.exit(f"eurise_patch: in {nav} la distanza alla svolta non compare due volte")
t = t.replace(old_dist, "    mNextTurnDistance.setText(app.organicmaps.eurise.EuriseGuide.turnDistance(mFrame.getContext(), info.distToTurn));")
nav.write_text(t)
patch(nav, "    updateStreetView(info);\n    mNavMenu.update(info);",
      """    updateStreetView(info);
    app.organicmaps.eurise.EuriseGuide.tint(mFrame.findViewById(R.id.nav_next_turn_frame), mStreetFrame, info,
                                            Router.get() == Router.Pedestrian);
    mNavMenu.update(info);""")

menu = root / "android" / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "widget" / "menu" / "NavMenu.java"
patch(menu, "    mTrackRecording.setOnClickListener(v -> onTrackRecordingClicked());",
      """    mTrackRecording.setOnClickListener(v -> onTrackRecordingClicked());
    bottomFrame.findViewById(R.id.eurise_share_arrival)
        .setOnClickListener(v -> app.organicmaps.eurise.EuriseGuide.shareArrival(mActivity, mRoutingInfo));""")
patch(root / "android" / "app" / "src" / "main" / "res" / "layout" / "layout_nav_bottom.xml",
      """    <com.google.android.material.button.MaterialButton
      android:id="@+id/stop\"""",
      """    <com.google.android.material.imageview.ShapeableImageView
      android:id="@+id/eurise_share_arrival"
      android:layout_width="0dp"
      android:layout_height="@dimen/nav_icon_size"
      android:layout_weight="0.2"
      android:padding="12dp"
      android:background="?selectableItemBackgroundBorderless"
      android:contentDescription="@string/eurise_arrival_share"
      android:scaleType="fitCenter"
      app:srcCompat="@drawable/ic_share"
      app:tint="?iconTint" />

    <com.google.android.material.button.MaterialButton
      android:id="@+id/stop\"""")
print("eurise_patch: guida con cartello colorato, metri fini e «Manda l'arrivo»")

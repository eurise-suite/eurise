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
    app.organicmaps.eurise.EuriseRouteWeather.update(mFrame.findViewById(R.id.eurise_route_weather), info,
                                                     Router.get() == Router.Pedestrian);
    app.organicmaps.eurise.EuriseArrival.update(mFrame.findViewById(R.id.eurise_arrival), info);
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

# Meteo sul percorso (tavola MapGuida): una riga sotto la via, logica in EuriseRouteWeather.java e RouteSampler.java.
# Chiede all'app Eurise (permesso della suite di prova: Mappe è firmata con la chiave di debug della suite).
patch(root / "android" / "app" / "src" / "main" / "res" / "layout" / "layout_nav_top.xml",
      """  <RelativeLayout
    android:id="@+id/nav_next_turn_container\"""",
      """  <com.google.android.material.textview.MaterialTextView
    android:id="@+id/eurise_route_weather"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginTop="@dimen/margin_half"
    android:layout_marginStart="@dimen/nav_street_left"
    android:layout_marginEnd="@dimen/margin_half"
    android:maxLines="2"
    android:ellipsize="end"
    android:visibility="gone"
    android:clickable="true"
    android:focusable="true"
    app:layout_constrainedWidth="true"
    app:layout_constraintHorizontal_bias="0"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintTop_toBottomOf="@+id/street_frame" />

  <com.google.android.material.textview.MaterialTextView
    android:id="@+id/eurise_arrival"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_marginTop="@dimen/margin_half"
    android:layout_marginStart="@dimen/nav_street_left"
    android:layout_marginEnd="@dimen/margin_half"
    android:maxLines="3"
    android:ellipsize="end"
    android:visibility="gone"
    android:clickable="true"
    android:focusable="true"
    app:layout_constrainedWidth="true"
    app:layout_constraintHorizontal_bias="0"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintTop_toBottomOf="@+id/eurise_route_weather" />

  <RelativeLayout
    android:id="@+id/nav_next_turn_container\"""")
patch(root / "android" / "app" / "src" / "main" / "AndroidManifest.xml",
      """  <uses-permission android:name="android.permission.INTERNET"/>""",
      """  <uses-permission android:name="android.permission.INTERNET"/>
  <!-- Eurise: meteo sul percorso dall'app Eurise di prova (stessa chiave di debug della suite). -->
  <permission android:name="eu.eurise.permission.SUITE_DEBUG" android:protectionLevel="signature"/>
  <uses-permission android:name="eu.eurise.permission.SUITE_DEBUG"/>
  <queries>
    <package android:name="eu.eurise.hub"/>
    <package android:name="eu.eurise.hub.debug"/>
  </queries>""")
print("eurise_patch: meteo sul percorso")

# «Dove devi andare» (tavola MapCerca): prima scheda della ricerca, EuriseGoFragment (in maps/android/…/search).
patch(root / "android" / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "search" / "TabAdapter.java",
      """  enum Tab
  {
    HISTORY {""",
      """  enum Tab
  {
    EURISE_GO {
      @Override
      public int getTitleRes()
      {
        return R.string.eurise_go_tab;
      }

      @Override
      public Class<? extends Fragment> getFragmentClass()
      {
        return EuriseGoFragment.class;
      }
    },

    HISTORY {""")
print("eurise_patch: «Dove devi andare» nella ricerca e frase d'arrivo")

# Ricerca (piano del 12 novembre, «Apice, BN»): domanda senza la sigla della provincia per il motore sul telefono e,
# se le mappe scaricate non trovano nulla, la scheda della ricerca online facoltativa (maps/android/…/EuriseOnlineSearch).
search = root / "android" / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "search" / "SearchFragment.java"
patch(search,
      """      SearchEngine.INSTANCE.searchInteractive(requireContext(), getQuery(), isCategory(), mLastQueryTimestamp,
                                              true /* isMapAndTable */);""",
      """      SearchEngine.INSTANCE.searchInteractive(requireContext(),
                                              app.organicmaps.eurise.EuriseOnlineSearch.local(getQuery()), isCategory(),
                                              mLastQueryTimestamp, true /* isMapAndTable */);""")
patch(search,
      """      if (!SearchEngine.INSTANCE.search(requireContext(), getQuery(), isCategory(), mLastQueryTimestamp,""",
      """      if (!SearchEngine.INSTANCE.search(requireContext(), app.organicmaps.eurise.EuriseOnlineSearch.local(getQuery()),
                                        isCategory(), mLastQueryTimestamp,""")
patch(search,
      """    UiUtils.showIf(show, mResultsPlaceholder);
  }""",
      """    // Eurise: niente nelle mappe scaricate → la scheda della ricerca online (facoltativa) al posto del segnaposto.
    UiUtils.showIf(show, mResultsPlaceholder);
    app.organicmaps.eurise.EuriseOnlineSearch.update(mResultsFrame, show && !isCategory(), getQuery(), this);
  }""")
print("eurise_patch: ricerca senza sigla della provincia e ricerca online facoltativa")

# Passo a) dell'interfaccia rifatta sulle tavole: la schermata della mappa con la barra satinata in basso (Cerca,
# Livelli, Posizione, Salvati, Menu) e il «+» per segnalare, in Compose (maps/android/…/eurise/ui/EuriseMapBar.kt).
# Compose entra solo nel modulo dell'app (plugin del compilatore Kotlin della stessa versione di CoMaps, BOM con Compose
# 1.7 (2024.12.01, la stessa della suite), che regge ancora il minSdk 21 di CoMaps); solo ui e foundation, niente Material 3. Il motore non cambia.
android_root = root / "android"
kotlin_version = None
for line in (android_root / "gradle" / "libs.versions.toml").read_text().splitlines():
    if line.strip().startswith("kotlin ="):
        kotlin_version = line.split("=", 1)[1].strip().strip('"')
        break
if not kotlin_version:
    sys.exit("eurise_patch: non trovo la versione di Kotlin in libs.versions.toml")
patch(android_root / "build.gradle.kts", "    alias(libs.plugins.kotlin.android) apply false\n",
      f'    alias(libs.plugins.kotlin.android) apply false\n    id("org.jetbrains.kotlin.plugin.compose") version "{kotlin_version}" apply false\n')
app_gradle = android_root / "app" / "build.gradle.kts"
patch(app_gradle, "    alias(libs.plugins.kotlin.android)\n", '    alias(libs.plugins.kotlin.android)\n    id("org.jetbrains.kotlin.plugin.compose")\n')
patch(app_gradle, "        dataBinding = true\n", "        dataBinding = true\n        compose = true\n")
patch(app_gradle, "    implementation(platform(libs.jetbrains.kotlin.bom))\n",
      """    implementation(platform(libs.jetbrains.kotlin.bom))
    // Eurise: schermate nuove in Compose (barra della mappa); solo ui e foundation.
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
""")
res = android_root / "app" / "src" / "main" / "res"
# I pulsanti di CoMaps in basso restano nel layout (MapButtonsController li cerca per id) ma nascosti; sotto, la barra.
bottom = res / "layout" / "map_buttons_bottom.xml"
patch(bottom, """  <androidx.constraintlayout.widget.ConstraintLayout
    style="@style/MwmWidget.M3.FAB.MapButton.Bottom\"""", """  <androidx.compose.ui.platform.ComposeView
    android:id="@+id/eurise_map_bar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_alignParentBottom="true" />
  <androidx.constraintlayout.widget.ConstraintLayout
    android:visibility="gone"
    style="@style/MwmWidget.M3.FAB.MapButton.Bottom\"""")
mbc = android_root / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "maplayer" / "MapButtonsController.java"
patch(mbc, "    initBottomButtons();\n", """    initBottomButtons();
    // Eurise: la barra satinata al posto dei pulsanti in basso (solo nella schermata normale della mappa).
    final View euriseBar = mFrame.findViewById(R.id.eurise_map_bar);
    app.organicmaps.eurise.ui.EuriseMapBar.attach(euriseBar, mMapButtonClickListener);
""")
# Livelli e Posizione stanno nella barra: i pulsanti di CoMaps a destra e in alto non si mostrano più quando c'è.
patch(mbc, "      mToggleMapLayerButton.setVisibility(View.VISIBLE);",
      "      mToggleMapLayerButton.setVisibility(euriseBar == null ? View.VISIBLE : View.GONE);")
patch(mbc, "UiUtils.showIf(show && !isInNavigationMode(), mToggleMapLayerButton);",
      "UiUtils.showIf(show && !isInNavigationMode() && mFrame.findViewById(R.id.eurise_map_bar) == null, mToggleMapLayerButton);")
patch(mbc, "        mNavMyPosition.showButton(show);",
      "        mNavMyPosition.showButton(show && mFrame.findViewById(R.id.eurise_map_bar) == null);")
patch(mbc, "    showButton(false, MapButtons.trackRecordingStatus);\n    return mFrame;",
      "    showButton(false, MapButtons.trackRecordingStatus);\n    if (euriseBar != null && mNavMyPosition != null)\n      mNavMyPosition.showButton(false);\n    return mFrame;")
# CoMaps compila con Kotlin 2.2 ma la sua BOM porta la libreria standard 2.4: finché il codice Kotlin era poco, non se ne
# accorgeva nessuno. Il compilatore legge comunque quelle classi; si salta solo il controllo della versione dei metadati.
with open(app_gradle, "a") as g:
    g.write("""
// Eurise: Kotlin 2.2 del compilatore con la libreria standard 2.4 della BOM di CoMaps.
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}
""")
print("eurise_patch: mappa con la barra satinata (Compose) e il «+» per segnalare")


# Passo b) dell'interfaccia rifatta sulle tavole (MapCerca): prima di scrivere, la ricerca mostra la pagina della suite in
# Compose (maps/android/…/eurise/ui/EuriseSearchHome.kt: Dove devi andare, Cerca vicino, recenti, contatto, mappe sul
# telefono) al posto delle schede di CoMaps, che restano nel layout ma nascoste. Il motore di ricerca non cambia.
search_layout = res / "layout" / "fragment_search.xml"
patch(search_layout, """        android:orientation="vertical">
        <com.google.android.material.tabs.TabLayout
          android:id="@+id/tabs"""", """        android:orientation="vertical">
        <androidx.compose.ui.platform.ComposeView
          android:id="@+id/eurise_search_home"
          android:layout_width="match_parent"
          android:layout_height="match_parent" />
        <com.google.android.material.tabs.TabLayout
          android:id="@+id/tabs"""")
patch(search, """    ViewCompat.setOnApplyWindowInsetsListener(mResultsPlaceholder, new WindowInsetUtils.ScrollableContentInsetsListener(mResultsPlaceholder, mOpenContactPicker));""",
      """    // Eurise (passo b, MapCerca): la pagina della suite prima di scrivere, al posto delle schede.
    final View euriseHome = root.findViewById(R.id.eurise_search_home);
    if (euriseHome != null)
    {
      tabLayout.setVisibility(View.GONE);
      root.findViewById(R.id.tabs_divider).setVisibility(View.GONE);
      pager.setVisibility(View.GONE);
      final android.widget.TextView euriseQuery = root.findViewById(R.id.query);
      if (euriseQuery != null)
        euriseQuery.setHint(R.string.eurise_sh_hint);
      app.organicmaps.eurise.ui.EuriseSearchHome.attach(euriseHome, this::setQuery,
          compatibleContactsAppInstalled ? () -> mContactPickerLauncher.launch(Utils.openContactPicker()) : null);
    }
    ViewCompat.setOnApplyWindowInsetsListener(mResultsPlaceholder, new WindowInsetUtils.ScrollableContentInsetsListener(mResultsPlaceholder, mOpenContactPicker));""")
# Il contatto sta nella pagina della suite: niente pulsante tondo di CoMaps sopra.
patch(search, "    UiUtils.showIf(!hasQuery && compatibleContactsAppInstalled, mOpenContactPicker);",
      "    UiUtils.showIf(!hasQuery && compatibleContactsAppInstalled && (getView() == null || getView().findViewById(R.id.eurise_search_home) == null), mOpenContactPicker);")
print("eurise_patch: ricerca con la pagina della suite (passo b)")

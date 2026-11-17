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

# Avvio: icona di Eurise Mappe sul blu notte della suite al posto del logo di CoMaps (layout nostro activity_splash.xml;
# Android 12+ in values-v31/eurise_splash.xml). Blu notte anche col tema scuro, come l'app principale.
patch(root / "android" / "app" / "src" / "main" / "res" / "values" / "themes.xml", """  <style name="MwmTheme.Splash">
    <item name="android:windowBackground">@color/bg_primary</item>
    <item name="android:textColorPrimary">@color/white_primary</item>
    <item name="android:navigationBarColor">@color/bg_primary</item>
    <item name="android:statusBarColor">@color/bg_primary</item>""", """  <style name="MwmTheme.Splash">
    <item name="android:windowBackground">@color/eurise_icon_background</item>
    <item name="android:textColorPrimary">@color/white_primary</item>
    <item name="android:navigationBarColor">@color/eurise_icon_background</item>
    <item name="android:statusBarColor">@color/eurise_icon_background</item>""")
print("eurise_patch: avvio con l'icona di Eurise Mappe")

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
# Lo zoom sta nella colonna tonda della barra (sopra la lente): i pulsanti di CoMaps a destra restano solo in guida.
patch(mbc, "    case zoom: UiUtils.showIf(show && Config.showZoomButtons(), buttonView); break;",
      "    case zoom: UiUtils.showIf(show && Config.showZoomButtons() && (isInNavigationMode() || mFrame.findViewById(R.id.eurise_map_bar) == null), buttonView); break;")
print("eurise_patch: mappa con la barra satinata (Compose) e Segnala nella barra, lente e zoom tondi")


# Passo b) dell'interfaccia rifatta sulle tavole (MapCerca): prima di scrivere, la ricerca mostra la pagina della suite in
# Compose (maps/android/…/eurise/ui/EuriseSearchHome.kt: Dove devi andare, Cerca vicino, recenti, contatto, mappe sul
# telefono) al posto delle schede di CoMaps, che restano nel layout ma nascoste. Il motore di ricerca non cambia.
search_layout = res / "layout" / "fragment_search.xml"
patch(search_layout, """        android:orientation="vertical">
        <com.google.android.material.tabs.TabLayout
          android:id="@+id/tabs\"""", """        android:orientation="vertical">
        <androidx.compose.ui.platform.ComposeView
          android:id="@+id/eurise_search_home"
          android:layout_width="match_parent"
          android:layout_height="match_parent" />
        <com.google.android.material.tabs.TabLayout
          android:id="@+id/tabs\"""")
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

# Passo c) dell'interfaccia rifatta sulle tavole (MapLuogo e MapLuogoAperta): nella scheda del luogo, sotto nome e indirizzo,
# le azioni della suite in Compose (maps/android/…/eurise/ui/EuriseLuogo.kt): Indicazioni e Parti ora, Salva, Chiama,
# Impegno, Condividi, e l'impegno della suite con questo luogo. I dettagli di CoMaps restano sotto.
pp_preview = res / "layout" / "place_page_preview.xml"
patch(pp_preview, """  </LinearLayout>

  <androidx.fragment.app.FragmentContainerView
    android:id="@+id/place_page_track_fragment\"""", """  </LinearLayout>

  <androidx.compose.ui.platform.ComposeView
    android:id="@+id/eurise_luogo"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />

  <androidx.fragment.app.FragmentContainerView
    android:id="@+id/place_page_track_fragment\"""")
patch(pp / "PlacePageView.java", "    mPreview = mFrame.findViewById(R.id.pp__preview);",
      """    mPreview = mFrame.findViewById(R.id.pp__preview);
    app.organicmaps.eurise.ui.EuriseLuogo.attach(mFrame.findViewById(R.id.eurise_luogo));""")
patch(pp / "PlacePageView.java", """      mOsmDescriptionContainer.setVisibility(VISIBLE);
    }
  }

  void refreshCategoryPreview()""", """      mOsmDescriptionContainer.setVisibility(VISIBLE);
    }
    app.organicmaps.eurise.ui.EuriseLuogo.bind(mMapObject);
  }

  void refreshCategoryPreview()""")
# Le azioni sono nella scheda: in basso restano solo «Parti da qui» e «Segnala» (e tutto quando si sta pianificando).
patch(pp / "PlacePageController.java", """      buttons.add(PlacePageButtons.ButtonType.EURISE_REPORT);
    }
    mViewModel.setCurrentButtons(buttons);""", """      buttons.add(PlacePageButtons.ButtonType.EURISE_REPORT);
      if (!RoutingController.get().isPlanning() && !RoutingController.get().isNavigating() && !mapObject.isTrack()
          && !mapObject.isMyPosition())
      {
        buttons.remove(PlacePageButtons.ButtonType.BOOKMARK_SAVE);
        buttons.remove(PlacePageButtons.ButtonType.BOOKMARK_DELETE);
        buttons.remove(PlacePageButtons.ButtonType.ROUTE_TO);
      }
    }
    mViewModel.setCurrentButtons(buttons);""")
# «Parti ora»: appena il percorso è calcolato, la guida parte da sola.
mwm = android_root / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "MwmActivity.java"
patch(mwm, """    if (!RoutingController.get().isPlanning())
      return;

    closeSearchToolbar(true, true);""", """    if (!RoutingController.get().isPlanning())
      return;

    closeSearchToolbar(true, true);
    // «Parti ora»: la guida parte dopo che CoMaps ha finito di aggiornare il pannello del percorso (chiamata qui dentro,
    // l'aggiornamento successivo del pannello rimetteva la pianificazione sopra la guida).
    if (app.organicmaps.eurise.ui.EuriseLuogo.consumeStartWhenBuilt())
      getWindow().getDecorView().post(() -> {
        if (RoutingController.get().isPlanning() && RoutingController.get().isBuilt())
          onRoutingStart();
      });""")
print("eurise_patch: scheda del luogo con le azioni della suite (passo c)")

# La voce di Alfred anche in Mappe: se c'è Eurise Voce con la voce scaricata, la guida parla con lei; altrimenti, come
# prima, con il motore del telefono. Il testo da leggere va a Voce, sul telefono, e non esce.
tts = android_root / "sdk" / "src" / "main" / "java" / "app" / "organicmaps" / "sdk" / "sound" / "TtsPlayer.java"
patch(tts, "  private boolean mInitializing;\n", """  private boolean mInitializing;
  // Eurise: il motore di Eurise Voce (voce di Alfred) se c'è; se non ha la lingua si torna al motore del telefono.
  @Nullable
  private String mEuriseEngine;
  private boolean mEuriseSkip;

  @Nullable
  private static String euriseEngine(@NonNull Context context)
  {
    for (String p : new String[] {"eu.eurise.voice", "eu.eurise.voice.debug"})
    {
      try
      {
        context.getPackageManager().getPackageInfo(p, 0);
        return p;
      }
      catch (Exception ignored) {}
    }
    return null;
  }
""")
patch(tts, """    mTts = new TextToSpeech(context, status -> UiThread.run(() -> {
      if (status == TextToSpeech.ERROR)""", """    mEuriseEngine = mEuriseSkip ? null : euriseEngine(context);
    mTts = new TextToSpeech(context, status -> UiThread.run(() -> {
      if (mEuriseEngine != null && (status == TextToSpeech.ERROR
          || mTts.isLanguageAvailable(Locale.getDefault()) < TextToSpeech.LANG_AVAILABLE))
      {
        mEuriseEngine = null;
        mEuriseSkip = true;
        mTts.shutdown();
        mTts = null;
        mInitializing = false;
        initialize(context);
        return;
      }
      if (status == TextToSpeech.ERROR)""")
patch(tts, """        sOnReloadCallback.run();
        mReloadTriggered = false;
      }
    }));""", """        sOnReloadCallback.run();
        mReloadTriggered = false;
      }
    }), mEuriseEngine);""")
print("eurise_patch: la guida parla con la voce di Alfred, se c'è")

# Android Auto, correzioni del proprietario dopo la prova del 13 novembre (tavole MapAutoGuida e MapAutoCerca).
# La logica sta in maps/android/…/eurise/car (EuriseCarInfo, EuriseCarVoice); qui gli agganci.
car = android_root / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "car"
libs = root / "libs"

# 1) Collegandosi all'auto la mappa passa lì da sola, senza «Continua in auto»; il telefono mostra «Continua sul
#    telefono» (MapPlaceholderActivity di CoMaps) e la riprende con un tocco. Un solo motore disegna una mappa alla volta.
session = car / "CarAppSession.java"
patch(session, """      screensStack.add(new MapPlaceholderScreen(getCarContext()));
    }
""", """      screensStack.add(new MapPlaceholderScreen(getCarContext()));
      // Eurise: niente domanda, la mappa va subito all'auto (il segnaposto qui sopra si chiude da solo).
      app.organicmaps.sdk.util.concurrency.UiThread.runLater(() -> mDisplayManager.changeDisplay(DisplayType.Car));
    }
""")
# 3) Senza guida la freccia guarda in alto lungo la strada (segui e ruota), come in guida.
patch(session, "  private boolean mInitFailed = false;\n", """  private boolean mInitFailed = false;
  // Eurise: l'ultimo modo della posizione, per capire se «segui senza rotazione» l'ha chiesto chi guida.
  private int mEuriseMode = -1;
""")
patch(session, """  public void onMyPositionModeChanged(int newMode)
  {
""", """  public void onMyPositionModeChanged(int newMode)
  {
    app.organicmaps.eurise.car.EuriseCarInfo.onModeChanged(mEuriseMode, newMode);
    mEuriseMode = newMode;
""")
patch(session, """      ThemeUtils.update(getCarContext());
      restoreRoute();
    }""", """      ThemeUtils.update(getCarContext());
      restoreRoute();
      app.organicmaps.eurise.car.EuriseCarInfo.rotate();
    }""")
patch(session, "    Framework.nativeSetCarScreenMode(true);\n",
      "    Framework.nativeSetCarScreenMode(true);\n    app.organicmaps.eurise.car.EuriseCarInfo.reset();\n")

# 2) Il microfono di Alfred sotto la lente; 4) e 5) senza destinazione «Sei su …», soccorsi e meteo qui.
mapscreen = car / "screens" / "MapScreen.java"
patch(mapscreen, "    builder.addAction(createIconAction(R.drawable.ic_search, this::openSearch));\n",
      """    builder.addAction(createIconAction(R.drawable.ic_search, this::openSearch));
    builder.addAction(createIconAction(R.drawable.eurise_ic_mic, this::euriseVoice));
""")
patch(mapscreen, """    builder.setMapActionStrip(UiHelpers.createMapActionStrip(getCarContext(), getSurfaceRenderer()));
    return builder.build();
  }""", """    builder.setMapActionStrip(UiHelpers.createMapActionStrip(getCarContext(), getSurfaceRenderer()));
    final androidx.car.app.navigation.model.MessageInfo euriseInfo =
        app.organicmaps.eurise.car.EuriseCarInfo.freeDrive(getCarContext(), this::invalidate);
    if (euriseInfo != null)
      builder.setNavigationInfo(euriseInfo);
    return builder.build();
  }

  // Eurise: la strada sotto l'auto e il riferimento per i soccorsi seguono la posizione.
  private final app.organicmaps.sdk.location.LocationListener mEuriseListener = this::euriseLocation;
  @androidx.annotation.Nullable
  private String mEuriseShown;
  @androidx.annotation.Nullable
  private android.location.Location mEuriseShownAt;

  @Override
  public void onStart(@NonNull androidx.lifecycle.LifecycleOwner owner)
  {
    super.onStart(owner);
    app.organicmaps.MwmApplication.from(getCarContext()).getLocationHelper().addListener(mEuriseListener);
  }

  @Override
  public void onStop(@NonNull androidx.lifecycle.LifecycleOwner owner)
  {
    app.organicmaps.MwmApplication.from(getCarContext()).getLocationHelper().removeListener(mEuriseListener);
    super.onStop(owner);
  }

  private void euriseLocation(@NonNull android.location.Location location)
  {
    app.organicmaps.eurise.car.EuriseCarInfo.onLocation(location);
    final String where = app.organicmaps.eurise.car.EuriseCarInfo.where(getCarContext(), null);
    // Si ridisegna quando cambia la strada o dopo 100 m (le coordinate per i soccorsi restano vicine al vero).
    if (!java.util.Objects.equals(where, mEuriseShown) || mEuriseShownAt == null
        || location.distanceTo(mEuriseShownAt) > 100)
    {
      mEuriseShown = where;
      mEuriseShownAt = location;
      invalidate();
    }
  }

  private void euriseVoice()
  {
    if (getScreenManager().getTop() != this)
      return;
    // «Ti ascolto» in una schermata sua, con ogni passo e ogni errore in vista (prova del 16 novembre).
    getScreenManager().push(new app.organicmaps.eurise.car.EuriseCarListenScreen(getCarContext(), text -> {
      if (getScreenManager().getTop() == this)
        getScreenManager().push(
            new SearchScreen.Builder(getCarContext(), getSurfaceRenderer()).setQuery(text).build());
    }));
  }""")

# 4) In guida «Sei su …» e il meteo sul percorso nella bolla dell'arrivo (TravelEstimate.setTripText).
navscreen = car / "screens" / "NavigationScreen.java"
patch(navscreen, "    final RoutingInfo info = Framework.nativeGetRouteFollowingInfo();\n",
      "    app.organicmaps.eurise.car.EuriseCarInfo.onLocation(location);\n"
      "    final RoutingInfo info = Framework.nativeGetRouteFollowingInfo();\n")
patch(navscreen, """    finish();
    getScreenManager().popToRoot();
  }""", """    finish();
    getScreenManager().popToRoot();
    app.organicmaps.eurise.car.EuriseCarInfo.rotate();
  }""")
rutils = car / "util" / "RoutingUtils.java"
patch(rutils, """    builder.addDestination(destinationBuilder.build(),
                           createTravelEstimate(info.distToTarget, info.totalTimeInSeconds));""",
      """    builder.addDestination(destinationBuilder.build(), euriseDestinationEstimate(context, info));""")
patch(rutils, """  @NonNull
  private static CharacterStyle createRoadShieldSpan""", """  /** Eurise (MapAutoGuida): la stima dell'arrivo con «Sei su …» e il meteo sul percorso accanto a km e ora. */
  @SuppressWarnings("NewApi") // ZonedDateTime is backported for Android versions below 8.0.
  @NonNull
  private static TravelEstimate euriseDestinationEstimate(@NonNull final CarContext context, @NonNull RoutingInfo info)
  {
    final TravelEstimate.Builder builder =
        new TravelEstimate.Builder(RoutingHelpers.createDistance(info.distToTarget),
                                   ZonedDateTime.now().plusSeconds(info.totalTimeInSeconds))
            .setRemainingTimeSeconds(info.totalTimeInSeconds)
            .setRemainingDistanceColor(Colors.DISTANCE);
    final String weather = app.organicmaps.eurise.EuriseRouteWeather.carLine(context, info, () -> {});
    final androidx.car.app.model.CarText trip =
        app.organicmaps.eurise.car.EuriseCarInfo.tripText(context, info.currentStreet, weather);
    if (trip != null)
      builder.setTripText(trip);
    return builder.build();
  }

  @NonNull
  private static CharacterStyle createRoadShieldSpan""")

# La strada sotto l'auto: stesso abbinamento del limite di velocità (RoutingManager::GetRoadSpeedLimitMps), ma restituisce
# «classe|sigla|nome»; senza direzione (auto ferma) prende la strada più vicina.
rm_hpp = libs / "map" / "routing_manager.hpp"
patch(rm_hpp, "  double GetRoadSpeedLimitMps(double lat, double lon, double bearingDeg);\n",
      """  double GetRoadSpeedLimitMps(double lat, double lon, double bearingDeg);
  /// Eurise: la strada sotto l'auto, «classe|sigla|nome» (classe = ftypes::HighwayClass); vuota se non c'è.
  /// bearingDeg < 0: auto ferma, si prende la strada più vicina.
  std::string EuriseRoadAt(double lat, double lon, double bearingDeg);
""")
rm_cpp = libs / "map" / "routing_manager.cpp"
patch(rm_cpp, '#include "indexer/data_source.hpp"\n', """#include "indexer/data_source.hpp"
#include "indexer/feature.hpp"
#include "indexer/feature_data.hpp"
#include "indexer/ftypes_matcher.hpp"

#include <algorithm>
""")
patch(rm_cpp, "void RoutingManager::ResetRoadSpeedLimitCache()\n", """std::string RoutingManager::EuriseRoadAt(double lat, double lon, double bearingDeg)
{
  double constexpr kMatchRadiusM = 30.0;

  if (!m_roadSpeedLimit)
    m_roadSpeedLimit = std::make_unique<RoadSpeedLimitState>(m_callbacks.m_dataSourceGetter(),
                                                             m_callbacks.m_countryParentNameGetterFn);
  auto & s = *m_roadSpeedLimit;

  m2::PointD const point = mercator::FromLatLon(lat, lon);
  bool const hasBearing = bearingDeg >= 0;
  double const rad = (hasBearing ? bearingDeg : 0.0) * std::numbers::pi / 180.0;
  m2::PointD const direction(std::sin(rad), std::cos(rad));

  std::vector<std::pair<routing::Edge, geometry::PointWithAltitude>> candidates;
  s.m_graph.FindClosestEdges(mercator::RectByCenterXYAndSizeInMeters(point, kMatchRadiusM), 4, candidates);

  routing::IndexRouter::BestEdgeComparator cmp(point, direction);
  routing::Edge const * best = nullptr;
  double bestDist = std::numeric_limits<double>::max();
  for (auto const & c : candidates)
  {
    if (hasBearing && !cmp.IsAlmostCodirectional(c.first))
      continue;
    double const d = cmp.GetSquaredDist(c.first);
    if (d < bestDist)
    {
      bestDist = d;
      best = &c.first;
    }
  }
  if (!best || !best->GetFeatureId().IsValid())
    return {};

  FeatureID const & fid = best->GetFeatureId();
  FeaturesLoaderGuard guard(m_callbacks.m_dataSourceGetter(), fid.m_mwmId);
  auto ft = guard.GetFeatureByIndex(fid.m_index);
  if (!ft)
    return {};

  auto const cls = ftypes::GetHighwayClass(feature::TypesHolder(*ft));
  std::string ref = ft->GetRef();
  std::string name(ft->GetName(localisation::kDefaultNameIndex));
  std::replace(ref.begin(), ref.end(), '|', ' ');
  std::replace(name.begin(), name.end(), '|', ' ');
  // «A51;E64»: la prima sigla basta.
  if (auto const semi = ref.find(';'); semi != std::string::npos)
    ref.resize(semi);
  return std::to_string(static_cast<int>(cls)) + "|" + ref + "|" + name;
}

void RoutingManager::ResetRoadSpeedLimitCache()
""")
fw_cpp = android_root / "sdk" / "src" / "main" / "cpp" / "app" / "organicmaps" / "sdk" / "Framework.cpp"
patch(fw_cpp, """JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeResetRoadSpeedLimit(JNIEnv *, jclass)
{
  frm()->GetRoutingManager().ResetRoadSpeedLimitCache();
}
""", """JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeResetRoadSpeedLimit(JNIEnv *, jclass)
{
  frm()->GetRoutingManager().ResetRoadSpeedLimitCache();
}

// Eurise: la strada sotto l'auto per Android Auto («Sei su …»), vedi RoutingManager::EuriseRoadAt.
JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeEuriseRoadAt(JNIEnv * env, jclass, jdouble lat,
                                                                               jdouble lon, jdouble bearingDeg)
{
  return jni::ToJavaString(env, frm()->GetRoutingManager().EuriseRoadAt(lat, lon, bearingDeg));
}
""")
fw_java = android_root / "sdk" / "src" / "main" / "java" / "app" / "organicmaps" / "sdk" / "Framework.java"
patch(fw_java, "  public static native void nativeResetRoadSpeedLimit();\n", """  public static native void nativeResetRoadSpeedLimit();
  /** Eurise: «classe|sigla|nome» della strada sotto l'auto; vuota se non c'è. bearingDeg &lt; 0: auto ferma. */
  public static native String nativeEuriseRoadAt(double lat, double lon, double bearingDeg);
""")

# 6) Quando Android Auto riduce la mappa (telefono, musica accanto), freccia e percorso si ricentrano nell'area rimasta:
#    la freccia sta al centro dell'area visibile (non dello schermo intero) e cambiare area la riporta subito lì.
mpc = libs / "drape_frontend" / "my_position_controller.cpp"
patch(mpc, """void MyPositionController::SetVisibleViewport(m2::RectD const & rect)
{
  m_visiblePixelRect = rect;
}""", """void MyPositionController::SetVisibleViewport(m2::RectD const & rect)
{
  // Eurise: se l'area visibile cambia mentre si segue la posizione, la vista si ricentra subito nell'area nuova.
  bool const changed = !m_visiblePixelRect.IsEmptyInterior() && !m2::IsEqual(m_visiblePixelRect, rect, 0.5, 0.5);
  m_visiblePixelRect = rect;
  if (changed && m_isPositionAssigned && (m_mode == location::Follow || m_mode == location::FollowAndRotate))
    UpdateViewport(kDoNotChangeZoom);
}""")
patch(mpc, """  double x = m_visiblePixelRect.Center().x;
  if (m_enablePerspectiveInRouting && ScreenBase::IsCarNavigationView())
    x = std::clamp(m_pixelRect.Center().x, m_visiblePixelRect.minX(), m_visiblePixelRect.maxX());
""", """  // Eurise: sempre al centro dell'area visibile (CoMaps teneva il centro dello schermo intero, fuori centro quando
  // Android Auto riduce la mappa).
  double const x = m_visiblePixelRect.Center().x;
""")

# Microfono (solo quando si tocca il microfono di Alfred) ed Eurise Voce, che trascrive sul telefono.
patch(android_root / "app" / "src" / "main" / "AndroidManifest.xml",
      """  <uses-permission android:name="eu.eurise.permission.SUITE_DEBUG"/>
  <queries>""", """  <uses-permission android:name="eu.eurise.permission.SUITE_DEBUG"/>
  <uses-permission android:name="android.permission.RECORD_AUDIO"/>
  <queries>
    <package android:name="eu.eurise.voice"/>
    <package android:name="eu.eurise.voice.debug"/>""")
print("eurise_patch: Android Auto (apertura diretta, Alfred, freccia in alto, «Sei su», meteo, area ridotta)")

# Passo d) dell'interfaccia rifatta sulle tavole (MapPercorso): sotto il percorso calcolato il pannello della suite in
# Compose (maps/android/…/eurise/ui/EurisePercorso.kt): Auto, Bici, A piedi; impegno con «Parti entro le…»; Manda
# l'arrivo, Salva il luogo; «Avvia la navigazione» grande. Il pulsante «Inizia» di CoMaps resta nel layout, nascosto, e
# fa il lavoro; il calcolo del percorso non cambia.
patch(res / "layout" / "altitude_chart_panel.xml", """      app:iconTint="?android:textColorPrimary" />
  </LinearLayout>

</LinearLayout>""", """      app:iconTint="?android:textColorPrimary" />
  </LinearLayout>

  <androidx.compose.ui.platform.ComposeView
    android:id="@+id/eurise_percorso"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />

</LinearLayout>""")
bottom_menu = android_root / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "routing" / "RoutingBottomMenuController.java"
patch(bottom_menu, """    MaterialButton saveButton = altitudeChartFrame.findViewById(R.id.btn__save);
    saveButton.setOnClickListener(this);
  }""", """    MaterialButton saveButton = altitudeChartFrame.findViewById(R.id.btn__save);
    saveButton.setOnClickListener(this);
    // Eurise (passo d, MapPercorso): il pannello della suite sotto il percorso.
    app.organicmaps.eurise.ui.EurisePercorso.attach(altitudeChartFrame.findViewById(R.id.eurise_percorso), mStart);
  }""")
patch(bottom_menu, """    showRoutingDetails();
    UiUtils.show(mAltitudeChartFrame);""", """    showRoutingDetails();
    UiUtils.show(mAltitudeChartFrame);
    app.organicmaps.eurise.ui.EurisePercorso.update();""")
patch(bottom_menu, """    UiUtils.hide(mAltitudeChartFrame, mTransitFrame);
  }""", """    UiUtils.hide(mAltitudeChartFrame, mTransitFrame);
    app.organicmaps.eurise.ui.EurisePercorso.update();
  }""")
patch(bottom_menu, """    boolean result = show && RoutingController.get().isBuilt();
    UiUtils.showIf(result, mStart);""", """    boolean result = show && RoutingController.get().isBuilt();
    // Eurise: «Avvia la navigazione» è nel pannello della suite; questo resta nascosto ma fa il lavoro.
    UiUtils.showIf(result && !app.organicmaps.eurise.ui.EurisePercorso.getAttached(), mStart);""")
print("eurise_patch: pannello del percorso della suite (passo d)")
# Android Auto di sera (prova del 13 novembre): in «Automatico» CoMaps segue solo il buio dichiarato dall'auto, e al
# tramonto molte auto dicono ancora «giorno»: la mappa restava nello stile chiaro, gialla. Ora è notte anche quando il
# sole è tramontato nel punto in cui si è (stesso calcolo di «Alba e tramonto»).
car_theme = android_root / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "car" / "util" / "ThemeUtils.java"
patch(car_theme, """    if (themeMode == ThemeMode.AUTO)
      return context.isDarkMode();""", """    if (themeMode == ThemeMode.AUTO)
      return context.isDarkMode() || !isDaytime(context);""")
patch(car_theme, """    if (oldThemeMode == ThemeMode.SUNRISE_SUNSET)
      scheduleSunCheck(context);""", """    if (oldThemeMode == ThemeMode.SUNRISE_SUNSET || oldThemeMode == ThemeMode.AUTO)
      scheduleSunCheck(context);""")


# Passo e) della guida, prima parte (tavole MapVelocita, MapIncrocio, MapRotonda, MapAutoGuida).
# Velocità e limite: maps/android/…/eurise/EuriseSpeed.java. La velocità diventa un tondo rosso oltre il limite più la
# tolleranza (Impostazioni › Navigazione), con un suono breve; il cartello del limite resta bianco col bordo rosso.
nav = android_root / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "routing" / "NavigationController.java"
patch(nav, """    final int fSpeedLimit = StringUtils.nativeFormatSpeed(info.speedLimitMps);
    final boolean speedLimitExceeded = fSpeedLimit < StringUtils.nativeFormatSpeed(location.getSpeed());
    mSpeedLimit.setSpeedLimit(fSpeedLimit, speedLimitExceeded);
    mCurrentSpeed.setCurrentSpeed(location.getSpeed());""", """    final int fSpeedLimit = StringUtils.nativeFormatSpeed(info.speedLimitMps);
    // Eurise (MapVelocita): rossa la velocità oltre il limite più la tolleranza; il cartello resta bianco.
    mSpeedLimit.setSpeedLimit(fSpeedLimit, false);
    mCurrentSpeed.setCurrentSpeed(location.getSpeed());
    app.organicmaps.eurise.EuriseSpeed.apply(mSpeedLimit, mCurrentSpeed, info.speedLimitMps, location.getSpeed());
    // La strada sotto l'auto (autostrada o no) decide quando compare la scena dell'uscita.
    app.organicmaps.eurise.car.EuriseCarInfo.onLocation(location);""")
speedview = android_root / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "widget" / "CurrentSpeedView.java"
patch(speedview, "    int ALERT_COLOR = 0xFFFF0000;", "    int ALERT_COLOR = 0xFFC8102E; // Eurise: il rosso dei cartelli")
patch(speedview, "    int TEXT_ALERT_COLOR = 0xFFFF0000;", "    int TEXT_ALERT_COLOR = 0xFFFFFFFF; // Eurise: bianco sul tondo rosso")

# La scena dell'uscita o della rotonda (maps/android/…/eurise/EuriseJunctionScene.java): sul telefono sotto le corsie,
# in verticale (in orizzontale arriva con la tavola MapOrizzontaleGuida); in Android Auto nella finestrella della manovra.
patch(nav, """  @NonNull
  private final CurrentSpeedView mCurrentSpeed;
""", """  @NonNull
  private final CurrentSpeedView mCurrentSpeed;
  // Eurise: la scena dell'uscita o della rotonda (solo nel layout verticale, per ora).
  @Nullable
  private final app.organicmaps.eurise.EuriseJunctionView mEuriseJunction;
""")
patch(nav, """    mCurrentSpeed = topFrame.findViewById(R.id.nav_current_speed);
""", """    mCurrentSpeed = topFrame.findViewById(R.id.nav_current_speed);
    mEuriseJunction = topFrame.findViewById(R.id.eurise_junction);
""")
patch(nav, """    mLanesView.setLanes(info.lanes);
""", """    mLanesView.setLanes(info.lanes);
    if (mEuriseJunction != null)
      mEuriseJunction.update(info);
""")
patch(nav, """    info.pedestrianTurnDirection.setTurnDrawable(mNextTurnImage);
""", """    info.pedestrianTurnDirection.setTurnDrawable(mNextTurnImage);
    if (mEuriseJunction != null)
      mEuriseJunction.hide();
""")
patch(nav, """  public void show(boolean show)
  {
""", """  public void show(boolean show)
  {
    if (!show)
    {
      app.organicmaps.eurise.EuriseSpeed.reset();
      if (mEuriseJunction != null)
        mEuriseJunction.hide();
    }
""")
patch(navscreen, """    if (steps.size() > 1)
      builder.setNextStep(steps.get(1));

    return builder.build();""", """    if (steps.size() > 1)
      builder.setNextStep(steps.get(1));

    // Eurise (MapAutoGuida): la scena dell'uscita o della rotonda nella finestrella della manovra.
    final android.graphics.Bitmap junction = app.organicmaps.eurise.EuriseJunctionScene.bitmap(
        getCarContext(),
        app.organicmaps.eurise.EuriseJunctionScene.from(getCarContext(), Framework.nativeGetRouteFollowingInfo(),
                                                        app.organicmaps.eurise.car.EuriseCarInfo.isFastRoad()),
        640, 360);
    if (junction != null)
      builder.setJunctionImage(new androidx.car.app.model.CarIcon.Builder(
                                   androidx.core.graphics.drawable.IconCompat.createWithBitmap(junction)).build());

    return builder.build();""")

# Impostazioni › Navigazione: tolleranza, suono, avviso del limite che cambia (tavola MapVelocita).
patch(res / "xml" / "prefs_navigation.xml", """    app:icon="@drawable/ic_speed"
    app:singleLineTitle="true" />""", """    app:icon="@drawable/ic_speed"
    app:singleLineTitle="true" />
  <ListPreference
    android:defaultValue="5"
    android:dependency="@string/pref_speedlimit"
    android:entries="@array/eurise_speed_tolerance_entries"
    android:entryValues="@array/eurise_speed_tolerance_values"
    android:key="eurise_speed_tolerance"
    android:summary="%s"
    android:title="@string/eurise_speed_tolerance"
    app:singleLineTitle="false" />
  <SwitchPreferenceCompat
    android:defaultValue="true"
    android:dependency="@string/pref_speedlimit"
    android:key="eurise_speed_sound"
    android:title="@string/eurise_speed_sound"
    android:widgetLayout="@layout/preference_switch"
    app:singleLineTitle="false" />
  <SwitchPreferenceCompat
    android:defaultValue="true"
    android:dependency="@string/pref_speedlimit"
    android:key="eurise_limit_notice"
    android:title="@string/eurise_limit_notice"
    android:summary="@string/eurise_limit_notice_summary"
    android:widgetLayout="@layout/preference_switch"
    app:singleLineTitle="false" />""")
# Impostazioni › Navigazione: i finestrini della guida (tavola MapFinestrini). Uscite, rotonde e incroci si tolgono uno
# per uno; quanto prima compaiono; frecce che si accendono o ferme. Le chiavi le legge EuriseJunctionScene.
patch(res / "xml" / "prefs_navigation.xml", """    android:key="eurise_limit_notice"
    android:title="@string/eurise_limit_notice"
    android:summary="@string/eurise_limit_notice_summary"
    android:widgetLayout="@layout/preference_switch"
    app:singleLineTitle="false" />""", """    android:key="eurise_limit_notice"
    android:title="@string/eurise_limit_notice"
    android:summary="@string/eurise_limit_notice_summary"
    android:widgetLayout="@layout/preference_switch"
    app:singleLineTitle="false" />
  <PreferenceCategory
    android:key="eurise_windows"
    android:title="@string/eurise_windows"
    android:summary="@string/eurise_window_footer"
    app:iconSpaceReserved="false">
    <SwitchPreferenceCompat
      android:defaultValue="true"
      android:key="eurise_window_exit"
      android:title="@string/eurise_window_exit"
      android:summary="@string/eurise_window_exit_summary"
      android:widgetLayout="@layout/preference_switch"
      app:singleLineTitle="false" />
    <SwitchPreferenceCompat
      android:defaultValue="true"
      android:key="eurise_window_roundabout"
      android:title="@string/eurise_window_roundabout"
      android:summary="@string/eurise_window_roundabout_summary"
      android:widgetLayout="@layout/preference_switch"
      app:singleLineTitle="false" />
    <SwitchPreferenceCompat
      android:defaultValue="true"
      android:key="eurise_window_turn"
      android:title="@string/eurise_window_turn"
      android:summary="@string/eurise_window_turn_summary"
      android:widgetLayout="@layout/preference_switch"
      app:singleLineTitle="false" />
    <ListPreference
      android:defaultValue="1"
      android:entries="@array/eurise_window_timing_entries"
      android:entryValues="@array/eurise_window_timing_values"
      android:key="eurise_window_timing"
      android:summary="%s"
      android:title="@string/eurise_window_timing"
      app:singleLineTitle="false" />
    <SwitchPreferenceCompat
      android:defaultValue="true"
      android:key="eurise_window_arrows"
      android:title="@string/eurise_window_arrows"
      android:summary="@string/eurise_window_arrows_summary"
      android:widgetLayout="@layout/preference_switch"
      app:singleLineTitle="false" />
  </PreferenceCategory>
  <PreferenceCategory
    android:key="eurise_guide_side_category"
    android:title="@string/eurise_guide_side_category"
    app:iconSpaceReserved="false">
    <SwitchPreferenceCompat
      android:defaultValue="true"
      android:key="eurise_guide_right"
      android:title="@string/eurise_guide_right"
      android:summary="@string/eurise_guide_right_summary"
      android:widgetLayout="@layout/preference_switch"
      app:singleLineTitle="false" />
  </PreferenceCategory>
  <PreferenceCategory
    android:key="eurise_landscape"
    android:title="@string/eurise_landscape"
    android:summary="@string/eurise_landscape_summary"
    app:iconSpaceReserved="false">
    <SwitchPreferenceCompat
      android:defaultValue="true"
      android:key="eurise_sky_map"
      android:title="@string/eurise_sky_map"
      android:summary="@string/eurise_sky_map_summary"
      android:widgetLayout="@layout/preference_switch"
      app:singleLineTitle="false" />
    <SwitchPreferenceCompat
      android:defaultValue="true"
      android:key="eurise_sky_weather"
      android:title="@string/eurise_sky_weather"
      android:summary="@string/eurise_sky_weather_summary"
      android:widgetLayout="@layout/preference_switch"
      app:singleLineTitle="false" />
    <SwitchPreferenceCompat
      android:defaultValue="true"
      android:key="eurise_seasons"
      android:title="@string/eurise_seasons"
      android:summary="@string/eurise_seasons_summary"
      android:widgetLayout="@layout/preference_switch"
      app:singleLineTitle="false" />
    <SwitchPreferenceCompat
      android:defaultValue="true"
      android:key="eurise_feasts"
      android:title="@string/eurise_feasts"
      android:summary="@string/eurise_feasts_summary"
      android:widgetLayout="@layout/preference_switch"
      app:singleLineTitle="false" />
    <SwitchPreferenceCompat
      android:defaultValue="true"
      android:key="eurise_your_days"
      android:title="@string/eurise_your_days"
      android:summary="@string/eurise_your_days_summary"
      android:widgetLayout="@layout/preference_switch"
      app:singleLineTitle="false" />
  </PreferenceCategory>""")
print("eurise_patch: guida, prima parte (velocità e limite, scena dell'uscita e della rotonda, anche in Android Auto)")

# Passo e) della guida, seconda parte: Segnala in guida (tavole MapSegnalaGuida e MapAutoSegnala). La logica sta in
# maps/android/…/eurise (EuriseGuideReports, EuriseGuideReportActivity, EuriseReportReviewActivity) e in
# eurise/car/EuriseCarReportScreen; qui gli agganci. Sei voci a un tocco, Annulla per 5 secondi; autovelox, limite errato e
# strada chiusa vanno a OpenStreetMap come nota anonima a fine viaggio, dopo un'occhiata all'elenco; lavori, coda e
# pericolo restano sul telefono due ore e avvisano chi ripassa di lì.
manifest = android_root / "app" / "src" / "main" / "AndroidManifest.xml"
patch(manifest, """    <activity
        android:name="app.organicmaps.eurise.EuriseReportActivity\"""", """    <!-- Segnala in guida: dalla bandierina o da Alfred (app della suite con la stessa firma, EXTRA_KIND). -->
    <activity
        android:name="app.organicmaps.eurise.EuriseGuideReportActivity"
        android:theme="@android:style/Theme.Translucent.NoTitleBar"
        android:exported="true"
        android:permission="eu.eurise.permission.SUITE_DEBUG"
        android:excludeFromRecents="true" />

    <activity
        android:name="app.organicmaps.eurise.EuriseReportReviewActivity"
        android:theme="@android:style/Theme.Translucent.NoTitleBar"
        android:exported="false"
        android:excludeFromRecents="true" />

    <activity
        android:name="app.organicmaps.eurise.EuriseReportActivity\"""")

# Sul telefono, in guida (tavola MapGuidaVertB): i pulsanti della mappa stanno sopra la scheda satinata in basso.
patch(res / "layout" / "map_buttons_layout_navigation.xml", 'android:layout_marginBottom="@dimen/nav_menu_height"',
      'android:layout_marginBottom="156dp"')
patch(res / "layout" / "map_buttons_layout_navigation.xml", 'android:layout_marginBottom="@dimen/nav_menu_height"',
      'android:layout_marginBottom="156dp"')


# Fine del viaggio (arrivo o «Fine»): l'elenco per OpenStreetMap; se si era in auto o col telefono in tasca, alla
# prossima apertura della mappa.
mwm = android_root / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "MwmActivity.java"
patch(mwm, """  public void onNavigationCancelled()
  {
    closeFloatingToolbarsAndPanels(true);
""", """  public void onNavigationCancelled()
  {
    closeFloatingToolbarsAndPanels(true);
    app.organicmaps.eurise.EuriseGuideReports.tripEnded(this);
""")
patch(mwm, """    MwmApplication.from(this).getSensorHelper().addListener(this);
  }""", """    MwmApplication.from(this).getSensorHelper().addListener(this);
    app.organicmaps.eurise.EuriseGuideReports.maybeReview(this);
  }""")

# Chi ripassa di lì: «Tua segnalazione di 25 min fa: Coda tra 300 m», una volta, nella stessa direzione.
patch(nav, """    app.organicmaps.eurise.car.EuriseCarInfo.onLocation(location);""",
      """    app.organicmaps.eurise.car.EuriseCarInfo.onLocation(location);
    final String euriseWarning = app.organicmaps.eurise.EuriseGuideReports.warning(mFrame.getContext(), location);
    if (euriseWarning != null)
      android.widget.Toast.makeText(mFrame.getContext(), euriseWarning, android.widget.Toast.LENGTH_LONG).show();""")

# Android Auto (MapAutoSegnala): la bandierina nella fila in alto della guida apre la griglia di sei voci sopra la mappa.
patch(navscreen, """    builder.addAction(stopActionBuilder.build());
    return builder.build();""", """    builder.addAction(new Action.Builder()
                          .setIcon(new CarIcon.Builder(IconCompat.createWithResource(getCarContext(),
                                                                                     R.drawable.eurise_ic_flag)).build())
                          .setOnClickListener(() -> getScreenManager().push(
                              new app.organicmaps.eurise.car.EuriseCarReportScreen(getCarContext(), getSurfaceRenderer())))
                          .build());
    builder.addAction(stopActionBuilder.build());
    return builder.build();""")
patch(navscreen, """    NavigationService.stopService(getCarContext());
    ThemeUtils.update(getCarContext());
    finish();""", """    NavigationService.stopService(getCarContext());
    ThemeUtils.update(getCarContext());
    app.organicmaps.eurise.EuriseGuideReports.tripEnded(getCarContext());
    finish();""")
patch(navscreen, """    mNavigationManager.updateTrip(mTrip);
""", """    mNavigationManager.updateTrip(mTrip);
    final String euriseWarning = app.organicmaps.eurise.EuriseGuideReports.warning(getCarContext(), location);
    if (euriseWarning != null)
      CarToast.makeText(getCarContext(), euriseWarning, CarToast.LENGTH_LONG).show();
""")
# Il microfono di Alfred in auto: «autovelox», «c'è una coda» segnano qui invece di cercare.
patch(mapscreen, """    getScreenManager().push(new app.organicmaps.eurise.car.EuriseCarListenScreen(getCarContext(), text -> {
      if (getScreenManager().getTop() == this)""", """    getScreenManager().push(new app.organicmaps.eurise.car.EuriseCarListenScreen(getCarContext(), text -> {
      final app.organicmaps.eurise.EuriseGuideReports.Kind euriseKind =
          app.organicmaps.eurise.EuriseGuideReports.Kind.fromWords(text);
      if (euriseKind != null)
      {
        final app.organicmaps.eurise.EuriseGuideReports.Report r =
            app.organicmaps.eurise.EuriseGuideReports.add(getCarContext(), euriseKind);
        androidx.car.app.CarToast.makeText(getCarContext(),
            r == null ? getCarContext().getString(R.string.eurise_guide_no_position)
                      : getCarContext().getString(euriseKind.label) + " "
                        + app.organicmaps.eurise.EuriseGuideReports.placed(getCarContext(), r),
            androidx.car.app.CarToast.LENGTH_LONG).show();
        return;
      }
      if (getScreenManager().getTop() == this)""")
print("eurise_patch: guida, seconda parte (Segnala in guida, sul telefono e in Android Auto; elenco di fine viaggio)")

# Guida in orizzontale (tavola MapOrizzontaleGuida): layout nostri in maps/android/…/res/layout-land (barra della guida a
# sinistra, colonna della manovra a destra), qui solo gli agganci: barra e arrivo in Compose, aggiornati a ogni guida.
patch(nav, """    mCurrentSpeed = topFrame.findViewById(R.id.nav_current_speed);
""", """    mCurrentSpeed = topFrame.findViewById(R.id.nav_current_speed);
    app.organicmaps.eurise.ui.EuriseGuidaOrizzontale.attachBar(mFrame.findViewById(R.id.eurise_guida_bar));
    app.organicmaps.eurise.ui.EuriseGuidaOrizzontale.attachCard(mFrame.findViewById(R.id.eurise_guida_card));
    app.organicmaps.eurise.ui.EuriseGuidaOrizzontale.applySide(mFrame);
    app.organicmaps.eurise.ui.EuriseGuidaOrizzontale.attachArrival(topFrame.findViewById(R.id.eurise_guida_arrivo));
""")
patch(nav, """    mNavMenu.update(info);
  }""", """    mNavMenu.update(info);
    app.organicmaps.eurise.ui.EuriseGuidaOrizzontale.update(info);
  }""")
# In orizzontale la manovra sta nella colonna a destra: lo spazio che CoMaps lascia a sinistra per la fotocamera e le
# barre di sistema (pensato per i riquadri a sinistra) diventava una striscia vuota accanto al cartello (prova del 14
# novembre, sera). Nella colonna niente spazio a sinistra; il margine destro della colonna tiene conto delle barre.
patch(nav, """          view.setPadding(windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).left, view.getPaddingTop(),""",
      """          view.setPadding(topFrame.findViewById(R.id.eurise_guide_column) != null ? 0
                          : windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).left, view.getPaddingTop(),""")
patch(nav, """      UiUtils.setViewInsetsPaddingNoTopNoBottom(v, windowInsets);

      final Insets safeDrawingInsets = windowInsets.getInsets(WindowInsetUtils.TYPE_SAFE_DRAWING);
      nextTurnContainer.setPadding(safeDrawingInsets.left, nextTurnContainer.getPaddingTop(),""",
      """      final View euriseColumn = topFrame.findViewById(R.id.eurise_guide_column);
      if (euriseColumn == null)
        UiUtils.setViewInsetsPaddingNoTopNoBottom(v, windowInsets);

      final Insets safeDrawingInsets = windowInsets.getInsets(WindowInsetUtils.TYPE_SAFE_DRAWING);
      if (euriseColumn != null)
      {
        final android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) euriseColumn.getLayoutParams();
        final int m = Math.round(14 * v.getResources().getDisplayMetrics().density);
        lp.setMarginStart(m + safeDrawingInsets.left);
        lp.setMarginEnd(m + safeDrawingInsets.right);
        euriseColumn.setLayoutParams(lp);
      }
      nextTurnContainer.setPadding(euriseColumn != null ? 0 : safeDrawingInsets.left, nextTurnContainer.getPaddingTop(),""")
print("eurise_patch: guida in orizzontale (MapOrizzontaleGuida)")

# «Livelli e fonti» (tavola MapLivelli): sotto i livelli di CoMaps le voci della guida e di cosa esce dal telefono.
patch(res / "layout" / "fragment_toggle_map_layer.xml", """    android:paddingStart="@dimen/margin_base"
    android:paddingEnd="@dimen/margin_base" />
</LinearLayout>""", """    android:paddingStart="@dimen/margin_base"
    android:paddingEnd="@dimen/margin_base" />

  <androidx.compose.ui.platform.ComposeView
    android:id="@+id/eurise_livelli"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
</LinearLayout>""")
layers = android_root / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "maplayer" / "ToggleMapLayerFragment.java"
patch(layers, """    initRecycler(mRoot);
    return mRoot;""", """    initRecycler(mRoot);
    app.organicmaps.eurise.ui.EuriseLivelli.attach(mRoot.findViewById(R.id.eurise_livelli));
    return mRoot;""")
print("eurise_patch: livelli e fonti (MapLivelli)")

# Benzinai col prezzo (tavola MapAttivita): l'elenco della suite, aperto da «Cerca vicino» (EuriseSearchHome).
patch(manifest, """    <activity
        android:name="app.organicmaps.eurise.EuriseReportReviewActivity\"""", """    <activity
        android:name="app.organicmaps.eurise.ui.EuriseFuelActivity"
        android:theme="@style/Theme.AppCompat.DayNight.NoActionBar"
        android:exported="false" />

    <activity
        android:name="app.organicmaps.eurise.EuriseReportReviewActivity\"""")
print("eurise_patch: benzinai col prezzo (MapAttivita)")


# Cielo e colline in guida (tavola MapCieloColline, approvata il 14 novembre). Il disegno sta in maps/engine/libs/
# drape_frontend/eurise_sky.{hpp,cpp} (copiati qui sotto) e in EuriseSky.java (l'immagine, con i colori di
# EuriseLandscape); qui gli agganci: inclinazione in guida, disegno dopo palazzi e percorso, ponte da Java.
engine = Path(__file__).resolve().parent / "engine"
for f in engine.rglob("*"):
    if f.is_file():
        dest = root / f.relative_to(engine)
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(f, dest)
patch(libs / "drape_frontend" / "CMakeLists.txt", "  screen_quad_renderer.cpp\n  screen_quad_renderer.hpp\n",
      "  screen_quad_renderer.cpp\n  screen_quad_renderer.hpp\n  eurise_sky.cpp\n  eurise_sky.hpp\n")

sb_cpp = libs / "geometry" / "screenbase.cpp"
patch(sb_cpp, "static std::atomic<bool> g_isCarNavigationView{false};\n", """static std::atomic<bool> g_isCarNavigationView{false};

// Eurise: con il cielo acceso la mappa in guida si inclina fino a 70-71° invece di 53/55°, ma il piano resta grande
// quanto con l'inclinazione di CoMaps (CalculateScale3d): stesse tessere, stessa vista vicino all'auto, e il bordo
// lontano scende sotto il bordo alto dello schermo, con circa il 27% di cielo come nella tavola.
static std::atomic<bool> g_euriseSky{false};
double constexpr kEuriseSkyCarAngle = math::pi * 70.0 / 180.0;
double constexpr kEuriseSkyAngle = math::pi * 71.0 / 180.0;
""")
patch(sb_cpp, "// static\ndouble ScreenBase::CalculateAutoPerspectiveAngle(double scale)\n{",
      "static double CoMapsAutoPerspectiveAngle(double scale)\n{")
patch(sb_cpp, "  return kMaxPerspectiveAngle2 * 0.99;\n}\n", """  return kMaxPerspectiveAngle2 * 0.99;
}

// static
double ScreenBase::CalculateAutoPerspectiveAngle(double scale)
{
  double const angle = CoMapsAutoPerspectiveAngle(scale);
  if (!g_euriseSky || angle <= 0.0)
    return angle;
  // Stessa salita di CoMaps col cambiare della scala, ma fino all'angolo del cielo.
  return g_isCarNavigationView ? angle / kCarPerspectiveAngle * kEuriseSkyCarAngle
                               : angle / kMaxPerspectiveAngle2 * kEuriseSkyAngle;
}

// static
void ScreenBase::SetEuriseSky(bool enabled)
{
  g_euriseSky = enabled;
}

// static
bool ScreenBase::IsEuriseSky()
{
  return g_euriseSky;
}
""")
patch(sb_cpp, "double ScreenBase::CalculateScale3d(double rotationAngle) const\n{\n",
      """double ScreenBase::CalculateScale3d(double rotationAngle) const
{
  // Eurise: col cielo il piano non cresce oltre quello dell'inclinazione piena di CoMaps (vedi g_euriseSky).
  if (g_euriseSky)
    rotationAngle = std::min(rotationAngle, (g_isCarNavigationView ? kCarPerspectiveAngle : kMaxPerspectiveAngle2) * 0.99);

""")
patch(sb_cpp, "#include <atomic>\n", "#include <algorithm>\n#include <atomic>\n")
patch(libs / "geometry" / "screenbase.hpp", "  static bool IsCarNavigationView();\n", """  static bool IsCarNavigationView();
  /// Eurise: cielo e colline in guida (drape_frontend/eurise_sky): la prospettiva automatica si inclina di più.
  static void SetEuriseSky(bool enabled);
  static bool IsEuriseSky();
""")

fr_cpp = libs / "drape_frontend" / "frontend_renderer.cpp"
patch(fr_cpp, '#include "drape_frontend/drape_notifier.hpp"\n',
      '#include "drape_frontend/drape_notifier.hpp"\n#include "drape_frontend/eurise_sky.hpp"\n')
patch(fr_cpp, """      RenderRouteLayer(modelView);
    }

    m_context->Clear(dp::ClearBits::DepthBit, dp::kClearBitsStoreAll);
""", """      RenderRouteLayer(modelView);
    }

    // Eurise: cielo e colline sopra il bordo lontano della mappa, davanti a palazzi e percorso, sotto scritte e segni.
    EuriseSkyRender(m_context, make_ref(m_gpuProgramManager), modelView);

    m_context->Clear(dp::ClearBits::DepthBit, dp::kClearBitsStoreAll);
""")
t = fr_cpp.read_text()
if t.count("  m_screenQuadRenderer.reset();\n") != 2:
    sys.exit(f"eurise_patch: in {fr_cpp} m_screenQuadRenderer.reset() non compare due volte")
fr_cpp.write_text(t.replace("  m_screenQuadRenderer.reset();\n", "  m_screenQuadRenderer.reset();\n  EuriseSkyReset();\n"))

patch(fw_cpp, '#include "drape_frontend/user_event_stream.hpp"\n',
      '#include "drape_frontend/eurise_sky.hpp"\n#include "drape_frontend/user_event_stream.hpp"\n')
patch(fw_cpp, """// Eurise: la strada sotto l'auto per Android Auto""", """// Eurise: cielo e colline in guida. pixels = ARGB di Android (righe dall'alto); null = spento (vista di CoMaps).
JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeEuriseSetSky(JNIEnv * env, jclass, jintArray pixels,
                                                                              jint width, jint height)
{
  std::vector<uint8_t> rgba;
  bool on = pixels != nullptr && width > 0 && height > 0 && env->GetArrayLength(pixels) >= width * height;
  if (on)
  {
    rgba.resize(static_cast<size_t>(width) * height * 4);
    jint * argb = env->GetIntArrayElements(pixels, nullptr);
    for (size_t i = 0, n = static_cast<size_t>(width) * height; i < n; ++i)
    {
      auto const c = static_cast<uint32_t>(argb[i]);
      rgba[i * 4 + 0] = static_cast<uint8_t>(c >> 16);
      rgba[i * 4 + 1] = static_cast<uint8_t>(c >> 8);
      rgba[i * 4 + 2] = static_cast<uint8_t>(c);
      rgba[i * 4 + 3] = static_cast<uint8_t>(c >> 24);
    }
    env->ReleaseIntArrayElements(pixels, argb, JNI_ABORT);
  }
  df::EuriseSkySetImage(std::move(rgba), on ? width : 0, on ? height : 0);
  ScreenBase::SetEuriseSky(on);
  frm()->InvalidateRendering();
}

// Eurise: la strada sotto l'auto per Android Auto""")
patch(fw_java, "  public static native void nativeResetRoadSpeedLimit();\n", """  public static native void nativeResetRoadSpeedLimit();
  /** Eurise: immagine del cielo e delle colline in guida (ARGB, righe dall'alto); null = spento. */
  public static native void nativeEuriseSetSky(@androidx.annotation.Nullable int[] pixels, int width, int height);
""")

patch(nav, """    if (show && !UiUtils.isVisible(mFrame))
      collapseNavMenu();
    UiUtils.showIf(show, mFrame);""", """    if (show && !UiUtils.isVisible(mFrame))
      collapseNavMenu();
    UiUtils.showIf(show, mFrame);
    app.organicmaps.eurise.EuriseSky.guide(mFrame.getContext(), show);""")
patch(nav, """    mNavMenu.update(info);
    app.organicmaps.eurise.ui.EuriseGuidaOrizzontale.update(info);""", """    mNavMenu.update(info);
    app.organicmaps.eurise.ui.EuriseGuidaOrizzontale.update(info);
    app.organicmaps.eurise.EuriseSky.tick(mFrame.getContext());""")
# Esplorando, tasto «3D»/«2D» (tavola MapEsploraInclinata, approvata il 17 novembre): la prospettiva automatica della
# guida anche fuori dalla guida, con lo stesso cielo. CoMaps non ha un messaggio per spegnerla: EnablePerspective legge
# il desiderio messo da nativeEuriseSetExplore3d (vero di base, così l'inizio della guida resta com'era).
patch(fr_cpp, """  case Message::Type::EnablePerspective:
  {
    AddUserEvent(make_unique_dp<SetAutoPerspectiveEvent>(true /* isAutoPerspective */));""", """  case Message::Type::EnablePerspective:
  {
    AddUserEvent(make_unique_dp<SetAutoPerspectiveEvent>(EuriseTakePerspectiveWish()));""")
patch(fw_cpp, """// Eurise: la strada sotto l'auto per Android Auto""", """// Eurise: esplorando, mappa inclinata fino al cielo («3D») o piatta («2D»), tavola MapEsploraInclinata.
JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeEuriseSetExplore3d(JNIEnv *, jclass, jboolean on)
{
  auto engine = frm()->GetDrapeEngine();
  if (engine == nullptr)
    return;
  df::EuriseSetPerspectiveWish(on);
  engine->EnablePerspective();
}

// Eurise: la strada sotto l'auto per Android Auto""")
patch(fw_java, "  public static native void nativeResetRoadSpeedLimit();\n", """  public static native void nativeResetRoadSpeedLimit();
  /** Eurise: esplorando, prospettiva automatica accesa («3D», fino al cielo) o spenta («2D»). */
  public static native void nativeEuriseSetExplore3d(boolean on);
""")
print("eurise_patch: cielo e colline in guida (MapCieloColline)")

# «Dove sei» mentre si esplora (tavola MapEsplora): il comune in un punto, dalle mappe scaricate (CityFinder del motore di
# ricerca, lo stesso che CoMaps usa per le località; nome locale, per la sigla della provincia dalla tabella ISTAT).
patch(fw_cpp, '#include "storage/country_info_getter.hpp"\n',
      '#include "search/city_finder.hpp"\n\n#include "i18n/localisation.hpp"\n\n#include "storage/country_info_getter.hpp"\n')
patch(fw_cpp, """// Eurise: la strada sotto l'auto per Android Auto""", """// Eurise: il comune nel punto (nome locale), vuoto se lì non c'è una mappa. Thread principale, come le altre chiamate.
JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeEuriseCityAt(JNIEnv * env, jclass, jdouble lat,
                                                                                 jdouble lon)
{
  static std::unique_ptr<search::CityFinder> finder;
  if (!finder)
    finder = std::make_unique<search::CityFinder>(frm()->GetDataSource());
  return jni::ToJavaString(env, finder->GetCityName(mercator::FromLatLon(lat, lon), static_cast<int8_t>(localisation::kDefaultNameIndex)));
}

// Eurise: la strada sotto l'auto per Android Auto""")
patch(fw_java, "  public static native void nativeResetRoadSpeedLimit();\n", """  public static native void nativeResetRoadSpeedLimit();
  /** Eurise: il comune nel punto (nome locale), vuoto se lì non c'è una mappa scaricata. */
  @NonNull
  public static native String nativeEuriseCityAt(double lat, double lon);
""")
print("eurise_patch: «Dove sei» mentre si esplora (MapEsplora)")

# Nomi delle vie più leggibili (tavola MapEsplora): nelle scritte delle strade da auto (dalle autostrade alle vie di
# quartiere) un punto in più e il grigio chiaro portato al medio. Scudi, sentieri e piste restano come in CoMaps.
import re as _re
roads_label = root / "data" / "styles" / "default" / "include" / "Roads_label.mapcss"
_txt = roads_label.read_text(encoding="utf-8")
_a = _txt.index("/* 3.TRUNK & MOTORWAY")
_b = _txt.index("/* Don't display names of sidewalks")
_mid = _re.sub(r"(?<!shield-)font-size: (\d+);", lambda m: "font-size: %d;" % (int(m.group(1)) + 1), _txt[_a:_b])
_mid = _mid.replace("text-color: @label_light;", "text-color: @label_medium;")
assert _mid != _txt[_a:_b]
roads_label.write_text(_txt[:_a] + _mid + _txt[_b:], encoding="utf-8")
print("eurise_patch: nomi delle vie più leggibili")

# Esplora (tavola MapEsplora): l'orizzonte col meteo in alto, sopra la mappa e sotto i pulsanti (non prende i tocchi), e
# le scorciatoie Benzinai, Farmacie, Mangiare, Negozi, Professionisti nella schermata normale della mappa.
patch(res / "layout" / "activity_map.xml", """    <app.organicmaps.sdk.MapView
      android:id="@+id/map"
      android:layout_width="match_parent"
      android:layout_height="match_parent" />
""", """    <app.organicmaps.sdk.MapView
      android:id="@+id/map"
      android:layout_width="match_parent"
      android:layout_height="match_parent" />
    <app.organicmaps.eurise.EuriseSkyBand
      android:id="@+id/eurise_sky_band"
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      android:layout_alignParentTop="true" />
""")
# Tutte le varianti della schermata normale (telefoni bassi e alti, verticale e orizzontale).
for _variant in ("layout", "layout-h400dp", "layout-land", "layout-h400dp-land"):
    patch(res / _variant / "map_buttons_layout_regular.xml", """  <include
    layout="@layout/map_status_track_recording\"""", """  <androidx.compose.ui.platform.ComposeView
    android:id="@+id/eurise_explore_chips"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_alignParentTop="true" />
  <include
    layout="@layout/map_status_track_recording\"""")
# In orizzontale (tavola MapOrizzontale) la barra della suite al posto dei pulsanti di CoMaps, come in verticale: i
# pulsanti restano nel layout (MapButtonsController li cerca per id) dentro un contenitore nascosto.
_land_bottom = res / "layout-land" / "map_buttons_bottom.xml"
_lb = _land_bottom.read_text(encoding="utf-8")
_head = """<androidx.constraintlayout.widget.ConstraintLayout
  xmlns:android="http://schemas.android.com/apk/res/android"
  xmlns:app="http://schemas.android.com/apk/res-auto"
  android:id="@+id/map_buttons_bottom"
  style="@style/MwmWidget.M3.FAB.MapButton.Bottom"
  android:layout_width="match_parent\""""
assert _head in _lb
_lb = _lb.replace(_head, """<RelativeLayout
  xmlns:android="http://schemas.android.com/apk/res/android"
  xmlns:app="http://schemas.android.com/apk/res-auto"
  android:id="@+id/map_buttons_bottom"
  android:layout_width="match_parent"
  android:layout_height="wrap_content">
  <androidx.compose.ui.platform.ComposeView
    android:id="@+id/eurise_map_bar"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_alignParentBottom="true" />
<androidx.constraintlayout.widget.ConstraintLayout
  android:visibility="gone"
  style="@style/MwmWidget.M3.FAB.MapButton.Bottom"
  android:layout_width="match_parent\"""", 1)
_lb = _lb.rstrip() + "\n</RelativeLayout>\n"
_land_bottom.write_text(_lb, encoding="utf-8")
patch(mbc, "    app.organicmaps.eurise.ui.EuriseMapBar.attach(euriseBar, mMapButtonClickListener);\n",
      "    app.organicmaps.eurise.ui.EuriseMapBar.attach(euriseBar, mMapButtonClickListener);\n"
      "    app.organicmaps.eurise.ui.EuriseEsplora.attach(mFrame.findViewById(R.id.eurise_explore_chips));\n")
print("eurise_patch: orizzonte e scorciatoie mentre si esplora (MapEsplora)")
# Tasto «3D»/«2D» mentre si esplora (tavola MapEsploraInclinata): a destra, sotto la fascia, in tutte le varianti.
for _variant in ("layout", "layout-h400dp", "layout-land", "layout-h400dp-land"):
    patch(res / _variant / "map_buttons_layout_regular.xml", """  <androidx.compose.ui.platform.ComposeView
    android:id="@+id/eurise_explore_chips\"""", """  <app.organicmaps.eurise.EuriseTilt
    android:id="@+id/eurise_tilt"
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:layout_alignParentTop="true"
    android:layout_alignParentEnd="true" />
  <androidx.compose.ui.platform.ComposeView
    android:id="@+id/eurise_explore_chips\"""")
print("eurise_patch: tasto 3D mentre si esplora (MapEsploraInclinata)")

# Ricerca (tavola MapCerca): il campo diventa una pillola satinata sulla testata, con un po' d'aria attorno; vale per
# tutte le barre di ricerca di Mappe (ricerca, risultati sulla mappa), così hanno la stessa forma.
patch(res / "layout" / "toolbar_search_controls.xml", """  android:id="@+id/search_container"
  android:layout_width="match_parent"
  android:layout_height="wrap_content\"""", """  android:id="@+id/search_container"
  android:layout_width="match_parent"
  android:layout_height="wrap_content"
  android:layout_marginTop="6dp"
  android:layout_marginBottom="6dp"
  android:layout_marginEnd="12dp"
  android:background="@drawable/eurise_search_pill\"""")
print("eurise_patch: campo di ricerca a pillola (MapCerca)")

# Scheda del luogo aperta (tavola MapLuogoAperta): sotto gli orari di CoMaps, contatti e indirizzo, «Nella suite» e «Altro».
patch(res / "layout" / "place_page_details.xml", """    <androidx.fragment.app.FragmentContainerView
      android:id="@+id/place_page_opening_hours_fragment"
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      tools:layout="@layout/place_page_opening_hours_fragment" />
""", """    <androidx.fragment.app.FragmentContainerView
      android:id="@+id/place_page_opening_hours_fragment"
      android:layout_width="match_parent"
      android:layout_height="wrap_content"
      tools:layout="@layout/place_page_opening_hours_fragment" />

    <androidx.compose.ui.platform.ComposeView
      android:id="@+id/eurise_luogo_aperta"
      android:layout_width="match_parent"
      android:layout_height="wrap_content" />
""")
patch(pp / "PlacePageView.java", "    app.organicmaps.eurise.ui.EuriseLuogo.attach(mFrame.findViewById(R.id.eurise_luogo));",
      "    app.organicmaps.eurise.ui.EuriseLuogo.attach(mFrame.findViewById(R.id.eurise_luogo));\n"
      "    app.organicmaps.eurise.ui.EuriseLuogoAperta.attach(mFrame.findViewById(R.id.eurise_luogo_aperta));")
# Gli orari nello stile della tavola li disegna EuriseOrari.kt (in cima a EuriseLuogoAperta): il riquadro di CoMaps
# non si aggiunge più. Il «Aperto · chiude alle…» in testa alla scheda resta quello di CoMaps.
patch(pp / "PlacePageView.java", """    updateViewFragment(PlacePageOpeningHoursFragment.class, OPENING_HOURS_FRAGMENT_TAG,
                       R.id.place_page_opening_hours_fragment, !TextUtils.isEmpty(ohStr));""",
      """    updateViewFragment(PlacePageOpeningHoursFragment.class, OPENING_HOURS_FRAGMENT_TAG,
                       R.id.place_page_opening_hours_fragment, false && !TextUtils.isEmpty(ohStr));""")
print("eurise_patch: scheda del luogo aperta (MapLuogoAperta, orari della tavola)")

# Il limite che arriva (tavola MapVelocita, «tra 200 m: 70»): dai tratti del percorso in corso, il primo limite diverso
# da quello di adesso entro aheadM metri. {km/h, metri} oppure null.
patch(fw_cpp, """// Eurise: la strada sotto l'auto per Android Auto""", """// Eurise: il prossimo limite di velocità sul percorso (MapVelocita).
JNIEXPORT jdoubleArray JNICALL Java_app_organicmaps_sdk_Framework_nativeEuriseNextSpeedLimit(JNIEnv * env, jclass,
                                                                                             jdouble aheadM)
{
  auto const route = frm()->GetRoutingManager().RoutingSession().GetRouteForTests();
  if (!route || !route->IsValid())
    return nullptr;
  auto const & segs = route->GetRouteSegments();
  size_t const idx = route->GetFollowedPolyline().GetCurrentIter().m_ind;
  if (segs.empty() || idx >= segs.size())
    return nullptr;
  auto const & cur = segs[idx].GetSpeedLimit();
  double const here = route->GetCurrentDistanceFromBeginMeters();
  for (size_t i = idx + 1; i < segs.size(); ++i)
  {
    // La distanza di un tratto è misurata alla sua fine: il tratto i comincia dove finisce i-1.
    double const ahead = segs[i - 1].GetDistFromBeginningMeters() - here;
    if (ahead > aheadM)
      break;
    auto const & next = segs[i].GetSpeedLimit();
    if (!next.IsNumeric())
      continue;
    if (cur.IsNumeric() && cur.GetSpeedKmPH() == next.GetSpeedKmPH())
      continue;
    jdouble const out[2] = {static_cast<jdouble>(next.GetSpeedKmPH()), ahead < 0 ? 0 : ahead};
    jdoubleArray arr = env->NewDoubleArray(2);
    env->SetDoubleArrayRegion(arr, 0, 2, out);
    return arr;
  }
  return nullptr;
}

// Eurise: la strada sotto l'auto per Android Auto""")
patch(fw_java, "  public static native String nativeEuriseCityAt(double lat, double lon);\n",
      """  public static native String nativeEuriseCityAt(double lat, double lon);
  /** Eurise: il prossimo limite sul percorso entro aheadM metri, {km/h, metri}; null se non cambia. */
  @Nullable
  public static native double[] nativeEuriseNextSpeedLimit(double aheadM);
""")
print("eurise_patch: il limite che arriva (MapVelocita)")

# Android Auto (tavola MapAutoCerca): «Dove devi andare» in cima alla ricerca, prima di Categorie e recenti.
car_search = android_root / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "car" / "screens" / "search" / "SearchScreen.java"
patch(car_search, """    builder.addItem(createCategoriesItem());
    SearchRecents.refresh();
    final int recentsSize = Math.min(SearchRecents.getSize(), MAX_RESULTS_SIZE - 1);""", """    app.organicmaps.eurise.car.EuriseCarDestinations.addRows(getCarContext(), builder, this::onSearchSubmitted);
    builder.addItem(createCategoriesItem());
    SearchRecents.refresh();
    final int recentsSize = Math.max(0, Math.min(SearchRecents.getSize(),
        MAX_RESULTS_SIZE - 1 - app.organicmaps.eurise.car.EuriseCarDestinations.size()));""")
patch(car_search, """    mResults = builder.build();
    
    return recentsSize != 0;""", """    mResults = builder.build();
    
    return recentsSize != 0 || app.organicmaps.eurise.car.EuriseCarDestinations.size() > 0;""")
patch(car_search, """    onSearchSubmitted(builder.mQuery);
""", """    onSearchSubmitted(builder.mQuery);
    app.organicmaps.eurise.car.EuriseCarDestinations.refresh(getCarContext(), () -> {
      if (mQuery.isEmpty())
      {
        mResults = null;
        invalidate();
      }
    });
""")
print("eurise_patch: «Dove devi andare» nella ricerca di Android Auto (MapAutoCerca)")

# Android Auto: la panoramica del percorso prima di partire si riadatta quando l'auto riduce la mappa (lista o scheda
# più larghe): stesso riquadro di CoMaps (percorso × 1,5) nella parte di schermo che resta visibile.
patch(fw_cpp, """// Eurise: la strada sotto l'auto per Android Auto""", """// Eurise: rimette tutto il percorso nella parte visibile della mappa (panoramica prima di partire).
JNIEXPORT void JNICALL Java_app_organicmaps_sdk_Framework_nativeEuriseShowRouteOverview(JNIEnv *, jclass)
{
  auto const route = frm()->GetRoutingManager().RoutingSession().GetRouteForTests();
  if (!route || !route->IsValid())
    return;
  m2::RectD rect = route->GetPoly().GetLimitRect();
  rect.Scale(1.5);
  frm()->ShowRect(rect, -1 /* maxScale */, true /* animation */, true /* useVisibleViewport */);
}

// Eurise: la strada sotto l'auto per Android Auto""")
patch(fw_java, "  public static native double[] nativeEuriseNextSpeedLimit(double aheadM);\n",
      "  public static native double[] nativeEuriseNextSpeedLimit(double aheadM);\n"
      "  /** Eurise: tutto il percorso nella parte visibile della mappa (Android Auto, prima di partire). */\n"
      "  public static native void nativeEuriseShowRouteOverview();\n")
car_surface = android_root / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "car" / "renderer" / "SurfaceCallbackBase.java"
patch(car_surface, """    if (!mVisibleArea.isEmpty())
      UiThread.runLater(()
                            -> Framework.nativeSetVisibleRect(mVisibleArea.left, mVisibleArea.top, mVisibleArea.right,
                                                              mVisibleArea.bottom));
  }
""", """    if (!mVisibleArea.isEmpty())
      UiThread.runLater(() -> {
        Framework.nativeSetVisibleRect(mVisibleArea.left, mVisibleArea.top, mVisibleArea.right, mVisibleArea.bottom);
        // Eurise: prima di partire, il percorso intero resta nella parte di mappa che si vede.
        final app.organicmaps.sdk.routing.RoutingController rc = app.organicmaps.sdk.routing.RoutingController.get();
        if (rc.isPlanning() && rc.isBuilt() && !rc.isNavigating())
          Framework.nativeEuriseShowRouteOverview();
      });
  }
""")
print("eurise_patch: panoramica del percorso che si riadatta in Android Auto")

# Mezzi pubblici e viaggi lontani (tavole MapMezzi, MapLontano, MapLontanoScelta): la schermata della suite.
patch(manifest, """    <activity
        android:name="app.organicmaps.eurise.ui.EuriseFuelActivity\"""", """    <activity
        android:name="app.organicmaps.eurise.ui.EuriseMezziActivity"
        android:theme="@style/Theme.AppCompat.DayNight.NoActionBar"
        android:exported="false" />

    <activity
        android:name="app.organicmaps.eurise.ui.EuriseFuelActivity\"""")
print("eurise_patch: mezzi pubblici e viaggi lontani (MapMezzi, MapLontano)")

# Tendina delle notifiche: l'icona piccola è quella di Eurise Mappe (res/drawable/ic_logo_small.xml in maps/android, al
# posto del logo di CoMaps) e la notifica della guida prende il blu notte della suite invece del verde di CoMaps.
patch(res / "values" / "colors.xml", """  <color name="notification">@color/base_accent</color>""",
      """  <color name="notification">#FF1F2A44</color>""")
print("eurise_patch: icona e colore di Eurise Mappe nella tendina delle notifiche")

# Scritte e segni lontani che sporgevano oltre l'orizzonte nel cielo (prova di Mappe 58): si disegnano solo sotto le
# colline (drape_frontend/eurise_sky.cpp, EuriseSkyClipLabels), poi il taglio dello schermo torna pieno.
patch(fr_cpp, """    {
      StencilWriterGuard guard(make_ref(m_postprocessRenderer), m_context);
      RenderOverlayLayer(modelView);
    }
""", """    {
      StencilWriterGuard guard(make_ref(m_postprocessRenderer), m_context);
      bool const euriseClip = EuriseSkyClipLabels(m_context, m_viewport);
      RenderOverlayLayer(modelView);
      if (euriseClip)
        m_viewport.Apply(m_context);
    }
""")
print("eurise_patch: niente scritte nel cielo oltre l'orizzonte")

# Il finestrino della svolta con l'incrocio vero (prova di Mappe 58: a un incrocio a T non si disegna la strada dritta):
# le direzioni di tutte le strade da auto che partono dall'incrocio più vicino al punto, dal grafo stradale di CoMaps.
patch(rm_hpp, "  std::string EuriseRoadAt(double lat, double lon, double bearingDeg);\n",
      """  std::string EuriseRoadAt(double lat, double lon, double bearingDeg);
  /// Eurise: le direzioni (gradi da nord, in senso orario, separate da «;») delle strade da auto che partono
  /// dall'incrocio più vicino al punto (entro 15 m); vuota se non c'è un incrocio vicino.
  std::string EuriseJunctionArms(double lat, double lon);
""")
patch(rm_cpp, "void RoutingManager::ResetRoadSpeedLimitCache()\n", """std::string RoutingManager::EuriseJunctionArms(double lat, double lon)
{
  double constexpr kSearchM = 40.0, kNodeM = 15.0, kSameDeg = 12.0;

  if (!m_roadSpeedLimit)
    m_roadSpeedLimit = std::make_unique<RoadSpeedLimitState>(m_callbacks.m_dataSourceGetter(),
                                                             m_callbacks.m_countryParentNameGetterFn);
  auto & s = *m_roadSpeedLimit;

  m2::PointD const point = mercator::FromLatLon(lat, lon);
  std::vector<std::pair<routing::Edge, geometry::PointWithAltitude>> candidates;
  s.m_graph.FindClosestEdges(mercator::RectByCenterXYAndSizeInMeters(point, kSearchM), 12, candidates);

  // Il nodo del grafo più vicino al punto: un capo di uno dei tratti vicini.
  geometry::PointWithAltitude node;
  double best = std::numeric_limits<double>::max();
  for (auto const & c : candidates)
  {
    if (c.first.IsFake())
      continue;
    for (auto const & j : {c.first.GetStartJunction(), c.first.GetEndJunction()})
    {
      double const d = mercator::DistanceOnEarth(point, j.GetPoint());
      if (d < best)
      {
        best = d;
        node = j;
      }
    }
  }
  if (best > kNodeM)
    return {};

  routing::IRoadGraph::EdgeListT out, in;
  s.m_graph.GetOutgoingEdges(node, out);
  s.m_graph.GetIngoingEdges(node, in);
  std::vector<double> arms;
  auto const add = [&](m2::PointD const & dir)
  {
    if (dir.Length() < 1e-12)
      return;
    double deg = std::atan2(dir.x, dir.y) * 180.0 / std::numbers::pi;
    if (deg < 0)
      deg += 360.0;
    for (double a : arms)
    {
      double diff = std::fabs(a - deg);
      diff = std::min(diff, 360.0 - diff);
      if (diff < kSameDeg)
        return;
    }
    arms.push_back(deg);
  };
  for (auto const & e : out)
    if (!e.IsFake())
      add(e.GetEndPoint() - e.GetStartPoint());
  for (auto const & e : in)
    if (!e.IsFake())
      add(e.GetStartPoint() - e.GetEndPoint());

  std::string res;
  for (double a : arms)
  {
    if (!res.empty())
      res += ';';
    res += std::to_string(static_cast<int>(std::lround(a)) % 360);
  }
  return res;
}

void RoutingManager::ResetRoadSpeedLimitCache()
""")
patch(fw_cpp, """// Eurise: la strada sotto l'auto per Android Auto («Sei su …»), vedi RoutingManager::EuriseRoadAt.""",
      """// Eurise: le strade che partono da un incrocio (finestrino della svolta), vedi RoutingManager::EuriseJunctionArms.
JNIEXPORT jstring JNICALL Java_app_organicmaps_sdk_Framework_nativeEuriseJunctionArms(JNIEnv * env, jclass, jdouble lat,
                                                                                     jdouble lon)
{
  return jni::ToJavaString(env, frm()->GetRoutingManager().EuriseJunctionArms(lat, lon));
}

// Eurise: la strada sotto l'auto per Android Auto («Sei su …»), vedi RoutingManager::EuriseRoadAt.""")
patch(fw_java, "  public static native String nativeEuriseRoadAt(double lat, double lon, double bearingDeg);\n",
      """  public static native String nativeEuriseRoadAt(double lat, double lon, double bearingDeg);
  /** Eurise: direzioni (gradi da nord, «;») delle strade da auto che partono dall'incrocio vicino; vuota se non c'è. */
  public static native String nativeEuriseJunctionArms(double lat, double lon);
""")
print("eurise_patch: le strade vere dell'incrocio per il finestrino della svolta")

# Android Auto con lo schermo diviso (mappa accanto ad altre app): la scheda dell'auto con «Sei su …» e meteo sparisce,
# quindi una pillola nostra sopra la mappa con velocità, via e meteo (eurise/car/EuriseCarSplitInfo.java).
car_sc = android_root / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "car" / "renderer" / "SurfaceCallback.java"
patch(car_sc, """    mPillOverlay.addView(mSpeedPill, pillParams);
  }""", """    mPillOverlay.addView(mSpeedPill, pillParams);
    mPillOverlay.addView(app.organicmaps.eurise.car.EuriseCarSplitInfo.create(mCarContext));
  }""")
patch(car_sc, """    if (mVirtualDisplay == null)
    {
      Logger.e(TAG, "Invalid virtual display, aborting.");
      return;
    }
""", """    if (mVirtualDisplay == null)
    {
      Logger.e(TAG, "Invalid virtual display, aborting.");
      return;
    }
    app.organicmaps.eurise.car.EuriseCarSplitInfo.onSurface(surfaceContainer.getWidth(), surfaceContainer.getHeight());
""")
patch(car_sc, """    mPillOverlay.setLayoutParams(getOverlayLayoutParams());
    updatePillSize();
  }""", """    mPillOverlay.setLayoutParams(getOverlayLayoutParams());
    updatePillSize();
    app.organicmaps.eurise.car.EuriseCarSplitInfo.onVisibleArea(visibleArea);
  }""")
print("eurise_patch: velocità, via e meteo con lo schermo diviso in Android Auto")

# Il microfono di Alfred anche nella ricerca di Android Auto (prova del 16 novembre: la dettatura della tastiera dell'auto
# non funziona senza i servizi Google): la frase diventa il testo cercato. In guida il microfono sta nella schermata della
# bandierina (EuriseCarReportScreen), non nella fila in alto: lì ci sono già voce, impostazioni, bandierina e stop, e
# l'auto ne accetta al massimo quattro (con il quinto Mappe 69 si chiudeva all'avvio della navigazione).
patch(car_search, """    if (!mQuery.isEmpty() && mResults != null && !mResults.getItems().isEmpty())
      builder.setActionStrip(createActionStrip());""", """    // Eurise: il microfono di Alfred sempre qui (la dettatura della tastiera dell'auto non va senza Google), e
    // «Mostra sulla mappa» quando ci sono risultati.
    builder.setActionStrip(euriseActionStrip(!mQuery.isEmpty() && mResults != null && !mResults.getItems().isEmpty()));""")
patch(car_search, """  @NonNull
  private ActionStrip createActionStrip()
  {""", """  @NonNull
  private ActionStrip euriseActionStrip(boolean withMap)
  {
    final ActionStrip.Builder strip = new ActionStrip.Builder();
    strip.addAction(new Action.Builder()
        .setIcon(new CarIcon.Builder(IconCompat.createWithResource(getCarContext(), R.drawable.eurise_ic_mic)).build())
        .setOnClickListener(() -> getScreenManager().push(new app.organicmaps.eurise.car.EuriseCarListenScreen(
            getCarContext(), this::onSearchSubmitted)))
        .build());
    if (withMap)
      strip.addAction(createActionStrip().getActions().get(0));
    return strip.build();
  }

  @NonNull
  private ActionStrip createActionStrip()
  {""")
print("eurise_patch: microfono di Alfred in guida e nella ricerca di Android Auto, schermata «Ti ascolto»")

# Il percorso come nelle tavole MapViaggio4Percorso e MapViaggio6Bici (approvate il 16 novembre): in alto la scheda di
# vetro con partenza, tappe e arrivo e le pillole «Evita …» e «+ Tappa» (eurise/ui/EuriseViaggio.kt) al posto della barra
# di CoMaps con i cinque tipi di percorso e delle sue opzioni, che restano nel layout ma nascoste (il controllore le usa).
patch(res / "layout" / "routing_plan.xml", """  android:orientation="vertical"
  android:clipToPadding="false">
  <FrameLayout android:layout_width="match_parent\"""", """  android:orientation="vertical"
  android:clipToPadding="false">
  <androidx.compose.ui.platform.ComposeView
    android:id="@+id/eurise_viaggio_top"
    android:layout_width="match_parent"
    android:layout_height="wrap_content" />
  <FrameLayout android:layout_width="match_parent\"""")
rpc = android_root / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "routing" / "RoutingPlanController.java"
patch(rpc, """    onTurnPreviewChanged(RoutingController.get().isPreviewingTurn());
  }""", """    onTurnPreviewChanged(RoutingController.get().isPreviewingTurn());
    // Eurise (MapViaggio4Percorso): la scheda di vetro al posto della barra e delle opzioni di CoMaps.
    app.organicmaps.eurise.ui.EuriseViaggio.attachTop(mFrame.findViewById(R.id.eurise_viaggio_top), getToolbar(),
                                                      mRoutingOptionsBanner);
  }""")
patch(rpc, """  public void showRoutingOptionsView()
  {
""", """  public void showRoutingOptionsView()
  {
    // Eurise: le scelte attive sono pillole nella scheda di vetro; il riquadro di CoMaps non si mostra.
    if (app.organicmaps.eurise.ui.EuriseViaggio.getAttached())
    {
      UiUtils.hide(mRoutingOptionsBanner);
      return;
    }
""")
print("eurise_patch: percorso con la scheda di vetro, le pillole e le tappe (MapViaggio4Percorso, 5, 6)")

# La scheda del luogo come nella tavola MapViaggio3Luogo: le azioni sono tutte nella scheda (Indicazioni, Parti ora,
# Salva, Chiama, Impegno, Condividi); la barra dei pulsanti di CoMaps in basso sparisce quando non si pianifica (prima
# restavano «Parti da qui» e «Segnala»), e torna con le sue voci di percorso mentre si pianifica o si guida.
patch(pp / "PlacePageController.java", """        buttons.remove(PlacePageButtons.ButtonType.ROUTE_TO);
      }""", """        buttons.remove(PlacePageButtons.ButtonType.ROUTE_TO);
        buttons.remove(PlacePageButtons.ButtonType.ROUTE_FROM);
        buttons.remove(PlacePageButtons.ButtonType.EURISE_REPORT);
      }""")
patch(pp / "PlacePageButtons.java", """  public void onChanged(List<ButtonType> buttonTypes)
  {
    createButtons(buttonTypes);
  }""", """  public void onChanged(List<ButtonType> buttonTypes)
  {
    createButtons(buttonTypes);
    // Eurise: senza pulsanti la barra non occupa spazio (scheda del luogo della tavola MapViaggio3Luogo).
    if (getView() != null)
      getView().setVisibility(buttonTypes == null || buttonTypes.isEmpty() ? View.GONE : View.VISIBLE);
  }""")
print("eurise_patch: scheda del luogo senza la barra di CoMaps (MapViaggio3Luogo)")

# La ricerca come nella tavola MapViaggio2Dove: titolo grande, impegni, Casa e Lavoro, recenti, e il campo in basso sotto
# il pollice, sopra la tastiera (eurise/ui/EuriseSearchHome.kt, bottomField). La barra di CoMaps è la stessa, spostata.
patch(search_layout, """      android:background="?colorPrimary"
      android:elevation="@dimen/dp_0"
      app:elevation="@dimen/dp_0">""", """      android:layout_gravity="bottom"
      android:background="@android:color/transparent"
      android:elevation="@dimen/dp_0"
      app:elevation="@dimen/dp_0">""")
patch(search_layout, """          android:layout_height="wrap_content"
          app:layout_scrollFlags="scroll|enterAlways"/>
    </com.google.android.material.appbar.AppBarLayout>

    <FrameLayout
      android:layout_width="match_parent"
      android:layout_height="match_parent"
      app:layout_behavior="@string/appbar_scrolling_view_behavior">""", """          android:layout_height="wrap_content"/>
    </com.google.android.material.appbar.AppBarLayout>

    <FrameLayout
      android:id="@+id/eurise_search_content"
      android:layout_width="match_parent"
      android:layout_height="match_parent">""")
patch(search, """    ViewCompat.setOnApplyWindowInsetsListener(mResultsPlaceholder, new WindowInsetUtils.ScrollableContentInsetsListener(mResultsPlaceholder, mOpenContactPicker));""",
      """    ViewCompat.setOnApplyWindowInsetsListener(mResultsPlaceholder, new WindowInsetUtils.ScrollableContentInsetsListener(mResultsPlaceholder, mOpenContactPicker));
    app.organicmaps.eurise.ui.EuriseSearchHome.bottomField(root);""")
print("eurise_patch: ricerca col campo in basso (MapViaggio2Dove)")

# La bolla della musica e delle chiamate in guida (tavole MapBollaAuto, MapBollaAutoChiamata, MapBollaTelefono, approvate
# il 16 novembre): codice in eurise/EuriseNowPlaying*.java, EuriseMediaBubble.java, EuriseLyrics.java; qui l'accesso alle
# notifiche (lo dà la persona nelle impostazioni del telefono; con l'interruttore di Livelli spento non si legge nulla) e
# gli agganci nella guida del telefono e sullo schermo dell'auto.
patch(manifest, """    <service
        android:name="app.organicmaps.car.CarAppService\"""", """    <service
        android:name="app.organicmaps.eurise.EuriseNowPlayingService"
        android:exported="true"
        android:label="@string/eurise_lv_media"
        android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
      <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
      </intent-filter>
    </service>
    <service
        android:name="app.organicmaps.car.CarAppService\"""")
navc = android_root / "app" / "src" / "main" / "java" / "app" / "organicmaps" / "routing" / "NavigationController.java"
patch(navc, """    app.organicmaps.eurise.ui.EuriseGuidaOrizzontale.attachArrival(topFrame.findViewById(R.id.eurise_guida_arrivo));
""", """    app.organicmaps.eurise.ui.EuriseGuidaOrizzontale.attachArrival(topFrame.findViewById(R.id.eurise_guida_arrivo));
    app.organicmaps.eurise.EuriseMediaBubble.attachPhone(mFrame);
""")
patch(car_sc, """    mPillOverlay.addView(app.organicmaps.eurise.car.EuriseCarSplitInfo.create(mCarContext));
""", """    mPillOverlay.addView(app.organicmaps.eurise.car.EuriseCarSplitInfo.create(mCarContext));
    mPillOverlay.addView(app.organicmaps.eurise.EuriseMediaBubble.car(
        mCarContext, () -> app.organicmaps.car.util.ThemeUtils.isNightMode(mCarContext)));
""")
print("eurise_patch: bolla della musica e delle chiamate in guida, sul telefono e in Android Auto")

# Le otto aggiunte alla bolla (tavole MapBollaPodcast…MapBollaArrivo e MapBollaAutoStati, approvate il 17 novembre):
# codice in eurise/EuriseTrip.java, EuriseSms.java, EuriseSpeak.java, EuriseReplyActivity.java, car/EuriseCarAlerts.java,
# car/EuriseCarShareScreen.java. Qui l'attività trasparente (risposta a voce, scelta dalla Rubrica) e la visibilità di
# Eurise Messaggi, che manda gli SMS di «Avvisa» e dell'arrivo condiviso.
patch(manifest, """    <service
        android:name="app.organicmaps.eurise.EuriseNowPlayingService\"""", """    <activity
        android:name="app.organicmaps.eurise.EuriseReplyActivity"
        android:exported="false"
        android:excludeFromRecents="true"
        android:noHistory="false"
        android:theme="@android:style/Theme.Translucent.NoTitleBar" />
    <service
        android:name="app.organicmaps.eurise.EuriseNowPlayingService\"""")
patch(manifest, """    <package android:name="eu.eurise.voice.debug"/>""", """    <package android:name="eu.eurise.voice.debug"/>
    <package android:name="eu.eurise.messages"/>
    <package android:name="eu.eurise.messages.debug"/>""")
print("eurise_patch: bolla, otto aggiunte (avvisi, messaggi, arrivo condiviso)")

# Android Auto: il riquadro della manovra nel verde della suite come nelle tavole approvate (MapAutoGuida, MapBollaAuto),
# non nel nero dell'auto. Il colore lo applica l'auto: può tenerne uno più scuro se il contrasto col testo bianco non basta.
# Il riquadro dell'arrivo (tempo e km) non ha un colore che l'app possa scegliere.
patch(navscreen, """    builder.setNavigationInfo(getNavigationInfo());
    return builder.build();""", """    builder.setNavigationInfo(getNavigationInfo());
    builder.setBackgroundColor(androidx.car.app.model.CarColor.createCustom(0xFF2F6B45, 0xFF2F6B45));
    return builder.build();""")
print("eurise_patch: Android Auto, riquadro della manovra verde")

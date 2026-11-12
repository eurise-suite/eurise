package app.organicmaps.eurise;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import app.organicmaps.R;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.downloader.CountryItem;
import app.organicmaps.sdk.downloader.MapManager;
import app.organicmaps.util.Utils;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Ricerca di Eurise Mappe (piano del 12 novembre: «la ricerca non trova facilmente i luoghi, es. Apice, BN»).
 *
 * 1. {@link #local}: la domanda per il motore sul telefono senza la sigla della provincia («Apice, BN» → «Apice»): il
 *    motore di CoMaps non conosce le sigle e con la virgola cerca due parole.
 * 2. {@link #update}: se nelle mappe scaricate non c'è nulla, una scheda offre la ricerca online su OpenStreetMap
 *    (Photon). È spenta di base: la prima volta spiega cosa esce (solo le parole cercate, nessuna posizione) e si spegne
 *    con un tocco. Toccando un risultato la mappa va lì e, se la regione non è scaricata, propone di scaricarla.
 */
public final class EuriseOnlineSearch
{
  private static final String PREFS = "eurise_search";
  private static final String KEY_ON = "online";
  private static final String TAG = "eurise_online";
  private static final ExecutorService IO = Executors.newSingleThreadExecutor();
  private static final Handler MAIN = new Handler(Looper.getMainLooper());
  /** «, BN», «(BN)», « BN» in fondo: una sigla di provincia di due lettere maiuscole. */
  private static final Pattern PROVINCE = Pattern.compile("\\s*[,(]?\\s*\\b[A-Z]{2}\\)?\\s*$");
  private static String sLastQuery = "";
  private static List<Place> sLastResults = new ArrayList<>();
  /** App Eurise che ha il centro privacy (la versione di prova prima), come per il meteo sul percorso. */
  private static final String[] HUBS = {"eu.eurise.hub.debug", "eu.eurise.hub"};
  /** Il nome del servizio nel centro privacy (core/suite PrivacyService.MapSearch, PrivacyContract). */
  private static final String SERVICE = "MapSearch";

  private EuriseOnlineSearch() {}

  /** Un luogo trovato online: nome, dove sta (comune, provincia, regione) e coordinate. */
  static final class Place
  {
    final String name;
    final String where;
    final double lat;
    final double lon;

    Place(String name, String where, double lat, double lon)
    {
      this.name = name;
      this.where = where;
      this.lat = lat;
      this.lon = lon;
    }
  }

  /** La domanda per il motore sul telefono: senza la sigla della provincia e senza spazi doppi. */
  @NonNull
  public static String local(@Nullable String query)
  {
    if (query == null)
      return "";
    String q = query.trim();
    if (q.length() > 4 && PROVINCE.matcher(q).find())
      q = PROVINCE.matcher(q).replaceFirst("");
    q = q.replaceAll("(?i)\\bprovincia di\\b", "").replaceAll("\\s+", " ").trim();
    return q.isEmpty() ? query.trim() : q;
  }

  private static SharedPreferences prefs(Context c)
  {
    return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  /**
   * Il centro privacy dell'app Eurise: «status», «enable», «disable», «log» per la ricerca online. Risponde con lo
   * stato, o null se l'app Eurise manca (allora vale solo l'interruttore qui). Mai dal thread principale.
   */
  @Nullable
  private static Boolean hub(Context c, String method)
  {
    for (String h : HUBS)
    {
      try
      {
        android.os.Bundle b = c.getContentResolver().call(android.net.Uri.parse("content://" + h + ".privacy"), method, SERVICE, null);
        if (b != null)
          return b.getBoolean("on");
      }
      catch (Exception ignored)
      {
        // App Eurise assente, di un'altra firma o senza il fornitore: si prova l'altra.
      }
    }
    return null;
  }

  /** Accende o spegne qui e nel centro privacy. */
  private static void setOn(Context c, boolean on)
  {
    prefs(c).edit().putBoolean(KEY_ON, on).apply();
    final Context app = c.getApplicationContext();
    IO.execute(() -> hub(app, on ? "enable" : "disable"));
  }

  /**
   * Chiamata da SearchFragment quando cambia lo stato dei risultati: [empty] vero se la ricerca sul telefono non ha
   * trovato nulla. Mostra (o toglie) la scheda della ricerca online dentro il riquadro dei risultati.
   */
  public static void update(@Nullable View resultsFrame, boolean empty, @Nullable String query, @NonNull Fragment fragment)
  {
    if (!(resultsFrame instanceof FrameLayout) || !fragment.isAdded())
      return;
    final FrameLayout frame = (FrameLayout) resultsFrame;
    View old = frame.findViewWithTag(TAG);
    if (!empty || TextUtils.isEmpty(query))
    {
      if (old != null)
        frame.removeView(old);
      return;
    }
    final Context c = fragment.requireContext();
    final LinearLayout box;
    if (old instanceof ScrollView)
    {
      box = (LinearLayout) ((ScrollView) old).getChildAt(0);
    }
    else
    {
      ScrollView scroll = new ScrollView(c);
      scroll.setTag(TAG);
      scroll.setBackgroundColor(resolveColor(c, android.R.attr.colorBackground));
      box = new LinearLayout(c);
      box.setOrientation(LinearLayout.VERTICAL);
      int pad = dp(c, 16);
      box.setPadding(pad, dp(c, 12), pad, pad);
      scroll.addView(box);
      frame.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }
    render(box, query.trim(), fragment);
    // Il centro privacy decide: se lì è stata spenta (o accesa), la scheda si aggiorna.
    final Context app = c.getApplicationContext();
    final String q = query.trim();
    IO.execute(() -> {
      Boolean on = hub(app, "status");
      if (on == null || on == prefs(app).getBoolean(KEY_ON, false))
        return;
      prefs(app).edit().putBoolean(KEY_ON, on).apply();
      MAIN.post(() -> {
        if (fragment.isAdded() && box.getParent() != null)
          render(box, q, fragment);
      });
    });
  }

  private static void render(LinearLayout box, String query, Fragment fragment)
  {
    final Context c = box.getContext();
    box.removeAllViews();
    box.addView(text(c, c.getString(R.string.eurise_search_none_local, query), 17, true));
    box.addView(text(c, c.getString(R.string.eurise_search_none_local_sub), 13.5f, false));
    final boolean on = prefs(c).getBoolean(KEY_ON, false);
    if (!on)
    {
      box.addView(button(c, c.getString(R.string.eurise_search_online), true, v -> explain(fragment, () -> search(box, query, fragment))));
      box.addView(text(c, c.getString(R.string.eurise_search_online_note), 12, false));
      box.addView(button(c, c.getString(R.string.eurise_search_download), false, v -> openDownloader(fragment)));
      return;
    }
    if (query.equals(sLastQuery) && !sLastResults.isEmpty())
      showResults(box, query, sLastResults, fragment);
    else
      search(box, query, fragment);
  }

  /** La prima volta: cosa esce e dove va; «Cerca» accende la ricerca online per le prossime volte. */
  private static void explain(Fragment fragment, Runnable then)
  {
    final Context c = fragment.requireContext();
    if (prefs(c).getBoolean(KEY_ON, false))
    {
      then.run();
      return;
    }
    new AlertDialog.Builder(c)
        .setTitle(R.string.eurise_search_online_title)
        .setMessage(R.string.eurise_search_online_explain)
        .setPositiveButton(R.string.eurise_search_online_ok, (d, w) -> {
          setOn(c, true);
          then.run();
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
  }

  private static void search(LinearLayout box, String query, Fragment fragment)
  {
    final Context c = box.getContext();
    box.removeAllViews();
    box.addView(text(c, c.getString(R.string.eurise_search_online_working, query), 15, true));
    final String lang = Locale.getDefault().getLanguage();
    final Context app = c.getApplicationContext();
    IO.execute(() -> {
      // Spenta dal centro privacy nel frattempo: niente richiesta.
      Boolean allowed = hub(app, "status");
      if (Boolean.FALSE.equals(allowed))
      {
        prefs(app).edit().putBoolean(KEY_ON, false).apply();
        MAIN.post(() -> { if (fragment.isAdded() && box.getParent() != null) render(box, query, fragment); });
        return;
      }
      List<Place> found = fetch(query, lang);
      if (found != null)
        hub(app, "log");
      MAIN.post(() -> {
        if (!fragment.isAdded() || box.getParent() == null)
          return;
        sLastQuery = query;
        sLastResults = found == null ? new ArrayList<>() : found;
        if (found == null)
        {
          box.removeAllViews();
          box.addView(text(c, c.getString(R.string.eurise_search_online_failed), 15, true));
          box.addView(button(c, c.getString(R.string.eurise_search_retry), true, v -> search(box, query, fragment)));
          return;
        }
        showResults(box, query, found, fragment);
      });
    });
  }

  private static void showResults(LinearLayout box, String query, List<Place> found, Fragment fragment)
  {
    final Context c = box.getContext();
    box.removeAllViews();
    box.addView(text(c, c.getString(found.isEmpty() ? R.string.eurise_search_online_empty : R.string.eurise_search_online_found, query), 15, true));
    for (Place p : found)
    {
      LinearLayout row = new LinearLayout(c);
      row.setOrientation(LinearLayout.VERTICAL);
      int pad = dp(c, 14);
      row.setPadding(pad, dp(c, 10), pad, dp(c, 10));
      GradientDrawable bg = new GradientDrawable();
      bg.setCornerRadius(dp(c, 16));
      bg.setColor(resolveColor(c, com.google.android.material.R.attr.colorSurface));
      row.setBackground(bg);
      row.addView(text(c, p.name, 15.5f, true));
      if (!p.where.isEmpty())
        row.addView(text(c, p.where, 12.5f, false));
      row.setOnClickListener(v -> open(fragment, p));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
      lp.topMargin = dp(c, 8);
      box.addView(row, lp);
    }
    TextView off = text(c, c.getString(R.string.eurise_search_online_off), 12.5f, true);
    off.setTextColor(resolveColor(c, androidx.appcompat.R.attr.colorPrimary));
    off.setPadding(0, dp(c, 14), 0, dp(c, 4));
    off.setOnClickListener(v -> {
      setOn(c, false);
      render(box, query, fragment);
    });
    box.addView(off);
    box.addView(text(c, c.getString(R.string.eurise_search_online_source), 11.5f, false));
  }

  /** Il luogo sulla mappa; se la sua regione non è sul telefono, propone di scaricarla. */
  private static void open(Fragment fragment, Place p)
  {
    final Activity a = fragment.requireActivity();
    Framework.nativeSetViewportCenter(p.lat, p.lon, 14);
    final String country = MapManager.nativeFindCountry(p.lat, p.lon);
    CountryItem item = TextUtils.isEmpty(country) ? null : CountryItem.fill(country);
    if (item != null && item.status != CountryItem.STATUS_DONE && item.status != CountryItem.STATUS_UPDATABLE
        && item.status != CountryItem.STATUS_PROGRESS && item.status != CountryItem.STATUS_ENQUEUED)
    {
      final long mb = Math.max(1, (item.totalSize > 0 ? item.totalSize : item.size) / (1024 * 1024));
      new AlertDialog.Builder(a)
          .setTitle(a.getString(R.string.eurise_search_get_region, item.name))
          .setMessage(a.getString(R.string.eurise_search_get_region_sub, item.name, mb))
          .setPositiveButton(R.string.eurise_search_get_region_ok, (d, w) -> {
            MapManager.startDownload(country);
            Utils.navigateToParent(a);
          })
          .setNegativeButton(R.string.eurise_search_get_region_later, (d, w) -> Utils.navigateToParent(a))
          .show();
      return;
    }
    Utils.navigateToParent(a);
  }

  private static void openDownloader(Fragment fragment)
  {
    try
    {
      Activity a = fragment.requireActivity();
      a.startActivity(new android.content.Intent(a, app.organicmaps.downloader.DownloaderActivity.class));
    }
    catch (Throwable ignored) {}
  }

  /** Photon (OpenStreetMap, komoot): solo le parole cercate e la lingua; nessuna posizione, nessun identificativo. */
  @Nullable
  private static List<Place> fetch(String query, String lang)
  {
    HttpURLConnection conn = null;
    try
    {
      String l = ("it".equals(lang) || "de".equals(lang) || "fr".equals(lang)) ? lang : "en";
      URL url = new URL("https://photon.komoot.io/api/?limit=8&lang=" + l + "&q=" + URLEncoder.encode(query, "UTF-8"));
      conn = (HttpURLConnection) url.openConnection();
      conn.setConnectTimeout(8000);
      conn.setReadTimeout(8000);
      conn.setRequestProperty("User-Agent", "EuriseMappe/1.0");
      if (conn.getResponseCode() != 200)
        return null;
      StringBuilder sb = new StringBuilder();
      try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8")))
      {
        String line;
        while ((line = r.readLine()) != null)
          sb.append(line);
      }
      JSONArray features = new JSONObject(sb.toString()).optJSONArray("features");
      List<Place> out = new ArrayList<>();
      if (features == null)
        return out;
      for (int i = 0; i < features.length(); i++)
      {
        JSONObject f = features.getJSONObject(i);
        JSONObject props = f.optJSONObject("properties");
        JSONArray coords = f.optJSONObject("geometry") == null ? null : f.getJSONObject("geometry").optJSONArray("coordinates");
        if (props == null || coords == null || coords.length() < 2)
          continue;
        String name = props.optString("name", "");
        List<String> parts = new ArrayList<>();
        for (String k : new String[] {"street", "city", "county", "state", "country"})
        {
          String v = props.optString(k, "");
          if (!v.isEmpty() && !v.equals(name) && !parts.contains(v))
            parts.add(v);
        }
        if (name.isEmpty() && !parts.isEmpty())
          name = parts.remove(0);
        if (name.isEmpty())
          continue;
        out.add(new Place(name, TextUtils.join(" · ", parts), coords.getDouble(1), coords.getDouble(0)));
      }
      return out;
    }
    catch (Exception e)
    {
      return null;
    }
    finally
    {
      if (conn != null)
        conn.disconnect();
    }
  }

  private static TextView text(Context c, String s, float size, boolean bold)
  {
    TextView t = new TextView(c);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(resolveColor(c, bold ? android.R.attr.textColorPrimary : android.R.attr.textColorSecondary));
    if (bold)
      t.setTypeface(Typeface.DEFAULT_BOLD);
    t.setPadding(0, dp(c, 4), 0, dp(c, 4));
    return t;
  }

  private static TextView button(Context c, String s, boolean strong, View.OnClickListener click)
  {
    TextView b = new TextView(c);
    b.setText(s);
    b.setTextSize(14.5f);
    b.setTypeface(Typeface.DEFAULT_BOLD);
    b.setGravity(Gravity.CENTER);
    int primary = resolveColor(c, androidx.appcompat.R.attr.colorPrimary);
    GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(dp(c, 24));
    if (strong)
    {
      bg.setColor(primary);
      b.setTextColor(resolveColor(c, com.google.android.material.R.attr.colorOnPrimary));
    }
    else
    {
      bg.setStroke(dp(c, 1), primary);
      b.setTextColor(primary);
    }
    b.setBackground(bg);
    b.setMinHeight(dp(c, 48));
    b.setOnClickListener(click);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.topMargin = dp(c, 12);
    b.setLayoutParams(lp);
    return b;
  }

  private static int resolveColor(Context c, int attr)
  {
    android.util.TypedValue v = new android.util.TypedValue();
    if (c.getTheme().resolveAttribute(attr, v, true))
      return v.resourceId != 0 ? c.getColor(v.resourceId) : v.data;
    return 0xFF1F2A44;
  }

  private static int dp(Context c, float v)
  {
    return Math.round(v * c.getResources().getDisplayMetrics().density);
  }
}

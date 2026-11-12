package app.organicmaps.eurise;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.organicmaps.sdk.Framework;
import app.organicmaps.sdk.routing.JunctionInfo;
import app.organicmaps.sdk.routing.RoutingInfo;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Meteo sul percorso nella guida (tavola MapGuida): una riga sotto la via con la cosa più importante della strada che
 * resta («Pioggia tra 40 km, verso le 15:20»); toccandola, punto per punto. Mappe non contatta servizi da sola: chiede
 * all'app Eurise (RouteWeatherContract in core/suite), che passa dal centro privacy e da Open-Meteo con i punti
 * arrotondati a circa 1 km. Spento finché non lo accendi: prima una spiegazione breve; spegnerlo non chiede nulla.
 * Senza l'app Eurise la riga non compare.
 */
public final class EuriseRouteWeather
{
  // Stessi nomi di core/suite RouteWeatherContract (Mappe non dipende dai moduli Kotlin della suite).
  private static final String[] HUBS = {"eu.eurise.hub.debug", "eu.eurise.hub"};
  private static final String STATUS = "status", ENABLE = "enable", DISABLE = "disable", ROUTE = "route";
  private static final String ON = "on", LAT = "lat", LON = "lon", TIME = "time", CODE = "code", TEMP = "temp",
      RAIN = "rain", GUSTS = "gusts";
  private static final int MAX_POINTS = 8;
  /** Ogni quanto si richiede il tempo durante la guida. */
  private static final long REFRESH_MS = 15 * 60_000L;

  private static final ExecutorService IO = Executors.newSingleThreadExecutor();

  // Stato della guida in corso (thread principale).
  @Nullable private static String sHub;
  private static boolean sChecked, sOn, sBusy;
  @Nullable private static String sRouteKey;
  private static double[] sCum;
  private static double[] sLat, sLon;
  private static long sFetched, sRouteChecked;
  // L'ultima risposta: punti chiesti e il loro tempo.
  @Nullable private static Result sResult;

  private static final class Result
  {
    double[] aheadKm;
    long[] time;
    int[] code, rain;
    float[] temp, gusts;
  }

  private EuriseRouteWeather() {}

  /** A ogni aggiornamento della guida (thread principale). [line]: la riga sotto la via. */
  public static void update(@Nullable TextView line, @NonNull RoutingInfo info, boolean pedestrian)
  {
    if (line == null)
      return;
    if (!sChecked)
    {
      sChecked = true;
      final Context app = line.getContext().getApplicationContext();
      IO.execute(() -> {
        final Bundle b = call(app, null, STATUS, null);
        line.post(() -> {
          sOn = b != null && b.getBoolean(ON);
          if (b == null)
            sHub = null;
          show(line);
        });
      });
      return;
    }
    if (sHub == null || pedestrian)
    {
      line.setVisibility(View.GONE);
      return;
    }
    show(line);
    if (!sOn || sBusy)
      return;
    final long now = System.currentTimeMillis();
    // La strada si rilegge al più ogni minuto (un ricalcolo del percorso la cambia).
    if (now - sRouteChecked > 60_000L)
    {
      sRouteChecked = now;
      loadRoute();
    }
    if (sCum == null || sCum.length < 2)
      return;
    if (sResult != null && now - sFetched < REFRESH_MS)
      return;
    fetch(line.getContext().getApplicationContext(), line, info, now);
  }

  /** La strada del percorso (punti al massimo a 1 km): si rilegge solo quando il percorso cambia. */
  private static void loadRoute()
  {
    final JunctionInfo[] j = Framework.nativeGetRouteJunctionPoints(1000);
    if (j == null || j.length < 2)
    {
      sCum = null;
      return;
    }
    final String key = j.length + ":" + j[j.length - 1].mLat + "," + j[j.length - 1].mLon;
    if (key.equals(sRouteKey))
      return;
    sRouteKey = key;
    sLat = new double[j.length];
    sLon = new double[j.length];
    for (int i = 0; i < j.length; i++)
    {
      sLat[i] = j[i].mLat;
      sLon[i] = j[i].mLon;
    }
    sCum = RouteSampler.cumulative(sLat, sLon);
    sResult = null;
    sFetched = 0;
  }

  private static void fetch(@NonNull Context app, @NonNull TextView line, @NonNull RoutingInfo info, long now)
  {
    final double total = sCum[sCum.length - 1];
    final double traveled = total * Math.max(0, Math.min(100, info.completionPercent)) / 100.0;
    final int[] idx = RouteSampler.pick(sCum, traveled, MAX_POINTS);
    final long[] time = RouteSampler.times(sCum, idx, traveled, now / 1000, Math.max(0, info.totalTimeInSeconds));
    final double[] lat = new double[idx.length], lon = new double[idx.length], ahead = new double[idx.length];
    for (int i = 0; i < idx.length; i++)
    {
      lat[i] = sLat[idx[i]];
      lon[i] = sLon[idx[i]];
      ahead[i] = Math.max(0, sCum[idx[i]] - traveled) / 1000.0;
    }
    sBusy = true;
    sFetched = now;
    IO.execute(() -> {
      final Bundle in = new Bundle();
      in.putDoubleArray(LAT, lat);
      in.putDoubleArray(LON, lon);
      in.putLongArray(TIME, time);
      final Bundle out = call(app, sHub, ROUTE, in);
      line.post(() -> {
        sBusy = false;
        if (out == null)
          return;
        sOn = out.getBoolean(ON);
        final int[] code = out.getIntArray(CODE);
        if (code != null && code.length == idx.length)
        {
          final Result r = new Result();
          r.aheadKm = ahead;
          r.time = time;
          r.code = code;
          r.rain = out.getIntArray(RAIN);
          r.temp = out.getFloatArray(TEMP);
          r.gusts = out.getFloatArray(GUSTS);
          if (r.rain != null && r.temp != null && r.gusts != null)
            sResult = r;
        }
        show(line);
      });
    });
  }

  /** La riga: spento → invito ad accenderlo; acceso → la cosa più importante (o «niente da segnalare»). */
  private static void show(@NonNull TextView line)
  {
    if (sHub == null)
    {
      line.setVisibility(View.GONE);
      return;
    }
    final Context c = line.getContext();
    style(line);
    line.setVisibility(View.VISIBLE);
    if (!sOn)
    {
      line.setText(c.getString(R.string.eurise_rw_off));
      line.setOnClickListener(v -> explain(line));
      return;
    }
    final Result r = sResult;
    if (r == null)
    {
      line.setText(c.getString(R.string.eurise_rw_loading));
      line.setOnClickListener(null);
      return;
    }
    line.setText(summary(c, r));
    line.setOnClickListener(v -> details(line, r));
  }

  @NonNull
  static String summary(@NonNull Context c, @NonNull Result r)
  {
    final RouteSampler.Finding f = RouteSampler.worst(r.code, r.rain, r.gusts);
    final int last = r.code.length - 1;
    if (f.kind == RouteSampler.Kind.CLEAR)
    {
      final float t = r.temp[last];
      return Float.isNaN(t) ? c.getString(R.string.eurise_rw_clear)
                            : c.getString(R.string.eurise_rw_clear_temp, Math.round(t));
    }
    final String what = c.getString(label(f.kind));
    final String hour = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(r.time[f.index] * 1000L));
    if (r.aheadKm[f.index] < 3)
      return c.getString(R.string.eurise_rw_here, glyph(f.kind), what);
    if (f.index == last)
      return c.getString(R.string.eurise_rw_arrival, glyph(f.kind), what, hour);
    return c.getString(R.string.eurise_rw_ahead, glyph(f.kind), what, Math.round(r.aheadKm[f.index]), hour);
  }

  private static int label(@NonNull RouteSampler.Kind k)
  {
    switch (k)
    {
    case STORM: return R.string.eurise_rw_storm;
    case ICE: return R.string.eurise_rw_ice;
    case SNOW: return R.string.eurise_rw_snow;
    case FOG: return R.string.eurise_rw_fog;
    case RAIN: return R.string.eurise_rw_rain;
    case WIND: return R.string.eurise_rw_wind;
    default: return R.string.eurise_rw_clear;
    }
  }

  @NonNull
  private static String glyph(@NonNull RouteSampler.Kind k)
  {
    switch (k)
    {
    case STORM: return "⛈";
    case ICE: return "🧊";
    case SNOW: return "❄";
    case FOG: return "🌫";
    case RAIN: return "🌧";
    case WIND: return "💨";
    default: return "☀";
    }
  }

  /** Il glifo del tempo per un codice WMO (per l'elenco dei punti). */
  @NonNull
  private static String sky(int code)
  {
    final RouteSampler.Kind k = RouteSampler.kind(code, 100, 0);
    if (k != RouteSampler.Kind.CLEAR && k != RouteSampler.Kind.RAIN)
      return glyph(k);
    if ((code >= 51 && code <= 65) || (code >= 80 && code <= 82))
      return "🌧";
    if (code == 0 || code == 1)
      return "☀";
    if (code == 2)
      return "⛅";
    return "☁";
  }

  /** Punto per punto: ora, distanza, cielo, gradi, pioggia. «Spegni» senza conferme. */
  private static void details(@NonNull TextView line, @NonNull Result r)
  {
    final Context c = line.getContext();
    final DateFormat df = DateFormat.getTimeInstance(DateFormat.SHORT);
    final StringBuilder sb = new StringBuilder();
    for (int i = 0; i < r.code.length; i++)
    {
      final String where = i == r.code.length - 1 ? c.getString(R.string.eurise_rw_dest)
                         : r.aheadKm[i] < 3 ? c.getString(R.string.eurise_rw_now)
                         : c.getString(R.string.eurise_rw_km, Math.round(r.aheadKm[i]));
      sb.append(df.format(new Date(r.time[i] * 1000L))).append("  ·  ").append(where).append("  ·  ").append(sky(r.code[i]));
      if (!Float.isNaN(r.temp[i]))
        sb.append(' ').append(String.format(Locale.getDefault(), "%d°", Math.round(r.temp[i])));
      if (r.rain[i] > 0)
        sb.append("  ·  ").append(c.getString(R.string.eurise_rw_rain_pct, r.rain[i]));
      if (r.gusts[i] >= 50)
        sb.append("  ·  ").append(c.getString(R.string.eurise_rw_gusts, Math.round(r.gusts[i])));
      sb.append('\n');
    }
    sb.append('\n').append(c.getString(R.string.eurise_rw_source));
    new MaterialAlertDialogBuilder(c)
        .setTitle(R.string.eurise_rw_title)
        .setMessage(sb.toString())
        .setPositiveButton(R.string.eurise_rw_close, null)
        .setNeutralButton(R.string.eurise_rw_turn_off, (d, w) -> toggle(line, false))
        .show();
  }

  /** Prima di accenderlo: cosa esce, dove va, chi lo vede, come si torna indietro. */
  private static void explain(@NonNull TextView line)
  {
    new MaterialAlertDialogBuilder(line.getContext())
        .setTitle(R.string.eurise_rw_title)
        .setMessage(R.string.eurise_rw_explain)
        .setPositiveButton(R.string.eurise_rw_turn_on, (d, w) -> toggle(line, true))
        .setNegativeButton(R.string.eurise_rw_not_now, null)
        .show();
  }

  private static void toggle(@NonNull TextView line, boolean on)
  {
    final Context app = line.getContext().getApplicationContext();
    sOn = on;
    sResult = null;
    sFetched = 0;
    show(line);
    IO.execute(() -> {
      final Bundle b = call(app, sHub, on ? ENABLE : DISABLE, null);
      line.post(() -> {
        if (b != null)
          sOn = b.getBoolean(ON);
        show(line);
      });
    });
  }

  /** La chiamata all'app Eurise (versione di prova prima); null se manca o non risponde. Fuori dal thread principale. */
  @Nullable
  private static Bundle call(@NonNull Context app, @Nullable String hub, @NonNull String method, @Nullable Bundle extras)
  {
    final String[] hubs = hub != null ? new String[] {hub} : HUBS;
    for (String h : hubs)
    {
      try
      {
        final Bundle b = app.getContentResolver().call(Uri.parse("content://" + h + ".routeweather"), method, null, extras);
        if (b != null)
        {
          sHub = h;
          return b;
        }
      }
      catch (Exception ignored)
      {
        // App Eurise assente, di un'altra firma o vecchia (senza il fornitore): si prova l'altra.
      }
    }
    return null;
  }

  /** La pillola satinata della suite: Perla di giorno, Notte di sera. */
  private static void style(@NonNull TextView line)
  {
    if (line.getTag() != null)
      return;
    line.setTag("styled");
    final Context c = line.getContext();
    final boolean night = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                          == Configuration.UI_MODE_NIGHT_YES;
    final float d = c.getResources().getDisplayMetrics().density;
    final GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(18 * d);
    bg.setColor(night ? 0xE61B2230 : 0xEBF7F5F0);
    bg.setStroke(Math.round(d), night ? 0x337FB2E5 : 0x221F2A44);
    line.setBackground(bg);
    line.setTextColor(night ? 0xFFE6EAF2 : 0xFF1F2A44);
    line.setTextSize(14);
    line.setPadding(Math.round(14 * d), Math.round(8 * d), Math.round(14 * d), Math.round(8 * d));
    line.setElevation(3 * d);
  }
}

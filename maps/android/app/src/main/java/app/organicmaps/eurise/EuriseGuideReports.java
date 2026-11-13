package app.organicmaps.eurise;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import app.organicmaps.MwmApplication;
import app.organicmaps.R;
import app.organicmaps.eurise.car.EuriseCarInfo;
import app.organicmaps.sdk.routing.RoutingController;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Segnala in guida (tavole MapSegnalaGuida e MapAutoSegnala): sei segnalazioni a un tocco, senza conferma, con il punto e
 * la direzione presi al tocco e «Annulla» per 5 secondi.
 *
 * <ul>
 *   <li>Autovelox, limite errato e strada chiusa sono errori della mappa: restano qui finché il viaggio finisce, poi
 *   compare l'elenco per un'occhiata e partono verso OpenStreetMap come note anonime (punto, tipo e direzione).</li>
 *   <li>Lavori, coda e pericolo durano poco: restano sul telefono e scadono dopo due ore. Servono a chi ripassa di lì
 *   (avviso a 500 m, nella stessa direzione). Per condividerle con altri servirebbe un servizio comune: da decidere.</li>
 * </ul>
 *
 * Tutto in un file di preferenze dell'app; nulla esce prima del tocco su «Manda» nell'elenco di fine viaggio.
 */
public final class EuriseGuideReports
{
  /** Le sei voci, nell'ordine della tavola: prima le tre per OpenStreetMap, poi le tre brevi. */
  public enum Kind
  {
    CAMERA(true, R.string.eurise_guide_camera, R.drawable.eurise_rep_camera, "Autovelox fisso (fixed speed camera)"),
    LIMIT(true, R.string.eurise_guide_limit, R.drawable.eurise_rep_limit, "Limite di velocità sbagliato (wrong speed limit)"),
    CLOSED(true, R.string.eurise_guide_closed, R.drawable.eurise_rep_closed, "Strada chiusa (road closed)"),
    WORKS(false, R.string.eurise_guide_works, R.drawable.eurise_rep_works, null),
    QUEUE(false, R.string.eurise_guide_queue, R.drawable.eurise_rep_queue, null),
    DANGER(false, R.string.eurise_guide_danger, R.drawable.eurise_rep_danger, null);

    public final boolean osm;
    @StringRes public final int label;
    @DrawableRes public final int icon;
    @Nullable final String osmText;

    Kind(boolean osm, @StringRes int label, @DrawableRes int icon, @Nullable String osmText)
    {
      this.osm = osm;
      this.label = label;
      this.icon = icon;
      this.osmText = osmText;
    }

    /** Dal nome («CAMERA») o dalla parola detta ad Alfred («autovelox»); null se non è una segnalazione. */
    @Nullable
    public static Kind parse(@Nullable String s)
    {
      if (s == null)
        return null;
      final String t = s.trim().toUpperCase(Locale.ROOT);
      for (Kind k : values())
        if (k.name().equals(t))
          return k;
      return null;
    }

    /**
     * Una frase breve detta in auto («autovelox», «segnala una coda», «c'è un pericolo», «limite sbagliato»), come la
     * capisce Alfred sul telefono (feature/assistant Grammar). Null se non è una segnalazione.
     */
    @Nullable
    public static Kind fromWords(@Nullable String said)
    {
      if (said == null)
        return null;
      final String t = said.trim().toLowerCase(Locale.ITALIAN).replaceAll("[^\\p{L}\\s']", " ")
                           .replaceAll("^(?:ehi\\s+|ok\\s+)?(?:alfred\\s+)?", "")
                           .replaceAll("^(?:segnala(?:re)?|segnalo|c'è|ce|c'e|attenzione)\\s+", "")
                           .replaceAll("^(?:una\\s+|uno\\s+|un'\\s*|un\\s+)", "").replaceAll("\\s+", " ").trim();
      if (t.isEmpty() || t.split(" ").length > 4)
        return null;
      if (t.matches("(?:auto)?velox|autovelox(?: fisso)?|speed camera|tutor"))
        return CAMERA;
      if (t.matches("limite (?:sbagliato|errato)|limite di velocità (?:sbagliato|errato)|wrong (?:speed )?limit"))
        return LIMIT;
      if (t.matches("strada chiusa|road closed"))
        return CLOSED;
      if (t.matches("lavori(?: in corso)?|cantiere|roadworks"))
        return WORKS;
      if (t.matches("coda|traffico|incolonnamento|queue|traffic jam"))
        return QUEUE;
      if (t.matches("pericolo|ostacolo|danger|hazard"))
        return DANGER;
      return null;
    }
  }

  /** Una segnalazione: dove, in che direzione (gradi, -1 se ferma), su che strada, con che limite in mappa. */
  public static final class Report
  {
    public final long id;
    @NonNull public final Kind kind;
    public final double lat;
    public final double lon;
    public final float bearing;
    @NonNull public final String road;
    public final int limit;
    public final long time;

    Report(long id, @NonNull Kind kind, double lat, double lon, float bearing, @NonNull String road, int limit, long time)
    {
      this.id = id;
      this.kind = kind;
      this.lat = lat;
      this.lon = lon;
      this.bearing = bearing;
      this.road = road;
      this.limit = limit;
      this.time = time;
    }
  }

  /** Le segnalazioni brevi scadono dopo due ore. */
  public static final long SHORT_LIFE_MS = 2 * 60 * 60 * 1000L;
  /** L'avviso a chi ripassa: entro 500 m, davanti, nella stessa direzione (±60°). */
  private static final float WARN_DISTANCE_M = 500f;
  private static final float WARN_MIN_DISTANCE_M = 40f;
  private static final float WARN_ANGLE = 60f;
  /** Il nome del servizio nel centro privacy (core/suite PrivacyService.MapReports). */
  public static final String PRIVACY_SERVICE = "MapReports";

  private static final String PREFS = "eurise_guide_reports";
  private static final String KEY_LIST = "list";
  private static final String KEY_DUE = "review_due";
  private static final Set<Long> sWarned = new HashSet<>();

  private EuriseGuideReports() {}

  private static SharedPreferences prefs(@NonNull Context c)
  {
    return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  // --- Archivio ---

  @NonNull
  private static synchronized List<Report> read(@NonNull Context c)
  {
    final List<Report> out = new ArrayList<>();
    try
    {
      final JSONArray a = new JSONArray(prefs(c).getString(KEY_LIST, "[]"));
      for (int i = 0; i < a.length(); i++)
      {
        final JSONObject o = a.getJSONObject(i);
        final Kind k = Kind.parse(o.optString("kind"));
        if (k == null)
          continue;
        out.add(new Report(o.getLong("id"), k, o.getDouble("lat"), o.getDouble("lon"), (float) o.optDouble("bearing", -1),
                           o.optString("road", ""), o.optInt("limit", -1), o.getLong("time")));
      }
    }
    catch (Exception ignored)
    {
      // File rovinato: si riparte da capo, sono segnalazioni non ancora mandate.
    }
    return out;
  }

  private static synchronized void write(@NonNull Context c, @NonNull List<Report> list)
  {
    final JSONArray a = new JSONArray();
    try
    {
      for (Report r : list)
        a.put(new JSONObject()
                  .put("id", r.id).put("kind", r.kind.name()).put("lat", r.lat).put("lon", r.lon)
                  .put("bearing", (double) r.bearing).put("road", r.road).put("limit", r.limit).put("time", r.time));
    }
    catch (Exception ignored)
    {
      return;
    }
    prefs(c).edit().putString(KEY_LIST, a.toString()).apply();
  }

  /** Toglie le brevi scadute; le altre restano finché non si mandano o si scartano. */
  @NonNull
  private static List<Report> alive(@NonNull Context c)
  {
    final long now = System.currentTimeMillis();
    final List<Report> all = read(c);
    final List<Report> keep = new ArrayList<>();
    for (Report r : all)
      if (r.kind.osm || now - r.time < SHORT_LIFE_MS)
        keep.add(r);
    if (keep.size() != all.size())
      write(c, keep);
    return keep;
  }

  /** Dove si è adesso: l'ultima posizione della guida (anche in Android Auto) o quella salvata da CoMaps. */
  @Nullable
  public static Location here(@NonNull Context c)
  {
    final Location l = EuriseCarInfo.lastLocation();
    if (l != null && System.currentTimeMillis() - l.getTime() < 60_000)
      return l;
    final Location saved = MwmApplication.from(c).getLocationHelper().getSavedLocation();
    return saved != null ? saved : l;
  }

  /** Segna qui e ora. Null se la posizione non c'è ancora. */
  @Nullable
  public static Report add(@NonNull Context c, @NonNull Kind kind)
  {
    final Location l = here(c);
    if (l == null)
      return null;
    final float bearing = l.hasBearing() && l.hasSpeed() && l.getSpeed() >= 2f ? l.getBearing() : -1f;
    final long now = System.currentTimeMillis();
    final Report r = new Report(now, kind, l.getLatitude(), l.getLongitude(), bearing, EuriseCarInfo.roadLabel(),
                                kind == Kind.LIMIT ? EuriseSpeed.lastLimit() : -1, now);
    final List<Report> list = alive(c);
    list.add(r);
    write(c, list);
    sWarned.add(r.id);
    return r;
  }

  /** «Annulla»: la segnalazione sparisce come se non ci fosse mai stata. */
  public static void remove(@NonNull Context c, long id)
  {
    final List<Report> list = read(c);
    final List<Report> keep = new ArrayList<>();
    for (Report r : list)
      if (r.id != id)
        keep.add(r);
    write(c, keep);
  }

  /** Quelle che aspettano l'occhiata per OpenStreetMap. */
  @NonNull
  public static List<Report> pending(@NonNull Context c)
  {
    final List<Report> out = new ArrayList<>();
    for (Report r : alive(c))
      if (r.kind.osm)
        out.add(r);
    return out;
  }

  // --- Fine del viaggio ---

  /**
   * Il viaggio è finito (arrivo o «Fine», sul telefono o in auto). Se ci sono segnalazioni per OpenStreetMap, l'elenco si
   * apre subito sul telefono quando si può, altrimenti alla prossima apertura della mappa.
   */
  public static void tripEnded(@NonNull Context c)
  {
    if (pending(c).isEmpty())
      return;
    prefs(c).edit().putBoolean(KEY_DUE, true).apply();
    // Solo con la mappa davanti: arrivando con il telefono in tasca (o in auto) l'elenco aspetta la prossima apertura.
    if (c instanceof androidx.lifecycle.LifecycleOwner && c instanceof Activity && !((Activity) c).isFinishing()
        && ((androidx.lifecycle.LifecycleOwner) c).getLifecycle().getCurrentState()
               .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
      maybeReview((Activity) c);
  }

  /** Alla ripresa della mappa: l'elenco di fine viaggio, se è rimasto in sospeso e non si sta guidando. */
  public static void maybeReview(@NonNull Activity a)
  {
    if (!prefs(a).getBoolean(KEY_DUE, false))
      return;
    final RoutingController rc = RoutingController.get();
    if (rc.isNavigating() || rc.isPlanning())
      return;
    if (pending(a).isEmpty())
    {
      reviewed(a);
      return;
    }
    a.startActivity(new Intent(a, EuriseReportReviewActivity.class));
  }

  /** L'elenco è stato visto (mandato, scartato o «Più tardi»): non si ripropone fino al prossimo viaggio. */
  static void reviewed(@NonNull Context c)
  {
    prefs(c).edit().putBoolean(KEY_DUE, false).apply();
  }

  // --- Avviso a chi ripassa ---

  /**
   * A ogni posizione in guida: la frase dell'avviso per una segnalazione propria poco più avanti, nella stessa
   * direzione («Coda segnalata da te 25 min fa, tra 300 m»), una volta sola; altrimenti null.
   */
  @Nullable
  public static String warning(@NonNull Context c, @Nullable Location l)
  {
    if (l == null || !l.hasBearing() || !l.hasSpeed() || l.getSpeed() < 2f)
      return null;
    // Quasi sempre non c'è nulla: niente lettura del JSON a ogni posizione.
    if (prefs(c).getString(KEY_LIST, "[]").length() <= 2)
      return null;
    final float[] d = new float[2];
    Report best = null;
    float bestDistance = Float.MAX_VALUE;
    for (Report r : alive(c))
    {
      if (sWarned.contains(r.id))
        continue;
      Location.distanceBetween(l.getLatitude(), l.getLongitude(), r.lat, r.lon, d);
      if (d[0] > WARN_DISTANCE_M || d[0] < WARN_MIN_DISTANCE_M || d[0] >= bestDistance)
        continue;
      // Davanti (verso del punto rispetto alla marcia) e, se si sa, nella stessa direzione di chi l'ha segnata.
      if (angle(d[1], l.getBearing()) > WARN_ANGLE)
        continue;
      if (r.bearing >= 0 && angle(r.bearing, l.getBearing()) > WARN_ANGLE)
        continue;
      best = r;
      bestDistance = d[0];
    }
    if (best == null)
      return null;
    sWarned.add(best.id);
    final int minutes = (int) Math.max(1, (System.currentTimeMillis() - best.time) / 60_000);
    final int meters = (int) (Math.round(bestDistance / 50f) * 50);
    final String ago = minutes < 120 ? c.getString(R.string.eurise_guide_ago_min, minutes)
                                     : c.getString(R.string.eurise_guide_ago_days);
    return c.getString(R.string.eurise_guide_warning, c.getString(best.kind.label), ago, Math.max(50, meters));
  }

  /** Differenza tra due direzioni, da 0 a 180 gradi. */
  static float angle(float a, float b)
  {
    float x = Math.abs(((a - b) % 360 + 360) % 360);
    return x > 180 ? 360 - x : x;
  }

  // --- Testi ---

  /** «segnato qui, sulla SS36» (o solo «segnato qui»). */
  @NonNull
  public static String placed(@NonNull Context c, @NonNull Report r)
  {
    return TextUtils.isEmpty(r.road) ? c.getString(R.string.eurise_guide_placed)
                                     : c.getString(R.string.eurise_guide_placed_on, r.road);
  }

  /** La direzione di marcia a parole: «nord-est»; null se non si sa. */
  @Nullable
  public static String compass(@NonNull Context c, float bearing)
  {
    if (bearing < 0)
      return null;
    final String[] names = c.getResources().getStringArray(R.array.eurise_guide_compass);
    return names[Math.round(bearing / 45f) % 8];
  }

  /** Il testo della nota per OpenStreetMap: tipo, limite in mappa, direzione. Niente di chi segnala. */
  @NonNull
  static String noteText(@NonNull Report r)
  {
    final StringBuilder b = new StringBuilder(r.kind.osmText == null ? r.kind.name() : r.kind.osmText);
    if (r.kind == Kind.LIMIT && r.limit > 0)
      b.append(": in mappa ").append(r.limit).append(" (map says ").append(r.limit).append(')');
    if (r.bearing >= 0)
      b.append("\nDirezione di marcia / heading: ").append(Math.round(r.bearing)).append('°');
    if (!TextUtils.isEmpty(r.road))
      b.append("\nStrada / road: ").append(r.road);
    b.append("\nSegnalato in guida / reported while driving\n#EuriseMappe");
    return b.toString();
  }

  // --- Centro privacy dell'app Eurise ---

  private static final String[] HUBS = {"eu.eurise.hub.debug", "eu.eurise.hub"};

  /**
   * Chiede al centro privacy dell'app Eurise («status» o «log») per le note a OpenStreetMap. Null se l'app Eurise manca:
   * allora decide solo il tocco su «Manda». Mai dal thread principale.
   */
  @Nullable
  static Boolean hub(@NonNull Context c, @NonNull String method)
  {
    for (String h : HUBS)
    {
      try
      {
        final Bundle b = c.getContentResolver().call(android.net.Uri.parse("content://" + h + ".privacy"), method,
                                                     PRIVACY_SERVICE, null);
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
}

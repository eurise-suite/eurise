package app.organicmaps.eurise;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import app.organicmaps.R;
import app.organicmaps.sdk.routing.RoutingInfo;
import app.organicmaps.sdk.routing.roadshield.RoadShield;
import app.organicmaps.sdk.routing.roadshield.RoadShieldInfo;
import app.organicmaps.sdk.routing.roadshield.RoadShieldType;
import app.organicmaps.sdk.util.Distance;
import app.organicmaps.util.Utils;
import java.text.DateFormat;
import java.util.Date;
import java.util.regex.Pattern;

/**
 * La guida di Eurise Mappe (tavola MapGuida, richiesta del proprietario dell'11 novembre):
 * il cartello dell'indicazione prende il colore del tipo di strada con le tinte della suite, i metri alla svolta
 * scendono a passi fini (10 m sotto i 200 m, poi 50 m fino a 500 e 100 m fino al chilometro), «Manda l'arrivo».
 * Tutto sul telefono: niente esce se non lo mandi tu.
 */
public final class EuriseGuide
{
  /** Tipo di strada dell'indicazione, dal riferimento (A4, SS36, SP2…) o dal colore del cartello di OSM. */
  public enum Road { MOTORWAY, MAIN, PROVINCIAL, LOCAL }

  // Tinte della suite (core/designsystem CalendarTint): Salvia, Blu notte, Petrolio; per le comunali il blu della suite.
  private static final int SALVIA = 0xFF4F6B5A;
  private static final int BLU_NOTTE = 0xFF3E5C8A;
  private static final int PETROLIO = 0xFF2E6E73;
  private static final int SUITE = 0xFF1F2A44;

  private static final Pattern MOTORWAY_REF = Pattern.compile("^(A|RA|T|M|BAB)\\s?\\d.*", Pattern.CASE_INSENSITIVE);
  private static final Pattern MAIN_REF = Pattern.compile("^(SS|SR|SGC|E|N|B|DN|NSA)\\s?\\d.*", Pattern.CASE_INSENSITIVE);
  private static final Pattern PROVINCIAL_REF = Pattern.compile("^(SP|SC|D|L|K|CR)\\s?\\d.*", Pattern.CASE_INSENSITIVE);

  private EuriseGuide() {}

  @NonNull
  public static Road road(@Nullable String ref, @Nullable RoadShieldInfo shields)
  {
    final String r = ref == null ? "" : ref.trim();
    if (!r.isEmpty())
    {
      if (MOTORWAY_REF.matcher(r).matches())
        return Road.MOTORWAY;
      if (MAIN_REF.matcher(r).matches())
        return Road.MAIN;
      if (PROVINCIAL_REF.matcher(r).matches())
        return Road.PROVINCIAL;
    }
    if (shields != null && shields.hasTargetRoadShields())
    {
      for (RoadShield s : shields.targetRoadShields)
      {
        if (s.type == RoadShieldType.GenericGreen || s.type == RoadShieldType.USInterstate)
          return Road.MOTORWAY;
        if (s.type == RoadShieldType.GenericBlue || s.type == RoadShieldType.USHighway || s.type == RoadShieldType.UKHighway)
          return Road.MAIN;
      }
      return Road.PROVINCIAL;
    }
    return Road.LOCAL;
  }

  public static int color(@NonNull Road road)
  {
    switch (road)
    {
    case MOTORWAY: return SALVIA;
    case MAIN: return BLU_NOTTE;
    case PROVINCIAL: return PETROLIO;
    default: return SUITE;
    }
  }

  /** Colora il cartello della svolta e la striscia della via con il tipo della strada dove si gira. */
  public static void tint(@Nullable View turnFrame, @Nullable View streetFrame, @NonNull RoutingInfo info, boolean pedestrian)
  {
    final ColorStateList c = pedestrian ? ColorStateList.valueOf(SUITE)
                                        : ColorStateList.valueOf(color(road(info.nextRef, info.nextStreetRoadShields)));
    if (turnFrame != null)
      ViewCompat.setBackgroundTintList(turnFrame, c);
    if (streetFrame != null)
      ViewCompat.setBackgroundTintList(streetFrame, c);
  }

  /** Metri arrotondati per la guida: [-1] se non si arrotonda (miglia, piedi, oltre il chilometro). */
  public static int roundMeters(double meters)
  {
    if (meters < 0 || meters >= 1000)
      return -1;
    final int step = meters < 200 ? 10 : (meters < 500 ? 50 : 100);
    return (int) (Math.round(meters / step) * step);
  }

  /** La distanza alla svolta, aggiornata a ogni posizione con i passi fini sotto il chilometro. */
  @NonNull
  public static CharSequence turnDistance(@NonNull Context context, @NonNull Distance d)
  {
    if (d.isValid() && d.mUnits == Distance.Units.Meters)
    {
      final int m = roundMeters(d.mDistance);
      if (m >= 0)
        return Utils.formatDistance(context, new Distance(m, String.valueOf(m), (byte) Distance.Units.Meters.ordinal()));
    }
    return Utils.formatDistance(context, d);
  }

  /** «Manda l'arrivo»: l'ora d'arrivo e quanto manca, da mandare con l'app che vuoi (Messaggi, Chat…). */
  public static void shareArrival(@NonNull Activity activity, @Nullable RoutingInfo info)
  {
    if (info == null || info.totalTimeInSeconds <= 0)
      return;
    final Date eta = new Date(System.currentTimeMillis() + info.totalTimeInSeconds * 1000L);
    final String time = DateFormat.getTimeInstance(DateFormat.SHORT).format(eta);
    final int minutes = Math.max(1, Math.round(info.totalTimeInSeconds / 60f));
    final String dist = info.distToTarget.isValid() ? info.distToTarget.toString(activity) : "";
    final String text = TextUtils.isEmpty(dist)
                        ? activity.getString(R.string.eurise_arrival_text_short, time, minutes)
                        : activity.getString(R.string.eurise_arrival_text, time, minutes, dist);
    final Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
    try
    {
      activity.startActivity(Intent.createChooser(send, activity.getString(R.string.eurise_arrival_share)));
    }
    catch (Exception ignored)
    {
      // Nessuna app per mandarlo: non succede nulla.
    }
  }
}

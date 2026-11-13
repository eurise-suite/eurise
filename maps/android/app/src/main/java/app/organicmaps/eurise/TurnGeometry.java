package app.organicmaps.eurise;

import android.location.Location;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.eurise.car.EuriseCarInfo;
import app.organicmaps.sdk.routing.JunctionInfo;
import app.organicmaps.sdk.routing.RouteGeometry;
import app.organicmaps.sdk.routing.RoutingController;

/**
 * Di quanto gira la strada in una manovra, dalla forma del percorso: serve alle scene perché ciò che si vede sia quello
 * vero (prova del 13 novembre: tornando indietro sulla via d'entrata, la scena della rotonda mostrava l'uscita prima;
 * richiesta del proprietario: lo stesso per uscite, immissioni e incroci). Per uscite e svolte basta la forma della
 * strada nei 40 m prima e dopo il punto della manovra ([simple]); per le rotonde serve la via da cui si entra, sotto.
 *
 * <p>L'uscita è il punto del percorso a [distToTurn] metri da qui; la direzione d'uscita è quella dei 25 m dopo. La
 * direzione d'entrata si prende la prima volta che la scena compare (a qualche centinaio di metri, sulla via che porta
 * alla rotonda) e si tiene per quella manovra: dentro l'anello la direzione dell'auto non dice più niente.
 */
final class TurnGeometry
{
  private static final double OUT_M = 25, IN_M = 30, MIN_FIRST_SIGHT_M = 80;

  private static int sRevision = -1;
  private static int sNear = 0;
  private static double sExitLat = Double.NaN, sExitLon = Double.NaN;
  private static double sIn = Double.NaN;

  private TurnGeometry() {}

  /**
   * Uscita, immissione o svolta: la direzione nei [SIDE_M] metri prima del punto della manovra contro quella nei
   * [SIDE_M] metri dopo. Gradi tra −180 e 180 (+ a destra), o NaN se non si sa.
   */
  static double simple(double distToTurn)
  {
    final Location loc = EuriseCarInfo.last();
    final JunctionInfo[] p = RoutingController.get().getRouteGeometry().mJunctions;
    if (loc == null || p.length < 2 || distToTurn < 0)
      return Double.NaN;
    final int near = nearest(p, loc, RoutingController.get().getRouteGeometry().mRevision);
    final double[] turn = along(p, near, loc.getLatitude(), loc.getLongitude(), distToTurn);
    final double[] before = along(p, near, loc.getLatitude(), loc.getLongitude(), Math.max(0, distToTurn - SIDE_M));
    if (turn == null || before == null)
      return Double.NaN;
    final double[] after = along(p, (int) turn[2], turn[0], turn[1], SIDE_M);
    if (after == null || meters(before[0], before[1], turn[0], turn[1]) < 5)
      return Double.NaN;
    return normalize(bearing(turn[0], turn[1], after[0], after[1]) - bearing(before[0], before[1], turn[0], turn[1]));
  }

  private static final double SIDE_M = 40;

  private static double normalize(double turn)
  {
    while (turn > 180)
      turn -= 360;
    while (turn <= -180)
      turn += 360;
    return turn;
  }

  /** Il tratto del percorso più vicino all'auto (si cerca in avanti dall'ultimo: il percorso non torna indietro). */
  private static int nearest(@NonNull JunctionInfo[] p, @NonNull Location loc, int revision)
  {
    if (revision != sRevision)
    {
      sRevision = revision;
      sNear = 0;
      sExitLat = sExitLon = sIn = Double.NaN;
    }
    final int from = Math.max(0, sNear - 20), to = Math.min(p.length - 1, sNear == 0 ? p.length - 1 : sNear + 3000);
    int near = from;
    double best = Double.MAX_VALUE;
    for (int i = from; i < to; i++)
    {
      final double d = segmentDistance(loc.getLatitude(), loc.getLongitude(), p[i], p[i + 1]);
      if (d < best)
      {
        best = d;
        near = i;
      }
    }
    sNear = near;
    return near;
  }

  /** Rotonda: gradi tra −180 e 180 (+ a destra), o NaN se non si sa. Thread principale. */
  static double roundabout(double distToTurn)
  {
    final Location loc = EuriseCarInfo.last();
    final RouteGeometry g = RoutingController.get().getRouteGeometry();
    final JunctionInfo[] p = g.mJunctions;
    if (loc == null || p.length < 2 || distToTurn < 0)
      return Double.NaN;
    final int near = nearest(p, loc, g.mRevision);

    final double[] exit = along(p, near, loc.getLatitude(), loc.getLongitude(), distToTurn);
    if (exit == null)
      return Double.NaN;
    final double[] after = along(p, (int) exit[2], exit[0], exit[1], OUT_M);
    if (after == null)
      return Double.NaN;
    final double out = bearing(exit[0], exit[1], after[0], after[1]);

    // Una manovra nuova: la direzione d'entrata si prende ora, se si è ancora abbastanza lontani dall'anello.
    if (Double.isNaN(sExitLat) || meters(exit[0], exit[1], sExitLat, sExitLon) > 40)
    {
      sExitLat = exit[0];
      sExitLon = exit[1];
      sIn = Double.NaN;
      if (distToTurn >= MIN_FIRST_SIGHT_M)
      {
        final double[] ahead = along(p, near, loc.getLatitude(), loc.getLongitude(), IN_M);
        if (ahead != null)
          sIn = bearing(loc.getLatitude(), loc.getLongitude(), ahead[0], ahead[1]);
      }
    }
    if (Double.isNaN(sIn))
      return Double.NaN;
    return normalize(out - sIn);
  }

  /** Il punto a [dist] metri lungo il percorso partendo da (lat, lon) sul tratto [i]: {lat, lon, tratto}. */
  @Nullable
  static double[] along(@NonNull JunctionInfo[] p, int i, double lat, double lon, double dist)
  {
    double left = dist, aLat = lat, aLon = lon;
    for (int k = i; k < p.length - 1; k++)
    {
      final JunctionInfo b = p[k + 1];
      final double seg = meters(aLat, aLon, b.mLat, b.mLon);
      if (seg >= left)
      {
        final double f = seg <= 0 ? 0 : left / seg;
        return new double[] {aLat + (b.mLat - aLat) * f, aLon + (b.mLon - aLon) * f, k};
      }
      left -= seg;
      aLat = b.mLat;
      aLon = b.mLon;
    }
    return null;
  }

  static double meters(double lat1, double lon1, double lat2, double lon2)
  {
    final double k = Math.cos(Math.toRadians((lat1 + lat2) / 2));
    final double dx = Math.toRadians(lon2 - lon1) * k, dy = Math.toRadians(lat2 - lat1);
    return 6371000.0 * Math.sqrt(dx * dx + dy * dy);
  }

  /** Gradi da nord in senso orario. */
  static double bearing(double lat1, double lon1, double lat2, double lon2)
  {
    final double k = Math.cos(Math.toRadians((lat1 + lat2) / 2));
    return Math.toDegrees(Math.atan2((lon2 - lon1) * k, lat2 - lat1));
  }

  private static double segmentDistance(double lat, double lon, @NonNull JunctionInfo a, @NonNull JunctionInfo b)
  {
    final double k = Math.cos(Math.toRadians(lat));
    final double ax = (a.mLon - lon) * k, ay = a.mLat - lat, bx = (b.mLon - lon) * k, by = b.mLat - lat;
    final double dx = bx - ax, dy = by - ay, len = dx * dx + dy * dy;
    final double t = len == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len));
    final double x = ax + dx * t, y = ay + dy * t;
    return x * x + y * y;
  }
}

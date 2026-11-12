package app.organicmaps.eurise;

import java.util.ArrayList;
import java.util.List;

/**
 * Meteo sul percorso, la parte che non tocca Android (si prova senza telefono): da quali punti della strada chiedere il
 * tempo, a che ora ci si passerà, e cosa dire in una riga. Distanze in metri, ore in secondi Unix.
 */
public final class RouteSampler
{
  /** Cosa c'è di più importante sulla strada che resta, dal più grave. */
  public enum Kind { STORM, ICE, SNOW, FOG, RAIN, WIND, CLEAR }

  /** Il tratto più grave: [index] tra i punti chiesti. */
  public static final class Finding
  {
    public final Kind kind;
    public final int index;
    Finding(Kind kind, int index) { this.kind = kind; this.index = index; }
  }

  /** Distanza minima tra due punti chiesti: il modello di Open-Meteo in Italia ha celle di 2 km, i fronti sono più larghi. */
  static final double MIN_STEP = 20_000;

  private RouteSampler() {}

  /** Metri dall'inizio del percorso per ogni punto (formula dell'emisenoverso). */
  public static double[] cumulative(double[] lat, double[] lon)
  {
    final double[] cum = new double[lat.length];
    for (int i = 1; i < lat.length; i++)
      cum[i] = cum[i - 1] + meters(lat[i - 1], lon[i - 1], lat[i], lon[i]);
    return cum;
  }

  static double meters(double lat1, double lon1, double lat2, double lon2)
  {
    final double r = 6_371_000;
    final double p1 = Math.toRadians(lat1), p2 = Math.toRadians(lat2);
    final double dp = p2 - p1, dl = Math.toRadians(lon2 - lon1);
    final double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
    return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
  }

  /**
   * I punti da chiedere sulla strada che resta ([traveled] metri già fatti): dove sei, poi uno ogni almeno 20 km, fino
   * all'arrivo, al massimo [max]. Restituisce gli indici nei punti del percorso.
   */
  public static int[] pick(double[] cum, double traveled, int max)
  {
    final int last = cum.length - 1;
    if (last < 0)
      return new int[0];
    final double total = cum[last];
    final double ahead = total - traveled;
    if (ahead <= 0 || max < 2)
      return new int[] {last};
    final double step = Math.max(MIN_STEP, ahead / (max - 1));
    final List<Integer> out = new ArrayList<>();
    int k = 0;
    for (double target = traveled; target < total && out.size() < max - 1; target += step)
    {
      while (k < last && cum[k] < target)
        k++;
      if (out.isEmpty() || out.get(out.size() - 1) != k)
        out.add(k);
    }
    if (out.isEmpty() || out.get(out.size() - 1) != last)
      out.add(last);
    final int[] r = new int[out.size()];
    for (int i = 0; i < r.length; i++)
      r[i] = out.get(i);
    return r;
  }

  /** L'ora di passaggio da ogni punto: il tempo che resta diviso in proporzione alla strada. */
  public static long[] times(double[] cum, int[] idx, double traveled, long nowSec, long remainingSec)
  {
    final double ahead = Math.max(1, cum[cum.length - 1] - traveled);
    final long[] t = new long[idx.length];
    for (int i = 0; i < idx.length; i++)
      t[i] = nowSec + Math.round(remainingSec * Math.max(0, cum[idx[i]] - traveled) / ahead);
    return t;
  }

  static Kind kind(int code, int rain, float gusts)
  {
    if (code >= 95 && code <= 99)
      return Kind.STORM;
    if (code == 56 || code == 57 || code == 66 || code == 67)
      return Kind.ICE;
    if ((code >= 71 && code <= 77) || code == 85 || code == 86)
      return Kind.SNOW;
    if (code == 45 || code == 48)
      return Kind.FOG;
    final boolean wet = (code >= 51 && code <= 65) || (code >= 80 && code <= 82);
    if ((wet && rain >= 30) || rain >= 60)
      return Kind.RAIN;
    if (gusts >= 60)
      return Kind.WIND;
    return Kind.CLEAR;
  }

  /** Il tratto più grave della strada che resta (il primo, a parità di gravità); CLEAR se non c'è nulla da dire. */
  public static Finding worst(int[] code, int[] rain, float[] gusts)
  {
    Kind best = Kind.CLEAR;
    int at = code.length - 1;
    for (int i = 0; i < code.length; i++)
    {
      final Kind k = kind(code[i], rain[i], gusts[i]);
      if (k.ordinal() < best.ordinal())
      {
        best = k;
        at = i;
      }
    }
    return new Finding(best, Math.max(0, at));
  }
}

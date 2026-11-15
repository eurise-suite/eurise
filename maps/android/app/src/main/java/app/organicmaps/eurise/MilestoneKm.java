package app.organicmaps.eurise;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Il km sulla strada dalle pietre miliari di OpenStreetMap («Sei su A51 · km 12,4», per soccorsi e carro attrezzi).
 * Solo calcolo, senza Android: il file lo prepara {@code maps/pietre/pietre.py} (formato descritto lì), lo scarica e lo
 * passa qui {@link EuriseMilestones}.
 *
 * <p>Il km si ricava tra due pietre della stessa strada vicine alla posizione: si proietta la posizione sul tratto che le
 * unisce e si interpola. Con le pietre ogni 100 m o 1 km la linea dritta tra le due segue la strada a pochi metri. Una
 * coppia vale solo se è coerente: la differenza di km deve somigliare alla distanza tra le pietre (così non si mescolano
 * due strade con la stessa sigla, come le provinciali di due province) e la posizione deve stare sul tratto, non di
 * fianco. Se nessuna coppia regge non si dà nessun km: per i soccorsi un km sbagliato è peggio di nessuno.
 */
public final class MilestoneKm
{
  /** La pietra più vicina deve stare entro questa distanza. */
  static final double NEAREST_MAX_M = 1500;
  /** Le pietre si cercano in questo raggio. */
  static final double SEARCH_M = 3000;
  /** Distanza massima della posizione dalla linea tra le due pietre (carreggiate separate, pietre a lato). */
  static final double LATERAL_MAX_M = 120;
  /** Fuori dal tratto tra le due pietre si prolunga al massimo di tanto. */
  static final double EXTEND_MAX_M = 300;
  private static final int PAIR_CANDIDATES = 12;
  private static final double M_PER_DEG = 111_320.0;

  public final int date;
  final int[] lat, lon, meters;
  final char[] ref;
  final String[][] refs;

  MilestoneKm(int date, int[] lat, int[] lon, int[] meters, char[] ref, String[][] refs)
  {
    this.date = date;
    this.lat = lat;
    this.lon = lon;
    this.meters = meters;
    this.ref = ref;
    this.refs = refs;
  }

  public int size()
  {
    return lat.length;
  }

  /** Legge il file già decompresso. */
  public static MilestoneKm read(InputStream in) throws IOException
  {
    final DataInputStream d = new DataInputStream(in);
    final byte[] magic = new byte[4];
    d.readFully(magic);
    if (magic[0] != 'E' || magic[1] != 'K' || magic[2] != 'M' || magic[3] != '1')
      throw new IOException("non è un file di pietre miliari");
    final int date = d.readInt();
    final int count = d.readInt();
    final int nRefs = d.readUnsignedShort();
    if (count < 0 || count > 20_000_000)
      throw new IOException("numero di pietre impossibile: " + count);
    final String[][] refs = new String[nRefs][];
    for (int i = 0; i < nRefs; i++)
    {
      final byte[] b = new byte[d.readUnsignedShort()];
      d.readFully(b);
      refs[i] = new String(b, java.nio.charset.StandardCharsets.UTF_8).split(";");
    }
    final int[] lat = new int[count], lon = new int[count], meters = new int[count];
    final char[] ref = new char[count];
    for (int i = 0; i < count; i++)
    {
      lat[i] = d.readInt();
      lon[i] = d.readInt();
      meters[i] = d.readInt();
      ref[i] = (char) d.readUnsignedShort();
      if (ref[i] >= nRefs || (i > 0 && lat[i] < lat[i - 1]))
        throw new IOException("file delle pietre rovinato");
    }
    return new MilestoneKm(date, lat, lon, meters, ref, refs);
  }

  /** «A 51», «a51» → «A51»: maiuscole, senza spazi. */
  static String normalize(String ref)
  {
    return ref == null ? "" : ref.replace(" ", "").toUpperCase(Locale.ROOT);
  }

  /**
   * Il km (con i decimali) della posizione sulla strada con sigla [roadRef]; NaN se le pietre non bastano per dirlo con
   * sicurezza.
   */
  public double kmAt(double latDeg, double lonDeg, String roadRef)
  {
    final String road = normalize(roadRef);
    if (road.isEmpty() || lat.length == 0)
      return Double.NaN;
    final double k = Math.cos(Math.toRadians(latDeg));
    final int band = (int) Math.ceil(SEARCH_M / M_PER_DEG * 1e6);
    final int la = (int) Math.round(latDeg * 1e6), lo = (int) Math.round(lonDeg * 1e6);

    // Le pietre della strada nel raggio, dalla più vicina.
    final List<double[]> near = new ArrayList<>(); // {x, y, km, distanza}
    for (int i = lowerBound(la - band); i < lat.length && lat[i] <= la + band; i++)
    {
      final double x = (lon[i] - lo) / 1e6 * k * M_PER_DEG, y = (lat[i] - la) / 1e6 * M_PER_DEG;
      final double dist = Math.hypot(x, y);
      if (dist <= SEARCH_M && hasRef(refs[ref[i]], road))
        near.add(new double[] {x, y, meters[i] / 1000.0, dist});
    }
    if (near.isEmpty())
      return Double.NaN;
    Collections.sort(near, (a, b) -> Double.compare(a[3], b[3]));
    if (near.get(0)[3] > NEAREST_MAX_M)
      return Double.NaN;

    double best = Double.NaN, bestScore = Double.MAX_VALUE;
    final int n = Math.min(near.size(), PAIR_CANDIDATES);
    for (int i = 0; i < n; i++)
      for (int j = i + 1; j < n; j++)
      {
        final double[] a = near.get(i), b = near.get(j);
        final double dKm = Math.abs(b[2] - a[2]);
        final double dx = b[0] - a[0], dy = b[1] - a[1];
        final double len = Math.hypot(dx, dy);
        // Pietre con lo stesso km (le due carreggiate) o troppo vicine non danno una direzione.
        if (dKm < 0.05 || len < 30)
          continue;
        // Lungo la strada la distanza è un po' più della linea dritta, mai molto meno.
        final double ratio = dKm * 1000 / len;
        if (ratio < 0.85 || ratio > 1.6)
          continue;
        // a e b sono rispetto alla posizione: la posizione è l'origine.
        final double t = -(a[0] * dx + a[1] * dy) / (len * len);
        final double lateral = Math.abs(a[0] * dy - a[1] * dx) / len;
        if (lateral > LATERAL_MAX_M)
          continue;
        final double outside = t < 0 ? -t * len : t > 1 ? (t - 1) * len : 0;
        if (outside > EXTEND_MAX_M)
          continue;
        // Meglio le coppie vicine che racchiudono la posizione.
        final double score = a[3] + b[3] + 4 * outside + lateral;
        if (score < bestScore)
        {
          bestScore = score;
          best = a[2] + t * (b[2] - a[2]);
        }
      }
    return best < 0 ? Double.NaN : best;
  }

  /** «km 12,4» nella lingua del telefono; vuoto se il km non si sa. */
  public static String label(double km, Locale locale)
  {
    if (Double.isNaN(km))
      return "";
    return "km " + String.format(locale, "%.1f", Math.floor(km * 10 + 0.5) / 10);
  }

  private static boolean hasRef(String[] set, String road)
  {
    for (String r : set)
      if (r.equals(road))
        return true;
    return false;
  }

  private int lowerBound(int value)
  {
    int i = Arrays.binarySearch(lat, value);
    if (i < 0)
      return -i - 1;
    while (i > 0 && lat[i - 1] == value)
      i--;
    return i;
  }
}

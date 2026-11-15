package app.organicmaps.eurise;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prove di {@link MilestoneKm} senza telefono né JUnit (CoMaps non ha prove Java): le lancia il lavoro di Mappe prima
 * di compilare, con {@code javac} e {@code java}. Un errore ferma la compilazione.
 */
public final class MilestoneKmTest
{
  private static int failures;

  public static void main(String[] args) throws IOException
  {
    straightRoad();
    bothCarriageways();
    otherRoad();
    tooFar();
    inconsistentPair();
    beside();
    curve();
    fileRoundTrip();
    labels();
    if (failures > 0)
    {
      System.err.println("MilestoneKmTest: " + failures + " prove fallite");
      System.exit(1);
    }
    System.out.println("MilestoneKmTest: tutte le prove riuscite");
  }

  /** Pietre lungo un meridiano: km [startKm] a lat 45, poi una ogni [stepM] metri verso nord, [eastM] a est. */
  private static void addRoad(List<int[]> rows, double startKm, int count, double stepM, double eastM, int ref)
  {
    final double k = Math.cos(Math.toRadians(45));
    for (int i = 0; i < count; i++)
    {
      final double lat = 45 + i * stepM / 111_320.0;
      final double lon = 9 + eastM / (111_320.0 * k);
      rows.add(new int[] {(int) Math.round(lat * 1e6), (int) Math.round(lon * 1e6),
                          (int) Math.round((startKm + i * stepM / 1000) * 1000), ref});
    }
  }

  private static MilestoneKm table(List<int[]> rows, String... refs)
  {
    rows.sort((a, b) -> Integer.compare(a[0], b[0]));
    final int n = rows.size();
    final int[] lat = new int[n], lon = new int[n], m = new int[n];
    final char[] r = new char[n];
    for (int i = 0; i < n; i++)
    {
      lat[i] = rows.get(i)[0];
      lon[i] = rows.get(i)[1];
      m[i] = rows.get(i)[2];
      r[i] = (char) rows.get(i)[3];
    }
    final String[][] sets = new String[refs.length][];
    for (int i = 0; i < refs.length; i++)
      sets[i] = refs[i].split(";");
    return new MilestoneKm(20261115, lat, lon, m, r, sets);
  }

  /** La posizione [northM] metri a nord di lat 45 sull'asse lon 9 (più [eastM] a est). */
  private static double at(MilestoneKm t, double northM, double eastM, String ref)
  {
    final double k = Math.cos(Math.toRadians(45));
    return t.kmAt(45 + northM / 111_320.0, 9 + eastM / (111_320.0 * k), ref);
  }

  private static void straightRoad()
  {
    final List<int[]> rows = new ArrayList<>();
    addRoad(rows, 10, 8, 1000, 12, 0);
    final MilestoneKm t = table(rows, "A51;E64");
    near("strada dritta, tra due pietre", at(t, 2400, 0, "A51"), 12.4);
    near("strada dritta, sulla pietra", at(t, 3000, 0, "A51"), 13.0);
    near("sigla con lo spazio e minuscola", at(t, 2400, 0, "a 51"), 12.4);
    near("seconda sigla", at(t, 2400, 0, "E64"), 12.4);
    near("oltre l'ultima pietra di poco", at(t, 7200, 0, "A51"), 17.2);
    nan("oltre l'ultima pietra di molto", at(t, 7900, 0, "A51"));
  }

  private static void bothCarriageways()
  {
    final List<int[]> rows = new ArrayList<>();
    addRoad(rows, 10, 6, 100, 15, 0);
    addRoad(rows, 10, 6, 100, -15, 0);
    final MilestoneKm t = table(rows, "A1");
    near("pietre ettometriche sulle due carreggiate", at(t, 250, 8, "A1"), 10.25);
  }

  private static void otherRoad()
  {
    final List<int[]> rows = new ArrayList<>();
    addRoad(rows, 10, 5, 1000, 12, 0);
    final MilestoneKm t = table(rows, "SS36");
    nan("altra strada", at(t, 2400, 0, "SS38"));
    nan("strada senza sigla", at(t, 2400, 0, ""));
  }

  private static void tooFar()
  {
    final List<int[]> rows = new ArrayList<>();
    addRoad(rows, 10, 2, 1000, 12, 0);
    final MilestoneKm t = table(rows, "SP12");
    nan("pietre lontane", at(t, -2000, 0, "SP12"));
  }

  private static void inconsistentPair()
  {
    // Due provinciali con la stessa sigla che si toccano: km 5 e km 40 a 600 m di distanza.
    final List<int[]> rows = new ArrayList<>();
    addRoad(rows, 5, 1, 1000, 10, 0);
    final List<int[]> other = new ArrayList<>();
    addRoad(other, 40, 1, 1000, 10, 0);
    other.get(0)[0] += (int) Math.round(600 / 111_320.0 * 1e6);
    rows.addAll(other);
    final MilestoneKm t = table(rows, "SP12");
    nan("coppia incoerente", at(t, 300, 0, "SP12"));
  }

  private static void beside()
  {
    final List<int[]> rows = new ArrayList<>();
    addRoad(rows, 10, 5, 1000, 12, 0);
    final MilestoneKm t = table(rows, "A4");
    nan("posizione di fianco alla strada", at(t, 1500, 400, "A4"));
  }

  private static void curve()
  {
    // Curva larga: pietre ogni km su un arco di raggio 3 km; la distanza lungo la strada supera la linea dritta.
    final List<int[]> rows = new ArrayList<>();
    final double k = Math.cos(Math.toRadians(45)), r = 3000;
    for (int i = 0; i < 4; i++)
    {
      final double a = i * 1000 / r;
      final double north = r * Math.sin(a), east = r * (1 - Math.cos(a));
      rows.add(new int[] {(int) Math.round((45 + north / 111_320.0) * 1e6),
                          (int) Math.round((9 + east / (111_320.0 * k)) * 1e6), 20_000 + i * 1000, 0});
    }
    final MilestoneKm t = table(rows, "A22");
    final double a = 1500 / r;
    near("in curva", at(t, r * Math.sin(a), r * (1 - Math.cos(a)), "A22"), 21.5);
  }

  private static void fileRoundTrip() throws IOException
  {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    final DataOutputStream d = new DataOutputStream(bytes);
    d.writeBytes("EKM1");
    d.writeInt(20261115);
    d.writeInt(2);
    d.writeShort(1);
    final byte[] ref = "A1;E35".getBytes(StandardCharsets.UTF_8);
    d.writeShort(ref.length);
    d.write(ref);
    for (int i = 0; i < 2; i++)
    {
      d.writeInt(45_000_000 + i * 8983);
      d.writeInt(9_000_000);
      d.writeInt(12_000 + i * 1000);
      d.writeShort(0);
    }
    final MilestoneKm t = MilestoneKm.read(new ByteArrayInputStream(bytes.toByteArray()));
    check("data del file", t.date == 20261115);
    check("pietre lette", t.size() == 2);
    near("km dal file", t.kmAt(45.0045, 9.0, "E35"), 12.5);
    boolean refused = false;
    try
    {
      MilestoneKm.read(new ByteArrayInputStream("XXXX".getBytes(StandardCharsets.US_ASCII)));
    }
    catch (IOException e)
    {
      refused = true;
    }
    check("file non valido rifiutato", refused);
  }

  private static void labels()
  {
    check("etichetta italiana", MilestoneKm.label(12.44, Locale.ITALY).equals("km 12,4"));
    check("etichetta inglese", MilestoneKm.label(12.46, Locale.UK).equals("km 12.5"));
    check("etichetta vuota", MilestoneKm.label(Double.NaN, Locale.ITALY).isEmpty());
  }

  private static void near(String name, double got, double want)
  {
    check(name + " (atteso " + want + ", ottenuto " + got + ")", !Double.isNaN(got) && Math.abs(got - want) < 0.06);
  }

  private static void nan(String name, double got)
  {
    check(name + " (atteso nessun km, ottenuto " + got + ")", Double.isNaN(got));
  }

  private static void check(String name, boolean ok)
  {
    if (!ok)
    {
      failures++;
      System.err.println("FALLITA: " + name);
    }
  }
}

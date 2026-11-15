package app.organicmaps.eurise;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Gli orari della scheda del luogo giorno per giorno (tavola MapLuogoAperta: «Oggi, venerdì · 8:30–12:30 · 15:00–19:30»,
 * «Domenica · chiuso»). Solo calcolo, senza Android: le tabelle orarie le legge CoMaps da OpenStreetMap
 * ({@code OpeningHours.nativeTimetablesFromString}) e {@code EuriseOrari} le passa qui in minuti.
 */
public final class OpeningRows
{
  private OpeningRows() {}

  /** Una tabella oraria di CoMaps in minuti: giorni (1 domenica … 7 sabato, come {@code Calendar}), apertura, pause. */
  public static final class Table
  {
    final int[] weekdays;
    final boolean fullDay;
    final int start, end;
    final int[] closedStart, closedEnd;

    public Table(int[] weekdays, boolean fullDay, int start, int end, int[] closedStart, int[] closedEnd)
    {
      this.weekdays = weekdays;
      this.fullDay = fullDay;
      this.start = start;
      this.end = end;
      this.closedStart = closedStart;
      this.closedEnd = closedEnd;
    }
  }

  /** Le fasce di apertura del giorno [day] ({start, end} in minuti), in ordine; vuoto se chiuso. */
  public static List<int[]> shifts(List<Table> tables, int day)
  {
    final List<int[]> out = new ArrayList<>();
    for (Table t : tables)
    {
      if (!has(t.weekdays, day))
        continue;
      if (t.fullDay)
      {
        out.add(new int[] {0, 24 * 60});
        continue;
      }
      int from = t.start;
      final int n = Math.min(t.closedStart.length, t.closedEnd.length);
      for (int i = 0; i < n; i++)
      {
        out.add(new int[] {from, t.closedStart[i]});
        from = t.closedEnd[i];
      }
      out.add(new int[] {from, t.end});
    }
    out.sort((a, b) -> Integer.compare(a[0], b[0]));
    return out;
  }

  /** Vero se tutti e sette i giorni hanno le stesse fasce (allora basta una riga «Tutti i giorni»). */
  public static boolean sameEveryDay(List<Table> tables)
  {
    final String first = key(shifts(tables, 1));
    for (int d = 2; d <= 7; d++)
      if (!first.equals(key(shifts(tables, d))))
        return false;
    return true;
  }

  /**
   * «8:30–12:30 · 15:00–19:30»; [allDay] per un giorno aperto 24 ore, [closed] per un giorno chiuso. [h24]: formato a
   * 24 ore (senza lo zero davanti, come nella tavola) o a 12.
   */
  public static String format(List<int[]> shifts, boolean h24, String allDay, String closed)
  {
    if (shifts.isEmpty())
      return closed;
    if (shifts.size() == 1 && shifts.get(0)[0] == 0 && shifts.get(0)[1] >= 24 * 60)
      return allDay;
    final StringBuilder b = new StringBuilder();
    for (int[] s : shifts)
    {
      if (b.length() > 0)
        b.append(" · ");
      b.append(time(s[0], h24)).append('–').append(time(s[1], h24));
    }
    return b.toString();
  }

  /** «8:30», «24:00» (fine giornata), «8:30 PM» a 12 ore. OpenStreetMap ammette anche 25:00: vale 1:00. */
  public static String time(int minutes, boolean h24)
  {
    int h = minutes / 60;
    final int m = minutes % 60;
    if (h > 24)
      h %= 24;
    if (h24)
      return h + ":" + (m < 10 ? "0" : "") + m;
    final boolean pm = h % 24 >= 12;
    final int h12 = h % 12 == 0 ? 12 : h % 12;
    return h12 + ":" + (m < 10 ? "0" : "") + m + (pm ? " PM" : " AM");
  }

  /** I giorni da mostrare a partire da oggi ([today], 1 … 7): [count] giorni di fila. */
  public static int[] daysFrom(int today, int count)
  {
    final int[] out = new int[count];
    for (int i = 0; i < count; i++)
      out[i] = (today - 1 + i) % 7 + 1;
    return out;
  }

  private static boolean has(int[] days, int day)
  {
    for (int d : days)
      if (d == day)
        return true;
    return false;
  }

  private static String key(List<int[]> shifts)
  {
    final StringBuilder b = new StringBuilder();
    for (int[] s : shifts)
      b.append(Arrays.toString(s));
    return b.toString();
  }
}

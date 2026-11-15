package app.organicmaps.eurise;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Prove di {@link OpeningRows} senza telefono (le lancia il lavoro di Mappe, come {@link MilestoneKmTest}). */
public final class OpeningRowsTest
{
  private static int failures;

  public static void main(String[] args)
  {
    // Lu-Ve 8:30-19:30 con pausa 12:30-15:00; Sa 8:30-12:30; domenica chiuso (la farmacia della tavola).
    final OpeningRows.Table week = new OpeningRows.Table(new int[] {2, 3, 4, 5, 6}, false, 510, 1170,
                                                         new int[] {750}, new int[] {900});
    final OpeningRows.Table saturday = new OpeningRows.Table(new int[] {7}, false, 510, 750, new int[0], new int[0]);
    final List<OpeningRows.Table> pharmacy = Arrays.asList(saturday, week);

    eq("venerdì", OpeningRows.format(OpeningRows.shifts(pharmacy, 6), true, "24 ore", "chiuso"), "8:30–12:30 · 15:00–19:30");
    eq("sabato", OpeningRows.format(OpeningRows.shifts(pharmacy, 7), true, "24 ore", "chiuso"), "8:30–12:30");
    eq("domenica", OpeningRows.format(OpeningRows.shifts(pharmacy, 1), true, "24 ore", "chiuso"), "chiuso");
    eq("a 12 ore", OpeningRows.format(OpeningRows.shifts(pharmacy, 7), false, "24 ore", "chiuso"), "8:30 AM–12:30 PM");
    check("settimana diversa", !OpeningRows.sameEveryDay(pharmacy));

    final OpeningRows.Table always = new OpeningRows.Table(new int[] {1, 2, 3, 4, 5, 6, 7}, true, 0, 0, new int[0], new int[0]);
    eq("24 ore", OpeningRows.format(OpeningRows.shifts(Collections.singletonList(always), 3), true, "24 ore", "chiuso"), "24 ore");
    check("sempre uguale", OpeningRows.sameEveryDay(Collections.singletonList(always)));

    final OpeningRows.Table night = new OpeningRows.Table(new int[] {6, 7}, false, 1080, 1560, new int[0], new int[0]);
    eq("fino a notte (26:00)", OpeningRows.format(OpeningRows.shifts(Collections.singletonList(night), 6), true, "", ""), "18:00–2:00");
    eq("mezzanotte", OpeningRows.time(24 * 60, true), "24:00");
    eq("mezzogiorno a 12 ore", OpeningRows.time(12 * 60, false), "12:00 PM");
    eq("da venerdì tre giorni", Arrays.toString(OpeningRows.daysFrom(6, 3)), "[6, 7, 1]");

    if (failures > 0)
    {
      System.err.println("OpeningRowsTest: " + failures + " prove fallite");
      System.exit(1);
    }
    System.out.println("OpeningRowsTest: tutte le prove riuscite");
  }

  private static void eq(String name, String got, String want)
  {
    check(name + " (atteso «" + want + "», ottenuto «" + got + "»)", want.equals(got));
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

package app.organicmaps.eurise;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prove di {@link GuideWit}: mai battute vicino a una manovra, e nelle battute di partenza e del rettilineo nessuna parola
 * che somigli a un'istruzione (le legge dalle stringhe di Mappe, in italiano e in inglese). Si lancia dalla radice del
 * repository, come le altre prove di Mappe.
 */
public final class GuideWitTest
{
  private static int failures;

  public static void main(String[] args) throws Exception
  {
    nothingNearATurn();
    departureThenCruiseWithGap();
    arrivalOnce();
    newTrip();
    safeWords();
    resourcesAreSafe("maps/android/app/src/main/res/values-it/eurise_strings.xml");
    resourcesAreSafe("maps/android/app/src/main/res/values/eurise_strings.xml");
    pickAvoidsRepeats();
    if (failures > 0)
    {
      System.err.println("GuideWitTest: " + failures + " errori");
      System.exit(1);
    }
    System.out.println("GuideWitTest: tutto bene");
  }

  private static void check(boolean ok, String what)
  {
    if (!ok)
    {
      failures++;
      System.err.println("NO: " + what);
    }
  }

  private static void nothingNearATurn()
  {
    final GuideWit w = new GuideWit();
    for (long t = 0; t < 60 * 60_000; t += 1000)
      check(w.next(t, 800, 50_000, false) == null, "battuta a 800 m da una svolta, al secondo " + t / 1000);
  }

  private static void departureThenCruiseWithGap()
  {
    final GuideWit w = new GuideWit();
    check(w.next(0, 9000, 90_000, false) == null, "battuta subito alla partenza");
    check(w.next(31_000, 9000, 90_000, false) == GuideWit.Moment.DEPARTURE, "manca la partenza");
    check(w.next(32_000, 9000, 90_000, false) == null, "due battute di fila");
    long t = 32_000;
    GuideWit.Moment m = null;
    while (m == null && t < 3_600_000)
    {
      t += 1000;
      m = w.next(t, 9000, 90_000, false);
    }
    check(m == GuideWit.Moment.CRUISE && t - 31_000 >= GuideWit.MIN_GAP_MS, "rettilineo troppo presto: " + t);
  }

  private static void arrivalOnce()
  {
    final GuideWit w = new GuideWit();
    w.next(0, 300, 2000, false);
    for (long t = 1000; t < 40_000; t += 1000)
      w.next(t, 300, 300, false);
    check(w.next(41_000, 20, 20, false) == GuideWit.Moment.ARRIVAL, "manca l'arrivo");
    check(w.next(42_000, 10, 10, false) == null, "arrivo detto due volte");
  }

  private static void newTrip()
  {
    final GuideWit w = new GuideWit();
    w.next(0, 9000, 90_000, false);
    check(w.next(31_000, 9000, 90_000, false) == GuideWit.Moment.DEPARTURE, "manca la partenza (1)");
    check(w.next(200_000, 9000, 90_000, false) == null, "viaggio nuovo: battuta subito");
    check(w.next(231_000, 9000, 90_000, false) == GuideWit.Moment.DEPARTURE, "manca la partenza del viaggio nuovo");
  }

  private static void safeWords()
  {
    check(!GuideWit.safe("Destra o sinistra, ormai poco importa."), "destra non riconosciuta");
    check(!GuideWit.safe("Alla rotonda prenda la seconda."), "rotonda non riconosciuta");
    check(!GuideWit.safe("Keep calm and turn left."), "inglese non riconosciuto");
    check(GuideWit.safe("Strada lunga e tranquilla. Mi godo il panorama."), "frase innocua scartata");
  }

  private static void resourcesAreSafe(String path) throws Exception
  {
    final String xml = Files.readString(Path.of(path));
    for (String name : new String[] {"eurise_wit_departure", "eurise_wit_cruise", "eurise_wit_arrival"})
    {
      final Matcher a = Pattern.compile("<string-array name=\"" + name + "\">(.*?)</string-array>", Pattern.DOTALL).matcher(xml);
      check(a.find(), path + ": manca " + name);
      if (!a.reset().find())
        continue;
      final Matcher item = Pattern.compile("<item>(.*?)</item>").matcher(a.group(1));
      int n = 0;
      while (item.find())
      {
        n++;
        final String line = item.group(1).replace("\\'", "'");
        check(line.split(" ").length <= 14, path + ": battuta troppo lunga: " + line);
        if (!name.equals("eurise_wit_arrival"))
          check(GuideWit.safe(line), path + ": parola da istruzione in " + name + ": " + line);
      }
      check(n >= 3, path + ": poche battute in " + name);
    }
  }

  private static void pickAvoidsRepeats()
  {
    final String[] all = {"Uno.", "Due.", "Tre.", "Quattro.", "Cinque.", "Sei.", "Sette."};
    final List<String> said = new ArrayList<>();
    final Random r = new Random(1);
    for (int i = 0; i < 30; i++)
      said.add(GuideWit.pick(GuideWit.Moment.CRUISE, all, r));
    for (int i = 1; i < said.size(); i++)
      check(!said.get(i).equals(said.get(i - 1)), "stessa battuta di fila");
    check(GuideWit.pick(GuideWit.Moment.CRUISE, new String[] {"Giri a destra."}, r) == null, "battuta insicura scelta");
  }
}

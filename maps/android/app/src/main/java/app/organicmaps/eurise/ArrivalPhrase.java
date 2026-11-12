package app.organicmaps.eurise;

/**
 * La frase d'arrivo (tavola MapGuida), la parte senza Android: quanto margine resta sull'impegno verso cui vai e come
 * dirlo. Minuti interi; positivo = anticipo.
 */
public final class ArrivalPhrase
{
  public enum Margin { EARLY, SOME, ON_TIME, LATE }

  private ArrivalPhrase() {}

  /** Minuti tra l'arrivo previsto e l'inizio dell'impegno (positivi se arrivi prima). */
  public static int marginMinutes(long etaMillis, long startMillis)
  {
    return (int) Math.round((startMillis - etaMillis) / 60_000.0);
  }

  public static Margin margin(int minutes)
  {
    if (minutes >= 15)
      return Margin.EARLY;
    if (minutes >= 3)
      return Margin.SOME;
    if (minutes >= -2)
      return Margin.ON_TIME;
    return Margin.LATE;
  }

  /**
   * L'impegno scelto da «Dove devi andare» vale ancora per questa guida: scelto da meno di 6 ore e non finito da più di
   * mezz'ora rispetto all'arrivo previsto.
   */
  public static boolean stillRelevant(long pickedAt, long startMillis, long now, long etaMillis)
  {
    return now - pickedAt < 6 * 3_600_000L && etaMillis - startMillis < 30 * 60_000L;
  }
}

package app.organicmaps.eurise;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.Calendar;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * «I tuoi giorni» nel paesaggio (tavola MapCieli): se oggi è il compleanno o l'anniversario dell'utente, lo chiede
 * all'app Eurise (YourDaysContract in core/suite), che legge le Date importanti sul telefono e risponde solo col tipo di
 * giorno: nessun nome, niente rete. Una domanda al giorno, fuori dal thread principale; finché non c'è risposta, o senza
 * l'app Eurise, è un giorno qualsiasi.
 */
public final class EuriseYourDays
{
  // Stessi nomi di core/suite YourDaysContract (Mappe non dipende dai moduli Kotlin della suite).
  private static final String[] HUBS = {"eu.eurise.hub.debug", "eu.eurise.hub"};
  private static final String TODAY = "today", KIND = "kind";
  public static final int NONE = 0, BIRTHDAY = 1, ANNIVERSARY = 2;

  private static final ExecutorService IO = Executors.newSingleThreadExecutor();

  private static volatile int sKind = NONE;
  /** Il giorno (anno × 1000 + giorno dell'anno) dell'ultima domanda: una sola al giorno. */
  private static volatile int sAsked = -1;

  private EuriseYourDays() {}

  /** Il tipo di giorno di oggi ([NONE], [BIRTHDAY], [ANNIVERSARY]); chiede all'app Eurise la prima volta del giorno. */
  public static int today(@NonNull Context c)
  {
    final Calendar cal = Calendar.getInstance();
    final int day = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR);
    if (day != sAsked)
    {
      sAsked = day;
      sKind = NONE;
      final Context app = c.getApplicationContext();
      IO.execute(() -> sKind = ask(app));
    }
    return sKind;
  }

  private static int ask(@NonNull Context app)
  {
    for (String h : HUBS)
    {
      try
      {
        final Bundle b = app.getContentResolver().call(Uri.parse("content://" + h + ".yourdays"), TODAY, null, null);
        if (b != null)
          return b.getInt(KIND, NONE);
      }
      catch (Exception ignored)
      {
        // App Eurise assente, di un'altra firma o vecchia (senza il fornitore): si prova l'altra.
      }
    }
    return NONE;
  }
}

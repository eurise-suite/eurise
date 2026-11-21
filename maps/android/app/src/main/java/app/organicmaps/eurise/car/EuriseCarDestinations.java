package app.organicmaps.eurise.car;

import app.organicmaps.eurise.EuriseSuite;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;

import androidx.annotation.NonNull;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.Row;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import app.organicmaps.R;
import app.organicmaps.eurise.EuriseArrival;

/**
 * «Dove devi andare» nella ricerca di Android Auto (tavola MapAutoCerca), nello stesso ordine del telefono (22 novembre):
 * Sempre a portata (Casa e Lavoro dai preferiti, {@link #addHabitual}), ricerche recenti, Categorie («Cerca vicino»), poi
 * gli impegni con un luogo dall'app Eurise (DestinationsContract, {@link #addRows}). Gli impegni si leggono fuori dal
 * thread principale; la schermata si ridisegna quando arrivano. Un tocco su un impegno lo ricorda (per «Manda l'arrivo»)
 * e cerca il luogo; su Casa o Lavoro cerca le sue coordinate.
 */
public final class EuriseCarDestinations
{
  private static final String[] HUBS = EuriseSuite.HUBS;
  private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

  /** Un impegno: titolo, luogo, inizio. */
  private static final class Item
  {
    final String title, place;
    final long start;

    Item(String title, String place, long start)
    {
      this.title = title;
      this.place = place;
      this.start = start;
    }

    String title() { return title; }
    String place() { return place; }
    long start() { return start; }

    @Override
    public boolean equals(Object o)
    {
      return o instanceof Item i && i.title.equals(title) && i.place.equals(place) && i.start == start;
    }

    @Override
    public int hashCode()
    {
      return title.hashCode() * 31 + place.hashCode() + Long.hashCode(start);
    }
  }

  @NonNull
  private static volatile List<Item> sItems = Collections.emptyList();
  /** Quante righe di Sempre a portata sono state messe l'ultima volta (size() non ha il Context). */
  private static volatile int sHabitualCount;

  private EuriseCarDestinations() {}

  /** Rilegge gli impegni; {@code changed} sul thread principale se la lista è cambiata. */
  public static void refresh(@NonNull Context c, @NonNull Runnable changed)
  {
    final Context app = c.getApplicationContext();
    final Handler main = new Handler(Looper.getMainLooper());
    WORKER.execute(() -> {
      Bundle b = null;
      for (String h : HUBS)
      {
        try
        {
          b = EuriseSuite.call(app, Uri.parse("content://" + h + ".destinations"), "list", null, null);
        }
        catch (Exception ignored)
        {
          // App Eurise assente o di un'altra firma.
        }
        if (b != null)
          break;
      }
      final List<Item> out = new ArrayList<>();
      if (b != null)
      {
        final String[] titles = b.getStringArray("title");
        final String[] places = b.getStringArray("place");
        final long[] starts = b.getLongArray("start");
        if (titles != null && places != null && starts != null)
          for (int i = 0; i < Math.min(Math.min(titles.length, places.length), Math.min(starts.length, 4)); i++)
            out.add(new Item(titles[i], places[i], starts[i]));
      }
      if (!out.equals(sItems))
      {
        sItems = Collections.unmodifiableList(out);
        main.post(changed);
      }
    });
  }

  /** Aggiunge le righe degli impegni alla lista; {@code search} cerca il luogo. */
  public static void addRows(@NonNull Context c, @NonNull ItemList.Builder list, @NonNull Consumer<String> search)
  {
    for (Item it : sItems)
    {
      final String when = DateUtils.formatDateTime(c, it.start(), DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_TIME
                                                                   | DateUtils.FORMAT_ABBREV_WEEKDAY);
      list.addItem(new Row.Builder()
                       .setTitle(it.title() + " · " + it.place())
                       .addText(c.getString(R.string.eurise_go_from_calendar, when))
                       .setOnClickListener(() -> {
                         EuriseArrival.remember(c, it.title(), it.start());
                         search.accept(it.place());
                       })
                       .build());
    }
  }

  /** Quante righe aggiungono {@link #addHabitual} e {@link #addRows} (per non superare il massimo di righe dell'auto). */
  public static int size()
  {
    return sItems.size() + sHabitualCount;
  }

  /** Sempre a portata: Casa e Lavoro (gli stessi preferiti del telefono); {@code search} ne cerca le coordinate. */
  public static void addHabitual(@NonNull Context c, @NonNull ItemList.Builder list, @NonNull Consumer<String> search)
  {
    final List<app.organicmaps.sdk.bookmarks.data.MapObject> places = habitual(c);
    sHabitualCount = places.size();
    for (app.organicmaps.sdk.bookmarks.data.MapObject m : places)
      list.addItem(new Row.Builder()
                       .setTitle(m.getTitle())
                       .addText(c.getString(R.string.eurise_go_always))
                       .setOnClickListener(() -> search.accept(
                           String.format(java.util.Locale.ROOT, "%.6f, %.6f", m.getLat(), m.getLon())))
                       .build());
  }

  @NonNull
  private static List<app.organicmaps.sdk.bookmarks.data.MapObject> habitual(@NonNull Context c)
  {
    try
    {
      return app.organicmaps.eurise.ui.EuriseSearchHomeKt.habitualPlaces(c);
    }
    catch (RuntimeException e)
    {
      return Collections.emptyList(); // preferiti non ancora caricati
    }
  }
}

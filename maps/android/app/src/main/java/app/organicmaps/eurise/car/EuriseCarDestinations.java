package app.organicmaps.eurise.car;

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
 * «Dove devi andare» nella ricerca di Android Auto (tavola MapAutoCerca): gli impegni con un luogo dall'app Eurise
 * (DestinationsContract), in cima alla lista prima di Categorie e ricerche recenti. Si leggono fuori dal thread
 * principale; la schermata si ridisegna quando arrivano. Un tocco ricorda l'impegno (per «Manda l'arrivo») e cerca il luogo.
 */
public final class EuriseCarDestinations
{
  private static final String[] HUBS = {"eu.eurise.hub.debug", "eu.eurise.hub"};
  private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

  /** Un impegno: titolo, luogo, inizio. */
  private record Item(String title, String place, long start) {}

  @NonNull
  private static volatile List<Item> sItems = Collections.emptyList();

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
          b = app.getContentResolver().call(Uri.parse("content://" + h + ".destinations"), "list", null, null);
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

  /** Quante righe aggiunge {@link #addRows} (per non superare il massimo di righe dell'auto). */
  public static int size()
  {
    return sItems.size();
  }
}

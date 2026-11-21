package app.organicmaps.eurise;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.sdk.downloader.CountryItem;
import app.organicmaps.sdk.downloader.MapManager;
import app.organicmaps.sdk.util.concurrency.UiThread;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

/**
 * Le pietre miliari sul telefono (il km su autostrade, superstrade, statali e provinciali): un file per paese, preparato
 * dal lavoro «Pietre miliari» di GitHub Actions da OpenStreetMap e pubblicato nella pre-release {@code pietre}.
 *
 * <p>Come le mappe, il file è uguale per tutti e si scarica a richiesta: solo con il servizio «Km sulle strade» acceso
 * nel centro privacy dell'app Eurise (Livelli), per i paesi delle mappe scaricate o, in viaggio, per il paese in cui si
 * è. Esce solo la richiesta del file del paese; la posizione no. Il calcolo è in {@link MilestoneKm}. Si aggiorna ogni
 * 60 giorni; un paese senza file (nessuna pietra in OpenStreetMap) si riprova dopo 30.
 */
public final class EuriseMilestones
{
  private static final String BASE = "https://github.com/eurise-suite/eurise/releases/download/pietre/";
  private static final String[] HUBS = EuriseSuite.HUBS;
  /** Il nome del servizio nel centro privacy (core/suite PrivacyService.Milestones). */
  private static final String SERVICE = "Milestones";
  private static final long DAY = 24L * 60 * 60 * 1000;
  private static final long MAX_AGE = 60 * DAY;
  private static final long RETRY_MISSING = 30 * DAY;

  private static final ExecutorService IO = Executors.newSingleThreadExecutor();
  /** Il paese della tabella in memoria e la tabella (null se il file non c'è). Una sola alla volta. */
  @Nullable private static volatile String sLoadedCountry;
  @Nullable private static volatile MilestoneKm sTable;
  @Nullable private static volatile String sLoading;
  /** Paesi già tentati in questa sessione (non si riscarica a ogni posizione). */
  private static final Set<String> sTried = new HashSet<>();

  private EuriseMilestones() {}

  /**
   * «km 12,4» sulla strada [ref] nel paese [country] (id della mappa di CoMaps, «Italy»); vuoto se non si sa. Dal thread
   * principale: se il file del paese non è ancora in memoria lo carica (o lo scarica, se il servizio è acceso) e per ora
   * risponde vuoto.
   */
  @NonNull
  public static String label(@NonNull Context c, @NonNull String country, double lat, double lon, @Nullable String ref)
  {
    if (country.isEmpty() || ref == null || ref.isEmpty())
      return "";
    final MilestoneKm table = sTable;
    if (!country.equals(sLoadedCountry))
    {
      load(c.getApplicationContext(), country);
      return "";
    }
    if (table == null)
      return "";
    return MilestoneKm.label(table.kmAt(lat, lon, ref), Locale.getDefault());
  }

  private static void load(@NonNull Context app, @NonNull String country)
  {
    if (country.equals(sLoading))
      return;
    sLoading = country;
    IO.execute(() -> {
      MilestoneKm table = read(file(app, country));
      final boolean stale = table == null || System.currentTimeMillis() - file(app, country).lastModified() > MAX_AGE;
      boolean first;
      synchronized (sTried)
      {
        first = sTried.add(country);
      }
      if (stale && first && Boolean.TRUE.equals(privacy(app, "status")) && download(app, country))
        table = read(file(app, country));
      sTable = table;
      sLoadedCountry = country;
      sLoading = null;
    });
  }

  /**
   * Scarica i file dei paesi [countries] (acceso il servizio in Livelli), fuori dal thread principale; [done] sul thread
   * principale con il numero di paesi che ora hanno le pietre sul telefono.
   */
  public static void downloadAll(@NonNull Context c, @NonNull Collection<String> countries, @Nullable IntCallback done)
  {
    final Context app = c.getApplicationContext();
    final List<String> list = new ArrayList<>(countries);
    IO.execute(() -> {
      int ok = 0;
      for (String country : list)
      {
        final File f = file(app, country);
        final boolean fresh = f.isFile() && System.currentTimeMillis() - f.lastModified() < MAX_AGE;
        if (fresh || download(app, country))
          ok++;
      }
      // La tabella in memoria si rilegge dal file nuovo alla prossima posizione.
      sLoadedCountry = null;
      synchronized (sTried)
      {
        sTried.clear();
      }
      final int count = ok;
      if (done != null)
        UiThread.run(() -> done.on(count));
    });
  }

  public interface IntCallback
  {
    void on(int value);
  }

  /**
   * I paesi delle mappe scaricate («Italy», «France»), senza doppioni. Dal thread principale, come le altre chiamate al
   * motore delle mappe.
   */
  @NonNull
  public static Set<String> downloadedCountries()
  {
    final Set<String> out = new LinkedHashSet<>();
    try
    {
      final List<CountryItem> items = new ArrayList<>();
      MapManager.nativeListItems(null, 0, 0, false, false, items);
      for (CountryItem item : items)
      {
        MapManager.nativeGetAttributes(item);
        final String top = item.topmostParentId != null && !item.topmostParentId.isEmpty() ? item.topmostParentId : item.id;
        if (top != null && !top.isEmpty())
          out.add(top);
      }
    }
    catch (Throwable ignored)
    {
      // Motore non pronto: nessun paese.
    }
    return out;
  }

  /** Il paese (id della mappa di primo livello) del punto; vuoto se non si sa. Dal thread principale. */
  @NonNull
  public static String countryAt(double lat, double lon)
  {
    try
    {
      final String id = MapManager.nativeFindCountry(lat, lon);
      if (id == null || id.isEmpty())
        return "";
      final CountryItem item = new CountryItem(id);
      MapManager.nativeGetAttributes(item);
      return item.topmostParentId != null && !item.topmostParentId.isEmpty() ? item.topmostParentId : id;
    }
    catch (Throwable e)
    {
      return "";
    }
  }

  // ---- Centro privacy (come i prezzi dei carburanti) ---------------------------------------------------------------

  /** «status», «enable», «disable», «log»: lo stato del servizio, o null se l'app Eurise manca. Fuori dal thread principale. */
  @Nullable
  public static Boolean privacy(@NonNull Context c, @NonNull String method)
  {
    for (String h : HUBS)
    {
      try
      {
        final Bundle b = EuriseSuite.call(c, Uri.parse("content://" + h + ".privacy"), method, SERVICE, null);
        if (b != null)
          return b.getBoolean("on");
      }
      catch (Exception ignored)
      {
        // App Eurise assente o di un'altra firma: si prova l'altra.
      }
    }
    return null;
  }

  // ---- File ---------------------------------------------------------------------------------------------------------

  /** «Italy» → «Italy.ekm.gz», «Czech Republic» → «Czech_Republic.ekm.gz» (come lo pubblica il lavoro di Actions). */
  @NonNull
  static String fileName(@NonNull String country)
  {
    return country.replaceAll("[^A-Za-z0-9-]+", "_") + ".ekm.gz";
  }

  @NonNull
  private static File file(@NonNull Context c, @NonNull String country)
  {
    return new File(new File(c.getFilesDir(), "pietre"), fileName(country));
  }

  @Nullable
  private static MilestoneKm read(@NonNull File f)
  {
    if (!f.isFile())
      return null;
    try (InputStream in = new GZIPInputStream(new BufferedInputStream(new FileInputStream(f), 64 * 1024)))
    {
      return MilestoneKm.read(in);
    }
    catch (Exception e)
    {
      return null;
    }
  }

  /** Scarica il file del paese; vero se ora c'è. Un paese senza file si segna e si riprova dopo 30 giorni. */
  private static boolean download(@NonNull Context app, @NonNull String country)
  {
    final File f = file(app, country);
    final File missing = new File(f.getPath() + ".manca");
    if (missing.isFile() && System.currentTimeMillis() - missing.lastModified() < RETRY_MISSING)
      return f.isFile();
    if (!Boolean.TRUE.equals(privacy(app, "status")))
      return f.isFile();
    HttpURLConnection conn = null;
    try
    {
      //noinspection ResultOfMethodCallIgnored
      f.getParentFile().mkdirs();
      conn = (HttpURLConnection) new URL(BASE + Uri.encode(fileName(country))).openConnection();
      conn.setConnectTimeout(15_000);
      conn.setReadTimeout(60_000);
      conn.setRequestProperty("User-Agent", "EuriseMappe");
      final int code = conn.getResponseCode();
      privacy(app, "log");
      if (code == 404)
      {
        //noinspection ResultOfMethodCallIgnored
        new FileOutputStream(missing).close();
        return f.isFile();
      }
      if (code != 200)
        return f.isFile();
      final File tmp = new File(f.getPath() + ".new");
      try (InputStream in = conn.getInputStream(); OutputStream out = new FileOutputStream(tmp))
      {
        final byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0)
          out.write(buf, 0, n);
      }
      // Si tiene solo un file che si legge per intero.
      if (read(tmp) == null || !tmp.renameTo(f))
      {
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
        return f.isFile();
      }
      //noinspection ResultOfMethodCallIgnored
      missing.delete();
      return true;
    }
    catch (Exception e)
    {
      return f.isFile();
    }
    finally
    {
      if (conn != null)
        conn.disconnect();
    }
  }
}

package app.organicmaps.eurise;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import app.organicmaps.R;

/**
 * Mappe nel backup cifrato della suite (area Backup, 21 novembre 2026; docs/stato/BACKUP.md). Lo stesso accordo di
 * core/suite SuiteBackupProvider, riscritto in Java perché Mappe si compila a parte, dentro CoMaps:
 * {@code content://eu.eurise.maps.eurise.backup/export} (lo zip in un tubo), {@code /import} (lo zip da rimettere, letto
 * mentre arriva), {@code call("apply")}, {@code call("summary")}, {@code call("ready")}.
 *
 * <p>Nel backup: i segnalibri e le tracce (i file .kml nella cartella {@code bookmarks} di CoMaps, che li carica tutti
 * all'avvio), {@code settings.ini} (le impostazioni di CoMaps: le stesse due cose che CoMaps metteva nel backup di
 * Android, ora spento), le impostazioni di Eurise ({@code eurise_*}: guida, cielo, bolla, finestre degli svincoli) e le
 * persone con cui si è condiviso il viaggio. Restano fuori le mappe scaricate, i km delle pietre miliari e i prezzi
 * (si riscaricano), il cestino dei segnalibri, le segnalazioni in guida (valgono due ore) e l'interruttore della ricerca
 * online (lo decide il centro privacy di Eurise).
 *
 * <p>Al ripristino non si perde niente: un elenco che manca arriva; uguale, si salta; con lo stesso nome ma diverso,
 * arriva accanto come «nome (dal backup).kml». Poi Mappe si chiude e riparte, così ricarica i segnalibri dai file.
 *
 * <p>Il fornitore è dichiarato {@code exported} con lettura e scrittura protette dal permesso della suite della variante
 * ({@link EuriseSuite#PERMISSION}); qui si controlla di nuovo chi chiama, perché {@code call} non passa dal manifesto.
 */
public class EuriseBackupProvider extends ContentProvider
{
  private static final String SETTINGS = "settings.ini";
  private static final String PREFS = "prefs/eurise.json";
  private static final String TRIP = "prefs/eurise_trip.json";
  /** Le chiavi di Eurise nelle preferenze di CoMaps: tutte quelle con questo prefisso. */
  private static final String PREFIX = "eurise_";
  /** In eurise_trip: le persone con cui si è condiviso il viaggio. */
  private static final Set<String> TRIP_KEYS = new HashSet<>(Arrays.asList("recent"));
  /** Si aspetta fino a dieci minuti (molte tracce lunghe). */
  private static final long APPLY_WAIT = 10 * 60 * 1000L;

  private static final Object sLock = new Object();
  @Nullable
  private static Thread sRestoring;
  @Nullable
  private static Boolean sResult;

  @Override
  public boolean onCreate()
  {
    return true;
  }

  private static void enforceCaller(@NonNull Context c)
  {
    // Solo l'app Eurise della variante: il backup non va a un'altra app della suite (docs/SICUREZZA.md, «Backup»).
    if (c.checkCallingPermission(EuriseSuite.PERMISSION) != PackageManager.PERMISSION_GRANTED)
      throw new SecurityException("Eurise: solo le app della suite");
    final String[] pkgs = c.getPackageManager().getPackagesForUid(android.os.Binder.getCallingUid());
    if (pkgs == null || !java.util.Arrays.asList(pkgs).contains(EuriseSuite.HUB))
      throw new SecurityException("Eurise: solo l'app Eurise");
  }

  @Nullable
  @Override
  public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException
  {
    final Context c = getContext();
    if (c == null)
      throw new FileNotFoundException();
    enforceCaller(c);
    final String last = uri.getLastPathSegment();
    final ParcelFileDescriptor[] pipe;
    try
    {
      pipe = ParcelFileDescriptor.createPipe();
    }
    catch (IOException e)
    {
      throw new FileNotFoundException(e.getMessage());
    }
    if ("export".equals(last))
    {
      new Thread(() -> {
        try (ZipOutputStream zip = new ZipOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])))
        {
          export(c, zip);
        }
        catch (Exception e)
        {
          try
          {
            pipe[1].closeWithError(e.getMessage() == null ? "export" : e.getMessage());
          }
          catch (IOException ignored)
          {
          }
        }
      }, "eurise-backup-export").start();
      return pipe[0];
    }
    if ("import".equals(last))
    {
      final Thread worker = new Thread(() -> {
        boolean ok;
        try (ZipInputStream zip = new ZipInputStream(new ParcelFileDescriptor.AutoCloseInputStream(pipe[0])))
        {
          restore(c, zip);
          ok = true;
        }
        catch (Exception e)
        {
          ok = false;
        }
        synchronized (sLock)
        {
          sResult = ok;
        }
      }, "eurise-backup-import");
      synchronized (sLock)
      {
        sResult = null;
        sRestoring = worker;
      }
      worker.start();
      return pipe[1];
    }
    try
    {
      pipe[0].close();
      pipe[1].close();
    }
    catch (IOException ignored)
    {
    }
    throw new FileNotFoundException(uri.toString());
  }

  @Nullable
  @Override
  public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras)
  {
    final Context c = getContext();
    if (c == null)
      return null;
    enforceCaller(c);
    final Bundle out = new Bundle();
    switch (method)
    {
    case "summary":
      String line = null;
      try
      {
        line = summary(c);
      }
      catch (Exception ignored)
      {
      }
      out.putString("summary", line);
      return out;
    case "ready":
      out.putBoolean("ready", true);
      return out;
    case "apply":
      final Thread worker;
      synchronized (sLock)
      {
        worker = sRestoring;
      }
      if (worker != null)
      {
        try
        {
          worker.join(APPLY_WAIT);
        }
        catch (InterruptedException e)
        {
          Thread.currentThread().interrupt();
        }
      }
      final boolean ok;
      synchronized (sLock)
      {
        ok = Boolean.TRUE.equals(sResult);
        sResult = null;
        sRestoring = null;
      }
      // CoMaps tiene i segnalibri in memoria: ripartendo li ricarica dai file appena scritti.
      if (ok)
        new Handler(Looper.getMainLooper()).postDelayed(() -> Process.killProcess(Process.myPid()), 300);
      out.putBoolean("ok", ok);
      return out;
    default:
      return null;
    }
  }

  // ------------------------------------------------------------------ cosa entra

  /** La cartella dei segnalibri di CoMaps: {@code GetPlatform().SettingsDir()/bookmarks}, cioè i file dell'app. */
  @NonNull
  static File bookmarksDir(@NonNull Context c)
  {
    return new File(c.getFilesDir(), "bookmarks");
  }

  @NonNull
  private static File[] kmlFiles(@NonNull Context c)
  {
    final File[] all = bookmarksDir(c).listFiles(f -> f.isFile() && BookmarkFiles.isList(f.getName()));
    return all == null ? new File[0] : all;
  }

  /** «3 elenchi, 42 luoghi e tracce», o null se non ce n'è. */
  @Nullable
  private static String summary(@NonNull Context c) throws IOException
  {
    final File[] files = kmlFiles(c);
    if (files.length == 0)
      return null;
    int places = 0;
    for (File f : files)
      places += BookmarkFiles.countPlacemarks(f);
    return c.getString(R.string.eurise_backup_summary, files.length, places);
  }

  private static void export(@NonNull Context c, @NonNull ZipOutputStream zip) throws IOException
  {
    for (File f : kmlFiles(c))
      addFile(zip, f, BookmarkFiles.PREFIX + f.getName());
    addFile(zip, new File(c.getFilesDir(), SETTINGS), SETTINGS);
    put(zip, PREFS, exportKeys(PreferenceManager.getDefaultSharedPreferences(c), null));
    put(zip, TRIP, exportKeys(c.getSharedPreferences("eurise_trip", Context.MODE_PRIVATE), TRIP_KEYS));
  }

  private static void restore(@NonNull Context c, @NonNull ZipInputStream zip) throws Exception
  {
    ZipEntry e;
    while ((e = zip.getNextEntry()) != null)
    {
      final String name = safeName(e);
      if (name == null)
        continue;
      if (name.startsWith(BookmarkFiles.PREFIX))
      {
        final String file = BookmarkFiles.fileName(name);
        if (file != null)
          restoreBookmarks(c, zip, file);
      }
      else if (name.equals(SETTINGS))
        writeEntry(zip, new File(c.getFilesDir(), SETTINGS));
      else if (name.equals(PREFS))
        restoreKeys(PreferenceManager.getDefaultSharedPreferences(c), readText(zip), null);
      else if (name.equals(TRIP))
        restoreKeys(c.getSharedPreferences("eurise_trip", Context.MODE_PRIVATE), readText(zip), TRIP_KEYS);
    }
  }

  /** Un elenco di segnalibri: arriva se manca, si salta se è uguale, va accanto «(dal backup)» se è diverso. */
  private static void restoreBookmarks(@NonNull Context c, @NonNull ZipInputStream zip, @NonNull String file)
      throws IOException
  {
    final File dir = bookmarksDir(c);
    if (!dir.isDirectory() && !dir.mkdirs())
      throw new IOException("non si crea la cartella dei segnalibri");
    final File staged = new File(dir, BookmarkFiles.STAGING);
    BookmarkFiles.copy(zip, staged);
    BookmarkFiles.place(dir, staged, file);
  }

  // ------------------------------------------------------------------ attrezzi (come SuiteBackupProvider)

  /** Il nome di una voce, o null se prova a uscire dalla cartella. */
  @Nullable
  static String safeName(@NonNull ZipEntry e)
  {
    final String n = e.getName();
    if (e.isDirectory() || n.contains("..") || n.startsWith("/") || n.contains("\\"))
      return null;
    return n;
  }

  private static void addFile(@NonNull ZipOutputStream zip, @NonNull File f, @NonNull String name) throws IOException
  {
    if (!f.isFile())
      return;
    zip.putNextEntry(new ZipEntry(name));
    try (InputStream in = new FileInputStream(f))
    {
      BookmarkFiles.pump(in, zip);
    }
    zip.closeEntry();
  }

  private static void put(@NonNull ZipOutputStream zip, @NonNull String name, @NonNull byte[] data) throws IOException
  {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(data);
    zip.closeEntry();
  }

  /** Copia la voce corrente in [target] passando da un file accanto, rinominato alla fine. */
  private static void writeEntry(@NonNull ZipInputStream zip, @NonNull File target) throws IOException
  {
    final File tmp = new File(target.getPath() + ".ripristino");
    BookmarkFiles.copy(zip, tmp);
    BookmarkFiles.move(tmp, target);
  }

  @NonNull
  private static String readText(@NonNull InputStream in) throws IOException
  {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    BookmarkFiles.pump(in, out);
    return out.toString("UTF-8");
  }

  /** Le chiavi scelte ([keys], o tutte quelle «eurise_» se null) con il loro tipo, come SuiteBackupProvider. */
  @NonNull
  static byte[] exportKeys(@NonNull SharedPreferences prefs, @Nullable Set<String> keys)
  {
    final JSONObject out = new JSONObject();
    try
    {
      for (Map.Entry<String, ?> en : prefs.getAll().entrySet())
      {
        final String k = en.getKey();
        if (!wanted(k, keys))
          continue;
        final Object v = en.getValue();
        final JSONObject t = new JSONObject();
        if (v instanceof Boolean)
          t.put("b", v);
        else if (v instanceof Integer)
          t.put("i", v);
        else if (v instanceof Long)
          t.put("l", v);
        else if (v instanceof Float)
          t.put("f", ((Float) v).doubleValue());
        else if (v instanceof String)
          t.put("s", v);
        else if (v instanceof Set)
        {
          final JSONArray a = new JSONArray();
          for (Object o : (Set<?>) v)
            a.put(String.valueOf(o));
          t.put("ss", a);
        }
        else
          continue;
        out.put(k, t);
      }
    }
    catch (Exception ignored)
    {
    }
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  /** Rimette le chiavi scritte da [exportKeys], solo quelle ammesse. */
  static void restoreKeys(@NonNull SharedPreferences prefs, @NonNull String json, @Nullable Set<String> keys)
      throws Exception
  {
    final JSONObject o = new JSONObject(json);
    final SharedPreferences.Editor edit = prefs.edit();
    for (Iterator<String> it = o.keys(); it.hasNext();)
    {
      final String k = it.next();
      if (!wanted(k, keys))
        continue;
      final JSONObject t = o.getJSONObject(k);
      if (t.has("b"))
        edit.putBoolean(k, t.getBoolean("b"));
      else if (t.has("i"))
        edit.putInt(k, t.getInt("i"));
      else if (t.has("l"))
        edit.putLong(k, t.getLong("l"));
      else if (t.has("f"))
        edit.putFloat(k, (float) t.getDouble("f"));
      else if (t.has("s"))
        edit.putString(k, t.getString("s"));
      else if (t.has("ss"))
      {
        final JSONArray a = t.getJSONArray("ss");
        final Set<String> s = new HashSet<>();
        for (int i = 0; i < a.length(); i++)
          s.add(a.getString(i));
        edit.putStringSet(k, s);
      }
    }
    edit.commit();
  }

  private static boolean wanted(@NonNull String key, @Nullable Set<String> keys)
  {
    return keys == null ? key.startsWith(PREFIX) : keys.contains(key);
  }

  // ------------------------------------------------------------------ il resto non serve

  @Nullable
  @Override
  public Cursor query(@NonNull Uri uri, @Nullable String[] p, @Nullable String s, @Nullable String[] a,
                      @Nullable String o)
  {
    return null;
  }

  @Nullable
  @Override
  public String getType(@NonNull Uri uri)
  {
    return "export".equals(uri.getLastPathSegment()) ? "application/zip" : null;
  }

  @Nullable
  @Override
  public Uri insert(@NonNull Uri uri, @Nullable ContentValues values)
  {
    return null;
  }

  @Override
  public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs)
  {
    return 0;
  }

  @Override
  public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
                    @Nullable String[] selectionArgs)
  {
    return 0;
  }
}

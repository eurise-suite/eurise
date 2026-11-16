package app.organicmaps.eurise;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/**
 * Il testo della canzone nella bolla in guida, riga per riga (tavola MapBollaAuto). Spotify e le altre app non danno il
 * testo alle altre app, quindi viene da LRCLIB (lrclib.net, archivio libero di testi sincronizzati) solo con il servizio
 * «Testi delle canzoni» acceso nel centro privacy dell'app Eurise (PrivacyService.Lyrics, spento di base). Esce solo ciò
 * che serve a trovare il testo: titolo, autore, album e durata; nessuna posizione né identificativo. I testi trovati
 * restano in memoria per le ultime 40 canzoni.
 */
public final class EuriseLyrics
{
  private static final String SERVICE = "Lyrics";
  private static final String[] HUBS = {"eu.eurise.hub.debug", "eu.eurise.hub"};
  private static final ExecutorService IO = Executors.newSingleThreadExecutor();
  private static final NowPlayingText.Lyrics NONE = NowPlayingText.parseLrc(null);

  private static final Map<String, NowPlayingText.Lyrics> CACHE = new LinkedHashMap<String, NowPlayingText.Lyrics>(48, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, NowPlayingText.Lyrics> eldest)
    {
      return size() > 40;
    }
  };

  @Nullable private static volatile String sKey;

  private EuriseLyrics() {}

  /** Il testo della canzone [m], se è già arrivato; null se non c'è (o il servizio è spento). */
  @Nullable
  public static NowPlayingText.Lyrics of(@Nullable EuriseNowPlaying.Media m)
  {
    if (m == null)
      return null;
    synchronized (CACHE)
    {
      final NowPlayingText.Lyrics l = CACHE.get(m.key());
      return l == null || l.isEmpty() ? null : l;
    }
  }

  /** Canzone nuova: cerca il testo, se il servizio è acceso. Dal thread principale. */
  static void onTrack(@NonNull Context c, @NonNull EuriseNowPlaying.Media m)
  {
    final String key = m.key();
    synchronized (CACHE)
    {
      if (CACHE.containsKey(key))
        return;
    }
    sKey = key;
    final Context app = c.getApplicationContext();
    IO.execute(() -> {
      if (!key.equals(sKey) || !Boolean.TRUE.equals(privacy(app, "status")))
        return;
      final NowPlayingText.Lyrics l = fetch(m);
      privacy(app, "log");
      // La bolla rilegge il testo a ogni battito: basta metterlo qui.
      synchronized (CACHE)
      {
        CACHE.put(key, l == null ? NONE : l);
      }
    });
  }

  @Nullable
  private static NowPlayingText.Lyrics fetch(@NonNull EuriseNowPlaying.Media m)
  {
    final Uri.Builder u = Uri.parse("https://lrclib.net/api/get").buildUpon()
                              .appendQueryParameter("track_name", m.title)
                              .appendQueryParameter("artist_name", m.artist == null ? "" : m.artist);
    if (m.album != null)
      u.appendQueryParameter("album_name", m.album);
    if (m.durationMs > 0)
      u.appendQueryParameter("duration", Long.toString(Math.round(m.durationMs / 1000.0)));
    HttpURLConnection conn = null;
    try
    {
      conn = (HttpURLConnection) new URL(u.build().toString()).openConnection();
      conn.setConnectTimeout(8_000);
      conn.setReadTimeout(10_000);
      conn.setRequestProperty("User-Agent", "EuriseMappe (https://github.com/eurise-suite/eurise)");
      if (conn.getResponseCode() != 200)
        return null;
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (InputStream in = conn.getInputStream())
      {
        final byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0 && out.size() < 512 * 1024)
          out.write(buf, 0, n);
      }
      final JSONObject o = new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
      if (o.optBoolean("instrumental"))
        return null;
      return NowPlayingText.parseLrc(o.isNull("syncedLyrics") ? null : o.optString("syncedLyrics"));
    }
    catch (Exception e)
    {
      return null;
    }
    finally
    {
      if (conn != null)
        conn.disconnect();
    }
  }

  /** Stato, accensione, spegnimento e registro del servizio nel centro privacy dell'app Eurise; null se l'app non c'è. */
  @Nullable
  public static Boolean privacy(@NonNull Context c, @NonNull String method)
  {
    for (String h : HUBS)
    {
      try
      {
        final Bundle b = c.getContentResolver().call(Uri.parse("content://" + h + ".privacy"), method, SERVICE, null);
        if (b != null)
          return b.getBoolean("on");
      }
      catch (Exception ignored)
      {
        // App Eurise assente, di un'altra firma o di una versione senza questo servizio.
      }
    }
    return null;
  }
}

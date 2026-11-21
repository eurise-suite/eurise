package app.organicmaps.eurise;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * I segnalibri di Mappe nel backup, senza Android (si prova con {@code BookmarkFilesTest} nel lavoro di Mappe):
 * quali voci dello zip sono elenchi accettabili, quanti luoghi e tracce ci sono, e dove va un elenco al ripristino
 * senza perdere quello che c'è già. Lo usa {@link EuriseBackupProvider}.
 */
public final class BookmarkFiles
{
  public static final String PREFIX = "bookmarks/";
  public static final String KML = ".kml";
  /** Il file d'appoggio: non finisce in .kml, così CoMaps non lo prende per un elenco se riparte a metà. */
  public static final String STAGING = ".eurise-ripristino";

  private BookmarkFiles() {}

  /** Il nome del file per la voce [entry] dello zip ({@code bookmarks/<nome>.kml}), o null se non è un elenco. */
  public static String fileName(String entry)
  {
    if (entry == null || !entry.startsWith(PREFIX))
      return null;
    final String f = entry.substring(PREFIX.length());
    if (f.isEmpty() || f.contains("/") || f.contains("\\") || f.contains("..") || f.startsWith(".")
        || !f.toLowerCase().endsWith(KML) || f.length() == KML.length())
      return null;
    return f;
  }

  /** Vero se [name] è un elenco che CoMaps carica (i .kml della cartella, non nascosti). */
  public static boolean isList(String name)
  {
    return !name.startsWith(".") && name.toLowerCase().endsWith(KML) && name.length() > KML.length();
  }

  /** Quante volte compare {@code <Placemark} (un luogo o una traccia ciascuno), leggendo a pezzi. */
  public static int countPlacemarks(InputStream in) throws IOException
  {
    final byte[] needle = "<Placemark".getBytes(StandardCharsets.US_ASCII);
    final byte[] buf = new byte[64 * 1024];
    int count = 0, matched = 0, n;
    while ((n = in.read(buf)) > 0)
    {
      for (int i = 0; i < n; i++)
      {
        if (buf[i] == needle[matched])
        {
          if (++matched == needle.length)
          {
            count++;
            matched = 0;
          }
        }
        else
          matched = buf[i] == needle[0] ? 1 : 0;
      }
    }
    return count;
  }

  public static int countPlacemarks(File f) throws IOException
  {
    try (InputStream in = new FileInputStream(f))
    {
      return countPlacemarks(in);
    }
  }

  /**
   * Mette [staged] (l'elenco del backup, già scritto in [dir]) al suo posto come [name]: se manca arriva; se c'è uguale
   * si scarta; se c'è diverso va accanto come «nome (dal backup).kml» (poi «(dal backup 2)»…), a meno che una copia
   * uguale ci sia già. Restituisce il file dove è finito, o quello uguale che c'era.
   */
  public static File place(File dir, File staged, String name) throws IOException
  {
    final File target = new File(dir, name);
    if (!target.exists())
    {
      move(staged, target);
      return target;
    }
    if (sameContent(staged, target))
    {
      staged.delete();
      return target;
    }
    final String base = name.substring(0, name.length() - KML.length());
    for (int i = 1;; i++)
    {
      final File other = new File(dir, base + (i == 1 ? " (dal backup)" : " (dal backup " + i + ")") + KML);
      if (!other.exists())
      {
        move(staged, other);
        return other;
      }
      if (sameContent(staged, other))
      {
        staged.delete();
        return other;
      }
    }
  }

  public static void copy(InputStream in, File to) throws IOException
  {
    final File parent = to.getParentFile();
    if (parent != null && !parent.isDirectory() && !parent.mkdirs())
      throw new IOException("non si crea " + parent.getName());
    try (OutputStream out = new FileOutputStream(to))
    {
      pump(in, out);
    }
  }

  public static void move(File from, File to) throws IOException
  {
    to.delete();
    if (!from.renameTo(to))
    {
      from.delete();
      throw new IOException("non si scrive " + to.getName());
    }
  }

  public static void pump(InputStream in, OutputStream out) throws IOException
  {
    final byte[] buf = new byte[64 * 1024];
    int n;
    while ((n = in.read(buf)) > 0)
      out.write(buf, 0, n);
  }

  public static boolean sameContent(File a, File b) throws IOException
  {
    if (a.length() != b.length())
      return false;
    try (InputStream x = new FileInputStream(a); InputStream y = new FileInputStream(b))
    {
      final byte[] bx = new byte[64 * 1024], by = new byte[64 * 1024];
      int n;
      while ((n = x.read(bx)) > 0)
      {
        int read = 0;
        while (read < n)
        {
          final int m = y.read(by, read, n - read);
          if (m <= 0)
            return false;
          read += m;
        }
        for (int i = 0; i < n; i++)
          if (bx[i] != by[i])
            return false;
      }
    }
    return true;
  }
}

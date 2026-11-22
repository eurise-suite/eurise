package app.organicmaps.eurise;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Prove di {@link BookmarkFiles} senza telefono (le lancia il lavoro di Mappe, come {@link MilestoneKmTest}). */
public final class BookmarkFilesTest
{
  private static int failures;

  public static void main(String[] args) throws IOException
  {
    eq("elenco", BookmarkFiles.fileName("bookmarks/I miei luoghi.kml"), "I miei luoghi.kml");
    eq("maiuscole", BookmarkFiles.fileName("bookmarks/Giro.KML"), "Giro.KML");
    eq("fuori dalla cartella", BookmarkFiles.fileName("bookmarks/../settings.ini"), null);
    eq("sottocartella", BookmarkFiles.fileName("bookmarks/a/b.kml"), null);
    eq("nascosto", BookmarkFiles.fileName("bookmarks/.eurise-ripristino"), null);
    eq("non kml", BookmarkFiles.fileName("bookmarks/traccia.gpx"), null);
    eq("solo estensione", BookmarkFiles.fileName("bookmarks/.kml"), null);
    eq("altra voce", BookmarkFiles.fileName("settings.ini"), null);
    check("d'appoggio non è un elenco", !BookmarkFiles.isList(BookmarkFiles.STAGING));

    final String kml = "<kml><Document><Placemark><name>Casa</name></Placemark>"
                       + "<<Placemark><name>Lavoro</name></Placemark><Placemark><LineString/></Placemark></Document></kml>";
    eq("luoghi e tracce", BookmarkFiles.countPlacemarks(new ByteArrayInputStream(kml.getBytes(StandardCharsets.UTF_8))), 3);
    // Un «<Placemark» a cavallo di due letture da 64 KiB si conta lo stesso.
    final StringBuilder big = new StringBuilder();
    while (big.length() < 64 * 1024 - 4)
      big.append(' ');
    big.append("<Placemark>");
    eq("a cavallo", BookmarkFiles.countPlacemarks(new ByteArrayInputStream(big.toString().getBytes(StandardCharsets.US_ASCII))), 1);

    final File dir = Files.createTempDirectory("segnalibri").toFile();
    write(new File(dir, "Viaggi.kml"), "A");
    // Uguale: si scarta, niente doppioni.
    eq("uguale", BookmarkFiles.place(dir, staged(dir, "A"), "Viaggi.kml").getName(), "Viaggi.kml");
    check("niente d'appoggio", !new File(dir, BookmarkFiles.STAGING).exists());
    // Diverso: accanto.
    eq("diverso", BookmarkFiles.place(dir, staged(dir, "B"), "Viaggi.kml").getName(), "Viaggi (dal backup).kml");
    eq("quello di prima resta", read(new File(dir, "Viaggi.kml")), "A");
    // Ancora diverso: il secondo accanto; lo stesso di prima: non si doppia.
    eq("terzo", BookmarkFiles.place(dir, staged(dir, "C"), "Viaggi.kml").getName(), "Viaggi (dal backup 2).kml");
    eq("già accanto", BookmarkFiles.place(dir, staged(dir, "B"), "Viaggi.kml").getName(), "Viaggi (dal backup).kml");
    // Manca: arriva col suo nome.
    eq("nuovo", BookmarkFiles.place(dir, staged(dir, "D"), "Nuovo.kml").getName(), "Nuovo.kml");
    eq("contenuto", read(new File(dir, "Nuovo.kml")), "D");
    final String[] left = dir.list();
    eq("file nella cartella", left == null ? -1 : left.length, 4);

    if (failures > 0)
    {
      System.err.println("BookmarkFilesTest: " + failures + " prove fallite");
      System.exit(1);
    }
    System.out.println("BookmarkFilesTest: tutte le prove passate");
  }

  private static File staged(File dir, String text) throws IOException
  {
    final File f = new File(dir, BookmarkFiles.STAGING);
    write(f, text);
    return f;
  }

  private static void write(File f, String text) throws IOException
  {
    Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
  }

  private static String read(File f) throws IOException
  {
    return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
  }

  private static void eq(String what, Object got, Object want)
  {
    if (got == null ? want != null : !got.equals(want))
    {
      failures++;
      System.err.println("NO  " + what + ": " + got + " invece di " + want);
    }
  }

  private static void check(String what, boolean ok)
  {
    if (!ok)
    {
      failures++;
      System.err.println("NO  " + what);
    }
  }
}

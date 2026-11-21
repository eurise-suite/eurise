package app.organicmaps.eurise;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Chi è della suite, per Mappe, in un posto solo (come core/suite SuiteAccess; docs/SICUREZZA.md, «Mappe»).
 *
 * <p>Mappe ha lo stesso pacchetto (eu.eurise.maps) in prova e in ufficiale, quindi la variante non si deduce dal nome:
 * la scrive maps/eurise_patch.py in {@link #OFFICIAL} secondo EURISE_VARIANTE («prova» di base, «ufficiale»). Ogni
 * variante conosce un solo permesso e un solo nome per app: le prove parlano solo con le prove, le ufficiali solo con le
 * ufficiali. Mai tutti e due: nelle ufficiali nessuna app dichiara SUITE_DEBUG, e un'app qualunque potrebbe dichiararlo
 * da sé come permesso «normale».
 *
 * <p>Prima di mandare qualcosa (una chiamata a un fornitore, una nota, un SMS, un'attività, un testo da leggere) si
 * controlla che il destinatario abbia il permesso di firma della suite: se l'app vera manca, un'app estranea con lo stesso
 * nome di pacchetto o di fornitore non riceve nulla.
 */
public final class EuriseSuite
{
  /** Variante della compilazione: la riga la riscrive eurise_patch.py. Nel repository resta la prova. */
  public static final boolean OFFICIAL = false;

  /** Il permesso di firma della suite per questa variante. */
  public static final String PERMISSION = OFFICIAL ? "eu.eurise.permission.SUITE" : "eu.eurise.permission.SUITE_DEBUG";

  /** L'app Eurise (centro privacy, impegni, note, attività, i tuoi giorni, meteo sul percorso). */
  public static final String HUB = pkg("eu.eurise.hub");
  /** Eurise Messaggi (SMS di «Avvisa» e dell'arrivo condiviso). */
  public static final String MESSAGES = pkg("eu.eurise.messages");
  /** Eurise Voce (dettatura in auto, voce di Alfred). */
  public static final String VOICE = pkg("eu.eurise.voice");

  /** Per i cicli che c'erano già («si prova l'altra»): ora c'è una sola app Eurise, quella della variante. */
  public static final String[] HUBS = {HUB};

  private EuriseSuite() {}

  private static String pkg(String base)
  {
    return OFFICIAL ? base : base + ".debug";
  }

  /** Vero se [pkg] è installato e ha il permesso di firma della suite (cioè è firmato con la nostra chiave). */
  public static boolean isSuite(@NonNull Context c, @Nullable String pkg)
  {
    if (pkg == null || pkg.isEmpty())
      return false;
    if (pkg.equals(c.getPackageName()))
      return true;
    try
    {
      return c.getPackageManager().checkPermission(PERMISSION, pkg) == PackageManager.PERMISSION_GRANTED;
    }
    catch (Exception e)
    {
      return false;
    }
  }

  /** Vero se il fornitore con [authority] appartiene a un'app della suite. */
  public static boolean providerTrusted(@NonNull Context c, @Nullable String authority)
  {
    if (authority == null || authority.isEmpty())
      return false;
    try
    {
      final ProviderInfo info = c.getPackageManager().resolveContentProvider(authority, 0);
      return info != null && isSuite(c, info.packageName);
    }
    catch (Exception e)
    {
      return false;
    }
  }

  /** {@code ContentResolver.call} solo verso un fornitore della suite; null altrimenti, come se l'app mancasse. */
  @Nullable
  public static Bundle call(@NonNull Context c, @NonNull Uri uri, @NonNull String method, @Nullable String arg,
                            @Nullable Bundle extras)
  {
    if (!providerTrusted(c, uri.getAuthority()))
      return null;
    return c.getContentResolver().call(uri, method, arg, extras);
  }

  /** L'intent se va (per componente o pacchetto) a un'app della suite; null altrimenti. */
  @Nullable
  public static Intent trusted(@NonNull Context c, @NonNull Intent i)
  {
    final String pkg = i.getComponent() != null ? i.getComponent().getPackageName() : i.getPackage();
    return isSuite(c, pkg) ? i : null;
  }
}

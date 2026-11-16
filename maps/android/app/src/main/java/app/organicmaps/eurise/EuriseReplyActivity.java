package app.organicmaps.eurise;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import java.util.ArrayList;

/**
 * Due passaggi del telefono che vogliono un'attività, senza schermata propria (trasparente):
 * <ul>
 *   <li>la risposta a voce a un messaggio (tavola MapBollaMessaggio): il riconoscimento vocale del telefono (Eurise Voce,
 *   se c'è), poi la frase torna alla bolla, che la fa rileggere ad Alfred prima di mandarla;</li>
 *   <li>«Condividi l'arrivo» (tavola MapBollaArrivo): la Rubrica del telefono per scegliere la persona. Mappe non ha il
 *   permesso dei contatti: legge solo nome e numero della persona scelta, con il permesso dato per quella scelta.</li>
 * </ul>
 */
public final class EuriseReplyActivity extends Activity
{
  private static final String EXTRA_MODE = "mode", EXTRA_PROMPT = "prompt";
  private static final int MODE_LISTEN = 1, MODE_CONTACT = 2;
  private static final int REQ = 7301;
  @Nullable private static EuriseTrip.Text sThen;

  /** Ascolta una frase e la passa a [then]; se il telefono non sa riconoscere la voce, lo dice. */
  public static void listen(@NonNull Context c, @NonNull String prompt, @NonNull EuriseTrip.Text then)
  {
    sThen = then;
    c.startActivity(new Intent(c, EuriseReplyActivity.class).putExtra(EXTRA_MODE, MODE_LISTEN)
                        .putExtra(EXTRA_PROMPT, prompt).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  /** Sceglie una persona dalla Rubrica e comincia a condividere l'arrivo con lei. */
  public static void pickForShare(@NonNull Context c)
  {
    c.startActivity(new Intent(c, EuriseReplyActivity.class).putExtra(EXTRA_MODE, MODE_CONTACT)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }

  @Override
  protected void onCreate(@Nullable Bundle state)
  {
    super.onCreate(state);
    if (state != null)
      return;
    final int mode = getIntent().getIntExtra(EXTRA_MODE, MODE_LISTEN);
    try
    {
      if (mode == MODE_CONTACT)
        startActivityForResult(new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI), REQ);
      else
        startActivityForResult(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                   .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                   .putExtra(RecognizerIntent.EXTRA_PROMPT, getIntent().getStringExtra(EXTRA_PROMPT))
                                   .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true),
                               REQ);
    }
    catch (ActivityNotFoundException e)
    {
      Toast.makeText(this, mode == MODE_CONTACT ? R.string.eurise_trip_no_contacts : R.string.eurise_trip_no_voice,
                     Toast.LENGTH_LONG).show();
      finish();
    }
  }

  @Override
  protected void onActivityResult(int request, int result, @Nullable Intent data)
  {
    super.onActivityResult(request, result, data);
    final int mode = getIntent().getIntExtra(EXTRA_MODE, MODE_LISTEN);
    if (request == REQ && result == RESULT_OK && data != null)
    {
      if (mode == MODE_CONTACT)
        contact(data.getData());
      else
      {
        final ArrayList<String> heard = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        final EuriseTrip.Text then = sThen;
        if (heard != null && !heard.isEmpty() && then != null && !heard.get(0).trim().isEmpty())
          then.onText(heard.get(0).trim());
      }
    }
    sThen = null;
    finish();
  }

  private void contact(@Nullable Uri uri)
  {
    if (uri == null)
      return;
    try (Cursor c = getContentResolver().query(uri, new String[] {ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                                                                  ContactsContract.CommonDataKinds.Phone.NUMBER},
                                               null, null, null))
    {
      if (c == null || !c.moveToFirst())
        return;
      final String name = c.getString(0);
      final String number = c.getString(1);
      if (number == null || number.trim().isEmpty())
        return;
      EuriseTrip.startShare(this, name == null || name.trim().isEmpty() ? number : name.trim(), number.trim());
      Toast.makeText(this, getString(app.organicmaps.sdk.routing.RoutingController.get().isNavigating()
                                         ? R.string.eurise_trip_share_started : R.string.eurise_trip_share_waiting,
                                     name == null ? number : name),
                     Toast.LENGTH_LONG).show();
    }
    catch (RuntimeException ignored)
    {
      // La Rubrica non ha dato il permesso per la scelta.
    }
  }
}

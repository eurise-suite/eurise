package app.organicmaps.eurise;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.annotation.Nullable;

/**
 * L'accesso alle notifiche che la persona dà a Mappe nelle impostazioni del telefono, solo per la bolla della musica e
 * delle chiamate in guida ({@link EuriseNowPlaying}). Lo avvia il sistema quando l'accesso c'è; con l'interruttore di
 * Livelli spento non legge nulla. Le notifiche che non sono chiamate si scartano senza guardarle.
 */
public final class EuriseNowPlayingService extends NotificationListenerService
{
  @Nullable static volatile EuriseNowPlayingService sInstance;

  @Override
  public void onListenerConnected()
  {
    sInstance = this;
    EuriseNowPlaying.onConnected(this);
  }

  @Override
  public void onListenerDisconnected()
  {
    sInstance = null;
    EuriseNowPlaying.onDisconnected();
  }

  @Override
  public void onNotificationPosted(StatusBarNotification sbn)
  {
    EuriseNowPlaying.onNotification(sbn, false);
  }

  @Override
  public void onNotificationRemoved(StatusBarNotification sbn)
  {
    EuriseNowPlaying.onNotification(sbn, true);
  }
}

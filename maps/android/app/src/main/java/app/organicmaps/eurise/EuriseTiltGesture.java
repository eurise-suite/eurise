package app.organicmaps.eurise;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;

import app.organicmaps.sdk.routing.RoutingController;

/**
 * Il gesto a due dita per il 3D mentre si esplora (come nelle altre mappe): due dita che salgono insieme piegano la mappa
 * fino al cielo, due dita che scendono la rimettono piatta. Fa la stessa cosa del tasto «3D»/«2D» ({@link EuriseTilt}).
 *
 * <p>CoMaps non ha questo gesto e il motore riceve i tocchi in un punto che le patch non raggiungono con sicurezza: il
 * gesto si riconosce prima, nella finestra (si avvolge il suo {@link Window.Callback}), e i tocchi passano alla mappa
 * come sempre. Solo quando il gesto è riconosciuto la mappa riceve un «annulla» (così non zooma né ruota) e il resto di
 * quel tocco a due dita non le arriva. Pizzicare (dita che si allontanano o si avvicinano) e ruotare restano della mappa.
 */
public final class EuriseTiltGesture
{
  /** Quanto devono salire o scendere insieme le due dita, in dp. */
  private static final float TRAVEL_DP = 70;
  /** Di quanto può cambiare la distanza tra le dita (oltre è un pizzico), in dp. */
  private static final float SPREAD_DP = 45;
  /** Le dita devono partire quasi insieme: un tocco a due dita, non un dito che arriva dopo. */
  private static final long SECOND_FINGER_MS = 250;

  private final float mDensity;
  private boolean mTracking, mDone;
  private float mY0, mY1, mX0, mX1, mSpread;
  private long mDownAt;

  private EuriseTiltGesture(@NonNull Context c)
  {
    mDensity = c.getResources().getDisplayMetrics().density;
  }

  /** Mette il gesto sulla finestra dell'attività (una volta sola). */
  public static void install(@NonNull Context c)
  {
    final Activity a = activity(c);
    if (a == null)
      return;
    final Window w = a.getWindow();
    final Window.Callback orig = w.getCallback();
    if (orig == null || Proxy.isProxyClass(orig.getClass()))
      return;
    final EuriseTiltGesture g = new EuriseTiltGesture(a);
    final InvocationHandler h = (proxy, method, args) -> {
      if ("dispatchTouchEvent".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof MotionEvent)
      {
        final MotionEvent e = (MotionEvent) args[0];
        if (g.consume(a, e))
        {
          if (!g.mCancelSent)
          {
            g.mCancelSent = true;
            final MotionEvent cancel = MotionEvent.obtain(e);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            orig.dispatchTouchEvent(cancel);
            cancel.recycle();
          }
          return true;
        }
      }
      try
      {
        return method.invoke(orig, args);
      }
      catch (InvocationTargetException ex)
      {
        throw ex.getCause() != null ? ex.getCause() : ex;
      }
    };
    w.setCallback((Window.Callback) Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),
                                                           new Class<?>[] {Window.Callback.class}, h));
  }

  private boolean mCancelSent;

  /** Vero se l'evento fa parte di un gesto riconosciuto: non va alla mappa. */
  private boolean consume(@NonNull Context c, @NonNull MotionEvent e)
  {
    switch (e.getActionMasked())
    {
    case MotionEvent.ACTION_DOWN:
      mTracking = false;
      mDone = false;
      mCancelSent = false;
      mDownAt = SystemClock.uptimeMillis();
      return false;
    case MotionEvent.ACTION_POINTER_DOWN:
      if (e.getPointerCount() == 2 && !mDone && SystemClock.uptimeMillis() - mDownAt < SECOND_FINGER_MS && allowed())
      {
        mTracking = true;
        mX0 = e.getX(0); mY0 = e.getY(0);
        mX1 = e.getX(1); mY1 = e.getY(1);
        mSpread = spread(e);
      }
      else
        mTracking = false;
      return mDone;
    case MotionEvent.ACTION_MOVE:
      if (mDone)
        return true;
      if (!mTracking || e.getPointerCount() != 2)
        return false;
      final float d0 = e.getY(0) - mY0, d1 = e.getY(1) - mY1;
      final float travel = TRAVEL_DP * mDensity;
      if (Math.abs(spread(e) - mSpread) > SPREAD_DP * mDensity
          || Math.abs(e.getX(0) - mX0) > Math.abs(d0) || Math.abs(e.getX(1) - mX1) > Math.abs(d1))
      {
        // Un pizzico o uno spostamento di lato: è della mappa.
        if (Math.abs(spread(e) - mSpread) > SPREAD_DP * mDensity)
          mTracking = false;
        return false;
      }
      if (Math.signum(d0) == Math.signum(d1) && Math.abs(d0) > travel && Math.abs(d1) > travel)
      {
        mDone = true;
        mTracking = false;
        EuriseSky.explore3d(c, d0 < 0); // le dita salgono: 3D; scendono: piatta
        return true;
      }
      return false;
    case MotionEvent.ACTION_UP:
    case MotionEvent.ACTION_CANCEL:
      final boolean was = mDone;
      mTracking = false;
      mDone = false;
      return was;
    default:
      return mDone;
    }
  }

  private static boolean allowed()
  {
    final RoutingController rc = RoutingController.get();
    return !rc.isPlanning() && !rc.isNavigating();
  }

  private static float spread(@NonNull MotionEvent e)
  {
    return (float) Math.hypot(e.getX(0) - e.getX(1), e.getY(0) - e.getY(1));
  }

  @Nullable
  private static Activity activity(@Nullable Context c)
  {
    while (c instanceof ContextWrapper)
    {
      if (c instanceof Activity)
        return (Activity) c;
      c = ((ContextWrapper) c).getBaseContext();
    }
    return null;
  }
}

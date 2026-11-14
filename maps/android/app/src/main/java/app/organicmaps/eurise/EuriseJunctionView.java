package app.organicmaps.eurise;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.PopupMenu;

import androidx.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.organicmaps.eurise.car.EuriseCarInfo;
import app.organicmaps.sdk.routing.CarDirection;
import app.organicmaps.sdk.routing.RoutingInfo;

/**
 * La scena dell'uscita o della rotonda in guida, sul telefono (tavole MapIncrocio e MapRotonda): compare da sola sopra
 * la mappa quando la manovra si avvicina, con le frecce che scorrono, e sparisce dopo. Mentre è nascosta non disegna
 * nulla e non consuma batteria. Anche gli incroci normali (MapIncroci). Tenuta premuta offre «Non mostrare più…» per
 * quel tipo di finestrino (MapFinestrini), senza uscire dalla guida.
 */
public class EuriseJunctionView extends View
{
  @Nullable private EuriseJunctionScene.Model mModel;
  private boolean mAnimated = true;
  private final long mStart = SystemClock.uptimeMillis();

  public EuriseJunctionView(Context context, @Nullable AttributeSet attrs)
  {
    super(context, attrs);
    setVisibility(GONE);
    setClipToOutline(true);
    setOutlineProvider(new ViewOutlineProvider() {
      @Override
      public void getOutline(View view, Outline outline)
      {
        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 18 * getResources().getDisplayMetrics().density);
      }
    });
    setOnLongClickListener(v -> {
      final EuriseJunctionScene.Model m = mModel;
      if (m == null)
        return false;
      final PopupMenu menu = new PopupMenu(getContext(), this);
      menu.getMenu().add(switch (m.kind)
      {
        case EXIT -> app.organicmaps.R.string.eurise_window_hide_exit;
        case ROUNDABOUT -> app.organicmaps.R.string.eurise_window_hide_roundabout;
        case TURN -> app.organicmaps.R.string.eurise_window_hide_turn;
      });
      menu.setOnMenuItemClickListener(item -> {
        PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
            .putBoolean(EuriseJunctionScene.prefFor(m.kind), false).apply();
        hide();
        return true;
      });
      menu.show();
      return true;
    });
  }

  /** A ogni aggiornamento della guida. */
  public void update(@Nullable RoutingInfo info)
  {
    final boolean fast = EuriseCarInfo.isFastRoad()
                         || (info != null && (info.carDirection == CarDirection.EXIT_HIGHWAY_TO_LEFT
                                              || info.carDirection == CarDirection.EXIT_HIGHWAY_TO_RIGHT));
    mModel = EuriseJunctionScene.from(getContext(), info, fast);
    mAnimated = EuriseJunctionScene.animated(getContext());
    final int visibility = mModel == null ? GONE : VISIBLE;
    if (getVisibility() != visibility)
      setVisibility(visibility);
    if (mModel != null)
    {
      setContentDescription(describe(mModel));
      invalidate();
    }
  }

  public void hide()
  {
    mModel = null;
    setVisibility(GONE);
  }

  @NonNull
  private String describe(@NonNull EuriseJunctionScene.Model m)
  {
    if (m.kind == EuriseJunctionScene.Kind.TURN)
      return getContext().getString(m.left ? app.organicmaps.R.string.eurise_junction_turn_left
                                           : app.organicmaps.R.string.eurise_junction_turn_right, m.street);
    if (m.kind == EuriseJunctionScene.Kind.ROUNDABOUT)
      return getContext().getString(app.organicmaps.R.string.eurise_junction_roundabout, m.exitNum)
             + (m.street.isEmpty() ? "" : ", " + m.street);
    final String exit = m.exitRef.isEmpty() ? "" : getContext().getString(app.organicmaps.R.string.eurise_junction_exit, m.exitRef) + ", ";
    return exit + m.roadRef + " " + m.destination;
  }

  @Override
  protected void onDraw(@NonNull Canvas canvas)
  {
    super.onDraw(canvas);
    final EuriseJunctionScene.Model m = mModel;
    if (m == null)
      return;
    // Frecce ferme: l'istante in cui si vedono tutte, e nessun ridisegno continuo.
    final float t = mAnimated ? (SystemClock.uptimeMillis() - mStart) / 1000f : 0.9f;
    EuriseJunctionScene.draw(getContext(), canvas, m, getWidth(), getHeight(), t);
    // Le frecce scorrono finché la scena è visibile.
    if (mAnimated)
      postInvalidateOnAnimation();
  }
}

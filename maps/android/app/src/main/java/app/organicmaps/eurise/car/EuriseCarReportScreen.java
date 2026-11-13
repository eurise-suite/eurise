package app.organicmaps.eurise.car;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.CarContext;
import androidx.car.app.model.Action;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.GridItem;
import androidx.car.app.model.GridTemplate;
import androidx.car.app.model.Header;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.MapWithContentTemplate;
import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.LifecycleOwner;
import app.organicmaps.R;
import app.organicmaps.car.renderer.Renderer;
import app.organicmaps.car.screens.base.BaseMapScreen;
import app.organicmaps.car.util.UiHelpers;
import app.organicmaps.eurise.EuriseGuideReports;
import app.organicmaps.eurise.EuriseGuideReports.Kind;
import app.organicmaps.eurise.EuriseGuideReports.Report;

/**
 * «Segnala qui» in Android Auto (tavola MapAutoSegnala): la griglia di sei voci sopra la mappa, il massimo che l'auto
 * mostra in guida. Un tocco segna (punto e direzione al tocco, come sul telefono), poi per 5 secondi «Annulla» e la
 * schermata torna alla guida da sola. Lo stesso archivio del telefono: l'elenco per OpenStreetMap arriva a fine viaggio.
 */
public class EuriseCarReportScreen extends BaseMapScreen
{
  private static final long UNDO_MS = 5_000;
  private static final Kind[] ORDER = {Kind.CAMERA, Kind.LIMIT, Kind.CLOSED, Kind.WORKS, Kind.QUEUE, Kind.DANGER};

  private final Handler mMain = new Handler(Looper.getMainLooper());
  @Nullable private Report mLast;

  public EuriseCarReportScreen(@NonNull CarContext carContext, @NonNull Renderer surfaceRenderer)
  {
    super(carContext, surfaceRenderer);
  }

  @NonNull
  @Override
  protected Template onGetTemplateImpl()
  {
    final MapWithContentTemplate.Builder builder = new MapWithContentTemplate.Builder();
    builder.setMapController(UiHelpers.createMapController(getCarContext(), getSurfaceRenderer()));
    builder.setContentTemplate(mLast == null ? grid() : undo(mLast));
    return builder.build();
  }

  @Override
  public void onDestroy(@NonNull LifecycleOwner owner)
  {
    mMain.removeCallbacksAndMessages(null);
    super.onDestroy(owner);
  }

  @NonNull
  private GridTemplate grid()
  {
    final CarContext c = getCarContext();
    final ItemList.Builder list = new ItemList.Builder();
    for (Kind k : ORDER)
      list.addItem(new GridItem.Builder()
                       .setTitle(c.getString(k.label))
                       .setImage(new CarIcon.Builder(IconCompat.createWithResource(c, k.icon)).build(),
                                 GridItem.IMAGE_TYPE_LARGE)
                       .setOnClickListener(() -> report(k))
                       .build());
    final Header header = new Header.Builder()
                              .setStartHeaderAction(Action.BACK)
                              .setTitle(c.getString(R.string.eurise_guide_title))
                              .build();
    return new GridTemplate.Builder().setHeader(header).setSingleList(list.build()).build();
  }

  @NonNull
  private MessageTemplate undo(@NonNull Report r)
  {
    final CarContext c = getCarContext();
    final String message = c.getString(r.kind.label) + " " + EuriseGuideReports.placed(c, r);
    final Action undo = new Action.Builder()
                            .setTitle(c.getString(R.string.eurise_guide_undo))
                            .setOnClickListener(() -> {
                              mMain.removeCallbacksAndMessages(null);
                              EuriseGuideReports.remove(c, r.id);
                              mLast = null;
                              finish();
                            })
                            .build();
    final Action ok = new Action.Builder()
                          .setTitle(c.getString(R.string.eurise_guide_ok))
                          .setOnClickListener(this::finish)
                          .build();
    return new MessageTemplate.Builder(message)
        .setHeader(new Header.Builder().setTitle(c.getString(R.string.eurise_guide_undo_hint)).build())
        .setIcon(new CarIcon.Builder(IconCompat.createWithResource(c, r.kind.icon)).build())
        .addAction(undo)
        .addAction(ok)
        .build();
  }

  private void report(@NonNull Kind kind)
  {
    final Report r = EuriseGuideReports.add(getCarContext(), kind);
    if (r == null)
    {
      androidx.car.app.CarToast.makeText(getCarContext(), R.string.eurise_guide_no_position,
                                         androidx.car.app.CarToast.LENGTH_LONG).show();
      return;
    }
    mLast = r;
    invalidate();
    mMain.postDelayed(() -> {
      if (getScreenManager().getTop() == this)
        finish();
    }, UNDO_MS);
  }
}

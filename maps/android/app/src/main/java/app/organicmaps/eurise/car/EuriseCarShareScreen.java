package app.organicmaps.eurise.car;

import androidx.annotation.NonNull;
import androidx.car.app.CarContext;
import androidx.car.app.model.Action;
import androidx.car.app.model.Header;
import androidx.car.app.model.ItemList;
import androidx.car.app.model.ListTemplate;
import androidx.car.app.model.Row;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.MapWithContentTemplate;
import app.organicmaps.R;
import app.organicmaps.car.renderer.Renderer;
import app.organicmaps.car.screens.base.BaseMapScreen;
import app.organicmaps.car.util.UiHelpers;
import app.organicmaps.eurise.EuriseTrip;
import java.util.List;

/**
 * «Condividi l'arrivo» in Android Auto (tavola MapBollaArrivo), dalla bandierina in guida: le persone con cui si è già
 * condiviso dal telefono (in auto non si sfoglia la Rubrica), oppure «Smetti di condividere». L'arrivo va per SMS da
 * Eurise Messaggi: solo l'ora d'arrivo, mai la posizione.
 */
public class EuriseCarShareScreen extends BaseMapScreen
{
  public EuriseCarShareScreen(@NonNull CarContext carContext, @NonNull Renderer surfaceRenderer)
  {
    super(carContext, surfaceRenderer);
  }

  @NonNull
  @Override
  protected Template onGetTemplateImpl()
  {
    final MapWithContentTemplate.Builder builder = new MapWithContentTemplate.Builder();
    builder.setMapController(UiHelpers.createMapController(getCarContext(), getSurfaceRenderer()));
    builder.setContentTemplate(list());
    return builder.build();
  }

  @NonNull
  private ListTemplate list()
  {
    final CarContext c = getCarContext();
    final ItemList.Builder items = new ItemList.Builder();
    final String now = EuriseTrip.shareName();
    if (now != null)
      items.addItem(new Row.Builder()
                        .setTitle(c.getString(R.string.eurise_trip_stop_share, now))
                        .setOnClickListener(() -> {
                          EuriseTrip.stopShare();
                          finish();
                        })
                        .build());
    final List<String[]> recent = EuriseTrip.recent(c);
    for (String[] p : recent)
    {
      if (p[0].equals(now))
        continue;
      final Row.Builder row = new Row.Builder().setTitle(p[0]).setOnClickListener(() -> {
        EuriseTrip.startShare(c, p[0], p[1].isEmpty() ? null : p[1]);
        finish();
      });
      if (!p[1].isEmpty())
        row.addText(p[1]);
      items.addItem(row.build());
    }
    if (now == null && recent.isEmpty())
      items.setNoItemsMessage(c.getString(R.string.eurise_trip_share_car_empty));
    return new ListTemplate.Builder()
        .setHeader(new Header.Builder().setStartHeaderAction(Action.BACK)
                       .setTitle(c.getString(R.string.eurise_trip_share_action)).build())
        .setSingleList(items.build())
        .build();
  }
}

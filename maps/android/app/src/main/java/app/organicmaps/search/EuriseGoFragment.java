package app.organicmaps.search;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import app.organicmaps.R;
import app.organicmaps.eurise.EuriseArrival;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * «Dove devi andare» nella ricerca di Mappe (tavola MapCerca): gli impegni dei prossimi 7 giorni con un luogo, letti
 * dall'app Eurise (DestinationsContract in core/suite, solo tra app della suite). Toccandone uno il luogo va nella
 * ricerca, che lavora sulle mappe scaricate: la posizione non esce dal telefono. L'impegno resta per la frase d'arrivo.
 */
public class EuriseGoFragment extends Fragment
{
  private static final String[] HUBS = {"eu.eurise.hub.debug", "eu.eurise.hub"};
  private static final ExecutorService IO = Executors.newSingleThreadExecutor();

  private LinearLayout mList;

  @Nullable
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state)
  {
    final Context c = requireContext();
    final ScrollView scroll = new ScrollView(c);
    mList = new LinearLayout(c);
    mList.setOrientation(LinearLayout.VERTICAL);
    final int pad = dp(16);
    mList.setPadding(pad, dp(8), pad, pad);
    scroll.addView(mList);
    load();
    return scroll;
  }

  private void load()
  {
    final Context app = requireContext().getApplicationContext();
    IO.execute(() -> {
      Bundle b = null;
      for (String h : HUBS)
      {
        try
        {
          b = app.getContentResolver().call(Uri.parse("content://" + h + ".destinations"), "list", null, null);
          if (b != null)
            break;
        }
        catch (Exception ignored)
        {
          // App Eurise assente o vecchia: si prova l'altra.
        }
      }
      final Bundle out = b;
      if (mList != null)
        mList.post(() -> show(out));
    });
  }

  private void show(@Nullable Bundle b)
  {
    if (!isAdded())
      return;
    mList.removeAllViews();
    mList.addView(heading(getString(R.string.eurise_go_title)));
    final String[] titles = b == null ? null : b.getStringArray("title");
    final String[] places = b == null ? null : b.getStringArray("place");
    final long[] starts = b == null ? null : b.getLongArray("start");
    if (b == null)
    {
      mList.addView(note(getString(R.string.eurise_go_no_hub)));
      return;
    }
    if (!b.getBoolean("calendar"))
    {
      mList.addView(note(getString(R.string.eurise_go_no_calendar)));
      return;
    }
    if (titles == null || places == null || starts == null || titles.length == 0)
    {
      mList.addView(note(getString(R.string.eurise_go_empty)));
      return;
    }
    final LinearLayout card = card();
    for (int i = 0; i < titles.length && i < places.length && i < starts.length; i++)
    {
      final String title = titles[i], place = places[i];
      final long start = starts[i];
      final String when = DateUtils.formatDateTime(requireContext(), start,
          DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_WEEKDAY);
      final View row = row(title + " · " + place, getString(R.string.eurise_go_from_calendar, when));
      row.setOnClickListener(v -> {
        EuriseArrival.remember(requireContext(), title, start);
        final Fragment parent = getParentFragment();
        if (parent instanceof SearchFragment)
          ((SearchFragment) parent).setQuery(place, false);
      });
      if (i > 0)
        card.addView(divider());
      card.addView(row);
    }
    mList.addView(card);
    mList.addView(note(getString(R.string.eurise_go_privacy)));
  }

  private boolean night()
  {
    return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
  }

  private int fg() { return night() ? 0xFFE6EAF2 : 0xFF1E1D1B; }
  private int muted() { return night() ? 0xFFA9B1C2 : 0xFF56564F; }

  private TextView heading(String text)
  {
    final TextView t = new TextView(requireContext());
    t.setText(text.toUpperCase(java.util.Locale.getDefault()));
    t.setTextSize(11);
    t.setLetterSpacing(0.08f);
    t.setTypeface(Typeface.DEFAULT_BOLD);
    t.setTextColor(muted());
    t.setPadding(dp(4), dp(10), dp(4), dp(8));
    return t;
  }

  private TextView note(String text)
  {
    final TextView t = new TextView(requireContext());
    t.setText(text);
    t.setTextSize(12.5f);
    t.setLineSpacing(0, 1.3f);
    t.setTextColor(muted());
    t.setPadding(dp(4), dp(12), dp(4), dp(4));
    return t;
  }

  private LinearLayout card()
  {
    final LinearLayout l = new LinearLayout(requireContext());
    l.setOrientation(LinearLayout.VERTICAL);
    final GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(dp(18));
    bg.setColor(night() ? 0xFF222A3A : 0xFFFFFFFF);
    l.setBackground(bg);
    l.setPadding(dp(14), 0, dp(14), 0);
    return l;
  }

  private View divider()
  {
    final View v = new View(requireContext());
    v.setBackgroundColor(night() ? 0x1FFFFFFF : 0x121E1D1B);
    v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2)));
    return v;
  }

  private View row(String title, String sub)
  {
    final Context c = requireContext();
    final LinearLayout r = new LinearLayout(c);
    r.setOrientation(LinearLayout.VERTICAL);
    r.setGravity(Gravity.CENTER_VERTICAL);
    r.setMinimumHeight(dp(62));
    r.setPadding(0, dp(10), 0, dp(10));
    r.setClickable(true);
    r.setFocusable(true);
    final android.util.TypedValue tv = new android.util.TypedValue();
    if (c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true))
      r.setForeground(c.getDrawable(tv.resourceId));
    final TextView t = new TextView(c);
    t.setText(title);
    t.setTextSize(15);
    t.setTypeface(Typeface.DEFAULT_BOLD);
    t.setTextColor(fg());
    t.setMaxLines(2);
    final TextView s = new TextView(c);
    s.setText(sub);
    s.setTextSize(12);
    s.setTextColor(muted());
    r.addView(t);
    r.addView(s);
    return r;
  }

  private int dp(int v)
  {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }
}

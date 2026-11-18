package app.organicmaps.eurise;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import app.organicmaps.R;
import app.organicmaps.sdk.routing.RoutingController;
import java.util.List;

/**
 * La bolla allungata a destra, in guida, con la musica che suona o la chiamata (tavole MapBollaAuto, MapBollaAutoChiamata,
 * MapBollaTelefono, approvate il 16 novembre) e le otto aggiunte del 17 novembre (MapBollaPodcast, MapBollaDopo,
 * MapBollaRadio, MapBollaPersa, MapBollaRitardo, MapBollaMessaggio, MapBollaImpegni, MapBollaArrivo, MapBollaAutoStati).
 *
 * <p>Musica: copertina con il simbolo dell'app, titolo, autore, album e anno, tempi, testo con il servizio «Testi delle
 * canzoni», e una riga sotto: «Dopo: …» dalla coda dell'app; per podcast e audiolibri il capitolo e «All'arrivo
 * mancheranno 10 min» (o «Finisce alle 9:44, prima dell'arrivo»); per la radio «in diretta». Un'altra riga per l'impegno
 * vicino (arancione se arrivi dopo l'inizio), il timer, la sveglia, l'arrivo condiviso: c'è anche senza musica.
 *
 * <p>Sul telefono la bolla mostra anche gli avvisi del viaggio ({@link EuriseTrip}: chiamata persa, messaggi,
 * «Avvisa: arrivo alle…», conferme con Annulla) con due pulsanti della stessa larghezza, il principale a destra sotto il
 * pollice. In auto si legge soltanto, più stretta e appoggiata ai pulsanti dell'auto (richiesta del proprietario, 17
 * novembre): gli avvisi con i pulsanti li mostra l'auto ({@link app.organicmaps.eurise.car.EuriseCarAlerts}) e la riga
 * nuova prende il posto di quella dell'album, senza aggiungere altezza.
 *
 * <p>Sul telefono un tocco apre l'app che suona (o la chiamata), la pressione lunga la nasconde per il viaggio.
 */
public final class EuriseMediaBubble extends LinearLayout implements Runnable
{
  /** Chi dice se è notte: il tema dell'auto o quello del telefono. */
  public interface Night
  {
    boolean isNight();
  }

  private static final long PAUSE_HIDE_MS = 10_000;
  private static final long CALL_GRACE_MS = 5_000;
  private static final long EVENT_WINDOW_MS = 30 * 60_000L;
  private static final long CLOCK_WINDOW_MS = 15 * 60_000L;
  private static final long NEXT_SOON_MS = 20_000;
  private static boolean sHiddenThisTrip;

  private static final int BLUE = 0xFF3E5C8A, GREEN = 0xFF2F6B45, ORANGE = 0xFF9A4A12, RED = 0xFFA3322B;

  /** Una riga sotto: testo, simbolo, colore. */
  private static final class Line
  {
    final String text;
    @DrawableRes final int icon;
    final int tone;
    /** Il numero grande a destra quando la riga è da sola (conto alla rovescia del timer). */
    @Nullable final String big;
    @Nullable final String title, sub;

    Line(String title, @Nullable String sub, @Nullable String big, int icon, int tone)
    {
      this.title = title;
      this.sub = sub;
      this.big = big;
      this.icon = icon;
      this.tone = tone;
      this.text = big != null ? title + " · " + big : sub != null ? title + " · " + sub : title;
    }
  }

  private final boolean mCar;
  private final Night mNight;
  private final float mMaxW, mFraction;
  private final ImageView mArt, mBadge, mIcon;
  private final TextView mInitials, mTitle, mSub, mSub2, mBig, mBigSub, mPos, mRem, mLyric, mLyricNext, mLine1, mLine2;
  private final TextView mLeft, mRight;
  private final LinearLayout mTimes, mButtons;
  private final View mBarFill;
  private final FrameLayout mBar;
  private final View mLyricBox;
  private long mCallEndedAt;
  private boolean mHadCall;
  @Nullable private EuriseTrip.Notice mShown;

  private EuriseMediaBubble(@NonNull Context c, boolean car, @NonNull Night night)
  {
    super(c);
    mCar = car;
    mNight = night;
    // In auto più grande (prova del proprietario del 17 novembre: «molto piccolina»).
    mMaxW = dp(car ? 480 : 300);
    mFraction = car ? 0.5f : 0.8f;
    setOrientation(VERTICAL);
    final int pad = dp(car ? 10 : 9);
    setPadding(pad, pad, dp(car ? 14 : 12), pad);
    setElevation(dp(4));
    setVisibility(GONE);

    final LinearLayout row = new LinearLayout(c);
    row.setOrientation(HORIZONTAL);
    row.setGravity(Gravity.CENTER_VERTICAL);
    addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

    final int artSize = dp(car ? 64 : 46);
    final FrameLayout artFrame = new FrameLayout(c);
    row.addView(artFrame, new LayoutParams(artSize + dp(4), artSize + dp(4)));
    mArt = new ImageView(c);
    mArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
    mArt.setClipToOutline(true);
    artFrame.addView(mArt, new FrameLayout.LayoutParams(artSize, artSize));
    mInitials = text(c, car ? 18 : 17, true);
    mInitials.setGravity(Gravity.CENTER);
    mInitials.setTextColor(0xFFFFFFFF);
    artFrame.addView(mInitials, new FrameLayout.LayoutParams(artSize, artSize));
    mIcon = new ImageView(c);
    mIcon.setScaleType(ImageView.ScaleType.CENTER);
    artFrame.addView(mIcon, new FrameLayout.LayoutParams(artSize, artSize));
    mBadge = new ImageView(c);
    final FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.END | Gravity.BOTTOM);
    artFrame.addView(mBadge, badge);

    final LinearLayout col = new LinearLayout(c);
    col.setOrientation(VERTICAL);
    final LayoutParams colLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
    colLp.leftMargin = dp(car ? 10 : 10);
    row.addView(col, colLp);
    mTitle = text(c, car ? 24 : 15, true);
    mSub = text(c, car ? 20 : 12.5f, false);
    mSub2 = text(c, car ? 17 : 11.5f, false);
    col.addView(mTitle);
    col.addView(mSub);
    col.addView(mSub2);

    mTimes = new LinearLayout(c);
    mTimes.setOrientation(VERTICAL);
    mTimes.setGravity(Gravity.END);
    final LayoutParams timesLp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    timesLp.leftMargin = dp(10);
    row.addView(mTimes, timesLp);
    mBig = text(c, car ? 24 : 17, true);
    mBigSub = text(c, car ? 15 : 11, false);
    mTimes.addView(mBig);
    mTimes.addView(mBigSub);

    final LinearLayout bar = new LinearLayout(c);
    bar.setOrientation(HORIZONTAL);
    bar.setGravity(Gravity.CENTER_VERTICAL);
    final LayoutParams barRowLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    barRowLp.topMargin = dp(car ? 7 : 6);
    addView(bar, barRowLp);
    mPos = text(c, car ? 17 : 11.5f, true);
    bar.addView(mPos);
    mBar = new FrameLayout(c);
    final LayoutParams trackLp = new LayoutParams(0, dp(car ? 7 : 5), 1f);
    trackLp.leftMargin = dp(8);
    trackLp.rightMargin = dp(8);
    bar.addView(mBar, trackLp);
    mBarFill = new View(c);
    mBar.addView(mBarFill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
    mRem = text(c, car ? 17 : 11.5f, true);
    bar.addView(mRem);

    final LinearLayout lyric = new LinearLayout(c);
    lyric.setOrientation(VERTICAL);
    final LayoutParams lyricLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    lyricLp.topMargin = dp(car ? 6 : 5);
    addView(lyric, lyricLp);
    mLyricBox = lyric;
    mLyric = text(c, car ? 20 : 13, true);
    mLyricNext = text(c, 13, false);
    lyric.addView(mLyric);

    // Le righe nuove (sul telefono; in auto la riga va al posto di quella dell'album).
    mLine1 = line(c);
    mLine2 = line(c);
    if (!car)
    {
      final LayoutParams l1 = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
      l1.topMargin = dp(6);
      addView(mLine1, l1);
      final LayoutParams l2 = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
      l2.topMargin = dp(4);
      addView(mLine2, l2);
    }

    // Due pulsanti della stessa larghezza, il principale a destra (solo sul telefono).
    mButtons = new LinearLayout(c);
    mButtons.setOrientation(HORIZONTAL);
    mLeft = button(c);
    mRight = button(c);
    final LayoutParams bl = new LayoutParams(0, dp(44), 1f);
    final LayoutParams br = new LayoutParams(0, dp(44), 1f);
    br.leftMargin = dp(8);
    mButtons.addView(mLeft, bl);
    mButtons.addView(mRight, br);
    mButtons.setVisibility(GONE);
    if (!car)
    {
      final LayoutParams bp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
      bp.topMargin = dp(8);
      addView(mButtons, bp);
      mLeft.setOnClickListener(v -> {
        if (mShown != null)
          EuriseTrip.left(phoneHost(), mShown);
      });
      mRight.setOnClickListener(v -> {
        if (mShown != null)
          EuriseTrip.right(phoneHost(), mShown);
      });
    }

    if (!car)
    {
      setOnClickListener(v -> open());
      setOnLongClickListener(v -> {
        sHiddenThisTrip = true;
        refresh();
        Toast.makeText(getContext(), R.string.eurise_np_hidden, Toast.LENGTH_SHORT).show();
        return true;
      });
    }
  }

  // ---- Dove si aggancia ---------------------------------------------------------------------------------------------

  /**
   * Sullo schermo dell'auto: nell'area libera della mappa (SurfaceCallback.initSpeedPill), in basso a sinistra (in alto
   * a destra la coprivano i pulsanti tondi dell'auto). Da qui
   * partono anche gli avvisi dell'auto con i pulsanti.
   */
  @NonNull
  public static View car(@NonNull Context car, @NonNull Night night)
  {
    final EuriseMediaBubble b = new EuriseMediaBubble(car, true, night);
    // In alto a destra, come deciso, ma dentro l'«area stabile» dell'auto: quella che i 4 tasti tondi non coprono mai,
    // nemmeno quando ricompaiono (prova del proprietario, 17 novembre). Vedi stackAboveSiblings.
    final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                                                     ViewGroup.LayoutParams.WRAP_CONTENT,
                                                                     Gravity.TOP | Gravity.END);
    lp.topMargin = b.dp(8);
    lp.rightMargin = b.dp(8);
    b.setLayoutParams(lp);
    if (car instanceof androidx.car.app.CarContext)
      app.organicmaps.eurise.car.EuriseCarAlerts.attach((androidx.car.app.CarContext) car);
    return b;
  }

  /**
   * Sul telefono, nella guida (NavigationController): a destra sotto il cartello della manovra (e corsie e meteo, se ci
   * sono); in orizzontale in alto, accanto a ciò che sta sul lato destro (barra o colonna della manovra).
   */
  public static void attachPhone(@Nullable View navFrame)
  {
    if (!(navFrame instanceof FrameLayout))
      return;
    final FrameLayout frame = (FrameLayout) navFrame;
    final EuriseMediaBubble b = new EuriseMediaBubble(frame.getContext(), false,
                                                      app.organicmaps.util.ThemeUtils::isNightTheme);
    final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                                                                     ViewGroup.LayoutParams.WRAP_CONTENT,
                                                                     Gravity.TOP | Gravity.END);
    lp.rightMargin = b.dp(12);
    frame.addView(b, lp);
    frame.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
      @Override
      public void onGlobalLayout()
      {
        if (!b.isAttachedToWindow())
        {
          frame.getViewTreeObserver().removeOnGlobalLayoutListener(this);
          return;
        }
        b.placeOnPhone(frame);
      }
    });
  }

  private void placeOnPhone(@NonNull FrameLayout frame)
  {
    final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
    final int[] base = new int[2];
    frame.getLocationInWindow(base);
    int top = dp(14), right = dp(12);
    final boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    if (land)
    {
      // Ciò che sta nella metà destra (barra della guida o colonna della manovra): la bolla gli sta accanto.
      for (int id : new int[] {R.id.eurise_guida_bar, R.id.eurise_guide_column})
      {
        final View v = frame.findViewById(id);
        if (v == null || v.getVisibility() != VISIBLE || v.getWidth() == 0)
          continue;
        final int[] at = new int[2];
        v.getLocationInWindow(at);
        final int left = at[0] - base[0];
        if (left > frame.getWidth() / 2)
          right = Math.max(right, frame.getWidth() - left + dp(12));
        top = Math.max(top, at[1] - base[1]);
      }
    }
    else
    {
      for (int id : new int[] {R.id.eurise_guide_card, R.id.lanes, R.id.eurise_route_weather})
      {
        final View v = frame.findViewById(id);
        if (v == null || v.getVisibility() != VISIBLE || v.getHeight() == 0)
          continue;
        final int[] at = new int[2];
        v.getLocationInWindow(at);
        top = Math.max(top, at[1] - base[1] + v.getHeight() + dp(8));
      }
    }
    if (lp.topMargin != top || lp.rightMargin != right)
    {
      lp.topMargin = top;
      lp.rightMargin = right;
      setLayoutParams(lp);
    }
  }

  // ---- Battito ------------------------------------------------------------------------------------------------------

  private final Runnable mChanged = this::refresh;

  @Override
  protected void onAttachedToWindow()
  {
    super.onAttachedToWindow();
    EuriseNowPlaying.listen(mChanged);
    refresh();
  }

  @Override
  protected void onDetachedFromWindow()
  {
    EuriseNowPlaying.unlisten(mChanged);
    removeCallbacks(this);
    super.onDetachedFromWindow();
  }

  @Override
  public void run()
  {
    refresh();
  }

  @Override
  protected void onMeasure(int widthSpec, int heightSpec)
  {
    final int avail = MeasureSpec.getSize(widthSpec);
    final float frac = !mCar && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                       ? 0.42f : mFraction;
    final int w = (int) Math.min(mMaxW, avail > 0 ? avail * frac : mMaxW);
    super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), heightSpec);
  }

  /** Ridisegna e riprogramma il battito (mezzo secondo quando si mostra, per i tempi e il testo). */
  public void refresh()
  {
    removeCallbacks(this);
    final boolean guiding = RoutingController.get().isNavigating();
    if (!guiding)
      sHiddenThisTrip = false;
    else if (EuriseNowPlaying.wanted(getContext()))
      EuriseTrip.ensure(getContext());
    final EuriseNowPlaying.Call call = EuriseNowPlaying.call();
    final long now = SystemClock.elapsedRealtime();
    if (call != null)
      mHadCall = true;
    else if (mHadCall)
    {
      mHadCall = false;
      mCallEndedAt = now;
    }
    final EuriseNowPlaying.Media media = EuriseNowPlaying.media();
    final boolean afterCall = mCallEndedAt > 0 && now - mCallEndedAt < CALL_GRACE_MS;
    final boolean mediaOk = media != null && !afterCall && (media.playing || now - media.pausedAt < PAUSE_HIDE_MS);
    final EuriseTrip.Notice notice = mCar ? null : EuriseTrip.notice();
    final Line status = guiding && EuriseNowPlaying.wanted(getContext()) ? status() : null;
    if (!guiding || sHiddenThisTrip || (call == null && notice == null && !mediaOk && status == null))
    {
      setVisibility(GONE);
      mShown = null;
      // Un controllo lento anche nascosta: la guida può cominciare con la musica che già suona, senza altri avvisi.
      postDelayed(this, guiding && (media != null || afterCall) ? 1_000 : 2_000);
      return;
    }
    style();
    reset();
    if (mCar)
      stackAboveSiblings();
    if (call != null)
    {
      showCall(call);
      if (notice != null && notice.kind == EuriseTrip.KIND_LATE)
      {
        show(mLine1, new Line(notice.sub == null ? notice.title : notice.sub, null, null, R.drawable.eurise_np_clock, ORANGE));
        buttons(notice);
      }
    }
    else if (notice != null)
      showNotice(notice);
    else if (mediaOk)
      showMedia(media, status);
    else
      showStatus(status);
    setVisibility(VISIBLE);
    postDelayed(this, 500);
  }

  /** Area libera e area stabile dell'auto (SurfaceCallbackBase), in pixel della superficie. */
  private static final android.graphics.Rect sCarVisible = new android.graphics.Rect();
  private static final android.graphics.Rect sCarStable = new android.graphics.Rect();

  /** Dall'auto: l'area libera (dove sta lo strato con la bolla) è cambiata. */
  public static void carVisibleArea(@NonNull android.graphics.Rect r)
  {
    final android.graphics.Rect copy = new android.graphics.Rect(r);
    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
      sCarVisible.set(copy);
      EuriseNowPlaying.poke();
    });
  }

  /** Dall'auto: l'area che i pulsanti che compaiono e scompaiono non coprono mai. */
  public static void carStableArea(@NonNull android.graphics.Rect r)
  {
    final android.graphics.Rect copy = new android.graphics.Rect(r);
    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
      sCarStable.set(copy);
      EuriseNowPlaying.poke();
    });
  }

  /**
   * In auto: in alto a destra dell'area stabile. Lo strato della bolla copre l'area libera, quindi i margini sono la
   * distanza tra i due rettangoli (più un piccolo stacco). Senza area stabile, in alto a destra dell'area libera.
   */
  private void stackAboveSiblings()
  {
    if (!(getLayoutParams() instanceof FrameLayout.LayoutParams))
      return;
    int top = dp(8), right = dp(8);
    if (!sCarStable.isEmpty() && !sCarVisible.isEmpty())
    {
      top += Math.max(0, sCarStable.top - sCarVisible.top);
      right += Math.max(0, sCarVisible.right - sCarStable.right);
    }
    final FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) getLayoutParams();
    if (lp.topMargin != top || lp.rightMargin != right)
    {
      lp.topMargin = top;
      lp.rightMargin = right;
      setLayoutParams(lp);
    }
  }

  /** Riporta le parti facoltative a nascoste: ogni stato mostra solo le sue. */
  private void reset()
  {
    mShown = null;
    mButtons.setVisibility(GONE);
    mLine1.setVisibility(GONE);
    mLine2.setVisibility(GONE);
    mIcon.setVisibility(GONE);
    mInitials.setVisibility(GONE);
    mBadge.setVisibility(GONE);
    mBig.setVisibility(VISIBLE);
    mTimes.setVisibility(GONE);
    mLyricBox.setVisibility(GONE);
    ((View) mBar.getParent()).setVisibility(GONE);
  }

  private void showMedia(@NonNull EuriseNowPlaying.Media m, @Nullable Line status)
  {
    final Context c = getContext();
    mArt.setImageBitmap(m.art);
    mArt.setBackground(m.art == null ? round(BLUE, 12) : null);
    mBadge.setImageDrawable(m.appIcon);
    mBadge.setVisibility(m.appIcon == null ? GONE : VISIBLE);
    set(mTitle, m.title);
    set(mSub, m.artist);
    String album = NowPlayingText.albumLine(m.album, m.year);
    if (m.spoken && m.track > 0)
    {
      final String chapter = m.tracks >= m.track ? c.getString(R.string.eurise_np_chapter_of, m.track, m.tracks)
                                                 : c.getString(R.string.eurise_np_chapter, m.track);
      album = album.isEmpty() ? chapter : album + " · " + chapter;
    }
    final String from = m.appName == null ? null : c.getString(R.string.eurise_np_from, m.appName);
    // In auto niente «da Spotify»: lo dice già il simbolo sulla copertina (prova del 18 novembre).
    set(mSub2, mCar ? album : album.isEmpty() ? from : album);
    mSub2.setTextColor(mCar ? sub() : muted());
    if (m.live)
    {
      // Radio: niente barra, il segno «in diretta» a destra.
      mTimes.setVisibility(VISIBLE);
      mBig.setVisibility(GONE);
      mBigSub.setText(R.string.eurise_np_live);
      mBigSub.setTextColor(mNight.isNight() ? 0xFFF0A49E : RED);
      mBigSub.setTypeface(Typeface.DEFAULT_BOLD);
    }
    final long pos = m.position();
    final boolean hasDuration = m.durationMs > 0;
    ((View) mBar.getParent()).setVisibility(hasDuration ? VISIBLE : GONE);
    if (hasDuration)
    {
      mPos.setText(NowPlayingText.clock(pos));
      final String rem = NowPlayingText.remaining(pos, m.durationMs);
      final String speed = m.spoken ? NowPlayingText.speed(m.speed) : "";
      mRem.setText(c.getString(R.string.eurise_np_remaining, rem,
                               NowPlayingText.clock(m.durationMs) + (speed.isEmpty() ? "" : " · " + speed)));
      final int w = mBar.getWidth() * NowPlayingText.progress(pos, m.durationMs) / 1000;
      final ViewGroup.LayoutParams lp = mBarFill.getLayoutParams();
      if (lp.width != w)
      {
        lp.width = w;
        mBarFill.setLayoutParams(lp);
      }
    }
    final NowPlayingText.Lyrics l = EuriseLyrics.of(m);
    final int i = l == null ? -1 : l.indexAt(pos);
    final String lyric = l == null ? null : l.line(i);
    // Una riga vuota (pausa strumentale) non mostra più la «♪» da sola: la riga sparisce finché non si canta.
    final boolean sung = lyric != null && !lyric.trim().isEmpty();
    mLyricBox.setVisibility(sung ? VISIBLE : GONE);
    if (sung)
      mLyric.setText(lyric);

    final Line media = mediaLine(m, pos);
    if (mCar)
    {
      // In auto una riga sola, al posto di quella dell'album: prima l'impegno o il timer, poi quella della traccia.
      final Line one = status != null ? status : media;
      if (one != null)
      {
        mSub2.setText(one.text);
        mSub2.setTextColor(tone(one.tone == 0 ? BLUE : one.tone));
        mSub2.setVisibility(VISIBLE);
      }
      return;
    }
    show(mLine1, media);
    show(mLine2, status);
  }

  /** La riga della traccia: arrivo per podcast e audiolibri, il brano dopo per la musica. */
  @Nullable
  private Line mediaLine(@NonNull EuriseNowPlaying.Media m, long pos)
  {
    final Context c = getContext();
    final long eta = EuriseTrip.eta();
    if (m.spoken && m.durationMs > 0 && eta > 0)
    {
      final long now = System.currentTimeMillis();
      final long left = NowPlayingText.leftAtArrival(pos, m.durationMs, m.speed, eta - now);
      if (left >= 0)
        return new Line(c.getString(R.string.eurise_np_left_at_arrival, NowPlayingText.minutesUp(left)), null, null,
                        R.drawable.eurise_np_arrive, GREEN);
      final long ends = eta + left;
      return new Line(c.getString(m.track > 0 ? R.string.eurise_np_chapter_ends : R.string.eurise_np_episode_ends,
                                  EuriseTrip.time(ends)), null, null, R.drawable.eurise_np_arrive, ORANGE);
    }
    if (!m.spoken && !m.live && m.next != null)
    {
      final boolean soon = m.durationMs > 0 && m.durationMs - pos < NEXT_SOON_MS;
      return new Line(c.getString(soon ? R.string.eurise_np_next_soon : R.string.eurise_np_next, m.next), null, null,
                      R.drawable.eurise_np_next, 0);
    }
    return null;
  }

  /** Impegno vicino (arancione se arrivi dopo), timer, sveglia, arrivo condiviso: la più importante. */
  @Nullable
  private Line status()
  {
    final Context c = getContext();
    final long now = System.currentTimeMillis();
    final long eta = EuriseTrip.eta();
    EuriseNowPlaying.Event event = null;
    final List<EuriseNowPlaying.Event> events = EuriseNowPlaying.events(c);
    for (EuriseNowPlaying.Event e : events)
      if (NowPlayingText.soon(e.start, now, EVENT_WINDOW_MS) && (event == null || e.start < event.start))
        event = e;
    Line eventLine = null;
    if (event != null)
    {
      final int in = NowPlayingText.minutesUp(event.start - now);
      final int late = eta > 0 ? NowPlayingText.lateMinutes(eta, event.start) : 0;
      final String title = c.getString(R.string.eurise_np_event_in, event.title, in);
      if (late >= 3)
        return new Line(title, c.getString(R.string.eurise_np_event_late, EuriseTrip.time(eta), late), null,
                        R.drawable.eurise_np_clock, ORANGE);
      eventLine = new Line(title, c.getString(R.string.eurise_np_event_at, EuriseTrip.time(event.start)), null,
                           R.drawable.eurise_np_clock, 0);
    }
    final EuriseNowPlaying.Timer timer = EuriseNowPlaying.timer();
    if (timer != null && NowPlayingText.soon(timer.endsAt, now, CLOCK_WINDOW_MS))
      return new Line(timer.title.isEmpty() ? c.getString(R.string.eurise_np_timer)
                                            : c.getString(R.string.eurise_np_timer_named, timer.title),
                      c.getString(R.string.eurise_np_timer_rings, EuriseTrip.time(timer.endsAt)),
                      NowPlayingText.clock(timer.endsAt - now), R.drawable.eurise_np_timer, 0);
    final long alarm = EuriseNowPlaying.nextAlarm(c);
    if (alarm > 0 && NowPlayingText.soon(alarm, now, CLOCK_WINDOW_MS))
      return new Line(c.getString(R.string.eurise_np_alarm, EuriseTrip.time(alarm), NowPlayingText.minutesUp(alarm - now)),
                      null, null, R.drawable.eurise_np_bell, 0);
    if (eventLine != null)
      return eventLine;
    final String share = EuriseTrip.shareName();
    if (share != null)
      return new Line(c.getString(R.string.eurise_trip_share_title, share),
                      eta > 0 ? EuriseTrip.time(eta) : null, null, R.drawable.eurise_np_pin, GREEN);
    return null;
  }

  /** Solo la riga (timer, impegno…) senza musica: il simbolo al posto della copertina. */
  private void showStatus(@Nullable Line s)
  {
    if (s == null)
      return;
    icon(s.icon, s.tone);
    set(mTitle, s.title);
    set(mSub, s.sub);
    mSub.setTextColor(s.tone != 0 ? tone(s.tone) : (mNight.isNight() ? 0xFFC9D2E0 : 0xFF3B3A36));
    set(mSub2, null);
    if (s.big != null)
    {
      mTimes.setVisibility(VISIBLE);
      mBig.setText(s.big);
      mBigSub.setText("");
    }
  }

  private void showNotice(@NonNull EuriseTrip.Notice n)
  {
    if (n.photo != null)
    {
      mArt.setImageBitmap(n.photo);
      mArt.setBackground(null);
    }
    else if (n.initials != null && n.kind != EuriseTrip.KIND_DONE && n.kind != EuriseTrip.KIND_SEND
             && n.kind != EuriseTrip.KIND_READING)
    {
      mArt.setImageDrawable(null);
      mArt.setBackground(round(n.tone == EuriseTrip.TONE_LATE ? 0xFF6E56A8 : GREEN, 100));
      mInitials.setText(initials(n.initials));
      mInitials.setVisibility(VISIBLE);
    }
    else
      icon(n.icon, n.tone == EuriseTrip.TONE_PLAIN ? 0 : n.tone == EuriseTrip.TONE_LATE ? ORANGE
                                                   : n.tone == EuriseTrip.TONE_MISSED ? RED : GREEN);
    set(mTitle, n.title);
    set(mSub, n.sub);
    mSub.setTextColor(n.tone == EuriseTrip.TONE_LATE ? tone(ORANGE) : (mNight.isNight() ? 0xFFC9D2E0 : 0xFF3B3A36));
    set(mSub2, null);
    if (n.photo != null || (n.initials != null && n.kind != EuriseTrip.KIND_DONE))
    {
      // Il simbolo dell'avviso accanto al nome (chiamata persa, messaggio).
      mTimes.setVisibility(VISIBLE);
      mBig.setVisibility(GONE);
      mBigSub.setText("");
      mBigSub.setCompoundDrawablesRelative(null, null, tinted(n.icon, tone(n.tone == 0 ? BLUE
          : n.tone == EuriseTrip.TONE_MISSED ? RED : n.tone == EuriseTrip.TONE_LATE ? ORANGE : GREEN)), null);
    }
    buttons(n);
  }

  private void buttons(@NonNull EuriseTrip.Notice n)
  {
    mShown = n;
    if (n.left == null && n.right == null)
      return;
    mButtons.setVisibility(VISIBLE);
    final String undoLeft = n.kind == EuriseTrip.KIND_SEND || n.kind == EuriseTrip.KIND_DONE
                            ? " " + ((n.remainingMs() + 999) / 1000) : "";
    mLeft.setVisibility(n.left == null ? GONE : VISIBLE);
    if (n.left != null)
      mLeft.setText(n.left);
    mRight.setVisibility(n.right == null ? GONE : VISIBLE);
    if (n.right != null)
      mRight.setText(n.right + (n.until > 0 && undoLeft.length() > 1 ? undoLeft : ""));
    final LayoutParams br = (LayoutParams) mRight.getLayoutParams();
    final int margin = n.left == null ? 0 : dp(8);
    if (br.leftMargin != margin)
    {
      br.leftMargin = margin;
      mRight.setLayoutParams(br);
    }
  }

  private void showCall(@NonNull EuriseNowPlaying.Call call)
  {
    if (call.photo != null)
    {
      mArt.setImageBitmap(call.photo);
      mArt.setBackground(null);
    }
    else
    {
      mArt.setImageDrawable(null);
      mArt.setBackground(round(GREEN, 100));
      mInitials.setText(initials(call.name));
      mInitials.setVisibility(VISIBLE);
    }
    set(mTitle, call.incoming ? getContext().getString(R.string.eurise_np_calling, call.name) : call.name);
    final String route = EuriseNowPlaying.route(getContext());
    final String detail = call.detail;
    set(mSub, detail == null ? route : route == null ? detail : detail + " · " + route);
    set(mSub2, call.together == null ? null : getContext().getString(R.string.eurise_np_together, call.together));
    mSub2.setTextColor(mNight.isNight() ? 0xFF9CC3A9 : GREEN);
    mTimes.setVisibility(VISIBLE);
    if (call.incoming)
    {
      mBig.setText("");
      mBig.setVisibility(GONE);
      mBigSub.setText(R.string.eurise_np_incoming);
    }
    else
    {
      mBig.setVisibility(VISIBLE);
      mBig.setText(NowPlayingText.clock(System.currentTimeMillis() - call.start));
      mBigSub.setText(R.string.eurise_np_ongoing);
    }
  }

  private void open()
  {
    final EuriseNowPlaying.Call call = EuriseNowPlaying.call();
    try
    {
      if (call != null && call.open != null)
      {
        call.open.send();
        return;
      }
      final EuriseNowPlaying.Media m = EuriseNowPlaying.media();
      if (m == null)
        return;
      final Intent i = getContext().getPackageManager().getLaunchIntentForPackage(m.pkg);
      if (i != null)
        getContext().startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
    catch (PendingIntent.CanceledException | RuntimeException ignored)
    {
      // Chiamata finita o app tolta nel frattempo.
    }
  }

  /** Il telefono come {@link EuriseTrip.Host}: risposta a voce col riconoscimento del telefono, chiamata col tastierino. */
  @NonNull
  private EuriseTrip.Host phoneHost()
  {
    final Context c = getContext();
    return new EuriseTrip.Host() {
      @NonNull
      @Override
      public Context context()
      {
        return c;
      }

      @Override
      public boolean car()
      {
        return false;
      }

      @Override
      public void listen(@NonNull String prompt, @NonNull EuriseTrip.Text then)
      {
        EuriseReplyActivity.listen(c, prompt, then);
      }

      @Override
      public void dial(@Nullable String number)
      {
        try
        {
          c.startActivity((number == null ? new Intent(Intent.ACTION_DIAL)
                                          : new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number)))
                              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        catch (RuntimeException ignored)
        {
          // Nessun telefono.
        }
      }
    };
  }

  // ---- Aspetto ------------------------------------------------------------------------------------------------------

  private void style()
  {
    final boolean night = mNight.isNight();
    final GradientDrawable bg = new GradientDrawable();
    bg.setCornerRadius(dp(mCar ? 20 : 20));
    // In auto vetro quasi pieno: la mappa sotto non deve disturbare la lettura.
    bg.setColor(mCar ? (night ? 0xFA1A202F : 0xFAFAF9F6) : (night ? 0xF01C2333 : 0xF0F8F7F4));
    bg.setStroke(Math.max(1, dp(1) / 2), night ? 0x29FFFFFF : 0x1F1E1D1B);
    setBackground(bg);
    final int ink = night ? (mCar ? 0xFFF2F5FA : 0xFFE8ECF4) : 0xFF1E1D1B;
    mTitle.setTextColor(ink);
    mBig.setTextColor(ink);
    mSub.setTextColor(sub());
    mPos.setTextColor(sub());
    mRem.setTextColor(sub());
    mBigSub.setTextColor(muted());
    mBigSub.setTypeface(Typeface.DEFAULT);
    mBigSub.setCompoundDrawablesRelative(null, null, null, null);
    mLyric.setTextColor(mCar ? ink : night ? 0xFFA9C1F2 : 0xFF1F2A44);
    mLyricNext.setTextColor(muted());
    mBar.setBackground(round(night ? 0x33FFFFFF : 0xFFDAD7CF, 3));
    mBarFill.setBackground(round(night ? 0xFFA9C1F2 : BLUE, 3));
    mLeft.setBackground(round(night ? 0x33FFFFFF : 0xFFE6E3DB, 14));
    mLeft.setTextColor(ink);
    mRight.setBackground(round(night ? 0xFF8FB4F0 : BLUE, 14));
    mRight.setTextColor(night ? 0xFF0E1118 : 0xFFFFFFFF);
    final float r = dp(12);
    mArt.setOutlineProvider(new ViewOutlineProvider() {
      @Override
      public void getOutline(View view, Outline outline)
      {
        final boolean round = EuriseNowPlaying.call() != null || (mShown != null && mShown.initials != null);
        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), round ? view.getWidth() / 2f : r);
      }
    });
    mArt.invalidateOutline();
  }

  /** Il simbolo di una riga al posto della copertina, su un tondo leggero. */
  private void icon(@DrawableRes int icon, int color)
  {
    mArt.setImageDrawable(null);
    mArt.setBackground(round(mNight.isNight() ? 0x26FFFFFF : 0xFFE6E3DB, 12));
    mIcon.setImageDrawable(tinted(icon, tone(color == 0 ? BLUE : color)));
    mIcon.setVisibility(VISIBLE);
  }

  private void show(@NonNull TextView v, @Nullable Line l)
  {
    if (l == null)
    {
      v.setVisibility(GONE);
      return;
    }
    v.setText(l.text);
    final int color = tone(l.tone == 0 ? BLUE : l.tone);
    v.setTextColor(l.tone == 0 ? (mNight.isNight() ? 0xFFDDE3EE : 0xFF1F2A44) : color);
    v.setCompoundDrawablesRelative(tinted(l.icon, color), null, null, null);
    v.setVisibility(VISIBLE);
  }

  /** I colori della tavola, schiariti di notte perché si leggano sul vetro scuro. */
  private int tone(int color)
  {
    if (!mNight.isNight())
      return color;
    if (color == ORANGE)
      return 0xFFF2B37C;
    if (color == RED)
      return 0xFFF0A49E;
    if (color == GREEN)
      return 0xFF9CC3A9;
    return 0xFFA9C1F2;
  }

  @Nullable
  private Drawable tinted(@DrawableRes int res, int color)
  {
    if (res == 0)
      return null;
    final Drawable d = androidx.core.content.ContextCompat.getDrawable(getContext(), res);
    if (d == null)
      return null;
    final Drawable m = d.mutate();
    m.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    final int s = dp(mCar ? 18 : 16);
    m.setBounds(0, 0, s, s);
    return m;
  }

  /** Secondo inchiostro (autore, tempi): in auto più scuro di giorno e più chiaro di notte. */
  private int sub()
  {
    final boolean night = mNight.isNight();
    return mCar ? (night ? 0xFFE1E7F0 : 0xFF2E2D2A) : (night ? 0xFFC9D2E0 : 0xFF3B3A36);
  }

  private int muted()
  {
    return mNight.isNight() ? 0xFFA9B4C4 : 0xFF56564F;
  }

  @NonNull
  private GradientDrawable round(int color, int radiusDp)
  {
    final GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radiusDp));
    return d;
  }

  @NonNull
  private static String initials(@NonNull String name)
  {
    final StringBuilder b = new StringBuilder();
    for (String part : name.trim().split("\\s+"))
    {
      if (!part.isEmpty() && Character.isLetterOrDigit(part.codePointAt(0)))
        b.appendCodePoint(Character.toUpperCase(part.codePointAt(0)));
      if (b.length() >= 2)
        break;
    }
    return b.length() == 0 ? "#" : b.toString();
  }

  private static void set(@NonNull TextView t, @Nullable String s)
  {
    if (s == null || s.isEmpty())
    {
      t.setVisibility(GONE);
      return;
    }
    if (!s.contentEquals(t.getText()))
      t.setText(s);
    t.setVisibility(VISIBLE);
  }

  @NonNull
  private TextView line(@NonNull Context c)
  {
    final TextView t = text(c, 13, true);
    t.setCompoundDrawablePadding(dp(7));
    t.setGravity(Gravity.CENTER_VERTICAL);
    t.setVisibility(GONE);
    return t;
  }

  @NonNull
  private TextView button(@NonNull Context c)
  {
    final TextView t = text(c, 13.5f, true);
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(10), 0, dp(10), 0);
    t.setClickable(true);
    t.setFocusable(true);
    return t;
  }

  @NonNull
  private TextView text(@NonNull Context c, float sp, boolean bold)
  {
    final TextView t = new TextView(c);
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    t.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    t.setMaxLines(1);
    t.setEllipsize(TextUtils.TruncateAt.END);
    t.setIncludeFontPadding(false);
    return t;
  }

  private int dp(float v)
  {
    return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getContext().getResources().getDisplayMetrics()));
  }
}

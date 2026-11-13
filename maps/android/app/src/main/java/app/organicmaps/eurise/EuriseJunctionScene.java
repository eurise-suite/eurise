package app.organicmaps.eurise;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import app.organicmaps.R;
import app.organicmaps.sdk.routing.CarDirection;
import app.organicmaps.sdk.routing.LaneInfo;
import app.organicmaps.sdk.routing.LaneWay;
import app.organicmaps.sdk.routing.RoutingInfo;
import app.organicmaps.sdk.util.Distance;

/**
 * La manovra vista dal volante (tavole MapIncrocio e MapRotonda), disegnata su un Canvas: la usa la vista in guida sul
 * telefono (animata) e Android Auto, come immagine dell'incrocio nella finestrella della manovra (ferma).
 *
 * <ul>
 *   <li><b>Uscita</b> (autostrada, superstrada, rampa): la strada con le corsie di OpenStreetMap, la corsia di uscita
 *   che si stacca verso la rampa ed è colorata, le frecce che scorrono dritte dalla tua corsia verso l'uscita, il
 *   portale con il cartello dell'uscita (numero, sigla, destinazioni) che lampeggia piano.</li>
 *   <li><b>Rotonda</b>: le uscite numerate come si incontrano, quella giusta blu, il percorso con frecce dipinte ferme
 *   che si accendono nel verso di marcia e, all'uscita, frecce che scorrono dritte verso la via.</li>
 * </ul>
 *
 * Compare 600 m prima dell'uscita su autostrada e superstrada (300 m nelle altre strade e alle rotonde) e si chiude
 * dopo la manovra. Le frecce non girano mai.
 */
public final class EuriseJunctionScene
{
  /** Spazio di disegno: tutto si scala da qui alla dimensione vera. */
  static final float W = 390f, H = 260f;
  private static final float PERIOD = 2.4f, PULSE = 1.8f;

  public enum Kind { EXIT, ROUNDABOUT }

  /** Ciò che serve a disegnare la scena, preso dalla guida di CoMaps. */
  public static final class Model
  {
    @NonNull public final Kind kind;
    /** L'uscita è a sinistra (o, alle rotonde, si guida a sinistra). */
    public final boolean left;
    public final int lanes;
    public final int exitNum;
    /** Quanto manca, da 0 (appena comparsa) a 1 (sulla manovra). */
    public final float progress;
    @NonNull public final String exitRef, roadRef, destination, street, current;
    public final boolean motorway;
    /**
     * Di quanto gira la strada nella manovra, in gradi (+ a destra, − a sinistra, ±180 si torna indietro), dalla forma del
     * percorso ([TurnGeometry]). Rotonda: tra l'entrata e l'uscita (NaN: uscite a ventaglio); uscita: la rampa.
     */
    public float turnDeg = Float.NaN;

    Model(@NonNull Kind kind, boolean left, int lanes, int exitNum, float progress, @NonNull String exitRef,
          @NonNull String roadRef, @NonNull String destination, @NonNull String street, @NonNull String current,
          boolean motorway)
    {
      this.kind = kind;
      this.left = left;
      this.lanes = lanes;
      this.exitNum = exitNum;
      this.progress = progress;
      this.exitRef = exitRef;
      this.roadRef = roadRef;
      this.destination = destination;
      this.street = street;
      this.current = current;
      this.motorway = motorway;
    }
  }

  private EuriseJunctionScene() {}

  /** Metri da una distanza di CoMaps (che può essere in km, piedi o miglia). */
  static double meters(@Nullable Distance d)
  {
    if (d == null || !d.isValid())
      return -1;
    return switch (d.mUnits)
    {
      case Kilometers -> d.mDistance * 1000.0;
      case Feet -> d.mDistance * 0.3048;
      case Miles -> d.mDistance * 1609.344;
      default -> d.mDistance;
    };
  }

  /**
   * La scena per questa guida, o null se la manovra non ne ha una (svolta normale) o è ancora lontana. [fastRoad]: si
   * è su autostrada o superstrada (la scena compare prima).
   */
  @Nullable
  public static Model from(@Nullable RoutingInfo info, boolean fastRoad)
  {
    if (info == null || info.carDirection == null)
      return null;
    final double m = meters(info.distToTurn);
    if (m < 0)
      return null;
    final CarDirection d = info.carDirection;
    final boolean roundabout = CarDirection.isRoundAbout(d);
    final boolean exit = d == CarDirection.EXIT_HIGHWAY_TO_LEFT || d == CarDirection.EXIT_HIGHWAY_TO_RIGHT
                         || (info.nextIsLink && (d == CarDirection.TURN_SLIGHT_LEFT || d == CarDirection.TURN_SLIGHT_RIGHT));
    if (!roundabout && !exit)
      return null;
    final double show = exit && fastRoad ? 600 : 300;
    if (m > show)
      return null;
    final float progress = (float) Math.max(0, Math.min(1, 1 - m / show));
    if (roundabout)
    {
      final Model r = new Model(Kind.ROUNDABOUT, info.isLeftHandTraffic, 1, Math.max(1, info.exitNum), progress, "", "",
                                "", nz(info.nextStreet), nz(info.currentStreet), false);
      r.turnDeg = (float) TurnGeometry.roundabout(m);
      return r;
    }
    // Il lato dell'uscita (o dell'immissione sulla rampa) si legge dalla forma del percorso, quando è chiara: la scena
    // deve mostrare la strada vera (richiesta del 14 novembre); altrimenti vale l'indicazione di CoMaps.
    final double turn = TurnGeometry.simple(m);
    final boolean left = !Double.isNaN(turn) && Math.abs(turn) >= 6 ? turn < 0
                         : d == CarDirection.EXIT_HIGHWAY_TO_LEFT || d == CarDirection.TURN_SLIGHT_LEFT;
    final LaneInfo[] lanes = info.lanes;
    int main = lanes == null ? 3 : Math.max(2, Math.min(5, lanes.length - countExitLanes(lanes, left)));
    final String roadRef = nz(info.nextDestinationRef).isEmpty() ? nz(info.nextRef) : nz(info.nextDestinationRef);
    final String dest = nz(info.nextDestination).isEmpty() ? nz(info.nextName) : nz(info.nextDestination);
    final Model e = new Model(Kind.EXIT, left, main, 0, progress, nz(info.nextJunctionRef), roadRef, dest,
                              nz(info.nextStreet), nz(info.currentStreet), fastRoad);
    e.turnDeg = (float) turn;
    return e;
  }

  /** Le corsie che vanno all'uscita, contate dal suo lato (non sono corsie della strada che continua). */
  private static int countExitLanes(@NonNull LaneInfo[] lanes, boolean left)
  {
    int n = 0;
    for (int k = 0; k < lanes.length; k++)
    {
      final LaneInfo l = lanes[left ? k : lanes.length - 1 - k];
      final LaneWay a = l.mActiveLaneWay;
      if (a == null || a == LaneWay.None || a == LaneWay.Through)
        break;
      n++;
    }
    return Math.min(n, 1);
  }

  @NonNull
  private static String nz(@Nullable String s)
  {
    return s == null ? "" : s.trim();
  }

  /** L'immagine ferma per Android Auto (finestrella della manovra). */
  @Nullable
  public static Bitmap bitmap(@NonNull Context c, @Nullable Model model, int width, int height)
  {
    if (model == null || width <= 0 || height <= 0)
      return null;
    final Bitmap b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    // Un istante in cui le frecce sono tutte ben visibili.
    draw(c, new Canvas(b), model, width, height, 0.9f);
    return b;
  }

  /** Disegna la scena in [w]×[h] al tempo [t] (secondi, per le frecce e il lampeggio). */
  public static void draw(@NonNull Context c, @NonNull Canvas canvas, @NonNull Model m, float w, float h, float t)
  {
    final float s = Math.max(w / W, h / H);
    canvas.save();
    canvas.translate((w - W * s) / 2f, (h - H * s) / 2f);
    canvas.scale(s, s);
    if (m.kind == Kind.EXIT)
      drawExit(c, canvas, m, t);
    else
      drawRoundabout(c, canvas, m, t);
    canvas.restore();
    drawProgress(canvas, w, h, m.progress);
  }

  // ---- Uscita -------------------------------------------------------------------------------------------------

  private static void drawExit(@NonNull Context c, @NonNull Canvas k, @NonNull Model m, float t)
  {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    sky(k, p, 0xFF9DBBD8, 0xFFDCE7EF, 106);
    p.setShader(null);
    p.setColor(0xFFA7B79A);
    k.drawPath(path("M0 92 C60 84 110 88 160 85 C220 82 300 78 390 88 L390 106 L0 106 Z"), p);
    p.setShader(new LinearGradient(0, 105, 0, H, 0xFFB9C7A6, 0xFF93A884, Shader.TileMode.CLAMP));
    k.drawRect(0, 105, W, H, p);

    k.save();
    if (m.left)
      k.scale(-1, 1, W / 2f, 0);
    final Shader asphalt = new LinearGradient(0, 105, 0, H, 0xFF7B818B, 0xFF4E545E, Shader.TileMode.CLAMP);
    p.setShader(asphalt);
    k.drawPath(path("M-40 260 L156 105 L214 105 L302 260 Z"), p);
    final Path ramp = path("M302 260 L440 260 C368 200 330 169 300 155.5 C318 141 350 134 400 130 L400 119 "
                           + "C340 122 300 130 276 145 L244 155.5 Z");
    k.drawPath(ramp, p);
    p.setShader(null);
    p.setColor(0xFFE9E4D8);
    k.drawPath(path("M244 155.5 L214 105 L222 105 L276 145 Z"), p);

    // La corsia di uscita colorata, che respira piano.
    p.setColor(0xFF3E5C8A);
    p.setAlpha((int) (255 * (0.35f + 0.25f * pulse(t, PULSE))));
    k.drawPath(path("M302 260 L440 260 C368 200 330 169 300 155.5 L244 155.5 Z"), p);
    p.setAlpha(128);
    k.drawPath(path("M300 155.5 C318 141 350 134 400 130 L400 119 C340 122 300 130 276 145 Z"), p);
    p.setAlpha(255);

    final Paint line = stroke(Color.WHITE, 3f);
    k.drawLine(-40, 260, 156, 105, line);
    k.drawPath(path("M440 260 C368 200 330 169 300 155.5 C318 141 350 134 400 130"), line);
    line.setStrokeWidth(2.6f);
    line.setPathEffect(new DashPathEffect(new float[] {16, 14}, 0));
    for (int i = 1; i < m.lanes; i++)
    {
      final float f = i / (float) m.lanes;
      k.drawLine(-40 + 342 * f, 260, 156 + 58 * f, 105, line);
    }
    line.setStrokeWidth(5f);
    line.setPathEffect(new DashPathEffect(new float[] {8, 7}, 0));
    k.drawLine(302, 260, 244, 155.5f, line);
    line.setPathEffect(null);
    line.setStrokeWidth(2.6f);
    k.drawLine(244, 155.5f, 214, 105, line);

    // Frecce che scorrono dritte dalla corsia di destra verso l'uscita: non girano.
    final float laneX = -40 + 342 * (m.lanes - 0.5f) / m.lanes;
    for (int i = 0; i < 3; i++)
      flowArrow(k, (t + i * PERIOD / 3f) % PERIOD / PERIOD, laneX + 10, 256, 306, 170, 28, 1.8f);
    k.restore();

    gantry(c, k, m, t);
  }

  /** Il portale con i cartelli: a sinistra la strada che continua, dalla parte dell'uscita il cartello che lampeggia. */
  private static void gantry(@NonNull Context c, @NonNull Canvas k, @NonNull Model m, float t)
  {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(0xFF7A8090);
    k.drawRect(14, 0, 19, 105, p);
    k.drawRect(371, 0, 376, 105, p);
    p.setColor(0xFF8A909C);
    k.drawRect(14, 4, 376, 9, p);

    final int sign = m.motorway || m.roadRef.startsWith("A") ? 0xFF2F7D46 : 0xFF2B4F8E;
    final RectF straight = m.left ? new RectF(202, 14, 368, 80) : new RectF(24, 14, 194, 80);
    final RectF exit = m.left ? new RectF(24, 14, 190, 80) : new RectF(198, 14, 368, 80);
    p.setColor(sign);
    k.drawRoundRect(straight, 7, 7, p);
    k.drawRoundRect(exit, 7, 7, p);

    final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    txt.setTypeface(Typeface.DEFAULT_BOLD);
    txt.setColor(Color.WHITE);
    txt.setTextSize(15);
    final String cur = m.current.isEmpty() ? "" : m.current;
    text(k, txt, cur, straight.left + 10, straight.top + 24, straight.width() - 20);
    txt.setTextSize(13);
    text(k, txt, "↓  ↓  ↓", straight.left + 10, straight.bottom - 10, straight.width() - 20);

    // Il cartello dell'uscita lampeggia piano (bordo bianco).
    final Paint border = stroke(Color.WHITE, 3.5f);
    border.setAlpha((int) (255 * (0.35f + 0.65f * pulse(t, PULSE))));
    k.drawRoundRect(exit, 7, 7, border);
    float y = exit.top + 8;
    if (!m.exitRef.isEmpty())
    {
      final String badge = c.getString(R.string.eurise_junction_exit, m.exitRef).toUpperCase();
      txt.setTextSize(11);
      final float bw = txt.measureText(badge) + 10;
      p.setColor(Color.WHITE);
      k.drawRoundRect(new RectF(exit.left + 9, y, exit.left + 9 + bw, y + 16), 4, 4, p);
      txt.setColor(sign);
      k.drawText(badge, exit.left + 14, y + 12.5f, txt);
      txt.setColor(Color.WHITE);
      y += 18;
    }
    final String title = TextUtils.isEmpty(m.roadRef) ? m.street : m.roadRef + (m.street.isEmpty() ? "" : " " + m.street);
    txt.setTextSize(14.5f);
    text(k, txt, title, exit.left + 9, y + 15, exit.width() - 18);
    if (!m.destination.isEmpty())
    {
      txt.setTypeface(Typeface.DEFAULT);
      txt.setTextSize(11.5f);
      txt.setColor(0xFFE7F0E9);
      text(k, txt, m.destination + (m.left ? "  ↖" : "  ↗"), exit.left + 9, exit.bottom - 8, exit.width() - 18);
    }
  }

  // ---- Rotonda ------------------------------------------------------------------------------------------------

  private static final float CX = 195, CY = 156, RX = 150, RY = 50, LRX = 112, LRY = 36;

  private static void drawRoundabout(@NonNull Context c, @NonNull Canvas k, @NonNull Model m, float t)
  {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    sky(k, p, 0xFFA9C3DB, 0xFFE1E9EF, 96);
    p.setShader(null);
    p.setColor(0xFFC9C3B6);
    k.drawRect(10, 56, 80, 96, p);
    k.drawRect(88, 66, 132, 96, p);
    k.drawRect(262, 60, 320, 96, p);
    k.drawRect(326, 50, 390, 96, p);
    p.setColor(0xFFA8B897);
    k.drawRect(0, 95, W, H, p);

    k.save();
    if (m.left)
      k.scale(-1, 1, W / 2f, 0);
    final float[] angles = exitAngles(m);
    final int count = angles.length;

    final Shader asphalt = new LinearGradient(0, 95, 0, H, 0xFF80868F, 0xFF535963, Shader.TileMode.CLAMP);
    p.setShader(asphalt);
    k.drawPath(path("M150 262 L176 196 L236 196 L262 262 Z"), p);
    for (float a : angles)
      k.drawPath(exitRoad(a), p);
    k.drawOval(new RectF(CX - RX, CY - RY, CX + RX, CY + RY), p);
    p.setShader(null);
    p.setColor(0xFF8FAE7E);
    final RectF island = new RectF(CX - 76, CY - 22, CX + 76, CY + 22);
    k.drawOval(island, p);
    k.drawOval(island, stroke(0xFFE9E4D8, 5));
    final Paint dash = stroke(Color.WHITE, 2.6f);
    dash.setPathEffect(new DashPathEffect(new float[] {14, 12}, 0));
    k.drawOval(new RectF(CX - LRX, CY - LRY, CX + LRX, CY + LRY), dash);

    // Il percorso: dall'ingresso in basso, lungo l'anello nel verso di marcia, fuori dall'uscita giusta.
    final float target = angles[Math.min(m.exitNum, count) - 1];
    final Path route = new Path();
    route.moveTo(206, 262);
    route.lineTo(208, CY + LRY + 4);
    for (float a = 70; a >= target; a -= 4)
      route.lineTo(ringX(a), ringY(a));
    route.lineTo(ringX(target), ringY(target));
    final float[] out = outer(target, 1.0f);
    // Uscita sulla via da cui si è entrati (inversione): si esce sul lato opposto della stessa via.
    final boolean back = backToEntry(target);
    if (back)
      out[0] -= 14;
    route.lineTo(out[0], out[1]);
    final Paint band = stroke(0xFF3E5C8A, 24);
    band.setAlpha((int) (255 * (0.35f + 0.25f * pulse(t, PULSE))));
    k.drawPath(route, band);

    // Frecce dipinte ferme che si accendono una dopo l'altra nel verso di marcia.
    final PathMeasure pm = new PathMeasure(route, false);
    final float len = pm.getLength();
    final float[] pos = new float[2], tan = new float[2];
    for (int i = 0; i < 6; i++)
    {
      pm.getPosTan(len * (0.08f + i * 0.13f), pos, tan);
      final float on = pulse(t - i * 0.3f, PULSE);
      chevron(k, pos[0], pos[1], (float) Math.toDegrees(Math.atan2(tan[1], tan[0])) + 90, 0.35f + 0.65f * on);
    }
    // All'uscita frecce che scorrono dritte verso la via.
    final float[] from = outer(target, 0.25f), to = outer(target, 0.95f);
    if (back)
    {
      from[0] -= 14;
      to[0] -= 14;
    }
    final float dir = (float) Math.toDegrees(Math.atan2(to[1] - from[1], to[0] - from[0])) + 90;
    for (int i = 0; i < 3; i++)
      flowArrow(k, (t + i * PERIOD / 3f) % PERIOD / PERIOD, from[0], from[1], to[0], to[1], dir, 1.5f);
    k.restore();

    // I numeri delle uscite come si incontrano (fuori dallo specchio, perché il testo resti leggibile).
    final Paint num = new Paint(Paint.ANTI_ALIAS_FLAG);
    num.setTypeface(Typeface.DEFAULT_BOLD);
    num.setTextAlign(Paint.Align.CENTER);
    num.setTextSize(14);
    for (int i = 0; i < count; i++)
    {
      final float[] o = outer(angles[i], 1.05f);
      // Il numero dell'uscita che coincide con l'entrata va accanto alla via, non sopra.
      if (backToEntry(angles[i]))
        o[0] -= 46;
      final float x = m.left ? W - o[0] : o[0];
      final boolean mine = i == Math.min(m.exitNum, count) - 1;
      p.setColor(mine ? 0xFF3E5C8A : Color.WHITE);
      k.drawCircle(x, o[1], mine ? 15 : 13, p);
      k.drawCircle(x, o[1], mine ? 15 : 13, stroke(mine ? Color.WHITE : 0xFF8A909C, mine ? 3 : 2));
      num.setColor(mine ? Color.WHITE : 0xFF6B7280);
      k.drawText(String.valueOf(i + 1), x, o[1] + 5, num);
    }

    // Il cartello: «3ª uscita · Via Lecco».
    final String label = c.getString(R.string.eurise_junction_roundabout, m.exitNum)
                         + (m.street.isEmpty() ? "" : " · " + m.street);
    final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    txt.setTypeface(Typeface.DEFAULT_BOLD);
    txt.setTextSize(13);
    txt.setColor(0xFF1F2A44);
    final float tw = Math.min(W - 40, txt.measureText(label) + 24);
    final RectF board = new RectF(W - 16 - tw, H - 44, W - 16, H - 14);
    p.setColor(Color.WHITE);
    k.drawRoundRect(board, 6, 6, p);
    k.drawRoundRect(board, 6, 6, stroke(0xFF1F2A44, 2));
    text(k, txt, label, board.left + 12, board.bottom - 10, board.width() - 24);
  }

  /**
   * Gli angoli delle uscite sullo schermo (0 = destra, −90 = in alto, 90 = in basso dove si entra), nel verso di marcia.
   * Con la forma del percorso l'uscita giusta sta dove va davvero la strada: dritto in alto, a destra a destra, a
   * sinistra a sinistra, l'inversione in basso sulla via d'entrata; le uscite prima si dispongono tra l'entrata e lei.
   * Senza, a ventaglio nella metà alta come nella tavola.
   */
  @NonNull
  static float[] exitAngles(@NonNull Model m)
  {
    if (Float.isNaN(m.turnDeg))
    {
      final int count = Math.max(3, m.exitNum);
      final float[] a = new float[count];
      for (int i = 0; i < count; i++)
        a[i] = -10f - i * (160f / (count - 1));
      return a;
    }
    // Si guida a sinistra: la scena è specchiata, quindi la svolta si legge al contrario.
    float turn = m.left ? -m.turnDeg : m.turnDeg;
    // Nel verso di marcia (antiorario sullo schermo) dall'entrata: a destra subito, poi dritto, sinistra, indietro.
    float target = -90f + turn;
    // Oltre i 120° a destra è un'inversione presa dall'altra parte: si fa il giro.
    if (target > 30f)
      target -= 360f;
    target = Math.max(-350f, Math.min(-10f, target));
    final int n = Math.max(1, m.exitNum);
    final int extra = target > -250f ? 1 : 0; // un'uscita dopo la nostra, se c'è posto
    final float[] a = new float[n + extra];
    final float first = Math.max(target, -25f);
    for (int i = 0; i < n; i++)
      a[i] = n == 1 ? target : first + (target - first) * i / (n - 1);
    if (extra == 1)
      a[n] = (target + -330f) / 2f;
    return a;
  }

  /** L'uscita è in basso, sulla via da cui si entra. */
  static boolean backToEntry(float deg)
  {
    return Math.sin(Math.toRadians(deg)) > 0.9;
  }

  private static float ringX(float deg)
  {
    return CX + LRX * (float) Math.cos(Math.toRadians(deg));
  }

  private static float ringY(float deg)
  {
    return CY + LRY * (float) Math.sin(Math.toRadians(deg));
  }

  /** Un punto sulla via d'uscita all'angolo [deg]: [f] = 0 sull'anello esterno, 1 in fondo alla via. */
  @NonNull
  private static float[] outer(float deg, float f)
  {
    final double r = Math.toRadians(deg);
    final float ex = CX + RX * (float) Math.cos(r), ey = CY + RY * (float) Math.sin(r);
    final float fx = CX + (RX + 40) * (float) Math.cos(r), fy = CY + (RY + 46) * (float) Math.sin(r) - 8;
    return new float[] {ex + (fx - ex) * f, ey + (fy - ey) * f};
  }

  @NonNull
  private static Path exitRoad(float deg)
  {
    final Path p = new Path();
    final float[] a1 = edge(deg - 9, 0.92f), a2 = edge(deg + 9, 0.92f);
    final float[] b1 = outer(deg - 4, 1.3f), b2 = outer(deg + 4, 1.3f);
    p.moveTo(a1[0], a1[1]);
    p.lineTo(b1[0], b1[1]);
    p.lineTo(b2[0], b2[1]);
    p.lineTo(a2[0], a2[1]);
    p.close();
    return p;
  }

  @NonNull
  private static float[] edge(float deg, float f)
  {
    final double r = Math.toRadians(deg);
    return new float[] {CX + RX * f * (float) Math.cos(r), CY + RY * f * (float) Math.sin(r)};
  }

  // ---- Pezzi comuni -------------------------------------------------------------------------------------------

  private static void sky(@NonNull Canvas k, @NonNull Paint p, int top, int bottom, float until)
  {
    p.setShader(new LinearGradient(0, 0, 0, until, top, bottom, Shader.TileMode.CLAMP));
    k.drawRect(-W, -H, 2 * W, until, p);
    p.setShader(null);
  }

  /** 0…1…0 in [period] secondi. */
  private static float pulse(float t, float period)
  {
    final float x = ((t % period) + period) % period / period;
    return (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * x));
  }

  /** La freccia piena che scorre da (x0,y0) a (x1,y1) a [f] del giro: compare, scorre, svanisce. Non ruota. */
  private static void flowArrow(@NonNull Canvas k, float f, float x0, float y0, float x1, float y1, float deg, float scale)
  {
    final float alpha = f < 0.2f ? f / 0.2f : f > 0.75f ? (1 - f) / 0.25f : 1f;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(Color.WHITE);
    p.setAlpha((int) (255 * Math.max(0, Math.min(1, alpha))));
    k.save();
    k.translate(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f);
    k.rotate(deg);
    k.scale(scale, scale);
    k.drawPath(path("M-10 6 L0 -8 L10 6 L5 6 L0 -1 L-5 6 Z"), p);
    k.restore();
  }

  private static void chevron(@NonNull Canvas k, float x, float y, float deg, float alpha)
  {
    final Paint p = stroke(Color.WHITE, 3.2f);
    p.setAlpha((int) (255 * alpha));
    k.save();
    k.translate(x, y);
    k.rotate(deg);
    k.scale(1.4f, 1.4f);
    k.drawPath(path("M-7 4 L0 -5 L7 4"), p);
    k.restore();
  }

  private static void drawProgress(@NonNull Canvas k, float w, float h, float progress)
  {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final float d = w / W;
    final RectF bar = new RectF(w - 14 * d, h * 0.42f, w - 7 * d, h * 0.92f);
    p.setColor(0x80FFFFFF);
    k.drawRoundRect(bar, 4 * d, 4 * d, p);
    p.setColor(Color.WHITE);
    k.drawRoundRect(new RectF(bar.left, bar.bottom - bar.height() * (1 - progress), bar.right, bar.bottom), 4 * d, 4 * d, p);
  }

  @NonNull
  private static Paint stroke(int color, float width)
  {
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeCap(Paint.Cap.ROUND);
    p.setStrokeJoin(Paint.Join.ROUND);
    p.setStrokeWidth(width);
    p.setColor(color);
    return p;
  }

  private static void text(@NonNull Canvas k, @NonNull Paint p, @NonNull String s, float x, float y, float max)
  {
    if (s.isEmpty())
      return;
    String t = s;
    while (t.length() > 1 && p.measureText(t) > max)
      t = t.substring(0, t.length() - 2) + "…";
    k.drawText(t, x, y, p);
  }

  /** Un percorso dalla sintassi SVG (solo M, L, C, Z con coordinate assolute): basta per le forme della scena. */
  @NonNull
  static Path path(@NonNull String d)
  {
    final Path p = new Path();
    final String[] tok = d.replace(",", " ").trim().split("\\s+|(?=[MLCZ])|(?<=[MLCZ])");
    char cmd = 'M';
    final float[] v = new float[6];
    int n = 0;
    for (String raw : tok)
    {
      final String s = raw.trim();
      if (s.isEmpty())
        continue;
      final char ch = s.charAt(0);
      if (s.length() == 1 && Character.isLetter(ch))
      {
        cmd = ch;
        n = 0;
        if (cmd == 'Z')
          p.close();
        continue;
      }
      v[n++] = Float.parseFloat(s);
      if (cmd == 'M' && n == 2)
      {
        p.moveTo(v[0], v[1]);
        n = 0;
        cmd = 'L';
      }
      else if (cmd == 'L' && n == 2)
      {
        p.lineTo(v[0], v[1]);
        n = 0;
      }
      else if (cmd == 'C' && n == 6)
      {
        p.cubicTo(v[0], v[1], v[2], v[3], v[4], v[5]);
        n = 0;
      }
    }
    return p;
  }
}

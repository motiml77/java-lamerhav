package unit4.collectionsLib;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import unit4.collectionsLib.Model.Access;
import unit4.collectionsLib.Model.Cell;
import unit4.collectionsLib.Model.ChainView;
import unit4.collectionsLib.Model.LinearView;
import unit4.collectionsLib.Model.ProblemView;
import unit4.collectionsLib.Model.ArrayView;
import unit4.collectionsLib.Model.StringView;
import unit4.collectionsLib.Model.LoopView;
import unit4.collectionsLib.Model.LoopGridView;
import unit4.collectionsLib.Model.MatrixView;
import unit4.collectionsLib.Model.Mark;
import unit4.collectionsLib.Model.Obj;
import unit4.collectionsLib.Model.ObjView;
import unit4.collectionsLib.Model.Step;
import unit4.collectionsLib.Model.TreeCell;
import unit4.collectionsLib.Model.TreeView;
import unit4.collectionsLib.Model.View;

/** מצייר את כל המבנים של צעד אחד */
final class StructPanel extends JPanel {
    static final Color BOX = new Color(0xEAF1FD);
    static final Color BOX_LINE = new Color(0x8FA6CC);
    static final Color NEW_C = new Color(0xC6F0D2);
    static final Color READ_C = new Color(0xFFEDA6);
    static final Color CHANGED_C = new Color(0xFFD3A3);
    static final Color GHOST = new Color(0xDC2626);
    static final Color GHOST_BG = new Color(0xFDE4E4);
    static final Color CURRENT = new Color(0xF97316);
    private static final Color LINE = new Color(0x3B4A63);
    private static final Color FOCUS = new Color(0x2F6FED);
    private static final Color FOCUS_BG = new Color(0xE8F0FE);
    private static final Color LABEL = new Color(0x7C3AED);
    private static final Color MUTED = new Color(0x8A93A3);
    private static final Color INK = new Color(0x1D2433);

    private final Font VALUE = Theme.font(Theme.boldFamily, Theme.boldStyle, 16);
    private final Font TITLE = Theme.font(Theme.boldFamily, Theme.boldStyle, 15);
    private final Font TYPE = Theme.font(Theme.family, Font.PLAIN, 13);
    private final Font SMALL = Theme.font(Theme.family, Font.PLAIN, 13);
    private final Font SMALL_B = Theme.font(Theme.boldFamily, Theme.boldStyle, 14);

    // כרטיס של אובייקט (Student, Car...): שם + שורת פרטים, וטבלת מאפיינים
    private final Font CARD_T = Theme.font(Theme.boldFamily, Theme.boldStyle, 15);
    private final Font CARD_S = Theme.font(Theme.family, Font.PLAIN, 12);
    private final Font FIELD = Theme.font(Theme.family, Font.PLAIN, 13);
    private final Font FIELD_B = Theme.font(Theme.boldFamily, Theme.boldStyle, 13);
    private final Font BADGE = Theme.font(Theme.boldFamily, Theme.boldStyle, 11);
    private static final int OBJ_H = 48, ROW_H = 18;

    private static final int PAD = 20, GAP = 36, TITLE_H = 30, ARC = 8;
    private static final Stroke THIN = new BasicStroke(1.3f);
    private static final Stroke THICK = new BasicStroke(3f);
    private static final Stroke DASHED = new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
            new float[] { 6f, 4f }, 0f);

    private Step step;
    /** מקום כל כרטיס אובייקט בציור האחרון (לקישור ה-Access ולחלונית המידע): זהות → {מלבן, האם במחסנית} */
    private final Map<Integer, Object[]> objRects = new HashMap<Integer, Object[]>();
    private final List<Object[]> objHits = new ArrayList<Object[]>();      // {מלבן במערכת הציור, אובייקט}
    private final List<Object[]> screenHits = new ArrayList<Object[]>();   // {מלבן על המסך, אובייקט} — לחלונית על העכבר
    private boolean inStack;
    /** הגדלה: 0 = התאמה אוטומטית לרוחב (רק הקטנה, עד 55%); אחרת פי כמה */
    private double manualZoom = 0, zoom = 1;
    private Runnable zoomListener;

    StructPanel() {
        setBackground(Color.WHITE);
        setToolTipText("");   // מפעיל חלוניות מידע (getToolTipText)
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) { repaint(); }
        });
    }

    void setStep(Step s) {
        setStep(s, false, 0);
    }

    // ------------------------------------------------------------ הנפשה של הכנסה / הוצאה (StructAnim)

    private StructAnim anim;
    /** בזמן מדידת הציור הקודם: מפתח → המקום (ראו captureLayout) */
    private Map<String, Rectangle> capture;
    private Map<View, String> capViews;
    private Map<Cell, String> capCells;
    /** המבנה הראשי שמצויר עכשיו (המפתח שלו), בזמן הנפשה / מדידה — אחרת null */
    private String curView;
    /** מציירים איבר שהוצא (בהנפשה) — בלי חלונית מידע עליו */
    private boolean noHits;
    /** בדיקות בלי חלון: מנפישים גם כשהלוח לא על המסך */
    boolean offscreen;

    /**
     * צעד חדש. animate — התקדמו בדיוק צעד אחד קדימה ("הבא" / הפעלה אוטומטית): מנפישים את מה שנוסף / הוצא מאז הצעד
     * שמוצג עכשיו. interval = כל כמה זמן מתקדמת ההפעלה האוטומטית (0 = לא פועלת) — ההנפשה מתקצרת בהתאם
     */
    void setStep(Step s, boolean animate, int interval) {
        stopAnim();
        Step prev = step;
        step = s;
        if (animate && prev != null && s != null && prev != s && (offscreen || isShowing())) {
            try {
                StructAnim a = StructAnim.build(prev, s, this);
                if (a != null) {
                    captureLayout(prev, a);
                    a.viewport = viewportWidth();
                    if (a.begin(interval)) anim = a;
                }
            } catch (RuntimeException e) {
                anim = null;   // בלי הנפשה — רק הצעד
            }
        }
        scrollPending = true;
        revalidate();
        repaint();
    }

    private void stopAnim() {
        if (anim != null) anim.stop();
        anim = null;
    }

    /** יש עכשיו הנפשה (גם קפואה) */
    boolean animating() {
        return anim != null;
    }

    StructAnim anim() {
        return anim;
    }

    /**
     * המקומות של המבנים והאיברים בציור של הצעד הקודם — מציירים אותו "בלי מסך" (על תמונה קטנה) ורושמים.
     * כך כל איבר יודע מאיפה הוא בא, גם אם עבר למבנה אחר (חוליה שנכנסה לרשימה)
     */
    private void captureLayout(Step prev, StructAnim a) {
        Step keep = step;
        Map<Integer, Object[]> keepRects = new HashMap<Integer, Object[]>(objRects);
        List<Object[]> keepHits = new ArrayList<Object[]>(objHits);
        Rectangle keepFocus = focusRect;
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        step = prev;
        capture = new HashMap<String, Rectangle>();
        capViews = a.fromViews;
        capCells = a.fromCells;
        try {
            Dimension d = layoutAll(g);
            a.oldTranslate = Math.max(0, (int) (getWidth() - d.width * zoom)) / 2;
            a.old = capture;
        } finally {
            g.dispose();
            step = keep;
            capture = null;
            capViews = null;
            capCells = null;
            curView = null;
            nest = 0;
            objRects.clear();
            objRects.putAll(keepRects);
            objHits.clear();
            objHits.addAll(keepHits);
            focusRect = keepFocus;
        }
    }

    /** איפה לצייר מבנה ראשי: בזמן הנפשה — בדרך מהמקום הקודם שלו לחדש; וגם קובע אותו כ"מבנה שמצויר עכשיו" */
    private Point place(View v, int x, int y) {
        curView = null;
        if (capture != null) {
            String k = capViews.get(v);
            if (k != null) {
                capture.put("V|" + k, new Rectangle(x, y, 0, 0));
                curView = k;
            }
            return new Point(x, y);
        }
        if (anim != null) {
            String k = anim.toViews.get(v);
            if (k != null) {
                curView = k;
                Point p = anim.origin(k, x, y);
                anim.dx = x - p.x;
                anim.dy = y - p.y;
                return p;
            }
        }
        return new Point(x, y);
    }

    /** האם לרשום / להנפיש את האיברים של המבנה שמצויר עכשיו (רק מבנה ראשי, לא מבנה בתוך תא) */
    private boolean hooked() {
        return nest == 0 && curView != null && (capture != null || anim != null);
    }

    /** עותק של g שמצייר שקוף (alpha כפול השקיפות שכבר יש ל-g — מבנה חדש שמופיע, ובתוכו חוליה שנופלת) */
    private static Graphics2D faded(Graphics2D g, float alpha) {
        Graphics2D g2 = (Graphics2D) g.create();
        if (alpha < 1f) {
            float base = g.getComposite() instanceof AlphaComposite ? ((AlphaComposite) g.getComposite()).getAlpha() : 1f;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, Math.min(1f, alpha * base))));
        }
        return g2;
    }

    /** בדיקות: איפה צוירו (על הלוח, בפיקסלים) האיברים שהוצאו, בציור האחרון */
    final List<Rectangle> exitBounds = new ArrayList<Rectangle>();

    /** האיברים שהוצאו: מעל הכול, בדרך החוצה — מאדימים, מתכווצים ונמחקים */
    private void drawExits(Graphics2D g) {
        for (StructAnim.Exit x : anim.exits) {
            float[] f = anim.exit(x);
            if (f == null) continue;
            int w = Math.round(f[2]), h = Math.round(f[3]), px = Math.round(f[0]), py = Math.round(f[1]);
            Graphics2D g2 = faded(g, f[4]);
            double cx = f[0] + f[2] / 2.0, cy = f[1] + f[3] / 2.0;
            g2.translate(cx, cy);
            g2.scale(f[5], f[5]);
            g2.translate(-cx, -cy);
            exitBounds.add(g2.getTransform().createTransformedShape(new Rectangle(px, py, w, h)).getBounds());
            float[] l = x.cell != null ? anim.exitLink(x, f, Math.min(20, w / 3)) : null;
            if (l != null) {   // החץ שיצא מהחוליה — נחתך ונעלם כשהיא נופלת
                Graphics2D ga = faded(g, l[4]);
                arrow(ga, Math.round(l[0]), Math.round(l[1]), Math.round(l[2]), Math.round(l[3]), f[6] > 0.5f ? GHOST : LINE);
                ga.dispose();
            }
            boolean was = noHits;
            noHits = true;
            try {
                if (x.cell != null) {
                    int pw = Math.min(20, w / 3), vw = w - pw;
                    cell(g2, px, py, vw, h, color(x.cell.mark), x.cell.value, x.cell.obj, x.cell.sub);
                    box(g2, px + vw, py, pw, h, color(x.cell.mark), null, false);
                    g2.setColor(LINE);
                    g2.fillOval(px + vw + pw / 2 - 3, py + h / 2 - 3, 7, 7);
                } else {
                    cell(g2, px, py, w, h, color(x.mark), x.text, x.obj, x.sub);
                }
                if (f[6] > 0.01f) {   // מאדים: הסימן "הוצא" מעליו
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, f[4] * f[6])));
                    String text = x.cell != null ? x.cell.value : x.obj != null ? x.obj.title : x.sub != null ? "" : x.text;
                    if (x.cell != null) {
                        int pw = Math.min(20, w / 3);
                        ghostBox(g2, px, py, w - pw, h, text);
                        ghostBox(g2, px + w - pw, py, pw, h, "");
                    } else {
                        ghostBox(g2, px, py, w, h, text);
                    }
                }
            } finally {
                noHits = was;
                g2.dispose();
            }
        }
    }

    void setZoomListener(Runnable r) { zoomListener = r; }

    /** תצוגה קטנה (ליד מעקב הרקורסיה): מקטינים כך שהכול ייכנס גם לגובה */
    private boolean fitHeight;

    void setFitHeight(boolean b) { fitHeight = b; }

    void zoomBy(double f) {
        stopAnim();
        manualZoom = Math.max(0.4, Math.min(2.5, zoom * f));
        revalidate();
        repaint();
    }

    void fit() {
        stopAnim();
        manualZoom = 0;
        revalidate();
        repaint();
    }

    String zoomText() {
        return Math.round(zoom * 100) + "%";
    }

    private int viewportWidth() {
        return getParent() != null && getParent().getWidth() > 100 ? getParent().getWidth() : 600;
    }

    private double computeZoom(Dimension natural) {
        // התאמה אוטומטית: מקטינים (עד 70%) כך שהמבנים ייכנסו ברוחב; בגובה — גוללים (כדי שהכתוב יישאר קריא)
        double fit = (viewportWidth() - 4) / (double) natural.width;
        if (fitHeight && getParent() != null && getParent().getHeight() > 40)
            fit = Math.min(fit, (getParent().getHeight() - 4) / (double) natural.height);
        double z = manualZoom > 0 ? manualZoom : Math.max(fitHeight ? 0.5 : 0.7, Math.min(1, fit));
        if (z != zoom) {
            zoom = z;
            if (zoomListener != null) {
                SwingUtilities.invokeLater(zoomListener);
            }
        }
        return z;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = layoutAll(null);
        double z = computeZoom(d);
        return new Dimension((int) Math.ceil(d.width * z), (int) Math.ceil(d.height * z));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        // הנפשה: נגמרה — מציירים כרגיל; הגדילו / הקטינו את החלון באמצע — נגמרת מיד
        if (anim != null && (anim.finished() || anim.viewport != viewportWidth())) stopAnim();
        Dimension d = layoutAll(null);
        double z = computeZoom(d);
        // כשיש מקום — המבנים במרכז
        int free = (int) (getWidth() - d.width * z);
        if (free > 0) g.translate(free / 2, 0);
        g.scale(z, z);
        focusRect = null;
        objRects.clear();
        objHits.clear();
        exitBounds.clear();
        if (anim != null) {
            anim.shiftX = (float) ((anim.oldTranslate - Math.max(free, 0) / 2) / z);
            anim.ghosts.clear();
            anim.now.clear();
        }
        if (anim == null) {
            layoutAll(g);
        } else {
            try {
                layoutAll(g);
                curView = null;
                drawExits(g);
            } catch (RuntimeException e) {   // משהו בהנפשה נכשל — בלי הנפשה, רק הצעד
                stopAnim();
                nest = 0;
                curView = null;
                g.dispose();
                repaint();
                return;
            }
        }
        curView = null;
        screenHits.clear();
        for (Object[] h : objHits) {
            Rectangle r = (Rectangle) h[0];
            screenHits.add(new Object[] { new Rectangle(Math.max(free, 0) / 2 + (int) (r.x * z), (int) (r.y * z), (int) (r.width * z), (int) (r.height * z)), h[1] });
        }
        g.dispose();
        if (scrollPending && focusRect != null) {
            scrollPending = false;
            final Rectangle r = new Rectangle((int) (focusRect.x * z), (int) (focusRect.y * z),
                    (int) (focusRect.width * z) + 1, (int) (focusRect.height * z) + 1);
            SwingUtilities.invokeLater(new Runnable() {
                public void run() { scrollRectToVisible(r); }
            });
        } else if (scrollPending) {
            scrollPending = false;
        }
    }

    /** כשה-g הוא null רק מודדים את הגודל, בלי לצייר */
    private Dimension layoutAll(Graphics2D g) {
        int y = PAD, maxX = 240;
        if (step == null || step.views.isEmpty()) {
            String t = step == null ? "מחכה להרצה..." : "אין עדיין מבנים בצעד הזה";
            if (g != null) {
                g.setColor(MUTED);
                g.setFont(TITLE);
                g.drawString(t, PAD, PAD + 30);
            }
            return new Dimension(textWidth(t, TITLE) + 2 * PAD, 90);
        }
        // שגיאה: כרטיס אדום בראש — מה קרה, למה, ומה עושים
        for (View v : step.views) {
            if (!(v instanceof ProblemView)) continue;
            Point o = at(g, v, PAD, y);
            Dimension d = drawProblem(g, (ProblemView) v, o.x, o.y);
            y += d.height + GAP / 2;
            maxX = Math.max(maxX, PAD + d.width);
        }
        // לולאות: כרטיס לכל לולאה (הפנימית מוזחת מתחת לחיצונית), ולידן טבלת הזוגות של לולאות מקוננות
        int loopsH = 0, lx = PAD;
        for (View v : step.views) {   // המונים (לולאות מקוננות) — מעל הכרטיסים
            if (!(v instanceof Model.LoopCountersView)) continue;
            Point o = at(g, v, PAD, y);
            Dimension d = drawCounters(g, (Model.LoopCountersView) v, o.x, o.y);
            loopsH += d.height + 10;
            maxX = Math.max(maxX, PAD + d.width);
        }
        for (View v : step.views) {
            if (!(v instanceof LoopView)) continue;
            Point o = at(g, v, PAD, y + loopsH);
            Dimension d = drawLoop(g, (LoopView) v, o.x, o.y);
            loopsH += d.height + 8;
            lx = Math.max(lx, PAD + d.width);
            maxX = Math.max(maxX, PAD + d.width);
        }
        int gx = lx + GAP, gridH = 0;
        for (View v : step.views) {
            if (!(v instanceof LoopGridView)) continue;
            Point o = at(g, v, gx, y);
            Dimension d = drawLoopGrid(g, (LoopGridView) v, o.x, o.y);
            gx += d.width + GAP;
            gridH = Math.max(gridH, d.height);
            maxX = Math.max(maxX, gx);
        }
        if (loopsH > 0 || gridH > 0) y += Math.max(loopsH, gridH) + GAP / 2;
        // מערכים: זה ליד זה כל עוד יש מקום ברוחב החלון
        int avail = viewportWidth() > 200 ? viewportWidth() - PAD : 900;
        int ax = PAD, arrRow = 0;
        for (View v : step.views) {
            if (!(v instanceof ArrayView || v instanceof MatrixView || v instanceof StringView)) continue;
            Dimension d = measure(v);
            if (ax > PAD && ax + d.width > avail) { y += arrRow + GAP / 2; ax = PAD; arrRow = 0; }
            if (g != null) {
                Point o = place(v, ax, y);
                if (v instanceof ArrayView) drawArray(g, (ArrayView) v, o.x, o.y);
                else if (v instanceof StringView) drawStringView(g, (StringView) v, o.x, o.y);
                else drawMatrix(g, (MatrixView) v, o.x, o.y);
                noteFocus(v, ax, y, d);
            }
            ax += d.width + GAP;
            arrRow = Math.max(arrRow, d.height);
            maxX = Math.max(maxX, ax);
        }
        if (arrRow > 0) y += arrRow + GAP / 2;
        int x = PAD, rowH = 0;
        for (View v : step.views) {
            if (v instanceof LinearView && ((LinearView) v).stack) {
                Point o = at(g, v, x, y);
                Graphics2D gv = fresh(g);
                Dimension d = drawStack(gv, (LinearView) v, o.x, o.y);
                if (gv != g) gv.dispose();
                if (g != null) noteFocus(v, x, y, d);
                x += d.width + GAP;
                rowH = Math.max(rowH, d.height);
            }
        }
        if (rowH > 0) {
            maxX = Math.max(maxX, x);
            y += rowH + GAP / 2;
        }
        for (View v : step.views) {
            Dimension d = null;
            if (v instanceof LinearView && !((LinearView) v).stack) {
                Point o = at(g, v, PAD, y);
                Graphics2D gv = fresh(g);
                d = drawQueue(gv, (LinearView) v, o.x, o.y);
                if (gv != g) gv.dispose();
            }
            if (d != null && g != null) noteFocus(v, PAD, y, d);
            if (d != null) { y += d.height + GAP / 2; maxX = Math.max(maxX, PAD + d.width); }
        }
        for (View v : step.views) {
            Dimension d = null;
            if (v instanceof ChainView) {
                Point o = at(g, v, PAD, y);
                Graphics2D gv = fresh(g);
                d = drawChain(gv, (ChainView) v, o.x, o.y);
                if (gv != g) gv.dispose();
            }
            if (d != null && g != null) noteFocus(v, PAD, y, d);
            if (d != null) { y += d.height + GAP / 2; maxX = Math.max(maxX, PAD + d.width); }
        }
        for (View v : step.views) {
            Dimension d = null;
            if (v instanceof TreeView) {
                Point o = at(g, v, PAD, y);
                Graphics2D gv = fresh(g);
                d = drawTree(gv, (TreeView) v, o.x, o.y);
                if (gv != g) gv.dispose();
            }
            if (d != null && g != null) noteFocus(v, PAD, y, d);
            if (d != null) { y += d.height + GAP / 2; maxX = Math.max(maxX, PAD + d.width); }
        }
        // אובייקטים שמשתנה מצביע אליהם ואינם בתוך מבנה: כרטיס מלא (כל המאפיינים)
        int ox = PAD, orow = 0;
        for (View v : step.views) {
            if (!(v instanceof ObjView)) continue;
            Dimension d = drawObjView(null, (ObjView) v, 0, 0);
            if (ox > PAD && ox + d.width > avail) { y += orow + GAP / 2; ox = PAD; orow = 0; }   // כמו מערכים: שורה חדשה כשאין מקום
            if (g != null) {
                Point o = place(v, ox, y);
                drawObjView(g, (ObjView) v, o.x, o.y);
                noteFocus(v, ox, y, d);
            }
            ox += d.width + GAP;
            orow = Math.max(orow, d.height);
            maxX = Math.max(maxX, ox);
        }
        if (orow > 0) y += orow + GAP / 2;
        curView = null;
        return new Dimension(maxX + PAD, y + PAD);
    }

    /** בהנפשה: מבנה חדש (ראו StructAnim.fresh) מצויר שקוף בהתחלה, ומופיע אחרי שהשאר פינו לו מקום */
    private Graphics2D fresh(Graphics2D g) {
        if (g == null || anim == null || curView == null) return g;
        float a = anim.viewAlpha(curView);
        return a >= 1f ? g : faded(g, a);
    }

    /** מדידה (g == null): המקום עצמו; ציור — דרך place (בהנפשה: המבנה בדרך מהמקום הקודם שלו) */
    private Point at(Graphics2D g, View v, int x, int y) {
        return g == null ? new Point(x, y) : place(v, x, y);
    }

    /** המבנה שהצעד נגע בו — כדי לגלול אליו אם הוא מחוץ לחלון */
    private Rectangle focusRect;
    private boolean scrollPending;

    private void noteFocus(View v, int x, int y, Dimension d) {
        if (v.focus && focusRect == null) focusRect = new Rectangle(x, y, d.width, d.height);
    }

    private Dimension measure(View v) {
        if (v instanceof StringView) return drawStringView(null, (StringView) v, 0, 0);
        return v instanceof ArrayView ? drawArray(null, (ArrayView) v, 0, 0) : drawMatrix(null, (MatrixView) v, 0, 0);
    }

    // ------------------------------------------------------------ לולאות

    private static final Color[] LOOP_C = { new Color(0x2F6FED), new Color(0x7C3AED), new Color(0x0E9F6E), new Color(0xD97706) };
    private static final Color DONE_BG = new Color(0xDDF4E4), DONE_LINE = new Color(0x7CC79A), TODO_BG = new Color(0xF7F8FA);

    /**
     * כרטיס לולאה: הכותרת (קוד), מספר הסיבוב, התנאי — עם הערכים ומה יצא (צבוע צהוב כשבודקים אותו עכשיו),
     * ומה שהלולאה עוברת עליו: ב-for שסופר — "סרגל" הערכים (במערך: כל המערך, עם האיברים), בתור / מחסנית — מה נשאר בהם,
     * ברשימה — על מה המצביע, וב-while — איך המשתנה שבתנאי השתנה מסיבוב לסיבוב.
     */
    private Dimension drawLoop(Graphics2D g, LoopView v, int x0, int y) {
        int x = x0 + v.depth * 26;
        Color accent = LOOP_C[v.depth % LOOP_C.length];
        Font mono = Theme.mono.deriveFont(Font.BOLD, 13f), monoP = Theme.mono.deriveFont(13f);
        String badge = v.iteration > 0 ? "סיבוב " + v.iteration : "לפני הסיבוב הראשון";
        String cond = v.cond.isEmpty() ? null : v.cond + (v.condValues.isEmpty() ? "" : "   →   " + v.condValues);
        String res = v.condResult == null ? null : String.valueOf(v.condResult);
        boolean arr = !v.elements.isEmpty();
        int n = v.values.size(), cw = arr ? Math.max(34, widthOfStrings(v.elements) + 14) : 34, ch = 30;
        String subjLabel = arr && v.subjectKind != null ? "עובר על ה" + v.subjectKind + " " + v.subjectName : null;
        String hist = v.history.isEmpty() ? null : String.join("  →  ", v.history);
        String histLbl = hist == null ? null : "הערך של " + v.historyVar + " בתחילת כל סיבוב:";
        int trackW = n == 0 ? 0 : (n + 1) * cw + 20;
        int structW = v.structItems.isEmpty() ? 0 : v.structItems.size() * 34 + 140;
        int lblW = textWidth("בודקים — יוצאים מהלולאה", SMALL_B);
        int w = Math.max(340, 24 + max(textWidth(v.header, mono) + textWidth(badge, SMALL_B) + textWidth(v.title, SMALL) + 64,
                cond == null ? 0 : textWidth(cond, monoP) + lblW + 40 + (res == null ? 0 : textWidth("→  " + res, mono) + 20),
                trackW, structW, v.structNote == null ? 0 : textWidth(v.structNote, SMALL_B) + 30,
                hist == null ? 0 : textWidth(hist, monoP) + textWidth(histLbl, SMALL) + 40,
                v.note == null ? 0 : textWidth(v.note, SMALL_B) + 30));
        int h = 14 + 22 + (cond != null ? 28 : 0) + (subjLabel != null ? 20 : 0) + (n > 0 ? 22 + ch + (arr ? 34 : 20) : v.var != null && v.varValue != null ? 22 : 0)
                + (v.structItems.isEmpty() ? 0 : 40) + (v.structNote != null ? 22 : 0) + (hist != null ? 24 : 0) + (v.note != null ? 24 : 0) + 8;
        if (g == null) return new Dimension(w + (x - x0), h);

        // מסגרת עם פס צבע (לפי עומק הקינון)
        g.setColor(Color.WHITE);
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setColor(v.focus ? accent : BOX_LINE);
        g.setStroke(v.focus ? new BasicStroke(2f) : THIN);
        g.drawRoundRect(x, y, w, h, 12, 12);
        g.setColor(accent);
        g.fillRoundRect(x, y, 6, h, 6, 6);
        int cy = y + 14;
        // שורה 1: הכותרת (קוד) + תג הסיבוב + שם (לולאה חיצונית / פנימית)
        g.setFont(mono);
        g.setColor(INK);
        g.drawString(v.header, x + 16, cy + 12);
        int bw = textWidth(badge, SMALL_B) + 18, bx = x + w - bw - 10;
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 28));
        g.fillRoundRect(bx, cy - 2, bw, 22, 22, 22);
        g.setColor(accent);
        g.setFont(SMALL_B);
        g.drawString(badge, bx + 9, cy + 13);
        g.setFont(SMALL);
        g.setColor(MUTED);
        g.drawString(v.title, bx - 10 - textWidth(v.title, SMALL), cy + 13);
        cy += 26;
        // התנאי
        if (cond != null) {
            if (v.atHeader) {
                g.setColor(READ_C);
                g.fillRoundRect(x + 10, cy - 3, w - 20, 24, 8, 8);
            }
            String lbl = !v.atHeader ? "תנאי:" : v.condResult == null ? "בודקים:" : v.condResult ? "בודקים — ממשיכים" : "בודקים — יוצאים מהלולאה";
            g.setFont(SMALL_B);
            g.setColor(MUTED);
            g.drawString(lbl, x + w - 18 - textWidth(lbl, SMALL_B), cy + 13);
            g.setFont(monoP);
            g.setColor(INK);
            g.drawString(cond, x + 16, cy + 13);
            if (res != null) {
                int rx = x + 16 + textWidth(cond, monoP) + 12;
                g.setFont(mono);
                g.setColor(v.condResult ? new Color(0x15803D) : GHOST);
                g.drawString("→  " + res, rx, cy + 13);
            }
            cy += 28;
        }
        if (subjLabel != null) {
            g.setFont(SMALL_B);
            g.setColor(accent);
            g.drawString(subjLabel, x + w - 18 - textWidth(subjLabel, SMALL_B), cy + 12);
            cy += 20;
        }
        // הסרגל: הערכים של המשתנה (במערך — כל המערך: האיבר בתא, האינדקס מתחת)
        if (n > 0) {
            int tx = x + 16, top = cy + 18;
            int cur = v.currentIndex >= 0 ? v.values.get(v.currentIndex) : Integer.MIN_VALUE;
            for (int i = 0; i <= n; i++) {
                int bx2 = tx + i * cw;
                if (i == n) {   // "יציאה": אחרי הערך האחרון התנאי כבר לא מתקיים
                    box(g, bx2 + 6, top, 44, ch, v.done ? GHOST_BG : TODO_BG, null, true);
                    g.setColor(v.done ? GHOST : MUTED);
                    center(g, "יציאה", SMALL, bx2 + 6 + 22, top + ch / 2);
                    continue;
                }
                int val = v.values.get(i);
                boolean inRange = v.rangeFrom < 0 || val >= v.rangeFrom && val <= v.rangeTo;
                boolean now = i == v.currentIndex;
                boolean past;
                if (!inRange) past = false;
                else if (v.done) past = true;
                else if (v.currentIndex < 0) past = false;
                else if (v.rangeFrom >= 0) past = v.descending ? val > cur : val < cur;
                else past = i < v.currentIndex;
                Color fill = now ? FOCUS_BG : past ? DONE_BG : inRange ? TODO_BG : new Color(0xFAFAFB);
                g.setColor(fill);
                g.fillRoundRect(bx2 + 1, top + 1, cw - 2, ch - 2, ARC, ARC);
                g.setColor(now ? FOCUS : past ? DONE_LINE : inRange ? BOX_LINE : new Color(0xE3E6EC));
                g.setStroke(now ? THICK : inRange ? THIN : DASHED);
                g.drawRoundRect(bx2 + 1, top + 1, cw - 2, ch - 2, ARC, ARC);
                String text = arr ? v.elements.get(i) : String.valueOf(val);
                g.setColor(!inRange ? new Color(0xB7BDC8) : past && !now ? new Color(0x15803D) : INK);
                center(g, text, now ? SMALL_B : SMALL, bx2 + cw / 2, top + ch / 2);
                if (arr) {
                    g.setColor(now ? FOCUS : MUTED);
                    center(g, String.valueOf(val), SMALL, bx2 + cw / 2, top + ch + 11);
                }
                if (now) labelStack(g, java.util.Collections.singletonList(v.var), bx2 + cw / 2, top - 2);
            }
            cy += 22 + ch + (arr ? 34 : 20);
        } else if (v.var != null && v.varValue != null) {
            g.setFont(monoP);
            g.setColor(INK);
            g.drawString(v.var + " = " + v.varValue, x + 16, cy + 13);
            cy += 22;
        }
        // תור / מחסנית: מה נשאר בהם (הראש ראשון)
        if (!v.structItems.isEmpty()) {
            int sx = x + 16, top = cy + 4, qx = sx, maxX = x + w - 18;
            // רוחב התא לפי הטקסט (מחרוזות ארוכות כמו "חיי שרה" לא עולות זו על זו); מה שלא נכנס בכרטיס — "…"
            for (int i = 0; i < v.structItems.size(); i++) {
                String s = v.structItems.get(i);
                int qw = Math.max(32, textWidth(s, SMALL) + 14);
                if (i > 0 && qx + qw > maxX) {
                    g.setColor(MUTED);
                    center(g, "…", SMALL, qx + 10, top + 13);
                    break;
                }
                box(g, qx, top, qw, 26, i == 0 ? READ_C : BOX, null, false);
                g.setColor(INK);
                center(g, s, SMALL, qx + qw / 2, top + 13);
                qx += qw + 2;
            }
            g.setColor(MUTED);
            g.setFont(SMALL);
            String head = "Stack".equals(v.subjectKind) || "מחסנית".equals(v.subjectKind) ? "ראש המחסנית" : "ראש התור";
            g.drawString(head, sx, top + 26 + 12);
            cy += 40;
        }
        if (v.structNote != null) {
            g.setFont(SMALL_B);
            g.setColor(accent);
            g.drawString(v.structNote, x + w - 18 - textWidth(v.structNote, SMALL_B), cy + 14);
            cy += 22;
        }
        // while: איך המשתנה שבתנאי השתנה מסיבוב לסיבוב
        if (hist != null) {
            g.setFont(SMALL);
            g.setColor(MUTED);
            g.drawString(histLbl, x + w - 18 - textWidth(histLbl, SMALL), cy + 14);
            g.setFont(monoP);
            g.setColor(INK);
            g.drawString(hist, x + 16, cy + 14);
            cy += 24;
        }
        if (v.note != null) {
            g.setColor(new Color(0xF3E8FF));
            g.fillRoundRect(x + 10, cy, w - 20, 22, 8, 8);
            g.setFont(SMALL_B);
            g.setColor(LOOP_C[1]);
            g.drawString(v.note, x + w - 18 - textWidth(v.note, SMALL_B), cy + 15);
        }
        return new Dimension(w + (x - x0), h);
    }

    private static int max(int... xs) {
        int m = 0;
        for (int x : xs) m = Math.max(m, x);
        return m;
    }

    private int widthOfStrings(List<String> l) {
        int m = 0;
        for (String x : l) m = Math.max(m, textWidth(x, SMALL_B));
        return Math.min(m, 90);
    }

    /**
     * המונים של לולאות מקוננות — זה בתוך זה (כמו בובות בתוך בובות): בתוך המסגרת של i נמצאת המסגרת של j.
     * כך רואים שעל כל ערך של i — j עובר על כל הערכים שלו.
     */
    private Dimension drawCounters(Graphics2D g, Model.LoopCountersView v, int x, int y) {
        int k = v.levels.size();
        int[] w = new int[k];
        Font big = Theme.mono.deriveFont(Font.BOLD, 22f);
        for (int i = k - 1; i >= 0; i--) {
            String[] l = v.levels.get(i);
            int own = 14 + Math.max(textWidth(l[0] + " =", SMALL_B), textWidth(l[2], SMALL)) + 8 + textWidth(l[1], big) + 14;
            w[i] = own + (i + 1 < k ? w[i + 1] + 10 : 0);
        }
        int h0 = 40 + 22 * (k - 1) + 16;
        int total = Math.max(w[0], titleWidth(v.title, "לולאות מקוננות"));
        if (g == null) return new Dimension(total, TITLE_H + h0);
        drawTitle(g, v.title, "לולאות מקוננות", x, y, true);
        int bx = x, by = y + TITLE_H, bh = h0;
        for (int i = 0; i < k; i++) {
            String[] l = v.levels.get(i);
            Color c = LOOP_C[i % LOOP_C.length];
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 22));
            g.fillRoundRect(bx, by, w[i], bh, 14, 14);
            g.setColor(c);
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(bx, by, w[i], bh, 14, 14);
            g.setFont(SMALL_B);
            g.drawString(l[0] + " =", bx + 12, by + 20);
            if (!l[2].isEmpty()) {
                g.setFont(SMALL);
                g.setColor(MUTED);
                g.drawString(l[2], bx + 12, by + 38);
            }
            g.setFont(big);
            g.setColor(c);
            int lw = Math.max(textWidth(l[0] + " =", SMALL_B), textWidth(l[2], SMALL));
            g.drawString(l[1], bx + 12 + lw + 8, by + 30);
            int own = 14 + lw + 8 + textWidth(l[1], big) + 14;
            bx += own;
            by += 11;
            bh -= 22;
        }
        return new Dimension(total, TITLE_H + h0);
    }

    /** טבלת הזוגות של שתי לולאות מקוננות: ירוק = כבר עבר, כחול = עכשיו, לבן = עוד יגיע, אפור = לא קיים (למשל j < i) */
    private Dimension drawLoopGrid(Graphics2D g, LoopGridView v, int x, int y) {
        int c = 26, rows = v.rows.size(), cols = v.cols.size(), head = 30;
        int w = head + cols * c + 12, h = TITLE_H + head + rows * c + 8;
        w = Math.max(w, titleWidth(v.title, "כל המעברים"));
        if (g == null) return new Dimension(w, h);
        drawTitle(g, v.title, "כל המעברים", x, y, true);
        int top = y + TITLE_H + head, left = x + head;
        g.setFont(SMALL_B);
        g.setColor(LOOP_C[1]);
        for (int j = 0; j < cols; j++) center(g, String.valueOf(v.cols.get(j)), SMALL, left + j * c + c / 2, top - head / 2);
        g.setColor(LOOP_C[0]);
        for (int i = 0; i < rows; i++) center(g, String.valueOf(v.rows.get(i)), SMALL, x + head / 2, top + i * c + c / 2);
        g.setColor(LOOP_C[0]);
        g.setFont(SMALL_B);
        // בפינה: "i \ j" — השורות לפי i, העמודות לפי j
        g.drawString(v.rowVar, x + 1, top - 3);
        g.setColor(MUTED);
        g.drawString("\\", x + 1 + textWidth(v.rowVar, SMALL_B), top - 3 - 5);
        g.setColor(LOOP_C[1]);
        g.drawString(v.colVar, x + 1 + textWidth(v.rowVar + "\\", SMALL_B), top - 3 - 10);
        for (int i = 0; i < rows; i++) {
            int[] st = v.cells.get(i);
            for (int j = 0; j < cols; j++) {
                int cx = left + j * c, cy = top + i * c;
                switch (st[j]) {
                    case LoopGridView.NONE:
                        g.setColor(new Color(0xEEF0F4));
                        g.fillRect(cx + 2, cy + 2, c - 4, c - 4);
                        break;
                    case LoopGridView.DONE:
                        g.setColor(DONE_BG);
                        g.fillRoundRect(cx + 1, cy + 1, c - 2, c - 2, 6, 6);
                        g.setColor(new Color(0x15803D));
                        center(g, "✓", SMALL_B, cx + c / 2, cy + c / 2);
                        break;
                    case LoopGridView.NOW:
                        g.setColor(FOCUS);
                        g.fillRoundRect(cx + 1, cy + 1, c - 2, c - 2, 6, 6);
                        break;
                    default:
                        g.setColor(Color.WHITE);
                        g.fillRoundRect(cx + 1, cy + 1, c - 2, c - 2, 6, 6);
                        g.setColor(BOX_LINE);
                        g.setStroke(THIN);
                        g.drawRoundRect(cx + 1, cy + 1, c - 2, c - 2, 6, 6);
                }
            }
        }
        return new Dimension(w, h);
    }

    // ------------------------------------------------------------ מחרוזת

    /**
     * מחרוזת: תו בכל תא ומתחתיו האינדקס. מעל התאים — חצים (charAt(i), "כאן שונה"...);
     * החלק שהשורה חותכת (substring) או מוצאת (indexOf) — צבוע ועם סוגר מתחת; ומתחת — מה כל פעולה מחזירה.
     */
    private Dimension drawStringView(Graphics2D g, StringView v, int x, int y) {
        String text = v.text;
        int n = text.length(), cw = 30, ch = 36;
        int most = 0;
        for (List<String> l : v.arrows.values()) most = Math.max(most, l.size());
        int labelsH = 22 + Math.max(0, most - 1) * LABEL_STEP;
        boolean range = v.from >= 0 && v.to > v.from;
        String type = "מחרוזת (String), אורך " + (v.truncated ? n + "+" : String.valueOf(n));
        int top = y + th() + labelsH;
        int opsW = 0, opsLines = 0;
        for (String o : v.ops) {
            for (String part : o.split(StringViz.NOTE)) { opsW = Math.max(opsW, textWidth(part, SMALL_B)); opsLines++; }
        }
        int opsH = v.ops.isEmpty() ? 0 : 10 + opsLines * 20 + (v.ops.size() - 1) * 6;
        int w = Math.max(Math.max(16 + Math.max(n, 1) * cw + 20, titleWidth(v.title, type)), opsW + 24);
        int h = th() + labelsH + ch + 22 + (range ? 12 : 0) + opsH;
        if (g == null) return new Dimension(w, h);

        drawTitle(g, v.title, type, x, y, v.focus);
        if (n == 0) {
            box(g, x + 8, top, cw * 2, ch, BOX, null, true);
            g.setColor(MUTED);
            center(g, "\"\"", SMALL_B, x + 8 + cw, top + ch / 2);
        }
        for (int i = 0; i < n; i++) {
            int bx = x + 8 + i * cw;
            boolean in = range && i >= v.from && i < v.to;
            Color fill = in ? (v.found ? NEW_C : READ_C) : v.changed ? CHANGED_C : BOX;
            char c = text.charAt(i);
            box(g, bx, top, cw, ch, fill, c == ' ' ? "\u2423" : String.valueOf(c), false);
            g.setColor(MUTED);
            center(g, String.valueOf(i), SMALL, bx + cw / 2, top + ch + 11);
            List<String> labels = v.arrows.get(i);
            if (labels != null) labelStack(g, labels, bx + cw / 2, top - 4);
        }
        int by = top + ch + 22;
        if (range) {   // סוגר מתחת לחלק המסומן
            int x1 = x + 8 + v.from * cw + 3, x2 = x + 8 + Math.min(v.to, n) * cw - 3;
            g.setColor(v.found ? new Color(0x15803D) : new Color(0xB45309));
            g.setStroke(new BasicStroke(2.2f));
            g.drawLine(x1, by + 2, x2, by + 2);
            g.drawLine(x1, by - 4, x1, by + 2);
            g.drawLine(x2, by - 4, x2, by + 2);
            by += 12;
        }
        int oy = by + 16;
        for (String o : v.ops) {
            String[] parts = o.split(StringViz.NOTE);
            boolean err = v.error && o.contains("Exception");
            // שורה 1: הקוד והתוצאה (משמאל לימין); שורה 2: ההסבר בעברית (מתחיל באות עברית — ולכן מימין לשמאל, צמוד לימין)
            g.setColor(err ? GHOST : FOCUS);
            g.setFont(SMALL_B);
            g.drawString(parts[0], x + 10, oy);
            oy += 20;
            if (parts.length > 1) {
                g.setColor(err ? GHOST : MUTED);
                g.setFont(SMALL);
                g.drawString(parts[1], x + w - 10 - textWidth(parts[1], SMALL), oy);
                oy += 20;
            }
            oy += 6;
        }
        return new Dimension(w, h);
    }

    // ------------------------------------------------------------ מערך חד-ממדי

    private Dimension drawArray(Graphics2D g, ArrayView v, int x, int y) {
        int n = v.items.size();
        boolean objs = hasObjs(v.objs);
        int cw = Math.max(44, widthOf(v.items, v.objs, null) + 22), ch = objs ? OBJ_H : 40;
        Dimension sb = subsBox(v.subs);
        if (sb != null) { cw = Math.max(cw, sb.width); ch = Math.max(ch, sb.height); }
        int mostLabels = 0;
        for (List<String> l : v.indexLabels.values()) mostLabels = Math.max(mostLabels, l.size());
        int labelsH = 22 + Math.max(0, mostLabels - 1) * LABEL_STEP;   // כמה משתני אינדקס על אותו תא — שורה לכל אחד
        String type = "מערך, אורך " + n + (v.truncated ? "+" : "");
        int top = y + th() + labelsH;
        int w = Math.max(16 + Math.max(n, 1) * cw + 20, titleWidth(v.title, type));
        int h = th() + labelsH + ch + 22;
        if (g == null) return new Dimension(w, h);

        drawTitle(g, v.title, type, x, y, v.focus || !v.changed.isEmpty());
        if (n == 0) {
            box(g, x + 8, top, cw, ch, BOX, null, true);
            g.setColor(MUTED);
            center(g, "ריק", SMALL_B, x + 8 + cw / 2, top + ch / 2);
        }
        for (int i = 0; i < n; i++) {
            int bx = x + 8 + i * cw;
            cell(g, bx, top, cw, ch, v.changed.contains(i) ? CHANGED_C : BOX, v.items.get(i), objAt(v.objs, i), subAt(v.subs, i));
            g.setColor(MUTED);
            center(g, String.valueOf(i), SMALL, bx + cw / 2, top + ch + 11);
            List<String> labels = v.indexLabels.get(i);
            if (labels != null) labelStack(g, labels, bx + cw / 2, top - 4);
        }
        if (v.truncated) {
            g.setColor(MUTED);
            g.setFont(SMALL_B);
            g.drawString("…", x + 12 + n * cw, top + ch / 2 + 5);
        }
        return new Dimension(w, h);
    }

    /** "i" בסגול עם חץ קטן כלפי התא */
    private void indexLabel(Graphics2D g, String text, int cx, int bottomY) {
        g.setColor(LABEL);
        g.setFont(SMALL_B);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, cx - fm.stringWidth(text) / 2, bottomY - 8);
        g.fillPolygon(new int[] { cx - 4, cx + 4, cx }, new int[] { bottomY - 6, bottomY - 6, bottomY }, 3);
    }

    // ------------------------------------------------------------ מערך דו-ממדי

    private Dimension drawMatrix(Graphics2D g, MatrixView v, int x, int y) {
        int rows = v.rows.size(), cols = 0;
        List<String> all = new ArrayList<String>();
        List<Obj> allObjs = new ArrayList<Obj>();
        for (int r = 0; r < v.rows.size(); r++) {
            List<String> row = v.rows.get(r);
            cols = Math.max(cols, row.size());
            all.addAll(row);
            for (int c = 0; c < row.size(); c++) allObjs.add(r < v.objs.size() ? objAt(v.objs.get(r), c) : null);
        }
        boolean objs = hasObjs(allObjs);
        int cw = Math.max(44, widthOf(all, allObjs, null) + 20), ch = objs ? OBJ_H : 36;
        int labelW = 0;
        for (List<String> l : v.rowLabels.values()) labelW = Math.max(labelW, textWidth(String.join(",", l), SMALL_B) + 16);
        int left = x + 8 + labelW + 22, top = y + th() + 22 + (v.colLabels.isEmpty() ? 0 : 18);
        String type = "מערך דו-ממדי " + rows + "×" + cols;
        int w = Math.max(left - x + Math.max(cols, 1) * cw + 16, titleWidth(v.title, type));
        int h = top - y + Math.max(rows, 1) * ch + 12;
        if (g == null) return new Dimension(w, h);

        drawTitle(g, v.title, type, x, y, v.focus || !v.changed.isEmpty());
        g.setColor(MUTED);
        for (int c = 0; c < cols; c++) center(g, String.valueOf(c), SMALL, left + c * cw + cw / 2, top - 9);
        for (int r = 0; r < rows; r++) {
            g.setColor(MUTED);
            center(g, String.valueOf(r), SMALL, left - 11, top + r * ch + ch / 2);
            List<String> row = v.rows.get(r);
            for (int c = 0; c < row.size(); c++)
                cell(g, left + c * cw, top + r * ch, cw, ch, v.changed.contains(r + "," + c) ? CHANGED_C : BOX, row.get(c),
                        r < v.objs.size() ? objAt(v.objs.get(r), c) : null);
            List<String> rl = v.rowLabels.get(r);
            if (rl != null) {
                g.setColor(LABEL);
                g.setFont(SMALL_B);
                String t = String.join(",", rl);
                g.drawString(t, left - 22 - textWidth(t, SMALL_B) - 8, top + r * ch + ch / 2 + 5);
                g.fillPolygon(new int[] { left - 26, left - 26, left - 20 }, new int[] { top + r * ch + ch / 2 - 4, top + r * ch + ch / 2 + 4, top + r * ch + ch / 2 }, 3);
            }
        }
        for (Map.Entry<Integer, List<String>> e : v.colLabels.entrySet())
            indexLabel(g, String.join(",", e.getValue()), left + e.getKey() * cw + cw / 2, top - 20);
        return new Dimension(w, h);
    }

    // ------------------------------------------------------------ מחסנית

    private Dimension drawStack(Graphics2D g, LinearView v, int x, int y) {
        int n = v.items.size(), rows = Math.max(n, 1);
        boolean objs = hasObjs(v.objs);
        int cw = Math.max(76, widthOf(v.items, v.objs, v.ghost) + 26), ch = objs ? OBJ_H : 36, ghostH = v.ghost != null || v.error != null ? (objs ? 62 : 50) : 14;
        Dimension sb = subsBox(v.subs);
        if (sb != null) { cw = Math.max(cw, sb.width); ch = Math.max(ch, sb.height); }
        int w = Math.max(cw + 84, titleWidth(v.title, "מחסנית")), h = th() + ghostH + rows * ch + 14;
        if (g == null) return new Dimension(w, h);
        boolean wasInStack = inStack;
        inStack = true;

        drawTitle(g, v.title, "מחסנית", x, y, v.focus);
        int cx = x + 8, top = y + th() + ghostH;
        // המקומות של האיברים ושל הקירות — בהנפשה: בדרך מהמקום הקודם (StructAnim.stack)
        Rectangle[] rs = new Rectangle[n];
        for (int i = 0; i < n; i++) rs[i] = new Rectangle(cx, top + i * ch, cw, ch);
        Rectangle wall = new Rectangle(cx, top, cw, rows * ch);
        float[] alpha = null;
        float ghostA = 1f;
        if (hooked()) {
            String key = curView;
            if (capture != null) {
                for (int i = 0; i < n; i++) capture.put("L|" + key + "|" + i, rs[i]);
                capture.put("W|" + key, wall);
            } else {
                alpha = new float[n];
                rs = anim.stack(key, rs, alpha);
                wall = anim.wall(key, wall);
                anim.ghosts.put(key, new Rectangle(cx, y + th() + 4, cw, ch));
                ghostA = anim.ghostAlpha(key);
            }
        }
        if (v.ghost != null && ghostA > 0.01f) {
            Graphics2D gg = faded(g, ghostA);
            ghostBox(gg, cx, y + th() + 4, cw, ch, v.ghost);
            gg.setFont(SMALL_B);
            gg.drawString("pop", cx + cw + 10, y + th() + 4 + ch / 2 + 5);
            gg.dispose();
        }
        if (v.error != null) {   // ניסו להוציא / לקרוא — ואין איבר: "?" אדום במקום שבו היה צריך להיות
            ghostBox(g, cx, y + th() + 4, cw, ch, "?");
            g.setFont(SMALL_B);
            g.setColor(GHOST);
            g.drawString(v.error, cx + cw + 10, y + th() + 4 + ch / 2 + 5);
        }
        int falling = alpha != null ? anim.added(curView) : 0;
        for (int i = 0; i < n; i++) {
            Color c = i == v.markIndex ? color(v.mark) : BOX;
            Graphics2D gi = alpha != null && alpha[i] < 1f ? faded(g, alpha[i]) : g;
            cell(gi, rs[i].x, rs[i].y, rs[i].width, rs[i].height, c, v.items.get(i), objAt(v.objs, i), subAt(v.subs, i));
            if (gi != g) gi.dispose();
        }
        // מה שנופל פנימה עובר מאחורי הכותרת (השם והתגית נשארים קריאים): מציירים את הכותרת שוב — רק איפה שהוא
        for (int i = 0; i < falling && i < n; i++) {
            if (rs[i].y >= y + th()) continue;
            Graphics2D gt = (Graphics2D) g.create();
            gt.clip(rs[i]);
            drawTitle(gt, v.title, "מחסנית", x, y, v.focus);
            gt.dispose();
        }
        inStack = wasInStack;
        g.setColor(v.error != null ? GHOST : LINE);
        g.setStroke(THICK);
        int wl = wall.x, wr = wall.x + wall.width, wt = wall.y, bottom = wall.y + wall.height + 5;
        g.drawPolyline(new int[] { wl - 5, wl - 5, wr + 5, wr + 5 },
                new int[] { wt - 8, bottom, bottom, wt - 8 }, 4);
        if (n == 0) {
            g.setColor(v.error != null ? GHOST : MUTED);
            center(g, "ריקה", SMALL_B, cx + cw / 2, top + ch / 2);
        } else {
            arrow(g, wr + 42, wt + ch / 2, wr + 10, wt + ch / 2, LINE);
            g.setFont(SMALL_B);
            g.drawString("top", wr + 46, wt + ch / 2 + 5);
        }
        return new Dimension(w, h);
    }

    // ------------------------------------------------------------ תור

    private static final int QARROW = 16;   // אורך החצים בצדי התור

    private Dimension drawQueue(Graphics2D g, LinearView v, int x, int y) {
        int n = v.items.size(), rows = Math.max(n, 1);
        boolean objs = hasObjs(v.objs);
        int cw = Math.max(56, widthOf(v.items, v.objs, v.ghost) + 24), ch = objs ? OBJ_H : 40;
        Dimension sb = subsBox(v.subs);
        if (sb != null) { cw = Math.max(cw, sb.width); ch = Math.max(ch, sb.height); }
        // משמאל: מקום לאיבר שהוצא (remove) — רק במבנה ראשי, או כשבאמת הוציאו; ואז חץ קצר החוצה
        boolean ghostSpace = nest == 0 || v.ghost != null;
        int left = x + (ghostSpace ? cw + 16 : 4) + QARROW + 8;
        int w = Math.max(left - x + rows * cw + QARROW + 18, titleWidth(v.title, "תור")), h = th() + ch + 40;
        if (g == null) return new Dimension(w, h);

        drawTitle(g, v.title, "תור", x, y, v.focus);
        int top = y + th() + 6;
        // המקומות של האיברים ושל הצינור — בהנפשה: בדרך מהמקום הקודם (StructAnim.queue)
        Rectangle[] rs = new Rectangle[n];
        for (int i = 0; i < n; i++) rs[i] = new Rectangle(left + i * cw, top, cw, ch);
        Rectangle wall = new Rectangle(left, top, rows * cw, ch);
        float[] alpha = null;
        float ghostA = 1f;
        if (hooked()) {
            String key = curView;
            if (capture != null) {
                for (int i = 0; i < n; i++) capture.put("L|" + key + "|" + i, rs[i]);
                capture.put("W|" + key, wall);
            } else {
                alpha = new float[n];
                rs = anim.queue(key, rs, alpha, cw + QARROW + 34);
                wall = anim.wall(key, wall);
                anim.ghosts.put(key, new Rectangle(x + 8, top, cw, ch));
                ghostA = anim.ghostAlpha(key);
            }
        }
        if (v.ghost != null && ghostA > 0.01f) {
            Graphics2D gg = faded(g, ghostA);
            ghostBox(gg, x + 8, top, cw, ch, v.ghost);
            center(gg, "remove", SMALL_B, x + 8 + cw / 2, top + ch + 16);
            gg.dispose();
        }
        if (v.error != null) {   // remove / head על תור ריק: "?" אדום ביציאה
            ghostBox(g, x + 8, top, cw, ch, "?");
            g.setColor(GHOST);
            center(g, v.error, SMALL_B, x + 8 + cw / 2, top + ch + 16);
        }
        g.setColor(v.error != null ? GHOST : LINE);
        g.setStroke(THICK);
        int wl = wall.x, wt = wall.y, wh = wall.height;
        g.drawLine(wl - 8, wt - 5, wl + wall.width + 8, wt - 5);
        g.drawLine(wl - 8, wt + wh + 5, wl + wall.width + 8, wt + wh + 5);
        // צדדים פתוחים עם חיצים: משמאל יוצאים (הראש — remove), מימין נכנסים (הזנב — insert). כך תור לא נראה כמו מערך
        int mid = wt + wh / 2, right = wl + wall.width;
        arrow(g, wl - 6, mid, wl - 6 - QARROW, mid, LINE, 6, 1.5f);
        arrow(g, right + 8 + QARROW, mid, right + 8, mid, LINE, 6, 1.5f);
        // האיברים — אחרי הצינור והחצים: בהנפשה איבר שנכנס עובר מעל החץ של הכניסה
        for (int i = 0; i < n; i++) {
            Color c = i == v.markIndex ? color(v.mark) : BOX;
            Graphics2D gi = alpha != null && alpha[i] < 1f ? faded(g, alpha[i]) : g;
            cell(gi, rs[i].x, rs[i].y, rs[i].width, rs[i].height, c, v.items.get(i), objAt(v.objs, i), subAt(v.subs, i));
            if (gi != g) gi.dispose();
        }
        if (n == 0) {
            g.setColor(v.error != null ? GHOST : MUTED);
            center(g, "ריק", SMALL_B, left + cw / 2, top + ch / 2);
        } else {
            // head / tail — מתחת לאיברים עצמם (בהנפשה הם זזים איתם: מי שמתקדם לראש / נכנס לזנב לוקח את השם)
            Graphics2D gl = alpha != null && alpha[0] < 1f ? faded(g, alpha[0]) : g;
            gl.setColor(LINE);
            center(gl, n == 1 ? "head / tail" : "head", SMALL_B, rs[0].x + rs[0].width / 2, rs[0].y + rs[0].height + 18);
            if (gl != g) gl.dispose();
            if (n > 1) {
                gl = alpha != null && alpha[n - 1] < 1f ? faded(g, alpha[n - 1]) : g;
                gl.setColor(LINE);
                center(gl, "tail", SMALL_B, rs[n - 1].x + rs[n - 1].width / 2, rs[n - 1].y + rs[n - 1].height + 18);
                if (gl != g) gl.dispose();
            }
        }
        return new Dimension(w, h);
    }

    // ------------------------------------------------------------ רשימה מקושרת

    /**
     * רשימה מקושרת: שורה של חוליות (ערך + מצביע), חץ מכל חוליה לבאה, ובסוף null (או מעגל / המשך ברשימה אחרת).
     * מעל כל חוליה — המשתנים שמצביעים עליה, כל אחד עם חץ משלו בצבע משלו (pointerLabels); כשיש כמה שמות, החוליות מתרחקות
     * זו מזו כך ששורות השמות של שכנות לא נוגעות זו בזו.
     * ענפים (חוליה חדשה שכבר מצביעה לאמצע הרשימה, חוליה שהוצאה ועדיין מצביעה הלאה): בשורה מתחת, והאחרונה בענף — מתחת
     * לחוליה שלפני החוליה שהיא מצביעה עליה, עם חץ אלכסוני אליה.
     */
    private Dimension drawChain(Graphics2D g, ChainView v, int x, int y) {
        List<ChainView> rows = new ArrayList<ChainView>();
        rows.add(v);
        rows.addAll(v.branches);
        List<String> values = new ArrayList<String>();
        List<Obj> cellObjs = new ArrayList<Obj>();
        List<View> cellSubs = new ArrayList<View>();
        for (ChainView r : rows) for (Cell c : r.cells) { values.add(c.value); cellObjs.add(c.obj); cellSubs.add(c.sub); }
        int n = v.cells.size();
        boolean objs = hasObjs(cellObjs);
        int vw = Math.max(48, widthOf(values, cellObjs, null) + 22), pw = 20, ch = objs ? OBJ_H : 38, ag = 38;
        Dimension sb = subsBox(cellSubs);
        if (sb != null) { vw = Math.max(vw, sb.width); ch = Math.max(ch, sb.height); }
        int cw = vw + pw, stepW = cw + ag;
        String tail = v.cycleTo >= 0 ? "" : v.joinText != null ? v.joinText + "  (ממשיך ברשימה אחרת)"
                : v.truncated ? "..." : "null";
        int tailW = textWidth(tail, SMALL_B);

        // --- עמודות: ענף שארוך מהמקום שלפני החוליה שהוא מצביע עליה דוחף את השורה הראשית ימינה
        int shift = 0;
        for (ChainView b : v.branches) shift = Math.max(shift, b.cells.size() - b.joinTo);
        int cols = shift + Math.max(n, 1);
        // כל ענף בשורה משלו מתחת לראשית, אלא אם יש מקום בשורה קיימת (העמודות לא חופפות)
        int[] brRow = new int[v.branches.size()];
        List<List<int[]>> taken = new ArrayList<List<int[]>>();
        for (int i = 0; i < v.branches.size(); i++) {
            ChainView b = v.branches.get(i);
            int from = shift + b.joinTo - b.cells.size(), to = shift + b.joinTo - 1;
            int r = 0;
            for (; r < taken.size(); r++) {
                boolean free = true;
                for (int[] t : taken.get(r)) if (from <= t[1] + 1 && to >= t[0] - 1) free = false;
                if (free) break;
            }
            if (r == taken.size()) taken.add(new ArrayList<int[]>());
            taken.get(r).add(new int[] { from, to });
            brRow[i] = r;
        }
        // כמה מקום צריכים השמות מעל כל עמודה — ומשם המרחק בין החוליות
        int[] need = new int[cols];
        for (int i = 0; i < n; i++) need[shift + i] = Math.max(need[shift + i], pointerRowW(v.cells.get(i).labels));
        for (ChainView b : v.branches)
            for (int k = 0; k < b.cells.size(); k++) {
                int col = shift + b.joinTo - b.cells.size() + k;
                need[col] = Math.max(need[col], pointerRowW(b.cells.get(k).labels));
            }
        int[] left = new int[cols];
        left[0] = x + 8 + Math.max(0, (need[0] - cw) / 2);
        for (int c = 1; c < cols; c++) left[c] = left[c - 1] + Math.max(stepW, (need[c - 1] + need[c]) / 2 + LBL_SPACE);
        int last = shift + n - 1;
        int lastX = left[last] + vw + pw / 2;
        // ה-null שבסוף: אם יש משתנים שהם null (p אחרי האחרונה) — השמות שלהם מעליו, בלי לגעת בשמות של החוליה האחרונה
        int nullW = pointerRowW(v.nullLabels);
        int agT = ag;
        if (nullW > 0) agT = Math.max(ag, (need[last] + nullW) / 2 + LBL_SPACE - (lastX + 4 + tailW / 2 - (left[last] + cw / 2)));
        int tailCx = lastX + agT + 4 + tailW / 2;
        int right = v.cycleTo >= 0 ? lastX + 30 : Math.max(lastX + agT + 4 + tailW + 10, nullW > 0 ? tailCx + nullW / 2 + 8 : 0);

        // --- גובה: שמות מעל השורה הראשית, השורה, מעגל, ענפים, והסבר על חוליות שאבדו
        boolean mainLabels = !v.nullLabels.isEmpty();
        for (Cell c : v.cells) mainLabels |= !c.labels.isEmpty();
        int top = y + th() + (mainLabels ? LABELS_H : 24);
        int below = top + ch + (v.cycleTo >= 0 ? 36 : 14);
        int[] rowTop = new int[taken.size()];
        int yy = top + ch + (v.cycleTo >= 0 ? 36 : 0);
        for (int r = 0; r < taken.size(); r++) {
            boolean lab = false;
            for (int i = 0; i < v.branches.size(); i++)
                if (brRow[i] == r) for (Cell c : v.branches.get(i).cells) lab |= !c.labels.isEmpty() || c.lost;
            rowTop[r] = yy + (lab ? LABELS_H + 4 : 30);
            yy = rowTop[r] + ch;
            below = yy + 14;
        }
        List<String[]> notes = new ArrayList<String[]>();   // {מה קרה, מה עושים}
        for (ChainView r : rows) if (r.lostNote != null) notes.add(new String[] { r.lostNote, r.lostHint });
        int w = Math.max(right - x, titleWidth(v.title, "רשימה מקושרת"));
        int noteW = Math.max(w, Math.min(620, Math.max(360, w)));
        int noteH = 0;
        if (nest == 0) for (String[] t : notes) noteH += 10 + paragraph(null, t[0], SMALL_B, GHOST, 0, 0, noteW) + paragraph(null, t[1], SMALL, INK, 0, 0, noteW);
        int h = below - y + noteH;
        if (noteH > 0) w = Math.max(w, noteW);
        if (g == null) return new Dimension(w, h);

        drawTitle(g, v.title, "רשימה מקושרת", x, y, v.focus);
        // המקומות של החוליות (השורה הראשית והענפים) — בהנפשה: בדרך מהמקום הקודם (StructAnim.cell)
        Rectangle[] mr = new Rectangle[n];
        for (int i = 0; i < n; i++) mr[i] = new Rectangle(left[shift + i], top, vw + pw, ch);
        List<Rectangle[]> brR = new ArrayList<Rectangle[]>();
        for (int b = 0; b < v.branches.size(); b++) {
            ChainView br = v.branches.get(b);
            int k = br.cells.size(), first = shift + br.joinTo - k;
            Rectangle[] r = new Rectangle[k];
            for (int i = 0; i < k; i++) r[i] = new Rectangle(left[first + i], rowTop[brRow[b]], vw + pw, ch);
            brR.add(r);
        }
        Rectangle lastStatic = n > 0 ? mr[n - 1] : null;
        float[][] mfx = chainPlaces(v.cells, mr);
        List<float[][]> brFx = new ArrayList<float[][]>();
        for (int b = 0; b < v.branches.size(); b++) brFx.add(chainPlaces(v.branches.get(b).cells, brR.get(b)));
        for (int i = 0; i < n; i++) {
            Cell c = v.cells.get(i);
            chainCellAt(g, c, mr[i], mfx == null ? null : mfx[i], pw);
            if (i < n - 1) link(g, c, v.cells.get(i + 1), mr[i], mr[i + 1].x, mr[i + 1].y + mr[i + 1].height / 2, pw);
        }
        int midY = top + ch / 2;
        // ה-null שבסוף נשאר צמוד לחוליה האחרונה כשהיא זזה (אבל לא "עף" עם חוליה חדשה שנופלת למקומה)
        Graphics2D gt = g;
        if (n > 0 && mfx != null && mfx[n - 1][1] >= 1f && (mr[n - 1].x != lastStatic.x || mr[n - 1].y != lastStatic.y)) {
            gt = (Graphics2D) g.create();
            gt.translate(mr[n - 1].x - lastStatic.x, mr[n - 1].y - lastStatic.y);
        }
        Graphics2D gMain = g;
        g = gt;
        if (v.cycleTo >= 0) {
            int tx = left[shift + v.cycleTo] + vw / 2, low = top + ch + 24;
            g.setColor(GHOST);
            g.setStroke(THICK);
            g.drawPolyline(new int[] { lastX, lastX + 20, lastX + 20, tx, tx },
                    new int[] { midY, midY, low, low, top + ch + 10 }, 5);
            arrow(g, tx, top + ch + 12, tx, top + ch + 2, GHOST);
            g.setFont(SMALL_B);
            g.drawString("מעגל!", (lastX + tx) / 2, low + 14);
        } else {
            arrow(g, lastX, midY, lastX + agT, midY, LINE);
            g.setColor(v.joinText != null ? CURRENT : !v.nullLabels.isEmpty() ? GHOST : MUTED);
            g.setFont(SMALL_B);
            g.drawString(tail, lastX + agT + 4, midY + 5);
            if (!v.nullLabels.isEmpty()) {   // p שהוא null: מצביע על ה-null שבסוף — ומשם אי אפשר להמשיך
                pointerLabels(g, v.nullLabels, tailCx, tailW, top - LBL_BASE, midY - 8, 0, GHOST);
                g.setColor(GHOST);
                g.setStroke(THICK);
                g.drawLine(tailCx - 9, midY + 9, tailCx + 9, midY + 25);
                g.drawLine(tailCx + 9, midY + 9, tailCx - 9, midY + 25);
            }
        }
        if (gt != gMain) gt.dispose();
        g = gMain;
        // ענפים: התאים בשורה מתחת, וחץ מהאחרונה אל החוליה שהיא מצביעה עליה
        for (int b = 0; b < v.branches.size(); b++) {
            ChainView br = v.branches.get(b);
            int k = br.cells.size();
            Rectangle[] r = brR.get(b);
            float[][] fx = brFx.get(b);
            for (int i = 0; i < k; i++) {
                Cell c = br.cells.get(i);
                chainCellAt(g, c, r[i], fx == null ? null : fx[i], pw);
                if (i < k - 1) {
                    link(g, c, br.cells.get(i + 1), r[i], r[i + 1].x, r[i + 1].y + r[i + 1].height / 2, pw);
                } else if (br.joinTo >= 0 && br.joinTo < n) {
                    Rectangle to = mr[br.joinTo];
                    link(g, c, v.cells.get(br.joinTo), r[i], to.x + Math.min(18, vw / 3), to.y + to.height + 1, pw);
                }
            }
        }
        if (noteH > 0) {
            int ny = below + 6;
            for (String[] t : notes) {
                ny += paragraph(g, t[0], SMALL_B, GHOST, x + noteW, ny, noteW);
                ny += paragraph(g, t[1], SMALL, INK, x + noteW, ny, noteW) + 10;
            }
        }
        return new Dimension(w, h);
    }

    /**
     * בהנפשה: איפה כל חוליה ברגע הזה (r מתעדכן במקום) — והשקיפות / ההגדלה שלה ({שקיפות, הגדלה} לכל חוליה, או null).
     * במדידת הציור הקודם — רושמים את המקומות
     */
    private float[][] chainPlaces(List<Cell> cells, Rectangle[] r) {
        if (!hooked()) return null;
        if (capture != null) {
            for (int i = 0; i < r.length; i++) {
                String k = capCells.get(cells.get(i));
                if (k != null) capture.put("N|" + k, r[i]);
            }
            return null;
        }
        float[][] fx = new float[r.length][2];
        for (int i = 0; i < r.length; i++) {
            String k = anim.toCells.get(cells.get(i));
            r[i] = anim.cell(k, r[i], fx[i], curView);
            if (k != null) anim.now.put(k, r[i]);
        }
        return fx;
    }

    /** חוליה במקום r (בהנפשה — עם שקיפות והגדלה סביב המרכז) */
    private void chainCellAt(Graphics2D g, Cell c, Rectangle r, float[] fx, int pw) {
        if (fx == null || (fx[0] >= 1f && fx[1] >= 1f)) {
            chainCell(g, c, r.x, r.y, r.width - pw, pw, r.height);
            return;
        }
        if (fx[0] <= 0.01f) return;
        Graphics2D g2 = faded(g, fx[0]);
        double cx = r.getCenterX(), cy = r.getCenterY();
        g2.translate(cx, cy);
        g2.scale(fx[1], fx[1]);
        g2.translate(-cx, -cy);
        chainCell(g2, c, r.x, r.y, r.width - pw, pw, r.height);
        g2.dispose();
    }

    /** החץ מחוליה a (במקום ra) אל החוליה b (הנקודה tx, ty). בהנפשה: חץ חדש "נמתח" בהדרגה */
    private void link(Graphics2D g, Cell a, Cell b, Rectangle ra, int tx, int ty, int pw) {
        int sx = ra.x + ra.width - pw + pw / 2, sy = ra.y + ra.height / 2;
        if (anim != null && hooked()) {
            int[] e = anim.linkEnd(anim.toCells.get(a), anim.toCells.get(b), sx, sy, tx, ty);
            if (e == null) return;
            tx = e[0];
            ty = e[1];
        }
        arrow(g, sx, sy, tx, ty, a.lost ? GHOST : LINE);
    }

    /** חוליה אחת: ערך + תא המצביע (עם נקודה), ומעליה המשתנים שמצביעים עליה; חוליה שאבדה — באדום מקווקו, ומעליה "אבדה" */
    private void chainCell(Graphics2D g, Cell c, int bx, int top, int vw, int pw, int ch) {
        if (c.lost) {   // אף משתנה לא מצביע עליה
            ghostBox(g, bx, top, vw, ch, c.value);
            ghostBox(g, bx + vw, top, pw, ch, "");
            g.setColor(GHOST);
            g.fillOval(bx + vw + pw / 2 - 3, top + ch / 2 - 3, 7, 7);
            center(g, "אבדה", SMALL_B, bx + (vw + pw) / 2, top - 12);
            return;
        }
        cell(g, bx, top, vw, ch, color(c.mark), c.value, c.obj, c.sub);
        box(g, bx + vw, top, pw, ch, color(c.mark), null, false);
        g.setColor(LINE);
        g.fillOval(bx + vw + pw / 2 - 3, top + ch / 2 - 3, 7, 7);
        if (c.current) {
            g.setColor(CURRENT);
            g.setStroke(THICK);
            g.drawRoundRect(bx - 2, top - 2, vw + pw + 4, ch + 4, ARC + 4, ARC + 4);
        }
        drawPointers(g, c, bx + (vw + pw) / 2, vw + pw, top - LBL_BASE, top - 1, 0);
    }

    // ------------------------------------------------------------ שגיאה

    private static final Color PROB_BG = new Color(0xFEF2F2), PROB_LINE = new Color(0xF19A9A), HINT_BG = new Color(0xFFF7E0);
    private final Font PROB_T = Theme.font(Theme.boldFamily, Theme.boldStyle, 17);
    private final Font CODE = new Font(Font.MONOSPACED, Font.BOLD, 14);

    /** כרטיס השגיאה: מימין — מה קרה, למה, ומה עושים; משמאל — ציור קטן של מה שחסר */
    private Dimension drawProblem(Graphics2D g, ProblemView p, int x, int y) {
        int avail = viewportWidth() > 300 ? viewportWidth() - 2 * PAD : 760;
        int w = Math.max(420, Math.min(820, avail));
        int pic = p.kind.equals("other") ? 0 : 190, textW = w - pic - 40;
        int th = paragraph(null, p.what, PROB_T, GHOST, 0, 0, textW);
        int wh = paragraph(null, p.why, SMALL_B, INK, 0, 0, textW);
        int hh = paragraph(null, "מה עושים: " + p.hint, SMALL, INK, 0, 0, textW - 16);
        int h = Math.max(16 + th + 8 + wh + 12 + hh + 16 + 16, pic > 0 ? 130 : 0);
        if (g == null) return new Dimension(w, h);
        g.setColor(PROB_BG);
        g.fillRoundRect(x, y, w, h, 14, 14);
        g.setColor(PROB_LINE);
        g.setStroke(new BasicStroke(2f));
        g.drawRoundRect(x, y, w, h, 14, 14);
        int right = x + w - 18, ty = y + 16;
        ty += paragraph(g, p.what, PROB_T, GHOST, right, ty, textW) + 8;
        ty += paragraph(g, p.why, SMALL_B, INK, right, ty, textW) + 12;
        g.setColor(HINT_BG);
        g.fillRoundRect(right - textW, ty - 4, textW, hh + 12, 10, 10);
        paragraph(g, "מה עושים: " + p.hint, SMALL, INK, right - 8, ty + 2, textW - 16);
        if (pic > 0) problemPicture(g, p, x + 18, y + 18, pic - 20, h - 36);
        return new Dimension(w, h);
    }

    /** הציור הקטן: מחסנית / תור ריקים עם "?", משתנה שמצביע על null, תא שאין במערך, רקורסיה */
    private void problemPicture(Graphics2D g, ProblemView p, int x, int y, int w, int h) {
        g.setStroke(THICK);
        int cx = x + w / 2, cy = y + h / 2;
        switch (p.kind) {
            case "empty-stack": {
                int bw = 70, bh = 34, top = cy - 16;
                ghostBox(g, cx - bw / 2, top - bh - 8, bw, bh, "?");
                g.setColor(GHOST);
                g.setStroke(THICK);
                g.drawPolyline(new int[] { cx - bw / 2 - 6, cx - bw / 2 - 6, cx + bw / 2 + 6, cx + bw / 2 + 6 },
                        new int[] { top, top + bh + 8, top + bh + 8, top }, 4);
                center(g, "ריקה", SMALL_B, cx, top + bh / 2 + 4);
                if (p.op != null) center(g, p.op, CODE, cx, top + bh + 26);
                break;
            }
            case "empty-queue": {
                int bw = 52, bh = 34, left = x + 52;
                ghostBox(g, x, cy - bh / 2, bw - 8, bh, "?");
                g.setColor(GHOST);
                g.setStroke(THICK);
                g.drawLine(left, cy - bh / 2 - 5, left + 2 * bw, cy - bh / 2 - 5);
                g.drawLine(left, cy + bh / 2 + 5, left + 2 * bw, cy + bh / 2 + 5);
                arrow(g, left - 2, cy, left - 2 - QARROW, cy, GHOST, 6, 1.5f);
                center(g, "ריק", SMALL_B, left + bw, cy);
                if (p.op != null) center(g, p.op, CODE, x + bw / 2, cy + bh / 2 + 22);
                break;
            }
            case "null": {
                String name = p.name != null ? p.name : "?";
                int bw = Math.max(44, textWidth(name, CODE) + 18), bh = 34, bx = x + 4, by = cy - 26;
                box(g, bx, by, bw, bh, BOX, null, false);
                g.setColor(INK);
                center(g, name, CODE, bx + bw / 2, by + bh / 2);
                arrow(g, bx + bw + 4, by + bh / 2, bx + bw + 46, by + bh / 2, GHOST);
                g.setColor(GHOST);
                g.setFont(SMALL_B);
                g.drawString("null", bx + bw + 52, by + bh / 2 + 5);
                int nx = bx + bw + 52 + textWidth("null", SMALL_B) / 2, ny = by + bh / 2 + 24;
                g.setStroke(THICK);
                g.drawLine(nx - 8, ny - 8, nx + 8, ny + 8);
                g.drawLine(nx + 8, ny - 8, nx - 8, ny + 8);
                if (p.op != null) center(g, "." + p.op, CODE, bx + bw / 2 + 40, by + bh + 46);
                break;
            }
            case "index": {
                int n = p.length < 0 ? 3 : Math.min(p.length, 4), cw = 30, ch = 30, bx = x, by = cy - 20;
                for (int i = 0; i < n; i++) {
                    box(g, bx + i * cw, by, cw, ch, BOX, null, false);
                    g.setColor(MUTED);
                    center(g, String.valueOf(p.length > 4 && i == 3 ? p.length - 1 : i), SMALL, bx + i * cw + cw / 2, by + ch + 12);
                }
                if (p.length > 4) { g.setColor(MUTED); center(g, "…", SMALL_B, bx + 3 * cw - 2, by + ch / 2); }
                int gx = bx + n * cw + 8;
                ghostBox(g, gx, by, cw + 6, ch, "?");
                g.setColor(GHOST);
                center(g, p.index >= 0 ? String.valueOf(p.index) : "?", SMALL_B, gx + (cw + 6) / 2, by + ch + 12);
                break;
            }
            case "recursion": {
                g.setFont(CODE);
                for (int i = 0; i < 4; i++) {
                    int bx = x + i * 12, by = y + 8 + i * 22;
                    g.setColor(i == 3 ? GHOST_BG : FOCUS_BG);
                    g.fillRoundRect(bx, by, 120, 26, 8, 8);
                    g.setColor(i == 3 ? GHOST : FOCUS);
                    g.setStroke(i == 3 ? DASHED : THIN);
                    g.drawRoundRect(bx, by, 120, 26, 8, 8);
                    g.drawString(i == 3 ? "..." : "f(...)", bx + 10, by + 18);
                }
                break;
            }
            case "loop": {   // לולאה שלא נגמרת: ∞ גדול, החץ שחוזר לתנאי, התנאי עצמו וכמה סיבובים
                int iw = Math.min(110, w - 20), top = y + 6;
                LoopCard.infinity(g, cx - iw / 2, top + iw / 4, iw, GHOST);
                int ty = top + iw / 2 + 18;
                if (p.op != null) {
                    String c = p.op.length() > 22 ? p.op.substring(0, 21) + "…" : p.op;
                    g.setColor(INK);
                    center(g, c, CODE, cx, ty);
                    ty += 22;
                }
                if (p.index >= 2) {
                    g.setColor(GHOST);
                    center(g, "סיבוב " + p.index + "…", SMALL_B, cx, ty);
                }
                break;
            }
            default:
                break;
        }
    }

    /**
     * פסקה בעברית (מימין לשמאל — גם כשהיא מתחילה במילה באנגלית), מיושרת לימין עד right, עם שבירת שורות לפי width.
     * g == null — רק מחשבים את הגובה.
     */
    private int paragraph(Graphics2D g, String text, Font f, Color c, int right, int y, int width) {
        if (text == null || text.isEmpty()) return 0;
        java.text.AttributedString as = new java.text.AttributedString(text);
        as.addAttribute(java.awt.font.TextAttribute.FONT, f);
        as.addAttribute(java.awt.font.TextAttribute.RUN_DIRECTION, java.awt.font.TextAttribute.RUN_DIRECTION_RTL);
        java.awt.font.FontRenderContext frc = g != null ? g.getFontRenderContext() : new java.awt.font.FontRenderContext(null, true, true);
        java.awt.font.LineBreakMeasurer lbm = new java.awt.font.LineBreakMeasurer(as.getIterator(), frc);
        int h = 0;
        if (g != null) g.setColor(c);
        while (lbm.getPosition() < text.length()) {
            java.awt.font.TextLayout tl = lbm.nextLayout(Math.max(40, width));
            float asc = tl.getAscent(), lh = tl.getAscent() + tl.getDescent() + tl.getLeading() + 2;
            if (g != null) tl.draw(g, right - tl.getAdvance(), y + h + asc);
            h += Math.round(lh);
        }
        return h;
    }

    // ------------------------------------------------------------ עץ

    private Dimension drawTree(Graphics2D g, TreeView v, int x, int y) {
        final List<TreeCell> inorder = new ArrayList<TreeCell>();
        final Map<TreeCell, Integer> depth = new IdentityHashMap<TreeCell, Integer>();
        collect(v.root, 0, inorder, depth);
        int maxDepth = 0, maxW = 0;
        boolean objs = false;
        List<View> nodeSubs = new ArrayList<View>();
        for (TreeCell c : inorder) {
            maxDepth = Math.max(maxDepth, depth.get(c));
            maxW = Math.max(maxW, c.obj != null ? objW(c.obj) : textWidth(c.value, VALUE));
            objs |= c.obj != null;
            nodeSubs.add(c.sub);
        }
        // צמתים של אובייקטים / של מבנים: מלבן קטן במקום עיגול
        Dimension sb = subsBox(nodeSubs);
        if (sb != null) maxW = Math.max(maxW, sb.width);
        boolean boxy = objs || sb != null;
        final int r = boxy ? Math.max(OBJ_H / 2, maxW / 2) : Math.max(19, maxW / 2 + 9);
        final int ry = boxy ? Math.max(OBJ_H / 2, sb != null ? sb.height / 2 : 0) : r;
        int most = 0;
        for (TreeCell c : inorder) most = Math.max(most, pointerRowW(c.labels));
        // המצביעים: השמות זה לצד זה מעל הצומת — הצמתים מתרחקים כך שהשמות של שכנים לא נוגעים זה בזה
        int nw = Math.max(2 * r + 12, most + LBL_SPACE), lv = boxy ? Math.max(76, 2 * ry + 30) : 70;
        final int extra = most > 0 ? 6 : 0;
        int w = Math.max(inorder.size() * nw + 16, titleWidth(v.title, "עץ בינארי"));
        int h = th() + 22 + extra + (maxDepth + 1) * lv;
        if (g == null) return new Dimension(w, h);

        drawTitle(g, v.title, "עץ בינארי", x, y, v.focus);
        Map<TreeCell, int[]> pos = new IdentityHashMap<TreeCell, int[]>();
        for (int i = 0; i < inorder.size(); i++) {
            TreeCell c = inorder.get(i);
            pos.put(c, new int[] { x + 8 + i * nw + nw / 2, y + th() + 22 + extra + depth.get(c) * lv + ry });
        }
        g.setColor(LINE);
        g.setStroke(new BasicStroke(2f));
        for (TreeCell c : inorder) {
            int[] p = pos.get(c);
            for (TreeCell k : new TreeCell[] { c.left, c.right }) {
                if (k == null) continue;
                int[] q = pos.get(k);
                g.drawLine(p[0], p[1], q[0], q[1]);
            }
        }
        for (TreeCell c : inorder) {
            int[] p = pos.get(c);
            if (c.sub != null || c.obj != null) {
                int cwid = c.sub != null ? 2 * r : Math.max(objW(c.obj), 2 * ry);
                cell(g, p[0] - cwid / 2, p[1] - ry, cwid, 2 * ry, color(c.mark), c.value, c.obj, c.sub);
                if (c.current || c.shared) {
                    g.setColor(c.current ? CURRENT : BOX_LINE);
                    g.setStroke(c.shared ? DASHED : THICK);
                    g.drawRoundRect(p[0] - cwid / 2 - 2, p[1] - ry - 2, cwid + 4, 2 * ry + 4, ARC + 3, ARC + 3);
                }
            } else {
                g.setColor(color(c.mark));
                g.fillOval(p[0] - r, p[1] - r, 2 * r, 2 * r);
                g.setColor(c.current ? CURRENT : c.mark == Mark.NONE ? BOX_LINE : darker(color(c.mark)));
                g.setStroke(c.shared ? DASHED : c.current ? THICK : THIN);
                g.drawOval(p[0] - r, p[1] - r, 2 * r, 2 * r);
                g.setColor(INK);
                center(g, c.value, VALUE, p[0], p[1]);
            }
            int top = p[1] - ry, span = c.sub != null ? 2 * r : c.obj != null ? Math.max(objW(c.obj), 2 * ry) : 2 * r;
            drawPointers(g, c, p[0], span, top - LBL_BASE_TREE, top - 1, c.sub != null || c.obj != null ? 0 : r);
        }
        return new Dimension(w, h);
    }

    private static void collect(TreeCell c, int d, List<TreeCell> out, Map<TreeCell, Integer> depth) {
        if (c == null) return;
        collect(c.left, d + 1, out, depth);
        out.add(c);
        depth.put(c, d);
        collect(c.right, d + 1, out, depth);
    }

    // ------------------------------------------------------------ אובייקטים של מחלקות (Student, Car, ...)
    //
    //  • בתוך מבנה — כרטיס קטן: שורה ראשונה שם, שנייה פרטים עיקריים (למשל "יא2 · 84"). ליד הכרטיס — שם המשתנה שמצביע אליו.
    //  • צעד שקורא / משנה מאפיין (s.getName()): הכרטיס מודגש, ומתחתיו נפתחת חלונית עם כל המאפיינים והמאפיין שנגעו בו מודגש.
    //  • אובייקט ששמור במשתנה ואינו בתוך מבנה — כרטיס מלא (כל המאפיינים) לבד.
    //  • מעבר עכבר על כרטיס — חלונית עם כל המאפיינים (בכל צעד).

    // ------------------------------------------------------------ מבנה בתוך מבנה
    // מחסנית של מחסניות, רשימה של תורים, עץ של מחסניות...: התא מכיל תצוגה מוקטנת של המבנה הפנימי (בלי כותרת),
    // שמצוירת באותן פונקציות ציור (ולכן גם היא יכולה להכיל מבנים — עד MAX_NEST רמות).

    private int nest;                                  // כמה רמות של "בתוך תא" אנחנו כרגע (0 = מבנה ראשי)
    private static final double SUB_SCALE = 0.8, SUB_MIN = 0.5;
    private static final int SUB_MAX_W = 300, SUB_MAX_H = 190;

    private int th() { return nest > 0 ? 6 : TITLE_H; }

    private Dimension drawView(Graphics2D g, View v, int x, int y) {
        if (v instanceof ArrayView) return drawArray(g, (ArrayView) v, x, y);
        if (v instanceof MatrixView) return drawMatrix(g, (MatrixView) v, x, y);
        if (v instanceof LinearView) return ((LinearView) v).stack ? drawStack(g, (LinearView) v, x, y) : drawQueue(g, (LinearView) v, x, y);
        if (v instanceof ChainView) return drawChain(g, (ChainView) v, x, y);
        if (v instanceof TreeView) return drawTree(g, (TreeView) v, x, y);
        return new Dimension(0, 0);
    }

    private static View subAt(List<View> subs, int i) {
        return i < subs.size() ? subs.get(i) : null;
    }

    private static boolean hasSubs(List<View> subs) {
        for (View s : subs) if (s != null) return true;
        return false;
    }

    private double subScale(Dimension d) {
        return Math.max(SUB_MIN, Math.min(SUB_SCALE, Math.min(SUB_MAX_W / (double) Math.max(1, d.width), SUB_MAX_H / (double) Math.max(1, d.height))));
    }

    /** גודל המבנה הפנימי אחרי הקטנה (בלי שוליים) */
    private Dimension subDim(View sub) {
        nest++;
        Dimension d = drawView(null, sub, 0, 0);
        nest--;
        double sc = subScale(d);
        return new Dimension((int) Math.ceil(d.width * sc), (int) Math.ceil(d.height * sc));
    }

    /** הגודל של התא הרחב / הגבוה ביותר שמכיל מבנה (עם שוליים); null אם אין מבנים בתאים */
    private Dimension subsBox(List<View> subs) {
        int w = 0, h = 0;
        boolean any = false;
        for (View s : subs) {
            if (s == null) continue;
            any = true;
            Dimension d = subDim(s);
            w = Math.max(w, d.width + 18);
            h = Math.max(h, d.height + 14);
        }
        return any ? new Dimension(w, h) : null;
    }

    private void subBox(Graphics2D g, int x, int y, int w, int h, Color fill, View sub) {
        g.setColor(fill);
        g.fillRoundRect(x + 1, y + 1, w - 2, h - 2, ARC + 2, ARC + 2);
        g.setColor(sub.focus ? FOCUS : fill == BOX ? BOX_LINE : darker(fill));
        g.setStroke(sub.focus ? THICK : THIN);
        g.drawRoundRect(x + 1, y + 1, w - 2, h - 2, ARC + 2, ARC + 2);
        nest++;
        Dimension d = drawView(null, sub, 0, 0);
        nest--;
        double sc = subScale(d);
        int dw = (int) Math.round(d.width * sc), dh = (int) Math.round(d.height * sc);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(x + (w - dw) / 2.0, y + (h - dh) / 2.0);
        g2.scale(sc, sc);
        nest++;
        drawView(g2, sub, 0, 0);
        nest--;
        g2.dispose();
        if (sub.title != null && !sub.title.isEmpty()) badge(g, sub.title, x + w - 4, y + 2);   // שם המשתנה על פינת התא
    }

    private static boolean hasObjs(List<Obj> objs) {
        for (Obj o : objs) if (o != null) return true;
        return false;
    }

    private static Obj objAt(List<Obj> objs, int i) {
        return i < objs.size() ? objs.get(i) : null;
    }

    private int objW(Obj o) {
        return Math.max(70, Math.max(textWidth(o.title, CARD_T), textWidth(o.sub, CARD_S)) + 22);
    }

    /** רוחב התא הרחב ביותר (טקסט רגיל או כרטיס) */
    private int widthOf(List<String> items, List<Obj> objs, String extra) {
        int m = extra == null ? 0 : textWidth(extra, VALUE);
        for (int i = 0; i < items.size(); i++) {
            Obj o = objAt(objs, i);
            m = Math.max(m, o != null ? objW(o) : textWidth(items.get(i), VALUE));
        }
        return m;
    }

    /** תא: ערך רגיל, או כרטיס אם זה אובייקט */
    private void cell(Graphics2D g, int x, int y, int w, int h, Color fill, String text, Obj o) {
        cell(g, x, y, w, h, fill, text, o, null);
    }

    private void cell(Graphics2D g, int x, int y, int w, int h, Color fill, String text, Obj o, View sub) {
        if (sub != null) subBox(g, x, y, w, h, fill, sub);
        else if (o == null) box(g, x, y, w, h, fill, text, false);
        else objBox(g, x, y, w, h, fill, o);
    }

    private boolean accessed(Obj o) {
        Access a = step == null ? null : step.access;
        return a != null && o != null && a.obj != null && o.id > 0 && o.id == a.obj.id;
    }

    private void objBox(Graphics2D g, int x, int y, int w, int h, Color fill, Obj o) {
        boolean acc = accessed(o);
        if (acc) fill = color(step.access.mark);
        g.setColor(fill);
        g.fillRoundRect(x + 1, y + 1, w - 2, h - 2, ARC + 2, ARC + 2);
        g.setColor(acc ? darker(fill) : fill == BOX ? BOX_LINE : darker(fill));
        g.setStroke(acc ? THICK : THIN);
        g.drawRoundRect(x + 1, y + 1, w - 2, h - 2, ARC + 2, ARC + 2);
        g.setColor(INK);
        center(g, o.title, CARD_T, x + w / 2, y + h * 38 / 100);
        if (acc) {
            // המאפיין שנקרא / שונה, ישר בכרטיס: "average = 84.0" (כל המאפיינים — בשורת הפרטים שמתחת)
            Access a = step.access;
            String val = a.field;
            for (String[] f : o.fields) if (f[0].equals(a.field)) val = f[1];
            // "average = 84.0" אם נכנס; אחרת בלי רווחים ובגופן קטן יותר; ואחרת — רק הערך (השם בשורת הפרטים שמתחת)
            String[] tries = { a.field + " = " + val, a.field + "=" + val, val };
            Font[] fonts = { FIELD_B, FIELD_B.deriveFont(11.5f), FIELD_B };
            int pick = 2;
            for (int i = 0; i < tries.length; i++) {
                if (getFontMetrics(fonts[i]).stringWidth(tries[i]) <= w - 10) { pick = i; break; }
            }
            String t = tries[pick];
            FontMetrics fm = getFontMetrics(fonts[pick]);
            while (t.length() > 4 && fm.stringWidth(t) > w - 10) t = t.substring(0, t.length() - 2) + "…";
            g.setColor(INK);
            center(g, t, fonts[pick], x + w / 2, y + h * 74 / 100);
        } else {
            g.setColor(MUTED);
            center(g, o.sub, CARD_S, x + w / 2, y + h * 74 / 100);
        }
        if (!o.labels.isEmpty()) badge(g, String.join(",", o.labels), x + w - 5, y + h);
        if (nest > 0 || noHits) return;   // בתוך מבנה מוקטן הקואורדינטות אחרות — בלי ריחוף (וגם לא על איבר שהוצא, בהנפשה)
        Rectangle r = new Rectangle(x, y, w, h);
        if (o.id > 0 && !objRects.containsKey(o.id)) objRects.put(o.id, new Object[] { r, Boolean.valueOf(inStack), o });
        objHits.add(new Object[] { r, o });
    }

    /** שם המשתנה על פינת הכרטיס (s1, best...) */
    private void badge(Graphics2D g, String text, int right, int cy) {
        g.setFont(BADGE);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(text) + 12, h = 16, x = right - w, y = cy - h / 2;
        g.setColor(Color.WHITE);
        g.fillRoundRect(x, y, w, h, h, h);
        g.setColor(LABEL);
        g.setStroke(THIN);
        g.drawRoundRect(x, y, w, h, h, h);
        g.drawString(text, x + 6, y + h - 4);
    }

    /** טבלת מאפיינים (ראו ObjCard): עד שתי עמודות בכרטיס המלא ובחלונית */
    private Dimension fieldsTable(Graphics2D g, Obj o, int x, int y, Access acc) {
        return ObjCard.table(this, g, o, x, y, acc, ObjCard.columnsFor(o.fields.size(), 2));
    }

    private static final Color SELF = new Color(0x7C3AED), SELF_BG = new Color(0xF6F1FF), SELF_INK = new Color(0x5B21B6);

    /**
     * אובייקט ששמור במשתנה ואינו בתוך מבנה: כותרת (שם המשתנה + סוג) וכל המאפיינים.
     * בתוך פעולה של האובייקט (d1.birthday()): הכותרת היא d1 עם תג "this", מסגרת סגולה, ומתחת "birthday() רצה בתוך d1" (ראו SelfViz).
     */
    private Dimension drawObjView(Graphics2D g, ObjView v, int x, int y) {
        SelfViz.Info self = SelfViz.of(step);
        boolean me = SelfViz.active(self, v.obj);
        String title = SelfViz.title(self, v.obj, v.title), cap = SelfViz.caption(self, v.obj);
        boolean thisChip = v.obj.labels.contains("this") && !title.equals("this");
        java.util.Map<String, Mark> marks = SelfViz.hotFor(self, v.obj);
        Dimension t = fieldsTable(null, v.obj, 0, 0, null);
        int chipW = thisChip ? textWidth("this", SMALL) + 14 + 6 : 0;
        int w = Math.max(t.width + 24, titleWidth(title, v.obj.type) + chipW), h = TITLE_H + t.height + 20;
        if (cap != null) {
            w = Math.max(w, textWidth(cap, SMALL) + 4);
            h += 20;
        }
        if (g == null) return new Dimension(w, h);
        boolean acc = accessed(v.obj);
        drawTitle(g, title, v.obj.type, x, y, v.focus || acc || me);
        if (thisChip) {   // תג "this" ליד הסוג: d1 הוא this בתוך הפעולה
            int cx = x + textWidth(title, TITLE) + 12 + textWidth(v.obj.type, TYPE) + 14 + 6;
            g.setFont(SMALL);
            FontMetrics fm = g.getFontMetrics();
            int th = fm.getHeight() + 1, base = y + TITLE_H - 11;
            g.setColor(SELF);
            g.fillRoundRect(cx, base - th + 5, fm.stringWidth("this") + 14, th, th, th);
            g.setColor(Color.WHITE);
            g.drawString("this", cx + 7, base);
        }
        Color fill = acc ? color(step.access.mark).brighter() : me ? SELF_BG : new Color(0xF6F8FC);
        g.setColor(fill);
        g.fillRoundRect(x, y + TITLE_H, w, t.height + 16, 10, 10);
        g.setColor(acc ? darker(color(step.access.mark)) : me ? SELF : BOX_LINE);
        g.setStroke(acc || me ? THICK : THIN);
        g.drawRoundRect(x, y + TITLE_H, w, t.height + 16, 10, 10);
        ObjCard.table(this, g, v.obj, x + 12, y + TITLE_H + 8, step.access, ObjCard.columnsFor(v.obj.fields.size(), 2), marks);
        if (cap != null) {
            g.setFont(SMALL);
            g.setColor(SELF_INK);
            g.drawString(cap, x + 2, y + TITLE_H + t.height + 16 + 17);
        }
        Rectangle r = new Rectangle(x, y + TITLE_H, w, t.height + 16);
        objHits.add(new Object[] { r, v.obj });
        return new Dimension(w, h);
    }

    @Override
    public String getToolTipText(java.awt.event.MouseEvent e) {
        for (Object[] h : screenHits) {
            if (!((Rectangle) h[0]).contains(e.getPoint())) continue;
            Obj o = (Obj) h[1];
            StringBuilder b = new StringBuilder("<html><body style='padding:2px'><b>").append(esc(o.type)).append("</b><table cellspacing=0 cellpadding=1>");
            for (String[] f : o.fields) b.append("<tr><td><font color='#6b7385'>").append(esc(f[0])).append("</font>&nbsp;&nbsp;</td><td>").append(esc(f[1])).append("</td></tr>");
            return b.append("</table></body></html>").toString();
        }
        return null;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ------------------------------------------------------------ עזרי ציור

    private static final int LABEL_STEP = 15;

    /**
     * שמות משתנים מעל תא: כל שם בשורה משלו (הראשון הכי קרוב), וחץ קטן אחד אל התא.
     * בכוונה רק למשתני אינדקס (i, j מעל תא במערך / תו במחרוזת / המונה בלולאה) — הם לא מצביעים, והתאים צרים.
     * לחוליות ולצמתים (מצביעים) — pointerLabels: חץ וצבע לכל משתנה
     */
    private void labelStack(Graphics2D g, List<String> names, int cx, int bottomY) {
        g.setColor(LABEL);
        g.setFont(SMALL_B);
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < names.size(); i++) {
            String l = names.get(i);
            g.drawString(l, cx - fm.stringWidth(l) / 2, bottomY - 8 - i * LABEL_STEP);
        }
        g.fillPolygon(new int[] { cx - 4, cx + 4, cx }, new int[] { bottomY - 6, bottomY - 6, bottomY }, 3);
    }

    // ------------------------------------------------------------ מצביעים (משתנים שמצביעים על חוליה / צומת)

    private static final int LABELS_H = 38;    // מקום מעל שורת חוליות שיש בה מצביעים: השמות והחצים
    private static final int LBL_BASE = 22;    // קו הבסיס של השמות: כמה מעל התא (ברשימה)
    private static final int LBL_BASE_TREE = 16;   // בעץ — קצר יותר (בין הרמות אין הרבה מקום)
    private static final int LBL_GAP = 9;      // רווח בין שני שמות מעל אותו תא
    private static final int LBL_SPACE = 14;   // רווח מינימלי בין השמות של שני תאים שכנים

    /**
     * צבע לכל מצביע: כהים וברורים על לבן, ושונים מצבעי הסימון של התאים (נוסף / נקרא / שונה / הוצא / הנוכחי).
     * הסגול הראשון — כמו שהיה לכל השמות עד עכשיו
     */
    private static final Color[] PTR_C = {
        new Color(0x7C3AED),   // סגול
        new Color(0x0F766E),   // טורקיז כהה
        new Color(0xDB2777),   // ורוד
        new Color(0x1D4ED8),   // כחול
        new Color(0x15803D),   // ירוק
        new Color(0x854D0E),   // חום
    };
    /** שם → צבע: נקבע בפעם הראשונה שהשם מצויר, ונשאר כך בכל הצעדים (קדימה ואחורה) */
    private static final Map<String, Color> ptrColors = new HashMap<String, Color>();

    static Color pointerColor(String name) {
        synchronized (ptrColors) {
            Color c = ptrColors.get(name);
            if (c == null) {
                c = PTR_C[ptrColors.size() % PTR_C.length];
                ptrColors.put(name, c);
            }
            return c;
        }
    }

    /** רוחב השמות מעל תא, זה לצד זה */
    private int pointerRowW(List<String> names) {
        if (names.isEmpty()) return 0;
        int w = 0;
        for (String n : names) w += textWidth(n, SMALL_B);
        return w + (names.size() - 1) * LBL_GAP;
    }

    /**
     * המשתנים שמצביעים על תא: השמות זה לצד זה (באמצע מעל התא), כל אחד בצבע שלו, ומכל שם חץ משלו — אל נקודה אחרת
     * על הקצה העליון של התא (משמאל לימין, באותו סדר, כך שהחצים לא נחתכים). כך רואים שכל משתנה הוא מצביע בפני עצמו.
     * span = רוחב התא; base = קו הבסיס של השמות; endY = הקצה העליון; circleR > 0 — התא הוא עיגול (צומת בעץ), והחצים
     * נגמרים על ההיקף. fixed — צבע אחד לכולם (null בסוף הרשימה: אדום)
     */
    private void pointerLabels(Graphics2D g, List<String> names, int cx, int span, int base, int endY, int circleR, Color fixed) {
        int k = names.size();
        if (k == 0) return;
        g.setFont(SMALL_B);
        FontMetrics fm = g.getFontMetrics();
        int rowW = pointerRowW(names);
        int lx = cx - rowW / 2;
        int sp = Math.min(Math.max(0, span - 16), Math.max((k - 1) * 14, rowW - 40));   // כמה רחב הפיזור של קצות החצים
        for (int i = 0; i < k; i++) {
            String name = names.get(i);
            int tw = fm.stringWidth(name);
            Color c = fixed != null ? fixed : pointerColor(name);
            int ex = k == 1 ? cx : cx - sp / 2 + i * sp / (k - 1);
            int ey = endY;
            if (circleR > 0) {
                int dx = Math.min(Math.abs(ex - cx), circleR - 1);
                ey = endY + circleR - (int) Math.round(Math.sqrt(circleR * circleR - dx * dx));
            }
            g.setColor(c);
            g.drawString(name, lx, base);
            arrow(g, lx + tw / 2, base + 4, ex, ey, c, 7, 1.8f);
            lx += tw + LBL_GAP;
        }
    }

    /** המצביעים על חוליה / צומת; בלי שמות — משולש כתום על התא האחרון שנגעו בו (בהרצה רגילה) */
    private void drawPointers(Graphics2D g, Cell c, int cx, int span, int base, int endY, int circleR) {
        if (!c.labels.isEmpty()) {
            pointerLabels(g, c.labels, cx, span, base, endY, circleR, null);
        } else if (c.current) {
            int bottomY = endY - (circleR > 0 ? 3 : 5);
            g.setColor(CURRENT);
            g.fillPolygon(new Polygon(new int[] { cx - 7, cx + 7, cx }, new int[] { bottomY - 12, bottomY - 12, bottomY - 2 }, 3));
        }
    }

    /** כותרת מבנה: שם המשתנה, ולידו "תגית" אפורה עם סוג המבנה */
    private void drawTitle(Graphics2D g, String name, String type, int x, int y, boolean focus) {
        if (nest > 0) return;   // מבנה בתוך תא: בלי כותרת
        g.setFont(TITLE);
        FontMetrics fm = g.getFontMetrics();
        int nw = fm.stringWidth(name), base = y + TITLE_H - 11;
        if (focus) {
            g.setColor(FOCUS_BG);
            g.fillRoundRect(x - 6, y + 2, nw + 12, TITLE_H - 8, 10, 10);
        }
        g.setColor(focus ? FOCUS : INK);
        g.drawString(name, x, base);
        g.setFont(TYPE);
        FontMetrics tm = g.getFontMetrics();
        int tx = x + nw + 12, tw = tm.stringWidth(type) + 14, th = tm.getHeight() + 2;
        g.setColor(new Color(0xEEF0F4));
        g.fillRoundRect(tx, base - th + 5, tw, th, th, th);
        g.setColor(MUTED);
        g.drawString(type, tx + 7, base);
    }

    private int titleWidth(String name, String type) {
        if (nest > 0) return 0;
        return textWidth(name, TITLE) + 12 + textWidth(type, TYPE) + 14 + 16;
    }

    private void box(Graphics2D g, int x, int y, int w, int h, Color fill, String text, boolean dashed) {
        g.setColor(fill);
        g.fillRoundRect(x + 1, y + 1, w - 2, h - 2, ARC, ARC);
        g.setColor(fill == BOX ? BOX_LINE : darker(fill));
        g.setStroke(dashed ? DASHED : THIN);
        g.drawRoundRect(x + 1, y + 1, w - 2, h - 2, ARC, ARC);
        if (text != null) {
            g.setColor(INK);
            center(g, text, VALUE, x + w / 2, y + h / 2);
        }
    }

    private static Color darker(Color c) {
        return new Color((int) (c.getRed() * 0.72), (int) (c.getGreen() * 0.72), (int) (c.getBlue() * 0.72));
    }

    private void ghostBox(Graphics2D g, int x, int y, int w, int h, String text) {
        g.setColor(GHOST_BG);
        g.fillRoundRect(x + 1, y + 1, w - 2, h - 2, ARC, ARC);
        g.setColor(GHOST);
        g.setStroke(DASHED);
        g.drawRoundRect(x + 1, y + 1, w - 2, h - 2, ARC, ARC);
        g.setColor(GHOST);
        center(g, text, VALUE, x + w / 2, y + h / 2);
    }

    private void arrow(Graphics2D g, int x1, int y1, int x2, int y2, Color c) {
        arrow(g, x1, y1, x2, y2, c, 9, 2f);
    }

    /** חץ מ-(x1,y1) אל (x2,y2), עם ראש באורך len */
    private void arrow(Graphics2D g, int x1, int y1, int x2, int y2, Color c, int len, float width) {
        g.setColor(c);
        g.setStroke(new BasicStroke(width));
        g.drawLine(x1, y1, x2, y2);
        double a = Math.atan2(y2 - y1, x2 - x1);
        int ax = (int) Math.round(x2 - len * Math.cos(a - 0.45)), ay = (int) Math.round(y2 - len * Math.sin(a - 0.45));
        int bx = (int) Math.round(x2 - len * Math.cos(a + 0.45)), by = (int) Math.round(y2 - len * Math.sin(a + 0.45));
        g.fillPolygon(new int[] { x2, ax, bx }, new int[] { y2, ay, by }, 3);
    }

    private void center(Graphics2D g, String s, Font f, int cx, int cy) {
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2, cy + (fm.getAscent() - fm.getDescent()) / 2);
    }

    private int textWidth(String s, Font f) {
        return getFontMetrics(f).stringWidth(s);
    }

    private int maxWidth(List<String> items, String extra) {
        int m = extra == null ? 0 : textWidth(extra, VALUE);
        for (String s : items) m = Math.max(m, textWidth(s, VALUE));
        return m;
    }

    static Color color(Mark m) {
        switch (m) {
            case NEW: return NEW_C;
            case READ: return READ_C;
            case CHANGED: return CHANGED_C;
            default: return BOX;
        }
    }
}

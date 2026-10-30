package unit4.collectionsLib;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
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
        step = s;
        scrollPending = true;
        revalidate();
        repaint();
    }

    void setZoomListener(Runnable r) { zoomListener = r; }

    /** תצוגה קטנה (ליד מעקב הרקורסיה): מקטינים כך שהכול ייכנס גם לגובה */
    private boolean fitHeight;

    void setFitHeight(boolean b) { fitHeight = b; }

    void zoomBy(double f) {
        manualZoom = Math.max(0.4, Math.min(2.5, zoom * f));
        revalidate();
        repaint();
    }

    void fit() {
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
        Dimension d = layoutAll(null);
        double z = computeZoom(d);
        // כשיש מקום — המבנים במרכז
        int free = (int) (getWidth() - d.width * z);
        if (free > 0) g.translate(free / 2, 0);
        g.scale(z, z);
        focusRect = null;
        objRects.clear();
        objHits.clear();
        layoutAll(g);
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
            Dimension d = drawProblem(g, (ProblemView) v, PAD, y);
            y += d.height + GAP / 2;
            maxX = Math.max(maxX, PAD + d.width);
        }
        // לולאות: כרטיס לכל לולאה (הפנימית מוזחת מתחת לחיצונית), ולידן טבלת הזוגות של לולאות מקוננות
        int loopsH = 0, lx = PAD;
        for (View v : step.views) {   // המונים (לולאות מקוננות) — מעל הכרטיסים
            if (!(v instanceof Model.LoopCountersView)) continue;
            Dimension d = drawCounters(g, (Model.LoopCountersView) v, PAD, y);
            loopsH += d.height + 10;
            maxX = Math.max(maxX, PAD + d.width);
        }
        for (View v : step.views) {
            if (!(v instanceof LoopView)) continue;
            Dimension d = drawLoop(g, (LoopView) v, PAD, y + loopsH);
            loopsH += d.height + 8;
            lx = Math.max(lx, PAD + d.width);
            maxX = Math.max(maxX, PAD + d.width);
        }
        int gx = lx + GAP, gridH = 0;
        for (View v : step.views) {
            if (!(v instanceof LoopGridView)) continue;
            Dimension d = drawLoopGrid(g, (LoopGridView) v, gx, y);
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
                if (v instanceof ArrayView) drawArray(g, (ArrayView) v, ax, y);
                else if (v instanceof StringView) drawStringView(g, (StringView) v, ax, y);
                else drawMatrix(g, (MatrixView) v, ax, y);
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
                Dimension d = drawStack(g, (LinearView) v, x, y);
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
            if (v instanceof LinearView && !((LinearView) v).stack) d = drawQueue(g, (LinearView) v, PAD, y);
            if (d != null && g != null) noteFocus(v, PAD, y, d);
            if (d != null) { y += d.height + GAP / 2; maxX = Math.max(maxX, PAD + d.width); }
        }
        for (View v : step.views) {
            Dimension d = null;
            if (v instanceof ChainView) d = drawChain(g, (ChainView) v, PAD, y);
            if (d != null && g != null) noteFocus(v, PAD, y, d);
            if (d != null) { y += d.height + GAP / 2; maxX = Math.max(maxX, PAD + d.width); }
        }
        for (View v : step.views) {
            Dimension d = null;
            if (v instanceof TreeView) d = drawTree(g, (TreeView) v, PAD, y);
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
                drawObjView(g, (ObjView) v, ox, y);
                noteFocus(v, ox, y, d);
            }
            ox += d.width + GAP;
            orow = Math.max(orow, d.height);
            maxX = Math.max(maxX, ox);
        }
        if (orow > 0) y += orow + GAP / 2;
        return new Dimension(maxX + PAD, y + PAD);
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
            int sx = x + 16, top = cy + 4;
            for (int i = 0; i < v.structItems.size(); i++) {
                box(g, sx + i * 34, top, 32, 26, i == 0 ? READ_C : BOX, null, false);
                g.setColor(INK);
                center(g, v.structItems.get(i), SMALL, sx + i * 34 + 16, top + 13);
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
        if (v.ghost != null) {
            ghostBox(g, cx, y + th() + 4, cw, ch, v.ghost);
            g.setFont(SMALL_B);
            g.drawString("pop", cx + cw + 10, y + th() + 4 + ch / 2 + 5);
        }
        if (v.error != null) {   // ניסו להוציא / לקרוא — ואין איבר: "?" אדום במקום שבו היה צריך להיות
            ghostBox(g, cx, y + th() + 4, cw, ch, "?");
            g.setFont(SMALL_B);
            g.setColor(GHOST);
            g.drawString(v.error, cx + cw + 10, y + th() + 4 + ch / 2 + 5);
        }
        for (int i = 0; i < n; i++) {
            Color c = i == v.markIndex ? color(v.mark) : BOX;
            cell(g, cx, top + i * ch, cw, ch, c, v.items.get(i), objAt(v.objs, i), subAt(v.subs, i));
        }
        inStack = wasInStack;
        g.setColor(v.error != null ? GHOST : LINE);
        g.setStroke(THICK);
        int bottom = top + rows * ch + 5;
        g.drawPolyline(new int[] { cx - 5, cx - 5, cx + cw + 5, cx + cw + 5 },
                new int[] { top - 8, bottom, bottom, top - 8 }, 4);
        if (n == 0) {
            g.setColor(v.error != null ? GHOST : MUTED);
            center(g, "ריקה", SMALL_B, cx + cw / 2, top + ch / 2);
        } else {
            arrow(g, cx + cw + 42, top + ch / 2, cx + cw + 10, top + ch / 2, LINE);
            g.setFont(SMALL_B);
            g.drawString("top", cx + cw + 46, top + ch / 2 + 5);
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
        if (v.ghost != null) {
            ghostBox(g, x + 8, top, cw, ch, v.ghost);
            center(g, "remove", SMALL_B, x + 8 + cw / 2, top + ch + 16);
        }
        if (v.error != null) {   // remove / head על תור ריק: "?" אדום ביציאה
            ghostBox(g, x + 8, top, cw, ch, "?");
            g.setColor(GHOST);
            center(g, v.error, SMALL_B, x + 8 + cw / 2, top + ch + 16);
        }
        for (int i = 0; i < n; i++) {
            Color c = i == v.markIndex ? color(v.mark) : BOX;
            cell(g, left + i * cw, top, cw, ch, c, v.items.get(i), objAt(v.objs, i), subAt(v.subs, i));
        }
        g.setColor(v.error != null ? GHOST : LINE);
        g.setStroke(THICK);
        g.drawLine(left - 8, top - 5, left + rows * cw + 8, top - 5);
        g.drawLine(left - 8, top + ch + 5, left + rows * cw + 8, top + ch + 5);
        // צדדים פתוחים עם חיצים: משמאל יוצאים (הראש — remove), מימין נכנסים (הזנב — insert). כך תור לא נראה כמו מערך
        int mid = top + ch / 2, right = left + rows * cw;
        arrow(g, left - 6, mid, left - 6 - QARROW, mid, LINE, 6, 1.5f);
        arrow(g, right + 8 + QARROW, mid, right + 8, mid, LINE, 6, 1.5f);
        if (n == 0) {
            g.setColor(v.error != null ? GHOST : MUTED);
            center(g, "ריק", SMALL_B, left + cw / 2, top + ch / 2);
        } else {
            g.setColor(LINE);
            center(g, n == 1 ? "head / tail" : "head", SMALL_B, left + cw / 2, top + ch + 18);
            if (n > 1) center(g, "tail", SMALL_B, left + (n - 1) * cw + cw / 2, top + ch + 18);
        }
        return new Dimension(w, h);
    }

    // ------------------------------------------------------------ רשימה מקושרת

    private Dimension drawChain(Graphics2D g, ChainView v, int x, int y) {
        List<String> values = new ArrayList<String>();
        List<Obj> cellObjs = new ArrayList<Obj>();
        for (Cell c : v.cells) { values.add(c.value); cellObjs.add(c.obj); }
        int n = v.cells.size();
        boolean objs = hasObjs(cellObjs);
        int vw = Math.max(48, widthOf(values, cellObjs, null) + 22), pw = 20, ch = objs ? OBJ_H : 38, ag = 38;
        List<View> cellSubs = new ArrayList<View>();
        for (Cell c : v.cells) cellSubs.add(c.sub);
        Dimension sb = subsBox(cellSubs);
        if (sb != null) { vw = Math.max(vw, sb.width); ch = Math.max(ch, sb.height); }
        int stepW = vw + pw + ag;
        String tail = v.cycleTo >= 0 ? "" : v.joinText != null ? v.joinText + "  (ממשיך ברשימה אחרת)"
                : v.truncated ? "..." : "null";
        int extra = Math.max(labelExtra(v.cells), v.nullLabels.isEmpty() ? 0 : (v.nullLabels.size() - 1) * LABEL_STEP);   // כמה מצביעים על אותה חוליה — כל אחד בשורה משלו
        int top = y + th() + 24 + extra;
        int w = Math.max(16 + n * stepW + textWidth(tail, SMALL_B) + 10, titleWidth(v.title, "רשימה מקושרת"));
        int noteW = Math.max(w, Math.min(620, Math.max(360, w)));
        int noteH = v.lostNote == null || nest > 0 ? 0 : 10 + paragraph(null, v.lostNote, SMALL_B, GHOST, 0, 0, noteW) + paragraph(null, v.lostHint, SMALL, INK, 0, 0, noteW);
        int h = th() + 24 + extra + ch + (v.cycleTo >= 0 ? 36 : 14) + noteH;
        if (noteH > 0) w = Math.max(w, noteW);
        if (g == null) return new Dimension(w, h);

        drawTitle(g, v.title, "רשימה מקושרת", x, y, v.focus);
        for (int i = 0; i < n; i++) {
            Cell c = v.cells.get(i);
            int bx = x + 8 + i * stepW;
            if (c.lost) {   // אף משתנה לא מצביע עליה: אדום מקווקו, ומעליה "אבדה"
                ghostBox(g, bx, top, vw, ch, c.value);
                ghostBox(g, bx + vw, top, pw, ch, "");
                g.setColor(GHOST);
                g.fillOval(bx + vw + pw / 2 - 3, top + ch / 2 - 3, 7, 7);
                center(g, "אבדה", SMALL_B, bx + (vw + pw) / 2, top - 12);
                int sx = bx + vw + pw / 2, sy = top + ch / 2;
                if (i < n - 1) arrow(g, sx, sy, bx + stepW, sy, GHOST);
                continue;
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
            drawLabels(g, c, bx + (vw + pw) / 2, top - 6);
            int sx = bx + vw + pw / 2, sy = top + ch / 2;
            if (i < n - 1) arrow(g, sx, sy, bx + stepW, sy, LINE);
        }
        int lastX = x + 8 + (n - 1) * stepW + vw + pw / 2, midY = top + ch / 2;
        if (v.cycleTo >= 0) {
            int tx = x + 8 + v.cycleTo * stepW + vw / 2, low = top + ch + 24;
            g.setColor(GHOST);
            g.setStroke(THICK);
            g.drawPolyline(new int[] { lastX, lastX + 20, lastX + 20, tx, tx },
                    new int[] { midY, midY, low, low, top + ch + 10 }, 5);
            arrow(g, tx, top + ch + 12, tx, top + ch + 2, GHOST);
            g.setFont(SMALL_B);
            g.drawString("מעגל!", (lastX + tx) / 2, low + 14);
        } else {
            arrow(g, lastX, midY, lastX + ag, midY, LINE);
            g.setColor(v.joinText != null ? CURRENT : !v.nullLabels.isEmpty() ? GHOST : MUTED);
            g.setFont(SMALL_B);
            g.drawString(tail, lastX + ag + 4, midY + 5);
            if (!v.nullLabels.isEmpty()) {   // p שהוא null: מצביע על ה-null שבסוף — ומשם אי אפשר להמשיך
                int cx = lastX + ag + 4 + textWidth(tail, SMALL_B) / 2;
                g.setColor(GHOST);
                g.setFont(SMALL_B);
                FontMetrics fm = g.getFontMetrics();
                for (int k = 0; k < v.nullLabels.size(); k++) {
                    String l = v.nullLabels.get(k);
                    g.drawString(l, cx - fm.stringWidth(l) / 2, top - 6 - 8 - k * LABEL_STEP);
                }
                g.fillPolygon(new int[] { cx - 4, cx + 4, cx }, new int[] { midY - 18, midY - 18, midY - 11 }, 3);
                g.setStroke(THICK);
                g.drawLine(cx - 9, midY + 9, cx + 9, midY + 25);
                g.drawLine(cx + 9, midY + 9, cx - 9, midY + 25);
            }
        }
        if (noteH > 0) {
            int ny = top + ch + (v.cycleTo >= 0 ? 36 : 14) + 6;
            ny += paragraph(g, v.lostNote, SMALL_B, GHOST, x + noteW, ny, noteW);
            paragraph(g, v.lostHint, SMALL, INK, x + noteW, ny, noteW);
        }
        return new Dimension(w, h);
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
        int nw = 2 * r + 12, lv = boxy ? Math.max(76, 2 * ry + 30) : 70;
        final int extra = labelExtra(inorder);   // כמה מצביעים על אותו צומת — כל אחד בשורה משלו
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
            drawLabels(g, c, p[0], p[1] - ry - 4);
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
        if (nest > 0) return;   // בתוך מבנה מוקטן הקואורדינטות אחרות — בלי ריחוף
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

    /** אובייקט ששמור במשתנה ואינו בתוך מבנה: כותרת (שם המשתנה + סוג) וכל המאפיינים */
    private Dimension drawObjView(Graphics2D g, ObjView v, int x, int y) {
        Dimension t = fieldsTable(null, v.obj, 0, 0, null);
        int w = Math.max(t.width + 24, titleWidth(v.title, v.obj.type)), h = TITLE_H + t.height + 20;
        if (g == null) return new Dimension(w, h);
        boolean acc = accessed(v.obj);
        drawTitle(g, v.title, v.obj.type, x, y, v.focus || acc);
        g.setColor(acc ? color(step.access.mark).brighter() : new Color(0xF6F8FC));
        g.fillRoundRect(x, y + TITLE_H, w, t.height + 16, 10, 10);
        g.setColor(acc ? darker(color(step.access.mark)) : BOX_LINE);
        g.setStroke(acc ? THICK : THIN);
        g.drawRoundRect(x, y + TITLE_H, w, t.height + 16, 10, 10);
        fieldsTable(g, v.obj, x + 12, y + TITLE_H + 8, step.access);
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

    /** מקום נוסף מעל התאים כשיש כמה מצביעים על אותו תא: כל שם בשורה משלו */
    private static int labelExtra(List<? extends Cell> cells) {
        int most = 0;
        for (Cell c : cells) most = Math.max(most, c.labels.size());
        return Math.max(0, most - 1) * LABEL_STEP;
    }

    /** שמות משתנים מעל תא: כל שם בשורה משלו (הראשון הכי קרוב), וחץ קטן אל התא */
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

    private void drawLabels(Graphics2D g, Cell c, int cx, int bottomY) {
        if (!c.labels.isEmpty()) {
            labelStack(g, c.labels, cx, bottomY);
        } else if (c.current) {
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

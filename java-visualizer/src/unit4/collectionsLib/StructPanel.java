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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import unit4.collectionsLib.Model.Cell;
import unit4.collectionsLib.Model.ChainView;
import unit4.collectionsLib.Model.LinearView;
import unit4.collectionsLib.Model.ArrayView;
import unit4.collectionsLib.Model.MatrixView;
import unit4.collectionsLib.Model.Mark;
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

    private static final int PAD = 20, GAP = 36, TITLE_H = 30, ARC = 8;
    private static final Stroke THIN = new BasicStroke(1.3f);
    private static final Stroke THICK = new BasicStroke(3f);
    private static final Stroke DASHED = new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
            new float[] { 6f, 4f }, 0f);

    private Step step;
    /** הגדלה: 0 = התאמה אוטומטית לרוחב (רק הקטנה, עד 55%); אחרת פי כמה */
    private double manualZoom = 0, zoom = 1;
    private Runnable zoomListener;

    StructPanel() {
        setBackground(Color.WHITE);
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
        layoutAll(g);
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
        // מערכים: זה ליד זה כל עוד יש מקום ברוחב החלון
        int avail = viewportWidth() > 200 ? viewportWidth() - PAD : 900;
        int ax = PAD, arrRow = 0;
        for (View v : step.views) {
            if (!(v instanceof ArrayView || v instanceof MatrixView)) continue;
            Dimension d = measure(v);
            if (ax > PAD && ax + d.width > avail) { y += arrRow + GAP / 2; ax = PAD; arrRow = 0; }
            if (g != null) {
                if (v instanceof ArrayView) drawArray(g, (ArrayView) v, ax, y);
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
        return new Dimension(maxX + PAD, y + PAD);
    }

    /** המבנה שהצעד נגע בו — כדי לגלול אליו אם הוא מחוץ לחלון */
    private Rectangle focusRect;
    private boolean scrollPending;

    private void noteFocus(View v, int x, int y, Dimension d) {
        if (v.focus && focusRect == null) focusRect = new Rectangle(x, y, d.width, d.height);
    }

    private Dimension measure(View v) {
        return v instanceof ArrayView ? drawArray(null, (ArrayView) v, 0, 0) : drawMatrix(null, (MatrixView) v, 0, 0);
    }

    // ------------------------------------------------------------ מערך חד-ממדי

    private Dimension drawArray(Graphics2D g, ArrayView v, int x, int y) {
        int n = v.items.size();
        int cw = Math.max(44, maxWidth(v.items, null) + 22), ch = 40, labelsH = 22;
        String type = "מערך, אורך " + n + (v.truncated ? "+" : "");
        int top = y + TITLE_H + labelsH;
        int w = Math.max(16 + Math.max(n, 1) * cw + 20, titleWidth(v.title, type));
        int h = TITLE_H + labelsH + ch + 22;
        if (g == null) return new Dimension(w, h);

        drawTitle(g, v.title, type, x, y, v.focus || !v.changed.isEmpty());
        if (n == 0) {
            box(g, x + 8, top, cw, ch, BOX, null, true);
            g.setColor(MUTED);
            center(g, "ריק", SMALL_B, x + 8 + cw / 2, top + ch / 2);
        }
        for (int i = 0; i < n; i++) {
            int bx = x + 8 + i * cw;
            box(g, bx, top, cw, ch, v.changed.contains(i) ? CHANGED_C : BOX, v.items.get(i), false);
            g.setColor(MUTED);
            center(g, String.valueOf(i), SMALL, bx + cw / 2, top + ch + 11);
            List<String> labels = v.indexLabels.get(i);
            if (labels != null) indexLabel(g, String.join(",", labels), bx + cw / 2, top - 4);
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
        for (List<String> r : v.rows) {
            cols = Math.max(cols, r.size());
            all.addAll(r);
        }
        int cw = Math.max(44, maxWidth(all, null) + 20), ch = 36;
        int labelW = 0;
        for (List<String> l : v.rowLabels.values()) labelW = Math.max(labelW, textWidth(String.join(",", l), SMALL_B) + 16);
        int left = x + 8 + labelW + 22, top = y + TITLE_H + 22 + (v.colLabels.isEmpty() ? 0 : 18);
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
                box(g, left + c * cw, top + r * ch, cw, ch, v.changed.contains(r + "," + c) ? CHANGED_C : BOX, row.get(c), false);
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
        int cw = Math.max(76, maxWidth(v.items, v.ghost) + 26), ch = 36, ghostH = v.ghost != null ? 50 : 14;
        int w = Math.max(cw + 84, titleWidth(v.title, "מחסנית")), h = TITLE_H + ghostH + rows * ch + 14;
        if (g == null) return new Dimension(w, h);

        drawTitle(g, v.title, "מחסנית", x, y, v.focus);
        int cx = x + 8, top = y + TITLE_H + ghostH;
        if (v.ghost != null) {
            ghostBox(g, cx, y + TITLE_H + 4, cw, ch, v.ghost);
            g.setFont(SMALL_B);
            g.drawString("pop", cx + cw + 10, y + TITLE_H + 4 + ch / 2 + 5);
        }
        for (int i = 0; i < n; i++) {
            Color c = i == v.markIndex ? color(v.mark) : BOX;
            box(g, cx, top + i * ch, cw, ch, c, v.items.get(i), false);
        }
        g.setColor(LINE);
        g.setStroke(THICK);
        int bottom = top + rows * ch + 5;
        g.drawPolyline(new int[] { cx - 5, cx - 5, cx + cw + 5, cx + cw + 5 },
                new int[] { top - 8, bottom, bottom, top - 8 }, 4);
        if (n == 0) {
            g.setColor(MUTED);
            center(g, "ריקה", SMALL_B, cx + cw / 2, top + ch / 2);
        } else {
            arrow(g, cx + cw + 42, top + ch / 2, cx + cw + 10, top + ch / 2, LINE);
            g.setFont(SMALL_B);
            g.drawString("top", cx + cw + 46, top + ch / 2 + 5);
        }
        return new Dimension(w, h);
    }

    // ------------------------------------------------------------ תור

    private Dimension drawQueue(Graphics2D g, LinearView v, int x, int y) {
        int n = v.items.size(), rows = Math.max(n, 1);
        int cw = Math.max(56, maxWidth(v.items, v.ghost) + 24), ch = 40;
        int left = x + cw + 40;
        int w = Math.max(left - x + rows * cw + 30, titleWidth(v.title, "תור")), h = TITLE_H + ch + 40;
        if (g == null) return new Dimension(w, h);

        drawTitle(g, v.title, "תור", x, y, v.focus);
        int top = y + TITLE_H + 6;
        if (v.ghost != null) {
            ghostBox(g, x + 8, top, cw, ch, v.ghost);
            center(g, "remove", SMALL_B, x + 8 + cw / 2, top + ch + 16);
        }
        for (int i = 0; i < n; i++) {
            Color c = i == v.markIndex ? color(v.mark) : BOX;
            box(g, left + i * cw, top, cw, ch, c, v.items.get(i), false);
        }
        g.setColor(LINE);
        g.setStroke(THICK);
        g.drawLine(left - 8, top - 5, left + rows * cw + 8, top - 5);
        g.drawLine(left - 8, top + ch + 5, left + rows * cw + 8, top + ch + 5);
        if (n == 0) {
            g.setColor(MUTED);
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
        for (Cell c : v.cells) values.add(c.value);
        int n = v.cells.size();
        int vw = Math.max(48, maxWidth(values, null) + 22), pw = 20, ch = 38, ag = 38;
        int stepW = vw + pw + ag;
        String tail = v.cycleTo >= 0 ? "" : v.joinText != null ? v.joinText + "  (ממשיך ברשימה אחרת)"
                : v.truncated ? "..." : "null";
        int top = y + TITLE_H + 24;
        int w = Math.max(16 + n * stepW + textWidth(tail, SMALL_B) + 10, titleWidth(v.title, "רשימה מקושרת"));
        int h = TITLE_H + 24 + ch + (v.cycleTo >= 0 ? 36 : 14);
        if (g == null) return new Dimension(w, h);

        drawTitle(g, v.title, "רשימה מקושרת", x, y, v.focus);
        for (int i = 0; i < n; i++) {
            Cell c = v.cells.get(i);
            int bx = x + 8 + i * stepW;
            box(g, bx, top, vw, ch, color(c.mark), c.value, false);
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
            g.setColor(v.joinText != null ? CURRENT : MUTED);
            g.setFont(SMALL_B);
            g.drawString(tail, lastX + ag + 4, midY + 5);
        }
        return new Dimension(w, h);
    }

    // ------------------------------------------------------------ עץ

    private Dimension drawTree(Graphics2D g, TreeView v, int x, int y) {
        final List<TreeCell> inorder = new ArrayList<TreeCell>();
        final Map<TreeCell, Integer> depth = new IdentityHashMap<TreeCell, Integer>();
        collect(v.root, 0, inorder, depth);
        int maxDepth = 0, maxW = 0;
        for (TreeCell c : inorder) {
            maxDepth = Math.max(maxDepth, depth.get(c));
            maxW = Math.max(maxW, textWidth(c.value, VALUE));
        }
        int r = Math.max(19, maxW / 2 + 9), nw = 2 * r + 12, lv = 70;
        int w = Math.max(inorder.size() * nw + 16, titleWidth(v.title, "עץ בינארי"));
        int h = TITLE_H + 22 + (maxDepth + 1) * lv;
        if (g == null) return new Dimension(w, h);

        drawTitle(g, v.title, "עץ בינארי", x, y, v.focus);
        Map<TreeCell, int[]> pos = new IdentityHashMap<TreeCell, int[]>();
        for (int i = 0; i < inorder.size(); i++) {
            TreeCell c = inorder.get(i);
            pos.put(c, new int[] { x + 8 + i * nw + nw / 2, y + TITLE_H + 22 + depth.get(c) * lv + r });
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
            g.setColor(color(c.mark));
            g.fillOval(p[0] - r, p[1] - r, 2 * r, 2 * r);
            g.setColor(c.current ? CURRENT : c.mark == Mark.NONE ? BOX_LINE : darker(color(c.mark)));
            g.setStroke(c.shared ? DASHED : c.current ? THICK : THIN);
            g.drawOval(p[0] - r, p[1] - r, 2 * r, 2 * r);
            g.setColor(INK);
            center(g, c.value, VALUE, p[0], p[1]);
            drawLabels(g, c, p[0], p[1] - r - 4);
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

    // ------------------------------------------------------------ עזרי ציור

    private void drawLabels(Graphics2D g, Cell c, int cx, int bottomY) {
        if (!c.labels.isEmpty()) {
            StringBuilder b = new StringBuilder();
            for (String l : c.labels) b.append(b.length() == 0 ? "" : ", ").append(l);
            g.setColor(LABEL);
            g.setFont(SMALL_B);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(b.toString(), cx - fm.stringWidth(b.toString()) / 2, bottomY - 8);
            // חץ קטן מהשם אל התא — כדי שיהיה ברור לאיזה תא / צומת המשתנה מצביע
            g.fillPolygon(new int[] { cx - 4, cx + 4, cx }, new int[] { bottomY - 6, bottomY - 6, bottomY }, 3);
        } else if (c.current) {
            g.setColor(CURRENT);
            g.fillPolygon(new Polygon(new int[] { cx - 7, cx + 7, cx }, new int[] { bottomY - 12, bottomY - 12, bottomY - 2 }, 3));
        }
    }

    /** כותרת מבנה: שם המשתנה, ולידו "תגית" אפורה עם סוג המבנה */
    private void drawTitle(Graphics2D g, String name, String type, int x, int y, boolean focus) {
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
        g.setColor(c);
        g.setStroke(new BasicStroke(2f));
        g.drawLine(x1, y1, x2, y2);
        double a = Math.atan2(y2 - y1, x2 - x1);
        int len = 9;
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

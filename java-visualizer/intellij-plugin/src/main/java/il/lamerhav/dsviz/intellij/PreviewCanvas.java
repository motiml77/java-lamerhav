package il.lamerhav.dsviz.intellij;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import il.lamerhav.dsviz.Scaffold;

/**
 * התרשים הקטן בחלונות "הוסף מבנה נתונים" ו"תרגיל חדש" — איך ייראה המבנה (גם מבנה בתוך מבנה: מחסנית של תורים, עץ של תורים...).
 * העתקה ל-Swing של NestedPreview (SWT) מהתוסף של Eclipse — אותם ציורים, בצבעים שמתאימים גם לערכת נושא כהה.
 */
final class PreviewCanvas extends JComponent {
    private static final int CW = 28, CH = 22, PAD = 6, ARROW = 12, QA = 10, OA = 18;

    private static final Color BOX_FILL = new JBColor(new Color(0xE8F0FE), new Color(0x2B3A55));
    private static final Color BOX_LINE = new JBColor(new Color(0x8FA1C4), new Color(0x6F82A8));
    private static final Color CELL_FILL = new JBColor(Color.WHITE, new Color(0x1E2433));
    private static final Color INK = new JBColor(new Color(0x1D2433), new Color(0xE6EAF2));
    private static final Color MUTED = new JBColor(new Color(0x6B7485), new Color(0x9AA3B5));
    private static final Color ACCENT = new JBColor(new Color(0x3B4A63), new Color(0xA9B8D8));

    private String outer = "stack", inner = "stack";
    private int cw = CW;
    private List<List<String>> groups = new ArrayList<List<String>>();
    private Graphics2D g;

    PreviewCanvas() {
        setOpaque(false);
    }

    void set(Scaffold.Preview p) {
        set(p.outer, p.inner, p.groups);
    }

    void set(String outer, String inner, List<List<String>> groups) {
        this.outer = outer;
        this.inner = inner;
        this.groups = groups == null ? new ArrayList<List<String>>() : groups;
        int longest = 0;
        for (List<String> gr : this.groups) if (gr != null) for (String x : gr) longest = Math.max(longest, Math.min(x.length(), 9));
        this.cw = Math.max(CW, longest * 7 + 12);
        revalidate();
        repaint();
    }

    private Point content() {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D m = img.createGraphics();
        m.setFont(font());
        g = m;
        try {
            return paint(false, 0, 0);
        } finally {
            m.dispose();
            g = null;
        }
    }

    private Font font() {
        return UIUtil.getLabelFont().deriveFont(Font.PLAIN, 11f);
    }

    /** גודל קבוע (רוחב 380, גובה 150) — התרשים מוקטן כדי להיכנס, והחלון לא "קופץ" כשמחליפים מבנה */
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(JBUI.scale(380), JBUI.scale(150));
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Point c = content();
        Graphics2D g2 = (Graphics2D) graphics.create();
        try {
            int w = getWidth(), h = getHeight() - 4;
            double s = Math.min(1.0, Math.min(w / (double) Math.max(1, c.x), h / (double) Math.max(1, c.y)));
            AffineTransform t = g2.getTransform();
            t.translate(Math.max(0, (int) ((w - c.x * s) / 2)), 2);
            t.scale(s, s);
            g2.setTransform(t);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(font());
            g = g2;
            paint(true, 0, 0);
        } finally {
            g = null;
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ ציור (draw=false — רק מודדים)

    private void text(String s, int x, int y, Color c) {
        g.setColor(c);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, x, y + fm.getAscent());
    }

    private Point paint(boolean draw, int x, int y) {
        int n = groups.size();
        if (outer.equals("card")) return drawCard(draw, x, y);
        if (n == 0) {
            if (draw) text("ריק — אין איברים", x + 6, y + 2, MUTED);
            return new Point(110, 20);
        }
        switch (outer) {
            case "matrix": return drawMatrix(draw, x, y);
            case "stack": return drawStack(draw, x, y);
            case "queue": return drawRow(draw, x, y, false, true);
            case "list": return drawRow(draw, x, y, true, false);
            case "tree": return drawTree(draw, x, y);
            default: return drawRow(draw, x, y, false, false);
        }
    }

    private Point innerSize(List<String> gr) {
        int k = gr == null ? 0 : gr.size();
        if (inner.equals("cell")) return new Point(cw, CH);
        if (inner.equals("stack")) return new Point(cw + 2 * PAD, Math.max(k, 1) * CH + 2 * PAD);
        if (inner.equals("queue")) return new Point(Math.max(k, 1) * cw + 2 * PAD + 2 * QA, CH + 2 * PAD + 4);
        return new Point(Math.max(k, 1) * cw + Math.max(0, k - 1) * ARROW + 2 * PAD + ARROW + 22, CH + 2 * PAD);
    }

    private void drawInner(int x, int y, List<String> gr, int w, int h) {
        if (inner.equals("cell")) {
            cell(x, y, gr == null || gr.isEmpty() ? "" : gr.get(0));
            return;
        }
        g.setColor(BOX_FILL);
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(BOX_LINE);
        g.setStroke(new BasicStroke(1));
        g.drawRoundRect(x, y, w, h, 8, 8);
        int k = gr == null ? 0 : gr.size();
        if (k == 0) text(gr == null ? "null" : "ריק", x + PAD + 2, y + h / 2 - 7, MUTED);
        if (inner.equals("stack")) {
            int cx = x + PAD;
            for (int i = 0; i < k; i++) cell(cx, y + PAD + i * CH, gr.get(k - 1 - i));
            if (k > 0) text("▼", x + w - 11, y + PAD + 3, ACCENT);
        } else if (inner.equals("queue")) {
            int cx0 = x + PAD + QA, cy = y + PAD + 2, len = Math.max(k, 1) * cw;
            for (int i = 0; i < k; i++) cell(cx0 + i * cw, cy, gr.get(i));
            queueTube(cx0, cy, len, CH, x + 2, cx0 + len + QA - 2, 2);
        } else {
            int cx = x + PAD;
            for (int i = 0; i < k; i++) {
                cell(cx, y + PAD, gr.get(i));
                text(i < k - 1 ? "→" : "→ null", cx + cw + 1, y + PAD + 4, ACCENT);
                cx += cw + (i < k - 1 ? ARROW : 0);
            }
        }
    }

    private void cell(int x, int y, String s) {
        g.setColor(CELL_FILL);
        g.fillRect(x, y, cw, CH);
        g.setColor(BOX_LINE);
        g.setStroke(new BasicStroke(1));
        g.drawRect(x, y, cw, CH);
        int max = cw > CW ? 9 : 4;
        String t = s.length() > max ? s.substring(0, max - 1) + "…" : s;
        FontMetrics fm = g.getFontMetrics();
        text(t, x + (cw - fm.stringWidth(t)) / 2, y + (CH - fm.getHeight()) / 2, INK);
    }

    private Point drawMatrix(boolean draw, int x, int y) {
        int rows = groups.size(), cols = 0;
        for (List<String> r : groups) cols = Math.max(cols, r == null ? 0 : r.size());
        int left = 26, top = 16;
        if (draw) {
            for (int j = 0; j < cols; j++) text("[" + j + "]", x + left + j * cw + cw / 2 - 8, y, MUTED);
            for (int i = 0; i < rows; i++) {
                text("[" + i + "]", x + 2, y + top + i * CH + 4, MUTED);
                List<String> r = groups.get(i);
                for (int j = 0; r != null && j < r.size(); j++) cell(x + left + j * cw, y + top + i * CH, r.get(j));
            }
        }
        return new Point(left + cols * cw + 8, top + rows * CH + 6);
    }

    private Point drawCard(boolean draw, int x, int y) {
        boolean student = inner.equals("Student");
        String[] fields = student
                ? new String[] { "name: \"Noa\"", "classRoom: \"11B\"", "age: 17", "grades: [90, 85, 77]" }
                : new String[] { "manufacturer: \"Mazda\"", "model: \"3\"", "year: 2019", "km, fuel..." };
        int w = 170, h = 26 + fields.length * 17 + 8;
        if (draw) {
            g.setColor(BOX_FILL);
            g.fillRoundRect(x + 4, y + 2, w, h, 10, 10);
            g.setColor(BOX_LINE);
            g.drawRoundRect(x + 4, y + 2, w, h, 10, 10);
            text(student ? "Student" : "Car", x + 14, y + 8, ACCENT);
            for (int i = 0; i < fields.length; i++) text(fields[i], x + 14, y + 28 + i * 17, INK);
        }
        return new Point(w + 10, h + 6);
    }

    private Point drawStack(boolean draw, int x, int y) {
        int n = groups.size(), w = 0, h = 0;
        for (List<String> gr : groups) { Point s = innerSize(gr); w = Math.max(w, s.x); h += s.y + 4; }
        int bx = x + 6, by = y + 4;
        if (draw) {
            int cy = by + 4;
            for (int i = n - 1; i >= 0; i--) {
                Point s = innerSize(groups.get(i));
                drawInner(bx + 4, cy, groups.get(i), w, s.y);
                cy += s.y + 4;
            }
            g.setColor(ACCENT);
            g.setStroke(new BasicStroke(3));
            g.drawPolyline(new int[] { bx, bx, bx + w + 8, bx + w + 8 }, new int[] { by, by + h + 4, by + h + 4, by }, 4);
            g.setStroke(new BasicStroke(1));
            Point first = innerSize(groups.get(n - 1));
            text("← top", bx + w + 14, by + 4 + first.y / 2 - 7, ACCENT);
        }
        return new Point(w + 70, h + 14);
    }

    private Point drawRow(boolean draw, int x, int y, boolean arrows, boolean queue) {
        int n = groups.size(), h = 0, w = 8, gap = arrows ? 22 : 6;
        for (List<String> gr : groups) { Point s = innerSize(gr); w += s.x + gap; h = Math.max(h, s.y); }
        int top = y + (queue ? 6 : 4), cx = x + 6 + (queue ? OA : 0);
        if (draw) {
            if (queue) queueTube(x + OA + 4, top - 2, w - 8, h + 6, x + 2, x + w + 2 * OA - 2, 3);
            for (int i = 0; i < n; i++) {
                Point s = innerSize(groups.get(i));
                drawInner(cx, top + (h - s.y) / 2 + (queue ? 1 : 0), groups.get(i), s.x, s.y);
                if (arrows) text(i < n - 1 ? "→" : "→ null", cx + s.x + 3, top + h / 2 - 7, ACCENT);
                if (!arrows && !queue) text("[" + i + "]", cx + s.x / 2 - 8, top + h + 4, MUTED);
                cx += s.x + gap;
            }
            if (queue) {
                text("head", x + OA + 6, top + h + 8, ACCENT);
                if (n > 1) text("tail", cx - gap - 22, top + h + 8, ACCENT);
            }
        }
        return new Point(w + (arrows ? 28 : 0) + (queue ? 2 * OA : 0), h + (queue ? 30 : arrows ? 12 : 26));
    }

    private void queueTube(int x, int y, int len, int h, int outX, int inX, int width) {
        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(width));
        g.drawLine(x - 3, y - 2, x + len + 3, y - 2);
        g.drawLine(x - 3, y + h + 2, x + len + 3, y + h + 2);
        g.setStroke(new BasicStroke(width > 2 ? 2 : 1));
        int mid = y + h / 2;
        arrowLeft(x - 2, outX, mid);
        arrowLeft(inX, x + len + 2, mid);
        g.setStroke(new BasicStroke(1));
    }

    private void arrowLeft(int fromX, int toX, int y) {
        g.drawLine(fromX, y, toX + 4, y);
        g.fillPolygon(new int[] { toX, toX + 6, toX + 6 }, new int[] { y, y - 4, y + 4 }, 3);
    }

    private Point drawTree(boolean draw, int x, int y) {
        int n = groups.size(), levelH = 0, cellW = 0;
        for (List<String> gr : groups) if (gr != null) { Point s = innerSize(gr); cellW = Math.max(cellW, s.x); levelH = Math.max(levelH, s.y); }
        if (cellW == 0) return new Point(40, 20);
        int[] depth = new int[n], order = new int[n];
        boolean[] present = new boolean[n];
        markTree(0, n, present);
        int[] counter = { 0 }, maxDepth = { 0 };
        number(0, 0, n, present, depth, order, counter, maxDepth);
        int stepX = cellW + 10, stepY = levelH + 22;
        int w = counter[0] * stepX + 8, h = (maxDepth[0] + 1) * stepY;
        if (draw) {
            g.setColor(ACCENT);
            g.setStroke(new BasicStroke(2));
            for (int i = 0; i < n; i++) {
                if (!present[i]) continue;
                for (int c = 2 * i + 1; c <= 2 * i + 2; c++) {
                    if (c >= n || !present[c]) continue;
                    g.drawLine(x + 4 + order[i] * stepX + cellW / 2, y + depth[i] * stepY + levelH,
                            x + 4 + order[c] * stepX + cellW / 2, y + depth[c] * stepY);
                }
            }
            g.setStroke(new BasicStroke(1));
            for (int i = 0; i < n; i++) {
                if (!present[i]) continue;
                Point s = innerSize(groups.get(i));
                drawInner(x + 4 + order[i] * stepX + (cellW - s.x) / 2, y + depth[i] * stepY + (levelH - s.y) / 2, groups.get(i), s.x, s.y);
            }
            text("root", x + 4 + order[0] * stepX + cellW + 2, y, MUTED);
        }
        return new Point(w + 30, h);
    }

    private void markTree(int i, int n, boolean[] present) {
        if (i >= n || groups.get(i) == null) return;
        present[i] = true;
        markTree(2 * i + 1, n, present);
        markTree(2 * i + 2, n, present);
    }

    private void number(int i, int d, int n, boolean[] present, int[] depth, int[] order, int[] counter, int[] maxDepth) {
        if (i >= n || !present[i]) return;
        number(2 * i + 1, d + 1, n, present, depth, order, counter, maxDepth);
        depth[i] = d;
        order[i] = counter[0]++;
        maxDepth[0] = Math.max(maxDepth[0], d);
        number(2 * i + 2, d + 1, n, present, depth, order, counter, maxDepth);
    }
}

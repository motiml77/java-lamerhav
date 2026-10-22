package il.lamerhav.dsviz.cs;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import il.lamerhav.dsviz.Scaffold;
import unit4.collectionsLib.Theme;

/**
 * תרשים קטן: איך ייראה המבנה — מבנה רגיל (בכל תא ערך), מבנה בתוך מבנה (מחסנית של תורים, רשימה של מחסניות, עץ של תורים...),
 * מערך דו-ממדי, או תלמיד / מכונית. אותו תרשים כמו בחלון של Eclipse (NestedPreview) ושל VS Code, ב-Swing.
 * תור מצויר כצינור פתוח: יוצאים מהראש (חץ החוצה משמאל) ונכנסים בזנב (חץ פנימה מימין) — כדי שלא ייראה כמו מערך.
 */
final class NestedDiagram extends JComponent {
    private static final int CW = 28, CH = 22, PAD = 6, ARROW = 12, QA = 10, OA = 18;
    private static final Color BOX_FILL = new Color(0xE8F0FE), BOX_LINE = new Color(0x8FA1C4), CELL = Color.WHITE,
            INK = new Color(0x1D2433), MUTED = new Color(0x6B7485), ACCENT = new Color(0x3B4A63);

    private String outer = "stack", inner = "stack";
    private List<List<String>> groups = new ArrayList<List<String>>();
    private int cw = CW;
    private int maxWidth = 330;
    private Graphics2D g;   // בזמן ציור; null בזמן מדידה

    void set(String outer, String inner, List<List<String>> groups) {
        this.outer = outer;
        this.inner = inner;
        this.groups = groups == null ? new ArrayList<List<String>>() : groups;
        int longest = 0;
        for (List<String> gr : this.groups) if (gr != null) for (String x : gr) longest = Math.max(longest, Math.min(x.length(), 9));
        cw = Math.max(CW, longest * 7 + 12);
        Dimension d = content();
        setPreferredSize(new Dimension(Math.min(d.width, maxWidth), (int) Math.ceil(d.height * Math.min(1.0, maxWidth / (double) Math.max(1, d.width))) + 4));
        revalidate();
        repaint();
    }

    void set(Scaffold.Preview p) {
        set(p.outer, p.inner, p.groups);
    }

    void setMaxWidth(int w) {
        maxWidth = w;
    }

    private Dimension content() {
        g = null;
        return layout(0, 0);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g2 = (Graphics2D) g0.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(Theme.small != null ? Theme.small : getFont());
        Dimension c = content();
        double s = Math.min(1.0, getWidth() / (double) Math.max(1, c.width));
        AffineTransform t = new AffineTransform();
        t.translate(Math.max(0, (int) ((getWidth() - c.width * s) / 2)), 2);
        t.scale(s, s);
        g2.transform(t);
        // הציור תמיד משמאל לימין (המבנים הם נתונים, לא טקסט) — בלי תלות בכיוון החלון
        this.g = g2;
        layout(0, 0);
        this.g = null;
        g2.dispose();
    }

    // ------------------------------------------------------------ ציור (g == null — רק מודד)

    private Dimension layout(int x, int y) {
        if (outer.equals("card")) return drawCard(x, y);
        if (groups.isEmpty()) {
            if (g != null) text("ריק — אין איברים", x + 6, y + 2, MUTED);
            return new Dimension(110, 20);
        }
        switch (outer) {
            case "matrix": return drawMatrix(x, y);
            case "stack": return drawStack(x, y);
            case "queue": return drawRow(x, y, false, true);
            case "list": return drawRow(x, y, true, false);
            case "tree": return drawTree(x, y);
            default: return drawRow(x, y, false, false);   // מערך
        }
    }

    private Dimension innerSize(List<String> list) {
        int k = list == null ? 0 : list.size();
        if (inner.equals("cell")) return new Dimension(cw, CH);
        if (inner.equals("stack")) return new Dimension(cw + 2 * PAD, Math.max(k, 1) * CH + 2 * PAD);
        if (inner.equals("queue")) return new Dimension(Math.max(k, 1) * cw + 2 * PAD + 2 * QA, CH + 2 * PAD + 4);
        return new Dimension(Math.max(k, 1) * cw + Math.max(0, k - 1) * ARROW + 2 * PAD + ARROW + 22, CH + 2 * PAD);   // רשימה: חיצים ו-null
    }

    private void text(String s, int x, int y, Color c) {
        g.setColor(c);
        g.drawString(s, x, y + g.getFontMetrics().getAscent());
    }

    private void drawInner(int x, int y, List<String> list, int w, int h) {
        if (g == null) return;
        if (inner.equals("cell")) {   // מבנה רגיל: בכל תא ערך אחד
            cell(x, y, list == null || list.isEmpty() ? "" : list.get(0));
            return;
        }
        g.setColor(BOX_FILL);
        g.fillRoundRect(x, y, w, h, 8, 8);
        g.setColor(BOX_LINE);
        g.setStroke(new BasicStroke(1));
        g.drawRoundRect(x, y, w, h, 8, 8);
        int k = list == null ? 0 : list.size();
        if (k == 0) text(list == null ? "null" : "ריק", x + PAD + 2, y + h / 2 - 7, MUTED);
        if (inner.equals("stack")) {   // הראש למעלה: האיבר האחרון שנכנס
            for (int i = 0; i < k; i++) cell(x + PAD, y + PAD + i * CH, list.get(k - 1 - i));
            if (k > 0) text("▼", x + w - 11, y + PAD + 3, ACCENT);
        } else if (inner.equals("queue")) {   // תור: צינור פתוח עם חיצים
            int cx0 = x + PAD + QA, cy = y + PAD + 2, len = Math.max(k, 1) * cw;
            for (int i = 0; i < k; i++) cell(cx0 + i * cw, cy, list.get(i));
            queueTube(cx0, cy, len, CH, x + 2, cx0 + len + QA - 2, 2f);
        } else {
            int cx = x + PAD;
            for (int i = 0; i < k; i++) {
                cell(cx, y + PAD, list.get(i));
                text(i < k - 1 ? "→" : "→ null", cx + cw + 1, y + PAD + 4, ACCENT);
                cx += cw + (i < k - 1 ? ARROW : 0);
            }
        }
    }

    private void cell(int x, int y, String s) {
        g.setColor(CELL);
        g.fillRect(x, y, cw, CH);
        g.setColor(BOX_LINE);
        g.setStroke(new BasicStroke(1));
        g.drawRect(x, y, cw, CH);
        int max = cw > CW ? 9 : 4;
        String t = s.length() > max ? s.substring(0, max - 1) + "…" : s;
        FontMetrics fm = g.getFontMetrics();
        g.setColor(INK);
        g.drawString(t, x + (cw - fm.stringWidth(t)) / 2, y + (CH - fm.getHeight()) / 2 + fm.getAscent());
    }

    /** תור: קווים למעלה ולמטה, צדדים פתוחים; משמאל חץ החוצה (הראש), מימין חץ פנימה (הזנב) */
    private void queueTube(int x, int y, int len, int h, int outX, int inX, float width) {
        g.setColor(ACCENT);
        g.setStroke(new BasicStroke(width));
        g.drawLine(x - 3, y - 2, x + len + 3, y - 2);
        g.drawLine(x - 3, y + h + 2, x + len + 3, y + h + 2);
        g.setStroke(new BasicStroke(width > 2 ? 2f : 1.5f));
        int mid = y + h / 2;
        arrowLeft(x - 2, outX, mid);
        arrowLeft(inX, x + len + 2, mid);
        g.setStroke(new BasicStroke(1));
    }

    private void arrowLeft(int fromX, int toX, int y) {
        g.drawLine(fromX, y, toX + 4, y);
        g.fillPolygon(new int[] { toX, toX + 6, toX + 6 }, new int[] { y, y - 4, y + 4 }, 3);
    }

    private Dimension drawStack(int x, int y) {
        int n = groups.size(), w = 0, h = 0;
        for (List<String> list : groups) { Dimension s = innerSize(list); w = Math.max(w, s.width); h += s.height + 4; }
        int bx = x + 6, by = y + 4;
        if (g != null) {
            int cy = by + 4;
            for (int i = n - 1; i >= 0; i--) {   // האחרון שנכנס — למעלה
                Dimension s = innerSize(groups.get(i));
                drawInner(bx + 4, cy, groups.get(i), w, s.height);
                cy += s.height + 4;
            }
            g.setColor(ACCENT);
            g.setStroke(new BasicStroke(3));
            g.drawPolyline(new int[] { bx, bx, bx + w + 8, bx + w + 8 }, new int[] { by, by + h + 4, by + h + 4, by }, 4);
            g.setStroke(new BasicStroke(1));
            Dimension first = innerSize(groups.get(n - 1));
            text("← top", bx + w + 14, by + 4 + first.height / 2 - 7, ACCENT);
        }
        return new Dimension(w + 70, h + 14);
    }

    /** תור / רשימה מקושרת / מערך: שורה של תאים או של מבנים פנימיים */
    private Dimension drawRow(int x, int y, boolean arrows, boolean queue) {
        int n = groups.size(), h = 0, w = 8, gap = arrows ? 22 : 6;
        for (List<String> list : groups) { Dimension s = innerSize(list); w += s.width + gap; h = Math.max(h, s.height); }
        int top = y + (queue ? 6 : 4), cx = x + 6 + (queue ? OA : 0);
        if (g != null) {
            if (queue) queueTube(x + OA + 4, top - 2, w - 8, h + 6, x + 2, x + w + 2 * OA - 2, 3f);
            for (int i = 0; i < n; i++) {
                Dimension s = innerSize(groups.get(i));
                drawInner(cx, top + (h - s.height) / 2 + (queue ? 1 : 0), groups.get(i), s.width, s.height);
                if (arrows) text(i < n - 1 ? "→" : "→ null", cx + s.width + 3, top + h / 2 - 7, ACCENT);
                if (!arrows && !queue) text("[" + i + "]", cx + s.width / 2 - 8, top + h + 4, MUTED);
                cx += s.width + gap;
            }
            if (queue) {
                text("head", x + OA + 6, top + h + 8, ACCENT);
                if (n > 1) text("tail", cx - gap - 22, top + h + 8, ACCENT);
            }
        }
        return new Dimension(w + (arrows ? 28 : 0) + (queue ? 2 * OA : 0), h + (queue ? 30 : arrows ? 12 : 26));
    }

    /** מערך דו-ממדי: טבלה של תאים, עם מספרי השורות והעמודות */
    private Dimension drawMatrix(int x, int y) {
        int rows = groups.size(), cols = 0;
        for (List<String> r : groups) cols = Math.max(cols, r == null ? 0 : r.size());
        int left = 26, top = 16;
        if (g != null) {
            for (int j = 0; j < cols; j++) text("[" + j + "]", x + left + j * cw + cw / 2 - 8, y, MUTED);
            for (int i = 0; i < rows; i++) {
                text("[" + i + "]", x + 2, y + top + i * CH + 4, MUTED);
                List<String> r = groups.get(i);
                for (int j = 0; r != null && j < r.size(); j++) cell(x + left + j * cw, y + top + i * CH, r.get(j));
            }
        }
        return new Dimension(left + cols * cw + 8, top + rows * CH + 6);
    }

    /** תלמיד / מכונית בודד: כרטיס עם המאפיינים */
    private Dimension drawCard(int x, int y) {
        boolean student = inner.equals("Student");
        String[] fields = student
                ? new String[] { "name: \"Noa\"", "classRoom: \"11B\"", "age: 17", "grades: [90, 85, 77]" }
                : new String[] { "manufacturer: \"Mazda\"", "model: \"3\"", "year: 2019", "km, fuel..." };
        int w = 170, h = 26 + fields.length * 17 + 8;
        if (g != null) {
            g.setColor(BOX_FILL);
            g.fillRoundRect(x + 4, y + 2, w, h, 10, 10);
            g.setColor(BOX_LINE);
            g.drawRoundRect(x + 4, y + 2, w, h, 10, 10);
            text(student ? "Student" : "Car", x + 14, y + 8, ACCENT);
            for (int i = 0; i < fields.length; i++) text(fields[i], x + 14, y + 28 + i * 17, INK);
        }
        return new Dimension(w + 10, h + 6);
    }

    private Dimension drawTree(int x, int y) {
        int n = groups.size(), levelH = 0, cellW = 0;
        for (List<String> list : groups) if (list != null) { Dimension s = innerSize(list); cellW = Math.max(cellW, s.width); levelH = Math.max(levelH, s.height); }
        if (cellW == 0) return new Dimension(40, 20);
        int[] depth = new int[n], order = new int[n];
        boolean[] present = new boolean[n];
        markTree(0, n, present);
        int[] counter = { 0 }, maxDepth = { 0 };
        number(0, 0, n, present, depth, order, counter, maxDepth);
        int stepX = cellW + 10, stepY = levelH + 22;
        int w = counter[0] * stepX + 8, h = (maxDepth[0] + 1) * stepY;
        if (g != null) {
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
                Dimension s = innerSize(groups.get(i));
                drawInner(x + 4 + order[i] * stepX + (cellW - s.width) / 2, y + depth[i] * stepY + (levelH - s.height) / 2, groups.get(i), s.width, s.height);
            }
            text("root", x + 4 + order[0] * stepX + cellW + 2, y, MUTED);
        }
        return new Dimension(w + 30, h);
    }

    /** אילו צמתים קיימים: צומת שאבא שלו חסר (או קבוצה null) — לא קיים */
    private void markTree(int i, int n, boolean[] present) {
        if (i >= n || groups.get(i) == null) return;
        present[i] = true;
        markTree(2 * i + 1, n, present);
        markTree(2 * i + 2, n, present);
    }

    /** מיקום אופקי לפי סדר תוכי (inorder) ועומק */
    private void number(int i, int d, int n, boolean[] present, int[] depth, int[] order, int[] counter, int[] maxDepth) {
        if (i >= n || !present[i]) return;
        number(2 * i + 1, d + 1, n, present, depth, order, counter, maxDepth);
        depth[i] = d;
        order[i] = counter[0]++;
        maxDepth[0] = Math.max(maxDepth[0], d);
        number(2 * i + 2, d + 1, n, present, depth, order, counter, maxDepth);
    }
}

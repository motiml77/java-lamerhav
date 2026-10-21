package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;

/**
 * תרשים קטן בחלון "תרגיל חדש": איך ייראה מבנה בתוך מבנה (מחסנית של תורים, רשימה של מחסניות, עץ של תורים...).
 * המבנה החיצוני מצויר עם המבנים הפנימיים בתוך התאים שלו — כמו בחלון ההרצה. מתעדכן תוך כדי הקלדת הערכים.
 */
final class NestedPreview extends Canvas {
    private static final int CW = 28, CH = 22, PAD = 6, GAP = 12, ARROW = 12, TOP = 18;

    private String outer = "stack", inner = "stack";
    private List<List<String>> groups = new ArrayList<List<String>>();
    private final Color boxFill, boxLine, cellFill, ink, muted, accent;
    private final Font small;

    NestedPreview(Composite parent) {
        super(parent, SWT.DOUBLE_BUFFERED | SWT.LEFT_TO_RIGHT);
        boxFill = new Color(getDisplay(), 0xE8, 0xF0, 0xFE);
        boxLine = new Color(getDisplay(), 0x8F, 0xA1, 0xC4);
        cellFill = new Color(getDisplay(), 0xFF, 0xFF, 0xFF);
        ink = new Color(getDisplay(), 0x1D, 0x24, 0x33);
        muted = new Color(getDisplay(), 0x6B, 0x74, 0x85);
        accent = new Color(getDisplay(), 0x3B, 0x4A, 0x63);
        FontData fd = getFont().getFontData()[0];
        small = new Font(getDisplay(), fd.getName(), Math.max(7, fd.getHeight() - 1), SWT.NORMAL);
        addPaintListener(e -> paint(e.gc));
        addDisposeListener(e -> {
            for (Color c : new Color[] { boxFill, boxLine, cellFill, ink, muted, accent }) c.dispose();
            small.dispose();
        });
    }

    /** groups: הערכים של כל מבנה פנימי לפי הסדר שבו כתבו אותם (בעץ — לפי רמות; null = אין צומת) */
    void set(String outer, String inner, List<List<String>> groups) {
        this.outer = outer;
        this.inner = inner;
        this.groups = groups == null ? new ArrayList<List<String>>() : groups;
        redraw();
    }

    @Override
    public Point computeSize(int wHint, int hHint, boolean changed) {
        Point content = paint(null, 0, 0);
        int w = wHint == SWT.DEFAULT ? Math.min(content.x, 330) : wHint;
        double s = Math.min(1.0, w / (double) Math.max(1, content.x));
        return new Point(w, (int) Math.ceil(content.y * s) + 4);
    }

    // ------------------------------------------------------------ ציור

    private void paint(GC gc) {
        Point content = paint(null, 0, 0);
        int w = getClientArea().width;
        double s = Math.min(1.0, w / (double) Math.max(1, content.x));
        Transform t = new Transform(getDisplay());
        t.translate(Math.max(0, (int) ((w - content.x * s) / 2)), 2);
        t.scale((float) s, (float) s);
        gc.setTransform(t);
        gc.setAntialias(SWT.ON);
        paint(gc, 0, 0);
        gc.setTransform(null);
        t.dispose();
    }

    /** מצייר (gc == null — רק מודד) ומחזיר את הגודל */
    private Point paint(GC gc, int x, int y) {
        if (gc != null) gc.setFont(small);
        int n = groups.size();
        if (n == 0) return new Point(40, 20);
        switch (outer) {
            case "stack": return drawStack(gc, x, y);
            case "queue": return drawRow(gc, x, y, false, true);
            case "list": return drawRow(gc, x, y, true, false);
            case "tree": return drawTree(gc, x, y);
            default: return drawRow(gc, x, y, false, false);   // מערך
        }
    }

    private Point innerSize(List<String> g) {
        int k = g == null ? 0 : g.size();
        if (inner.equals("stack")) return new Point(CW + 2 * PAD, Math.max(k, 1) * CH + 2 * PAD);
        if (inner.equals("queue")) return new Point(Math.max(k, 1) * CW + 2 * PAD, CH + 2 * PAD);
        return new Point(Math.max(k, 1) * CW + Math.max(0, k - 1) * ARROW + 2 * PAD + ARROW + 22, CH + 2 * PAD);   // רשימה: חיצים ו-null
    }

    /** המבנה הפנימי בתוך מלבן; מחזיר את גודלו */
    private Point drawInner(GC gc, int x, int y, List<String> g, int w, int h) {
        if (gc != null) {
            gc.setBackground(boxFill);
            gc.fillRoundRectangle(x, y, w, h, 8, 8);
            gc.setForeground(boxLine);
            gc.setLineWidth(1);
            gc.drawRoundRectangle(x, y, w, h, 8, 8);
            int k = g == null ? 0 : g.size();
            if (k == 0) {
                gc.setForeground(muted);
                gc.drawString(g == null ? "null" : "ריק", x + PAD + 2, y + h / 2 - 7, true);
            }
            if (inner.equals("stack")) {   // הראש למעלה: האיבר האחרון שנכנס
                int cx = x + PAD;
                for (int i = 0; i < k; i++) cell(gc, cx, y + PAD + i * CH, g.get(k - 1 - i));
                if (k > 0) { gc.setForeground(accent); gc.drawString("▼", x + w - 11, y + PAD + 3, true); }
            } else if (inner.equals("queue")) {
                for (int i = 0; i < k; i++) cell(gc, x + PAD + i * CW, y + PAD, g.get(i));
            } else {
                int cx = x + PAD;
                for (int i = 0; i < k; i++) {
                    cell(gc, cx, y + PAD, g.get(i));
                    gc.setForeground(accent);
                    gc.drawString(i < k - 1 ? "→" : "→ null", cx + CW + 1, y + PAD + 4, true);
                    cx += CW + (i < k - 1 ? ARROW : 0);
                }
            }
        }
        return new Point(w, h);
    }

    private void cell(GC gc, int x, int y, String text) {
        gc.setBackground(cellFill);
        gc.fillRectangle(x, y, CW, CH);
        gc.setForeground(boxLine);
        gc.drawRectangle(x, y, CW, CH);
        gc.setForeground(ink);
        String t = text.length() > 4 ? text.substring(0, 3) + "…" : text;
        Point e = gc.textExtent(t);
        gc.drawString(t, x + (CW - e.x) / 2, y + (CH - e.y) / 2, true);
    }

    private Point drawStack(GC gc, int x, int y) {
        int n = groups.size(), w = 0, h = 0;
        for (List<String> g : groups) { Point s = innerSize(g); w = Math.max(w, s.x); h += s.y + 4; }
        int bx = x + 6, by = y + 4;
        if (gc != null) {
            int cy = by + 4;
            for (int i = n - 1; i >= 0; i--) {   // האחרון שנכנס — למעלה
                Point s = innerSize(groups.get(i));
                drawInner(gc, bx + 4, cy, groups.get(i), w, s.y);
                cy += s.y + 4;
            }
            gc.setForeground(accent);
            gc.setLineWidth(3);
            gc.drawPolyline(new int[] { bx, by, bx, by + h + 4, bx + w + 8, by + h + 4, bx + w + 8, by });
            gc.setLineWidth(1);
            Point first = innerSize(groups.get(n - 1));
            gc.drawString("← top", bx + w + 14, by + 4 + first.y / 2 - 7, true);
        }
        return new Point(w + 70, h + 14);
    }

    /** תור / רשימה מקושרת / מערך: שורה של מבנים פנימיים */
    private Point drawRow(GC gc, int x, int y, boolean arrows, boolean queue) {
        int n = groups.size(), h = 0, w = 8, gap = arrows ? 22 : 6;
        for (List<String> g : groups) { Point s = innerSize(g); w += s.x + gap; h = Math.max(h, s.y); }
        int top = y + (queue ? 6 : 4), cx = x + 6;
        if (gc != null) {
            if (queue) {
                gc.setForeground(accent);
                gc.setLineWidth(3);
                gc.drawLine(x + 2, top - 2, x + w - 2, top - 2);
                gc.drawLine(x + 2, top + h + 4, x + w - 2, top + h + 4);
                gc.setLineWidth(1);
            }
            for (int i = 0; i < n; i++) {
                Point s = innerSize(groups.get(i));
                drawInner(gc, cx, top + (h - s.y) / 2 + (queue ? 1 : 0), groups.get(i), s.x, s.y);
                gc.setForeground(accent);
                if (arrows) gc.drawString(i < n - 1 ? "→" : "→ null", cx + s.x + 3, top + h / 2 - 7, true);
                if (!arrows && !queue) {
                    gc.setForeground(muted);
                    gc.drawString("[" + i + "]", cx + s.x / 2 - 8, top + h + 4, true);
                }
                cx += s.x + gap;
            }
            if (queue) {
                gc.setForeground(accent);
                gc.drawString("head", x + 10, top + h + 8, true);
                if (n > 1) gc.drawString("tail", cx - gap - 22, top + h + 8, true);
            }
        }
        return new Point(w + (arrows ? 28 : 0), h + (queue ? 30 : arrows ? 12 : 26));
    }

    private Point drawTree(GC gc, int x, int y) {
        int n = groups.size(), levelH = 0, cellW = 0;
        for (List<String> g : groups) if (g != null) { Point s = innerSize(g); cellW = Math.max(cellW, s.x); levelH = Math.max(levelH, s.y); }
        if (cellW == 0) return new Point(40, 20);
        int[] depth = new int[n], order = new int[n];
        boolean[] present = new boolean[n];
        markTree(0, n, present);
        int[] counter = { 0 }, maxDepth = { 0 };
        number(0, 0, n, present, depth, order, counter, maxDepth);
        int stepX = cellW + 10, stepY = levelH + 22;
        int w = counter[0] * stepX + 8, h = (maxDepth[0] + 1) * stepY;
        if (gc != null) {
            gc.setForeground(accent);
            gc.setLineWidth(2);
            for (int i = 0; i < n; i++) {
                if (!present[i]) continue;
                for (int c = 2 * i + 1; c <= 2 * i + 2; c++) {
                    if (c >= n || !present[c]) continue;
                    gc.drawLine(x + 4 + order[i] * stepX + cellW / 2, y + depth[i] * stepY + levelH,
                            x + 4 + order[c] * stepX + cellW / 2, y + depth[c] * stepY);
                }
            }
            gc.setLineWidth(1);
            for (int i = 0; i < n; i++) {
                if (!present[i]) continue;
                Point s = innerSize(groups.get(i));
                drawInner(gc, x + 4 + order[i] * stepX + (cellW - s.x) / 2, y + depth[i] * stepY + (levelH - s.y) / 2, groups.get(i), s.x, s.y);
            }
            gc.setForeground(muted);
            gc.drawString("root", x + 4 + order[0] * stepX + cellW + 2, y, true);
        }
        return new Point(w + 30, h);
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

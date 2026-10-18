package unit4.collectionsLib;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;

import unit4.collectionsLib.Model.Cell;
import unit4.collectionsLib.Model.ChainView;
import unit4.collectionsLib.Model.LinearView;
import unit4.collectionsLib.Model.Mark;
import unit4.collectionsLib.Model.Step;
import unit4.collectionsLib.Model.TreeCell;
import unit4.collectionsLib.Model.TreeView;
import unit4.collectionsLib.Model.View;

/** מצייר את כל המבנים של צעד אחד */
final class StructPanel extends JPanel {
    static final Color BOX = new Color(0xDCE8FB);
    static final Color NEW_C = new Color(0xA8E6B0);
    static final Color READ_C = new Color(0xFFE680);
    static final Color CHANGED_C = new Color(0xFFC48A);
    static final Color GHOST = new Color(0xD64545);
    static final Color CURRENT = new Color(0xFF8C00);
    private static final Color LINE = new Color(0x34495E);
    private static final Color FOCUS = new Color(0x1A5FB4);
    private static final Color FOCUS_BG = new Color(0xE3EEFF);
    private static final Color LABEL = new Color(0x8E44AD);
    private static final Color MUTED = new Color(0x8A8A8A);

    private static final Font VALUE = new Font("Dialog", Font.BOLD, 15);
    private static final Font TITLE = new Font("Dialog", Font.BOLD, 15);
    private static final Font SMALL = new Font("Dialog", Font.PLAIN, 12);
    private static final Font SMALL_B = new Font("Dialog", Font.BOLD, 13);

    private static final int PAD = 24, GAP = 40, TITLE_H = 28;
    private static final Stroke THIN = new BasicStroke(1.4f);
    private static final Stroke THICK = new BasicStroke(3f);
    private static final Stroke DASHED = new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
            new float[] { 6f, 4f }, 0f);

    private Step step;

    StructPanel() {
        setBackground(Color.WHITE);
    }

    void setStep(Step s) {
        step = s;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return layoutAll(null);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        layoutAll(g);
        g.dispose();
    }

    /** כשה-g הוא null רק מודדים את הגודל, בלי לצייר */
    private Dimension layoutAll(Graphics2D g) {
        int y = PAD, maxX = 300;
        if (step == null || step.views.isEmpty()) {
            if (g != null) {
                g.setColor(MUTED);
                g.setFont(TITLE);
                g.drawString(step == null ? "מחכה להרצה..." : "אין עדיין מבנים להצגה", PAD, PAD + 20);
            }
            return new Dimension(maxX, 80);
        }
        int x = PAD, rowH = 0;
        for (View v : step.views) {
            if (v instanceof LinearView && ((LinearView) v).stack) {
                Dimension d = drawStack(g, (LinearView) v, x, y);
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
            if (d != null) { y += d.height + GAP / 2; maxX = Math.max(maxX, PAD + d.width); }
        }
        for (View v : step.views) {
            Dimension d = null;
            if (v instanceof ChainView) d = drawChain(g, (ChainView) v, PAD, y);
            if (d != null) { y += d.height + GAP / 2; maxX = Math.max(maxX, PAD + d.width); }
        }
        for (View v : step.views) {
            Dimension d = null;
            if (v instanceof TreeView) d = drawTree(g, (TreeView) v, PAD, y);
            if (d != null) { y += d.height + GAP / 2; maxX = Math.max(maxX, PAD + d.width); }
        }
        return new Dimension(maxX + PAD, y + PAD);
    }

    // ------------------------------------------------------------ מחסנית

    private Dimension drawStack(Graphics2D g, LinearView v, int x, int y) {
        int n = v.items.size(), rows = Math.max(n, 1);
        int cw = Math.max(70, maxWidth(v.items, v.ghost) + 22), ch = 32, ghostH = 46;
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
        int cw = Math.max(56, maxWidth(v.items, v.ghost) + 20), ch = 36;
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
        int vw = Math.max(46, maxWidth(values, null) + 18), pw = 20, ch = 34, ag = 38;
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
                g.drawRect(bx - 2, top - 2, vw + pw + 4, ch + 4);
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
            g.setColor(c.current ? CURRENT : LINE);
            g.setStroke(c.shared ? DASHED : c.current ? THICK : THIN);
            g.drawOval(p[0] - r, p[1] - r, 2 * r, 2 * r);
            g.setColor(Color.BLACK);
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
            g.drawString(b.toString(), cx - fm.stringWidth(b.toString()) / 2, bottomY - 2);
        } else if (c.current) {
            g.setColor(CURRENT);
            g.fillPolygon(new Polygon(new int[] { cx - 7, cx + 7, cx }, new int[] { bottomY - 12, bottomY - 12, bottomY - 2 }, 3));
        }
    }

    private void drawTitle(Graphics2D g, String name, String type, int x, int y, boolean focus) {
        String s = name + "   (" + type + ")";
        g.setFont(TITLE);
        int w = g.getFontMetrics().stringWidth(s);
        if (focus) {
            g.setColor(FOCUS_BG);
            g.fillRoundRect(x - 4, y, w + 12, TITLE_H - 6, 10, 10);
        }
        g.setColor(focus ? FOCUS : LINE);
        g.drawString(s, x + 2, y + TITLE_H - 12);
    }

    private int titleWidth(String name, String type) {
        return textWidth(name + "   (" + type + ")", TITLE) + 16;
    }

    private void box(Graphics2D g, int x, int y, int w, int h, Color fill, String text, boolean dashed) {
        g.setColor(fill);
        g.fillRect(x, y, w, h);
        g.setColor(LINE);
        g.setStroke(dashed ? DASHED : THIN);
        g.drawRect(x, y, w, h);
        if (text != null) {
            g.setColor(Color.BLACK);
            center(g, text, VALUE, x + w / 2, y + h / 2);
        }
    }

    private void ghostBox(Graphics2D g, int x, int y, int w, int h, String text) {
        g.setColor(new Color(0xFDE2E2));
        g.fillRect(x, y, w, h);
        g.setColor(GHOST);
        g.setStroke(DASHED);
        g.drawRect(x, y, w, h);
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

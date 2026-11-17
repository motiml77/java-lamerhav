package unit4.collectionsLib;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;

import unit4.collectionsLib.Model.Access;
import unit4.collectionsLib.Model.ArrayView;
import unit4.collectionsLib.Model.Mark;
import unit4.collectionsLib.Model.MatrixView;
import unit4.collectionsLib.Model.Obj;
import unit4.collectionsLib.Model.ObjView;
import unit4.collectionsLib.Model.Step;
import unit4.collectionsLib.Model.View;

/**
 * טבלת המאפיינים של אובייקט (שם, ערך) — משותפת לחלון המבנים, לחלונית הצד ולכרטיס המלא.
 * מאפיין שערכו אובייקט של מחלקה של התלמיד (Team.leader) מצויר כאן ככרטיס קטן — הסוג, תג של משתנה שמצביע עליו, והמאפיינים שלו
 * (שוב בטבלה כזו, עד 3 רמות); ומאפיין שהוא מערך (int[] scores) — כתאים קטנים עם האינדקסים מתחת, כמו המערך הרגיל בקטן.
 * המדידה והציור — באותה פעולה (g == null: רק מודדים), כך שהגודל תמיד מתאים לציור.
 */
final class ObjCard {
    private ObjCard() {}

    static final int ROW_H = 18;
    static final Font FIELD = Theme.font(Theme.family, Font.PLAIN, 13);
    static final Font FIELD_B = Theme.font(Theme.boldFamily, Theme.boldStyle, 13);
    private static final Color INK = new Color(0x1D2433), MUTED = new Color(0x6B7385);

    // כרטיס בתוך כרטיס, מערך בתוך כרטיס
    private static final Font MINI = FIELD.deriveFont(12f), TINY = FIELD.deriveFont(10f), TINY_B = FIELD_B.deriveFont(11.5f);
    private static final Color LABEL = new Color(0x7C3AED), CHIP = new Color(0xEEF0F4), CARD_LINE = new Color(0xB4C2DA);
    /** תא קטן: גובה; מתחת — האינדקס; מעל — מקום לתווית (i); ריווח בכרטיס הפנימי; שורת הסוג שלו; רוחב מינימלי של תא; רוחב מקסימלי של טקסט */
    private static final int CELL_H = 18, IDX_H = 11, LBL_H = 15, PAD = 5, HEAD_H = 18, MIN_CELL = 22, VAL_MAX = 170;

    static Font mono() {
        return Theme.mono.deriveFont(12.5f);
    }

    /**
     * מה השורה עושה באובייקט (SelfViz): מאפיינים שהיא קוראת / משנה, ותוויות אינדקס (i) על תאים של מערך-מאפיין.
     * מפתח = שם המאפיין, או מסלול לאובייקט פנימי ("leader.grade") — כשהשורה מגיעה אליו דרך החיצוני (t.leader.grade)
     */
    static final class Hot {
        final Map<String, Mark> marks = new HashMap<String, Mark>();
        final Map<String, Map<Integer, List<String>>> idx = new HashMap<String, Map<Integer, List<String>>>();    // מערך; מטריצה — שורות
        final Map<String, Map<Integer, List<String>>> cols = new HashMap<String, Map<Integer, List<String>>>();   // עמודות של מטריצה
        /** בשביל האובייקטים הפנימיים: מה שהשורה עושה בהם דרך משתנה שמצביע ישר עליהם (s.grade, כש-s מצויר בתוך t) */
        SelfViz.Info self;
    }

    /**
     * טבלת מאפיינים. המאפיין שנגעו בו (acc) מודגש בצבע הקריאה / השינוי.
     * cols = כמה עמודות (מאפיינים רבים — כמה עמודות, כדי שהטבלה תישאר נמוכה). g == null: רק מודדים.
     */
    static Dimension table(JComponent c, Graphics2D g, Obj o, int x, int y, Access acc, int cols) {
        return table(c, g, o, x, y, acc, cols, (Hot) null);
    }

    /** marks = עוד מאפיינים מודגשים (שהשורה קוראת / משנה — age, this.age), לפי השם */
    static Dimension table(JComponent c, Graphics2D g, Obj o, int x, int y, Access acc, int cols, Map<String, Mark> marks) {
        Hot h = null;
        if (marks != null) {
            h = new Hot();
            h.marks.putAll(marks);
        }
        return table(c, g, o, x, y, acc, cols, h);
    }

    /** hs = מה השורה עושה באובייקט (גם באובייקטים ובמערכים שבתוכו) — או null */
    static Dimension table(JComponent c, Graphics2D g, Obj o, int x, int y, Access acc, int cols, Hot hs) {
        int n = o.fields.size(), per = (n + cols - 1) / cols;
        Font mono = mono();
        int nameW = 0, valW = 0;
        FontMetrics mm = c.getFontMetrics(mono), fm = c.getFontMetrics(FIELD), fb = c.getFontMetrics(FIELD_B);
        Dimension[] rich = new Dimension[n];   // מאפיין שמצויר (כרטיס / תאים) — הגודל שלו; null = טקסט
        for (int i = 0; i < n; i++) {
            String[] f = o.fields.get(i);
            nameW = Math.max(nameW, mm.stringWidth(f[0]));
            rich[i] = richValue(c, null, o, f[0], 0, 0, hs, acc);
            valW = Math.max(valW, rich[i] != null ? rich[i].width : Math.min(VAL_MAX, Math.max(fm.stringWidth(f[1]), fb.stringWidth(f[1]))));
        }
        int[] rowH = new int[n];
        for (int i = 0; i < n; i++) rowH[i] = rich[i] == null ? ROW_H : Math.max(ROW_H, rich[i].height + 4);
        int colW = nameW + valW + 26, w = cols * colW + (cols - 1) * 10, h = ROW_H;
        for (int k = 0; k < cols; k++) {
            int ch = 0;
            for (int i = k * per; i < Math.min(n, (k + 1) * per); i++) ch += rowH[i];
            h = Math.max(h, ch);
        }
        if (g == null) return new Dimension(w, h);
        int[] colY = new int[Math.max(1, cols)];
        for (int i = 0; i < n; i++) {
            String[] f = o.fields.get(i);
            int col = i / per, cx = x + col * (colW + 10), cy = y + colY[col];
            colY[col] += rowH[i];
            boolean touched = acc != null && f[0].equals(acc.field) && acc.obj != null && acc.obj.id == o.id;
            Mark mark = touched ? acc.mark : hs != null ? hs.marks.get(f[0]) : null;
            boolean lit = mark != null;
            if (lit) {
                Color fill = StructPanel.color(mark);
                g.setColor(fill);
                g.fillRoundRect(cx - 2, cy + 1, colW + 4, rowH[i] - 2, 8, 8);
                g.setColor(darker(fill));
                g.setStroke(new BasicStroke(1.3f));
                g.drawRoundRect(cx - 2, cy + 1, colW + 4, rowH[i] - 2, 8, 8);
            }
            // השם — בגובה השורה הראשונה של הערך: מול התאים של מערך, מול הסוג של כרטיס פנימי
            int base = rich[i] == null ? cy + ROW_H - 5 : o.nested.containsKey(f[0]) ? cy + 2 + PAD + 11 : cy + 2 + LBL_H + CELL_H - 5;
            g.setFont(mono);
            g.setColor(lit ? INK : MUTED);
            g.drawString(f[0], cx + 4, base);
            int vx = cx + 4 + nameW + 14;
            if (rich[i] != null) {
                richValue(c, g, o, f[0], vx, cy + 2, hs, acc);
                continue;
            }
            g.setFont(lit ? FIELD_B : FIELD);
            g.setColor(INK);
            String val = f[1];
            FontMetrics vm = g.getFontMetrics();
            while (val.length() > 4 && vm.stringWidth(val) > valW) val = val.substring(0, val.length() - 2) + "…";
            g.drawString(val, vx, cy + ROW_H - 5);
        }
        return new Dimension(w, h);
    }

    static int columnsFor(int fields, int max) {
        return Math.min(max, fields > 6 ? 3 : fields > 3 ? 2 : 1);
    }

    static Color darker(Color c) {
        return new Color((int) (c.getRed() * 0.72), (int) (c.getGreen() * 0.72), (int) (c.getBlue() * 0.72));
    }

    // ------------------------------------------------------------ ערך שמצויר: כרטיס פנימי / תאים קטנים

    /** המאפיין field של o, אם הוא אובייקט פנימי או מערך: מצייר אותו ב-(x, y) (או רק מודד, g == null) ומחזיר את הגודל; null = טקסט */
    private static Dimension richValue(JComponent c, Graphics2D g, Obj o, String field, int x, int y, Hot hs, Access acc) {
        Obj n = o.nested.get(field);
        if (n != null) return card(c, g, n, x, y, inner(hs, field, n), acc);
        View a = o.arrays.get(field);
        if (a instanceof ArrayView) return miniArray(c, g, (ArrayView) a, x, y, hs == null ? null : hs.idx.get(field));
        if (a instanceof MatrixView)
            return miniMatrix(c, g, (MatrixView) a, x, y, hs == null ? null : hs.idx.get(field), hs == null ? null : hs.cols.get(field));
        return null;
    }

    /** מה השורה עושה באובייקט הפנימי n (המאפיין field): המסלולים "field.x" של החיצוני, ומה שנעשה דרך המשתנים שמצביעים עליו */
    static Hot inner(Hot hs, String field, Obj n) {
        Hot h = new Hot();
        if (hs != null) {
            h.self = hs.self;
            String pre = field + ".";
            for (Map.Entry<String, Mark> e : hs.marks.entrySet())
                if (e.getKey().startsWith(pre)) h.marks.put(e.getKey().substring(pre.length()), e.getValue());
            strip(hs.idx, pre, h.idx);
            strip(hs.cols, pre, h.cols);
        }
        SelfViz.addHot(h, h.self, n);
        return h;
    }

    private static void strip(Map<String, Map<Integer, List<String>>> from, String pre, Map<String, Map<Integer, List<String>>> to) {
        for (Map.Entry<String, Map<Integer, List<String>>> e : from.entrySet())
            if (e.getKey().startsWith(pre)) to.put(e.getKey().substring(pre.length()), e.getValue());
    }

    /** כרטיס פנימי: הסוג (Student) ותג של המשתנים שמצביעים עליו (s), ומתחת — טבלת המאפיינים שלו */
    private static Dimension card(JComponent c, Graphics2D g, Obj n, int x, int y, Hot hs, Access acc) {
        Dimension t = table(c, null, n, 0, 0, acc, 1, hs);
        FontMetrics tm = c.getFontMetrics(TINY), lm = c.getFontMetrics(TINY_B);
        int chipW = tm.stringWidth(n.type) + 12;
        String tag = String.join(", ", n.labels);
        int tagW = n.labels.isEmpty() ? 0 : lm.stringWidth(tag) + 12;
        int w = Math.max(t.width, chipW + (tagW > 0 ? 5 + tagW : 0)) + 2 * PAD, h = HEAD_H + t.height + 2 * PAD;
        if (g == null) return new Dimension(w, h);
        boolean touched = acc != null && acc.obj != null && n.id > 0 && acc.obj.id == n.id;
        g.setColor(Color.WHITE);
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setColor(touched ? darker(StructPanel.color(acc.mark)) : CARD_LINE);
        g.setStroke(new BasicStroke(touched ? 2f : 1.1f));
        g.drawRoundRect(x, y, w, h, 10, 10);
        int cy = y + PAD, ch = 15;
        g.setColor(CHIP);
        g.fillRoundRect(x + PAD, cy, chipW, ch, ch, ch);
        g.setColor(MUTED);
        g.setFont(TINY);
        g.drawString(n.type, x + PAD + 6, cy + 11);
        if (tagW > 0) {   // המשתנה שמצביע על האובייקט הזה (כמו התג על תא של מבנה בתוך מבנה)
            int tx = x + PAD + chipW + 5;
            g.setColor(Color.WHITE);
            g.fillRoundRect(tx, cy, tagW, ch, ch, ch);
            g.setColor(LABEL);
            g.setStroke(new BasicStroke(1.1f));
            g.drawRoundRect(tx, cy, tagW, ch, ch, ch);
            g.setFont(TINY_B);
            g.drawString(tag, tx + 6, cy + 11);
        }
        table(c, g, n, x + PAD, y + PAD + HEAD_H, acc, 1, hs);
        return new Dimension(w, h);
    }

    /** רוחב תא קטן: לפי הטקסט הארוך ביותר */
    private static int cellW(JComponent c, List<String> items) {
        FontMetrics fm = c.getFontMetrics(MINI);
        int w = 0;
        for (String s : items) w = Math.max(w, fm.stringWidth(s));
        return Math.max(MIN_CELL, Math.min(VAL_MAX, w + 10));
    }

    /** מערך בתוך כרטיס: תא לכל איבר (עד 12, ואז "…"), האינדקס מתחת, תא שהשתנה בכתום, ותווית (i) מעל התא שהשורה ניגשת אליו */
    private static Dimension miniArray(JComponent c, Graphics2D g, ArrayView a, int x, int y, Map<Integer, List<String>> labels) {
        int n = a.items.size(), cw = cellW(c, a.items);
        int w = n == 0 ? cw + 12 : n * cw + (a.truncated ? 16 : 0), h = LBL_H + CELL_H + IDX_H;
        if (g == null) return new Dimension(w, h);
        int top = y + LBL_H;
        if (n == 0) {   // מערך באורך 0
            emptyBox(g, x, top, cw + 12);
            return new Dimension(w, h);
        }
        for (int i = 0; i < n; i++) {
            int bx = x + i * cw;
            miniCell(g, bx, top, cw, a.items.get(i), a.changed.contains(i));
            g.setFont(TINY);
            g.setColor(MUTED);
            centered(g, String.valueOf(i), bx + cw / 2, top + CELL_H + IDX_H - 1);
            List<String> l = labels == null ? null : labels.get(i);
            if (l != null) indexLabel(g, String.join(",", l), bx + cw / 2, top);
        }
        if (a.truncated) {
            g.setFont(MINI);
            g.setColor(MUTED);
            g.drawString("…", x + n * cw + 4, top + CELL_H - 5);
        }
        return new Dimension(w, h);
    }

    /** מטריצה קטנה בתוך כרטיס (עד 6×8): מספר השורה משמאל, מספר העמודה מתחת, ותוויות (r / c) של השורה והעמודה שהשורה ניגשת אליהן */
    private static Dimension miniMatrix(JComponent c, Graphics2D g, MatrixView m, int x, int y, Map<Integer, List<String>> rowLabels,
            Map<Integer, List<String>> colLabels) {
        int rows = m.rows.size(), cols = 0;
        List<String> all = new java.util.ArrayList<String>();
        for (List<String> r : m.rows) {
            cols = Math.max(cols, r.size());
            all.addAll(r);
        }
        int cw = cellW(c, all);
        FontMetrics lm = c.getFontMetrics(TINY_B), tm = c.getFontMetrics(TINY);
        int rlW = 0;
        if (rowLabels != null) for (List<String> l : rowLabels.values()) rlW = Math.max(rlW, lm.stringWidth(String.join(",", l)) + 9);
        int numW = tm.stringWidth(String.valueOf(Math.max(0, rows - 1))) + 5, left = x + rlW + numW;
        int w = left - x + Math.max(cols, 1) * cw, h = LBL_H + Math.max(rows, 1) * CELL_H + IDX_H;
        if (g == null) return new Dimension(w, h);
        int top = y + LBL_H;
        if (rows == 0) {
            emptyBox(g, left, top, cw + 12);
            return new Dimension(w, h);
        }
        for (int r = 0; r < rows; r++) {
            int ry = top + r * CELL_H;
            g.setFont(TINY);
            g.setColor(MUTED);
            String num = String.valueOf(r);
            g.drawString(num, left - 4 - tm.stringWidth(num), ry + CELL_H - 5);
            List<String> row = m.rows.get(r);
            for (int k = 0; k < row.size(); k++) miniCell(g, left + k * cw, ry, cw, row.get(k), m.changed.contains(r + "," + k));
            List<String> rl = rowLabels == null ? null : rowLabels.get(r);
            if (rl != null) {
                g.setFont(TINY_B);
                g.setColor(LABEL);
                g.drawString(String.join(",", rl), x, ry + CELL_H - 5);
            }
        }
        g.setFont(TINY);
        g.setColor(MUTED);
        for (int k = 0; k < cols; k++) centered(g, String.valueOf(k), left + k * cw + cw / 2, top + rows * CELL_H + IDX_H - 1);
        if (colLabels != null)
            for (Map.Entry<Integer, List<String>> e : colLabels.entrySet())
                indexLabel(g, String.join(",", e.getValue()), left + e.getKey() * cw + cw / 2, top);
        return new Dimension(w, h);
    }

    /** תא קטן: ערך במרכז; שהשתנה בצעד הזה — בכתום (כמו ArrayView.changed) */
    private static void miniCell(Graphics2D g, int x, int y, int w, String text, boolean changed) {
        Color fill = changed ? StructPanel.CHANGED_C : StructPanel.BOX;
        g.setColor(fill);
        g.fillRoundRect(x + 1, y, w - 2, CELL_H, 6, 6);
        g.setColor(changed ? darker(fill) : StructPanel.BOX_LINE);
        g.setStroke(new BasicStroke(changed ? 1.5f : 1f));
        g.drawRoundRect(x + 1, y, w - 2, CELL_H, 6, 6);
        g.setFont(changed ? MINI.deriveFont(Font.BOLD) : MINI);
        g.setColor(INK);
        FontMetrics fm = g.getFontMetrics();
        String t = text;
        while (t.length() > 2 && fm.stringWidth(t) > w - 6) t = t.substring(0, t.length() - 2) + "…";
        g.drawString(t, x + (w - fm.stringWidth(t)) / 2, y + CELL_H - 5);
    }

    private static void emptyBox(Graphics2D g, int x, int y, int w) {
        g.setColor(StructPanel.BOX_LINE);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 3f, 3f }, 0f));
        g.drawRoundRect(x + 1, y, w - 2, CELL_H, 6, 6);
        g.setFont(TINY);
        g.setColor(MUTED);
        centered(g, "ריק", x + w / 2, y + CELL_H - 6);
    }

    /** "i" בסגול מעל התא, עם חץ קטן כלפיו (כמו במערך הרגיל) */
    private static void indexLabel(Graphics2D g, String text, int cx, int cellTop) {
        g.setColor(LABEL);
        g.setFont(TINY_B);
        centered(g, text, cx, cellTop - 5);
        g.fillPolygon(new int[] { cx - 3, cx + 3, cx }, new int[] { cellTop - 4, cellTop - 4, cellTop }, 3);
    }

    private static void centered(Graphics2D g, String s, int cx, int baseline) {
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, baseline);
    }

    // ------------------------------------------------------------ בדיקות

    /**
     * בשביל הבדיקות (-Ddsviz.conditions=true / TraceViewer --check): הכרטיסים המלאים בכל צעד, עם מה שמצויר בתוכם —
     * [טבלה #26 שורה 45: this: Team(this){name="אלפא", leader=Student(s){name="נועה", grade=90}, scores=[0, 0, 0] i→1}].
     * אובייקט פנימי: סוג(תגים){...}; מערך: [...] עם * על תא שהשתנה, "…" אם קוצר, ותוויות אינדקס (i→1; במטריצה i→r1 / j→c2)
     */
    static String dump() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Recorder.stepCount(); i++) {
            Step s = Recorder.step(i);
            SelfViz.Info in = SelfViz.of(s);
            for (View v : s.views) {
                if (!(v instanceof ObjView) || ((ObjView) v).obj == null) continue;
                Obj o = ((ObjView) v).obj;
                b.append("  [טבלה #").append(i + 1).append(" שורה ").append(s.where == null ? "?" : String.valueOf(s.where.line)).append(": ")
                 .append(v.title).append(": ").append(text(o, SelfViz.hot(in, o))).append("]\n");
            }
        }
        return b.toString();
    }

    static String text(Obj o, Hot hs) {
        StringBuilder b = new StringBuilder(o.type);
        if (!o.labels.isEmpty()) b.append('(').append(String.join(",", o.labels)).append(')');
        b.append('{');
        for (int i = 0; i < o.fields.size(); i++) {
            String f = o.fields.get(i)[0];
            b.append(i == 0 ? "" : ", ").append(f).append('=');
            Obj n = o.nested.get(f);
            View a = o.arrays.get(f);
            if (n != null) b.append(text(n, inner(hs, f, n)));
            else if (a instanceof ArrayView) {
                ArrayView av = (ArrayView) a;
                b.append('[');
                for (int k = 0; k < av.items.size(); k++) b.append(k == 0 ? "" : ", ").append(av.items.get(k)).append(av.changed.contains(k) ? "*" : "");
                b.append(av.truncated ? ", …]" : "]");
                labels(b, hs == null ? null : hs.idx.get(f), "");
            } else if (a instanceof MatrixView) {
                MatrixView m = (MatrixView) a;
                b.append('[');
                for (int r = 0; r < m.rows.size(); r++) {
                    b.append(r == 0 ? "[" : ", [");
                    for (int k = 0; k < m.rows.get(r).size(); k++)
                        b.append(k == 0 ? "" : ", ").append(m.rows.get(r).get(k)).append(m.changed.contains(r + "," + k) ? "*" : "");
                    b.append(']');
                }
                b.append(']');
                labels(b, hs == null ? null : hs.idx.get(f), "r");
                labels(b, hs == null ? null : hs.cols.get(f), "c");
            } else b.append(o.fields.get(i)[1]);
        }
        return b.append('}').toString();
    }

    private static void labels(StringBuilder b, Map<Integer, List<String>> l, String kind) {
        if (l == null) return;
        for (Map.Entry<Integer, List<String>> e : new java.util.TreeMap<Integer, List<String>>(l).entrySet())
            for (String name : e.getValue()) b.append(' ').append(name).append('→').append(kind).append(e.getKey());
    }
}

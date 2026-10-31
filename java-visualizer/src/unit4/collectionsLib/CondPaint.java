package unit4.collectionsLib;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.text.AttributedString;

import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;

import unit4.collectionsLib.CondViz.Block;
import unit4.collectionsLib.CondViz.Branch;
import unit4.collectionsLib.CondViz.Check;
import unit4.collectionsLib.CondViz.Part;

/**
 * מצייר בקוד את מה ש-CondViz מצא:
 * - בשורת התנאי, מימין לקוד: המשתנים בקטן (x = 7), התנאי עם הערכים (7 % 2 == 0), ותגית צבעונית — ✓ מתקיים / ✗ לא מתקיים, ולאן ממשיכים;
 * - תנאי מורכב (&&, ||, !): קו צבעוני מתחת לכל חלק בקוד (ירוק / אדום / אפור מקווקו = לא נבדק בכלל), כל חלק עם הערכים שלו,
 *   ובשורה שמתחת — למה התנאי יצא כך (כולל הקיצור: את מה שאחרי החלק שקבע לא בודקים);
 * - בזמן שרצים בתוך ענף: פס צבעוני ליד הענף שרץ (ירוק = if, סגול = else) עם תגית, והענף שמדלגים עליו — מעומעם.
 */
final class CondPaint {
    private CondPaint() {}

    static final Color TRUE = new Color(0x15803D), FALSE = new Color(0xDC2626), UNKNOWN = new Color(0x6B7280);
    static final Color THEN = new Color(0x16A34A), ELSE = new Color(0x7C3AED);
    private static final Color PILL_BG = new Color(0xEEF2FF), PILL_LINE = new Color(0xC7D2FE), PILL_TEXT = new Color(0x1E293B);
    private static final Color VALUES = new Color(0x475569);

    /** used: לכל שורה — עד איפה כבר צוירו בה תגיות (כדי שתגית נוספת, למשל של קריאה לפעולת עזר, תצויר אחריהן) */
    static void paint(Graphics2D g, JTextComponent c, CondViz.Marks m, java.util.Map<Integer, Integer> used) {
        if (m == null) return;
        Branch inner = m.branches.isEmpty() ? null : m.branches.get(m.branches.size() - 1);
        // הענפים שמדלגים עליהם — מעומעמים (בכל תנאי שהשורה בתוכו)
        for (Branch br : m.branches) {
            Block b = br.block;
            if (br.inElse) dim(g, c, b.thenFirst, b.thenLast);
            else if (b.hasElse()) dim(g, c, b.elseFirst, b.elseLast);
        }
        if (inner != null) {
            Block b = inner.block;
            Color col = inner.inElse ? ELSE : THEN;
            if (inner.inElse) stripe(g, c, b.elseFirst, b.elseLast, col);
            else stripe(g, c, b.thenFirst, b.thenLast, col);
            int tagLine = inner.inElse ? b.elseLine : b.line;
            if (m.check == null || m.check.block.line != tagLine) tag(g, c, tagLine, inner.inElse ? "✗" : "✓", CondViz.note(inner), col, used);
        }
        if (m.check != null) check(g, c, m.check, used);
    }

    // ------------------------------------------------------------ שורת התנאי

    private static void check(Graphics2D g, JTextComponent c, Check k, java.util.Map<Integer, Integer> used) {
        if (!k.parts.isEmpty()) {
            compound(g, c, k, used);
            return;
        }
        Rectangle r = lineRect(c, k.block.line);
        if (r == null) return;
        Font mono = chipMono();
        FontRenderContext frc = g.getFontRenderContext();
        int h = Math.min(r.height - 2, 20), y = r.y + (r.height - h) / 2;
        // רוחב: הפילים של המשתנים, הערכים, והתגית
        FontMetrics fm = g.getFontMetrics(mono);
        int w = pillsWidth(fm, k);
        String vals = k.values.isEmpty() ? "" : k.values;
        if (!vals.isEmpty()) w += fm.stringWidth(vals) + 12 + fm.stringWidth("→ ") + 4;
        TextLayout tl = resultLayout(k, frc);
        int tagW = resultWidth(g, k, tl);
        w += tagW;
        int x = place(c, k.block.line, r, w);
        used.put(k.block.line, x + w + 6);

        chipBackground(g, x, y, w, h);
        g.setFont(mono);
        int base = y + (h + fm.getAscent() - fm.getDescent()) / 2;
        x = drawPills(g, k, fm, x, y, h, base);
        if (!vals.isEmpty()) {
            g.setColor(VALUES);
            g.drawString("→ " + vals, x + 4, base);
            x += fm.stringWidth("→ " + vals) + 16;
        }
        drawResult(g, k, tl, x, y, h, tagW);
    }

    // ------------------------------------------------------------ חלקי השבב (משותף לתנאי פשוט ולתנאי מורכב)

    private static Font chipMono() {
        return Theme.mono.deriveFont(Theme.mono.getSize2D() - 1.5f);
    }

    private static int pillsWidth(FontMetrics fm, Check k) {
        int w = 0;
        for (String[] v : k.vars) w += fm.stringWidth(v[0] + " = " + v[1]) + 14 + 6;
        return w;
    }

    /** רקע עדין לכל השבב — כדי שיהיה קריא גם אם הוא עולה על קוד ארוך */
    private static void chipBackground(Graphics2D g, int x, int y, int w, int h) {
        g.setColor(new Color(255, 255, 255, 235));
        g.fillRoundRect(x - 6, y - 2, w + 12, h + 4, 12, 12);
        g.setColor(new Color(0xE2E8F0));
        g.drawRoundRect(x - 6, y - 2, w + 12, h + 4, 12, 12);
    }

    /** הפילים של המשתנים (x = 7). מחזיר איפה ממשיכים */
    private static int drawPills(Graphics2D g, Check k, FontMetrics fm, int x, int y, int h, int base) {
        g.setFont(fm.getFont());
        for (String[] v : k.vars) {
            String s = v[0] + " = " + v[1];
            int pw = fm.stringWidth(s) + 14;
            g.setColor(PILL_BG);
            g.fillRoundRect(x, y + 1, pw, h - 2, h - 2, h - 2);
            g.setColor(PILL_LINE);
            g.drawRoundRect(x, y + 1, pw, h - 2, h - 2, h - 2);
            g.setColor(PILL_TEXT);
            g.drawString(s, x + 7, base);
            x += pw + 6;
        }
        return x;
    }

    private static String resultMark(Check k) {
        return k.result == null ? "?" : k.result ? "✓" : "✗";
    }

    private static Color resultColor(Check k) {
        return k.result == null ? UNKNOWN : k.result ? TRUE : FALSE;
    }

    private static TextLayout resultLayout(Check k, FontRenderContext frc) {
        String label = k.result == null ? "בודקים…" : k.result ? "מתקיים" + (k.outcome.isEmpty() ? "" : " · " + k.outcome) : "לא מתקיים" + (k.outcome.isEmpty() ? "" : " · " + k.outcome);
        return rtl(label, Theme.smallBold != null ? Theme.smallBold : Theme.small, frc);
    }

    private static int resultWidth(Graphics2D g, Check k, TextLayout tl) {
        return (int) Math.ceil(tl.getAdvance()) + g.getFontMetrics(Theme.small).stringWidth(resultMark(k)) + 22;
    }

    /** התגית הצבעונית: ✓ מתקיים · נכנסים ל-if */
    private static void drawResult(Graphics2D g, Check k, TextLayout tl, int x, int y, int h, int tagW) {
        String mark = resultMark(k);
        g.setColor(resultColor(k));
        g.fillRoundRect(x, y, tagW, h, h, h);
        g.setColor(Color.WHITE);
        // מימין לשמאל: הסימן (✓ / ✗) מימין, הטקסט משמאלו
        g.setFont(Theme.small);
        FontMetrics sm = g.getFontMetrics(Theme.small);
        int markX = x + tagW - 9 - sm.stringWidth(mark);
        g.drawString(mark, markX, y + (h + sm.getAscent() - sm.getDescent()) / 2);
        tl.draw(g, markX - 5 - tl.getAdvance(), y + (h + tl.getAscent() - tl.getDescent()) / 2f);
    }

    // ------------------------------------------------------------ תנאי מורכב (&&, ||, !): כל חלק בנפרד

    static final Color SKIP = new Color(0x9CA3AF), MAYBE = new Color(0x64748B);

    /** הצבע של חלק: ירוק מתקיים, אדום לא מתקיים, אפור לא נבדק, אפור-כחלחל לא ידוע */
    static Color color(Part p) {
        if (p.state == CondViz.SKIPPED) return SKIP;
        if (p.state == CondViz.UNSURE || p.value == null) return MAYBE;
        return p.value ? TRUE : FALSE;
    }

    /**
     * תנאי מורכב:
     * - בקוד: קו דק מתחת לכל חלק (ירוק מתקיים, אדום לא מתקיים, אפור מקווקו — לא נבדק בכלל, מנוקד — לא ידוע). הקוד עצמו לא מוסתר;
     * - מימין לשורה: המשתנים, כל חלק עם הערכים (7 > 5 ✓ && 3 > 4 ✗ && לא נבדק), והתגית — כמו בתנאי פשוט;
     * - בשורה שמתחת (או מעל): למה — "y > 4 לא מתקיים (3 > 4), ולכן את ... לא בודקים בכלל".
     * כשאין מקום — קודם מוותרים על הפילים של המשתנים, אחר כך על הערכים (הם גם בהסבר); התגית תמיד.
     */
    private static void compound(Graphics2D g, JTextComponent c, Check k, java.util.Map<Integer, Integer> used) {
        int line = k.block.line;
        Rectangle r = lineRect(c, line);
        if (r == null) return;
        for (Part p : k.parts) underline(g, c, k.block, p);

        Font mono = chipMono();
        FontRenderContext frc = g.getFontRenderContext();
        FontMetrics fm = g.getFontMetrics(mono);
        int h = Math.min(r.height - 2, 20), y = r.y + (r.height - h) / 2;
        int wPills = pillsWidth(fm, k);
        java.util.List<Seg> segs = segments(k);
        int wVals = segsWidth(g, segs, fm) + fm.stringWidth("→ ") + 4 + 12;
        TextLayout tl = resultLayout(k, frc);
        int tagW = resultWidth(g, k, tl);
        Rectangle vis = c.getVisibleRect();
        int right = vis.x + vis.width - 10, start = lineEndX(c, line) + 22;
        boolean pills = wPills > 0, vals = !segs.isEmpty();
        int w = wPills + (vals ? wVals : 0) + tagW;
        if (start + w > right && pills) {
            pills = false;
            w -= wPills;
        }
        if (start + w > right && vals) {
            vals = false;
            w -= wVals;
        }
        int x = start + w > right ? Math.max(vis.x + 40, right - w) : start;
        used.put(line, x + w + 6);
        int chipX = x;

        chipBackground(g, x, y, w, h);
        int base = y + (h + fm.getAscent() - fm.getDescent()) / 2;
        if (pills) x = drawPills(g, k, fm, x, y, h, base);
        if (vals) {
            g.setFont(mono);
            g.setColor(VALUES);
            g.drawString("→ ", x + 4, base);
            x = drawSegs(g, segs, fm, x + 4 + fm.stringWidth("→ "), y, h, base) + 12;
        }
        drawResult(g, k, tl, x, y, h, tagW);
        if (!k.why.isEmpty()) why(g, c, k, chipX, chipX + w, used);
    }

    /** קטע בשבב של הערכים: טקסט (חלק עם הערכים, או && / || / סוגריים) */
    private static final class Seg {
        String text;
        Color color;
        String mark;        // ✓ / ✗ / ? — או null
        boolean skipped;    // "לא נבדק"
    }

    private static java.util.List<Seg> segments(Check k) {
        java.util.List<Seg> out = new java.util.ArrayList<Seg>();
        String cond = k.block.cond;
        int pos = 0;
        for (Part p : k.parts) {
            String op = cond.substring(pos, p.from).replaceAll("\\s+", " ");
            if (!op.isEmpty()) out.add(seg(op, VALUES, null, false));
            boolean skipped = p.state == CondViz.SKIPPED;
            out.add(seg(skipped ? "לא נבדק" : CondViz.compact(p.sub.isEmpty() ? p.code : p.sub), color(p), skipped ? null : CondViz.mark(p), skipped));
            pos = p.to;
        }
        String tail = cond.substring(pos).replaceAll("\\s+", " ");
        if (!tail.isEmpty()) out.add(seg(tail, VALUES, null, false));
        return out;
    }

    private static Seg seg(String text, Color color, String mark, boolean skipped) {
        Seg s = new Seg();
        s.text = text;
        s.color = color;
        s.mark = mark;
        s.skipped = skipped;
        return s;
    }

    private static Font markFont() {
        return (Theme.smallBold != null ? Theme.smallBold : Theme.small).deriveFont(11f);
    }

    private static int segsWidth(Graphics2D g, java.util.List<Seg> segs, FontMetrics fm) {
        FontMetrics mk = g.getFontMetrics(markFont());
        int w = 0;
        for (Seg s : segs) {
            if (s.skipped) w += (int) Math.ceil(rtl(s.text, Theme.small.deriveFont(11f), g.getFontRenderContext()).getAdvance()) + 12;
            else w += fm.stringWidth(s.text) + (s.mark == null ? 0 : mk.stringWidth(s.mark) + 3);
        }
        return w;
    }

    private static int drawSegs(Graphics2D g, java.util.List<Seg> segs, FontMetrics fm, int x, int y, int h, int base) {
        Font mk = markFont();
        FontMetrics mm = g.getFontMetrics(mk);
        for (Seg s : segs) {
            if (s.skipped) {   // "לא נבדק" — פיל אפור מקווקו
                TextLayout tl = rtl(s.text, Theme.small.deriveFont(11f), g.getFontRenderContext());
                int pw = (int) Math.ceil(tl.getAdvance()) + 10;
                Graphics2D d = (Graphics2D) g.create();
                d.setColor(new Color(0xF3F4F6));
                d.fillRoundRect(x + 1, y + 2, pw, h - 4, h - 4, h - 4);
                d.setColor(SKIP);
                d.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] { 3f, 2f }, 0f));
                d.drawRoundRect(x + 1, y + 2, pw, h - 4, h - 4, h - 4);
                d.dispose();
                g.setColor(new Color(0x6B7280));
                tl.draw(g, x + 6, y + (h + tl.getAscent() - tl.getDescent()) / 2f);
                x += pw + 2;
                continue;
            }
            g.setFont(fm.getFont());
            g.setColor(s.color);
            g.drawString(s.text, x, base);
            x += fm.stringWidth(s.text);
            if (s.mark != null) {
                g.setFont(mk);
                g.drawString(s.mark, x + 2, base);
                x += mm.stringWidth(s.mark) + 3;
            }
        }
        return x;
    }

    /** קו מתחת לחלק בקוד (גם כשהתנאי ממשיך כמה שורות) */
    @SuppressWarnings("deprecation")
    private static void underline(Graphics2D g, JTextComponent c, Block b, Part p) {
        if (b.condLine < 0) return;
        FontMetrics fm = c.getFontMetrics(c.getFont());
        java.util.Map<Integer, int[]> rows = new java.util.LinkedHashMap<Integer, int[]>();   // y של השורה → {x1, x2, y של הקו}
        java.util.Map<Integer, String> texts = new java.util.HashMap<Integer, String>();
        for (int q = p.from; q < p.to; q++) {
            char ch = b.cond.charAt(q);
            if (ch == '\n' || ch == '\r') continue;
            int[] lc = b.at(q);
            int off = docOffset(c, lc[0], lc[1], texts);
            if (off < 0) continue;
            try {
                Rectangle a = c.modelToView(off), e = c.modelToView(off + 1);
                if (a == null) continue;
                int x1 = a.x, x2 = e != null && e.y == a.y ? e.x : a.x + fm.charWidth(ch);
                int[] row = rows.get(a.y);
                if (row == null) rows.put(a.y, new int[] { Math.min(x1, x2), Math.max(x1, x2), a.y + Math.min(a.height - 2, fm.getAscent() + 2) });
                else {
                    row[0] = Math.min(row[0], Math.min(x1, x2));
                    row[1] = Math.max(row[1], Math.max(x1, x2));
                }
            } catch (BadLocationException ex) {
                // בלי
            }
        }
        if (rows.isEmpty()) return;
        Color col = color(p);
        Graphics2D u = (Graphics2D) g.create();
        u.setColor(col);
        if (p.state == CondViz.SKIPPED) u.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] { 4f, 3f }, 0f));
        else if (p.state == CondViz.UNSURE || p.value == null) u.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] { 0.5f, 3f }, 0f));
        else u.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // רק הקו — בלי סימן על הקוד עצמו (הוא היה מסתיר את התו שאחרי החלק, למשל את ה-) של התנאי); ✓ / ✗ של כל חלק — בשבב שמימין
        for (int[] row : rows.values()) u.drawLine(row[0] + 1, row[2], row[1] - 1, row[2]);
        u.dispose();
    }

    /** המיקום במסמך של התו col (מ-0) בשורה line של הקובץ — בשורה שבחלון יש גם סימני כיוון (LRM / RLE / PDF) שאין בקובץ */
    private static int docOffset(JTextComponent c, int line, int col, java.util.Map<Integer, String> texts) {
        Element root = c.getDocument().getDefaultRootElement();
        if (line < 1 || line > root.getElementCount()) return -1;
        Element e = root.getElement(line - 1);
        String t = texts.get(line);
        if (t == null) {
            try {
                t = c.getDocument().getText(e.getStartOffset(), e.getEndOffset() - e.getStartOffset());
            } catch (BadLocationException ex) {
                return -1;
            }
            texts.put(line, t);
        }
        int k = 0;
        for (int i = 0; i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == '‎' || ch == '‏' || ch >= '‪' && ch <= '‮') continue;
            if (k == col) return e.getStartOffset() + i;
            k++;
        }
        return -1;
    }

    /**
     * ההסבר (למה) — בשורה שמתחת לתנאי (או מעל, אם שם יש מקום), מיושר לימין של השבב ומחובר אליו בקו קטן.
     * אם אין מקום באף שורה — מתחת לתנאי, על הקוד (עם רקע).
     */
    private static void why(Graphics2D g, JTextComponent c, Check k, int chipX, int chipRight, java.util.Map<Integer, Integer> used) {
        Rectangle r = lineRect(c, k.block.line);
        Font f = Theme.small;
        TextLayout tl = rtl(k.why, f, g.getFontRenderContext());
        FontMetrics sm = g.getFontMetrics(f);
        String mark = resultMark(k);
        Color col = resultColor(k);
        int h = Math.min(r.height - 2, 20);
        int w = (int) Math.ceil(tl.getAdvance()) + sm.stringWidth(mark) + 24;
        Rectangle vis = c.getVisibleRect();
        int right = vis.x + vis.width - 10;
        int below = k.block.condLastLine() + 1, above = k.block.line - 1;
        int row = -1, x = 0;
        for (int alt : new int[] { below, above }) {
            Rectangle ra = lineRect(c, alt);
            if (ra == null || used.containsKey(alt)) continue;
            int min = lineEndX(c, alt) + 22;
            int xa = Math.max(min, Math.min(chipRight, right) - w);   // מיושר לימין של השבב
            if (xa + w > right) continue;
            row = alt;
            x = xa;
            break;
        }
        boolean over = row < 0;
        if (over) {   // אין מקום: מתחת לתנאי, על הקוד
            row = lineRect(c, below) != null ? below : above;
            if (lineRect(c, row) == null) return;
            x = Math.max(vis.x + 40, Math.min(chipRight, right) - w);
        }
        Rectangle ra = lineRect(c, row);
        int y = ra.y + (ra.height - h) / 2;
        used.put(row, Math.max(used.containsKey(row) ? used.get(row) : 0, x + w));
        // קו קטן מהשבב אל ההסבר
        int cx = Math.min(x + w, chipRight) - h / 2 - 2, cy1 = row > k.block.line ? y : y + h;
        int cy2 = row > k.block.line ? r.y + (r.height + Math.min(r.height - 2, 20)) / 2 + 2 : r.y + (r.height - Math.min(r.height - 2, 20)) / 2 - 2;
        Graphics2D d = (Graphics2D) g.create();
        d.setColor(col);
        d.setStroke(new BasicStroke(1.4f));
        d.drawLine(cx, cy1, cx, cy2);
        d.dispose();
        g.setColor(over ? Color.WHITE : new Color(255, 255, 255, 235));
        g.fillRoundRect(x, y, w, h, h, h);
        g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 30));
        g.fillRoundRect(x, y, w, h, h, h);
        g.setColor(col);
        g.drawRoundRect(x, y, w, h, h, h);
        g.setFont(f);
        int markX = x + w - 10 - sm.stringWidth(mark);
        g.drawString(mark, markX, y + (h + sm.getAscent() - sm.getDescent()) / 2);
        g.setColor(new Color(0x1F2937));
        tl.draw(g, markX - 6 - tl.getAdvance(), y + (h + tl.getAscent() - tl.getDescent()) / 2f);
    }

    // ------------------------------------------------------------ ענפים

    /** פס צבעוני משמאל לשורות הענף שרץ, ורקע בגוון שלו */
    private static void stripe(Graphics2D g, JTextComponent c, int first, int last, Color col) {
        Rectangle a = lineRect(c, first), b = lineRect(c, last);
        if (a == null || b == null) return;
        int y1 = a.y, y2 = b.y + b.height;
        g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 22));
        g.fillRect(0, y1, c.getWidth(), y2 - y1);
        g.setColor(col);
        g.fillRect(0, y1, 4, y2 - y1);
    }

    /** הענף שמדלגים עליו: מעומעם, עם קו מקווקו בצד */
    private static void dim(Graphics2D g, JTextComponent c, int first, int last) {
        if (first <= 0) return;
        Rectangle a = lineRect(c, first), b = lineRect(c, last);
        if (a == null || b == null) return;
        int y1 = a.y, y2 = b.y + b.height;
        Graphics2D d = (Graphics2D) g.create();
        d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.62f));
        d.setColor(Color.WHITE);
        d.fillRect(0, y1, c.getWidth(), y2 - y1);
        d.dispose();
        Graphics2D s = (Graphics2D) g.create();
        s.setColor(new Color(0xB0B7C3));
        s.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] { 4f, 4f }, 0f));
        s.drawLine(2, y1, 2, y2);
        s.dispose();
    }

    /** תגית קטנה מימין לשורה: סימן + טקסט בעברית */
    static void tag(Graphics2D g, JTextComponent c, int line, String mark, String text, Color col, java.util.Map<Integer, Integer> used) {
        Rectangle r = lineRect(c, line);
        if (r == null) return;
        Font f = Theme.small;
        TextLayout tl = rtl(text, f, g.getFontRenderContext());
        FontMetrics sm = g.getFontMetrics(f);
        int h = Math.min(r.height - 2, 20), y = r.y + (r.height - h) / 2;
        int w = (int) Math.ceil(tl.getAdvance()) + sm.stringWidth(mark) + 24;
        Integer after = used.get(line);
        int x = after == null ? place(c, line, r, w) : after + 8;
        int row = line;
        Rectangle vis = c.getVisibleRect();
        if (after != null && x + w > vis.x + vis.width - 10) {
            // אין מקום אחרי מה שכבר בשורה — בשורה שמתחת / מעל (אם יש שם מקום), עם קו קטן לשורה
            for (int alt : new int[] { line + 1, line - 1 }) {
                Rectangle ra = lineRect(c, alt);
                if (ra == null || used.containsKey(alt)) continue;
                int xa = Math.max(lineEndX(c, alt) + 22, Math.min(x, vis.x + vis.width - 10 - w));
                if (xa + w > vis.x + vis.width - 10) continue;
                int ya = ra.y + (ra.height - h) / 2;
                g.setColor(col);
                int cx = xa + h / 2;
                g.drawLine(cx, alt > line ? ya : ya + h, cx, r.y + r.height / 2);
                g.fillOval(cx - 2, r.y + r.height / 2 - 2, 4, 4);
                row = alt;
                x = xa;
                y = ya;
                break;
            }
        }
        used.put(row, x + w);
        g.setColor(new Color(255, 255, 255, 235));
        g.fillRoundRect(x, y, w, h, h, h);
        g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 30));
        g.fillRoundRect(x, y, w, h, h, h);
        g.setColor(col);
        g.drawRoundRect(x, y, w, h, h, h);
        g.setFont(f);
        int markX = x + w - 10 - sm.stringWidth(mark);
        g.drawString(mark, markX, y + (h + sm.getAscent() - sm.getDescent()) / 2);
        tl.draw(g, markX - 6 - tl.getAdvance(), y + (h + tl.getAscent() - tl.getDescent()) / 2f);
    }

    // ------------------------------------------------------------ עזרים

    /** איפה לצייר שבב ברוחב w בשורה: אחרי סוף הקוד, ואם אין מקום — צמוד לקצה הימני של מה שרואים */
    private static int place(JTextComponent c, int line, Rectangle r, int w) {
        int x = lineEndX(c, line) + 22;
        Rectangle vis = c.getVisibleRect();
        int right = vis.x + vis.width - 10;
        if (x + w > right) x = Math.max(vis.x + 40, right - w);
        return x;
    }

    @SuppressWarnings("deprecation")
    static Rectangle lineRect(JTextComponent c, int line) {
        Element root = c.getDocument().getDefaultRootElement();
        if (line < 1 || line > root.getElementCount()) return null;
        try {
            return c.modelToView(root.getElement(line - 1).getStartOffset());
        } catch (BadLocationException e) {
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    static int lineEndX(JTextComponent c, int line) {
        Element root = c.getDocument().getDefaultRootElement();
        Element e = root.getElement(line - 1);
        int x = 0;
        try {
            for (int o = e.getStartOffset(); o < e.getEndOffset() - 1; o++) {   // הסוף החזותי (שורה משמאל לימין, עם סימני כיוון)
                Rectangle r = c.modelToView(o + 1);
                if (r != null) x = Math.max(x, r.x);
            }
        } catch (BadLocationException ignored) {
            // נשאר מה שנמצא
        }
        return x;
    }

    /** טקסט בעברית (גם עם מספרים וקוד קצר) — מימין לשמאל */
    static TextLayout rtl(String s, Font f, FontRenderContext frc) {
        AttributedString as = new AttributedString(s);
        as.addAttribute(TextAttribute.FONT, f);
        as.addAttribute(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL);
        return new TextLayout(as.getIterator(), frc);
    }
}

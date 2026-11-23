package unit4.collectionsLib;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.TextLayout;
import java.awt.geom.Ellipse2D;
import java.util.List;
import java.util.Map;

import javax.swing.text.JTextComponent;

import unit4.collectionsLib.LoopViz.Loop;
import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.Step;

/**
 * "הלולאה לא רצה אף לא פעם אחת": לולאה (for / while / for-each) שהתנאי שלה לא התקיים כבר בבדיקה הראשונה, אז הגוף שלה
 * לא רץ אפילו פעם אחת — נורת אזהרה צהובה מהבהבת עם הודעה קטנה ליד שורת הלולאה בחלון הקוד.
 *
 * מזהים לפי מה שקרה בפועל (הצעדים שנרשמו), לא לפי חישוב התנאי: בצעד של שורת הלולאה — זו הכניסה הראשונה אליה
 * (השורה הקודמת באותה קריאה לא הייתה בגוף שלה), והשורה הבאה שרצה באותה קריאה היא כבר אחרי הלולאה ולא בגוף.
 * מראים את זה בצעד של שורת הלולאה ובצעד שאחריו (השורה שאחרי הלולאה) — כדי שגם כשההרצה עוד לא הספיקה להקליט את הצעד הבא
 * בזמן שהתלמיד היה בשורת הלולאה, האזהרה תופיע לפחות פעם אחת.
 *
 * לא מסמנים: do-while (תמיד רץ פעם אחת), ולולאה שהגוף שלה באותה שורה כמו הכותרת / גוף ריק (אי אפשר לדעת לפי שורות).
 * ולולאה פנימית שלא רצה בסיבוב מסוים של החיצונית — כן מסומנת (באותו סיבוב).
 * עובד ב-Java (שורה-שורה עם JDI) וב-C# (dsviz-cs) — בשתיהן הצעדים באותו סדר.
 */
final class LoopSkip {
    private LoopSkip() {}

    static final String TEXT = "הלולאה לא רצה אף לא פעם אחת";

    private static final int SCAN = 20000;

    /** שורת הלולאה שלא רצה ושמסמנים בצעד הזה, או -1 */
    static int at(int index) {
        try {
            Step s = Recorder.step(index);
            Loop l = skipped(index, s);
            if (l != null) return l.header;
            if (s == null || s.kind != Kind.LINE || s.where == null) return -1;
            int pi = previous(index, s);   // הצעד שאחרי: השורה שאחרי הלולאה
            if (pi >= 0) {
                Loop pl = skipped(pi, Recorder.step(pi));
                if (pl != null) return pl.header;
            }
        } catch (RuntimeException e) {
            // בלי אזהרה
        }
        return -1;
    }

    /** הלולאה שהצעד index הוא הכניסה הראשונה אליה, והגוף שלה לא רץ — או null */
    static Loop skipped(int index, Step s) {
        if (s == null || s.kind != Kind.LINE || s.where == null || s.where.file == null) return null;
        List<Loop> loops = LoopArrow.loops(s.where);
        Loop best = null;
        for (Loop l : loops) {
            if (l.kind.equals("do") || l.bodyStart <= l.header || l.end < l.bodyStart || l.header != s.where.line) continue;
            if (best == null || l.end - l.header < best.end - best.header) best = l;
        }
        if (best == null) return null;
        int pi = previous(index, s);
        if (pi >= 0) {   // חזרה מהגוף לבדיקת התנאי — הלולאה כן רצה
            int pl = Recorder.step(pi).where.line;
            if (pl > best.header && pl <= best.end) return null;
        }
        int ni = next(index, s);
        if (ni < 0) return null;   // הצעד הבא עוד לא נרשם (או שהקריאה נגמרה בשגיאה) — לא יודעים
        Step n = Recorder.step(ni);
        if (n.kind == Kind.LINE && n.where.line >= best.bodyStart && n.where.line <= best.end) return null;   // הגוף רץ
        return best;
    }

    /** האינדקס של הצעד הקודם שרץ באותה קריאה (אותו עומק, אותה פעולה), או -1 אם הקריאה רק התחילה */
    private static int previous(int index, Step s) {
        int depth = s.calls.size();
        for (int j = index - 1; j >= 0 && j > index - SCAN; j--) {
            Step t = Recorder.step(j);
            if (t == null || t.where == null) continue;
            int d = t.calls.size();
            if (d < depth) return -1;   // נכנסנו לפעולה רק עכשיו
            if (d > depth) continue;    // בתוך פעולה שנקראה מהשורה הקודמת
            if (t.kind == Kind.RETURN || t.kind == Kind.ERROR || t.kind == Kind.END) return -1;   // קריאה קודמת באותו עומק נגמרה
            if (t.kind != Kind.LINE) continue;
            if (!eq(t.where.method, s.where.method) || !eq(t.where.file, s.where.file)) return -1;
            return j;
        }
        return -1;
    }

    /** האינדקס של הצעד הבא שרץ באותה קריאה: שורה (באותה פעולה), או חזרה מהפעולה. -1 — עוד לא נרשם / נגמר בשגיאה */
    private static int next(int index, Step s) {
        int depth = s.calls.size(), known = Recorder.stepCount();
        for (int j = index + 1; j < known && j < index + SCAN; j++) {
            Step t = Recorder.step(j);
            if (t == null) return -1;
            int d = t.calls.size();
            if (d > depth) continue;    // בתוך פעולה שהתנאי קורא לה
            if (d < depth) return -1;
            if (t.kind == Kind.ERROR || t.kind == Kind.END) return -1;
            if (t.kind == Kind.RETURN) return j;
            if (t.kind != Kind.LINE) continue;
            return t.where != null && eq(t.where.method, s.where.method) && eq(t.where.file, s.where.file) ? j : -1;
        }
        return -1;
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /** בשביל הבדיקות (מצב טקסט, -Ddsviz.conditions=true): בכל צעד שבו מסומנת לולאה שלא רצה — איזו */
    static String dump() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Recorder.stepCount(); i++) {
            Step s = Recorder.step(i);
            Loop l = skipped(i, s);
            if (l != null) b.append("  [לולאה לא רצה #").append(i + 1).append(" שורה ").append(l.header).append(' ').append(l.kind).append(" | ").append(l.text).append("]\n");
        }
        return b.toString();
    }

    // ------------------------------------------------------------ הציור

    static final Color AMBER = new Color(0xB45309), YELLOW = new Color(0xFACC15), PILL = new Color(0xFEF3C7);

    /** 0..1: ההבהוב — מחזור של שנייה בערך; ב-1 הנורה דולקת בעוצמה מלאה, ב-0 כמעט כבויה */
    static float blink(long now) {
        return (float) (0.5 + 0.5 * Math.sin(now / 1000.0 * 2 * Math.PI));
    }

    /** התגית מימין לשורת הלולאה (באותו סגנון כמו התגית "הלולאה לא נגמרת"): נורה צהובה מהבהבת, והטקסט משמאלה */
    static void paint(Graphics2D g0, JTextComponent c, int line, Map<Integer, Integer> used, float blink) {
        Rectangle r = CondPaint.lineRect(c, line);
        if (r == null) return;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Font f = Theme.smallBold != null ? Theme.z(Theme.smallBold) : Theme.z(Theme.small);
        if (f == null) f = new Font(Font.DIALOG, Font.BOLD, 13);
        TextLayout tl = CondPaint.rtl(TEXT, f, g.getFontRenderContext());
        int h = Math.min(r.height - 2, (int) Math.round(20 * Theme.zoom)), y = r.y + (r.height - h) / 2;
        int lamp = Math.max(10, h - 4);   // הנורה
        int w = (int) Math.ceil(tl.getAdvance()) + lamp + 24;
        Integer after = used.get(line);
        Rectangle vis = c.getVisibleRect();
        int x = after != null ? after + 8 : CondPaint.lineEndX(c, line) + 22;
        int row = line;
        if (x + w > vis.x + vis.width - 10) {
            // אין מקום בשורת הלולאה (התנאי כבר תפס אותה, או שהקוד ארוך) — בשורה שמתחת / מעל, עם קו קטן אל שורת הלולאה
            boolean placed = false;
            for (int alt : new int[] { line + 1, line - 1 }) {
                Rectangle ra = CondPaint.lineRect(c, alt);
                if (ra == null || used.containsKey(alt)) continue;
                int xa = Math.max(CondPaint.lineEndX(c, alt) + 22, Math.min(x, vis.x + vis.width - 10 - w));
                if (xa + w > vis.x + vis.width - 10) continue;
                int ya = ra.y + (ra.height - h) / 2;
                g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 190));
                g.setStroke(new BasicStroke(1.4f));
                int cx = xa + h / 2;
                g.drawLine(cx, alt > line ? ya : ya + h, cx, r.y + r.height / 2);
                g.fillOval(cx - 2, r.y + r.height / 2 - 2, 5, 5);
                row = alt;
                x = xa;
                y = ya;
                placed = true;
                break;
            }
            if (!placed) x = Math.max(vis.x + 10, vis.x + vis.width - 10 - w);   // אין מקום בשום שורה: לפחות בתוך מה שרואים
        }
        used.put(row, x + w);
        // הרקע: צהוב בהיר שנושם קצת עם הנורה
        g.setColor(new Color(255, 255, 255, 245));
        g.fillRoundRect(x, y, w, h, h, h);
        g.setColor(new Color(PILL.getRed(), PILL.getGreen(), PILL.getBlue(), 190 + (int) (65 * blink)));
        g.fillRoundRect(x, y, w, h, h, h);
        g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 120 + (int) (110 * blink)));
        g.setStroke(new BasicStroke(1.4f));
        g.drawRoundRect(x, y, w, h, h, h);
        // הנורה מימין (קריאה מימין לשמאל): הילה זוהרת + גוף הנורה
        float cx = x + w - 9 - lamp / 2f, cy = y + h / 2f;
        float glow = lamp * (0.9f + 0.45f * blink);
        Rectangle rr = CondPaint.lineRect(c, row);
        Rectangle band = new Rectangle(vis.x, rr.y, vis.width, rr.height);   // ההילה לא גולשת לשורות שמעל ומתחת (לשורה שהתגית בה)
        java.awt.Shape clip = g.getClip();
        g.clipRect(band.x, band.y, band.width, band.height);
        g.setPaint(new RadialGradientPaint(cx, cy, glow, new float[] { 0f, 1f }, new Color[] { new Color(250, 204, 21, 40 + (int) (170 * blink)), new Color(250, 204, 21, 0) }));
        g.fill(new Ellipse2D.Float(cx - glow, cy - glow, glow * 2, glow * 2));
        g.setClip(clip);
        int a = 70 + (int) (185 * blink);
        g.setPaint(new RadialGradientPaint(cx - lamp * 0.12f, cy - lamp * 0.15f, lamp * 0.62f, new float[] { 0f, 1f },
                new Color[] { new Color(255, 238, 120, a), new Color(YELLOW.getRed(), YELLOW.getGreen(), YELLOW.getBlue(), a) }));
        g.fill(new Ellipse2D.Float(cx - lamp / 2f, cy - lamp / 2f, lamp, lamp));
        g.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 200));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Float(cx - lamp / 2f, cy - lamp / 2f, lamp, lamp));
        g.setColor(new Color(0x78350F));
        tl.draw(g, cx - lamp / 2f - 7 - tl.getAdvance(), y + (h + tl.getAscent() - tl.getDescent()) / 2f);
        g.dispose();
    }
}

package unit4.collectionsLib;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.text.JTextComponent;

/**
 * קלט מהמקלדת בחלון הקוד (ראו Keyboard): בשורה שקוראת קלט כשהתוכנית מחכה עכשיו — פס בצד ותגית מלאה
 * "⌨ מחכה לקלט מהמקלדת"; ובצעד של הקלט עצמו (גם כשחוזרים אחורה) — תגית "⌨ הוקלד: 5".
 * הצבע של קלט (טורקיז) זהה בקוד, בפלט ובפס ההקלדה. סמל המקלדת מצויר (חד בכל הגדלה, בלי תלות בגופן).
 */
final class InputPaint {
    private InputPaint() {}

    static final Color INPUT = new Color(0x0F766E);          // הצבע של קלט מהמקלדת
    static final Color INPUT_DARK = new Color(0x134E4A);
    static final Color SOFT = new Color(0xE6F7F3);           // רקע עדין (מה שהוקלד, בפלט)
    static final Color BAR_BG = new Color(0xF0FBF8);         // רקע פס ההקלדה
    static final Color BAR_LINE = new Color(0x2BB3A0);

    /** התוכנית מחכה עכשיו לקלט בשורה line. pulse = 0..1 — טבעת "נושמת" סביב התגית */
    static void waiting(Graphics2D g, JTextComponent c, int line, Map<Integer, Integer> used, float pulse) {
        Rectangle r = CondPaint.lineRect(c, line);
        if (r == null) return;
        g.setColor(INPUT);
        g.fillRect(0, r.y, 4, r.height);
        tag(g, c, line, r, "מחכה לקלט מהמקלדת", true, used, pulse);
    }

    /** הצעד של הקלט: מה שהוקלד בשורה line */
    static void typed(Graphics2D g, JTextComponent c, int line, String text, Map<Integer, Integer> used) {
        Rectangle r = CondPaint.lineRect(c, line);
        if (r == null) return;
        String t = text.length() > 40 ? text.substring(0, 39) + "…" : text;
        // LRM מסביב למה שהוקלד: "5 7" לא מתהפך ל-"7 5" בתוך המשפט בעברית
        tag(g, c, line, r, t.isEmpty() ? "הוקלדה שורה ריקה" : "הוקלד: \u200E" + t + "\u200E", false, used, 0);
    }

    /** הצעד שבו התוכנית קיבלה "אין עוד קלט" (אחרי "סיום הקלט") */
    static void ended(Graphics2D g, JTextComponent c, int line, Map<Integer, Integer> used) {
        Rectangle r = CondPaint.lineRect(c, line);
        if (r != null) tag(g, c, line, r, "סוף הקלט", false, used, 0);
    }

    private static void tag(Graphics2D g, JTextComponent c, int line, Rectangle r, String text, boolean filled,
            Map<Integer, Integer> used, float pulse) {
        Font f = Theme.smallBold;
        TextLayout tl = CondPaint.rtl(text, f, g.getFontRenderContext());
        int h = Math.min(r.height - 2, 20), y = r.y + (r.height - h) / 2;
        int iw = 15, w = (int) Math.ceil(tl.getAdvance()) + iw + 26;
        Integer after = used.get(line);
        int x = after == null ? CondPaint.lineEndX(c, line) + 22 : after + 8;
        Rectangle vis = c.getVisibleRect();
        int right = vis.x + vis.width - 10;
        if (x + w > right) x = Math.max(after == null ? vis.x + 40 : after + 8, right - w);   // צמוד לקצה של מה שרואים
        used.put(line, x + w);
        Color fg;
        if (filled) {
            if (pulse > 0) {
                g.setColor(new Color(INPUT.getRed(), INPUT.getGreen(), INPUT.getBlue(), (int) (85 * pulse)));
                g.setStroke(new BasicStroke(3f));
                g.drawRoundRect(x - 3, y - 3, w + 6, h + 6, h + 6, h + 6);
            }
            g.setColor(INPUT);
            g.fillRoundRect(x, y, w, h, h, h);
            fg = Color.WHITE;
        } else {
            g.setColor(new Color(255, 255, 255, 235));
            g.fillRoundRect(x, y, w, h, h, h);
            g.setColor(SOFT);
            g.fillRoundRect(x, y, w, h, h, h);
            g.setColor(INPUT);
            g.setStroke(new BasicStroke(1f));
            g.drawRoundRect(x, y, w, h, h, h);
            fg = INPUT;
        }
        // מימין לשמאל: הסמל מימין, הטקסט משמאלו
        int ix = x + w - 10 - iw;
        keyboard(g, ix, y + (h - 10) / 2, 1f, fg);
        g.setColor(fg);
        tl.draw(g, ix - 6 - tl.getAdvance(), y + (h + tl.getAscent() - tl.getDescent()) / 2f);
    }

    /** סמל מקלדת: מסגרת מעוגלת, שתי שורות מקשים ומקש רווח. בגודל 15x10 כפול scale */
    static void keyboard(Graphics2D g0, double x, double y, float scale, Color col) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(x, y);
        g.scale(scale, scale);
        g.setColor(col);
        g.setStroke(new BasicStroke(1.3f));
        g.draw(new RoundRectangle2D.Double(0.65, 0.65, 13.7, 8.7, 3, 3));
        for (int i = 0; i < 4; i++) g.fill(new Rectangle2D.Double(2.6 + i * 2.6, 2.6, 1.5, 1.4));
        for (int i = 0; i < 3; i++) g.fill(new Rectangle2D.Double(3.9 + i * 2.6, 4.6, 1.5, 1.4));
        g.fill(new Rectangle2D.Double(4.2, 6.6, 6.6, 1.3));
        g.dispose();
    }

    /** סמל המקלדת כ-Icon (לפס ההקלדה) */
    static Icon icon(final float scale, final Color col) {
        return new Icon() {
            public void paintIcon(Component comp, Graphics g, int x, int y) {
                keyboard((Graphics2D) g, x, y + 0.5, scale, col);
            }
            public int getIconWidth() { return (int) Math.ceil(15 * scale); }
            public int getIconHeight() { return (int) Math.ceil(10 * scale) + 1; }
        };
    }
}

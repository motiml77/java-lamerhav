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

/**
 * מצייר בקוד את מה ש-CondViz מצא:
 * - בשורת התנאי, מימין לקוד: המשתנים בקטן (x = 7), התנאי עם הערכים (7 % 2 == 0), ותגית צבעונית — ✓ מתקיים / ✗ לא מתקיים, ולאן ממשיכים;
 * - בזמן שרצים בתוך ענף: פס צבעוני ליד הענף שרץ (ירוק = if, סגול = else) עם תגית, והענף שמדלגים עליו — מעומעם.
 */
final class CondPaint {
    private CondPaint() {}

    static final Color TRUE = new Color(0x15803D), FALSE = new Color(0xDC2626), UNKNOWN = new Color(0x6B7280);
    static final Color THEN = new Color(0x16A34A), ELSE = new Color(0x7C3AED);
    private static final Color PILL_BG = new Color(0xEEF2FF), PILL_LINE = new Color(0xC7D2FE), PILL_TEXT = new Color(0x1E293B);
    private static final Color VALUES = new Color(0x475569);

    static void paint(Graphics2D g, JTextComponent c, CondViz.Marks m) {
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
            if (m.check == null || m.check.block.line != tagLine) tag(g, c, tagLine, inner.inElse ? "✗" : "✓", CondViz.note(inner), col);
        }
        if (m.check != null) check(g, c, m.check);
    }

    // ------------------------------------------------------------ שורת התנאי

    private static void check(Graphics2D g, JTextComponent c, Check k) {
        Rectangle r = lineRect(c, k.block.line);
        if (r == null) return;
        Font small = Theme.small, mono = Theme.mono.deriveFont(Theme.mono.getSize2D() - 1.5f);
        FontRenderContext frc = g.getFontRenderContext();
        int h = Math.min(r.height - 2, 20), y = r.y + (r.height - h) / 2;
        // רוחב: הפילים של המשתנים, הערכים, והתגית
        FontMetrics fm = g.getFontMetrics(mono);
        int w = 0;
        for (String[] v : k.vars) w += fm.stringWidth(v[0] + " = " + v[1]) + 14 + 6;
        String vals = k.values.isEmpty() ? "" : k.values;
        if (!vals.isEmpty()) w += fm.stringWidth(vals) + 12 + fm.stringWidth("→ ") + 4;
        String label = k.result == null ? "בודקים…" : k.result ? "מתקיים" + (k.outcome.isEmpty() ? "" : " · " + k.outcome) : "לא מתקיים" + (k.outcome.isEmpty() ? "" : " · " + k.outcome);
        String mark = k.result == null ? "?" : k.result ? "✓" : "✗";
        TextLayout tl = rtl(label, Theme.smallBold != null ? Theme.smallBold : small, frc);
        int tagW = (int) Math.ceil(tl.getAdvance()) + g.getFontMetrics(small).stringWidth(mark) + 22;
        w += tagW;
        int x = place(c, k.block.line, r, w);

        // רקע עדין לכל השבב — כדי שיהיה קריא גם אם הוא עולה על קוד ארוך
        g.setColor(new Color(255, 255, 255, 235));
        g.fillRoundRect(x - 6, y - 2, w + 12, h + 4, 12, 12);
        g.setColor(new Color(0xE2E8F0));
        g.drawRoundRect(x - 6, y - 2, w + 12, h + 4, 12, 12);

        g.setFont(mono);
        int base = y + (h + fm.getAscent() - fm.getDescent()) / 2;
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
        if (!vals.isEmpty()) {
            g.setColor(VALUES);
            g.drawString("→ " + vals, x + 4, base);
            x += fm.stringWidth("→ " + vals) + 16;
        }
        Color col = k.result == null ? UNKNOWN : k.result ? TRUE : FALSE;
        g.setColor(col);
        g.fillRoundRect(x, y, tagW, h, h, h);
        g.setColor(Color.WHITE);
        // מימין לשמאל: הסימן (✓ / ✗) מימין, הטקסט משמאלו
        g.setFont(small);
        FontMetrics sm = g.getFontMetrics(small);
        int markX = x + tagW - 9 - sm.stringWidth(mark);
        g.drawString(mark, markX, y + (h + sm.getAscent() - sm.getDescent()) / 2);
        tl.draw(g, markX - 5 - tl.getAdvance(), y + (h + tl.getAscent() - tl.getDescent()) / 2f);
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
    private static void tag(Graphics2D g, JTextComponent c, int line, String mark, String text, Color col) {
        Rectangle r = lineRect(c, line);
        if (r == null) return;
        Font f = Theme.small;
        TextLayout tl = rtl(text, f, g.getFontRenderContext());
        FontMetrics sm = g.getFontMetrics(f);
        int h = Math.min(r.height - 2, 20), y = r.y + (r.height - h) / 2;
        int w = (int) Math.ceil(tl.getAdvance()) + sm.stringWidth(mark) + 24;
        int x = place(c, line, r, w);
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
    private static int lineEndX(JTextComponent c, int line) {
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
    private static TextLayout rtl(String s, Font f, FontRenderContext frc) {
        AttributedString as = new AttributedString(s);
        as.addAttribute(TextAttribute.FONT, f);
        as.addAttribute(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL);
        return new TextLayout(as.getIterator(), frc);
    }
}

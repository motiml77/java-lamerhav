package unit4.collectionsLib;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.TextLayout;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;

import unit4.collectionsLib.CallViz.Phase;
import unit4.collectionsLib.CallViz.State;
import unit4.collectionsLib.Model.Frame;

/**
 * מצייר את היציאה לפעולת עזר והחזרה ממנה (CallViz):
 * - בקוד: השורה שקראה "מחכה" (כתום), חץ מהשורה שקראה אל הכותרת של פעולת העזר (ובחזרה — חץ ירוק אל השורה שקראה),
 *   ותגיות קטנות: "קוראים ל-isEven()", "נכנסים עם x = 8", "מחזירה true", "isEven() החזירה true".
 * - Strip: פס מעל הקוד — המסלול (sumEven() ← isEven()) ומשפט שמסביר מה קורה עכשיו.
 */
final class CallPaint {
    private CallPaint() {}

    static final Color OUT = new Color(0x2563EB), WAIT = new Color(0xD97706), BACK = new Color(0x15803D);

    static Color color(Phase p) {
        switch (p) {
            case CALLING:
            case ENTERED:
                return OUT;
            case INSIDE:
                return WAIT;
            default:
                return BACK;
        }
    }

    // ------------------------------------------------------------ בקוד

    /** לפני הקוד והתנאים: הרקע של השורות שמחכות */
    static void background(Graphics2D g, JTextComponent c, State st, String fileKey) {
        if (st == null || st.phase == Phase.CALLING || st.phase == Phase.RETURNED) return;
        for (int i = 0; i < st.path.size() - 1; i++) {
            Frame f = st.path.get(i);
            if (!same(f, fileKey)) continue;
            Rectangle r = CondPaint.lineRect(c, f.line);
            if (r == null) continue;
            g.setColor(new Color(WAIT.getRed(), WAIT.getGreen(), WAIT.getBlue(), 26));
            g.fillRect(0, r.y, c.getWidth(), r.height);
            g.setColor(WAIT);
            g.fillRect(0, r.y, 4, r.height);
        }
    }

    /** אחרי הקוד והתנאים: החצים והתגיות */
    static void foreground(Graphics2D g, JTextComponent c, State st, String fileKey, Map<Integer, Integer> used) {
        if (st == null) return;
        int sig = same(st.callee, fileKey) ? CallViz.signatureLine(st.callee) : -1;
        String callee = CallViz.ltr(CallViz.name(st.callee));
        switch (st.phase) {
            case CALLING:
                if (same(st.caller, fileKey)) {
                    if (sig > 0) arrow(g, c, st.caller.line, sig, OUT, true);
                    CondPaint.tag(g, c, st.caller.line, "↘", "קוראים ל-" + callee, OUT, used);
                }
                break;
            case ENTERED:
            case INSIDE:
                if (same(st.caller, fileKey) && sig > 0) arrow(g, c, st.caller.line, sig, st.phase == Phase.ENTERED ? OUT : WAIT, true);
                if (sig > 0 && st.phase == Phase.ENTERED)
                    CondPaint.tag(g, c, sig, "↘", st.args.isEmpty() ? "נכנסים לפעולת העזר" : "נכנסים עם " + CallViz.ltr(st.args), OUT, used);
                waiting(g, c, st, fileKey, used);
                break;
            case RETURNING: {
                Frame here = st.path.get(st.path.size() - 1);
                if (same(st.caller, fileKey) && same(here, fileKey)) arrow(g, c, here.line, st.caller.line, BACK, false);
                if (same(here, fileKey))
                    CondPaint.tag(g, c, here.line, "↩", (st.voidReturn ? "מסתיימת" : "מחזירה " + CallViz.ltr(st.returned)) + " — חוזרים לשורה " + st.caller.line, BACK, used);
                waiting(g, c, st, fileKey, used);
                break;
            }
            default:   // RETURNED
                if (same(st.caller, fileKey))
                    CondPaint.tag(g, c, st.caller.line, "↩", st.voidReturn ? callee + " הסתיימה" : callee + " החזירה " + CallViz.ltr(st.returned), BACK, used);
        }
    }

    /** "מחכה ל-isEven()" בכל שורה שקראה ומחכה (כולן חוץ מהפעולה שרצה עכשיו) */
    private static void waiting(Graphics2D g, JTextComponent c, State st, String fileKey, Map<Integer, Integer> used) {
        for (int i = 0; i < st.path.size() - 1; i++) {
            Frame f = st.path.get(i);
            if (!same(f, fileKey)) continue;
            CondPaint.tag(g, c, f.line, "…", "מחכה ל-" + CallViz.ltr(CallViz.name(st.path.get(i + 1))), WAIT, used);
        }
    }

    /**
     * חץ בשוליים השמאליים של הקוד: מהשורה from אל השורה to (מתחילת הקוד בשורה, אל תחילת הקוד בשורה השנייה).
     * dashed = מקווקו (יציאה / המתנה); מלא = חזרה.
     */
    private static void arrow(Graphics2D g0, JTextComponent c, int from, int to, Color col, boolean dashed) {
        if (from == to) return;
        Rectangle a = CondPaint.lineRect(c, from), b = CondPaint.lineRect(c, to);
        if (a == null || b == null) return;
        int ya = a.y + a.height / 2, yb = b.y + b.height / 2;
        int xa = Math.max(14, textStart(c, from) - 4), xb = Math.max(14, textStart(c, to) - 4);
        int rail = 7;
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(col);
        g.setStroke(dashed ? new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] { 5f, 4f }, 0f)
                : new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D p = new Path2D.Float();
        int r = 6, dir = yb > ya ? 1 : -1;
        p.moveTo(xa, ya);
        p.lineTo(rail + r, ya);
        p.quadTo(rail, ya, rail, ya + dir * r);
        p.lineTo(rail, yb - dir * r);
        p.quadTo(rail, yb, rail + r, yb);
        p.lineTo(xb - 1, yb);
        g.draw(p);
        g.setStroke(new BasicStroke(1f));
        Path2D head = new Path2D.Float();
        head.moveTo(xb + 3, yb);
        head.lineTo(xb - 5, yb - 5);
        head.lineTo(xb - 5, yb + 5);
        head.closePath();
        g.fill(head);
        g.fillOval(xa - 3, ya - 3, 6, 6);
        g.dispose();
    }

    /** איפה מתחיל הקוד בשורה (אחרי הרווחים) */
    @SuppressWarnings("deprecation")
    private static int textStart(JTextComponent c, int line) {
        Element root = c.getDocument().getDefaultRootElement();
        if (line < 1 || line > root.getElementCount()) return 14;
        Element e = root.getElement(line - 1);
        try {
            String t = c.getDocument().getText(e.getStartOffset(), e.getEndOffset() - e.getStartOffset());
            int k = 0;
            while (k < t.length() && (t.charAt(k) == ' ' || t.charAt(k) == '\t' || t.charAt(k) == '‪' || t.charAt(k) == '‎')) k++;
            Rectangle r = c.modelToView(e.getStartOffset() + k);
            return r == null ? 14 : r.x;
        } catch (javax.swing.text.BadLocationException ex) {
            return 14;
        }
    }

    private static boolean same(Frame f, String fileKey) {
        return f != null && fileKey != null && (f.packagePath() + f.file).equals(fileKey);
    }

    // ------------------------------------------------------------ הפס מעל הקוד

    /** המסלול (מימין לשמאל: sumEven() ← isEven()) ומשפט על מה שקורה עכשיו. מוצג רק כשבהרצה יש קריאה לפעולת עזר */
    static final class Strip extends JComponent {
        private State st;
        private List<Frame> plain;   // כשאין עכשיו פעולת עזר: הפעולות שרצות (בלי main)

        Strip() {
            setOpaque(true);
            setPreferredSize(new Dimension(100, 54));
        }

        void set(State st, List<Frame> calls) {
            this.st = st;
            this.plain = st == null && calls != null && calls.size() >= 2 ? calls.subList(0, calls.size() - 1) : null;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g.setColor(new Color(0xF8FAFC));
            g.fillRect(0, 0, w, h);
            g.setColor(Theme.BORDER);
            g.drawLine(0, h - 1, w, h - 1);

            Font chipFont = Theme.mono.deriveFont(Theme.mono.getSize2D() - 1f);
            FontMetrics fm = g.getFontMetrics(chipFont);
            int x = w - 12, y = 6, ch = 22;
            // הכותרת הקטנה: "קריאות לפעולות:"
            TextLayout head = CondPaint.rtl("הפעולות שרצות:", Theme.small, g.getFontRenderContext());
            g.setColor(Theme.MUTED);
            x -= (int) Math.ceil(head.getAdvance());
            head.draw(g, x, y + (ch + head.getAscent() - head.getDescent()) / 2f);
            x -= 10;

            List<Frame> path = st != null ? st.path : plain;
            Color now = st != null ? color(st.phase) : Theme.MUTED;
            if (path != null) {
                for (int i = 0; i < path.size(); i++) {
                    Frame f = path.get(i);
                    boolean last = i == path.size() - 1;
                    boolean upcoming = st != null && st.phase == Phase.CALLING && last;
                    boolean waiting = st != null && !last && st.phase != Phase.CALLING && st.phase != Phase.RETURNED;
                    String text = CallViz.name(f) + (waiting ? "  · מחכה" : "");
                    int cw = fm.stringWidth(text) + 18;
                    x -= cw;
                    Color fill = last && st != null && !upcoming ? now : Color.WHITE;
                    Color line = waiting ? WAIT : upcoming ? OUT : last ? now : new Color(0xCBD5E1);
                    g.setColor(fill);
                    g.fillRoundRect(x, y, cw, ch, ch, ch);
                    g.setColor(line);
                    if (upcoming) g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[] { 4f, 3f }, 0f));
                    g.drawRoundRect(x, y, cw, ch, ch, ch);
                    g.setStroke(new BasicStroke(1f));
                    g.setFont(chipFont);
                    g.setColor(fill == Color.WHITE ? (waiting ? WAIT.darker() : Theme.TEXT) : Color.WHITE);
                    g.drawString(text, x + 9, y + (ch + fm.getAscent() - fm.getDescent()) / 2);
                    if (!last) {   // חץ שמאלה: מי שקרא ← למי שנקרא
                        int ax = x - 6, ay = y + ch / 2;
                        g.setColor(new Color(0x94A3B8));
                        g.setStroke(new BasicStroke(1.6f));
                        g.drawLine(ax, ay, ax - 14, ay);
                        g.drawLine(ax - 14, ay, ax - 9, ay - 4);
                        g.drawLine(ax - 14, ay, ax - 9, ay + 4);
                        g.setStroke(new BasicStroke(1f));
                        x -= 26;
                    }
                }
            }
            // המשפט
            String text = st != null ? CallViz.sentence(st) : plain != null ? "רצה הפעולה " + CallViz.ltr(CallViz.name(plain.get(0)))
                    + " — כשהיא תקרא לפעולת עזר, כאן יופיע לאן יצאנו ולאן חוזרים" : "main רצה";
            TextLayout tl = CondPaint.rtl((st != null ? icon(st.phase) + "  " : "") + text, st != null ? Theme.smallBold : Theme.small, g.getFontRenderContext());
            g.setColor(st != null ? now.darker() : Theme.MUTED);
            float tx = Math.max(8, w - 12 - tl.getAdvance());
            tl.draw(g, tx, y + ch + 6 + tl.getAscent());
            g.dispose();
        }

        private static String icon(Phase p) {
            switch (p) {
                case CALLING:
                case ENTERED:
                    return "↘";
                case INSIDE:
                    return "…";
                default:
                    return "↩";
            }
        }
    }
}

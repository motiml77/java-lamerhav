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
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import unit4.collectionsLib.Model.Frame;
import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.Step;

/**
 * מעקב רקורסיה: כל קריאה לפעולה היא "מסך" (כרטיס) נפרד, במדרגות —
 * מה היא קיבלה, המשתנים שלה, השורות שבוצעו בה (תנאי if → true/false, קריאות, return → הערך),
 * ומה המצב שלה (פועלת / מחכה לתשובה / החזירה ערך).
 * חץ כחול למטה = קריאה לפעולה, חץ ירוק חזרה למעלה = הערך שהוחזר.
 */
final class RecursionPanel extends JPanel {

    /** שורה שבוצעה בתוך קריאה */
    static final class Entry {
        int line;
        String code;
        Boolean cond;                                 // תנאי if / while: התוצאה (null = לא ידוע)
        String returned;                              // שורת return: הערך שהוחזר
        boolean returnedVoid;
        final List<Call> calls = new ArrayList<Call>();   // הקריאות שהשורה הזו פתחה
    }

    /** קריאה אחת לפעולה */
    static final class Call {
        int id;
        String method;
        int depth;
        Call parent;
        final List<Call> children = new ArrayList<Call>();
        final List<Entry> trail = new ArrayList<Entry>();
        List<String[]> params;   // המשתנים בשורה הראשונה של הקריאה = מה שהיא קיבלה
        List<String[]> vars;     // המשתנים בצעד האחרון שלה
        int line = -1;
        boolean done;
        String returned;         // null = void (או שעוד לא החזירה)
        int x, y, h;             // מיקום בציור
        boolean shown;           // main לא מוצג (הוא המעטפת), אלא אם ביקשו "גם main"
    }

    private static final int W = 300, INDENT = 38, GAP = 22, PAD = 18, HEAD = 32, VARS = 22, ROW = 20, FOOT = 28, ARC = 14;
    private static final int MAX_TRAIL = 6;

    private static final Color ACTIVE = new Color(0x2F6FED), ACTIVE_BG = new Color(0xE8F0FE);
    private static final Color WAIT = new Color(0xA9B4C6), WAIT_BG = new Color(0xF3F5F9);
    private static final Color DONE = new Color(0x2E9E5B), DONE_BG = new Color(0xE3F5E9);
    private static final Color FALSE = new Color(0xD14343), CALL = new Color(0x6B87C9);
    private static final Color INK = new Color(0x1D2433), MUTED = new Color(0x6B7385), CUR_ROW = new Color(0xFFF0B3);

    private static final Pattern COND = Pattern.compile("^\\s*(?:\\}\\s*)?(?:else\\s+)?(?:if|while)\\s*\\(");

    private final Font mono = Theme.mono.deriveFont(12.5f), monoBold = Theme.mono.deriveFont(Font.BOLD, 13f);
    private final Font small = Theme.mono.deriveFont(11.5f), smallBold = Theme.mono.deriveFont(Font.BOLD, 11.5f);
    private final Font ui = Theme.font(Theme.family, Font.PLAIN, 12), uiBold = Theme.font(Theme.boldFamily, Theme.boldStyle, 13);

    private final List<Call> order = new ArrayList<Call>();   // לפי סדר הקריאות
    private Call active;
    private boolean hideMain = true;
    private double zoom = 1;
    private int contentW, contentH;

    RecursionPanel() {
        setBackground(Color.WHITE);
    }

    /** true = בלי הכרטיס של main (בניית המבנים היא מעטפת) — המעקב מתחיל בפעולה שהתלמיד כתב */
    void setHideMain(boolean hide) {
        hideMain = hide;
    }

    /** האם בצעד הזה יש רקורסיה (אותה פעולה פעמיים במחסנית) */
    static boolean recursive(Step s) {
        for (int i = 0; i < s.calls.size(); i++)
            for (int j = i + 1; j < s.calls.size(); j++)
                if (s.calls.get(i).method.equals(s.calls.get(j).method)
                        && s.calls.get(i).className.equals(s.calls.get(j).className)) return true;
        return false;
    }

    // ------------------------------------------------------------ בניית עץ הקריאות

    /** בונה את עץ הקריאות מכל הצעדים עד הצעד הנוכחי */
    void setStep(int upTo) {
        order.clear();
        active = null;
        List<Call> stack = new ArrayList<Call>();
        int ids = 0;
        for (int i = 0; i <= upTo; i++) {
            Step s = Recorder.step(i);
            int d = s.calls.size();
            if (s.kind == Kind.END) {   // התוכנית הסתיימה — כל הקריאות שנשארו הסתיימו
                for (Call c : stack) c.done = true;
                stack.clear();
                continue;
            }
            if (d == 0) continue;
            // המחסנית של הצעד (מלמטה למעלה) מול הקריאות הפעילות שלנו
            int k = 0;
            while (k < stack.size() && k < d && s.calls.get(d - 1 - k).method.equals(stack.get(k).method)) k++;
            while (stack.size() > k) stack.remove(stack.size() - 1).done = true;   // יצאה בלי "חזרה" (חריגה)
            while (stack.size() < d) {
                Frame f = s.calls.get(d - 1 - stack.size());
                Call c = new Call();
                c.id = ++ids;
                c.method = f.method;
                c.depth = stack.size();
                c.parent = stack.isEmpty() ? null : stack.get(stack.size() - 1);
                if (c.parent != null) {
                    c.parent.children.add(c);
                    Entry at = last(c.parent);
                    if (at != null) at.calls.add(c);   // השורה בקורא שפתחה את הקריאה הזו
                }
                stack.add(c);
                c.shown = !(hideMain && c.depth == 0);
                if (c.shown) order.add(c);
            }
            Call top = stack.get(stack.size() - 1);
            if (s.where != null) top.line = s.where.line;
            if (s.kind == Kind.LINE) {
                if (s.vars != null) {
                    if (top.params == null) top.params = s.vars;
                    top.vars = s.vars;
                }
                addLine(top, s.where);
            }
            if (i == upTo) active = top;   // בצעד "חזרה" — הקריאה שמחזירה עכשיו
            if (s.kind == Kind.RETURN) {
                if (s.vars != null) top.vars = s.vars;
                top.done = true;
                top.returned = s.returned;
                Entry e = last(top);
                if (e != null && s.where != null && e.line == s.where.line) {
                    if (e.cond != null || COND.matcher(e.code).find()) e.cond = Boolean.TRUE;   // if (...) return x;
                    e.returned = s.returned;
                    e.returnedVoid = s.returned == null;
                }
                stack.remove(stack.size() - 1);
            }
        }
        layoutCalls();
        revalidate();
        repaint();
        final Call a = active;
        if (a != null) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    scrollRectToVisible(new Rectangle((int) (a.x * zoom) - 10, (int) (a.y * zoom) - 30,
                            (int) (W * zoom) + 20, (int) (a.h * zoom) + 60));
                }
            });
        }
    }

    private static Entry last(Call c) {
        return c.trail.isEmpty() ? null : c.trail.get(c.trail.size() - 1);
    }

    /** שורה חדשה בקריאה. אם השורה הקודמת הייתה תנאי — לפי השורה הזו יודעים אם הוא התקיים */
    private static void addLine(Call c, Frame where) {
        if (where == null) return;
        Entry prev = last(c);
        if (prev != null && prev.line == where.line) return;   // אותה שורה שוב (למשל חזרה מקריאה באמצע השורה)
        if (prev != null && prev.cond == null && COND.matcher(prev.code).find()) {
            String after = afterCondition(prev.code);
            if (after.isEmpty() || after.equals("{")) prev.cond = where.line == prev.line + 1;   // הגוף בשורות הבאות
            else if (after.startsWith("return")) prev.cond = Boolean.FALSE;   // אם היה מתקיים — הפעולה הייתה מחזירה כאן
        }
        Entry e = new Entry();
        e.line = where.line;
        String code = SourceFiles.line(where);
        e.code = code == null ? "" : code.trim();
        c.trail.add(e);
    }

    /** מה שבא אחרי ה-) שסוגר את התנאי: "if (n <= 1) return 1;" → "return 1;" */
    private static String afterCondition(String code) {
        Matcher m = COND.matcher(code);
        if (!m.find()) return "";
        int depth = 1, i = m.end();
        for (; i < code.length() && depth > 0; i++) {
            char ch = code.charAt(i);
            if (ch == '(') depth++;
            else if (ch == ')') depth--;
        }
        return code.substring(Math.min(i, code.length())).trim();
    }

    private void layoutCalls() {
        int y = PAD;
        contentW = 0;
        for (Call c : order) {
            c.x = PAD + (c.depth - (hideMain ? 1 : 0)) * INDENT;
            c.y = y;
            int rows = Math.min(MAX_TRAIL, c.trail.size()) + (c.trail.size() > MAX_TRAIL ? 1 : 0);
            c.h = HEAD + VARS + Math.max(1, rows) * ROW + FOOT + 8;
            y += c.h + GAP;
            contentW = Math.max(contentW, c.x + W + 60);
        }
        contentH = y + PAD;
    }

    @Override
    public Dimension getPreferredSize() {
        zoom = computeZoom();
        return new Dimension((int) Math.ceil(Math.max(contentW, 300) * zoom), (int) Math.ceil(Math.max(contentH, 120) * zoom));
    }

    private double computeZoom() {
        int avail = getParent() != null && getParent().getWidth() > 100 ? getParent().getWidth() - 4 : 600;
        return contentW <= 0 ? 1 : Math.max(0.6, Math.min(1, avail / (double) contentW));
    }

    // ------------------------------------------------------------ ציור

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        zoom = computeZoom();
        g.scale(zoom, zoom);
        if (order.isEmpty()) {
            g.setColor(MUTED);
            g.setFont(uiBold);
            g.drawString("עוד אין קריאות לפעולות", PAD, PAD + 20);
            g.dispose();
            return;
        }
        for (Call c : order) if (c.parent != null && c.parent.shown) callArrow(g, c.parent, c);
        for (Call c : order) if (c.parent != null && c.parent.shown && c.done) returnArrow(g, c.parent, c);
        for (Call c : order) card(g, c);
        g.dispose();
    }

    private void card(Graphics2D g, Call c) {
        boolean isActive = c == active, waiting = !c.done && !isActive;
        Color line = isActive ? ACTIVE : c.done ? DONE : WAIT, head = isActive ? ACTIVE_BG : c.done ? DONE_BG : WAIT_BG;
        RoundRectangle2D box = new RoundRectangle2D.Double(c.x, c.y, W, c.h, ARC, ARC);
        g.setColor(Color.WHITE);
        g.fill(box);
        Shape clip = g.getClip();
        g.clip(box);
        g.setColor(head);
        g.fillRect(c.x, c.y, W, HEAD);
        g.setClip(clip);
        g.setColor(line);
        g.setStroke(new BasicStroke(isActive ? 2.6f : 1.4f));
        g.draw(box);

        // מספר הקריאה בעיגול, ו-"solve(n = 3)"
        g.setColor(line);
        g.fillOval(c.x + 8, c.y + 7, 20, 18);
        g.setColor(Color.WHITE);
        center(g, String.valueOf(c.id), smallBold, c.x + 18, c.y + 16);
        g.setColor(INK);
        g.setFont(monoBold);
        g.drawString(fit(c.method + "(" + params(c) + ")", g.getFontMetrics(), W - 44), c.x + 36, c.y + 21);

        // המשתנים — בשורה אחת
        int y = c.y + HEAD + 15;
        g.setFont(small);
        g.setColor(MUTED);
        g.drawString(fit(varsLine(c), g.getFontMetrics(), W - 20), c.x + 10, y);
        g.setColor(new Color(0xEEF0F4));
        g.drawLine(c.x + 8, c.y + HEAD + VARS, c.x + W - 8, c.y + HEAD + VARS);

        // השורות שבוצעו בקריאה הזו
        y = c.y + HEAD + VARS + 15;
        int from = Math.max(0, c.trail.size() - MAX_TRAIL);
        if (from > 0) {
            g.setFont(ui);
            g.setColor(MUTED);
            String more = "(ועוד " + from + " שורות לפני)";
            g.drawString(more, c.x + W - 10 - g.getFontMetrics().stringWidth(more), y);
            y += ROW;
        }
        if (c.trail.isEmpty()) {
            g.setFont(ui);
            g.setColor(MUTED);
            String t = "עוד לא בוצעה אף שורה";
            g.drawString(t, c.x + W - 10 - g.getFontMetrics().stringWidth(t), y);
        }
        for (int i = from; i < c.trail.size(); i++, y += ROW) {
            Entry e = c.trail.get(i);
            boolean current = isActive && !c.done && i == c.trail.size() - 1;
            if (current) {
                g.setColor(CUR_ROW);
                g.fillRect(c.x + 3, y - 14, W - 6, ROW - 1);
            }
            trailRow(g, c, e, y);
        }

        // מצב הקריאה — בעברית, מיושר לימין
        int fy = c.y + c.h - 10;
        g.setColor(new Color(0xEEF0F4));
        g.drawLine(c.x + 8, c.y + c.h - FOOT, c.x + W - 8, c.y + c.h - FOOT);
        String status;
        Color sc;
        if (c.done && isActive) {
            status = c.returned != null ? "מחזירה עכשיו  " + c.returned : "מסתיימת עכשיו";
            sc = DONE;
        } else if (isActive) {
            status = "פועלת עכשיו" + (c.line > 0 ? "  ·  שורה " + c.line : "");
            sc = ACTIVE;
        } else if (waiting) {
            Call next = c.children.isEmpty() ? null : c.children.get(c.children.size() - 1);
            status = next != null ? "מחכה לתשובה מקריאה " + next.id : "מחכה";
            sc = MUTED;
        } else if (c.returned != null) {
            status = "החזירה  " + c.returned;
            sc = DONE;
        } else {
            status = "הסתיימה";
            sc = DONE;
        }
        g.setFont(uiBold);
        g.setColor(sc);
        FontMetrics sm = g.getFontMetrics();
        String st = fit(status, sm, W - 20);
        g.drawString(st, c.x + W - 10 - sm.stringWidth(st), fy);
    }

    /** שורה אחת ביומן: "7  if (n <= 1)" ומימין תגית: true / false / #3 → 6 / מחזירה 24 */
    private void trailRow(Graphics2D g, Call c, Entry e, int y) {
        String tag = null;
        Color tagColor = null;
        if (e.returned != null || e.returnedVoid) {
            tag = e.returned != null ? "→ " + e.returned : "סיום";
            tagColor = DONE;
            if (Boolean.TRUE.equals(e.cond)) tag = "true " + tag;
        } else if (e.cond != null) {
            tag = e.cond ? "true" : "false";
            tagColor = e.cond ? DONE : FALSE;
        } else if (!e.calls.isEmpty()) {
            StringBuilder b = new StringBuilder();
            for (Call k : e.calls) {
                if (b.length() > 0) b.append("  ");
                b.append("#").append(k.id).append(k.done && k.returned != null ? " → " + k.returned : k.done ? "" : " …");
            }
            tag = b.toString();
            tagColor = CALL;
        }
        int tagW = 0;
        if (tag != null) {
            g.setFont(smallBold);
            FontMetrics fm = g.getFontMetrics();
            tag = fit(tag, fm, 130);
            tagW = fm.stringWidth(tag) + 12;
            int tx = c.x + W - 8 - tagW;
            g.setColor(tagColor);
            g.fillRoundRect(tx, y - 13, tagW, 17, 17, 17);
            g.setColor(Color.WHITE);
            g.drawString(tag, tx + 6, y);
        }
        g.setFont(small);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(MUTED);
        String num = String.valueOf(e.line);
        g.drawString(num, c.x + 28 - fm.stringWidth(num), y);
        g.setColor(INK);
        g.drawString(fit(e.code, fm, W - 46 - tagW - (tag != null ? 6 : 0)), c.x + 34, y);
    }

    /** מה הקריאה קיבלה: המשתנים בשורה הראשונה שלה (בלי this) */
    private static String params(Call c) {
        if (c.params == null) return "";
        StringBuilder b = new StringBuilder();
        for (String[] p : c.params) {
            if (p[0].equals("this")) continue;
            b.append(b.length() == 0 ? "" : ", ").append(p[0]).append(" = ").append(shortValue(p[1]));
        }
        return b.toString();
    }

    /** כל המשתנים של הקריאה עכשיו, בשורה אחת: "n = 3   rest = 2" */
    private static String varsLine(Call c) {
        if (c.vars == null || c.vars.isEmpty()) return "(אין משתנים)";
        StringBuilder b = new StringBuilder();
        for (String[] v : c.vars) {
            if (v[0].equals("this")) continue;
            b.append(b.length() == 0 ? "" : "   ").append(v[0]).append(" = ").append(shortValue(v[1]));
        }
        return b.length() == 0 ? "(אין משתנים)" : b.toString();
    }

    /** "BinNode [5]" → "[5]" (החוליה / הצומת שהערך שלהם 5) — כדי שהכתוב יהיה קצר */
    private static String shortValue(String v) {
        if (v.startsWith("BinNode ")) return v.substring(8);
        if (v.startsWith("Node ")) return v.substring(5);
        return v;
    }

    // ------------------------------------------------------------ חיצים

    /** קריאה: מהכרטיס של הקורא, למטה, ואל הכרטיס החדש */
    private void callArrow(Graphics2D g, Call from, Call to) {
        int x = from.x + 18, y1 = from.y + from.h, y2 = to.y + 18;
        g.setColor(CALL);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Double p = new Path2D.Double();
        p.moveTo(x, y1);
        p.lineTo(x, y2);
        p.lineTo(to.x - 4, y2);
        g.draw(p);
        head(g, to.x - 2, y2, 0);
    }

    /** חזרה: מהכרטיס שסיים, ימינה ולמעלה, אל הקורא — עם הערך שהוחזר */
    private void returnArrow(Graphics2D g, Call to, Call from) {
        int sx = from.x + W, sy = from.y + from.h - 14;
        int rx = sx + 22, ty = to.y + to.h - 14;
        g.setColor(DONE);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] { 6f, 4f }, 0f));
        Path2D.Double p = new Path2D.Double();
        p.moveTo(sx, sy);
        p.lineTo(rx, sy);
        p.lineTo(rx, ty);
        p.lineTo(to.x + W + 4, ty);
        g.draw(p);
        g.setStroke(new BasicStroke(2f));
        head(g, to.x + W + 2, ty, Math.PI);
        if (from.returned != null) {
            g.setFont(monoBold);
            FontMetrics fm = g.getFontMetrics();
            String v = fit(from.returned, fm, 120);
            int w = fm.stringWidth(v) + 14, h = 20, px = rx - w / 2, py = sy - h - 4;
            g.setColor(DONE);
            g.fillRoundRect(px, py, w, h, h, h);
            g.setColor(Color.WHITE);
            g.drawString(v, px + 7, py + 15);
        }
    }

    private static void head(Graphics2D g, double x, double y, double angle) {
        Path2D.Double a = new Path2D.Double();
        a.moveTo(0, 0);
        a.lineTo(-9, -5);
        a.lineTo(-9, 5);
        a.closePath();
        Graphics2D h = (Graphics2D) g.create();
        h.translate(x, y);
        h.rotate(angle);
        h.fill(a);
        h.dispose();
    }

    // ------------------------------------------------------------ עזרים

    private static String fit(String s, FontMetrics fm, int width) {
        if (fm.stringWidth(s) <= width) return s;
        while (s.length() > 1 && fm.stringWidth(s + "…") > width) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private static void center(Graphics2D g, String s, Font f, int cx, int cy) {
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2, cy + (fm.getAscent() - fm.getDescent()) / 2);
    }
}

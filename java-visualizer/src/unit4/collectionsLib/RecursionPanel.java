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
        String method, cls;
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
    private List<Call> all = new ArrayList<Call>();            // כל הקריאות עד הצעד הנוכחי (גם main)
    private final List<Call> cards = new ArrayList<Call>();   // הכרטיסים שמצוירים (ברקורסיה כפולה — רק הדרך אל הקריאה הנוכחית)
    private RecursionKind kind;
    /** הפס שמעל המעקב: איזו רקורסיה זו, הסבר קצר, ואיפה אנחנו עכשיו */
    final Banner banner = new Banner();
    /** עץ הקריאות (רקורסיה כפולה / מרובה) — מתחת לפס, קבוע (לא נגלל עם הכרטיסים) */
    final TreeView tree = new TreeView();
    private int treeH, treeW;
    private static final Color[] METHOD_COLORS = { new Color(0x2F6FED), new Color(0x7C3AED), new Color(0x0E9F9A), new Color(0xD97706) };
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
        Call[] act = new Call[1];
        all = build(upTo, act);
        active = act[0];
        for (Call c : all) {
            c.shown = !(hideMain && c.depth == 0);
            if (c.shown) order.add(c);
        }
        kind = RecursionKind.of();
        layoutCalls();
        banner.revalidate();
        banner.repaint();
        tree.revalidate();
        tree.repaint();
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

    /** כל הקריאות (גם main) מהצעד הראשון עד upTo, לפי סדר הקריאות; active[0] = הקריאה של הצעד upTo */
    static List<Call> build(int upTo, Call[] active) {
        List<Call> res = new ArrayList<Call>();
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
                c.cls = f.className;
                c.depth = stack.size();
                c.parent = stack.isEmpty() ? null : stack.get(stack.size() - 1);
                if (c.parent != null) {
                    c.parent.children.add(c);
                    Entry at = last(c.parent);
                    if (at != null) at.calls.add(c);   // השורה בקורא שפתחה את הקריאה הזו
                }
                stack.add(c);
                res.add(c);
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
            if (i == upTo && active != null) active[0] = top;   // בצעד "חזרה" — הקריאה שמחזירה עכשיו
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
        return res;
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
            if (after.isEmpty() || after.equals("{")) prev.cond = where.line == bodyStart(where, prev.line);   // הגוף בשורות הבאות
            else if (after.startsWith("return")) prev.cond = Boolean.FALSE;   // אם היה מתקיים — הפעולה הייתה מחזירה כאן
        }
        Entry e = new Entry();
        e.line = where.line;
        String code = SourceFiles.line(where);
        e.code = code == null ? "" : code.trim();
        c.trail.add(e);
    }

    /** השורה הראשונה של הגוף אחרי תנאי: מדלגים על "{" לבד בשורה (סגנון C#), שורות ריקות והערות */
    private static int bodyStart(Frame where, int condLine) {
        List<String> lines = SourceFiles.lines(where);
        int n = condLine + 1;
        while (lines != null && n - 1 < lines.size()) {
            String t = lines.get(n - 1).trim();
            if (!(t.isEmpty() || t.equals("{") || t.startsWith("//"))) break;
            n++;
        }
        return n;
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

    /** ברקורסיה כפולה / מרובה: עץ קריאות קטן למעלה, ומתחתיו הכרטיסים של הדרך אל הקריאה הנוכחית בלבד */
    private boolean treeMode() {
        return kind != null && (kind.type == RecursionKind.Type.BRANCHING || kind.type == RecursionKind.Type.MUTUAL && kind.mutualBranching);
    }

    private void layoutCalls() {
        cards.clear();
        treeH = 0;
        contentW = 0;
        if (treeMode()) {
            layoutTree();
            java.util.Set<Call> path = new java.util.HashSet<Call>();
            for (Call b = active; b != null; b = b.parent) path.add(b);
            for (Call c : order) if (path.contains(c)) cards.add(c);
        } else {
            cards.addAll(order);
        }
        int y = PAD;
        for (Call c : cards) {
            c.x = PAD + (c.depth - (hideMain ? 1 : 0)) * INDENT;
            c.y = y;
            int rows = Math.min(MAX_TRAIL, c.trail.size()) + (c.trail.size() > MAX_TRAIL ? 1 : 0);
            c.h = HEAD + VARS + Math.max(1, rows) * ROW + FOOT + 8;
            y += c.h + GAP;
            contentW = Math.max(contentW, c.x + W + 60);
        }
        contentH = y + PAD;
    }

    // ------------------------------------------------------------ עץ הקריאות (רקורסיה כפולה)

    private static final int NODE_H = 24, LEVEL = 50, NODE_GAP = 10;
    private final java.util.Map<Call, Rectangle> nodes = new java.util.LinkedHashMap<Call, Rectangle>();

    /** "fib(3)" — הערכים בלבד, בלי שמות הפרמטרים (כדי שהעץ יישאר צר) */
    private static String nodeText(Call c) {
        StringBuilder b = new StringBuilder();
        if (c.params != null) for (String[] p : c.params) {
            if (p[0].equals("this")) continue;
            String v = shortValue(p[1]);
            if (v.length() > 10) v = v.substring(0, 9) + "…";
            b.append(b.length() == 0 ? "" : ",").append(v);
        }
        return c.method + "(" + b + ")";
    }

    private void layoutTree() {
        nodes.clear();
        List<Call> roots = new ArrayList<Call>();
        for (Call c : all) if (kind.in(c) && !kind.in(c.parent)) roots.add(c);
        if (roots.isEmpty()) return;
        FontMetrics fm = getFontMetrics(small);
        int[] next = { PAD };
        int levels = 0;
        for (Call r : roots) levels = Math.max(levels, placeNode(r, 0, next, fm));
        treeH = 34 + levels * LEVEL + 14;
        treeW = next[0] + PAD;
    }

    /** מיקום צומת: עלים משמאל לימין לפי הסדר, הורה באמצע הילדים. מחזיר כמה רמות יש מתחתיו */
    private int placeNode(Call c, int level, int[] next, FontMetrics fm) {
        int w = Math.max(44, fm.stringWidth(nodeText(c)) + 16);
        List<Call> kids = kind.kids(c);
        int levels = 1;
        int cx;
        if (kids.isEmpty()) {
            cx = next[0] + w / 2;
            next[0] += w + NODE_GAP;
        } else {
            for (Call k : kids) levels = Math.max(levels, 1 + placeNode(k, level + 1, next, fm));
            Rectangle a = nodes.get(kids.get(0)), b = nodes.get(kids.get(kids.size() - 1));
            cx = (a.x + a.width / 2 + b.x + b.width / 2) / 2;
            if (cx - w / 2 < PAD) cx = PAD + w / 2;
            next[0] = Math.max(next[0], cx + w / 2 + NODE_GAP);
        }
        nodes.put(c, new Rectangle(cx - w / 2, PAD + 26 + level * LEVEL, w, NODE_H));
        return levels;
    }

    private void drawTree(Graphics2D g) {
        if (nodes.isEmpty()) return;
        g.setFont(uiBold);
        g.setColor(MUTED);
        String t = "עץ הקריאות";
        g.drawString(t, PAD, PAD + 12);
        java.util.Set<Call> path = new java.util.HashSet<Call>();
        for (Call b = active; b != null; b = b.parent) path.add(b);
        for (java.util.Map.Entry<Call, Rectangle> e : nodes.entrySet()) {
            Call c = e.getKey();
            Rectangle r = e.getValue(), pr = nodes.get(c.parent);
            if (pr == null) continue;
            boolean onPath = path.contains(c);
            g.setColor(onPath ? ACTIVE : new Color(0xC9D1DE));
            g.setStroke(new BasicStroke(onPath ? 2.2f : 1.3f));
            g.drawLine(pr.x + pr.width / 2, pr.y + pr.height + (c.parent.done ? 0 : 0), r.x + r.width / 2, r.y);
        }
        for (java.util.Map.Entry<Call, Rectangle> e : nodes.entrySet()) {
            Call c = e.getKey();
            Rectangle r = e.getValue();
            boolean isActive = c == active, done = c.done && !isActive;
            Color line = isActive ? ACTIVE : done ? DONE : path.contains(c) ? ACTIVE : WAIT;
            Color fill = isActive ? ACTIVE_BG : done ? DONE_BG : WAIT_BG;
            g.setColor(fill);
            g.fillRoundRect(r.x, r.y, r.width, r.height, r.height, r.height);
            g.setColor(line);
            g.setStroke(new BasicStroke(isActive ? 2.4f : 1.3f));
            g.drawRoundRect(r.x, r.y, r.width, r.height, r.height, r.height);
            Color mc = methodColor(c);
            g.setColor(mc != null ? mc : INK);
            center(g, nodeText(c), small, r.x + r.width / 2, r.y + r.height / 2);
            if (c.done && c.returned != null) {   // הערך שהקריאה החזירה — מתחת לצומת
                g.setFont(smallBold);
                FontMetrics fm = g.getFontMetrics();
                String v = fit("= " + c.returned, fm, Math.max(r.width, 70));
                g.setColor(DONE);
                g.drawString(v, r.x + r.width / 2 - fm.stringWidth(v) / 2, r.y + r.height + 13);
            }
        }
    }

    /** ברקורסיה הדדית: צבע לכל פעולה (null — לא צריך) */
    private Color methodColor(Call c) {
        if (kind == null || kind.type != RecursionKind.Type.MUTUAL || !kind.in(c)) return null;
        int i = 0;
        for (String m : kind.methods) {
            if (m.equals(RecursionKind.key(c))) return METHOD_COLORS[i % METHOD_COLORS.length];
            i++;
        }
        return null;
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
        for (Call c : cards) if (c.parent != null && c.parent.shown && cards.contains(c.parent)) callArrow(g, c.parent, c);
        for (Call c : cards) if (c.parent != null && c.parent.shown && cards.contains(c.parent) && c.done) returnArrow(g, c.parent, c);
        for (Call c : cards) card(g, c);
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
        Color mc = methodColor(c);
        if (mc != null) {   // רקורסיה הדדית: פס בצבע של הפעולה
            Shape cl = g.getClip();
            g.clip(box);
            g.setColor(mc);
            g.fillRect(c.x, c.y, 6, c.h);
            g.setClip(cl);
        }
        g.setColor(line);
        g.setStroke(new BasicStroke(isActive ? 2.6f : 1.4f));
        g.draw(box);

        // מספר הקריאה בתווית "#3" (כמו בהפניות מהכרטיסים האחרים), ואחריו "solve(n = 3)"
        g.setFont(smallBold);
        int badgeW = g.getFontMetrics().stringWidth("#" + c.id) + 12;
        g.setColor(line);
        g.fillRoundRect(c.x + 8, c.y + 7, badgeW, 19, 19, 19);
        g.setColor(Color.WHITE);
        center(g, "#" + c.id, smallBold, c.x + 8 + badgeW / 2, c.y + 16);
        int baseW = 0;
        if (kind != null && kind.base(c)) {   // תנאי העצירה: הקריאה שלא קראה שוב
            g.setFont(Theme.font(Theme.boldFamily, Theme.boldStyle, 11));
            FontMetrics bm = g.getFontMetrics();
            String bt = "תנאי עצירה";
            baseW = bm.stringWidth(bt) + 14;
            int bx = c.x + W - 8 - baseW;
            g.setColor(new Color(0xFFF4D6));
            g.fillRoundRect(bx, c.y + 7, baseW, 19, 19, 19);
            g.setColor(new Color(0xB7791F));
            g.drawRoundRect(bx, c.y + 7, baseW, 19, 19, 19);
            g.drawString(bt, bx + 7, c.y + 21);
            baseW += 6;
        }
        g.setColor(mc != null ? mc : INK);
        g.setFont(monoBold);
        g.drawString(fit(c.method + "(" + params(c) + ")", g.getFontMetrics(), W - 28 - badgeW - baseW), c.x + 16 + badgeW, c.y + 21);

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
            status = next != null ? "מחכה לתשובה מקריאה \u200E#" + next.id : "מחכה";
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
            // return n * fact(n - 1): מה הקריאה החזירה, ומה השורה מחזירה בעזרתו — העבודה שנעשית בדרך חזרה
            if (!e.calls.isEmpty() && e.returned != null) {
                StringBuilder b = new StringBuilder();
                for (Call k : e.calls) if (k.done && k.returned != null) b.append(b.length() > 0 ? " " : "").append("#").append(k.id).append("→").append(k.returned);
                if (b.length() > 0) tag = b + " ⇒ " + e.returned;
            }
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
        drawCode(g, fit(e.code, fm, W - 46 - tagW - (tag != null ? 6 : 0)), c.x + 34, y, fm);
    }

    /** שורת קוד צבעונית: כל מקטע בצבע שלו (הגופן ברוחב קבוע, אז מתקדמים לפי מדידה) */
    private static void drawCode(Graphics2D g, String code, int x, int y, FontMetrics fm) {
        int pos = 0;
        g.setColor(Syntax.color(Syntax.PLAIN));
        for (int[] t : Syntax.tokens(code)) {
            if (t[0] > pos) { g.setColor(Syntax.color(Syntax.PLAIN)); g.drawString(code.substring(pos, t[0]), x + fm.stringWidth(code.substring(0, pos)), y); }
            g.setColor(Syntax.color(t[2]));
            g.drawString(code.substring(t[0], t[1]), x + fm.stringWidth(code.substring(0, t[0])), y);
            pos = t[1];
        }
        if (pos < code.length()) {
            g.setColor(Syntax.color(Syntax.PLAIN));
            g.drawString(code.substring(pos), x + fm.stringWidth(code.substring(0, pos)), y);
        }
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
            // הערך באמצע הקו האנכי של החזרה (ולא בפינה שלו), כדי שיהיה ברור שזה מה שעובר בקו הזה
            int w = fm.stringWidth(v) + 14, h = 20, px = rx - w / 2, py = (sy + ty) / 2 - h / 2;
            g.setColor(DONE);
            g.fillRoundRect(px, py, w, h, h, h);
            g.setColor(Color.WHITE);
            g.drawString(v, px + 7, py + 15);
            if (kind != null && kind.type == RecursionKind.Type.TAIL) {   // רקורסיית זנב: הערך עולה בלי שינוי
                g.setFont(ui);
                g.setColor(DONE);
                g.drawString("אותו ערך", px + w + 4, py + 15);
            }
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

    /** הכותרת מעל הכרטיסים: הפס (סוג הרקורסיה) ועץ הקריאות */
    javax.swing.JComponent header() {
        JPanel p = new JPanel(new java.awt.BorderLayout());
        p.setOpaque(false);
        p.add(banner, java.awt.BorderLayout.NORTH);
        p.add(tree, java.awt.BorderLayout.CENTER);
        return p;
    }

    /** עץ הקריאות, מוקטן כך שייכנס לרוחב (ולגובה של עד 300 נקודות) */
    final class TreeView extends JPanel {
        TreeView() {
            setOpaque(true);
            setBackground(Color.WHITE);
        }

        private double scale() {
            int w = getParent() != null && getParent().getWidth() > 100 ? getParent().getWidth() : 600;
            return treeW <= 0 ? 1 : Math.max(0.45, Math.min(1, Math.min((w - 8) / (double) treeW, 300.0 / Math.max(1, treeH))));
        }

        @Override
        public Dimension getPreferredSize() {
            if (!treeMode() || nodes.isEmpty()) return new Dimension(10, 0);
            return new Dimension(300, (int) Math.ceil(treeH * scale()) + 2);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (!treeMode() || nodes.isEmpty()) return;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            double sc = scale();
            int ox = (int) Math.max(0, (getWidth() - treeW * sc) / 2);   // באמצע
            g.translate(ox, 0);
            g.scale(sc, sc);
            drawTree(g);
            g.dispose();
            g0.setColor(new Color(0xE3E7EE));
            g0.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
        }
    }

    // ------------------------------------------------------------ הפס העליון

    /** איזו רקורסיה, הסבר במשפט, ואיפה אנחנו (ירידה / תנאי העצירה / חזרה) — מעל הכרטיסים, לא זז בגלילה */
    final class Banner extends JPanel {
        Banner() {
            setOpaque(true);
            setBackground(new Color(0xFBFCFE));
        }

        @Override
        public Dimension getPreferredSize() {
            if (kind == null || kind.type == RecursionKind.Type.NONE) return new Dimension(10, 0);
            return new Dimension(300, 58 + 17 * explainLines().size());
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (kind == null || kind.type == RecursionKind.Type.NONE) return;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int right = getWidth() - 12;
            // שורה 1: הסוג (תג), המבנה, ובהדדית — תג בצבע לכל פעולה
            int x = chip(g, kind.title(), new Color(0x1D2433), Color.WHITE, right, 8, uiBold);
            if (kind.structure != null) x = chip(g, "על " + kind.structure, new Color(0xEEF0F4), INK, x - 8, 8, RecursionPanel.this.ui);
            if (kind.type == RecursionKind.Type.MUTUAL) {
                int i = 0;
                for (String m : kind.methods) {
                    Color mc = METHOD_COLORS[i++ % METHOD_COLORS.length];
                    x = chip(g, "\u200E" + m.substring(m.indexOf('#') + 1) + "\u200E", mc, Color.WHITE, x - 8, 8, smallBold);
                }
            }
            // ההסבר (עד שתי שורות)
            g.setFont(RecursionPanel.this.ui);
            g.setColor(MUTED);
            FontMetrics fm = g.getFontMetrics();
            int y = 47;
            for (String ex : explainLines()) {
                g.drawString(ex, right - fm.stringWidth(ex), y);
                y += 17;
            }
            // איפה אנחנו עכשיו
            String ph = kind.phase(all, active);
            if (!ph.isEmpty()) {
                g.setFont(uiBold);
                fm = g.getFontMetrics();
                ph = fit(ph, fm, getWidth() - 24);
                g.setColor(ph.startsWith("■") ? new Color(0xB7791F) : ph.startsWith("↑") ? DONE : ACTIVE);
                g.drawString(ph, right - fm.stringWidth(ph), y + 2);
            }
            g.setColor(new Color(0xE3E7EE));
            g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            g.dispose();
        }

        /** ההסבר מחולק לשורות לפי הרוחב (עד שתיים) */
        private List<String> explainLines() {
            FontMetrics fm = getFontMetrics(RecursionPanel.this.ui);
            int width = Math.max(200, (getWidth() > 0 ? getWidth() : 520) - 24);
            List<String> res = new ArrayList<String>();
            StringBuilder line = new StringBuilder();
            for (String w : kind.explain().split(" ")) {
                String t = line.length() == 0 ? w : line + " " + w;
                if (fm.stringWidth(t) > width && line.length() > 0) {
                    res.add(line.toString());
                    line = new StringBuilder(w);
                } else {
                    line = new StringBuilder(t);
                }
            }
            if (line.length() > 0) res.add(line.toString());
            if (res.size() > 2) {
                String two = res.get(1);
                for (int i = 2; i < res.size(); i++) two += " " + res.get(i);
                res = new ArrayList<String>(res.subList(0, 1));
                res.add(fit(two, fm, width));
            }
            return res;
        }

        /** תג מעוגל שהקצה הימני שלו ב-right; מחזיר את הקצה השמאלי */
        private int chip(Graphics2D g, String text, Color bg, Color fg, int right, int y, Font f) {
            g.setFont(f);
            FontMetrics fm = g.getFontMetrics();
            int w = fm.stringWidth(text) + 18, h = 22;
            g.setColor(bg);
            g.fillRoundRect(right - w, y, w, h, h, h);
            g.setColor(fg);
            g.drawString(text, right - w + 9, y + 16);
            return right - w;
        }
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

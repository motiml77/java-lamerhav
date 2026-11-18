package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * תרשים מחלקות קטן בנוסח של שאלה בהורשה ופולימורפיזם (ציור מסוג "classes" — BankStore.Figure): קופסה לכל מחלקה — השם (בצבע של
 * המחלקה, כמו בהרצה הוויזואלית: אדום, סגול, טורקיז...), התכונות והפעולות — ומשולש ריק אצל מחלקת העל, כמו ב-UML.
 * המחלקות נקראות מהקוד שבתרגיל עצמו (Java או C#), כך שהתרשים תמיד מתאים לקוד ולשמות של השפה (getMilk / GetMilk).
 * מחלקת העל למעלה, והמחלקות שיורשות ממנה בשורה מתחתיה; מחלקה שלא בעץ הורשה (Person, Zoo) — בצד, בלי צבע.
 * הפריסה והציור לא תלויים ב-SWT או ב-Swing: Pen מודד טקסט ומצייר (BankClassCanvas ב-Eclipse, BankClassFigure ב-Visual Studio / VS Code).
 */
final class BankClasses {
    private BankClasses() {}

    /** מחלקה: שם, מחלקת העל (או null), התכונות והפעולות כמו שהן נראות בתרשים ("String name", "double getMilk()") */
    static final class Cls {
        String name, sup;
        boolean isAbstract, isInterface;
        final List<String> fields = new ArrayList<String>(), methods = new ArrayList<String>();
        /** פעולות מופשטות (באותו סדר כמו methods — לכתב נטוי) */
        final List<Boolean> abstracts = new ArrayList<Boolean>();
        // פריסה
        int x, y, w, h, color = -1, depth;
        final List<Cls> kids = new ArrayList<Cls>();
    }

    /** מה שצריך כדי לפרוס ולצייר: מדידת טקסט, וציור (צבעים — 0xRRGGBB) */
    interface Pen {
        int width(String s, boolean bold);
        int lineHeight();
        void box(int x, int y, int w, int h, int fill, int stroke);
        void line(int x1, int y1, int x2, int y2, int color);
        void triangle(int[] xs, int[] ys, int fill, int stroke);
        /** y — ראש השורה */
        void text(String s, int x, int y, boolean bold, boolean italic, int color);
    }

    /** הצבעים של המחלקות בעץ — כמו בהרצה הוויזואלית (Poly.PALETTE) */
    static final int[] PALETTE = { 0xc0392b, 0x7b3fb0, 0x0c8583, 0x1f6fb5, 0x1f8a4c, 0xc8650f, 0xb83b7a };
    static final int INK = 0x23272f, MUTED = 0x5d6470, LINE = 0x9aa1ad, CARD = 0xffffff, GREY = 0x6b7280;
    static final int PAD = 7, GAP_X = 18, GAP_Y = 30;

    // ------------------------------------------------------------------ קריאה מהקוד

    private static final Pattern HEAD = Pattern.compile(
            "\\b((?:abstract|public|sealed|static|final)\\s+)*(class|interface)\\s+(\\w+)(?:\\s*<[^>{]*>)?\\s*(?:extends\\s+(\\w+)|:\\s*(\\w+))?[^{]*\\{");
    private static final Pattern FIELD = Pattern.compile("^((?:private|protected|public|internal|static|final|readonly)\\s+)+([\\w<>\\[\\],.]+)\\s+(\\w+)\\s*(=[^;]*)?;");
    private static final Pattern METHOD = Pattern.compile(
            "^((?:private|protected|public|internal|static|final|abstract|virtual|override|sealed|new)\\s+)*([\\w<>\\[\\],.]+)\\s+(\\w+)\\s*\\(([^)]*)\\)");

    /**
     * המחלקות שבשמות names, מהקוד של כל מחלקה בתרגיל (codes: שם → קוד), לפי הסדר של names. מחלקה שאין לה קוד — קופסה עם השם בלבד.
     */
    static List<Cls> parse(List<String> names, Map<String, String> codes) {
        List<Cls> out = new ArrayList<Cls>();
        for (String n : names) {
            Cls c = new Cls();
            c.name = n;
            String code = codes.get(n);
            if (code != null) read(c, code);
            out.add(c);
        }
        return out;
    }

    private static void read(Cls c, String code) {
        String t = code.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\\n]*", " ").replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
        Matcher h = HEAD.matcher(t);
        if (!h.find()) return;
        c.isInterface = "interface".equals(h.group(2));
        c.isAbstract = h.group(0).matches("(?s).*\\babstract\\b.*");
        String sup = h.group(4) != null ? h.group(4) : h.group(5);
        if (sup != null && !sup.matches("I[A-Z]\\w*")) c.sup = sup;   // C#: ממשק (IComparable) — לא מחלקת על
        // רק מה שבגוף המחלקה עצמה (עומק 1), שורה אחרי שורה
        int depth = 0;
        StringBuilder line = new StringBuilder();
        for (int i = h.end(); i < t.length(); i++) {
            char ch = t.charAt(i);
            if (ch == '{') {
                if (depth == 0) member(c, line.toString().trim(), true);
                depth++;
                line.setLength(0);
                continue;
            }
            if (ch == '}') {
                if (depth == 0) break;
                depth--;
                line.setLength(0);
                continue;
            }
            if (depth > 0) continue;
            if (ch == ';') {
                member(c, (line + ";").trim(), false);
                line.setLength(0);
            } else line.append(ch == '\n' ? ' ' : ch);
        }
    }

    /** שורה בגוף המחלקה: תכונה ("String name;"), כותרת של פעולה (עם גוף), או פעולה מופשטת (בלי גוף, עם ;) */
    private static void member(Cls c, String s, boolean body) {
        s = s.replaceAll("\\s+", " ").replaceAll("^\\[[^\\]]*\\]\\s*", "");
        if (s.isEmpty()) return;
        Matcher m = METHOD.matcher(s);
        if (m.find() && !s.matches("^[^(]*=.*")) {
            String mods = m.group(1) == null ? "" : m.group(1), type = m.group(2), name = m.group(3);
            if (name.equals(c.name) || type.equals("new") || type.equals("return")) return;   // בנאי
            if (mods.contains("private") && !body) return;
            List<String> ps = new ArrayList<String>();
            for (String p : m.group(4).split(",")) {
                p = p.trim();
                if (p.isEmpty()) continue;
                String[] w = p.split("\\s+");
                ps.add(w.length >= 2 ? w[w.length - 2] : p);   // רק הטיפוס: getAnimal(int)
            }
            c.methods.add(type + " " + name + "(" + String.join(", ", ps) + ")");
            c.abstracts.add(mods.contains("abstract") || c.isInterface && !body);
            return;
        }
        Matcher f = FIELD.matcher(s);
        if (f.find() && !s.contains("(") && !(f.group(0).contains("static") && f.group(0).contains("final"))) c.fields.add(f.group(2) + " " + f.group(3));
    }

    // ------------------------------------------------------------------ פריסה

    /** מחזיר {רוחב, גובה} של התרשים כולו, ומציב את הקופסאות (x, y, w, h) */
    static int[] layout(List<Cls> all, Pen pen) {
        Map<String, Cls> by = new LinkedHashMap<String, Cls>();
        for (Cls c : all) {
            c.kids.clear();
            c.color = -1;
            by.put(c.name, c);
        }
        List<Cls> roots = new ArrayList<Cls>(), loose = new ArrayList<Cls>();
        for (Cls c : all) {
            Cls p = c.sup == null ? null : by.get(c.sup);
            if (p != null) p.kids.add(c);
            else roots.add(c);
        }
        // צבע — רק למחלקות שבעץ הורשה (יש להן מחלקת על או מחלקות שיורשות), לפי הסדר: קודם השורשים, ואז הילדים
        List<Cls> order = new ArrayList<Cls>();
        for (Cls r : roots) if (!r.kids.isEmpty()) order.add(r);
        for (int i = 0; i < order.size(); i++) order.addAll(order.get(i).kids);
        for (int i = 0; i < order.size(); i++) order.get(i).color = PALETTE[i % PALETTE.length];
        for (Cls r : roots) if (r.kids.isEmpty()) loose.add(r);
        roots.removeAll(loose);
        int lh = pen.lineHeight();
        for (Cls c : all) {
            int w = pen.width(title(c), true);
            if (c.isAbstract || c.isInterface) w = Math.max(w, pen.width(tag(c), false));
            for (String s : c.fields) w = Math.max(w, pen.width(s, false));
            for (String s : c.methods) w = Math.max(w, pen.width(s, false));
            c.w = w + 2 * PAD;
            int rows = 1 + (c.isAbstract || c.isInterface ? 1 : 0);
            c.h = rows * lh + PAD + (c.fields.isEmpty() ? 0 : c.fields.size() * lh + PAD) + (c.methods.isEmpty() ? 0 : c.methods.size() * lh + PAD) + PAD / 2;
        }
        // גובה כל רמה — הקופסה הגבוהה ביותר בה
        List<Integer> levelH = new ArrayList<Integer>();
        for (Cls r : roots) depth(r, 0, levelH);
        int x = 0;
        for (Cls r : roots) x = place(r, x, levelH) + GAP_X * 2;
        int width = Math.max(0, x - GAP_X * 2), height = 0;
        for (int i = 0; i < levelH.size(); i++) height += levelH.get(i) + (i > 0 ? GAP_Y : 0);
        // מחלקות בלי הורשה — בצד, בטור מלמעלה
        int lx = width > 0 ? width + GAP_X * 2 : 0, ly = 0, lw = 0;
        for (Cls c : loose) {
            c.x = lx;
            c.y = ly;
            ly += c.h + GAP_X;
            lw = Math.max(lw, c.w);
        }
        if (!loose.isEmpty()) {
            width = lx + lw;
            height = Math.max(height, ly - GAP_X);
        }
        return new int[] { width + 2, height + 2 };
    }

    private static void depth(Cls c, int d, List<Integer> levelH) {
        c.depth = d;
        while (levelH.size() <= d) levelH.add(0);
        levelH.set(d, Math.max(levelH.get(d), c.h));
        for (Cls k : c.kids) depth(k, d + 1, levelH);
    }

    /** מציב את התת-עץ מ-x; מחזיר את הקצה הימני שלו */
    private static int place(Cls c, int x, List<Integer> levelH) {
        int y = 0;
        for (int i = 0; i < c.depth; i++) y += levelH.get(i) + GAP_Y;
        c.y = y;
        if (c.kids.isEmpty()) {
            c.x = x;
            return x + c.w;
        }
        int kx = x;
        for (Cls k : c.kids) kx = place(k, kx, levelH) + GAP_X;
        int kidsRight = kx - GAP_X;
        int kidsW = kidsRight - x;
        if (kidsW >= c.w) {
            c.x = x + (kidsW - c.w) / 2;
            return kidsRight;
        }
        // ההורה רחב מהילדים — מזיזים אותם למרכז מתחתיו
        int shift = (c.w - kidsW) / 2;
        for (Cls k : c.kids) move(k, shift);
        c.x = x;
        return x + c.w;
    }

    private static void move(Cls c, int dx) {
        c.x += dx;
        for (Cls k : c.kids) move(k, dx);
    }

    // ------------------------------------------------------------------ ציור

    static void paint(List<Cls> all, Pen pen) {
        int lh = pen.lineHeight();
        // קווי ההורשה: מכל ילד למעלה, קו אופקי משותף, ומשולש ריק מתחת להורה
        for (Cls c : all) {
            if (c.kids.isEmpty()) continue;
            int px = c.x + c.w / 2, py = c.y + c.h, tri = 8, busY = py + tri + (GAP_Y - tri) / 2;
            int minX = px, maxX = px;
            for (Cls k : c.kids) {
                int kx = k.x + k.w / 2;
                pen.line(kx, k.y, kx, busY, LINE);
                minX = Math.min(minX, kx);
                maxX = Math.max(maxX, kx);
            }
            pen.line(minX, busY, maxX, busY, LINE);
            pen.line(px, busY, px, py + tri, LINE);
            pen.triangle(new int[] { px, px - tri, px + tri }, new int[] { py, py + tri, py + tri }, CARD, LINE);
        }
        for (Cls c : all) {
            int color = c.color < 0 ? GREY : c.color;
            pen.box(c.x, c.y, c.w, c.h, CARD, color);
            int y = c.y + PAD / 2;
            if (c.isAbstract || c.isInterface) {
                pen.text(tag(c), c.x + (c.w - pen.width(tag(c), false)) / 2, y, false, true, MUTED);
                y += lh;
            }
            pen.text(title(c), c.x + (c.w - pen.width(title(c), true)) / 2, y, true, c.isAbstract, color);
            y += lh + PAD / 2;
            if (!c.fields.isEmpty()) {
                pen.line(c.x, y, c.x + c.w - 1, y, color);
                y += PAD / 2;
                for (String s : c.fields) {
                    pen.text(s, c.x + PAD, y, false, false, INK);
                    y += lh;
                }
                y += PAD / 2;
            }
            if (!c.methods.isEmpty()) {
                pen.line(c.x, y, c.x + c.w - 1, y, color);
                y += PAD / 2;
                for (int i = 0; i < c.methods.size(); i++) {
                    pen.text(c.methods.get(i), c.x + PAD, y, false, c.abstracts.get(i), INK);
                    y += lh;
                }
            }
        }
    }

    private static String title(Cls c) {
        return c.name;
    }

    private static String tag(Cls c) {
        return c.isInterface ? "«interface»" : "abstract";
    }

    /** תיאור טקסטואלי (לבדיקות): "Animal{String name | String getName(), String say()} Mammal<Animal{...}" */
    static String describe(List<Cls> all) {
        StringBuilder b = new StringBuilder();
        for (Cls c : all) {
            if (b.length() > 0) b.append(' ');
            b.append(c.name).append(c.sup != null ? "<" + c.sup : "").append(c.isAbstract ? "[abstract]" : "").append('{')
                    .append(String.join(", ", c.fields)).append(" | ").append(String.join(", ", c.methods)).append('}');
        }
        return b.toString();
    }
}

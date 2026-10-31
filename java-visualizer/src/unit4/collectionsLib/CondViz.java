package unit4.collectionsLib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import unit4.collectionsLib.Model.Frame;
import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.Step;

/**
 * תנאים בקוד, בחלון ההרצה:
 * - ברגע הבדיקה (השורה של if / else if / while / for / do-while): המשתנים שבתנאי והערכים שלהם, התנאי עם הערכים,
 *   והאם הוא מתקיים — ולאן ממשיכים (נכנסים ל-if / עוברים ל-else / מדלגים / ממשיכים לסיבוב / יוצאים מהלולאה).
 * - בזמן שרצים בתוך ענף: איזה ענף רץ (if או else), ואיזה ענף מדלגים עליו.
 * התשובה (true / false) נקבעת לפי השורה שרצה אחרי הבדיקה — כך היא נכונה גם לתנאי עם קריאות לפעולות (s.isEmpty()).
 * רק כשאי אפשר לדעת מזה (if (c) x++; בשורה אחת) — מחשבים מהערכים. עובד ב-Java וב-C# (אותו תחביר).
 */
final class CondViz {
    private CondViz() {}

    /** תנאי בקוד: if (גם else if), while, for, do-while. השורות מ-1; -1 = אין */
    static final class Block {
        String kind;                  // if / while / for / do
        int line;                     // שורת הבדיקה (ב-do: השורה של while בסוף)
        String cond = "";
        int thenFirst = -1, thenLast = -1;   // הגוף (ב-if: ענף ה-if; בלולאה: גוף הלולאה)
        int elseLine = -1, elseFirst = -1, elseLast = -1;
        boolean elseIsIf;            // else if
        Block chainParent;            // ה-if שזה ה-else if שלו (if (a) ... else if (b) — ל-b), או null
        int at = -1, elseIfAt = -1;   // מיקום המילה if / for... בקוד, ומיקום ה-if שאחרי ה-else
        String tail = "";             // הגוף, כשהוא באותה שורה עם התנאי (if (c) return x;)
        String forInit = "", forUpdate = "";   // for (init; cond; update)
        int end;                      // השורה האחרונה של כל המשפט (כולל ה-else)
        int condLine = -1, condCol;   // איפה מתחיל התנאי בקובץ (שורה מ-1, עמודה מ-0) — לסימון החלקים שלו בקוד

        /** השורה והעמודה בקובץ של התו ה-q בתנאי */
        int[] at(int q) {
            int l = condLine, col = condCol;
            for (int i = 0; i < q && i < cond.length(); i++) {
                if (cond.charAt(i) == '\n') {
                    l++;
                    col = 0;
                } else {
                    col++;
                }
            }
            return new int[] { l, col };
        }

        /** השורה האחרונה של התנאי (תנאי ארוך יכול להמשיך כמה שורות) */
        int condLastLine() {
            return condLine < 0 ? line : at(cond.length())[0];
        }

        boolean inThen(int l) { return thenFirst > 0 && l >= thenFirst && l <= thenLast; }
        boolean inElse(int l) { return elseFirst > 0 && l >= elseFirst && l <= elseLast; }
        boolean hasElse() { return elseLine > 0; }
    }

    /** בדיקה של תנאי בצעד הזה: המשתנים, התנאי עם הערכים, והתוצאה (null = עוד לא ידוע) */
    static final class Check {
        Block block;
        final List<String[]> vars = new ArrayList<String[]>();   // {שם, ערך}
        String values = "";      // התנאי עם הערכים במקום המשתנים ("" = אין מה להציב)
        Boolean result;
        String outcome = "";     // "נכנסים ל-if" / "עוברים ל-else" / "יוצאים מהלולאה"...
        Node tree;               // תנאי מורכב (&&, ||, !): המבנה שלו — או null (תנאי פשוט)
        final List<Part> parts = new ArrayList<Part>();   // תנאי מורכב: החלקים, לפי הסדר בקוד (ריק = תנאי פשוט)
        String why = "";         // תנאי מורכב: למה יצא כך (משפט קצר בעברית)
    }

    /** ענף שרץ עכשיו (השורה הנוכחית בתוך if או else של תנאי) */
    static final class Branch {
        Block block;
        boolean inElse;
        int condLine;          // השורה שבה נבדק התנאי (ל-else if: השורה שלו)
    }

    static final class Marks {
        Check check;                                     // בשורת התנאי — או null
        final List<Branch> branches = new ArrayList<Branch>();   // מהחיצוני לפנימי
    }

    private static final Map<String, List<Block>> parsed = new HashMap<String, List<Block>>();

    /** מה לסמן בקוד בצעד index (רק בהרצה שורה-שורה) */
    static Marks marks(int index) {
        Marks m = new Marks();
        Step s;
        try {
            s = Recorder.step(index);
        } catch (RuntimeException e) {
            return m;
        }
        if (s == null || s.where == null || s.vars == null) return m;
        List<Block> blocks = blocks(s.where);
        if (blocks.isEmpty()) return m;
        int line = s.where.line;
        for (Block b : blocks) {
            if (!b.kind.equals("if") || line == b.line || b.thenFirst == b.line && !b.hasElse()) continue;   // שורת הבדיקה / if בשורה אחת
            if (b.inThen(line) || b.inElse(line)) {
                Branch br = new Branch();
                br.block = b;
                br.inElse = b.inElse(line);
                br.condLine = b.line;
                m.branches.add(br);
            }
        }
        if (s.kind == Kind.LINE) {
            for (Block b : blocks) {
                if (b.line == line && !b.cond.isEmpty()) {
                    m.check = check(index, s, b);
                    break;
                }
            }
        }
        return m;
    }

    /**
     * ההסבר ליד הענף שרץ: איזה ענף ולמה. בשרשרת else if — אילו תנאים לא התקיימו ואיזה כן.
     * למשל: "רץ ה-else if — התנאי בשורה 6 לא התקיים, והתנאי בשורה 8 התקיים", "רץ ה-else — אף תנאי לא התקיים (שורות 6, 8)".
     */
    static String note(Branch br) {
        Block b = br.block;
        if (br.inElse && b.elseIsIf) return "התנאי בשורה " + b.line + " לא התקיים — עברנו ל-else if";
        List<Integer> failed = new ArrayList<Integer>();   // התנאים הקודמים בשרשרת — כולם לא התקיימו
        for (Block p = b.chainParent; p != null && failed.size() < 20; p = p.chainParent) failed.add(0, p.line);
        if (br.inElse) {
            if (failed.isEmpty()) return "רץ ה-else — כי התנאי בשורה " + b.line + " לא התקיים";
            failed.add(b.line);
            return "רץ ה-else — אף תנאי לא התקיים (שורות " + join(failed) + ")";
        }
        if (failed.isEmpty()) return "רץ ה-if — כי התנאי בשורה " + b.line + " התקיים";
        return "רץ ה-else if — " + (failed.size() == 1 ? "התנאי בשורה " + failed.get(0) + " לא התקיים"
                : "התנאים בשורות " + join(failed) + " לא התקיימו") + ", והתנאי בשורה " + b.line + " התקיים";
    }

    private static String join(List<Integer> lines) {
        StringBuilder b = new StringBuilder();
        for (int l : lines) b.append(b.length() > 0 ? ", " : "").append(l);
        return b.toString();
    }

    /** בשביל הבדיקות (מצב טקסט, -Ddsviz.conditions=true): מה מוצג בכל צעד */
    static String dump() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Recorder.stepCount(); i++) {
            Marks m = marks(i);
            Step s = Recorder.step(i);
            if (m.check != null) {
                Check c = m.check;
                b.append("  [תנאי #").append(i + 1).append(" שורה ").append(c.block.line).append(' ').append(c.block.kind).append(": ")
                        .append(c.block.cond).append(" | ").append(c.values).append(" | ");
                for (String[] v : c.vars) b.append(v[0]).append('=').append(v[1]).append(' ');
                if (!c.parts.isEmpty()) {   // תנאי מורכב: כל חלק — ✓ / ✗ / לא נבדק / ?
                    b.append("| חלקים: ");
                    for (int k = 0; k < c.parts.size(); k++) b.append(k == 0 ? "" : ", ").append(compact(c.parts.get(k).code)).append(' ').append(mark(c.parts.get(k)));
                    b.append(' ');
                }
                b.append("| ").append(c.result).append(' ').append(c.outcome).append("]\n");
                if (!c.why.isEmpty()) b.append("  [למה #").append(i + 1).append(": ").append(c.why.replace("\u200E", "")).append("]\n");
            }
            for (Branch br : m.branches) {
                b.append("  [ענף #").append(i + 1).append(" שורה ").append(s.where.line).append(": ")
                        .append(br.inElse ? "else" : "if").append(" של שורה ").append(br.condLine).append("]\n");
            }
            if (!m.branches.isEmpty()) b.append("  [הסבר #").append(i + 1).append(": ").append(note(m.branches.get(m.branches.size() - 1))).append("]\n");
        }
        return b.toString();
    }

    /** הסימן של חלק בתנאי מורכב: ✓ מתקיים, ✗ לא מתקיים, "לא נבדק" (קיצור), ? לא ידוע */
    static String mark(Part p) {
        if (p.state == SKIPPED) return "לא נבדק";
        if (p.state == UNSURE || p.value == null) return "?";
        return p.value ? "✓" : "✗";
    }

    // ------------------------------------------------------------ התוצאה של התנאי

    private static Check check(int index, Step s, Block b) {
        Check c = new Check();
        c.block = b;
        Map<String, Object> env = env(s.vars);
        c.result = observed(index, s, b);
        String loopVar = null;
        if (b.kind.equals("for")) {
            Matcher m = FOR_VAR.matcher(b.forInit);
            if (m.matches()) {
                loopVar = m.group(1);
                env = forState(index, s, b, env, loopVar, m.group(2), c.result);
            }
        }
        for (String id : identifiers(b.cond)) {
            if (id.equals(loopVar) && env.containsKey(id)) {
                c.vars.add(new String[] { id, shorten(String.valueOf(env.get(id))) });
                continue;
            }
            for (String[] v : s.vars) {
                if (v[0].equals(id)) {
                    c.vars.add(new String[] { id, shorten(v[1]) });
                    break;
                }
            }
        }
        c.values = values(b.cond, env);
        if (c.result == null) c.result = LoopViz.evalCondition(b.cond, env);
        if (c.result == null && !c.values.isEmpty()) c.result = LoopViz.evalCondition(c.values, new HashMap<String, Object>());   // !false, 7 % 2 == 0
        try {
            parts(index, s, c, env);
        } catch (RuntimeException e) {
            c.tree = null;   // בלי פירוק לחלקים — התנאי מוצג כמו תנאי פשוט
            c.parts.clear();
        }
        c.outcome = outcome(b, c.result);
        c.why = why(c);
        return c;
    }

    private static final Pattern FOR_VAR = Pattern.compile("(?s)\\s*(?:[A-Za-z_][\\w.<>\\[\\]]*\\s+)?([A-Za-z_]\\w*)\\s*=\\s*([^,]+?)\\s*");

    /**
     * בשורת ה-for הערכים שנרשמו בצעד הם לפעמים מלפני מה שקורה בה (ב-Java: בכניסה הראשונה — לפני int i = 0, ובחזרה — לפני ה-i++).
     * כדי שהשבב יראה מה שבאמת נבדק: ממשיכים לסיבוב — הערך מהשורה הבאה בגוף; כניסה ראשונה בלי המשתנה — ההצבה ההתחלתית;
     * יוצאים מהלולאה — העדכון (i++), אלא אם הערך כבר מסביר את התוצאה.
     */
    private static Map<String, Object> forState(int index, Step s, Block b, Map<String, Object> env, String v, String init, Boolean seen) {
        Map<String, Object> e = new HashMap<String, Object>(env);
        Step body = Boolean.TRUE.equals(seen) ? nextInFrame(index, s) : null;
        if (body != null && b.inThen(body.where.line) && body.where.line != b.line) {
            for (String[] x : body.vars) {
                if (!x[0].equals(v)) continue;
                Object val = LoopViz.value(x[1]);
                if (val != null) e.put(v, val);
                return e;
            }
        }
        if (!env.containsKey(v)) {
            Object x = eval(init, env);
            if (x != null) e.put(v, x);
            return e;
        }
        Object now = env.get(v);
        if (!(now instanceof Integer) || seen == null || seen.equals(LoopViz.evalCondition(b.cond, env))) return env;
        Integer next = null;
        for (String part : b.forUpdate.split(",")) {
            Integer r = update(part.replaceAll("\\s+", ""), v, (Integer) now, env);
            if (r != null) next = r;
        }
        if (next == null) return env;
        e.put(v, next);
        return seen.equals(LoopViz.evalCondition(b.cond, e)) ? e : env;
    }

    /** השורה הבאה שרצה באותה קריאה (null = הקריאה נגמרה קודם) */
    private static Step nextInFrame(int index, Step s) {
        int depth = s.calls.size(), n = Recorder.stepCount();
        for (int j = index + 1; j < n && j < index + 20000; j++) {
            Step t = Recorder.step(j);
            if (t.where == null) continue;
            int d = t.calls.size();
            if (d < depth || t.kind == Kind.END) return null;
            if (t.kind == Kind.LINE && d == depth && t.where.method.equals(s.where.method)) return t;
        }
        return null;
    }

    private static Integer update(String u, String v, int now, Map<String, Object> env) {
        if (u.equals(v + "++") || u.equals("++" + v)) return now + 1;
        if (u.equals(v + "--") || u.equals("--" + v)) return now - 1;
        if (!u.startsWith(v)) return null;
        String rest = u.substring(v.length());
        for (String op : new String[] { "+=", "-=", "*=", "/=", "=" }) {
            if (!rest.startsWith(op)) continue;
            Object x = eval(rest.substring(op.length()), env);
            if (!(x instanceof Integer)) return null;
            int y = (Integer) x;
            switch (op) {
                case "+=": return now + y;
                case "-=": return now - y;
                case "*=": return now * y;
                case "/=": return y == 0 ? null : now / y;
                default: return y;
            }
        }
        return null;
    }

    private static Object eval(String expr, Map<String, Object> env) {
        try {
            return StringViz.eval(expr.trim(), env);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final Pattern CALL = Pattern.compile("(?<![.\\w])([A-Za-z_]\\w*)\\s*\\.\\s*(isEmpty|hasNext|hasLeft|hasRight|IsEmpty|HasNext|HasLeft|HasRight)\\s*\\(\\s*\\)");

    /** התנאי עם הערכים: המשתנים, וגם s.isEmpty() → false ("" אם אין מה להציב) */
    static String values(String cond, Map<String, Object> env) {
        Matcher m = CALL.matcher(cond);
        StringBuffer b = new StringBuffer();
        boolean any = false;
        while (m.find()) {
            Object r = null;
            try {
                r = StringViz.eval(m.group(1) + "." + Character.toLowerCase(m.group(2).charAt(0)) + m.group(2).substring(1) + "()", env);
            } catch (RuntimeException e) {
                r = null;
            }
            m.appendReplacement(b, Matcher.quoteReplacement(r instanceof Boolean ? String.valueOf(r) : m.group()));
            any |= r instanceof Boolean;
        }
        m.appendTail(b);
        String withCalls = b.toString(), sub = LoopViz.substitute(withCalls, env);
        return !sub.isEmpty() ? sub : any ? withCalls : "";
    }

    /** לפי השורה הבאה שרצה באותה קריאה: האם התנאי התקיים. null = אי אפשר לדעת */
    static Boolean observed(int index, Step s, Block b) {
        int depth = s.calls.size(), n = Recorder.stepCount();
        int next = -1;
        boolean left = false;
        for (int j = index + 1; j < n && j < index + 20000; j++) {
            Step t = Recorder.step(j);
            if (t.where == null) continue;
            int d = t.calls.size();
            if (d < depth || t.kind == Kind.RETURN && d == depth || t.kind == Kind.END || t.kind == Kind.ERROR && d <= depth) {
                left = true;   // הקריאה נגמרה (return / שגיאה) לפני שורה נוספת
                break;
            }
            if (t.kind != Kind.LINE || d != depth || !t.where.method.equals(s.where.method)) continue;
            // אותה שורה (אחרי קריאה לפעולה בתוך התנאי — גם ב-while / for: חוזרים לשורה של התנאי, וזו עדיין אותה בדיקה)
            if (t.where.line == b.line && (b.kind.equals("if") || continuation(j, depth, b.line))) continue;
            next = t.where.line;
            break;
        }
        boolean bodyOnHeader = b.thenFirst == b.line;
        if (b.kind.equals("do")) {
            if (next < 0) return null;
            return b.inThen(next) && next < b.line;
        }
        if (b.kind.equals("while") || b.kind.equals("for")) {
            if (next < 0) return left ? Boolean.FALSE : null;
            if (b.inThen(next) && next != b.line) return Boolean.TRUE;
            return bodyOnHeader ? null : Boolean.FALSE;
        }
        // if
        if (next < 0) {
            if (left && bodyOnHeader && Pattern.compile("\\breturn\\b|\\bthrow\\b").matcher(b.tail).find()) return Boolean.TRUE;
            return null;
        }
        if (b.inThen(next) && !bodyOnHeader) return Boolean.TRUE;
        if (b.inElse(next)) return Boolean.FALSE;
        if (bodyOnHeader) return null;   // if (c) x++; — מהשורה הבאה אי אפשר לדעת
        return Boolean.FALSE;
    }

    static String outcome(Block b, Boolean r) {
        if (r == null) return "";
        switch (b.kind) {
            case "while":
            case "for":
                return r ? "ממשיכים לסיבוב" : "יוצאים מהלולאה";
            case "do":
                return r ? "חוזרים לסיבוב נוסף" : "יוצאים מהלולאה";
            default:
                String self = b.chainParent != null ? "else if" : "if";
                return r ? "נכנסים ל-" + self : b.hasElse() ? (b.elseIsIf ? "עוברים ל-else if" : "עוברים ל-else") : "מדלגים על ה-" + self;
        }
    }

    private static Map<String, Object> env(List<String[]> vars) {
        Map<String, Object> env = new HashMap<String, Object>();
        for (String[] v : vars) {
            Object val = LoopViz.value(v[1]);
            if (val == null) val = bareString(v[1]);   // C#: מחרוזת ברשימת המשתנים בלי מרכאות (x=abc, לא x="abc" כמו ב-Java)
            if (val != null) env.put(v[0], val);
        }
        return env;
    }

    private static final Pattern TYPE_PREFIX = Pattern.compile("^[A-Z]\\w*\\s");

    /**
     * טקסט שסביר שהוא מחרוזת (C#: בלי מרכאות) — לא מבנה [..] / {..}, ולא "Student ..." וכדומה.
     * אורך 1 — לא (עלול להיות char, שגם הוא ב-C# בלי מרכאות; עדיף "?" מאשר להתייחס אליו כמחרוזת בטעות)
     */
    private static String bareString(String text) {
        if (text == null || text.length() < 2 || text.equals("null")) return null;
        if (text.indexOf('[') >= 0 || text.indexOf('{') >= 0 || text.indexOf('}') >= 0 || text.indexOf(']') >= 0) return null;
        if (TYPE_PREFIX.matcher(text).find()) return null;
        return text;
    }

    private static final Pattern ID = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'|(?<![.\\w])([A-Za-z_]\\w*)(?!\\s*\\()");
    private static final java.util.Set<String> WORDS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "true", "false", "null", "new", "instanceof", "this", "int", "char", "double", "boolean", "String", "string", "is"));

    /** המשתנים שבתנאי, לפי הסדר, בלי כפילויות (לא שמות של פעולות, לא מילים שמורות) */
    static List<String> identifiers(String cond) {
        List<String> out = new ArrayList<String>();
        Matcher m = ID.matcher(cond);
        while (m.find()) {
            String id = m.group(1);
            if (id != null && !WORDS.contains(id) && !out.contains(id)) out.add(id);
        }
        return out;
    }

    static String shorten(String v) {
        String s = v.replaceAll("\\s+", " ").trim();
        return s.length() > 22 ? s.substring(0, 20) + "…" : s;
    }

    // ------------------------------------------------------------ תנאי מורכב: כל חלק בנפרד (&&, ||, !, סוגריים)

    /** מצב של חלק: נבדק / לא נבדק (החלקים שלפניו כבר קבעו — כמו ב-Java וב-C#) / לא ידוע אם נבדק */
    static final int CHECKED = 0, SKIPPED = 1, UNSURE = 2;

    /** חלק של תנאי מורכב — ביטוי בלי && / || ברמה העליונה שלו: x > 5, !s.isEmpty(), isEven(x), !(x < 0) */
    static final class Part {
        String code;              // הקוד של החלק
        int from, to;             // המיקום בתוך התנאי (Block.cond)
        String sub = "";          // עם הערכים (7 > 5); "" = אין מה להציב
        Boolean value;            // מתקיים / לא מתקיים; null = לא ידוע
        int state = CHECKED;
        // לחישוב
        Boolean env;              // הערך לפי המשתנים של הצעד (null = אי אפשר לחשב)
        String envSub = "";
        final List<int[]> calls = new ArrayList<int[]>();   // קריאות לפעולות בחלק, לפי סדר הסיום: {התחלה, סוף, תחילת השם, סוף השם}
        final List<String> names = new ArrayList<String>(); // שמות הפעולות (באותו סדר)
        final List<Integer> logged = new ArrayList<Integer>();   // אילו מהקריאות נרשמות בצעדים (מבנה / פעולה של התלמיד)
    }

    /** המבנה של תנאי מורכב: op = '&' / '|' / '!' עם ילדים, או 0 = חלק */
    static final class Node {
        char op;
        final List<Node> kids = new ArrayList<Node>();
        Part part;
        String code = "";         // הקוד (כולל הסוגריים, כשיש)
    }

    private static final int MAX_PARTS = 12;

    /** המבנה של התנאי: לפי הקדימויות (! לפני && לפני ||) וסוגריים, בלי לפרק בתוך קריאה לפעולה או מחרוזת. null = תנאי פשוט */
    static Node tree(String cond) {
        String bl = LoopViz.blank(cond);
        int depth = 0;
        for (int i = 0; i < bl.length(); i++) {   // ?: — בלי פירוק
            char ch = bl.charAt(i);
            if (ch == '(' || ch == '[' || ch == '{') depth++;
            else if (ch == ')' || ch == ']' || ch == '}') depth--;
            else if (ch == '?' && depth == 0) return null;
        }
        Node n = node(cond, bl, 0, cond.length(), 0);
        return n.op == 0 ? null : n;
    }

    private static Node node(String s, String bl, int from, int to, int depth) {
        if (depth > 40) throw new IllegalStateException("תנאי עמוק מדי");
        while (from < to && Character.isWhitespace(bl.charAt(from))) from++;
        while (to > from && Character.isWhitespace(bl.charAt(to - 1))) to--;
        if (from >= to) throw new IllegalStateException("חלק ריק");
        for (String op : new String[] { "||", "&&" }) {
            List<int[]> sp = split(bl, from, to, op);
            if (sp.size() < 2) continue;
            Node n = new Node();
            n.op = op.charAt(0);
            n.code = s.substring(from, to);
            for (int[] p : sp) n.kids.add(node(s, bl, p[0], p[1], depth + 1));
            return n;
        }
        if (bl.charAt(from) == '(' && close(bl, from) == to - 1) {
            Node in = node(s, bl, from + 1, to - 1, depth + 1);
            if (in.op != 0) in.code = s.substring(from, to);   // (a || b) — עם הסוגריים
            return in;   // (x > 0) — החלק בלי הסוגריים
        }
        if (bl.charAt(from) == '!' && (from + 1 >= to || bl.charAt(from + 1) != '=')) {
            Node in = node(s, bl, from + 1, to, depth + 1);
            if (in.op != 0) {   // !(a && b)
                Node n = new Node();
                n.op = '!';
                n.kids.add(in);
                n.code = s.substring(from, to);
                return n;
            }
        }
        Node n = new Node();
        n.code = s.substring(from, to);
        n.part = new Part();
        n.part.from = from;
        n.part.to = to;
        n.part.code = n.code;
        return n;
    }

    /** מפצל לפי op ברמה העליונה (לא בתוך סוגריים; המחרוזות כבר ריקות) */
    private static List<int[]> split(String bl, int from, int to, String op) {
        List<int[]> out = new ArrayList<int[]>();
        int depth = 0, start = from;
        for (int i = from; i < to; i++) {
            char ch = bl.charAt(i);
            if (ch == '(' || ch == '[' || ch == '{') depth++;
            else if (ch == ')' || ch == ']' || ch == '}') depth--;
            else if (depth == 0 && bl.startsWith(op, i)) {
                out.add(new int[] { start, i });
                start = i + op.length();
                i += op.length() - 1;
            }
        }
        out.add(new int[] { start, to });
        return out;
    }

    private static int close(String bl, int open) {
        char o = bl.charAt(open), c = o == '(' ? ')' : ']';
        int depth = 0;
        for (int i = open; i < bl.length(); i++) {
            if (bl.charAt(i) == o) depth++;
            else if (bl.charAt(i) == c && --depth == 0) return i;
        }
        return -1;
    }

    private static int openOf(String bl, int closeAt) {
        char c = bl.charAt(closeAt), o = c == ')' ? '(' : '[';
        int depth = 0;
        for (int i = closeAt; i >= 0; i--) {
            if (bl.charAt(i) == c) depth++;
            else if (bl.charAt(i) == o && --depth == 0) return i;
        }
        return -1;
    }

    static void leaves(Node n, List<Part> out) {
        if (n.op == 0) out.add(n.part);
        for (Node k : n.kids) leaves(k, out);
    }

    private static final Pattern NAME_CALL = Pattern.compile("([A-Za-z_]\\w*)\\s*\\(");
    private static final Pattern NEW_BEFORE = Pattern.compile("\\bnew\\s+$");
    private static final java.util.Set<String> NOT_CALLS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "if", "while", "for", "switch", "return", "new", "typeof", "sizeof", "nameof", "catch", "instanceof", "is",
            "else", "synchronized", "using", "lock", "foreach", "fixed", "try", "do"));
    /** פעולות של המבנים בספרייה — כל קריאה אליהן נרשמת כצעד (s.isEmpty()  →  false) */
    private static final java.util.Set<String> LIB = new java.util.HashSet<String>(java.util.Arrays.asList(
            "isempty", "top", "head", "pop", "remove", "push", "insert", "getvalue", "getnext", "hasnext",
            "getleft", "getright", "hasleft", "hasright", "setvalue", "setnext", "setleft", "setright"));

    /** הקריאות לפעולות בחלק (לפי סדר הסיום — כך הן נרשמות בצעדים): isEven(s.top()) → top, isEven */
    private static void calls(Part p) {
        String bl = LoopViz.blank(p.code);
        Matcher m = NAME_CALL.matcher(bl);
        List<int[]> found = new ArrayList<int[]>();
        while (m.find()) {
            String name = m.group(1);
            if (NOT_CALLS.contains(name) || NEW_BEFORE.matcher(bl.substring(0, m.start())).find()) continue;
            int open = m.end() - 1, close = close(bl, open);
            if (close < 0) continue;
            found.add(new int[] { receiver(bl, m.start()), close + 1, m.start(), m.end(1) });
        }
        java.util.Collections.sort(found, new java.util.Comparator<int[]>() {
            public int compare(int[] a, int[] b) {
                return a[1] - b[1];
            }
        });
        for (int[] f : found) {
            p.calls.add(f);
            p.names.add(p.code.substring(f[2], f[3]));
        }
    }

    /** תחילת הביטוי שהפעולה נקראת עליו: s.top() → s ; p.getNext().getValue() → p */
    private static int receiver(String bl, int nameStart) {
        int j = nameStart;
        while (true) {
            int k = j;
            while (k > 0 && Character.isWhitespace(bl.charAt(k - 1))) k--;
            if (k == 0 || bl.charAt(k - 1) != '.') return j;
            k--;
            while (k > 0 && Character.isWhitespace(bl.charAt(k - 1))) k--;
            if (k > 0 && (bl.charAt(k - 1) == ')' || bl.charAt(k - 1) == ']')) {
                k = openOf(bl, k - 1);
                if (k < 0) return j;
            }
            while (k > 0 && Character.isJavaIdentifierPart(bl.charAt(k - 1))) k--;
            if (k >= j) return j;
            j = k;
        }
    }

    /** המשתנה שהפעולה נקראת עליו: s.top() → s ("" = אין) */
    private static String root(String code, int[] call) {
        if (call[0] >= call[2]) return "";
        Matcher m = Pattern.compile("^\\s*([A-Za-z_]\\w*)").matcher(code.substring(call[0], call[2]));
        return m.find() ? m.group(1) : "";
    }

    /**
     * תנאי מורכב: כל חלק — עם הערכים, האם התקיים, והאם בכלל נבדק (קיצור: ב-&& אחרי חלק שלא מתקיים, וב-|| אחרי חלק שמתקיים,
     * את השאר לא בודקים — בדיוק כמו ב-Java וב-C#).
     * מה ידוע: התוצאה של כל התנאי (לפי מה שרץ אחר כך), הערכים של המשתנים, והפעולות שנקראו בזמן הבדיקה (הצעדים —
     * s.isEmpty() → false, isEven החזירה true). עוברים על כל האפשרויות לערכים של החלקים, ומשאירים רק את אלה שמתאימות לכל מה שידוע.
     * מה שלא נקבע — "?" (ולעולם לא סותר את התוצאה שבאמת יצאה).
     */
    private static void parts(int index, Step s, Check c, Map<String, Object> env) {
        Block b = c.block;
        Node root = tree(b.cond);
        if (root == null) return;
        List<Part> parts = new ArrayList<Part>();
        leaves(root, parts);
        if (parts.size() < 2 || parts.size() > MAX_PARTS) return;
        for (Part p : parts) {
            calls(p);
            p.env = leafValue(p.code, env);
            p.envSub = values(p.code, env);
        }
        List<String[]> log = log(index, s, b);
        List<String[]> flog = null;
        if (log != null) {
            java.util.Set<String> inCond = new java.util.HashSet<String>(), inLog = new java.util.HashSet<String>();
            for (Part p : parts) inCond.addAll(p.names);
            flog = new ArrayList<String[]>();
            for (String[] e : log) {
                if (!inCond.contains(e[0])) continue;
                flog.add(e);
                inLog.add(e[0]);
            }
            java.util.Set<String> declared = declared(s.where);
            for (Part p : parts) {
                for (int i = 0; i < p.calls.size(); i++) {
                    String name = p.names.get(i);
                    Object on = env.get(root(p.code, p.calls.get(i)));
                    if (inLog.contains(name) || declared.contains(name) || LIB.contains(name.toLowerCase()) && on instanceof StringViz.Struct) p.logged.add(i);
                }
            }
        }
        int[][] levels = { { 1, 1 }, { 0, 1 }, { 1, 0 }, { 0, 0 } };   // {לפי המשתנים, לפי הצעדים} — מהמדויק ביותר
        Agg a = null;
        for (int[] lv : levels) {
            if (lv[1] == 1 && flog == null) continue;
            a = enumerate(root, parts, c.result, lv[0] == 1, lv[1] == 1 ? flog : null, env);
            if (a.count > 0) break;
        }
        if (a == null || a.count == 0) return;
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            p.state = a.evaluated[i] == a.count ? CHECKED : a.evaluated[i] == 0 ? SKIPPED : UNSURE;
            p.value = a.seenTrue[i] && !a.seenFalse[i] ? Boolean.TRUE : a.seenFalse[i] && !a.seenTrue[i] ? Boolean.FALSE : null;
            p.sub = p.state == SKIPPED ? "" : a.sub[i] != null ? a.sub[i] : p.envSub;
            if (p.sub.equals(p.code)) p.sub = "";
        }
        if (c.result == null && a.rootTrue != a.rootFalse) c.result = a.rootTrue;
        c.tree = root;
        c.parts.addAll(parts);
        // התנאי עם הערכים: רק החלקים שנבדקו (חלק שלא נבדק נשאר כמו בקוד)
        StringBuilder v = new StringBuilder();
        int pos = 0;
        boolean any = false;
        for (Part p : parts) {
            boolean show = p.state != SKIPPED && !p.sub.isEmpty();
            v.append(b.cond, pos, p.from).append(show ? p.sub : p.code);
            any |= show;
            pos = p.to;
        }
        v.append(b.cond.substring(pos));
        c.values = any ? v.toString().replaceAll("\\s*\\n\\s*", " ") : "";
    }

    /** הערך של חלק לפי המשתנים (null = אי אפשר לחשב) */
    private static Boolean leafValue(String code, Map<String, Object> env) {
        Boolean r = LoopViz.evalCondition(code, env);
        if (r != null) return r;
        String sub = values(code, env);
        return sub.isEmpty() ? null : LoopViz.evalCondition(sub, new HashMap<String, Object>());   // !false
    }

    /** כל האפשרויות שמתאימות למה שידוע: לכל חלק — בכמה מהן נבדק, ואילו ערכים היו לו */
    private static final class Agg {
        int count;
        int[] evaluated;
        boolean[] seenTrue, seenFalse;
        String[] sub;
        boolean rootTrue, rootFalse;
    }

    private static Agg enumerate(Node root, List<Part> parts, Boolean result, boolean useEnv, List<String[]> flog, Map<String, Object> env) {
        int n = parts.size();
        Agg a = new Agg();
        a.evaluated = new int[n];
        a.seenTrue = new boolean[n];
        a.seenFalse = new boolean[n];
        a.sub = new String[n];
        Map<Part, Integer> idx = new java.util.IdentityHashMap<Part, Integer>();
        for (int i = 0; i < n; i++) idx.put(parts.get(i), i);
        boolean[] val = new boolean[n], ev = new boolean[n];
        List<Integer> order = new ArrayList<Integer>();
        String[] subs = new String[n];
        for (int mask = 0; mask < (1 << n); mask++) {
            for (int i = 0; i < n; i++) {
                val[i] = (mask >> i & 1) == 1;
                ev[i] = false;
                subs[i] = null;
            }
            order.clear();
            boolean r = simulate(root, val, ev, order, idx);
            if (result != null && r != result) continue;
            boolean ok = true;
            for (int i = 0; i < n && ok; i++) {
                Part p = parts.get(i);
                if (useEnv && ev[i] && p.env != null && p.env != val[i]) ok = false;
            }
            if (ok && flog != null) ok = matchLog(parts, order, val, subs, flog, env);
            if (!ok) continue;
            a.count++;
            if (r) a.rootTrue = true;
            else a.rootFalse = true;
            for (int i = 0; i < n; i++) {
                if (!ev[i]) continue;
                a.evaluated[i]++;
                if (val[i]) a.seenTrue[i] = true;
                else a.seenFalse[i] = true;
                if (a.sub[i] == null && subs[i] != null) a.sub[i] = subs[i];
            }
        }
        return a;
    }

    /** הפעולות שנקראו — בדיוק לפי הסדר שנרשם בצעדים, ומה שהחזירו מתאים לערך של החלק */
    private static boolean matchLog(List<Part> parts, List<Integer> order, boolean[] val, String[] subs, List<String[]> flog, Map<String, Object> env) {
        int pos = 0;
        for (int i : order) {
            Part p = parts.get(i);
            if (p.logged.isEmpty()) continue;
            String[] got = new String[p.calls.size()];
            for (int k : p.logged) {
                if (pos >= flog.size() || !flog.get(pos)[0].equals(p.names.get(k))) return false;
                got[k] = flog.get(pos++)[1];
            }
            String[] vs = withReturns(p, got, env);
            if (vs == null) continue;
            subs[i] = vs[0];
            if (vs[1] != null && Boolean.parseBoolean(vs[1]) != val[i]) return false;
        }
        return pos == flog.size();
    }

    /** מריצים את התנאי כמו ב-Java: משמאל לימין, ובקיצור (&& נעצר ב-false, || נעצר ב-true) */
    private static boolean simulate(Node n, boolean[] val, boolean[] ev, List<Integer> order, Map<Part, Integer> idx) {
        if (n.op == 0) {
            int i = idx.get(n.part);
            ev[i] = true;
            order.add(i);
            return val[i];
        }
        if (n.op == '!') return !simulate(n.kids.get(0), val, ev, order, idx);
        boolean and = n.op == '&';
        for (Node k : n.kids)
            if (simulate(k, val, ev, order, idx) != and) return !and;
        return and;
    }

    /**
     * החלק עם מה שהפעולות בו החזירו (לפי הצעדים): s.top() > 0 → 5 > 0. got = לכל קריאה מה שהחזירה (null = לא ידוע).
     * מחזיר {התצוגה, "true"/"false"/null} — או null אם אין מה להציב
     */
    private static String[] withReturns(Part p, String[] got, Map<String, Object> env) {
        Map<String, Object> e = new HashMap<String, Object>(env);
        StringBuilder ev = new StringBuilder(), show = new StringBuilder();
        int pos = 0;
        boolean any = false;
        for (int k = 0; k < p.calls.size(); k++) {
            int[] cl = p.calls.get(k);
            if (cl[0] < pos || got[k] == null) continue;
            boolean outer = true;   // רק הקריאה החיצונית (isEven(s.top()) → מה ש-isEven החזירה)
            for (int j = k + 1; j < p.calls.size(); j++)
                if (p.calls.get(j)[0] <= cl[0] && p.calls.get(j)[1] >= cl[1]) outer = false;
            if (!outer) continue;
            Object v = LoopViz.value(got[k]);
            if (v == null && got[k].matches("\\[.*\\]")) v = new StringViz.Struct("Node");   // חוליה (לא null)
            if (v == null) continue;
            String name = "__r" + k;
            e.put(name, v);
            ev.append(p.code, pos, cl[0]).append(name);
            show.append(p.code, pos, cl[0]).append(got[k]);
            pos = cl[1];
            any = true;
        }
        if (!any) return null;
        ev.append(p.code.substring(pos));
        show.append(p.code.substring(pos));
        Boolean r = LoopViz.evalCondition(ev.toString(), e);
        String sub = LoopViz.substitute(show.toString(), env);
        return new String[] { sub.isEmpty() ? show.toString() : sub, r == null ? null : r.toString() };
    }

    private static final Pattern OP_STEP = Pattern.compile("\\.\\s*(\\w+)\\s*\\(.*?\\)\\s*→\\s*(.*?)\\s*$");

    /**
     * הפעולות שנקראו בזמן הבדיקה של התנאי, לפי הסדר: {שם, מה שהחזירה} — פעולות של מבנים (s.isEmpty()  →  false)
     * ופעולות של התלמיד שנקראו מהשורה של התנאי. null = אי אפשר לדעת (למשל כשהגוף באותה שורה — הקריאות שלו מתערבבות)
     */
    private static List<String[]> log(int index, Step s, Block b) {
        if (b.thenFirst == b.line || !b.tail.isEmpty()) return null;
        int d = s.calls.size(), n = Recorder.stepCount();
        String method = s.where.method;
        int start = index;   // אחרי חזרה מפעולה שנקראה בתנאי השורה מסומנת שוב — מתחילים מהצעד הראשון של הבדיקה
        while (true) {
            Step t = Recorder.step(start);
            if (t.kind == Kind.LINE && t.calls.size() == d && t.where.line == b.line && !continuation(start, d, b.line)) break;
            if (start == 0) return null;
            Step p = Recorder.step(start - 1);
            if (p == null || p.where == null || p.calls.size() < d) return null;
            if (p.calls.size() == d && (p.where.line != b.line || !p.where.method.equals(method))) return null;
            start--;
        }
        List<String[]> out = new ArrayList<String[]>();
        for (int j = start + 1; j < n && j < start + 20000; j++) {
            Step t = Recorder.step(j);
            if (t.where == null) {
                if (t.kind == Kind.END) break;
                continue;
            }
            int td = t.calls.size();
            if (td < d) break;
            if (td == d) {
                if (t.kind == Kind.LINE) {
                    if (t.where.line == b.line && continuation(j, d, b.line)) continue;
                    break;
                }
                if (t.kind == Kind.RETURN || t.kind == Kind.END || t.kind == Kind.ERROR) break;
                if (t.where.line != b.line || !t.where.method.equals(method)) break;
                Matcher m = OP_STEP.matcher(t.text == null ? "" : t.text);
                if (m.find()) out.add(new String[] { m.group(1), m.group(2) });
                continue;
            }
            if (td == d + 1 && t.kind == Kind.RETURN && t.calls.get(1).line == b.line) out.add(new String[] { t.calls.get(0).method, t.returned });
        }
        return out;
    }

    /** צעד j (בשורה line) הוא המשך של אותה בדיקה: לפניו (אחרי פעולות של מבנים באותה שורה) — חזרה מפעולה שנקראה מהשורה הזו */
    static boolean continuation(int j, int d, int line) {
        for (int k = j - 1; k >= 0 && k > j - 200; k--) {
            Step p = Recorder.step(k);
            if (p == null || p.where == null) return false;
            int pd = p.calls.size();
            if (pd == d + 1 && p.kind == Kind.RETURN) return p.calls.size() > 1 && p.calls.get(1).line == line;
            if (pd != d || p.kind == Kind.LINE || p.kind == Kind.RETURN || p.where.line != line) return false;
        }
        return false;
    }

    private static final Map<String, java.util.Set<String>> declaredIn = new HashMap<String, java.util.Set<String>>();
    private static final Pattern DECL = Pattern.compile("\\b([A-Za-z_]\\w*)\\s*\\([^;{}()]*\\)\\s*(?:throws\\s+[\\w.,\\s]+)?\\{");

    /** הפעולות שמוגדרות בקובץ (פעולות של התלמיד — כל קריאה אליהן נרשמת, עם מה שהחזירה) */
    private static synchronized java.util.Set<String> declared(Frame f) {
        String key = f.packagePath() + f.file;
        java.util.Set<String> out = declaredIn.get(key);
        if (out == null) {
            out = new java.util.HashSet<String>();
            String text = SourceFiles.text(f);
            if (text != null) {
                Matcher m = DECL.matcher(LoopViz.blank(text));
                while (m.find()) if (!NOT_CALLS.contains(m.group(1))) out.add(m.group(1));
            }
            declaredIn.put(key, out);
        }
        return out;
    }

    // ------------------------------------------------------------ ההסבר: למה התנאי המורכב יצא כך

    /**
     * משפט קצר בעברית: מה קבע את התוצאה. למשל
     * "y > 4 לא מתקיים (3 > 4), ולכן את x / (y - 3) > 1 לא בודקים בכלל" / "x > 5 מתקיים (7 > 5) — וזה מספיק ב-||".
     * קוד בתוך המשפט עטוף בסימני כיוון (LRM), כדי שלא יתהפך.
     */
    static String why(Check c) {
        if (c.tree == null || c.result == null) return "";
        for (Part p : c.parts)   // חלק שאי אפשר לחשב, ולא ברור מה יצא בו — לא מנחשים
            if (p.state != SKIPPED && p.value == null)
                return "לא ידוע מה יצא ב-" + ltr(compact(p.code)) + " (אי אפשר לחשב את זה כאן), אבל התנאי כולו " + (c.result ? "התקיים" : "לא התקיים");
        for (Part p : c.parts)
            if (p.state == UNSURE) return "התנאי כולו " + (c.result ? "התקיים" : "לא התקיים") + " — אבל לא ידוע אילו חלקים נבדקו";
        for (int level = 2; level >= 0; level--) {   // מהמפורט לקצר — עד שנכנס
            String r = reason(c.tree, c.result, level);
            if (visible(r) <= 110) return r;
        }
        return c.result ? "התנאי כולו מתקיים" : "התנאי כולו לא מתקיים";
    }

    static int visible(String s) {
        return s.replace("‎", "").length();
    }

    /** למה n יצא v (n נבדק). level: 2 = גם מה שהיה לפני החלק שקבע, 1 = בקצרה, 0 = רק החלק שקבע */
    private static String reason(Node n, boolean v, int level) {
        if (n.op == 0) return say(n.part, v);
        if (n.op == '!') return reason(n.kids.get(0), !v, level) + ", וה-" + ltr("!") + " הופך את התוצאה";
        boolean and = n.op == '&';
        int size = n.kids.size();
        if (and == v) {   // && שמתקיים / || שלא מתקיים: כל החלקים
            StringBuilder b = new StringBuilder();
            Node inner = null;
            int compound = 0;
            for (int i = 0; i < size; i++) {
                Node k = n.kids.get(i);
                b.append(i == 0 ? "" : i == size - 1 ? ", וגם " : ", ");
                b.append(k.op == 0 ? say(k.part, v) : ltr(compact(k.code)) + (v ? " מתקיים" : " לא מתקיים"));
                if (k.op != 0) {
                    inner = k;
                    compound++;
                }
            }
            String all = b.toString();
            if (compound == 1 && level >= 2) {   // (y == 3 || y == 9) מתקיים — כי y == 3 מתקיים
                String w = all + " — כי " + reason(inner, v, 0);
                if (visible(w) <= 110) return w;
            }
            if (level >= 1 && visible(all) <= 90) return all;
            return and ? (size == 2 ? "שני החלקים מתקיימים" : "כל החלקים מתקיימים") + " — וב-" + ltr("&&") + " צריך שכולם יתקיימו"
                    : (size == 2 ? "שני החלקים לא מתקיימים" : "אף חלק לא מתקיים") + " — וב-" + ltr("||") + " צריך שלפחות אחד יתקיים";
        }
        // && שלא מתקיים / || שמתקיים: החלק הראשון שקבע — ואת מה שאחריו לא בודקים
        int k = -1;
        for (int i = 0; i < size && k < 0; i++) if (Boolean.valueOf(v).equals(value(n.kids.get(i)))) k = i;
        if (k < 0) return v ? "התנאי מתקיים" : "התנאי לא מתקיים";
        String s = reason(n.kids.get(k), v, level == 0 ? 0 : 1);
        if (!and) s += " — וזה מספיק ב-" + ltr("||");
        String pre = "";
        if (k == 1 && level >= 2) {
            pre = reason(n.kids.get(0), !v, 0) + ", אבל ";
        } else if (k >= 1 && level >= 1) {
            Node first = n.kids.get(0);
            String who = k > 1 ? "החלקים שלפניו" : first.op == 0 && compact(first.code).length() <= 30 ? ltr(compact(first.code)) : "החלק הראשון";
            pre = who + (and ? (k > 1 ? " מתקיימים" : " מתקיים") : (k > 1 ? " לא מתקיימים" : " לא מתקיים")) + ", אבל ";
        }
        int rest = size - k - 1;
        String post = "";
        if (rest == 1) {
            String r = compact(n.kids.get(k + 1).code);
            post = (and ? ", ולכן את " : ", אז את ") + (r.length() <= 30 ? ltr(r) : "החלק " + ORDINAL[Math.min(k + 1, ORDINAL.length - 1)]) + " לא בודקים בכלל";
        } else if (rest > 1) {
            post = (and ? ", ולכן את " : ", אז את ") + "שאר החלקים לא בודקים בכלל";
        } else if (and && level >= 1) {
            post = " — וב-" + ltr("&&") + " צריך ש" + (size == 2 ? "שניהם" : "כולם") + " יתקיימו";
        }
        return pre + s + post;
    }

    private static final String[] ORDINAL = { "הראשון", "השני", "השלישי", "הרביעי", "החמישי", "הבא" };

    /** הערך של חלק / של ביטוי (null = לא ידוע / לא נבדק) */
    private static Boolean value(Node n) {
        if (n.op == 0) return n.part.state == CHECKED ? n.part.value : null;
        if (n.op == '!') {
            Boolean v = value(n.kids.get(0));
            return v == null ? null : !v;
        }
        boolean and = n.op == '&';
        for (Node k : n.kids) {
            Boolean v = value(k);
            if (v == null) return null;
            if (v != and) return !and;
        }
        return and;
    }

    private static String say(Part p, boolean v) {
        return ltr(compact(p.code)) + (v ? " מתקיים" : " לא מתקיים") + (p.sub.isEmpty() ? "" : " (" + ltr(compact(p.sub)) + ")");
    }

    static String compact(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    private static String ltr(String s) {
        return CallViz.ltr(s);
    }

    // ------------------------------------------------------------ המבנה של התנאים בקובץ

    static synchronized List<Block> blocks(Frame f) {
        String key = f.packagePath() + f.file;
        List<Block> b = parsed.get(key);
        if (b == null) {
            String text = SourceFiles.text(f);
            b = text == null ? new ArrayList<Block>() : parse(text);
            parsed.put(key, b);
        }
        return b;
    }

    private static final Pattern KEYWORD = Pattern.compile("\\b(if|while|for|foreach|do)\\b");

    /** כל התנאים בקובץ (לפי הטקסט, בלי הערות ומחרוזות) */
    static List<Block> parse(String text) {
        Parser p = new Parser(text);
        List<Block> out = new ArrayList<Block>();
        Matcher m = KEYWORD.matcher(p.code);
        while (m.find()) {
            try {
                Block b = p.statement(m.start(), m.group(1));
                if (b != null) out.add(b);
            } catch (RuntimeException e) {
                // משפט שלא הצלחנו לפרק — בלי סימון
            }
        }
        for (Block b : out)   // שרשרת else if: כל if יודע מי ה-if שלפניו
            if (b.elseIsIf)
                for (Block c : out)
                    if (c.at == b.elseIfAt && c.kind.equals("if")) c.chainParent = b;
        return out;
    }

    private static final class Parser {
        final String text, code;
        final int[] lineOf;

        Parser(String text) {
            this.text = text;
            this.code = LoopViz.blank(text);
            lineOf = new int[text.length() + 1];
            int l = 1;
            for (int i = 0; i < text.length(); i++) {
                lineOf[i] = l;
                if (text.charAt(i) == '\n') l++;
            }
            lineOf[text.length()] = l;
        }

        int line(int pos) {
            return lineOf[Math.max(0, Math.min(pos, text.length()))];
        }

        int skip(int i) {
            while (i < code.length() && Character.isWhitespace(code.charAt(i))) i++;
            return i;
        }

        boolean word(int i, String w) {
            return code.startsWith(w, i) && (i + w.length() >= code.length() || !Character.isJavaIdentifierPart(code.charAt(i + w.length())))
                    && (i == 0 || !Character.isJavaIdentifierPart(code.charAt(i - 1)));
        }

        int match(int open) {
            char o = code.charAt(open), c = o == '(' ? ')' : o == '{' ? '}' : ']';
            int depth = 0;
            for (int i = open; i < code.length(); i++) {
                char ch = code.charAt(i);
                if (ch == o) depth++;
                else if (ch == c && --depth == 0) return i;
            }
            throw new IllegalStateException("סוגר חסר");
        }

        /** משפט שמתחיל ב-at עם מילת המפתח kw — Block (בלי לקדם את החיפוש) */
        Block statement(int at, String kw) {
            if (kw.equals("do")) {
                int body = skip(at + 2);
                int bodyEnd = end(body);
                int w = skip(bodyEnd);
                if (!word(w, "while")) return null;
                int open = skip(w + 5);
                if (open >= code.length() || code.charAt(open) != '(') return null;
                int close = match(open);
                Block b = new Block();
                b.kind = "do";
                b.line = line(w);
                b.cond = text.substring(open + 1, close).trim();
                condAt(b, open + 1);
                range(b, body, bodyEnd, true);
                b.end = line(close);
                return b;
            }
            int open = skip(at + kw.length());
            if (open >= code.length() || code.charAt(open) != '(') return null;
            int close = match(open);
            String inside = text.substring(open + 1, close);
            Block b = new Block();
            b.at = at;
            b.line = line(at);
            b.kind = kw.equals("foreach") ? "for" : kw;
            if (kw.equals("for")) {
                List<Integer> semis = new ArrayList<Integer>();
                int depth = 0;
                for (int i = open + 1; i < close; i++) {
                    char ch = code.charAt(i);
                    if (ch == '(' || ch == '[' || ch == '{') depth++;
                    else if (ch == ')' || ch == ']' || ch == '}') depth--;
                    else if (ch == ';' && depth == 0) semis.add(i);
                }
                b.cond = semis.size() == 2 ? text.substring(semis.get(0) + 1, semis.get(1)).trim() : "";   // for-each — בלי תנאי
                if (semis.size() == 2) {
                    condAt(b, semis.get(0) + 1);
                    b.forInit = text.substring(open + 1, semis.get(0)).trim();
                    b.forUpdate = text.substring(semis.get(1) + 1, close).trim();
                }
            } else if (kw.equals("foreach")) {
                b.cond = "";
            } else {
                b.cond = inside.trim();
                condAt(b, open + 1);
            }
            int body = skip(close + 1);
            if (body >= code.length()) return null;
            if (kw.equals("while") && code.charAt(body) == ';') return null;   // הסוף של do { ... } while (c);
            int bodyEnd = end(body);
            range(b, body, bodyEnd, true);
            if (line(body) == b.line && code.charAt(body) != '{') b.tail = code.substring(body, bodyEnd);
            b.end = line(bodyEnd - 1);
            if (kw.equals("if")) {
                int e = skip(bodyEnd);
                if (word(e, "else")) {
                    b.elseLine = line(e);
                    int eb = skip(e + 4);
                    b.elseIsIf = word(eb, "if");
                    if (b.elseIsIf) b.elseIfAt = eb;
                    int ebEnd = end(eb);
                    range(b, eb, ebEnd, false);
                    b.end = line(ebEnd - 1);
                }
            }
            return b;
        }

        /** איפה מתחיל התנאי (אחרי הרווחים שמ-from) */
        void condAt(Block b, int from) {
            int p = from;
            while (p < text.length() && text.charAt(p) <= ' ') p++;
            int ls = p;
            while (ls > 0 && text.charAt(ls - 1) != '\n') ls--;
            b.condLine = line(p);
            b.condCol = p - ls;
        }

        /** הגוף מ-from עד to: השורה של התו הראשון והאחרון בתוכו (בלוק {} — בלי הסוגריים עצמם) */
        void range(Block b, int from, int to, boolean then) {
            int first = from, last = to - 1;
            if (code.charAt(from) == '{') {
                first = skip(from + 1);
                last = to - 2;
                while (last > first && Character.isWhitespace(code.charAt(last))) last--;
                if (first >= to - 1) return;   // בלוק ריק
            }
            if (then) {
                b.thenFirst = line(first);
                b.thenLast = line(last);
            } else {
                b.elseFirst = line(first);
                b.elseLast = line(last);
            }
        }

        /** הסוף (אחרי התו האחרון) של המשפט שמתחיל ב-at */
        int end(int at) {
            at = skip(at);
            char ch = code.charAt(at);
            if (ch == '{') return match(at) + 1;
            if (word(at, "if") || word(at, "while") || word(at, "for") || word(at, "foreach")) {
                String kw = word(at, "if") ? "if" : word(at, "while") ? "while" : word(at, "foreach") ? "foreach" : "for";
                int open = skip(at + kw.length());
                int close = match(open);
                int e = end(close + 1);
                if (kw.equals("if")) {
                    int x = skip(e);
                    if (word(x, "else")) e = end(x + 4);
                }
                return e;
            }
            if (word(at, "do")) {
                int e = end(at + 2);
                int w = skip(e);
                int open = skip(w + 5);
                int close = match(open);
                int semi = skip(close + 1);
                return semi < code.length() && code.charAt(semi) == ';' ? semi + 1 : close + 1;
            }
            int depth = 0;   // משפט פשוט: עד ; ברמה העליונה
            for (int i = at; i < code.length(); i++) {
                char c = code.charAt(i);
                if (c == '(' || c == '[' || c == '{') depth++;
                else if (c == ')' || c == ']' || c == '}') depth--;
                else if (c == ';' && depth <= 0) return i + 1;
            }
            return code.length();
        }
    }
}

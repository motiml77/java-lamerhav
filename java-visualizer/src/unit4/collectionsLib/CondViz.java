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
                b.append("| ").append(c.result).append(' ').append(c.outcome).append("]\n");
            }
            for (Branch br : m.branches) {
                b.append("  [ענף #").append(i + 1).append(" שורה ").append(s.where.line).append(": ")
                        .append(br.inElse ? "else" : "if").append(" של שורה ").append(br.condLine).append("]\n");
            }
            if (!m.branches.isEmpty()) b.append("  [הסבר #").append(i + 1).append(": ").append(note(m.branches.get(m.branches.size() - 1))).append("]\n");
        }
        return b.toString();
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
        c.outcome = outcome(b, c.result);
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
            if (t.where.line == b.line && b.kind.equals("if")) continue;   // אותה שורה (למשל אחרי קריאה לפעולה בתוך התנאי)
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
            if (val != null) env.put(v[0], val);
        }
        return env;
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
                    b.forInit = text.substring(open + 1, semis.get(0)).trim();
                    b.forUpdate = text.substring(semis.get(1) + 1, close).trim();
                }
            } else if (kw.equals("foreach")) {
                b.cond = "";
            } else {
                b.cond = inside.trim();
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

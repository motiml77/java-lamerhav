package unit4.collectionsLib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * לולאה שלא נגמרת בהרצה הוויזואלית — איך מזהים אותה (ב-Java: JdiTracer שבתהליך האב משתמש בזה; ב-C#: Recorder.cs עושה אותו דבר בתוך התהליך):
 * <ul>
 * <li>א. בטוח: אותו מצב בדיוק (המשתנים, המבנים והאובייקטים) חוזר בשורת התנאי של הלולאה, באותה קריאה —
 *     בתוכנית בלי אקראיות, קלט או שעון זה אומר שהיא תסתובב כך לנצח;</li>
 * <li>ב. כנראה: אחרי הרבה סיבובים (1000 — או 30 סיבובים ושתי שניות) המשתנים שבתנאי לא השתנו בכלל (והגוף גם לא משנה אותם),
 *     או שהם רק מתרחקים מהסוף (‎i--‎ כשהתנאי ‎i &lt; 10‎). אחרי שהתיעוד נעצר (5000 צעדים) — דוגמים את התוכנית כמה פעמים בשנייה;</li>
 * <li>ג. תקועה בלי צעדים: אין שורה חדשה כמה שניות (‎while (true);‎ בשורה אחת) והתוכנית לא מחכה לקלט — דוגמים כמו ב-ב.</li>
 * </ul>
 * לולאה רגילה וארוכה (סכום 1..2000) לא נעצרת: המשתנה שבתנאי מתקדם לסוף.
 * כאן: ניתוח הקוד של הלולאה (Info), ההחלטה לפי הסיבובים (Tracker) או לפי דגימות (Sampler), וההחלטה עצמה (Verdict) — כטקסט שעובר
 * לתהליך של התוכנית / לקובץ הצעדים של C#. הכרטיס שמסביר לתלמיד מה קרה, למה, ומה עושים — LoopCard.
 */
final class LoopGuard {
    private LoopGuard() {}

    static final int ROUNDS = 1000;            // אחרי כמה סיבובים בלי התקדמות עוצרים
    static final int QUICK_ROUNDS = 30;        // ... או אחרי כמה סיבובים, אם הלולאה כבר רצה QUICK_MS (הרצה איטית)
    static final long QUICK_MS = 2000;
    static final int SAME_ROUNDS = 3;          // מצב שחוזר — רק אחרי כמה סיבובים שאפשר לראות
    static final long QUIET_MS = 1500;         // אין אף שורה חדשה כל כך הרבה זמן — מתחילים לדגום
    static final long SAMPLE_EVERY_MS = 100;
    static final long SAMPLE_WINDOW_MS = 1500; // כמה זמן הדגימות צריכות להראות שהלולאה תקועה
    static final int SAMPLE_MIN = 8;
    static final long LONG_MS = 10000;         // אחרי שהתיעוד נעצר: לולאה שרצה יותר מזה בלי לצאת
    static final long LONG_TOWARD_MS = 30000;  // ... ואם המשתנה שבתנאי מתקדם לסוף — מחכים יותר

    // ------------------------------------------------------------ ניתוח הקוד של לולאה

    /** מה אפשר לדעת על לולאה מהקוד שלה — בלי להריץ */
    static final class Info {
        final LoopViz.Loop loop;
        final int atLine;                                   // השורה שבה בודקים את התנאי (do: בסוף)
        final String cond;
        final List<String> vars = new ArrayList<String>();  // המשתנים שבתנאי (בלי שמות של פעולות ומחלקות)
        final Set<String> fields = new HashSet<String>();   // מהם: כאלה שכתובים this.x
        boolean pure = true;                                // בתנאי רק פעולות שלא משנות כלום (isEmpty, getNext, length...)
        boolean exits;                                      // בגוף יש break / return / throw — אולי יוצאים בדרך אחרת
        boolean calls;                                      // בגוף יש קריאה לפעולה שאינה של הספרייה (יכולה לשנות מאפיינים)
        final Set<String> assigned = new HashSet<String>(); // משתנים מהתנאי שהגוף (או החלק השלישי של for) משנה ישירות
        final Set<String> escapes = new HashSet<String>();  // משתנים מהתנאי שהגוף מעביר לפעולה, או משנה דרכם מבנה (pop, setNext...)
        boolean constant;                                   // while (true) / for (;;)
        String cmpVar, cmpOp, cmpBound;                     // השוואה פשוטה: i < n  →  i, <, n (המשתנה תמיד משמאל)
        boolean semicolon;                                  // while (x > 0);  — נקודה-פסיק מיותרת: הגוף ריק
        boolean blockAfter;                                 // ואחריה בלוק { ... } שבכלל לא שייך ללולאה
        String update = "";                                 // for: החלק השלישי (i++)
        String body = "";

        Info(LoopViz.Loop l) {
            loop = l;
            atLine = l.kind.equals("do") ? l.end : l.header;
            cond = l.cond == null ? "" : l.cond.trim();
        }

        /** המשתנה חייב לגדול כדי שהלולאה תיגמר (i &lt; n) — או לקטון (x &gt; 0); null אם לא ידוע (!=) */
        Boolean mustGrow() {
            if (cmpOp == null) return null;
            if (cmpOp.startsWith("<")) return Boolean.TRUE;
            if (cmpOp.startsWith(">")) return Boolean.FALSE;
            return null;
        }
    }

    private static final Set<String> KEYWORDS = new HashSet<String>(Arrays.asList("true", "false", "null", "new", "instanceof", "this",
            "int", "long", "double", "char", "boolean", "float", "short", "byte", "is", "as", "not", "and", "or"));
    /** פעולות שאפשר לקרוא להן בתנאי בלי לשנות שום דבר */
    private static final Set<String> PURE = new HashSet<String>(Arrays.asList("isEmpty", "getNext", "getValue", "hasNext", "getLeft", "getRight",
            "hasLeft", "hasRight", "top", "head", "length", "charAt", "equals", "equalsIgnoreCase", "compareTo", "size", "abs", "max", "min",
            "sqrt", "pow", "contains", "indexOf", "startsWith", "endsWith", "isDigit", "isLetter", "isUpperCase", "isLowerCase", "getName",
            "getAge", "getGrade", "getAverage", "getYear", "getPrice", "Count"));
    /** פעולות של הספרייה / של Java שלא משנות שום דבר של התלמיד (בגוף הלולאה) */
    private static final Set<String> HARMLESS = new HashSet<String>(Arrays.asList("print", "println", "printf", "Write", "WriteLine", "format",
            "valueOf", "toString", "parseInt", "push", "pop", "insert", "remove", "setNext", "setValue", "setLeft", "setRight", "if", "while",
            "for", "switch", "catch", "synchronized", "return", "foreach", "using", "lock", "substring", "toUpperCase", "toLowerCase", "trim",
            "sizeOf", "nameof", "typeof", "sizeof", "duplicate"));
    private static final Pattern IDENT = Pattern.compile("(?<![.\\w$])([A-Za-z_]\\w*)(?!\\s*\\()");
    private static final Pattern CALLED = Pattern.compile("([A-Za-z_]\\w*)\\s*\\(");
    private static final Pattern EXIT = Pattern.compile("\\b(break|return|throw|goto)\\b|System\\s*\\.\\s*exit|Environment\\s*\\.\\s*Exit");
    private static final Pattern MUTATE = Pattern.compile("\\s*\\.\\s*(pop|push|insert|remove|setNext|setValue|setLeft|setRight|add|clear|set|put"
            + "|Pop|Push|Insert|Remove|SetNext|SetValue|SetLeft|SetRight|Add|Clear|Enqueue|Dequeue)\\s*\\(");
    private static final Pattern RANDOM = Pattern.compile("Math\\s*\\.\\s*random|\\bRandom\\b|\\bScanner\\b|System\\s*\\.\\s*in\\b|currentTimeMillis|nanoTime"
            + "|\\bLocalDate|\\bLocalTime|\\bInstant\\b|\\bConsole\\s*\\.\\s*Read|\\bReadLine\\b|\\bReadKey\\b|\\bDateTime\\b|\\bStopwatch\\b"
            + "|\\bTickCount|\\bGuid\\b|\\bThread\\b|\\bTask\\b|hashCode|GetHashCode|identityHashCode|BufferedReader|InputStream|\\bUUID\\b");

    /** האם בקוד יש אקראיות / קלט / שעון — אז מצב שחוזר לא אומר שהלולאה תסתובב לנצח */
    static boolean nondeterministic(String code) {
        return code != null && RANDOM.matcher(LoopViz.blank(code)).find();
    }

    private static final Map<LoopViz.Loop, Info> infos = new java.util.IdentityHashMap<LoopViz.Loop, Info>();

    /** lines = כל שורות הקובץ (בשביל ; מיותרת אחרי התנאי). null ללולאת for-each (היא תמיד נגמרת) */
    static synchronized Info analyze(LoopViz.Loop l, List<String> lines) {
        if (l == null || l.kind.equals("foreach")) return null;
        Info info = infos.get(l);
        if (info != null) return info;
        info = new Info(l);
        infos.put(l, info);
        String c = LoopViz.blank(info.cond).replaceAll("\\bthis\\s*\\.\\s*", " this_");
        Matcher m = IDENT.matcher(c);
        while (m.find()) {
            String id = m.group(1);
            boolean field = id.startsWith("this_");
            if (field) id = id.substring(5);
            int after = m.end();
            while (after < c.length() && Character.isWhitespace(c.charAt(after))) after++;
            boolean dot = after < c.length() && c.charAt(after) == '.';
            if (KEYWORDS.contains(id) || dot && Character.isUpperCase(id.charAt(0)) && !info.vars.contains(id)) continue;   // Math.abs, Integer.MAX_VALUE
            if (!info.vars.contains(id)) info.vars.add(id);
            if (field) info.fields.add(id);
        }
        Matcher k = CALLED.matcher(c);
        while (k.find()) if (!PURE.contains(k.group(1)) && !PURE.contains(lower(k.group(1)))) info.pure = false;
        info.constant = c.trim().isEmpty() || c.trim().equals("true");
        if (l.kind.equals("for")) {
            String inside = l.text.substring(l.text.indexOf('(') + 1, l.text.lastIndexOf(')'));
            String[] parts = inside.split(";", -1);
            if (parts.length == 3) info.update = parts[2].trim();
        }
        semicolon(info, l, lines);
        info.body = info.semicolon || l.body == null ? "" : l.body;   // while (x > 0); { ... } — הבלוק בכלל לא הגוף
        String changes = info.body + " ;\n" + info.update;
        info.exits = EXIT.matcher(info.body).find();
        Matcher call = CALLED.matcher(info.body);
        while (call.find()) {
            String n = call.group(1);
            int before = call.start() - 1;
            while (before >= 0 && Character.isWhitespace(info.body.charAt(before))) before--;
            boolean method = before >= 0 && info.body.charAt(before) == '.';
            if (HARMLESS.contains(n) || method && (PURE.contains(n) || PURE.contains(lower(n)) || HARMLESS.contains(lower(n)))) continue;
            if (n.equals("new") || Character.isUpperCase(n.charAt(0)) && before >= 3 && info.body.substring(0, before + 1).trim().endsWith("new")) continue;
            info.calls = true;
        }
        for (String v : info.vars) {
            String q = Pattern.quote(v);
            if (Pattern.compile("(?<![.\\w$])" + q + "\\s*(?:\\[[^\\]]*\\]\\s*)*(?:[-+*/%&|^]|<<|>>>?)?=(?!=)").matcher(changes).find()
                    || Pattern.compile("(?<![.\\w$])" + q + "\\s*(\\+\\+|--)").matcher(changes).find()
                    || Pattern.compile("(\\+\\+|--)\\s*" + q + "(?![\\w$])").matcher(changes).find()
                    || Pattern.compile("\\b(ref|out)\\s+" + q + "\\b").matcher(changes).find())
                info.assigned.add(v);
            if (Pattern.compile("(?<![.\\w$])" + q + MUTATE.pattern()).matcher(changes).find() || passed(changes, v)
                    || Pattern.compile("(?<![.\\w$])" + q + "\\s*\\.\\s*\\w+\\s*=(?!=)").matcher(changes).find()) info.escapes.add(v);
            Matcher mc = Pattern.compile("(?<![.\\w$])" + q + "\\s*\\.\\s*([A-Za-z_]\\w*)\\s*\\(").matcher(changes);   // פעולה של האובייקט — אולי משנה אותו
            while (mc.find()) if (!PURE.contains(mc.group(1)) && !PURE.contains(lower(mc.group(1))) && !mc.group(1).matches("(?i)print|println|toString|equals"))
                info.escapes.add(v);
        }
        comparison(info, c);
        return info;
    }

    /**
     * נקודה-פסיק מיד אחרי הסוגריים של while / for:  while (x > 0);  — הגוף של הלולאה הוא פקודה ריקה
     * (וגם אם אחריה בא בלוק { ... } — הוא רץ אחרי הלולאה, לא בתוכה). לא נוגעים ב-do { } while (...); — שם זה תקין.
     */
    private static void semicolon(Info info, LoopViz.Loop l, List<String> lines) {
        if (lines == null || !(l.kind.equals("while") || l.kind.equals("for")) || l.header < 1 || l.header > lines.size()) return;
        StringBuilder b = new StringBuilder();
        for (int i = l.header - 1; i < lines.size() && i < l.header + 3; i++) b.append(LoopViz.blank(lines.get(i))).append('\n');
        String t = b.toString();
        Matcher k = Pattern.compile("\\b" + l.kind + "\\s*\\(").matcher(t);
        if (!k.find()) return;
        int close = StringViz.matching(t, k.end() - 1);
        if (close < 0) return;
        int i = close + 1;
        while (i < t.length() && Character.isWhitespace(t.charAt(i))) i++;
        if (i >= t.length() || t.charAt(i) != ';') return;
        info.semicolon = true;
        int j = i + 1;
        String rest = t.substring(j);
        for (int n = l.header + 3; n < lines.size() && n < l.header + 8; n++) rest += LoopViz.blank(lines.get(n)) + "\n";
        info.blockAfter = rest.trim().startsWith("{");
    }

    private static String lower(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** האם המשתנה מועבר כפרמטר לפעולה: f(x) / f(a, x) */
    private static boolean passed(String code, String v) {
        Matcher m = Pattern.compile("(?<![.\\w$])" + Pattern.quote(v) + "(?![\\w$])").matcher(code);
        while (m.find()) {
            int a = m.start() - 1, b = m.end();
            while (a >= 0 && Character.isWhitespace(code.charAt(a))) a--;
            while (b < code.length() && Character.isWhitespace(code.charAt(b))) b++;
            if (a < 0 || b >= code.length() || (code.charAt(a) != '(' && code.charAt(a) != ',') || (code.charAt(b) != ')' && code.charAt(b) != ',')) continue;
            int depth = 0, open = -1;   // הסוגר שפותח את רשימת הפרמטרים
            for (int i = a; i >= 0; i--) {
                char ch = code.charAt(i);
                if (ch == ')') depth++;
                else if (ch == '(' && depth-- == 0) { open = i; break; }
            }
            if (open <= 0) continue;
            int w = open - 1;
            while (w >= 0 && Character.isWhitespace(code.charAt(w))) w--;
            int e = w;
            while (w >= 0 && (Character.isLetterOrDigit(code.charAt(w)) || code.charAt(w) == '_')) w--;
            String name = code.substring(w + 1, e + 1);
            if (!name.isEmpty() && !name.matches("if|while|for|switch|return|catch|synchronized|lock|using|foreach") && !HARMLESS.contains(name)) return true;
        }
        return false;
    }

    /** תנאי שהוא השוואה אחת: i < n, x > 0, 0 < x, x != 0 — המשתנה בצד שמאל */
    private static void comparison(Info info, String c) {
        String s = c.trim();
        while (s.startsWith("(") && StringViz.matching(s, 0) == s.length() - 1) s = s.substring(1, s.length() - 1).trim();
        if (topLevel(s, "&&") || topLevel(s, "||")) return;
        String found = null;
        int at = -1;
        for (String op : new String[] { "<=", ">=", "!=", "<", ">" }) {
            int i = topIndex(s, op);
            if (i >= 0) { found = op; at = i; break; }
        }
        if (found == null) return;
        String a = s.substring(0, at).trim().replace("this_", ""), b = s.substring(at + found.length()).trim().replace("this_", "");
        boolean aVar = a.matches("[A-Za-z_]\\w*") && info.vars.contains(a), bVar = b.matches("[A-Za-z_]\\w*") && info.vars.contains(b);
        if (aVar && (!bVar || !info.assigned.contains(b) || info.assigned.contains(a))) {
            info.cmpVar = a; info.cmpOp = found; info.cmpBound = b;
        } else if (bVar) {
            info.cmpVar = b; info.cmpBound = a;
            info.cmpOp = found.equals("<") ? ">" : found.equals(">") ? "<" : found.equals("<=") ? ">=" : found.equals(">=") ? "<=" : found;
        }
    }

    private static boolean topLevel(String s, String op) {
        return topIndex(s, op) >= 0;
    }

    /** המקום של האופרטור מחוץ לסוגריים (ל-"<" לא תופסים את "<=" / "<<"), או -1 */
    private static int topIndex(String s, String op) {
        int depth = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '(' || ch == '[') depth++;
            else if (ch == ')' || ch == ']') depth--;
            else if (depth == 0 && s.startsWith(op, i)) {
                char next = i + op.length() < s.length() ? s.charAt(i + op.length()) : ' ', prev = i > 0 ? s.charAt(i - 1) : ' ';
                if (op.length() == 1 && (next == '=' || next == op.charAt(0) || prev == op.charAt(0) || prev == '<' || prev == '>' || prev == '=')) continue;
                if (op.equals("<") && next == '-') continue;   // Stack<-1 לא קורה; ליתר ביטחון
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------ ערכים

    /** ערך מספרי של משתנה כפי שהוא מוצג (5, 'a', 3.0) — או null */
    static Long num(String v) {
        if (v == null) return null;
        String s = v.trim();
        if (s.length() == 3 && s.charAt(0) == '\'' && s.charAt(2) == '\'') return (long) s.charAt(1);
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            try {
                double d = Double.parseDouble(s);
                return d == Math.rint(d) && Math.abs(d) < 1e15 ? (long) d : null;
            } catch (NumberFormatException e2) {
                return null;
            }
        }
    }

    /**
     * כמה הלולאה "קרובה לסוף" לפי ההשוואה (גדל = מתקרבים): i &lt; n → i - n; x &gt; 0 → 0 - x; x != 0 → -|x - 0|.
     * null אם אי אפשר לחשב.
     */
    static Long progress(Info info, Map<String, String> vals) {
        if (info == null || info.cmpVar == null) return null;
        Long v = num(vals.get(info.cmpVar));
        if (v == null) return null;
        Long b = bound(info.cmpBound, vals);
        if (b == null) return null;
        if (info.cmpOp.startsWith("<")) return v - b;
        if (info.cmpOp.startsWith(">")) return b - v;
        return -Math.abs(v - b);
    }

    /** הצד השני של ההשוואה: מספר, משתנה, n - 1, s.length() — לפי הערכים של עכשיו */
    static Long bound(String expr, Map<String, String> vals) {
        if (expr == null) return null;
        Long direct = num(expr);
        if (direct != null) return direct;
        if (vals.containsKey(expr.trim())) return num(vals.get(expr.trim()));
        Map<String, Object> env = new HashMap<String, Object>();
        for (Map.Entry<String, String> e : vals.entrySet()) {
            Object o = LoopViz.value(e.getValue());
            if (o != null) env.put(e.getKey(), o);
        }
        Object r = StringViz.eval(expr.replace(".Length", ".length()").replace(".Count", ".length()"), env);
        if (r instanceof Integer) return (long) (Integer) r;
        if (r instanceof Character) return (long) (Character) r;
        return null;
    }

    static boolean isNumber(String s) {
        return s != null && num(s) != null;
    }

    /** ערך שמצביע על מבנה / אובייקט (ולא מספר, תו, מחרוזת או null) — את התוכן שלו בודקים בנפרד */
    static boolean isRef(String v) {
        if (v == null || v.equals("null") || num(v) != null) return false;
        return !(v.startsWith("\"") || v.equals("true") || v.equals("false"));
    }

    // ------------------------------------------------------------ ההחלטה

    /** הלולאה לא נגמרת: איך ידענו, איפה, וכמה זמן — עובר כטקסט אחד (גם מ-C# בקובץ הצעדים, בשורה LOOP) */
    static final class Verdict {
        static final String SAME = "same", UNCHANGED = "unchanged", AWAY = "away", LONG = "long";
        String kind;
        int line;               // השורה שבה בודקים את התנאי של הלולאה
        int rounds = -1;        // כמה סיבובים כבר עברו (-1: לא ידוע)
        long ms;                // כמה זמן היא רצה
        boolean quiet;          // בלי צעדים: אין שורות חדשות (לולאה בשורה אחת), או אחרי שהתיעוד נעצר
        String trendVar = "";   // המשתנה שבהשוואה, וכמה מהערכים שלו (בשביל "רק הולך וקטן: 0, -1, -2...")
        final List<String> trend = new ArrayList<String>();

        @Override
        public String toString() {
            StringBuilder t = new StringBuilder();
            for (String x : trend) t.append(t.length() == 0 ? "" : ",").append(x.replace(',', ' '));
            return "loop\u0002" + kind + "\u0002" + line + "\u0002" + rounds + "\u0002" + ms + "\u0002" + (quiet ? 1 : 0) + "\u0002" + trendVar + "\u0002" + t;
        }

        static Verdict parse(String s) {
            if (s == null || !s.startsWith("loop\u0002")) return null;
            String[] f = s.split("\u0002", -1);
            Verdict v = new Verdict();
            try {
                v.kind = f[1];
                v.line = Integer.parseInt(f[2]);
                v.rounds = Integer.parseInt(f[3]);
                v.ms = Long.parseLong(f[4]);
                v.quiet = f[5].equals("1");
                v.trendVar = f.length > 6 ? f[6] : "";
                if (f.length > 7 && !f[7].isEmpty()) v.trend.addAll(Arrays.asList(f[7].split(",")));
            } catch (RuntimeException e) {
                return null;
            }
            return v;
        }

        void addTrend(String var, List<Long> values) {
            trendVar = var == null ? "" : var;
            trend.clear();
            if (values.size() <= 6) {
                for (Long x : values) trend.add(String.valueOf(x));
            } else {
                for (int i = 0; i < 3; i++) trend.add(String.valueOf(values.get(i)));
                trend.add("…");
                trend.add(String.valueOf(values.get(values.size() - 1)));
            }
        }
    }

    // ------------------------------------------------------------ בזמן התיעוד: כל ביקור בשורת התנאי

    /** לולאה אחת שרצה עכשיו (בקריאה מסוימת לפעולה) */
    static final class Act {
        final int depth, header, end;
        final String method;
        int visits;
        long start;
        Map<String, String> first;
        int firstMut;
        boolean same = true;                                       // המשתנים שבתנאי לא השתנו מאז הסיבוב הראשון
        Long lastP;
        int away, toward, flat;                                    // כמה ביקורים ברצף ההתקדמות קטנה / גדלה / לא זזה
        final List<Long> values = new ArrayList<Long>();           // ערכי המשתנה שבהשוואה (הראשונים והאחרונים)
        final Map<String, Set<String>> states = new HashMap<String, Set<String>>();   // מצב "זול" → המצבים המלאים שנראו איתו
        int deepChecks;

        Act(int depth, String method, int header, int end) {
            this.depth = depth;
            this.method = method;
            this.header = header;
            this.end = end;
        }
    }

    /** מקור למצב המלא (המשתנים, המבנים, האובייקטים והמשתנים הסטטיים) — מחושב רק כשהמצב ה"זול" חוזר; null = לא ידוע */
    interface Deep {
        String get();
    }

    /**
     * עוקב אחרי הלולאות שרצות עכשיו (בתהליך האב של Java): מקבל כל שורה (כדי לדעת מתי יוצאים מלולאה)
     * וכל ביקור בשורת התנאי — ומחליט אם הלולאה לא נגמרת.
     */
    static final class Tracker {
        private final Map<String, Act> acts = new LinkedHashMap<String, Act>();

        /** כל שורה: לולאות שיצאנו מהן (או שהפעולה שלהן הסתיימה) נשכחות */
        void line(int depth, String fileKey, String method, int line) {
            java.util.Iterator<Map.Entry<String, Act>> it = acts.entrySet().iterator();
            while (it.hasNext()) {
                Act a = it.next().getValue();
                if (a.depth > depth || a.depth == depth && (!a.method.equals(fileKey + "|" + method) || line < a.header || line > a.end)) it.remove();
            }
        }

        /** פעולה הסתיימה: הלולאות שלה (ושל מה שהיא קראה) נשכחות */
        void exit(int depth) {
            java.util.Iterator<Map.Entry<String, Act>> it = acts.entrySet().iterator();
            while (it.hasNext()) if (it.next().getValue().depth >= depth) it.remove();
        }

        void clear() {
            acts.clear();
        }

        /**
         * ביקור בשורת התנאי. vals = ערכי המשתנים שבתנאי; mutations = מונה השינויים במבנים של הספרייה;
         * cheap = כל המשתנים כטקסט (או null); deep = המצב המלא; deterministic = אין בקוד אקראיות / קלט.
         */
        Verdict header(int depth, String fileKey, String method, Info info, Map<String, String> vals, int mutations,
                String cheap, Deep deep, boolean deterministic, long now) {
            String key = depth + "|" + fileKey + "|" + method + "|" + info.loop.header;
            Act a = acts.get(key);
            if (a == null) acts.put(key, a = new Act(depth, fileKey + "|" + method, info.loop.header, info.loop.end));
            a.visits++;
            int rounds = info.loop.kind.equals("do") ? a.visits : a.visits - 1;
            boolean refs = false;
            for (String v : info.vars) refs |= isRef(vals.get(v));
            if (a.visits == 1) {
                a.start = now;
                a.first = new HashMap<String, String>(vals);
                a.firstMut = mutations;
            } else if (!vals.equals(a.first) || refs && mutations != a.firstMut) {
                a.same = false;
            }
            Long p = progress(info, vals);
            if (p != null && a.lastP != null) {
                if (p < a.lastP) { a.away++; a.toward = 0; a.flat = 0; }
                else if (p > a.lastP) { a.toward++; a.away = 0; a.flat = 0; }
                else { a.flat++; a.away = 0; a.toward = 0; }
            } else if (p == null) {
                a.away = a.toward = a.flat = 0;
            }
            a.lastP = p;
            Long cv = info.cmpVar == null ? null : num(vals.get(info.cmpVar));
            if (cv != null) {
                if (a.values.size() < 3) a.values.add(cv);
                else if (a.values.size() < 4) a.values.add(cv);
                else a.values.set(3, cv);
            }
            long ms = now - a.start;
            boolean enough = rounds >= ROUNDS || rounds >= QUICK_ROUNDS && ms >= QUICK_MS;

            // א. אותו מצב בדיוק חוזר
            if (deterministic && cheap != null && deep != null) {
                Set<String> seen = a.states.get(cheap);
                if (seen == null) {
                    if (a.states.size() < 2000) a.states.put(cheap, new HashSet<String>());
                } else if (rounds >= SAME_ROUNDS && a.deepChecks < 300) {
                    a.deepChecks++;
                    String d = deep.get();
                    if (d != null && !seen.add(d)) return verdict(Verdict.SAME, info, rounds, ms, a);
                }
            }
            if (!enough || info.exits) return null;
            // ב. המשתנים שבתנאי לא השתנו בכלל, ושום דבר בלולאה לא משנה אותם
            if (a.same && info.pure && untouched(info, vals)) return verdict(Verdict.UNCHANGED, info, rounds, ms, a);
            // ... או שהם משתנים יחד (i++ וגם n++) — המרחק לסוף לא קטן
            if (rounds >= ROUNDS && info.pure && p != null && a.flat >= rounds - 1) return verdict(Verdict.UNCHANGED, info, rounds, ms, a);
            // ... או שהם רק מתרחקים מהסוף
            if (info.pure && p != null && a.away >= QUICK_ROUNDS && a.away >= Math.min(rounds - 1, 200)) return verdict(Verdict.AWAY, info, rounds, ms, a);
            return null;
        }

        private static Verdict verdict(String kind, Info info, int rounds, long ms, Act a) {
            Verdict v = new Verdict();
            v.kind = kind;
            v.line = info.atLine;
            v.rounds = rounds;
            v.ms = ms;
            v.addTrend(info.cmpVar, a.values);
            return v;
        }
    }

    /** אף משתנה מהתנאי לא משתנה בגוף הלולאה — לא ישירות, לא דרך פעולה, ולא דרך מאפיין */
    static boolean untouched(Info info, Map<String, String> vals) {
        for (String v : info.vars) {
            if (info.assigned.contains(v)) return false;
            if (info.escapes.contains(v) && isRef(vals.get(v))) return false;
            if ((info.fields.contains(v) || !vals.containsKey(v)) && info.calls) return false;   // מאפיין: פעולה יכולה לשנות אותו
        }
        return true;
    }

    // ------------------------------------------------------------ אחרי שהתיעוד נעצר / כשאין צעדים: דגימות

    /** לולאה אחת שהתוכנית בתוכה ברגע הדגימה (בפעולה אחת במחסנית הקריאות) */
    static final class Ctx {
        int level;                     // 0 = הפעולה התחתונה (main)
        boolean top;                   // זו הפעולה העליונה של התלמיד ברגע הדגימה (ולא פעולה שמחכה לפעולה שהיא קראה)
        String fileKey, method;
        Info info;
        Map<String, String> vals = new HashMap<String, String>();

        String key() {
            return level + "|" + fileKey + "|" + method + "|" + info.loop.header;
        }
    }

    /** דגימה: הלולאות שהתוכנית בתוכן (מהפנימית לחיצונית), מונה השינויים, והאם הקוד של התלמיד רץ ממש עכשיו */
    static final class Sample {
        long t;
        final List<Ctx> ctxs = new ArrayList<Ctx>();
        int mutations;
        boolean studentTop;
    }

    /** תוצאה של הדוגם: ההחלטה, ובאיזו לולאה לעצור */
    static final class Hit {
        Verdict verdict;
        Ctx ctx;
    }

    /**
     * דוגם (Java, תהליך האב): כמה פעמים בשנייה בודקים איפה התוכנית ומה ערכי המשתנים שבתנאי.
     * מהלולאה הפנימית החוצה: לולאה שהמשתנים שלה לא זזים (או רק מתרחקים) לאורך שנייה וחצי — לא נגמרת;
     * לולאה שמתקדמת לסוף — מחכים (ואחרי הרבה זמן: "רצה יותר מדי"); לולאה שמתחילה מחדש כל פעם (פנימית) — בודקים את החיצונית.
     */
    static final class Sampler {
        private final List<Sample> samples = new ArrayList<Sample>();
        private final Map<String, Long> present = new HashMap<String, Long>();   // לולאה → מתי התחילה להופיע בכל הדגימות ברצף
        boolean quiet;

        void reset() {
            samples.clear();
            present.clear();
        }

        Hit add(Sample s) {
            samples.add(s);
            while (samples.size() > 400 || !samples.isEmpty() && s.t - samples.get(0).t > LONG_TOWARD_MS + 1000) samples.remove(0);
            Map<String, Long> now = new HashMap<String, Long>();
            for (Ctx c : s.ctxs) {
                Long since = present.get(c.key());
                now.put(c.key(), since == null ? s.t : since);
            }
            present.clear();
            present.putAll(now);
            // החלון: הדגימות של השנייה וחצי האחרונות
            List<Sample> w = new ArrayList<Sample>();
            for (Sample x : samples) if (s.t - x.t <= SAMPLE_WINDOW_MS + SAMPLE_EVERY_MS) w.add(x);
            if (w.size() < SAMPLE_MIN || s.t - w.get(0).t < SAMPLE_WINDOW_MS - SAMPLE_EVERY_MS) return null;
            int top = 0;
            for (Sample x : w) if (x.studentTop) top++;
            if (top * 3 < w.size()) return null;   // רוב הזמן בתוך פעולה של Java (מיון ארוך...) — לא מנחשים
            boolean toward = false;
            for (Ctx c : s.ctxs) {
                if (present.get(c.key()) == null || s.t - present.get(c.key()) < SAMPLE_WINDOW_MS - SAMPLE_EVERY_MS) continue;   // לא בכל החלון
                List<Map<String, String>> seq = new ArrayList<Map<String, String>>();
                boolean refs = false, mutSame = true, ran = false;
                for (Sample x : w) {
                    Ctx in = find(x, c.key());
                    if (in == null) { seq = null; break; }
                    seq.add(in.vals);
                    ran |= in.top;
                    if (x.mutations != w.get(0).mutations) mutSame = false;
                }
                // הלולאה עצמה לא רצה אף פעם בחלון — רק חיכתה לפעולה אחת ארוכה שהיא קראה (לא לולאה שמסתובבת)
                if (seq == null || !ran) continue;
                for (String v : c.info.vars) refs |= isRef(c.vals.get(v));
                boolean unchanged = c.info.pure;
                for (Map<String, String> m : seq) if (!m.equals(seq.get(0))) unchanged = false;
                if (refs && !mutSame) unchanged = false;
                Hit h = new Hit();
                h.ctx = c;
                // שום דבר שהתנאי בודק לא זז במשך שנייה וחצי (מיליוני סיבובים) — ואין break / return שיוציא
                if (unchanged && !c.info.exits) return hit(h, Verdict.UNCHANGED, s, w);
                List<Long> ps = new ArrayList<Long>();
                for (Map<String, String> m : seq) ps.add(progress(c.info, m));
                int dec = 0, inc = 0;
                boolean known = !ps.isEmpty() && !ps.contains(null);
                for (int i = 1; known && i < ps.size(); i++) {
                    if (ps.get(i) < ps.get(i - 1)) dec++;
                    else if (ps.get(i) > ps.get(i - 1)) inc++;
                }
                if (known && dec >= 3 && inc == 0 && c.info.pure && !c.info.exits) return hit(h, Verdict.AWAY, s, w);
                if (known && inc > 0 && dec == 0) { toward = true; break; }   // מתקדמת לסוף — זה מה שלוקח זמן
                if (known && dec == 0 && inc == 0 && c.info.pure && !c.info.exits) return hit(h, Verdict.UNCHANGED, s, w);   // המרחק לסוף לא זז
            }
            // רצה כבר הרבה זמן בלי לצאת (אחרי שהתיעוד נעצר)
            for (Ctx c : s.ctxs) {
                Long since = present.get(c.key());
                if (since == null) continue;
                if (s.t - since >= (toward ? LONG_TOWARD_MS : LONG_MS)) {
                    Hit h = new Hit();
                    h.ctx = s.ctxs.get(0);
                    return hit(h, Verdict.LONG, s, w);
                }
            }
            return null;
        }

        private Hit hit(Hit h, String kind, Sample s, List<Sample> w) {
            Verdict v = new Verdict();
            v.kind = kind;
            v.line = h.ctx.info.atLine;
            Long since = present.get(h.ctx.key());
            v.ms = since == null ? 0 : s.t - since;
            v.quiet = true;
            List<Long> vals = new ArrayList<Long>();
            if (h.ctx.info.cmpVar != null)
                for (Sample x : w) {
                    Ctx in = find(x, h.ctx.key());
                    Long n = in == null ? null : num(in.vals.get(h.ctx.info.cmpVar));
                    if (n != null && (vals.isEmpty() || !vals.get(vals.size() - 1).equals(n))) vals.add(n);
                }
            v.addTrend(h.ctx.info.cmpVar, vals);
            h.verdict = v;
            return h;
        }

        private static Ctx find(Sample x, String key) {
            for (Ctx c : x.ctxs) if (c.key().equals(key)) return c;
            return null;
        }
    }

    /** הלולאה הפנימית ביותר (מתוך loops) שהשורה בתוכה, ובלי for-each; null אם אין */
    static LoopViz.Loop innermost(List<LoopViz.Loop> loops, int line) {
        LoopViz.Loop best = null;
        for (LoopViz.Loop l : loops) {
            if (l.kind.equals("foreach") || line < l.header || line > l.end) continue;
            if (best == null || l.header >= best.header) best = l;
        }
        return best;
    }

    /** כל הלולאות שהשורה בתוכן, מהפנימית לחיצונית (בלי for-each) */
    static List<LoopViz.Loop> around(List<LoopViz.Loop> loops, int line) {
        List<LoopViz.Loop> res = new ArrayList<LoopViz.Loop>();
        for (LoopViz.Loop l : loops) if (!l.kind.equals("foreach") && line >= l.header && line <= l.end) res.add(l);
        java.util.Collections.sort(res, new java.util.Comparator<LoopViz.Loop>() {
            public int compare(LoopViz.Loop a, LoopViz.Loop b) {
                return b.header - a.header;
            }
        });
        return res;
    }

    /** הלולאה ששורת התנאי שלה היא line (while / for: הכותרת; do: הסוף) */
    static LoopViz.Loop at(List<LoopViz.Loop> loops, int line) {
        for (LoopViz.Loop l : loops) {
            if (l.kind.equals("foreach")) continue;
            if (l.kind.equals("do") ? l.end == line : l.header == line) return l;
        }
        return null;
    }
}

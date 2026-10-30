package unit4.collectionsLib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import unit4.collectionsLib.Model.StringView;
import unit4.collectionsLib.Model.View;

/**
 * מחרוזות בהרצה שורה-שורה: כל משתנה String מוצג כשורת תאים (תו בכל תא, עם האינדקסים),
 * ולפי השורה שעומדת לרוץ מסמנים מה היא עושה במחרוזת — לפני שהיא רצה:
 * charAt — חץ לתו; substring — החלק שנחתך; indexOf / contains / startsWith — החלק שנמצא; length — כל המחרוזת;
 * equals / compareTo — המקום הראשון שבו המחרוזות שונות. ההסבר (למשל s.substring(1, 4) → "ell") מופיע מתחת לתאים.
 *
 * הפעולות הן הפעולות של המחלקה String שנלמדות בתוכנית הלימודים במדעי המחשב (length, charAt, substring, indexOf,
 * equals, compareTo...) ועוד כמה נפוצות (toUpperCase, contains, startsWith...).
 * ערכי הפרמטרים מחושבים מהמשתנים של השורה (מספרים, תווים, מחרוזות, i+1, s.length()-1...); ביטוי מסובך יותר — רק ההסבר בלי סימון.
 */
final class StringViz {
    private StringViz() {}

    static final int MAX_CELLS = 80;
    /** null בביטוי (p != null) */
    static final Object NULL = new Object() {
        @Override
        public String toString() {
            return "null";
        }
    };

    /** מבנה מהספרייה כפי שהוא מופיע במשתנים: Queue [1,2,3] / Stack [3,2,1] / Node [5] — סוג ותוכן (בשביל isEmpty ו-null) */
    static final class Struct {
        final String kind;
        final List<String> items = new ArrayList<String>();

        Struct(String kind) {
            this.kind = kind;
        }
    }
    /** מפריד בין הקוד והתוצאה (משמאל לימין) לבין ההסבר בעברית — שמוצג בשורה משלו, כדי שלא יתהפך */
    static final String NOTE = "\u0001";
    private static final Pattern CALL = Pattern.compile("\\.\\s*(length|isEmpty|charAt|substring|indexOf|lastIndexOf|contains|startsWith|endsWith"
            + "|equals|equalsIgnoreCase|compareTo|compareToIgnoreCase|toUpperCase|toLowerCase|trim|replace|concat)\\s*\\(");

    /** הערך האחרון של כל משתנה מחרוזת — כדי לסמן מחרוזת שהשתנתה */
    private static final Map<String, String> last = new HashMap<String, String>();

    /** vars = המשתנים של השורה {שם, ערך כפי שה-Tracer שלח}; code = השורה שעומדת לרוץ (או null) */
    static List<View> views(List<String[]> vars, String code) {
        Map<String, Object> env = new HashMap<String, Object>();
        for (String[] v : vars) {
            Object val = parseValue(v[1]);
            if (val != null) env.put(v[0], val);
        }
        List<View> res = new ArrayList<View>();
        Map<String, String> now = new HashMap<String, String>();
        for (String[] v : vars) {
            if (!(env.get(v[0]) instanceof String) || !isStringLiteral(v[1])) continue;
            String text = (String) env.get(v[0]);
            StringView sv = new StringView();
            sv.title = v[0];
            sv.truncated = text.length() >= MAX_CELLS && text.endsWith("…");   // ה-Tracer קיצר מחרוזת ארוכה
            sv.text = text.length() > MAX_CELLS ? text.substring(0, MAX_CELLS) : text;
            String prev = last.get(v[0]);
            sv.changed = prev != null && !prev.equals(text);
            now.put(v[0], text);
            if (code != null) analyze(sv, v[0], text, code, env);
            sv.focus = !sv.ops.isEmpty() || sv.changed;
            res.add(sv);
        }
        last.clear();
        last.putAll(now);
        return res;
    }

    private static boolean isStringLiteral(String s) {
        return s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"");
    }

    /** ערך משתנה כפי שה-Tracer שלח: 5 → מספר, 'a' → תו, "abc" → מחרוזת; אחרת null */
    static Object parseValue(String s) {
        if (s == null) return null;
        if (isStringLiteral(s)) return s.substring(1, s.length() - 1);
        if (s.length() == 3 && s.startsWith("'") && s.endsWith("'")) return s.charAt(1);
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** כל הפעולות על המשתנה name בשורה */
    private static void analyze(StringView sv, String name, String s, String code, Map<String, Object> env) {
        Matcher m = Pattern.compile("(?<![\\w.])" + Pattern.quote(name) + "\\s*\\.\\s*\\w+\\s*\\(").matcher(code);
        while (m.find()) {
            Matcher c = CALL.matcher(code);
            if (!c.find(m.start() + name.length()) || c.start() != skipSpaces(code, m.start() + name.length())) continue;
            int open = c.end() - 1, close = matching(code, open);
            if (close < 0) continue;
            List<String> args = splitArgs(code.substring(open + 1, close));
            List<Object> vals = new ArrayList<Object>();
            boolean ok = true;
            for (String a : args) {
                Object v = eval(a, env);
                if (v == null) ok = false;
                vals.add(v);
            }
            String call = name + "." + c.group(1) + "(" + String.join(", ", trimAll(args)) + ")";
            if (!ok) continue;   // פרמטר שלא הצלחנו לחשב — לא מנחשים
            apply(sv, s, c.group(1), args, vals, call);
        }
    }

    private static int skipSpaces(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    private static void apply(StringView sv, String s, String op, List<String> args, List<Object> v, String call) {
        int n = s.length();
        try {
            switch (op) {
                case "length":
                    if (sv.from < 0) { sv.from = 0; sv.to = n; }
                    sv.ops.add(call + "  →  " + n);
                    return;
                case "isEmpty":
                    sv.ops.add(call + "  →  " + (n == 0));
                    return;
                case "charAt": {
                    int i = num(v.get(0));
                    if (i < 0 || i >= n) { outOfRange(sv, call, "אין תו במקום " + i + " (האינדקסים: 0 עד " + (n - 1) + ")"); return; }
                    arrow(sv, i, simpleName(args.get(0)) ? args.get(0).trim() : "charAt");
                    sv.ops.add(call + "  →  '" + s.charAt(i) + "'");
                    return;
                }
                case "substring": {
                    int a = num(v.get(0)), b = v.size() > 1 ? num(v.get(1)) : n;
                    if (a < 0 || b > n || a > b) {
                        outOfRange(sv, call, "אפשר לחתוך רק בין 0 ל-" + n + ", וההתחלה לא אחרי הסוף");
                        return;
                    }
                    if (sv.from < 0 || sv.found) { sv.from = a; sv.to = b; sv.found = false; }
                    sv.ops.add(call + "  →  \"" + s.substring(a, b) + "\"" + NOTE + "חיתוך: מתו " + a + " עד לפני תו " + b);
                    return;
                }
                case "indexOf":
                case "lastIndexOf": {
                    String x = text(v.get(0));
                    boolean last = op.equals("lastIndexOf");
                    int r;
                    if (v.size() > 1) {
                        int f = num(v.get(1));
                        if (f >= 0 && f < n) arrow(sv, f, "מחפשים מכאן");
                        r = last ? s.lastIndexOf(x, f) : s.indexOf(x, f);
                    } else {
                        r = last ? s.lastIndexOf(x) : s.indexOf(x);
                    }
                    if (r >= 0) found(sv, r, r + Math.max(1, x.length()));
                    sv.ops.add(call + "  →  " + r + (r < 0 ? NOTE + "לא נמצא" : NOTE + "נמצא במקום " + r));
                    return;
                }
                case "contains": {
                    String x = text(v.get(0));
                    int r = s.indexOf(x);
                    if (r >= 0 && !x.isEmpty()) found(sv, r, r + x.length());
                    sv.ops.add(call + "  →  " + (r >= 0));
                    return;
                }
                case "startsWith":
                case "endsWith": {
                    String x = text(v.get(0));
                    boolean r = op.equals("startsWith") ? s.startsWith(x) : s.endsWith(x);
                    if (r && !x.isEmpty()) found(sv, op.equals("startsWith") ? 0 : n - x.length(), op.equals("startsWith") ? x.length() : n);
                    sv.ops.add(call + "  →  " + r);
                    return;
                }
                case "equals":
                case "equalsIgnoreCase":
                case "compareTo":
                case "compareToIgnoreCase": {
                    String x = text(v.get(0));
                    boolean ignore = op.endsWith("IgnoreCase");
                    String a = ignore ? s.toLowerCase() : s, b = ignore ? x.toLowerCase() : x;
                    int k = 0;
                    while (k < a.length() && k < b.length() && a.charAt(k) == b.charAt(k)) k++;
                    boolean same = a.equals(b);
                    if (!same && k < n) arrow(sv, k, "כאן שונה");
                    String r = op.startsWith("equals") ? String.valueOf(op.equals("equals") ? s.equals(x) : s.equalsIgnoreCase(x))
                            : String.valueOf(op.equals("compareTo") ? s.compareTo(x) : s.compareToIgnoreCase(x));
                    String why = same ? "" : k < a.length() && k < b.length() ? NOTE + "השוני הראשון במקום " + k + ":  '" + s.charAt(k) + "'  מול  '" + x.charAt(k) + "'"
                            : NOTE + "אחת המחרוזות נגמרת קודם";
                    sv.ops.add(call + "  →  " + r + why);
                    return;
                }
                case "toUpperCase":
                    sv.ops.add(call + "  →  \"" + s.toUpperCase() + "\"" + NOTE + "מחרוזת חדשה — " + sv.title + " עצמה לא משתנה");
                    return;
                case "toLowerCase":
                    sv.ops.add(call + "  →  \"" + s.toLowerCase() + "\"" + NOTE + "מחרוזת חדשה — " + sv.title + " עצמה לא משתנה");
                    return;
                case "trim":
                    sv.ops.add(call + "  →  \"" + s.trim() + "\"");
                    return;
                case "replace":
                    sv.ops.add(call + "  →  \"" + s.replace(text(v.get(0)), text(v.get(1))) + "\"" + NOTE + "מחרוזת חדשה — " + sv.title + " עצמה לא משתנה");
                    return;
                case "concat":
                    sv.ops.add(call + "  →  \"" + s.concat(text(v.get(0))) + "\"");
                    return;
                default:
            }
        } catch (RuntimeException e) {
            // פרמטרים מסוג לא צפוי — בלי סימון
        }
    }

    private static void outOfRange(StringView sv, String call, String why) {
        sv.error = true;
        sv.ops.add(call + "  →  StringIndexOutOfBoundsException" + NOTE + "שגיאה! " + why);
    }

    private static void found(StringView sv, int from, int to) {
        if (sv.from >= 0 && !sv.found) return;   // חיתוך שכבר סומן בשורה — הוא החשוב
        sv.from = from;
        sv.to = to;
        sv.found = true;
    }

    private static void arrow(StringView sv, int i, String label) {
        List<String> l = sv.arrows.get(i);
        if (l == null) sv.arrows.put(i, l = new ArrayList<String>());
        if (!l.contains(label)) l.add(label);
    }

    private static boolean simpleName(String s) {
        return s.trim().matches("[A-Za-z_]\\w*");
    }

    private static int num(Object o) {
        if (o instanceof Integer) return (Integer) o;
        if (o instanceof Character) return (Character) o;
        throw new IllegalArgumentException();
    }

    private static String text(Object o) {
        if (o instanceof String) return (String) o;
        if (o instanceof Character) return String.valueOf(o);
        throw new IllegalArgumentException();
    }

    private static List<String> trimAll(List<String> l) {
        List<String> r = new ArrayList<String>();
        for (String s : l) r.add(s.trim());
        return r;
    }

    /** הסוגר שסוגר את זה שב-open (מדלגים על מחרוזות ותווים), או -1 */
    static int matching(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' || ch == '\'') { i = skipLiteral(s, i); continue; }
            if (ch == '(') depth++;
            else if (ch == ')' && --depth == 0) return i;
        }
        return -1;
    }

    private static int skipLiteral(String s, int i) {
        char q = s.charAt(i);
        for (int j = i + 1; j < s.length(); j++) {
            if (s.charAt(j) == '\\') { j++; continue; }
            if (s.charAt(j) == q) return j;
        }
        return s.length();
    }

    static List<String> splitArgs(String s) {
        List<String> res = new ArrayList<String>();
        if (s.trim().isEmpty()) return res;
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' || ch == '\'') { i = skipLiteral(s, i); continue; }
            if (ch == '(') depth++;
            else if (ch == ')') depth--;
            else if (ch == ',' && depth == 0) { res.add(s.substring(start, i)); start = i + 1; }
        }
        res.add(s.substring(start));
        return res;
    }

    // ------------------------------------------------------------ חישוב ביטוי פשוט: 3, i, i+1, s.length()-1, 'a', "ab", (n-i)/2

    static Object eval(String expr, Map<String, Object> env) {
        try {
            Parser p = new Parser(expr.trim(), env);
            Object v = p.expr();
            p.ws();
            return p.i == p.s.length() ? v : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final class Parser {
        final String s;
        final Map<String, Object> env;
        int i;

        Parser(String s, Map<String, Object> env) {
            this.s = s;
            this.env = env;
        }

        void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        boolean eat(char c) {
            ws();
            if (i < s.length() && s.charAt(i) == c) { i++; return true; }
            return false;
        }

        Object expr() {
            Object v = term();
            while (true) {
                if (eat('+')) {
                    Object r = term();
                    v = v instanceof String || r instanceof String ? String.valueOf(v) + r : (Object) (num(v) + num(r));
                } else if (eat('-')) {
                    v = num(v) - num(term());
                } else {
                    return v;
                }
            }
        }

        Object term() {
            Object v = unary();
            while (true) {
                if (eat('*')) v = num(v) * num(unary());
                else if (eat('/')) v = num(v) / num(unary());
                else if (eat('%')) v = num(v) % num(unary());
                else return v;
            }
        }

        Object unary() {
            if (eat('-')) return -num(unary());
            if (eat('(')) {
                Object v = expr();
                if (!eat(')')) throw new IllegalArgumentException();
                return v;
            }
            return primary();
        }

        Object primary() {
            ws();
            if (i >= s.length()) throw new IllegalArgumentException();
            char ch = s.charAt(i);
            if (Character.isDigit(ch)) {
                int j = i;
                while (j < s.length() && Character.isDigit(s.charAt(j))) j++;
                Object v = Integer.valueOf(s.substring(i, j));
                i = j;
                return v;
            }
            if (ch == '\'' || ch == '"') {
                int j = skipLiteral(s, i);
                String body = unescape(s.substring(i + 1, Math.min(j, s.length())));
                i = j + 1;
                if (ch == '\'') {
                    if (body.length() != 1) throw new IllegalArgumentException();
                    return body.charAt(0);
                }
                return body;
            }
            if (Character.isJavaIdentifierStart(ch)) {
                int j = i;
                while (j < s.length() && Character.isJavaIdentifierPart(s.charAt(j))) j++;
                String name = s.substring(i, j);
                i = j;
                if (name.equals("null")) return NULL;
                if (name.equals("true") || name.equals("false")) return Boolean.valueOf(name);
                Object v = env.get(name);
                if (v == null) throw new IllegalArgumentException();
                // אחרי השם: arr[i]  arr.length  s.length()  s.charAt(i)  q.isEmpty()
                while (true) {
                    int save = i;
                    ws();
                    if (i < s.length() && s.charAt(i) == '[' && v instanceof List) {
                        i++;
                        int k = num(expr());
                        if (!eat(']')) throw new IllegalArgumentException();
                        List<?> l = (List<?>) v;
                        if (k < 0 || k >= l.size()) throw new IllegalArgumentException();
                        v = l.get(k);
                        continue;
                    }
                    if (i < s.length() && s.charAt(i) == '.') {
                        i++;
                        ws();
                        int m = i;
                        while (m < s.length() && Character.isJavaIdentifierPart(s.charAt(m))) m++;
                        String member = s.substring(i, m);
                        i = m;
                        boolean call = eat('(');
                        if (member.equals("length") && !call && v instanceof List) { v = ((List<?>) v).size(); continue; }
                        if (member.equals("length") && call && eat(')') && v instanceof String) { v = ((String) v).length(); continue; }
                        if (member.equals("isEmpty") && call && eat(')')) {
                            if (v instanceof String) { v = ((String) v).isEmpty(); continue; }
                            if (v instanceof Struct) { v = ((Struct) v).items.isEmpty(); continue; }
                        }
                        if (member.equals("charAt") && call && v instanceof String) {
                            int k = num(expr());
                            if (!eat(')') || k < 0 || k >= ((String) v).length()) throw new IllegalArgumentException();
                            v = ((String) v).charAt(k);
                            continue;
                        }
                        throw new IllegalArgumentException();
                    }
                    i = save;
                    return v;
                }
            }
            throw new IllegalArgumentException();
        }
    }

    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            if (c == '\\' && k + 1 < s.length()) {
                char n = s.charAt(++k);
                b.append(n == 'n' ? '\n' : n == 't' ? '\t' : n);
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }
}

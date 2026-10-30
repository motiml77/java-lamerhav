package unit4.collectionsLib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import unit4.collectionsLib.Model.Frame;
import unit4.collectionsLib.Model.LoopGridView;
import unit4.collectionsLib.Model.LoopView;
import unit4.collectionsLib.Model.View;

/**
 * לולאות בהרצה שורה-שורה: לכל לולאה שהשורה הנוכחית נמצאת בתוכה (for / while / do / for-each) — כרטיס:
 * מספר הסיבוב, התנאי עם הערכים (i &lt; n → 2 &lt; 5 → true), וב-for שסופר — "סרגל" של כל הערכים של המשתנה:
 * מה כבר עבר, איפה עכשיו, ומתי הלולאה תיגמר. לולאות מקוננות — כרטיס בתוך כרטיס, ובשתי לולאות for שסופרות
 * גם טבלה של כל הזוגות (i, j): אילו כבר עברו ואיפה עכשיו (גם כשהגבול של הפנימית תלוי בחיצונית, כמו j &lt; i).
 *
 * הכול מחושב מקוד המקור ומהמשתנים של השורה — בלי לשנות את הקוד של התלמיד.
 */
final class LoopViz {
    private LoopViz() {}

    static final int MAX_TRACK = 40, MAX_GRID = 16;

    /** לולאה בקוד */
    static final class Loop {
        int header, bodyStart, end;        // שורות (מ-1): שורת הלולאה, תחילת הגוף, הסוף
        String kind;                       // for / while / do / foreach
        String text;                       // הכותרת, למשל for (int i = 0; i < n; i++)
        String cond = "";                  // התנאי
        String var, init, op, bound;       // for שסופר: i, 0, <, n
        int stepBy;                        // +1 / -1 / +k
    }

    /** מצב לולאה פעילה (לכל קריאה לפעולה בנפרד — גם ברקורסיה) */
    private static final class State {
        int iteration, lastLine;
        Integer start;                     // הערך ההתחלתי של המשתנה
    }

    private static final Map<String, List<Loop>> parsed = new HashMap<String, List<Loop>>();
    private static final Map<String, State> states = new HashMap<String, State>();

    static List<View> views(List<Frame> frames, List<String[]> vars) {
        List<View> res = new ArrayList<View>();
        if (frames.isEmpty()) return res;
        Frame f = frames.get(0);
        String key = f.packagePath() + f.file;
        List<Loop> loops = parsed.get(key);
        if (loops == null) {
            List<String> lines = SourceFiles.lines(f);
            loops = lines == null ? new ArrayList<Loop>() : parse(lines);
            parsed.put(key, loops);
        }
        Map<String, Object> env = new HashMap<String, Object>();
        for (String[] v : vars) {
            Object val = StringViz.parseValue(v[1]);
            if (val == null && (v[1].equals("true") || v[1].equals("false"))) val = Boolean.valueOf(v[1]);
            if (val != null) env.put(v[0], val);
        }
        String prefix = frames.size() + ":" + key + ":";   // עומק הקריאה — ברקורסיה לכל קריאה לולאות משלה
        // הלולאות שהשורה בתוכן: מהחיצונית לפנימית
        List<Loop> active = new ArrayList<Loop>();
        for (Loop l : loops) if (f.line >= l.header && f.line <= l.end) active.add(l);
        // לולאות שיצאנו מהן — מתחילות מחדש בפעם הבאה
        for (Loop l : loops) {
            if (active.contains(l)) continue;
            states.remove(prefix + l.header);
        }
        List<LoopView> chain = new ArrayList<LoopView>();
        for (int d = 0; d < active.size(); d++) {
            Loop l = active.get(d);
            State s = states.get(prefix + l.header);
            if (s == null) {
                states.put(prefix + l.header, s = new State());
                s.lastLine = -1;
            }
            boolean inBody = f.line >= l.bodyStart && f.line > l.header;
            boolean sameLineBody = l.bodyStart == l.header;
            boolean outside = s.lastLine < 0 || s.lastLine > l.end || s.lastLine < l.header;
            if (l.kind.equals("do")) {   // do: סיבוב חדש כשנכנסים, ובכל חזרה מהתנאי (בסוף) אל תחילת הגוף
                if (outside || s.lastLine == l.end && f.line < l.end) s.iteration++;
            } else if (sameLineBody) {
                if (f.line == l.header && s.lastLine == l.header) s.iteration++;   // גוף באותה שורה: כל ביקור נוסף בשורה = סיבוב
            } else if (inBody && (s.lastLine == l.header || outside)) {
                s.iteration++;   // מהכותרת (בדיקת התנאי) אל הגוף — סיבוב חדש
            }
            s.lastLine = f.line;
            if (l.var != null && s.start == null) {
                Object st = StringViz.eval(l.init, env);
                if (st instanceof Integer) s.start = (Integer) st;
                else if (env.get(l.var) instanceof Integer) s.start = (Integer) env.get(l.var);
            }
            LoopView v = new LoopView();
            v.depth = d;
            v.kind = l.kind;
            v.header = l.text;
            v.title = d == 0 ? (active.size() > 1 ? "לולאה חיצונית" : "לולאה") : d == active.size() - 1 ? "לולאה פנימית" : "לולאה פנימית " + d;
            v.iteration = s.iteration;
            v.atHeader = f.line == l.header && !l.kind.equals("do") || l.kind.equals("do") && f.line == l.end;
            v.cond = l.cond;
            v.condValues = substitute(l.cond, env);
            v.condResult = evalCondition(l.cond, env);
            v.focus = d == active.size() - 1;
            if (l.var != null && s.start != null && env.get(l.var) instanceof Integer) {
                Object b = StringViz.eval(l.bound, env);
                if (b instanceof Integer) track(v, l, s.start, (Integer) b, (Integer) env.get(l.var));
            }
            chain.add(v);
            res.add(v);
        }
        // שתי לולאות for שסופרות, זו בתוך זו: טבלה של כל הזוגות
        for (int d = 0; d + 1 < active.size(); d++) {
            Loop o = active.get(d), in = active.get(d + 1);
            State so = states.get(prefix + o.header), si = states.get(prefix + in.header);
            if (o.var == null || in.var == null || so == null || so.start == null) continue;
            LoopGridView g = grid(o, in, so.start, si == null ? null : si.start, env);
            if (g != null) {
                g.focus = true;
                res.add(g);
            }
        }
        return res;
    }

    /** הערכים של המשתנה מההתחלה ועד הגבול (רק אם יש עד 40), ואיפה הוא עכשיו */
    private static void track(LoopView v, Loop l, int start, int bound, int now) {
        List<Integer> vals = values(start, l.op, bound, l.stepBy);
        if (vals == null || vals.isEmpty() || vals.size() > MAX_TRACK) {
            v.var = l.var;
            v.varValue = now;
            return;
        }
        v.var = l.var;
        v.varValue = now;
        v.values.addAll(vals);
        v.currentIndex = vals.indexOf(now);
        v.done = v.currentIndex < 0 && !vals.isEmpty() && passed(now, vals.get(vals.size() - 1), l.stepBy);
    }

    private static boolean passed(int now, int last, int step) {
        return step > 0 ? now > last : now < last;
    }

    /** כל הערכים שהמשתנה יקבל, או null אם הלולאה לא סופרת בצורה פשוטה */
    static List<Integer> values(int start, String op, int bound, int step) {
        if (step == 0) return null;
        List<Integer> res = new ArrayList<Integer>();
        for (int x = start; res.size() <= MAX_TRACK; x += step) {
            boolean ok;
            switch (op) {
                case "<": ok = x < bound; break;
                case "<=": ok = x <= bound; break;
                case ">": ok = x > bound; break;
                case ">=": ok = x >= bound; break;
                case "!=": ok = x != bound; break;
                default: return null;
            }
            if (!ok) break;
            res.add(x);
        }
        return res;
    }

    /** טבלת הזוגות (i, j): שורה לכל ערך של החיצוני, ובה הערכים של הפנימי (שיכולים להיות תלויים ב-i) */
    private static LoopGridView grid(Loop o, Loop in, int oStart, Integer iStart, Map<String, Object> env) {
        Object ob = StringViz.eval(o.bound, env);
        if (!(ob instanceof Integer) || !(env.get(o.var) instanceof Integer)) return null;
        List<Integer> rows = values(oStart, o.op, (Integer) ob, o.stepBy);
        if (rows == null || rows.isEmpty() || rows.size() > MAX_GRID) return null;
        int curI = (Integer) env.get(o.var);
        Integer curJ = env.get(in.var) instanceof Integer ? (Integer) env.get(in.var) : null;
        LoopGridView g = new LoopGridView();
        g.rowVar = o.var;
        g.colVar = in.var;
        g.title = "הזוגות (" + o.var + ", " + in.var + ")";
        int minCol = Integer.MAX_VALUE, maxCol = Integer.MIN_VALUE;
        List<List<Integer>> perRow = new ArrayList<List<Integer>>();
        for (int r : rows) {
            Map<String, Object> e = new HashMap<String, Object>(env);
            e.put(o.var, r);
            Object st = StringViz.eval(in.init, e), b = StringViz.eval(in.bound, e);
            if (!(st instanceof Integer) || !(b instanceof Integer)) return null;
            List<Integer> cols = values((Integer) st, in.op, (Integer) b, in.stepBy);
            if (cols == null || cols.size() > MAX_GRID) return null;
            perRow.add(cols);
            for (int c : cols) { minCol = Math.min(minCol, c); maxCol = Math.max(maxCol, c); }
        }
        if (minCol > maxCol) return null;
        boolean colsUp = in.stepBy > 0;
        for (int c = colsUp ? minCol : maxCol; colsUp ? c <= maxCol : c >= minCol; c += colsUp ? 1 : -1) g.cols.add(c);
        if (g.cols.size() > MAX_GRID) return null;
        g.rows.addAll(rows);
        int rowNow = rows.indexOf(curI);
        for (int ri = 0; ri < rows.size(); ri++) {
            List<Integer> cols = perRow.get(ri);
            int[] st = new int[g.cols.size()];
            for (int ci = 0; ci < g.cols.size(); ci++) {
                int c = g.cols.get(ci);
                int k = cols.indexOf(c);
                if (k < 0) { st[ci] = LoopGridView.NONE; continue; }
                boolean before = rowNow < 0 ? passed(curI, rows.get(rows.size() - 1), o.stepBy) : ri < rowNow;
                if (before) st[ci] = LoopGridView.DONE;
                else if (ri == rowNow && curJ != null) {
                    int jk = cols.indexOf(curJ);
                    if (k < jk || jk < 0 && passed(curJ, cols.isEmpty() ? curJ : cols.get(cols.size() - 1), in.stepBy)) st[ci] = LoopGridView.DONE;
                    else if (k == jk) st[ci] = LoopGridView.NOW;
                    else st[ci] = LoopGridView.TODO;
                } else st[ci] = LoopGridView.TODO;
            }
            g.cells.add(st);
        }
        return g;
    }

    // ------------------------------------------------------------ תנאי: הצבת הערכים וחישוב

    /** i < arr.length  →  2 < 5 (מחליפים משתנים בערכים שלהם; s.length() → האורך) */
    static String substitute(String cond, Map<String, Object> env) {
        if (cond == null || cond.isEmpty()) return "";
        StringBuilder b = new StringBuilder();
        Matcher m = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'|([A-Za-z_]\\w*)(\\s*\\.\\s*length\\s*\\(\\s*\\))?").matcher(cond);
        int last = 0;
        boolean any = false;
        while (m.find()) {
            b.append(cond, last, m.start());
            last = m.end();
            String id = m.group(1);
            Object v = id == null ? null : env.get(id);
            boolean dotBefore = m.start() > 0 && cond.charAt(m.start() - 1) == '.';
            if (v == null || dotBefore) { b.append(m.group()); continue; }
            if (m.group(2) != null) {
                if (v instanceof String) { b.append(((String) v).length()); any = true; } else b.append(m.group());
                continue;
            }
            b.append(v instanceof String ? "\"" + v + "\"" : v instanceof Character ? "'" + v + "'" : String.valueOf(v));
            any = true;
        }
        b.append(cond.substring(last));
        return any ? b.toString() : "";
    }

    /** true / false אם אפשר לחשב (השוואות, &&, ||, !), אחרת null */
    static Boolean evalCondition(String cond, Map<String, Object> env) {
        try {
            return cond == null || cond.trim().isEmpty() ? null : orExpr(cond.trim(), env);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Boolean orExpr(String s, Map<String, Object> env) {
        List<String> parts = splitTop(s, "||");
        if (parts.size() > 1) {
            for (String p : parts) if (orExpr(p.trim(), env)) return true;
            return false;
        }
        parts = splitTop(s, "&&");
        if (parts.size() > 1) {
            for (String p : parts) if (!orExpr(p.trim(), env)) return false;
            return true;
        }
        if (s.startsWith("!") && !s.startsWith("!=")) return !orExpr(s.substring(1).trim(), env);
        if (s.startsWith("(") && StringViz.matching(s, 0) == s.length() - 1) return orExpr(s.substring(1, s.length() - 1).trim(), env);
        for (String op : new String[] { "<=", ">=", "==", "!=", "<", ">" }) {
            List<String> sides = splitTop(s, op);
            if (sides.size() != 2) continue;
            Object a = StringViz.eval(sides.get(0), env), b = StringViz.eval(sides.get(1), env);
            if (a == null || b == null) throw new IllegalArgumentException();
            if (op.equals("==") || op.equals("!=")) {
                boolean eq = (a instanceof Character || a instanceof Integer) && (b instanceof Character || b instanceof Integer)
                        ? num(a) == num(b) : a.equals(b);
                return op.equals("==") == eq;
            }
            int x = num(a), y = num(b);
            switch (op) {
                case "<=": return x <= y;
                case ">=": return x >= y;
                case "<": return x < y;
                default: return x > y;
            }
        }
        if (s.equals("true")) return true;
        if (s.equals("false")) return false;
        Object v = env.get(s);
        if (v instanceof Boolean) return (Boolean) v;
        throw new IllegalArgumentException();
    }

    private static int num(Object o) {
        if (o instanceof Integer) return (Integer) o;
        if (o instanceof Character) return (Character) o;
        throw new IllegalArgumentException();
    }

    /** מפצל לפי op ברמה העליונה (לא בתוך סוגריים / מחרוזות). ל-"<" לא מפצלים את "<=" / "<<" */
    private static List<String> splitTop(String s, String op) {
        List<String> res = new ArrayList<String>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                char q = c;
                for (i++; i < s.length() && s.charAt(i) != q; i++) if (s.charAt(i) == '\\') i++;
                continue;
            }
            if (c == '(' || c == '[') depth++;
            else if (c == ')' || c == ']') depth--;
            else if (depth == 0 && s.startsWith(op, i)) {
                if (op.length() == 1) {
                    char next = i + 1 < s.length() ? s.charAt(i + 1) : ' ', prev = i > 0 ? s.charAt(i - 1) : ' ';
                    if (next == '=' || next == op.charAt(0) || prev == op.charAt(0) || prev == '<' || prev == '>') continue;
                }
                if (op.equals("==") || op.equals("!=")) {
                    char prev = i > 0 ? s.charAt(i - 1) : ' ';
                    if (prev == '<' || prev == '>' || prev == '!' || prev == '=') continue;
                }
                res.add(s.substring(start, i));
                start = i + op.length();
                i += op.length() - 1;
            }
        }
        res.add(s.substring(start));
        return res;
    }

    // ------------------------------------------------------------ איתור הלולאות בקוד

    private static final Pattern LOOP = Pattern.compile("\\b(for|while)\\s*\\(|\\bdo\\s*(\\{|$)");
    private static final Pattern COUNTING = Pattern.compile(
            "^\\s*(?:int|long|short|byte)?\\s*([A-Za-z_]\\w*)\\s*=\\s*([^;]+);\\s*([A-Za-z_]\\w*)\\s*(<=|>=|<|>|!=)\\s*([^;]+);\\s*(.+?)\\s*$");

    static List<Loop> parse(List<String> lines) {
        String text = String.join("\n", lines);
        String clean = blank(text);   // בלי הערות ומחרוזות — כדי שלא נמצא "for" בתוכן
        int[] lineOf = new int[clean.length() + 1];
        for (int i = 0, ln = 1; i <= clean.length(); i++) {
            lineOf[i] = ln;
            if (i < clean.length() && clean.charAt(i) == '\n') ln++;
        }
        List<Loop> res = new ArrayList<Loop>();
        Matcher m = LOOP.matcher(clean);
        while (m.find()) {
            Loop l = new Loop();
            l.header = lineOf[m.start()];
            int after;
            if (m.group(1) != null) {
                int open = m.end() - 1, close = StringViz.matching(clean, open);
                if (close < 0) continue;
                String inside = text.substring(open + 1, close);
                l.text = (m.group(1) + " (" + inside.replaceAll("\\s+", " ").trim() + ")");
                if (m.group(1).equals("while")) {
                    // while של do-while: מופיע אחרי } — כבר טופל עם ה-do
                    int k = m.start() - 1;
                    while (k >= 0 && Character.isWhitespace(clean.charAt(k))) k--;
                    if (k >= 0 && clean.charAt(k) == '}' && isDoClose(clean, k)) continue;
                    l.kind = "while";
                    l.cond = inside.trim();
                } else if (inside.contains(":") && !inside.contains(";")) {
                    l.kind = "foreach";
                } else {
                    l.kind = "for";
                    String[] parts = inside.split(";", -1);
                    if (parts.length == 3) l.cond = parts[1].trim();
                    counting(l, inside);
                }
                after = close + 1;
            } else {
                l.kind = "do";
                l.text = "do { ... }";
                after = m.end() - (m.group(2) != null && m.group(2).equals("{") ? 1 : 0);
            }
            // הגוף: בלוק { ... } או פקודה אחת
            int k = after;
            while (k < clean.length() && Character.isWhitespace(clean.charAt(k))) k++;
            if (k < clean.length() && clean.charAt(k) == '{') {
                int close = matchBrace(clean, k);
                if (close < 0) continue;
                l.bodyStart = lineOf[Math.min(k + 1, clean.length())];
                if (clean.substring(k + 1, close).trim().isEmpty()) l.bodyStart = l.header;
                else {
                    int first = k + 1;
                    while (first < close && Character.isWhitespace(clean.charAt(first))) first++;
                    l.bodyStart = lineOf[first];
                }
                l.end = lineOf[close];
                if (l.kind.equals("do")) {   // do { ... } while (cond);
                    Matcher w = Pattern.compile("\\G\\s*while\\s*\\(").matcher(clean);
                    if (w.find(close + 1)) {
                        int wo = w.end() - 1, wc = StringViz.matching(clean, wo);
                        if (wc > 0) {
                            l.cond = text.substring(wo + 1, wc).trim();
                            l.text = "do { ... } while (" + l.cond.replaceAll("\\s+", " ") + ")";
                            l.end = lineOf[wc];
                        }
                    }
                }
            } else {
                int semi = clean.indexOf(';', k);
                if (semi < 0) continue;
                l.bodyStart = lineOf[k];
                l.end = lineOf[semi];
            }
            res.add(l);
        }
        return res;
    }

    /** for (int i = 0; i < n; i++) — משתנה, התחלה, השוואה, גבול וקפיצה */
    private static void counting(Loop l, String inside) {
        Matcher c = COUNTING.matcher(inside.replace('\n', ' '));
        if (!c.matches() || !c.group(1).equals(c.group(3))) return;
        String v = c.group(1), upd = c.group(6).replaceAll("\\s+", "");
        int step;
        if (upd.equals(v + "++") || upd.equals("++" + v)) step = 1;
        else if (upd.equals(v + "--") || upd.equals("--" + v)) step = -1;
        else {
            Matcher u = Pattern.compile("^" + Pattern.quote(v) + "(\\+|-)=(\\d+)$").matcher(upd);
            Matcher u2 = Pattern.compile("^" + Pattern.quote(v) + "=" + Pattern.quote(v) + "(\\+|-)(\\d+)$").matcher(upd);
            if (u.matches()) step = (u.group(1).equals("+") ? 1 : -1) * Integer.parseInt(u.group(2));
            else if (u2.matches()) step = (u2.group(1).equals("+") ? 1 : -1) * Integer.parseInt(u2.group(2));
            else return;
        }
        if (step == 0) return;
        l.var = v;
        l.init = c.group(2).trim();
        l.op = c.group(4);
        l.bound = c.group(5).trim();
        l.stepBy = step;
    }

    private static boolean isDoClose(String clean, int closeBrace) {
        int depth = 0;
        for (int i = closeBrace; i >= 0; i--) {
            char c = clean.charAt(i);
            if (c == '}') depth++;
            else if (c == '{' && --depth == 0) {
                int k = i - 1;
                while (k >= 0 && Character.isWhitespace(clean.charAt(k))) k--;
                return k >= 1 && clean.startsWith("do", k - 1) && (k - 2 < 0 || !Character.isJavaIdentifierPart(clean.charAt(k - 2)));
            }
        }
        return false;
    }

    private static int matchBrace(String s, int open) {
        int depth = 0;
        for (int i = open; i < s.length(); i++) {
            if (s.charAt(i) == '{') depth++;
            else if (s.charAt(i) == '}' && --depth == 0) return i;
        }
        return -1;
    }

    /** הערות ומחרוזות → רווחים (באותו אורך, כדי שהמיקומים יישארו) */
    static String blank(String t) {
        StringBuilder b = new StringBuilder(t);
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '/' && i + 1 < t.length() && t.charAt(i + 1) == '/') {
                while (i < t.length() && t.charAt(i) != '\n') b.setCharAt(i++, ' ');
            } else if (c == '/' && i + 1 < t.length() && t.charAt(i + 1) == '*') {
                while (i < t.length() && !(t.charAt(i) == '*' && i + 1 < t.length() && t.charAt(i + 1) == '/')) {
                    if (t.charAt(i) != '\n') b.setCharAt(i, ' ');
                    i++;
                }
                if (i < t.length()) { b.setCharAt(i, ' '); if (i + 1 < t.length()) b.setCharAt(i + 1, ' '); i++; }
            } else if (c == '"' || c == '\'') {
                char q = c;
                for (i++; i < t.length() && t.charAt(i) != q && t.charAt(i) != '\n'; i++) {
                    if (t.charAt(i) == '\\' && i + 1 < t.length()) { b.setCharAt(i, ' '); i++; }
                    b.setCharAt(i, ' ');
                }
            }
        }
        return b.toString();
    }
}

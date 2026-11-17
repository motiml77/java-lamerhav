package unit4.collectionsLib;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import unit4.collectionsLib.Model.Frame;
import unit4.collectionsLib.Model.Mark;
import unit4.collectionsLib.Model.Obj;
import unit4.collectionsLib.Model.Step;

/**
 * פעולה שרצה בתוך אובייקט (d1.birthday()): מי האובייקט, ואילו מאפיינים שלו השורה קוראת / משנה.
 * - בתוך הפעולה: הכרטיס של this מקבל את השם שהקוד שקרא נתן לו ("d1"), מסגרת סגולה ושורה קטנה "birthday() רצה בתוך d1".
 *   פרמטר שהוא אובייקט מקבל את השם שלו אצל מי שקרא (other ← d1).
 * - בשורה שקוראת (d1.birthday()): הכרטיס של d1 מסומן "נכנסים ל-birthday()".
 * - בכל שורה: המאפיינים שהשורה משתמשת בהם (age, this.age, other.age) מודגשים — צהוב קריאה, כתום שינוי.
 * הכול מחושב מהקוד ומהצעדים, ולכן עובד אותו דבר ב-Java וב-C#.
 */
final class SelfViz {
    private SelfViz() {}

    static final class Info {
        String self;                 // השם של this אצל מי שקרא: "d1", "this" (פעולה של אותו אובייקט), או "" (לא ידוע)
        String method = "";          // birthday() / הבנאי Dog()
        boolean ctor;                // בנאי: האובייקט עוד בבנייה
        String calling;              // בשורה שקוראת: שם המשתנה שעליו קוראים (d1) — או null
        String callingMethod;        // ... ולאיזו פעולה (birthday)
        final Map<String, String> alias = new LinkedHashMap<String, String>();   // פרמטר → השם שלו אצל מי שקרא (other → d1)
        /** שם משתנה → מאפיין → קריאה/שינוי. מאפיין של אובייקט פנימי — לפי המסלול: t.leader.grade → "leader.grade" */
        final Map<String, Map<String, Mark>> hot = new LinkedHashMap<String, Map<String, Mark>>();
        /** תוויות אינדקס על מערך-מאפיין (scores[i] → i על התא 1): שם משתנה → מאפיין (מסלול) → אינדקס → שמות; במטריצה — השורות */
        final Map<String, Map<String, Map<Integer, List<String>>>> idx = new LinkedHashMap<String, Map<String, Map<Integer, List<String>>>>();
        /** ... והעמודות של מטריצה-מאפיין (grid[r][c] → c) */
        final Map<String, Map<String, Map<Integer, List<String>>>> cols = new LinkedHashMap<String, Map<String, Map<Integer, List<String>>>>();
    }

    private static int cachedIndex = -1;
    private static Step cachedStep;
    private static Info cached;

    /** המידע לצעד (null — אין מה להראות) */
    static synchronized Info of(Step s) {
        if (s == null) return null;
        if (s == cachedStep && s.index == cachedIndex) return cached;
        Info in = compute(s);
        cachedStep = s;
        cachedIndex = s.index;
        cached = in;
        return in;
    }

    private static Info compute(Step s) {
        if (s.where == null || s.calls.isEmpty() || s.vars == null) return null;
        Info in = new Info();
        boolean any = false;
        Frame top = s.calls.get(0);
        if (hasVar(s, "this") && s.calls.size() >= 2) {
            any = true;
            in.ctor = top.method.equals("<init>") || top.method.equals(".ctor");
            String m = in.ctor ? simple(top.className) : top.method;
            in.method = in.ctor ? "הבנאי " + m + "()" : m + "()";
            Frame caller = s.calls.get(1);
            String line = clean(SourceFiles.line(caller));
            in.self = "";
            if (line != null) {
                int open = -1;
                Matcher mm = in.ctor ? Pattern.compile("(\\w+)\\s*=\\s*new\\s+" + Pattern.quote(m) + "\\s*(?:<[^()]*>)?\\s*\\(").matcher(line)
                        : Pattern.compile("(?<![\\w.])(\\w+)\\s*\\.\\s*" + Pattern.quote(m) + "\\s*\\(").matcher(line);
                if (mm.find() && !in.ctor && (mm.group(1).equals("super") || mm.group(1).equals("base"))) {
                    in.self = outer(s);   // super.say() / base.Say(): אותו עצם, הגרסה של מחלקת העל
                    open = mm.end() - 1;
                } else if (mm.find(0)) {
                    in.self = mm.group(1);
                    open = mm.end() - 1;
                } else if (!in.ctor) {
                    Matcher own = Pattern.compile("(?<![\\w.])" + Pattern.quote(m) + "\\s*\\(").matcher(line);
                    if (own.find() && caller.className.equals(top.className)) {   // קריאה בלי נקודה מפעולה של אותה מחלקה — אותו אובייקט
                        in.self = outer(s);   // birthday() קוראת ל-bark(): גם bark() רצה בתוך d1
                        open = own.end() - 1;
                    }
                } else {
                    Matcher nw = Pattern.compile("new\\s+" + Pattern.quote(m) + "\\s*(?:<[^()]*>)?\\s*\\(").matcher(line);
                    if (nw.find()) open = nw.end() - 1;
                }
                if (open >= 0) aliases(in, top, line, open);
            }
        } else {
            // בשורה שקוראת לפעולה של אובייקט: הצעד הבא בתוך הפעולה, ו-this שלה הוא המשתנה שלפני הנקודה
            Step next = next(s);
            if (next != null && next.calls.size() == s.calls.size() + 1 && hasVar(next, "this")) {
                Frame callee = next.calls.get(0);
                if (!callee.method.equals("<init>") && !callee.method.equals(".ctor")) {
                    String line = clean(SourceFiles.line(s.calls.get(0)));
                    Matcher mm = line == null ? null
                            : Pattern.compile("(?<![\\w.])(\\w+)\\s*\\.\\s*" + Pattern.quote(callee.method) + "\\s*\\(").matcher(line);
                    if (mm != null && mm.find() && !mm.group(1).equals("this")) {
                        in.calling = mm.group(1);
                        in.callingMethod = callee.method;
                        any = true;
                    }
                }
            }
        }
        if (s.kind != Model.Kind.RETURN && hotFields(in, s)) any = true;   // בצעד היציאה השורה כבר רצה
        return any ? in : null;
    }

    /** פעולה שנקראה מפעולה של אותו אובייקט: השם של this אצל הפעולה שקראה (d1), או "this" */
    private static String outer(Step s) {
        int d = s.calls.size();
        for (int j = s.index - 1, k = 0; j >= 0 && k < 400; j--, k++) {
            Step p;
            try {
                p = Recorder.step(j);
            } catch (RuntimeException e) {
                break;
            }
            if (p == null || p.calls.size() > d - 1) continue;
            if (p.calls.size() < d - 1 || p.kind == Model.Kind.RETURN) break;
            Info in = hasVar(p, "this") ? compute(p) : null;
            return in != null && in.self != null && !in.self.isEmpty() ? in.self : "this";
        }
        return "this";
    }

    /** הפרמטרים שקיבלו משתנה פשוט (isOlderThan(d1) → other ← d1) */
    private static void aliases(Info in, Frame callee, String line, int open) {
        List<String> params = CallViz.params(callee);
        if (params == null || params.isEmpty()) return;
        int depth = 0, start = open + 1, k = 0;
        for (int i = open + 1; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '(' || c == '[' || c == '{') depth++;
            else if ((c == ')' || c == ']' || c == '}') && depth > 0) depth--;
            else if ((c == ',' && depth == 0) || c == ')') {
                String a = line.substring(start, i).trim();
                if (k < params.size() && a.matches("[A-Za-z_]\\w*") && !a.equals(params.get(k)) && !a.equals("null")
                        && !a.equals("true") && !a.equals("false")) in.alias.put(params.get(k), a);
                k++;
                start = i + 1;
                if (c == ')') return;
            }
        }
    }

    /**
     * המאפיינים שהשורה הנוכחית קוראת / משנה: age או this.age (של this), other.age (של other) — וגם של אובייקט שמצויר בתוך כרטיס:
     * t.leader.grade (או leader.grade בתוך פעולה של t) → "leader.grade" אצל t, ו-s.grade אצל s (שמצויר בתוך t). ומערך-מאפיין:
     * scores[i] / this.scores[i] → התווית i על התא שבו i עומד (כמו במערך רגיל)
     */
    private static boolean hotFields(Info in, Step s) {
        String line = clean(SourceFiles.line(s.where));
        if (line == null) return false;
        boolean found = false;
        for (Obj o : Recorder.allObjectsOf(s)) {
            for (String label : o.labels) {
                boolean self = label.equals("this");
                found |= hotIn(in, s, line, label, o, "", Pattern.quote(label) + "\\s*\\.\\s*", self ? "" : null, 0);
            }
        }
        return found;
    }

    /**
     * המאפיינים של o (שמגיעים אליו ב-label ואחריו path) שהשורה נוגעת בהם. dotted = התבנית עד המאפיין ("t\.leader\."),
     * bare = בתוך פעולה של האובייקט החיצוני — אותו מסלול בלי this ("leader\.") — או null
     */
    private static boolean hotIn(Info in, Step s, String line, String label, Obj o, String path, String dotted, String bare, int depth) {
        boolean found = false;
        for (String[] f : o.fields) {
            String fn = f[0];
            if (!fn.matches("\\w+")) continue;
            String q = Pattern.quote(fn), d = dotted + q + "\\b";
            // בתוך הפעולה: age לבד הוא this.age — אלא אם יש משתנה מקומי / פרמטר בשם הזה (גם leader.grade: אם leader הוא משתנה — לא)
            String b = bare != null && !hasVar(s, depth == 0 ? fn : path.substring(0, path.indexOf('.'))) ? bare + q + "\\b" : null;
            Mark m = null;
            if (find(line, "(?<![\\w.])" + d + "(?!\\s*\\()")) m = changed(line, d + "(?!\\s*\\()") ? Mark.CHANGED : Mark.READ;
            if (m == null && b != null && find(line, "(?<![\\w.])" + b + "(?!\\s*\\()")) m = changed(line, b + "(?!\\s*\\()") ? Mark.CHANGED : Mark.READ;
            if (m != null) {
                put(in.hot, label, path + fn, m);
                found = true;
            }
            Model.View av = o.arrays.get(fn);
            if (av != null) {
                int size = av instanceof Model.ArrayView ? ((Model.ArrayView) av).items.size() : ((Model.MatrixView) av).rows.size();
                int width = av instanceof Model.MatrixView && !((Model.MatrixView) av).rows.isEmpty() ? ((Model.MatrixView) av).rows.get(0).size() : 0;
                for (String[] v : s.vars) {
                    int k;
                    try { k = Integer.parseInt(v[1]); } catch (NumberFormatException e) { continue; }
                    String iv = Pattern.quote(v[0]) + "\\b";
                    for (String target : b == null ? new String[] { d } : new String[] { d, b }) {
                        String t = "(?<![\\w.])" + target;
                        if (k >= 0 && k < size && find(line, t + "\\s*\\[\\s*" + iv)) label(in.idx, label, path + fn, k, v[0]);
                        if (av instanceof Model.MatrixView && k >= 0 && k < width
                                && (find(line, t + "\\s*\\[[^\\]]*\\]\\s*\\[\\s*" + iv) || find(line, t + "\\s*\\[[^\\],]*,\\s*" + iv)))
                            label(in.cols, label, path + fn, k, v[0]);   // grid[r][c] (וב-C# גם grid[r, c])
                    }
                }
            }
            Obj n = o.nested.get(fn);
            if (n != null && depth < Recorder.OBJ_NEST)
                found |= hotIn(in, s, line, label, n, path + fn + ".", d + "\\s*\\.\\s*", b == null ? null : b + "\\s*\\.\\s*", depth + 1);
        }
        return found;
    }

    private static boolean find(String line, String regex) {
        return Pattern.compile(regex).matcher(line).find();
    }

    private static void put(Map<String, Map<String, Mark>> hot, String label, String field, Mark m) {
        Map<String, Mark> h = hot.get(label);
        if (h == null) hot.put(label, h = new LinkedHashMap<String, Mark>());
        h.put(field, m);
    }

    private static void label(Map<String, Map<String, Map<Integer, List<String>>>> all, String label, String field, int k, String name) {
        Map<String, Map<Integer, List<String>>> byField = all.get(label);
        if (byField == null) all.put(label, byField = new LinkedHashMap<String, Map<Integer, List<String>>>());
        Map<Integer, List<String>> byIndex = byField.get(field);
        if (byIndex == null) byField.put(field, byIndex = new java.util.TreeMap<Integer, List<String>>());
        List<String> names = byIndex.get(k);
        if (names == null) byIndex.put(k, names = new java.util.ArrayList<String>());
        if (!names.contains(name)) names.add(name);
    }

    /** השורה משנה את target: target = ..., target++, target += ... — וגם תא שלו: target[i] = ... */
    private static boolean changed(String line, String target) {
        return Pattern.compile("(?<![\\w.])" + target + "(?:\\s*\\[[^\\]]*\\])*\\s*(?:\\+\\+|--|[-+*/%]?=(?!=))").matcher(line).find()
                || Pattern.compile("(?:\\+\\+|--)\\s*" + target).matcher(line).find();
    }

    /** המאפיינים המודגשים של אובייקט (לפי השמות שמצביעים עליו בצעד) — או null */
    static Map<String, Mark> hotFor(Info in, Obj o) {
        if (in == null || o == null) return null;
        Map<String, Mark> res = null;
        for (String l : o.labels) {
            Map<String, Mark> h = in.hot.get(l);
            if (h == null) continue;
            if (res == null) res = new LinkedHashMap<String, Mark>();
            res.putAll(h);
        }
        return res;
    }

    /** מה מודגש בטבלה של אובייקט (ראו ObjCard.Hot): לפי השמות שמצביעים עליו בצעד. גם כשאין כלום — Hot ריק (בשביל האובייקטים הפנימיים) */
    static ObjCard.Hot hot(Info in, Obj o) {
        ObjCard.Hot h = new ObjCard.Hot();
        h.self = in;
        addHot(h, in, o);
        return h;
    }

    /** מוסיף ל-h את מה שהשורה עושה באובייקט o דרך משתנה שמצביע עליו (s.grade, this.scores[i]) */
    static void addHot(ObjCard.Hot h, Info in, Obj o) {
        if (in == null || o == null) return;
        for (String l : o.labels) {
            Map<String, Mark> m = in.hot.get(l);
            if (m != null) h.marks.putAll(m);
            merge(h.idx, in.idx.get(l));
            merge(h.cols, in.cols.get(l));
        }
    }

    private static void merge(Map<String, Map<Integer, List<String>>> to, Map<String, Map<Integer, List<String>>> from) {
        if (from == null) return;
        for (Map.Entry<String, Map<Integer, List<String>>> e : from.entrySet()) {
            Map<Integer, List<String>> t = to.get(e.getKey());
            if (t == null) to.put(e.getKey(), t = new java.util.TreeMap<Integer, List<String>>());
            for (Map.Entry<Integer, List<String>> x : e.getValue().entrySet()) {
                List<String> names = t.get(x.getKey());
                if (names == null) t.put(x.getKey(), names = new java.util.ArrayList<String>());
                for (String n : x.getValue()) if (!names.contains(n)) names.add(n);
            }
        }
    }

    /** הכותרת של כרטיס האובייקט: "d1" במקום "this", "other ← d1" לפרמטר */
    static String title(Info in, Obj o, String title) {
        if (in == null) return title;
        if (o.labels.contains("this") && in.self != null && !in.self.isEmpty() && !in.self.equals("this")) {
            return in.self + (in.ctor ? " (בבנייה)" : "");
        }
        for (String l : o.labels) {
            String a = in.alias.get(l);
            if (a != null) return l + "  ←  " + a;
        }
        return title;
    }

    /** השורה הקטנה מתחת לכרטיס של this: "birthday() רצה בתוך d1" */
    static String caption(Info in, Obj o) {
        if (in == null) return null;
        if (o.labels.contains("this") && in.self != null) {
            String who = in.self.isEmpty() || in.self.equals("this") ? "האובייקט הזה" : in.self;
            if (in.ctor) return in.method + " בונה את " + (in.self.isEmpty() ? "האובייקט הזה" : who);
            return ltr(in.method) + " רצה בתוך " + who;
        }
        if (in.calling != null && o.labels.contains(in.calling)) return "נכנסים ל-" + ltr(in.callingMethod + "()") + " של " + in.calling;
        return null;
    }

    /** הכרטיס הזה הוא האובייקט שהפעולה רצה בתוכו (או שעומדים להיכנס אליו) */
    static boolean active(Info in, Obj o) {
        return in != null && (o.labels.contains("this") && in.self != null || in.calling != null && o.labels.contains(in.calling));
    }

    /** כותרת הקוד: "בתוך d1.birthday()" */
    static String where(Step s) {
        Info in = of(s);
        if (in == null || in.self == null || in.self.isEmpty() || in.self.equals("this") || in.ctor) return null;
        return in.self + "." + in.method;
    }

    private static Step next(Step s) {
        try {
            return s.index + 1 < Recorder.stepCount() ? Recorder.step(s.index + 1) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean hasVar(Step s, String name) {
        if (s.vars == null) return false;
        for (String[] v : s.vars) if (v[0].equals(name)) return true;
        return false;
    }

    private static String clean(String line) {
        return line == null ? null : LoopViz.blank(line);
    }

    private static String simple(String className) {
        int i = Math.max(className.lastIndexOf('.'), className.lastIndexOf('$'));
        return i < 0 ? className : className.substring(i + 1);
    }

    private static String ltr(String s) {
        return "‎" + s + "‎";
    }

    /** בשביל הבדיקות (מצב טקסט): מה מוצג בכל צעד */
    static String dump() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Recorder.stepCount(); i++) {
            Step s = Recorder.step(i);
            Info in = of(s);
            if (in == null) continue;
            b.append("  [אובייקט #").append(i + 1).append(" שורה ").append(s.where.line).append(':');
            if (in.self != null) b.append(" this=").append(in.self.isEmpty() ? "?" : in.self).append(" ב-").append(in.method);
            for (Obj o : Recorder.allObjectsOf(s))   // this יכול להיות גם כרטיס בתוך כרטיס
                if (o.labels.contains("this")) {
                    b.append(" {");
                    for (int k = 0; k < o.fields.size(); k++) b.append(k > 0 ? ", " : "").append(o.fields.get(k)[0]).append('=').append(o.fields.get(k)[1]);
                    b.append('}');
                    break;
                }
            if (in.calling != null) b.append(" נכנסים ל-").append(in.calling).append('.').append(in.callingMethod).append("()");
            for (Map.Entry<String, String> e : in.alias.entrySet()) b.append(' ').append(e.getKey()).append("←").append(e.getValue());
            List<Map.Entry<String, Map<String, Mark>>> hot = new java.util.ArrayList<Map.Entry<String, Map<String, Mark>>>(in.hot.entrySet());
            java.util.Collections.sort(hot, new java.util.Comparator<Map.Entry<String, Map<String, Mark>>>() {   // this ראשון — אותו סדר ב-Java וב-C#
                public int compare(Map.Entry<String, Map<String, Mark>> a, Map.Entry<String, Map<String, Mark>> b) {
                    boolean ta = a.getKey().equals("this"), tb = b.getKey().equals("this");
                    return ta != tb ? (ta ? -1 : 1) : a.getKey().compareTo(b.getKey());
                }
            });
            for (Map.Entry<String, Map<String, Mark>> e : hot)
                for (Map.Entry<String, Mark> f : e.getValue().entrySet())
                    b.append(' ').append(e.getKey()).append('.').append(f.getKey()).append('=').append(f.getValue() == Mark.CHANGED ? "שינוי" : "קריאה");
            b.append("]\n");
        }
        return b.toString();
    }
}

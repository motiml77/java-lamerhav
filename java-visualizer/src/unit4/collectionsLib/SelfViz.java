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
        final Map<String, Map<String, Mark>> hot = new LinkedHashMap<String, Map<String, Mark>>();   // שם משתנה → מאפיין → קריאה/שינוי
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
                if (mm.find()) {
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

    /** המאפיינים שהשורה הנוכחית קוראת / משנה: age או this.age (של this), other.age (של other) */
    private static boolean hotFields(Info in, Step s) {
        String line = clean(SourceFiles.line(s.where));
        if (line == null) return false;
        boolean found = false;
        for (Obj o : Recorder.objectsOf(s)) {
            for (String label : o.labels) {
                boolean self = label.equals("this");
                for (String[] f : o.fields) {
                    String fn = f[0];
                    if (!fn.matches("\\w+")) continue;
                    Mark m = null;
                    String q = Pattern.quote(fn), dotted = Pattern.quote(label) + "\\s*\\.\\s*" + q + "\\b(?!\\s*\\()";
                    if (Pattern.compile("(?<![\\w.])" + dotted).matcher(line).find()) m = changed(line, dotted) ? Mark.CHANGED : Mark.READ;
                    // בתוך הפעולה: age לבד הוא this.age — אלא אם יש משתנה מקומי / פרמטר בשם הזה
                    if (m == null && self && !hasVar(s, fn)) {
                        String bare = q + "\\b(?!\\s*\\()";
                        if (Pattern.compile("(?<![\\w.])" + bare).matcher(line).find()) m = changed(line, bare) ? Mark.CHANGED : Mark.READ;
                    }
                    if (m == null) continue;
                    Map<String, Mark> h = in.hot.get(label);
                    if (h == null) in.hot.put(label, h = new LinkedHashMap<String, Mark>());
                    h.put(fn, m);
                    found = true;
                }
            }
        }
        return found;
    }

    private static boolean changed(String line, String target) {
        return Pattern.compile("(?<![\\w.])" + target + "\\s*(?:\\+\\+|--|[-+*/%]?=(?!=))").matcher(line).find()
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
            for (Obj o : Recorder.objectsOf(s))
                if (o.labels.contains("this")) {
                    b.append(" {");
                    for (int k = 0; k < o.fields.size(); k++) b.append(k > 0 ? ", " : "").append(o.fields.get(k)[0]).append('=').append(o.fields.get(k)[1]);
                    b.append('}');
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

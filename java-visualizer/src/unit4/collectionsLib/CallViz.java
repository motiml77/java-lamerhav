package unit4.collectionsLib;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import unit4.collectionsLib.Model.Frame;
import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.Step;

/**
 * פעולה שקוראת לפעולת עזר (לא מ-main): היציאה לפעולת העזר והחזרה ממנה, בכל צעד.
 * - יוצאים: בשורה שקוראת — "קוראים ל-isEven".
 * - נכנסים / בתוך פעולת העזר: מי קרא לה, מאיזו שורה, עם אילו ערכים — והפעולה שקראה "מחכה" בשורה שלה.
 * - חוזרים: מה פעולת העזר מחזירה, ולאיזו שורה חוזרים; ואחר כך — בשורה שקראה: "חזרנו עם true".
 * קריאה מ-main לא מסומנת (זו הפעולה של התרגיל), וגם לא רקורסיה (פעולה שקוראת לעצמה — לזה יש את מעקב הרקורסיה).
 * עובד ב-Java וב-C# (אותם צעדים).
 */
final class CallViz {
    private CallViz() {}

    enum Phase {
        CALLING,    // בשורה שקוראת לפעולת העזר — הצעד הבא כבר בתוכה
        ENTERED,    // הצעד הראשון בתוך פעולת העזר
        INSIDE,     // בתוך פעולת העזר
        RETURNING,  // פעולת העזר מחזירה ערך (או מסתיימת)
        RETURNED    // חזרנו לשורה שקראה
    }

    static final class State {
        Phase phase;
        Frame caller;          // הפעולה שקראה — והשורה שבה היא מחכה / שממנה קראה (caller.line)
        int now = -1;          // RETURNED: השורה שבה ממשיכים (אחרי קריאה ל-void — כבר השורה הבאה)
        Frame callee;          // פעולת העזר
        String args = "";      // "x = 8" — הערכים שפעולת העזר קיבלה
        String returned;       // RETURNING / RETURNED: הערך שהוחזר (null = void / לא ידוע)
        boolean voidReturn;    // הפעולה לא מחזירה ערך
        final List<Frame> path = new ArrayList<Frame>();   // הפעולות בדרך (בלי main), מהחיצונית לפנימית — האחרונה היא שרצה עכשיו
    }

    private static int scanned;
    private static boolean found;

    /** האם יש בהרצה קריאה לפעולת עזר (שלא מ-main) — אם לא, לא מציגים את פס הקריאות בכלל. בודק רק צעדים חדשים */
    static synchronized boolean any() {
        int n = Recorder.stepCount();
        if (n < scanned) scanned = 0;   // הרצה חדשה
        for (; !found && scanned < n; scanned++) {
            Step s = Recorder.step(scanned);
            if (s != null && s.calls.size() >= 3 && helper(s.calls.get(1), s.calls.get(0))) found = true;
        }
        return found;
    }

    static State state(int index) {
        Step s;
        try {
            s = Recorder.step(index);
        } catch (RuntimeException e) {
            return null;
        }
        if (s == null || s.where == null || s.calls.isEmpty()) return null;
        int d = s.calls.size();
        // בתוך פעולת עזר: הפעולה שמתחתיה במחסנית היא לא main
        if (d >= 3 && helper(s.calls.get(1), s.calls.get(0))) {
            State st = new State();
            st.caller = s.calls.get(1);
            st.callee = s.calls.get(0);
            path(st, s.calls, null);
            int entry = entry(index, d);
            if (s.kind == Kind.RETURN) {
                st.phase = Phase.RETURNING;
                st.returned = s.returned;
                st.voidReturn = s.returned == null;
            } else {
                st.phase = entry == index ? Phase.ENTERED : Phase.INSIDE;
            }
            st.args = args(st.callee, entry);
            return st;
        }
        if (d < 2) return null;   // ב-main
        // חזרנו עכשיו מפעולת עזר
        if (index > 0) {
            Step p = Recorder.step(index - 1);
            if (p != null && p.kind == Kind.RETURN && p.calls.size() == d + 1 && helper(s.calls.get(0), p.calls.get(0))) {
                State st = new State();
                st.phase = Phase.RETURNED;
                st.caller = p.calls.size() >= 2 ? p.calls.get(1) : s.calls.get(0);   // השורה שממנה קראו
                st.now = s.where.line;
                st.callee = p.calls.get(0);
                st.returned = p.returned;
                st.voidReturn = p.returned == null;
                st.args = args(st.callee, entry(index - 1, d + 1));
                path(st, s.calls, null);
                return st;
            }
        }
        // קוראים עכשיו לפעולת עזר: הצעד הבא (אחרי הצעדים של אותה שורה) כבר בתוכה
        for (int j = index + 1, n = Recorder.stepCount(); j < n && j < index + 200; j++) {
            Step t = Recorder.step(j);
            if (t == null || t.where == null) continue;
            int td = t.calls.size();
            if (td > d) {
                Frame callee = t.calls.get(td - d - 1);
                if (!helper(s.calls.get(0), callee)) return null;
                State st = new State();
                st.phase = Phase.CALLING;
                st.caller = s.calls.get(0);
                st.callee = callee;
                st.args = td == d + 1 ? args(callee, j) : "";
                path(st, s.calls, callee);
                return st;
            }
            if (td < d || t.kind == Kind.LINE || t.kind == Kind.RETURN || t.where.line != s.where.line) return null;
        }
        return null;
    }

    /** קריאה לפעולת עזר: מי שקרא הוא לא main, ופעולת העזר היא פעולה אחרת (לא רקורסיה) */
    private static boolean helper(Frame caller, Frame callee) {
        if (caller == null || callee == null) return false;
        if (caller.method.equals("main") || caller.method.equals("Main")) return false;
        return !(caller.method.equals(callee.method) && caller.className.equals(callee.className));
    }

    /** הצעד הראשון של הקריאה הנוכחית (עומק d) */
    private static int entry(int index, int d) {
        int j = index;
        while (j > 0) {
            Step p = Recorder.step(j - 1);
            if (p == null || p.calls.size() < d) break;
            j--;
        }
        return j;
    }

    private static void path(State st, List<Frame> calls, Frame extra) {
        for (int i = calls.size() - 2; i >= 0; i--) st.path.add(calls.get(i));   // בלי main (האחרונה)
        if (extra != null) st.path.add(extra);
    }

    // ------------------------------------------------------------ הפרמטרים

    /** "x = 8, s = Stack [8,3]" — הפרמטרים של פעולת העזר בצעד הראשון שלה */
    static String args(Frame callee, int entry) {
        Step e;
        try {
            e = Recorder.step(entry);
        } catch (RuntimeException ex) {
            return "";
        }
        if (e == null || e.vars == null) return "";
        List<String> names = params(callee);
        StringBuilder b = new StringBuilder();
        for (String[] v : e.vars) {
            if (names != null && !names.contains(v[0])) continue;
            if (b.length() > 0) b.append(", ");
            b.append(v[0]).append(" = ").append(CondViz.shorten(v[1]));
            if (b.length() > 60) break;
        }
        return b.toString();
    }

    /** שמות הפרמטרים מהכותרת של הפעולה בקוד (null = לא נמצאה — אז כל המשתנים בצעד הראשון הם הפרמטרים) */
    static List<String> params(Frame f) {
        String[] sig = signature(f);
        if (sig == null) return null;
        List<String> names = new ArrayList<String>();
        String inside = sig[1];
        int depth = 0, start = 0;
        for (int i = 0; i <= inside.length(); i++) {
            char c = i < inside.length() ? inside.charAt(i) : ',';
            if (c == '<' || c == '(' || c == '[') depth++;
            else if (c == '>' || c == ')' || c == ']') depth--;
            else if (c == ',' && depth == 0) {
                String p = inside.substring(start, i).replaceAll("=.*$", "").trim();
                Matcher m = Pattern.compile("([A-Za-z_]\\w*)\\s*$").matcher(p);
                if (m.find()) names.add(m.group(1));
                start = i + 1;
            }
        }
        return names;
    }

    /** השורה של הכותרת של הפעולה (למשל static boolean isEven(int x) {) — או -1 */
    static int signatureLine(Frame f) {
        String[] sig = signature(f);
        return sig == null ? -1 : Integer.parseInt(sig[0]);
    }

    /** {שורת הכותרת, מה שבתוך הסוגריים} — מחפשים למעלה מהשורה הנוכחית את ההגדרה של הפעולה */
    private static String[] signature(Frame f) {
        List<String> lines = SourceFiles.lines(f);
        if (lines == null || f.line < 1) return null;
        String name = f.method.equals("<init>") || f.method.equals(".ctor") ? simple(f.className) : f.method;
        Pattern decl = Pattern.compile("^\\s*(?:(?:public|private|protected|internal|static|final|override|virtual|abstract|synchronized|sealed|new|async)\\s+)*"
                + "(?:[\\w.<>\\[\\],?]+(?:\\s*<[^()]*>)?\\s+)?" + Pattern.quote(name) + "\\s*\\((.*)$");
        for (int i = Math.min(f.line, lines.size()) - 1; i >= 0 && i >= f.line - 400; i--) {
            String l = LoopViz.blank(lines.get(i));
            Matcher m = decl.matcher(l);
            if (!m.find() || l.trim().endsWith(";") || l.contains("=")) continue;
            String rest = m.group(1);
            StringBuilder inside = new StringBuilder();
            int depth = 1;
            for (int k = i, from = 0; k < lines.size() && k < i + 10; k++, from = 0) {
                String t = k == i ? rest : LoopViz.blank(lines.get(k));
                for (int c = from; c < t.length(); c++) {
                    char ch = t.charAt(c);
                    if (ch == '(') depth++;
                    else if (ch == ')' && --depth == 0) return new String[] { String.valueOf(i + 1), inside.toString() };
                    inside.append(ch);
                }
                inside.append(' ');
            }
            return null;
        }
        return null;
    }

    private static String simple(String className) {
        int i = Math.max(className.lastIndexOf('.'), className.lastIndexOf('$'));
        return i < 0 ? className : className.substring(i + 1);
    }

    // ------------------------------------------------------------ טקסטים

    /** שם קצר לפעולה: isEven() */
    static String name(Frame f) {
        return (f.method.equals("<init>") || f.method.equals(".ctor") ? simple(f.className) : f.method) + "()";
    }

    /** המשפט בפס הקריאות */
    static String sentence(State st) {
        String callee = ltr(name(st.callee)), caller = ltr(name(st.caller));
        String with = st.args.isEmpty() ? "" : " עם " + ltr(st.args);
        switch (st.phase) {
            case CALLING:
                return "בשורה " + st.caller.line + " " + caller + " קוראת לפעולת העזר " + callee + " — יוצאים אליה" + with;
            case ENTERED:
                return "יצאנו מ-" + caller + " (שורה " + st.caller.line + ") לפעולת העזר " + callee + with;
            case INSIDE:
                return "בתוך פעולת העזר " + callee + " · " + caller + " מחכה בשורה " + st.caller.line + " לתשובה";
            case RETURNING:
                return (st.voidReturn ? callee + " הסתיימה" : callee + " מחזירה " + ltr(st.returned))
                        + " — חוזרים ל-" + caller + " בשורה " + st.caller.line;
            default:
                String got = st.voidReturn ? callee + " הסתיימה" : callee + " החזירה " + ltr(st.returned);
                if (st.now > 0 && st.now != st.caller.line)
                    return "חזרנו ל-" + caller + " מהקריאה בשורה " + st.caller.line + " — " + got + ", ממשיכים בשורה " + st.now;
                return "חזרנו ל-" + caller + " בשורה " + st.caller.line + " — " + got + ", ממשיכים מאותה נקודה";
        }
    }

    /** קוד (isEven(), x = 8, Stack [8,3]) בתוך משפט בעברית — בלי שהסוגריים והפסיקים יתהפכו */
    static String ltr(String s) {
        return "\u200E" + s + "\u200E";
    }

    /** בשביל הבדיקות (מצב טקסט, -Ddsviz.conditions=true): מה פס הקריאות מראה בכל צעד */
    static String dump() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Recorder.stepCount(); i++) {
            State st = state(i);
            if (st == null) continue;
            b.append("  [קריאה #").append(i + 1).append(' ').append(st.phase).append(" שורה ").append(Recorder.step(i).where.line).append(": ");
            for (int k = 0; k < st.path.size(); k++) b.append(k > 0 ? " > " : "").append(name(st.path.get(k)));
            b.append(" | ").append(sentence(st).replace("\u200E", "")).append(" | כותרת ").append(signatureLine(st.callee)).append("]\n");
        }
        return b.toString();
    }
}

package unit4.collectionsLib;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import unit4.collectionsLib.Model.Frame;
import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.Step;

/**
 * מעקב המשתנים לכל פעולה שפעילה עכשיו — הפעולה שרצה, וכל מי שמחכות לה (main ← sumEven ← isEven, או fact(4) ← fact(3) ...):
 * - הרשימה: השם, הערכים שהקריאה קיבלה ("fact(n = 3)" — כך מבדילים בין הקריאות ברקורסיה) והשורה שבה כל פעולה נמצאת.
 * - המשתנים של כל פעולה ברגע הזה (Step.frameVars), ומה השתנה מאז הצעד הקודם — רק באותה קריאה, לא בקריאה אחרת של אותה פעולה.
 * - משפט קצר: "sumEven() מחכה בשורה 29 לתשובה מ-isEven()", או בצעד היציאה "rem() מחזירה 0 — חוזרים ל-isEven() בשורה 10".
 * החלון (VarsPanel) מציג את זה. עובד אותו דבר ב-Java וב-C# (אותם צעדים).
 */
final class FrameViz {
    private FrameViz() {}

    /** ברשימה לכל היותר כמה פעולות (ברקורסיה עמוקה: הפנימיות, ובסוף main) */
    static final int MAX_LIST = 12;

    /** המשתנים של הפעולה k בצעד (0 = הפעולה שרצה, 1 = מי שקרא לה...); null = לא ידוע */
    static List<String[]> vars(Step s, int k) {
        if (s == null || k < 0) return null;
        if (k == 0) return s.vars;
        if (s.frameVars == null || k >= s.frameVars.size()) return null;
        return s.frameVars.get(k);
    }

    // ------------------------------------------------------------ זהות הקריאה

    /** לכל צעד: לכל פעולה במחסנית (מלמטה: 0 = main) — הצעד הראשון של הקריאה הזו. null = צעד בלי מחסנית (כמו הקודם) */
    private static final List<int[]> entries = new ArrayList<int[]>();
    private static Step firstSeen;

    /** הצעד הראשון של הקריאה שהפעולה k בצעד s שייכת לה (-1 = לא ידוע) */
    static int entry(Step s, int k) {
        int[] e = entries(s.index);
        int b = s.calls.size() - 1 - k;
        return e == null || b < 0 || b >= e.length ? -1 : e[b];
    }

    /** "אותה קריאה": הפעולה בעומק b (מלמטה) בשני הצעדים היא אותה הפעלה של הפעולה (לא קריאה חדשה אחרי שהקודמת חזרה) */
    static boolean sameCall(Step a, int ka, Step b, int kb) {
        if (a == null || b == null) return false;
        int ea = entry(a, ka), eb = entry(b, kb);
        return ea >= 0 && ea == eb && a.calls.size() - 1 - ka == b.calls.size() - 1 - kb;
    }

    private static synchronized int[] entries(int index) {
        int n = Recorder.stepCount();
        Step first = n > 0 ? Recorder.step(0) : null;
        if (entries.size() > n || first != firstSeen) {   // הרצה חדשה
            entries.clear();
            firstSeen = first;
        }
        while (entries.size() <= index && entries.size() < n) {
            int i = entries.size();
            Step s = Recorder.step(i);
            if (s.calls.isEmpty()) {   // סוף התוכנית מאפס; צעד אחר בלי מחסנית — כאילו לא היה
                entries.add(s.kind == Kind.END ? new int[0] : null);
                continue;
            }
            int q = i - 1;   // הצעד האחרון שהייתה לו מחסנית
            while (q >= 0 && entries.get(q) == null) q--;
            Step p = q >= 0 ? Recorder.step(q) : null;
            int[] pe = q >= 0 ? entries.get(q) : null;
            int d = s.calls.size();
            int[] e = new int[d];
            boolean same = p != null && pe != null;
            for (int b = 0; b < d; b++) {
                Frame f = s.calls.get(d - 1 - b);
                if (same) {
                    int pd = p.calls.size();
                    Frame g = b < pd ? p.calls.get(pd - 1 - b) : null;
                    same = g != null && b < pe.length && g.method.equals(f.method) && g.className.equals(f.className)
                            && !(p.kind == Kind.RETURN && pd == b + 1);   // הפעולה בעומק הזה חזרה — זו קריאה חדשה
                }
                e[b] = same ? pe[b] : i;
            }
            entries.add(e);
        }
        return index < entries.size() ? entries.get(index) : null;
    }

    /**
     * SelfViz.Info של הפעולה k (לא רק הרצה) — איזה אובייקט this שלה (d1), כדי לכתוב "this (d1)" גם לפעולה שמחכה,
     * לא רק לזאת שרצה. מחושב מהצעד הראשון של הקריאה (entry) — שם ה-self כבר ידוע, ולא משתנה במהלך הקריאה.
     */
    static SelfViz.Info selfOf(Step s, int k) {
        int e = entry(s, k);
        if (e < 0) return null;
        try {
            return SelfViz.of(Recorder.step(e));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------ טקסטים

    /** "isEven()" / הבנאי: "Dog()" */
    static String name(Frame f) {
        return CallViz.name(f);
    }

    /** "fact(n = 3)" — השם עם הערכים שהקריאה קיבלה (בצעד הראשון שלה). main / בלי פרמטרים — "main()" */
    static String title(Step s, int k) {
        Frame f = s.calls.get(k);
        String n = name(f);
        String base = n.substring(0, n.length() - 2);
        int e = entry(s, k);
        if (e < 0 || f.method.equals("main") || f.method.equals("Main")) return n;
        Step first;
        try {
            first = Recorder.step(e);
        } catch (RuntimeException ex) {
            return n;
        }
        List<String[]> vs = first == null ? null : vars(first, first.calls.size() - (s.calls.size() - k));
        if (vs == null) return n;
        List<String> params = CallViz.params(f);
        StringBuilder b = new StringBuilder();
        for (String[] v : vs) {
            if (v[0].equals("this") || (params != null && !params.contains(v[0]))) continue;
            String val = v[1].replaceAll("\\s+", " ").trim();
            if (val.length() > 14) val = val.substring(0, 13) + "…";
            String add = (b.length() > 0 ? ", " : "") + v[0] + " = " + val;
            if (b.length() + add.length() > 34) {
                b.append(", …");
                break;
            }
            b.append(add);
        }
        return base + "(" + b + ")";
    }

    /** הפעולות ברשימה (אינדקסים ב-calls): הפנימית ראשונה; ברקורסיה עמוקה — MAX_LIST-1 הפנימיות, -1 (במקום הדילוג) ו-main */
    static List<Integer> listed(Step s) {
        List<Integer> res = new ArrayList<Integer>();
        int n = s.calls.size();
        for (int k = 0; k < n; k++) {
            if (n > MAX_LIST && k == MAX_LIST - 1) {
                res.add(-1);
                k = n - 1;
            }
            res.add(k);
        }
        return res;
    }

    /** כמה קריאות דילגנו עליהן ברשימה (ברקורסיה עמוקה) */
    static int skipped(Step s) {
        return Math.max(0, s.calls.size() - MAX_LIST);
    }

    /** המשתנים שהשתנו מאז הצעד הקודם באותה קריאה (לא בהשוואה לפעולה אחרת או לקריאה אחרת של אותה פעולה) */
    static Set<String> changed(Step s, int k) {
        Set<String> res = new LinkedHashSet<String>();
        List<String[]> now = vars(s, k);
        int b = s.calls.size() - 1 - k;
        int e = entry(s, k);
        if (now == null || e < 0) return res;
        for (int j = s.index - 1; j >= e && j >= 0 && j > s.index - 3000; j--) {
            Step p = Recorder.step(j);
            if (p == null || p.calls.size() <= b) continue;
            int kp = p.calls.size() - 1 - b;
            List<String[]> before = vars(p, kp);
            if (before == null || before == now) continue;
            if (!sameCall(s, k, p, kp)) break;
            Map<String, String> old = new HashMap<String, String>();
            for (String[] v : before) old.put(v[0], v[1]);
            for (String[] v : now) if (!v[1].equals(old.get(v[0]))) res.add(v[0]);
            break;
        }
        return res;
    }

    /**
     * הפעולה הזו (באותה קריאה) עדיין תקבל משתנים בהמשך — למשל פרמטר שעוד לא נקרא, או "int sum = 0;" שעוד לא רץ.
     * הטבלה ריקה כרגע, אבל לא כי לפעולה הזו אין בכלל משתנים: ההודעה צריכה לומר "עדיין" ולא "אין משתנים" סתם.
     */
    static boolean willHaveVars(Step s, int k) {
        int e = entry(s, k);
        if (e < 0) return false;
        int b = s.calls.size() - 1 - k;
        for (int j = s.index + 1, n = Recorder.stepCount(); j < n && j < s.index + 3000; j++) {
            Step t = Recorder.step(j);
            if (t == null || t.calls.size() <= b) break;   // הפעולה הזו כבר לא במחסנית — לא תקבל עוד משתנים בקריאה הזו
            int kt = t.calls.size() - 1 - b;
            if (!sameCall(s, k, t, kt)) break;
            List<String[]> vs = vars(t, kt);
            if (vs != null && !vs.isEmpty()) return true;
        }
        return false;
    }

    /**
     * המשפט מעל המשתנים — או null:
     * פעולה שמחכה: "sumEven() מחכה בשורה 29 לתשובה מ-isEven()"; צעד היציאה: "rem() מחזירה 0 — חוזרים ל-isEven() בשורה 10".
     */
    static String note(Step s, int k) {
        if (s.calls.isEmpty() || k < 0 || k >= s.calls.size()) return null;
        if (k > 0) {
            Frame me = s.calls.get(k);
            return ltr(label(s, k)) + " מחכה בשורה " + me.line + " לתשובה מ-" + ltr(label(s, k - 1));
        }
        if (s.kind != Kind.RETURN) return null;
        String who = ltr(label(s, 0));
        String what = s.returned == null ? who + " הסתיימה" : who + " מחזירה " + ltr(s.returned);
        if (s.calls.size() < 2) return what;
        return what + " — חוזרים ל-" + ltr(label(s, 1)) + " בשורה " + s.calls.get(1).line;
    }

    /** בתוך משפט: ברקורסיה עם הערכים ("fact(n = 3)" — כדי לדעת איזו קריאה), אחרת רק השם ("isEven()") */
    static String label(Step s, int k) {
        return recursive(s, k) ? title(s, k) : name(s.calls.get(k));
    }

    /**
     * הפעולה k מופיעה במחסנית יותר מפעם אחת — או שבקריאה הזו היא כבר קראה לעצמה (הקריאה החיצונית ברקורסיה,
     * למשל fact(n = 4) בצעד שבו היא מחזירה 24: אז היא לבד במחסנית, אבל עדיין צריך לדעת איזו קריאה זו)
     */
    static boolean recursive(Step s, int k) {
        Frame me = s.calls.get(k);
        for (int j = 0; j < s.calls.size(); j++) {
            Frame f = s.calls.get(j);
            if (j != k && f.method.equals(me.method) && f.className.equals(me.className)) return true;
        }
        int e = entry(s, k), b = s.calls.size() - 1 - k;
        if (e < 0) return false;
        for (int i = s.index - 1; i >= e && i > s.index - 3000; i--) {   // צעד קודם באותה קריאה, שבו היא קראה לעצמה
            Step p;
            try {
                p = Recorder.step(i);
            } catch (RuntimeException ex) {
                return false;
            }
            if (p == null || p.calls.size() <= b + 1) continue;
            for (int j = 0; j < p.calls.size() - 1 - b; j++) {
                Frame f = p.calls.get(j);
                if (f.method.equals(me.method) && f.className.equals(me.className)) return true;
            }
        }
        return false;
    }

    private static String ltr(String s) {
        return "‎" + s + "‎";
    }

    // ------------------------------------------------------------ בדיקות

    /**
     * בשביל הבדיקות (מצב טקסט, -Ddsviz.conditions=true): בכל צעד שיש בו יותר מפעולה אחת —
     * כל הפעולות (הפנימית ראשונה) עם המשתנים שלהן: [מסגרות #12: isEven{x=8} ← sumEven{s=Stack [3], sum=0, ...} ← main{s=Stack [3]}]
     */
    static String dump() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < Recorder.stepCount(); i++) {
            Step s = Recorder.step(i);
            if (s == null) continue;
            if (s.kind == Kind.END) {   // [סיום #86: main{s=Stack [8,3], total=8, q=Queue []}] — משתני main בסוף הריצה
                b.append("  [סיום #").append(i + 1).append(": ").append(Syntax.csharp ? "Main" : "main").append('{');
                if (s.vars != null) for (int j = 0; j < s.vars.size(); j++) b.append(j > 0 ? ", " : "").append(s.vars.get(j)[0]).append('=').append(s.vars.get(j)[1]);
                b.append("}]\n");
                continue;
            }
            if (s.calls.size() < 2) continue;
            b.append("  [מסגרות #").append(i + 1).append(": ");
            for (int k : listed(s)) {
                if (k < 0) {
                    b.append(" ← …");
                    continue;
                }
                b.append(k == 0 ? "" : " ← ").append(s.calls.get(k).method).append('{');
                List<String[]> vs = vars(s, k);
                if (vs == null) b.append('?');
                else for (int j = 0; j < vs.size(); j++) b.append(j > 0 ? ", " : "").append(vs.get(j)[0]).append('=').append(vs.get(j)[1]);
                b.append('}');
            }
            String note = note(s, 0);
            if (note != null) b.append(" | ").append(note.replace("‎", ""));
            b.append("]\n");
        }
        return b.toString();
    }
}

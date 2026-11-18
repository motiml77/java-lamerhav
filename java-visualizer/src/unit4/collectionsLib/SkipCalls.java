package unit4.collectionsLib;

import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.Step;

/**
 * "לדלג על פונקציות": החלון מציג רק את השורות של main. שורה שקוראת לפעולה של התלמיד (int total = SumEven(s)) היא צעד אחד,
 * שמראה את המצב <b>אחרי</b> שהפעולה הסתיימה — המבנים, הפלט והמשתנים — בלי השורות שבתוכה.
 *
 * ההקלטה עצמה לא משתנה (כמו LineAfter): כאן רק מחליטים מה מוצג. known = כמה צעדים כבר הגיעו (הרצה שעוד נמשכת: פעולה שעוד
 * רצה — השורה מוצגת כמו שהיא, ומתעדכנת כשהצעדים שאחרי החזרה מגיעים).
 */
final class SkipCalls {
    private SkipCalls() {}

    /** הצעד מוצג במצב הזה: main בלבד (חוץ מפעולה שמתקפלת לתוך השורה שלה), והשגיאה והסוף — תמיד */
    static boolean shown(Step s, int known) {
        if (s.kind == Kind.END || s.kind == Kind.ERROR) return true;
        if (s.calls.size() >= 2) return false;
        return !folded(s.index, Math.max(known, s.index + 1));   // הצעד עצמו כבר הגיע (גם כשהחלון עוד לא ספר אותו)
    }

    /**
     * השורה idx קוראת לפעולה של התלמיד (הצעד שאחריה עמוק יותר במחסנית): הצעד הראשון אחרי שהפעולה הסתיימה (חזרה לאותו עומק).
     * -1: השורה לא קוראת לפעולה, הפעולה עוד לא הסתיימה, או שההרצה נעצרה בתוכה (השגיאה מוצגת בצעד שלה)
     */
    static int target(int idx, int known) {
        if (idx < 0 || idx + 1 >= known) return -1;
        Step s = Recorder.step(idx), nx = Recorder.step(idx + 1);
        if (s == null || nx == null || s.kind != Kind.LINE || nx.calls.size() <= s.calls.size()) return -1;
        for (int j = idx + 2; j < known; j++) {
            Step t = Recorder.step(j);
            if (t == null || t.kind == Kind.ERROR || t.kind == Kind.END) return -1;
            if (t.calls.size() <= s.calls.size()) return j;
        }
        return -1;
    }

    /** פעולה באותה שורה מיד אחרי החזרה מהפעולה (Console.WriteLine(MaxOf(q))) — מוצגת בתוך צעד השורה, לא לבד */
    static boolean folded(int idx, int known) {
        if (idx <= 0) return false;
        Step s = Recorder.step(idx);
        if (!LineAfter.op(s)) return false;
        Step p = Recorder.step(idx - 1);
        if (p == null || p.calls.size() <= s.calls.size()) return false;   // לא חזרנו הרגע מפעולה
        for (int i = idx - 2; i >= 0; i--) {
            Step t = Recorder.step(i);
            if (t == null) return false;
            if (t.calls.size() <= s.calls.size()) return t.kind == Kind.LINE && LineAfter.sameSpot(t, s) && target(i, known) == idx;
        }
        return false;
    }

    /** מאיזה צעד מציירים את המבנים והפלט כשהצעד idx מוצג: אחרי הפעולה שהשורה קראה לה; אחרת — כמו "שורה שורה" */
    static int data(int idx, int known) {
        int j = target(idx, known);
        return j >= 0 ? j : LineAfter.data(idx, known);
    }

    /** הצעד שהמשתנים שלו הם המצב "אחרי השורה" של idx, או -1 */
    static int after(int idx, int known) {
        int j = target(idx, known);
        if (j < 0) return LineAfter.after(idx, known);
        Step t = Recorder.step(j);
        return t != null && t.kind == Kind.LINE ? j : LineAfter.after(j, known);
    }
}

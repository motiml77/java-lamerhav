package unit4.collectionsLib;

import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.Step;

/**
 * "כל שורה": השורה המסומנת בקוד מראה את מה שהיא <b>כבר עשתה</b> — לא את המצב שלפניה.
 *
 * ההקלטה עצמה לא משתנה: כל שורה מוקלטת כצעד LINE (המשתנים ברגע שלפני שהשורה רצה), ואחריו צעדי הפעולות שהיא עשתה
 * באותה שורה (q.Insert(8), s.Pop() → 5, הדפסה...). כאן רק מחליטים מה החלון מציג בכל צעד:
 * - הפעולה הראשונה של השורה "מתקפלת" לתוך צעד השורה (folded): צעד אחד — המבנים כבר אחרי הפעולה, והסימון על השורה שעשתה אותה.
 *   עוד פעולות באותה שורה (s2.Push(s1.Pop())) נשארות צעדים משלהן, כדי שכל אחת תונפש.
 * - המשתנים: אחרי השורה (after) — מהצעד LINE הבא, כשהוא באותה הפעלה של אותה פעולה. שורה שקוראת לפעולה של התלמיד, קלט
 *   מהמקלדת, חזרה מפעולה וכו' — בלי "אחרי" (כמו קודם): הערך החדש מופיע, ומסומן, בצעד הבא של אותה פעולה.
 * הצעד המייצג נשאר צעד ה-LINE — כך הסימונים בקוד (תנאים, חץ הלולאה, קריאות, לולאה שלא נגמרת) מחושבים בדיוק כמו קודם.
 * כיבוי (לבדיקה / השוואה): -Ddsviz.lineAfter=false או DSVIZ_LINE_AFTER=0
 */
final class LineAfter {
    private LineAfter() {}

    static final boolean ENABLED = enabled();

    private static boolean enabled() {
        String p = System.getProperty("dsviz.lineAfter");
        if (p != null) return !p.trim().equalsIgnoreCase("false") && !p.trim().equals("0");
        String e = System.getenv("DSVIZ_LINE_AFTER");
        return e == null || !(e.trim().equals("0") || e.trim().equalsIgnoreCase("false"));
    }

    /** צעד של פעולה שהשורה עשתה (על מבנה / מחרוזת / הדפסה) — לא שורה, חזרה, קלט, שגיאה או סיום */
    static boolean op(Step s) {
        if (s == null) return false;
        switch (s.kind) {
        case CREATE:
        case CHANGE:
        case READ:
        case CHECK:
        case OUTPUT:
            return true;
        default:
            return false;
        }
    }

    /** אותה שורה באותה פעולה ובאותו עומק במחסנית */
    static boolean sameSpot(Step a, Step b) {
        if (a == null || b == null || a.where == null || b.where == null) return false;
        if (a.calls.size() != b.calls.size() || a.where.line != b.where.line) return false;
        return eq(a.where.file, b.where.file) && eq(a.where.method, b.where.method) && eq(a.where.className, b.where.className);
    }

    private static boolean eq(String x, String y) {
        return x == null ? y == null : x.equals(y);
    }

    /** הצעד i הוא הפעולה הראשונה של שורה, מיד אחרי צעד ה-LINE שלה — הוא מוצג כחלק מצעד השורה (ולא כצעד נפרד) */
    static boolean folded(int i) {
        if (i <= 0) return false;
        Step s = Recorder.step(i), p = Recorder.step(i - 1);
        return op(s) && p != null && p.kind == Kind.LINE && sameSpot(p, s);
    }

    /** מאיזה צעד מציירים את המבנים / הפלט כשהצעד i מוצג: הפעולה שמתקפלת לתוכו, אם יש (ואם כבר הגיעה); אחרת i עצמו */
    static int data(int i, int known) {
        Step s = Recorder.step(i);
        if (s != null && s.kind == Kind.LINE && i + 1 < known && folded(i + 1)) return i + 1;
        return i;
    }

    /**
     * הצעד שהמשתנים שלו הם המצב "אחרי השורה" של הצעד i — או -1 (אין: עוד פעולות באותה שורה, קריאה לפעולה של התלמיד,
     * קלט, חזרה, סוף, או שהצעד הבא עוד לא הגיע)
     */
    static int after(int i, int known) {
        Step s = Recorder.step(i);
        if (s == null || !(s.kind == Kind.LINE || op(s))) return -1;
        int d = data(i, known), n = d + 1;
        if (n >= known) return -1;
        Step ds = Recorder.step(d), t = Recorder.step(n);
        if (ds == null || t == null) return -1;
        if (op(t) && sameSpot(ds, t)) return -1;   // השורה עוד לא נגמרה — עוד פעולה בה
        if (t.kind != Kind.LINE || t.vars == null || ds.vars == null) return -1;
        if (t.calls.size() != ds.calls.size()) return -1;   // נכנסו לפעולה של התלמיד / חזרו ממנה
        return FrameViz.sameCall(ds, 0, t, 0) ? n : -1;
    }
}

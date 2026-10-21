package unit4.collectionsLib;

import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.Mark;

/**
 * פקודות אופציונליות לשליטה בוויזואליזציה.
 * אין חובה להשתמש באף אחת מהן — כל פעולה על המבנים מוצגת אוטומטית.
 *
 * <pre>
 *   Viz.name(s, "מקורית");         // לתת שם למבנה (במקום השם שזוהה אוטומטית)
 *   Viz.note("עכשיו מתחילים להפוך"); // הערה שתופיע כצעד בהרצה
 *   Viz.point("p", p);              // תווית "p" מעל החוליה / הצומת ש-p מצביע עליו
 *   Viz.off();  ...  Viz.on();      // לכבות / להדליק תיעוד בקטע קוד
 *   Viz.textMode();                 // בלי חלון — הצעדים מודפסים לקונסול
 * </pre>
 */
public final class Viz {
    private Viz() {}

    /** לתת שם למבנה (מחסנית, תור, ראש רשימה או שורש עץ) */
    public static void name(Object structure, String name) {
        Recorder.sync();
        Recorder.setName(structure, name, 2);
        Recorder.record(Kind.NOTE, structure, Mark.NONE, null, "שם חדש: " + name);
    }

    /** הערה שתופיע כצעד בהרצה */
    public static void note(String text) {
        Recorder.sync();
        Recorder.record(Kind.NOTE, null, Mark.NONE, null, "הערה: " + text);
    }

    /**
     * להציג תווית (שם המשתנה) מעל חוליה / צומת.
     * כדאי לקרוא לזה בכל פעם שהמשתנה מתקדם, למשל: p = p.getNext(); Viz.point("p", p);
     */
    public static void point(String label, Object node) {
        Recorder.sync();
        Recorder.point(label, node);
        Recorder.record(Kind.NOTE, node, Mark.READ, null,
                label + "  ←  " + (node == null ? "null" : Recorder.nodeRef(node)));
    }

    /** הפסקת התיעוד (למשל לפני לולאה ארוכה שלא מעניין לראות) */
    public static void off() {
        Recorder.sync();
        Recorder.enabled = false;
    }

    /** חידוש התיעוד */
    public static void on() {
        Recorder.sync();
        Recorder.enabled = true;
    }

    /** בלי חלון: כל צעד מודפס לקונסול. יש לקרוא לזה בתחילת main */
    public static void textMode() {
        Recorder.textMode = true;
        Recorder.active = true;
    }

    /**
     * פותח את החלון הוויזואלי גם בהרצה רגילה (Run) — למי שעובד בלי התוסף.
     * יש לקרוא לזה בתחילת main. (עם התוסף אין צורך: כפתור "הרצה ויזואלית" פותח את החלון.)
     */
    public static void show() {
        Recorder.active = true;
    }

    /**
     * מחרוזת שמתארת כל מבנה (בלי להוסיף צעדים להרצה):
     * מחסנית/תור כמו toString, רשימה: "3 -> 8 -> null", עץ: סריקה תוכית (inorder).
     */
    public static String text(Object structure) {
        if (structure == null) return "null";
        if (structure instanceof Node) {
            StringBuilder b = new StringBuilder();
            java.util.Set<Object> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Object, Boolean>());
            Node<?> p = (Node<?>) structure;
            while (p != null && seen.add(p)) {
                b.append(p.value).append(" -> ");
                p = p.next;
            }
            return b.append(p == null ? "null" : "(מעגל)").toString();
        }
        if (structure instanceof BinNode) {
            StringBuilder b = new StringBuilder("inorder: ");
            inorder((BinNode<?>) structure, b, 0);
            return b.toString().trim();
        }
        if (structure.getClass().isArray()) return arrayText(structure);
        return String.valueOf(structure);
    }

    private static String arrayText(Object a) {
        StringBuilder b = new StringBuilder("[");
        int n = java.lang.reflect.Array.getLength(a);
        for (int i = 0; i < n; i++) {
            Object x = java.lang.reflect.Array.get(a, i);
            b.append(i == 0 ? "" : ", ").append(x != null && x.getClass().isArray() ? arrayText(x) : String.valueOf(x));
        }
        return b.append(']').toString();
    }

    /**
     * להציג מערך (חד או דו ממדי) בחלון, ולעדכן אותו בכל צעד.
     * לא צריך את זה למערכים שנוצרו ב-Build.array / Build.matrix, וגם לא בהרצה שורה-שורה (שם כל המערכים מוצגים לבד).
     */
    public static void watch(Object array) {
        Recorder.sync();
        Recorder.watchArray(array, SourceFiles.guessWatchName(Recorder.callerFrame()));
        Recorder.record(Kind.NOTE, null, Mark.NONE, null, "מציגים את המערך " + Recorder.nameOf(array));
    }

    /** כמו watch(array), עם שם לתצוגה */
    public static void watch(String name, Object array) {
        Recorder.sync();
        Recorder.watchArray(array, name);
        Recorder.record(Kind.NOTE, null, Mark.NONE, null, "מציגים את המערך " + name);
    }

    private static void inorder(BinNode<?> t, StringBuilder b, int depth) {
        if (t == null || depth > 200) return;
        inorder(t.left, b, depth + 1);
        b.append(t.value).append(' ');
        inorder(t.right, b, depth + 1);
    }

    /**
     * הרצה "שורה אחר שורה": כל שורה בקוד מוצגת כצעד, עם המשתנים המקומיים.
     * שמים את זה כשורה הראשונה ב-main (אין צורך בזה כשמריצים מכפתור "הרצה ויזואלית" של התוסף).
     * דורש JDK 11 ומעלה.
     */
    public static void lines() {
        if (LineByLine.isChild() || Recorder.lineMode) return;
        StackTraceElement[] trace = new Throwable().getStackTrace();
        String main = null;
        for (StackTraceElement e : trace) if (e.getMethodName().equals("main")) main = e.getClassName();
        if (main == null) return;
        System.exit(LineByLine.trace(main, new String[0]));
    }

    /** מספר הצעדים המקסימלי שיתועדו (ברירת מחדל 5000) */
    public static void maxSteps(int n) {
        Recorder.maxSteps = n;
    }
}

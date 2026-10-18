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
        Recorder.setName(structure, name, 2);
        Recorder.record(Kind.NOTE, structure, Mark.NONE, null, "שם חדש: " + name);
    }

    /** הערה שתופיע כצעד בהרצה */
    public static void note(String text) {
        Recorder.record(Kind.NOTE, null, Mark.NONE, null, "הערה: " + text);
    }

    /**
     * להציג תווית (שם המשתנה) מעל חוליה / צומת.
     * כדאי לקרוא לזה בכל פעם שהמשתנה מתקדם, למשל: p = p.getNext(); Viz.point("p", p);
     */
    public static void point(String label, Object node) {
        Recorder.point(label, node);
        Recorder.record(Kind.NOTE, node, Mark.READ, null,
                label + "  ←  " + (node == null ? "null" : Recorder.nodeRef(node)));
    }

    /** הפסקת התיעוד (למשל לפני לולאה ארוכה שלא מעניין לראות) */
    public static void off() {
        Recorder.enabled = false;
    }

    /** חידוש התיעוד */
    public static void on() {
        Recorder.enabled = true;
    }

    /** בלי חלון: כל צעד מודפס לקונסול. יש לקרוא לזה בתחילת main */
    public static void textMode() {
        Recorder.textMode = true;
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
        return String.valueOf(structure);
    }

    private static void inorder(BinNode<?> t, StringBuilder b, int depth) {
        if (t == null || depth > 200) return;
        inorder(t.left, b, depth + 1);
        b.append(t.value).append(' ');
        inorder(t.right, b, depth + 1);
    }

    /** מספר הצעדים המקסימלי שיתועדו (ברירת מחדל 5000) */
    public static void maxSteps(int n) {
        Recorder.maxSteps = n;
    }
}

package unit4.collectionsLib;

import java.util.ArrayList;
import java.util.List;

/**
 * מודל הנתונים של "צילומי המצב" (snapshots).
 * כל צעד בתוכנית שומר עותק של מצב כל המבנים באותו רגע, כך שאפשר לנוע קדימה ואחורה.
 * מחלקה פנימית של הספרייה — התלמידים לא צריכים להכיר אותה.
 */
final class Model {
    private Model() {}

    /** סוג הצעד */
    enum Kind { CREATE, CHANGE, READ, CHECK, OUTPUT, NOTE, ERROR, END, LINE, RETURN }

    /** איך לצבוע איבר שהצעד נגע בו */
    enum Mark { NONE, NEW, READ, CHANGED }

    /** מסגרת אחת במחסנית הקריאות של התלמיד */
    static final class Frame {
        final String className, method, file;
        final int line;

        Frame(StackTraceElement e) {
            this(e.getClassName(), e.getMethodName(), e.getFileName(), e.getLineNumber());
        }

        Frame(String className, String method, String file, int line) {
            this.className = className;
            this.method = method;
            this.file = file;
            this.line = line;
        }

        String packagePath() {
            int dot = className.lastIndexOf('.');
            return dot < 0 ? "" : className.substring(0, dot).replace('.', '/') + "/";
        }

        String place() {
            return (file == null ? className : file) + ":" + line;
        }

        String label() {
            String m = method.equals("<init>") ? "constructor" : method;
            return m + "()   —   " + place();
        }
    }

    /** צעד אחד בהרצה */
    static final class Step {
        int index;
        Kind kind;
        String text;
        Frame where;                       // השורה בקוד של התלמיד שגרמה לצעד
        List<Frame> calls = new ArrayList<Frame>(); // מחסנית הקריאות (העליונה ראשונה)
        List<View> views = new ArrayList<View>();
        int outLen;                        // כמה פלט הודפס עד הצעד הזה
        List<String[]> vars;               // במצב שורה-שורה: המשתנים המקומיים {שם, ערך}; אחרת null
        String returned;                   // RETURN: הערך שהפעולה החזירה (null אם void)
        Access access;                     // קריאה / שינוי של מאפיין באובייקט (למשל s.getName()) — או null
    }

    /**
     * אובייקט של מחלקה (Student, Car, או מחלקה של התלמיד) — כרטיס קטן בתוך המבנה, וטבלת המאפיינים שלו.
     * id = זהות האובייקט (אותו אובייקט בכמה מקומות / קישור ל-Access); 0 או שלילי = לא ידוע.
     */
    static final class Obj {
        int id;
        String type;                       // Student
        String title;                      // שורה ראשונה בכרטיס: "נועה"
        String sub;                        // שורה שנייה: "יא2 · 84"
        final List<String[]> fields = new ArrayList<String[]>();   // {שם, ערך}
        final List<String> labels = new ArrayList<String>();       // שמות משתנים שמצביעים עליו (s1, best...)
    }

    /** הצעד קרא ({@link Mark#READ}) או שינה ({@link Mark#CHANGED}) את המאפיין field של האובייקט obj */
    static final class Access {
        Obj obj;
        String field;                      // שם המאפיין, כמו ב-Obj.fields
        String text;                       // "getName()"
        Mark mark = Mark.READ;
    }

    /** אובייקט שמשתנה מצביע אליו ואינו בתוך מבנה: מוצג לבד, כטבלת מאפיינים */
    static final class ObjView extends View {
        Obj obj;
    }

    abstract static class View {
        String title;
        boolean focus;                     // האם הצעד הנוכחי נגע במבנה הזה
        Object src;                        // המבנה האמיתי שממנו נבנה (רק ב-Recorder; לא נקרא בחלון)
    }

    /** מחסנית או תור */
    static final class LinearView extends View {
        boolean stack;
        final List<String> items = new ArrayList<String>(); // מחסנית: הראש ראשון. תור: ראש התור ראשון
        int markIndex = -1;
        Mark mark = Mark.NONE;
        String ghost;                      // ערך שהוצא בצעד הזה (pop / remove)
        final List<Obj> objs = new ArrayList<Obj>();   // במקביל ל-items: אובייקט (או null אם זה ערך רגיל)
        final List<View> subs = new ArrayList<View>(); // במקביל ל-items: מבנה שמוכל בתא (מחסנית של מחסניות...) או null
    }

    /** תא ברשימה מקושרת או צומת בעץ */
    static class Cell {
        String value;
        Mark mark = Mark.NONE;
        boolean current;                   // הצומת האחרון שנגעו בו (ה"מצביע")
        Obj obj;                           // אם הערך הוא אובייקט של מחלקה
        View sub;                          // אם הערך הוא מבנה (מחסנית / תור / רשימה / עץ) — מצויר בתוך התא
        final List<String> labels = new ArrayList<String>(); // שמות מ-Viz.point
    }

    /** שרשרת של Node */
    static final class ChainView extends View {
        final List<Cell> cells = new ArrayList<Cell>();
        int cycleTo = -1;                  // אם האחרון מצביע חזרה לתא קודם
        String joinText;                   // אם האחרון מצביע לצומת שמוצג ברשימה אחרת
        boolean truncated;
    }

    static final class TreeCell extends Cell {
        TreeCell left, right;
        boolean shared;                    // הצומת כבר צויר במקום אחר (מעגל / שיתוף)
    }

    /**
     * משתנה מחרוזת (String): תו בכל תא, עם האינדקסים — ומה שהשורה הנוכחית עושה בו:
     * charAt — חץ לתו; substring — החלק שנחתך מסומן; indexOf — החלק שנמצא; length — כל המחרוזת. op = הסבר, למשל s.substring(1, 4) → "ell"
     */
    static final class StringView extends View {
        String text = "";
        boolean truncated, changed;
        int from = -1, to = -1;            // החלק המסומן [from, to)
        boolean found;                     // החלק המסומן הוא תוצאת חיפוש (צבע אחר)
        final java.util.Map<Integer, List<String>> arrows = new java.util.HashMap<Integer, List<String>>();   // אינדקס → תוויות (i, charAt...)
        final List<String> ops = new ArrayList<String>();   // הסבר לכל פעולה בשורה
        boolean error;                     // פעולה מחוץ לגבולות המחרוזת
    }

    /** מערך חד-ממדי */
    static final class ArrayView extends View {
        final List<String> items = new ArrayList<String>();
        final java.util.Set<Integer> changed = new java.util.HashSet<Integer>();
        final List<Obj> objs = new ArrayList<Obj>();   // במקביל ל-items (או ריק אם אין אובייקטים)
        final List<View> subs = new ArrayList<View>(); // במקביל ל-items (או ריק אם אין מבנים בתוך תאים)
        final List<Object> subSrc = new ArrayList<Object>();   // המבנים עצמם (Recorder בלבד) — לרענון התוכן בכל צעד
        /** אינדקס → שמות משתנים שערכם הוא האינדקס הזה (למשל i, j) */
        final java.util.Map<Integer, List<String>> indexLabels = new java.util.HashMap<Integer, List<String>>();
        boolean truncated;
    }

    /** מערך דו-ממדי */
    static final class MatrixView extends View {
        final List<List<String>> rows = new ArrayList<List<String>>();
        final java.util.Set<String> changed = new java.util.HashSet<String>();   // "שורה,עמודה"
        final List<List<Obj>> objs = new ArrayList<List<Obj>>();   // במקביל ל-rows (או ריק אם אין אובייקטים)
        final java.util.Map<Integer, List<String>> rowLabels = new java.util.HashMap<Integer, List<String>>();
        final java.util.Map<Integer, List<String>> colLabels = new java.util.HashMap<Integer, List<String>>();
    }

    /** עץ של BinNode */
    static final class TreeView extends View {
        TreeCell root;
    }
}

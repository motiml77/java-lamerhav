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

    /** סוג הצעד. INPUT = התוכנית קראה כאן קלט מהמקלדת (Scanner / Console.ReadLine) — מה שהוקלד ב-Step.input */
    enum Kind { CREATE, CHANGE, READ, CHECK, OUTPUT, NOTE, ERROR, END, LINE, RETURN, INPUT }

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
        String input;                      // INPUT: מה שהוקלד (בלי ירידת השורה); null = סוף הקלט (או צעד אחר)
        /**
         * המשתנים של כל פעולה במחסנית, במקביל ל-calls: 0 = הפעולה שרצה (= vars), 1 = מי שקרא לה... (ראו FrameViz).
         * null — לא נשמרו (הרצה ישנה); איבר null — הפעולה הזו עמוק מדי במחסנית
         */
        List<List<String[]>> frameVars;
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
        String error;                      // פעולה שנכשלה כי המבנה ריק ("pop()") — מצויר מקום ריק עם "?" באדום
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
        boolean lost;                      // אף משתנה לא מצביע על החוליה, ואי אפשר להגיע אליה (רשימה מקושרת)
        /** זהות החוליה / הצומת (אותה חוליה בכל הצעדים — בשביל ההנפשה, StructAnim); 0 = לא ידוע (למשל הרצה של C#) */
        int id;
    }

    /** שרשרת של Node */
    static final class ChainView extends View {
        final List<Cell> cells = new ArrayList<Cell>();
        int cycleTo = -1;                  // אם האחרון מצביע חזרה לתא קודם
        String joinText;                   // אם האחרון מצביע לצומת שמוצג ברשימה אחרת
        boolean truncated;
        String lostNote, lostHint;         // יש בשרשרת חוליות שאבדו: מה קרה, ומה עושים (או null)
        final List<String> nullLabels = new ArrayList<String>();   // משתנים שהם null בסוף השרשרת (p אחרי האחרונה) — באדום
        /**
         * "ענפים": שרשראות שהאחרונה שלהן מצביעה לחוליה באמצע השרשרת הזו — למשל חוליה חדשה q אחרי q.setNext(p.getNext()),
         * או חוליה שהוצאה מהרשימה ועדיין מצביעה הלאה. מצוירות בשורה מתחת, עם חץ אל החוליה שהן מצביעות עליה (joinTo)
         */
        final List<ChainView> branches = new ArrayList<ChainView>();
        int joinTo = -1;                   // בענף: האינדקס של החוליה בשרשרת הראשית שהאחרונה בענף מצביעה עליה
    }

    /**
     * התוכנית נעצרה בשגיאה: כרטיס אדום בראש התצוגה — מה קרה, למה, ומה עושים — עם ציור קטן
     * (kind: "empty-stack" / "empty-queue" — מבנה ריק; "null" — name מצביע על null; "index"; "recursion"; "loop" — לולאה שלא נגמרת
     * (name = המשתנה העיקרי בתנאי, op = התנאי — ראו LoopCard); "other")
     */
    static final class ProblemView extends View {
        String kind = "other", what = "", why = "", hint = "", name, op;
        int index = -1, length = -1;       // index: התא שניסו, length: כמה תאים יש (loop: index = כמה סיבובים)
        int line = -1;                     // loop: שורת התנאי של הלולאה שלא נגמרת (התגית בקוד)
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

    /**
     * לולאה שהשורה הנוכחית בתוכה: הכותרת, מספר הסיבוב, התנאי (עם הערכים ומה יצא), וב-for שסופר — כל הערכים של המשתנה
     * (values), איפה הוא עכשיו (currentIndex), ו-done אם כבר עבר את כולם. depth = 0 לחיצונית, 1 לפנימית...
     */
    static final class LoopView extends View {
        int depth, iteration;
        String kind, header, cond = "", condValues = "";
        Boolean condResult;                // null = לא הצלחנו לחשב
        boolean atHeader;                  // עכשיו בודקים את התנאי
        String var;
        Integer varValue;
        final List<Integer> values = new ArrayList<Integer>();
        int currentIndex = -1;
        boolean done;
        // על מה הלולאה עוברת
        String subjectName, subjectKind;   // arr / q ; "מערך" / "תור" / "מחסנית" / "רשימה"
        final List<String> elements = new ArrayList<String>();     // מערך: האיבר בכל מקום בסרגל (במקביל ל-values)
        final List<String> structItems = new ArrayList<String>();  // תור / מחסנית: מה יש בו עכשיו (הראש ראשון)
        String structNote;                 // "יש עוד 3 איברים" / "p מצביע על חוליה עם הערך 5"
        String historyVar;                 // while: המשתנה שבתנאי, וערכו בתחילת כל סיבוב
        final List<String> history = new ArrayList<String>();
        String note;                       // "הלולאה הפנימית מתחילה מחדש..."
        int rangeFrom = -1, rangeTo = -1;  // מערך שלם בסרגל: האינדקסים שהלולאה עוברת עליהם (השאר — מעומעמים)
        boolean descending;                // הלולאה יורדת (i--): מה שכבר עבר — מימין למקום הנוכחי
    }

    /** לולאות מקוננות: המונים, זה בתוך זה (i ובתוכו j) — כדי לראות מה בתוך מה */
    static final class LoopCountersView extends View {
        final List<String[]> levels = new ArrayList<String[]>();   // {שם, ערך, טווח} מהחיצונית לפנימית
    }

    /** שתי לולאות for מקוננות: טבלה של כל הזוגות (שורה = ערך של החיצוני, עמודה = ערך של הפנימי) */
    static final class LoopGridView extends View {
        static final int NONE = 0, TODO = 1, DONE = 2, NOW = 3;
        String rowVar, colVar;
        final List<Integer> rows = new ArrayList<Integer>(), cols = new ArrayList<Integer>();
        final List<int[]> cells = new ArrayList<int[]>();
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

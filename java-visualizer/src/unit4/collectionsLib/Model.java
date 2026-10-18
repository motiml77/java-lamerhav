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
    enum Kind { CREATE, CHANGE, READ, CHECK, OUTPUT, NOTE, ERROR, END }

    /** איך לצבוע איבר שהצעד נגע בו */
    enum Mark { NONE, NEW, READ, CHANGED }

    /** מסגרת אחת במחסנית הקריאות של התלמיד */
    static final class Frame {
        final String className, method, file;
        final int line;

        Frame(StackTraceElement e) {
            className = e.getClassName();
            method = e.getMethodName();
            file = e.getFileName();
            line = e.getLineNumber();
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
    }

    abstract static class View {
        String title;
        boolean focus;                     // האם הצעד הנוכחי נגע במבנה הזה
    }

    /** מחסנית או תור */
    static final class LinearView extends View {
        boolean stack;
        final List<String> items = new ArrayList<String>(); // מחסנית: הראש ראשון. תור: ראש התור ראשון
        int markIndex = -1;
        Mark mark = Mark.NONE;
        String ghost;                      // ערך שהוצא בצעד הזה (pop / remove)
    }

    /** תא ברשימה מקושרת או צומת בעץ */
    static class Cell {
        String value;
        Mark mark = Mark.NONE;
        boolean current;                   // הצומת האחרון שנגעו בו (ה"מצביע")
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

    /** עץ של BinNode */
    static final class TreeView extends View {
        TreeCell root;
    }
}

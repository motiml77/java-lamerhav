package unit4.collectionsLib;

/**
 * פעולות עזר שמקבלות מבנה ומחזירות תשובה — בלי לשנות את המבנה שנשלח. עם שורה אחת בראש הקובץ
 * (התוסף מוסיף אותה לבד כשיוצרים מבנה) קוראים להן ישירות, בלי "Helpers.":
 * <pre>
 *   import static unit4.collectionsLib.Helpers.*;
 *   ...
 *   int n = size(s);                     // כמה איברים במחסנית / בתור / ברשימה / בעץ
 *   Stack&lt;Integer&gt; copy = duplicate(s);   // עותק באותו סדר
 * </pre>
 * גם במבנה בתוך מבנה (תור של מחסניות, רשימה של תורים...): size סופר את האיברים של המבנה החיצוני,
 * ו-duplicate מעתיק גם את המבנים הפנימיים — שינוי בעותק לא משנה את המקור.
 * בהרצה הוויזואלית כל קריאה היא צעד אחד (בלי כל ה-pop וה-push שבפנים).
 */
public final class Helpers {
    private Helpers() {}

    /** כמה איברים יש במחסנית — המחסנית נשארת כמו שהייתה */
    public static <T> int size(Stack<T> s) {
        Recorder.sync();
        int n = s.items.size();
        Recorder.read(s, "size(" + Recorder.nameOf(s) + ")  →  " + n);
        return n;
    }

    /** כמה איברים יש בתור — התור נשאר כמו שהיה */
    public static <T> int size(Queue<T> q) {
        Recorder.sync();
        int n = q.items.size();
        Recorder.read(q, "size(" + Recorder.nameOf(q) + ")  →  " + n);
        return n;
    }

    /** כמה חוליות יש ברשימה (מ-lst עד הסוף) — הרשימה נשארת כמו שהייתה. רשימה ריקה (null) — 0 */
    public static <T> int size(Node<T> lst) {
        Recorder.sync();
        int n = 0;
        for (Node<T> p = lst; p != null; p = p.next) n++;
        if (lst != null) Recorder.read(lst, "size(" + Recorder.nodeRef(lst) + ")  →  " + n);
        return n;
    }

    /** כמה צמתים יש בעץ — העץ נשאר כמו שהיה. עץ ריק (null) — 0 */
    public static <T> int size(BinNode<T> t) {
        Recorder.sync();
        int n = count(t);
        if (t != null) Recorder.read(t, "size(" + Recorder.nodeRef(t) + ")  →  " + n);
        return n;
    }

    private static int count(BinNode<?> t) {
        return t == null ? 0 : 1 + count(t.left) + count(t.right);
    }

    /** עותק של המחסנית, באותו סדר (הראש של העותק = הראש של המקור) — המחסנית המקורית נשארת כמו שהייתה */
    public static <T> Stack<T> duplicate(Stack<T> s) {
        Recorder.sync();
        Recorder.suspend();
        Stack<T> copy;
        try {
            copy = new Stack<T>();
            for (T x : s.items) copy.items.add(deep(x));
        } finally {
            Recorder.resume();
        }
        Recorder.nameCopy(copy);
        Recorder.create(copy, "duplicate(" + Recorder.nameOf(s) + ")  →  עותק חדש");
        return copy;
    }

    /** עותק של התור, באותו סדר — התור המקורי נשאר כמו שהיה */
    public static <T> Queue<T> duplicate(Queue<T> q) {
        Recorder.sync();
        Recorder.suspend();
        Queue<T> copy;
        try {
            copy = new Queue<T>();
            for (T x : q.items) copy.items.add(deep(x));
        } finally {
            Recorder.resume();
        }
        Recorder.nameCopy(copy);
        Recorder.create(copy, "duplicate(" + Recorder.nameOf(q) + ")  →  עותק חדש");
        return copy;
    }

    /** עותק של הרשימה: חוליות חדשות באותו סדר — הרשימה המקורית נשארת כמו שהייתה. רשימה ריקה (null) — null */
    public static <T> Node<T> duplicate(Node<T> lst) {
        if (lst == null) return null;
        Recorder.sync();
        Recorder.suspend();
        Node<T> first;
        try {
            first = copyList(lst);
        } finally {
            Recorder.resume();
        }
        Recorder.nameCopy(first);
        Recorder.create(first, "duplicate(" + Recorder.nodeRef(lst) + ")  →  רשימה חדשה");
        return first;
    }

    /** עותק של העץ: צמתים חדשים באותו מבנה — העץ המקורי נשאר כמו שהיה. עץ ריק (null) — null */
    public static <T> BinNode<T> duplicate(BinNode<T> t) {
        if (t == null) return null;
        Recorder.sync();
        Recorder.suspend();
        BinNode<T> root;
        try {
            root = copyTree(t);
        } finally {
            Recorder.resume();
        }
        Recorder.nameCopy(root);
        Recorder.create(root, "duplicate(" + Recorder.nodeRef(t) + ")  →  עץ חדש");
        return root;
    }

    private static <T> Node<T> copyList(Node<T> lst) {
        Node<T> first = new Node<T>(deep(lst.value)), last = first;
        for (Node<T> p = lst.next; p != null; p = p.next) {
            last.next = new Node<T>(deep(p.value));
            last = last.next;
        }
        return first;
    }

    private static <T> BinNode<T> copyTree(BinNode<T> t) {
        if (t == null) return null;
        return new BinNode<T>(copyTree(t.left), deep(t.value), copyTree(t.right));
    }

    /** איבר שהוא בעצמו מבנה (מחסנית בתוך תור...) — מועתק גם הוא; כל איבר אחר (מספר, מחרוזת, תלמיד) — כמו שהוא */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <T> T deep(T x) {
        if (x instanceof Stack) {
            Stack c = new Stack();
            for (Object y : ((Stack<?>) x).items) c.items.add(deep(y));
            return (T) c;
        }
        if (x instanceof Queue) {
            Queue c = new Queue();
            for (Object y : ((Queue<?>) x).items) c.items.add(deep(y));
            return (T) c;
        }
        if (x instanceof Node) return (T) copyList((Node) x);
        if (x instanceof BinNode) return (T) copyTree((BinNode) x);
        return x;
    }
}

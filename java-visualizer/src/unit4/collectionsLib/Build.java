package unit4.collectionsLib;

import java.util.Random;

/**
 * בניה מהירה של מבנים לבדיקה — בשורה אחת, בלי לולאות.
 * הבנייה עצמה מוצגת כצעד אחד בלבד.
 *
 * <pre>
 *   Stack&lt;Integer&gt; s = Build.stack(1, 2, 3);      // 3 בראש המחסנית
 *   Queue&lt;String&gt;  q = Build.queue("a", "b");      // "a" בראש התור
 *   Node&lt;Integer&gt;  lst = Build.list(4, 7, 1);       // 4 -> 7 -> 1 -> null
 *   BinNode&lt;Integer&gt; t = Build.tree(5, 3, 8, null, 4); // לפי רמות (null = אין צומת)
 *   BinNode&lt;Integer&gt; b = Build.bst(5, 3, 8, 1, 4);   // עץ חיפוש בינארי
 *   Stack&lt;Integer&gt; r = Build.randomStack(6, 1, 20); // 6 מספרים אקראיים בין 1 ל-20
 * </pre>
 */
public final class Build {
    private Build() {}

    private static final Random RND = new Random();

    /** מחסנית: האיברים נכנסים לפי הסדר (push), כך שהאחרון יהיה בראש */
    @SafeVarargs
    public static <T> Stack<T> stack(T... items) {
        Recorder.sync();
        Recorder.suspend();
        Stack<T> s;
        try {
            s = new Stack<T>();
            for (T x : items) s.push(x);
        } finally {
            Recorder.resume();
        }
        Recorder.nameFromCaller(s);
        Recorder.create(s, "נבנתה מחסנית " + Recorder.nameOf(s) + " = " + s + "  (הראש משמאל)");
        return s;
    }

    /** תור: האיברים נכנסים לפי הסדר (insert), כך שהראשון יהיה בראש התור */
    @SafeVarargs
    public static <T> Queue<T> queue(T... items) {
        Recorder.sync();
        Recorder.suspend();
        Queue<T> q;
        try {
            q = new Queue<T>();
            for (T x : items) q.insert(x);
        } finally {
            Recorder.resume();
        }
        Recorder.nameFromCaller(q);
        Recorder.create(q, "נבנה תור " + Recorder.nameOf(q) + " = " + q + "  (ראש התור משמאל)");
        return q;
    }

    /** רשימה מקושרת: מחזיר את החוליה הראשונה (או null אם אין איברים) */
    @SafeVarargs
    public static <T> Node<T> list(T... items) {
        Recorder.sync();
        Recorder.suspend();
        Node<T> head = null;
        try {
            for (int i = items.length - 1; i >= 0; i--) head = new Node<T>(items[i], head);
        } finally {
            Recorder.resume();
        }
        if (head == null) return null;
        Recorder.nameFromCaller(head);
        Recorder.create(head, "נבנתה רשימה " + java.util.Arrays.toString(items));
        return head;
    }

    /**
     * עץ בינארי לפי רמות (כמו מערך): האיבר ה-i הוא אב של 2i+1 (שמאל) ו-2i+2 (ימין).
     * null פירושו "אין כאן צומת".  לדוגמה: Build.tree(5, 3, 8, null, 4)
     */
    @SafeVarargs
    public static <T> BinNode<T> tree(T... levelOrder) {
        Recorder.sync();
        Recorder.suspend();
        BinNode<T> root;
        try {
            root = treeAt(levelOrder, 0);
        } finally {
            Recorder.resume();
        }
        if (root == null) return null;
        Recorder.nameFromCaller(root);
        Recorder.create(root, "נבנה עץ " + java.util.Arrays.toString(levelOrder));
        return root;
    }

    private static <T> BinNode<T> treeAt(T[] a, int i) {
        if (i >= a.length || a[i] == null) return null;
        return new BinNode<T>(treeAt(a, 2 * i + 1), a[i], treeAt(a, 2 * i + 2));
    }

    /** עץ חיפוש בינארי: הכנסה לפי הסדר */
    @SafeVarargs
    public static <T extends Comparable<T>> BinNode<T> bst(T... items) {
        Recorder.sync();
        Recorder.suspend();
        BinNode<T> root = null;
        try {
            for (T x : items) root = bstInsert(root, x);
        } finally {
            Recorder.resume();
        }
        if (root == null) return null;
        Recorder.nameFromCaller(root);
        Recorder.create(root, "נבנה עץ חיפוש " + java.util.Arrays.toString(items));
        return root;
    }

    private static <T extends Comparable<T>> BinNode<T> bstInsert(BinNode<T> t, T x) {
        if (t == null) return new BinNode<T>(x);
        if (x.compareTo(t.value) < 0) t.left = bstInsert(t.left, x);
        else t.right = bstInsert(t.right, x);
        return t;
    }

    // ---- אקראי

    public static Stack<Integer> randomStack(int n, int min, int max) {
        Recorder.sync();
        return stack(randoms(n, min, max));
    }

    public static Queue<Integer> randomQueue(int n, int min, int max) {
        Recorder.sync();
        return queue(randoms(n, min, max));
    }

    public static Node<Integer> randomList(int n, int min, int max) {
        Recorder.sync();
        return list(randoms(n, min, max));
    }

    public static BinNode<Integer> randomBst(int n, int min, int max) {
        Recorder.sync();
        return bst(randoms(n, min, max));
    }

    private static Integer[] randoms(int n, int min, int max) {
        Integer[] a = new Integer[n];
        for (int i = 0; i < n; i++) a[i] = min + RND.nextInt(max - min + 1);
        return a;
    }
}

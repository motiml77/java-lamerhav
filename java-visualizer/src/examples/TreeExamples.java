package examples;

import unit4.collectionsLib.*;

/** דוגמאות עצים ברקורסיה: מספר צמתים, גובה, הכנסה לעץ חיפוש */
public class TreeExamples {

    /** כמה צמתים בעץ */
    public static int count(BinNode<Integer> t) {
        if (t == null) return 0;                             // מקרה בסיס: עץ ריק — אפס צמתים
        return 1 + count(t.getLeft()) + count(t.getRight()); // הצומת הזה + כל מה שבתת-העץ השמאלי והימני
    }

    /** גובה העץ (עלה בודד = 0) */
    public static int height(BinNode<Integer> t) {
        if (t == null) return -1;                            // מקרה בסיס: עץ ריק
        return 1 + Math.max(height(t.getLeft()), height(t.getRight()));   // הצד הגבוה יותר + צעד אחד
    }

    /** הכנסה לעץ חיפוש בינארי */
    public static void insert(BinNode<Integer> t, int x) {
        if (x < t.getValue()) {
            if (t.hasLeft()) insert(t.getLeft(), x);
            else t.setLeft(new BinNode<Integer>(x));
        } else {
            if (t.hasRight()) insert(t.getRight(), x);
            else t.setRight(new BinNode<Integer>(x));
        }
    }

    public static void main(String[] args) {
        BinNode<Integer> t = new BinNode<Integer>(5);   // השורש
        t.setLeft(new BinNode<Integer>(3));
        t.setRight(new BinNode<Integer>(8));
        t.getLeft().setLeft(new BinNode<Integer>(1));
        t.getLeft().setRight(new BinNode<Integer>(4));
        t.getRight().setRight(new BinNode<Integer>(9));
        System.out.println("מספר צמתים: " + count(t));
        System.out.println("גובה: " + height(t));
        insert(t, 7);
        insert(t, 2);
    }
}

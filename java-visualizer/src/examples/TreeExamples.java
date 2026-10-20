package examples;

import unit4.collectionsLib.*;

/** דוגמאות עצים: מספר צמתים, גובה, הכנסה לעץ חיפוש */
public class TreeExamples {

    public static int count(BinNode<Integer> t) {
        if (t == null) return 0;
        return 1 + count(t.getLeft()) + count(t.getRight());
    }

    public static int height(BinNode<Integer> t) {
        if (t == null) return -1;
        return 1 + Math.max(height(t.getLeft()), height(t.getRight()));
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

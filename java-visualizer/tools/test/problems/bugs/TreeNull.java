package bugs;

import unit4.collectionsLib.*;

public class TreeNull {
    // סכום העץ — בלי תנאי עצירה ל-null
    public static int sum(BinNode<Integer> t) {
        return t.getValue() + sum(t.getLeft()) + sum(t.getRight());
    }

    public static void main(String[] args) {
        BinNode<Integer> t = new BinNode<Integer>(new BinNode<Integer>(2), 1, new BinNode<Integer>(3));
        System.out.println(sum(t));
    }
}

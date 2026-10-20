import unit4.collectionsLib.*;

public class PromoTree {

    // הכנסה לעץ חיפוש בינארי
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
        BinNode<Integer> t = new BinNode<Integer>(50);
        t.setLeft(new BinNode<Integer>(30));
        t.setRight(new BinNode<Integer>(70));
        t.getLeft().setLeft(new BinNode<Integer>(20));
        t.getRight().setLeft(new BinNode<Integer>(60));
        t.getRight().setRight(new BinNode<Integer>(80));
        insert(t, 40);
        insert(t, 65);
        insert(t, 90);
    }
}

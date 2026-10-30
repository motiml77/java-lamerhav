import unit4.collectionsLib.BinNode;

// רקורסיה על עץ בינארי
public class TreeCount {
    static int count(BinNode<Integer> t) {
        if (t == null) return 0;
        return 1 + count(t.getLeft()) + count(t.getRight());
    }

    public static void main(String[] args) {
        BinNode<Integer> t = new BinNode<Integer>(new BinNode<Integer>(2), 5, new BinNode<Integer>(8));
        System.out.println(count(t));
    }
}

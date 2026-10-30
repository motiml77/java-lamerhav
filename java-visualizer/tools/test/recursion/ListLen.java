import unit4.collectionsLib.Node;

// רקורסיה על שרשרת חוליות
public class ListLen {
    static int length(Node<Integer> p) {
        if (p == null) return 0;
        return 1 + length(p.getNext());
    }

    public static void main(String[] args) {
        Node<Integer> list = new Node<Integer>(3, new Node<Integer>(7, new Node<Integer>(1)));
        System.out.println(length(list));
    }
}

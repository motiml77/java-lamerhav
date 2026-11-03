import unit4.collectionsLib.*;

public class Big {
    public static void main(String[] args) {
        Queue<Integer> q = new Queue<Integer>();
        for (int i = 0; i < 10000; i++) {
            q.insert(i);
        }
        Node<Integer> first = null;
        for (int i = 0; i < 10000; i++) {
            first = new Node<Integer>(i, first);
        }
        System.out.println("done " + q.head() + " " + first.getValue());
    }
}

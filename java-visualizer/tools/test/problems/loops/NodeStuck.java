package loops;

import unit4.collectionsLib.*;

public class NodeStuck {
    // סכום הרשימה — עם טעות: שכחו להתקדם (p = p.getNext())
    public static int sum(Node<Integer> lst) {
        int total = 0;
        Node<Integer> p = lst;
        while (p != null) {
            total = total + p.getValue();
        }
        return total;
    }

    public static void main(String[] args) {
        Node<Integer> lst = new Node<Integer>(3, new Node<Integer>(8, new Node<Integer>(5)));
        System.out.println(sum(lst));
    }
}

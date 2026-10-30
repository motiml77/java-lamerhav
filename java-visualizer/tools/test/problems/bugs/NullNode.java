package bugs;

import unit4.collectionsLib.*;

public class NullNode {
    // הערך האחרון ברשימה — עם טעות: התנאי בודק את p ולא את p.getNext()
    public static int last(Node<Integer> lst) {
        Node<Integer> p = lst;
        while (p != null) {
            p = p.getNext();
        }
        return p.getValue();
    }

    public static void main(String[] args) {
        Node<Integer> lst = new Node<Integer>(3, new Node<Integer>(8, new Node<Integer>(5)));
        System.out.println(last(lst));
    }
}

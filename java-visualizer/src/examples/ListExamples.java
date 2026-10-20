package examples;

import unit4.collectionsLib.*;

/** דוגמאות רשימה מקושרת: סכום, מחיקה, היפוך */
public class ListExamples {

    public static int sum(Node<Integer> lst) {
        int total = 0;
        Node<Integer> p = lst;
        while (p != null) {
            total += p.getValue();
            p = p.getNext();
        }
        return total;
    }

    /** מחיקת החוליה הראשונה שערכה x. מחזירה את ראש הרשימה החדש */
    public static Node<Integer> remove(Node<Integer> lst, int x) {
        if (lst == null) return null;
        if (lst.getValue() == x) return lst.getNext();
        Node<Integer> prev = lst;
        while (prev.hasNext() && prev.getNext().getValue() != x) {
            prev = prev.getNext();
        }
        if (prev.hasNext()) {
            prev.setNext(prev.getNext().getNext());
        }
        return lst;
    }

    /** היפוך הרשימה במקום */
    public static Node<Integer> reverse(Node<Integer> lst) {
        Node<Integer> prev = null;
        Node<Integer> cur = lst;
        while (cur != null) {
            Node<Integer> next = cur.getNext();
            cur.setNext(prev);
            prev = cur;
            cur = next;
        }
        return prev;
    }

    public static void main(String[] args) {
        Node<Integer> lst = new Node<Integer>(6);   // מתחילים מהחוליה האחרונה
        lst = new Node<Integer>(1, lst);
        lst = new Node<Integer>(8, lst);
        lst = new Node<Integer>(3, lst);   // החוליה הראשונה
        System.out.println("סכום: " + sum(lst));

        lst = remove(lst, 1);
        lst = reverse(lst);
    }
}

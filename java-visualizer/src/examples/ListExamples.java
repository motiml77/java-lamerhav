package examples;

import unit4.collectionsLib.*;

/** דוגמאות רשימה מקושרת: סכום, מחיקה, היפוך */
public class ListExamples {

    public static int sum(Node<Integer> lst) {
        int total = 0;
        Node<Integer> p = lst;
        Viz.point("p", p);                     // אופציונלי: תווית "p" מעל החוליה
        while (p != null) {
            total += p.getValue();
            p = p.getNext();
            Viz.point("p", p);
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
            Viz.point("prev", prev);
            Viz.point("cur", cur);
        }
        return prev;
    }

    public static void main(String[] args) {
        Node<Integer> lst = Build.list(3, 8, 1, 6);
        System.out.println("סכום: " + sum(lst));
        Viz.point("p", null);

        lst = remove(lst, 1);
        Viz.name(lst, "lst");
        lst = reverse(lst);
        Viz.name(lst, "lst");
    }
}

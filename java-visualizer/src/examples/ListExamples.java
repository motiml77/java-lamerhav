package examples;

import unit4.collectionsLib.*;

/** דוגמאות רשימה מקושרת: סכום, מחיקה, היפוך */
public class ListExamples {

    public static int sum(Node<Integer> lst) {
        int total = 0;                   // כאן מצטבר הסכום
        Node<Integer> curr = lst;        // מצביע עזר: עובר חוליה אחרי חוליה (lst נשאר בראש הרשימה)
        while (curr != null) {
            total += curr.getValue();
            curr = curr.getNext();       // מתקדמים לחוליה הבאה
        }
        return total;
    }

    /** מחיקת החוליה הראשונה שערכה x. מחזירה את ראש הרשימה החדש */
    public static Node<Integer> remove(Node<Integer> lst, int x) {
        if (lst == null) return null;
        if (lst.getValue() == x) return lst.getNext();   // מוחקים את הראש — הבא הופך לראש
        Node<Integer> curr = lst;        // עומד על החוליה שלפני זו שבודקים
        while (curr.hasNext() && curr.getNext().getValue() != x) {
            curr = curr.getNext();
        }
        if (curr.hasNext()) {
            curr.setNext(curr.getNext().getNext());      // "מדלגים" על החוליה שמוחקים
        }
        return lst;
    }

    /** היפוך הרשימה במקום */
    public static Node<Integer> reverse(Node<Integer> lst) {
        Node<Integer> prev = null;       // החוליה שכבר הפכנו (בהתחלה אין)
        Node<Integer> curr = lst;        // החוליה שהופכים עכשיו
        while (curr != null) {
            Node<Integer> next = curr.getNext();   // שומרים את הבאה — לפני שמשנים את הקישור
            curr.setNext(prev);                    // הופכים את החץ אחורה
            prev = curr;
            curr = next;
        }
        return prev;                     // prev היא הראש החדש
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

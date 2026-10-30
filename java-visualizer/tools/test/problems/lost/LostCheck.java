package lost;

import unit4.collectionsLib.*;

// "חוליות שאבדו": מקרים שבהם אסור לסמן (הרשימה עדיין מוחזקת) — ומקרה אחד שבו צריך
public class LostCheck {
    static Node<Integer> saved;   // משתנה סטטי מחזיק רשימה

    static class MyList {         // מחלקה של התלמיד שמחזיקה רשימה
        Node<Integer> first;
        MyList(Node<Integer> f) { first = f; }
        int size() {
            int c = 0;
            Node<Integer> p = first;
            while (p != null) { c++; p = p.getNext(); }
            return c;
        }
    }

    // הפרמטר מתקדם — אבל main עדיין מחזיק את הראש: לא אבד
    static int count(Node<Integer> lst) {
        int c = 0;
        while (lst != null) {
            c++;
            lst = lst.getNext();
        }
        return c;
    }

    // רקורסיה שבונה רשימה חדשה
    static Node<Integer> copy(Node<Integer> lst) {
        if (lst == null) return null;
        Node<Integer> rest = copy(lst.getNext());
        return new Node<Integer>(lst.getValue(), rest);
    }

    static int walkSaved() {
        int s = 0;
        Node<Integer> p = saved;
        saved = null;
        saved = p;   // הוחזר
        while (p != null) { s += p.getValue(); p = p.getNext(); }
        return s;
    }

    public static void main(String[] args) {
        Node<Integer> lst = new Node<Integer>(1, new Node<Integer>(2, new Node<Integer>(3)));
        System.out.println("count " + count(lst));
        MyList m = new MyList(new Node<Integer>(7, new Node<Integer>(8)));
        System.out.println("size " + m.size());
        Node<Integer> c = copy(lst);
        System.out.println("copy " + c);
        saved = new Node<Integer>(4, new Node<Integer>(5));
        System.out.println("saved " + walkSaved());
        System.out.println("MARK-NO-LOSS-ABOVE");
        // עכשיו טעות אמיתית: מתקדמים עם lst עצמו
        while (lst.hasNext()) lst = lst.getNext();
        System.out.println("end " + lst.getValue());
    }
}

import unit4.collectionsLib.*;

// Helpers.size / Helpers.duplicate: לא משנים את המבנה שנשלח, ומעתיקים גם מבנים פנימיים
public class HelpersCheck {
    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
        System.out.println("  ok  " + what);
    }

    public static void main(String[] args) {
        Stack<Integer> s = new Stack<Integer>();
        s.push(1); s.push(2); s.push(3);
        check(Helpers.size(s) == 3 && s.toString().equals("[3,2,1]"), "size של מחסנית, והמחסנית לא השתנתה");
        Stack<Integer> sc = Helpers.duplicate(s);
        check(sc.toString().equals("[3,2,1]") && sc != s && s.toString().equals("[3,2,1]"), "duplicate של מחסנית באותו סדר");
        sc.pop();
        check(s.toString().equals("[3,2,1]"), "שינוי בעותק לא משנה את המקור");

        Queue<Stack<Integer>> qs = new Queue<Stack<Integer>>();
        Stack<Integer> in1 = new Stack<Integer>(); in1.push(7); in1.push(8);
        qs.insert(in1); qs.insert(new Stack<Integer>());
        check(Helpers.size(qs) == 2, "size של תור של מחסניות = מספר המחסניות");
        Queue<Stack<Integer>> qc = Helpers.duplicate(qs);
        check(qc.toString().equals(qs.toString()), "duplicate של תור של מחסניות: " + qc);
        qc.head().pop();
        check(in1.toString().equals("[8,7]"), "המחסנית הפנימית הועתקה (pop בעותק לא נוגע במקור)");
        check(Helpers.size(in1) == 2 && Helpers.size(qc.head()) == 1, "size של מחסנית פנימית");

        Node<String> lst = new Node<String>("a", new Node<String>("b", new Node<String>("c")));
        check(Helpers.size(lst) == 3 && Helpers.size((Node<String>) null) == 0, "size של רשימה (ושל רשימה ריקה)");
        Node<String> lc = Helpers.duplicate(lst);
        lc.getNext().setValue("X");
        check(lst.getNext().getValue().equals("b") && lc != lst && lc.getNext().getValue().equals("X"), "duplicate של רשימה: חוליות חדשות");

        Node<Queue<Integer>> lq = new Node<Queue<Integer>>(new Queue<Integer>());
        lq.getValue().insert(5);
        Node<Queue<Integer>> lqc = Helpers.duplicate(lq);
        lqc.getValue().remove();
        check(lq.getValue().toString().equals("[5]"), "רשימה של תורים: התור הפנימי הועתק");

        BinNode<Integer> t = new BinNode<Integer>(new BinNode<Integer>(1), 2, new BinNode<Integer>(null, 3, new BinNode<Integer>(4)));
        check(Helpers.size(t) == 4, "size של עץ");
        BinNode<Integer> tc = Helpers.duplicate(t);
        tc.getRight().setValue(99);
        check(t.getRight().getValue() == 3 && Helpers.size(tc) == 4, "duplicate של עץ: צמתים חדשים");

        Queue<Integer> q = new Queue<Integer>();
        check(Helpers.size(q) == 0 && Helpers.duplicate(q).isEmpty(), "תור ריק");
        System.out.println("MARK-END");
    }
}

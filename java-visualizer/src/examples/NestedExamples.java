package examples;

import unit4.collectionsLib.*;

/**
 * מבנה בתוך מבנה: מחסנית של מחסניות, רשימה של תורים, עץ של מחסניות...
 * בחלון ההרצה כל מבנה פנימי מצויר בתוך התא של המבנה החיצוני.
 */
public class NestedExamples {

    /** סכום כל המספרים במחסנית של מחסניות — שני המבנים חוזרים למצבם המקורי */
    public static int sumAll(Stack<Stack<Integer>> stk) {
        Stack<Stack<Integer>> tmp = new Stack<Stack<Integer>>();   // עזר: שומר את המחסניות הפנימיות
        int sum = 0;                                               // כאן סופרים
        while (!stk.isEmpty()) {
            Stack<Integer> inner = stk.pop();                      // מחסנית פנימית אחת
            Stack<Integer> innerTmp = new Stack<Integer>();        // עזר לפנימית
            while (!inner.isEmpty()) {
                int x = inner.pop();
                sum += x;
                innerTmp.push(x);
            }
            while (!innerTmp.isEmpty()) {
                inner.push(innerTmp.pop());                        // מחזירים את הפנימית כמו שהייתה
            }
            tmp.push(inner);
        }
        while (!tmp.isEmpty()) {
            stk.push(tmp.pop());
        }
        return sum;
    }

    /** מרוקנת את המחסנית של המחסניות לתוך מחסנית אחת של מספרים */
    public static Stack<Integer> flatten(Stack<Stack<Integer>> stk) {
        Stack<Integer> all = new Stack<Integer>();
        while (!stk.isEmpty()) {
            Stack<Integer> inner = stk.pop();
            while (!inner.isEmpty()) {
                all.push(inner.pop());
            }
        }
        return all;
    }

    /** כמה מספרים יש בסך הכול ברשימה מקושרת של תורים (התורים חוזרים למצבם) */
    public static int totalSize(Node<Queue<Integer>> lst) {
        int total = 0;
        Node<Queue<Integer>> curr = lst;                           // מצביע שעובר על הרשימה
        while (curr != null) {
            Queue<Integer> q = curr.getValue();                    // התור שבחוליה הנוכחית
            Queue<Integer> tmp = new Queue<Integer>();
            while (!q.isEmpty()) {
                tmp.insert(q.remove());
                total++;
            }
            while (!tmp.isEmpty()) {
                q.insert(tmp.remove());
            }
            curr = curr.getNext();
        }
        return total;
    }

    /** מכניסה את x לראש כל המחסניות שבעץ (רקורסיה על העץ) */
    public static void pushAll(BinNode<Stack<Integer>> t, int x) {
        if (t == null) return;                                     // מקרה בסיס
        t.getValue().push(x);                                      // הערך בצומת הוא מחסנית שלמה
        pushAll(t.getLeft(), x);
        pushAll(t.getRight(), x);
    }

    public static void main(String[] args) {
        // מחסנית של מחסניות
        Stack<Stack<Integer>> stk = new Stack<Stack<Integer>>();
        Stack<Integer> inner = new Stack<Integer>();
        inner.push(5);
        inner.push(2);
        stk.push(inner);
        inner = new Stack<Integer>();
        inner.push(8);
        inner.push(1);
        inner.push(9);
        stk.push(inner);
        System.out.println("סכום: " + sumAll(stk));

        // רשימה מקושרת של תורים
        Queue<Integer> q1 = new Queue<Integer>();
        q1.insert(3);
        q1.insert(7);
        Queue<Integer> q2 = new Queue<Integer>();
        q2.insert(4);
        Node<Queue<Integer>> lst = new Node<Queue<Integer>>(q2);
        lst = new Node<Queue<Integer>>(q1, lst);
        System.out.println("כמה מספרים בתורים: " + totalSize(lst));

        // עץ של מחסניות
        Stack<Integer> s1 = new Stack<Integer>();
        s1.push(1);
        Stack<Integer> s2 = new Stack<Integer>();
        s2.push(2);
        s2.push(3);
        BinNode<Stack<Integer>> t = new BinNode<Stack<Integer>>(s1);
        t.setLeft(new BinNode<Stack<Integer>>(s2));
        pushAll(t, 0);

        // ורוקנים את המחסנית של המחסניות למחסנית אחת
        Stack<Integer> all = flatten(stk);
        System.out.println("אחרי שיטוח: " + all);
    }
}

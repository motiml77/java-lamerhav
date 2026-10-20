package examples;

import unit4.collectionsLib.*;

/** דוגמאות מחסנית: היפוך, הכנסה לתחתית (רקורסיה), ומיון */
public class StackExamples {

    /** מחזירה מחסנית חדשה הפוכה. המחסנית המקורית נשארת כמו שהייתה */
    public static Stack<Integer> reverse(Stack<Integer> s) {
        Stack<Integer> temp = new Stack<Integer>();
        Stack<Integer> result = new Stack<Integer>();
        while (!s.isEmpty()) {
            int x = s.pop();
            result.push(x);
            temp.push(x);
        }
        while (!temp.isEmpty()) {
            s.push(temp.pop());
        }
        return result;
    }

    /** הכנסת x לתחתית המחסנית — ברקורסיה (שימו לב למחסנית הקריאות בחלון!) */
    public static void pushBottom(Stack<Integer> s, int x) {
        if (s.isEmpty()) {
            s.push(x);
            return;
        }
        int top = s.pop();
        pushBottom(s, x);
        s.push(top);
    }

    public static void main(String[] args) {
        Stack<Integer> s = new Stack<Integer>();
        s.push(1);
        s.push(2);
        s.push(3);
        s.push(4);   // האחרון שנכנס — בראש המחסנית
        Stack<Integer> r = reverse(s);
        System.out.println("הפוכה: " + r);

        Viz.note("עכשיו: הכנסה לתחתית ברקורסיה");
        pushBottom(s, 99);
        System.out.println("אחרי pushBottom: " + s);
    }
}

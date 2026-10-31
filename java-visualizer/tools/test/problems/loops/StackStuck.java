package loops;

import unit4.collectionsLib.*;

public class StackStuck {
    // כמה איברים במחסנית — עם טעות: top במקום pop (לא מוציאים)
    public static int count(Stack<Integer> s) {
        int c = 0;
        while (!s.isEmpty()) {
            int x = s.top();
            c++;
        }
        return c;
    }

    public static void main(String[] args) {
        Stack<Integer> s = new Stack<Integer>();
        s.push(4);
        s.push(7);
        s.push(1);
        System.out.println(count(s));
    }
}

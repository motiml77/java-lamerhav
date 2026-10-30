package bugs;

import unit4.collectionsLib.*;

public class EmptyPop {
    // סכום המחסנית — עם טעות: לולאה אחת יותר מדי
    public static int sum(Stack<Integer> s) {
        int total = 0;
        for (int i = 0; i <= 3; i++) {
            total += s.pop();
        }
        return total;
    }

    public static void main(String[] args) {
        Stack<Integer> s = new Stack<Integer>();
        s.push(4);
        s.push(7);
        s.push(1);
        System.out.println(sum(s));
    }
}

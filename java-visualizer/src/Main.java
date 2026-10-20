import unit4.collectionsLib.*;

public class Main {

    // דוגמה: סכום האיברים במחסנית — בלי לשנות את המחסנית המקורית
    public static int sum(Stack<Integer> stk) {
        Stack<Integer> tmp = new Stack<Integer>();   // מחסנית עזר: שומרת את האיברים כדי להחזיר אותם בסוף
        int total = 0;                               // כאן מצטבר הסכום
        while (!stk.isEmpty()) {
            int x = stk.pop();                       // האיבר שהוצאנו עכשיו
            total += x;
            tmp.push(x);
        }
        while (!tmp.isEmpty()) {                     // מחזירים את האיברים (בסדר ההפוך — לכן בסדר המקורי)
            stk.push(tmp.pop());
        }
        return total;
    }

    public static void main(String[] args) {
        Stack<Integer> stk = new Stack<Integer>();
        stk.push(4);
        stk.push(7);
        stk.push(1);
        stk.push(9);   // האחרון שנכנס — בראש המחסנית

        System.out.println("הסכום: " + sum(stk));
        System.out.println("המחסנית אחרי: " + stk);
    }
}

import unit4.collectionsLib.*;

public class PromoStack {

    // סכום האיברים במחסנית — בלי להרוס אותה
    public static int sum(Stack<Integer> stk) {
        Stack<Integer> tmp = new Stack<Integer>();   // מחסנית עזר
        int total = 0;
        while (!stk.isEmpty()) {
            int x = stk.pop();
            total = total + x;
            tmp.push(x);
        }
        while (!tmp.isEmpty()) {
            stk.push(tmp.pop());                     // מחזירים למקום
        }
        return total;
    }

    public static void main(String[] args) {
        Stack<Integer> stk = new Stack<Integer>();
        stk.push(4);
        stk.push(7);
        stk.push(1);
        stk.push(9);
        System.out.println("הסכום: " + sum(stk));
    }
}

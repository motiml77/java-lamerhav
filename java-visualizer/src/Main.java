import unit4.collectionsLib.*;

public class Main {

    // דוגמה: סכום האיברים במחסנית — בלי לשנות את המחסנית המקורית
    public static int sum(Stack<Integer> s) {
        Stack<Integer> temp = new Stack<Integer>();
        int total = 0;
        while (!s.isEmpty()) {
            int x = s.pop();
            total += x;
            temp.push(x);
        }
        while (!temp.isEmpty()) {
            s.push(temp.pop());
        }
        return total;
    }

    public static void main(String[] args) {
        Stack<Integer> s = new Stack<Integer>();
        s.push(4);
        s.push(7);
        s.push(1);
        s.push(9);   // האחרון שנכנס — בראש המחסנית

        System.out.println("הסכום: " + sum(s));
        System.out.println("המחסנית אחרי: " + s);
    }
}

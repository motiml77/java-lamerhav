import unit4.collectionsLib.*;

public class Targil {
    /** כמה מספרים זוגיים במחסנית (המחסנית חוזרת למצבה) */
    public static int countEven(Stack<Integer> s) {
        int count = 0;
        Stack<Integer> temp = new Stack<Integer>();
        while (!s.isEmpty()) {
            int x = s.pop();
            if (x % 2 == 0) count++;
            temp.push(x);
        }
        while (!temp.isEmpty()) s.push(temp.pop());
        return count;
    }

    public static void main(String[] args) {
        Stack<Integer> s = new Stack<Integer>();
        s.push(4);
        System.out.println(countEven(s));
    }
}

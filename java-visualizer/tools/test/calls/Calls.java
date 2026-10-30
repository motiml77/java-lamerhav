import unit4.collectionsLib.*;

// בדיקה לתצוגת היציאה לפעולת עזר והחזרה ממנה (לא מ-main): עם ערך, void, ופעולת עזר שקוראת לפעולת עזר
public class Calls {
    static int rem(int x, int d) {
        return x % d;
    }

    static boolean isEven(int x) {
        return rem(x, 2) == 0;
    }

    static int bigger(int a, int b) {
        if (a > b) {
            return a;
        }
        return b;
    }

    static void report(String title, int value) {
        System.out.println(title + ": " + value);
    }

    static int sumEven(Stack<Integer> s) {
        int sum = 0;
        Stack<Integer> tmp = new Stack<Integer>();
        while (!s.isEmpty()) {
            int v = s.pop();
            if (isEven(v)) {
                sum += v;
            }
            tmp.push(v);
        }
        while (!tmp.isEmpty()) {
            s.push(tmp.pop());
        }
        return sum;
    }

    static int maxOf(Queue<Integer> q) {
        int m = q.remove();
        while (!q.isEmpty()) {
            m = bigger(m, q.remove());
        }
        report("max", m);
        return m;
    }

    public static void main(String[] args) {
        Stack<Integer> s = new Stack<Integer>();
        s.push(3);
        s.push(8);
        int total = sumEven(s);
        Queue<Integer> q = new Queue<Integer>();
        q.insert(4);
        q.insert(9);
        System.out.println(total + " " + maxOf(q));
    }
}

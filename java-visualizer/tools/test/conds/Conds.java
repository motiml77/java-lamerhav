import unit4.collectionsLib.*;

// בדיקה לתצוגת התנאים: if / else / else if, בלי סוגריים, return בשורה אחת, while / for / do-while, ותנאי עם פעולה
public class Conds {
    static String kind(int x) {
        if (x < 0) {
            return "negative";
        } else if (x == 0) {
            return "zero";
        } else {
            return "positive";
        }
    }

    static int fact(int n) {
        if (n <= 1) return 1;
        return n * fact(n - 1);
    }

    public static void main(String[] args) {
        int x = 7;
        int evens = 0, odds = 0;
        if (x % 2 == 0) {
            evens++;
            System.out.println("even");
        } else {
            odds++;
            System.out.println("odd");
        }
        if (x > 5)
            x = x - 5;
        else
            x = x + 5;
        Stack<Integer> s = new Stack<Integer>();
        s.push(3);
        s.push(8);
        int sum = 0;
        while (!s.isEmpty())
        {
            int v = s.pop();
            if (v > 5) sum += v;
            sum++;
        }
        for (int i = 0; i < 2; i++) {
            sum += i;
        }
        int k = 3;
        do {
            k--;
        } while (k > 1);
        System.out.println(kind(-2) + " " + kind(0) + " " + kind(4) + " " + fact(3) + " " + sum + " " + k);
    }
}

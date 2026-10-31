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
        System.out.println(kind(-2) + " " + kind(0) + " " + kind(4) + " " + fact(3) + " " + sum + " " + k + " " + grade(75) + grade(10) + " " + parts());
    }

    static String grade(int g) {
        if (g >= 90) {
            return "A";
        } else if (g >= 80) {
            return "B";
        } else if (g >= 70) {
            return "C";
        } else {
            return "F";
        }
    }

    // תנאים מורכבים (&&, ||, !, סוגריים): כל חלק עם הערכים שלו, קיצור (short-circuit), ו"?" כשאי אפשר לחשב
    static String parts() {
        int x = 7, y = 3;
        String s = "abc";
        StringBuilder out = new StringBuilder();
        if (x > 5 && y > 4) out.append("1");           // && — החלק השני נכשל
        if (x > 5 || y > 4) out.append("2");            // || — החלק הראשון מספיק, השני לא נבדק
        if (x % 2 == 0 && y != 0 || s.length() == 3) out.append("3");   // קדימות: && לפני ||
        if (!(x < 0) && (y == 3 || y == 9)) out.append("4");            // ! וסוגריים
        if (y > 4 && x / (y - 3) > 1) out.append("5");  // קיצור: נמנעים מחילוק באפס
        Stack<Integer> st = new Stack<Integer>();
        st.push(2);
        st.push(5);
        while (!st.isEmpty() && st.top() > 0) {
            st.pop();
        }
        if (s.indexOf("z") >= 0 || y > 1) out.append("6");   // s.indexOf — אי אפשר לחשב כאן (מוצג "?")
        return out.toString();
    }
}

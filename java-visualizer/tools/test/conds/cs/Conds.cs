using System;
using Unit4.CollectionsLib;

// בדיקה לתצוגת התנאים ב-C#: if / else / else if, בלי סוגריים, return בשורה אחת, while / for / do-while, ותנאי עם פעולה
public class Conds {
    static string Kind(int x) {
        if (x < 0) {
            return "negative";
        } else if (x == 0) {
            return "zero";
        } else {
            return "positive";
        }
    }

    static int Fact(int n) {
        if (n <= 1) return 1;
        return n * Fact(n - 1);
    }

    public static void Main(string[] args) {
        int x = 7;
        int evens = 0, odds = 0;
        if (x % 2 == 0) {
            evens++;
            Console.WriteLine("even");
        } else {
            odds++;
            Console.WriteLine("odd");
        }
        if (x > 5)
            x = x - 5;
        else
            x = x + 5;
        Stack<int> s = new Stack<int>();
        s.Push(3);
        s.Push(8);
        int sum = 0;
        while (!s.IsEmpty())
        {
            int v = s.Pop();
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
        Console.WriteLine(Kind(-2) + " " + Kind(0) + " " + Kind(4) + " " + Fact(3) + " " + sum + " " + k + " " + Grade(75) + Grade(10) + " " + Parts());
    }

    static string Grade(int g) {
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
    static string Parts() {
        int x = 7, y = 3;
        string s = "abc";
        string outp = "";
        if (x > 5 && y > 4) outp += "1";           // && — החלק השני נכשל
        if (x > 5 || y > 4) outp += "2";            // || — החלק הראשון מספיק, השני לא נבדק
        if (x % 2 == 0 && y != 0 || s.Length == 3) outp += "3";   // קדימות: && לפני ||, ו-.Length
        if (!(x < 0) && (y == 3 || y == 9)) outp += "4";          // ! וסוגריים
        if (y > 4 && x / (y - 3) > 1) outp += "5";  // קיצור: נמנעים מחילוק באפס
        Stack<int> st = new Stack<int>();
        st.Push(2);
        st.Push(5);
        while (!st.IsEmpty() && st.Top() > 0) {
            st.Pop();
        }
        if (s.IndexOf("z") >= 0 || y > 1) outp += "6";   // s.IndexOf — אי אפשר לחשב כאן (מוצג "?")
        return outp;
    }
}

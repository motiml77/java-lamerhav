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
        Console.WriteLine(Kind(-2) + " " + Kind(0) + " " + Kind(4) + " " + Fact(3) + " " + sum + " " + k);
    }
}

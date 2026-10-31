using System;
using Unit4.CollectionsLib;

// חץ "חוזרים לראש הלולאה" ב-C#: for, while (סוגריים בשורה נפרדת, וקריאה לפעולת עזר בגוף), do-while,
// מקוננות (כשהפנימית נגמרת — החץ של החיצונית), foreach, continue, ו-for בשורה אחת (בלי חץ)
public class LoopBack {
    static int Twice(int x) {
        return x * 2;
    }

    static int Run(int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            sum += i;
        }
        int k = n;
        while (k > 0)
        {
            sum += Twice(k);
            k--;
        }
        int d = 2;
        do {
            d--;
        } while (d > 0);
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                sum += a * b;
            }
        }
        int[] arr = { 4, 5 };
        foreach (int x in arr) {
            if (x > 4) {
                sum += x;
            }
        }
        for (int c = 0; c < 3; c++) {
            if (c == 1) continue;
            sum += c;
        }
        for (int t = 0; t < 2; t++) sum += t;
        return sum;
    }

    public static void Main(string[] args) {
        Console.WriteLine(Run(2));
    }
}

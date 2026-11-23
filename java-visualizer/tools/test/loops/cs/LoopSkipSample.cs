using System;
using Unit4.CollectionsLib;

// "הלולאה לא רצה אף לא פעם אחת" ב-C#: while (סוגריים בשורה נפרדת), for, foreach על מערך ריק, לולאה פנימית שלא רצה בסיבוב אחד,
// ולולאה בתוך פעולה שנקראת פעמיים. לא מסומנים: לולאה שרצה, do-while, ולולאה שהגוף שלה באותה שורה
public class LoopSkipSample {
    static int Count(int n) {
        int c = 0;
        for (int i = 0; i < n; i++) {
            c++;
        }
        return c;
    }

    static int Run(int n) {
        int sum = 0;
        int k = 0;
        while (k > 5)
        {
            sum += k;
            k--;
        }
        for (int i = 0; i < n; i++) {
            sum += i;
        }
        int[] empty = new int[0];
        foreach (int x in empty) {
            sum += x;
        }
        for (int a = 0; a < 3; a++) {
            for (int b = 0; b < a; b++) {
                sum += b;
            }
        }
        int d = 0;
        do {
            d++;
        } while (d < 0);
        for (int t = 0; t < 2; t++) {
            sum += t;
        }
        for (int u = 5; u < 3; u++) sum += u;
        sum += Count(0) + Count(2);
        return sum;
    }

    public static void Main(string[] args) {
        Console.WriteLine(Run(0));
    }
}

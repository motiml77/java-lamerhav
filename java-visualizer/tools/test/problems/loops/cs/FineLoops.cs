using System;
using Unit4.CollectionsLib;

// לולאות תקינות שנראות "חשודות" — אף אחת מהן לא נעצרת; וגם לולאה ארוכה (סכום 1..20000 — אחרי שהתיעוד מלא)
public class FineLoops {
    private int count;

    private void Tick() {
        count++;
    }

    public static void Main(string[] args) {
        // while (true) עם break
        int i = 0;
        while (true) {
            i++;
            if (i == 100) break;
        }
        // מאפיין שמשתנה דרך פעולה (לא בתוך הגוף עצמו)
        FineLoops f = new FineLoops();
        while (f.count < 50) {
            f.Tick();
        }
        // המשתנה שבתנאי משתנה רק לפעמים
        int found = 0, k = 0;
        while (found < 3) {
            k++;
            if (k % 50 == 0) found++;
        }
        // לולאות מקוננות (הפנימית מתחילה מחדש)
        int pairs = 0;
        for (int a = 0; a < 8; a++) {
            for (int b = 0; b < 8; b++) {
                pairs++;
            }
        }
        // רשימה: p מתקדם
        Node<int> lst = new Node<int>(1, new Node<int>(2, new Node<int>(3)));
        int sum = 0;
        for (Node<int> p = lst; p != null; p = p.GetNext()) sum += p.GetValue();
        // לולאה ארוכה אבל תקינה
        long big = 0;
        int j = 1;
        while (j <= 20000) {
            big = big + j;
            j++;
        }
        Console.WriteLine(i + " " + f.count + " " + k + " " + pairs + " " + sum + " " + big);
    }
}

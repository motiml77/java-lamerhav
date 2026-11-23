// "הלולאה לא רצה אף לא פעם אחת": while ו-for שהתנאי שלהם לא מתקיים, for-each על מערך ריק, לולאה פנימית שלא רצה בסיבוב אחד של החיצונית,
// ולולאה בתוך פעולה שנקראת פעמיים. לא מסומנים: לולאה שרצה, do-while (תמיד רץ פעם אחת), ולולאה שהגוף שלה באותה שורה
public class LoopSkipSample {
    static int count(int n) {
        int c = 0;
        for (int i = 0; i < n; i++) {
            c++;
        }
        return c;
    }

    static int run(int n) {
        int sum = 0;
        int k = 0;
        while (k > 5) {
            sum += k;
            k--;
        }
        for (int i = 0; i < n; i++) {
            sum += i;
        }
        int[] empty = new int[0];
        for (int x : empty) {
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
        sum += count(0) + count(2);
        return sum;
    }

    public static void main(String[] args) {
        System.out.println(run(0));
    }
}

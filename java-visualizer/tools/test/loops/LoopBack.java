// חץ "חוזרים לראש הלולאה": for, while (עם קריאה לפעולת עזר בגוף), do-while, מקוננות (כשהפנימית נגמרת — החץ של החיצונית),
// for-each, continue, ו-for בשורה אחת (בלי חץ)
public class LoopBack {
    static int twice(int x) {
        return x * 2;
    }

    static int run(int n) {
        int sum = 0;
        for (int i = 0; i < n; i++) {
            sum += i;
        }
        int k = n;
        while (k > 0) {
            sum += twice(k);
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
        for (int x : arr) {
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

    public static void main(String[] args) {
        System.out.println(run(2));
    }
}

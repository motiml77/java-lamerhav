package loops;

// לולאות לבדיקה: for, מקוננות (גם משולשת), while, do-while, for-each
public class Main {
    public static void main(String[] args) {
        int sum = 0;
        for (int i = 0; i < 4; i++) {
            sum += i;
        }
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < i; j++) {
                sum += j;
            }
        }
        int n = 20;
        while (n > 1) {
            n = n / 2;
        }
        int k = 3;
        do {
            k--;
        } while (k > 0);
        int[] arr = { 5, 6 };
        for (int x : arr) {
            sum += x;
        }
        for (int t = 10; t > 0; t -= 5) sum += t;
        System.out.println(sum);
    }
}

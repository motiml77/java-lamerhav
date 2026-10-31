package loops;

public class CounterStuck {
    // סכום 5 + 4 + 3 + 2 + 1 — עם טעות: שכחו להקטין את x
    public static void main(String[] args) {
        int x = 5, sum = 0;
        while (x > 0) {
            sum = sum + x;
        }
        System.out.println(sum);
    }
}

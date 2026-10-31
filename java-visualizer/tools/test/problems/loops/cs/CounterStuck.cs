using System;

// סכום 5 + 4 + 3 + 2 + 1 — עם טעות: שכחו להקטין את x
public class CounterStuck {
    public static void Main(string[] args) {
        int x = 5, sum = 0;
        while (x > 0) {
            sum = sum + x;
        }
        Console.WriteLine(sum);
    }
}

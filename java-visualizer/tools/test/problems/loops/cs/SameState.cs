using System;

// הדפסה מ-n עד 1 — עם טעות: שכחו n-- (אותו מצב חוזר בכל סיבוב)
public class SameState {
    public static void CountDown(int n) {
        while (n >= 1) {
            Console.WriteLine(n);
        }
    }

    public static void Main(string[] args) {
        CountDown(3);
    }
}

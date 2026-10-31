package loops;

public class SameState {
    // הדפסה מ-n עד 1 — עם טעות: שכחו n-- (אותו מצב חוזר בכל סיבוב)
    public static void countDown(int n) {
        while (n >= 1) {
            System.out.println(n);
        }
    }

    public static void main(String[] args) {
        countDown(3);
    }
}

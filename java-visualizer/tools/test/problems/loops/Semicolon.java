package loops;

public class Semicolon {
    // ספירה לאחור — עם טעות: ; מיותרת אחרי התנאי (הגוף של הלולאה ריק)
    public static void main(String[] args) {
        int n = 3;
        while (n > 0); {
            System.out.println(n);
            n--;
        }
    }
}

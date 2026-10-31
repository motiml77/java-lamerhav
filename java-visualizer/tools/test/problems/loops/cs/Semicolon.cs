using System;

// ספירה לאחור — עם טעות: ; מיותרת אחרי התנאי (הגוף של הלולאה ריק)
public class Semicolon {
    public static void Main(string[] args) {
        int n = 3;
        while (n > 0); {
            Console.WriteLine(n);
            n--;
        }
    }
}

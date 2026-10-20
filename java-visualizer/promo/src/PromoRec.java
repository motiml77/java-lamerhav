import unit4.collectionsLib.*;

public class PromoRec {

    // תרגיל ברקורסיה: עצרת  n! = n * (n-1)!
    public static int fact(int n) {
        if (n <= 1) {
            return 1;                  // מקרה בסיס
        }
        int rest = fact(n - 1);        // קריאה רקורסיבית
        return n * rest;
    }

    public static void main(String[] args) {
        int f = fact(4);
        System.out.println("4! = " + f);
    }
}

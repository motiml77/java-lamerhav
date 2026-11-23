// תנאי עצירה שגוי: sum(5) יורד 5, 3, 1, -1, ... ומדלג על 0 — הרקורסיה לא נעצרת (StackOverflowError).
// ההרצה הוויזואלית צריכה להזהיר כבר בזמן הירידה, לא רק אחרי שהתוכנית מתרסקת
public class BadBase {
    static int sum(int n) {
        if (n == 0) return 0;
        return n + sum(n - 2);
    }

    public static void main(String[] args) {
        System.out.println(sum(5));
    }
}

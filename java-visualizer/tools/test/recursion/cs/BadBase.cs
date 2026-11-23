using System;
using Unit4.CollectionsLib;

// תנאי עצירה שגוי ב-C#: Sum(5) יורד 5, 3, 1, -1, ... ומדלג על 0 — הרקורסיה לא נעצרת.
// ההרצה הוויזואלית צריכה להזהיר כבר בזמן הירידה
public class BadBase {
    static int Sum(int n) {
        if (n == 0) return 0;
        return n + Sum(n - 2);
    }

    public static void Main(string[] args) {
        Console.WriteLine(Sum(5));
    }
}

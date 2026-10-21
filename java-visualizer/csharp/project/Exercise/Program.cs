using System;
using Unit4.CollectionsLib;

class Program
{
    // תרגיל: כותבים פעולה שמקבלת מחסנית stk ומחזירה את סכום האיברים שבה (המחסנית צריכה להישאר כמו שהייתה)
    static int Sum(Stack<int> stk)
    {
        // ========== TODO: כאן כותבים את הפתרון ==========

        return 0;
    }

    // ==================== מכאן והלאה: מעטפת מוכנה — לא צריך לשנות ====================
    static void Main(string[] args)
    {
        Stack<int> stk = new Stack<int>();
        stk.Push(4);
        stk.Push(7);
        stk.Push(1);
        stk.Push(9);   // האחרון שנכנס — בראש המחסנית

        Console.WriteLine("הסכום: " + Sum(stk));
        Console.WriteLine("המחסנית אחרי: " + stk);
    }
}

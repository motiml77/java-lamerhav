using System;
using Unit4.CollectionsLib;

// דוגמאות לרקורסיה: עצרת, סכום 1..n, והדפסה מהסוף להתחלה
class Program
{
    // עצרת: n! = n * (n-1)!
    static int Fact(int n)
    {
        if (n <= 1)
        {
            return 1;                  // מקרה בסיס — כאן הרקורסיה נעצרת
        }
        int rest = Fact(n - 1);        // rest = התשובה של הבעיה הקטנה יותר (n-1)
        return n * rest;               // מרכיבים מזה את התשובה לבעיה הגדולה
    }

    // סכום המספרים 1..n
    static int SumTo(int n)
    {
        if (n == 0)
        {
            return 0;
        }
        int rest = SumTo(n - 1);
        return n + rest;
    }

    // מדפיס את האיברים מהסוף להתחלה — בלי להרוס את המחסנית
    static void PrintBottomUp(Stack<int> stk)
    {
        if (stk.IsEmpty())
        {
            return;
        }
        int x = stk.Pop();             // מוציאים את הראש
        PrintBottomUp(stk);            // קודם מטפלים בשאר המחסנית (הקריאה הרקורסיבית)
        Console.WriteLine(x);          // ורק אחרי שחזרנו — מדפיסים
        stk.Push(x);                   // ומחזירים למקום
    }

    static void Main(string[] args)
    {
        Console.WriteLine("4! = " + Fact(4));
        Console.WriteLine("סכום 1..5 = " + SumTo(5));

        Stack<int> stk = new Stack<int>();
        stk.Push(3);
        stk.Push(2);
        stk.Push(1);
        PrintBottomUp(stk);
    }
}

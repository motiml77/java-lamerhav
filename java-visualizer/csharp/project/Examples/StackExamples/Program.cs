using System;
using Unit4.CollectionsLib;

// דוגמאות מחסנית: סכום בלי להרוס, ומציאת המקסימום
class Program
{
    // סכום האיברים — המחסנית חוזרת למצבה המקורי
    static int Sum(Stack<int> stk)
    {
        Stack<int> tmp = new Stack<int>();   // מחסנית עזר: שומרת את האיברים כדי להחזיר אותם
        int total = 0;                       // כאן מצטבר הסכום
        while (!stk.IsEmpty())
        {
            int x = stk.Pop();
            total = total + x;
            tmp.Push(x);
        }
        while (!tmp.IsEmpty())
        {
            stk.Push(tmp.Pop());             // מחזירים למקום
        }
        return total;
    }

    // האיבר הגדול ביותר במחסנית (לא ריקה)
    static int Max(Stack<int> stk)
    {
        Stack<int> tmp = new Stack<int>();
        int max = stk.Top();
        while (!stk.IsEmpty())
        {
            int x = stk.Pop();
            if (x > max)
            {
                max = x;
            }
            tmp.Push(x);
        }
        while (!tmp.IsEmpty())
        {
            stk.Push(tmp.Pop());
        }
        return max;
    }

    static void Main(string[] args)
    {
        Stack<int> stk = new Stack<int>();
        stk.Push(4);
        stk.Push(7);
        stk.Push(1);
        stk.Push(9);   // האחרון שנכנס — בראש המחסנית

        Console.WriteLine("הסכום: " + Sum(stk));
        Console.WriteLine("המקסימום: " + Max(stk));
        Console.WriteLine("המחסנית אחרי: " + stk);
    }
}

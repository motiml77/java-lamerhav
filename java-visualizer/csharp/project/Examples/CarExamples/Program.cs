using System;
using Unit4.CollectionsLib;

// דוגמה עם מכוניות: כמה מכוניות ישנות יש במחסנית
class Program
{
    // כמה מכוניות ישנות (10 שנים ומעלה) במחסנית — המחסנית חוזרת למצבה
    static int CountOld(Stack<Car> stk)
    {
        Stack<Car> tmp = new Stack<Car>();
        int count = 0;
        while (!stk.IsEmpty())
        {
            Car c1 = stk.Pop();
            if (c1.IsOld())
            {
                count = count + 1;
            }
            tmp.Push(c1);
        }
        while (!tmp.IsEmpty())
        {
            stk.Push(tmp.Pop());
        }
        return count;
    }

    static void Main(string[] args)
    {
        Stack<Car> stk = new Stack<Car>();
        stk.Push(new Car("Mazda", "3", 2012));
        stk.Push(new Car("Kia", "Picanto", 2022));
        stk.Push(new Car("Toyota", "Corolla", 2008));

        Console.WriteLine("מכוניות ישנות: " + CountOld(stk));
    }
}

using System;
using Unit4.CollectionsLib;

// כמה איברים במחסנית — עם טעות: Top במקום Pop (לא מוציאים)
public class StackStuck {
    public static int Count(Stack<int> s) {
        int c = 0;
        while (!s.IsEmpty()) {
            int x = s.Top();
            c++;
        }
        return c;
    }

    public static void Main(string[] args) {
        Stack<int> s = new Stack<int>();
        s.Push(4);
        s.Push(7);
        s.Push(1);
        Console.WriteLine(Count(s));
    }
}

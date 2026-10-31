using System;
using Unit4.CollectionsLib;

// קלט מהמקלדת: Console.ReadLine עם int.Parse, קריאה בלולאה, ושורת טקסט בעברית
public class InputSum
{
    public static void Main(string[] args)
    {
        Console.Write("כמה מספרים? ");
        int n = int.Parse(Console.ReadLine());
        Stack<int> s = new Stack<int>();
        for (int i = 0; i < n; i++)
        {
            Console.Write("מספר " + (i + 1) + ": ");
            s.Push(int.Parse(Console.ReadLine()));
        }
        Console.Write("מה השם שלך? ");
        string name = Console.ReadLine();
        int sum = 0;
        while (!s.IsEmpty())
        {
            sum += s.Pop();
        }
        Console.WriteLine("שלום " + name + ", הסכום: " + sum);
    }
}

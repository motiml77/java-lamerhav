using System;

// רקורסיה כפולה
public class Program
{
    static int Fib(int n)
    {
        if (n < 2) return n;
        return Fib(n - 1) + Fib(n - 2);
    }

    public static void Main(string[] args)
    {
        Console.WriteLine(Fib(4));
    }
}

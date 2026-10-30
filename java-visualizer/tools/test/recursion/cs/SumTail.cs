using System;

// רקורסיית זנב
public class Program
{
    static int Sum(int n, int acc)
    {
        if (n == 0) return acc;
        return Sum(n - 1, acc + n);
    }

    public static void Main(string[] args)
    {
        Console.WriteLine(Sum(4, 0));
    }
}

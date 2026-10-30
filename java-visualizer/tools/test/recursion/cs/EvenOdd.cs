using System;

// רקורסיה הדדית
public class Program
{
    static bool IsEven(int n)
    {
        if (n == 0) return true;
        return IsOdd(n - 1);
    }

    static bool IsOdd(int n)
    {
        if (n == 0) return false;
        return IsEven(n - 1);
    }

    public static void Main(string[] args)
    {
        Console.WriteLine(IsEven(3));
    }
}

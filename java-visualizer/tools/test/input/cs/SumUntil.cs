using System;

// קלט עד שנגמר: while ((line = Console.ReadLine()) != null) — בחלון "סיום הקלט", בבדיקה סוף הקלט
public class SumUntil
{
    static int ReadSum()
    {
        int sum = 0;
        Console.WriteLine("הקלידו מספרים:");
        string line;
        while ((line = Console.ReadLine()) != null)
        {
            sum += int.Parse(line);
        }
        return sum;
    }

    public static void Main(string[] args)
    {
        int s = ReadSum();
        Console.WriteLine("סכום: " + s);
    }
}

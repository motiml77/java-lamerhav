using System;

// קלט בלי ReadLine: תו אחד ב-Console.Read(), ושורה ב-Console.In.ReadLine (גם בעברית)
public class ReadChars
{
    public static void Main(string[] args)
    {
        Console.Write("אות באנגלית: ");
        int c = Console.Read();
        Console.In.ReadLine();
        Console.Write("עיר: ");
        string city = Console.In.ReadLine();
        Console.WriteLine("קיבלתי " + (char)c + " ו-" + city + " (" + city.Length + " תווים)");
    }
}

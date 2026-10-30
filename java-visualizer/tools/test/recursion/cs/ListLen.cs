using System;
using Unit4.CollectionsLib;

// רקורסיה על שרשרת חוליות
public class Program
{
    static int Length(Node<int> p)
    {
        if (p == null) return 0;
        return 1 + Length(p.GetNext());
    }

    public static void Main(string[] args)
    {
        Node<int> list = new Node<int>(3, new Node<int>(7, new Node<int>(1)));
        Console.WriteLine(Length(list));
    }
}

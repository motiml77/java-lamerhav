using System;
using Unit4.CollectionsLib;

// דוגמאות עצים ברקורסיה: מספר צמתים, גובה, הכנסה לעץ חיפוש
class Program
{
    // כמה צמתים בעץ
    static int Count(BinNode<int> t)
    {
        if (t == null) return 0;                            // מקרה בסיס: עץ ריק — אפס צמתים
        return 1 + Count(t.GetLeft()) + Count(t.GetRight()); // הצומת הזה + כל מה שבתת-העץ השמאלי והימני
    }

    // גובה העץ (עלה בודד = 0)
    static int Height(BinNode<int> t)
    {
        if (t == null) return -1;                           // מקרה בסיס: עץ ריק
        return 1 + Math.Max(Height(t.GetLeft()), Height(t.GetRight()));   // הצד הגבוה יותר + צעד אחד
    }

    // הכנסה לעץ חיפוש בינארי
    static void Insert(BinNode<int> t, int x)
    {
        if (x < t.GetValue())
        {
            if (t.HasLeft()) Insert(t.GetLeft(), x);
            else t.SetLeft(new BinNode<int>(x));
        }
        else
        {
            if (t.HasRight()) Insert(t.GetRight(), x);
            else t.SetRight(new BinNode<int>(x));
        }
    }

    static void Main(string[] args)
    {
        BinNode<int> t = new BinNode<int>(5);   // השורש
        t.SetLeft(new BinNode<int>(3));
        t.SetRight(new BinNode<int>(8));
        t.GetLeft().SetLeft(new BinNode<int>(1));
        t.GetLeft().SetRight(new BinNode<int>(4));
        t.GetRight().SetRight(new BinNode<int>(9));

        Console.WriteLine("מספר צמתים: " + Count(t));
        Console.WriteLine("גובה: " + Height(t));
        Insert(t, 7);
        Insert(t, 2);
    }
}

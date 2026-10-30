using System;
using Unit4.CollectionsLib;

// Helpers.Size / Helpers.Duplicate: לא משנים את המבנה שנשלח, ומעתיקים גם מבנים פנימיים
public class HelpersCheck
{
    static void Check(bool ok, string what)
    {
        if (!ok) throw new Exception("FAIL " + what);
        Console.WriteLine("  ok  " + what);
    }

    public static void Main(string[] args)
    {
        Stack<int> s = new Stack<int>();
        s.Push(1); s.Push(2); s.Push(3);
        Check(Helpers.Size(s) == 3 && s.ToString() == "[3,2,1]", "Size של מחסנית, והמחסנית לא השתנתה");
        Stack<int> sc = Helpers.Duplicate(s);
        sc.Pop();
        Check(s.ToString() == "[3,2,1]" && sc.ToString() == "[2,1]", "Duplicate של מחסנית, ושינוי בעותק לא משנה את המקור");
        Queue<Stack<int>> qs = new Queue<Stack<int>>();
        Stack<int> in1 = new Stack<int>(); in1.Push(7); in1.Push(8);
        qs.Insert(in1);
        Queue<Stack<int>> qc = Helpers.Duplicate(qs);
        qc.Head().Pop();
        Check(Helpers.Size(qs) == 1 && in1.ToString() == "[8,7]", "תור של מחסניות: המחסנית הפנימית הועתקה");
        Node<string> lst = new Node<string>("a", new Node<string>("b"));
        Node<string> lc = Helpers.Duplicate(lst);
        lc.SetValue("X");
        Check(Helpers.Size(lst) == 2 && lst.GetValue() == "a", "Duplicate של רשימה");
        BinNode<int> t = new BinNode<int>(new BinNode<int>(1), 2, null);
        Check(Helpers.Size(t) == 2 && Helpers.Size(Helpers.Duplicate(t)) == 2, "Size ו-Duplicate של עץ");
        Console.WriteLine("MARK-END");
    }
}

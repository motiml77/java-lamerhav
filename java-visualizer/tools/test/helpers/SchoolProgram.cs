// תוכנית "של בית הספר" לבדיקה שההרצה הוויזואלית מדפיסה בדיוק מה שההרצה הרגילה מדפיסה, כשהפרויקט מפנה ל-Unit4.dll האמיתי של בתי הספר:
// ההרצה הרגילה בונה עם Unit4.dll, ההרצה הוויזואלית — עם הספרייה של DSViz. משתמשת בכל ה-API של הספרייה: Stack / Queue / Node / BinNode,
// StackInt ו-BinTreeUtils, וגם הדפסה ישירה של חוליה (שם ToString מחזיר רק את ערך החוליה). הפלט דטרמיניסטי (גם מהעץ האקראי מדפיסים רק מה שקבוע).
using System;
using Unit4.CollectionsLib;
using Unit4.BinTreeUtilsLib;

namespace SchoolApp
{
    class Program
    {
        static void Main(string[] args)
        {
            Node<int> lst = new Node<int>(4, new Node<int>(8, new Node<int>(2)));
            Console.WriteLine("node: " + lst + " | next: " + lst.GetNext() + " | has: " + lst.HasNext());
            Node<string> words = new Node<string>("שלום", new Node<string>("עולם"));
            Console.WriteLine(words);
            StackInt si = new StackInt();
            Console.WriteLine("stackint empty: " + si + " " + si.IsEmpty());
            si.Push(1); si.Push(5); si.Push(3);
            Console.WriteLine("stackint: " + si + " top=" + si.Top() + " pop=" + si.Pop() + " after=" + si);
            Stack<int> s = new Stack<int>();
            s.Push(7); s.Push(9);
            Queue<string> q = new Queue<string>();
            q.Insert("a"); q.Insert("b c");
            Stack<Queue<string>> sq = new Stack<Queue<string>>();
            sq.Push(q);
            Queue<double> qd = new Queue<double>();
            qd.Insert(1.5); qd.Insert(2);
            Console.WriteLine(s + " " + q + " " + sq + " " + qd + " head=" + q.Head() + " remove=" + q.Remove() + " left=" + q);
            BinNode<int> t = new BinNode<int>(new BinNode<int>(new BinNode<int>(0), 1, null), 2, new BinNode<int>(null, 3, new BinNode<int>(4)));
            Console.WriteLine("tree: " + t + " left=" + t.GetLeft() + " h=" + BinTreeUtils.Height(t) + " n=" + BinTreeUtils.NumOfNodes(t)
                + " leaves=" + BinTreeUtils.NumOfLeaves(t) + " full=" + BinTreeUtils.IsFull(t) + " valid=" + BinTreeUtils.TreeIsValid(t));
            BinNode<int> perfect = new BinNode<int>(new BinNode<int>(1), 2, new BinNode<int>(3));
            Console.WriteLine("perfect full=" + BinTreeUtils.IsFull(perfect) + " leaf full=" + BinTreeUtils.IsFull(new BinNode<int>(5)) + " leaf h=" + BinTreeUtils.Height(new BinNode<int>(5)));
            Console.WriteLine("pre=" + Text(BinTreeUtils.PreOrder2Nodes(t)) + " in=" + Text(BinTreeUtils.InOrder2Nodes(t))
                + " post=" + Text(BinTreeUtils.PostOrder2Nodes(t)) + " level=" + Text(BinTreeUtils.LevelOrder2Nodes(t)));
            Console.WriteLine("exists3=" + BinTreeUtils.Exists(t, 3) + " exists7=" + BinTreeUtils.Exists(t, 7)
                + " parent(4)=" + BinTreeUtils.Parent(t, t.GetRight().GetRight()) + " parent(root)=" + (BinTreeUtils.Parent(t, t) == null));
            BinNode<int> c = BinTreeUtils.Clone(t);
            Console.WriteLine("clone=" + Text(BinTreeUtils.PreOrder2Nodes(c)) + " same=" + (c == t));
            BinNode<int> shared = new BinNode<int>(1);
            Console.WriteLine("shared valid=" + BinTreeUtils.TreeIsValid(new BinNode<int>(shared, 0, shared)));
            BinNode<int> r = BinTreeUtils.BuildRandomTree(7, 3, 5);
            bool inRange = true;
            for (Node<int> p = BinTreeUtils.LevelOrder2Nodes(r); p != null; p = p.GetNext()) inRange = inRange && p.GetValue() >= 3 && p.GetValue() <= 5;
            Console.WriteLine("random n=" + BinTreeUtils.NumOfNodes(r) + " range=" + inRange + " valid=" + BinTreeUtils.TreeIsValid(r));
        }

        static string Text(Node<int> p)
        {
            string s = "";
            while (p != null) { s += p.GetValue() + (p.HasNext() ? "," : ""); p = p.GetNext(); }
            return s;
        }
    }
}

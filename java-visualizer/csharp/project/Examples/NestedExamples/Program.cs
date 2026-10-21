using System;
using Unit4.CollectionsLib;

// מבנה בתוך מבנה: מחסנית של מחסניות, רשימה של תורים, עץ של מחסניות...
// בחלון ההרצה כל מבנה פנימי מצויר בתוך התא של המבנה החיצוני.
class Program
{
    // סכום כל המספרים במחסנית של מחסניות — שני המבנים חוזרים למצבם המקורי
    static int SumAll(Stack<Stack<int>> stk)
    {
        Stack<Stack<int>> tmp = new Stack<Stack<int>>();   // עזר: שומר את המחסניות הפנימיות
        int sum = 0;                                       // כאן סופרים
        while (!stk.IsEmpty())
        {
            Stack<int> inner = stk.Pop();                  // מחסנית פנימית אחת
            Stack<int> innerTmp = new Stack<int>();        // עזר לפנימית
            while (!inner.IsEmpty())
            {
                int x = inner.Pop();
                sum += x;
                innerTmp.Push(x);
            }
            while (!innerTmp.IsEmpty())
            {
                inner.Push(innerTmp.Pop());                // מחזירים את הפנימית כמו שהייתה
            }
            tmp.Push(inner);
        }
        while (!tmp.IsEmpty())
        {
            stk.Push(tmp.Pop());
        }
        return sum;
    }

    // מרוקנת את המחסנית של המחסניות לתוך מחסנית אחת של מספרים
    static Stack<int> Flatten(Stack<Stack<int>> stk)
    {
        Stack<int> all = new Stack<int>();
        while (!stk.IsEmpty())
        {
            Stack<int> inner = stk.Pop();
            while (!inner.IsEmpty())
            {
                all.Push(inner.Pop());
            }
        }
        return all;
    }

    // כמה מספרים יש בסך הכול ברשימה מקושרת של תורים (התורים חוזרים למצבם)
    static int TotalSize(Node<Queue<int>> lst)
    {
        int total = 0;
        Node<Queue<int>> curr = lst;                       // מצביע שעובר על הרשימה
        while (curr != null)
        {
            Queue<int> q = curr.GetValue();                // התור שבחוליה הנוכחית
            Queue<int> tmp = new Queue<int>();
            while (!q.IsEmpty())
            {
                tmp.Insert(q.Remove());
                total++;
            }
            while (!tmp.IsEmpty())
            {
                q.Insert(tmp.Remove());
            }
            curr = curr.GetNext();
        }
        return total;
    }

    // מכניסה את x לראש כל המחסניות שבעץ (רקורסיה על העץ)
    static void PushAll(BinNode<Stack<int>> t, int x)
    {
        if (t == null) return;                             // מקרה בסיס
        t.GetValue().Push(x);                              // הערך בצומת הוא מחסנית שלמה
        PushAll(t.GetLeft(), x);
        PushAll(t.GetRight(), x);
    }

    static void Main(string[] args)
    {
        // מחסנית של מחסניות
        Stack<Stack<int>> stk = new Stack<Stack<int>>();
        Stack<int> inner = new Stack<int>();
        inner.Push(5);
        inner.Push(2);
        stk.Push(inner);
        inner = new Stack<int>();
        inner.Push(8);
        inner.Push(1);
        inner.Push(9);
        stk.Push(inner);
        Console.WriteLine("סכום: " + SumAll(stk));

        // רשימה מקושרת של תורים
        Queue<int> q1 = new Queue<int>();
        q1.Insert(3);
        q1.Insert(7);
        Queue<int> q2 = new Queue<int>();
        q2.Insert(4);
        Node<Queue<int>> lst = new Node<Queue<int>>(q2);
        lst = new Node<Queue<int>>(q1, lst);
        Console.WriteLine("כמה מספרים בתורים: " + TotalSize(lst));

        // עץ של מחסניות
        Stack<int> s1 = new Stack<int>();
        s1.Push(1);
        Stack<int> s2 = new Stack<int>();
        s2.Push(2);
        s2.Push(3);
        BinNode<Stack<int>> t = new BinNode<Stack<int>>(s1);
        t.SetLeft(new BinNode<Stack<int>>(s2));
        PushAll(t, 0);

        // ורוקנים את המחסנית של המחסניות למחסנית אחת
        Stack<int> all = Flatten(stk);
        Console.WriteLine("אחרי שיטוח: " + all);
    }
}

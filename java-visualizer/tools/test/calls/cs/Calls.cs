using System;
using Unit4.CollectionsLib;

// בדיקה לתצוגת היציאה לפעולת עזר והחזרה ממנה ב-C# (לא מ-Main): עם ערך, void, ופעולת עזר שקוראת לפעולת עזר
public class Calls {
    static int Rem(int x, int d) {
        return x % d;
    }

    static bool IsEven(int x) {
        return Rem(x, 2) == 0;
    }

    static int Bigger(int a, int b) {
        if (a > b) {
            return a;
        }
        return b;
    }

    static void Report(string title, int value) {
        Console.WriteLine(title + ": " + value);
    }

    static int SumEven(Stack<int> s) {
        int sum = 0;
        Stack<int> tmp = new Stack<int>();
        while (!s.IsEmpty()) {
            int v = s.Pop();
            if (IsEven(v)) {
                sum += v;
            }
            tmp.Push(v);
        }
        while (!tmp.IsEmpty()) {
            s.Push(tmp.Pop());
        }
        return sum;
    }

    static int MaxOf(Queue<int> q) {
        int m = q.Remove();
        while (!q.IsEmpty()) {
            m = Bigger(m, q.Remove());
        }
        Report("max", m);
        return m;
    }

    public static void Main(string[] args) {
        Stack<int> s = new Stack<int>();
        s.Push(3);
        s.Push(8);
        int total = SumEven(s);
        Queue<int> q = new Queue<int>();
        q.Insert(4);
        q.Insert(9);
        Console.WriteLine(total + " " + MaxOf(q));
    }
}

// ספרייה "של בית הספר" לבדיקות: namespace Unit4.CollectionsLib ב-assembly בשם Unit4 — כמו הספרייה שבתי הספר מחלקים (Unit4.dll).
// ה-API כמו בספריית הבגרות, ככל שידוע לנו: Node<T>, BinNode<T>, Stack<T>, Queue<T> עם אותם שמות פעולות ואותם בנאים.
// משמש את tools/test/cs-tests.sh (סעיף 10 — הבנייה הרגילה מול הספרייה הזו, וההשוואה חבר-חבר לספרייה של DSViz: ApiCompare.cs)
// ואת tools/test/windows-vs-matrix/fixtures.ps1 (Unit4.dll לפרויקטים ב-Visual Studio). C# 5 (המהדר של ‎.NET Framework שבכל Windows).
namespace Unit4.CollectionsLib
{
    public class Node<T>
    {
        private T value;
        private Node<T> next;

        public Node(T value) { this.value = value; this.next = null; }
        public Node(T value, Node<T> next) { this.value = value; this.next = next; }
        public T GetValue() { return value; }
        public Node<T> GetNext() { return next; }
        public bool HasNext() { return next != null; }
        public void SetValue(T value) { this.value = value; }
        public void SetNext(Node<T> next) { this.next = next; }
        public override string ToString() { return value + " --> " + next; }
    }

    public class BinNode<T>
    {
        private BinNode<T> left;
        private T value;
        private BinNode<T> right;

        public BinNode(T value) { this.left = null; this.value = value; this.right = null; }
        public BinNode(BinNode<T> left, T value, BinNode<T> right) { this.left = left; this.value = value; this.right = right; }
        public T GetValue() { return value; }
        public BinNode<T> GetLeft() { return left; }
        public BinNode<T> GetRight() { return right; }
        public bool HasLeft() { return left != null; }
        public bool HasRight() { return right != null; }
        public void SetValue(T value) { this.value = value; }
        public void SetLeft(BinNode<T> left) { this.left = left; }
        public void SetRight(BinNode<T> right) { this.right = right; }
        public override string ToString() { return "(" + left + " " + value + " " + right + ")"; }
    }

    public class Stack<T>
    {
        private Node<T> head;

        public Stack() { head = null; }
        public bool IsEmpty() { return head == null; }
        public void Push(T x) { head = new Node<T>(x, head); }
        public T Pop() { T x = head.GetValue(); head = head.GetNext(); return x; }
        public T Top() { return head.GetValue(); }
        public override string ToString()
        {
            string s = "[";
            for (Node<T> p = head; p != null; p = p.GetNext()) s += p.GetValue() + (p.HasNext() ? "," : "");
            return s + "]";
        }
    }

    public class Queue<T>
    {
        private Node<T> first;
        private Node<T> last;

        public Queue() { first = null; last = null; }
        public bool IsEmpty() { return first == null; }
        public void Insert(T x)
        {
            Node<T> n = new Node<T>(x);
            if (first == null) first = n; else last.SetNext(n);
            last = n;
        }
        public T Remove() { T x = first.GetValue(); first = first.GetNext(); if (first == null) last = null; return x; }
        public T Head() { return first.GetValue(); }
        public override string ToString()
        {
            string s = "[";
            for (Node<T> p = first; p != null; p = p.GetNext()) s += p.GetValue() + (p.HasNext() ? "," : "");
            return s + "]";
        }
    }
}

using System;

namespace Unit4.CollectionsLib
{
    /// <summary>
    /// פעולות עזר שמקבלות מבנה ומחזירות תשובה — בלי לשנות את המבנה שנשלח. עם using static בראש הקובץ
    /// (התוסף מוסיף אותו לבד) קוראים להן ישירות:
    ///   using static Unit4.CollectionsLib.Helpers;
    ///   int n = Size(s);                  // כמה איברים במחסנית / בתור / ברשימה / בעץ
    ///   Stack&lt;int&gt; copy = Duplicate(s);   // עותק באותו סדר
    /// גם במבנה בתוך מבנה (תור של מחסניות...): Size סופר את האיברים של המבנה החיצוני,
    /// ו-Duplicate מעתיק גם את המבנים הפנימיים. בהרצה הוויזואלית כל קריאה היא צעד אחד.
    /// </summary>
    public static class Helpers
    {
        /// <summary>כמה איברים יש במחסנית — המחסנית נשארת כמו שהייתה</summary>
        public static int Size<T>(Stack<T> s)
        {
            int n = s.items.Count;
            Recorder.Read(s, "Size(" + Recorder.NameOf(s) + ")  →  " + n);
            return n;
        }

        /// <summary>כמה איברים יש בתור — התור נשאר כמו שהיה</summary>
        public static int Size<T>(Queue<T> q)
        {
            int n = q.items.Count;
            Recorder.Read(q, "Size(" + Recorder.NameOf(q) + ")  →  " + n);
            return n;
        }

        /// <summary>כמה חוליות יש ברשימה — הרשימה נשארת כמו שהייתה. רשימה ריקה (null) — 0</summary>
        public static int Size<T>(Node<T> lst)
        {
            int n = 0;
            for (Node<T> p = lst; p != null; p = p.next) n++;
            if (lst != null) Recorder.Read(lst, "Size(" + Recorder.NodeRef(lst) + ")  →  " + n);
            return n;
        }

        /// <summary>כמה צמתים יש בעץ — העץ נשאר כמו שהיה. עץ ריק (null) — 0</summary>
        public static int Size<T>(BinNode<T> t)
        {
            int n = Count(t);
            if (t != null) Recorder.Read(t, "Size(" + Recorder.NodeRef(t) + ")  →  " + n);
            return n;
        }

        static int Count<T>(BinNode<T> t)
        {
            return t == null ? 0 : 1 + Count(t.left) + Count(t.right);
        }

        /// <summary>עותק של המחסנית, באותו סדר — המחסנית המקורית נשארת כמו שהייתה</summary>
        public static Stack<T> Duplicate<T>(Stack<T> s)
        {
            Stack<T> copy;
            Recorder.Quiet++;
            try
            {
                copy = new Stack<T>();
                foreach (T x in s.items) copy.items.Add(Deep(x));
            }
            finally { Recorder.Quiet--; }
            Recorder.Create(copy, "Duplicate(" + Recorder.NameOf(s) + ")  →  עותק חדש");
            return copy;
        }

        /// <summary>עותק של התור, באותו סדר — התור המקורי נשאר כמו שהיה</summary>
        public static Queue<T> Duplicate<T>(Queue<T> q)
        {
            Queue<T> copy;
            Recorder.Quiet++;
            try
            {
                copy = new Queue<T>();
                foreach (T x in q.items) copy.items.Add(Deep(x));
            }
            finally { Recorder.Quiet--; }
            Recorder.Create(copy, "Duplicate(" + Recorder.NameOf(q) + ")  →  עותק חדש");
            return copy;
        }

        /// <summary>עותק של הרשימה: חוליות חדשות באותו סדר — הרשימה המקורית נשארת כמו שהייתה. רשימה ריקה (null) — null</summary>
        public static Node<T> Duplicate<T>(Node<T> lst)
        {
            if (lst == null) return null;
            Node<T> first;
            Recorder.Quiet++;
            try { first = CopyList(lst); }
            finally { Recorder.Quiet--; }
            Recorder.Create(first, "Duplicate(" + Recorder.NodeRef(lst) + ")  →  רשימה חדשה");
            return first;
        }

        /// <summary>עותק של העץ: צמתים חדשים באותו מבנה — העץ המקורי נשאר כמו שהיה. עץ ריק (null) — null</summary>
        public static BinNode<T> Duplicate<T>(BinNode<T> t)
        {
            if (t == null) return null;
            BinNode<T> root;
            Recorder.Quiet++;
            try { root = CopyTree(t); }
            finally { Recorder.Quiet--; }
            Recorder.Create(root, "Duplicate(" + Recorder.NodeRef(t) + ")  →  עץ חדש");
            return root;
        }

        static Node<T> CopyList<T>(Node<T> lst)
        {
            Node<T> first = new Node<T>(Deep(lst.value)), last = first;
            for (Node<T> p = lst.next; p != null; p = p.next)
            {
                last.next = new Node<T>(Deep(p.value));
                last = last.next;
            }
            return first;
        }

        static BinNode<T> CopyTree<T>(BinNode<T> t)
        {
            if (t == null) return null;
            return new BinNode<T>(CopyTree(t.left), Deep(t.value), CopyTree(t.right));
        }

        /// <summary>איבר שהוא בעצמו מבנה (מחסנית בתוך תור...) — מועתק גם הוא; כל איבר אחר — כמו שהוא</summary>
        static T Deep<T>(T x)
        {
            if (x == null) return x;
            Type t = x.GetType();
            if (!t.IsGenericType) return x;
            Type g = t.GetGenericTypeDefinition();
            if (g != typeof(Stack<>) && g != typeof(Queue<>) && g != typeof(Node<>) && g != typeof(BinNode<>)) return x;
            // Duplicate המתאים לסוג הפנימי (Stack<int>, Queue<string>...) — דרך reflection, כי T כאן הוא הסוג החיצוני
            var m = typeof(Helpers).GetMethod(g == typeof(Stack<>) ? nameof(DeepStack) : g == typeof(Queue<>) ? nameof(DeepQueue)
                : g == typeof(Node<>) ? nameof(CopyList) : nameof(CopyTree),
                System.Reflection.BindingFlags.NonPublic | System.Reflection.BindingFlags.Static);
            return (T)m.MakeGenericMethod(t.GetGenericArguments()[0]).Invoke(null, new object[] { x });
        }

        static Stack<E> DeepStack<E>(Stack<E> s)
        {
            var c = new Stack<E>();
            foreach (E y in s.items) c.items.Add(Deep(y));
            return c;
        }

        static Queue<E> DeepQueue<E>(Queue<E> q)
        {
            var c = new Queue<E>();
            foreach (E y in q.items) c.items.Add(Deep(y));
            return c;
        }
    }
}

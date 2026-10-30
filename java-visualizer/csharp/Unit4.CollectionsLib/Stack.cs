using System;
using System.Collections.Generic;
using System.Text;

namespace Unit4.CollectionsLib
{
    /// <summary>
    /// מחסנית — כמו בספריית הבגרות (Unit4.CollectionsLib). בהרצה ויזואלית כל פעולה מתועדת ומוצגת בחלון.
    /// </summary>
    public class Stack<T> : IVizLinear
    {
        internal readonly List<T> items = new List<T>();   // התחתית ראשונה, הראש אחרון

        public Stack()
        {
            Recorder.Register(this, "Stack");
            Recorder.Create(this, "נוצרה מחסנית ריקה: " + Recorder.NameOf(this));
        }

        /// <summary>הכנסת x לראש המחסנית</summary>
        public void Push(T x)
        {
            items.Add(x);
            Recorder.Change(this, Mark.NEW, null, Recorder.NameOf(this) + ".Push(" + Recorder.Str(x) + ")");
        }

        /// <summary>הוצאת האיבר שבראש המחסנית והחזרתו</summary>
        public T Pop()
        {
            if (items.Count == 0)
                throw new InvalidOperationException("Pop() על מחסנית ריקה (" + Recorder.NameOf(this) + ")");
            T x = items[items.Count - 1];
            items.RemoveAt(items.Count - 1);
            Recorder.Change(this, Mark.NONE, Recorder.Str(x), Recorder.NameOf(this) + ".Pop()  →  " + Recorder.Str(x));
            return x;
        }

        /// <summary>החזרת האיבר שבראש המחסנית בלי להוציא אותו</summary>
        public T Top()
        {
            if (items.Count == 0)
                throw new InvalidOperationException("Top() על מחסנית ריקה (" + Recorder.NameOf(this) + ")");
            T x = items[items.Count - 1];
            Recorder.Read(this, Recorder.NameOf(this) + ".Top()  →  " + Recorder.Str(x));
            return x;
        }

        public bool IsEmpty()
        {
            bool e = items.Count == 0;
            Recorder.Check(this, Recorder.NameOf(this) + ".IsEmpty()  →  " + (e ? "true" : "false"));
            return e;
        }

        /// <summary>המחסנית כמחרוזת, הראש ראשון: [5,4,3]</summary>
        public override string ToString()
        {
            var b = new StringBuilder("[");
            for (int i = items.Count - 1; i >= 0; i--)
            {
                b.Append(items[i]);
                if (i > 0) b.Append(',');
            }
            return b.Append(']').ToString();
        }

        bool IVizLinear.IsStack { get { return true; } }

        IList<object> IVizLinear.VizItems
        {
            get
            {
                var l = new List<object>(items.Count);
                for (int i = items.Count - 1; i >= 0; i--) l.Add(items[i]);
                return l;
            }
        }
    }
}

using System;
using System.Collections.Generic;
using System.Text;

namespace Unit4.CollectionsLib
{
    /// <summary>
    /// תור — כמו בספריית הבגרות (Unit4.CollectionsLib). בהרצה ויזואלית כל פעולה מתועדת ומוצגת בחלון.
    /// </summary>
    public class Queue<T> : IVizLinear
    {
        internal readonly List<T> items = new List<T>();   // ראש התור ראשון

        public Queue()
        {
            Recorder.Register(this, "Queue");
            Recorder.Create(this, "נוצר תור ריק: " + Recorder.NameOf(this));
        }

        /// <summary>הכנסת x לסוף התור</summary>
        public void Insert(T x)
        {
            items.Add(x);
            Recorder.Change(this, Mark.NEW, null, Recorder.NameOf(this) + ".Insert(" + Recorder.Str(x) + ")");
        }

        /// <summary>הוצאת האיבר שבראש התור והחזרתו</summary>
        public T Remove()
        {
            if (items.Count == 0)
                throw new InvalidOperationException("Remove() על תור ריק (" + Recorder.NameOf(this) + ")");
            T x = items[0];
            items.RemoveAt(0);
            Recorder.Change(this, Mark.NONE, Recorder.Str(x), Recorder.NameOf(this) + ".Remove()  →  " + Recorder.Str(x));
            return x;
        }

        /// <summary>החזרת האיבר שבראש התור בלי להוציא אותו</summary>
        public T Head()
        {
            if (items.Count == 0)
                throw new InvalidOperationException("Head() על תור ריק (" + Recorder.NameOf(this) + ")");
            T x = items[0];
            Recorder.Read(this, Recorder.NameOf(this) + ".Head()  →  " + Recorder.Str(x));
            return x;
        }

        public bool IsEmpty()
        {
            bool e = items.Count == 0;
            Recorder.Check(this, Recorder.NameOf(this) + ".IsEmpty()  →  " + (e ? "true" : "false"));
            return e;
        }

        /// <summary>התור כמחרוזת, ראש התור ראשון: [1,2,3]</summary>
        public override string ToString()
        {
            var b = new StringBuilder("[");
            for (int i = 0; i < items.Count; i++)
            {
                if (i > 0) b.Append(',');
                b.Append(items[i]);
            }
            return b.Append(']').ToString();
        }

        bool IVizLinear.IsStack { get { return false; } }

        IList<object> IVizLinear.VizItems
        {
            get
            {
                var l = new List<object>(items.Count);
                foreach (T x in items) l.Add(x);
                return l;
            }
        }
    }
}

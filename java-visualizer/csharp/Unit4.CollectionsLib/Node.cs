using System.Collections.Generic;
using System.Text;

namespace Unit4.CollectionsLib
{
    /// <summary>
    /// חוליה ברשימה מקושרת — כמו בספריית הבגרות (Unit4.CollectionsLib).
    /// בהרצה ויזואלית כל פעולה מתועדת ומוצגת בחלון.
    /// </summary>
    public class Node<T> : IVizNode
    {
        internal T value;
        internal Node<T> next;

        public Node(T value)
        {
            this.value = value;
            Recorder.Register(this, "Node");
            Recorder.Create(this, "new Node(" + Recorder.Str(value) + ")");
        }

        public Node(T value, Node<T> next)
        {
            this.value = value;
            this.next = next;
            Recorder.Register(this, "Node");
            Recorder.Create(this, "new Node(" + Recorder.Str(value) + ", " + Recorder.NodeRef(next) + ")");
        }

        public T GetValue()
        {
            Recorder.Read(this, Ref() + ".GetValue()  →  " + Recorder.Str(value));
            return value;
        }

        public Node<T> GetNext()
        {
            Recorder.Read(this, Ref() + ".GetNext()  →  " + Recorder.NodeRef(next));
            return next;
        }

        public bool HasNext()
        {
            Recorder.Check(this, Ref() + ".HasNext()  →  " + (next != null ? "true" : "false"));
            return next != null;
        }

        public void SetValue(T value)
        {
            string old = Recorder.Str(this.value);
            this.value = value;
            Recorder.Change(this, Mark.CHANGED, null, "[" + old + "].SetValue(" + Recorder.Str(value) + ")");
        }

        public void SetNext(Node<T> next)
        {
            this.next = next;
            Recorder.Change(this, Mark.CHANGED, null, Ref() + ".SetNext(" + Recorder.NodeRef(next) + ")");
        }

        // ---- תאימות לגרסה הישנה של Unit4 (GetInfo / SetInfo)

        /// <summary>כמו GetValue — מהגרסה הישנה של Unit4</summary>
        public T GetInfo() { return GetValue(); }

        /// <summary>כמו SetValue — מהגרסה הישנה של Unit4</summary>
        public void SetInfo(T value) { SetValue(value); }

        string Ref() { return Recorder.NodeRef(this); }

        /// <summary>כל הרשימה מהחוליה הזו והלאה, למשל: 5 -> 2 -> 8 -> null</summary>
        public override string ToString()
        {
            var b = new StringBuilder();
            var seen = new HashSet<Node<T>>();
            Node<T> p = this;
            while (p != null && seen.Add(p))
            {
                b.Append(p.value).Append(" -> ");
                p = p.next;
            }
            return b.Append(p == null ? "null" : "(מעגל)").ToString();
        }

        object IVizNode.VizValue { get { return value; } }
        IVizNode IVizNode.VizNext { get { return next; } }
    }
}

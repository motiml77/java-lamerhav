using System.Text;

namespace Unit4.CollectionsLib
{
    /// <summary>
    /// צומת בעץ בינארי — כמו בספריית הבגרות (Unit4.CollectionsLib).
    /// בהרצה ויזואלית כל פעולה מתועדת ומוצגת בחלון.
    /// </summary>
    public class BinNode<T> : IVizBin
    {
        T value;
        BinNode<T> left, right;

        public BinNode(T value)
        {
            this.value = value;
            Recorder.Register(this, "BinNode");
            Recorder.Create(this, "new BinNode(" + Recorder.Str(value) + ")");
        }

        public BinNode(BinNode<T> left, T value, BinNode<T> right)
        {
            this.left = left;
            this.value = value;
            this.right = right;
            Recorder.Register(this, "BinNode");
            Recorder.Create(this, "new BinNode(" + Recorder.NodeRef(left) + ", " + Recorder.Str(value) + ", " + Recorder.NodeRef(right) + ")");
        }

        public T GetValue()
        {
            Recorder.Read(this, Ref() + ".GetValue()  →  " + Recorder.Str(value));
            return value;
        }

        public BinNode<T> GetLeft()
        {
            Recorder.Read(this, Ref() + ".GetLeft()  →  " + Recorder.NodeRef(left));
            return left;
        }

        public BinNode<T> GetRight()
        {
            Recorder.Read(this, Ref() + ".GetRight()  →  " + Recorder.NodeRef(right));
            return right;
        }

        public bool HasLeft()
        {
            Recorder.Check(this, Ref() + ".HasLeft()  →  " + (left != null ? "true" : "false"));
            return left != null;
        }

        public bool HasRight()
        {
            Recorder.Check(this, Ref() + ".HasRight()  →  " + (right != null ? "true" : "false"));
            return right != null;
        }

        public void SetValue(T value)
        {
            string old = Recorder.Str(this.value);
            this.value = value;
            Recorder.Change(this, Mark.CHANGED, null, "[" + old + "].SetValue(" + Recorder.Str(value) + ")");
        }

        public void SetLeft(BinNode<T> left)
        {
            this.left = left;
            Recorder.Change(this, Mark.CHANGED, null, Ref() + ".SetLeft(" + Recorder.NodeRef(left) + ")");
        }

        public void SetRight(BinNode<T> right)
        {
            this.right = right;
            Recorder.Change(this, Mark.CHANGED, null, Ref() + ".SetRight(" + Recorder.NodeRef(right) + ")");
        }

        // ---- תאימות לגרסה הישנה של Unit4 (GetInfo / SetInfo)
        public T GetInfo() { return GetValue(); }
        public void SetInfo(T value) { SetValue(value); }

        string Ref() { return Recorder.NodeRef(this); }

        public override string ToString() { return value == null ? "null" : value.ToString(); }

        object IVizBin.VizValue { get { return value; } }
        IVizBin IVizBin.VizLeft { get { return left; } }
        IVizBin IVizBin.VizRight { get { return right; } }
    }
}

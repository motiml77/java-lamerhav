using System;
using System.Collections.Generic;
using Unit4.CollectionsLib;

namespace Unit4.BinTreeUtilsLib
{
    /// <summary>
    /// פעולות שירות לעץ בינרי — כמו BinTreeUtils שבספרייה של בית הספר (Unit4.dll, ‏using Unit4.BinTreeUtilsLib;), באותם שמות ובאותה התנהגות:
    /// גובה של עלה הוא 0, IsFull — עץ מלא (כל העלים באותה רמה), BuildRandomTree בונה בדיוק maxNodes צמתים (לפחות אחד) עם ערכים בין low ל-high,
    /// ועץ ריק (null) — NullReferenceException, חוץ מ-TreeIsValid ו-Parent. בהרצה הוויזואלית כל קריאה היא צעד אחד.
    /// </summary>
    public static class BinTreeUtils
    {
        static readonly Random rnd = new Random();

        static void NotNull<T>(BinNode<T> tree, string op)
        {
            if (tree == null) throw new NullReferenceException("BinTreeUtils." + op + " קיבלה עץ ריק (null)");
        }

        static T Read<T>(object tree, string text, T result)
        {
            Recorder.Read(tree, "BinTreeUtils." + text + "  →  " + Recorder.Str(result));
            return result;
        }

        /// <summary>האם מבנה העץ חוקי: אין צומת שמגיעים אליו פעמיים (צומת משותף או מעגל). עץ ריק — חוקי</summary>
        public static bool TreeIsValid<T>(BinNode<T> tree)
        {
            var seen = new HashSet<BinNode<T>>();
            var todo = new System.Collections.Generic.Stack<BinNode<T>>();
            bool ok = true;
            if (tree != null) todo.Push(tree);
            while (ok && todo.Count > 0)
            {
                BinNode<T> p = todo.Pop();
                if (!seen.Add(p)) { ok = false; break; }
                if (p.left != null) todo.Push(p.left);
                if (p.right != null) todo.Push(p.right);
            }
            return tree == null ? ok : Read(tree, "TreeIsValid(" + Recorder.NodeRef(tree) + ")", ok);
        }

        /// <summary>עץ בינרי חדש עם maxNodes צמתים (לפחות אחד) וערכים אקראיים בין low ל-high (כולל)</summary>
        public static BinNode<int> BuildRandomTree(int maxNodes, int low, int high)
        {
            int n = Math.Max(1, maxNodes);
            BinNode<int> root;
            Recorder.Quiet++;
            try
            {
                root = new BinNode<int>(rnd.Next(low, high + 1));
                // כל צומת חדש: יורדים מהשורש, בכל צומת שמאלה או ימינה באקראי, עד מקום פנוי (כמו בספרייה של בית הספר — אותה התפלגות צורות)
                for (int i = 1; i < n; i++)
                {
                    BinNode<int> p = root;
                    while (true)
                    {
                        bool left = rnd.Next(2) == 0;
                        BinNode<int> c = left ? p.left : p.right;
                        if (c != null) { p = c; continue; }
                        c = new BinNode<int>(rnd.Next(low, high + 1));
                        if (left) p.left = c; else p.right = c;
                        break;
                    }
                }
            }
            finally { Recorder.Quiet--; }
            Recorder.Create(root, "BinTreeUtils.BuildRandomTree(" + maxNodes + ", " + low + ", " + high + ")  →  עץ חדש עם " + n + " צמתים");
            return root;
        }

        /// <summary>שרשרת חוליות עם ערכי העץ בסדר תחילי (שורש, שמאל, ימין)</summary>
        public static Node<T> PreOrder2Nodes<T>(BinNode<T> tree) { return Chain(tree, "PreOrder2Nodes", 0); }

        /// <summary>שרשרת חוליות עם ערכי העץ בסדר תוכי (שמאל, שורש, ימין)</summary>
        public static Node<T> InOrder2Nodes<T>(BinNode<T> tree) { return Chain(tree, "InOrder2Nodes", 1); }

        /// <summary>שרשרת חוליות עם ערכי העץ בסדר סופי (שמאל, ימין, שורש)</summary>
        public static Node<T> PostOrder2Nodes<T>(BinNode<T> tree) { return Chain(tree, "PostOrder2Nodes", 2); }

        /// <summary>שרשרת חוליות עם ערכי העץ לפי הרמות (משמאל לימין)</summary>
        public static Node<T> LevelOrder2Nodes<T>(BinNode<T> tree) { return Chain(tree, "LevelOrder2Nodes", 3); }

        static Node<T> Chain<T>(BinNode<T> tree, string op, int order)
        {
            NotNull(tree, op);
            var values = new List<T>();
            if (order == 3)
            {
                var q = new System.Collections.Generic.Queue<BinNode<T>>();
                q.Enqueue(tree);
                while (q.Count > 0)
                {
                    BinNode<T> p = q.Dequeue();
                    values.Add(p.value);
                    if (p.left != null) q.Enqueue(p.left);
                    if (p.right != null) q.Enqueue(p.right);
                }
            }
            else Walk(tree, order, values);
            Node<T> first = null;
            Recorder.Quiet++;
            try { for (int i = values.Count - 1; i >= 0; i--) first = new Node<T>(values[i], first); }
            finally { Recorder.Quiet--; }
            Recorder.Create(first, "BinTreeUtils." + op + "(" + Recorder.NodeRef(tree) + ")  →  שרשרת חדשה עם " + values.Count + " חוליות");
            return first;
        }

        static void Walk<T>(BinNode<T> t, int order, List<T> values)
        {
            if (t == null) return;
            if (order == 0) values.Add(t.value);
            Walk(t.left, order, values);
            if (order == 1) values.Add(t.value);
            Walk(t.right, order, values);
            if (order == 2) values.Add(t.value);
        }

        /// <summary>גובה העץ: עלה — 0</summary>
        public static int Height<T>(BinNode<T> tree)
        {
            NotNull(tree, "Height");
            return Read(tree, "Height(" + Recorder.NodeRef(tree) + ")", H(tree));
        }

        static int H<T>(BinNode<T> t) { return t == null ? -1 : 1 + Math.Max(H(t.left), H(t.right)); }

        static int Count<T>(BinNode<T> t) { return t == null ? 0 : 1 + Count(t.left) + Count(t.right); }

        static int Leaves<T>(BinNode<T> t) { return t == null ? 0 : t.left == null && t.right == null ? 1 : Leaves(t.left) + Leaves(t.right); }

        /// <summary>מספר הצמתים בעץ</summary>
        public static int NumOfNodes<T>(BinNode<T> tree)
        {
            NotNull(tree, "NumOfNodes");
            return Read(tree, "NumOfNodes(" + Recorder.NodeRef(tree) + ")", Count(tree));
        }

        /// <summary>מספר העלים בעץ</summary>
        public static int NumOfLeaves<T>(BinNode<T> tree)
        {
            NotNull(tree, "NumOfLeaves");
            return Read(tree, "NumOfLeaves(" + Recorder.NodeRef(tree) + ")", Leaves(tree));
        }

        /// <summary>האם העץ מלא: לכל צומת שאינו עלה יש שני ילדים, וכל העלים באותה רמה (עלה בודד — מלא)</summary>
        public static bool IsFull<T>(BinNode<T> tree)
        {
            NotNull(tree, "IsFull");
            int h = H(tree);
            bool full = h < 31 && Count(tree) == (1 << (h + 1)) - 1;
            return Read(tree, "IsFull(" + Recorder.NodeRef(tree) + ")", full);
        }

        /// <summary>עץ חדש שהוא העתק מדויק של העץ (צמתים חדשים, אותם ערכים, אותו מבנה)</summary>
        public static BinNode<T> Clone<T>(BinNode<T> tree)
        {
            NotNull(tree, "Clone");
            BinNode<T> copy;
            Recorder.Quiet++;
            try { copy = Copy(tree); }
            finally { Recorder.Quiet--; }
            Recorder.Create(copy, "BinTreeUtils.Clone(" + Recorder.NodeRef(tree) + ")  →  עץ חדש");
            return copy;
        }

        static BinNode<T> Copy<T>(BinNode<T> t) { return t == null ? null : new BinNode<T>(Copy(t.left), t.value, Copy(t.right)); }

        /// <summary>האם הערך x נמצא בעץ (השוואה עם Equals)</summary>
        public static bool Exists<T>(BinNode<T> tree, T x)
        {
            NotNull(tree, "Exists");
            return Read(tree, "Exists(" + Recorder.NodeRef(tree) + ", " + Recorder.Str(x) + ")", Find(tree, x));
        }

        static bool Find<T>(BinNode<T> t, T x)
        {
            return t != null && (EqualityComparer<T>.Default.Equals(t.value, x) || Find(t.left, x) || Find(t.right, x));
        }

        /// <summary>ההורה של הצומת child בעץ (אותו צומת עצמו, לא רק אותו ערך), או null — אם child הוא השורש או לא בעץ</summary>
        public static BinNode<T> Parent<T>(BinNode<T> tree, BinNode<T> child)
        {
            BinNode<T> p = Up(tree, child);
            if (tree != null) Recorder.Read(tree, "BinTreeUtils.Parent(" + Recorder.NodeRef(tree) + ", " + Recorder.NodeRef(child) + ")  →  " + Recorder.NodeRef(p));
            return p;
        }

        static BinNode<T> Up<T>(BinNode<T> t, BinNode<T> child)
        {
            if (t == null || child == null) return null;
            if (ReferenceEquals(t.left, child) || ReferenceEquals(t.right, child)) return t;
            return Up(t.left, child) ?? Up(t.right, child);
        }
    }
}

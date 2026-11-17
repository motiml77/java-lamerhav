using System;
using System.Collections.Generic;
using Unit4.BinTreeUtilsLib;
using Unit4.CollectionsLib;

namespace Unit4.BinTreeCanvasLib
{
    /// <summary>מהירות הציור — אותם ערכים כמו בספרייה של בית הספר (מילישניות בין צומת לצומת; StopAfterAllSteps — מקש אחרי כל צומת)</summary>
    public enum AnimationSpeed
    {
        StandardSpeedPlus3 = 0,
        StandardSpeedPlus2 = 50,
        StandardSpeedPlus1 = 100,
        StandardSpeed = 150,
        StandardSpeedMinus1 = 200,
        StandardSpeedMinus2 = 400,
        StandardSpeedMinus3 = 800,
        StopAfterAllSteps = -1
    }

    /// <summary>
    /// TreeCanvas של הספרייה של בית הספר (Unit4.dll, ‏using Unit4.BinTreeCanvasLib;): שם הוא פותח חלון ומצייר את העץ ואת סדר המעבר.
    /// בהרצה הוויזואלית העץ כבר מצויר בתצוגה, ולכן כאן אין חלון — אבל הפלט לקונסול זהה: עם PrintOutAllElements(true) כל TreeDraw…Order
    /// מדפיס את הערכים בסדר המעבר ("4, 2, 7"), בלי ירידת שורה (וב-StopAfterAllSteps — ירידת שורה בסוף, בלי לחכות למקשים).
    /// כך קוד שנכתב לספרייה של בית הספר רץ גם בהרצה הוויזואלית, עם אותו פלט.
    /// </summary>
    public static class TreeCanvas
    {
        static int delay = (int)AnimationSpeed.StandardSpeed;
        static bool printOut;
        static List<string> inOrder, preOrder, postOrder, levelOrder;

        public static void SetAnimastion(AnimationSpeed speed) { delay = (int)speed; }

        public static void PrintOutAllElements(bool printOut) { TreeCanvas.printOut = printOut; }

        /// <summary>העץ שמציירים. עץ ריק או לא חוקי (צומת משותף / מעגל) — אין מה לצייר, וה-TreeDraw…Order לא מדפיסים (כמו שם)</summary>
        public static void AddTree<T>(BinNode<T> tree)
        {
            inOrder = preOrder = postOrder = levelOrder = null;
            if (tree == null || !BinTreeUtils.TreeIsValid(tree)) { Recorder.Note("TreeCanvas: אין עץ לצייר"); return; }
            inOrder = new List<string>(); preOrder = new List<string>(); postOrder = new List<string>(); levelOrder = new List<string>();
            Walk(tree);
            var q = new System.Collections.Generic.Queue<BinNode<T>>();
            q.Enqueue(tree);
            while (q.Count > 0)
            {
                BinNode<T> n = q.Dequeue();
                levelOrder.Add(Text(n));
                if (n.GetLeft() != null) q.Enqueue(n.GetLeft());
                if (n.GetRight() != null) q.Enqueue(n.GetRight());
            }
            Recorder.Note("TreeCanvas: העץ נוסף לציור (בהרצה הרגילה הוא מצויר בחלון של בית הספר)");
        }

        static string Text<T>(BinNode<T> n) { return n.GetValue().ToString(); }

        static void Walk<T>(BinNode<T> n)
        {
            if (n == null) return;
            preOrder.Add(Text(n));
            Walk(n.GetLeft());
            inOrder.Add(Text(n));
            Walk(n.GetRight());
            postOrder.Add(Text(n));
        }

        public static void TreeDrawInOrder() { Draw(inOrder, "תוכי (In-order)"); }
        public static void TreeDrawPostOrder() { Draw(postOrder, "סופי (Post-order)"); }
        public static void TreeDrawPreOrder() { Draw(preOrder, "תחילי (Pre-order)"); }
        public static void TreeDrawLevelOrder() { Draw(levelOrder, "לפי רמות (Level-order)"); }

        static void Draw(List<string> order, string name)
        {
            if (order == null) return;   // לפני AddTree (או עץ ריק) — כמו שם: לא קורה כלום
            Recorder.Note("TreeCanvas: מעבר " + name + ": " + string.Join(", ", order));
            if (printOut) Console.Write(string.Join(", ", order));
            if (delay == -1) Console.WriteLine();
        }
    }
}

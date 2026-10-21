using System.Collections.Generic;

namespace Unit4.CollectionsLib
{
    /// <summary>סוגי הצעדים (כמו Model.Kind בגרסת ה-Java)</summary>
    internal enum Kind { CREATE, CHANGE, READ, CHECK, OUTPUT, NOTE, ERROR, END, LINE, RETURN }

    /// <summary>איך לצבוע איבר שהצעד נגע בו</summary>
    internal enum Mark { NONE, NEW, READ, CHANGED }

    internal sealed class Frame
    {
        public string Class, Method, File;
        public int Line;
        public List<KeyValuePair<string, object>> Vars = new List<KeyValuePair<string, object>>();
        public bool Returned, Faulted;
        public Frame Copy() { return new Frame { Class = Class, Method = Method, File = File, Line = Line }; }
    }

    internal sealed class Step
    {
        public Kind Kind;
        public string Text;
        public List<Frame> Calls = new List<Frame>();      // העליונה ראשונה
        public List<View> Views = new List<View>();
        public int OutLen;
        public List<string[]> Vars;                        // {שם, ערך}; null אם אין
        public string Returned;                            // RETURN: הערך שהוחזר (null = void)
    }

    internal abstract class View
    {
        public string Title;
        public bool Focus;
    }

    internal sealed class LinearView : View
    {
        public bool Stack;
        public readonly List<string> Items = new List<string>();   // מחסנית: הראש ראשון. תור: ראש התור ראשון
        public int MarkIndex = -1;
        public Mark Mark = Mark.NONE;
        public string Ghost;
    }

    internal class Cell
    {
        public string Value;
        public Mark Mark = Mark.NONE;
        public bool Current;
        public readonly List<string> Labels = new List<string>();
    }

    internal sealed class ChainView : View
    {
        public readonly List<Cell> Cells = new List<Cell>();
        public int CycleTo = -1;
        public string JoinText;
        public bool Truncated;
    }

    internal sealed class TreeCell : Cell
    {
        public TreeCell Left, Right;
        public bool Shared;
    }

    internal sealed class TreeView : View
    {
        public TreeCell Root;
    }

    internal sealed class ArrayView : View
    {
        public readonly List<string> Items = new List<string>();
        public readonly HashSet<int> Changed = new HashSet<int>();
        public readonly Dictionary<int, List<string>> IndexLabels = new Dictionary<int, List<string>>();
        public bool Truncated;
    }

    internal sealed class MatrixView : View
    {
        public readonly List<List<string>> Rows = new List<List<string>>();
        public readonly HashSet<string> Changed = new HashSet<string>();          // "שורה,עמודה"
        public readonly Dictionary<int, List<string>> RowLabels = new Dictionary<int, List<string>>();
        public readonly Dictionary<int, List<string>> ColLabels = new Dictionary<int, List<string>>();
    }

    /// <summary>הגישה הגנרית למבנים (בלי לדעת את T) — בשביל הצילום</summary>
    internal interface IVizLinear { bool IsStack { get; } IList<object> VizItems { get; } }
    internal interface IVizNode { object VizValue { get; } IVizNode VizNext { get; } }
    internal interface IVizBin { object VizValue { get; } IVizBin VizLeft { get; } IVizBin VizRight { get; } }
}

using System.Collections.Generic;
using System.IO;
using System.Text;
using Unit4.CollectionsLib;

namespace DsViz
{
    /// <summary>
    /// כותב את ההרצה לקובץ טקסט שהחלון (unit4.collectionsLib.TraceViewer ב-Java) קורא.
    /// שורה לרשומה, שדות מופרדים ב-TAB; בתוך שדה: \\ \t \n \r מוסתרים, ו-\0 = null.
    /// </summary>
    internal static class TraceWriter
    {
        public static void Write(string path)
        {
            using (var w = new StreamWriter(path, false, new UTF8Encoding(false)))
            {
                w.NewLine = "\n";
                Row(w, "DSVIZ", "1", "cs");
                foreach (var kv in Recorder.Sources) Row(w, "SRC", kv.Key, kv.Value);
                foreach (Step s in Recorder.Steps)
                {
                    Row(w, "STEP", s.Kind.ToString(), s.OutLen.ToString(), s.Returned ?? "\0", s.Text);
                    foreach (Frame f in s.Calls) Row(w, "FR", f.Class, f.Method, f.File ?? "", f.Line.ToString());
                    if (s.Vars != null)
                    {
                        Row(w, "VARS");
                        foreach (string[] v in s.Vars) Row(w, "VAR", v[0], v[1]);
                    }
                    foreach (View v in s.Views) WriteView(w, v);
                }
                Row(w, "OUT", Recorder.Output.ToString());
            }
        }

        static void WriteView(StreamWriter w, View v)
        {
            string focus = v.Focus ? "1" : "0";
            var lin = v as LinearView;
            if (lin != null)
            {
                Row(w, "V", "LIN", lin.Stack ? "1" : "0", focus, v.Title, lin.MarkIndex.ToString(), lin.Mark.ToString(), lin.Ghost ?? "\0");
                foreach (string it in lin.Items) Row(w, "I", it);
                return;
            }
            var chain = v as ChainView;
            if (chain != null)
            {
                Row(w, "V", "CHAIN", focus, v.Title, chain.CycleTo.ToString(), chain.JoinText ?? "\0", chain.Truncated ? "1" : "0");
                foreach (Cell c in chain.Cells) Row(w, "C", c.Value, c.Mark.ToString(), c.Current ? "1" : "0", string.Join("\u0001", c.Labels));
                return;
            }
            var tree = v as TreeView;
            if (tree != null)
            {
                Row(w, "V", "TREE", focus, v.Title);
                WriteTree(w, tree.Root);
                return;
            }
            var arr = v as ArrayView;
            if (arr != null)
            {
                Row(w, "V", "ARR", focus, v.Title, arr.Truncated ? "1" : "0");
                foreach (string it in arr.Items) Row(w, "I", it);
                foreach (int i in arr.Changed) Row(w, "CH", i.ToString());
                foreach (var kv in arr.IndexLabels) foreach (string n in kv.Value) Row(w, "IL", kv.Key.ToString(), n);
                return;
            }
            var mat = (MatrixView)v;
            Row(w, "V", "MAT", focus, v.Title);
            foreach (List<string> r in mat.Rows)
            {
                var f = new List<string> { "ROW" };
                if (r.Count == 0) f.Add("\0"); else f.AddRange(r);
                Row(w, f.ToArray());
            }
            foreach (string c in mat.Changed) Row(w, "CH", c);
            foreach (var kv in mat.RowLabels) foreach (string n in kv.Value) Row(w, "RL", kv.Key.ToString(), n);
            foreach (var kv in mat.ColLabels) foreach (string n in kv.Value) Row(w, "CL", kv.Key.ToString(), n);
        }

        static void WriteTree(StreamWriter w, TreeCell c)
        {
            if (c == null) { Row(w, "NULL"); return; }
            Row(w, "N", c.Value, c.Mark.ToString(), c.Current ? "1" : "0", c.Shared ? "1" : "0", string.Join("\u0001", c.Labels));
            if (c.Shared) { Row(w, "NULL"); Row(w, "NULL"); return; }   // צומת משותף — בלי ילדים (כמו ב-Java)
            WriteTree(w, c.Left);
            WriteTree(w, c.Right);
        }

        static void Row(StreamWriter w, params string[] fields)
        {
            for (int i = 0; i < fields.Length; i++)
            {
                if (i > 0) w.Write('\t');
                w.Write(Escape(fields[i]));
            }
            w.Write('\n');
        }

        static string Escape(string s)
        {
            if (s == "\0") return "\\0";
            var b = new StringBuilder(s.Length + 8);
            foreach (char c in s)
            {
                switch (c)
                {
                    case '\\': b.Append("\\\\"); break;
                    case '\t': b.Append("\\t"); break;
                    case '\n': b.Append("\\n"); break;
                    case '\r': b.Append("\\r"); break;
                    default: b.Append(c); break;
                }
            }
            return b.ToString();
        }
    }
}

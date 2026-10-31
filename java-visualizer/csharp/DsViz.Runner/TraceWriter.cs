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
                    if (s.Access != null)
                    {
                        Row(w, "ACC", s.Access.Field, s.Access.Text, s.Access.Mark.ToString());
                        WriteObj(w, s.Access.Obj);
                    }
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
                for (int i = 0; i < lin.Items.Count; i++)
                {
                    Row(w, "I", lin.Items[i]);
                    if (i < lin.Objs.Count) WriteObj(w, lin.Objs[i]);
                    if (i < lin.Subs.Count) WriteSub(w, lin.Subs[i]);
                }
                return;
            }
            var chain = v as ChainView;
            if (chain != null)
            {
                Row(w, "V", "CHAIN", focus, v.Title, chain.CycleTo.ToString(), chain.JoinText ?? "\0", chain.Truncated ? "1" : "0");
                WriteCells(w, chain);
                foreach (ChainView br in chain.Branches)   // BR joinTo, ואחריה התאים של הענף
                {
                    Row(w, "BR", br.JoinTo.ToString());
                    WriteCells(w, br);
                }
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
                for (int i = 0; i < arr.Items.Count; i++)
                {
                    Row(w, "I", arr.Items[i]);
                    if (i < arr.Objs.Count) WriteObj(w, arr.Objs[i]);
                    if (i < arr.Subs.Count) WriteSub(w, arr.Subs[i]);
                }
                foreach (int i in arr.Changed) Row(w, "CH", i.ToString());
                foreach (var kv in arr.IndexLabels) foreach (string n in kv.Value) Row(w, "IL", kv.Key.ToString(), n);
                return;
            }
            var ov = v as ObjView;
            if (ov != null)
            {
                Row(w, "V", "OBJ", focus, v.Title);
                WriteObj(w, ov.Obj);
                return;
            }
            var mat = (MatrixView)v;
            Row(w, "V", "MAT", focus, v.Title);
            for (int ri = 0; ri < mat.Rows.Count; ri++)
            {
                List<string> r = mat.Rows[ri];
                var f = new List<string> { "ROW" };
                if (r.Count == 0) f.Add("\0"); else f.AddRange(r);
                Row(w, f.ToArray());
                if (ri < mat.Objs.Count)
                    for (int ci = 0; ci < mat.Objs[ri].Count; ci++)
                        if (mat.Objs[ri][ci] != null) { Row(w, "OBJAT", ci.ToString()); WriteObj(w, mat.Objs[ri][ci]); }
            }
            foreach (string c in mat.Changed) Row(w, "CH", c);
            foreach (var kv in mat.RowLabels) foreach (string n in kv.Value) Row(w, "RL", kv.Key.ToString(), n);
            foreach (var kv in mat.ColLabels) foreach (string n in kv.Value) Row(w, "CL", kv.Key.ToString(), n);
        }

        static void WriteCells(StreamWriter w, ChainView chain)
        {
            foreach (Cell c in chain.Cells)
            {
                Row(w, "C", c.Value, c.Mark.ToString(), c.Current ? "1" : "0", string.Join("\u0001", c.Labels));
                WriteObj(w, c.Obj);
                WriteSub(w, c.Sub);
            }
        }

        static void WriteTree(StreamWriter w, TreeCell c)
        {
            if (c == null) { Row(w, "NULL"); return; }
            Row(w, "N", c.Value, c.Mark.ToString(), c.Current ? "1" : "0", c.Shared ? "1" : "0", string.Join("\u0001", c.Labels));
            WriteObj(w, c.Obj);
            WriteSub(w, c.Sub);
            if (c.Shared) { Row(w, "NULL"); Row(w, "NULL"); return; }   // צומת משותף — בלי ילדים (כמו ב-Java)
            WriteTree(w, c.Left);
            WriteTree(w, c.Right);
        }

        /// <summary>מבנה בתוך תא: SUB, שורות ה-V של המבנה הפנימי (רקורסיבי), ENDSUB</summary>
        static void WriteSub(StreamWriter w, View v)
        {
            if (v == null) return;
            Row(w, "SUB");
            WriteView(w, v);
            Row(w, "ENDSUB");
        }

        /// <summary>אובייקט של מחלקה: שורת O (זהות, סוג, כותרת, פרטים, תוויות) ואחריה שורת F לכל מאפיין</summary>
        static void WriteObj(StreamWriter w, Obj o)
        {
            if (o == null) return;
            Row(w, "O", o.Id.ToString(), o.Type, o.Title, o.Sub, string.Join("\u0001", o.Labels));
            foreach (string[] f in o.Fields) Row(w, "F", f[0], f[1]);
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
            if (s == null) return "";
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

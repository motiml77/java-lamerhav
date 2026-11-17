// משווה חבר-חבר את ה-API הציבורי של שתי ספריות ב-namespace Unit4.CollectionsLib, Unit4.BinTreeUtilsLib ו-Unit4.BinTreeCanvasLib (TreeCanvas): הספרייה של בית הספר (Unit4.dll — האמיתית,
// שבתי הספר מחלקים; ב-cs-tests היא מורדת ונבדקת מול sha256) מול הספרייה של DSViz (Unit4.CollectionsLib.dll). ההרצה הוויזואלית מקמפלת תמיד מול
// הספרייה של DSViz — גם קוד שנכתב לספרייה של בית הספר — ולכן כל חבר ציבורי שיש שם צריך להיות גם אצלנו (אותו שם, אותם פרמטרים, אותו סוג מוחזר;
// גם בירושה — StackInt שלנו יורשת מ-Stack<int>).
//   ApiCompare <Unit4.dll של בית הספר> <Unit4.CollectionsLib.dll של DSViz>
// מדפיס "both" / "only-school" / "only-dsviz" לכל חבר; קוד יציאה 1 אם יש חבר שקיים רק בספרייה של בית הספר.
using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;

static class ApiCompare
{
    static string Fmt(Type t)
    {
        if (t.IsGenericParameter) return t.Name;
        if (t.IsArray) return Fmt(t.GetElementType()) + "[]";
        if (t.IsGenericType) return t.Name.Split('`')[0] + "<" + string.Join(",", t.GetGenericArguments().Select(Fmt)) + ">";
        return t.Name;
    }

    /// <summary>"טביעת אצבע" של Unit4.dll (ApiCompare --snapshot): שורות "ns …", "type …", "api …" — במקום הקובץ עצמו, כשאין ממנו מקור להורדה</summary>
    static bool IsSnapshot(string path) { return path.EndsWith(".txt", StringComparison.OrdinalIgnoreCase); }
    static IEnumerable<string> SnapshotLines(string path, string prefix)
    {
        return System.IO.File.ReadAllLines(path).Where(l => l.StartsWith(prefix + " ", StringComparison.Ordinal)).Select(l => l.Substring(prefix.Length + 1));
    }

    static SortedSet<string> Members(string dll)
    {
        var set = new SortedSet<string>(StringComparer.Ordinal);
        if (IsSnapshot(dll)) { foreach (string l in SnapshotLines(dll, "api")) set.Add(l); return set; }
        Assembly a = Assembly.LoadFrom(dll);
        foreach (Type t in a.GetExportedTypes().Where(t => t.Namespace == "Unit4.CollectionsLib" || t.Namespace == "Unit4.BinTreeUtilsLib" || t.Namespace == "Unit4.BinTreeCanvasLib"))
        {
            string tn = Fmt(t);
            set.Add("type " + t.Namespace + "." + tn);
            const BindingFlags F = BindingFlags.Public | BindingFlags.Instance | BindingFlags.Static | BindingFlags.DeclaredOnly;
            foreach (ConstructorInfo c in t.GetConstructors(F))
                set.Add(tn + "(" + string.Join(", ", c.GetParameters().Select(p => Fmt(p.ParameterType))) + ")");
            // פעולות: גם בירושה (חוץ מאלה של object) — StackInt שלנו יורשת את Push / Pop / Top / IsEmpty / ToString מ-Stack<int>
            const BindingFlags M = BindingFlags.Public | BindingFlags.Instance | BindingFlags.Static | BindingFlags.FlattenHierarchy;
            foreach (MethodInfo m in t.GetMethods(M).Where(m => !m.IsSpecialName && m.DeclaringType.FullName != "System.Object"))
                set.Add((m.IsStatic ? "static " : "") + Fmt(m.ReturnType) + " " + tn + "." + m.Name
                    + (m.IsGenericMethod ? "<" + string.Join(",", m.GetGenericArguments().Select(Fmt)) + ">" : "")
                    + "(" + string.Join(", ", m.GetParameters().Select(p => Fmt(p.ParameterType))) + ")");
            foreach (PropertyInfo p in t.GetProperties(F))
                set.Add(Fmt(p.PropertyType) + " " + tn + "." + p.Name + " { " + (p.CanRead ? "get; " : "") + (p.CanWrite ? "set; " : "") + "}");
        }
        return set;
    }

    /// <summary>
    /// ApiCompare --types <א> <ב>: כל סוג ציבורי ב-א שאין ב-ב (לפי namespace ושם), שורה לכל סוג — "Unit4.GridLib.Grid".
    /// ApiCompare --namespaces <א>: כל ה-namespaces של הסוגים הציבוריים ב-א.
    /// </summary>
    static IEnumerable<string> TypeNames(string dll)
    {
        if (IsSnapshot(dll)) return SnapshotLines(dll, "type");
        return Assembly.LoadFrom(dll).GetExportedTypes().Where(t => !t.IsNested && t.Namespace != null).Select(t => t.Namespace + "." + t.Name.Split('`')[0]);
    }

    static int Main(string[] args)
    {
        if (args[0] == "--types")
        {
            var mine = new HashSet<string>(TypeNames(args[2]));
            foreach (string t in TypeNames(args[1]).Where(t => !mine.Contains(t)).Distinct().OrderBy(x => x, StringComparer.Ordinal)) Console.WriteLine(t);
            return 0;
        }
        if (args[0] == "--snapshot")
        {
            foreach (string n in TypeNames(args[1]).Select(t => t.Substring(0, t.LastIndexOf('.'))).Distinct().OrderBy(x => x, StringComparer.Ordinal)) Console.WriteLine("ns " + n);
            foreach (string t in TypeNames(args[1]).Distinct().OrderBy(x => x, StringComparer.Ordinal)) Console.WriteLine("type " + t);
            foreach (string m in Members(args[1])) Console.WriteLine("api " + m);
            return 0;
        }
        if (args[0] == "--namespaces")
        {
            foreach (string n in TypeNames(args[1]).Select(t => t.Substring(0, t.LastIndexOf('.'))).Distinct().OrderBy(x => x, StringComparer.Ordinal)) Console.WriteLine(n);
            return 0;
        }
        SortedSet<string> school = Members(args[0]), ours = Members(args[1]);
        int missing = 0;
        foreach (string m in school.Union(ours).OrderBy(x => x, StringComparer.Ordinal))
        {
            bool s = school.Contains(m), o = ours.Contains(m);
            if (s && !o) missing++;
            Console.WriteLine((s && o ? "both        " : s ? "only-school " : "only-dsviz  ") + m);
        }
        return missing == 0 ? 0 : 1;
    }
}

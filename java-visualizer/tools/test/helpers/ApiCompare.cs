// משווה חבר-חבר את ה-API הציבורי של שתי ספריות ב-namespace Unit4.CollectionsLib: הספרייה "של בית הספר" (SchoolUnit4.cs → Unit4.dll)
// מול הספרייה של DSViz (Unit4.CollectionsLib.dll). ההרצה הוויזואלית מקמפלת תמיד מול הספרייה של DSViz — גם קוד שנכתב לספרייה של
// בית הספר — ולכן כל חבר ציבורי שיש שם צריך להיות גם אצלנו (אותו שם, אותם פרמטרים, אותו סוג מוחזר).
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

    static SortedSet<string> Members(string dll)
    {
        var set = new SortedSet<string>(StringComparer.Ordinal);
        Assembly a = Assembly.LoadFrom(dll);
        foreach (Type t in a.GetExportedTypes().Where(t => t.Namespace == "Unit4.CollectionsLib"))
        {
            string tn = Fmt(t);
            set.Add("type " + tn);
            const BindingFlags F = BindingFlags.Public | BindingFlags.Instance | BindingFlags.Static | BindingFlags.DeclaredOnly;
            foreach (ConstructorInfo c in t.GetConstructors(F))
                set.Add(tn + "(" + string.Join(", ", c.GetParameters().Select(p => Fmt(p.ParameterType))) + ")");
            foreach (MethodInfo m in t.GetMethods(F).Where(m => !m.IsSpecialName))
                set.Add((m.IsStatic ? "static " : "") + Fmt(m.ReturnType) + " " + tn + "." + m.Name
                    + (m.IsGenericMethod ? "<" + string.Join(",", m.GetGenericArguments().Select(Fmt)) + ">" : "")
                    + "(" + string.Join(", ", m.GetParameters().Select(p => Fmt(p.ParameterType))) + ")");
            foreach (PropertyInfo p in t.GetProperties(F))
                set.Add(Fmt(p.PropertyType) + " " + tn + "." + p.Name + " { " + (p.CanRead ? "get; " : "") + (p.CanWrite ? "set; " : "") + "}");
        }
        return set;
    }

    static int Main(string[] args)
    {
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

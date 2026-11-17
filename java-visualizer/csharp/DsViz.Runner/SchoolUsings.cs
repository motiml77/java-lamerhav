using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection.Metadata;
using System.Reflection.PortableExecutable;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using Microsoft.CodeAnalysis.Text;

namespace DsViz
{
    /// <summary>
    /// שורות using של ספריות בית הספר שאין בספרייה של DSViz.
    ///
    /// Unit4.dll של בתי הספר מכיל, חוץ מהאוספים (Unit4.CollectionsLib), BinTreeUtils ו-TreeCanvas (שיש גם אצלנו), גם ספריות גרפיות
    /// (Unit4.GridLib, Unit4.TurtleLib, Unit4.FrogLib...). בתבניות של בתי הספר יש לרוב בראש הקובץ using לכמה מהן גם כשלא משתמשים בהן,
    /// והקוד נבנה בהרצה הרגילה (מול Unit4.dll). ההרצה הוויזואלית מקמפלת מול הספרייה של DSViz, ושם ה-namespace לא קיים — CS0234,
    /// וההרצה לא התחילה. לכן: using שהמהדר לא מוצא (לפי השגיאות של המהדר) ל-namespace שקיים ב-DLL של בית הספר שהפרויקט מפנה אליו
    /// (נקרא מהקובץ עצמו — כך גם ספרייה שמשרד החינוך יוסיף בעתיד מכוסה, בלי רשימה בקוד) מוחלף ברווחים (אותו אורך, אותן שורות — המספור
    /// של השורות בתצוגה לא זז), ומקמפלים שוב. כשאין DLL לקרוא (ProjectReference, קובץ חסר) — כל namespace שמתחיל ב-Unit4.
    /// using עם שגיאת הקלדה (namespace שלא קיים גם שם) נשאר שגיאה, כמו בהרצה הרגילה. קוד שבאמת משתמש בספרייה גרפית — הסבר בעברית.
    /// הסיווג נבדק מול Unit4.dll האמיתי (cs-tests, סעיף כט): כל סוג ציבורי שם קיים אצלנו באותו API, או רשום כאן ב-GraphicTypes.
    /// </summary>
    internal static class SchoolUsings
    {
        /// <summary>הסוגים הציבוריים בספריות הגרפיות של Unit4.dll (לפי הקובץ האמיתי) — להסבר כשהקוד משתמש באחד מהם</summary>
        static readonly Dictionary<string, string[]> GraphicTypes = new Dictionary<string, string[]>(StringComparer.Ordinal)
        {
            { "Unit4.GridLib", new[] { "Grid", "Point" } },
            { "Unit4.TurtleLib", new[] { "Turtle" } },
            { "Unit4.FrogLib", new[] { "FrogsBase", "BrownFrog", "GreenFrog" } },
            { "Unit4.TowerLib", new[] { "Tower" } },
            { "Unit4.HanoiLib", new[] { "HanoiTowersSimulator" } },
            { "Unit4.CardLib", new[] { "Card" } },
            { "Unit4.PolygonLib", new[] { "Polygon", "Point" } },
            { "Unit4.OrganLib", new[] { "Organ" } },
            { "Unit4.DiceLib", new[] { "Die" } },
            { "Unit4.DreidelLib", new[] { "Dreidel" } },
            { "Unit4.BucketLib", new[] { "Bucket" } },
        };

        static string NameOf(UsingDirectiveSyntax u)
        {
            string name = u.Name?.ToString();
            if (name == null) return null;
            name = new string(name.Where(c => !char.IsWhiteSpace(c)).ToArray());
            if (name.StartsWith("global::", StringComparison.Ordinal)) name = name.Substring(8);
            int lt = name.IndexOf('<');
            return lt >= 0 ? name.Substring(0, lt) : name;
        }

        /// <summary>כל הסוגים שרשומים כגרפיים ("Unit4.GridLib.Grid") — לבדיקה מול הקובץ האמיתי (dsviz-cs --school-graphics)</summary>
        internal static IEnumerable<string> GraphicTypeNames()
        {
            return GraphicTypes.SelectMany(kv => kv.Value.Select(t => kv.Key + "." + t)).OrderBy(s => s, StringComparer.Ordinal);
        }

        static readonly Dictionary<string, Dictionary<string, List<string>>> dllCache = new Dictionary<string, Dictionary<string, List<string>>>(StringComparer.OrdinalIgnoreCase);

        /// <summary>namespace → הסוגים הציבוריים בו, מתוך ה-DLL (בלי לטעון אותו — System.Reflection.Metadata); null אם אי אפשר לקרוא</summary>
        internal static Dictionary<string, List<string>> TypesIn(string dll)
        {
            if (string.IsNullOrEmpty(dll)) return null;
            Dictionary<string, List<string>> map;
            if (dllCache.TryGetValue(dll, out map)) return map;
            try
            {
                map = new Dictionary<string, List<string>>(StringComparer.Ordinal);
                using (FileStream fs = File.OpenRead(dll))
                using (var pe = new PEReader(fs))
                {
                    MetadataReader md = pe.GetMetadataReader();
                    foreach (TypeDefinitionHandle h in md.TypeDefinitions)
                    {
                        TypeDefinition td = md.GetTypeDefinition(h);
                        if ((td.Attributes & System.Reflection.TypeAttributes.VisibilityMask) != System.Reflection.TypeAttributes.Public) continue;
                        string ns = md.GetString(td.Namespace), name = md.GetString(td.Name).Split('`')[0];
                        if (ns.Length == 0) continue;
                        List<string> list;
                        if (!map.TryGetValue(ns, out list)) map[ns] = list = new List<string>();
                        list.Add(name);
                    }
                }
            }
            catch (Exception) { map = null; }
            dllCache[dll] = map;
            return map;
        }

        /// <summary>האם השם (namespace, או סוג בתוכו — using static / alias) שייך ל-namespace שיש ב-DLL של בית הספר</summary>
        static bool InSchool(string name, Dictionary<string, List<string>> school)
        {
            if (school == null) return name.StartsWith("Unit4.", StringComparison.Ordinal);
            for (string n = name; n.Contains('.'); n = n.Substring(0, n.LastIndexOf('.')))
                if (school.ContainsKey(n)) return true;
            return false;
        }

        /// <summary>ה-namespaces של שורות using שהמהדר לא מצא (CS0234 / CS0246 בתוך שורת ה-using) ושקיימים בספרייה של בית הספר</summary>
        internal static HashSet<string> Missing(IEnumerable<Diagnostic> errors, string schoolDll)
        {
            var school = TypesIn(schoolDll);
            var names = new HashSet<string>(StringComparer.Ordinal);
            foreach (Diagnostic d in errors)
            {
                if ((d.Id != "CS0234" && d.Id != "CS0246") || !d.Location.IsInSource) continue;
                SyntaxNode node = d.Location.SourceTree.GetRoot().FindNode(d.Location.SourceSpan, getInnermostNodeForTie: true);
                UsingDirectiveSyntax u = node.AncestorsAndSelf().OfType<UsingDirectiveSyntax>().FirstOrDefault();
                string name = u == null ? null : NameOf(u);
                if (name != null && InSchool(name, school)) names.Add(name);
            }
            return names;
        }

        /// <summary>אותו קובץ, ובמקום כל using לאחד מ-names — רווחים (אותו אורך; ירידות שורה נשארות)</summary>
        internal static SyntaxTree Blank(SyntaxTree tree, ISet<string> names)
        {
            if (names.Count == 0) return tree;
            SourceText text = tree.GetText();
            var changes = new List<TextChange>();
            foreach (UsingDirectiveSyntax u in tree.GetRoot().DescendantNodes().OfType<UsingDirectiveSyntax>())
            {
                string name = NameOf(u);
                if (name == null || !names.Contains(name)) continue;
                string src = text.ToString(u.Span);
                changes.Add(new TextChange(u.Span, new string(src.Select(c => c == '\r' || c == '\n' ? c : ' ').ToArray())));
            }
            return changes.Count == 0 ? tree : tree.WithChangedText(text.WithChanges(changes));
        }

        /// <summary>
        /// הסבר לשגיאות שנשארו אחרי שהורדנו את ה-using: הקוד משתמש בסוג מספרייה גרפית של בית הספר (למשל Grid מ-Unit4.GridLib) —
        /// ההרצה הוויזואלית לא מריצה אותה; ההרצה הרגילה לא מושפעת
        /// </summary>
        internal static IEnumerable<string> Hints(IEnumerable<Diagnostic> errors, ISet<string> dropped, string schoolDll)
        {
            var school = TypesIn(schoolDll);
            var said = new HashSet<string>(StringComparer.Ordinal);
            foreach (Diagnostic d in errors.Where(e => e.Id == "CS0246" || e.Id == "CS0103"))
            {
                string msg = d.GetMessage();
                foreach (string ns in dropped)
                {
                    string[] graphic;
                    bool isGraphic = GraphicTypes.TryGetValue(ns, out graphic);
                    List<string> inDll = null;
                    IEnumerable<string> types = school != null && school.TryGetValue(ns, out inDll) ? inDll : (graphic ?? new string[0]);
                    foreach (string t in types)
                    {
                        if (!msg.Contains("'" + t + "'") || !said.Add(t)) continue;
                        yield return isGraphic
                            ? "← '" + t + "' היא מהספרייה של בית הספר (" + ns + ") — ספרייה גרפית שההרצה הוויזואלית לא מריצה. ההרצה הרגילה (הכפתור הירוק) לא מושפעת."
                            : "← '" + t + "' היא מהספרייה של בית הספר (" + ns + "), ואין אותה בספרייה של DSViz — ההרצה הוויזואלית לא יכולה להריץ אותה."
                              + " ההרצה הרגילה (הכפתור הירוק) לא מושפעת.";
                    }
                }
            }
        }
    }
}

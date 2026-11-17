using System;
using System.Collections.Generic;
using System.Linq;
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
    /// וההרצה לא התחילה. לכן: using ל-namespace של Unit4 שהמהדר לא מוצא (רק כזה, לפי השגיאות של המהדר — לא namespace של התלמיד) מוחלף
    /// ברווחים (אותו אורך, אותן שורות — המספור של השורות בתצוגה לא זז), ומקמפלים שוב. קוד שבאמת משתמש בספרייה גרפית — הסבר בעברית.
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

        /// <summary>ה-namespaces (שמתחילים ב-Unit4.) של שורות using שהמהדר לא מצא (CS0234 / CS0246 בתוך שורת ה-using)</summary>
        internal static HashSet<string> Missing(IEnumerable<Diagnostic> errors)
        {
            var names = new HashSet<string>(StringComparer.Ordinal);
            foreach (Diagnostic d in errors)
            {
                if ((d.Id != "CS0234" && d.Id != "CS0246") || !d.Location.IsInSource) continue;
                SyntaxNode node = d.Location.SourceTree.GetRoot().FindNode(d.Location.SourceSpan, getInnermostNodeForTie: true);
                UsingDirectiveSyntax u = node.AncestorsAndSelf().OfType<UsingDirectiveSyntax>().FirstOrDefault();
                string name = u == null ? null : NameOf(u);
                if (name != null && name.StartsWith("Unit4.", StringComparison.Ordinal)) names.Add(name);
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
        internal static IEnumerable<string> Hints(IEnumerable<Diagnostic> errors, ISet<string> dropped)
        {
            var said = new HashSet<string>(StringComparer.Ordinal);
            foreach (Diagnostic d in errors.Where(e => e.Id == "CS0246" || e.Id == "CS0103"))
            {
                string msg = d.GetMessage();
                foreach (string ns in dropped)
                {
                    string[] types;
                    if (!GraphicTypes.TryGetValue(ns, out types)) continue;
                    foreach (string t in types)
                    {
                        if (!msg.Contains("'" + t + "'") || !said.Add(t)) continue;
                        yield return "← '" + t + "' היא מהספרייה של בית הספר (" + ns + ") — ספרייה גרפית שההרצה הוויזואלית לא מריצה."
                            + " ההרצה הרגילה (הכפתור הירוק) לא מושפעת.";
                    }
                }
            }
        }
    }
}

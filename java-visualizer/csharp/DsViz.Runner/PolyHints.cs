using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text.RegularExpressions;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace DsViz
{
    /// <summary>
    /// הורשה ופולימורפיזם: הסבר קצר בעברית לכל שגיאה נפוצה, עם השמות מהקוד של התלמיד ועם הקוד המתוקן בסוף
    /// (כמו "המורה בעברית" של Eclipse — HebrewErrors). מודפס אחרי השגיאות, בשורות שמתחילות ב-← (Visual Studio מציג אותן בחלון מימין לשמאל).
    ///   pet.Bark() כש-Bark רק ב-Dog · (Dog)pet.Bark() · Dog d = pet; · Cat c = new Dog(); · (Cat)d · בנאי בלי : base(...) ·
    ///   פעולה מופשטת שלא נכתבה · new למחלקה מופשטת · override בלי virtual · override בלי פעולה לדרוס · הרשאה / טיפוס החזרה בדריסה ·
    ///   תכונה private של מחלקת-העל · sealed · ואזהרות: פעולה שמסתירה במקום לדרוס (CS0114), תכונה שחוזרת במחלקה היורשת (CS0108)
    /// </summary>
    static class PolyHints
    {
        public static IEnumerable<string> For(IEnumerable<Diagnostic> diagnostics, Compilation comp)
        {
            var seen = new HashSet<string>();
            foreach (Diagnostic d in diagnostics)
            {
                if (!d.Location.IsInSource) continue;
                string text = null;
                try { text = Explain(d, comp); } catch (Exception) { }
                if (text == null) continue;
                var span = d.Location.GetLineSpan();
                string where = Path.GetFileName(span.Path) + ", שורה " + (span.StartLinePosition.Line + 1);
                string line = "← " + where + ": " + (d.Severity == DiagnosticSeverity.Warning ? "(אזהרה) " : "") + text;
                if (seen.Add(line)) yield return line;
            }
        }

        static string Explain(Diagnostic d, Compilation comp)
        {
            SyntaxTree tree = d.Location.SourceTree;
            SemanticModel model = comp.GetSemanticModel(tree);
            SyntaxNode node = tree.GetRoot().FindNode(d.Location.SourceSpan, getInnermostNodeForTie: true);
            string msg = d.GetMessage();
            switch (d.Id)
            {
                case "CS1061": return Member(node, model, comp);
                case "CS0266": case "CS0029": return Assign(node, model);
                case "CS0030": return Cast(node, model);
                case "CS7036": case "CS1729": return BaseCall(node, model);
                case "CS0534": case "CS0535": return AbstractMissing(node, model, msg, d.Id == "CS0535");
                case "CS0144": return AbstractNew(node, model, comp);
                case "CS0506": return NotVirtual(node, model);
                case "CS0115": return NothingToOverride(node, model);
                case "CS0507": return Access(node, model);
                case "CS0508": return ReturnType(node, model);
                case "CS0122": return Private(node, model);
                case "CS0239": return Q(msg, 1) == null ? null : "אי אפשר לדרוס את " + Q(msg, 1) + ", כי היא sealed (סופית). נותנים לפעולה שם אחר, או מוחקים את sealed.";
                case "CS0509": return Q(msg, 1) == null ? null : "אי אפשר לרשת מ-" + Q(msg, 1) + ", כי היא sealed (סופית). אם צריך לרשת — מוחקים את sealed מהכותרת שלה.";
                case "CS0513": return Abstract(node, model) ?? null;
                case "CS0500": return AbstractBody(node, model);
                case "CS0114": return Hides(node, model);
                case "CS0108": return HidesField(node, model);
            }
            return null;
        }

        // ------------------------------------------------------------ גישה דרך הפניה

        /// <summary>pet.Bark() כש-Bark רק ב-Dog; (Dog)pet.Bark() — ההמרה חלה על התוצאה</summary>
        static string Member(SyntaxNode node, SemanticModel model, Compilation comp)
        {
            var ma = node.AncestorsAndSelf().OfType<MemberAccessExpressionSyntax>().FirstOrDefault();
            if (ma == null) return null;
            string name = ma.Name.Identifier.ValueText;
            var inv = ma.Parent as InvocationExpressionSyntax;
            SyntaxNode whole = inv != null && inv.Expression == ma ? (SyntaxNode)inv : ma;
            string call = name + (inv != null ? inv.ArgumentList.ToString() : "");
            if (whole.Parent is CastExpressionSyntax ce && ce.Expression == whole)
            {
                var ct = model.GetTypeInfo(ce.Type).Type;
                if (ct != null && HasMember(ct, name))
                    return "הסוגריים של ההמרה במקום הלא נכון: ההמרה חלה על מה ש-" + name + "() מחזירה, ולא על " + ma.Expression
                        + ". עוטפים בסוגריים את ההמרה יחד עם ההפניה: ((" + ce.Type + ")" + ma.Expression + ")." + call;
            }
            var t = model.GetTypeInfo(ma.Expression).Type as INamedTypeSymbol;
            if (!Mine(t)) return null;
            var sub = Derived(comp, t).FirstOrDefault(x => Declares(x, t, name));
            if (sub == null) return null;
            string recv = ma.Expression.ToString();
            return "ל-" + t.Name + " אין " + name + " — היא רק ב-" + sub.Name + ". " + recv + " היא הפניה מסוג " + t.Name + ", ודרכה רואים רק את מה שיש ב-" + t.Name
                + ". אם היא מצביעה על " + sub.Name + " — בודקים עם is וממירים: ((" + sub.Name + ")" + recv + ")." + call;
        }

        /// <summary>Dog d = pet; (מחלקת-העל למשתנה של מחלקה יורשת) · Cat c = new Dog(); (מחלקות אחיות)</summary>
        static string Assign(SyntaxNode node, SemanticModel model)
        {
            var e = node as ExpressionSyntax ?? node.AncestorsAndSelf().OfType<ExpressionSyntax>().FirstOrDefault();
            if (e == null) return null;
            var info = model.GetTypeInfo(e);
            var from = info.Type as INamedTypeSymbol;
            var to = info.ConvertedType as INamedTypeSymbol;
            if (!Mine(from) || !Mine(to) || SymbolEqualityComparer.Default.Equals(from, to)) return null;
            string var = null, decl = null;
            if (e.Parent is EqualsValueClauseSyntax ev && ev.Parent is VariableDeclaratorSyntax vd)
            {
                var = vd.Identifier.ValueText;
                if (vd.Parent is VariableDeclarationSyntax vds) decl = vds.Type.ToString();
            }
            else if (e.Parent is AssignmentExpressionSyntax asg && asg.Right == e) var = asg.Left.ToString();
            if (Inherits(to, from))
            {
                if (e is ObjectCreationExpressionSyntax oc)
                    return "עצם של " + from.Name + " הוא לא " + to.Name + ": כל " + to.Name + " הוא " + from.Name + ", אבל לא להפך. יוצרים עצם של " + to.Name + ": "
                        + (decl != null ? decl + " " : "") + (var != null ? var + " = " : "") + "new " + to.Name + (oc.ArgumentList != null ? oc.ArgumentList.ToString() : "()") + ";";
                string fix = "(" + to.Name + ")" + e;
                if (var != null) fix = (decl != null ? decl + " " : "") + var + " = " + fix + ";";
                return "כל " + to.Name + " הוא " + from.Name + ", אבל לא כל " + from.Name + " הוא " + to.Name + ". אם " + e + " באמת מצביע על " + to.Name
                    + " — ממירים (ואפשר לבדוק קודם עם is): " + fix;
            }
            var common = Common(from, to);
            if (common == null) return null;
            return from.Name + " ו-" + to.Name + " יורשות שתיהן מ-" + common.Name + ", אבל " + from.Name + " הוא לא " + to.Name + ". משתנה מסוג " + common.Name
                + " יכול להצביע על כל אחת מהן" + (var != null && decl != null ? ": " + common.Name + " " + var + " = " + e + ";" : ".");
        }

        /// <summary>(Cat)d כש-d הוא Dog</summary>
        static string Cast(SyntaxNode node, SemanticModel model)
        {
            var ce = node.AncestorsAndSelf().OfType<CastExpressionSyntax>().FirstOrDefault();
            if (ce == null) return null;
            var from = model.GetTypeInfo(ce.Expression).Type as INamedTypeSymbol;
            var to = model.GetTypeInfo(ce.Type).Type as INamedTypeSymbol;
            if (!Mine(from) || !Mine(to)) return null;
            var common = Common(from, to);
            if (common == null) return "אי אפשר להמיר " + from.Name + " ל-" + to.Name + " — אין ביניהן ירושה. ממירים רק בין מחלקה למחלקה שיורשת ממנה (בכל כיוון).";
            return "אי אפשר להמיר " + from.Name + " ל-" + to.Name + " — אין עצם שהוא גם " + from.Name + " וגם " + to.Name + ". שתיהן יורשות מ-" + common.Name
                + ", אבל אלה ענפים נפרדים. הפניה מסוג " + common.Name + " יכולה להצביע על כל אחת מהן: " + common.Name + " a = " + ce.Expression + ";";
        }

        // ------------------------------------------------------------ בנאים ומחלקות מופשטות

        /// <summary>בנאי של מחלקה יורשת בלי : base(...), כשלמחלקת-העל אין בנאי בלי ערכים (או בלי בנאי בכלל)</summary>
        static string BaseCall(SyntaxNode node, SemanticModel model)
        {
            var ctor = node.AncestorsAndSelf().OfType<ConstructorDeclarationSyntax>().FirstOrDefault();
            var cls = node.AncestorsAndSelf().OfType<ClassDeclarationSyntax>().FirstOrDefault();
            if (cls == null) return null;
            if (ctor == null && !(node is ClassDeclarationSyntax) && node.Parent != cls && !(node is BaseListSyntax)) return null;
            if (ctor != null && ctor.Initializer != null) return null;   // : base(...) עם ערכים לא נכונים — ההודעה הרגילה
            var me = model.GetDeclaredSymbol(cls) as INamedTypeSymbol;
            var sup = me?.BaseType;
            if (!Mine(sup)) return null;
            var baseCtor = sup.InstanceConstructors.Where(c => c.DeclaredAccessibility != Accessibility.Private).OrderBy(c => c.Parameters.Length).FirstOrDefault();
            if (baseCtor == null || baseCtor.Parameters.Length == 0) return null;
            if (ctor != null)
            {
                var mine = model.GetDeclaredSymbol(ctor) as IMethodSymbol;
                var args = baseCtor.Parameters.Select(bp =>
                {
                    var same = mine?.Parameters.FirstOrDefault(p => p.Name == bp.Name) ?? mine?.Parameters.FirstOrDefault(p => SymbolEqualityComparer.Default.Equals(p.Type, bp.Type));
                    return same != null ? same.Name : bp.Name;
                });
                return "הבנאי של " + me.Name + " צריך לקרוא לבנאי של " + sup.Name + ", כי ל-" + sup.Name + " אין בנאי בלי ערכים. קודם בונים את החלק של " + sup.Name
                    + ". מוסיפים אחרי הכותרת: " + ctor.Modifiers + " " + me.Name + ctor.ParameterList + " : base(" + string.Join(", ", args) + ")";
            }
            string ps = string.Join(", ", baseCtor.Parameters.Select(p => TypeName(p.Type) + " " + p.Name));
            return "ל-" + me.Name + " אין בנאי, ו-" + sup.Name + " צריכה ערכים כדי להיבנות. מוסיפים ל-" + me.Name + " בנאי שמעביר אותם ל-" + sup.Name + ": public " + me.Name
                + "(" + ps + ") : base(" + string.Join(", ", baseCtor.Parameters.Select(p => p.Name)) + ") { }";
        }

        /// <summary>מחלקה שלא כתבה פעולה מופשטת שירשה (CS0534) / פעולה של ממשק שהיא מממשת (CS0535)</summary>
        static string AbstractMissing(SyntaxNode node, SemanticModel model, string msg, bool iface)
        {
            var cls = node.AncestorsAndSelf().OfType<ClassDeclarationSyntax>().FirstOrDefault();
            var me = cls == null ? null : model.GetDeclaredSymbol(cls) as INamedTypeSymbol;
            if (me == null) return null;
            var m = Regex.Match(msg, "member '([^']+)\\.([A-Za-z_]\\w*)\\(");
            if (!m.Success) return null;
            string owner = m.Groups[1].Value.Split('.').Last(), name = m.Groups[2].Value;
            IMethodSymbol target = null;
            for (var t = me.BaseType; t != null && target == null; t = t.BaseType) target = t.GetMembers(name).OfType<IMethodSymbol>().FirstOrDefault(x => x.IsAbstract);
            if (target == null) target = me.AllInterfaces.SelectMany(i => i.GetMembers(name).OfType<IMethodSymbol>()).FirstOrDefault();
            if (target == null) return null;
            string header = "public " + (iface ? "" : "override ") + TypeName(target.ReturnType) + " " + name + "(" + string.Join(", ", target.Parameters.Select(p => TypeName(p.Type) + " " + p.Name)) + ") { ... }";
            return (iface ? me.Name + " מממשת את " + owner + ", ולא כתבה את הפעולה " + name + "()" : me.Name + " יורשת מ-" + owner + " את הפעולה המופשטת " + name + "(), ולא כתבה אותה")
                + ". פעולה מופשטת היא הבטחה: כל מחלקה שיורשת חייבת לכתוב אותה. מוסיפים ל-" + me.Name + ": " + header;
        }

        /// <summary>new Animal() כש-Animal מופשטת או ממשק</summary>
        static string AbstractNew(SyntaxNode node, SemanticModel model, Compilation comp)
        {
            var oc = node.AncestorsAndSelf().OfType<ObjectCreationExpressionSyntax>().FirstOrDefault();
            var t = oc == null ? null : model.GetTypeInfo(oc).Type as INamedTypeSymbol;
            if (!Mine(t)) return null;
            string what = t.TypeKind == TypeKind.Interface ? t.Name + " הוא ממשק — אי אפשר ליצור ממנו עצם" : t.Name + " מופשטת (abstract) — אי אפשר ליצור ממנה עצם";
            var sub = Derived(comp, t).FirstOrDefault(x => !x.IsAbstract && x.TypeKind == TypeKind.Class);
            if (sub == null) return what + ". כותבים מחלקה שיורשת ממנה וכותבת את הפעולות המופשטות שלה, ויוצרים עצם שלה.";
            string var = oc.Parent is EqualsValueClauseSyntax ev && ev.Parent is VariableDeclaratorSyntax vd ? vd.Identifier.ValueText : "a";
            return what + ". יוצרים עצם של מחלקה שיורשת ממנה, ושומרים אותו בהפניה מסוג " + t.Name + ": " + t.Name + " " + var + " = new " + sub.Name
                + (oc.ArgumentList != null ? oc.ArgumentList.ToString() : "()") + ";";
        }

        static string Abstract(SyntaxNode node, SemanticModel model)
        {
            var cls = node.AncestorsAndSelf().OfType<ClassDeclarationSyntax>().FirstOrDefault();
            if (cls == null) return null;
            return "פעולה מופשטת (abstract) יכולה להיות רק במחלקה מופשטת. מוסיפים abstract לכותרת של המחלקה: public abstract class " + cls.Identifier.ValueText;
        }

        static string AbstractBody(SyntaxNode node, SemanticModel model)
        {
            var md = node.AncestorsAndSelf().OfType<MethodDeclarationSyntax>().FirstOrDefault();
            if (md == null) return null;
            return "לפעולה מופשטת אין גוף — רק כותרת ונקודה-פסיק. כל מחלקה שיורשת כותבת את הגוף שלה: " + md.Modifiers + " " + md.ReturnType + " " + md.Identifier + md.ParameterList + ";";
        }

        // ------------------------------------------------------------ דריסה

        /// <summary>override על פעולה שב-Animal לא מסומנת virtual</summary>
        static string NotVirtual(SyntaxNode node, SemanticModel model)
        {
            var mine = Method(node, model, out var md);
            var over = mine == null ? null : Inherited(mine).FirstOrDefault(x => SameParams(x, mine));
            if (over == null) return null;
            string owner = over.ContainingType.Name;
            return "כדי ש-" + mine.ContainingType.Name + " תוכל לדרוס את " + mine.Name + "(), צריך לסמן אותה ב-" + owner + " כ-virtual. מוסיפים virtual לכותרת ב-" + owner + ": "
                + Access(over) + "virtual " + TypeName(over.ReturnType) + " " + over.Name + "(" + Params(over) + ")";
        }

        /// <summary>override כשאין במחלקת-העל פעולה עם השם והפרמטרים האלה</summary>
        static string NothingToOverride(SyntaxNode node, SemanticModel model)
        {
            var mine = Method(node, model, out var md);
            if (mine == null) return null;
            var all = Inherited(mine).ToList();
            var same = all.FirstOrDefault(x => x.Name == mine.Name);
            if (same != null)
                return "ל-" + same.ContainingType.Name + " יש " + same.Name + ", אבל עם פרמטרים אחרים — לכן אין מה לדרוס. בדריסה הפרמטרים זהים בדיוק: "
                    + Access(same) + "override " + TypeName(same.ReturnType) + " " + same.Name + "(" + Params(same) + ")";
            var near = all.Where(x => x.Parameters.Length == mine.Parameters.Length)
                .FirstOrDefault(x => string.Equals(x.Name, mine.Name, StringComparison.OrdinalIgnoreCase) || Distance(x.Name.ToLowerInvariant(), mine.Name.ToLowerInvariant()) <= 2);
            if (near != null)
                return "כתוב override, אבל ל-" + near.ContainingType.Name + " אין פעולה " + mine.Name + " — יש " + near.Name + ". בדריסה השם זהה בדיוק (גם אותיות גדולות וקטנות): "
                    + Access(near) + "override " + TypeName(near.ReturnType) + " " + near.Name + "(" + Params(near) + ")";
            return "כתוב override, אבל למחלקת-העל אין פעולה " + mine.Name + "() לדרוס. אם זו פעולה חדשה — מוחקים את override; אחרת בודקים את השם והפרמטרים.";
        }

        static string Access(SyntaxNode node, SemanticModel model)
        {
            var mine = Method(node, model, out var md);
            var over = mine?.OverriddenMethod;
            if (over == null) return null;
            return mine.Name + "() דורסת את הפעולה של " + over.ContainingType.Name + ", ולכן ההרשאה שלה חייבת להיות זהה (" + Access(over).Trim() + "). מתקנים את הכותרת: "
                + Access(over) + "override " + TypeName(over.ReturnType) + " " + over.Name + "(" + Params(over) + ")";
        }

        static string ReturnType(SyntaxNode node, SemanticModel model)
        {
            var mine = Method(node, model, out var md);
            var over = mine?.OverriddenMethod ?? (mine == null ? null : Inherited(mine).FirstOrDefault(x => x.Name == mine.Name && SameParams(x, mine)));
            if (over == null) return null;
            return mine.Name + "() דורסת את " + over.Name + "() של " + over.ContainingType.Name + ", ולכן חייבת להחזיר " + TypeName(over.ReturnType)
                + " (אם זו פעולה אחרת — תנו לה שם אחר). מתקנים את הכותרת: " + Access(over) + "override " + TypeName(over.ReturnType) + " " + over.Name + "(" + Params(over) + ")";
        }

        /// <summary>CS0114: Say() ב-Dog בלי override מסתירה את Say() של Animal — pet.Say() יריץ את הגרסה של Animal</summary>
        static string Hides(SyntaxNode node, SemanticModel model)
        {
            var mine = Method(node, model, out var md);
            var hidden = mine == null ? null : Inherited(mine).FirstOrDefault(x => x.Name == mine.Name && SameParams(x, mine));
            if (hidden == null) return null;
            string owner = hidden.ContainingType.Name;
            return mine.Name + "() ב-" + mine.ContainingType.Name + " מסתירה את " + hidden.Name + "() של " + owner + " במקום לדרוס אותה: דרך הפניה מסוג " + owner
                + " תרוץ הגרסה של " + owner + ". כדי לדרוס: " + Access(hidden) + "override " + TypeName(hidden.ReturnType) + " " + hidden.Name + "(" + Params(hidden) + ")";
        }

        /// <summary>CS0108: תכונה באותו שם כמו תכונה של מחלקת-העל — לעצם יהיו שתיים</summary>
        static string HidesField(SyntaxNode node, SemanticModel model)
        {
            var v = node.AncestorsAndSelf().OfType<VariableDeclaratorSyntax>().FirstOrDefault();
            var f = v == null ? null : model.GetDeclaredSymbol(v) as IFieldSymbol;
            if (f == null) return null;
            ISymbol other = null;
            for (var t = f.ContainingType.BaseType; t != null && other == null; t = t.BaseType) other = t.GetMembers(f.Name).FirstOrDefault(x => x is IFieldSymbol || x is IPropertySymbol);
            if (other == null) return null;
            string owner = other.ContainingType.Name;
            return "גם ב-" + owner + " יש " + f.Name + " — לעצם של " + f.ContainingType.Name + " יהיו שתי תכונות " + f.Name + " נפרדות. התכונה כבר עוברת בירושה: מוחקים את השורה, ואת הערך מעבירים לבנאי של "
                + owner + ": : base(" + f.Name + ")";
        }

        /// <summary>תכונה / פעולה private של מחלקת-העל, בשימוש בתוך המחלקה שיורשת</summary>
        static string Private(SyntaxNode node, SemanticModel model)
        {
            var id = node.AncestorsAndSelf().OfType<SimpleNameSyntax>().FirstOrDefault();
            if (id == null) return null;
            var cls = node.AncestorsAndSelf().OfType<ClassDeclarationSyntax>().FirstOrDefault();
            var me = cls == null ? null : model.GetDeclaredSymbol(cls) as INamedTypeSymbol;
            if (me == null) return null;
            string name = id.Identifier.ValueText;
            for (var t = me.BaseType; t != null; t = t.BaseType)
            {
                var f = t.GetMembers(name).FirstOrDefault(x => x.DeclaredAccessibility == Accessibility.Private);
                if (f == null || !Mine(t)) continue;
                string cap = name.Length > 0 ? char.ToUpperInvariant(name[0]) + name.Substring(1) : name;
                string head = name + " פרטית (private) ב-" + t.Name + ", ולכן גם " + me.Name + " שיורשת ממנה לא רואה אותה";
                if (t.GetMembers(cap).OfType<IPropertySymbol>().Any()) return head + ". משתמשים במאפיין של " + t.Name + ": " + cap;
                if (t.GetMembers("Get" + cap).OfType<IMethodSymbol>().Any()) return head + ". משתמשים בפעולה של " + t.Name + ": Get" + cap + "()";
                string type = f is IFieldSymbol fs ? TypeName(fs.Type) : "int";
                return head + ". אם " + me.Name + " צריכה לגשת אליה ישירות — משנים ב-" + t.Name + " את private ל-protected: protected " + type + " " + name + ";";
            }
            return null;
        }

        // ------------------------------------------------------------ עזרים

        static IMethodSymbol Method(SyntaxNode node, SemanticModel model, out MethodDeclarationSyntax md)
        {
            md = node.AncestorsAndSelf().OfType<MethodDeclarationSyntax>().FirstOrDefault();
            return md == null ? null : model.GetDeclaredSymbol(md) as IMethodSymbol;
        }

        /// <summary>הפעולות שאפשר לדרוס / להסתיר ממחלקות-העל (כולל object)</summary>
        static IEnumerable<IMethodSymbol> Inherited(IMethodSymbol m)
        {
            for (var t = m.ContainingType.BaseType; t != null; t = t.BaseType)
                foreach (var x in t.GetMembers().OfType<IMethodSymbol>())
                    if (x.MethodKind == MethodKind.Ordinary && !x.IsStatic && x.DeclaredAccessibility != Accessibility.Private) yield return x;
        }

        static bool SameParams(IMethodSymbol a, IMethodSymbol b) =>
            a.Parameters.Length == b.Parameters.Length && a.Parameters.Zip(b.Parameters, (x, y) => SymbolEqualityComparer.Default.Equals(x.Type, y.Type)).All(z => z);

        /// <summary>מחלקה / ממשק שהתלמיד כתב (בקוד של הפרויקט)</summary>
        static bool Mine(ITypeSymbol t) =>
            t is INamedTypeSymbol n && (n.TypeKind == TypeKind.Class || n.TypeKind == TypeKind.Interface) && n.Locations.Any(l => l.IsInSource);

        static bool Inherits(ITypeSymbol sub, ITypeSymbol sup)
        {
            if (sub == null || sup == null || SymbolEqualityComparer.Default.Equals(sub, sup)) return false;
            for (var t = sub.BaseType; t != null; t = t.BaseType) if (SymbolEqualityComparer.Default.Equals(t, sup)) return true;
            return sub.AllInterfaces.Any(i => SymbolEqualityComparer.Default.Equals(i, sup));
        }

        /// <summary>מחלקת-העל המשותפת הקרובה (לא object) של שתי מחלקות שאין ביניהן ירושה</summary>
        static INamedTypeSymbol Common(INamedTypeSymbol a, INamedTypeSymbol b)
        {
            if (Inherits(a, b) || Inherits(b, a)) return null;
            for (var t = a.BaseType; t != null && t.SpecialType != SpecialType.System_Object; t = t.BaseType) if (Inherits(b, t)) return t;
            return a.AllInterfaces.FirstOrDefault(i => Inherits(b, i));
        }

        /// <summary>כל המחלקות בקוד של הפרויקט שיורשות מ-t (או מממשות אותו)</summary>
        static IEnumerable<INamedTypeSymbol> Derived(Compilation comp, INamedTypeSymbol t)
        {
            foreach (var tree in comp.SyntaxTrees)
            {
                var model = comp.GetSemanticModel(tree);
                foreach (var td in tree.GetRoot().DescendantNodes().OfType<ClassDeclarationSyntax>())
                    if (model.GetDeclaredSymbol(td) is INamedTypeSymbol s && Inherits(s, t)) yield return s;
            }
        }

        /// <summary>name מוגדר ב-sub או במחלקה שבין sub ל-sup</summary>
        static bool Declares(INamedTypeSymbol sub, INamedTypeSymbol sup, string name)
        {
            for (var t = sub; t != null && !SymbolEqualityComparer.Default.Equals(t, sup); t = t.BaseType) if (t.GetMembers(name).Any()) return true;
            return false;
        }

        static bool HasMember(ITypeSymbol t, string name)
        {
            for (var x = t; x != null; x = x.BaseType) if (x.GetMembers(name).Any()) return true;
            return false;
        }

        static string Access(IMethodSymbol m) =>
            m.DeclaredAccessibility == Accessibility.Public ? "public " : m.DeclaredAccessibility == Accessibility.Protected ? "protected " : "";

        static string Params(IMethodSymbol m) => string.Join(", ", m.Parameters.Select(p => TypeName(p.Type) + " " + p.Name));

        static string TypeName(ITypeSymbol t) => t.ToDisplayString(SymbolDisplayFormat.MinimallyQualifiedFormat);

        /// <summary>השם שבין הגרשיים ה-n בהודעה (בלי namespace)</summary>
        static string Q(string msg, int n)
        {
            var ms = Regex.Matches(msg, "'([^']+)'");
            if (ms.Count <= n) return null;
            return ms[n].Groups[1].Value.Split('.').Last();
        }

        static int Distance(string a, string b)
        {
            var d = new int[a.Length + 1, b.Length + 1];
            for (int i = 0; i <= a.Length; i++) d[i, 0] = i;
            for (int j = 0; j <= b.Length; j++) d[0, j] = j;
            for (int i = 1; i <= a.Length; i++)
                for (int j = 1; j <= b.Length; j++)
                    d[i, j] = Math.Min(Math.Min(d[i - 1, j] + 1, d[i, j - 1] + 1), d[i - 1, j - 1] + (a[i - 1] == b[j - 1] ? 0 : 1));
            return d[a.Length, b.Length];
        }
    }
}

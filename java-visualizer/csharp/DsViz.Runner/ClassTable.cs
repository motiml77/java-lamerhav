using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace DsViz
{
    /// <summary>
    /// טבלת המחלקות של התלמיד (הורשה ופולימורפיזם — Poly בחלון של Java): לכל מחלקה — מחלקת העל (אם גם היא של התלמיד), abstract,
    /// ממשקים, המאפיינים שלה (גם מאפיין אוטומטי) והפעולות שלה לפי הסדר בקוד, עם virtual / override / new — מ-Roslyn, לפני ההוקים.
    /// נכתבת בכותרת של קובץ ההרצה: CLS שם, מחלקת-על, abstract, interface, ממשקים (מופרדים ב-\u0001); אחריה CF שם, טיפוס, נראות
    /// לכל מאפיין, ו-CM שם, טיפוסי הפרמטרים, כמה, דגלים (c בנאי, s static, a abstract, p private, v virtual/override/abstract,
    /// o override, n new), נראות, טיפוס ההחזרה (ריק = void). קובץ בלי השורות האלה — הכרטיסים כמו קודם.
    /// </summary>
    internal static class ClassTable
    {
        public static readonly List<string[]> Rows = new List<string[]>();

        public static void Build(Compilation comp)
        {
            Rows.Clear();
            try
            {
                var seen = new HashSet<string>();
                foreach (SyntaxTree tree in comp.SyntaxTrees)
                {
                    if (tree.FilePath == "GlobalUsings.g.cs") continue;
                    SemanticModel model = comp.GetSemanticModel(tree);
                    foreach (TypeDeclarationSyntax td in tree.GetRoot().DescendantNodes().OfType<TypeDeclarationSyntax>())
                    {
                        if (!(td is ClassDeclarationSyntax || td is InterfaceDeclarationSyntax)) continue;
                        var t = model.GetDeclaredSymbol(td) as INamedTypeSymbol;
                        if (t == null || !seen.Add(t.Name)) continue;   // partial — פעם אחת
                        Add(t);
                    }
                }
            }
            catch (Exception)
            {
                Rows.Clear();   // בלי טבלה — הכרטיסים כמו קודם
            }
        }

        static bool InSource(ITypeSymbol t)
        {
            return t != null && t.Locations.Any(l => l.IsInSource);
        }

        static string Vis(Accessibility a)
        {
            switch (a)
            {
                case Accessibility.Public: return "+";
                case Accessibility.Private: return "-";
                case Accessibility.Protected: return "#";
                default: return "~";
            }
        }

        static string TypeText(ITypeSymbol t)
        {
            return t == null ? "?" : t.ToDisplayString(SymbolDisplayFormat.MinimallyQualifiedFormat);
        }

        static int Pos(ISymbol s)
        {
            Location l = s.Locations.FirstOrDefault(x => x.IsInSource);
            return l == null ? int.MaxValue : l.SourceSpan.Start;
        }

        static void Add(INamedTypeSymbol t)
        {
            string sup = t.BaseType != null && InSource(t.BaseType) ? t.BaseType.Name : "";
            Rows.Add(new[] { "CLS", t.Name, sup, t.IsAbstract && t.TypeKind != TypeKind.Interface ? "1" : "0", t.TypeKind == TypeKind.Interface ? "1" : "0",
                             string.Join("\u0001", t.Interfaces.Select(i => i.Name)) });
            // מאפיינים: שדות של המופע, ומאפיין אוטומטי (השדה שמאחוריו) — לפי הסדר בקוד
            var fields = new List<KeyValuePair<int, string[]>>();
            foreach (ISymbol m in t.GetMembers())
            {
                var f = m as IFieldSymbol;
                if (f == null || f.IsStatic || f.IsConst) continue;
                ISymbol owner = f.AssociatedSymbol;
                if (f.IsImplicitlyDeclared && !(owner is IPropertySymbol)) continue;
                string name = owner is IPropertySymbol ? owner.Name : f.Name;
                Accessibility acc = owner is IPropertySymbol ? owner.DeclaredAccessibility : f.DeclaredAccessibility;
                fields.Add(new KeyValuePair<int, string[]>(Pos(owner ?? f), new[] { "CF", name, TypeText(f.Type), Vis(acc) }));
            }
            foreach (var kv in fields.OrderBy(x => x.Key)) Rows.Add(kv.Value);
            // פעולות: הבנאים ואז הפעולות, לפי הסדר בקוד
            var ctors = new List<KeyValuePair<int, string[]>>();
            var methods = new List<KeyValuePair<int, string[]>>();
            foreach (ISymbol m in t.GetMembers())
            {
                var x = m as IMethodSymbol;
                if (x == null || x.IsImplicitlyDeclared) continue;
                bool ctor = x.MethodKind == MethodKind.Constructor;
                if (!ctor && x.MethodKind != MethodKind.Ordinary) continue;
                bool hides = x.DeclaringSyntaxReferences.Select(r => r.GetSyntax()).OfType<MethodDeclarationSyntax>()
                              .Any(d => d.Modifiers.Any(k => k.IsKind(SyntaxKind.NewKeyword)));
                string flags = (ctor ? "c" : "") + (x.IsStatic ? "s" : "") + (x.IsAbstract ? "a" : "")
                               + (x.DeclaredAccessibility == Accessibility.Private ? "p" : "")
                               + (x.IsVirtual || x.IsOverride || x.IsAbstract ? "v" : "") + (x.IsOverride ? "o" : "") + (hides ? "n" : "");
                var row = new[] { "CM", ctor ? t.Name : x.Name, string.Join(",", x.Parameters.Select(p => p.Type.Name)), x.Parameters.Length.ToString(),
                                  flags, Vis(x.DeclaredAccessibility), ctor || x.ReturnsVoid ? "" : TypeText(x.ReturnType) };
                (ctor ? ctors : methods).Add(new KeyValuePair<int, string[]>(Pos(x), row));
            }
            foreach (var kv in ctors.OrderBy(k => k.Key)) Rows.Add(kv.Value);
            foreach (var kv in methods.OrderBy(k => k.Key)) Rows.Add(kv.Value);
        }
    }
}

using System;
using System.Collections.Generic;
using System.Linq;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;

namespace DsViz
{
    /// <summary>
    /// מכניס לקוד של התלמיד דיווח לפני כל שורה (VizHooks.Ln) — עם שמות וערכי כל המשתנים שבתחום —
    /// ועוטף כל פעולה ב-Enter / Exit, ואת ערכי ההחזרה ב-Ret. זה מה שנותן את ה"שורה אחר שורה" בלי debugger.
    /// עובד על העץ המקורי + SemanticModel (כדי לדעת אילו משתנים בתחום ומוגדרים), ולכן חשוב לבקר את הצמתים המקוריים.
    /// </summary>
    internal sealed class Instrumenter : CSharpSyntaxRewriter
    {
        const string H = "global::Unit4.CollectionsLib.VizHooks.";

        readonly SemanticModel model;
        readonly string file;
        string cls = "Program";
        string retType;          // סוג ההחזרה של הפעולה הנוכחית (null = void / לא מדווחים)

        public Instrumenter(SemanticModel model, string file)
        {
            this.model = model;
            this.file = file;
        }

        // ------------------------------------------------------------ מחלקות

        public override SyntaxNode VisitClassDeclaration(ClassDeclarationSyntax n) { return InClass(n.Identifier.Text, () => base.VisitClassDeclaration(n)); }
        public override SyntaxNode VisitStructDeclaration(StructDeclarationSyntax n) { return InClass(n.Identifier.Text, () => base.VisitStructDeclaration(n)); }
        public override SyntaxNode VisitRecordDeclaration(RecordDeclarationSyntax n) { return InClass(n.Identifier.Text, () => base.VisitRecordDeclaration(n)); }

        SyntaxNode InClass(string name, Func<SyntaxNode> visit)
        {
            string old = cls;
            cls = name;
            try { return visit(); } finally { cls = old; }
        }

        // ------------------------------------------------------------ פעולות

        public override SyntaxNode VisitMethodDeclaration(MethodDeclarationSyntax n)
        {
            if (!Eligible(n.Modifiers, n.Body, n.ExpressionBody)) return n;
            bool isVoid = n.ReturnType is PredefinedTypeSyntax p && p.Keyword.IsKind(SyntaxKind.VoidKeyword);
            string old = retType;
            retType = isVoid || n.ReturnType is RefTypeSyntax ? null : n.ReturnType.ToString();
            try
            {
                BlockSyntax inner;
                if (n.Body != null) inner = (BlockSyntax)Visit(n.Body);
                else inner = ExpressionBody(n.ExpressionBody.Expression, isVoid);
                BlockSyntax wrapped = Wrap(n.Identifier.Text, Line(n.Identifier), inner);
                return n.WithBody(wrapped).WithExpressionBody(null).WithSemicolonToken(SyntaxFactory.Token(SyntaxKind.None));
            }
            finally { retType = old; }
        }

        public override SyntaxNode VisitConstructorDeclaration(ConstructorDeclarationSyntax n)
        {
            if (!Eligible(n.Modifiers, n.Body, n.ExpressionBody)) return n;
            string old = retType;
            retType = null;
            try
            {
                BlockSyntax inner = n.Body != null ? (BlockSyntax)Visit(n.Body) : ExpressionBody(n.ExpressionBody.Expression, true);
                BlockSyntax wrapped = Wrap("<init>", Line(n.Identifier), inner);
                return n.WithBody(wrapped).WithExpressionBody(null).WithSemicolonToken(SyntaxFactory.Token(SyntaxKind.None));
            }
            finally { retType = old; }
        }

        static bool Eligible(SyntaxTokenList modifiers, BlockSyntax body, ArrowExpressionClauseSyntax expr)
        {
            if (body == null && expr == null) return false;
            foreach (SyntaxToken m in modifiers)
                if (m.IsKind(SyntaxKind.AsyncKeyword) || m.IsKind(SyntaxKind.AbstractKeyword) || m.IsKind(SyntaxKind.ExternKeyword)) return false;
            if (body != null && body.DescendantNodes(x => !(x is LocalFunctionStatementSyntax) && !(x is AnonymousFunctionExpressionSyntax))
                                    .OfType<YieldStatementSyntax>().Any()) return false;   // איטרטורים — לא נוגעים
            return true;
        }

        BlockSyntax ExpressionBody(ExpressionSyntax e, bool isVoid)
        {
            if (isVoid) return SyntaxFactory.Block(SyntaxFactory.ExpressionStatement(e));
            return SyntaxFactory.Block(SyntaxFactory.ReturnStatement(WrapReturn(e)));
        }

        ExpressionSyntax WrapReturn(ExpressionSyntax e)
        {
            if (retType == null) return e;
            return SyntaxFactory.InvocationExpression(
                SyntaxFactory.ParseExpression(H + "Ret<" + retType + ">"),
                SyntaxFactory.ArgumentList(SyntaxFactory.SingletonSeparatedList(SyntaxFactory.Argument(e)))).WithTriviaFrom(e);
        }

        BlockSyntax Wrap(string method, int line, BlockSyntax inner)
        {
            var enter = SyntaxFactory.ParseStatement(H + "Enter(" + Quote(cls) + ", " + Quote(method) + ", " + Quote(file) + ", " + line + ");");
            var tpl = (TryStatementSyntax)SyntaxFactory.ParseStatement(
                "try { } catch (global::System.Exception __dsviz_e) { " + H + "Fault(__dsviz_e); throw; } finally { " + H + "Exit(); }");
            return SyntaxFactory.Block(enter, tpl.WithBlock(inner));
        }

        public override SyntaxNode VisitReturnStatement(ReturnStatementSyntax n)
        {
            if (n.Expression == null || retType == null) return base.VisitReturnStatement(n);
            var e = (ExpressionSyntax)Visit(n.Expression);
            return n.WithExpression(WrapReturn(e));
        }

        // לא נכנסים לביטויי למדה ולפעולות מקומיות (ה-return שבהן שייך להן)
        public override SyntaxNode VisitSimpleLambdaExpression(SimpleLambdaExpressionSyntax n) { return n; }
        public override SyntaxNode VisitParenthesizedLambdaExpression(ParenthesizedLambdaExpressionSyntax n) { return n; }
        public override SyntaxNode VisitAnonymousMethodExpression(AnonymousMethodExpressionSyntax n) { return n; }
        public override SyntaxNode VisitLocalFunctionStatement(LocalFunctionStatementSyntax n) { return n; }

        // ------------------------------------------------------------ משפטים

        public override SyntaxNode VisitBlock(BlockSyntax b)
        {
            return b.WithStatements(Hooked(b.Statements));
        }

        public override SyntaxNode VisitSwitchSection(SwitchSectionSyntax n)
        {
            return n.WithStatements(Hooked(n.Statements));
        }

        SyntaxList<StatementSyntax> Hooked(SyntaxList<StatementSyntax> statements)
        {
            var list = new List<StatementSyntax>();
            foreach (StatementSyntax s in statements)
            {
                var visited = (StatementSyntax)Visit(s);
                if (Hookable(s)) list.Add(HookStatement(s));
                list.Add(visited);
            }
            return SyntaxFactory.List(list);
        }

        static bool Hookable(StatementSyntax s)
        {
            return !(s is BlockSyntax || s is EmptyStatementSyntax || s is LabeledStatementSyntax || s is LocalFunctionStatementSyntax
                     || s is ForStatementSyntax || s is WhileStatementSyntax || s is DoStatementSyntax
                     || s is FixedStatementSyntax || s is UnsafeStatementSyntax);
        }

        StatementSyntax Embed(StatementSyntax orig)
        {
            var v = (StatementSyntax)Visit(orig);
            if (v is BlockSyntax || !Hookable(orig)) return v;
            return SyntaxFactory.Block(HookStatement(orig), v);
        }

        public override SyntaxNode VisitIfStatement(IfStatementSyntax n)
        {
            var cond = (ExpressionSyntax)Visit(n.Condition);
            ElseClauseSyntax els = n.Else == null ? null : n.Else.WithStatement(Embed(n.Else.Statement));
            return n.WithCondition(cond).WithStatement(Embed(n.Statement)).WithElse(els);
        }

        public override SyntaxNode VisitWhileStatement(WhileStatementSyntax n)
        {
            var cond = (ExpressionSyntax)Visit(n.Condition);
            var loop = LoopArgs(n, "while", Line(n.WhileKeyword), n.Condition, n.Statement, null);
            return n.WithCondition(Conjoin(HookExpression(n.Condition, n.Condition.SpanStart, Line(n.WhileKeyword), null, loop), cond))
                    .WithStatement(Embed(n.Statement));
        }

        public override SyntaxNode VisitDoStatement(DoStatementSyntax n)
        {
            var cond = (ExpressionSyntax)Visit(n.Condition);
            var loop = LoopArgs(n, "do", Line(n.DoKeyword), n.Condition, n.Statement, null);
            return n.WithCondition(Conjoin(HookExpression(n.Condition, n.Condition.SpanStart, Line(n.WhileKeyword), null, loop), cond))
                    .WithStatement(Embed(n.Statement));
        }

        public override SyntaxNode VisitForStatement(ForStatementSyntax n)
        {
            var init = n.Declaration == null ? null : (VariableDeclarationSyntax)Visit(n.Declaration);
            var initializers = SyntaxFactory.SeparatedList(n.Initializers.Select(i => (ExpressionSyntax)Visit(i)));
            var incs = SyntaxFactory.SeparatedList(n.Incrementors.Select(i => (ExpressionSyntax)Visit(i)));
            StatementSyntax body;
            ExpressionSyntax cond = null;
            if (n.Condition != null)
            {
                var c = (ExpressionSyntax)Visit(n.Condition);
                var loop = LoopArgs(n, "for", Line(n.ForKeyword), n.Condition, n.Statement, n.Incrementors);
                cond = Conjoin(HookExpression(n.Condition, n.Condition.SpanStart, Line(n.ForKeyword), null, loop), c);
                body = Embed(n.Statement);
            }
            else
            {
                // for (;;) — השורה מדווחת בתחילת כל סיבוב (וגם הבדיקה של לולאה שלא נגמרת)
                var b = (StatementSyntax)Visit(n.Statement);
                var loop = LoopArgs(n, "for", Line(n.ForKeyword), null, n.Statement, n.Incrementors);
                var hook = SyntaxFactory.ExpressionStatement(HookExpression(n.Statement, n.Statement.SpanStart, Line(n.ForKeyword), null, loop));
                body = SyntaxFactory.Block(hook, b);
            }
            return n.WithDeclaration(init).WithInitializers(initializers).WithCondition(cond).WithIncrementors(incs).WithStatement(body);
        }

        public override SyntaxNode VisitForEachStatement(ForEachStatementSyntax n)
        {
            var expr = (ExpressionSyntax)Visit(n.Expression);
            var b = (StatementSyntax)Visit(n.Statement);
            var hook = HookStatementAt(n.Statement, n.Statement.SpanStart, n.Statement, Line(n.ForEachKeyword), model.GetDeclaredSymbol(n));
            StatementSyntax body = b is BlockSyntax blk
                ? blk.WithStatements(blk.Statements.Insert(0, hook))
                : SyntaxFactory.Block(hook, b);
            return n.WithExpression(expr).WithStatement(body);
        }

        public override SyntaxNode VisitUsingStatement(UsingStatementSyntax n)
        {
            var decl = n.Declaration == null ? null : (VariableDeclarationSyntax)Visit(n.Declaration);
            var expr = n.Expression == null ? null : (ExpressionSyntax)Visit(n.Expression);
            return n.WithDeclaration(decl).WithExpression(expr).WithStatement(Embed(n.Statement));
        }

        public override SyntaxNode VisitLockStatement(LockStatementSyntax n)
        {
            return n.WithExpression((ExpressionSyntax)Visit(n.Expression)).WithStatement(Embed(n.Statement));
        }

        // ------------------------------------------------------------ הוקים

        static ExpressionSyntax Conjoin(ExpressionSyntax hook, ExpressionSyntax cond)
        {
            return SyntaxFactory.BinaryExpression(SyntaxKind.LogicalAndExpression, hook, SyntaxFactory.ParenthesizedExpression(cond));
        }

        StatementSyntax HookStatement(StatementSyntax s)
        {
            return HookStatementAt(s, s.SpanStart, s, Line(s.GetFirstToken()));
        }

        StatementSyntax HookStatementAt(SyntaxNode at, int position, SyntaxNode flow, int line, ISymbol extra = null)
        {
            return SyntaxFactory.ExpressionStatement(HookExpression(flow, position, line, extra));
        }

        ExpressionSyntax HookExpression(SyntaxNode flow, int position, int line, ISymbol extra = null, string loop = null)
        {
            // בתנאי של לולאה: Lp(שורה, מה ידוע על הלולאה, צד שמאל, צד ימין, ...) — לזיהוי לולאה שלא נגמרת (LoopGuard)
            var sb = new System.Text.StringBuilder(loop == null ? H + "Ln(" + line : H + "Lp(" + line + ", " + loop);
            foreach (ISymbol v in VisibleVariables(flow, position, extra))
            {
                string id = SyntaxFacts.GetKeywordKind(v.Name) != SyntaxKind.None ? "@" + v.Name : v.Name;
                sb.Append(", ").Append(Quote(v.Name)).Append(", ").Append(id);
            }
            if (HasThis(position)) sb.Append(", \"this\", this");   // פעולה של אובייקט: האובייקט שהיא רצה בתוכו (כמו ב-Java)
            return SyntaxFactory.ParseExpression(sb.Append(')').ToString());
        }

        /// <summary>האם במקום הזה יש this של מחלקה (פעולה / בנאי / property שאינם static; לא struct)</summary>
        bool HasThis(int position)
        {
            ISymbol s;
            try { s = model.GetEnclosingSymbol(position); }
            catch (Exception) { return false; }
            while (s is IMethodSymbol f && (f.MethodKind == MethodKind.AnonymousFunction || f.MethodKind == MethodKind.LocalFunction))
            {
                if (f.IsStatic) return false;
                s = f.ContainingSymbol;
            }
            return s is IMethodSymbol m && !m.IsStatic && m.ContainingType != null && m.ContainingType.TypeKind == TypeKind.Class
                && (m.MethodKind == MethodKind.Ordinary || m.MethodKind == MethodKind.Constructor || m.MethodKind == MethodKind.PropertyGet
                    || m.MethodKind == MethodKind.PropertySet);
        }

        /// <summary>פרמטרים ומשתנים מקומיים שבתחום במקום הזה, מוגדרים כבר, וגם אפשר לקרוא (לא ref struct, לא out)</summary>
        List<ISymbol> VisibleVariables(SyntaxNode flow, int position, ISymbol extra)
        {
            var res = new List<ISymbol>();
            HashSet<ISymbol> assigned = null;
            try
            {
                DataFlowAnalysis df = flow is StatementSyntax st ? model.AnalyzeDataFlow(st) : model.AnalyzeDataFlow((ExpressionSyntax)flow);
                if (df != null && df.Succeeded) assigned = new HashSet<ISymbol>(df.DefinitelyAssignedOnEntry, SymbolEqualityComparer.Default);
            }
            catch (Exception) { }

            var syms = model.LookupSymbols(position).Where(s => s is ILocalSymbol || s is IParameterSymbol).ToList();
            if (extra != null && !syms.Contains(extra, SymbolEqualityComparer.Default)) syms.Add(extra);
            foreach (ISymbol s in syms.OrderBy(x => x is IParameterSymbol ? 0 : 1).ThenBy(x => x.Locations.FirstOrDefault()?.SourceSpan.Start ?? 0))
            {
                if (s.IsImplicitlyDeclared || s.Name.StartsWith("_") || string.IsNullOrEmpty(s.Name)) continue;
                ITypeSymbol type;
                if (s is ILocalSymbol l)
                {
                    if (l.RefKind != RefKind.None) continue;
                    type = l.Type;
                    SyntaxReference decl = l.DeclaringSyntaxReferences.FirstOrDefault();
                    if (decl != null && decl.Span.End > position && !SymbolEqualityComparer.Default.Equals(s, extra)) continue;   // עוד לא הוגדר
                    if (!SymbolEqualityComparer.Default.Equals(s, extra) && (assigned == null || !assigned.Contains(s))) continue;
                }
                else
                {
                    var p = (IParameterSymbol)s;
                    if (p.RefKind == RefKind.Out) continue;
                    type = p.Type;
                }
                if (type == null || type.IsRefLikeType || type.TypeKind == TypeKind.Pointer || type.TypeKind == TypeKind.FunctionPointer
                    || type.TypeKind == TypeKind.Error) continue;
                res.Add(s);
            }
            return res;
        }

        // ------------------------------------------------------------ לולאה שלא נגמרת: מה יודעים מהקוד

        static readonly HashSet<string> Pure = new HashSet<string> {
            "IsEmpty", "GetNext", "GetValue", "HasNext", "GetLeft", "GetRight", "HasLeft", "HasRight", "Top", "Head", "Equals",
            "CompareTo", "Contains", "IndexOf", "StartsWith", "EndsWith", "IsDigit", "IsLetter", "IsUpper", "IsLower", "IsWhiteSpace",
            "Abs", "Max", "Min", "Sqrt", "Pow", "Size", "Count", "Length", "GetName", "GetAge", "GetGrade", "GetAverage", "GetYear",
            "GetPrice", "ToString", "ToUpper", "ToLower", "Trim", "Substring", "IsNullOrEmpty" };

        static readonly System.Text.RegularExpressions.Regex Random = new System.Text.RegularExpressions.Regex(
            @"\bRandom\b|\bConsole\s*\.\s*Read|\bReadLine\b|\bReadKey\b|\bDateTime\b|\bStopwatch\b|\bTickCount|\bGuid\b|\bThread\b|\bTask\b|GetHashCode|\bFile\s*\.|\bStreamReader\b");

        bool? nondet;

        /// <summary>
        /// הארגומנטים הקבועים של Lp: "info" (סוג|שורה|סוף|השוואה|המשתנה שבהשוואה|דגלים|המשתנים שבתנאי), ושני הצדדים של
        /// השוואה פשוטה (i &lt; n) — כביטויים שמחושבים בכל סיבוב (בלי קריאות לפעולות), או null
        /// </summary>
        string LoopArgs(StatementSyntax loop, string kind, int header, ExpressionSyntax cond, StatementSyntax body, SeparatedSyntaxList<ExpressionSyntax>? incs)
        {
            try { return LoopArgsUnsafe(loop, kind, header, cond, body, incs); }
            catch (Exception) { return Quote(kind + "|" + header + "|" + header + "|||E|") + ", null, null"; }
        }

        string LoopArgsUnsafe(StatementSyntax loop, string kind, int header, ExpressionSyntax cond, StatementSyntax body, SeparatedSyntaxList<ExpressionSyntax>? incs)
        {
            int end = loop.GetLocation().GetLineSpan().EndLinePosition.Line + 1;
            var parts = new List<SyntaxNode> { body };
            if (cond != null) parts.Add(cond);
            if (incs != null) parts.AddRange(incs.Value);

            // המשתנים שבתנאי (מקומיים / פרמטרים), ומאפיינים שבתנאי
            var vars = new List<string>();
            var varSyms = new List<ISymbol>();
            bool fields = false, pure = true;
            if (cond != null)
            {
                foreach (IdentifierNameSyntax id in cond.DescendantNodesAndSelf().OfType<IdentifierNameSyntax>())
                {
                    ISymbol sym = model.GetSymbolInfo(id).Symbol;
                    if (sym is ILocalSymbol || sym is IParameterSymbol)
                    {
                        if (!vars.Contains(sym.Name)) { vars.Add(sym.Name); varSyms.Add(sym); }
                    }
                    else if (sym is IFieldSymbol f && !f.IsConst || sym is IPropertySymbol)
                    {
                        var ma = id.Parent as MemberAccessExpressionSyntax;
                        bool ofLocal = ma != null && ma.Name == id && !(ma.Expression is ThisExpressionSyntax)
                                       && (model.GetSymbolInfo(ma.Expression).Symbol is ILocalSymbol || model.GetSymbolInfo(ma.Expression).Symbol is IParameterSymbol);
                        if (!ofLocal) fields = true;
                    }
                }
                foreach (InvocationExpressionSyntax inv in cond.DescendantNodesAndSelf().OfType<InvocationExpressionSyntax>())
                {
                    var m = model.GetSymbolInfo(inv).Symbol as IMethodSymbol;
                    if (m == null || !Pure.Contains(m.Name) || m.Locations.Any(l => l.IsInSource)) pure = false;
                }
                if (cond.DescendantNodesAndSelf().Any(x => x is AssignmentExpressionSyntax
                        || x is PostfixUnaryExpressionSyntax pu && (pu.IsKind(SyntaxKind.PostIncrementExpression) || pu.IsKind(SyntaxKind.PostDecrementExpression))
                        || x is PrefixUnaryExpressionSyntax pr && (pr.IsKind(SyntaxKind.PreIncrementExpression) || pr.IsKind(SyntaxKind.PreDecrementExpression))))
                    pure = false;
            }

            // מה הלולאה משנה: משתנים מהתנאי (W), מאפיינים / תאים במערך (O), קריאה לפעולה של התלמיד (C)
            var written = new HashSet<ISymbol>(SymbolEqualityComparer.Default);
            foreach (SyntaxNode part in parts)
            {
                DataFlowAnalysis df = part is StatementSyntax st ? model.AnalyzeDataFlow(st) : model.AnalyzeDataFlow((ExpressionSyntax)part);
                if (df == null || !df.Succeeded) { written.UnionWith(varSyms); continue; }
                written.UnionWith(df.WrittenInside);
            }
            bool w = varSyms.Any(v => written.Contains(v)), outside = false, calls = false;
            foreach (SyntaxNode part in parts)
                foreach (SyntaxNode x in part.DescendantNodesAndSelf())
                {
                    ExpressionSyntax target = null;
                    if (x is AssignmentExpressionSyntax asg) target = asg.Left;
                    else if (x is PostfixUnaryExpressionSyntax pu && (pu.IsKind(SyntaxKind.PostIncrementExpression) || pu.IsKind(SyntaxKind.PostDecrementExpression))) target = pu.Operand;
                    else if (x is PrefixUnaryExpressionSyntax pr && (pr.IsKind(SyntaxKind.PreIncrementExpression) || pr.IsKind(SyntaxKind.PreDecrementExpression))) target = pr.Operand;
                    else if (x is ArgumentSyntax arg && !arg.RefKindKeyword.IsKind(SyntaxKind.None)) target = arg.Expression;
                    if (target != null)
                    {
                        ISymbol ts = target is IdentifierNameSyntax || target is DeclarationExpressionSyntax ? model.GetSymbolInfo(target).Symbol : null;
                        if (target is DeclarationExpressionSyntax) ts = null;
                        if (!(target is DeclarationExpressionSyntax) && !(ts is ILocalSymbol) && !(ts is IParameterSymbol p && p.RefKind == RefKind.None)
                            && !(target is TupleExpressionSyntax)) outside = true;
                    }
                    if (x is InvocationExpressionSyntax inv)
                    {
                        var m = model.GetSymbolInfo(inv).Symbol as IMethodSymbol;
                        if (m == null || m.MethodKind == MethodKind.DelegateInvoke || m.Locations.Any(l => l.IsInSource)) calls = true;
                    }
                    else if (x is ObjectCreationExpressionSyntax oc)
                    {
                        var m = model.GetSymbolInfo(oc).Symbol as IMethodSymbol;
                        if (m == null || m.Locations.Any(l => l.IsInSource)) calls = true;
                    }
                }

            // יציאה מהלולאה בדרך אחרת: break (של הלולאה הזו), return, throw, goto
            bool exits = false;
            foreach (SyntaxNode x in body.DescendantNodesAndSelf(y => !(y is LocalFunctionStatementSyntax) && !(y is AnonymousFunctionExpressionSyntax)))
            {
                if (x is ReturnStatementSyntax || x is ThrowStatementSyntax || x is ThrowExpressionSyntax || x is GotoStatementSyntax
                    || x is YieldStatementSyntax) exits = true;
                else if (x is BreakStatementSyntax)
                {
                    SyntaxNode up = x.Parent;
                    while (up != null && up != loop && !(up is WhileStatementSyntax || up is DoStatementSyntax || up is ForStatementSyntax
                           || up is ForEachStatementSyntax || up is SwitchStatementSyntax)) up = up.Parent;
                    if (up == loop) exits = true;
                }
                else if (x is InvocationExpressionSyntax inv && inv.Expression.ToString().Replace(" ", "").EndsWith("Environment.Exit")) exits = true;
            }

            if (nondet == null) nondet = Random.IsMatch(loop.SyntaxTree.GetText().ToString());
            bool constant = cond == null || cond is LiteralExpressionSyntax lit && lit.IsKind(SyntaxKind.TrueLiteralExpression);

            // השוואה פשוטה: i < n, x > 0, 0 < x, x != 0 — המשתנה תמיד משמאל
            string op = "", cmpVar = "", cmpArg = "null", boundArg = "null";
            ExpressionSyntax c = cond;
            while (c is ParenthesizedExpressionSyntax par) c = par.Expression;
            if (c is BinaryExpressionSyntax bin && (bin.IsKind(SyntaxKind.LessThanExpression) || bin.IsKind(SyntaxKind.LessThanOrEqualExpression)
                    || bin.IsKind(SyntaxKind.GreaterThanExpression) || bin.IsKind(SyntaxKind.GreaterThanOrEqualExpression) || bin.IsKind(SyntaxKind.NotEqualsExpression))
                && Numeric(bin.Left) && Numeric(bin.Right) && Simple(bin.Left) && Simple(bin.Right))
            {
                string o = bin.OperatorToken.Text;
                ISymbol a = VarOf(bin.Left), b = VarOf(bin.Right);
                bool aw = a != null && written.Contains(a), bw = b != null && written.Contains(b);
                if (a != null && (b == null || !bw || aw))
                {
                    op = o; cmpVar = a.Name;
                    cmpArg = "(object)(" + bin.Left.WithoutTrivia() + ")"; boundArg = "(object)(" + bin.Right.WithoutTrivia() + ")";
                }
                else if (b != null)
                {
                    op = o == "<" ? ">" : o == ">" ? "<" : o == "<=" ? ">=" : o == ">=" ? "<=" : o; cmpVar = b.Name;
                    cmpArg = "(object)(" + bin.Right.WithoutTrivia() + ")"; boundArg = "(object)(" + bin.Left.WithoutTrivia() + ")";
                }
            }

            string flags = (exits ? "E" : "") + (pure ? "P" : "") + (w ? "W" : "") + (outside ? "O" : "") + (calls ? "C" : "")
                           + (nondet == true ? "N" : "") + (fields ? "F" : "") + (constant ? "T" : "");
            string info = kind + "|" + header + "|" + end + "|" + op + "|" + cmpVar + "|" + flags + "|" + string.Join(",", vars);
            return Quote(info) + ", " + cmpArg + ", " + boundArg;
        }

        /// <summary>המשתנה (מקומי / פרמטר / מאפיין) שהביטוי הוא בדיוק הוא — או null</summary>
        ISymbol VarOf(ExpressionSyntax e)
        {
            if (e is MemberAccessExpressionSyntax ma && ma.Expression is ThisExpressionSyntax) e = ma.Name;
            if (!(e is IdentifierNameSyntax)) return null;
            ISymbol s = model.GetSymbolInfo(e).Symbol;
            return s is ILocalSymbol || s is IParameterSymbol || s is IFieldSymbol f && !f.IsConst ? s : null;
        }

        bool Numeric(ExpressionSyntax e)
        {
            ITypeSymbol t = model.GetTypeInfo(e).Type;
            if (t == null) return false;
            switch (t.SpecialType)
            {
                case SpecialType.System_Char: case SpecialType.System_SByte: case SpecialType.System_Byte: case SpecialType.System_Int16:
                case SpecialType.System_UInt16: case SpecialType.System_Int32: case SpecialType.System_UInt32: case SpecialType.System_Int64:
                case SpecialType.System_UInt64: case SpecialType.System_Single: case SpecialType.System_Double: case SpecialType.System_Decimal:
                    return true;
                default:
                    return false;
            }
        }

        /// <summary>ביטוי שאפשר לחשב עוד פעם בלי לשנות כלום: מספר, משתנה, arr.Length, s.Count, n - 1 (בלי קריאות לפעולות)</summary>
        static bool Simple(ExpressionSyntax e)
        {
            return e.DescendantNodesAndSelf().All(x => x is ExpressionSyntax == false || x is IdentifierNameSyntax || x is LiteralExpressionSyntax
                || x is MemberAccessExpressionSyntax || x is ParenthesizedExpressionSyntax || x is ThisExpressionSyntax || x is PredefinedTypeSyntax
                || x is PrefixUnaryExpressionSyntax pu && (pu.IsKind(SyntaxKind.UnaryMinusExpression) || pu.IsKind(SyntaxKind.UnaryPlusExpression))
                || x is BinaryExpressionSyntax b && (b.IsKind(SyntaxKind.AddExpression) || b.IsKind(SyntaxKind.SubtractExpression)
                    || b.IsKind(SyntaxKind.MultiplyExpression) || b.IsKind(SyntaxKind.DivideExpression)));
        }

        static int Line(SyntaxToken t)
        {
            return t.GetLocation().GetLineSpan().StartLinePosition.Line + 1;
        }

        static string Quote(string s)
        {
            return SyntaxFactory.Literal(s).ToString();
        }
    }
}

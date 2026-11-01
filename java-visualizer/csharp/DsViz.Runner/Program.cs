using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Reflection;
using System.Text;
using System.Threading;
using System.Xml.Linq;
using Microsoft.CodeAnalysis;
using Microsoft.CodeAnalysis.CSharp;
using Microsoft.CodeAnalysis.CSharp.Syntax;
using Unit4.CollectionsLib;

namespace DsViz
{
    /// <summary>
    /// dsviz-cs — הרצה ויזואלית לפרויקט C#. עובד מכל סביבה (Visual Studio, VS Code, Rider, שורת פקודה):
    ///   dsviz-cs &lt;תיקיית פרויקט | קובץ .csproj | קובץ .cs&gt; [--trace קובץ] [--input קובץ] [-- ארגומנטים לתוכנית]
    /// 1. מקמפל את הקוד של התלמיד (Roslyn) — שגיאות מוצגות כמו ב-Visual Studio.
    /// 2. מכניס לפני כל שורה דיווח על המשתנים והמבנים (Instrumenter), ומריץ את התוכנית.
    /// 3. פותח את אותו חלון ויזואלי של Eclipse / VS Code (Java, TraceViewer) עם ההרצה — בסוף, או כבר כשהתוכנית
    ///    מחכה לקלט מהמקלדת (Keyboard / LiveViewer), כדי שאפשר יהיה להקליד בו.
    /// </summary>
    internal static class Program
    {
        static int Main(string[] args)
        {
            try { Console.OutputEncoding = new UTF8Encoding(false); } catch (Exception) { }
            if (args.Length > 0 && args[0] == "--check") return Check(args.Skip(1).ToArray());
            if (args.Length > 1 && args[0] == "--check-project") return CheckProject(args[1]);
            if (args.Length > 1 && args[0] == "--ensure-profile") return EnsureProfile(args[1]);
            string target = null, traceOut = null, inputFile = null;
            var progArgs = new List<string>();
            for (int i = 0; i < args.Length; i++)
            {
                if (args[i] == "--") { progArgs.AddRange(args.Skip(i + 1)); break; }
                if (args[i] == "--trace" && i + 1 < args.Length) traceOut = args[++i];
                else if (args[i] == "--input" && i + 1 < args.Length) inputFile = args[++i];   // קלט מהמקלדת מקובץ (בדיקות): שורה בכל פעם שהתוכנית מחכה
                else if (args[i] == "--version") { Console.WriteLine("dsviz-cs 1.0.1"); return 0; }
                else if (target == null) target = args[i];
            }
            if (target == null)
            {
                Console.Error.WriteLine("שימוש: dsviz-cs <תיקיית פרויקט | קובץ .csproj | קובץ .cs> [--trace קובץ] [--input קובץ] [-- ארגומנטים]");
                return 2;
            }

            var project = ProjectInfo.Load(target);
            if (project.Files.Count == 0)
            {
                Console.Error.WriteLine("לא נמצאו קובצי .cs ב-" + target);
                return 2;
            }
            Console.WriteLine("הרצה ויזואלית: מקמפל את " + project.Name + "...");

            // --- 1. קומפילציה של הקוד המקורי (שגיאות מוצגות בלי הכנסת ההוקים)
            var parse = new CSharpParseOptions(project.LangVersion);
            var trees = new List<SyntaxTree>();
            var plain = new Dictionary<SyntaxTree, SyntaxTree>();   // קובץ עם top-level שעטפנו ב-Main — והמקור, אם העטיפה לא מתקמפלת
            bool topLevel = false;
            foreach (string f in project.Files)
            {
                string text = SourceReader.Read(f);
                Recorder.Sources[Path.GetFileName(f)] = text;
                SyntaxTree tree = CSharpSyntaxTree.ParseText(text, parse, f);
                string wrapped = TopLevel.Wrap(tree);
                if (wrapped != null)
                {
                    SyntaxTree w = CSharpSyntaxTree.ParseText(wrapped, parse, f);
                    plain[w] = tree;
                    tree = w;
                    topLevel = true;
                }
                trees.Add(tree);
            }
            // כמו Directory.Build.props של תיקיית התרגילים: פעולות העזר מהספרייה (Size / Duplicate) בלי Helpers. — תמיד
            trees.Add(CSharpSyntaxTree.ParseText("global using static global::Unit4.CollectionsLib.Helpers; " + (project.ImplicitUsings ?
                    "global using global::System; global using global::System.Collections.Generic; global using global::System.IO; " +
                    "global using global::System.Linq; global using global::System.Net.Http; global using global::System.Threading; " +
                    "global using global::System.Threading.Tasks;" : ""), parse, "GlobalUsings.g.cs"));

            // איזה Main מריצים: קובץ מסוים מתוך פרויקט — ה-Main שבו (ושאר הקבצים בפרויקט נכללים);
            // אחרת ה-StartupObject של הפרויקט, ואם אין — מה שהמהדר בוחר (כמו ב-Visual Studio)
            string mainType = project.StartupObject ?? (topLevel ? "Program" : null);   // כמו ב-Visual Studio: ה-top-level הוא ה-Main
            if (project.StartFile != null)
            {
                SyntaxTree start = trees.FirstOrDefault(t => string.Equals(Path.GetFullPath(t.FilePath), project.StartFile, StringComparison.OrdinalIgnoreCase));
                string inFile = start == null ? null : MainTypeIn(start);
                if (inFile != null)
                {
                    mainType = inFile;
                    // קובץ אחר עם "top-level statements" (כמו Program.cs של תבנית חדשה: Console.WriteLine("Hello, World!");) היה דורס את ה-Main הזה
                    trees.RemoveAll(t => t != start && t.GetRoot() is CompilationUnitSyntax cu && cu.Members.OfType<GlobalStatementSyntax>().Any());
                }
            }

            var refs = MetadataReferences();
            var options = new CSharpCompilationOptions(OutputKind.ConsoleApplication, optimizationLevel: OptimizationLevel.Debug,
                allowUnsafe: project.AllowUnsafe, nullableContextOptions: NullableContextOptions.Disable, mainTypeName: mainType);
            var original = CSharpCompilation.Create("StudentProgram", trees, refs, options);
            var errors = original.GetDiagnostics().Where(d => d.Severity == DiagnosticSeverity.Error).ToList();
            if (errors.Count > 0 && plain.Count > 0)
            {
                // העטיפה של ה-top-level לא הצליחה (למשל פעולה מקומית שמשתמשת במשתנה של התוכנית) — מריצים כמו שהוא, בלי שורה-שורה שם
                trees = trees.Select(t => plain.TryGetValue(t, out SyntaxTree o) ? o : t).ToList();
                if (mainType == "Program" && project.StartupObject == null) mainType = null;
                options = options.WithMainTypeName(mainType);
                original = CSharpCompilation.Create("StudentProgram", trees, refs, options);
                errors = original.GetDiagnostics().Where(d => d.Severity == DiagnosticSeverity.Error).ToList();
            }
            if (errors.Count > 0)
            {
                foreach (Diagnostic d in errors) Console.Error.WriteLine(Format(d));
                foreach (string hint in Hints(errors, project)) Console.Error.WriteLine(hint);
                Console.Error.WriteLine("הקומפילציה נכשלה — " + errors.Count + " שגיאות. מתקנים ומריצים שוב.");
                return 1;
            }

            // פרויקט של Visual Studio: מוסיפים לו (פעם אחת) את "הרצה ויזואלית" לתפריט שליד כפתור ההפעלה
            if (traceOut == null && LaunchProfile.Ensure(project))
                Console.WriteLine("נוסף לפרויקט \"הרצה ויזואלית\": מעכשיו אפשר להריץ ישר מ-Visual Studio — בחץ שליד כפתור ההפעלה הירוק בוחרים \"הרצה ויזואלית\".");

            // --- 2. הכנסת ההוקים
            var instrumented = new List<SyntaxTree>();
            foreach (SyntaxTree t in trees)
            {
                if (t.FilePath == "GlobalUsings.g.cs") { instrumented.Add(t); continue; }
                SyntaxNode root = new Instrumenter(original.GetSemanticModel(t), Path.GetFileName(t.FilePath)).Visit(t.GetRoot());
                instrumented.Add(CSharpSyntaxTree.Create((Microsoft.CodeAnalysis.CSharp.CSharpSyntaxNode)root, parse, t.FilePath, Encoding.UTF8));
            }
            var compilation = CSharpCompilation.Create("StudentProgram", instrumented, refs, options);
            Assembly assembly;
            using (var ms = new MemoryStream())
            {
                var result = compilation.Emit(ms);
                if (!result.Success)
                {
                    Console.Error.WriteLine("שגיאה פנימית בהכנת ההרצה הוויזואלית (נא לדווח):");
                    foreach (Diagnostic d in result.Diagnostics.Where(d => d.Severity == DiagnosticSeverity.Error).Take(10))
                        Console.Error.WriteLine("  " + d);
                    return 3;
                }
                assembly = Assembly.Load(ms.ToArray());
            }
            MethodInfo entry = assembly.EntryPoint;
            if (entry == null)
            {
                Console.Error.WriteLine("לא נמצאה הפעולה Main. כותבים: static void Main(string[] args) — כמו בתבנית.");
                return 2;
            }

            // --- 3. הרצה
            Recorder.Active = true;
            TextWriter console = Console.Out;
            var tee = new TeeWriter(console);
            Console.SetOut(tee);
            // קלט מהמקלדת: Console.In מתחלף בקורא שמחכה גם להקלדה בחלון (שנפתח כשהתוכנית מחכה) — רק בלי --trace
            Keyboard keyboard = Keyboard.Install(console, traceOut == null ? (Func<Keyboard, LiveViewer>)(k => LiveViewer.Open(k, console)) : null, inputFile);
            int exit = 0;
            var thread = new Thread(() => exit = RunProgram(entry, progArgs.ToArray()), 256 * 1024 * 1024);
            thread.IsBackground = true;   // לולאה שלא נגמרת: החוט של התלמיד נשאר תקוע — והתהליך עדיין יכול להסתיים
            thread.Start();
            // מחכים לסוף התוכנית — או לעצירה בלולאה שלא נגמרת (Recorder.LoopLine / LoopGuard)
            while (!thread.Join(50))
                if (Recorder.LoopStop.WaitOne(0)) break;
            Console.Out.Flush();
            if (Recorder.LoopStopped) exit = 1;
            else Recorder.Finish();

            // החלון כבר פתוח (התוכנית חיכתה לקלט): מקבל את שאר הצעדים, ומחכים שייסגר
            if (keyboard.Viewer != null && keyboard.Viewer.Alive)
            {
                keyboard.Viewer.Finish();
                return exit;
            }

            // --- 4. החלון
            string trace = traceOut ?? Path.Combine(Path.GetTempPath(), "dsviz-" + Process.GetCurrentProcess().Id + ".trace");
            TraceWriter.Write(trace);
            if (traceOut != null) return exit;
            try { ViewerLauncher.Show(trace); }
            finally { try { File.Delete(trace); } catch (Exception) { } }
            return exit;
        }

        /// <summary>כמו ב-Directory.Build.props: Size(x) / Duplicate(x) מהספרייה בלי Helpers.</summary>
        static SyntaxTree HelpersUsing(CSharpParseOptions parse)
        {
            return CSharpSyntaxTree.ParseText("global using static global::Unit4.CollectionsLib.Helpers;", parse, "HelpersUsing.g.cs");
        }

        /// <summary>בדיקה (לבדיקות אוטומטיות): מקמפל כל קובץ .cs בנפרד מול הספרייה ומדפיס שגיאות. קוד יציאה 1 אם יש</summary>
        static int Check(string[] files)
        {
            var refs = MetadataReferences();
            var parse = new CSharpParseOptions(LanguageVersion.Latest);
            var options = new CSharpCompilationOptions(OutputKind.ConsoleApplication, nullableContextOptions: NullableContextOptions.Disable);
            int bad = 0;
            foreach (string f in files)
            {
                var tree = CSharpSyntaxTree.ParseText(SourceReader.Read(f), parse, f);
                var errors = CSharpCompilation.Create("Check", new[] { tree, HelpersUsing(parse) }, refs, options)
                    .GetDiagnostics().Where(d => d.Severity == DiagnosticSeverity.Error).ToList();
                if (errors.Count == 0) continue;
                bad++;
                Console.Error.WriteLine(f);
                foreach (Diagnostic d in errors.Take(3)) Console.Error.WriteLine("  " + Format(d));
            }
            Console.WriteLine("נבדקו " + files.Length + " קבצים, " + bad + " עם שגיאות");
            return bad == 0 ? 0 : 1;
        }

        /// בדיקת קומפילציה של פרויקט שלם (כל הקבצים יחד) — בשביל "שליחת תרגיל" ב-Eclipse:
        /// מדפיס כל שגיאה בשורה "קובץ(שורה,עמודה): error CSxxxx: הודעה", ובסוף "errors: N"
        static int CheckProject(string target)
        {
            var project = ProjectInfo.Load(target);
            var parse = new CSharpParseOptions(project.LangVersion);
            var trees = project.Files.Select(f => CSharpSyntaxTree.ParseText(SourceReader.Read(f), parse, f)).ToList();
            trees.Add(HelpersUsing(parse));
            var options = new CSharpCompilationOptions(OutputKind.ConsoleApplication, nullableContextOptions: NullableContextOptions.Disable);
            var errors = CSharpCompilation.Create(project.Name, trees, MetadataReferences(), options)
                .GetDiagnostics().Where(d => d.Severity == DiagnosticSeverity.Error).ToList();
            foreach (Diagnostic d in errors.Take(30)) Console.WriteLine(Format(d));
            Console.WriteLine("errors: " + errors.Count);
            return errors.Count == 0 ? 0 : 1;
        }

        /// <summary>
        /// מצב קטן בשביל DSViz.targets (MSBuild ImportBefore/ImportAfter ברמת המשתמש — ראו installer/DSViz.targets):
        /// מוסיף את פרופיל "הרצה ויזואלית" לפרויקט אם חסר, בלי לסרוק את כל קבצי ה-.cs (ProjectInfo.LoadProjectOnly —
        /// מהיר, כדי שגם ירוץ בבנייה של עיצוב-זמן ב-Visual Studio בלי לעכב את פתיחת הפרויקט). לא נכשל אף פעם.
        /// </summary>
        static int EnsureProfile(string dir)
        {
            try { LaunchProfile.Ensure(ProjectInfo.LoadProjectOnly(dir)); } catch (Exception) { }
            return 0;
        }

        /// <summary>השם המלא של המחלקה בקובץ שיש בה static Main (או null)</summary>
        static string MainTypeIn(SyntaxTree tree)
        {
            foreach (var m in tree.GetRoot().DescendantNodes().OfType<MethodDeclarationSyntax>())
            {
                if (m.Identifier.Text != "Main" || !m.Modifiers.Any(SyntaxKind.StaticKeyword)) continue;
                var names = new List<string>();
                for (SyntaxNode n = m.Parent; n != null; n = n.Parent)
                {
                    if (n is BaseTypeDeclarationSyntax t) names.Insert(0, t.Identifier.Text);
                    else if (n is BaseNamespaceDeclarationSyntax ns) names.Insert(0, ns.Name.ToString());
                }
                if (names.Count > 0) return string.Join(".", names);
            }
            return null;
        }

        /// <summary>הסבר בעברית לשגיאות שקורות כשמריצים פרויקט רגיל של Visual Studio</summary>
        static IEnumerable<string> Hints(List<Diagnostic> errors, ProjectInfo project)
        {
            var ids = new HashSet<string>(errors.Select(e => e.Id));
            if (ids.Contains("CS0017"))
                yield return "← בפרויקט יש יותר מפעולת Main אחת. מריצים את הקובץ שרוצים: dsviz-cs \"<הקובץ>.cs\" — ירוץ ה-Main שבו (ושאר הקבצים בפרויקט נכללים).";
            if (errors.Any(e => e.Id == "CS0104" && e.GetMessage().Contains("Unit4.CollectionsLib") && e.GetMessage().Contains("System.Collections.Generic")))
                yield return "← Stack / Queue קיימים גם ב-‎.NET (System.Collections.Generic) וגם בספרייה. בקובץ ה-‎.csproj משנים ל-<ImplicitUsings>disable</ImplicitUsings>,"
                    + " או מוחקים את השורה using System.Collections.Generic; (אותה שגיאה מופיעה גם ב-Visual Studio).";
            if (errors.Any(e => e.Id == "CS0103" && e.GetMessage().Contains("'Console'")))
                yield return "← חסרה בראש הקובץ השורה: using System;";
            if (ids.Contains("CS0246") && errors.Any(e => e.GetMessage().Contains("'Unit4'")))
                yield return "← הספרייה נקראת Unit4.CollectionsLib: בראש הקובץ — using Unit4.CollectionsLib;";
        }

        static int RunProgram(MethodInfo entry, string[] args)
        {
            try
            {
                object r = entry.Invoke(null, entry.GetParameters().Length == 0 ? null : new object[] { args });
                var task = r as System.Threading.Tasks.Task;
                if (task != null) task.GetAwaiter().GetResult();
                return r is int ? (int)r : 0;
            }
            catch (TargetInvocationException e)
            {
                Recorder.Fault(e.InnerException);
                Console.Error.WriteLine("Unhandled exception. " + e.InnerException);
                return 1;
            }
        }

        static string Format(Diagnostic d)
        {
            var span = d.Location.GetLineSpan();
            string where = d.Location.IsInSource
                ? Path.GetFileName(span.Path) + "(" + (span.StartLinePosition.Line + 1) + "," + (span.StartLinePosition.Character + 1) + ")"
                : "";
            return where + ": error " + d.Id + ": " + d.GetMessage();
        }

        static List<MetadataReference> MetadataReferences()
        {
            var paths = ((string)AppContext.GetData("TRUSTED_PLATFORM_ASSEMBLIES")).Split(Path.PathSeparator);
            var list = new List<MetadataReference>();
            foreach (string p in paths)
            {
                string name = Path.GetFileName(p);
                // לא מוסיפים את ה-Roslyn וה-runner עצמם (הספרייה Unit4.CollectionsLib כן נשארת)
                if (name.StartsWith("Microsoft.CodeAnalysis") || name == "dsviz-cs.dll") continue;
                list.Add(MetadataReference.CreateFromFile(p));
            }
            return list;
        }
    }

    /// <summary>מעתיק כל הדפסה גם לקונסול וגם להקלטה</summary>
    internal sealed class TeeWriter : TextWriter
    {
        readonly TextWriter original;
        public TeeWriter(TextWriter original) { this.original = original; }
        public override Encoding Encoding { get { return original.Encoding; } }
        public override void Write(char value) { original.Write(value); Recorder.OnOutput(value.ToString()); }
        public override void Write(string value) { original.Write(value); if (value != null) Recorder.OnOutput(value); }
        public override void Write(char[] buffer, int index, int count)
        {
            original.Write(buffer, index, count);
            Recorder.OnOutput(new string(buffer, index, count));
        }
        public override void WriteLine(string value) { original.WriteLine(value); Recorder.OnOutput((value ?? "") + "\n"); }
        public override void WriteLine() { original.WriteLine(); Recorder.OnOutput("\n"); }
        public override void Flush() { original.Flush(); }
    }

    internal static class SourceReader
    {
        /// <summary>UTF-8 (עם או בלי BOM), ואם זה לא UTF-8 תקין — windows-1255 (עברית ישנה)</summary>
        public static string Read(string path)
        {
            byte[] b = File.ReadAllBytes(path);
            try
            {
                string t = new UTF8Encoding(false, true).GetString(b);
                return t.Length > 0 && t[0] == '﻿' ? t.Substring(1) : t;
            }
            catch (Exception)
            {
                try { return Encoding.GetEncoding(1255).GetString(b); }
                catch (Exception) { return Encoding.UTF8.GetString(b); }
            }
        }
    }

    internal sealed class ProjectInfo
    {
        public string Name, Dir;
        public List<string> Files = new List<string>();
        public bool ImplicitUsings, AllowUnsafe;
        public string StartFile;       // הקובץ שביקשו להריץ (כשהוא חלק מפרויקט) — ה-Main שבו
        public string StartupObject;   // <StartupObject> מה-csproj
        public string Csproj;          // קובץ הפרויקט (או null)
        public bool SdkStyle;          // פרויקט ‎.NET חדש (<Project Sdk=...>) — יש לו launchSettings.json
        public LanguageVersion LangVersion = LanguageVersion.Latest;

        public static ProjectInfo Load(string target)
        {
            var p = new ProjectInfo();
            string csproj = null;
            if (File.Exists(target) && target.EndsWith(".cs", StringComparison.OrdinalIgnoreCase))
            {
                // קובץ בתוך פרויקט (למשל מ-Visual Studio): כל הפרויקט עם ההגדרות שלו, ומריצים את ה-Main שבקובץ
                string owner = FindProject(Path.GetDirectoryName(Path.GetFullPath(target)));
                if (owner != null)
                {
                    ProjectInfo whole = Load(owner);
                    whole.StartFile = Path.GetFullPath(target);
                    if (!whole.Files.Any(f => string.Equals(f, whole.StartFile, StringComparison.OrdinalIgnoreCase))) whole.Files.Add(whole.StartFile);
                    return whole;
                }
                p.Dir = Path.GetDirectoryName(Path.GetFullPath(target));
                p.Name = Path.GetFileName(target);
                p.Files.Add(Path.GetFullPath(target));
                return p;
            }
            if (File.Exists(target) && target.EndsWith(".csproj", StringComparison.OrdinalIgnoreCase)) csproj = Path.GetFullPath(target);
            else if (Directory.Exists(target))
                csproj = Directory.GetFiles(target, "*.csproj").FirstOrDefault();
            p.Dir = csproj != null ? Path.GetDirectoryName(csproj) : Path.GetFullPath(target);
            p.Name = csproj != null ? Path.GetFileNameWithoutExtension(csproj) : Path.GetFileName(p.Dir.TrimEnd(Path.DirectorySeparatorChar));
            if (csproj != null)
            {
                try
                {
                    XDocument x = XDocument.Load(csproj);
                    p.Csproj = csproj;
                    p.SdkStyle = x.Root != null && x.Root.Attribute("Sdk") != null;
                    string V(string n) { return x.Descendants().Where(e => e.Name.LocalName == n).Select(e => e.Value.Trim()).FirstOrDefault(); }
                    string iu = V("ImplicitUsings"), un = V("AllowUnsafeBlocks"), lv = V("LangVersion"), so = V("StartupObject");
                    if (!string.IsNullOrEmpty(so)) p.StartupObject = so;
                    p.ImplicitUsings = iu != null && (iu.Equals("enable", StringComparison.OrdinalIgnoreCase) || iu.Equals("true", StringComparison.OrdinalIgnoreCase));
                    p.AllowUnsafe = un != null && un.Equals("true", StringComparison.OrdinalIgnoreCase);
                    LanguageVersion parsed;
                    if (lv != null && LanguageVersionFacts.TryParse(lv, out parsed)) p.LangVersion = parsed;
                }
                catch (Exception) { }
            }
            string[] skip = { "bin", "obj", "node_modules" };
            foreach (string f in Directory.EnumerateFiles(p.Dir, "*.cs", SearchOption.AllDirectories))
            {
                string rel = f.Substring(p.Dir.Length).TrimStart(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
                string[] parts = rel.Split(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
                if (parts.Take(parts.Length - 1).Any(d => skip.Contains(d) || d.StartsWith("."))) continue;
                if (f.EndsWith(".g.cs") || f.EndsWith(".designer.cs", StringComparison.OrdinalIgnoreCase) || f.EndsWith("AssemblyInfo.cs")) continue;
                p.Files.Add(f);
            }
            p.Files.Sort(StringComparer.OrdinalIgnoreCase);
            return p;
        }

        /// <summary>הפרויקט (.csproj) שהתיקייה שייכת אליו — בתיקייה עצמה או עד 4 תיקיות למעלה</summary>
        static string FindProject(string dir)
        {
            for (int i = 0; dir != null && i < 5; i++, dir = Path.GetDirectoryName(dir))
            {
                string[] found;
                try { found = Directory.GetFiles(dir, "*.csproj"); }
                catch (Exception) { return null; }
                if (found.Length == 1) return found[0];
                if (found.Length > 1) return null;   // כמה פרויקטים באותה תיקייה — רק הקובץ
            }
            return null;
        }

        /// <summary>
        /// רק קריאת ה-csproj של תיקייה (Dir, Csproj, SdkStyle) — בלי לסרוק את כל קבצי ה-.cs שבפרויקט.
        /// בשביל --ensure-profile (DSViz.targets): רץ בכל פתיחה/בנייה של פרויקט ב-Visual Studio, וצריך להיות מהיר.
        /// </summary>
        public static ProjectInfo LoadProjectOnly(string dir)
        {
            var p = new ProjectInfo { Dir = Path.GetFullPath(dir) };
            string csproj = null;
            try { csproj = Directory.GetFiles(p.Dir, "*.csproj").FirstOrDefault(); } catch (Exception) { }
            if (csproj == null) return p;
            p.Csproj = csproj;
            p.Name = Path.GetFileNameWithoutExtension(csproj);
            try
            {
                XDocument x = XDocument.Load(csproj);
                p.SdkStyle = x.Root != null && x.Root.Attribute("Sdk") != null;
            }
            catch (Exception) { }
            return p;
        }
    }

    /// <summary>
    /// "top-level statements" (תבנית חדשה של Visual Studio: הקוד ישר בקובץ, בלי class ו-Main) — עוטפים ב-Main רגיל,
    /// והפעולות שכתובות אחרי הקוד הופכות לפעולות של המחלקה. בלי לשנות את מספרי השורות (מוסיפים רק בתוך שורות קיימות),
    /// כך שההרצה הוויזואלית עוקבת אחרי הקוד והפעולות שורה-שורה כמו בכל תוכנית.
    /// </summary>
    internal static class TopLevel
    {
        public static string Wrap(SyntaxTree tree)
        {
            if (!(tree.GetRoot() is CompilationUnitSyntax root)) return null;
            var globals = root.Members.OfType<GlobalStatementSyntax>().ToList();
            if (globals.Count == 0) return null;
            var funcs = globals.Where(g => g.Statement is LocalFunctionStatementSyntax).ToList();
            var stmts = globals.Where(g => !(g.Statement is LocalFunctionStatementSyntax)).ToList();
            if (stmts.Count == 0) return null;
            // רק כשהפעולות אחרי הקוד (כך בדרך כלל) — אחרת אי אפשר בלי להזיז שורות
            if (funcs.Count > 0 && funcs.Min(g => g.SpanStart) < stmts.Max(g => g.Span.End)) return null;
            bool isAsync = stmts.Any(g => Body(g).OfType<AwaitExpressionSyntax>().Any());
            bool returnsInt = stmts.Any(g => Body(g).OfType<ReturnStatementSyntax>().Any(r => r.Expression != null));
            string sig = "static " + (isAsync ? "async System.Threading.Tasks.Task" + (returnsInt ? "<int>" : "") : returnsInt ? "int" : "void")
                + " Main(string[] args) { ";
            string text = tree.GetText().ToString();
            var inserts = new List<KeyValuePair<int, string>>();
            inserts.Add(new KeyValuePair<int, string>(stmts[0].SpanStart, "partial class Program { " + sig));
            inserts.Add(new KeyValuePair<int, string>(stmts[stmts.Count - 1].Span.End, " }"));
            foreach (var g in funcs)
            {
                var lf = (LocalFunctionStatementSyntax)g.Statement;
                if (!lf.Modifiers.Any(SyntaxKind.StaticKeyword)) inserts.Add(new KeyValuePair<int, string>(g.SpanStart, "static "));
            }
            inserts.Add(new KeyValuePair<int, string>(globals[globals.Count - 1].Span.End, " }"));
            var b = new StringBuilder(text);
            foreach (var ins in inserts.OrderByDescending(i => i.Key)) b.Insert(ins.Key, ins.Value);
            return b.ToString();
        }

        /// <summary>הצמתים של משפט — בלי מה שבתוך למדות ופעולות מקומיות (שם return / await שייכים להן)</summary>
        static IEnumerable<SyntaxNode> Body(GlobalStatementSyntax g)
        {
            return g.DescendantNodes(n => !(n is LambdaExpressionSyntax) && !(n is AnonymousMethodExpressionSyntax) && !(n is LocalFunctionStatementSyntax));
        }
    }

    /// <summary>פרופיל "הרצה ויזואלית" ב-Properties\launchSettings.json של פרויקט — כמו בפרויקטים של התבנית</summary>
    internal static class LaunchProfile
    {
        const string Name = "הרצה ויזואלית";

        public static bool Ensure(ProjectInfo p)
        {
            if (p.Csproj == null || !p.SdkStyle) return false;
            try
            {
                string file = Path.Combine(p.Dir, "Properties", "launchSettings.json");
                var root = File.Exists(file)
                    ? System.Text.Json.Nodes.JsonNode.Parse(File.ReadAllText(file), null,
                        new System.Text.Json.JsonDocumentOptions { CommentHandling = System.Text.Json.JsonCommentHandling.Skip, AllowTrailingCommas = true }) as System.Text.Json.Nodes.JsonObject
                    : new System.Text.Json.Nodes.JsonObject();
                if (root == null) return false;
                var profiles = root["profiles"] as System.Text.Json.Nodes.JsonObject;
                if (profiles == null) { profiles = new System.Text.Json.Nodes.JsonObject(); root["profiles"] = profiles; }
                if (profiles.ContainsKey(Name)) return false;
                // ההרצה הרגילה נשארת ראשונה (F5 כמו קודם)
                if (!profiles.Any(kv => (string)kv.Value?["commandName"] == "Project"))
                    profiles.Add("הרצה רגילה", new System.Text.Json.Nodes.JsonObject { ["commandName"] = "Project" });
                profiles.Add(Name, new System.Text.Json.Nodes.JsonObject
                {
                    ["commandName"] = "Executable",
                    // Mac (Rider): הנתיב המלא של dsviz-cs שרץ עכשיו (~/Library/Application Support/DSViz/cs/dsviz-cs)
                    ["executablePath"] = OperatingSystem.IsWindows() || string.IsNullOrEmpty(Environment.ProcessPath)
                        || Path.GetFileName(Environment.ProcessPath) != "dsviz-cs"
                        ? "%LOCALAPPDATA%\\DSViz\\cs\\dsviz-cs.exe" : Environment.ProcessPath,
                    ["commandLineArgs"] = "\"$(ProjectDir)\"",
                    ["workingDirectory"] = "$(ProjectDir)"
                });
                Directory.CreateDirectory(Path.GetDirectoryName(file));
                File.WriteAllText(file, root.ToJsonString(new System.Text.Json.JsonSerializerOptions
                {
                    WriteIndented = true,
                    Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping
                }), new UTF8Encoding(false));
                return true;
            }
            catch (Exception)
            {
                return false;   // לא חשוב — מריצים בכל מקרה
            }
        }
    }
}

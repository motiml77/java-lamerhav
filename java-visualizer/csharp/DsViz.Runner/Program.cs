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
using Unit4.CollectionsLib;

namespace DsViz
{
    /// <summary>
    /// dsviz-cs — הרצה ויזואלית לפרויקט C#. עובד מכל סביבה (Visual Studio, VS Code, Rider, שורת פקודה):
    ///   dsviz-cs &lt;תיקיית פרויקט | קובץ .csproj | קובץ .cs&gt; [--trace קובץ] [-- ארגומנטים לתוכנית]
    /// 1. מקמפל את הקוד של התלמיד (Roslyn) — שגיאות מוצגות כמו ב-Visual Studio.
    /// 2. מכניס לפני כל שורה דיווח על המשתנים והמבנים (Instrumenter), ומריץ את התוכנית.
    /// 3. פותח את אותו חלון ויזואלי של Eclipse / VS Code (Java, TraceViewer) עם ההרצה.
    /// </summary>
    internal static class Program
    {
        static int Main(string[] args)
        {
            try { Console.OutputEncoding = new UTF8Encoding(false); } catch (Exception) { }
            if (args.Length > 0 && args[0] == "--check") return Check(args.Skip(1).ToArray());
            if (args.Length > 1 && args[0] == "--check-project") return CheckProject(args[1]);
            string target = null, traceOut = null;
            var progArgs = new List<string>();
            for (int i = 0; i < args.Length; i++)
            {
                if (args[i] == "--") { progArgs.AddRange(args.Skip(i + 1)); break; }
                if (args[i] == "--trace" && i + 1 < args.Length) traceOut = args[++i];
                else if (args[i] == "--version") { Console.WriteLine("dsviz-cs 1.0.1"); return 0; }
                else if (target == null) target = args[i];
            }
            if (target == null)
            {
                Console.Error.WriteLine("שימוש: dsviz-cs <תיקיית פרויקט | קובץ .csproj | קובץ .cs> [--trace קובץ] [-- ארגומנטים]");
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
            foreach (string f in project.Files)
            {
                string text = SourceReader.Read(f);
                Recorder.Sources[Path.GetFileName(f)] = text;
                trees.Add(CSharpSyntaxTree.ParseText(text, parse, f));
            }
            if (project.ImplicitUsings)
                trees.Add(CSharpSyntaxTree.ParseText(
                    "global using global::System; global using global::System.Collections.Generic; global using global::System.IO; " +
                    "global using global::System.Linq; global using global::System.Net.Http; global using global::System.Threading; " +
                    "global using global::System.Threading.Tasks;", parse, "GlobalUsings.g.cs"));

            var refs = MetadataReferences();
            var options = new CSharpCompilationOptions(OutputKind.ConsoleApplication, optimizationLevel: OptimizationLevel.Debug,
                allowUnsafe: project.AllowUnsafe, nullableContextOptions: NullableContextOptions.Disable);
            var original = CSharpCompilation.Create("StudentProgram", trees, refs, options);
            var errors = original.GetDiagnostics().Where(d => d.Severity == DiagnosticSeverity.Error).ToList();
            if (errors.Count > 0)
            {
                foreach (Diagnostic d in errors) Console.Error.WriteLine(Format(d));
                Console.Error.WriteLine("הקומפילציה נכשלה — " + errors.Count + " שגיאות. מתקנים ומריצים שוב.");
                return 1;
            }

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
            var tee = new TeeWriter(Console.Out);
            Console.SetOut(tee);
            int exit = 0;
            var thread = new Thread(() => exit = RunProgram(entry, progArgs.ToArray()), 256 * 1024 * 1024);
            thread.Start();
            thread.Join();
            Console.Out.Flush();
            Recorder.Finish();

            // --- 4. החלון
            string trace = traceOut ?? Path.Combine(Path.GetTempPath(), "dsviz-" + Process.GetCurrentProcess().Id + ".trace");
            TraceWriter.Write(trace);
            if (traceOut != null) return exit;
            try { ViewerLauncher.Show(trace); }
            finally { try { File.Delete(trace); } catch (Exception) { } }
            return exit;
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
                var errors = CSharpCompilation.Create("Check", new[] { tree }, refs, options)
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
            var options = new CSharpCompilationOptions(OutputKind.ConsoleApplication, nullableContextOptions: NullableContextOptions.Disable);
            var errors = CSharpCompilation.Create(project.Name, trees, MetadataReferences(), options)
                .GetDiagnostics().Where(d => d.Severity == DiagnosticSeverity.Error).ToList();
            foreach (Diagnostic d in errors.Take(30)) Console.WriteLine(Format(d));
            Console.WriteLine("errors: " + errors.Count);
            return errors.Count == 0 ? 0 : 1;
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
        public LanguageVersion LangVersion = LanguageVersion.Latest;

        public static ProjectInfo Load(string target)
        {
            var p = new ProjectInfo();
            string csproj = null;
            if (File.Exists(target) && target.EndsWith(".cs", StringComparison.OrdinalIgnoreCase))
            {
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
                    string V(string n) { return x.Descendants().Where(e => e.Name.LocalName == n).Select(e => e.Value.Trim()).FirstOrDefault(); }
                    string iu = V("ImplicitUsings"), un = V("AllowUnsafeBlocks"), lv = V("LangVersion");
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
    }
}

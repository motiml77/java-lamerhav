package il.lamerhav.dsviz.cs;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import il.lamerhav.dsviz.ClassGen;

/**
 * "צור מחלקה" בכלי של Visual Studio — הקבצים (CsClassFile): פרויקט ישן (‎.NET Framework‎, כל קובץ רשום ב-csproj) ופרויקט SDK,
 * BOM וסוף שורה כמו בקובץ הנוכחי, ה-namespace שלו, הפתרון שמעל, והמחלקות של הפרויקט (לרשימת הטיפוסים).
 *   java il.lamerhav.dsviz.cs.CsClassFileTest <תיקיית עבודה> <classgen-project>
 */
public class CsClassFileTest {
    static int checks;

    static void ok(boolean b, String what) {
        checks++;
        if (!b) throw new AssertionError(what);
    }

    static void write(Path f, String text, boolean bom) throws Exception {
        Files.createDirectories(f.getParent());
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        if (bom) {
            byte[] x = new byte[b.length + 3];
            x[0] = (byte) 0xEF; x[1] = (byte) 0xBB; x[2] = (byte) 0xBF;
            System.arraycopy(b, 0, x, 3, b.length);
            b = x;
        }
        Files.write(f, b);
    }

    public static void main(String[] a) throws Exception {
        Path w = new File(a[0]).toPath().toAbsolutePath();
        // --- פרויקט ישן: csproj בלי Sdk, CRLF ו-BOM; Program.cs עם namespace { }
        Path old = w.resolve("Sln/OldApp");
        write(w.resolve("Sln/OldApp.sln"), "Microsoft Visual Studio Solution File, Format Version 12.00\r\n", true);
        String proj = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n<Project ToolsVersion=\"15.0\" xmlns=\"http://schemas.microsoft.com/developer/msbuild/2003\">\r\n"
                + "  <ItemGroup>\r\n    <Reference Include=\"System\" />\r\n  </ItemGroup>\r\n  <ItemGroup>\r\n    <Compile Include=\"Program.cs\" />\r\n"
                + "    <Compile Include=\"Properties\\AssemblyInfo.cs\" />\r\n  </ItemGroup>\r\n</Project>\r\n";
        write(old.resolve("OldApp.csproj"), proj, true);
        write(old.resolve("Program.cs"), "using System;\r\n\r\nnamespace OldApp\r\n{\r\n    class Program\r\n    {\r\n        static void Main() { }\r\n    }\r\n}\r\n", true);
        String[] ns = CsClassFile.namespace(old.resolve("Program.cs"));
        ok(ns[0].equals("OldApp") && ns[1].equals("block"), ns[0] + " " + ns[1]);
        ok(CsClassFile.projectDir(old.resolve("Models")) != null && CsClassFile.projectDir(old).equals(old), "projectDir");
        ok(!CsClassFile.sdkStyle(old.resolve("OldApp.csproj")), "פרויקט ישן");
        ok(CsClassFile.solution(old).getFileName().toString().equals("OldApp.sln"), "הפתרון שמעל הפרויקט");
        ok(CsClassFile.taken(old).containsKey("Program"), "Program.cs תפוס");
        List<ClassGen.Known> known = CsClassFile.known(old);
        ok(known.size() == 1 && known.get(0).name.equals("Program") && known.get(0).pkg.equals("OldApp"), "סריקה: " + known.size());

        ClassGen.Options o = new ClassGen.Options();
        o.lang = ClassGen.CS;
        o.className = "Student";
        o.namespace = ns[0];
        o.toString = true;
        o.fields.add(new ClassGen.Field("string", "name"));
        o.fields.add(new ClassGen.Field("int", "grade"));
        o.fields.add(new ClassGen.Field("bool", "active"));
        String code = ClassGen.generate(o);
        Path created = CsClassFile.write(old, "Student.cs", code, old.resolve("Program.cs"));
        byte[] raw = Files.readAllBytes(created);
        String text = CsClassFile.read(created);
        ok((raw[0] & 0xFF) == 0xEF && (raw[1] & 0xFF) == 0xBB && (raw[2] & 0xFF) == 0xBF, "BOM");
        ok(text.startsWith("namespace OldApp\r\n{\r\n    public class Student\r\n    {\r\n") && !text.replace("\r\n", "").contains("\n"), "CRLF כמו Program.cs");
        try {
            CsClassFile.write(old, "Student.cs", code, null);
            ok(false, "קובץ קיים — אמור להיכשל");
        } catch (java.io.IOException e) {
            ok(e.getMessage().contains("Student.cs"), e.getMessage());
        }
        ok(CsClassFile.addCompile(old.resolve("OldApp.csproj"), created), "נרשם בפרויקט");
        String p2 = CsClassFile.read(old.resolve("OldApp.csproj"));
        ok(p2.contains("    <Compile Include=\"Properties\\AssemblyInfo.cs\" />\r\n    <Compile Include=\"Student.cs\" />\r\n  </ItemGroup>"), p2);
        byte[] praw = Files.readAllBytes(old.resolve("OldApp.csproj"));
        ok((praw[0] & 0xFF) == 0xEF && !p2.replace("\r\n", "").contains("\n"), "BOM ו-CRLF נשמרו ב-csproj");
        ok(!CsClassFile.addCompile(old.resolve("OldApp.csproj"), created), "פעם שנייה — כבר רשום");
        // קובץ בתיקייה פנימית — עם \ ; csproj בלי Compile בכלל — ItemGroup חדש
        Path car = CsClassFile.write(Files.createDirectories(old.resolve("Models")), "Car.cs", "public class Car\n{\n}\n", null);
        ok(CsClassFile.addCompile(old.resolve("OldApp.csproj"), car) && CsClassFile.read(old.resolve("OldApp.csproj")).contains("<Compile Include=\"Models\\Car.cs\" />"), "תיקייה פנימית");
        write(w.resolve("Bare/Bare.csproj"), "<Project ToolsVersion=\"15.0\">\n  <PropertyGroup />\n</Project>\n", false);
        Path bare = CsClassFile.write(w.resolve("Bare"), "Box.cs", "public class Box\n{\n}\n", null);
        ok(CsClassFile.addCompile(w.resolve("Bare/Bare.csproj"), bare)
                && CsClassFile.read(w.resolve("Bare/Bare.csproj")).equals("<Project ToolsVersion=\"15.0\">\n  <PropertyGroup />\n  <ItemGroup>\n    <Compile Include=\"Box.cs\" />\n  </ItemGroup>\n</Project>\n"),
                CsClassFile.read(w.resolve("Bare/Bare.csproj")));
        ok(CsClassFile.read(bare).equals("public class Box\r\n{\r\n}\r\n"), "בלי קובץ נוכחי — CRLF (כמו ש-Visual Studio כותב)");

        // --- פרויקט SDK: לא נוגעים ב-csproj (כל קובץ ‎.cs‎ נכלל), namespace X;
        Path sdk = w.resolve("Sdk");
        String sp = "<Project Sdk=\"Microsoft.NET.Sdk\">\n  <PropertyGroup>\n    <OutputType>Exe</OutputType>\n  </PropertyGroup>\n</Project>\n";
        write(sdk.resolve("Sdk.csproj"), sp, false);
        write(sdk.resolve("Program.cs"), "namespace My.App;\n\nclass Program { static void Main() { } }\n", false);
        ok(CsClassFile.sdkStyle(sdk.resolve("Sdk.csproj")), "SDK");
        ns = CsClassFile.namespace(sdk.resolve("Program.cs"));
        ok(ns[0].equals("My.App") && ns[1].equals("file"), ns[0]);
        Path pt = CsClassFile.write(sdk, "Point.cs", "namespace My.App;\n\npublic class Point\n{\n}\n", sdk.resolve("Program.cs"));
        ok(!CsClassFile.read(pt).contains("\r\n"), "\\n כמו Program.cs");
        ok(!CsClassFile.addCompile(sdk.resolve("Sdk.csproj"), pt) && CsClassFile.read(sdk.resolve("Sdk.csproj")).equals(sp), "SDK — csproj לא משתנה");
        ok(CsClassFile.solution(sdk) == null || !CsClassFile.solution(sdk).startsWith(sdk), "בלי sln בפרויקט");

        // --- הסריקה של classgen-project/cs: Student ו-Car (וגם Student של School.Other), בלי Ghost שב-target
        if (a.length > 1) {
            List<ClassGen.Known> k = CsClassFile.known(new File(a[1], "cs/Model").toPath());
            StringBuilder names = new StringBuilder();
            for (ClassGen.Known x : k) names.append(x.name).append('@').append(x.pkg).append(' ');
            ok(names.toString().equals("Car@School.Model Program@School.App Student@School.Model Student@School.Other "), names.toString());
        }
        System.out.println("CsClassFile: " + checks + " בדיקות עברו");
    }
}

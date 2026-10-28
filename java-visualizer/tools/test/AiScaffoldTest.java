package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * בדיקה ל"שליחת תרגיל": מכל תשובה לדוגמה (tools/test/ai/*.json — Java, ai/cs/*.json — C#) בונים את הקבצים, ובודקים:
 * מבנה (TODO, הסמן בשורה הריקה, הערות), ושהקבצים נכתבים לתיקייה משלהם (ex01_stacks / Ex01_Stacks) —
 * ואז run-tests.sh מקמפל ומריץ אותם (Java: javac; C#: dsviz-cs --check-project).
 *   java AiScaffoldTest <json-dir> <out-src-dir>
 */
public class AiScaffoldTest {
    public static void main(String[] a) throws Exception {
        File dir = new File(a[0]);
        Path out = Paths.get(a[1]);
        List<File> cases = new ArrayList<File>();
        for (File d : new File[] { dir, new File(dir, "cs") }) {
            File[] fs = d.listFiles((x, n) -> n.endsWith(".json"));
            Arrays.sort(fs);
            cases.addAll(Arrays.asList(fs));
        }
        List<String> mains = new ArrayList<String>(), csDirs = new ArrayList<String>();
        HashSet<String> taken = new HashSet<String>(Arrays.asList("examples", "ex07_old")), takenCs = new HashSet<String>(Arrays.asList("Targil1"));
        for (File f : cases) {
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            AiScaffold.Exercise e = AiScaffold.read(Json.obj(Json.parse(json)));
            String pkg = AiScaffold.folderName(e.topic, e.cs ? takenCs : taken, e.cs);
            (e.cs ? takenCs : taken).add(pkg);
            List<AiScaffold.GeneratedFile> files = AiScaffold.build(e, pkg, Arrays.asList(f.getName(), "צילום מסך 1.png"));
            check(!files.isEmpty(), f + ": אין קבצים");
            AiScaffold.GeneratedFile main = files.get(0);
            check(main.className.equals(e.mainClass), "הקובץ הראשון הוא המחלקה הראשית");
            check(main.code.contains(e.cs ? "public static void Main(string[] args)" : "public static void main(String[] args)"), f + ": אין main");
            if (!e.expectedOutput.isEmpty()) {
                // מה צריך להיות מודפס אם הפתרון נכון — בהערה מעל main, שורה-שורה
                check(main.code.contains("// אם פתרת נכון את התרגיל, צריך להיות מודפס על המסך:"), f + ": אין הערה על הפלט הצפוי");
                for (String l : e.expectedOutput.split("\n")) check(main.code.contains("//     " + l), f + ": שורת פלט חסרה בהערה: " + l);
                int at = main.code.indexOf("אם פתרת נכון"), mainAt = main.code.indexOf(e.cs ? "static void Main(" : "static void main(");
                check(at > 0 && at < mainAt, f + ": ההערה צריכה להיות מעל main");
            }
            int todos = 0, cursors = 0;
            for (AiScaffold.GeneratedFile g : files) {
                String[] lines = g.code.split("\n", -1);
                for (String l : lines) if (l.contains(AiScaffold.TODO)) todos++;
                if (g.cursorLine >= 0) {
                    cursors++;
                    check(lines[g.cursorLine - 1].contains(AiScaffold.TODO), f + ": הסמן לא מתחת ל-TODO (" + g.cursorLine + ")");
                    check(lines[g.cursorLine].trim().isEmpty(), f + ": הסמן לא בשורה ריקה");
                }
                if (e.cs) {
                    check(g.code.startsWith("using System;\nusing Unit4.CollectionsLib;"), "using");
                    check(!g.code.contains("namespace ") && !g.code.contains("System.Collections.Generic"), "בלי namespace / Generic");
                    check(g.fileName().endsWith(".cs"), "סיומת .cs");
                } else check(g.code.startsWith("package " + pkg + ";"), "package");
                check(!g.code.contains("*/ וסוף"), "*/ בתוך הערה");
                Path p = (e.cs ? out.resolve("cs") : out).resolve(pkg).resolve(g.fileName());
                Files.createDirectories(p.getParent());
                Files.write(p, g.code.getBytes(StandardCharsets.UTF_8));
            }
            check(todos == e.tasks.size(), f + ": מספר ה-TODO (" + todos + ") שונה ממספר הסעיפים (" + e.tasks.size() + ")");
            check(cursors == 1, f + ": צריך בדיוק קובץ אחד עם סמן");
            if (e.cs) csDirs.add(out.resolve("cs").resolve(pkg).toString());
            else mains.add(pkg + "." + e.mainClass);
            System.out.println("✓ " + f.getName() + " → " + pkg + " (" + files.size() + " קבצים, " + e.tasks.size() + " סעיפים)");
        }
        check(mains.get(0).startsWith("ex08_stacks.") && csDirs.get(0).endsWith("Ex01_Stacks"), "מספור התיקיות: " + mains + " " + csDirs);
        Files.write(out.resolve("mains.txt"), String.join("\n", mains).getBytes(StandardCharsets.UTF_8));
        Files.write(out.resolve("cs-dirs.txt"), String.join("\n", csDirs).getBytes(StandardCharsets.UTF_8));
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}

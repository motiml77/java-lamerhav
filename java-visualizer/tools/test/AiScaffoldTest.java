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
 * בדיקה ל"שליחת תרגיל": מכל תשובה לדוגמה (tools/test/ai/*.json) בונים את הקבצים, ובודקים:
 * מבנה (TODO, הסמן בשורה הריקה, הערות), ושהקבצים נכתבים לתיקיית חבילה — ואז run-tests.sh מקמפל ומריץ אותם.
 *   java AiScaffoldTest <json-dir> <out-src-dir>
 */
public class AiScaffoldTest {
    public static void main(String[] a) throws Exception {
        File dir = new File(a[0]);
        Path out = Paths.get(a[1]);
        File[] cases = dir.listFiles((d, n) -> n.endsWith(".json"));
        Arrays.sort(cases);
        List<String> mains = new ArrayList<String>();
        HashSet<String> taken = new HashSet<String>();
        for (File f : cases) {
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            AiScaffold.Exercise e = AiScaffold.read(Json.obj(Json.parse(json)));
            String pkg = AiScaffold.packageName(e.packageName, taken);
            taken.add(pkg);
            List<AiScaffold.GeneratedFile> files = AiScaffold.build(e, pkg, Arrays.asList(f.getName(), "צילום מסך 1.png"));
            check(!files.isEmpty(), f + ": אין קבצים");
            AiScaffold.GeneratedFile main = files.get(0);
            check(main.className.equals(e.mainClass), "הקובץ הראשון הוא המחלקה הראשית");
            check(main.code.contains("public static void main(String[] args)"), f + ": אין main");
            int todos = 0, cursors = 0;
            for (AiScaffold.GeneratedFile g : files) {
                String[] lines = g.code.split("\n", -1);
                for (String l : lines) if (l.contains(AiScaffold.TODO)) todos++;
                if (g.cursorLine >= 0) {
                    cursors++;
                    check(lines[g.cursorLine - 1].contains(AiScaffold.TODO), f + ": הסמן לא מתחת ל-TODO (" + g.cursorLine + ")");
                    check(lines[g.cursorLine].trim().isEmpty(), f + ": הסמן לא בשורה ריקה");
                }
                check(g.code.startsWith("package " + pkg + ";"), "package");
                check(!g.code.contains("*/ וסוף"), "*/ בתוך הערה");
                Path p = out.resolve(pkg).resolve(g.fileName());
                Files.createDirectories(p.getParent());
                Files.write(p, g.code.getBytes(StandardCharsets.UTF_8));
            }
            check(todos == e.tasks.size(), f + ": מספר ה-TODO (" + todos + ") שונה ממספר הסעיפים (" + e.tasks.size() + ")");
            check(cursors == 1, f + ": צריך בדיוק קובץ אחד עם סמן");
            mains.add(pkg + "." + e.mainClass);
            System.out.println("✓ " + f.getName() + " → " + pkg + " (" + files.size() + " קבצים, " + e.tasks.size() + " סעיפים)");
        }
        Files.write(out.resolve("mains.txt"), String.join("\n", mains).getBytes(StandardCharsets.UTF_8));
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}

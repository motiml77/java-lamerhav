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
 * מבנה (שורת הסימון, הסמן בשורה הריקה, הערות), ושהקבצים נכתבים לתיקייה משלהם (ex01_stacks / Ex01_Stacks) —
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
        int emptyClasses = 0;
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
                check(main.code.contains("אם פתרת נכון את התרגיל, צריך להיות מודפס על המסך:"), f + ": אין הערה על הפלט הצפוי");
                for (String l : e.expectedOutput.split("\n")) check(main.code.contains(l), f + ": שורת פלט חסרה בהערה: " + l);
                int at = main.code.indexOf("אם פתרת נכון"), mainAt = main.code.indexOf(e.cs ? "static void Main(" : "static void main(");
                check(at > 0 && at < mainAt, f + ": ההערה צריכה להיות מעל main");
            }
            int cursors = 0;
            for (AiScaffold.GeneratedFile g : files) {
                String[] lines = g.code.split("\n", -1);
                if (g.cursorLine >= 0) {
                    cursors++;
                    // הסמן בשורה הריקה שמתחת לכותרת הפעולה — בלי הערות מעליה ובלי הערות בתוכה
                    String mk = lines[g.cursorLine - 1];
                    check(mk.contains(e.tasks.size() > 1 ? AiScaffold.markFor(e.tasks.get(0), true) : AiScaffold.MARK), f + ": הסמן לא מתחת לשורת הסימון (" + g.cursorLine + "): " + mk);
                    // בלי return בתוך הפעולה — התלמיד כותב בעצמו
                    check(lines[g.cursorLine + 1].trim().equals("}"), f + ": אחרי הסמן צריך להיות סוף הפעולה, בלי return: " + lines[g.cursorLine + 1]);
                    String before = lines[g.cursorLine - 2].trim();
                    check(before.endsWith("{") || before.endsWith(")"), f + ": מעל שורת הסימון צריכה להיות כותרת הפעולה: " + before);
                    check(lines[g.cursorLine].trim().isEmpty(), f + ": הסמן לא בשורה ריקה");
                }
                if (e.cs) {
                    check(g.code.startsWith("using System;\nusing Unit4.CollectionsLib;"), "using");
                    check(!g.code.contains("namespace ") && !g.code.contains("System.Collections.Generic"), "בלי namespace / Generic");
                    check(g.fileName().endsWith(".cs"), "סיומת .cs");
                } else check(g.code.startsWith("package " + pkg + ";"), "package");
                check(!g.code.contains("*/ וסוף"), "*/ בתוך הערה");
                // בלי כותרת, בלי נוסח התרגיל ובלי Javadoc — רק שלד ובדיקה
                check(!g.code.contains("/**") && !g.code.contains("נבנה מהתרגיל") && !g.code.contains("המעטפת מוכנה"), f + ": נשאר הסבר של התרגיל בקוד");
                check(!g.code.contains(" result = ") && !g.code.contains("answer = "), f + ": נשאר משתנה בתוך הפעולה");
                check(!g.code.startsWith("\n") && !g.code.contains("\n\n\n") && !g.code.matches("(?s).*\\{\n\n.*") && !g.code.matches("(?s).*\n\n\\s*\\}.*"), f + ": שורות ריקות מיותרות");
                check(!g.code.contains("תזכורת") && !g.code.contains("זמני"), f + ": נשארו הערות בתוך הפעולה");
                if (e.tasks.size() > 1) for (AiScaffold.Task t : e.tasks) if (t.className.equals(g.className))
                    check(g.code.contains("// " + AiScaffold.markFor(t, true)), f + ": חסרה שורת סעיף " + t.label + " ב-" + g.className);
                // מחלקה שסעיף שייך אליה אבל לא תוארה בתשובה (אין לה קוד): נוצרת בכל זאת, והשורה הראשונה בתוכה — הערה (מימין לשמאל)
                // שמבקשת מהתלמיד להוסיף את התכונות שמתוארות בתרגיל. במחלקה שתוארה ובמחלקה הראשית — אין הערה כזו
                boolean undescribed = !g.className.equals(e.mainClass) && "".equals(e.classes.get(g.className));
                check(g.code.contains(AiScaffold.ADD_FIELDS) == undescribed, f + ": הערת התכונות במחלקה " + g.className + " (לא תוארה: " + undescribed + ")");
                if (undescribed) {
                    emptyClasses++;
                    int at = Arrays.asList(lines).indexOf(e.cs ? "public class " + g.className : "public class " + g.className + " {");
                    String first = at < 0 ? "" : lines[at + (e.cs ? 2 : 1)];
                    check(first.equals("    // " + AiScaffold.rtl(AiScaffold.addFields(g.className))), f + ": ההערה לא בראש המחלקה " + g.className + ": " + first);
                    check(first.contains("המחלקה " + g.className + " מהתרגיל — הוסיפו כאן את התכונות שמתוארות בתרגיל"), f + ": נוסח ההערה: " + first);
                }
                AiScaffold.testReturns = true;   // לקומפילציה בבדיקות — עם return חשוף
                AiScaffold.GeneratedFile gc = null;
                for (AiScaffold.GeneratedFile x : AiScaffold.build(e, pkg, Arrays.asList(f.getName(), "צילום מסך 1.png"))) if (x.className.equals(g.className)) gc = x;
                AiScaffold.testReturns = false;
                Path p = (e.cs ? out.resolve("cs") : out).resolve(pkg).resolve(g.fileName());
                Files.createDirectories(p.getParent());
                Files.write(p, gc.code.getBytes(StandardCharsets.UTF_8));
            }
            check(cursors == 1, f + ": צריך בדיוק קובץ אחד עם סמן");
            if (e.cs) csDirs.add(out.resolve("cs").resolve(pkg).toString());
            else mains.add(pkg + "." + e.mainClass);
            System.out.println("✓ " + f.getName() + " → " + pkg + " (" + files.size() + " קבצים, " + e.tasks.size() + " סעיפים)");
        }
        check(mains.get(0).startsWith("ex08_stacks.") && csDirs.get(0).endsWith("Ex01_Stacks"), "מספור התיקיות: " + mains + " " + csDirs);
        // ai/4-messy.json (Circle, Java) ו-ai/cs/2-classes.json (Coach, C#): סעיף במחלקה שלא תוארה
        check(emptyClasses >= 2, "מחלקות שלא תוארו (עם הערת התכונות): " + emptyClasses);
        Files.write(out.resolve("mains.txt"), String.join("\n", mains).getBytes(StandardCharsets.UTF_8));
        Files.write(out.resolve("cs-dirs.txt"), String.join("\n", csDirs).getBytes(StandardCharsets.UTF_8));
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}

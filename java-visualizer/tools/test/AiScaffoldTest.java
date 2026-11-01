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
                // סעיפים שעונים עליהם במילים — לא בקוד (רק בהודעה לתלמיד אחרי שהתרגיל נוצר)
                for (String[] q : e.others) check(!g.code.contains(q[1]), f + ": סעיף במילים נכנס לקוד: " + q[1]);
                if (e.tasks.size() > 1) for (AiScaffold.Task t : e.tasks) if (t.className.equals(g.className))
                    check(g.code.contains("// " + AiScaffold.markFor(t, true)), f + ": חסרה שורת סעיף " + t.label + " ב-" + g.className);
                // מחלקה שסעיף שייך אליה אבל לא תוארה בתשובה (אין לה קוד), או שתוארה ריקה ("public class Shelf { }"): נוצרת בכל זאת,
                // והשורה הראשונה בתוכה — הערה (מימין לשמאל) שמבקשת מהתלמיד להוסיף את התכונות שמתוארות בתרגיל.
                // במחלקה שתוארה עם תוכן ובמחלקה הראשית — אין הערה כזו
                String described = e.classes.get(g.className);
                boolean undescribed = !g.className.equals(e.mainClass) && described != null
                        && (described.isEmpty() || described.replaceAll("\\s+", "").matches(".*class" + g.className + "\\{\\}\\}?"));
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
        // ai/4-messy.json (Circle, Java) ו-ai/cs/2-classes.json (Coach, C#): סעיף במחלקה שלא תוארה;
        // ai/4-messy.json (Shelf, Java) ו-ai/cs/2-classes.json (Referee, C# בתוך namespace): מחלקה שתוארה ריקה
        check(emptyClasses >= 4, "מחלקות שלא תוארו או שתוארו ריקות (עם הערת התכונות): " + emptyClasses);
        othersMessage(dir);
        folderNames(out);
        Files.write(out.resolve("mains.txt"), String.join("\n", mains).getBytes(StandardCharsets.UTF_8));
        Files.write(out.resolve("cs-dirs.txt"), String.join("\n", csDirs).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * ההודעה אחרי שהתרגיל נוצר (Eclipse, Visual Studio; אותו נוסח ב-VS Code וב-IntelliJ): הסעיפים שעונים עליהם במילים —
     * ובלי הודעה בכלל כשיש סעיפים בקוד ואין סעיפים במילים
     */
    static void othersMessage(File dir) throws Exception {
        AiScaffold.Exercise e = AiScaffold.read(Json.obj(Json.parse(new String(Files.readAllBytes(new File(dir, "1-stack.json").toPath()), StandardCharsets.UTF_8))));
        check(e.tasks.size() == 2 && e.others.size() == 1, "1-stack: 2 סעיפים בקוד וסעיף אחד במילים");
        String words = AiScaffold.othersText(e.tasks.size(), e.others);
        check(words.equals("סעיפים שעונים עליהם במילים (בלי קוד):\nג — מהי סיבוכיות זמן הריצה של הפעולה שכתבתם בסעיף ב? נמקו."), "סעיף במילים: " + words);
        check(AiScaffold.codeTasks(2).equals("2 סעיפים בקוד") && AiScaffold.codeTasks(1).equals("סעיף אחד בקוד"), "מספר הסעיפים בקוד");
        check(AiScaffold.othersText(2, new ArrayList<String[]>()).isEmpty(), "יש סעיפים בקוד ואין במילים — אין מה להוסיף");
        check(AiScaffold.othersText(0, new ArrayList<String[]>()).equals("בתרגיל אין סעיפים שכותבים בהם קוד."), "בלי סעיפים בקוד");
        check(AiScaffold.othersText(0, e.others).equals("בתרגיל אין סעיפים שכותבים בהם קוד.\n\n" + words), "בלי סעיפים בקוד, עם סעיף במילים");
        check(!words.contains("בראש הקובץ"), "לא אומרים שהשאלות בראש הקובץ");
        // טקסט ארוך (וגם עם ירידות שורה): שורה אחת, עד 200 תווים, נחתך בין מילים ומסתיים ב-"…"
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 60; i++) longText.append("מילה").append(i % 7 == 6 ? "\n" : " ");
        String line = AiScaffold.otherLine("ד", longText.toString());
        String body = line.substring("ד — ".length());
        check(line.startsWith("ד — ") && !line.contains("\n") && body.endsWith("…") && body.length() <= 201 && body.length() > 150
                && !body.contains(" …"), "קיצור טקסט ארוך: " + line);
        check(AiScaffold.otherLine("", " מהי  הסיבוכיות? ").equals("מהי הסיבוכיות?"), "בלי סעיף — רק הטקסט");
        // ההודעה הקצרה (Eclipse): רק אילו סעיפים עונים עליהם במילים, בלי הטקסט
        check(AiScaffold.wordsLine(e.others).equals("סעיף ג: עונים במילים"), "שורת הסעיפים במילים: " + AiScaffold.wordsLine(e.others));
        check(AiScaffold.wordsLine(Arrays.asList(new String[] { "ג'", "x" }, new String[] { "ד", "y" })).equals("סעיפים ג, ד: עונים במילים"), "שני סעיפים במילים");
        check(AiScaffold.wordsLine(Arrays.asList(new String[] { "", "x" }, new String[] { "", "y" })).equals("2 סעיפים: עונים במילים"), "סעיפים בלי סימון");
        check(AiScaffold.wordsLine(new ArrayList<String[]>()).isEmpty() && AiScaffold.wordsLine(null).isEmpty(), "בלי סעיפים במילים — בלי שורה");
        System.out.println("✓ ההודעה אחרי שהתרגיל נוצר: סעיפים במילים / בלי סעיפים בקוד");
    }

    /** שם החבילה / הפרויקט של תרגיל חדש: לא מתנגש בשום שם שכבר קיים (בכל תיקיות המקור, בלי הבדל באותיות, קובץ או תיקייה) */
    static void folderNames(Path out) throws Exception {
        // אותו נושא כמו תרגיל קיים — המספר הבא, לא דורסים
        check(AiScaffold.folderName("stacks", new HashSet<String>(Arrays.asList("ex01_stacks")), false).equals("ex02_stacks"), "אותו נושא — ex02");
        // בלי הבדל בין אותיות גדולות לקטנות
        check(AiScaffold.folderName("stacks", new HashSet<String>(Arrays.asList("EX01_Stacks")), false).equals("ex02_stacks"), "אותיות גדולות — ex02");
        // חבילות בתיקיית מקור אחרת, גם מקוננות (שם מלא): כל חלק תפוס, והמספר ממשיך אחרי הגבוה
        String across = AiScaffold.folderName("stacks", new HashSet<String>(Arrays.asList("ex01_stacks", "school.ex04_queues", "main/java/ex05_trees")), false);
        check(across.equals("ex06_stacks"), "חבילות בכמה תיקיות מקור: " + across);
        check(AiScaffold.folderName("queues", new HashSet<String>(Arrays.asList("ex01_queues.sub", "a")), false).equals("ex02_queues"), "שם מלא ex01_queues.sub");
        // קובץ (לא תיקייה) באותו שם — תפוס
        File d = Files.createTempDirectory(out, "names").toFile();
        Files.write(new File(d, "ex01_lists").toPath(), new byte[0]);
        new File(d, "EX02_Lists").mkdir();
        String disk = AiScaffold.folderName("lists", new HashSet<String>(), false, d);
        check(disk.equals("ex03_lists"), "קובץ ותיקייה בתיקייה: " + disk);
        // C#: Ex01_..., תיקיות תחת Exercises, פרויקטים בפתרון, בלי הבדל באותיות
        check(AiScaffold.folderName("binary_trees", new HashSet<String>(Arrays.asList("ex01_stacks")), true).equals("Ex02_BinaryTrees"), "C#: אותיות קטנות בשם קיים");
        check(AiScaffold.folderName("stacks", new HashSet<String>(Arrays.asList("Ex01_Stacks", "Ex02_Stacks.csproj", "Program")), true).equals("Ex03_Stacks"), "C#: אותו נושא + פרויקט בפתרון");
        System.out.println("✓ שם התרגיל החדש לא מתנגש בשמות קיימים (תיקיות מקור, אותיות, קובץ / תיקייה, C#)");
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import il.lamerhav.dsviz.ClassGen;

/**
 * "צור מחלקה" — המחולל של Java (ClassGen: Eclipse, IntelliJ, הכלי של Visual Studio):
 *   1. בדיקות: הקוד של Student (Java ו-C#), תיקון טיפוסים, וההודעות על קלט שגוי.
 *   2. כל מקרה מ-classgen-cases.txt לקובץ (השגיאות, האזהרות והקוד) — classgen-tests.sh משווה ל-classgen.js (VS Code).
 *
 *   java ClassGenTest classgen-cases.txt <תיקייה> [classgen-project]   (עם הפרויקט — גם הסריקה והרשימות, להשוואה)
 */
public class ClassGenTest {
    static int checks;

    static void eq(Object want, Object got) {
        checks++;
        if (!String.valueOf(want).equals(String.valueOf(got))) throw new AssertionError("\nרצינו:\n" + want + "\nקיבלנו:\n" + got);
    }

    static void ok(boolean b, String what) {
        checks++;
        if (!b) throw new AssertionError(what);
    }

    static ClassGen.Options opts(String lang, String name, String... fields) {
        ClassGen.Options o = new ClassGen.Options();
        o.lang = lang;
        o.className = name;
        for (String f : fields) {
            int sp = f.lastIndexOf(' ');
            o.fields.add(new ClassGen.Field(f.substring(0, sp), f.substring(sp + 1)));
        }
        return o;
    }

    static String titles(List<ClassGen.Group> groups) {
        List<String> t = new java.util.ArrayList<String>();
        for (ClassGen.Group g : groups) t.add(g.title);
        return t.toString();
    }

    static String types(ClassGen.Group g) {
        List<String> t = new java.util.ArrayList<String>();
        for (ClassGen.Item i : g.items) t.add(i.type);
        return t.toString();
    }

    /** הסריקה והרשימות כטקסט (אותו פורמט ב-classgen-test.js): המחלקות, ואז כל קבוצה — "טיפוס|טקסט אפור" */
    static String scanText(String lang, List<ClassGen.Known> known) {
        StringBuilder sb = new StringBuilder();
        for (ClassGen.Known k : known) sb.append(k.name).append('|').append(k.pkg).append('|').append(k.file).append('\n');
        for (List<ClassGen.Group> groups : Arrays.asList(ClassGen.typeGroups(lang, known), ClassGen.elementGroups(lang, known)))
            for (ClassGen.Group g : groups) {
                sb.append("# ").append(g.title).append('\n');
                if (g.items.isEmpty()) sb.append("(").append(g.empty).append(")\n");
                for (ClassGen.Item i : g.items) sb.append(i.type).append('|').append(i.hint).append('\n');
            }
        return sb.toString();
    }

    static String firstError(ClassGen.Options o) {
        ClassGen.Check c = ClassGen.check(o);
        return c.errors.isEmpty() ? "" : c.errors.get(0);
    }

    public static void main(String[] a) throws Exception {
        // --- Student: String name, int grade, boolean active — עם בנאי ו-toString
        ClassGen.Options s = opts("java", "Student", "String name", "int grade", "boolean active");
        s.toString = true;
        eq("public class Student {\n"
                + "    private String name;\n    private int grade;\n    private boolean active;\n\n"
                + "    public Student(String name, int grade, boolean active) {\n"
                + "        this.name = name;\n        this.grade = grade;\n        this.active = active;\n    }\n\n"
                + "    public String getName() {\n        return this.name;\n    }\n\n"
                + "    public void setName(String name) {\n        this.name = name;\n    }\n\n"
                + "    public int getGrade() {\n        return this.grade;\n    }\n\n"
                + "    public void setGrade(int grade) {\n        this.grade = grade;\n    }\n\n"
                + "    public boolean getActive() {\n        return this.active;\n    }\n\n"
                + "    public void setActive(boolean active) {\n        this.active = active;\n    }\n\n"
                + "    public String toString() {\n"
                + "        return \"Student: name=\" + this.name + \", grade=\" + this.grade + \", active=\" + this.active;\n    }\n}\n",
                ClassGen.generate(s));
        // אותו דבר ב-C# (boolean / String מהרשימה של Java הופכים ל-bool / string), בתוך namespace של הפרויקט
        ClassGen.Options c = opts("cs", "Student", "String name", "int grade", "boolean active");
        c.toString = true;
        c.namespace = "School";
        eq("namespace School\n{\n    public class Student\n    {\n"
                + "        private string name;\n        private int grade;\n        private bool active;\n\n"
                + "        public Student(string name, int grade, bool active)\n        {\n"
                + "            this.name = name;\n            this.grade = grade;\n            this.active = active;\n        }\n\n"
                + "        public string GetName()\n        {\n            return this.name;\n        }\n\n"
                + "        public void SetName(string name)\n        {\n            this.name = name;\n        }\n\n"
                + "        public int GetGrade()\n        {\n            return this.grade;\n        }\n\n"
                + "        public void SetGrade(int grade)\n        {\n            this.grade = grade;\n        }\n\n"
                + "        public bool GetActive()\n        {\n            return this.active;\n        }\n\n"
                + "        public void SetActive(bool active)\n        {\n            this.active = active;\n        }\n\n"
                + "        public override string ToString()\n        {\n"
                + "            return \"Student: name=\" + this.name + \", grade=\" + this.grade + \", active=\" + this.active;\n        }\n    }\n}\n",
                ClassGen.generate(c));
        // namespace X; (כמו בקובץ הנוכחי) — בלי הזחה נוספת
        c.fileScoped = true;
        ok(ClassGen.generate(c).startsWith("namespace School;\n\npublic class Student\n{\n    private string name;"), ClassGen.generate(c));

        // --- import / using רק כשיש מבנה של הקורס
        ok(!ClassGen.generate(opts("java", "A", "int x")).contains("import"), "בלי import");
        ok(ClassGen.generate(opts("java", "A", "Queue<int> q")).startsWith("import unit4.collectionsLib.*;\n\npublic class A {\n    private Queue<Integer> q;"), "Queue<int> ← Queue<Integer>, ועם import");
        ClassGen.Options pk = opts("java", "A", "Stack<String> s");
        pk.pkg = "ex1";
        ok(ClassGen.generate(pk).startsWith("package ex1;\n\nimport unit4.collectionsLib.*;\n\npublic class A {"), ClassGen.generate(pk));
        ok(ClassGen.generate(opts("cs", "A", "BinNode<Integer> t")).startsWith("using Unit4.CollectionsLib;\n\npublic class A\n{\n    private BinNode<int> t;"), "C#: BinNode<int> ועם using");
        ok(!ClassGen.generate(opts("java", "A", "java.util.Stack<Integer> s")).contains("import"), "שם מלא — בלי import של הקורס");

        // --- טיפוסים: כתיב אחיד ותיקונים
        eq("Queue<Integer>", ClassGen.normalizeType(" Queue < int > ", "java"));
        eq("Map<String, Integer>", ClassGen.normalizeType("Map<string,int>", "java"));
        eq("Dictionary<string, int>", ClassGen.normalizeType("Dictionary<String, Integer>", "cs"));
        eq("int[][]", ClassGen.normalizeType("int [ ] [ ]", "java"));
        eq("int[,]", ClassGen.normalizeType("int[ , ]", "cs"));
        eq("null", ClassGen.normalizeType("int[,]", "java"));
        eq("int?", ClassGen.normalizeType("int?", "cs"));
        eq("boolean", ClassGen.normalizeType("bool", "java"));
        eq("Student", ClassGen.normalizeType("Student", "java"));
        eq("Queue<Student>", ClassGen.normalizeType("Queue<Student>", "cs"));
        for (String bad : Arrays.asList("void", "in t", "List<>", "Queue<int", "int[", "class", "3x", "a-b", "Stack<void>"))
            eq("null", ClassGen.normalizeType(bad, "java"));

        // --- הודעות על קלט שגוי (בעברית)
        eq("כותבים שם למחלקה", firstError(opts("java", " ", "int x")));
        eq("שם המחלקה חייב להיות באנגלית, בלי רווחים, ולהתחיל באות", firstError(opts("java", "My Class", "int x")));
        eq("שם המחלקה חייב להיות באנגלית, בלי רווחים, ולהתחיל באות", firstError(opts("java", "תלמיד", "int x")));
        eq("\"class\" היא מילה שמורה של Java — בוחרים שם אחר למחלקה", firstError(opts("java", "class", "int x")));
        eq("\"string\" היא מילה שמורה של C# — בוחרים שם אחר למחלקה", firstError(opts("cs", "string", "int x")));
        eq("\"Queue\" הוא שם של מחלקה מוכנה — בוחרים שם אחר למחלקה", firstError(opts("java", "Queue", "int x")));
        ClassGen.Options tk = opts("java", "student", "int x");
        tk.taken.put("Student", "Student.java");
        eq("כבר יש מחלקה בשם Student (בקובץ Student.java) — בוחרים שם אחר", firstError(tk));
        eq("מוסיפים לפחות שדה אחד (טיפוס ושם)", firstError(opts("java", "A")));
        eq("יש שני שדות בשם x", firstError(opts("java", "A", "int x", "double x")));
        eq("השדות name ו-Name יוצרים את אותה פעולה (getName) — משנים שם לאחד מהם", firstError(opts("java", "A", "int name", "int Name")));
        eq("השדות name ו-Name יוצרים את אותה פעולה (GetName) — משנים שם לאחד מהם", firstError(opts("cs", "A", "int name", "int Name")));
        eq("שדה לא יכול להיקרא כמו המחלקה (A)", firstError(opts("cs", "A", "int A")));
        ClassGen.Options sp = opts("java", "A");
        sp.fields.add(new ClassGen.Field("int", "my name"));
        eq("השדה \"my name\": שם חייב להיות באנגלית, בלי רווחים, ולהתחיל באות", firstError(sp));
        eq("השדה \"int\": זו מילה שמורה של Java — בוחרים שם אחר", firstError(opts("java", "A", "int int")));
        eq("השדה x: הטיפוס \"void\" לא תקין (למשל int, String, Queue<Integer>)", firstError(opts("java", "A", "void x")));
        eq("השדה x: הטיפוס \"in t\" לא תקין (למשל int, string, Queue<int>)", firstError(opts("cs", "A", "in t x")));
        ClassGen.Options nt = opts("java", "A");
        nt.fields.add(new ClassGen.Field("", "x"));
        eq("השדה x: חסר טיפוס", firstError(nt));
        // אזהרות (לא חוסמות): אות קטנה בהתחלה, מחלקה שיש בספריית הקורס
        ClassGen.Check w = ClassGen.check(opts("java", "point", "int x"));
        ok(w.ok() && w.className.equals("Point") && w.warnings.get(0).equals("שם של מחלקה מתחיל באות גדולה — המחלקה תיקרא Point"), String.valueOf(w.warnings));
        ok(ClassGen.fileName(w.className, "java").equals("Point.java") && ClassGen.fileName("Point", "cs").equals("Point.cs"), "שם הקובץ");
        w = ClassGen.check(opts("cs", "Car", "int x"));
        ok(w.ok() && w.warnings.get(0).equals("בספריית הקורס כבר יש מחלקה Car — בפרויקט הזה המחלקה החדשה תחליף אותה"), String.valueOf(w.warnings));
        // generate נכשל עם השגיאה הראשונה; התצוגה המקדימה ממשיכה בלי מה שלא תקין
        try {
            ClassGen.generate(opts("java", "A", "int 1x"));
            ok(false, "היה צריך להיכשל");
        } catch (IllegalArgumentException e) {
            ok(e.getMessage().contains("1x"), e.getMessage());
        }
        ok(ClassGen.preview(opts("java", "", "int x", "int 1y")).startsWith("public class MyClass {\n    private int x;\n\n"), ClassGen.preview(opts("java", "", "int x", "int 1y")));

        // --- מה כבר יש בקובץ: package / namespace / מחלקות (לא בתוך הערות ומחרוזות)
        eq("ex1.data", ClassGen.packageOf("// package nope;\npackage ex1.data;\nimport x.*;\n"));
        eq("", ClassGen.packageOf("public class A {}"));
        eq("[ConsoleApp1, block]", Arrays.toString(ClassGen.namespaceOf("using System;\nnamespace ConsoleApp1\n{\n class P {} }")));
        eq("[My.App, file]", Arrays.toString(ClassGen.namespaceOf("namespace My.App;\nclass P {}")));
        eq("null", Arrays.toString(ClassGen.namespaceOf("class P {}")));
        List<String> types = ClassGen.declaredTypes("/* class Hidden */ public class Main { String s = \"class Fake\"; }\nclass Helper {}\ninterface Shape {}\nenum Color { RED }");
        eq("[Main, Helper, Shape, Color]", types);
        eq(4, ClassGen.classLine("package a;\n\nimport x.*;\n\npublic class A {\n}\n"));

        // --- מחלקות שכבר יש בפרויקט: import / using למחלקה מחבילה אחרת, בלי — מאותה חבילה, Student של הקורס כשאין בפרויקט
        ClassGen.Options im = opts("java", "Team", "Student best", "Queue<Student> line", "Car[] cars");
        im.pkg = "school.app";
        im.known.add(new ClassGen.Known("Student", "school.model", "school/model/Student.java"));
        im.known.add(new ClassGen.Known("Car", "school.model", "school/model/Car.java"));
        ok(ClassGen.generate(im).startsWith("package school.app;\n\nimport unit4.collectionsLib.*;\nimport school.model.Car;\nimport school.model.Student;\n\npublic class Team {"), ClassGen.generate(im));
        im.pkg = "school.model";
        ok(ClassGen.generate(im).startsWith("package school.model;\n\nimport unit4.collectionsLib.*;\n\npublic class Team {"), ClassGen.generate(im));
        ClassGen.Options ic = opts("cs", "Team", "Student best", "Queue<Student> line", "Shape s");
        ic.namespace = "School.App";
        ic.known.add(new ClassGen.Known("Student", "School.Model", ""));
        ic.known.add(new ClassGen.Known("Shape", "Geo", ""));
        ok(ClassGen.generate(ic).startsWith("using Unit4.CollectionsLib;\nusing Geo;\nusing Student = School.Model.Student;\n\nnamespace School.App\n{"), ClassGen.generate(ic));
        ok(ClassGen.generate(opts("java", "P", "Student s")).startsWith("import unit4.collectionsLib.*;\n\npublic class P {"), "Student של הקורס");
        ClassGen.Options dup = opts("java", "T", "Student s");
        dup.pkg = "app";
        dup.known.add(new ClassGen.Known("Student", "a", ""));
        dup.known.add(new ClassGen.Known("Student", "b", ""));
        ClassGen.Check dc = ClassGen.check(dup);
        ok(dc.imports.equals(Arrays.asList("import a.Student;")) && dc.warnings.get(0).equals("יש כמה מחלקות בשם Student (a, b) — נבחרה הראשונה. אפשר לבחור אחרת מהרשימה"), dc.imports + " " + dc.warnings);
        dup.fields.get(0).refs.put("Student", "b");
        dc = ClassGen.check(dup);
        ok(dc.imports.equals(Arrays.asList("import b.Student;")) && dc.warnings.isEmpty(), dc.imports + " " + dc.warnings);
        ClassGen.Options same = opts("java", "Student", "int x");
        same.pkg = "school.model";
        same.known.add(new ClassGen.Known("Student", "school.model", "school/model/Student.java"));
        eq("כבר יש מחלקה בשם Student (בקובץ school/model/Student.java) — בוחרים שם אחר", firstError(same));
        same.pkg = "other";
        ok(ClassGen.check(same).ok(), "אותו שם בחבילה אחרת — מותר");
        // הטיפוס המלא של שורה: מבנה של הקורס + מה שבתוכו
        eq("Queue<Integer>", ClassGen.fullType("Queue", "", "java"));
        eq("Stack<Student>", ClassGen.fullType("Stack ", " Student", "java"));
        eq("Node<int>", ClassGen.fullType("Node", null, "cs"));
        eq("Queue<Integer>[]", ClassGen.fullType("Queue<Integer>[]", "String", "java"));
        eq("Student", ClassGen.fullType("Student", "Integer", "cs"));

        // --- הרשימה של הטיפוסים: ארבע קבוצות, ובלי מחלקות בפרויקט — שורה אפורה
        List<ClassGen.Group> g0 = ClassGen.typeGroups("cs", new java.util.ArrayList<ClassGen.Known>());
        eq("[סוגים בסיסיים, מבני נתונים של הקורס, מחלקות שכבר קיימות בפרויקט, מערכים]", titles(g0));
        eq("[int, double, bool, char, string]", types(g0.get(0)));
        eq("[Queue, Stack, Node, BinNode]", types(g0.get(1)));
        ok(g0.get(2).items.isEmpty() && g0.get(2).empty.equals("אין עדיין מחלקות אחרות בפרויקט"), "בלי מחלקות");
        eq("[int[], double[], string[]]", types(g0.get(3)));
        eq("[Integer, Double, Boolean, Character, String]", types(ClassGen.elementGroups("java", new java.util.ArrayList<ClassGen.Known>()).get(0)));

        // --- סריקת הפרויקט (classgen-project): Student ו-Car מוצעים, גם כמערך ובתוך מבנה; Student כפול — עם החבילה באפור
        if (a.length >= 3) {
            File out = new File(a[1]);
            out.mkdirs();
            for (String lang : Arrays.asList("java", "cs")) {
                List<ClassGen.Known> known = ClassGen.scanProject(new File(a[2], lang), lang);
                String text = scanText(lang, known);
                Files.write(new File(out, "scan-" + lang + ".txt").toPath(), text.getBytes(StandardCharsets.UTF_8));
                ok(text.contains("\nCar|\n") && text.contains("\nCar[]|\n") && text.contains("\nStudent|" + (lang.equals("cs") ? "School.Model" : "school.model") + "\n")
                        && text.contains("\nStudent|" + (lang.equals("cs") ? "School.Other" : "school.other") + "\n") && !text.contains("Ghost") && !text.contains("Grade")
                        && !text.contains("InComment") && !text.contains("InString"), text);
            }
        }

        // --- כל המקרים לקבצים (להשוואה מול classgen.js)
        if (a.length >= 2) {
            File out = new File(a[1]);
            out.mkdirs();
            int n = 0;
            for (String line : Files.readAllLines(new File(a[0]).toPath(), StandardCharsets.UTF_8)) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                n++;
                String text = ClassGen.describe(ClassGen.parseCase(line.startsWith("!") ? line.substring(1) : line));
                Files.write(new File(out, n + ".txt").toPath(), text.getBytes(StandardCharsets.UTF_8));
            }
            System.out.println("ClassGen (Java): " + checks + " בדיקות עברו, " + n + " מקרים נכתבו");
        } else System.out.println("ClassGen (Java): " + checks + " בדיקות עברו");
    }
}

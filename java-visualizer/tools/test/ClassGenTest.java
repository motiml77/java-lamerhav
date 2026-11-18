import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
        for (ClassGen.Known k : known) sb.append(k.name).append('|').append(k.pkg).append('|').append(k.file).append(" # ").append(ClassGen.modelText(k)).append('\n');
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

        // --- הורשה: מה נקרא מהקוד, דריסות, virtual ב-C#, צבעים, היררכיות מוכנות, עץ משלכם, הוסף עצם
        poly();

        // --- כל המקרים לקבצים (להשוואה מול classgen.js)
        if (a.length >= 2) {
            File out = new File(a[1]);
            out.mkdirs();
            int n = 0;
            for (String line : Files.readAllLines(new File(a[0]).toPath(), StandardCharsets.UTF_8)) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                n++;
                String text = ClassGen.describe(ClassGen.parseCase(line.startsWith("!") ? line.substring(1) : line, new File(a[0]).getAbsoluteFile().getParentFile()));
                Files.write(new File(out, n + ".txt").toPath(), text.getBytes(StandardCharsets.UTF_8));
            }
            int t = 0;
            for (String[] tc : treeCases()) {
                Files.write(new File(out, "tree-" + tc[0] + ".txt").toPath(), tc[1].getBytes(StandardCharsets.UTF_8));
                t++;
            }
            System.out.println("ClassGen (Java): " + checks + " בדיקות עברו, " + n + " מקרים ו-" + t + " היררכיות / עצמים נכתבו");
        } else System.out.println("ClassGen (Java): " + checks + " בדיקות עברו");
    }

    static ClassGen.Options base(String lang, String ns, List<ClassGen.Known> known) {
        ClassGen.Options o = new ClassGen.Options();
        o.lang = lang;
        if (lang.equals("cs")) o.namespace = ns;
        else o.pkg = ns;
        if (known != null) o.known = known;
        return o;
    }

    /** היררכיה כטקסט (אותו פורמט ב-classgen-test.js): שגיאות, אזהרות, הקבצים, וציור העץ */
    static String treeText(ClassGen.Tree t, ClassGen.TreeResult r) {
        StringBuilder sb = new StringBuilder();
        for (String e : r.errors) sb.append("ERROR ").append(e).append('\n');
        for (String w : r.warnings) sb.append("WARN ").append(w).append('\n');
        sb.append("LAYOUT");
        for (Object[] l : ClassGen.treeLayout(t, new java.util.ArrayList<ClassGen.Known>())) sb.append(' ').append(l[0]).append(':').append(l[1]).append(':').append(l[2]).append(':').append(l[3]);
        sb.append('\n');
        for (String[] f : r.files) sb.append("--- ").append(f[0]).append('\n').append(f[1]);
        return sb.toString();
    }

    static List<ClassGen.TreeRow> ownRows(String spec) {
        List<ClassGen.TreeRow> rows = new java.util.ArrayList<ClassGen.TreeRow>();
        for (String r : spec.split("/", -1)) {
            String[] p = r.split("\\|", -1);
            rows.add(new ClassGen.TreeRow(p[0], p.length > 1 ? p[1] : "", p.length > 2 ? p[2] : ""));
        }
        return rows;
    }

    /** טבלאות של "עץ משלכם" (שורות מופרדות ב-/, עמודות ב-|) — גם classgen-test.js משתמש בהן */
    static final String[] OWN = { "Vehicle||String model; int year/Bus|Vehicle|int seats/Taxi|vehicle|String company, boolean night/MiniBus|Bus|",
            "A||int x/a|A|int y", "A|B|int x/B|A|int y", "Node||int value/Thing|Ghost|", "Box|Dog|int size", "Q||int x;int 2y/R|Q|int x" };

    static List<String[]> treeCases() {
        List<String[]> out = new java.util.ArrayList<String[]>();
        for (String lang : Arrays.asList("java", "cs"))
            for (String ns : Arrays.asList("", lang.equals("cs") ? "Zoo1" : "zoo.app")) {
                for (ClassGen.Tree t : ClassGen.trees())
                    for (boolean run : new boolean[] { false, true }) {
                        if (run && !lang.equals("cs")) continue;
                        ClassGen.Options b = base(lang, ns, null);
                        b.fileScoped = run;
                        out.add(new String[] { lang + "-" + t.id + (ns.isEmpty() ? "" : "-ns") + (run ? "-run" : ""), treeText(t, ClassGen.treeFiles(t, b, true, run)) });
                    }
                for (int i = 0; i < OWN.length; i++) {
                    List<String> errors = new java.util.ArrayList<String>();
                    List<ClassGen.Known> proj = ClassGen.treeFiles(ClassGen.trees().get(0), base(lang, ns, null), false, false).known;
                    ClassGen.Tree t = ClassGen.ownTree(ownRows(OWN[i]), proj, errors);
                    ClassGen.Options b = base(lang, ns, proj);
                    ClassGen.TreeResult r = ClassGen.treeFiles(t, b, true, false);
                    r.errors.addAll(0, errors);
                    out.add(new String[] { lang + "-own" + i + (ns.isEmpty() ? "" : "-ns"), treeText(t, r) });
                }
                // הוסף עצם — מול המחלקות של בעלי חיים וצורות
                List<ClassGen.Known> known = new java.util.ArrayList<ClassGen.Known>(ClassGen.treeFiles(ClassGen.trees().get(0), base(lang, ns, null), false, false).known);
                known.addAll(ClassGen.treeFiles(ClassGen.trees().get(1), base(lang, ns, null), false, false).known);
                StringBuilder sb = new StringBuilder();
                for (String spec : OBJECTS) {
                    ClassGen.ObjOptions o = objOptions(lang, spec);
                    ClassGen.ObjResult r = ClassGen.objectCode(o, known);
                    sb.append("## ").append(spec).append('\n');
                    for (String e : r.errors) sb.append("ERROR ").append(e).append('\n');
                    sb.append(r.code).append('\n');
                }
                for (String type : Arrays.asList("Animal", "Dog", "Shape", "Person", "Nope")) {
                    sb.append("CLASSES ").append(type).append(':');
                    for (ClassGen.Known k : ClassGen.objectClasses(known, type)) {
                        sb.append(' ').append(k.name).append('(');
                        List<ClassGen.Field> ps = ClassGen.ctorParams(known, k.name);
                        for (int i = 0; i < ps.size(); i++)
                            sb.append(i > 0 ? ", " : "").append(ps.get(i).type).append(' ').append(ps.get(i).name).append('@').append(ClassGen.paramOwner(known, k.name, ps.get(i).name));
                        sb.append(')');
                    }
                    sb.append('\n');
                }
                for (String t : SPLITS) sb.append("SPLIT ").append(t).append(" → ").append(String.join(" | ", ClassGen.splitArgs(t))).append('\n');
                out.add(new String[] { lang + "-objects" + (ns.isEmpty() ? "" : "-ns"), sb.toString() });
            }
        return out;
    }

    /** ערכים לתא של מערך (ClassGen.splitArgs) — גם classgen-test.js */
    static final String[] SPLITS = { "Rexy, male, 3.2", "  ", "new Person(\"a, b\"), \"x,y\", ',', f(1, g(2, 3))", "\"say \\\"hi, you\\\"\", 2", "a,,b" };

    /** "הוסף עצם": "type|name|cls|arg;arg..." או מערך "type|name|[]4|cls:arg;arg/cls:arg/..." */
    static final String[] OBJECTS = { "Animal|a1|Boxer|Rexy;male;3.2;3;house", "Animal|a2|Labrador|Toto;male;2.5;4;new Person(\"Hila\")",
            "Dog|d|Dog|\"Dasty\";female;0.5;3", "Shape|s|Circle|red;2", "Animal|a3|Animal|", "Animal|zoo|[]4|Dog:Dasty;female;0.5;3//Boxer:Rexy;male;3.2;3;42/Animal:Bob",
            "Animal|x|Person|Hila", "Shape|s|Shape|red", "Animal|1x|Dog|", "|a|Dog|", "Animal|zoo|[]40|", "Animal|zoo|[]2|Person:Hila/Mammal:Numa;female;2",
            "Person|p|Person|He said \"hi\"" };

    static ClassGen.ObjOptions objOptions(String lang, String spec) {
        String[] p = spec.split("\\|", -1);
        ClassGen.ObjOptions o = new ClassGen.ObjOptions();
        o.lang = lang;
        o.varType = p[0];
        o.varName = p[1];
        o.indent = "    ";
        if (p[2].startsWith("[]")) {
            o.array = true;
            o.size = Integer.parseInt(p[2].substring(2));
            for (String cell : p[3].split("/", -1)) {
                List<String> c = new java.util.ArrayList<String>();
                if (!cell.isEmpty()) {
                    int colon = cell.indexOf(':');
                    c.add(colon < 0 ? cell : cell.substring(0, colon));
                    if (colon >= 0) c.addAll(Arrays.asList(cell.substring(colon + 1).split(";", -1)));
                }
                o.cells.add(c);
            }
        } else {
            o.cls = p[2];
            if (!p[3].isEmpty()) o.args.addAll(Arrays.asList(p[3].split(";", -1)));
        }
        return o;
    }

    static void poly() throws Exception {
        // מה נקרא מהקוד: מחלקת על, שדות (גם int a = 1, b = 2 ו-ArrayList<String> x = new ...()), הבנאי הרחב, פעולות עם מגדירים
        List<ClassGen.Known> ks = ClassGen.models("public abstract class Shape extends Base implements Cloneable {\n"
                + "  @Deprecated protected int a = 1, b = 2;\n  private static int n;\n  private java.util.List<String> names = new java.util.ArrayList<String>();\n"
                + "  public Shape() { this(1); }\n  public Shape(int a, String b) { }\n  private Shape(int a, int b, int c) { }\n"
                + "  public abstract double area();\n  protected void grow(final int by, String why) { if (by > 0) { a++; } }\n"
                + "  public final String id() { return \"x { y }\"; }\n  class Inner { void hidden() {} }\n}\n"
                + "interface Walker { void walk(); default void rest() { } }\nfinal class Locked { }\n", "Shape.java");
        eq("Shape : Base abstract | int a; int b; java.util.List<String> names; | ctor(int a, String b) | [public abstract] double area()!; [protected] void grow(int by, String why); [public final] String id();",
                ClassGen.modelText(ks.get(0)));
        eq("Walker interface | | ctor(-) | void walk()!; [default] void rest();", ClassGen.modelText(ks.get(1)));
        eq("Locked final | | ctor(-) |", ClassGen.modelText(ks.get(2)));
        List<ClassGen.Known> cs = ClassGen.models("namespace Zoo {\n public class Dog : Mammal, IComparable<Dog> {\n  public int Legs { get; set; }\n"
                + "  public Dog(string name, int bones = 3) : base(name) { }\n  public string GetName() => name;\n  public sealed override string Say() { return \"\"; }\n"
                + "  [Obsolete] public virtual void Bark(ref int times) { }\n }\n public class Pet : IDisposable { public void Dispose() { } }\n}\n", "Dog.cs");
        eq("Dog : Mammal | int Legs; | ctor(string name, int bones) | [public] string GetName(); [public sealed override] string Say(); [public virtual] void Bark(int times);",
                ClassGen.modelText(cs.get(0)));
        eq("Zoo", cs.get(0).pkg);
        eq("Pet | | ctor(-) | [public] void Dispose();", ClassGen.modelText(cs.get(1)));
        // virtual נוסף לפעולה הנכונה (במחלקה הנכונה, עם אותו מספר פרמטרים)
        String src = "public class A\n{\n    public string Kind() { return \"a\"; }\n}\npublic class B\n{\n    public string Kind(int x) { return \"b\"; }\n    public string Kind()\n    {\n        return \"b\";\n    }\n}\n";
        eq(src.replace("    public string Kind()\n    {", "    public virtual string Kind()\n    {"), ClassGen.addVirtual(src, "B", "Kind", ""));
        eq(src, ClassGen.addVirtual(src.replace("public string Kind()\n", "public virtual string Kind()\n"), "B", "Kind", "").replace("public virtual string Kind()\n", "public string Kind()\n"));
        // צבעים כמו בהרצה: השורשים, ואז הילדים — מחלקה בלי עץ אפורה
        ClassGen.TreeResult zoo = ClassGen.treeFiles(ClassGen.trees().get(0), base("java", "", null), true, false);
        Map<String, Integer> col = ClassGen.colors(ClassGen.sorted(zoo.known));
        eq("{Animal=0, Mammal=1, Dog=2, Boxer=3, Labrador=4, Person=-1}", col.toString());
        eq("#c0392b #7b3fb0 #55607a", String.format("#%06x #%06x #%06x", ClassGen.ink(col.get("Animal")), ClassGen.ink(col.get("Mammal")), ClassGen.ink(col.get("Person"))));
        ClassGen.TreeResult school = ClassGen.treeFiles(ClassGen.trees().get(2), base("java", "", null), true, false);
        eq("{Person=0, Teacher=1, HomeroomTeacher=2, Pupil=3}", ClassGen.colors(ClassGen.sorted(school.known)).toString());
        // היררכיה מוכנה: כל הקבצים, Boxer עם super(...) ו-@Override; ב-C# — : base(...) ו-override, ו-Run כשיש כבר Main
        ok(zoo.ok() && zoo.files.size() == 7 && zoo.files.get(6)[0].equals("ZooDemo.java"), zoo.errors.toString());
        ok(zoo.files.get(3)[1].contains("    public Boxer(String name, String gender, double milk, int bonesToEat, Object guard) {\n        super(name, gender, milk, bonesToEat);\n"), zoo.files.get(3)[1]);
        ok(zoo.files.get(6)[1].contains("        zoo[3] = new Boxer(\"Rexy\", \"male\", 3.2, 3, \"house\");\n"), zoo.files.get(6)[1]);
        ClassGen.TreeResult zcs = ClassGen.treeFiles(ClassGen.trees().get(0), base("cs", "", null), true, true);
        ok(zcs.files.get(3)[1].contains("    public Boxer(string name, string gender, double milk, int bonesToEat, object guard) : base(name, gender, milk, bonesToEat)\n")
                && zcs.files.get(3)[1].contains("    public override string Say()\n") && zcs.files.get(0)[1].contains("    public virtual string Say()\n"), zcs.files.get(3)[1]);
        ok(zcs.files.get(6)[1].contains("public static void Run()") && zcs.files.get(6)[1].contains("System.Console.WriteLine(zoo[i].Say());"), zcs.files.get(6)[1]);
        // שם תפוס / שם של Unit4 בהיררכיה — שגיאה עם שם המחלקה
        ClassGen.Options tb = base("cs", "", null);
        tb.taken.put("Dog", "Dog.cs");
        ClassGen.TreeResult bad = ClassGen.treeFiles(ClassGen.trees().get(0), tb, true, false);
        ok(!bad.ok() && bad.errors.get(0).equals("Dog: כבר יש מחלקה בשם Dog (בקובץ Dog.cs) — בוחרים שם אחר"), bad.errors.toString());
        eq("\"Helpers\" הוא שם של מחלקה בספריית הקורס (Unit4) — מחלקה בשם הזה תתנגש ב-using Unit4.CollectionsLib; בוחרים שם אחר למחלקה", firstError(opts("cs", "Helpers", "int x")));
        // צור מחלקה עם יורשת מ-: הפרמטרים של הבנאי, שדה שכבר עובר בירושה, פעולה מופשטת שחובה לדרוס
        ClassGen.Options in = opts("java", "Puppy", "int age");
        in.known = zoo.known;
        in.superName = "Dog";
        ClassGen.Check ic = ClassGen.check(in);
        ok(ic.ok() && ic.superParams.size() == 4 && ic.inherited.size() == 3 && ic.inherited.get(0).from.equals("Animal"), ic.errors.toString());
        in.fields.get(0).name = "milk";
        eq("השדה milk כבר קיים במחלקה Mammal — המחלקה יורשת אותו. בוחרים שם אחר", firstError(in));
        ClassGen.TreeResult shapes = ClassGen.treeFiles(ClassGen.trees().get(1), base("java", "", null), false, false);
        ClassGen.Options sq = opts("java", "Square", "double side");
        sq.known = shapes.known;
        sq.superName = "Shape";
        ClassGen.Check sc = ClassGen.check(sq);
        ok(sc.overrides.size() == 1 && sc.overrides.get(0).isAbstract && ClassGen.generate(sq).contains("    @Override\n    public double area() {\n        return 0;\n    }\n"), ClassGen.generate(sq));
        sq.isAbstract = true;
        ok(ClassGen.check(sq).overrides.isEmpty() && ClassGen.generate(sq).startsWith("public abstract class Square extends Shape {"), ClassGen.generate(sq));
        // הוסף עצם
        ClassGen.ObjResult or = ClassGen.objectCode(objOptions("java", OBJECTS[0]), zoo.known);
        eq("    Animal a1 = new Boxer(\"Rexy\", \"male\", 3.2, 3, \"house\");", or.code);
        eq("\"a \\\"b\\\"\"", ClassGen.literal("String", "a \"b\"", "java"));
        eq("'x'", ClassGen.literal("char", "x", "cs"));
        eq("0", ClassGen.literal("int", "", "cs"));
        eq("42", ClassGen.literal("Object", "42", "java"));
        eq("\"house\"", ClassGen.literal("object", "house", "cs"));
    }
}

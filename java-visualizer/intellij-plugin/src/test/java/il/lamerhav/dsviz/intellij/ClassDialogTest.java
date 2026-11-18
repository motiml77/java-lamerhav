package il.lamerhav.dsviz.intellij;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import il.lamerhav.dsviz.ClassGen;

/**
 * "צור מחלקה" ב-IntelliJ: החלון (קוד חי, הודעות בעברית, הכפתור מושבת), הרשימה של הטיפוסים בקבוצות (גם מחלקות שכבר יש בפרויקט,
 * ושורה אפורה כשאין), import למחלקה מחבילה אחרת, והקובץ שנוצר ליד הקובץ הנוכחי ונפתח בעורך.
 */
public class ClassDialogTest extends LightJavaCodeInsightFixtureTestCase {

    public void testStudentWithGettersAndSetters() {
        ClassDialog d = new ClassDialog(getProject(), "ex1", new LinkedHashMap<String, String>(), new ArrayList<ClassGen.Known>());
        try {
            assertEquals("כותבים שם למחלקה", d.error());
            assertFalse(d.isOKActionEnabled());
            d.demo("student", "String name;int grade;boolean active");
            d.withToString(true);
            assertEquals("", d.error());
            assertTrue(d.isOKActionEnabled());
            assertEquals("Student", d.className());
            assertEquals("Student.java", d.fileName());
            String code = d.code();
            assertTrue(code, code.startsWith("package ex1;\n\npublic class Student {\n    private String name;\n    private int grade;\n    private boolean active;\n"));
            assertTrue(code.contains("    public Student(String name, int grade, boolean active) {\n"));
            assertTrue(code.contains("    public boolean getActive() {\n        return this.active;\n    }\n"));
            assertTrue(code.contains("    public void setGrade(int grade) {\n        this.grade = grade;\n    }\n"));
            assertTrue(code.contains("return \"Student: name=\" + this.name + \", grade=\" + this.grade + \", active=\" + this.active;"));
            // שגיאות: שם שדה כפול, מילה שמורה
            d.demo("Student", "int x;double x");
            assertEquals("יש שני שדות בשם x", d.error());
            assertFalse(d.isOKActionEnabled());
            d.demo("class", "int x");
            assertEquals("\"class\" היא מילה שמורה של Java — בוחרים שם אחר למחלקה", d.error());
        } finally {
            d.close(0);
        }
    }

    public void testTypeListGroupsAndProjectClasses() {
        // בלי מחלקות בפרויקט — שורה אפורה
        ClassDialog empty = new ClassDialog(getProject(), "", new LinkedHashMap<String, String>(), new ArrayList<ClassGen.Known>());
        try {
            List<String> rows = empty.typeList(0, false);
            assertEquals(Arrays.asList("# סוגים בסיסיים", "int", "double", "boolean", "char", "String", "# מבני נתונים של הקורס", "Queue", "Stack", "Node", "BinNode",
                    "# מחלקות שכבר קיימות בפרויקט", "(אין עדיין מחלקות אחרות בפרויקט)", "# מערכים", "int[]", "double[]", "String[]"), rows);
        } finally {
            empty.close(0);
        }
        // פרויקט עם Student ו-Car (ועוד Student בחבילה אחרת): מוצעים, גם כמערך ובתוך Queue; import לחבילה של מה שנבחר
        List<ClassGen.Known> known = Arrays.asList(new ClassGen.Known("Student", "school.model", "school/model/Student.java"),
                new ClassGen.Known("Car", "school.model", "school/model/Car.java"), new ClassGen.Known("Student", "school.other", "school/other/Student.java"));
        ClassDialog d = new ClassDialog(getProject(), "school.app", new LinkedHashMap<String, String>(), known);
        try {
            List<String> rows = d.typeList(0, false);
            assertTrue(rows.toString(), rows.containsAll(Arrays.asList("Car", "Student school.model", "Student school.other", "Car[]", "Student[] school.model")));
            d.demo("Team", "Student best{Student=school.model};Queue<Student> line{Student=school.model};Car[] cars");
            assertTrue(d.typeList(1, true).toString(), d.typeList(1, true).containsAll(Arrays.asList("Integer", "Character", "Car", "Student school.other")));
            assertEquals("", d.error());
            assertTrue(d.code(), d.code().startsWith("package school.app;\n\nimport unit4.collectionsLib.*;\nimport school.model.Car;\nimport school.model.Student;\n\npublic class Team {\n"
                    + "    private Student best;\n    private Queue<Student> line;\n    private Car[] cars;\n"));
        } finally {
            d.close(0);
        }
    }

    /** הורשה: יורשת מ- (עם הצבעים של ההרצה), שדות שירשה, דריסה עם @Override, פעולה חדשה, בנאי שמעביר ל-super */
    public void testInheritanceFromProjectClass() {
        ClassGen.Options zb = new ClassGen.Options();
        ClassGen.TreeResult zoo = ClassGen.treeFiles(ClassGen.trees().get(0), zb, false, false);
        ClassDialog d = new ClassDialog(getProject(), "", new LinkedHashMap<String, String>(), zoo.known);
        try {
            List<String> sup = d.inherit().superList();
            assertEquals("בלי", sup.get(0));
            assertTrue(sup.toString(), sup.contains("Animal #c0392b") && sup.contains("Dog #0c8583") && sup.contains("Person #55607a"));
            d.demo("Puppy", "int age");
            d.inherit().demoSuper("Dog");
            d.inherit().demoPick("say()");
            d.inherit().addMethod("String", "bark", "int times", false, false);
            assertEquals("", d.error());
            String code = d.code();
            assertTrue(code, code.startsWith("public class Puppy extends Dog {\n    private int age;\n\n    public Puppy(String name, String gender, double milk, int bonesToEat, int age) {\n"
                    + "        super(name, gender, milk, bonesToEat);\n"));
            assertTrue(code, code.contains("    @Override\n    public String say() {\n        return super.say();\n    }\n"));
            assertTrue(code, code.contains("    public String bark(int times) {\n        return \"Puppy\";\n    }\n"));
            assertTrue(d.inherit().overrideList().toString(), d.inherit().overrideList().contains("say() ✓"));
            d.demo("Puppy", "double milk");
            assertEquals("השדה milk כבר קיים במחלקה Mammal — המחלקה יורשת אותו. בוחרים שם אחר", d.error());
        } finally {
            d.close(0);
        }
    }

    /** היררכיה מוכנה ועץ משלכם: הקבצים נוצרים בתיקייה, והשם של מחלקה מ-Unit4 נחסם */
    public void testTreeDialogCreatesAllFiles() throws Exception {
        File base = Files.createTempDirectory("dsviz-tree").toFile();
        VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(base);
        ClassGen.Options o = new ClassGen.Options();
        PolyDialogs.TreeDialog d = new PolyDialogs.TreeDialog(getProject(), o);
        try {
            assertEquals("", d.error());
            assertTrue(d.isOKActionEnabled());
            assertEquals(7, d.result.files.size());
            List<VirtualFile> made = PolyDialogs.createAll(getProject(), dir, d.result, d.tree);
            assertEquals(7, made.size());
            assertTrue(VfsUtilCore.loadText(dir.findChild("Boxer.java")).contains("public class Boxer extends Dog {"));
            assertTrue(VfsUtilCore.loadText(dir.findChild("ZooDemo.java")).contains("zoo[3] = new Boxer(\"Rexy\", \"male\", 3.2, 3, \"house\");"));
            d.panel.demoChoose(3);
            d.panel.demoRows("Node||int value/Box|Node|int size");
            assertTrue(d.error(), d.error().startsWith("Node: \"Node\" הוא שם של מחלקה מוכנה"));
            assertFalse(d.isOKActionEnabled());
        } finally {
            d.close(0);
        }
    }

    /** הוסף עצם: רק המחלקה או יורשת שלה, ערך לכל פרמטר, והשם נבדק מול הקוד שבמקום הסמן */
    public void testObjectDialog() {
        ClassGen.TreeResult zoo = ClassGen.treeFiles(ClassGen.trees().get(0), new ClassGen.Options(), false, false);
        String text = "public class Main {\n    public static void main(String[] args) {\n        int a1 = 3;\n        \n    }\n}\n";
        PolyDialogs.ObjectDialog d = new PolyDialogs.ObjectDialog(getProject(), zoo.known, text, text.indexOf("        \n"), "        ");
        try {
            d.panel.demo("Animal", "a2", "Boxer", "Rexy, male, 3.2, 3, house");
            assertEquals("", d.error());
            assertEquals("        Animal a2 = new Boxer(\"Rexy\", \"male\", 3.2, 3, \"house\");", d.code);
            assertEquals(Arrays.asList("Animal", "Mammal", "Dog", "Boxer", "Labrador"), d.panel.classList());
            d.panel.demo("Animal", "a1", "Dog", "Dasty, female, 0.5, 3");
            assertTrue(d.error(), d.error().contains("a1"));
            d.panel.demoArray("Animal", "zoo", "Dog:Dasty, female, 0.5, 3/:/Labrador:Toto, male, 2.5, 4, new Person(\"Hila\")");
            assertEquals("        Animal[] zoo = new Animal[3];\n        zoo[0] = new Dog(\"Dasty\", \"female\", 0.5, 3);\n"
                    + "        zoo[2] = new Labrador(\"Toto\", \"male\", 2.5, 4, new Person(\"Hila\"));", d.code);
        } finally {
            d.close(0);
        }
    }

    public void testCreatesFileNextToCurrentAndScansProject() throws Exception {
        File base = Files.createTempDirectory("dsviz-cc").toFile();
        File pkg = new File(base, "school/model");
        pkg.mkdirs();
        Files.write(new File(pkg, "Car.java").toPath(), "package school.model;\npublic class Car {}\n".getBytes(StandardCharsets.UTF_8));
        VirtualFile dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(pkg);
        assertNotNull(dir);
        assertEquals("Car.java", ClassCreator.taken(dir).get("Car"));
        List<ClassGen.Known> scan = ClassGen.scanProject(base, ClassGen.JAVA);
        assertEquals(1, scan.size());
        assertEquals("school.model", scan.get(0).pkg);
        ClassGen.Options o = new ClassGen.Options();
        o.className = "Garage";
        o.pkg = "school.model";
        o.known = scan;
        o.fields.add(new ClassGen.Field("Queue<Car>", "cars"));
        String code = ClassGen.generate(o);
        VirtualFile f = ClassCreator.create(getProject(), dir, "Garage.java", code);
        assertNotNull(f);
        assertEquals(code, VfsUtilCore.loadText(f));
        assertTrue(code.startsWith("package school.model;\n\nimport unit4.collectionsLib.*;\n\npublic class Garage {"));
        // אותו שם שוב — לא דורסים
        assertNull(ClassCreator.create(getProject(), dir, "Garage.java", code));
    }
}

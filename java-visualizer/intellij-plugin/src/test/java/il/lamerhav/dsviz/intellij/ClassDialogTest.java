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

package il.lamerhav.dsviz.cs;

import il.lamerhav.dsviz.Scaffold;
import java.nio.file.*;

/** יוצר שני תרגילים (רגיל ומבנה-בתוך-מבנה) בתיקיית תרגילים ומדפיס את הנתיבים; הבדיקה עצמה ב-cs-tests.sh */
public class CsProjectTest {
    public static void main(String[] a) throws Exception {
        Path root = Paths.get(a[0]);
        String bad = CsProject.check(root);
        if (bad != null) throw new RuntimeException(bad);
        String[][] cases = { { "stack", "Integer", "int" }, { "list_queue", "String", "structure" }, { "tree_stack", "Integer", "void" } };
        for (String[] c : cases) {
            Scaffold.Options o = new Scaffold.Options();
            o.structure = c[0]; o.type = c[1]; o.returns = c[2]; o.values = Scaffold.defaultValues(c[0], c[1], false);
            o.className = CsProject.nextName(root); o.functionName = "solve"; o.recursive = c[0].equals("stack"); o.count = 1;
            Path f = CsProject.write(root, o.className, ScaffoldCs.generate(o).code);
            System.out.println(f);
        }
        if (!CsProject.taken(root, "Targil1")) throw new RuntimeException("taken");
        // "שליחת תרגיל" ב-Visual Studio: השמות התפוסים = הפרויקטים ב-.sln + התיקיות שב-Exercises (הפרויקט החדש יקבל שם אחר)
        java.util.List<String> names = il.lamerhav.dsviz.VsSubmit.takenNames(root);
        for (String n : new String[] { "Targil1", "Targil2", "Exercise", "StackExamples" })
            if (!names.contains(n)) throw new RuntimeException("takenNames: חסר " + n + " ב-" + names);
    }
}

package il.lamerhav.dsviz;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;

import org.eclipse.core.runtime.NullProgressMonitor;

/**
 * "שליחת תרגיל" ב-C# — המסלול של SubmitHandler.CsBuild מחוץ ל-Eclipse: פרויקט חדש בתיקיית התרגילים (Ex01_...), רישום בפתרון,
 * בדיקת קומפילציה עם dsviz-cs --check-project (‎~/.dsviz/cs-path.txt), ואם יש שגיאות ושרת — תיקון אחד.
 *   java CsBuildTest <DataStructures-CS> <exercise.json> [worker-url]
 */
public class CsBuildTest {
    public static void main(String[] a) throws Exception {
        Path root = Paths.get(a[0]);
        Map<String, Object> raw = Json.obj(Json.parse(new String(Files.readAllBytes(Paths.get(a[1])), StandardCharsets.UTF_8)));
        WorkerClient client = a.length > 2 ? new WorkerClient(a[2], "") : null;
        SubmitHandler.CsBuild b = new SubmitHandler.CsBuild(client, raw, Arrays.asList("targil.pdf"), root);
        b.run(new NullProgressMonitor());
        check(b.dir != null && Files.isDirectory(b.dir), "התיקייה נוצרה");
        check(b.dir.getParent().getFileName().toString().equals("Exercises") && b.name.matches("Ex\\d\\d_[A-Za-z]+"), "שם התיקייה: " + b.dir);
        check(Files.exists(b.dir.resolve(b.name + ".csproj")) && Files.exists(b.dir.resolve("Properties/launchSettings.json")), "csproj + launchSettings");
        String sln = new String(Files.readAllBytes(CsSolution.solution(root)), StandardCharsets.UTF_8);
        check(sln.contains("\"" + b.name + "\", \"Exercises\\" + b.name + "\\" + b.name + ".csproj\""), "רשום בפתרון");
        check(b.checked, "נבדקה קומפילציה (dsviz-cs)");
        check(b.errors.isEmpty(), "שגיאות: " + b.errors);
        System.out.println("✓ C#: " + b.name + " — " + b.focusFile + ", רשום בפתרון, מתקמפל");
    }

    static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }
}

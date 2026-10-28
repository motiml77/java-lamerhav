package il.lamerhav.dsviz.cs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;

import il.lamerhav.dsviz.CsSolution;

/**
 * יצירת פרויקט תרגיל ב-C# בתיקיית התרגילים (DataStructures-CS) — הקוד עצמו ב-CsSolution (משותף עם "שליחת תרגיל" ב-Eclipse).
 */
final class CsProject {
    private CsProject() {}

    static final String LIB = CsSolution.LIB;

    static String csproj(String name) { return CsSolution.csproj(name); }

    static String check(Path root) { return CsSolution.check(root); }

    static Path exercisesDir(Path root) { return CsSolution.exercisesDir(root); }

    static Path solution(Path root) { return CsSolution.solution(root); }

    static String nextName(Path root) { return CsSolution.nextName(root); }

    static boolean taken(Path root, String name) { return CsSolution.taken(root, name); }

    /** כותב את הפרויקט ורושם אותו בפתרון. מחזיר את הנתיב ל-Program.cs */
    static Path write(Path root, String name, String code) throws IOException {
        return CsSolution.write(root, name, Collections.singletonMap("Program.cs", code)).resolve("Program.cs");
    }
}

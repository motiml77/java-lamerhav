package il.lamerhav.dsviz.intellij;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.vfs.VirtualFile;

import il.lamerhav.dsviz.ClassGen;

/**
 * "צור מחלקה" ב-IntelliJ (מהתפריט / לחיצה ימנית, מהכפתור בחלון "הוסף מבנה נתונים", או כשהסמן מחוץ לפעולה): החלון, ואז קובץ חדש
 * Name.java באותה תיקייה (ובאותה חבילה) של הקובץ הנוכחי — או של התיקייה שנבחרה — ופתיחה בעורך
 */
final class ClassCreator {
    private ClassCreator() {}

    static void run(Project project, VirtualFile dir, String pkg) {
        if (dir == null || !dir.isDirectory()) {
            Say.info(project, "פותחים קובץ Java (או בוחרים תיקייה בעץ הקבצים) — המחלקה החדשה נוצרת שם, ואז לוחצים שוב \"צור מחלקה\".", ClassGen.TITLE);
            return;
        }
        ClassDialog d = new ClassDialog(project, pkg, taken(dir), known(project, dir));
        if (!d.showAndGet() || d.code() == null) return;
        create(project, dir, d.fileName(), d.code());
    }

    /** יוצר את הקובץ ופותח אותו (הסמן בשורה של public class). null — לא נוצר (כבר קיים / שגיאה) */
    static VirtualFile create(Project project, VirtualFile dir, String fileName, String code) {
        dir.refresh(false, false);
        if (dir.findChild(fileName) != null) {
            Say.warn(project, "כבר יש קובץ " + fileName + " בתיקייה — בוחרים שם אחר.", ClassGen.TITLE);
            return null;
        }
        try {
            VirtualFile f = ProjectFiles.create(project, new File(dir.getPath()), fileName, code);
            ProjectFiles.open(project, f, ClassGen.classLine(code));
            return f;
        } catch (IOException ex) {
            Say.error(project, Say.failed("לא הצלחתי ליצור את המחלקה", ex), ClassGen.TITLE);
            return null;
        }
    }

    /** הקבצים שכבר יש בתיקייה (Student.java ← Student) */
    static Map<String, String> taken(VirtualFile dir) {
        Map<String, String> t = new LinkedHashMap<String, String>();
        for (VirtualFile c : dir.getChildren())
            if (!c.isDirectory() && c.getName().endsWith(".java")) t.put(c.getName().substring(0, c.getName().length() - 5), c.getName());
        return t;
    }

    /** כל המחלקות בתיקיות הקוד של המודול (ובלי מודול — בתיקיית הפרויקט): לרשימת הטיפוסים, ל-import ולבדיקת שם כפול */
    static List<ClassGen.Known> known(Project project, VirtualFile near) {
        List<File> roots = new ArrayList<File>();
        Module m = ModuleUtilCore.findModuleForFile(near, project);
        if (m != null) for (VirtualFile r : ModuleRootManager.getInstance(m).getSourceRoots(false)) roots.add(new File(r.getPath()));
        if (roots.isEmpty() && project.getBasePath() != null) roots.add(new File(project.getBasePath()));
        List<ClassGen.Known> out = new ArrayList<ClassGen.Known>();
        Set<String> seen = new HashSet<String>();
        for (File r : roots)
            for (ClassGen.Known k : ClassGen.scanProject(r, ClassGen.JAVA))
                if (seen.add(k.name + "|" + k.pkg)) out.add(k);
        return out;
    }
}

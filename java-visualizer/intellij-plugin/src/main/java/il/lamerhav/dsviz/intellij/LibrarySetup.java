package il.lamerhav.dsviz.intellij;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * ספריית הקורס (unit4.collectionsLib — Stack, Queue, Node, BinNode...) בכל מודול Java — כמו LibraryEverywhere ב-Eclipse:
 * אם המחלקות עוד לא מוכרות במודול (ואין בו module-info.java), מעתיקים את dsviz.jar לתיקייה lib של המודול
 * ומוסיפים אותו כספרייה של המודול, עם קוד המקור (src/ שבתוך ה-jar — Ctrl+לחיצה על Stack פותח את הקוד).
 * מודולים של Gradle / Maven לא משנים (שם הייבוא מחדש מוחק ספריות שהוספו ידנית) — ההרצה הוויזואלית בכל זאת מוסיפה את הספרייה.
 * ספרייה אחרת של unit4 (למשל ה-jar המקורי של הקורס) נשארת; בהרצה הוויזואלית הספרייה שלנו לפניה.
 */
public final class LibrarySetup {
    private LibrarySetup() {}

    static final String PROBE = "unit4.collectionsLib.Queue";
    static final String LIB_NAME = "dsviz";

    /** כל המודולים בפרויקט (כשהאינדקס מוכן). רץ בהפתיחה של פרויקט, ושוב כשנוסף מודול */
    public static void ensureAll(Project project) {
        if (!Dsviz.on() || project.isDisposed()) return;
        DumbService.getInstance(project).runWhenSmart(() -> {
            List<Module> need = ReadAction.compute(() -> {
                List<Module> l = new ArrayList<Module>();
                if (project.isDisposed()) return l;
                for (Module m : ModuleManager.getInstance(project).getModules()) if (needs(m)) l.add(m);
                return l;
            });
            for (Module m : need) add(m);
            refreshCopies(project);
        });
    }

    /** המודול צריך את הספרייה? (מודול Java, לא Gradle/Maven, בלי module-info, ו-unit4.collectionsLib.Queue לא מוכר בו). קריאה בלבד */
    public static boolean needs(Module m) {
        if (m.isDisposed() || !isJava(m) || external(m) || hasLibrary(m)) return false;
        try {
            if (JavaPsiFacade.getInstance(m.getProject()).findClass(PROBE, m.getModuleWithDependenciesAndLibrariesScope(false)) != null) return false;
            return !hasModuleInfo(m);
        } catch (com.intellij.openapi.project.IndexNotReadyException e) {
            return false;   // האינדקס עוד נבנה — ננסה שוב כשיהיה מוכן (ensureAll)
        }
    }

    static boolean isJava(Module m) {
        return "JAVA_MODULE".equals(ModuleType.get(m).getId());
    }

    static boolean external(Module m) {
        try {
            return ExternalSystemModulePropertyManager.getInstance(m).getExternalSystemId() != null;
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean hasModuleInfo(Module m) {
        GlobalSearchScope scope = m.getModuleContentScope();
        return !FilenameIndex.getVirtualFilesByName("module-info.java", scope).isEmpty();
    }

    /**
     * מוסיף את הספרייה למודול (בחוט של המסך, בפעולת כתיבה). אם למודול יש תיקייה — העותק נכנס ל-lib/dsviz.jar שלו
     * (הפרויקט נשאר שלם גם במחשב אחר, וגם ב-Eclipse / VS Code); אחרת — ה-jar שבתוך התוסף.
     */
    public static void add(Module m) {
        File src = Dsviz.libraryJar();
        if (src == null) return;
        Runnable r = () -> {
            if (m.isDisposed() || hasLibrary(m)) return;
            File jar = copyInto(m, src);
            VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
            if (vf == null) return;
            String url = "jar://" + vf.getPath() + "!/";
            ModuleRootModificationUtil.updateModel(m, model -> {
                LibraryTable table = model.getModuleLibraryTable();
                Library lib = table.createLibrary(LIB_NAME);
                Library.ModifiableModel lm = lib.getModifiableModel();
                lm.addRoot(url, OrderRootType.CLASSES);
                lm.addRoot(url + "src", OrderRootType.SOURCES);
                lm.commit();
            });
        };
        if (ApplicationManager.getApplication().isDispatchThread()) r.run();
        else ApplicationManager.getApplication().invokeAndWait(r);
    }

    /** כבר יש ספרייה בשם dsviz במודול */
    public static boolean hasLibrary(Module m) {
        for (com.intellij.openapi.roots.OrderEntry e : ModuleRootManager.getInstance(m).getOrderEntries()) {
            if (e instanceof com.intellij.openapi.roots.LibraryOrderEntry && LIB_NAME.equals(((com.intellij.openapi.roots.LibraryOrderEntry) e).getLibraryName()))
                return true;
        }
        return false;
    }

    /** lib/dsviz.jar בתיקייה של המודול (מעודכן לגרסה של התוסף), או ה-jar של התוסף אם אין תיקייה / אי אפשר לכתוב */
    static File copyInto(Module m, File src) {
        VirtualFile[] roots = ModuleRootManager.getInstance(m).getContentRoots();
        if (roots.length == 0 || roots[0].getFileSystem() != LocalFileSystem.getInstance()) return src;
        File dest = new File(roots[0].getPath(), "lib" + File.separator + Dsviz.JAR_NAME);
        try {
            if (!dest.isFile() || !Arrays.equals(Files.readAllBytes(dest.toPath()), Files.readAllBytes(src.toPath()))) {
                dest.getParentFile().mkdirs();
                Files.copy(src.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return dest;
        } catch (Exception e) {
            return src;
        }
    }

    /** אחרי שדרוג התוסף: lib/dsviz.jar בכל מודול שיש בו את הספרייה שלנו מתעדכן לגרסה החדשה */
    static void refreshCopies(Project project) {
        File src = Dsviz.libraryJar();
        if (src == null) return;
        for (Module m : ModuleManager.getInstance(project).getModules()) {
            if (m.isDisposed() || !hasLibrary(m)) continue;
            VirtualFile[] roots = ModuleRootManager.getInstance(m).getContentRoots();
            if (roots.length == 0) continue;
            File dest = new File(roots[0].getPath(), "lib" + File.separator + Dsviz.JAR_NAME);
            try {
                if (dest.isFile() && dest.length() != src.length() || dest.isFile() && !Arrays.equals(Files.readAllBytes(dest.toPath()), Files.readAllBytes(src.toPath()))) {
                    Files.copy(src.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dest);
                    if (vf != null) vf.refresh(true, false);
                }
            } catch (Exception ignored) {
                // נשארת הגרסה הקודמת
            }
        }
    }

    /**
     * לפרויקט בלי JDK: רושמים את ה-Java 21 שההתקנה התקינה ("DSViz Java 21") ומגדירים אותה לפרויקט — כמו Eclipse (bundledJre).
     * מחזיר true אם הוגדר.
     */
    public static boolean ensureSdk(Project project) {
        if (!Dsviz.on() || ProjectRootManager.getInstance(project).getProjectSdk() != null) return false;
        String home = Dsviz.dsvizFile("jdk-path.txt");
        if (home == null || JdkFinder.javaExe(new File(home)) == null) return false;
        boolean[] done = { false };
        ApplicationManager.getApplication().invokeAndWait(() -> WriteAction.run(() -> {
            if (project.isDisposed() || ProjectRootManager.getInstance(project).getProjectSdk() != null) return;
            ProjectJdkTable table = ProjectJdkTable.getInstance();
            Sdk sdk = null;
            for (Sdk s : table.getAllJdks()) if (home.equals(s.getHomePath())) sdk = s;
            if (sdk == null) {
                sdk = JavaSdk.getInstance().createJdk("DSViz Java 21", home, false);
                table.addJdk(sdk);
            }
            ProjectRootManager.getInstance(project).setProjectSdk(sdk);
            done[0] = true;
        }));
        return done[0];
    }
}

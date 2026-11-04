package il.lamerhav.dsviz.intellij;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.search.PsiSearchHelper;
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
 * מודול עם module-info.java: הספרייה נוספת רק אם הקוד באמת משתמש בה (import unit4...), ובמודול נכתב "requires dsviz;" — בלי זה javac
 * לא נותן למודול לראות אותה ("package unit4.collectionsLib is not visible").
 * מודולים של Gradle / Maven לא משנים מעצמם (שם הייבוא מחדש מוחק ספריות שהוספו ידנית). רק בלחיצה על "הרצה ויזואלית", כשהקוד משתמש בספרייה
 * והיא חסרה: התלות נכתבת ב-pom.xml / build.gradle (lib/dsviz.jar בתיקיית הפרויקט), ו-IntelliJ מתבקש לטעון מחדש.
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
            for (Module m : ModuleManager.getInstance(project).getModules()) if (ReadAction.compute(() -> needsRequires(m))) addRequires(m);
            refreshCopies(project);
        });
    }

    /** המודול צריך את הספרייה? (מודול Java, לא Gradle/Maven, ו-unit4.collectionsLib.Queue לא מוכר בו; עם module-info — רק אם הקוד משתמש בה). קריאה בלבד */
    public static boolean needs(Module m) {
        if (m.isDisposed() || !isJava(m) || externalModule(m) || hasLibrary(m)) return false;
        try {
            if (JavaPsiFacade.getInstance(m.getProject()).findClass(PROBE, m.getModuleWithDependenciesAndLibrariesScope(false)) != null) return false;
            return !hasModuleInfo(m) || usesCourseLibrary(m);   // מודול (module-info) — רק אם הקוד באמת משתמש בספרייה
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

    /** יש בקוד של המודול import (או שימוש) ב-unit4 — הספרייה של הקורס. קריאה בלבד */
    static boolean usesCourseLibrary(Module m) {
        boolean[] found = { false };
        PsiSearchHelper.getInstance(m.getProject()).processAllFilesWithWord("unit4", m.getModuleContentScope(), f -> {
            if ("JAVA".equals(f.getFileType().getName())) found[0] = true;
            return !found[0];
        }, true);
        return found[0];
    }

    /** module-info.java של המודול (הראשון), או null */
    private static VirtualFile moduleInfo(Module m) {
        java.util.Collection<VirtualFile> l = FilenameIndex.getVirtualFilesByName("module-info.java", m.getModuleContentScope());
        return l.isEmpty() ? null : l.iterator().next();
    }

    /** במודול יש module-info.java, הספרייה כבר בו והקוד משתמש בה — אבל חסר "requires dsviz;". קריאה בלבד */
    public static boolean needsRequires(Module m) {
        if (m.isDisposed() || externalModule(m) || !hasLibrary(m)) return false;
        try {
            VirtualFile mi = moduleInfo(m);
            if (mi == null || !usesCourseLibrary(m)) return false;
            Document d = FileDocumentManager.getInstance().getDocument(mi);
            return d != null && BuildFiles.requires(d.getText(), LIB_NAME) != null;
        } catch (com.intellij.openapi.project.IndexNotReadyException e) {
            return false;
        }
    }

    /** כותב "requires dsviz;" ב-module-info.java (פעולת כתיבה של IntelliJ — אפשר לבטל ב-Ctrl+Z). מחזיר true אם נכתב */
    public static boolean addRequires(Module m) {
        boolean[] done = { false };
        Runnable r = () -> {
            VirtualFile mi = ReadAction.compute(() -> moduleInfo(m));
            Document d = mi == null ? null : FileDocumentManager.getInstance().getDocument(mi);
            String edited = d == null ? null : BuildFiles.requires(d.getText(), LIB_NAME);
            if (edited == null) return;
            WriteCommandAction.runWriteCommandAction(m.getProject(), "DSViz: requires " + LIB_NAME, null, () -> d.setText(edited));
            FileDocumentManager.getInstance().saveDocument(d);
            done[0] = true;
        };
        if (ApplicationManager.getApplication().isDispatchThread()) r.run();
        else ApplicationManager.getApplication().invokeAndWait(r);
        return done[0];
    }

    // ------------------------------------------------------------------ Maven / Gradle

    /** לבדיקות: מי נחשב מודול של Maven / Gradle (במקום ExternalSystemModulePropertyManager) */
    static volatile java.util.function.Predicate<Module> externalOverride;

    static boolean externalModule(Module m) {
        java.util.function.Predicate<Module> o = externalOverride;
        return o != null ? o.test(m) : external(m);
    }

    /** מודול של Maven / Gradle שהקוד שלו משתמש בספרייה, והיא לא מוכרת בו ולא כתובה בקובץ ההגדרות. קריאה בלבד */
    public static boolean needsBuildFile(Module m) {
        if (m.isDisposed() || !isJava(m) || !externalModule(m) || hasLibrary(m)) return false;
        try {
            if (JavaPsiFacade.getInstance(m.getProject()).findClass(PROBE, m.getModuleWithDependenciesAndLibrariesScope(false)) != null) return false;
            return usesCourseLibrary(m) && buildFile(m) != null;
        } catch (com.intellij.openapi.project.IndexNotReadyException e) {
            return false;
        }
    }

    /** קובץ ההגדרות של הפרויקט (pom.xml / build.gradle.kts / build.gradle) — ראשון שנמצא בתיקיית הפרויקט */
    static VirtualFile buildFile(Module m) {
        VirtualFile root = buildRoot(m);
        if (root == null) return null;
        for (String n : new String[] { "pom.xml", "build.gradle.kts", "build.gradle" }) {
            VirtualFile f = root.findChild(n);
            if (f != null) return f;
        }
        return null;
    }

    /** תיקיית הפרויקט של Maven / Gradle: זו ש-IntelliJ קישר למודול, ואם אין — תיקיית התוכן של המודול או אחת מעליה */
    private static VirtualFile buildRoot(Module m) {
        try {
            String linked = ExternalSystemModulePropertyManager.getInstance(m).getLinkedProjectPath();
            if (linked != null) {
                VirtualFile v = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(linked));
                if (v != null && v.isDirectory() && hasBuildFile(v)) return v;
            }
        } catch (Throwable ignored) {
            // נמשיך לחפש
        }
        for (VirtualFile r : ModuleRootManager.getInstance(m).getContentRoots()) {
            VirtualFile d = r;
            for (int i = 0; d != null && i < 4; i++, d = d.getParent()) if (hasBuildFile(d)) return d;
        }
        return null;
    }

    private static boolean hasBuildFile(VirtualFile d) {
        return d.findChild("pom.xml") != null || d.findChild("build.gradle.kts") != null || d.findChild("build.gradle") != null;
    }

    /**
     * כותב את התלות ב-pom.xml / build.gradle (ומעתיק את הספרייה ל-lib/dsviz.jar בתיקיית הפרויקט), ומבקש מ-IntelliJ לטעון מחדש.
     * מחזיר את שם הקובץ שנערך (או שכבר כתוב בו ועוד לא נטען), או null אם לא נערך כלום.
     */
    public static String addToBuildFile(Module m) {
        File src = Dsviz.libraryJar();
        if (src == null) return null;
        String[] name = { null };
        Runnable r = () -> {
            VirtualFile f = ReadAction.compute(() -> buildFile(m));
            if (f == null || f.getParent() == null) return;
            File dir = new File(f.getParent().getPath());
            File jar = copyJar(dir, src);
            if (jar.equals(src)) return;   // אי אפשר לכתוב ל-lib — לא מכניסים תלות בקובץ שלא קיים
            LocalFileSystem.getInstance().refreshAndFindFileByIoFile(jar);
            Document d = FileDocumentManager.getInstance().getDocument(f);
            if (d == null) return;
            String edited = "pom.xml".equals(f.getName()) ? BuildFiles.maven(d.getText()) : BuildFiles.gradle(d.getText(), f.getName().endsWith(".kts"));
            if (edited != null) {
                WriteCommandAction.runWriteCommandAction(m.getProject(), "DSViz: ספריית הקורס", null, () -> d.setText(edited));
                FileDocumentManager.getInstance().saveDocument(d);
            } else if (!d.getText().toLowerCase(java.util.Locale.ROOT).contains("dsviz")) {
                return;   // לא הצלחנו להכניס (קובץ שלא מכירים) — לא טוענים מחדש
            }
            name[0] = f.getName();   // נכתב עכשיו, או שכבר כתוב ועוד לא נטען — בשני המקרים מחכים לטעינה מחדש
        };
        if (ApplicationManager.getApplication().isDispatchThread()) r.run();
        else ApplicationManager.getApplication().invokeAndWait(r);
        if (name[0] != null) reloadExternal(m.getProject(), name[0]);
        return name[0];
    }

    /** מבקש מ-IntelliJ לטעון מחדש את פרויקט Maven / Gradle (אם הפעולה לא קיימת בגרסה הזאת — המורה רואה את "Load changes") */
    private static void reloadExternal(Project project, String file) {
        String id = "pom.xml".equals(file) ? "Maven.Reimport" : "ExternalSystem.RefreshAllProjects";
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                com.intellij.openapi.actionSystem.AnAction a = com.intellij.openapi.actionSystem.ActionManager.getInstance().getAction(id);
                if (a == null || project.isDisposed()) return;
                com.intellij.openapi.actionSystem.ActionManager.getInstance().tryToExecute(a, null, null, null, true);
            } catch (Throwable ignored) {
                // ייטען כשיאשרו את ההצעה של IntelliJ
            }
        });
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
        if (ReadAction.compute(() -> hasModuleInfo(m) && usesCourseLibrary(m))) addRequires(m);   // module-info: בלי requires הספרייה לא נראית למודול
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
        return copyJar(new File(roots[0].getPath()), src);
    }

    /**
     * ‎<dir>/lib/dsviz.jar — מעתיקים אם חסר או שונה (גרסה אחרת מוחלפת בגרסה של התוסף). אי אפשר לכתוב שם (תיקייה לקריאה בלבד,
     * lib שהוא קובץ, הקובץ נעול) — ה-jar שבתוך התוסף, בלי הודעה ובלי חריגה.
     */
    static File copyJar(File dir, File src) {
        File dest = new File(dir, "lib" + File.separator + Dsviz.JAR_NAME);
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

package il.lamerhav.dsviz.intellij;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;

import org.jetbrains.jps.model.java.JavaSourceRootType;

/** קבצים בפרויקט: תיקיות הקוד, יצירת קובץ ופתיחתו, הדוגמאות הפתורות */
public final class ProjectFiles {
    private ProjectFiles() {}

    public static final List<String> EXAMPLES = Arrays.asList("StackExamples", "QueueExamples", "ListExamples", "TreeExamples",
            "ArrayExamples", "StudentExamples", "CarExamples", "RecursionExamples", "NestedExamples");

    /** תיקיות הקוד (לא בדיקות) של כל המודולים. אם אין בכלל — src חדשה בתיקיית הפרויקט */
    static List<ExerciseDialog.Target> targets(Project project) {
        List<ExerciseDialog.Target> out = ReadAction.compute(() -> {
            List<ExerciseDialog.Target> l = new ArrayList<ExerciseDialog.Target>();
            for (Module m : ModuleManager.getInstance(project).getModules()) {
                for (VirtualFile r : ModuleRootManager.getInstance(m).getSourceRoots(JavaSourceRootType.SOURCE)) {
                    if (r.getFileSystem() != LocalFileSystem.getInstance()) continue;
                    String rel = project.getBasePath() != null && r.getPath().startsWith(project.getBasePath())
                            ? r.getPath().substring(project.getBasePath().length()).replaceFirst("^/", "") : r.getPath();
                    l.add(new ExerciseDialog.Target(new File(r.getPath()), (rel.isEmpty() ? r.getName() : rel) + "  (" + m.getName() + ")"));
                }
            }
            return l;
        });
        if (out.isEmpty() && project.getBasePath() != null) out.add(new ExerciseDialog.Target(new File(project.getBasePath(), "src"), "src (תיקייה חדשה)"));
        return out;
    }

    /** התיקייה היא תיקיית קוד? אם לא — יוצרים אותה ומסמנים אותה כתיקיית קוד במודול הראשון (בחוט של המסך) */
    static void ensureSourceRoot(Project project, File dir) throws IOException {
        dir.mkdirs();
        VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
        if (vf == null) throw new IOException("לא הצלחתי ליצור את התיקייה " + dir);
        Module owner = ModuleUtilCore.findModuleForFile(vf, project);
        boolean isRoot = owner != null && Arrays.asList(ModuleRootManager.getInstance(owner).getSourceRoots(false)).contains(vf);
        if (isRoot) return;
        Module[] modules = ModuleManager.getInstance(project).getModules();
        Module m = owner != null ? owner : modules.length > 0 ? modules[0] : null;
        if (m == null) return;
        VirtualFile base = project.getBasePath() == null ? vf.getParent() : LocalFileSystem.getInstance().findFileByPath(project.getBasePath());
        ModuleRootModificationUtil.updateModel(m, model -> {
            ContentEntry entry = null;
            for (ContentEntry e : model.getContentEntries()) if (e.getFile() != null && VfsUtilCore.isAncestor(e.getFile(), vf, false)) entry = e;
            if (entry == null) entry = model.addContentEntry(base != null && VfsUtilCore.isAncestor(base, vf, false) ? base : vf);
            entry.addSourceFolder(vf, false);
        });
    }

    /** יוצר קובץ (UTF-8) בתיקייה ומחזיר אותו */
    static VirtualFile create(Project project, File dir, String name, String content) throws IOException {
        return WriteAction.computeAndWait(() -> {
            VirtualFile d = VfsUtil.createDirectoryIfMissing(dir.getPath());
            if (d == null) throw new IOException("לא הצלחתי ליצור את התיקייה " + dir);
            VirtualFile f = d.findChild(name);
            if (f == null) f = d.createChildData(ProjectFiles.class, name);
            f.setCharset(java.nio.charset.StandardCharsets.UTF_8);
            VfsUtil.saveText(f, content);
            return f;
        });
    }

    static void open(Project project, VirtualFile f, int line) {
        new OpenFileDescriptor(project, f, Math.max(0, line), 0).navigate(true);
    }

    /** מעתיק את הדוגמאות הפתורות לתיקייה examples בתיקיית הקוד (רק מה שחסר). מחזיר את הקבצים */
    static List<VirtualFile> copyExamples(Project project, File src) throws IOException {
        List<VirtualFile> out = new ArrayList<VirtualFile>();
        File dir = new File(src, "examples");
        for (String name : EXAMPLES) {
            File f = new File(dir, name + ".java");
            if (f.isFile()) {
                VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
                if (vf != null) out.add(vf);
                continue;
            }
            out.add(create(project, dir, name + ".java", Dsviz.resourceText("/resources/examples/" + name + ".java")));
        }
        return out;
    }

    /** השורה הראשונה של ההסבר (‎/** ... *‎/) בראש קובץ דוגמה */
    static String describeExample(String name) {
        try {
            String text = Dsviz.resourceText("/resources/examples/" + name + ".java");
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("/\\*\\*\\s*(.*?)\\s*\\*/", java.util.regex.Pattern.DOTALL).matcher(text);
            if (m.find()) return m.group(1).replaceAll("\\s*\\n\\s*\\*\\s*", " ").replaceAll("^\\*\\s*", "").trim();
        } catch (IOException ignored) {
            // בלי הסבר
        }
        return "";
    }
}

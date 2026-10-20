package il.lamerhav.dsviz;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.JavaRuntime;

/** הכנת פרויקט Java לתרגילים: ספריית dsviz.jar ב-Build Path, תיקיית קוד, קידוד UTF-8 */
public final class ProjectSetup {
    private ProjectSetup() {}

    public static final String JAR_NAME = "dsviz.jar";
    public static final String DEFAULT_PROJECT = "DataStructures";
    public static final List<String> EXAMPLES = Arrays.asList("StackExamples", "QueueExamples", "ListExamples", "TreeExamples",
            "ArrayExamples", "StudentExamples", "CarExamples");
    private static final String MARKER = "unit4/collectionsLib/Stack.class";

    /** מה לעשות עם ספריית unit4 אחרת שנמצאה ב-Build Path (למשל ה-jar המקורי של הבגרות) */
    public interface ConflictResolver {
        /** מחזיר true כדי להסיר את הספריות האלה מה-Build Path */
        boolean removeConflicts(List<String> names);
    }

    public static List<IJavaProject> javaProjects() {
        List<IJavaProject> res = new ArrayList<IJavaProject>();
        for (IProject p : ResourcesPlugin.getWorkspace().getRoot().getProjects()) {
            try {
                if (p.isOpen() && p.hasNature(JavaCore.NATURE_ID)) res.add(JavaCore.create(p));
            } catch (CoreException ignored) {}
        }
        return res;
    }

    /** שם פנוי לפרויקט חדש: DataStructures, DataStructures2, ... */
    public static String freeProjectName() {
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        String name = DEFAULT_PROJECT;
        for (int i = 2; root.getProject(name).exists(); i++) name = DEFAULT_PROJECT + i;
        return name;
    }

    public static IJavaProject createProject(String name) throws CoreException {
        IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject(name);
        p.create(null);
        p.open(null);
        IProjectDescription d = p.getDescription();
        d.setNatureIds(new String[] { JavaCore.NATURE_ID });
        d.setComment("תרגילי מבני נתונים עם הרצה ויזואלית");
        p.setDescription(d, null);
        p.setDefaultCharset("UTF-8", null);
        IFolder src = p.getFolder("src");
        src.create(true, true, null);
        IFolder bin = p.getFolder("bin");
        if (!bin.exists()) bin.create(true, true, null);
        IJavaProject jp = JavaCore.create(p);
        jp.setRawClasspath(new IClasspathEntry[] {
                JavaCore.newSourceEntry(src.getFullPath()),
                JavaRuntime.getDefaultJREContainerEntry() }, bin.getFullPath(), null);
        return jp;
    }

    /**
     * מוודא שהספרייה נמצאת בפרויקט וב-Build Path. מחזיר את תיקיית הקוד שבה יוצרים תרגילים.
     */
    public static IContainer prepare(IJavaProject jp, ConflictResolver resolver) throws CoreException {
        IProject p = jp.getProject();
        IFolder lib = p.getFolder("lib");
        if (!lib.exists()) lib.create(true, true, null);
        IFile jar = lib.getFile(JAR_NAME);
        byte[] fresh = resource("/resources/" + JAR_NAME);
        if (!jar.exists()) {
            jar.create(new ByteArrayInputStream(fresh), true, null);
        } else if (!Arrays.equals(read(jar), fresh)) {
            jar.setContents(new ByteArrayInputStream(fresh), true, false, null);
        }

        IContainer src = null;
        List<IClasspathEntry> entries = new ArrayList<IClasspathEntry>(Arrays.asList(jp.getRawClasspath()));
        boolean hasJar = false, changed = false;
        List<IClasspathEntry> conflicts = new ArrayList<IClasspathEntry>();
        List<String> conflictNames = new ArrayList<String>();
        for (IClasspathEntry e : entries) {
            if (e.getEntryKind() == IClasspathEntry.CPE_SOURCE && src == null) {
                IResource r = ResourcesPlugin.getWorkspace().getRoot().findMember(e.getPath());
                if (r instanceof IContainer) src = (IContainer) r;
            }
            if (e.getEntryKind() == IClasspathEntry.CPE_LIBRARY) {
                if (e.getPath().equals(jar.getFullPath())) hasJar = true;
                else if (containsUnit4(e.getPath())) {
                    conflicts.add(e);
                    conflictNames.add(e.getPath().lastSegment());
                }
            }
        }
        if (!conflicts.isEmpty() && resolver != null && resolver.removeConflicts(conflictNames)) {
            entries.removeAll(conflicts);
            changed = true;
        }
        if (!hasJar) {
            entries.add(JavaCore.newLibraryEntry(jar.getFullPath(), jar.getFullPath(), new Path("src")));
            changed = true;
        }
        if (src == null) {
            IFolder f = p.getFolder("src");
            if (!f.exists()) f.create(true, true, null);
            entries.add(0, JavaCore.newSourceEntry(f.getFullPath()));
            src = f;
            changed = true;
        }
        if (changed) jp.setRawClasspath(entries.toArray(new IClasspathEntry[0]), null);
        return src;
    }

    /** תיקיית הקוד הקיימת (בלי לשנות כלום), או null */
    public static IContainer existingSource(IJavaProject jp) {
        try {
            for (IPackageFragmentRoot r : jp.getPackageFragmentRoots()) {
                if (r.getKind() == IPackageFragmentRoot.K_SOURCE && r.getCorrespondingResource() instanceof IContainer)
                    return (IContainer) r.getCorrespondingResource();
            }
        } catch (CoreException ignored) {}
        return null;
    }

    public static IFile createFile(IContainer folder, String name, String content) throws CoreException {
        IFile f = folder.getFile(new Path(name));
        f.create(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), true, null);
        f.setCharset("UTF-8", null);
        return f;
    }

    /** מעתיק את הדוגמאות הפתורות ל-src/examples. מחזיר את הקבצים */
    public static List<IFile> copyExamples(IContainer src) throws CoreException {
        IFolder dir = src.getFolder(new Path("examples"));
        if (!dir.exists()) dir.create(true, true, null);
        List<IFile> res = new ArrayList<IFile>();
        for (String name : EXAMPLES) {
            IFile f = dir.getFile(name + ".java");
            if (!f.exists()) {
                f.create(new ByteArrayInputStream(resource("/resources/examples/" + name + ".java")), true, null);
                f.setCharset("UTF-8", null);
            }
            res.add(f);
        }
        return res;
    }

    /** Targil1, Targil2 ... — השם הפנוי הבא בתיקייה */
    public static String nextClassName(IContainer src) {
        for (int i = 1; ; i++) {
            if (src == null || !src.getFile(new Path("Targil" + i + ".java")).exists()) return "Targil" + i;
        }
    }

    private static boolean containsUnit4(IPath path) {
        IResource r = ResourcesPlugin.getWorkspace().getRoot().findMember(path);
        File file = r != null && r.getLocation() != null ? r.getLocation().toFile() : path.toFile();
        if (!file.isFile()) return false;
        try (ZipFile z = new ZipFile(file)) {
            return z.getEntry(MARKER) != null;
        } catch (IOException e) {
            return false;
        }
    }

    static byte[] resource(String name) throws CoreException {
        try (InputStream in = ProjectSetup.class.getResourceAsStream(name)) {
            if (in == null) throw new IOException("missing " + name);
            return readAll(in);
        } catch (IOException e) {
            throw new CoreException(new org.eclipse.core.runtime.Status(org.eclipse.core.runtime.IStatus.ERROR,
                    "il.lamerhav.dsviz", "קובץ חסר בתוסף: " + name, e));
        }
    }

    private static byte[] read(IFile f) throws CoreException {
        try (InputStream in = f.getContents(true)) {
            return readAll(in);
        } catch (IOException e) {
            return new byte[0];
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) > 0;) out.write(buf, 0, n);
        return out.toByteArray();
    }
}

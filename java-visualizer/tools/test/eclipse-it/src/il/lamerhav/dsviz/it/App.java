package il.lamerhav.dsviz.it;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.compiler.CategorizedProblem;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;

/**
 * בדיקה בתוך Eclipse אמיתי של "המורה בעברית": פרויקט Java עם קוד של תלמידים, בנייה (סימונים ב-Problems),
 * עבודה בעורך (reconcile — מה שמופיע בזמן הכתיבה), וכיבוי מהתפריט (ההעדפה) — שאחריו לא נשאר אף סימון.
 *   eclipse -application il.lamerhav.dsviz.it.app <dsviz.jar> <cases-dir> <out.txt>
 */
public class App implements IApplication {
    private static final String MARKER = "il.lamerhav.dsviz.mentor";

    @Override
    public Object start(IApplicationContext context) throws Exception {
        String[] a = (String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS);
        PrintStream out = new PrintStream(new File(a[2]), "UTF-8");
        try {
            run(new File(a[0]), new File(a[1]), out);
        } catch (Throwable t) {
            t.printStackTrace(out);
        }
        out.close();
        return IApplication.EXIT_OK;
    }

    private void run(File lib, File cases, PrintStream out) throws Exception {
        IWorkspace ws = ResourcesPlugin.getWorkspace();
        IWorkspaceDescription wd = ws.getDescription();
        wd.setAutoBuilding(false);
        ws.setDescription(wd);
        IProject p = ws.getRoot().getProject("Students");
        if (p.exists()) p.delete(true, true, null);
        p.create(null);
        p.open(null);
        IProjectDescription d = p.getDescription();
        d.setNatureIds(new String[] { JavaCore.NATURE_ID });
        p.setDescription(d, null);
        IJavaProject jp = JavaCore.create(p);
        IFolder src = p.getFolder("src"), libDir = p.getFolder("lib");
        src.create(true, true, null);
        libDir.create(true, true, null);
        IFile jar = libDir.getFile("dsviz.jar");
        try (FileInputStream in = new FileInputStream(lib)) {
            jar.create(in, true, null);
        }
        jp.setRawClasspath(new IClasspathEntry[] { JavaCore.newSourceEntry(src.getFullPath()),
                JavaCore.newContainerEntry(new org.eclipse.core.runtime.Path("org.eclipse.jdt.launching.JRE_CONTAINER")),
                JavaCore.newLibraryEntry(jar.getFullPath(), null, null) }, p.getFolder("bin").getFullPath(), null);
        jp.setOption(JavaCore.COMPILER_COMPLIANCE, "11");
        jp.setOption(JavaCore.COMPILER_SOURCE, "11");
        jp.setOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, "11");
        List<IFile> files = new ArrayList<IFile>();
        File[] list = cases.listFiles((dir, n) -> n.endsWith(".java"));
        java.util.Arrays.sort(list);
        for (File f : list) {
            IFile file = src.getFile(f.getName());
            file.create(new java.io.ByteArrayInputStream(Files.readAllBytes(f.toPath())), true, null);
            file.setCharset("UTF-8", null);
            files.add(file);
        }

        // 1. בנייה: סימונים ב-Problems
        setMentor(true);
        p.build(IncrementalProjectBuilder.FULL_BUILD, null);
        int mentor = 0;
        for (IFile f : files) {
            for (IMarker m : f.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_ZERO)) {
                boolean ours = MARKER.equals(m.getType());
                if (ours) mentor++;
                out.println("BUILD " + f.getName() + ":" + m.getAttribute(IMarker.LINE_NUMBER, -1) + " " + (ours ? "mentor" : "jdt")
                        + (m.getAttribute(IMarker.SEVERITY, 0) == IMarker.SEVERITY_ERROR ? " error " : " warning ") + clean(m.getAttribute(IMarker.MESSAGE, "")));
            }
        }
        out.println("BUILD-MENTOR-COUNT " + mentor);

        // 2. עורך: בזמן הכתיבה (reconcile) — מוחקים נקודה-פסיק בקובץ הראשון ובודקים שההסבר מופיע מיד
        IFile first = src.getFile("Targil.java");
        ICompilationUnit cu = JavaCore.createCompilationUnitFrom(first);
        final List<IProblem> got = new ArrayList<IProblem>();
        final IProblemRequestor req = new IProblemRequestor() {
            @Override
            public void acceptProblem(IProblem problem) {
                got.add(problem);
            }

            @Override
            public void beginReporting() {
                got.clear();
            }

            @Override
            public void endReporting() {
            }

            @Override
            public boolean isActive() {
                return true;
            }
        };
        WorkingCopyOwner owner = new WorkingCopyOwner() {
            @Override
            public IProblemRequestor getProblemRequestor(ICompilationUnit workingCopy) {
                return req;
            }
        };
        ICompilationUnit wc = cu.getWorkingCopy(owner, null);
        String text = wc.getSource();
        String edited = text.replaceFirst("(?m)^(\\s*int\\s+count\\s*=\\s*0);", "$1");
        wc.getBuffer().setContents(edited);
        wc.reconcile(AST.getJLSLatest(), true, owner, null);
        for (IProblem pr : got) {
            String type = pr instanceof CategorizedProblem ? ((CategorizedProblem) pr).getMarkerType() : "?";
            out.println("RECONCILE " + first.getName() + ":" + pr.getSourceLineNumber() + " " + (MARKER.equals(type) ? "mentor" : "jdt") + " "
                    + clean(pr.getMessage()));
        }
        wc.discardWorkingCopy();

        // 2ב. הרצה ויזואלית: קומפילציה לפני ההרצה — עם שגיאות לא מריצים (RunVisualHandler.compileErrors)
        for (IFile f : files) {
            List<String> errs = new ArrayList<String>();
            il.lamerhav.dsviz.RunVisualHandler.compileErrors(f, errs);
            out.println("RUNCHECK " + f.getName() + " errors=" + errs.size() + (errs.isEmpty() ? "" : " first=" + clean(errs.get(0)).replace("\n", " | ")));
        }

        // 3. כיבוי מהתפריט: אחרי בנייה מלאה — אין אף סימון שלנו
        setMentor(false);
        p.build(IncrementalProjectBuilder.FULL_BUILD, null);
        int off = 0;
        for (IFile f : files) for (IMarker m : f.findMarkers(MARKER, true, IResource.DEPTH_ZERO)) off++;
        out.println("OFF-MENTOR-COUNT " + off);
        setMentor(true);
        p.build(IncrementalProjectBuilder.FULL_BUILD, null);
        int on = 0;
        for (IFile f : files) for (IMarker m : f.findMarkers(MARKER, true, IResource.DEPTH_ZERO)) on++;
        out.println("ON-AGAIN-MENTOR-COUNT " + on);
    }

    private static void setMentor(boolean on) throws Exception {
        IEclipsePreferences p = InstanceScope.INSTANCE.getNode("il.lamerhav.dsviz");
        p.putBoolean("mentor", on);
        p.flush();
    }

    /** בלי תווי הכיווניות (RLE / LRM / PDF) — לקריאה ולבדיקה */
    private static String clean(String s) {
        return s.replace("\u202B", "").replace("\u202C", "").replace("\u200E", "");
    }

    @Override
    public void stop() {
    }
}

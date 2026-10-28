package il.lamerhav.dsviz;

import java.io.ByteArrayInputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * "שליחת תרגיל": החלון (SubmitDialog) שולח את דף התרגיל ומקבל את התיאור שלו; כאן בונים ממנו את קובצי ה-Java — כל תרגיל בחבילה משלו,
 * עם שם לפי סוג התרגיל ומספר רץ (src/ex01_stacks, src/ex02_binary_trees...) כדי שיהיה קל למצוא ולמחוק בסוף.
 * בודקים שהכול מתקמפל (ואם לא — תיקון אחד דרך השרת), ופותחים עם הסמן במקום הפתרון של הסעיף הראשון.
 */
public class SubmitHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = HandlerUtil.getActiveShell(event);
        IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
        WorkerClient client = WorkerClient.configured();
        SubmitDialog d = new SubmitDialog(shell, ProjectSetup.javaProjects(), ProjectSetup.freeProjectName(), Ui.currentProject(event), client);
        if (d.open() != Window.OK || d.exercise() == null) return null;
        Build b = null;
        try {
            IJavaProject jp = d.project() != null ? d.project() : ProjectSetup.createProject(d.newProjectName());
            IContainer src = ProjectSetup.prepare(jp, Ui.resolver(shell));
            b = new Build(client, d.exercise(), d.sourceNames(), jp, src);
            new ProgressMonitorDialog(shell).run(true, true, b::run);
            open(page, b);
            if (!b.errors.isEmpty()) {
                MessageDialog.openWarning(shell, "שליחת תרגיל",
                        "התרגיל נוצר (בתיקייה " + b.folderName + "), אבל יש בו " + b.errors.size() + " שגיאות קומפילציה (מסומנות באדום בקוד):\n\n"
                        + String.join("\n", b.errors.subList(0, Math.min(4, b.errors.size())))
                        + "\n\nבדרך כלל זה ב-main (הנתונים לדוגמה) — אפשר לתקן, או לשלוח שוב.");
            } else if (b.tasks == 0) {
                MessageDialog.openInformation(shell, "שליחת תרגיל", "בתרגיל לא נמצאו סעיפים שצריך לכתוב בהם קוד — השאלות מופיעות בראש הקובץ.");
            }
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            MessageDialog.openError(shell, "שליחת תרגיל", "לא הצלחתי ליצור את התרגיל: " + (t != null ? t.getMessage() : e.getMessage()));
        } catch (InterruptedException e) {
            // בוטל באמצע — פותחים את מה שכבר נוצר
            try {
                if (b != null) open(page, b);
            } catch (CoreException ignored) {
                // לא נפתח — הקבצים בכל זאת בפרויקט
            }
        } catch (CoreException e) {
            Ui.error(shell, "לא הצלחתי ליצור את התרגיל.", e);
        }
        return null;
    }

    /** פותח את הקבצים: קודם המחלקות מהתרגיל, ובסוף הקובץ עם הסעיף הראשון — שיהיה הפעיל, והסמן מחכה במקום הפתרון */
    private static void open(IWorkbenchPage page, Build b) throws CoreException {
        if (b.files.isEmpty()) return;
        IFile focus = null;
        int line = 0;
        for (int i = 0; i < b.files.size(); i++) {
            if (b.generated.get(i).cursorLine >= 0) { focus = b.files.get(i); line = b.generated.get(i).cursorLine; }
        }
        if (focus == null) focus = b.files.get(0);
        for (int i = b.files.size() - 1; i >= 0; i--) if (!b.files.get(i).equals(focus)) Ui.openAt(page, b.files.get(i), 0);
        Ui.openAt(page, focus, line);
    }

    /** Java: בניית הקבצים בחבילה משלהם + בדיקת קומפילציה + תיקון אחד (רץ ברקע, עם חלון התקדמות) */
    static final class Build {
        final WorkerClient client;
        final List<String> sources;
        final IJavaProject jp;
        final IContainer src;
        Map<String, Object> raw;
        String folderName = "";
        final List<AiScaffold.GeneratedFile> generated = new ArrayList<AiScaffold.GeneratedFile>();
        final List<IFile> files = new ArrayList<IFile>();
        final List<String> errors = new ArrayList<String>();
        int tasks;

        Build(WorkerClient client, Map<String, Object> raw, List<String> sources, IJavaProject jp, IContainer src) {
            this.client = client;
            this.raw = raw;
            this.sources = sources;
            this.jp = jp;
            this.src = src;
        }

        void run(IProgressMonitor m) throws InvocationTargetException, InterruptedException {
            try {
                m.beginTask("בונה את התרגיל…", 5);
                Set<String> taken = new HashSet<String>();
                for (IResource r : src.members()) taken.add(r.getName());
                AiScaffold.Exercise e = AiScaffold.read(raw);
                String pkg = AiScaffold.folderName(e.topic, taken, false);
                folderName = pkg;
                IFolder folder = src.getFolder(new Path(pkg));
                write(e, pkg, folder);
                m.worked(1);
                m.subTask("בודק שהקוד מתקמפל…");
                compile(folder, m);
                m.worked(1);
                if (!errors.isEmpty() && client != null && !m.isCanceled()) {
                    m.subTask("יש שגיאות קומפילציה — מבקש תיקון…");
                    Map<String, String> code = new LinkedHashMap<String, String>();
                    for (AiScaffold.GeneratedFile g : generated) code.put(g.fileName(), g.code);
                    try {
                        Map<String, Object> fixed = client.repair(raw, code, errors);
                        raw = fixed;
                        AiScaffold.Exercise e2 = AiScaffold.read(fixed);
                        for (IFile f : files) f.delete(true, null);
                        files.clear();
                        generated.clear();
                        write(e2, pkg, folder);
                        m.worked(1);
                        m.subTask("בודק שוב…");
                        compile(folder, m);
                    } catch (WorkerClient.AiError ignored) {
                        // התיקון לא הצליח — נשארים עם הקבצים כמו שהם (והשגיאות מוצגות)
                    }
                }
                m.done();
            } catch (CoreException e) {
                throw new InvocationTargetException(e, e.getStatus().getMessage());
            }
        }

        private void write(AiScaffold.Exercise e, String pkg, IFolder folder) throws CoreException {
            tasks = e.tasks.size();
            if (!folder.exists()) folder.create(true, true, null);
            for (AiScaffold.GeneratedFile g : AiScaffold.build(e, pkg, sources)) {
                IFile f = folder.getFile(g.fileName());
                ByteArrayInputStream in = new ByteArrayInputStream(g.code.getBytes(StandardCharsets.UTF_8));
                if (f.exists()) f.setContents(in, true, false, null);
                else f.create(in, true, null);
                f.setCharset("UTF-8", null);
                files.add(f);
                generated.add(g);
            }
        }

        /** קומפילציה של הפרויקט ואיסוף השגיאות בקבצים החדשים: "Targil.java:12: ..." */
        private void compile(IFolder folder, IProgressMonitor m) throws CoreException {
            errors.clear();
            jp.getProject().build(IncrementalProjectBuilder.INCREMENTAL_BUILD, m);
            for (IMarker mk : folder.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE)) {
                if (mk.getAttribute(IMarker.SEVERITY, 0) != IMarker.SEVERITY_ERROR) continue;
                errors.add(mk.getResource().getName() + ":" + mk.getAttribute(IMarker.LINE_NUMBER, 0) + ": " + mk.getAttribute(IMarker.MESSAGE, ""));
                if (errors.size() >= 30) break;
            }
        }
    }
}

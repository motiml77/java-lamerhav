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
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * "שליחת תרגיל": החלון (SubmitDialog) שולח את דף התרגיל, ובאותו חלון — בלי חלון התקדמות נוסף — בונים ממנו את קובצי ה-Java
 * (Build, כאן למטה): כל תרגיל בחבילה משלו, עם שם לפי סוג התרגיל ומספר רץ (src/ex01_stacks, src/ex02_binary_trees...) כדי שיהיה קל
 * למצוא ולמחוק בסוף. בודקים שהכול מתקמפל (ואם לא — תיקון אחד דרך השרת), ופותחים עם הסמן במקום הפתרון של הסעיף הראשון,
 * והלשונית עם main ממש לידה. ההצעות לפתרון שבתשובה נבדקות (SolutionCheck) ונשמרות בצד — הן נפתחות רק מהכפתור "הצעה לפתרון"
 * ליד כל סעיף (SolutionMinings). בסוף — הודעה קטנה בפינה (Toast), בלי חלון שצריך לסגור.
 */
public class SubmitHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = Ui.shell(event);
        IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
        WorkerClient client = WorkerClient.configured();
        if (client != null) {   // בלי אינטרנט אין טעם לפתוח את החלון — אומרים את זה מיד
            final boolean[] on = { true };
            org.eclipse.swt.custom.BusyIndicator.showWhile(shell.getDisplay(), () -> on[0] = client.online(3500));
            if (!on[0]) {
                Say.warn(shell, "שליחת תרגיל — אין חיבור לאינטרנט",
                        "כדי לשלוח תרגיל צריך חיבור לאינטרנט, וכרגע אין חיבור. בדקו את החיבור (Wi-Fi או כבל) ונסו שוב.\n\n"
                        + "כל שאר הכלים (הרצה ויזואלית, הוספת מבנה נתונים, דוגמאות פתורות) עובדים גם בלי אינטרנט.");
                return null;
            }
        }
        SubmitDialog d = new SubmitDialog(shell, ProjectSetup.javaProjects(), ProjectSetup.freeProjectName(), Ui.currentProject(event), client);
        int code = d.open();
        Build b = d.build();
        if (b == null || b.files.isEmpty()) return null;
        try {
            open(page, b);
        } catch (CoreException e) {
            Ui.error(shell, "התרגיל נוצר, אבל לא הצלחתי לפתוח אותו.", e);
            return null;
        }
        if (code == Window.OK) Toast.created(page, b);   // בוטל באמצע — רק פותחים את מה שכבר נוצר, בלי הודעה
        return null;
    }

    /**
     * פותח את הקבצים: קודם המחלקות מהתרגיל, אחר כך הקובץ עם main, ובסוף הקובץ עם הסעיף הראשון — שיהיה הפעיל, והסמן מחכה
     * במקום הפתרון. כך הלשונית של main נפתחת ממש ליד הלשונית שבה עונים (כשהסעיפים במחלקה אחרת, למשל Button.java ו-main ב-Program.java),
     * ואפשר להריץ בלי לחפש אותה.
     */
    static void open(IWorkbenchPage page, Build b) throws CoreException {
        if (b.files.isEmpty()) return;
        IFile focus = null, main = null;
        int line = 0;
        String mainClass = b.exercise == null ? "" : b.exercise.mainClass;
        for (int i = 0; i < b.files.size(); i++) {
            if (focus == null && b.generated.get(i).cursorLine >= 0) { focus = b.files.get(i); line = b.generated.get(i).cursorLine; }
            if (b.generated.get(i).className.equals(mainClass)) main = b.files.get(i);
        }
        if (focus == null) focus = main != null ? main : b.files.get(0);
        for (int i = b.files.size() - 1; i >= 0; i--) {
            IFile f = b.files.get(i);
            if (!f.equals(focus) && !f.equals(main)) Folding.collapseGiven(Ui.openAt(page, f, 0), f);
        }
        if (main != null && !main.equals(focus)) Folding.collapseGiven(Ui.openAt(page, main, 0), main);
        org.eclipse.ui.IEditorPart ed = Ui.openAt(page, focus, line);
        Folding.collapseGiven(ed, focus);
        AnswerHighlight.attach(ed);
    }

    /**
     * השמות שתפוסים בפרויקט — כדי שהחבילה של התרגיל החדש לא תתנגש באף אחד: כל החבילות בכל תיקיות המקור (גם מקוננות — השם המלא,
     * ו-AiScaffold.folderName בודק כל חלק שלו), ומה שכבר נמצא בתיקיית המקור שנבחרה (תיקיות וקבצים).
     */
    static java.util.Set<String> takenNames(IJavaProject jp, IContainer src) throws CoreException {
        java.util.Set<String> taken = new HashSet<String>();
        for (IResource r : src.members()) taken.add(r.getName());
        try {
            for (org.eclipse.jdt.core.IPackageFragmentRoot root : jp.getPackageFragmentRoots()) {
                if (root.getKind() != org.eclipse.jdt.core.IPackageFragmentRoot.K_SOURCE) continue;
                for (org.eclipse.jdt.core.IJavaElement pkg : root.getChildren()) {
                    String n = pkg.getElementName();
                    if (!n.isEmpty()) taken.add(n);
                }
                if (root.getResource() instanceof IContainer)
                    for (IResource r : ((IContainer) root.getResource()).members()) taken.add(r.getName());
            }
        } catch (org.eclipse.jdt.core.JavaModelException ignored) {
            // בלי מודל Java — רק מה שבתיקייה
        }
        return taken;
    }

    /**
     * Java: בניית הקבצים בחבילה משלהם + בדיקת קומפילציה + תיקון אחד (רץ ברקע). ההתקדמות — ב-subTask: החלון של שליחת התרגיל
     * מציג לפיה את השלבים (SubmitDialog.Stages)
     */
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
        AiScaffold.Exercise exercise;
        /** מה יצא בבדיקת ההצעות לפתרון: ok / ran / failed / unchecked ("" — אין הצעות) */
        String solutionStatus = "";

        Build(WorkerClient client, Map<String, Object> raw, List<String> sources, IJavaProject jp, IContainer src) {
            this.client = client;
            this.raw = raw;
            this.sources = sources;
            this.jp = jp;
            this.src = src;
        }

        void run(IProgressMonitor m) throws InvocationTargetException, InterruptedException {
            try {
                m.beginTask("בונה את התרגיל…", 7);
                Set<String> taken = takenNames(jp, src);
                AiScaffold.Exercise e = AiScaffold.read(raw);
                exercise = e;
                String pkg = AiScaffold.folderName(e.topic, taken, false, src.getLocation() == null ? null : src.getLocation().toFile());
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
                        exercise = e2;
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
                m.worked(1);
                if (errors.isEmpty() && !m.isCanceled()) solutions(pkg, folder, m);
                m.done();
            } catch (CoreException e) {
                throw new InvocationTargetException(e, e.getStatus().getMessage());
            }
        }

        /**
         * "הצעה לפתרון": בודקים שההצעות של Gemini באמת נכונות — מריצים את main עם ההצעות (בתיקייה זמנית, לא בפרויקט) ומשווים לפלט הצפוי.
         * אם לא — סבב תיקון אחד (mode=solution), שמתקן את ההצעה או את הפלט הצפוי (ואז גם ההערה מעל main מתעדכנת).
         * בסוף ההצעות נשמרות בצד (SolutionStore) — הקבצים של התלמיד נשארים בלי פתרון.
         */
        private void solutions(String pkg, IFolder folder, IProgressMonitor m) throws CoreException {
            AiScaffold.Exercise e = exercise;
            boolean any = false;
            for (AiScaffold.Task t : e.tasks) any |= !t.solution.isEmpty();
            if (!any) return;   // שרת ישן — בלי הצעות
            m.subTask("בודק שההצעות לפתרון נותנות את התוצאה הנכונה…");
            java.io.File lib = RunVisualHandler.libraryJar();
            String javaExe = javaFor(jp);
            SolutionCheck.Result r = lib == null ? new SolutionCheck.Result() : check(e, pkg, lib, javaExe);
            m.worked(1);
            if (!r.passed() && r.status != SolutionCheck.Status.UNCHECKED && client != null && !m.isCanceled()) {
                m.subTask("ההצעה לפתרון לא נתנה בדיוק את הפלט הצפוי — מבקש תיקון…");
                Map<String, String> code = new LinkedHashMap<String, String>();
                for (AiScaffold.GeneratedFile g : AiScaffold.build(e, pkg, sources, true)) code.put(g.fileName(), g.code);
                List<String> before = new ArrayList<String>();
                for (AiScaffold.Task t : e.tasks) before.add(t.solution);
                String expectedBefore = e.expectedOutput;
                try {
                    boolean changed = AiScaffold.takeSolutions(e, AiScaffold.read(client.repair(raw, code, r.problems, "solution")));
                    m.subTask("בודק שוב…");
                    SolutionCheck.Result r2 = check(e, pkg, lib, javaExe);
                    if (r2.rank() > r.rank()) {
                        r = r2;
                        if (r2.status != SolutionCheck.Status.OK) e.expectedOutput = expectedBefore;   // פלט צפוי שלא אומת — לא משנים את ההערה
                        else if (changed) {   // הפלט הצפוי תוקן ואומת — ההערה מעל main נכתבת מחדש
                            files.clear();
                            generated.clear();
                            write(e, pkg, folder);
                        }
                    } else {
                        for (int i = 0; i < e.tasks.size(); i++) e.tasks.get(i).solution = before.get(i);
                        e.expectedOutput = expectedBefore;
                    }
                } catch (WorkerClient.AiError ignored) {
                    // התיקון לא הצליח — ההצעות נשמרות כ"לא אומתו"
                }
            }
            solutionStatus = r.stored();
            List<SolutionStore.Entry> entries = new ArrayList<SolutionStore.Entry>();
            for (AiScaffold.Task t : e.tasks) {
                if (t.solution.isEmpty()) continue;
                SolutionStore.Entry s = new SolutionStore.Entry();
                s.path = folder.getFile(t.className + ".java").getFullPath().toString();
                s.cls = t.className;
                s.method = t.methodName;
                for (String[] p : t.params) s.params.add(p[0]);
                s.label = t.label;
                s.code = t.solution;
                s.status = solutionStatus;
                entries.add(s);
            }
            SolutionMinings.store().add(entries);
        }

        private static SolutionCheck.Result check(AiScaffold.Exercise e, String pkg, java.io.File lib, String javaExe) {
            try {
                return SolutionCheck.run(e, pkg, lib, javaExe, java.nio.file.Files.createTempDirectory("dsviz-solution").toFile(), 15);
            } catch (java.io.IOException x) {
                return new SolutionCheck.Result();
            }
        }

        /** ה-Java שמריץ את הפרויקט (כמו בהרצה רגילה), ואם אין — ה-Java שהותקן עם התוכנה */
        private static String javaFor(IJavaProject jp) {
            try {
                org.eclipse.jdt.launching.IVMInstall vm = org.eclipse.jdt.launching.JavaRuntime.getVMInstall(jp);
                if (vm != null && vm.getInstallLocation() != null) {
                    String exe = SolutionCheck.javaOf(vm.getInstallLocation().getAbsolutePath());
                    if (exe != null) return exe;
                }
            } catch (CoreException ignored) {
                // בלי JRE מוגדר
            }
            return SolutionCheck.javaOf(AutoSetup.dsvizFile("jdk-path.txt"));
        }

        /** האם בשורה (מ-1) של הקובץ נמצאת הכותרת של פעולה של סעיף */
        private boolean taskHeader(String fileName, int line) {
            for (AiScaffold.GeneratedFile g : generated) {
                if (!g.fileName().equals(fileName)) continue;
                String[] lines = g.code.split("\n", -1);
                if (line < 1 || line > lines.length) return false;
                String l = lines[line - 1].trim();
                for (AiScaffold.Task t : exercise.tasks) if (t.className.equals(g.className) && l.startsWith(AiScaffold.header(t))) return true;
            }
            return false;
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

        /**
         * קומפילציה של הפרויקט ואיסוף השגיאות בקבצים החדשים: "Targil.java:12: ..."
         * בלי "This method must return a result" על הכותרת של פעולה של סעיף — הפעולות ריקות בכוונה (התלמיד כותב את ה-return)
         */
        private void compile(IFolder folder, IProgressMonitor m) throws CoreException {
            errors.clear();
            jp.getProject().build(IncrementalProjectBuilder.INCREMENTAL_BUILD, m);
            for (IMarker mk : folder.findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE)) {
                if (mk.getAttribute(IMarker.SEVERITY, 0) != IMarker.SEVERITY_ERROR || MentorProblem.MARKER.equals(mk.getType())) continue; // בלי ההסברים בעברית
                if (mk.getAttribute(org.eclipse.jdt.core.IJavaModelMarker.ID, 0) == org.eclipse.jdt.core.compiler.IProblem.ShouldReturnValue
                        && taskHeader(mk.getResource().getName(), mk.getAttribute(IMarker.LINE_NUMBER, 0))) continue;
                errors.add(mk.getResource().getName() + ":" + mk.getAttribute(IMarker.LINE_NUMBER, 0) + ": " + mk.getAttribute(IMarker.MESSAGE, ""));
                if (errors.size() >= 30) break;
            }
        }
    }
}

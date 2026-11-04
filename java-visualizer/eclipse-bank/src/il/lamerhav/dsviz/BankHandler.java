package il.lamerhav.dsviz;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * הכפתור "מאגר תרגילים": החלון (BankDialog) בוחר שאלה, וכאן בונים ממנה את התרגיל — באותו מנוע כמו "שליחת תרגיל"
 * (SubmitHandler.Build, בלי שרת: השאלה כבר בפורמט של התשובה של Gemini, וההצעות לפתרון כבר נבדקו — tools/test/bank-check.sh).
 * התוצאה זהה: חבילה משלה (src/ex03_stacks), פעולה לכל סעיף עם "כאן עונים", main שמריץ את כולן ומעליו הפלט הרצוי, ו-💡 ליד כל סעיף.
 * אחרי היצירה: נפתח חלון הצד "השאלה" (BankView) עם הנוסח, ונרשם מה נוצר (BankProgress) — בפעם הבאה "פתח את התרגיל" ממשיך את אותה עבודה.
 */
public class BankHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) {
        if (!Power.on()) return null;   // DSViz כבוי
        Shell shell = Ui.shell(event);
        IWorkbenchPage page = HandlerUtil.getActiveWorkbenchWindow(event).getActivePage();
        BankStore store = BankStore.load();
        BankDialog d = new BankDialog(shell, store, BankProgress.get(), ProjectSetup.javaProjects(), ProjectSetup.freeProjectName(), Ui.currentProject(event));
        if (d.open() != Window.OK || d.question() == null) return null;
        BankStore.Question q = d.question();
        if (d.action() == BankDialog.Action.OPEN) {
            openExisting(page, q);
            return null;
        }
        try {
            IJavaProject jp = d.project() != null ? d.project() : ProjectSetup.createProject(d.newProjectName());
            IContainer src = ProjectSetup.prepare(jp, Ui.resolver(shell));
            SubmitHandler.Build b = create(new ProgressMonitorDialog(shell), q, jp, src);
            if (b == null || b.files.isEmpty()) {
                Say.warn(shell, "מאגר תרגילים", "לא הצלחתי ליצור את התרגיל. נסו שוב, או בחרו פרויקט אחר.");
                return null;
            }
            SubmitHandler.open(page, b);
            Toast.created(page, b);
            BankFloat.show(page, q.id);
        } catch (CoreException e) {
            Ui.error(shell, "לא הצלחתי ליצור את התרגיל.", e);
        }
        return null;
    }

    /**
     * בונה את התרגיל מהשאלה בתיקיית המקור src (ברקע, עם חלון התקדמות ctx) ורושם אותו ב-BankProgress.
     * מחזיר את הבנייה (הקבצים, השגיאות), או null אם נכשלה.
     */
    static SubmitHandler.Build create(IRunnableContext ctx, BankStore.Question q, IJavaProject jp, IContainer src) {
        Map<String, Object> raw = Json.obj(Json.parse(Json.write(q.exercise)));   // עותק — הבנייה לא משנה את המאגר
        final SubmitHandler.Build b = new SubmitHandler.Build(null, raw, Collections.<String>emptyList(), jp, src);
        try {
            ctx.run(true, false, m -> b.run(m));
        } catch (InvocationTargetException | InterruptedException e) {
            return null;
        }
        if (b.files.isEmpty()) return null;
        BankProgress.Created c = new BankProgress.Created();
        c.project = jp.getProject().getName();
        c.folder = b.files.get(0).getParent().getFullPath().toString();
        for (int i = 0; i < b.files.size(); i++) {
            String p = b.files.get(i).getFullPath().toString();
            c.files.add(p);
            if (c.focus.isEmpty() && b.generated.get(i).cursorLine >= 0) c.focus = p;
        }
        BankProgress.get().created(q.id, c);
        return b;
    }

    /** "פתח את התרגיל": הקבצים שכבר נוצרו מהשאלה (מה שהתלמיד כתב נשאר) — main ליד הקובץ של הסעיפים, והסמן ב"כאן עונים" הראשון */
    static void openExisting(IWorkbenchPage page, BankStore.Question q) {
        List<BankProgress.Created> all = BankProgress.get().createdOf(q.id);
        if (all.isEmpty()) return;
        BankProgress.Created c = all.get(all.size() - 1);
        List<IFile> files = new ArrayList<IFile>();
        IFile focus = null;
        for (String p : c.files) {
            IResource r = ResourcesPlugin.getWorkspace().getRoot().findMember(new Path(p));
            if (r instanceof IFile) {
                files.add((IFile) r);
                if (p.equals(c.focus)) focus = (IFile) r;
            }
        }
        if (files.isEmpty()) return;
        if (focus == null) focus = files.get(0);
        try {
            for (IFile f : files) if (!f.equals(focus)) Folding.collapseGiven(Ui.openAt(page, f, 0), f);
            IEditorPart ed = Ui.openAt(page, focus, answerLine(focus));
            Folding.collapseGiven(ed, focus);
            AnswerHighlight.attach(ed);
            BankFloat.show(page, q.id);
        } catch (PartInitException e) {
            // הקובץ לא נפתח — נשארים עם מה שכן נפתח
        }
    }

    /**
     * "סיימתי" (V ירוק): מסמנים, ומוחקים את קבצי התרגיל שנוצרו מהשאלה (החבילה כולה — ex03_stacks), כדי שהפרויקט לא יתמלא
     * בתרגילים ישנים. ask — שואלים קודם (מחיקה). מחזיר true אם סומן.
     */
    static boolean complete(Shell shell, BankStore.Question q, boolean ask) {
        List<BankProgress.Created> made = BankProgress.get().createdOf(q.id);
        List<String> names = new ArrayList<String>();
        for (BankProgress.Created c : made) for (IFile f : createdFiles(c)) names.add(f.getName());
        if (ask && !names.isEmpty() && !Say.ask(shell, "סיימתי את התרגיל",
                "השאלה תסומן בסימן ירוק, וקבצי התרגיל יימחקו מהפרויקט — כדי שלא יצטברו תרגילים ישנים.\n\n"
                + "אפשר תמיד לפתוח את השאלה שוב מ\"מאגר תרגילים\" ולהתחיל מחדש.\n\n"
                + (names.size() == 1 ? "הקובץ שיימחק: " : "הקבצים שיימחקו: ") + String.join(", ", names)
                + "\nרק הקבצים שנוצרו מהתרגיל — קבצים אחרים לא נמחקים.", "סיום ומחיקה", "ביטול")) return false;
        BankProgress.get().setDone(q.id, true);
        List<String> kept = new ArrayList<String>();
        for (BankProgress.Created c : made) kept.addAll(cleanUp(c));
        BankProgress.get().forgetCreated(q.id);
        if (!kept.isEmpty() && shell != null)
            Say.info(shell, "סיימתי את התרגיל", "קבצי התרגיל נמחקו. בתיקייה יש עוד קבצים שלא נוצרו מהתרגיל — הם נשארו במקומם:\n" + String.join(", ", kept));
        return true;
    }

    /**
     * הקבצים שנוצרו מהתרגיל ועדיין קיימים — רק בתוך התיקייה של התרגיל (חבילה בתיקיית מקור: ‎/פרויקט/src/ex01_stacks‎),
     * כדי שלעולם לא יימחק קובץ אחר של התלמיד
     */
    static List<IFile> createdFiles(BankProgress.Created c) {
        List<IFile> out = new ArrayList<IFile>();
        if (c.folder.isEmpty() || new Path(c.folder).segmentCount() < 3) return out;
        for (String p : c.files) {
            if (!p.startsWith(c.folder + "/") || p.substring(c.folder.length() + 1).contains("/")) continue;
            IResource r = ResourcesPlugin.getWorkspace().getRoot().findMember(new Path(p));
            if (r instanceof IFile && r.exists() && !out.contains(r)) out.add((IFile) r);
        }
        return out;
    }

    /**
     * מוחק תרגיל אחד — רק מה שנוצר בשבילו: הלשוניות של הקבצים שלו (בלי לשמור), הקבצים עצמם, הקבצים המקומפלים שלהם (bin),
     * וההצעות לפתרון ששמורות להם. התיקייה (והתיקייה שלה ב-bin) נמחקת רק אם לא נשאר בה כלום.
     * מחזיר את הקבצים האחרים שנשארו בתיקייה (של התלמיד — לא נוגעים בהם).
     */
    static List<String> cleanUp(BankProgress.Created c) {
        final List<IFile> files = createdFiles(c);
        final java.util.Set<String> paths = new java.util.HashSet<String>();
        for (IFile f : files) paths.add(f.getFullPath().toString());
        List<String> kept = new ArrayList<String>();
        IResource folder = c.folder.isEmpty() ? null : ResourcesPlugin.getWorkspace().getRoot().findMember(new Path(c.folder));
        // 1. הלשוניות בעורך — רק של הקבצים האלה
        Runnable close = () -> {
            for (org.eclipse.ui.IWorkbenchWindow w : org.eclipse.ui.PlatformUI.getWorkbench().getWorkbenchWindows()) {
                for (IWorkbenchPage page : w.getPages()) {
                    List<org.eclipse.ui.IEditorReference> refs = new ArrayList<org.eclipse.ui.IEditorReference>();
                    for (org.eclipse.ui.IEditorReference ref : page.getEditorReferences()) {
                        try {
                            IFile f = ref.getEditorInput().getAdapter(IFile.class);
                            if (f != null && paths.contains(f.getFullPath().toString())) refs.add(ref);
                        } catch (PartInitException e) {
                            // לשונית שלא נטענה — מדלגים
                        }
                    }
                    if (!refs.isEmpty()) page.closeEditors(refs.toArray(new org.eclipse.ui.IEditorReference[0]), false);
                }
            }
        };
        org.eclipse.swt.widgets.Display d = org.eclipse.swt.widgets.Display.getDefault();
        if (d.getThread() == Thread.currentThread()) close.run();
        else d.syncExec(close);
        // 2. הקבצים המקומפלים שלהם (bin/<חבילה>/Name.class ו-Name$...class) — לפני מחיקת המקור
        IContainer out = null;
        try {
            IJavaProject jp = folder == null ? null : org.eclipse.jdt.core.JavaCore.create(folder.getProject());
            if (jp != null && jp.exists()) {
                IResource o = ResourcesPlugin.getWorkspace().getRoot().findMember(jp.getOutputLocation().append(folder.getName()));
                if (o instanceof IContainer && o.exists()) {
                    out = (IContainer) o;
                    for (IFile f : files) {
                        String base = f.getName().replaceAll("\\.java$", "");
                        for (IResource k : out.members()) {
                            String n = k.getName();
                            if (k instanceof IFile && (n.equals(base + ".class") || n.startsWith(base + "$") && n.endsWith(".class"))) k.delete(true, null);
                        }
                    }
                }
            }
        } catch (CoreException e) {
            // נמחק בבנייה הבאה
        }
        // 3. הקבצים עצמם
        for (IFile f : files) {
            try {
                f.delete(true, null);
            } catch (CoreException e) {
                kept.add(f.getName());
            }
        }
        // 4. התיקייה — רק אם ריקה (קבצים אחרים של התלמיד נשארים, עם התיקייה)
        try {
            if (folder instanceof IContainer && folder.exists()) {
                IResource[] rest = ((IContainer) folder).members();
                if (rest.length == 0) folder.delete(true, null);
                else for (IResource r : rest) kept.add(r.getName());
            }
            if (out != null && out.exists() && out.members().length == 0) out.delete(true, null);
        } catch (CoreException e) {
            // נשאר
        }
        // 5. ההצעות לפתרון של הקבצים שנמחקו (SolutionStore — באותה חבילה; בלי לשנות את התוסף הראשי)
        try {
            SolutionStore store = SolutionMinings.store();
            synchronized (store) {
                java.lang.reflect.Method load = SolutionStore.class.getDeclaredMethod("load"), save = SolutionStore.class.getDeclaredMethod("save");
                java.lang.reflect.Field entries = SolutionStore.class.getDeclaredField("entries");
                load.setAccessible(true);
                save.setAccessible(true);
                entries.setAccessible(true);
                load.invoke(store);
                @SuppressWarnings("unchecked")
                List<SolutionStore.Entry> list = (List<SolutionStore.Entry>) entries.get(store);
                if (list.removeIf(e -> paths.contains(e.path))) save.invoke(store);
            }
        } catch (Exception e) {
            // לא נמחקו — הן לא יופיעו ממילא (הקבצים לא קיימים)
        }
        return kept;
    }

    /** השורה (מ-0) של "כאן עונים" הראשון בקובץ, או 0 */
    private static int answerLine(IFile f) {
        try (java.io.InputStream in = f.getContents()) {
            String[] lines = new String(ProjectSetup.readAll(in), java.nio.charset.StandardCharsets.UTF_8).split("\n", -1);
            for (int i = 0; i < lines.length; i++) if (lines[i].contains("כאן עונים") || lines[i].contains("כאן כותבים את התשובה")) return i;
        } catch (Exception e) {
            // לא נקרא — מתחילת הקובץ
        }
        return 0;
    }

    // ------------------------------------------------------------------ לבדיקות (tools/test/eclipse-it — מחבילה אחרת)

    /** יוצר תרגיל משאלה בפרויקט (נוצר אם אין), בלי חלונות. מחזיר את נתיבי הקבצים שנוצרו */
    public static List<String> createForTest(String questionId, String projectName) throws CoreException {
        BankStore.Question q = BankStore.load().find(questionId);
        if (q == null) throw new IllegalArgumentException("no question " + questionId);
        IJavaProject jp = null;
        for (IJavaProject p : ProjectSetup.javaProjects()) if (p.getProject().getName().equals(projectName)) jp = p;
        if (jp == null) jp = ProjectSetup.createProject(projectName);
        IContainer src = ProjectSetup.prepare(jp, names -> false);
        SubmitHandler.Build b = create((fork, cancel, r) -> {
            try {
                r.run(new org.eclipse.core.runtime.NullProgressMonitor());
            } catch (InvocationTargetException | InterruptedException e) {
                throw e;
            }
        }, q, jp, src);
        List<String> out = new ArrayList<String>();
        if (b != null) for (IFile f : b.files) out.add(f.getFullPath().toString());
        if (b != null) for (String e : b.errors) out.add("ERROR " + e);
        return out;
    }

    /** כל השאלות: "id|topic|level|title" */
    public static List<String> questionsForTest() {
        List<String> out = new ArrayList<String>();
        for (BankStore.Question q : BankStore.load().questions) out.add(q.id + "|" + q.topic + "|" + q.level.id + "|" + q.title);
        return out;
    }

    /** ההצעות לפתרון שנשמרו לקובץ (בשביל 💡): "Class.method label status" */
    public static List<String> solutionsForTest(String path) {
        List<String> out = new ArrayList<String>();
        for (SolutionStore.Entry e : SolutionMinings.store().forFile(path)) out.add(e.cls + "." + e.method + " " + e.label + " " + e.status);
        return out;
    }

    /** לאיזו שאלה שייך קובץ (לפי מה שנוצר), או null */
    public static String questionOfForTest(String path) {
        return BankProgress.get().questionOf(path);
    }

    public static boolean doneForTest(String id) {
        return BankProgress.get().done(id);
    }

    /** "סיימתי" בלי לשאול: V + מחיקת הקבצים */
    public static boolean completeForTest(String id) {
        BankStore.Question q = BankStore.load().find(id);
        return q != null && complete(null, q, false);
    }

    /** החלון הצף "השאלה" (אם פתוח): המעטפת, השאלה, ומה מוצג */
    public static Shell floatShellForTest() {
        BankFloat f = BankFloat.current();
        return f == null ? null : f.shell();
    }

    public static String floatQuestionForTest() {
        BankFloat f = BankFloat.current();
        return f == null ? null : f.question();
    }

    public static List<String> floatShownForTest() {
        BankFloat f = BankFloat.current();
        return f == null ? new ArrayList<String>() : new ArrayList<String>(f.shown);
    }

    public static void setDoneForTest(String id, boolean done) {
        BankProgress.get().setDone(id, done);
    }

    /** פותח את חלון המאגר (בלי לחכות שייסגר), בוחר נושא ושאלה — לצילום מסך. מחזיר את החלון */
    public static Shell openDialogForTest(Shell parent, String topic, String questionId) {
        BankDialog d = new BankDialog(parent, BankStore.load(), BankProgress.get(), ProjectSetup.javaProjects(), ProjectSetup.freeProjectName(), null);
        d.setBlockOnOpen(false);
        d.open();
        d.select(topic, questionId);
        return d.getShell();
    }

    /** "פתח את התרגיל" — כמו בחלון */
    public static void openExistingForTest(IWorkbenchPage page, String questionId) {
        BankStore.Question q = BankStore.load().find(questionId);
        if (q != null) openExisting(page, q);
    }
}

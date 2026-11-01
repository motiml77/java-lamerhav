package il.lamerhav.dsviz;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.ide.IDE;

/**
 * "הרצה ויזואלית": שומר ומריץ את המחלקה של הקובץ הפתוח (עם main) דרך LineByLine שבספרייה —
 * החלון מראה כל שורה בקוד, את המשתנים ואת המבנים. (Run הרגיל של Eclipse מדפיס רק לקונסול.)
 */
public class RunVisualHandler extends AbstractHandler {
    private static final String VM_ARGS = "-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Ddsviz.visual=true";
    private static final String LINE_RUNNER = "unit4.collectionsLib.LineByLine";

    @Override
    public Object execute(ExecutionEvent event) {
        Shell shell = Ui.shell(event);
        IFile file = Ui.currentFile(event);
        if (file == null || !"java".equals(file.getFileExtension())) {
            Say.info(shell, "הרצה ויזואלית", "פתחו קובץ Java (תרגיל) ואז לחצו \"הרצה ויזואלית\".");
            return null;
        }
        IDE.saveAllEditors(new IResource[] { file.getProject() }, false);
        try {
            String main = mainType(file);
            if (main == null) {
                // מחלקה בלי main (למשל מחלקה חדשה שהתלמיד כתב): מריצים את המחלקה שמשתמשת בה — או מציעים להוסיף main קצרה
                IType user = mainUsing(file, shell);
                if (user == null) {
                    offerMain(shell, event, file);
                    return null;
                }
                main = user.getFullyQualifiedName();
                if (user.getResource() instanceof IFile) file = (IFile) user.getResource();
            }
            // פרויקט של התלמיד (כל פרויקט Java): לא משנים בו כלום מלבד הוספת ספריית הקורס אם אין בו בכלל (כדי ש-Queue וכו' יתקמפלו).
            // ספרייה אחרת של unit4 (למשל ה-jar המקורי של הקורס) נשארת; בזמן ההרצה הספרייה שלנו לפניה — היא שמציירת את המבנים.
            LibraryEverywhere.ensure(JavaCore.create(file.getProject()));
            // קודם מקמפלים ובודקים שגיאות — עם שגיאות לא מריצים (הרצה ויזואלית של קוד שלא מתקמפל נותנת תוצאות שגויות)
            List<Problem> errors = problems(file);
            if (!errors.isEmpty()) {
                warn(shell, HandlerUtil.getActiveWorkbenchWindow(event).getActivePage(), errors);
                return null;
            }
            DebugUITools.launch(config(JavaCore.create(file.getProject()), main), ILaunchManager.RUN_MODE);
        } catch (CoreException e) {
            Ui.error(shell, "לא הצלחתי להריץ.", e);
        }
        return null;
    }

    /** שגיאה אחת שחוסמת את ההרצה: איפה, משפט אחד בעברית, ושורת הקוד */
    static final class Problem {
        final IMarker marker;
        final IFile file;
        final int line;
        final String sentence, code;

        Problem(IMarker marker, IFile file, int line, String sentence, String code) {
            this.marker = marker;
            this.file = file;
            this.line = line;
            this.sentence = sentence;
            this.code = code;
        }

        /** "Button.java · שורה 42" */
        String where() {
            return file.getName() + " · שורה " + line;
        }
    }

    /**
     * בנייה סינכרונית של הפרויקט (גם אם הבנייה האוטומטית כבויה) ואיסוף כל השגיאות בו (כטקסט: "Targil.java · שורה 6\nההסבר").
     * מחזיר את הסימון הראשון (לפתיחה בעורך).
     */
    public static IMarker compileErrors(IFile file, List<String> out) throws CoreException {
        List<Problem> ps = problems(file);
        for (Problem p : ps) out.add(p.where() + "\n" + p.sentence);
        return ps.isEmpty() ? null : ps.get(0).marker;
    }

    private static final java.util.regex.Pattern OTHER_LINE = java.util.regex.Pattern.compile("\\s*\\(הסימון באנגלית — בשורה (\\d+)\\)");

    /**
     * בנייה סינכרונית של הפרויקט ואיסוף השגיאות שחוסמות את ההרצה: בקובץ הזה, ובקבצים שהוא משתמש בהם (לפי שם המחלקה).
     * ההסברים בעברית (סימון המורה בעברית) מועדפים; שגיאה של Eclipse בשורה שכבר יש לה הסבר בעברית לא חוזרת — גם כשההסבר
     * מסומן בשורה אחרת ("הסימון באנגלית — בשורה 42"). שגיאה של Eclipse בלי הסבר — מתורגמת (problems_he.properties).
     */
    static List<Problem> problems(IFile file) throws CoreException {
        file.getProject().build(IncrementalProjectBuilder.INCREMENTAL_BUILD, null);
        List<IMarker> errs = new ArrayList<IMarker>();
        // בפרויקט יש הרבה תרגילים — שגיאה בתרגיל אחר לא צריכה לחסום. חוסמות: שגיאות בקובץ הזה, ובקבצים שהוא משתמש בהם (לפי שם המחלקה)
        String source = text(file);
        for (IMarker m : file.getProject().findMarkers(IMarker.PROBLEM, true, IResource.DEPTH_INFINITE)) {
            if (m.getAttribute(IMarker.SEVERITY, 0) != IMarker.SEVERITY_ERROR) continue;
            IResource r = m.getResource();
            boolean used = r.equals(file) || "java".equals(r.getFileExtension())
                    && java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(r.getName().replaceFirst("\\.java$", "")) + "\\b").matcher(source).find();
            if (used) errs.add(m);
        }
        // שגיאות בקובץ הפתוח קודם
        java.util.Collections.sort(errs, (a, b) -> {
            boolean fa = a.getResource().equals(file), fb = b.getResource().equals(file);
            if (fa != fb) return fa ? -1 : 1;
            int c = a.getResource().getName().compareTo(b.getResource().getName());
            return c != 0 ? c : a.getAttribute(IMarker.LINE_NUMBER, 0) - b.getAttribute(IMarker.LINE_NUMBER, 0);
        });
        java.util.Set<String> hebrew = new java.util.HashSet<String>();
        for (IMarker m : errs) {
            if (!MentorProblem.MARKER.equals(m.getType())) continue;
            String at = m.getResource().getFullPath() + ":";
            hebrew.add(at + m.getAttribute(IMarker.LINE_NUMBER, 0));
            java.util.regex.Matcher o = OTHER_LINE.matcher(RtlText.strip(m.getAttribute(IMarker.MESSAGE, "")));
            if (o.find()) hebrew.add(at + o.group(1));   // ההסבר בעברית בשורה אחרת מזו ש-Eclipse סימן
        }
        java.util.Map<IResource, String[]> lines = new java.util.HashMap<IResource, String[]>();
        List<Problem> out = new ArrayList<Problem>();
        for (IMarker m : errs) {
            boolean heb = MentorProblem.MARKER.equals(m.getType());
            int line = m.getAttribute(IMarker.LINE_NUMBER, 0);
            if (!heb && hebrew.contains(m.getResource().getFullPath() + ":" + line)) continue;
            if (!(m.getResource() instanceof IFile)) continue;
            String[] ls = lines.get(m.getResource());
            if (ls == null) {
                ls = text((IFile) m.getResource()).split("\r?\n", -1);
                lines.put(m.getResource(), ls);
            }
            String code = line >= 1 && line <= ls.length ? ls[line - 1].trim() : "";
            if (code.matches("[{}();]*")) code = header(ls, line - 1);   // "}" בסוף פעולה ("חסר return") — הכותרת שלה
            out.add(new Problem(m, (IFile) m.getResource(), line, sentence(m, heb), code));
        }
        return out;
    }

    /** הכותרת של הבלוק שהשורה סוגרת (עד 40 שורות למעלה, באותה הזחה): "public static int countSame(Button[] arr, Button b) {" */
    static String header(String[] ls, int at) {
        if (at < 0 || at >= ls.length) return "";
        String close = ls[at];
        int indent = close.length() - close.replaceAll("^\\s+", "").length();
        for (int i = at - 1; i >= 0 && i >= at - 40; i--) {
            String l = ls[i];
            if (l.trim().isEmpty()) continue;
            int k = l.length() - l.replaceAll("^\\s+", "").length();
            if (k == indent && l.trim().endsWith("{") && l.contains("(")) return l.trim();
            if (k < indent) break;
        }
        return "";
    }

    /** משפט אחד בעברית: ההסבר של המורה בעברית (בלי העצה שבסוגריים המרובעים), או התרגום של הודעת Eclipse */
    static String sentence(IMarker m, boolean hebrew) {
        String msg = RtlText.strip(m.getAttribute(IMarker.MESSAGE, "")).trim();
        if (!hebrew) {
            int id = m.getAttribute(org.eclipse.jdt.core.IJavaModelMarker.ID, 0) & org.eclipse.jdt.core.compiler.IProblem.IgnoreCategoriesMask;
            String t = id == 0 ? null : HebrewErrors.template(id);
            if (t != null) {
                String[] a = markerArguments(m.getAttribute(org.eclipse.jdt.core.IJavaModelMarker.ARGUMENTS, ""));
                for (int i = 0; i < a.length; i++) a[i] = HebrewErrors.shorten(a[i]);
                return HebrewErrors.splitAdvice(HebrewErrors.fill(t, a))[0].replaceAll("\\.$", "");
            }
            return msg;
        }
        msg = OTHER_LINE.matcher(msg).replaceAll("");
        int tip = msg.indexOf(" [");
        if (tip > 0 && msg.endsWith("]")) msg = msg.substring(0, tip);
        return msg.trim().replaceAll("\\.$", "");
    }

    /** הארגומנטים של בעיית JDT כפי שנשמרו בסימון: "2:int#boolean" (# בתוך ארגומנט — ##) */
    static String[] markerArguments(String attr) {
        int colon = attr == null ? -1 : attr.indexOf(':');
        if (colon < 0) return new String[0];
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        String rest = attr.substring(colon + 1);
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '#' && i + 1 < rest.length() && rest.charAt(i + 1) == '#') { cur.append('#'); i++; }
            else if (c == '#') { out.add(cur.toString().trim()); cur.setLength(0); }
            else cur.append(c);
        }
        out.add(cur.toString().trim());
        return out.toArray(new String[0]);
    }

    private static String text(IFile f) {
        try {
            return new String(ProjectSetup.readAll(f.getContents()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";   // בלי טקסט — רק השגיאות של הקובץ עצמו
        }
    }

    /** שגיאות קומפילציה — לא מריצים. כותרת קצרה, כרטיס לכל שגיאה (עד 4), ומעבר לשגיאה הראשונה */
    static void warn(Shell shell, final org.eclipse.ui.IWorkbenchPage page, final List<Problem> errors) {
        int n = errors.size();
        Say say = new Say(shell, Say.Kind.ERROR, "הרצה ויזואלית")
                .heading((n == 1 ? "שגיאה אחת" : n + " שגיאות") + " — מתקנים ומריצים שוב")
                .text("ההרצה הוויזואלית מתחילה רק כשהקוד מתקמפל.").width(500)
                .buttons("מעבר לשגיאה הראשונה", "סגירה").onPrimary(() -> jump(page, errors.get(0)));
        for (int i = 0; i < n && i < 4; i++) {
            final Problem p = errors.get(i);
            say.card(p.where(), () -> jump(page, p), p.sentence, p.code);
        }
        if (n > 4) say.more("ועוד " + (n - 4) + ", בחלון Problems ובחלון \"הסברים בעברית\".");
        say.open();
    }

    /** פותח את הקובץ בעורך, עם הסמן על השגיאה */
    static void jump(org.eclipse.ui.IWorkbenchPage page, Problem p) {
        try {
            if (page != null) IDE.openEditor(page, p.marker, true);
        } catch (Exception e) {
            try {
                Ui.openAt(page, p.file, Math.max(0, p.line - 1));
            } catch (Exception ignored) {
                // לא נפתח
            }
        }
    }

    /**
     * ה-classpath של ההרצה: קודם הספרייה שלנו (מתוך התוסף — לא מהפרויקט), ואחריה ה-classpath הרגיל של הפרויקט.
     * כך ההרצה הוויזואלית עובדת בכל פרויקט — גם אם יש בו ספריית unit4 אחרת — בלי לשנות את הפרויקט עצמו.
     */
    public static List<String> launchClasspath(org.eclipse.jdt.core.IJavaProject jp) throws CoreException {
        List<String> cp = new ArrayList<String>();
        java.io.File lib = libraryJar();
        if (lib != null) {
            org.eclipse.jdt.launching.IRuntimeClasspathEntry e = JavaRuntime.newArchiveRuntimeClasspathEntry(new org.eclipse.core.runtime.Path(lib.getAbsolutePath()));
            e.setClasspathProperty(org.eclipse.jdt.launching.IRuntimeClasspathEntry.USER_CLASSES);
            cp.add(e.getMemento());
        }
        for (org.eclipse.jdt.launching.IRuntimeClasspathEntry e : JavaRuntime.computeUnresolvedRuntimeClasspath(jp)) cp.add(e.getMemento());
        return cp;
    }

    /** הספרייה (dsviz.jar) מתוך התוסף, בתיקייה של התוסף ב-Eclipse (נכתבת מחדש כשהתוסף מתעדכן) */
    static java.io.File libraryJar() {
        try {
            org.osgi.framework.Bundle b = org.eclipse.core.runtime.Platform.getBundle("il.lamerhav.dsviz");
            byte[] data = ProjectSetup.resource("/resources/" + ProjectSetup.JAR_NAME);
            java.io.File dir = org.eclipse.core.runtime.Platform.getStateLocation(b).toFile();
            java.io.File f = new java.io.File(dir, "dsviz-" + b.getVersion() + "-" + Integer.toHexString(java.util.Arrays.hashCode(data)) + ".jar");
            if (!f.isFile() || f.length() != data.length) {
                dir.mkdirs();
                java.nio.file.Files.write(f.toPath(), data);
            }
            return f;
        } catch (Exception e) {
            return null;   // ההרצה ממשיכה עם הספרייה שבפרויקט
        }
    }

    /** לאיזו גרסת Java הפרויקט מקומפל (8, 11, 17, 21...) */
    private static int targetVersion(org.eclipse.jdt.core.IJavaProject jp) {
        String v = jp.getOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, true);
        if (v == null) return 0;
        try {
            return v.startsWith("1.") ? Integer.parseInt(v.substring(2)) : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * המחלקות בפרויקט שיש בהן main ומשתמשות במחלקה של הקובץ (לפי השם). אחת — מחזירים אותה; כמה — התלמיד בוחר;
     * אף אחת — null. (בוחרים "לא" — גם null)
     */
    private static IType mainUsing(IFile file, Shell shell) throws CoreException {
        ICompilationUnit cu = JavaCore.createCompilationUnitFrom(file);
        if (cu == null || cu.getTypes().length == 0) return null;
        String name = cu.getTypes()[0].getElementName();
        java.util.regex.Pattern used = java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(name) + "\\b");
        List<IType> found = new ArrayList<IType>();
        org.eclipse.jdt.core.IJavaProject jp = cu.getJavaProject();
        for (org.eclipse.jdt.core.IPackageFragmentRoot root : jp.getPackageFragmentRoots()) {
            if (root.getKind() != org.eclipse.jdt.core.IPackageFragmentRoot.K_SOURCE) continue;
            for (org.eclipse.jdt.core.IJavaElement pkg : root.getChildren()) {
                if (!(pkg instanceof org.eclipse.jdt.core.IPackageFragment)) continue;
                for (ICompilationUnit u : ((org.eclipse.jdt.core.IPackageFragment) pkg).getCompilationUnits()) {
                    if (u.equals(cu)) continue;
                    String src = u.getSource();
                    if (src == null || !used.matcher(src).find()) continue;
                    for (IType t : u.getTypes()) for (IMethod m : t.getMethods()) if (m.isMainMethod()) found.add(t);
                }
            }
        }
        if (found.isEmpty()) return null;
        // התרגיל שנוצר ב"שליחת תרגיל": main במחלקה הראשית, באותה חבילה (בתרגילים אחרים אולי יש מחלקה באותו שם) — היא קודמת
        List<IType> same = new ArrayList<IType>();
        for (IType t : found) if (t.getPackageFragment().equals(cu.getParent())) same.add(t);
        if (!same.isEmpty()) found = same;
        if (found.size() == 1) return found.get(0);
        List<String> names = new ArrayList<String>();
        for (IType t : found) names.add(t.getFullyQualifiedName());
        int i = Say.choose(shell, "הרצה ויזואלית", "בקובץ " + file.getName() + " אין main. איזו מחלקה להריץ? (כולן משתמשות ב-" + name + ")", names);
        return i < 0 ? null : found.get(i);
    }

    /** אין main ואין מי שמשתמש במחלקה: מציעים להוסיף main קצרה לבדיקה בסוף המחלקה (רק אם התלמיד מסכים — כל השאר לא משתנה) */
    private static void offerMain(Shell shell, ExecutionEvent event, IFile file) throws CoreException {
        ICompilationUnit cu = JavaCore.createCompilationUnitFrom(file);
        IType t = cu == null || cu.getTypes().length == 0 ? null : cu.getTypes()[0];
        if (t == null) {
            Say.info(shell, "הרצה ויזואלית", "בקובץ " + file.getName() + " אין main, אז אין מה להריץ.");
            return;
        }
        String name = t.getElementName();
        if (!Say.ask(shell, "הרצה ויזואלית",
                "בקובץ " + file.getName() + " אין main, ואין בפרויקט מחלקה עם main שמשתמשת ב-" + name + ".\n\n"
                + "להוסיף בסוף המחלקה פעולת main קצרה לבדיקה? (כל מה שכתבתם נשאר כמו שהוא)\n"
                + "אחרי שתכתבו בה מה לבדוק, לוחצים שוב \"הרצה ויזואלית\".", "הוספת main", "ביטול")) return;
        org.eclipse.ui.IEditorPart ed = Ui.openAt(HandlerUtil.getActiveWorkbenchWindow(event).getActivePage(), file, 0);
        if (!(ed instanceof org.eclipse.ui.texteditor.ITextEditor)) return;
        org.eclipse.ui.texteditor.ITextEditor te = (org.eclipse.ui.texteditor.ITextEditor) ed;
        org.eclipse.jface.text.IDocument doc = te.getDocumentProvider().getDocument(te.getEditorInput());
        int close = Scaffold.classEnd(doc.get(), t.getNameRange().getOffset() + 1);
        if (close < 0) return;
        try {
            int lineStart = doc.getLineOffset(doc.getLineOfOffset(close));
            String var = Character.toLowerCase(name.charAt(0)) + name.substring(1);
            String code = "\n    // בדיקה: כאן יוצרים אובייקטים וקוראים לפעולות (\"הרצה ויזואלית\" מראה כל שורה)\n"
                    + "    public static void main(String[] args) {\n"
                    + "        // למשל: " + name + " " + var + " = new " + name + "(...);\n"
                    + "        \n"
                    + "    }\n";
            doc.replace(lineStart, 0, code);
            int caret = lineStart + code.indexOf("        \n") + 8;
            te.selectAndReveal(caret, 0);
        } catch (org.eclipse.jface.text.BadLocationException e) {
            // לא נוסף
        }
    }

    private static String mainType(IFile file) throws CoreException {
        ICompilationUnit cu = JavaCore.createCompilationUnitFrom(file);
        for (IType t : cu.getTypes()) {
            for (IMethod m : t.getMethods()) if (m.isMainMethod()) return t.getFullyQualifiedName();
        }
        return null;
    }

    /**
     * תצורת הרצה לכל מחלקה — נוצרת פעם אחת ונשמרת. המחלקה הראשית היא LineByLine, ושם המחלקה של התלמיד
     * עובר כארגומנט. התצורה "פרטית" (לא בהיסטוריית Run) — כדי שכפתור ה-Run הרגיל לא יפתח את החלון.
     */
    private static ILaunchConfiguration config(org.eclipse.jdt.core.IJavaProject jp, String main) throws CoreException {
        String project = jp.getProject().getName();
        ILaunchManager mgr = DebugPlugin.getDefault().getLaunchManager();
        ILaunchConfigurationType type = mgr.getLaunchConfigurationType(IJavaLaunchConfigurationConstants.ID_JAVA_APPLICATION);
        for (ILaunchConfiguration c : mgr.getLaunchConfigurations(type)) {
            if (LINE_RUNNER.equals(c.getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, ""))
                    && main.equals(c.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, ""))
                    && project.equals(c.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, ""))) {
                ILaunchConfigurationWorkingCopy wc = c.getWorkingCopy();
                setup(wc, jp);   // בכל הרצה מחדש: ה-classpath של הפרויקט יכול להשתנות
                return wc.doSave();
            }
        }
        String simple = main.substring(main.lastIndexOf('.') + 1) + " (הרצה ויזואלית)";
        ILaunchConfigurationWorkingCopy wc = type.newInstance(null, mgr.generateLaunchConfigurationName(simple));
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, project);
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, LINE_RUNNER);
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, main);
        setup(wc, jp);
        return wc.doSave();
    }

    /**
     * הרצה שורה-שורה צריכה Java עם ממשק הדיבאג (jdk.jdi). אם תוכנית ההתקנה התקינה Java 21 —
     * רושמים אותה ב-Eclipse (פעם אחת) ומריצים איתה. מחזיר את נתיב ה-JRE container, או null.
     */
    private static String bundledJre() {
        try {
            String home = AutoSetup.dsvizFile("jdk-path.txt");
            if (home == null || !(new java.io.File(home, "bin/java.exe").isFile() || new java.io.File(home, "bin/java").isFile()))
                return null;
            org.eclipse.jdt.launching.IVMInstallType type =
                    JavaRuntime.getVMInstallType("org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType");
            if (type == null) return null;
            org.eclipse.jdt.launching.IVMInstall vm = type.findVMInstall("il.lamerhav.dsviz.jdk21");
            if (vm == null) {
                org.eclipse.jdt.launching.VMStandin s = new org.eclipse.jdt.launching.VMStandin(type, "il.lamerhav.dsviz.jdk21");
                s.setName("Java 21 (מבני נתונים)");
                s.setInstallLocation(new java.io.File(home));
                vm = s.convertToRealVM();
                JavaRuntime.saveVMConfiguration();
            }
            return JavaRuntime.newJREContainerPath(vm).toPortableString();
        } catch (Exception e) {
            return null;   // נשארים עם ה-Java של הפרויקט
        }
    }

    private static void setup(ILaunchConfigurationWorkingCopy wc, org.eclipse.jdt.core.IJavaProject jp) throws CoreException {
        // Java 21 שבהתקנה (עם ממשק הדיבאג) — אלא אם הפרויקט מקומפל לגרסה חדשה יותר
        String jre = targetVersion(jp) <= 21 ? bundledJre() : null;
        if (jre != null) wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_JRE_CONTAINER_PATH, jre);
        else wc.removeAttribute(IJavaLaunchConfigurationConstants.ATTR_JRE_CONTAINER_PATH);
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_DEFAULT_CLASSPATH, false);
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CLASSPATH, launchClasspath(jp));
        wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, VM_ARGS);
        wc.setAttribute(IDebugUIConstants.ATTR_CONSOLE_ENCODING, "UTF-8");
        wc.setAttribute(IDebugUIConstants.ATTR_PRIVATE, true);
    }
}

package il.lamerhav.dsviz.intellij;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiMethodUtil;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * "הרצה ויזואלית" ב-IntelliJ — כמו RunVisualHandler ב-Eclipse: שומרים, מוודאים שהספרייה במודול, מקמפלים (CompilerManager.make),
 * ועם שגיאות בקובץ (או בקבצים שהוא משתמש בהם) לא מריצים — חלון בעברית ופתיחת השגיאה הראשונה. אחרת מריצים את
 * unit4.collectionsLib.LineByLine עם המחלקה של התלמיד, והפלט (וגם הקלט מהמקלדת) בחלון Run של IntelliJ.
 */
public final class VisualRunner {
    private VisualRunner() {}

    static final String TITLE = "הרצה ויזואלית";

    /** הפקודה: java ... -cp <classpath> unit4.collectionsLib.LineByLine <המחלקה> (בלי תלות ב-IntelliJ — נבדק גם בבדיקות) */
    public static GeneralCommandLine commandLine(String javaExe, List<String> classpath, String mainClass, File workDir, String... extraVmArgs) {
        GeneralCommandLine cmd = new GeneralCommandLine(javaExe);
        cmd.addParameters("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Ddsviz.visual=true");
        if (SystemInfo.isMac) cmd.addParameter("-Xdock:name=DSViz");   // השם ב-Dock (ולא "java")
        cmd.addParameters(extraVmArgs);
        cmd.addParameters("-cp", String.join(File.pathSeparator, classpath), Dsviz.LINE_RUNNER, mainClass);
        cmd.setCharset(StandardCharsets.UTF_8);
        if (workDir != null) cmd.setWorkDirectory(workDir);
        return cmd;
    }

    // ------------------------------------------------------------------ מאיפה מריצים

    /** המחלקה עם main בקובץ (הראשונה), או null */
    public static PsiClass mainClassIn(PsiJavaFile file) {
        for (PsiClass c : PsiTreeUtil.findChildrenOfType(file, PsiClass.class)) {
            if (c.getQualifiedName() != null && PsiMethodUtil.hasMainMethod(c)) return c;
        }
        for (PsiClass c : file.getClasses()) if (PsiMethodUtil.hasMainMethod(c)) return c;
        return null;
    }

    /**
     * main של התרגיל: בתרגיל של "שליחת תרגיל" הסעיפים יכולים להיות במחלקה אחת ו-main במחלקה אחרת באותה חבילה (Program.java).
     * מחפשים בקבצים שבאותה תיקייה מחלקה עם main שמזכירה את המחלקה — בלי אינדקס, אז זה עובד גם מיד אחרי שהקבצים נוצרו.
     * null — אין אחת כזאת בדיוק.
     */
    static PsiClass mainInPackage(PsiJavaFile file, PsiClass cls) {
        com.intellij.psi.PsiDirectory dir = file.getContainingDirectory();
        if (dir == null || cls.getName() == null) return null;
        Pattern name = Pattern.compile("\\b" + Pattern.quote(cls.getName()) + "\\b");
        List<PsiClass> found = new ArrayList<PsiClass>();
        for (PsiFile f : dir.getFiles()) {
            if (!(f instanceof PsiJavaFile) || f.equals(file)) continue;
            PsiClass m = mainClassIn((PsiJavaFile) f);
            if (m != null && name.matcher(f.getText()).find()) found.add(m);
        }
        return found.size() == 1 ? found.get(0) : null;
    }

    /** המחלקות בפרויקט עם main שמשתמשות במחלקה (למחלקה בלי main — למשל מחלקה שהתלמיד כתב) */
    static List<PsiClass> mainsUsing(PsiClass cls) {
        Set<PsiClass> out = new LinkedHashSet<PsiClass>();
        Collection<PsiReference> refs = ReferencesSearch.search(cls, GlobalSearchScope.projectScope(cls.getProject())).findAll();
        for (PsiReference r : refs) {
            PsiFile f = r.getElement().getContainingFile();
            if (!(f instanceof PsiJavaFile) || f.equals(cls.getContainingFile())) continue;
            PsiClass m = mainClassIn((PsiJavaFile) f);
            if (m != null) out.add(m);
        }
        return new ArrayList<PsiClass>(out);
    }

    /** הפעולה (מהתפריט, מהסרגל, מהגאטר): הקובץ הפתוח → המחלקה להריץ → קומפילציה → הרצה */
    public static void run(Project project, PsiJavaFile file) {
        PsiClass main = mainClassIn(file);
        if (main == null) {
            PsiClass[] classes = file.getClasses();
            if (classes.length == 0) {
                Say.info(project, "בקובץ " + file.getName() + " אין מחלקה — אין מה להריץ.", TITLE);
                return;
            }
            main = mainInPackage(file, classes[0]);
            List<PsiClass> users = main != null ? java.util.Collections.singletonList(main) : mainsUsing(classes[0]);
            if (users.size() == 1) main = users.get(0);
            else if (users.size() > 1) {
                String[] names = new String[users.size()];
                for (int i = 0; i < names.length; i++) names[i] = users.get(i).getQualifiedName();
                int k = Say.choose(project, "בקובץ " + file.getName() + " אין main. איזו מחלקה להריץ? (כולן משתמשות ב-" + classes[0].getName() + ")",
                        TITLE, Dsviz.RUN, names);
                if (k < 0) return;
                main = users.get(k);
            } else {
                offerMain(project, file, classes[0]);
                return;
            }
        }
        run(project, main);
    }

    public static void run(Project project, PsiClass main) {
        String mainName = ClassUtil.getJVMClassName(main);
        PsiFile psi = main.getContainingFile();
        VirtualFile vf = psi == null ? null : psi.getVirtualFile();
        Module module = vf == null ? null : ModuleUtilCore.findModuleForFile(vf, project);
        if (mainName == null || module == null) {
            Say.info(project, "פתחו קובץ Java (תרגיל) מתוך הפרויקט, ואז לחצו \"הרצה ויזואלית\".", TITLE);
            return;
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments();
        FileDocumentManager.getInstance().saveAllDocuments();
        // פרויקט של התלמיד (כל פרויקט Java): לא משנים בו כלום מלבד הוספת ספריית הקורס אם אין בו בכלל (כדי ש-Queue וכו' יתקמפלו)
        if (Dsviz.libraryJar() != null && ReadAction.compute(() -> LibrarySetup.needs(module))) LibrarySetup.add(module);
        String source = psi.getText();
        CompilerManager.getInstance(project).make(module, (aborted, errors, warnings, ctx) -> {
            if (aborted || project.isDisposed()) return;
            List<CompilerMessage> relevant = relevantErrors(ctx, vf, source);
            if (!relevant.isEmpty()) {
                warn(project, relevant);
                return;
            }
            boolean otherErrors = errors > 0;
            ApplicationManager.getApplication().executeOnPooledThread(() -> launch(project, module, mainName, vf, otherErrors));
        });
    }

    /**
     * השגיאות שחוסמות: בקובץ הזה, ובקבצים שהוא משתמש בהם (לפי שם המחלקה) — בפרויקט יש הרבה תרגילים, ושגיאה בתרגיל אחר לא צריכה לחסום.
     * השגיאות בקובץ הפתוח קודם.
     */
    static List<CompilerMessage> relevantErrors(CompileContext ctx, VirtualFile file, String source) {
        List<CompilerMessage> mine = new ArrayList<CompilerMessage>(), used = new ArrayList<CompilerMessage>(), unknown = new ArrayList<CompilerMessage>();
        for (CompilerMessage m : ctx.getMessages(CompilerMessageCategory.ERROR)) {
            VirtualFile f = m.getVirtualFile();
            if (f == null) unknown.add(m);
            else if (f.equals(file)) mine.add(m);
            else if ("java".equals(f.getExtension()) && Pattern.compile("\\b" + Pattern.quote(f.getNameWithoutExtension()) + "\\b").matcher(source).find()) used.add(m);
        }
        mine.addAll(used);
        if (mine.isEmpty() && !unknown.isEmpty() && ctx.getMessages(CompilerMessageCategory.ERROR).length == unknown.size()) mine.addAll(unknown);
        return mine;
    }

    /**
     * יש שגיאות קומפילציה — לא מריצים. בחלון שקופץ רק התראה קצרה ו"מעבר לשגיאה הראשונה";
     * הפירוט — בחלון הצדדי Build (נפתח כאן בלי לקחת את הפוקוס מהעורך).
     */
    static void warn(Project project, List<CompilerMessage> errors) {
        List<Notice.Card> cards = new ArrayList<Notice.Card>();
        for (CompilerMessage m : errors) cards.add(card(project, m));
        showBuildWindow(project);
        Notice.show(project, compileErrors(cards));
    }

    /** "יש 2 שגיאות בקוד" + שורה אחת, בלי כרטיסים — הפירוט בחלון Build */
    static Notice.Spec compileErrors(List<Notice.Card> cards) {
        int n = cards.size();
        Notice.Spec s = new Notice.Spec(Modern.Kind.ERROR, TITLE, n == 1 ? "יש שגיאה בקוד" : "יש " + n + " שגיאות בקוד")
                .buttons("מעבר לשגיאה הראשונה", "סגירה");
        s.text = "מתקנים ומריצים שוב. הפירוט בחלון Build בצד.";
        s.onPrimary = n == 0 ? null : cards.get(0).go;
        s.width = 400;
        return s;
    }

    /** פותח את חלון Build (רשימת השגיאות) בלי לקחת את הפוקוס */
    static void showBuildWindow(Project project) {
        ApplicationManager.getApplication().invokeLater(() -> {
            if (project.isDisposed()) return;
            com.intellij.openapi.wm.ToolWindow w = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("Build");
            if (w != null) w.show(null);
        });
    }

    /** כרטיס לשגיאה: "Button.java · שורה 42", ההסבר בעברית, והשורה מהקוד */
    static Notice.Card card(Project project, CompilerMessage m) {
        VirtualFile f = m.getVirtualFile();
        Navigatable nav = m.getNavigatable();
        int line = nav instanceof OpenFileDescriptor ? ((OpenFileDescriptor) nav).getLine() : -1;
        String where = (f == null ? "" : f.getName()) + (line >= 0 ? (f == null ? "" : " · ") + "שורה " + (line + 1) : "");
        String code = "";
        if (f != null && line >= 0) {
            code = ReadAction.compute(() -> {
                Document d = FileDocumentManager.getInstance().getDocument(f);
                if (d == null || line >= d.getLineCount()) return "";
                return d.getText(new com.intellij.openapi.util.TextRange(d.getLineStartOffset(line), d.getLineEndOffset(line))).trim();
            });
        }
        Runnable go = nav != null ? () -> { if (nav.canNavigate()) nav.navigate(true); } : null;
        return new Notice.Card(where, JavacHebrew.translate(m.getMessage()), code, go);
    }

    static String describe(CompilerMessage m) {
        VirtualFile f = m.getVirtualFile();
        String where = f == null ? "" : f.getName();
        if (m.getNavigatable() instanceof OpenFileDescriptor) where += " · שורה " + (((OpenFileDescriptor) m.getNavigatable()).getLine() + 1);
        return (where.isEmpty() ? "" : where + "\n") + JavacHebrew.translate(m.getMessage());
    }

    // ------------------------------------------------------------------ ההרצה

    /** ה-JDK, ה-classpath וההרצה עצמה (בחוט רקע) */
    static void launch(Project project, Module module, String mainName, VirtualFile mainFile, boolean otherErrors) {
        Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
        JdkFinder.Found jdk = JdkFinder.find(sdk == null ? null : sdk.getHomePath(), targetVersion(project, module));
        if (jdk == null) {
            ApplicationManager.getApplication().invokeLater(() -> Say.error(project,
                    "לא נמצאה Java (JDK) להרצה.\nאפשר להתקין מחדש את DSViz (ההתקנה כוללת Java 21), או לבחור JDK לפרויקט ב-File ← Project Structure.", TITLE));
            return;
        }
        List<String> cp = classpath(module);
        if (otherErrors) {
            // יש שגיאות רק בתרגילים אחרים במודול — מקמפלים לבד רק את הקובץ הזה ואת מה שהוא משתמש בו (javac -sourcepath)
            File out = compileAlone(module, mainFile, jdk, cp);
            if (out != null) cp.add(1, out.getAbsolutePath());
        }
        File dir = new File(project.getBasePath() == null ? System.getProperty("user.home") : project.getBasePath());
        GeneralCommandLine cmd = commandLine(jdk.java(), cp, mainName, dir);
        ApplicationManager.getApplication().invokeLater(() -> start(project, cmd, mainName, module, mainFile));
    }

    private static void start(Project project, GeneralCommandLine cmd, String mainName, Module module, VirtualFile mainFile) {
        if (project.isDisposed()) return;
        try {
            OSProcessHandler handler = new KillableColoredProcessHandler(cmd);
            String simple = mainName.substring(mainName.lastIndexOf('.') + 1);
            new RunContentExecutor(project, handler)
                    .withTitle(TITLE + ": " + simple)
                    .withActivateToolWindow(true)
                    .withFocusToolWindow(false)
                    .withRerun(() -> {
                        PsiFile f = ReadAction.compute(() -> mainFile.isValid() ? com.intellij.psi.PsiManager.getInstance(project).findFile(mainFile) : null);
                        if (f instanceof PsiJavaFile) run(project, (PsiJavaFile) f);
                    })
                    .run();
        } catch (ExecutionException e) {
            Say.error(project, "לא הצלחתי להריץ: " + e.getMessage(), TITLE);
        }
    }

    /** קודם הספרייה שלנו (מתוך התוסף — היא שמציירת את המבנים), ואחריה ה-classpath הרגיל של המודול (בלי ה-JDK) */
    static List<String> classpath(Module module) {
        List<String> cp = new ArrayList<String>();
        File lib = Dsviz.libraryJar();
        if (lib != null) cp.add(lib.getAbsolutePath());
        for (String p : ReadAction.compute(() -> OrderEnumerator.orderEntries(module).recursively().withoutSdk().getPathsList().getPathList()))
            if (!cp.contains(p)) cp.add(p);
        return cp;
    }

    /** לאיזו גרסת Java המודול מקומפל (8, 11, 17, 21...), או 0 */
    static int targetVersion(Project project, Module module) {
        try {
            String t = CompilerConfiguration.getInstance(project).getBytecodeTargetLevel(module);
            if (t != null && !t.isEmpty()) return t.startsWith("1.") ? Integer.parseInt(t.substring(2)) : Integer.parseInt(t.replaceAll("\\D.*", ""));
        } catch (Exception ignored) {
            // לפי רמת השפה
        }
        try {
            LanguageLevel l = ReadAction.compute(() -> com.intellij.openapi.module.LanguageLevelUtil.getEffectiveLanguageLevel(module));
            return l.toJavaVersion().feature;
        } catch (Throwable e) {
            return 0;
        }
    }

    /** javac של ה-JDK על הקובץ בלבד (וכל מה שהוא משתמש בו — ‎-sourcepath) לתיקייה זמנית. null אם לא הצליח */
    static File compileAlone(Module module, VirtualFile mainFile, JdkFinder.Found jdk, List<String> cp) {
        String javac = jdk.javac();
        if (javac == null) return null;
        try {
            File out = Files.createTempDirectory("dsviz-run").toFile();
            List<String> roots = new ArrayList<String>();
            for (VirtualFile r : ReadAction.compute(() -> ModuleRootManager.getInstance(module).getSourceRoots(false))) roots.add(r.getPath());
            List<String> args = new ArrayList<String>();
            args.add(javac);
            args.add("-g");
            args.add("-nowarn");
            args.add("-encoding");
            args.add("UTF-8");
            args.add("-d");
            args.add(out.getAbsolutePath());
            args.add("-cp");
            args.add(String.join(File.pathSeparator, cp));
            if (!roots.isEmpty()) {
                args.add("-sourcepath");
                args.add(String.join(File.pathSeparator, roots));
            }
            args.add(mainFile.getPath());
            Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0 ? out : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ מחלקה בלי main

    /** אין main ואין מי שמשתמש במחלקה: מציעים להוסיף main קצרה לבדיקה בסוף המחלקה (רק אם התלמיד מסכים) */
    static void offerMain(Project project, PsiJavaFile file, PsiClass cls) {
        String name = cls.getName();
        if (!Say.ask(project,
                "בקובץ " + file.getName() + " אין main, ואין בפרויקט מחלקה עם main שמשתמשת ב-" + name + ".\n\n"
                + "להוסיף בסוף המחלקה פעולת main קצרה לבדיקה? (כל מה שכתבתם נשאר כמו שהוא)\n"
                + "אחרי שתכתבו בה מה לבדוק — לוחצים שוב \"הרצה ויזואלית\".", TITLE, "להוסיף main", "לא")) return;
        Document doc = PsiDocumentManager.getInstance(project).getDocument(file);
        if (doc == null || cls.getRBrace() == null) return;
        int close = cls.getRBrace().getTextOffset();
        int lineStart = doc.getLineStartOffset(doc.getLineNumber(close));
        String var = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        String code = "\n    // בדיקה: כאן יוצרים אובייקטים וקוראים לפעולות (\"הרצה ויזואלית\" מראה כל שורה)\n"
                + "    public static void main(String[] args) {\n"
                + "        // למשל: " + name + " " + var + " = new " + name + "(...);\n"
                + "        \n"
                + "    }\n";
        WriteCommandAction.runWriteCommandAction(project, TITLE, null, () -> doc.insertString(lineStart, code), file);
        new OpenFileDescriptor(project, file.getVirtualFile(), lineStart + code.indexOf("        \n") + 8).navigate(true);
    }
}

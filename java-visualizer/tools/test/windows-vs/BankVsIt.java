package il.lamerhav.dsviz;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * בדיקה של "מאגר תרגילים" ל-Visual Studio, כמו תלמיד: פותחים את החלון, בוחרים נושא ושאלה, מסננים, מחפשים, "צור תרגיל" (הפרויקט נוצר
 * ונרשם בפתרון), החלון הצף, "הצעה לפתרון", "לפעולה של סעיף א", ובסוף "סיימתי את התרגיל" + אישור — והפרויקט נמחק עם כל המעטפת שלו.
 * צילום של כל חלון (וב-Windows גם של כל המסך — עם Visual Studio).
 *   java -cp dsviz.jar;dsviz-bank-vs.jar;<זה> il.lamerhav.dsviz.BankVsIt <DataStructures-CS> <תיקיית פלט> [--go <קובץ>]
 * --go: אחרי היצירה כותבים <פלט>/created.txt ומחכים שהקובץ יופיע (בינתיים הסקריפט בונה, מריץ ופותח את Visual Studio עם החלון הצף).
 */
public final class BankVsIt {
    static File out;
    static int fails, oks;
    static final List<String> lines = new ArrayList<String>();
    static final boolean windows = System.getProperty("os.name").toLowerCase().contains("win");

    public static void main(String[] a) throws Exception {
        Path root = Paths.get(a[0]);
        out = new File(a[1]);
        out.mkdirs();
        String go = null;
        for (int i = 2; i < a.length; i++) if (a[i].equals("--go")) go = a[++i];
        if (a.length > 3 && a[2].equals("--clean-check")) {
            // הפתיחה הבאה של הכלי (אחרי ש-Visual Studio נסגר / שחרר): מה שנשאר מהתרגיל (obj) נמחק — ורק זה
            File d = new File(a[3]);
            BankVs.init(root);
            check("pending-clean", !d.exists(), "בפתיחה הבאה של הכלי: מה שנשאר מהתרגיל (obj) נמחק", d.exists() ? BankVs.list(d).toString() : "");
            Files.write(new File(out, "clean-result.txt").toPath(), String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
            System.exit(fails == 0 ? 0 : 1);
        }
        try {
            run(root, go);
        } catch (Throwable t) {
            fail("crash", "חריגה: " + t);
            t.printStackTrace();
        }
        log("SUMMARY " + oks + " ok / " + fails + " FAIL");
        Files.write(new File(out, "bank-vs-result.txt").toPath(), String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        System.exit(fails == 0 ? 0 : 1);
    }

    static void run(Path root, String go) throws Exception {
        BankVs.init(root);
        check("load", BankVs.store.questions.size() >= 27 && BankVs.store.problems.isEmpty(),
                "המאגר נטען: " + BankVs.store.questions.size() + " שאלות בגרסת C#", String.valueOf(BankVs.store.problems.size()));
        BankStore.Question q = null;
        for (BankStore.Question x : BankVs.store.questions) if (x.id.equals("stack-sum-size")) q = x;
        final BankStore.Question sq = q;
        check("cs-text", q != null && q.sections.get(0).text.contains("SumStack") && Json.str(q.exercise.get("language")).equals("csharp"),
                "השאלה בגרסת C#: \"SumStack\" בנוסח, language=csharp", "");

        // 1. החלון
        ui(() -> { BankVsWindow.open(null, null); return null; });
        final BankVsWindow w = BankVsWindow.current;
        pause(1500);
        shot(w.frame, "01-bank-window");
        check("tree", ui(() -> w.treeShown.toString()).contains("TOPIC loops open"), "העץ: הנושא הראשון פתוח עם השאלות", ui(() -> w.treeShown.toString()));

        // 2. נושא מחסנית ושאלה
        ui(() -> { w.select("stack-sum-size"); return null; });
        pause(800);
        shot(w.frame, "02-bank-stack");
        String prev = ui(() -> w.previewShown.toString());
        check("preview", prev.contains("SECTION א") && prev.contains("SumStack") && prev.contains("FIGURE stack") && prev.contains("EXPECTED סעיף א: 22"),
                "התצוגה: סעיפים, ציור של המחסנית, הפלט הרצוי", prev);
        check("tree-q", ui(() -> w.treeShown.toString()).contains("Q stack-sum-size easy"), "בעץ: השאלה עם נקודת 'קל'", "");

        // 3. סינון וחיפוש
        click(w.frame, "בינוני");
        pause(500);
        shot(w.frame, "03-filter-medium");
        check("filter", ui(() -> w.treeShown.toString()).equals("[EMPTY]"), "סינון 'בינוני' — עוד אין שאלות: 'לא נמצאו שאלות'", ui(() -> w.treeShown.toString()));
        click(w.frame, "הכול");
        ui(() -> { find(w.frame, JTextField.class).setText("מחסנית"); return null; });
        pause(500);
        shot(w.frame, "04-search");
        String t = ui(() -> w.treeShown.toString());
        check("search", t.contains("Q stack-sum-size") && !t.contains("Q loops-"), "חיפוש 'מחסנית': רק השאלות של מחסנית", t);
        ui(() -> { find(w.frame, JTextField.class).setText(""); return null; });

        // 4. שאלה עם עץ ותור (ציורים)
        ui(() -> { w.select("tree-count-sum"); return null; });
        pause(600);
        shot(w.frame, "05-bank-tree");
        ui(() -> { w.select("queue-sum-even"); return null; });
        pause(600);
        shot(w.frame, "06-bank-queue");
        ui(() -> { w.select("stack-sum-size"); return null; });
        pause(400);

        // 5. "צור תרגיל"
        File ex = CsSolution.exercisesDir(BankVs.root).toFile();
        click(w.frame, "צור תרגיל");
        long end = System.currentTimeMillis() + 240000;
        while (System.currentTimeMillis() < end && ui(() -> BankVsFloat.current == null || !BankVsFloat.current.isShowing())) pause(500);
        pause(1500);
        check("float", ui(() -> BankVsFloat.current != null && BankVsFloat.current.isShowing()), "אחרי 'צור תרגיל': החלון הצף 'השאלה' נפתח", "");
        check("bank-closed", ui(() -> BankVsWindow.current == null), "חלון המאגר נסגר", "");
        List<File> projects = BankVs.projects(sq);
        check("project", projects.size() == 1, "נוצר פרויקט: " + (projects.isEmpty() ? "-" : projects.get(0).getName()), String.valueOf(projects));
        if (projects.isEmpty()) return;
        File dir = projects.get(0);
        File[] cs = dir.listFiles((d, n) -> n.endsWith(".cs"));
        String sln = new String(Files.readAllBytes(CsSolution.solution(BankVs.root)), StandardCharsets.UTF_8);
        check("files", cs != null && cs.length >= 1 && new File(dir, dir.getName() + ".csproj").isFile(), "בפרויקט: .csproj וקבצי .cs (" + (cs == null ? 0 : cs.length) + ")", "");
        check("sln", sln.contains("\"" + dir.getName() + "\""), "הפרויקט רשום בפתרון DataStructures-CS.sln", "");
        String code = cs == null || cs.length == 0 ? "" : new String(Files.readAllBytes(cs[0].toPath()), StandardCharsets.UTF_8);
        check("code", code.contains("SumStack(Stack<int> s)") && code.contains("SizeOf(Stack<int> s)") && code.contains("Console.WriteLine"),
                "הקוד: SumStack ו-SizeOf (Stack<int>), ו-Main שמדפיס", code.length() > 600 ? code.substring(0, 600) : code);
        check("solutions", BankVs.solutionsUnder(dir) == 2, "2 הצעות לפתרון נשמרו בצד (solutions.json)", String.valueOf(BankVs.solutionsUnder(dir)));
        Files.write(new File(out, "created-code.txt").toPath(), code.getBytes(StandardCharsets.UTF_8));
        final JFrame fl = ui(() -> BankVsFloat.current);
        shot(fl, "07-float");
        String fs = ui(() -> BankVsFloat.shown.toString());
        String ft = ui(() -> texts(fl));
        check("float-wording", ft.contains("שם התרגיל: " + dir.getName()) && ft.contains("כותבים את התשובה בכל סעיף") && !ft.contains("בפתרון") && !ft.contains("תמיד מעל"),
                "בחלון הצף: 'שם התרגיל: ...' (בלי 'פתרון' במובן של Visual Studio — 'פתרון' רק לתשובה)", ft);
        check("float-on-top", ui(() -> fl.isAlwaysOnTop() && !fl.isUndecorated() && fl.isResizable()),
                "החלון הצף תמיד מעל (קבוע, בלי תיבת סימון), ועם כפתורי מזעור והגדלה רגילים", "");
        check("float-content", fs.contains("PROJECT " + dir.getName()) && fs.contains("DONE-BUTTON todo") && fs.contains("SECTION א"),
                "בחלון הצף: שם הפרויקט, הסעיפים, 'סיימתי את התרגיל'", fs);

        // 6. "הצעה לפתרון" (חלון) ו"לפעולה של סעיף א"
        SwingUtilities.invokeLater(() -> clickNow(fl, "הצעה לפתרון"));
        JDialog sol = waitDialog("הצעה לפתרון", 10000);
        check("solution", sol != null, "'הצעה לפתרון' של סעיף א נפתחת", "");
        if (sol != null) {
            pause(600);
            shot(sol, "08-solution");
            check("solution-code", texts(sol).contains("SumStack"), "בהצעה: הפעולה SumStack", "");
            ui(() -> { sol.dispose(); return null; });
        }
        // בינתיים (Windows): Visual Studio טוען מחדש את הפתרון (Reload) — ואז "לפעולה" פותח את הקובץ בו
        if (go != null) {
            Files.write(new File(out, "created.txt").toPath(), dir.getAbsolutePath().getBytes(StandardCharsets.UTF_8));
            waitFile(go + ".1");
        }
        BankVs.opened.clear();
        click(fl, "לפעולה של סעיף א");
        pause(1500);
        String op = BankVs.opened.toString();
        check("jump", op.matches(".*FILE \\w+\\.cs:[1-9]\\d*.*"), "'לפעולה של סעיף א' פותח ב-Visual Studio את הקובץ בשורה של SumStack: " + op, op);
        if (windows) {
            pause(6000);
            shotScreen("09-jump-screen");
        }

        // 7. בינתיים: הסקריפט כותב את הפתרון, בונה ב-MSBuild ומריץ "הרצה ויזואלית" (החלון הצף מעל Visual Studio)
        if (go != null) {
            Files.write(new File(out, "jumped.txt").toPath(), BankVs.opened.toString().getBytes(StandardCharsets.UTF_8));
            waitFile(go + ".2");
            ui(() -> { BankVsFloat.render(); return null; });
            pause(800);
        }
        // 7ב. בחלון הצף אין כפתור הרצה — מריצים מהכפתור "הרצה ויזואלית" שבסרגל של Visual Studio (הפרופיל של הפרויקט)
        String ft2 = ui(() -> texts(BankVsFloat.current));
        check("float-no-run", !ft2.contains("| הרצה ויזואלית |") && ft2.contains("ומריצים בכפתור \"הרצה ויזואלית\" שבסרגל העליון"),
                "בחלון הצף אין כפתור הרצה — מריצים מהכפתור 'הרצה ויזואלית' שבסרגל של Visual Studio", ft2);
        if (go != null) {
            Files.write(new File(out, "ran.txt").toPath(), "ok".getBytes(StandardCharsets.UTF_8));
            waitFile(go + ".3");
        }

        // 8. "סיימתי את התרגיל" — כמו תלמיד: לחיצה, החלון שואל, "סיום ומחיקה"
        final JFrame fl2 = ui(() -> BankVsFloat.current);
        if (fl2 == null) { fail("float-gone", "החלון הצף נסגר לפני 'סיימתי'"); return; }
        // קודם "ביטול": חוזרים לשאלה, ושום דבר לא נמחק
        ui(() -> { find(fl2, BankVsFloat.DoneButton.class).click(); return null; });
        pause(400);
        click(fl2, "ביטול");
        pause(300);
        String bt = ui(() -> texts(fl2) + BankVsFloat.shown);
        check("done-cancel", ui(() -> BankVsFloat.confirm == null && fl2.isShowing()) && bt.contains("לפעולה של סעיף") && bt.contains("DONE-BUTTON todo") && dir.isDirectory(),
                "'סיימתי' ואז 'ביטול' — חוזרים לשאלה, ושום דבר לא נמחק", bt);
        // ועכשיו באמת: האישור מופיע בתוך החלון הצף עצמו (במקום השאלה) — בלי חלון נוסף שמכסה אותו
        ui(() -> { find(fl2, BankVsFloat.DoneButton.class).click(); return null; });
        pause(500);
        String at = ui(() -> texts(fl2));
        boolean extra = ui(() -> { for (Window x : Window.getWindows()) if (x instanceof JDialog && x.isShowing()) return true; return false; });
        check("done-ask", ui(() -> BankVsFloat.confirm != null) && !extra && at.contains("התרגיל שיימחק: " + dir.getName()) && !at.contains("מהפתרון")
                && at.contains("רק מה שנוצר מהתרגיל נמחק") && at.contains("סיום ומחיקה") && !at.contains("לפעולה של סעיף") && !at.matches(".*\\bV\\b.*"),
                "'סיימתי את התרגיל' — האישור בתוך החלון הצף (בעברית בלבד, עם שם התרגיל), בלי חלון נוסף שמכסה", at);
        if (ui(() -> BankVsFloat.confirm == null)) return;
        pause(500);
        shot(fl2, "10-done-ask");
        if (windows) shotScreen("10-done-ask-screen");
        SwingUtilities.invokeLater(() -> clickNow(fl2, "סיום ומחיקה"));
        end = System.currentTimeMillis() + 30000;
        while (System.currentTimeMillis() < end && ui(() -> BankVsFloat.current != null)) pause(300);
        pause(1000);
        if (dir.exists()) log("  ..   נשאר אחרי 'סיימתי' (Visual Studio מחזיק): " + BankVs.list(dir));
        if (go != null) {
            // Visual Studio טוען מחדש את הפתרון (בלי הפרויקט) ומשחרר את קבצי הבנייה; בפתיחה הבאה של הכלי — מה שנשאר (bin / obj) נמחק
            Files.write(new File(out, "done.txt").toPath(), "ok".getBytes(StandardCharsets.UTF_8));
            waitFile(go + ".4");
        }
        BankVs.cleanPending();
        String sln2 = new String(Files.readAllBytes(CsSolution.solution(BankVs.root)), StandardCharsets.UTF_8);
        log("  ..   AFTER-DONE dir=" + dir.exists() + " sln=" + sln2.contains("\"" + dir.getName() + "\"") + " solutions=" + BankVs.solutionsUnder(dir)
                + " created=" + BankProgress.get().createdOf(sq.id).size() + " done=" + BankProgress.get().done(sq.id));
        check("done-float", ui(() -> BankVsFloat.current == null), "אחרי האישור: החלון הצף נסגר", "");
        check("done-mark", BankProgress.get().done(sq.id), "השאלה סומנה בסימן ירוק", "");
        boolean pending = dir.exists() && BankVs.pendingFile().isFile()
                && new String(Files.readAllBytes(BankVs.pendingFile().toPath()), StandardCharsets.UTF_8).contains(dir.getAbsolutePath() + "|");
        boolean onlyBuild = true;
        if (dir.exists()) for (String p : BankVs.list(dir)) onlyBuild &= p.equals("obj") || p.equals("bin") || p.startsWith("obj" + File.separator) || p.startsWith("bin" + File.separator);
        check("done-dir", !dir.exists() || pending && onlyBuild, !dir.exists() ? "תיקיית הפרויקט נמחקה (כולל bin / obj)"
                : "קבצי התרגיל נמחקו; נשארו רק קבצי בנייה (obj) ש-Visual Studio יצר מחדש — נרשמו למחיקה בפתיחה הבאה של הכלי", dir.getPath() + " " + (dir.exists() ? BankVs.list(dir) : ""));
        Files.write(new File(out, "pending-dir.txt").toPath(), (dir.exists() ? dir.getAbsolutePath() : "").getBytes(StandardCharsets.UTF_8));
        check("done-sln", !sln2.contains("\"" + dir.getName() + "\"") && sln2.contains("Global") && sln2.contains("EndGlobal"),
                "הפרויקט הוצא מהפתרון (.sln), והפתרון שלם", "");
        check("done-solutions", BankVs.solutionsUnder(dir) == 0, "ההצעות לפתרון של התרגיל נמחקו", "");
        check("done-forget", BankProgress.get().createdOf(sq.id).isEmpty(), "השיוך של התרגיל לשאלה נמחק", "");
        Files.write(new File(out, "sln-after.txt").toPath(), sln2.getBytes(StandardCharsets.UTF_8));

        // 8ב. קבצים אחרים של התלמיד לא נמחקים: תרגיל נוסף, והתלמיד הוסיף קובץ משלו לפרויקט (ועוד קובץ בתיקיית התרגילים)
        BankStore.Question q2 = null;
        for (BankStore.Question x : BankVs.store.questions) if (x.id.equals("stack-max-contains")) q2 = x;
        final BankStore.Question kq = q2;
        ExercisePipeline.Result r2 = BankVs.create(kq, new ExercisePipeline.Progress() {
            public void say(String text) { }

            public boolean cancelled() { return false; }
        });
        File dir2 = CsSolution.exercisesDir(BankVs.root).resolve(r2.folderName).toFile();
        File mine = new File(dir2, "MyHelper.cs"), notes = BankVs.root.resolve("my-notes.txt").toFile();
        Files.write(mine.toPath(), "public static class MyHelper\n{\n    public static int Twice(int x) { return 2 * x; }\n\n    public static void Main() { System.Console.WriteLine(Twice(21)); }\n}\n".getBytes(StandardCharsets.UTF_8));
        Files.write(notes.toPath(), "שלי".getBytes(StandardCharsets.UTF_8));
        List<File> made2 = new ArrayList<File>(r2.files);
        BankVs.Removal plan2 = BankVs.plan(BankProgress.get().createdOf(kq.id).get(0));
        log("  ..   KEEP plan: files=" + plan2.files + " others=" + plan2.others + " whole=" + plan2.wholeProject());
        check("keep-plan", !plan2.wholeProject() && plan2.others.contains("MyHelper.cs") && plan2.files.size() == made2.size(),
                "תרגיל שהתלמיד הוסיף לו קובץ משלו: רק קבצי התרגיל יימחקו, הפרויקט נשאר", plan2.others.toString());
        BankVs.complete(null, kq, false);
        int left2 = 0;
        for (File f : made2) if (f.exists()) left2++;
        String sln3 = new String(Files.readAllBytes(CsSolution.solution(BankVs.root)), StandardCharsets.UTF_8);
        check("keep-files", left2 == 0, "קבצי התרגיל נמחקו (" + made2.size() + ")", String.valueOf(left2));
        check("keep-mine", mine.isFile() && notes.isFile(), "הקובץ של התלמיד בפרויקט (MyHelper.cs) והקובץ שלו בתיקיית התרגילים — נשארו", "");
        check("keep-project", dir2.isDirectory() && new File(dir2, dir2.getName() + ".csproj").isFile() && sln3.contains("\"" + dir2.getName() + "\""),
                "הפרויקט " + dir2.getName() + " (עם הקובץ של התלמיד) נשאר בפתרון", "");
        check("keep-other-sln", sln3.contains("\"Exercise\"") && sln3.contains("\"StackExamples\""), "שאר הפרויקטים בפתרון לא נגעו", "");
        Files.write(new File(out, "keep-project.txt").toPath(), dir2.getAbsolutePath().getBytes(StandardCharsets.UTF_8));

        // 9. המאגר שוב: הסימן הירוק בעץ ו"צור תרגיל" (לא "פתח")
        ui(() -> { BankVsWindow.open(null, "stack-sum-size"); return null; });
        pause(1200);
        final BankVsWindow w2 = BankVsWindow.current;
        shot(w2.frame, "11-bank-after-done");
        check("after-tree", ui(() -> w2.treeShown.toString()).contains("Q stack-sum-size easy done"), "בעץ: סימן ירוק ליד השאלה", ui(() -> w2.treeShown.toString()));
        check("after-create", ui(() -> texts(w2.frame)).contains("צור תרגיל") && !ui(() -> w2.previewShown.toString()).contains("EXISTING"),
                "אחרי המחיקה: שוב 'צור תרגיל'", "");
        ui(() -> { w2.frame.dispose(); return null; });
    }

    // ------------------------------------------------------------------ עזרים

    static void check(String id, boolean ok, String what, String why) {
        if (ok) { oks++; log("  ok   " + id + "  " + what); }
        else fail(id, what + (why == null || why.isEmpty() ? "" : " — " + why));
    }

    static void fail(String id, String what) {
        fails++;
        log("  FAIL " + id + "  " + what);
    }

    static void log(String s) {
        System.out.println(s);
        lines.add(s);
    }

    static <T> T ui(Callable<T> c) throws Exception {
        final AtomicReference<T> r = new AtomicReference<T>();
        final AtomicReference<Exception> e = new AtomicReference<Exception>();
        SwingUtilities.invokeAndWait(() -> {
            try { r.set(c.call()); } catch (Exception x) { e.set(x); }
        });
        if (e.get() != null) throw e.get();
        return r.get();
    }

    static void waitFile(String path) {
        File g = new File(path);
        long end = System.currentTimeMillis() + 30 * 60000;
        while (!g.exists() && System.currentTimeMillis() < end) pause(1000);
        log("  ..   ממשיכים (" + g.getName() + ")");
    }

    static void pause(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }

    static void click(Container c, String text) throws Exception {
        ui(() -> { clickNow(c, text); return null; });
    }

    static void clickNow(Container c, String text) {
        for (Component k : all(c)) {
            if (k instanceof AbstractButton && k.isShowing() && ((AbstractButton) k).getText().replace("‏", "").trim().equals(text)) {
                ((AbstractButton) k).doClick();
                return;
            }
        }
        throw new IllegalStateException("אין כפתור \"" + text + "\"");
    }

    static String texts(Container c) {
        StringBuilder b = new StringBuilder();
        for (Component k : all(c)) {
            if (k instanceof javax.swing.JLabel) b.append(((javax.swing.JLabel) k).getText()).append(" | ");
            else if (k instanceof AbstractButton) b.append(((AbstractButton) k).getText()).append(" | ");
            else if (k instanceof javax.swing.text.JTextComponent) b.append(((javax.swing.text.JTextComponent) k).getText()).append(" | ");
        }
        return b.toString().replaceAll("[\u200E\u200F\u202A-\u202E]", "");
    }

    @SuppressWarnings("unchecked")
    static <T> T find(Container c, Class<T> type) {
        for (Component k : all(c)) if (type.isInstance(k)) return (T) k;
        return null;
    }

    static List<Component> all(Container c) {
        List<Component> out = new ArrayList<Component>();
        for (Component k : c.getComponents()) {
            out.add(k);
            if (k instanceof Container) out.addAll(all((Container) k));
        }
        if (c instanceof JFrame) out.addAll(all(((JFrame) c).getContentPane()));
        return out;
    }

    static JDialog waitDialog(String title, long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog && w.isShowing() && ((JDialog) w).getTitle().replace("‏", "").contains(title)) return (JDialog) w;
            }
            pause(200);
        }
        return null;
    }

    /** צילום של החלון עצמו (מהציור של Swing — גם אם חלון אחר מסתיר אותו) */
    static void shot(Window w, String name) {
        try {
            final BufferedImage[] img = new BufferedImage[1];
            SwingUtilities.invokeAndWait(() -> {
                java.awt.Container root = w instanceof JFrame ? ((JFrame) w).getRootPane() : ((JDialog) w).getRootPane();
                img[0] = new BufferedImage(Math.max(1, root.getWidth()), Math.max(1, root.getHeight()), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img[0].createGraphics();
                root.paint(g);
                g.dispose();
            });
            ImageIO.write(img[0], "png", new File(out, name + ".png"));
            log("  ..   צילום: " + name + ".png");
        } catch (Exception e) {
            log("  ..   צילום " + name + " נכשל: " + e);
        }
    }

    /** צילום של כל המסך (Windows: רואים את Visual Studio והחלון הצף מעליו) */
    static void shotScreen(String name) {
        try {
            Rectangle r = new Rectangle(java.awt.Toolkit.getDefaultToolkit().getScreenSize());
            ImageIO.write(new Robot().createScreenCapture(r), "png", new File(out, name + ".png"));
            log("  ..   צילום מסך: " + name + ".png");
        } catch (Exception e) {
            log("  ..   צילום מסך " + name + " נכשל: " + e);
        }
    }
}

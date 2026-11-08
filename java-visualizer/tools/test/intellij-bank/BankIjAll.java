package il.lamerhav.dsviz;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * "מאגר תרגילים" ל-IntelliJ (הכלי במצב --ij) — בדיקה רחבה בלי IntelliJ: גשר מדומה רושם כל בקשה ל-IntelliJ (OPEN / RUN / REFRESH).
 *   1. כל 27 השאלות (גרסת Java): חבילה בתיקיית הקוד, בלי סימוני כיוון, הצעה לכל סעיף; "התלמיד פותר" — javac מקמפל לתיקיית הפלט
 *      (כמו out/production של IntelliJ), והתוכנית מדפיסה בדיוק את הפלט הרצוי; וגם ההרצה הוויזואלית (LineByLine) מגיעה לסוף
 *   2. הגשר: יצירה → REFRESH; "לפעולה של סעיף א" → OPEN קובץ ושורה של הפעולה; "הרצה ויזואלית" → RUN על הקובץ עם main; בלי token — נדחה
 *   3. החלונות במצב IntelliJ (כותרת, "בפרויקט ...", תמיד מעל), ו"צור תרגיל" מהחלון עצמו
 *   4. "סיימתי" על כל ה-27: רק קבצי התרגיל וה-class שלהם נמחקים, החבילות הריקות נמחקות, ההצעות נשכחות
 *   5. קובץ של התלמיד בחבילה (וה-class שלו) נשאר עם החבילה; שני עותקים; נתיב עם עברית ורווחים; קובץ התקדמות פגום
 *   java -cp dsviz.jar:dsviz-bank-vs.jar:<זה> il.lamerhav.dsviz.BankIjAll <תיקיית עבודה> <dsviz.jar>
 */
public final class BankIjAll {
    static int fails, oks;
    static final List<String> lines = new ArrayList<String>();
    static final List<String> bridged = Collections.synchronizedList(new ArrayList<String>());
    /** הגשר המדומה: האם "IntelliJ" מציג עכשיו חלון דו-שיח */
    static volatile boolean ideDialog;
    static File work, lib, out, src;

    public static void main(String[] a) throws Exception {
        work = new File(a[0]);
        lib = new File(a[1]).getAbsoluteFile();
        BankVs.delete(work);
        work.mkdirs();
        try {
            run();
        } catch (Throwable t) {
            fail("crash", "חריגה: " + t);
            t.printStackTrace();
        }
        log("SUMMARY " + oks + " ok / " + fails + " FAIL");
        Files.write(new File(work, "bank-ij-result.txt").toPath(), String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        System.exit(fails == 0 ? 0 : 1);
    }

    static void run() throws Exception {
        File project = new File(work, "Students");
        src = new File(project, "src");
        out = new File(project, "out/production/Students");
        src.mkdirs();
        out.mkdirs();
        // גשר מדומה (כמו התוסף): token נכון → "OK", אחרת "DENIED"
        final ServerSocket ss = new ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress());
        Thread srv = new Thread(() -> {
            while (!ss.isClosed()) {
                try (Socket so = ss.accept()) {
                    String l = new BufferedReader(new InputStreamReader(so.getInputStream(), StandardCharsets.UTF_8)).readLine();
                    boolean ok = l != null && l.startsWith("tok123\t");
                    boolean dialog = ok && l.equals("tok123\tDIALOG");
                    if (ok && !dialog) bridged.add(l.substring(7));
                    Writer w = new OutputStreamWriter(so.getOutputStream(), StandardCharsets.UTF_8);
                    w.write(!ok ? "DENIED\n" : dialog ? (ideDialog ? "1\n" : "0\n") : "OK\n");
                    w.flush();
                } catch (Exception e) {
                    // נסגר
                }
            }
        });
        srv.setDaemon(true);
        srv.start();
        BankVs.ij = true;
        BankVs.lib = lib;
        BankVs.javaExe = new File(System.getProperty("java.home"), "bin/java").getPath();
        BankVs.solutionsFile = new File(work, "solutions.json");
        BankVs.projectName = "Students";
        BankVs.port = ss.getLocalPort();
        BankVs.token = "tok123";
        BankVs.outDirs.add(out);
        System.setProperty("dsviz.bankProgress", new File(work, "progress.json").getPath());
        BankVs.init(src.toPath());
        check("load", BankVs.store.questions.size() >= 54 && BankVs.store.questions.get(0).exercise.get("language").equals("java"),
                "המאגר נטען בגרסת Java: " + BankVs.store.questions.size() + " שאלות", "");
        BankStore.Question sq = find("stack-sum-size");
        check("java-text", sq.sections.get(0).text.contains("sumStack") && !sq.sections.get(0).text.contains("SumStack"), "בנוסח: שמות של Java (sumStack)", "");

        // 1. כל השאלות
        final int total = BankVs.store.questions.size();
        List<File> dirs = new ArrayList<File>();
        for (BankStore.Question q : BankVs.store.questions) {
            File d = createOne(q);
            if (d != null) dirs.add(d);
        }
        check("all-count", dirs.size() == total, "נוצרו " + total + " תרגילים בתיקיית הקוד (חבילות ex01_ ... ex" + total + "_)", String.valueOf(dirs.size()));

        // 2. הגשר
        bridged.clear();
        BankVs.openSolution();
        BankStore.Question q1 = find("queue-sum-even");
        File d1 = BankVs.projects(q1).get(0);
        AiScaffold.Task t1 = AiScaffold.read(q1.exercise).tasks.get(0);
        BankVsFloat.jump(null, q1, t1);
        String b = String.valueOf(bridged);
        File f1 = new File(d1, t1.className + ".java");
        int line = BankVs.methodLine(f1, t1.methodName);
        check("bridge", b.contains("REFRESH\t" + src.getAbsolutePath()) && b.contains("OPEN\t" + f1.getAbsolutePath() + "\t" + line) && line > 0 && !b.contains("RUN"),
                "הגשר ל-IntelliJ: רענון, ופתיחת " + f1.getName() + " בשורה " + line + " (" + t1.methodName + ") — ההרצה מהכפתור של IntelliJ עצמו", b);
        String tok = BankVs.token;
        BankVs.token = "wrong";
        String denied = BankVs.bridge("OPEN", f1.getPath(), "1");
        BankVs.token = tok;
        check("bridge-token", "DENIED".equals(denied), "בקשה בלי ה-token הנכון נדחית (תוכנה אחרת במחשב לא יכולה לשלוט ב-IntelliJ)", String.valueOf(denied));

        // 3. החלונות במצב IntelliJ
        BankVsWindow w = ui(() -> new BankVsWindow());
        String texts = ui(() -> BankVsIt.texts(w.frame));
        check("window-ij", w.frame.getTitle().contains("IntelliJ") && texts.contains("בפרויקט Students") && texts.contains("ל-IntelliJ") && !texts.contains("Visual Studio"),
                "חלון המאגר במצב IntelliJ: 'מאגר תרגילים — IntelliJ', 'בפרויקט Students', בלי Visual Studio", w.frame.getTitle());
        ui(() -> { w.frame.dispose(); return null; });
        BankStore.Question q2 = find("tree-count-sum");
        BankVs.complete(null, q2, false);   // מתחילים נקי: יוצרים מהחלון עצמו
        BankProgress.get().setDone(q2.id, false);
        bridged.clear();
        ui(() -> { BankVsWindow.open(null, q2.id); return null; });
        BankVsIt.pause(800);
        BankVsIt.click(BankVsWindow.current.frame, "צור תרגיל");
        long end = System.currentTimeMillis() + 120000;
        while (System.currentTimeMillis() < end && ui(() -> BankVsFloat.current == null || !BankVsFloat.current.isShowing())) BankVsIt.pause(300);
        BankVsIt.pause(800);
        String ft = ui(() -> BankVsFloat.current == null ? "" : BankVsIt.texts(BankVsFloat.current) + " | title=" + BankVsFloat.current.getTitle());
        check("ui-create", !BankVs.projects(q2).isEmpty() && ft.contains("שם התרגיל: ex") && !ft.contains("בפתרון") && !ft.contains("תמיד מעל") && !ft.contains("Ctrl+S")
                && ui(() -> BankVsFloat.current.isAlwaysOnTop() && !BankVsFloat.current.isUndecorated() && BankVsFloat.current.isResizable()),
                "'צור תרגיל' בחלון: החלון הצף נפתח — 'שם התרגיל: ex...', תמיד מעל (קבוע, בלי תיבת סימון), ועם כפתור מזעור", ft);
        check("float-no-run", !ft.contains("| הרצה ויזואלית |") && ft.contains("ומריצים בכפתור \"הרצה ויזואלית\" שבסרגל העליון"),
                "בחלון הצף אין כפתור הרצה — מריצים מהכפתור 'הרצה ויזואלית' שבסרגל של IntelliJ", ft);

        // חלון דו-שיח של IntelliJ (למשל שגיאות בהרצה) — החלון הצף יורד אחורה ולא מסתיר אותו, ואחר כך חוזר להיות מעל
        ideDialog = true;
        long ye = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < ye && ui(() -> BankVsFloat.current.isAlwaysOnTop())) BankVsIt.pause(100);
        boolean yielded = ui(() -> !BankVsFloat.current.isAlwaysOnTop());
        ideDialog = false;
        ye = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < ye && ui(() -> !BankVsFloat.current.isAlwaysOnTop())) BankVsIt.pause(100);
        boolean back = ui(() -> BankVsFloat.current.isAlwaysOnTop());
        check("float-yield", yielded && back, "כש-IntelliJ מציג חלון דו-שיח — החלון הצף יורד אחורה; כשנסגר — שוב 'תמיד מעל'", "yield=" + yielded + " back=" + back);

        // "סיימתי את התרגיל": האישור מופיע בתוך החלון הצף עצמו (בלי חלון נוסף שמכסה אותו); "ביטול" מחזיר לשאלה
        final javax.swing.JFrame fl = ui(() -> BankVsFloat.current);
        File d2 = BankVs.projects(q2).get(0);
        ui(() -> { BankVsIt.find(fl, BankVsFloat.DoneButton.class).click(); return null; });
        BankVsIt.pause(400);
        String ct = ui(() -> BankVsIt.texts(fl));
        boolean extra = ui(() -> { for (java.awt.Window x : java.awt.Window.getWindows()) if (x instanceof javax.swing.JDialog && x.isShowing()) return true; return false; });
        check("done-inline", ui(() -> BankVsFloat.current == fl && BankVsFloat.confirm != null) && !extra && ct.contains("סיום ומחיקה") && ct.contains("ביטול")
                && ct.contains("שיימחק") && ct.contains("רק מה שנוצר מהתרגיל נמחק") && !ct.contains("לפעולה של סעיף"),
                "'סיימתי את התרגיל': האישור בתוך החלון הצף (במקום השאלה), בלי חלון נוסף", ct);
        shot("ij-float-done-confirm.png");
        BankVsIt.click(fl, "ביטול");
        BankVsIt.pause(300);
        String bt = ui(() -> BankVsIt.texts(fl) + BankVsFloat.shown);
        check("done-cancel", ui(() -> BankVsFloat.confirm == null && fl.isShowing()) && bt.contains("לפעולה של סעיף") && bt.contains("DONE-BUTTON todo") && d2.isDirectory()
                && !BankProgress.get().done(q2.id), "'ביטול' — חוזרים לשאלה, ושום דבר לא נמחק", bt);
        ui(() -> { BankVsIt.find(fl, BankVsFloat.DoneButton.class).click(); return null; });
        BankVsIt.pause(300);
        BankVsIt.click(fl, "סיום ומחיקה");
        BankVsIt.pause(500);
        check("done-confirm", ui(() -> BankVsFloat.current == null) && !d2.exists() && BankProgress.get().done(q2.id),
                "'סיום ומחיקה' — החבילה נמחקה, השאלה סומנה, והחלון הצף נסגר", "dir=" + d2.exists());
        BankProgress.get().setDone(q2.id, false);
        check("ui-create-bridge", String.valueOf(bridged).contains("REFRESH") && String.valueOf(bridged).contains("OPEN\t"),
                "אחרי היצירה: IntelliJ מקבל רענון ופתיחה של הקובץ בסעיף א", String.valueOf(bridged));
        shot("ij-float.png");
        ui(() -> { if (BankVsFloat.current != null) BankVsFloat.current.dispose(); return null; });

        // 4. קובץ של התלמיד בחבילה + ה-class שלו
        BankStore.Question qk = find("stack-max-contains");
        File dk = BankVs.projects(qk).get(0);
        File mine = new File(dk, "MyHelper.java");
        Files.write(mine.toPath(), ("package " + dk.getName() + ";\npublic class MyHelper { public static int twice(int x) { return 2 * x; } }\n").getBytes(StandardCharsets.UTF_8));
        File mineClass = new File(out, dk.getName() + "/MyHelper.class");
        Files.write(mineClass.toPath(), new byte[] { 1, 2, 3 });
        BankVs.complete(null, qk, false);
        File[] left = dk.listFiles();
        check("keep-mine", mine.isFile() && mineClass.isFile() && left != null && left.length == 1 && new File(out, dk.getName()).list().length == 1,
                "קובץ של התלמיד בחבילה (MyHelper.java וה-class שלו) נשאר; קבצי התרגיל וה-class שלהם נמחקו", java.util.Arrays.toString(left));

        // 5. "סיימתי" על כל השאר
        int done = 0;
        for (BankStore.Question q : BankVs.store.questions) {
            BankVs.complete(null, q, false);
            if (BankProgress.get().done(q.id)) done++;
        }
        int leftDirs = 0, leftClasses = 0;
        StringBuilder why = new StringBuilder();
        for (File d : dirs) {
            if (d.equals(dk)) continue;
            if (d.exists()) { leftDirs++; why.append(d.getName()).append(BankVs.list(d)); }
            File od = new File(out, d.getName());
            if (od.exists()) { leftClasses++; why.append(" out:").append(od.getName()).append(BankVs.list(od)); }
        }
        int sols = new SolutionStore(BankVs.solutionsFile).all().size();
        check("finish-all", done == total && leftDirs == 0 && leftClasses == 0 && sols == 0,
                "'סיימתי' על כל התרגילים: " + done + " סומנו, החבילות וה-class שלהן נמחקו, ואין יותר הצעות שמורות", why + " solutions=" + sols);
        check("finish-mine-still", mine.isFile(), "וגם אחרי כל זה — הקובץ של התלמיד עדיין שם", "");
        check("finish-refresh", String.valueOf(bridged).contains("REFRESH"), "אחרי המחיקה IntelliJ מקבל רענון (הלשוניות של הקבצים שנמחקו נסגרות)", "");

        // 6. שני עותקים
        BankStore.Question qc = find("list-min-count");
        BankProgress.get().setDone(qc.id, false);
        ExercisePipeline.Result r1 = BankVs.create(qc, QUIET), r2 = BankVs.create(qc, QUIET);
        check("copies", !r1.folderName.equals(r2.folderName) && BankVs.projects(qc).size() == 2, "שני עותקים: " + r1.folderName + " ו-" + r2.folderName, "");
        BankVs.complete(null, qc, false);
        check("copies-done", BankVs.projects(qc).isEmpty() && !new File(src, r1.folderName).exists() && !new File(src, r2.folderName).exists(),
                "'סיימתי' מוחק את שני העותקים", "");

        // 7. קובץ התקדמות פגום
        File pf = new File(System.getProperty("dsviz.bankProgress"));
        Files.write(pf.toPath(), "{ שבור".getBytes(StandardCharsets.UTF_8));
        pf.setLastModified(System.currentTimeMillis() + 5000);
        boolean cOk;
        try {
            cOk = BankProgress.get().doneCount(BankVs.store.questions) == 0;
            BankProgress.get().setDone("loops-sum-factorial", true);
            cOk &= BankProgress.get().done("loops-sum-factorial");
        } catch (RuntimeException e) {
            cOk = false;
        }
        check("corrupt-progress", cOk, "קובץ התקדמות פגום: לא נופלים, מתחילים מחדש", "");

        // 8. נתיב עם עברית ורווחים
        Path saved = BankVs.root;
        File heb = new File(work, "פרויקטים של דני/תרגול עם רווחים/src");
        heb.mkdirs();
        BankVs.root = heb.toPath();
        boolean hOk = true;
        for (String id : new String[] { "classes-rectangle", "recursion-digits", "strings-palindrome-vowels" }) {
            BankStore.Question q = find(id);
            BankProgress.get().setDone(id, false);
            File d = createOne(q);
            BankVs.complete(null, q, false);
            hOk &= d != null && !d.exists();
        }
        check("hebrew-path", hOk, "בנתיב עם עברית ורווחים: יצירה, הרצה ו'סיימתי' (3 שאלות)", heb.getPath());
        BankVs.root = saved;
        ss.close();
    }

    static final ExercisePipeline.Progress QUIET = new ExercisePipeline.Progress() {
        public void say(String text) { }

        public boolean cancelled() { return false; }
    };

    /** יוצר, בודק, פותר, מקמפל ומריץ שאלה אחת */
    static File createOne(BankStore.Question q) throws Exception {
        ExercisePipeline.Result r = BankVs.create(q, QUIET);
        File dir = new File(BankVs.root.toFile(), r.folderName);
        int tasks = AiScaffold.read(q.exercise).tasks.size();
        boolean marks = false;
        for (File f : r.files) marks |= new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).matches("(?s).*[‪-‮].*");
        boolean ok = dir.isDirectory() && r.errors.isEmpty() && !marks && r.files.size() >= 1 && BankVs.solutionsUnder(dir) == tasks;
        check("create-" + q.id, ok, q.id + " → " + r.folderName + " (" + r.files.size() + " קבצים, " + tasks + " סעיפים)", "errors=" + r.errors + " marks=" + marks);
        int applied = solve(dir);
        // javac לתיקיית הפלט (כמו IntelliJ), והרצה רגילה
        List<String> cmd = new ArrayList<String>(java.util.Arrays.asList(new File(System.getProperty("java.home"), "bin/javac").getPath(), "-encoding", "UTF-8",
                "-nowarn", "-cp", lib.getPath(), "-d", out.getPath()));
        for (File f : dir.listFiles((d, n) -> n.endsWith(".java"))) cmd.add(f.getPath());
        String[] c = ExercisePipeline.exec(cmd, dir, 120);
        String main = null;
        for (File f : dir.listFiles((d, n) -> n.endsWith(".java")))
            if (new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).contains("static void main(")) main = dir.getName() + "." + f.getName().replace(".java", "");
        String[] run = ExercisePipeline.exec(java.util.Arrays.asList(BankVs.javaExe, "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.getPath() + File.pathSeparator + lib.getPath(), String.valueOf(main)), dir, 60);
        String output = run == null ? "" : SolutionCheck.clean(run[0]);
        boolean same = c != null && "0".equals(c[2]) && run != null && SolutionCheck.same(SolutionCheck.clean(q.expectedOutput()), output);
        // ההרצה הוויזואלית (LineByLine — מה ש"הרצה ויזואלית" מריצה), בלי מסך
        String[] vis = ExercisePipeline.exec(java.util.Arrays.asList(BankVs.javaExe, "-Djava.awt.headless=true", "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8", "-Ddsviz.visual=true", "-cp", out.getPath() + File.pathSeparator + lib.getPath(), "unit4.collectionsLib.LineByLine", String.valueOf(main)), dir, 120);
        boolean visOk = vis != null && vis[0].contains("התוכנית הסתיימה");
        check("run-" + q.id, applied == tasks && same && visOk, q.id + ": הפתרון בכל " + applied + " הסעיפים — javac, הפלט בדיוק כמו בשאלה, וההרצה הוויזואלית מגיעה לסוף",
                "javac=" + (c == null ? "-" : c[1].trim()) + " out=" + output.replace("\n", " | ") + " vis=" + visOk);
        return dir;
    }

    /** "התלמיד כותב את הפתרון": כל פעולה ריקה של סעיף מוחלפת בהצעה ששמורה בצד */
    static int solve(File dir) throws Exception {
        int applied = 0;
        String prefix = BankVs.slash(dir.getAbsolutePath()) + "/";
        for (SolutionStore.Entry e : new SolutionStore(BankVs.storeFile()).all()) {
            if (!BankVs.slash(e.path).startsWith(prefix)) continue;
            File f = new File(e.path);
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            int k = text.indexOf(" " + e.method + "(");
            if (k < 0) continue;
            int start = text.lastIndexOf('\n', k) + 1, open = text.indexOf('{', k), depth = 0, endI = -1;
            for (int i = open; i < text.length(); i++) {
                if (text.charAt(i) == '{') depth++;
                else if (text.charAt(i) == '}' && --depth == 0) { endI = i; break; }
            }
            if (endI < 0) continue;
            String indent = text.substring(start).replaceAll("(?s)^([ \t]*).*", "$1");
            StringBuilder code = new StringBuilder();
            for (String l : e.code.split("\n", -1)) code.append(code.length() == 0 ? "" : "\n").append(indent).append(l);
            text = text.substring(0, start) + code + text.substring(endI + 1);
            Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
            applied++;
        }
        return applied;
    }

    static BankStore.Question find(String id) {
        for (BankStore.Question q : BankVs.store.questions) if (q.id.equals(id)) return q;
        throw new IllegalArgumentException(id);
    }

    static void shot(String name) {
        javax.swing.JFrame f = BankVsFloat.current;
        if (f != null) BankVsIt.out = work;
        if (f != null) BankVsIt.shot(f, name.replace(".png", ""));
    }

    static <T> T ui(java.util.concurrent.Callable<T> c) throws Exception {
        return BankVsIt.ui(c);
    }

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
}

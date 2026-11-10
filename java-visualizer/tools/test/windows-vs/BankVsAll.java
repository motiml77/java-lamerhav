package il.lamerhav.dsviz;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * "מאגר תרגילים" ל-Visual Studio — בדיקה רחבה, בלי חלונות (כמו הכלי עצמו: BankVs.create / complete):
 *   create — כל 27 השאלות: נוצר פרויקט, רשום בפתרון, בלי סימוני כיוון, הצעה לכל סעיף; "התלמיד פותר" (ההצעות נכתבות במקום
 *            הפעולות הריקות) ו-dsviz-cs מריץ — הפלט בדיוק כמו בשאלה. ברשימה all-projects.txt: id|תיקייה|קובץ הפלט הרצוי
 *            (הסקריפט בונה אחר כך את כל הפתרון ב-MSBuild של Visual Studio ומריץ כל תרגיל).
 *   finish — "סיימתי" על כל ה-27: הכול נמחק והפתרון חוזר להיות כמו בהתחלה; ועוד מקרים: שני עותקים של אותה שאלה, תיקייה שנמחקה
 *            ידנית, ביטול הסימון, קובץ התקדמות פגום, תיקייה עם עברית ורווחים, חיפוש וסינון, שאלה אקראית.
 *   java -cp dsviz.jar;dsviz-bank-vs.jar;<זה> il.lamerhav.dsviz.BankVsAll create|finish <DataStructures-CS> <תיקיית פלט>
 */
public final class BankVsAll {
    static int fails, oks;
    static final List<String> lines = new ArrayList<String>();
    static File out;

    public static void main(String[] a) throws Exception {
        System.setProperty("dsviz.noOpen", "true");
        out = new File(a[2]);
        out.mkdirs();
        BankVs.init(Paths.get(a[1]));
        try {
            if (a[0].equals("create")) create();
            else finish();
        } catch (Throwable t) {
            fail("crash", "חריגה: " + t);
            t.printStackTrace();
        }
        log("SUMMARY " + oks + " ok / " + fails + " FAIL");
        Files.write(new File(out, "bank-all-" + a[0] + ".txt").toPath(), String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
        System.exit(fails == 0 ? 0 : 1);
    }

    static final ExercisePipeline.Progress QUIET = new ExercisePipeline.Progress() {
        public void say(String text) { }

        public boolean cancelled() { return false; }
    };

    // ------------------------------------------------------------------ create

    static void create() throws Exception {
        String slnBefore = sln();
        Files.write(new File(out, "sln-before.txt").toPath(), slnBefore.getBytes(StandardCharsets.UTF_8));
        StringBuilder list = new StringBuilder();
        int n = 0;
        for (BankStore.Question q : BankVs.store.questions) {
            if (q.origin.contains("test-")) continue;
            n++;
            File dir = createOne(q);
            if (dir == null) continue;
            File exp = new File(out, "expected-" + q.id + ".txt");
            Files.write(exp.toPath(), q.expectedOutput().getBytes(StandardCharsets.UTF_8));
            list.append(q.id).append('|').append(dir.getAbsolutePath()).append('|').append(exp.getAbsolutePath()).append('\n');
        }
        Files.write(new File(out, "all-projects.txt").toPath(), list.toString().getBytes(StandardCharsets.UTF_8));
        check("all-count", n == total() && n >= 54, "נוצרו תרגילים מכל " + n + " השאלות", "");
    }

    /** כמה שאלות במאגר (בלי שאלות הבדיקה) */
    static int total() {
        int n = 0;
        for (BankStore.Question q : BankVs.store.questions) if (!q.origin.contains("test-")) n++;
        return n;
    }

    /** כמה שאלות ברמה הזו (בלי שאלות הבדיקה) */
    static int count(String level) {
        int n = 0;
        for (BankStore.Question q : BankVs.store.questions) if (!q.origin.contains("test-") && q.level.id.equals(level)) n++;
        return n;
    }

    /** יוצר, בודק, פותר ומריץ שאלה אחת. מחזיר את תיקיית הפרויקט */
    static File createOne(BankStore.Question q) throws Exception {
        String id = q.id;
        ExercisePipeline.Result r = BankVs.create(q, QUIET);
        File dir = CsSolution.exercisesDir(BankVs.root).resolve(r.folderName).toFile();
        int tasks = AiScaffold.read(q.exercise).tasks.size();
        boolean marks = false;
        for (File f : r.files) marks |= new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).matches("(?s).*[\u202A-\u202E].*");
        boolean ok = dir.isDirectory() && new File(dir, r.folderName + ".csproj").isFile() && sln().contains("\"" + r.folderName + "\"")
                && r.errors.isEmpty() && !marks && BankVs.solutionsUnder(dir) == 1 && BankVs.prepared(q) == 1 && BankVs.projects(q).contains(dir);
        check("create-" + id, ok, id + " → " + r.folderName + " (" + r.files.size() + " קבצים, " + tasks + " סעיפים, בפתרון, בלי סימוני כיוון)",
                "errors=" + r.errors + " marks=" + marks + " solutions=" + BankVs.solutionsUnder(dir));
        // התרגיל נוצר עם סעיף א בלבד; "הכן סעיף ב" (וכו') מוסיף כל סעיף — הפעולה, הבדיקות ב-main וההצעה לפתרון
        List<String> prepProblems = new ArrayList<String>();
        AiScaffold.Exercise allTasks = AiScaffold.read(q.exercise);
        for (int k = 1; k < allTasks.tasks.size(); k++) {
            String p = BankVs.prepare(q, allTasks.tasks.get(k));
            if (p != null) prepProblems.add(p);
            if (BankVs.prepared(q) != k + 1) prepProblems.add("prepared=" + BankVs.prepared(q) + " אחרי סעיף " + allTasks.tasks.get(k).label);
        }
        check("prepare-" + q.id, prepProblems.isEmpty() && BankVs.solutionsUnder(dir) == tasks,
                q.id + ": \"הכן סעיף\" הוסיף את כל " + (tasks - 1) + " הסעיפים הנוספים (פעולה, בדיקות ב-main, הצעה לפתרון)", String.join(" | ", prepProblems));
        int applied = solve(dir);
        long t0 = System.currentTimeMillis();
        String[] run = ExercisePipeline.exec(cmd(dir), dir, 120);
        long ms = System.currentTimeMillis() - t0;
        String output = run == null ? "" : SolutionCheck.clean(stripHeader(run[0]));
        boolean same = run != null && "0".equals(run[2]) && SolutionCheck.same(SolutionCheck.clean(q.expectedOutput()), output);
        String trace = "";
        if (!same) {   // לאבחון: קוד היציאה, הזמן, ותחילת קובץ ההרצה (null = לא הסתיים בזמן)
            File tf = new File(out, "trace-" + dir.getName() + ".txt");
            if (tf.isFile()) {
                String t = new String(Files.readAllBytes(tf.toPath()), StandardCharsets.UTF_8);
                trace = " trace(" + t.length() + ")=" + t.substring(0, Math.min(400, t.length())).replace("\n", " | ");
            }
        }
        check("run-" + id, applied == tasks && same, id + ": הפתרון בכל " + applied + " הסעיפים, וההרצה הוויזואלית מדפיסה בדיוק את הפלט הרצוי",
                "applied=" + applied + " code=" + (run == null ? "-" : run[2]) + " ms=" + ms + " out=" + output.replace("\n", " | ")
                        + (run == null ? "" : " err=" + run[1].trim()) + trace);
        return dir;
    }

    static List<String> cmd(File dir) throws Exception {
        List<String> c = new ArrayList<String>(VsSubmit.dsvizCs());
        c.add(dir.getAbsolutePath());
        c.add("--trace");
        c.add(new File(out, "trace-" + dir.getName() + ".txt").getAbsolutePath());   // מחוץ לתיקיית התרגיל (קובץ זר שם — "סיימתי" לא ימחק את הפרויקט)
        return c;
    }

    static String stripHeader(String text) {
        text = text.replace("\r", "");
        int nl = text.indexOf('\n');
        return text.startsWith("הרצה ויזואלית") && nl >= 0 ? text.substring(nl + 1) : text;
    }

    /** "התלמיד כותב את הפתרון": כל פעולה ריקה של סעיף מוחלפת בהצעה ששמורה בצד */
    static int solve(File dir) throws Exception {
        int applied = 0;
        String prefix = BankVs.slash(dir.getAbsolutePath()).toLowerCase() + "/";
        for (SolutionStore.Entry e : new SolutionStore(VsSubmit.storeFile()).all()) {
            if (!BankVs.slash(e.path).toLowerCase().startsWith(prefix)) continue;
            File f = new File(e.path);
            String text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            int k = text.indexOf(" " + e.method + "(");
            if (k < 0) continue;
            int start = text.lastIndexOf('\n', k) + 1, open = text.indexOf('{', k), depth = 0, end = -1;
            for (int c = open; c < text.length(); c++) {
                if (text.charAt(c) == '{') depth++;
                else if (text.charAt(c) == '}' && --depth == 0) { end = c; break; }
            }
            if (end < 0) continue;
            String indent = text.substring(start).replaceAll("(?s)^([ \t]*).*", "$1");
            String nl = text.contains("\r\n") ? "\r\n" : "\n";
            StringBuilder code = new StringBuilder();
            for (String l : e.code.split("\n", -1)) code.append(code.length() == 0 ? "" : nl).append(indent).append(l.replace("\r", ""));
            text = text.substring(0, start) + code + text.substring(end + 1);
            Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
            applied++;
        }
        return applied;
    }

    // ------------------------------------------------------------------ finish

    static void finish() throws Exception {
        // 1. "סיימתי" על כל ה-27
        int done = 0;
        List<File> dirs = new ArrayList<File>();
        for (BankStore.Question q : BankVs.store.questions) {
            if (q.origin.contains("test-")) continue;
            dirs.addAll(BankVs.projects(q));
            BankVs.complete(null, q, false);
            if (BankProgress.get().done(q.id)) done++;
        }
        BankVs.cleanPending();
        int left = 0;
        StringBuilder leftNames = new StringBuilder();
        for (File d : dirs) {
            if (!d.exists()) continue;
            boolean onlyBuild = true;
            for (String p : BankVs.list(d)) onlyBuild &= p.equals("obj") || p.equals("bin") || p.startsWith("obj" + File.separator) || p.startsWith("bin" + File.separator);
            if (!onlyBuild) { left++; leftNames.append(d.getName()).append(BankVs.list(d)).append(' '); }
        }
        String before = new String(Files.readAllBytes(new File(out, "sln-before.txt").toPath()), StandardCharsets.UTF_8);
        check("finish-all", done == total() && left == 0 && dirs.size() == total(), "'סיימתי' על כל " + dirs.size() + " התרגילים: " + done + " סומנו, ולא נשאר מהם שום קובץ", leftNames.toString());
        check("finish-sln", projectsIn(sln()).equals(projectsIn(before)), "הפתרון חזר להיות בדיוק כמו לפני היצירה (" + projectsIn(before).size() + " פרויקטים)",
                projectsIn(sln()).toString());
        int sols = 0;
        for (File d : dirs) sols += BankVs.solutionsUnder(d);
        check("finish-solutions", sols == 0, "אין יותר הצעות לפתרון שמורות לתרגילים שנמחקו", String.valueOf(sols));

        // 2. שני עותקים של אותה שאלה ("צור עותק חדש")
        BankStore.Question q = find("queue-sum-even");
        BankProgress.get().setDone(q.id, false);
        ExercisePipeline.Result r1 = BankVs.create(q, QUIET), r2 = BankVs.create(q, QUIET);
        check("copies-create", !r1.folderName.equals(r2.folderName) && BankVs.projects(q).size() == 2, "שני עותקים: " + r1.folderName + " ו-" + r2.folderName, "");
        BankVs.complete(null, q, false);
        check("copies-done", BankVs.projects(q).isEmpty() && !sln().contains("\"" + r1.folderName + "\"") && !sln().contains("\"" + r2.folderName + "\""),
                "'סיימתי' מוחק את שני העותקים, ומוציא את שניהם מהפתרון", "");

        // 3. התלמיד מחק ידנית את תיקיית הפרויקט — "סיימתי" מוציא אותו גם מהפתרון
        BankStore.Question q3 = find("list-length-sum");
        ExercisePipeline.Result r3 = BankVs.create(q3, QUIET);
        File d3 = CsSolution.exercisesDir(BankVs.root).resolve(r3.folderName).toFile();
        BankVs.delete(d3);
        BankVs.complete(null, q3, false);
        check("manual-delete", !sln().contains("\"" + r3.folderName + "\"") && BankProgress.get().done(q3.id),
                "תיקייה שנמחקה ידנית: 'סיימתי' מסמן, ומוציא את " + r3.folderName + " מהפתרון (בלי פרויקט שבור)", "");

        // 4. ביטול הסימון
        BankProgress.get().setDone(q3.id, false);
        check("undo-mark", !BankProgress.get().done(q3.id) && BankProgress.get().doneCount(BankVs.store.questions) == total() - 1,
                "לחיצה על 'עשיתי את התרגיל' מבטלת את הסימון", String.valueOf(BankProgress.get().doneCount(BankVs.store.questions)));

        // 5. חיפוש וסינון (החלון עצמו, בלי להציג)
        BankVsWindow w = ui(() -> new BankVsWindow());
        ui(() -> { w.search = "עץ"; w.levelFilter = ""; w.rebuildTree(); return null; });
        String t1 = String.valueOf(w.treeShown);
        ui(() -> { w.search = "xyz-אין-כזה"; w.rebuildTree(); return null; });
        String t2 = String.valueOf(w.treeShown);
        ui(() -> { w.search = ""; w.levelFilter = "easy"; w.open.addAll(java.util.Arrays.asList("loops", "arrays", "strings", "classes", "recursion", "stack", "queue", "list", "tree")); w.rebuildTree(); return null; });
        int easy = 0;
        for (String s : w.treeShown) if (s.startsWith("Q ")) easy++;
        ui(() -> { w.levelFilter = "hard"; w.rebuildTree(); return null; });
        String t3 = String.valueOf(w.treeShown);
        check("search", t1.contains("Q tree-") && !t1.contains("Q stack-") && t2.equals("[EMPTY]") && easy == count("easy") && easy >= 27 && t3.equals("[EMPTY]"),
                "חיפוש 'עץ' — רק עץ בינארי; חיפוש בלי תוצאות — 'לא נמצאו שאלות'; 'קל' — " + easy + "; 'קשה' — אין עדיין", t1 + " / " + t2 + " / " + easy + " / " + t3);

        // 6. שאלה אקראית: לא מחזירה שאלה שכבר סומנה
        for (BankStore.Question x : BankVs.store.questions) BankProgress.get().setDone(x.id, !x.id.equals("strings-palindrome-vowels"));
        boolean onlyOpen = true;
        for (int i = 0; i < 15; i++) {
            ui(() -> { w.showQuestion(find("loops-sum-factorial")); w.selected = find("loops-sum-factorial"); return null; });
            ui(() -> { for (java.awt.Component c : w.buttons.getComponents())
                if (c instanceof javax.swing.JButton && ((javax.swing.JButton) c).getText().equals("שאלה אקראית")) ((javax.swing.JButton) c).doClick();
                return null; });
            onlyOpen &= w.selected.id.equals("strings-palindrome-vowels");
        }
        check("random", onlyOpen, "'שאלה אקראית' בוחרת רק שאלה שעוד לא נעשתה (15 פעמים)", w.selected.id);
        ui(() -> { w.frame.dispose(); return null; });

        // 7. קובץ התקדמות פגום — לא נופלים, מתחילים מחדש
        File pf = new File(System.getProperty("dsviz.bankProgress"));
        Files.write(pf.toPath(), "{ זה לא JSON".getBytes(StandardCharsets.UTF_8));
        pf.setLastModified(System.currentTimeMillis() + 5000);
        boolean corruptOk;
        try {
            corruptOk = BankProgress.get().doneCount(BankVs.store.questions) == 0;
            BankProgress.get().setDone("stack-sum-size", true);
            corruptOk &= BankProgress.get().done("stack-sum-size");
        } catch (RuntimeException e) {
            corruptOk = false;
        }
        check("corrupt-progress", corruptOk, "קובץ התקדמות פגום: הכלי לא נופל, מתחיל מחדש, ושומר שוב", "");

        // 8. תיקיית תרגילים בנתיב עם עברית ורווחים (שם משתמש / תיקיית מסמכים כזו)
        Path heb = Paths.get(out.getAbsolutePath(), "מסמכים של דני", "תיקייה עם רווחים", "DataStructures-CS");
        copy(BankVs.root, heb);
        Path saved = BankVs.root;
        BankVs.root = heb;
        boolean hebOk = true;
        for (String id : new String[] { "classes-time", "recursion-array", "tree-leaves-max" }) {
            File d = createOne(find(id));
            BankStore.Question hq = find(id);
            BankVs.complete(null, hq, false);
            BankVs.cleanPending();
            hebOk &= d != null && (!d.exists() || BankVs.list(d).isEmpty() || BankVs.list(d).get(0).startsWith("obj"));
        }
        check("hebrew-path", hebOk, "בנתיב עם עברית ורווחים: יצירה, הרצה ו'סיימתי' עובדים (3 שאלות)", heb.toString());
        BankVs.root = saved;
    }

    // ------------------------------------------------------------------ עזרים

    static BankStore.Question find(String id) {
        for (BankStore.Question q : BankVs.store.questions) if (q.id.equals(id)) return q;
        throw new IllegalArgumentException(id);
    }

    static String sln() throws Exception {
        return new String(Files.readAllBytes(CsSolution.solution(BankVs.root)), StandardCharsets.UTF_8);
    }

    static java.util.TreeSet<String> projectsIn(String sln) {
        java.util.TreeSet<String> s = new java.util.TreeSet<String>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^Project\\(\"[^\"]*\"\\)\\s*=\\s*\"([^\"]+)\"").matcher(sln);
        while (m.find()) s.add(m.group(1));
        return s;
    }

    static void copy(Path from, Path to) throws Exception {
        try (java.util.stream.Stream<Path> s = Files.walk(from)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path rel = from.relativize(p);
                if (rel.toString().startsWith("Exercises" + File.separator + "Ex") || rel.toString().contains(File.separator + "obj") || rel.toString().contains(File.separator + "bin")) continue;
                Path t = to.resolve(rel.toString());
                if (Files.isDirectory(p)) Files.createDirectories(t);
                else Files.copy(p, t, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    static <T> T ui(java.util.concurrent.Callable<T> c) throws Exception {
        final java.util.concurrent.atomic.AtomicReference<T> r = new java.util.concurrent.atomic.AtomicReference<T>();
        final java.util.concurrent.atomic.AtomicReference<Exception> e = new java.util.concurrent.atomic.AtomicReference<Exception>();
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            try { r.set(c.call()); } catch (Exception x) { e.set(x); }
        });
        if (e.get() != null) throw e.get();
        return r.get();
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

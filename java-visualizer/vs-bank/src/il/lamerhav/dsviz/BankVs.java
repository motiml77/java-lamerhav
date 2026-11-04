package il.lamerhav.dsviz;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import unit4.collectionsLib.Theme;

/**
 * "מאגר תרגילים" ל-Visual Studio (C#) — לבדיקה בלבד (לא בהתקנה לתלמידים): jar נפרד שרץ ליד dsviz.jar של ההתקנה.
 *   javaw -cp "dsviz.jar;dsviz-bank-vs.jar" il.lamerhav.dsviz.BankVs bank|question <תיקיית DataStructures-CS>
 * bank — חלון המאגר (BankVsWindow): נושאים, שאלות לפי קושי, ובחירה יוצרת פרויקט Exercises\Ex01_... בפתרון, עם פעולה לכל סעיף,
 *        Main שמריץ ומדפיס, והצעה לפתרון לכל סעיף (אותו מנוע כמו "שליחת תרגיל ב-C#", בלי Gemini: ExercisePipeline.csharp).
 * question — החלון הצף "השאלה" (BankVsFloat) של התרגיל האחרון שנוצר.
 * "סיימתי את התרגיל" (בחלון הצף): סימון ירוק, והפרויקט נמחק עם כל המעטפת שלו — התיקייה (כולל bin / obj), הרישום בפתרון (.sln),
 * ההצעות לפתרון השמורות, והשיוך לשאלה.
 * ההתקדמות נשמרת ב-~/.dsviz/bank-progress-cs.json (נפרד מ-Eclipse).
 */
public final class BankVs {
    private BankVs() {}

    static final String TITLE = "מאגר תרגילים — C#";
    static Path root;
    static BankStore store;
    /** בדיקות: מה היה נפתח ב-Visual Studio (עם -Ddsviz.noOpen=true לא נפתח כלום) */
    static final List<String> opened = new ArrayList<String>();

    public static void main(final String[] a) throws Exception {
        if (a.length < 2) {
            System.err.println("שימוש: BankVs bank|question <תיקיית DataStructures-CS>");
            System.exit(2);
        }
        init(Paths.get(a[1]));
        SwingUtilities.invokeAndWait(() -> {
            if (a[0].equals("question")) BankVsFloat.showLast(null);
            else BankVsWindow.open(null, null);
        });
    }

    /** הכנה (גם לבדיקות): ההתקדמות של C#, העיצוב, והמאגר */
    static void init(Path r) throws Exception {
        if (System.getProperty("dsviz.bankProgress") == null)
            System.setProperty("dsviz.bankProgress", Paths.get(System.getProperty("user.home"), ".dsviz", "bank-progress-cs.json").toString());
        root = r.toAbsolutePath();
        cleanPending();
        SwingUtilities.invokeAndWait(() -> {
            Theme.install();
            compactFonts();
            store = BankStore.load(true);
        });
    }

    /** גופנים כמו בשאר החלונות של C# (CsTool) */
    static void compactFonts() {
        Theme.ui = Theme.ui.deriveFont(java.awt.Font.PLAIN, 13f);
        Theme.uiBold = Theme.uiBold.deriveFont(java.awt.Font.BOLD, 13f);
        Theme.small = Theme.small.deriveFont(java.awt.Font.PLAIN, 11.5f);
        Theme.big = Theme.big.deriveFont(java.awt.Font.BOLD, 17f);
        if (Theme.mono != null) Theme.mono = Theme.mono.deriveFont(java.awt.Font.PLAIN, 12.5f);
        javax.swing.plaf.FontUIResource ui = new javax.swing.plaf.FontUIResource(Theme.ui);
        UIManager.put("defaultFont", ui);
        for (String k : new String[] { "Label.font", "Button.font", "ToggleButton.font", "CheckBox.font", "TextField.font", "ComboBox.font",
                "List.font", "OptionPane.messageFont", "OptionPane.buttonFont" })
            UIManager.put(k, ui);
        UIManager.put("ToolTip.font", new javax.swing.plaf.FontUIResource(Theme.small));
    }

    static String topicTitle(String id) {
        for (BankStore.Topic t : store.topics) if (t.id.equals(id)) return t.title;
        return id;
    }

    // ------------------------------------------------------------------ יצירת התרגיל

    /** יוצר את הפרויקט מהשאלה (בלי Gemini), שומר את ההצעות לפתרון בצד, ורושם אותו כשייך לשאלה */
    static ExercisePipeline.Result create(BankStore.Question q, ExercisePipeline.Progress p) throws Exception {
        Map<String, Object> raw = Json.obj(Json.parse(Json.write(q.exercise)));   // עותק: המנוע משנה את המפה
        ExercisePipeline.Result r = VsSubmit.build(null, raw, new ArrayList<String>(), root, VsSubmit.dsvizCs(), p, VsSubmit.takenNames(root));
        if (r.files.isEmpty()) throw new IOException(r.writeError != null ? r.writeError : "הקבצים לא נכתבו");
        for (File f : r.files) stripMarks(f);
        for (SolutionStore.Entry e : r.entries) e.code = e.code.replaceAll("[\\u202A-\\u202E\\u2066-\\u2069]", "");
        if (!r.entries.isEmpty()) new SolutionStore(VsSubmit.storeFile()).add(r.entries);
        BankProgress.Created c = new BankProgress.Created();
        c.project = root.getFileName().toString();
        c.folder = slash(CsSolution.exercisesDir(root).resolve(r.folderName).toString());
        c.focus = r.focus == null ? "" : slash(r.focus.getPath());
        for (File f : r.files) c.files.add(slash(f.getPath()));
        BankProgress.get().created(q.id, c);
        return r;
    }

    /**
     * Visual Studio מציג את סימוני הכיוון הבלתי נראים (RLE / PDF שבהערות בעברית) כתוויות "u+202B" בתוך הקוד — מוחקים אותם מהקבצים
     * שנוצרו (בעורך של Visual Studio ההערות בעברית מוצגות טוב גם בלעדיהם)
     */
    static void stripMarks(File f) {
        try {
            String t = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            String c = t.replaceAll("[\u202A-\u202E\u2066-\u2069]", "");
            if (!c.equals(t)) Files.write(f.toPath(), c.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // נשאר כמו שהוא
        }
    }

    static String slash(String p) {
        return p.replace('\\', '/');
    }

    /** הפרויקטים של השאלה שעוד קיימים (החדש בסוף) */
    static List<File> projects(BankStore.Question q) {
        List<File> out = new ArrayList<File>();
        for (BankProgress.Created c : BankProgress.get().createdOf(q.id)) {
            File d = new File(c.folder);
            if (d.isDirectory() && !out.contains(d)) out.add(d);
        }
        return out;
    }

    // ------------------------------------------------------------------ "סיימתי"

    /** מה נמחק מתרגיל אחד: רק מה שנוצר ממנו — ולא שום קובץ אחר של התלמיד */
    static final class Removal {
        final File dir;
        /** קבצי ה-.cs שנוצרו מהתרגיל (רק ישירות בתיקיית הפרויקט) */
        final List<File> files = new ArrayList<File>();
        /** קבצים / תיקיות אחרים בתיקייה שלא נוצרו מהתרגיל — אם יש, הפרויקט נשאר (רק קבצי התרגיל נמחקים) */
        final List<String> others = new ArrayList<String>();
        /** הפרויקט בתוך Exercises של תיקיית התרגילים (אחרת — לא מוחקים תיקייה בכלל) */
        boolean inExercises;

        Removal(File dir) {
            this.dir = dir;
        }

        /** אין בתיקייה שום דבר של התלמיד — מוחקים את הפרויקט כולו (תיקייה + רישום בפתרון) */
        boolean wholeProject() {
            return inExercises && others.isEmpty();
        }
    }

    /** מה נוצר מהתרגיל בתיקייה, ומה עוד יש בה. מה שנוצר: קבצי ה-.cs שלו, ‎<שם>.csproj(.user)‎, Properties\launchSettings.json, bin, obj */
    static Removal plan(BankProgress.Created c) {
        File dir = new File(c.folder);
        Removal r = new Removal(dir);
        try {
            File ex = CsSolution.exercisesDir(root).toFile().getCanonicalFile();
            r.inExercises = dir.getCanonicalFile().getParentFile().equals(ex);
        } catch (IOException e) {
            r.inExercises = false;
        }
        java.util.Set<String> made = new java.util.HashSet<String>();
        for (String p : c.files) {
            File f = new File(p);
            if (f.isFile() && f.getParentFile() != null && f.getParentFile().getAbsoluteFile().equals(dir.getAbsoluteFile())) {
                r.files.add(f);
                made.add(f.getName().toLowerCase());
            }
        }
        String[] names = dir.list();
        if (names != null) {
            for (String n : names) {
                String l = n.toLowerCase();
                if (made.contains(l) || l.equals(dir.getName().toLowerCase() + ".csproj") || l.equals(dir.getName().toLowerCase() + ".csproj.user")
                        || l.equals("bin") || l.equals("obj")) continue;
                if (l.equals("properties")) {
                    String[] p = new File(dir, n).list();
                    boolean onlyLaunch = p != null && (p.length == 0 || p.length == 1 && p[0].equalsIgnoreCase("launchSettings.json"));
                    if (onlyLaunch) continue;
                }
                r.others.add(n);
            }
        }
        return r;
    }

    /**
     * סימון ירוק, ומחיקה של מה שנוצר מהשאלה — ורק של זה: אם בתיקיית הפרויקט אין שום דבר אחר, הפרויקט נמחק כולו (התיקייה והרישום
     * בפתרון); אם התלמיד הוסיף שם קבצים משלו — נמחקים רק קבצי התרגיל, והפרויקט עם הקבצים שלו נשאר. גם ההצעות לפתרון השמורות
     * והשיוך לשאלה נמחקים. ask — שואלים קודם (בחלון בעברית, עם הרשימה). מחזיר false אם התלמיד ביטל.
     */
    static boolean complete(Component parent, BankStore.Question q, boolean ask) {
        List<Removal> plans = new ArrayList<Removal>();
        for (BankProgress.Created c : BankProgress.get().createdOf(q.id)) {
            Removal r = plan(c);
            if (!r.files.isEmpty() || r.dir.isDirectory() && r.wholeProject()) plans.add(r);
        }
        StringBuilder what = new StringBuilder();
        for (Removal r : plans) {
            List<String> n = new ArrayList<String>();
            for (File f : r.files) n.add(f.getName());
            what.append(what.length() == 0 ? "" : "\n");
            if (r.wholeProject()) what.append("הפרויקט שיימחק מהפתרון: ").append(r.dir.getName()).append(n.isEmpty() ? "" : " (" + String.join(", ", n) + ")");
            else what.append("קבצי התרגיל שיימחקו: ").append(String.join(", ", n)).append("\nהפרויקט ").append(r.dir.getName())
                    .append(" נשאר — יש בו עוד קבצים שלך: ").append(String.join(", ", r.others));
        }
        if (ask && !plans.isEmpty() && RtlDialog.ask(parent, "סיימתי את התרגיל", RtlDialog.Kind.OK, "סיימתי את התרגיל",
                "השאלה תסומן בסימן ירוק, וקבצי התרגיל יימחקו — כדי שלא יצטברו תרגילים ישנים.\n\n"
                + "אפשר תמיד לפתוח את השאלה שוב מ\"מאגר תרגילים\" ולהתחיל מחדש.\n\n"
                + what + "\nרק מה שנוצר מהתרגיל נמחק — קבצים אחרים לא נמחקים.", "סיום ומחיקה", "ביטול") != 0) return false;
        BankProgress.get().setDone(q.id, true);
        List<String> left = new ArrayList<String>();
        for (Removal r : plans) left.addAll(remove(r));
        BankProgress.get().forgetCreated(q.id);
        if (!left.isEmpty() && parent != null)
            RtlDialog.show(parent, "סיימתי את התרגיל", RtlDialog.Kind.WARN, "חלק מהקבצים לא נמחקו",
                    String.join("\n", left) + "\n\nכנראה שהתוכנית עדיין רצה, או שקובץ פתוח בתוכנה אחרת. אפשר למחוק אותם ידנית.");
        return true;
    }

    /** מוחק לפי התוכנית. מחזיר מה שלא הצליח להימחק */
    static List<String> remove(Removal r) {
        List<String> left = new ArrayList<String>();
        forgetSolutions(r.files);
        for (File f : r.files) {
            for (int i = 0; i < 6 && f.exists(); i++) {
                f.delete();
                if (f.exists()) sleep(500);
            }
            if (f.exists()) left.add(f.getPath());
        }
        if (!r.wholeProject() || !r.dir.isDirectory()) return left;
        Path sln = CsSolution.solution(root);
        try {
            if (sln != null) unregister(sln, r.dir.getName());
        } catch (IOException e) {
            // הפתרון לא עודכן — Visual Studio יראה פרויקט חסר
        }
        // רק מה שהתוכנית מצאה: ‎.csproj(.user)‎, Properties\launchSettings.json, bin, obj — ואז התיקייה (ריקה)
        for (String n : new String[] { r.dir.getName() + ".csproj", r.dir.getName() + ".csproj.user", "Properties" + File.separator + "launchSettings.json" })
            new File(r.dir, n).delete();
        new File(r.dir, "Properties").delete();
        for (String n : new String[] { "bin", "obj" }) {
            File b = new File(r.dir, n);
            for (int i = 0; i < 6 && b.exists(); i++) {
                delete(b);
                if (b.exists()) sleep(500);   // קובץ שנעול לרגע (הבנייה של Visual Studio) — מנסים שוב
            }
        }
        if (!r.dir.delete() && r.dir.exists()) {
            // נשארו רק קבצי בנייה (bin / obj) ש-Visual Studio עוד מחזיק — נמחקים בפעם הבאה שהכלי נפתח (pending), ולא נוגעים בשום דבר אחר
            List<String> rest = list(r.dir);
            boolean onlyBuild = true;
            for (String p : rest) onlyBuild &= p.startsWith("bin" + File.separator) || p.startsWith("obj" + File.separator) || p.equals("bin") || p.equals("obj");
            if (onlyBuild) addPending(r.dir);
            else left.add(r.dir.getPath());
        }
        return left;
    }

    /** כל מה שבתיקייה (נתיבים יחסיים) — להודעות ולבדיקות */
    static List<String> list(File dir) {
        List<String> out = new ArrayList<String>();
        list(dir, "", out);
        return out;
    }

    private static void list(File d, String pre, List<String> out) {
        File[] k = d.listFiles();
        if (k == null) return;
        for (File f : k) {
            out.add(pre + f.getName());
            if (f.isDirectory()) list(f, pre + f.getName() + File.separator, out);
        }
    }

    static File pendingFile() {
        return Paths.get(System.getProperty("user.home"), ".dsviz", "bank-cs-pending.txt").toFile();
    }

    static synchronized void addPending(File dir) {
        try {
            List<String> l = pendingFile().isFile() ? new ArrayList<String>(Files.readAllLines(pendingFile().toPath(), StandardCharsets.UTF_8)) : new ArrayList<String>();
            if (!l.contains(dir.getAbsolutePath())) l.add(dir.getAbsolutePath());
            pendingFile().getParentFile().mkdirs();
            Files.write(pendingFile().toPath(), String.join("\n", l).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // לא נרשם — התיקייה (רק bin / obj) נשארת
        }
    }

    /**
     * תרגילים שנמחקו אבל Visual Studio החזיק את קבצי הבנייה שלהם: מוחקים את bin / obj ואת התיקייה — רק אם עדיין אין בה שום דבר אחר
     * (אם בינתיים נוסף שם קובץ — לא נוגעים). נקרא כשהכלי נפתח.
     */
    static synchronized void cleanPending() {
        if (!pendingFile().isFile()) return;
        List<String> keep = new ArrayList<String>();
        try {
            for (String p : Files.readAllLines(pendingFile().toPath(), StandardCharsets.UTF_8)) {
                if (p.trim().isEmpty()) continue;
                File d = new File(p.trim());
                if (!d.isDirectory()) continue;
                String[] names = d.list();
                boolean onlyBuild = names != null;
                if (names != null) for (String n : names) onlyBuild &= n.equalsIgnoreCase("bin") || n.equalsIgnoreCase("obj");
                if (!onlyBuild) continue;   // יש שם משהו אחר — לא שלנו למחוק
                delete(new File(d, "bin"));
                delete(new File(d, "obj"));
                if (!d.delete()) keep.add(d.getAbsolutePath());
            }
            if (keep.isEmpty()) pendingFile().delete();
            else Files.write(pendingFile().toPath(), String.join("\n", keep).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // בפעם הבאה
        }
    }

    /** מוציא את הפרויקט מקובץ ה-.sln: Project ... EndProject, וכל השורות עם ה-GUID שלו (Debug / Release, תיקיות) */
    static void unregister(Path sln, String name) throws IOException {
        byte[] raw = Files.readAllBytes(sln);
        boolean bom = raw.length >= 3 && (raw[0] & 0xFF) == 0xEF && (raw[1] & 0xFF) == 0xBB && (raw[2] & 0xFF) == 0xBF;
        String text = new String(raw, bom ? 3 : 0, raw.length - (bom ? 3 : 0), StandardCharsets.UTF_8);
        String nl = text.contains("\r\n") ? "\r\n" : "\n";
        Matcher m = Pattern.compile("(?m)^Project\\(\"[^\"]*\"\\)\\s*=\\s*\"" + Pattern.quote(name) + "\",\\s*\"[^\"]*\",\\s*\"(\\{[^}]+\\})\"\\s*$").matcher(text);
        if (!m.find()) return;
        String guid = m.group(1);
        List<String> out = new ArrayList<String>();
        String[] lines = text.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            if (l.startsWith("Project(") && l.contains("\"" + guid + "\"")) {
                while (i < lines.length && !lines[i].trim().equals("EndProject")) i++;   // עד EndProject (כולל)
                continue;
            }
            if (l.contains(guid)) continue;
            out.add(l);
        }
        Files.write(sln, ((bom ? "\uFEFF" : "") + String.join(nl, out)).getBytes(StandardCharsets.UTF_8));
    }

    /** מוחק מ-~/.dsviz/solutions.json את ההצעות של הקבצים האלה (שלא יופיעו ב"הצעה לפתרון ב-C#" אחרי שהתרגיל נמחק) */
    @SuppressWarnings("unchecked")
    static void forgetSolutions(List<File> files) {
        java.util.Set<String> paths = new java.util.HashSet<String>();
        for (File f : files) paths.add(slash(f.getAbsolutePath()).toLowerCase());
        try {
            SolutionStore s = new SolutionStore(VsSubmit.storeFile());
            Method load = SolutionStore.class.getDeclaredMethod("load"), save = SolutionStore.class.getDeclaredMethod("save");
            Field f = SolutionStore.class.getDeclaredField("entries");
            load.setAccessible(true);
            save.setAccessible(true);
            f.setAccessible(true);
            synchronized (s) {
                load.invoke(s);
                boolean changed = false;
                for (Iterator<SolutionStore.Entry> it = ((List<SolutionStore.Entry>) f.get(s)).iterator(); it.hasNext();) {
                    if (paths.contains(slash(it.next().path).toLowerCase())) {
                        it.remove();
                        changed = true;
                    }
                }
                if (changed) save.invoke(s);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // גרסה אחרת של SolutionStore — ההצעות נשארות (לא מזיק)
        }
    }

    /** כמה הצעות שמורות לקבצים שבתיקייה (לבדיקות) */
    static int solutionsUnder(File dir) {
        String prefix = slash(dir.getAbsolutePath()).toLowerCase() + "/";
        int n = 0;
        for (SolutionStore.Entry e : new SolutionStore(VsSubmit.storeFile()).all()) if (slash(e.path).toLowerCase().startsWith(prefix)) n++;
        return n;
    }

    static void delete(File f) {
        File[] k = f.listFiles();
        if (k != null) for (File c : k) delete(c);
        f.delete();
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ Visual Studio

    private static String devenv;

    /** devenv.exe של Visual Studio (vswhere), או null */
    static String devenv() {
        if (devenv != null) return devenv.isEmpty() ? null : devenv;
        devenv = "";
        String pf = System.getenv("ProgramFiles(x86)");
        if (pf == null) return null;
        File vswhere = new File(pf, "Microsoft Visual Studio\\Installer\\vswhere.exe");
        if (!vswhere.isFile()) return null;
        String[] out = ExercisePipeline.exec(java.util.Arrays.asList(vswhere.getPath(), "-latest", "-products", "*", "-property", "productPath"), null, 20);
        if (out != null && new File(out[0].trim()).isFile()) devenv = out[0].trim();
        return devenv.isEmpty() ? null : devenv;
    }

    static boolean vsRunning() {
        String[] out = ExercisePipeline.exec(java.util.Arrays.asList("tasklist", "/FI", "IMAGENAME eq devenv.exe", "/NH"), null, 15);
        return out != null && out[0].toLowerCase().contains("devenv.exe");
    }

    /** הפתרון ב-Visual Studio: אם Visual Studio לא פתוח — פותחים אותו עם הפתרון; אם פתוח — הוא יציע Reload לבד */
    static void openSolution() {
        Path sln = CsSolution.solution(root);
        if (sln == null) return;
        opened.add("SLN " + sln);
        if (Boolean.getBoolean("dsviz.noOpen") || vsRunning()) return;
        String dv = devenv();
        try {
            if (dv != null) new ProcessBuilder(dv, sln.toString()).start();
            else if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(sln.toFile());
        } catch (IOException ignored) {
            // לא נפתח — הפרויקט בכל זאת בפתרון
        }
    }

    /** פותח קובץ ב-Visual Studio שכבר פתוח (devenv /edit), והסמן בשורה (מ-1) */
    static void openFile(File f, int line) {
        opened.add("FILE " + f.getName() + ":" + line);
        if (Boolean.getBoolean("dsviz.noOpen")) return;
        String dv = devenv();
        try {
            if (dv != null) {
                List<String> cmd = new ArrayList<String>(java.util.Arrays.asList(dv, "/edit", f.getPath()));
                if (line > 0) {
                    cmd.add("/command");
                    cmd.add("Edit.GoTo " + line);
                }
                new ProcessBuilder(cmd).start();
            } else if (java.awt.Desktop.isDesktopSupported()) java.awt.Desktop.getDesktop().open(f);
        } catch (IOException ignored) {
            // לא נפתח
        }
    }

    /**
     * "הרצה ויזואלית" מהחלון הצף — אותו דבר כמו הפרופיל "הרצה ויזואלית" של הפרויקט ב-Visual Studio (dsviz-cs על תיקיית הפרויקט),
     * בלי לבחור קודם Startup Project. מריץ את מה ששמור בדיסק (ב-Visual Studio שומרים קודם: Ctrl+S).
     */
    static void runVisual(File projectDir) {
        opened.add("RUN " + projectDir.getName());
        if (Boolean.getBoolean("dsviz.noOpen")) return;
        List<String> cmd = new ArrayList<String>(VsSubmit.dsvizCs());
        cmd.add(projectDir.getAbsolutePath());
        try {
            new ProcessBuilder(cmd).directory(projectDir).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(new File(System.getProperty("java.io.tmpdir"), "dsviz-bank-run.txt"))).start();
        } catch (IOException ignored) {
            // לא רץ
        }
    }

    /** השורה (מ-1) של הכותרת של הפעולה בקובץ, או 0 */
    static int methodLine(File f, String method) {
        try {
            List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            Pattern p = Pattern.compile("^\\s*(public|private|protected|internal|static)\\b.*\\b" + Pattern.quote(method) + "\\s*\\(");
            for (int i = 0; i < lines.size(); i++) if (p.matcher(lines.get(i)).find()) return i + 1;
        } catch (IOException ignored) {
            // אין קובץ
        }
        return 0;
    }
}

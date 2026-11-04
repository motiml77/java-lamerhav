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

    // ---- מצב IntelliJ (Java): התוסף dsviz-bank מפעיל את הכלי עם --ij, ומקבל ממנו בקשות בגשר מקומי (פתיחת קובץ, הרצה ויזואלית, רענון)
    /** IntelliJ (Java) — root היא תיקיית הקוד (src) של הפרויקט, והתרגיל הוא חבילה ex01_... בתוכה */
    static boolean ij;
    static File lib, solutionsFile;
    static String javaExe, token = "", projectName = "";
    static int port;
    /** תיקיות הפלט של הקומפילציה (IntelliJ: out/production/<מודול>) — מוחקים משם רק את ה-class של קבצי התרגיל */
    static final List<File> outDirs = new ArrayList<File>();

    static String title() {
        return ij ? "מאגר תרגילים — IntelliJ" : TITLE;
    }

    static String ide() {
        return ij ? "IntelliJ" : "Visual Studio";
    }

    /** איפה התרגיל נוצר — לטקסט בחלונות */
    static String where() {
        return ij ? "בפרויקט " + projectName : "ב-Visual Studio";
    }

    static String ext() {
        return ij ? ".java" : ".cs";
    }

    static File storeFile() {
        return ij && solutionsFile != null ? solutionsFile : VsSubmit.storeFile();
    }
    static BankStore store;
    /** בדיקות: מה היה נפתח ב-Visual Studio (עם -Ddsviz.noOpen=true לא נפתח כלום) */
    static final List<String> opened = java.util.Collections.synchronizedList(new ArrayList<String>());

    public static void main(final String[] a) throws Exception {
        if (a.length < 2) {
            System.err.println("שימוש: BankVs bank|question <תיקיית DataStructures-CS>");
            System.exit(2);
        }
        configure(a);
        SwingUtilities.invokeAndWait(() -> {
            if (a[0].equals("question")) BankVsFloat.showLast(null);
            else BankVsWindow.open(null, null);
        });
    }

    /** הארגומנטים (גם לתסריטי הבדיקה): <מצב> <תיקייה> [--ij --lib --java --store --port --token --project --out], ואז init */
    static void configure(String[] a) throws Exception {
        for (int i = 2; i < a.length; i++) {
            String k = a[i], v = i + 1 < a.length ? a[i + 1] : "";
            switch (k) {
                case "--ij": ij = true; continue;
                case "--lib": lib = new File(v); break;
                case "--java": javaExe = v; break;
                case "--store": solutionsFile = new File(v); break;
                case "--port": port = Integer.parseInt(v); break;
                case "--token": token = v; break;
                case "--project": projectName = v; break;
                case "--out": for (String o : v.split(java.util.regex.Pattern.quote(File.pathSeparator))) if (!o.isEmpty()) outDirs.add(new File(o)); break;
                default: continue;
            }
            i++;
        }
        init(Paths.get(a[1]));
    }

    /** הכנה (גם לבדיקות): ההתקדמות של C#, העיצוב, והמאגר */
    static void init(Path r) throws Exception {
        if (System.getProperty("dsviz.bankProgress") == null)
            System.setProperty("dsviz.bankProgress", Paths.get(System.getProperty("user.home"), ".dsviz", ij ? "bank-progress-ij.json" : "bank-progress-cs.json").toString());
        root = r.toAbsolutePath();
        if (!ij) CsSolution.openIde = (sln, csproj) -> inOpenVs("add", sln, csproj.toString());
        cleanPending();
        SwingUtilities.invokeAndWait(() -> {
            Theme.install();
            compactFonts();
            store = BankStore.load(!ij);
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
        ExercisePipeline.Result r;
        if (ij) {
            // IntelliJ: חבילה ex01_... בתיקיית הקוד, קומפילציה ובדיקת ההצעות מול dsviz.jar (בלי Gemini)
            List<String> taken = new ArrayList<String>();
            String[] names = root.toFile().list();
            if (names != null) for (String n : names) if (new File(root.toFile(), n).isDirectory()) taken.add(n);
            r = ExercisePipeline.java(null, raw, new ArrayList<String>(), root.toFile(), lib, javaExe, p, taken);
        } else {
            r = VsSubmit.build(null, raw, new ArrayList<String>(), root, VsSubmit.dsvizCs(), p, VsSubmit.takenNames(root));
        }
        if (r.files.isEmpty()) throw new IOException(r.writeError != null ? r.writeError : "הקבצים לא נכתבו");
        for (File f : r.files) stripMarks(f);
        for (SolutionStore.Entry e : r.entries) e.code = e.code.replaceAll("[\\u202A-\\u202E\\u2066-\\u2069]", "");
        if (!r.entries.isEmpty()) new SolutionStore(storeFile()).add(r.entries);
        BankProgress.Created c = new BankProgress.Created();
        c.project = root.getFileName().toString();
        c.folder = slash((ij ? root : CsSolution.exercisesDir(root)).resolve(r.folderName).toString());
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

        /** התיקייה כבר לא קיימת (נמחקה ידנית), אבל הפרויקט עוד רשום בפתרון — רק מוציאים אותו מהפתרון */
        boolean missingButRegistered() {
            if (ij || dir.exists() || !inExercises) return false;
            Path sln = CsSolution.solution(root);
            try {
                return sln != null && new String(Files.readAllBytes(sln), StandardCharsets.UTF_8).contains("\"" + dir.getName() + "\",");
            } catch (IOException e) {
                return false;
            }
        }
    }

    /** מה נוצר מהתרגיל בתיקייה, ומה עוד יש בה. מה שנוצר: קבצי ה-.cs שלו, ‎<שם>.csproj(.user)‎, Properties\launchSettings.json, bin, obj */
    static Removal plan(BankProgress.Created c) {
        File dir = new File(c.folder);
        Removal r = new Removal(dir);
        try {
            File ex = (ij ? root : CsSolution.exercisesDir(root)).toFile().getCanonicalFile();
            File parent = dir.getAbsoluteFile().getParentFile();
            r.inExercises = parent != null && parent.getCanonicalFile().equals(ex);
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
                if (made.contains(l)) continue;
                if (ij) {   // IntelliJ: בחבילה יש רק את קבצי התרגיל; כל דבר אחר — של התלמיד
                    r.others.add(n);
                    continue;
                }
                if (l.equals(dir.getName().toLowerCase() + ".csproj") || l.equals(dir.getName().toLowerCase() + ".csproj.user")
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

    /** מה יימחק מהשאלה (רק מה שנוצר ממנה) */
    static List<Removal> plans(BankStore.Question q) {
        List<Removal> plans = new ArrayList<Removal>();
        for (BankProgress.Created c : BankProgress.get().createdOf(q.id)) {
            Removal r = plan(c);
            if (!r.files.isEmpty() || r.dir.isDirectory() && r.wholeProject() || r.missingButRegistered()) plans.add(r);
        }
        return plans;
    }

    /** הנוסח של "סיימתי את התרגיל" (מה יקרה ומה יימחק), או null אם אין מה למחוק */
    static String doneText(BankStore.Question q) {
        List<Removal> plans = plans(q);
        if (plans.isEmpty()) return null;
        StringBuilder what = new StringBuilder();
        for (Removal r : plans) {
            List<String> n = new ArrayList<String>();
            for (File f : r.files) n.add(f.getName());
            what.append(what.length() == 0 ? "" : "\n");
            if (ij) {
                what.append(n.size() == 1 ? "הקובץ שיימחק: " : "הקבצים שיימחקו: ").append(String.join(", ", n));
                if (!r.others.isEmpty()) what.append("\nהחבילה ").append(r.dir.getName()).append(" נשארת — יש בה עוד קבצים שלך: ").append(String.join(", ", r.others));
            } else if (!r.dir.exists()) what.append("הפרויקט ").append(r.dir.getName()).append(" כבר נמחק מהמחשב — הוא יוצא גם מ-Visual Studio");
            else if (r.wholeProject()) what.append("התרגיל שיימחק: ").append(r.dir.getName()).append(n.isEmpty() ? "" : " (" + String.join(", ", n) + ")");
            else what.append("קבצי התרגיל שיימחקו: ").append(String.join(", ", n)).append("\nהתרגיל ").append(r.dir.getName())
                    .append(" נשאר ב-Visual Studio — יש בו עוד קבצים שלך: ").append(String.join(", ", r.others));
        }
        return "השאלה תסומן בסימן ירוק, וקבצי התרגיל יימחקו — כדי שלא יצטברו תרגילים ישנים.\n\n"
                + "אפשר תמיד לפתוח את השאלה שוב מ\"מאגר תרגילים\" ולהתחיל מחדש.\n\n"
                + what + "\nרק מה שנוצר מהתרגיל נמחק — קבצים אחרים לא נמחקים.";
    }

    /**
     * סימון ירוק, ומחיקה של מה שנוצר מהשאלה — ורק של זה: אם בתיקיית הפרויקט אין שום דבר אחר, הפרויקט נמחק כולו (התיקייה והרישום
     * בפתרון); אם התלמיד הוסיף שם קבצים משלו — נמחקים רק קבצי התרגיל, והפרויקט עם הקבצים שלו נשאר. גם ההצעות לפתרון השמורות
     * והשיוך לשאלה נמחקים. ask — שואלים קודם (בחלון בעברית, עם הרשימה). מחזיר false אם התלמיד ביטל.
     */
    static boolean complete(Component parent, BankStore.Question q, boolean ask) {
        String text = ask ? doneText(q) : null;
        if (text != null && RtlDialog.ask(parent, "סיימתי את התרגיל", RtlDialog.Kind.OK, "סיימתי את התרגיל", text, "סיום ומחיקה", "ביטול") != 0) return false;
        List<Removal> plans = plans(q);
        BankProgress.get().setDone(q.id, true);
        List<String> left = new ArrayList<String>();
        for (Removal r : plans) left.addAll(remove(r));
        BankProgress.get().forgetCreated(q.id);
        laterClean();
        if (!left.isEmpty() && parent != null)
            RtlDialog.show(parent, "סיימתי את התרגיל", RtlDialog.Kind.WARN, "חלק מהקבצים לא נמחקו",
                    String.join("\n", left) + "\n\nכנראה שהתוכנית עדיין רצה, או שקובץ פתוח בתוכנה אחרת. אפשר למחוק אותם ידנית.");
        return true;
    }

    /** מוחק לפי התוכנית. מחזיר מה שלא הצליח להימחק */
    static List<String> remove(Removal r) {
        List<String> left = new ArrayList<String>();
        // Visual Studio פתוח עם הפתרון: קודם הוא מוציא את הפרויקט בעצמו (ושומר) — בלי "File Modification Detected", ולפני שהקבצים נמחקים
        Path vsSln = ij || !(r.missingButRegistered() || (r.wholeProject() && r.dir.isDirectory())) ? null : CsSolution.solution(root);
        boolean byVs = vsSln != null && inOpenVs("remove", vsSln, r.dir.getName());
        if (r.missingButRegistered()) {
            if (byVs) return left;
            try {
                unregister(CsSolution.solution(root), r.dir.getName());
            } catch (IOException e) {
                left.add(r.dir.getName());
            }
            return left;
        }
        forgetSolutions(r.files);
        for (File f : r.files) {
            for (int i = 0; i < 6 && f.exists(); i++) {
                f.delete();
                if (f.exists()) sleep(500);
            }
            if (f.exists()) left.add(f.getPath());
        }
        if (ij) {
            deleteClasses(r);
            if (r.wholeProject() && r.dir.isDirectory()) {
                String[] rest = r.dir.list();
                if (rest != null && rest.length == 0 && !r.dir.delete()) left.add(r.dir.getPath());
            }
            bridge("REFRESH", root.toString());
            return left;
        }
        if (!r.wholeProject() || !r.dir.isDirectory()) return left;
        Path sln = CsSolution.solution(root);
        try {
            if (sln != null && !byVs) unregister(sln, r.dir.getName());
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
        // Visual Studio (שהפרויקט עוד טעון בו עד ה-Reload) יכול ליצור מחדש את obj אחרי המחיקה — בודקים שוב אחר כך (cleanPending
        // מוחק רק תיקייה שיש בה bin / obj בלבד, ושוכח תיקייה שכבר לא קיימת)
        addPending(r.dir);
        if (!r.dir.delete() && r.dir.exists()) {
            // נשארו רק קבצי בנייה (bin / obj) ש-Visual Studio עוד מחזיק — נמחקים בפעם הבאה שהכלי נפתח (pending), ולא נוגעים בשום דבר אחר
            List<String> rest = list(r.dir);
            boolean onlyBuild = true;
            for (String p : rest) onlyBuild &= p.startsWith("bin" + File.separator) || p.startsWith("obj" + File.separator) || p.equals("bin") || p.equals("obj");
            if (!onlyBuild) left.add(r.dir.getPath());
        }
        return left;
    }

    /**
     * IntelliJ: הקבצים המקומפלים של קבצי התרגיל בכל תיקיית פלט (out/production/<מודול>/ex01_.../Name.class ו-Name$...class) —
     * רק הם; תיקיית החבילה שם נמחקת רק אם נשארה ריקה
     */
    static void deleteClasses(Removal r) {
        for (File out : outDirs) {
            File pkg = new File(out, r.dir.getName());
            File[] k = pkg.listFiles();
            if (k == null) continue;
            for (File f : r.files) {
                String base = f.getName().replaceAll("\\.java$", "");
                for (File c : k) {
                    String n = c.getName();
                    if (c.isFile() && (n.equals(base + ".class") || n.startsWith(base + "$") && n.endsWith(".class"))) c.delete();
                }
            }
            String[] rest = pkg.list();
            if (rest != null && rest.length == 0) pkg.delete();
        }
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

    /** בדיקה חוזרת ברקע (אחרי 30 ו-90 שניות, כל עוד הכלי פתוח) — אחרי ש-Visual Studio טען מחדש את הפתרון ושחרר את obj */
    static void laterClean() {
        Thread t = new Thread(() -> {
            for (long ms : new long[] { 30000, 60000 }) {
                sleep(ms);
                cleanPending();
            }
        }, "dsviz-bank-clean");
        t.setDaemon(true);
        t.start();
    }

    static File pendingFile() {
        return Paths.get(System.getProperty("user.home"), ".dsviz", "bank-cs-pending.txt").toFile();
    }

    /** נרשם לבדיקה חוזרת: "נתיב|זמן" */
    static synchronized void addPending(File dir) {
        try {
            List<String> l = pendingFile().isFile() ? new ArrayList<String>(Files.readAllLines(pendingFile().toPath(), StandardCharsets.UTF_8)) : new ArrayList<String>();
            l.removeIf(x -> x.split("\\|")[0].equals(dir.getAbsolutePath()));
            l.add(dir.getAbsolutePath() + "|" + System.currentTimeMillis());
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
                String[] parts = p.trim().split("\\|");
                long at = 0;
                try {
                    at = parts.length > 1 ? Long.parseLong(parts[1]) : 0;
                } catch (NumberFormatException ignored) {
                    // שורה ישנה בלי זמן
                }
                File d = new File(parts[0]);
                if (!d.isDirectory()) {
                    // עוד לא נוצרה מחדש (או נמחקה לגמרי) — שומרים 10 דקות, אולי Visual Studio עוד יכתוב שם obj
                    if (System.currentTimeMillis() - at < 10 * 60000) keep.add(p.trim());
                    continue;
                }
                String[] names = d.list();
                boolean onlyBuild = names != null;
                if (names != null) for (String n : names) onlyBuild &= n.equalsIgnoreCase("bin") || n.equalsIgnoreCase("obj");
                if (!onlyBuild) continue;   // יש שם משהו אחר — לא שלנו למחוק
                delete(new File(d, "bin"));
                delete(new File(d, "obj"));
                if (!d.delete()) keep.add(p.trim());
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
            SolutionStore s = new SolutionStore(storeFile());
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
        for (SolutionStore.Entry e : new SolutionStore(storeFile()).all()) if (slash(e.path).toLowerCase().startsWith(prefix)) n++;
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
        if (ij) {
            bridge("REFRESH", root.toString());
            return;
        }
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
        if (ij) {
            bridge("OPEN", f.getAbsolutePath(), String.valueOf(line));
            return;
        }
        if (Boolean.getBoolean("dsviz.noOpen")) return;
        // ברקע — כדי שהחלונות לא ייתקעו בזמן ש-Visual Studio עסוק
        Thread t = new Thread(() -> {
            // Visual Studio כבר פתוח: פותחים את הקובץ בו (DTE) — devenv /edit פותח לפעמים Visual Studio נוסף כשהפתוח עסוק
            if (vsRunning() && openInRunningVs(f, line)) return;
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
        }, "dsviz-open");
        t.setDaemon(true);
        t.start();
    }

    /**
     * פותח את הקובץ ב-Visual Studio שכבר פתוח, דרך האוטומציה שלו (DTE, ב-PowerShell): הקובץ נפתח באותו חלון, הסמן בשורה,
     * ו-Visual Studio עולה מקדימה; והפרויקט של הקובץ נהיה ה-Startup Project (כדי ש"הרצה ויזואלית" בסרגל תריץ את התרגיל). כש-Visual Studio עסוק ("Working on it") הוא דוחה את הקריאה — מנסים שוב כמה פעמים.
     * מחזיר false אם לא הצליח (ואז devenv /edit).
     */
    static boolean openInRunningVs(File f, int line) {
        String ps = "$ErrorActionPreference='Stop'; $d=$null; "
                + "foreach($p in 'VisualStudio.DTE.18.0','VisualStudio.DTE.17.0','VisualStudio.DTE'){ try { $d=[Runtime.InteropServices.Marshal]::GetActiveObject($p); break } catch {} }; "
                + "if(-not $d){ exit 3 }; "
                + "for($i=0; $i -lt 15; $i++){ try { "
                + "$null=$d.ItemOperations.OpenFile($env:DSVIZ_FILE); "
                + "if([int]$env:DSVIZ_LINE -gt 0){ $d.ActiveDocument.Selection.GotoLine([int]$env:DSVIZ_LINE, $true) }; "
                + "$d.MainWindow.Activate(); break } catch { Start-Sleep -Milliseconds 1500 } }; if($i -ge 15){ exit 4 }; "
                // התרגיל = הפרויקט שמריצים (Startup Project): הכפתור "הרצה ויזואלית" בסרגל של Visual Studio מריץ אותו מיד.
                // שתי דרכים (מאפיין הפתרון StartupProject, ו-StartupProjects), ובודקים שזה נקלט
                // הפרויקט — לפי השם (התיקייה של הקובץ), במעבר לפי מספר על הפרויקטים של הפתרון
                + "for($j=0; $j -lt 20; $j++){ try { $pr=$null; $c=$d.Solution.Projects.Count; "
                + "for($q=1; $q -le $c; $q++){ $x=$d.Solution.Projects.Item($q); if($x.Name -eq $env:DSVIZ_PROJECT -or ([string]$x.UniqueName).EndsWith('\\' + $env:DSVIZ_PROJECT + '.csproj')){ $pr=$x } }; "
                + "if($pr){ try { $d.Solution.Properties.Item('StartupProject').Value=$pr.Name } catch { Write-Output ('PROP ' + $_) }; "
                + "$now=(@($d.Solution.SolutionBuild.StartupProjects) -join ','); "
                + "if($now -notlike ('*' + $pr.Name + '*')){ try { $d.Solution.SolutionBuild.StartupProjects=$pr.UniqueName } catch { Write-Output ('SB1 ' + $_) }; $now=(@($d.Solution.SolutionBuild.StartupProjects) -join ',') }; "
                + "if($now -notlike ('*' + $pr.Name + '*')){ try { $d.Solution.SolutionBuild.StartupProjects=[object[]]@($pr.UniqueName) } catch { Write-Output ('SB2 ' + $_) }; $now=(@($d.Solution.SolutionBuild.StartupProjects) -join ',') }; "
                + "Write-Output ('STARTUP ' + $pr.UniqueName + ' -> ' + $now); if($now -like ('*' + $pr.Name + '*')){ exit 0 } } "
                + "else { Write-Output ('NO-PROJECT ' + $env:DSVIZ_PROJECT + ' of ' + $c) } } catch { Write-Output ('FIND ' + $_) }; Start-Sleep -Milliseconds 1500 }; exit 0";
        File dir = f.getAbsoluteFile().getParentFile();
        int code = powershell(ps, 75, "DSVIZ_FILE", f.getAbsolutePath(), "DSVIZ_LINE", String.valueOf(line), "DSVIZ_PROJECT", dir == null ? "" : dir.getName());
        opened.add("DTE " + code);
        return code == 0;
    }

    /**
     * גשר ל-IntelliJ: שורה אחת ל-127.0.0.1:port — "token\tפקודה\tארגומנטים". התוסף פותח קובץ (OPEN) או מרענן (REFRESH).
     * בלי גשר (בדיקות) — רק נרשם ב-opened. מחזיר את התשובה ("OK ..."), או null.
     */
    static String bridge(String cmd, String... args) {
        opened.add("BRIDGE " + cmd + " " + String.join(" ", args));
        return send(cmd, args);
    }

    /**
     * Visual Studio פתוח עם הפתרון הזה: הוא עצמו מוסיף את הפרויקט ("add", ‏arg = ה-.csproj) או מוציא אותו ("remove", ‏arg = השם),
     * דרך האוטומציה שלו (DTE), ושומר את הפתרון. כך אין "File Modification Detected" (שינוי של ה-.sln מבחוץ), והפרויקט מופיע מיד.
     * false — Visual Studio לא פתוח / פתוח עם פתרון אחר / לא הצליח: ואז משנים את קובץ ה-.sln עצמו.
     */
    static boolean inOpenVs(String action, Path sln, String arg) {
        if (ij || Boolean.getBoolean("dsviz.noOpen") || !windows() || sln == null || !vsRunning()) return false;
        String name = action.equals("add") ? new File(arg).getName().replaceFirst("\\.csproj$", "") : arg;
        // אחרי השמירה בודקים את קובץ ה-.sln עצמו: הפרויקט באמת נוסף / יצא. לא — קוד 6, ואז משנים את הקובץ בעצמנו
        String ps = "$ErrorActionPreference='Stop'; $d=$null; "
                + "foreach($p in 'VisualStudio.DTE.18.0','VisualStudio.DTE.17.0','VisualStudio.DTE'){ try { $d=[Runtime.InteropServices.Marshal]::GetActiveObject($p); break } catch {} }; "
                + "if(-not $d){ Write-Output 'NO-DTE'; exit 3 }; "
                + "$sln=[IO.Path]::GetFullPath($env:DSVIZ_SLN); "
                + "for($i=0; $i -lt 20; $i++){ try { "
                + "if(-not $d.Solution.IsOpen){ Write-Output 'NOT-OPEN'; exit 5 }; "
                + "if([IO.Path]::GetFullPath($d.Solution.FullName) -ne $sln){ Write-Output ('OTHER ' + $d.Solution.FullName); exit 5 }; "
                + "if($env:DSVIZ_ACTION -eq 'add'){ $null=$d.Solution.AddFromFile($env:DSVIZ_ARG, $false) } "
                // הפרויקטים לפי מספר (Item(1..Count)) — מעבר עם foreach על האוסף של Visual Studio ב-PowerShell לא מוצא אותם
                + "else { $found=@(); $c=$d.Solution.Projects.Count; for($q=1; $q -le $c; $q++){ $x=$d.Solution.Projects.Item($q); Write-Output ('PROJ ' + $x.Name + ' | ' + $x.UniqueName); "
                + "if($x.Name -eq $env:DSVIZ_NAME -or ([string]$x.UniqueName).EndsWith('\\' + $env:DSVIZ_NAME + '.csproj')){ $found += $x } }; "
                + "foreach($x in $found){ $d.Solution.Remove($x) }; Write-Output ('REMOVED ' + $found.Count) }; "
                + "break } catch { Write-Output ('RETRY ' + $_); Start-Sleep -Milliseconds 1500 } }; if($i -ge 20){ exit 4 }; "
                + "for($k=0; $k -lt 10; $k++){ try { $d.Solution.SaveAs($d.Solution.FullName) } catch { Write-Output ('SAVE ' + $_) }; Start-Sleep -Milliseconds 700; "
                + "$has=([IO.File]::ReadAllText($sln)).Contains($env:DSVIZ_WANT); "
                + "if(($env:DSVIZ_ACTION -eq 'add') -eq $has){ Write-Output ('OK ' + $env:DSVIZ_ACTION + ' ' + $env:DSVIZ_NAME); exit 0 }; Start-Sleep -Milliseconds 800 }; "
                + "Write-Output ('NOT-SAVED ' + $env:DSVIZ_ACTION); exit 6";
        int code = powershell(ps, 70, "DSVIZ_ACTION", action, "DSVIZ_SLN", sln.toAbsolutePath().toString(), "DSVIZ_ARG", arg,
                "DSVIZ_NAME", name, "DSVIZ_WANT", "\"" + name + "\"");
        opened.add("VS-" + action.toUpperCase(java.util.Locale.ROOT) + " " + code);
        return code == 0;
    }

    /**
     * PowerShell עם התסריט מקודד (‎-EncodedCommand‎ — בלי בעיות של מרכאות בשורת הפקודה של Windows), ומשתני סביבה (שם, ערך, ...).
     * הפלט נכתב ליומן dsviz-bank-dte.txt בתיקייה הזמנית. מחזיר את קוד היציאה, או ‎-1‎ אם לא נגמר בזמן / לא רץ.
     */
    static int powershell(String script, int timeoutSec, String... env) {
        try {
            String enc = java.util.Base64.getEncoder().encodeToString(("$ProgressPreference='SilentlyContinue'; " + script).getBytes(StandardCharsets.UTF_16LE));
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", enc);
            for (int i = 0; i + 1 < env.length; i += 2) pb.environment().put(env[i], env[i + 1]);
            File log = new File(System.getProperty("java.io.tmpdir"), "dsviz-bank-dte.txt");
            try (java.io.Writer w = new java.io.OutputStreamWriter(new java.io.FileOutputStream(log, true), StandardCharsets.UTF_8)) {
                w.write("--- " + new java.util.Date() + " " + (env.length > 1 ? env[1] : "") + "\n");
            }
            pb.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log));
            Process p = pb.start();
            p.getOutputStream().close();
            if (!p.waitFor(timeoutSec, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return -1;
            }
            return p.exitValue();
        } catch (Exception e) {
            return -1;
        }
    }

    static boolean windows() {
        return System.getProperty("os.name", "").startsWith("Windows");
    }

    /** IntelliJ / Visual Studio מציג עכשיו חלון דו-שיח (למשל שגיאות בהרצה, או "פתיחת קובץ") — שהחלון הצף לא יסתיר אותו */
    static boolean ideDialog() {
        return ij ? "1".equals(send("DIALOG")) : vsDialog;
    }

    private static volatile boolean vsDialog;
    private static boolean vsWatching;

    /**
     * Visual Studio: תהליך PowerShell אחד ברקע בודק כל ‎0.6‎ שניות אם החלון שמקדימה הוא חלון דו-שיח של Visual Studio (חלון של devenv
     * שיש לו חלון-אב — הדו-שיחים; לחלון הראשי אין), ומדפיס 1 / 0 כשזה משתנה. נסגר לבד כשהכלי נסגר.
     */
    static synchronized void watchVsDialogs() {
        if (ij || vsWatching || Boolean.getBoolean("dsviz.noOpen") || !windows()) return;
        vsWatching = true;
        String pid = java.lang.management.ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
        String ps = "Add-Type -Namespace DsViz -Name W -MemberDefinition '"
                + "[DllImport(\"user32.dll\")] public static extern IntPtr GetForegroundWindow(); "
                + "[DllImport(\"user32.dll\")] public static extern IntPtr GetWindow(IntPtr h, uint c); "
                + "[DllImport(\"user32.dll\")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint p);'; "
                + "$last=''; while($true){ "
                + "if(-not (Get-Process -Id ([int]$env:DSVIZ_PARENT) -ErrorAction SilentlyContinue)){ exit 0 }; "
                + "$s='0'; $h=[DsViz.W]::GetForegroundWindow(); "
                + "if($h -ne [IntPtr]::Zero -and [DsViz.W]::GetWindow($h, 4) -ne [IntPtr]::Zero){ "
                + "$p=[uint32]0; [void][DsViz.W]::GetWindowThreadProcessId($h, [ref]$p); "
                + "if((Get-Process -Id ([int]$p) -ErrorAction SilentlyContinue).ProcessName -eq 'devenv'){ $s='1' } }; "
                + "if($s -ne $last){ [Console]::Out.WriteLine($s); [Console]::Out.Flush(); $last=$s }; "
                + "Start-Sleep -Milliseconds 600 }";
        String enc = java.util.Base64.getEncoder().encodeToString(ps.getBytes(StandardCharsets.UTF_16LE));
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-EncodedCommand", enc);
            pb.environment().put("DSVIZ_PARENT", pid);
            pb.redirectErrorStream(true);
            final Process p = pb.start();
            p.getOutputStream().close();
            Runtime.getRuntime().addShutdownHook(new Thread(p::destroy));
            Thread t = new Thread(() -> {
                try (java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    for (String l; (l = in.readLine()) != null;) {
                        l = l.trim();
                        if (l.equals("1") || l.equals("0")) vsDialog = l.equals("1");
                    }
                } catch (IOException ignored) {
                    // התהליך נסגר
                }
                vsDialog = false;
            }, "dsviz-vs-dialogs");
            t.setDaemon(true);
            t.start();
        } catch (IOException e) {
            vsWatching = false;
        }
    }

    static String send(String cmd, String... args) {
        if (port <= 0 || Boolean.getBoolean("dsviz.noOpen")) return null;
        try (java.net.Socket so = new java.net.Socket()) {
            so.connect(new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), port), 3000);
            so.setSoTimeout(15000);
            java.io.Writer w = new java.io.OutputStreamWriter(so.getOutputStream(), StandardCharsets.UTF_8);
            w.write(token + "\t" + cmd + (args.length == 0 ? "" : "\t" + String.join("\t", args)) + "\n");
            w.flush();
            return new java.io.BufferedReader(new java.io.InputStreamReader(so.getInputStream(), StandardCharsets.UTF_8)).readLine();
        } catch (IOException e) {
            return null;   // IntelliJ נסגר — לא נופלים
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

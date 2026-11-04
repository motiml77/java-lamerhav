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
        if (!r.entries.isEmpty()) new SolutionStore(VsSubmit.storeFile()).add(r.entries);
        BankProgress.Created c = new BankProgress.Created();
        c.project = root.getFileName().toString();
        c.folder = slash(CsSolution.exercisesDir(root).resolve(r.folderName).toString());
        c.focus = r.focus == null ? "" : slash(r.focus.getPath());
        for (File f : r.files) c.files.add(slash(f.getPath()));
        BankProgress.get().created(q.id, c);
        return r;
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

    /**
     * סימון ירוק, ומחיקה של כל מה שנוצר מהשאלה: הפרויקט (התיקייה כולה), הרישום בפתרון, ההצעות לפתרון השמורות, והשיוך לשאלה.
     * ask — שואלים קודם (בחלון בעברית). מחזיר false אם התלמיד ביטל.
     */
    static boolean complete(Component parent, BankStore.Question q, boolean ask) {
        List<File> dirs = projects(q);
        StringBuilder names = new StringBuilder();
        for (File d : dirs) names.append(names.length() == 0 ? "" : ", ").append(d.getName());
        if (ask && !dirs.isEmpty() && RtlDialog.ask(parent, "סיימתי את התרגיל", RtlDialog.Kind.OK, "סיימתי את התרגיל",
                "השאלה תסומן בסימן ירוק, והפרויקט של התרגיל יימחק מהפתרון — כדי שלא יצטברו תרגילים ישנים.\n\n"
                + "אפשר תמיד לפתוח את השאלה שוב מ\"מאגר תרגילים\" ולהתחיל מחדש.\n\n"
                + (dirs.size() == 1 ? "הפרויקט שיימחק: " : "הפרויקטים שיימחקו: ") + names, "סיום ומחיקה", "ביטול") != 0) return false;
        BankProgress.get().setDone(q.id, true);
        List<String> left = new ArrayList<String>();
        for (File d : dirs) {
            String why = removeProject(d);
            if (why != null) left.add(why);
        }
        BankProgress.get().forgetCreated(q.id);
        if (!left.isEmpty() && parent != null)
            RtlDialog.show(parent, "סיימתי את התרגיל", RtlDialog.Kind.WARN, "חלק מהקבצים לא נמחקו",
                    String.join("\n", left) + "\n\nכנראה שהתוכנית עדיין רצה, או שקובץ פתוח בתוכנה אחרת. אפשר למחוק את התיקייה ידנית.");
        return true;
    }

    /** מוחק פרויקט אחד: הרישום בפתרון, ההצעות השמורות שלו, והתיקייה. null — הכול נמחק; אחרת — מה נשאר */
    static String removeProject(File dir) {
        Path sln = CsSolution.solution(root);
        try {
            if (sln != null) unregister(sln, dir.getName());
        } catch (IOException e) {
            // הפתרון לא עודכן — Visual Studio יראה פרויקט חסר; התיקייה בכל זאת נמחקת
        }
        forgetSolutions(dir);
        for (int i = 0; i < 6 && dir.exists(); i++) {
            delete(dir);
            if (dir.exists()) sleep(500);   // קובץ שנעול לרגע (הבנייה של Visual Studio) — מנסים שוב
        }
        return dir.exists() ? dir.getPath() : null;
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

    /** מוחק מ-~/.dsviz/solutions.json את ההצעות של הקבצים שבתיקייה (שלא יופיעו ב"הצעה לפתרון ב-C#" אחרי שהתרגיל נמחק) */
    @SuppressWarnings("unchecked")
    static void forgetSolutions(File dir) {
        String prefix = slash(dir.getAbsolutePath()) + "/";
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
                    if (slash(it.next().path).toLowerCase().startsWith(prefix.toLowerCase())) {
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

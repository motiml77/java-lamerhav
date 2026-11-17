package il.lamerhav.dsviz;

import java.io.File;
import java.util.List;

/**
 * בדיקה של "מאגר תרגילים" ב-VS Code (vscode-bank-test.js): הכלי מופעל בדיוק כמו ש-bank.js מפעיל אותו (אותה פקודה, הארגומנטים
 * ב-DSVIZ_ARGS, הגשר של bank.js), אבל במקום החלונות — הפעולות עצמן: יצירת תרגיל מהשאלה, "לפעולה של סעיף א", "הכן סעיף ב",
 * החלון במצב VS Code (הטקסטים), ו"סיימתי את התרגיל". כל שלב מודפס בשורה — הבדיקה ב-node משווה מול מה שהגשר קיבל ומה שבדיסק.
 *   ‎-Dq=<מזהה שאלה>‎ ‎-DwithWindow=true‎ (צריך תצוגה — Xvfb)
 */
public final class BankVsCodeDriver {
    private BankVsCodeDriver() {}

    static final ExercisePipeline.Progress QUIET = new ExercisePipeline.Progress() {
        public void say(String text) {}

        public boolean cancelled() {
            return false;
        }
    };

    public static void main(String[] a) throws Exception {
        String[] args = BankVs.envArgs(a);
        out("ARGS " + args.length + " " + String.join(" | ", args));
        BankVs.configure(args);
        out("MODE vscode=" + BankVs.vscode + " ij=" + BankVs.ij + " cs=" + BankVs.csBridge + " bridged=" + BankVs.bridged() + " key=" + BankVs.key()
                + " progress=" + new File(System.getProperty("dsviz.bankProgress")).getName());
        out("TITLE " + BankVs.title());
        out("WHERE " + BankVs.where());
        out("IDE " + BankVs.ide());
        BankStore.Question q = null;
        for (BankStore.Question x : BankVs.store.questions) if (x.id.equals(System.getProperty("q"))) q = x;
        if (q == null) throw new IllegalArgumentException("no question " + System.getProperty("q"));
        out("LANG " + q.exercise.get("language"));
        ExercisePipeline.Result r = BankVs.create(q, QUIET);
        out("CREATED " + r.folderName + " errors=" + r.errors.size() + " files=" + r.files.size());
        for (File f : r.files) out("FILE " + f.getAbsolutePath());
        File dir = r.files.isEmpty() ? null : r.files.get(0).getParentFile();
        out("PROJ " + (dir == null ? "" : dir.getName()) + " csproj=" + (dir != null && new File(dir, dir.getName() + ".csproj").isFile()));
        BankVs.openSolution();
        List<AiScaffold.Task> tasks = AiScaffold.read(q.exercise).tasks;
        AiScaffold.Task t1 = tasks.get(0);
        BankVsFloat.jump(null, q, t1);
        File own = BankVs.classFiles(q).get(t1.className);
        out("JUMP " + (own == null ? "" : own.getAbsolutePath()) + " " + (own == null ? 0 : BankVs.methodLine(own, t1.methodName)));
        if (tasks.size() > 1) {
            String p = BankVs.prepare(q, tasks.get(1));
            out("PREPARE " + (p == null ? "ok" : p) + " prepared=" + BankVs.prepared(q));
        }
        if (Boolean.getBoolean("withWindow")) {
            BankVsWindow w = BankVsIt.ui(() -> new BankVsWindow());
            String texts = BankVsIt.ui(() -> BankVsIt.texts(w.frame));
            out("WINDOW " + w.frame.getTitle() + " || " + texts.replace('\n', ' '));
            BankVsIt.ui(() -> { w.frame.dispose(); return null; });
        }
        String done = BankVs.doneText(q);
        out("DONETEXT " + (done == null ? "" : done.replace('\n', ' ')));
        BankVs.complete(null, q, false);
        out("DONE projects=" + BankVs.projects(q).size() + " left=" + (r.files.isEmpty() ? 0 : (r.files.get(0).exists() ? 1 : 0)));
        System.exit(0);
    }

    static void out(String s) {
        System.out.println(s);
        System.out.flush();
    }
}

package il.lamerhav.dsviz.cs;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import il.lamerhav.dsviz.ClassGen;
import il.lamerhav.dsviz.NameCheck;
import il.lamerhav.dsviz.PolyPanels;
import il.lamerhav.dsviz.RtlDialog;
import unit4.collectionsLib.Theme;

/**
 * הורשה ופולימורפיזם בכלי של Visual Studio (C#): "היררכיה מוכנה" — כל המחלקות של עץ בקבצים חדשים ליד הקובץ הנוכחי, ו-main לדוגמה
 * (בפרויקט שכבר יש בו Main — פעולה Run()); "הוסף עצם" — הקוד (Animal a1 = new Boxer(...);) נכנס ללוח, כמו "הוסף מבנה נתונים".
 * החלונות עצמם — PolyPanels (משותף ל-IntelliJ); ההיגיון — ClassGen (אותו קוד כמו ב-VS Code).
 */
final class CsPoly {
    private CsPoly() {}

    static final String TREE_TITLE = ClassGen.TREES_TITLE + " — C#", OBJECT_TITLE = ClassGen.OBJECT_TITLE + " — C#";

    static boolean noSource(Path source, String title, String what) {
        if (source != null && Files.isRegularFile(source)) return false;
        RtlDialog.show(null, title, RtlDialog.Kind.WARN, "לא נמצא קובץ C#",
                "פותחים ב-Visual Studio את הקובץ שעובדים עליו (‎.cs‎) — " + what + " — ולוחצים שוב \"הוספת מבנה נתונים\" " + RtlDialog.ARROW + " \"" + title.replace(" — C#", "") + "\".");
        return true;
    }

    static String shown(Path root, Path source) {
        return root != null && source.startsWith(root) ? root.relativize(source).toString() : source.getFileName().toString();
    }

    // ------------------------------------------------------------------ היררכיה מוכנה

    static void createTree(Path root, String shot, Path source) {
        if (noSource(source, TREE_TITLE, "המחלקות נוצרות לידו")) return;
        Path dir = source.toAbsolutePath().getParent();
        String[] ns = CsClassFile.namespace(source);
        ClassGen.Options base = new ClassGen.Options();
        base.lang = ClassGen.CS;
        base.namespace = ns[0];
        base.fileScoped = "file".equals(ns[1]);
        base.taken = CsClassFile.taken(dir);
        base.known = CsClassFile.known(dir);
        boolean run = CsClassFile.hasMain(dir);
        TreeForm f = new TreeForm(base, run, shown(root, source));
        if (!f.show(shot)) return;
        try {
            ClassGen.TreeResult r = f.result;
            Path proj = CsClassFile.projectDir(dir), csproj = CsClassFile.csproj(proj);
            boolean classic = csproj != null && !CsClassFile.sdkStyle(csproj);
            List<Path> created = new ArrayList<Path>();
            for (String[] file : r.files) created.add(CsClassFile.write(dir, file[0], file[1], source));
            List<Path> touched = CsClassFile.applyVirtual(proj != null ? proj : dir, r.edits);
            boolean registered = false;
            if (classic) for (Path c : created) registered |= CsClassFile.addCompile(csproj, c);
            StringBuilder names = new StringBuilder();
            for (Path c : created) names.append(names.length() > 0 ? ", " : "").append(c.getFileName());
            String main = f.tree.main;
            String slnEnv = System.getenv("DSVIZ_VS_SLN");
            Path sln = slnEnv != null && !slnEnv.trim().isEmpty() && Files.isRegularFile(Paths.get(slnEnv.trim())) ? Paths.get(slnEnv.trim())
                    : CsClassFile.solution(proj != null ? proj : dir);
            if (!created.isEmpty()) il.lamerhav.dsviz.VsDte.addAndOpen(sln, source.toFile(), created.get(created.size() - 1).toFile(), 1, false);
            StringBuilder virt = new StringBuilder();
            for (Path t : touched) virt.append("נוספה המילה virtual בקובץ ").append(t.getFileName()).append(".\n");
            RtlDialog.show(null, TREE_TITLE, RtlDialog.Kind.OK, "נוצרו " + created.size() + " קבצים",
                    names + "\n" + virt
                    + (classic ? (registered ? "הם נרשמו בקובץ הפרויקט " + csproj.getFileName() + " — אם Visual Studio פתוח, מאשרים Reload.\n" : "")
                            : "הם כבר חלק מהפרויקט (כל קובץ ‎.cs‎ בתיקייה נכלל בו).\n")
                    + (main == null || !f.withMain() ? "" : run ? "בפרויקט כבר יש Main — כותבים בו את השורה " + main + ".Run(); ומריצים בהרצה הוויזואלית.\n"
                            : "מריצים את " + main + " בהרצה הוויזואלית — ורואים את הקופסאות זו בתוך זו.\n"));
        } catch (Exception e) {
            RtlDialog.show(null, TREE_TITLE, RtlDialog.Kind.ERROR, "לא הצלחתי ליצור את המחלקות", e.getMessage());
        }
    }

    static final class TreeForm extends CsTool.Base {
        final ClassGen.Options base;
        final boolean run;
        final String docName;
        PolyPanels.TreePanel panel;
        JLabel errorLabel, warnLabel;
        JButton ok;
        ClassGen.TreeResult result;
        ClassGen.Tree tree;

        TreeForm(ClassGen.Options base, boolean run, String docName) {
            this.base = base;
            this.run = run;
            this.docName = docName;
        }

        boolean withMain() {
            return panel.result().files.size() > 0 && tree != null && tree.main != null;
        }

        boolean show(String shot) {
            JDialog d = dialog(TREE_TITLE);
            JPanel content = new JPanel(new BorderLayout(0, 8));
            content.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));
            JPanel head = new JPanel(new GridLayout(0, 1));
            JLabel t = new JLabel(ClassGen.TREES_TITLE);
            t.setFont(Theme.big);
            JLabel sub = new JLabel("כל המחלקות של עץ בלחיצה אחת — כל מחלקה בקובץ משלה, עם בנאי שמעביר ל-base, ו-main לדוגמה עם מערך"
                    + (docName != null ? "  ·  ליד " + docName + "‏" : ""));
            sub.setForeground(Theme.MUTED);
            head.add(t);
            head.add(sub);
            content.add(withHelp(head), BorderLayout.NORTH);
            if (Theme.mono != null) PolyPanels.code = Theme.mono;
            panel = new PolyPanels.TreePanel(base, run, this::update);
            content.add(scroll(new CsTool.Card(panel)), BorderLayout.CENTER);
            warnLabel = new JLabel(" ");
            warnLabel.setForeground(new java.awt.Color(0xB26A00));
            errorLabel = new JLabel(" ");
            errorLabel.setForeground(new java.awt.Color(0xC62828));
            ok = new JButton("יצירת המחלקות");
            JButton cancel = new JButton("ביטול");
            style(ok, cancel);
            JPanel msgs = new JPanel(new GridLayout(0, 1));
            msgs.add(warnLabel);
            msgs.add(errorLabel);
            JPanel south = new JPanel(new BorderLayout());
            south.add(msgs, BorderLayout.NORTH);
            south.add(buttons(d, ok, cancel), BorderLayout.CENTER);
            content.add(south, BorderLayout.SOUTH);
            d.setContentPane(content);
            onClick(ok, () -> { if (result != null && result.ok() && !result.files.isEmpty()) { accepted = true; d.dispose(); } });
            javax.swing.JRootPane rp = d.getRootPane();
            rp.registerKeyboardAction(e -> d.dispose(), javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            rp.putClientProperty("dsviz.form", this);   // לצילומי מסך ולבדיקות (SwingScript: call ...)
            update();
            applyOrientation(content);
            PolyPanels.fixLtr(content);
            return display(d, shot);
        }

        void update() {
            if (panel == null || ok == null) return;
            List<String> errs = new ArrayList<String>();
            tree = panel.tree(errs);
            result = panel.result();
            errorLabel.setText(result.errors.isEmpty() ? " " : result.errors.get(0));
            List<String> w = new ArrayList<String>(result.warnings);
            if (run && tree != null && tree.main != null) w.add("בפרויקט כבר יש Main — ה-main לדוגמה נוצר כפעולה " + tree.main + ".Run()\u200E");
            warnLabel.setText(w.isEmpty() ? " " : String.join(" · ", w));
            ok.setEnabled(result.ok() && !result.files.isEmpty());
            PolyPanels.fixLtr(panel);
            relayout();
        }

        // ---------------------------------------------------------------- SwingScript: call ...
        void choose(String k) {
            panel.demoChoose(Integer.parseInt(k.trim()));
        }

        void rows(String spec) {
            panel.demoRows(spec);
        }
    }

    // ------------------------------------------------------------------ הוסף עצם

    static void insertObject(Path root, String shot, Path source) {
        if (noSource(source, OBJECT_TITLE, "המחלקות נקראות מהפרויקט שלו")) return;
        Path dir = source.toAbsolutePath().getParent();
        String text = "";
        try {
            text = CsClassFile.read(source);
        } catch (Exception ignored) {
            // בלי בדיקת שם מול הקובץ
        }
        ObjectForm f = new ObjectForm(CsClassFile.known(dir), text, shown(root, source));
        if (!f.show(shot)) return;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(f.code), null);
        if (System.getProperty("dsviz.printCode") != null) System.out.println(f.code);   // לבדיקות
        RtlDialog.show(null, OBJECT_TITLE, RtlDialog.Kind.OK, "הקוד הועתק",
                "עוברים ל-Visual Studio, לוחצים במקום של העצם (בתוך Main או בתוך פעולה), ואז Ctrl+V.");
    }

    static final class ObjectForm extends CsTool.Base {
        final List<ClassGen.Known> known;
        final String docText, docName;
        PolyPanels.ObjectPanel panel;
        JLabel errorLabel, warnLabel;
        JButton ok;
        String code;

        ObjectForm(List<ClassGen.Known> known, String docText, String docName) {
            this.known = known;
            this.docText = docText;
            this.docName = docName;
        }

        boolean show(String shot) {
            JDialog d = dialog(OBJECT_TITLE);
            JPanel content = new JPanel(new BorderLayout(0, 8));
            content.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));
            JPanel head = new JPanel(new GridLayout(0, 1));
            JLabel t = new JLabel(ClassGen.OBJECT_TITLE);
            t.setFont(Theme.big);
            JLabel sub = new JLabel("משתנה מסוג מחלקת העל שמצביע על עצם של מחלקה יורשת — או מערך שבכל תא מחלקה אחרת" + (docName != null ? "  ·  " + docName + "‏" : ""));
            sub.setForeground(Theme.MUTED);
            head.add(t);
            head.add(sub);
            content.add(withHelp(head), BorderLayout.NORTH);
            if (Theme.mono != null) PolyPanels.code = Theme.mono;
            panel = new PolyPanels.ObjectPanel(ClassGen.CS, known, this::update, null);
            content.add(scroll(new CsTool.Card(panel)), BorderLayout.CENTER);
            warnLabel = new JLabel(" ");
            warnLabel.setForeground(new java.awt.Color(0xB26A00));
            errorLabel = new JLabel(" ");
            errorLabel.setForeground(new java.awt.Color(0xC62828));
            ok = new JButton("העתקת הקוד");
            JButton cancel = new JButton("ביטול");
            style(ok, cancel);
            JPanel msgs = new JPanel(new GridLayout(0, 1));
            msgs.add(warnLabel);
            msgs.add(errorLabel);
            JPanel south = new JPanel(new BorderLayout());
            south.add(msgs, BorderLayout.NORTH);
            south.add(buttons(d, ok, cancel), BorderLayout.CENTER);
            content.add(south, BorderLayout.SOUTH);
            d.setContentPane(content);
            onClick(ok, () -> { if (code != null) { accepted = true; d.dispose(); } });
            javax.swing.JRootPane rp = d.getRootPane();
            rp.registerKeyboardAction(e -> d.dispose(), javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            rp.putClientProperty("dsviz.form", this);
            update();
            applyOrientation(content);
            PolyPanels.fixLtr(content);
            return display(d, shot);
        }

        void update() {
            if (panel == null || ok == null) return;
            ClassGen.ObjResult r = panel.result("");
            errorLabel.setText(r.errors.isEmpty() ? " " : r.errors.get(0));
            // ב-Visual Studio אי אפשר לדעת איפה הסמן: השם נבדק מול הקובץ שנערך לאחרונה, וכשהוא כבר קיים שם — רק אזהרה
            String name = r.code.replaceAll("(?s)^\\s*[\\w<>\\[\\]]+\\s+(\\w+)\\s*=.*", "$1");
            boolean clash = r.ok() && NameCheck.takenNames(docText, -1).contains(name);
            warnLabel.setText(clash ? "בקובץ כבר יש משהו בשם " + name + " — אם זה באותה פעולה, בוחרים שם אחר" : " ");
            code = r.ok() ? r.code : null;
            ok.setEnabled(r.ok());
            PolyPanels.fixLtr(panel);
            relayout();
        }

        // ---------------------------------------------------------------- SwingScript: call ...
        void demo(String type, String var, String cls, String values) {
            panel.demo(type, var, cls, values);
        }

        void demoArray(String type, String var, String cells) {
            panel.demoArray(type, var, cells);
        }
    }
}

package il.lamerhav.dsviz.intellij;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;

/**
 * הכפתור "?": חלון שבו בוחרים הדרכה (סרטון קצר), והסרטון נפתח בדף נגן בדפדפן — עם בחירת מהירות.
 * הסרטונים: בתיקייה help של ההתקנה (אם יש), ואם לא — מנוגנים ישר מהאתר (כמו ב-Eclipse וב-VS Code; help/player.html).
 */
public final class Help {
    private Help() {}

    static final String TITLE = "הדרכה לשימוש";
    /** {מזהה, כותרת, תיאור} — כמו ב-vscode-extension/help.js וב-HelpDialog של Eclipse */
    static final String[][] ITEMS = {
        { "a", "01 · יצירת מבנה נתונים חדש", "שמים את הסמן במקום הנכון בקוד, לוחצים \"הוסף מבנה נתונים\", בוחרים מבנה, ערכים ושם — ורואים את הקוד שנוצר ואת ההרצה." },
        { "b", "02 · דוגמאות פתורות", "פותחים תרגיל מוכן מהרשימה, קוראים את הפתרון, ומריצים אותו בחלון ההדמיה — צעד אחרי צעד." },
        { "c", "03 · מבנה בתוך מבנה", "שלוש דוגמאות: מחסנית של תורים, רשימה של מחסניות ועץ של תורים — יצירת התרגיל וההרצה הוויזואלית." },
        { "d", "04 · מוסיפים התאמות לתרגיל", "אחרי שהתרגיל נוצר: מוסיפים ידנית מבנה נוסף, משתנה x ומערך — ורואים שגם הם מופיעים בהדמיה." },
        { "e", "05 · שליחת תרגיל והצעה לפתרון", "מעלים את דף התרגיל — צילום מסך (Ctrl+V), PDF או Word — ומקבלים קוד מוכן עם פעולה לכל סעיף; ולכל סעיף \"💡 הצעה לפתרון\" שנבדקה." },
    };

    public static void run(Project project) {
        ChooserDialog d = new ChooserDialog(project);
        if (!d.showAndGet()) return;
        try {
            BrowserUtil.browse(playerPage(d.chosen()));
        } catch (Exception e) {
            Say.error(project, "לא הצלחתי לפתוח את ההדרכה: " + e.getMessage(), TITLE);
        }
    }

    /** התיקייה help שההתקנה יצרה (עם הסרטונים), או null */
    static File installedHelp() {
        File dir = new File(Dsviz.installDir(), "help");
        return new File(dir, "player.html").isFile() ? dir : null;
    }

    /**
     * דף נגן להדרכה אחת: עותק של help/player.html בתיקייה זמנית, עם ההדרכה שנבחרה (בלי #, שלא תמיד עובר לדפדפן),
     * ועם &lt;base&gt; לתיקיית ההתקנה — אם יש בה את הסרטונים. אחרת הנגן מנגן מהאתר.
     */
    static File playerPage(String id) throws Exception {
        String html = Dsviz.resourceText("/resources/help/player.html");
        File installed = installedHelp();
        html = html.replace("var id = null ||", "var id = \"" + id.replaceAll("[^a-z]", "") + "\" ||");
        if (installed != null) html = html.replaceFirst("<head>", Matcher.quoteReplacement("<head><base href=\"" + installed.toURI() + "\">"));
        File dir = new File(System.getProperty("java.io.tmpdir"), "dsviz-help-intellij");
        dir.mkdirs();
        File out = new File(dir, "player-" + id + ".html");
        Files.write(out.toPath(), html.getBytes(StandardCharsets.UTF_8));
        return out;
    }

    /** משך כל סרטון (tutorials.json) */
    static String duration(String id) {
        try {
            Matcher m = Pattern.compile("\"" + id + "\"\\s*:\\s*\"([^\"]+)\"").matcher(Dsviz.resourceText("/resources/help/tutorials.json"));
            return m.find() ? m.group(1) : "";
        } catch (Exception e) {
            return "";
        }
    }

    static final class ChooserDialog extends Modern.Dialog {
        private JBList<String[]> list;

        ChooserDialog(Project project) {
            super(project);
            setTitle(TITLE);
            setOKButtonText("צפייה");
            setCancelButtonText("ביטול");
            init();
        }

        @Override
        protected JComponent createCenterPanel() {
            JPanel root = new JPanel(new BorderLayout(0, JBUI.scale(6)));
            root.add(Forms.header(TITLE, "בוחרים הדרכה — לכל אחת סרטון קצר שמראה בדיוק איפה לוחצים ומה נפתח."), BorderLayout.NORTH);
            list = new JBList<String[]>(ITEMS);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setSelectedIndex(0);
            list.setCellRenderer(new ColoredListCellRenderer<String[]>() {
                @Override
                protected void customizeCellRenderer(JList<? extends String[]> l, String[] it, int index, boolean selected, boolean focus) {
                    setIcon(Dsviz.HELP);
                    append(it[1], SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
                    String d = duration(it[0]);
                    if (!d.isEmpty()) append("   " + d, SimpleTextAttributes.GRAYED_ATTRIBUTES);
                    setToolTipText(it[2]);
                    setBorder(JBUI.Borders.empty(6, 8));
                }
            });
            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) doOKAction();
                }
            });
            JBScrollPane sp = new JBScrollPane(list);
            sp.setPreferredSize(new Dimension(JBUI.scale(460), JBUI.scale(200)));
            root.add(sp, BorderLayout.CENTER);
            javax.swing.JTextPane desc = Say.rtlText(ITEMS[0][2], 460);
            desc.setForeground(com.intellij.util.ui.UIUtil.getContextHelpForeground());
            root.add(desc, BorderLayout.SOUTH);
            list.addListSelectionListener(e -> {
                String[] it = list.getSelectedValue();
                if (it != null) desc.setText(it[2]);
            });
            Forms.rtl(root);
            return Modern.Dialog.wrap(root);
        }

        @Override
        public JComponent getPreferredFocusedComponent() {
            return list;
        }

        String chosen() {
            String[] it = list.getSelectedValue();
            return it == null ? "a" : it[0];
        }
    }
}

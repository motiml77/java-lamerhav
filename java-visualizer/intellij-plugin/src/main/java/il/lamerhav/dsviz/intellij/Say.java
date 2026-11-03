package il.lamerhav.dsviz.intellij;

import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;

import javax.swing.Icon;
import javax.swing.JList;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

/**
 * הודעות בעברית — כולן בחלון האחד של DSViz (Notice): מימין לשמאל, כותרת קצרה, תג קטן, כפתור ראשי כחול וכפתור שקט.
 * הודעה, שאלה כן/לא, ובחירה מרשימה. (בלי חלונות ההודעה הרגילים של IntelliJ ושל Swing — שם עברית מוצגת משמאל לימין.)
 */
public final class Say {
    private Say() {}

    public static void info(Project project, String text, String title) {
        Notice.show(project, spec(Modern.Kind.INFO, title, text));
    }

    public static void warn(Project project, String text, String title) {
        Notice.show(project, spec(Modern.Kind.WARN, title, text));
    }

    public static void error(Project project, String text, String title) {
        Notice.show(project, spec(Modern.Kind.ERROR, title, text));
    }

    // ------------------------------------------------------------------ בלי אנגלית בהודעות

    private static final java.util.regex.Pattern HEBREW = java.util.regex.Pattern.compile("[א-ת]");
    private static final java.util.regex.Pattern LATIN = java.util.regex.Pattern.compile("[A-Za-z]");

    static boolean hasHebrew(String s) {
        return s != null && HEBREW.matcher(s).find();
    }

    /**
     * הודעה לתלמיד בלי פרטים טכניים באנגלית: שורות בלי עברית (stack trace, פלט של Java) נמחקות, וזנב אנגלי אחרי ":"
     * ("לא הצלחתי לקרוא את הקובץ: /tmp/x (Permission denied)") נחתך. הודעה בלי עברית בכלל — "" (המתקשר שם משפט משלו).
     */
    static String clean(String msg) {
        if (msg == null) return "";
        StringBuilder out = new StringBuilder();
        for (String line : msg.split("\\r?\\n")) {
            String l = line.trim();
            if (!hasHebrew(l)) continue;
            int colon = l.lastIndexOf(": ");
            if (colon > 0) {
                String tail = l.substring(colon + 2);
                boolean technical = LATIN.matcher(tail).find() && (!hasHebrew(tail) || tail.matches("(?s)\\s*([A-Za-z]:)?[/\\\\].*"));   // אנגלית, או נתיב
                if (technical) l = l.substring(0, colon).trim() + ".";
            }
            if (out.length() > 0) out.append('\n');
            out.append(l);
        }
        return out.toString();
    }

    /** הסיבה בעברית לשגיאה של קבצים / תהליך (בלי ההודעה באנגלית של Java), או "" */
    static String reason(Throwable e) {
        if (e == null) return "";
        String m = String.valueOf(e.getMessage());
        String low = m.toLowerCase(java.util.Locale.ROOT);
        if (e instanceof java.nio.file.AccessDeniedException || low.contains("permission denied") || low.contains("access is denied")
                || low.contains("read-only") || low.contains("operation not permitted") || low.contains("cannot create") || low.contains("could not create"))
            return "אין הרשאה לכתוב בתיקייה הזאת";
        if (low.contains("no space left") || low.contains("not enough space") || low.contains("disk full")) return "אין מספיק מקום בדיסק";
        if (e instanceof java.nio.file.NoSuchFileException || e instanceof java.io.FileNotFoundException || low.contains("not a directory")
                || low.contains("no such file")) return "הקובץ או התיקייה לא נמצאו";
        return clean(m);
    }

    /** "לא הצלחתי X" + הסיבה בעברית אם יש ("לא הצלחתי ליצור את התרגיל: אין הרשאה לכתוב בתיקייה הזאת") */
    static String failed(String what, Throwable e) {
        String r = reason(e);
        return what + (r.isEmpty() ? "." : ": " + r);
    }

    /** שאלה: true אם בחרו yes */
    public static boolean ask(Project project, String text, String title, String yes, String no) {
        return new Notice(project, spec(Modern.Kind.QUESTION, title, text).buttons(yes, no)).ask();
    }

    /**
     * ההודעה: הכותרת של החלון היא החלק הראשון של title (עד " — "), והכותרת שבתוך החלון — החלק שאחריו, או השורה הראשונה של הטקסט
     * כשהיא קצרה. כך "שליחת תרגיל — אין חיבור לאינטרנט" נהיה חלון "שליחת תרגיל" עם הכותרת "אין חיבור לאינטרנט".
     */
    static Notice.Spec spec(Modern.Kind kind, String title, String text) {
        String t = title == null ? "" : title, body = text == null ? "" : text.trim();
        int dash = t.indexOf(" — ");
        String window = dash > 0 ? t.substring(0, dash) : t, heading = dash > 0 ? t.substring(dash + 3) : "";
        if (heading.isEmpty()) {
            int nl = body.indexOf('\n');
            String first = nl < 0 ? body : body.substring(0, nl);
            if (first.length() <= 60 && nl > 0) {
                heading = first;
                body = body.substring(nl).trim();
            } else if (body.length() <= 60 && !body.contains("\n")) {
                heading = body;
                body = "";
            } else heading = window;
        }
        return new Notice.Spec(kind, window.isEmpty() ? "DSViz" : window, heading).text(body);
    }

    /** בחירה מרשימה: האינדקס שנבחר, או -1 */
    public static int choose(Project project, String text, String title, Icon itemIcon, String[] items) {
        JBList<String> list = new JBList<String>(items);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setBackground(Modern.PANEL);
        list.setCellRenderer(new ColoredListCellRenderer<String>() {
            @Override
            protected void customizeCellRenderer(JList<? extends String> l, String value, int index, boolean selected, boolean focus) {
                if (itemIcon != null) setIcon(itemIcon);
                append(Modern.RLM + value);
                setBorder(JBUI.Borders.empty(5, 8));
                setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) return;
                DialogWrapper d = DialogWrapper.findInstance(list);
                if (d != null) d.clickDefaultButton();
            }
        });
        JBScrollPane sp = new JBScrollPane(list, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(new Modern.Round(Modern.LINE, 6, 0, 0));
        sp.setPreferredSize(new Dimension(JBUI.scale(560), Math.min(JBUI.scale(330), list.getPreferredSize().height + JBUI.scale(12))));
        Notice.Spec s = new Notice.Spec(Modern.Kind.QUESTION, title, title).text(text).buttons("פתיחה", "ביטול");
        s.width = 540;
        s.extra = sp;
        return new Notice(project, s).ask() ? list.getSelectedIndex() : -1;
    }

    /** טקסט עברי מימין לשמאל, שנשבר לשורות ברוחב קבוע */
    static JTextPane rtlText(String text, int width) {
        JTextPane p = new JTextPane();
        p.setEditable(false);
        p.setOpaque(false);
        p.setBorder(JBUI.Borders.empty());
        p.setFont(UIUtil.getLabelFont());
        p.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        StyledDocument doc = p.getStyledDocument();
        doc.putProperty(TextAttribute.RUN_DIRECTION, TextAttribute.RUN_DIRECTION_RTL);
        SimpleAttributeSet right = new SimpleAttributeSet();
        StyleConstants.setAlignment(right, StyleConstants.ALIGN_RIGHT);
        try {
            doc.insertString(0, text, null);
        } catch (javax.swing.text.BadLocationException ignored) {
            // לא יקרה
        }
        doc.setParagraphAttributes(0, doc.getLength(), right, false);
        int w = JBUI.scale(width);
        p.setSize(new Dimension(w, Short.MAX_VALUE));
        p.setPreferredSize(new Dimension(w, p.getPreferredSize().height));
        return p;
    }
}

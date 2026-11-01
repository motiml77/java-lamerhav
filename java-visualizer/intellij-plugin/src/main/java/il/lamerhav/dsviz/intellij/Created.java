package il.lamerhav.dsviz.intellij;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;

/**
 * אחרי שהתרגיל נוצר: הודעה קצרה אחת (בלון של IntelliJ, מימין לשמאל) — "✓ התרגיל נוצר", "3 סעיפים · הסמן בסעיף א", התיקייה
 * באותיות קטנות, הסעיפים שעונים עליהם במילים (רק השמות, והטקסט — ב"הצגה", בחלונית קטנה), ו-💡 כשיש הצעות לפתרון.
 * עם שגיאות קומפילציה — הודעת אזהרה עם "מעבר לשגיאה הראשונה". בלי חלונות מודאליים.
 */
final class Created {
    private Created() {}

    static final String TITLE_OK = "✓ התרגיל נוצר";
    static final String SHOW = "הצגה";
    static final String JUMP = "מעבר לשגיאה הראשונה";

    /** "סעיף ד: עונים במילים" / "סעיפים ג, ד: עונים במילים" ("" — אין) */
    static String wordsLine(List<String[]> others) {
        List<String> labels = new ArrayList<String>();
        for (String[] o : others == null ? new ArrayList<String[]>() : others) {
            String l = o[0] == null ? "" : o[0].trim().replaceAll("^סעיף\\s+", "").replaceAll("['׳]$", "");
            if (!l.isEmpty() && !labels.contains(l)) labels.add(l);
        }
        if (others == null || others.isEmpty()) return "";
        if (labels.isEmpty()) return others.size() == 1 ? "סעיף אחד: עונים במילים" : others.size() + " סעיפים: עונים במילים";
        return (labels.size() == 1 ? "סעיף " : "סעיפים ") + String.join(", ", labels) + ": עונים במילים";
    }

    /** "3 סעיפים · הסמן בסעיף א" / "סעיף אחד · הסמן במקום התשובה" / "אין סעיפים בקוד" */
    static String countLine(int tasks, String firstLabel) {
        if (tasks <= 0) return "אין סעיפים בקוד";
        if (tasks == 1) return "סעיף אחד · הסמן במקום התשובה";
        return tasks + " סעיפים · הסמן " + (firstLabel == null || firstLabel.isEmpty() ? "בסעיף הראשון" : "בסעיף " + firstLabel);
    }

    private static final Pattern MARK = Pattern.compile("כאן עונים סעיף\\s*([^'׳=\\s]+)");

    /** הסעיף שבו מחכה הסמן — לפי שורת הסימון "====כאן עונים סעיף א'====" שמעל השורה של הסמן ("" — לא נמצא) */
    static String firstLabel(String text, int line) {
        String[] ls = text.split("\n", -1);
        for (int i = Math.min(line, ls.length - 1); i >= 0 && i >= line - 3; i--) {
            Matcher m = MARK.matcher(Submitter.clean(ls[i]));
            if (m.find()) return m.group(1);
        }
        return "";
    }

    static String firstLabel(VirtualFile f, int line) {
        try {
            return firstLabel(new String(Files.readAllBytes(new File(f.getPath()).toPath()), StandardCharsets.UTF_8), line);
        } catch (Exception e) {
            return "";
        }
    }

    /** הכותרת, התוכן (HTML מימין לשמאל), הסוג והפעולות של ההודעה */
    static final class Text {
        /** הכותרת — עם סימן כיוון (RLM) בהתחלה, כדי שגם בכותרת ✓ יהיה מימין */
        String title = Modern.RLM + TITLE_OK, html = "";
        NotificationType type = NotificationType.INFORMATION;
        boolean show, jump;
    }

    /** muted — צבע הטקסט המשני (HTML, למשל ‎#646C7A) */
    static Text text(Submitter.Outcome out, String firstLabel, String muted) {
        Text t = new Text();
        List<String> lines = new ArrayList<String>();
        lines.add(esc(countLine(out.tasks, firstLabel)));
        if (!out.folder.isEmpty())
            lines.add("<span style='color:" + muted + "'><small>בתיקייה " + esc(out.folder) + "</small></span>");
        String words = wordsLine(out.others);
        if (!words.isEmpty()) {
            lines.add(esc(words));
            t.show = true;
        }
        if (!out.errors.isEmpty()) {
            t.type = NotificationType.WARNING;
            int n = out.errors.size();
            t.title = Modern.RLM + "התרגיל נוצר · " + (n == 1 ? "יש שגיאת קומפילציה אחת" : "יש " + n + " שגיאות קומפילציה");
            t.jump = true;
        } else if (out.tasks > 0 && !out.solutionStatus.isEmpty()) lines.add("💡 ליד כל סעיף — הצעה לפתרון");
        StringBuilder b = new StringBuilder("<div dir='rtl' style='text-align:right'>");
        for (String l : lines) b.append("<div>").append(Modern.RLM).append(l).append("</div>");
        t.html = b.append("</div>").toString();
        return t;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** ההודעה עצמה (בלון), עם "הצגה" (הסעיפים במילים) ו"מעבר לשגיאה הראשונה" */
    static Notification show(Project project, Submitter.Outcome out, File folder, String firstLabel) {
        Text t = text(out, firstLabel, "#" + ColorUtil.toHex(Modern.MUTED));
        Notification n = Dsviz.notify(project, t.title, t.html, t.type);
        if (t.show) {
            List<String[]> others = new ArrayList<String[]>(out.others);
            n.addAction(NotificationAction.create(SHOW, (e, notification) -> showOthers(project, others)));
        }
        if (t.jump) {
            List<String> errors = new ArrayList<String>(out.errors);
            n.addAction(NotificationAction.createSimple(JUMP, () -> jumpToFirstError(project, errors, out.files, folder)));
        }
        return n;
    }

    // ------------------------------------------------------------------ שגיאת הקומפילציה הראשונה

    private static final Pattern ERROR = Pattern.compile("^\\s*(.+?\\.java):(\\d+):");

    /** {שם הקובץ, השורה (מ-1)} מתוך "Button.java:42: ...", או null */
    static String[] firstError(List<String> errors) {
        for (String e : errors) {
            Matcher m = ERROR.matcher(e);
            if (m.find()) return new String[] { new File(m.group(1)).getName(), m.group(2) };
        }
        return null;
    }

    static void jumpToFirstError(Project project, List<String> errors, List<String> files, File folder) {
        String[] at = firstError(errors);
        if (at == null) return;
        File target = null;
        for (String f : files) if (new File(f).getName().equals(at[0])) target = new File(f);
        if (target == null && folder != null) target = new File(folder, at[0]);
        VirtualFile vf = target == null ? null : LocalFileSystem.getInstance().refreshAndFindFileByIoFile(target);
        if (vf != null) ProjectFiles.open(project, vf, Integer.parseInt(at[1]) - 1);
    }

    // ------------------------------------------------------------------ "הצגה": הטקסט של הסעיפים שעונים עליהם במילים

    /** התוכן של החלונית: לכל סעיף — השם, והטקסט (עברית מימין לשמאל, קוד משמאל לימין בגופן קוד) */
    static JComponent othersPanel(List<String[]> others) {
        JPanel col = Modern.panel(null);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(JBUI.Borders.empty(12, 14));
        int w = 400;
        for (int i = 0; i < others.size(); i++) {
            String[] o = others.get(i);
            if (i > 0) {
                col.add(Box.createVerticalStrut(JBUI.scale(10)));
                JComponent r = Modern.rule();
                r.setAlignmentX(Component.RIGHT_ALIGNMENT);
                col.add(r);
                col.add(Box.createVerticalStrut(JBUI.scale(10)));
            }
            String label = o[0] == null || o[0].trim().isEmpty() ? "סעיף" : o[0].trim().startsWith("סעיף") ? o[0].trim() : "סעיף " + o[0].trim();
            JComponent head = Modern.label(label + " · עונים במילים", 13, Font.BOLD, Modern.INK);
            JPanel hr = new JPanel(new BorderLayout());
            hr.setOpaque(false);
            hr.add(head, BorderLayout.LINE_START);
            hr.setAlignmentX(Component.RIGHT_ALIGNMENT);
            hr.setMaximumSize(new Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
            col.add(hr);
            col.add(Box.createVerticalStrut(JBUI.scale(6)));
            JPanel body = Modern.prose(o[1] == null ? "" : o[1], w, 13);
            body.setAlignmentX(Component.RIGHT_ALIGNMENT);
            col.add(body);
        }
        Modern.rtl(col);
        JBScrollPane sp = new JBScrollPane(col, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(JBUI.Borders.empty());
        Dimension pref = col.getPreferredSize();
        sp.setPreferredSize(new Dimension(JBUI.scale(w + 28) + 14, Math.min(JBUI.scale(300), pref.height + 2)));
        sp.getVerticalScrollBar().setUnitIncrement(JBUI.scale(16));
        Modern.rtl(sp);
        return sp;
    }

    static JBPopup showOthers(Project project, List<String[]> others) {
        JComponent c = othersPanel(others);
        JBPopup p = JBPopupFactory.getInstance().createComponentPopupBuilder(c, c)
                .setRequestFocus(true)
                .setFocusable(true)
                .setMovable(true)
                .setResizable(true)
                .setCancelOnClickOutside(true)
                .setCancelKeyEnabled(true)
                .createPopup();
        p.showCenteredInCurrentWindow(project);
        return p;
    }
}

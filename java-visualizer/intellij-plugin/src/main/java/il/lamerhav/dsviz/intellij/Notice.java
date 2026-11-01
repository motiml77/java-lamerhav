package il.lamerhav.dsviz.intellij;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;

/**
 * החלון האחד של ההודעות של DSViz (במקום Messages / JOptionPane / החלונות הישנים עם האיקס האדום הגדול):
 * לוח לבן מימין לשמאל, תג קטן וכותרת קצרה, משפט הסבר, כרטיסים (למשל שגיאה לכל כרטיס — עם קישור לשורה), וכפתור ראשי כחול
 * ולידו כפתור שקט. כל ההודעות של התוסף עוברות כאן (Say, שגיאות הקומפילציה של ההרצה הוויזואלית, אין אינטרנט, כיבוי).
 */
final class Notice extends Modern.Dialog {
    /** כרטיס: איפה ("Button.java · שורה 42" — קישור כשיש go), משפט בעברית, ושורת קוד (לא חובה) */
    static final class Card {
        final String where, text, code;
        final Runnable go;

        Card(String where, String text, String code, Runnable go) {
            this.where = where == null ? "" : where;
            this.text = text == null ? "" : text;
            this.code = code == null ? "" : code;
            this.go = go;
        }
    }

    /** מה מציגים */
    static final class Spec {
        Modern.Kind kind = Modern.Kind.INFO;
        /** הכותרת של החלון (בשורת הכותרת של מערכת ההפעלה) */
        String window = "DSViz";
        /** הכותרת הקצרה בתוך החלון */
        String heading = "";
        String text = "";
        final List<Card> cards = new ArrayList<Card>();
        /** "ועוד 3" — אחרי הכרטיסים */
        String more = "";
        /** רכיב נוסף (למשל רשימה לבחירה) */
        JComponent extra;
        String primary = "אישור";
        /** הכפתור השקט ("ביטול" / "סגירה"), או null — רק כפתור אחד */
        String secondary;
        /** מה קורה בכפתור הראשי (אחרי שהחלון נסגר) */
        Runnable onPrimary;
        int width = 440;

        Spec(Modern.Kind kind, String window, String heading) {
            this.kind = kind;
            this.window = window;
            this.heading = heading;
        }

        Spec text(String t) {
            text = t == null ? "" : t;
            return this;
        }

        Spec buttons(String primary, String secondary) {
            this.primary = primary;
            this.secondary = secondary;
            return this;
        }
    }

    /** כמה כרטיסים לכל היותר (ואחריהם "ועוד N") */
    static final int MAX_CARDS = 4;

    final Spec spec;
    private JComponent body;

    Notice(Project project, Spec spec) {
        super(project);
        this.spec = spec;
        setTitle(spec.window);
        setOKButtonText(spec.primary);
        if (spec.secondary != null) setCancelButtonText(spec.secondary);
        setResizable(false);
        init();
    }

    @Override
    protected Action[] createActions() {
        return spec.secondary == null ? new Action[] { getOKAction() } : new Action[] { getOKAction(), getCancelAction() };
    }

    @Override
    protected JComponent createCenterPanel() {
        body = content(spec, r -> {
            close(CANCEL_EXIT_CODE);
            ApplicationManager.getApplication().invokeLater(r);
        });
        return body;
    }

    @Override
    protected void doOKAction() {
        super.doOKAction();
        if (spec.onPrimary != null) ApplicationManager.getApplication().invokeLater(spec.onPrimary);
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        if (spec.extra instanceof javax.swing.JScrollPane && ((javax.swing.JScrollPane) spec.extra).getViewport().getView() instanceof JComponent)
            return (JComponent) ((javax.swing.JScrollPane) spec.extra).getViewport().getView();
        return spec.extra;
    }

    JComponent body() {
        return body;
    }

    /** הצגה (חלון מודאלי) */
    void open() {
        show();
    }

    /** הצגה: true אם לחצו על הכפתור הראשי */
    boolean ask() {
        return showAndGet();
    }

    static Notice show(Project project, Spec spec) {
        Notice n = new Notice(project, spec);
        n.open();
        return n;
    }

    /**
     * התוכן (גם לבדיקות ולצילומי מסך — בלי החלון): תג וכותרת, משפט, כרטיסים, "ועוד", ורכיב נוסף. after — סוגר את החלון ואז מריץ
     * (לחיצה על קישור בכרטיס).
     */
    static JPanel content(Spec s, Consumer<Runnable> after) {
        JPanel root = Modern.Dialog.body(new BorderLayout(JBUI.scale(12), 0));
        JLabel badge = new JLabel(Modern.badge(s.kind));
        badge.setVerticalAlignment(SwingConstants.TOP);
        badge.setBorder(JBUI.Borders.emptyTop(1));
        root.add(badge, BorderLayout.LINE_START);
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        int w = s.width;
        if (!s.heading.isEmpty()) add(col, Modern.text(s.heading, w, 15, Modern.INK, Font.BOLD), 0);
        if (!s.text.isEmpty()) add(col, Modern.text(s.text, w, 13, Modern.INK_2), 6);
        int shown = Math.min(MAX_CARDS, s.cards.size());
        JPanel cards = new JPanel();
        cards.setOpaque(false);
        cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
        for (int i = 0; i < shown; i++) {
            if (i > 0) cards.add(Box.createVerticalStrut(JBUI.scale(8)));
            JPanel c = card(s.cards.get(i), w - 26, after);
            c.setAlignmentX(Component.RIGHT_ALIGNMENT);
            cards.add(c);
        }
        if (shown > 0) {
            JComponent holder = cards;
            if (cards.getPreferredSize().height > JBUI.scale(420)) {
                JBScrollPane sp = new JBScrollPane(cards, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                sp.setBorder(JBUI.Borders.empty());
                sp.setOpaque(false);
                sp.getViewport().setOpaque(false);
                sp.setPreferredSize(new Dimension(JBUI.scale(w + 8), JBUI.scale(420)));
                holder = sp;
            }
            add(col, holder, 12);
        }
        String more = s.more.isEmpty() && s.cards.size() > MAX_CARDS ? "ועוד " + (s.cards.size() - MAX_CARDS) : s.more;
        if (!more.isEmpty()) add(col, Modern.label(more, 12, Font.PLAIN, Modern.MUTED), 8);
        if (s.extra != null) add(col, s.extra, 12);
        root.add(col, BorderLayout.CENTER);
        Modern.rtl(root);
        return root;
    }

    private static <T extends JComponent> T add(JPanel col, T c, int top) {
        if (col.getComponentCount() > 0 && top > 0) col.add(Box.createVerticalStrut(JBUI.scale(top)));
        c.setAlignmentX(Component.RIGHT_ALIGNMENT);
        col.add(c);
        return c;
    }

    /** כרטיס של שגיאה / פריט: הקישור למקום, המשפט, ושורת הקוד */
    static JPanel card(Card c, int width, Consumer<Runnable> after) {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        if (!c.where.isEmpty()) {
            JLabel where = c.go != null ? Modern.link(Modern.RLM + c.where, () -> after.accept(c.go))
                    : Modern.label(Modern.RLM + c.where, 12, Font.PLAIN, Modern.MUTED);
            where.setFont(Modern.font(12, Font.BOLD));
            where.setToolTipText(c.go != null ? "מעבר לשורה" : null);
            add(col, wrapRight(where), 0);
        }
        if (!c.text.isEmpty()) add(col, Modern.text(c.text, width, 13, Modern.INK), 4);
        if (!c.code.isEmpty()) {
            JComponent code = Modern.codeBlock(c.code, 12);
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false);
            wrap.add(code, BorderLayout.CENTER);
            wrap.putClientProperty(Modern.LTR, Boolean.TRUE);
            wrap.setMaximumSize(new Dimension(JBUI.scale(width), Integer.MAX_VALUE));
            add(col, wrap, 6);
        }
        JPanel card = Modern.card(col);
        card.setMaximumSize(new Dimension(JBUI.scale(width + 26), Integer.MAX_VALUE));
        return card;
    }

    /** רכיב קטן בצד ימין של שורה מלאה */
    private static JPanel wrapRight(JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(c, BorderLayout.LINE_START);
        p.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        return p;
    }
}

package il.lamerhav.dsviz.intellij;

import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

/**
 * הודעות בעברית — מימין לשמאל באמת (Messages של IntelliJ מציג טקסט עברי משמאל לימין, ומספרים "קופצים" למקום הלא נכון):
 * הודעה, שאלה כן/לא, ובחירה מרשימה.
 */
public final class Say {
    private Say() {}

    public static void info(Project project, String text, String title) {
        new MessageDialog(project, title, text, Messages.getInformationIcon(), "אישור", null).show();
    }

    public static void warn(Project project, String text, String title) {
        new MessageDialog(project, title, text, Messages.getWarningIcon(), "אישור", null).show();
    }

    public static void error(Project project, String text, String title) {
        new MessageDialog(project, title, text, Messages.getErrorIcon(), "אישור", null).show();
    }

    /** שאלה: true אם בחרו yes */
    public static boolean ask(Project project, String text, String title, String yes, String no) {
        return new MessageDialog(project, title, text, Messages.getQuestionIcon(), yes, no).showAndGet();
    }

    /** בחירה מרשימה: האינדקס שנבחר, או -1 */
    public static int choose(Project project, String text, String title, Icon itemIcon, String[] items) {
        ChooseDialog d = new ChooseDialog(project, title, text, itemIcon, items);
        return d.showAndGet() ? d.list.getSelectedIndex() : -1;
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

    static final class MessageDialog extends DialogWrapper {
        private final String text;
        private final Icon icon;
        private final boolean question;

        MessageDialog(Project project, String title, String text, Icon icon, String ok, String cancel) {
            super(project, true);
            this.text = text;
            this.icon = icon;
            this.question = cancel != null;
            setTitle(title);
            setOKButtonText(ok);
            if (cancel != null) setCancelButtonText(cancel);
            init();
        }

        @Override
        protected Action[] createActions() {
            return question ? new Action[] { getOKAction(), getCancelAction() } : new Action[] { getOKAction() };
        }

        @Override
        protected JComponent createCenterPanel() {
            JPanel p = new JPanel(new BorderLayout(JBUI.scale(14), 0));
            JLabel i = new JLabel(icon);
            i.setVerticalAlignment(SwingConstants.TOP);
            p.add(i, BorderLayout.LINE_START);
            JTextPane t = rtlText(text, 440);
            JComponent body = t;
            if (t.getPreferredSize().height > JBUI.scale(420)) {
                JBScrollPane sp = new JBScrollPane(t);
                sp.setBorder(JBUI.Borders.empty());
                sp.setPreferredSize(new Dimension(JBUI.scale(460), JBUI.scale(420)));
                body = sp;
            }
            p.add(body, BorderLayout.CENTER);
            p.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            return p;
        }
    }

    static final class ChooseDialog extends DialogWrapper {
        private final String text;
        private final Icon itemIcon;
        final JBList<String> list;

        ChooseDialog(Project project, String title, String text, Icon itemIcon, String[] items) {
            super(project, true);
            this.text = text;
            this.itemIcon = itemIcon;
            list = new JBList<String>(items);
            setTitle(title);
            setOKButtonText("פתיחה");
            setCancelButtonText("ביטול");
            init();
        }

        @Override
        protected JComponent createCenterPanel() {
            JPanel p = new JPanel(new BorderLayout(0, JBUI.scale(8)));
            p.add(rtlText(text, 600), BorderLayout.NORTH);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setSelectedIndex(0);
            list.setCellRenderer(new ColoredListCellRenderer<String>() {
                @Override
                protected void customizeCellRenderer(JList<? extends String> l, String value, int index, boolean selected, boolean focus) {
                    if (itemIcon != null) setIcon(itemIcon);
                    append("‏" + value);
                    setBorder(JBUI.Borders.empty(4, 6));
                }
            });
            list.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) doOKAction();
                }
            });
            JBScrollPane sp = new JBScrollPane(list, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            sp.setPreferredSize(new Dimension(JBUI.scale(620), Math.min(JBUI.scale(330), list.getPreferredSize().height + JBUI.scale(12))));
            p.add(sp, BorderLayout.CENTER);
            p.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            return p;
        }

        @Override
        public JComponent getPreferredFocusedComponent() {
            return list;
        }
    }
}

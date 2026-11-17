package il.lamerhav.dsviz.intellij;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;

import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import il.lamerhav.dsviz.ClassGen;

/**
 * שדה טיפוס עם רשימה נפתחת בקבוצות (החלון "צור מחלקה" — כמו TypeField של Eclipse ו-TypeBox של Visual Studio): כותבים חופשי, או
 * ▼ / חץ למטה ובוחרים. כותרת אפורה לכל קבוצה, הטיפוסים בגופן קוד, package באפור כשיש שני שמות זהים, וקבוצה ריקה — שורה אפורה.
 */
final class TypeBox extends JPanel {
    interface Groups {
        List<ClassGen.Group> get();
    }

    interface Pick {
        void picked(ClassGen.Item it);
    }

    final JBTextField text = new JBTextField();
    private final Groups groups;
    private final Pick onPick;
    private final Runnable onType, onEnter;
    private final DefaultListModel<Object> model = new DefaultListModel<Object>();
    private final JList<Object> list = new JList<Object>(model);
    private final JBScrollPane listScroll = new JBScrollPane(list);
    private Popup pop;
    private boolean internal;

    /** שורה שאינה טיפוס: כותרת קבוצה / "אין עדיין..." */
    private static final class Head {
        final String text;
        final boolean empty;

        Head(String text, boolean empty) {
            this.text = text;
            this.empty = empty;
        }
    }

    TypeBox(int width, Groups groups, Pick onPick, Runnable onType, Runnable onEnter) {
        super(new BorderLayout(JBUI.scale(2), 0));
        this.groups = groups;
        this.onPick = onPick;
        this.onType = onType;
        this.onEnter = onEnter;
        setOpaque(false);
        text.setFont(Forms.codeFont());
        text.setColumns(width / 9);
        JButton arrow = new JButton("▼");
        arrow.setFocusable(false);
        arrow.setToolTipText("רשימת הטיפוסים");
        arrow.setMargin(JBUI.insets(0, 4));
        arrow.setFont(arrow.getFont().deriveFont(arrow.getFont().getSize2D() - 3f));
        arrow.setPreferredSize(new Dimension(JBUI.scale(28), text.getPreferredSize().height));
        add(text, BorderLayout.CENTER);
        add(arrow, BorderLayout.LINE_END);
        arrow.addActionListener(e -> {
            if (isOpen()) close();
            else {
                text.requestFocusInWindow();
                open(true);
            }
        });
        text.getDocument().addDocumentListener(new DocumentAdapter() {
            @Override
            protected void textChanged(DocumentEvent e) {
                if (internal) return;
                onType.run();
                if (text.isFocusOwner()) SwingUtilities.invokeLater(() -> open(false));
            }
        });
        text.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int k = e.getKeyCode();
                if (k == KeyEvent.VK_DOWN || k == KeyEvent.VK_UP) {
                    e.consume();
                    if (!isOpen()) open(true);
                    move(k == KeyEvent.VK_DOWN ? 1 : -1);
                } else if (k == KeyEvent.VK_ENTER && !e.isControlDown()) {
                    e.consume();   // לא הכפתור "יצירת המחלקה"
                    Object v = isOpen() ? list.getSelectedValue() : null;
                    if (v instanceof ClassGen.Item) pick((ClassGen.Item) v);
                    else {
                        close();
                        onEnter.run();
                    }
                } else if (k == KeyEvent.VK_ESCAPE && isOpen()) {
                    e.consume();   // רק הרשימה נסגרת — לא החלון
                    close();
                }
            }
        });
        text.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                close();
            }
        });
        list.setFocusable(false);
        list.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        list.setCellRenderer(new ListCellRenderer<Object>() {
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean foc) {
                JPanel p = new JPanel(new BorderLayout(JBUI.scale(12), 0));
                p.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
                p.setOpaque(true);
                p.setBackground(UIUtil.getListBackground());
                if (v instanceof Head) {
                    Head h = (Head) v;
                    JBLabel lb = new JBLabel(h.text);
                    lb.setFont(lb.getFont().deriveFont(h.empty ? Font.ITALIC : Font.BOLD, lb.getFont().getSize2D() - 1f));
                    lb.setForeground(UIUtil.getContextHelpForeground());
                    p.setBorder(h.empty || i == 0 ? JBUI.Borders.empty(4, 12) : JBUI.Borders.compound(JBUI.Borders.customLineTop(JBColor.border()), JBUI.Borders.empty(6, 12, 2, 12)));
                    p.add(lb, BorderLayout.LINE_START);
                    return p;
                }
                ClassGen.Item it = (ClassGen.Item) v;
                JBLabel type = new JBLabel(it.type);
                type.setFont(Forms.codeFont());
                type.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
                JBLabel hint = new JBLabel(it.hint);
                hint.setForeground(sel ? UIUtil.getListSelectionForeground(true) : UIUtil.getContextHelpForeground());
                type.setForeground(sel ? UIUtil.getListSelectionForeground(true) : UIUtil.getListForeground());
                if (sel) p.setBackground(UIUtil.getListSelectionBackground(true));
                p.setBorder(JBUI.Borders.empty(3, 16, 3, 14));
                p.add(type, BorderLayout.LINE_START);
                p.add(hint, BorderLayout.LINE_END);
                return p;
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                if (i >= 0 && model.get(i) instanceof ClassGen.Item) pick((ClassGen.Item) model.get(i));
            }
        });
        list.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                if (i >= 0 && model.get(i) instanceof ClassGen.Item && list.getSelectedIndex() != i) list.setSelectedIndex(i);
            }
        });
        listScroll.setBorder(BorderFactory.createLineBorder(JBColor.border()));
        listScroll.setHorizontalScrollBarPolicy(JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    }

    String value() {
        return text.getText();
    }

    /** כתיבה בשדה בלי לפתוח את הרשימה */
    void set(String v) {
        internal = true;
        try {
            text.setText(v == null ? "" : v);
        } finally {
            internal = false;
        }
    }

    boolean isOpen() {
        return pop != null;
    }

    void close() {
        if (pop != null) {
            pop.hide();
            pop = null;
        }
    }

    private void move(int d) {
        int n = model.size(), i = list.getSelectedIndex();
        for (int k = 0; k < n; k++) {
            i = (i + d + n) % n;
            if (model.get(i) instanceof ClassGen.Item) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    private void pick(ClassGen.Item it) {
        close();
        set(it.type);
        text.requestFocusInWindow();
        onPick.picked(it);
    }

    /** הפריטים שיוצגו (לבדיקות): כותרות ופריטים לפי הסדר — כותרת כ-"# כותרת", שורה אפורה כ-"(טקסט)" */
    List<String> rows(boolean all) {
        fill(all);
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < model.size(); i++) {
            Object v = model.get(i);
            if (v instanceof Head) out.add(((Head) v).empty ? "(" + ((Head) v).text + ")" : "# " + ((Head) v).text);
            else out.add(((ClassGen.Item) v).type + (((ClassGen.Item) v).hint.isEmpty() ? "" : " " + ((ClassGen.Item) v).hint));
        }
        return out;
    }

    private int fill(boolean all) {
        String q = all ? "" : text.getText().trim().toLowerCase(Locale.ROOT);
        model.clear();
        int items = 0;
        for (ClassGen.Group g : groups.get()) {
            List<ClassGen.Item> its = new ArrayList<ClassGen.Item>();
            for (ClassGen.Item it : g.items) if (q.isEmpty() || it.type.toLowerCase(Locale.ROOT).contains(q)) its.add(it);
            if (!q.isEmpty() && its.isEmpty()) continue;
            model.addElement(new Head(g.title, false));
            if (its.isEmpty() && !g.empty.isEmpty()) model.addElement(new Head(g.empty, true));
            for (ClassGen.Item it : its) model.addElement(it);
            items += its.size();
        }
        return q.isEmpty() ? Math.max(items, 1) : items;
    }

    /** פותח את הרשימה מתחת לשדה (all — הכול; אחרת מסונן לפי מה שכתוב) */
    void open(boolean all) {
        int items = fill(all);
        close();
        if (items == 0 || !isShowing()) return;
        list.setVisibleRowCount(Math.min(model.size(), 15));
        list.clearSelection();
        listScroll.setPreferredSize(null);
        Dimension pref = listScroll.getPreferredSize();
        listScroll.setPreferredSize(new Dimension(Math.max(getWidth(), Math.max(pref.width + JBUI.scale(18), JBUI.scale(240))), pref.height + 2));
        java.awt.Point at = getLocationOnScreen();
        pop = PopupFactory.getSharedInstance().getPopup(this, listScroll, at.x, at.y + getHeight() + JBUI.scale(2));
        pop.show();
    }
}

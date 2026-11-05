package il.lamerhav.dsviz;

import unit4.collectionsLib.Theme;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * חלון "מאגר תרגילים — C#" (כמו ב-Eclipse, בסגנון של אתרי תרגול): סרגל צד מימין — ההתקדמות, חיפוש, סינון לפי קושי, ועץ של נושאים
 * (סמל, שם, כמה סומנו מתוך כמה) שנפתח לשאלות (נקודת קושי, שם, סימון ירוק למי שסיים); ובמרכז — הנוסח של השאלה שנבחרה עם הציורים.
 * למטה: "צור תרגיל" (או "פתח את התרגיל" אם כבר נוצר), "צור עותק חדש", "שאלה אקראית", "סגירה".
 */
final class BankVsWindow {
    /** הגדלים — בגודל התצוגה הנוכחי (BankZoom) */
    static int SIDEBAR = 330, TOPIC_H = 40, Q_H = 30, PREVIEW_W = 640;

    static BankVsWindow current;
    /** לבדיקות: מה מוצג בעץ ובתצוגה */
    final List<String> treeShown = new ArrayList<String>(), previewShown = new ArrayList<String>();

    final JFrame frame;
    final Set<String> open = new HashSet<String>();
    String levelFilter = "", search = "";
    BankStore.Question selected;
    final JPanel tree = new JPanel();
    final JPanel preview = new JPanel(new BorderLayout());
    final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 10));
    final JComponent stats;
    final List<JButton> chips = new ArrayList<JButton>();
    JScrollPane previewScroll;
    private final Runnable onChange;
    private final Map<String, Image> icons = new HashMap<String, Image>();

    static void open(String topic, String id) {
        if (current != null && current.frame.isDisplayable()) {
            current.frame.toFront();
            if (id != null) current.select(id);
            return;
        }
        current = new BankVsWindow();
        BankStore.Question first = null;
        for (BankStore.Question q : BankVs.store.questions) {
            if (id != null ? q.id.equals(id) : topic == null || q.topic.equals(topic)) {
                first = q;
                break;
            }
        }
        // בלי id מפורש (פתיחה רגילה) — הכול סגור ושום שאלה לא נבחרה; עם id (חזרה לשאלה מסוימת) — נפתח הנושא שלה
        if (id != null && first != null) current.select(first.id);
        else if (topic != null && first != null) current.select(first.id);
        else current.rebuildTree();
        current.frame.setVisible(true);
    }

    BankVsWindow() {
        frame = new JFrame(RtlDialog.rtl(BankVs.title()));
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        try {
            frame.setIconImage(ImageIO.read(BankVsWindow.class.getResource("/icons/bank.png")));
        } catch (Exception ignored) {
            // בלי סמל
        }
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(RtlDialog.PAGE);

        // ---- סרגל צד (מימין)
        JPanel side = new JPanel(new BorderLayout());
        side.setBackground(RtlDialog.PANEL);
        side.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, RtlDialog.LINE));
        side.setPreferredSize(new Dimension(SIDEBAR, 10));
        JPanel sideTop = BankVsPanel.column();
        sideTop.setBorder(BorderFactory.createEmptyBorder(14, 14, 8, 14));
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.RIGHT_ALIGNMENT);
        titleRow.add(RtlDialog.label("מאגר תרגילים", Theme.z(RtlDialog.bold().deriveFont(Font.BOLD, 18f)), RtlDialog.INK), BorderLayout.LINE_START);
        titleRow.add(BankZoom.controls(), BorderLayout.LINE_END);
        sideTop.add(titleRow);
        sideTop.add(RtlDialog.gap(2));
        sideTop.add(RtlDialog.label("ל-" + BankVs.ide(), RtlDialog.small(), RtlDialog.MUTED));
        sideTop.add(RtlDialog.gap(10));
        stats = new Stats();
        stats.setAlignmentX(Component.RIGHT_ALIGNMENT);
        sideTop.add(stats);
        sideTop.add(RtlDialog.gap(10));
        final JTextField searchField = new HintField("חיפוש שאלה…");
        searchField.setAlignmentX(Component.RIGHT_ALIGNMENT);
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { changed(); }

            public void removeUpdate(DocumentEvent e) { changed(); }

            public void changedUpdate(DocumentEvent e) { changed(); }

            void changed() {
                search = searchField.getText().trim();
                rebuildTree();
            }
        });
        searchField.putClientProperty("dsviz.id", "search");
        sideTop.add(searchField);
        sideTop.add(RtlDialog.gap(8));
        JPanel chipRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        chipRow.setOpaque(false);
        chipRow.setAlignmentX(Component.RIGHT_ALIGNMENT);
        String[][] levels = { { "", "הכול" }, { "easy", "קל" }, { "medium", "בינוני" }, { "hard", "קשה" } };
        for (final String[] l : levels) {
            final JButton c = RtlDialog.secondary(l[1]);
            c.putClientProperty("dsviz.level", l[0]);
            c.addActionListener(e -> {
                levelFilter = l[0];
                styleChips();
                rebuildTree();
            });
            chips.add(c);
            chipRow.add(c);
        }
        chipRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, chipRow.getPreferredSize().height));
        sideTop.add(chipRow);
        side.add(sideTop, BorderLayout.NORTH);
        tree.setLayout(new javax.swing.BoxLayout(tree, javax.swing.BoxLayout.Y_AXIS));
        tree.setBackground(RtlDialog.PANEL);
        JPanel treeHolder = new JPanel(new BorderLayout());
        treeHolder.setBackground(RtlDialog.PANEL);
        treeHolder.add(tree, BorderLayout.NORTH);
        JScrollPane ts = new JScrollPane(treeHolder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        ts.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, RtlDialog.LINE));
        ts.getVerticalScrollBar().setUnitIncrement(16);
        side.add(ts, BorderLayout.CENTER);
        page.add(side, BorderLayout.LINE_START);   // RTL: LINE_START = ימין

        // ---- התצוגה (במרכז)
        preview.setBackground(RtlDialog.PAGE);
        previewScroll = new JScrollPane(preview, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);   // קוד רחב (בהגדלה) — גוללים, לא נחתך
        previewScroll.setBorder(BorderFactory.createEmptyBorder());
        previewScroll.getVerticalScrollBar().setUnitIncrement(18);
        previewScroll.getViewport().setBackground(RtlDialog.PAGE);
        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(RtlDialog.PAGE);
        JPanel head = BankVsPanel.column();
        head.setBorder(BorderFactory.createEmptyBorder(14, 20, 6, 20));
        head.add(RtlDialog.label("בוחרים שאלה מהרשימה — ונוצר תרגיל מוכן " + BankVs.where() + ": פעולה לכל סעיף, " + (BankVs.ij ? "main" : "Main") + " שמריץ אותן, והצעה לפתרון לכל סעיף.",
                RtlDialog.small(), RtlDialog.MUTED));
        center.add(head, BorderLayout.NORTH);
        center.add(previewScroll, BorderLayout.CENTER);
        JPanel foot = new JPanel(new BorderLayout());
        foot.setBackground(RtlDialog.PAGE);
        foot.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, RtlDialog.LINE));
        buttons.setOpaque(false);
        buttons.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        foot.add(buttons, BorderLayout.CENTER);
        center.add(foot, BorderLayout.SOUTH);
        page.add(center, BorderLayout.CENTER);

        frame.setContentPane(page);
        BankZoom.install(frame);
        styleChips();
        rebuildTree();
        RtlDialog.rtl(page);
        Rectangle s = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        frame.setSize(Math.min(1080, s.width - 40), Math.min(780, s.height - 40));
        frame.setLocationRelativeTo(null);
        onChange = () -> SwingUtilities.invokeLater(() -> {
            if (!frame.isDisplayable()) return;
            stats.repaint();
            rebuildTree();
            if (selected != null) showQuestion(selected);
        });
        BankProgress.get().listen(onChange);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                BankProgress.get().unlisten(onChange);
                if (current == BankVsWindow.this) current = null;
            }
        });
    }

    void styleChips() {
        for (JButton c : chips) {
            boolean on = levelFilter.equals(c.getClientProperty("dsviz.level"));
            c.putClientProperty("FlatLaf.style", on
                    ? "background:#2F6FED; foreground:#ffffff; arc:999; borderWidth:0; focusWidth:0; innerFocusWidth:0; margin:2,10,2,10"
                    : "background:#ffffff; foreground:#3B4250; arc:999; borderColor:#CDD2DA; focusWidth:0; innerFocusWidth:0; margin:2,10,2,10; hoverBackground:#F2F3F6");
            c.setBackground(on ? RtlDialog.ACCENT : Color.WHITE);
            c.setForeground(on ? Color.WHITE : RtlDialog.INK_2);
            c.setFont(on ? RtlDialog.bold() : RtlDialog.ui());
        }
    }

    // ------------------------------------------------------------------ העץ

    List<BankStore.Question> visible(String topic) {
        List<BankStore.Question> out = new ArrayList<BankStore.Question>();
        String s = search.toLowerCase();
        for (BankStore.Question q : BankVs.store.questions) {
            if (!q.topic.equals(topic)) continue;
            if (!levelFilter.isEmpty() && !q.level.id.equals(levelFilter)) continue;
            if (!s.isEmpty() && !(q.title + " " + q.intro).toLowerCase().contains(s) && !BankVs.topicTitle(q.topic).contains(search)) continue;
            out.add(q);
        }
        return out;
    }

    void rebuildTree() {
        tree.removeAll();
        treeShown.clear();
        boolean any = false;
        for (BankStore.Topic t : BankVs.store.topics) {
            List<BankStore.Question> all = new ArrayList<BankStore.Question>();
            for (BankStore.Question q : BankVs.store.questions) if (q.topic.equals(t.id)) all.add(q);
            List<BankStore.Question> vis = visible(t.id);
            if (vis.isEmpty() && (!search.isEmpty() || !levelFilter.isEmpty())) continue;
            any = true;
            boolean isOpen = open.contains(t.id) || !search.isEmpty();
            int done = BankProgress.get().doneCount(all);
            tree.add(new TopicRow(t, isOpen, done, all.size()));
            treeShown.add("TOPIC " + t.id + " " + (isOpen ? "open" : "closed") + " " + done + "/" + all.size());
            if (isOpen) {
                for (BankStore.Question q : vis) {
                    boolean d = BankProgress.get().done(q.id);
                    tree.add(new QuestionRow(q, d));
                    treeShown.add("Q " + q.id + " " + q.level.id + (d ? " done" : ""));
                }
                if (vis.isEmpty()) {
                    JLabel none = RtlDialog.label("עוד אין שאלות בנושא הזה", RtlDialog.small(), RtlDialog.MUTED);
                    none.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 40));
                    tree.add(none);
                }
            }
        }
        if (!any) {
            JLabel none = RtlDialog.label("לא נמצאו שאלות", RtlDialog.ui(), RtlDialog.MUTED);
            none.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 16));
            tree.add(none);
            treeShown.add("EMPTY");
        }
        RtlDialog.rtl(tree);
        tree.revalidate();
        tree.repaint();
    }

    Image icon(String topic) {
        if (!icons.containsKey(topic)) {
            Image im = null;
            try {
                java.net.URL u = BankVsWindow.class.getResource("/icons/topics/" + topic + ".png");
                if (u != null) im = ImageIO.read(u);
            } catch (Exception ignored) {
                // בלי סמל
            }
            icons.put(topic, im);
        }
        return icons.get(topic);
    }

    /** שורה של נושא: חץ, סמל, שם, "2/3" ופס התקדמות קטן. לחיצה — פותחת / סוגרת */
    final class TopicRow extends JComponent {
        final BankStore.Topic t;
        final boolean isOpen;
        final int done, total;
        boolean hover;

        TopicRow(final BankStore.Topic t, boolean isOpen, int done, int total) {
            this.t = t;
            this.isOpen = isOpen;
            this.done = done;
            this.total = total;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(SIDEBAR - 20, TOPIC_H));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, TOPIC_H));
            setAlignmentX(Component.RIGHT_ALIGNMENT);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }

                @Override
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }

                @Override
                public void mouseReleased(MouseEvent e) { toggle(t.id); }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int w = getWidth(), h = getHeight();
            if (hover) {
                g.setColor(RtlDialog.SUNK);
                g.fillRoundRect(6, 2, w - 12, h - 4, 10, 10);
            }
            // מימין לשמאל: חץ, סמל, שם; משמאל — "2/3" ופס
            int x = w - 14;
            g.setColor(RtlDialog.MUTED);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int cy = h / 2;
            if (isOpen) g.drawPolyline(new int[] { x - 9, x - 5, x - 1 }, new int[] { cy - 2, cy + 2, cy - 2 }, 3);
            else g.drawPolyline(new int[] { x - 3, x - 7, x - 3 }, new int[] { cy - 4, cy, cy + 4 }, 3);
            x -= 16;
            Image im = icon(t.id);
            if (im != null) g.drawImage(im, x - 26, cy - 13, 26, 26, null);
            x -= 34;
            g.setFont(Theme.z(RtlDialog.bold().deriveFont(Font.BOLD, 14f)));
            g.setColor(RtlDialog.INK);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(t.title, x - fm.stringWidth(t.title), cy + fm.getAscent() / 2 - 2);
            // התקדמות
            String c = done + "/" + total;
            g.setFont(RtlDialog.small());
            fm = g.getFontMetrics();
            g.setColor(RtlDialog.MUTED);
            g.drawString(c, 16, cy + fm.getAscent() / 2 - 3);
            int bx = 16 + fm.stringWidth(c) + 6, bw = 38;
            g.setColor(RtlDialog.LINE);
            g.fillRoundRect(bx, cy - 2, bw, 4, 4, 4);
            if (total > 0 && done > 0) {
                g.setColor(RtlDialog.GREEN);
                g.fillRoundRect(bx, cy - 2, Math.max(4, bw * done / total), 4, 4, 4);
            }
            g.dispose();
        }
    }

    /** שורה של שאלה (מוזחת): קו העץ, נקודת קושי, השם, וסימון ירוק למי שסיים. לחיצה — מציגה את השאלה */
    final class QuestionRow extends JComponent {
        final BankStore.Question q;
        final boolean done;
        boolean hover;

        QuestionRow(final BankStore.Question q, boolean done) {
            this.q = q;
            this.done = done;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(SIDEBAR - 20, Q_H));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, Q_H));
            setAlignmentX(Component.RIGHT_ALIGNMENT);
            setToolTipText(done ? RtlDialog.rtl("סימון ירוק — כבר עשית את התרגיל (מסמנים בחלון \"השאלה\": \"סיימתי את התרגיל\")") : null);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }

                @Override
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }

                @Override
                public void mouseReleased(MouseEvent e) { select(q.id); }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight(), cy = h / 2;
            boolean sel = selected != null && selected.id.equals(q.id);
            if (sel || hover) {
                g.setColor(sel ? new Color(0xE8F0FE) : RtlDialog.SUNK);
                g.fillRoundRect(28, 1, w - 40, h - 2, 8, 8);
            }
            int x = w - 30;
            g.setColor(RtlDialog.LINE_2);
            g.drawLine(x, 0, x, h);   // קו העץ
            x -= 12;
            g.setColor(BankVsPanel.level(q.level));
            g.fillOval(x - 8, cy - 4, 8, 8);
            x -= 16;
            g.setFont(Theme.z(sel ? RtlDialog.bold().deriveFont(Font.BOLD, 12.5f) : RtlDialog.ui().deriveFont(12.5f)));
            g.setColor(RtlDialog.INK);
            FontMetrics fm = g.getFontMetrics();
            String t = q.title;
            int max = x - 56;
            while (fm.stringWidth(t) > max && t.length() > 3) t = t.substring(0, t.length() - 2);
            if (!t.equals(q.title)) t = t + "…";
            g.drawString(t, x - fm.stringWidth(t), cy + fm.getAscent() / 2 - 2);
            if (done) {   // עיגול ירוק עם ✓
                int cx = 42;
                g.setColor(RtlDialog.GREEN);
                g.fillOval(cx - 8, cy - 8, 16, 16);
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawPolyline(new int[] { cx - 4, cx - 1, cx + 4 }, new int[] { cy, cy + 3, cy - 3 }, 3);
            }
            g.dispose();
        }
    }

    void toggle(String topic) {
        if (!open.remove(topic)) open.add(topic);
        rebuildTree();
    }

    // ------------------------------------------------------------------ התצוגה

    void select(String id) {
        for (BankStore.Question q : BankVs.store.questions) {
            if (!q.id.equals(id)) continue;
            selected = q;
            open.add(q.topic);
            rebuildTree();
            showQuestion(q);
            return;
        }
    }

    void showQuestion(final BankStore.Question q) {
        preview.removeAll();
        previewShown.clear();
        JPanel col = BankVsPanel.column();
        col.setBorder(BorderFactory.createEmptyBorder(6, 20, 20, 20));
        col.add(BankVsPanel.titleRow(q, BankVs.topicTitle(q.topic)));
        col.add(RtlDialog.gap(12));
        // רוחב התצוגה לפי המקום שבאמת יש בחלון (גם בהגדלה — הטקסט יורד שורה במקום להיחתך)
        int room = frame.getWidth() > 0 ? frame.getWidth() - SIDEBAR - 110 : PREVIEW_W;
        col.add(BankVsPanel.build(q, Math.max(300, Math.min(PREVIEW_W, room)), null));
        preview.add(col, BorderLayout.NORTH);
        previewShown.add("TITLE " + q.title);
        for (BankStore.Section s : q.sections) previewShown.add("SECTION " + s.label + " " + s.text.replace("\n", " | "));
        for (BankStore.Figure f : q.figures) previewShown.add("FIGURE " + f.type);
        for (BankStore.Section s : q.sections) for (BankStore.Figure f : s.figures) previewShown.add("FIGURE " + f.type + " \"" + f.caption + "\"");
        if (!q.expectedOutput().isEmpty()) previewShown.add("EXPECTED " + q.expectedOutput().replace("\n", " | "));

        buttons.removeAll();
        final List<File> projects = BankVs.projects(q);
        if (projects.isEmpty()) {
            JButton create = RtlDialog.primary("צור תרגיל");
            create.addActionListener(e -> create(q));
            buttons.add(create);
            frame.getRootPane().setDefaultButton(create);
        } else {
            JButton openIt = RtlDialog.primary("פתח את התרגיל");
            openIt.setToolTipText(RtlDialog.rtl("הפרויקט " + projects.get(projects.size() - 1).getName() + " כבר ב-" + BankVs.ide() + " — ממשיכים מאיפה שעצרתם"));
            openIt.addActionListener(e -> {
                BankVs.openSolution();
                BankVsFloat.show(q);
                frame.dispose();
            });
            buttons.add(openIt);
            JButton again = RtlDialog.secondary("צור עותק חדש");
            again.addActionListener(e -> create(q));
            buttons.add(again);
            frame.getRootPane().setDefaultButton(openIt);
            previewShown.add("EXISTING " + projects.get(projects.size() - 1).getName());
        }
        JButton random = RtlDialog.secondary("שאלה אקראית");
        random.addActionListener(e -> {
            List<BankStore.Question> pool = new ArrayList<BankStore.Question>();
            for (BankStore.Question x : BankVs.store.questions) if (!BankProgress.get().done(x.id) && x != selected) pool.add(x);
            if (pool.isEmpty()) pool.addAll(BankVs.store.questions);
            if (!pool.isEmpty()) select(pool.get(new Random().nextInt(pool.size())).id);
        });
        buttons.add(random);
        if (AccessGate.isTeacher()) {   // מורה: חוזר לעמדת הניהול והקוד בכל פעם
            JButton manage = RtlDialog.secondary("עמדת ניהול");
            manage.addActionListener(e -> AccessGate.showTeacherPanel(frame));
            buttons.add(manage);
        }
        JButton close = RtlDialog.secondary("סגירה");
        close.addActionListener(e -> frame.dispose());
        buttons.add(close);
        RtlDialog.rtl(preview);
        RtlDialog.rtl(buttons);
        preview.revalidate();
        preview.repaint();
        // אם בכל זאת רחב מהחלון (קוד ארוך בהגדלה גדולה) — מתחילים מימין, כמו שקוראים
        SwingUtilities.invokeLater(() -> {
            javax.swing.JScrollBar h = previewScroll.getHorizontalScrollBar();
            h.setValue(h.getMaximum());
        });
        buttons.revalidate();
        buttons.repaint();
        SwingUtilities.invokeLater(() -> previewScroll.getVerticalScrollBar().setValue(0));
    }

    // ------------------------------------------------------------------ יצירה

    /** בונה את התרגיל (חלון קטן עם התקדמות), ואז: הפתרון ב-Visual Studio, החלון הצף של השאלה, והחלון הזה נסגר */
    void create(final BankStore.Question q) {
        String bad = BankVs.ij ? (java.nio.file.Files.isDirectory(BankVs.root) && java.nio.file.Files.isWritable(BankVs.root) ? null
                : "אין אפשרות לכתוב בתיקיית הקוד של הפרויקט: " + BankVs.root) : CsSolution.check(BankVs.root);
        if (bad != null) {
            RtlDialog.show(frame, BankVs.title(), RtlDialog.Kind.ERROR, "תיקיית התרגילים לא מוכנה", bad + (BankVs.ij ? "" : "\nמריצים שוב את ההתקנה (DSViz-Setup.exe) עם חבילת C#."));
            return;
        }
        final JDialog wait = new JDialog(frame, RtlDialog.rtl(BankVs.title()), false);
        JPanel body = BankVsPanel.column();
        body.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        body.setBackground(RtlDialog.PANEL);
        body.setOpaque(true);
        body.add(RtlDialog.label("יוצר את התרגיל…", Theme.z(RtlDialog.bold().deriveFont(Font.BOLD, 15f)), RtlDialog.INK));
        body.add(RtlDialog.gap(6));
        final JLabel stage = RtlDialog.label("מכין את הקבצים", RtlDialog.ui(), RtlDialog.INK_2);
        body.add(stage);
        body.add(RtlDialog.gap(10));
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setAlignmentX(Component.RIGHT_ALIGNMENT);
        bar.setPreferredSize(new Dimension(320, 6));
        body.add(bar);
        wait.setContentPane(body);
        RtlDialog.rtl(body);
        wait.pack();
        wait.setLocationRelativeTo(frame);
        wait.setVisible(true);
        for (Component c : buttons.getComponents()) c.setEnabled(false);
        new SwingWorker<ExercisePipeline.Result, String>() {
            @Override
            protected ExercisePipeline.Result doInBackground() throws Exception {
                return BankVs.create(q, new ExercisePipeline.Progress() {
                    public void say(String text) {
                        publish(text);
                    }

                    public boolean cancelled() {
                        return false;
                    }
                });
            }

            @Override
            protected void process(List<String> chunks) {
                stage.setText(RtlDialog.rtl(chunks.get(chunks.size() - 1).replace("…", "")));
            }

            @Override
            protected void done() {
                wait.dispose();
                for (Component c : buttons.getComponents()) c.setEnabled(true);
                ExercisePipeline.Result r;
                try {
                    r = get();
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    RtlDialog.show(frame, BankVs.title(), RtlDialog.Kind.ERROR, "התרגיל לא נוצר", String.valueOf(c.getMessage()));
                    return;
                }
                if (!r.errors.isEmpty()) RtlDialog.errors(frame, BankVs.title(), "התרגיל נוצר, אבל יש בו שגיאות קומפילציה", r.errors);
                BankVs.openSolution();
                BankVsFloat.show(q);
                if (r.focus != null) BankVs.openFile(r.focus, r.line + 1);
                frame.dispose();
            }
        }.execute();
    }

    // ------------------------------------------------------------------ ההתקדמות

    /** "ההתקדמות שלי": X מתוך Y, ופס לכל רמה */
    final class Stats extends JComponent {
        Stats() {
            setPreferredSize(new Dimension(SIDEBAR - 28, BankZoom.px(74)));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, BankZoom.px(74)));
        }

        private Font base(Font f) {
            return f.deriveFont((float) (f.getSize2D() / Theme.zoom));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            // הגדלה: כל הכרטיס גדל כיחידה אחת (המיקומים כאן קבועים — לכן מקטינים את הגופנים בחזרה ומגדילים את הציור)
            g.scale(Theme.zoom, Theme.zoom);
            int w = (int) Math.round(getWidth() / Theme.zoom);
            g.setColor(RtlDialog.SUNK);
            g.fillRoundRect(0, 0, w - 1, (int) Math.round(getHeight() / Theme.zoom) - 1, 12, 12);
            List<BankStore.Question> all = BankVs.store.questions;
            int done = BankProgress.get().doneCount(all);
            g.setFont(base(RtlDialog.bold()));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(RtlDialog.INK);
            String head = "ההתקדמות שלי";
            g.drawString(head, w - 12 - fm.stringWidth(head), 20);
            String c = done + " מתוך " + all.size();
            g.setFont(base(RtlDialog.small()));
            g.setColor(RtlDialog.MUTED);
            g.drawString(c, 12, 20);
            int y = 34;
            for (BankStore.Level l : BankStore.Level.values()) {
                List<BankStore.Question> qs = new ArrayList<BankStore.Question>();
                for (BankStore.Question q : all) if (q.level == l) qs.add(q);
                int d = BankProgress.get().doneCount(qs);
                fm = g.getFontMetrics();
                g.setColor(RtlDialog.INK_2);
                g.drawString(l.title, w - 12 - fm.stringWidth(l.title), y + 8);
                int bx = 60, bw = w - 12 - 50 - bx;
                g.setColor(RtlDialog.LINE);
                g.fillRoundRect(bx, y + 2, bw, 6, 6, 6);
                if (!qs.isEmpty() && d > 0) {
                    g.setColor(BankVsPanel.level(l));
                    g.fillRoundRect(bx + bw - Math.max(6, bw * d / qs.size()), y + 2, Math.max(6, bw * d / qs.size()), 6, 6, 6);
                }
                g.setColor(RtlDialog.MUTED);
                g.drawString(d + "/" + qs.size(), 12, y + 8);
                y += 13;
            }
            g.dispose();
        }
    }

    /** תיבת טקסט עם רמז אפור ("חיפוש שאלה…") */
    static final class HintField extends JTextField {
        final String hint;

        HintField(String hint) {
            this.hint = hint;
            setFont(RtlDialog.ui());
            putClientProperty("JTextField.placeholderText", hint);
            putClientProperty("FlatLaf.style", "arc:8");
        }
    }
}

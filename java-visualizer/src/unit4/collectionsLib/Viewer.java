package unit4.collectionsLib;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;

import unit4.collectionsLib.Model.Frame;
import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.Step;

/** החלון: מבנים, קוד, מחסנית קריאות ופלט — צעד אחר צעד */
final class Viewer extends JFrame {
    private static final Font UI = new Font("Dialog", Font.PLAIN, 14);
    private static final Font BIG = new Font("Dialog", Font.BOLD, 18);
    private static final Font MONO = new Font("Monospaced", Font.PLAIN, 14);
    private static final Color CODE_LINE = new Color(0xFFF3B0);

    static void open() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        Viewer v = new Viewer();
        v.setVisible(true);
    }

    private int cur = -1, known = 0;
    private boolean adjusting;

    private final JSlider slider = new JSlider(0, 0, 0);
    private final JLabel counter = new JLabel(" ");
    private final JLabel desc = new JLabel(" ");
    private final JLabel where = new JLabel(" ");
    private final JPanel banner = new JPanel(new BorderLayout(4, 2));
    private final StructPanel structs = new StructPanel();
    private final JTextArea code = new JTextArea();
    private final JLabel codeTitle = new JLabel(" ");
    private final DefaultListModel<String> callsModel = new DefaultListModel<String>();
    private final JList<String> calls = new JList<String>(callsModel);
    private final JLabel callsTitle = new JLabel("מחסנית הקריאות");
    private final JTextArea console = new JTextArea(5, 40);
    private final JCheckBox showChecks = new JCheckBox("להציג בדיקות (isEmpty, hasNext...)", true);
    private final JSlider speed = new JSlider(1, 10, 6);
    private final JButton playBtn = new JButton("הפעלה אוטומטית");
    private final Timer player;
    private String codeKey;
    private Object codeHighlight;

    private Viewer() {
        super("הרצה ויזואלית — מבני נתונים");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // --- סרגל שליטה
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        bar.add(button("התחלה", new Runnable() { public void run() { show(0); } }));
        bar.add(button("הקודם »", new Runnable() { public void run() { prev(); } }));
        bar.add(button("« הבא", new Runnable() { public void run() { next(); } }));
        bar.add(button("סוף", new Runnable() { public void run() { show(known - 1); } }));
        playBtn.setFont(UI);
        playBtn.setFocusable(false);
        playBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { togglePlay(); }
        });
        bar.add(playBtn);
        bar.add(new JLabel("  מהירות:"));
        speed.setPreferredSize(new Dimension(110, 24));
        speed.setFocusable(false);
        bar.add(speed);
        showChecks.setFont(UI);
        showChecks.setFocusable(false);
        bar.add(showChecks);
        counter.setFont(UI);
        bar.add(Box.createHorizontalStrut(12));
        bar.add(counter);

        slider.setFocusable(false);
        slider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (!adjusting && slider.getValue() != cur) show(slider.getValue());
            }
        });

        desc.setFont(BIG);
        where.setFont(UI);
        banner.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        banner.add(desc, BorderLayout.CENTER);
        banner.add(where, BorderLayout.SOUTH);

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(bar);
        north.add(slider);
        north.add(banner);

        // --- אזור המבנים + מקרא
        JPanel right = new JPanel(new BorderLayout());
        JScrollPane structScroll = new JScrollPane(structs);
        structScroll.getViewport().setBackground(Color.WHITE);
        structScroll.getVerticalScrollBar().setUnitIncrement(16);
        structScroll.getHorizontalScrollBar().setUnitIncrement(16);
        right.add(structScroll, BorderLayout.CENTER);
        right.add(legend(), BorderLayout.SOUTH);

        // --- קוד + מחסנית קריאות
        code.setEditable(false);
        code.setFont(MONO);
        code.setFocusable(false);
        code.setHighlighter(new javax.swing.text.DefaultHighlighter());
        JPanel codePanel = new JPanel(new BorderLayout());
        codeTitle.setFont(UI);
        codeTitle.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        codePanel.add(codeTitle, BorderLayout.NORTH);
        codePanel.add(new JScrollPane(code), BorderLayout.CENTER);

        calls.setFont(MONO);
        calls.setFocusable(false);
        JPanel callsPanel = new JPanel(new BorderLayout());
        callsTitle.setFont(UI);
        callsTitle.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        callsPanel.add(callsTitle, BorderLayout.NORTH);
        callsPanel.add(new JScrollPane(calls), BorderLayout.CENTER);

        JSplitPane left = new JSplitPane(JSplitPane.VERTICAL_SPLIT, codePanel, callsPanel);
        left.setResizeWeight(0.75);

        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        center.setResizeWeight(0.4);

        // --- פלט
        console.setEditable(false);
        console.setFont(MONO);
        JPanel consolePanel = new JPanel(new BorderLayout());
        JLabel ct = new JLabel("פלט (System.out) עד הצעד הזה");
        ct.setFont(UI);
        ct.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        consolePanel.add(ct, BorderLayout.NORTH);
        consolePanel.add(new JScrollPane(console), BorderLayout.CENTER);

        JSplitPane main = new JSplitPane(JSplitPane.VERTICAL_SPLIT, center, consolePanel);
        main.setResizeWeight(0.82);

        getContentPane().add(north, BorderLayout.NORTH);
        getContentPane().add(main, BorderLayout.CENTER);
        getContentPane().applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        for (JComponent c : new JComponent[] { code, console, calls }) c.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);

        keys();

        player = new Timer(600, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (cur >= known - 1) togglePlay();
                else next();
                player.setDelay(1700 - speed.getValue() * 160);
            }
        });
        new Timer(200, new ActionListener() {
            public void actionPerformed(ActionEvent e) { poll(); }
        }).start();

        setSize(1300, 820);
        setLocationRelativeTo(null);
        poll();
    }

    private JButton button(String text, final Runnable r) {
        JButton b = new JButton(text);
        b.setFont(UI);
        b.setFocusable(false);
        b.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { r.run(); }
        });
        return b;
    }

    private JPanel legend() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        p.add(swatch(StructPanel.NEW_C, "נוסף / נוצר"));
        p.add(swatch(StructPanel.READ_C, "נקרא"));
        p.add(swatch(StructPanel.CHANGED_C, "שונה"));
        p.add(swatch(new Color(0xFDE2E2), "הוצא"));
        p.add(swatch(StructPanel.CURRENT, "החוליה/הצומת האחרונים שנגעו בהם"));
        return p;
    }

    private JLabel swatch(final Color c, String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Dialog", Font.PLAIN, 12));
        l.setIcon(new javax.swing.Icon() {
            public void paintIcon(java.awt.Component comp, Graphics g, int x, int y) {
                g.setColor(c);
                g.fillRect(x, y, 14, 14);
                g.setColor(Color.DARK_GRAY);
                g.drawRect(x, y, 14, 14);
            }
            public int getIconWidth() { return 15; }
            public int getIconHeight() { return 15; }
        });
        return l;
    }

    private void keys() {
        JComponent root = getRootPane();
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        bind(root, im, "next", new Runnable() { public void run() { next(); } },
                KeyEvent.VK_LEFT, KeyEvent.VK_DOWN, KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_ENTER, KeyEvent.VK_SPACE);
        bind(root, im, "prev", new Runnable() { public void run() { prev(); } },
                KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_PAGE_UP, KeyEvent.VK_BACK_SPACE);
        bind(root, im, "first", new Runnable() { public void run() { show(0); } }, KeyEvent.VK_HOME);
        bind(root, im, "last", new Runnable() { public void run() { show(known - 1); } }, KeyEvent.VK_END);
        bind(root, im, "play", new Runnable() { public void run() { togglePlay(); } }, KeyEvent.VK_P);
    }

    private void bind(JComponent root, InputMap im, String name, final Runnable r, int... codes) {
        for (int k : codes) im.put(KeyStroke.getKeyStroke(k, 0), name);
        root.getActionMap().put(name, new AbstractAction() {
            public void actionPerformed(ActionEvent e) { r.run(); }
        });
    }

    // ------------------------------------------------------------ ניווט

    private void poll() {
        int n = Recorder.stepCount();
        if (n == known) return;
        known = n;
        adjusting = true;
        slider.setMaximum(Math.max(0, n - 1));
        adjusting = false;
        if (cur < 0 && n > 0) show(0);
        else updateCounter();
    }

    private boolean visible(int i) {
        return showChecks.isSelected() || Recorder.step(i).kind != Kind.CHECK;
    }

    private void next() {
        int j = cur + 1;
        while (j < known && !visible(j)) j++;
        if (j < known) show(j);
    }

    private void prev() {
        int j = cur - 1;
        while (j > 0 && !visible(j)) j--;
        if (j >= 0) show(j);
    }

    private void togglePlay() {
        if (player.isRunning()) {
            player.stop();
            playBtn.setText("הפעלה אוטומטית");
        } else {
            if (cur >= known - 1) show(0);
            player.setDelay(1700 - speed.getValue() * 160);
            player.start();
            playBtn.setText("עצירה");
        }
    }

    private void updateCounter() {
        counter.setText("צעד " + (cur + 1) + " מתוך " + known);
    }

    private void show(int i) {
        if (i < 0 || i >= known) return;
        cur = i;
        Step s = Recorder.step(i);
        adjusting = true;
        slider.setValue(i);
        adjusting = false;
        updateCounter();

        desc.setText(s.text);
        // תיאור שמתחיל בקוד (s.push(5)) מוצג משמאל לימין, תיאור בעברית — מימין לשמאל
        boolean hebrew = !s.text.isEmpty() && Character.UnicodeBlock.of(s.text.charAt(0)) == Character.UnicodeBlock.HEBREW;
        desc.setComponentOrientation(hebrew ? ComponentOrientation.RIGHT_TO_LEFT : ComponentOrientation.LEFT_TO_RIGHT);
        desc.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        banner.setBackground(bannerColor(s.kind));
        banner.setOpaque(true);
        if (s.where != null) {
            String m = s.where.method.equals("<init>") ? "בנאי" : "הפעולה " + s.where.method;
            where.setText("שורה " + s.where.line + "  בקובץ  " + s.where.file + "     ·     בתוך  " + m);
        } else {
            where.setText(" ");
        }
        structs.setStep(s);
        updateCode(s.where);
        updateCalls(s.calls);
        console.setText(Recorder.outputUpTo(s.outLen));
        console.setCaretPosition(console.getDocument().getLength());
    }

    private static Color bannerColor(Kind k) {
        switch (k) {
            case CREATE: case CHANGE: return new Color(0xE4F6E7);
            case READ: return new Color(0xFFF8D6);
            case CHECK: return new Color(0xF1F1F1);
            case OUTPUT: return new Color(0xDDF1FA);
            case NOTE: return new Color(0xEFE4FA);
            case ERROR: return new Color(0xFAD4D4);
            default: return new Color(0xDDDDDD);
        }
    }

    // ------------------------------------------------------------ קוד ומחסנית קריאות

    private void updateCode(Frame f) {
        if (f == null) return;
        String key = f.packagePath() + f.file;
        if (!key.equals(codeKey)) {
            codeKey = key;
            codeHighlight = null;
            List<String> lines = SourceFiles.lines(f);
            if (lines == null) {
                code.setText("קובץ המקור " + key + " לא נמצא.\n"
                        + "(צריך להריץ מתוך תיקיית הפרויקט כדי לראות את הקוד)");
                codeTitle.setText(f.file);
                return;
            }
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) b.append(String.format("%4d  ", i + 1)).append(lines.get(i)).append('\n');
            code.setText(b.toString());
            codeTitle.setText("הקוד:  " + f.file);
        }
        try {
            if (f.line < 1 || f.line > code.getLineCount()) return;
            final int start = code.getLineStartOffset(f.line - 1);
            int end = code.getLineEndOffset(f.line - 1);
            Highlighter h = code.getHighlighter();
            if (codeHighlight != null) h.removeHighlight(codeHighlight);
            codeHighlight = h.addHighlight(start, end, new LinePainter());
            SwingUtilities.invokeLater(new Runnable() {
                public void run() { scrollToCenter(start); }
            });
        } catch (BadLocationException ignored) {}
    }

    @SuppressWarnings("deprecation")
    private void scrollToCenter(int offset) {
        try {
            Rectangle r = code.modelToView(offset);
            if (r == null) return;
            Rectangle vis = code.getVisibleRect();
            int y = Math.max(0, r.y - vis.height / 2 + r.height / 2);
            code.scrollRectToVisible(new Rectangle(0, y, 1, vis.height));
        } catch (BadLocationException ignored) {}
    }

    /** צובע את כל רוחב השורה הנוכחית */
    private static final class LinePainter implements Highlighter.HighlightPainter {
        @SuppressWarnings("deprecation")
        public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
            try {
                Rectangle r = c.modelToView(p0);
                g.setColor(CODE_LINE);
                g.fillRect(0, r.y, c.getWidth(), r.height);
                g.setColor(new Color(0xE0B000));
                g.fillRect(0, r.y, 5, r.height);
            } catch (BadLocationException ignored) {}
        }
    }

    private void updateCalls(List<Frame> frames) {
        callsModel.clear();
        for (Frame f : frames) callsModel.addElement(f.label());
        callsTitle.setText("מחסנית הקריאות  (" + frames.size() + ")  —  הקריאה הפעילה למעלה");
        if (!frames.isEmpty()) calls.setSelectedIndex(0);
    }
}

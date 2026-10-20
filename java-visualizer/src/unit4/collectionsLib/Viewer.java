package unit4.collectionsLib;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;

import unit4.collectionsLib.Model.Frame;
import unit4.collectionsLib.Model.Kind;
import unit4.collectionsLib.Model.Step;

/**
 * החלון של "הרצה ויזואלית":
 * למעלה — הקוד (משמאל) והתצוגה הוויזואלית של המבנים (מימין),
 * למטה — מעקב משתנים, מחסנית הקריאות והפלט. מתקדמים צעד אחר צעד.
 */
final class Viewer extends JFrame {

    static void open() {
        Theme.install();
        Viewer v = new Viewer();
        v.setVisible(true);
    }

    // ------------------------------------------------------------ מצב

    /** האינדקסים (ב-Recorder) של הצעדים שמוצגים — כולם, או רק פעולות על מבנים */
    private final List<Integer> vis = new ArrayList<Integer>();
    private int known = 0, pos = -1;
    private boolean onlyOps, hasLines, adjusting;

    // ------------------------------------------------------------ רכיבים

    private final JSlider timeline = new JSlider(0, 0, 0);
    private final JLabel counter = new JLabel(" ");
    private final JButton prevBtn, nextBtn, playBtn;
    private final JSlider speed = new JSlider(1, 10, 6);
    private final JPanel modePanel;
    private final JToggleButton allBtn = new JToggleButton("כל שורה"), opsBtn = new JToggleButton("רק פעולות");
    private final Timer player;

    private final Chip chip = new Chip();
    private final JLabel desc = new JLabel(" ");
    private final JLabel where = new JLabel(" ");
    private final StepCard stepCard = new StepCard();

    private final JTextArea code = new JTextArea();
    private final Gutter gutter = new Gutter();
    private final JLabel codeSub = new JLabel(" ");
    private String codeKey;
    private Object codeHighlight;
    private int codeLine = -1;

    private final StructPanel structs = new StructPanel();
    private final JLabel zoomLabel = new JLabel("100%");

    private final DefaultTableModel varsModel = new DefaultTableModel(new Object[] { "משתנה", "ערך" }, 0) {
        @Override
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable vars = new JTable(varsModel);
    private final JLabel varsSub = new JLabel(" ");
    private final Set<Integer> changedRows = new HashSet<Integer>();

    private final DefaultListModel<String> callsModel = new DefaultListModel<String>();
    private final JList<String> calls = new JList<String>(callsModel);
    private final JLabel callsSub = new JLabel(" ");

    private final JTextArea console = new JTextArea();

    private final List<JSplitPane> splits = new ArrayList<JSplitPane>();
    private final List<Double> splitRatios = new ArrayList<Double>();

    private Viewer() {
        super("הרצה ויזואלית — מבני נתונים");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImage(appIcon());

        // --- סרגל השליטה
        prevBtn = button("הקודם", new Glyph(Glyph.PREV), false, "צעד קודם  (חץ ימינה)");
        nextBtn = button("הבא", new Glyph(Glyph.NEXT), true, "הצעד הבא  (חץ שמאלה / רווח)");
        nextBtn.setHorizontalTextPosition(SwingConstants.LEADING);
        playBtn = button("הפעלה", new Glyph(Glyph.PLAY), false, "התקדמות אוטומטית  (P)");
        playBtn.setHorizontalTextPosition(SwingConstants.LEADING);
        prevBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { prev(); } });
        nextBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { next(); } });
        playBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { togglePlay(); } });

        speed.setFocusable(false);
        speed.setOpaque(false);
        speed.setPreferredSize(new Dimension(90, 24));
        speed.setToolTipText("מהירות ההפעלה האוטומטית");
        JLabel speedLabel = label("מהירות", Theme.small, Theme.MUTED);

        JPanel nav = row(8);
        nav.add(prevBtn);
        nav.add(nextBtn);
        nav.add(Box(10));
        nav.add(playBtn);
        nav.add(speedLabel);
        nav.add(speed);

        timeline.setFocusable(false);
        timeline.setOpaque(false);
        timeline.setToolTipText("גוררים כדי לעבור לכל צעד");
        timeline.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if (!adjusting && timeline.getValue() != pos) showPos(timeline.getValue());
            }
        });

        counter.setFont(Theme.uiBold);
        counter.setForeground(Theme.TEXT);
        ButtonGroup g = new ButtonGroup();
        for (JToggleButton b : new JToggleButton[] { allBtn, opsBtn }) {
            g.add(b);
            b.setFocusable(false);
            b.setFont(Theme.small);
            b.putClientProperty("JButton.buttonType", "toolBarButton");
            b.putClientProperty("FlatLaf.style", "selectedBackground: #FFFFFF; selectedForeground: #2F6FED; arc: 10;"
                    + " margin: 3,10,3,10; toolbar.hoverBackground: #E3E7EE");
        }
        allBtn.setSelected(true);
        allBtn.setToolTipText("כל שורה בקוד היא צעד — עם המשתנים");
        opsBtn.setToolTipText("רק הצעדים שמשנים או קוראים מבנה (push, insert, setNext...)");
        allBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { setOnlyOps(false); } });
        opsBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { setOnlyOps(true); } });
        modePanel = new Pill(new Color(0xE6E9EF));
        modePanel.setLayout(new FlowLayout(FlowLayout.CENTER, 2, 2));
        modePanel.add(allBtn);
        modePanel.add(opsBtn);
        modePanel.setVisible(false);

        JPanel info = row(12);
        info.add(counter);
        info.add(modePanel);

        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setOpaque(false);
        bar.add(nav, BorderLayout.LINE_START);
        bar.add(timeline, BorderLayout.CENTER);
        bar.add(info, BorderLayout.LINE_END);

        // --- כרטיס הצעד הנוכחי
        desc.setFont(Theme.big);
        desc.setForeground(Theme.TEXT);
        desc.setHorizontalAlignment(SwingConstants.RIGHT);
        where.setFont(Theme.small);
        where.setForeground(Theme.MUTED);
        stepCard.setLayout(new BorderLayout(14, 0));
        stepCard.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 16));
        stepCard.add(chip, BorderLayout.LINE_START);
        stepCard.add(desc, BorderLayout.CENTER);
        stepCard.add(where, BorderLayout.LINE_END);

        JPanel north = new JPanel(new BorderLayout(0, 10));
        north.setOpaque(false);
        north.add(bar, BorderLayout.NORTH);
        north.add(stepCard, BorderLayout.CENTER);

        // --- הקוד
        code.setEditable(false);
        code.setBackground(Color.WHITE);
        code.setFont(Theme.mono);
        code.setForeground(Theme.TEXT);
        code.setFocusable(false);
        code.setMargin(new java.awt.Insets(6, 10, 6, 10));
        code.setHighlighter(new DefaultHighlighter());
        JScrollPane codeScroll = scroll(code);
        codeScroll.setRowHeaderView(gutter);
        Card codeCard = new Card("הקוד", codeSub, null, codeScroll);

        // --- התצוגה הוויזואלית
        JScrollPane structScroll = scroll(structs);
        structScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        structs.setZoomListener(new Runnable() {
            public void run() { zoomLabel.setText(structs.zoomText()); }
        });
        Card structCard = new Card("תצוגה ויזואלית", null, zoomTools(), structScroll);
        structCard.add(legend(), BorderLayout.SOUTH);

        // --- מעקב משתנים
        vars.setFocusable(false);
        vars.setRowSelectionAllowed(false);
        vars.setFillsViewportHeight(true);
        vars.getTableHeader().setReorderingAllowed(false);
        vars.getTableHeader().setFont(Theme.smallBold);
        vars.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        vars.getColumnModel().getColumn(0).setPreferredWidth(110);
        vars.getColumnModel().getColumn(0).setMaxWidth(220);
        vars.getColumnModel().getColumn(1).setPreferredWidth(300);
        vars.setDefaultRenderer(Object.class, new VarRenderer());
        Card varsCard = new Card("מעקב משתנים", varsSub, null, scroll(vars));

        // --- מחסנית הקריאות
        calls.setFocusable(false);
        calls.setFont(Theme.mono);
        calls.setFixedCellHeight(28);
        calls.setCellRenderer(new CallRenderer());
        JScrollPane callsScroll = scroll(calls);
        Card callsCard = new Card("מחסנית הקריאות", callsSub, null, callsScroll);

        // --- פלט
        console.setEditable(false);
        console.setBackground(Color.WHITE);
        console.setFont(Theme.mono);
        console.setForeground(Theme.TEXT);
        console.setMargin(new java.awt.Insets(6, 10, 6, 10));
        JScrollPane consoleScroll = scroll(console);
        Card outCard = new Card("פלט", label("\u200ESystem.out\u200E עד הצעד הזה", Theme.small, Theme.MUTED), null, consoleScroll);

        // --- סידור: למעלה קוד | תצוגה, למטה מעקב | קריאות | פלט
        JSplitPane top = split(JSplitPane.HORIZONTAL_SPLIT, codeCard, structCard, 0.6);
        JComponent bottom;
        if (Recorder.lineMode) {
            bottom = split(JSplitPane.HORIZONTAL_SPLIT, varsCard,
                    split(JSplitPane.HORIZONTAL_SPLIT, callsCard, outCard, 0.42), 0.36);
        } else {
            bottom = split(JSplitPane.HORIZONTAL_SPLIT, callsCard, outCard, 0.4);
        }
        JSplitPane main = split(JSplitPane.VERTICAL_SPLIT, top, bottom, 0.67);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(Theme.BG);
        root.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        root.add(north, BorderLayout.NORTH);
        root.add(main, BorderLayout.CENTER);
        setContentPane(root);

        root.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        // הקוד, הפלט והמבנים — משמאל לימין (מספרי השורות משמאל לקוד)
        for (JComponent c : new JComponent[] { codeScroll, consoleScroll, callsScroll, structScroll })
            c.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);

        keys();
        player = new Timer(600, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (pos >= vis.size() - 1) togglePlay();
                else next();
                player.setDelay(delay());
            }
        });
        new Timer(200, new ActionListener() {
            public void actionPerformed(ActionEvent e) { poll(); }
        }).start();

        // גודל החלון לפי המסך (גם במסכים קטנים ובהגדלת תצוגה של Windows)
        Rectangle scr = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int w = Math.min(1440, scr.width * 92 / 100), h = Math.min(900, scr.height * 92 / 100);
        setMinimumSize(new Dimension(Math.min(760, scr.width), Math.min(520, scr.height)));
        setSize(Math.max(w, Math.min(900, scr.width)), Math.max(h, Math.min(600, scr.height)));
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                for (int i = 0; i < splits.size(); i++) splits.get(i).setDividerLocation(splitRatios.get(i));
            }
        });
        poll();
    }

    // ------------------------------------------------------------ בניית רכיבים

    private static JPanel row(int gap) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEADING, gap, 0));
        p.setOpaque(false);
        return p;
    }

    private static Component Box(int w) {
        return javax.swing.Box.createHorizontalStrut(w);
    }

    private static JLabel label(String text, Font f, Color c) {
        JLabel l = new JLabel(text);
        l.setFont(f);
        l.setForeground(c);
        return l;
    }

    private static JButton button(String text, Icon icon, boolean primary, String tip) {
        JButton b = new JButton(text, icon);
        b.setFont(Theme.uiBold);
        b.setFocusable(false);
        b.setIconTextGap(8);
        b.setToolTipText(tip);
        b.putClientProperty("FlatLaf.style", primary
                ? "background: #2F6FED; foreground: #FFFFFF; hoverBackground: #2457C5; pressedBackground: #1E4AA8;"
                  + " borderWidth: 0; arc: 12; margin: 6,16,6,16"
                : "background: #FFFFFF; hoverBackground: #F0F3F8; borderColor: #D5DBE5; arc: 12; margin: 6,14,6,14");
        return b;
    }

    private static JScrollPane scroll(JComponent c) {
        JScrollPane s = new JScrollPane(c);
        s.setBorder(BorderFactory.createEmptyBorder());
        s.setViewportBorder(null);
        s.getViewport().setBackground(Color.WHITE);
        s.getVerticalScrollBar().setUnitIncrement(16);
        s.getHorizontalScrollBar().setUnitIncrement(16);
        return s;
    }

    private JSplitPane split(int orientation, Component a, Component b, double ratio) {
        JSplitPane s = new JSplitPane(orientation, a, b);
        s.setResizeWeight(ratio);
        s.setBorder(BorderFactory.createEmptyBorder());
        s.setOpaque(false);
        s.setContinuousLayout(true);
        splits.add(s);
        splitRatios.add(ratio);
        return s;
    }

    private JComponent zoomTools() {
        JPanel p = row(2);
        JButton out = small("−", "הקטנה"), in = small("+", "הגדלה"), fit = small("התאמה", "התאמה לגודל החלון");
        out.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { structs.zoomBy(1 / 1.2); } });
        in.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { structs.zoomBy(1.2); } });
        fit.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { structs.fit(); } });
        zoomLabel.setFont(Theme.small);
        zoomLabel.setForeground(Theme.MUTED);
        zoomLabel.setPreferredSize(new Dimension(42, 20));
        zoomLabel.setHorizontalAlignment(SwingConstants.CENTER);
        p.add(fit);
        p.add(in);
        p.add(zoomLabel);
        p.add(out);
        return p;
    }

    private static JButton small(String text, String tip) {
        JButton b = new JButton(text);
        b.setFont(Theme.smallBold);
        b.setFocusable(false);
        b.setToolTipText(tip);
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.putClientProperty("FlatLaf.style", "margin: 2,8,2,8; arc: 8");
        return b;
    }

    private JPanel legend() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEADING, 12, 6));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        p.add(swatch(StructPanel.NEW_C, null, "נוסף / נוצר"));
        p.add(swatch(StructPanel.READ_C, null, "נקרא"));
        p.add(swatch(StructPanel.CHANGED_C, null, "שונה"));
        p.add(swatch(StructPanel.GHOST_BG, StructPanel.GHOST, "הוצא"));
        p.add(swatch(Color.WHITE, StructPanel.CURRENT, "החוליה / הצומת הנוכחיים"));
        return p;
    }

    private static JLabel swatch(final Color fill, final Color line, String text) {
        JLabel l = label(text, Theme.small, Theme.MUTED);
        l.setIconTextGap(6);
        l.setIcon(new Icon() {
            public void paintIcon(Component c, Graphics g0, int x, int y) {
                Graphics2D g = (Graphics2D) g0.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(fill);
                g.fillRoundRect(x, y + 1, 14, 14, 5, 5);
                g.setColor(line != null ? line : StructPanel.BOX_LINE);
                g.setStroke(new BasicStroke(line != null ? 2f : 1f));
                g.drawRoundRect(x, y + 1, 14, 14, 5, 5);
                g.dispose();
            }
            public int getIconWidth() { return 15; }
            public int getIconHeight() { return 16; }
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
        bind(root, im, "first", new Runnable() { public void run() { showPos(0); } }, KeyEvent.VK_HOME);
        bind(root, im, "last", new Runnable() { public void run() { showPos(vis.size() - 1); } }, KeyEvent.VK_END);
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
        for (int i = known; i < n; i++) {
            Step s = Recorder.step(i);
            if (s.kind == Kind.LINE && !hasLines) {
                hasLines = true;
                modePanel.setVisible(true);
            }
            if (visible(s)) vis.add(i);
        }
        known = n;
        updateTimeline();
        if (pos < 0 && !vis.isEmpty()) showPos(0);
    }

    private boolean visible(Step s) {
        return !onlyOps || (s.kind != Kind.LINE && s.kind != Kind.CHECK);
    }

    /** מעבר בין "כל שורה" ל"רק פעולות" — נשארים בערך באותו מקום בהרצה */
    private void setOnlyOps(boolean only) {
        if (only == onlyOps) return;
        int cur = pos >= 0 && pos < vis.size() ? vis.get(pos) : 0;
        onlyOps = only;
        vis.clear();
        for (int i = 0; i < known; i++) if (visible(Recorder.step(i))) vis.add(i);
        int p = 0;
        for (int i = 0; i < vis.size(); i++) if (vis.get(i) <= cur) p = i;
        pos = -1;
        updateTimeline();
        showPos(p);
    }

    private void updateTimeline() {
        adjusting = true;
        timeline.setMaximum(Math.max(0, vis.size() - 1));
        if (pos >= 0) timeline.setValue(pos);
        adjusting = false;
        updateCounter();
    }

    private void next() {
        if (pos < vis.size() - 1) showPos(pos + 1);
    }

    private void prev() {
        if (pos > 0) showPos(pos - 1);
    }

    private int delay() {
        return 1700 - speed.getValue() * 160;
    }

    private void togglePlay() {
        if (player.isRunning()) {
            player.stop();
            playBtn.setText("הפעלה");
            playBtn.setIcon(new Glyph(Glyph.PLAY));
        } else {
            if (pos >= vis.size() - 1) showPos(0);
            player.setDelay(delay());
            player.start();
            playBtn.setText("עצירה");
            playBtn.setIcon(new Glyph(Glyph.PAUSE));
        }
    }

    private void updateCounter() {
        counter.setText(vis.isEmpty() ? "מחכה להרצה..." : "צעד " + (pos + 1) + " מתוך " + vis.size());
        prevBtn.setEnabled(pos > 0);
        nextBtn.setEnabled(pos < vis.size() - 1);
    }

    private void showPos(int p) {
        if (p < 0 || p >= vis.size()) return;
        pos = p;
        Step s = Recorder.step(vis.get(p));
        adjusting = true;
        timeline.setValue(p);
        adjusting = false;
        updateCounter();

        showStep(s);
        structs.setStep(s);
        updateCode(s.where);
        updateCalls(s.calls);
        if (Recorder.lineMode) updateVars(s);
        console.setText(Recorder.outputUpTo(s.outLen));
        console.setCaretPosition(console.getDocument().getLength());
    }

    /** כרטיס הצעד: תווית צבעונית (שורה 12 / פעולה / הדפסה...), התיאור, והמקום בקוד */
    private void showStep(Step s) {
        Color[] c = kindColors(s.kind);
        chip.set(kindName(s), c[0], c[1]);
        stepCard.stripe = c[1];
        String text = s.text;
        boolean codeText = false;
        if (s.kind == Kind.LINE) {
            int colon = text.indexOf(':');
            if (colon >= 0) text = text.substring(colon + 1).trim();
            text = text.replace("‪", "").replace("‬", "");
            codeText = true;
        }
        desc.setFont(codeText ? Theme.monoBig : Theme.big);
        desc.setText(codeText ? ltr(text) : text);
        desc.setToolTipText(text);
        boolean hebrew = !codeText && !text.isEmpty() && Character.UnicodeBlock.of(text.charAt(0)) == Character.UnicodeBlock.HEBREW;
        desc.setComponentOrientation(hebrew || !codeText ? ComponentOrientation.RIGHT_TO_LEFT : ComponentOrientation.LEFT_TO_RIGHT);
        desc.setHorizontalAlignment(SwingConstants.RIGHT);
        if (s.where != null) {
            where.setText("בתוך " + method(s.where) + "   ·   " + lrm(s.where.file));
        } else {
            where.setText(" ");
        }
        stepCard.repaint();
    }

    private static String kindName(Step s) {
        switch (s.kind) {
            case LINE: return s.where != null ? "שורה " + s.where.line : "שורה";
            case CREATE: return "נוצר";
            case CHANGE: return "שינוי";
            case READ: return "קריאה";
            case CHECK: return "בדיקה";
            case OUTPUT: return "הדפסה";
            case NOTE: return "הערה";
            case ERROR: return "שגיאה";
            case END: return "סיום";
            default: return "צעד";
        }
    }

    /** {רקע התווית, צבע הטקסט והפס} */
    private static Color[] kindColors(Kind k) {
        switch (k) {
            case LINE: return new Color[] { new Color(0xE3ECFF), new Color(0x2F5BD3) };
            case CREATE: case CHANGE: return new Color[] { new Color(0xDDF5E4), new Color(0x1E7B3A) };
            case READ: case CHECK: return new Color[] { new Color(0xFFF1C7), new Color(0x8A6100) };
            case OUTPUT: return new Color[] { new Color(0xDBF2F9), new Color(0x0B6E8A) };
            case NOTE: return new Color[] { new Color(0xEEE5FB), new Color(0x6B3FA0) };
            case ERROR: return new Color[] { new Color(0xFDE2E2), new Color(0xB42318) };
            default: return new Color[] { new Color(0xE9ECF1), new Color(0x4B5565) };
        }
    }

    // ------------------------------------------------------------ קוד

    private void updateCode(Frame f) {
        if (f == null) return;
        String key = f.packagePath() + f.file;
        if (!key.equals(codeKey)) {
            codeKey = key;
            codeHighlight = null;
            List<String> lines = SourceFiles.lines(f);
            if (lines == null) {
                code.setText("קובץ המקור " + key + " לא נמצא.\n(צריך להריץ מתוך תיקיית הפרויקט כדי לראות את הקוד)");
                codeSub.setText(f.file);
                codeLine = -1;
                gutter.revalidate();
                return;
            }
            StringBuilder b = new StringBuilder();
            // כל שורה "משמאל לימין" — כדי שמחרוזות בעברית לא יהפכו את סדר השורה
            for (int i = 0; i < lines.size(); i++) b.append(ltr(lines.get(i))).append(i < lines.size() - 1 ? "\n" : "");
            code.setText(b.toString());
            gutter.revalidate();
        }
        codeSub.setText(lrm(f.file) + "   ·   בתוך " + method(f));
        codeLine = f.line;
        gutter.repaint();
        try {
            Highlighter h = code.getHighlighter();
            if (codeHighlight != null) h.removeHighlight(codeHighlight);
            codeHighlight = null;
            if (f.line < 1 || f.line > code.getLineCount()) return;
            final int start = code.getLineStartOffset(f.line - 1);
            codeHighlight = h.addHighlight(start, code.getLineEndOffset(f.line - 1), new LinePainter());
            code.repaint();
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
            code.scrollRectToVisible(new Rectangle(vis.x, y, 1, vis.height));
        } catch (BadLocationException ignored) {}
    }

    /** צובע את כל רוחב השורה הנוכחית */
    private static final class LinePainter implements Highlighter.HighlightPainter {
        @SuppressWarnings("deprecation")
        public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c) {
            try {
                Rectangle r = c.modelToView(p0);
                g.setColor(Theme.CODE_LINE);
                g.fillRect(0, r.y, c.getWidth(), r.height);
            } catch (BadLocationException ignored) {}
        }
    }

    /** מספרי השורות משמאל לקוד, עם חץ בשורה הנוכחית */
    private final class Gutter extends JComponent {
        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(Theme.mono);
            int digits = Math.max(2, String.valueOf(code.getLineCount()).length());
            return new Dimension(fm.charWidth('0') * digits + 30, code.getPreferredSize().height);
        }

        @Override
        @SuppressWarnings("deprecation")
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Rectangle clip = g.getClipBounds();
            g.setColor(Theme.GUTTER);
            g.fillRect(clip.x, clip.y, clip.width, clip.height);
            g.setColor(Theme.BORDER);
            g.drawLine(getWidth() - 1, clip.y, getWidth() - 1, clip.y + clip.height);
            FontMetrics fm = g.getFontMetrics(Theme.mono);
            int n = code.getLineCount();
            for (int i = 0; i < n; i++) {
                Rectangle r;
                try { r = code.modelToView(code.getLineStartOffset(i)); } catch (BadLocationException e) { continue; }
                if (r == null || r.y + r.height < clip.y) continue;
                if (r.y > clip.y + clip.height) break;
                boolean cur = i + 1 == codeLine;
                if (cur) {
                    g.setColor(Theme.CODE_LINE);
                    g.fillRect(0, r.y, getWidth() - 1, r.height);
                    g.setColor(Theme.CODE_BAR);
                    Path2D.Double a = new Path2D.Double();
                    int cy = r.y + r.height / 2;
                    a.moveTo(4, cy - 5);
                    a.lineTo(10, cy);
                    a.lineTo(4, cy + 5);
                    a.closePath();
                    g.fill(a);
                }
                String s = String.valueOf(i + 1);
                g.setFont(cur ? Theme.mono.deriveFont(Font.BOLD) : Theme.mono);
                g.setColor(cur ? Theme.TEXT : new Color(0xA0A7B4));
                g.drawString(s, getWidth() - 10 - fm.stringWidth(s), r.y + fm.getAscent() + (r.height - fm.getHeight()) / 2);
            }
            g.dispose();
        }
    }

    /** "solve()" — מוקף בסימני כיוון כדי שהסוגריים לא יקפצו בתוך טקסט בעברית */
    private static String method(Frame f) {
        return f.method.equals("<init>") ? "בנאי" : lrm(f.method + "()");
    }

    private static String lrm(String s) {
        return "\u200E" + s + "\u200E";
    }

    /**
     * שורת קוד משמאל לימין גם כשיש בה מחרוזות בעברית: סימן כיוון (LRM) משני צידי כל מרכאה,
     * כדי שמספרים שאחרי "מחרוזת בעברית" לא "יימשכו" לתוכה ויהפכו את סדר השורה.
     */
    private static String ltr(String line) {
        return "‎" + line.replace("\"", "‎\"‎").replace("'", "‎'‎");
    }

    // ------------------------------------------------------------ משתנים וקריאות

    /** טבלת המשתנים. ערך שהשתנה מאז השורה הקודמת באותה פעולה — מסומן בצהוב */
    private void updateVars(Step s) {
        varsModel.setRowCount(0);
        changedRows.clear();
        if (s.vars == null) {
            varsSub.setText(" ");
            return;
        }
        Step prev = null;
        for (int i = s.index - 1; i >= 0 && prev == null; i--) {
            Step p = Recorder.step(i);
            if (p.kind == Kind.LINE && p.vars != s.vars) prev = p;
        }
        boolean sameFrame = prev != null && s.where != null && prev.where != null
                && prev.where.method.equals(s.where.method) && prev.calls.size() == s.calls.size();
        Map<String, String> old = new HashMap<String, String>();
        if (sameFrame && prev.vars != null) for (String[] v : prev.vars) old.put(v[0], v[1]);
        for (int i = 0; i < s.vars.size(); i++) {
            String[] v = s.vars.get(i);
            varsModel.addRow(new Object[] { v[0], ltr(v[1]) });
            if (sameFrame && !v[1].equals(old.get(v[0]))) changedRows.add(i);
        }
        String m = s.where == null ? "" : "בפעולה " + method(s.where);
        varsSub.setText(m + (changedRows.isEmpty() ? "" : "   ·   צהוב = השתנה עכשיו"));
    }

    private final class VarRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean focus, int row, int col) {
            super.getTableCellRendererComponent(t, v, false, false, row, col);
            boolean changed = changedRows.contains(row);
            setBackground(changed ? Theme.CHANGED : Color.WHITE);
            setForeground(Theme.TEXT);
            setFont(col == 0 ? Theme.mono.deriveFont(Font.BOLD) : Theme.mono);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            setHorizontalAlignment(col == 0 ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setToolTipText(v == null ? null : String.valueOf(v));
            return this;
        }
    }

    private void updateCalls(List<Frame> frames) {
        callsModel.clear();
        for (Frame f : frames) callsModel.addElement(f.label());
        callsSub.setText(frames.isEmpty() ? "אין קריאות פעילות" : frames.size() == 1 ? "קריאה אחת"
                : frames.size() + " קריאות   ·   הפעילה למעלה");
    }

    private static final class CallRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
            super.getListCellRendererComponent(list, value, index, false, false);
            boolean active = index == 0;
            setFont(active ? Theme.mono.deriveFont(Font.BOLD) : Theme.mono);
            setForeground(active ? Theme.ACCENT_DARK : Theme.MUTED);
            setBackground(active ? Theme.ACCENT_SOFT : Color.WHITE);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, active ? 4 : 0, 0, 0, Theme.ACCENT),
                    BorderFactory.createEmptyBorder(0, active ? 8 : 12, 0, 8)));
            return this;
        }
    }

    // ------------------------------------------------------------ רכיבים מצוירים

    /** כרטיס לבן עם פינות מעוגלות: כותרת, תת-כותרת, כלים, ותוכן */
    private static final class Card extends JPanel {
        Card(String title, JLabel sub, JComponent tools, JComponent content) {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(1, 1, 6, 1));
            JPanel head = new JPanel(new BorderLayout(10, 0));
            head.setOpaque(false);
            head.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                    BorderFactory.createEmptyBorder(8, 14, 8, 10)));
            JPanel titles = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 0));
            titles.setOpaque(false);
            titles.add(label(title, Theme.title, Theme.TEXT));
            if (sub != null) {
                sub.setFont(Theme.small);
                sub.setForeground(Theme.MUTED);
                titles.add(sub);
            }
            head.add(titles, BorderLayout.LINE_START);
            if (tools != null) head.add(tools, BorderLayout.LINE_END);
            add(head, BorderLayout.NORTH);
            JPanel body = new JPanel(new BorderLayout());
            body.setOpaque(false);
            body.setBorder(BorderFactory.createEmptyBorder(2, 4, 0, 4));
            body.add(content, BorderLayout.CENTER);
            add(body, BorderLayout.CENTER);
            setMinimumSize(new Dimension(160, 90));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Theme.CARD);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g.setColor(Theme.BORDER);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g.dispose();
        }
    }

    /** כרטיס הצעד הנוכחי: לבן, עם פס צבעוני בצד ימין לפי סוג הצעד */
    private static final class StepCard extends JPanel {
        Color stripe = Theme.ACCENT;

        StepCard() { setOpaque(false); }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width, Math.max(d.height, 56));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth() - 1, h = getHeight() - 1;
            g.setColor(Theme.CARD);
            g.fillRoundRect(0, 0, w, h, 14, 14);
            g.setColor(stripe);
            g.setClip(new java.awt.geom.RoundRectangle2D.Double(0, 0, w, h, 14, 14));
            g.fillRect(w - 5, 0, 6, h + 1);
            g.setClip(null);
            g.setColor(Theme.BORDER);
            g.drawRoundRect(0, 0, w, h, 14, 14);
            g.dispose();
        }
    }

    /** תווית צבעונית מעוגלת ("שורה 12", "הדפסה"...) */
    private static final class Chip extends JLabel {
        private Color bg = Theme.ACCENT_SOFT;

        Chip() {
            setFont(Theme.uiBold);
            setHorizontalAlignment(SwingConstants.CENTER);
            setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        }

        void set(String text, Color bg, Color fg) {
            this.bg = bg;
            setText(text);
            setForeground(fg);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width, 72), d.height);
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Dimension d = getPreferredSize();
            int y = (getHeight() - d.height) / 2;
            g.setColor(bg);
            g.fillRoundRect(0, y, getWidth(), d.height, d.height, d.height);
            g.dispose();
            super.paintComponent(g0);
        }
    }

    /** רקע מעוגל אפור — למתג "כל שורה / רק פעולות" */
    private static final class Pill extends JPanel {
        private final Color bg;

        Pill(Color bg) {
            this.bg = bg;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(bg);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g.dispose();
        }
    }

    /** סמלים מצוירים לכפתורים (חדים בכל הגדלה של המסך). בעברית "הבא" פונה שמאלה */
    private static final class Glyph implements Icon {
        static final int PREV = 0, NEXT = 1, PLAY = 2, PAUSE = 3;
        private final int kind;

        Glyph(int kind) { this.kind = kind; }

        public int getIconWidth() { return 14; }
        public int getIconHeight() { return 14; }

        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(c.isEnabled() ? c.getForeground() : new Color(0xB0B6C0));
            g.translate(x, y);
            switch (kind) {
                case PREV: case NEXT: {
                    g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    if (kind == NEXT) g.drawPolyline(new int[] { 9, 4, 9 }, new int[] { 2, 7, 12 }, 3);
                    else g.drawPolyline(new int[] { 5, 10, 5 }, new int[] { 2, 7, 12 }, 3);
                    break;
                }
                case PLAY: {
                    Path2D.Double p = new Path2D.Double();
                    p.moveTo(12, 1.5);
                    p.lineTo(2, 7);
                    p.lineTo(12, 12.5);
                    p.closePath();
                    g.fill(p);
                    break;
                }
                default:
                    g.fillRoundRect(2, 1, 4, 12, 2, 2);
                    g.fillRoundRect(8, 1, 4, 12, 2, 2);
            }
            g.dispose();
        }
    }

    /** סמל החלון: ריבוע כחול מעוגל עם שלושה תאים של מבנה */
    private static java.awt.Image appIcon() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.ACCENT);
        g.fillRoundRect(0, 0, 64, 64, 18, 18);
        g.setColor(Color.WHITE);
        for (int i = 0; i < 3; i++) g.fillRoundRect(9 + i * 16, 24, 13, 16, 4, 4);
        g.dispose();
        return img;
    }
}

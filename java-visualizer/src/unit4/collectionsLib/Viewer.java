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
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
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
 * משמאל — הקוד, ומתחתיו מעקב המשתנים והפלט; מימין, בכל הגובה — התצוגה הוויזואלית
 * (ובתרגיל רקורסיה גם לשונית "רקורסיה"). מתקדמים צעד אחר צעד.
 */
final class Viewer extends JFrame {

    static void open() {
        Timing.mark("Viewer.open התחיל");
        Theme.install();
        Timing.mark("Theme.install הסתיים");
        if (!License.allowViewer()) {   // נעילה לתשלום (כבויה כל עוד השרת לא הפעיל אותה): התוכנית ממשיכה לרוץ כרגיל, בלי החלון
            Recorder.stopVisual();
            Recorder.viewerReady.countDown();
            String ready = System.getProperty(Splash.READY_PROPERTY);
            if (ready != null) {
                try { new java.io.File(ready).createNewFile(); } catch (java.io.IOException ignored) {}
            }
            return;
        }
        Viewer v = new Viewer();
        Timing.mark("Viewer נבנה");
        v.setVisible(true);
        Timing.mark("החלון גלוי");
        Recorder.viewerReady.countDown();
        String ready = System.getProperty(Splash.READY_PROPERTY);
        if (ready != null) {   // מסמן לחלון ה"טוען" (בתהליך האב) שאפשר לסגור אותו
            try { new java.io.File(ready).createNewFile(); } catch (java.io.IOException ignored) {}
        }
    }

    // ------------------------------------------------------------ מצב

    /** האינדקסים (ב-Recorder) של הצעדים שמוצגים — כולם, או רק פעולות על מבנים */
    private final List<Integer> vis = new ArrayList<Integer>();
    private int known = 0, pos = -1;
    private boolean onlyOps, hasLines, adjusting;
    /** inFunction = התוכנית כבר נכנסה לפעולה (לא main); mainOnly = הסתיימה בלי פעולות; showMain = "גם main" */
    private boolean inFunction, mainOnly, showMain;
    /** לשונית "רקורסיה" רק בתרגיל ברקורסיה: בקובץ התרגיל כתוב "רקורסיה" (בהערה — כך נוצר תרגיל ברקורסיה) */
    private boolean checkedExercise, recursionExercise;
    private JComponent viewTabsBox;
    private final JToggleButton mainBtn = new SwitchButton("להציג גם main");
    private int angle;
    private boolean finished;
    private final Spin busy = new Spin(true);
    private final Spin overlay = new Spin(false);

    // ------------------------------------------------------------ רכיבים

    private final JSlider timeline = new JSlider(0, 0, 0);
    private final StepBadge counter = new StepBadge();
    private final JButton prevBtn, nextBtn, playBtn;
    private final JSlider speed = new JSlider(1, 10, 6);
    private final JPanel modePanel;
    private final JToggleButton allBtn = new SegButton("כל שורה"), opsBtn = new SegButton("רק פעולות");
    private final Timer player;


    private final CodePane code = new CodePane();
    private final Gutter gutter = new Gutter();
    private final JLabel codeSub = new JLabel(" ");
    private final CallPaint.Strip callStrip = new CallPaint.Strip();
    private String codeKey;
    private Object codeHighlight;
    private int codeLine = -1;

    private final StructPanel structs = new StructPanel();
    private final ObjDock objDock = new ObjDock();
    private final RecursionPanel recursion = new RecursionPanel();
    private final StructPanel miniStructs = new StructPanel();
    private final JPanel miniBox = new JPanel(new BorderLayout());
    private final JPanel viewPages = new JPanel(new java.awt.CardLayout());
    private final JToggleButton structTab = new SegButton("מבנים"), recTab = new SegButton("רקורסיה");
    private JComponent zoomBox;
    private boolean recursionShown, userChoseView;
    private final JLabel zoomLabel = new JLabel("100%");

    private final DefaultTableModel varsModel = new DefaultTableModel(new Object[] { "משתנה", "ערך" }, 0) {
        @Override
        public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable vars = new JTable(varsModel);
    private final JLabel varsSub = new JLabel(" ");
    private final Set<Integer> changedRows = new HashSet<Integer>();


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

        ButtonGroup g = new ButtonGroup();
        for (JToggleButton b : new JToggleButton[] { allBtn, opsBtn }) g.add(b);
        allBtn.setSelected(true);
        allBtn.setToolTipText("כל שורה בקוד היא צעד — עם המשתנים");
        opsBtn.setToolTipText("רק הצעדים שמשנים או קוראים מבנה (push, insert, setNext...)");
        allBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { setOnlyOps(false); } });
        opsBtn.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { setOnlyOps(true); } });
        modePanel = new Pill(new Color(0xEDF0F5), Theme.BORDER);
        modePanel.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
        modePanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        modePanel.add(allBtn);
        modePanel.add(opsBtn);
        modePanel.setVisible(false);

        // פס המידע: מספר הצעד, "כל שורה / רק פעולות", ומתג "להציג גם main" — בתוך מסגרת לבנה אחת
        JPanel info = new Pill(Color.WHITE, Theme.BORDER);
        info.setLayout(new FlowLayout(FlowLayout.LEADING, 0, 0));
        info.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        info.add(busy);
        info.add(counter);
        info.add(new Divider(modePanel));
        info.add(modePanel);
        info.add(new Divider(mainBtn));
        mainBtn.setToolTipText("להציג גם את הצעדים של main (בניית המבנים — המעטפת). בלי זה המעקב מתחיל בפעולה שכתבתם");
        mainBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showMain = mainBtn.isSelected();
                recursion.setHideMain(!showMain);
                rebuildVisible();
            }
        });
        mainBtn.setVisible(false);
        info.add(mainBtn);

        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setOpaque(false);
        bar.add(nav, BorderLayout.LINE_START);
        bar.add(timeline, BorderLayout.CENTER);
        bar.add(info, BorderLayout.LINE_END);

        JPanel north = new JPanel(new BorderLayout(0, 10));
        north.setOpaque(false);
        north.add(bar, BorderLayout.NORTH);

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
        // מעל הקוד: הפס של הקריאות לפעולות עזר (רק בהרצה שיש בה כאלה)
        JPanel codeBody = new JPanel(new BorderLayout());
        codeBody.setOpaque(false);
        callStrip.setVisible(false);
        codeBody.add(callStrip, BorderLayout.NORTH);
        codeBody.add(codeScroll, BorderLayout.CENTER);
        Card codeCard = new Card("הקוד", codeSub, null, codeBody);

        // --- התצוגה הוויזואלית
        JScrollPane structScroll = scroll(structs);
        structScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        structs.setZoomListener(new Runnable() {
            public void run() { zoomLabel.setText(structs.zoomText()); }
        });
        // שתי לשוניות: המבנים, ומעקב הרקורסיה (כל קריאה = כרטיס)
        JScrollPane recScroll = scroll(recursion);
        JPanel structPage = new JPanel(new BorderLayout());
        structPage.setOpaque(false);
        structPage.add(structScroll, BorderLayout.CENTER);
        JPanel structSouth = new JPanel(new BorderLayout());
        structSouth.setOpaque(false);
        structSouth.add(objDock, BorderLayout.NORTH);
        structSouth.add(legend(), BorderLayout.CENTER);
        structPage.add(structSouth, BorderLayout.SOUTH);
        JPanel recPage = new JPanel(new BorderLayout());
        recPage.setOpaque(false);
        recPage.add(recursion.header(), BorderLayout.NORTH);   // איזו רקורסיה זו, ואיפה אנחנו (וברקורסיה כפולה — עץ הקריאות)
        recPage.add(recScroll, BorderLayout.CENTER);
        // מתחת לכרטיסים: המבנים (מערך, מחסנית...) כמו שהם בשלב הזה — בקטן
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        miniStructs.setFitHeight(true);
        JScrollPane miniScroll = scroll(miniStructs);
        miniScroll.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        miniBox.setOpaque(false);
        miniBox.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        JLabel miniTitle = label("המבנים בשלב הזה", Theme.smallBold, Theme.MUTED);
        miniTitle.setBorder(BorderFactory.createEmptyBorder(4, 10, 0, 10));
        miniBox.add(miniTitle, BorderLayout.NORTH);
        miniBox.add(miniScroll, BorderLayout.CENTER);
        miniBox.setPreferredSize(new Dimension(100, 200));
        miniBox.setVisible(false);
        south.add(miniBox, BorderLayout.CENTER);
        south.add(recursionLegend(), BorderLayout.SOUTH);
        recPage.add(south, BorderLayout.SOUTH);
        viewPages.setOpaque(false);
        viewPages.add(structPage, "structs");
        viewPages.add(recPage, "recursion");
        JPanel viewTools = row(10);
        viewTools.add(viewTabsBox = viewTabs());
        viewTabsBox.setVisible(false);
        viewTools.add(zoomBox = zoomTools());
        Card structCard = new Card(null, null, viewTools, viewPages);
        recScroll.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);

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
        vars.setRowHeight(22);
        vars.setTableHeader(null);   // קומפקטי: "שם | ערך" בלי כותרת
        Card varsCard = new Card("מעקב משתנים", varsSub, null, scroll(vars));

        // --- פלט
        console.setEditable(false);
        console.setBackground(Color.WHITE);
        console.setFont(Theme.mono);
        console.setForeground(Theme.TEXT);
        console.setMargin(new java.awt.Insets(6, 10, 6, 10));
        JScrollPane consoleScroll = scroll(console);
        Card outCard = new Card("פלט", label("\u200ESystem.out\u200E עד הצעד הזה", Theme.small, Theme.MUTED), null, consoleScroll);

        // --- סידור: משמאל — הקוד, ומתחתיו מעקב משתנים ופלט; מימין — התצוגה הוויזואלית לכל הגובה
        JComponent under = Recorder.lineMode ? split(JSplitPane.HORIZONTAL_SPLIT, varsCard, outCard, 0.5) : outCard;
        JSplitPane left = split(JSplitPane.VERTICAL_SPLIT, codeCard, under, 0.7);
        JSplitPane main = split(JSplitPane.HORIZONTAL_SPLIT, left, structCard, 0.55);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBackground(Theme.BG);
        root.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        root.add(north, BorderLayout.NORTH);
        root.add(main, BorderLayout.CENTER);
        setContentPane(root);

        root.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        // הקוד, הפלט והמבנים — משמאל לימין (מספרי השורות משמאל לקוד)
        for (JComponent c : new JComponent[] { codeScroll, consoleScroll, structScroll })
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
        // ספינר טעינה: על כל החלון עד שמגיע הצעד הראשון, וקטן ליד המונה כל עוד התוכנית עוד רצה
        setGlassPane(overlay);
        overlay.setVisible(true);
        new Timer(40, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                angle = (angle + 12) % 360;
                if (overlay.isVisible()) overlay.repaint();
                if (busy.isVisible()) busy.repaint();
            }
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

    /** המתג "מבנים / רקורסיה" */
    private JComponent viewTabs() {
        ButtonGroup g = new ButtonGroup();
        for (JToggleButton b : new JToggleButton[] { structTab, recTab }) {
            g.add(b);
        }
        structTab.setSelected(true);
        structTab.setToolTipText("המבנים (מחסנית, תור, רשימה, עץ, מערך) בצעד הזה");
        recTab.setToolTipText("מעקב רקורסיה: כל קריאה לפעולה בכרטיס נפרד — מה קיבלה, ומה החזירה");
        structTab.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { userChoseView = true; showView(false); }
        });
        recTab.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) { userChoseView = true; showView(true); }
        });
        JPanel p = new Pill(new Color(0xEDF0F5), Theme.BORDER);
        p.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
        p.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));
        p.add(structTab);
        p.add(recTab);
        return p;
    }

    private static boolean isRecursionExercise(Frame main) {
        List<String> lines = SourceFiles.lines(main);
        if (lines == null) return false;
        for (String l : lines) if (l.contains("רקורסיה") || l.toLowerCase().contains("recursion")) return true;
        return false;
    }

    private void showView(boolean rec) {
        recursionShown = rec;
        (rec ? recTab : structTab).setSelected(true);
        ((java.awt.CardLayout) viewPages.getLayout()).show(viewPages, rec ? "recursion" : "structs");
        zoomBox.setVisible(!rec);
        if (rec && pos >= 0 && pos < vis.size()) showPos(pos);
    }

    /** תווית קטנה כמו הכדורים בכרטיסי הרקורסיה (#16 → 2) */
    private static JLabel miniChip(String text) {
        JLabel l = new JLabel("\u200E" + text + "\u200E");
        l.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        l.setFont(Theme.mono.deriveFont(Font.BOLD, 12f));
        l.setForeground(Color.WHITE);
        l.setOpaque(true);
        l.setBackground(new Color(0x6B87C9));
        l.setBorder(BorderFactory.createEmptyBorder(1, 7, 1, 7));
        return l;
    }

    private JPanel recursionLegend() {
        JPanel colors = new JPanel(new FlowLayout(FlowLayout.LEADING, 12, 4));
        colors.setOpaque(false);
        colors.add(swatch(new Color(0xE8F0FE), new Color(0x2F6FED), "פועלת עכשיו"));
        colors.add(swatch(new Color(0xF3F5F9), new Color(0xA9B4C6), "מחכה לתשובה"));
        colors.add(swatch(new Color(0xE3F5E9), new Color(0x2E9E5B), "סיימה והחזירה ערך"));
        JPanel help = new JPanel(new FlowLayout(FlowLayout.LEADING, 12, 0));
        help.setOpaque(false);
        help.add(label("שורה שקראה לפעולה:", Theme.small, Theme.MUTED));
        help.add(miniChip("#16 → 2"));
        help.add(label("(קריאה 16 החזירה 2)", Theme.small, Theme.MUTED));
        help.add(miniChip("#16 …"));
        help.add(label("(עוד רצה)", Theme.small, Theme.MUTED));
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER),
                BorderFactory.createEmptyBorder(0, 0, 4, 0)));
        p.add(colors);
        p.add(help);
        return p;
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
        p.add(swatch(StructPanel.NEW_C, null, "נוסף / נוצר"));
        p.add(swatch(StructPanel.READ_C, null, "נקרא"));
        p.add(swatch(StructPanel.CHANGED_C, null, "שונה"));
        p.add(swatch(StructPanel.GHOST_BG, StructPanel.GHOST, "הוצא"));
        p.add(swatch(Color.WHITE, StructPanel.CURRENT, "החוליה / הצומת הנוכחיים"));
        // זכויות יוצרים — שורה דקה מתחת למקרא, בקצה השני
        JPanel box = new JPanel(new BorderLayout());
        box.setOpaque(false);
        box.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        box.add(p, BorderLayout.CENTER);
        JLabel c = label(Theme.COPYRIGHT, Theme.small.deriveFont(10.5f), new Color(0xA0A8B8));
        c.setBorder(BorderFactory.createEmptyBorder(0, 10, 3, 10));
        JPanel line = new JPanel(new BorderLayout());
        line.setOpaque(false);
        line.add(c, BorderLayout.LINE_END);
        // "תרום לנו" — קטן, בזהב, בצד השני של שורת זכויות היוצרים
        JPanel give = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 0));
        give.setOpaque(false);
        give.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        give.add(new Donate());
        line.add(give, BorderLayout.LINE_START);
        box.add(line, BorderLayout.SOUTH);
        return box;
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
        boolean rebuild = false;
        for (int i = known; i < n; i++) {
            Step s = Recorder.step(i);
            if (s.kind == Kind.END || s.kind == Kind.ERROR) {   // התוכנית הסתיימה — נעלם הספינר
                finished = true;
                busy.setVisible(false);
                overlay.setVisible(false);
            }
            if (s.kind == Kind.LINE && !hasLines) {
                hasLines = true;
                modePanel.setVisible(true);
            }
            if (!checkedExercise && !s.calls.isEmpty()) {
                checkedExercise = true;
                recursionExercise = isRecursionExercise(s.calls.get(s.calls.size() - 1));
                viewTabsBox.setVisible(recursionExercise);
            }
            if (s.calls.size() >= 2 && !inFunction) {
                inFunction = true;
                mainBtn.setVisible(true);
            }
            // תוכנית שכולה ב-main (בלי פעולות) — מציגים את main
            if (s.kind == Kind.END && !inFunction && !mainOnly) {
                mainOnly = true;
                rebuild = true;
            }
            if (visible(s)) vis.add(i);
        }
        known = n;
        if (rebuild) {
            rebuildVisible();
            return;
        }
        updateTimeline();
        if (pos < 0 && !vis.isEmpty()) showPos(0);
        if (!vis.isEmpty()) overlay.setVisible(false);
    }

    /**
     * המעקב מתמקד בפעולות שהתלמיד כתב: הצעדים של main עצמו (בניית המבנים — המעטפת) לא מוצגים,
     * אלא אם לוחצים "גם main" או שאין בתוכנית פעולות בכלל. המבנים שנבנו ב-main מופיעים מוכנים.
     */
    private boolean visible(Step s) {
        if (onlyOps && (s.kind == Kind.LINE || s.kind == Kind.CHECK)) return false;
        boolean always = s.kind == Kind.END || s.kind == Kind.ERROR;
        return always || showMain || mainOnly || s.calls.size() >= 2;
    }

    /** מעבר בין "כל שורה" ל"רק פעולות" — נשארים בערך באותו מקום בהרצה */
    private void setOnlyOps(boolean only) {
        if (only == onlyOps) return;
        onlyOps = only;
        rebuildVisible();
    }

    /** בונה מחדש את רשימת הצעדים שמוצגים — ונשארים בערך באותו מקום בהרצה */
    private void rebuildVisible() {
        int cur = pos >= 0 && pos < vis.size() ? vis.get(pos) : 0;
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
        counter.set(vis.isEmpty() ? -1 : pos + 1, vis.size());
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

        structs.setStep(s);
        objDock.setStep(s);
        // תרגיל ברקורסיה: בפעם הראשונה שיש רקורסיה — עוברים לבד ללשונית "רקורסיה" (אפשר לחזור ל"מבנים")
        if (recursionExercise && !userChoseView && !recursionShown && RecursionPanel.recursive(s)) showView(true);
        if (recursionShown) {
            recursion.setStep(vis.get(p));
            miniStructs.setStep(s);
            boolean show = !s.views.isEmpty();
            if (miniBox.isVisible() != show) {
                miniBox.setVisible(show);
                miniBox.getParent().revalidate();
            }
        }
        updateCode(s.where);
        String inside = SelfViz.where(s);   // פעולה של אובייקט: "בתוך d1.birthday()"
        if (inside != null && s.where != null && codeSub.getText().contains("בתוך")) codeSub.setText(lrm(s.where.file) + "   ·   בתוך " + lrm(inside));
        CondViz.Marks marks = null;
        if (Recorder.lineMode && codeLine > 0) {
            try {
                marks = CondViz.marks(vis.get(p));
            } catch (RuntimeException e) {
                marks = null;   // בלי סימון התנאים
            }
        }
        code.marks = marks;
        // פעולה שקוראת לפעולת עזר: היציאה אליה והחזרה (לא מ-main)
        CallViz.State call = null;
        if (Recorder.lineMode && CallViz.any()) {
            if (!callStrip.isVisible()) {
                callStrip.setVisible(true);
                callStrip.getParent().revalidate();
            }
            try {
                call = CallViz.state(vis.get(p));
            } catch (RuntimeException e) {
                call = null;   // בלי סימון הקריאות
            }
            callStrip.set(call, s.calls);
        }
        code.call = call;
        code.fileKey = codeKey;
        code.repaint();
        if (Recorder.lineMode) updateVars(s);
        console.setText(Recorder.outputUpTo(s.outLen));
        console.setCaretPosition(console.getDocument().getLength());
    }

    // ------------------------------------------------------------ קוד

    private void updateCode(Frame f) {
        if (f == null) return;
        String key = f.packagePath() + f.file;
        if (!key.equals(codeKey)) {
            codeKey = key;
            code.getHighlighter().removeAllHighlights();   // הסימון של הקובץ הקודם — אחרת הוא נשאר על השורה הראשונה
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
            code.showCode(b.toString());
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

    /** אזור הקוד: טקסט צבעוני (מילים שמורות, טיפוסים, פעולות, משתנים, מחרוזות, הערות), בלי גלישת שורות */
    private static final class CodePane extends javax.swing.JTextPane {
        private final javax.swing.text.SimpleAttributeSet[] styles = new javax.swing.text.SimpleAttributeSet[9];
        /** התנאים בצעד הנוכחי (CondViz): התגית בשורת התנאי, והענף שרץ / שמדלגים עליו */
        CondViz.Marks marks;
        /** הקריאה לפעולת עזר בצעד הנוכחי (CallViz) — או null; fileKey = הקובץ שמוצג */
        CallViz.State call;
        String fileKey;

        @Override
        protected void paintComponent(Graphics g0) {
            if (call != null) {   // הרקע של השורה שמחכה — מתחת לטקסט
                Graphics2D g = (Graphics2D) g0.create();
                try {
                    g.setColor(getBackground());
                    g.fillRect(0, 0, getWidth(), getHeight());
                    CallPaint.background(g, this, call, fileKey);
                } catch (RuntimeException e) {
                    // בלי
                }
                g.dispose();
                setOpaque(false);
                super.paintComponent(g0);
                setOpaque(true);
            } else {
                super.paintComponent(g0);
            }
            if (marks == null && call == null) return;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            java.util.Map<Integer, Integer> used = new java.util.HashMap<Integer, Integer>();
            try {
                CondPaint.paint(g, this, marks, used);
            } catch (RuntimeException e) {
                // בלי סימון התנאים — הקוד עצמו מוצג כרגיל
            }
            try {
                CallPaint.foreground(g, this, call, fileKey, used);
            } catch (RuntimeException e) {
                // בלי סימון הקריאות
            }
            g.dispose();
        }

        CodePane() {
            for (int k = 0; k < styles.length; k++) {
                styles[k] = new javax.swing.text.SimpleAttributeSet();
                javax.swing.text.StyleConstants.setForeground(styles[k], Syntax.color(k));
                javax.swing.text.StyleConstants.setItalic(styles[k], k == Syntax.COMMENT);
            }
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            // בלי גלישה — גוללים לצדדים (אבל כשהקוד צר מהחלון — ממלאים את כל הרוחב)
            return getParent() == null || getUI().getPreferredSize(this).width <= getParent().getWidth();
        }

        void showCode(String text) {
            setText(text);
            javax.swing.text.StyledDocument doc = getStyledDocument();
            doc.setCharacterAttributes(0, doc.getLength(), styles[Syntax.PLAIN], true);
            for (int[] t : Syntax.tokens(text)) doc.setCharacterAttributes(t[0], t[1] - t[0], styles[t[2]], false);
            setCaretPosition(0);
        }

        int getLineCount() {
            return getDocument().getDefaultRootElement().getElementCount();
        }

        int getLineStartOffset(int line) throws BadLocationException {
            return getDocument().getDefaultRootElement().getElement(line).getStartOffset();
        }

        int getLineEndOffset(int line) throws BadLocationException {
            return getDocument().getDefaultRootElement().getElement(line).getEndOffset();
        }
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
        String s = "\u200E" + line.replace("\"", "\u200E\"\u200E").replace("'", "\u200E'\u200E");
        // רצף של מילים בעברית (הערה / מחרוזת) נקרא מימין לשמאל, בלי להפוך את שאר השורה
        return HEBREW_RUN.matcher(s).replaceAll("\u202B$0\u202C\u200E");
    }

    private static final java.util.regex.Pattern HEBREW_RUN = java.util.regex.Pattern.compile(
            "[\u0590-\u05FF](?:[\u0590-\u05FF \\-.,:;!?()0-9]*[\u0590-\u05FF])?");

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
            String name = v[0];
            SelfViz.Info self = name.equals("this") ? SelfViz.of(s) : null;
            if (self != null && self.self != null && !self.self.isEmpty() && !self.self.equals("this")) name = "this (" + self.self + ")";
            varsModel.addRow(new Object[] { name, ltr(v[1]) });
            if (sameFrame && !v[1].equals(old.get(v[0]))) changedRows.add(i);
        }
        String inside = SelfViz.where(s);
        String m = s.where == null ? "" : "בפעולה " + (inside != null ? lrm(inside) : method(s.where));
        varsSub.setText(m);
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
            if (title != null) titles.add(label(title, Theme.title, Theme.TEXT));
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

    /**
     * ספינר מסתובב. small = סימן קטן ליד המונה ("התוכנית עוד רצה");
     * אחרת — שכבה שקופה על כל החלון עם ספינר גדול וטקסט (לפני הצעד הראשון). לא חוסם עכבר.
     */
    private final class Spin extends JComponent {
        private final boolean small;

        Spin(boolean small) {
            this.small = small;
            setOpaque(false);
            if (small) setPreferredSize(new Dimension(20, 20));
            if (small) setToolTipText("התוכנית עוד רצה — צעדים חדשים ממשיכים להתווסף");
        }

        @Override
        public boolean contains(int x, int y) {
            return small && super.contains(x, y);   // השכבה הגדולה לא תופסת עכבר
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int d = small ? 16 : 46, x, y;
            if (small) {
                x = (getWidth() - d) / 2;
                y = (getHeight() - d) / 2;
            } else {
                g.setColor(new Color(255, 255, 255, 215));
                g.fillRect(0, 0, getWidth(), getHeight());
                x = (getWidth() - d) / 2;
                y = getHeight() / 2 - d - 6;
            }
            g.setStroke(new BasicStroke(small ? 2.4f : 5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0xDCE3F0));
            g.drawOval(x, y, d, d);
            g.setColor(Theme.ACCENT);
            g.draw(new java.awt.geom.Arc2D.Double(x, y, d, d, -angle, 100, java.awt.geom.Arc2D.OPEN));
            if (!small) {
                g.setFont(Theme.big);
                g.setColor(Theme.TEXT);
                String t = "מריץ את התוכנית...";
                g.drawString(t, (getWidth() - g.getFontMetrics().stringWidth(t)) / 2, y + d + 34);
            }
            g.dispose();
        }
    }

    /** רקע מעוגל (עם מסגרת דקה) — לפס המידע ולמתג "כל שורה / רק פעולות" */
    private static final class Pill extends JPanel {
        private final Color bg, line;

        Pill(Color bg, Color line) {
            this.bg = bg;
            this.line = line;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = Math.min(getHeight(), 16);
            g.setColor(bg);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            if (line != null) {
                g.setColor(line);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            }
            g.dispose();
        }
    }

    /** קו אנכי דק בין חלקי פס המידע — מוצג רק כשהחלק שאחריו מוצג */
    private static final class Divider extends JComponent {
        private final Component next;

        Divider(Component next) {
            this.next = next;
        }

        @Override
        public boolean isVisible() {
            return next.isVisible();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(21, 26);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(Theme.BORDER);
            g.fillRect(10, 4, 1, getHeight() - 8);
        }
    }

    private static Graphics2D smooth(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    /** "צעד 27 מתוך 50": המספר הנוכחי מודגש בכחול. כל חלק מצויר לבד (מימין לשמאל) — כדי שהמספרים לא יקפצו */
    private static final class StepBadge extends JComponent {
        private int pos = -1, total;

        void set(int pos, int total) {
            this.pos = pos;
            this.total = total;
            revalidate();
            repaint();
        }

        private String[] parts() {
            return pos < 0 ? new String[] { "מחכה להרצה..." } : new String[] { "צעד", String.valueOf(pos), "מתוך", String.valueOf(total) };
        }

        private Font fontOf(int i) {
            return pos >= 0 && i == 1 ? Theme.uiBold.deriveFont(Theme.uiBold.getSize2D() + 2f) : Theme.small;
        }

        @Override
        public Dimension getPreferredSize() {
            String[] p = parts();
            int w = 16;
            for (int i = 0; i < p.length; i++) w += getFontMetrics(fontOf(i)).stringWidth(p[i]) + (i > 0 ? 6 : 0);
            // רוחב קבוע לפי המספר הגדול ביותר — כדי שהפס לא "יקפוץ" בכל צעד
            if (pos >= 0) w += Math.max(0, getFontMetrics(fontOf(1)).stringWidth(String.valueOf(total)) - getFontMetrics(fontOf(1)).stringWidth(p[1]));
            return new Dimension(w, 30);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = smooth(g0);
            String[] p = parts();
            int x = getWidth() - 8, base = getHeight() / 2 + 5;
            for (int i = 0; i < p.length; i++) {
                Font f = fontOf(i);
                g.setFont(f);
                g.setColor(pos >= 0 && i == 1 ? Theme.ACCENT : pos >= 0 && i == 3 ? Theme.TEXT : Theme.MUTED);
                int w = g.getFontMetrics().stringWidth(p[i]);
                x -= w;
                g.drawString(p[i], x, base);
                x -= 6;
            }
            g.dispose();
        }
    }

    /** כפתור במתג מקטעים: הנבחר — לבן עם טקסט כחול ומסגרת; האחר — טקסט אפור (כהה במעבר עכבר) */
    private static final class SegButton extends JToggleButton {
        SegButton(String text) {
            super(text);
            setFocusable(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setOpaque(false);
            setFont(Theme.small);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(getFontMetrics(Theme.smallBold).stringWidth(getText()) + 28, 26);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = smooth(g0);
            int w = getWidth(), h = getHeight();
            boolean sel = isSelected();
            if (sel) {
                g.setColor(new Color(0, 0, 0, 18));   // צל עדין
                g.fillRoundRect(1, 2, w - 2, h - 2, 12, 12);
                g.setColor(Color.WHITE);
                g.fillRoundRect(0, 0, w - 1, h - 2, 12, 12);
                g.setColor(new Color(0xD3DAE6));
                g.drawRoundRect(0, 0, w - 1, h - 2, 12, 12);
            } else if (getModel().isRollover()) {
                g.setColor(new Color(0xE2E6EE));
                g.fillRoundRect(0, 0, w - 1, h - 2, 12, 12);
            }
            Font f = sel ? Theme.smallBold : Theme.small;
            g.setFont(f);
            g.setColor(sel ? Theme.ACCENT : getModel().isRollover() ? Theme.TEXT : Theme.MUTED);
            java.awt.FontMetrics fm = g.getFontMetrics();
            g.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h - 2 + fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }

    /** מתג הפעלה (כמו בטלפון) עם תווית: כבוי — אפור; פועל — כחול. בעברית המתג מימין לתווית */
    private static final class SwitchButton extends JToggleButton {
        private static final int TW = 34, TH = 18;

        SwitchButton(String text) {
            super(text);
            setFocusable(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setOpaque(false);
            setFont(Theme.small);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(TW + 10 + getFontMetrics(Theme.small).stringWidth(getText()) + 8, 28);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = smooth(g0);
            int w = getWidth(), h = getHeight(), tx = w - TW - 2, ty = (h - TH) / 2;
            boolean on = isSelected();
            g.setColor(on ? Theme.ACCENT : getModel().isRollover() ? new Color(0xB8C0CC) : new Color(0xCBD2DC));
            g.fillRoundRect(tx, ty, TW, TH, TH, TH);
            int k = TH - 4, kx = on ? tx + 2 : tx + TW - k - 2;   // פועל: העיגול זז שמאלה (בכיוון הקריאה)
            g.setColor(new Color(0, 0, 0, 30));
            g.fillOval(kx, ty + 3, k, k);
            g.setColor(Color.WHITE);
            g.fillOval(kx, ty + 2, k, k);
            g.setFont(Theme.small);
            g.setColor(on ? Theme.TEXT : Theme.MUTED);
            java.awt.FontMetrics fm = g.getFontMetrics();
            g.drawString(getText(), tx - 10 - fm.stringWidth(getText()), (h + fm.getAscent() - fm.getDescent()) / 2);
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

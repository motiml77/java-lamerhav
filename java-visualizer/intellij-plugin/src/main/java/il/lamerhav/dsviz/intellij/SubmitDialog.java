package il.lamerhav.dsviz.intellij;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.TransferHandler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.WrapLayout;

/**
 * חלון "שליחת תרגיל" במראה החדש (כמו באתר): מצרפים את דף התרגיל — PDF / Word / תמונות, או צילום מסך שמדביקים (Ctrl+V) —
 * ובוחרים לאיזו תיקיית קוד. אחרי "שליחה" אותו חלון מראה את הבנייה: הקבצים שצורפו, השלבים (✓ ירוק — הסתיים, נקודה כחולה ושניות —
 * עכשיו, אפור — עוד לא), ו"ביטול" בלבד. השליחה עצמה (SubmitCli, ברקע — לא בחוט של המסך) — Submitter. בסוף החלון נסגר לבד,
 * והקובץ נפתח בסעיף א. בשגיאה — שורה אדומה קצרה ו"נסו שוב", והקבצים נשארים.
 */
final class SubmitDialog extends Modern.Dialog {
    /** קובץ ברשימה: הנתיב (תמונה מודבקת נשמרת לקובץ זמני) והשם להצגה */
    static final class Item {
        final File file;
        final String name;
        private Image thumb;
        private boolean thumbTried;

        Item(File file, String name) {
            this.file = file;
            this.name = name;
        }

        boolean image() {
            String n = name.toLowerCase();
            return n.matches(".*\\.(png|jpe?g|gif|bmp|webp)$");
        }

        String ext() {
            int dot = name.lastIndexOf('.');
            return dot < 0 ? "" : name.substring(dot + 1).toUpperCase();
        }

        /** תמונה מוקטנת (רק לתמונות; נטענת פעם אחת) */
        Image thumb() {
            if (thumbTried) return thumb;
            thumbTried = true;
            if (!image()) return null;
            try {
                BufferedImage b = ImageIO.read(file);
                if (b == null) return null;
                int w = 192, h = Math.max(1, b.getHeight() * w / Math.max(1, b.getWidth()));
                BufferedImage s = new BufferedImage(w, Math.min(h, 400), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = s.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, s.getWidth(), s.getHeight());
                g.drawImage(b, 0, 0, w, h, null);
                g.dispose();
                thumb = s;
            } catch (Exception ignored) {
                // בלי תמונה מוקטנת
            }
            return thumb;
        }
    }

    /** מריץ את השליחה (ברקע). בבדיקות ובצילומי מסך — מזויף */
    interface Runner {
        Submitter.Outcome run(List<Item> items, String note, ExerciseDialog.Target target, Submitter.Listener listener);
    }

    private enum Mode { FORM, BUILDING, FAILED }

    private final Project project;
    private final List<ExerciseDialog.Target> targets;
    private final List<Item> model = new ArrayList<Item>();
    private final CardLayout deckLayout = new CardLayout();
    private JPanel deck;
    private Drop drop;
    private JBTextArea note;
    private ComboBox<ExerciseDialog.Target> targetCombo;
    private Build build;
    private Mode mode = Mode.FORM;
    private Runner runner;
    private volatile boolean cancelled;
    private int runId;
    private Submitter.Outcome outcome;
    private ExerciseDialog.Target runTarget;
    private int pasted;
    private boolean autoStart;
    private final Action retry = new AbstractAction("נסו שוב") {
        {
            putValue(DEFAULT_ACTION, Boolean.TRUE);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            start();
        }
    };

    SubmitDialog(Project project, List<ExerciseDialog.Target> targets) {
        super(project);
        this.project = project;
        this.targets = targets;
        setTitle("שליחת תרגיל");
        setOKButtonText("שליחה");
        setCancelButtonText("ביטול");
        init();
    }

    void runner(Runner r) {
        runner = r;
    }

    /** מתחיל לשלוח מיד כשהחלון נפתח (Submitter.submit — בלי שלב הבחירה) */
    void autoStart() {
        autoStart = true;
    }

    @Override
    public void show() {
        if (autoStart) ApplicationManager.getApplication().invokeLater(this::start, ModalityState.any());
        super.show();
    }

    @Override
    protected Action[] createActions() {
        switch (mode) {
            case BUILDING: return new Action[] { getCancelAction() };
            case FAILED: return new Action[] { retry, getCancelAction() };
            default: return new Action[] { getOKAction(), getCancelAction() };
        }
    }

    @Override
    protected JComponent createCenterPanel() {
        deck = Modern.panel(deckLayout);
        deck.add(form(), "form");
        build = new Build();
        build.onChangeFiles(this::backToForm);
        deck.add(build, "build");
        deckLayout.show(deck, "form");
        // Ctrl+V בכל מקום בחלון (חוץ משדה ההערה — שם מדביקים טקסט)
        deck.registerKeyboardAction(e -> paste(), KeyStroke.getKeyStroke(KeyEvent.VK_V, com.intellij.openapi.util.SystemInfo.isMac ? java.awt.event.InputEvent.META_DOWN_MASK : java.awt.event.InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        Modern.rtl(deck);
        return deck;
    }

    private JComponent form() {
        JPanel root = Modern.Dialog.body(null);
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        add(root, Modern.label("שליחת תרגיל", 18, Font.BOLD, Modern.INK), 0);
        add(root, Modern.label("מצרפים את דף התרגיל ומקבלים קוד מוכן: פעולה לכל סעיף ו-main.", 13, Font.PLAIN, Modern.MUTED), 4);
        drop = new Drop();
        add(root, drop, 14);
        add(root, Modern.label("הערה (לא חובה)", 12, Font.BOLD, Modern.INK_2), 14);
        note = new JBTextArea(2, 40);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setFont(Modern.font(13, Font.PLAIN));
        note.getEmptyText().setText("למשל: רק סעיפים א-ב, או \"התרגיל בעמוד 2\"");
        note.setBorder(JBUI.Borders.empty(6, 8));
        note.setBackground(Modern.PANEL);
        note.setForeground(Modern.INK);
        JBScrollPane ns = new JBScrollPane(note);
        ns.setBorder(new Modern.Round(Modern.LINE_2, 6, 0, 0));
        add(root, ns, 6);
        add(root, Modern.label("לאיזו תיקייה", 12, Font.BOLD, Modern.INK_2), 14);
        targetCombo = new ComboBox<ExerciseDialog.Target>(targets.toArray(new ExerciseDialog.Target[0]));
        targetCombo.putClientProperty(Modern.LTR, Boolean.TRUE);
        add(root, targetCombo, 6);
        add(root, Modern.label("התרגיל נכנס לחבילה משלו (למשל ex01_stacks), והסמן מחכה בסעיף א.", 12, Font.PLAIN, Modern.MUTED), 6);
        JPanel outer = Modern.panel(new BorderLayout());
        outer.add(root, BorderLayout.NORTH);
        return outer;
    }

    private static <T extends JComponent> T add(JPanel col, T c, int top) {
        if (top > 0) col.add(Box.createVerticalStrut(JBUI.scale(top)));
        c.setAlignmentX(Component.RIGHT_ALIGNMENT);
        if (c instanceof JLabel) c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        col.add(c);
        return c;
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return drop;
    }

    @Override
    protected ValidationInfo doValidate() {
        return mode == Mode.FORM && model.isEmpty() ? new ValidationInfo("עוד לא צירפתם את התרגיל — בחרו קובץ או הדביקו תמונה (Ctrl+V).", drop) : null;
    }

    // ------------------------------------------------------------------ שליחה

    @Override
    protected void doOKAction() {
        if (mode == Mode.FORM) start();
    }

    @Override
    public void doCancelAction() {
        cancelled = true;
        super.doCancelAction();
    }

    /** מעבר לתצוגת הבנייה והשליחה ברקע */
    void start() {
        if (model.isEmpty()) return;
        ExerciseDialog.Target t = target();
        List<Item> items = items();
        String n = note();
        mode = Mode.BUILDING;
        cancelled = false;
        int id = ++runId;
        build.reset(items);
        deckLayout.show(deck, "build");
        fillFooter();
        runTarget = t;
        Runner r = runner;
        if (r == null) {
            String bad = Submitter.prepare(project, t);
            if (bad != null) {
                failed(id, bad);
                return;
            }
            r = Submitter.defaultRunner(project);
        }
        Submitter.Listener l = new Submitter.Listener() {
            @Override
            public void progress(String text) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (id == runId && mode == Mode.BUILDING) build.progress(text);
                }, ModalityState.any());
            }

            @Override
            public boolean cancelled() {
                return cancelled || id != runId;
            }
        };
        Runner run = r;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            Submitter.Outcome out;
            try {
                out = run.run(items, n, t, l);
            } catch (Throwable e) {
                out = new Submitter.Outcome();
                out.error = "לא הצלחתי ליצור את התרגיל: " + e.getMessage();
            }
            Submitter.Outcome o = out;
            ApplicationManager.getApplication().invokeLater(() -> done(id, o), ModalityState.any());
        });
    }

    private void done(int id, Submitter.Outcome out) {
        if (id != runId || cancelled || isDisposed()) return;
        if (out == null || (out.error != null && out.files.isEmpty())) {
            failed(id, out == null ? "לא הצלחתי ליצור את התרגיל." : out.error);
            return;
        }
        outcome = out;
        build.finished();
        Timer t = new Timer(450, e -> {
            if (!isDisposed()) close(OK_EXIT_CODE);
        });
        t.setRepeats(false);
        t.start();
    }

    private void failed(int id, String error) {
        if (id != runId || isDisposed()) return;
        mode = Mode.FAILED;
        build.fail(error);
        fillFooter();
    }

    private void backToForm() {
        runId++;
        mode = Mode.FORM;
        build.stopTimer();
        deckLayout.show(deck, "form");
        fillFooter();
    }

    @Override
    protected void dispose() {
        runId++;
        if (build != null) build.stopTimer();
        super.dispose();
    }

    /** התוצאה של השליחה (אחרי שהחלון נסגר ב-OK) */
    Submitter.Outcome outcome() {
        return outcome;
    }

    /** תיקיית הקוד שאליה נשלח */
    ExerciseDialog.Target runTarget() {
        return runTarget != null ? runTarget : target();
    }

    Build build() {
        return build;
    }

    /** התוכן: הטופס או הבנייה (לבדיקות ולצילומי מסך) */
    JPanel deck() {
        return deck;
    }

    // ------------------------------------------------------------------ הקבצים

    private void choose() {
        FileChooserDescriptor d = new FileChooserDescriptor(true, false, false, false, false, true);
        d.setTitle("בחירת דף התרגיל");
        d.withFileFilter(f -> f.getExtension() != null && f.getExtension().toLowerCase().matches("pdf|docx|doc|png|jpg|jpeg|gif|bmp|webp|txt"));
        for (VirtualFile f : FileChooser.chooseFiles(d, project, null)) add(new File(f.getPath()), f.getName());
    }

    /** Ctrl+V: תמונה (צילום מסך), קבצים שהועתקו, או טקסט */
    void paste() {
        if (mode != Mode.FORM) return;
        Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        try {
            Transferable t = cb.getContents(null);
            if (t != null && addFrom(t)) return;
            if (t != null && t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                if (note.isFocusOwner()) {
                    note.paste();
                    return;
                }
                String text = (String) t.getTransferData(DataFlavor.stringFlavor);
                if (text != null && text.trim().length() >= 10) {
                    File f = File.createTempFile("dsviz-text-", ".txt");
                    Files.write(f.toPath(), text.getBytes(StandardCharsets.UTF_8));
                    add(f, "טקסט מודבק " + (++pasted) + ".txt");
                }
            }
        } catch (Exception ignored) {
            // אין מה להדביק
        }
    }

    @SuppressWarnings("unchecked")
    private boolean addFrom(Transferable t) {
        try {
            if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                for (File f : (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor)) add(f, f.getName());
                return true;
            }
            if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image img = (Image) t.getTransferData(DataFlavor.imageFlavor);
                BufferedImage b = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_RGB);
                b.getGraphics().drawImage(img, 0, 0, null);
                File f = File.createTempFile("dsviz-paste-", ".png");
                ImageIO.write(b, "png", f);
                add(f, "צילום מסך " + (++pasted) + ".png");
                return true;
            }
        } catch (Exception ignored) {
            // לא תמונה ולא קבצים
        }
        return false;
    }

    /** מוסיף קובץ לרשימה (גם לבדיקות ולצילומי מסך) */
    void add(File f, String name) {
        if (!f.isFile()) return;
        model.add(new Item(f, name));
        if (drop != null) drop.refresh();
    }

    private void removeItem(Item it) {
        model.remove(it);
        drop.refresh();
    }

    List<Item> items() {
        return new ArrayList<Item>(model);
    }

    String note() {
        return note.getText().trim();
    }

    ExerciseDialog.Target target() {
        return (ExerciseDialog.Target) targetCombo.getSelectedItem();
    }

    // ------------------------------------------------------------------ אזור הקבצים (גוררים / מדביקים / בוחרים)

    /** מסגרת מקווקוות: ריקה — הסבר ושני כפתורים; עם קבצים — תמונות מוקטנות (עם ×) ו"עוד קובץ" */
    private final class Drop extends JPanel {
        private final JPanel tiles = new JPanel(new WrapLayout(FlowLayout.LEADING, JBUI.scale(10), JBUI.scale(8)));

        Drop() {
            super(new BorderLayout());
            setOpaque(false);
            setFocusable(true);
            setBorder(JBUI.Borders.empty(10, 12));
            tiles.setOpaque(false);
            TransferHandler th = new TransferHandler() {
                @Override
                public boolean canImport(TransferSupport s) {
                    return mode == Mode.FORM && (s.isDataFlavorSupported(DataFlavor.javaFileListFlavor) || s.isDataFlavorSupported(DataFlavor.imageFlavor));
                }

                @Override
                public boolean importData(TransferSupport s) {
                    return addFrom(s.getTransferable());
                }
            };
            setTransferHandler(th);
            tiles.setTransferHandler(th);
            setPreferredSize(new Dimension(JBUI.scale(500), JBUI.scale(168)));
            refresh();
        }

        void refresh() {
            removeAll();
            if (model.isEmpty()) {
                JPanel c = new JPanel();
                c.setOpaque(false);
                c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
                c.add(Box.createVerticalGlue());
                JLabel icon = new JLabel(pageIcon(""));
                icon.setAlignmentX(Component.CENTER_ALIGNMENT);
                c.add(icon);
                c.add(Box.createVerticalStrut(JBUI.scale(8)));
                JLabel t = Modern.label("גוררים לכאן PDF, Word או תמונה, או מדביקים צילום מסך (Ctrl+V)", 13, Font.PLAIN, Modern.INK_2);
                t.setAlignmentX(Component.CENTER_ALIGNMENT);
                c.add(t);
                c.add(Box.createVerticalStrut(JBUI.scale(10)));
                JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, JBUI.scale(8), 0));
                row.setOpaque(false);
                row.add(new Modern.Button("בחירת קבצים…", false, SubmitDialog.this::choose));
                row.add(new Modern.Button("הדבקה", false, SubmitDialog.this::paste));
                row.setAlignmentX(Component.CENTER_ALIGNMENT);
                c.add(row);
                c.add(Box.createVerticalGlue());
                add(c, BorderLayout.CENTER);
            } else {
                tiles.removeAll();
                for (Item it : model) tiles.add(new Thumb(it, 96, 72, () -> removeItem(it)));
                if (model.size() < 10) tiles.add(addTile());
                JBScrollPane sp = new JBScrollPane(tiles, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                sp.setBorder(JBUI.Borders.empty());
                sp.setOpaque(false);
                sp.getViewport().setOpaque(false);
                add(sp, BorderLayout.CENTER);
                add(Modern.label(model.size() == 1 ? "קובץ אחד · אפשר להוסיף עוד (עד 10)" : model.size() + " קבצים · אפשר להוסיף עוד (עד 10)",
                        12, Font.PLAIN, Modern.MUTED), BorderLayout.SOUTH);
            }
            Modern.rtl(this);
            revalidate();
            repaint();
        }

        private JComponent addTile() {
            JPanel t = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = JBUI.scale(96), h = JBUI.scale(72), x = (getWidth() - w) / 2;
                    g2.setColor(Modern.LINE_2);
                    g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 4f, 3f }, 0f));
                    g2.drawRoundRect(x, 0, w - 1, h - 1, JBUI.scale(8), JBUI.scale(8));
                    g2.setColor(Modern.MUTED);
                    g2.setStroke(new BasicStroke(JBUI.scale(1.6f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cx = x + w / 2, cy = h / 2, r = JBUI.scale(8);
                    g2.drawLine(cx - r, cy, cx + r, cy);
                    g2.drawLine(cx, cy - r, cx, cy + r);
                    g2.dispose();
                }
            };
            t.setOpaque(false);
            JLabel name = Modern.label("עוד קובץ", 11, Font.PLAIN, Modern.MUTED);
            name.setHorizontalAlignment(SwingConstants.CENTER);
            t.add(name, BorderLayout.SOUTH);
            t.setPreferredSize(new Dimension(JBUI.scale(100), JBUI.scale(72 + 20)));
            t.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            t.setToolTipText("בחירת קבצים");
            t.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    choose();
                }
            });
            return t;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(model.isEmpty() ? Modern.SUNK : Modern.PANEL);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, JBUI.scale(12), JBUI.scale(12));
            g2.setColor(isFocusOwner() ? Modern.ACCENT : Modern.LINE_2);
            g2.setStroke(new BasicStroke(JBUI.scale(1.2f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f,
                    new float[] { JBUI.scale(5f), JBUI.scale(4f) }, 0f));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, JBUI.scale(12), JBUI.scale(12));
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ תמונה מוקטנת של קובץ

    /** דף עם פינה מקופלת והסיומת (PDF באדום, DOCX בכחול) — לקבצים שאינם תמונה */
    static Icon pageIcon(String ext) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                int w = getIconWidth(), h = getIconHeight(), f = w / 4;
                Path2D p = new Path2D.Float();
                p.moveTo(x, y);
                p.lineTo(x + w - f, y);
                p.lineTo(x + w, y + f);
                p.lineTo(x + w, y + h);
                p.lineTo(x, y + h);
                p.closePath();
                g2.setColor(Modern.PANEL);
                g2.fill(p);
                g2.setColor(Modern.LINE_2);
                g2.draw(p);
                g2.drawLine(x + w - f, y, x + w - f, y + f);
                g2.drawLine(x + w - f, y + f, x + w, y + f);
                if (!ext.isEmpty()) {
                    g2.setColor(ext.equals("PDF") ? Modern.ERR : ext.startsWith("DOC") ? Modern.ACCENT : Modern.MUTED);
                    g2.setFont(Modern.font(9, Font.BOLD));
                    int tw = g2.getFontMetrics().stringWidth(ext);
                    g2.drawString(ext, x + (w - tw) / 2, y + h - JBUI.scale(6));
                } else {
                    g2.setColor(Modern.LINE_2);
                    for (int i = 0; i < 3; i++) g2.drawLine(x + JBUI.scale(5), y + JBUI.scale(9 + i * 5), x + w - JBUI.scale(6), y + JBUI.scale(9 + i * 5));
                }
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return JBUI.scale(26);
            }

            @Override
            public int getIconHeight() {
                return JBUI.scale(32);
            }
        };
    }

    /** תמונה מוקטנת של קובץ ושמו מתחת; עם onRemove — × בפינה */
    static final class Thumb extends JPanel {
        private final Item item;
        private final int tw, th;

        Thumb(Item item, int w, int h, Runnable onRemove) {
            super(new BorderLayout());
            this.item = item;
            this.tw = w;
            this.th = h;
            setOpaque(false);
            boolean he = Modern.hasHebrew(item.name);
            JLabel name = Modern.label("", 11, Font.PLAIN, Modern.MUTED);
            String shown = fit(item.name, name.getFontMetrics(name.getFont()), JBUI.scale(w));
            name.setText(he ? Modern.bidi(shown) : shown);
            name.setHorizontalAlignment(SwingConstants.CENTER);
            name.setComponentOrientation(he ? ComponentOrientation.RIGHT_TO_LEFT : ComponentOrientation.LEFT_TO_RIGHT);
            if (!he) name.putClientProperty(Modern.LTR, Boolean.TRUE);
            name.setPreferredSize(new Dimension(JBUI.scale(w + 4), name.getPreferredSize().height));
            name.setToolTipText(item.name);
            add(name, BorderLayout.SOUTH);
            setPreferredSize(new Dimension(JBUI.scale(w + 4), JBUI.scale(h) + name.getPreferredSize().height + JBUI.scale(4)));
            setToolTipText(item.name + "   " + Math.max(1, item.file.length() / 1024) + " KB");
            if (onRemove != null) {
                setLayout(null);
                name.setBounds(0, JBUI.scale(h + 4), JBUI.scale(w + 4), name.getPreferredSize().height);
                add(name);
                JLabel x = new JLabel("×", SwingConstants.CENTER) {
                    @Override
                    protected void paintComponent(Graphics g) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(Modern.PANEL);
                        g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                        g2.setColor(Modern.LINE_2);
                        g2.drawOval(0, 0, getWidth() - 1, getHeight() - 1);
                        g2.dispose();
                        super.paintComponent(g);
                    }
                };
                x.setFont(Modern.font(13, Font.BOLD));
                x.setForeground(Modern.INK_2);
                x.setToolTipText("הסרה");
                x.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                int s = JBUI.scale(18);
                x.setBounds(0, 0, s, s);
                x.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        onRemove.run();
                    }
                });
                add(x);
                setComponentZOrder(x, 0);
            }
        }

        /** שם ארוך — מקוצר עם "…" (הוא כולו ב-tooltip) */
        static String fit(String name, java.awt.FontMetrics fm, int width) {
            if (fm.stringWidth(name) <= width) return name;
            String s = name;
            while (s.length() > 1 && fm.stringWidth(s + "…") > width) s = s.substring(0, s.length() - 1);
            return s.trim() + "…";
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = JBUI.scale(tw), h = JBUI.scale(th), x = (getWidth() - w) / 2, y = JBUI.scale(2);
            int arc = JBUI.scale(8);
            Image img = item.thumb();
            g2.setColor(img != null ? Color.WHITE : Modern.SUNK);
            g2.fillRoundRect(x, y, w, h, arc, arc);
            if (img != null) {
                java.awt.Shape clip = g2.getClip();
                g2.clip(new java.awt.geom.RoundRectangle2D.Float(x, y, w, h, arc, arc));
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                int iw = img.getWidth(null), ih = img.getHeight(null);
                double s = Math.max((double) w / iw, (double) h / ih);
                g2.drawImage(img, x, y, (int) Math.round(iw * s), (int) Math.round(ih * s), null);   // החלק העליון של הדף
                g2.setClip(clip);
            } else {
                Icon p = pageIcon(item.ext());
                p.paintIcon(this, g2, x + (w - p.getIconWidth()) / 2, y + (h - p.getIconHeight()) / 2);
            }
            g2.setColor(Modern.LINE);
            g2.drawRoundRect(x, y, w - 1, h - 1, arc, arc);
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------ תצוגת הבנייה

    /** השלבים — כמו באתר, לפי ההודעות {"progress": …} של SubmitCli */
    static final String[] STAGES = { "מכין את הקבצים", "שולח ל-Gemini", "בונה את התרגיל", "בודק שהקוד מתקמפל", "בודק את ההצעות לפתרון" };

    /** לאיזה שלב שייכת הודעה של SubmitCli; ‎-1 — הודעת משנה (תיקון / בדיקה חוזרת), שמוצגת בשורה קטנה מתחת לשלב הנוכחי */
    static int stageOf(String progress) {
        String p = progress == null ? "" : progress;
        if (p.contains("מבקש תיקון") || p.contains("בודק שוב") || p.contains("מתקן")) return -1;
        if (p.contains("מכין את הקבצים")) return 0;
        if (p.contains("שולח")) return 1;
        if (p.contains("בונה את התרגיל")) return 2;
        if (p.contains("מתקמפל")) return 3;
        if (p.contains("ההצעות לפתרון")) return 4;
        return -1;
    }

    /** הודעת משנה קצרה, בלי מקף ארוך */
    static String subLine(String progress) {
        String p = progress == null ? "" : progress.trim();
        if (p.contains("שגיאות קומפילציה")) return "יש שגיאות קומפילציה, מבקש תיקון…";
        if (p.contains("הפלט הצפוי")) return "הפלט של ההצעה לא יצא כמו הצפוי, מבקש תיקון…";
        return p.replace(" — ", ", ");
    }

    /** שורה אחת קצרה מהשגיאה (בלי הפירוט) */
    static String shortError(String error) {
        String e = error == null ? "" : error.trim();
        if (e.isEmpty()) return "לא הצלחתי ליצור את התרגיל.";
        e = e.split("\n")[0].trim();
        if (e.length() > 110) {
            int cut = e.lastIndexOf(' ', 104);
            e = e.substring(0, cut > 60 ? cut : 104).trim() + "…";
        }
        return e;
    }

    /** הבנייה: קו התקדמות דק, הקבצים שצורפו, השלבים, ושורת שגיאה */
    static final class Build extends JPanel {
        final Modern.TopLine line = new Modern.TopLine();
        private final JPanel thumbs = new JPanel(new FlowLayout(FlowLayout.LEADING, JBUI.scale(8), 0));
        private final Row[] rows = new Row[STAGES.length];
        private final JLabel heading = Modern.label("בונים את התרגיל", 15, Font.BOLD, Modern.INK);
        static final String SUB = "בדרך כלל עד דקה. החלון ייסגר לבד והקובץ ייפתח בסעיף א.";
        private final JLabel sub = Modern.label(SUB, 12, Font.PLAIN, Modern.MUTED);
        private final JLabel error = Modern.label(" ", 13, Font.PLAIN, Modern.ERR);
        private final JPanel errorRow = new JPanel(new BorderLayout(JBUI.scale(10), 0));
        private Runnable onChangeFiles;
        private int current = -1;
        private long stageStart;
        private final Timer timer;

        Build() {
            super(new BorderLayout());
            setBackground(Modern.PANEL);
            add(line, BorderLayout.NORTH);
            JPanel col = Modern.Dialog.body(null);
            col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
            SubmitDialog.add(col, heading, 0);
            SubmitDialog.add(col, sub, 4);
            thumbs.setOpaque(false);
            SubmitDialog.add(col, thumbs, 14);
            JPanel list = new JPanel();
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
            list.setOpaque(false);
            for (int i = 0; i < STAGES.length; i++) {
                list.add(rows[i] = new Row(STAGES[i]));
                rows[i].setAlignmentX(Component.RIGHT_ALIGNMENT);
            }
            JPanel listCard = Modern.card(list);
            SubmitDialog.add(col, listCard, 14);
            errorRow.setOpaque(false);
            errorRow.add(error, BorderLayout.CENTER);
            errorRow.add(Modern.link("שינוי הקבצים", () -> { if (onChangeFiles != null) onChangeFiles.run(); }), BorderLayout.LINE_END);
            errorRow.setVisible(false);
            SubmitDialog.add(col, errorRow, 10);
            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(col, BorderLayout.NORTH);
            add(top, BorderLayout.CENTER);
            timer = new Timer(1000, e -> tick());
            Modern.rtl(this);
        }

        void onChangeFiles(Runnable r) {
            onChangeFiles = r;
        }

        void reset(List<Item> items) {
            thumbs.removeAll();
            for (Item it : items) thumbs.add(new Thumb(it, 64, 48, null));
            Modern.rtl(thumbs);
            for (Row r : rows) r.set(Row.PENDING, "", "");
            current = -1;
            heading.setText("בונים את התרגיל");
            sub.setText(SUB);
            errorRow.setVisible(false);
            line.set(0.04, Modern.ACCENT);
            progress("מכין את הקבצים…");
            timer.start();
            revalidate();
            repaint();
        }

        /** הודעה מ-SubmitCli */
        void progress(String text) {
            int k = stageOf(text);
            if (k < 0) {
                if (current >= 0) rows[current].sub(subLine(text));
                return;
            }
            if (k < current) return;
            if (k > current) {
                for (int i = 0; i < k; i++) rows[i].set(Row.DONE, "", "");
                current = k;
                stageStart = System.currentTimeMillis();
                rows[k].set(Row.NOW, "", seconds(0));
                line.set((k + 1.0) / STAGES.length, Modern.ACCENT);
            }
        }

        /** תיקון השעון (לבדיקות ולצילומי מסך: "12 שנ'") */
        void elapsed(int seconds) {
            stageStart = System.currentTimeMillis() - seconds * 1000L;
            tick();
        }

        private void tick() {
            if (current >= 0 && rows[current].state == Row.NOW)
                rows[current].time.setText(seconds((int) ((System.currentTimeMillis() - stageStart) / 1000)));
        }

        static String seconds(int s) {
            return s + " שנ'";
        }

        void finished() {
            timer.stop();
            for (Row r : rows) r.set(Row.DONE, "", "");
            heading.setText("התרגיל מוכן");
            line.set(1, Modern.OK);
        }

        void fail(String message) {
            timer.stop();
            if (current >= 0) rows[current].set(Row.FAILED, rows[current].subText(), "");
            heading.setText("לא הצלחתי ליצור את התרגיל");
            sub.setText(Modern.RLM + "הקבצים שצירפתם נשארו. אפשר לנסות שוב.");
            error.setText(Modern.RLM + shortError(message));
            error.setToolTipText(message != null && message.length() > 110 ? "<html><div dir='rtl'>" + message.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>") + "</div></html>" : null);
            errorRow.setVisible(true);
            line.set(1, Modern.ERR);
            revalidate();
            repaint();
        }

        void stopTimer() {
            timer.stop();
        }

        /** מצב השלבים (לבדיקות): ✓ / ● / ○ / ✕ */
        String states() {
            StringBuilder b = new StringBuilder();
            for (Row r : rows) b.append(r.state);
            return b.toString();
        }

        String time() {
            return current >= 0 ? rows[current].time.getText() : "";
        }

        String subText() {
            return current >= 0 ? rows[current].subText() : "";
        }
    }

    /** שורה של שלב: סימן, השם, השניות (בשלב הנוכחי), ומתחת — הודעת משנה קטנה */
    static final class Row extends JPanel {
        static final char PENDING = '○', NOW = '●', DONE = '✓', FAILED = '✕';
        char state = PENDING;
        private final JLabel mark = new JLabel();
        private final JLabel name;
        final JLabel time = Modern.label("", 12, Font.PLAIN, Modern.MUTED);
        private final JLabel sub = Modern.label("", 12, Font.PLAIN, Modern.MUTED);

        Row(String text) {
            super(new BorderLayout(JBUI.scale(10), 0));
            setOpaque(false);
            setBorder(JBUI.Borders.empty(4, 2));
            name = Modern.label(text, 13, Font.PLAIN, Modern.MUTED);
            mark.setPreferredSize(new Dimension(JBUI.scale(16), JBUI.scale(16)));
            mark.setVerticalAlignment(SwingConstants.TOP);
            JPanel mid = new JPanel(new BorderLayout());
            mid.setOpaque(false);
            mid.add(name, BorderLayout.NORTH);
            mid.add(sub, BorderLayout.SOUTH);
            sub.setVisible(false);
            JPanel markBox = new JPanel(new BorderLayout());
            markBox.setOpaque(false);
            markBox.add(mark, BorderLayout.NORTH);
            add(markBox, BorderLayout.LINE_START);
            add(mid, BorderLayout.CENTER);
            JPanel tb = new JPanel(new BorderLayout());
            tb.setOpaque(false);
            tb.add(time, BorderLayout.NORTH);
            add(tb, BorderLayout.LINE_END);
            set(PENDING, "", "");
        }

        void set(char state, String subText, String timeText) {
            this.state = state;
            mark.setIcon(markIcon(state));
            name.setFont(Modern.font(13, state == NOW ? Font.BOLD : Font.PLAIN));
            name.setForeground(state == NOW ? Modern.INK : state == DONE ? Modern.INK_2 : state == FAILED ? Modern.ERR : Modern.MUTED);
            time.setText(timeText);
            sub(subText);
        }

        void sub(String text) {
            sub.setText(text == null || text.isEmpty() ? "" : Modern.RLM + text);
            sub.setVisible(text != null && !text.isEmpty());
        }

        String subText() {
            String t = sub.getText();
            return t.isEmpty() ? "" : t.substring(1);
        }

        static Icon markIcon(char state) {
            return new Icon() {
                @Override
                public void paintIcon(Component c, Graphics g, int x, int y) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                    int s = getIconWidth();
                    float u = s / 16f;
                    g2.translate(x, y + JBUI.scale(2));
                    switch (state) {
                        case DONE: {
                            g2.setColor(Modern.OK);
                            g2.setStroke(new BasicStroke(2f * u, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            Path2D p = new Path2D.Float();
                            p.moveTo(3 * u, 8.5f * u);
                            p.lineTo(6.5f * u, 12 * u);
                            p.lineTo(13 * u, 4.5f * u);
                            g2.draw(p);
                            break;
                        }
                        case NOW:
                            g2.setColor(Modern.ACCENT_SOFT);
                            g2.fillOval(Math.round(2 * u), Math.round(2 * u), Math.round(12 * u), Math.round(12 * u));
                            g2.setColor(Modern.ACCENT);
                            g2.fillOval(Math.round(4.5f * u), Math.round(4.5f * u), Math.round(7 * u), Math.round(7 * u));
                            break;
                        case FAILED:
                            g2.setColor(Modern.ERR);
                            g2.setStroke(new BasicStroke(2f * u, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                            g2.drawLine(Math.round(4.5f * u), Math.round(4.5f * u), Math.round(11.5f * u), Math.round(11.5f * u));
                            g2.drawLine(Math.round(11.5f * u), Math.round(4.5f * u), Math.round(4.5f * u), Math.round(11.5f * u));
                            break;
                        default:
                            g2.setColor(Modern.LINE_2);
                            g2.setStroke(new BasicStroke(1.5f * u));
                            g2.drawOval(Math.round(4.5f * u), Math.round(4.5f * u), Math.round(7 * u), Math.round(7 * u));
                    }
                    g2.dispose();
                }

                @Override
                public int getIconWidth() {
                    return JBUI.scale(16);
                }

                @Override
                public int getIconHeight() {
                    return JBUI.scale(16);
                }
            };
        }
    }
}

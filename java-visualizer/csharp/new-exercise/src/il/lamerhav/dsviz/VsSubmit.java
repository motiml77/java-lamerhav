package il.lamerhav.dsviz;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import unit4.collectionsLib.Theme;

/**
 * "שליחת תרגיל" ו"הצעה לפתרון" ל-Visual Studio (C#) — החלונות (Swing) של הקיצורים "שליחת תרגיל ב-C#" ו"הצעה לפתרון ב-C#".
 * אותו מנוע כמו ב-Eclipse וב-VS Code (ExercisePipeline): התרגיל נבנה כפרויקט Exercises\Ex01_... בתיקיית התרגילים, נרשם בפתרון
 * (Visual Studio מציג אותו), מתקמפל ונבדק ב-dsviz-cs; ההצעות לפתרון נבדקות (מריצים את Main עם ההצעות ומשווים לפלט הצפוי)
 * ונשמרות בצד (~/.dsviz/solutions.json) — לא בקוד של התלמיד. בחלון "הצעה לפתרון" בוחרים תרגיל וסעיף, ורק אז רואים את ההצעה.
 * לסרטוני ההדרכה ולבדיקות: לשורש החלון יש "dsviz.addFile" (הוספת קובץ כמו "בחירת קבצים…").
 */
public final class VsSubmit {
    private VsSubmit() {}

    static final Color PURPLE = new Color(0x7C5CF0);

    /** איפה נשמרות ההצעות */
    static File storeFile() {
        return Paths.get(System.getProperty("user.home"), ".dsviz", "solutions.json").toFile();
    }

    /** הפקודה של dsviz-cs: DSVIZ_CS (גם dsviz-cs.dll דרך dotnet) → ליד ה-jar (התקנה: ...\DSViz\cs) → ~/.dsviz/cs-path.txt → PATH */
    static List<String> dsvizCs() {
        List<String> c = new ArrayList<String>();
        String env = System.getenv("DSVIZ_CS");
        List<File> cands = new ArrayList<File>();
        if (env != null && !env.isEmpty()) cands.add(new File(env));
        try {
            File jar = new File(VsSubmit.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            cands.add(new File(jar.getParentFile(), "cs" + File.separator + "dsviz-cs.exe"));
        } catch (Exception ignored) {
            // לא מ-jar
        }
        try {
            String p = new String(Files.readAllBytes(Paths.get(System.getProperty("user.home"), ".dsviz", "cs-path.txt")), "UTF-8").trim();
            if (!p.isEmpty()) cands.add(new File(p));
        } catch (Exception ignored) {
            // אין
        }
        for (File f : cands) {
            if (!f.isFile()) continue;
            if (f.getName().toLowerCase().endsWith(".dll")) c.add("dotnet");
            c.add(f.getAbsolutePath());
            return c;
        }
        c.add("dsviz-cs");
        return c;
    }

    // ------------------------------------------------------------------ שליחת תרגיל

    public static void submit(Path root) {
        String bad = CsSolution.check(root);
        if (bad != null) {
            JOptionPane.showMessageDialog(null, bad + "\nאפשר להריץ שוב את ההתקנה (DSViz-Setup.exe) עם חבילת C#.", "שליחת תרגיל — C#", JOptionPane.ERROR_MESSAGE);
            return;
        }
        WorkerClient.CLIENT = "dsviz-vs/1.0.1";
        WorkerClient client = WorkerClient.configured();
        if (client == null) {
            JOptionPane.showMessageDialog(null, "השרת של \"שליחת תרגיל\" לא מוגדר.", "שליחת תרגיל — C#", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!client.online(3500)) {   // בלי אינטרנט אין טעם לפתוח את החלון — אומרים את זה מיד
            JOptionPane.showMessageDialog(null, "כדי לשלוח תרגיל צריך חיבור לאינטרנט, וכרגע אין חיבור.\n\nבדקו את החיבור (Wi-Fi או כבל) ונסו שוב.\n"
                    + "כל שאר הכלים — הרצה ויזואלית, הוספת מבנה נתונים, דוגמאות פתורות — עובדים גם בלי אינטרנט.",
                    "שליחת תרגיל — אין חיבור לאינטרנט", JOptionPane.WARNING_MESSAGE);
            return;
        }
        new SubmitWindow(root, client).show();
    }

    /** פריט ברשימה: קובץ, תמונה מודבקת או טקסט מודבק */
    private static final class Entry {
        ExerciseFiles.Item item;
        String name, kind;   // pdf / word / image / text
        Image thumb;
        long size;
    }

    private static final class SubmitWindow {
        final Path root;
        final WorkerClient client;
        final JDialog dlg = new JDialog((java.awt.Frame) null, "שליחת תרגיל ב-C#", true);
        final List<Entry> entries = new ArrayList<Entry>();
        final JPanel items = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 10));
        final JLabel empty = new JLabel("גוררים לכאן קבצים, מדביקים צילום מסך (Ctrl+V), או בוחרים קבצים — PDF, Word (docx) או תמונות", SwingConstants.CENTER);
        final JLabel summary = new JLabel(" ");
        final JLabel status = new JLabel(" ");
        final JTextArea note = new JTextArea(3, 40);
        final JButton send = new JButton("שליחה ויצירת התרגיל"), cancel = new JButton("ביטול");
        final JButton pick = new JButton("בחירת קבצים…"), paste = new JButton("הדבקה (Ctrl+V)");
        int pastedImages, pastedTexts;
        boolean busy;

        SubmitWindow(Path root, WorkerClient client) {
            this.root = root;
            this.client = client;
        }

        void show() {
            JPanel content = new JPanel(new BorderLayout(0, 12));
            content.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));
            content.add(header("שליחת תרגיל", "מצרפים את דף התרגיל — ומקבלים פרויקט מוכן ב-Visual Studio: המחלקות, Main, ופעולה לכל סעיף עם מקום לכתוב את הפתרון."), BorderLayout.NORTH);

            JPanel body = new JPanel();
            body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
            // דף התרגיל
            JPanel page = new JPanel(new BorderLayout(0, 6));
            page.add(bold("דף התרגיל"), BorderLayout.NORTH);
            JPanel drop = new DashedPanel(new BorderLayout(0, 6));
            items.setOpaque(false);
            empty.setForeground(Theme.MUTED);
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
            btns.add(pick);
            btns.add(paste);
            for (JButton b : new JButton[] { pick, paste }) b.putClientProperty("FlatLaf.style", "arc:14; background:#ffffff");
            JPanel center = new JPanel(new BorderLayout());
            center.add(items, BorderLayout.CENTER);
            center.add(empty, BorderLayout.SOUTH);
            drop.add(center, BorderLayout.CENTER);
            drop.add(btns, BorderLayout.SOUTH);
            drop.setPreferredSize(new Dimension(640, 200));
            page.add(drop, BorderLayout.CENTER);
            summary.setForeground(Theme.MUTED);
            summary.setFont(Theme.small);
            page.add(summary, BorderLayout.SOUTH);
            body.add(new Card(page));
            body.add(gap(12));
            // הערה
            JPanel notes = new JPanel(new BorderLayout(0, 6));
            JPanel nt = new JPanel(new BorderLayout(0, 2));
            nt.add(bold("הערה (לא חובה)"), BorderLayout.NORTH);
            JLabel hint = new JLabel("למשל: \"רק שאלה 2\", או: \"הפעולה בסעיף ב צריכה להיות רקורסיבית\"");
            hint.setForeground(Theme.MUTED);
            hint.setFont(Theme.small);
            nt.add(hint, BorderLayout.SOUTH);
            notes.add(nt, BorderLayout.NORTH);
            note.setLineWrap(true);
            note.setWrapStyleWord(true);
            note.setFont(Theme.ui);
            JScrollPane ns = new JScrollPane(note);
            ns.setBorder(BorderFactory.createLineBorder(new Color(0xC5CCD8)));
            notes.add(ns, BorderLayout.CENTER);
            JLabel where = new JLabel("הפרויקט ייווצר בתיקייה Exercises שבתיקיית התרגילים (" + root.getFileName() + ") וייכנס לפתרון ב-Visual Studio.");
            where.setForeground(Theme.MUTED);
            where.setFont(Theme.small);
            JLabel privacy = new JLabel("הקבצים נשלחים לשרת של DSViz ומשם ל-Gemini (של Google) כדי לזהות את התרגיל. לא לשלוח דפים עם פרטים אישיים.");
            privacy.setForeground(Theme.MUTED);
            privacy.setFont(Theme.small);
            JPanel under = new JPanel(new BorderLayout(0, 2));
            under.add(where, BorderLayout.NORTH);
            under.add(privacy, BorderLayout.SOUTH);
            notes.add(under, BorderLayout.SOUTH);
            body.add(new Card(notes));
            body.add(gap(8));
            status.setFont(Theme.uiBold);
            JPanel st = new JPanel(new BorderLayout());
            st.add(status, BorderLayout.CENTER);
            body.add(st);
            content.add(body, BorderLayout.CENTER);

            // כפתורים
            send.putClientProperty("FlatLaf.style", "background:#2F6FED; foreground:#ffffff; arc:14; borderWidth:0; hoverBackground:#2457C5; pressedBackground:#1E4AA8; focusedBackground:#2F6FED; disabledBackground:#B9C8EE; disabledText:#ffffff");
            send.setFont(Theme.uiBold);
            send.setPreferredSize(new Dimension(190, 34));
            cancel.putClientProperty("FlatLaf.style", "arc:14; background:#ffffff");
            cancel.setPreferredSize(new Dimension(84, 34));
            content.add(buttons(send, cancel), BorderLayout.SOUTH);

            pick.addActionListener(e -> pickFiles());
            paste.addActionListener(e -> pasteClipboard());
            cancel.addActionListener(e -> dlg.dispose());
            send.addActionListener(e -> send());
            dlg.getRootPane().registerKeyboardAction(e -> {
                if (!note.isFocusOwner()) pasteClipboard();
            }, KeyStroke.getKeyStroke(KeyEvent.VK_V, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), JComponent.WHEN_IN_FOCUSED_WINDOW);
            new DropTarget(dlg, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
                @SuppressWarnings("unchecked")
                public void drop(DropTargetDropEvent e) {
                    try {
                        e.acceptDrop(DnDConstants.ACTION_COPY);
                        Object v = e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        for (File f : (List<File>) v) addFile(f);
                    } catch (Exception ex) {
                        say(ex.getMessage(), true);
                    }
                }
            });
            dlg.getRootPane().putClientProperty("dsviz.addFile", (java.util.function.Consumer<File>) this::addFile);
            dlg.getRootPane().putClientProperty("dsviz.pasteImage", (java.util.function.Consumer<File>) f -> {
                try {
                    add(ExerciseFiles.fromPastedImage(Files.readAllBytes(f.toPath()), ++pastedImages), "image", ImageIO.read(f), f.length());
                } catch (Exception ex) {
                    say(ex.getMessage(), true);
                }
            });

            dlg.setContentPane(content);
            rtl(content);
            content.setOpaque(true);
        content.setBackground(Theme.BG);
            refresh();
            dlg.pack();
            dlg.setMinimumSize(new Dimension(700, dlg.getHeight()));
            dlg.setLocationRelativeTo(null);
            dlg.setVisible(true);
        }

        void pickFiles() {
            JFileChooser fc = new JFileChooser();
            fc.setMultiSelectionEnabled(true);
            fc.setDialogTitle("דף התרגיל — PDF, Word או תמונות");
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("דף התרגיל (PDF, Word, תמונות)", "pdf", "docx", "png", "jpg", "jpeg", "webp", "gif", "bmp", "txt"));
            if (fc.showOpenDialog(dlg) != JFileChooser.APPROVE_OPTION) return;
            for (File f : fc.getSelectedFiles()) addFile(f);
        }

        void addFile(File f) {
            try {
                ExerciseFiles.Item it = ExerciseFiles.fromFile(f);
                String ext = ExerciseFiles.ext(f.getName());
                String kind = it.parts.isEmpty() ? "image" : ext.equals("pdf") ? "pdf" : ext.startsWith("doc") ? "word" : ext.equals("txt") ? "text" : "image";
                Image img = kind.equals("image") ? ImageIO.read(f) : null;
                add(it, kind, img, f.length());
            } catch (ExerciseFiles.UserError e) {
                say(e.getMessage(), true);
            } catch (Exception e) {
                say("לא הצלחתי לקרוא את הקובץ " + f.getName() + ": " + e.getMessage(), true);
            }
        }

        void pasteClipboard() {
            if (busy) return;
            try {
                Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
                Transferable t = cb.getContents(null);
                if (t == null) throw new IllegalStateException();
                if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                    Image img = (Image) t.getTransferData(DataFlavor.imageFlavor);
                    BufferedImage bi = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = bi.createGraphics();
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, bi.getWidth(), bi.getHeight());
                    g.drawImage(img, 0, 0, null);
                    g.dispose();
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    ImageIO.write(bi, "png", out);
                    add(ExerciseFiles.fromPastedImage(out.toByteArray(), ++pastedImages), "image", bi, out.size());
                    return;
                }
                if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    @SuppressWarnings("unchecked")
                    List<File> fs = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                    for (File f : fs) addFile(f);
                    return;
                }
                if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    String s = (String) t.getTransferData(DataFlavor.stringFlavor);
                    add(ExerciseFiles.fromPastedText(s, ++pastedTexts), "text", null, s.length() * 2L);
                    return;
                }
                throw new IllegalStateException();
            } catch (ExerciseFiles.UserError e) {
                say(e.getMessage(), true);
            } catch (Exception e) {
                say("בלוח אין תמונה. מצלמים את התרגיל (Win+Shift+S) ואז Ctrl+V כאן בחלון.", true);
            }
        }

        void add(ExerciseFiles.Item it, String kind, Image img, long size) {
            Entry e = new Entry();
            e.item = it;
            e.name = it.name;
            e.kind = kind;
            e.thumb = img;
            e.size = size;
            entries.add(e);
            say(" ", false);
            refresh();
        }

        void refresh() {
            items.removeAll();
            for (Entry e : entries) items.add(new Thumb(e, () -> {
                entries.remove(e);
                refresh();
            }));
            empty.setVisible(entries.isEmpty());
            int files = 0, imgs = 0;
            long total = 0;
            for (Entry e : entries) {
                if (e.kind.equals("image")) imgs++;
                else files++;
                total += e.size;
            }
            List<String> parts = new ArrayList<String>();
            if (files > 0) parts.add(files == 1 ? "קובץ אחד" : files + " קבצים");
            if (imgs > 0) parts.add(imgs == 1 ? "תמונה אחת" : imgs + " תמונות");
            summary.setText(entries.isEmpty() ? " " : String.join(" ו-", parts) + " · " + size(total) + "   ·   אפשר להוסיף עוד, או להסיר עם ×");
            send.setEnabled(!busy && !entries.isEmpty());
            rtl(items);
            items.revalidate();
            items.repaint();
        }

        void say(String text, boolean bad) {
            status.setText(text == null || text.isEmpty() ? " " : text);
            status.setForeground(bad ? new Color(0xDC2626) : Theme.ACCENT_DARK);
        }

        void send() {
            if (busy || entries.isEmpty()) return;
            final List<ExerciseFiles.Item> list = new ArrayList<ExerciseFiles.Item>();
            final List<String> names = new ArrayList<String>();
            for (Entry e : entries) {
                list.add(e.item);
                names.add(e.name);
            }
            try {
                ExerciseFiles.checkAll(list);
            } catch (ExerciseFiles.UserError e) {
                say(e.getMessage(), true);
                return;
            }
            busy = true;
            for (JComponent c : new JComponent[] { pick, paste, note }) c.setEnabled(false);
            refresh();
            dlg.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            final String text = note.getText();
            new SwingWorker<ExercisePipeline.Result, String>() {
                String error;

                protected ExercisePipeline.Result doInBackground() throws Exception {
                    publish("שולח את התרגיל… (בדרך כלל עד דקה)");
                    Map<String, Object> raw;
                    try {
                        raw = client.exercise(list, text, "csharp");
                    } catch (WorkerClient.AiError e) {
                        error = e.getMessage();
                        return null;
                    }
                    publish("בונה את התרגיל…");
                    ExercisePipeline.Result r = ExercisePipeline.csharp(client, raw, names, root, dsvizCs(), new ExercisePipeline.Progress() {
                        public void say(String t) {
                            publish(t);
                        }

                        public boolean cancelled() {
                            return false;
                        }
                    });
                    if (!r.entries.isEmpty()) new SolutionStore(storeFile()).add(r.entries);
                    return r;
                }

                protected void process(List<String> chunks) {
                    say(chunks.get(chunks.size() - 1), false);
                }

                protected void done() {
                    busy = false;
                    dlg.setCursor(Cursor.getDefaultCursor());
                    for (JComponent c : new JComponent[] { pick, paste, note }) c.setEnabled(true);
                    ExercisePipeline.Result r = null;
                    try {
                        r = get();
                    } catch (Exception e) {
                        error = "לא הצלחתי ליצור את התרגיל: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                    }
                    if (r == null) {
                        say(error == null ? "לא הצלחתי ליצור את התרגיל." : error, true);
                        refresh();
                        return;
                    }
                    dlg.dispose();
                    finished(r);
                }
            }.execute();
        }

        void finished(ExercisePipeline.Result r) {
            String msg;
            if (!r.errors.isEmpty()) {
                msg = "התרגיל נוצר (" + r.folderName + "), אבל יש בו " + r.errors.size() + " שגיאות קומפילציה:\n\n"
                        + String.join("\n", r.errors.subList(0, Math.min(4, r.errors.size())))
                        + "\n\nבדרך כלל זה ב-Main (הנתונים לדוגמה) — אפשר לתקן, או לשלוח שוב.";
            } else {
                msg = "נוצר התרגיל " + r.folderName + (r.tasks == 0 ? " (בלי סעיפים שצריך לכתוב בהם קוד — השאלות בראש הקובץ)." : ".") + "\n\n"
                        + "ב-Visual Studio: בחלון Solution Explorer לחצו קליק ימני על " + r.folderName + " ← Set as Startup Project,\n"
                        + "כתבו את הפתרון במקום המסומן, ובתפריט שליד כפתור ההפעלה הירוק בחרו \"הרצה ויזואלית\".\n"
                        + "(אם Visual Studio כבר פתוח — אשרו Reload כשהוא שואל.)"
                        + (r.entries.isEmpty() ? "" : "\n\nנתקעתם? בקיצור \"הצעה לפתרון ב-C#\" יש הצעה לכל סעיף (" + statusShort(r.solutionStatus) + ").");
            }
            JOptionPane.showMessageDialog(null, msg, "שליחת תרגיל — C#", r.errors.isEmpty() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
            if (!Boolean.getBoolean("dsviz.noOpen") && java.awt.Desktop.isDesktopSupported()) {
                try {
                    Path sln = CsSolution.solution(root);
                    java.awt.Desktop.getDesktop().open((sln != null ? sln : r.focus.toPath()).toFile());
                } catch (Exception ignored) {
                    // לא נפתח — הפרויקט בכל זאת בפתרון
                }
            }
        }
    }

    static String statusShort(String s) {
        return "ok".equals(s) ? "נבדקו — נותנות את הפלט הצפוי" : "ran".equals(s) ? "רצות בלי שגיאות" : "לא אומתו — כדאי לבדוק בעצמכם";
    }

    // ------------------------------------------------------------------ הצעה לפתרון

    /** חלון (לא חוסם — אפשר להשאיר אותו פתוח ליד Visual Studio): התרגילים והסעיפים, ולחיצה על סעיף מראה את ההצעה */
    public static void solutions(Path root) {
        List<SolutionStore.Entry> all = new SolutionStore(storeFile()).all();
        JFrame f = new JFrame("הצעה לפתרון — C#");
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(16, 18, 12, 18));
        content.add(header("הצעה לפתרון", "כדאי לנסות קודם לבד. נתקעתם? בוחרים תרגיל וסעיף — והנה דרך אחת לפתור (יש גם דרכים נכונות אחרות)."), BorderLayout.NORTH);

        // הרשימה: התרגיל האחרון למעלה
        final DefaultListModel<Object> model = new DefaultListModel<Object>();
        List<SolutionStore.Entry> sorted = new ArrayList<SolutionStore.Entry>(all);
        sorted.removeIf(e -> !e.path.toLowerCase().endsWith(".cs"));
        Map<String, List<SolutionStore.Entry>> byEx = new LinkedHashMap<String, List<SolutionStore.Entry>>();
        sorted.sort((a, b) -> Long.compare(b.time, a.time));
        for (SolutionStore.Entry e : sorted) {
            String ex = new File(e.path).getParentFile() == null ? "" : new File(e.path).getParentFile().getName();
            byEx.computeIfAbsent(ex, k -> new ArrayList<SolutionStore.Entry>()).add(e);
        }
        for (Map.Entry<String, List<SolutionStore.Entry>> g : byEx.entrySet()) {
            model.addElement(g.getKey());
            g.getValue().sort((a, b) -> a.label.compareTo(b.label));
            for (SolutionStore.Entry e : g.getValue()) model.addElement(e);
        }
        final JList<Object> list = new JList<Object>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new javax.swing.DefaultListCellRenderer() {
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean focus) {
                JLabel c = (JLabel) super.getListCellRendererComponent(l, v, i, sel && v instanceof SolutionStore.Entry, false);
                c.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
                c.setHorizontalAlignment(SwingConstants.RIGHT);
                if (v instanceof SolutionStore.Entry) {
                    SolutionStore.Entry e = (SolutionStore.Entry) v;
                    c.setText("💡 " + section(e.label) + " — ‎" + e.method + "()‎");
                    c.setFont(Theme.ui);
                    c.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 22));
                } else {
                    c.setText(String.valueOf(v));
                    c.setFont(Theme.uiBold);
                    c.setForeground(Theme.MUTED);
                    c.setBorder(BorderFactory.createEmptyBorder(10, 10, 3, 8));
                }
                return c;
            }
        });
        JScrollPane ls = new JScrollPane(list);
        ls.setPreferredSize(new Dimension(250, 380));
        ls.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        // הצד של ההצעה
        final JLabel title = new JLabel("בוחרים סעיף מהרשימה");
        title.setFont(Theme.big);
        final JLabel pill = new JLabel(" ");
        pill.setOpaque(true);
        pill.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
        pill.setVisible(false);
        final JTextPane code = new JTextPane();
        code.setEditable(false);
        code.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        code.setFont(Theme.mono != null ? Theme.mono.deriveFont(13.5f) : new Font(Font.MONOSPACED, Font.PLAIN, 13));
        code.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        code.setText(all.isEmpty() ? "" : "");
        JScrollPane cs = new JScrollPane(code);
        cs.setPreferredSize(new Dimension(560, 380));
        cs.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        if (sorted.isEmpty()) {
            title.setText("עוד אין הצעות");
            code.setText("הצעות לפתרון יש לתרגילים שנוצרו ב\"שליחת תרגיל ב-C#\".");
        }
        JPanel right = new JPanel(new BorderLayout(0, 8));
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.add(title, BorderLayout.NORTH);
        top.add(pill, BorderLayout.SOUTH);
        right.add(top, BorderLayout.NORTH);
        right.add(cs, BorderLayout.CENTER);
        JPanel mid = new JPanel(new BorderLayout(12, 0));
        mid.add(ls, BorderLayout.LINE_START);
        mid.add(right, BorderLayout.CENTER);
        content.add(mid, BorderLayout.CENTER);

        final JButton copy = new JButton("העתקה"), close = new JButton("סגירה");
        copy.putClientProperty("FlatLaf.style", "background:#2F6FED; foreground:#ffffff; arc:14; borderWidth:0; hoverBackground:#2457C5; disabledBackground:#B9C8EE; disabledText:#ffffff");
        copy.setFont(Theme.uiBold);
        copy.setPreferredSize(new Dimension(110, 34));
        copy.setEnabled(false);
        close.putClientProperty("FlatLaf.style", "arc:14; background:#ffffff");
        close.setPreferredSize(new Dimension(84, 34));
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 6));   // בלי שורת זכויות יוצרים בחלון הזה
        bp.add(copy);
        bp.add(close);
        content.add(bp, BorderLayout.SOUTH);
        close.addActionListener(e -> f.dispose());
        copy.addActionListener(e -> {
            String sel = code.getSelectedText();
            String text = sel != null && !sel.trim().isEmpty() ? sel : code.getText();
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            copy.setText(sel != null && !sel.trim().isEmpty() ? "הקטע הועתק ✓" : "הועתק ✓");
            new javax.swing.Timer(1600, x -> copy.setText("העתקה")) { { setRepeats(false); } }.start();
        });
        list.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            Object v = list.getSelectedValue();
            if (!(v instanceof SolutionStore.Entry)) {
                if (v != null) list.clearSelection();
                return;
            }
            SolutionStore.Entry s = (SolutionStore.Entry) v;
            title.setText("הצעה לפתרון — " + section(s.label));
            String[] st = status(s.status);
            pill.setText(st[0]);
            pill.setBackground(new Color(Integer.parseInt(st[1], 16)));
            pill.setForeground(new Color(Integer.parseInt(st[2], 16)));
            pill.setVisible(true);
            colorize(code, s.code);
            code.setCaretPosition(0);
            copy.setEnabled(true);
        });
        f.getRootPane().putClientProperty("dsviz.list", list);
        f.setContentPane(content);
        rtl(content);
        code.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        content.setOpaque(true);
        content.setBackground(Theme.BG);
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    static String[] status(String s) {
        String text = ExercisePipeline.statusText(s);
        switch (s == null ? "" : s) {
            case "ok": return new String[] { text, "E7F6EC", "1E6B34" };
            case "ran": return new String[] { text, "E8F0FE", "24489B" };
            case "failed": return new String[] { text, "FFF4DE", "8A5300" };
            default: return new String[] { text, "F1F3F7", "667085" };
        }
    }

    static String section(String label) {
        String l = label == null ? "" : label.trim();
        if (l.matches("[א-ת]")) return "סעיף " + l + "'";
        if (l.matches("\\d+")) return "סעיף " + l;
        return l;
    }

    private static final java.util.regex.Pattern TOKENS = java.util.regex.Pattern.compile(
            "(\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'|//[^\\n]*|\\b[A-Za-z_]\\w*\\b|\\d+)");
    private static final java.util.Set<String> KW = new java.util.HashSet<String>(java.util.Arrays.asList(("public private protected internal static void int long "
            + "double float bool char string new return if else while for foreach in do break continue class null true false this var out ref const switch case default").split(" ")));

    /** הקוד עם צבעים — כמו בעורך */
    static void colorize(JTextPane pane, String code) {
        pane.setText(code);
        StyledDocument doc = pane.getStyledDocument();
        SimpleAttributeSet kw = new SimpleAttributeSet(), st = new SimpleAttributeSet(), cm = new SimpleAttributeSet(), ty = new SimpleAttributeSet();
        StyleConstants.setForeground(kw, new Color(0x0000FF));
        StyleConstants.setForeground(st, new Color(0xA31515));
        StyleConstants.setForeground(cm, new Color(0x008000));
        StyleConstants.setForeground(ty, new Color(0x2B91AF));
        java.util.regex.Matcher m = TOKENS.matcher(code);
        while (m.find()) {
            String w = m.group();
            SimpleAttributeSet a = w.startsWith("//") ? cm : w.startsWith("\"") || w.startsWith("'") ? st : KW.contains(w) ? kw
                    : Character.isUpperCase(w.charAt(0)) ? ty : null;
            if (a != null) doc.setCharacterAttributes(m.start(), w.length(), a, false);
        }
    }

    // ------------------------------------------------------------------ רכיבים

    static JPanel header(String title, String sub) {
        JPanel h = new JPanel(new BorderLayout(0, 2));
        JLabel t = new JLabel(title);
        t.setFont(Theme.big);
        t.setForeground(Theme.TEXT);
        JLabel s = new JLabel(sub);
        s.setForeground(Theme.MUTED);
        h.add(t, BorderLayout.NORTH);
        h.add(s, BorderLayout.SOUTH);
        return h;
    }

    static JLabel bold(String s) {
        JLabel l = new JLabel(s);
        l.setFont(Theme.uiBold);
        l.setForeground(Theme.TEXT);
        return l;
    }

    static Component gap(int h) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(1, h));
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, h));
        return p;
    }

    static JPanel buttons(JButton ok, JButton cancel) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 6));
        p.add(ok);
        p.add(cancel);
        JPanel row = new JPanel(new BorderLayout());
        row.add(p, BorderLayout.CENTER);
        JLabel c = new JLabel(Theme.COPYRIGHT);
        c.setFont(Theme.small);
        c.setForeground(new Color(0xA0A8B8));
        c.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        c.putClientProperty("ltr", Boolean.TRUE);
        row.add(c, BorderLayout.LINE_END);
        return row;
    }

    /** מימין לשמאל; פאנלים שקופים (חוץ מהכרטיסים) */
    static void rtl(Container c) {
        c.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        clear(c);
    }

    private static void clear(Component c) {
        if (c instanceof JPanel && !(c instanceof Card) && !(c instanceof DashedPanel) && !(c instanceof Thumb)) ((JPanel) c).setOpaque(false);
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) clear(k);
    }

    static String size(long n) {
        return n < 1024 * 1024 ? Math.max(1, Math.round(n / 1024.0)) + "KB" : String.format("%.1fMB", n / 1024.0 / 1024.0);
    }

    /** כרטיס לבן מעוגל */
    static final class Card extends JPanel {
        Card(JComponent inner) {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
            add(inner, BorderLayout.CENTER);
            setAlignmentX(Component.RIGHT_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Theme.CARD);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 22, 22);
            g.setColor(Theme.BORDER);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 22, 22);
            g.dispose();
        }
    }

    /** אזור הגרירה — מסגרת מקווקוות */
    static final class DashedPanel extends JPanel {
        DashedPanel(java.awt.LayoutManager l) {
            super(l);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(0xFAFBFD));
            g.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 18, 18);
            g.setColor(new Color(0xC5CCD8));
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[] { 6f, 5f }, 0f));
            g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 18, 18);
            g.dispose();
        }
    }

    /** תמונה ממוזערת של פריט, עם × להסרה */
    static final class Thumb extends JPanel {
        Thumb(Entry e, Runnable remove) {
            super(new BorderLayout(0, 4));
            setOpaque(false);
            setPreferredSize(new Dimension(122, 112));
            setBorder(BorderFactory.createEmptyBorder(8, 8, 6, 8));
            JLabel pic;
            if (e.thumb != null) {
                int w = e.thumb.getWidth(null), h = e.thumb.getHeight(null);
                double k = Math.min(100.0 / Math.max(1, w), 64.0 / Math.max(1, h));
                pic = new JLabel(new ImageIcon(e.thumb.getScaledInstance(Math.max(1, (int) (w * k)), Math.max(1, (int) (h * k)), Image.SCALE_SMOOTH)));
            } else {
                pic = new JLabel(e.kind.equals("pdf") ? "PDF" : e.kind.equals("word") ? "Word" : "טקסט", SwingConstants.CENTER) {
                    @Override
                    protected void paintComponent(Graphics g0) {
                        Graphics2D g = (Graphics2D) g0.create();
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g.setColor(e.kind.equals("pdf") ? new Color(0xDC2626) : e.kind.equals("word") ? new Color(0x2563EB) : new Color(0x6B7280));
                        int bw = 64, bh = 44;
                        g.fillRoundRect((getWidth() - bw) / 2, (getHeight() - bh) / 2, bw, bh, 10, 10);
                        g.dispose();
                        super.paintComponent(g0);
                    }
                };
                pic.setForeground(Color.WHITE);
                pic.setFont(Theme.uiBold.deriveFont(15f));
            }
            add(pic, BorderLayout.CENTER);
            JLabel n = new JLabel(e.name, SwingConstants.CENTER);
            n.setFont(Theme.small);
            n.setToolTipText(e.name);
            add(n, BorderLayout.SOUTH);
            JButton x = new JButton("×");
            x.setToolTipText("הסרה");
            x.setFocusable(false);
            x.putClientProperty("FlatLaf.style", "arc:999; background:#9CA3AF; foreground:#ffffff; borderWidth:0; margin:0,0,0,0");
            x.setPreferredSize(new Dimension(20, 20));
            x.addActionListener(ev -> remove.run());
            JPanel xr = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            xr.setOpaque(false);
            xr.add(x);
            add(xr, BorderLayout.NORTH);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g.setColor(Theme.BORDER);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g.dispose();
        }
    }
}

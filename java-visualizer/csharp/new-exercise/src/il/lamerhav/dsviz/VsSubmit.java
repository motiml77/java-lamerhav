package il.lamerhav.dsviz;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * חלון אחד, שלושה מצבים, בעיצוב של האתר (RtlDialog): הטופס; אחרי "שליחה" — תצוגת הבנייה (השלבים); ובסוף — כרטיס קצר "התרגיל נוצר"
 * עם הסעיפים שעונים עליהם במילים (בלחיצה על "הצגה") ואיך פותחים ב-Visual Studio. כל ההודעות — בחלון של RtlDialog.
 * לסרטוני ההדרכה ולבדיקות: לשורש החלון יש "dsviz.addFile" (הוספת קובץ כמו "בחירת קבצים…").
 */
public final class VsSubmit {
    private VsSubmit() {}

    static final String TITLE = "שליחת תרגיל — C#";

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

    // ------------------------------------------------------------------ השמות התפוסים

    /** שמות הפרויקטים ב-.sln והתיקיות שבתיקיית התרגילים — הפרויקט החדש יקבל שם אחר מכולם */
    public static List<String> takenNames(Path root) {
        TreeSet<String> out = new TreeSet<String>();
        Path sln = CsSolution.solution(root);
        if (sln != null) {
            try {
                Matcher m = Pattern.compile("(?m)^\\s*Project\\(\"[^\"]*\"\\)\\s*=\\s*\"([^\"]+)\"").matcher(new String(Files.readAllBytes(sln), StandardCharsets.UTF_8));
                while (m.find()) out.add(m.group(1));
            } catch (Exception ignored) {
                // לא נקרא
            }
        }
        String[] dirs = CsSolution.exercisesDir(root).toFile().list();
        if (dirs != null) for (String d : dirs) if (new File(CsSolution.exercisesDir(root).toFile(), d).isDirectory()) out.add(d);
        return new ArrayList<String>(out);
    }

    /**
     * ExercisePipeline.csharp עם השמות התפוסים (הפרמטר האחרון, Collection&lt;String&gt; taken). בגרסה שעוד אין בה את הפרמטר —
     * בלעדיו (ואז רק התיקיות שב-Exercises נחשבות תפוסות).
     */
    static ExercisePipeline.Result build(WorkerClient client, Map<String, Object> raw, List<String> names, Path root, List<String> cs,
            ExercisePipeline.Progress p, Collection<String> taken) throws Exception {
        java.lang.reflect.Method m;
        try {
            m = ExercisePipeline.class.getDeclaredMethod("csharp", WorkerClient.class, Map.class, List.class, Path.class, List.class,
                    ExercisePipeline.Progress.class, Collection.class);
        } catch (NoSuchMethodException e) {
            return ExercisePipeline.csharp(client, raw, names, root, cs, p);
        }
        try {
            m.setAccessible(true);
            return (ExercisePipeline.Result) m.invoke(null, client, raw, names, root, cs, p, taken);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof Exception) throw (Exception) c;
            throw new RuntimeException(c);
        }
    }

    // ------------------------------------------------------------------ השלבים (מה ש-ExercisePipeline אומר תוך כדי)

    static final String[] STAGES = { "מכין את הקבצים", "שולח ל-Gemini", "בונה את התרגיל", "בודק שהקוד מתקמפל", "בודק את ההצעות לפתרון" };

    /** הודעה מהבנייה → מספר השלב (0-4), או -1: שורה אפורה מתחת לשלב הנוכחי (noteOf) — כמו stageFor ב-VS Code */
    static int stageOf(String text) {
        String t = text == null ? "" : text.trim();
        if (t.startsWith("מכין את הקבצים")) return 0;
        if (t.startsWith("שולח")) return 1;
        if (t.startsWith("בונה את התרגיל")) return 2;
        if (t.startsWith("בודק שהקוד מתקמפל")) return 3;
        if (t.startsWith("בודק שההצעות")) return 4;
        return -1;
    }

    static String noteOf(String text) {
        return (text == null ? "" : text.trim()).replaceAll("\\s+—\\s+", " · ").replaceFirst("\\s*\\(בדרך כלל[^)]*\\)", "");
    }

    /** "סעיף ד: עונים במילים" / "סעיפים ג, ד: עונים במילים" ("" — אין) */
    static String wordLabels(List<String[]> others) {
        List<String> labels = new ArrayList<String>();
        int n = 0;
        boolean all = true;
        for (String[] o : others) {
            if (o == null || o[1] == null || o[1].trim().isEmpty()) continue;
            n++;
            String l = o[0] == null ? "" : o[0].trim().replaceFirst("'$", "");
            if (l.isEmpty()) all = false;
            labels.add(l);
        }
        if (n == 0) return "";
        if (all) return (n == 1 ? "סעיף " : "סעיפים ") + String.join(", ", labels) + ": עונים במילים";
        return (n == 1 ? "סעיף אחד" : n + " סעיפים") + ": עונים במילים";
    }

    /** הקובץ עם Main מבין הקבצים (או הראשון) */
    static File mainFile(List<File> files) {
        Pattern main = Pattern.compile("\\bstatic\\s+(void|int|async\\s+Task)\\s+Main\\s*\\(");
        for (File f : files) {
            try {
                if (main.matcher(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)).find()) return f;
            } catch (Exception ignored) {
                // לא נקרא
            }
        }
        return files.isEmpty() ? null : files.get(0);
    }

    // ------------------------------------------------------------------ שליחת תרגיל

    public static void submit(Path root) {
        String bad = CsSolution.check(root);
        if (bad != null) {
            RtlDialog.show(null, TITLE, RtlDialog.Kind.ERROR, "תיקיית התרגילים לא מוכנה", bad + "\nמריצים שוב את ההתקנה (DSViz-Setup.exe) עם חבילת C#.");
            return;
        }
        WorkerClient.CLIENT = "dsviz-vs/1.0.1";
        WorkerClient client = WorkerClient.configured();
        if (client == null) {
            RtlDialog.show(null, TITLE, RtlDialog.Kind.ERROR, "השרת של שליחת תרגיל לא מוגדר", null);
            return;
        }
        while (!client.online(3500)) {   // בלי אינטרנט אין טעם לפתוח את החלון — אומרים את זה מיד
            int a = RtlDialog.ask(null, TITLE, RtlDialog.Kind.WARN, "אין חיבור לאינטרנט",
                    "שליחת תרגיל צריכה אינטרנט. שאר הכלים (הרצה ויזואלית, הוספת מבנה נתונים, דוגמאות) עובדים גם בלי.", "נסו שוב", "סגירה");
            if (a != 0) return;
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
        final CardLayout cards = new CardLayout();
        final JPanel deck = new JPanel(cards);
        // הטופס
        final List<Entry> entries = new ArrayList<Entry>();
        final JPanel items = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        final JLabel empty = RtlDialog.label("גוררים לכאן את דף התרגיל. PDF, Word או תמונה; צילום מסך: Ctrl+V", RtlDialog.ui(), RtlDialog.MUTED);
        final JLabel summary = RtlDialog.label(" ", RtlDialog.small(), RtlDialog.MUTED);
        final JLabel status = RtlDialog.label(" ", RtlDialog.ui(), RtlDialog.RED);
        final JTextArea note = new JTextArea(3, 40);
        final JButton send = RtlDialog.primary("שליחה ויצירת התרגיל"), cancel = RtlDialog.secondary("ביטול");
        final JButton pick = RtlDialog.secondary("בחירת קבצים…"), paste = RtlDialog.secondary("הדבקה (Ctrl+V)");
        int pastedImages, pastedTexts;
        // הבנייה
        final TopLine top = new TopLine();
        final JPanel thumbs = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 0));
        final StageRow[] rows = new StageRow[STAGES.length];
        final JPanel fail = new JPanel(new BorderLayout(12, 0));
        final JLabel failText = RtlDialog.label(" ", RtlDialog.ui(), RtlDialog.RED);
        final JButton retry = RtlDialog.primary("נסו שוב"), stop = RtlDialog.secondary("ביטול");
        final javax.swing.Timer timer = new javax.swing.Timer(500, e -> tick());
        int cur = -1, run;   // run — מספר השליחה; תוצאה של שליחה שבוטלה לא מוצגת
        long t0;
        boolean busy;
        // הסוף
        final JPanel done = new JPanel(new BorderLayout());

        SubmitWindow(Path root, WorkerClient client) {
            this.root = root;
            this.client = client;
        }

        void show() {
            deck.add(formView(), "form");
            deck.add(buildView(), "build");
            deck.add(done, "done");
            deck.setOpaque(true);
            deck.setBackground(RtlDialog.PANEL);

            pick.addActionListener(e -> pickFiles());
            paste.addActionListener(e -> pasteClipboard());
            cancel.addActionListener(e -> dlg.dispose());
            send.addActionListener(e -> send());
            retry.addActionListener(e -> send());
            stop.addActionListener(e -> backToForm());
            dlg.getRootPane().registerKeyboardAction(e -> {
                if (!note.isFocusOwner() && !busy) pasteClipboard();
            }, KeyStroke.getKeyStroke(KeyEvent.VK_V, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()), JComponent.WHEN_IN_FOCUSED_WINDOW);
            new DropTarget(dlg, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
                @SuppressWarnings("unchecked")
                public void drop(DropTargetDropEvent e) {
                    try {
                        e.acceptDrop(DnDConstants.ACTION_COPY);
                        if (busy) return;
                        Object v = e.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                        for (File f : (List<File>) v) addFile(f);
                    } catch (Exception ex) {
                        say(ex.getMessage());
                    }
                }
            });
            dlg.getRootPane().putClientProperty("dsviz.addFile", (java.util.function.Consumer<File>) this::addFile);
            dlg.getRootPane().putClientProperty("dsviz.pasteImage", (java.util.function.Consumer<File>) f -> {
                try {
                    add(ExerciseFiles.fromPastedImage(Files.readAllBytes(f.toPath()), ++pastedImages), "image", ImageIO.read(f), f.length());
                } catch (Exception ex) {
                    say(ex.getMessage());
                }
            });
            // לבדיקות ולסרטונים: הודעה מהבנייה כאילו הגיעה מ-ExercisePipeline (בלי שרת)
            dlg.getRootPane().putClientProperty("dsviz.progress", (java.util.function.Consumer<String>) t -> {
                if (cur < 0) startBuild();
                progress(t);
            });

            dlg.setContentPane(deck);
            RtlDialog.rtl(deck);
            refresh();
            dlg.pack();
            dlg.setMinimumSize(new Dimension(700, dlg.getHeight()));
            dlg.setLocationRelativeTo(null);
            dlg.setVisible(true);
            timer.stop();
        }

        // ---------------------------------------------------------- הטופס

        JComponent formView() {
            JPanel body = column();
            body.add(header("שליחת תרגיל", "מצרפים את דף התרגיל, ומקבלים פרויקט מוכן ב-Visual Studio: פעולה ריקה לכל סעיף, ו-Main לבדיקה."));
            body.add(RtlDialog.gap(14));
            // דף התרגיל
            JPanel drop = new DashedPanel(new BorderLayout(0, 8));
            items.setOpaque(false);
            JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
            btns.setOpaque(false);
            btns.add(pick);
            btns.add(paste);
            JPanel center = new JPanel(new BorderLayout());
            center.setOpaque(false);
            center.add(items, BorderLayout.CENTER);
            empty.setHorizontalAlignment(SwingConstants.CENTER);
            empty.setBorder(BorderFactory.createEmptyBorder(14, 0, 4, 0));
            center.add(empty, BorderLayout.SOUTH);
            drop.add(center, BorderLayout.CENTER);
            JPanel under = new JPanel(new BorderLayout(0, 4));
            under.setOpaque(false);
            under.add(btns, BorderLayout.NORTH);
            summary.setHorizontalAlignment(SwingConstants.CENTER);
            under.add(summary, BorderLayout.SOUTH);
            drop.add(under, BorderLayout.SOUTH);
            drop.setPreferredSize(new Dimension(640, 196));
            drop.setAlignmentX(Component.RIGHT_ALIGNMENT);
            drop.setMaximumSize(new Dimension(Integer.MAX_VALUE, 196));
            body.add(drop);
            body.add(RtlDialog.gap(14));
            // הערה
            body.add(RtlDialog.label("הערה (לא חובה)", RtlDialog.bold(), RtlDialog.INK));
            body.add(RtlDialog.gap(4));
            note.setLineWrap(true);
            note.setWrapStyleWord(true);
            note.setFont(RtlDialog.ui());
            note.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            JScrollPane ns = new JScrollPane(note);
            ns.setBorder(BorderFactory.createLineBorder(RtlDialog.LINE_2));
            ns.setAlignmentX(Component.RIGHT_ALIGNMENT);
            body.add(ns);
            body.add(RtlDialog.gap(4));
            body.add(RtlDialog.label("למשל: \"רק שאלה 2\", או: \"הפעולה בסעיף ב צריכה להיות רקורסיבית\"", RtlDialog.small(), RtlDialog.MUTED));
            body.add(RtlDialog.gap(10));
            body.add(RtlDialog.label("הפרויקט נכנס לתיקייה Exercises שב-" + root.getFileName() + ", ולפתרון של Visual Studio.", RtlDialog.small(), RtlDialog.MUTED));
            body.add(RtlDialog.label("הדף עובר דרך השרת של DSViz ל-Gemini של Google. לא לשלוח דפים עם פרטים אישיים.", RtlDialog.small(), RtlDialog.MUTED));
            body.add(RtlDialog.gap(8));
            body.add(status);
            JPanel foot = RtlDialog.footer();
            foot.add(send);
            foot.add(cancel);
            JLabel c = new JLabel(Theme.COPYRIGHT);
            c.setFont(RtlDialog.small());
            c.setForeground(new Color(0xA0A8B8));
            JPanel row = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    g.setColor(RtlDialog.PAGE);
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.setColor(RtlDialog.LINE);
                    g.drawLine(0, 0, getWidth(), 0);
                }
            };
            foot.setOpaque(false);
            row.add(foot, BorderLayout.CENTER);
            c.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
            row.add(c, BorderLayout.LINE_END);
            return page(body, row);
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
                say(e.getMessage());
            } catch (Exception e) {
                say("לא הצלחתי לקרוא את הקובץ " + f.getName() + ": " + e.getMessage());
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
                say(e.getMessage());
            } catch (Exception e) {
                say("בלוח אין תמונה. מצלמים את התרגיל (Win+Shift+S) ואז Ctrl+V כאן בחלון.");
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
            say(" ");
            refresh();
        }

        void refresh() {
            items.removeAll();
            for (Entry e : entries) items.add(new Thumb(e, () -> {
                entries.remove(e);
                refresh();
            }, false));
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
            summary.setText(entries.isEmpty() ? " " : RtlDialog.rtl(String.join(" ו-", parts) + " · " + size(total)));
            send.setEnabled(!busy && !entries.isEmpty());
            RtlDialog.rtl(items);
            items.revalidate();
            items.repaint();
        }

        /** שורה אדומה בטופס (שגיאה בקובץ / בלוח) */
        void say(String text) {
            status.setText(text == null || text.trim().isEmpty() ? " " : RtlDialog.rtl(text));
        }

        // ---------------------------------------------------------- הבנייה

        JComponent buildView() {
            JPanel body = column();
            body.add(header("בונה את התרגיל", "בדרך כלל עד דקה. בסוף הפתרון נפתח ב-Visual Studio."));
            body.add(RtlDialog.gap(12));
            thumbs.setOpaque(false);
            thumbs.setAlignmentX(Component.RIGHT_ALIGNMENT);
            body.add(thumbs);
            body.add(RtlDialog.gap(12));
            JPanel list = new JPanel();
            list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
            list.setOpaque(false);
            for (int i = 0; i < STAGES.length; i++) {
                rows[i] = new StageRow(STAGES[i]);
                if (i > 0) list.add(RtlDialog.gap(6));
                list.add(rows[i]);
            }
            body.add(RtlDialog.card(list, 12, 14));
            body.add(RtlDialog.gap(12));
            fail.setOpaque(false);
            fail.add(failText, BorderLayout.CENTER);
            JPanel rb = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
            rb.setOpaque(false);
            rb.add(retry);
            fail.add(rb, BorderLayout.LINE_START);
            fail.setAlignmentX(Component.RIGHT_ALIGNMENT);
            fail.setVisible(false);
            body.add(fail);
            JPanel foot = RtlDialog.footer();
            foot.add(stop);
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false);
            wrap.add(top, BorderLayout.NORTH);
            wrap.add(page(body, foot), BorderLayout.CENTER);
            return wrap;
        }

        void startBuild() {
            busy = true;
            thumbs.removeAll();
            for (Entry e : entries) thumbs.add(new Thumb(e, null, true));
            RtlDialog.rtl(thumbs);
            for (StageRow r : rows) r.set(StageRow.PENDING, "");
            fail.setVisible(false);
            cur = -1;
            setStage(0);
            cards.show(deck, "build");
            timer.start();
        }

        void setStage(int i) {
            if (i < cur) return;
            cur = i;
            t0 = System.currentTimeMillis();
            for (int k = 0; k < rows.length; k++) {
                if (k < i) rows[k].set(StageRow.DONE, "");
                else if (k == i) rows[k].set(StageRow.NOW, "");
                else rows[k].set(StageRow.PENDING, "");
            }
            top.fraction = (i + 0.5) / rows.length;
            top.repaint();
        }

        void tick() {
            if (cur < 0 || rows[cur].state != StageRow.NOW) return;
            long s = (System.currentTimeMillis() - t0) / 1000;
            rows[cur].secs.setText(s >= 1 ? RtlDialog.rtl(s + " שנ'") : " ");
        }

        void progress(String text) {
            int st = stageOf(text);
            if (st >= 0) setStage(st);
            else rows[Math.max(cur, 0)].setNote(noteOf(text));
        }

        void failed(String text) {
            timer.stop();
            busy = false;
            rows[Math.max(cur, 0)].set(StageRow.BAD, "");
            String t = text == null || text.trim().isEmpty() ? "לא הצלחתי ליצור את התרגיל." : text.trim().split("\\r?\\n")[0].replaceAll("\\s+—\\s+", ", ");
            if (t.length() > 150) t = t.substring(0, 148) + "…";
            failText.setText(RtlDialog.rtl(t));
            failText.setToolTipText(text);
            fail.setVisible(true);
            deck.revalidate();
        }

        /** "ביטול" בזמן הבנייה (או אחרי שגיאה): חוזרים לטופס עם אותם קבצים. התוצאה של השליחה הזאת כבר לא תוצג */
        void backToForm() {
            run++;
            timer.stop();
            busy = false;
            cur = -1;
            dlg.setCursor(Cursor.getDefaultCursor());
            for (JComponent c : new JComponent[] { pick, paste, note }) c.setEnabled(true);
            refresh();
            cards.show(deck, "form");
        }

        void send() {
            if (entries.isEmpty()) return;
            final List<ExerciseFiles.Item> list = new ArrayList<ExerciseFiles.Item>();
            final List<String> names = new ArrayList<String>();
            for (Entry e : entries) {
                list.add(e.item);
                names.add(e.name);
            }
            try {
                ExerciseFiles.checkAll(list);
            } catch (ExerciseFiles.UserError e) {
                say(e.getMessage());
                if (busy || cur >= 0) backToForm();
                return;
            }
            final int me = ++run;
            startBuild();
            final String text = note.getText();
            final List<String> taken = takenNames(root);
            new SwingWorker<ExercisePipeline.Result, String>() {
                String error;

                protected ExercisePipeline.Result doInBackground() throws Exception {
                    publish("מכין את הקבצים…");
                    publish("שולח את התרגיל… (בדרך כלל עד דקה)");
                    Map<String, Object> raw;
                    try {
                        raw = client.exercise(list, text, "csharp");
                    } catch (WorkerClient.AiError e) {
                        error = e.getMessage();
                        return null;
                    }
                    if (me != run) return null;
                    publish("בונה את התרגיל…");
                    ExercisePipeline.Result r = build(client, raw, names, root, dsvizCs(), new ExercisePipeline.Progress() {
                        public void say(String t) {
                            publish(t);
                        }

                        public boolean cancelled() {
                            return me != run;
                        }
                    }, taken);
                    if (!r.entries.isEmpty()) new SolutionStore(storeFile()).add(r.entries);
                    return r;
                }

                protected void process(List<String> chunks) {
                    if (me != run) return;
                    for (String c : chunks) progress(c);
                }

                protected void done() {
                    if (me != run) return;   // בוטל — החלון כבר חזר לטופס
                    ExercisePipeline.Result r = null;
                    try {
                        r = get();
                    } catch (Exception e) {
                        error = "לא הצלחתי ליצור את התרגיל: " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                    }
                    if (r == null) {
                        failed(error);
                        return;
                    }
                    finished(r);
                }
            }.execute();
        }

        // ---------------------------------------------------------- הסוף: כרטיס קצר

        void finished(ExercisePipeline.Result r) {
            timer.stop();
            busy = false;
            done.removeAll();
            done.add(doneView(r), BorderLayout.CENTER);
            RtlDialog.rtl(done);
            dlg.setTitle(TITLE);
            cards.show(deck, "done");
            dlg.pack();
            dlg.setLocationRelativeTo(null);
            if (!Boolean.getBoolean("dsviz.noOpen") && java.awt.Desktop.isDesktopSupported()) {
                try {
                    Path sln = CsSolution.solution(root);
                    java.awt.Desktop.getDesktop().open((sln != null ? sln : r.focus.toPath()).toFile());
                } catch (Exception ignored) {
                    // לא נפתח — הפרויקט בכל זאת בפתרון
                }
            }
        }

        JComponent doneView(ExercisePipeline.Result r) {
            final int w = 600;
            JPanel body = column();
            JPanel in = new JPanel();
            in.setLayout(new BoxLayout(in, BoxLayout.Y_AXIS));
            in.setOpaque(false);
            boolean errs = !r.errors.isEmpty();
            JPanel head = new JPanel(new BorderLayout(8, 0));
            head.setOpaque(false);
            JLabel mark = new JLabel(errs ? "●" : "✓");
            mark.setFont(RtlDialog.bold().deriveFont(Font.BOLD, 18f));
            mark.setForeground(errs ? RtlDialog.AMBER : RtlDialog.GREEN);
            head.add(mark, BorderLayout.LINE_START);
            head.add(RtlDialog.label(errs ? "התרגיל נוצר · " + (r.errors.size() == 1 ? "יש שגיאת קומפילציה אחת" : "יש " + r.errors.size() + " שגיאות קומפילציה")
                    : "התרגיל נוצר", RtlDialog.bold().deriveFont(Font.BOLD, 18f), RtlDialog.INK), BorderLayout.CENTER);
            head.setAlignmentX(Component.RIGHT_ALIGNMENT);
            in.add(head);
            String tasks = r.tasks == 0 ? "אין בו סעיפים בקוד" : r.tasks == 1 ? "סעיף אחד" : r.tasks + " סעיפים";
            in.add(RtlDialog.label(r.folderName + RtlDialog.RLM + " · " + tasks, RtlDialog.ui(), RtlDialog.MUTED));   // RLM: המספר לא נצמד לשם
            // הסעיפים שעונים עליהם במילים: שורה אחת, והטקסט עצמו רק בלחיצה על "הצגה"
            String words = wordLabels(r.others);
            if (!words.isEmpty()) {
                in.add(RtlDialog.gap(10));
                JPanel wr = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
                wr.setOpaque(false);
                wr.add(RtlDialog.label(words, RtlDialog.ui(), RtlDialog.INK_2));
                final JButton show = RtlDialog.link("הצגה");
                wr.add(RtlDialog.label("  ·  ", RtlDialog.ui(), RtlDialog.MUTED));
                wr.add(show);
                wr.setAlignmentX(Component.RIGHT_ALIGNMENT);
                wr.setMaximumSize(new Dimension(Integer.MAX_VALUE, wr.getPreferredSize().height + 4));
                in.add(wr);
                final JPanel parts = new JPanel();
                parts.setLayout(new BoxLayout(parts, BoxLayout.Y_AXIS));
                parts.setOpaque(false);
                parts.setAlignmentX(Component.RIGHT_ALIGNMENT);
                for (String[] o : r.others) {
                    if (o[1] == null || o[1].trim().isEmpty()) continue;
                    JPanel p = new JPanel();
                    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
                    p.setOpaque(false);
                    p.add(RtlDialog.label(section(o[0]), RtlDialog.bold(), RtlDialog.INK));
                    p.add(RtlDialog.gap(4));
                    p.add(RtlDialog.rich(o[1], w - 64, RtlDialog.INK_2));
                    parts.add(RtlDialog.gap(8));
                    parts.add(new RtlDialog.Panel(new BorderLayout(), RtlDialog.PAGE, RtlDialog.LINE) {
                        {
                            setBorder(BorderFactory.createEmptyBorder(8, 12, 10, 12));
                            add(p, BorderLayout.CENTER);
                            setAlignmentX(Component.RIGHT_ALIGNMENT);
                        }
                    });
                }
                parts.setVisible(false);
                in.add(parts);
                show.addActionListener(e -> {
                    parts.setVisible(!parts.isVisible());
                    show.setText(parts.isVisible() ? "הסתרה" : "הצגה");
                    RtlDialog.rtl(parts);
                    dlg.pack();
                });
            }
            // שגיאות קומפילציה: כרטיס לכל שגיאה (עד 3), עם ההסבר בעברית
            if (errs) {
                in.add(RtlDialog.gap(6));
                for (int i = 0; i < Math.min(3, r.errors.size()); i++) {
                    in.add(RtlDialog.gap(6));
                    in.add(RtlDialog.errorCard(r.errors.get(i), w));
                }
                if (r.errors.size() > 3) in.add(RtlDialog.label("ועוד " + (r.errors.size() - 3) + " בחלון Error List של Visual Studio", RtlDialog.small(), RtlDialog.MUTED));
                in.add(RtlDialog.gap(4));
                in.add(RtlDialog.label("בדרך כלל זה ב-Main (הנתונים לדוגמה). אפשר לתקן, או לשלוח שוב.", RtlDialog.small(), RtlDialog.MUTED));
            }
            body.add(RtlDialog.card(in, 14, 16));
            body.add(RtlDialog.gap(14));
            // איך פותחים ב-Visual Studio — קצר: קודם הקובץ עם Main
            File main = r.files.isEmpty() ? null : mainFile(r.files);
            body.add(RtlDialog.label("ב-Visual Studio", RtlDialog.bold(), RtlDialog.INK));
            body.add(RtlDialog.gap(4));
            String[] steps = {
                "אם הוא שואל, מאשרים Reload.",
                "ב-Solution Explorer: קליק ימני על " + r.folderName + " " + RtlDialog.ARROW + " Set as Startup Project.",
                main == null ? "עונים במקום המסומן בכל סעיף." : "פותחים את " + main.getName() + " (שם Main), ועונים במקום המסומן.",
                "מריצים: בתפריט שליד הכפתור הירוק בוחרים \"הרצה ויזואלית\"."
            };
            for (int i = 0; i < steps.length; i++) body.add(step(i + 1, steps[i]));
            if (!r.entries.isEmpty()) {
                body.add(RtlDialog.gap(6));
                body.add(RtlDialog.label("נתקעתם? בקיצור \"הצעה לפתרון ב-C#\" יש הצעה לכל סעיף.", RtlDialog.small(), RtlDialog.MUTED));
            }
            JButton close = RtlDialog.primary("סגירה");
            close.addActionListener(e -> dlg.dispose());
            JPanel foot = RtlDialog.footer();
            foot.add(close);
            dlg.getRootPane().setDefaultButton(close);
            return page(body, foot);
        }

        JComponent step(int n, String text) {
            JPanel p = new JPanel(new BorderLayout(8, 0));
            p.setOpaque(false);
            JLabel num = new JLabel(String.valueOf(n), SwingConstants.CENTER) {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(RtlDialog.SUNK);
                    g.fillRoundRect(0, (getHeight() - 18) / 2, 18, 18, 8, 8);
                    g.setColor(RtlDialog.LINE_2);
                    g.drawRoundRect(0, (getHeight() - 18) / 2, 17, 17, 8, 8);
                    g.dispose();
                    super.paintComponent(g0);
                }
            };
            num.setFont(RtlDialog.mono(11f));
            num.setForeground(RtlDialog.INK_2);
            num.setPreferredSize(new Dimension(18, 22));
            p.add(num, BorderLayout.LINE_START);
            p.add(RtlDialog.label(text, RtlDialog.ui(), RtlDialog.INK_2), BorderLayout.CENTER);
            p.setAlignmentX(Component.RIGHT_ALIGNMENT);
            p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            return p;
        }
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
        ls.setBorder(BorderFactory.createLineBorder(RtlDialog.LINE));

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
        cs.setBorder(BorderFactory.createLineBorder(RtlDialog.LINE));
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

        final JButton copy = RtlDialog.primary("העתקה"), close = RtlDialog.secondary("סגירה");
        copy.setPreferredSize(new Dimension(110, 32));
        copy.setEnabled(false);
        close.setPreferredSize(new Dimension(84, 32));
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
        content.setBackground(RtlDialog.PANEL);
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
        JPanel h = new JPanel();
        h.setLayout(new BoxLayout(h, BoxLayout.Y_AXIS));
        h.setOpaque(false);
        h.add(RtlDialog.label(title, RtlDialog.bold().deriveFont(Font.BOLD, 18f), RtlDialog.INK));
        h.add(RtlDialog.gap(2));
        h.add(RtlDialog.label(sub, RtlDialog.ui(), RtlDialog.MUTED));
        h.setAlignmentX(Component.RIGHT_ALIGNMENT);
        return h;
    }

    static JLabel bold(String s) {
        return RtlDialog.label(s, RtlDialog.bold(), RtlDialog.INK);
    }

    static Component gap(int h) {
        return RtlDialog.gap(h);
    }

    /** עמודה: רכיבים אחד מתחת לשני, מיושרים לימין */
    static JPanel column() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        return p;
    }

    /** תוכן עם שוליים, ושורת הכפתורים למטה */
    static JPanel page(JComponent body, JComponent foot) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(true);
        p.setBackground(RtlDialog.PANEL);
        body.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
        JPanel top = new JPanel(new BorderLayout());   // התוכן בגובה שלו; המקום שנשאר (החלון בגובה של המצב הגבוה) — למטה
        top.setOpaque(false);
        top.add(body, BorderLayout.NORTH);
        p.add(top, BorderLayout.CENTER);
        p.add(foot, BorderLayout.SOUTH);
        return p;
    }

    /** מימין לשמאל */
    static void rtl(Container c) {
        RtlDialog.rtl(c);
    }

    static String size(long n) {
        return n < 1024 * 1024 ? Math.max(1, Math.round(n / 1024.0)) + "KB" : String.format("%.1fMB", n / 1024.0 / 1024.0);
    }

    /** אזור הגרירה — מסגרת מקווקוות דקה, פינות קטנות */
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
            g.setColor(RtlDialog.PAGE);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g.setColor(RtlDialog.LINE_2);
            g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[] { 4f, 3f }, 0f));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g.dispose();
        }
    }

    /** תמונה ממוזערת של פריט (עם × להסרה; בתצוגת הבנייה — קטנה ובלי ×) */
    static final class Thumb extends JPanel {
        Thumb(Entry e, Runnable remove, boolean small) {
            super(new BorderLayout(0, 4));
            setOpaque(false);
            int w = small ? 84 : 108, h = small ? 52 : 64;
            setPreferredSize(new Dimension(w + 12, h + 34));
            setBorder(BorderFactory.createEmptyBorder(6, 6, 5, 6));
            JLabel pic;
            if (e.thumb != null) {
                int iw = e.thumb.getWidth(null), ih = e.thumb.getHeight(null);
                double k = Math.min((double) w / Math.max(1, iw), (double) h / Math.max(1, ih));
                pic = new JLabel(new ImageIcon(e.thumb.getScaledInstance(Math.max(1, (int) (iw * k)), Math.max(1, (int) (ih * k)), Image.SCALE_SMOOTH)));
            } else {
                pic = new JLabel(e.kind.equals("pdf") ? "PDF" : e.kind.equals("word") ? "Word" : "TXT", SwingConstants.CENTER) {
                    @Override
                    protected void paintComponent(Graphics g0) {
                        Graphics2D g = (Graphics2D) g0.create();
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g.setColor(e.kind.equals("pdf") ? RtlDialog.RED : e.kind.equals("word") ? RtlDialog.ACCENT : RtlDialog.MUTED);
                        int bw = 44, bh = 26;
                        g.fillRoundRect((getWidth() - bw) / 2, (getHeight() - bh) / 2, bw, bh, 6, 6);
                        g.dispose();
                        super.paintComponent(g0);
                    }
                };
                pic.setForeground(Color.WHITE);
                pic.setFont(RtlDialog.mono(11.5f).deriveFont(Font.BOLD));
            }
            JPanel box = new RtlDialog.Panel(new BorderLayout(), RtlDialog.SUNK, null);
            box.add(pic, BorderLayout.CENTER);
            add(box, BorderLayout.CENTER);
            JLabel n = new JLabel(e.name, SwingConstants.CENTER);
            n.setFont(RtlDialog.small().deriveFont(11f));
            n.setForeground(RtlDialog.MUTED);
            n.setToolTipText(e.name);
            add(n, BorderLayout.SOUTH);
            if (remove != null && !small) {
                JButton x = new JButton("×");
                x.setToolTipText("הסרה");
                x.setFocusable(false);
                x.putClientProperty("FlatLaf.style", "arc:6; background:#ffffff; foreground:#3B4250; borderColor:#CDD2DA; margin:0,0,0,0; focusWidth:0");
                x.setPreferredSize(new Dimension(20, 20));
                x.addActionListener(ev -> remove.run());
                JPanel xr = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
                xr.setOpaque(false);
                xr.add(x);
                xr.putClientProperty("ltr", Boolean.TRUE);
                JPanel layer = new JPanel(new BorderLayout());
                layer.setOpaque(false);
                layer.add(xr, BorderLayout.NORTH);
                box.add(layer, BorderLayout.NORTH);
            }
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g.setColor(RtlDialog.LINE);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g.dispose();
        }
    }

    /** שורה ברשימת השלבים: סימן (✓ ירוק / נקודה כחולה / עיגול אפור / ✕ אדום), השם, השניות, ושורה אפורה קטנה מתחת */
    static final class StageRow extends JPanel {
        static final int PENDING = 0, NOW = 1, DONE = 2, BAD = 3;
        int state;
        final JLabel name, secs, note;
        final JComponent icon;

        StageRow(String text) {
            super(new BorderLayout(10, 0));
            setOpaque(false);
            icon = new JComponent() {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int cx = getWidth() / 2, cy = 11;
                    if (state == DONE || state == BAD) {
                        g.setColor(state == DONE ? RtlDialog.GREEN : RtlDialog.RED);
                        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        if (state == DONE) {
                            g.drawLine(cx - 5, cy, cx - 1, cy + 4);
                            g.drawLine(cx - 1, cy + 4, cx + 5, cy - 4);
                        } else {
                            g.drawLine(cx - 4, cy - 4, cx + 4, cy + 4);
                            g.drawLine(cx - 4, cy + 4, cx + 4, cy - 4);
                        }
                    } else if (state == NOW) {
                        g.setColor(RtlDialog.ACCENT);
                        g.fillOval(cx - 4, cy - 4, 8, 8);
                    } else {
                        g.setColor(RtlDialog.LINE_2);
                        g.setStroke(new BasicStroke(1.5f));
                        g.drawOval(cx - 4, cy - 4, 8, 8);
                    }
                    g.dispose();
                }
            };
            icon.setPreferredSize(new Dimension(16, 22));
            JPanel iw = new JPanel(new BorderLayout());
            iw.setOpaque(false);
            iw.add(icon, BorderLayout.NORTH);
            add(iw, BorderLayout.LINE_START);
            name = RtlDialog.label(text, RtlDialog.ui(), RtlDialog.MUTED);
            secs = RtlDialog.label(" ", RtlDialog.small(), RtlDialog.MUTED);
            note = RtlDialog.label(" ", RtlDialog.small(), RtlDialog.MUTED);
            JPanel line = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
            line.setOpaque(false);
            line.add(name);
            line.add(RtlDialog.gap(1));
            JPanel sp = new JPanel();
            sp.setOpaque(false);
            sp.setPreferredSize(new Dimension(10, 1));
            line.add(sp);
            line.add(secs);
            JPanel col = new JPanel(new BorderLayout());
            col.setOpaque(false);
            col.add(line, BorderLayout.NORTH);
            col.add(note, BorderLayout.CENTER);
            add(col, BorderLayout.CENTER);
            note.setVisible(false);
            setAlignmentX(Component.RIGHT_ALIGNMENT);
        }

        void set(int s, String n) {
            state = s;
            name.setFont(s == NOW ? RtlDialog.bold() : RtlDialog.ui());
            name.setForeground(s == PENDING ? new Color(0x9AA1AD) : s == DONE ? RtlDialog.INK_2 : RtlDialog.INK);
            secs.setText(" ");
            if (s != BAD) setNote(n);   // אחרי שגיאה — השורה האפורה נשארת
            icon.repaint();
        }

        void setNote(String n) {
            boolean has = n != null && !n.trim().isEmpty();
            note.setText(has ? RtlDialog.rtl(n.trim()) : " ");
            note.setVisible(has);
            revalidate();
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /** קו כחול דק למעלה, שמתמלא מימין לשמאל לפי השלב */
    static final class TopLine extends JComponent {
        double fraction;

        TopLine() {
            setPreferredSize(new Dimension(10, 2));
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = (int) Math.round(getWidth() * Math.max(0, Math.min(1, fraction)));
            g.setColor(RtlDialog.PANEL);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(RtlDialog.ACCENT);
            g.fillRect(getWidth() - w, 0, w, getHeight());
        }
    }
}

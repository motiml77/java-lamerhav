package il.lamerhav.dsviz;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

/**
 * החלון הצף "השאלה" ליד Visual Studio: הנוסח של השאלה, הציורים, "לפעולה של סעיף א" (פותח את הקובץ ב-Visual Studio, בפעולה),
 * "הצעה לפתרון" לכל סעיף, הפלט הרצוי, ובסוף "סיימתי את התרגיל" — סימון ירוק, והפרויקט נמחק (BankVs.complete).
 * סוגרים ופותחים מתי שרוצים (הקיצור "השאלה — מאגר תרגילים C#" פותח את השאלה האחרונה); "תמיד מעל Visual Studio" — כברירת מחדל.
 */
final class BankVsFloat {
    private BankVsFloat() {}

    static final int WIDTH = 470;
    static JFrame current;
    static BankStore.Question question;
    private static Rectangle lastBounds;
    /** לבדיקות: מה מוצג */
    static final List<String> shown = new ArrayList<String>();

    static File lastFile() {
        return Paths.get(System.getProperty("user.home"), ".dsviz", "bank-cs-last.txt").toFile();
    }

    /** השאלה האחרונה שנפתחה בחלון הצף (או שנוצר ממנה תרגיל) */
    static void showLast(Component parent) {
        String id = "";
        try {
            id = new String(Files.readAllBytes(lastFile().toPath()), StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            // עוד לא היה
        }
        BankStore.Question q = null;
        for (BankStore.Question x : BankVs.store.questions) if (x.id.equals(id)) q = x;
        if (q == null) {
            RtlDialog.show(parent, "השאלה", RtlDialog.Kind.INFO, "עוד לא נבחרה שאלה",
                    "פותחים את \"מאגר תרגילים\", בוחרים שאלה ויוצרים ממנה תרגיל — והשאלה תופיע כאן, ליד Visual Studio.");
            BankVsWindow.open(null, null);
            return;
        }
        show(q);
    }

    static void show(BankStore.Question q) {
        question = q;
        try {
            File f = lastFile();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), q.id.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // לא נשמר — לא נורא
        }
        if (current != null) {
            lastBounds = current.getBounds();
            current.dispose();
        }
        final JFrame f = new JFrame(RtlDialog.rtl("השאלה — " + q.title));
        f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        f.setAlwaysOnTop(true);
        try {
            f.setIconImage(javax.imageio.ImageIO.read(BankVsFloat.class.getResource("/icons/question.png")));
        } catch (Exception ignored) {
            // בלי סמל
        }
        current = f;
        render();
        final Runnable onChange = () -> SwingUtilities.invokeLater(() -> {
            if (current == f && f.isDisplayable()) render();
        });
        BankProgress.get().listen(onChange);
        f.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                BankProgress.get().unlisten(onChange);
                lastBounds = f.getBounds();
                if (current == f) current = null;
            }
        });
        if (lastBounds != null) f.setBounds(lastBounds);
        else {
            Rectangle s = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            f.setBounds(s.x + s.width - WIDTH - 12, s.y + 40, WIDTH, Math.min(s.height - 80, 900));
        }
        f.setVisible(true);
    }

    /** התוכן (שוב אחרי כל שינוי ב-V) */
    static void render() {
        final JFrame f = current;
        final BankStore.Question q = question;
        if (f == null || q == null) return;
        shown.clear();
        JPanel page = new JPanel(new BorderLayout());
        page.setBackground(RtlDialog.PAGE);
        JPanel top = BankVsPanel.column();
        top.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));
        top.add(BankVsPanel.titleRow(q, BankVs.topicTitle(q.topic)));
        // איפה התרגיל: הפרויקט בפתרון, ואיך מריצים
        List<File> projects = BankVs.projects(q);
        if (!projects.isEmpty()) {
            top.add(RtlDialog.gap(8));
            String name = projects.get(projects.size() - 1).getName();
            JPanel how = BankVsPanel.column();
            how.add(RtlDialog.label("התרגיל בפתרון: " + name, RtlDialog.bold(), RtlDialog.INK));
            how.add(RtlDialog.gap(3));
            how.add(BankVsPanel.text("מריצים: ב-Solution Explorer קליק ימני על " + name + " ובוחרים Set as Startup Project, "
                    + "ואז \"הרצה ויזואלית\" ליד הכפתור הירוק.", WIDTH - 64, RtlDialog.INK_2));
            RtlDialog.Panel box = new RtlDialog.Panel(new BorderLayout(), new Color(0xEEF4FF), new Color(0xC9D8F5));
            box.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            box.add(how, BorderLayout.CENTER);
            box.setAlignmentX(Component.RIGHT_ALIGNMENT);
            top.add(box);
            shown.add("PROJECT " + name);
        }
        final JCheckBox pin = new JCheckBox(RtlDialog.rtl("תמיד מעל Visual Studio"), f.isAlwaysOnTop());
        pin.setOpaque(false);
        pin.setFont(RtlDialog.small());
        pin.setForeground(RtlDialog.MUTED);
        pin.setAlignmentX(Component.RIGHT_ALIGNMENT);
        pin.addActionListener(e -> f.setAlwaysOnTop(pin.isSelected()));
        top.add(RtlDialog.gap(4));
        top.add(pin);
        page.add(top, BorderLayout.NORTH);

        JPanel body = BankVsPanel.column();
        body.setBorder(BorderFactory.createEmptyBorder(4, 14, 14, 14));
        body.add(BankVsPanel.build(q, WIDTH - 60, new BankVsPanel.Actions() {
            public void jump(AiScaffold.Task t) {
                BankVsFloat.jump(f, q, t);
            }

            public void solution(AiScaffold.Task t) {
                BankVsFloat.solution(f, t);
            }
        }));
        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(RtlDialog.PAGE);
        holder.add(body, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(holder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, RtlDialog.LINE));
        sp.getVerticalScrollBar().setUnitIncrement(18);
        sp.getViewport().setBackground(RtlDialog.PAGE);
        page.add(sp, BorderLayout.CENTER);

        boolean done = BankProgress.get().done(q.id);
        JPanel foot = new JPanel(new BorderLayout());
        foot.setBackground(RtlDialog.PANEL);
        foot.setBorder(BorderFactory.createEmptyBorder(10, 16, 12, 16));
        DoneButton db = new DoneButton(done, () -> {
            if (BankProgress.get().done(q.id)) BankProgress.get().setDone(q.id, false);
            else if (BankVs.complete(f, q, true)) f.dispose();
        });
        foot.add(db, BorderLayout.CENTER);
        page.add(foot, BorderLayout.SOUTH);
        shown.add("DONE-BUTTON " + (done ? "done" : "todo"));
        for (BankStore.Section s : q.sections) shown.add("SECTION " + s.label);

        f.setContentPane(page);
        RtlDialog.rtl(page);
        f.getContentPane().validate();
        f.repaint();
    }

    /** "לפעולה של סעיף א": פותח ב-Visual Studio את הקובץ של הפעולה, בשורה שלה */
    static void jump(Component parent, BankStore.Question q, AiScaffold.Task t) {
        List<File> projects = BankVs.projects(q);
        if (projects.isEmpty()) {
            RtlDialog.show(parent, "השאלה", RtlDialog.Kind.INFO, "עוד אין תרגיל מהשאלה",
                    "יוצרים אותו בחלון \"מאגר תרגילים\": בוחרים את השאלה ולוחצים \"צור תרגיל\".");
            return;
        }
        File dir = projects.get(projects.size() - 1);
        File file = new File(dir, t.className + ".cs");
        if (!file.isFile()) {
            File[] cs = dir.listFiles((d, n) -> n.endsWith(".cs"));
            for (File c : cs == null ? new File[0] : cs) if (BankVs.methodLine(c, t.methodName) > 0) file = c;
        }
        BankVs.openFile(file, BankVs.methodLine(file, t.methodName));
    }

    /** "הצעה לפתרון": הפעולה הפתורה של הסעיף */
    static void solution(Component parent, AiScaffold.Task t) {
        String label = t.label.replaceAll("['׳]+$", "");
        JPanel body = new JPanel();
        body.setLayout(new javax.swing.BoxLayout(body, javax.swing.BoxLayout.Y_AXIS));
        body.add(RtlDialog.heading(RtlDialog.Kind.INFO, "הצעה לפתרון — סעיף " + label));
        body.add(RtlDialog.gap(6));
        body.add(RtlDialog.label("כדאי לנסות קודם לבד. ההצעה היא דרך אחת — יש עוד דרכים נכונות.", RtlDialog.small(), RtlDialog.MUTED));
        body.add(RtlDialog.gap(10));
        body.add(RtlDialog.codeBlock(t.solution, 520));
        shown.add("SOLUTION " + label);
        RtlDialog.dialog(parent, "הצעה לפתרון", body, "סגירה");
    }

    /** כפתור רחב: לפני — "סיימתי את התרגיל" (מסגרת ירוקה); אחרי — רקע ירוק ו"עשיתי את התרגיל" (לחיצה מבטלת את הסימון) */
    static final class DoneButton extends JComponent {
        final boolean done;
        boolean hover;

        DoneButton(boolean done, final Runnable toggle) {
            this.done = done;
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(RtlDialog.rtl(done ? "לחיצה מבטלת את הסימון" : "סימון ירוק — והפרויקט של התרגיל נמחק, כדי שהפתרון לא יתמלא בתרגילים ישנים"));
            setPreferredSize(new Dimension(200, 42));
            setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
            putClientProperty("ltr", Boolean.TRUE);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }

                @Override
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (SwingUtilities.isLeftMouseButton(e) && contains(e.getPoint())) toggle.run();
                }
            });
            this.toggle = toggle;
        }

        final Runnable toggle;

        /** לבדיקות: כמו לחיצה */
        void click() {
            toggle.run();
        }

        String text() {
            return done ? "עשיתי את התרגיל" : "סיימתי את התרגיל";
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            Color green = RtlDialog.GREEN;
            g.setColor(done ? green : hover ? new Color(0xEAF7EF) : Color.WHITE);
            g.fillRoundRect(0, 0, w - 1, h - 1, 14, 14);
            g.setColor(green);
            g.setStroke(new BasicStroke(done ? 1f : 2f));
            g.drawRoundRect(1, 1, w - 3, h - 3, 14, 14);
            Font font = RtlDialog.bold().deriveFont(Font.BOLD, 14f);
            g.setFont(font);
            String t = text();
            int tw = g.getFontMetrics().stringWidth(t), iw = 18, total = tw + iw + 8, x = (w - total) / 2;
            int cy = h / 2, cx = x + total - iw / 2;
            g.setColor(done ? Color.WHITE : green);
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawPolyline(new int[] { cx - 7, cx - 2, cx + 7 }, new int[] { cy, cy + 5, cy - 5 }, 3);   // ✓ מימין לטקסט
            g.drawString(t, x, cy + g.getFontMetrics().getAscent() / 2 - 2);
            g.dispose();
        }
    }
}

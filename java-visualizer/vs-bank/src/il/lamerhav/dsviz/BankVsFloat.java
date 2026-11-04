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
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

/**
 * החלון הצף "השאלה" ליד Visual Studio: הנוסח של השאלה, הציורים, "לפעולה של סעיף א" (פותח את הקובץ ב-Visual Studio, בפעולה),
 * "הצעה לפתרון" לכל סעיף, הפלט הרצוי, ובסוף "סיימתי את התרגיל" — סימון ירוק, והפרויקט נמחק (BankVs.complete).
 * סוגרים ופותחים מתי שרוצים (הקיצור "השאלה — מאגר תרגילים C#" פותח את השאלה האחרונה); תמיד מעל התוכנה (קבוע; אפשר למזער את החלון).
 */
final class BankVsFloat {
    private BankVsFloat() {}

    static final int WIDTH = 470;
    static JFrame current;
    static BankStore.Question question;
    /** "סיימתי את התרגיל" נלחץ: הנוסח של האישור (מוצג בחלון במקום השאלה), או null */
    static String confirm;
    private static Rectangle lastBounds;
    /** לבדיקות: מה מוצג */
    static final List<String> shown = new ArrayList<String>();

    static File lastFile() {
        return Paths.get(System.getProperty("user.home"), ".dsviz", BankVs.ij ? "bank-ij-last.txt" : "bank-cs-last.txt").toFile();
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
                    "פותחים את \"מאגר תרגילים\", בוחרים שאלה ויוצרים ממנה תרגיל — והשאלה תופיע כאן, ליד " + BankVs.ide() + ".");
            BankVsWindow.open(null, null);
            return;
        }
        show(q);
    }

    static void show(BankStore.Question q) {
        question = q;
        confirm = null;
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
        f.setAlwaysOnTop(true);   // תמיד מעל התוכנה (הגדרה קבועה); מי שרוצה — ממזער את החלון
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
        if (lastBounds == null) lastBounds = savedBounds();
        if (lastBounds != null) f.setBounds(lastBounds);
        else {
            // ברירת מחדל: מימין, אבל לא על Solution Explorer של Visual Studio (העמודה הימנית) — משמאל לה
            Rectangle s = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            int x = s.width >= 1600 && !BankVs.ij ? s.x + s.width - WIDTH - 380 : s.x + s.width - WIDTH - 12;   // IntelliJ: בקצה הימני (כלי הצד שם צרים)
            f.setBounds(x, s.y + 60, WIDTH, Math.min(s.height - 100, 880));
        }
        f.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) { saveBounds(f.getBounds()); }

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) { saveBounds(f.getBounds()); }
        });
        f.setVisible(true);
        if (BankVs.ij) yieldToIdeDialogs(f);
    }

    /**
     * IntelliJ: כשהוא מציג חלון דו-שיח (למשל "יש שגיאות" בהרצה ויזואלית), החלון הצף — שתמיד מעל — היה מסתיר אותו. אז בזמן שהחלון
     * הזה פתוח, החלון הצף יורד אחורה; כשהוא נסגר — חוזר להיות מעל.
     */
    static void yieldToIdeDialogs(final JFrame f) {
        Thread t = new Thread(() -> {
            boolean down = false;
            while (f.isDisplayable()) {
                final boolean d = BankVs.ideDialog();
                if (d != down) {
                    down = d;
                    SwingUtilities.invokeLater(() -> {
                        if (!f.isDisplayable()) return;
                        f.setAlwaysOnTop(!d);
                        if (d) f.toBack();   // וכשנסגר — שוב מעל (בלי לקחת את המקלדת מהעורך)
                        shown.add(d ? "YIELD" : "ON-TOP");
                    });
                }
                try {
                    Thread.sleep(700);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "dsviz-float-yield");
        t.setDaemon(true);
        t.start();
    }

    /** המקום והגודל שהתלמיד בחר לחלון — נשמרים, כדי שבפעם הבאה ייפתח באותו מקום */
    static File boundsFile() {
        return Paths.get(System.getProperty("user.home"), ".dsviz", BankVs.ij ? "bank-ij-float.txt" : "bank-cs-float.txt").toFile();
    }

    static Rectangle savedBounds() {
        try {
            String[] p = new String(Files.readAllBytes(boundsFile().toPath()), StandardCharsets.UTF_8).trim().split(",");
            Rectangle r = new Rectangle(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
            Rectangle s = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            return s.intersects(r) && r.width >= 300 && r.height >= 300 ? r : null;   // מסך אחר / קטן — ברירת המחדל
        } catch (Exception e) {
            return null;
        }
    }

    static void saveBounds(Rectangle r) {
        try {
            boundsFile().getParentFile().mkdirs();
            Files.write(boundsFile().toPath(), (r.x + "," + r.y + "," + r.width + "," + r.height).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // לא נשמר
        }
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
        // איפה התרגיל, ואיך מריצים
        List<File> projects = BankVs.projects(q);
        if (!projects.isEmpty()) {
            top.add(RtlDialog.gap(8));
            String name = projects.get(projects.size() - 1).getName();
            JPanel how = BankVsPanel.column();
            how.add(RtlDialog.label("שם התרגיל: " + name, RtlDialog.bold(), RtlDialog.INK));
            how.add(RtlDialog.gap(4));
            // מריצים מהתוכנה עצמה, איפה שכותבים את הקוד: הכפתור "הרצה ויזואלית" של DSViz בסרגל העליון
            how.add(RtlDialog.label("כותבים את התשובה בכל סעיף, ומריצים בכפתור \"הרצה ויזואלית\" שבסרגל העליון.", RtlDialog.small(), RtlDialog.INK_2));
            RtlDialog.Panel box = new RtlDialog.Panel(new BorderLayout(), new Color(0xEEF4FF), new Color(0xC9D8F5));
            box.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            box.add(how, BorderLayout.CENTER);
            box.setAlignmentX(Component.RIGHT_ALIGNMENT);
            top.add(box);
            shown.add("PROJECT " + name);
        }
        page.add(top, BorderLayout.NORTH);

        if (confirm != null) {
            // "סיימתי את התרגיל": האישור בתוך החלון עצמו (במקום השאלה) — בלי חלון נוסף שמכסה אותו
            page.add(confirmPage(f, q, confirm), BorderLayout.CENTER);
            JPanel foot = RtlDialog.footer();
            javax.swing.JButton yes = RtlDialog.primary("סיום ומחיקה"), no = RtlDialog.secondary("ביטול");
            yes.addActionListener(e -> {
                confirm = null;
                if (BankVs.complete(f, q, false)) f.dispose();
                else render();
            });
            no.addActionListener(e -> {
                confirm = null;
                render();
            });
            foot.add(yes);
            foot.add(no);
            page.add(foot, BorderLayout.SOUTH);
            shown.add("DONE-CONFIRM");
        } else {
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
                else {
                    String text = BankVs.doneText(q);
                    if (text == null) {   // אין קבצים למחוק — רק הסימון
                        if (BankVs.complete(f, q, false)) f.dispose();
                    } else {
                        confirm = text;
                        render();
                    }
                }
            });
            foot.add(db, BorderLayout.CENTER);
            page.add(foot, BorderLayout.SOUTH);
            shown.add("DONE-BUTTON " + (done ? "done" : "todo"));
            for (BankStore.Section s : q.sections) shown.add("SECTION " + s.label);
        }

        f.setContentPane(page);
        RtlDialog.rtl(page);
        // אחרי החלפת התוכן בחלון שכבר מוצג (למשל אחרי שינוי בסימון) — פריסה מחדש של כל החלון; בלי זה החלון נשאר ריק (לבן)
        f.getRootPane().revalidate();
        f.validate();
        f.repaint();
    }

    /** דף האישור של "סיימתי את התרגיל": כותרת, מה יקרה ומה יימחק */
    static JComponent confirmPage(JFrame f, BankStore.Question q, String text) {
        JPanel card = BankVsPanel.column();
        card.add(RtlDialog.heading(RtlDialog.Kind.OK, "סיימתי את התרגיל"));
        card.add(RtlDialog.gap(10));
        card.add(RtlDialog.rich(text, WIDTH - 90, RtlDialog.INK_2, true));
        RtlDialog.Panel box = new RtlDialog.Panel(new BorderLayout(), Color.WHITE, RtlDialog.LINE);
        box.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        box.add(card, BorderLayout.CENTER);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(RtlDialog.PAGE);
        holder.setBorder(BorderFactory.createEmptyBorder(10, 14, 14, 14));
        holder.add(box, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(holder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, RtlDialog.LINE));
        sp.getViewport().setBackground(RtlDialog.PAGE);
        return sp;
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
        File file = new File(dir, t.className + BankVs.ext());
        if (!file.isFile()) {
            File[] cs = dir.listFiles((d, n) -> n.endsWith(BankVs.ext()));
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
        body.add(RtlDialog.codeBlock(t.solution.replaceAll("[\\u202A-\\u202E\\u2066-\\u2069]", ""), 520));   // בלי סימוני כיוון (Visual Studio מציג אותם)
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
            setToolTipText(RtlDialog.rtl(done ? "לחיצה מבטלת את הסימון" : "סימון ירוק — וקבצי התרגיל נמחקים, כדי שלא יצטברו תרגילים ישנים"));
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

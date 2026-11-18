package il.lamerhav.dsviz;

import unit4.collectionsLib.Theme;
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
import java.util.Map;

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
 * סוגרים ופותחים מתי שרוצים (הכפתור "השאלה" בסרגל של Visual Studio פותח את השאלה האחרונה); תמיד מעל התוכנה (קבוע; אפשר למזער את החלון).
 */
final class BankVsFloat {
    private BankVsFloat() {}

    static final int WIDTH = 470;
    static JFrame current;
    static BankStore.Question question;
    /** "סיימתי את התרגיל" נלחץ: הנוסח של האישור (מוצג בחלון במקום השאלה), או null */
    static String confirm;
    /** בדף האישור: התלמיד בחר לשמור את קבצי התרגיל (ברירת המחדל — מחיקה) */
    static boolean keepFiles;
    /** "הגשה למורה": הדף מוצג במקום השאלה (null — לא מגישים עכשיו). אחרי כל שינוי בו — render() */
    static SubmitState submit;
    /** אחרי הגשה: "הוגש למורה ב-10:42" — מוצג מעל "סיימתי את התרגיל" */
    static String submitNote;
    /** לבדיקות: איך הסתיימה ההגשה האחרונה (ok / queued / קוד השגיאה); null — עוד לא הייתה מאז שדף ההגשה נפתח */
    static String submitResult;

    /** מצב הדף "הגשה למורה" */
    static final class SubmitState {
        /** קובצי התרגיל (שם → תוכן), כמו שהיו כשהדף נפתח */
        Map<String, String> files = new java.util.LinkedHashMap<String, String>();
        String mainClass = "";
        /** תוצאת בדיקת ההרצה; null — הבדיקה עוד רצה */
        RunCheck.Result check;
        /** "שם המגיש" (בעמדת מחשב) — ריק בכל הגשה חדשה, כדי שלא יוגש בטעות בשם של מי שישב קודם */
        String who = "";
        /** הודעה מהשרת כשההגשה לא עברה */
        String error;
        boolean busy;
        /** "סיימתי את התרגיל": אחרי ההגשה מסמנים ומוחקים (keep — שומרים את הקבצים) */
        boolean thenComplete, keep;
    }
    private static Rectangle lastBounds;
    /** לבדיקות: מה מוצג */
    static final List<String> shown = new ArrayList<String>();

    static File lastFile() {
        return Paths.get(System.getProperty("user.home"), ".dsviz", "bank-" + BankVs.key() + "-last.txt").toFile();
    }

    /**
     * "סיימתי": השאלה הזאת כבר לא "השאלה האחרונה" — אחרת הכפתור "השאלה" בסרגל ממשיך לפתוח אותה שוב ושוב.
     * אחרי זה הכפתור אומר "עוד לא נבחרה שאלה" ופותח את המאגר. שאלה אחרת שנפתחה בינתיים לא נמחקת.
     */
    static void forgetLast(String id) {
        try {
            File f = lastFile();
            if (f.isFile() && new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim().equals(id)) Files.delete(f.toPath());
        } catch (Exception ignored) {
            // לא נמחק — לא נורא
        }
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
        if (question == null || !question.id.equals(q.id)) submitNote = null;
        question = q;
        confirm = null;
        submit = null;
        try {
            File f = lastFile();
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), q.id.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // לא נשמר — לא נורא
        }
        if (current != null) {
            if (usable(current)) lastBounds = current.getBounds();   // חלון ממוזער מדווח על מקום מחוץ למסך — לא זוכרים אותו
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
        BankZoom.install(f);
        render();
        final Runnable onChange = () -> SwingUtilities.invokeLater(() -> {
            if (current == f && f.isDisplayable()) render();
        });
        BankProgress.get().listen(onChange);
        f.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                BankProgress.get().unlisten(onChange);
                if (usable(f)) lastBounds = f.getBounds();
                if (current == f) current = null;
            }
        });
        if (!usableBounds(lastBounds)) lastBounds = savedBounds();
        if (lastBounds != null) f.setBounds(lastBounds);
        else {
            // ברירת מחדל: מימין, אבל לא על Solution Explorer של Visual Studio (העמודה הימנית) — משמאל לה
            Rectangle s = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            int x = s.width >= 1600 && !BankVs.bridged() ? s.x + s.width - WIDTH - 380 : s.x + s.width - WIDTH - 12;   // IntelliJ: בקצה הימני (כלי הצד שם צרים)
            f.setBounds(x, s.y + 60, WIDTH, Math.min(s.height - 100, 880));
        }
        f.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentMoved(java.awt.event.ComponentEvent e) { if (usable(f)) saveBounds(f.getBounds()); }

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) { if (usable(f)) saveBounds(f.getBounds()); }
        });
        f.setExtendedState(java.awt.Frame.NORMAL);   // תמיד פתוח (לא ממוזער) בצד ימין
        f.setVisible(true);
        f.toFront();
        BankVs.watchVsDialogs();
        yieldToIdeDialogs(f);
    }

    /**
     * IntelliJ / Visual Studio: כשהם מציגים חלון דו-שיח (למשל "יש שגיאות" בהרצה ויזואלית), החלון הצף — שתמיד מעל — היה מסתיר אותו. אז בזמן שהחלון
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
        return Paths.get(System.getProperty("user.home"), ".dsviz", "bank-" + BankVs.key() + "-float.txt").toFile();
    }

    /** החלון פתוח (לא ממוזער) ובמקום סביר */
    private static boolean usable(JFrame f) {
        return (f.getExtendedState() & java.awt.Frame.ICONIFIED) == 0 && usableBounds(f.getBounds());
    }

    /** מקום סביר: גודל מינימלי, ולפחות חלק ממנו על מסך */
    private static boolean usableBounds(Rectangle r) {
        if (r == null || r.width < 300 || r.height < 300) return false;
        for (java.awt.GraphicsDevice d : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle i = d.getDefaultConfiguration().getBounds().intersection(r);
            if (i.width >= 150 && i.height >= 100) return true;
        }
        return false;
    }

    static Rectangle savedBounds() {
        try {
            String[] p = new String(Files.readAllBytes(boundsFile().toPath()), StandardCharsets.UTF_8).trim().split(",");
            Rectangle r = new Rectangle(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
            return usableBounds(r) ? r : null;   // מסך אחר / קטן — ברירת המחדל
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
        JPanel zoomRow = new JPanel(new BorderLayout());
        zoomRow.setOpaque(false);
        zoomRow.setAlignmentX(Component.RIGHT_ALIGNMENT);
        zoomRow.add(BankZoom.controls(), BorderLayout.LINE_END);
        top.add(zoomRow);
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

        JScrollPane question = null;
        if (submit != null) {
            page.add(submitPage(f, q, submit), BorderLayout.CENTER);
            page.add(submitFooter(f, q, submit), BorderLayout.SOUTH);
            shown.add("SUBMIT-PAGE " + (submit.check == null ? "checking" : submit.check.tone()) + (submit.thenComplete ? " then-complete" : ""));
        } else if (confirm != null) {
            // "סיימתי את התרגיל": האישור בתוך החלון עצמו (במקום השאלה) — בלי חלון נוסף שמכסה אותו
            page.add(confirmPage(f, q, confirm), BorderLayout.CENTER);
            JPanel foot = RtlDialog.footer();
            javax.swing.JButton yes = RtlDialog.primary(keepFiles ? "סיום ושמירת הקבצים" : "סיום ומחיקה"), no = RtlDialog.secondary("ביטול");
            yes.putClientProperty("dsviz.id", "done-yes");
            yes.addActionListener(e -> {
                boolean k = keepFiles;
                confirm = null;
                keepFiles = false;
                if (canSubmit(q)) openSubmit(f, q, true, k);   // "סיימתי" מגיש למורה לפני המחיקה
                else if (BankVs.complete(f, q, false, k)) f.dispose();
                else render();
            });
            no.addActionListener(e -> {
                confirm = null;
                keepFiles = false;
                render();
            });
            foot.add(yes);
            foot.add(no);
            page.add(foot, BorderLayout.SOUTH);
            shown.add("DONE-CONFIRM");
        } else {
            JPanel body = BankVsPanel.column();
            body.setBorder(BorderFactory.createEmptyBorder(4, 14, 14, 14));
            final int ready = BankVs.prepared(q);   // כמה סעיפים כבר בקוד
            shown.add("PREPARED " + ready);
            body.add(BankVsPanel.build(q, WIDTH - 60, new BankVsPanel.Actions() {
                public void jump(AiScaffold.Task t) {
                    BankVsFloat.jump(f, q, t);
                }

                public void solution(AiScaffold.Task t) {
                    BankVsFloat.solution(f, t);
                }

                public int prepared() {
                    return ready;
                }

                public void prepare(AiScaffold.Task t) {
                    BankVsFloat.prepare(f, q, t);
                }
            }));
            JPanel holder = new JPanel(new BorderLayout());
            holder.setBackground(RtlDialog.PAGE);
            holder.add(body, BorderLayout.NORTH);
            JScrollPane sp = new JScrollPane(holder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            sp.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, RtlDialog.LINE));
            sp.getVerticalScrollBar().setUnitIncrement(18);
            sp.getViewport().setBackground(RtlDialog.PAGE);
            page.add(sp, BorderLayout.CENTER);
            question = sp;

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
                        keepFiles = false;
                        render();
                    }
                }
            });
            foot.add(db, BorderLayout.CENTER);
            // "הגשה למורה" — רק למי שיש לו למי להגיש (תלמיד שמשויך למורה, או עמדת מחשב של הכיתה), וכשיש תרגיל
            if (canSubmit(q)) {
                JPanel up = BankVsPanel.column();
                javax.swing.JButton send = RtlDialog.secondary(Submit.label());
                send.putClientProperty("dsviz.id", "submit-open");
                send.setAlignmentX(Component.RIGHT_ALIGNMENT);
                send.addActionListener(e -> openSubmit(f, q, false, false));
                JPanel row = new JPanel(new BorderLayout());
                row.setOpaque(false);
                row.setAlignmentX(Component.RIGHT_ALIGNMENT);
                row.add(send, BorderLayout.CENTER);
                up.add(row);
                if (submitNote != null) {
                    up.add(RtlDialog.gap(4));
                    up.add(RtlDialog.label(submitNote, RtlDialog.small(), RtlDialog.GREEN));
                    shown.add("SUBMIT-NOTE " + submitNote);
                }
                up.add(RtlDialog.gap(8));
                foot.add(up, BorderLayout.NORTH);
                shown.add("SUBMIT-BUTTON " + Submit.label());
            }
            page.add(foot, BorderLayout.SOUTH);
            shown.add("DONE-BUTTON " + (done ? "done" : "todo"));
            for (BankStore.Section s : q.sections) shown.add("SECTION " + s.label);
        }

        if (submit == null) whoField = null;
        if (submitResult != null) shown.add("SUBMIT-RESULT " + submitResult);
        f.setContentPane(page);
        RtlDialog.rtl(page);
        final javax.swing.JTextField wf = whoField;
        if (wf != null) SwingUtilities.invokeLater(() -> {
            if (wf.isShowing() && wf.isEnabled()) {
                wf.requestFocusInWindow();
                wf.setCaretPosition(wf.getText().length());
            }
        });
        // אחרי החלפת התוכן בחלון שכבר מוצג (למשל אחרי שינוי בסימון) — פריסה מחדש של כל החלון; בלי זה החלון נשאר ריק (לבן)
        f.getRootPane().revalidate();
        f.validate();
        f.repaint();
        // הנוסח מתחיל מלמעלה ומימין (תחילת השורות בעברית) — גם אם החלון צר מהתוכן; אחרי שהחלון קיבל את הגודל שלו (show)
        final JScrollPane sp = question;
        if (sp != null) SwingUtilities.invokeLater(() -> {
            if (current == f && f.isDisplayable()) BankVsPanel.scrollToStart(sp);
        });
    }

    // ------------------------------------------------------------------ "הגשה למורה"

    /** יש למי להגיש (תלמיד שמשויך למורה / עמדת מחשב), ויש תרגיל מהשאלה */
    static boolean canSubmit(BankStore.Question q) {
        return Submit.available() && !BankVs.projects(q).isEmpty();
    }

    /** פותח את דף ההגשה, ובודק ברקע שהקוד נבנה ורץ (RunCheck). thenComplete — "סיימתי": אחרי ההגשה מסמנים ומוחקים */
    static void openSubmit(final JFrame f, final BankStore.Question q, boolean thenComplete, boolean keep) {
        final SubmitState st = new SubmitState();
        st.thenComplete = thenComplete;
        st.keep = keep;
        List<File> projects = BankVs.projects(q);
        final File dir = projects.isEmpty() ? null : projects.get(projects.size() - 1);
        st.mainClass = AiScaffold.read(Json.obj(Json.parse(Json.write(q.exercise)))).mainClass;
        st.files = BankSubmit.files(dir, BankVs.ext(), st.mainClass);
        submit = st;
        submitNote = null;
        submitResult = null;
        render();
        new javax.swing.SwingWorker<RunCheck.Result, Void>() {
            @Override
            protected RunCheck.Result doInBackground() {
                return BankSubmit.check(q, st.files, st.mainClass, !BankVs.ij, BankVs.lib, BankVs.javaExe, VsSubmit.dsvizCs());
            }

            @Override
            protected void done() {
                try {
                    st.check = get();
                } catch (Exception e) {
                    st.check = new RunCheck.Result();
                }
                if (submit == st && f.isDisplayable()) render();
            }
        }.execute();
    }

    /** דף "הגשה למורה": מה נשלח, תוצאת בדיקת ההרצה, ובעמדת מחשב — "שם המגיש" */
    static JComponent submitPage(final JFrame f, final BankStore.Question q, final SubmitState st) {
        JPanel card = BankVsPanel.column();
        card.add(RtlDialog.heading(RtlDialog.Kind.INFO, Submit.label()));
        card.add(RtlDialog.gap(10));
        card.add(RtlDialog.rich("התרגיל: " + q.title, WIDTH - 90, RtlDialog.INK, true));
        card.add(RtlDialog.gap(4));
        card.add(RtlDialog.rich(BankSubmit.filesLine(st.files), WIDTH - 90, RtlDialog.INK_2, true));
        card.add(RtlDialog.gap(12));
        // בדיקת ההרצה
        if (st.check == null) card.add(RtlDialog.label("בודק שהקוד נבנה ורץ…", RtlDialog.ui(), RtlDialog.MUTED));
        else {
            String tone = st.check.tone();
            shown.add("SUBMIT-CHECK " + tone + " " + st.check.summary());
            Color c = "good".equals(tone) ? RtlDialog.GREEN : "bad".equals(tone) ? RtlDialog.RED : "warn".equals(tone) ? new Color(0xB45309) : RtlDialog.MUTED;
            card.add(RtlDialog.rich(("good".equals(tone) ? "✓ " : "") + st.check.summary(), WIDTH - 90, c, true));
            if (!st.check.errors.isEmpty()) {   // שגיאות קומפילציה: הקובץ והשורה, ההסבר בעברית, וההודעה המקורית
                for (String e : st.check.errors.subList(0, Math.min(3, st.check.errors.size()))) {
                    card.add(RtlDialog.gap(6));
                    card.add(RtlDialog.errorCard(e, WIDTH - 90));
                }
            } else if (!st.check.error.isEmpty() && !st.check.timedOut) {
                card.add(RtlDialog.gap(6));
                card.add(RtlDialog.codeBlock(st.check.error, WIDTH - 110));
            }
            if (!"good".equals(tone) && !"none".equals(tone)) {
                card.add(RtlDialog.gap(6));
                card.add(RtlDialog.rich("אפשר לבטל, לתקן ולהגיש שוב — או להגיש כך: המורה יראה את הקוד ואת מה שקרה בהרצה.", WIDTH - 90, RtlDialog.INK_2, true));
            }
        }
        // בעמדת מחשב: שם המגיש (חובה)
        if ("station".equals(Submit.mode())) {
            card.add(RtlDialog.gap(14));
            card.add(RtlDialog.label("שם המגיש", RtlDialog.bold(), RtlDialog.INK));
            card.add(RtlDialog.gap(4));
            final javax.swing.JTextField who = new javax.swing.JTextField(st.who, 18);
            who.putClientProperty("dsviz.id", "submit-who");
            who.setFont(RtlDialog.ui());
            who.setComponentOrientation(java.awt.ComponentOrientation.RIGHT_TO_LEFT);
            who.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
            who.setAlignmentX(Component.RIGHT_ALIGNMENT);
            who.setMaximumSize(new java.awt.Dimension(260, who.getPreferredSize().height + 6));
            who.setEnabled(!st.busy);
            who.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                void changed() {
                    st.who = who.getText();
                    javax.swing.JButton yes = whoButton;
                    if (yes != null) yes.setEnabled(ready(st));
                }

                @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { changed(); }
                @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { changed(); }
                @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { changed(); }
            });
            card.add(who);
            card.add(RtlDialog.gap(4));
            card.add(RtlDialog.label("כותבים את השם שלכם: המחשב הזה משמש כמה תלמידים.", RtlDialog.small(), RtlDialog.MUTED));
            shown.add("SUBMIT-WHO");
            whoField = who;
        } else whoField = null;
        if (st.error != null) {
            card.add(RtlDialog.gap(12));
            card.add(RtlDialog.rich(st.error, WIDTH - 90, RtlDialog.RED, true));
            shown.add("SUBMIT-ERROR " + st.error);
        }
        RtlDialog.Panel box = new RtlDialog.Panel(new BorderLayout(), Color.WHITE, RtlDialog.LINE);
        box.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        box.add(card, BorderLayout.CENTER);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(RtlDialog.PAGE);
        holder.setBorder(BorderFactory.createEmptyBorder(10, 14, 14, 14));
        holder.add(box, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(holder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sp.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, RtlDialog.LINE));
        sp.getViewport().setBackground(RtlDialog.PAGE);
        return sp;
    }

    /** הכפתור "הגשה" של הדף שמוצג עכשיו — "שם המגיש" מפעיל אותו בלי לצייר את הדף מחדש (כדי שההקלדה לא תיקטע) */
    private static javax.swing.JButton whoButton;
    /** השדה "שם המגיש" של הדף שמוצג עכשיו (או null) — אחרי שהדף מצויר מחדש (הבדיקה הסתיימה) הסמן חוזר אליו, בסוף מה שנכתב */
    private static javax.swing.JTextField whoField;

    /** אפשר ללחוץ "הגשה": הבדיקה הסתיימה, יש קבצים, לא שולחים עכשיו, ובעמדה — נכתב שם */
    static boolean ready(SubmitState st) {
        return st.check != null && !st.busy && !st.files.isEmpty() && (!"station".equals(Submit.mode()) || Submit.whoOk(st.who));
    }

    static JPanel submitFooter(final JFrame f, final BankStore.Question q, final SubmitState st) {
        JPanel foot = RtlDialog.footer();
        final javax.swing.JButton yes = RtlDialog.primary(st.busy ? "שולח…" : st.thenComplete ? "הגשה וסיום" : "הגשה"), no = RtlDialog.secondary("ביטול");
        yes.putClientProperty("dsviz.id", "submit-yes");
        yes.setEnabled(ready(st));
        whoButton = yes;
        yes.addActionListener(e -> send(f, q, st));
        no.setEnabled(!st.busy);
        no.addActionListener(e -> {
            submit = null;
            render();
        });
        foot.add(yes);
        foot.add(no);
        if (st.thenComplete && st.error != null && !st.busy) {   // ההגשה לא עוברת — לא נועלים את "סיימתי"
            javax.swing.JButton skip = RtlDialog.link("לסיים בלי להגיש");
            skip.putClientProperty("dsviz.id", "submit-skip");
            skip.addActionListener(e -> {
                submit = null;
                if (BankVs.complete(f, q, false, st.keep)) f.dispose();
                else render();
            });
            foot.add(skip);
        }
        return foot;
    }

    /** שולח (ברקע). הצליח / נשמר לשליחה מאוחרת — חוזרים לשאלה עם "הוגש למורה ב-…" (או מסיימים את התרגיל); אחרת — ההודעה בדף */
    static void send(final JFrame f, final BankStore.Question q, final SubmitState st) {
        if (!ready(st)) return;
        st.busy = true;
        st.error = null;
        render();
        final Submit.Work w = BankSubmit.work(q, BankVs.topicTitle(q.topic), st.files, st.mainClass, BankVs.ext(), BankVs.ide());
        w.who = st.who;
        w.check = st.check.ran || st.check.needsInput ? st.check.toMap() : null;
        new javax.swing.SwingWorker<Account.Result, Void>() {
            @Override
            protected Account.Result doInBackground() {
                return Submit.send(w);
            }

            @Override
            protected void done() {
                Account.Result r;
                try {
                    r = get();
                } catch (Exception e) {
                    r = new Account.Result("error", "ההגשה לא הצליחה. נסו שוב בעוד רגע.", null);
                }
                st.busy = false;
                submitResult = r.status;
                if (r.ok() || "queued".equals(r.status)) {
                    Submit.flushAsync();
                    if (submit == st) submit = null;
                    submitNote = Submit.doneText(r);
                    if (st.thenComplete) {
                        if (BankVs.complete(f, q, false, st.keep)) f.dispose();
                        else if (f.isDisplayable()) render();
                    } else if (f.isDisplayable()) render();
                    return;
                }
                st.error = r.message.isEmpty() ? "ההגשה לא הצליחה. נסו שוב בעוד רגע." : r.message;
                if (submit == st && f.isDisplayable()) render();
            }
        }.execute();
    }

    /** דף האישור של "סיימתי את התרגיל": כותרת, מה יקרה ומה יימחק */
    static JComponent confirmPage(JFrame f, BankStore.Question q, String text) {
        JPanel card = BankVsPanel.column();
        card.add(RtlDialog.heading(RtlDialog.Kind.OK, "סיימתי את התרגיל"));
        card.add(RtlDialog.gap(10));
        card.add(RtlDialog.rich("השאלה תסומן בסימן ירוק. מה לעשות עם קבצי התרגיל?", WIDTH - 90, RtlDialog.INK, true));
        if (canSubmit(q)) {   // "סיימתי" מגיש למורה לפני המחיקה
            card.add(RtlDialog.gap(6));
            card.add(RtlDialog.rich("לפני הסיום התרגיל יוגש " + (Submit.teacher().isEmpty() ? "למורה" : "למורה " + Submit.teacher()) + ".", WIDTH - 90, RtlDialog.ACCENT_2, true));
            shown.add("DONE-SUBMITS");
        }
        card.add(RtlDialog.gap(6));
        // למחוק (ברירת המחדל — כדי שלא יצטברו תרגילים ישנים) או לשמור ולהמשיך לעבוד עליהם
        javax.swing.ButtonGroup group = new javax.swing.ButtonGroup();
        javax.swing.JRadioButton delete = choice("למחוק את קבצי התרגיל (מומלץ)", !keepFiles, "done-delete"),
                keep = choice("לשמור את הקבצים", keepFiles, "done-keep");
        group.add(delete);
        group.add(keep);
        delete.addActionListener(e -> { if (keepFiles) { keepFiles = false; render(); } });
        keep.addActionListener(e -> { if (!keepFiles) { keepFiles = true; render(); } });
        card.add(delete);
        card.add(keep);
        card.add(RtlDialog.gap(8));
        card.add(RtlDialog.rich(keepFiles
                ? "הקבצים נשארים כמו שהם, ואפשר להמשיך לעבוד עליהם. מ\"מאגר תרגילים\" אפשר לפתוח אותם שוב (\"פתח את התרגיל\")."
                : text, WIDTH - 90, RtlDialog.INK_2, true));
        shown.add("DONE-CHOICE " + (keepFiles ? "keep" : "delete"));
        RtlDialog.Panel box = new RtlDialog.Panel(new BorderLayout(), Color.WHITE, RtlDialog.LINE);
        box.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        box.add(card, BorderLayout.CENTER);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setBackground(RtlDialog.PAGE);
        holder.setBorder(BorderFactory.createEmptyBorder(10, 14, 14, 14));
        holder.add(box, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(holder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sp.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, RtlDialog.LINE));
        sp.getViewport().setBackground(RtlDialog.PAGE);
        return sp;
    }

    /** בחירה בדף האישור של "סיימתי": מימין לשמאל, בגופן של החלון */
    static javax.swing.JRadioButton choice(String text, boolean selected, String id) {
        javax.swing.JRadioButton b = new javax.swing.JRadioButton(RtlDialog.rtl(text), selected);
        b.setFont(RtlDialog.bold().deriveFont(RtlDialog.ui().getSize2D()));
        b.setForeground(RtlDialog.INK);
        b.setOpaque(false);
        b.setFocusPainted(false);
        b.setComponentOrientation(java.awt.ComponentOrientation.RIGHT_TO_LEFT);
        b.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        b.setAlignmentX(Component.RIGHT_ALIGNMENT);
        b.putClientProperty("dsviz.id", id);
        return b;
    }

    /** "הכן סעיף ב": הפעולה של הסעיף והבדיקות שלו נכנסות לקוד (ברקע — כדי שהחלון לא ייתקע), ואז החלון מתעדכן */
    static void prepare(JFrame f, BankStore.Question q, AiScaffold.Task t) {
        String label = t.label.replaceAll("['׳]+$", "");
        shown.add("PREPARE-CLICK " + label);
        new javax.swing.SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return BankVs.prepare(q, t);
            }

            @Override
            protected void done() {
                String problem;
                try {
                    problem = get();
                } catch (Exception e) {
                    problem = String.valueOf(e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                }
                shown.add("PREPARED-SECTION " + label + (problem == null ? " ok" : " " + problem));
                if (f.isDisplayable()) render();
                if (problem != null)
                    RtlDialog.show(f, "השאלה", RtlDialog.Kind.WARN, "סעיף " + label + "' לא נוסף עד הסוף", problem);
            }
        }.execute();
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
            Font font = Theme.z(RtlDialog.bold().deriveFont(14f));
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

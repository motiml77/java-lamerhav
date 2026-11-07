package il.lamerhav.dsviz;

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
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/**
 * מסך הכניסה למאגר התרגילים (ולשליחת תרגיל): מורה / עמדת מחשב / תלמיד — אותו מסך ב-Eclipse, ב-IntelliJ וב-Visual Studio.
 * אחרי כניסה אחת המחשב זוכר (Account), והמאגר נפתח מיד — גם בלי אינטרנט.
 *
 *   AccessGate.require(parent, () -> פתיחת המאגר)
 *
 * כשהשרת עוד לא מפעיל את הכניסה (ACCESS כבוי) — המאגר נפתח כמו קודם, בלי מסך.
 */
public final class AccessGate {
    private AccessGate() {}

    static final Color PAGE = new Color(0xF4F6FB), CARD = Color.WHITE, INK = new Color(0x17213A), MUTED = new Color(0x5A6478),
            LINE = new Color(0xE1E6F0), FIELD = new Color(0xC9D2E3), BLUE = new Color(0x2F6FED), BLUE_SOFT = new Color(0xE8EFFD),
            GREEN = new Color(0x1F7A44), GREEN_SOFT = new Color(0xE3F4EA), WARN = new Color(0x9A3412), WARN_SOFT = new Color(0xFFF7ED);
    private static final String RLM = "‏";

    /** לבדיקות: בלי מסך (-Ddsviz.access=off) */
    static boolean disabled() {
        return "off".equalsIgnoreCase(System.getProperty("dsviz.access", "")) || "off".equalsIgnoreCase(System.getenv("DSVIZ_ACCESS"));
    }

    /** פותח את המאגר אם יש כניסה תקפה במחשב (או שהשרת לא דורש); אחרת — מסך הכניסה, ואחרי הכניסה פותח */
    public static void require(final Component parent, final Runnable open) {
        if (disabled() || Account.current() != null) {
            open.run();
            refreshLater();
            return;
        }
        // אין כניסה במחשב: שואלים את השרת אם הכניסה בכלל פעילה (בלי אינטרנט — מסך הכניסה, עם הסבר).
        // חוט רגיל (לא SwingWorker, שהחוטים שלו "daemon"): בכלי של Visual Studio עוד אין שום חלון פתוח,
        // ובלי חוט כזה Java נסגרת לפני שהתשובה מגיעה — ומסך הכניסה לא נפתח
        Thread t = new Thread(() -> {
            boolean need;
            try { need = serverRequiresLogin(); } catch (Throwable e) { need = true; }
            final boolean show = need;
            SwingUtilities.invokeLater(() -> {
                if (!show) open.run();
                else new AccessGate.Dialog(parent, open).setVisible(true);
            });
        }, "dsviz-access");
        t.setDaemon(false);
        t.start();
    }

    /** הכניסה פעילה בשרת? (בלי אינטרנט — true: אי אפשר לדעת, והמסך מסביר) */
    static Boolean serverRequiresLogin() {
        Account.Result r = getConfig();
        if ("offline".equals(r.status) || "error".equals(r.status)) return Boolean.TRUE;
        return Boolean.TRUE.equals(r.body.get("enabled"));
    }

    private static Account.Result getConfig() {
        String base = Account.serverUrl();
        if (base.isEmpty()) return new Account.Result("error", "", null);
        java.net.HttpURLConnection c = null;
        try {
            c = (java.net.HttpURLConnection) new java.net.URL(base + "/v1/access/config").openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(8000);
            int code = c.getResponseCode();
            java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while (in != null && (n = in.read(b)) > 0) o.write(b, 0, n);
            Map<String, Object> j = Json.obj(Json.parse(new String(o.toByteArray(), "UTF-8")));
            return new Account.Result(code < 400 ? "ok" : "error", "", j);
        } catch (Exception e) {
            return new Account.Result("offline", "", null);
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** כשיש אינטרנט: בדיקה ברקע (עמדה ששוחררה / גישה שבוטלה — נמחקת; אישור מתחדש) */
    static void refreshLater() {
        if (disabled()) return;
        Thread t = new Thread(Account::refresh, "dsviz-access-refresh");
        t.setDaemon(true);
        t.start();
    }

    /** שורה קצרה למאגר: מי מחובר (או ריק) */
    public static String who() {
        Account.Access a = disabled() ? null : Account.current();
        return a == null ? "" : a.label();
    }

    // ------------------------------------------------------------------ החלון

    static final class Dialog extends JDialog {
        private static final long serialVersionUID = 1L;
        private final Runnable open;
        private final CardLayout cards = new CardLayout();
        private final JPanel body = new JPanel(cards);
        private final JTextField code = field(""), name = field(Account.machineName());
        private final JLabel stationError = text("", 13, false, new Color(0xB42318));
        private final JPanel message = column();
        private Account.Cancel cancel;
        private Timer payPoll;

        Dialog(Component parent, Runnable open) {
            super(parent == null ? null : SwingUtilities.getWindowAncestor(parent) != null ? SwingUtilities.getWindowAncestor(parent)
                    : parent instanceof Window ? (Window) parent : null, "כניסה למאגר התרגילים", ModalityType.MODELESS);
            this.open = open;
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            JPanel root = new JPanel(new BorderLayout());
            root.setBackground(PAGE);
            root.add(header(), BorderLayout.NORTH);
            body.setOpaque(false);
            body.add(choose(), "choose");
            body.add(station(), "station");
            body.add(message, "message");
            root.add(body, BorderLayout.CENTER);
            root.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            code.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
            setContentPane(root);
            setSize(760, 450);
            setLocationRelativeTo(parent);
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosed(java.awt.event.WindowEvent e) {
                    if (cancel != null) cancel.cancel();
                    if (payPoll != null) payPoll.stop();
                }
            });
        }

        private JComponent header() {
            JPanel h = new JPanel(new BorderLayout());
            h.setBackground(CARD);
            h.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE), BorderFactory.createEmptyBorder(12, 22, 12, 22)));
            JLabel t = text("מאגר תרגילים", 17, true, INK);
            t.setIcon(new Logo());
            t.setIconTextGap(10);
            h.add(t, BorderLayout.LINE_START);
            return h;
        }

        private JComponent choose() {
            JPanel p = column();
            p.setBorder(BorderFactory.createEmptyBorder(26, 30, 24, 30));
            p.add(text("כניסה למאגר התרגילים", 24, true, INK));
            p.add(Box.createVerticalStrut(6));
            p.add(text("פעם אחת במחשב הזה — אחר כך המאגר נפתח לבד, גם בלי אינטרנט.", 15, false, MUTED));
            p.add(Box.createVerticalStrut(20));
            JPanel grid = new JPanel(new GridLayout(1, 3, 14, 0));
            grid.setOpaque(false);
            grid.setAlignmentX(Component.RIGHT_ALIGNMENT);
            grid.add(roleCard("מורה", "כניסה עם חשבון Google שההנהלה הגדירה. רואים את קוד המורה ואת העמדות.", "כניסה עם Google", () -> google("teacher")));
            grid.add(roleCard("עמדת מחשב בכיתה", "מקלידים את קוד המורה — והמחשב הזה פתוח לכל התלמידים שעובדים עליו.", "הקלדת קוד המורה", () -> cards.show(body, "station")));
            grid.add(roleCard("תלמיד", "כניסה עם חשבון Google. מי ששילם — המאגר נפתח בכל המחשבים שלו.", "כניסה עם Google", () -> google("student")));
            p.add(grid);
            return p;
        }

        private JComponent roleCard(String title, String desc, String action, final Runnable go) {
            JPanel c = new Rounded(CARD, LINE, 16);
            c.setLayout(new BorderLayout(0, 10));
            c.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
            JPanel top = column();
            top.add(text(title, 18, true, INK));
            top.add(Box.createVerticalStrut(8));
            top.add(wrap(desc, 14, MUTED, 130));
            c.add(top, BorderLayout.CENTER);
            JButton b = primary(action);
            b.addActionListener(e -> go.run());
            c.add(b, BorderLayout.SOUTH);
            return c;
        }

        private JComponent station() {
            JPanel p = column();
            p.setBorder(BorderFactory.createEmptyBorder(26, 30, 24, 30));
            p.add(text("חיבור עמדת מחשב", 24, true, INK));
            p.add(Box.createVerticalStrut(6));
            p.add(wrap("את הקוד מקבלים מהמורה (4 תווים). כל מחשב שמתחבר משתמש בהיתר אחד של המורה; אותו מחשב שמתחבר שוב — בלי היתר נוסף.", 15, MUTED, 440));
            p.add(Box.createVerticalStrut(18));
            p.add(text("קוד המורה", 14, true, INK));
            p.add(Box.createVerticalStrut(6));
            code.setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
            code.setHorizontalAlignment(SwingConstants.CENTER);
            p.add(code);
            p.add(Box.createVerticalStrut(14));
            p.add(text("שם למחשב (כדי שהמורה יזהה אותו ברשימה)", 14, true, INK));
            p.add(Box.createVerticalStrut(6));
            p.add(name);
            p.add(Box.createVerticalStrut(8));
            p.add(stationError);
            p.add(Box.createVerticalStrut(14));
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.RIGHT_ALIGNMENT);
            final JButton go = primary("חיבור המחשב");
            JButton back = secondary("חזרה");
            back.addActionListener(e -> cards.show(body, "choose"));
            go.addActionListener(e -> claim(go));
            getRootPane().setDefaultButton(go);
            row.add(go);
            row.add(back);
            p.add(row);
            return p;
        }

        private void claim(final JButton go) {
            final String c = code.getText().trim(), n = name.getText().trim();
            if (c.replaceAll("[\\s-]", "").length() != 4) { stationError.setText(RLM + "הקוד הוא 4 תווים, למשל K7QM."); return; }
            if (n.isEmpty()) { stationError.setText(RLM + "כתבו שם למחשב — למשל \"מעבדה 2 · עמדה 14\"."); return; }
            stationError.setText(" ");
            go.setEnabled(false);
            go.setText("מתחבר…");
            new SwingWorker<Account.Result, Void>() {
                @Override
                protected Account.Result doInBackground() { return Account.claimStation(c, n); }

                @Override
                protected void done() {
                    go.setEnabled(true);
                    go.setText("חיבור המחשב");
                    Account.Result r;
                    try { r = get(); } catch (Exception e) { r = new Account.Result("error", "החיבור לא הצליח.", null); }
                    if (r.ok()) {
                        success("המחשב מחובר", "העמדה של " + teacherName(r) + " — המאגר פתוח לכל מי שעובד על המחשב הזה, גם בלי אינטרנט.");
                    } else if ("bad-code".equals(r.status)) {
                        stationError.setText(RLM + "הקוד לא נמצא. בודקים מול המורה ומקלידים שוב.");
                    } else if ("full".equals(r.status)) {
                        show("אין עוד היתרים", (r.teacherInt("seats") == 1 ? "העמדה היחידה" : "כל " + r.teacherInt("seats") + " העמדות") + " של " + teacherName(r) + " כבר בשימוש. אפשר לקנות עוד עמדות בלוח המורה, או לבקש מההנהלה לשחרר מחשב שהוחלף.", WARN, WARN_SOFT, true);
                    } else if ("blocked".equals(r.status)) {
                        show("הגישה של המורה הושבתה", "פונים למורה או להנהלה.", WARN, WARN_SOFT, true);
                    } else {
                        stationError.setText(RLM + (r.message.isEmpty() ? "החיבור לא הצליח. נסו שוב." : r.message));
                    }
                }
            }.execute();
        }

        private static String teacherName(Account.Result r) {
            String n = Json.str(r.teacher().get("name"));
            return n.isEmpty() ? "המורה" : n;
        }

        private void google(final String role) {
            cancel = new Account.Cancel();
            final Account.Cancel mine = cancel;
            final String[] url = { "" };
            JPanel p = message;
            p.removeAll();
            p.setBorder(BorderFactory.createEmptyBorder(40, 40, 30, 40));
            p.add(text("ממשיכים בדפדפן", 24, true, INK));
            p.add(Box.createVerticalStrut(10));
            p.add(wrap("נפתח דפדפן עם הכניסה של Google. נכנסים שם — והחלון הזה ממשיך לבד.", 16, MUTED, 440));
            p.add(Box.createVerticalStrut(18));
            final JLabel copy = text("הדפדפן לא נפתח? לחיצה כאן מעתיקה את הכתובת", 14, true, BLUE);
            copy.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            copy.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (url[0].isEmpty()) return;
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(url[0]), null);
                    copy.setText("הכתובת הועתקה — מדביקים אותה בדפדפן");
                }
            });
            p.add(copy);
            p.add(Box.createVerticalStrut(22));
            JButton stop = secondary("ביטול");
            stop.addActionListener(e -> { mine.cancel(); cards.show(body, "choose"); });
            p.add(stop);
            cards.show(body, "message");
            p.revalidate();
            p.repaint();
            new SwingWorker<Account.Result, Void>() {
                @Override
                protected Account.Result doInBackground() {
                    return Account.signIn(role, u -> url[0] = u, mine, 10 * 60 * 1000L);
                }

                @Override
                protected void done() {
                    if (mine.cancelled || !isDisplayable()) return;
                    Account.Result r;
                    try { r = get(); } catch (Exception e) { r = new Account.Result("error", "הכניסה לא הצליחה.", null); }
                    afterSignIn(role, r);
                }
            }.execute();
        }

        private void afterSignIn(String role, Account.Result r) {
            switch (r.status) {
                case "ok":
                    success("נכנסת בהצלחה", "teacher".equals(role) ? "המאגר פתוח. את קוד המורה ואת העמדות רואים בדף \"החשבון שלי\" בדפדפן." : "המאגר פתוח — גם בפעם הבאה, גם בלי אינטרנט.");
                    break;
                case "pay":
                    waitForPayment();
                    break;
                case "not-teacher":
                    show("החשבון עוד לא מוגדר כמורה", "בדפדפן אפשר לשלוח בקשה להנהלה. אחרי האישור לוחצים שוב \"כניסה עם Google\".", WARN, WARN_SOFT, true);
                    break;
                case "blocked":
                    show("הגישה של החשבון הושבתה", "פונים להנהלה.", WARN, WARN_SOFT, true);
                    break;
                case "devices":
                    List<String> d = Account.devices(r);
                    show("החשבון כבר מחובר בכל המחשבים המותרים", "מחובר ב: " + String.join(", ", d) + ". בדפדפן אפשר לנתק מחשב שכבר לא בשימוש, ואז לנסות שוב.", WARN, WARN_SOFT, true);
                    break;
                case "cancelled":
                    if (r.message.isEmpty()) cards.show(body, "choose");
                    else show("לא התקבלה כניסה", r.message, WARN, WARN_SOFT, true);
                    break;
                default:
                    show("הכניסה לא הצליחה", r.message.isEmpty() ? "נסו שוב בעוד רגע." : r.message, WARN, WARN_SOFT, true);
            }
        }

        /** תלמיד שעוד לא שילם: דף התשלום פתוח בדפדפן; בודקים כל 5 שניות, וכשאושר — נפתח */
        private void waitForPayment() {
            show("מחכים לתשלום", "דף התשלום פתוח בדפדפן. אחרי שהתשלום עובר, החלון הזה מתעדכן לבד — לא צריך ללחוץ על כלום.", BLUE, BLUE_SOFT, true);
            payPoll = new Timer(5000, null);
            payPoll.addActionListener(e -> new SwingWorker<Account.Result, Void>() {
                @Override
                protected Account.Result doInBackground() { return Account.refresh(); }

                @Override
                protected void done() {
                    try {
                        if (get().ok() && Account.current() != null && payPoll != null) {
                            payPoll.stop();
                            success("התשלום התקבל", "המאגר פתוח עד סוף שנת הלימודים, בכל המחשבים שלך.");
                        }
                    } catch (Exception ignored) {
                        // ננסה שוב
                    }
                }
            }.execute());
            payPoll.start();
        }

        private void success(String title, String text) {
            show(title, text, GREEN, GREEN_SOFT, false);
            Timer t = new Timer(1400, e -> { dispose(); open.run(); });
            t.setRepeats(false);
            t.start();
        }

        private void show(String title, String msg, Color fg, Color bg, boolean back) {
            JPanel p = message;
            p.removeAll();
            p.setBorder(BorderFactory.createEmptyBorder(40, 40, 30, 40));
            JPanel box = new Rounded(bg, bg, 14);
            box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
            box.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
            box.setAlignmentX(Component.RIGHT_ALIGNMENT);
            box.add(text(title, 21, true, fg));
            box.add(Box.createVerticalStrut(8));
            box.add(wrap(msg, 15, INK, 420));
            p.add(box);
            if (back) {
                p.add(Box.createVerticalStrut(18));
                JButton b = secondary("חזרה");
                b.addActionListener(e -> { if (payPoll != null) payPoll.stop(); cards.show(body, "choose"); });
                p.add(b);
            }
            cards.show(body, "message");
            p.revalidate();
            p.repaint();
        }
    }

    // ------------------------------------------------------------------ עמדת ניהול של המורה

    /** האם המחשב מחובר כמורה (אז מציגים את הכפתור "עמדת ניהול") */
    public static boolean isTeacher() {
        Account.Access a = disabled() ? null : Account.current();
        return a != null && a.isTeacher();
    }

    /**
     * "עמדת ניהול" של המורה, בכל פעם שרוצים: הקוד של המורה (לחיבור עמדות מחשב) עם כפתור העתקה, וכפתור שפותח את
     * עמדת הניהול בדפדפן (עמדות, מקומות, תשלום). נפתח תמיד מקדימה (אחר כך חלון רגיל).
     */
    private static TeacherPanel teacherPanel;

    /**
     * מביא את החלון לקדמת המסך (גם כשהחלון הראשי של Eclipse / IntelliJ הוא זה שבפוקוס): "תמיד מעל" לרגע קצר מכריח את מערכת
     * ההפעלה להציג אותו מלפנים, ואז הוא חלון רגיל — לא מסתיר דברים אחרים אחרי שעוברים הלאה.
     */
    static void front(final java.awt.Window w) {
        try {
            if ((w instanceof java.awt.Frame) && (((java.awt.Frame) w).getExtendedState() & java.awt.Frame.ICONIFIED) != 0)
                ((java.awt.Frame) w).setExtendedState(java.awt.Frame.NORMAL);
            w.setAlwaysOnTop(true);
            w.toFront();
            w.requestFocus();
            javax.swing.Timer t = new javax.swing.Timer(400, e -> {
                if (w.isDisplayable()) w.setAlwaysOnTop(false);
            });
            t.setRepeats(false);
            t.start();
        } catch (RuntimeException ignored) {
            // לא מצליחים להקדים — החלון עדיין פתוח
        }
    }

    public static void showTeacherPanel(final Component parent) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (teacherPanel != null && teacherPanel.isDisplayable()) {   // כבר פתוח — רק מביאים לקדמה
                    front(teacherPanel);
                    return;
                }
                final TeacherPanel p = new TeacherPanel(parent);
                teacherPanel = p;
                p.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosed(java.awt.event.WindowEvent e) {
                        if (teacherPanel == p) teacherPanel = null;
                    }
                });
                p.setVisible(true);
                front(p);
            }
        });
    }

    static final class TeacherPanel extends JDialog {
        private static final long serialVersionUID = 1L;

        TeacherPanel(Component parent) {
            super(parent == null ? null : SwingUtilities.getWindowAncestor(parent) != null ? SwingUtilities.getWindowAncestor(parent)
                    : parent instanceof Window ? (Window) parent : null, "עמדת ניהול", ModalityType.MODELESS);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            Account.Access a = Account.current();
            final String codeText = Account.teacherCode();
            JPanel root = new JPanel(new BorderLayout());
            root.setBackground(PAGE);
            JPanel col = column();
            col.setBorder(BorderFactory.createEmptyBorder(22, 28, 22, 28));
            col.add(text("עמדת ניהול של המורה", 22, true, INK));
            col.add(Box.createVerticalStrut(4));
            col.add(text(a == null ? "" : a.label(), 14, false, MUTED));
            col.add(Box.createVerticalStrut(16));
            col.add(text("הקוד שלך לחיבור עמדות מחשב", 14, true, INK));
            col.add(Box.createVerticalStrut(6));
            JLabel big = new JLabel(codeText.isEmpty() ? "—" : codeText, SwingConstants.CENTER);
            big.setFont(new Font(Font.MONOSPACED, Font.BOLD, 40));
            big.setForeground(BLUE);
            big.setOpaque(true);
            big.setBackground(BLUE_SOFT);
            big.setBorder(BorderFactory.createEmptyBorder(10, 24, 10, 24));
            big.setAlignmentX(Component.RIGHT_ALIGNMENT);
            col.add(big);
            col.add(Box.createVerticalStrut(6));
            col.add(wrap(codeText.isEmpty() ? "הקוד עוד לא נטען. נכנסים לעמדת הניהול בדפדפן (הכפתור למטה) — שם הוא מוצג תמיד."
                    : "מקלידים אותו בכל מחשב במעבדה, במסך \"עמדת מחשב\". הקוד נשמר כאן — אפשר לחזור לכאן בכל פעם.", 13, MUTED, 400));
            col.add(Box.createVerticalStrut(16));
            JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.RIGHT_ALIGNMENT);
            JButton open = primary("פתח עמדת ניהול בדפדפן");
            open.addActionListener(e -> Account.openManagement());
            JButton copy = secondary("העתק קוד");
            copy.setEnabled(!codeText.isEmpty());
            copy.addActionListener(e -> {
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(codeText), null);
                copy.setText(RLM + "הועתק");
            });
            JButton close = secondary("סגירה");
            close.addActionListener(e -> dispose());
            row.add(open);
            row.add(copy);
            row.add(close);
            col.add(row);
            root.add(col, BorderLayout.CENTER);
            root.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            setContentPane(root);
            pack();
            setMinimumSize(new Dimension(480, 0));
            setLocationRelativeTo(parent);
        }
    }

    // ------------------------------------------------------------------ רכיבים קטנים

    static JPanel column() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setAlignmentX(Component.RIGHT_ALIGNMENT);
        return p;
    }

    static JLabel text(String s, int size, boolean bold, Color c) {
        JLabel l = new JLabel(RLM + s);
        l.setFont(new Font(Font.DIALOG, bold ? Font.BOLD : Font.PLAIN, size));
        l.setForeground(c);
        l.setAlignmentX(Component.RIGHT_ALIGNMENT);
        l.setHorizontalAlignment(SwingConstants.RIGHT);
        return l;
    }

    /** טקסט שיורד שורה (HTML מימין לשמאל) */
    static JLabel wrap(String s, int size, Color c, int width) {
        String esc = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        JLabel l = new JLabel("<html><div dir='rtl' style='width:" + width + "px'>" + esc + "</div></html>");
        l.setFont(new Font(Font.DIALOG, Font.PLAIN, size));
        l.setForeground(c);
        l.setAlignmentX(Component.RIGHT_ALIGNMENT);
        l.setHorizontalAlignment(SwingConstants.RIGHT);
        return l;
    }

    static JTextField field(String v) {
        JTextField f = new JTextField(v);
        f.setFont(new Font(Font.DIALOG, Font.PLAIN, 16));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        f.setPreferredSize(new Dimension(300, 44));
        f.setAlignmentX(Component.RIGHT_ALIGNMENT);
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(FIELD, 1, true), BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        return f;
    }

    static JButton primary(String s) {
        JButton b = new JButton(RLM + s);
        b.setFont(new Font(Font.DIALOG, Font.BOLD, 15));
        b.setForeground(Color.WHITE);
        b.setBackground(BLUE);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        b.putClientProperty("FlatLaf.style", "background: #2F6FED; foreground: #FFFFFF; hoverBackground: #1E4FB8; borderWidth: 0; arc: 12");
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    static JButton secondary(String s) {
        JButton b = new JButton(RLM + s);
        b.setFont(new Font(Font.DIALOG, Font.PLAIN, 15));
        b.setForeground(INK);
        b.setBackground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(FIELD, 1, true), BorderFactory.createEmptyBorder(9, 16, 9, 16)));
        b.putClientProperty("FlatLaf.style", "background: #FFFFFF; borderColor: #C9D2E3; arc: 12");
        b.setAlignmentX(Component.RIGHT_ALIGNMENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** כרטיס עם פינות מעוגלות */
    static class Rounded extends JPanel {
        private static final long serialVersionUID = 1L;
        private final Color fill, line;
        private final int arc;

        Rounded(Color fill, Color line, int arc) {
            this.fill = fill; this.line = line; this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g.setColor(line);
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            g.dispose();
        }
    }

    /** הסמל: ספר בריבוע כחול */
    static final class Logo implements javax.swing.Icon {
        public int getIconWidth() { return 30; }
        public int getIconHeight() { return 30; }

        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(BLUE);
            g.fillRoundRect(x, y, 30, 30, 9, 9);
            g.setColor(Color.WHITE);
            g.setStroke(new java.awt.BasicStroke(2f));
            g.drawRoundRect(x + 9, y + 6, 12, 18, 3, 3);
            g.drawLine(x + 12, y + 11, x + 18, y + 11);
            g.drawLine(x + 12, y + 15, x + 16, y + 15);
            g.dispose();
        }
    }
}

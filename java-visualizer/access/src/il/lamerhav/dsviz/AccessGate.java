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
        if (disabled()) {
            open.run();
            return;
        }
        final Account.Access saved = Account.current();
        if (saved != null) {
            checkThenOpen(parent, open, saved);
            return;
        }
        // אין כניסה במחשב: שואלים את השרת אם הכניסה בכלל פעילה (בלי אינטרנט — מסך הכניסה, עם הסבר).
        // לא מחכים לתשובה כדי להראות את המסך: אם היא לא הגיעה תוך רגע — המסך נפתח מיד (מקדימה), ואם בסוף מתברר שהשרת
        // לא דורש כניסה — הוא נסגר והמאגר נפתח.
        // חוט רגיל (לא SwingWorker, שהחוטים שלו "daemon"): בכלי של Visual Studio עוד אין שום חלון פתוח,
        // ובלי חוט כזה Java נסגרת לפני שהתשובה מגיעה — ומסך הכניסה לא נפתח
        final AccessGate.Dialog[] shown = { null };
        final boolean[] decided = { false };
        final Timer early = new Timer(SHOW_AFTER_MS, e -> {
            if (decided[0] || shown[0] != null) return;
            shown[0] = new AccessGate.Dialog(parent, open);
            showInFront(shown[0]);
        });
        early.setRepeats(false);
        SwingUtilities.invokeLater(early::start);
        Thread t = new Thread(() -> {
            boolean need;
            try { need = serverRequiresLogin(); } catch (Throwable e) { need = true; }
            try { Account.flushPendingLogouts(); } catch (Throwable ignored) { /* בפעם הבאה */ }
            // יש כניסה שמורה, רק שהאישור שבה פג / לא אומת (המחשב היה בלי אינטרנט זמן רב, שעון לא נכון): לא מבקשים קוד / כניסה
            // מחדש — בודקים מול השרת, שמחדש את האישור, ופותחים את המאגר
            String step = "";
            try {
                if (need && Account.hasSavedToken()) {
                    Account.refresh();
                    if (Account.current() != null) need = false;
                    else step = studentNeeds();   // תלמיד מחובר שנשאר לו להשלים הרשמה / לשלם — ישר למסך הזה, בלי כניסה מחדש
                }
            } catch (Throwable ignored) { /* מסך הכניסה */ }
            final boolean show = need;
            final String needs = step;
            SwingUtilities.invokeLater(() -> {
                decided[0] = true;
                early.stop();
                if (show) {
                    if (shown[0] == null) {
                        shown[0] = new AccessGate.Dialog(parent, open);
                        if (!needs.isEmpty()) shown[0].studentStep(needs, false);
                        showInFront(shown[0]);
                    } else if (!needs.isEmpty() && shown[0].isDisplayable() && shown[0].idle()) shown[0].studentStep(needs, false);
                } else {
                    if (shown[0] != null) shown[0].dispose();
                    open.run();
                }
            });
        }, "dsviz-access");
        t.setDaemon(false);
        t.start();
    }

    /** אחרי כמה זמן בלי תשובה מהשרת מראים את מסך הכניסה בכל זאת */
    static final int SHOW_AFTER_MS = 700;

    /** כמה מחכים לבדיקה מול השרת לפני שפותחים את המאגר עם הכניסה השמורה (בלי אינטרנט — לא מחכים יותר מזה) */
    static final int CHECK_WAIT_MS = 3500;

    /**
     * יש כניסה שמורה במחשב: כשיש אינטרנט — קודם בודקים מול השרת (עד CHECK_WAIT_MS). עמדה שההנהלה שחררה / גישה שבוטלה —
     * הכניסה נמחקת (Account.refresh) ובמקום המאגר נפתח מסך הכניסה, עם הסבר. תקפה, או שאין תשובה (בלי אינטרנט / שרת איטי) —
     * המאגר נפתח כרגיל, והבדיקה ממשיכה ברקע (אם בסוף התברר שבוטל — בפתיחה הבאה כבר מסך הכניסה).
     */
    static void checkThenOpen(final Component parent, final Runnable open, final Account.Access saved) {
        final boolean[] done = { false };
        final Runnable openBank = () -> {
            if (done[0]) return;
            done[0] = true;
            open.run();
        };
        final Timer wait = new Timer(CHECK_WAIT_MS, e -> openBank.run());
        wait.setRepeats(false);
        SwingUtilities.invokeLater(wait::start);
        // חוט רגיל (לא daemon): בכלי של Visual Studio עוד אין חלון פתוח, ו-Java לא תיסגר לפני התשובה
        Thread t = new Thread(() -> {
            Account.Result r;
            try { r = Account.refresh(); } catch (Throwable e) { r = new Account.Result("error", "", null); }
            final boolean gone = !"offline".equals(r.status) && !"error".equals(r.status) && Account.current() == null;
            final String needs = gone ? studentNeeds() : "";
            SwingUtilities.invokeLater(() -> {
                wait.stop();
                if (done[0]) return;          // כבר נפתח (התשובה איחרה) — הפעם הבאה תראה את מסך הכניסה
                if (!gone) {
                    openBank.run();
                    return;
                }
                done[0] = true;
                AccessGate.Dialog d = new AccessGate.Dialog(parent, open);
                if (!needs.isEmpty()) {   // תלמיד שעוד מחובר: נשאר להשלים הרשמה, או שהמאגר עבר לתשלום — לא "הכניסה לא בתוקף"
                    d.studentStep(needs, false);
                    showInFront(d);
                    return;
                }
                d.notice(saved.isStation()
                        ? "המחשב הזה כבר לא מחובר כעמדת מחשב — המורה או ההנהלה שחררו אותו. כדי להמשיך מתחברים שוב עם קוד המורה."
                        : "הכניסה שהייתה שמורה במחשב הזה כבר לא בתוקף. כדי להמשיך מתחברים שוב.");
                showInFront(d);
            });
        }, "dsviz-access-check");
        t.setDaemon(false);
        t.start();
    }

    /**
     * תלמיד שמחובר במחשב הזה (הכרטיס שלו שמור), והשרת ענה בבדיקה האחרונה שנשאר לו צעד לפני שהמאגר נפתח:
     * "register" — להשלים הרשמה (שם מלא וטלפון, בדפדפן); "pay" — המאגר בתשלום. אחרת ""
     */
    static String studentNeeds() {
        Map<String, Object> st = Account.studentInfo();
        String s = st == null ? "" : Json.str(st.get("status"));
        return "register".equals(s) || "pay".equals(s) ? s : "";
    }

    /**
     * מסך הכניסה נפתח מקדימה — גם כשהוא נפתח מתוך Eclipse / IntelliJ / Visual Studio שהחלון שלהם בפוקוס. "תמיד מעל" רק לרגע
     * (front), ושוב כשהחלון באמת מוצג על המסך; אחר כך זה חלון רגיל — אפשר לעבור לחלון אחר והוא יורד אחורה.
     */
    static void showInFront(final java.awt.Window w) {
        w.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) {
                front(w);
            }
        });
        w.setVisible(true);
        front(w);
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
            OsTrust.apply(c);
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
        /** הסבר למה מסך הכניסה נפתח (עמדה ששוחררה / כניסה שבוטלה) — מעל שלוש האפשרויות; מוסתר כשאין */
        private final JLabel notice = text("", 14, true, WARN);
        private final JPanel message = column();
        private Account.Cancel cancel;
        private Timer payPoll;
        /** המסך שמוצג עכשיו: "choose" (שלוש האפשרויות) / "station" / "message" */
        private String showing = "choose";

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

        /** שורת הסבר בראש המסך (למשל: "ההנהלה שחררה את העמדה") */
        void notice(String s) {
            boolean was = notice.isVisible();
            notice.setText(RLM + s);
            notice.setVisible(s != null && !s.isEmpty());
            if (notice.isVisible() && !was) setSize(getWidth(), getHeight() + 46);   // מקום לשורה — הכרטיסים לא נחתכים
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
            notice.setVisible(false);
            notice.setOpaque(true);
            notice.setBackground(WARN_SOFT);
            notice.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            p.add(Box.createVerticalStrut(8));
            p.add(notice);
            p.add(Box.createVerticalStrut(6));
            p.add(text("פעם אחת במחשב הזה — אחר כך המאגר נפתח לבד, גם בלי אינטרנט.", 15, false, MUTED));
            p.add(Box.createVerticalStrut(20));
            JPanel grid = new JPanel(new GridLayout(1, 3, 14, 0));
            grid.setOpaque(false);
            grid.setAlignmentX(Component.RIGHT_ALIGNMENT);
            grid.add(roleCard("מורה", "כניסה עם חשבון Google שההנהלה הגדירה. רואים את קוד המורה ואת העמדות.", "כניסה עם Google", () -> google("teacher")));
            grid.add(roleCard("עמדת מחשב בכיתה", "מקלידים את קוד המורה — והמחשב הזה פתוח לכל התלמידים שעובדים עליו.", "הקלדת קוד המורה", () -> showCard("station")));
            grid.add(roleCard("תלמיד", "כניסה עם חשבון Google. בפעם הראשונה: שם מלא וטלפון, ואפשר גם קוד מורה.", "כניסה עם Google", () -> google("student")));
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
            back.addActionListener(e -> showCard("choose"));
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
                        longError(r.message.isEmpty() ? "החיבור לא הצליח. נסו שוב." : r.message);
                    }
                }
            }.execute();
        }

        /** הודעה ארוכה (למשל: סינון אינטרנט שחוסם את החיבור המאובטח) — יורדת שורה, והחלון גדל כדי שלא תיחתך */
        private boolean grown;

        private void longError(String msg) {
            stationError.setText(wrapHtml(msg, 450));
            if (msg.length() > 70 && !grown) {
                grown = true;
                setSize(getWidth(), getHeight() + 50);
            }
            stationError.revalidate();
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
            stop.addActionListener(e -> { mine.cancel(); showCard("choose"); });
            p.add(stop);
            showCard("message");
            p.revalidate();
            p.repaint();
            new SwingWorker<Account.Result, Void>() {
                @Override
                protected Account.Result doInBackground() {
                    return Account.signIn(role, u -> url[0] = u, mine, 10 * 60 * 1000L);
                }

                @Override
                protected void done() {
                    if (cancel != mine || !isDisplayable()) return;   // החלון נסגר / התחילה כניסה אחרת
                    Account.Result r;
                    try { r = get(); } catch (Exception e) { r = new Account.Result("error", "הכניסה לא הצליחה.", null); }
                    // ביטול כשהכניסה כבר הסתיימה בדפדפן (הקוד מומש) — נכנסנו: מראים את התוצאה ולא "בוטל"
                    if (mine.cancelled && !r.ok() && !"pay".equals(r.status) && !"register".equals(r.status)) return;
                    afterSignIn(role, r);
                }
            }.execute();
        }

        private void afterSignIn(String role, Account.Result r) {
            switch (r.status) {
                case "ok":
                    changed();
                    success("נכנסת בהצלחה", "teacher".equals(role) ? "המאגר פתוח. את קוד המורה ואת העמדות רואים בדף \"החשבון שלי\" בדפדפן." : "המאגר פתוח — גם בפעם הבאה, גם בלי אינטרנט.");
                    break;
                case "pay":
                case "register":
                    changed();
                    studentStep(r.status, true);   // הדפדפן כבר בדף התשלום / ההרשמה (signIn הפנה אותו לשם)
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
                    if (r.message.isEmpty()) showCard("choose");
                    else show("לא התקבלה כניסה", r.message, WARN, WARN_SOFT, true);
                    break;
                default:
                    show("הכניסה לא הצליחה", r.message.isEmpty() ? "נסו שוב בעוד רגע." : r.message, WARN, WARN_SOFT, true);
            }
        }

        /** אף פעולה לא באמצע: מוצגות שלוש האפשרויות (אז מותר להחליף למסך "נשאר להשלים הרשמה / לשלם") */
        boolean idle() {
            return "choose".equals(showing);
        }

        /**
         * תלמיד מחובר שנשאר לו צעד אחד בדפדפן לפני שהמאגר נפתח: "register" — הרשמה (שם מלא וטלפון), "pay" — תשלום.
         * מסבירים, כפתור פותח את הדף (עם הסימן של החשבון — Account.studentPageUrl), ובודקים מול השרת כל כמה שניות: כשהצעד
         * הושלם — המאגר נפתח לבד. pageOpen: הדפדפן כבר בדף הזה (מיד אחרי "כניסה עם Google").
         */
        void studentStep(final String what, boolean pageOpen) {
            if (payPoll != null) payPoll.stop();
            final boolean pay = "pay".equals(what);
            Map<String, Object> st = Account.studentInfo();
            Object price = st == null ? null : st.get("price");
            String cost = price instanceof Number && ((Number) price).doubleValue() > 0 ? " (" + ((Number) price).intValue() + " \u20aa)" : "";
            String title = pay ? (pageOpen ? "מחכים לתשלום" : "המאגר פתוח עכשיו בתשלום") : (pageOpen ? "משלימים הרשמה בדפדפן" : "נשאר להשלים הרשמה");
            String text = pay
                    ? "כדי להמשיך להשתמש במאגר התרגילים צריך תשלום אחד" + cost + ", בדף מאובטח בדפדפן. אחרי שהתשלום עובר, החלון הזה מתעדכן לבד — לא צריך ללחוץ על כלום."
                    : "ממלאים פעם אחת שם מלא וטלפון, בדף בדפדפן. אחרי ההרשמה החלון הזה מתעדכן לבד והמאגר נפתח — לא צריך ללחוץ על כלום.";
            JPanel p = message;
            p.removeAll();
            p.setBorder(BorderFactory.createEmptyBorder(34, 40, 26, 40));
            JPanel box = new Rounded(BLUE_SOFT, BLUE_SOFT, 14);
            box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
            box.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
            box.setAlignmentX(Component.RIGHT_ALIGNMENT);
            box.add(text(title, 21, true, BLUE));
            box.add(Box.createVerticalStrut(8));
            box.add(wrap(text, 15, INK, 420));
            String who = st == null ? "" : Json.str(st.get("email"));
            if (!who.isEmpty()) {
                box.add(Box.createVerticalStrut(8));
                box.add(text("החשבון: " + who, 13, false, MUTED));
            }
            p.add(box);
            p.add(Box.createVerticalStrut(16));
            final JLabel hint = text(pageOpen ? "הדף פתוח בדפדפן." : " ", 13, false, MUTED);
            final JButton go = pageOpen ? secondary(pay ? "פתיחת דף התשלום שוב" : "פתיחת דף ההרשמה שוב") : primary(pay ? "לתשלום בדפדפן" : "להרשמה בדפדפן");
            go.putClientProperty("dsviz.id", "student-step");
            go.addActionListener(e -> {
                String url = Account.studentPageUrl(what);
                if (url.isEmpty()) return;
                try {
                    Account.browser.accept(url);
                    hint.setText(RLM + "הדף נפתח בדפדפן. אחרי שמסיימים שם, החלון הזה מתעדכן לבד.");
                } catch (RuntimeException x) {
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(url), null);
                    hint.setText(RLM + "הדפדפן לא נפתח. הכתובת הועתקה — מדביקים אותה בדפדפן.");
                }
            });
            JButton back = secondary("כניסה אחרת");
            back.addActionListener(e -> { if (payPoll != null) payPoll.stop(); showCard("choose"); });
            JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.RIGHT_ALIGNMENT);
            row.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            row.add(go);
            row.add(back);
            p.add(row);
            p.add(Box.createVerticalStrut(10));
            p.add(hint);
            p.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            showCard("message");
            p.revalidate();
            p.repaint();
            // בודקים מול השרת: כל 5 שניות בחמש הדקות הראשונות, ואחר כך כל 20 שניות (כל עוד החלון פתוח)
            final int[] ticks = { 0 };
            final Timer poll = new Timer(POLL_MS, null);
            payPoll = poll;
            poll.addActionListener(e -> new SwingWorker<Account.Result, Void>() {
                @Override
                protected Account.Result doInBackground() { return Account.refresh(); }

                @Override
                protected void done() {
                    if (payPoll != poll || !isDisplayable()) return;
                    if (++ticks[0] == 60) poll.setDelay(4 * POLL_MS);
                    try {
                        get();
                    } catch (Exception ignored) {
                        return;   // ננסה שוב
                    }
                    if (Account.current() != null) {
                        poll.stop();
                        changed();
                        success(pay ? "התשלום התקבל" : "ההרשמה הושלמה", pay ? "המאגר פתוח עד סוף שנת הלימודים, בכל המחשבים שלך." : "המאגר פתוח — גם בפעם הבאה, גם בלי אינטרנט.");
                        return;
                    }
                    String now = studentNeeds();
                    if (!now.isEmpty() && !now.equals(what)) studentStep(now, false);   // נרשם, והמאגר בתשלום — עוברים למסך התשלום
                    else if (now.isEmpty() && Account.userState() == null) { poll.stop(); showCard("choose"); }   // התנתק ממקום אחר
                }
            }.execute());
            poll.start();
        }

        /** כל כמה זמן בודקים מול השרת אם ההרשמה / התשלום הושלמו (לבדיקות — קצר יותר) */
        static int POLL_MS = 5000;

        private void showCard(String name) {
            showing = name;
            cards.show(body, name);
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
                b.addActionListener(e -> { if (payPoll != null) payPoll.stop(); showCard("choose"); });
                p.add(b);
            }
            showCard("message");
            p.revalidate();
            p.repaint();
        }
    }

    // ------------------------------------------------------------------ כניסה / התנתקות: מי שצריך לדעת (הכפתור "עמדת ניהול" ב-Eclipse)

    private static final List<Runnable> CHANGED = new java.util.concurrent.CopyOnWriteArrayList<Runnable>();

    private static boolean watching;

    /**
     * נקרא אחרי כניסה / התנתקות (בחוט של Swing) — למשל הכפתור "עמדת ניהול" בסרגל של Eclipse מתעדכן מיד. גם כשהכניסה השתנתה
     * בתוכנה אחרת במחשב (Eclipse / Visual Studio / IntelliJ / "מודלים חישוביים" — AccessWatch)
     */
    public static void onChange(Runnable r) {
        CHANGED.add(r);
        synchronized (CHANGED) {
            if (watching) return;
            watching = true;
        }
        AccessWatch.listen(() -> SwingUtilities.invokeLater(AccessGate::notifyListeners));
    }

    /** מפסיק להודיע (חלון שנסגר) */
    public static void offChange(Runnable r) {
        CHANGED.remove(r);
    }

    static void changed() {
        AccessWatch.baseline();   // כבר מודיעים כאן — המעקב לא יודיע שוב על אותו שינוי
        notifyListeners();
    }

    private static void notifyListeners() {
        for (Runnable r : CHANGED) {
            try { r.run(); } catch (RuntimeException ignored) { /* מאזין שנכשל לא עוצר את האחרים */ }
        }
    }

    /**
     * יש כאן משתמש שאפשר לנתק: מורה או תלמיד שנכנסו עם Google. עמדת מחשב — לא: את העמדה משחררים המורה / ההנהלה
     * (תלמיד בכיתה לא יכול "לנתק" מחשב של בית הספר ולתפוס היתר מחדש). אין התנתקות של עמדה בשום מקום:
     * מורה / תלמיד שנכנסו עם Google במחשב שהוא גם עמדה — מתנתקים רק מהחשבון שלהם (העמדה נשארת); כניסה שעוד לא הושלמה (תלמיד
     * שעוד לא שילם) על עמדה — בלי כפתור, כדי שלא ייראה כאילו הוא מנתק את העמדה
     */
    public static boolean canSignOut() {
        if (disabled()) return false;
        Map<String, Object> u = Account.userState();
        if (u == null || Json.str(u.get("token")).isEmpty()) return false;
        return Account.userSignedIn() || !Account.stationConnected();
    }

    /** לבדיקות: התשובה לשאלה "להתנתק?" בלי חלון (null — שואלים בחלון) */
    static volatile Boolean confirmForTest;

    /**
     * "התנתקות": שואלים (בעברית), מבטלים את הכרטיס בשרת ומוחקים את הכניסה מהמחשב (בלי אינטרנט — רק מהמחשב, והשרת מעודכן
     * בפעם הבאה), סוגרים את עמדת הניהול, ומודיעים (onChange). after — אחרי התנתקות (למשל: סוגרים את המאגר). עמדת מחשב לא מושפעת.
     */
    public static void signOut(final Component parent, final Runnable after) {
        SwingUtilities.invokeLater(() -> {
            if (!canSignOut()) return;
            Map<String, Object> u = Account.userState();
            String who = u == null ? "" : Json.str(u.get("email"));
            boolean teacher = u != null && "teacher".equals(Json.str(u.get("role")));
            String q = "להתנתק" + (who.isEmpty() ? "" : " מהחשבון " + who) + " במחשב הזה?\n"
                    + (teacher ? "עמדת הניהול והמאגר ייסגרו. עמדות המחשב שחיברת בכיתה ממשיכות לעבוד כרגיל." : "המאגר ייסגר.")
                    + "\nבפעם הבאה ייפתח מסך הכניסה.";
            Boolean yes = confirmForTest;
            if (yes == null) yes = ask(parent, "התנתקות", q, "התנתקות", "ביטול");
            if (!yes) return;
            new SwingWorker<Account.Result, Void>() {
                @Override
                protected Account.Result doInBackground() { return Account.signOut(); }

                @Override
                protected void done() {
                    Account.Result r;
                    try { r = get(); } catch (Exception e) { r = new Account.Result("offline", "התנתקת במחשב הזה.", null); }
                    lastSignOut = r;
                    if (teacherPanel != null) teacherPanel.dispose();
                    changed();
                    String msg = r.ok() ? "התנתקת. בפעם הבאה שפותחים את המאגר מופיע מסך הכניסה." : r.message;
                    Component owner = parent != null && parent.isDisplayable() ? parent : null;
                    if (confirmForTest == null) tell(owner, "התנתקות", msg);
                    if (after != null) after.run();
                }
            }.execute();
        });
    }

    /** לבדיקות: התוצאה של ההתנתקות האחרונה */
    static volatile Account.Result lastSignOut;

    /** חלון שאלה קטן מימין לשמאל, מקדימה. true — הכפתור הראשון */
    static boolean ask(Component parent, String title, String text, String yes, String no) {
        final boolean[] answer = { false };
        final JDialog d = small(parent, title, text);
        JPanel row = (JPanel) d.getRootPane().getClientProperty("dsviz.buttons");
        JButton y = primary(yes), n = secondary(no);
        y.addActionListener(e -> { answer[0] = true; d.dispose(); });
        n.addActionListener(e -> d.dispose());
        row.add(y);
        row.add(n);
        d.getRootPane().setDefaultButton(n);   // Enter לא מנתק בטעות
        d.pack();
        d.setLocationRelativeTo(parent);
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) { front(d); }
        });
        d.setVisible(true);   // מודאלי — חוזר כשנסגר
        return answer[0];
    }

    /** הודעה קצרה עם "סגירה" */
    static void tell(Component parent, String title, String text) {
        final JDialog d = small(parent, title, text);
        JPanel row = (JPanel) d.getRootPane().getClientProperty("dsviz.buttons");
        JButton ok = primary("סגירה");
        ok.addActionListener(e -> d.dispose());
        row.add(ok);
        d.getRootPane().setDefaultButton(ok);
        d.pack();
        d.setLocationRelativeTo(parent);
        d.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent e) { front(d); }
        });
        d.setVisible(true);
    }

    private static JDialog small(Component parent, String title, String text) {
        Window owner = parent == null ? null : parent instanceof Window ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        final JDialog d = new JDialog(owner, title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel col = column();
        col.setBorder(BorderFactory.createEmptyBorder(20, 24, 18, 24));
        for (String line : text.split("\n")) {
            col.add(wrap(line, 15, INK, 380));
            col.add(Box.createVerticalStrut(6));
        }
        col.add(Box.createVerticalStrut(10));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.RIGHT_ALIGNMENT);
        col.add(row);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(PAGE);
        root.add(col, BorderLayout.CENTER);
        root.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        d.setContentPane(root);
        d.getRootPane().putClientProperty("dsviz.buttons", row);
        return d;
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
    /** כמה זמן לכל היותר חלון שנפתח נשאר "מעל הכול" אם המשתמש לא נגע בו */
    static final int FRONT_MS = 20000;
    private static final java.util.Map<java.awt.Window, Boolean> FRONT = new java.util.WeakHashMap<java.awt.Window, Boolean>();

    static void front(final java.awt.Window w) {
        try {
            if ((w instanceof java.awt.Frame) && (((java.awt.Frame) w).getExtendedState() & java.awt.Frame.ICONIFIED) != 0)
                ((java.awt.Frame) w).setExtendedState(java.awt.Frame.NORMAL);
            w.setAlwaysOnTop(true);
            w.toFront();
            w.requestFocus();
            if (FRONT.containsKey(w)) return;   // כבר מוחזק מקדימה (נקרא שוב מ-windowOpened)
            FRONT.put(w, Boolean.TRUE);
            // נשאר מעל הכול עד שהמשתמש נוגע בחלון (לחיצה / מקש בתוכו), או FRONT_MS לכל היותר; ואז חלון רגיל (אפשר להוריד אחורה).
            // לא רק לרגע: Eclipse / Visual Studio מפעילים את החלון שלהם שוב אחרי הלחיצה על הכפתור (וב-Visual Studio הכלי הוא תהליך
            // נפרד, ש-Windows לא נותן לו לקחת את הפוקוס) — "תמיד מעל" לרגע ואז רגיל = המסך נבלע מאחור
            final long until = System.currentTimeMillis() + FRONT_MS;
            final java.awt.event.AWTEventListener[] touch = { null };
            final javax.swing.Timer t = new javax.swing.Timer(500, null);
            final Runnable release = () -> {
                t.stop();
                if (touch[0] != null) java.awt.Toolkit.getDefaultToolkit().removeAWTEventListener(touch[0]);
                FRONT.remove(w);
                if (w.isDisplayable()) w.setAlwaysOnTop(false);
            };
            touch[0] = ev -> {
                if (ev.getID() != java.awt.event.MouseEvent.MOUSE_PRESSED && ev.getID() != java.awt.event.KeyEvent.KEY_PRESSED) return;
                Object src = ev.getSource();
                if (src instanceof Component && SwingUtilities.getWindowAncestor((Component) src) == w || src == w)
                    SwingUtilities.invokeLater(release);
            };
            java.awt.Toolkit.getDefaultToolkit().addAWTEventListener(touch[0],
                    java.awt.AWTEvent.MOUSE_EVENT_MASK | java.awt.AWTEvent.KEY_EVENT_MASK);
            final int[] ticks = { 0 };
            t.addActionListener(e -> {
                if (!w.isDisplayable() || System.currentTimeMillis() > until) {
                    release.run();
                    return;
                }
                if (++ticks[0] <= 6) w.toFront();   // בשלוש השניות הראשונות: שוב קדימה, אם סביבת העבודה הפעילה את עצמה
            });
            t.start();
        } catch (RuntimeException ignored) {
            // לא מצליחים להקדים — החלון עדיין פתוח
        }
    }

    public static void showTeacherPanel(final Component parent) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (teacherPanel != null && teacherPanel.isDisplayable()) {   // כבר פתוח — מביאים לקדמה ומעדכנים מהשרת
                    front(teacherPanel);
                    teacherPanel.reload();
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
        private final JLabel name = text("", 16, true, INK), email = text("", 13, false, MUTED);
        private final JLabel notice = text("", 14, true, WARN);
        private final JLabel big = new JLabel("—", SwingConstants.CENTER);
        private final JLabel note = wrap("", 13, MUTED, 400);
        private final JLabel seats = text("", 14, false, INK);
        /** "3 הגשות חדשות מחכות לך" — מהשרת (Account.teacherSummary); מוסתר כשאין תשובה */
        private final JLabel fresh = text(" ", 14, true, BLUE);
        private final JButton copy = secondary("העתק קוד");
        private String codeText = "";
        /** לבדיקות: כמה פעמים הנתונים נטענו מחדש מהשרת */
        volatile int reloads;

        TeacherPanel(Component parent) {
            super(parent == null ? null : SwingUtilities.getWindowAncestor(parent) != null ? SwingUtilities.getWindowAncestor(parent)
                    : parent instanceof Window ? (Window) parent : null, "עמדת ניהול", ModalityType.MODELESS);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            JPanel root = new JPanel(new BorderLayout());
            root.setBackground(PAGE);
            JPanel col = column();
            col.setBorder(BorderFactory.createEmptyBorder(22, 28, 22, 28));
            col.add(text("עמדת ניהול של המורה", 22, true, INK));
            col.add(Box.createVerticalStrut(8));
            col.add(name);
            col.add(Box.createVerticalStrut(2));
            col.add(email);
            notice.setOpaque(true);
            notice.setBackground(WARN_SOFT);
            notice.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            notice.setVisible(false);
            col.add(Box.createVerticalStrut(8));
            col.add(notice);
            col.add(Box.createVerticalStrut(10));
            col.add(text("הקוד שלך — לתלמידים ולמחשבי המעבדה", 14, true, INK));
            col.add(Box.createVerticalStrut(6));
            big.setFont(new Font(Font.MONOSPACED, Font.BOLD, 40));
            big.setForeground(BLUE);
            big.setOpaque(true);
            big.setBackground(BLUE_SOFT);
            big.setBorder(BorderFactory.createEmptyBorder(10, 24, 10, 24));
            big.setAlignmentX(Component.RIGHT_ALIGNMENT);
            big.putClientProperty("dsviz.id", "code");
            col.add(big);
            col.add(Box.createVerticalStrut(6));
            col.add(note);
            col.add(Box.createVerticalStrut(12));
            seats.putClientProperty("dsviz.id", "seats");
            col.add(seats);
            col.add(Box.createVerticalStrut(4));
            fresh.putClientProperty("dsviz.id", "fresh");
            fresh.setVisible(false);
            col.add(fresh);
            col.add(Box.createVerticalStrut(2));
            col.add(text("ההגשות, התלמידים, הכיתות והמחשבים — בעמדת הניהול בדפדפן.", 13, false, MUTED));
            col.add(Box.createVerticalStrut(16));
            JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.RIGHT_ALIGNMENT);
            JButton open = primary("פתח עמדת ניהול בדפדפן");
            open.addActionListener(e -> Account.openManagement());
            copy.addActionListener(e -> {
                if (codeText.isEmpty()) return;
                java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(codeText), null);
                copy.setText(RLM + "הועתק");
            });
            JButton out = secondary("התנתקות");
            out.addActionListener(e -> signOut(this, null));
            JButton close = secondary("סגירה");
            close.addActionListener(e -> dispose());
            row.add(open);
            row.add(copy);
            row.add(out);
            row.add(close);
            col.add(row);
            root.add(col, BorderLayout.CENTER);
            root.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            setContentPane(root);
            fill(Account.teacherInfo());
            pack();
            setMinimumSize(new Dimension(560, 0));
            setLocationRelativeTo(parent);
            reload();
        }

        /** מציג את מה ששמור במחשב (מהבדיקה האחרונה מול השרת). null — כבר לא מורה מחובר */
        void fill(Map<String, Object> t) {
            if (t == null) {
                codeText = "";
                name.setText(RLM + "החשבון כבר לא מחובר כמורה במחשב הזה");
                email.setText(" ");
                big.setText("—");
                seats.setText(" ");
                note.setText(" ");
                notice.setText(RLM + "הגישה כמורה לא פעילה (ההנהלה השביתה את החשבון, או שהכניסה בוטלה). מתחברים שוב מהמאגר.");
                notice.setVisible(true);
                copy.setEnabled(false);
            } else {
                codeText = Json.str(t.get("code"));
                String n = Json.str(t.get("name")), m = Json.str(t.get("email"));
                name.setText(RLM + (n.isEmpty() ? m : n));
                email.setText(RLM + (n.isEmpty() ? " " : m));
                big.setText(codeText.isEmpty() ? "—" : codeText);
                int s = ((Number) t.get("seats")).intValue(), u = ((Number) t.get("used")).intValue();
                seats.setText(RLM + (s < 0 ? "עמדות מחשב: מתעדכן…" : "עמדות מחשב בשימוש: " + Math.max(0, u) + " מתוך " + s));
                note.setText(wrapHtml(codeText.isEmpty() ? "הקוד עוד לא נטען. נכנסים לעמדת הניהול בדפדפן (הכפתור למטה) — שם הוא מוצג תמיד."
                        : "תלמיד שנכנס עם Google מצטרף אליך עם הקוד הזה, ובמעבדה מקלידים אותו במסך \"עמדת מחשב\". הקוד קבוע.", 400));
                notice.setVisible(false);
                copy.setEnabled(!codeText.isEmpty());
            }
            if (isDisplayable()) pack();
        }

        /** כמה הגשות חדשות מחכות למורה (מתלמידים וממחשבי המעבדה), או שאין. בלי תשובה מהשרת — השורה מוסתרת */
        void showFresh(Account.Result s) {
            if (s == null || !s.ok()) {
                fresh.setVisible(false);
            } else {
                int a = num(s.body.get("newStudents")), b = num(s.body.get("newStations")), kids = num(s.body.get("students"));
                String text = a + b == 0 ? "אין הגשות חדשות" + (kids > 0 ? " · " + (kids == 1 ? "תלמיד אחד" : kids + " תלמידים") : "")
                        : (a + b == 1 ? "הגשה חדשה אחת מחכה לך" : (a + b) + " הגשות חדשות מחכות לך") + " (" + a + " מתלמידים · " + b + " ממחשבי המעבדה)";
                fresh.setText(RLM + text);
                fresh.setForeground(a + b == 0 ? MUTED : BLUE);
                fresh.setVisible(true);
            }
            if (isDisplayable()) pack();
        }

        private static int num(Object o) {
            return o instanceof Number ? ((Number) o).intValue() : 0;
        }

        /** בדיקה מול השרת ברקע: המקומות / הקוד העדכניים; מורה שהושבת — ההודעה, והכפתור בסרגל נכבה */
        void reload() {
            Thread t = new Thread(() -> {
                Account.Result r;
                try { r = Account.refresh(); } catch (Throwable e) { r = null; }
                final boolean online = r != null && !"offline".equals(r.status) && !"error".equals(r.status);
                Account.Result sum;
                try { sum = online ? Account.teacherSummary() : null; } catch (Throwable e) { sum = null; }
                final Account.Result summary = sum;
                SwingUtilities.invokeLater(() -> {
                    if (!isDisplayable()) return;
                    Map<String, Object> info = Account.teacherInfo();
                    if (online || info == null) fill(info);
                    showFresh(info == null ? null : summary);
                    reloads++;
                    if (info == null) changed();
                });
            }, "dsviz-teacher-panel");
            t.setDaemon(true);
            t.start();
        }
    }

    // ------------------------------------------------------------------ תלמיד: "המורה שלי" (הצטרפות עם קוד המורה, מתוך התוכנה)

    /** תלמיד שמחובר עם Google במחשב הזה, והמאגר פתוח לו */
    public static boolean isStudent() {
        if (disabled()) return false;
        Account.Access a = Account.current();
        return a != null && !a.isStation() && !a.isTeacher() && Account.studentInfo() != null;
    }

    /** המורה של התלמיד המחובר (השם), או "" — תלמיד עצמאי / עוד לא הצטרף / לא תלמיד */
    public static String studentTeacher() {
        Map<String, Object> st = isStudent() ? Account.studentInfo() : null;
        return st == null ? "" : Json.str(st.get("teacher"));
    }

    /**
     * הטקסט של הקישור במאגר לתלמיד מחובר: "המורה שלי" (כשהוא משויך) / "הצטרפות למורה" (כשלא). "" — לא תלמיד, או שהשרת
     * עוד לא יודע לקבל הגשות (אז אין טעם להצטרף מכאן)
     */
    public static String teacherLink() {
        if (!isStudent() || !Account.serverHas(false, "submit")) return "";
        return studentTeacher().isEmpty() ? "הצטרפות למורה" : "המורה שלי";
    }

    /** החלון הפתוח (או null) — גם לבדיקות */
    static MyTeacher myTeacher;

    /** החלון "המורה שלי": מי המורה, הצטרפות עם קוד המורה, יציאה / החלפה. רק לתלמיד מחובר */
    public static void showMyTeacher(final Component parent) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (!isStudent()) return;
                if (myTeacher != null && myTeacher.isDisplayable()) {
                    front(myTeacher);
                    return;
                }
                final MyTeacher d = new MyTeacher(parent);
                myTeacher = d;
                d.addWindowListener(new java.awt.event.WindowAdapter() {
                    @Override
                    public void windowClosed(java.awt.event.WindowEvent e) {
                        if (myTeacher == d) myTeacher = null;
                    }
                });
                d.setVisible(true);
                front(d);
            }
        });
    }

    /** שאלת אישור (בבדיקות — confirmForTest עונה בלי חלון) */
    private static boolean confirm(Component parent, String title, String text, String yes) {
        Boolean t = confirmForTest;
        return t != null ? t : ask(parent, title, text, yes, "ביטול");
    }

    static final class MyTeacher extends JDialog {
        private static final long serialVersionUID = 1L;
        private final JPanel body = column();
        private final JTextField code = field("");
        private final JLabel error = text(" ", 13, false, new Color(0xB42318));
        /** הודעה ירוקה אחרי פעולה שהצליחה ("הצטרפת למורה…") */
        private String done = "";
        /** מוצג שדה הקוד גם כשכבר יש מורה ("החלפת מורה") */
        private boolean switching;
        private boolean busy;

        MyTeacher(Component parent) {
            super(parent == null ? null : SwingUtilities.getWindowAncestor(parent) != null ? SwingUtilities.getWindowAncestor(parent)
                    : parent instanceof Window ? (Window) parent : null, "המורה שלי", ModalityType.MODELESS);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            JPanel root = new JPanel(new BorderLayout());
            root.setBackground(PAGE);
            body.setBorder(BorderFactory.createEmptyBorder(22, 28, 22, 28));
            root.add(body, BorderLayout.CENTER);
            setContentPane(root);
            code.setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
            code.setHorizontalAlignment(SwingConstants.CENTER);
            code.setMaximumSize(new Dimension(190, 46));
            code.setPreferredSize(new Dimension(190, 46));
            code.putClientProperty("dsviz.id", "teacher-code");
            error.putClientProperty("dsviz.id", "teacher-error");
            fill();
            setMinimumSize(new Dimension(520, 0));
            setLocationRelativeTo(parent);
            // המצב העדכני מהשרת (המורה מחק את התלמיד / התלמיד הצטרף מהדף בדפדפן) — ברקע
            Thread t = new Thread(() -> {
                try { Account.refresh(); } catch (Throwable ignored) { /* נשאר מה ששמור */ }
                SwingUtilities.invokeLater(() -> { if (isDisplayable() && !busy) { fill(); changed(); } });
            }, "dsviz-my-teacher");
            t.setDaemon(true);
            t.start();
        }

        /** בונה את החלון לפי המצב השמור: יש מורה → מי הוא + "החלפת מורה" / "יציאה מהמורה"; אין → שדה לקוד המורה */
        void fill() {
            Map<String, Object> st = Account.studentInfo();
            String teacher = st == null ? "" : Json.str(st.get("teacher")), school = st == null ? "" : Json.str(st.get("school"));
            boolean linked = !teacher.isEmpty();
            body.removeAll();
            body.add(text(linked && !switching ? "המורה שלי" : linked ? "החלפת מורה" : "הצטרפות למורה", 22, true, INK));
            body.add(Box.createVerticalStrut(10));
            if (!done.isEmpty()) {
                JLabel ok = text(done, 14, true, GREEN);
                ok.setOpaque(true);
                ok.setBackground(GREEN_SOFT);
                ok.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
                ok.putClientProperty("dsviz.id", "teacher-done");
                body.add(ok);
                body.add(Box.createVerticalStrut(10));
            }
            if (linked) {
                JPanel box = new Rounded(BLUE_SOFT, BLUE_SOFT, 14);
                box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
                box.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
                box.setAlignmentX(Component.RIGHT_ALIGNMENT);
                JLabel who = text(teacher, 19, true, BLUE);
                who.putClientProperty("dsviz.id", "teacher-name");
                box.add(who);
                if (!school.isEmpty()) {
                    box.add(Box.createVerticalStrut(2));
                    box.add(text(school, 14, false, MUTED));
                }
                body.add(box);
                body.add(Box.createVerticalStrut(10));
                body.add(wrap("כדי להגיש תרגיל: בחלון השאלה לוחצים \"הגשה למורה\". המורה רואה את הקוד ואת מה שיצא בהרצה.", 14, MUTED, 430));
                body.add(Box.createVerticalStrut(14));
            }
            final JButton join = primary(linked ? "מעבר למורה הזה" : "הצטרפות");
            if (!linked || switching) {
                body.add(wrap(linked ? "מקלידים את הקוד של המורה החדש (4 תווים). ההגשות שכבר נשלחו נשארות אצל המורה הקודם."
                        : "מקלידים את הקוד שהמורה נותן (4 תווים). אחרי ההצטרפות מגישים למורה תרגילים ישר מחלון השאלה.", 14, linked ? MUTED : INK, 430));
                body.add(Box.createVerticalStrut(12));
                body.add(text("קוד המורה", 14, true, INK));
                body.add(Box.createVerticalStrut(6));
                JPanel line = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
                line.setOpaque(false);
                line.setAlignmentX(Component.RIGHT_ALIGNMENT);
                line.add(code);
                body.add(line);
                body.add(Box.createVerticalStrut(6));
                body.add(error);
                body.add(Box.createVerticalStrut(12));
                join.putClientProperty("dsviz.id", "teacher-join");
                join.addActionListener(e -> join(join, false));
                getRootPane().setDefaultButton(join);
            } else getRootPane().setDefaultButton(null);
            JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            row.setOpaque(false);
            row.setAlignmentX(Component.RIGHT_ALIGNMENT);
            if (!linked || switching) row.add(join);
            if (linked && !switching) {
                JButton other = secondary("החלפת מורה");
                other.putClientProperty("dsviz.id", "teacher-switch");
                other.addActionListener(e -> { switching = true; done = ""; error.setText(" "); fill(); code.requestFocusInWindow(); });
                final JButton leave = secondary("יציאה מהמורה");
                leave.putClientProperty("dsviz.id", "teacher-leave");
                leave.addActionListener(e -> leave(leave, teacher));
                row.add(other);
                row.add(leave);
            } else if (linked) {
                JButton back = secondary("חזרה");
                back.addActionListener(e -> { switching = false; error.setText(" "); fill(); });
                row.add(back);
            }
            JButton close = secondary("סגירה");
            close.putClientProperty("dsviz.id", "teacher-close");
            close.addActionListener(e -> dispose());
            row.add(close);
            body.add(row);
            body.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            code.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
            body.revalidate();
            body.repaint();
            pack();
        }

        private void say(String msg) {
            error.setText(wrapHtml(msg, 430));
            pack();
        }

        private void join(final JButton go, final boolean replace) {
            final String c = code.getText().trim();
            if (c.replaceAll("[\\s-]", "").length() != 4) { say("הקוד הוא 4 תווים, למשל K7QM."); return; }
            busy = true;
            go.setEnabled(false);
            go.setText("רגע…");
            new SwingWorker<Account.Result, Void>() {
                @Override
                protected Account.Result doInBackground() { return Account.studentJoin(c, replace); }

                @Override
                protected void done() {
                    busy = false;
                    if (!isDisplayable()) return;
                    Account.Result r;
                    try { r = get(); } catch (Exception e) { r = new Account.Result("error", "", null); }
                    String name = Json.str(r.teacher().get("name"));
                    if (name.isEmpty()) name = "המורה";
                    go.setEnabled(true);
                    go.setText(switching ? "מעבר למורה הזה" : "הצטרפות");
                    lastJoin = r.status;
                    switch (r.status) {
                        case "ok":
                            MyTeacher.this.done = "ההצטרפות הצליחה. מעכשיו מגישים למורה " + name + ".";
                            switching = false;
                            code.setText("");
                            error.setText(" ");
                            changed();
                            fill();
                            break;
                        case "bad-code":
                            say("הקוד לא נמצא. בודקים מול המורה ומקלידים שוב.");
                            break;
                        case "closed":
                            say(name + " סגר כרגע את ההצטרפות עם הקוד. מבקשים מהמורה לפתוח אותה (בעמדת הניהול שלו).");
                            break;
                        case "blocked":
                            say("הגישה של המורה הזה הושבתה. פונים למורה.");
                            break;
                        case "replace":   // כבר משויך למורה אחר: שואלים לפני שעוברים
                            if (confirm(MyTeacher.this, "מעבר למורה אחר", "החשבון כבר משויך למורה אחר. לעבור למורה " + name + "? ההגשות שכבר נשלחו נשארות אצל המורה הקודם.", "מעבר")) join(go, true);
                            break;
                        default:
                            say(r.message.isEmpty() ? "ההצטרפות לא הצליחה. מנסים שוב בעוד רגע." : r.message);
                    }
                }
            }.execute();
        }

        /** לבדיקות: התשובה האחרונה של ההצטרפות */
        volatile String lastJoin = "";

        private void leave(final JButton go, final String teacher) {
            if (!confirm(this, "יציאה מהמורה", "לצאת מהמורה " + teacher + "? אחרי היציאה אי אפשר להגיש לו תרגילים. מה שכבר הוגש נשאר אצלו.", "יציאה")) return;
            busy = true;
            go.setEnabled(false);
            new SwingWorker<Account.Result, Void>() {
                @Override
                protected Account.Result doInBackground() { return Account.studentLeave(); }

                @Override
                protected void done() {
                    busy = false;
                    if (!isDisplayable()) return;
                    Account.Result r;
                    try { r = get(); } catch (Exception e) { r = new Account.Result("error", "", null); }
                    go.setEnabled(true);
                    if (r.ok()) {
                        MyTeacher.this.done = "יצאת מהמורה " + teacher + ".";
                        changed();
                        fill();
                    } else {
                        MyTeacher.this.done = "";
                        fill();
                        JLabel l = text(r.message.isEmpty() ? "היציאה לא הצליחה. מנסים שוב בעוד רגע." : r.message, 13, false, new Color(0xB42318));
                        body.add(Box.createVerticalStrut(8));
                        body.add(l);
                        body.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
                        pack();
                    }
                }
            }.execute();
        }
    }

    static String wrapHtml(String s, int width) {
        String esc = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html><div dir='rtl' style='width:" + width + "px'>" + esc + "</div></html>";
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

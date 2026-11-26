package il.lamerhav.dsviz;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * הרשימות בעמדת הניהול של המורה (AccessGate.TeacherPanel): מחשבי המעבדה שמשויכים למורה והתלמידים שמשויכים אליו.
 * בנוי לכיתה אמיתית — 30-40 תלמידים ועשרות מחשבים: רשימה אחת בכל רגע (שתי לשוניות עם המספרים), חיפוש, סינון
 * (תלמידים — לפי כיתה; מחשבים — לפי מתי פעלו), שורות של שורה אחת בעמודות מיושרות עם כותרת עמודות שנשארת במקום,
 * התלמידים מקובצים לפי כיתה, והרשימה גדלה עם החלון. הפעולות עצמן (שחרור מחשב / הסרת תלמיד) — אצל מי שיצר (Actions).
 */
final class Roster extends JPanel {
    private static final long serialVersionUID = 1L;

    interface Actions {
        void releaseStation(String id, String name, JButton b);

        void releaseStudent(String email, String name, JButton b);
    }

    static final int ROW = 34, GROUP = 28, HEAD = 26, LIST_W = 640, LIST_H = HEAD + 9 * ROW + 2;
    private static final String RLM = "‏", LRM = "‎";
    private static final Color ZEBRA = new Color(0xFAFBFD), RULE = new Color(0xEEF0F4), GREY = new Color(0x8A93A6), BAND = new Color(0xEEF2FA);
    private static final Cols STATION_COLS = new Cols(new double[] { 1, 0, 0 }, new int[] { 0, 240, 78 });
    private static final Cols STUDENT_COLS = new Cols(new double[] { 1, 1.15, 0, 0 }, new int[] { 0, 0, 112, 78 });

    private final Actions actions;
    /** הגרסה עם מרכז הבקרה (תלמידים מצטרפים למורה) — הלשונית "תלמידים" תמיד מוצגת; בלי — רק כשיש תלמידים משויכים */
    private final boolean classroom;
    private final JButton tabStations = tab("tab-stations"), tabStudents = tab("tab-students");
    private final Search search = new Search();
    /** הסינון: כפתור שפותח תפריט (כיתות / מתי פעלו) — נראה אותו דבר בכל סביבה, בלי תיבת הבחירה של מערכת ההפעלה */
    private final JButton filter = AccessGate.secondary("");
    private final List<String> filterLabels = new ArrayList<String>();
    private final JLabel count = AccessGate.text(" ", 12, false, AccessGate.MUTED);
    private final JLabel note = AccessGate.text(" ", 12, false, AccessGate.MUTED);
    private final JPanel head = new JPanel(), list = new JPanel(new Stack());
    private final JScrollPane scroll;

    private List<Map<String, Object>> stations = new ArrayList<Map<String, Object>>(), students = new ArrayList<Map<String, Object>>();
    private final List<String> filterKeys = new ArrayList<String>();
    private String stationFilter = "", classFilter = "", message = "טוען את הרשימות…", flash;
    private boolean onStudents, picked, loaded;
    private int releasesLeft = -1;
    /** לבדיקות: כמה שורות (מחשבים / תלמידים) מוצגות עכשיו ברשימה, אחרי החיפוש והסינון */
    volatile int shownRows;

    Roster(Actions actions, boolean classroom) {
        super(new BorderLayout(0, 6));
        this.actions = actions;
        this.classroom = classroom;
        setOpaque(false);
        setAlignmentX(Component.RIGHT_ALIGNMENT);

        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEADING, 2, 0));
        tabs.setOpaque(false);
        tabs.add(tabStations);
        tabs.add(tabStudents);
        tabStations.addActionListener(e -> pick(false));
        tabStudents.addActionListener(e -> pick(true));

        filter.setFont(new Font(Font.DIALOG, Font.PLAIN, 13));
        filter.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AccessGate.FIELD, 1, true), BorderFactory.createEmptyBorder(5, 12, 5, 12)));
        filter.putClientProperty("dsviz.id", "roster-filter");
        filter.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            for (int i = 0; i < filterKeys.size(); i++) {
                final String key = filterKeys.get(i);
                JMenuItem it = new JMenuItem(RLM + filterLabels.get(i));
                it.setFont(new Font(Font.DIALOG, key.equals(onStudents ? classFilter : stationFilter) ? Font.BOLD : Font.PLAIN, 13));
                it.setHorizontalAlignment(SwingConstants.RIGHT);
                it.putClientProperty("dsviz.id", "roster-filter:" + key);
                it.addActionListener(x -> filterBy(key));
                menu.add(it);
            }
            menu.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
            menu.setPopupSize(Math.max(filter.getWidth(), menu.getPreferredSize().width), menu.getPreferredSize().height);
            menu.show(filter, 0, filter.getHeight() + 2);
        });
        search.putClientProperty("dsviz.id", "roster-search");
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { rebuild(true); }

            public void removeUpdate(DocumentEvent e) { rebuild(true); }

            public void changedUpdate(DocumentEvent e) { rebuild(true); }
        });
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 0));
        tools.setOpaque(false);
        tools.add(filter);
        tools.add(search);

        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.setOpaque(false);
        bar.add(tabs, BorderLayout.LINE_START);
        bar.add(tools, BorderLayout.LINE_END);
        add(bar, BorderLayout.NORTH);

        list.setOpaque(true);
        list.setBackground(Color.WHITE);
        Track holder = new Track();
        holder.add(list, BorderLayout.NORTH);
        scroll = new JScrollPane(holder, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(AccessGate.FIELD, 1, true));
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.getVerticalScrollBar().setUnitIncrement(ROW);
        scroll.getVerticalScrollBar().setUI(new Bar());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(12, 0));
        scroll.getVerticalScrollBar().setOpaque(false);
        JPanel corner = new JPanel();
        corner.setBackground(new Color(0xF7F8FB));
        scroll.setCorner(JScrollPane.UPPER_LEADING_CORNER, corner);
        scroll.setCorner(JScrollPane.UPPER_TRAILING_CORNER, corner);
        scroll.setPreferredSize(new Dimension(LIST_W, LIST_H));
        scroll.setMinimumSize(new Dimension(200, HEAD + 3 * ROW));
        head.setOpaque(true);
        head.setBackground(new Color(0xF7F8FB));
        head.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, AccessGate.LINE), BorderFactory.createEmptyBorder(0, 12, 0, 12)));
        head.setPreferredSize(new Dimension(100, HEAD));
        // כותרת העמודות (head) — ב-columnHeader של הגלילה: נשארת במקום כשגוללים, ומיושרת עם השורות גם כשיש פס גלילה (rebuild)
        add(scroll, BorderLayout.CENTER);

        JPanel foot = new JPanel(new BorderLayout(8, 0));
        foot.setOpaque(false);
        note.putClientProperty("dsviz.id", "roster-note");
        count.putClientProperty("dsviz.id", "roster-count");
        foot.add(note, BorderLayout.CENTER);
        foot.add(count, BorderLayout.LINE_END);
        add(foot, BorderLayout.SOUTH);

        applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        rebuild(true);
    }

    // ------------------------------------------------------------------ מה מוצג

    /** הרשימות מהשרת (Account.teacherPanel): stations — id, name, lastSeen; students — email, name, className, lastSubmitAt */
    void show(List<Object> st, List<Object> su, int releasesLeft) {
        final Collator he = Collator.getInstance(new Locale("he"));
        stations = maps(st);
        students = maps(su);
        Collections.sort(stations, new Comparator<Map<String, Object>>() {
            public int compare(Map<String, Object> a, Map<String, Object> b) { return natural(s(a, "name"), s(b, "name"), he); }
        });
        Collections.sort(students, new Comparator<Map<String, Object>>() {
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                String ca = s(a, "className"), cb = s(b, "className");
                if (ca.isEmpty() != cb.isEmpty()) return ca.isEmpty() ? 1 : -1;   // בלי כיתה — בסוף
                int c = natural(ca, cb, he);
                return c != 0 ? c : he.compare(title(a), title(b));
            }
        });
        this.releasesLeft = releasesLeft;
        if (!picked && !loaded) onStudents = stations.isEmpty() && !students.isEmpty();
        loaded = true;
        rebuild(false);
    }

    /** אין רשימות להציג (עוד נטען / אין חיבור) — ההודעה במקום הרשימה */
    void message(String s) {
        loaded = false;
        message = s;
        rebuild(true);
    }

    /** אישור קצר אחרי פעולה ("המחשב שוחרר") — מוצג מתחת לרשימה עד הפעולה הבאה */
    void flash(String s) {
        flash = s;
        showNote();
    }

    boolean onStudents() {
        return onStudents;
    }

    private void pick(boolean studentsTab) {
        picked = true;
        flash = null;
        if (onStudents == studentsTab) return;
        onStudents = studentsTab;
        rebuild(true);
    }

    /** הסינון של הלשונית הנוכחית: "" — הכול; תלמידים — "c:<כיתה>" ("c:" — בלי כיתה); מחשבים — week / old / never */
    void filterBy(String key) {
        flash = null;
        if (onStudents) classFilter = key; else stationFilter = key;
        rebuild(true);
    }

    /** לבדיקות: האפשרויות שבתפריט הסינון עכשיו (המפתחות) */
    List<String> filterKeys() {
        return new ArrayList<String>(filterKeys);
    }

    private void rebuild(boolean top) {
        int at = scroll.getVerticalScrollBar().getValue();
        tabStudents.setVisible(classroom || !students.isEmpty());
        if (!tabStudents.isVisible()) onStudents = false;
        style(tabStations, "מחשבי המעבדה" + (loaded ? " (" + stations.size() + ")" : ""), !onStudents);
        style(tabStudents, "תלמידים" + (loaded ? " (" + students.size() + ")" : ""), onStudents);
        fillFilter();
        search.hint = onStudents ? "חיפוש לפי שם, מייל או כיתה" : "חיפוש לפי כינוי המחשב";
        search.setVisible(loaded && (onStudents ? students : stations).size() > 0);
        search.repaint();
        head.removeAll();
        head.setLayout(onStudents ? STUDENT_COLS : STATION_COLS);
        for (String h : onStudents ? new String[] { "שם", "מייל", "הגשה אחרונה", "" } : new String[] { "כינוי המחשב", "פעל לאחרונה", "" })
            head.add(AccessGate.text(h, 12, true, AccessGate.MUTED));
        boolean rows = loaded && (onStudents ? students : stations).size() > 0;
        if (rows != (scroll.getColumnHeader() != null && scroll.getColumnHeader().getView() != null)) scroll.setColumnHeaderView(rows ? head : null);   // בלי שורות — בלי כותרת עמודות
        head.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        list.removeAll();
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        int shown = 0, all = (onStudents ? students : stations).size();
        boolean narrowed = !q.isEmpty() || !(onStudents ? classFilter : stationFilter).isEmpty();
        if (!loaded) {
            list.add(noteRow(message));
        } else if (all == 0) {
            list.add(noteRow(onStudents ? "עוד אין תלמידים משויכים. תלמיד שנכנס עם Google מצטרף אליך עם הקוד שלך."
                    : "עוד אין מחשבי מעבדה מחוברים. במחשב בכיתה מקלידים את הקוד שלך במסך \"עמדת מחשב\"."));
        } else if (onStudents) {
            Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<String, List<Map<String, Object>>>();
            boolean classes = false;
            for (Map<String, Object> m : students) {
                String cls = s(m, "className");
                classes |= !cls.isEmpty();
                if (!classFilter.isEmpty() && !classFilter.equals("c:" + cls)) continue;
                if (!q.isEmpty() && !(title(m) + "\n" + s(m, "email") + "\n" + cls).toLowerCase(Locale.ROOT).contains(q)) continue;
                List<Map<String, Object>> g = groups.get(cls);
                if (g == null) groups.put(cls, g = new ArrayList<Map<String, Object>>());
                g.add(m);
            }
            for (Map.Entry<String, List<Map<String, Object>>> g : groups.entrySet()) {
                if (classes) list.add(groupRow(g.getKey().isEmpty() ? "בלי כיתה" : g.getKey(), g.getValue().size()));
                int i = 0;
                for (Map<String, Object> m : g.getValue()) list.add(studentRow(m, i++));
                shown += g.getValue().size();
            }
        } else {
            for (Map<String, Object> m : stations) {
                if (!stationFilter.isEmpty() && !stationFilter.equals(age(s(m, "lastSeen")))) continue;
                if (!q.isEmpty() && !s(m, "name").toLowerCase(Locale.ROOT).contains(q)) continue;
                list.add(stationRow(m, shown++));
            }
        }
        if (loaded && all > 0 && shown == 0)
            list.add(noteRow(q.isEmpty() ? "אין " + (onStudents ? "תלמידים" : "מחשבים") + " בסינון הזה." : "לא נמצא \"" + search.getText().trim() + "\" — מנסים חלק מהשם, או מנקים את החיפוש."));
        shownRows = shown;
        count.setText(RLM + (loaded && narrowed && all > 0 ? "מוצגים " + shown + " מתוך " + all : " "));
        showNote();
        list.revalidate();
        list.repaint();
        revalidate();
        repaint();
        final int back = top ? 0 : at;
        javax.swing.SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(back));
    }

    private void showNote() {
        if (flash != null) {
            note.setText(RLM + "✓ " + flash);
            note.setForeground(AccessGate.GREEN);
        } else {
            note.setText(RLM + (!loaded ? " " : onStudents ? "תלמיד שמוסר נהיה עצמאי, וההגשות שלו נשארות אצלך."
                    : (releasesLeft >= 0 ? "שחרורי מחשבים שנשארו החודש: " + releasesLeft + " · " : "") + "מחשב ששוחרר מפנה מקום, ומתחבר שוב עם הקוד שלך."));
            note.setForeground(AccessGate.MUTED);
        }
    }

    /** הסינון של הלשונית: תלמידים — הכיתות (עם מספר התלמידים); מחשבים — מתי פעלו. מוסתר כשאין מה לסנן */
    private void fillFilter() {
        filterKeys.clear();
        filterLabels.clear();
        if (loaded && onStudents) {
            Map<String, Integer> n = new LinkedHashMap<String, Integer>();
            for (Map<String, Object> m : students) {
                Integer was = n.get(s(m, "className"));
                n.put(s(m, "className"), was == null ? 1 : was + 1);
            }
            if (n.size() > 1) {
                option("", "כל הכיתות (" + students.size() + ")");
                for (Map.Entry<String, Integer> e : n.entrySet()) option("c:" + e.getKey(), (e.getKey().isEmpty() ? "בלי כיתה" : e.getKey()) + " (" + e.getValue() + ")");
            }
        } else if (loaded) {
            int week = 0, old = 0, never = 0;
            for (Map<String, Object> m : stations) {
                String a = age(s(m, "lastSeen"));
                if (a.equals("week")) week++; else if (a.equals("old")) old++; else if (a.equals("never")) never++;
            }
            if (old + never > 0 || (week > 0 && week < stations.size())) {
                option("", "כל המחשבים (" + stations.size() + ")");
                if (week > 0) option("week", "פעלו בשבוע האחרון (" + week + ")");
                if (old > 0) option("old", "לא פעלו מעל 30 יום (" + old + ")");
                if (never > 0) option("never", "עוד לא פעלו (" + never + ")");
            }
        }
        int i = filterKeys.indexOf(onStudents ? classFilter : stationFilter);
        if (i < 0) {   // הכיתה / הקבוצה שנבחרה כבר לא קיימת (התלמיד האחרון שלה הוסר) — חוזרים ל"הכול"
            if (onStudents) classFilter = ""; else stationFilter = "";
            i = 0;
        }
        boolean any = !filterKeys.isEmpty();
        filter.setVisible(any);
        if (any) {
            filter.setText(RLM + filterLabels.get(i) + "  ▾");
            filter.setForeground(i > 0 ? AccessGate.BLUE : AccessGate.INK);
        }
    }

    private void option(String key, String label) {
        filterKeys.add(key);
        filterLabels.add(label);
    }

    // ------------------------------------------------------------------ שורות

    private JPanel stationRow(Map<String, Object> m, int i) {
        final String id = s(m, "id"), nm = s(m, "name"), seen = s(m, "lastSeen"), age = age(seen);
        JLabel name = cell(nm.isEmpty() ? "מחשב בלי שם" : nm, 14, true, AccessGate.INK);
        name.setIcon(new Dot(age.equals("week") ? AccessGate.GREEN : age.equals("old") ? AccessGate.WARN : GREY, age.equals("never")));
        name.setIconTextGap(8);
        JLabel last = cell(seen.isEmpty() ? "עוד לא פעל" : age.equals("old") ? ago(seen) + " · אולי הוחלף" : ago(seen), 13, false, age.equals("old") ? AccessGate.WARN : AccessGate.MUTED);
        final JButton b = small("שחרור", "release-station:" + id);
        b.addActionListener(e -> { flash = null; actions.releaseStation(id, nm, b); });
        return row(STATION_COLS, "station:" + id, i, name, last, b);
    }

    private JPanel studentRow(Map<String, Object> m, int i) {
        final String em = s(m, "email"), nm = s(m, "name"), last = s(m, "lastSubmitAt");
        JLabel mail = cell(nm.isEmpty() ? "" : LRM + em + LRM, 12, false, AccessGate.MUTED);
        mail.setToolTipText(em);
        final JButton b = small("הסרה", "release-student:" + em);
        b.addActionListener(e -> { flash = null; actions.releaseStudent(em, nm.isEmpty() ? em : nm, b); });
        return row(STUDENT_COLS, "student:" + em, i, cell(nm.isEmpty() ? LRM + em + LRM : nm, 14, true, AccessGate.INK), mail,
                cell(last.isEmpty() ? "עוד לא הגיש" : ago(last), 13, false, last.isEmpty() ? GREY : AccessGate.MUTED), b);
    }

    private static JPanel row(Cols cols, String id, int i, Component... cells) {
        JPanel r = new JPanel(cols);
        r.setOpaque(true);
        r.setBackground(i % 2 == 0 ? Color.WHITE : ZEBRA);
        r.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, RULE), BorderFactory.createEmptyBorder(0, 12, 0, 12)));
        r.putClientProperty("dsviz.id", id);
        for (Component c : cells) r.add(c);
        return fixed(r, ROW);
    }

    /** כותרת של כיתה בתוך רשימת התלמידים */
    private static JPanel groupRow(String name, int n) {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 5));
        r.setOpaque(true);
        r.setBackground(BAND);
        r.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        r.putClientProperty("dsviz.id", "group:" + name);
        r.add(AccessGate.text(name, 13, true, AccessGate.INK));
        r.add(AccessGate.text("  ·  " + (n == 1 ? "תלמיד אחד" : n + " תלמידים"), 12, false, AccessGate.MUTED));
        return fixed(r, GROUP);
    }

    private static JPanel noteRow(String s) {
        JPanel r = new JPanel(new BorderLayout());
        r.setOpaque(false);
        JLabel l = new JLabel(AccessGate.wrapHtml(s, 430));
        l.setFont(new Font(Font.DIALOG, Font.PLAIN, 13));
        l.setForeground(AccessGate.MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        r.add(l, BorderLayout.CENTER);
        r.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        return r;
    }

    private static JPanel fixed(JPanel r, int h) {
        r.setPreferredSize(new Dimension(100, h));
        r.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        return r;
    }

    private static JLabel cell(String s, int size, boolean bold, Color c) {
        JLabel l = AccessGate.text(s, size, bold, c);
        if (!s.isEmpty()) l.setToolTipText(RLM + s);   // שם / מייל ארוך שנחתך — מלא בריחוף
        return l;
    }

    private static JButton small(String s, String id) {
        JButton b = AccessGate.secondary(s);
        b.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AccessGate.FIELD, 1, true), BorderFactory.createEmptyBorder(3, 12, 3, 12)));
        b.putClientProperty("dsviz.id", id);
        return b;
    }

    private static JButton tab(String id) {
        JButton b = new JButton();
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.putClientProperty("dsviz.id", id);
        return b;
    }

    private static void style(JButton b, String text, boolean on) {
        b.setText(RLM + text);
        b.setFont(new Font(Font.DIALOG, on ? Font.BOLD : Font.PLAIN, 14));
        b.setForeground(on ? AccessGate.BLUE : AccessGate.MUTED);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 3, 0, on ? AccessGate.BLUE : AccessGate.LINE), BorderFactory.createEmptyBorder(6, 12, 5, 12)));
    }

    // ------------------------------------------------------------------ עזר

    private static List<Map<String, Object>> maps(List<Object> l) {
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
        if (l != null) for (Object o : l) out.add(Json.obj(o));
        return out;
    }

    private static String s(Map<String, Object> m, String k) {
        return Json.str(m.get(k)).trim();
    }

    private static String title(Map<String, Object> m) {
        return s(m, "name").isEmpty() ? s(m, "email") : s(m, "name");
    }

    /** "week" — פעל ב-7 הימים האחרונים; "old" — לא פעל מעל 30 יום; "never" — עוד לא פעל; אחרת "" */
    static String age(String iso) {
        if (iso.isEmpty()) return "never";
        long d = daysSince(iso);
        return d > 30 ? "old" : d < 7 ? "week" : "";
    }

    static long daysSince(String iso) {
        try { return (System.currentTimeMillis() - java.time.Instant.parse(iso).toEpochMilli()) / 86400000L; } catch (Exception e) { return 0; }
    }

    /** "לפני 5 דקות" / "לפני 3 שעות" / "אתמול" / "לפני 4 ימים" / "12.11.2026" */
    static String ago(String iso) {
        long t;
        try { t = java.time.Instant.parse(iso).toEpochMilli(); } catch (Exception e) { return iso; }
        long m = Math.max(0, (System.currentTimeMillis() - t) / 60000L);
        if (m < 2) return "עכשיו";
        if (m < 60) return "לפני " + m + " דקות";
        long h = m / 60;
        if (h < 24) return h == 1 ? "לפני שעה" : h == 2 ? "לפני שעתיים" : "לפני " + h + " שעות";
        long d = h / 24;
        if (d == 1) return "אתמול";
        if (d == 2) return "לפני יומיים";
        if (d < 30) return "לפני " + d + " ימים";
        return Account.dmy(iso.substring(0, Math.min(10, iso.length())));
    }

    /** השוואה "טבעית": "עמדה 2" לפני "עמדה 10" (מספרים לפי הערך שלהם, השאר לפי האלף-בית) */
    static int natural(String a, String b, Comparator<Object> text) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            boolean da = Character.isDigit(a.charAt(i)), db = Character.isDigit(b.charAt(j));
            int si = i, sj = j;
            if (da && db) {
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String x = a.substring(si, i).replaceFirst("^0+(?=.)", ""), y = b.substring(sj, j).replaceFirst("^0+(?=.)", "");
                int c = x.length() != y.length() ? x.length() - y.length() : x.compareTo(y);
                if (c != 0) return c;
            } else {
                while (i < a.length() && !Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && !Character.isDigit(b.charAt(j))) j++;
                int c = text.compare(a.substring(si, i), b.substring(sj, j));
                if (c != 0) return c;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }

    /** השורות זו מתחת לזו: כל אחת ברוחב הרשימה כולה ובגובה שלה */
    static final class Stack implements LayoutManager {
        public void addLayoutComponent(String name, Component c) {}

        public void removeLayoutComponent(Component c) {}

        public Dimension preferredLayoutSize(Container p) {
            int h = 0, w = 0;
            for (Component c : p.getComponents()) if (c.isVisible()) { Dimension d = c.getPreferredSize(); h += d.height; w = Math.max(w, d.width); }
            return new Dimension(w, h);
        }

        public Dimension minimumLayoutSize(Container p) {
            return new Dimension(0, 0);
        }

        public void layoutContainer(Container p) {
            int y = 0;
            for (Component c : p.getComponents()) if (c.isVisible()) { int h = c.getPreferredSize().height; c.setBounds(0, y, p.getWidth(), h); y += h; }
        }
    }

    /** עמודות מיושרות בכל השורות: לכל עמודה רוחב קבוע (פיקסלים) או חלק יחסי מהמקום שנשאר; כפתור — בגודל שלו, בקצה העמודה */
    static final class Cols implements LayoutManager {
        static final int GAP = 10;
        private final double[] flex;
        private final int[] fixed;

        Cols(double[] flex, int[] fixed) {
            this.flex = flex;
            this.fixed = fixed;
        }

        public void addLayoutComponent(String name, Component c) {}

        public void removeLayoutComponent(Component c) {}

        public Dimension preferredLayoutSize(Container p) {
            int w = GAP * (fixed.length - 1);
            for (int i = 0; i < fixed.length; i++) w += fixed[i] > 0 ? fixed[i] : (int) (120 * flex[i]);
            Insets in = p.getInsets();
            return new Dimension(w + in.left + in.right, ROW);
        }

        public Dimension minimumLayoutSize(Container p) {
            return new Dimension(0, ROW);
        }

        public void layoutContainer(Container p) {
            Insets in = p.getInsets();
            int n = Math.min(p.getComponentCount(), fixed.length), h = p.getHeight() - in.top - in.bottom;
            int free = p.getWidth() - in.left - in.right - GAP * (fixed.length - 1);
            double parts = 0;
            for (int i = 0; i < fixed.length; i++) { free -= fixed[i]; parts += flex[i]; }
            free = Math.max(0, free);
            boolean rtl = !p.getComponentOrientation().isLeftToRight();
            int x = rtl ? p.getWidth() - in.right : in.left;
            for (int i = 0; i < n; i++) {
                int w = fixed[i] > 0 ? fixed[i] : (int) Math.floor(free * flex[i] / parts);
                int at = rtl ? x - w : x;
                Component c = p.getComponent(i);
                if (c instanceof AbstractButton) {
                    Dimension d = c.getPreferredSize();
                    int bw = Math.min(w, d.width), bh = Math.min(h, d.height);
                    c.setBounds(rtl ? at : at + w - bw, in.top + (h - bh) / 2, bw, bh);
                } else {
                    c.setBounds(at, in.top, w, h);
                }
                x = rtl ? at - GAP : at + w + GAP;
            }
        }
    }

    /** נקודת מצב ליד שם המחשב: ירוק — פעל השבוע; אפור — לא לאחרונה; כתום — מעל 30 יום; טבעת ריקה — עוד לא פעל (גם כתוב בעמודה) */
    static final class Dot implements Icon {
        private final Color c;
        private final boolean hollow;

        Dot(Color c, boolean hollow) {
            this.c = c;
            this.hollow = hollow;
        }

        public int getIconWidth() { return 10; }

        public int getIconHeight() { return 10; }

        public void paintIcon(Component comp, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(c);
            if (hollow) { g.setStroke(new java.awt.BasicStroke(1.6f)); g.drawOval(x + 1, y + 1, 7, 7); } else g.fillOval(x, y, 9, 9);
            g.dispose();
        }
    }

    /** שדה החיפוש: הרמז ("חיפוש לפי שם…") מצויר רק כשהשדה ריק */
    static final class Search extends JTextField {
        private static final long serialVersionUID = 1L;
        String hint = "";

        Search() {
            setFont(new Font(Font.DIALOG, Font.PLAIN, 13));
            setPreferredSize(new Dimension(210, 30));
            setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AccessGate.FIELD, 1, true), BorderFactory.createEmptyBorder(2, 10, 2, 10)));
            setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            if (!getText().isEmpty() || hint.isEmpty()) return;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(getFont());
            g.setColor(GREY);
            FontMetrics fm = g.getFontMetrics();
            Insets in = getInsets();
            g.drawString(hint, getWidth() - in.right - fm.stringWidth(hint), (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    /** פס גלילה דק בלי חצים (אותו דבר בכל סביבה) */
    static final class Bar extends javax.swing.plaf.basic.BasicScrollBarUI {
        private static JButton none() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected JButton createDecreaseButton(int o) { return none(); }

        @Override
        protected JButton createIncreaseButton(int o) { return none(); }

        @Override
        protected void paintTrack(Graphics g, javax.swing.JComponent c, Rectangle r) {
            g.setColor(Color.WHITE);
            g.fillRect(r.x, r.y, r.width, r.height);
        }

        @Override
        protected void paintThumb(Graphics g0, javax.swing.JComponent c, Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(isDragging || isThumbRollover() ? GREY : AccessGate.FIELD);
            g.fillRoundRect(r.x + 3, r.y + 2, r.width - 6, r.height - 4, 6, 6);
            g.dispose();
        }
    }

    /** המכל של הרשימה בתוך הגלילה: ברוחב החלון הנגלל (השורות ממלאות את כל הרוחב), בגובה של השורות */
    static final class Track extends JPanel implements Scrollable {
        private static final long serialVersionUID = 1L;

        Track() {
            super(new BorderLayout());
            setBackground(Color.WHITE);
        }

        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }

        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return ROW; }

        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(ROW, r.height - ROW); }

        public boolean getScrollableTracksViewportWidth() { return true; }

        public boolean getScrollableTracksViewportHeight() { return false; }
    }
}

package il.lamerhav.dsviz;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;

/**
 * נוסח של שאלה מהמאגר — אותה תצוגה בחלון "מאגר תרגילים" ובחלון הצד "השאלה":
 *   כרטיס פתיחה (הטקסט + ציורים), כרטיס לכל סעיף ("סעיף א'" בולט, הטקסט, ציורים קטנים של המבנים, ובחלון הצד — "לפעולה" ו-💡),
 *   וכרטיס "כשהכול פתור נכון, main מדפיס" עם הפלט בגופן של קוד.
 * הציורים — NestedPreview (אותו ציור כמו בחלון ההרצה ובחלון "הוסף מבנה נתונים"): מחסנית פתוחה למעלה, תור כצינור עם חיצים,
 * שרשרת חוליות עם חיצים ו-null, עץ, מערך עם מקומות.
 */
final class BankQuestionPanel {
    private BankQuestionPanel() {}

    /** מה אפשר לעשות מכל סעיף (רק בחלון הצד; בחלון המאגר — null) */
    interface Actions {
        void jump(AiScaffold.Task task);

        void solution(AiScaffold.Task task);
    }

    /** בונה את הנוסח בתוך parent (GridLayout של עמודה אחת). shown — לבדיקות: מה הוצג (או null) */
    static void build(Composite parent, BankStore.Question q, Actions actions, List<String> shown) {
        AiScaffold.Exercise ex = AiScaffold.read(q.exercise);
        if (!q.intro.isEmpty() || !q.figures.isEmpty()) {
            Composite c = Look.card(parent, wide());
            if (!q.intro.isEmpty()) text(c, q.intro, 0, SWT.NORMAL, Look.TEXT, shown);
            figures(c, q.figures, shown);
        }
        for (BankStore.Section s : q.sections) {
            Composite c = Look.card(parent, wide());
            String label = s.label.replaceAll("['׳]+$", "");
            Label head = text(c, label.isEmpty() ? "סעיף" : "סעיף " + label + "'", 1, SWT.BOLD, Look.ACCENT_DARK, shown);
            ((GridData) head.getLayoutData()).verticalIndent = 2;
            text(c, s.text, 0, SWT.NORMAL, Look.TEXT, shown);
            figures(c, s.figures, shown);
            final AiScaffold.Task task = taskOf(ex, s.label);
            if (actions != null && task != null) {
                Composite links = new Composite(c, SWT.RIGHT_TO_LEFT);
                RowLayout ll = new RowLayout(SWT.HORIZONTAL);
                ll.wrap = true;   // חלון צר — הכפתור השני יורד לשורה הבאה (ולא נחתך)
                ll.marginLeft = ll.marginRight = 0;
                ll.marginTop = 6;
                ll.marginBottom = 2;
                ll.spacing = 8;
                links.setLayout(ll);
                links.setLayoutData(wide());
                new Look.Round(links, "לפעולה " + task.methodName, false, () -> actions.jump(task));
                new Look.Round(links, "💡 הצעה לפתרון", false, () -> actions.solution(task));
                if (shown != null) shown.add("SECTION-BUTTONS " + label + " " + task.methodName);
            }
        }
        String expected = q.expectedOutput();
        if (!expected.isEmpty()) {
            Composite c = Look.card(parent, wide());
            text(c, "כשהכול פתור נכון, main מדפיס:", 0, SWT.BOLD, Look.TEXT, shown);
            Composite box = Look.panel(c, Look.SURFACE, Look.BORDER, 6, 10, 8);
            ((Composite) box.getParent()).setLayoutData(wide());
            for (String line : expected.split("\n")) {
                Label l = new Label(box, SWT.WRAP | SWT.RIGHT_TO_LEFT);
                // פלט של תוכנית: כל השורה בכיוון עברי אחד (כמו בהערה מעל main) — בלי לפרק את [3,8,1,6] למספרים נפרדים
                l.setText(line.isEmpty() ? " " : "\u200F\u202B" + line + "\u202C");
                l.setFont(Look.mono(parent.getDisplay(), 0));
                l.setForeground(Look.color(parent.getDisplay(), Look.INK2));
                l.setLayoutData(fill());
            }
            if (shown != null) shown.add("EXPECTED " + expected.replace("\n", " | "));
            text(c, "מריצים את main (\"הרצה ויזואלית\") ובודקים שזה מה שמודפס.", -1, SWT.NORMAL, Look.MUTED, shown);
        }
    }

    /** כותרת השאלה: נקודה בצבע הקושי, השם, ומתחת — הנושא, הרמה (ו-"✓ עשיתי") */
    static void titleRow(Composite parent, BankStore.Question q, String topicTitle, List<String> shown) {
        Display d = parent.getDisplay();
        Composite head = new Composite(parent, SWT.RIGHT_TO_LEFT);
        GridLayout hl = new GridLayout(2, false);
        hl.marginWidth = 0;
        hl.marginHeight = 0;
        hl.horizontalSpacing = 8;
        head.setLayout(hl);
        head.setLayoutData(wide());
        org.eclipse.swt.widgets.Canvas dot = new org.eclipse.swt.widgets.Canvas(head, SWT.NO_FOCUS | SWT.DOUBLE_BUFFERED);
        GridData dg = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
        dg.widthHint = 14;
        dg.heightHint = 14;
        dot.setLayoutData(dg);
        final int color = q.level.color;
        dot.addListener(SWT.Paint, e -> {
            e.gc.setAntialias(SWT.ON);
            e.gc.setBackground(Look.color(d, color));
            e.gc.fillOval(1, 1, 12, 12);
        });
        text(head, q.title, 2, SWT.BOLD, Look.TEXT, shown);
        boolean done = BankProgress.get().done(q.id);
        text(parent, (topicTitle == null ? "" : topicTitle + " · ") + "רמה: " + q.level.title + (done ? "   ✓ עשיתי" : ""), -1, SWT.BOLD,
                done ? Look.OK : q.level.color, shown);
    }

    /** שורה של ציורים קטנים (יורדת שורה כשאין מקום), ומתחת לכל ציור — הכיתוב שלו */
    private static void figures(Composite parent, List<BankStore.Figure> figs, List<String> shown) {
        if (figs.isEmpty()) return;
        Display d = parent.getDisplay();
        Composite row = new Composite(parent, SWT.RIGHT_TO_LEFT);
        RowLayout rl = new RowLayout(SWT.HORIZONTAL);
        rl.wrap = true;
        rl.marginLeft = rl.marginRight = 0;
        rl.marginTop = 6;
        rl.spacing = 18;
        row.setLayout(rl);
        GridData rg = wide();
        rg.widthHint = 120;
        row.setLayoutData(rg);
        for (BankStore.Figure f : figs) {
            Composite one = new Composite(row, SWT.NONE);
            GridLayout ol = new GridLayout(1, false);
            ol.marginWidth = 0;
            ol.marginHeight = 0;
            ol.verticalSpacing = 2;
            one.setLayout(ol);
            NestedPreview p = new NestedPreview(one);
            p.setBackground(Look.color(d, Look.CARD));
            p.set(f.type, f.inner, f.groups);
            Point size = p.computeSize(SWT.DEFAULT, SWT.DEFAULT);
            int w = Math.min(size.x, 300);   // "בקטנה" — ציור רחב מוקטן
            p.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, false, 1, 1));
            ((GridData) p.getLayoutData()).widthHint = w;
            ((GridData) p.getLayoutData()).heightHint = p.computeSize(w, SWT.DEFAULT).y;
            if (!f.caption.isEmpty()) {
                Label cap = new Label(one, SWT.CENTER | SWT.RIGHT_TO_LEFT);
                cap.setText(caption(f.caption));
                cap.setFont(Look.font(d, -1, SWT.NORMAL));
                cap.setForeground(Look.color(d, Look.MUTED));
                cap.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false));
            }
            one.setLayoutData(new RowData());
            if (shown != null) shown.add("FIGURE " + f.type + (f.inner.equals("cell") ? "" : "/" + f.inner) + " " + f.groups + (f.caption.isEmpty() ? "" : " \"" + f.caption + "\""));
        }
    }

    /**
     * בחלון צד של Eclipse ערכת הנושא (CSS) צובעת מחדש את הרקע של כל רכיב — והכרטיסים הלבנים נעשים אפורים.
     * מחזירים את הצבעים שלנו: כרטיס (Look.card) — לבן, תיבה (Look.panel) — הצבע שלה, וכל השאר — כמו ההורה.
     */
    static void restoreColors(Control c, int bg) {
        if (c == null || c.isDisposed()) return;
        int inner = bg;
        Object panel = c.getData("dsviz.panel");
        if (panel instanceof int[]) inner = ((int[]) panel)[0];
        else if (c instanceof Composite && (c.getStyle() & SWT.NO_BACKGROUND) != 0) inner = Look.CARD;   // המסגרת של Look.card
        c.setBackground(Look.color(c.getDisplay(), bg));
        if (c instanceof Composite) for (Control k : ((Composite) c).getChildren()) restoreColors(k, inner);
        c.redraw();
    }

    /**
     * כיתוב של ציור ("המחסנית s (4 בראש)", "לפני: removeAll(s, 3)"): כל השורה מימין לשמאל, וכל קטע באנגלית
     * (שם, או קריאה לפעולה עם הסוגריים שלה) — יחידה אחת משמאל לימין, כדי שהסוגריים והפסיקים לא יתערבבו עם העברית
     */
    static String caption(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*(\\([^()]*\\))?").matcher(s);
        StringBuilder b = new StringBuilder("\u200F\u202B");
        int last = 0;
        while (m.find()) {
            b.append(s, last, m.start()).append("\u202A").append(m.group()).append("\u202C");
            last = m.end();
        }
        return b.append(s.substring(last)).append("\u202C").toString();
    }

    static Label text(Composite parent, String s, int delta, int style, int rgb, List<String> shown) {
        Label l = new Label(parent, SWT.WRAP | SWT.RIGHT_TO_LEFT);
        l.setText(RtlText.rtl(s));
        l.setFont(Look.font(parent.getDisplay(), delta, style));
        l.setForeground(Look.color(parent.getDisplay(), rgb));
        l.setLayoutData(fill());
        if (shown != null) shown.add("TEXT " + s.replace("\n", " | "));
        return l;
    }

    static GridData wide() {
        return new GridData(SWT.FILL, SWT.TOP, true, false);
    }

    /** רוחב מינימלי קטן — כדי שהשורות יישברו לפי רוחב החלון ולא יגדילו אותו */
    static GridData fill() {
        GridData g = new GridData(SWT.FILL, SWT.TOP, true, false);
        g.widthHint = 120;
        return g;
    }

    static AiScaffold.Task taskOf(AiScaffold.Exercise ex, String label) {
        String l = label.replaceAll("[\\s'׳.]", "");
        for (AiScaffold.Task t : ex.tasks) if (t.label.replaceAll("[\\s'׳.]", "").equals(l)) return t;
        return null;
    }
}

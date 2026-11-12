package il.lamerhav.dsviz;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Layout;

/**
 * נוסח של שאלה מהמאגר — אותה תצוגה בחלון "מאגר תרגילים" ובחלון הצד "השאלה":
 *   כרטיס פתיחה (הטקסט + ציורים), כרטיס לכל סעיף ("סעיף א'" בולט, הטקסט, ציורים קטנים של המבנים, ובחלון הצד — "לפעולה" ו-💡),
 *   וכרטיס "כשהכול פתור נכון, התוכנית מדפיסה" עם הפלט כמו בקונסול.
 * הציורים — NestedPreview (אותו ציור כמו בחלון ההרצה ובחלון "הוסף מבנה נתונים"): מחסנית פתוחה למעלה, תור כצינור עם חיצים,
 * שרשרת חוליות עם חיצים ו-null, עץ, מערך עם מקומות.
 */
final class BankQuestionPanel {
    private BankQuestionPanel() {}

    /** ציור רחב מזה (עץ גדול, שרשרת ארוכה) — לבדיקות: "FIGURE-FIT" (מוצג כולו, ומוקטן לרוחב השורה רק אם היא צרה ממנו) */
    static final int WIDE_FIGURE = 380;

    /** מה אפשר לעשות מכל סעיף (רק בחלון הצד; בחלון המאגר — null) */
    interface Actions {
        void jump(AiScaffold.Task task);

        void solution(AiScaffold.Task task);

        /** כמה סעיפים כבר בקוד (התרגיל נבנה סעיף אחרי סעיף — BankSections). בלי תרגיל — כולם */
        default int prepared() {
            return Integer.MAX_VALUE;
        }

        /** "הכן סעיף ב": הפעולה של הסעיף והבדיקות שלו נכנסות לקוד */
        default void prepare(AiScaffold.Task task) {}
    }

    /** בונה את הנוסח בתוך parent (GridLayout של עמודה אחת). shown — לבדיקות: מה הוצג (או null) */
    static void build(Composite parent, BankStore.Question q, Actions actions, List<String> shown) {
        AiScaffold.Exercise ex = AiScaffold.read(q.exercise);
        if (!q.intro.isEmpty() || !q.figures.isEmpty()) {
            Composite c = Look.card(parent, wide());
            if (!q.intro.isEmpty()) text(c, q.intro, 0, SWT.NORMAL, Look.TEXT, shown);
            figures(c, q.figures, shown);
        }
        int ready = actions == null ? Integer.MAX_VALUE : actions.prepared();
        int index = 0;
        for (BankStore.Section s : q.sections) {
            final int at = index++;
            Composite c = Look.card(parent, wide());
            String label = s.label.replaceAll("['׳]+$", "");
            Label head = text(c, label.isEmpty() ? "סעיף" : "סעיף " + label + "'", 1, SWT.BOLD, Look.ACCENT_DARK, shown);
            ((GridData) head.getLayoutData()).verticalIndent = 2;
            text(c, s.text, 0, SWT.NORMAL, Look.TEXT, shown);
            figures(c, s.figures, shown);
            final AiScaffold.Task task = taskOf(ex, s.label);
            if (actions != null && task != null && at >= ready) {
                // הסעיף עוד לא בקוד: "הכן סעיף ב" (רק לסעיף הבא בתור) — הפעולה והבדיקות שלו נכנסות למקום הנכון בקוד
                if (at == ready) {
                    Composite links = new Composite(c, SWT.RIGHT_TO_LEFT);
                    RowLayout ll = new RowLayout(SWT.HORIZONTAL);
                    ll.marginLeft = ll.marginRight = 0;
                    ll.marginTop = 6;
                    ll.marginBottom = 2;
                    links.setLayout(ll);
                    links.setLayoutData(wide());
                    Look.Round prep = new Look.Round(links, "הכן סעיף " + label + "'", true, () -> actions.prepare(task));
                    prep.setToolTipText("מוסיף לקוד את הפעולה " + task.methodName + " ואת הבדיקות של הסעיף ב-main");
                    prep.setData("dsviz.id", "prepare-" + label);
                    text(c, "הפעולה של הסעיף והבדיקות שלו ב-main ייכנסו לקוד במקום המתאים — לא משנה איפה הסמן.", -1, SWT.NORMAL, Look.MUTED, shown);
                    if (shown != null) shown.add("PREPARE-BUTTON " + label + " " + task.methodName);
                } else text(c, "אחרי סעיף " + q.sections.get(at - 1).label.replaceAll("['׳]+$", "") + "'", -1, SWT.NORMAL, Look.MUTED, shown);
            } else if (actions != null && task != null) {
                Composite links = new Composite(c, SWT.RIGHT_TO_LEFT);
                RowLayout ll = new RowLayout(SWT.HORIZONTAL);
                ll.wrap = true;   // חלון צר — הכפתור השני יורד לשורה הבאה (ולא נחתך)
                ll.marginLeft = ll.marginRight = 0;
                ll.marginTop = 6;
                ll.marginBottom = 2;
                ll.spacing = 8;
                links.setLayout(ll);
                links.setLayoutData(wide());
                Look.Round go = new Look.Round(links, label.isEmpty() ? "לפעולה" : "לפעולה של סעיף " + label, false, () -> actions.jump(task));
                go.setToolTipText("פותח את הקובץ, והסמן בתחילת הפעולה " + task.methodName);
                Look.Round hint = new Look.Round(links, "הצעה לפתרון", false, () -> actions.solution(task));
                hint.setIcon(SolutionMinings.icon(links.getDisplay()));   // המנורה המצוירת — כמו בשורה שמעל הפעולה בעורך (אמוג'י יוצא ריבוע ריק)
                if (shown != null) shown.add("SECTION-BUTTONS " + label + " " + task.methodName);
            }
        }
        String expected = q.expectedOutput();
        boolean partial = ready < q.sections.size();
        if (partial) expected = BankSections.expectedUpTo(expected, BankSections.labels(q.exercise), ready);   // רק הסעיפים שכבר בקוד
        if (!expected.isEmpty()) {
            Composite c = Look.card(parent, wide());
            text(c, partial ? "כשהסעיפים שכבר בקוד פתורים נכון, התוכנית מדפיסה:" : "כשהכול פתור נכון, התוכנית מדפיסה:", 0, SWT.BOLD, Look.TEXT, shown);
            Composite box = Look.panel(c, Look.SURFACE, Look.BORDER, 6, 10, 8);
            ((Composite) box.getParent()).setLayoutData(wide());
            // בדיוק כמו בקונסול של Eclipse (שם התלמיד יראה את הפלט): כל שורה משמאל לימין, בגופן רגיל — המספרים באותו סדר
            Composite lines = new Composite(box, SWT.LEFT_TO_RIGHT);
            GridLayout ll = new GridLayout(1, false);
            ll.marginWidth = 0;
            ll.marginHeight = 0;
            ll.verticalSpacing = 2;
            lines.setLayout(ll);
            lines.setLayoutData(fill());
            for (String line : expected.split("\n")) {
                Label l = new Label(lines, SWT.WRAP | SWT.LEFT_TO_RIGHT);
                l.setText(line.isEmpty() ? " " : line);
                l.setFont(Look.font(parent.getDisplay(), 0, SWT.NORMAL));
                l.setForeground(Look.color(parent.getDisplay(), Look.INK2));
                l.setLayoutData(fill());
            }
            if (shown != null) shown.add("EXPECTED " + expected.replace("\n", " | "));
            text(c, partial ? "מריצים את התוכנית בכפתור \"הרצה ויזואלית\", ובודקים שזה מה שמודפס. כשמכינים את הסעיף הבא — מתווספות כאן גם השורות שלו."
                    : "מריצים את התוכנית בכפתור \"הרצה ויזואלית\", ובודקים שזה מה שמודפס.", -1, SWT.NORMAL, Look.MUTED, shown);
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

    /**
     * שורה של ציורים קטנים (יורדת שורה כשאין מקום), ומתחת לכל ציור — הכיתוב שלו. ציור שרחב מהשורה (עץ גדול, שרשרת ארוכה, חלון צר) —
     * מוקטן לרוחב שלה (FigureRow), כך שרואים תמיד את כולו, בלי גלילה הצידה
     */
    private static void figures(Composite parent, List<BankStore.Figure> figs, List<String> shown) {
        if (figs.isEmpty()) return;
        Display d = parent.getDisplay();
        Composite row = new Composite(parent, SWT.RIGHT_TO_LEFT);
        row.setLayout(new FigureRow());
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
            // גודל טבעי של הציור (NestedPreview מקטין לרוחב 330 — מחשבים את הרוחב האמיתי מהיחס בין הגבהים)
            NestedPreview probe = new NestedPreview(one);
            probe.set(f.type, f.inner, f.groups);
            Point small = probe.computeSize(SWT.DEFAULT, SWT.DEFAULT);
            int fullH = probe.computeSize(20000, SWT.DEFAULT).y, smallH = Math.max(1, small.y);
            int naturalW = fullH > smallH + 1 ? (int) Math.ceil(small.x * (double) fullH / smallH) : small.x;
            probe.dispose();
            // בגודל המלא שלו; אם השורה צרה ממנו — FigureRow מקטין אותו לרוחב השורה (NestedPreview מקטין את הציור לרוחב שניתן לו)
            NestedPreview p = new NestedPreview(one);
            p.setBackground(Look.color(d, Look.CARD));
            p.set(f.type, f.inner, f.groups);
            p.setData(FigureRow.NATURAL, naturalW);
            GridData pg = new GridData(SWT.CENTER, SWT.TOP, false, false);
            pg.widthHint = naturalW;
            pg.heightHint = p.computeSize(naturalW, SWT.DEFAULT).y;
            p.setLayoutData(pg);
            if (shown != null && naturalW > WIDE_FIGURE) shown.add("FIGURE-FIT " + naturalW);
            if (!f.caption.isEmpty()) {
                Label cap = new Label(one, SWT.CENTER | SWT.RIGHT_TO_LEFT);
                cap.setText(caption(f.caption));
                cap.setFont(Look.font(d, -1, SWT.NORMAL));
                cap.setForeground(Look.color(d, Look.MUTED));
                cap.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, true, false));
            }
            if (shown != null) shown.add("FIGURE " + f.type + (f.inner.equals("cell") ? "" : "/" + f.inner) + " " + f.groups + (f.caption.isEmpty() ? "" : " \"" + f.caption + "\""));
        }
    }

    /**
     * הפריסה של שורת הציורים: כמו RowLayout שהייתה כאן (מימין לשמאל, רווח 18, יורדת שורה כשאין מקום — אותם מקומות בדיוק), ועוד דבר אחד:
     * ציור שלבדו רחב מהשורה מוקטן לרוחב שלה, באותו יחס. GridLayout של הכרטיס שואל את הגובה לפי הרוחב האמיתי (כמו בטקסט שיורד שורה),
     * אז כשהחלון משנה רוחב — הציורים יורדים שורה / חוזרים, ומוקטנים / חוזרים לגודל המלא.
     */
    static final class FigureRow extends Layout {
        /** הרוחב הטבעי של הציור (על ה-NestedPreview) */
        static final String NATURAL = "dsviz.naturalWidth";
        static final int SPACING = 18, TOP = 6, BOTTOM = 3;

        @Override
        protected Point computeSize(Composite c, int wHint, int hHint, boolean flush) {
            Point p = place(c, wHint == SWT.DEFAULT ? Integer.MAX_VALUE : wHint, false);
            if (wHint != SWT.DEFAULT) p.x = wHint;
            if (hHint != SWT.DEFAULT) p.y = hHint;
            return p;
        }

        @Override
        protected void layout(Composite c, boolean flush) {
            place(c, c.getClientArea().width, true);
        }

        private static Point place(Composite c, int width, boolean move) {
            int x = 0, y = TOP, rowH = 0, maxX = 0;
            Control[] kids = c.getChildren();
            for (int i = 0; i < kids.length; i++) {
                Point s = fit(kids[i], width);
                if (i > 0 && x + s.x > width) {   // לא נכנס — לשורה הבאה
                    x = 0;
                    y += rowH + SPACING;
                    rowH = 0;
                }
                if (move) {
                    kids[i].setBounds(x, y, s.x, s.y);
                    if (kids[i] instanceof Composite) ((Composite) kids[i]).layout(true);
                }
                x += s.x + SPACING;
                maxX = Math.max(maxX, x);
                rowH = Math.max(rowH, s.y);
            }
            return new Point(Math.max(0, maxX - SPACING), y + rowH + BOTTOM);
        }

        /** הגודל של ציור אחד עם הכיתוב שלו, כשיש width: בגודל הטבעי, או מוקטן לרוחב הזה */
        private static Point fit(Control one, int width) {
            if (one instanceof Composite) {
                for (Control k : ((Composite) one).getChildren()) {
                    Object natural = k.getData(NATURAL);
                    if (!(natural instanceof Integer) || !(k.getLayoutData() instanceof GridData)) continue;
                    int w = Math.max(1, Math.min((Integer) natural, width));
                    GridData g = (GridData) k.getLayoutData();
                    if (g.widthHint != w) {
                        g.widthHint = w;
                        g.heightHint = k.computeSize(w, SWT.DEFAULT).y;
                    }
                }
            }
            Point s = one.computeSize(SWT.DEFAULT, SWT.DEFAULT, true);
            return new Point(Math.min(s.x, Math.max(1, width)), s.y);   // כיתוב ארוך מהשורה — לא מרחיב אותה
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
            // RLM אחרי הקטע באנגלית: בלעדיו מספר שבא אחריו ("s (4 בראש)") נדבק לאנגלית ועובר לצד השני
            b.append(s, last, m.start()).append("\u202A").append(m.group()).append("\u202C\u200F");
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

package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

/**
 * כל ההודעות של התוסף — חלון אחד במראה של האתר (site/DESIGN.md): משטח לבן, קו של 1px, פינות קטנות, מימין לשמאל.
 * כותרת קצרה עם סמל קטן בצבע (מידע / אזהרה / שגיאה / שאלה), טקסט קצר, ולפעמים רשימת כרטיסים (שגיאה בכל כרטיס,
 * או אפשרויות לבחירה); כפתור ראשי כחול וכפתור משני שקט. במקומם של MessageDialog / ErrorDialog / ElementListSelectionDialog,
 * שמציגים את העברית משמאל לימין ונראים ישנים. כל טקסט עובר דרך RtlText: קוד ושמות קבצים משמאל לימין כיחידה אחת.
 * tools/test/run-tests.sh בודק שאין בתוסף שימוש ישיר בחלונות ההודעה של JFace מחוץ לקובץ הזה.
 */
final class Say {
    enum Kind { INFO, WARN, ERROR, QUESTION, DONE }

    /** כרטיס: קישור (למשל "Button.java · שורה 42"), משפט בעברית, וקטע קוד בשורה משלו. כל חלק יכול להיות null */
    static final class Card {
        final String link, text, code;
        final Runnable onLink;

        Card(String link, Runnable onLink, String text, String code) {
            this.link = link;
            this.onLink = onLink;
            this.text = text;
            this.code = code;
        }
    }

    /** מזהה לחלונות של Say (לבדיקות: shell.getData(ID) — סוג ההודעה) */
    static final String ID = "dsviz.say";

    private final Shell parent;
    private final Kind kind;
    private final String title;
    private String text, more, heading;
    private final List<Card> cards = new ArrayList<Card>();
    private List<String> choices;
    private String primary = "אישור", secondary;
    private Runnable onPrimary;
    private int width = 460;

    Say(Shell parent, Kind kind, String title) {
        this.parent = parent;
        this.kind = kind;
        this.title = title;
    }

    /** הטקסט (שורות ריקות מפרידות בין פסקאות) */
    Say text(String t) {
        text = t;
        return this;
    }

    Say card(String link, Runnable onLink, String sentence, String code) {
        cards.add(new Card(link, onLink, sentence, code));
        return this;
    }

    /** הכותרת בתוך החלון (אחרת — מהכותרת של החלון) */
    Say heading(String h) {
        heading = h;
        return this;
    }

    /** שורה קטנה אחרי הכרטיסים ("ועוד 3 — בחלון Problems") */
    Say more(String t) {
        more = t;
        return this;
    }

    /** הכפתורים: ראשי (כחול) ומשני (שקט, או null) */
    Say buttons(String main, String other) {
        primary = main;
        secondary = other;
        return this;
    }

    /** מה עושים בלחיצה על הכפתור הראשי (לפני שהחלון נסגר) */
    Say onPrimary(Runnable r) {
        onPrimary = r;
        return this;
    }

    Say width(int w) {
        width = w;
        return this;
    }

    /** 0 — הכפתור הראשי, 1 — המשני, -1 — נסגר (Esc / ✕). ב-choose: מספר האפשרות שנבחרה */
    int open() {
        if (Display.getCurrent() == null) {   // נקרא מחוט רקע — מציגים בחוט של הממשק
            final int[] r = { -1 };
            Display.getDefault().syncExec(() -> r[0] = open());
            return r[0];
        }
        Shell p = parent != null && !parent.isDisposed() ? parent : Display.getDefault().getActiveShell();
        Box b = new Box(p);
        b.open();
        return b.result;
    }

    // ------------------------------------------------------------------ קיצורים

    static void info(Shell s, String title, String text) {
        new Say(s, Kind.INFO, title).text(text).open();
    }

    static void warn(Shell s, String title, String text) {
        new Say(s, Kind.WARN, title).text(text).open();
    }

    static void error(Shell s, String title, String text) {
        new Say(s, Kind.ERROR, title).text(text).buttons("סגירה", null).open();
    }

    /** שאלה עם שני כפתורים: true — הראשי */
    static boolean ask(Shell s, String title, String text, String yes, String no) {
        return new Say(s, Kind.QUESTION, title).text(text).buttons(yes, no).open() == 0;
    }

    /** בחירה מרשימה (כל אפשרות — כרטיס שלוחצים עליו). מחזיר את המספר שלה, או -1 */
    static int choose(Shell s, String title, String text, List<String> options) {
        Say say = new Say(s, Kind.QUESTION, title).text(text).buttons(null, "ביטול");
        say.choices = options;
        int r = say.open();
        return r >= 100 ? r - 100 : -1;
    }

    // ------------------------------------------------------------------ החלון

    private final class Box extends Dialog {
        int result = -1;

        Box(Shell p) {
            super(p);
            setShellStyle(SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL | SWT.RIGHT_TO_LEFT);
        }

        @Override
        protected void configureShell(Shell shell) {
            super.configureShell(shell);
            shell.setText(title);
            shell.setData(ID, kind.name());
            shell.setBackground(Look.color(shell.getDisplay(), Look.CARD));
            shell.setBackgroundMode(SWT.INHERIT_DEFAULT);
            shell.addListener(SWT.Traverse, e -> {
                if (e.detail == SWT.TRAVERSE_RETURN && primary != null) {
                    e.doit = false;
                    finish(0);
                }
            });
        }

        @Override
        protected Control createDialogArea(Composite parent) {
            Display d = parent.getDisplay();
            Composite area = new Composite(parent, SWT.NONE);
            area.setBackground(Look.color(d, Look.CARD));
            GridLayout al = new GridLayout(1, false);
            al.marginWidth = 20;
            al.marginTop = 16;
            al.marginBottom = 14;
            al.verticalSpacing = 10;
            area.setLayout(al);
            area.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

            Composite head = new Composite(area, SWT.NONE);
            GridLayout hl = new GridLayout(2, false);
            hl.marginWidth = 0;
            hl.marginHeight = 0;
            hl.horizontalSpacing = 10;
            head.setLayout(hl);
            head.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            Canvas icon = new Canvas(head, SWT.NO_FOCUS | SWT.LEFT_TO_RIGHT | SWT.DOUBLE_BUFFERED);
            GridData ig = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
            ig.widthHint = 22;
            ig.heightHint = 22;
            icon.setLayoutData(ig);
            icon.addListener(SWT.Paint, e -> icon(e.gc, d, kind, 22));
            Label t = new Label(head, SWT.WRAP);
            t.setText(RtlText.rtl(shortTitle()));
            t.setFont(Look.font(d, 2, SWT.BOLD));
            t.setForeground(Look.color(d, Look.TEXT));
            GridData tg = new GridData(SWT.FILL, SWT.CENTER, true, false);
            tg.widthHint = width - 32;
            t.setLayoutData(tg);

            if (text != null && !text.trim().isEmpty()) {
                Label l = new Label(area, SWT.WRAP);
                l.setText(RtlText.rtl(text.trim()));
                l.setFont(Look.font(d, 0, SWT.NORMAL));
                l.setForeground(Look.color(d, Look.INK2));
                GridData lg = new GridData(SWT.FILL, SWT.TOP, true, false);
                lg.widthHint = width;
                l.setLayoutData(lg);
            }
            for (Card c : cards) card(area, c);
            if (choices != null) for (int i = 0; i < choices.size(); i++) choice(area, choices.get(i), 100 + i);
            if (more != null) {
                Label m = new Label(area, SWT.WRAP);
                m.setText(RtlText.rtl(more));
                m.setFont(Look.font(d, -1, SWT.NORMAL));
                m.setForeground(Look.color(d, Look.MUTED));
                GridData mg = new GridData(SWT.FILL, SWT.TOP, true, false);
                mg.widthHint = width;
                m.setLayoutData(mg);
            }
            return area;
        }

        /** הכותרת בחלון: מה שאחרי " — " בכותרת של החלון ("הרצה ויזואלית — יש שגיאות בקוד" ← "יש שגיאות בקוד") */
        private String shortTitle() {
            if (heading != null) return heading;
            int dash = title.indexOf(" — ");
            return dash > 0 && dash + 3 < title.length() ? title.substring(dash + 3) : title;
        }

        private void card(Composite area, final Card c) {
            Display d = area.getDisplay();
            Composite body = Look.panel(area, Look.SURFACE, Look.BORDER, 6, 12, 8);
            body.getParent().setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            if (c.link != null) {
                Label l = new Label(body, SWT.NONE);
                l.setText(RtlText.rtl(c.link));
                l.setFont(Look.font(d, -1, SWT.BOLD));
                l.setForeground(Look.color(d, c.onLink != null ? Look.ACCENT_DARK : Look.MUTED));
                if (c.onLink != null) {
                    l.setCursor(d.getSystemCursor(SWT.CURSOR_HAND));
                    l.addListener(SWT.MouseUp, e -> {
                        c.onLink.run();
                        finish(-1);
                    });
                }
            }
            if (c.text != null && !c.text.isEmpty()) {
                Label s = new Label(body, SWT.WRAP);
                s.setText(RtlText.rtl(c.text));
                s.setForeground(Look.color(d, Look.TEXT));
                GridData sg = new GridData(SWT.FILL, SWT.TOP, true, false);
                sg.widthHint = width - 26;
                s.setLayoutData(sg);
            }
            if (c.code != null && !c.code.trim().isEmpty()) {
                Composite box = Look.panel(body, Look.CARD, Look.BORDER, 4, 8, 4);
                box.getParent().setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
                Label code = new Label(box, SWT.LEFT_TO_RIGHT);
                String k = RtlText.strip(c.code).trim().replace("\t", "    ");
                if (k.length() > 90) k = k.substring(0, 88) + "…";
                code.setText(k);
                code.setFont(Look.mono(d, -1));
                code.setForeground(Look.color(d, Look.TEXT));
                code.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
            }
        }

        private void choice(Composite area, String option, final int code) {
            Display d = area.getDisplay();
            final Composite body = Look.panel(area, Look.CARD, Look.BORDER, 6, 12, 8);
            body.getParent().setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            Label l = new Label(body, SWT.WRAP);
            l.setText(RtlText.rtl(option));
            l.setForeground(Look.color(d, Look.TEXT));
            GridData lg = new GridData(SWT.FILL, SWT.TOP, true, false);
            lg.widthHint = width - 26;
            l.setLayoutData(lg);
            Listener enter = e -> Look.repaintPanel(body, 0xF3F7FF, Look.ACCENT);
            Listener exit = e -> Look.repaintPanel(body, Look.CARD, Look.BORDER);
            Listener up = e -> finish(code);
            for (Control x : new Control[] { body.getParent(), body, l }) {
                x.setCursor(d.getSystemCursor(SWT.CURSOR_HAND));
                x.addListener(SWT.MouseEnter, enter);
                x.addListener(SWT.MouseExit, exit);
                x.addListener(SWT.MouseUp, up);
            }
        }

        @Override
        protected Control createButtonBar(Composite parent) {
            final Display d = parent.getDisplay();
            final Composite bar = new Composite(parent, SWT.NONE);
            bar.setBackground(Look.color(d, Look.SURFACE));
            GridLayout l = new GridLayout(2, false);
            l.marginWidth = 20;
            l.marginHeight = 12;
            l.horizontalSpacing = 8;
            bar.setLayout(l);
            bar.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
            bar.addListener(SWT.Paint, e -> {
                e.gc.setForeground(Look.color(d, Look.BORDER));
                e.gc.drawLine(0, 0, bar.getSize().x, 0);
            });
            // מימין לשמאל: הראשון מימין — הכפתור הראשי, ולידו המשני
            if (primary != null) new Look.Round(bar, primary, true, () -> {
                if (onPrimary != null) onPrimary.run();
                finish(0);
            }).setData(ID, "primary");
            if (secondary != null) new Look.Round(bar, secondary, false, () -> finish(1)).setData(ID, "secondary");
            return bar;
        }

        @Override
        protected Point getInitialSize() {
            Point p = getShell().computeSize(SWT.DEFAULT, SWT.DEFAULT, true);
            org.eclipse.swt.graphics.Rectangle screen = getShell().getMonitor().getClientArea();
            return new Point(Math.min(Math.max(p.x, 360), screen.width - 40), Math.min(p.y, screen.height - 60));
        }

        private void finish(int r) {
            result = r;
            close();
        }
    }

    /** הסמל הקטן ליד הכותרת: עיגול בצבע של סוג ההודעה, וסימן לבן (i / ! / ✕ / ? / ✓) */
    static void icon(GC gc, Display d, Kind k, int size) {
        int c = k == Kind.ERROR ? Look.ERR : k == Kind.WARN ? Look.AMBER : k == Kind.DONE ? Look.OK : Look.ACCENT;
        gc.setAntialias(SWT.ON);
        gc.setBackground(Look.color(d, c));
        gc.fillOval(0, 0, size - 1, size - 1);
        gc.setForeground(d.getSystemColor(SWT.COLOR_WHITE));
        gc.setBackground(d.getSystemColor(SWT.COLOR_WHITE));
        gc.setLineWidth(Math.max(2, size / 10));
        gc.setLineCap(SWT.CAP_ROUND);
        int m = size / 2, q = size / 4;
        switch (k) {
            case ERROR:
                gc.drawLine(m - q + 2, m - q + 2, m + q - 2, m + q - 2);
                gc.drawLine(m + q - 2, m - q + 2, m - q + 2, m + q - 2);
                break;
            case WARN:
                gc.drawLine(m, q, m, m + 2);
                gc.fillOval(m - 1, size - q - 2, 3, 3);
                break;
            case DONE:
                gc.drawPolyline(new int[] { m - q, m, m - 2, m + q - 2, m + q, m - q + 1 });
                break;
            case QUESTION: {
                gc.setFont(Look.font(d, 1, SWT.BOLD));
                Point e = gc.textExtent("?");
                gc.drawString("?", (size - e.x) / 2, (size - e.y) / 2, true);
                break;
            }
            default:
                gc.fillOval(m - 1, q - 1, 3, 3);
                gc.drawLine(m, m - 2, m, size - q);
        }
        gc.setLineWidth(1);
    }
}

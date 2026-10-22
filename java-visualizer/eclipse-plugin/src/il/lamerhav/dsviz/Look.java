package il.lamerhav.dsviz;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;

/**
 * המראה של החלונות "תרגיל חדש" ו"הוספה לקוד": כרטיסים לבנים מעוגלים על רקע בהיר, כפתורי-בחירה מעוגלים (כחול בהיר בבחירה),
 * וכפתורים ראשי / משני — אותו מראה בדיוק כמו החלונות של C# (csharp/new-exercise, CsTool) ושל חלון ההרצה (Theme).
 */
final class Look {
    private Look() {}

    private static final String KEEP = "dsviz.keepFont";

    static final int BG = 0xF1F3F7, CARD = 0xFFFFFF, BORDER = 0xDFE3EA, TEXT = 0x1D2433, MUTED = 0x667085,
            ACCENT = 0x2F6FED, ACCENT_DARK = 0x2457C5, ACCENT_SOFT = 0xE8F0FE;

    @SuppressWarnings("unchecked")
    static Color color(Display d, int rgb) {
        Map<Integer, Color> m = (Map<Integer, Color>) d.getData("dsviz.colors");
        if (m == null) {
            final Map<Integer, Color> created = new HashMap<Integer, Color>();
            m = created;
            d.setData("dsviz.colors", m);
            d.disposeExec(() -> { for (Color c : created.values()) c.dispose(); });
        }
        Color c = m.get(rgb);
        if (c == null) {
            c = new Color(d, (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            m.put(rgb, c);
        }
        return c;
    }

    /** גופן הדיאלוג, מוגדל ב-delta נקודות (ובסגנון style) */
    @SuppressWarnings("unchecked")
    static Font font(Display d, int delta, int style) {
        Map<String, Font> m = (Map<String, Font>) d.getData("dsviz.fonts");
        if (m == null) {
            final Map<String, Font> created = new HashMap<String, Font>();
            m = created;
            d.setData("dsviz.fonts", m);
            d.disposeExec(() -> { for (Font f : created.values()) f.dispose(); });
        }
        String key = delta + "/" + style;
        Font f = m.get(key);
        if (f == null) {
            FontData fd = org.eclipse.jface.resource.JFaceResources.getDialogFont().getFontData()[0];
            f = new Font(d, fd.getName(), Math.max(7, fd.getHeight() + delta), style);
            m.put(key, f);
        }
        return f;
    }

    /** כותרת החלון + שורת הסבר מתחתיה */
    static void header(Composite area, String title, String subtitle) {
        Display d = area.getDisplay();
        Label t = new Label(area, SWT.NONE);
        t.setText(title);
        t.setFont(font(d, 3, SWT.BOLD));
        t.setData(KEEP, Boolean.TRUE);
        t.setForeground(color(d, TEXT));
        Label s = new Label(area, SWT.NONE);
        s.setText(subtitle);
        s.setForeground(color(d, MUTED));
    }

    /** כותרת של קבוצה בתוך כרטיס */
    static Label section(Composite c, String text, int topSpace) {
        Display d = c.getDisplay();
        Label l = new Label(c, SWT.NONE);
        l.setText(text);
        l.setFont(font(d, 0, SWT.BOLD));
        l.setData(KEEP, Boolean.TRUE);
        l.setForeground(color(d, TEXT));
        GridData gd = new GridData(SWT.FILL, SWT.TOP, true, false);
        gd.verticalIndent = topSpace;
        l.setLayoutData(gd);
        return l;
    }

    /** כרטיס לבן מעוגל; מחזיר את הפנים שלו (שם מוסיפים את הרכיבים) */
    static Composite card(Composite parent, GridData data) {
        final Display d = parent.getDisplay();
        final Composite outer = new Composite(parent, SWT.NO_BACKGROUND);
        outer.setBackground(color(d, BG));
        GridLayout ol = new GridLayout(1, false);
        ol.marginWidth = 14;
        ol.marginHeight = 10;
        outer.setLayout(ol);
        outer.setLayoutData(data);
        final Image[] cache = new Image[1];   // הכרטיס מצויר פעם אחת לכל גודל — בגלילה רק מעתיקים תמונה
        outer.addListener(SWT.Paint, e -> {
            Point s = outer.getSize();
            if (s.x < 1 || s.y < 1) return;
            if (cache[0] == null || cache[0].getBounds().width != s.x || cache[0].getBounds().height != s.y) {
                if (cache[0] != null) cache[0].dispose();
                cache[0] = new Image(d, s.x, s.y);
                GC g = new GC(cache[0]);
                g.setAntialias(SWT.ON);
                g.setBackground(color(d, BG));
                g.fillRectangle(0, 0, s.x, s.y);
                g.setBackground(color(d, CARD));
                g.fillRoundRectangle(0, 0, s.x - 1, s.y - 1, 22, 22);
                g.setForeground(color(d, BORDER));
                g.drawRoundRectangle(0, 0, s.x - 1, s.y - 1, 22, 22);
                g.dispose();
            }
            e.gc.drawImage(cache[0], 0, 0);
        });
        outer.addListener(SWT.Dispose, e -> { if (cache[0] != null) cache[0].dispose(); });
        Composite body = new Composite(outer, SWT.NONE);
        body.setBackground(color(d, CARD));
        GridLayout bl = new GridLayout(1, false);
        bl.marginWidth = 0;
        bl.marginHeight = 0;
        bl.verticalSpacing = 4;
        body.setLayout(bl);
        body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        body.setBackgroundMode(SWT.INHERIT_DEFAULT);
        return body;
    }

    // ------------------------------------------------------------------ כפתור-בחירה

    /** כפתור-בחירה מעוגל: לבן, ובבחירה — כחול בהיר עם מסגרת כחולה. מתנהג כמו Button של SWT.TOGGLE */
    static final class Chip extends Canvas {
        private final String text;
        private boolean selected, hover;
        private int minWidth = 0, height = 34, textWidth = -1;
        private Image cache;      // הציור של המצב הנוכחי (גודל + בחירה + ריחוף) — בגלילה רק מעתיקים אותו
        private String cacheKey;

        Chip(Composite parent, String text, int minWidth, int height) {
            super(parent, SWT.NO_BACKGROUND | SWT.LEFT_TO_RIGHT | SWT.NO_FOCUS);
            addListener(SWT.Dispose, e -> { if (cache != null) cache.dispose(); });
            this.text = text;
            this.minWidth = minWidth;
            this.height = height;
            setCursor(getDisplay().getSystemCursor(SWT.CURSOR_HAND));
            addListener(SWT.Paint, e -> paint(e.gc));
            addListener(SWT.MouseEnter, e -> { hover = true; redraw(); });
            addListener(SWT.MouseExit, e -> { hover = false; redraw(); });
            addListener(SWT.MouseUp, e -> {
                if (getEnabled() && e.button == 1 && e.x >= 0 && e.y >= 0 && e.x < getSize().x && e.y < getSize().y) notifyListeners(SWT.Selection, new Event());
            });
        }

        boolean getSelection() { return selected; }

        void setSelection(boolean s) { if (selected != s) { selected = s; redraw(); } }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            redraw();
        }

        void addSelectionListener(final SelectionListener l) {
            addListener(SWT.Selection, e -> l.widgetSelected(new SelectionEvent(e)));
        }

        @Override
        public Point computeSize(int wHint, int hHint, boolean changed) {
            if (textWidth < 0) {   // הרוחב של הטקסט לא משתנה — מודדים פעם אחת (computeSize נקרא שוב ושוב בכל סידור מחדש)
                GC gc = new GC(this);
                gc.setFont(font(getDisplay(), 0, SWT.BOLD));
                textWidth = gc.textExtent(text).x;
                gc.dispose();
            }
            return new Point(wHint != SWT.DEFAULT ? wHint : Math.max(minWidth, textWidth + 26), hHint != SWT.DEFAULT ? hHint : height);
        }

        private void paint(GC target) {
            Point s = getSize();
            if (s.x < 1 || s.y < 1) return;
            String key = s.x + "x" + s.y + (selected ? "S" : "") + (hover ? "H" : "") + (getEnabled() ? "E" : "") + getParent().getBackground().hashCode();
            if (cache == null || !key.equals(cacheKey)) {
                if (cache != null) cache.dispose();
                cache = new Image(getDisplay(), s.x, s.y);
                GC g = new GC(cache);
                draw(g, s);
                g.dispose();
                cacheKey = key;
            }
            target.drawImage(cache, 0, 0);
        }

        private void draw(GC gc, Point s) {
            Display d = getDisplay();
            gc.setAntialias(SWT.ON);
            gc.setBackground(getParent().getBackground());
            gc.fillRectangle(0, 0, s.x, s.y);
            boolean on = selected, enabled = getEnabled();
            int fill = !enabled ? 0xF6F7F9 : on ? ACCENT_SOFT : hover ? 0xF4F7FD : CARD;
            int line = !enabled ? 0xE6E9EF : on ? ACCENT : hover ? 0xB8C4DC : BORDER;
            int ink = !enabled ? 0xB4BAC6 : on ? ACCENT_DARK : TEXT;
            gc.setBackground(color(d, fill));
            gc.fillRoundRectangle(1, 1, s.x - 2, s.y - 2, 14, 14);
            gc.setForeground(color(d, line));
            gc.setLineWidth(on ? 2 : 1);
            gc.drawRoundRectangle(on ? 1 : 0, on ? 1 : 0, s.x - (on ? 3 : 1), s.y - (on ? 3 : 1), 14, 14);
            gc.setLineWidth(1);
            gc.setFont(font(d, 0, on ? SWT.BOLD : SWT.NORMAL));
            String t = text;
            Point e = gc.textExtent(t);
            while (e.x > s.x - 8 && t.length() > 2) { t = t.substring(0, t.length() - 2) + "…"; e = gc.textExtent(t); }
            gc.setForeground(color(d, ink));
            gc.drawString(t, (s.x - e.x) / 2, (s.y - e.y) / 2, true);
        }
    }

    /** קבוצת כפתורי-בחירה: תמיד אחד נבחר; onChange נקרא אחרי כל לחיצה */
    static Chip chip(Composite parent, String text, Object data, final List<Chip> group, int height, int width, final Runnable onChange) {
        final Chip b = new Chip(parent, text, width, height);
        b.setData(data);
        GridData gd = new GridData(SWT.FILL, SWT.FILL, true, false);
        gd.heightHint = height;
        gd.widthHint = width;
        b.setLayoutData(gd);
        group.add(b);
        b.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                for (Chip o : group) o.setSelection(o == b);   // תמיד אחד נבחר
                onChange.run();
            }
        });
        return b;
    }

    // ------------------------------------------------------------------ כפתורים ראשי / משני

    static final class Round extends Canvas {
        private final String text;
        private final boolean primary;
        private boolean hover, down;
        private int textWidth = -1;
        private Image cache;
        private String cacheKey;

        Round(Composite parent, String text, boolean primary, final Runnable action) {
            super(parent, SWT.NO_BACKGROUND | SWT.LEFT_TO_RIGHT | SWT.NO_FOCUS);
            addListener(SWT.Dispose, e -> { if (cache != null) cache.dispose(); });
            this.text = text;
            this.primary = primary;
            setCursor(getDisplay().getSystemCursor(SWT.CURSOR_HAND));
            addListener(SWT.Paint, e -> paint(e.gc));
            addListener(SWT.MouseEnter, e -> { hover = true; redraw(); });
            addListener(SWT.MouseExit, e -> { hover = false; down = false; redraw(); });
            addListener(SWT.MouseDown, e -> { down = true; redraw(); });
            addListener(SWT.MouseUp, e -> {
                boolean fire = down && getEnabled() && e.x >= 0 && e.y >= 0 && e.x < getSize().x && e.y < getSize().y;
                down = false;
                redraw();
                if (fire) action.run();
            });
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            redraw();
        }

        @Override
        public Point computeSize(int wHint, int hHint, boolean changed) {
            if (textWidth < 0) {
                GC gc = new GC(this);
                gc.setFont(font(getDisplay(), 0, primary ? SWT.BOLD : SWT.NORMAL));
                textWidth = gc.textExtent(text).x;
                gc.dispose();
            }
            return new Point(Math.max(primary ? 130 : 80, textWidth + 36), 32);
        }

        private void paint(GC target) {
            Point s = getSize();
            if (s.x < 1 || s.y < 1) return;
            String key = s.x + "x" + s.y + (hover ? "H" : "") + (down ? "D" : "") + (getEnabled() ? "E" : "") + getParent().getBackground().hashCode();
            if (cache == null || !key.equals(cacheKey)) {
                if (cache != null) cache.dispose();
                cache = new Image(getDisplay(), s.x, s.y);
                GC g = new GC(cache);
                draw(g, s);
                g.dispose();
                cacheKey = key;
            }
            target.drawImage(cache, 0, 0);
        }

        private void draw(GC gc, Point s) {
            Display d = getDisplay();
            gc.setAntialias(SWT.ON);
            gc.setBackground(getParent().getBackground());
            gc.fillRectangle(0, 0, s.x, s.y);
            boolean enabled = getEnabled();
            int fill, ink = primary ? 0xFFFFFF : TEXT;
            if (primary) fill = !enabled ? 0xB9C8EE : down ? 0x1E4AA8 : hover ? ACCENT_DARK : ACCENT;
            else fill = down ? 0xE9EDF5 : hover ? 0xF4F7FD : CARD;
            gc.setBackground(color(d, fill));
            gc.fillRoundRectangle(0, 0, s.x - 1, s.y - 1, 16, 16);
            if (!primary) {
                gc.setForeground(color(d, hover ? 0xB8C4DC : 0xC9D0DC));
                gc.drawRoundRectangle(0, 0, s.x - 1, s.y - 1, 16, 16);
            }
            gc.setFont(font(d, 0, primary ? SWT.BOLD : SWT.NORMAL));
            Point e = gc.textExtent(text);
            gc.setForeground(color(d, ink));
            gc.drawString(text, (s.x - e.x) / 2, (s.y - e.y) / 2, true);
        }
    }

    /** שורת הכפתורים של החלון: ראשי (הפעולה) + ביטול; הראשי בימין (החלון מימין לשמאל) */
    static Composite buttonBar(Composite parent, String okText, Runnable ok, Runnable cancel, Round[] okOut) {
        Composite bar = new Composite(parent, SWT.NONE);
        bar.setBackground(color(parent.getDisplay(), BG));
        GridLayout l = new GridLayout(2, false);
        l.marginWidth = 20;
        l.marginHeight = 10;
        l.horizontalSpacing = 10;
        bar.setLayout(l);
        bar.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));
        Round primary = new Round(bar, okText, true, ok);
        new Round(bar, "ביטול", false, cancel);
        okOut[0] = primary;
        return bar;
    }

    // ------------------------------------------------------------------ גודל וגלילה

    /** גופן קטן יותר לכל הרכיבים הרגילים בחלון (כותרות, כפתורי-בחירה וכפתורים ראשיים/משניים מצוירים בנפרד) */
    static void applyFont(Control root, int delta) {
        Display d = root.getDisplay();
        if (!(root instanceof Chip) && !(root instanceof Round) && !(root instanceof ScrolledComposite) && root.getData(KEEP) == null
                && !(root instanceof Canvas && !(root instanceof Composite)))
            root.setFont(font(d, delta, SWT.NORMAL));
        if (root instanceof Composite && !(root instanceof Chip) && !(root instanceof Round))
            for (Control c : ((Composite) root).getChildren()) applyFont(c, delta);
    }

    /** אזור גלילה אנכי: מחזיר את הגלילה, ואת התוכן שלו ב-content[0] (מוסיפים אליו רכיבים כרגיל) */
    static ScrolledComposite scroll(Composite parent, GridLayout layout, Composite[] content) {
        final ScrolledComposite sc = new ScrolledComposite(parent, SWT.V_SCROLL);
        sc.setBackground(color(parent.getDisplay(), BG));
        sc.setBackgroundMode(SWT.INHERIT_DEFAULT);
        sc.setExpandHorizontal(true);
        sc.setExpandVertical(true);
        sc.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        Composite c = new Composite(sc, SWT.NONE);
        c.setLayout(layout);
        sc.setContent(c);
        sc.addListener(SWT.Resize, e -> updateScroll(sc));
        content[0] = c;
        return sc;
    }

    /** מעדכן את הגובה המינימלי של התוכן (אחרי שינוי בתוכן או ברוחב) — מופיע פס גלילה רק אם הוא לא נכנס */
    static void updateScroll(ScrolledComposite sc) {
        Control c = sc.getContent();
        if (c == null) return;
        Rectangle r = sc.getClientArea();
        sc.setMinHeight(c.computeSize(Math.max(1, r.width), SWT.DEFAULT, true).y);
    }

    /** גלגלת העכבר גוללת את האזור גם כשהעכבר מעל רכיב בתוכו */
    static void wheel(final ScrolledComposite sc) {
        wheel(sc, sc.getContent());
    }

    private static void wheel(final ScrolledComposite sc, Control c) {
        if (!(c instanceof org.eclipse.swt.widgets.Combo) && !(c instanceof org.eclipse.swt.widgets.Spinner))
            c.addListener(SWT.MouseWheel, e -> {
                scrollBy(sc, -e.count * 30);
                e.doit = false;
            });
        if (c instanceof Composite) for (Control k : ((Composite) c).getChildren()) wheel(sc, k);
    }

    private static final String PENDING = "dsviz.pendingScroll";

    /**
     * גלילה ממוזגת: בטאצ'פד/גלגלת מהירה מגיעים עשרות אירועים בשנייה, וכל הזזה של התוכן מציירת מחדש את כל הרכיבים שבו —
     * אז צוברים את ההפרש ומזיזים פעם אחת בכל סבב ציור (במקום הזזה אחרי כל אירוע, שנערמת ונראית כמריחה)
     */
    static void scrollBy(final ScrolledComposite sc, int dy) {
        Integer pending = (Integer) sc.getData(PENDING);
        sc.setData(PENDING, (pending == null ? 0 : pending) + dy);
        if (pending != null) return;   // כבר מתוזמן
        sc.getDisplay().timerExec(12, () -> {
            if (sc.isDisposed()) return;
            Integer sum = (Integer) sc.getData(PENDING);
            sc.setData(PENDING, null);
            if (sum == null || sum == 0) return;
            Point o = sc.getOrigin();
            int max = Math.max(0, sc.getContent().getSize().y - sc.getClientArea().height);
            int y = Math.max(0, Math.min(max, o.y + sum));
            if (y != o.y) sc.setOrigin(o.x, y);
        });
    }

    /** גודל החלון לפי התוכן, אבל לא יותר מהמסך (שולי ביטחון לשורת המשימות וכותרת החלון) */
    static void fit(Shell sh, boolean growOnly) {
        Rectangle screen = sh.getMonitor().getClientArea();
        Point need = sh.computeSize(SWT.DEFAULT, SWT.DEFAULT), cur = sh.getSize();
        int w = Math.min(need.x, screen.width - 40), h = Math.min(need.y, screen.height - 60);
        if (growOnly) { w = Math.max(w, cur.x); h = Math.max(h, cur.y); }
        if (w != cur.x || h != cur.y) sh.setSize(w, h);
        sh.layout(true, true);
    }

    // ------------------------------------------------------------------ שדה עם הצעה באפור

    /**
     * שדה טקסט שמציע ערך בצבע אפור: כל עוד לא הקלידו — מוצגת ההצעה (ו-value() מחזיר ""), ברגע שמקלידים ההצעה נעלמת.
     * אם מוחקים הכול — ההצעה חוזרת.
     */
    static final class HintText {
        final org.eclipse.swt.widgets.Text text;
        private String hint = "";
        private boolean showing, internal;
        private final Display display;

        HintText(Composite parent, int style) {
            text = new org.eclipse.swt.widgets.Text(parent, style);
            display = parent.getDisplay();
            text.addListener(SWT.Verify, e -> {
                if (internal || !showing) return;
                if (e.text.isEmpty()) { e.doit = false; return; }   // מחיקה כשמוצגת ההצעה — לא עושים כלום
                e.start = 0;                                          // הקלדה: מחליפים את כל ההצעה
                e.end = text.getCharCount();
                showing = false;
                text.setForeground(null);
            });
            text.addListener(SWT.Modify, e -> {
                if (!internal && !showing && text.getText().isEmpty()) show();
            });
            Listener caretToStart = e -> { if (showing) text.setSelection(0); };
            text.addListener(SWT.FocusIn, caretToStart);
            text.addListener(SWT.MouseUp, caretToStart);
            text.addListener(SWT.KeyUp, e -> { if (showing && (e.keyCode == SWT.ARROW_RIGHT || e.keyCode == SWT.END)) text.setSelection(0); });
        }

        private void show() {
            internal = true;
            try {
                text.setText(hint);
                text.setForeground(color(display, 0x9AA3B2));
                text.setSelection(0);
                showing = true;
            } finally {
                internal = false;
            }
        }

        /** ההצעה (נראית באפור כשהשדה ריק) */
        void setHint(String h) {
            hint = h == null ? "" : h;
            if (showing || text.getText().isEmpty()) show();
        }

        /** מה שהמשתמש הקליד — "" אם עוד לא הקליד כלום (אז משתמשים בהצעה) */
        String value() {
            return showing ? "" : text.getText();
        }

        boolean isShowingHint() {
            return showing;
        }
    }
}

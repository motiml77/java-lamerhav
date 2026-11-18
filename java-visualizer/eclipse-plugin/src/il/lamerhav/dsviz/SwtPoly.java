package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

/**
 * הורשה ופולימורפיזם ב-Eclipse (SWT): התוספת לחלון "צור מחלקה" ({@link Inherit}), "היררכיה מוכנה" ({@link TreeDialog}) ו"הוסף עצם"
 * ({@link ObjectDialog}). אותו מראה ואותו היגיון כמו PolyPanels (Swing — Visual Studio ו-IntelliJ): הבחירה של מחלקה היא תגיות בצבע
 * של המחלקה (כמו בהרצה הוויזואלית, ClassGen.colors), ושם המחלקה תמיד כתוב על הצבע.
 */
public final class SwtPoly {
    private SwtPoly() {}

    // ------------------------------------------------------------------ תגית / ציור עץ

    /** תגית של מחלקה: מסגרת מעוגלת בצבע שלה עם השם. selectable — לוחצים לבחירה (מסגרת עבה כשנבחרה) */
    static final class Tag extends Canvas {
        String name;
        int color;
        boolean selected, isAbstract;
        Runnable onClick;

        Tag(Composite parent, String name, int color) {
            super(parent, SWT.DOUBLE_BUFFERED | SWT.LEFT_TO_RIGHT);
            this.name = name;
            this.color = color;
            setData("dsviz.keepFont", Boolean.TRUE);
            setFont(Look.mono(getDisplay(), -1));
            addListener(SWT.Paint, e -> paint(e.gc));
            addListener(SWT.MouseUp, e -> { if (onClick != null) onClick.run(); });
        }

        String label() {
            return name + (isAbstract ? " (abstract)" : "");
        }

        @Override
        public Point computeSize(int wHint, int hHint, boolean changed) {
            GC gc = new GC(this);
            gc.setFont(getFont());
            Point e = gc.textExtent(label());
            gc.dispose();
            return new Point(e.x + 20, e.y + 8);
        }

        void paint(GC g) {
            Point s = getSize();
            g.setAntialias(SWT.ON);
            g.setBackground(getParent().getBackground());
            g.fillRectangle(0, 0, s.x, s.y);
            drawTag(g, 1, 1, s.x - 2, s.y - 2, label(), color, selected, getFont());
        }
    }

    static void drawTag(GC g, int x, int y, int w, int h, String text, int color, boolean bold, Font f) {
        Display d = g.getDevice() instanceof Display ? (Display) g.getDevice() : Display.getCurrent();
        g.setBackground(Look.color(d, ClassGen.bg(color)));
        g.fillRoundRectangle(x, y, w - 1, h - 1, 10, 10);
        g.setForeground(Look.color(d, ClassGen.ink(color)));
        g.setLineWidth(bold ? 3 : 1);
        g.setLineStyle(ClassGen.dashed(color) ? SWT.LINE_DASH : SWT.LINE_SOLID);
        g.drawRoundRectangle(x, y, w - 1, h - 1, 10, 10);
        g.setLineWidth(1);
        g.setLineStyle(SWT.LINE_SOLID);
        g.setFont(f);
        Point e = g.textExtent(text);
        g.drawString(text, x + 9, y + (h - e.y) / 2, true);
    }

    /** העץ: כל מחלקה בתגית בצבע שלה, ילדים מוזחים עם קו מחבר */
    static final class TreeCanvas extends Canvas {
        List<Object[]> rows = new ArrayList<Object[]>();

        TreeCanvas(Composite parent) {
            super(parent, SWT.DOUBLE_BUFFERED | SWT.LEFT_TO_RIGHT | SWT.BORDER);
            setData("dsviz.keepFont", Boolean.TRUE);
            setFont(Look.mono(getDisplay(), -1));
            setBackground(Look.color(getDisplay(), Look.SURFACE));
            addListener(SWT.Paint, e -> paint(e.gc));
        }

        int rowH(GC g) {
            return g.textExtent("Ag").y + 12;
        }

        @Override
        public Point computeSize(int wHint, int hHint, boolean changed) {
            GC g = new GC(this);
            g.setFont(getFont());
            int w = 200, h = rowH(g);
            for (Object[] r : rows) w = Math.max(w, 16 + (Integer) r[1] * 26 + g.textExtent(r[0] + ((Boolean) r[3] ? " (abstract)" : "")).x + 30);
            g.dispose();
            return new Point(Math.max(wHint, w), Math.max(60, rows.size() * h + 10));
        }

        void paint(GC g) {
            g.setAntialias(SWT.ON);
            g.setFont(getFont());
            int h = rowH(g);
            if (rows.isEmpty()) {
                g.setForeground(Look.color(getDisplay(), Look.MUTED));
                g.drawString("(העץ יצויר כאן)", 10, 10, true);
            }
            for (int i = 0; i < rows.size(); i++) {
                Object[] r = rows.get(i);
                int depth = (Integer) r[1], x = 8 + depth * 26, y = 5 + i * h;
                String text = r[0] + ((Boolean) r[3] ? " (abstract)" : "");
                if (depth > 0) {
                    int p = i - 1;
                    while (p >= 0 && (Integer) rows.get(p)[1] >= depth) p--;
                    if (p >= 0) {
                        g.setForeground(Look.color(getDisplay(), 0xB8C0CC));
                        int px = 8 + (depth - 1) * 26 + 12, py = 5 + p * h + h - 6, my = y + (h - 6) / 2;
                        g.drawLine(px, py, px, my);
                        g.drawLine(px, my, x, my);
                    }
                }
                drawTag(g, x, y, g.textExtent(text).x + 20, h - 6, text, (Integer) r[2], false, getFont());
            }
        }
    }

    /** שורת תגיות לבחירה של מחלקה ("בלי" / "ריק" ראשון כשיש) — כמו רשימה נפתחת, אבל כל מחלקה בצבע שלה */
    static final class Picker {
        final Composite box;
        final List<Tag> tags = new ArrayList<Tag>();
        final String none;
        String value = "";
        Runnable onChange;
        Map<String, Integer> colors;

        Picker(Composite parent, String none, Runnable onChange) {
            box = new Composite(parent, SWT.LEFT_TO_RIGHT);
            RowLayout rl = new RowLayout(SWT.HORIZONTAL);
            rl.wrap = true;
            rl.spacing = 6;
            rl.marginLeft = rl.marginTop = rl.marginBottom = 0;
            box.setLayout(rl);
            box.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            this.none = none;
            this.onChange = onChange;
        }

        void set(List<ClassGen.Known> items, Map<String, Integer> colors, String select) {
            this.colors = colors;
            for (Tag t : tags) t.dispose();
            tags.clear();
            if (none != null) add("", none, -1, false);
            for (ClassGen.Known k : items) {
                Integer c = colors.get(k.name);
                add(k.name, k.name, c == null ? -1 : c, k.isAbstract);
            }
            boolean found = false;
            for (Tag t : tags) found |= t.getData().equals(select);
            value = found ? select : tags.isEmpty() ? "" : (String) tags.get(0).getData();
            mark();
            box.layout(true, true);
        }

        private void add(String key, String label, int color, boolean abs) {
            final Tag t = new Tag(box, label, color);
            t.isAbstract = abs;
            t.setData(key);
            t.setToolTipText(key.isEmpty() ? label : key);
            t.onClick = () -> select((String) t.getData());
            tags.add(t);
        }

        void select(String key) {
            value = key;
            mark();
            if (onChange != null) onChange.run();
        }

        void mark() {
            for (Tag t : tags) {
                t.selected = t.getData().equals(value);
                t.redraw();
            }
        }
    }

    static Label muted(Composite c, String text) {
        Label l = new Label(c, SWT.WRAP);
        l.setText(text);
        l.setForeground(Look.color(c.getDisplay(), Look.MUTED));
        l.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        return l;
    }

    static Text text(Composite c, int width, String hint) {
        Text t = new Text(c, SWT.BORDER | SWT.LEFT_TO_RIGHT);
        t.setMessage(hint);
        t.setFont(Look.mono(c.getDisplay(), 0));
        t.setData("dsviz.keepFont", Boolean.TRUE);
        GridData g = new GridData(SWT.FILL, SWT.CENTER, false, false);
        g.widthHint = width;
        t.setLayoutData(g);
        return t;
    }

    static Composite grid(Composite parent, int cols, int style) {
        Composite c = new Composite(parent, style);
        GridLayout l = new GridLayout(cols, false);
        l.marginWidth = 0;
        l.marginHeight = 0;
        l.horizontalSpacing = 6;
        l.verticalSpacing = 4;
        c.setLayout(l);
        c.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        return c;
    }

    static void clear(Composite c) {
        for (Control k : c.getChildren()) k.dispose();
    }

    // ------------------------------------------------------------------ צור מחלקה: יורשת מ-

    /** התוספת ל"צור מחלקה" (ClassDialog): top() אחרי שם המחלקה, lower() אחרי השדות */
    static final class Inherit {
        final String lang;
        final List<ClassGen.Known> known;
        final Runnable onChange;
        Picker sup;
        Composite inheritedBox, overridesBox, methodsBox;
        Label inheritedHead, overridesHead;
        Button isAbstract;
        final Set<String> picked = new LinkedHashSet<String>();
        final List<Control[]> methods = new ArrayList<Control[]>();
        String shownKeys = "", shownInherited = "";

        Inherit(String lang, List<ClassGen.Known> known, Runnable onChange) {
            this.lang = lang;
            this.known = known;
            this.onChange = onChange;
        }

        void top(Composite parent) {
            Look.section(parent, "יורשת מ-", 10);
            sup = new Picker(parent, "בלי", onChange);
            sup.set(ClassGen.superCandidates(known), ClassGen.colors(ClassGen.sorted(known)), "");
            if (known.isEmpty()) muted(parent, "(אין עדיין מחלקות בפרויקט)");
            inheritedHead = Look.section(parent, "שדות שירשה", 8);
            inheritedBox = grid(parent, 2, SWT.LEFT_TO_RIGHT);
        }

        void lower(Composite parent) {
            overridesHead = Look.section(parent, "פעולות לדריסה", 10);
            overridesBox = grid(parent, 2, SWT.LEFT_TO_RIGHT);
            Look.section(parent, "פעולה חדשה", 10);
            muted(parent, "טיפוס מוחזר · שם · פרמטרים (למשל  int steps, String word)");
            methodsBox = grid(parent, 5, SWT.LEFT_TO_RIGHT);
            Composite bar = grid(parent, 1, SWT.NONE);
            new Look.Round(bar, "+ פעולה", false, () -> addMethod("String", "", "", false, true));
            isAbstract = new Button(parent, SWT.CHECK);
            isAbstract.setText("מחלקה מופשטת (abstract) — אי אפשר ליצור ממנה עצם, רק לרשת ממנה");
            isAbstract.addListener(SWT.Selection, e -> { layoutMethods(); onChange.run(); });
            show(false);
        }

        void addMethod(String ret, String name, String params, boolean abs, boolean focus) {
            Text r = text(methodsBox, 70, "String"), n = text(methodsBox, 90, "say"), p = text(methodsBox, 160, "int x, String s");
            r.setText(ret);
            n.setText(name);
            p.setText(params);
            Button a = new Button(methodsBox, SWT.CHECK);
            a.setText("abstract");
            a.setSelection(abs);
            Button x = new Button(methodsBox, SWT.PUSH | SWT.FLAT);
            x.setText("✕");
            x.setToolTipText("הסרת הפעולה");
            final Control[] row = { r, n, p, a, x };
            methods.add(row);
            for (Text t : new Text[] { r, n, p }) t.addListener(SWT.Modify, e -> onChange.run());
            a.addListener(SWT.Selection, e -> onChange.run());
            x.addListener(SWT.Selection, e -> {
                methods.remove(row);
                for (Control c : row) c.dispose();
                relayout();
                onChange.run();
            });
            layoutMethods();
            relayout();
            if (focus) n.setFocus();
            onChange.run();
        }

        void layoutMethods() {
            for (Control[] row : methods) row[3].setVisible(isAbstract != null && isAbstract.getSelection());
        }

        void relayout() {
            Shell s = methodsBox.getShell();
            s.layout(true, true);
        }

        void show(boolean on) {
            for (Control c : new Control[] { inheritedHead, inheritedBox, overridesHead, overridesBox }) {
                if (c == null) continue;
                c.setVisible(on);
                ((GridData) c.getLayoutData()).exclude = !on;
            }
        }

        void apply(ClassGen.Options o) {
            o.superName = sup == null ? "" : sup.value;
            o.superPkg = null;
            for (ClassGen.Known k : known) if (k.name.equals(o.superName)) { o.superPkg = k.pkg; break; }
            o.isAbstract = isAbstract != null && isAbstract.getSelection();
            o.overrides.clear();
            o.overrides.addAll(picked);
            o.methods.clear();
            for (Control[] row : methods) {
                ClassGen.Method m = new ClassGen.Method(((Text) row[0]).getText(), ((Text) row[1]).getText(), ((Text) row[2]).getText());
                m.isAbstract = o.isAbstract && ((Button) row[3]).getSelection();
                o.methods.add(m);
            }
        }

        /** אחרי כל שינוי: שדות שירשה ופעולות לדריסה (בונה מחדש רק כשהשתנו) */
        boolean refresh(ClassGen.Check c, String className) {
            boolean on = c != null && c.sup != null, changed = false;
            boolean wasOn = inheritedHead != null && inheritedHead.getVisible();
            if (on != wasOn) changed = true;
            show(on);
            if (!on) {
                shownKeys = "";
                shownInherited = "";
                return changed;
            }
            Map<String, Integer> col = ClassGen.colorsWith(known, className == null ? "MyClass" : className, c.sup);
            StringBuilder ik = new StringBuilder();
            for (ClassGen.Inherited h : c.inherited) {
                ik.append(h.from).append(col.get(h.from)).append(':');
                for (ClassGen.Field f : h.fields) ik.append(f.type).append(' ').append(f.name).append(',');
            }
            if (!ik.toString().equals(shownInherited)) {
                shownInherited = ik.toString();
                clear(inheritedBox);
                for (ClassGen.Inherited h : c.inherited) {
                    Integer ci = col.get(h.from);
                    new Tag(inheritedBox, h.from, ci == null ? -1 : ci);
                    StringBuilder fs = new StringBuilder();
                    for (ClassGen.Field f : h.fields) fs.append(fs.length() > 0 ? ", " : "").append(f.type).append(' ').append(f.name);
                    Label l = new Label(inheritedBox, SWT.LEFT_TO_RIGHT);
                    l.setText(fs.length() == 0 ? "(אין שדות)" : fs.toString());
                    l.setFont(Look.mono(inheritedBox.getDisplay(), -1));
                    l.setData("dsviz.keepFont", Boolean.TRUE);
                    l.setForeground(Look.color(inheritedBox.getDisplay(), 0x8A909C));
                }
                changed = true;
            }
            StringBuilder keys = new StringBuilder();
            for (ClassGen.Overridable ov : c.overridable) keys.append(ov.key).append(ov.isAbstract).append('|');
            keys.append(isAbstract.getSelection());
            if (!keys.toString().equals(shownKeys)) {
                shownKeys = keys.toString();
                clear(overridesBox);
                if (c.overridable.isEmpty()) {
                    Label l = muted(overridesBox, "(אין פעולות שאפשר לדרוס — toString בתיבת הסימון למטה)");
                    ((GridData) l.getLayoutData()).horizontalSpan = 2;
                }
                for (final ClassGen.Overridable ov : c.overridable) {
                    boolean required = ov.isAbstract && !isAbstract.getSelection();
                    final Button b = new Button(overridesBox, SWT.CHECK | SWT.LEFT_TO_RIGHT);
                    b.setText(ov.method.ret + " " + ov.method.name + "(" + ov.method.params + ")");
                    b.setFont(Look.mono(overridesBox.getDisplay(), -1));
                    b.setData("dsviz.keepFont", Boolean.TRUE);
                    b.setSelection(required || picked.contains(ov.key));
                    b.setEnabled(!required);
                    b.addListener(SWT.Selection, e -> {
                        if (b.getSelection()) picked.add(ov.key);
                        else picked.remove(ov.key);
                        onChange.run();
                    });
                    Integer ci = col.get(ov.from);
                    Label from = new Label(overridesBox, SWT.NONE);
                    from.setText("מ-" + ov.from + (required ? " — מופשטת, חובה לדרוס" : ""));
                    from.setForeground(Look.color(overridesBox.getDisplay(), ClassGen.ink(ci == null ? -1 : ci)));
                }
                changed = true;
            }
            return changed;
        }
    }

    // ------------------------------------------------------------------ היררכיה מוכנה

    public static final class TreeDialog extends Dialog {
        final ClassGen.Options base;
        final List<ClassGen.Tree> trees = ClassGen.trees();
        final List<Look.Chip> chips = new ArrayList<Look.Chip>();
        final List<Control[]> rows = new ArrayList<Control[]>();
        Label about, files, errorLabel, warnLabel;
        Composite own, ownRows;
        TreeCanvas view;
        Button withMain;
        Text preview;
        Look.Round ok;
        int choice;
        boolean updating;
        ClassGen.TreeResult result;
        ClassGen.Tree tree;

        public TreeDialog(Shell parent, ClassGen.Options base) {
            super(parent);
            this.base = base;
            setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.RIGHT_TO_LEFT | SWT.APPLICATION_MODAL);
        }

        @Override
        protected void configureShell(Shell shell) {
            super.configureShell(shell);
            shell.setText(ClassGen.TREES_TITLE);
            shell.setBackground(Look.color(shell.getDisplay(), Look.BG));
            shell.setBackgroundMode(SWT.INHERIT_DEFAULT);
        }

        @Override
        protected Control createDialogArea(Composite parent) {
            Composite area = (Composite) super.createDialogArea(parent);
            GridLayout al = (GridLayout) area.getLayout();
            al.marginWidth = 16;
            al.marginHeight = 8;
            area.setBackground(Look.color(area.getDisplay(), Look.BG));
            Look.header(area, ClassGen.TREES_TITLE, "כל המחלקות של עץ בלחיצה אחת — כל מחלקה בקובץ משלה, עם בנאי שמעביר ל-super, ו-main לדוגמה עם מערך");
            GridLayout cl = new GridLayout(2, false);
            cl.marginWidth = 0;
            cl.horizontalSpacing = 14;
            Composite[] holder = new Composite[1];
            ScrolledComposite scroll = Look.scroll(area, cl, holder);
            Composite right = Look.card(holder[0], new GridData(SWT.FILL, SWT.FILL, false, true));
            Composite left = Look.card(holder[0], new GridData(SWT.FILL, SWT.FILL, true, true));
            Look.section(right, "בוחרים היררכיה", 0);
            Composite chipRow = new Composite(right, SWT.NONE);
            chipRow.setLayout(new GridLayout(trees.size() + 1, false));
            for (int i = 0; i <= trees.size(); i++) {
                final int k = i;
                Look.chip(chipRow, i < trees.size() ? trees.get(i).title : ClassGen.OWN_TREE, k, chips, 30, 140, () -> choose(k)).setSelection(i == 0);
            }
            about = muted(right, " ");
            own = grid(right, 1, SWT.NONE);
            muted(own, "שם המחלקה · יורשת מ- (ריק — אף אחת) · השדות (String name; int age)");
            ownRows = grid(own, 5, SWT.LEFT_TO_RIGHT);
            Composite bar = grid(own, 1, SWT.NONE);
            new Look.Round(bar, "+ מחלקה", false, () -> addRow("", "", "", true));
            Look.section(right, "העץ", 10);
            view = new TreeCanvas(right);
            GridData vg = new GridData(SWT.FILL, SWT.TOP, true, false);
            vg.heightHint = 200;
            vg.widthHint = 340;
            view.setLayoutData(vg);
            withMain = new Button(right, SWT.CHECK);
            withMain.setText("גם main לדוגמה — מערך של מחלקת העל, ועצם מכל מחלקה");
            withMain.setSelection(true);
            withMain.addListener(SWT.Selection, e -> update());
            files = muted(right, " ");
            Look.section(left, "כך ייראה ה-main", 0);
            preview = new Text(left, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL | SWT.LEFT_TO_RIGHT);
            preview.setFont(Look.mono(area.getDisplay(), -1));
            preview.setData("dsviz.keepFont", Boolean.TRUE);
            preview.setBackground(Look.color(area.getDisplay(), Look.SURFACE));
            GridData pg = new GridData(SWT.FILL, SWT.FILL, true, true);
            pg.widthHint = 420;
            pg.heightHint = 380;
            preview.setLayoutData(pg);
            warnLabel = new Label(area, SWT.WRAP);
            warnLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            warnLabel.setForeground(Look.color(area.getDisplay(), 0x9A6700));
            errorLabel = new Label(area, SWT.WRAP);
            errorLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            errorLabel.setForeground(area.getDisplay().getSystemColor(SWT.COLOR_RED));
            addRow("Vehicle", "", "String model; int year", false);
            addRow("Bus", "Vehicle", "int seats", false);
            addRow("Taxi", "Vehicle", "String company", false);
            Look.applyFont(area, -1);
            Look.wheel(scroll);
            choose(0);
            return area;
        }

        @Override
        protected Control createButtonBar(Composite parent) {
            Look.Round[] out = new Look.Round[1];
            Composite bar = Look.buttonBar(parent, "יצירת המחלקות", () -> { if (ok.getEnabled()) okPressed(); }, () -> cancelPressed(), out);
            ok = out[0];
            update();
            return bar;
        }

        void choose(int k) {
            choice = k;
            for (Look.Chip c : chips) c.setSelection(Integer.valueOf(k).equals(c.getData()));
            boolean ownOn = k == trees.size();
            own.setVisible(ownOn);
            ((GridData) own.getLayoutData()).exclude = !ownOn;
            about.setText(k < trees.size() ? trees.get(k).about : "כותבים בטבלה את המחלקות — העץ מצויר תוך כדי.");
            update();
        }

        void addRow(String name, String sup, String fields, boolean focus) {
            Text n = text(ownRows, 90, "Vehicle");
            Label ex = new Label(ownRows, SWT.NONE);
            ex.setText("extends");
            Combo s = new Combo(ownRows, SWT.DROP_DOWN | SWT.LEFT_TO_RIGHT);
            GridData sg = new GridData(SWT.FILL, SWT.CENTER, false, false);
            sg.widthHint = 100;
            s.setLayoutData(sg);
            s.setText(sup);
            Text f = text(ownRows, 170, "String name; int age");
            n.setText(name);
            f.setText(fields);
            Button x = new Button(ownRows, SWT.PUSH | SWT.FLAT);
            x.setText("✕");
            final Control[] row = { n, ex, s, f, x };
            rows.add(row);
            n.addListener(SWT.Modify, e -> update());
            f.addListener(SWT.Modify, e -> update());
            s.addListener(SWT.Modify, e -> update());
            x.addListener(SWT.Selection, e -> {
                rows.remove(row);
                for (Control c : row) c.dispose();
                getShell().layout(true, true);
                update();
            });
            if (getShell() != null) getShell().layout(true, true);
            if (focus) n.setFocus();
            update();
        }

        ClassGen.Tree tree(List<String> errors) {
            if (choice < trees.size()) return trees.get(choice);
            List<ClassGen.TreeRow> rs = new ArrayList<ClassGen.TreeRow>();
            for (Control[] r : rows) rs.add(new ClassGen.TreeRow(((Text) r[0]).getText(), ((Combo) r[2]).getText(), ((Text) r[3]).getText()));
            return ClassGen.ownTree(rs, base.known, errors);
        }

        void update() {
            if (updating || errorLabel == null || ok == null) return;
            updating = true;
            try {
                List<String> errors = new ArrayList<String>();
                tree = tree(errors);
                view.rows = ClassGen.treeLayout(tree, base.known);
                view.redraw();
                result = ClassGen.treeFiles(tree, base, withMain.getSelection(), false);
                result.errors.addAll(0, errors);
                StringBuilder fs = new StringBuilder();
                String main = null;
                for (String[] f : result.files) {
                    fs.append(fs.length() > 0 ? ", " : "").append(f[0]);
                    if (tree.main != null && f[0].startsWith(tree.main + ".")) main = f[1];
                }
                files.setText(fs.length() == 0 ? " " : "ייווצרו: " + fs);
                String text = main != null ? main : result.files.isEmpty() ? "" : result.files.get(0)[1];
                if (!text.equals(preview.getText())) preview.setText(text);
                errorLabel.setText(result.errors.isEmpty() ? "" : result.errors.get(0));
                warnLabel.setText(String.join("\n", result.warnings));
                ok.setEnabled(result.ok() && !result.files.isEmpty());
                // "יורשת מ-" בטבלה: שאר השורות ומחלקות הפרויקט
                List<String> names = new ArrayList<String>();
                for (Control[] r : rows) {
                    String n = ((Text) r[0]).getText().trim();
                    if (!n.isEmpty()) names.add(ClassGen.cap(n));
                }
                for (ClassGen.Known k : ClassGen.superCandidates(base.known)) if (!names.contains(k.name)) names.add(k.name);
                for (Control[] r : rows) {
                    Combo s = (Combo) r[2];
                    String self = ClassGen.cap(((Text) r[0]).getText().trim());
                    List<String> want = new ArrayList<String>();
                    for (String n : names) if (!n.equals(self)) want.add(n);
                    if (!java.util.Arrays.asList(s.getItems()).equals(want)) {
                        String cur = s.getText();
                        s.setItems(want.toArray(new String[0]));
                        s.setText(cur);
                    }
                }
                getShell().layout(true, true);
            } finally {
                updating = false;
            }
        }

        // ---------------------------------------------------------------- DlgScript: call ...
        void pick(String k) {
            choose(Integer.parseInt(k.trim()));
        }
    }

    // ------------------------------------------------------------------ הוסף עצם

    public static final class ObjectDialog extends Dialog {
        final List<ClassGen.Known> known;
        final String doc, indent;
        final int offset;
        final Map<String, Integer> colors;
        Button one, many;
        Picker type, cls;
        Text name;
        Spinner size;
        Composite params, cells;
        Label clsHead, paramsHead, cellsHead, errorLabel;
        Text preview;
        Look.Round ok;
        final List<Text> args = new ArrayList<Text>();
        final List<Control[]> cellRows = new ArrayList<Control[]>();
        String shownParams = "", code;
        boolean updating;

        public ObjectDialog(Shell parent, List<ClassGen.Known> known, String doc, int offset, String indent) {
            super(parent);
            this.known = known;
            this.doc = doc;
            this.offset = offset;
            this.indent = indent;
            this.colors = ClassGen.colors(ClassGen.sorted(known));
            setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.RIGHT_TO_LEFT | SWT.APPLICATION_MODAL);
        }

        @Override
        protected void configureShell(Shell shell) {
            super.configureShell(shell);
            shell.setText(ClassGen.OBJECT_TITLE);
            shell.setBackground(Look.color(shell.getDisplay(), Look.BG));
            shell.setBackgroundMode(SWT.INHERIT_DEFAULT);
        }

        @Override
        protected Control createDialogArea(Composite parent) {
            Composite area = (Composite) super.createDialogArea(parent);
            GridLayout al = (GridLayout) area.getLayout();
            al.marginWidth = 16;
            al.marginHeight = 8;
            area.setBackground(Look.color(area.getDisplay(), Look.BG));
            Look.header(area, ClassGen.OBJECT_TITLE, "משתנה מסוג מחלקת העל שמצביע על עצם של מחלקה יורשת — או מערך שבכל תא מחלקה אחרת");
            Composite card = Look.card(area, new GridData(SWT.FILL, SWT.FILL, true, true));
            Composite modes = grid(card, 2, SWT.NONE);
            one = new Button(modes, SWT.RADIO);
            one.setText("עצם אחד");
            one.setSelection(true);
            many = new Button(modes, SWT.RADIO);
            many.setText("מערך של מחלקת העל");
            one.addListener(SWT.Selection, e -> update());
            many.addListener(SWT.Selection, e -> update());
            Look.section(card, "טיפוס המשתנה", 10);
            List<ClassGen.Known> types = new ArrayList<ClassGen.Known>();
            for (ClassGen.Known k : ClassGen.sorted(known)) if (!k.isInterface) types.add(k);
            type = new Picker(card, null, () -> { fillClasses(); update(); });
            type.set(types, colors, "");
            Composite nr = grid(card, 4, SWT.NONE);
            new Label(nr, SWT.NONE).setText("שם:");
            name = text(nr, 110, "a1");
            name.setText(types.isEmpty() ? "a1" : Character.toLowerCase(types.get(0).name.charAt(0)) + "1");
            name.addListener(SWT.Modify, e -> update());
            new Label(nr, SWT.NONE).setText("גודל המערך:");
            size = new Spinner(nr, SWT.BORDER);
            size.setValues(4, 1, 12, 0, 1, 1);
            size.addListener(SWT.Modify, e -> update());
            clsHead = Look.section(card, "המחלקה של העצם", 10);
            cls = new Picker(card, null, () -> update());
            paramsHead = Look.section(card, "ערך לכל פרמטר של הבנאי", 10);
            params = grid(card, 3, SWT.LEFT_TO_RIGHT);
            cellsHead = Look.section(card, "התאים (מחלקה וערכים בפסיקים)", 10);
            cells = grid(card, 3, SWT.LEFT_TO_RIGHT);
            Look.section(card, "הקוד שייכנס במקום הסמן", 10);
            preview = new Text(card, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.LEFT_TO_RIGHT);
            preview.setFont(Look.mono(area.getDisplay(), -1));
            preview.setData("dsviz.keepFont", Boolean.TRUE);
            preview.setBackground(Look.color(area.getDisplay(), Look.SURFACE));
            GridData pg = new GridData(SWT.FILL, SWT.FILL, true, true);
            pg.widthHint = 480;
            pg.heightHint = 90;
            preview.setLayoutData(pg);
            errorLabel = new Label(area, SWT.WRAP);
            errorLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
            errorLabel.setForeground(area.getDisplay().getSystemColor(SWT.COLOR_RED));
            fillClasses();
            Look.applyFont(area, -1);
            return area;
        }

        @Override
        protected Control createButtonBar(Composite parent) {
            Look.Round[] out = new Look.Round[1];
            Composite bar = Look.buttonBar(parent, "הוספה לקוד", () -> { if (ok.getEnabled()) okPressed(); }, () -> cancelPressed(), out);
            ok = out[0];
            update();
            return bar;
        }

        void fillClasses() {
            List<ClassGen.Known> ok = ClassGen.objectClasses(known, type.value);
            cls.set(ok, colors, ok.size() > 1 ? ok.get(1).name : ok.isEmpty() ? "" : ok.get(0).name);
            for (Control[] r : cellRows) fillCell((Combo) r[1]);
        }

        void fillCell(Combo b) {
            String was = b.getText();
            List<String> items = new ArrayList<String>();
            items.add("");
            for (ClassGen.Known k : ClassGen.objectClasses(known, type.value)) items.add(k.name);
            b.setItems(items.toArray(new String[0]));
            b.setText(items.contains(was) ? was : "");
        }

        void layoutParams() {
            List<ClassGen.Field> ps = ClassGen.ctorParams(known, cls.value);
            StringBuilder key = new StringBuilder(cls.value);
            for (ClassGen.Field f : ps) key.append('|').append(f.type).append(' ').append(f.name);
            if (key.toString().equals(shownParams)) return;
            shownParams = key.toString();
            List<String> old = new ArrayList<String>();
            for (Text t : args) old.add(t.getText());
            clear(params);
            args.clear();
            for (int i = 0; i < ps.size(); i++) {
                ClassGen.Field f = ps.get(i);
                String owner = ClassGen.paramOwner(known, cls.value, f.name);
                Integer c = colors.get(owner);
                new Tag(params, owner, c == null ? -1 : c);
                Label l = new Label(params, SWT.LEFT_TO_RIGHT);
                l.setText(f.type + " " + f.name);
                l.setFont(Look.mono(params.getDisplay(), -1));
                l.setData("dsviz.keepFont", Boolean.TRUE);
                Text t = text(params, 140, "...");
                if (i < old.size()) t.setText(old.get(i));
                t.addListener(SWT.Modify, e -> update());
                args.add(t);
            }
        }

        void layoutCells() {
            int n = size.getSelection();
            while (cellRows.size() < n) {
                Label idx = new Label(cells, SWT.NONE);
                idx.setText("[" + cellRows.size() + "]");
                Combo b = new Combo(cells, SWT.READ_ONLY | SWT.LEFT_TO_RIGHT);
                fillCell(b);
                Text v = text(cells, 260, "ערכים בפסיקים");
                b.addListener(SWT.Selection, e -> update());
                v.addListener(SWT.Modify, e -> update());
                cellRows.add(new Control[] { idx, b, v });
            }
            for (int i = 0; i < cellRows.size(); i++)
                for (Control c : cellRows.get(i)) {
                    c.setVisible(i < n);
                    ((GridData) (c.getLayoutData() == null ? new GridData() : c.getLayoutData())).exclude = i >= n;
                }
            for (int i = 0; i < n; i++) {
                Control[] r = cellRows.get(i);
                StringBuilder ph = new StringBuilder();
                for (ClassGen.Field f : ClassGen.ctorParams(known, ((Combo) r[1]).getText())) ph.append(ph.length() > 0 ? ", " : "").append(f.name);
                ((Text) r[2]).setMessage(ph.toString());
                ((Text) r[2]).setEnabled(!((Combo) r[1]).getText().isEmpty());
            }
        }

        ClassGen.ObjResult result() {
            ClassGen.ObjOptions o = new ClassGen.ObjOptions();
            o.lang = ClassGen.JAVA;
            o.varType = type.value;
            o.varName = name.getText();
            o.indent = indent;
            o.array = many.getSelection();
            o.cls = cls.value;
            for (Text t : args) o.args.add(t.getText());
            o.size = size.getSelection();
            for (int i = 0; i < o.size && i < cellRows.size(); i++) {
                Control[] r = cellRows.get(i);
                List<String> cell = new ArrayList<String>();
                String c = ((Combo) r[1]).getText();
                if (!c.isEmpty()) {
                    cell.add(c);
                    cell.addAll(ClassGen.splitArgs(((Text) r[2]).getText()));
                }
                o.cells.add(cell);
            }
            ClassGen.ObjResult r = ClassGen.objectCode(o, known);
            if (r.ok()) {
                String bad = NameCheck.checkName(o.varName.trim(), doc, offset, n -> ClassGen.isIdentifier(n, ClassGen.JAVA));
                if (bad != null) r.errors.add(bad);
            }
            if (known.isEmpty()) r.errors.add(0, "אין עדיין מחלקות בפרויקט — קודם \"צור מחלקה\" או \"היררכיה מוכנה\"");
            return r;
        }

        void update() {
            if (updating || errorLabel == null || ok == null) return;
            updating = true;
            try {
                boolean arr = many.getSelection();
                for (Control c : new Control[] { clsHead, cls.box, paramsHead, params }) {
                    c.setVisible(!arr);
                    ((GridData) c.getLayoutData()).exclude = arr;
                }
                for (Control c : new Control[] { cellsHead, cells }) {
                    c.setVisible(arr);
                    ((GridData) c.getLayoutData()).exclude = !arr;
                }
                size.setEnabled(arr);
                if (!arr) layoutParams();
                else layoutCells();
                ClassGen.ObjResult r = result();
                if (!r.code.equals(preview.getText())) preview.setText(r.code.replace(indent, ""));
                errorLabel.setText(r.errors.isEmpty() ? "" : r.errors.get(0));
                code = r.ok() ? r.code : null;
                ok.setEnabled(r.ok());
                getShell().layout(true, true);
            } finally {
                updating = false;
            }
        }

        String code() {
            return code;
        }

        // ---------------------------------------------------------------- DlgScript: call ...
        void demo(String varType, String var, String c, String values) {
            type.select(varType);
            name.setText(var);
            cls.select(c);
            update();
            List<String> vs = ClassGen.splitArgs(values);
            for (int i = 0; i < args.size() && i < vs.size(); i++) args.get(i).setText(vs.get(i));
            update();
        }
    }
}

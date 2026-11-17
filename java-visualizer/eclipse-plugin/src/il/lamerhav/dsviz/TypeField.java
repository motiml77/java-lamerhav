package il.lamerhav.dsviz;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

/**
 * שדה טיפוס עם רשימה נפתחת בקבוצות (החלון "צור מחלקה"): כותבים חופשי, או פותחים את הרשימה (▼ / חץ למטה) ובוחרים.
 * הרשימה: כותרת לכל קבוצה (באפור), הטיפוסים (בגופן קוד), ולידם טקסט אפור (package — כששתי מחלקות באותו שם);
 * קבוצה ריקה — שורה אפורה. הקלדה מסננת. Enter בוחר, Esc סוגר את הרשימה (ולא את החלון).
 */
final class TypeField {
    interface Groups {
        List<ClassGen.Group> get();
    }

    interface Pick {
        void picked(ClassGen.Item item);
    }

    final Composite box;
    final Text text;
    private final Button arrow;
    private final Groups groups;
    private final Pick onPick;
    private final Runnable onType, onEnter;
    private Shell pop;
    private Table table;
    private boolean internal;

    TypeField(Composite parent, int width, Groups groups, Pick onPick, Runnable onType, Runnable onEnter) {
        this.groups = groups;
        this.onPick = onPick;
        this.onType = onType;
        this.onEnter = onEnter;
        box = new Composite(parent, SWT.LEFT_TO_RIGHT);
        GridLayout l = new GridLayout(2, false);
        l.marginWidth = 0;
        l.marginHeight = 0;
        l.horizontalSpacing = 0;
        box.setLayout(l);
        text = new Text(box, SWT.BORDER | SWT.LEFT_TO_RIGHT);
        GridData tg = new GridData(SWT.FILL, SWT.CENTER, true, false);
        tg.widthHint = width;
        text.setLayoutData(tg);
        text.setFont(Look.mono(parent.getDisplay(), 0));
        text.setData("dsviz.keepFont", Boolean.TRUE);
        arrow = new Button(box, SWT.ARROW | SWT.DOWN);
        arrow.setToolTipText("רשימת הטיפוסים");
        arrow.setLayoutData(new GridData(SWT.CENTER, SWT.FILL, false, true));
        arrow.addListener(SWT.Selection, e -> {
            if (isOpen()) close();
            else {
                text.setFocus();
                open(true);
            }
        });
        text.addListener(SWT.Modify, e -> {
            if (internal) return;
            onType.run();
            if (text.isFocusControl()) open(false);
        });
        text.addListener(SWT.KeyDown, this::key);
        text.addListener(SWT.Traverse, e -> {
            if (e.detail == SWT.TRAVERSE_ESCAPE && isOpen()) {   // Esc: רק הרשימה נסגרת
                e.doit = false;
                e.detail = SWT.TRAVERSE_NONE;
                close();
            } else if (e.detail == SWT.TRAVERSE_RETURN) e.doit = false;   // Enter — מטופל ב-KeyDown
        });
        // יצאו מהשדה אל רכיב אחר בחלון — הרשימה נסגרת (אבל לא כשהפוקוס עבר לרשימה עצמה, או ל"אף אחד" בזמן שהיא נפתחת)
        text.addListener(SWT.FocusOut, e -> text.getDisplay().asyncExec(() -> {
            if (text.isDisposed() || pop == null || pop.isDisposed()) return;
            Control f = text.getDisplay().getFocusControl();
            if (f != null && f != text && f.getShell() != pop) close();
        }));
        text.addListener(SWT.Dispose, e -> { if (pop != null && !pop.isDisposed()) pop.dispose(); });
        Listener moved = e -> close();
        // עוברים לחלון אחר (לא הרשימה) — נסגרת
        Listener away = e -> text.getDisplay().asyncExec(() -> {
            if (text.isDisposed() || pop == null || pop.isDisposed()) return;
            Shell a = text.getDisplay().getActiveShell();
            if (a != null && a != pop && a != text.getShell()) close();
        });
        parent.getShell().addListener(SWT.Move, moved);
        parent.getShell().addListener(SWT.Resize, moved);
        parent.getShell().addListener(SWT.Deactivate, away);
        text.addListener(SWT.Dispose, e -> {
            if (!box.getShell().isDisposed()) {
                box.getShell().removeListener(SWT.Move, moved);
                box.getShell().removeListener(SWT.Resize, moved);
                box.getShell().removeListener(SWT.Deactivate, away);
            }
        });
    }

    String value() {
        return text.getText();
    }

    /** כתיבה בשדה בלי לפתוח את הרשימה */
    void set(String v) {
        internal = true;
        try {
            text.setText(v == null ? "" : v);
        } finally {
            internal = false;
        }
    }

    boolean isOpen() {
        return pop != null && !pop.isDisposed() && pop.isVisible();
    }

    void close() {
        if (pop != null && !pop.isDisposed()) pop.setVisible(false);
    }

    private void key(Event e) {
        if (e.keyCode == SWT.ARROW_DOWN || e.keyCode == SWT.ARROW_UP) {
            e.doit = false;
            if (!isOpen()) open(true);
            move(e.keyCode == SWT.ARROW_DOWN ? 1 : -1);
        } else if (e.character == SWT.CR || e.keyCode == SWT.KEYPAD_CR) {
            if ((e.stateMask & SWT.MOD1) != 0) return;   // Ctrl+Enter — יצירה (החלון)
            e.doit = false;
            if (isOpen() && table.getSelectionIndex() >= 0 && table.getItem(table.getSelectionIndex()).getData() instanceof ClassGen.Item) {
                pick((ClassGen.Item) table.getItem(table.getSelectionIndex()).getData());
                return;
            }
            close();
            onEnter.run();
        }
    }

    private void move(int d) {
        int n = table.getItemCount(), i = table.getSelectionIndex();
        for (int k = 0; k < n; k++) {
            i = (i + d + n) % n;
            if (table.getItem(i).getData() instanceof ClassGen.Item) {
                table.setSelection(i);
                table.showSelection();
                return;
            }
        }
    }

    private void pick(ClassGen.Item it) {
        close();
        set(it.type);
        text.setFocus();
        text.setSelection(text.getCharCount());
        onPick.picked(it);
    }

    /** פותח את הרשימה מתחת לשדה (all — הכול; אחרת מסונן לפי מה שכתוב) */
    void open(boolean all) {
        String q = all ? "" : text.getText().trim().toLowerCase(Locale.ROOT);
        List<Object[]> rows = new ArrayList<Object[]>();   // { סוג: 0 כותרת / 1 פריט / 2 ריק, טקסט, טקסט אפור, פריט }
        int items = 0;
        for (ClassGen.Group g : groups.get()) {
            List<ClassGen.Item> its = new ArrayList<ClassGen.Item>();
            for (ClassGen.Item it : g.items) if (q.isEmpty() || it.type.toLowerCase(Locale.ROOT).contains(q)) its.add(it);
            if (!q.isEmpty() && its.isEmpty()) continue;
            rows.add(new Object[] { 0, g.title, "", null });
            if (its.isEmpty() && !g.empty.isEmpty()) rows.add(new Object[] { 2, g.empty, "", null });
            for (ClassGen.Item it : its) rows.add(new Object[] { 1, it.type, it.hint, it });
            items += its.size();
        }
        if (items == 0 && !q.isEmpty()) {
            close();
            return;
        }
        Display d = text.getDisplay();
        if (pop == null || pop.isDisposed()) {
            pop = new Shell(text.getShell(), SWT.NO_TRIM | SWT.ON_TOP | SWT.RIGHT_TO_LEFT);
            pop.setBackground(Look.color(d, Look.BORDER));
            FillLayout fl = new FillLayout();
            fl.marginWidth = 1;
            fl.marginHeight = 1;
            pop.setLayout(fl);
            table = new Table(pop, SWT.SINGLE | SWT.FULL_SELECTION | SWT.V_SCROLL | SWT.RIGHT_TO_LEFT);
            table.setBackground(Look.color(d, Look.CARD));
            new TableColumn(table, SWT.RIGHT);
            new TableColumn(table, SWT.RIGHT);
            table.addListener(SWT.MouseUp, e -> {
                TableItem ti = table.getItem(new Point(e.x, e.y));
                if (ti != null && ti.getData() instanceof ClassGen.Item) pick((ClassGen.Item) ti.getData());
            });
            table.addListener(SWT.Selection, e -> {   // כותרת / שורה אפורה — לא נבחרות
                int i = table.getSelectionIndex();
                if (i >= 0 && !(table.getItem(i).getData() instanceof ClassGen.Item)) table.deselectAll();
            });
            table.addListener(SWT.DefaultSelection, e -> {
                int i = table.getSelectionIndex();
                if (i >= 0 && table.getItem(i).getData() instanceof ClassGen.Item) pick((ClassGen.Item) table.getItem(i).getData());
            });
            table.addListener(SWT.KeyDown, e -> {
                if (e.keyCode == SWT.ESC) {
                    close();
                    text.setFocus();
                }
            });
        }
        table.removeAll();
        GC gc = new GC(table);
        int w0 = 60, w1 = 0;
        for (Object[] r : rows) {
            int kind = (Integer) r[0];
            TableItem ti = new TableItem(table, SWT.NONE);
            // טיפוס / package (באנגלית) בתוך רשימה מימין לשמאל: LRM משני הצדדים — אחרת int[] מוצג []int
            ti.setText(0, kind == 1 ? "\u200E" + r[1] + "\u200E" : (String) r[1]);
            ti.setText(1, ((String) r[2]).isEmpty() ? "" : "\u200E" + r[2] + "\u200E");
            if (kind == 0) {
                ti.setFont(0, Look.font(d, -1, SWT.BOLD));
                ti.setForeground(0, Look.color(d, Look.MUTED));
                gc.setFont(Look.font(d, -1, SWT.BOLD));
            } else if (kind == 2) {
                ti.setFont(0, Look.font(d, -1, SWT.ITALIC));
                ti.setForeground(0, Look.color(d, 0x9AA3B2));
                gc.setFont(Look.font(d, -1, SWT.ITALIC));
            } else {
                ti.setData(r[3]);
                ti.setFont(0, Look.mono(d, 0));
                ti.setForeground(1, Look.color(d, 0x9AA3B2));
                gc.setFont(Look.mono(d, 0));
            }
            w0 = Math.max(w0, gc.textExtent((String) r[1]).x + 28);
            if (!((String) r[2]).isEmpty()) {
                gc.setFont(table.getFont());
                w1 = Math.max(w1, gc.textExtent((String) r[2]).x + 20);
            }
        }
        gc.dispose();
        table.getColumn(0).setWidth(w0);
        table.getColumn(1).setWidth(w1);
        int h = Math.min(rows.size() * table.getItemHeight() + 4, 460);
        int w = Math.max(box.getSize().x, w0 + w1 + 22);
        Point at = box.toDisplay(0, box.getSize().y + 1);
        Rectangle screen = text.getMonitor().getClientArea();
        int x = Math.max(screen.x, Math.min(at.x, screen.x + screen.width - w));
        int y = at.y + h > screen.y + screen.height ? box.toDisplay(0, 0).y - h - 1 : at.y;
        pop.setBounds(x, y, w, h);
        if (!pop.isVisible()) {
            pop.setVisible(true);
            text.setFocus();   // ממשיכים להקליד בשדה (ב-GTK חלון חדש לוקח את הפוקוס)
        }
        table.setTopIndex(0);
    }
}
